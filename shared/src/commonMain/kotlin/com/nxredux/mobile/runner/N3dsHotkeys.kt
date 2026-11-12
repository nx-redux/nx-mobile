package com.nxredux.mobile.runner

import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.ui.nx.NxButton
import kotlin.math.min

/** A core key the 3DS hotkeys send themselves ([N3dsHotkeys.takeKeys]), not as the pad button that caused it. */
enum class N3dsKey {
    /** Azahar's "Home/Swap screens" (citra_swap_screen_mode: toggle or hold), the runner's L3. */
    L3,
    /** Azahar's "Touch Screen Touch": a touch at its right-stick cursor, the runner's R3. */
    R3,
    /** The game's Select, held back while it may start a chord. */
    SELECT,
}

data class N3dsKeyEvent(val key: N3dsKey, val down: Boolean)

/** A frontend action a 3DS input asks for. */
sealed interface N3dsAction {
    /** SELECT + Left (-1) / Right (+1): the next citra_layout_option ([N3dsOptions.cycleLayout]). */
    data class CycleLayout(val delta: Int) : N3dsAction
    /** SELECT + R: stylus mode turned on or off. */
    data class Stylus(val on: Boolean) : N3dsAction
}

/** [forward]: pass the event on to the core as is. [action]: what the frontend does. */
data class N3dsDecision(val forward: Boolean, val action: N3dsAction? = null)

/**
 * NX Redux's DS controls on the 3DS, before the core ([NdsHotkeys] for the DS), on SELECT chords: L2/R2 are the New 3DS's
 * ZL/ZR there, so they always reach the core. SELECT + L is Azahar's swap-screens button (sent as L3), SELECT + Left/Right
 * cycles the core's layout, SELECT + R toggles stylus mode: the left hand has the screens, the right hand the stylus.
 *
 * Many 3DS games pause on Select, so unlike the DS, SELECT is held back and never reaches the core while held: a chord
 * (START too, the host's menu chord) consumes it for that hold; released with no chord after a hold of at most
 * [SELECT_CANCEL_MS], it reaches the core as a tap ([SELECT_TAP_MS] long). Held longer than that with no chord, the release
 * sends nothing: a long hold cancels. The hold is timed by [tick]: exactly [SELECT_CANCEL_MS] still taps, any more cancels.
 *
 * Azahar draws its own touchscreen cursor, moved by the right analog stick and touching on R3 (citra_analog_function's
 * pointer, input/mouse_tracker.cpp), so in stylus mode the d-pad and left stick become the right stick ([cursor]) and A
 * becomes R3. The core moves its cursor by deflection x (bottom-screen width / 20) per frame, a screen in a third of a
 * second at full tilt, so a held d-pad starts just past the core's [deadzone] and speeds up the longer it is held; the
 * stick goes as it is. A release always goes where its press went.
 *
 * Pure: the host feeds events and ticks ([ticking]), sends [takeKeys] and [cursor] after each, and applies actions. Time
 * is only what [tick] is told.
 */
class N3dsHotkeys {
    companion object {
        /** D-pad deflection past the deadzone the moment a direction is pressed. */
        const val CURSOR_START = 0.05f
        /** Deflection gained per second a direction is held. */
        const val CURSOR_ACCEL = 0.6f
        const val DEFAULT_DEADZONE = N3dsOptions.DEFAULT_DEADZONE
        /** How long a held-back SELECT's tap is held in the core: three frames at 60 Hz. */
        const val SELECT_TAP_MS = 50L
        /** A SELECT held longer than this with no chord is cancelled: its release sends nothing. Exactly this still taps. */
        const val SELECT_CANCEL_MS = 1_000L
    }

    private enum class Select { IDLE, PENDING, CHORD, TAP }

    var stylus = false
        private set
    /**
     * A pad skin shows (host-set): its touch screen is under the finger and it places the screens, so SELECT + R (stylus)
     * and SELECT + Left / Right (layout) are kept from the core and do nothing; SELECT + L still swaps.
     */
    var skinShown = false
    /**
     * The skin lays the screens out by the player's layout (a regular phone's landscape, N3dsOptions.skinLayout; host-set):
     * SELECT + Left / Right cycles it there too.
     */
    var skinLayouts = false

