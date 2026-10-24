package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow

/** An options row's description as a share of its name. */
private const val DESC_OF_LABEL = 0.72f

/** A description's most lines on its row; past them it ends in an ellipsis and the name gets the info icon. */
internal const val OPTION_DESC_LINES = 2

/** The touch rows' sizes from a list row's pitch [itemHeight]: the name, the description and its line, the value, the row. */
internal class OptionRowSizes(
    val label: TextUnit, val desc: TextUnit, val descLine: TextUnit, val value: TextUnit, val row: Dp, val segments: Dp,
    /** Whether a segmented row's choices fit on its right ([rememberSegmentsFitRight]); else they go under its text. */
    val segmentsRight: (List<String>) -> Boolean = { false },
    /** A row with no description: its name and the air alone. */
    val bare: Dp = row,
) {
    /** Row [r]'s segments go under its text: a segmented row whose choices don't fit on its right. */
    fun segmentsBelow(r: MenuRow): Boolean = r is MenuRow.Option && r.control == OptionControl.SEGMENTED && !segmentsRight(r.choices)

    /** Row [r]'s height: a row with no description is [bare]; segments under the text add their line ([segments]). */
    fun heightOf(r: MenuRow): Dp = (if (r.info.isBlank()) bare else row) + if (segmentsBelow(r)) segments else 0.dp
}

@Composable
internal fun optionRowSizes(itemHeight: Dp, rowWidth: Dp): OptionRowSizes {
    val listText = NxTextSizes.listLabel(itemHeight)
    val label = nxOptionsLabelSize(listText)
    val desc = label * DESC_OF_LABEL
    val descLine = desc * 1.25f
    // Name, two description lines and the row's air; every row of a page the same height, so the list's whole-row
    // scroll, snap and fades work as on the other pages.
    val row = with(LocalDensity.current) { (label * 1.25f).toDp() + (descLine * OPTION_DESC_LINES).toDp() } + OPTION_ROW_AIR * 2
    val value = nxOptionsValueSize(listText)
    // The segments' line under a segmented row's text: the track (its 2 dp inset, a choice's 5 dp padding and text) and a gap.
    val segments = with(LocalDensity.current) { (value * 0.9f).toDp() } + (2.dp + 5.dp) * 2 + 2.dp + SEGMENTS_GAP
    val bare = with(LocalDensity.current) { (label * 1.25f).toDp() } + OPTION_ROW_AIR * 2
    return OptionRowSizes(
        label, desc, descLine, value, maxOf(row, itemHeight), segments, rememberSegmentsFitRight(value, rowWidth - NxOptionsInnerPad * 2),
        maxOf(bare, itemHeight),
    )
}

/**
 * An options page as touch rows ([OptionTouchRow]): every row its name and description, an option its control on the
 * right. Whole rows from the top, the list scrolling smoothly under them with the edge fades, the selection kept a row
 * clear of them. No description box: an empty page shows its [MenuPage.footer] in the middle; a page with rows shows no
 * footer (no config status line).
 */
