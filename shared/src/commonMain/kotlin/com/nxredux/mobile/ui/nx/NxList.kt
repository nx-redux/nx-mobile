package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Canvas
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first

/**
 * [cycleable]: on an options page ([NxRowKind.OPTIONS]), LEFT/RIGHT change [secondary], so it shows `< value >` while
 * selected. [spacer]: on an options page, half a row of empty space between groups (never selected or tapped).
 * [section]: on an options page, a group's label ("Console settings"), drawn in the hint grey rather than as a row.
 * [tag]: a small pill before the label (List Home's "Continue", a shelf game's shelf name); null on every other row.
 */
data class NxRow(
    val label: String, val enabled: Boolean = true, val secondary: String? = null, val cycleable: Boolean = false,
    val spacer: Boolean = false, val section: Boolean = false, val tag: String? = null,
    /** A leading icon (a file list's folder), in the label's ink: dark on the pill, the list text off it. */
    val icon: DrawableResource? = null,
)

/** Aim for ~this physical row height on any display; the row count follows from the list height. A rich row targets 1.5x this. */
val NxListPitch = 32.5.dp

/** How many rows fit in a given list height at [pitch] ([NxListPitch]; at least 1). */
fun nxRowCountFor(listHeight: Dp, pitch: Dp = NxListPitch): Int = maxOf(1, (listHeight.value / pitch.value).toInt())

/**
 * The pill's horizontal padding and the row-label inset: a fixed physical size, like the row pitch, so lists look the
 * same on every screen and orientation. Tool-page titles and details use it too so they line up with the labels.
 */
fun nxListInset(): Dp = 14.dp

/**
 * The row-label size of a list aiming at [pitch] ([NxListPitch], or it under Appearance → Text size): 62% of that
 * target, not of the rows the list's height fits, so the text is the same on every screen and orientation. Tool-page
 * details size from it too.
 */
fun nxListLabelSize(pitch: Dp = NxListPitch): TextUnit = NxTextSizes.listLabel(pitch)

/** Bold rows with the white pill, which glides between rows (NX Redux's timing); row count adapts to the list height so rows stay a sane physical size on any screen. */
@Composable
fun NxList(
    rows: List<NxRow>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    rowHeightOverride: Dp? = null,
    pillPaddingOverride: Dp? = null,
    textSizeOverride: TextUnit? = null,
    onLongPress: ((Int) -> Unit)? = null,
    endPadOverride: Dp? = null,   // the row's right margin; the scaffold sets it so labels reach 90% of the screen
    outlined: Boolean = false,    // a button row (Continue): a grey ring round the pill's place while unlit ([NxButtonRing])
    edgeMargin: Int = 0,          // rows kept in view past the selection: 1 under a scroll fade
) = NxList(
    rows, { selectedIndex }, onSelect, onOpen, modifier, listState, rowHeightOverride, pillPaddingOverride, textSizeOverride,
    onLongPress, endPadOverride, outlined, edgeMargin = edgeMargin,
)

