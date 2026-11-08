package com.nxredux.mobile.ui

import com.nxredux.mobile.library.Collections
import com.nxredux.mobile.library.FolderMerge
import com.nxredux.mobile.library.FolderSummary
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.LibraryScanner
import com.nxredux.mobile.library.LooseFile
import com.nxredux.mobile.library.MoveItem
import com.nxredux.mobile.library.MoveResult
import com.nxredux.mobile.library.RomMover
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.folderSummary
import com.nxredux.mobile.library.homeSummary
import com.nxredux.mobile.library.rewriteLines
import com.nxredux.mobile.library.splitFolders
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.tryOr
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What first setup's folder summaries need from [AppState]: the home, a scan, the ROMs folder offer and the steps' end. */
internal interface FolderSummaryHost {
    fun home(): ContentRef?
    /** Scans [root] and shows the library, then runs [then] after a good scan ([AppState]'s scanNow). */
    suspend fun scanNow(root: ContentRef, then: ((List<SystemFolder>) -> Unit)?)
    /** First setup's ROMs folder offer: Add folder, Skip. */
    fun showAddFirstExtra()
    /** The folder steps are over: on to the rest of onboarding, then the library loads. */
    fun finishFolderSteps()
    /** The library changed (a folder emptied by a move is no longer a source): leaving Library reloads. */
    fun markLibraryDirty()
    /** recent.txt's read-modify-write lock, shared with every other writer. */
    val recentsLock: Mutex
}

/**
 * First setup's folder summaries: the home page after the home pick (what the home holds in `Roms/` and outside it, and
 * the move that tidies it), and an added ROMs folder's summary with its move into the home. A move's progress shows on the
 * page; B stops it after the current game ([moveCancelled]).
 */
