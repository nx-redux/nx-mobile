package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/*
 * An options list's rows and half-row spacer gaps ([NxRow.spacer]) don't divide its viewport evenly: the slot holds whole
 * rows plus room for one gap, so with no gap or two on screen a row-top rest still leaves half a row cut at the bottom,
 * and the list's own end (its last row's bottom on the viewport's bottom) cut a row at the top. So an options list:
 *  - rests only on a row's top (a gap's top would open the page on a blank half row): [nxOptionsRests];
 *  - ends on one too, its last row whole: a bottom padding ([nxOptionsEndPad]) moves the scroll's end onto a row top,
 *    the leftover blank below the last row like the slack below a list that fits;
 *  - at rest draws only the rows wholly inside the viewport ([nxOptionsWholeBottom]); while it moves, partial rows show,
 *    so the scroll itself stays continuous.
 * All in the rows' own whole px, the heights the lazy list lays out.
 */

/** The top of each of [spacers]' items, and their total after the last: rows of [rowPx], gaps of [gapPx]. */
internal fun nxOptionsStarts(spacers: List<Boolean>, rowPx: Int, gapPx: Int): IntArray {
    val starts = IntArray(spacers.size + 1)
    spacers.forEachIndexed { i, gap -> starts[i + 1] = starts[i] + if (gap) gapPx else rowPx }
    return starts
}

/** The bottom padding that puts a [viewportPx] list's scroll end on a row's top, its last row whole, the rest blank below.
 * 0 when the list fits. */
internal fun nxOptionsEndPad(starts: IntArray, spacers: List<Boolean>, viewportPx: Int): Int {
    val total = starts.last()
    if (total <= viewportPx) return 0
    for (i in spacers.indices) if (!spacers[i] && total - starts[i] <= viewportPx) return viewportPx - (total - starts[i])
    return 0   // the last row alone is taller than the viewport: nothing better than the plain end
}

/** The scroll offsets an options list rests at: 0 and every row's top up to its scroll end ([endPadPx] included). */
internal fun nxOptionsRests(starts: IntArray, spacers: List<Boolean>, viewportPx: Int, endPadPx: Int): IntArray {
    val maxScroll = maxOf(0, starts.last() + endPadPx - viewportPx)
    val rests = mutableSetOf(0, maxScroll)
    spacers.forEachIndexed { i, gap -> if (!gap && starts[i] <= maxScroll) rests += starts[i] }
    return rests.sorted().toIntArray()
}

/**
 * Where a fling from [current] comes to rest: the rest nearest where its decay would stop ([current] + [decayOffset]),
 * but with a [direction] (a real fling, not a slow drag let go: -1 or 1) at least the next rest that way, so a short
 * flick still moves a row rather than springing back.
 */
internal fun nxOptionsFlingRest(rests: IntArray, current: Float, decayOffset: Float, direction: Int): Int {
    val aim = current + decayOffset
    var best = rests.minByOrNull { abs(it - aim) } ?: return 0
    if (direction > 0) rests.firstOrNull { it > current + 0.5f }?.let { next -> if (best < next) best = next }
    if (direction < 0) rests.lastOrNull { it < current - 0.5f }?.let { prev -> if (best > prev) best = prev }
    return best
}

/** At scroll [offset], the bottom (from the viewport's top) of the last item wholly inside [viewportPx]: where a resting
 * list stops drawing. The full viewport when even the first item doesn't fit. An item over the end by no more than
 * [WHOLE_SLACK_PX] counts as whole (a viewport sized in dp from the rows rounds a px or two under their px sum). */
internal fun nxOptionsWholeBottom(starts: IntArray, offset: Int, viewportPx: Int): Int {
    var bottom = -1
    for (j in 1 until starts.size) {
        val end = starts[j] - offset
        if (end > viewportPx + WHOLE_SLACK_PX) break
        if (end > 0) bottom = minOf(end, viewportPx)
    }
    return if (bottom <= 0) viewportPx else bottom
}

/** [nxOptionsWholeBottom]'s rounding forgiveness, px. */
private const val WHOLE_SLACK_PX = 2

/**
 * The rest to scroll to so row [selected] shows whole (null: it already does): up to its top when above the viewport,
 * else down to the first rest that brings its bottom in. [marginPx]: room kept past the row on both sides (clamped to
 * the list's ends), so it never rests under a scroll fade while more is that way.
 */
