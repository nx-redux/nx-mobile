package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nxredux.mobile.settings.OptionTextSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How a list page draws its rows: the pill list (categories, game lists, the in-game main menu) or NX Redux's options
 * row (ui_list.c UI_renderSettingsRow), the "Label: Value" pages.
 */
enum class NxRowKind { PILL, OPTIONS }

/** An options row's value of its label: smaller, so the label leads. */
const val NX_OPTIONS_VALUE_OF_LABEL = 0.8f

/** Appearance → Text size's share of the list label for an options row's label ([OptionTextSize.ofListLabel]), as App provides it. */
val LocalNxOptionText = staticCompositionLocalOf { OptionTextSize.DEFAULT.ofListLabel }

/**
 * A list's row pitch under Appearance → Text size ([ofListLabel], [LocalNxOptionText]): [listPitch] at Large, in
 * proportion at Small (0.6 / 0.8). Every list (pill and options) is scaled here, its text following the row.
 */
fun nxOptionsRowHeight(listPitch: Dp, ofListLabel: Float = OptionTextSize.DEFAULT.ofListLabel): Dp =
    listPitch * (ofListLabel / OptionTextSize.LARGE.ofListLabel)

/** Appearance → Text size as a scale of the fixed sizes (page title, hint bar): 1 at Large, 0.75 at Small. */
fun nxTextScale(ofListLabel: Float): Float = ofListLabel / OptionTextSize.LARGE.ofListLabel

/** The scale ([nxTextScale]) under the screen's Appearance → Text size ([LocalNxOptionText]). */
@Composable
fun nxTextScale(): Float = nxTextScale(LocalNxOptionText.current)

/** A game card's name (the game list Carousel's selected one, Home's cards): 18 sp at Large, under Text size. */
@Composable
internal fun nxCardTitleSize(): TextUnit = 18.sp * nxTextScale()

/** A game card's stats and info lines: 80% of its name ([nxCardTitleSize]). */
@Composable
internal fun nxCardStatsSize(): TextUnit = nxCardTitleSize() * 0.8f

/** An options row's label from its list's label [listText] (already scaled by Text size): Large's share of it (0.8). */
fun nxOptionsLabelSize(listText: TextUnit): TextUnit = listText * OptionTextSize.LARGE.ofListLabel

/** An options row's value from its list's label [listText]: [NX_OPTIONS_VALUE_OF_LABEL] of the row's label. */
fun nxOptionsValueSize(listText: TextUnit): TextUnit = nxOptionsLabelSize(listText) * NX_OPTIONS_VALUE_OF_LABEL

/** An options spacer's height ([NxRow.spacer]) as a share of the options row. */
const val NX_OPTIONS_GAP = 0.5f

/** The height [rows] take at [rowHeight]: whole rows, and half a row for each spacer. */
fun nxOptionsListHeight(rows: List<NxRow>, rowHeight: Dp): Dp =
    rows.fold(0.dp) { h, r -> h + if (r.spacer) rowHeight * NX_OPTIONS_GAP else rowHeight }

/** The px [rows] take as [NxOptionsList] lays them out: [rowPx] a row, [gapPx] a spacer, each already rounded. */
internal fun nxOptionsListHeightPx(rows: List<NxRow>, rowPx: Int, gapPx: Int): Int = rows.sumOf { if (it.spacer) gapPx else rowPx }

/**
 * [nxOptionsListHeight] in the list's own rounded px: a list that fits, sized by this, holds its rows exactly (sized in
 * dp it can come out a px under their sum, and scroll by it, or hide its last row as cut).
 */
@Composable
fun nxOptionsFitHeight(rows: List<NxRow>, rowHeight: Dp): Dp = with(LocalDensity.current) {
    nxOptionsListHeightPx(rows, rowHeight.roundToPx(), (rowHeight * NX_OPTIONS_GAP).roundToPx()).toDp()
}

/** A scrolling options list's arrow strip, one above and one below the rows: the arrow (a quarter of its width high) with
 * 2 dp of air each side; the row's own padding adds the rest. Smaller than the pill list's half-row gutter, so the strips
 * don't cost an options row. */
val NxOptionsArrowStrip: Dp = NxScrollArrowWidth / 4 + 4.dp

/** NX Redux SETTINGS_ROW_PADDING (8), at the list's 1.25: the label and value inset inside the row. */
val NxOptionsInnerPad: Dp = 10.dp

/** The value as drawn: a cycleable value shows its change arrows while its row is selected (NX Redux "< value >"). */
fun nxOptionsValueText(value: String, selected: Boolean, cycleable: Boolean): String =
    if (selected && cycleable) "< $value >" else value

/** A selected options row's label rect ([fill]) and label text ([text]), and the bar behind its value ([bar]) and the value on it ([barText]). */
data class NxOptionsSelection(val fill: Color, val text: Color, val bar: Color, val barText: Color)

/**
 * The selection [colors] give an options row, as NX Redux's UI_renderSettingsRow draws its label rect in accentPill() with
 * the accent's text: [NxColors.accent] under [NxColors.onAccent], as the main menu's pill, and the bar behind the value in
 * [NxColors.secondaryAccent] under [NxColors.onSecondaryAccent] (the Secondary accent setting). The Settings value pages,
 * the in-game menu's options rows and its adjust strip all draw it.
 */
fun nxOptionsSelection(colors: NxColors): NxOptionsSelection =
    NxOptionsSelection(colors.accent, colors.onAccent, colors.secondaryAccent, colors.onSecondaryAccent)


