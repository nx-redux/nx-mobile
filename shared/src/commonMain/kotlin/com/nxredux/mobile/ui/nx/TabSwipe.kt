package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/*
 * Swiping between the main menu's tabs on a page that scrolls vertically (List, Minimal, Grid, vertical stack; a
 * horizontal Carousel or Backdrop keeps the swipe for its own row). The page follows the finger, the fixed chrome
 * (tab row, top header / stats, top-right icons: [pageFixed]) staying put; past [TAB_SWIPE_COMMIT] of the width, or on
 * a fling, the next tab (finger right to left) or the previous glides to rest carrying the release velocity
 * ([TabSwipe.launchSettle]), then commits once settled so no neighbour page is composed mid-slide and the fixed chrome
 * swaps cleanly at the end ([TabPager]), wrapping over the shown tabs as L1 / R1 do; short of it the page glides back.
 * The first [TAB_SWIPE_LOCK_DP] decide the axis: within [TAB_SWIPE_ANGLE] degrees of horizontal a tab swipe, otherwise
 * the list's scroll. A swipe starting in the system's back-gesture zone at either edge is left to it.
 */

/** Critically damped settle spring stiffness (zero overshoot, velocity-continuous). */
internal const val TAB_SWIPE_STIFFNESS = 900f

/** Stop settling once within a pixel of rest so the commit does not wait on sub-pixel tails. */
internal const val TAB_SWIPE_VISIBILITY_PX = 1f

/** The share of the width a drag must pass to commit. */
internal const val TAB_SWIPE_COMMIT = 0.3f

/** The finger's travel that locks the axis (12 dp * sin 30° = 6 dp, under Android's 8 dp scroll slop). */
internal const val TAB_SWIPE_LOCK_DP = 12f

/** The most a tab swipe may lean off horizontal, degrees. */
internal const val TAB_SWIPE_ANGLE = 30f

/** A release at least this fast (dp/s) in the drag's direction commits whatever the distance: a fling. */
internal const val TAB_SWIPE_FLING_DP = 500f

/** The axis a move of [dx], [dy] px locks to once it travels [lockPx]: null before, true for a tab swipe. */
internal fun tabSwipeAxis(dx: Float, dy: Float, lockPx: Float): Boolean? {
    if (dx * dx + dy * dy < lockPx * lockPx) return null
    return atan2(abs(dy), abs(dx)) * 180f / PI.toFloat() <= TAB_SWIPE_ANGLE
}

/**
 * The tab step a release at [drag] px of [width] at [velocity] px/s ([flingPx] px/s a fling) makes: 1 (the next: the
 * finger went left), −1 (the previous) or 0 (back to the page).
 */
internal fun tabSwipeStep(drag: Float, width: Float, velocity: Float, flingPx: Float): Int {
    if (drag == 0f) return 0
    val dir = if (drag < 0f) 1 else -1
    val fling = abs(velocity) >= flingPx && (velocity < 0f) == (drag < 0f)
    return if (abs(drag) >= TAB_SWIPE_COMMIT * width || fling) dir else 0
}

/** The drag target in px for [step] (1: next tab at -[width], -1: previous at +[width], 0: rest at 0). */
internal fun tabSwipeTarget(step: Int, width: Float): Float = if (step == 0) 0f else -step * width

/**
 * Release [velocity] (px/s) clamped for a critically damped spring of [stiffness] settling from [drag] to [target]:
 * preserves finger momentum while guaranteeing zero overshoot past [target] (and on a [commit], preventing reverse
 * velocity from pulling backward before sliding to [target]).
 */
internal fun tabSwipeSettleVelocity(drag: Float, target: Float, velocity: Float, stiffness: Float, commit: Boolean): Float {
    val d = drag - target
    if (d == 0f) return 0f
    val omega = sqrt(stiffness)
    val bound = -omega * d
    return if (d > 0f) {
        if (commit) velocity.coerceIn(bound, 0f) else velocity.coerceAtLeast(bound)
    } else {
        if (commit) velocity.coerceIn(0f, bound) else velocity.coerceAtMost(bound)
    }
}

/**
 * The main menu's tab swipe: [drag], px, the pages' shift (the finger's, then a commit's or a return's settle, [settle]);
 * [pending], the tab step a settled commit asked for, cleared when the new tab shows ([TabPager]); [width], the pages' px.
 */
@Stable
internal class TabSwipe {
    var drag by mutableFloatStateOf(0f)
    var pending = 0
    var width = 0f
    val settle = Animatable(0f)
    internal var scope: CoroutineScope? = null
    private var settleJob: Job? = null
    private var settleGen = 0

    /** Stops any settle in flight immediately; queued frame callbacks cannot touch [drag] after this. */
    fun cancelSettle() {
        settleGen++
        settleJob?.cancel()
        settleJob = null
    }

