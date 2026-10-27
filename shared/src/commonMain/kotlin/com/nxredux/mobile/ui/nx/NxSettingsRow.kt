package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.LocalSettingsBlock
import com.nxredux.mobile.ui.SettingsRowsBlock
import com.nxredux.mobile.ui.settingsRowsFade

/*
 * The app's touch settings rows (Settings' value pages, Emulator Settings, Game Settings), drawn as the in-game menu's
 * options rows are, with the same touch row and controls (NxTouchRows.kt); a group label, a spacer, a paragraph, a
 * button and a disabled row are this list's own.
 */

/**
 * One touch settings row. [info]: its description line (at most [SETTING_DESC_LINES] on the row); [detail]: a fuller
 * text the info popup shows when [info] is a short line of it. [choices]: every value's label in LEFT / RIGHT order
 * (empty: not a value row), [index] the current one's (-1 none). [value]: a text on the right of a row without choices
 * (a count, a read-only value). [opens]: it opens a page (`›`). [section]: a group's label, grey, never selected;
 * [spacer]: half a row of space between groups. [warning]: the description is a problem, in the warning colour.
 */
data class NxSettingRow(
    val label: String,
    override val info: String = "",
    override val detail: String = "",
    val choices: List<String> = emptyList(),
    val index: Int = -1,
    val value: String? = null,
    val opens: Boolean = false,
    val enabled: Boolean = true,
    val section: Boolean = false,
    val spacer: Boolean = false,
    val warning: Boolean = false,
    /** A button's label on the row's right (Library's Rescan): the row's tap (or A) is its press. */
    val button: String? = null,
    /** [label] as a plain paragraph (System's licences): the hint colour across the row, wrapping; never selected or tapped. */
    val paragraph: Boolean = false,
) : NxDescribedRow

val NxSettingRow.control: NxSettingControl get() = nxSettingControl(choices)

/** The choice a switch row's flip sets: the other of its two values. */
val NxSettingRow.flipped: Int get() = 1 - index.coerceIn(0, 1)

/** A touch on a settings row's control or info icon. */
sealed interface NxSettingEvent {
    /** Set row [row]'s value to choice [index] (a segment, the switch). */
    data class Choose(val row: Int, val index: Int) : NxSettingEvent
    data class OpenPicker(val row: Int) : NxSettingEvent
    /** The row's info icon. */
    data class Info(val row: Int) : NxSettingEvent
}

/**
 * Something drawn under a list's last row, scrolling with the rows (a Layouts group's picture): its own width is the
 * list's plus [bleedStart] and [bleedEnd] (negative: inside the list's edges); [height] for that width, and its
 * [content] at it. With a pad it is kept in view while the last row is selected ([nxFooterKeep]).
 */
class NxListFooter(
    val bleedStart: Dp = 0.dp, val bleedEnd: Dp = 0.dp,
    val height: (Dp) -> Dp, val content: @Composable (Dp) -> Unit,
)

/** The footer item's key in a list ([NxListFooter]): not a row, so keep-in-view does not size rows by it. */
internal const val NX_LIST_FOOTER_KEY = "footer"

/** How keep-in-view treats a list's footer for the selected row ([nxFooterKeep]). */
internal enum class NxFooterKeep {
    /** The row alone, as any list. */
    ROW,
    /** The footer item: it fits under the row, so the whole picture comes into view. */
    FOOTER,
    /** The row at the top, [edge][nxFooterKeep] clear: the footer is too tall to show whole with it, so as much as fits under it. */
    ROW_AT_TOP,
}

/**
 * Keep-in-view for the selected row [selected] of [rows] with a footer [footerPx] tall ([footer] false: none): while
 * the last row that can be selected is selected, the footer item ([NxFooterKeep.FOOTER]) when it, the row
 * ([rowPx]) and the fade [edge] above and below fit the [viewportPx]; else the row at the top under its fade edge
 * ([NxFooterKeep.ROW_AT_TOP]), so the picture shows as far as fits and the selection never leaves the screen. Any other
 * row: [NxFooterKeep.ROW]. The selection stays on the row.
 */
