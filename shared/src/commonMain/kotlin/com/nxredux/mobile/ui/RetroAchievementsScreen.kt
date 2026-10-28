package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nxredux.mobile.achievements.RaController
import com.nxredux.mobile.achievements.RaState
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDetailListBody
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxProgressBody
import com.nxredux.mobile.ui.nx.NxRow

/**
 * The RetroAchievements tool, laid out like the Cheats & Patches page: while idle a grey title, a details block
 * (user, points, unsynced unlocks, last sync) and the rows (Achievements / Sync now / Settings, and Download progress
 * once there is a download); a progress body while a sync, sign-in or reset runs (B cancels a sync; the others run to
 * the end), and the destructive confirm. A download keeps the rows: it has a page of its own ([RaDownloadScreen]).
 * [focused] = false: the dimmed left pane of the column browser ([RaSplitScreen]), with no hints.
 */
@Composable
fun RetroAchievementsScreen(
    screen: Screen.RetroAchievements, controller: RaController, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier,
    onHint: ((NxButton) -> Unit)? = null,
    focused: Boolean = true,
) {
    val state by controller.state.collectAsState()
    val download by controller.download.collectAsState()
    Box(modifier.fillMaxSize()) {
        val line = raProgressLine(state)
        if (line != null) {
            val cancellable = state is RaState.Syncing
            NxProgressBody("RetroAchievements", line, raProgressFraction(state), detail = raProgressDetail(state), hints = if (cancellable && focused) listOf(Hint(NxButton.B, "Cancel")) else emptyList(), onHint = onHint)
        } else {
            NxDetailListBody(
                title = "RetroAchievements",
                details = raDetails(state),
                rows = raRows(state, download).map { NxRow(raRowLabel(it, download)) },
                selectedIndex = selectedIndex,
                hints = if (focused) listOf(Hint(NxButton.A, "Select"), Hint(NxButton.B, "Back")) else emptyList(),
                onSelect = onSelect, onOpen = onOpen, onHint = onHint,
            )
        }
        if (focused) screen.confirm?.let { c ->
            NxDialog(c.title, c.text, onConfirm = { onHint?.invoke(NxButton.A) }, confirmLabel = c.confirmLabel, onCancel = { onHint?.invoke(NxButton.B) })
        }
    }
}
