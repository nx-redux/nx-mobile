package com.nxredux.mobile.ui

import com.nxredux.mobile.scraper.ArtworkController
import com.nxredux.mobile.scraper.JobPhase
import com.nxredux.mobile.scraper.KeyResult
import com.nxredux.mobile.scraper.SignInResult
import com.nxredux.mobile.scraper.LibraryGameRef
import com.nxredux.mobile.scraper.ScrapeState
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What [controller]'s build and keys turn on (Settings). */
internal fun artSources(controller: ArtworkController) = ArtSources(
    ss = controller.ssAvailable, tgdb = controller.tgdbAvailable, sgdb = controller.sgdbAvailable,
    sgdbKey = controller.sgdbKeySet, tgdbBuilt = controller.tgdbSupported,
)

/** What the Artwork pages need from [AppState]: its notice, the Tools page and the library as it stands. */
internal interface ArtworkScreenHost {
    fun showNotice(text: String)
    fun showTools()
    /** The library games the controller works on (from memory, no I/O). */
    fun games(): List<LibraryGameRef>
    /**
     * What [games] is built from (the index, the hidden set, the Android games console), compared by identity: while the
     * same instances come back, so would the same games.
     */
    fun gamesInputs(): List<Any?>
    /** The main menu's console groups: display name and tags, in menu order. */
    fun consoles(): List<Pair<String, Set<String>>>
}

/**
 * Tools → Artwork: the page, Fetch by console and Progress; Settings (with the accounts) and Reset are in
 * [ArtworkAccountSettings]. Long actions run under [loader].
 */
