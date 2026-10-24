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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow

/*
 * The app's touch settings rows (Settings' value pages, Emulator Settings, Game Settings), built as the in-game menu's
 * options rows are (InGameMenuOptions.kt): a bold name with its description under it, an info icon when the text says
 * more than the row shows, and a control by the values: a switch, a segmented button or "Value ›" opening a picker.
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
    val info: String = "",
    val detail: String = "",
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
)

/** The text the row's info popup shows: its [NxSettingRow.detail] when that says more than its line, else its info. */
val NxSettingRow.fullInfo: String get() = detail.takeIf { it.length > info.length } ?: info

/** The info popup says more than the row: a detail text longer than its line (the info icon shows, X opens it). */
val NxSettingRow.hasMoreInfo: Boolean get() = detail.isNotBlank() && detail.length > info.length

/** How a settings row shows and sets its value ([nxSettingControl]). */
enum class NxSettingControl {
    /** Two values that read as on/off: a switch. */
    SWITCH,
    /** Two to four short values: a segmented button under the text. */
    SEGMENTED,
    /** More or longer values: "Value ›", a tap opens the picker. */
    PICKER,
    /** No list (or a single value): the value only. */
    VALUE,
}

/** On/off word pairs a switch stands for (lower case, the "on" word first); Show / Hide too, as Layouts' rows. */
private val SWITCH_PAIRS = listOf("on" to "off", "show" to "hide", "enabled" to "disabled", "true" to "false", "yes" to "no")

/** A segmented button's most values, the longest label it takes and its labels' total length. */
private const val SEGMENT_MAX = 4
private const val SEGMENT_LABEL_MAX = 10
private const val SEGMENT_TOTAL_MAX = 24

/** The index of [choices]' "on" value when they are an on/off pair ([SWITCH_PAIRS]), else -1. */
fun nxSwitchOnIndex(choices: List<String>): Int {
    if (choices.size != 2) return -1
    val (a, b) = choices.map { it.trim().lowercase() }
    return SWITCH_PAIRS.firstNotNullOfOrNull { (on, off) ->
        when {
            a == on && b == off -> 0
            a == off && b == on -> 1
            else -> null
        }
    } ?: -1
}

/** The control for a row with [choices]: a switch, a segmented button, a picker, or the value alone. */
fun nxSettingControl(choices: List<String>): NxSettingControl = when {
    choices.size < 2 -> NxSettingControl.VALUE
    nxSwitchOnIndex(choices) >= 0 -> NxSettingControl.SWITCH
    choices.size <= SEGMENT_MAX && choices.all { it.length <= SEGMENT_LABEL_MAX } && choices.sumOf { it.length } <= SEGMENT_TOTAL_MAX ->
        NxSettingControl.SEGMENTED
    else -> NxSettingControl.PICKER
}

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

/** A description's most lines on its row; past them it ends in an ellipsis and the name gets the info icon. */
internal const val SETTING_DESC_LINES = 2

/** A description as a share of its name. */
private const val DESC_OF_LABEL = 0.9f   // one text-size step ([NxTextSizes.STEP]) over 0.72

/** The touch rows' sizes from a list row's pitch: the name, the description and its line, the value, the row, a segmented row's extra line. */
internal class NxSettingRowSizes(
    val label: TextUnit, val desc: TextUnit, val descLine: TextUnit, val value: TextUnit, val row: Dp, val segments: Dp,
    /** Whether a segmented row's choices fit on its right ([rememberSegmentsFitRight]); else they go under its text. */
    val segmentsRight: (List<String>) -> Boolean = { false },
    /** A row with no description: its name and the air alone. */
    val bare: Dp = row,
) {
    /** Row [r]'s segments go under its text: an enabled segmented row whose choices don't fit on its right. */
    fun segmentsBelow(r: NxSettingRow): Boolean = r.enabled && r.control == NxSettingControl.SEGMENTED && !segmentsRight(r.choices)

    fun heightOf(r: NxSettingRow): Dp = when {
        r.spacer -> bare * NX_OPTIONS_GAP
        r.section -> row * SECTION_OF_ROW
        else -> (if (r.info.isBlank()) bare else row) + if (segmentsBelow(r)) segments else 0.dp
    }
}

/** The room above and below a settings row's text (inside its plate, which keeps a 2 dp gap to its neighbours). */
private val SETTING_ROW_AIR = 12.dp

