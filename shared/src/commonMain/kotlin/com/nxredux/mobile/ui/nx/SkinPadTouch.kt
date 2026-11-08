package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.skin.ControlShape
import com.nxredux.mobile.skin.PlacedControl
import com.nxredux.mobile.skin.SkinPlacement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A pad skin's touch handling, shared by the game's skin view and the main menu's: which control a point is on, the
 * eight-way d-pad, and fingers sliding between buttons. Pad buttons go to [onPad]; the skin's other buttons (MIC, SWAP,
 * SAVE, LOAD, REWIND, FAST) to [onAction] by their id, press and release. [tick] is the press haptic. Pointer ids are
 * the caller's (a View's pointer id, a Compose pointer's).
 */
class SkinPadTouch(
    private val onPad: (PadEvent) -> Unit,
    private val onAction: (String, Boolean) -> Unit,
    private val tick: () -> Unit,
    /** What is held changed: the pressed shade ([pressedShapes]) is drawn again. */
    private val onChange: () -> Unit = {},
    /**
     * The analog stick's position, x right and y down, each -1..1 and no longer than 1 together (as the drawn pad's
     * stick, [normalizeStick]); (0, 0) when let go.
     */
    private val onStick: (Float, Float) -> Unit = { _, _ -> },
    /** The 3DS C-stick nub (control `CSTICK`, the right analog), as [onStick]. */
    private val onCStick: (Float, Float) -> Unit = { _, _ -> },
) {
    /** The layout hit-tested; a new one lets go of everything held on the old. */
    var placement: SkinPlacement? = null
        set(value) {
            if (field == value) return
            releaseAll()
            field = value
            under = value?.let(::underCaps) ?: emptySet()
        }

    /**
     * The ids of the caps under the d-pad, the stick or the face buttons (the fold board's and landscape's MENU, DISK,
     * SELECT, START, swaps): they press only when no other touch is on the skin, and a finger never slides onto them, so a
     * thumb slipping off a cluster doesn't open the menu or flip the disk. Touch only: a controller's buttons are untouched.
     */
    private var under: Set<String> = emptySet()

    /** Fingers that landed on an under cap while something else was held: the pad's, but pressing nothing. */
    private val ignored = HashSet<Long>()

    private sealed interface Hit
    private data class Btn(val b: NxButton, val id: String) : Hit
    private data class Act(val id: String) : Hit
    private object Dpad : Hit
    private class Stick(val c: PlacedControl) : Hit

    /** Pointer id -> the buttons it holds (the d-pad holds up to two directions), or the action it holds. */
    private val held = HashMap<Long, Set<NxButton>>()
    private val heldAction = HashMap<Long, String>()
    /** A held stick: its finger, where it landed (the stick's centre for it: first touch centres it), the knob's offset. */
    private class StickHold(val finger: Long, val centre: Pair<Float, Float>, var knob: Pair<Float, Float> = 0f to 0f)
    /** Stick control id (`STICK`, `CSTICK`) -> its hold; each stick its own finger. */
    private val sticks = HashMap<String, StickHold>()
    private fun stickOf(finger: Long): String? = sticks.entries.firstOrNull { it.value.finger == finger }?.key
    private fun sendStick(stick: String, x: Float, y: Float) = if (stick == CSTICK) onCStick(x, y) else onStick(x, y)

    /** Whether [x], [y] (window px of the placement) is on one of the skin's controls. */
    fun hits(x: Float, y: Float): Boolean = hitAt(x, y) != null

    private fun hitAt(x: Float, y: Float): Hit? {
        val p = placement ?: return null
        // the sticks first, over a touch area a little larger than their rings: one wins a touch on the d-pad's edge (the
        // nearest, if two reach)
        p.controls.filter { it.spec.shape == ControlShape.STICK && hypot(x - it.x, y - it.y) <= it.spec.r * STICK_SLOP }
            .minByOrNull { hypot(x - it.x, y - it.y) }?.let { return Stick(it) }
        val slop = 22f * p.k
        for (c in p.controls) {
            val hit = when (c.spec.shape) {
                ControlShape.DPAD -> abs(x - c.x) <= c.spec.r + slop && abs(y - c.y) <= c.spec.r + slop
                ControlShape.CIRCLE -> hypot(x - c.x, y - c.y) <= c.spec.r
                ControlShape.STICK -> false
                ControlShape.RECT -> abs(x - c.x) <= c.spec.hw + slop && abs(y - c.y) <= c.spec.hh + slop
            }
            if (hit) return hitOf(c) ?: continue
        }
        return null
    }

    private fun hitOf(c: PlacedControl): Hit? = when (val id = c.spec.id) {
        "DPAD" -> Dpad
        in ACTIONS -> Act(id)
        else -> BUTTONS[id]?.let { Btn(it, id) }
    }

    /**
     * [p]'s caps under a cluster: below the d-pad's, the stick's or the face diamond's bottom edge, with the centre
     * within that cluster's width.
     */
    private fun underCaps(p: SkinPlacement): Set<String> {
        fun span(c: PlacedControl) = Triple(c.x - c.spec.r, c.x + c.spec.r, c.y + c.spec.r)
        val clusters = p.controls.filter { it.spec.shape == ControlShape.DPAD || it.spec.shape == ControlShape.STICK }.map(::span).toMutableList()
        p.controls.filter { it.spec.id in FACE }.map(::span).takeIf { it.isNotEmpty() }?.let { f ->
            clusters += Triple(f.minOf { it.first }, f.maxOf { it.second }, f.maxOf { it.third })
        }
        return p.controls.filter { c ->
            c.spec.shape != ControlShape.DPAD && c.spec.shape != ControlShape.STICK && c.spec.id !in FACE &&
                clusters.any { (l, r, bottom) -> c.y > bottom && c.x in l..r }
        }.map { it.spec.id }.toSet()
    }

    /** The d-pad's directions at [x], [y]: eight ways by angle, nothing in the dead centre. */
    private fun dpadAt(x: Float, y: Float): Set<NxButton> {
        val p = placement ?: return emptySet()
        val pad = p.controls.firstOrNull { it.spec.shape == ControlShape.DPAD } ?: return emptySet()
        val dx = x - pad.x
        val dy = y - pad.y
        if (hypot(dx, dy) < 22f * p.k) return emptySet()
        val a = (atan2(-dy.toDouble(), dx.toDouble()) * 180.0 / PI).let { if (it < 0) it + 360 else it }
        return when ((((a + 22.5) % 360) / 45).toInt()) {   // 0 = right, counter-clockwise
            0 -> setOf(NxButton.RIGHT); 1 -> setOf(NxButton.RIGHT, NxButton.UP); 2 -> setOf(NxButton.UP)
            3 -> setOf(NxButton.UP, NxButton.LEFT); 4 -> setOf(NxButton.LEFT); 5 -> setOf(NxButton.LEFT, NxButton.DOWN)
            6 -> setOf(NxButton.DOWN); else -> setOf(NxButton.DOWN, NxButton.RIGHT)
        }
    }

    private fun setHeld(id: Long, next: Set<NxButton>) {
        val prev = held[id] ?: emptySet()
        if (prev == next) { held.getOrPut(id) { next }; return }
        (prev - next).forEach { onPad(PadEvent.Up(it)) }
        (next - prev).forEach { onPad(PadEvent.Down(it)) }
        if ((next - prev).isNotEmpty()) tick()
        held[id] = next
        onChange()
    }

    private fun isUnder(h: Hit?): Boolean = when (h) {
        is Btn -> h.id in under
        is Act -> h.id in under
        else -> false
    }

    private fun anyHeld(): Boolean = held.isNotEmpty() || heldAction.isNotEmpty() || sticks.isNotEmpty()

    /** A finger down at [x], [y]: true when it landed on a control (it is then this pad's), false when on none. */
    fun press(id: Long, x: Float, y: Float): Boolean {
        val h = hitAt(x, y) ?: return false
        if (isUnder(h) && anyHeld()) { ignored += id; return true }
        when (h) {
            is Btn -> setHeld(id, setOf(h.b))
            Dpad -> setHeld(id, dpadAt(x, y))
            is Act -> { heldAction[id] = h.id; onAction(h.id, true); tick(); onChange() }
            is Stick -> {
                val sid = h.c.spec.id
                if (sid in sticks) return true   // a second finger on a held stick: the first keeps it
                sticks[sid] = StickHold(id, x to y)
                sendStick(sid, 0f, 0f); tick(); onChange()
            }
        }
        return true
    }

    /** [stick]'s knob offset from its centre, window px (no longer than its travel); (0, 0) while not held. */
    fun knobOffset(stick: String = STICK): Pair<Float, Float> = sticks[stick]?.knob ?: (0f to 0f)

    private fun moveStick(stick: String, x: Float, y: Float) {
        val hold = sticks[stick] ?: return
        val c = placement?.controls?.firstOrNull { it.spec.shape == ControlShape.STICK && it.spec.id == stick } ?: return
        val travel = if (c.spec.travel > 0f) c.spec.travel else c.spec.r * 0.6f
        val drawn = if (c.spec.knobTravel > 0f) c.spec.knobTravel else travel   // the knob's own (shorter) way
        val (nx, ny) = normalizeStick(x - hold.centre.first, y - hold.centre.second, travel)
        hold.knob = nx * drawn to ny * drawn
        sendStick(stick, nx, ny); onChange()
    }

    /** Whether the finger [id] went down on a control and is still held. */
    fun holds(id: Long): Boolean = id in held || id in heldAction || stickOf(id) != null || id in ignored

    /** A held finger moved: the d-pad keeps steering; a finger on a button slides to the button it is over. */
    fun move(id: Long, x: Float, y: Float) {
        stickOf(id)?.let { moveStick(it, x, y); return }
        if (id in heldAction) return
        val cur = held[id] ?: return
        val onDpad = cur.isEmpty() || cur.all { it.isDirection }
        val over = hitAt(x, y).takeUnless(::isUnder)   // never slide onto an under cap: as if over nothing
        if (onDpad && (over == Dpad || over == null)) setHeld(id, dpadAt(x, y))
        else if (over is Btn) setHeld(id, setOf(over.b))
    }

    fun release(id: Long) {
        ignored.remove(id)
        stickOf(id)?.let { sticks.remove(it); sendStick(it, 0f, 0f); onChange() }
        heldAction.remove(id)?.let { onAction(it, false); onChange() }
        held[id]?.let { setHeld(id, emptySet()) }
        held.remove(id)
    }

    fun releaseAll() = (held.keys + heldAction.keys + ignored + sticks.values.map { it.finger }).toList().forEach(::release)

    /**
     * The caps held now, as shapes in the placement's px, each shaded while held: a round cap, a rect's pill, the
     * d-pad's held arms (two on a diagonal), an action's slot.
     */
    fun pressedShapes(): List<SkinPressShape> {
        val p = placement ?: return emptyList()
        val buttons = held.values.flatten().toSet()
        val actions = heldAction.values.toSet()
        if (buttons.isEmpty() && actions.isEmpty() && sticks.isEmpty()) return emptyList()
        val out = ArrayList<SkinPressShape>()
        for (c in p.controls) {
            val s = c.spec
            when (s.shape) {
                // the knob, where it is now
                ControlShape.STICK -> sticks[s.id]?.let { hold -> val knob = hold.knob; out += SkinPressShape.Circle(c.x + knob.first, c.y + knob.second, if (s.capR > 0f) s.capR else s.r - (if (s.knobTravel > 0f) s.knobTravel else s.travel)) }
                ControlShape.DPAD -> {
                    val a = if (s.arm > 0f) s.arm else s.r / 3f
                    val l = s.r
                    if (NxButton.UP in buttons) out += SkinPressShape.Box(c.x - a, c.y - l, c.x + a, c.y - a, ARM_CORNER * p.k)
                    if (NxButton.DOWN in buttons) out += SkinPressShape.Box(c.x - a, c.y + a, c.x + a, c.y + l, ARM_CORNER * p.k)
                    if (NxButton.LEFT in buttons) out += SkinPressShape.Box(c.x - l, c.y - a, c.x - a, c.y + a, ARM_CORNER * p.k)
                    if (NxButton.RIGHT in buttons) out += SkinPressShape.Box(c.x + a, c.y - a, c.x + l, c.y + a, ARM_CORNER * p.k)
                }
                else -> {
                    val on = if (s.id in ACTIONS) s.id in actions else BUTTONS[s.id]?.let { it in buttons } == true
                    if (!on) continue
                    out += when (s.shape) {
                        ControlShape.CIRCLE -> SkinPressShape.Circle(c.x, c.y, if (s.capR > 0f) s.capR else s.r * 0.75f)
                        else -> SkinPressShape.Box(c.x - s.hw, c.y - s.hh, c.x + s.hw, c.y + s.hh, minOf(s.hw, s.hh))
                    }
                }
            }
        }
        return out
    }

    private companion object {
        const val ARM_CORNER = 10f
        /** The stick's touch area: its ring's radius times this. */
        const val STICK_SLOP = 1.25f
        /** The face buttons: the diamond, and on the N64 its C buttons (in the diamond's places) with B / A beside them. */
        val FACE = setOf("A", "B", "X", "Y", "CU", "CL", "CR", "CD")
        /** The stick control ids: the stick (the Circle Pad on a 3DS) and the 3DS C-stick nub. */
        const val STICK = "STICK"
        const val CSTICK = "CSTICK"
        val BUTTONS = mapOf(
            "A" to NxButton.A, "B" to NxButton.B, "X" to NxButton.X, "Y" to NxButton.Y, "L" to NxButton.L1, "R" to NxButton.R1, "L2" to NxButton.L2, "R2" to NxButton.R2,
            "DISK" to NxButton.L1,   // the FDS skin's DISK: fceumm's L, its one-press disk flip
            "CU" to NxButton.C_UP, "CL" to NxButton.C_LEFT, "CR" to NxButton.C_RIGHT, "CD" to NxButton.C_DOWN,   // the N64's C buttons
            "START" to NxButton.START, "SELECT" to NxButton.SELECT, "MENU" to NxButton.MENU,
            "HOME" to NxButton.HOME,   // the Wii's HOME
            "L3" to NxButton.L3, "R3" to NxButton.R3,   // the PlayStation's stick clicks
        )
        val ACTIONS = setOf("MIC", "SWAP", "SAVE", "LOAD", "REWIND", "FAST", "TOUCH_1", "TOUCH_2", "TOUCH_3", "TOUCH_4", "TURBO_A", "TURBO_B", "STICK_SWAP", "MOTION")
    }
}

/** A held cap's outline in window px, shaded [SKIN_PRESS_SHADE] black while held. */
sealed interface SkinPressShape {
    data class Circle(val x: Float, val y: Float, val r: Float) : SkinPressShape
    /** [left] .. [right] x [top] .. [bottom] with corners of [corner]. */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float, val corner: Float) : SkinPressShape
}

/** A control's label ([com.nxredux.mobile.skin.ControlSpec.label]): MiSans SemiBold, this grey (the skin's printed labels). */
const val SKIN_LABEL_GREY = 0xFFC4C4C4.toInt()

/** MiSans SemiBold's cap height in ems: the text size that gives a label its cap height. */
const val SKIN_LABEL_CAP = 0.739f

/** A repeating slice at most this many picture px across is stretched rather than tiled (a plain strip: the same look). */
const val SKIN_STRETCH_MAX = 4f

/** How dark a held cap goes: this much black over it. */
const val SKIN_PRESS_SHADE = 0.3f
