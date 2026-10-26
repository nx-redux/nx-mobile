package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nxredux.mobile.ui.listArtLabelEnd
import kotlin.math.roundToInt

/**
 * The selected item's info band on an [NxScreenScaffold] list, fixed just above the hint bar with its bottom [overlap]
 * reaching into the bar's empty top. It draws the list's scroll arrows (the pair [content] is handed), so the scaffold
 * draws none of its own. The list keeps [reserve] (the band's height less the overlap) clear above the hint bar and
 * fills the rest with whole rows ([nxListFit]). The fit is fixed per screen, so rows never move as the band's text changes.
 */
class NxHintsBand(
    val reserve: Dp, val overlap: Dp,
    /** Drawn in the hint bar's row while the bar is hidden ([NxScreenScaffold]'s hideHints), e.g. a List game list's info line. */
    val barInfo: (@Composable () -> Unit)? = null,
    val content: @Composable (arrows: @Composable () -> Unit) -> Unit,
)

/**
 * A list or grid body over a hint bar: the shared shape of the main menu, Home, game lists, and the tool and settings
 * pages. Without a [band] the list shows whole rows between two half-row arrow gutters. With one, the rows fill the slot
 * and the arrows are drawn in the band.
 *
 * [title]: a tool page's title row ([NxPageTitle]) above the list, the list centred by eye under it ([NxTitledBody]).
 * [showArt]: with art (drawn full-screen behind by App) the labels run over it up to [NX_LABEL_REACH] of the screen;
 * without, the list keeps [NxList]'s narrow right margin.
 * [listTopGap]: with a band, the gap above the first row, taken from the rows' slot.
 * [listTopEdge]: with a band, an edge this far above the list's top (the main menu's tab underline, List Home's strip),
 * in place of [listTopGap]. The rows are spaced so the edge-to-first-pill gap equals the last pill's gap to the band's
 * arrows ([nxListFitUnder]). It can be negative: the rows then start up inside the tab row's empty foot.
 * [labelStart]: where the row labels start, in place of [nxListInset]. The list moves right by the difference and the
 * labels still end where they did.
 * [rowKind] [NxRowKind.OPTIONS]: [NxOptionsList] rows (a list row's pitch) in place of the pill list, art unused, always
 * at the top. On a titled page a list that fits takes only its rows' height.
 * [grid]: a body in place of the list (Grid, Carousel and Backdrop pages, the tool apps grid), running down to the hint
 * bar; [rows], [title] and [band] are unused.
 * [scrollFade]: the rows fade at the edge they scroll on ([nxScrollFade]) in place of the scroll arrows (false only
 * draws the arrows); a band keeps the arrows' room, so nothing moves, a titled page gives it to the rows.
 * [rowEdges]: with a band, gaps above the first row and below the last, in rows (top to bottom); the whole rows that fit
 * the rest stretch to fill it exactly ([listTopGap] and [listTopEdge] unused).
 * [rowsLike]: under a [listTopEdge], the row height a [rowEdges] list with these edges would have here, so the rows
 * match those lists', and its bottom gap; the top gap takes what is left ([nxListFitUnder]'s rowPx, bottomRows).
 * [onPageRows]: told the whole rows the list shows, again when that changes (a game list pages LEFT / RIGHT by them).
 * [hideHints]: no hint bar is drawn but its room is kept, so nothing moves. [hintShade] keeps the bar's
 * [NxColors.scrimHints] shade there (with [NxHintsBand.barInfo] drawn on it).
 */
