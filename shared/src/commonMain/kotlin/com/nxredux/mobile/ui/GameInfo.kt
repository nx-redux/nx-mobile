package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.gametime.GameTimeFormat
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.path
import com.nxredux.mobile.scraper.StoredGameInfo
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.ui.nx.carouselGameLines

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

enum class InfoKind { TIME, ACHIEVEMENTS, NEXT, COUNT, GENRE, RELEASE }

/** The segment a caption puts on a row of its own under the rest: the next goal, or the release in its place. */
internal val InfoLine.ownRow: Boolean get() = kind == InfoKind.NEXT || kind == InfoKind.RELEASE

/** Between the info line's segments: a middle dot, a space each side. */
internal const val INFO_SEPARATOR = " · "

/** One segment of the info line (the UI shows the segments on one line, in order); the trophy goes before an [InfoKind.ACHIEVEMENTS] one. */
data class InfoLine(val kind: InfoKind, val text: String)

/**
 * The selected game's info segments, each left out without data: "<when> - <duration>" (either alone), "n of m", then
 * "Next: <title>"; shown joined by [INFO_SEPARATOR], e.g. "Today - 1m 31s · 0 of 35 · Next: That Was Easy".
 * [full] false (a grid tile): the achievement count without the next goal. Where [full], a game with no achievements
 * to show (signed out, or no set), or any game with [metaAlways] (Layouts → Game info: Details, the default), has its stored
 * ScreenScraper facts ([meta]) in their place: its genre for the count ([noGenre] without one: [NO_GENRE] in the
 * menus, so every caption keeps its pill), "<year> · <publisher>" for the next goal, left out without data.
 */
internal fun gameInfoLines(
    info: GameInfo?, now: Long, full: Boolean, meta: StoredGameInfo? = null, metaAlways: Boolean = false, noGenre: String? = null,
): List<InfoLine> = buildList {
    val time = listOfNotNull(info?.lastPlayedAt?.let { whenText(now, it) }, info?.totalMs?.let(GameTimeFormat::duration)).joinToString(" - ")
    if (time.isNotEmpty()) add(InfoLine(InfoKind.TIME, time))
    val ra = info?.ra?.takeUnless { full && metaAlways }
    if (ra != null) {
        add(InfoLine(InfoKind.ACHIEVEMENTS, "${ra.unlocked} of ${ra.total}"))
        if (full && ra.next != null) add(InfoLine(InfoKind.NEXT, "Next: ${ra.next}"))
    } else if (full) {
        (meta?.genre?.trim()?.ifEmpty { null } ?: noGenre)?.let { add(InfoLine(InfoKind.GENRE, it)) }
        val release = listOfNotNull(meta?.year?.trim()?.ifEmpty { null }, meta?.publisher?.trim()?.ifEmpty { null }).joinToString(INFO_SEPARATOR)
        if (release.isNotEmpty()) add(InfoLine(InfoKind.RELEASE, release))
    }
}

/**
 * The games' stored ScreenScraper facts by path ([AppState.gameMeta]) and Layouts → Game info ([always]: they show in
 * the achievements' place for every game, not only one without achievements). [shown]: the menus show them (Extra
 * info), so a game without a genre gets [NO_GENRE].
 */
internal data class GameMeta(val byPath: Map<String, StoredGameInfo> = emptyMap(), val always: Boolean = false, val shown: Boolean = false) {
    companion object { val NONE = GameMeta() }
}

/** The genre pill's word for a game with no genre stored (none fetched yet, or ScreenScraper has none: an Android game). */
internal const val NO_GENRE = "Game"

/** A [InfoKind.GENRE] segment's genres ("Sports / Soccer" is two), each drawn as a pill. */
internal fun genreParts(text: String): List<String> = text.split('/', ',').map { it.trim() }.filter { it.isNotEmpty() }

/** Between a genre segment's genres in its text ([genreParts] reads them back). */
internal const val GENRE_JOIN = " / "

/** How many genre pills ([widths] px, in order, [gap] px between) fit [room] px: the first always, cut short itself if it must be. */
internal fun genresThatFit(widths: List<Float>, gap: Float, room: Float): Int {
    var used = 0f
    widths.forEachIndexed { i, w ->
        used += w + if (i > 0) gap else 0f
        if (i > 0 && used > room) return i
    }
    return widths.size
}

