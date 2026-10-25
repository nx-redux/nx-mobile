package com.nxredux.mobile.runner

import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.ui.nx.AxisDirections
import com.nxredux.mobile.ui.nx.DpadHat
import com.nxredux.mobile.ui.nx.InputRouter
import com.nxredux.mobile.ui.nx.NotificationQueue
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.PadProfile
import com.nxredux.mobile.ui.nx.PlayerPadRow
import com.nxredux.mobile.ui.nx.PlayerSlotRow
import com.nxredux.mobile.ui.nx.VirtualStickRouter
import com.nxredux.mobile.ui.nx.map
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What [GameInputRouter] needs from [GameActivity]: the game's identity and view, the menu and quit flags, and the
 * actions input triggers elsewhere (the menu, the Wii HOME, the layout, the config). Nothing here is a copy: the activity
 * owns each of these.
 */
internal interface GameInputHost : LifecycleOwner {
    val retroView: GLRetroView?
    val menuOpen: Boolean
    val gameTag: String
    val gameCoreId: String
    val frontendValues: Map<String, String>
    val lockedOptions: Set<String>
    /** The pad table for this game ([GameActivity] resolves it at launch and on a Controller Type change). */
    val padMap: PadMap
    val wiiPad: WiiPad?
    val isN64: Boolean
    val isDc: Boolean
    val isNds: Boolean
    val isN3ds: Boolean
    val isWii: Boolean
    val isDolphin: Boolean
    val rumbles: Array<RumbleMotor>
    val inputManager: InputManager?
    val notifications: NotificationQueue
    val menuRouter: InputRouter
    val gameContainer: FrameLayout
    /** The DS screens as laid out now on main, and on the second display (px of it); empty = none. */
    val ndsRegions: List<NdsRegion>
    val secondNdsRegions: List<NdsRegion>
    val presentation: SecondScreenPresentation?
    /** The game's controller shortcuts (fast forward, rewind, reset); null before the launch has read them. */
    val shortcutKeys: ShortcutKeys?
    fun padProfile(): PadProfile
    fun publishNotifications()
    fun toggleMenu()
    /** A bound shortcut's press or release ([ShortcutKeys]). */
    fun onShortcut(action: ShortcutAction, down: Boolean)
    /** The buttons were taken (the menu, the focus): held shortcuts end, toggled ones stay ([SpeedController.releaseHolds]). */
    fun releaseShortcutHolds()
    /**
     * A raw key while the in-game menu is open: true when its Shortcuts page's press to bind took it ([InGameMenuModel.takesKey]),
     * so the menu never sees it (MENU, B, Back included).
     */
    fun menuCaptureKey(keyCode: Int, down: Boolean, repeat: Boolean): Boolean
    fun backPressed()
    fun pressWiiHome()
    fun recenterWiiMotion()
    fun onN3dsSwapPressed(down: Boolean): Boolean
    fun connectDolphinPorts()
    fun onInputDevicesChanged()
    fun applyFrontend(values: Map<String, String>)
    fun persistGameKey(key: String, value: String, failure: String)
    fun syncN3dsCore()
    fun isLandscape(): Boolean
    fun ndsControllerMode(): Boolean
    /** The activity's own handling (super) of an event the router leaves to the system. */
    fun superGenericMotionEvent(event: MotionEvent?): Boolean
    fun superKeyDown(keyCode: Int, event: KeyEvent): Boolean
    fun superKeyUp(keyCode: Int, event: KeyEvent): Boolean
}

/**
 * The game's input: hardware keys and axes (onKeyDown / onKeyUp / onGenericMotionEvent, which the activity forwards
 * here), the on-screen pad, which port each pad drives ([slots], [inputs]), the trigger axes, the GameCube triggers, the
 * DS / 3DS hotkeys and stylus pen, the menu chord, and releasing what a pad held ([releaseAllGameInput], [releasePort]).
 */
internal class GameInputRouter(private val host: GameInputHost) {

    companion object {
        private const val NDS_LABEL_MS = 1500L   // a DS hotkey's label ("Bottom screen", "Stylus on")
        private const val PLAYER_NOTICE_MS = 2500L   // "Player 2: <pad>" when a pad takes a player slot
        private const val PEN_TICK_MS = 16L
        /**
         * Cores that read the left stick as analog (N64, PSP, PS1 DualShock, Dreamcast, and Dolphin's GameCube stick and
         * Wii Nunchuk and Classic sticks): a gamepad's stick is analog only there. melonDS DS has its own routing (the NDS
         * branch of onGenericMotionEvent). Every other core, prboom included, gets the stick folded into the d-pad.
         */
        private val STICK_CORES = setOf("mupen64plus_next", "ppsspp", "pcsx_rearmed", "swanstation", "flycast", "dolphin")
    }

    private val retroView get() = host.retroView
    private val menuOpen get() = host.menuOpen
    private val gameTag get() = host.gameTag
    private val gameCoreId get() = host.gameCoreId
    private val frontendValues get() = host.frontendValues
    private val lockedOptions get() = host.lockedOptions
    private val padMap get() = host.padMap
    private val wiiPad get() = host.wiiPad
    private val isN64 get() = host.isN64
    private val isDc get() = host.isDc
    private val isNds get() = host.isNds
    private val isN3ds get() = host.isN3ds
    private val isWii get() = host.isWii
    private val isDolphin get() = host.isDolphin
    private val rumbles get() = host.rumbles
    private val inputManager get() = host.inputManager
    private val notifications get() = host.notifications
    private val menuRouter get() = host.menuRouter
    private val lifecycleScope get() = host.lifecycleScope
    private val ndsRegions get() = host.ndsRegions
    private val secondNdsRegions get() = host.secondNdsRegions
    private val presentation get() = host.presentation
    private fun padProfile() = host.padProfile()
    private fun publishNotifications() = host.publishNotifications()
    private fun toggleMenu() = host.toggleMenu()
    private fun backPressed() = host.backPressed()
    private fun pressWiiHome() = host.pressWiiHome()
    private fun recenterWiiMotion() = host.recenterWiiMotion()
    private fun onN3dsSwapPressed(down: Boolean) = host.onN3dsSwapPressed(down)
    private fun connectDolphinPorts() = host.connectDolphinPorts()
    private fun onInputDevicesChanged() = host.onInputDevicesChanged()
    private fun applyFrontend(values: Map<String, String>) = host.applyFrontend(values)
    private fun syncN3dsCore() = host.syncN3dsCore()
    private fun isLandscape() = host.isLandscape()
    private fun ndsControllerMode() = host.ndsControllerMode()

    val dpadHat = DpadHat()