@Composable
fun NxScreenScaffold(
    rows: List<NxRow>, selectedIndex: Int,
    hints: List<Hint>, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier,
    onHint: ((NxButton) -> Unit)? = null,   // makes the hint bar tappable
    onLongPress: ((Int) -> Unit)? = null,
    title: String? = null,
    showArt: Boolean = true,
    band: NxHintsBand? = null,
    grid: (@Composable (Modifier) -> Unit)? = null,
    rowKind: NxRowKind = NxRowKind.PILL,
    labelStart: Dp? = null,
    listTopGap: Dp = 0.dp,
    listTopEdge: Dp? = null,
    hideHints: Boolean = false,
    hintShade: Boolean = false,
    selection: (() -> Int)? = null,   // read by the pill rows in place of selectedIndex, so a selection step recomposes only the rows that changed
    artBox: Boolean = false,   // boxed List art on the right: the labels stop short of it (listArtLabelEnd)
    onFirstPill: ((Float) -> Unit)? = null,   // under a listTopEdge: the first pill's top, dp from the list's top (List Home centres its strip by it)
    keepBarRoom: Boolean = true,   // false: hidden hints leave no room either, the body runs to the bottom (a main menu Carousel, centred in what shows)
    header: (@Composable () -> Unit)? = null,   // under the title, above the rows (a page's banner); the rows fit what it leaves
    scrollFade: Boolean = true,
    rowEdges: Pair<Float, Float>? = null,
    rowsLike: Pair<Float, Float>? = null,
    onPageRows: ((Int) -> Unit)? = null,   // the whole rows the list shows (a game list's LEFT / RIGHT page)
) {
    val listSelection = selection ?: { selectedIndex }
    val listState = rememberLazyListState()
    // Every list's pitch under Appearance → Text size: the list's at Large, in proportion at Small (the text follows the row).
    val bandPitch = nxOptionsRowHeight(NxListPitch, LocalNxOptionText.current)
    // Up and down side by side, 1 dp apart, whichever applies, packed (a lone arrow leaves no gap): the pair a band draws.
    val arrowPair = @Composable {
        if (!scrollFade) Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
            if (listState.canScrollBackward) NxScrollArrow(up = true, width = NxScrollArrowWidth)
            if (listState.canScrollForward) NxScrollArrow(up = false, width = NxScrollArrowWidth)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The body ends at the hint bar's top edge. It draws above the bar, so a band can reach into the bar's top.
            if (grid != null) Box(Modifier.weight(1f).fillMaxWidth()) {
                grid(Modifier.fillMaxSize())
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().zIndex(1f)) {
                // No band: split the height into N rows, then show the whole rows that fit above a half-row arrow gutter.
                // With a band: the slot (the height less the top gap and the band's reserve) holds exactly N whole rows.
                val density = LocalDensity.current
                // Under a listTopEdge the top gap depends on the rows' pill gap, so it is solved with the fit.
                val edgeFit = rowEdges?.takeIf { band != null }?.let { (top, bottom) -> with(density) { nxListFitEdges(maxHeight.roundToPx(), density.density, top, bottom, bandPitch) } } ?: if (band != null && listTopEdge != null) with(density) {
                    nxListFitUnder(
                        maxHeight.roundToPx(), band.reserve.roundToPx(), density.density, density.fontScale, listTopEdge.value,
                        bandHeight = (band.reserve + band.overlap).value, pitch = bandPitch,
                        rowPx = rowsLike?.let { (t, b) -> nxListFitEdges(maxHeight.roundToPx(), density.density, t, b, bandPitch).row.roundToPx() },
                        bottomRows = rowsLike?.second,
                    )
                } else null
                val topGap = when {
                    band == null -> 0.dp
                    edgeFit != null -> with(density) { edgeFit.gapPx.toDp() }
                    else -> listTopGap
                }
                // Under an edge the rows' slot is exactly the n rows; the rest goes below them ([NxListEdgeFit.bottomPx]).
                val bottomPad = when {
                    edgeFit != null -> with(density) { edgeFit.bottomPx.toDp() }
                    else -> band?.reserve ?: 0.dp
                }
                val sized = maxHeight - topGap - (band?.reserve ?: 0.dp)
                // The first pill sits half a pill gap inside its row.
                if (edgeFit != null && onFirstPill != null) {
                    val pillTop = with(density) { edgeFit.gapPx.toDp().value } + nxPillGap(edgeFit.row.value, density.fontScale, bandPitch) / 2
                    val report by rememberUpdatedState(onFirstPill)
                    LaunchedEffect(pillTop) { report(pillTop) }
                }
                // A band's slot in whole px as the padded box below lays it out (each padding rounds on its own).
                val (n, rowHeight) = if (edgeFit != null) edgeFit.rows to edgeFit.row
                else if (band != null) with(density) {
                    nxListFit(maxHeight.roundToPx() - topGap.roundToPx() - band.reserve.roundToPx(), density.density, bandPitch)
                }
                else maxOf(2, nxRowCountFor(sized, bandPitch)).let { it to sized / it }
                val gutter = rowHeight / 2
                val gutters = band == null
                // The list is whole rows above one arrow gutter. Under a title the leftover height (a title row
                // can leave a part row) is split above and below, so the list sits centred between the title and the hints.
                // A titled options list sits at the top instead, directly under the title.
                val centred = title != null && rowKind != NxRowKind.OPTIONS
                NxTitledBody(title, Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) {
                    header?.invoke()
                    BoxWithConstraints(
                        Modifier.weight(1f).nxGrowUp(-topGap).fillMaxSize().padding(top = maxOf(0.dp, topGap), bottom = bottomPad),
                        contentAlignment = if (centred) Alignment.CenterStart else Alignment.TopStart,
                    ) {
                        // Untitled (the one-pane Settings pages), an options list keeps the pill list's half-row gutter, so
                        // its first row sits where the Settings menu's does.
                        val options = rowKind == NxRowKind.OPTIONS && title != null
                        val itemHeight = rowHeight   // options rows are list rows (Text size already in the pitch)
                        val slack = 2f / LocalDensity.current.density
                        // A titled options list that fits takes its own height. One that scrolls has an [NxOptionsArrowStrip]
                        // in place of the half-row gutter when it draws arrows (no [scrollFade]).
                        val optionsHeight = if (options) nxOptionsFitHeight(rows, itemHeight) else 0.dp
                        val fits = options && optionsHeight.value <= maxHeight.value + slack
                        val strip = when { !gutters || fits || scrollFade -> 0.dp; options -> NxOptionsArrowStrip; else -> gutter }
                        // An options gap takes half a row of the slot: the whole rows fit in the rest.
                        val gap = if (rowKind == NxRowKind.OPTIONS && !fits && rows.any { it.spacer }) itemHeight * NX_OPTIONS_GAP else 0.dp
                        val whole = nxWholeRows(maxHeight - strip - gap, itemHeight, pxSlack = slack)
                if (onPageRows != null) {
                    val report by rememberUpdatedState(onPageRows)
                    LaunchedEffect(whole) { report(whole) }
                }
                        // In the rows' own rounded px, so the list rests on whole rows ([nxRowsSlotPx]).
                        val slot = if (fits) optionsHeight else with(density) { nxRowsSlotPx(itemHeight.roundToPx(), whole, gap.roundToPx()).toDp() }
                        val listWidth = maxWidth
                        val listShift = Modifier.padding(start = labelStart?.let { maxOf(0.dp, it - nxListInset()) } ?: 0.dp)
                        Column(Modifier.fillMaxWidth()) {
                            // An options list fades itself (its rows rest whole); a titled page's fade is half a row, the main menu's taller.
                            val fade = scrollFade && rowKind != NxRowKind.OPTIONS
                            Box(Modifier.fillMaxWidth().height(slot).then(if (fade) Modifier.nxScrollFade(listState, height = if (gutters) itemHeight / 2 else 56.dp, strong = true) else Modifier)) {
                                if (rowKind == NxRowKind.OPTIONS) {
                                    NxOptionsList(
                                        rows, selectedIndex, onSelect, onOpen, rowHeight = itemHeight, listText = NxTextSizes.listLabel(bandPitch),
                                        modifier = Modifier.fillMaxSize(), listState = listState, onLongPress = onLongPress, scrollFade = scrollFade,
                                    )
                                } else if (!showArt) {
                                    NxList(rows, listSelection, onSelect, onOpen, Modifier.fillMaxSize().then(listShift), listState = listState, rowHeightOverride = rowHeight, onLongPress = onLongPress, edgeMargin = if (scrollFade) 1 else 0)
                                } else {
                                    // The list spans the width over the art App draws behind (NX Redux draws titles over it), its
                                    // labels reaching [NX_LABEL_REACH] of the screen.
                                    val insets = restingSystemBars().union(WindowInsets.displayCutout)   // the insets App pads the screens by
                                    val dir = LocalLayoutDirection.current
                                    val endPad = with(density) {
                                        nxLabelEndPad(listWidth, insets.getLeft(this, dir).toDp(), insets.getRight(this, dir).toDp(), nxListInset())
                                    }
                                    NxList(rows, listSelection, onSelect, onOpen, Modifier.fillMaxSize().then(listShift), listState = listState, rowHeightOverride = rowHeight, onLongPress = onLongPress, edgeMargin = if (scrollFade) 1 else 0,
                                        endPadOverride = if (artBox) maxOf(endPad, listArtLabelEnd(listWidth.value, LocalPortrait.current).dp) else endPad)
                                }
                            }
                            // Up and down side by side under the rows, at the labels' start, whichever applies (the main menu's pair).
                            if (strip > 0.dp) Box(Modifier.fillMaxWidth().height(strip).padding(start = labelStart ?: nxListInset()), contentAlignment = Alignment.CenterStart) {
                                arrowPair()
                            }
                        }
                    }
                } }
                // The band, over the list and art, its bottom [NxHintsBand.overlap] inside the hint bar. A plain Box takes
                // no pointer input, so hint taps still reach the bar. Touch only there is no bar to reach into, so the band
                // rests on the body's bottom, unless hideHints keeps the bar's row (a List's info line in it).
                val noBarRow = touchOnly() && !hideHints
                band?.let { b -> Box(Modifier.align(Alignment.BottomCenter).offset(y = if (noBarRow) 0.dp else b.overlap).fillMaxWidth()) { b.content(arrowPair) } }
            }
            if (!hideHints) NxHintBar(hints, onHint = onHint)
            else if (keepBarRoom) Box {
                // The bar laid out unseen, so its room matches the shown bar's exactly; no taps (no onHint). Touch only
                // the bar draws nothing, so the room is a plain row of its height ([NX_HINT_BAR_ROW_HEIGHT]).
                if (touchOnly()) Spacer(Modifier.fillMaxWidth().height(NX_HINT_BAR_ROW_HEIGHT))
                else NxHintBar(hints, Modifier.alpha(0f))   // role: laid out unseen
                if (hintShade) Box(Modifier.matchParentSize().background(LocalNxColors.current.scrimHints))
                band?.barInfo?.let { info -> Box(Modifier.matchParentSize()) { info() } }
            }
        }
    }
}