internal fun nxFooterKeep(
    rows: List<NxSettingRow>, selected: Int, footer: Boolean, footerPx: Int, rowPx: Int, edge: Int, viewportPx: Int,
): NxFooterKeep {
    val last = rows.indexOfLast { !it.section && !it.spacer && !it.paragraph }
    if (!footer || selected < 0 || selected != last) return NxFooterKeep.ROW
    return if (footerPx + rowPx + edge * 2 <= viewportPx) NxFooterKeep.FOOTER else NxFooterKeep.ROW_AT_TOP
}

/** A description as a share of its name. */
private const val DESC_OF_LABEL = 0.9f   // one text-size step ([NxTextSizes.STEP]) over 0.72

/** Row [r]'s segments go under its text: an enabled segmented row whose choices don't fit on its right. */
internal fun NxTouchRowSizes.segmentsBelow(r: NxSettingRow): Boolean = r.enabled && r.control == NxSettingControl.SEGMENTED && !segmentsRight(r.label, r.choices)

/** Row [r]'s height: a spacer half a bare row, a group label half a row, else the touch row's ([NxTouchRowSizes.height]). */
internal fun NxTouchRowSizes.heightOf(r: NxSettingRow): Dp = when {
    r.spacer -> bare * NX_OPTIONS_GAP
    r.section -> row * SECTION_OF_ROW
    else -> height(described = r.info.isNotBlank(), segmentsBelow = segmentsBelow(r))
}

/** A group label's row as a share of a row. */
private const val SECTION_OF_ROW = 0.5f

/**
 * A titled settings page of touch rows: the [title], an optional [header] under it (a banner, a page's lines), the rows
 * from the top ([NxSettingsList]), the hint bar. [preview]: the two-pane Settings' unfocused page (taps only open it).
 */
