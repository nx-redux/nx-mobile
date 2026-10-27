package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.nxredux.mobile.ui.LocalSettingsBlock
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * The touch rows both settings lists draw: the app's Settings rows (NxSettingsRow.kt) and the in-game menu's options
 * rows (InGameMenuOptions.kt). A bold name with its description under it, an info icon when the text says more than the
 * row shows, and a control by the values ([nxSettingControl]): a switch, a segmented button or "Value ›" opening the
 * picker ([NxSettingPicker]); the info popup ([NxSettingInfoPopup]) shows the full text.
 */

/** A row with a description line [info] and a fuller [detail] text the info popup shows when [info] is a short line of it. */
interface NxDescribedRow {
    val info: String
    val detail: String
}

/** The text the row's info popup shows: its [NxDescribedRow.detail] when that says more than its line, else its info. */
val NxDescribedRow.fullInfo: String get() = detail.takeIf { it.length > info.length } ?: info

/** The info popup says more than the row: a detail text longer than its line (the info icon shows, X opens it). */
val NxDescribedRow.hasMoreInfo: Boolean get() = detail.isNotBlank() && detail.length > info.length

/** How a touch row shows and sets its value ([nxSettingControl]). */
enum class NxSettingControl {
    /** Two values that read as on/off: a switch. */
    SWITCH,
    /** Two to four short values: a segmented button. */
    SEGMENTED,
    /** More or longer values: "Value ›", a tap opens the picker. */
    PICKER,
    /** No list (or a single value): the value only (in-game, LEFT/RIGHT still step it). */
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

/** A description's most lines on its row; past them it ends in an ellipsis and the name gets the info icon. */
internal const val SETTING_DESC_LINES = 2

/**
 * The touch rows' sizes from a list row's pitch: the name, the description and its line, the value, a two-line row
 * ([row]: the fades' and keep-in-view's measure), a segmented row's extra line. [labelLineDp] and [descLineDp]: the
 * name's and a description line's height.
 */
internal class NxTouchRowSizes(
    val label: TextUnit, val desc: TextUnit, val descLine: TextUnit, val value: TextUnit, val row: Dp, val segments: Dp,
    /** Whether a segmented row's choices fit on its right ([rememberSegmentsFitRight]); else they go under its text. */
    val segmentsRight: (String, List<String>) -> Boolean = { _, _ -> false },
    /** A list gap's measure (an options gap, a spacer): a bare row at least the list's pitch. */
    val bare: Dp = row,
    val labelLineDp: Dp = 0.dp, val descLineDp: Dp = 0.dp,
    /** Whether a segmented row's choices fit on a line of their own ([rememberSegmentsFitLine]). */
    val segmentsFitLine: (List<String>) -> Boolean = { true },
) {
    /**
     * A touch row's height as it lays out: its text (the name's line and its description's [descLines] lines, 0 to
     * [SETTING_DESC_LINES]) or a taller [control] beside it, [TOUCH_ROW_AIR] above and below whatever it has; segments
     * under the text add their line ([segments]). Every row the same air: a bare row is no taller for the list's pitch,
     * a one-line description no taller for a second line it doesn't have.
     */
    fun height(descLines: Int, segmentsBelow: Boolean, control: Dp = 0.dp): Dp =
        maxOf(labelLineDp + descLineDp * descLines.coerceIn(0, SETTING_DESC_LINES), control) + TOUCH_ROW_AIR * 2 +
            if (segmentsBelow) segments else 0.dp
}

/** The room above and below a touch row's text, the same on every row (its 2 dp gap to its neighbours, outside its plate, included). */
internal val TOUCH_ROW_AIR = 12.dp

/** A touch row's gap to its neighbours, outside its plate (part of [TOUCH_ROW_AIR]). */
private val TOUCH_ROW_GAP = 2.dp

/** The touch rows' sizes for list rows [itemHeight] high and [rowWidth] wide, the description [descOfLabel] of the name. */
@Composable
internal fun nxTouchRowSizes(itemHeight: Dp, rowWidth: Dp, descOfLabel: Float): NxTouchRowSizes {
    val listText = nxListLabel()
    val label = nxOptionsLabelSize(listText)
    val desc = label * descOfLabel
    val descLine = desc * 1.25f
    // The name, two description lines and [TOUCH_ROW_AIR] above and below them: the fades' and keep-in-view's measure.
    val labelLine = with(LocalDensity.current) { (label * 1.25f).toDp() }
    val descLineDp = with(LocalDensity.current) { descLine.toDp() }
    val row = labelLine + descLineDp * SETTING_DESC_LINES + TOUCH_ROW_AIR * 2
    val value = nxOptionsValueSize(listText)
    // The segments' line under a segmented row's text: the track (2 dp inset, a choice's 5 dp padding, its text) and a gap.
    val segments = with(LocalDensity.current) { (value * 0.9f).toDp() } + (2.dp + 5.dp) * 2 + 2.dp + SEGMENTS_GAP
    val bare = with(LocalDensity.current) { (label * 1.25f).toDp() } + TOUCH_ROW_AIR * 2
    return NxTouchRowSizes(
        label, desc, descLine, value, maxOf(row, itemHeight), segments, rememberSegmentsFitRight(value, label, rowWidth - NxOptionsInnerPad * 2),
        maxOf(bare, itemHeight), labelLine, descLineDp, rememberSegmentsFitLine(value, rowWidth - NxOptionsInnerPad * 2),
    )
}

/** The gap between a segmented row's description and its segments. */
private val SEGMENTS_GAP = 6.dp

/** Whether a touch row draws its info icon: only while [selected], and only when its line is [truncated] or [moreInfo]. */
internal fun nxInfoIconShown(selected: Boolean, truncated: Boolean, moreInfo: Boolean): Boolean = selected && (truncated || moreInfo)

/** The selected row's plate: the accent, faint, so the row's own colours read on it. */
private const val SOFT_SELECTION_ALPHA = 0.16f

/**
 * One touch row, as tall as its text ([NxTouchRowSizes.height]: the name's line, the description's own lines, the same
 * [TOUCH_ROW_AIR] above and below on every row): the [label] (bold) with the info icon while it is [selected] and the [info] line is cut
 * off or [moreInfo] says there is more ([nxInfoIconShown]), the description under it (at most [SETTING_DESC_LINES], the warning colour when [warning]), and the
 * [control] on the right, or [below] under the text (a segmented row's segments that don't fit on the right): at its
 * start, right-aligned in the Settings rows block ([LocalSettingsBlock]). Selected: a soft rounded plate behind it, so the control stays readable; not [enabled]: dimmed. [onInfo] null:
 * the info icon takes no taps. [onLongPress]: a long press anywhere on the row's own area (its name and description);
 * null, none (a long press is a tap). [onTruncated] hears whether the description fits while the row is [selected];
 * [onFits] whether it fits, selected or not.
 */
@Composable
internal fun NxTouchRow(
    label: String, info: String, moreInfo: Boolean, selected: Boolean, sizes: NxTouchRowSizes,
    onTap: () -> Unit, onInfo: (() -> Unit)?, onTruncated: (String, Boolean) -> Unit,
    enabled: Boolean = true, warning: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onFits: ((Boolean) -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(14.dp)
    var truncated by remember(info) { mutableStateOf(false) }
    LaunchedEffect(selected, truncated, info) { if (selected && info.isNotBlank()) onTruncated(info, truncated) }
    LaunchedEffect(truncated, info) { onFits?.invoke(!truncated) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = TOUCH_ROW_GAP)
            // The selected row's plate, where the help note leaves it lit ([NxSettingInfoPopup]).
            .then(if (selected) Modifier.onGloballyPositioned { NxHelpAnchor.row = NxHelpRow(label, it.boundsInWindow(), sizes.desc) } else Modifier)
            .clip(shape)
            .then(if (selected) Modifier.background(colors.accent.copy(alpha = SOFT_SELECTION_ALPHA), shape) else Modifier)   // role: a touch row's soft selection
            .nxTapOrHold(onTap, onLongPress).padding(horizontal = NxOptionsInnerPad, vertical = TOUCH_ROW_AIR - TOUCH_ROW_GAP)
            .alpha(if (enabled) 1f else NX_DISABLED_ALPHA),   // role: a disabled row
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                // The name's line exactly (the info icon, a touch taller, overflows it rather than growing the row when it shows).
                Row(Modifier.height(sizes.labelLineDp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label, color = colors.listText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.Bold,
                        fontSize = sizes.label, lineHeight = sizes.label * 1.25f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Cut off, or a fuller text behind it: the popup shows it. Only on the selected row, so a page of long
                    // descriptions isn't a column of icons.
                    if (nxInfoIconShown(selected, truncated, moreInfo)) NxInfoIcon(sizes.desc, onInfo)
                }
                if (info.isNotBlank()) {
                    Text(
                        info, color = if (warning) colors.warning else colors.hint, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal,
                        fontSize = sizes.desc, lineHeight = sizes.descLine, maxLines = SETTING_DESC_LINES, overflow = TextOverflow.Ellipsis,
                        onTextLayout = { r -> truncated = r.hasVisualOverflow || (r.lineCount > 0 && r.isLineEllipsized(r.lineCount - 1)) },
                    )
                }
            }
            if (below == null) {
                Spacer(Modifier.width(12.dp))
                control()
            }
        }
        if (below != null) {
            Spacer(Modifier.height(SEGMENTS_GAP))
            // In the Settings rows block, right-aligned under the label (the pane never widens for it); elsewhere at the text's start.
            Box(Modifier.fillMaxWidth(), contentAlignment = if (LocalSettingsBlock.current != null) Alignment.CenterEnd else Alignment.CenterStart) { below() }
        }
    }
}