/** How many whole [row]s fit in a list [slot]. The bands around the slot are laid out in whole pixels, each rounding by
 * under half a pixel, so the slot can come out up to ~1.5 px short of an exact fit. [pxSlack] (2 px, in dp) absorbs that
 * so rounding never drops a row; a fraction-of-a-row epsilon is too small for large rows (0.82 px short of a 73 px row). */
internal fun nxWholeRows(slot: Dp, row: Dp, pxSlack: Float): Int = maxOf(1, ((slot.value + pxSlack) / row.value).toInt())

/** A band list's row count and row height: as many rows as fit [slotPx] at [pitch] ([NxListPitch]; at least 2),
 * stretched to fill it so no part row shows. Each row is a whole number of px ([density] px per dp): fractional rows
 * round per item and n of them could overrun the slot, clipping the last and lighting a spurious down arrow. The
 * under-n px left over fall below the rows, into the gap over the band. */
internal fun nxListFit(slotPx: Int, density: Float, pitch: Dp = NxListPitch): Pair<Int, Dp> {
    val n = maxOf(2, nxRowCountFor((slotPx / density).dp, pitch))
    return n to ((slotPx / n) / density).dp
}

/** [NxList]'s pill padding above and below its label, as a share of the row pitch. */
internal const val NX_PILL_PAD = 0.08f

