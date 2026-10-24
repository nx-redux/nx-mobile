package com.nxredux.mobile.ui

import com.nxredux.mobile.gametime.GameTotal
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.ui.nx.HomeFrame
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.homeBoard
import com.nxredux.mobile.ui.nx.homePortraitTwin
import kotlin.math.max
import kotlin.random.Random

/**
 * What Home's stats strip ([homeStrip]) shows. [playedMs], [unlocks] and [top] cover the calendar month so far;
 * [unlocks] is null when signed out or not counted. [computed] false = nothing computed yet: every value shows "…".
 */
data class HomeStats(val playedMs: Long?, val unlocks: Int?, val signedIn: Boolean, val top: GameTotal?, val computed: Boolean)

/**
 * The Home tab: the top section (Continue or Pick a game, any inline games, the tool squares), the row games, the
 * strip and the shelves' games.
 */
data class Home(val top: List<MainRow>, val pins: List<MainRow>, val strip: HomeStrip?, val freshInstall: Boolean, val shelves: List<MainRow.Shelf> = emptyList())

/**
 * What HomeScreen needs beside the rows: the [strip], how many rows are the top section, and the pinned counts. [frame]
 * and [lines]: the frame and strip lines the rows were built for (null before Home's first layout report), so the
 * screen places them on their own board until a rebuild for a new frame arrives. [shelves]: Home's non-empty
 * shelves and their sizes, in page order.
 */
data class HomeView(
    val strip: HomeStrip?, val topCount: Int, val tools: Int, val games: Int,
    val frame: HomeFrame? = null, val lines: Int? = null,
    val shelves: List<Pair<HomeShelf, Int>> = emptyList(),
)

internal const val HOME_DOCK_GAP = 12f
/** The smallest a dock square gets, on a body too small for Grid Home's squares. */
private const val HOME_DOCK_MIN_SIDE = 32f

/**
 * The Carousel Home's dock squares' side on [f]: Grid Home's portrait pinned tool squares' ([HomeBoard.square], with the
 * strip's [stripLines] and the [games] pinned games Grid would have in its rows), in landscape too ([homePortraitTwin]),
 * so the dock looks the same either way; at least [HOME_DOCK_MIN_SIDE]. AppState fits the dock with it ([homeDockFit])
 * and the screen draws it, so the two agree.
 */
internal fun homeDockSide(f: HomeFrame, stripLines: Int, games: Int): Float =
    max(HOME_DOCK_MIN_SIDE, homeBoard(if (f.portrait) f else homePortraitTwin(f), stripLines, tools = 1, games = games).square)

/** The Carousel Home's dock squares of [side] dp that fit [widthDp] (its gutters off): at least one. */
internal fun homeDockFit(widthDp: Float, portrait: Boolean, side: Float): Int {
    val gutter = if (portrait) 16f else 24f
    return ((widthDp - 2 * gutter + HOME_DOCK_GAP) / (side + HOME_DOCK_GAP)).toInt().coerceAtLeast(1)
}

/** The Carousel Home's row items in [rows] ([homeTabRows]): every row but the pinned tools and "+N", which make the dock. */
internal fun homeCarouselRowCount(rows: List<MainRow>): Int = rows.count { it !is MainRow.PinnedTool && it !is MainRow.MoreTools }

internal const val NO_PLAY_YET = "No play yet"
internal const val STAT_PENDING = "\u2026"         // a stats value not computed yet

/**
 * Home from the last game ([last]), the pins in their order and the stats, for a top section of [slots] squares (the
 * board's, [com.nxredux.mobile.ui.nx.homeBoard]). Continue's own game is never a pin. The first [inline] pinned games
 * (the board's [com.nxredux.mobile.ui.nx.HomeBoard.inlineGames], on every landscape face) go in [Home.top] right
 * after Continue, before the tools; [Home.pins] holds the rest. More tools than slots: the first slots − 1 and a
 * [MainRow.MoreTools] for the rest. [shelves] ([homeShelves]) follow the row games, a fresh install's too (a library
 * with nothing played still gets Not started).
 */
internal fun buildHome(
    last: MainRow.Continue?, pinnedGames: List<Listed>, pinnedTools: List<ToolRow>, stats: HomeStats, slots: Int,
    shelves: List<Pair<HomeShelf, List<Listed>>> = emptyList(), inline: Int = 0,
): Home {
    val rows = shelves.flatMap { (k, games) -> games.map { MainRow.Shelf(it, k) } }
    if (last == null && pinnedGames.isEmpty() && pinnedTools.isEmpty()) return Home(listOf(MainRow.PickAGame), emptyList(), strip = null, freshInstall = true, shelves = rows)
    val games = pinnedGames.filterNot { it.game.path == last?.entry?.game?.path }.map { MainRow.Pin(it) }
    // MoreTools carries the tools it hides: every tool past the first slots − 1 (5 tools, 4 slots: +2).
    val tools = if (pinnedTools.size > slots) pinnedTools.take(slots - 1).map { MainRow.PinnedTool(it) } + MainRow.MoreTools(pinnedTools.size - (slots - 1))
        else pinnedTools.map { MainRow.PinnedTool(it) }
    val n = inline.coerceIn(0, games.size)
    return Home(listOf(last ?: MainRow.PickAGame) + games.take(n) + tools, games.drop(n), homeStrip(stats), freshInstall = false, shelves = rows)
}

/** Every pinned tool is a square or hidden behind "+N"; [HomeView.games] counts every pinned game, inline ones too. */
internal fun homeView(home: Home, rows: List<MainRow>): HomeView = HomeView(
    home.strip, home.top.size,
    tools = rows.count { it is MainRow.PinnedTool } + (rows.firstNotNullOfOrNull { it as? MainRow.MoreTools }?.count ?: 0),
    games = rows.count { it is MainRow.Pin },
    shelves = shelfBands(rows),
)