    // Re-lay-out when a controller is plugged/unplugged so the on-screen pad (and a DS portrait band) hides/shows live.
    val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = onInputDevicesChanged()
        override fun onInputDeviceRemoved(deviceId: Int) {
            padLayouts.remove(deviceId)
            rumbles.forEach { it.deviceRemoved(deviceId) }
            val key = keysById.remove(deviceId)
            val port = key?.let { k -> slots?.disconnected(k) }
            // A pad unplugged mid-press never sends its key-up or its axes back to rest. One player's pad lets go of
            // its own port only; a one-player core lets go of everything. The open menu released it all already.
            if (!menuOpen) { if (slots == null) releaseAllGameInput() else port?.let(::releasePort) }
            onInputDevicesChanged()
        }
        override fun onInputDeviceChanged(deviceId: Int) { padLayouts.remove(deviceId); onInputDevicesChanged() }
    }

    /** Each pad's detected face layout by device id ([PadFaceLayout.detect]); null for an id with no device. */
    private val padLayouts = HashMap<Int, PadFaceLayout?>()

    /** Each held on-screen button's sent code, so its release repeats its press's. */
    private val padSent = SentKeys<NxButton>()

    /**
     * The keycode a hardware key is sent to the runner as, null to send nothing. Face keys work as printed (Console
     * Settings → Controller Layout names the pad's letters, [PhysicalKeys]): N64 by its printed letter's name
     * ([N64Input]); every other tag by the console's [com.nxredux.mobile.runner.FaceMode], then its [PadMap] picks what
     * that button sends. A key-up repeats its key-down's.
     */
    private fun runnerKey(event: KeyEvent, keyCode: Int, port: Int): Int? {
        if (isN64) {
            // By name on every pad, as on the handheld: the printed letter picks the N64 input; R2 and SELECT send nothing.
            val printed = positionOf(event, keyCode)
            val button = PadKeymap.buttonFor(printed) ?: return printed
            return N64Input.toRunner(button, printed)
        }
        val held = event.deviceId to keyCode
        val sent = inputs[port].sent
        if (event.action == KeyEvent.ACTION_UP) return sent.up(held, send(event, keyCode))
        // Only the first down records; a key-repeat sends what it recorded.
        if (event.repeatCount > 0) return sent.held(held) ?: send(event, keyCode)
        return sent.down(held, send(event, keyCode))
    }

    private fun send(event: KeyEvent, keyCode: Int): Int = padMap.sendKey(positionOf(event, keyCode))

    /**
     * The console button [keyCode] is for this game, as its keycode ([PhysicalKeys.toRunner]): the printed letter
     * (Console Settings → Controller Layout names the pad's letters; Auto = the detected layout), through the core's
     * letter swap on DC/NGP, or the key's position on the positional consoles (PlayStation, Sega, arcade...).
     */
    private fun positionOf(event: KeyEvent, keyCode: Int): Int {
        val detected = detectedLayout(event)
        return PhysicalKeys.toRunner(gameTag, keyCode, detected, PadOptions.printedLayout(frontendValues, detected))
    }

    /** The letter printed on [keyCode] ([PhysicalKeys.printed]): what the in-game menus act on, so A confirms where A is printed. */
    private fun printedKey(event: KeyEvent, keyCode: Int): Int {
        val detected = detectedLayout(event)
        return PhysicalKeys.printed(keyCode, detected, PadOptions.printedLayout(frontendValues, detected))
    }

    /** Where [event]'s device has its face keycodes ([PadFaceLayout.detectDevice]), cached per device id. */
    private fun detectedLayout(event: KeyEvent): PadFaceLayout? =
        if (padLayouts.containsKey(event.deviceId)) padLayouts[event.deviceId] else
            event.device?.let { d -> PadFaceLayout.detectDevice(event.deviceId, d.isVirtual, d.vendorId, d.name.orEmpty()) }
                .also { padLayouts[event.deviceId] = it }

    val menuAxes = AxisDirections { menuRouter.onEvent(it) }   // the hat / left stick as held menu directions

    /** The on-screen stick: the game's left analog while playing, held menu directions while the menu is open. */
    val stickRouter = VirtualStickRouter(
        onAnalog = { x, y ->
            if (isN3ds && n3dsHotkeys.onStick(x, y)) syncN3dsCursor()
            else if (!(isNds && ndsHotkeys.onStick(x, y))) retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, 0)
        },
        onMenu = { menuRouter.onEvent(it) },
    )

    /**
     * Local multiplayer ([CoreRegistry.playersFor] > 1): which pad is which player; null for a one-player core, whose
     * pads and on-screen pad all drive port 0. Built at launch.
     */
    var slots: PlayerSlots? = null
    /**
     * Each port's live input ([PortInputs]); one port for a one-player core. Rebuilt with [slots]. Per port it holds the
     * L2/R2 from trigger axes and keys (with hysteresis, pressed once per pull: every core but the Dreamcast, analog)
     * and the left stick folded into the d-pad ([stickDpadStep], with hysteresis, on a core without a stick).
     */
    var inputs = PortInputs(1)
    /** Pad keys by device id: a removed device can no longer be asked for its descriptor. */
    val keysById = HashMap<Int, DeviceKey>()
    /** Pad names by key, so a held (disconnected) slot still shows its pad's name. */
    private val padNames = HashMap<DeviceKey, String>()

    private val ndsHotkeys = NdsHotkeys()        // a new activity, a new instance: stylus mode is off at every launch
    private val ndsHat = DpadHat()               // hardware hat directions the core has been given (the forwarded ones)
    private var ndsHatX = 0
    private var ndsHatY = 0
    private var sentPen: NdsPen? = null          // the pen as last sent (null = stylus off)
    private var penJob: Job? = null
    private var penDot: PenDot? = null           // the stylus cursor on main, over the GL surface ([positionPen])

    val n3dsHotkeys = N3dsHotkeys()     // a new activity, a new instance: stylus mode is off at every launch
    private var n3dsHatX = 0
    private var n3dsHatY = 0
    private var n3dsHwRight = 0f to 0f          // the hardware right stick as last read
    var n3dsPadRight = 0f to 0f         // the on-screen C-stick as last moved (New 3DS controls)
    private var n3dsSentRight = 0f to 0f        // the right stick as last sent to the core
    val n3dsStylusOn = mutableStateOf(false)   // stylus mode, as the pad's STYLUS pill shows it
    val ndsStylusOn = mutableStateOf(false)    // the DS's, likewise
    private var n3dsTickJob: Job? = null        // ticks the hotkeys while they ask ([N3dsHotkeys.ticking])

    /**
     * The core reads a pad's left stick as an analog stick ([STICK_CORES]). Any other core gets the stick folded into the
     * d-pad (onGenericMotionEvent).
     */
    private val coreHasStick: Boolean get() = gameCoreId in STICK_CORES   // netplay too: sticks are synced

    /** The DS STYLUS pill: L2's stylus toggle. */
    fun toggleNdsStylus() {
        applyNdsAction(ndsHotkeys.toggleStylus())
        syncPen()
    }

    /** The STYLUS pill: SELECT + L1's stylus toggle. */
    fun toggleN3dsStylus() {
        applyN3dsAction(n3dsHotkeys.toggleStylus())
        syncN3dsCursor()
    }

    /** The SWAP pill: Azahar's swap-screens key (L3), held as long as the pill; split, it swaps the displays on press. */
    fun onN3dsSwap(down: Boolean) {
        if (onN3dsSwapPressed(down)) return
        retroView?.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_THUMBL)
    }

    /** Dolphin is running a GameCube game, so the pad is the GameCube's ([PadProfile.Kind.GC]) and L / R are analog triggers. */
    val gcPad: Boolean get() = isDolphin && padProfile().kind == PadProfile.Kind.GC

    /** The GameCube pad's L and R: one tap/hold gesture, applied state, hold timer each. */
    private val gcGestures = mapOf(NxButton.L2 to TriggerGesture(), NxButton.R2 to TriggerGesture())
    private val gcStates = mutableMapOf(NxButton.L2 to TriggerGesture.State.OFF, NxButton.R2 to TriggerGesture.State.OFF)
    private val gcHoldTimers = mutableMapOf<NxButton, Job>()
    /** The soft-pressed sides, which the pad draws lit. */
    val gcLatched = mutableStateOf(emptySet<NxButton>())

    /** An on-screen L or R ([btn]) touch: a tap latches a soft press, holding presses it fully while held. */
    private fun gcTrigger(btn: NxButton, down: Boolean) {
        val gesture = gcGestures[btn] ?: return
        val now = SystemClock.uptimeMillis()
        if (down) {
            gcHoldTimers.remove(btn)?.cancel()
            gcApply(btn, gesture.down(now))
            gcHoldTimers[btn] = lifecycleScope.launch {
                delay(gesture.holdMs)
                gcApply(btn, gesture.tick(now + gesture.holdMs))   // the timer is the hold: FULL even if delay resumed a hair early
            }
        } else {
            gcHoldTimers.remove(btn)?.cancel()
            gcApply(btn, gesture.up(now))
        }
    }

    /** Sends [btn]'s trigger at [state]: soft is the analog half only, full also holds the digital key, off lets both go. */
    private fun gcApply(btn: NxButton, state: TriggerGesture.State) {
        val was = gcStates[btn]
        gcStates[btn] = state
        gcLatched.value = gcStates.filterValues { it == TriggerGesture.State.SOFT }.keys
        val view = retroView ?: return
        fun level(b: NxButton) = when (gcStates[b]) { TriggerGesture.State.SOFT -> 0.5f; TriggerGesture.State.FULL -> 1f; else -> 0f }
        view.sendTriggers(0, level(NxButton.L2), level(NxButton.R2))
        val full = state == TriggerGesture.State.FULL
        if (full != (was == TriggerGesture.State.FULL)) {
            PadKeymap.keyCodeFor(btn)?.let { view.sendKeyEvent(if (full) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, it) }
        }
    }

    /** Lets both GameCube triggers go and forgets the latches (the menu opening, a profile change, quitting). */
    fun gcClearTriggers() {
        gcHoldTimers.values.forEach { it.cancel() }
        gcHoldTimers.clear()
        for ((btn, gesture) in gcGestures) {
            gesture.clear()
            if (gcStates[btn] != TriggerGesture.State.OFF) gcApply(btn, TriggerGesture.State.OFF)
        }
    }

    /** The on-screen Classic Controller's (or GameCube C-) right stick: the core's right analog on port 0. */
    fun onWiiRightStick(x: Float, y: Float) {
        if (menuOpen) return
        retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y, 0)
    }

    /** NDS hotkeys and stylus for one button event; true when it goes on to the core. A key repeat only asks whether its press did. */
    private fun routeNds(button: NxButton, down: Boolean, repeat: Boolean = false): Boolean {
        if (repeat) return !ndsHotkeys.consumes(button)
        val decision = ndsHotkeys.onButton(button, down)
        decision.action?.let(::applyNdsAction)
        if (!decision.forward) syncPen()
        return decision.forward
    }

    fun applyNdsAction(action: NdsAction) {
        val landscape = isLandscape()
        when (action) {
            NdsAction.SwapScreens -> setNdsKey(NdsOptions.swapBigScreen(frontendValues))
            is NdsAction.CycleLayout -> {
                // From the layout showing on this screen (an unset row steps from its default).
                setNdsKey(NdsOptions.cycleLayout(
                    frontendValues, landscape, ndsControllerMode(), action.delta, host.gameContainer.resources.screenClass(),
                ))
            }
            is NdsAction.CycleCorner -> NdsOptions.cycleCorner(frontendValues, action.delta).let { (key, value) ->
                setNdsKey(key to value, "PiP: " + NdsOptions.valueLabel(key, value))
            }
            is NdsAction.Stylus -> {
                ndsStylusOn.value = action.on
                if (action.on) releaseStylusInputs()
                showNdsLabel(if (action.on) "Stylus on" else "Stylus off")
            }
        }
    }

    /**
     * A hotkey's layout change: applied now, shown as [label] (the new value's by default), and saved to the game's config.
     * A key locked in the .cfg is left alone.
     */
    private fun setNdsKey(change: Pair<String, String>, label: String = NdsOptions.valueLabel(change.first, change.second)) {
        val (key, value) = change
        if (key in lockedOptions) return
        applyFrontend(frontendValues + change)
        showNdsLabel(label)
        host.persistGameKey(key, value, "Could not save the screen layout")
    }

    private fun showNdsLabel(text: String) {
        notifications.push(text, null, NDS_LABEL_MS, SystemClock.elapsedRealtime())
        publishNotifications()
    }

    /** The d-pad, left stick and A now drive the pen: the core must not keep what they held. */
    private fun releaseStylusInputs() {
        val view = retroView ?: return
        releaseDirections(view)
        PadKeymap.keyCodeFor(NxButton.A)?.let { view.sendKeyEvent(KeyEvent.ACTION_UP, it) }
    }

    /** Sends the pen when it changed (pressed, or just lifted), moves the cursor, and keeps the pen ticking in stylus mode. */
    fun syncPen() {
        val view = retroView ?: return
        val pen = ndsHotkeys.pen.takeIf { ndsHotkeys.stylus }
        if (pen != sentPen) {
            when {
                pen != null && pen.pressed -> view.sendPointer(pen.frameX, pen.frameY, true)
                sentPen?.pressed == true -> view.sendPointer(0f, 0f, false)
            }
            sentPen = pen
        }
        positionPen()
        if (!ndsHotkeys.stylus) {
            penJob?.cancel()
            penJob = null
            return
        }
        if (penJob?.isActive == true) return
        penJob = lifecycleScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (true) {
                delay(PEN_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                if (!menuOpen && ndsHotkeys.tick(now - last)) syncPen()
                last = now
            }
        }
    }

    /**
     * The stylus cursor over the bottom screen wherever it is shown ([DualScreen.penOn]): on main (the inset included), or
     * on the second display when the bottom screen is drawn there; hidden on the other display, and without stylus mode.
     */
    fun positionPen() {
        val on = if (ndsHotkeys.stylus) DualScreen.penOn(ndsRegions, secondNdsRegions) else null
        val pen = ndsHotkeys.pen
        val mainRegion = ndsRegions.firstOrNull { it.screen == NdsScreen.BOTTOM }.takeIf { on == PictureOn.MAIN }
        if (mainRegion == null) penDot?.hide() else {
            val (cx, cy) = NdsLayout.pointIn(mainRegion, pen.x, pen.y)
            // Above the GL surface in the game container.
            (penDot ?: PenDot(host.gameContainer).also { penDot = it }).show(cx, cy, pen.pressed)
        }
        val secondRegion = secondNdsRegions.firstOrNull { it.screen == NdsScreen.BOTTOM }.takeIf { on == PictureOn.SECOND }
        presentation?.let { p ->
            if (secondRegion == null) p.hidePen() else {
                val (cx, cy) = NdsLayout.pointIn(secondRegion, pen.x, pen.y)
                p.showPen(cx, cy, pen.pressed)
            }
        }
    }

    /**
     * Hardware hat (or, outside stylus mode, left-stick) edges as direction presses through the hotkeys; the forwarded
     * ones reach the core as its d-pad. This replaces the d-pad keys Android would synthesise from the event.
     */
    private fun routeNdsHat(view: GLRetroView, event: MotionEvent) {
        val x = ndsHotkeys.joystickDirection(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_X))
        val y = ndsHotkeys.joystickDirection(event.getAxisValue(MotionEvent.AXIS_HAT_Y), event.getAxisValue(MotionEvent.AXIS_Y))
        var changed = false
        fun edge(old: Int, new: Int, negative: NxButton, positive: NxButton) {
            if (old == new) return
            val released = when (old) { -1 -> negative; 1 -> positive; else -> null }
            val pressed = when (new) { -1 -> negative; 1 -> positive; else -> null }
            if (released != null && routeNds(released, down = false)) changed = ndsHat.release(released) || changed
            if (pressed != null && routeNds(pressed, down = true)) changed = ndsHat.press(pressed) || changed
        }
        edge(ndsHatX, x, NxButton.LEFT, NxButton.RIGHT)
        edge(ndsHatY, y, NxButton.UP, NxButton.DOWN)
        ndsHatX = x
        ndsHatY = y
        if (changed) view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, ndsHat.x.toFloat(), ndsHat.y.toFloat(), 0)
    }

    /** Picks the 3DS hotkeys' core values out of [values] (launch, a menu change, a restore); other keys are ignored. */
    fun n3dsCoreValues(values: Map<String, String>) {
        values[N3dsOptions.DEADZONE]?.let { n3dsHotkeys.deadzone = N3dsOptions.deadzone(it) }
    }

    /**
     * 3DS hotkeys and stylus for one button event; true when it goes on to the core as is. What it becomes instead (L3, R3, a
     * held-back SELECT) is sent here. A key repeat only asks whether its press did.
     */
    private fun routeN3ds(button: NxButton, down: Boolean, repeat: Boolean = false): Boolean {
        if (repeat) return !n3dsHotkeys.consumes(button)
        val decision = n3dsHotkeys.onButton(button, down)
        decision.action?.let(::applyN3dsAction)
        syncN3dsCursor()
        return decision.forward
    }

    /** The keys the 3DS hotkeys produced: L3 (Azahar's swap screens), R3 (its touch at the cursor), the game's SELECT. */
    private fun sendN3dsKeys() {
        val view = retroView
        for (e in n3dsHotkeys.takeKeys()) {
            if (e.key == N3dsKey.L3 && onN3dsSwapPressed(e.down)) continue   // split: SELECT + R swaps the displays, as the pill does
            val code = when (e.key) {
                N3dsKey.L3 -> KeyEvent.KEYCODE_BUTTON_THUMBL
                N3dsKey.R3 -> KeyEvent.KEYCODE_BUTTON_THUMBR
                N3dsKey.SELECT -> KeyEvent.KEYCODE_BUTTON_SELECT
            }
            view?.sendKeyEvent(if (e.down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code)
        }
    }

    private fun applyN3dsAction(action: N3dsAction) {
        when (action) {
            is N3dsAction.CycleLayout -> {
                // The row in use (orientation, controller), as the DS's; applyFrontend sets the core's layout from it.
                val (key, value) = N3dsOptions.cycleLayout(frontendValues, isLandscape(), ndsControllerMode(), action.delta)
                if (key in lockedOptions) return
                applyFrontend(frontendValues + (key to value))   // Azahar re-lays its screens out on its next frame
                showNdsLabel(N3dsOptions.layoutLabel(value))
                host.persistGameKey(key, value, "Could not save the screen layout")
            }
            is N3dsAction.Stylus -> {
                n3dsStylusOn.value = action.on
                syncN3dsCore()   // the cursor stays shown while stylus mode is on
                // On: the d-pad, left stick and A now drive the cursor, so the core must not keep what they held. Off: the
                // hotkeys lift a touch held, and syncN3dsCursor gives the right stick back.
                if (action.on) releaseStylusInputs()
                showNdsLabel(if (action.on) "Stylus on" else "Stylus off")
            }
        }
    }

    /**
     * Sends the hotkeys' keys and the right stick when it changed: stylus mode's cursor ([N3dsHotkeys.cursor]), else the
     * hardware stick. Keeps ticking the hotkeys while they ask (a SELECT held toward its 1 s cancel or tapping, a held d-pad
     * cursor speeding up).
     */
    fun syncN3dsCursor() {
        sendN3dsKeys()
        val view = retroView ?: return
        // The on-screen C-stick while it is off centre, else the hardware stick.
        val right = n3dsHotkeys.cursor ?: n3dsPadRight.takeIf { it != (0f to 0f) } ?: n3dsHwRight
        if (right != n3dsSentRight) {
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, right.first, right.second, 0)
            n3dsSentRight = right
        }
        if (!n3dsHotkeys.ticking || n3dsTickJob?.isActive == true) return
        n3dsTickJob = lifecycleScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (n3dsHotkeys.ticking) {
                delay(PEN_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                if (!menuOpen) n3dsHotkeys.tick(now - last)
                last = now
                syncN3dsCursor()
            }
            n3dsTickJob = null
        }
    }

    /** Hardware hat edges as direction presses through the 3DS hotkeys; the forwarded ones reach the core as its d-pad. */
    private fun routeN3dsHat(view: GLRetroView, event: MotionEvent) {
        val x = n3dsHotkeys.joystickDirection(event.getAxisValue(MotionEvent.AXIS_HAT_X))
        val y = n3dsHotkeys.joystickDirection(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        var changed = false
        fun edge(old: Int, new: Int, negative: NxButton, positive: NxButton) {
            if (old == new) return
            val released = when (old) { -1 -> negative; 1 -> positive; else -> null }
            val pressed = when (new) { -1 -> negative; 1 -> positive; else -> null }
            if (released != null && routeN3ds(released, down = false)) changed = ndsHat.release(released) || changed
            if (pressed != null && routeN3ds(pressed, down = true)) changed = ndsHat.press(pressed) || changed
        }
        edge(n3dsHatX, x, NxButton.LEFT, NxButton.RIGHT)
        edge(n3dsHatY, y, NxButton.UP, NxButton.DOWN)
        n3dsHatX = x
        n3dsHatY = y
        if (changed) view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, ndsHat.x.toFloat(), ndsHat.y.toFloat(), 0)
    }

    /**
     * L2/R2 pulled as trigger axes (AXIS_LTRIGGER/AXIS_BRAKE, AXIS_RTRIGGER/AXIS_GAS): each pull, with hysteresis, is one
     * press and one release ([TriggerAxes]), to the DS hotkeys, the 3DS hotkeys (ZL/ZR, always forwarded) or the core as
     * the L2/R2 key. A trigger that has also come as a key event goes by its keys alone ([routeTriggerKey]). Not for the
     * Dreamcast, whose triggers are analog.
     */
    private fun routeTriggerAxes(event: MotionEvent, port: Int = 0) {
        val l = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val r = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        for ((button, down) in inputs[port].triggers.onAxes(l, r)) sendAxisTrigger(button, down, port)
    }

    /** An axis trigger's press or release: to the DS hotkeys, the 3DS hotkeys (ZL/ZR, always forwarded) or the core. */
    private fun sendAxisTrigger(button: NxButton, down: Boolean, port: Int) {
        when {
            isNds -> routeNds(button, down)
            isN3ds -> if (routeN3ds(button, down)) sendTriggerKey(button, down)
            else -> sendTriggerKey(button, down, port)
        }
    }

    /**
     * A shortcut fired ([down]) or ended on [keyCode]: an L2/R2 pull that is the shortcut's keeps its axis out of the core
     * too ([TriggerAxes.take]), as a pad may send both. One its axis pressed first is let go; an analog one reads at rest.
     */
    private fun takeTrigger(keyCode: Int, port: Int, down: Boolean) {
        val button = PadKeymap.buttonFor(keyCode)?.takeIf { it == NxButton.L2 || it == NxButton.R2 } ?: return
        val t = inputs[port].triggers
        if (!down) { t.untake(button); return }
        val pressed = t.take(button)
        if (isDc || gcPad) retroView?.let { v -> t.analogNow().let { (l, r) -> v.sendTriggers(port, l, r) } }
        else if (pressed) sendAxisTrigger(button, false, port)
    }

    /**
     * L2/R2 as a key event: true when it goes on (the DS: to its hotkeys; others: on to the core as any key). From the
     * first one on, that trigger's axes are ignored, so a controller that sends both a key and an axis for one pull
     * toggles once; a pull already pressed by its axis is not pressed again.
     */
    private fun routeTriggerKey(button: NxButton, down: Boolean, port: Int = 0): Boolean = inputs[port].triggers.onKey(button, down)

    /**
     * A GameCube game drops the L2/R2 key of a pad that also reports that trigger as an axis: Dolphin's full press is
     * `Trigger0+ | (L2 & !Trigger0+)`, so a key sent on a light pull clicked fully at once. The analog alone drives it
     * (onGenericMotionEvent) and Dolphin's own threshold makes the click. A pad without trigger axes keeps its key.
     */
    private fun gcAnalogTriggerKey(button: NxButton, device: InputDevice?): Boolean {
        if (!gcPad || device == null) return false
        val axes = when (button) {
            NxButton.L2 -> intArrayOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE)
            NxButton.R2 -> intArrayOf(MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS)
            else -> return false
        }
        return axes.any { device.getMotionRange(it) != null }
    }

    /** An axis trigger's press or release to the core, as its L2/R2 key would be sent (by name on N64, else the pad map). */
    private fun sendTriggerKey(button: NxButton, down: Boolean, port: Int = 0) {
        val keyCode = PadKeymap.keyCodeFor(button) ?: return
        val code = if (isN64) N64Input.toRunner(button, keyCode) else padMap.sendKey(keyCode)
        code?.let { retroView?.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, it, port) }
    }

    /** The activity's onStop: no pen or 3DS hotkey tick in the background; onStart restarts them ([syncPen], [syncN3dsCursor]). */
    fun onStop() {
        penJob?.cancel()   // no 60 Hz pen tick in the background; onStart restarts it (stylus mode itself is kept)
        penJob = null
        if (isN3ds) {   // backgrounded: no held-back SELECT or forwarded key left hanging, and no tick
            n3dsHotkeys.releaseAll()
            sendN3dsKeys()
        }
        n3dsTickJob?.cancel()
        n3dsTickJob = null
        releaseShortcuts()   // backgrounded: a Hold FF / Hold Rewind never hears its release
    }

    /** Release any held game inputs so nothing sticks in the core while the menu is up. */
    fun releaseAllGameInput() {
        // Port 0 keeps its folded stick d-pad state; the other players' ports are released in full.
        val p0 = inputs[0]
        p0.sent.clear(); padSent.clear()
        p0.triggers.releaseAll()
        p0.selectHeld = false
        p0.startHeld = false
        for (p in 1 until inputs.count) releasePort(p)
        val view = retroView ?: return
        RELEASED_BUTTONS.forEach { b -> PadKeymap.keyCodeFor(b)?.let { view.sendKeyEvent(KeyEvent.ACTION_UP, it) } }
        RELEASED_THUMB_KEYS.forEach { view.sendKeyEvent(KeyEvent.ACTION_UP, it) }
        releaseDirections(view)
        // An analog trigger value wins over the keys in the runner and the menu swallows the release axis: zero it.
        if (isDc || gcPad) view.sendTriggers(0, 0f, 0f)
        if (gcPad) gcClearTriggers()
        if (isWii) view.setPointerVelocity(0, 0f, 0f)   // the menu swallows the stick's return to rest: the pointer stops
        ndsHotkeys.releaseAll()
        ndsHatX = 0
        ndsHatY = 0
        syncPen()
        releaseShortcuts()   // their releases go to the menu now
        if (isN3ds) {
            n3dsHotkeys.releaseAll()   // sends up what it held in the core (L3, R3, a tapping SELECT); a held-back SELECT is dropped
            n3dsHatX = 0
            n3dsHatY = 0
            n3dsHwRight = 0f to 0f
            n3dsPadRight = 0f to 0f
            syncN3dsCursor()
        }
    }

    /**
     * The shortcut keys' releases will not come here: nothing held, a bound L2/R2 is the game's again ([TriggerAxes.untakeAll]),
     * and a Hold FF / Hold Rewind ends (a toggled one stays on).
     */
    private fun releaseShortcuts() {
        host.shortcutKeys?.releaseAll()
        inputs[0].triggers.untakeAll()   // a bound L2/R2's pull ends with it: its analog must not stay at rest
        host.releaseShortcutHolds()
    }

    /** Lets go of everything [port] held in the core: its keys, directions, sticks and DC or GameCube analog triggers. */
    fun releasePort(port: Int) {
        inputs[port].reset()
        if (port == 0) releaseShortcuts()   // P1's pad went: a SELECT or Hold FF held on it must not stay held
        val view = retroView ?: return
        RELEASED_BUTTONS.forEach { b -> PadKeymap.keyCodeFor(b)?.let { view.sendKeyEvent(KeyEvent.ACTION_UP, it, port) } }
        RELEASED_THUMB_KEYS.forEach { view.sendKeyEvent(KeyEvent.ACTION_UP, it, port) }
        releaseDirections(view, port)
        view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, port)
        if (isWii) view.setPointerVelocity(port, 0f, 0f)   // an unplugged pad's tilted stick must not keep moving the pointer
        if (isDc || gcPad) view.sendTriggers(port, 0f, 0f)
    }

    /**
     * Lets go of every direction [port] holds in the core: the d-pad (as a hat and as keys) and the left stick. Port 0
     * also clears the on-screen and DS hats, which are only ever port 0's.
     */
    private fun releaseDirections(view: GLRetroView, port: Int = 0) {
        if (port == 0) {
            dpadHat.clear()
            ndsHat.clear()
        }
        view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f, port)
        view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f, port)
        listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
            .forEach { view.sendKeyEvent(KeyEvent.ACTION_UP, it, port) }
    }

    /** Drives the open menu from a hardware hat / left-stick (vertical = item, horizontal = slot). */
    private fun routeMenuMotion(event: MotionEvent) {
        menuAxes.update(
            event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y),
            event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
        )
    }

    // ------------------------------------------------------------------ on-screen pad input

    fun onPad(event: PadEvent) {
        // The menus follow the letter the on-screen pad shows (Dreamcast draws "A" on NxButton.B); the game gets the raw button.
        rumbles.firstOrNull()?.useDevice(null)   // the on-screen pad: the phone in the player's hands takes the rumble
        if (menuOpen) { menuRouter.onEvent(event.map(padProfile()::menuButton)); return }
        val btn = event.button
        val down = event is PadEvent.Down
        // MENU opens the pause menu (not forwarded to the core).
        if (btn == NxButton.MENU) { if (down) toggleMenu(); return }
        // The Wii's HOME: a resume point first, then a tap of the core's Home ([pressWiiHome]); the release is the tap's.
        if (btn == NxButton.HOME) { if (down && isWii) pressWiiHome(); return }
        // SELECT + START held together also opens it (the on-screen pad is P1, port 0).
        if (inputs.menuChord(0, btn, down)) { toggleMenu(); return }
        if (isNds && !routeNds(btn, down)) return   // R2, L2, SELECT + a direction and the stylus inputs stay out of the core
        if (isN3ds && !routeN3ds(btn, down)) return  // SELECT (held back for chords), its chords and the stylus inputs: routeN3ds sends what they become
        val view = retroView ?: return
        if (gcPad && (btn == NxButton.L2 || btn == NxButton.R2)) { gcTrigger(btn, down); return }
        if (btn.isDirection) {
            if (down) dpadHat.press(btn) else dpadHat.release(btn)
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, dpadHat.x.toFloat(), dpadHat.y.toFloat(), 0)
        } else {
            val code = if (isN64) N64Input.toRunner(btn, PadKeymap.keyCodeFor(btn) ?: 0)
                else PadKeymap.keyCodeFor(padMap.sends(btn))
            if (code == null || code == 0) return
            val sent = if (down) padSent.down(btn, code) else padSent.up(btn, code)
            view.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, sent)
        }
    }

    fun onStick(x: Float, y: Float) = stickRouter.onStick(x, y, menuOpen)

    /** The on-screen New 3DS C-stick: the core's right analog (Azahar's C-stick, and its pointer in the default mode). */
    fun onCStick(x: Float, y: Float) {
        if (menuOpen || !isN3ds) return
        n3dsPadRight = x to y
        syncN3dsCursor()
    }

    fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        // The rumble follows the pad in use, on its own port (a one-player core: port 0); a pad with no slot yet claims below.
        if (event != null && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) portFor(event.device, claim = false)?.let { noteRumbleDevice(event.device, it) }
        if (menuOpen) { event?.let { routeMenuMotion(it) }; return true }
        val view = retroView ?: return host.superGenericMotionEvent(event)
        if (event != null && isN3ds) {
            if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
                routeN3dsHat(view, event)
                routeTriggerAxes(event)     // L2/R2 are ZL/ZR: an axis-only trigger presses them too
                val sx = event.getAxisValue(MotionEvent.AXIS_X)
                val sy = event.getAxisValue(MotionEvent.AXIS_Y)
                if (!n3dsHotkeys.onStick(sx, sy)) view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, sx, sy, 0)
                n3dsHwRight = event.getAxisValue(MotionEvent.AXIS_Z) to event.getAxisValue(MotionEvent.AXIS_RZ)
                syncN3dsCursor()
                return true   // consumed, as for the DS: no synthesised d-pad keys passing routeN3ds twice
            }
            return host.superGenericMotionEvent(event)
        }
        if (event != null && isNds) {
            // Joystick events only: a mouse or hover event reads its hat, trigger and stick axes as 0, which would
            // release a held hat, re-fire a trigger or move the pen.
            if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
                routeNdsHat(view, event)
                routeTriggerAxes(event)
                val sx = event.getAxisValue(MotionEvent.AXIS_X)
                val sy = event.getAxisValue(MotionEvent.AXIS_Y)
                if (!ndsHotkeys.onStick(sx, sy)) view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, sx, sy, 0)
                view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ), 0)
                // Consumed: unhandled, Android would synthesise d-pad keys from the hat and stick, and they would pass
                // routeNds a second time (a SELECT chord firing twice). routeNdsHat sends those directions itself.
                return true
            }
            return host.superGenericMotionEvent(event)
        }
        // Joystick events only, and consumed. Unhandled, Android would synthesise d-pad keys from the hat and the left
        // stick, which would reach the core through onKeyDown as a second d-pad (a stick tilt also pressing the d-pad).
        // The hat goes as MOTION_SOURCE_DPAD here, and on a core without an analog stick the stick too (folded in below,
        // coreHasStick); the open menu reads the axes itself (routeMenuMotion, above). A mouse or hover event is not a
        // pad: its AXIS_X/Y are pointer coordinates and its hat reads 0 (it would release a held hat), so it goes to the
        // system.
        if (event != null && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            // A pad with no player slot claims one only with an axis pushed past half (not its resting report on connect).
            // The right stick (AXIS_Z/RZ) never claims: some pads rest those at -1.
            // A one-player core (no slots) builds nothing: portFor gives port 0 whatever the claim.
            val claims = slots != null && axesClaim(floatArrayOf(
                event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE), event.getAxisValue(MotionEvent.AXIS_GAS),
            ))
            val port = portFor(event.device, claim = claims) ?: return true   // no slot: dropped
            noteRumbleDevice(event.device, port)
            val inp = inputs[port]
            // Dreamcast triggers are analog (flycast reads RETRO_DEVICE_INDEX_ANALOG_BUTTON).
            if (isDc || gcPad) {   // GameCube triggers too: Dolphin reads them as analog buttons
                val l = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
                val r = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
                inp.triggers.analog(l, r).let { (al, ar) -> view.sendTriggers(port, al, ar) }   // a shortcut's pull reads at rest
            } else {
                routeTriggerAxes(event, port)   // a digital L2/R2 from a pad that reports its triggers only as axes
            }
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val sx = event.getAxisValue(MotionEvent.AXIS_X)
            val sy = event.getAxisValue(MotionEvent.AXIS_Y)
            if (coreHasStick) {
                view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, hatX, hatY, port)
            } else {
                // The runner's JOYPAD d-pad is the hat axes and keys only, and consuming the event drops the d-pad keys
                // Android would make from the stick, so a digital core's stick is folded in here.
                inp.stickDpadX = stickDpadStep(sx, inp.stickDpadX)
                inp.stickDpadY = stickDpadStep(sy, inp.stickDpadY)
                view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, hatOrStick(hatX, inp.stickDpadX).toFloat(), hatOrStick(hatY, inp.stickDpadY).toFloat(), port)
            }
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, sx, sy, port)
            val rz = event.getAxisValue(MotionEvent.AXIS_Z) to event.getAxisValue(MotionEvent.AXIS_RZ)
            if (isWii && wiiPad != null && wiiPad != WiiPad.CLASSIC) {
                // Wiimote types: the right stick aims the IR pointer (Dolphin's pointer reads POINTER, ir_mode 2).
                val (vx, vy) = WiiPointer.velocity(rz.first, rz.second)
                view.setPointerVelocity(port, vx, vy)
            } else {
                view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, rz.first, rz.second, port)
            }
            return true
        }
        return host.superGenericMotionEvent(event)
    }

    // Volume keys are never pad buttons (PadKeymap has none): they go to the system, not the core or the menu.
    private fun isVolumeKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
        keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE

    /** A real controller's input on [port]: that port's rumble follows it (to its vibrator; port 0: else the phone's). */
    private fun noteRumbleDevice(device: InputDevice?, port: Int) {
        if (device != null && !device.isVirtual) rumbles.getOrNull(port)?.useDevice(device.id)
    }

    /** A physical pad: not virtual, with gamepad or joystick sources (a DualSense's touchpad device is neither). */
    fun isPad(d: InputDevice): Boolean = !d.isVirtual && (
        (d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (d.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)

    /**
     * The port [device]'s input goes to: 0 for a one-player core, the on-screen pad or a non-pad device; else its
     * player slot, claimed when [claim] (a key-down, an axis past half) and it has none. Null drops the input: every
     * slot is taken, or the pad is benched (its slot set to Empty on the Players page).
     */
    private fun portFor(device: InputDevice?, claim: Boolean): Int? {
        val s = slots ?: return 0
        if (device == null || !isPad(device)) return 0
        val key = DeviceKey(device.descriptor)
        keysById[device.id] = key
        padNames[key] = device.name
        if (!claim) return s.portOf(key)
        val c = s.claim(key) ?: return null
        if (c.joined) { onPlayerJoined(c.port, device); connectDolphinPorts() }
        return c.port
    }

    /** A pad took (or came back to) player slot [port]: a notice says who is who. */
    private fun onPlayerJoined(port: Int, device: InputDevice) {
        notifications.push("Player ${port + 1}: ${device.name}", null, PLAYER_NOTICE_MS, SystemClock.elapsedRealtime())
        publishNotifications()
    }

    /** The Players page's rows: each slot (Empty, a live pad, a held one) and each connected pad, names numbered; null without slots. */
    fun playerRows(): Pair<List<PlayerSlotRow>, List<PlayerPadRow>>? {
        val s = slots ?: return null
        val pads = connectedPads()
        pads.forEach {
            val key = DeviceKey(it.descriptor)
            keysById[it.id] = key   // so a SetPlayer finds the rumble of a pad never pressed
            padNames[key] = it.name
        }
        val keys = pads.map { DeviceKey(it.descriptor) }
        val numbered = keys.zip(PadNames.numbered(pads.map { it.name })).toMap()
        fun name(k: DeviceKey) = numbered[k] ?: padNames[k] ?: "Controller"
        return s.slots().map { v ->
            when (v) {
                SlotView.Empty -> PlayerSlotRow(null, "", connected = false)
                is SlotView.Playing -> PlayerSlotRow(v.key.value, name(v.key), connected = true)
                is SlotView.Held -> PlayerSlotRow(v.key.value, name(v.key), connected = false)
            }
        } to keys.map { k -> PlayerPadRow(k.value, name(k)) }
    }

    /** The connected physical pads, one per descriptor (a pad may show as several input devices). */
    private fun connectedPads(): List<InputDevice> {
        val im = inputManager ?: return emptyList()
        return im.inputDeviceIds.toList().mapNotNull(im::getInputDevice).filter(::isPad).distinctBy { it.descriptor }
    }

    /** Acts on [ShortcutKeys]' [verdict] for [keyCode] on [port]; true when the key is taken (it goes no further). */
    private fun shortcut(verdict: KeyVerdict, keyCode: Int = 0, port: Int = 0): Boolean = when (verdict) {
        KeyVerdict.Pass -> false
        KeyVerdict.Swallow -> true
        KeyVerdict.OpenMenu -> { toggleMenu(); true }
        is KeyVerdict.Fire -> { takeTrigger(keyCode, port, verdict.down); host.onShortcut(verdict.action, verdict.down); true }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode)) return host.superKeyDown(keyCode, event)
        // The rumble follows the pad in use, on its own port (a one-player core: port 0); a pad with no slot yet claims below.
        portFor(event.device, claim = false)?.let { noteRumbleDevice(event.device, it) }
        if (menuOpen) {
            // Press to bind first, on the raw code (as [ShortcutKeys] matches it in game), Back too: it cancels.
            if (host.menuCaptureKey(keyCode, down = true, repeat = event.repeatCount > 0)) return true
            if (keyCode == KeyEvent.KEYCODE_BACK) { if (event.repeatCount == 0) backPressed(); return true }
            if (event.repeatCount == 0) PadKeymap.buttonFor(printedKey(event, keyCode))?.let { menuRouter.onEvent(PadEvent.Down(it)) }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) { if (event.repeatCount == 0) backPressed(); return true }
        val first = event.repeatCount == 0
        val button = PadKeymap.buttonFor(keyCode)
        // Any pad can pause, before the slot lookup: also one with no slot (table full) or benched. It claims nothing. With a
        // MENU combo bound, MENU is a modifier first: the menu opens on its release if no combo fired ([ShortcutKeys]).
        if (button == NxButton.MENU) {
            val keys = host.shortcutKeys
            if (keys?.holdsMenu == true) keys.onDown(keyCode, repeat = !first) else if (first) toggleMenu()
            return true
        }
        val port = portFor(event.device, claim = true) ?: return true   // no slot: dropped
        // Wii: the left-stick click recentres the phone's motion, the right-stick click is HOME ([pressWiiHome]); neither
        // reaches the core as itself.
        if (WiiShortcuts.isRecenter(isWii, keyCode)) { if (first) recenterWiiMotion(); return true }
        if (WiiShortcuts.isHome(isWii, keyCode)) { if (first) pressWiiHome(); return true }
        // 3DS split: the left-stick click is SWAP between the displays.
        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL && onN3dsSwapPressed(down = first)) return true
        noteRumbleDevice(event.device, port)
        // SELECT + START opens the menu: P1's (port 0's) only. Held state follows every down, repeats too.
        if (button != null && inputs.menuChord(port, button, down = true) && first) { toggleMenu(); return true }
        // Shortcuts, P1's (port 0's) only, as the menu chord: after the built-in hotkeys above, which they can't bind
        // ([com.nxredux.mobile.shortcuts.EffectiveShortcuts.blocked]), before the DS / 3DS routing and the core.
        if (port == 0) host.shortcutKeys?.let { keys -> if (shortcut(keys.onDown(keyCode, repeat = !first), keyCode, port)) return true }
        // The hotkeys and stylus see the console button the key is (its printed letter on DS/3DS), as the core will.
        if (isNds) PadKeymap.buttonFor(positionOf(event, keyCode))?.let { b ->
            if (b == NxButton.L2 || b == NxButton.R2) { if (first && routeTriggerKey(b, down = true)) routeNds(b, down = true); return true }
            if (!routeNds(b, down = true, repeat = !first)) return true
        }
        if (gcPad) PadKeymap.buttonFor(positionOf(event, keyCode))?.let { b -> if (gcAnalogTriggerKey(b, event.device)) return true }
        if (!isNds && !isDc && first) PadKeymap.buttonFor(positionOf(event, keyCode))?.let { b ->
            if ((b == NxButton.L2 || b == NxButton.R2) && !routeTriggerKey(b, down = true, port)) return true   // its axis pressed it
        }
        if (isN3ds) PadKeymap.buttonFor(positionOf(event, keyCode))?.let { b ->
            if (!routeN3ds(b, down = true, repeat = !first)) return true
        }
        // As printed (or by position on positional consoles) through the console's pad map; N64 by name ([runnerKey]).
        runnerKey(event, keyCode, port)?.let { retroView?.sendKeyEvent(event.action, it, port) }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode)) return host.superKeyUp(keyCode, event)
        if (menuOpen) {
            if (host.menuCaptureKey(keyCode, down = false, repeat = false)) return true
            PadKeymap.buttonFor(printedKey(event, keyCode))?.let { menuRouter.onEvent(PadEvent.Up(it)) }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        // A held-back MENU (a MENU combo is bound): its release opens the menu if no combo fired, from any pad as its press.
        if (PadKeymap.buttonFor(keyCode) == NxButton.MENU) host.shortcutKeys?.takeIf { it.holdsMenu }?.let { shortcut(it.onUp(keyCode)); return true }
        val port = portFor(event.device, claim = false) ?: return true
        if (WiiShortcuts.isRecenter(isWii, keyCode) || WiiShortcuts.isHome(isWii, keyCode)) return true
        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL && onN3dsSwapPressed(down = false)) return true
        PadKeymap.buttonFor(keyCode)?.let { inputs.menuChord(port, it, down = false) }
        if (port == 0) host.shortcutKeys?.let { keys -> if (shortcut(keys.onUp(keyCode), keyCode, port)) return true }
        if (isNds) PadKeymap.buttonFor(positionOf(event, keyCode))?.let { b ->   // as on the down
            if (b == NxButton.L2 || b == NxButton.R2) { if (routeTriggerKey(b, down = false)) routeNds(b, down = false); return true }
            if (!routeNds(b, down = false)) return true
        }
        if (gcPad) PadKeymap.buttonFor(positionOf(event, keyCode))?.let { b -> if (gcAnalogTriggerKey(b, event.device)) return true }
        if (!isNds && !isDc) PadKeymap.buttonFor(positionOf(event, keyCode))?.let { b ->
            if ((b == NxButton.L2 || b == NxButton.R2) && !routeTriggerKey(b, down = false, port)) return true   // not held
        }
        if (isN3ds) PadKeymap.buttonFor(positionOf(event, keyCode))?.let { b ->
            if (!routeN3ds(b, down = false)) return true
        }
        // As printed (or by position on positional consoles) through the console's pad map; N64 by name ([runnerKey]).
        runnerKey(event, keyCode, port)?.let { retroView?.sendKeyEvent(event.action, it, port) }
        return true
    }
}

