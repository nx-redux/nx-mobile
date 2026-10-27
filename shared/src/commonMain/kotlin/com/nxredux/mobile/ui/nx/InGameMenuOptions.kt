package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** An options row's description as a share of its name. */
private const val DESC_OF_LABEL = 0.72f

/** Row [r]'s segments go under its text: a segmented option whose choices don't fit on its right. */
private fun NxTouchRowSizes.segmentsBelow(r: MenuRow): Boolean = r is MenuRow.Option && r.control == NxSettingControl.SEGMENTED && !segmentsRight(r.label, r.choices)

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
            val sizes = nxTouchRowSizes(itemHeight, maxWidth, DESC_OF_LABEL)
            val count = page.rows.size
            // Rows differ in height (a bare row, segments under the text): kept clear of the fade in px.
            NxKeepSelectedInView(listState, page.selected, count, edge = with(LocalDensity.current) { (sizes.row / 2).roundToPx() })
            Column(Modifier.fillMaxWidth()) {
                // The rows as tall as they lay out ([NxTouchRowSizes.height]), the list no taller than them nor the room.
                // The page's intro: plain text above the rows, as Settings' paragraphs; never selected or tapped.
                if (page.intro.isNotEmpty()) Text(
                    page.intro, color = colors.hint, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal, fontSize = sizes.desc, lineHeight = sizes.descLine,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = NxOptionsInnerPad, vertical = 6.dp),
                )
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false).nxScrollFade(listState, height = sizes.row / 2, strong = true),
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
}

/**
 * What a tap on options row [index] does, when not the lists' tap ([nxTap], null): a switch flips, a picker opens, a
 * segmented or value-only row is selected (its segments take their own taps). A visual setting keeps the lists' tap,
 * which enters adjust mode as A does; its control still acts on its own.
 */
private fun optionRowTap(row: MenuRow, index: Int, onSelect: (Int) -> Unit, onOption: (OptionEvent) -> Unit): (() -> Unit)? {
    if (row !is MenuRow.Option || row.visual) return null
    return when (row.control) {
        NxSettingControl.SWITCH -> { { onOption(OptionEvent.Choose(index, 1 - row.index.coerceIn(0, 1))) } }
        NxSettingControl.PICKER -> { { onOption(OptionEvent.OpenPicker(index)) } }
        NxSettingControl.SEGMENTED, NxSettingControl.VALUE -> { { onSelect(index) } }
    }
}

/**
 * One touch options row ([NxTouchRow]): the name with the info icon when the description is cut off or has a fuller
 * detail ([hasMoreInfo]), the description under it, and on the right the option's control ([NxSettingControl]) or a
 * submenu's `›`; a segmented option's segments that don't fit there sit under the text at its start. [onTruncated]
 * hears whether the description fits.
 */
@Composable
private fun OptionTouchRow(
    row: MenuRow, selected: Boolean, sizes: NxTouchRowSizes,
    onTap: () -> Unit, onOption: (OptionEvent) -> Unit, index: Int,
    onTruncated: (String, Boolean) -> Unit,
) {
    NxTouchRow(
        row.label, row.info, row.hasMoreInfo, selected, sizes,
        onTap = onTap, onInfo = { onOption(OptionEvent.Info(index)) }, onTruncated = onTruncated,
        below = if (row is MenuRow.Option && sizes.segmentsBelow(row)) {
            { NxSegments(row.choices, row.index, sizes.value) { onOption(OptionEvent.Choose(index, it)) } }
        } else null,
    ) {
        when (row) {
            is MenuRow.Option -> OptionControlView(row, index, sizes.value, onOption)
            is MenuRow.Submenu, is MenuRow.Action -> NxValueText("›", sizes.label, LocalNxColors.current.listText)
            is MenuRow.Slot -> Unit
        }
    }
}

/** The control on an options row's right, by its [NxSettingControl]. */
@Composable
private fun OptionControlView(row: MenuRow.Option, index: Int, valueSize: TextUnit, onOption: (OptionEvent) -> Unit) {
    when (row.control) {
        NxSettingControl.SWITCH -> NxSwitch(
            on = row.index == nxSwitchOnIndex(row.choices),
            // Its own tap (a visual row's body enters adjust mode instead).
            onClick = { onOption(OptionEvent.Choose(index, 1 - row.index.coerceIn(0, 1))) },
        )
        NxSettingControl.SEGMENTED -> NxSegments(row.choices, row.index, valueSize) { onOption(OptionEvent.Choose(index, it)) }
        NxSettingControl.PICKER -> NxPickerValue(row.valueLabel, valueSize) { onOption(OptionEvent.OpenPicker(index)) }
        NxSettingControl.VALUE -> NxValueText(row.valueLabel, valueSize, LocalNxColors.current.listText, Modifier.widthIn(max = 180.dp))
    }
}

/** The popup over the menu ([MenuState.popup], null none): the value picker or a row's help note (which fades out). */
@Composable
internal fun MenuPopupView(popup: MenuPopup?, onOption: (OptionEvent) -> Unit) {
    if (popup is MenuPopup.Picker) NxSettingPicker(
        popup.title, popup.choices, popup.current, popup.cursor,
        onPick = { onOption(OptionEvent.Pick(it)) }, onDismiss = { onOption(OptionEvent.ClosePopup) },
    )
    NxSettingInfoPopup((popup as? MenuPopup.Info)?.let { NxHelpText(it.title, it.text, it.scrollSeq, it.scrollDir) }, onClose = { onOption(OptionEvent.ClosePopup) })
}
