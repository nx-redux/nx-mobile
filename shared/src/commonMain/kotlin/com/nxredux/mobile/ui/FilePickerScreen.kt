package com.nxredux.mobile.ui

import com.nxredux.mobile.shared.res.nav_icon_file
import com.nxredux.mobile.shared.res.nav_icon_folder
import com.nxredux.mobile.shared.res.nav_icon_new_folder
import com.nxredux.mobile.shared.res.nav_icon_up
import com.nxredux.mobile.shared.res.Res
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nxredux.mobile.ui.nx.nxListInset
import com.nxredux.mobile.ui.nx.LocalNxHintBarStart
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDetailListBody
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxTextPrompt
import com.nxredux.mobile.ui.nx.nxPageGutter

/** The list-style picker's hints for the [selected] row: New folder only inside a folder and never when picking a zip. */
internal fun filePickerHints(s: Screen.FilePicker, selected: Int = -1): List<Hint> = buildList {
    // The d-pad's sideways jump between the folder's entries and the actions under them: the way it goes from here.
    val actions = s.rows.indexOfFirst { it is PickerRow.Action || it == PickerRow.NewFolder }
    if (actions >= 0) add(if (selected >= actions) Hint(NxButton.LEFT, "Folders") else Hint(NxButton.RIGHT, "Actions"))
    add(Hint(NxButton.A, "Select"))
    add(Hint(NxButton.B, "Back"))
    // Touch has the Create new folder row; X stays for a pad.
    if (s.dir != null && s.mode != FilePickMode.ImportZip) add(Hint(NxButton.X, "New folder"))
}

/** The picker's rows as list rows: ".." with the up arrow, folders and volumes (they open like folders) with the folder icon, files with the file icon and dimmed, "Nothing here" dimmed. */
internal fun pickerListRows(rows: List<PickerRow>): List<NxRow> = rows.map {
    val icon = when (it) {
        PickerRow.NewFolder -> Res.drawable.nav_icon_new_folder
        PickerRow.Up -> Res.drawable.nav_icon_up
        is PickerRow.Folder, is PickerRow.Volume -> Res.drawable.nav_icon_folder
        is PickerRow.File -> Res.drawable.nav_icon_file
        else -> null
    }
    NxRow(it.label, enabled = it != PickerRow.Empty && it !is PickerRow.File, icon = icon)   // a file: shown dimmed, never picked
}

/** How many of [rows] are actions (Use this folder / Save here, Create new folder), pinned under the scrolling list. */
internal fun pickerPinnedRows(rows: List<PickerRow>): Int = rows.takeLastWhile { it is PickerRow.Action || it == PickerRow.NewFolder }.size

/**
 * The list-style picker, the app's usual list ([NxDetailListBody]) titled with the folder's path from its volume;
 * New folder's prompt ([NxTextPrompt]) and Export's "Replace …?" over it. Buttons go to AppState ([onHint]).
 */
@Composable
fun FilePickerScreen(
    screen: Screen.FilePicker, selectedIndex: Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit,
    onHint: (NxButton) -> Unit, onDraft: (String) -> Unit, modifier: Modifier = Modifier,
) {
    // The page's gutter, as the other pages': the pills on it, the title, rows and hint bar's text the pill's inset in.
    val gutter = nxPageGutter()
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNxHintBarStart provides nxListInset()) { NxDetailListBody(
            title = screen.title, details = listOfNotNull(screen.status), headline = false,   // a quiet line under the title
            rows = pickerListRows(screen.rows),
            selectedIndex = selectedIndex, hints = filePickerHints(screen, selectedIndex),
            // A file is shown, not picked: a tap on it neither selects nor opens.
            onSelect = { if (screen.rows.getOrNull(it) !is PickerRow.File) onSelect(it) },
            onOpen = { if (screen.rows.getOrNull(it) !is PickerRow.File) onOpen(it) }, onHint = onHint, rowsAtTop = true, pinnedBottom = pickerPinnedRows(screen.rows),
            actionsOutlined = false,   // the actions as plain rows, no ring; one column in either orientation
            modifier = Modifier.padding(horizontal = gutter),
        ) }
        screen.prompt?.let { NxTextPrompt("New folder", it, onDraft, onConfirm = { onHint(NxButton.A) }, onCancel = { onHint(NxButton.B) }) }
        screen.replace?.let { target ->
            NxDialog(
                "Replace ${target.substringAfterLast('/')}?", "The file in this folder is replaced by the new export.",
                onConfirm = { onHint(NxButton.A) }, confirmLabel = "Replace", onCancel = { onHint(NxButton.B) },
            )
        }
    }
}