internal const val HOME_NEW_DAYS = 30
internal const val HOME_PICK_UP_DAYS = 14
/** The most games a shelf holds: Grid Home shows up to this many ([com.nxredux.mobile.ui.nx.homeShelfCap]), the other Home layouts [HOME_SHELF_LIST_MAX]. */
internal const val HOME_SHELF_MAX = 4
/** The most games Not started holds: as many as the others. */
internal const val HOME_NOT_STARTED_MAX = 4
/** The most games a shelf shows on List, Minimal and Carousel Home. */
internal const val HOME_SHELF_LIST_MAX = 3
private const val SHELF_DAY_MS = 86_400_000L

/** The most games [shelf] holds: Not started [HOME_NOT_STARTED_MAX], the others [HOME_SHELF_MAX] (4 each). */
internal fun homeShelfMax(shelf: HomeShelf): Int = if (shelf == HomeShelf.NOT_STARTED) HOME_NOT_STARTED_MAX else HOME_SHELF_MAX

/**
 * Home's shelves from [library] (every listed game), in page order, empties dropped, each at most [homeShelfMax], no
 * game on two: New (first seen within [HOME_NEW_DAYS] before [now], never after it; newest first, then by name), Not
 * started (no [info] play and not in [recent]: shuffled by [day], the local day's start, so it holds for the day), Pick
 * up again (last played more than [HOME_PICK_UP_DAYS] ago, most played first). [exclude] (Continue's and the pinned
 * games' paths), Unassigned and Android games never show.
 */
internal fun homeShelves(
    library: List<Listed>, info: Map<String, GameInfo>, recent: Set<String>, exclude: Set<String>, now: Long, day: Long,
): List<Pair<HomeShelf, List<Listed>>> {
    val pool = library.filter { !it.system.unassigned && !it.system.android && it.game.path !in exclude }.distinctBy { it.game.path }
    val taken = HashSet<String>()
    fun pick(shelf: HomeShelf, games: List<Listed>) = games.filter { it.game.path !in taken }.take(homeShelfMax(shelf)).onEach { taken += it.game.path }
    val new = pick(HomeShelf.NEW, pool.filter { g -> g.game.firstSeen?.let { (now - it) in 0..HOME_NEW_DAYS * SHELF_DAY_MS } == true }
        .sortedWith(compareByDescending<Listed> { it.game.firstSeen }.thenBy { it.shown.lowercase() }))
    val notStarted = pick(HomeShelf.NOT_STARTED, pool.filter { info[it.game.path]?.lastPlayedAt == null && it.game.path !in recent && it.game.path !in taken }
        .sortedBy { it.game.path }.shuffled(Random(day)))
    val pickUp = pick(HomeShelf.PICK_UP, pool.filter { g -> info[g.game.path]?.lastPlayedAt?.let { now - it > HOME_PICK_UP_DAYS * SHELF_DAY_MS } == true }
        .sortedByDescending { info[it.game.path]?.totalMs ?: 0L })
    return listOf(HomeShelf.NEW to new, HomeShelf.NOT_STARTED to notStarted, HomeShelf.PICK_UP to pickUp).filter { it.second.isNotEmpty() }
}

/**
 * Home's shelves kept between Home rebuilds (a frame report, the stats, a game info refresh all rebuild Home often):
 * [compute] ([homeShelves]) runs again only when the library (the [shelves] call's systems, by identity), the game info
 * (by identity), the recents, the exclusions or the local day changed. Within a day the first build's `now` stands for
 * the rest, so New's 30-day window moves by the day.
 */
internal class HomeShelvesCache(
    private val compute: (List<Listed>, Map<String, GameInfo>, Set<String>, Set<String>, Long, Long) -> List<Pair<HomeShelf, List<Listed>>> = ::homeShelves,
) {
    private var systems: List<SystemFolder>? = null
    private var info: Map<String, GameInfo>? = null
    private var recent: Set<String>? = null
    private var exclude: Set<String>? = null
    private var day: Long? = null
    private var kept: List<Pair<HomeShelf, List<Listed>>> = emptyList()

    /** The shelves for [systems] (every listed game), [info], [recent], [exclude] at [now] on [day] ([homeShelves]). */
    fun shelves(systems: List<SystemFolder>, info: Map<String, GameInfo>, recent: Set<String>, exclude: Set<String>, now: Long, day: Long): List<Pair<HomeShelf, List<Listed>>> {
        if (systems === this.systems && info === this.info && recent == this.recent && exclude == this.exclude && day == this.day) return kept
        kept = compute(systems.flatMap { s -> s.games.map { Listed(s, it) } }, info, recent, exclude, now, day)
        this.systems = systems; this.info = info; this.recent = recent; this.exclude = exclude; this.day = day
        return kept
    }
}

/** The shelves in Home's [rows] and their sizes, in order (consecutive [MainRow.Shelf]s of one [HomeShelf] are one). */
internal fun shelfBands(rows: List<MainRow>): List<Pair<HomeShelf, Int>> {
    val out = ArrayList<Pair<HomeShelf, Int>>()
    for (r in rows) if (r is MainRow.Shelf) {
        if (out.lastOrNull()?.first == r.shelf) out[out.lastIndex] = r.shelf to out.last().second + 1 else out += r.shelf to 1
    }
    return out
}

/** The pinned tools' rows in [pins] order (their [pinKey]s), skipping any this platform does not have (not in [available], the Tools tab's rows) and any uninstalled app. */
internal fun pinnedToolRows(pins: List<String>, available: List<ToolRow>): List<ToolRow> =
    pins.mapNotNull { k -> available.firstOrNull { it.pinKey == k } }
