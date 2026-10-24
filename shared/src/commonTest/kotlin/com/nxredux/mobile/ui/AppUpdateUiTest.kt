package com.nxredux.mobile.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.launcher.AppLauncher
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.netplay.FakeNetplayPlatform
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.update.AppRelease
import com.nxredux.mobile.update.UpdateController
import com.nxredux.mobile.update.UpdateFile
import com.nxredux.mobile.update.UpdateInstallResult
import com.nxredux.mobile.update.UpdatePlatform
import com.nxredux.mobile.update.UpdatePolicy
import com.nxredux.mobile.update.UpdateState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The updater in a real [AppState]: the main menu's popup, Settings → System's About update row and the host's install gate. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateUiTest {
    private class Settings : AppSettings {
        override var treeRef: ContentRef? = ContentRef("Home")
        override var onboardingDone: Boolean? = true
        override var onboardingStep: String? = null
        override var extraFolders: List<ExtraFolder> = emptyList()
        override var defaultTags: Map<String, String> = emptyMap()
        override var padTheme = PadTheme.DEFAULT
        override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap()
        override var gameListStyle: MenuStyle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_GAME_LIST_STYLE
        override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap()
        override var gameListOrientation: MenuOrientation = com.nxredux.mobile.settings.MenuStyles.DEFAULT_ORIENTATION
        override var gameListVerticalAlignment: com.nxredux.mobile.settings.VerticalListAlignment = com.nxredux.mobile.settings.VerticalListAlignment.DEFAULT
        override var toolApps: List<String> = emptyList()
        override var androidConsoleApps: List<String> = emptyList()
        override var androidGamesAsked = true
        override var androidPins: List<String> = emptyList()
        override var raEnabled = false
        override var raUsername = ""
        override var raToken: String? = null
        override var raShowNotifications = true
        override var raNotificationSeconds = 3
        override var raProgressSeconds = 1
        override var raSortOrder = RaSortOrder.DEFAULT
        override var ssUsername = ""
        override var ssPassword: String? = null
        override var sgdbApiKey: String? = null
        override var tgdbApiKey: String? = null
        override var artReplaceExisting = false
        override var artBox2d = false
        override var artWheel = false
        override var artMix = false
        override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showRecentHint = true; override var showSearchHint = true; override var showNetplayHint = true; override var menuPageTitle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = com.nxredux.mobile.settings.MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = com.nxredux.mobile.settings.AccentPreset.DEFAULT; override var secondaryAccent: com.nxredux.mobile.settings.SecondaryAccent = com.nxredux.mobile.settings.SecondaryAccent.DEFAULT
    }

    private object Picker : FolderPicker { override fun pickHome() {}; override fun pickExtra() {}; override fun forget(ref: ContentRef) {} }

    private class Runner : EmulatorRunner {
        override fun launch(session: GameSession, onStarted: () -> Unit) { onStarted() }
    }

    private class Launcher(private val apps: List<InstalledApp>) : AppLauncher {
        override suspend fun installedApps() = apps
        override fun label(id: String) = apps.firstOrNull { it.id == id }?.label
        override fun launch(id: String) = true
        override fun isHome() = false
        override fun setHome(on: Boolean) {}
        override suspend fun icon(id: String): ImageBitmap? = null
    }

    /** UpdateControllerTest's platform, cut down: the feed, a download that can hold at 50 of 100, and the busy reason. */
    private class FakePlatform : UpdatePlatform {
        override val dev = false
        override val installedCode = 3_000L
        override val installedVersion = "0.3.0"
        var feed: String = releaseJson("v0.4.0")
        var feedError: Exception? = null
        var busy: String? = null
        /** When set, `download` holds on it after reporting 50 of 100. */
        var downloadGate: CompletableDeferred<Unit>? = null
        var fetches = 0
        var downloads = 0
        override var lastSuccess = 0L
        override var lastFailure = 0L
        override var silenced: String? = null
        override var pendingUpdate: String? = null
        override suspend fun fetchLatest(): String { fetches++; feedError?.let { throw it }; return feed }
        override suspend fun download(release: AppRelease, onProgress: (done: Long, total: Long) -> Unit): UpdateFile {
            downloads++
            onProgress(50L, 100L)
            downloadGate?.await()
            return UpdateFile("/cache/update.apk")
        }
        override suspend fun sha256(file: UpdateFile) = "abc123"
        override fun discard(file: UpdateFile) {}
        override fun busyReason(): String? = busy
        override fun canInstall() = true
        override fun askInstallPermission() {}
        override fun install(file: UpdateFile, clear: () -> Boolean, onResult: (UpdateInstallResult) -> Unit) {}
        override fun resumeInstall() = true
        override fun keepAlive(active: Boolean, text: String?) {}
        override fun showWaiting(reason: String?) {}
    }

    private class Rig(val state: AppState, val updater: UpdateController, val platform: FakePlatform, val settings: Settings)

    private val source = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Super Nintendo (SFC)"),
            "Home/Roms/Super Nintendo (SFC)" to listOf("Super Mario Kart (USA).sfc"),
        ),
    )

    /** An AppState with an updater over [platform]; [load]: the main menu shows (and its automatic check runs). */
    private fun TestScope.rig(
        platform: FakePlatform = FakePlatform(), withUpdater: Boolean = true, launcher: AppLauncher? = null,
        settings: Settings = Settings(), netplay: FakeNetplayPlatform? = null, load: Boolean = true, now: () -> Long = { NOW },
    ): Rig {
        // The controller's polls run in the background scope, so an unfinished wait does not keep the test running.
        val updater = UpdateController(platform, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)), now = now)
        val state = AppState(
            AppDeps(
                settings, source, Runner(), Picker, compute = UnconfinedTestDispatcher(testScheduler), launcher = launcher,
                netplay = netplay, updater = updater.takeIf { withUpdater },
            ),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        if (load) { state.load(); runCurrent() }
        return Rig(state, updater, platform, settings)
    }

    private fun TestScope.pastGuard() { advanceTimeBy(ANDROID_ASK_GUARD_MILLIS + 1); runCurrent() }

    /** Settings -> System, its About group's rows (between the group's label and the gap before Licenses). */
    private fun Rig.openAbout(): List<SettingsRow> {
        state.openSettings(SettingsSection.SYSTEM)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        val from = rows.indexOfFirst { it.kind == SettingsKind.SECTION && it.label == "About" } + 1
        return rows.subList(from, rows.indexOfFirst { it.kind == SettingsKind.SECTION && it.label == "Licenses" } - 1)
    }

    private fun Rig.updateRow(): SettingsRow? = assertIs<Screen.Settings>(state.screen.value).rows.firstOrNull { it.kind == SettingsKind.APP_UPDATE }

    /** A on About's update row. */
    private fun Rig.pressUpdateRow() {
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        state.select(rows.indexOfFirst { it.kind == SettingsKind.APP_UPDATE })
        state.onButton(NxButton.A)
    }

    // --- The popup ---

    @Test fun the_main_menu_asks_about_a_newer_release_and_a_downloads_it() = runTest {
        val r = rig(FakePlatform().apply { downloadGate = CompletableDeferred() })
        assertIs<Screen.Systems>(r.state.screen.value)
        assertEquals("0.4.0", r.state.updatePopup.value?.version)
        r.state.onButton(NxButton.DOWN)                                     // the dialog takes the pad
        assertNotNull(r.state.updatePopup.value)
        pastGuard()
        r.state.onButton(NxButton.A)                                        // Update
        assertNull(r.state.updatePopup.value)
        assertIs<UpdateState.Downloading>(r.updater.state.value)
    }

    @Test fun up_and_down_on_the_popup_scroll_its_notes() = runTest {
        val r = rig()
        pastGuard()
        val steps = ArrayList<Int>()
        val seen = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { r.state.updateScroll.collect { steps += it } }
        r.state.onButton(NxButton.DOWN)
        r.state.onButton(NxButton.DOWN)
        r.state.onButton(NxButton.UP)
        assertEquals(listOf(1, 1, -1), steps)
        assertNotNull(r.state.updatePopup.value)                           // scrolling answers nothing
        assertEquals(0, r.platform.downloads)
        seen.cancel()
    }

    @Test fun b_on_the_popup_silences_the_version() = runTest {
        val r = rig()
        pastGuard()
        r.state.onButton(NxButton.B)                                        // Not now
        assertNull(r.state.updatePopup.value)
        assertEquals("0.4.0", r.platform.silenced)
        assertIs<UpdateState.Available>(r.updater.state.value)             // About's row still offers it
        assertEquals(0, r.platform.downloads)
    }

    @Test fun back_on_the_popup_is_not_now() = runTest {
        val r = rig()
        pastGuard()
        assertTrue(r.state.back())
        assertNull(r.state.updatePopup.value)
        assertEquals("0.4.0", r.platform.silenced)
        assertIs<Screen.Systems>(r.state.screen.value)                     // the menu stays
    }

    @Test fun the_popup_ignores_presses_just_after_it_shows() = runTest {
        val r = rig()
        r.state.onButton(NxButton.A)                                        // a stray press meant for the menu
        assertTrue(r.state.back())                                          // taken, not answered
        assertNotNull(r.state.updatePopup.value)
        assertEquals(0, r.platform.downloads)
        assertNull(r.platform.silenced)
    }

    @Test fun the_android_games_question_goes_first() = runTest {
        val settings = Settings().apply { androidGamesAsked = false }
        val r = rig(launcher = Launcher(listOf(InstalledApp("a/A", "Alpha", game = true))), settings = settings)
        assertNotNull(r.state.androidGamesAsk.value)
        assertNotNull(r.updater.popup.value)                                // the controller has one to ask
        assertNull(r.state.updatePopup.value)                               // but the games question shows
        pastGuard()
        r.state.onButton(NxButton.B); advanceUntilIdle()                   // its Not now
        assertNull(r.state.androidGamesAsk.value)
        assertEquals("0.4.0", r.state.updatePopup.value?.version)          // then the update's
        assertNull(r.platform.silenced)                                     // the B was the games question's
    }

    @Test fun the_menu_checks_when_it_comes_up_or_comes_back_not_when_rebuilt() = runTest {
        var clock = NOW
        val p = FakePlatform().apply { feedError = IllegalStateException("offline") }
        val r = rig(p, now = { clock })
        assertEquals(1, p.fetches)                                          // the menu's first show
        clock += UpdatePolicy.RETRY_MS
        r.state.openTools()                                                 // a tab switch rebuilds the menu in place
        assertEquals(1, p.fetches)
        r.state.onForeground(); runCurrent()                                // back over the menu: due again
        assertEquals(2, p.fetches)
        assertNull(r.state.updatePopup.value)                               // a failed automatic check says nothing
    }

    @Test fun no_updater_no_popup_and_no_row() = runTest {
        val r = rig(withUpdater = false)
        assertNull(r.state.updatePopup.value)
        assertEquals(SettingsKind.COPY_INFO, r.openAbout().last().kind)
    }

    // --- About's row ---

    @Test fun about_ends_with_the_info_rows_then_the_update_row() = runTest {
        val r = rig(load = false)
        val rows = r.openAbout()
        assertEquals(SettingsKind.APP_UPDATE, rows.last().kind)
        assertEquals("Copy device info", rows[rows.size - 2].label)
        assertTrue(rows.dropLast(2).all { it.kind == SettingsKind.INFO })
        assertTrue(rows.none { it.label == "Font" })   // the font is in the Licenses text, not a row
        assertEquals("Check for updates", rows.last().label)               // Idle
        assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Check")), settingsHints(rows, rows.lastIndex))
    }

    @Test fun the_row_follows_the_updater() = runTest {
        val p = FakePlatform().apply { feed = releaseJson("v0.3.0") }
        val r = rig(p, load = false)
        r.openAbout()
        r.pressUpdateRow()                                                  // Idle: Check for updates
        assertEquals("Up to date (0.3.0)", r.updateRow()?.label)            // the page refreshed itself
        assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Check")), settingsHints(assertIs<Screen.Settings>(r.state.screen.value).rows, r.state.selection.value.index))

        p.feedError = IllegalStateException("offline")
        r.pressUpdateRow()                                                  // UpToDate: checks again
        assertEquals("Couldn't reach GitHub", r.updateRow()?.label)
        r.pressUpdateRow()                                                  // Failed without a release: checks again
        assertEquals(3, p.fetches)
        assertEquals("Couldn't reach GitHub", r.updateRow()?.label)

        p.feedError = null
        p.feed = releaseJson("v0.4.0")
        r.pressUpdateRow()
        assertEquals("Update to 0.4.0", r.updateRow()?.label)
        assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Update")), settingsHints(assertIs<Screen.Settings>(r.state.screen.value).rows, r.state.selection.value.index))
        assertNull(r.state.updatePopup.value)                               // About's check never pops up

        p.downloadGate = CompletableDeferred()
        r.pressUpdateRow()                                                  // Available: the confirm first
        assertEquals("0.4.0", r.state.updateConfirm.value?.version)
        assertEquals(0, p.downloads)
        pastGuard()
        r.state.onButton(NxButton.A)                                        // its Update
        assertNull(r.state.updateConfirm.value)
        assertEquals("Downloading 50%", r.updateRow()?.label)
        assertEquals(listOf(Hint(NxButton.B, "Back")), settingsHints(assertIs<Screen.Settings>(r.state.screen.value).rows, r.state.selection.value.index))
        r.pressUpdateRow()                                                  // Downloading: nothing
        assertEquals(1, p.downloads)
        assertEquals(4, p.fetches)

        p.busy = "Close your game to install"
        p.downloadGate?.complete(Unit); runCurrent()
        assertEquals("Close your game to install", r.updateRow()?.label)    // Waiting: its reason
        r.pressUpdateRow()                                                  // Waiting: nothing
        assertEquals(1, p.downloads)
        assertIs<UpdateState.Waiting>(r.updater.state.value)
    }

    @Test fun a_on_a_failed_download_downloads_again() = runTest {
        val p = FakePlatform()
        val r = rig(p, load = false)
        r.openAbout()
        p.downloadGate = CompletableDeferred<Unit>().apply { completeExceptionally(IllegalStateException("cut")) }
        r.pressUpdateRow()                                                  // check
        r.pressUpdateRow(); pastGuard(); r.state.onButton(NxButton.A)     // Update to 0.4.0, confirmed: fails
        assertEquals("Update failed", r.updateRow()?.label)
        assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Update")), settingsHints(assertIs<Screen.Settings>(r.state.screen.value).rows, r.state.selection.value.index))
        p.downloadGate = CompletableDeferred()
        r.pressUpdateRow()                                                  // Failed with a release: the confirm
        assertEquals("0.4.0", r.state.updateConfirm.value?.version)
        pastGuard(); r.state.onButton(NxButton.A)
        assertEquals(2, p.downloads)
        assertEquals(1, p.fetches)
        assertEquals("Downloading 50%", r.updateRow()?.label)
    }

    @Test fun the_update_confirm_cancels_and_ignores_the_press_that_opened_it() = runTest {
        val p = FakePlatform()
        val r = rig(p, load = false)
        r.openAbout()
        r.pressUpdateRow()                                                  // check: Update to 0.4.0
        r.pressUpdateRow()                                                  // the confirm
        r.state.onButton(NxButton.A)                                        // a second A straight after: ignored
        assertNotNull(r.state.updateConfirm.value)
        assertEquals(0, p.downloads)
        pastGuard()
        r.state.onButton(NxButton.B)                                        // Cancel
        assertNull(r.state.updateConfirm.value)
        assertEquals(0, p.downloads)
        assertIs<UpdateState.Available>(r.updater.state.value)
        assertNull(p.silenced)                                              // Cancel is not Not now
        r.pressUpdateRow(); pastGuard()
        assertTrue(r.state.back())                                          // Back cancels too, and leaves Settings open
        assertNull(r.state.updateConfirm.value)
        assertIs<Screen.Settings>(r.state.screen.value)
        assertEquals(0, p.downloads)
    }

    // --- The host's install gate ---

    @Test fun the_host_holds_the_install_off_the_menu_and_under_netplay() = runTest {
        val np = FakeNetplayPlatform()
        val r = rig(netplay = np)
        assertNull(r.updater.hostBusy())                                    // the main menu, nothing over it
        val systems = assertIs<Screen.Systems>(r.state.screen.value).tabs.flatMap { it.rows }.flatMap { (it as? MainRow.System)?.systems.orEmpty() }
        pastGuard()
        r.state.onButton(NxButton.B)                                        // the popup's Not now
        r.state.openSystem(systems.first { it.tag == "SFC" })
        assertEquals("", r.updater.hostBusy())                              // off the main menu
        r.state.onButton(NxButton.Y)                                        // the Netplay sheet
        assertNotNull(r.state.netplay?.page?.value)
        assertEquals("Close Netplay to install", r.updater.hostBusy())
    }

    @Test fun a_released_host_leaves_the_updater_waiting_as_off_the_menu() = runTest {
        val r = rig()
        r.state.releaseUpdater()
        assertEquals("", r.updater.hostBusy())
    }

    @Test fun releasing_an_older_host_keeps_the_newer_one_and_a_menu_takes_it_back() = runTest {
        val r = rig()
        val newer = AppState(
            AppDeps(Settings(), source, Runner(), Picker, compute = UnconfinedTestDispatcher(testScheduler), updater = r.updater),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        assertEquals("", r.updater.hostBusy())                              // the newer instance hosts now (not loaded: off the menu)
        val newerHook = r.updater.hostBusy
        r.state.releaseUpdater()                                            // the older one goes: the newer one's hook stays
        assertTrue(r.updater.hostBusy === newerHook)
        newer.releaseUpdater()
        assertEquals("", r.updater.hostBusy())
        r.state.onForeground()                                              // the older one's main menu shows: it hosts again
        assertNull(r.updater.hostBusy())
    }

    private companion object {
        const val NOW = 100_000_000_000L

        fun releaseJson(tag: String) =
            """{"tag_name":"$tag","body":"Faster menus.","assets":[{"name":"NXReduxMobile-$tag.apk","size":100,"digest":"sha256:abc123","browser_download_url":"https://example.com/$tag.apk"}]}"""
    }
}