/** The buttons a release lets go of in the core ([GameInputRouter.releaseAllGameInput], [GameInputRouter.releasePort]). */
private val RELEASED_BUTTONS = listOf(
    NxButton.A, NxButton.B, NxButton.X, NxButton.Y, NxButton.L1, NxButton.L2,
    NxButton.R1, NxButton.R2, NxButton.START, NxButton.SELECT, NxButton.MENU,
)

/**
 * The thumbstick clicks (L3, R3) a release also lets go of: no [NxButton] has them. A pad's clicks reach the core as these
 * same keycodes on every console (no pad map or face swap touches them), so the release matches what was sent.
 */
private val RELEASED_THUMB_KEYS = listOf(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR)

/**
 * One axis of a gamepad's left stick as a d-pad direction (-1, 0, 1), given the direction [held] from the last event:
 * pressed past [press], kept until it falls back under [release] (the gap keeps a stick resting near the line from
 * chattering). Pressed straight across, it flips without a release in between.
 */
internal fun stickDpadStep(value: Float, held: Int, press: Float = 0.5f, release: Float = 0.4f): Int = when {
    value >= press -> 1
    value <= -press -> -1
    held > 0 && value >= release -> 1
    held < 0 && value <= -release -> -1
    else -> 0
}

/** One d-pad axis from a hat and the stick's step: a pressed hat wins, else the stick. */
internal fun hatOrStick(hat: Float, stickStep: Int): Int = when {
    hat >= 0.5f -> 1
    hat <= -0.5f -> -1
    else -> stickStep
}
