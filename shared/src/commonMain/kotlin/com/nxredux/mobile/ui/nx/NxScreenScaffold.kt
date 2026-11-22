package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nxredux.mobile.ui.LocalSettingsBlock
import com.nxredux.mobile.ui.SettingsBlockMode
import com.nxredux.mobile.ui.SettingsRowsBlock
import com.nxredux.mobile.ui.SettingsWordList
import com.nxredux.mobile.ui.listArtLabelEnd

/**
 * The selected item's info band on an [NxScreenScaffold] list, fixed just above the hint bar with its bottom [overlap]
 * reaching into the bar's empty top. The list keeps [reserve] (the band's height less the overlap) clear above the hint
 * bar and fills the rest with whole rows ([nxListFit]). The fit is fixed per screen, so rows never move as the band's
 * text changes.
 */
class NxHintsBand(
    val reserve: Dp, val overlap: Dp,
    /** Drawn in the hint bar's row while the bar is hidden ([NxScreenScaffold]'s hideHints), e.g. a List game list's info line. */
    val barInfo: (@Composable () -> Unit)? = null,
    val content: @Composable () -> Unit,
)

/**
 * A list or grid body over a hint bar: the shared shape of the main menu, Home, game lists, and the tool and settings
 * pages. Without a [band] the list shows the whole rows that fit. With one, the rows fill the slot above the band's
 * reserve. The rows fade at the edge they scroll on ([nxScrollFade]).
 *
 * [title]: a tool page's title row ([NxPageTitle]) above the list, the list centred by eye under it ([NxTitledBody]).
 * [showArt]: with art (drawn full-screen behind by App) the labels run over it up to [NX_LABEL_REACH] of the screen;
 * without, the list keeps [NxList]'s narrow right margin.
 * [rowKind] [NxRowKind.OPTIONS]: [NxOptionsList] rows (a list row's pitch) in place of the pill list, art unused, always
 * at the top. On a titled page a list that fits takes only its rows' height.
 * [grid]: a body in place of the list (Grid, Carousel and Backdrop pages, the tool apps grid), running down to the hint
 * bar; [rows], [title] and [band] are unused.
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
    hideHints: Boolean = false,
    hintShade: Boolean = false,
    selection: (() -> Int)? = null,   // read by the pill rows in place of selectedIndex, so a selection step recomposes only the rows that changed
    artBox: Boolean = false,   // boxed List art on the right: the labels stop short of it (listArtLabelEnd)
    keepBarRoom: Boolean = true,   // false: hidden hints leave no room either, the body runs to the bottom (a main menu Carousel, centred in what shows)
    header: (@Composable () -> Unit)? = null,   // under the title, above the rows (a page's banner); the rows fit what it leaves
    onPageRows: ((Int) -> Unit)? = null,   // the whole rows the list shows (a game list's LEFT / RIGHT page)
) {
    val listSelection = selection ?: { selectedIndex }
    val listState = rememberLazyListState()
    // The Settings rows block ([LocalSettingsBlock]): a titled list page's rows from the bottom, under its title line.
    val block = LocalSettingsBlock.current
    if (block != null && grid == null && band == null) {
        NxRowsBlockPage(block, title, rows, listSelection, hints, onSelect, onOpen, modifier, onHint, onLongPress, rowKind, header, listState)
        return
    }
    // Every list's pitch under Appearance → Text size: the list's at Large, in proportion at Small (the text follows the row).
    val bandPitch = nxOptionsRowHeight(NxListPitch, LocalNxOptionText.current)
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The body ends at the hint bar's top edge. It draws above the bar, so a band can reach into the bar's top.
            if (grid != null) Box(Modifier.weight(1f).fillMaxWidth()) {
                grid(Modifier.fillMaxSize())
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().zIndex(1f)) {
                // No band: split the height into N rows and show the whole rows that fit.
                // With a band: the slot (the height less the band's reserve) holds exactly N whole rows.
                val density = LocalDensity.current
                val bottomPad = band?.reserve ?: 0.dp
                // A band's slot in whole px as the padded box below lays it out.
                val rowHeight = if (band != null) with(density) {
                    nxListFit(maxHeight.roundToPx() - band.reserve.roundToPx(), density.density, bandPitch).second
                }
                else maxOf(2, nxRowCountFor(maxHeight, bandPitch)).let { maxHeight / it }
                val gutters = band == null
                // Under a title the leftover height (a title row can leave a part row) is split above and below, so the
                // list sits centred between the title and the hints.
                // A titled options list sits at the top instead, directly under the title.
                val centred = title != null && rowKind != NxRowKind.OPTIONS
                NxTitledBody(title, Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) {
                    header?.invoke()
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxSize().padding(bottom = bottomPad),
                        contentAlignment = if (centred) Alignment.CenterStart else Alignment.TopStart,
                    ) {
                        // Untitled (the one-pane Settings pages), an options list fills the slot as the pill list does, so
                        // its first row sits where the Settings menu's does.
                        val options = rowKind == NxRowKind.OPTIONS && title != null
                        val slack = 2f / LocalDensity.current.density
                        // A titled options list that fits takes its own height (its rows are list rows: Text size already in the pitch).
                        val optionsHeight = if (options) nxOptionsFitHeight(rows, rowHeight) else 0.dp
                        val fits = options && optionsHeight.value <= maxHeight.value + slack
                        // An options gap takes half a row of the slot: the whole rows fit in the rest.
                        val gap = if (rowKind == NxRowKind.OPTIONS && !fits && rows.any { it.spacer }) rowHeight * NX_OPTIONS_GAP else 0.dp
                        val whole = nxWholeRows(maxHeight - gap, rowHeight, pxSlack = slack)
                        if (onPageRows != null) {
                            val report by rememberUpdatedState(onPageRows)
                            LaunchedEffect(whole) { report(whole) }
                        }
                        // In the rows' own rounded px, so the list rests on whole rows ([nxRowsSlotPx]).
                        val slot = if (fits) optionsHeight else with(density) { nxRowsSlotPx(rowHeight.roundToPx(), whole, gap.roundToPx()).toDp() }
                        val listWidth = maxWidth
                        // An options list fades itself (its rows rest whole); a titled page's fade is half a row, the main menu's taller.
                        val fade = rowKind != NxRowKind.OPTIONS
                        Box(Modifier.fillMaxWidth().height(slot).then(if (fade) Modifier.nxScrollFade(listState, height = if (gutters) rowHeight / 2 else 56.dp, strong = true) else Modifier)) {
                            if (rowKind == NxRowKind.OPTIONS) {
                                NxOptionsList(
                                    rows, selectedIndex, onSelect, onOpen, rowHeight = rowHeight, listText = NxTextSizes.listLabel(bandPitch),
                                    modifier = Modifier.fillMaxSize(), listState = listState, onLongPress = onLongPress,
                                )
                            } else if (!showArt) {
                                NxList(rows, listSelection, onSelect, onOpen, Modifier.fillMaxSize(), listState = listState, rowHeightOverride = rowHeight, onLongPress = onLongPress, edgeMargin = 1)
                            } else {
                                // The list spans the width over the art App draws behind (NX Redux draws titles over it), its
                                // labels reaching [NX_LABEL_REACH] of the screen.
                                val insets = nxScreenInsets()   // the insets App pads the screens by
                                val dir = LocalLayoutDirection.current
                                val endPad = with(density) {
                                    nxLabelEndPad(listWidth, insets.getLeft(this, dir).toDp(), insets.getRight(this, dir).toDp(), nxListInset())
                                }
                                NxList(rows, listSelection, onSelect, onOpen, Modifier.fillMaxSize(), listState = listState, rowHeightOverride = rowHeight, onLongPress = onLongPress, edgeMargin = 1,
                                    endPadOverride = if (artBox) maxOf(endPad, listArtLabelEnd(listWidth.value, nxPageInset().end.value).dp) else endPad)
                            }
                        }
                    }
                } }
                // The band, over the list and art, its bottom [NxHintsBand.overlap] inside the hint bar. A plain Box takes
                // no pointer input, so hint taps still reach the bar. Touch only there is no bar to reach into, so the band
                // rests on the body's bottom, unless hideHints keeps the bar's row (a List's info line in it).
                val noBarRow = touchOnly() && !hideHints
                band?.let { b -> Box(Modifier.align(Alignment.BottomCenter).offset(y = if (noBarRow) 0.dp else b.overlap).fillMaxWidth()) { b.content() } }
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
 * round per item and n of them could overrun the slot, clipping the last. The under-n px left over fall below the rows,
 * into the gap over the band. */