    /** The core's right-stick deadzone (0..1, [N3dsOptions.deadzone]), host-set. */
    var deadzone: Float = DEFAULT_DEADZONE

    private var select = Select.IDLE
    private var tapMs = 0L                                 // TAP: pressed in the core so far
    private var selectMs = 0L                              // PENDING: SELECT held so far, for the cancel
    private val consumed = HashMap<NxButton, N3dsKey?>()   // pressed and kept from the core as itself -> the key it went as
    private val held = HashSet<NxButton>()                 // cursor directions held (stylus mode)
    private var heldMs = 0L
    private var stickX = 0f
    private var stickY = 0f
    private val out = ArrayList<N3dsKeyEvent>()

    private val selectHeld: Boolean get() = select == Select.PENDING || select == Select.CHORD

    /** The keys to send to the core since the last call, in order. */
    fun takeKeys(): List<N3dsKeyEvent> = out.toList().also { out.clear() }

    /**
     * The host keeps calling [tick] while this is true: a SELECT held and not yet past [SELECT_CANCEL_MS], a SELECT tapping,
     * or a d-pad cursor speeding up.
     */
    val ticking: Boolean
        get() = select == Select.TAP || (select == Select.PENDING && !selectCancelled) || (stylus && held.isNotEmpty())

    private val selectCancelled: Boolean get() = selectMs > SELECT_CANCEL_MS

    /** Stylus mode on or off: SELECT + R1, or the pad's STYLUS pill. Off lifts a touch held. */
    fun toggleStylus(): N3dsAction.Stylus {
        stylus = !stylus
        resetCursor()
        if (!stylus) liftTouch()
        return N3dsAction.Stylus(stylus)
    }

    fun onButton(button: NxButton, down: Boolean): N3dsDecision {
        if (button == NxButton.SELECT) {
            onSelect(down)
            return N3dsDecision(false)
        }
        if (!down) {
            if (button.isDirection) {
                held.remove(button)
                if (held.isEmpty()) heldMs = 0L
            }
            if (button !in consumed) return N3dsDecision(true)
            consumed.remove(button)?.let { out += N3dsKeyEvent(it, false) }
            return N3dsDecision(false)
        }
        // SELECT + START opens the in-game menu (the host's chord, not this): it too keeps SELECT from the core.
        if (button == NxButton.START && select == Select.PENDING) select = Select.CHORD
        val chord = selectHeld && button in CHORD_BUTTONS
        if (chord && select == Select.PENDING) select = Select.CHORD   // the core never sees this SELECT
        var key: N3dsKey? = null
        val decision = when {
            chord && button == NxButton.L1 -> { key = N3dsKey.L3; N3dsDecision(false) }
            chord && skinShown && !(skinLayouts && button.isHorizontal()) -> N3dsDecision(false)
            chord && button == NxButton.R1 -> N3dsDecision(false, toggleStylus())
            chord -> N3dsDecision(false, N3dsAction.CycleLayout(if (button == NxButton.RIGHT) 1 else -1))
            stylus && button.isDirection -> {
                if (held.isEmpty()) heldMs = 0L
                held += button
                N3dsDecision(false)
            }
            stylus && button == NxButton.A -> { key = N3dsKey.R3; N3dsDecision(false) }
            else -> N3dsDecision(true)
        }
        if (!decision.forward) {
            consumed[button] = key
            key?.let { out += N3dsKeyEvent(it, true) }
        }
        return decision
    }

