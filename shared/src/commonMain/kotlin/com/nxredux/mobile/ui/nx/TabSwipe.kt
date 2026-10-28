package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/*
 * Swiping between the main menu's tabs on a page that scrolls vertically (List, Minimal; a Grid or Carousel keeps the
 * swipe for its own content). The page follows the finger, the tab row's words staying put; past [TAB_SWIPE_COMMIT] of
 * the width, or on a fling, the next tab (finger right to left) or the previous slides in from where the finger let
 * the old page go ([TabPager]), wrapping over the shown tabs as L1 / R1 do; short of
 * it the page goes back. [TAB_SWIPE_MS] ease-out either way. The first [TAB_SWIPE_LOCK_DP] decide the axis: within
 * [TAB_SWIPE_ANGLE] degrees of horizontal a tab swipe, otherwise the list's scroll. A swipe starting in the system's
 * back-gesture zone at either edge is left to it.
 */

/** The commit (or return) slide. */
internal const val TAB_SWIPE_MS = 220

/** The share of the width a drag must pass to commit. */
internal const val TAB_SWIPE_COMMIT = 0.3f

/** The finger's travel that locks the axis. */
internal const val TAB_SWIPE_LOCK_DP = 16f

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

/**
 * The main menu's tab swipe: [drag], px, the pages' shift (the finger's, then a commit's or a return's settle, [settle]);
 * [pending], the tab step a commit asked for, applied when the new tab shows ([TabPager]); [width], the pages' px.
 */
@Stable
internal class TabSwipe {
    var drag by mutableFloatStateOf(0f)
    var pending = 0
    var width = 0f
    val settle = Animatable(0f)
}

/** App's tab swipe; null outside the main menu's pages. */
internal val LocalTabSwipe = staticCompositionLocalOf<TabSwipe?> { null }

/**
 * A page's slide between tabs: [shift], px for a page that wide (the live page's with the finger, then each page's
 * through the commit), and whether its fixed parts show ([fixedShown]). The fixed parts (the tab row, Home's stats, the
 * top-right icons: [pageFixed]) counter-shift so they never move, and swap at once when a swipe commits: the old page's
 * hide as the new one's show, so nothing fades or blinks there. [width]: the pages', as last laid out.
 */
internal class PageSlide(val shift: (width: Float) -> Float, val fixedShown: () -> Boolean, val width: () -> Float = { 0f })

internal val LocalPageSlide = compositionLocalOf { PageSlide({ 0f }, { true }) }

/** A fixed part of a main menu page ([PageSlide]): still while the page slides between tabs, shown only on the page that has the tab. */
@Composable
internal fun Modifier.pageFixed(): Modifier {
    val slide = LocalPageSlide.current
    return graphicsLayer { translationX = -slide.shift(slide.width()); alpha = if (slide.fixedShown()) 1f else 0f }
}

/** The tab swipe on a main menu page ([enabled]); a commit calls [onStep] with 1 (next) or −1 (previous). */
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
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                total += change.positionChange()
                if (horizontal == null) {
                    horizontal = tabSwipeAxis(total.x, total.y, lock)
                    if (horizontal == false) return@awaitEachGesture   // the list's scroll
                    if (horizontal == true) { scope.launch { swipe.settle.stop() }; base = swipe.drag }
                }
                if (horizontal == true) {
                    tracker.addPointerInputChange(change)
                    change.consume()   // the list under it never sees it
                    swipe.drag = base + total.x
                }
            }
            if (horizontal != true) return@awaitEachGesture
            val s = tabSwipeStep(swipe.drag, size.width.toFloat(), tracker.calculateVelocity().x, fling)
            if (s != 0) {
                swipe.pending = s   // the pages carry on from here once the new tab shows ([TabPager])
                step(s)
            } else scope.launch { swipe.settleTo(0f) }
        }
    }
}

/** The pages from where they are to [to] px, [TAB_SWIPE_MS] ease-out. */
internal suspend fun TabSwipe.settleTo(to: Float) {
    settle.snapTo(drag)
    settle.animateTo(to, tween(TAB_SWIPE_MS, easing = EaseOut)) { drag = value }
}
