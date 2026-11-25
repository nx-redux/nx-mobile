package com.nxredux.mobile.runner

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.options.WiiOptions
import com.nxredux.mobile.options.WiiScreen
import com.nxredux.mobile.settings.AndroidAppSettings
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
internal interface WiiDolphinHost : GameScreenHost {
    val settings: AndroidAppSettings
    val gameTag: String
    val isWii: Boolean
    val isDolphin: Boolean
    val isN3ds: Boolean
    val gameReady: Boolean
    val menuOpen: Boolean
    val playerCount: Int
    val frontendValues: Map<String, String>
    val lockedOptions: Set<String>
    fun applyFrontend(values: Map<String, String>)
    fun applyOptions(vars: List<Variable>)
    fun persistGameKey(key: String, value: String, failure: String)
    fun padProfile(): PadProfile
    fun dropPadProfile()
    fun relayoutPad()
    /** [relayoutPad] posted, for a change made by a press still being dispatched ([GameActivity.relayoutPadAfterPress]). */
    fun relayoutPadAfterPress()
    /** The open menu's frontend rows again (its Wii rows follow [menuLocked]). */
    fun publishMenuFrontend()
}

/**
 * Dolphin's Wii and GameCube side of the game screen: the disc's controls (GameTDB), the resolved Wii controller and
 * Motion Controls, the devices Dolphin's ports get, the Wii Screen shape and its pointer geometry, the Wii's HOME, and
 * what the phone's motion sensors ([motion]) feed for a Wii or a 3DS game on Azahar. Main thread.
 */
