package com.nxredux.mobile.ui

import com.nxredux.mobile.cheats.db.CheatDatabase
import com.nxredux.mobile.cheats.db.CheatDbSource
import com.nxredux.mobile.cheats.db.CheatDbState

/** [APPS]: the app drawer (every launchable app); [APP]: one Android app added to Tools or pinned on Home. */
enum class ToolKind { RETRO_ACHIEVEMENTS, GAME_TIME, ARTWORK, CHEAT_DB, DEVICE_SYNC, SETTINGS, APPS, APP }

/** A row on the Tools screen: the tool's name and a one-line status. [appId]: an Android app's id ([ToolKind.APP] only). */
data class ToolRow(val kind: ToolKind, val label: String, val secondary: String?, val appId: String? = null)

/** What tool-pins.txt keeps for [this]: the kind's name, or "APP:" and the app's id ([com.nxredux.mobile.storage.ToolPins]). */
val ToolRow.pinKey: String get() = if (kind == ToolKind.APP) "APP:$appId" else kind.name

/** A tool's name as the Tools tab shows it; null for an Android app (it has its own label). */
val ToolKind.toolName: String?
    get() = when (this) {
        ToolKind.RETRO_ACHIEVEMENTS -> "RetroAchievements"
        ToolKind.GAME_TIME -> "Game Tracker"
        ToolKind.ARTWORK -> "Artwork"
        ToolKind.CHEAT_DB -> "Cheats & Patches"
        ToolKind.DEVICE_SYNC -> "Device Sync"
        ToolKind.SETTINGS -> "Settings"
        ToolKind.APPS -> "Apps"
        ToolKind.APP -> null
    }

/**
 * A page opened from inside a tool: the tool's name, then the page's ("RetroAchievements | Achievements"), the same
 * separator as a game list's [listTitle]. The tool's own top page keeps its plain name. [split]: a pane of a split
 * (two panes), whose left pane already shows where it came from: the page's own name alone ("Settings", not
 * "RetroAchievements | Settings").
 */
fun subPageTitle(tool: ToolKind, page: String, split: Boolean = false): String =
    if (split) page else tool.toolName?.let { "$it | $page" } ?: page

/** The title of a page inside a tool ([subPageTitle], [split] as there), its [toolPageCount] after it; null for any other screen. */
fun toolPageTitle(screen: Screen, split: Boolean = false): String? =
    toolPageTitleHead(screen, split)?.let { head -> toolPageCount(screen)?.let { "$head $it" } ?: head }

/** A tool page title's count ("(3/20)" on a game's achievements), drawn after the title and never cut; null when it has none. */
fun toolPageCount(screen: Screen): String? = when (screen) {
    is Screen.RaGame -> screen.game
    is Screen.RaAchievement -> screen.game
    else -> null
}?.let { "(${it.unlocked}/${it.total})" }

/** [toolPageTitle] without its [toolPageCount]: the part an over-long title cuts. */
fun toolPageTitleHead(screen: Screen, split: Boolean = false): String? = when (screen) {
    is Screen.RaGames -> subPageTitle(ToolKind.RETRO_ACHIEVEMENTS, "Achievements", split)
    is Screen.RaGame -> subPageTitle(ToolKind.RETRO_ACHIEVEMENTS, screen.game.title, split)
    is Screen.RaAchievement -> subPageTitle(ToolKind.RETRO_ACHIEVEMENTS, screen.game.title, split)   // an achievement names its game
    is Screen.RaSettings -> subPageTitle(ToolKind.RETRO_ACHIEVEMENTS, "Settings", split)
    is Screen.GameTimeSessions -> subPageTitle(ToolKind.GAME_TIME, screen.game.name, split)
    is Screen.ArtworkConsoles -> subPageTitle(ToolKind.ARTWORK, "Fetch by console", split)
    is Screen.ArtworkSettings -> subPageTitle(ToolKind.ARTWORK, "Settings", split)
    is Screen.ArtworkReset -> subPageTitle(ToolKind.ARTWORK, "Reset artwork", split)
    is Screen.ArtworkProgress -> subPageTitle(ToolKind.ARTWORK, "Progress", split)
    else -> null
}

/** A tile of the app drawer or the Android games picker: [shown] = in the Tools tab (drawer) or the console (games). */
data class ToolAppRow(val id: String, val label: String, val shown: Boolean)

/** The rows of the Cheats & Patches page; which ones show depends on the state. */
enum class CheatDbAction(val label: String) {
    DOWNLOAD("Download (${CheatDatabase.DOWNLOAD_HINT})"), CHECK("Check for updates"), REMOVE("Remove")
}

fun cheatDbRows(state: CheatDbState): List<CheatDbAction> = when (state) {
    CheatDbState.NotInstalled -> listOf(CheatDbAction.DOWNLOAD)
    is CheatDbState.Installed -> listOf(CheatDbAction.CHECK, CheatDbAction.REMOVE)
    else -> emptyList()   // Unknown or busy: no rows, the page shows progress instead
}

/** The Tools row's status line; null state = no controller on this platform. */
fun cheatDbStatus(state: CheatDbState?): String = when (state) {
    null -> "Unavailable"
    CheatDbState.Unknown -> "…"
    CheatDbState.NotInstalled -> "Not installed"
    is CheatDbState.Installed -> "${state.manifest.systems} systems · ${state.manifest.fileCount} files · ${formatInstallDate(state.manifest.installedAt)}"
    is CheatDbState.Downloading -> "Downloading…"
    is CheatDbState.Installing -> "Installing…"
    is CheatDbState.Checking -> "Checking…"
    is CheatDbState.Removing -> "Removing…"
}

/** The details block on the Cheats & Patches page: first line is the headline, the rest are dim. [systems] = consoles the download covers. */
fun cheatDbDetails(state: CheatDbState, systems: Int): List<String> = when (state) {
    CheatDbState.Unknown -> listOf("…")
    CheatDbState.NotInstalled -> listOf("Not installed", "${CheatDatabase.DOWNLOAD_HINT.replaceFirstChar { it.uppercase() }} download", "Cheats and patches for $systems systems")
    is CheatDbState.Installed -> listOf("Installed", "${state.manifest.systems} systems · ${state.manifest.fileCount} files", "Last update: ${formatInstallDate(state.manifest.installedAt)}")
    else -> listOf(cheatDbStatus(state))
}

/** The progress body's line while busy; null when idle. */
fun cheatDbProgressLine(state: CheatDbState): String? = when (state) {
    is CheatDbState.Downloading -> {
        val what = if (state.source == CheatDbSource.PCSX2) "patches" else "cheats"
        if (state.total > 0) "Downloading $what… ${mb(state.done)} / ${mb(state.total)} MB" else "Downloading $what… ${mb(state.done)} MB"
    }
    is CheatDbState.Installing -> "Installing ${state.tag} (${state.index}/${state.count})"
    is CheatDbState.Checking -> "Checking for updates…"
    is CheatDbState.Removing -> "Removing…"
    else -> null
}

/** 0..1 for the bar, null when the total is unknown or the phase has no measure. */
fun cheatDbProgressFraction(state: CheatDbState): Float? = when (state) {
    is CheatDbState.Downloading -> if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else null
    is CheatDbState.Installing -> if (state.count > 0) state.index.toFloat() / state.count else null
    else -> null
}

/** One decimal, no String.format in commonMain. */
private fun mb(bytes: Long): String {
    val tenths = (bytes * 10 + (1 shl 19)) / (1 shl 20)   // rounded to 0.1 MB
    return "${tenths / 10}.${tenths % 10}"
}
