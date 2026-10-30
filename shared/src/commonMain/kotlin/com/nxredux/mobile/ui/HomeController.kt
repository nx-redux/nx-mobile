package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.homeShelfCap
import com.nxredux.mobile.achievements.RaController
import com.nxredux.mobile.gametime.GameTimeData
import com.nxredux.mobile.gametime.GameTotal
import com.nxredux.mobile.gametime.LocalCalendar
import com.nxredux.mobile.library.path
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.ui.nx.HomeCarouselMove
import com.nxredux.mobile.ui.nx.HomeFrame
import com.nxredux.mobile.ui.nx.HomeMemory
import com.nxredux.mobile.ui.nx.HomeShape
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.homeAtTop
import com.nxredux.mobile.ui.nx.homeBoard
import com.nxredux.mobile.ui.nx.homeCarouselStep
import com.nxredux.mobile.ui.nx.homeClamp
import com.nxredux.mobile.ui.nx.homeRemember
import com.nxredux.mobile.ui.nx.homeShape
import com.nxredux.mobile.ui.nx.homeStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Home's frame before its screen reports one: a 960 × 344 landscape stand-in. */
private val HOME_STAND_IN = HomeFrame(960f, 344f, false)

/** How many Home builds [HomeController] keeps, to know the frame and strip lines older rows on screen were built for. */
private const val HOME_BUILDS_KEPT = 8

/** One Home build: its [rows], their [view], and the [frame] and strip [lines] its board was made on. */
private data class HomeBuild(val rows: List<MainRow>, val view: HomeView, val frame: HomeFrame, val lines: Int)

/**
 * What [Home] was built from (to rebuild it with new stats or a new frame, no I/O): [recent] are recent.txt's paths, for
 * Not started; [shots] the hidden save states' frames by game path ([HomeShot]), for the pins and the shelves.
 */
internal data class HomeInputs(
    val last: MainRow.Continue?, val games: List<Listed>, val tools: List<ToolRow>, val recent: Set<String>,
    val shots: Map<String, String> = emptyMap(),
)

/**
 * [row], a Home row, in the new Home [rows]: itself; else "+N" for "+N" (its count follows the slots), "+N" for a
 * pinned tool the slots now hide, the last tool square for a "+N" whose tools now all fit, a shelf game's pin when it
 * was just pinned ([pinnedTools]: the tools pinned now). Null: gone.
 */
internal fun homeRowIn(row: MainRow?, rows: List<MainRow>, pinnedTools: List<ToolRow>?): Int? {
    if (row == null) return null
    rows.indexOf(row).takeIf { it >= 0 }?.let { return it }
    val more = rows.indexOfFirst { it is MainRow.MoreTools }.takeIf { it >= 0 }
    return when (row) {
        is MainRow.MoreTools -> more ?: rows.indexOfLast { it is MainRow.PinnedTool }.takeIf { it >= 0 }
        is MainRow.PinnedTool -> more?.takeIf { pinnedTools?.contains(row.row) == true }   // still pinned: hidden
        is MainRow.Shelf -> rows.indexOfFirst { it is MainRow.Pin && it.entry.game.path == row.entry.game.path }.takeIf { it >= 0 }
        else -> null
    }
}

/**
 * Continue's index (0) when [was], a pin or shelf game no longer in Home's [rows], is the game now on Continue: a
 * launched one leaves the rows as the last game, and the focus follows it there. Null otherwise.
 */
internal fun homeLaunchedPin(was: MainRow?, rows: List<MainRow>): Int? {
    val entry = (was as? MainRow.Pin)?.entry ?: (was as? MainRow.Shelf)?.entry ?: return null
    if (was in rows) return null
    val cont = rows.firstOrNull() as? MainRow.Continue ?: return null
    return 0.takeIf { cont.entry.game.path == entry.game.path }
}