/** [lines] with only the first genre: for a one-line caption that is not fitted to its width. */
internal fun firstGenre(lines: List<InfoLine>): List<InfoLine> =
    lines.map { l -> if (l.kind == InfoKind.GENRE) l.copy(text = genreParts(l.text).firstOrNull() ?: l.text) else l }

/** Layouts → Game info's value: what a caption shows under the play time. */
internal fun gameInfoLabel(achievements: Boolean): String = if (achievements) "Achievements" else "Details"

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

/** A game's info segments ([gameInfoLines]) from [gameInfo] and [meta] at [now]; none for no game. [full] false: a grid tile's. */
internal fun gameInfoFor(gameInfo: Map<String, GameInfo>, now: Long, game: GameEntry?, full: Boolean, meta: GameMeta = GameMeta.NONE): List<InfoLine> =
    game?.let { gameInfoLines(gameInfo[it.path], now, full, meta.byPath[it.path], meta.always, NO_GENRE.takeIf { meta.shown }) }.orEmpty()

/** [GameMeta] for the launcher menus: none while Layouts → Extra info hides the game info ([launcherGameInfo]). */
internal fun launcherGameMeta(byPath: Map<String, StoredGameInfo>, always: Boolean, extraInfo: Boolean): GameMeta =
    if (extraInfo) GameMeta(byPath, always, shown = true) else GameMeta.NONE

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

/**
 * A game list Grid's per-tile info: entry index to that game's tile info, as [rowTileInfo], then its details from the
 * stored facts ([meta], as Layouts → Extra info lets the menus show them, whatever Game info says: [gameTileLines]). Read lazily
 * per tile, where its caption shows.
 */
internal fun entryTileInfo(entries: List<Listed>, gameInfo: Map<String, GameInfo>, now: Long, meta: GameMeta = GameMeta.NONE): (Int) -> List<InfoLine> =
    { i ->
        val game = entries.getOrNull(i)?.game
        // The details have their own rows: Layouts → Game info does not trade them for the achievements here.
        gameTileLines(gameInfoFor(gameInfo, now, game, full = false), if (!meta.shown) emptyList() else gameInfoFor(gameInfo, now, game, full = true, meta.copy(always = true)))
    }

/**
 * [game]'s full info where its details have a place of their own (a game list's horizontal Carousel: the time and
 * achievements in the picture, the details under the name, [carouselGameLines]): both, whatever Layouts → Game info
 * says ([GameMeta.always] trades one for the other only where they share a place). Extra info off ([meta] not shown):
 * no details.
 */
internal fun gameInfoApart(gameInfo: Map<String, GameInfo>, now: Long, game: GameEntry?, meta: GameMeta): List<InfoLine> {
    val stats = carouselGameLines(gameInfoFor(gameInfo, now, game, full = true, meta.copy(always = false))).picture
    val details = if (!meta.shown) emptyList() else carouselGameLines(gameInfoFor(gameInfo, now, game, full = true, meta.copy(always = true))).underTitle
    return stats + details
}

/**
 * A game list's selected [game]'s info in [style] and [orientation]: where its stats and details have places of their
 * own (a horizontal Carousel: the picture and under the name; a Grid: the line under the title and the selected tile,
 * [gridHeaderInfo]) both, whatever Layouts → Game info says ([gameInfoApart]); elsewhere as it says ([gameInfoFor]).
 */
internal fun gameListInfo(style: MenuStyle, orientation: MenuOrientation, gameInfo: Map<String, GameInfo>, now: Long, game: GameEntry?, meta: GameMeta): List<InfoLine> =
    if (style == MenuStyle.GRID || style == MenuStyle.CAROUSEL && orientation == MenuOrientation.HORIZONTAL) gameInfoApart(gameInfo, now, game, meta)
    else gameInfoFor(gameInfo, now, game, full = true, meta)

/**
 * A game list Grid tile's lines: its count-only [stats] (the time, the achievement count), then the details of its
 * [full] info (the genre, "<year> · <publisher>": what a Carousel puts under its name, [carouselGameLines]).
 */
internal fun gameTileLines(stats: List<InfoLine>, full: List<InfoLine>): List<InfoLine> = stats + carouselGameLines(full).underTitle
