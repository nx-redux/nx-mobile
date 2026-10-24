package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaController
import com.nxredux.mobile.achievements.RaDownloadPhase
import com.nxredux.mobile.achievements.RaGameFetch
import com.nxredux.mobile.achievements.RaGameView
import com.nxredux.mobile.achievements.RaPrefetch
import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.achievements.RaState
import com.nxredux.mobile.achievements.RaStoreException
import com.nxredux.mobile.achievements.sortedBy
import com.nxredux.mobile.library.path
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingControl
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.control
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The details pane's achievements download only after the highlight has stayed on a game this long. */
private const val DETAIL_RA_REST_MS = 500L

/** What the RetroAchievements screens need from [AppState]: its notice, the Tools page, the menus' game info, the chooser. */
internal interface RaScreenHost {
    fun showNotice(text: String)
    fun showTools()
    /** Opens the highlighted row, as a tap on it ([AppState.openSelected]). */
    fun openSelected()
    fun refreshResume()
    /** The menus' play time and achievement progress read again ([AppState.gameInfo]). */
    fun refreshGameInfo()
    /** Signed in to RetroAchievements: a token and a user name. */
    fun raSignedIn(): Boolean
    /** A value row's picker popup titled [title]: [labels], [current] under the cursor; [apply] takes the picked index. */
    fun openPicker(title: String, labels: List<String>, current: Int, apply: (Int) -> Unit)
}

/**
 * RetroAchievements in the launcher: the achievements panel over a game list and the second display's details pane
 * ([raPanel], [gameDetailRa]), and the Tools pages (the main page, the game data download, the games browser, a game's
 * achievements, one achievement, and the settings page). [achievements] null: a platform without it.
 */
