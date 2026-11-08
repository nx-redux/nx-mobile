package com.nxredux.mobile.ui

import com.nxredux.mobile.library.ArcadeCompanions
import com.nxredux.mobile.library.CompanionLookup
import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.Sidecars
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.runner.ClassicNes
import com.nxredux.mobile.runner.CoreDescriptor
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.runner.LaunchName
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.SaveTransferRules
import com.nxredux.mobile.storage.Recent
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.LaunchTimeline
import com.nxredux.mobile.ui.nx.ListSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What launches need from [AppState]: Kids Mode's lock, the menu's push and rows, an app's start, and the index. */
internal interface LaunchHost {
    fun kidsLocked(): Boolean
    /** A launch happens: a hidden tab pushed over the menu is dropped (the menu is its own tab after a game). */
    fun dropPushedTab()
    /** An Android game's app started ([AppsController.launchApp]). */
    fun launchApp(entry: Listed): Boolean
    fun showNotice(text: String)
    fun rememberMenuSelection()
    /** [game] moved from [from] to [toTag] in the index (a Classic NES cart now recorded as MGBA); true when it moved. */
    suspend fun moveInIndex(game: GameEntry, from: SystemFolder, toTag: String): Boolean
    suspend fun refreshOpenList(select: String?)
    fun openMainRow(s: Screen.Systems, index: Int, forward: NavMotion = NavMotion.FORWARD): Job?
    fun clockNow(): Long
}

/**
 * Starting games: the file and companion checks before the runner, a Classic NES cart's switch to mGBA, resume from the
 * last save, the highlighted game's resume marker, and a Minimal page's open (a folder's fade, a game's or tool's launch
 * sequence over the hand-off to the game).
 */
