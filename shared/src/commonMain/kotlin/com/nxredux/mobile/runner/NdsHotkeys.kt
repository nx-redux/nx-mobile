package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.math.hypot
import kotlin.math.min

/** A frontend action an NDS input asks for. */
sealed interface NdsAction {
    /** L3, SELECT + L1 or L2: the other screen becomes the big one. */
    data object SwapScreens : NdsAction
    /** SELECT + Left (-1) / Right (+1): the next layout for the current orientation. */
    data class CycleLayout(val delta: Int) : NdsAction
    /** SELECT + Up (-1) / Down (+1): the picture-in-picture inset to the next corner, clockwise. */
    data class CycleCorner(val delta: Int) : NdsAction
    /** SELECT + R1 or R2: stylus mode turned on or off. */
    data class Stylus(val on: Boolean) : NdsAction
}

/** [forward]: pass the event on to the core. [action]: what the frontend does. */
data class NdsDecision(val forward: Boolean, val action: NdsAction? = null)

/** The stylus pen in DS bottom-screen pixels (x 0..255, y 0..191), and whether it touches. */
data class NdsPen(val x: Float, val y: Float, val pressed: Boolean) {
    /** In 0..1 of the stacked 256x384 core frame: the bottom half. */
    val frameX: Float get() = (x + 0.5f) / 256f
    val frameY: Float get() = 0.5f + (y + 0.5f) / 192f * 0.5f
}

/**
 * NX Redux's DS controls (nxredux.com/emulators/nintendo-ds), before the core. As on the 3DS ([N3dsHotkeys]): L3 or
 * SELECT + L1 swaps screens, SELECT + R1 toggles stylus mode, SELECT + Left/Right cycles the layout; L2 (swap) and R2
 * (stylus) too (the left side the screens, the right side the stylus, as L3 and R3), and SELECT + Up/Down moves the inset. In stylus mode the right stick, the left stick and the d-pad move
 * a pen on the bottom screen (the d-pad faster the longer a direction is held, DraStic-like), and R3 or A touches at it.
 * A release always goes where its press went. L3, L2 and R2 never reach the core. Pure: the host feeds events and
 * ticks, and applies actions.
 */
class NdsHotkeys {
    companion object {
        /** DS pixels per second the moment a direction is pressed. */
        const val PEN_START_SPEED = 48f
        /** Speed gained per second a direction is held (px/s²). */
        const val PEN_ACCEL = 320f
        /** Top speed, for the d-pad and a full stick (px/s). */
        const val PEN_MAX_SPEED = 360f
        const val STICK_DEADZONE = 0.2f
        private const val MAX_X = 255f
        private const val MAX_Y = 191f
        private val START = NdsPen(128f, 96f, pressed = false)
    }

    var stylus = false
        private set
    var pen = START
        private set

    private var selectHeld = false
    private val consumed = HashSet<NxButton>()   // pressed and kept from the core: their releases are kept too
    private val held = HashSet<NxButton>()       // pen directions held (stylus mode)
    private var heldMs = 0L                      // how long a direction has been held
    private var stickX = 0f
    private var stickY = 0f
    private var rightX = 0f
    private var rightY = 0f

    /** Stylus mode on or off: SELECT + R1, R2, or the pad's STYLUS pill. Off lifts the pen. */
    fun toggleStylus(): NdsAction.Stylus {
        stylus = !stylus
        resetPen()   // off: lift the pen; on: a stick already held does not move it at once
        return NdsAction.Stylus(stylus)
    }

    /** A pad skin shows (host-set): where it places the screens itself ([skinLayouts] false) SELECT + Left / Right does nothing. */
    var skinShown = false
    /** The skin lays the screens out by the player's layout (a regular phone's landscape, NdsOptions.skinLayout; host-set). */
    var skinLayouts = false

