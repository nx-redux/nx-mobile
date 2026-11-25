package com.nxredux.mobile.runner

import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import com.nxredux.mobile.ui.nx.isController
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.WiiuOptions
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
    /** A pad took or came back to a player slot: the Players rows shown (the open menu's, the second-screen panel's) are stale. */
    fun onPlayersChanged()
    fun applyFrontend(values: Map<String, String>)
    fun persistGameKey(key: String, value: String, failure: String)
    fun syncN3dsCore()
    fun isLandscape(): Boolean
    fun ndsControllerMode(): Boolean
    /** The game window's screen class ([windowShape]): the DS and 3DS layout cycles skip what it does not offer. */
    fun screenClass(): ScreenClass
    /** A second display places the DS / 3DS screens: the layout cycle does nothing, silently. */
    fun layoutSetByDual(): Boolean
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
        private val CENTRED = 0f to 0f
        // The right stick (AXIS_Z/RZ) is not here: some pads rest those at -1.
        private val CLAIM_AXES = intArrayOf(
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
        )
        /**
         * Cores that read the left stick as analog (N64, PSP, PS1 DualShock, PS2, Dreamcast, and Dolphin's GameCube stick
         * and Wii Nunchuk and Classic sticks): a gamepad's stick is analog only there. melonDS DS has its own routing (the NDS
         * branch of onGenericMotionEvent). Every other core, prboom included, gets the stick folded into the d-pad.
         */
        private val STICK_CORES = setOf("mupen64plus_next", "ppsspp", "pcsx_rearmed", "swanstation", "flycast", "dolphin", "armsx2")
    }

    private val n3dsSwapKey = N3dsSwapKey()
    private val lifecycleScope get() = host.lifecycleScope

    val dpadHat = DpadHat()

    // Re-lay-out when a controller is plugged/unplugged so the on-screen pad (and a DS portrait band) hides/shows live.
    val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = host.onInputDevicesChanged()
        override fun onInputDeviceRemoved(deviceId: Int) {
            padLayouts.remove(deviceId)
            host.rumbles.forEach { it.deviceRemoved(deviceId) }
            val key = keysById.remove(deviceId)
            val port = key?.let { k -> slots?.disconnected(k) }
            // A pad unplugged mid-press never sends its key-up or its axes back to rest. One player's pad lets go of
            // its own port only; a one-player core lets go of everything. The open menu released it all already.
            if (!host.menuOpen) { if (slots == null) releaseAllGameInput() else port?.let(::releasePort) }
            host.onInputDevicesChanged()
        }
        override fun onInputDeviceChanged(deviceId: Int) {
            padLayouts.remove(deviceId)
            keysById[deviceId]?.let { k -> host.inputManager?.getInputDevice(deviceId)?.let { padNames[k] = it.name } }
            host.onInputDevicesChanged()
        }
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
        if (host.isN64) {
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

    private fun send(event: KeyEvent, keyCode: Int): Int = host.padMap.sendKey(positionOf(event, keyCode))

    /**
     * The console button [keyCode] is for this game, as its keycode ([PhysicalKeys.toRunner]): the printed letter
     * (Controls → Controller Layout names the pad's letters; Auto = the detected layout), through the core's
     * letter swap on DC/NGP, or the key's position on the positional consoles (PlayStation, Sega, arcade...).
     */
    private fun positionOf(event: KeyEvent, keyCode: Int): Int {
        val detected = detectedLayout(event)
        return PhysicalKeys.toRunner(host.gameTag, keyCode, detected, PadOptions.printedLayout(host.frontendValues, detected))
    }

    /** The letter printed on [keyCode] ([PhysicalKeys.printed]): what the in-game menus act on, so A confirms where A is printed. */
    private fun printedKey(event: KeyEvent, keyCode: Int): Int {
        val detected = detectedLayout(event)
        return PhysicalKeys.printed(keyCode, detected, PadOptions.printedLayout(host.frontendValues, detected))
    }

    /** Where [event]'s device has its face keycodes ([PadFaceLayout.detectDevice]), cached per device id. */
    private fun detectedLayout(event: KeyEvent): PadFaceLayout? =
        if (padLayouts.containsKey(event.deviceId)) padLayouts[event.deviceId] else
            event.device?.let { d -> PadFaceLayout.detectDevice(event.deviceId, d.isVirtual, d.vendorId, d.name.orEmpty()) }
                .also { padLayouts[event.deviceId] = it }

    val menuAxes = AxisDirections { host.menuRouter.onEvent(it) }   // the hat / left stick as held menu directions

    /** The on-screen stick: the game's left analog while playing, held menu directions while the menu is open. */
    val stickRouter = VirtualStickRouter(
        onAnalog = { x, y ->
            if (host.isN3ds && n3dsHotkeys.onStick(x, y)) syncN3dsCursor()
            else if (host.isNds && ndsHotkeys.onStick(x, y)) wakePen()
            else host.retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, 0)
        },
        onMenu = { host.menuRouter.onEvent(it) },
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

    /**
     * A pad skin [shown] or gone, and whether it lays the DS / 3DS screens out by the player's layout ([layouts]: a regular
     * phone's landscape): both consoles' chords follow (SELECT + Left / Right cycles only where the layout shows).
     */
    fun setSkinLayouts(shown: Boolean, layouts: Boolean) {
        n3dsHotkeys.skinShown = shown; n3dsHotkeys.skinLayouts = shown && layouts
        ndsHotkeys.skinShown = shown; ndsHotkeys.skinLayouts = shown && layouts
        wiiuHotkeys.skinShown = shown; wiiuHotkeys.skinLayouts = shown && layouts
    }

    /** The skin lays the DS / 3DS / Wii U screens out by the player's layout ([setSkinLayouts]). */
    val skinLayouts: Boolean get() = n3dsHotkeys.skinLayouts
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
    val n3dsStylusOn = mutableStateOf(false)   // stylus mode
    val ndsStylusOn = mutableStateOf(false)    // the DS's, likewise
    private var n3dsTickJob: Job? = null        // ticks the hotkeys while they ask ([N3dsHotkeys.ticking])

    /** The Wii U's SELECT chords on P1: the 3DS's hold-back, as SELECT is the GamePad's minus. */
    private val wiiuHotkeys = N3dsHotkeys(wiiu = true)
    private var wiiuHatX = 0
    private var wiiuHatY = 0
    private var wiiuTickJob: Job? = null
    private val isWiiu: Boolean get() = WiiuOptions.appliesTo(host.gameTag)

    /**
     * The core reads a pad's left stick as an analog stick ([STICK_CORES]). Any other core gets the stick folded into the
     * d-pad (onGenericMotionEvent).
     */
    private val coreHasStick: Boolean get() = host.gameCoreId in STICK_CORES   // netplay too: sticks are synced

    /** The DS's stylus toggle, as R2's. */
    fun toggleNdsStylus() {
        applyNdsAction(ndsHotkeys.toggleStylus())
        syncPen()
    }

    /** The 3DS's stylus toggle, as SELECT + L1's. */
    fun toggleN3dsStylus() {
        applyN3dsAction(n3dsHotkeys.toggleStylus())
        syncN3dsCursor()
    }

    /** The skin's SWAP: Azahar's swap-screens key (L3), held as long as the cap; split, it swaps the displays on press. */
    fun onN3dsSwap(down: Boolean) {
        if (host.onN3dsSwapPressed(down)) return
        host.retroView?.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_THUMBL)
    }

    /** Dolphin is running a GameCube game, so the pad is the GameCube's ([PadProfile.Kind.GC]) and L / R are analog triggers. */
    val gcPad: Boolean get() = host.isDolphin && host.padProfile().kind == PadProfile.Kind.GC

    /** The GameCube pad's L and R: one tap/hold gesture, applied state, hold timer each. */
    private val gcGestures = mapOf(NxButton.L2 to TriggerGesture(), NxButton.R2 to TriggerGesture())
    private val gcStates = mutableMapOf(NxButton.L2 to TriggerGesture.State.OFF, NxButton.R2 to TriggerGesture.State.OFF)
    private val gcHoldTimers = mutableMapOf<NxButton, Job>()

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
        val view = host.retroView ?: return
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

    /** An on-screen right stick (the PS2's): the core's right analog on port 0. */
    fun onRightStick(x: Float, y: Float) {
        if (host.menuOpen) return
        host.retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y, 0)
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
        val landscape = host.isLandscape()
        when (action) {
            NdsAction.SwapScreens -> setNdsKey(NdsOptions.swapBigScreen(host.frontendValues))
            is NdsAction.CycleLayout -> {
                if (host.layoutSetByDual()) return
                // From the layout showing on this screen (an unset row steps from its default).
                setNdsKey(NdsOptions.cycleLayout(
                    host.frontendValues, landscape, host.ndsControllerMode(), action.delta, host.screenClass(),
                ))
            }
            is NdsAction.CycleCorner -> NdsOptions.cycleCorner(host.frontendValues, action.delta).let { (key, value) ->
                setNdsKey(key to value, "PiP: " + NdsOptions.valueLabel(key, value))
            }
            is NdsAction.Stylus -> {
                ndsStylusOn.value = action.on
                if (action.on) releaseStylusInputs()
                showNdsLabel(if (action.on) "Stylus on" else "Stylus off")
            }
        }
    }

    /** The Wii U's SWAP: the other screen big (split: on the picture display), saved to the game's config as the DS's is. */
    fun swapWiiuScreens() = setNdsKey(WiiuOptions.swapBigScreen(host.frontendValues))

    /**
     * A hotkey's layout change: applied now, shown as [label] (the new value's by default), and saved to the game's config.
     * A key locked in the .cfg is left alone.
     */
    private fun setNdsKey(change: Pair<String, String>, label: String = NdsOptions.valueLabel(change.first, change.second)) {
        val (key, value) = change
        if (key in host.lockedOptions) return
        host.applyFrontend(host.frontendValues + change)
        showNdsLabel(label)
        host.persistGameKey(key, value, "Could not save the screen layout")
    }

    /** The notices' buttons, by toast id: what a tap on one does ([showNotice]). */
    private val noticeActions = HashMap<Long, () -> Unit>()

    /** A notice over the picture for [ms] (a tip: [LayoutTip]), with a button [action] whose tap runs [onAction] and closes it. */
    fun showNotice(text: String, ms: Long, action: String? = null, onAction: () -> Unit = {}) {
        val id = host.notifications.push(text, null, ms, SystemClock.elapsedRealtime(), action)
        if (action != null && id >= 0) noticeActions[id] = onAction
        host.publishNotifications()
    }

    /** A notice's Close was tapped: it goes, its action not run. */
    fun onNoticeDismiss(id: Long) {
        noticeActions.remove(id)
        host.notifications.dismiss(id)
        host.publishNotifications()
    }

    /** A notice's button was tapped: its action, and the notice goes. */
    fun onNoticeAction(id: Long) {
        noticeActions.remove(id)?.invoke()
        host.notifications.dismiss(id)
        host.publishNotifications()
    }

    private fun showNdsLabel(text: String) {
        host.notifications.push(text, null, NDS_LABEL_MS, SystemClock.elapsedRealtime())
        host.publishNotifications()
    }

    /** The d-pad, left stick and A now drive the pen: the core must not keep what they held. */
    private fun releaseStylusInputs() {
        val view = host.retroView ?: return
        releaseDirections(view)
        PadKeymap.keyCodeFor(NxButton.A)?.let { view.sendKeyEvent(KeyEvent.ACTION_UP, it) }
    }

    /** Sends the pen when it changed (pressed, or just lifted), moves the cursor, and keeps the pen ticking in stylus mode. */
    fun syncPen() {
        val view = host.retroView ?: return
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
        wakePen()
    }

    /**
     * Ticks the pen while it moves. Only a held direction or stick moves it, and taking or changing either is an event that
     * wakes this ([routeNds] through [syncPen], or [ndsHotkeys]'s onStick), so the tick ends at the first step that moves
     * nothing (nothing held, or pushed against an edge) and while the menu is open (it lets go of everything).
     */
    private fun wakePen() {
        if (!ndsHotkeys.stylus || host.menuOpen || penJob?.isActive == true) return
        penJob = lifecycleScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (true) {
                delay(PEN_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                if (host.menuOpen || !ndsHotkeys.tick(now - last)) break
                last = now
                syncPen()
            }
        }
    }

    /**
     * The stylus cursor over the bottom screen wherever it is shown ([DualScreen.penOn]): on main (the inset included), or
     * on the second display when the bottom screen is drawn there; hidden on the other display, and without stylus mode.
     */
    fun positionPen() {
        val on = if (ndsHotkeys.stylus) DualScreen.penOn(host.ndsRegions, host.secondNdsRegions) else null
        val pen = ndsHotkeys.pen
        val mainRegion = host.ndsRegions.firstOrNull { it.screen == NdsScreen.BOTTOM }.takeIf { on == PictureOn.MAIN }
        if (mainRegion == null) penDot?.hide() else {
            val (cx, cy) = NdsLayout.pointIn(mainRegion, pen.x, pen.y)
            // Above the GL surface in the game container.
            (penDot ?: PenDot(host.gameContainer).also { penDot = it }).show(cx, cy, pen.pressed)
        }
        val secondRegion = host.secondNdsRegions.firstOrNull { it.screen == NdsScreen.BOTTOM }.takeIf { on == PictureOn.SECOND }
        host.presentation?.let { p ->
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
        routeHat(view, ndsHatX, ndsHatY, x, y)
        ndsHatX = x
        ndsHatY = y
    }

    /**
     * The hat's edges from ([oldX], [oldY]) to ([x], [y]) as direction presses and releases through the DS, 3DS or Wii U
     * hotkeys ([routeHatButton]; true from them: forward it); the forwarded ones reach the core as its d-pad. No lambda:
     * this runs for every joystick event.
     */
    private fun routeHat(view: GLRetroView, oldX: Int, oldY: Int, x: Int, y: Int) {
        // `or`, not `||`: both axes' edges are routed.
        val changed = hatEdge(oldX, x, NxButton.LEFT, NxButton.RIGHT) or hatEdge(oldY, y, NxButton.UP, NxButton.DOWN)
        if (changed) view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, ndsHat.x.toFloat(), ndsHat.y.toFloat(), 0)
    }

    /** One hat axis' edge ([routeHat]); true when the d-pad the core gets changed. */
    private fun hatEdge(old: Int, new: Int, negative: NxButton, positive: NxButton): Boolean {
        if (old == new) return false
        var changed = false
        val released = when (old) { -1 -> negative; 1 -> positive; else -> null }
        val pressed = when (new) { -1 -> negative; 1 -> positive; else -> null }
        if (released != null && routeHatButton(released, false)) changed = ndsHat.release(released)
        if (pressed != null && routeHatButton(pressed, true)) changed = ndsHat.press(pressed) || changed
        return changed
    }

    private fun routeHatButton(button: NxButton, down: Boolean): Boolean = when {
        host.isN3ds -> routeN3ds(button, down)
        isWiiu -> routeWiiu(button, down)
        else -> routeNds(button, down)
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
        val view = host.retroView
        for (e in n3dsHotkeys.takeKeys()) {
            if (e.key == N3dsKey.L3 && host.onN3dsSwapPressed(e.down)) continue   // split: SELECT + R swaps the displays, as the skin's SWAP does
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
                if (host.layoutSetByDual()) return
                // The row in use (orientation, controller), as the DS's; applyFrontend sets the core's layout from it.
                val (key, value) = N3dsOptions.cycleLayout(host.frontendValues, host.isLandscape(), host.ndsControllerMode(), action.delta, host.screenClass())
                if (key in host.lockedOptions) return
                host.applyFrontend(host.frontendValues + (key to value))   // Azahar re-lays its screens out on its next frame
                showNdsLabel(N3dsOptions.layoutLabel(value))
                host.persistGameKey(key, value, "Could not save the screen layout")
            }
            is N3dsAction.Stylus -> {
                n3dsStylusOn.value = action.on
                host.syncN3dsCore()   // the cursor stays shown while stylus mode is on
                // On: the d-pad, left stick and A now drive the cursor, so the core must not keep what they held. Off: the
                // hotkeys lift a touch held, and syncN3dsCursor gives the right stick back.
                if (action.on) releaseStylusInputs()
                showNdsLabel(if (action.on) "Stylus on" else "Stylus off")
            }
            N3dsAction.SwapScreens -> Unit   // the 3DS swaps through Azahar's L3 key, never as an action
        }
    }

    /**
     * Sends the hotkeys' keys and the right stick when it changed: stylus mode's cursor ([N3dsHotkeys.cursor]), else the
     * hardware stick. Keeps ticking the hotkeys while they ask (a SELECT held toward its 1 s cancel or tapping, a held d-pad
     * cursor speeding up).
     */
    fun syncN3dsCursor() {
        sendN3dsKeys()
        val view = host.retroView ?: return
        // The on-screen C-stick while it is off centre, else the hardware stick.
        val right = n3dsHotkeys.cursor ?: n3dsPadRight.takeIf { it != CENTRED } ?: n3dsHwRight
        if (right != n3dsSentRight) {
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, right.first, right.second, 0)
            n3dsSentRight = right
        }
        if (!n3dsHotkeys.ticking || n3dsTickJob?.isActive == true) return
        n3dsTickJob = tickWhileAsked(n3dsHotkeys, ::syncN3dsCursor)
    }

    /** Ticks [hotkeys] while they ask ([N3dsHotkeys.ticking]), with [sync] after each tick to send what they made. */
    private fun tickWhileAsked(hotkeys: N3dsHotkeys, sync: () -> Unit): Job = lifecycleScope.launch {
        var last = SystemClock.elapsedRealtime()
        while (hotkeys.ticking) {
            delay(PEN_TICK_MS)
            val now = SystemClock.elapsedRealtime()
            if (!host.menuOpen) hotkeys.tick(now - last)
            last = now
            sync()
        }
    }

    /**
     * The Wii U's SELECT chords for one P1 button event ([wiiuHotkeys]); true when it goes on to the core as is. A held-back
     * SELECT is sent here when it taps. A key repeat only asks whether its press did.
     */
    private fun routeWiiu(button: NxButton, down: Boolean, repeat: Boolean = false): Boolean {
        if (repeat) return !wiiuHotkeys.consumes(button)
        val decision = wiiuHotkeys.onButton(button, down)
        when (val action = decision.action) {
            is N3dsAction.CycleLayout -> if (!host.layoutSetByDual()) {
                setNdsKey(WiiuOptions.cycleLayout(host.frontendValues, host.isLandscape(), host.ndsControllerMode(), action.delta))
            }
            N3dsAction.SwapScreens -> swapWiiuScreens()
            is N3dsAction.Stylus, null -> Unit
        }
        syncWiiuKeys()
        return decision.forward
    }

    /** Sends the game the SELECT taps the Wii U's hotkeys made, and keeps them ticking while they ask. */
    private fun syncWiiuKeys() {
        val select = PadKeymap.keyCodeFor(host.padMap.sends(NxButton.SELECT))
        for (e in wiiuHotkeys.takeKeys()) {
            if (e.key == N3dsKey.SELECT && select != null) host.retroView?.sendKeyEvent(if (e.down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, select)
        }
        if (!wiiuHotkeys.ticking || wiiuTickJob?.isActive == true) return
        wiiuTickJob = tickWhileAsked(wiiuHotkeys, ::syncWiiuKeys)
    }

    /** Hardware hat edges as direction presses through the 3DS hotkeys; the forwarded ones reach the core as its d-pad. */
    private fun routeN3dsHat(view: GLRetroView, event: MotionEvent) {
        val x = n3dsHotkeys.joystickDirection(event.getAxisValue(MotionEvent.AXIS_HAT_X))
        val y = n3dsHotkeys.joystickDirection(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        routeHat(view, n3dsHatX, n3dsHatY, x, y)
        n3dsHatX = x
        n3dsHatY = y
    }

    /**
     * L2/R2 pulled as trigger axes (AXIS_LTRIGGER/AXIS_BRAKE, AXIS_RTRIGGER/AXIS_GAS): each pull, with hysteresis, is one
     * press and one release ([TriggerAxes]), to the DS hotkeys, the 3DS hotkeys (ZL/ZR, always forwarded) or the core as
     * the L2/R2 key. A trigger that has also come as a key event goes by its keys alone ([routeTriggerKey]). Not for the
     * Dreamcast, whose triggers are analog.
     */
    private fun routeTriggerAxes(event: MotionEvent, port: Int = 0) {
        TriggerAxes.forEachChange(inputs[port].triggers.onAxes(leftTrigger(event), rightTrigger(event))) { button, down -> sendAxisTrigger(button, down, port) }
    }

    // Some pads report the triggers as LTRIGGER/RTRIGGER, others as BRAKE/GAS.
    private fun leftTrigger(event: MotionEvent) = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
    private fun rightTrigger(event: MotionEvent) = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))

    /** An axis trigger's press or release: to the DS hotkeys, the 3DS hotkeys (ZL/ZR, always forwarded) or the core. */
    private fun sendAxisTrigger(button: NxButton, down: Boolean, port: Int) {
        when {
            host.isNds -> routeNds(button, down)
            host.isN3ds -> if (routeN3ds(button, down)) sendTriggerKey(button, down)
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
        if (host.isDc || gcPad) host.retroView?.sendTriggers(port, t.analogLeft, t.analogRight)
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
        val code = if (host.isN64) N64Input.toRunner(button, keyCode) else host.padMap.sendKey(keyCode)
        code?.let { host.retroView?.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, it, port) }
    }

    /** The activity's onStop: no pen or 3DS hotkey tick in the background; onStart restarts them ([syncPen], [syncN3dsCursor]). */
    fun onStop() {
        penJob?.cancel()   // stylus mode itself is kept
        penJob = null
        if (host.isN3ds) {   // backgrounded: no held-back SELECT or forwarded key left hanging, and no tick
            n3dsHotkeys.releaseAll()
            sendN3dsKeys()
        }
        n3dsTickJob?.cancel()
        n3dsTickJob = null
        if (isWiiu) {
            wiiuHotkeys.releaseAll()
            syncWiiuKeys()
        }
        wiiuTickJob?.cancel()
        wiiuTickJob = null
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
        val view = host.retroView ?: return
        RELEASED_BUTTONS.forEach { b -> PadKeymap.keyCodeFor(b)?.let { view.sendKeyEvent(KeyEvent.ACTION_UP, it) } }
        RELEASED_THUMB_KEYS.forEach { view.sendKeyEvent(KeyEvent.ACTION_UP, it) }
        releaseDirections(view)
        // An analog trigger value wins over the keys in the runner and the menu swallows the release axis: zero it.
        if (host.isDc || gcPad) view.sendTriggers(0, 0f, 0f)
        if (gcPad) gcClearTriggers()
        if (host.isWii) view.setPointerVelocity(0, 0f, 0f)   // the menu swallows the stick's return to rest: the pointer stops
        ndsHotkeys.releaseAll()
        ndsHatX = 0
        ndsHatY = 0
        syncPen()
        releaseShortcuts()   // their releases go to the menu now
        if (host.isN3ds) {
            n3dsHotkeys.releaseAll()   // sends up what it held in the core (L3, R3, a tapping SELECT); a held-back SELECT is dropped
            n3dsHatX = 0
            n3dsHatY = 0
            n3dsHwRight = CENTRED
            n3dsPadRight = CENTRED
            syncN3dsCursor()
        }
        if (isWiiu) {
            wiiuHotkeys.releaseAll()   // a tapping SELECT is let go; a held-back one is dropped
            wiiuHatX = 0
            wiiuHatY = 0
            syncWiiuKeys()
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
        val view = host.retroView ?: return
        RELEASED_BUTTONS.forEach { b -> PadKeymap.keyCodeFor(b)?.let { view.sendKeyEvent(KeyEvent.ACTION_UP, it, port) } }
        RELEASED_THUMB_KEYS.forEach { view.sendKeyEvent(KeyEvent.ACTION_UP, it, port) }
        releaseDirections(view, port)
        view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, port)
        if (host.isWii) view.setPointerVelocity(port, 0f, 0f)   // an unplugged pad's tilted stick must not keep moving the pointer
        if (host.isDc || gcPad) view.sendTriggers(port, 0f, 0f)
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
        host.rumbles.firstOrNull()?.useDevice(null)   // the on-screen pad: the phone in the player's hands takes the rumble
        if (host.menuOpen) { host.menuRouter.onEvent(event.map(host.padProfile()::menuButton)); return }
        val btn = event.button
        val down = event is PadEvent.Down
        // MENU opens the pause menu (not forwarded to the core).
        if (btn == NxButton.MENU) { if (down) host.toggleMenu(); return }
        // The Wii's HOME: a resume point first, then a tap of the core's Home ([GameInputHost.pressWiiHome]); the release is the tap's.
        // The Wii U's (Cemu has no GamePad HOME): the pause menu, as MENU.
        if (btn == NxButton.HOME) {
            if (down && host.isWii) host.pressWiiHome() else if (down && WiiuOptions.appliesTo(host.gameTag)) host.toggleMenu()
            return
        }
        // SELECT + START held together also opens it (the on-screen pad is P1, port 0).
        if (inputs.menuChord(0, btn, down)) { host.toggleMenu(); return }
        if (host.isNds && !routeNds(btn, down)) return   // L3, L2, R2, the SELECT chords and the stylus inputs stay out of the core
        if (host.isN3ds && !routeN3ds(btn, down)) return  // SELECT (held back for chords), its chords and the stylus inputs: routeN3ds sends what they become
        if (isWiiu && !routeWiiu(btn, down)) return       // SELECT (held back for chords) and its chords, likewise
        val view = host.retroView ?: return
        if (gcPad && (btn == NxButton.L2 || btn == NxButton.R2)) { gcTrigger(btn, down); return }
        if (btn.isDirection) {
            if (down) dpadHat.press(btn) else dpadHat.release(btn)
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, dpadHat.x.toFloat(), dpadHat.y.toFloat(), 0)
        } else {
            val code = if (host.isN64) N64Input.toRunner(btn, PadKeymap.keyCodeFor(btn) ?: 0)
                else PadKeymap.keyCodeFor(host.padMap.sends(btn))
            if (code == null || code == 0) return
            val sent = if (down) padSent.down(btn, code) else padSent.up(btn, code)
            view.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, sent)
        }
    }

    fun onStick(x: Float, y: Float) = stickRouter.onStick(x, y, host.menuOpen)

    /** The on-screen New 3DS C-stick: the core's right analog (Azahar's C-stick, and its pointer in the default mode). */
    fun onCStick(x: Float, y: Float) {
        if (host.menuOpen || !host.isN3ds) return
        n3dsPadRight = x to y
        syncN3dsCursor()
    }

    fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        // The rumble follows the pad in use, on its own port (a one-player core: port 0); a pad with no slot yet claims below.
        // One lookup per event: this is the hot path.
        val joystick = event != null && event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        val padPort = if (joystick) portFor(event?.device, claim = false)?.also { noteRumbleDevice(event?.device, it) } else null
        if (host.menuOpen) { event?.let { routeMenuMotion(it) }; return true }
        val view = host.retroView ?: return host.superGenericMotionEvent(event)
        if (event != null && host.isN3ds) {
            if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
                routeN3dsHat(view, event)
                routeTriggerAxes(event)     // L2/R2 are ZL/ZR: an axis-only trigger presses them too
                val sx = event.getAxisValue(MotionEvent.AXIS_X)
                val sy = event.getAxisValue(MotionEvent.AXIS_Y)
                if (!n3dsHotkeys.onStick(sx, sy)) view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, sx, sy, 0)
                val rx = event.getAxisValue(MotionEvent.AXIS_Z)
                val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
                if (rx != n3dsHwRight.first || ry != n3dsHwRight.second) n3dsHwRight = rx to ry
                syncN3dsCursor()
                return true   // consumed, as for the DS: no synthesised d-pad keys passing routeN3ds twice
            }
            return host.superGenericMotionEvent(event)
        }
        if (event != null && host.isNds) {
            // Joystick events only: a mouse or hover event reads its hat, trigger and stick axes as 0, which would
            // release a held hat, re-fire a trigger or move the pen.
            if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
                routeNdsHat(view, event)
                routeTriggerAxes(event)
                val sx = event.getAxisValue(MotionEvent.AXIS_X)
                val sy = event.getAxisValue(MotionEvent.AXIS_Y)
                if (ndsHotkeys.onStick(sx, sy)) wakePen() else view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, sx, sy, 0)
                val rx = event.getAxisValue(MotionEvent.AXIS_Z)
                val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
                if (ndsHotkeys.onRightStick(rx, ry)) wakePen() else view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, rx, ry, 0)
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
            // A pad with no player slot claims one only with an axis pushed past half (not its resting report on connect);
            // one in a slot (or any pad on a one-player core: port 0) already has its port from the lookup above.
            val port = padPort ?: run {
                if (slots == null || !axesClaim(claimAxes(event))) return true   // no slot: dropped
                portFor(event.device, claim = true)?.also { noteRumbleDevice(event.device, it) } ?: return true
            }
            val inp = inputs[port]
            // Dreamcast triggers are analog (flycast reads RETRO_DEVICE_INDEX_ANALOG_BUTTON).
            if (host.isDc || gcPad) {   // GameCube triggers too: Dolphin reads them as analog buttons
                val t = inp.triggers
                t.analog(leftTrigger(event), rightTrigger(event))
                view.sendTriggers(port, t.analogLeft, t.analogRight)   // a shortcut's pull reads at rest
            } else {
                routeTriggerAxes(event, port)   // a digital L2/R2 from a pad that reports its triggers only as axes
            }
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val sx = event.getAxisValue(MotionEvent.AXIS_X)
            val sy = event.getAxisValue(MotionEvent.AXIS_Y)
            if (!coreHasStick) {
                // The runner's JOYPAD d-pad is the hat axes and keys only, and consuming the event drops the d-pad keys
                // Android would make from the stick, so a digital core's stick is folded in here.
                inp.stickDpadX = stickDpadStep(sx, inp.stickDpadX)
                inp.stickDpadY = stickDpadStep(sy, inp.stickDpadY)
            }
            if (isWiiu && port == 0) {
                // P1's d-pad edges through the SELECT chords; the forwarded ones reach the core as its d-pad
                val x = hatOrStick(hatX, inp.stickDpadX)
                val y = hatOrStick(hatY, inp.stickDpadY)
                routeHat(view, wiiuHatX, wiiuHatY, x, y)
                wiiuHatX = x
                wiiuHatY = y
            } else if (coreHasStick) {
                view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, hatX, hatY, port)
            } else {
                view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, hatOrStick(hatX, inp.stickDpadX).toFloat(), hatOrStick(hatY, inp.stickDpadY).toFloat(), port)
            }
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, sx, sy, port)
            val rx = event.getAxisValue(MotionEvent.AXIS_Z)
            val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
            if (host.isWii && host.wiiPad != null && host.wiiPad != WiiPad.CLASSIC) {
                // Wiimote types: the right stick aims the IR pointer (Dolphin's pointer reads POINTER, ir_mode 2).
                view.setPointerVelocity(port, WiiPointer.velocity(rx), WiiPointer.velocity(ry))
            } else {
                view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, rx, ry, port)
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
        if (device != null && !device.isVirtual) host.rumbles.getOrNull(port)?.useDevice(device.id)
    }

    /**
     * The port [device]'s input goes to: 0 for a one-player core, the on-screen pad or a non-pad device; else its
     * player slot, claimed when [claim] (a key-down, an axis past half) and it has none. Null drops the input: every
     * slot is taken, or the pad is benched (its slot set to Empty on the Players page).
     */
    private fun portFor(device: InputDevice?, claim: Boolean): Int? {
        val s = slots ?: return 0
        if (device == null || !device.isController()) return 0
        // A device id is never reused for another device, and its descriptor never changes: one key per id.
        val key = keysById[device.id] ?: DeviceKey(device.descriptor).also {
            keysById[device.id] = it
            padNames[it] = device.name
        }
        if (!claim) return s.portOf(key)
        val c = s.claim(key) ?: return null
        if (c.joined) { onPlayerJoined(c.port, device); host.connectDolphinPorts() }
        return c.port
    }

    /** The axes a slotless pad claims with ([axesClaim]), into one reused array: this runs for every joystick event. */
    private fun claimAxes(event: MotionEvent): FloatArray = claimValues.also { v ->
        for (i in CLAIM_AXES.indices) v[i] = event.getAxisValue(CLAIM_AXES[i])
    }
    private val claimValues = FloatArray(CLAIM_AXES.size)

    /** A pad took (or came back to) player slot [port]: a notice says who is who. */
    private fun onPlayerJoined(port: Int, device: InputDevice) {
        host.notifications.push("Player ${port + 1}: ${device.name}", null, PLAYER_NOTICE_MS, SystemClock.elapsedRealtime())
        host.publishNotifications()
        host.onPlayersChanged()
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
        } to s.outOfPlay.let { benched -> keys.map { k -> PlayerPadRow(k.value, name(k), benched = k in benched) } }
    }

    /** The connected physical pads, one per descriptor (a pad may show as several input devices). */
    private fun connectedPads(): List<InputDevice> {
        val im = host.inputManager ?: return emptyList()
        return im.inputDeviceIds.toList().mapNotNull(im::getInputDevice).filter { it.isController() }.distinctBy { it.descriptor }
    }

    /** Acts on [ShortcutKeys]' [verdict] for [keyCode] on [port]; true when the key is taken (it goes no further). */
    private fun shortcut(verdict: KeyVerdict, keyCode: Int = 0, port: Int = 0): Boolean = when (verdict) {
        KeyVerdict.Pass -> false
        KeyVerdict.Swallow -> true
        KeyVerdict.OpenMenu -> { host.toggleMenu(); true }
        is KeyVerdict.Fire -> { takeTrigger(keyCode, port, verdict.down); host.onShortcut(verdict.action, verdict.down); true }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode)) return host.superKeyDown(keyCode, event)
        // The rumble follows the pad in use, on its own port (a one-player core: port 0); a pad with no slot yet claims below.
        portFor(event.device, claim = false)?.let { noteRumbleDevice(event.device, it) }
        if (host.menuOpen) {
            // Press to bind first, on the raw code (as [ShortcutKeys] matches it in game), Back too: it cancels.
            if (host.menuCaptureKey(keyCode, down = true, repeat = event.repeatCount > 0)) return true
            if (keyCode == KeyEvent.KEYCODE_BACK) { if (event.repeatCount == 0) host.backPressed(); return true }
            if (event.repeatCount == 0) PadKeymap.buttonFor(printedKey(event, keyCode))?.let { host.menuRouter.onEvent(PadEvent.Down(it)) }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) { if (event.repeatCount == 0) host.backPressed(); return true }
        val first = event.repeatCount == 0
        val button = PadKeymap.buttonFor(keyCode)
        // Any pad can pause, before the slot lookup: also one with no slot (table full) or benched. It claims nothing. With a
        // MENU combo bound, MENU is a modifier first: the menu opens on its release if no combo fired ([ShortcutKeys]).
        if (button == NxButton.MENU) {
            val keys = host.shortcutKeys
            if (keys?.holdsMenu == true) keys.onDown(keyCode, repeat = !first) else if (first) host.toggleMenu()
            return true
        }
        val port = portFor(event.device, claim = true) ?: return true   // no slot: dropped
        // Wii: the left-stick click recentres the phone's motion, the right-stick click is HOME ([GameInputHost.pressWiiHome]); neither
        // reaches the core as itself. 3DS split: the left-stick click is SWAP between the displays. Elsewhere (the PS2's
        // L3 / R3) they go on to the core.
        when (thumbKeyRoute(keyCode, host.isWii, host.isN3ds)) {
            ThumbKeyRoute.WII_RECENTER -> { if (first) host.recenterWiiMotion(); return true }
            ThumbKeyRoute.WII_HOME -> { if (first) host.pressWiiHome(); return true }
            ThumbKeyRoute.N3DS_SWAP -> if (n3dsSwapKey.down(repeat = !first) { host.onN3dsSwapPressed(down = true) }) return true
            ThumbKeyRoute.CORE -> {}
        }
        noteRumbleDevice(event.device, port)
        // SELECT + START opens the menu: P1's (port 0's) only. Held state follows every down, repeats too.
        if (button != null && inputs.menuChord(port, button, down = true) && first) { host.toggleMenu(); return true }
        // Shortcuts, P1's (port 0's) only, as the menu chord: after the built-in hotkeys above, which they can't bind
        // ([com.nxredux.mobile.shortcuts.EffectiveShortcuts.blocked]), before the DS / 3DS routing and the core.
        if (port == 0) host.shortcutKeys?.let { keys -> if (shortcut(keys.onDown(keyCode, repeat = !first), keyCode, port)) return true }
        // The hotkeys and stylus see the console button the key is (its printed letter on DS/3DS), as the core will.
        val placed = PadKeymap.buttonFor(positionOf(event, keyCode))
        if (host.isNds) (placed ?: ndsThumbButton(keyCode))?.let { b ->
            if (b == NxButton.L2 || b == NxButton.R2) { if (first && routeTriggerKey(b, down = true)) routeNds(b, down = true); return true }
            if (!routeNds(b, down = true, repeat = !first)) return true
        }
        if (gcPad) placed?.let { b -> if (gcAnalogTriggerKey(b, event.device)) return true }
        if (!host.isNds && !host.isDc && first) placed?.let { b ->
            if ((b == NxButton.L2 || b == NxButton.R2) && !routeTriggerKey(b, down = true, port)) return true   // its axis pressed it
        }
        if (host.isN3ds) placed?.let { b ->
            if (!routeN3ds(b, down = true, repeat = !first)) return true
        }
        if (isWiiu && port == 0) placed?.let { b ->
            if (!routeWiiu(b, down = true, repeat = !first)) return true
        }
        // As printed (or by position on positional consoles) through the console's pad map; N64 by name ([runnerKey]).
        runnerKey(event, keyCode, port)?.let { host.retroView?.sendKeyEvent(event.action, it, port) }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode)) return host.superKeyUp(keyCode, event)
        if (host.menuOpen) {
            if (host.menuCaptureKey(keyCode, down = false, repeat = false)) return true
            PadKeymap.buttonFor(printedKey(event, keyCode))?.let { host.menuRouter.onEvent(PadEvent.Up(it)) }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        // A held-back MENU (a MENU combo is bound): its release opens the menu if no combo fired, from any pad as its press.
        if (PadKeymap.buttonFor(keyCode) == NxButton.MENU) host.shortcutKeys?.takeIf { it.holdsMenu }?.let { shortcut(it.onUp(keyCode)); return true }
        val port = portFor(event.device, claim = false) ?: return true
        when (thumbKeyRoute(keyCode, host.isWii, host.isN3ds)) {
            ThumbKeyRoute.WII_RECENTER, ThumbKeyRoute.WII_HOME -> return true
            ThumbKeyRoute.N3DS_SWAP -> if (host.onN3dsSwapPressed(down = false)) return true
            ThumbKeyRoute.CORE -> {}
        }
        PadKeymap.buttonFor(keyCode)?.let { inputs.menuChord(port, it, down = false) }
        if (port == 0) host.shortcutKeys?.let { keys -> if (shortcut(keys.onUp(keyCode), keyCode, port)) return true }
        val placed = PadKeymap.buttonFor(positionOf(event, keyCode))   // as on the down
        if (host.isNds) (placed ?: ndsThumbButton(keyCode))?.let { b ->
            if (b == NxButton.L2 || b == NxButton.R2) { if (routeTriggerKey(b, down = false)) routeNds(b, down = false); return true }
            if (!routeNds(b, down = false)) return true
        }
        if (gcPad) placed?.let { b -> if (gcAnalogTriggerKey(b, event.device)) return true }
        if (!host.isNds && !host.isDc) placed?.let { b ->
            if ((b == NxButton.L2 || b == NxButton.R2) && !routeTriggerKey(b, down = false, port)) return true   // not held
        }
        if (host.isN3ds) placed?.let { b ->
            if (!routeN3ds(b, down = false)) return true
        }
        if (isWiiu && port == 0) placed?.let { b ->
            if (!routeWiiu(b, down = false)) return true
        }
        // As printed (or by position on positional consoles) through the console's pad map; N64 by name ([runnerKey]).
        runnerKey(event, keyCode, port)?.let { host.retroView?.sendKeyEvent(event.action, it, port) }
        return true
    }
}

