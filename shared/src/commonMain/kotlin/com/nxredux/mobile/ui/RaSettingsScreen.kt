package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.NxSettingsPage
import com.nxredux.mobile.ui.nx.NxSignInPrompt

/**
 * Tools -> RetroAchievements -> Settings: the account (Sign in or Sign out with the status under it), game data
 * download, notification and sort preferences, and the destructive resets, as Settings' touch rows
 * ([raSettingsTouchRow]). Sign in opens the sign-in modal (username and password); Sign out and the destructive resets
 * ask first ([RaConfirm]). [onSetting] takes the controls' and info icons' taps, [onTruncated] whether the selected
 * row's description fits. [split]: the focused right pane of the column browser ([RaSplitScreen]): its own name as the
 * title ([subPageTitle]), and no dialogs (the split draws them over both panes).
 */
@Composable
fun RaSettingsScreen(
    screen: Screen.RaSettings, state: AppState, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier,
    onHint: (NxButton) -> Unit,
    split: Boolean = false,
    onSetting: (NxSettingEvent) -> Unit = {},
    onTruncated: (String, Boolean) -> Unit = { _, _ -> },
    truncated: String? = null,   // the selected row's description the UI reported cut off ([AppState.settingInfoTruncated])
) {
    val rows = remember(screen.rows) { screen.rows.map(::raSettingsTouchRow) }   // not per d-pad move
    val touch = rows.getOrNull(selectedIndex)
    Box(modifier.fillMaxSize()) {
        NxSettingsPage(
            toolPageTitle(screen, split), rows, selectedIndex,
            hints = raSettingsHints(screen.rows.getOrNull(selectedIndex), touch, touch != null && nxShowsInfo(touch, truncated)),
            onSelect = onSelect, onOpen = onOpen, onEvent = onSetting, onTruncated = onTruncated, onHint = onHint,
        )
        if (!split) RaSettingsDialogs(screen, state, onHint)
    }
}

/** The settings page's sign-in prompt and confirm, whichever is open. */
@Composable
internal fun RaSettingsDialogs(screen: Screen.RaSettings, state: AppState, onHint: (NxButton) -> Unit) {
    if (screen.signingIn) {
        NxSignInPrompt(
            screen.draftUser, screen.draftPassword, state::raDraftUser, state::raDraftPassword,
            onConfirm = { onHint(NxButton.A) }, onCancel = { onHint(NxButton.B) },
        )
    }
    screen.confirm?.let { c ->
        NxDialog(c.title, c.text, onConfirm = { onHint(NxButton.A) }, confirmLabel = c.confirmLabel, onCancel = { onHint(NxButton.B) })
    }
}

/**
 * An RA settings row as a touch row: its name, its description ([raSettingInfo]; Sign in / Sign out's status and Download
 * all's count as its line, an error in the warning colour) and its values (a dimmed row shows its value only). A group
 * label and a gap as the list's.
 */
internal fun raSettingsTouchRow(row: RaSettingsRow): NxSettingRow {
    if (row.kind == RaSettingKind.SECTION) return NxSettingRow(row.label, section = true)
    if (row.kind == RaSettingKind.GAP) return NxSettingRow("", spacer = true)
    val text = raSettingInfo(row)
    val values = row.choices?.takeIf { row.enabled }
    return NxSettingRow(
        row.label, info = text.line, detail = text.detail,
        choices = values?.labels.orEmpty(), index = values?.index ?: -1,
        value = if (values == null && row.kind !in RA_DESCRIBED_KINDS) row.secondary else null,
        enabled = row.enabled, warning = row.warning,
    )
}

/** Rows whose secondary (the status, the cached count) is their description, not a value. */
private val RA_DESCRIBED_KINDS = setOf(RaSettingKind.SIGN_IN, RaSettingKind.SIGN_OUT, RaSettingKind.DOWNLOAD_ALL)

/** A compact row: no line, [text] (or a fuller [detail]) behind the info icon only. */
private fun quiet(text: String, detail: String = "") = SettingText("", detail.ifEmpty { text })

/**
 * [row]'s description, from the user guide's RetroAchievements page: a line for a status (the account, the cached count),
 * a consequence (the resets) or a dimmed row's reason, the fuller text behind the info icon; the rest compact ([quiet]).
 */
internal fun raSettingInfo(row: RaSettingsRow): SettingText = when (row.kind) {
    RaSettingKind.SIGN_IN -> SettingText(
        row.secondary.orEmpty(),
        "Sign in with a free retroachievements.org account. The app keeps only the sign-in token RetroAchievements " +
            "returns, not your password.",
    )
    RaSettingKind.SIGN_OUT -> SettingText(
        row.secondary.orEmpty(),
        "Sign out asks first. Signing out turns achievements off, and signing in again needs your password. Your " +
            "unsynced unlocks, game data and badges stay.",
    )
    RaSettingKind.ENABLED ->
        if (row.enabled) quiet("Track achievements in games") else SettingText("Needs a sign-in", "Track achievements in games. Needs a sign-in.")
    RaSettingKind.DOWNLOAD_ALL -> SettingText(
        if (row.enabled) row.secondary.orEmpty() else "Needs a sign-in",
        "Cache achievement data and badges for every game in your library, in the background. It needs a connection, " +
            "and running it again only fetches what is new.",
    )
    RaSettingKind.NOTIFICATIONS -> quiet("Show unlock and progress notices in games")
    RaSettingKind.NOTIFICATION_SECONDS -> quiet("How long achievement notices stay")
    RaSettingKind.PROGRESS_SECONDS -> quiet("How long a progress notice stays")
    RaSettingKind.SORT_ORDER -> quiet("How achievements are sorted, here and in the in-game menu")
    RaSettingKind.RESET_ACCOUNT -> SettingText(
        "Forgets unsynced unlocks",
        "Sign out and forget sessions and unsynced unlocks. Game data and badges stay. Run Sync now first.",
    )
    RaSettingKind.ERASE_ALL -> SettingText(
        "Deletes every cached game and unsynced unlock",
        "Delete every cached game and unsynced unlock, and sign out. Badges and game fingerprints kept in your ROM " +
            "folders stay. Run Sync now first.",
    )
    RaSettingKind.RESET_SETTINGS -> SettingText(
        "You stay signed in",
        "Put the notification and sort settings back and turn Enable achievements off. You stay signed in.",
    )
    RaSettingKind.SECTION, RaSettingKind.GAP -> SettingText("")
}
