package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.achievements.RaBadgeFolders
import com.nxredux.mobile.achievements.RaDownload
import com.nxredux.mobile.achievements.RaDownloadItem
import com.nxredux.mobile.achievements.RaDownloadPhase
import com.nxredux.mobile.achievements.RaGameView
import com.nxredux.mobile.achievements.RaItemStatus
import com.nxredux.mobile.achievements.RaState
import com.nxredux.mobile.achievements.RaSyncStep
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.HintTouch
import com.nxredux.mobile.ui.nx.GamePanelModel
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxRichRow
import com.nxredux.mobile.ui.nx.NxSettingControl
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.control
import com.nxredux.mobile.ui.nx.NxTone

/**
 * The rows of the RetroAchievements page (idle or beside a running download; a sync, sign-in or reset shows progress
 * instead). [DOWNLOAD]: the game data download's progress page, once there is one this process.
 */
enum class RaAction(val label: String) { ACHIEVEMENTS("Achievements"), SYNC("Sync now"), SETTINGS("Settings"), DOWNLOAD("Progress") }

/** The destructive actions that ask first. */
enum class RaConfirm(val title: String, val text: String, val confirmLabel: String) {
    SIGN_OUT("Sign out?", "Signing in again needs your password. Achievements turn off; unsynced unlocks, game data and badges stay.", "Sign out"),
    RESET_ACCOUNT("Reset account data?", "Signs out and forgets sessions and unsynced unlocks. Game data and badges stay.", "Reset"),
    ERASE_ALL("Erase all achievement data?", "Deletes every cached game and unsynced unlock, and signs out. Badges and fingerprints in your ROM folders stay.", "Erase"),
}

/** The RetroAchievements settings page's row kinds; [SECTION] a group's label and [GAP] the space before one, never selected. */
enum class RaSettingKind {
    SECTION, GAP, ENABLED, SIGN_IN, SIGN_OUT, DOWNLOAD_ALL, NOTIFICATIONS, NOTIFICATION_SECONDS, PROGRESS_SECONDS,
    SORT_ORDER, RESET_ACCOUNT, ERASE_ALL, RESET_SETTINGS,
}

/**
 * [enabled] = false: drawn greyed and A does nothing (the account rows that need a sign-in). [secondary]: a value row's
 * value, Sign in / Sign out's status and Download all's cached count; [choices]: a value row's values, its touch control
 * ([raSettingsTouchRow]); [warning]: the status is a problem (the last error).
 */
data class RaSettingsRow(
    val kind: RaSettingKind, val label: String, val secondary: String? = null, val enabled: Boolean = true,
    val choices: SettingChoices? = null, val warning: Boolean = false,
) {
    val selectable: Boolean get() = kind != RaSettingKind.SECTION && kind != RaSettingKind.GAP
}

/**
 * The settings page's hints for the highlighted row, as Settings' ([settingsHints]): a value row LEFT Change, with A
 * Toggle on a switch or Choose on a picker (none on segments, where A steps); an action A Select; a dimmed row Back
 * only. X Info while its description is cut off or says more ([info]).
 */
fun raSettingsHints(row: RaSettingsRow?, touch: NxSettingRow? = null, info: Boolean = false): List<Hint> {
    val back = Hint(NxButton.B, "Back")
    val x = if (info) listOf(Hint(NxButton.X, "Info")) else emptyList()
    return when {
        row == null || !row.selectable -> listOf(back)
        !row.enabled -> listOf(back) + x
        touch != null && touch.choices.size >= 2 -> listOf(back, Hint(NxButton.LEFT, "Change")) + when (touch.control) {
            NxSettingControl.SWITCH -> listOf(Hint(NxButton.A, "Toggle"))
            NxSettingControl.PICKER -> listOf(Hint(NxButton.A, "Choose"))
            else -> emptyList()
        } + x
        else -> listOf(back, Hint(NxButton.A, "Select")) + x
    }
}