@Composable
fun NxSettingsPage(
    title: String?, rows: List<NxSettingRow>, selectedIndex: Int, hints: List<Hint>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onEvent: (NxSettingEvent) -> Unit,
    modifier: Modifier = Modifier,
    onTruncated: (String, Boolean) -> Unit = { _, _ -> },
    onHint: ((NxButton) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    preview: Boolean = false,
    footer: NxListFooter? = null,   // the Settings rows block only: under the rows, scrolling with them
) {
    // The Settings rows block ([LocalSettingsBlock]): the title its first line, the rows from the bottom.
    LocalSettingsBlock.current?.let { block ->
        Column(modifier.fillMaxSize()) {
            SettingsRowsBlock(block, title, Modifier.weight(1f), header) {
                NxSettingsList(rows, selectedIndex, onSelect, onOpen, onEvent, Modifier, onTruncated, preview, footer)
            }
            NxHintBar(hints, onHint = onHint)
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        NxTitledBody(title, Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                header?.invoke()
                NxSettingsList(rows, selectedIndex, onSelect, onOpen, onEvent, Modifier.weight(1f), onTruncated, preview)
            }
        }
        NxHintBar(hints, onHint = onHint)
    }
}

/**
 * Touch settings rows ([NxSettingTouchRow]) from the top, as tall as they need up to the room given; past it the list
 * scrolls smoothly under edge fades, the selection kept a row clear of them. A tap: a switch row flips, a picker row
 * opens its picker, a segmented row is selected (its segments take their own taps), any other row is the lists' tap
 * ([nxTap]). Group labels, spacers and paragraphs take none.
 */
@Composable
fun NxSettingsList(
    rows: List<NxSettingRow>, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onEvent: (NxSettingEvent) -> Unit,
    modifier: Modifier = Modifier,
    onTruncated: (String, Boolean) -> Unit = { _, _ -> },
    preview: Boolean = false,
    footer: NxListFooter? = null,
) {
    val itemHeight = nxOptionsRowHeight(NxListPitch, LocalNxOptionText.current)   // Appearance → Text size
    val listState = rememberLazyListState()
    val tapOpens = LocalNxTapOpens.current
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = NxOptionsSidePad).padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
        val sizes = nxTouchRowSizes(itemHeight, maxWidth, DESC_OF_LABEL)
        // A paragraph wraps to its own height, unknown here: such a list takes all the room given.
        // Summed in each row's own rounded px, as the list lays them out: a Dp sum can come out a px short, which would
        // leave the list scrollable by that px and its bottom fade showing over the last row.
        val density = LocalDensity.current
        val listWidth = maxWidth
        // The footer's own width: the list's and its bleeds. A bleed past the list's edge widens the lazy list itself by
        // it (the rows padded back in), so the footer is laid out, faded and scrolled inside it rather than clipped.
        val outStart = maxOf(0.dp, footer?.bleedStart ?: 0.dp)
        val outEnd = maxOf(0.dp, footer?.bleedEnd ?: 0.dp)
        val footerWidth = listWidth + (footer?.bleedStart ?: 0.dp) + (footer?.bleedEnd ?: 0.dp)
        val footerHeight = footer?.height?.invoke(footerWidth) ?: 0.dp
        val total = if (rows.any { it.paragraph }) maxHeight
            else with(density) { (rows.sumOf { sizes.heightOf(it).roundToPx() } + footerHeight.roundToPx()).toDp() }
        // Rows differ in height (a bare row, a group label, segments under the text): kept clear of the fade in px.
        val fade = settingsRowsFade(sizes.row / 2)   // the Settings rows block's 24 dp
        val edgePx = with(density) { fade.roundToPx() }
        val keep = with(density) {
            nxFooterKeep(
                rows, selectedIndex, footer != null, footerHeight.roundToPx(),
                rows.getOrNull(selectedIndex)?.let { sizes.heightOf(it).roundToPx() } ?: 0, edgePx, minOf(total, maxHeight).roundToPx(),
            )
        }
        NxKeepSelectedInView(
            listState, if (keep == NxFooterKeep.FOOTER) rows.size else selectedIndex, rows.size + (if (footer != null) 1 else 0),
            edge = edgePx,
        )
        // A footer too tall to show whole under the last row: that row at the top under its fade edge, the picture
        // below it as far as fits.
        if (keep == NxFooterKeep.ROW_AT_TOP) LaunchedEffect(selectedIndex) {
            snapshotFlow { listState.layoutInfo }.first { it.totalItemsCount > selectedIndex && it.visibleItemsInfo.isNotEmpty() }
            listState.animateScrollToItem(selectedIndex)
            listState.animateScrollBy(-edgePx.toFloat())
        }
        LazyColumn(
            Modifier.nxBleed(outStart, outEnd).height(minOf(total, maxHeight)).nxScrollFade(listState, height = fade, strong = true),
            state = listState, flingBehavior = rememberNxRowSnapFling(listState),
        ) {
            itemsIndexed(rows) { i, row ->
                val selected = i == selectedIndex
                val default = { nxTap(i, selected, tapOpens(i), onSelect, onOpen) }
                // Padded back to the list's edges when the lazy list is widened for its footer.
                Box(Modifier.padding(start = outStart, end = outEnd)) {
                    NxSettingTouchRow(
                        row, selected, sizes, index = i,
                        onTap = if (preview) default else settingRowTap(row, i, onSelect, onEvent) ?: default,
                        onEvent = if (preview) null else onEvent,
                        onTruncated = { info, t -> if (selected) onTruncated(info, t) },
                    )
                }
            }
            // Under the last row, scrolling with them (a Layouts group's picture); never selected.
            if (footer != null) item(key = NX_LIST_FOOTER_KEY) {
                // From the lazy list's left: the list's own left ([outStart]) less the footer's start bleed.
                Box(Modifier.fillMaxWidth().height(footerHeight).padding(start = outStart - footer.bleedStart)) {
                    footer.content(footerWidth)
                }
            }
        }
    }
}

/**
 * This element [start] wider on its left and [end] on its right than it is given, drawn that far past its slot's edges
 * (a list whose footer runs past the rows). Nothing when both are zero.
 */
private fun Modifier.nxBleed(start: Dp, end: Dp): Modifier =
    if (start <= 0.dp && end <= 0.dp) fillMaxWidth()
    else layout { measurable, constraints ->
        val s = start.roundToPx()
        val w = constraints.maxWidth + s + end.roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-s, 0) }
    }

/** What a tap on row [index] does, when not the lists' tap (null): a switch flips, a picker opens, a segmented row is selected. */
private fun settingRowTap(row: NxSettingRow, index: Int, onSelect: (Int) -> Unit, onEvent: (NxSettingEvent) -> Unit): (() -> Unit)? {
    if (!row.enabled || row.choices.isEmpty()) return null
    return when (row.control) {
        NxSettingControl.SWITCH -> { { onEvent(NxSettingEvent.Choose(index, row.flipped)) } }
        NxSettingControl.PICKER -> { { onEvent(NxSettingEvent.OpenPicker(index)) } }
        NxSettingControl.SEGMENTED, NxSettingControl.VALUE -> { { onSelect(index) } }
    }
}

