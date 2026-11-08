package com.nxredux.mobile.runner

import android.content.res.Configuration
import android.os.Build
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.R
import com.nxredux.mobile.options.WiiOptions
import com.nxredux.mobile.options.WiiScreen
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.nxredux.mobile.ui.nx.InputRouter
import com.nxredux.mobile.ui.nx.PadProfile
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What [WiiDolphinController] needs from [GameActivity]: the game state the controller and sensors follow, the frontend
 * values and config writes Motion Controls goes through, and the pad and menu it rebuilds when the controller changes.
 */
internal interface WiiDolphinHost : LifecycleOwner {
    val activity: ComponentActivity
    val retroView: GLRetroView?
    val gameTag: String
    val isWii: Boolean
    val isDolphin: Boolean
    val isN3ds: Boolean
    val gameReady: Boolean
    val quitting: Boolean
    val menuOpen: Boolean
    val menuRouter: InputRouter
    val playerCount: Int
    val frontendValues: Map<String, String>
    val lockedOptions: Set<String>
    fun applyFrontend(values: Map<String, String>)
    fun applyOptions(vars: List<Variable>)
    fun persistGameKey(key: String, value: String, failure: String)
    fun padProfile(): PadProfile
    fun dropPadProfile()
    fun relayoutPad()
    fun withMenu(block: (InGameMenuModel) -> Unit)
    fun refreshWiiMenu(model: InGameMenuModel)
    fun toast(msg: String): android.widget.Toast
}

/**
 * Dolphin's Wii and GameCube side of the game screen: the disc's controls (GameTDB), the resolved Wii controller and
 * Motion Controls, the devices Dolphin's ports get, the Wii Screen shape and its pointer geometry, the Wii's HOME, and the
 * phone's motion sensors (which a 3DS game on Azahar uses too). Main thread.
 */