    fun onButton(button: NxButton, down: Boolean): NdsDecision {
        if (button == NxButton.SELECT) selectHeld = down   // SELECT itself still reaches the core
        if (!down) {
            val wasConsumed = consumed.remove(button)
            if (button.isDirection) {
                held.remove(button)
                if (held.isEmpty()) heldMs = 0L
            }
            if ((button == NxButton.A || button == NxButton.R3) && wasConsumed) pen = pen.copy(pressed = false)
            return NdsDecision(forward = !wasConsumed)
        }
        val decision = when {
            button == NxButton.L2 || button == NxButton.L3 -> NdsDecision(false, NdsAction.SwapScreens)
            selectHeld && button == NxButton.L1 -> NdsDecision(false, NdsAction.SwapScreens)
            button == NxButton.R2 -> NdsDecision(false, toggleStylus())
            selectHeld && button == NxButton.R1 -> NdsDecision(false, toggleStylus())
            selectHeld && (button == NxButton.LEFT || button == NxButton.RIGHT) ->
                if (skinShown && !skinLayouts) NdsDecision(false)   // the skin places the screens: kept, nothing to cycle
                else NdsDecision(false, NdsAction.CycleLayout(if (button == NxButton.RIGHT) 1 else -1))
            selectHeld && (button == NxButton.UP || button == NxButton.DOWN) ->
                NdsDecision(false, NdsAction.CycleCorner(if (button == NxButton.DOWN) 1 else -1))
            stylus && button.isDirection -> {
                if (held.isEmpty()) heldMs = 0L
                held += button
                NdsDecision(false)
            }
            stylus && (button == NxButton.A || button == NxButton.R3) -> {
                pen = pen.copy(pressed = true)
                NdsDecision(false)
            }
            else -> NdsDecision(true)
        }
        if (!decision.forward) consumed += button
        return decision
    }

    /** Key repeats of [button] are kept from the core exactly when its press was. */
    fun consumes(button: NxButton): Boolean = button in consumed

    /** The left stick; true when stylus mode takes it (then it is not forwarded). */
    fun onStick(x: Float, y: Float): Boolean {
        stickX = x
        stickY = y
        return stylus
    }

    /** The right stick; true when stylus mode takes it (then it is not forwarded). */
    fun onRightStick(x: Float, y: Float): Boolean {
        rightX = x
        rightY = y
        return stylus
    }

    /** Moves the pen by [dtMs] of held directions and sticks; true when it moved. */
    fun tick(dtMs: Long): Boolean {
        if (!stylus || dtMs <= 0L) return false
        return move(dtMs)
    }

    private fun stickStep(x: Float, y: Float, dt: Float): Pair<Float, Float> {
        val magnitude = hypot(x, y)
        if (magnitude <= STICK_DEADZONE) return 0f to 0f
        val speed = min(1f, (magnitude - STICK_DEADZONE) / (1f - STICK_DEADZONE)) * PEN_MAX_SPEED
        return x / magnitude * speed * dt to y / magnitude * speed * dt
    }

    private fun move(dtMs: Long): Boolean {
        val dt = dtMs / 1000f
        var dx = 0f
        var dy = 0f
        val hx = (if (NxButton.RIGHT in held) 1 else 0) - (if (NxButton.LEFT in held) 1 else 0)
        val hy = (if (NxButton.DOWN in held) 1 else 0) - (if (NxButton.UP in held) 1 else 0)
        if (hx != 0 || hy != 0) {
            val speed = min(PEN_START_SPEED + PEN_ACCEL * heldMs / 1000f, PEN_MAX_SPEED)
            heldMs += dtMs
            dx += hx * speed * dt
            dy += hy * speed * dt
        }
        stickStep(stickX, stickY, dt).let { (sx, sy) -> dx += sx; dy += sy }
        stickStep(rightX, rightY, dt).let { (sx, sy) -> dx += sx; dy += sy }
        if (dx == 0f && dy == 0f) return false
        val x = (pen.x + dx).coerceIn(0f, MAX_X)
        val y = (pen.y + dy).coerceIn(0f, MAX_Y)
        if (x == pen.x && y == pen.y) return false
        pen = pen.copy(x = x, y = y)
        return true
    }

    /**
     * One axis of a hardware joystick as a d-pad direction (-1, 0, 1): the hat, else the left stick, which stands in for
     * the d-pad (the core has no left-stick input) except in stylus mode, where it is the pen ([onStick]). The host
     * consumes its joystick events and routes these directions itself, so Android synthesises no d-pad keys from them.
     */
    fun joystickDirection(hat: Float, stick: Float): Int {
        fun dir(v: Float) = if (v < -0.5f) -1 else if (v > 0.5f) 1 else 0
        return dir(hat).takeIf { it != 0 } ?: if (stylus) 0 else dir(stick)
    }

    /** The menu opened: forget held buttons and lift the pen; stylus mode stays as it is. */
    fun releaseAll() {
        selectHeld = false
        consumed.clear()
        resetPen()
    }

    /** Stops the pen: no held direction, centred sticks, the pen lifted. Consumed presses stay consumed. */
    private fun resetPen() {
        held.clear()
        heldMs = 0L
        stickX = 0f
        stickY = 0f
        rightX = 0f
        rightY = 0f
        pen = pen.copy(pressed = false)
    }
}