/**
 * [NxList] with the selection as a lambda, for scrolling at 120 Hz: it is read only where it shows (the pill's
 * glide, keeping the row in view, each row's selected and lit state), so a selection step recomposes those and the two
 * rows that changed, never the list or the screen around it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NxList(
    rows: List<NxRow>,
    selectedIndex: () -> Int,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    rowHeightOverride: Dp? = null,
    pillPaddingOverride: Dp? = null,
    textSizeOverride: TextUnit? = null,
    onLongPress: ((Int) -> Unit)? = null,
    endPadOverride: Dp? = null,
    outlined: Boolean = false,
    edgeMargin: Int = 0,   // rows kept in view past the selection ([NxKeepSelectedInView]): 1 under a scroll fade
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val state = listState
    val tapOpens = LocalNxTapOpens.current
    NxKeepSelectedInView(state, selectedIndex, rows.size, edgeMargin)
    // Under a page's top-right icons ([LocalNxListTopCut]): the list's box in root px, so a row level with them stops short.
    val topCut = LocalNxListTopCut.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(if (topCut == null) modifier else modifier.onGloballyPositioned { bounds = it.boundsInRoot() }) {
        val rowHeight = rowHeightOverride ?: (maxHeight / nxRowCountFor(maxHeight))
        val listFont = textSizeOverride ?: nxListLabel()   // a fixed size: the rows stretch to fill the height, the text does not
        val sidePad = endPadOverride ?: (maxWidth * 0.044f)
        val pillPad = pillPaddingOverride ?: nxListInset()
        val density = LocalDensity.current
        // Whole rows the box shows, a px of float error forgiven: a band list's px rows fit it exactly ([nxListFit]).
        val identity = remember(rows) { nxPillListIdentity(rows) }
        val glide = rememberNxPillGlide(identity, selectedIndex, rows.size, nxWholeRows(maxHeight, rowHeight, pxSlack = 1f / density.density))
        val pitch = with(density) { rowHeight.roundToPx() }
        val highlight = nxHighlight()
        val pill = remember(glide, state, pitch, highlight) { Modifier.fillMaxSize().nxPillGlide(glide, state, pitch, highlight) }
        // The rows read the selection through this State, each in its own scope ([NxListRow]): a step recomposes the rows
        // whose selected or lit state changed, not every row on screen.
        val selection = rememberUpdatedState(selectedIndex)
        val style = NxListRowStyle(rowHeight, listFont, sidePad, pillPad, font, colors, outlined)
        LazyColumn(
            state = state,
            modifier = pill,
            flingBehavior = rememberNxRowSnapFling(state),
        ) {
            itemsIndexed(rows) { i, row ->
                val selected by remember(i) { derivedStateOf { i == selection.value() } }
                val lit by remember(glide, i) { derivedStateOf { i == glide.litIndex } }   // on-pill colours follow the gliding pill
                val cutPx by remember(i, topCut, pitch) {
                    derivedStateOf {
                        if (topCut == null) 0f else nxRowTopCutPx(
                            nxRowTopPx(i, bounds.top, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, pitch),
                            topCut.left, topCut.bottom, bounds.right,
                        )
                    }
                }
                val endPad = if (cutPx > 0f) maxOf(sidePad, with(density) { cutPx.toDp() }) else sidePad
                NxListRow(row, i, selected, lit, style, endPad, glide, tapOpens, onSelect, onOpen, onLongPress)
            }
        }
    }
}

/** What every row of one [NxList] shares: its pitch, label size, margins, font and colours. */
internal data class NxListRowStyle(
    val rowHeight: Dp, val listFont: TextUnit, val sidePad: Dp, val pillPad: Dp, val font: FontFamily, val colors: NxColors,
    val outlined: Boolean = false,
)

/** One [NxList] row: its tag ([NxRow.tag]), label (and muted secondary), on-pill colours while [lit], scrolling (marquee) while [selected]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NxListRow(
    row: NxRow, i: Int, selected: Boolean, lit: Boolean, style: NxListRowStyle, endPad: Dp, glide: NxPillGlide,
    tapOpens: (Int) -> Boolean, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
) {
    Box(
        Modifier.fillMaxWidth().height(style.rowHeight)
            .combinedClickable(
                onClick = { nxTap(i, selected, tapOpens(i), onSelect, onOpen) },
                onLongClick = onLongPress?.let { lp -> { lp(i) } },
            )
            .padding(end = endPad),   // flush to the left edge; keep the right margin for landscape (and the top icons clear)
        contentAlignment = Alignment.CenterStart,
    ) {
        // The pill itself is drawn once behind the list (nxPillGlide); the row reports its pill-content size.
        val textModifier = Modifier.onSizeChanged { glide.sizes[i] = it }
            .then(if (style.outlined && !lit) Modifier.nxButtonRing(style.colors) else Modifier)
            .padding(horizontal = style.pillPad, vertical = style.rowHeight * NX_PILL_PAD)
        val tag = row.tag
        val icon = row.icon
        if (tag == null && icon == null) NxListRowLabel(row, selected, lit, style, textModifier)
        else Row(textModifier.alpha(if (row.enabled) 1f else NX_DISABLED_ALPHA), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) NxRowIcon(icon, lit, style)
            if (tag != null) NxRowTag(tag, lit, style)
            // The pill wraps the icon, the tag and the label; the label alone scrolls (marquee) or ellipsizes in what's left.
            NxListRowLabel(row.copy(enabled = true), selected, lit, style, Modifier.weight(1f, fill = false))
        }
    }
}

/**
 * A row's [NxRow.tag] (List Home's "Continue", as the handheld's `home_list_layout.c` tag): 15 sp on a 20 sp line,
 * padded 2 x 8 dp in a round pill, 10 dp before the label. Unlit: the list text at 14% behind
 * the list text; lit: black at 14% over the pill, its text in the on-pill ink.
 */