internal class WiiDolphinController(
    private val host: WiiDolphinHost,
    private val input: GameInputRouter,
    private val saves: SaveSession,
) {
    companion object {
        /** The Dolphin core's descriptor id (Wii and GameCube tags). */
        const val DOLPHIN_ID = "dolphin"
        /** How long the Wii's HOME holds the core's Home (R3): a few frames, as a tap. */
        private const val WII_HOME_PRESS_MS = 150L
    }

    /** The Wii game's controls from GameTDB, looked up by the disc ID read at launch (null = unread or not listed). */
    private var controls: WiiControls? = null
    /** The resolved Wii controller ([WiiController.resolve]); null outside Wii, and until the launch resolves it. */
    var pad: WiiPad? = null
        private set
    /** Motion Controls resolved ([WiiMotion.resolve]): the phone's sensors drive port 0's Wiimote. */
    private var motionOn = false
    /** The phone's accelerometer and gyroscope, created for a Wii or 3DS game only. */
    private var motionSensors: MotionSensors? = null
    /** The display rotation the motion level was last captured in: a change mid-game recentres ([onConfigurationChanged]). */
    private var lastRotation = -1
    /**
     * The disc read under the WII tag is a GameCube disc ([WiiDisc.gameCube]): it keeps the plain pad and the descriptor's
     * ports, with no Wiimote, pointer, motion or Wii menu rows.
     */
    var gameCube = false
        private set
    private var homeSaving = false   // [pressHome]'s resume point being saved (main thread only)
    /** [pressHome] saved this session's resume point. */
    @Volatile var homeSaved = false
        private set
    /** The Wii shape chosen at launch ([screenOption]); null for any other console. */
    private var launchWide: Boolean? = null
    /** The shape the pointer geometry is set for: the launch's, then the game's own ([followShape]). */
    private var pointerWide: Boolean? = null
    /** [WiiScreen.keptShapeNote] is said once per launch. */
    private var shapeNoted = false

    /** Both of the phone's motion sensors are there (Motion Controls can be offered). */
    val hasSensors: Boolean get() = motionSensors?.hasSensors == true

    /**
     * Launch, once the content is known: Wii Controller and Motion Controls (Console Settings), whose Auto looks the disc
     * ID read from the image ([wiiDisc]; null when it is not a Wii tag or not a readable image) up in GameTDB's table.
     */
    suspend fun resolveAtLaunch(tag: String, wiiDisc: WiiDisc?) {
        // A GameCube image under the WII tag (outside a gc folder): the plain pad, the C-stick as the right stick.
        if (wiiDisc?.gameCube == true) {
            gameCube = true
            host.dropPadProfile()   // the GameCube pad, not the plain one
            host.relayoutPad()   // the pad was composed from the intent tag in onCreate
            NxLog.i(TAG_LOG, "GameCube disc ${wiiDisc.id} under the Wii tag: no Wii controls")
            host.withMenu { host.refreshWiiMenu(it) }   // a menu opened during the read drops its Wii rows
        }
        if (WiiOptions.appliesTo(tag) && !gameCube) {
            val id = wiiDisc?.id
            controls = withContext(Dispatchers.IO) { WiiControlsAsset.get(host.activity).lookup(id) }
            val sensors = sensors()
            LibretroDroid.setSensorAvailable(sensors.hasAccel, sensors.hasGyro)
            // The rotation the level starts in: only a real rotation recentres, not the first unrelated configuration
            // change (a pad attached, a fold posture).
            lastRotation = displayRotation()
            // The current values: the menu may have changed them while the image was read.
            val pad = WiiController.resolve(host.frontendValues[WiiOptions.CONTROLLER], controls)
            this.pad = pad
            motionOn = WiiMotion.resolve(host.frontendValues[WiiOptions.MOTION], pad, controls, sensors.hasSensors)
            LibretroDroid.setSensorEnabled(0, motionOn)
            NxLog.i(TAG_LOG, "Wii disc $id: $controls -> $pad, motion $motionOn")
            host.dropPadProfile()
            host.relayoutPad()
            // A menu opened during the read: its Motion Controls row (left out until the sensors were known) and its Wii rows.
            host.withMenu { host.refreshWiiMenu(it) }
        }
    }

    /**
     * Dolphin: port 0 and the slots a pad already holds get the game's device (Wii: the resolved controller, Dolphin's
     * Wiimote + Nunchuk, Classic, MotionPlus); the other ports stay unplugged until a pad claims them
     * ([connectDolphinPorts]), or Dolphin connects Wiimotes 2-4 and the game shows a co-star cursor.
     */
    fun launchPorts(players: Int): Map<Int, Int> = dolphinPorts(dolphinDevice(), claimedPorts(), players)

    /** The first frame: the devices and sensors again, as the core may not have been loaded when they were first set. */
    fun onFirstFrame(view: GLRetroView) {
        if (host.isDolphin && !host.isWii) sendDevice()   // GameCube: a slot claimed while the game loaded
        if (host.isWii) {
            view.stickyPointer = true   // the right stick moves the IR pointer, which stays where it was left
            // Always, not only on a change made during load: a previous game's core unload (its onDestroy can run
            // after this screen's onCreate) resets the native sensor state, and resending the device makes Dolphin
            // ask for the sensors again (cores/patches/dolphin.patch).
            motionSensors?.let { LibretroDroid.setSensorAvailable(it.hasAccel, it.hasGyro) }
            LibretroDroid.setSensorEnabled(0, motionOn)
            sendDevice()
            syncMotionSensors()
            if (controls?.motionPlus == true && motionSensors?.hasSensors != true) {
                host.toast("Motion actions in this game need a phone with a gyroscope")
            }
        }
        if (host.isN3ds) {
            offerN3dsMotion()   // again: a previous game's unload can reset the native sensor state after the launch's offer
            syncMotionSensors()
        }
    }

    /** The motion level is captured in the display's frame: a new rotation takes the phone's attitude as level again. */
    fun onConfigurationChanged() {
        if (host.isWii) displayRotation().let { r -> if (r != lastRotation) { lastRotation = r; motionSensors?.recenter() } }
    }

    fun onDestroy() {
        // Created only by the Wii and 3DS paths, which both enable sensor 0; a next game sets its own on its first frame.
        motionSensors?.let { it.stop(); LibretroDroid.setSensorEnabled(0, false) }
    }

    /**
     * A Wii Controller or Motion Controls change (menu row, Restore Defaults, the pad's motion button): resolved again,
     * the pad rebuilt, and the core given the new device on every port now (Dolphin rebuilds a port's mapping live, and
     * re-asks the runner for sensors before it does; cores/patches/dolphin.patch). Only once the launch resolved the pad:
     * before that the launch reads the values itself.
     */
    fun apply(values: Map<String, String>) {
        if (!host.isWii || pad == null) return
        val pad = WiiController.resolve(values[WiiOptions.CONTROLLER], controls)
        val motion = WiiMotion.resolve(values[WiiOptions.MOTION], pad, controls, motionSensors?.hasSensors == true)
        if (pad == this.pad && motion == motionOn) return
        if (pad != this.pad) {
            // Only a pad change lets go of everything: a motion toggle (the pad's MOTION, tapped with one thumb) must
            // not drop the stick the other thumb holds.
            if (!host.menuOpen) input.releaseAllGameInput()
            // A stick held through the change would keep the pointer drifting (a Classic Controller never stops it), and
            // a right stick left tilted under Classic would stay stuck as the Nunchuk's tilt, which the right stick does
            // not reach (Dolphin binds it to tilt under IR mode 2): both are zeroed on every port.
            host.retroView?.let { v ->
                for (p in 0 until maxOf(host.playerCount, 1)) {
                    v.setPointerVelocity(p, 0f, 0f)
                    v.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, p)
                }
            }
        }
        this.pad = pad
        motionOn = motion
        LibretroDroid.setSensorEnabled(0, motion)
        host.dropPadProfile()
        // Before the first frame the core may not be loaded: the first-frame block sends the device then, whatever it is.
        if (host.gameReady) { sendDevice(); syncMotionSensors() }
        // Posted, as for the Sega pad (applySegaPad): the press that made the change is still being dispatched.
        host.activity.findViewById<FrameLayout>(R.id.root).post {
            if (host.menuOpen) host.menuRouter.releaseAll()
            host.relayoutPad()
        }
    }

    /** The Dolphin device for [pad]: MotionPlus added when the game takes it ([WiiController.motionPlus]). */
    private fun wiiDevice(pad: WiiPad): Int = WiiController.device(pad, WiiController.motionPlus(pad, controls))

    /** Dolphin's device for its connected ports: the resolved Wii controller, or the descriptor's plain pad (GameCube). */
    private fun dolphinDevice(): Int =
        pad?.let(::wiiDevice) ?: CoreDescriptor.JOYPAD

    /** The ports a pad holds in the player slots (live or held for it): with port 0, the ones Dolphin has connected ([dolphinPorts]). */
    private fun claimedPorts(): Set<Int> =
        input.slots?.slots().orEmpty().withIndex().filter { it.value != SlotView.Empty }.map { it.index }.toSet()

    /**
     * Sends the device to port 0 and every claimed port on the emulation thread. Never plugs in an unclaimed port, and a
     * pad that unplugs keeps its port (its slot is held), so the game is not asked to reconnect a remote.
     */
    private fun sendDevice() {
        if (!host.isDolphin) return
        val view = host.retroView ?: return
        val ports = dolphinPorts(dolphinDevice(), claimedPorts(), host.playerCount).filterValues { it != CoreDescriptor.NONE }
        view.queueEvent {
            for ((p, device) in ports) {
                runCatching { view.setControllerType(p, device) }.onFailure { NxLog.w(TAG_LOG, "setting the Dolphin controller failed", it) }
            }
        }
    }

    /** A pad took player slot N on Dolphin: that port is connected now (before this, it was unplugged). */
    fun connectDolphinPorts() {
        if (host.isDolphin && host.gameReady) sendDevice()
    }

    /**
     * The sensors listen only while motion is on and the game runs (not paused, menu closed, in front). [foreground]:
     * onResume and onPause pass it, as the lifecycle state there does not say yet which side of the pause this is.
     */
    fun syncMotionSensors(foreground: Boolean = host.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        val sensors = motionSensors ?: return
        val n3ds = host.isN3ds && sensors.hasAccel
        val run = ((host.isWii && motionOn) || n3ds) && host.gameReady && !host.menuOpen && !host.quitting && foreground
        when {
            !run -> sensors.stop()
            n3ds -> sensors.startN3ds(::displayRotation)
            else -> sensors.start(::displayRotation, { pad ?: WiiPad.SIDEWAYS })
        }
    }

    /**
     * 3DS motion: the phone's sensors offered to the core. Before the load, as Azahar asks for them once, when its HID
     * service builds the motion device (citra_libretro/input/input_factory.cpp), each sensor on its own: without a gyroscope
     * the tilt still comes through and the gyro reads still; without an accelerometer the console reads as a 3DS at rest.
     */
    fun offerN3dsMotion() {
        val sensors = sensors()
        LibretroDroid.setSensorAvailable(sensors.hasAccel, sensors.hasGyro)
        LibretroDroid.setSensorEnabled(0, sensors.hasAccel)
    }

    /** The phone's sensors feeding port 0, made on first use. */
    private fun sensors(): MotionSensors = motionSensors ?: MotionSensors(host.activity) { a, g ->
        LibretroDroid.setSensorValues(0, a.x, a.y, a.z, g.x, g.y, g.z)
    }.also { motionSensors = it }

    /** The display's rotation (Surface.ROTATION_*), which [MotionSensors] turns the phone's axes by. */
    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) host.activity.display?.rotation ?: 0 else host.activity.windowManager.defaultDisplay.rotation

    /** The pad's motion button: tap toggles Motion Controls (saved for this game), long press recentres. */
    private fun toggleMotion() {
        if (WiiOptions.MOTION in host.lockedOptions) return   // locked in the .cfg: left alone, as the hotkeys do (setNdsKey)
        val next = if (motionOn) WiiMotion.OFF else WiiMotion.ON
        host.applyFrontend(host.frontendValues + (WiiOptions.MOTION to next))
        host.persistGameKey(WiiOptions.MOTION, next, "Could not save Motion Controls")
    }

    /** Takes the phone's attitude at the next reading as the Wiimote's level. */
    fun recenterMotion() { motionSensors?.recenter() }

    /** The pad's motion button ([PadMotion]): Wiimote types on a phone with both sensors; none without them. */
    fun padMotion(): com.nxredux.mobile.ui.nx.PadMotion? =
        if (host.isWii && host.padProfile().motionButton && motionSensors?.hasSensors == true) com.nxredux.mobile.ui.nx.PadMotion(motionOn, ::toggleMotion, ::recenterMotion) else null

    /**
     * Display → Wii Screen for a Wii game: Dolphin's widescreen system setting, chosen from the orientation the
     * game starts in and the screen's shape (games read it once at boot), and the touch pointer's geometry for that
     * shape. Empty for any other console.
     */
    fun screenOption(): Map<String, String> {
        if (!WiiOptions.appliesTo(host.gameTag) || gameCube) return emptyMap()
        val portrait = host.activity.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val m = host.activity.resources.displayMetrics
        val screenAspect = maxOf(m.widthPixels, m.heightPixels).toFloat() / minOf(m.widthPixels, m.heightPixels).coerceAtLeast(1)
        val wide = WiiScreen.widescreen(host.frontendValues[WiiOptions.SCREEN], portrait, screenAspect)
        launchWide = wide
        pointerWide = wide
        return mapOf(WiiScreen.CORE_KEY to WiiScreen.coreValue(wide)) + pointerOptions(wide)
    }

    private fun pointerOptions(wide: Boolean): Map<String, String> = WiiScreen.pointerOptions(
        wide, host.frontendValues[WiiOptions.POINTER_WIDTH], host.frontendValues[WiiOptions.POINTER_HEIGHT], host.frontendValues[WiiOptions.POINTER_OFFSET],
    )

    /**
     * A save state keeps the shape its game booted with, so a resumed Wii game can draw the other shape than the one
     * chosen at launch: the core's frame aspect ([g], `[w, h, aspect, rotation]`) says which. The pointer geometry follows
     * it (Dolphin takes it live), so a tap still lands under the finger, and a note says once why Wii Screen did not apply.
     */
    fun followShape(g: FloatArray) {
        val launchWide = launchWide ?: return
        if (g.size < 3 || g[2] <= 0f) return
        val wide = WiiScreen.isWide(g[2])
        if (wide != pointerWide) {
            pointerWide = wide
            NxLog.i("GameActivity", "Wii game draws ${if (wide) "16:9" else "4:3"} (launched ${if (launchWide) "16:9" else "4:3"}): pointer follows")
            host.applyOptions(pointerOptions(wide).map { (k, v) -> Variable(k, v) })
        }
        if (!shapeNoted) WiiScreen.keptShapeNote(wide, launchWide)?.let {
            shapeNoted = true
            android.widget.Toast.makeText(host.activity, it, android.widget.Toast.LENGTH_LONG).show()   // two lines: the short one ends too soon
        }
    }

    /**
     * The Wii's HOME (the pad's, or a controller's right-stick click): saves the resume point, then taps the core's Home
     * (R3). Choosing Wii Menu in the game's HOME menu closes the game ([GLRetroView.getShutdownRequests]) with no state of
     * its own, since the game has stopped itself by then: resuming lands here, just before the HOME menu opened. A press
     * while one is still being saved is dropped.
     */
    fun pressHome() {
        if (homeSaving) return
        val view = host.retroView ?: return
        val ss = saves.saveStates
        if (ss == null || !host.gameReady || saves.resumePending) { tapHome(view); return }
        homeSaving = true
        saves.writeHomeResumePoint(view, ss, onSaved = { homeSaved = true }) { homeSaving = false; if (!host.quitting) tapHome(view) }
    }

    private fun tapHome(view: GLRetroView) {
        view.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_THUMBR, 0)
        host.lifecycleScope.launch { delay(WII_HOME_PRESS_MS); view.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_THUMBR, 0) }
    }
}
