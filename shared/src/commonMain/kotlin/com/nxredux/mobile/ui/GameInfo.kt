package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.gametime.GameTimeFormat
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.path

/** What the menu shows under a selected game: last played and total time (game time), and RetroAchievements progress. */
data class GameInfo(val lastPlayedAt: Long?, val totalMs: Long?, val ra: RaProgress?, val plays: Long? = null)

/** Whether [info]'s game has achievements: a played homebrew or hack has a pointer folder but no set, so only real data counts. */
internal fun hasRaSet(info: GameInfo?): Boolean = info?.ra?.total?.let { it > 0 } == true

private const val DAY_MS = 86_400_000L

/** "Today", "Yesterday", "N days ago", "Last week", "N weeks ago", "Last month", "N months ago", "Last year", "N years ago". */
internal fun whenText(now: Long, then: Long): String {
    val days = ((now - then).coerceAtLeast(0) / DAY_MS).toInt()
    return when {
        days == 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> "$days days ago"
        days < 14 -> "Last week"
        days < 30 -> "${days / 7} weeks ago"
        days < 60 -> "Last month"
        days < 365 -> "${days / 30} months ago"
        days < 730 -> "Last year"
        else -> "${days / 365} years ago"
    }
}

/**
 * When the first of [thens] (last-played times) next changes its [whenText] label after [now]: the soonest whole day since
 * its play still ahead (a time ahead of [now] reads "Today" until a day past it). Null with no times.
 */
internal fun nextWhenChange(now: Long, thens: Iterable<Long>): Long? =
    thens.minOfOrNull { then -> if (then > now) then + DAY_MS else then + ((now - then) / DAY_MS + 1) * DAY_MS }

enum class InfoKind { TIME, ACHIEVEMENTS, NEXT, COUNT }

/** Between the info line's segments: a middle dot, a space each side. */
internal const val INFO_SEPARATOR = " · "

/** One segment of the info line (the UI shows the segments on one line, in order); the trophy goes before an [InfoKind.ACHIEVEMENTS] one. */
data class InfoLine(val kind: InfoKind, val text: String)

/**
 * The selected game's info segments, each left out without data: "<when> - <duration>" (either alone), "n of m", then
 * "Next: <title>"; shown joined by [INFO_SEPARATOR], e.g. "Today - 1m 31s · 0 of 35 · Next: That Was Easy".
 * [full] false (a grid tile): the achievement count without the next goal.
 */
internal fun gameInfoLines(info: GameInfo?, now: Long, full: Boolean): List<InfoLine> {
    if (info == null) return emptyList()
    return buildList {
        val time = listOfNotNull(info.lastPlayedAt?.let { whenText(now, it) }, info.totalMs?.let(GameTimeFormat::duration)).joinToString(" - ")
        if (time.isNotEmpty()) add(InfoLine(InfoKind.TIME, time))
        info.ra?.let { ra ->
            add(InfoLine(InfoKind.ACHIEVEMENTS, "${ra.unlocked} of ${ra.total}"))
            if (full && ra.next != null) add(InfoLine(InfoKind.NEXT, "Next: ${ra.next}"))
        }
    }
}

/**
 * A console row's info line: its games over every tag it merges (SFC and SUPA are one row). The systems are the
 * playable ones with hidden games already dropped, so this is what opening the console lists.
 */
internal fun consoleInfoLines(row: MainRow.System): List<InfoLine> = listOf(InfoLine(InfoKind.COUNT, gamesLabel(row.systems.sumOf { it.gameCount })))

/** A collection row's info line: the games its list shows ("1 game", "N games"); none when its file would not read. */
internal fun collectionInfoLines(row: MainRow.Collection): List<InfoLine> = row.count?.let { listOf(InfoLine(InfoKind.COUNT, gamesLabel(it))) }.orEmpty()

/** The Game Switcher's line under the title: the game's system, then its place among the recents ("Super Nintendo ES   2 / 6"). */
internal fun switcherSubtitle(systemName: String, index: Int, count: Int): String = "$systemName   ${index + 1} / $count"

/**
 * The game info the launcher menus (the main menu, Home included, and the game lists) draw from: [gameInfo],
 * or none while Layouts → Extra info hides it ([extraInfo] false), so every layout's play time, achievements and next
 * goal go at once ([gameInfoFor] gives none) while a console's or collection's game count ([consoleInfoLines]) stays.
 */
internal fun launcherGameInfo(gameInfo: Map<String, GameInfo>, extraInfo: Boolean): Map<String, GameInfo> =
    if (extraInfo) gameInfo else emptyMap()

/** A game's info segments ([gameInfoLines]) from [gameInfo] at [now]; none for no game. [full] false: a grid tile's. */
internal fun gameInfoFor(gameInfo: Map<String, GameInfo>, now: Long, game: GameEntry?, full: Boolean): List<InfoLine> =
    game?.let { gameInfoLines(gameInfo[it.path], now, full) }.orEmpty()

/** A main menu row's game (a pin's, a shelf game's or Continue's); null for any other row. */
internal fun MainRow?.game(): GameEntry? = listed()?.game

/** A main menu row's listed game (a pin's, a shelf game's or Continue's); null for any other row. */
internal fun MainRow?.listed(): Listed? = when (this) {
    is MainRow.Pin -> entry
    is MainRow.Shelf -> entry
    is MainRow.Continue -> entry
    else -> null
}

/**
 * The main menu Grid's and Home pins' per-tile info: row index to that row's game's tile info ([gameInfoFor], not
 * full); none for a row without a game. Each tile reads its own lazily, where its caption shows (the selected tile,
 * and an art-less one always), so a selection move computes nothing for the others.
 */
internal fun rowTileInfo(rows: List<MainRow>, gameInfo: Map<String, GameInfo>, now: Long): (Int) -> List<InfoLine> =
    { i -> gameInfoFor(gameInfo, now, rows.getOrNull(i).game(), full = false) }

/** A game list Grid's per-tile info: entry index to that game's tile info, as [rowTileInfo]. */
internal fun entryTileInfo(entries: List<Listed>, gameInfo: Map<String, GameInfo>, now: Long): (Int) -> List<InfoLine> =
    { i -> gameInfoFor(gameInfo, now, entries.getOrNull(i)?.game, full = false) }