@Composable
private fun NxRowTag(tag: String, lit: Boolean, style: NxListRowStyle) {
    val colors = style.colors
    val fill = if (lit) Color.Black.copy(alpha = NX_TAG_FILL_ALPHA) else colors.listText.copy(alpha = NX_TAG_FILL_ALPHA)
    Box(
        Modifier.padding(end = 10.dp).background(fill, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            tag, color = if (lit) colors.onAccent else colors.listText, fontFamily = style.font, fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp.pinned(), lineHeight = 20.sp.pinned(), maxLines = 1,
        )
    }
}

/** A row's [NxRow.icon]: as tall as the label's cap-to-baseline line, 8 dp before it, in its ink. */
@Composable
private fun NxRowIcon(icon: DrawableResource, lit: Boolean, style: NxListRowStyle) {
    val size = with(LocalDensity.current) { style.listFont.toDp() }
    Image(
        painterResource(icon), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(size),
        colorFilter = ColorFilter.tint(if (lit) style.colors.onAccent else style.colors.listText),
    )
}

/** A [NxRowTag]'s fill alpha, lit or not. */
internal const val NX_TAG_FILL_ALPHA = 0.14f

/** A [NxListRow]'s label and muted secondary, scrolling (marquee) while [selected]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NxListRowLabel(row: NxRow, selected: Boolean, lit: Boolean, style: NxListRowStyle, textModifier: Modifier) {
    val colors = style.colors
    val listFont = style.listFont
    Text(
        text = buildAnnotatedString {
            append(row.label)
            row.secondary?.let {
                append("  ")
                // Secondary text (a folder, a tag, a count) is muted and smaller than the label.
                val muted = if (lit) colors.onAccentMuted else colors.hint
                withStyle(SpanStyle(color = muted, fontSize = NxTextSizes.secondary(listFont))) { append(it) }
            }
        },
        color = if (lit) colors.onAccent else colors.listText,
        fontFamily = style.font, fontWeight = FontWeight.SemiBold, fontSize = listFont, lineHeight = listFont,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = textModifier.alpha(if (row.enabled) 1f else NX_DISABLED_ALPHA).let { if (selected) it.basicMarquee() else it },
    )
}

/**
 * The fling of every NX list: after any touch scroll (a fling, a slow drag let go, an overscroll at either end) the
 * list comes to rest with a row's top on the viewport's top. Snapping to the start, not Compose's default centre: the
 * list slots hold a whole number of rows, so a start-aligned rest shows only whole rows, while centring a row in an even
 * number of them leaves half a row cut at the top and the bottom.
 */
@Composable
internal fun rememberNxRowSnapFling(state: LazyListState): FlingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start)

/**
 * The px height of a list slot showing [rows] rows of [rowPx] each (plus an options gap of [gapPx]). The lazy list lays
 * each row out at its own rounded px, so a slot of [rows] x a fractional dp row can come out a px or two off their sum:
 * a start-snapped list ([rememberNxRowSnapFling]) would then rest with that much of a row cut at the bottom, or a sliver
 * of the row above showing at the end. Sized from the rounded row, the slot holds the rows exactly.
 */
internal fun nxRowsSlotPx(rowPx: Int, rows: Int, gapPx: Int = 0): Int = rowPx * rows + gapPx

/** The whole-px row that fits [rows] times in [slotPx] (the under-[rows] px left over goes below the rows). */
internal fun nxFitRowPx(slotPx: Int, rows: Int): Int = slotPx / maxOf(1, rows)

/** A disabled row's opacity. */
internal const val NX_DISABLED_ALPHA = 0.38f

/** The scroll that brings a list's highlighted row into view ([nxKeepInViewScroll]). */
internal sealed interface NxKeepInView {
    data object None : NxKeepInView
    /** Scroll by [px] (the row is partly on screen). */
    data class By(val px: Int) : NxKeepInView
    /** Jump to [index] at [offset] (negative: the row lands that far below the top). */
    data class ToItem(val index: Int, val offset: Int) : NxKeepInView
}

