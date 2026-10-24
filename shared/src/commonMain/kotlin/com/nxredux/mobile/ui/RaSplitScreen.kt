package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.nxredux.mobile.ui.nx.ListSelection

/**
 * The RetroAchievements column browser on a split window ([raSplitView]): the level the user is on ([page], drawn with
 * split = true) focused on the right; on the left, dimmed, with no hints, the level it came from ([AppState.raParent])
 * with the row that opened it highlighted. A tap on the left pane opens that level's row in place of the right one
 * ([AppState.raParentTap]). The RetroAchievements Settings sign-in prompt and confirms cover both panes.
 */
@Composable
internal fun RaSplitScreen(state: AppState, s: Screen, page: @Composable () -> Unit) {
    val parent = state.raParent(s)
    SplitPanes(
        left = { parent?.let { RaParentPane(state, it) } },
        right = page,
        over = { if (s is Screen.RaSettings) RaSettingsDialogs(s, state, state::onButton) },
    )
}

/** The dimmed left pane: [parent]'s level drawn as it was (a progress body on the RetroAchievements page too), its row lit. */
@Composable
private fun RaParentPane(state: AppState, parent: RaParent) {
    val c = state.achievements ?: return
    val tap: (Int) -> Unit = remember(parent.screen::class) { { i -> state.raParentTap(parent.screen, i) } }
    val dim = Modifier.alpha(UNFOCUSED_ALPHA)
    when (val p = parent.screen) {
        is Screen.RetroAchievements -> RetroAchievementsScreen(p, c, parent.row, tap, tap, modifier = dim, focused = false)
        is Screen.RaGames -> RaGamesScreen(p, parent.row, tap, tap, modifier = dim, onHint = {}, split = true, focused = false)
        is Screen.RaGame -> RaGameScreen(p, c, parent.row, tap, tap, modifier = dim, split = true, focused = false)
        else -> Box(Modifier.fillMaxSize())
    }
}

/**
 * A RetroAchievements level below its page as one page, or ([split]) as the focused right pane of [RaSplitScreen]: the
 * title without the tool's name, the settings' dialogs drawn by the split.
 */
@Composable
internal fun RaLevelPage(state: AppState, s: Screen, selection: State<ListSelection>, split: Boolean) {
    val c = state.achievements
    when (s) {
        is Screen.RaGames -> WithIndex(selection) { RaGamesScreen(s, it, state::select, { state.openSelected() }, onHint = state::onButton, split = split) }
        is Screen.RaGame -> c?.let { WithIndex(selection) { RaGameScreen(s, c, it, state::select, { state.openSelected() }, onHint = state::onButton, split = split) } } ?: Box(Modifier.fillMaxSize())
        is Screen.RaAchievement -> c?.let { RaAchievementScreen(s, c, onHint = state::onButton, split = split) } ?: Box(Modifier.fillMaxSize())
        is Screen.RaSettings -> {
            val truncated by state.settingInfoTruncated.collectAsState()   // the selected row's cut-off line: its X Info hint
            WithIndex(selection) {
                RaSettingsScreen(
                    s, state, it, state::select, { state.openSelected() }, onHint = state::onButton, split = split,
                    onSetting = state::settingEvent, onTruncated = state::onSettingInfoTruncated, truncated = truncated,
                )
            }
        }
        else -> Unit
    }
}
