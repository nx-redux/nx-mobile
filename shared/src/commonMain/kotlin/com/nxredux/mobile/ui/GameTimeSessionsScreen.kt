package com.nxredux.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.nxredux.mobile.gametime.GameSession
import com.nxredux.mobile.gametime.GameSessions
import com.nxredux.mobile.gametime.GameTimeFormat
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nav_icon_delete
import com.nxredux.mobile.shared.res.nav_icon_merge
import com.nxredux.mobile.shared.res.nav_icon_unmerge
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.HintTouch
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDetailListBody
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxTextSizes

/**
 * Game Tracker -> a game: its plays (a launch's slices are one row, schema v2), newest first, under the totals (time,
 * plays, last played). The rows only move; B goes back; the game's Merge / Unmerge / Delete are [gameSessionsHints].
 * The rows are a step smaller than a list's (date: secondary, length and device: caption), at the same pitch. [split]: the focused right pane of the column browser
 * ([GameTimeSplitScreen]), titled by the game alone.
 */
@Composable
fun GameTimeSessionsScreen(
    screen: Screen.GameTimeSessions, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, onHint: (NxButton) -> Unit, split: Boolean = false,
) {
    NxDetailListBody(
        title = toolPageTitle(screen, split).orEmpty(),
        details = gameSessionsDetails(screen.sessions),
        rows = screen.sessions.sessions.map { gameSessionRow(it, screen.sessions.multiDevice) },
        selectedIndex = selectedIndex, hints = gameSessionsHints(screen),
        onSelect = onSelect, onOpen = {}, modifier = modifier, onHint = onHint,
        rowText = NxTextSizes::secondary,   // the date one step below the list label, so the length (and device) is a caption
    )
}

/**
 * B Back, then the game's actions: X Merge (another game to merge into), SELECT Unmerge (games merged into
 * it), Y Delete. On touch they are icons at the title's right, since the list's long press is not on this page.
 */
internal fun gameSessionsHints(s: Screen.GameTimeSessions): List<Hint> = listOfNotNull(
    Hint(NxButton.B, "Back"),
    Hint(NxButton.X, "Merge", HintTouch.Icon(Res.drawable.nav_icon_merge)).takeIf { s.canMerge },
    Hint(NxButton.SELECT, "Unmerge", HintTouch.Icon(Res.drawable.nav_icon_unmerge)).takeIf { s.game.members > 0 },
    Hint(NxButton.Y, "Delete", HintTouch.Icon(Res.drawable.nav_icon_delete)),
)

/** The lines above the rows: the total time (the headline), how many plays, the last play; one line when there are none. */
internal fun gameSessionsDetails(s: GameSessions): List<String> {
    if (s.sessions.isEmpty()) return listOf("No plays recorded")
    val n = s.sessions.size
    return listOf(
        "${GameTimeFormat.duration(s.sessions.sumOf { it.durationMs })} total",
        if (n == 1) "1 play" else "$n plays",
        "Last played ${formatDateTime(s.sessions.maxOf { it.endedAt })}",
    )
}

/** One session: its start date and time, then its length, and the device it ran on when the game has sessions from several. */
internal fun gameSessionRow(s: GameSession, multiDevice: Boolean): NxRow = NxRow(
    formatDateTime(s.startedAt),
    secondary = GameTimeFormat.duration(s.durationMs) + if (multiDevice) "  ·  ${s.deviceName}" else "",
)

/**
 * The Game Tracker column browser on a split window ([gameTimeSplitView]): the game's sessions focused on the right; on
 * the left, dimmed, with no hints, the tracker list with that game's row highlighted ([AppState.gameTimeParent]). A tap
 * on another game there opens its sessions in their place ([AppState.gameTimeParentTap]).
 */
@Composable
internal fun GameTimeSplitScreen(state: AppState, s: Screen.GameTimeSessions, selection: State<ListSelection>) {
    val parent = state.gameTimeParent(s)
    SplitPanes(
        left = {
            parent?.let { p ->
                GameTimeScreen(
                    p.screen, p.row, state::gameTimeParentTap, state::gameTimeParentTap, onModalSelect = {},
                    modifier = Modifier.alpha(UNFOCUSED_ALPHA), onHint = {}, focused = false,   // role: an unfocused pane, dimmed
                )
            }
        },
        right = { WithIndex(selection) { GameTimeSessionsScreen(s, it, state::select, onHint = state::onButton, split = true) } },
    )
}