@Composable
internal fun OptionsBody(
    page: MenuPage,
    rowHeight: Dp,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onOption: (OptionEvent) -> Unit,
    onInfoTruncated: (String, Boolean) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalNxColors.current
    if (page.rows.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
            Text(page.footer, color = colors.hint, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = NxTextSizes.menuInfo, lineHeight = NxTextSizes.menuInfoLine, textAlign = TextAlign.Center)
        }
        return
    }
    val itemHeight = nxOptionsRowHeight(rowHeight, LocalNxOptionText.current)   // Appearance → Text size
    val listState = rememberLazyListState()
    val tapOpens = LocalNxTapOpens.current
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(start = NxOptionsSidePad, end = NxOptionsSidePad, top = rootListTopPad(itemHeight)),
            contentAlignment = Alignment.TopCenter,
        ) {
            val sizes = optionRowSizes(itemHeight, maxWidth)
            val count = page.rows.size
            // The rows' own heights (a segmented row is taller), the list no taller than the room.
            // In each row's own rounded px, as the list lays them out (a Dp sum can fall a px short and leave a fade).
            val total = with(LocalDensity.current) { page.rows.sumOf { sizes.heightOf(it).roundToPx() }.toDp() }
            // Rows differ in height (a bare row, segments under the text): kept clear of the fade in px.
            NxKeepSelectedInView(listState, page.selected, count, edge = with(LocalDensity.current) { (sizes.row / 2).roundToPx() })
            LazyColumn(
                Modifier.fillMaxWidth().height(minOf(total, maxHeight)).nxScrollFade(listState, height = sizes.row / 2, strong = true),
                state = listState, flingBehavior = rememberNxRowSnapFling(listState),
            ) {
                items(count) { i ->
                    val row = page.rows[i]
                    val selected = i == page.selected
                    val default = { nxTap(i, selected, tapOpens(i), onSelect, onActivate) }
                    OptionTouchRow(
                        row, selected, sizes,
                        onTap = optionRowTap(row, i, onSelect, onOption) ?: default,
                        onOption = { onOption(it) }, index = i,
                        onTruncated = { info, t -> if (selected) onInfoTruncated(info, t) },
                    )
                }
            }
        }
    }
}

/**
 * What a tap on options row [index] does, when not the lists' tap ([nxTap], null): a switch flips, a picker opens, a
 * segmented or value-only row is selected (its segments take their own taps). A visual setting keeps the lists' tap,
 * which enters adjust mode as A does; its control still acts on its own.
 */
private fun optionRowTap(row: MenuRow, index: Int, onSelect: (Int) -> Unit, onOption: (OptionEvent) -> Unit): (() -> Unit)? {
    if (row !is MenuRow.Option || row.visual) return null
    return when (row.control) {
        OptionControl.SWITCH -> { { onOption(OptionEvent.Choose(index, 1 - row.index.coerceIn(0, 1))) } }
        OptionControl.PICKER -> { { onOption(OptionEvent.OpenPicker(index)) } }
        OptionControl.SEGMENTED, OptionControl.VALUE -> { { onSelect(index) } }
    }
}

/**
 * One touch options row: the name (bold) with the info icon when the description is cut off or has a fuller detail
 * ([hasMoreInfo]), the description under it (at most [OPTION_DESC_LINES]), and on the right the option's control ([OptionControl]) or a submenu's `›`. Selected:
 * a soft rounded plate behind it, so the control stays readable. [onTruncated] hears whether the description fits.
 */
@Composable
private fun OptionTouchRow(
    row: MenuRow, selected: Boolean, sizes: OptionRowSizes,
    onTap: () -> Unit, onOption: (OptionEvent) -> Unit, index: Int,
    onTruncated: (String, Boolean) -> Unit,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val shape = RoundedCornerShape(14.dp)
    val info = row.info
    var truncated by remember(info) { mutableStateOf(false) }
    LaunchedEffect(selected, truncated, info) { if (selected && info.isNotBlank()) onTruncated(info, truncated) }
    // A segmented option draws its segments under the text, at its start (as a reference settings design does), not on the right.
    val below = sizes.segmentsBelow(row)
    Column(
        Modifier.fillMaxWidth().height(sizes.heightOf(row)).padding(vertical = 2.dp).clip(shape)
            .then(if (selected) Modifier.background(colors.accent.copy(alpha = SOFT_SELECTION_ALPHA), shape) else Modifier)
            .clickable(onClick = onTap).padding(horizontal = NxOptionsInnerPad),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.label, color = colors.listText, fontFamily = font, fontWeight = FontWeight.Bold,
                        fontSize = sizes.label, lineHeight = sizes.label * 1.25f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Cut off, or a fuller text behind it ([hasMoreInfo]): the popup shows it.
                    if (truncated || row.hasMoreInfo) InfoIcon(sizes.desc) { onOption(OptionEvent.Info(index)) }
                }
                if (info.isNotBlank()) {
                    Text(
                        info, color = colors.hint, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal,
                        fontSize = sizes.desc, lineHeight = sizes.descLine, maxLines = OPTION_DESC_LINES, overflow = TextOverflow.Ellipsis,
                        onTextLayout = { r -> truncated = r.hasVisualOverflow || (r.lineCount > 0 && r.isLineEllipsized(r.lineCount - 1)) },
                    )
                }
            }
            if (!below) {
                Spacer(Modifier.width(12.dp))
                when (row) {
                    is MenuRow.Option -> OptionControlView(row, index, sizes.value, onOption)
                    is MenuRow.Submenu, is MenuRow.Action -> ValueText("›", sizes.label, colors.listText)
                    is MenuRow.Slot -> Unit
                }
            }
        }
        if (below && row is MenuRow.Option) {
            Spacer(Modifier.height(SEGMENTS_GAP))
            Segments(row.choices, row.index, sizes.value) { onOption(OptionEvent.Choose(index, it)) }
        }
    }
}