    /**
     * Smoothly settles [drag] from where it is to [to] px, carrying release [velocity] without overshooting [to].
     * When [commitStep] is non-zero, [onCommit] runs once the incoming page has reached rest so no neighbour tab is
     * composed mid-slide and fixed headers swap cleanly at the end.
     */
    fun launchSettle(
        scope: CoroutineScope,
        to: Float,
        velocity: Float = 0f,
        commitStep: Int = 0,
        onCommit: (() -> Unit)? = null,
    ) {
        cancelSettle()
        val gen = settleGen
        settleJob = scope.launch {
            val v = tabSwipeSettleVelocity(drag, to, velocity, TAB_SWIPE_STIFFNESS, commit = commitStep != 0)
            settle.snapTo(drag)
            settle.animateTo(
                targetValue = to,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = TAB_SWIPE_STIFFNESS,
                    visibilityThreshold = TAB_SWIPE_VISIBILITY_PX,
                ),
                initialVelocity = v,
            ) {
                if (settleGen == gen) drag = value
            }
            if (settleGen != gen) return@launch
            drag = to
            settleJob = null
            if (commitStep != 0 && onCommit != null) {
                pending = commitStep
                onCommit()
                // If AppState ignored the switch (e.g. loader active), glide back so the page is never stranded.
                withFrameNanos { }
                if (settleGen == gen && pending != 0) {
                    pending = 0
                    launchSettle(scope, 0f)
                }
            }
        }
    }
}

/** App's tab swipe; null outside the main menu's pages. */
internal val LocalTabSwipe = staticCompositionLocalOf<TabSwipe?> { null }

/**
 * A page's slide between tabs: [shift], px for a page that wide (the live page's with the finger, then each page's
 * through the settle), and whether its fixed parts show ([fixedShown]). The fixed parts (the tab row, the top title /
 * stats header, the top-right icons: [pageFixed]) counter-shift so they never move, and swap at once when a settled
 * swipe commits: the old page's hide as the new one's show, so nothing fades or blinks there. [width]: the pages', as
 * last laid out.
 */
internal class PageSlide(val shift: (width: Float) -> Float, val fixedShown: () -> Boolean, val width: () -> Float = { 0f })

internal val LocalPageSlide = compositionLocalOf { PageSlide({ 0f }, { true }) }

/** A fixed part of a main menu page ([PageSlide]): still while the page slides between tabs, shown only on the page that has the tab. */
@Composable
internal fun Modifier.pageFixed(): Modifier {
    val slide = LocalPageSlide.current
    return graphicsLayer { translationX = -slide.shift(slide.width()); alpha = if (slide.fixedShown()) 1f else 0f }
}

/** The tab swipe on a main menu page ([enabled]); a settled commit calls [onStep] with 1 (next) or −1 (previous). */
@Composable
internal fun Modifier.tabSwipe(enabled: Boolean, onStep: (Int) -> Unit): Modifier {
    val swipe = LocalTabSwipe.current ?: return this
    if (!enabled) return this
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val edges = WindowInsets.systemGestures
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    return pointerInput(swipe) {
        val lock = TAB_SWIPE_LOCK_DP * density.density
        val fling = TAB_SWIPE_FLING_DP * density.density
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val left = edges.getLeft(density, dir)
            val right = edges.getRight(density, dir)
            if (down.position.x < left || down.position.x > size.width - right) return@awaitEachGesture
            val tracker = VelocityTracker().apply { addPointerInputChange(down) }
            var total = Offset.Zero
            var horizontal: Boolean? = null
            var base = 0f   // where a settle still under way had the pages when the finger took them
            var lockX = 0f  // horizontal travel already spent locking the axis, subtracted so drag starts with zero jump
            val w = size.width.toFloat()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (horizontal == true) change.consume()
                    break
                }
                tracker.addPointerInputChange(change)
                total += change.positionChange()
                if (horizontal == null) {
                    horizontal = tabSwipeAxis(total.x, total.y, lock)
                    if (horizontal == false) return@awaitEachGesture   // the list's scroll
                    if (horizontal == true) {
                        swipe.cancelSettle()
                        base = swipe.drag
                        lockX = total.x
                    }
                }
                if (horizontal == true) {
                    change.consume()   // the list under it never sees it
                    swipe.drag = if (w > 0f) (base + (total.x - lockX)).coerceIn(-w, w) else 0f
                }
            }
            if (horizontal != true || w <= 0f) return@awaitEachGesture
            val velocity = tracker.calculateVelocity().x
            val effectiveDrag = if (swipe.drag != 0f) swipe.drag else total.x
            val s = tabSwipeStep(effectiveDrag, w, velocity, fling)
            val target = tabSwipeTarget(s, w)
            swipe.launchSettle(swipe.scope ?: scope, target, velocity, commitStep = s, onCommit = if (s != 0) ({ step(s) }) else null)
        }
    }
}