/** A group label's row as a share of a row. */
private const val SECTION_OF_ROW = 0.5f

@Composable
internal fun nxSettingRowSizes(itemHeight: Dp, rowWidth: Dp): NxSettingRowSizes {
    val listText = NxTextSizes.listLabel(itemHeight)
    val label = nxOptionsLabelSize(listText)
    val desc = label * DESC_OF_LABEL
    val descLine = desc * 1.25f
    // Name, two description lines and the row's air, every row the same height (the snap and fades as elsewhere).
    // The name, two description lines and [SETTING_ROW_AIR] above and below them, so a selected row's plate has room.
    val row = with(LocalDensity.current) { (label * 1.25f).toDp() + (descLine * SETTING_DESC_LINES).toDp() } + SETTING_ROW_AIR * 2
    val value = nxOptionsValueSize(listText)
    // The segments' line under a segmented row's text: the track (2 dp inset, a choice's 5 dp padding, its text) and a gap.
    val segments = with(LocalDensity.current) { (value * 0.9f).toDp() } + (2.dp + 5.dp) * 2 + 2.dp + SEGMENTS_GAP
    val bare = with(LocalDensity.current) { (label * 1.25f).toDp() } + SETTING_ROW_AIR * 2
    return NxSettingRowSizes(
        label, desc, descLine, value, maxOf(row, itemHeight), segments, rememberSegmentsFitRight(value, rowWidth - NxOptionsInnerPad * 2),
        maxOf(bare, itemHeight),
    )
}

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
) {
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
) {
    val itemHeight = nxOptionsRowHeight(NxListPitch, LocalNxOptionText.current)   // Appearance → Text size
    val listState = rememberLazyListState()
    val tapOpens = LocalNxTapOpens.current
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = NxOptionsSidePad).padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
        val sizes = nxSettingRowSizes(itemHeight, maxWidth)
        // A paragraph wraps to its own height, unknown here: such a list takes all the room given.
        // Summed in each row's own rounded px, as the list lays them out: a Dp sum can come out a px short, which would
        // leave the list scrollable by that px and its bottom fade showing over the last row.
        val density = LocalDensity.current
        val total = if (rows.any { it.paragraph }) maxHeight else with(density) { rows.sumOf { sizes.heightOf(it).roundToPx() }.toDp() }
        // Rows differ in height (a bare row, a group label, segments under the text): kept clear of the fade in px.
        NxKeepSelectedInView(listState, selectedIndex, rows.size, edge = with(density) { (sizes.row / 2).roundToPx() })
        LazyColumn(
            Modifier.fillMaxWidth().height(minOf(total, maxHeight)).nxScrollFade(listState, height = sizes.row / 2, strong = true),
            state = listState, flingBehavior = rememberNxRowSnapFling(listState),
        ) {
            itemsIndexed(rows) { i, row ->
                val selected = i == selectedIndex
                val default = { nxTap(i, selected, tapOpens(i), onSelect, onOpen) }
                NxSettingTouchRow(
                    row, selected, sizes, index = i,
                    onTap = if (preview) default else settingRowTap(row, i, onSelect, onEvent) ?: default,
                    onEvent = if (preview) null else onEvent,
                    onTruncated = { info, t -> if (selected) onTruncated(info, t) },
                )
            }
        }
    }
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
 * One touch settings row: the name (bold) with the info icon when the description is cut off or has a fuller detail
 * ([hasMoreInfo]), the description under it, and on the right the control ([NxSettingControl]), a value, or a page's
 * `›`; a segmented row's segments sit under the text at its start. Selected: a soft rounded plate behind it. A group
 * label is a grey line, a spacer empty, a paragraph wrapping grey text. The description and a paragraph are MiSans Regular
 * ([LocalNxFontWeights]), the rest Semibold. [onEvent] null: the controls take no taps (a preview). [onTruncated] hears
 * whether the description fits.
 */