/** The buttons a release lets go of in the core ([GameInputRouter.releaseAllGameInput], [GameInputRouter.releasePort]). */
private val RELEASED_BUTTONS = listOf(
    NxButton.A, NxButton.B, NxButton.X, NxButton.Y, NxButton.L1, NxButton.L2,
    NxButton.R1, NxButton.R2, NxButton.START, NxButton.SELECT, NxButton.MENU,
)

/**
 * The thumbstick clicks (L3, R3) a release also lets go of, kept out of [RELEASED_BUTTONS]: the on-screen L3 / R3 (and the
 * Wii's HOME) send these keycodes, and a pad's clicks reach the core as the same keycodes on every console (no pad map or
 * face swap touches them), so the release matches what was sent.
 */
private val RELEASED_THUMB_KEYS = listOf(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR)

/**
 * A physical stick click as the DS hotkeys' button: L3 swaps the screens and R3 taps with the stylus ([NdsHotkeys]).
 * [PadKeymap.buttonFor] leaves the clicks out, so on other consoles they reach the core as themselves.
 */
internal fun ndsThumbButton(keyCode: Int): NxButton? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_THUMBL -> NxButton.L3
    KeyEvent.KEYCODE_BUTTON_THUMBR -> NxButton.R3
    else -> null
}