/**
 * One touch settings row ([NxTouchRow]): the name with the info icon when the description is cut off or has a fuller
 * detail ([hasMoreInfo]), the description under it, and on the right the control ([NxSettingControl]), a value, a button
 * or a page's `›`; a segmented row's segments that don't fit there sit under the text (right-aligned in the Settings rows block). A group label is a
 * grey line, a spacer empty, a paragraph wrapping grey text (MiSans Regular, [LocalNxFontWeights]). [onEvent] null: the
 * controls take no taps (a preview). [onTruncated] hears whether the description fits.
 */
@Composable
private fun NxSettingTouchRow(
    row: NxSettingRow, selected: Boolean, sizes: NxTouchRowSizes, index: Int,
    onTap: () -> Unit, onEvent: ((NxSettingEvent) -> Unit)?, onTruncated: (String, Boolean) -> Unit,
) {
    val colors = LocalNxColors.current
    if (row.spacer) { Spacer(Modifier.fillMaxWidth().height(sizes.heightOf(row))); return }
    if (row.section) {
        Box(Modifier.fillMaxWidth().height(sizes.heightOf(row)).padding(horizontal = NxOptionsInnerPad), contentAlignment = Alignment.BottomStart) {
            Text(row.label, color = colors.hint, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = sizes.desc, lineHeight = sizes.desc, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    if (row.paragraph) {
        Text(
            row.label, color = colors.hint, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal, fontSize = sizes.desc, lineHeight = sizes.descLine,
            modifier = Modifier.fillMaxWidth().padding(horizontal = NxOptionsInnerPad, vertical = 6.dp),
        )
        return
    }
    val choose: ((Int) -> Unit)? = if (row.enabled) onEvent?.let { e -> { k: Int -> e(NxSettingEvent.Choose(index, k)) } } else null
    NxTouchRow(
        row.label, row.info, row.hasMoreInfo, selected, sizes, sizes.heightOf(row),
        onTap = onTap, onInfo = onEvent?.let { e -> { e(NxSettingEvent.Info(index)) } }, onTruncated = onTruncated,
        enabled = row.enabled, warning = row.warning,
        below = if (sizes.segmentsBelow(row)) { { NxSegments(row.choices, row.index, sizes.value, choose) } } else null,
    ) {
        SettingControlView(row, sizes, choose, onEvent?.let { e -> { e(NxSettingEvent.OpenPicker(index)) } })
    }
}

/** The right side of a row: its control by [NxSettingControl], else its button, or its value and a page's `›`. */
@Composable
private fun SettingControlView(row: NxSettingRow, sizes: NxTouchRowSizes, choose: ((Int) -> Unit)?, openPicker: (() -> Unit)?) {
    val colors = LocalNxColors.current
    val current = row.choices.getOrNull(row.index) ?: row.value
    when {
        row.enabled && row.control == NxSettingControl.SWITCH ->
            NxSwitch(on = row.index == nxSwitchOnIndex(row.choices), onClick = choose?.let { c -> { c(row.flipped) } })
        // Short choices that fit on the right ([NxTouchRowSizes.segmentsRight]); longer ones draw under the text.
        row.enabled && row.control == NxSettingControl.SEGMENTED -> NxSegments(row.choices, row.index, sizes.value, choose)
        row.enabled && row.control == NxSettingControl.PICKER -> NxPickerValue(current.orEmpty(), sizes.value, openPicker)
        row.button != null -> Box(
            Modifier.clip(RoundedCornerShape(50)).border(1.dp, colors.inkRing, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) { NxValueText(row.button, sizes.value, colors.listText) }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (current != null) NxValueText(current, sizes.value, colors.listText, Modifier.widthIn(max = 180.dp))
            if (row.opens) {
                if (current != null) Spacer(Modifier.width(6.dp))
                NxValueText("›", if (current != null) sizes.value * 1.2f else sizes.label, if (current != null) colors.hint else colors.listText)
            }
        }
    }
}