internal class FolderSummaryController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val deps: AppDeps,
    private val scanner: LibraryScanner,
    private val library: LibraryIndexHolder,
    private val collections: Collections,
    private val host: FolderSummaryHost,
) {
    var homePage: Screen.FolderSummary? = null   // first setup's home page, when the home held games: B on the next step returns to it
    var homeOutside: List<LooseFile> = emptyList()   // that home's games outside Roms
    @Volatile var moveCancelled = false   // B while a summary's move runs: it stops after the current game
        private set

    /** First setup starts over: no home page to return to and no outside games planned. */
    fun reset() {
        homePage = null
        homeOutside = emptyList()
    }

    /** B while a summary's move runs: the move stops after the current game. */
    fun cancelMove() {
        moveCancelled = true
    }
    /**
     * First setup's home page after the pick's scan: what [home] holds, in `Roms/` and outside it (its outside games
     * found as an extra folder's are). A home with no game skips it, to the ROMs folder offer.
     */
    fun showHomePage(home: ContentRef) {
        screen.value = Screen.Loading
        scope.launch {
            val outside = tryOr({ emptyList() }) { scanner.scanOutsideRoms(home, deps.settings.defaultTags) }
            val index = library.snapshot ?: return@launch host.showAddFirstExtra()
            homeOutside = outside
            val summary = homeSummary(index, outside).copy(split = splitFolders(index).size)
            if (summary.games == 0) { homePage = null; return@launch host.showAddFirstExtra() }
            showHomeSummary(summary)
        }
    }

    /** The home page with [summary] (after a move: what the home held before it, and the move's [result]). */
    private fun showHomeSummary(summary: FolderSummary, result: MoveResult? = null) {
        val name = host.home()?.let { deps.source.displayName(it) } ?: ""
        val s = Screen.FolderSummary(Recents.HOME, name, name, summary, move = result == null && homeTidy(summary), result = result)
        homePage = s
        screen.value = s
        selection.value = ListSelection(0, folderSummaryRows(s).size)
    }

    /** Whether the home page has anything to move: games outside `Roms/`, or a console split over several folders. */
    private fun homeTidy(summary: FolderSummary) = summary.outside > 0 || summary.split > 0

    /**
     * The home page's move: its outside games into `Roms/` (the folders they empty removed), then each split console's
     * other folders merged into its main one, every line naming a merged game rewritten (Recents, pins, hidden games,
     * collections), then a rescan and the result.
     */
    private fun moveHomeOutside(s: Screen.FolderSummary) = launchMove(s) { home, index, mover ->
        // Each merged game's old line → new, filled as it moves: whatever ends the move (a stop, an error, the app
        // closing), every line naming a game that did move is rewritten to it.
        val moved = LinkedHashMap<String, String>()
        // The lines of the games moved so far, rewritten every few seconds too: a process killed mid-merge leaves
        // only the last few seconds' games at their old paths, not all of them.
        val rewritten = HashSet<String>()
        suspend fun flushLines() {
            val fresh = moved.filterKeys { it !in rewritten }
            if (fresh.isEmpty()) return
            runCatching { rewriteHomeLines(fresh) }.onSuccess { rewritten += fresh.keys }
        }
        val flusher = launch { while (true) { delay(LINES_FLUSH_MS); withContext(NonCancellable) { flushLines() } } }
        var outside = MoveResult()
        val result = try {
            val items = mover.planLoose(homeOutside, index)
            val movedItems = HashSet<MoveItem>()
            outside = mover.run(items, ::moveProgress, onMoved = { movedItems += it }) { moveCancelled }
            // Folders where a game (or a file nothing placed) stayed are never tried: a wrong empty listing must not delete them.
            val keep = homeOutside.filter { f -> movedItems.none { it.relativeDir == f.relativeDir && (it.subdir != null || f.entry.name in it.files) } }
            mover.pruneEmpty(home, homeOutside.map { it.relativeDir }, keep = keep.map { it.relativeDir })
            val merged = if (outside.cancelled) MoveResult() else FolderMerge(deps.source, home).merge(index, splitFolders(index), moved, ::moveProgress) { moveCancelled }.first
            MoveResult(outside.moved + merged.moved, outside.clashed + merged.clashed, outside.failed + merged.failed, outside.cancelled || merged.cancelled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // What moved is counted; the rest (outside and merge) stays where it was.
            outside.copy(moved = outside.moved + moved.size, failed = outside.failed + 1)
        } finally {
            flusher.cancel()
            withContext(NonCancellable) { flushLines() }
        }
        homeOutside = emptyList()
        host.scanNow(home, then = { showHomeSummary(s.summary, result) })
    }

    /** A move from [s]: its progress on the page from 0, then [move] with the home, the library and a mover into the home. */
    private fun launchMove(s: Screen.FolderSummary, move: suspend CoroutineScope.(home: ContentRef, index: LibraryIndex, mover: RomMover) -> Unit) {
        val home = host.home() ?: return
        val index = library.snapshot ?: return
        moveCancelled = false
        screen.value = s.copy(progress = 0 to 0)
        selection.value = ListSelection(0, 0)
        scope.launch { move(home, index, RomMover(deps.source, home)) }
    }

    /** A move's progress, on the summary page while it shows. */
    private fun moveProgress(done: Int, total: Int) {
        (screen.value as? Screen.FolderSummary)?.let { screen.value = it.copy(progress = done to total) }
    }

    /** Every saved line naming a game the merge [moved] (old path → new) follows it: Recents, pins, hidden games, collections. */
    private suspend fun rewriteHomeLines(moved: Map<String, String>) {
        host.recentsLock.withLock {
            val recents = deps.userData.readRecents()
            val next = recents.map { r -> moved[r.path]?.let { r.copy(path = it) } ?: r }
            if (next != recents) deps.userData.writeRecents(next)
        }
        val pins = deps.userData.readPins()
        pins.map { p -> moved[p.path]?.let { p.copy(path = it) } ?: p }.takeIf { it != pins }?.let { deps.userData.writePins(it) }
        val hidden = deps.userData.readHidden()
        rewriteLines(hidden, moved).takeIf { it != hidden }?.let { deps.userData.writeHidden(it) }
        for (name in collections.names()) {
            val lines = collections.readOrNull(name) ?: continue
            val next = rewriteLines(lines, moved)
            if (next != lines) collections.writeMerged(name, next, lines)
        }
    }

    /**
     * First setup's summary of the folder [extraId] just added, from the library its scan stored, on Continue. After a
     * move, [before] (what the folder held) stays the summary, so the page still says what was found.
     */
    fun showFolderSummary(extraId: String, result: MoveResult? = null, before: FolderSummary? = null) {
        val index = library.snapshot ?: return host.finishFolderSteps()
        val extra = deps.settings.extraFolders.firstOrNull { it.id == extraId }
        val name = extra?.let { deps.source.displayName(it.ref) } ?: ""
        val home = host.home()?.let { deps.source.displayName(it) } ?: ""
        val summary = before ?: folderSummary(index, extraId)
        val s = Screen.FolderSummary(extraId, name, home, summary, move = result != null, result = result)
        screen.value = s
        val rows = folderSummaryRows(s)
        selection.value = ListSelection(0, rows.size)   // on the first row: the Move option where it is offered, else Continue
    }

    /**
     * A or a tap on the summary's row [index]: Move toggles; Continue with Move on asks first ([Screen.FolderSummary.confirmMove]),
     * else goes on.
     */
    fun folderSummaryRow(s: Screen.FolderSummary, index: Int) {
        if (s.progress != null) return
        when (folderSummaryRows(s).getOrNull(index)?.kind) {
            SummaryRowKind.MOVE -> { screen.value = s.copy(move = !s.move); return }
            SummaryRowKind.CONTINUE, null -> Unit
        }
        if (folderMoveAsk(s) != null) { screen.value = s.copy(confirmMove = true); return }
        folderSummaryContinue(s)
    }

    /** The summary's Continue: the move when it is on (and confirmed), else on to the next step. */
    private fun folderSummaryContinue(s: Screen.FolderSummary) {
        if (s.extraId == Recents.HOME) {
            if (s.move && s.result == null && homeTidy(s.summary)) moveHomeOutside(s) else host.showAddFirstExtra()
            return
        }
        if (s.move && s.result == null && s.summary.games > s.summary.unassigned) return moveSummaryGames(s)
        host.finishFolderSteps()
    }

    /** The move's confirmation over the summary: A moves, B goes back to the page (Move stays on). */
    fun onFolderMoveConfirm(s: Screen.FolderSummary, button: NxButton) {
        when (button) {
            NxButton.A -> folderSummaryContinue(s.copy(confirmMove = false))
            NxButton.B -> screen.value = s.copy(confirmMove = false)
            else -> Unit
        }
    }

    /** The summary's move: [s]'s placed games into the home's Roms, its progress on the page, then a rescan and the result. */
    private fun moveSummaryGames(s: Screen.FolderSummary) = launchMove(s) { home, index, mover ->
        // An unexpected error is a result too (nothing moved past it), never a crash of the app's scope.
        val result = tryOr({ MoveResult(failed = s.summary.games - s.summary.unassigned) }) {
            mover.run(mover.plan(index, s.extraId), ::moveProgress) { moveCancelled }
        }
        host.scanNow(home, then = {
            // Emptied by the move: the folder is no longer a source.
            val extra = deps.settings.extraFolders.firstOrNull { it.id == s.extraId }
            if (extra != null && library.snapshot?.let { folderSummary(it, s.extraId).games } == 0) {
                deps.settings.extraFolders = deps.settings.extraFolders - extra
                deps.folderPicker.forget(extra.ref)
                host.markLibraryDirty()
            }
            showFolderSummary(s.extraId, result, before = s.summary)
        })
    }
}

/** How often a home move rewrites the lines of the games it moved so far. */
private const val LINES_FLUSH_MS = 3_000L