/**
 * One options row, filling the width it is given (the caller sets the side margins, NX Redux's PADDING). The label sits
 * [innerPad] in, truncated before the value; the value ([NxRow.secondary]) is right-aligned [innerPad] from the end,
 * `< value >` while selected on a [NxRow.cycleable] row. Selected with a value: a full-width bar under a label-width rect
 * ([nxOptionsSelection]: the Secondary accent's bar under the accent and their texts); selected without one: the label
 * rect only; unselected: no background. [onValueClick], when set, makes the selected row's value tappable.
 */
@Composable
fun NxOptionsRow(
    row: NxRow, selected: Boolean, rowHeight: Dp, labelSize: TextUnit, valueSize: TextUnit, innerPad: Dp,
    modifier: Modifier = Modifier,
    onValueClick: (() -> Unit)? = null,
) {
    val colors = LocalNxColors.current
    val selection = nxOptionsSelection(colors)
    val font = LocalNxFont.current
    val corner = RoundedCornerShape(50)   // a pill, round-ended as the list's
    val value = row.secondary?.let { nxOptionsValueText(it, selected, row.cycleable) }
    val alpha = if (row.enabled) 1f else NX_DISABLED_ALPHA
    Layout(
        modifier = modifier.fillMaxWidth().height(rowHeight)
            .let { if (selected && value != null) it.background(selection.bar, corner) else it },
        content = {
            Box(
                Modifier.fillMaxHeight().let { if (selected) it.background(selection.fill, corner) else it }.padding(horizontal = innerPad),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    row.label, color = when { selected -> selection.text; row.section -> colors.hint; else -> colors.listText },
                    fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = labelSize, lineHeight = labelSize,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(alpha),
                )
            }
            if (value != null) {
                Box(
                    Modifier.fillMaxHeight().let { m -> if (selected && onValueClick != null) m.clickable(onClick = onValueClick) else m }
                        .padding(horizontal = innerPad),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        value, color = if (selected) selection.barText else colors.listText,
                        fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = valueSize, lineHeight = valueSize,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(alpha),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val inner = innerPad.roundToPx()
        val fixed = Constraints(minHeight = height, maxHeight = height)
        // The value first (at most 60% of the row), then the label in what is left: NX Redux keeps the value whole and
        // fits the label to its left, with one inset between them.
        val valuePlaceable = measurables.getOrNull(1)?.measure(fixed.copy(maxWidth = (width * 0.6f).toInt()))
        val labelMax = if (valuePlaceable == null) width else maxOf(0, width - valuePlaceable.width + inner)
        val labelPlaceable = measurables[0].measure(fixed.copy(maxWidth = labelMax))
        layout(width, height) {
            labelPlaceable.place(0, 0)
            valuePlaceable?.place(width - valuePlaceable.width, 0)
        }
    }
}

/**
 * A scrolling list of [NxOptionsRow]s at [rowHeight], sized from the pill list's [listText] (the label and value take
 * NX Redux's proportions of it). Selection, taps and long presses behave as [NxList]'s ([nxTap]). [sidePad] is the row's margin on each side.
 * [scrollFade]: the rows fade at the edge they scroll on ([nxScrollFade]), the selection kept a row clear of the fades
 * (while the list shows three rows or more).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NxOptionsList(
    rows: List<NxRow>, selectedIndex: Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit,
    rowHeight: Dp, listText: TextUnit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onLongPress: ((Int) -> Unit)? = null,
    sidePad: Dp = NxOptionsSidePad,
    scrollFade: Boolean = false,
) {
    val innerPad = NxOptionsInnerPad
    val labelSize = nxOptionsLabelSize(listText)   // [listText] carries Text size (the list's scaled pitch)
    val valueSize = nxOptionsValueSize(listText)
    val tapOpens = LocalNxTapOpens.current
    BoxWithConstraints(modifier) {
        // Whole-px rows and gaps, the heights the list lays out, so the rests ([NxOptionsGeometry]) are exact.
        val density = LocalDensity.current
        val rowPx = with(density) { rowHeight.roundToPx() }
        val gapPx = with(density) { (rowHeight * NX_OPTIONS_GAP).roundToPx() }
        val rowDp = with(density) { rowPx.toDp() }
        val gapDp = with(density) { gapPx.toDp() }
        val viewportPx = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val spacers = remember(rows) { rows.map { it.spacer } }
        val geometry = remember(spacers, rowPx, gapPx, viewportPx) { NxOptionsGeometry.of(spacers, rowPx, gapPx, viewportPx) }
        val margin = if (scrollFade && viewportPx >= rowPx * 3) rowPx else 0
        NxOptionsKeepSelectedInView(listState, selectedIndex, geometry, margin)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nxOptionsWholeRows(listState, geometry)
                .then(if (scrollFade) Modifier.nxScrollFade(listState, height = rowDp / 2, strong = true) else Modifier),
            contentPadding = PaddingValues(bottom = with(density) { geometry.endPadPx.toDp() }),
            flingBehavior = rememberNxOptionsSnapFling(listState, geometry),
        ) {
            itemsIndexed(rows) { i, row ->
                if (row.spacer) { Spacer(Modifier.fillMaxWidth().height(gapDp)); return@itemsIndexed }
                val selected = i == selectedIndex
                NxOptionsRow(
                    row, selected, rowDp, labelSize, valueSize, innerPad,
                    Modifier.combinedClickable(
                        onClick = { nxTap(i, selected, tapOpens(i), onSelect, onOpen) },
                        onLongClick = onLongPress?.let { lp -> { lp(i) } },
                    ).padding(horizontal = sidePad),
                )
            }
        }
    }
}

/**
 * The options row's side margin (NX Redux PADDING): the list inset less the row's own inset, so options labels start
 * where the pill list's labels and the page titles do.
 */
val NxOptionsSidePad: Dp = maxOf(0.dp, nxListInset() - NxOptionsInnerPad)
