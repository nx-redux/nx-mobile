package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How far a release carries on, in seconds of its velocity: a release at v dp/s lands v × this further along. A quick
 * flick (about 2000 dp/s) carries some 500 dp, two to three row items.
 */
internal const val SWIPE_FLING_S = 0.25f

/**
 * The natural frequencies (rad/s) a released swipe's settle spring may take ([swipeSettleOmega]): stiffness 64 to 400
 * (unit mass). A release from rest takes the stiffest, settling in about the d-pad slide's time.
 */
internal const val SWIPE_SETTLE_OMEGA_MIN = 8f
internal const val SWIPE_SETTLE_OMEGA_MAX = 20f

/** A finger speed (dp/s) from which a release may take the softest spring; slower ones are held stiffer, towards rest's. */
internal const val SWIPE_SETTLE_SLOW_DP = 300f

/**
 * The frequency of the critically damped spring a release from [from] at [velocity] (value units / s; the finger at
 * [speedDp] dp/s) settles to [to] on: 2 × speed / distance, where the spring starts at the finger's speed with no
 * acceleration and only slows from there (a stiffer one would first surge ahead of the finger: a rest projected
 * [SWIPE_FLING_S] on is well ahead of it). Held within [SWIPE_SETTLE_OMEGA_MAX] and a floor that falls from it (a
 * still finger: a quick snap to the nearest item) to [SWIPE_SETTLE_OMEGA_MIN] at [SWIPE_SETTLE_SLOW_DP]. No velocity
 * towards [to]: the floor.
 */
internal fun swipeSettleOmega(from: Float, to: Float, velocity: Float, speedDp: Float): Float {
    val moving = (abs(speedDp) / SWIPE_SETTLE_SLOW_DP).coerceAtMost(1f)
    val floor = SWIPE_SETTLE_OMEGA_MAX - (SWIPE_SETTLE_OMEGA_MAX - SWIPE_SETTLE_OMEGA_MIN) * moving
    val distance = to - from
    val towards = if (distance > 0f) velocity else -velocity
    if (towards <= 0f || distance == 0f) return floor
    return (2f * towards / abs(distance)).coerceIn(floor, SWIPE_SETTLE_OMEGA_MAX)
}

/**
 * The spring a released swipe settles on: critically damped (no bounce) at frequency [omega] (stiffness omega²), ending
 * within [threshold] (value units; a tenth of a dp of the row's movement, so the last step is never visible).
 */
internal fun swipeSettleSpec(omega: Float, threshold: Float): SpringSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = omega * omega, visibilityThreshold = threshold)

/**
 * The velocity (value units / s) a settle from [from] to [to] at frequency [omega] starts with: the finger's
 * [velocity], so the row carries on at the speed it was dragged rather than stopping and starting a fresh ease. Held
 * within [omega] × the distance: from there a critically damped spring can't pass [to] (a flick at the last item
 * doesn't bounce past it), and a velocity away from [to] carries it at most about a fifth of the distance further.
 */
internal fun swipeSettleVelocity(from: Float, to: Float, velocity: Float, omega: Float): Float {
    val cap = omega * abs(to - from)
    return velocity.coerceIn(-cap, cap)
}

/**
 * How far [swipeRubber] moves per unit of [value]: 1 inside [lo]..[hi], less and less past them (0 for a hard clamp),
 * so a release in the resistance hands the settle the speed the row was really moving at, not the finger's.
 */
internal fun swipeRubberSlope(value: Float, lo: Float, hi: Float, give: Float): Float {
    val over = when {
        value > hi -> value - hi
        value < lo -> lo - value
        else -> return 1f
    }
    if (give <= 0f) return 0f
    return give * give / ((over + give) * (over + give))
}

/**
 * Animates to [target] over [spec], unless a released swipe is already settling there: its spring keeps the finger's
 * velocity, and an eased restart towards the same place would drop it. A d-pad step (a new target) always eases.
 */
internal suspend fun Animatable<Float, AnimationVector1D>.easeUnlessSettling(target: Float, spec: AnimationSpec<Float>) {
    if (isRunning && targetValue == target) return
    animateTo(target, spec)
}

/**
 * [value] held between [lo] and [hi], with resistance past them: the overshoot eases towards [give] and never reaches it,
 * so a drag past an end moves the row a little, less and less.
 */
internal fun swipeRubber(value: Float, lo: Float, hi: Float, give: Float): Float {
    if (give <= 0f) return value.coerceIn(lo, hi)
    val over = when {
        value > hi -> value - hi
        value < lo -> value - lo
        else -> return value
    }
    val eased = give * abs(over) / (abs(over) + give)
    return if (over > 0f) hi + eased else lo - eased
}

/**
 * dp the finger moves for the first step of a Carousel / Backdrop row's position: [rowItem]'s first step, the
 * selection's centre to its neighbour's. Later steps are each pair of side items' ([rowSwipeDelta]).
 */
internal fun rowSwipeStep(g: RowGeometry): Float = g.restDx(1f)

