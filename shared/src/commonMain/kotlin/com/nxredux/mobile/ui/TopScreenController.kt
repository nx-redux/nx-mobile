package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.scraper.LibraryGameRef
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.TopDetails
import com.nxredux.mobile.scraper.StoredGameInfo
import com.nxredux.mobile.ui.nx.TopStats
import com.nxredux.mobile.ui.nx.ListSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.nxredux.mobile.tryOr
import com.nxredux.mobile.tryOrNull
import com.nxredux.mobile.ui.nx.collectionStats
import com.nxredux.mobile.ui.nx.consoleStats
import com.nxredux.mobile.ui.nx.statsKey
import com.nxredux.mobile.ui.nx.detailsRaFor
import com.nxredux.mobile.ui.nx.detailsBody
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nxredux.mobile.scraper.GameInfoStore
import com.nxredux.mobile.library.path

/** What the top screen's pages read from [AppState]: the library as loaded, a list's games and the stored game info. */
internal interface TopScreenHost {
    fun systems(): List<SystemFolder>
    /** A console's games across its folders; main thread only (its cache is the main thread's). */
    fun consoleEntries(systems: List<SystemFolder>): List<Listed>
    suspend fun entriesFor(source: ListSource): List<Listed>
    /** The stored ScreenScraper info; null on a platform without artwork. */
    fun gameInfoStore(): GameInfoStore?
    fun infoFetcher(): InfoFetcher?
    fun artworkRef(tag: String, g: GameEntry): LibraryGameRef
}

/**
 * What the top screen shows while the menu is on two screens: the highlighted game's details ([topDetails]), a
 * console's or a collection's stats ([topStats]), and on one screen or two the highlighted game's stored info
 * ([gameMeta]). Each follows the screen and the selection; a move cancels the read before it.
 */
