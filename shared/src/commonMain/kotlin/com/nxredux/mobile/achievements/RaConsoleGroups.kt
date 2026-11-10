package com.nxredux.mobile.achievements

import com.nxredux.mobile.runner.CoreRegistry

/** The Tools browser's consoles: which console a cached game is listed under, and the games grouped by it. */
object RaConsoleGroups {
    /** The console of a game the app can place nowhere. */
    const val OTHER = "Other"

    /**
     * A game's console name and tag: its library folder's ([libraryTag], [libraryName]: the names the Consoles tab
     * shows), else its pointer's tag ([pointerTag]: a game no longer indexed, or a handheld's `rom.txt`), else
     * RetroAchievements' [raConsoleId]; [OTHER] with no tag when none of them names a console.
     */
    fun consoleOf(libraryTag: String?, libraryName: String?, pointerTag: String?, raConsoleId: Int?): Pair<String, String?> {
        if (libraryTag != null && !libraryName.isNullOrBlank()) return libraryName to libraryTag
        pointerTag?.let { t -> CoreRegistry.consoleOf(t)?.let { return it.displayName to t.trim().uppercase() } }
        raConsoleId?.let(RaConsoles::tagOf)?.let { t -> CoreRegistry.consoleOf(t)?.let { return it.displayName to t } }
        return OTHER to null
    }

    /** [games] by console, sorted by name as the Consoles tab is ([OTHER] last); each console keeps its games' order. */
    fun of(games: List<RaGameView>): List<RaConsoleView> =
        games.groupBy { it.console }.map { (name, list) -> RaConsoleView(name, list.firstNotNullOfOrNull { it.consoleTag }, list) }
            .sortedWith(compareBy({ it.name == OTHER }, { it.name.lowercase() }))
}