/**
 * The visible gap between two consecutive pills of rows [row] dp apart at the system [fontScale]: the row less its pill,
 * which is the label's natural MiSans line ([NX_FONT_LINE] per sp of the list label, [NxTextSizes.listLabel] of the
 * list's target [pitch]) plus [NX_PILL_PAD] above and below. A pill sits centred in its row, half this gap above it.
 */
internal fun nxPillGap(row: Float, fontScale: Float, pitch: Dp = NxListPitch): Float =
    row * (1f - 2 * NX_PILL_PAD) - NxTextSizes.listLabel(pitch).value * fontScale * NX_FONT_LINE

/** The list top gap (dp) that puts the first pill of rows [row] dp apart one [nxPillGap] under an edge [edgeAbove] dp above the list's top. */
internal fun nxListTopGapUnder(edgeAbove: Float, row: Float, fontScale: Float, pitch: Dp = NxListPitch): Float = nxPillGap(row, fontScale, pitch) / 2 - edgeAbove

/**
 * A band list's fit under an edge ([nxListFitUnder]): its top gap in px (may be negative), row count, row height, and
 * the px left between the last row and the list's bottom ([bottomPx]).
 */
internal data class NxListEdgeFit(val gapPx: Int, val rows: Int, val row: Dp, val bottomPx: Int)

