package com.nxredux.mobile.ui

import com.nxredux.mobile.library.SystemFolder

/** The Games tab's list: every playable game of every console, and the Android console's games. */
internal object AllGames {
    /**
     * The games of [systems] that can be played (not Unassigned, not without a core) and of [android], flattened and
     * sorted by shown name ignoring case, a name on two consoles keeping both, the console's tag breaking the tie.
     * Each row keeps its own system, so it launches under its own console.
     */
    fun entries(systems: List<SystemFolder>, android: SystemFolder?): List<Listed> =
        (systems + listOfNotNull(android))
            .filter { !it.unassigned && (it.core != null || it.android) }
            .flatMap { s -> s.games.map { Listed(s, it) } }
            .sortedWith(compareBy({ it.game.shownName.lowercase() }, { it.system.tag }))
}