fun raRows(state: RaState, download: RaDownload? = null): List<RaAction> =
    if (state is RaState.Idle || state is RaState.Prefetching) RaAction.entries.filter { it != RaAction.DOWNLOAD || download != null } else emptyList()

/** A page row's label: the download's with its count, "Progress (12/340)". */
fun raRowLabel(action: RaAction, download: RaDownload?): String =
    if (action == RaAction.DOWNLOAD && download != null) "${action.label} (${download.finished}/${download.total})" else action.label

/** The download page's first line: "d / t · ~M min left", or its phase when it is not running. */
fun raDownloadHeader(d: RaDownload): String {
    if (d.total == 0 && d.phase == RaDownloadPhase.RUNNING) return "Starting download…"
    val count = "${d.finished} / ${d.total}"
    return when (d.phase) {
        RaDownloadPhase.RUNNING -> d.secondsLeft?.let { "$count · ~${((it + 59) / 60).coerceAtLeast(1)} min left" } ?: count
        RaDownloadPhase.PAUSED -> "$count · paused"
        RaDownloadPhase.DONE -> "$count · done"
        RaDownloadPhase.CANCELLED -> "$count · cancelled"
        RaDownloadPhase.STOPPED -> "$count · stopped" + (d.stopReason?.let { ": $it" } ?: "")
    }
}

/** The download page's second line: what the finished games came to. */
fun raDownloadSummary(d: RaDownload): String = "${d.cached} cached · ${d.unknown} without achievements · ${d.skipped} skipped"

/** A download row's status: its step, or why it was skipped or failed. */
fun raDownloadStatus(item: RaDownloadItem): String = when (item.status) {
    RaItemStatus.WAITING -> "Waiting"
    RaItemStatus.HASHING -> "Hashing"
    RaItemStatus.DOWNLOADING -> "Downloading"
    RaItemStatus.CACHED -> "Cached"
    RaItemStatus.ALREADY_CACHED -> "Already cached"
    RaItemStatus.NO_ACHIEVEMENTS -> "No achievements"
    RaItemStatus.SKIPPED -> "Skipped" + (item.reason?.let { ": $it" } ?: "")
    RaItemStatus.ERROR -> "Error" + (item.reason?.let { ": $it" } ?: "")
}

/**
 * The download page's hints: Y Pause / Resume and X Cancel while it runs, A Done once it has ended (it clears the
 * download), B Back. On touch Pause, Cancel and Done are buttons under the details, above the list.
 */
fun raDownloadHints(phase: RaDownloadPhase): List<Hint> = buildList {
    when (phase) {
        RaDownloadPhase.RUNNING -> add(Hint(NxButton.Y, "Pause", HintTouch.Button(primary = true)))
        RaDownloadPhase.PAUSED -> add(Hint(NxButton.Y, "Resume", HintTouch.Button(primary = true)))
        else -> add(Hint(NxButton.A, "Done", HintTouch.Button(primary = true)))
    }
    if (phase == RaDownloadPhase.RUNNING || phase == RaDownloadPhase.PAUSED) add(Hint(NxButton.X, "Cancel", HintTouch.Button(primary = false)))
    add(Hint(NxButton.B, "Back"))
}

/** Thousands separator without java.text: "1,234". */
internal fun groupThousands(n: Int): String =
    if (n < 0) "-" + groupThousands(-n) else n.toString().reversed().chunked(3).joinToString(",").reversed()

/** The details block on the RetroAchievements page: user, points, unsynced unlocks, last sync. */
fun raDetails(state: RaState): List<String> {
    val s = (state as? RaState.Idle)?.status ?: (state as? RaState.Prefetching)?.previous ?: return listOf(raProgressLine(state) ?: "…")
    return listOf(
        s.user ?: "Not signed in",
        s.points?.let { "${groupThousands(it)} points" } ?: "Sign in under Settings",
        when (s.pending) {
            0 -> if (s.user == null) "0 unlocks waiting" else "Nothing waiting"
            1 -> "1 unlock waiting"
            else -> "${s.pending} unlocks waiting"
        },
        "Last sync: " + (s.lastSync?.let(::formatDateTime) ?: "Never"),
    )
}