/**
 * The smallest scroll that brings row [selected] fully into view: by its overflow when it is partly visible ([itemOffset]
 * and [itemSize], null when off screen), else a jump that lands it on the last line when it lies past [lastVisible] (one
 * [rowHeight] above the viewport's end) or on the top line when before it.
 */
internal fun nxKeepInViewScroll(
    selected: Int, itemOffset: Int?, itemSize: Int, viewportStart: Int, viewportEnd: Int, lastVisible: Int, rowHeight: Int,
): NxKeepInView {
    if (itemOffset != null) {
        val overflowBottom = itemOffset + itemSize - viewportEnd
        val overflowTop = viewportStart - itemOffset
        return when {
            overflowBottom > 0 -> NxKeepInView.By(overflowBottom)
            overflowTop > 0 -> NxKeepInView.By(-overflowTop)
            else -> NxKeepInView.None
        }
    }
    return if (selected > lastVisible) NxKeepInView.ToItem(selected, -(viewportEnd - viewportStart - rowHeight))
    else NxKeepInView.ToItem(selected, 0)
}

/**
 * [nxKeepInViewScroll] with [margin] rows kept in view past row [selected] on both sides (clamped to the list's [count]
 * rows), so it never rests on the line under a scroll fade while more is that way: the row [margin] below is brought in
 * first, else the row [margin] above. [offsetOf]: a row's offset when it is on screen, null when not; [lastVisible]: the
 * last row on screen.
 */
internal fun nxKeepInViewMargin(
    selected: Int, margin: Int, count: Int, offsetOf: (Int) -> Int?, itemSize: Int, viewportStart: Int, viewportEnd: Int,
    lastVisible: Int, rowHeight: Int,
): NxKeepInView {
    val below = minOf(count - 1, selected + margin)
    val above = maxOf(0, selected - margin)
    val down = nxKeepInViewScroll(below, offsetOf(below), itemSize, viewportStart, viewportEnd, lastVisible, rowHeight)
    if (down is NxKeepInView.By && down.px > 0 || down is NxKeepInView.ToItem && below > lastVisible) return down
    val up = nxKeepInViewScroll(above, offsetOf(above), itemSize, viewportStart, viewportEnd, lastVisible, rowHeight)
    return if (up is NxKeepInView.By && up.px < 0 || up is NxKeepInView.ToItem) up else NxKeepInView.None
}

/**
 * [nxKeepInViewScroll] keeping [edge] px clear past row [selected] on both sides (its rows can differ in height, so a
 * row margin can't promise that): it never rests under a scroll fade [edge] tall. The list clamps a scroll past its
 * ends, where no fade shows. [edge] shrinks to what the viewport has room for around the row.
 */
internal fun nxKeepInViewEdge(
    selected: Int, itemOffset: Int?, itemSize: Int, edge: Int, viewportStart: Int, viewportEnd: Int, lastVisible: Int,
): NxKeepInView {
    val e = minOf(edge, (viewportEnd - viewportStart - itemSize) / 2).coerceAtLeast(0)
    if (itemOffset != null) return nxKeepInViewScroll(
        selected, itemOffset - e, itemSize + 2 * e, viewportStart, viewportEnd, lastVisible, itemSize + 2 * e,
    )
    return if (selected > lastVisible) NxKeepInView.ToItem(selected, -(viewportEnd - viewportStart - itemSize - e))
    else NxKeepInView.ToItem(selected, -e)
}

/** [NxKeepSelectedInView] reading [selectedIndex] in its own scope (only this recomposes on a selection step). */
@Composable
internal fun NxKeepSelectedInView(state: LazyListState, selectedIndex: () -> Int, count: Int, margin: Int = 0) =
    NxKeepSelectedInView(state, selectedIndex(), count, margin)