/**
 * The row's position change for a finger drag of [dragDp] (right positive, towards the first item): [rowItem]'s offsets
 * inverted ([RowGeometry.restDx]), so the item centred when the drag began stays under the finger, step by step (the
 * side items are smaller, so closer; a Carousel's, tapering, closer still further out). A slow drag keeps pace with the row.
 */
internal fun rowSwipeDelta(dragDp: Float, g: RowGeometry): Float {
    if (rowSwipeStep(g) <= 0f) return 0f
    val steps = rowSwipeAt(abs(dragDp), g).first
    return if (dragDp > 0f) -steps else steps
}

/**
 * The steps a drag of [d] dp (≥ 0) covers ([RowGeometry.restDx] inverted: it only grows), and the dp per step there
 * (its rate, an item's width and the gap). Past [TAPER_STEPS] the rate is constant; before, a bisection.
 */
private fun rowSwipeAt(d: Float, g: RowGeometry): Pair<Float, Float> {
    fun rate(s: Float) = if (s < 1f) g.rowFirstStep() else g.itemW * g.sideScale(s) + g.gap
    val far = g.restDx(TAPER_STEPS)
    if (d >= far) return rate(TAPER_STEPS).let { r -> if (r <= 0f) TAPER_STEPS to 0f else (TAPER_STEPS + (d - far) / r) to r }
    var lo = 0f
    var hi = TAPER_STEPS
    repeat(32) { val mid = (lo + hi) / 2; if (g.restDx(mid) < d) lo = mid else hi = mid }
    val s = (lo + hi) / 2
    return s to rate(s)
}

/** [rowSwipeDelta]'s rate, position per dp, at a drag of [dragDp]: the settle's starting speed. */
internal fun rowSwipeSlope(dragDp: Float, g: RowGeometry): Float =
    rowSwipeAt(abs(dragDp), g).second.let { if (it <= 0f) 0f else -1f / it }

/**
 * A Carousel / Backdrop row's selection after a swipe from position [startPos], released [dragDp] along (right positive,
 * which moves the row towards the first item) at [velocityDp] dp/s: the drag projected [SWIPE_FLING_S] on, moved by
 * the items' positions ([rowSwipeDelta]), rounded to the nearest item and kept in the list.
 */
internal fun rowSwipeTarget(startPos: Float, dragDp: Float, velocityDp: Float, count: Int, g: RowGeometry): Int {
    if (count <= 0) return 0
    if (rowSwipeStep(g) <= 0f) return startPos.roundToInt().coerceIn(0, count - 1)
    return (startPos + rowSwipeDelta(dragDp + velocityDp * SWIPE_FLING_S, g)).roundToInt().coerceIn(0, count - 1)
}

/**
 * A horizontal swipe over [value] (a row's position): while a finger drags, [live] holds the value
 * the row shows (NaN otherwise; the caller draws `live` when set, else [value]), moved [toValue] for the finger's drag
 * (by default [perDp] per dp it moves right), held in [lo]..[hi] with [give] of resistance ([swipeRubber]). On release,
 * [target] (start value, drag in dp, velocity in dp/s) picks the index to select and the value to rest on: [value]
 * settles there on a critically damped spring that starts at the finger's velocity and only slows from there
 * ([swipeSettleOmega], [swipeSettleVelocity]; the owner's own animation to a new selection goes through
 * [easeUnlessSettling]), and [onSelect] is called with the index.
 * A cancel eases back over [ms]. The selection never changes while the finger is down.
 * Apply this before any translating `graphicsLayer`, so the finger's positions are in a frame that doesn't move.
 * The items keep their own taps: a drag past the touch slop consumes the pointer, which cancels their tap and long press.
 */
@Composable
internal fun Modifier.horizontalSwipe(
    key: Any?,
    value: Animatable<Float, AnimationVector1D>,
    live: MutableFloatState,
    perDp: Float, lo: Float, hi: Float, give: Float, ms: Int,
    target: (start: Float, dragDp: Float, velocityDp: Float) -> Pair<Int, Float>,
    onSelect: (Int) -> Unit,
    toValue: (dragDp: Float) -> Float = { it * perDp },   // the value's change for a drag (a row: its items' positions)
    slope: (dragDp: Float) -> Float = { perDp },          // [toValue]'s rate there, for the settle's starting speed
): Modifier = axisSwipe(false, key, value, live, perDp, lo, hi, give, ms, target, onSelect, toValue, slope)

/**
 * [horizontalSwipe] on either axis: [vertical] drags on Y (a vertical stack), where a drag down is positive, as a drag
 * right is on X (towards the first item), so the row's [rowSwipeDelta] / [rowSwipeTarget] / [rowSwipeSlope] work
 * unchanged on the stack's [StackGeometry.asRow].
 */