/**
 * How far below a List band's top its scroll arrows' ink starts, in dp, for a band [bandHeight] dp tall. The arrows
 * ([NxScrollArrow], [NxScrollArrowWidth] wide, a quarter of that tall, inked to the box's top) are centred in the band.
 */
internal fun nxBandArrowInkTop(bandHeight: Float): Float = (bandHeight - NxScrollArrowWidth.value / 4) / 2

/**
 * The list top gap (px) that makes the gap from an edge [edgePx] above the list's top down to the first pill equal to
 * the gap from the last pill down to the band's arrows' ink, [inkTopPx] below the list's top, for [rows] rows of
 * [rowPx]. Each pill sits half a pill gap inside its row at both ends, so that half cancels:
 * edge + gap = ink − (gap + rows·row).
 */
internal fun nxListBalancedGap(edgePx: Float, inkTopPx: Float, rows: Int, rowPx: Int): Int =
    ((inkTopPx - rows * rowPx - edgePx) / 2).roundToInt()

/**
 * [nxListFit] for a band list [heightPx] tall (its [reservePx] kept clear above the hint bar, its band [bandHeight] dp
 * tall) under an edge [edgeAbove] dp above it. The rows' count and pitch fit the slot left when the first pill sits one
 * pill gap under the edge ([nxListTopGapUnder]). The gap and the row depend on each other, so the gap is solved from the
 * fit without it, then refined once. The rows then shift so the edge-to-first-pill gap equals the last pill's gap to
 * the band's arrows' ink ([nxListBalancedGap], [nxBandArrowInkTop]), keeping their count and pitch. [rowPx]: the rows
 * this tall in place of the fit's, as many as fit the slot (at least 2); the balance splits what is left above the
 * first pill and below the last. With [bottomRows] too, the gap under the last row is that many rows and what is left
 * goes above the first (never under the edge's least gap).
 */
internal fun nxListFitUnder(
    heightPx: Int, reservePx: Int, density: Float, fontScale: Float, edgeAbove: Float, bandHeight: Float, pitch: Dp = NxListPitch,
    rowPx: Int? = null, bottomRows: Float? = null,
): NxListEdgeFit {
    var gapPx = 0
    var fit = nxListFit(heightPx - reservePx, density, pitch)
    var slotPx = heightPx - reservePx
    repeat(2) {
        gapPx = (nxListTopGapUnder(edgeAbove, fit.second.value, fontScale, pitch) * density).roundToInt()
        slotPx = heightPx - gapPx - reservePx
        fit = nxListFit(slotPx, density, pitch)
    }
    if (rowPx != null && bottomRows != null) {
        val bottomPx = (rowPx * bottomRows).roundToInt()
        val rows = maxOf(2, (slotPx - bottomPx) / rowPx)
        return NxListEdgeFit(heightPx - reservePx - bottomPx - rows * rowPx, rows, (rowPx / density).dp, bottomPx + reservePx)
    }
    val rows = if (rowPx != null) maxOf(2, slotPx / rowPx) else fit.first
    val row = rowPx ?: (slotPx / rows)   // the fit's whole-px row
    val inkTopPx = heightPx - reservePx + nxBandArrowInkTop(bandHeight) * density
    val balanced = nxListBalancedGap(edgeAbove * density, inkTopPx, rows, row)
    return NxListEdgeFit(balanced, rows, if (rowPx != null) (row / density).dp else fit.second, maxOf(0, heightPx - balanced - rows * row))
}