/** The room above and below an options row's text (inside its plate), as the Settings rows'. */
private val OPTION_ROW_AIR = 12.dp

/** The gap between a segmented row's description and its segments. */
private val SEGMENTS_GAP = 6.dp

/** The selected touch row's plate: the accent, faint, so the row's own colours read on it. */
private const val SOFT_SELECTION_ALPHA = 0.16f

/** The control on an options row's right, by its [OptionControl]. */
@Composable
private fun OptionControlView(row: MenuRow.Option, index: Int, valueSize: TextUnit, onOption: (OptionEvent) -> Unit) {
    val colors = LocalNxColors.current
    when (row.control) {
        OptionControl.SWITCH -> NxSwitch(
            on = row.index == switchOnIndex(row.choices),
            // Its own tap (a visual row's body enters adjust mode instead).
            onClick = { onOption(OptionEvent.Choose(index, 1 - row.index.coerceIn(0, 1))) },
        )
        OptionControl.SEGMENTED -> Segments(row.choices, row.index, valueSize) { onOption(OptionEvent.Choose(index, it)) }
        OptionControl.PICKER -> Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOption(OptionEvent.OpenPicker(index)) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ValueText(row.valueLabel, valueSize, colors.listText, Modifier.widthIn(max = 180.dp))
            Spacer(Modifier.width(6.dp))
            ValueText("›", valueSize * 1.2f, colors.hint)
        }
        OptionControl.VALUE -> ValueText(row.valueLabel, valueSize, colors.listText, Modifier.widthIn(max = 180.dp))
    }
}

@Composable
private fun ValueText(text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
    Text(text, color = color, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/**
 * An on/off switch: on, the Secondary accent track with the accent knob right; off, a dim track with the knob left (on
 * a [lit] row, the accent pill, a dark track, as the dim one vanishes on it). The options rows' and the game panel's
 * Cheats rows'.
 */
@Composable
internal fun NxSwitch(on: Boolean, onClick: () -> Unit, lit: Boolean = false) {
    val colors = LocalNxColors.current
    Box(
        Modifier.size(SwitchWidth, SwitchHeight).clip(CircleShape)
            .background(if (on) colors.secondaryAccent else if (lit) colors.background.copy(alpha = 0.35f) else colors.main.copy(alpha = 0.22f))
            .clickable(onClick = onClick).padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val knob = SwitchHeight - 6.dp
        Box(
            Modifier.offset(x = if (on) SwitchWidth - 6.dp - knob else 0.dp).size(knob).clip(CircleShape)
                .background(if (on) colors.accent else colors.main),
        )
    }
}

private val SwitchWidth = 44.dp
private val SwitchHeight = 24.dp

/** A segmented button: one pill per choice in an outlined track, the [selected] one on the accent; a tap picks it. */
@Composable
private fun Segments(choices: List<String>, selected: Int, size: TextUnit, onPick: (Int) -> Unit) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(50)
    Row(Modifier.clip(shape).border(1.dp, colors.main.copy(alpha = 0.3f), shape).padding(2.dp)) {
        choices.forEachIndexed { i, label ->
            val lit = i == selected
            Box(
                Modifier.clip(shape).then(if (lit) Modifier.background(colors.accent, shape) else Modifier)
                    .clickable { onPick(i) }.padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                ValueText(label, size * 0.9f, if (lit) colors.onAccent else colors.listText)
            }
        }
    }
}

/** The info icon: a question mark in a circle, sized from the description; a tap shows the full text. */
@Composable
private fun InfoIcon(textSize: TextUnit, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    val disc = with(LocalDensity.current) { (textSize * 1.25f).toDp() }
    // The padding widens the tap target past the small disc.
    Box(Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(disc).border(1.5.dp, colors.hint, CircleShape), contentAlignment = Alignment.Center) {
            Text("?", color = colors.hint, fontFamily = LocalNxFont.current, fontWeight = FontWeight.Bold, fontSize = textSize * 0.8f, lineHeight = textSize * 0.8f)
        }
    }
}