internal class RaScreenController(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val error: MutableStateFlow<String?>,
    private val settings: AppSettings,
    private val loader: ScreenLoader,
    private val achievements: RaController?,
    private val dualMenu: StateFlow<Boolean>,
    private val host: RaScreenHost,
) {
    /** The cached RetroAchievements games' hashes by library path, read with the game info (for the panel). */
    var raHashes: Map<String, String> = emptyMap()
    /**
     * The panel's one-game downloads this run, by library path: null while one runs, else how it ended. Only a passing
     * failure is tried again (on the panel's next open); an unrecognised or unreadable file stays marked, so reopening the
     * panel never downloads it again (and in a later run its cached GameID 0 answers without a request).
     */
    private val raFetches = HashMap<String, RaGameFetch?>()
    private val _raPanel = MutableStateFlow<RaPanel?>(null)
    /** The achievements panel over a game list; null while closed. */
    val raPanel: StateFlow<RaPanel?> = _raPanel.asStateFlow()
    private var raPanelJob: Job? = null
    private val _gameDetailRa = MutableStateFlow<RaPanel?>(null)
    /**
     * While [dualMenu] on a game list and signed in: the highlighted game's achievements, for the details pane on the second
     * display (the panel's contents, without its row or detail state); else null. A game with no cached data downloads only
     * once the highlight has rested on it for [DETAIL_RA_REST_MS], so scrolling past games requests nothing.
     */
    val gameDetailRa: StateFlow<RaPanel?> = _gameDetailRa.asStateFlow()
    private var raDetailJob: Job? = null

    // --- The achievements panel over a game list ---

    /** The platform has RetroAchievements and the user is signed in: a game list offers the panel (the trophy icon, X, the game's menu). */
    val hasAchievements: Boolean get() = achievements != null && host.raSignedIn()

    /** The trophy icon: opens the panel on the highlighted game, or closes it. */
    fun toggleRaPanel() { if (_raPanel.value != null) closeRaPanel() else openRaPanel() }

    /**
     * X (or the trophy icon) on a game list: the panel on its highlighted game. Not over its menu, search or Layout
     * dropdown, nor while [dualMenu] (the second display's details pane shows the game's achievements).
     */
    fun openRaPanel() {
        val s = screen.value as? Screen.Games ?: return
        if (!hasAchievements || dualMenu.value || s.menu != null || s.search != null || s.layoutMenu != null) return
        raFetches.values.removeAll { it is RaGameFetch.Failed }   // opened again: a failed download is tried once more
        _raPanel.value = RaPanel(null, "", RaPanelContent.Loading)
        followRaPanel(force = true)
    }

    fun closeRaPanel() {
        raPanelJob?.cancel(); raPanelJob = null
        _raPanel.value = null
    }

    /**
     * The open panel on the game list's highlighted game: when that game changed (or [force]), its cached achievements are
     * read again. Signed out or no game: a message instead. A game with no cached data downloads in the background
     * ([RaController.fetchGame], not cancelled by the panel moving on); the panel shows it once it is cached.
     */
    fun followRaPanel(force: Boolean = false) = followRa(false, force)

    /** [followRaPanel] for the details pane ([gameDetailRa]): off unless [dualMenu] on a game list, signed in, with a highlighted game. */
    fun followRaDetails(force: Boolean = false) = followRa(true, force)

    /** One feed for both: the panel's ([details] false) or the details pane's. The pane waits [DETAIL_RA_REST_MS] before starting a download. */
    private fun followRa(details: Boolean, force: Boolean, rested: Boolean = false) {
        val target = if (details) _gameDetailRa else _raPanel
        val s = screen.value as? Screen.Games
        val entry = s?.entries?.getOrNull(selection.value.index)
        if (details && (!dualMenu.value || entry == null || achievements == null || settings.raUsername.isBlank())) {
            raDetailJob?.cancel(); raDetailJob = null
            target.value = null
            return
        }
        val p = target.value ?: (if (details) RaPanel(null, "", RaPanelContent.Loading) else return)
        if (s == null) return
        val c = achievements ?: return
        val path = entry?.game?.path
        if (!force && p.path == path) return
        if (details && !rested && path != null) {
            if (force && raDetailJob?.isActive == true && p.path == path && path !in raFetches) return   // still resting on it: keep the window
            if (!force && raFetches[path] is RaGameFetch.Failed) raFetches.remove(path)   // the highlight came back: a failed download is tried once more
        }
        (if (details) raDetailJob else raPanelJob)?.cancel()
        val title = entry?.shown.orEmpty()
        val keep = { content: RaPanelContent -> RaPanel(path, title, content) }
        if (entry == null) { target.value = keep(RaPanelContent.NoData); return }
        if (settings.raUsername.isBlank()) { target.value = keep(RaPanelContent.SignedOut); return }
        val hash = raHashes[path] ?: (raFetches[path] as? RaGameFetch.Cached)?.hash ?: run {
            if (details && !rested && entry.game.path !in raFetches) {
                // Not asked for yet: the highlight must rest on this game before it is downloaded.
                target.value = keep(RaPanelContent.Loading)
                raDetailJob = scope.launch { delay(DETAIL_RA_REST_MS); followRa(true, force = true, rested = true) }
                return
            }
            val content = raFetchContent(c, entry.game.path) ?: return followRa(details, force = true, rested = true)   // cached at once: read it
            target.value = keep(content)
            return
        }
        // A re-read of the game shown keeps its rows (no Loading flash); a new game starts on its first achievement.
        val same = p.path == path && p.content is RaPanelContent.Game
        if (!same) target.value = keep(RaPanelContent.Loading)
        val job = scope.launch {
            val list = try { c.achievements(hash).sortedBy(settings.raSortOrder) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            val badges = try { c.badgeListing(hash) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyMap() }
            val now = target.value ?: return@launch
            if (now.path != path) return@launch   // the selection moved on meanwhile
            val content = RaPanelContent.Game(hash, list, badges)
            target.value = if (same) now.copy(content = content).let { it.copy(row = it.row.coerceAtMost((it.visible.size - 1).coerceAtLeast(0))) }
                else now.copy(content = content, row = 0, detail = false)
        }
        if (details) raDetailJob = job else raPanelJob = job
    }

    /**
     * The panel's content for [path], a game with no cached data: its download's state, the download started when there
     * is none. Null once it is cached (a download that ended at once): the panel reads the game.
     */
    private fun raFetchContent(c: RaController, path: String): RaPanelContent? {
        if (path !in raFetches) {
            raFetches[path] = null
            var started = false   // set once this returns: a download ending before that is shown by the return instead
            scope.launch {
                val o = try { c.fetchGame(path) } catch (e: CancellationException) { throw e } catch (e: Exception) { RaGameFetch.Failed(RaPrefetch.NO_REPLY) }
                raFetches[path] = o
                if (o is RaGameFetch.Cached) { raHashes = raHashes + (path to o.hash); host.refreshGameInfo() }   // its progress in the menus too
                if (started && _raPanel.value?.path == path) followRaPanel(force = true)
                if (started && _gameDetailRa.value?.path == path) followRaDetails(force = true)
            }
            started = true
        }
        return when (val o = raFetches[path]) {
            null -> RaPanelContent.Downloading
            is RaGameFetch.Cached -> null
            is RaGameFetch.Failed -> RaPanelContent.Failed(o.reason)
            is RaGameFetch.Unrecognised -> RaPanelContent.Unrecognised(o.hash)
            RaGameFetch.Unreadable -> RaPanelContent.Unreadable
            RaGameFetch.NotSupported -> RaPanelContent.NoData   // a console RetroAchievements lacks
        }
    }

    /** B on a game list with the panel open: back from an achievement's details, else the panel closes. False when it is closed. */
    fun backRaPanel(): Boolean {
        val p = _raPanel.value ?: return false
        if (p.detail) _raPanel.value = p.copy(detail = false) else closeRaPanel()
        return true
    }

    /** A tap on the panel's achievement [index] (of the shown rows): its details. */
    fun openRaPanelRow(index: Int) {
        val p = _raPanel.value ?: return
        if (index in p.visible.indices) _raPanel.value = p.copy(row = index, detail = true)
    }

    /**
     * The open panel has the pad: UP / DOWN move over the achievements, LEFT / RIGHT to the previous / next game (the
     * panel follows), A opens one's details (signed out: the RetroAchievements page), B back from the details or
     * closes, X closes.
     */
    fun onRaPanelButton(p: RaPanel, button: NxButton) {
        val last = (p.visible.size - 1).coerceAtLeast(0)
        when (button) {
            NxButton.UP -> if (!p.detail) _raPanel.value = p.copy(row = (p.row - 1).coerceAtLeast(0))
            NxButton.DOWN -> if (!p.detail) _raPanel.value = p.copy(row = (p.row + 1).coerceAtMost(last))
            NxButton.LEFT -> { selection.value = selection.value.up(wrap = false); host.refreshResume() }
            NxButton.RIGHT -> { selection.value = selection.value.down(wrap = false); host.refreshResume() }
            NxButton.A -> when {
                p.content is RaPanelContent.SignedOut -> { closeRaPanel(); showRetroAchievements() }
                !p.detail && p.visible.isNotEmpty() -> _raPanel.value = p.copy(detail = true)
            }
            NxButton.B -> if (p.detail) _raPanel.value = p.copy(detail = false) else closeRaPanel()
            NxButton.X -> closeRaPanel()
            else -> Unit
        }
    }

    // --- RetroAchievements: main page, games browser, a game's achievements, one achievement ---

    private var raSelection = ListSelection(0, 0)        // the main page's row, remembered while in the browser or settings
    private var raGamesSelection = ListSelection(0, 0)   // the games list's row, remembered while in a game
    private var raGamesScreen: Screen.RaGames? = null    // the games list a game was opened from (the left pane beside it)
    private var raGameScreen: Screen.RaGame? = null      // the game page (list and filter) an achievement was opened from
    private var raGameSelection = ListSelection(0, 0)    // its row, remembered while on that achievement

    /** The RetroAchievements page on the row last used there. */
    fun showRetroAchievements() {
        val c = achievements ?: run { host.showTools(); return }
        screen.value = Screen.RetroAchievements()
        selection.value = raSelection.clampedTo(raRows(c.state.value, c.download.value).size)
    }

    /** The game data download's progress page, on its first row. */
    private fun openRaDownload() {
        val c = achievements ?: return
        screen.value = Screen.RaDownload()
        selection.value = ListSelection(0, c.download.value?.items?.size ?: 0)
    }

    /**
     * The download page: UP / DOWN move through its games, Y pauses or resumes, X cancels (after "Cancel download?"), A
     * (Done, once it has ended) clears it, B goes back to the RetroAchievements page on its Progress row, the download
     * running on.
     */
    fun onRaDownloadButton(s: Screen.RaDownload, button: NxButton) {
        val c = achievements ?: run { host.showTools(); return }
        if (s.confirmCancel) {
            when (button) {
                NxButton.A -> { c.cancel(); screen.value = s.copy(confirmCancel = false) }
                NxButton.B -> screen.value = s.copy(confirmCancel = false)
                else -> Unit
            }
            return
        }
        val phase = c.download.value?.phase
        val ongoing = phase == RaDownloadPhase.RUNNING || phase == RaDownloadPhase.PAUSED
        when (button) {
            NxButton.UP -> selection.value = selection.value.clampedTo(c.download.value?.items?.size ?: 0).up()
            NxButton.DOWN -> selection.value = selection.value.clampedTo(c.download.value?.items?.size ?: 0).down()
            NxButton.Y -> if (phase == RaDownloadPhase.RUNNING) c.pause() else if (phase == RaDownloadPhase.PAUSED) c.resume()
            // Done: an ended download is cleared, the page's Progress row with it.
            NxButton.A -> if (!ongoing) { c.clearDownload(); raSelection = ListSelection(0, 0); showRetroAchievements() }
            NxButton.X -> if (ongoing) screen.value = s.copy(confirmCancel = true)
            NxButton.B -> {
                raSelection = ListSelection(raRows(c.state.value, c.download.value).indexOf(RaAction.DOWNLOAD).coerceAtLeast(0), 0)
                showRetroAchievements()
            }
            else -> Unit
        }
    }

    // --- RetroAchievements settings page ---

    private var raSettingsSelection = ListSelection(0, 0)
    private val RA_BUSY = "Busy, try again"
    private val RA_READ_FAILED = "Couldn't read achievements"

    /**
     * The settings page's rows, from the settings and the controller's last status, in groups: Account (Sign in or Sign
     * out with the status under it, Enable achievements, Download all game data), Notifications, Achievements (the sort
     * order) and Reset. Each value row carries its values ([raSettingValue]).
     */
    private fun raSettingsRows(): List<RaSettingsRow> {
        val st = (achievements?.state?.value as? RaState.Idle)?.status
        val s = settings
        val signedIn = s.raToken != null   // enabling achievements and downloading game data need an account
        fun value(kind: RaSettingKind, label: String, text: String, enabled: Boolean = true) = RaSettingsRow(
            kind, label, text, enabled = enabled,
            choices = raSettingValue(kind)?.let { v -> v.labels?.let { SettingChoices(it, v.index) } },
        )
        val status = st?.lastError ?: if (signedIn) "Signed in as ${s.raUsername}" else "Not signed in"
        return listOf(
            RaSettingsRow(RaSettingKind.SECTION, "Account"),
            if (signedIn) RaSettingsRow(RaSettingKind.SIGN_OUT, "Sign out", status, warning = st?.lastError != null)
            else RaSettingsRow(RaSettingKind.SIGN_IN, "Sign in", status, warning = st?.lastError != null),
            value(RaSettingKind.ENABLED, "Enable achievements", if (signedIn && s.raEnabled) "On" else "Off", enabled = signedIn),
            RaSettingsRow(RaSettingKind.DOWNLOAD_ALL, "Download all game data", st?.let { "${it.cachedGames} games cached" }, enabled = signedIn),
            RaSettingsRow(RaSettingKind.GAP, ""),
            RaSettingsRow(RaSettingKind.SECTION, "Notifications"),
            value(RaSettingKind.NOTIFICATIONS, "Show notifications", if (s.raShowNotifications) "On" else "Off"),
            value(RaSettingKind.NOTIFICATION_SECONDS, "Notification duration", "${s.raNotificationSeconds} s"),
            value(RaSettingKind.PROGRESS_SECONDS, "Progress duration", if (s.raProgressSeconds == 0) "Off" else "${s.raProgressSeconds} s"),
            RaSettingsRow(RaSettingKind.GAP, ""),
            RaSettingsRow(RaSettingKind.SECTION, "Achievements"),
            value(RaSettingKind.SORT_ORDER, "Achievement sort order", s.raSortOrder.label),
            RaSettingsRow(RaSettingKind.GAP, ""),
            RaSettingsRow(RaSettingKind.SECTION, "Reset"),
            RaSettingsRow(RaSettingKind.RESET_ACCOUNT, "Reset account data"),
            RaSettingsRow(RaSettingKind.ERASE_ALL, "Erase all achievement data"),
            RaSettingsRow(RaSettingKind.RESET_SETTINGS, "Reset settings to defaults"),
        )
    }

    /**
     * The settings page's value row [kind] as a [SettingValue] ([settingValue]'s table for this page): the two durations
     * (LEFT / RIGHT stop at their ends, A wraps), the sort order, and the On / Off rows. Each re-renders the page after a
     * change. Null for a row that is not a value row.
     */
    private fun raSettingValue(kind: RaSettingKind?): SettingValue<*>? {
        val s = settings
        fun <T> cycle(values: List<T>, current: T, label: (T) -> String, clamps: Boolean = false, set: (T) -> Unit) =
            SettingValue(values, current, set, label, clamps) { refreshRaSettings() }
        val onOff = { on: Boolean -> if (on) "On" else "Off" }
        return when (kind) {
            RaSettingKind.NOTIFICATION_SECONDS -> cycle((1..5).toList(), s.raNotificationSeconds, { "$it s" }, clamps = true) { s.raNotificationSeconds = it }
            RaSettingKind.PROGRESS_SECONDS -> cycle((0..5).toList(), s.raProgressSeconds, { if (it == 0) "Off" else "$it s" }, clamps = true) { s.raProgressSeconds = it }
            RaSettingKind.SORT_ORDER -> cycle(RaSortOrder.entries, s.raSortOrder, { it.label }) { s.raSortOrder = it }
            RaSettingKind.ENABLED -> cycle(listOf(false, true), s.raEnabled, onOff) { s.raEnabled = it }
            RaSettingKind.NOTIFICATIONS -> cycle(listOf(false, true), s.raShowNotifications, onOff) { s.raShowNotifications = it }
            else -> null
        }
    }

    /**
     * Opens on the remembered row, but never on Sign out (nor a group label): the page lands on the row under it
     * instead, so a stray A never signs out.
     */
    private fun openRaSettings() {
        val rows = raSettingsRows()
        screen.value = Screen.RaSettings(rows)
        val sel = raSettingsSelection.clampedTo(rows.size).landOn { rows[it].selectable }
        selection.value = if (rows.getOrNull(sel.index)?.kind != RaSettingKind.SIGN_OUT) sel
            else sel.copy(index = sel.index + 1).clampedTo(rows.size).landOn { rows[it].selectable }
    }

    /** Re-renders the rows, keeping an open sign-in modal or confirm. */
    private fun refreshRaSettings() {
        (screen.value as? Screen.RaSettings)?.let { screen.value = it.copy(rows = raSettingsRows()) }
    }

    /** The controller went idle on the settings page: the rows follow, and a sign-in modal left open over a finished sign-in closes. */
    private fun onRaSettingsIdle() {
        val s = screen.value as? Screen.RaSettings ?: return
        if (s.signingIn && settings.raToken != null) closeRaSignIn(s) else refreshRaSettings()
    }

    /**
     * The controller's state [s] moved while a RetroAchievements page shows ([AppState]'s collector): the page lands on the
     * row that started the job once it is idle again, and the settings page's rows follow.
     */
    fun onRaState(s: RaState, c: RaController) {
        when (screen.value) {
            // Busy shows no rows; back to idle it lands on the row that started the job.
            is Screen.RetroAchievements -> selection.value = (if (selection.value.count == 0) raSelection else selection.value).clampedTo(raRows(s, c.download.value).size)
            is Screen.RaSettings -> if (s is RaState.Idle) onRaSettingsIdle()   // the rows after a sign-in, sign-out or reset
            else -> Unit
        }
    }

    /** The sign-in modal's live username (the composable reports every change), so A can sign in with it. */
    fun raDraftUser(text: String) {
        val s = screen.value as? Screen.RaSettings ?: return
        if (s.signingIn) screen.value = s.copy(draftUser = text)
    }

    /** The sign-in modal's live password; never saved, only sent with the sign-in. */
    fun raDraftPassword(text: String) {
        val s = screen.value as? Screen.RaSettings ?: return
        if (s.signingIn) screen.value = s.copy(draftPassword = text)
    }

    /** Closes the sign-in modal and forgets what was typed. */
    private fun closeRaSignIn(s: Screen.RaSettings) {
        screen.value = s.copy(rows = raSettingsRows(), signingIn = false, draftUser = "", draftPassword = "")
    }

    /** The sign-in modal's A: signs in with the typed entries; a blank one keeps the modal open. */
    private fun submitRaSignIn(s: Screen.RaSettings, c: RaController) {
        if (s.draftUser.isBlank() || s.draftPassword.isBlank()) { error.value = "Enter a username and password"; return }
        if (c.busy) { error.value = RA_BUSY; return }
        closeRaSignIn(s)
        c.login(s.draftUser.trim(), s.draftPassword)
        if (!c.busy) refreshRaSettings()   // finished without suspending
    }

    /** Back to the RetroAchievements page; anything typed is dropped. */
    private fun leaveRaSettings() {
        raSettingsSelection = selection.value
        showRetroAchievements()
    }

    /**
     * The level the left pane shows beside [screen] in the RetroAchievements column browser ([raSplitView]): the screen it
     * was opened from and the row that opened it. The page beside its Settings and the games list; the games list beside a
     * game; the game's achievements (its filter kept) beside one achievement. Null for any other screen.
     */
    fun raParent(screen: Screen): RaParent? = when (screen) {
        is Screen.RaSettings, is Screen.RaGames -> RaParent(Screen.RetroAchievements(), raPageRow(screen) ?: 0)
        is Screen.RaGame -> raGamesScreen?.let { RaParent(it, raGamesSelection.index) }
        is Screen.RaAchievement -> RaParent(raGameScreen ?: Screen.RaGame(screen.game, listOf(screen.achievement), badges = screen.badges), raGameSelection.index)
        else -> null
    }

    /**
     * A tap on row [index] of the dimmed left pane showing [parent] ([raParent]): that level's row opens in place of the
     * current one, as B then A there would (another game's achievements, RetroAchievements Settings beside its page, Sync
     * on the page). The row already open, an open sign-in or confirm, a loading screen, or a pane no longer beside the
     * current level do nothing: a one-tap list ([com.nxredux.mobile.ui.nx.nxTap]) calls this twice, and the second call
     * finds the row open (or the screen moved on).
     */
    fun raParentTap(parent: Screen, index: Int) {
        val here = screen.value
        val p = raParent(here) ?: return
        if (p.screen::class != parent::class || index == p.row || loader.active) return
        if (here is Screen.RaSettings) {
            if (here.signingIn || here.confirm != null) return
            raSettingsSelection = selection.value
        }
        when (val ps = p.screen) {
            is Screen.RetroAchievements -> {
                raSelection = ListSelection(index, RaAction.entries.size); showRetroAchievements()
                if (selection.value.index == index) host.openSelected()
            }
            // Straight to the tapped game or achievement: the right pane changes, the list never shows on it in between.
            is Screen.RaGames -> ps.games.getOrNull(index)?.let { openRaGame(it, ps, ListSelection(index, ps.games.size)) }
            is Screen.RaGame -> openRaAchievement(ps, ListSelection(index, visibleAchievements(ps).size))
            else -> return
        }
    }

    /** UP/DOWN move, LEFT/RIGHT change a value row, A acts, B closes a dialog or leaves; an open sign-in modal takes A (sign in) and B or MENU (cancel). */
    fun onRaSettingsButton(s: Screen.RaSettings, button: NxButton) {
        val c = achievements ?: run { host.showTools(); return }
        if (s.signingIn) {
            when (button) {
                NxButton.A -> submitRaSignIn(s, c)
                NxButton.B, NxButton.MENU -> closeRaSignIn(s)
                else -> Unit
            }
            return
        }
        if (s.confirm != null) {
            when (button) {
                NxButton.A -> {
                    screen.value = s.copy(confirm = null)
                    if (c.busy) { error.value = RA_BUSY; return }   // a sign-in or other job is still running: nothing would happen
                    when (s.confirm) { RaConfirm.SIGN_OUT -> c.signOut(); RaConfirm.RESET_ACCOUNT -> c.resetAccount(); RaConfirm.ERASE_ALL -> c.eraseAll() }
                    refreshRaSettings()
                }
                NxButton.B -> screen.value = s.copy(confirm = null)
                else -> Unit
            }
            return
        }
        val row = s.rows.getOrNull(selection.value.index)
        when (button) {
            NxButton.UP, NxButton.DOWN -> selection.value = selection.value.step(if (button == NxButton.UP) -1 else 1) { s.rows.getOrNull(it)?.selectable == true }
            // The value rows step either way (NX Redux ITEM_CYCLE): the durations stop at their ends, the sort order and the
            // On/Off rows wrap. Enable needs an account, as on A.
            NxButton.LEFT, NxButton.RIGHT -> if (row?.enabled != false) raSettingValue(row?.kind)?.let { v ->
                v.change(if (button == NxButton.RIGHT) 1 else -1, wrap = !v.clamps)
            }
            NxButton.A -> if (row?.enabled == false) Unit else when (row?.kind) {
                // A value row as on Settings: a picker row (many or long values) opens its picker, the others step
                // forward with wrap-around.
                RaSettingKind.ENABLED, RaSettingKind.NOTIFICATIONS, RaSettingKind.NOTIFICATION_SECONDS, RaSettingKind.PROGRESS_SECONDS,
                RaSettingKind.SORT_ORDER -> raSettingValue(row?.kind)?.let { v ->
                    if (row != null && raSettingsTouchRow(row).control == NxSettingControl.PICKER) openRaPicker(row) else v.change(1)
                }
                RaSettingKind.SIGN_IN ->
                    if (c.busy) error.value = RA_BUSY   // a sign-in (or other job) is still running
                    else screen.value = s.copy(signingIn = true, draftUser = settings.raUsername, draftPassword = "")
                RaSettingKind.SIGN_OUT -> screen.value = s.copy(confirm = RaConfirm.SIGN_OUT)
                RaSettingKind.DOWNLOAD_ALL -> {
                    val before = c.download.value
                    c.prefetchAll()
                    // Its progress page (B from there: the RetroAchievements page), even when it ended at once (every game
                    // cached already); a download that could not start goes back to that page.
                    if (c.download.value.let { it != null && it !== before }) { raSettingsSelection = selection.value; openRaDownload() } else leaveRaSettings()
                }
                RaSettingKind.RESET_ACCOUNT -> screen.value = s.copy(confirm = RaConfirm.RESET_ACCOUNT)
                RaSettingKind.ERASE_ALL -> screen.value = s.copy(confirm = RaConfirm.ERASE_ALL)
                RaSettingKind.RESET_SETTINGS -> {
                    // The non-credential defaults; the username and token stay.
                    settings.raEnabled = false; settings.raShowNotifications = true
                    settings.raNotificationSeconds = 3; settings.raProgressSeconds = 1; settings.raSortOrder = RaSortOrder.DEFAULT
                    refreshRaSettings()
                }
                RaSettingKind.SECTION, RaSettingKind.GAP, null -> Unit
            }
            NxButton.B -> leaveRaSettings()
            else -> Unit
        }
    }

    /**
     * A touch on a settings row's control ([com.nxredux.mobile.ui.nx.NxSettingsList]), selecting it: a segment or the
     * switch sets that value ([SettingValue.changeTo]), a picker row opens its picker. Nothing on a dimmed row or under
     * an open sign-in or confirm. The info icon is [AppState]'s ([AppState.showSettingInfo]).
     */
    fun onRaSettingEvent(s: Screen.RaSettings, event: NxSettingEvent) {
        if (s.signingIn || s.confirm != null) return
        val i = event.row
        val row = s.rows.getOrNull(i)?.takeIf { it.selectable && it.enabled } ?: return
        selection.value = selection.value.copy(index = i)
        when (event) {
            is NxSettingEvent.Choose -> raSettingValue(row.kind)?.changeTo(event.index)
            is NxSettingEvent.OpenPicker -> openRaPicker(row)
            is NxSettingEvent.Info -> Unit
        }
    }

    /** [row]'s picker: every value, the current one under the cursor; a pick sets it, the row found again by its kind. */
    private fun openRaPicker(row: RaSettingsRow) {
        val c = row.choices?.takeIf { it.labels.size >= 2 } ?: return
        host.openPicker(row.label, c.labels, c.index) { picked ->
            if (screen.value is Screen.RaSettings) raSettingValue(row.kind)?.changeTo(picked)
        }
    }

    /** Reads the cached games under the loading overlay; [keep] = land on the row last used in the list (returning from a game). */
    private fun openRaGames(keep: Boolean) {
        val c = achievements ?: return
        val here = screen.value
        loader.start {
            val games = try { c.games() } catch (e: RaStoreException) { error.value = RA_READ_FAILED; return@start }
            if (screen.value !== here) return@start   // the user moved on meanwhile
            screen.value = Screen.RaGames(games)
            selection.value = (if (keep) raGamesSelection else ListSelection(0, 0)).clampedTo(games.size)
        }
    }

    /**
     * Opens [game]'s achievements from [games] on row [row] (the list, or a game page beside it in the column browser:
     * that page stays until the new one has loaded, so the list never shows in between).
     */
    private fun openRaGame(game: RaGameView, games: Screen.RaGames, row: ListSelection) {
        val c = achievements ?: return
        val here = screen.value
        loader.start {
            val list = try { c.achievements(game.hash) } catch (e: RaStoreException) { error.value = RA_READ_FAILED; return@start }
            val badges = try { c.badgeListing(game.hash) } catch (e: RaStoreException) { emptyMap() }
            if (screen.value !== here) return@start   // the user moved on meanwhile
            raGamesScreen = games; raGamesSelection = row
            screen.value = Screen.RaGame(game, list.sortedBy(settings.raSortOrder), badges = badges)
            selection.value = ListSelection(0, list.size)
        }
    }

    /** UP/DOWN move over the action rows; A runs the highlighted one; B cancels a running job, closes the confirm, or goes back. */
    fun onRaButton(s: Screen.RetroAchievements, button: NxButton) {
        val c = achievements ?: run { host.showTools(); return }
        if (s.confirm != null) {
            when (button) {
                NxButton.A -> {
                    screen.value = Screen.RetroAchievements()
                    when (s.confirm) { RaConfirm.SIGN_OUT -> c.signOut(); RaConfirm.RESET_ACCOUNT -> c.resetAccount(); RaConfirm.ERASE_ALL -> c.eraseAll() }
                }
                NxButton.B -> screen.value = Screen.RetroAchievements()
                else -> Unit
            }
            return
        }
        when (button) {
            NxButton.UP -> selection.value = selection.value.up()
            NxButton.DOWN -> selection.value = selection.value.down()
            NxButton.A -> {
                val action = raRows(c.state.value, c.download.value).getOrNull(selection.value.index) ?: return
                raSelection = selection.value
                when (action) {
                    RaAction.ACHIEVEMENTS -> openRaGames(keep = false)
                    RaAction.SYNC -> c.syncNow()
                    RaAction.SETTINGS -> openRaSettings()
                    RaAction.DOWNLOAD -> openRaDownload()
                }
            }
            // B cancels a sync (its progress fills the page); beside a download, or idle, it leaves (the download runs on).
            // A sign-in or reset runs to its end.
            NxButton.B -> when (c.state.value) {
                is RaState.Syncing -> c.cancel()
                is RaState.Idle, is RaState.Prefetching -> { raSelection = selection.value; host.showTools() }
                else -> Unit
            }
            else -> Unit
        }
    }

    /** [game]'s achievement on row [row], from that page (or from the achievement beside it in the column browser). */
    private fun openRaAchievement(game: Screen.RaGame, row: ListSelection) {
        val a = visibleAchievements(game).getOrNull(row.index) ?: return
        raGameScreen = game; raGameSelection = row
        screen.value = Screen.RaAchievement(game.game, a, game.badges)
    }

    /** The browser screens: UP/DOWN move, A opens, Y (a game) toggles locked-only, B goes up one level. */
    fun onRaBrowserButton(s: Screen, button: NxButton) {
        when (button) {
            NxButton.UP -> selection.value = selection.value.up()
            NxButton.DOWN -> selection.value = selection.value.down()
            NxButton.A -> when (s) {
                is Screen.RaGames -> s.games.getOrNull(selection.value.index)?.let { openRaGame(it, s, selection.value) }
                is Screen.RaGame -> openRaAchievement(s, selection.value)
                else -> Unit
            }
            NxButton.Y -> (s as? Screen.RaGame)?.let {
                val toggled = it.copy(lockedOnly = !it.lockedOnly)
                screen.value = toggled
                selection.value = selection.value.clampedTo(visibleAchievements(toggled).size)
                // On touch the filter is an icon only, so it says what it did.
                host.showNotice(if (toggled.lockedOnly) "Showing locked achievements only" else "Showing all achievements")
            }
            NxButton.B -> raBack(s)
            else -> Unit
        }
    }

    /** One level up the RetroAchievements screens; the games list reloads so a sync or play since shows. */
    fun raBack(s: Screen) {
        when (s) {
            is Screen.RaAchievement -> {
                // Back to the game page it came from, with the same filter and row.
                val g = raGameScreen ?: Screen.RaGame(s.game, listOf(s.achievement), badges = s.badges)
                screen.value = g
                selection.value = raGameSelection.clampedTo(visibleAchievements(g).size)
            }
            is Screen.RaGame -> openRaGames(keep = true)   // a B cancel stays on the game
            is Screen.RaGames -> showRetroAchievements()
            is Screen.RaSettings -> onRaSettingsButton(s, NxButton.B)
            is Screen.RetroAchievements -> onRaButton(s, NxButton.B)
            else -> Unit
        }
    }
}