internal class LaunchController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val error: MutableStateFlow<String?>,
    private val deps: AppDeps,
    private val sidecars: Sidecars,
    private val switcher: SwitcherController,
    private val host: LaunchHost,
) {
    private val _minimalOpening = MutableStateFlow<MinimalOpening?>(null)

    /**
     * A Minimal page's folder being opened, while the other names fade and a moment after ([openMinimal]); null otherwise.
     * Input waits until the row opens ([minimalPending]). A game or a tool runs [launchSequence] instead.
     */
    val minimalOpening: StateFlow<MinimalOpening?> = _minimalOpening.asStateFlow()

    /** A Minimal open is under way (a folder's names fading, a launch's spread and hand-off): input waits till it lands. */
    var minimalPending = false
        private set
    private var minimalJob: Job? = null
    private val _launchSequence = MutableStateFlow<LaunchSequence?>(null)

    /**
     * A Minimal game's or tool's launch ([openMinimal]): black spreads from its name for [LaunchTimeline.SPREAD_MS] (a
     * [LaunchTimeline.REDUCED_FADE_MS] fade under [reducedMotion]) and the row opens at its end; a game or an app then has
     * the screen ([LaunchPhase.HANDED_OFF]) until [AppState.onForeground] fades the overlay out ([LaunchPhase.RETURNING]), an
     * in-app tool comes up under it fading out at once, and a refusal retreats ([LaunchPhase.RETREAT]) before its Problem
     * shows. Null otherwise, and [LaunchTimeline.GIVE_UP_MS] after a hand-off that never came back.
     */
    val launchSequence: StateFlow<LaunchSequence?> = _launchSequence.asStateFlow()

    /** The UI's capture of the selected name (set by the menu): a launch sequence's game carries it ([GameSession.launchName]). */
    var launchNameProvider: (() -> LaunchName?)? = null
    private var launchSeqJob: Job? = null
    private var launchSeqIds = 0L

    /** The runner (or an app's start) was called since the sequence's open: a hand-off, not a refusal. */
    var launchHandedOff = false

    /** A launch's Problem met during the spread: shown once the black has retreated. */
    private var heldLaunchError: String? = null
    private val _resumeInfo = MutableStateFlow(ResumeInfo.NONE)
    val resumeInfo: StateFlow<ResumeInfo> = _resumeInfo.asStateFlow()
    private var resumeJob: Job? = null     // highlighted game's resume-marker read
    private var launchJob: Job? = null     // pre-launch checks + runner launch in flight

    private fun listed(game: GameEntry): Listed? = (screen.value as? Screen.Games)?.entries?.firstOrNull { it.game == game }

    fun play(game: GameEntry): Job? = listed(game)?.let { launch(it, resumeSlot = null) }

    /** The context menu's Play from start (a game with a save state, which A resumes): [entry] from the start. */
    fun playFromStart(entry: Listed) {
        host.rememberMenuSelection()   // Home's game, as A on it (a game list has none to remember)
        launch(entry, resumeSlot = null)
    }

    /**
     * Continues [entry] from its last save, read now (never a slot read earlier); without one it plays [entry] when
     * [orPlay] (A on a game), else does nothing. An Unassigned game never launches; an Android game has no save.
     */
    fun resumeFromSave(entry: Listed, orPlay: Boolean = false): Job? {
        val e = entry.takeIf { !it.system.unassigned && !it.system.android } ?: return null
        return scope.launch {   // ends with the launch's own job (a launch sequence waits on it)
            val info = deps.userData.resumeInfo(e.system.tag, e.game.fileName)
            (if (info.canResume) launch(e, info.slot) else if (orPlay) launch(e, resumeSlot = null) else null)?.join()
        }
    }

    /**
     * A netplay launch ([NetplayController]'s agreement): [entry] under the agreed core (which may not be its folder's),
     * never resumed, with [session] for the runner. Everything else is [launch]'s.
     */
    fun launchNetplay(entry: Listed, session: NetplaySession) {
        val core = CoreRegistry.forId(session.coreId) ?: return
        launch(entry, resumeSlot = null, coreOverride = core, netplay = session)
    }

    /** Starts [entry] (see the steps inside); the start's job, or null when none began (an app, no core, one in flight). */
    fun launch(entry: Listed, resumeSlot: Int?, coreOverride: CoreDescriptor? = null, netplay: NetplaySession? = null, fromSwitcher: Boolean = false): Job? {
        if (host.kidsLocked()) return null   // Kids Mode: no game (nor Android game) while Locked
        val listed = entry.system
        val game = entry.game
        // After a game the menu is its own tab again (the pushed one is not restored): dropped only for a launch that
        // happens (an app's start, or the runner's call), so a coreless, in-flight, vanished or unreadable game leaves the
        // push for B to return to.
        if (listed.android) { host.dropPushedTab(); if (host.launchApp(entry)) launchHandedOff = true; return null }
        if (coreOverride == null && listed.core == null) return null
        if (launchJob?.isActive == true) return null   // a launch is already in flight
        switcher.cancelLoad()   // a switcher still loading must not open over the launched game
        val job = scope.launch {
            val gone = "Could not find ${game.fileName}. If it was moved or deleted, use Rescan in Settings → Library."
            if (!stillThere(game)) {   // the index can be older than the tree: no recents entry, no runner call
                launchError(gone)
                return@launch
            }
            // A netplay launch keeps the agreed core.
            val sys = (if (coreOverride == null) classicNesSystem(entry) else null) ?: listed
            val core = coreOverride ?: sys.core ?: return@launch
            // A .cue/.m3u/.ccd/.toc loads the files it names from the same folder; all must be there before the core starts.
            val lookup = companionsOrNull { DiscSheet.companions(deps.source, game.folder, game.fileName) }
            val companions = when (lookup) {
                null -> { launchError(gone); return@launch }
                is CompanionLookup.Missing -> { launchError("Missing ${lookup.name} next to ${lookup.sheet}"); return@launch }
                is CompanionLookup.Found -> lookup.companions
            }
            // An fbneo set also loads its parent and BIOS sets: the ones beside it go along, a BIOS not beside it is checked in the system dir.
            // The set table is fbneo's: flycast's NAOMI/Atomiswave zips, loaded as is too, take none of it.
            var systemSets = emptyList<String>()
            val fbneoSet = core.id == "fbneo" && CoreRegistry.loadsArchive(sys.tag, game.fileName)
            // A NAOMI/Atomiswave clone takes its parent set from beside it (flycast opens /rom/<parent>.zip); its BIOS is GameActivity's check.
            val naomiArchive = core.id == "flycast" && CoreRegistry.loadsArchive(sys.tag, game.fileName)
            val all = if (fbneoSet) {
                val arcade = companionsOrNull {
                    // The set table's first read parses it (hundreds of KB): off the main thread.
                    val sets = withContext(deps.compute) { deps.arcadeSets() }
                    ArcadeCompanions.find(deps.source, game.folder, game.fileName, sets)
                } ?: run { launchError(gone); return@launch }
                systemSets = arcade.systemSets
                companions + arcade.companions
            } else if (naomiArchive) {
                val parent = companionsOrNull {
                    val sets = withContext(deps.compute) { deps.naomiSets() }
                    // A zipped disc (not one of flycast's sets) is unpacked at launch and takes no parent.
                    if (!CoreRegistry.loadsArchive(sys.tag, game.fileName, sets)) emptyList()
                    else ArcadeCompanions.findParent(deps.source, game.folder, game.fileName, sets)
                } ?: run { launchError(gone); return@launch }
                companions + parent
            } else companions
            // The Brick records the shown name on every open (launcher.c Recents_setAlias(self->name)), replacing a stale one.
            val recent = Recent(game.path, game.shownName)
            // Only a game the core actually loaded is recent: the runner can still refuse it (bad archive, BIOS, load error),
            // so it keeps a launch record the game marks started, and the menu records it then.
            // Checked last, right before the runner: a game started during an import or a Device Sync could later write over
            // a save that just arrived.
            SaveTransferRules.launchRefusal(deps.saveTransfer?.importing == true, deps.deviceSync?.running == true)?.let { host.showNotice(it); return@launch }
            if (host.kidsLocked()) return@launch   // Kids Mode locked while this launch was on its way (the file checks above)
            host.dropPushedTab()
            switcher.switcherReturn = fromSwitcher   // set only once the runner is called: a refused launch never gets here (the runner keeps it on disk)
            // A launch sequence's spread hands its name over; any other launch has none.
            val launchName = if (_launchSequence.value?.phase == LaunchPhase.SPREAD) launchNameProvider?.invoke() else null
            launchHandedOff = true
            deps.runner.launch(GameSession(sys.tag, game.shownName, core, game.ref, game.folder, game.fileName, resumeSlot, all, systemSets = systemSets, gamePath = game.path,
                artPath = panelArt(deps.settings.backdropArt, game), netplay = netplay, launchName = launchName, fromSwitcher = fromSwitcher), recent)
        }
        launchJob = job
        job.invokeOnCompletion { if (launchJob === job) launchJob = null }
        return job
    }

    /** [load]'s companion lookup, or null when it fails: any failure, an Error too, is a game not found. */
    private inline fun <T : Any> companionsOrNull(load: () -> T): T? =
        try { load() } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }

    /** [launch]'s Problem: held during a launch sequence's spread (shown after its retreat, [openMinimal]), else shown now. */
    private fun launchError(text: String) {
        if (_launchSequence.value?.phase == LaunchPhase.SPREAD) heldLaunchError = text else error.value = text
    }

    /**
     * A Classic NES Series cart ([ClassicNes]) that would run gpSP only as the console's default runs mGBA:
     * mGBA is recorded as its Emulator pick, as the Emulator menu would ([Sidecars.set], then [moveInIndex]), so its
     * states, Game Settings, resume and menus all follow MGBA from this launch on; the user can still pick GBA there,
     * which this never overrides. The MGBA system to launch under; null: launch as listed. The header is read only
     * here, never by a scan: a bare ROM's first bytes, or an archive's ROM entry's ([AppDeps.archiveHead]).
     */
    private suspend fun classicNesSystem(entry: Listed): SystemFolder? {
        val sys = entry.system
        val game = entry.game
        if (sys.tag != ClassicNes.GPSP_TAG) return null
        val picked = try {
            sidecars.read(game.sourceId, game.folder, game.relativeDir)[game.fileName]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }?.takeIf { CoreRegistry.consoleTag(it) == CoreRegistry.consoleTag(sys.tag) }   // another console's tag is ignored, as by the scan
        // Only a ROM mGBA's core takes (a .gba): a .bin would list under GBA again on the next scan, and mGBA's unpack would refuse it.
        val mgba = CoreRegistry.forTag(ClassicNes.MGBA_TAG) ?: return null
        val tag = ClassicNes.launchTag(sys.tag, picked) {
            try {
                if (Names.isArchive(game.fileName)) deps.archiveHead(game.ref, game.fileName, mgba.extensions, ClassicNes.HEAD_BYTES)
                else if (Names.extensionOf(game.fileName) in mgba.extensions) deps.source.readHead(game.ref, ClassicNes.HEAD_BYTES)
                else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }
        if (tag == sys.tag) return null
        // A failed write still runs mGBA now; the next launch reads the header again.
        val recorded = try {
            sidecars.set(game.sourceId, game.folder, game.relativeDir, game.fileName, tag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
        if (recorded && host.moveInIndex(game, sys, tag)) host.refreshOpenList(select = game.path)
        return sys.copy(tag = tag, core = mgba)
    }

    /** The file is still where the index says: a non-directory child of its folder. Any failure (a revoked tree) counts as gone. */
    private suspend fun stillThere(game: GameEntry): Boolean = try {
        deps.source.child(game.folder, game.fileName)?.isDirectory == false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        false
    }

    /** Re-reads the highlighted game's resume marker: a list's, Home's Continue card's, pinned or shelf game's (after returning from a game). */
    fun refreshResume() {
        resumeJob?.cancel()
        val s = screen.value
        val index = selection.value.index
        val e = when (s) {
            is Screen.Games -> s.entries.getOrNull(index)?.takeIf { s.source != ListSource.Hidden }   // hidden games are not resumed from here
            // Continue and Home's pinned and shelf games (their A Resume or A Play): the focused one only, read off the main thread.
            is Screen.Systems -> when (val row = s.rows.getOrNull(index)) {
                is MainRow.Continue -> row.entry
                is MainRow.Pin -> row.entry.takeIf { s.category == MenuCategory.HOME }
                is MainRow.Shelf -> row.entry.takeIf { s.category == MenuCategory.HOME }
                else -> null
            }
            else -> null
        }?.takeIf { !it.system.unassigned && !it.system.android } ?: run { _resumeInfo.value = ResumeInfo.NONE; return }
        resumeJob = scope.launch {
            val info = deps.userData.resumeInfo(e.system.tag, e.game.fileName)
            if (screen.value === s && selection.value.index == index) _resumeInfo.value = info   // drop stale reads
        }
    }

    /**
     * Opens game list row [index] of [s]: its save state resumes (its menu has Play from start), none plays; an Android
     * game opens its app. The start's job, as [openMainRow]'s.
     */
    fun openGameRow(s: Screen.Games, index: Int): Job? =
        s.entries.getOrNull(index)?.let { if (it.system.android || it.system.unassigned) play(it.game) else resumeFromSave(it, orPlay = true) }

    /** The page a Minimal open belongs to ([MinimalOpening.page]): a tab's category, a game list's source; null for any other screen. */
    private fun minimalPage(s: Screen): Any? = when (s) {
        is Screen.Systems -> s.category
        is Screen.Games -> s.source
        else -> null
    }

    /** [s]'s row [index] as a Minimal open checks it is still there: a tab's row, a game list's game. */
    private fun minimalRow(s: Screen, index: Int): Any? = when (s) {
        is Screen.Systems -> s.rows.getOrNull(index)
        is Screen.Games -> s.entries.getOrNull(index)
        else -> null
    }

    /**
     * A on a Minimal page (a tab, Home's too, or a game list). A game or a tool runs the launch sequence
     * ([launchMinimal]). A folder (a console, a collection, Home's "+N" and Pick a game) fades the other names out
     * ([minimalOpening]) and opens as they have ([MINIMAL_LEAVE_MS]) if its page still shows it: a console or collection
     * whose list is Minimal too by its name morphing into the list's title ([NavMotion.MINIMAL_FORWARD]), else sliding in;
     * the opening clears a moment later (sooner on the next press: [AppState.onButton]).
     */
    fun openMinimal(s: Screen) {
        if (minimalPending) return
        val page = minimalPage(s) ?: return
        val index = selection.value.index
        val row = minimalRow(s, index) ?: return
        val game = row !is MainRow || row is MainRow.Pin || row is MainRow.Shelf || row is MainRow.Continue
        val tool = (row as? MainRow.Tool)?.row ?: (row as? MainRow.PinnedTool)?.row
        if (game || tool != null) return launchMinimal(page, index, row, tool)
        val list = (row is MainRow.System || row is MainRow.Collection) && deps.settings.gameListStyle == MenuStyle.MINIMAL
        minimalJob?.cancel()
        _minimalOpening.value = MinimalOpening(page, index)
        minimalPending = true
        minimalJob = scope.launch {
            delay(MINIMAL_LEAVE_MS)
            minimalPending = false
            val now = screen.value
            if (now is Screen.Systems && minimalPage(now) == page && minimalRow(now, index) == row)
                host.openMainRow(now, index, if (list) NavMotion.MINIMAL_FORWARD else NavMotion.FORWARD)
            delay(MINIMAL_CLEAR_MS)
            _minimalOpening.value = null
        }
    }

    /**
     * [openMinimal]'s game or [tool] ([row], [index] on [page]): [LaunchPhase.SPREAD] for [LaunchTimeline.SPREAD_MS]
     * ([LaunchTimeline.REDUCED_FADE_MS] under [reducedMotion]), input waiting ([minimalPending]); then the row opens if its
     * page still shows it. A runner call or an app's start hands off ([LaunchPhase.HANDED_OFF], till [AppState.onForeground] or
     * [LaunchTimeline.GIVE_UP_MS]); an in-app tool is up under the black, which fades ([LaunchPhase.RETURNING]); anything
     * else (the row gone, a refused or coreless game, an app that would not start) retreats, its Problem held till after.
     */
    private fun launchMinimal(page: Any, index: Int, row: Any, tool: ToolRow?) {
        minimalJob?.cancel(); _minimalOpening.value = null
        launchSeqJob?.cancel()
        val id = ++launchSeqIds
        _launchSequence.value = LaunchSequence(id, page, index, LaunchPhase.SPREAD, host.clockNow(), tool != null)
        minimalPending = true
        heldLaunchError = null
        launchSeqJob = scope.launch {
            delay(if (deps.reducedMotion()) LaunchTimeline.REDUCED_FADE_MS else LaunchTimeline.SPREAD_MS)
            val now = screen.value
            if (minimalPage(now) != page || minimalRow(now, index) != row) return@launch retreatLaunch(id)
            launchHandedOff = false
            val start = when (now) {
                is Screen.Systems -> host.openMainRow(now, index)
                is Screen.Games -> openGameRow(now, index)
                else -> null
            }
            start?.join()   // a game's checks and runner call (or refusal)
            when {
                launchHandedOff -> {
                    setLaunchPhase(id, LaunchPhase.HANDED_OFF)
                    delay(LaunchTimeline.GIVE_UP_MS)   // the app never left ([AppState.onBackground] cancels this): stop covering the menu
                    endLaunch(id)
                }
                tool != null && tool.kind != ToolKind.APP -> {
                    setLaunchPhase(id, LaunchPhase.RETURNING)
                    minimalPending = false
                    delay(LaunchTimeline.RETURN_FADE_MS)
                    endLaunch(id)
                }
                else -> retreatLaunch(id)
            }
        }
    }

    /** Sequence [id]'s black retreats into its name over [LaunchTimeline.RETREAT_MS]; then the held Problem shows. */
    private suspend fun retreatLaunch(id: Long) {
        setLaunchPhase(id, LaunchPhase.RETREAT)
        delay(LaunchTimeline.RETREAT_MS)
        endLaunch(id)
        heldLaunchError?.let { heldLaunchError = null; error.value = it }
    }

    private fun setLaunchPhase(id: Long, phase: LaunchPhase) {
        _launchSequence.value?.takeIf { it.id == id }?.let { _launchSequence.value = it.copy(phase = phase) }
    }

    /** Sequence [id] is over (a newer one keeps its state): the overlay gone, input free. */
    private fun endLaunch(id: Long) {
        if (_launchSequence.value?.id != id) return
        _launchSequence.value = null
        minimalPending = false
    }

    /** Back in front over a handed-off launch: the overlay fades out over [LaunchTimeline.RETURN_FADE_MS], input free now. */
    fun returnFromLaunch() {
        val seq = _launchSequence.value?.takeIf { it.phase == LaunchPhase.HANDED_OFF } ?: return
        launchSeqJob?.cancel()
        _launchSequence.value = seq.copy(phase = LaunchPhase.RETURNING)
        minimalPending = false
        launchSeqJob = scope.launch { delay(LaunchTimeline.RETURN_FADE_MS); endLaunch(seq.id) }
    }

    /** The faded page is gone (its list slid in): nothing left to clear. */
    fun forgetMinimalOpening() { _minimalOpening.value = null }

    /** A Minimal folder open that has landed is cleared now (the next press, or a return to the app). */
    fun clearMinimalOpening() { _minimalOpening.value?.let { minimalJob?.cancel(); _minimalOpening.value = null } }

    /** The game (or app) a Minimal launch handed off to is in front: no give-up now, the next return fades it. */
    fun onBackground() { if (_launchSequence.value?.phase == LaunchPhase.HANDED_OFF) launchSeqJob?.cancel() }
}

/** A Minimal folder open clears this long after its list opened ([MINIMAL_LEAVE_MS]). */
private const val MINIMAL_CLEAR_MS = 1_000L
