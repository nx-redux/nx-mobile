package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.nxModalText
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.gametime.GameTimeFormat
import com.nxredux.mobile.ui.nx.*

/**
 * Tools -> Game Tracker: play time per game group. A (or one tap) opens a row's plays ([GameTimeSessionsScreen]);
 * MENU or a long press its Merge / Unmerge / Delete options. [focused] = false: the dimmed left pane of the column
 * browser beside a game's sessions ([GameTimeSplitScreen]): no hints, no modal, no long press.
 */
@Composable
fun GameTimeScreen(
    screen: Screen.GameTime, selectedIndex: Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit,
    onModalSelect: (Int) -> Unit, modifier: Modifier = Modifier, onHint: (NxButton) -> Unit,
    focused: Boolean = true,
) {
    val back = listOf(Hint(NxButton.B, "Back"))
    if (screen.rows.isEmpty() && !screen.loading) {
        NxEmptyState("No play activity", "Play a game and its time shows up here.", back, onHint, modifier)
        return
    }
    NxRichListBody(
        title = "Game Tracker: ${GameTimeFormat.duration(screen.grandTotalMs)}",
        rows = remember(screen.rows) {   // not per d-pad move
            screen.rows.map {
                NxRichRow(
                    it.total.name, GameTimeFormat.rowLine(it.total), thumbnail = it.art,
                    subtitleSpans = GameTimeFormat.rowParts(it.total).map { (text, emphasis) -> NxSubtitleSpan(text, emphasis) },
                )
            }
        },
        selectedIndex = selectedIndex,
        hints = if (focused) listOf(Hint(NxButton.A, "Plays"), Hint(NxButton.MENU, "Options")) + back else emptyList(),
        onSelect = onSelect, onOpen = onOpen, modifier = modifier, onHint = onHint,
        onLongPress = if (focused) { i -> onSelect(i); onHint(NxButton.MENU) } else null,   // long press = MENU on that row
    )
    if (!focused) return
    val row = screen.rows.getOrNull(selectedIndex)?.total ?: return
    when (val m = screen.modal) {
        null -> Unit
        is GameTimeModal.Options -> ModalList(row.name, m.actions.map { it.label }, m.index, onHint, onModalSelect)
        is GameTimeModal.PickMerge -> ModalList("Merge ${row.name} into", m.candidates.map { "${it.name} · ${GameTimeFormat.duration(it.totalMs)}" }, m.index, onHint, onModalSelect)
        is GameTimeModal.PickName -> ModalList("Name to keep", m.names, m.index, onHint, onModalSelect)
        is GameTimeModal.PickUnmerge -> ModalList("Unmerge from ${row.name}", m.members.map { "${it.name} · ${GameTimeFormat.duration(it.totalMs)}" }, m.index, onHint, onModalSelect)
        GameTimeModal.ConfirmDelete -> NxDialog(
            "Delete play time?",
            if (row.members > 0) "Delete play time for ${row.name} and the ${row.members} ${if (row.members == 1) "game" else "games"} merged into it?" else "Delete play time for ${row.name}?",
            onConfirm = { onHint(NxButton.A) }, confirmLabel = "Delete", onCancel = { onHint(NxButton.B) },
        )
    }
}

/**
 * At most this many modal rows show; a longer list (Merge into… lists every other game, a console submenu ~30 consoles)
 * scrolls inside, keeping the highlight in view.
 */
internal const val MODAL_ROWS = 8

@Composable
private fun ModalList(caption: String, labels: List<String>, index: Int, onButton: (NxButton) -> Unit, onSelect: (Int) -> Unit) {
    val rowHeight = 28.dp * nxTextScale()   // the rows grow with their text (Appearance → Text size)
    NxModal(caption = caption, kind = ModalKind.MENU, onButton = onButton) {
        NxList(labels.map { NxRow(it) }, index, onSelect = onSelect, onOpen = { onButton(NxButton.A) },
            modifier = Modifier.height(rowHeight * minOf(labels.size, MODAL_ROWS)), rowHeightOverride = rowHeight, pillPaddingOverride = 12.dp, textSizeOverride = nxModalText())
    }
}