@Composable
private fun NxSettingTouchRow(
    row: NxSettingRow, selected: Boolean, sizes: NxSettingRowSizes, index: Int,
    onTap: () -> Unit, onEvent: ((NxSettingEvent) -> Unit)?, onTruncated: (String, Boolean) -> Unit,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    if (row.spacer) { Spacer(Modifier.fillMaxWidth().height(sizes.heightOf(row))); return }
    if (row.section) {
        Box(Modifier.fillMaxWidth().height(sizes.heightOf(row)).padding(horizontal = NxOptionsInnerPad), contentAlignment = Alignment.BottomStart) {
            Text(row.label, color = colors.hint, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = sizes.desc, lineHeight = sizes.desc, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val shape = RoundedCornerShape(14.dp)
    val info = row.info
    var truncated by remember(info) { mutableStateOf(false) }
    LaunchedEffect(selected, truncated, info) { if (selected && info.isNotBlank()) onTruncated(info, truncated) }
    val below = sizes.segmentsBelow(row)
    val choose: ((Int) -> Unit)? = if (row.enabled) onEvent?.let { e -> { k: Int -> e(NxSettingEvent.Choose(index, k)) } } else null
    Column(
        Modifier.fillMaxWidth().height(sizes.heightOf(row)).padding(vertical = 2.dp).clip(shape)
            .then(if (selected) Modifier.background(colors.accent.copy(alpha = SOFT_SELECTION_ALPHA), shape) else Modifier)
            .clickable(onClick = onTap).padding(horizontal = NxOptionsInnerPad)
            .alpha(if (row.enabled) 1f else NX_DISABLED_ALPHA),
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
                    if (truncated || row.hasMoreInfo) SettingInfoIcon(sizes.desc, onEvent?.let { e -> { e(NxSettingEvent.Info(index)) } })
                }
                if (info.isNotBlank()) {
                    Text(
                        info, color = if (row.warning) colors.warning else colors.hint, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal,
                        fontSize = sizes.desc, lineHeight = sizes.descLine, maxLines = SETTING_DESC_LINES, overflow = TextOverflow.Ellipsis,
                        onTextLayout = { r -> truncated = r.hasVisualOverflow || (r.lineCount > 0 && r.isLineEllipsized(r.lineCount - 1)) },
                    )
                }
            }
            if (!below) {
                Spacer(Modifier.width(12.dp))
                SettingControlView(row, sizes, choose, onEvent?.let { e -> { e(NxSettingEvent.OpenPicker(index)) } })
            }
        }
        if (below) {
            Spacer(Modifier.height(SEGMENTS_GAP))
            SettingSegments(row.choices, row.index, sizes.value, choose)
        }
    }
}

/** The gap between a segmented row's description and its segments. */
private val SEGMENTS_GAP = 6.dp

/** The selected row's plate: the accent, faint, so the row's own colours read on it. */
private const val SOFT_SELECTION_ALPHA = 0.16f