/**
 * A list's fit with [top] and [bottom] gaps in rows ([NxScreenScaffold]'s rowEdges): as many rows as fit [heightPx] less
 * the gaps at [pitch], one fewer (at least 2) so the rows run taller than the pitch, then one whole-px row height so the
 * rows and both gaps fill it.
 */
internal fun nxListFitEdges(heightPx: Int, density: Float, top: Float, bottom: Float, pitch: Dp = NxListPitch): NxListEdgeFit {
    val rows = maxOf(2, nxRowCountFor(((heightPx - (top + bottom) * pitch.value * density) / density).dp, pitch) - 1)
    val rowPx = (heightPx / (rows + top + bottom)).toInt()
    val topPx = (rowPx * top).roundToInt()
    return NxListEdgeFit(topPx, rows, (rowPx / density).dp, heightPx - topPx - rows * rowPx)
}

/** Lays its content out [by] taller, the extra above its own top (for a negative list top gap); nothing when [by] is not positive. */
private fun Modifier.nxGrowUp(by: Dp): Modifier = if (by <= 0.dp) this else layout { measurable, c ->
    val k = by.roundToPx()
    val p = measurable.measure(c.copy(minHeight = c.minHeight + k, maxHeight = if (c.hasBoundedHeight) c.maxHeight + k else c.maxHeight))
    layout(p.width, maxOf(c.minHeight, p.height - k)) { p.place(0, -k) }
}

/** How far a label reaches across the full screen (its left inset included) before it ellipsizes: NX Redux's long titles run over the art. */
internal const val NX_LABEL_REACH = 0.9f

/** The row end padding that ends a label ([pillPad] inside its pill) at [NX_LABEL_REACH] of the screen, for a list [listWidth] wide between
 * the [leftInset] and [rightInset] (never negative). */
internal fun nxLabelEndPad(listWidth: Dp, leftInset: Dp, rightInset: Dp, pillPad: Dp): Dp =
    maxOf(0.dp, (listWidth + leftInset + rightInset) * (1f - NX_LABEL_REACH) - rightInset - pillPad)

/** The hint bar's ink below its top edge: its vertical padding plus the glyph art's transparent margin. */
internal val NxHintInkTop = 6.dp + HINT_GLYPH_SIZE * NAV_ART_MARGIN

/**
 * A list body under an optional page [title] ([NxPageTitle]) and over the hint bar: [content] gets the height under the
 * title, less a balance that centres it by eye. The title row centres its text in [MENU_TAB_ROW_HEIGHT], so it leaves
 * more empty space under its letters (row bottom to baseline) than the hint bar leaves over its glyphs ([NxHintInkTop]);
 * content centred in the plain gap reads as sitting low. The difference goes under the content (or over it, should the
 * title ever leave less), so the content's own centre sits midway between the title's letters and the hint glyphs.
 * Measured in the one layout pass from the title's first baseline, so it follows the title's font and size with no jump.
 */
@Composable
internal fun NxTitledBody(title: String?, modifier: Modifier = Modifier, titleSuffix: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    if (title == null) {
        Box(modifier) { content() }
        return
    }
    Layout(content = { NxPageTitle(title, suffix = titleSuffix, trailing = trailing); Box { content() } }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val head = measurables[0].measure(loose)
        val baseline = head[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: head.height
        val shift = (head.height - baseline) - NxHintInkTop.roundToPx()   // > 0: the title leaves more; the extra goes below
        val bodyHeight = (constraints.maxHeight - head.height - kotlin.math.abs(shift)).coerceAtLeast(0)
        val body = measurables[1].measure(Constraints.fixed(constraints.maxWidth, bodyHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            head.place(0, 0)
            body.place(0, head.height + maxOf(0, -shift))
        }
    }
}
