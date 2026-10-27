package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/*
 * The Settings rows block's edge fade, by row rather than by pixel: while there is more below, the last rows in view
 * step down ([steps], e.g. 0.6 then 0.25 for the last), and the top mirrors it once scrolled. A row is faded whole, so
 * its tag goes with it. A thin linear mask ([nxRowEdgeMask]) softens a row passing the edge while the list moves, and
 * at rest a row the edge still cuts (rows of differing heights can't all end on the edge): that row is the outermost
 * step, so there is always a faint row saying more is there.
 */

/** One laid-out item: its [index], its top [offset] in the viewport and its [size], in px. */
internal data class RowSpan(val index: Int, val offset: Int, val size: Int)

/** Px a row may overrun the viewport and still count as whole (rounding). */
private const val ROW_SLACK = 2

/**
 * Item [index]'s opacity in a list showing [rows] between [start] and [end] (px) of [total] items: 1, or a step of
 * [steps] (outermost last) by how many rows lie between it and an edge with more beyond ([back] above, [forward] below),
 * as many steps as rows beyond, so one row beyond fades the last row to the last step. A row the edge cuts counts as
 * in view (the outermost step). At rest ([moving] false) by whole rows; while moving, between steps by how much of
 * the rows in between shows. [isRow]: items that count as rows (not an options gap).
 */
internal fun nxRowFadeAlpha(
    rows: List<RowSpan>, index: Int, start: Int, end: Int, total: Int, back: Boolean, forward: Boolean,
    steps: List<Float>, moving: Boolean, isRow: (Int) -> Boolean = { true },
): Float {
    val row = rows.firstOrNull { it.index == index } ?: return 1f
    if (!isRow(index)) return 1f
    val counted = rows.filter { isRow(it.index) }
    fun shown(r: RowSpan): Float {
        val px = (minOf(r.offset + r.size, end) - maxOf(r.offset, start)).coerceAtLeast(0)
        if (px <= ROW_SLACK) return 0f
        return if (moving) px.toFloat() / maxOf(1, r.size) else 1f   // at rest a row in view counts whole
    }
    val inView = counted.filter { shown(it) > 0f }
    var alpha = 1f
    if (forward) {
        val last = inView.maxOfOrNull { it.index } ?: index
        val beyond = (last + 1 until total).count(isRow) + (if (inView.any { it.index == last && it.offset + it.size > end + ROW_SLACK }) 1 else 0)
        val n = minOf(steps.size, beyond)
        if (n > 0) alpha = minOf(alpha, rowStep(counted.filter { it.index > index }.sumOf { shown(it).toDouble() }.toFloat(), steps.takeLast(n)))
    }
    if (back) {
        val first = inView.minOfOrNull { it.index } ?: index
        val beyond = (0 until first).count(isRow) + (if (inView.any { it.index == first && it.offset < start - ROW_SLACK }) 1 else 0)
        val n = minOf(steps.size, beyond)
        if (n > 0) alpha = minOf(alpha, rowStep(counted.filter { it.index < index }.sumOf { shown(it).toDouble() }.toFloat(), steps.takeLast(n)))
    }
    return alpha
}

/** The step [rowsIn] rows in from the edge: the outermost step at 0, the next at 1, … full past the last, linear between. */
private fun rowStep(rowsIn: Float, steps: List<Float>): Float {
    val levels = steps.reversed() + 1f
    if (rowsIn >= levels.lastIndex) return 1f
    val i = rowsIn.toInt().coerceAtLeast(0)
    return levels[i] + (levels[i + 1] - levels[i]) * (rowsIn - i)
}

/** Item [index] of [state]'s list faded by row ([nxRowFadeAlpha]), read at draw time. */
internal fun Modifier.nxRowFade(state: LazyListState, index: Int, steps: List<Float>, isRow: (Int) -> Boolean = { true }): Modifier =
    graphicsLayer {
        val info = state.layoutInfo
        alpha = nxRowFadeAlpha(
            info.visibleItemsInfo.map { RowSpan(it.index, it.offset, it.size) }, index,
            info.viewportStartOffset, info.viewportEndOffset, info.totalItemsCount,
            state.canScrollBackward, state.canScrollForward, steps, state.isScrollInProgress, isRow,
        )
    }

/**
 * The thin linear mask at an edge with more beyond: while [state] moves (a row passing the edge softens), and at rest
 * while a row is still cut there.
 */
internal fun Modifier.nxRowEdgeMask(state: LazyListState): Modifier = nxEdgeFade(
    { state.canScrollBackward && (state.isScrollInProgress || state.layoutInfo.let { i -> i.visibleItemsInfo.firstOrNull()?.let { it.offset < i.viewportStartOffset - ROW_SLACK } == true }) },
    { state.canScrollForward && (state.isScrollInProgress || state.layoutInfo.let { i -> i.visibleItemsInfo.lastOrNull()?.let { it.offset + it.size > i.viewportEndOffset + ROW_SLACK } == true }) },
    ROW_EDGE_MASK,
)

/** [nxRowEdgeMask]'s height. */
private val ROW_EDGE_MASK = 12.dp