@Composable
internal fun Modifier.axisSwipe(
    vertical: Boolean,
    key: Any?,
    value: Animatable<Float, AnimationVector1D>,
    live: MutableFloatState,
    perDp: Float, lo: Float, hi: Float, give: Float, ms: Int,
    target: (start: Float, dragDp: Float, velocityDp: Float) -> Pair<Int, Float>,
    onSelect: (Int) -> Unit,
    toValue: (dragDp: Float) -> Float = { it * perDp },
    slope: (dragDp: Float) -> Float = { perDp },
): Modifier {
    val map by rememberUpdatedState(toValue)
    val rate by rememberUpdatedState(slope)
    val scope = rememberCoroutineScope()
    val pick by rememberUpdatedState(target)
    val select by rememberUpdatedState(onSelect)
    return pointerInput(vertical, key, value, live, perDp, lo, hi, give, ms) {
        val tracker = VelocityTracker()
        val drag = SwipeDrag(live)
        var heading = value.value   // where value was going when the drag stopped it: a cancelled drag eases back there
        // A finger release: pick the index and rest, settle there carrying the finger's velocity, select.
        fun release(start: Float, dragDp: Float, velocityDp: Float) {
            val (index, rest) = pick(start, dragDp, velocityDp)
            val from = live.floatValue
            val mine = drag.gesture
            val shown = if (from.isNaN()) value.value else from
            // The row's own speed: the finger's, in value units, slowed as swipeRubber slows it past an end.
            val speed = velocityDp * rate(dragDp) * swipeRubberSlope(start + map(dragDp), lo, hi, give)
            // Undispatched: value is settling towards rest before select() recomposes, so the owner's d-pad ease
            // (easeUnlessSettling) sees the settle and leaves it alone.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (mine != drag.gesture) return@launch
                if (!from.isNaN()) value.snapTo(from)
                if (mine != drag.gesture) return@launch
                live.floatValue = Float.NaN
                val omega = swipeSettleOmega(shown, rest, speed, velocityDp)
                value.animateTo(rest, swipeSettleSpec(omega, abs(perDp) * 0.1f), swipeSettleVelocity(shown, rest, speed, omega))
            }
            select(index)
        }
        // A cancel: the live drag clears and the row eases back to where it was going. It never selects.
        fun cancel() {
            val from = drag.cancel() ?: return
            val mine = drag.gesture
            // Undispatched: value takes the shown x in this same frame, so the row doesn't flash back first.
            if (scope.isActive) scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (mine != drag.gesture) return@launch
                if (!from.isNaN()) value.snapTo(from)
                value.animateTo(heading, tween(ms, easing = NxMenuEasing))
            }
        }
        val onStart = { _: Offset ->
            tracker.resetTracking()
            heading = value.targetValue
            drag.begin(value.value)   // a release still settling: starts from what is on screen
            scope.launch(start = CoroutineStart.UNDISPATCHED) { value.stop() }   // held under the finger now
            Unit
        }
        val onEnd = {
            val v = tracker.calculateVelocity()
            drag.release((if (vertical) v.y else v.x) / density, ::release)
        }
        val onMove = { change: PointerInputChange, amount: Float ->
            change.consume()
            tracker.addPointerInputChange(change)
            drag.move(amount / density, map, lo, hi, give)
        }
        try {
            if (vertical) detectVerticalDragGestures(onDragStart = onStart, onDragEnd = onEnd, onDragCancel = { cancel() }, onVerticalDrag = onMove)
            else detectHorizontalDragGestures(onDragStart = onStart, onDragEnd = onEnd, onDragCancel = { cancel() }, onHorizontalDrag = onMove)
        } finally {
            // The detector stopped mid-drag (a key changed: a tab switch, a rotation, a fold, the pad showing or hiding;
            // or the row left). The pick and select now belong to the new layout, so this only clears the drag.
            cancel()
        }
    }
}

/**
 * A [horizontalSwipe]'s finger state: [live] (the value shown while a finger is down, NaN otherwise) and the drag so far.
 * A drag ends exactly once: [release] (the finger lifted: hands the drag to its callback, which selects) or [cancel]
 * (anything else: clears [live], never selects).
 */
internal class SwipeDrag(private val live: MutableFloatState) {
    var start = 0f; private set
    var dragDp = 0f; private set
    var gesture = 0; private set   // a release's coroutine hands the row back to value only if no new drag has begun since
    private var down = false

    fun begin(resting: Float) {
        gesture++
        down = true
        start = live.floatValue.takeUnless { it.isNaN() } ?: resting
        dragDp = 0f
        live.floatValue = start
    }

    fun move(dp: Float, toValue: (Float) -> Float, lo: Float, hi: Float, give: Float) {
        dragDp += dp
        live.floatValue = swipeRubber(start + toValue(dragDp), lo, hi, give)
    }

    /** The finger lifted at [velocityDp]: [onRelease] (start, drag, velocity) once, if a drag was under way. */
    fun release(velocityDp: Float, onRelease: (start: Float, dragDp: Float, velocityDp: Float) -> Unit) {
        if (!down) return
        down = false
        onRelease(start, dragDp, velocityDp)
    }

    /** Anything but a lift ended the drag: clears [live] and returns what it showed; null if no drag was under way. */
    fun cancel(): Float? {
        if (!down) return null
        down = false
        return live.floatValue.also { live.floatValue = Float.NaN }
    }
}