/** What Home needs from [AppState]: its layout, the clock, the library and the main menu's tab moves. */
internal interface HomeHost {
    /** Home's layout (Settings -> Layouts). */
    fun homeStyle(): MenuStyle
    fun clockNow(): Long
    /** Signed in to RetroAchievements, for the stats strip. */
    fun raSignedIn(): Boolean
    /** [path] against the library (no SAF call): the most played game's name. */
    fun resolveOne(path: String): Listed?
    /** Home's shelves for [inputs] now ([AppState]'s shelvesFor). */
    fun shelvesFor(inputs: HomeInputs): List<Pair<HomeShelf, List<Listed>>>
    /** A shelf game's own badge ([MainRow.Shelf.tag]): Most played's play time; null for the rest. */
    fun shelfTag(shelf: HomeShelf, game: Listed): String? = null
    /** The Home tab rebuilt from what it was last built from (new stats). */
    fun rebuildHomeTab()
    fun refreshResume()
    /** The tab [step] away (past a LEFT / RIGHT edge). */
    fun switchTab(step: Int)
}

/**
 * The Home tab's model and focus: its builds (rows and view for each frame), the stats strip, the d-pad memory (Grid,
 * Carousel and Minimal) and the remembered Home row. The tabs themselves stay in [AppState]; the rows Home is built
 * from come in as [homeInputs].
 */
