package com.nxredux.mobile.ui

import com.nxredux.mobile.scraper.ArtworkController
import com.nxredux.mobile.scraper.JobPhase
import com.nxredux.mobile.scraper.KeyResult
import com.nxredux.mobile.scraper.SignInResult
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The error shown when an artwork action fails outside the controller's own handling (a full or failing store). */
internal const val ARTWORK_STORAGE_ERROR = "Couldn't save artwork data"

/**
 * Runs an artwork action; a failure other than cancellation becomes [ARTWORK_STORAGE_ERROR] instead of an uncaught
 * throw on the UI scope (which has no handler), as ContextMenuController.menuAction does.
 */
internal suspend fun guardArtwork(error: MutableStateFlow<String?>, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        error.value = ARTWORK_STORAGE_ERROR
    }
}

/**
 * Tools → Artwork → Settings: Replace existing art, the optional art (2D box art, Wheel, Generate mix), the art
 * sources' accounts (ScreenScraper sign-in modal and the SteamGridDB / TheGamesDB key prompt, drafts in the screen, as
 * RetroAchievements) and Reset artwork. [showPage] goes back to the Artwork page on a row.
 */
internal class ArtworkAccountSettings(
    private val scope: CoroutineScope,
    private val screen: MutableStateFlow<Screen>,
    private val selection: MutableStateFlow<ListSelection>,
    private val error: MutableStateFlow<String?>,
    private val settings: AppSettings,
    private val loader: ScreenLoader,
    private val controller: ArtworkController,
    private val host: ArtworkScreenHost,
    private val showPage: (ArtworkAction) -> Unit,
) {
    private val signedIn: Boolean get() = settings.ssUsername.isNotEmpty() && settings.ssPassword != null

    /**
     * In groups: Art to fetch (Replace existing art and the optional art: 2D box art, Wheel, Generate mix; only when
     * fetching is available; the Wheel shows On and is locked while Generate mix needs it, as NX Redux's "Needed by
     * Generate mix"), then Accounts (ScreenScraper's login / logout row, only in a build with its credentials; the
     * SteamGridDB key rows; TheGamesDB's key rows and its read-only allowance for the key in use, only once the user's key
     * is set), and Reset artwork last, after a gap.
     */
    private fun settingRows(): List<ArtworkSettingRow> = buildList {
        if (controller.available) {
            add(ArtworkSettingRow(ArtworkSettingKind.SECTION, "Art to fetch"))
            add(ArtworkSettingRow(ArtworkSettingKind.REPLACE, "Replace existing art", onOff(settings.artReplaceExisting)))
            add(ArtworkSettingRow(ArtworkSettingKind.BOX2D, "2D box art", onOff(settings.artBox2d)))
            add(ArtworkSettingRow(ArtworkSettingKind.WHEEL, "Wheel", onOff(settings.artWheel || settings.artMix), enabled = !settings.artMix))
            add(ArtworkSettingRow(ArtworkSettingKind.MIX, "Generate mix", onOff(settings.artMix)))
        }
        if (controller.ssAvailable || controller.sgdbAvailable || controller.tgdbSupported) {
            if (isNotEmpty()) add(ArtworkSettingRow(ArtworkSettingKind.GAP, ""))
            add(ArtworkSettingRow(ArtworkSettingKind.SECTION, "Accounts"))
        }
        // ScreenScraper is one row: login, or once signed in logout with today's requests (the name and password not shown).
        if (controller.ssAvailable) {
            // Login and logout are dimmed while art is downloading (the job runs under the login it started with).
            val idle = controller.state.value.phase != JobPhase.RUNNING
            if (signedIn) add(ArtworkSettingRow(
                ArtworkSettingKind.SS_LOGOUT, "ScreenScraper logout", controller.state.value.quota?.let { "${quotaText(it)} today" } ?: "—",
                enabled = idle,
            ))
            else add(ArtworkSettingRow(ArtworkSettingKind.SS_LOGIN, "ScreenScraper login", enabled = idle))
        }
        if (controller.sgdbAvailable) {
            val set = controller.sgdbKeySet
            add(ArtworkSettingRow(ArtworkSettingKind.SGDB_KEY, "SteamGridDB API key", if (set) "Set" else "Not set"))
            if (set) add(ArtworkSettingRow(ArtworkSettingKind.SGDB_REMOVE, "Remove SteamGridDB key"))
        }
        if (controller.tgdbSupported) {
            val set = controller.tgdbKeySet
            add(ArtworkSettingRow(ArtworkSettingKind.TGDB_KEY, "TheGamesDB API key", if (set) "Set" else "Not set"))
            if (set) add(ArtworkSettingRow(ArtworkSettingKind.TGDB_REMOVE, "Remove TheGamesDB key"))
        }
        if (controller.tgdbAvailable) {
            val left = controller.state.value.tgdbLeft
            add(ArtworkSettingRow(ArtworkSettingKind.TGDB_LEFT, "TheGamesDB", left?.let { "${groupThousands(it)} left this month" } ?: "—"))
        }
        if (isNotEmpty()) add(ArtworkSettingRow(ArtworkSettingKind.GAP, ""))
        add(ArtworkSettingRow(ArtworkSettingKind.RESET, "Reset artwork"))
    }

    /** The Settings page as it would open now (no modal), for the left pane beside Reset ([ArtworkScreenController.parentOf]). */
    fun settingsPage(): Screen.ArtworkSettings = Screen.ArtworkSettings(settingRows())

    fun openSettings(on: ArtworkSettingKind? = null) {
        val rows = settingRows()
        screen.value = Screen.ArtworkSettings(rows)
        selection.value = ListSelection(on?.let { k -> rows.indexOfFirst { it.kind == k } }?.coerceAtLeast(0) ?: 0, rows.size)
            .landOn { rows[it].selectable }
        // The quota and allowance rows follow through [refreshSettings]; a failed read keeps "—".
        if (controller.ssAvailable && signedIn) scope.launch { quietly { controller.refreshQuota() } }
        if (controller.tgdbAvailable) scope.launch { quietly { controller.tgdbAllowance() } }
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            Unit
        }
    }

    /** Rebuilds the Settings rows in place (an open modal kept), the highlight kept on its row ([keepRow]). */
    fun refreshSettings() {
        val s = screen.value as? Screen.ArtworkSettings ?: return
        showRows(s.copy(rows = settingRows()), s.rows)
    }

    /** Shows [next], its rows rebuilt from [before]'s, the highlight kept on the same row ([keepRow]). */
    private fun showRows(next: Screen.ArtworkSettings, before: List<ArtworkSettingRow>) {
        screen.value = next
        selection.value = ListSelection(keepRow(before, selection.value.index, next.rows), next.rows.size)
    }

    /**
     * Where the highlight goes when the rows change, so a second A never lands on another action: the same row kind
     * when it is still there; ScreenScraper's login for its logout (and back); otherwise the nearest row above it that
     * stays (a key's Remove row or TheGamesDB's allowance: that source's key row), or the first. Never a group label or gap.
     */
    private fun keepRow(before: List<ArtworkSettingRow>, index: Int, rows: List<ArtworkSettingRow>): Int {
        fun at(kind: ArtworkSettingKind) = rows.indexOfFirst { it.kind == kind && it.selectable }.takeIf { it >= 0 }
        val first = rows.indexOfFirst { it.selectable }.coerceAtLeast(0)
        val kind = before.getOrNull(index)?.takeIf { it.selectable }?.kind
            ?: return ListSelection(index, rows.size).clampedTo(rows.size).landOn { rows[it].selectable }.index
        at(kind)?.let { return it }
        when (kind) {
            ArtworkSettingKind.SS_LOGOUT -> at(ArtworkSettingKind.SS_LOGIN)
            ArtworkSettingKind.SS_LOGIN -> at(ArtworkSettingKind.SS_LOGOUT)
            else -> null
        }?.let { return it }
        return (index - 1 downTo 0).firstNotNullOfOrNull { i -> before[i].takeIf { it.selectable }?.let { at(it.kind) } } ?: first
    }

    fun draftUser(text: String) {
        val s = screen.value as? Screen.ArtworkSettings ?: return
        if (s.signingIn) screen.value = s.copy(draftUser = text)
    }

    fun draftPassword(text: String) {
        val s = screen.value as? Screen.ArtworkSettings ?: return
        if (s.signingIn) screen.value = s.copy(draftPassword = text)
    }

    fun draftKey(text: String) {
        val s = screen.value as? Screen.ArtworkSettings ?: return
        if (s.editingKey) screen.value = s.copy(draftKey = text)
    }

    private fun closeKey(s: Screen.ArtworkSettings) {
        showRows(s.copy(rows = settingRows(), editingKey = false, draftKey = ""), s.rows)
    }

    /**
     * Checks the typed key under the loader; the source's answer decides whether it is saved
     * ([ArtworkController.setSgdbKey] / [ArtworkController.setTgdbKey]). A saved TheGamesDB key's allowance is read next.
     */
    private fun submitKey(s: Screen.ArtworkSettings) {
        val key = s.draftKey.trim()
        val keyFor = s.keyFor
        if (key.isEmpty()) { error.value = "Enter a ${keyFor.label} API key"; return }
        closeKey(s)
        loader.start(message = "Checking key…", cancellable = false) {
            guardArtwork(error) {
                val result = if (keyFor == ArtKeyFor.TGDB) controller.setTgdbKey(key) else controller.setSgdbKey(key)
                if (result == KeyResult.OK) host.showNotice(keyResultText(result, keyFor)) else error.value = keyResultText(result, keyFor)
                if (result == KeyResult.OK && keyFor == ArtKeyFor.TGDB) refreshAllowance()
            }
            refreshSettings()
        }
    }

    /** Reads the allowance of the key now in use (after a TheGamesDB key change); a failed read keeps "—". */
    private fun refreshAllowance() {
        if (controller.tgdbAvailable) scope.launch { quietly { controller.tgdbAllowance() }; refreshSettings() }
    }

    private fun closeSignIn(s: Screen.ArtworkSettings) {
        showRows(s.copy(rows = settingRows(), signingIn = false, draftUser = "", draftPassword = ""), s.rows)
    }

    private fun submitSignIn(s: Screen.ArtworkSettings) {
        if (s.draftUser.isBlank() || s.draftPassword.isBlank()) { error.value = "Enter a username and password"; return }
        val user = s.draftUser.trim()
        val password = s.draftPassword
        closeSignIn(s)
        // A fetch that started while the prompt was open keeps its login: the row is dimmed now, as logout's.
        if (controller.state.value.phase == JobPhase.RUNNING) return
        loader.start(message = "Signing in…", cancellable = false) {
            guardArtwork(error) {
                val result = controller.signIn(user, password)
                if (result == SignInResult.OK) host.showNotice(signInText(result)) else error.value = signInText(result)
            }
            refreshSettings()
        }
    }

    fun onSettingsButton(s: Screen.ArtworkSettings, button: NxButton) {
        if (s.signingIn) {
            when (button) {
                NxButton.A -> submitSignIn(s)
                NxButton.B, NxButton.MENU -> closeSignIn(s)
                else -> Unit
            }
            return
        }
        if (s.confirmLogout) {
            when (button) {
                NxButton.A -> {
                    screen.value = s.copy(confirmLogout = false)
                    // A fetch that started while the dialog was open keeps the login.
                    if (controller.state.value.phase != JobPhase.RUNNING) { controller.signOut(); refreshSettings(); host.showNotice("Signed out") }
                    else refreshSettings()
                }
                NxButton.B, NxButton.MENU -> screen.value = s.copy(confirmLogout = false)
                else -> Unit
            }
            return
        }
        if (s.editingKey) {
            when (button) {
                NxButton.A -> submitKey(s)
                NxButton.B, NxButton.MENU -> closeKey(s)
                else -> Unit
            }
            return
        }
        val row = s.rows.getOrNull(selection.value.index)
        when (button) {
            NxButton.UP, NxButton.DOWN -> selection.value = selection.value.step(if (button == NxButton.UP) -1 else 1) { s.rows.getOrNull(it)?.selectable == true }
            NxButton.LEFT, NxButton.RIGHT -> if (row?.enabled == true && row.kind.toggle) toggle(row.kind)
            NxButton.A -> if (row?.enabled == true) when (row.kind) {
                ArtworkSettingKind.REPLACE, ArtworkSettingKind.BOX2D, ArtworkSettingKind.WHEEL, ArtworkSettingKind.MIX -> toggle(row.kind)
                ArtworkSettingKind.SS_LOGIN ->
                    screen.value = s.copy(signingIn = true, draftUser = settings.ssUsername, draftPassword = "")
                ArtworkSettingKind.SS_LOGOUT -> screen.value = s.copy(confirmLogout = true)
                ArtworkSettingKind.SGDB_KEY -> screen.value = s.copy(editingKey = true, draftKey = "", keyFor = ArtKeyFor.SGDB)
                ArtworkSettingKind.SGDB_REMOVE -> { controller.clearSgdbKey(); refreshSettings(); host.showNotice("SteamGridDB key removed") }
                ArtworkSettingKind.TGDB_KEY -> screen.value = s.copy(editingKey = true, draftKey = "", keyFor = ArtKeyFor.TGDB)
                ArtworkSettingKind.TGDB_REMOVE -> {
                    controller.clearTgdbKey(); refreshSettings(); host.showNotice("TheGamesDB key removed"); refreshAllowance()
                }
                ArtworkSettingKind.RESET -> openReset()
                ArtworkSettingKind.TGDB_LEFT, ArtworkSettingKind.SECTION, ArtworkSettingKind.GAP -> Unit
            }
            NxButton.B -> showPage(ArtworkAction.SETTINGS)
            else -> Unit
        }
    }

    /**
     * A touch on a Settings row's switch ([com.nxredux.mobile.ui.nx.NxSettingsList]), selecting it: an On/Off row set to
     * choice [NxSettingEvent.Choose.index] (Off, On), flipped only when that is not its value already. Nothing under an
     * open prompt or dialog; the info icon is [AppState]'s.
     */
    fun onSettingEvent(s: Screen.ArtworkSettings, event: NxSettingEvent) {
        if (s.dialogOpen || event !is NxSettingEvent.Choose) return
        val row = s.rows.getOrNull(event.row)?.takeIf { it.selectable && it.enabled } ?: return
        selection.value = selection.value.copy(index = event.row)
        if (row.kind.toggle && (event.index == 1) != (row.secondary == "On")) toggle(row.kind)
    }

    private fun onOff(on: Boolean) = if (on) "On" else "Off"

    /** Flips an On/Off row's setting (the Wheel only while Generate mix is off: its row is locked otherwise). */
    private fun toggle(kind: ArtworkSettingKind) {
        when (kind) {
            ArtworkSettingKind.REPLACE -> settings.artReplaceExisting = !settings.artReplaceExisting
            ArtworkSettingKind.BOX2D -> settings.artBox2d = !settings.artBox2d
            ArtworkSettingKind.WHEEL -> if (!settings.artMix) settings.artWheel = !settings.artWheel
            ArtworkSettingKind.MIX -> settings.artMix = !settings.artMix
            else -> return
        }
        refreshSettings()
    }

    // --- Reset ---

    private fun openReset() {
        // As Fetch by console: the Android games console is among them when shown (its screenshots go with its scope or All).
        val scopes = listOf(ResetScope("All", null)) + host.consoles().map { (name, tags) -> ResetScope(name, tags) }
        screen.value = Screen.ArtworkReset(scopes)
        selection.value = ListSelection(0, scopes.size)
    }

    fun onResetButton(s: Screen.ArtworkReset, button: NxButton) {
        s.confirm?.let { scope ->
            when (button) {
                NxButton.A -> {
                    screen.value = s.copy(confirm = null)
                    loader.start(message = "Deleting art…", cancellable = false) {
                        guardArtwork(error) {
                            host.showNotice(resetText(controller.resetArt(scope.tags)))
                        }
                        openSettings(ArtworkSettingKind.RESET)
                    }
                }
                NxButton.B -> screen.value = s.copy(confirm = null)
                else -> Unit
            }
            return
        }
        when (button) {
            NxButton.UP -> selection.value = selection.value.up()
            NxButton.DOWN -> selection.value = selection.value.down()
            NxButton.A -> s.scopes.getOrNull(selection.value.index)?.let { screen.value = s.copy(confirm = it) }
            NxButton.B -> openSettings(ArtworkSettingKind.RESET)
            else -> Unit
        }
    }
}
