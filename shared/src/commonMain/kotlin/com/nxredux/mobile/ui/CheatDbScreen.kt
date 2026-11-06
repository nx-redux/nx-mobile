package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nxredux.mobile.cheats.db.CheatDatabase
import com.nxredux.mobile.cheats.db.CheatDbController
import com.nxredux.mobile.cheats.db.CheatDbState
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxProgressBody

/**
 * The Cheats & Patches tool (the cheat database), in the Settings root's look ([SettingsRootScreen]): while idle,
 * bottom-left, the small label, its head (the install state bold, then size or counts and the last update) and the
 * actions as large words (Download / Check for updates / Remove), a tap opening one;
 * a progress body while working (B cancels), and the remove confirm. State comes straight from the controller.
 */
@Composable
fun CheatDbScreen(
    screen: Screen.CheatDb, controller: CheatDbController, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier,
    onHint: ((NxButton) -> Unit)? = null,
) {
    val state by controller.state.collectAsState()
    Box(modifier.fillMaxSize()) {
        val line = cheatDbProgressLine(state)
        if (line != null) {
            // Removing can't be cancelled (the controller ignores it), so it offers no B hint.
            val hints = if (state is CheatDbState.Removing) emptyList() else listOf(Hint(NxButton.B, "Cancel"))
            NxProgressBody(ToolKind.CHEAT_DB.toolName!!, line, cheatDbProgressFraction(state), hints = hints, onHint = onHint)
        } else {
            // The install state as the head's headline, its size or counts and last update the description.
            val details = cheatDbDetails(state, CheatDatabase.rowsFor(CheatDatabase.installTags).size)
            SettingsRootScreen(
                ToolKind.CHEAT_DB.toolName!!, cheatDbRows(state).map { it.label }, emptyList(), selectedIndex,
                onTap = { onSelect(it); onOpen(it) }, hintBar = true, onHint = onHint,
                headline = details.firstOrNull(), description = details.drop(1),
            )
        }
        if (screen.confirmRemove) {
            NxDialog(
                "Remove Cheats & Patches?", "Removes the downloaded files; your own cheat and patch files stay.",
                onConfirm = { onHint?.invoke(NxButton.A) }, confirmLabel = "Remove", onCancel = { onHint?.invoke(NxButton.B) },
            )
        }
    }
}