/**
 * Keeps the highlighted row of a lazy list on screen: the smallest scroll that brings it fully into view, or a jump
 * (landing it on the last line when moving down past the visible rows). Shared by [NxList] and [NxRichList]. It waits
 * for the list's first layout of its [count] rows (before that the layout info is empty and the row would land on the
 * top line); the first placement is instant, later moves animate. [margin]: rows kept in view past it on both sides
 * ([nxKeepInViewMargin]; none while the list shows under three rows). [edge]: instead, px kept clear past it on both
 * sides ([nxKeepInViewEdge]), for rows of differing heights under a fade that tall.
 */
@Composable
internal fun NxKeepSelectedInView(state: LazyListState, selectedIndex: Int, count: Int, margin: Int = 0, edge: Int = 0) {
    val placed = remember(state) { BooleanArray(1) }   // a plain flag: whether the first placement has happened
    LaunchedEffect(selectedIndex, count, edge) {
        if (count == 0 || selectedIndex < 0) return@LaunchedEffect   // nothing highlighted (an unfocused pane)
        val layoutInfo = snapshotFlow { state.layoutInfo }
            .first { it.totalItemsCount == count && it.viewportEndOffset > it.viewportStartOffset && it.visibleItemsInfo.isNotEmpty() }
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val rowPx = layoutInfo.visibleItemsInfo.maxOf { it.size }   // a row, not an options gap
        val shown = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / maxOf(1, rowPx)
        val scroll = if (edge > 0) nxKeepInViewEdge(
            selectedIndex, item?.offset, item?.size ?: rowPx, edge,
            layoutInfo.viewportStartOffset, layoutInfo.viewportEndOffset, layoutInfo.visibleItemsInfo.last().index,
        ) else if (margin > 0 && shown >= 3) nxKeepInViewMargin(
            selectedIndex, margin, count,
            offsetOf = { r -> layoutInfo.visibleItemsInfo.firstOrNull { it.index == r }?.offset },
            itemSize = rowPx, viewportStart = layoutInfo.viewportStartOffset, viewportEnd = layoutInfo.viewportEndOffset,
            lastVisible = layoutInfo.visibleItemsInfo.last().index, rowHeight = rowPx,
        ) else nxKeepInViewScroll(
            selected = selectedIndex,
            itemOffset = item?.offset, itemSize = item?.size ?: 0,
            viewportStart = layoutInfo.viewportStartOffset, viewportEnd = layoutInfo.viewportEndOffset,
            lastVisible = layoutInfo.visibleItemsInfo.last().index,
            rowHeight = layoutInfo.visibleItemsInfo.maxOf { it.size },   // a row, not an options gap
        )
        val animate = placed[0]
        placed[0] = true
        when (scroll) {
            NxKeepInView.None -> Unit
            is NxKeepInView.By -> if (animate) state.animateScrollBy(scroll.px.toFloat()) else state.scrollBy(scroll.px.toFloat())
            is NxKeepInView.ToItem ->
                if (animate) state.animateScrollToItem(scroll.index, scroll.offset) else state.scrollToItem(scroll.index, scroll.offset)
        }
    }
}

/** The list scroll arrows' width: a fixed physical size, like the row pitch and the pill inset, so it matches on every screen. */
val NxScrollArrowWidth = 28.dp

/** Every scroll arrow's opacity: one quiet standard across lists, the main menu and the in-game menu. */
const val NX_SCROLL_ARROW_ALPHA = 0.2f

/** A wide, short scroll chevron (NX Redux's 24x6 = 4:1 arrow), drawn subtly; the caller centres it on the full screen width. */
@Composable
fun NxScrollArrow(up: Boolean, width: Dp, modifier: Modifier = Modifier, opacity: Float = NX_SCROLL_ARROW_ALPHA) {
    val color = LocalNxColors.current.listText
    Canvas(modifier.size(width = width, height = width / 4)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (up) {
                moveTo(0f, h); lineTo(w, h); lineTo(w / 2f, 0f)
            } else {
                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2f, h)
            }
            close()
        }
        drawPath(path, color, alpha = opacity)
    }
}

/** A button's grey ring (Continue), so it reads as one: the pictures' grey, round as the pill. Lit, the pill takes its place. */
internal fun Modifier.nxButtonRing(colors: NxColors): Modifier =
    border(NX_BUTTON_RING, colors.hint.copy(alpha = 0.6f), RoundedCornerShape(50))

/** [nxButtonRing]'s width. */
private val NX_BUTTON_RING = 1.5.dp