internal class ArtworkScreenController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val error: MutableStateFlow<String?>,
    private val settings: AppSettings,
    private val loader: ScreenLoader,
    private val controller: ArtworkController,
    private val host: ArtworkScreenHost,
) {
    private var restored = false
    private var pageSelection = ListSelection(0, 0)
    private val more = ArtworkAccountSettings(scope, screen, selection, error, settings, loader, controller, host, ::showPage)

    fun open() {
        showPage(null)
        if (controller.tgdbAvailable && controller.state.value.tgdbLeft == null) scope.launch {
            guardArtwork(error) { controller.tgdbAllowance() }   // the TheGamesDB line follows through [onState]
        }
        if (!restored && controller.available) {
            restored = true
            scope.launch {   // a persisted paused job: its Resume row
                guardArtwork(error) { controller.restorePending() }
                refreshPage()
            }
        }
    }

    // --- The Artwork page ---

    /** The page as it stands; [progress]: with its Progress row even before the job is there (beside a starting fetch). */
    private fun page(progress: Boolean = false): Screen.Artwork {
        val state = controller.state.value
        val details = artworkDetails(controller.available, state)
        return Screen.Artwork(details, artworkRows(controller.available, state, settings.artReplaceExisting, progress))
    }

    /** The job as it moves ([AppState.artworkJob]: the stage's words are drawn again on each emission). */
    val job: StateFlow<ScrapeState> get() = controller.state

    /** The sign-in and key prompts' live text ([ArtworkAccountSettings]). */
    fun draftUser(text: String) = more.draftUser(text)
    fun draftPassword(text: String) = more.draftPassword(text)
    fun draftKey(text: String) = more.draftKey(text)

    /** The page on the row of [on] (null: the row last used). */
    private fun showPage(on: ArtworkAction?) {
        val p = page()
        screen.value = p
        val at = on?.let { a -> p.rows.indexOfFirst { it.action == a } }?.takeIf { it >= 0 }
        selection.value = (if (at != null) ListSelection(at, p.rows.size) else pageSelection).clampedTo(p.rows.size)
    }

    /** Rebuilds the open page in place, the highlight kept on the same row when it is still there. */
    private fun refreshPage() {
        val s = screen.value as? Screen.Artwork ?: return
        val on = s.rows.getOrNull(selection.value.index)?.action
        val p = page()
        screen.value = p
        val at = p.rows.indexOfFirst { it.action == on }
        selection.value = (if (at >= 0) ListSelection(at, p.rows.size) else selection.value).clampedTo(p.rows.size)
    }

    /** The library's art changed outside a fetch ([AppState]'s art check): Fetch by console's counts follow. */
    fun libraryChanged() = onState(controller.state.value)

    /**
     * The job moved on: the page, Fetch by console's counts, Progress or Settings follows. A starting Progress waits for
     * its job ([startFetch]); the cancel dialog closes once the job is no longer running or paused.
     */
    fun onState(state: ScrapeState) {
        when (val s = screen.value) {
            is Screen.Artwork -> refreshPage()
            is Screen.ArtworkConsoles -> refreshConsoles(s)
            is Screen.ArtworkProgress -> if (!s.starting) {
                val active = state.phase == JobPhase.RUNNING || state.phase == JobPhase.PAUSED
                screen.value = s.copy(job = state, confirmCancel = s.confirmCancel && active)
                selection.value = selection.value.clampedTo(artworkProgressRows(state).size)
            }
            is Screen.ArtworkSettings -> more.refreshSettings()
            else -> Unit
        }
    }

    private fun onPageButton(s: Screen.Artwork, button: NxButton) {
        when (button) {
            NxButton.UP -> selection.value = selection.value.up()
            NxButton.DOWN -> selection.value = selection.value.down()
            NxButton.A -> {
                val row = s.rows.getOrNull(selection.value.index) ?: return
                pageSelection = selection.value
                when (row.action) {
                    ArtworkAction.RESUME -> { controller.resume(); openProgress() }
                    ArtworkAction.FETCH_MISSING -> fetchMissing()
                    ArtworkAction.FETCH_BY_CONSOLE -> openConsoles()
                    ArtworkAction.PROGRESS -> openProgress()
                    ArtworkAction.SETTINGS -> more.openSettings()
                }
            }
            NxButton.B -> { pageSelection = selection.value; host.showTools() }
            else -> Unit
        }
    }

    /** Whether any artwork source is set up to fetch from. */
    val available: Boolean get() = controller.available

    /**
     * Setup's artwork step: the sources' accounts it offers, as Settings' Accounts group ([ArtworkAccountSettings]):
     * ScreenScraper's sign-in (a build with its credentials), the SteamGridDB and TheGamesDB keys.
     */
    val ssOffered: Boolean get() = controller.ssAvailable
    val ssUser: String? get() = settings.ssUsername.takeIf { it.isNotEmpty() && settings.ssPassword != null }
    val sgdbOffered: Boolean get() = controller.sgdbAvailable
    val sgdbKeySet: Boolean get() = controller.sgdbKeySet
    val tgdbOffered: Boolean get() = controller.tgdbSupported
    val tgdbKeySet: Boolean get() = controller.tgdbKeySet

    /** ScreenScraper's sign-in, checked with it ([ArtworkController.signIn]); kept only when it answers OK. */
    suspend fun signIn(user: String, password: String): SignInResult = controller.signIn(user, password)

    /** [key] for [source], checked with it and kept only when it answers OK. */
    suspend fun setKey(source: ArtKeyFor, key: String): KeyResult =
        if (source == ArtKeyFor.TGDB) controller.setTgdbKey(key) else controller.setSgdbKey(key)

    /**
     * Onboarding's Fetch now: the library's art fetched as the Artwork page's Fetch would (every game with Replace
     * existing, else the missing), in the background with no progress page. False with no source to fetch from.
     */
    fun fetchInBackground(): Boolean {
        if (!controller.available) return false
        if (settings.artReplaceExisting) controller.fetchAll(true) else controller.fetchMissing()
        return true
    }

    private fun fetchMissing() {
        val replace = settings.artReplaceExisting
        if (!replace && controller.missingCount(host.games()) == 0) { host.showNotice("Every game has art"); return }
        startFetch { if (replace) controller.fetchAll(true) else controller.fetchMissing() }
    }

    /** Bumped by each [startFetch]: only the latest one's queuing ends its starting Progress. */
    private var fetchStarts = 0

    /**
     * Starts a fetch ([fetch] returns its queuing, [ArtworkController.fetchMissing]) and opens Progress on it. Joining a
     * running or paused job shows that job at once; otherwise the new job is not there yet (the library is read first),
     * so Progress opens starting, empty rather than on the last job's rows, until the queuing ends.
     */
    private fun startFetch(fetch: () -> Job?) {
        val before = controller.state.value
        val queuing = fetch()
        if (queuing == null || before.phase == JobPhase.RUNNING || before.phase == JobPhase.PAUSED) { openProgress(); return }
        val start = ++fetchStarts
        screen.value = Screen.ArtworkProgress(ScrapeState(), starting = true)
        selection.value = ListSelection(0, 0)
        scope.launch {
            queuing.join()
            val s = screen.value as? Screen.ArtworkProgress
            if (s?.starting == true && start == fetchStarts) openProgress()
        }
    }

    // --- Fetch by console ---

    private fun consoleRows(): List<ArtworkConsole> {
        val games = host.games()
        return host.consoles().mapNotNull { (name, tags) ->
            val up = tags.mapTo(HashSet()) { it.uppercase() }
            val mine = games.filter { it.tag.uppercase() in up }
            if (mine.none { controller.fetchable(it) }) null else ArtworkConsole(name, up, controller.missingCount(mine))
        }
    }

    /** The library behind Fetch by console's counts ([ArtworkScreenHost.gamesInputs]); counted again only when it changed. */
    private var consolesInputs: List<Any?> = emptyList()

    private fun openConsoles() {
        val rows = consoleRows()
        consolesInputs = host.gamesInputs()
        screen.value = Screen.ArtworkConsoles(rows)
        selection.value = ListSelection(0, rows.size)
    }

    /** Fetch by console's missing counts follow a running fetch, the highlight kept on the same console. */
    private fun refreshConsoles(s: Screen.ArtworkConsoles) {
        val inputs = host.gamesInputs()
        if (inputs.size == consolesInputs.size && inputs.indices.all { inputs[it] === consolesInputs[it] }) return
        consolesInputs = inputs
        val on = s.rows.getOrNull(selection.value.index)?.name
        val rows = consoleRows()
        screen.value = Screen.ArtworkConsoles(rows)
        val at = rows.indexOfFirst { it.name == on }
        selection.value = (if (at >= 0) ListSelection(at, rows.size) else selection.value).clampedTo(rows.size)
    }

    private fun onConsolesButton(s: Screen.ArtworkConsoles, button: NxButton) {
        when (button) {
            NxButton.UP -> selection.value = selection.value.up()
            NxButton.DOWN -> selection.value = selection.value.down()
            NxButton.A -> {
                val c = s.rows.getOrNull(selection.value.index) ?: return
                val replace = settings.artReplaceExisting
                if (!replace && c.missing == 0) { host.showNotice("Every ${c.name} game has art"); return }
                startFetch { controller.fetchConsole(c.tags, replace) }
            }
            NxButton.B -> showPage(ArtworkAction.FETCH_BY_CONSOLE)
            else -> Unit
        }
    }

    // --- Progress ---

    private fun openProgress() {
        val state = controller.state.value
        screen.value = Screen.ArtworkProgress(state)
        selection.value = ListSelection(0, artworkProgressRows(state).size)
    }

    private fun onProgressButton(s: Screen.ArtworkProgress, button: NxButton) {
        if (s.confirmCancel) {
            when (button) {
                NxButton.A -> { screen.value = s.copy(confirmCancel = false); controller.cancel() }
                NxButton.B -> screen.value = s.copy(confirmCancel = false)
                else -> Unit
            }
            return
        }
        val phase = controller.state.value.phase
        when (button) {
            NxButton.UP -> selection.value = selection.value.up()
            NxButton.DOWN -> selection.value = selection.value.down()
            NxButton.Y -> when (phase) {
                JobPhase.RUNNING -> controller.pause()
                JobPhase.PAUSED -> controller.resume()
                else -> Unit
            }
            NxButton.X -> if (phase == JobPhase.RUNNING || phase == JobPhase.PAUSED) screen.value = s.copy(confirmCancel = true)
            // Done: a finished job is cleared, its Progress row with it.
            NxButton.A -> if (phase == JobPhase.DONE || phase == JobPhase.CANCELLED) scope.launch { controller.clear(); showPage(ArtworkAction.FETCH_MISSING) }
            NxButton.B -> showPage(ArtworkAction.PROGRESS)
            else -> Unit
        }
    }

    // --- Settings' wide stage ([artworkSplitView]) ---

    /** The words last drawn beside a page ([stagePage]): a word tap finds its word by what it does there. */
    private var drawnWords: List<ArtworkRow> = emptyList()

    /**
     * The Artwork page the stage's word block shows beside [s]: [s] itself on the root, else the page as it stands, with
     * its Progress word even before the job is there (beside a starting fetch). Remembered as drawn ([wordTap]).
     */
    fun stagePage(s: Screen): Screen.Artwork =
        (s as? Screen.Artwork ?: page(progress = s is Screen.ArtworkProgress)).also { drawnWords = it.rows }

    /** The stage's word lit for page [s] (open grey, with its "›"): Reset is under Settings. Null on the root and elsewhere. */
    fun wordOf(s: Screen): ArtworkAction? = when (s) {
        is Screen.ArtworkConsoles -> ArtworkAction.FETCH_BY_CONSOLE
        is Screen.ArtworkProgress -> ArtworkAction.PROGRESS
        is Screen.ArtworkSettings, is Screen.ArtworkReset -> ArtworkAction.SETTINGS
        else -> null
    }

    /**
     * A tap on the stage's word [index]. On the root it selects and opens the word (as A). On a page that word's page
     * opens in its place, as B then A would (Fetch missing art starting its job); from Reset, Settings' word goes back
     * to the Settings rows on Reset. The word is found by what it does as drawn, so a word gone since (Resume) does
     * nothing. The open page's own word, an open dialog or prompt, or a loading screen do nothing.
     */
    fun wordTap(index: Int) {
        if (loader.active) return
        when (val here = screen.value) {
            is Screen.Artwork -> {
                if (index !in here.rows.indices) return
                selection.value = ListSelection(index, here.rows.size)
                onPageButton(here, NxButton.A)
            }
            else -> {
                val open = wordOf(here) ?: return
                if (dialogOpen(here)) return
                val action = drawnWords.getOrNull(index)?.action ?: return
                if (action == open) {
                    if (here is Screen.ArtworkReset) more.openSettings(ArtworkSettingKind.RESET)
                    return
                }
                if (page().rows.none { it.action == action }) return
                showPage(action)
                (screen.value as? Screen.Artwork)?.let { onPageButton(it, NxButton.A) }
            }
        }
    }

    /**
     * The page the root previews in the right frame while [action] is highlighted: Fetch by console's consoles,
     * Progress, the Settings rows. Fetch missing art and Resume have no page: null (an empty frame).
     */
    fun preview(action: ArtworkAction): Screen? = when (action) {
        ArtworkAction.FETCH_BY_CONSOLE -> Screen.ArtworkConsoles(consoleRows())
        ArtworkAction.PROGRESS -> Screen.ArtworkProgress(controller.state.value)
        ArtworkAction.SETTINGS -> more.settingsPage()
        ArtworkAction.RESUME, ArtworkAction.FETCH_MISSING -> null
    }

    /**
     * A tap on [row] of the root's preview: opens the highlighted word's page on that row, without acting on it. A
     * one-tap list ([com.nxredux.mobile.ui.nx.nxTap]) calls this twice: the second call, on the page it opened with
     * [row] selected, acts on it (A), as Settings' preview ([AppState.settingsPreviewTap]).
     */
    fun previewTap(row: Int) {
        if (loader.active) return
        val here = screen.value
        if (here !is Screen.Artwork) {
            if (wordOf(here) != null && !dialogOpen(here) && row == selection.value.index) onButton(here, NxButton.A)
            return
        }
        val action = here.rows.getOrNull(selection.value.index)?.action ?: return
        pageSelection = selection.value
        when (action) {
            ArtworkAction.FETCH_BY_CONSOLE -> { openConsoles(); selectRow(row) }
            ArtworkAction.PROGRESS -> { openProgress(); selectRow(row) }
            ArtworkAction.SETTINGS -> more.openSettings(more.settingsPage().rows.getOrNull(row)?.kind)
            ArtworkAction.RESUME, ArtworkAction.FETCH_MISSING -> Unit
        }
    }

    /** The open page's selection moved to [row], kept within its rows. */
    private fun selectRow(row: Int) { selection.value = selection.value.copy(index = row).clampedTo(selection.value.count) }

    /** A dialog or prompt open on an Artwork page: it takes every input, the stage's word and preview taps included. */
    private fun dialogOpen(s: Screen): Boolean = when (s) {
        is Screen.ArtworkSettings -> s.dialogOpen
        is Screen.ArtworkReset -> s.confirm != null
        is Screen.ArtworkProgress -> s.confirmCancel
        else -> false
    }

    // --- Dispatch ---

    /** Handles [button] on an Artwork screen; false for any other screen. */
    fun onButton(s: Screen, button: NxButton): Boolean {
        when (s) {
            is Screen.Artwork -> onPageButton(s, button)
            is Screen.ArtworkConsoles -> onConsolesButton(s, button)
            is Screen.ArtworkProgress -> onProgressButton(s, button)
            is Screen.ArtworkSettings -> more.onSettingsButton(s, button)
            is Screen.ArtworkReset -> more.onResetButton(s, button)
            else -> return false
        }
        return true
    }

    /** A touch on an Artwork Settings row's control ([ArtworkAccountSettings.onSettingEvent]). */
    fun onSettingEvent(s: Screen.ArtworkSettings, event: NxSettingEvent) = more.onSettingEvent(s, event)

    // --- The game context menu's Fetch art / Change art ---

    /**
     * None for a game no enabled art source knows (excluded tags never); Change art when it has any art (3D or 2D
     * box, screenshot, wheel; written by the app or placed by hand: [hasAnyArt]), else Fetch art.
     */
    fun artMenu(entry: Listed): ArtMenuItem {
        if (!controller.available || entry.system.unassigned || !controller.canFetch(entry.system.tag, entry.game.fileName, entry.system.android)) return ArtMenuItem.NONE
        return if (hasAnyArt(entry.game)) ArtMenuItem.CHANGE else ArtMenuItem.FETCH
    }
}