/** A tap ([onTap], null none) and, when given, a long press ([onLongPress]) on this element. */
internal fun Modifier.nxTapOrHold(onTap: (() -> Unit)?, onLongPress: (() -> Unit)?): Modifier = when {
    onLongPress != null -> combinedClickable(onClick = onTap ?: {}, onLongClick = onLongPress)
    onTap != null -> clickable(onClick = onTap)
    else -> this
}

/** A touch row's value text: one line, Semibold. */
@Composable
internal fun NxValueText(text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
    Text(text, color = color, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** A picker row's control: the current value and a `›`; a tap ([onClick], null none) opens the picker, a long press is [onLongPress]. */
@Composable
internal fun NxPickerValue(text: String, size: TextUnit, onLongPress: (() -> Unit)? = null, onClick: (() -> Unit)?) {
    val colors = LocalNxColors.current
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).nxTapOrHold(onClick, onLongPress).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NxValueText(text, size, colors.listText, Modifier.widthIn(max = 180.dp))
        Spacer(Modifier.width(6.dp))
        NxValueText("›", size * 1.2f, colors.hint)
    }
}

/**
 * An on/off switch: on, the Secondary accent track with the accent knob right; off, a dim track with the knob left (on
 * a [lit] row, the accent pill, a dark track, as the dim one vanishes on it). [onClick] null: it takes no taps. The touch
 * rows' and the game panel's Cheats rows'.
 */