    private fun onSelect(down: Boolean) {
        if (down) {
            if (select == Select.TAP) out += N3dsKeyEvent(N3dsKey.SELECT, false)   // the last tap ends early
            if (select == Select.PENDING || select == Select.CHORD) return       // a stray repeat: the hold goes on
            select = Select.PENDING
            selectMs = 0L
            return
        }
        when (select) {
            Select.PENDING -> {   // no chord: the game gets its Select now as a short tap, unless held so long it cancelled
                if (selectCancelled) {
                    select = Select.IDLE
                    return
                }
                out += N3dsKeyEvent(N3dsKey.SELECT, true)
                select = Select.TAP
                tapMs = 0L
            }
            Select.CHORD -> select = Select.IDLE
            Select.IDLE, Select.TAP -> Unit
        }
    }

    /** Key repeats of [button] are kept from the core exactly when its press was. SELECT is always the hotkeys'. */
    fun consumes(button: NxButton): Boolean = button == NxButton.SELECT || button in consumed

    /** The left stick; true when stylus mode takes it (then it is not the circle pad). */
    fun onStick(x: Float, y: Float): Boolean {
        stickX = x
        stickY = y
        return stylus
    }

    /**
     * Advances time by [dtMs]: a held SELECT's cancel, SELECT's tap ([takeKeys]) and the held d-pad's speed. True when
     * [cursor] changed.
     */
    fun tick(dtMs: Long): Boolean {
        if (dtMs <= 0L) return false
        if (select == Select.PENDING && !selectCancelled) selectMs += dtMs
        if (select == Select.TAP) {
            tapMs += dtMs
            if (tapMs >= SELECT_TAP_MS) {
                out += N3dsKeyEvent(N3dsKey.SELECT, false)
                select = Select.IDLE
            }
        }
        if (!stylus || held.isEmpty()) return false
        val before = cursor
        heldMs += dtMs
        return cursor != before
    }

    /**
     * The right stick stylus mode sends in the core's -1..1 (y down), or null when it sends none (stylus off, nothing held:
     * the hardware right stick goes through). A held d-pad wins over the stick.
     */
    val cursor: Pair<Float, Float>?
        get() {
            if (!stylus) return null
            val hx = (if (NxButton.RIGHT in held) 1 else 0) - (if (NxButton.LEFT in held) 1 else 0)
            val hy = (if (NxButton.DOWN in held) 1 else 0) - (if (NxButton.UP in held) 1 else 0)
            if (hx != 0 || hy != 0) {
                val d = min(1f, deadzone + CURSOR_START + CURSOR_ACCEL * heldMs / 1000f)
                return hx * d to hy * d
            }
            return if (stickX != 0f || stickY != 0f) stickX to stickY else null
        }

    /** One axis of a hardware hat as a d-pad direction (-1, 0, 1); the left stick is the circle pad (or the cursor), never the d-pad. */
    fun joystickDirection(hat: Float): Int = if (hat < -0.5f) -1 else if (hat > 0.5f) 1 else 0

    /**
     * The menu opened or the game paused: every key this sent is released ([takeKeys]: L3, R3, a tapping SELECT), held
     * buttons are forgotten and the cursor stops. Stylus mode stays as it is.
     */
    fun releaseAll() {
        if (select == Select.TAP) out += N3dsKeyEvent(N3dsKey.SELECT, false)
        select = Select.IDLE
        consumed.values.filterNotNull().distinct().forEach { out += N3dsKeyEvent(it, false) }
        consumed.clear()
        resetCursor()
    }

    /** Stylus off: a touch A held is lifted now, and its release is kept from the core. */
    private fun liftTouch() {
        consumed.entries.forEach { e ->
            if (e.value == N3dsKey.R3) {
                out += N3dsKeyEvent(N3dsKey.R3, false)
                e.setValue(null)
            }
        }
    }

    private fun resetCursor() {
        held.clear()
        heldMs = 0L
        stickX = 0f
        stickY = 0f
    }
}

private val CHORD_BUTTONS = setOf(NxButton.LEFT, NxButton.RIGHT, NxButton.L1, NxButton.R1)

/** LEFT or RIGHT: the layout chord's buttons. */
private fun NxButton.isHorizontal() = this == NxButton.LEFT || this == NxButton.RIGHT