internal fun nxOptionsKeepInView(selected: Int, current: Int, starts: IntArray, rests: IntArray, viewportPx: Int, marginPx: Int = 0): Int? {
    if (selected !in 0 until starts.size - 1) return null
    val top = maxOf(0, starts[selected] - marginPx)
    val bottom = minOf(starts.last(), starts[selected + 1] + marginPx)
    return when {
        top < current -> rests.lastOrNull { it <= top } ?: 0
        bottom > current + viewportPx -> rests.firstOrNull { it + viewportPx >= bottom } ?: rests.last()
        else -> null
    }
}

/** An options list's geometry in px: its [rowPx], [starts] ([nxOptionsStarts]), [endPadPx] and [rests]. */
internal class NxOptionsGeometry(val rowPx: Int, val starts: IntArray, val viewportPx: Int, val endPadPx: Int, val rests: IntArray) {
    /** The list's scroll offset from its first visible item, or null before it has items. */
    fun offset(state: LazyListState): Int? =
        starts.getOrNull(state.firstVisibleItemIndex)?.takeIf { state.firstVisibleItemIndex < starts.size - 1 }
            ?.plus(state.firstVisibleItemScrollOffset)

    companion object {
        fun of(spacers: List<Boolean>, rowPx: Int, gapPx: Int, viewportPx: Int): NxOptionsGeometry {
            val starts = nxOptionsStarts(spacers, rowPx, gapPx)
            val pad = nxOptionsEndPad(starts, spacers, viewportPx)
            return NxOptionsGeometry(rowPx, starts, viewportPx, pad, nxOptionsRests(starts, spacers, viewportPx, pad))
        }
    }
}

/** An options list's fling: it comes to rest on a row's top ([nxOptionsFlingRest]), the decay carrying it most of the way
 * and a short settle the rest, as Compose's own lazy list snapping does. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberNxOptionsSnapFling(state: LazyListState, geometry: NxOptionsGeometry): FlingBehavior {
    val minFling = with(LocalDensity.current) { 400.dp.toPx() }   // Compose's MinFlingVelocityDp: under it, the nearest rest
    val provider = remember(state, geometry, minFling) {
        object : SnapLayoutInfoProvider {
            var target: Int? = null

            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float {
                val current = geometry.offset(state)?.toFloat() ?: return 0f.also { target = null }
                val direction = if (abs(velocity) < minFling) 0 else if (velocity > 0) 1 else -1
                val rest = nxOptionsFlingRest(geometry.rests, current, decayOffset, direction)
                target = rest
                // The decay goes to a row short of the rest (in the fling's direction only); the settle does the last row.
                val ahead = (rest - current) * (if (velocity >= 0) 1 else -1)
                return maxOf(0f, ahead - geometry.rowPx)
            }

            override fun calculateSnapOffset(velocity: Float): Float {
                val current = geometry.offset(state)?.toFloat() ?: return 0f
                val rest = target ?: nxOptionsFlingRest(geometry.rests, current, 0f, 0)
                target = null
                return rest - current
            }
        }
    }
    return rememberSnapFlingBehavior(provider)
}

/** Draws a resting options list only down to its last whole item ([nxOptionsWholeBottom]); a moving one draws whole. */
internal fun Modifier.nxOptionsWholeRows(state: LazyListState, geometry: NxOptionsGeometry): Modifier = drawWithContent {
    val offset = geometry.offset(state)
    if (state.isScrollInProgress || offset == null) drawContent()
    else clipRect(bottom = nxOptionsWholeBottom(geometry.starts, offset, size.height.toInt()).toFloat()) { this@drawWithContent.drawContent() }
}

/**
 * [NxKeepSelectedInView] for an options list: the highlighted row whole on screen at a rest ([nxOptionsKeepInView]), so a
 * d-pad move never leaves the list on a cut row. The first placement is instant, later moves animate.
 */
@Composable
internal fun NxOptionsKeepSelectedInView(state: LazyListState, selectedIndex: Int, geometry: NxOptionsGeometry, marginPx: Int = 0) {
    val placed = remember(state) { BooleanArray(1) }
    val count = geometry.starts.size - 1
    LaunchedEffect(selectedIndex, geometry, marginPx) {
        if (count == 0 || selectedIndex < 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo }
            .first { it.totalItemsCount == count && it.viewportEndOffset > it.viewportStartOffset && it.visibleItemsInfo.isNotEmpty() }
        val current = geometry.offset(state) ?: return@LaunchedEffect
        val target = nxOptionsKeepInView(selectedIndex, current, geometry.starts, geometry.rests, geometry.viewportPx, marginPx)
        val animate = placed[0]
        placed[0] = true
        if (target == null) return@LaunchedEffect
        val delta = (target - current).toFloat()
        if (animate) state.animateScrollBy(delta) else state.scrollBy(delta)
    }
}
