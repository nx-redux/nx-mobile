package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.jvm.JvmName
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * NX Redux's pill glide (common/ui/ui_list.c, PILL_ANIM_MS): 150 ms, time based, so it lasts the same whatever the
 * frame rate.
 */
internal const val NX_PILL_GLIDE_MS = 150

/** NX Redux's pill easing: smoothstep (ease-in-out), so the travel itself is visible rather than front-loaded. */
internal val NxSmoothstep = Easing { t -> t * t * (3f - 2f * t) }

/**
 * Where a glide from [position] (the pill's current, possibly fractional, row) to [to] starts. A wrap (last to first or
 * first to last, [from] being the previous selection) or a jump further than the [visibleRows] enters from one row off
 * the target in the direction of travel, as NX Redux does, instead of sweeping across the whole list; any other move
 * starts where the pill is.
 */
internal fun nxPillGlideStart(position: Float, from: Int, to: Int, count: Int, visibleRows: Int): Float = when {
    count > 1 && from == count - 1 && to == 0 -> -1f
    count > 1 && from == 0 && to == count - 1 -> count.toFloat()
    abs(to - position) > visibleRows -> to - sign(to - position)
    else -> position
}

/** The top of (possibly fractional) row [position] in a lazy list of [pitch]-tall rows scrolled to [firstIndex] + [firstOffset]. */
internal fun nxPillRowTop(position: Float, firstIndex: Int, firstOffset: Int, pitch: Int): Float =
    (position - firstIndex) * pitch - firstOffset

/**
 * The one selection pill of an [NxList] or [NxRichList], drawn behind the rows. It glides between rows in row space (a
 * fractional row index), so a list scrolling under it keeps it on its row, and stretches from the old row's measured
 * pill size to the new one's. Rows report their pill-content size into [sizes] by index.
 */
@Stable
internal class NxPillGlide(initial: Int, val sizes: MutableMap<Int, IntSize> = mutableStateMapOf()) {
    private var target by mutableIntStateOf(initial)
    private var fromPosition by mutableFloatStateOf(initial.toFloat())
    private var fromSize by mutableStateOf<IntSize?>(null)
    private val progress = Animatable(1f)

    /** The pill's current row, fractional mid-glide. */
    val position: Float get() = fromPosition + (target - fromPosition) * progress.value

    /**
     * The row the pill covers the most of, whose text takes the on-pill colours: the old row stays lit until the pill
     * leaves it and the new one lights as it arrives (NX Redux's "pill over"), so no text is dark on bare background.
     */
    val litIndex by derivedStateOf { position.roundToInt() }

    /** The pill's current size, or null before the target row has been measured. */
    fun size(): Size? {
        val to = sizes[target] ?: return fromSize?.let { Size(it.width.toFloat(), it.height.toFloat()) }
        val from = fromSize ?: to
        val t = progress.value
        return Size(from.width + (to.width - from.width) * t, from.height + (to.height - from.height) * t)
    }

    /** Glides to [index]; a move mid-glide restarts from where the pill is. */
    suspend fun moveTo(index: Int, count: Int, visibleRows: Int) {
        if (index == target) return
        val here = position
        val hereSize = size()?.let { IntSize(it.width.roundToInt(), it.height.roundToInt()) }
        fromPosition = nxPillGlideStart(here, target, index, count, visibleRows)
        fromSize = hereSize
        target = index
        progress.snapTo(0f)
        progress.animateTo(1f, tween(NX_PILL_GLIDE_MS, easing = NxSmoothstep))
    }
}

/**
 * What identifies a list for its pill glide: the row labels, without their values. A row's value changing in place (the
 * in-game menu's `< Disc 2/3 >`) keeps the same list, so the pill does not snap; other labels (a folder change) are a new one.
 */
@JvmName("nxPillListIdentityOfRows")
internal fun nxPillListIdentity(rows: List<NxRow>): List<String> = rows.map { it.label }

/** [nxPillListIdentity] of an [NxRichList]: its titles, without the subtitles (a progress line) that change in place. */
@JvmName("nxPillListIdentityOfRichRows")
internal fun nxPillListIdentity(rows: List<NxRichRow>): List<String> = rows.map { it.title }

/** The entries of [sizes] past a list of [count] rows: the ones a shorter new list drops. */
internal fun nxStaleSizeKeys(sizes: Map<Int, *>, count: Int): List<Int> = sizes.keys.filter { it >= count }

/**
 * The pill glide for a list of [count] rows, identified by [identity] ([nxPillListIdentity]), with [selectedIndex]
 * highlighted. A new list (another [identity], as on a folder change) starts a fresh glide at its selection, so it snaps
 * there instead of gliding from the old list's row; the first composition likewise starts at rest. Measured sizes of
 * the rows the new list still has survive, since rows re-report theirs only when they change; the rest are dropped.
 */
@Composable
internal fun rememberNxPillGlide(identity: Any, selectedIndex: Int, count: Int, visibleRows: Int): NxPillGlide {
    val sizes = remember { mutableStateMapOf<Int, IntSize>() }
    val glide = remember(identity) {
        nxStaleSizeKeys(sizes, count).forEach { sizes.remove(it) }
        NxPillGlide(selectedIndex, sizes)
    }
    LaunchedEffect(glide, selectedIndex) { glide.moveTo(selectedIndex, count, visibleRows) }
    return glide
}

/**
 * [rememberNxPillGlide] with the selection as a lambda, read only by the glide's own effect ([PillGlideFollow]), so a
 * selection step does not recompose the caller (smooth scrolling at 120 Hz).
 */
@Composable
internal fun rememberNxPillGlide(identity: Any, selectedIndex: () -> Int, count: Int, visibleRows: Int): NxPillGlide {
    val sizes = remember { mutableStateMapOf<Int, IntSize>() }
    val glide = remember(identity) {
        nxStaleSizeKeys(sizes, count).forEach { sizes.remove(it) }
        NxPillGlide(Snapshot.withoutReadObservation { selectedIndex() }, sizes)
    }
    PillGlideFollow(glide, selectedIndex, count, visibleRows)
    return glide
}

@Composable
private fun PillGlideFollow(glide: NxPillGlide, selectedIndex: () -> Int, count: Int, visibleRows: Int) {
    val sel = selectedIndex()
    LaunchedEffect(glide, sel) { glide.moveTo(sel, count, visibleRows) }
}

/**
 * Draws [glide]'s pill in [color] behind a lazy list of [pitch]-px rows: a capsule at the row's start edge ([start] px
 * in, a list whose rows have a gutter), centred in its row. Clipped to the list, so a pill entering from beyond an edge
 * is revealed only through the edge row.
 */
internal fun Modifier.nxPillGlide(glide: NxPillGlide, state: LazyListState, pitch: Int, color: Color, start: Float = 0f): Modifier =
    clipToBounds().drawBehind {
        val size = glide.size() ?: return@drawBehind
        val top = nxPillRowTop(glide.position, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, pitch) +
            (pitch - size.height) / 2f
        val left = if (layoutDirection == LayoutDirection.Rtl) this.size.width - size.width - start else start
        val radius = minOf(size.width, size.height) / 2f
        drawRoundRect(color, Offset(left, top), size, CornerRadius(radius))
    }