/** The progress body's line while busy (a download aside); null when idle. */
fun raProgressLine(state: RaState): String? = when (state) {
    is RaState.Idle -> null
    RaState.Authenticating -> "Signing in…"
    is RaState.Syncing -> state.progress.let { p ->
        when (p.step) {
            RaSyncStep.STARTING -> "Starting sync…"
            RaSyncStep.SUBMITTING -> "Sending unlock ${p.done} of ${p.total}"
            RaSyncStep.ACCOUNT -> "Checking your account…"
            RaSyncStep.READING -> "Reading game data ${p.done} of ${p.total}"
            RaSyncStep.PROGRESS -> "Fetching progress: system ${p.done} of ${p.total}"
            RaSyncStep.SESSIONS -> "Updating game ${p.done} of ${p.total}"
        }
    }
    is RaState.Prefetching -> null   // the page keeps its rows; the download has a page of its own
    is RaState.Resetting -> "Deleting…"
}

/** The line under the bar naming the current item (a game title, a system's game count); null when there is none. */
fun raProgressDetail(state: RaState): String? = (state as? RaState.Syncing)?.progress?.current

/** 0..1 for the bar, null when the phase has no known total. */
fun raProgressFraction(state: RaState): Float? = when (state) {
    is RaState.Syncing -> state.progress.let { p -> if (p.total > 0) (p.done.toFloat() / p.total).coerceIn(0f, 1f) else null }
    else -> null
}

/**
 * An achievement as a rich-list row (the game list's panel, the Tools game page): the badge (its "_lock" variant while locked, [raBadgeModel]: the badge alone
 * says unlocked or not), the title with its points in a pill ([GamePanelModel.pointsBadge]), the description under it.
 * No state words, as the in-game panel's Achievements tab.
 */
fun raAchievementRow(a: RaAchievement, badges: Map<String, String>): NxRichRow =
    NxRichRow(a.title, a.description.ifBlank { null }, thumbnail = raBadgeModel(badges, a), titleBadge = GamePanelModel.pointsBadge(a))

/** How a browser subtitle reads: unlocked (green), pending sync (amber) or locked / plain (dim). */
enum class RaTone { UNLOCKED, PENDING, LOCKED }

/** A browser row's second line and its tone. */
data class RaSubtitle(val text: String, val tone: RaTone)

/** The list tone a subtitle is drawn in. */
fun RaTone.toNxTone(): NxTone = when (this) {
    RaTone.UNLOCKED -> NxTone.SUCCESS
    RaTone.PENDING -> NxTone.WARNING
    RaTone.LOCKED -> NxTone.DIM
}

/** A game row: "u/t unlocked", plus "· n pending sync" in amber while unlocks wait to be sent. */
fun raGameSubtitle(g: RaGameView): RaSubtitle =
    if (g.pending > 0) RaSubtitle("${g.unlocked}/${g.total} unlocked · ${g.pending} pending sync", RaTone.PENDING)
    else RaSubtitle("${g.unlocked}/${g.total} unlocked", RaTone.LOCKED)

/** The badge image model for [a] from its game's listing: the greyed variant while locked; null until downloaded. */
fun raBadgeModel(badges: Map<String, String>, a: RaAchievement): String? =
    RaBadgeFolders.fileName(a.badge, locked = a.state == RaAchievementState.LOCKED)?.let(badges::get)

/** The rows a game page lists: all, or only the locked ones. */
fun visibleAchievements(s: Screen.RaGame): List<RaAchievement> =
    if (s.lockedOnly) s.achievements.filter { it.state == RaAchievementState.LOCKED } else s.achievements