internal class HomeController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val calendar: LocalCalendar,
    private val gameTime: GameTimeData?,
    private val achievements: RaController?,
    private val compute: CoroutineContext,
    private val host: HomeHost,
) {
    /** What Home was last built from ([AppState]'s main rows set it). */
    var homeInputs: HomeInputs? = null
    var homeFrame: HomeFrame? = null   // Home's body as its screen last reported it (null: not laid out yet)
    private val _homeView = MutableStateFlow(HomeView(null, 1, 0, 0))
    /** What Home draws beside its rows (the strip, the top section's size, the pinned counts), for the rows on screen. */
    val homeView: StateFlow<HomeView> = _homeView.asStateFlow()
    /** The last Home builds, newest first ([HOME_BUILDS_KEPT]): rows on screen may be older than the newest (a menu holds the swap back). */
    private val homeBuilds = ArrayDeque<HomeBuild>()
    /** Home's stats strip: the last result, kept while a newer one computes; "…" until the first (no achievements signed out). */
    private var homeStats = HomeStats(null, null, signedIn = host.raSignedIn(), top = null, computed = false)
    private var homeStatsDay = Long.MIN_VALUE   // the local day (its midnight) the last stats computation was asked on
    private var homeStatsJob: Job? = null
    private var homeStatsDirty = false   // a refresh was asked for while one ran: it runs once more
    private var homeStatsAsked = false   // the first menu show asked for them
    private val _homeCarouselRowLast = MutableStateFlow(0)
    /**
     * The Carousel Home's row item last on: UP from the dock lands there, and the carousel shows it while the dock has
     * the focus ([com.nxredux.mobile.ui.nx.homeCarouselShownRow]). One memory for both, so they agree after Home leaves and comes back.
     */
    val homeCarouselRowLast: StateFlow<Int> = _homeCarouselRowLast.asStateFlow()
    private var homeRowLast: Int
        get() = _homeCarouselRowLast.value
        set(v) { _homeCarouselRowLast.value = v }
    private var homeDockLast: Int? = null   // its dock square last on
    private var homeCarouselRows = -1   // the row count they were taken in
    private var homeMemory = HomeMemory()   // Home's d-pad memory: the top tile last on, the one left before it, the pin last on
    private var homeLastIndex = -1   // Home's focus as [homeMemory] last noted it (in [homeMemoryRows]), the move's start
    private var homeSelShape: HomeShape? = null   // the Home shape the remembered Home row (tabSelections) was taken in
    private var homeSelRow: MainRow? = null   // the remembered Home row itself
    private var homeMemoryRows: List<MainRow>? = null   // the Home rows [homeMemory]'s indices were taken in

    /** Home's frame now: the last reported one, before one [HOME_STAND_IN]. */
    private fun homeFrameNow(): HomeFrame = homeFrame ?: HOME_STAND_IN

    /** Home's board for [tools], [games] and the large face's [shelves] on [frame] (the frame now) with the strip's [stripLines]. */
    private fun homeBoardFor(stripLines: Int, tools: Int, games: Int, frame: HomeFrame = homeFrameNow(), shelves: List<Pair<HomeShelf, Int>> = emptyList()) =
        homeBoard(frame, stripLines, tools, games, shelves)

    /** The strip's lines: two, or one with no Most played ([homeStripLines]). */
    private fun homeLinesFor(strip: HomeStrip?) = homeStripLines(strip)

    /**
     * Home from its [inputs], its slots from the board they make on the frame ([homeBoardFor]; the
     * [com.nxredux.mobile.ui.nx.HomeToolMode.SPS3] split and the portrait type grow with the tools), with Home's shelves
     * ([HomeHost.shelvesFor]) and every landscape face's inline games (the board's inlineGames).
     */
    fun homeFor(inputs: HomeInputs): Home {
        val (last, games, tools) = inputs
        val strip = homeStrip(homeStats).takeIf { last != null || games.isNotEmpty() || tools.isNotEmpty() }
        val picked = host.shelvesFor(inputs)
        // List, Minimal and Carousel: up to [HOME_SHELF_LIST_MAX] a shelf; Grid: what its board shows ([homeShelfCap]).
        val shelves = picked.map { (shelf, games) -> shelf to games.take(HOME_SHELF_LIST_MAX) }
        when (host.homeStyle()) {
            // List and Minimal: every tool is a row. Carousel: the dock's squares, "+N" for the rest.
            MenuStyle.LIST, MenuStyle.MINIMAL -> return buildHome(last, games, tools, homeStats, Int.MAX_VALUE, shelves, tag = host::shelfTag, shot = { inputs.shots[it.game.path] })
            MenuStyle.CAROUSEL, MenuStyle.BACKDROP -> {
                // The dock's squares are Grid Home's pinned tool squares on this frame ([homeDockSide]).
                val f = homeFrameNow()
                val side = homeDockSide(f, homeLinesFor(strip), games.count { it.game.path != last?.entry?.game?.path })
                return buildHome(last, games, tools, homeStats, homeDockFit(f, side), shelves, tag = host::shelfTag, shot = { inputs.shots[it.game.path] })
            }
            MenuStyle.GRID -> Unit
        }
        val gamesLeft = games.count { it.game.path != last?.entry?.game?.path }
        val lines = homeLinesFor(strip)
        // The board caps each shelf to what its face shows ([homeShelfCap]); its sizes follow the non-empty shelves in order.
        val b = homeBoardFor(lines, tools.size, gamesLeft, shelves = picked.map { (shelf, games) -> shelf to games.size })
        var k = 0
        val gridShelves = picked.map { (shelf, games) -> shelf to if (games.isEmpty()) games else games.take(b.shelfSizes.getOrElse(k++) { 0 }) }
        return buildHome(last, games, tools, homeStats, b.slots, gridShelves, b.inlineGames, tag = host::shelfTag, shot = { inputs.shots[it.game.path] })
    }

    /** [built] is the Home tab now: its view shows once the screen shows its rows ([publishHomeView]). */
    fun noteHomeBuilt(built: Home) {
        val rows = homeTabRows(built, host.homeStyle())
        val lines = homeLinesFor(built.strip)
        // The view carries the frame and lines the rows were built for: HomeScreen places them on that board.
        homeBuilds.addFirst(HomeBuild(rows, homeView(built, rows).copy(frame = homeFrame, lines = lines), homeFrameNow(), lines))
        while (homeBuilds.size > HOME_BUILDS_KEPT) homeBuilds.removeLast()
        publishHomeView()
    }

    /**
     * Publishes the view of the Home rows on screen: the newest build's off the main menu, else the build of the rows
     * the menu shows (older ones while a menu holds the swap back, or a restored snapshot: the switcher, Game Settings).
     * HomeScreen splits the rows it draws with it, so the two always match. Call it after every assignment of a
     * [Screen.Systems].
     */
    fun publishHomeView() {
        val newest = homeBuilds.firstOrNull() ?: return
        val shown = (screen.value as? Screen.Systems)?.tabs?.firstOrNull { it.category == MenuCategory.HOME }?.rows
        val build = if (shown == null) newest else homeBuilds.firstOrNull { it.rows == shown } ?: return
        _homeView.value = build.view
    }

    /**
     * Recomputes Home's stats on [AppDeps.compute]: over the calendar month so far (from local 00:00 on the 1st,
     * [LocalCalendar.monthStart]), the play time and most played game, then the achievements unlocked in it over the
     * games played in it. The previous values stay on Home's strip until the result replaces them. One computation at a
     * time: asked for while one runs, it runs once more when that one ends.
     */
    private fun refreshHomeStats() {
        homeStatsDay = calendar.dayStart(host.clockNow())
        if (homeStatsJob?.isActive == true) { homeStatsDirty = true; return }
        homeStatsJob = scope.launch {
            do {
                homeStatsDirty = false
                homeStats = computeHomeStats() ?: homeStats.takeIf { it.computed } ?: homeStats.copy(computed = true)
                host.rebuildHomeTab()
            } while (homeStatsDirty)
        }
    }

    /**
     * Home's stats now, or null when the play-time read failed (the caller keeps what it had). The most played game goes
     * by its library name when its path is still in the library, else by Game Tracker's.
     */
    private suspend fun computeHomeStats(): HomeStats? {
        val signedIn = host.raSignedIn()
        val data = gameTime ?: return HomeStats(null, null, signedIn, null, computed = true)
        val now = host.clockNow()
        val computed = try {
            withContext(compute) {
                val since = calendar.monthStart(now)   // local 00:00 on the 1st, DST-safe
                val window = data.window(since, now)
                // RA pointers and game time both key a game by its library path (GamePath.of), so the paths pass as they are.
                val unlocks = if (signedIn && window.totalMs > 0L) achievements?.unlocksSince(since, data.playedPaths(since, now)) else null
                window to unlocks
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val (window, unlocks) = computed
        val named = { t: GameTotal -> host.resolveOne(t.romPath)?.let { t.copy(name = it.game.shownName) } ?: t }
        val top = window.top?.let(named)
        return HomeStats(window.totalMs, unlocks, signedIn, top, computed = true)
    }

    /**
     * The Home tab's [HomeShape] from its [rows] ([homeTabRows]): the board their own counts make on the frame and strip
     * lines they were built for (the newest build of equal rows, so rows on screen get the frame now), else on the frame now.
     */
    private fun homeShapeOf(rows: List<MainRow>): HomeShape {
        val build = homeBuilds.firstOrNull { it.rows == rows }
        val tools = rows.count { it is MainRow.PinnedTool } + (rows.firstNotNullOfOrNull { it as? MainRow.MoreTools }?.count ?: 0)
        val lines = build?.lines ?: homeLinesFor(homeBuilds.firstOrNull()?.view?.strip ?: _homeView.value.strip)
        return homeShape(homeBoardFor(lines, tools, rows.count { it is MainRow.Pin }, build?.frame ?: homeFrameNow(), shelfBands(rows)))
    }

    /** [row] in the new Home [rows] ([homeRowIn]), a pinned tool counted as still pinned by [homeInputs]. */
    fun homeFind(row: MainRow?, rows: List<MainRow>): Int? = homeRowIn(row, rows, homeInputs?.tools)

    /**
     * Home's focus is on [index] in [rows], moved there from the focus last noted ([homeLastIndex], in the same rows; a
     * new set of rows is no move): [homeRemember] keeps the top tile last on (UP from the rows), the one left before it
     * (a tie's way back) and the pin last on.
     */
    fun noteHomeFocus(index: Int, rows: List<MainRow>, moveFrom: Int = homeLastIndex) {
        if (host.homeStyle() != MenuStyle.GRID) { noteCarouselFocus(index, rows); refreshStatsOnNewDay(); return }
        val same = homeMemoryRows == rows
        fitHomeMemory(rows)
        val from = if (same && moveFrom >= 0) moveFrom else index
        homeMemory = homeRemember(homeMemory, from = from, to = index, shape = homeShapeOf(rows))
        homeLastIndex = index
        refreshStatsOnNewDay()
    }

    /** The Carousel Home's focus is on [index] in [rows]: the row's item or the dock square last used (reset when the row count changed). */
    private fun noteCarouselFocus(index: Int, rows: List<MainRow>) {
        val rowCount = homeCarouselRowCount(rows)
        if (rowCount != homeCarouselRows) { homeCarouselRows = rowCount; homeRowLast = 0; homeDockLast = null }
        if (index < rowCount) homeRowLast = index else homeDockLast = index
    }

    /**
     * Home is shown or its focus moved: once the stats were asked for, a new local day since (Home left open past
     * midnight, maybe into a new month) recomputes them. Only a date compare otherwise.
     */
    private fun refreshStatsOnNewDay() {
        if (homeStatsAsked && calendar.dayStart(host.clockNow()) != homeStatsDay) refreshHomeStats()
    }

    /** A foreground: Home's stats again once the menu's first show asked for them (before it, that show computes them). */
    fun refreshStatsIfAsked() { if (homeStatsAsked) refreshHomeStats() }

    /**
     * The main menu showed [category]: its first show computes Home's stats (then only a foreground does: a tab switch
     * never does), and Home showing again after a sign-in or sign-out (in a tool) does too, so the strip's achievements
     * follow it. The flag is not flipped here: signed in, the strip needs the count, not a "0".
     */
    fun menuShown(category: MenuCategory?) {
        if (!homeStatsAsked) { homeStatsAsked = true; refreshHomeStats() }
        else if (category == MenuCategory.HOME && host.raSignedIn() != homeStats.signedIn) refreshHomeStats()
    }

    /** Home's row [index] of [rows] is remembered with its tab's selection: the row itself, and on a Grid Home the shape it was taken in. */
    fun rememberHomeRow(rows: List<MainRow>, index: Int) {
        homeSelShape = if (host.homeStyle() == MenuStyle.GRID) homeShapeOf(rows) else null; homeSelRow = rows.getOrNull(index)
    }

    /**
     * Home's remembered row [r] in [rows]: the same row where it still shows (a rotation may have moved its index, the
     * slots following the frame); else Continue for a launched pin; else moved with [homeClamp] when its shape changed
     * since it was remembered.
     */
    fun rememberedRow(r: ListSelection, rows: List<MainRow>): ListSelection {
        homeFind(homeSelRow, rows)?.let { return ListSelection(it, rows.size) }
        homeLaunchedPin(homeSelRow, rows)?.let { return ListSelection(it, rows.size) }
        if (host.homeStyle() != MenuStyle.GRID) return r
        val old = homeSelShape ?: return r
        val new = homeShapeOf(rows)
        return if (old == new) r else ListSelection(homeClamp(r.index, old, new), rows.size)
    }

    /**
     * The row to keep when Home [from] is rebuilt as [to] ([AppState]'s keptIndex on Home): with a new shape (a pin gone,
     * other columns) it clamps into its section ([homeClamp]); a shelf game follows itself, or its pin when it was just
     * pinned ([homeFind]). The Carousel Home keeps a row item in the row (a gone last card lands on the one before it,
     * never on the dock's first square).
     */
    fun keptIndex(from: Screen.Systems, to: Screen.Systems): Int {
        (from.rows.getOrNull(selection.value.index) as? MainRow.Shelf)?.let { homeFind(it, to.rows) }?.let { return it }
        when (host.homeStyle()) {
            MenuStyle.LIST, MenuStyle.MINIMAL -> return selection.value.index
            MenuStyle.CAROUSEL, MenuStyle.BACKDROP -> {
                val i = selection.value.index
                val rowCount = homeCarouselRowCount(to.rows)
                return if (i < homeCarouselRowCount(from.rows) && rowCount > 0) i.coerceAtMost(rowCount - 1) else i
            }
            MenuStyle.GRID -> Unit
        }
        val old = homeShapeOf(from.rows)
        val new = homeShapeOf(to.rows)
        return if (old == new) selection.value.index else homeClamp(selection.value.index, old, new)
    }

    /**
     * [homeMemory], taken in [homeMemoryRows], moved into [rows] when they changed: each remembered tile onto the same
     * row where it still shows in its section (a rotation changes the slots, so the rows' indices shift and a tool may go
     * behind "+N"; a pin change shifts them too); otherwise [homeClamp] moves it by place (onto the pin after it), or it is forgotten.
     */
    private fun fitHomeMemory(rows: List<MainRow>) {
        val old = homeMemoryRows
        homeMemoryRows = rows
        if (old == null || old == rows) return
        val oldShape = homeShapeOf(old)
        val shape = homeShapeOf(rows)
        fun moved(i: Int, top: Boolean): Int {
            if (i < 0) return -1
            val same = homeFind(old.getOrNull(i), rows)?.takeIf { (it < shape.topCount) == top }
            return same ?: homeClamp(i, oldShape, shape).takeIf { (it < shape.topCount) == top } ?: -1
        }
        homeMemory = HomeMemory(
            lastTop = moved(homeMemory.lastTop, top = true).coerceAtLeast(0),
            prevTop = moved(homeMemory.prevTop, top = true),
            lastPin = moved(homeMemory.lastPin, top = false),
        )
    }

    /**
     * The d-pad on the Home tab ([homeStep]): the top split and the Pinned rows, with [homeMemory]. UP with nothing above
     * ([homeAtTop]) reaches the tab row. Past a LEFT / RIGHT edge it switches tab on a fresh press only (never a key
     * [repeat]: a held key stops at the edge). False off Home (or with a menu open).
     */
    fun homeMove(button: NxButton, repeat: Boolean): Boolean {
        val s = screen.value as? Screen.Systems ?: return false
        if (s.category != MenuCategory.HOME || s.menu != null) return false
        when (host.homeStyle()) {
            // The generic List step: UP / DOWN wrap, LEFT / RIGHT switch tab. A press on Home is used: a new day recomputes the stats.
            MenuStyle.LIST -> { refreshStatsOnNewDay(); return false }
            MenuStyle.CAROUSEL, MenuStyle.BACKDROP -> return homeCarouselMove(s, button, repeat)
            MenuStyle.MINIMAL -> return homeMinimalMove(s, button, repeat)
            MenuStyle.GRID -> Unit
        }
        val shape = homeShapeOf(s.rows)
        fitHomeMemory(s.rows)   // the pins may have changed since the memory was taken
        val sel = selection.value
        if (button == NxButton.UP && homeAtTop(sel.index, shape, homeMemory)) return true   // the top: it stays
        val next = homeStep(sel.index, shape, homeMemory, button)
        if (next == null) { if (!repeat) host.switchTab(if (button == NxButton.RIGHT) 1 else -1); return true }
        if (next != sel.index) {
            selection.value = sel.copy(index = next)
            noteHomeFocus(next, s.rows, moveFrom = sel.index)
            host.refreshResume()
        }
        return true
    }

    /**
     * The d-pad on Minimal Home: UP / DOWN step one name, as every Minimal page's (the List step): past an end a fresh
     * press wraps (UP on the first to the last, DOWN on the last to the first), a held key's [repeat] stops there.
     * LEFT / RIGHT switch tab on a fresh press only. False for any other button.
     */
    private fun homeMinimalMove(s: Screen.Systems, button: NxButton, repeat: Boolean): Boolean {
        refreshStatsOnNewDay()
        val sel = selection.value
        when (button) {
            NxButton.LEFT, NxButton.RIGHT -> if (!repeat) host.switchTab(if (button == NxButton.RIGHT) 1 else -1)
            NxButton.UP, NxButton.DOWN -> {
                val next = if (button == NxButton.UP) sel.up(wrap = !repeat) else sel.down(wrap = !repeat)
                if (next.index != sel.index) { selection.value = next; noteHomeFocus(next.index, s.rows); host.refreshResume() }
            }
            else -> return false
        }
        return true
    }

    private fun homeCarouselMove(s: Screen.Systems, button: NxButton, repeat: Boolean): Boolean {
        val sel = selection.value
        val rowCount = homeCarouselRowCount(s.rows)
        when (val m = homeCarouselStep(sel.index, rowCount, s.rows.size, button, homeRowLast, homeDockLast)) {
            is HomeCarouselMove.To -> if (m.index != sel.index) { selection.value = sel.copy(index = m.index); noteHomeFocus(m.index, s.rows); host.refreshResume() }
            is HomeCarouselMove.SwitchTab -> if (!repeat) host.switchTab(m.step)
            HomeCarouselMove.Stay -> Unit
        }
        return true
    }
}