/** The right side of a row: its control by [NxSettingControl], else its value and a page's `›`. */
@Composable
private fun SettingControlView(row: NxSettingRow, sizes: NxSettingRowSizes, choose: ((Int) -> Unit)?, openPicker: (() -> Unit)?) {
    val colors = LocalNxColors.current
    val current = row.choices.getOrNull(row.index) ?: row.value
    when {
        row.enabled && row.control == NxSettingControl.SWITCH ->
            SettingSwitch(on = row.index == nxSwitchOnIndex(row.choices), onClick = choose?.let { c -> { c(row.flipped) } })
        // Short choices that fit on the right ([NxSettingRowSizes.segmentsRight]); longer ones draw under the text.
        row.enabled && row.control == NxSettingControl.SEGMENTED -> SettingSegments(row.choices, row.index, sizes.value, choose)
        row.enabled && row.control == NxSettingControl.PICKER -> Row(
            Modifier.clip(RoundedCornerShape(8.dp)).then(if (openPicker != null) Modifier.clickable(onClick = openPicker) else Modifier).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingValueText(current.orEmpty(), sizes.value, colors.listText, Modifier.widthIn(max = 180.dp))
            Spacer(Modifier.width(6.dp))
            SettingValueText("›", sizes.value * 1.2f, colors.hint)
        }
        row.button != null -> Box(
            Modifier.clip(RoundedCornerShape(50)).border(1.dp, colors.main.copy(alpha = 0.3f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) { SettingValueText(row.button, sizes.value, colors.listText) }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (current != null) SettingValueText(current, sizes.value, colors.listText, Modifier.widthIn(max = 180.dp))
            if (row.opens) {
                if (current != null) Spacer(Modifier.width(6.dp))
                SettingValueText("›", if (current != null) sizes.value * 1.2f else sizes.label, if (current != null) colors.hint else colors.listText)
            }
        }
    }
}

@Composable
private fun SettingValueText(text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
    Text(text, color = color, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** An on/off switch: on, the Secondary accent track with the accent knob right; off, a dim track with the knob left. */
@Composable
private fun SettingSwitch(on: Boolean, onClick: (() -> Unit)?) {
    val colors = LocalNxColors.current
    Box(
        Modifier.size(SwitchWidth, SwitchHeight).clip(CircleShape)
            .background(if (on) colors.secondaryAccent else colors.main.copy(alpha = 0.22f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(3.dp),
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
private fun SettingSegments(choices: List<String>, selected: Int, size: TextUnit, onPick: ((Int) -> Unit)?) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(50)
    Row(Modifier.clip(shape).border(1.dp, colors.main.copy(alpha = 0.3f), shape).padding(2.dp)) {
        choices.forEachIndexed { i, label ->
            val lit = i == selected
            Box(
                Modifier.clip(shape).then(if (lit) Modifier.background(colors.accent, shape) else Modifier)
                    .then(if (onPick != null) Modifier.clickable { onPick(i) } else Modifier).padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                SettingValueText(label, size * 0.9f, if (lit) colors.onAccent else colors.listText)
            }
        }
    }
}

/** The info icon: a question mark in a circle, sized from the description; a tap shows the full text. */
@Composable
private fun SettingInfoIcon(textSize: TextUnit, onClick: (() -> Unit)?) {
    val colors = LocalNxColors.current
    val disc = with(LocalDensity.current) { (textSize * 1.25f).toDp() }
    // The padding widens the tap target past the small disc.
    Box(
        Modifier.clip(CircleShape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(disc).border(1.5.dp, colors.hint, CircleShape), contentAlignment = Alignment.Center) {
            Text("?", color = colors.hint, fontFamily = LocalNxFont.current, fontWeight = FontWeight.Bold, fontSize = textSize * 0.8f, lineHeight = textSize * 0.8f)
        }
    }
}

/** A row's full description over the page ([NxModal] INFO): A, B, X or a tap close it; [scrollSeq] counts UP/DOWN steps ([scrollDir]). */
@Composable
fun NxSettingInfoPopup(title: String, text: String, scrollSeq: Int, scrollDir: Int, onClose: () -> Unit) {
    val steps = remember { MutableSharedFlow<Int>(extraBufferCapacity = 8) }
    LaunchedEffect(scrollSeq) { if (scrollSeq > 0) steps.emit(scrollDir) }
    NxModal(caption = title, kind = ModalKind.INFO, onButton = { onClose() }, scrollSteps = steps) {
        Text(text, color = LocalNxColors.current.listText, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal, fontSize = nxDialogBody(), lineHeight = nxDialogBody() * 1.35f)   // Regular, as the row's description
    }
}

/** A picker row's height, and the rows shown before it scrolls. */
private val PickerRow = 44.dp
private const val PICKER_ROWS = 7

/**
 * The value picker: NxModal's frame around every choice, the d-pad's [cursor] on the accent pill and the value set
 * ([current]) marked with a dot. A tap picks a value ([onPick]); a tap on the scrim closes it ([onDismiss]). A long
 * list scrolls, the cursor kept in view.
 */
@Composable
fun NxSettingPicker(title: String, choices: List<String>, current: Int, cursor: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalNxColors.current
    val k = nxTextScale()   // Appearance → Text size
    val rowH = PickerRow * k
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (cursor - 2).coerceAtLeast(0))
    NxKeepSelectedInView(list, cursor, choices.size, margin = 1)
    Box(
        Modifier.fillMaxSize().background(NxModalScrimColor).pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().background(NxModalPanelColor, RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }   // taps inside stay inside
                .padding(8.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NxModalCardColor).padding(vertical = 12.dp)) {
                Text(
                    title, color = colors.hint, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold,
                    fontSize = nxModalSmall(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
                LazyColumn(
                    Modifier.fillMaxWidth().height(rowH * minOf(choices.size, PICKER_ROWS)).nxScrollFade(list, height = rowH / 2, strong = true),
                    state = list,
                ) {
                    itemsIndexed(choices) { i, label ->
                        SettingPickerRow(label, cursor = i == cursor, current = i == current, rowH) { onPick(i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingPickerRow(label: String, cursor: Boolean, current: Boolean, height: Dp, onClick: () -> Unit) {
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
        // The value set: a filled dot.
        if (current) Box(Modifier.size(8.dp).clip(CircleShape).background(text))
    }
}