@Composable
internal fun NxSwitch(on: Boolean, onClick: (() -> Unit)?, lit: Boolean = false, onLongPress: (() -> Unit)? = null) {
    val colors = LocalNxColors.current
    Box(
        Modifier.size(SwitchWidth, SwitchHeight).clip(CircleShape)
            .background(if (on) colors.secondaryAccent else if (lit) colors.background.copy(alpha = 0.35f) else colors.main.copy(alpha = 0.22f))   // role: a switch's off track, lit and unlit
            .nxTapOrHold(onClick, onLongPress).padding(3.dp),
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

/** A segmented button: one pill per choice in an outlined track, the [selected] one on the accent; a tap picks it ([onPick], null none), a long press is [onLongPress]. */
@Composable
internal fun NxSegments(choices: List<String>, selected: Int, size: TextUnit, onLongPress: (() -> Unit)? = null, onPick: ((Int) -> Unit)?) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(50)
    Row(Modifier.clip(shape).border(1.dp, colors.inkRing, shape).padding(2.dp)) {
        choices.forEachIndexed { i, label ->
            val lit = i == selected
            Box(
                Modifier.clip(shape).then(if (lit) Modifier.background(colors.accent, shape) else Modifier)
                    .nxTapOrHold(onPick?.let { p -> { p(i) } }, onLongPress).padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                NxValueText(label, size * 0.9f, if (lit) colors.onAccent else colors.listText)
            }
        }
    }
}

/** The info icon: a question mark in a circle, sized from the description; a tap ([onClick], null none) shows the full text. */
@Composable
private fun NxInfoIcon(textSize: TextUnit, onClick: (() -> Unit)?) {
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

/** A picker row's height, and the rows shown before it scrolls. */
private val PickerRow = 44.dp
private const val PICKER_ROWS = 7

/**
 * The value picker: NxModal's frame (scrim, panel, card with the row's name) around every choice, the d-pad's [cursor]
 * on the accent pill and the value set ([current]) marked with a dot. A tap picks a value ([onPick]); a tap on the scrim
 * closes it ([onDismiss]). A long list (an offset, a shader parameter) scrolls, the cursor kept in view.
 */
@Composable
fun NxSettingPicker(title: String, choices: List<String>, current: Int, cursor: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalNxColors.current
    val k = nxTextScale()   // Appearance → Text size
    val rowH = PickerRow * k
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (cursor - 2).coerceAtLeast(0))
    NxKeepSelectedInView(list, cursor, choices.size, margin = 1)
    Box(
        Modifier.fillMaxSize().background(LocalNxColors.current.scrimModal).pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().background(LocalNxColors.current.modalPanel, RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }   // taps inside stay inside
                .padding(8.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LocalNxColors.current.modalCard).padding(vertical = 12.dp)) {
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
                        PickerRowView(label, cursor = i == cursor, current = i == current, rowH) { onPick(i) }
                    }
                }
            }
        }
    }
}

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
        // The value set: a filled dot.
        if (current) Box(Modifier.size(8.dp).clip(CircleShape).background(text))
    }
}
