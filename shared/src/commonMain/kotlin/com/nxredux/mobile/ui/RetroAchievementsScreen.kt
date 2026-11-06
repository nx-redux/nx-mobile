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
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxProgressBody

/**
 * The RetroAchievements tool, in the Settings root's look ([SettingsRootScreen]): while idle, bottom-left, the small
 * "RetroAchievements" label, its head (the user bold, then points and the last sync, [SettingsWordHead]) and the
 * actions as large words (Achievements / Sync now with the unlocks waiting / Settings, and Progress once there is a
 * download); a tap on a word opens it; a progress body while a sync, sign-in or reset runs (B cancels a sync; the others run to
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
            NxProgressBody(RA_TITLE, line, raProgressFraction(state), detail = raProgressDetail(state), hints = if (cancellable && focused) listOf(Hint(NxButton.B, "Cancel")) else emptyList(), onHint = onHint)
        } else {
            val pending = raPending(state)
            SettingsRootScreen(
                RA_TITLE, raRows(state, download).map { raRowLabel(it, download, pending) }, emptyList(), selectedIndex,
                onTap = { onSelect(it); onOpen(it) }, hintBar = focused, onHint = onHint,
                headline = raHeadline(state), description = raDetails(state),
            )
        }
        if (focused) screen.confirm?.let { c ->
            NxDialog(c.title, c.text, onConfirm = { onHint?.invoke(NxButton.A) }, confirmLabel = c.confirmLabel, onCancel = { onHint?.invoke(NxButton.B) })
        }
    }
}

/** The tool's name, its page's label. */
internal const val RA_TITLE = "RetroAchievements"