internal fun nxListFit(slotPx: Int, density: Float, pitch: Dp = NxListPitch): Pair<Int, Dp> {
    val n = maxOf(2, nxRowCountFor((slotPx / density).dp, pitch))
    return n to ((slotPx / n) / density).dp
}

/** [NxList]'s pill padding above and below its label, as a share of the row pitch. */
internal const val NX_PILL_PAD = 0.08f

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

/**
 * A list page as the Settings rows block ([SettingsRowsBlock]): [title] its first line, [header] under it, the rows at
 * the list pitch (Appearance → Text size) taking only their own height, from the bottom; past the room they scroll under
 * the block's thin fade ([com.nxredux.mobile.ui.SETTINGS_ROWS_FADE]), the next row peeking, the selection kept a row clear of it. Pill-kind rows draw as the section block's words
 * ([SettingsWordList], no pill), options rows as NX Redux's options row; the hint bar below, as the page's.
 */
@Composable
internal fun NxRowsBlockPage(
    block: SettingsBlockMode, title: String?, rows: List<NxRow>, selection: () -> Int, hints: List<Hint>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, onHint: ((NxButton) -> Unit)? = null,
    onLongPress: ((Int) -> Unit)? = null, rowKind: NxRowKind = NxRowKind.PILL, header: (@Composable () -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    val pitch = nxOptionsRowHeight(NxListPitch, LocalNxOptionText.current)
    Column(modifier.fillMaxSize()) {
        SettingsRowsBlock(block, title, Modifier.weight(1f), header, listBottom = true) {
            if (rowKind == NxRowKind.OPTIONS) {
                NxOptionsList(
                    rows, selection(), onSelect, onOpen, rowHeight = pitch, listText = NxTextSizes.listLabel(pitch),
                    modifier = Modifier.fillMaxWidth().height(nxOptionsFitHeight(rows, pitch) + com.nxredux.mobile.ui.LocalSettingsRowsBottom.current), listState = listState,
                    onLongPress = onLongPress,
                )
            } else {
                // The section block's words, no pill: its own height, held to the room (the list then scrolls).
                SettingsWordList(rows, selection, onSelect, onOpen, Modifier.fillMaxWidth(), listState)
            }
        }
        NxHintBar(hints, onHint = onHint)
    }
}