/** The popup over the menu ([MenuState.popup]): the value picker or a row's full description. */
@Composable
internal fun MenuPopupView(popup: MenuPopup, onOption: (OptionEvent) -> Unit) {
    when (popup) {
        is MenuPopup.Picker -> OptionPicker(popup, onOption)
        is MenuPopup.Info -> {
            // The model counts UP/DOWN in scrollSeq; each new value is one step for the modal's scroll.
            val steps = remember { MutableSharedFlow<Int>(extraBufferCapacity = 8) }
            LaunchedEffect(popup.scrollSeq) { if (popup.scrollSeq > 0) steps.emit(popup.scrollDir) }
            NxModal(caption = popup.title, kind = ModalKind.INFO, onButton = { onOption(OptionEvent.ClosePopup) }, scrollSteps = steps) {
                Text(popup.text, color = LocalNxColors.current.listText, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal, fontSize = nxDialogBody(), lineHeight = nxDialogBody() * 1.35f)   // Regular, as the row's description
            }
        }
    }
}

/** A picker row's height. */
private val PickerRow = 44.dp

/**
 * The value picker: NxModal's frame (scrim, panel, card with the option's name) around every choice, the d-pad's
 * [MenuPopup.Picker.cursor] on the accent pill and the value set marked with a dot. A tap picks a value; a tap on the
 * scrim closes it. A lazy list, so a long one (an offset, a shader parameter) scrolls, the cursor kept in view.
 */
@Composable
private fun OptionPicker(picker: MenuPopup.Picker, onOption: (OptionEvent) -> Unit) {
    val colors = LocalNxColors.current
    val k = nxTextScale()   // Appearance → Text size
    val rowH = PickerRow * k
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (picker.cursor - 2).coerceAtLeast(0))
    NxKeepSelectedInView(list, picker.cursor, picker.choices.size, margin = 1)
    Box(
        Modifier.fillMaxSize().background(NxModalScrimColor).pointerInput(Unit) { detectTapGestures { onOption(OptionEvent.ClosePopup) } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().background(NxModalPanelColor, RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }   // taps inside stay inside
                .padding(8.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NxModalCardColor).padding(vertical = 12.dp)) {
                Text(
                    picker.title, color = colors.hint, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold,
                    fontSize = nxModalSmall() * k, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
                LazyColumn(
                    Modifier.fillMaxWidth().height(rowH * minOf(picker.choices.size, PICKER_ROWS)).nxScrollFade(list, height = rowH / 2, strong = true),
                    state = list,
                ) {
                    itemsIndexed(picker.choices) { i, label ->
                        PickerRowView(label, cursor = i == picker.cursor, current = i == picker.current, rowH) { onOption(OptionEvent.Pick(i)) }
                    }
                }
            }
        }
    }
}

/** Picker rows shown before it scrolls. */
private const val PICKER_ROWS = 7

@Composable
private fun PickerRowView(label: String, cursor: Boolean, current: Boolean, height: Dp, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(50)
    val text = if (cursor) colors.onAccent else colors.listText
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp, vertical = 2.dp).clip(shape)
            .then(if (cursor) Modifier.background(colors.accent, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, color = text, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = nxModalText(),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        // The value set: a filled dot; the others none.
        if (current) Box(Modifier.size(8.dp).clip(CircleShape).background(text))
    }
}