internal class TopScreenController(
    private val scope: CoroutineScope,
    private val screen: StateFlow<Screen>,
    private val selection: StateFlow<ListSelection>,
    private val dualMenu: StateFlow<Boolean>,
    private val gameInfo: StateFlow<Map<String, GameInfo>>,
    private val deps: AppDeps,
    private val ra: RaScreenController,
    private val host: TopScreenHost,
) {
    private val _gameMeta = MutableStateFlow<Map<String, StoredGameInfo>>(emptyMap())
    /** Stored ScreenScraper info by game path, for the games highlighted so far ([followGameMeta]): a caption's genre and release. */
    val gameMeta: StateFlow<Map<String, StoredGameInfo>> = _gameMeta.asStateFlow()
    private var metaFor: String? = null   // the game path [followGameMeta] last followed

    private val _topDetails = MutableStateFlow<TopDetails?>(null)
    /** The top screen's game details page ([followTopDetails]); null while the top screen shows no game. */
    val topDetails: StateFlow<TopDetails?> = _topDetails.asStateFlow()
    private var topDetailsJob: Job? = null
    private val _topStats = MutableStateFlow<TopStats?>(null)
    /** The top screen's console or collection stats page ([followTopStats]); null while the top screen shows neither. */
    val topStats: StateFlow<TopStats?> = _topStats.asStateFlow()
    private var topStatsJob: Job? = null
    private var topStatsFor: StatsInputs? = null   // the latest computation's inputs: only it writes [topStats]
    private val statsCache = HashMap<String, Pair<TopContent, TopStats>>()   // by [TopStats.key] with its target, for [statsCacheOf]'s library and play time
    private var statsCacheOf: Pair<List<SystemFolder>, Map<String, GameInfo>>? = null
    private val consoleBios = HashMap<String, BiosMissing?>()   // by console key: file reads, kept per library ([readConsoleBios])
    private var consoleBiosLibrary: List<SystemFolder>? = null

    /** What one [topStats] computation was started for: the [target] (as [key]), the library and the play time it read. */
    private class StatsInputs(val key: String, val target: TopContent, val systems: List<SystemFolder>, val info: Map<String, GameInfo>)

    /** [a] and [b] are one stats target built from the same folders (a rescan gives the row new ones under the same tags). */
    private fun sameStatsTarget(a: TopContent, b: TopContent): Boolean = when {
        a is TopContent.ConsoleStats && b is TopContent.ConsoleStats -> a.systems.size == b.systems.size && a.systems.indices.all { a.systems[it] === b.systems[it] }
        else -> a == b
    }

    /**
     * [topStats] on the console or collection [DualMenu.top] shows while the menu is on two screens, else null. Stats
     * built from the same library and play time show again at once ([statsCache]); else they are computed off the main
     * thread, the page shown staying while its own target is recomputed (new play time, a rescan). The next move cancels
     * that computation, and one that ends anyway writes only while it is still the latest ([topStatsFor]).
     */
    fun followTopStats() {
        val top = if (dualMenu.value) DualMenu.top(screen.value, selection.value.index) else TopContent.Backdrop
        val key = statsKey(top)
        if (key == null) {
            topStatsJob?.cancel(); topStatsJob = null; topStatsFor = null
            _topStats.value = null
            return
        }
        val systems = host.systems()
        val info = gameInfo.value
        topStatsFor?.let { if (it.key == key && sameStatsTarget(it.target, top) && it.systems === systems && it.info === info) return }   // computing or shown already
        if (statsCacheOf?.let { it.first === systems && it.second === info } != true) {
            statsCache.clear(); statsCacheOf = systems to info
        }
        if (consoleBiosLibrary !== systems) { consoleBios.clear(); consoleBiosLibrary = systems }
        topStatsJob?.cancel(); topStatsJob = null
        val inputs = StatsInputs(key, top, systems, info)
        topStatsFor = inputs
        statsCache[key]?.takeIf { sameStatsTarget(it.first, top) }?.let { _topStats.value = it.second; return }
        if (_topStats.value?.key != key) _topStats.value = null   // never another target's page meanwhile
        // Built here: consoleEntries' cache belongs to the main thread.
        val console = (top as? TopContent.ConsoleStats)?.let { it.systems to host.consoleEntries(it.systems) }
        topStatsJob = scope.launch {
            val base = if (console != null) {
                val (group, entries) = console
                val stats = withContext(deps.compute) { consoleStats(group, entries, info) }
                val bios = if (consoleBios.containsKey(key)) consoleBios[key] else readConsoleBios(group).also { if (consoleBiosLibrary === systems) consoleBios[key] = it }
                stats.copy(bios = bios)
            } else {
                val name = (top as TopContent.CollectionStats).name
                val entries = tryOr({ emptyList() }) { host.entriesFor(ListSource.Collection(name)) }
                withContext(deps.compute) { collectionStats(name, entries, info) }
            }
            val stats = base.copy(shelf = base.shelf.map { it.copy(resume = shelfResume(it.entry)) })
            // A read that ended after a move (one that ignored the cancel) must not paint over the target now shown.
            if (topStatsFor !== inputs) return@launch
            if (statsCacheOf?.let { it.first === systems && it.second === info } == true) statsCache[key] = top to stats
            _topStats.value = stats
        }
    }

    /** [group]'s BIOS state (required missing on any of its tags wins); none for the Android console. */
    private suspend fun readConsoleBios(group: List<SystemFolder>): BiosMissing? {
        val facts = deps.topFacts ?: return null
        if (group.any { it.android || it.unassigned }) return null
        val bios = group.map { s -> tryOrNull { facts.biosMissing(s.tag) } }
        return bios.firstOrNull { it == BiosMissing.REQUIRED } ?: bios.firstOrNull { it != null }
    }

    /** [entry]'s resume picture for a shelf tile, read only when it has no scraped screenshot (that one is shown first). */
    private suspend fun shelfResume(entry: Listed): String? {
        if (entry.game.screenshotRef != null || entry.system.android || entry.system.unassigned) return null
        return tryOrNull { deps.userData.resumeInfo(entry.system.tag, entry.game.fileName).previewPath }
    }

    /**
     * [topDetails] on the game [DualMenu.top] shows while the menu is on two screens, else null. A move publishes the new
     * game's page at once from what is in memory, then its resume save once read; the next move cancels that read, so a
     * slow one never paints over the game now highlighted. [reread]: the same game's read again (its play time moved:
     * back from a game).
     */
    fun followTopDetails(reread: Boolean = false) {
        val entry = (if (dualMenu.value) DualMenu.top(screen.value, selection.value.index) as? TopContent.GameDetails else null)?.entry
        ra.followRaDetails(entry)
        if (entry == null) {
            topDetailsJob?.cancel(); topDetailsJob = null
            _topDetails.value = null
            return
        }
        val shown = _topDetails.value?.takeIf { sameGame(it.entry, entry) }
        val path = entry.game.path
        _topDetails.value = TopDetails(
            entry, gameInfo.value[path], shown?.resume ?: ResumeInfo.NONE,
            detailsBody(detailsRaFor(entry, ra.detailsRa.value)), shown?.scraped,
        )
        if (shown != null && !reread) return   // its reads ran or are running
        val ref = infoRef(entry)
        topDetailsJob?.cancel()
        topDetailsJob = scope.launch {
            val saves = !entry.system.android && !entry.system.unassigned
            val resume = if (saves) tryOr({ ResumeInfo.NONE }) { deps.userData.resumeInfo(entry.system.tag, entry.game.fileName) } else ResumeInfo.NONE
            val scraped = ref?.let { r -> host.gameInfoStore()?.read(r) }
            val now = _topDetails.value?.takeIf { sameGame(it.entry, entry) } ?: return@launch
            _topDetails.value = now.copy(resume = resume, scraped = scraped)
        }
    }

    /**
     * The highlighted game's stored ScreenScraper info into [gameMeta], for its caption, and the info fetch on it
     * ([InfoFetcher.follow]: a game rested on with none stored is looked up). On one screen or two: the game is the one
     * the top screen's details would show ([DualMenu.top]).
     */
    fun followGameMeta() {
        val entry = (DualMenu.top(screen.value, selection.value.index) as? TopContent.GameDetails)?.entry
        val ref = entry?.let(::infoRef)
        val path = ref?.gamePath
        if (path == metaFor) return
        metaFor = path
        host.infoFetcher()?.follow(ref)
        if (ref == null || path == null || path in _gameMeta.value) return
        scope.launch { host.gameInfoStore()?.read(ref)?.let { _gameMeta.value += path to it } }
    }

    /** [entry] as the info fetch and store name it; null for an Android or Unassigned game (no ScreenScraper info). */
    private fun infoRef(entry: Listed): LibraryGameRef? =
        if (entry.system.android || entry.system.unassigned) null else host.artworkRef(entry.system.tag, entry.game)

    /** [path]'s info was just fetched: the details page shows it when that game is still on it. */
    suspend fun showFetchedInfo(path: String) {
        val entry = (DualMenu.top(screen.value, selection.value.index) as? TopContent.GameDetails)?.entry?.takeIf { it.game.path == path } ?: return
        val scraped = infoRef(entry)?.let { r -> host.gameInfoStore()?.read(r) } ?: return
        _gameMeta.value += path to scraped
        _topDetails.value = _topDetails.value?.takeIf { sameGame(it.entry, entry) }?.copy(scraped = scraped)
    }

    /** Whether [a] and [b] are one game: a rebuilt list gives new [Listed]s for the same files. */
    private fun sameGame(a: Listed, b: Listed): Boolean = a.system.tag == b.system.tag && a.game.path == b.game.path


    /** The game's achievements changed ([r]): the details page shows them in place. */
    fun onDetailsRa(r: RaPanel?) {
        _topDetails.value?.let { d -> _topDetails.value = d.copy(achievements = detailsBody(detailsRaFor(d.entry, r))) }
    }

    /** Game info files may have changed on disk: every highlighted game's info is read again. */
    fun forgetGameMeta() { _gameMeta.value = emptyMap(); metaFor = null }

    /** [stale]'s stats (null: every target's) hold games that changed: computed again, shown in place meanwhile. */
    fun invalidateStats(stale: String?) {
        if (stale == null) statsCache.clear() else statsCache.remove(stale)
        if (stale == null || topStatsFor?.key == stale) { topStatsFor = null; followTopStats() }
    }

    /** Files landed in Bios/: the consoles' "BIOS missing" badges read again. */
    fun forgetBios() { consoleBios.clear(); consoleBiosLibrary = null; statsCache.clear(); statsCacheOf = null }
}