/** The 3DS SWAP key's downs: a held key's repeats go where its first down went, never read as a release. */
internal class N3dsSwapKey {
    private var claimed = false
    fun down(repeat: Boolean, press: () -> Boolean): Boolean {
        if (!repeat) claimed = press()
        return claimed
    }
}

/** Where a physical stick click goes ([thumbKeyRoute]). */
internal enum class ThumbKeyRoute { WII_RECENTER, WII_HOME, N3DS_SWAP, CORE }

/**
 * Where a physical [keyCode] goes when it is a stick click: on the Wii ([wii]) L3 recentres the motion and R3 is HOME
 * ([WiiShortcuts]); on the 3DS ([n3ds]) L3 is SWAP, taken only while the displays are split (else it goes on to the core
 * as Azahar's swap key). Every other key, and both clicks on every other console (the PS2's L3 / R3), go to the core.
 */
internal fun thumbKeyRoute(keyCode: Int, wii: Boolean, n3ds: Boolean): ThumbKeyRoute = when {
    WiiShortcuts.isRecenter(wii, keyCode) -> ThumbKeyRoute.WII_RECENTER
    WiiShortcuts.isHome(wii, keyCode) -> ThumbKeyRoute.WII_HOME
    n3ds && keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL -> ThumbKeyRoute.N3DS_SWAP
    else -> ThumbKeyRoute.CORE
}

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
