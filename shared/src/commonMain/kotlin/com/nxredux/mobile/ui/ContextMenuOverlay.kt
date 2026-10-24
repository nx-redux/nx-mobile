package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.nxModalText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.ModalKind
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxList
import com.nxredux.mobile.ui.nx.NxModal
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxTextPrompt

/**
 * NX Redux's context menu in the shared modal frame: the game's name (or the submenu's title) as caption, one row per action
 * (or per choice once a submenu is open), with no hints (A selects, B goes back). A prompt (Rename,
 * New Collection, a collection's Rename or Delete confirm) replaces it.
 */
@Composable
fun ContextMenuOverlay(menu: ContextMenu, onButton: (NxButton) -> Unit, onSelect: (Int) -> Unit, onOpen: () -> Unit, onDraft: (String) -> Unit, onColumns: (Int) -> Unit = {}) {
    when (val p = menu.prompt) {
        is MenuPrompt.Rename -> { NxTextPrompt("Rename", p.draft, onDraft, onConfirm = { onButton(NxButton.A) }, onCancel = { onButton(NxButton.B) }); return }
        is MenuPrompt.RenameCollection -> { NxTextPrompt("Rename collection", p.draft, onDraft, onConfirm = { onButton(NxButton.A) }, onCancel = { onButton(NxButton.B) }); return }
        is MenuPrompt.DeleteCollection -> {
            NxDialog(
                "Delete collection", "Delete '${p.name}'? The games stay in your library.",
                onConfirm = { onButton(NxButton.A) }, confirmLabel = "Delete", onCancel = { onButton(NxButton.B) },
            )
            return
        }
        is MenuPrompt.NewCollection -> { NxTextPrompt("New collection name", p.draft, onDraft, onConfirm = { onButton(NxButton.A) }, onCancel = { onButton(NxButton.B) }); return }
        null -> Unit
    }
    val sub = menu.sub
    val rows = sub?.labels?.mapIndexed { i, l -> NxRow(l, secondary = if (i == sub.current) "•" else null) } ?: menu.items.map { NxRow(it.label) }
    val selected = sub?.index ?: menu.index
    val rowHeight = 28.dp * nxTextScale()   // the rows grow with their text (Appearance → Text size)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = menuColumns(rows.size, maxWidth >= MENU_TWO_COLUMN_MIN_SCREEN)
        LaunchedEffect(columns) { onColumns(columns) }
        DisposableEffect(Unit) { onDispose { onColumns(1) } }
        NxModal(caption = sub?.title ?: menu.caption, kind = ModalKind.MENU, onButton = onButton, maxWidth = if (columns == 2) 640.dp else 420.dp) {
            if (columns == 1) {
                NxList(rows, selected, onSelect = onSelect, onOpen = { onOpen() }, modifier = Modifier.height(rowHeight * minOf(rows.size, MODAL_ROWS)), rowHeightOverride = rowHeight, pillPaddingOverride = 12.dp, textSizeOverride = nxModalText())
            } else {
                // Column-major and balanced ([menuColumnRows]: the left holds the odd one); only the column holding the
                // selection lights a row.
                val perColumn = menuColumnRows(rows.size, columns)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MENU_COLUMN_GAP)) {
                    rows.chunked(perColumn).forEachIndexed { c, part ->
                        val offset = c * perColumn
                        val local = (selected - offset).takeIf { it in part.indices } ?: -1
                        // keyed on focus: NxPillGlide would glide to/from -1 through visible rows; a fresh list puts the pill in place (none when unfocused).
                        key(local >= 0) {
                            NxList(part, local, onSelect = { onSelect(offset + it) }, onOpen = { onOpen() }, modifier = Modifier.weight(1f).height(rowHeight * part.size), rowHeightOverride = rowHeight, pillPaddingOverride = 12.dp, textSizeOverride = nxModalText())
                        }
                    }
                }
            }
        }
    }
}

/** Narrowest a column reads well. */
private val MENU_COLUMN_MIN = 160.dp
private val MENU_COLUMN_GAP = 12.dp
/** The screen width two columns need: both columns, the gap, and the modal's side margins, panel and card padding (48 + 16 + 32). */
private val MENU_TWO_COLUMN_MIN_SCREEN = MENU_COLUMN_MIN * 2 + MENU_COLUMN_GAP + 96.dp