internal class WiiDolphinController(
    private val host: WiiDolphinHost,
    private val input: GameInputRouter,
    private val saves: SaveSession,
    private val motion: PhoneMotion,
) {
    companion object {
        /** The Dolphin core's descriptor id (Wii and GameCube tags). */
        const val DOLPHIN_ID = "dolphin"
        /** How long the Wii's HOME holds the core's Home (R3): a few frames, as a tap. */
        private const val WII_HOME_PRESS_MS = 150L

        /**
         * Left without HOME this session, the resume point on disk is an older session's, behind the game's own save on
         * the NAND: dropped so the next launch boots fresh. Without Save on quit nothing points at it any more (the stop
         * unlinks it), so the file stays.
         */
        fun dropsResumePointOnCoreEnd(homeSaved: Boolean, saveOnQuit: Boolean): Boolean = !homeSaved && saveOnQuit
    }

    /** The Wii game's controls from GameTDB, looked up by the disc ID read at launch (null = unread or not listed). */
    private var controls: WiiControls? = null
    /** The resolved Wii controller ([WiiController.resolve]); null outside Wii, and until the launch resolves it. */
    var pad: WiiPad? = null
        private set
    /** Motion Controls resolved ([WiiMotion.resolve]): the phone's sensors drive port 0's Wiimote. */
    private var motionOn = false
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
    /**
     * The core asked to end (main thread): set before the launch drops the stale resume point, while the quit has not
     * started yet, so neither a HOME save nor onStop's auto state can queue a slot 9 behind the drop.
     */
    var endedByCore = false
    /** The Wii shape chosen at launch ([screenOption]); null for any other console. */
    private var launchWide: Boolean? = null
    /** The shape the pointer geometry is set for: the launch's, then the game's own ([followShape]). */
    private var pointerWide: Boolean? = null
    /** [WiiScreen.keptShapeNote] is said once per launch. */
    private var shapeNoted = false

    /** Both of the phone's motion sensors are there (Motion Controls can be offered). */
    val hasSensors: Boolean get() = motion.hasSensors

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
            host.publishMenuFrontend()   // a menu opened during the read drops its Wii rows
        }
        if (WiiOptions.appliesTo(tag) && !gameCube) {
            val id = wiiDisc?.id
            controls = withContext(Dispatchers.IO) { WiiControlsAsset.get(host.activity).lookup(id) }
            val sensors = motion.offer()
            motion.captureRotation()
            // The current values: the menu may have changed them while the image was read.
            val pad = WiiController.resolve(host.frontendValues[WiiOptions.CONTROLLER], controls)
            this.pad = pad
            motionOn = WiiMotion.resolve(host.frontendValues[WiiOptions.MOTION], pad, controls, sensors.hasSensors)
            LibretroDroid.setSensorEnabled(0, motionOn)
            NxLog.i(TAG_LOG, "Wii disc $id: $controls -> $pad, motion $motionOn")
            host.dropPadProfile()
            host.relayoutPad()
            // A menu opened during the read: its Motion Controls row (left out until the sensors were known) and its Wii rows.
            host.publishMenuFrontend()
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
            motion.reoffer()
            LibretroDroid.setSensorEnabled(0, motionOn)
            sendDevice()
            syncMotionSensors()
            if (controls?.motionPlus == true && !motion.hasSensors) {
                host.toast("Motion actions in this game need a phone with a gyroscope")
            }
        }
        if (host.isN3ds) {
            offerN3dsMotion()   // again: a previous game's unload can reset the native sensor state after the launch's offer
            syncMotionSensors()
        }
    }

    /** The Wiimote's level is captured in the display's frame: a new rotation takes the phone's attitude as level again. */
    fun onConfigurationChanged() {
        if (host.isWii) motion.onRotationChanged()
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
        val on = WiiMotion.resolve(values[WiiOptions.MOTION], pad, controls, hasSensors)
        if (pad == this.pad && on == motionOn) return
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
        motionOn = on
        LibretroDroid.setSensorEnabled(0, on)
        host.dropPadProfile()
        // Before the first frame the core may not be loaded: the first-frame block sends the device then, whatever it is.
        if (host.gameReady) { sendDevice(); syncMotionSensors() }
        host.relayoutPadAfterPress()
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

    /** What the phone's sensors feed for this game: the Wiimote while Motion Controls is on, a 3DS with an accelerometer. */
    fun motionFeed(): PhoneMotion.Feed? = when {
        host.isN3ds && motion.sensors?.hasAccel == true -> PhoneMotion.Feed.N3ds
        host.isWii && motionOn -> PhoneMotion.Feed.Wiimote { pad ?: WiiPad.SIDEWAYS }
        else -> null
    }

    private fun syncMotionSensors() = motion.sync(motionFeed())

    /**
     * 3DS motion: the phone's sensors offered to the core. Before the load, as Azahar asks for them once, when its HID
     * service builds the motion device (citra_libretro/input/input_factory.cpp), each sensor on its own: without a gyroscope
     * the tilt still comes through and the gyro reads still; without an accelerometer the console reads as a 3DS at rest.
     */
    fun offerN3dsMotion() {
        LibretroDroid.setSensorEnabled(0, motion.offer().hasAccel)
    }

    /** The pad's motion button: tap toggles Motion Controls (saved for this game), long press recentres. */
    private fun toggleMotion() {
        if (WiiOptions.MOTION in host.lockedOptions) return   // locked in the .cfg: left alone, as the hotkeys do (setNdsKey)
        val next = if (motionOn) WiiMotion.OFF else WiiMotion.ON
        host.applyFrontend(host.frontendValues + (WiiOptions.MOTION to next))
        host.persistGameKey(WiiOptions.MOTION, next, "Could not save Motion Controls")
    }

    /** Takes the phone's attitude at the next reading as the Wiimote's level. */
    fun recenterMotion() { motion.recenter() }

    /** The pad's motion button ([PadMotion]): Wiimote types on a phone with both sensors; none without them. */
    fun padMotion(): com.nxredux.mobile.ui.nx.PadMotion? =
        if (host.isWii && host.padProfile().motionButton && hasSensors) com.nxredux.mobile.ui.nx.PadMotion(motionOn, ::toggleMotion, ::recenterMotion) else null

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
            NxLog.i(TAG_LOG, "Wii game draws ${if (wide) "16:9" else "4:3"} (launched ${if (launchWide) "16:9" else "4:3"}): pointer follows")
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
        // A quit (or the core's own end) decides slot 9 itself: a HOME save now would race it, and the core is about to go.
        if (homeSaving || host.quitting || endedByCore) return
        val view = host.retroView ?: return
        val ss = saves.saveStates
        // Without Save on quit there is no resume point to keep: HOME opens at once.
        val keep = host.settings.saveOnQuit
        if (ss == null || !host.gameReady || saves.resumePending || !keep) { tapHome(view); return }
        homeSaving = true
        saves.writeHomeResumePoint(view, ss, onSaved = { homeSaved = true }) { homeSaving = false; if (!host.quitting) tapHome(view) }
    }

    private fun tapHome(view: GLRetroView) {
        view.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_THUMBR, 0)
        host.lifecycleScope.launch { delay(WII_HOME_PRESS_MS); view.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_THUMBR, 0) }
    }
}
