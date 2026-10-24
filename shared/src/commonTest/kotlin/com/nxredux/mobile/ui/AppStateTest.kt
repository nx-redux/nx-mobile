package com.nxredux.mobile.ui

import com.nxredux.mobile.library.IndexedSystem
import com.nxredux.mobile.settings.SwitcherLayout
import com.nxredux.mobile.achievements.RaDownloadPhase
import com.nxredux.mobile.achievements.RaState
import com.nxredux.mobile.achievements.RaResponse
import com.nxredux.mobile.achievements.FakeRaHasher
import com.nxredux.mobile.achievements.FakeRaHttp
import com.nxredux.mobile.achievements.FakeRaStore
import com.nxredux.mobile.achievements.RaController
import com.nxredux.mobile.achievements.RaHomeTree
import com.nxredux.mobile.achievements.RaPaths
import com.nxredux.mobile.achievements.RaGamePointer
import com.nxredux.mobile.achievements.RaGamePointers
import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.achievements.fakePlatform
import com.nxredux.mobile.achievements.setsJson
import com.nxredux.mobile.gametime.FixedOffsetCalendar
import com.nxredux.mobile.gametime.GameTimeData
import com.nxredux.mobile.gametime.LocalCalendar
import com.nxredux.mobile.gametime.WindowTotals
import com.nxredux.mobile.gametime.GameTotal
import androidx.compose.ui.graphics.ImageBitmap
import com.nxredux.mobile.launcher.AppLauncher
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.library.CollectionCountCache
import com.nxredux.mobile.library.CountingRomsSource
import com.nxredux.mobile.cheats.db.*
import com.nxredux.mobile.library.Entry
import com.nxredux.mobile.library.MoveResult
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.IndexedExtra
import com.nxredux.mobile.library.IndexedGame
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.LibraryIndexCodec
import com.nxredux.mobile.library.LibraryScanner
import com.nxredux.mobile.library.ListingFault
import com.nxredux.mobile.library.ListingUnavailable
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.library.Sidecars
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.fits
import com.nxredux.mobile.library.path
import com.nxredux.mobile.runner.Companion
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.ImportResult
import com.nxredux.mobile.settings.ImportSource
import com.nxredux.mobile.settings.SaveTransfer
import com.nxredux.mobile.settings.SaveTransferRules
import com.nxredux.mobile.settings.TransferReport
import com.nxredux.mobile.storage.AllFilesAccess
import com.nxredux.mobile.storage.FakeAllFilesAccess
import com.nxredux.mobile.storage.FakeNotificationAccess
import com.nxredux.mobile.storage.ImportSummary
import com.nxredux.mobile.storage.RetroArchFolders
import com.nxredux.mobile.storage.SkipReason
import com.nxredux.mobile.storage.SkippedFile
import com.nxredux.mobile.storage.NotificationAccess
import com.nxredux.mobile.storage.FakeLocalFiles
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.LocalFiles
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.StateFormat
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.settings.ConsoleArt
import com.nxredux.mobile.settings.GameListArt
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.settings.SecondaryAccent
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.settings.GroupTweak
import com.nxredux.mobile.settings.PadGroup
import com.nxredux.mobile.settings.PadLayout
import com.nxredux.mobile.settings.PadLayouts
import com.nxredux.mobile.settings.PadScreen
import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.settings.PadSlot
import com.nxredux.mobile.settings.PadVibration
import com.nxredux.mobile.settings.GameRumble
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.settings.Sources
import com.nxredux.mobile.storage.LibraryIndexStore
import com.nxredux.mobile.storage.MemoryLibraryIndexStore
import com.nxredux.mobile.storage.MemoryPrivateFiles
import com.nxredux.mobile.storage.PrivateFiles
import com.nxredux.mobile.storage.MemorySidecarStore
import com.nxredux.mobile.storage.NoUserData
import com.nxredux.mobile.storage.Recent
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.RecentsRecorder
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.storage.Shortcut
import com.nxredux.mobile.storage.Shortcuts
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.scraper.AndroidArtStore
import com.nxredux.mobile.storage.ToolPins
import com.nxredux.mobile.storage.UserDataStore
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxSettingControl
import com.nxredux.mobile.ui.nx.control
import com.nxredux.mobile.ui.nx.HomeFrame
import com.nxredux.mobile.ui.nx.HomeToolMode
import com.nxredux.mobile.ui.nx.HomeTopKind
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.homeBoard
import com.nxredux.mobile.ui.nx.homeCarouselShownRow
import com.nxredux.mobile.ui.nx.FooterMode
import com.nxredux.mobile.ui.nx.LauncherChrome
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.nxTap
import kotlin.jvm.JvmName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred

/** Every styled main menu tab on List ([AppStateTest]'s settings fixture); a test sets a tab with `LIST_TABS + mapOf(tab to style)`. */
private val LIST_TABS = mapOf(MenuCategory.CONSOLES to MenuStyle.LIST, MenuCategory.COLLECTIONS to MenuStyle.LIST, MenuCategory.TOOLS to MenuStyle.LIST)

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateTest {
    private class MemorySettings(override var treeRef: ContentRef? = null) : AppSettings {
        override var extraFolders: List<ExtraFolder> = emptyList()
        override var defaultTags: Map<String, String> = emptyMap()
        override var padLayouts: com.nxredux.mobile.settings.PadLayoutMap = emptyMap()
        override var debugLog = false
        override var secondScreen = true
        override var pauseWhenUnfocused = true
        override var bottomScreen = com.nxredux.mobile.runner.BottomScreen.AUTO
        override var menuPad = true
        override var saveFormat = SaveFormat.DEFAULT
        override var stateFormat = StateFormat.DEFAULT
        override var useExtractedFileName = false
        override var padTheme: PadTheme = PadTheme.DEFAULT
        // Pinned to List, the default before Carousel: the walks below step Consoles, Collections and Tools as lists. The
        // real defaults (a missing tab is its MenuStyles.defaultStyleOf) are tested with menuStyles = emptyMap() ([freshSettings]).
        override var menuStyles: Map<MenuCategory, MenuStyle> = LIST_TABS
        override var gameListStyle: MenuStyle = MenuStyle.LIST
        override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap()
        override var gameListOrientation: MenuOrientation = MenuStyles.DEFAULT_ORIENTATION
        override var gameListVerticalAlignment: VerticalListAlignment = VerticalListAlignment.DEFAULT
        override var toolApps: List<String> = emptyList()
        override var androidConsoleApps: List<String> = emptyList()
        override var androidGamesAsked = false
        override var allFilesAsked = false
        override var onboardingDone: Boolean? = true   // done: tests of other things never meet onboarding ([freshInstall] does)
        override var onboardingStep: String? = null
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
        override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showRecentHint = true; override var showSearchHint = true; override var showNetplayHint = true; override var menuPageTitle = MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = AccentPreset.DEFAULT
        override var secondaryAccent: SecondaryAccent = SecondaryAccent.DEFAULT
        override var switcherLayout = com.nxredux.mobile.settings.SwitcherLayout.DEFAULT
        override var optionTextSize = com.nxredux.mobile.settings.OptionTextSize.DEFAULT
    }
    /** [starts] false: the runner refuses every game (never reports a start); [started] then holds the callbacks to fire by hand. */
    private class RecordingRunner(private val starts: Boolean = true) : EmulatorRunner {
        val launched = ArrayList<GameSession>()
        val started = ArrayList<() -> Unit>()
        override fun launch(session: GameSession, onStarted: () -> Unit) {
            launched += session
            if (starts) onStarted() else started += onStarted
        }
    }
    private class NoopPicker : FolderPicker {
        var picks = 0; var extraPicks = 0; val forgotten = ArrayList<ContentRef>()
        override fun pickHome() { picks++ }
        override fun pickExtra() { extraPicks++ }
        override fun forget(ref: ContentRef) { forgotten += ref }
    }
    private class FakeLauncher(var apps: List<InstalledApp> = emptyList(), @set:JvmName("putHome") var home: Boolean = false) : AppLauncher {   // the setter's JVM name would clash with setHome()
        val launched = mutableListOf<String>()
        val homeRequests = mutableListOf<Boolean>()
        var missing = emptySet<String>()   // ids label() treats as uninstalled
        override suspend fun installedApps() = apps
        override fun label(id: String) = if (id in missing) null else apps.firstOrNull { it.id == id }?.label
        override fun launch(id: String): Boolean { launched += id; return id !in missing }
        override fun isHome() = home
        override fun setHome(on: Boolean) { homeRequests += on }
        val iconAsks = mutableListOf<String>()
        override suspend fun icon(id: String): ImageBitmap? { iconAsks += id; return null }   // no icon: tiles draw the generic app glyph
    }

    /** Tools -> RetroAchievements, wherever it sorts among the tools. */
    private fun openRa(state: AppState) {
        state.openTools()
        state.select(state.screen.value.toolRows().indexOfFirst { it.kind == ToolKind.RETRO_ACHIEVEMENTS })
        state.openSelected()
    }

    private fun menuKinds(state: AppState) = state.screen.value.contextMenu?.items?.map { it.kind } ?: emptyList()

    /** Opens the context menu on the highlighted row and presses A on [kind]. */
    private fun choose(state: AppState, kind: ContextKind) {
        state.onButton(NxButton.MENU)
        val kinds = menuKinds(state)
        val at = kinds.indexOf(kind); assertTrue(at >= 0, "no $kind in $kinds")
        repeat(at) { state.onButton(NxButton.DOWN) }
        state.onButton(NxButton.A)
    }

    /** Tools -> Settings -> Library -> Unassigned games: a list the main menu does not carry. */
    private fun openUnassigned(state: AppState) {
        state.openSettings(SettingsSection.LIBRARY)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        state.select(rows.indexOfFirst { it.kind == SettingsKind.UNASSIGNED }); state.openSelected()
        assertTrue(assertIs<Screen.Games>(state.screen.value).system.unassigned)
    }

    private val source = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Nintendo DS (NDS)", "Commodore 64 (C64)", "Game Boy (GB)", "Game Boy Advance (GBA)"),
            "Home/Roms/Nintendo DS (NDS)" to listOf("Mario Kart DS.nds"),
            "Home/Roms/Commodore 64 (C64)" to listOf("Boulder Dash 2.chd"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
            "sd/Loose" to listOf("Kirby.gba", "thing.chd", "v.img"),
            "OldRoms" to listOf("Game Boy (GB)"),
            "OldRoms/Game Boy (GB)" to listOf("Tetris.gb"),
        ),
    )

    /** Settings as a fresh install has them: no home, onboarding never decided. */
    private fun freshInstall() = MemorySettings().apply { onboardingDone = null }

    /** The welcome's Get started, then the Text size page's Continue: on to the folder steps. */
    private fun AppState.getStarted() { onButton(NxButton.A); continueTextSize() }
    private fun AppState.continueTextSize() {
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(screen.value).step)
        select(1); onButton(NxButton.A)
    }

    // An install that already has a home folder decided onboarding before it existed: it reads as done, never the welcome.
    @Test fun an_existing_install_with_a_home_counts_onboarding_as_done() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { onboardingDone = null }
        val state = state(settings)
        state.load()
        assertEquals(true, settings.onboardingDone)
        assertIs<Screen.Systems>(state.screen.value)
    }

    // A fresh install (no home) decides it is not done.
    @Test fun a_fresh_install_has_onboarding_to_do() = runTest {
        val settings = freshInstall()
        state(settings).load()
        assertEquals(false, settings.onboardingDone)
    }

    @Test fun a_fresh_install_opens_on_the_welcome_then_the_folder_steps() = runTest {
        val settings = freshInstall()
        val state = state(settings)
        state.load()
        val s = assertIs<Screen.Onboarding>(state.screen.value)
        assertEquals(OnboardingStep.WELCOME, s.step)
        state.getStarted()                                         // Get started
        assertIs<Screen.PickFolder>(state.screen.value)
    }

    @Test fun after_the_folder_steps_onboarding_continues_at_the_handheld_and_done_lands_on_home() = runTest {
        val settings = freshInstall()
        val state = state(settings)
        state.load(); state.getStarted()                           // welcome → pick folder
        state.setHomePast(ContentRef("Home"))
        state.skipFirstExtra()
        assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(state.screen.value).step)
        var guard = 0
        while ((state.screen.value as? Screen.Onboarding)?.step != OnboardingStep.DONE && guard++ < 30) {
            state.select((state.screen.value as Screen.Onboarding).rows.lastIndex); state.onButton(NxButton.A)   // the no / later choice, or Continue
        }
        assertEquals(OnboardingStep.DONE, assertIs<Screen.Onboarding>(state.screen.value).step)
        state.onButton(NxButton.A)                                         // Start
        assertEquals(true, settings.onboardingDone)
        assertNull(settings.onboardingStep)
        val home = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.HOME, home.category)
    }

    // A built-in second screen (a dual-screen handheld's): setup asks which screen is the bottom one right after the text
    // size, before the folder steps. The main screen's button stores main, the second screen's stores second, Skip stores nothing.
    @Test fun a_built_in_second_screen_adds_the_bottom_screen_step_after_the_text_size() = runTest {
        for (pick in listOf("main", "second", "skip")) {
            val settings = freshInstall()
            val state = state(settings, builtInSecond = { true })
            state.load(); state.onButton(NxButton.A); state.continueTextSize()
            val s = assertIs<Screen.Onboarding>(state.screen.value)
            assertEquals(OnboardingStep.BOTTOM_SCREEN, s.step)
            assertTrue(s.canGoBack)
            assertEquals(listOf("This is the bottom screen", "Skip"), s.rows.map { it.label })
            state.onButton(NxButton.B)                                         // back to the text size, and on again
            assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(state.screen.value).step, pick)
            state.continueTextSize()
            when (pick) {
                "main" -> { state.select(0); state.onButton(NxButton.A) }
                "second" -> state.onboardingBottomScreenOnSecond()
                else -> { state.select(1); state.onButton(NxButton.A) }
            }
            assertEquals(if (pick == "skip") "auto" else pick, settings.bottomScreen.value, pick)
            assertIs<Screen.PickFolder>(state.screen.value)
            state.onButton(NxButton.B)                                         // the first folder step goes back to it
            assertEquals(OnboardingStep.BOTTOM_SCREEN, assertIs<Screen.Onboarding>(state.screen.value).step, pick)
            state.select(1); state.onButton(NxButton.A)
            state.setHomePast(ContentRef("Home")); state.skipFirstExtra()
            assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(state.screen.value).step, pick)
            state.onButton(NxButton.B)                                         // the folder steps, not the bottom screen
            assertIs<Screen.AddFirstExtra>(state.screen.value)
        }
    }

    @Test fun without_a_built_in_second_screen_the_step_is_left_out() = runTest {
        val settings = freshInstall()
        val state = state(settings)
        state.load(); state.getStarted(); state.setHomePast(ContentRef("Home")); state.skipFirstExtra()
        assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(state.screen.value).step)
        state.onboardingBottomScreenOnSecond()                                 // a stray tap: not on the step, nothing stored
        assertEquals("auto", settings.bottomScreen.value)
        assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(state.screen.value).step)
    }

    // B walks a first run back one page at a time: the handheld question → the ROMs folder → the home folder pick → the
    // text size question; going forward again comes back to the handheld question.
    @Test fun b_on_the_handheld_step_goes_back_through_the_folder_steps_to_the_welcome() = runTest {
        val settings = freshInstall()
        val state = state(settings)
        state.load(); state.getStarted(); state.setHomePast(ContentRef("Home")); state.skipFirstExtra()
        assertEquals(true, assertIs<Screen.Onboarding>(state.screen.value).canGoBack)
        state.onButton(NxButton.B)
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        assertEquals(false, state.menuPadShown())
        state.onButton(NxButton.B)
        assertEquals(Recents.HOME, assertIs<Screen.FolderSummary>(state.screen.value).extraId)   // the home's page
        state.onButton(NxButton.B)
        assertIs<Screen.PickFolder>(state.screen.value)
        state.onButton(NxButton.B)
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(state.screen.value).step)
        state.continueTextSize()
        assertIs<Screen.PickFolder>(state.screen.value)
        state.setHomePast(ContentRef("Home"))
        state.select(1); state.onButton(NxButton.A)                        // Skip
        assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(state.screen.value).step)
    }

    // The folder steps are setup pages too: numbered with the rest ("Setup · Step n of m"), choices as rows.
    @Test fun the_folder_steps_are_numbered_with_the_rest_of_setup() = runTest {
        val settings = freshInstall()
        val state = state(settings, allFiles = FakeAllFilesAccess(declared = true, isGranted = false))
        state.load(); state.getStarted()
        assertIs<Screen.PermissionsStep>(state.screen.value)
        assertEquals("Setup · Step 1 of 10" to true, state.setupChrome())
        state.select(1); state.onButton(NxButton.A)                        // Skip
        assertIs<Screen.PickFolder>(state.screen.value)
        assertEquals("Setup · Step 2 of 10" to true, state.setupChrome())
        state.setHome(ContentRef("Home"))
        assertIs<Screen.FolderSummary>(state.screen.value)                 // the home's games: its page
        assertEquals("Setup · Step 3 of 11" to true, state.setupChrome())
        state.onButton(NxButton.A)                                         // Continue
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        assertEquals("Setup · Step 4 of 11" to true, state.setupChrome())
        assertEquals(ListSelection(0, 2), state.selection.value)
        state.select(1); state.onButton(NxButton.A)                        // Skip
        assertEquals("Setup · Step 5 of 11", assertIs<Screen.Onboarding>(state.screen.value).title)
        state.onButton(NxButton.B); state.onButton(NxButton.B); state.onButton(NxButton.B)
        assertIs<Screen.PickFolder>(state.screen.value)
        state.onButton(NxButton.B)
        assertIs<Screen.PermissionsStep>(state.screen.value)                  // still not granted: its step again
    }

    // Without an All files step (access granted, or not declared) setup starts at the home folder pick.
    @Test fun without_the_all_files_step_the_home_pick_is_step_one() = runTest {
        val state = state(freshInstall())
        state.load(); state.getStarted()
        assertEquals("Setup · Step 1 of 9" to true, state.setupChrome())
        state.onButton(NxButton.B)
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(state.screen.value).step)
    }

    // Once setup is done, B on the home folder pick (a lost home) does not reopen the welcome.
    @Test fun b_on_the_home_folder_pick_after_setup_does_not_reopen_the_welcome() = runTest {
        val settings = MemorySettings().apply { onboardingDone = true }
        val state = state(settings)
        state.load()
        assertIs<Screen.PickFolder>(state.screen.value)
        state.onButton(NxButton.B)
        assertIs<Screen.PickFolder>(state.screen.value)
    }

    @Test fun a_killed_app_resumes_onboarding_at_the_saved_step() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { onboardingDone = false; onboardingStep = "ACCENT" }
        val state = state(settings)
        state.load()
        assertEquals(OnboardingStep.ACCENT, assertIs<Screen.Onboarding>(state.screen.value).step)
    }

    // The welcome is the name, the pitch and one Get started: setup is not skippable.
    @Test fun the_welcome_is_the_name_the_pitch_and_one_get_started() = runTest {
        val state = state(freshInstall())
        state.load()
        val s = assertIs<Screen.Onboarding>(state.screen.value)
        assertEquals(listOf("NX Redux", "Pick up. Select a game. Play."), s.details)
        assertEquals(listOf("Get started"), s.rows.map { it.label })
        assertEquals(false, s.canGoBack)
    }

    // Text size is asked first, right after the welcome (every page after it reads at that size): its value row flips
    // Large / Small in place, Continue goes on to the folder steps, and B from the first folder step comes back to it.
    @Test fun setup_asks_the_text_size_right_after_the_welcome() = runTest {
        val settings = freshInstall()
        val state = state(settings)
        state.load(); state.onButton(NxButton.A)                           // Get started
        val s = assertIs<Screen.Onboarding>(state.screen.value)
        assertEquals(OnboardingStep.TEXT_SIZE, s.step)
        assertEquals("Setup", s.title)
        assertEquals(true, s.canGoBack)
        assertEquals(listOf("Text size" to "Large", "Continue" to null), s.rows.map { it.label to it.secondary })
        state.select(0); state.onButton(NxButton.A)
        assertEquals(com.nxredux.mobile.settings.OptionTextSize.SMALL, settings.optionTextSize)
        assertEquals("Small", assertIs<Screen.Onboarding>(state.screen.value).rows[0].secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(com.nxredux.mobile.settings.OptionTextSize.LARGE, settings.optionTextSize)
        state.select(1); state.onButton(NxButton.A)                        // Continue
        assertIs<Screen.PickFolder>(state.screen.value)
        assertNull(settings.onboardingStep?.takeIf { it == OnboardingStep.TEXT_SIZE.name })
        state.onButton(NxButton.B)
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(state.screen.value).step)
        state.onButton(NxButton.B)
        assertEquals(OnboardingStep.WELCOME, assertIs<Screen.Onboarding>(state.screen.value).step)
    }

    // The customise question no longer asks the text size (setup asked it first).
    @Test fun the_customise_question_leaves_text_size_out() = runTest {
        val (state, _) = atCustomise(yes = false)
        assertTrue(assertIs<Screen.Onboarding>(state.screen.value).details.none { "text size" in it.lowercase() })
    }

    // The menu pad stays hidden through the welcome, the folder steps and onboarding up to its own step.
    @Test fun the_menu_pad_is_hidden_until_onboarding_asks_about_it() = runTest {
        val settings = freshInstall().apply { menuPad = true }
        val state = state(settings)
        state.load()
        assertEquals(false, state.menuPadShown())                          // welcome
        state.getStarted()
        assertIs<Screen.PickFolder>(state.screen.value)
        assertEquals(false, state.menuPadShown())
        state.setHomePast(ContentRef("Home")); state.skipFirstExtra()
        assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(state.screen.value).step)
        assertEquals(false, state.menuPadShown())
    }

    // On its own step the pad previews the selected answer; after it, the answer holds.
    /** First setup up to "Add a ROMs folder?", then sd/Loose added: its summary page. */
    private fun TestScope.atFolderSummary(): Pair<AppState, MemorySettings> {
        val settings = freshInstall()
        val state = state(settings)
        state.load(); state.getStarted()
        state.setHomePast(ContentRef("Home"))
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        state.addExtra(ContentRef("sd/Loose"))
        return state to settings
    }

    // --- The home folder's own page: what it holds, and its games outside Roms moved in (On by default) ---

    private fun messyHome() = FakeRomsSource(
        dirs = mapOf(
            "M" to listOf("Roms", "Games"), "M/Roms" to listOf("Game Boy (GB)"), "M/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "M/Games" to listOf("Kirby.gba", "notes.txt"),
        ),
        bytes = mapOf("M/Games/Kirby.gba" to ByteArray(4)),
    )

    @Test fun a_home_with_games_shows_its_count_and_moves_the_outside_ones_by_default() = runTest {
        val src = messyHome()
        val state = state(freshInstall(), src = src)
        state.load(); state.getStarted()                           // welcome → pick
        state.setHome(ContentRef("M")); advanceUntilIdle()
        val s = assertIs<Screen.FolderSummary>(state.screen.value)
        assertEquals(Recents.HOME, s.extraId)
        assertEquals(listOf("Found 2 games in M", "Game Boy (1)", "Game Boy Advance (1)", "", "1 of them is outside Roms."), folderSummaryDetails(s))
        assertEquals(SummaryRowKind.MOVE to "On", folderSummaryRows(s).first().let { it.kind to it.row.secondary })
        assertEquals("Setup · Step 2 of 10", state.setupChrome().first)
        state.select(state.selection.value.count - 1); state.onButton(NxButton.A); advanceUntilIdle()   // Continue: the move
        assertTrue("Kirby.gba" in src.listChildren(ContentRef("M/Roms/Game Boy Advance (GBA)")).map { it.name })
        assertEquals(listOf("notes.txt"), src.listChildren(ContentRef("M/Games")).map { it.name })   // unrecognised: stays
        assertTrue("Moved 1 into M/Roms" in folderSummaryDetails(assertIs<Screen.FolderSummary>(state.screen.value)))
        state.select(state.selection.value.count - 1); state.onButton(NxButton.A)
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        assertEquals("Setup · Step 3 of 10", state.setupChrome().first)
    }

    @Test fun a_home_whose_games_are_all_in_roms_has_no_move_row() = runTest {
        val state = state(freshInstall())
        state.load(); state.getStarted()
        state.setHome(ContentRef("Home")); advanceUntilIdle()
        val s = assertIs<Screen.FolderSummary>(state.screen.value)
        assertEquals(listOf(SummaryRowKind.CONTINUE), folderSummaryRows(s).map { it.kind })
        state.onButton(NxButton.A)
        assertIs<Screen.AddFirstExtra>(state.screen.value)
    }

    @Test fun an_empty_home_skips_the_page() = runTest {
        val state = state(freshInstall(), src = FakeRomsSource(dirs = mapOf("E" to emptyList())))
        state.load(); state.getStarted()
        state.setHome(ContentRef("E")); advanceUntilIdle()
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        assertEquals("Setup · Step 2 of 9", state.setupChrome().first)
    }

    @Test fun b_walks_from_the_home_page_to_the_pick_and_from_the_extra_offer_back_to_it() = runTest {
        val state = state(freshInstall(), src = messyHome())
        state.load(); state.getStarted()
        state.setHome(ContentRef("M")); advanceUntilIdle()
        state.onButton(NxButton.B)
        assertEquals(Screen.PickFolder, state.screen.value)
        state.setHome(ContentRef("M")); advanceUntilIdle()
        state.select(0); state.onButton(NxButton.A)                         // Move off
        state.select(state.selection.value.count - 1); state.onButton(NxButton.A)
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        state.onButton(NxButton.B)
        assertEquals(Recents.HOME, assertIs<Screen.FolderSummary>(state.screen.value).extraId)
    }

    // A console split over two Roms folders (the handheld's GBA and MGBA) is merged by the home page's move, and every
    // line naming a moved game (a collection, a Recent, a pin, a hidden game) follows it.
    @Test fun the_home_page_merges_a_split_console_and_the_lines_follow() = runTest {
        val gba = "S/Roms/Game Boy Advance (GBA)"; val mgba = "S/Roms/Game Boy Advance (MGBA)"
        val src = FakeRomsSource(
            dirs = mapOf(
                "S" to listOf("Roms", "Collections"), "S/Roms" to listOf("Game Boy Advance (GBA)", "Game Boy Advance (MGBA)"),
                gba to listOf("A.gba"), mgba to listOf("K.gba"), "S/Collections" to listOf("Favs.txt"),
            ),
            texts = mapOf("S/Collections/Favs.txt" to "/Roms/Game Boy Advance (MGBA)/K.gba\n/Roms/Game Boy Advance (GBA)/A.gba\n"),
            bytes = mapOf("$mgba/K.gba" to ByteArray(4)),
        )
        val old = "/Roms/Game Boy Advance (MGBA)/K.gba"; val new = "/Roms/Game Boy Advance (GBA)/K.gba"
        val data = FakeUserData(recents = listOf(Recent(old)), pins = listOf(Shortcut(old, "Kirby")), hidden = listOf(old))
        val state = state(freshInstall(), src = src, data = data)
        state.load(); state.getStarted()
        state.setHome(ContentRef("S")); advanceUntilIdle()
        val s = assertIs<Screen.FolderSummary>(state.screen.value)
        assertTrue("1 console has more than one folder; they are merged into one." in folderSummaryDetails(s))
        assertEquals(SummaryRowKind.MOVE to "On", folderSummaryRows(s).first().let { it.kind to it.row.secondary })
        state.select(folderSummaryRows(s).lastIndex); state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals(setOf("A.gba", "K.gba"), src.listChildren(ContentRef(gba)).map { it.name }.toSet())
        assertTrue("Game Boy Advance (MGBA)" !in src.listChildren(ContentRef("S/Roms")).map { it.name })   // emptied: gone
        assertEquals(listOf(Recent(new)), data.recents)
        assertEquals(listOf(Shortcut(new, "Kirby")), data.pins)
        assertEquals(listOf(new), data.hidden)
        assertEquals("$new\n/Roms/Game Boy Advance (GBA)/A.gba\n", src.readText(ContentRef("S/Collections/Favs.txt")))
    }

    // A long merge rewrites the lines of the games already moved every few seconds, so a process killed mid-merge does not
    // leave every moved game's Recents, pins and collection lines at the old paths.
    @Test fun a_merge_still_running_has_already_rewritten_the_lines_of_moved_games() = runTest {
        val gba = "S/Roms/Game Boy Advance (GBA)"; val mgba = "S/Roms/Game Boy Advance (MGBA)"
        val base = FakeRomsSource(
            dirs = mapOf("S" to listOf("Roms"), "S/Roms" to listOf("Game Boy Advance (GBA)", "Game Boy Advance (MGBA)"),
                gba to listOf("A.gba"), mgba to listOf("K.gba", "L.gba")),
            bytes = mapOf("$mgba/K.gba" to ByteArray(4), "$mgba/L.gba" to ByteArray(4)),
        )
        val stuck = kotlinx.coroutines.CompletableDeferred<Unit>()
        val src = object : RomsSource by base {
            override suspend fun moveFile(from: ContentRef, name: String, to: ContentRef): Boolean {
                if (name == "L.gba") stuck.await()                            // the second game never finishes moving
                return base.moveFile(from, name, to)
            }
        }
        val old = "/Roms/Game Boy Advance (MGBA)/K.gba"
        val data = FakeUserData(recents = listOf(Recent(old)))
        val state = state(freshInstall(), src = src, data = data)
        state.load(); state.getStarted()
        state.setHome(ContentRef("S")); advanceUntilIdle()
        val s = assertIs<Screen.FolderSummary>(state.screen.value)
        state.select(folderSummaryRows(s).lastIndex); state.onButton(NxButton.A)
        advanceTimeBy(6_000); runCurrent()
        assertEquals(listOf(Recent("/Roms/Game Boy Advance (GBA)/K.gba")), data.recents)
        stuck.complete(Unit); advanceUntilIdle()
    }

    @Test fun adding_a_folder_in_setup_shows_its_summary() = runTest {
        val (state, _) = atFolderSummary()
        val s = assertIs<Screen.FolderSummary>(state.screen.value)
        assertEquals(3, s.summary.games)
        assertEquals(2, s.summary.unassigned)                              // thing.chd and v.img: nothing recognised
        assertEquals(listOf("Game Boy Advance" to 1), s.summary.byConsole)
        assertTrue("2 games need a console. Find them in Settings → Library → Unassigned games." in folderSummaryDetails(s))
        assertEquals("Setup · Step 4 of 11" to true, state.setupChrome())
        assertEquals(   // a line per console, then a blank line before what follows
            listOf("Found 3 games in ${s.name}", "Game Boy Advance (1)", "", "2 games need a console. Find them in Settings → Library → Unassigned games."),
            folderSummaryDetails(s),
        )
        assertEquals(SummaryRowKind.MOVE, folderSummaryRows(s).first().kind)
        assertEquals(ListSelection(0, folderSummaryRows(s).size), state.selection.value)   // on the Move option, not Continue
        assertEquals(false, state.menuPadShown())
    }

    @Test fun continue_without_moving_goes_on_to_the_handheld_question() = runTest {
        val (state, settings) = atFolderSummary()
        state.select(state.selection.value.count - 1); state.onButton(NxButton.A)                                         // Continue
        assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(state.screen.value).step)
        assertEquals("Setup · Step 5 of 11", assertIs<Screen.Onboarding>(state.screen.value).title)
        assertEquals(listOf(ContentRef("sd/Loose")), settings.extraFolders.map { it.ref })
        assertTrue("Kirby.gba" in source.listChildren(ContentRef("sd/Loose")).map { it.name })
    }

    @Test fun the_move_box_starts_unchecked_and_toggles() = runTest {
        val (state, _) = atFolderSummary()
        assertEquals(false, assertIs<Screen.FolderSummary>(state.screen.value).move)
        state.select(summaryRow(state, SummaryRowKind.MOVE)); state.onButton(NxButton.A)
        assertEquals(true, assertIs<Screen.FolderSummary>(state.screen.value).move)
        state.onButton(NxButton.A)
        assertEquals(false, assertIs<Screen.FolderSummary>(state.screen.value).move)
    }

    // The Move row is a value row ("< Off >"): LEFT and RIGHT flip it too.
    @Test fun left_and_right_flip_the_move_row() = runTest {
        val (state, _) = atFolderSummary()
        state.select(summaryRow(state, SummaryRowKind.MOVE))
        state.onButton(NxButton.RIGHT)
        assertEquals(true, assertIs<Screen.FolderSummary>(state.screen.value).move)
        state.onButton(NxButton.LEFT)
        assertEquals(false, assertIs<Screen.FolderSummary>(state.screen.value).move)
        assertEquals(summaryRow(state, SummaryRowKind.MOVE), state.selection.value.index)
    }

    private fun summaryRow(state: AppState, kind: SummaryRowKind): Int =
        folderSummaryRows(assertIs<Screen.FolderSummary>(state.screen.value)).indexOfFirst { it.kind == kind }

    @Test fun continue_with_move_moves_placed_games_and_keeps_the_rest() = runTest {
        val (state, settings) = atFolderSummary()
        state.select(summaryRow(state, SummaryRowKind.MOVE)); state.onButton(NxButton.A)                        // Move: on
        state.select(summaryRow(state, SummaryRowKind.CONTINUE)); state.onButton(NxButton.A)                        // Continue
        advanceUntilIdle()
        assertTrue("Kirby.gba" in source.listChildren(ContentRef("Home/Roms/Game Boy Advance (GBA)")).map { it.name })
        assertEquals(listOf("thing.chd", "v.img"), source.listChildren(ContentRef("sd/Loose")).map { it.name })
        assertEquals(listOf(ContentRef("sd/Loose")), settings.extraFolders.map { it.ref })   // unassigned games remain there
        val done = assertIs<Screen.FolderSummary>(state.screen.value)
        assertEquals(MoveResult(moved = 1), done.result)
        assertEquals(3, done.summary.games)                                // still what the folder held: "Found 3 games", then "Moved 1…"
        state.onButton(NxButton.A)
        assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(state.screen.value).step)
    }

    // A provider error during the move is a result, not a crash: the page says what could not move.
    @Test fun a_failing_move_shows_its_failures() = runTest {
        val src = FakeRomsSource(source.snapshotDirs(), bytes = mapOf("sd/Loose/Kirby.gba" to ByteArray(4))).apply { moveThrows = true }
        val settings = freshInstall()
        val state = state(settings, src)
        state.load(); state.getStarted(); state.setHomePast(ContentRef("Home")); state.addExtra(ContentRef("sd/Loose"))
        state.select(summaryRow(state, SummaryRowKind.MOVE)); state.onButton(NxButton.A)
        state.select(summaryRow(state, SummaryRowKind.CONTINUE)); state.onButton(NxButton.A)
        advanceUntilIdle()
        val done = assertIs<Screen.FolderSummary>(state.screen.value)
        assertEquals(MoveResult(failed = 1), done.result)
        assertTrue(folderSummaryDetails(done).any { "1 could not be moved" in it }, "${folderSummaryDetails(done)}")
    }

    @Test fun b_on_the_summary_goes_back_to_the_roms_folder_step() = runTest {
        val (state, _) = atFolderSummary()
        state.onButton(NxButton.B)
        assertIs<Screen.AddFirstExtra>(state.screen.value)
    }

    @Test fun adding_a_folder_from_settings_notices_its_unassigned_games() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSettings(SettingsSection.LIBRARY)
        state.addExtra(ContentRef("sd/Loose"))
        assertEquals("2 games need a console: Library → Unassigned games.", state.notice.value)
    }

    /** A home already picked, onboarding resumed at [step] (HANDHELD for the handheld's own steps: answer Yes from there). */
    private fun TestScope.atOnboardingStep(step: OnboardingStep): Pair<AppState, MemorySettings> {
        val settings = MemorySettings(ContentRef("Home")).apply { onboardingDone = false; onboardingStep = step.name }
        val state = state(settings)
        state.load()
        return state to settings
    }

    // RetroAchievements comes right after artwork (its download runs while the layout is picked): no menu pad there yet.
    @Test fun retroachievements_follows_artwork_before_the_menu_pad_step() = runTest {
        val (state, settings) = atOnboardingStep(OnboardingStep.ARTWORK)
        settings.menuPad = true
        state.select(1); state.onButton(NxButton.A)                       // Later
        assertEquals(OnboardingStep.RA_SIGN_IN, assertIs<Screen.Onboarding>(state.screen.value).step)
        assertEquals(false, state.menuPadShown())
    }

    @Test fun yes_on_the_handheld_step_shows_its_formats_and_they_are_saved() = runTest {
        val (state, settings) = atOnboardingStep(OnboardingStep.HANDHELD)
        state.select(0); state.onButton(NxButton.A)
        assertEquals(OnboardingStep.SAVE_FORMATS, assertIs<Screen.Onboarding>(state.screen.value).step)
        state.select(0); state.onButton(NxButton.RIGHT)
        assertEquals(SaveFormat.DEFAULT.step(1), settings.saveFormat)
        assertEquals(SaveFormat.DEFAULT.step(1).label, assertIs<Screen.Onboarding>(state.screen.value).rows[0].secondary)
        state.select(1); state.onButton(NxButton.LEFT)
        assertEquals(StateFormat.DEFAULT.step(-1), settings.stateFormat)
        state.select(2); state.onButton(NxButton.A)
        assertEquals(true, settings.useExtractedFileName)
        assertEquals(2, state.selection.value.index)                       // a value change keeps the row
        state.select(3); state.onButton(NxButton.A)
        assertEquals(OnboardingStep.IMPORT_SAVES, assertIs<Screen.Onboarding>(state.screen.value).step)
        state.select(3); state.onButton(NxButton.A)                        // Skip
        assertEquals(OnboardingStep.SYNC_OFFER, assertIs<Screen.Onboarding>(state.screen.value).step)
    }

    @Test fun no_on_the_handheld_step_goes_to_import_then_artwork() = runTest {
        val (state, _) = atOnboardingStep(OnboardingStep.HANDHELD)
        state.select(1); state.onButton(NxButton.A)
        assertEquals(OnboardingStep.IMPORT_SAVES, assertIs<Screen.Onboarding>(state.screen.value).step)
        state.select(3); state.onButton(NxButton.A)                        // Skip
        assertEquals(OnboardingStep.ARTWORK, assertIs<Screen.Onboarding>(state.screen.value).step)
    }

    @Test fun the_accent_page_steps_both_accents_in_place_then_continues() = runTest {
        val (state, settings) = atOnboardingStep(OnboardingStep.ACCENT)
        val rows = assertIs<Screen.Onboarding>(state.screen.value).rows
        assertEquals(listOf("Accent", "Secondary accent", "Continue"), rows.map { it.label })
        assertEquals(0, state.selection.value.index)
        val before = state.screen.value
        state.onButton(NxButton.RIGHT)
        assertEquals(AccentPreset.DEFAULT.step(1), settings.accent)
        assertNotEquals(before, state.screen.value)                         // a new screen, so App re-reads the colours
        assertEquals(settings.accent, assertIs<Screen.Onboarding>(state.screen.value).accent)
        assertEquals(settings.accent.label, assertIs<Screen.Onboarding>(state.screen.value).rows[0].secondary)
        state.onButton(NxButton.LEFT)
        assertEquals(AccentPreset.DEFAULT, settings.accent)
        val secondary = settings.secondaryAccent
        state.onButton(NxButton.DOWN)
        state.onButton(NxButton.RIGHT)
        assertEquals(secondary.step(1), settings.secondaryAccent)
        assertEquals(OnboardingStep.ACCENT, assertIs<Screen.Onboarding>(state.screen.value).step)   // a value row stays on the page
        state.onButton(NxButton.DOWN)
        state.onButton(NxButton.A)                                          // Continue
        assertEquals(OnboardingStep.DONE, assertIs<Screen.Onboarding>(state.screen.value).step)
        assertEquals(secondary.step(1), settings.secondaryAccent)
    }

    @Test fun start_on_the_last_page_finishes_setup_as_press_start_does() = runTest {
        val (state, settings) = atOnboardingStep(OnboardingStep.DONE)
        assertEquals("Press Start", assertIs<Screen.Onboarding>(state.screen.value).rows.single().label)
        state.onButton(NxButton.START)
        assertEquals(true, settings.onboardingDone)
        assertIs<Screen.Systems>(state.screen.value)
    }

    /** Onboarding at "Customise the layout?" on a device in [portrait] with or without a [controller]; Yes when [yes]. */
    private fun TestScope.atCustomise(portrait: Boolean = true, controller: Boolean = false, yes: Boolean = true): Pair<AppState, MemorySettings> {
        val (state, settings) = atOnboardingStep(OnboardingStep.CUSTOMISE)
        state.onPadContext(portrait, controller)
        assertEquals(OnboardingStep.CUSTOMISE, assertIs<Screen.Onboarding>(state.screen.value).step)
        if (yes) {
            state.select(0); state.onButton(NxButton.A)
        }
        return state to settings
    }

    private fun step(state: AppState) = assertIs<Screen.Onboarding>(state.screen.value).step

    // an app killed on a customise page resumes on it (portrait, no controller: the pad pages apply).
    @Test fun a_kill_on_the_pad_page_resumes_on_it() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { onboardingDone = false; onboardingStep = OnboardingStep.PAD.name }
        val state = state(settings)
        state.load()
        assertEquals(OnboardingStep.PAD, step(state))
    }

    // "Use the defaults" writes nothing, even over values an earlier run chose.
    @Test fun customise_no_goes_to_accent_and_writes_nothing() = runTest {
        val (state, settings) = atCustomise(yes = false)
        settings.menuPageTitle = false; settings.gameListStyle = MenuStyle.BACKDROP; settings.menuPad = false
        state.select(1); state.onButton(NxButton.A)                         // Use the defaults
        assertEquals(OnboardingStep.ACCENT, step(state))
        assertEquals(false, settings.menuPageTitle); assertEquals(MenuStyle.BACKDROP, settings.gameListStyle); assertEquals(false, settings.menuPad)
    }

    @Test fun customise_walks_its_pages_and_writes_each() = runTest {
        val (state, settings) = atCustomise()
        assertEquals(OnboardingStep.PAD, step(state))
        state.select(1); state.onButton(NxButton.RIGHT)                     // the theme row: Charcoal → Retro
        state.select(2); state.onButton(NxButton.A)                         // Continue (the pad Show, as it is)
        assertEquals(OnboardingStep.TITLE, step(state))
        state.select(0); state.onButton(NxButton.A)                         // the page title: Hide (flips in place)
        state.select(2); state.onButton(NxButton.A)                         // Continue (the hints Show, as they are)
        val tabs = listOf(OnboardingStep.TAB_HOME to MenuCategory.HOME, OnboardingStep.TAB_CONSOLES to MenuCategory.CONSOLES,
            OnboardingStep.TAB_COLLECTIONS to MenuCategory.COLLECTIONS, OnboardingStep.TAB_TOOLS to MenuCategory.TOOLS)
        for ((st, c) in tabs) {
            assertEquals(st, step(state))
            state.select(0)
            while (MenuStyles.styleOf(settings.menuStyles, c) != MenuStyle.LIST) state.onButton(NxButton.RIGHT)   // the layout row steps
            state.select(state.selection.value.count - 1); state.onButton(NxButton.A)   // Continue
        }
        assertEquals(OnboardingStep.GAME_LISTS, step(state))
        state.select(0)
        while (settings.gameListStyle != MenuStyle.BACKDROP) state.onButton(NxButton.RIGHT)
        state.select(state.selection.value.count - 1); state.onButton(NxButton.A)   // Continue
        assertEquals(OnboardingStep.ACCENT, step(state))
        assertEquals(true, settings.menuPad); assertEquals(PadTheme.RETRO, settings.padTheme)
        assertEquals(false, settings.menuPageTitle); assertEquals(true, settings.menuButtonHints)
        for ((_, c) in tabs) assertEquals(MenuStyle.LIST, MenuStyles.styleOf(settings.menuStyles, c))
        assertEquals(MenuStyle.BACKDROP, settings.gameListStyle)
    }

    // the pad off (no controller) leaves no buttons: theme, title and hints are skipped.
    // The pad row flips in place (A, a tap, LEFT/RIGHT) so the pad can be compared; only Continue goes on.
    @Test fun the_pad_page_flips_in_place_and_off_skips_theme_title_and_hints() = runTest {
        val (state, settings) = atCustomise()
        assertEquals(listOf("On-screen pad" to "Show", "Pad theme" to "Charcoal", "Continue" to null), assertIs<Screen.Onboarding>(state.screen.value).rows.map { it.label to it.secondary })
        assertEquals(true, state.menuPadShown())
        val total = assertIs<Screen.Onboarding>(state.screen.value).title
        state.select(0); state.onButton(NxButton.A)                         // Hide: the pad goes at once, the page stays
        assertEquals(OnboardingStep.PAD, step(state))
        assertEquals(false, settings.menuPad); assertEquals(false, state.menuPadShown())
        assertNotEquals(total, assertIs<Screen.Onboarding>(state.screen.value).title)   // the count follows at once
        state.onButton(NxButton.RIGHT)                                      // and back
        assertEquals(true, settings.menuPad)
        state.onButton(NxButton.LEFT)
        assertEquals(false, settings.menuPad)
        assertEquals(listOf("On-screen pad", "Continue"), assertIs<Screen.Onboarding>(state.screen.value).rows.map { it.label })   // no pad: no theme row
        state.select(1); state.onButton(NxButton.A)                         // Continue
        assertEquals(OnboardingStep.TAB_HOME, step(state))
    }

    // The theme is a second row on the pad page: it flips in place too.
    @Test fun the_pad_theme_row_flips_in_place() = runTest {
        val (state, settings) = atCustomise()
        val before = state.screen.value
        state.select(1); state.onButton(NxButton.A)                         // Charcoal → Retro, the page stays
        assertEquals(PadTheme.RETRO, settings.padTheme)
        assertEquals(OnboardingStep.PAD, step(state))
        assertNotEquals(before, state.screen.value)                        // republished: App re-reads the theme
        state.onButton(NxButton.LEFT)
        assertEquals(PadTheme.CHARCOAL, settings.padTheme)
    }

    // A phone with no controller, set up in landscape, still gets the pad page: it uses the pad when held upright.
    @Test fun a_phone_without_a_controller_gets_the_pad_page_in_landscape_too() = runTest {
        val (state, _) = atCustomise(portrait = false)
        assertEquals(OnboardingStep.PAD, step(state))
        assertEquals("Turn your phone upright to see the pad.", assertIs<Screen.Onboarding>(state.screen.value).details.last())
        state.onPadContext(portrait = true, controller = false)            // turned upright: the pad shows, the line goes
        assertTrue("Turn your phone upright to see the pad." !in assertIs<Screen.Onboarding>(state.screen.value).details)
    }

    // The page title and the button hints are value rows on one page (one picture above shows the pair): each flips in place.
    @Test fun the_page_title_and_hints_rows_flip_in_place() = runTest {
        val (state, settings) = atCustomise(controller = true)
        assertEquals(OnboardingStep.TITLE, step(state))
        assertEquals(listOf("Page title" to "Show", "Button hints" to "Show", "Continue" to null), assertIs<Screen.Onboarding>(state.screen.value).rows.map { it.label to it.secondary })
        state.select(0); state.onButton(NxButton.A)
        assertEquals(false, settings.menuPageTitle)
        state.select(1); state.onButton(NxButton.RIGHT)
        assertEquals(false, settings.menuButtonHints)
        assertEquals(OnboardingStep.TITLE, step(state))
        assertEquals(1, state.selection.value.index)                       // the row stays selected
        state.select(2); state.onButton(NxButton.A)
        assertEquals(OnboardingStep.TAB_HOME, step(state))
    }

    // Setup shows the menu pad on its own page only: every other page has the full height; the menu then follows the choice.
    @Test fun the_menu_pad_shows_only_on_the_pad_page_during_setup() = runTest {
        val (state, settings) = atCustomise()
        assertEquals(true, settings.menuPad)
        assertEquals(true, state.menuPadShown())                           // PAD
        state.select(2); state.onButton(NxButton.A)                         // Continue
        assertEquals(OnboardingStep.TITLE, step(state))
        assertEquals(false, state.menuPadShown())
        while (step(state) != OnboardingStep.DONE) { state.select(state.selection.value.count - 1); state.onButton(NxButton.A) }
        assertEquals(false, state.menuPadShown())                          // DONE too
        state.onButton(NxButton.A)
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(true, state.menuPadShown())                           // the menu: the choice
    }

    // A layout page: the layout row above one picture of it. LEFT / RIGHT or A step the layout in place (applied at once,
    // the picture following); only Continue goes on.
    @Test fun a_layout_page_steps_its_layout_in_place_and_continue_goes_on() = runTest {
        val (state, settings) = atCustomise(controller = true)
        state.select(2); state.onButton(NxButton.A)                         // past the page title page
        assertEquals(OnboardingStep.TAB_HOME, step(state))
        val page = assertIs<Screen.Onboarding>(state.screen.value)
        assertEquals(listOf("Home layout" to "Grid", "Home stats" to "Show", "Continue" to null), page.rows.map { it.label to it.secondary })
        assertEquals(LayoutShot.Tab(MenuCategory.HOME, MenuStyle.GRID), page.shot)
        state.onButton(NxButton.RIGHT)                                      // Grid -> Carousel
        assertEquals(MenuStyle.CAROUSEL, settings.menuStyles[MenuCategory.HOME])
        assertEquals(LayoutShot.Tab(MenuCategory.HOME, MenuStyle.CAROUSEL), assertIs<Screen.Onboarding>(state.screen.value).shot)
        state.onButton(NxButton.A)                                          // A on the row: the next one, the page stays
        assertEquals(MenuStyle.MINIMAL, settings.menuStyles[MenuCategory.HOME])
        assertEquals(OnboardingStep.TAB_HOME, step(state))
        state.onButton(NxButton.LEFT)                                       // back to Carousel
        assertEquals(MenuStyle.CAROUSEL, settings.menuStyles[MenuCategory.HOME])
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)           // Home stats: Hide, the picture without the strip
        assertEquals(false, settings.menuHomeStats)
        assertEquals(LayoutShot.Tab(MenuCategory.HOME, MenuStyle.CAROUSEL, stats = false), assertIs<Screen.Onboarding>(state.screen.value).shot)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)           // Continue
        assertEquals(OnboardingStep.TAB_CONSOLES, step(state))
    }

    // A tab's page: its orientation row while it is a Carousel, the picture standing on end with Vertical.
    @Test fun a_tab_layout_page_offers_the_carousels_orientation() = runTest {
        val (state, settings) = atCustomise(controller = true)
        while (step(state) != OnboardingStep.TAB_CONSOLES) { state.select(state.selection.value.count - 1); state.onButton(NxButton.A) }
        state.select(0)
        while (MenuStyles.styleOf(settings.menuStyles, MenuCategory.CONSOLES) != MenuStyle.LIST) state.onButton(NxButton.RIGHT)
        assertEquals(listOf("Consoles layout", "Continue"), assertIs<Screen.Onboarding>(state.screen.value).rows.map { it.label })   // List: no orientation
        while (MenuStyles.styleOf(settings.menuStyles, MenuCategory.CONSOLES) != MenuStyle.CAROUSEL) state.onButton(NxButton.RIGHT)
        assertEquals(listOf("Consoles layout" to "Carousel", "Consoles orientation" to "Horizontal", "Continue" to null), assertIs<Screen.Onboarding>(state.screen.value).rows.map { it.label to it.secondary })
        state.select(1); state.onButton(NxButton.RIGHT)
        assertEquals(MenuOrientation.VERTICAL, settings.menuOrientations[MenuCategory.CONSOLES])
        assertEquals(LayoutShot.Tab(MenuCategory.CONSOLES, MenuStyle.CAROUSEL, vertical = true), assertIs<Screen.Onboarding>(state.screen.value).shot)
        assertEquals(1, state.selection.value.index)                       // the row stays selected
    }

    // The game lists' page: orientation for a Carousel or Backdrop, alignment once it stands on end; the picture follows.
    @Test fun the_game_lists_page_offers_orientation_and_alignment() = runTest {
        val (state, settings) = atCustomise(controller = true)
        while (step(state) != OnboardingStep.GAME_LISTS) { state.select(state.selection.value.count - 1); state.onButton(NxButton.A) }
        state.select(0)
        while (settings.gameListStyle != MenuStyle.BACKDROP) state.onButton(NxButton.RIGHT)
        assertEquals(listOf("Game lists layout", "Game lists orientation", "Continue"), assertIs<Screen.Onboarding>(state.screen.value).rows.map { it.label })
        state.select(1); state.onButton(NxButton.RIGHT)                    // Vertical: the alignment row comes
        assertEquals(listOf("Game lists layout", "Game lists orientation", "Game lists alignment", "Continue"), assertIs<Screen.Onboarding>(state.screen.value).rows.map { it.label })
        state.select(2); state.onButton(NxButton.RIGHT)                    // Right
        assertEquals(VerticalListAlignment.RIGHT, settings.gameListVerticalAlignment)
        assertEquals(LayoutShot.Games(MenuStyle.BACKDROP, vertical = true, right = true), assertIs<Screen.Onboarding>(state.screen.value).shot)
    }

    @Test fun the_game_lists_page_steps_through_five_layouts() = runTest {
        val (state, settings) = atCustomise(controller = true)
        while (step(state) != OnboardingStep.GAME_LISTS) { state.select(state.selection.value.count - 1); state.onButton(NxButton.A) }
        val page = assertIs<Screen.Onboarding>(state.screen.value)
        assertEquals("Game lists layout", page.rows[0].label)
        val seen = mutableSetOf<MenuStyle>()
        state.select(0)
        repeat(MenuStyles.GAME_LIST_STYLES.size) { state.onButton(NxButton.RIGHT); seen += settings.gameListStyle }
        assertEquals(MenuStyles.GAME_LIST_STYLES.toSet(), seen)
        assertEquals(LayoutShot.Games(settings.gameListStyle), assertIs<Screen.Onboarding>(state.screen.value).shot)
    }

    // With a controller there is no pad page, but title and hints are offered.
    @Test fun a_controller_skips_the_pad_pages() = runTest {
        val (state, _) = atCustomise(portrait = true, controller = true)
        assertEquals(OnboardingStep.TITLE, step(state))
    }

    // B from the first customise page goes back to the question.
    @Test fun b_from_the_first_customise_page_returns_to_the_question() = runTest {
        val (state, _) = atCustomise()
        assertEquals(OnboardingStep.PAD, step(state))
        state.onButton(NxButton.B)
        assertEquals(OnboardingStep.CUSTOMISE, step(state))
    }

    // each page opens on the value in use.
    @Test fun customise_pages_open_on_the_current_values() = runTest {
        val (state, settings) = atCustomise(yes = false)
        settings.menuPad = false; settings.padTheme = PadTheme.RETRO; settings.menuPageTitle = false
        settings.menuStyles = MenuCategory.entries.associateWith { MenuStyle.LIST }; settings.gameListStyle = MenuStyle.CAROUSEL
        state.select(0); state.onButton(NxButton.A)                         // Yes
        assertEquals("Hide", assertIs<Screen.Onboarding>(state.screen.value).rows[0].secondary)   // PAD shows the value in use
        state.select(0); state.onButton(NxButton.A)                         // Show
        assertEquals("Retro", assertIs<Screen.Onboarding>(state.screen.value).rows[1].secondary)
        state.select(2); state.onButton(NxButton.A)                         // Continue
        assertEquals("Hide", assertIs<Screen.Onboarding>(state.screen.value).rows[0].secondary)   // TITLE shows the value in use
        state.select(2); state.onButton(NxButton.A)                         // Continue
        assertEquals("List", assertIs<Screen.Onboarding>(state.screen.value).rows[0].secondary)   // HOME on List
        repeat(4) { state.select(state.selection.value.count - 1); state.onButton(NxButton.A) }   // each tab's Continue
        assertEquals(OnboardingStep.GAME_LISTS, step(state))
        assertEquals("Carousel", assertIs<Screen.Onboarding>(state.screen.value).rows[0].secondary)
    }

    // A failed sign-in with a token already kept (signed in before) stays on the step with its error.
    @Test fun onboarding_sign_in_failure_with_an_old_token_still_stays() = runTest {
        val f = raOnboarding(); val state = f.state
        (state.settings as MemorySettings).raToken = "old"
        f.http.on("login2", body = """{"Success":false,"Error":"Bad password"}""")
        state.select(0); state.onButton(NxButton.A)
        state.onboardingDraftUser("me"); state.onboardingDraftPassword("wrong"); state.onButton(NxButton.A)
        advanceUntilIdle()
        if (state.error.value != null) state.onButton(NxButton.A)
        val s = assertIs<Screen.Onboarding>(state.screen.value)
        assertEquals(OnboardingStep.RA_SIGN_IN, s.step)
        assertTrue(s.details.any { "Bad password" in it }, "${s.details}")
    }

    @Test fun without_a_tree_the_picker_screen_shows() = runTest {
        val picker = NoopPicker()
        val state = AppState(AppDeps(MemorySettings(), source, RecordingRunner(), picker, compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        assertIs<Screen.PickFolder>(state.screen.value)
        state.pickHome()
        assertEquals(1, picker.picks)
    }

    @Test fun with_a_tree_systems_load_then_games_then_launch() = runTest {
        val runner = RecordingRunner()
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, runner, NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        val systems = assertIs<Screen.Systems>(state.screen.value)
        assertTrue(systems.systems.map { it.tag }.containsAll(listOf("GB", "GBA", "NDS")))
        assertTrue(systems.systems.none { it.tag == "PSP" })   // skeleton console with no games is hidden
        assertEquals(1, systems.systems.first { it.tag == "GB" }.gameCount)

        state.openSystem(systems.systems.first { it.tag == "NDS" })
        val games = assertIs<Screen.Games>(state.screen.value)
        assertEquals(listOf("Mario Kart DS"), games.games.map { it.shownName })

        state.play(games.games[0])
        assertEquals(1, runner.launched.size)
        assertEquals("NDS", runner.launched[0].systemTag)
        assertEquals("melondsds", runner.launched[0].core.id)
        assertEquals(ContentRef("Home/Roms/Nintendo DS (NDS)/Mario Kart DS.nds"), runner.launched[0].content)
        assertEquals("Mario Kart DS.nds", runner.launched[0].fileName)
        assertEquals(games.games[0].path, runner.launched.last().gamePath)

        assertTrue(state.back())
        assertIs<Screen.Systems>(state.screen.value)
        assertTrue(!state.back())
    }

    /** A GBA header's first bytes with game code [code] (FP7E: Classic NES Series Pac-Man). */
    private fun gbaHead(code: String): ByteArray = ByteArray(0xC0).also { h ->
        code.encodeToByteArray().copyInto(h, 0xAC)
        h[0xB2] = 0x96.toByte()
    }

    private fun gbaSource(systemsTxt: String? = null) = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Game Boy Advance (GBA)"),
            "Home/Roms/Game Boy Advance (GBA)" to listOfNotNull("Pac-Man.gba", "Emerald.gba", "Zelda.zip", systemsTxt?.let { "systems.txt" }),
        ),
        texts = systemsTxt?.let { mapOf("Home/Roms/Game Boy Advance (GBA)/systems.txt" to it) }.orEmpty(),
        bytes = mapOf(
            "Home/Roms/Game Boy Advance (GBA)/Pac-Man.gba" to gbaHead("FP7E"),
            "Home/Roms/Game Boy Advance (GBA)/Emerald.gba" to gbaHead("BPEE"),
        ),
    )

    private fun TestScope.gbaState(src: FakeRomsSource, runner: RecordingRunner, defaultTag: String? = null) = AppState(
        AppDeps(
            MemorySettings(ContentRef("Home")).apply { defaultTag?.let { defaultTags = mapOf("gba" to it) } }, src, runner, NoopPicker(),
            compute = UnconfinedTestDispatcher(testScheduler),
            // Zelda.zip holds a Classic NES Series ROM.
            archiveHead = { ref, _, _, _ -> if (ref.value.endsWith("/Zelda.zip")) gbaHead("FZLE") else null },
        ),
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    private fun playGba(state: AppState, name: String) {
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.displayName == "Game Boy Advance" })
        state.play(assertIs<Screen.Games>(state.screen.value).games.first { it.fileName == name })
        state.back()
    }

    @Test fun a_classic_nes_series_cart_on_the_default_gpsp_runs_mgba_as_its_emulator_pick() = runTest {
        val src = gbaSource()
        val runner = RecordingRunner()
        val state = gbaState(src, runner)
        state.load()
        playGba(state, "Pac-Man.gba")
        assertEquals("MGBA", runner.launched.last().systemTag)
        assertEquals("mgba", runner.launched.last().core.id)
        // Recorded as the Emulator menu records a pick: states, Game Settings and resume follow MGBA from now on.
        assertEquals("Pac-Man.gba\tMGBA\n", src.textAt("Home/Roms/Game Boy Advance (GBA)/systems.txt"))
        assertTrue(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "MGBA" }.games.any { it.fileName == "Pac-Man.gba" })
        playGba(state, "Pac-Man.gba")
        assertEquals("MGBA", runner.launched.last().systemTag)
        playGba(state, "Zelda.zip")   // an archive's ROM header
        assertEquals("MGBA", runner.launched.last().systemTag)
        playGba(state, "Emerald.gba")   // any other game keeps the default
        assertEquals("GBA", runner.launched.last().systemTag)
        assertEquals("gpsp", runner.launched.last().core.id)
    }

    @Test fun a_per_game_gba_pick_keeps_a_classic_nes_series_cart_on_gpsp() = runTest {
        val src = gbaSource(systemsTxt = "Pac-Man.gba\tGBA\n")
        val runner = RecordingRunner()
        val state = gbaState(src, runner)
        state.load()
        playGba(state, "Pac-Man.gba")
        assertEquals("GBA", runner.launched.last().systemTag)
        assertEquals("gpsp", runner.launched.last().core.id)
        assertEquals("Pac-Man.gba\tGBA\n", src.textAt("Home/Roms/Game Boy Advance (GBA)/systems.txt"))
    }

    @Test fun a_default_of_mgba_runs_a_classic_nes_series_cart_as_listed() = runTest {
        val src = gbaSource()
        val runner = RecordingRunner()
        val state = gbaState(src, runner, defaultTag = "MGBA")
        state.load()
        playGba(state, "Pac-Man.gba")
        assertEquals("MGBA", runner.launched.last().systemTag)
        assertNull(src.textAt("Home/Roms/Game Boy Advance (GBA)/systems.txt"))   // nothing to record
    }

    @Test fun y_opens_netplay_on_supported_game() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"),
                "Home/Roms" to listOf("Super Nintendo (SFC)", "Game Boy Advance (MGBA)", "Nintendo 64 (N64)"),
                "Home/Roms/Super Nintendo (SFC)" to listOf("Super Mario Kart (USA).sfc"),
                "Home/Roms/Game Boy Advance (MGBA)" to listOf("Pokemon Emerald (USA).gba"),
                "Home/Roms/Nintendo 64 (N64)" to listOf("Super Mario 64 (USA).z64"),
            ),
        )
        val runner = RecordingRunner()
        val np = com.nxredux.mobile.netplay.FakeNetplayPlatform()
        val state = AppState(
            // The (MGBA) folder names only the console: Default emulators puts its game on mGBA.
            AppDeps(MemorySettings(ContentRef("Home")).apply { defaultTags = mapOf("gba" to "MGBA") }, src, runner, NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler), netplay = np),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load()
        val systems = assertIs<Screen.Systems>(state.screen.value).systems
        val controller = assertNotNull(state.netplay)

        state.openSystem(systems.first { it.tag == "MGBA" })   // MGBA is the cable only, and there is none: Y does nothing
        state.onButton(NxButton.Y)
        assertNull(controller.page.value)
        assertIs<Screen.Games>(state.screen.value)
        assertTrue(ContextKind.NETPLAY !in run { state.onButton(NxButton.MENU); menuKinds(state) })
        state.onButton(NxButton.B)
        state.back()

        state.openSystem(systems.first { it.tag == "N64" })   // no netplay for Nintendo 64 at all: Y does nothing
        state.onButton(NxButton.Y)
        assertNull(controller.page.value)
        assertIs<Screen.Games>(state.screen.value)
        assertTrue(ContextKind.NETPLAY !in run { state.onButton(NxButton.MENU); menuKinds(state) })
        state.onButton(NxButton.B)
        state.back()

        state.openSystem(systems.first { it.tag == "SFC" })
        val games = assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.Y)
        assertEquals("Super Mario Kart", assertIs<NetplayPage.Role>(controller.page.value).title)   // the shown name
        state.onButton(NxButton.DOWN)   // the sheet takes the pad, not the list
        assertEquals(1, assertIs<NetplayPage.Role>(controller.page.value).selected)
        assertTrue(state.back())        // Back closes the sheet, the list stays
        assertNull(controller.page.value)
        assertSame(games, state.screen.value)

        choose(state, ContextKind.NETPLAY)   // the context menu's Netplay opens the same sheet
        assertNull(state.screen.value.contextMenu)
        assertIs<NetplayPage.Role>(controller.page.value)
        state.onButton(NxButton.B)
        assertNull(controller.page.value)

        val session = com.nxredux.mobile.netplay.NetplaySession(com.nxredux.mobile.netplay.NetplayRole.CLIENT, "10.0.0.1", "mednafen_supafaust", "snes9x")
        state.launchNetplay(games.entries[0], session)
        assertEquals("mednafen_supafaust", runner.launched.single().core.id)
        assertEquals(session, runner.launched.single().netplay)
        assertNull(runner.launched.single().resumeSlot)
        assertEquals("SFC", runner.launched.single().systemTag)
    }

    @Test fun without_a_netplay_platform_y_and_the_menu_offer_none() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"),
                "Home/Roms" to listOf("Super Nintendo (SFC)"),
                "Home/Roms/Super Nintendo (SFC)" to listOf("Super Mario Kart (USA).sfc"),
            ),
        )
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), src, RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "SFC" })
        assertNull(state.netplay)
        state.onButton(NxButton.Y)
        assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.MENU)
        assertTrue(ContextKind.NETPLAY !in menuKinds(state))
    }

    @Test fun a_transient_read_failure_keeps_the_tree_and_shows_the_picker() = runTest {
        val flaky = object : RomsSource {
            override suspend fun listChildren(dir: ContentRef): List<Entry> = throw IllegalStateException("boom")
            override suspend fun readText(file: ContentRef): String? = null
        }
        val settings = MemorySettings(ContentRef("Home"))
        val state = AppState(AppDeps(settings, flaky, RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        assertIs<Screen.PickFolder>(state.screen.value)
        assertNotNull(state.error.value)
        assertEquals(ContentRef("Home"), settings.treeRef)   // transient error must not force a re-pick
    }

    @Test fun a_hidden_listing_keeps_the_index_and_the_tree() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val picker = NoopPicker()
        val store = MemoryLibraryIndexStore()
        val st = state(settings, picker = picker, store = store)
        st.load()
        val before = assertIs<Screen.Systems>(st.screen.value).systems
        val writes = store.writes
        source.listingFault = ListingFault.UNAVAILABLE
        st.rescan()
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertEquals(listOf(loose), settings.extraFolders)
        assertTrue(picker.forgotten.isEmpty())
        assertEquals(before, assertIs<Screen.Systems>(st.screen.value).systems)
        assertEquals(writes, store.writes)                                                       // the stored index is not replaced
        assertEquals("Storage did not list a folder just now; the library is unchanged. Use Rescan in Settings → Library in a moment.", st.notice.value)
        assertNull(st.error.value)
    }

    /** The page holding [kind]'s row: Default emulators for the tags, Emulators and Second screen for theirs, else Library. */
    private fun libraryPageOf(kind: SettingsKind): SettingsSection = when (kind) {
        SettingsKind.DEFAULT_TAG -> SettingsSection.DEFAULT_EMULATORS
        SettingsKind.EMULATORS, SettingsKind.DEFAULTS -> SettingsSection.EMULATORS
        SettingsKind.SECOND_SCREEN_ENABLE, SettingsKind.BOTTOM_SCREEN -> SettingsSection.SECOND_SCREEN
        else -> SettingsSection.LIBRARY
    }

    /** Settings -> Library or Emulators (or a sub-page) with [kind]'s row selected; returns its index. */
    private fun openLibraryOn(st: AppState, kind: SettingsKind): Int {
        st.openSettings(libraryPageOf(kind))
        val row = assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.kind == kind }
        st.select(row)
        return row
    }

    /** The Library page's game count ("3 games"), from its first row's "<n> games · <m> consoles" label. */
    private fun gamesLine(st: AppState): String {
        val s = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsSection.LIBRARY, s.section)
        assertEquals(SettingsKind.RESCAN, s.rows[0].kind)
        return s.rows[0].label.substringBefore(" · ")
    }

    private val listingHiddenNotice = "Storage did not list a folder just now; the library is unchanged. Use Rescan in Settings → Library in a moment."

    @Test fun a_hidden_listing_on_rescan_from_library_returns_to_the_rescan_row() = runTest {
        val store = MemoryLibraryIndexStore()
        val st = state(MemorySettings(ContentRef("Home")), store = store)
        st.load()
        val row = openLibraryOn(st, SettingsKind.RESCAN)
        val writes = store.writes
        source.listingFault = ListingFault.UNAVAILABLE
        st.onButton(NxButton.A)                                                                  // the counts row: rescan
        val s = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsSection.LIBRARY, s.section)
        assertEquals(row, st.selection.value.index)
        assertEquals("3 games", gamesLine(st))                                                   // the old index, its count
        assertEquals(listingHiddenNotice, st.notice.value)
        assertNull(st.error.value)
        assertEquals(writes, store.writes)
    }

    @Test fun a_hidden_listing_on_a_tapped_rescan_returns_to_the_rescan_row() = runTest {
        val store = MemoryLibraryIndexStore()
        val st = state(MemorySettings(ContentRef("Home")), store = store)
        st.load()
        val row = openLibraryOn(st, SettingsKind.RESCAN)
        val writes = store.writes
        source.listingFault = ListingFault.UNAVAILABLE
        st.openSelected()                                                                        // the touch path
        assertEquals(SettingsSection.LIBRARY, assertIs<Screen.Settings>(st.screen.value).section)
        assertEquals(row, st.selection.value.index)
        assertEquals(listingHiddenNotice, st.notice.value)
        assertEquals(writes, store.writes)
    }

    @Test fun an_offline_home_on_rescan_from_library_returns_to_the_rescan_row_with_the_error() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val card = CardSource(source)
        val store = MemoryLibraryIndexStore()
        val st = state(settings, card, store = store)
        st.load()
        val row = openLibraryOn(st, SettingsKind.RESCAN)
        val writes = store.writes
        card.homeMounted = false
        st.onButton(NxButton.A)
        val s = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsSection.LIBRARY, s.section)
        assertEquals(row, st.selection.value.index)
        assertEquals("3 games", gamesLine(st))
        assertEquals("The home folder's storage is not available. Insert it, then use Rescan in Settings → Library.", st.error.value)
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertEquals(writes, store.writes)
    }

    @Test fun a_hidden_listing_on_a_home_pick_from_library_returns_to_the_home_row() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore()
        val st = state(settings, store = store)
        st.load()
        val row = openLibraryOn(st, SettingsKind.HOME)
        val writes = store.writes
        source.listingFault = ListingFault.UNAVAILABLE
        st.setHomePast(ContentRef("Home"))                                                           // the same folder picked again
        assertEquals(SettingsSection.LIBRARY, assertIs<Screen.Settings>(st.screen.value).section)
        assertEquals(row, st.selection.value.index)
        assertEquals(listingHiddenNotice, st.notice.value)
        assertNull(st.error.value)
        assertEquals(writes, store.writes)
    }

    @Test fun a_plain_rescan_under_a_hidden_listing_still_lands_on_systems_even_from_library() = runTest {
        val st = state(MemorySettings(ContentRef("Home")))
        st.load()
        openLibraryOn(st, SettingsKind.RESCAN)
        source.listingFault = ListingFault.UNAVAILABLE
        st.rescan()                                                                              // no Settings caller: the start's landing
        assertIs<Screen.Systems>(st.screen.value)
        assertEquals(listingHiddenNotice, st.notice.value)
    }

    @Test fun a_hidden_listing_with_no_index_keeps_the_tree_and_says_to_reopen() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        source.listingFault = ListingFault.UNAVAILABLE
        val st = state(settings)
        st.load()
        assertIs<Screen.PickFolder>(st.screen.value)
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertEquals("Storage did not list the library folders just now. Reopen the app in a moment.", st.error.value)
    }

    @Test fun a_hidden_listing_at_the_start_probe_keeps_the_shown_index_and_says_so() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore()
        state(settings, store = store).load()
        val writes = store.writes
        source.listingFault = ListingFault.UNAVAILABLE
        val st = state(settings, store = store)
        st.load()                                                                                // shows the stored index, then probes the home
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertIs<Screen.Systems>(st.screen.value)
        assertEquals(writes, store.writes)
        assertEquals("Storage did not list a folder just now; the library is unchanged. Use Rescan in Settings → Library in a moment.", st.notice.value)
        assertNull(st.error.value)
    }

    @Test fun coreless_systems_are_hidden_from_the_list() = runTest {
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        val systems = assertIs<Screen.Systems>(state.screen.value)
        assertTrue(systems.systems.map { it.tag }.containsAll(listOf("GB", "GBA", "NDS")))
        assertTrue(systems.systems.none { it.tag == "C64" })
        assertTrue(systems.systems.all { it.core != null })   // Commodore 64 (no core) is not shown
    }

    @Test fun buttons_move_the_selection_through_the_tab_row_to_the_bottom_and_a_opens_b_goes_back() = runTest {
        val runner = RecordingRunner()
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, runner, NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onConsoles()
        val systems = assertIs<Screen.Systems>(state.screen.value).systems
        val nds = systems.indexOfFirst { it.tag == "NDS" }
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.UP)                         // wraps to the last row (the last console; Tools is a tab), never the tab row
        assertFalse(state.tabFocus.value)
        assertEquals(systems.size - 1, state.selection.value.index)
        repeat(systems.size - 1 - nds) { state.onButton(NxButton.UP) }   // up to Nintendo DS
        assertEquals(nds, state.selection.value.index)
        state.onButton(NxButton.A)
        val games = assertIs<Screen.Games>(state.screen.value)
        assertEquals("NDS", games.system.tag)
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.A)                          // opens Mario Kart DS
        assertEquals(1, runner.launched.size)
        state.onButton(NxButton.B)
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(nds, state.selection.value.index)      // remembered
    }
    @Test fun tap_selects_then_opens() = runTest {
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onConsoles()
        state.select(1); assertEquals(1, state.selection.value.index)
        state.openSelected(); assertIs<Screen.Games>(state.screen.value)
    }

    private class FakeUserData(
        var recents: List<Recent> = emptyList(),
        val resumable: Map<Pair<String, String>, ResumeInfo> = emptyMap(),
        var pins: List<Shortcut> = emptyList(),
        var hidden: List<String> = emptyList(),
        var failWrites: Boolean = false,
    ) : UserDataStore {
        override suspend fun readRecents() = recents
        override suspend fun writeRecents(list: List<Recent>) { recents = list }
        override suspend fun readPins() = pins
        override suspend fun writePins(list: List<Shortcut>): Boolean { if (failWrites) return false; pins = list; return true }
        override suspend fun readHidden() = hidden
        override suspend fun writeHidden(list: List<String>): Boolean { if (failWrites) return false; hidden = list; return true }
        override suspend fun resumeInfo(tag: String, fileName: String) = resumable[tag to fileName] ?: ResumeInfo.NONE
    }

    private fun stateWith(userData: FakeUserData, runner: RecordingRunner, scope: CoroutineScope) =
        AppState(AppDeps(MemorySettings(ContentRef("Home")), source, runner, NoopPicker(), userData, compute = scope.coroutineContext[ContinuationInterceptor]!!), scope)

    // A in a game list resumes the game's save state (Play from start is in its menu); X does nothing there.
    @Test fun launching_adds_to_recents_and_a_resumes_from_the_slot() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(resumable = mapOf(("NDS" to "Mario Kart DS.nds") to ResumeInfo(9, "file:///p.bmp")))
        val state = stateWith(data, runner, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "NDS" })   // Nintendo DS
        assertEquals(ResumeInfo(9, "file:///p.bmp"), state.resumeInfo.value)
        state.onButton(NxButton.X)
        assertTrue(runner.launched.isEmpty())
        state.onButton(NxButton.A)
        assertEquals(9, runner.launched.single().resumeSlot)
        assertEquals(listOf(Recent("/Roms/Nintendo DS (NDS)/Mario Kart DS.nds", "Mario Kart DS")), data.recents)
    }

    private fun ndsList(settings: MemorySettings, runner: RecordingRunner, scope: CoroutineScope, save: Boolean = true): AppState {
        val data = FakeUserData(resumable = if (save) mapOf(("NDS" to "Mario Kart DS.nds") to ResumeInfo(9, "file:///p.bmp")) else emptyMap())
        val state = AppState(AppDeps(settings, source, runner, NoopPicker(), data, compute = scope.coroutineContext[ContinuationInterceptor]!!), scope)
        state.load()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "NDS" })
        return state
    }

    @Test fun a_game_with_a_save_leads_its_menu_with_play_from_start_which_ignores_the_save() = runTest {
        for (hints in listOf(true, false)) {
            val runner = RecordingRunner()
            val state = ndsList(MemorySettings(ContentRef("Home")).apply { menuButtonHints = hints }, runner, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            state.onButton(NxButton.MENU)
            assertEquals(ContextKind.PLAY_FROM_START, menuKinds(state).first(), "hints $hints")
            state.onButton(NxButton.A)
            assertNull(state.screen.value.contextMenu)
            assertNull(runner.launched.single().resumeSlot)              // from the start, the save left alone
        }
    }

    @Test fun a_game_without_a_save_offers_no_play_from_start() = runTest {
        val state = ndsList(MemorySettings(ContentRef("Home")).apply { menuButtonHints = false }, RecordingRunner(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), save = false)
        state.onButton(NxButton.MENU)
        assertTrue(ContextKind.PLAY_FROM_START !in menuKinds(state))
    }

    // The hint bar is text only on a touch-only screen, so Layouts → Button hints hides it there too.
    @Test fun hints_hidden_in_settings_are_hidden_on_a_touch_only_screen_too() = runTest {
        val state = ndsList(MemorySettings(ContentRef("Home")).apply { menuButtonHints = false }, RecordingRunner(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.onFooterMode(FooterMode.BUTTONS)                           // no controller, no pad
        assertFalse(state.launcherChrome().buttonHints)
        assertTrue(state.launcherChrome().tabRow)                        // the tab row stays: the touch way to switch tabs
    }

    @Test fun hidden_hints_put_netplay_first_on_a_home_game_menu() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Super Nintendo (SFC)"), "Home/Roms/Super Nintendo (SFC)" to listOf("Super Mario Kart (USA).sfc")),
        )
        suspend fun homeMenu(hints: Boolean): List<ContextKind> {
            val data = FakeUserData(recents = listOf(Recent("/Roms/Super Nintendo (SFC)/Super Mario Kart (USA).sfc")))
            val state = AppState(
                AppDeps(MemorySettings(ContentRef("Home")).apply { menuButtonHints = hints }, src, RecordingRunner(), NoopPicker(), data, compute = UnconfinedTestDispatcher(testScheduler), netplay = com.nxredux.mobile.netplay.FakeNetplayPlatform()),
                CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            )
            state.load(); advanceUntilIdle()
            state.selectHome { it is MainRow.Continue }
            state.onButton(NxButton.MENU)
            return menuKinds(state)
        }
        // Hints hidden, Netplay leads the game's menu; Recent and Search are icons then, not menu items.
        assertEquals(listOf(ContextKind.NETPLAY, ContextKind.PIN), homeMenu(hints = false))
        assertEquals(listOf(ContextKind.PIN), homeMenu(hints = true))
    }

    @Test fun a_home_game_with_a_save_offers_play_from_start() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Super Nintendo (SFC)"), "Home/Roms/Super Nintendo (SFC)" to listOf("Super Mario Kart (USA).sfc")),
        )
        val data = FakeUserData(
            recents = listOf(Recent("/Roms/Super Nintendo (SFC)/Super Mario Kart (USA).sfc")),
            resumable = mapOf(("SFC" to "Super Mario Kart (USA).sfc") to ResumeInfo(3)),
        )
        val runner = RecordingRunner()
        val state = AppState(
            AppDeps(MemorySettings(ContentRef("Home")), src, runner, NoopPicker(), data, compute = UnconfinedTestDispatcher(testScheduler)),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load(); advanceUntilIdle()
        state.selectHome { it is MainRow.Continue }
        state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.PLAY_FROM_START, ContextKind.PIN), menuKinds(state))
        state.onButton(NxButton.A); advanceUntilIdle()
        assertNull(runner.launched.single().resumeSlot)
    }

    // Layouts → Recent / Search / Netplay hint. A hidden Netplay hint leads a game's menu; a hidden Recent or Search hint becomes an icon at the tab row's right, never a menu item.
    @Test fun a_hidden_netplay_hint_moves_into_the_menu_recent_and_search_do_not() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Super Nintendo (SFC)"), "Home/Roms/Super Nintendo (SFC)" to listOf("Super Mario Kart (USA).sfc")),
        )
        suspend fun home(configure: MemorySettings.() -> Unit): AppState {
            val data = FakeUserData(recents = listOf(Recent("/Roms/Super Nintendo (SFC)/Super Mario Kart (USA).sfc")))
            val state = AppState(
                AppDeps(MemorySettings(ContentRef("Home")).apply(configure), src, RecordingRunner(), NoopPicker(), data, compute = UnconfinedTestDispatcher(testScheduler), netplay = com.nxredux.mobile.netplay.FakeNetplayPlatform()),
                CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            )
            state.load(); advanceUntilIdle()
            state.selectHome { it is MainRow.Continue }
            return state
        }
        suspend fun homeMenu(configure: MemorySettings.() -> Unit): List<ContextKind> =
            home(configure).also { it.onButton(NxButton.MENU) }.let(::menuKinds)
        assertEquals(listOf(ContextKind.PIN), homeMenu {})
        assertEquals(listOf(ContextKind.NETPLAY, ContextKind.PIN), homeMenu { showNetplayHint = false })
        assertEquals(listOf(ContextKind.PIN), homeMenu { showRecentHint = false })
        assertEquals(listOf(ContextKind.PIN), homeMenu { showSearchHint = false })
        assertTrue(home { showRecentHint = false }.launcherChrome().recentIcon)
        assertTrue(home { showSearchHint = false }.launcherChrome().searchIcon)
    }

    @Test fun the_hint_rows_show_only_with_the_bar_and_flip() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_TITLE_HINTS)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals(listOf("Recent hint", "Search hint"), rows().filter { it.kind == SettingsKind.HINT_SHOWN }.map { it.label })   // no netplay here: no Netplay hint
        val i = rows().indexOfFirst { it.hint == NxButton.START }
        state.select(i); state.onButton(NxButton.RIGHT)
        assertFalse(settings.showSearchHint); assertEquals("Hide", rows()[i].secondary); assertEquals(i, state.selection.value.index)
        state.onButton(NxButton.A); assertTrue(settings.showSearchHint)
        settings.showRecentHint = false
        state.onButton(NxButton.B)                                           // Reset to defaults is on Layouts itself
        state.select(rows().indexOfFirst { it.kind == SettingsKind.RESET_DEFAULTS }); state.onButton(NxButton.A)
        assertTrue(settings.showRecentHint)
        settings.menuButtonHints = false
        state.openSettings(SettingsSection.LAYOUT_TITLE_HINTS)
        assertTrue(rows().none { it.kind == SettingsKind.HINT_SHOWN })
    }

    @Test fun a_on_a_game_without_a_save_plays_from_the_start() = runTest {
        val runner = RecordingRunner()
        val state = stateWith(FakeUserData(), runner, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "GB" })   // Game Boy
        assertEquals(ResumeInfo.NONE, state.resumeInfo.value)
        state.onButton(NxButton.A)
        assertNull(runner.launched.single().resumeSlot)
    }

    @Test fun select_opens_switcher_with_resumable_recents_and_a_resumes() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(
            recents = listOf(Recent("/Roms/Game Boy (GB)/Tetris.gb"), Recent("/Roms/Nintendo DS (NDS)/Mario Kart DS.nds")),
            resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9), ("NDS" to "Mario Kart DS.nds") to ResumeInfo(2)),
        )
        val state = stateWith(data, runner, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onButton(NxButton.SELECT)
        val sw = assertIs<Screen.Switcher>(state.screen.value)
        assertEquals(listOf("Tetris.gb", "Mario Kart DS.nds"), sw.entries.map { it.game.fileName })
        state.onButton(NxButton.RIGHT)
        assertEquals(1, assertIs<Screen.Switcher>(state.screen.value).index)
        state.onButton(NxButton.RIGHT)   // no wrap
        assertEquals(1, assertIs<Screen.Switcher>(state.screen.value).index)
        state.onButton(NxButton.A)
        assertEquals(2, runner.launched.single().resumeSlot)
        assertEquals("Mario Kart DS.nds", runner.launched.single().fileName)
        assertIs<Screen.Systems>(state.screen.value)          // switcher closes on launch
        assertEquals("/Roms/Nintendo DS (NDS)/Mario Kart DS.nds", data.recents.first().path)
    }

    @Test fun the_switcher_list_moves_on_up_and_down_wrapping_a_fresh_press_and_ignores_left_and_right() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { switcherLayout = com.nxredux.mobile.settings.SwitcherLayout.LIST }
        val runner = RecordingRunner()
        val data = FakeUserData(
            recents = listOf(Recent("/Roms/Game Boy (GB)/Tetris.gb"), Recent("/Roms/Nintendo DS (NDS)/Mario Kart DS.nds")),
            resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9), ("NDS" to "Mario Kart DS.nds") to ResumeInfo(2)),
        )
        val state = state(settings, runner = runner, data = data)
        state.load()
        state.onButton(NxButton.SELECT)
        fun index() = assertIs<Screen.Switcher>(state.screen.value).index
        assertTrue(assertIs<Screen.Switcher>(state.screen.value).list)
        state.onButton(NxButton.RIGHT); assertEquals(0, index())   // the List has no LEFT/RIGHT
        state.onButton(NxButton.UP); assertEquals(1, index())      // a fresh press wraps
        state.onButton(NxButton.DOWN, repeat = true); assertEquals(1, index())   // a held one stops at the end
        state.onButton(NxButton.DOWN); assertEquals(0, index())
        state.selectSwitcherRow(1); assertEquals(1, index())       // a row tap
        state.onButton(NxButton.A)
        assertEquals("Mario Kart DS.nds", runner.launched.single().fileName)
    }

    @Test fun the_switcher_list_labels_same_name_games_as_a_merged_list_and_extra_copies_by_folder() {
        fun e(tag: String, file: String, name: String, subtitle: String? = null) = SwitcherEntry(
            SystemFolder(tag, tag, null, emptyList()),
            GameEntry(ContentRef(file), file, name, folder = ContentRef(tag), sourceId = "home", relativeDir = "", subtitle = subtitle), ResumeInfo(1),
        )
        val rows = switcherListRows(listOf(
            e("FC", "Tetris.nes", "Tetris"), e("GB", "Tetris.gb", "Tetris"),          // different files, different tags
            e("GBA", "Zelda.gba", "Zelda"), e("GBA", "Zelda.gba", "Zelda", subtitle = "Extra"),   // one file twice: its folder tells
            e("NDS", "Mario.nds", "Mario"),
        ))
        assertEquals(listOf("Tetris.nes (FC)", "Tetris.gb (GB)", "Zelda", "Zelda", "Mario"), rows.map { it.label })   // the stems collide: whole file names
        assertEquals(listOf(null, null, null, "Extra", null), rows.map { it.secondary })
    }

    @Test fun a_tap_selects_a_switcher_list_row_but_not_a_full_screen_one() = runTest {
        val data = FakeUserData(
            recents = listOf(Recent("/Roms/Game Boy (GB)/Tetris.gb"), Recent("/Roms/Nintendo DS (NDS)/Mario Kart DS.nds")),
            resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9), ("NDS" to "Mario Kart DS.nds") to ResumeInfo(2)),
        )
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings, data = data)
        state.load()
        state.onButton(NxButton.SELECT)
        val menuSelection = state.selection.value
        state.selectSwitcherRow(1); assertEquals(0, assertIs<Screen.Switcher>(state.screen.value).index)   // Full screen: no rows
        state.onButton(NxButton.B)
        settings.switcherLayout = com.nxredux.mobile.settings.SwitcherLayout.LIST
        state.onButton(NxButton.SELECT)
        state.selectSwitcherRow(1); assertEquals(1, assertIs<Screen.Switcher>(state.screen.value).index)
        state.selectSwitcherRow(5); assertEquals(1, assertIs<Screen.Switcher>(state.screen.value).index)   // out of range: kept
        assertEquals(menuSelection, state.selection.value)   // the menu's selection under it is untouched
    }

    @Test fun game_switcher_layout_cycles_full_screen_and_list() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        val i = state.selectLayoutsRow(SettingsKind.SWITCHER_LAYOUT)
        assertEquals("Full screen", state.layoutsRows()[i].secondary)
        assertTrue(settingsNxRow(state.layoutsRows()[i]).cycleable)
        state.onButton(NxButton.RIGHT); assertEquals(com.nxredux.mobile.settings.SwitcherLayout.LIST, settings.switcherLayout)
        state.onButton(NxButton.RIGHT); assertEquals(com.nxredux.mobile.settings.SwitcherLayout.FULL, settings.switcherLayout)
        state.onButton(NxButton.LEFT); assertEquals("List", state.layoutsRows()[i].secondary)
    }

    @Test fun switcher_hides_unresolvable_and_non_resumable_recents() = runTest {
        val data = FakeUserData(
            recents = listOf(
                Recent("/Roms/Game Boy (GB)/Gone.gb"),                // ROM deleted
                Recent("/Roms/Missing (MS)/x.gb"),                     // system folder gone
                Recent("/Roms/Commodore 64 (C64)/Boulder Dash 2.chd"),       // no core
                Recent("/Roms/Nintendo DS (NDS)/Mario Kart DS.nds"),   // not resumable
                Recent("/Roms/Game Boy (GB)/Tetris.gb"),
            ),
            resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(0), ("GB" to "Gone.gb") to ResumeInfo(9)),
        )
        val state = stateWith(data, RecordingRunner(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onButton(NxButton.SELECT)
        assertEquals(listOf("Tetris.gb"), assertIs<Screen.Switcher>(state.screen.value).entries.map { it.game.fileName })
        assertEquals(5, data.recents.size)   // hidden, not deleted
    }

    @Test fun remove_last_entry_shows_empty_state_and_b_returns() = runTest {
        val data = FakeUserData(recents = listOf(Recent("/Roms/Game Boy (GB)/Tetris.gb")), resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9)))
        val twoGames = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Game Boy (GB)"), "Home/Roms/Game Boy (GB)" to listOf("Dr. Mario.gb", "Tetris.gb")))
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), twoGames, RecordingRunner(), NoopPicker(), data, compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "GB" })
        state.select(1)
        state.onButton(NxButton.SELECT)
        state.onButton(NxButton.Y)
        state.onButton(NxButton.A)                          // confirm
        val sw = assertIs<Screen.Switcher>(state.screen.value)
        assertTrue(sw.entries.isEmpty())
        assertEquals(0, sw.index)
        assertTrue(data.recents.isEmpty())
        state.onButton(NxButton.B)
        assertIs<Screen.Games>(state.screen.value)          // back to where SELECT was pressed
        assertEquals(1, state.selection.value.index)        // games selection restored
        state.onButton(NxButton.SELECT)
        state.onButton(NxButton.SELECT)                     // SELECT also closes
        assertIs<Screen.Games>(state.screen.value)
    }

    @Test fun switcher_resolves_recents_without_listing_system_folders() = runTest {
        var listingAllowed = true
        val guarded = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> {
                check(listingAllowed) { "switcher listed ${dir.value}" }
                return source.listChildren(dir)
            }
            override suspend fun child(dir: ContentRef, name: String): Entry? = source.child(dir, name)
        }
        val data = FakeUserData(
            recents = listOf(Recent("/Roms/Game Boy (GB)/Gone.gb"), Recent("/Roms/Game Boy (GB)/Tetris.gb"), Recent("/Roms/Nintendo DS (NDS)/Mario Kart DS.nds")),
            resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9), ("GB" to "Gone.gb") to ResumeInfo(9), ("NDS" to "Mario Kart DS.nds") to ResumeInfo(1)),
        )
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), guarded, RecordingRunner(), NoopPicker(), data, compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        listingAllowed = false
        state.onButton(NxButton.SELECT)
        val sw = assertIs<Screen.Switcher>(state.screen.value)
        assertEquals(listOf("Tetris.gb", "Mario Kart DS.nds"), sw.entries.map { it.game.fileName })   // Gone.gb hidden
        assertEquals(ContentRef("Home/Roms/Game Boy (GB)/Tetris.gb"), sw.entries[0].game.ref)
        assertEquals(3, data.recents.size)                  // hidden, not deleted
        assertNull(state.error.value)
    }

    private fun twoRecents() = FakeUserData(
        recents = listOf(Recent("/Roms/Game Boy (GB)/Tetris.gb"), Recent("/Roms/Nintendo DS (NDS)/Mario Kart DS.nds")),
        resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9), ("NDS" to "Mario Kart DS.nds") to ResumeInfo(2)),
    )

    @Test fun y_asks_before_removing_and_b_or_select_cancels() = runTest {
        val data = twoRecents()
        val runner = RecordingRunner()
        val state = stateWith(data, runner, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onButton(NxButton.SELECT)
        state.onButton(NxButton.RIGHT)
        state.onButton(NxButton.Y)
        var sw = assertIs<Screen.Switcher>(state.screen.value)
        assertTrue(sw.confirmRemove)
        assertEquals(2, sw.entries.size)                    // nothing removed yet
        state.onButton(NxButton.LEFT)                       // ignored while confirming
        state.onButton(NxButton.X)
        assertEquals(1, assertIs<Screen.Switcher>(state.screen.value).index)
        state.onButton(NxButton.B)
        sw = assertIs<Screen.Switcher>(state.screen.value)
        assertTrue(!sw.confirmRemove)
        assertEquals(1, sw.index)                           // same entry still highlighted
        assertEquals(2, sw.entries.size)
        assertEquals(2, data.recents.size)                  // recent.txt untouched
        state.onButton(NxButton.Y)
        state.onButton(NxButton.SELECT)                     // SELECT cancels too (does not close the switcher)
        sw = assertIs<Screen.Switcher>(state.screen.value)
        assertTrue(!sw.confirmRemove)
        state.onButton(NxButton.Y)
        assertTrue(state.back())                            // system back dismisses the confirm first
        assertIs<Screen.Switcher>(state.screen.value).let { assertTrue(!it.confirmRemove) }
        assertTrue(runner.launched.isEmpty())
        assertEquals(2, data.recents.size)
    }

    @Test fun a_in_the_confirm_removes_and_writes_recents() = runTest {
        val data = twoRecents()
        val runner = RecordingRunner()
        val state = stateWith(data, runner, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onButton(NxButton.SELECT)
        state.onButton(NxButton.Y)
        state.onButton(NxButton.A)
        val sw = assertIs<Screen.Switcher>(state.screen.value)
        assertTrue(!sw.confirmRemove)
        assertEquals(listOf("Mario Kart DS.nds"), sw.entries.map { it.game.fileName })
        assertEquals(listOf(Recent("/Roms/Nintendo DS (NDS)/Mario Kart DS.nds")), data.recents)
        assertTrue(runner.launched.isEmpty())               // A confirmed the removal, did not resume
    }

    @Test fun y_on_an_empty_switcher_does_nothing() = runTest {
        val state = stateWith(FakeUserData(), RecordingRunner(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onButton(NxButton.SELECT)
        state.onButton(NxButton.Y)
        val sw = assertIs<Screen.Switcher>(state.screen.value)
        assertTrue(sw.entries.isEmpty() && !sw.confirmRemove)
    }

    /** readRecents suspends, so input can arrive while the switcher / a launch is still loading. */
    private class SlowUserData(
        var recents: List<Recent> = emptyList(),
        val resumable: Map<Pair<String, String>, ResumeInfo> = emptyMap(),
    ) : UserDataStore {
        var reads = 0
        override suspend fun readRecents(): List<Recent> { reads++; delay(100); return recents }
        override suspend fun writeRecents(list: List<Recent>) { recents = list }
        override suspend fun readPins(): List<Shortcut> = emptyList()
        override suspend fun writePins(list: List<Shortcut>) = true
        override suspend fun readHidden(): List<String> = emptyList()
        override suspend fun writeHidden(list: List<String>) = true
        override suspend fun resumeInfo(tag: String, fileName: String) = resumable[tag to fileName] ?: ResumeInfo.NONE
    }

    private fun slowState(data: SlowUserData, runner: RecordingRunner, scope: CoroutineScope) =
        AppState(AppDeps(MemorySettings(ContentRef("Home")), source, runner, NoopPicker(), data, compute = scope.coroutineContext[ContinuationInterceptor]!!), scope)

    private val tetrisResumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9))

    @Test fun select_twice_while_loading_opens_one_switcher() = runTest {
        val data = SlowUserData(listOf(Recent("/Roms/Game Boy (GB)/Tetris.gb")), tetrisResumable)
        val state = slowState(data, RecordingRunner(), CoroutineScope(StandardTestDispatcher(testScheduler)))
        state.load(); advanceUntilIdle()
        val before = data.reads                             // the main menu reads recents for its Recently Played row
        state.onButton(NxButton.SELECT)
        state.onButton(NxButton.SELECT)                     // still loading: ignored
        advanceUntilIdle()
        assertEquals(before + 1, data.reads)
        assertEquals(listOf("Tetris.gb"), assertIs<Screen.Switcher>(state.screen.value).entries.map { it.game.fileName })
        state.onButton(NxButton.B)
        assertIs<Screen.Systems>(state.screen.value)
    }

    @Test fun back_while_switcher_loads_cancels_it() = runTest {
        val data = SlowUserData(listOf(Recent("/Roms/Game Boy (GB)/Tetris.gb")), tetrisResumable)
        val state = slowState(data, RecordingRunner(), CoroutineScope(StandardTestDispatcher(testScheduler)))
        state.load(); advanceUntilIdle()
        state.onConsoles()
        state.onButton(NxButton.DOWN)                       // Nintendo DS
        state.onButton(NxButton.A); advanceUntilIdle()
        assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.SELECT)
        state.onButton(NxButton.B)                          // back to Systems before the switcher loads
        advanceUntilIdle()
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(1, state.selection.value.index)        // systems selection restored
    }

    @Test fun a_twice_launches_once() = runTest {
        val runner = RecordingRunner()
        val data = SlowUserData()
        val state = slowState(data, runner, CoroutineScope(StandardTestDispatcher(testScheduler)))
        state.load(); advanceUntilIdle()
        state.select(assertIs<Screen.Systems>(state.screen.value).systems.indexOfFirst { it.tag == "GB" })
        state.onButton(NxButton.A); advanceUntilIdle()      // Game Boy
        state.onButton(NxButton.A)
        state.onButton(NxButton.A)                          // launch still in flight: ignored
        advanceUntilIdle()
        assertEquals(1, runner.launched.size)
        assertEquals(listOf(Recent("/Roms/Game Boy (GB)/Tetris.gb", "Tetris")), data.recents)
        state.onButton(NxButton.A); advanceUntilIdle()      // after it completes, launching works again
        assertEquals(2, runner.launched.size)
    }

    private fun TestScope.state(settings: MemorySettings, src: RomsSource = source, picker: NoopPicker = NoopPicker(), runner: RecordingRunner = RecordingRunner(), data: UserDataStore = NoUserData, store: LibraryIndexStore = MemoryLibraryIndexStore(), now: (() -> Long)? = null, gameTime: GameTimeData? = null, calendar: LocalCalendar = FixedOffsetCalendar(), launcher: AppLauncher? = null, rumblePreview: (GameRumble) -> Unit = {}, padEditor: ((PadEditRequest) -> Unit)? = null, saveTransfer: SaveTransfer? = null, allFiles: AllFilesAccess? = null, localFiles: LocalFiles? = null, notifications: NotificationAccess? = null, builtInSecond: () -> Boolean = { false }) =
        AppState(AppDeps(settings, src, runner, picker, data, indexStore = store, now = now, gameTime = gameTime, compute = UnconfinedTestDispatcher(testScheduler), calendar = calendar, launcher = launcher, rumblePreview = rumblePreview, padEditor = padEditor, saveTransfer = saveTransfer, allFilesAccess = allFiles, localFiles = localFiles, notificationAccess = notifications, builtInSecondScreen = builtInSecond), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    /** What a scan of [src] for [settings] stores, built without an AppState (so without the skeleton). */
    private suspend fun scannedIndex(settings: MemorySettings, src: RomsSource = source): LibraryIndex =
        LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { settings.treeRef }).scan(settings.treeRef!!, settings.extraFolders, settings.defaultTags)

    private fun List<String>.listings() = filter { it.startsWith("listChildren:") }

    @Test fun the_skeleton_is_created_on_load_and_the_list_ends_with_tools() = runTest {
        val src = FakeRomsSource(dirs = mapOf("Home" to emptyList()))
        val state = state(MemorySettings(ContentRef("Home")), src)
        state.load()
        assertTrue("Home/Roms/Game Boy (GB)" in src.written)
        assertTrue("Home/Bios/GB" in src.written)
        assertTrue("Home/Saves" !in src.written)   // saves live inside the app: the home folder gets no Saves/
        val systems = assertIs<Screen.Systems>(state.screen.value)
        assertTrue(systems.systems.isEmpty())                // every skeleton console is empty, so all are hidden
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.TOOLS), systems.tabs.map { it.category })   // Home and the Tools tab
        state.onButton(NxButton.A)                           // Pick a game: no Consoles tab, stays
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.R1)
        assertEquals(2, state.selection.value.count)         // Cheat Database, Settings
        state.onButton(NxButton.UP)                          // wraps to the last row (Settings)
        state.onButton(NxButton.A)
        assertIs<Screen.SettingsMenu>(state.screen.value)
    }

    @Test fun a_re_picked_home_gets_its_skeleton_too() = runTest {
        val src = FakeRomsSource(dirs = mapOf("Home" to emptyList(), "Home2" to emptyList()))
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings, src)
        state.load()
        settings.treeRef = ContentRef("Home2")
        state.load()
        assertTrue("Home2/Roms" in src.written)
        assertTrue("Home2/Bios" in src.written)
        assertIs<Screen.Systems>(state.screen.value)
    }

    @Test fun an_old_roms_pick_is_cleared_with_an_explanation() = runTest {
        val settings = MemorySettings(ContentRef("OldRoms"))
        val state = state(settings)
        state.load()
        assertIs<Screen.PickFolder>(state.screen.value)
        assertNull(settings.treeRef)
        assertTrue(state.error.value!!.contains("Pick its parent"))
    }

    @Test fun extras_add_with_overlap_check_and_list_in_settings() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val picker = NoopPicker()
        val state = state(settings, picker = picker)
        state.load()
        state.addExtra(ContentRef("Home/Roms"))
        assertEquals(emptyList<ExtraFolder>(), settings.extraFolders)
        assertTrue(state.error.value!!.contains("overlaps"))
        state.dismissError()
        state.addExtra(ContentRef("sd/Loose"))
        assertEquals(1, settings.extraFolders.size)
        assertEquals("Loose-d116b9e3", settings.extraFolders[0].id)
        val systems = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(2, systems.systems.first { it.tag == "GBA" }.gameCount)   // Mario (home) + Kirby (loose)
        assertTrue(systems.systems.none { it.unassigned })                      // Unassigned lives in Settings -> Library
        state.openSettings(SettingsSection.LIBRARY)
        assertEquals(
            listOf(SettingsKind.RESCAN, SettingsKind.HOME, SettingsKind.EXTRA, SettingsKind.ADD_EXTRA, SettingsKind.SAVES, SettingsKind.UNASSIGNED),
            selectableKinds(state),
        )
        state.openSettings(SettingsSection.DEFAULT_EMULATORS)
        val d = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(List(10) { SettingsKind.DEFAULT_TAG }, d.rows.map { it.kind })
        // By console name; the Sega consoles have a choice (PicoDrive or Genesis Plus GX), games or not.
        assertEquals(listOf("gba", "segacd", "gg", "md", "sms", "sg1000", "ps", "sfc", "gb", "nds"), d.rows.map { it.consoleId })
        assertEquals("gpSP (GBA)", d.rows[0].secondary)
        state.cycleDefaultTag("gba")
        assertEquals(mapOf("gba" to "MGBA"), settings.defaultTags)
        assertEquals("mGBA (MGBA)", assertIs<Screen.Settings>(state.screen.value).rows[0].secondary)
        state.openSettings(SettingsSection.LIBRARY)
        assertEquals("ROM Folder: Loose", libraryRow(state, SettingsKind.EXTRA).label)
        state.select(rowIndex(state, SettingsKind.EXTRA)); state.openSelected()
        assertEquals(settings.extraFolders[0], assertIs<Screen.Settings>(state.screen.value).confirmRemove)
        state.onButton(NxButton.A)
        assertEquals(emptyList<ExtraFolder>(), settings.extraFolders)
        assertEquals(listOf(ContentRef("Home/Roms"), ContentRef("sd/Loose")), picker.forgotten)   // the refused pick's grant was released too
    }

    @Test fun a_home_pick_that_overlaps_an_extra_is_refused_and_its_grant_released() = runTest {
        val src = FakeRomsSource(dirs = mapOf(
            "sd" to listOf("Parent"),
            "sd/Parent" to listOf("Home", "Loose"),
            "sd/Parent/Home" to listOf("Roms"),
            "sd/Parent/Home/Roms" to listOf("Game Boy (GB)"),
            "sd/Parent/Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "sd/Parent/Loose" to listOf("Kirby.gba"),
        ))
        val settings = MemorySettings(ContentRef("sd/Parent/Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Parent/Loose"))) }
        val picker = NoopPicker()
        val state = state(settings, src, picker)
        state.load()
        state.setHomePast(ContentRef("sd/Parent"))
        assertEquals("That folder overlaps Loose; remove that extra folder first", state.error.value)
        assertEquals(ContentRef("sd/Parent/Home"), settings.treeRef)
        assertEquals(listOf(ContentRef("sd/Parent")), picker.forgotten)
        assertTrue(src.written.none { it.startsWith("sd/Parent/Roms") || it == "sd/Parent/Bios" })   // no skeleton in the parent
    }

    @Test fun a_new_home_replaces_the_old_and_releases_its_grant() = runTest {
        val src = FakeRomsSource(dirs = mapOf("Home" to emptyList(), "Home2" to emptyList()))
        val settings = MemorySettings(ContentRef("Home"))
        val picker = NoopPicker()
        val state = state(settings, src, picker)
        state.load()
        state.setHomePast(ContentRef("Home2"))
        assertNull(state.error.value)
        assertEquals(ContentRef("Home2"), settings.treeRef)
        assertEquals(listOf(ContentRef("Home")), picker.forgotten)
        assertIs<Screen.Systems>(state.screen.value)
        assertTrue("Home2/Roms" in src.written)
        state.setHomePast(ContentRef("Home2"))                // the same folder again keeps its grant
        assertEquals(listOf(ContentRef("Home")), picker.forgotten)
    }

    @Test fun a_revoked_extra_is_dropped_with_a_notice() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Gone"))) }
        val picker = NoopPicker()
        val throwing = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> = if (dir.value == "sd/Gone") throw IllegalArgumentException("gone") else source.listChildren(dir)
        }
        val state = state(settings, throwing, picker)
        state.load()
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(emptyList<ExtraFolder>(), settings.extraFolders)
        assertEquals(listOf(ContentRef("sd/Gone")), picker.forgotten)
        assertEquals("Lost access to Gone; add it again in Tools → Settings → Library", state.notice.value)
        state.onButton(NxButton.DOWN)
        assertNull(state.notice.value)
    }

    /**
     * [src] with "sd/Loose" and "Home" on removable cards: an unmounted card lists as the provider answers for it (no
     * root row, so "Folder no longer exists", as for a deleted folder) and reports itself offline. [looseLag] /
     * [homeLag]: that many more listings fail the same way once mounted (the provider's roots lag the mount) while the
     * card no longer reports offline. [hold]: isOffline waits for it (a slow platform check).
     */
    private class CardSource(private val src: RomsSource, var looseMounted: Boolean = true, var homeMounted: Boolean = true) : RomsSource by src {
        var looseLag = 0
        var homeLag = 0
        var hold: CompletableDeferred<Unit>? = null
        private fun loose(ref: ContentRef) = ref.value.startsWith("sd/Loose")
        private fun home(ref: ContentRef) = ref.value.startsWith("Home")
        private fun out(ref: ContentRef) = (!looseMounted && loose(ref)) || (!homeMounted && home(ref))
        private fun lagging(ref: ContentRef) = (loose(ref) && looseLag > 0) || (home(ref) && homeLag > 0)
        override suspend fun listChildren(dir: ContentRef): List<Entry> {
            if (out(dir)) throw IllegalArgumentException("Folder no longer exists")
            if (lagging(dir)) {
                if (loose(dir)) looseLag-- else homeLag--
                throw IllegalArgumentException("Folder no longer exists")
            }
            return src.listChildren(dir)
        }
        override suspend fun child(dir: ContentRef, name: String): Entry? = if (out(dir) || lagging(dir)) null else src.child(dir, name)
        override suspend fun isOffline(tree: ContentRef): Boolean { hold?.await(); return out(tree) }
    }

    private fun gbaCount(st: AppState) = assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GBA" }.gameCount

    @Test fun an_extra_on_an_unmounted_card_keeps_its_grant_and_its_games_return_once_it_is_back() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source)
        val picker = NoopPicker()
        val store = MemoryLibraryIndexStore()
        val st = state(settings, card, picker, store = store)
        st.load()
        assertEquals(2, gbaCount(st))
        card.looseMounted = false
        st.rescan()
        assertEquals(listOf(loose), settings.extraFolders)                                       // kept, grant not released
        assertTrue(picker.forgotten.isEmpty())
        assertEquals("Loose is not available. Insert its storage, then use Rescan in Settings → Library.", st.notice.value)
        assertEquals(1, gbaCount(st))                                                            // its games are left out
        val saved = assertNotNull(LibraryIndexCodec.decode(store.text!!))
        assertEquals(listOf(loose.id), saved.offline)
        assertTrue(saved.fits(ContentRef("Home"), settings.extraFolders, settings.defaultTags))

        // A start while the card is still out shows the index as it is: no scan, no notice.
        val writes = store.writes
        val st2 = state(settings, card, picker, store = store)
        st2.load()
        assertEquals(writes, store.writes)
        assertEquals(1, gbaCount(st2))
        assertNull(st2.notice.value)

        // Back in: the next return to the library rescans by itself, with no re-pick.
        card.looseMounted = true
        st.load()
        assertEquals(writes + 1, store.writes)
        assertEquals(2, gbaCount(st))
        assertEquals(emptyList(), assertNotNull(LibraryIndexCodec.decode(store.text!!)).offline)
        assertTrue(picker.forgotten.isEmpty())
    }

    @Test fun a_stored_index_with_an_offline_extra_rescans_at_start_once_the_card_is_back() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source, looseMounted = false)
        val store = MemoryLibraryIndexStore()
        state(settings, card, store = store).load()
        assertEquals(listOf(loose.id), assertNotNull(LibraryIndexCodec.decode(store.text!!)).offline)
        card.looseMounted = true
        val st = state(settings, card, store = store)
        st.load()
        assertEquals(2, gbaCount(st))
        assertEquals(2, store.writes)
    }

    @Test fun coming_back_to_the_app_on_the_main_menu_rescans_once_an_offline_extra_is_mounted() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source, looseMounted = false)
        val store = MemoryLibraryIndexStore()
        val st = state(settings, card, store = store)
        st.load()
        st.dismissNotice()
        val writes = store.writes
        st.onForeground()                                                                        // still out: nothing
        assertEquals(writes, store.writes)
        card.looseMounted = true
        val gba = assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GBA" }
        st.openSystem(gba)
        st.onForeground()                                                                        // in a list: left alone
        assertIs<Screen.Games>(st.screen.value)
        assertEquals(writes, store.writes)
        st.back()
        st.onForeground()
        assertEquals(writes + 1, store.writes)
        assertEquals(2, gbaCount(st))
    }

    @Test fun coming_back_to_the_app_once_the_home_is_mounted_runs_the_skipped_probe() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))
        val card = CardSource(source, homeMounted = false)
        val st = state(settings, card, store = store)
        st.load()
        st.dismissError()
        st.onForeground()
        assertTrue("Home/Bios" !in source.written)
        card.homeMounted = true
        st.onForeground()
        assertTrue("Home/Bios" in source.written)
        assertNull(st.error.value)
    }

    @Test fun an_extra_that_still_fails_on_its_second_mounted_scan_is_dropped() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source, looseMounted = false)
        val picker = NoopPicker()
        val st = state(settings, card, picker)
        st.load()
        card.looseMounted = true
        card.looseLag = Int.MAX_VALUE                                                            // deleted while the card was out
        st.dismissNotice()
        st.onForeground()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        assertEquals(listOf(loose), settings.extraFolders)                                       // first mounted failure: kept
        st.onForeground()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        assertEquals(emptyList(), settings.extraFolders)                                         // second: dropped, grant released
        assertEquals(listOf(loose.ref), picker.forgotten)
        assertEquals("Lost access to Loose; add it again in Tools → Settings → Library", st.notice.value)
    }

    @Test fun a_return_and_a_load_during_the_lag_share_one_home_probe() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))
        val card = CardSource(source, homeMounted = false)
        val st = state(settings, card, store = store)
        st.load()
        st.dismissError()
        card.homeMounted = true
        card.homeLag = Int.MAX_VALUE
        st.onForeground()                                                                        // probe fails, waits to retry
        st.load()                                                                                // leaving Settings meanwhile: joins it
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        assertEquals(ContentRef("Home"), settings.treeRef)                                       // one probe, one grace: kept
        assertNotNull(store.text)
        assertEquals(0, store.deletes)
    }

    @Test fun a_first_scan_inside_the_lag_retries_before_dropping_the_home() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore()                                                    // no fitting index (say, after a version bump)
        val card = CardSource(source).apply { homeLag = 1 }
        val st = state(settings, card, store = store)
        st.load()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertIs<Screen.Systems>(st.screen.value)
        assertNotNull(store.text)
        assertNull(st.error.value)
    }

    @Test fun an_extra_mounted_again_before_its_provider_root_is_back_is_kept_not_forgotten() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source, looseMounted = false)
        val picker = NoopPicker()
        val store = MemoryLibraryIndexStore()
        val st = state(settings, card, picker, store = store)
        st.load()
        st.dismissNotice()
        card.looseMounted = true
        card.looseLag = Int.MAX_VALUE                                                            // mounted, the provider has no root yet
        st.onForeground()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        assertEquals(listOf(loose), settings.extraFolders)
        assertTrue(picker.forgotten.isEmpty())
        assertEquals("Loose is not ready yet; use Rescan in Settings → Library in a moment.", st.notice.value)
        val saved = assertNotNull(LibraryIndexCodec.decode(store.text!!))
        assertEquals(listOf(loose.id), saved.offline)                                            // still recorded offline
        card.looseLag = 0
        st.onForeground()
        assertEquals(2, gbaCount(st))
        assertEquals(emptyList(), assertNotNull(LibraryIndexCodec.decode(store.text!!)).offline)
        assertTrue(picker.forgotten.isEmpty())
    }

    @Test fun a_home_mounted_again_before_its_provider_root_is_back_is_kept_once() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))
        val card = CardSource(source, homeMounted = false)
        val st = state(settings, card, store = store)
        st.load()
        st.dismissError()
        card.homeMounted = true
        card.homeLag = Int.MAX_VALUE
        st.onForeground()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        assertEquals(ContentRef("Home"), settings.treeRef)                                       // not yet back: kept, index too
        assertNotNull(store.text)
        assertEquals(0, store.deletes)
        assertIs<Screen.Systems>(st.screen.value)
        assertNull(st.error.value)
        st.onForeground()                                                                        // mounted and still gone: dropped as before
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        assertNull(settings.treeRef)
        assertIs<Screen.PickFolder>(st.screen.value)
    }

    @Test fun a_home_whose_root_lags_at_start_is_probed_again_after_a_pause() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))
        val card = CardSource(source).apply { homeLag = 1 }
        val st = state(settings, card, store = store)
        st.load()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertNull(st.error.value)
        assertTrue("Home/Bios" in source.written)                                               // the retry read it: skeleton ensured
    }

    @Test fun a_home_back_after_a_rescan_found_it_offline_gets_its_probe_on_return() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val card = CardSource(source)
        val st = state(settings, card)
        st.load()
        assertFalse(MenuCategory.COLLECTIONS in assertIs<Screen.Systems>(st.screen.value).tabs.map { it.category })
        card.homeMounted = false
        st.rescan()
        assertEquals("The home folder's storage is not available. Insert it, then use Rescan in Settings → Library.", st.error.value)
        st.dismissError()
        st.load()                                                                                // already said: not repeated
        assertNull(st.error.value)
        val dir = assertNotNull(source.createDirectory(ContentRef("Home"), "Collections"))      // added while the card was out
        source.writeText(dir.ref, "Faves.txt", "/Roms/Game Boy (GB)/Tetris.gb\n")
        card.homeMounted = true
        st.onForeground()
        assertTrue(MenuCategory.COLLECTIONS in assertIs<Screen.Systems>(st.screen.value).tabs.map { it.category })   // the skipped probe ran
        card.homeMounted = false
        st.load()
        assertEquals("The home folder's storage is not available. Insert it, then reopen the app.", st.error.value)   // back, then out again: said again
    }

    @Test fun an_offline_extra_and_a_dropped_one_in_the_same_scan_share_one_notice() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val gone = ExtraFolder("g1", ContentRef("sd/Gone"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(gone, loose) }
        val inner = CardSource(source, looseMounted = false)
        val card = object : RomsSource by inner {
            override suspend fun listChildren(dir: ContentRef): List<Entry> = if (dir.value == "sd/Gone") throw IllegalArgumentException("gone") else inner.listChildren(dir)
        }
        val st = state(settings, card)
        st.load()
        assertEquals("Lost access to Gone; add it again in Tools → Settings → Library. Loose is not available.", st.notice.value)
        assertEquals(listOf(loose), settings.extraFolders)
    }

    @Test fun a_second_return_while_the_first_check_runs_starts_no_second_scan_nor_one_on_a_new_screen() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source, looseMounted = false)
        val store = MemoryLibraryIndexStore()
        val st = state(settings, card, store = store)
        st.load()
        card.looseMounted = true
        val writes = store.writes
        card.hold = CompletableDeferred()
        st.onForeground()
        st.onForeground()
        card.hold!!.complete(Unit)
        assertEquals(writes + 1, store.writes)                                                   // one scan
        assertEquals(2, gbaCount(st))

        card.looseMounted = false
        st.rescan()                                                                              // offline again
        val writes2 = store.writes
        card.looseMounted = true
        card.hold = CompletableDeferred()
        st.onForeground()
        st.openSystem(assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GBA" })   // the user moves on meanwhile
        card.hold!!.complete(Unit)
        assertIs<Screen.Games>(st.screen.value)
        assertEquals(writes2, store.writes)
    }

    private fun libraryRow(st: AppState, kind: SettingsKind) = assertIs<Screen.Settings>(st.screen.value).rows.first { it.kind == kind }

    /** The open Settings page's first [kind] row's index. */
    private fun rowIndex(st: AppState, kind: SettingsKind) = assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.kind == kind }.also { assertTrue(it >= 0, "no $kind") }

    /** The open Settings page's rows the selection lands on, by kind (Library's group labels and gaps left out). */
    private fun selectableKinds(st: AppState) = assertIs<Screen.Settings>(st.screen.value).rows.filter { it.selectable }.map { it.kind }

    private fun pickRow(st: AppState, label: String) {
        val s = assertIs<Screen.FilePicker>(st.screen.value)
        val at = s.rows.indexOfFirst { it.label == label }
        assertTrue(at >= 0, "no $label in ${s.rows.map { it.label }}")
        st.select(at); st.onButton(NxButton.A)
    }

    private fun localFiles() = FakeLocalFiles(dirs = listOf("/storage/emulated/0/NXRedux", "/storage/emulated/0/Loose"))

    @Test fun with_all_files_access_first_setup_picks_a_file_home_in_the_list_picker() = runTest {
        val settings = MemorySettings().apply { allFilesAsked = true }
        val picker = NoopPicker()
        val st = state(settings, fileSource(), picker = picker, allFiles = FakeAllFilesAccess(isGranted = true), localFiles = localFiles())
        st.load()
        assertIs<Screen.PickFolder>(st.screen.value)
        st.onButton(NxButton.A)                                   // Pick folder
        assertEquals(0, picker.picks)                             // not Android's picker
        pickRow(st, "Internal storage"); pickRow(st, "NXRedux"); pickRow(st, "Use this folder")
        assertEquals(fileHome, settings.treeRef)
        assertIs<Screen.FolderSummary>(st.screen.value)           // setHome saw first setup: its next step (the home holds games)
    }

    // The portrait menu pad belongs to the menu: tool pages (Settings among them) never show it, whatever Controls → Menu pad
    // says, except Controls (and its Pad layout) and Appearance, where the pad and its theme are previewed.
    @Test fun the_menu_pad_shows_on_the_menu_and_the_pad_settings_only() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuPad = true }
        val state = state(settings)
        state.load()
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(true, state.menuPadShown())                           // the menu
        state.openSettingsMenu()
        assertEquals(false, state.menuPadShown())                          // Tools → Settings
        for (section in listOf(SettingsSection.LIBRARY, SettingsSection.EMULATORS, SettingsSection.MAIN_MENU, SettingsSection.LAYOUT_HOME, SettingsSection.SYSTEM)) {
            state.openSettings(section)
            assertEquals(false, state.menuPadShown(), "$section")
        }
        for (section in listOf(SettingsSection.CONTROLS, SettingsSection.PAD_LAYOUT, SettingsSection.APPEARANCE)) {
            state.openSettings(section)
            assertEquals(true, state.menuPadShown(), "$section")
        }
        settings.menuPad = false
        state.openSettings(SettingsSection.CONTROLS)
        assertEquals(false, state.menuPadShown())                          // off is off there too
    }

    // First setup's in-app folder picker is before onboarding asks about the menu pad: no pad there either.
    @Test fun the_menu_pad_stays_hidden_in_first_setups_folder_picker() = runTest {
        val settings = MemorySettings().apply { allFilesAsked = true; onboardingDone = null; menuPad = true }
        val st = state(settings, fileSource(), allFiles = FakeAllFilesAccess(isGranted = true), localFiles = localFiles())
        st.load()
        st.getStarted()                                   // Get started
        st.onButton(NxButton.A)                                   // Pick folder
        assertIs<Screen.FilePicker>(st.screen.value)
        assertEquals(false, st.menuPadShown())
        pickRow(st, "Internal storage"); pickRow(st, "NXRedux")
        assertEquals(false, st.menuPadShown())
    }

    @Test fun b_out_of_the_picker_returns_to_the_pick_screen_with_nothing_set() = runTest {
        val settings = MemorySettings().apply { allFilesAsked = true }
        val st = state(settings, fileSource(), allFiles = FakeAllFilesAccess(isGranted = true), localFiles = localFiles())
        st.load()
        st.onButton(NxButton.A)
        assertTrue(st.back())                                     // the volume list: cancel
        assertIs<Screen.PickFolder>(st.screen.value)
        assertNull(settings.treeRef)
    }

    @Test fun add_rom_folder_starts_beside_the_file_home_and_returns_to_library() = runTest {
        val settings = MemorySettings(fileHome)
        val st = state(settings, fileSource(), allFiles = FakeAllFilesAccess(isGranted = true), localFiles = localFiles())
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        st.select(assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.kind == SettingsKind.ADD_EXTRA }); st.openSelected()
        assertEquals("/storage/emulated/0", assertIs<Screen.FilePicker>(st.screen.value).dir)
        pickRow(st, "Loose"); pickRow(st, "Use this folder")
        assertEquals(FileRefs.of("/storage/emulated/0/Loose"), settings.extraFolders.single().ref)
        assertEquals(SettingsSection.LIBRARY, assertIs<Screen.Settings>(st.screen.value).section)
    }

    @Test fun a_save_transfer_pick_comes_back_to_its_caller_and_the_screen_it_left() = runTest {
        val st = state(MemorySettings(fileHome), fileSource(), allFiles = FakeAllFilesAccess(isGranted = true), localFiles = localFiles())
        st.load()
        val before = st.screen.value
        val got = ArrayList<ContentRef?>()
        st.pickFile(FilePickMode.Export("nx-mobile-saves.zip")) { got += it }
        assertEquals("/storage/emulated/0/NXRedux", assertIs<Screen.FilePicker>(st.screen.value).dir)   // the home itself
        st.onButton(NxButton.X)
        assertTrue(st.textEntryOpen)                              // New folder's field sees the d-pad first
        st.filePickerDraft("Exports"); st.onButton(NxButton.A)
        assertFalse(st.textEntryOpen)
        pickRow(st, "Save here")
        assertEquals(listOf<ContentRef?>(FileRefs.of("/storage/emulated/0/NXRedux/Exports/nx-mobile-saves.zip")), got)
        assertSame(before, st.screen.value)
        st.pickFile(FilePickMode.ImportZip) { got += it }
        repeat(3) { st.onButton(NxButton.B) }                     // up, up to the volumes, cancel
        assertNull(got.last())
    }

    @Test fun without_access_the_pickers_are_androids_even_with_local_files() = runTest {
        val picker = NoopPicker()
        val st = state(MemorySettings(ContentRef("Home")), picker = picker, allFiles = FakeAllFilesAccess(isGranted = false), localFiles = localFiles())
        st.load()
        st.pickHome(); st.pickExtra()
        assertEquals(1, picker.picks); assertEquals(1, picker.extraPicks)
    }

    @Test fun a_second_pick_while_the_picker_is_open_is_cancelled_and_leaves_the_first_alone() = runTest {
        val st = state(MemorySettings(fileHome), fileSource(), allFiles = FakeAllFilesAccess(isGranted = true), localFiles = localFiles())
        st.load()
        val before = st.screen.value
        val first = ArrayList<ContentRef?>(); val second = ArrayList<ContentRef?>()
        st.pickFile(FilePickMode.ImportFolder) { first += it }
        val open = st.screen.value
        st.pickFile(FilePickMode.ImportZip) { second += it }
        assertEquals(listOf<ContentRef?>(null), second)
        assertSame(open, st.screen.value)
        pickRow(st, "Use this folder")
        assertEquals(listOf<ContentRef?>(fileHome), first)
        assertSame(before, st.screen.value)
    }

    @Test fun while_the_new_folder_prompt_is_open_the_list_and_the_screen_underneath_do_not_move() = runTest {
        val st = state(MemorySettings(fileHome), fileSource(), allFiles = FakeAllFilesAccess(isGranted = true), localFiles = localFiles())
        st.load()
        st.pickFile(FilePickMode.ImportFolder) {}
        st.onButton(NxButton.X)
        val prompt = assertIs<Screen.FilePicker>(st.screen.value)
        val at = st.selection.value
        listOf(NxButton.DOWN, NxButton.UP, NxButton.X).forEach { st.onButton(it) }
        assertEquals(at, st.selection.value)
        assertEquals(prompt, st.screen.value)
        st.onButton(NxButton.B)                                   // cancels the prompt, not the picker
        assertNull(assertIs<Screen.FilePicker>(st.screen.value).prompt)
    }

    @Test fun while_the_replace_dialog_is_open_the_list_and_the_screen_underneath_do_not_move() = runTest {
        val files = FakeLocalFiles(dirs = listOf("/storage/emulated/0/NXRedux", "/storage/emulated/0/NXRedux/A"), files = listOf("/storage/emulated/0/NXRedux/saves.zip"))
        val st = state(MemorySettings(fileHome), fileSource(), allFiles = FakeAllFilesAccess(isGranted = true), localFiles = files)
        st.load()
        val got = ArrayList<ContentRef?>()
        st.pickFile(FilePickMode.Export("saves.zip")) { got += it }
        pickRow(st, "Save here")
        val replace = assertIs<Screen.FilePicker>(st.screen.value)
        assertNotNull(replace.replace)
        val at = st.selection.value
        listOf(NxButton.DOWN, NxButton.UP, NxButton.X).forEach { st.onButton(it) }
        assertEquals(at, st.selection.value)
        assertEquals(replace, st.screen.value)
        assertTrue(got.isEmpty())
        st.onButton(NxButton.A)
        assertEquals(listOf<ContentRef?>(FileRefs.of("/storage/emulated/0/NXRedux/saves.zip")), got)
    }

    private val accessOff = "All files access is off. Turn it on in Settings → Library → All files access, or pick the home folder again."
    private val tree = "content://com.android.externalstorage.documents/tree/"
    private val fileHome = FileRefs.of("/storage/emulated/0/NXRedux")

    /** [fileHome] with one GB game and a Loose folder beside it, keyed as FileRomsSource's refs are. */
    private fun fileSource(extra: Map<String, List<String>> = emptyMap()) = FakeRomsSource(
        dirs = mapOf(
            fileHome.value to listOf("Roms"),
            "${fileHome.value}/Roms" to listOf("Game Boy (GB)"),
            "${fileHome.value}/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "file:///storage/emulated/0/Loose" to listOf("Kirby.gba"),
        ) + extra,
    )

    /** [src] as FileRomsSource reads it: file refs only while [access] is granted, offline (never gone) without it. */
    private class AccessSource(private val src: RomsSource, private val access: FakeAllFilesAccess) : RomsSource by src {
        private fun off(ref: ContentRef) = FileRefs.isFile(ref) && !access.isGranted
        override suspend fun listChildren(dir: ContentRef): List<Entry> = if (off(dir)) throw IllegalStateException("Permission denied") else src.listChildren(dir)
        override suspend fun child(dir: ContentRef, name: String): Entry? = if (off(dir)) null else src.child(dir, name)
        override suspend fun isOffline(tree: ContentRef): Boolean = off(tree)
    }

    /** Like [AccessSource], but a revoked file ref lists EMPTY instead of failing (File.listFiles() on shared storage may). */
    private class EmptyWhenOffSource(private val src: RomsSource, private val access: FakeAllFilesAccess) : RomsSource by src {
        private fun off(ref: ContentRef) = FileRefs.isFile(ref) && !access.isGranted
        override suspend fun listChildren(dir: ContentRef): List<Entry> = if (off(dir)) emptyList() else src.listChildren(dir)
        override suspend fun child(dir: ContentRef, name: String): Entry? = if (off(dir)) null else src.child(dir, name)
        override suspend fun isOffline(tree: ContentRef): Boolean = off(tree)
    }

    @Test fun with_all_files_access_a_saf_home_and_extra_become_file_refs_keeping_ids_and_grants() = runTest {
        val src = fileSource()
        val looseSaf = ContentRef("${tree}primary%3ALoose")
        val download = ContentRef("content://com.android.providers.downloads.documents/tree/raw%3A%2Fx")
        val loose = ExtraFolder(Sources.idFor(src.displayName(looseSaf), looseSaf), looseSaf)
        val other = ExtraFolder(Sources.idFor(src.displayName(download), download), download)
        val settings = MemorySettings(ContentRef("${tree}primary%3ANXRedux")).apply { extraFolders = listOf(loose, other); allFilesAsked = true }
        val picker = NoopPicker()
        val files = FakeLocalFiles(dirs = listOf("/storage/emulated/0/NXRedux", "/storage/emulated/0/Loose"))
        val st = state(settings, src, picker = picker, allFiles = FakeAllFilesAccess(isGranted = true), localFiles = files)
        st.load()
        assertEquals(fileHome, settings.treeRef)
        assertEquals(listOf(loose.copy(ref = FileRefs.of("/storage/emulated/0/Loose")), other), settings.extraFolders)   // ids kept; Downloads has no path
        assertTrue(picker.forgotten.isEmpty())                                                                          // the SAF grants are kept
        assertTrue(assertIs<Screen.Systems>(st.screen.value).systems.any { it.tag == "GB" })
        st.load()                                                                                                      // a later start: the id stays
        assertEquals(loose.id, settings.extraFolders.first().id)
    }

    @Test fun without_access_nothing_converts() = runTest {
        val settings = MemorySettings(ContentRef("${tree}primary%3ANXRedux"))
        val files = FakeLocalFiles(dirs = listOf("/storage/emulated/0/NXRedux"))
        val st = state(settings, FakeRomsSource(dirs = emptyMap()), allFiles = FakeAllFilesAccess(isGranted = false), localFiles = files)
        st.load()
        assertEquals(ContentRef("${tree}primary%3ANXRedux"), settings.treeRef)
    }

    @Test fun a_grant_on_resume_converts_and_reloads_the_main_menu() = runTest {
        val saf = ContentRef("${tree}primary%3ANXRedux")
        val src = fileSource(mapOf(
            saf.value to listOf("Roms"),
            "${saf.value}/Roms" to listOf("Game Boy Advance (GBA)"),
            "${saf.value}/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
        ))
        val access = FakeAllFilesAccess(isGranted = false)
        val settings = MemorySettings(saf)
        val st = state(settings, src, allFiles = access, localFiles = FakeLocalFiles(dirs = listOf("/storage/emulated/0/NXRedux")))
        st.load()
        assertTrue(assertIs<Screen.Systems>(st.screen.value).systems.any { it.tag == "GBA" })   // read through SAF
        access.isGranted = true
        st.onAllFilesAccessChanged()
        assertEquals(fileHome, settings.treeRef)
        val systems = assertIs<Screen.Systems>(st.screen.value).systems
        assertTrue(systems.any { it.tag == "GB" }); assertTrue(systems.none { it.tag == "GBA" })   // rescanned from the file home
    }

    @Test fun with_access_revoked_a_file_home_shows_its_index_and_the_access_message_and_is_back_on_a_grant() = runTest {
        val access = FakeAllFilesAccess(isGranted = true)
        val src = AccessSource(fileSource(), access)
        val settings = MemorySettings(fileHome).apply { allFilesAsked = true }
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings, src)))
        access.isGranted = false
        val st = state(settings, src, store = store, allFiles = access)
        st.load()
        assertTrue(assertIs<Screen.Systems>(st.screen.value).systems.any { it.tag == "GB" })      // the index, as for an unmounted card
        assertEquals(accessOff, st.error.value)
        assertEquals(fileHome, settings.treeRef)
        assertEquals(0, store.deletes)
        st.dismissError()
        access.isGranted = true
        st.load()                                                                                  // back to the library with access on
        assertNull(st.error.value)
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals("/storage/emulated/0/NXRedux", libraryRow(st, SettingsKind.HOME).secondary)   // its path
    }

    @Test fun with_access_revoked_and_no_index_the_home_pick_says_so_and_keeps_the_home() = runTest {
        val access = FakeAllFilesAccess(isGranted = false)
        val settings = MemorySettings(fileHome).apply { allFilesAsked = true }
        val st = state(settings, AccessSource(fileSource(), access), allFiles = access)
        st.load()
        assertIs<Screen.PickFolder>(st.screen.value)
        assertEquals(accessOff, st.error.value)
        assertEquals(fileHome, settings.treeRef)
    }

    @Test fun with_access_revoked_a_file_extra_is_skipped_as_offline_and_kept() = runTest {
        val access = FakeAllFilesAccess(isGranted = false)
        val looseRef = FileRefs.of("/storage/emulated/0/Loose")
        val loose = ExtraFolder(Sources.idFor("Loose", looseRef), looseRef)
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val picker = NoopPicker()
        val st = state(settings, AccessSource(source, access), picker = picker, allFiles = access)
        st.load()
        assertIs<Screen.Systems>(st.screen.value)
        assertEquals(listOf(loose), settings.extraFolders)
        assertTrue(picker.forgotten.isEmpty())
        assertNull(st.notice.value)   // no notice: Library says it
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals("All files access off", libraryRow(st, SettingsKind.EXTRA).secondary)
        assertTrue(libraryRow(st, SettingsKind.EXTRA).warning)
        assertEquals("Off: some ROM folders can't be read", libraryRow(st, SettingsKind.ALL_FILES).note)
        assertTrue(libraryRow(st, SettingsKind.ALL_FILES).warning)
    }

    @Test fun an_offline_file_extra_with_access_granted_keeps_the_unmounted_card_wording() = runTest {
        val access = FakeAllFilesAccess(isGranted = true)
        val looseRef = FileRefs.of("/storage/emulated/0/Loose")
        val loose = ExtraFolder(Sources.idFor("Loose", looseRef), looseRef)
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val cardOut = object : RomsSource by source {
            override suspend fun isOffline(tree: ContentRef): Boolean = FileRefs.isFile(tree)
        }
        val st = state(settings, cardOut, allFiles = access)
        st.load()
        assertEquals("Loose is not available. Insert its storage, then use Rescan in Settings → Library.", st.notice.value)
    }

    @Test fun with_access_revoked_an_empty_listing_of_the_file_home_never_clears_the_home_or_the_index() = runTest {
        val access = FakeAllFilesAccess(isGranted = true)
        val full = fileSource()
        val looseRef = FileRefs.of("/storage/emulated/0/Loose")
        val loose = ExtraFolder(Sources.idFor("Loose", looseRef), looseRef)
        val settings = MemorySettings(fileHome).apply { extraFolders = listOf(loose); allFilesAsked = true }
        val picker = NoopPicker()
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings, full)))
        val stored = store.text
        access.isGranted = false
        val st = state(settings, EmptyWhenOffSource(full, access), picker = picker, store = store, allFiles = access)
        st.load()                                                                                  // a start from the index
        assertTrue(assertIs<Screen.Systems>(st.screen.value).systems.any { it.tag == "GB" })
        assertEquals(accessOff, st.error.value)
        st.dismissError()
        st.rescan()                                                                                // a scan that would read the empty listing
        assertTrue(assertIs<Screen.Systems>(st.screen.value).systems.any { it.tag == "GB" })
        assertEquals(accessOff, st.error.value)
        assertEquals(fileHome, settings.treeRef)
        assertEquals(listOf(loose), settings.extraFolders)
        assertTrue(picker.forgotten.isEmpty())
        assertEquals(0, store.deletes)
        assertEquals(stored, store.text)                                                           // the library cache is not overwritten
    }

    @Test fun with_access_revoked_a_file_extra_that_lists_empty_stays_offline_and_comes_back_with_its_games_on_a_regrant() = runTest {
        val access = FakeAllFilesAccess(isGranted = true)
        val base = FakeRomsSource(dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to emptyList(),
            "file:///storage/emulated/0/Loose" to listOf("Kirby.gba"),
        ))
        val looseRef = FileRefs.of("/storage/emulated/0/Loose")
        val loose = ExtraFolder(Sources.idFor("Loose", looseRef), looseRef)
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val before = scannedIndex(settings, base)
        assertTrue(before.systems.any { s -> s.games.isNotEmpty() })
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(before))
        access.isGranted = false
        val st = state(settings, EmptyWhenOffSource(base, access), store = store, allFiles = access)
        st.load()
        st.rescan()
        val after = LibraryIndexCodec.decode(store.text!!)!!
        assertEquals(listOf(loose.id), after.offline)
        assertEquals(listOf(loose), settings.extraFolders)
        access.isGranted = true   // a re-grant: the offline record sends the next load to a scan, so the games are back with no manual Rescan
        st.load()
        assertEquals(before.systems.sumOf { it.games.size }, LibraryIndexCodec.decode(store.text!!)!!.systems.sumOf { it.games.size })
    }

    @Test fun with_access_revoked_and_no_index_an_empty_listing_keeps_the_home_and_stores_nothing() = runTest {
        val access = FakeAllFilesAccess(isGranted = false)
        val settings = MemorySettings(fileHome).apply { allFilesAsked = true }
        val store = MemoryLibraryIndexStore()
        val st = state(settings, EmptyWhenOffSource(fileSource(), access), store = store, allFiles = access)
        st.load()
        assertIs<Screen.PickFolder>(st.screen.value)
        assertEquals(accessOff, st.error.value)
        assertEquals(fileHome, settings.treeRef)
        assertEquals(0, store.writes)
        assertEquals(0, store.deletes)
    }

    // --- 3b-1: All files access ---

    // First setup's All files step: B goes back to the text size question, its Skip row on to the home pick.
    @Test fun b_on_the_all_files_step_goes_back_to_the_text_size_and_skip_goes_on() = runTest {
        val access = FakeAllFilesAccess(declared = true, isGranted = false)
        val settings = MemorySettings().apply { onboardingDone = null }
        val st = state(settings, allFiles = access)
        st.load()
        st.getStarted()                             // Get started
        assertIs<Screen.PermissionsStep>(st.screen.value)
        st.onButton(NxButton.B)
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(st.screen.value).step)
        assertFalse(settings.allFilesAsked)
        st.continueTextSize()
        assertIs<Screen.PermissionsStep>(st.screen.value)
        assertTrue(st.back())                               // the system back likewise
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(st.screen.value).step)
        st.continueTextSize()
        st.select(1); st.onButton(NxButton.A)               // Skip
        assertTrue(settings.allFilesAsked)
        assertIs<Screen.PickFolder>(st.screen.value)
        assertEquals(0, access.opened)
    }

    // The Permissions page: a row per permission this device uses, with its status; a row asks, Continue goes on.
    @Test fun first_setup_asks_for_its_permissions_once_before_the_home_pick() = runTest {
        val access = FakeAllFilesAccess(declared = true, isGranted = false)
        val settings = MemorySettings()
        val picker = NoopPicker()
        val st = state(settings, picker = picker, allFiles = access)
        st.load()
        assertEquals(Screen.PermissionsStep(allFiles = false, notifications = null), st.screen.value)
        assertEquals(ListSelection(0, 2), st.selection.value)         // All files access, Continue
        st.onButton(NxButton.A)                                         // All files access: Android's page
        assertEquals(1, access.opened)
        assertFalse(settings.allFilesAsked)
        st.onAllFilesAccessChanged()                                    // back, not granted: the page stays
        assertEquals(Screen.PermissionsStep(allFiles = false, notifications = null), st.screen.value)
        access.isGranted = true
        st.onAllFilesAccessChanged()                                    // granted: its row says so
        assertEquals(Screen.PermissionsStep(allFiles = true, notifications = null), st.screen.value)
        st.select(1); st.onButton(NxButton.A)                           // Continue
        assertTrue(settings.allFilesAsked)
        assertIs<Screen.PickFolder>(st.screen.value)
        val again = state(settings, allFiles = FakeAllFilesAccess(declared = true, isGranted = false))
        again.load()
        assertIs<Screen.PickFolder>(again.screen.value)                 // asked once
    }

    @Test fun notifications_are_asked_on_the_same_page() = runTest {
        val notifications = FakeNotificationAccess(isGranted = false)
        val settings = MemorySettings()
        val st = state(settings, allFiles = FakeAllFilesAccess(declared = true, isGranted = true), notifications = notifications)
        st.load()
        assertEquals(Screen.PermissionsStep(allFiles = true, notifications = false), st.screen.value)
        st.select(1); st.onButton(NxButton.A)                           // Notifications
        assertEquals(1, notifications.requests)
        notifications.isGranted = true
        st.onAllFilesAccessChanged()
        assertEquals(Screen.PermissionsStep(allFiles = true, notifications = true), st.screen.value)
        assertEquals(3, st.selection.value.count)
    }

    @Test fun no_permissions_page_when_everything_is_granted() = runTest {
        val st = state(MemorySettings(), allFiles = FakeAllFilesAccess(declared = true, isGranted = true), notifications = FakeNotificationAccess(isGranted = true))
        st.load()
        assertIs<Screen.PickFolder>(st.screen.value)
    }

    @Test fun skip_or_back_goes_on_to_the_home_pick_at_once() = runTest {
        val access = FakeAllFilesAccess(declared = true, isGranted = false)
        val settings = MemorySettings()
        val st = state(settings, allFiles = access)
        st.load()
        st.onButton(NxButton.B)
        assertIs<Screen.PickFolder>(st.screen.value)
        assertTrue(settings.allFilesAsked)
        assertEquals(0, access.opened)
        val other = state(MemorySettings(), allFiles = access)
        other.load()
        assertTrue(other.back())
        assertIs<Screen.PickFolder>(other.screen.value)
    }

    @Test fun no_step_where_the_permission_is_not_declared_or_already_granted() = runTest {
        for (access in listOf(FakeAllFilesAccess(declared = false), FakeAllFilesAccess(declared = true, isGranted = true), null)) {
            val st = state(MemorySettings(), allFiles = access)
            st.load()
            assertIs<Screen.PickFolder>(st.screen.value, "$access")
        }
    }

    @Test fun library_shows_all_files_access_after_add_rom_folder_where_declared() = runTest {
        val access = FakeAllFilesAccess(declared = true, isGranted = false)
        val st = state(MemorySettings(ContentRef("Home")), allFiles = access)
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals(listOf(SettingsKind.RESCAN, SettingsKind.HOME, SettingsKind.ADD_EXTRA, SettingsKind.ALL_FILES, SettingsKind.SAVES), assertIs<Screen.Settings>(st.screen.value).rows.map { it.kind })
        assertEquals(SettingsRow(SettingsKind.ALL_FILES, "All files access", "Off"), libraryRow(st, SettingsKind.ALL_FILES))
        st.select(rowIndex(st, SettingsKind.ALL_FILES)); st.openSelected()
        assertEquals(1, access.opened)
        access.isGranted = true
        st.onAllFilesAccessChanged()                        // the resume after Android's page
        assertEquals("On", libraryRow(st, SettingsKind.ALL_FILES).secondary)
        val plain = state(MemorySettings(ContentRef("Home")), allFiles = FakeAllFilesAccess(declared = false))
        plain.load()
        plain.openSettings(SettingsSection.LIBRARY)
        assertTrue(assertIs<Screen.Settings>(plain.screen.value).rows.none { it.kind == SettingsKind.ALL_FILES })
    }

    @Test fun library_marks_an_extra_on_an_unmounted_card_until_it_is_back() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source, looseMounted = false)
        val st = state(settings, card)
        st.load()
        st.dismissNotice()
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals(SettingsRow(SettingsKind.EXTRA, "ROM Folder: Loose", "Not available", extra = loose, warning = true), libraryRow(st, SettingsKind.EXTRA))
        card.looseMounted = true
        st.load()                                                                                // Back: the card is found, rescanned in
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals(SettingsRow(SettingsKind.EXTRA, "ROM Folder: Loose", "ROM folder", extra = loose), libraryRow(st, SettingsKind.EXTRA))
    }

    @Test fun library_says_an_extra_mounted_but_not_listing_yet_is_not_ready_yet() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source, looseMounted = false)
        val st = state(settings, card)
        st.load()
        card.looseMounted = true
        card.looseLag = Int.MAX_VALUE
        st.dismissNotice()
        st.onForeground()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals("Not ready yet", libraryRow(st, SettingsKind.EXTRA).secondary)
    }

    @Test fun a_stored_not_ready_yet_extra_reads_as_not_available_after_a_restart() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source, looseMounted = false)
        val store = MemoryLibraryIndexStore()
        val st = state(settings, card, store = store)
        st.load()
        card.looseMounted = true
        card.looseLag = Int.MAX_VALUE
        st.dismissNotice()
        st.onForeground()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()
        assertEquals(listOf(loose.id), assertNotNull(LibraryIndexCodec.decode(store.text!!)).lateMount)
        card.looseMounted = false                                                                // pulled; the app restarts
        card.looseLag = 0
        val st2 = state(settings, card, store = store)
        st2.load()                                                                               // from the stored index, no scan
        st2.openSettings(SettingsSection.LIBRARY)
        assertEquals("Not available", libraryRow(st2, SettingsKind.EXTRA).secondary)
    }

    @Test fun rescanning_from_library_updates_an_extra_row_that_went_offline() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val card = CardSource(source)
        val st = state(settings, card)
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals("ROM folder", libraryRow(st, SettingsKind.EXTRA).secondary)
        card.looseMounted = false
        openLibraryOn(st, SettingsKind.RESCAN)
        st.onButton(NxButton.A)                                                                  // the counts row: rescan
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals("Not available", libraryRow(st, SettingsKind.EXTRA).secondary)
    }

    @Test fun an_offline_extra_can_still_be_removed_from_library() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val picker = NoopPicker()
        val st = state(settings, CardSource(source, looseMounted = false), picker)
        st.load()
        st.dismissNotice()
        st.openSettings(SettingsSection.LIBRARY)
        st.select(assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.kind == SettingsKind.EXTRA })
        st.onButton(NxButton.A)                                                                  // confirm prompt
        assertEquals(loose, assertIs<Screen.Settings>(st.screen.value).confirmRemove)
        st.onButton(NxButton.A)                                                                  // remove
        assertEquals(emptyList(), settings.extraFolders)
        assertEquals(listOf(loose.ref), picker.forgotten)
        assertTrue(assertIs<Screen.Settings>(st.screen.value).rows.none { it.kind == SettingsKind.EXTRA })
    }

    @Test fun library_marks_a_home_known_offline_until_it_reads_again() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))
        val card = CardSource(source, homeMounted = false)
        val st = state(settings, card, store = store)
        st.load()
        st.dismissError()
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals(SettingsRow(SettingsKind.HOME, "Home folder", "Not available", warning = true), libraryRow(st, SettingsKind.HOME))
        card.homeMounted = true
        st.onButton(NxButton.B)                                                                  // Back from Library: load, the probe reads it
        assertIs<Screen.SettingsMenu>(st.screen.value)
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals(SettingsRow(SettingsKind.HOME, "Home folder", "Home"), libraryRow(st, SettingsKind.HOME))
    }

    @Test fun a_start_with_the_home_on_an_unmounted_card_keeps_the_home_and_shows_the_index() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))
        val card = CardSource(source, homeMounted = false)
        val st = state(settings, card, store = store)
        st.load()
        assertEquals(1, gbaCount(st))                                                            // Mario.gba, from the index
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertNotNull(store.text)
        assertEquals(0, store.deletes)
        assertEquals("The home folder's storage is not available. Insert it, then reopen the app.", st.error.value)
        st.dismissError()
        st.load()                                                                                // a return to the library: said once
        assertNull(st.error.value)
        card.homeMounted = true
        st.load()
        assertNull(st.error.value)
        assertTrue("Home/Bios" in source.written)                                               // the skeleton, now it is readable
    }

    @Test fun a_rescan_with_the_home_on_an_unmounted_card_keeps_the_library_as_it_was() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val card = CardSource(source)
        val store = MemoryLibraryIndexStore()
        val st = state(settings, card, store = store)
        st.load()
        val before = store.text
        card.homeMounted = false
        st.rescan()
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertEquals(before, store.text)
        assertEquals(0, store.deletes)
        assertEquals(1, gbaCount(st))
        assertEquals("The home folder's storage is not available. Insert it, then use Rescan in Settings → Library.", st.error.value)
    }

    @Test fun a_first_scan_with_the_home_on_an_unmounted_card_keeps_the_home_for_the_next_start() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore()
        val st = state(settings, CardSource(source, homeMounted = false), store = store)
        st.load()
        assertIs<Screen.PickFolder>(st.screen.value)
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertEquals("The home folder's storage is not available. Insert it, then reopen the app.", st.error.value)
    }

    @Test fun menu_opens_on_multi_tag_consoles_and_switching_writes_the_sidecar_and_moves_the_game() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        val systems = assertIs<Screen.Systems>(state.screen.value).systems
        state.openSystem(systems.first { it.tag == "GB" })
        state.onButton(NxButton.MENU)
        assertNotNull(assertIs<Screen.Games>(state.screen.value).menu)   // every console list has a menu now
        assertTrue(ContextKind.EMULATOR !in menuKinds(state))          // GB has one emulator: no Emulator item
        state.onButton(NxButton.B)
        state.back()
        state.openSystem(systems.first { it.tag == "GBA" })
        state.onButton(NxButton.MENU)
        val menu = assertIs<Screen.Games>(state.screen.value).menu!!
        assertEquals("Mario", menu.entry!!.game.shownName)
        assertEquals(listOf(ContextKind.PIN, ContextKind.HIDE, ContextKind.RENAME, ContextKind.ADD_TO_COLLECTION, ContextKind.GAME_SETTINGS, ContextKind.EMULATOR), menu.items.map { it.kind })
        state.onButton(NxButton.B)
        choose(state, ContextKind.EMULATOR)              // open the Emulator submenu
        assertEquals(listOf("GBA", "MGBA"), assertIs<Screen.Games>(state.screen.value).menu!!.sub!!.values)
        state.onButton(NxButton.DOWN)
        state.onButton(NxButton.A)                       // choose MGBA
        assertEquals("Mario.gba\tMGBA\n", source.textAt("Home/Roms/Game Boy Advance (GBA)/systems.txt"))
        val games = assertIs<Screen.Games>(state.screen.value)
        assertEquals("MGBA", games.system.tag)
        assertEquals(listOf("Mario"), games.games.map { it.shownName })
        assertNull(games.menu)
    }

    @Test fun the_main_menu_is_typed_rows_ending_with_tools_and_lists_carry_their_system() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.onConsoles()
        val main = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.TOOLS, main.tabs.last().category)
        val rows = main.rows                                             // no pins: the first tab is Consoles
        assertTrue(rows.all { it is MainRow.System })
        assertEquals(rows.size, state.selection.value.count)
        state.openSystem((rows[0] as MainRow.System).system)
        val games = assertIs<Screen.Games>(state.screen.value)
        assertEquals(ListSource.Console((rows[0] as MainRow.System).system), games.source)
        assertTrue(games.entries.all { it.system == (rows[0] as MainRow.System).system })
        state.onButton(NxButton.MENU)
        val menu = assertIs<Screen.Games>(state.screen.value).menu!!
        assertEquals(games.entries[0], menu.entry)
        assertEquals(ContextKind.PIN, menu.items[0].kind)     // every console list has a menu
        state.onButton(NxButton.A)                            // PIN closes the menu
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
    }

    @Test fun menu_closes_on_b_and_menu_and_long_press_opens_on_a_row() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        val gba = assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "GBA" }
        state.openSystem(gba)
        state.openContextMenu(0)
        assertNotNull(assertIs<Screen.Games>(state.screen.value).menu)
        state.onButton(NxButton.B)
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        state.onButton(NxButton.MENU); state.onButton(NxButton.MENU)
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        assertIs<Screen.Games>(state.screen.value)       // B with the menu closed would have gone back; MENU did not
    }

    @Test fun unassigned_games_get_a_console_menu_that_assigns_the_default_tag() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val state = state(settings)
        state.load()
        openUnassigned(state)
        choose(state, ContextKind.CONSOLE)                       // Console submenu
        val sub = assertIs<Screen.Games>(state.screen.value).menu!!.sub!!
        repeat(sub.values.indexOf("psp")) { state.onButton(NxButton.DOWN) }
        state.onButton(NxButton.A)
        assertEquals("thing.chd\tPSP\n", source.textAt("Home/Roms/.sources/${settings.extraFolders.single().id}/systems.txt"))
        assertTrue(source.written.none { it.startsWith("sd/") })   // nothing written inside the extra
        val games = assertIs<Screen.Games>(state.screen.value)
        assertEquals("PSP", games.system.tag)
        assertEquals(listOf("thing"), games.games.map { it.shownName })
    }

    @Test fun a_two_column_collection_picker_steps_across_with_left_and_right() = runTest {
        val names = (1..9).map { "C$it" }
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms", "Collections"),
                "Home/Roms" to listOf("Game Boy (GB)"),
                "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
                "Home/Collections" to names.map { "$it.txt" },
            ),
            texts = names.associate { "Home/Collections/$it.txt" to "" },
        )
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.CONSOLES })
        state.select(0); state.onButton(NxButton.A)
        choose(state, ContextKind.ADD_TO_COLLECTION)
        fun sub() = assertIs<Screen.Games>(state.screen.value).menu!!.sub!!
        assertTrue(sub().values.size in 9..16, "picker: ${sub().values.size}")
        state.onButton(NxButton.RIGHT)
        assertEquals(0, sub().index)                 // one column: LEFT/RIGHT do nothing
        state.onMenuLayout(2)
        val perColumn = (sub().values.size + 1) / 2   // the columns balance ([menuColumnRows])
        state.onButton(NxButton.RIGHT)
        assertEquals(perColumn, sub().index)
        state.onButton(NxButton.UP)
        assertEquals(perColumn - 1, sub().index)     // the right column's top: on to the left column's foot
        state.onButton(NxButton.DOWN); state.onButton(NxButton.LEFT)
        assertEquals(0, sub().index)
        repeat(20) { state.onButton(NxButton.DOWN) }
        assertEquals(20 % sub().values.size, sub().index)   // DOWN loops round the picker
    }

    @Test fun an_unassigned_img_offers_only_the_playstation() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val state = state(settings)
        state.load()
        openUnassigned(state)
        val games = assertIs<Screen.Games>(state.screen.value)
        state.select(games.games.indexOfFirst { it.fileName == "v.img" })
        choose(state, ContextKind.CONSOLE)
        assertEquals(listOf("ps"), assertIs<Screen.Games>(state.screen.value).menu!!.sub!!.values)
        assertNull(state.error.value)
        assertNull(source.textAt("sd/Loose/systems.txt"))
    }

    @Test fun switching_emulator_never_prompts_and_switches_at_once() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), source)
        state.load()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "GBA" })
        choose(state, ContextKind.EMULATOR); state.onButton(NxButton.DOWN); state.onButton(NxButton.A)
        val games = assertIs<Screen.Games>(state.screen.value)
        assertNull(games.menu)
        assertNull(state.error.value)
        assertEquals("MGBA", games.system.tag)
        assertEquals("Mario.gba\tMGBA\n", source.textAt("Home/Roms/Game Boy Advance (GBA)/systems.txt"))
    }

    @Test fun buttons_are_swallowed_while_the_sidecar_write_runs() = runTest {
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val gated = object : RomsSource by source {
            override suspend fun writeText(dir: ContentRef, name: String, text: String): Boolean { writes++; gate.await(); return source.writeText(dir, name, text) }
        }
        val state = state(MemorySettings(ContentRef("Home")), gated)
        state.load()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "GBA" })
        choose(state, ContextKind.EMULATOR); state.onButton(NxButton.DOWN); state.onButton(NxButton.A)   // choose MGBA: the write suspends
        val during = assertIs<Screen.Games>(state.screen.value)
        assertTrue(during.menu!!.busy)
        state.onButton(NxButton.A); state.onButton(NxButton.B); state.onButton(NxButton.DOWN); state.onButton(NxButton.MENU)
        state.menuSelect(0); state.menuOpen(); state.openContextMenu(0)
        assertTrue(state.back())                          // Back is swallowed too: the menu stays open
        assertEquals(during, state.screen.value)
        assertEquals(1, writes)
        gate.complete(Unit)
        val after = assertIs<Screen.Games>(state.screen.value)
        assertEquals("MGBA", after.system.tag)
        assertNull(after.menu)
        assertEquals(1, writes)
    }

    @Test fun first_home_pick_offers_an_extra_folder_then_skip_lands_on_systems() = runTest {
        val state = state(MemorySettings())
        state.load()
        state.setHomePast(ContentRef("Home"))
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        state.onButton(NxButton.B)
        assertIs<Screen.Systems>(state.screen.value)
    }

    @Test fun a_on_the_offer_opens_the_extra_picker_and_a_good_pick_lands_on_systems_with_its_consoles() = runTest {
        val settings = MemorySettings()
        val picker = NoopPicker()
        val state = state(settings, picker = picker)
        state.load()
        state.setHomePast(ContentRef("Home"))
        state.onButton(NxButton.A)
        assertEquals(1, picker.extraPicks)
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        state.addExtra(ContentRef("sd/Loose"))
        state.select(state.selection.value.count - 1); state.onButton(NxButton.A)                                         // the summary's Continue
        val systems = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(2, systems.systems.first { it.tag == "GBA" }.gameCount)   // Mario (home) + Kirby (the extra)
        assertTrue(systems.systems.none { it.unassigned })
    }

    @Test fun an_overlapping_first_extra_keeps_the_offer_with_an_error() = runTest {
        val settings = MemorySettings()
        val state = state(settings)
        state.load()
        state.setHomePast(ContentRef("Home"))
        state.addExtra(ContentRef("Home"))
        assertEquals("That folder is your home folder.", state.error.value)
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        assertEquals(emptyList<ExtraFolder>(), settings.extraFolders)
    }

    @Test fun a_home_repick_from_settings_does_not_offer_an_extra() = runTest {
        val src = FakeRomsSource(dirs = mapOf("Home" to emptyList(), "Home2" to emptyList()))
        val state = state(MemorySettings(ContentRef("Home")), src)
        state.load()
        state.setHomePast(ContentRef("Home2"))
        assertIs<Screen.Systems>(state.screen.value)
    }

    @Test fun a_roms_folder_mispick_does_not_offer_an_extra() = runTest {
        val settings = MemorySettings()
        val state = state(settings)
        state.load()
        state.setHomePast(ContentRef("Home/Roms"))
        assertIs<Screen.PickFolder>(state.screen.value)
        assertTrue(state.error.value!!.contains("Pick its parent"))
        assertNull(settings.treeRef)
    }

    @Test fun back_on_the_offer_skips() = runTest {
        val state = state(MemorySettings())
        state.load()
        state.setHomePast(ContentRef("Home"))
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        assertTrue(state.back())
        assertIs<Screen.Systems>(state.screen.value)
    }

    @Test fun a_retry_after_a_failed_first_load_still_offers_an_extra() = runTest {
        var failing = true
        val flaky = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> = if (failing) throw IllegalStateException("boom") else source.listChildren(dir)
        }
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings, flaky)
        state.load()                                   // transient failure: the tree is kept, the picker shows
        assertIs<Screen.PickFolder>(state.screen.value)
        assertEquals(ContentRef("Home"), settings.treeRef)
        state.dismissError()
        failing = false
        state.setHomePast(ContentRef("Home"))
        assertIs<Screen.AddFirstExtra>(state.screen.value)
    }

    @Test fun opening_a_system_makes_no_source_call() = runTest {
        val counting = CountingRomsSource(source)
        val st = state(MemorySettings(ContentRef("Home")), counting)
        st.load()
        counting.calls.clear()
        st.openSystem(assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "NDS" })
        assertEquals(listOf("Mario Kart DS"), assertIs<Screen.Games>(st.screen.value).games.map { it.shownName })
        assertEquals(emptyList<String>(), counting.calls)
    }

    @Test fun switcher_entries_resolve_from_the_index_with_no_source_call() = runTest {
        val counting = CountingRomsSource(source)
        val st = state(MemorySettings(ContentRef("Home")), counting, data = twoRecents())
        st.load()
        counting.calls.clear()
        st.onButton(NxButton.SELECT)
        assertEquals(listOf("Tetris.gb", "Mario Kart DS.nds"), assertIs<Screen.Switcher>(st.screen.value).entries.map { it.game.fileName })
        assertEquals(emptyList<String>(), counting.calls)
    }

    @Test fun a_re_added_extra_gets_the_same_id() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val st = state(settings)
        st.addExtra(ContentRef("sd/Loose"))
        val first = settings.extraFolders.single()
        st.removeExtra(first)
        st.addExtra(ContentRef("sd/Loose"))
        assertEquals(first, settings.extraFolders.single())
    }

    @Test fun a_colliding_id_gets_a_suffix() = runTest {
        // Another folder already holds the id sd/Loose would get (a hash collision): no load() first, so nothing migrates it.
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("Loose-d116b9e3", ContentRef("sd/Other"))) }
        state(settings).addExtra(ContentRef("sd/Loose"))
        assertEquals(listOf("Loose-d116b9e3", "Loose-d116b9e3-2"), settings.extraFolders.map { it.id })
    }

    @Test fun re_adding_an_existing_extra_says_it_is_already_added_and_keeps_the_grant() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val picker = NoopPicker()
        val st = state(settings, picker = picker)
        st.load()
        st.addExtra(ContentRef("sd/Loose"))
        val before = settings.extraFolders
        st.addExtra(ContentRef("sd/Loose"))
        assertEquals("That folder is already added.", st.error.value)
        assertEquals(before, settings.extraFolders)
        assertFalse(ContentRef("sd/Loose") in picker.forgotten)
        st.dismissError()
        st.addExtra(ContentRef("sd"))                                                             // a parent of the extra still nests
        assertEquals("That folder overlaps Loose; files would list twice.", st.error.value)
        assertEquals(before, settings.extraFolders)
    }

    @Test fun adding_the_home_as_an_extra_says_it_is_the_home_and_keeps_the_grant() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val picker = NoopPicker()
        val st = state(settings, picker = picker)
        st.load()
        st.addExtra(ContentRef("Home"))
        assertEquals("That folder is your home folder.", st.error.value)
        assertEquals(emptyList<ExtraFolder>(), settings.extraFolders)
        assertFalse(ContentRef("Home") in picker.forgotten)
    }

    @Test fun a_usable_index_shows_systems_before_any_source_call() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))
        lateinit var st: AppState
        val early = ArrayList<String>()
        val counting = CountingRomsSource(source) { if (st.screen.value !is Screen.Systems) early += it }
        st = state(settings, counting, store = store)
        st.load()
        assertEquals(listOf("GB", "GBA", "NDS"), assertIs<Screen.Systems>(st.screen.value).systems.map { it.tag })
        assertEquals(emptyList<String>(), early)                               // nothing touched the tree before the list showed
        // then only the background home probe lists, and the skeleton lists Roms once (this home misses console folders)
        assertEquals(listOf("listChildren:Home", "listChildren:Home/Roms"), counting.calls.listings())
        assertEquals(0, store.writes)
    }

    @Test fun a_missing_garbled_old_or_mismatched_index_scans_and_writes_a_fresh_one() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val good = scannedIndex(settings)
        val cases = listOf(
            null,
            "{ not json",
            LibraryIndexCodec.encode(good.copy(version = 1)),
            LibraryIndexCodec.encode(good.copy(version = LibraryIndex.VERSION + 1)),
            LibraryIndexCodec.encode(good.copy(home = "Elsewhere")),
            LibraryIndexCodec.encode(good.copy(defaultTags = mapOf("gba" to "MGBA"))),
            LibraryIndexCodec.encode(good.copy(extras = listOf(IndexedExtra("Loose-d116b9e3", "sd/Loose")))),
        )
        for (stored in cases) {
            val store = MemoryLibraryIndexStore(stored)
            val counting = CountingRomsSource(source)
            val st = state(settings, counting, store = store)
            st.load()
            assertIs<Screen.Systems>(st.screen.value)
            assertTrue("listChildren:Home/Roms" in counting.calls, "no scan for $stored")
            val written = assertNotNull(store.text?.let(LibraryIndexCodec::decode))
            assertTrue(written.fits(ContentRef("Home"), settings.extraFolders, settings.defaultTags))
            assertEquals(1, store.writes)
        }
    }

    @Test fun extras_in_another_order_keep_the_index_but_a_changed_default_tag_rescans_on_back() = runTest {
        val a = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val b = ExtraFolder(Sources.idFor("Mixed", ContentRef("sd/Mixed")), ContentRef("sd/Mixed"))   // no such folder: scans empty
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(a, b) }
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))
        settings.extraFolders = listOf(b, a)
        val counting = CountingRomsSource(source)
        val st = state(settings, counting, store = store)
        st.load()
        assertEquals(listOf("listChildren:Home", "listChildren:Home/Roms"), counting.calls.listings())   // same set of extras: no scan (probe + skeleton only)
        st.openSettings(SettingsSection.DEFAULT_EMULATORS)
        st.select(assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.consoleId == "gba" })
        st.onButton(NxButton.RIGHT)                                             // GBA -> MGBA
        counting.calls.clear()
        st.onButton(NxButton.B)
        assertTrue("listChildren:Home/Roms" in counting.calls)                  // the index no longer fits: scanned
        assertEquals("MGBA", assertNotNull(store.text?.let(LibraryIndexCodec::decode)).defaultTags["gba"])
        val lib = assertIs<Screen.Settings>(st.screen.value)                    // back on Emulators, on Default emulators
        assertEquals(SettingsSection.EMULATORS, lib.section)
        assertEquals(SettingsKind.DEFAULTS, lib.rows[st.selection.value.index].kind)
        counting.calls.clear()
        st.onButton(NxButton.B)
        assertTrue("listChildren:Home/Roms" !in counting.calls)                 // the rescan's index fits: no second scan
        assertIs<Screen.SettingsMenu>(st.screen.value)                          // B from Emulators lands on the Settings menu
        st.onButton(NxButton.B); st.onButton(NxButton.B)                        // Tools, then the main menu
        assertTrue(assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "MGBA" }.games.any { it.fileName == "Kirby.gba" })
    }

    @Test fun load_after_skipping_the_first_extra_reads_the_index_without_a_scan() = runTest {
        val counting = CountingRomsSource(source)
        val store = MemoryLibraryIndexStore()
        val st = state(MemorySettings(), counting, store = store)
        st.load()
        st.setHomePast(ContentRef("Home"))
        assertIs<Screen.AddFirstExtra>(st.screen.value)
        assertEquals(1, store.writes)
        counting.calls.clear()
        st.onButton(NxButton.B)
        assertIs<Screen.Systems>(st.screen.value)
        assertEquals(listOf("listChildren:Home"), counting.calls.listings())
        assertEquals(1, store.writes)
    }

    @Test fun the_home_probe_clears_a_gone_home_and_ignores_a_transient_failure() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val stored = LibraryIndexCodec.encode(scannedIndex(settings))
        val store = MemoryLibraryIndexStore(stored)
        val gone = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> = throw IllegalArgumentException("Folder no longer exists")
        }
        val st = state(settings, gone, store = store)
        st.load()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()                   // one retry first: a card's root can lag its mount
        assertIs<Screen.PickFolder>(st.screen.value)
        assertNull(settings.treeRef)
        assertNull(store.text)
        assertEquals("Could not read the home folder: Folder no longer exists", st.error.value)

        val flakySettings = MemorySettings(ContentRef("Home"))
        val flaky = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> = throw IllegalStateException("busy")
        }
        val st2 = state(flakySettings, flaky, store = MemoryLibraryIndexStore(stored))
        st2.load()
        assertIs<Screen.Systems>(st2.screen.value)
        assertEquals(ContentRef("Home"), flakySettings.treeRef)
        assertNull(st2.error.value)
    }

    @Test fun a_failed_index_write_deletes_the_older_index() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))   // an older copy that still fits
        store.failWrites = true
        val st = state(settings, store = store)
        st.rescan()
        assertIs<Screen.Systems>(st.screen.value)
        assertNull(store.text)                                                   // the next start scans instead of trusting it
        assertEquals(1, store.deletes)
    }

    @Test fun removing_an_extra_rewrites_the_index_and_back_goes_straight_to_the_menu() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val store = MemoryLibraryIndexStore()
        val counting = CountingRomsSource(source)
        val st = state(settings, counting, store = store)
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        st.select(rowIndex(st, SettingsKind.EXTRA)); st.openSelected(); st.onButton(NxButton.A)   // remove Loose
        assertEquals(2, store.writes)
        assertEquals(emptyList<IndexedExtra>(), assertNotNull(store.text?.let(LibraryIndexCodec::decode)).extras)
        counting.calls.clear()
        st.onButton(NxButton.B)                                                 // Library -> menu: the re-index is current, no reload
        assertEquals(emptyList<String>(), counting.calls.listings())
        assertIs<Screen.SettingsMenu>(st.screen.value)
        st.onButton(NxButton.B); st.onButton(NxButton.B)
        assertTrue(assertIs<Screen.Systems>(st.screen.value).systems.none { it.unassigned })
    }

    @Test fun a_tag_change_moves_the_game_in_the_index_without_a_scan() = runTest {
        val store = MemoryLibraryIndexStore()
        val counting = CountingRomsSource(source)
        val st = state(MemorySettings(ContentRef("Home")), counting, store = store)
        st.load()
        st.openSystem(assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GBA" })
        counting.calls.clear()
        choose(st, ContextKind.EMULATOR); st.onButton(NxButton.DOWN); st.onButton(NxButton.A)   // Emulator -> MGBA
        val games = assertIs<Screen.Games>(st.screen.value)
        assertEquals("MGBA", games.system.tag)
        assertEquals(listOf("Mario"), games.games.map { it.shownName })
        assertEquals(emptyList<String>(), counting.calls.listings())
        val saved = assertNotNull(store.text?.let(LibraryIndexCodec::decode))
        assertEquals(listOf("Mario.gba"), saved.systems.first { it.tag == "MGBA" }.games.map { it.file })
        assertEquals(emptyList<IndexedGame>(), saved.systems.first { it.tag == "GBA" }.games)   // kept at 0: it has a home folder
        assertEquals(2, store.writes)
        st.back()
        val systems = assertIs<Screen.Systems>(st.screen.value).systems
        assertTrue(systems.none { it.tag == "GBA" })                                            // empty: hidden from the list
        assertEquals(systems.indexOfFirst { it.tag == "MGBA" }, st.selection.value.index)
        assertEquals(systems.size, st.selection.value.count)       // the Consoles tab (Tools is a tab of its own)
    }

    @Test fun assigning_the_last_unassigned_game_removes_unassigned_from_the_list() = runTest {
        val src = FakeRomsSource(dirs = mapOf("Home" to emptyList(), "sd/One" to listOf("thing.chd")))
        val one = ExtraFolder(Sources.idFor("One", ContentRef("sd/One")), ContentRef("sd/One"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(one) }
        val st = state(settings, src)
        st.load()
        assertEquals(emptyList(), assertIs<Screen.Systems>(st.screen.value).systems)             // the skeleton consoles are all empty
        openUnassigned(st)
        choose(st, ContextKind.CONSOLE)                                     // Console submenu
        val sub = assertIs<Screen.Games>(st.screen.value).menu!!.sub!!
        repeat(sub.values.indexOf("psp")) { st.onButton(NxButton.DOWN) }
        st.onButton(NxButton.A)
        assertEquals("thing.chd\tPSP\n", src.textAt("Home/Roms/.sources/${one.id}/systems.txt"))
        assertEquals("PSP", assertIs<Screen.Games>(st.screen.value).system.tag)
        st.back()
        assertEquals(listOf("PSP"), assertIs<Screen.Systems>(st.screen.value).systems.map { it.tag })
        assertEquals(ListSelection(0, 1), st.selection.value)                                   // PSP, the Consoles tab's only row
        st.openSettings(SettingsSection.LIBRARY)
        assertTrue(assertIs<Screen.Settings>(st.screen.value).rows.none { it.kind == SettingsKind.UNASSIGNED })   // nothing left to assign
    }

    @Test fun a_failed_index_write_after_a_move_deletes_the_index_and_the_next_start_scans() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore()
        val st = state(settings, store = store)
        st.load()
        assertNotNull(store.text)
        store.failWrites = true
        st.openSystem(assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GBA" })
        choose(st, ContextKind.EMULATOR); st.onButton(NxButton.DOWN); st.onButton(NxButton.A)
        assertEquals("MGBA", assertIs<Screen.Games>(st.screen.value).system.tag)                // the in-memory index has the move
        assertNull(store.text)
        store.failWrites = false
        val counting = CountingRomsSource(source)
        val next = state(settings, counting, store = store)
        next.load()
        assertTrue("listChildren:Home/Roms" in counting.calls)
        assertTrue(assertIs<Screen.Systems>(next.screen.value).systems.first { it.tag == "MGBA" }.games.any { it.fileName == "Mario.gba" })
    }

    private fun gbHome(vararg files: String) = FakeRomsSource(
        dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Game Boy (GB)"), "Home/Roms/Game Boy (GB)" to files.toList()),
    )

    private val psDir = "Home/Roms/Sony PlayStation (PS)"
    private fun psHome(files: List<String>, texts: Map<String, String>) = FakeRomsSource(
        dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Sony PlayStation (PS)"), psDir to files),
        texts = texts,
    )

    @Test fun a_cue_launches_with_its_tracks_as_companions() = runTest {
        val cue = "FILE \"Crash (Track 1).bin\" BINARY\n  TRACK 01 MODE2/2352\nFILE \"crash (track 2).bin\" BINARY\n  TRACK 02 AUDIO\n"
        val src = psHome(listOf("Crash.cue", "Crash (Track 1).bin", "Crash (Track 2).bin"), mapOf("$psDir/Crash.cue" to cue))
        val runner = RecordingRunner()
        val st = state(MemorySettings(ContentRef("Home")), src, runner = runner)
        st.load()
        val ps = assertIs<Screen.Systems>(st.screen.value).systems.single { it.tag == "PS" }
        assertEquals(listOf("Crash.cue"), ps.games.map { it.fileName })   // the tracks are not listed
        st.openSystem(ps)
        st.play(ps.games.single())
        val session = runner.launched.single()
        assertEquals("pcsx_rearmed", session.core.id)
        assertEquals(
            listOf(
                Companion("Crash (Track 1).bin", ContentRef("$psDir/Crash (Track 1).bin")),
                Companion("crash (track 2).bin", ContentRef("$psDir/Crash (Track 2).bin")),   // the cue's spelling, the file's ref
            ),
            session.companions,
        )
    }

    @Test fun a_cue_whose_bin_is_missing_fails_the_launch_and_writes_no_recent() = runTest {
        val src = psHome(listOf("Crash.cue"), mapOf("$psDir/Crash.cue" to "FILE \"Crash.bin\" BINARY\n"))
        val runner = RecordingRunner()
        val data = FakeUserData()
        val st = state(MemorySettings(ContentRef("Home")), src, runner = runner, data = data)
        st.load()
        val ps = assertIs<Screen.Systems>(st.screen.value).systems.single { it.tag == "PS" }
        st.openSystem(ps)
        st.play(ps.games.single())
        assertEquals("Missing Crash.bin next to Crash.cue", st.error.value)
        assertTrue(runner.launched.isEmpty())
        assertTrue(data.recents.isEmpty())
    }

    @Test fun only_fbneo_launches_look_up_arcade_companions() = runTest {
        // A flycast (Dreamcast/NAOMI) zip is loaded as is too, but fbneo's set table must not attach fbneo parents or BIOS to it.
        val table = com.nxredux.mobile.library.ArcadeSets.parse("mslug6\tMetal Slug 6\t\tneogeo\t\nneogeo\tNeo Geo\t\t\t1\n")
        var reads = 0
        val src = FakeRomsSource(dirs = mapOf(
            "Home" to listOf("Roms"), "Home/Roms" to listOf("Dreamcast (DC)", "Arcade (FBN)"),
            "Home/Roms/Dreamcast (DC)" to listOf("mslug6.zip", "neogeo.zip"),
            "Home/Roms/Arcade (FBN)" to listOf("mslug6.zip"),
        ))
        val runner = RecordingRunner()
        val st = AppState(
            AppDeps(MemorySettings(ContentRef("Home")), src, runner, NoopPicker(), arcadeSets = { reads++; table }, compute = UnconfinedTestDispatcher(testScheduler)),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        st.load()
        advanceUntilIdle()   // the scan reads the set table on deps.compute, the test scheduler
        val dc = assertIs<Screen.Systems>(st.screen.value).systems.single { it.tag == "DC" }
        val readsAfterScan = reads
        st.openSystem(dc)
        st.play(dc.games.single { it.fileName == "mslug6.zip" })
        advanceUntilIdle()
        val session = runner.launched.single()
        assertEquals("flycast", session.core.id)
        assertEquals(emptyList(), session.companions)
        assertEquals(emptyList(), session.systemSets)
        assertEquals(readsAfterScan, reads)   // the set table is not even read
        st.back()
        val fbn = assertIs<Screen.Systems>(st.screen.value).systems.single { it.tag == "FBN" }
        st.openSystem(fbn)
        st.play(fbn.games.single())
        advanceUntilIdle()
        assertEquals(listOf("neogeo"), runner.launched.last().systemSets)   // fbneo still looks its BIOS up
    }

    @Test fun a_naomi_clone_launches_with_its_parent_set_beside_it() = runTest {
        val naomi = com.nxredux.mobile.library.NaomiSets.parse(
            "mushik2e\t\tMushiking II\tnaomi\nmushik2k\tmushik2e\tMushiking II (Korea)\tnaomi\nmslug6\t\tMetal Slug 6\tawbios\n",
        )
        val dcDir = "Home/Roms/Dreamcast (DC)"
        suspend fun launch(files: List<String>, game: String): GameSession {
            val src = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Dreamcast (DC)"), dcDir to files))
            val runner = RecordingRunner()
            val st = AppState(
                AppDeps(MemorySettings(ContentRef("Home")), src, runner, NoopPicker(), naomiSets = { naomi }, compute = UnconfinedTestDispatcher(testScheduler)),
                CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            )
            st.load()
            advanceUntilIdle()
            val dc = assertIs<Screen.Systems>(st.screen.value).systems.single { it.tag == "DC" }
            st.openSystem(dc)
            st.play(dc.games.single { it.fileName == game })
            advanceUntilIdle()
            return runner.launched.single()
        }
        // flycast opens /rom/<parent>.zip: the parent goes along, named in lowercase.
        assertEquals(
            listOf(Companion("mushik2e.zip", ContentRef("$dcDir/MUSHIK2E.ZIP"))),
            launch(listOf("mushik2k.zip", "MUSHIK2E.ZIP"), "mushik2k.zip").companions,
        )
        assertEquals(listOf("mushik2e.7z"), launch(listOf("mushik2k.zip", "mushik2e.7z"), "mushik2k.zip").companions.map { it.fileName })
        // A parent not beside it: launched anyway (a non-merged clone carries its parent's ROMs).
        assertEquals(emptyList(), launch(listOf("mushik2k.zip"), "mushik2k.zip").companions)
        // A parent set and a zipped disc take nothing.
        assertEquals(emptyList(), launch(listOf("mslug6.zip", "mushik2e.zip"), "mslug6.zip").companions)
        assertEquals(emptyList(), launch(listOf("Crazy Taxi.zip", "mushik2e.zip"), "Crazy Taxi.zip").companions)
    }

    @Test fun a_single_file_game_launches_without_companions() = runTest {
        val runner = RecordingRunner()
        val st = state(MemorySettings(ContentRef("Home")), runner = runner)
        st.load()
        val gb = assertIs<Screen.Systems>(st.screen.value).systems.single { it.tag == "GB" }
        st.openSystem(gb)
        st.play(gb.games.single())
        assertEquals(emptyList(), runner.launched.single().companions)
    }

    private val goneError = "Could not find Gone.gb. If it was moved or deleted, use Rescan in Settings → Library."

    @Test fun a_missing_file_shows_the_rescan_hint_and_neither_writes_recents_nor_launches() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings, gbHome("Gone.gb", "Tetris.gb"))))
        val data = FakeUserData()
        val runner = RecordingRunner()
        val st = state(settings, gbHome("Tetris.gb"), runner = runner, data = data, store = store)   // Gone.gb was deleted after the scan
        st.load()
        st.openSystem(assertIs<Screen.Systems>(st.screen.value).systems.single { it.tag == "GB" })
        val games = assertIs<Screen.Games>(st.screen.value).games
        assertEquals(listOf("Gone.gb", "Tetris.gb"), games.map { it.fileName })                 // still listed until a rescan
        st.play(games[0])
        assertEquals(goneError, st.error.value)
        assertTrue(runner.launched.isEmpty())
        assertTrue(data.recents.isEmpty())
        st.dismissError()
        st.play(games[1])
        assertEquals(listOf("Tetris.gb"), runner.launched.map { it.fileName })
    }

    @Test fun a_vanished_recent_still_shows_in_the_switcher_and_fails_at_launch() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings, gbHome("Gone.gb", "Tetris.gb"))))
        val data = FakeUserData(recents = listOf(Recent("/Roms/Game Boy (GB)/Gone.gb")), resumable = mapOf(("GB" to "Gone.gb") to ResumeInfo(9)))
        val runner = RecordingRunner()
        val st = state(settings, gbHome("Tetris.gb"), runner = runner, data = data, store = store)
        st.load()
        st.onButton(NxButton.SELECT)
        assertEquals(listOf("Gone.gb"), assertIs<Screen.Switcher>(st.screen.value).entries.map { it.game.fileName })
        st.onButton(NxButton.A)
        assertEquals(goneError, st.error.value)
        assertTrue(runner.launched.isEmpty())
        assertEquals(listOf(Recent("/Roms/Game Boy (GB)/Gone.gb")), data.recents)
    }

    @Test fun a_revoked_extra_keeps_its_games_until_a_rescan_and_fails_at_launch() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings)))
        val revoked = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> =
                if (dir.value.startsWith("sd/Loose")) throw IllegalArgumentException("revoked") else source.listChildren(dir)
            override suspend fun child(dir: ContentRef, name: String): Entry? =
                if (dir.value.startsWith("sd/Loose")) throw IllegalArgumentException("revoked") else source.child(dir, name)
        }
        val data = FakeUserData()
        val runner = RecordingRunner()
        val picker = NoopPicker()
        val st = state(settings, revoked, picker, runner, data, store)
        st.load()
        val gba = assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GBA" }
        assertEquals(2, gba.gameCount)                                                           // extras are not probed at start
        st.openSystem(gba)
        st.play(gba.games.first { it.fileName == "Kirby.gba" })
        assertEquals("Could not find Kirby.gba. If it was moved or deleted, use Rescan in Settings → Library.", st.error.value)
        assertTrue(runner.launched.isEmpty())
        assertTrue(data.recents.isEmpty())
        st.dismissError()
        st.rescan()
        assertEquals("Lost access to Loose; add it again in Tools → Settings → Library", st.notice.value)
        assertEquals(emptyList<ExtraFolder>(), settings.extraFolders)
        assertEquals(listOf(ContentRef("sd/Loose")), picker.forgotten)
        assertEquals(1, assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GBA" }.gameCount)
    }

    @Test fun the_rescan_row_scans_and_returns_to_settings_with_a_notice() = runTest {
        val counting = CountingRomsSource(source)
        val store = MemoryLibraryIndexStore()
        val st = state(MemorySettings(ContentRef("Home")), counting, store = store)
        st.load()
        source.writeText(ContentRef("Home/Roms/Game Boy (GB)"), "Dr. Mario.gb", "")   // added outside the app: not listed until a rescan
        st.openSettings(SettingsSection.LIBRARY)
        val s = assertIs<Screen.Settings>(st.screen.value)
        val row = s.rows.indexOfFirst { it.kind == SettingsKind.RESCAN }
        assertEquals(0, row)                                           // the counts row, first
        assertEquals(SettingsRow(SettingsKind.RESCAN, "3 games · 3 consoles"), s.rows[row])   // no scan stamped yet: no Last scan
        assertEquals("3 games", gamesLine(st))
        st.select(row)
        counting.calls.clear()
        st.onButton(NxButton.A)
        assertTrue("listChildren:Home/Roms" in counting.calls)
        assertEquals(row, st.selection.value.index)
        assertEquals("4 games", gamesLine(st))
        assertEquals("Library rescanned: 4 games", st.notice.value)
        assertEquals(2, store.writes)
        st.onButton(NxButton.DOWN)
        assertNull(st.notice.value)
    }

    @Test fun one_game_reads_1_game() = runTest {
        val st = state(MemorySettings(ContentRef("Home")), gbHome("Tetris.gb"))
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        val rows = assertIs<Screen.Settings>(st.screen.value).rows
        val row = rows.indexOfFirst { it.kind == SettingsKind.RESCAN }
        assertEquals("1 game", gamesLine(st))
        st.select(row)
        st.openSelected()
        assertEquals("Library rescanned: 1 game", st.notice.value)
    }

    @Test fun a_rescan_that_loses_the_home_shows_the_picker_with_the_error() = runTest {
        var gone = false
        val src = object : RomsSource by source {
            override suspend fun child(dir: ContentRef, name: String): Entry? = if (gone) throw IllegalArgumentException("Folder no longer exists") else source.child(dir, name)
        }
        val settings = MemorySettings(ContentRef("Home"))
        val st = state(settings, src)
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        st.select(assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.kind == SettingsKind.RESCAN })
        gone = true
        st.openSelected()
        advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()                   // one retry first: a card's root can lag its mount
        assertIs<Screen.PickFolder>(st.screen.value)
        assertEquals("Could not read the home folder: Folder no longer exists", st.error.value)
        assertNull(settings.treeRef)
    }

    @Test fun a_second_tap_on_rescan_shows_the_new_count() = runTest {
        val st = state(MemorySettings(ContentRef("Home")))
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        val row = assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.kind == SettingsKind.RESCAN }
        st.select(row)
        st.openSelected()                                                                   // the touch path: no onButton, so no notice dismissal
        assertEquals("Library rescanned: 3 games", st.notice.value)
        source.writeText(ContentRef("Home/Roms/Game Boy (GB)"), "Dr. Mario.gb", "")
        st.select(row)
        st.openSelected()
        assertEquals("4 games", gamesLine(st))
        assertEquals("Library rescanned: 4 games", st.notice.value)
    }

    private fun TestScope.rescanTapped(): Pair<AppState, Int> {
        val st = state(MemorySettings(ContentRef("Home")))
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        val row = assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.kind == SettingsKind.RESCAN }
        st.select(row)
        st.openSelected()
        return st to row
    }

    @Test fun the_notice_clears_itself_after_three_seconds() = runTest {
        val (st, _) = rescanTapped()
        assertEquals("Library rescanned: 3 games", st.notice.value)
        advanceTimeBy(2_999); runCurrent()
        assertEquals("Library rescanned: 3 games", st.notice.value)
        advanceTimeBy(1); runCurrent()
        assertNull(st.notice.value)
    }

    @Test fun a_newer_notice_restarts_the_timer() = runTest {
        val (st, row) = rescanTapped()
        advanceTimeBy(2_000); runCurrent()
        source.writeText(ContentRef("Home/Roms/Game Boy (GB)"), "Dr. Mario.gb", "")
        st.select(row)
        st.openSelected()
        assertEquals("Library rescanned: 4 games", st.notice.value)
        advanceTimeBy(2_000); runCurrent()                                                  // 4 s: the first timer is gone
        assertEquals("Library rescanned: 4 games", st.notice.value)
        advanceTimeBy(1_000); runCurrent()                                                  // 5 s: three after the second
        assertNull(st.notice.value)
    }

    /** A source whose listings wait on [gate] while [hold] is set: a rescan held mid-scan. */
    private class HeldSource(private val inner: RomsSource) : RomsSource by inner {
        var hold = false
        val gate = CompletableDeferred<Unit>()
        override suspend fun listChildren(dir: ContentRef): List<Entry> { if (hold) gate.await(); return inner.listChildren(dir) }
    }

    @Test fun the_rescan_row_scans_under_the_overlay_which_b_cannot_cancel() = runTest {
        val held = HeldSource(source)
        val store = MemoryLibraryIndexStore()
        val st = state(MemorySettings(ContentRef("Home")), held, store = store)
        st.load()
        val row = openLibraryOn(st, SettingsKind.RESCAN)
        source.writeText(ContentRef("Home/Roms/Game Boy (GB)"), "Dr. Mario.gb", "")
        val writes = store.writes
        held.hold = true
        st.onButton(NxButton.A)                                                                  // the counts row: rescan
        assertIs<Screen.Settings>(st.screen.value)                                               // Settings stays underneath
        advanceTimeBy(100); assertFalse(st.loading.value)
        advanceTimeBy(100); assertTrue(st.loading.value)
        assertEquals("Scanning library…", st.loadingMessage.value)
        st.onButton(NxButton.B); assertTrue(st.back())                                           // swallowed: the scan runs on
        assertTrue(st.loading.value)
        assertIs<Screen.Settings>(st.screen.value)
        held.gate.complete(Unit); runCurrent()
        assertFalse(st.loading.value)
        assertEquals(row, st.selection.value.index)
        assertEquals("4 games", gamesLine(st))
        assertEquals("Library rescanned: 4 games", st.notice.value)
        assertEquals(writes + 1, store.writes)
    }

    @Test fun a_hidden_listing_under_the_rescan_overlay_clears_it_and_keeps_the_library() = runTest {
        val held = HeldSource(source)
        val store = MemoryLibraryIndexStore()
        val st = state(MemorySettings(ContentRef("Home")), held, store = store)
        st.load()
        val row = openLibraryOn(st, SettingsKind.RESCAN)
        val writes = store.writes
        held.hold = true
        source.listingFault = ListingFault.UNAVAILABLE
        st.openSelected()
        advanceTimeBy(200); assertTrue(st.loading.value)
        held.gate.complete(Unit); runCurrent()
        assertFalse(st.loading.value)
        assertEquals(row, st.selection.value.index)
        assertEquals("3 games", gamesLine(st))
        assertEquals(listingHiddenNotice, st.notice.value)
        assertEquals(writes, store.writes)
    }

    @Test fun a_fast_rescan_never_flickers_the_overlay() = runTest {
        val (st, _) = rescanTapped()
        assertFalse(st.loading.value)
        assertEquals("Library rescanned: 3 games", st.notice.value)
    }

    @Test fun a_button_still_clears_the_notice_at_once() = runTest {
        val (st, _) = rescanTapped()
        assertEquals("Library rescanned: 3 games", st.notice.value)
        st.onButton(NxButton.DOWN)
        assertNull(st.notice.value)
        advanceTimeBy(3_000); runCurrent()
        assertNull(st.notice.value)
    }

    @Test fun a_lost_access_notice_from_the_same_rescan_is_kept() = runTest {
        var gone = false
        val src = object : RomsSource by source {
            override suspend fun listChildren(dir: ContentRef): List<Entry> =
                if (gone && dir.value.startsWith("sd/Loose")) throw IllegalArgumentException("revoked") else source.listChildren(dir)
            override suspend fun child(dir: ContentRef, name: String): Entry? =
                if (gone && dir.value.startsWith("sd/Loose")) throw IllegalArgumentException("revoked") else source.child(dir, name)
        }
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val st = state(settings, src)
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        st.select(assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.kind == SettingsKind.RESCAN })
        gone = true
        st.onButton(NxButton.A)
        assertIs<Screen.Settings>(st.screen.value)
        assertTrue(st.notice.value!!.startsWith("Lost access to"), st.notice.value)
        assertEquals(emptyList<ExtraFolder>(), settings.extraFolders)
    }

    @Test fun an_extras_art_listing_failure_keeps_the_home_and_lists_the_extras_games() = runTest {
        val src = FakeRomsSource(dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Game Boy (GB)"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "sd/Loose" to listOf("Kirby.gba", ".media"),
            "sd/Loose/.media" to listOf("Kirby.png"),
        ))
        val throwing = object : RomsSource by src {
            override suspend fun listChildren(dir: ContentRef): List<Entry> =
                if (dir.value == "sd/Loose/.media") throw IllegalArgumentException("revoked") else src.listChildren(dir)
        }
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val st = state(settings, throwing)
        st.load()
        val systems = assertIs<Screen.Systems>(st.screen.value)
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertNull(st.error.value)
        assertEquals(listOf("Kirby.gba"), systems.systems.first { it.tag == "GBA" }.games.map { it.fileName })
        st.rescan()
        assertIs<Screen.Systems>(st.screen.value)
        assertEquals(ContentRef("Home"), settings.treeRef)
        assertEquals(1, settings.extraFolders.size)
    }

    // Art deleted outside the app (a file manager) while away: coming back reads each game's art again, so the index and
    // the lists lose it without a rescan, and it stays in the stored index.
    @Test fun art_deleted_while_away_is_dropped_on_coming_back() = runTest {
        val gb = "Home/Roms/Game Boy (GB)/.media"
        val src = FakeRomsSource(dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Game Boy (GB)"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb", ".media"),
            gb to listOf("boxart"),
            "$gb/boxart" to listOf("Tetris.png"),
        ))
        val st = state(MemorySettings(ContentRef("Home")), src)
        st.load(); advanceUntilIdle()
        fun tetris() = assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GB" }.games.single()
        assertEquals(ContentRef("$gb/boxart/Tetris.png"), tetris().boxartRef)
        src.delete("$gb/boxart/Tetris.png")
        st.onForeground(); advanceUntilIdle()
        assertNull(tetris().boxartRef)
    }

    // Coming back lists only the art folders whose modified time moved since the scan read them: none on a return with
    // nothing changed; a deletion (which bumps its folder's time) is still dropped.
    @Test fun coming_back_lists_only_the_art_folders_that_changed() = runTest {
        val gb = "Home/Roms/Game Boy (GB)/.media"
        val fake = FakeRomsSource(dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Game Boy (GB)"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb", ".media"),
            gb to listOf("boxart", "screenshot"),
            "$gb/boxart" to listOf("Tetris.png"),
            "$gb/screenshot" to listOf("Tetris.png"),
        )).apply { stamps["$gb/boxart"] = 1_000L; stamps["$gb/screenshot"] = 1_000L }
        val src = CountingRomsSource(fake)
        val st = state(MemorySettings(ContentRef("Home")), src)
        st.load(); advanceUntilIdle()
        fun tetris() = assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GB" }.games.single()
        src.calls.clear()
        st.onForeground(); advanceUntilIdle()
        assertTrue(src.calls.listings().none { it.startsWith("listChildren:$gb/") }, "${src.calls}")
        fake.delete("$gb/boxart/Tetris.png")
        fake.stamps["$gb/boxart"] = 2_000L
        src.calls.clear()
        st.onForeground(); advanceUntilIdle()
        assertEquals(listOf("listChildren:$gb/boxart"), src.calls.listings().filter { it.startsWith("listChildren:$gb/") })
        assertNull(tetris().boxartRef)
        assertEquals(ContentRef("$gb/screenshot/Tetris.png"), tetris().screenshotRef)
    }

    @Test fun a_throwing_sidecar_write_reports_and_closes_the_menu_without_moving_the_game() = runTest {
        var failing = false
        val throwing = object : RomsSource by source {
            override suspend fun writeText(dir: ContentRef, name: String, text: String): Boolean =
                if (failing) throw IllegalStateException("provider gone") else source.writeText(dir, name, text)
            override suspend fun createDirectory(parent: ContentRef, name: String): Entry? =
                if (failing) throw IllegalStateException("provider gone") else source.createDirectory(parent, name)
        }
        val store = MemoryLibraryIndexStore()
        val st = state(MemorySettings(ContentRef("Home")), throwing, store = store)
        st.load()
        val before = store.read()
        st.openSystem(assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GBA" })
        failing = true
        choose(st, ContextKind.EMULATOR); st.onButton(NxButton.DOWN); st.onButton(NxButton.A)   // choose MGBA
        assertEquals("Could not save the emulator choice", st.error.value)
        val games = assertIs<Screen.Games>(st.screen.value)
        assertNull(games.menu)
        assertEquals("GBA", games.system.tag)
        assertEquals(listOf("Mario.gba"), games.games.map { it.fileName })
        assertEquals(before, store.read())
    }

    @Test fun a_switch_whose_move_changes_nothing_rescans_instead_of_keeping_the_old_tag() = runTest {
        val gate = CompletableDeferred<Unit>()
        var gated = false
        val inner = object : RomsSource by source {
            override suspend fun writeText(dir: ContentRef, name: String, text: String): Boolean {
                if (gated) gate.await()
                return source.writeText(dir, name, text)
            }
        }
        val counting = CountingRomsSource(inner)
        val st = state(MemorySettings(ContentRef("Home")), counting)
        st.load()
        st.openSystem(assertIs<Screen.Systems>(st.screen.value).systems.first { it.tag == "GBA" })
        gated = true
        choose(st, ContextKind.EMULATOR); st.onButton(NxButton.DOWN); st.onButton(NxButton.A)   // choose MGBA: the write suspends
        // Meanwhile the library changes under the open list: Mario is no longer in GBA, so the stale list's move is a no-op.
        source.writeText(ContentRef("Home/Roms/Game Boy Advance (GBA)"), "systems.txt", "Mario.gba\tMGBA\n")
        st.rescan()
        assertEquals(listOf("MGBA"), assertIs<Screen.Systems>(st.screen.value).systems.filter { s -> s.games.any { it.fileName == "Mario.gba" } }.map { it.tag })
        counting.calls.clear()
        gate.complete(Unit)
        assertTrue("listChildren:Home/Roms" in counting.calls, counting.calls.toString())   // the scan fallback ran
        val games = assertIs<Screen.Games>(st.screen.value)
        assertEquals("MGBA", games.system.tag)
        assertEquals(listOf("Mario.gba"), games.games.map { it.fileName })
    }

    @Test fun removing_an_extra_refreshes_the_library_count_after_the_background_scan() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val st = state(settings)
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals("6 games", gamesLine(st))           // NDS, GB, GBA + Kirby + two Unassigned
        val extraRow = openLibraryOn(st, SettingsKind.EXTRA)
        st.onButton(NxButton.A)                          // confirm prompt
        st.onButton(NxButton.A)                          // remove
        val after = assertIs<Screen.Settings>(st.screen.value)
        assertNull(after.confirmRemove)
        assertTrue(after.rows.none { it.kind == SettingsKind.EXTRA })
        assertEquals(extraRow, st.selection.value.index)
        assertEquals(SettingsKind.ADD_EXTRA, after.rows[st.selection.value.index].kind)   // the next row, Loose gone
        assertEquals("3 games", gamesLine(st))           // counted after the re-index
    }

    private fun cheatDb(fake: FakeCheatDbPlatform, scope: CoroutineScope) = CheatDbController(fake, setOf("N64"), scope, now = { 7L })

    private fun stateWithTools(fake: FakeCheatDbPlatform, scope: CoroutineScope, settings: MemorySettings = MemorySettings(ContentRef("Home"))): AppState =
        AppState(AppDeps(settings, source, RecordingRunner(), NoopPicker(), FakeUserData(), cheatDb = cheatDb(fake, scope), compute = scope.coroutineContext[ContinuationInterceptor]!!), scope).also { it.load() }

    private fun TestScope.toolPinState(files: MemoryPrivateFiles, data: FakeUserData = FakeUserData()): AppState {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return AppState(AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), data, cheatDb = cheatDb(FakeCheatDbPlatform(), scope),
            privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
    }

    /** Home with Continue (Tetris), a pinned game (Mario) and every Tools tab tool pinned ([ToolPins.FILE] written before load). */
    private fun TestScope.homeFixture(
        settings: MemorySettings, runner: RecordingRunner = RecordingRunner(),
        data: FakeUserData = FakeUserData(recents = listOf(Recent(tetris)), pins = listOf(Shortcut(mario, "Mario"))),
    ): AppState {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val files = MemoryPrivateFiles()
        val deps = { f: MemoryPrivateFiles -> AppDeps(settings, source, runner, NoopPicker(), data, cheatDb = cheatDb(FakeCheatDbPlatform(), scope), privateFiles = f, compute = UnconfinedTestDispatcher(testScheduler)) }
        // The tool keys this fixture offers: a throwaway state's Tools tab.
        val keys = AppState(deps(MemoryPrivateFiles()), scope).also { it.load(); advanceUntilIdle(); it.openTools() }.screen.value.toolRows().map { it.pinKey }
        files.files[ToolPins.FILE] = ToolPins.encode(keys)
        return AppState(deps(files), scope).also { it.load(); advanceUntilIdle() }
    }

    @Test fun home_list_rows_put_the_tools_last_and_show_every_tool() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.LIST) }
        val state = homeFixture(settings)
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        assertIs<MainRow.Continue>(rows[0])
        assertIs<MainRow.Pin>(rows[1])
        assertTrue(rows.none { it is MainRow.MoreTools })
        assertEquals(state.toolPins.size, rows.takeLastWhile { it is MainRow.PinnedTool }.size)
    }

    // Minimal Home: the List's rows; UP / DOWN wrap at the ends on a fresh press, a held key's repeat stops there.
    @Test fun minimal_home_wraps_on_a_fresh_press_and_a_held_key_stops_at_the_ends() = runTest {
        val state = homeFixture(MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.MINIMAL) })
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        assertIs<MainRow.Continue>(rows[0])
        assertTrue(rows.none { it is MainRow.MoreTools })   // every tool a row, as List
        state.select(rows.size - 1)
        state.onButton(NxButton.DOWN, repeat = true)
        assertEquals(rows.size - 1, state.selection.value.index)   // held: stops on the last
        state.onButton(NxButton.DOWN)
        assertEquals(0, state.selection.value.index)               // fresh: the last to the first
        state.onButton(NxButton.UP, repeat = true)
        assertEquals(0, state.selection.value.index)               // held: stops on the first
        state.onButton(NxButton.UP)
        assertEquals(rows.size - 1, state.selection.value.index)   // fresh: the first to the last
        assertFalse(state.tabFocus.value)
        state.onButton(NxButton.UP)
        assertEquals(rows.size - 2, state.selection.value.index)
    }

    /** Every tab and the game lists in Minimal, on the library of [with_a_tree_systems_load_then_games_then_launch]. */
    private fun TestScope.minimalEverywhere(runner: RecordingRunner, consoles: MenuStyle = MenuStyle.MINIMAL): AppState {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuStyles = MenuCategory.entries.associateWith { MenuStyle.MINIMAL } + (MenuCategory.CONSOLES to consoles)
            gameListStyle = MenuStyle.MINIMAL
        }
        return AppState(AppDeps(settings, source, runner, NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            .also { it.load(); advanceUntilIdle() }
    }

    // A Minimal Consoles tab: A fades the other names on the console (its page the tab's, no band: its list is Minimal too)
    // and opens its list by the morph once they have; the Minimal list's A grows the band on its game, and launches it as
    // the band fills. B morphs back to the tab.
    @Test fun minimal_tab_and_game_list_open_after_their_band() = runTest {
        val runner = RecordingRunner()
        val state = minimalEverywhere(runner)
        val tabs = assertIs<Screen.Systems>(state.screen.value).tabs
        state.selectTab(tabs.indexOfFirst { it.category == MenuCategory.CONSOLES })
        val consoles = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.CONSOLES, consoles.category)
        val nds = consoles.rows.indexOfFirst { (it as? MainRow.System)?.system?.tag == "NDS" }
        state.select(nds)
        state.onButton(NxButton.A)
        assertEquals(MinimalOpening(MenuCategory.CONSOLES, nds, band = false), state.minimalOpening.value)
        assertIs<Screen.Systems>(state.screen.value)   // not yet
        advanceTimeBy(MINIMAL_LEAVE_MS + 1)
        val games = assertIs<Screen.Games>(state.screen.value)
        assertEquals(NavMotion.MINIMAL_FORWARD, state.nav.value.motion)
        assertEquals(null, state.minimalOpening.value)   // the faded tab is gone
        assertEquals(listOf("Mario Kart DS"), games.games.map { it.shownName })
        state.onButton(NxButton.A)   // the game's band
        assertEquals(MinimalOpening(games.source, 0), state.minimalOpening.value)
        assertTrue(runner.launched.isEmpty())
        advanceTimeBy(MINIMAL_OPEN_MS + 1)
        assertEquals(1, runner.launched.size)
        assertEquals("Mario Kart DS.nds", runner.launched[0].fileName)
        advanceUntilIdle()
        assertEquals(null, state.minimalOpening.value)
    }

    // B on a Minimal list morphs back only to a Minimal tab; a List tab slides, and so does A from it into a Minimal list.
    @Test fun minimal_morphs_need_minimal_on_both_sides() = runTest {
        val state = minimalEverywhere(RecordingRunner())
        val tabs = assertIs<Screen.Systems>(state.screen.value).tabs
        state.selectTab(tabs.indexOfFirst { it.category == MenuCategory.CONSOLES })
        val nds = assertIs<Screen.Systems>(state.screen.value).rows.indexOfFirst { (it as? MainRow.System)?.system?.tag == "NDS" }
        state.select(nds)
        state.onButton(NxButton.A)
        advanceTimeBy(MINIMAL_LEAVE_MS + 1)
        assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.B)
        advanceUntilIdle()
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(NavMotion.MINIMAL_BACK, state.nav.value.motion)
        assertEquals(nds, state.selection.value.index)

        val list = minimalEverywhere(RecordingRunner(), consoles = MenuStyle.LIST)
        list.selectTab(assertIs<Screen.Systems>(list.screen.value).tabs.indexOfFirst { it.category == MenuCategory.CONSOLES })
        list.select(assertIs<Screen.Systems>(list.screen.value).rows.indexOfFirst { (it as? MainRow.System)?.system?.tag == "NDS" })
        list.onButton(NxButton.A)
        advanceUntilIdle()
        assertIs<Screen.Games>(list.screen.value)
        assertEquals(NavMotion.FORWARD, list.nav.value.motion)
        list.onButton(NxButton.B)
        advanceUntilIdle()
        assertIs<Screen.Systems>(list.screen.value)
        assertEquals(NavMotion.BACK, list.nav.value.motion)
    }

    // A Minimal tab steps as a List: UP / DOWN wrap on a fresh press, a held key stops at the ends; LEFT / RIGHT switch tab.
    @Test fun a_minimal_tab_wraps_as_a_list() = runTest {
        val state = minimalEverywhere(RecordingRunner())
        val tabs = assertIs<Screen.Systems>(state.screen.value).tabs
        state.selectTab(tabs.indexOfFirst { it.category == MenuCategory.CONSOLES })
        val count = assertIs<Screen.Systems>(state.screen.value).rows.size
        assertTrue(count > 1)
        state.select(count - 1)
        state.onButton(NxButton.DOWN, repeat = true)
        assertEquals(count - 1, state.selection.value.index)
        state.onButton(NxButton.DOWN)
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.UP)
        assertEquals(count - 1, state.selection.value.index)
        state.onButton(NxButton.RIGHT)
        assertTrue(assertIs<Screen.Systems>(state.screen.value).category != MenuCategory.CONSOLES)
    }

    // Minimal Home's open: A starts the band (homeOpening) and the row opens a moment later; input waits meanwhile.
    @Test fun minimal_home_opens_the_row_after_its_band() = runTest {
        val state = homeFixture(MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.MINIMAL) })
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        val tool = rows.indexOfFirst { it is MainRow.PinnedTool }
        state.select(tool)
        state.onButton(NxButton.A)
        assertEquals(tool, state.minimalOpening.value?.index)
        assertIs<Screen.Systems>(state.screen.value)   // not yet
        state.onButton(NxButton.UP)
        assertEquals(tool, state.selection.value.index)   // input waits
        advanceTimeBy(MINIMAL_OPEN_MS + 1)
        assertTrue(state.screen.value !is Screen.Systems)   // the tool opened
        advanceUntilIdle()
        assertEquals(null, state.minimalOpening.value?.index)
    }

    @Test fun switching_home_layout_keeps_the_focused_game() = runTest {
        // A pinned game, and a pinned tool: a top square on Grid, a row after the games and shelves on List, so its
        // index differs between the two and only finding it by row (homeFind) keeps it.
        for (focus in listOf<(MainRow) -> Boolean>({ it is MainRow.Pin }, { it is MainRow.PinnedTool })) {
            val settings = MemorySettings(ContentRef("Home"))
            val state = homeFixture(settings)
            val s = assertIs<Screen.Systems>(state.screen.value)
            val at = s.rows.indexOfFirst(focus)
            state.select(at)
            val focused = s.rows[at]
            state.openSettings(SettingsSection.MAIN_MENU)
            state.selectLayoutsRow(SettingsKind.MENU_STYLE, MenuCategory.HOME); state.onButton(NxButton.LEFT)   // Home layout: Grid -> List
            state.onButton(NxButton.B); state.onButton(NxButton.B); state.onButton(NxButton.B)                // back to Home
            val now = assertIs<Screen.Systems>(state.screen.value)
            assertEquals(MenuStyle.LIST, state.styleOf(now))
            if (focused is MainRow.PinnedTool) assertNotEquals(at, now.rows.indexOf(focused))   // the index moved
            assertEquals(focused, now.rows[state.selection.value.index])
        }
    }

    @Test fun list_home_shows_shelves_on_any_frame() = runTest {
        val state = homeFixture(MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.LIST) })
        advanceUntilIdle()   // the game info read: shelves wait for it (gameInfoRead)
        // The fixture library has games never played (not Tetris, not Mario): Not started holds them on the stand-in frame.
        assertTrue(assertIs<Screen.Systems>(state.screen.value).rows.any { it is MainRow.Shelf && it.shelf == HomeShelf.NOT_STARTED })
    }

    @Test fun a_tool_row_pins_and_unpins_into_the_private_file_never_shortcuts() = runTest {
        val files = MemoryPrivateFiles()
        val data = FakeUserData()
        val state = toolPinState(files, data)
        state.load(); advanceUntilIdle()
        state.openTools()
        assertEquals(ToolKind.CHEAT_DB, state.screen.value.toolRows()[0].kind)
        state.onButton(NxButton.MENU)
        val menu = assertIs<Screen.Systems>(state.screen.value).menu!!
        assertEquals(listOf(ContextItem(ContextKind.PIN_TOOL, "Pin")), menu.items)
        assertEquals("Cheat Database", menu.tool?.label)
        state.onButton(NxButton.A)                                       // Unconfined: the write and rebuild run now
        assertEquals("Pinned", state.notice.value)                      // before its auto-clear
        assertEquals(listOf("CHEAT_DB"), state.toolPins)
        assertEquals("CHEAT_DB", files.files[ToolPins.FILE])
        assertEquals(setOf(ToolPins.FILE), files.files.keys.filter { !it.endsWith(".json") }.toSet())   // no shortcuts.txt beside it
        assertEquals(emptyList(), data.pins)                                                           // and no game pin written
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)

        state.openContextMenu(1)                                         // a long press on Settings
        assertEquals(1, state.selection.value.index)
        assertEquals(listOf(ContextKind.PIN_TOOL), menuKinds(state))
        state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals("CHEAT_DB\nSETTINGS", files.files[ToolPins.FILE])

        state.select(0)
        assertEquals(listOf(ContextKind.UNPIN_TOOL), run { state.onButton(NxButton.MENU); menuKinds(state) })
        assertEquals("Unpin", assertIs<Screen.Systems>(state.screen.value).menu!!.items[0].label)
        state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals(listOf("SETTINGS"), state.toolPins)
        assertEquals("SETTINGS", files.files[ToolPins.FILE])
        assertEquals(emptyList(), data.pins)
    }

    @Test fun tool_pins_load_from_the_private_file_and_a_bad_read_is_empty() = runTest {
        val files = MemoryPrivateFiles().apply { this.files[ToolPins.FILE] = "SETTINGS\nBOGUS\nSETTINGS\n" }
        val state = toolPinState(files)
        state.load(); advanceUntilIdle()
        assertEquals(listOf("SETTINGS"), state.toolPins)
        state.openTools(); state.select(1)
        state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.UNPIN_TOOL), menuKinds(state))

        val broken = object : PrivateFiles {
            override suspend fun read(name: String): String? = throw IllegalStateException("unreadable")
            override suspend fun write(name: String, text: String) = throw IllegalStateException("read-only")
        }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val st = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), FakeUserData(), cheatDb = cheatDb(FakeCheatDbPlatform(), scope),
            privateFiles = broken, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        st.load(); advanceUntilIdle()
        assertEquals(emptyList(), st.toolPins)
        st.openTools()
        choose(st, ContextKind.PIN_TOOL); advanceUntilIdle()
        assertEquals(emptyList(), st.toolPins)                            // the write failed: nothing pinned
        assertEquals("Could not save the pin", st.error.value)
        assertNull(assertIs<Screen.Systems>(st.screen.value).menu)
    }

    @Test fun tools_tab_follows_the_consoles_and_lists_the_tools() = runTest {
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val main = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.TOOLS), main.tabs.map { it.category })
        state.onConsoles()
        assertEquals(main.systems.size, state.selection.value.count)   // the Consoles tab: one row per console
        state.onButton(NxButton.R1)
        assertEquals(listOf(ToolRow(ToolKind.CHEAT_DB, "Cheat Database", null), ToolRow(ToolKind.SETTINGS, "Settings", null)), state.screen.value.toolRows())
        assertEquals(ListSelection(0, 2), state.selection.value)
        state.onButton(NxButton.R1)                                    // wraps back to Home
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun grid_tab_moves_by_tiles_and_switches_tab_past_the_ends() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        state.onGridLayout(3)
        assertEquals(3, state.selection.value.count)         // NDS, GB, GBA (a still grid, one row of 3)
        state.onButton(NxButton.RIGHT); assertEquals(1, state.selection.value.index); state.onButton(NxButton.LEFT)
        state.onButton(NxButton.LEFT)                        // past the first console: the previous tab (Home)
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun grid_right_past_the_last_tile_switches_to_the_next_tab() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        state.onGridLayout(3)
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.RIGHT)
        assertEquals(2, state.selection.value.index)         // the last tile
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.RIGHT)                       // past it: the next tab
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun grid_down_steps_a_row() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        state.onGridLayout(2)                                // 3 consoles in 2 columns: a still grid of two rows
        state.onButton(NxButton.DOWN)
        assertEquals(2, state.selection.value.index)         // index + columns
    }

    @Test fun grid_steps_by_the_reported_rows() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        state.onGridLayout(1, rows = 3)                      // 3 consoles, 1 column of 3 rows (a tablet): a still grid
        state.onButton(NxButton.RIGHT)                       // row order: the next tile, not the next column
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.UP)                          // row 1 -> row 0
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.UP)                          // the top row: the tab row
        assertTrue(state.tabFocus.value)
        state.onButton(NxButton.UP)                          // wraps to the bottom row
        assertEquals(2, state.selection.value.index)
    }

    // The tall portrait's two columns of three rows: 3 consoles fit, so the grid is still, row by row.
    @Test fun tall_grid_of_two_columns_steps_rows_and_wraps() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        state.onGridLayout(2, 3)
        for (next in listOf(2, 0)) {                         // DOWN a row, wrapping in the column
            state.onButton(NxButton.DOWN)
            assertEquals(next, state.selection.value.index)
        }
        state.onButton(NxButton.RIGHT)
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.UP)                          // the top row: the tab row
        assertTrue(state.tabFocus.value)
    }

    @Test fun every_grid_dpad_press_counts_even_one_that_moves_nothing() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        state.onButton(NxButton.DOWN)                        // a List tab: not a Grid press
        assertEquals(0, state.gridPresses.value)
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        state.onGridLayout(3)                                // 3 consoles, one row of 3: DOWN moves nothing
        val before = state.selection.value.index
        state.onButton(NxButton.DOWN)
        assertEquals(before, state.selection.value.index)
        assertEquals(1, state.gridPresses.value)             // still a press: the Grid brings its selection back
        state.onButton(NxButton.RIGHT, repeat = true)
        assertEquals(2, state.gridPresses.value)
    }

    @Test fun held_right_on_a_list_tab_does_not_cycle_tabs() = runTest {
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.onConsoles()
        state.onButton(NxButton.RIGHT, repeat = true)        // a key repeat: no tab switch
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.LEFT, repeat = true)
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.RIGHT)                       // a fresh press switches
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun held_right_on_a_grid_stops_at_the_last_tile_then_a_fresh_press_switches() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        state.onGridLayout(1)                                // 3 consoles, 1 column: a sliding grid
        state.onButton(NxButton.RIGHT)                       // fresh press: next column
        repeat(5) {                                          // held: stops at the end, never leaves the tab
            state.onButton(NxButton.RIGHT, repeat = true)
            assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
            assertEquals(2, state.selection.value.index)
        }
        state.onButton(NxButton.RIGHT)                       // a fresh press past the end: the next tab
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun held_down_repeats_within_a_grid() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        state.onGridLayout(2)
        state.onButton(NxButton.DOWN, repeat = true)         // repeats step as usual
        assertEquals(2, state.selection.value.index)
    }

    @Test fun style_of_follows_the_screen() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        settings.gameListStyle = MenuStyle.GRID
        state.openSelected()
        assertEquals(settings.gameListStyle, state.styleOf(assertIs<Screen.Games>(state.screen.value)))
        settings.menuStyles = MenuCategory.entries.associateWith { MenuStyle.GRID }
        assertEquals(MenuStyle.LIST, state.styleOf(Screen.Systems(emptyList())))   // no category: List
    }

    @Test fun list_tab_left_right_switch_tabs_up_wraps_to_the_bottom() = runTest {
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.onConsoles()
        state.onButton(NxButton.RIGHT)                       // List: RIGHT is the next tab
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.UP)                          // the bottom of Tools (not through the tab row)
        assertEquals(state.selection.value.count - 1, state.selection.value.index)
        state.onButton(NxButton.LEFT)                        // back to Consoles, its remembered row
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun grid_columns_do_not_leak_to_another_tab() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)   // Tools stays List
        state.onGridLayout(3)
        state.onButton(NxButton.R1)                          // Tools: a List tab
        state.onButton(NxButton.DOWN)
        assertEquals(1, state.selection.value.index)         // List step, not a grid row of 3
        state.onButton(NxButton.L1)                          // back to Consoles: columns reset until the grid reports again
        state.onButton(NxButton.DOWN)                        // not laid out: List step, not a grid row
        assertEquals(minOf(1, state.selection.value.count - 1), state.selection.value.index)
    }

    @Test fun home_up_from_the_top_reaches_the_tab_row_not_the_bottom() = runTest {
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.onGridLayout(3)                                // ignored: Home is no Grid
        state.onButton(NxButton.UP)
        assertEquals(0, state.selection.value.index)
        assertTrue(state.tabFocus.value)
    }

    private fun AppState.category() = assertIs<Screen.Systems>(screen.value).category

    @Test fun carousel_home_held_keys_never_switch_tabs_or_reach_the_tab_row() = runTest {
        val state = homeFixture(MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.CAROUSEL) })
        state.onButton(NxButton.LEFT, repeat = true); assertEquals(MenuCategory.HOME, state.category())
        state.onButton(NxButton.UP, repeat = true); assertFalse(state.tabFocus.value)
        state.onButton(NxButton.UP); assertTrue(state.tabFocus.value)
        state.onButton(NxButton.UP)                                      // tab row UP: the dock (it has tools)
        assertIs<MainRow.PinnedTool>(assertIs<Screen.Systems>(state.screen.value).rows[state.selection.value.index])
    }

    @Test fun carousel_home_down_reaches_the_dock_and_a_on_more_opens_tools() = runTest {
        val state = homeFixture(MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.CAROUSEL) })
        state.onHomeLayout(HomeFrame(16f * 2 + 40f, 600f, portrait = true))   // room for one dock square
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        assertIs<MainRow.MoreTools>(rows.last())
        state.onButton(NxButton.DOWN)
        assertEquals(rows.lastIndex, state.selection.value.index)
        state.onButton(NxButton.A)
        assertEquals(MenuCategory.TOOLS, state.category())
    }

    @Test fun list_and_carousel_home_taps_select_then_open_a_pinned_game() = runTest {
        for (style in listOf(MenuStyle.LIST, MenuStyle.CAROUSEL)) {
            val runner = RecordingRunner()
            val state = homeFixture(MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to style) }, runner)
            val rows = assertIs<Screen.Systems>(state.screen.value).rows
            val pin = rows.indexOfFirst { it is MainRow.Pin }
            val opens = !mainRowTakesTwoTaps(rows[pin])
            assertFalse(opens)
            tap(state, pin, opens)                                       // a pinned game: the first tap selects
            assertEquals(pin, state.selection.value.index); assertTrue(runner.launched.isEmpty())
            tap(state, pin, opens)                                       // the second plays it
            assertEquals(1, runner.launched.size)
        }
    }

    @Test fun list_home_up_wraps_and_never_reaches_the_tab_row() = runTest {
        val state = homeFixture(MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.LIST) })
        state.onButton(NxButton.UP)
        assertEquals(state.selection.value.count - 1, state.selection.value.index); assertFalse(state.tabFocus.value)
        state.onButton(NxButton.RIGHT); assertEquals(MenuCategory.CONSOLES, state.category())
    }

    private fun carouselHome() = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.CAROUSEL) }

    /** The row item the Carousel Home's carousel shows now ([homeCarouselShownRow] on AppState's row memory). */
    private fun AppState.carouselShown(): Int {
        val rows = assertIs<Screen.Systems>(screen.value).rows
        return homeCarouselShownRow(selection.value.index, homeCarouselRowCount(rows), homeCarouselRowLast.value)
    }

    @Test fun carousel_home_shows_the_tapped_row_item_above_the_dock_after_home_comes_back() = runTest {
        val state = homeFixture(carouselHome())
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        val pin = rows.indexOfFirst { it is MainRow.Pin }
        val dock = homeCarouselRowCount(rows)
        assertIs<MainRow.PinnedTool>(rows[dock])
        state.select(pin)                                                // a tap on Mario's card
        state.select(dock)                                               // a tap on a dock square
        assertEquals(pin, state.carouselShown())
        state.onButton(NxButton.R1); state.onButton(NxButton.L1)         // Home left and back
        assertEquals(MenuCategory.HOME, state.category())
        assertEquals(dock, state.selection.value.index)                  // still on the dock square
        assertEquals(pin, state.carouselShown())                         // the carousel still shows Mario
        state.onButton(NxButton.UP)                                      // and UP lands on it
        assertEquals(pin, state.selection.value.index)
    }

    @Test fun carousel_home_taps_and_a_tab_entry_update_the_row_memory() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = homeFixture(settings)                                // Grid
        val pin = assertIs<Screen.Systems>(state.screen.value).rows.indexOfFirst { it is MainRow.Pin }
        state.select(pin)
        settings.menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.CAROUSEL)
        state.onButton(NxButton.R1); state.onButton(NxButton.L1)         // Home entered as Carousel, on Mario
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        assertIs<MainRow.Pin>(rows[state.selection.value.index])
        assertEquals(state.selection.value.index, state.homeCarouselRowLast.value)   // the tab entry noted it
        state.select(0)                                                  // a tap on Continue
        assertEquals(0, state.homeCarouselRowLast.value)
        state.select(homeCarouselRowCount(rows))                         // a tap on the dock: the row memory stays
        assertEquals(0, state.homeCarouselRowLast.value)
        assertEquals(0, state.carouselShown())
    }

    @Test fun carousel_home_unpinning_the_last_row_card_keeps_the_focus_in_the_row() = runTest {
        // Every library game played (no Not started shelf) and two pinned: no shelf after the last pinned card.
        val boulder = "/Roms/Commodore 64 (C64)/Boulder Dash 2.chd"
        val data = FakeUserData(recents = listOf(Recent(tetris), Recent(mario), Recent(mk), Recent(boulder)), pins = listOf(Shortcut(mario, "Mario"), Shortcut(mk, "Mario Kart")))
        val state = homeFixture(carouselHome(), data = data)
        advanceUntilIdle()
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        assertTrue(rows.none { it is MainRow.Shelf }, "$rows")
        val last = homeCarouselRowCount(rows) - 1
        assertEquals(mk, assertIs<MainRow.Pin>(rows[last]).entry.game.path)
        state.select(last)
        choose(state, ContextKind.UNPIN); advanceUntilIdle()
        val now = assertIs<Screen.Systems>(state.screen.value).rows
        assertTrue(now.none { it is MainRow.Pin && it.entry.game.path == mk })
        assertTrue(state.selection.value.index < homeCarouselRowCount(now), "focus in the dock: ${now[state.selection.value.index]}")
        assertEquals(homeCarouselRowCount(now) - 1, state.selection.value.index)   // the card before it
    }

    @Test fun list_and_carousel_home_pinning_a_shelf_game_follows_it_to_its_pin() = runTest {
        for (style in listOf(MenuStyle.LIST, MenuStyle.CAROUSEL)) {
            val state = homeFixture(MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to style) })
            advanceUntilIdle()
            val shelf = assertIs<MainRow.Shelf>(state.screen.value.homeRows.last { it is MainRow.Shelf })
            state.selectHome { it == shelf }
            choose(state, ContextKind.PIN); advanceUntilIdle()
            val focused = assertIs<Screen.Systems>(state.screen.value).rows[state.selection.value.index]
            assertEquals(shelf.entry.game.path, assertIs<MainRow.Pin>(focused, "$style").entry.game.path)
        }
    }

    @Test fun list_and_carousel_home_moves_recompute_the_stats_on_a_new_day() = runTest {
        for (style in listOf(MenuStyle.LIST, MenuStyle.CAROUSEL)) {
            val day = 86_400_000L
            var clock = 100 * day + 15 * 3_600_000L
            val gameTime = HomeGameTime()
            val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.HOME to style) }
            val state = state(settings, data = FakeUserData(recents = listOf(Recent(mk)), pins = listOf(Shortcut(tetris, "Tetris"))), now = { clock }, gameTime = gameTime)
            state.load(); advanceUntilIdle()
            val move = if (style == MenuStyle.LIST) NxButton.DOWN else NxButton.RIGHT
            val back = if (style == MenuStyle.LIST) NxButton.UP else NxButton.LEFT
            val calls = gameTime.windowCalls
            state.onButton(move); state.onButton(back); advanceUntilIdle()
            assertEquals(calls, gameTime.windowCalls, "$style")          // the same day: nothing
            clock = 101 * day + 3_600_000L
            state.onButton(move); advanceUntilIdle()                     // a focus move on a new day
            assertEquals(calls + 1, gameTime.windowCalls, "$style")
        }
    }

    // --- List and stack wrap (a fresh press), Layouts -> Page title hidden ---

    @Test fun main_menu_list_wraps_on_a_fresh_press_and_a_held_key_stops_at_the_ends() = runTest {
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.onConsoles()
        val last = state.selection.value.count - 1
        state.onButton(NxButton.UP, repeat = true)                       // held at the first: stays
        assertEquals(0, state.selection.value.index); assertFalse(state.tabFocus.value)
        state.onButton(NxButton.UP)                                      // a fresh press: the last row, never the tab row
        assertEquals(last, state.selection.value.index); assertFalse(state.tabFocus.value)
        state.onButton(NxButton.DOWN, repeat = true)                     // held at the last: stays
        assertEquals(last, state.selection.value.index)
        state.onButton(NxButton.DOWN)                                    // a fresh press: the first
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.DOWN, repeat = true)                     // repeats still step inside the list
        assertEquals(1, state.selection.value.index)
    }

    @Test fun game_list_list_wraps_on_a_fresh_press_and_a_held_key_stops_at_the_ends() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = MenuStyle.LIST }
        val state = AppState(AppDeps(settings, gbHome("Dr. Mario.gb", "Tetris.gb", "Zelda.gb"), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onConsoles()
        state.openSelected()
        assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.UP, repeat = true); assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.UP); assertEquals(2, state.selection.value.index)
        state.onButton(NxButton.DOWN, repeat = true); assertEquals(2, state.selection.value.index)
        state.onButton(NxButton.DOWN); assertEquals(0, state.selection.value.index)
    }

    @Test fun vertical_stacks_wrap_on_a_fresh_press_and_a_held_key_stops_on_the_main_menu_and_in_game_lists() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL)
            menuOrientations = mapOf(MenuCategory.CONSOLES to MenuOrientation.VERTICAL)
        }
        val menu = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings)
        menu.onConsoles()
        menu.onButton(NxButton.UP, repeat = true); assertEquals(0, menu.selection.value.index)
        menu.onButton(NxButton.UP); assertEquals(2, menu.selection.value.index); assertFalse(menu.tabFocus.value)
        menu.onButton(NxButton.DOWN, repeat = true); assertEquals(2, menu.selection.value.index)
        menu.onButton(NxButton.DOWN); assertEquals(0, menu.selection.value.index)

        for (style in listOf(MenuStyle.CAROUSEL, MenuStyle.BACKDROP)) {
            val games = MemorySettings(ContentRef("Home")).apply { gameListStyle = style; gameListOrientation = MenuOrientation.VERTICAL }
            val list = AppState(AppDeps(games, gbHome("Dr. Mario.gb", "Tetris.gb", "Zelda.gb"), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            list.load(); list.onConsoles(); list.openSelected()
            assertIs<Screen.Games>(list.screen.value)
            list.onButton(NxButton.UP, repeat = true); assertEquals(0, list.selection.value.index, "$style")
            list.onButton(NxButton.UP); assertEquals(2, list.selection.value.index, "$style")
            list.onButton(NxButton.DOWN, repeat = true); assertEquals(2, list.selection.value.index, "$style")
            list.onButton(NxButton.DOWN); assertEquals(0, list.selection.value.index, "$style")
        }
    }

    @Test fun page_title_hidden_up_never_focuses_the_tab_row_and_l1_r1_and_edges_still_switch_tabs() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuPageTitle = false
            menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID, MenuCategory.TOOLS to MenuStyle.CAROUSEL)
        }
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings)
        state.onConsoles()
        state.onGridLayout(2)                                            // 0 1 / 2
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.UP)      // the Grid's top row: stays
        assertFalse(state.tabFocus.value); assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.R1)                                      // Tools: a Carousel row
        assertEquals(MenuCategory.TOOLS, state.category())
        state.onButton(NxButton.UP); assertFalse(state.tabFocus.value)
        state.onButton(NxButton.L1); assertEquals(MenuCategory.CONSOLES, state.category())
        state.onGridLayout(2)                                            // the Grid reports again
        state.onButton(NxButton.LEFT); state.onButton(NxButton.LEFT)     // past the Grid's start: the tab before
        assertEquals(MenuCategory.HOME, state.category())
        state.select(0); state.onButton(NxButton.UP)                     // Home's top: stays
        assertFalse(state.tabFocus.value)
    }

    @Test fun page_title_hidden_on_touch_only_landscape_keeps_the_tab_row() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuPageTitle = false; menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL)
        }
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings)
        state.onFooterMode(FooterMode.BUTTONS)                           // no controller, no pad: the tab row stays
        assertEquals(LauncherChrome(tabRow = true, listTitle = false, buttonHints = true, topIcons = true, noBar = true, netplayHint = false), state.launcherChrome())
        state.onConsoles()
        state.onButton(NxButton.UP)
        assertTrue(state.tabFocus.value)
        state.onFooterMode(FooterMode.HINTS)                             // a controller connects: the row hides, the content takes the d-pad
        assertFalse(state.tabFocus.value)
        assertEquals(LauncherChrome(tabRow = false, listTitle = false, buttonHints = true), state.launcherChrome())
        state.onButton(NxButton.UP); assertFalse(state.tabFocus.value)
    }

    @Test fun hiding_the_page_title_while_the_tab_row_has_the_d_pad_gives_it_back() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL) }
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings)
        state.onConsoles()
        state.onButton(NxButton.UP)
        assertTrue(state.tabFocus.value)
        settings.menuPageTitle = false                                   // changed behind the menu's back
        state.onButton(NxButton.RIGHT)                                   // the content's RIGHT, not the tab row's
        assertFalse(state.tabFocus.value)
        assertEquals(MenuCategory.CONSOLES, state.category()); assertEquals(1, state.selection.value.index)
    }

    /** The main menu on Consoles as a horizontal Carousel, whose UP reaches the tab row from any item (a List's wraps). */
    private fun TestScope.carouselConsoles(): AppState {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL) }
        return stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings).also { it.onConsoles() }
    }

    // The main menu's tab row (UP from a horizontal row, a Grid's top row or Home's top; a List or a stack
    // wraps instead): LEFT/RIGHT switch tabs and keep it, DOWN, A and B return to the content where it was, UP wraps to
    // the content's bottom.
    @Test fun row_up_focuses_the_tab_row_and_left_right_switch_tabs_there() = runTest {
        val state = carouselConsoles()
        state.onButton(NxButton.RIGHT)
        assertFalse(state.tabFocus.value)
        state.onButton(NxButton.UP)                          // the tab row
        assertTrue(state.tabFocus.value)
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.RIGHT)                       // the next tab, the tab row kept
        assertEquals(MenuCategory.TOOLS, state.category()); assertTrue(state.tabFocus.value)
        state.onButton(NxButton.RIGHT, repeat = true)        // a held key never switches
        assertEquals(MenuCategory.TOOLS, state.category())
        state.onButton(NxButton.LEFT)
        assertEquals(MenuCategory.CONSOLES, state.category()); assertTrue(state.tabFocus.value)
        state.onButton(NxButton.DOWN)                        // back to the content, on its item
        assertFalse(state.tabFocus.value)
        assertEquals(1, state.selection.value.index)
        assertEquals(MenuCategory.CONSOLES, state.category())
    }

    @Test fun a_and_b_on_the_tab_row_return_to_the_remembered_item_without_opening_or_leaving() = runTest {
        val state = carouselConsoles()
        state.onButton(NxButton.UP); state.onButton(NxButton.A)
        assertFalse(state.tabFocus.value)
        assertEquals(MenuCategory.CONSOLES, state.category())   // A opened nothing
        state.onButton(NxButton.UP); state.onButton(NxButton.B)
        assertFalse(state.tabFocus.value)
        assertEquals(MenuCategory.CONSOLES, state.category())
        state.onButton(NxButton.UP)
        assertTrue(state.back())                             // the system back, as B: to the content, not out of the app
        assertFalse(state.tabFocus.value)
        assertFalse(state.back())                            // on the content: the main menu does not handle it
        state.onButton(NxButton.UP)
        state.onButton(NxButton.MENU); state.onButton(NxButton.X); state.onButton(NxButton.Y)   // nothing on the tab row
        assertTrue(state.tabFocus.value)
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
    }

    @Test fun up_on_the_tab_row_wraps_to_a_list_bottom_and_a_held_up_stops_at_the_edges() = runTest {
        val state = carouselConsoles()
        state.onButton(NxButton.UP, repeat = true)           // held: stays in the row
        assertFalse(state.tabFocus.value); assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.UP)
        state.onButton(NxButton.UP, repeat = true)           // held on the tab row: stays there
        assertTrue(state.tabFocus.value)
        state.onButton(NxButton.RIGHT)                       // Tools, a List, on the tab row
        state.onButton(NxButton.UP)                          // a fresh press: the List's last row
        assertFalse(state.tabFocus.value)
        assertEquals(MenuCategory.TOOLS, state.category())
        assertEquals(state.selection.value.count - 1, state.selection.value.index)
    }

    @Test fun l1_r1_switch_tabs_from_the_content_and_from_the_tab_row() = runTest {
        val state = carouselConsoles()
        state.onButton(NxButton.R1)
        assertEquals(MenuCategory.TOOLS, state.category()); assertFalse(state.tabFocus.value)
        state.onButton(NxButton.L1)
        state.onButton(NxButton.UP); state.onButton(NxButton.R1)
        assertEquals(MenuCategory.TOOLS, state.category()); assertTrue(state.tabFocus.value)
        state.onButton(NxButton.L1)
        assertEquals(MenuCategory.CONSOLES, state.category()); assertTrue(state.tabFocus.value)
    }

    @Test fun touch_clears_the_tab_row_and_a_tab_tap_never_enters_it() = runTest {
        val state = carouselConsoles()                       // a tab tap
        assertFalse(state.tabFocus.value)
        state.onButton(NxButton.UP); state.select(1)         // a tap on an item
        assertFalse(state.tabFocus.value); assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.UP)
        assertTrue(state.tabFocus.value)
        state.openContextMenu(0)                             // a long press
        assertFalse(state.tabFocus.value)
    }

    // The main menu's hint bar sends its buttons through onButton: on the tab row it shows only SELECT Recent and A Open,
    // and a tap on them does what the button does there.
    @Test fun the_open_hint_on_the_tab_row_enters_the_content_and_recent_opens_the_switcher() = runTest {
        val state = carouselConsoles()
        state.onButton(NxButton.UP)
        assertTrue(state.tabFocus.value)
        state.onButton(NxButton.A)                           // the "A Open" hint: back to the content, nothing opened
        assertFalse(state.tabFocus.value)
        assertEquals(MenuCategory.CONSOLES, state.category()); assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.UP)
        state.onButton(NxButton.SELECT)                      // the "SELECT Recent" hint: the switcher
        assertFalse(state.tabFocus.value)
        assertIs<Screen.Switcher>(state.screen.value)
    }

    @Test fun leaving_the_main_menu_clears_the_tab_row() = runTest {
        val state = carouselConsoles()
        state.onButton(NxButton.UP); state.goHome()
        assertFalse(state.tabFocus.value)
        assertEquals(MenuCategory.HOME, state.category())
        state.onConsoles(); state.onButton(NxButton.UP)
        state.openSelected()                                 // a tap on the highlighted console: opens it
        assertIs<Screen.Games>(state.screen.value)
        assertFalse(state.tabFocus.value)
        state.onButton(NxButton.B)
        assertFalse(state.tabFocus.value)
        state.onButton(NxButton.UP); state.onButton(NxButton.SELECT)   // SELECT still opens the switcher
        assertFalse(state.tabFocus.value)
    }

    @Test fun game_lists_still_wrap_on_up_from_the_first_row() = runTest {
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), gbHome("Dr. Mario.gb", "Tetris.gb", "Zelda.gb"), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onConsoles()
        state.openSelected()
        assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.UP)
        assertEquals(2, state.selection.value.index)
        assertFalse(state.tabFocus.value)
    }

    @Test fun grid_up_from_the_top_row_reaches_the_tab_row_and_up_there_lands_on_the_bottom_row() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        state.onGridLayout(2)                                // 0 1 / 2
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.UP)
        assertTrue(state.tabFocus.value); assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.UP)                          // column 1 is empty below: the last tile
        assertFalse(state.tabFocus.value); assertEquals(2, state.selection.value.index)
        state.onButton(NxButton.UP); assertEquals(0, state.selection.value.index)   // the row above, no tab row
        assertFalse(state.tabFocus.value)
        state.onButton(NxButton.UP); state.onButton(NxButton.UP)   // the tab row, then the bottom of column 0
        assertEquals(2, state.selection.value.index)
    }

    @Test fun carousel_up_reaches_the_tab_row_from_any_item_and_up_there_comes_back() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL) }
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings)
        state.onConsoles()
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.DOWN)
        assertFalse(state.tabFocus.value)                    // DOWN in a row: nothing
        state.onButton(NxButton.UP)
        assertTrue(state.tabFocus.value); assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.UP)                          // one row: its bottom is the item it was on
        assertFalse(state.tabFocus.value); assertEquals(1, state.selection.value.index)
    }
    @Test fun home_up_from_the_top_reaches_the_tab_row_and_up_there_lands_on_the_last_pin() = runTest {
        val state = threeGamesTwoTools(); state.load(); advanceUntilIdle()
        state.onHomeLayout(PORT)                             // Pick a game, Cheat DB, Settings | Tetris, Mario | Mario Kart
        state.onButton(NxButton.DOWN); state.onButton(NxButton.UP)   // a pin, then the top: not the tab row yet
        assertFalse(state.tabFocus.value)
        state.onButton(NxButton.UP)
        assertTrue(state.tabFocus.value); assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.DOWN)                        // back on the item it was on
        assertFalse(state.tabFocus.value); assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.UP); state.onButton(NxButton.UP)   // the tab row, then the bottom: the last pin
        assertFalse(state.tabFocus.value); assertEquals("Mario Kart DS", state.homePin())
        state.onButton(NxButton.LEFT)                        // Home's own edges still switch tabs: past the row's start
        assertEquals(MenuCategory.TOOLS, state.category()); assertFalse(state.tabFocus.value)
        state.onButton(NxButton.R1); state.onButton(NxButton.UP)   // Home again, on its pin; UP from the second row
        assertFalse(state.tabFocus.value)
        state.select(0); state.onButton(NxButton.UP); state.onButton(NxButton.LEFT)   // the tab row: LEFT is the tab before
        assertEquals(MenuCategory.TOOLS, state.category()); assertTrue(state.tabFocus.value)
    }

    @Test fun game_list_left_right_never_switch_tabs() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings)
        state.onConsoles()
        state.openSelected()                                 // the first console's games
        val list = assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.LEFT); state.onButton(NxButton.RIGHT)   // List: no-ops
        assertSame(list, state.screen.value)
        settings.gameListStyle = MenuStyle.GRID
        state.onGridLayout(3)
        state.onButton(NxButton.LEFT); state.onButton(NxButton.RIGHT)   // Grid: stops at the ends
        assertSame(list, state.screen.value)
        assertEquals(0, state.selection.value.index)
    }

    @Test fun carousel_left_right_step_and_switch_tab_only_on_a_fresh_press() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL) }
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings)
        state.onConsoles()
        val count = state.selection.value.count
        assertEquals(3, count)                                  // the fixture's consoles, as in the grid tests
        state.onButton(NxButton.RIGHT); assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.UP); state.onButton(NxButton.DOWN)
        assertEquals(1, state.selection.value.index)            // UP is the tab row, DOWN back: the row keeps its item
        assertFalse(state.tabFocus.value)
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.RIGHT, repeat = true)
        assertEquals(2, state.selection.value.index)            // held at the end: stays
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.RIGHT)                          // fresh press past the end: next tab
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun backdrop_game_list_stops_at_the_ends() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = MenuStyle.BACKDROP }
        // The shared fixture has one game per console; a GB home with three games gives the row somewhere to go.
        val state = AppState(AppDeps(settings, gbHome("Dr. Mario.gb", "Tetris.gb", "Zelda.gb"), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onConsoles()
        state.openSelected()                                    // the GB games
        val list = assertIs<Screen.Games>(state.screen.value)
        val count = state.selection.value.count
        assertEquals(3, count)
        state.onButton(NxButton.LEFT)                           // before the first: stays
        assertEquals(0, state.selection.value.index)
        repeat(count - 1) { state.onButton(NxButton.RIGHT) }
        assertEquals(count - 1, state.selection.value.index)    // the last
        state.onButton(NxButton.UP); state.onButton(NxButton.DOWN)
        assertEquals(count - 1, state.selection.value.index)    // UP/DOWN do nothing in a row (no List wrap)
        state.onButton(NxButton.RIGHT)                          // past the last: stays, no tab switch
        assertEquals(count - 1, state.selection.value.index)
        assertSame(list, state.screen.value)
    }

    @Test fun grid_then_carousel_tab_steps_by_one_and_both_keep_their_rows() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID, MenuCategory.TOOLS to MenuStyle.CAROUSEL)
        }
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings)
        state.onConsoles()
        state.onGridLayout(3)                                   // Consoles: a still grid, one row of 3
        state.onButton(NxButton.RIGHT); assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.R1)                             // Tools: a Carousel tab
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
        assertEquals(ListSelection(0, 2), state.selection.value)
        state.onButton(NxButton.RIGHT)                          // a row step of 1, not the grid's 3 columns
        assertEquals(1, state.selection.value.index)
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.UP)                             // UP in a row: the tab row, the item kept
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.L1)                             // back to the Grid tab: its row kept
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.R1)                             // and Tools kept its row
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
        assertEquals(1, state.selection.value.index)
    }

    @Test fun one_item_carousel_tab_switches_only_on_a_fresh_press() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + mapOf(MenuCategory.COLLECTIONS to MenuStyle.CAROUSEL) }
        // The Co-op library gives a Collections tab with exactly one row.
        val state = state(settings, src = twoConsoles(coOp = true)).also { it.load() }
        state.openCollectionsTab()
        assertEquals(ListSelection(0, 1), state.selection.value)
        state.onButton(NxButton.RIGHT, repeat = true)           // held: stays put
        assertEquals(MenuCategory.COLLECTIONS, assertIs<Screen.Systems>(state.screen.value).category)
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.RIGHT)                          // a fresh press: the next tab
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun one_game_carousel_game_list_stays_put() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = MenuStyle.CAROUSEL }
        val state = AppState(AppDeps(settings, gbHome("Tetris.gb"), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onConsoles()
        state.openSelected()                                    // the GB games: one
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals(ListSelection(0, 1), state.selection.value)
        state.onButton(NxButton.RIGHT)
        assertEquals(0, state.selection.value.index)
        assertSame(list, state.screen.value)
        state.onButton(NxButton.RIGHT, repeat = true); state.onButton(NxButton.LEFT)
        assertEquals(0, state.selection.value.index)
        assertSame(list, state.screen.value)
    }

    @Test fun vertical_carousel_tab_steps_up_down_wraps_and_switches_tab_on_left_right() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL)
            menuOrientations = mapOf(MenuCategory.CONSOLES to MenuOrientation.VERTICAL)
        }
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings)
        state.onConsoles()
        assertEquals(3, state.selection.value.count)
        state.onButton(NxButton.DOWN); assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.DOWN); assertEquals(2, state.selection.value.index)
        state.onButton(NxButton.DOWN, repeat = true); assertEquals(2, state.selection.value.index)   // held at the last: stops
        state.onButton(NxButton.DOWN); assertEquals(0, state.selection.value.index)                  // a fresh press: wraps to the first
        state.onButton(NxButton.UP, repeat = true)                                                   // held at the first: stays
        assertEquals(0, state.selection.value.index); assertFalse(state.tabFocus.value)
        state.onButton(NxButton.UP)                                                                  // a fresh press: the last, never the tab row
        assertFalse(state.tabFocus.value); assertEquals(2, state.selection.value.index)
        state.onButton(NxButton.UP)                                                                  // a step
        assertFalse(state.tabFocus.value); assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.RIGHT, repeat = true)                                                // held: no tab switch, no step
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.RIGHT)                                                               // a fresh press: the next tab
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.L1)
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.LEFT)                                                                // a fresh LEFT: the tab before
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun list_game_list_keeps_its_up_wrap_with_a_stored_vertical() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = MenuStyle.LIST; gameListOrientation = MenuOrientation.VERTICAL }
        val state = AppState(AppDeps(settings, gbHome("Dr. Mario.gb", "Tetris.gb", "Zelda.gb"), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onConsoles()
        state.openSelected()                                    // the GB games
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals(3, state.selection.value.count)
        assertEquals(MenuOrientation.HORIZONTAL, state.orientationOf(list))
        state.onButton(NxButton.UP)                             // List: UP on the first wraps to the last
        assertEquals(2, state.selection.value.index)
        assertSame(list, state.screen.value)
    }

    @Test fun vertical_game_lists_wrap_on_a_fresh_press_and_ignore_left_right() = runTest {
        for (style in listOf(MenuStyle.BACKDROP, MenuStyle.CAROUSEL)) {
            val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = style; gameListOrientation = MenuOrientation.VERTICAL }
            val state = AppState(AppDeps(settings, gbHome("Dr. Mario.gb", "Tetris.gb", "Zelda.gb"), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            state.load()
            state.onConsoles()
            state.openSelected()                                    // the GB games
            val list = assertIs<Screen.Games>(state.screen.value)
            assertEquals(3, state.selection.value.count)
            state.onButton(NxButton.UP, repeat = true)              // held at the first: stays (no tab row here)
            assertEquals(0, state.selection.value.index); assertFalse(state.tabFocus.value); assertSame(list, state.screen.value)
            state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN, repeat = true)
            assertEquals(2, state.selection.value.index, "$style")  // the last: a held key stops
            state.onButton(NxButton.LEFT); state.onButton(NxButton.RIGHT)
            assertEquals(2, state.selection.value.index)            // LEFT / RIGHT do nothing
            assertSame(list, state.screen.value)
        }
        // A Grid ignores a stored Vertical: RIGHT steps as the Grid's.
        val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = MenuStyle.GRID; gameListOrientation = MenuOrientation.VERTICAL }
        val state = AppState(AppDeps(settings, gbHome("Dr. Mario.gb", "Tetris.gb", "Zelda.gb"), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onConsoles()
        state.openSelected()
        state.onGridLayout(3)
        state.onButton(NxButton.RIGHT)
        assertEquals(1, state.selection.value.index)
    }

    @Test fun without_a_controller_the_tool_opens_nothing() = runTest {
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.openTools()
        assertNull(state.screen.value.toolRows()[0].secondary)
        state.openSelected()
        state.screen.value.toolRows()   // A does nothing: still on the Tools tab
    }

    @Test fun tools_page_state_follows_the_controller() = runTest {
        val fake = FakeCheatDbPlatform().apply { downloadGate = CompletableDeferred() }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = stateWithTools(fake, scope)
        state.openTools()
        state.cheatDb!!.download()
        assertIs<CheatDbState.Downloading>(state.cheatDb!!.state.value)
        fake.downloadGate!!.complete(Unit)
        assertIs<CheatDbState.Installed>(state.cheatDb!!.state.value)
        assertEquals("Cheat database installed: 1 files for 1 systems", state.notice.value)
        state.onButton(NxButton.DOWN)
        assertNull(state.notice.value)   // the next button clears the pill
    }

    @Test fun cheat_db_page_actions_dispatch_to_the_controller() = runTest {
        val fake = FakeCheatDbPlatform()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = stateWithTools(fake, scope)
        state.openTools()
        state.openSelected()                                          // Cheat Database
        assertEquals(Screen.CheatDb(), state.screen.value)
        assertEquals(ListSelection(0, 1), state.selection.value)   // one row: Download
        state.onButton(NxButton.A)                                    // Download
        assertIs<CheatDbState.Installed>(state.cheatDb!!.state.value)
        assertEquals(ListSelection(0, 2), state.selection.value)   // rows re-counted after the state change
        state.onButton(NxButton.DOWN)                                 // Remove
        state.onButton(NxButton.A)
        assertEquals(Screen.CheatDb(confirmRemove = true), state.screen.value)
        state.onButton(NxButton.B)
        assertEquals(Screen.CheatDb(), state.screen.value)           // B closes the confirm first
        state.onButton(NxButton.A); state.onButton(NxButton.A)        // confirm
        assertEquals(CheatDbState.NotInstalled, state.cheatDb!!.state.value)
        assertEquals("Cheat database removed", state.notice.value)
        state.onButton(NxButton.B)
        state.screen.value.toolRows()
    }

    @Test fun b_while_busy_cancels_and_stays() = runTest {
        val fake = FakeCheatDbPlatform().apply { downloadGate = CompletableDeferred() }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = stateWithTools(fake, scope)
        state.openTools(); state.openSelected()
        state.onButton(NxButton.A)
        assertIs<CheatDbState.Downloading>(state.cheatDb!!.state.value)
        state.onButton(NxButton.B)
        assertEquals(CheatDbState.NotInstalled, state.cheatDb!!.state.value)
        assertEquals(Screen.CheatDb(), state.screen.value)
    }

    @Test fun a_failure_shows_the_problem_dialog() = runTest {
        val fake = FakeCheatDbPlatform().apply { downloadError = CheatDbException(CheatDbException.Kind.NETWORK) }
        val state = stateWithTools(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.openTools(); state.openSelected()
        state.onButton(NxButton.A)
        assertEquals("Download failed (check Wi-Fi)", state.error.value)
        state.onButton(NxButton.B)   // dismisses the dialog only
        assertNull(state.error.value)
        assertEquals(Screen.CheatDb(), state.screen.value)
    }
    @Test fun the_main_menu_ends_with_tools_and_has_no_settings_row() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        val main = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.TOOLS), main.tabs.map { it.category })   // Settings is a tool, not a tab
        state.onConsoles()
        val systems = main.systems
        assertEquals(systems.size, state.selection.value.count)
        state.select(systems.size + 1)                                  // past the end: clamped to the last console
        assertEquals(systems.size - 1, state.selection.value.index)
        state.onButton(NxButton.L1); state.onButton(NxButton.L1)        // wraps to the last tab
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
        state.screen.value.toolRows()
    }

    @Test fun tools_settings_opens_the_section_menu_and_b_returns_to_the_settings_row() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openTools()
        assertEquals(listOf("Cheat Database", "Settings"), state.screen.value.toolRows().map { it.label })
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)
        val menu = assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(listOf(SettingsSection.LIBRARY, SettingsSection.EMULATORS, SettingsSection.APPEARANCE, SettingsSection.MAIN_MENU, SettingsSection.CONTROLS, SettingsSection.SYSTEM), menu.sections)
        assertEquals(listOf("Library", "Emulators", "Appearance", "Layouts", "Controls", "System"), menu.sections.map { it.label })
        assertEquals(ListSelection(0, 6), state.selection.value)
        state.onButton(NxButton.B)
        state.screen.value.toolRows()
        assertEquals(ListSelection(1, 2), state.selection.value)       // Settings stays highlighted
    }

    @Test fun each_section_opens_its_rows_and_b_returns_to_the_menu_on_that_section() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openTools()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)       // Settings menu
        state.onButton(NxButton.A)                                      // Library
        val lib = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.LIBRARY, lib.section)
        assertEquals(listOf("3 games · 3 consoles", "Home folder"), lib.rows.take(2).map { it.label })
        state.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)       // Emulators
        val emu = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.EMULATORS, emu.section)
        assertEquals(listOf("Default emulators", "Settings"), emu.rows.map { it.label })   // Saves under Library, Pause when unfocused under System
        state.onButton(NxButton.B)
        assertEquals(ListSelection(1, 6), state.selection.value)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)       // Appearance
        assertEquals(SettingsSection.APPEARANCE, assertIs<Screen.Settings>(state.screen.value).section)
        state.onButton(NxButton.B)
        assertEquals(ListSelection(2, 6), state.selection.value)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)       // Layouts
        assertEquals(SettingsSection.MAIN_MENU, assertIs<Screen.Settings>(state.screen.value).section)
        state.onButton(NxButton.B)
        assertEquals(ListSelection(3, 6), state.selection.value)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)       // Controls
        assertEquals(SettingsSection.CONTROLS, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(listOf("Pad vibration", "Game rumble", "Menu pad", "Pad layout", "Reset to defaults"), assertIs<Screen.Settings>(state.screen.value).rows.map { it.label })
        state.onButton(NxButton.B)
        assertEquals(ListSelection(4, 6), state.selection.value)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)       // System, the last
        val sys = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.SYSTEM, sys.section)
        assertEquals(listOf("General", "Developer", "About", "Licenses"), sys.rows.filter { it.kind == SettingsKind.SECTION }.map { it.label })   // no launcher: no Launcher group
        state.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(ListSelection(5, 6), state.selection.value)
    }

    // --- Settings -> Library: one page of Storage, ROM folders and Games groups; Default emulators under Emulators ---

    @Test fun the_library_page_shows_its_counts_then_one_flat_list_counting_a_merged_console_once() = runTest {
        val st = state(MemorySettings(ContentRef("Home")), snes)
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        val s = assertIs<Screen.Settings>(st.screen.value)
        assertTrue(s.details.isEmpty())                                                            // the counts are the first row now
        assertEquals(
            listOf(
                SettingsRow(SettingsKind.RESCAN, "7 games · 2 consoles"),                           // SFC + SUPA are one console; no clock, no Last scan
                SettingsRow(SettingsKind.HOME, "Home folder", "Home"),                              // no group labels or gaps
                SettingsRow(SettingsKind.ADD_EXTRA, "Add ROM folder"),
                SettingsRow(SettingsKind.SAVES, "Saves"),                                           // no Unassigned or Hidden game: no rows for them
            ),
            s.rows,
        )
        assertEquals(0, st.selection.value.index)                                                  // on the counts row
    }

    @Test fun the_library_page_adds_unassigned_then_hidden_only_when_they_hold_games() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val st = state(settings, data = FakeUserData(hidden = listOf(mario)))
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        val s = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(
            listOf(SettingsKind.RESCAN, SettingsKind.HOME, SettingsKind.EXTRA, SettingsKind.ADD_EXTRA, SettingsKind.SAVES, SettingsKind.UNASSIGNED, SettingsKind.HIDDEN),
            selectableKinds(st),
        )
        assertEquals(SettingsRow(SettingsKind.UNASSIGNED, "Unassigned games", "2 games"), libraryRow(st, SettingsKind.UNASSIGNED))
        assertEquals(SettingsRow(SettingsKind.HIDDEN, "Hidden games", "1 game"), libraryRow(st, SettingsKind.HIDDEN))
        assertEquals(SettingsRow(SettingsKind.EXTRA, "ROM Folder: Loose", "ROM folder", extra = settings.extraFolders.single()), libraryRow(st, SettingsKind.EXTRA))
        assertEquals(rowIndex(st, SettingsKind.HOME) + 1, s.rows.indexOfFirst { it.kind == SettingsKind.EXTRA })   // right under the home, above Add ROM folder
    }

    @Test fun the_library_page_without_a_home_says_so() = runTest {
        val st = state(MemorySettings())
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals(SettingsRow(SettingsKind.RESCAN, "0 games · 0 consoles"), assertIs<Screen.Settings>(st.screen.value).rows[0])
        assertEquals(SettingsRow(SettingsKind.HOME, "Home folder", "No home folder"), libraryRow(st, SettingsKind.HOME))
    }

    @Test fun library_rows_show_a_file_folders_path() = runTest {
        val settings = MemorySettings(fileHome)
        val st = state(settings, fileSource(), allFiles = FakeAllFilesAccess(isGranted = true), localFiles = localFiles())
        st.load()
        st.addExtra(FileRefs.of("/storage/emulated/0/Loose"))
        st.openSettings(SettingsSection.LIBRARY)
        assertEquals("/storage/emulated/0/NXRedux", libraryRow(st, SettingsKind.HOME).secondary)
        assertEquals(SettingsRow(SettingsKind.EXTRA, "ROM Folder: Loose", "/storage/emulated/0/Loose", extra = settings.extraFolders.single()), libraryRow(st, SettingsKind.EXTRA))
        assertEquals("On", libraryRow(st, SettingsKind.ALL_FILES).secondary)
    }

    @Test fun library_up_and_down_walk_its_flat_list_and_wrap() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val st = state(MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) })
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        val rows = assertIs<Screen.Settings>(st.screen.value).rows
        assertEquals(SettingsKind.RESCAN, rows[st.selection.value.index].kind)                    // starts on the counts row
        val seen = List(7) { st.onButton(NxButton.DOWN); rows[st.selection.value.index].kind }
        assertEquals(listOf(SettingsKind.HOME, SettingsKind.EXTRA, SettingsKind.ADD_EXTRA, SettingsKind.SAVES, SettingsKind.UNASSIGNED, SettingsKind.RESCAN, SettingsKind.HOME), seen)   // row by row; wraps to the top
        st.onButton(NxButton.UP); st.onButton(NxButton.UP)
        assertEquals(SettingsKind.UNASSIGNED, rows[st.selection.value.index].kind)
    }

    @Test fun the_all_files_switch_opens_androids_page_and_follows_the_grant() = runTest {
        val access = FakeAllFilesAccess(declared = true, isGranted = false)
        val st = state(MemorySettings(ContentRef("Home")), allFiles = access)
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        val at = rowIndex(st, SettingsKind.ALL_FILES)
        val page = assertIs<Screen.Settings>(st.screen.value)
        val touch = settingsTouchRow(page.rows[at], page.choices[at], page.section)
        assertEquals(NxSettingControl.SWITCH, touch.control)
        assertEquals(0, touch.index)                                                               // Off
        assertEquals("", touch.info)                                                               // a line only for a warning
        assertEquals("Lets cores and saves use your home folder's real path", touch.detail)
        st.chooseSetting(at, 1)                                                                    // the switch flipped by a tap
        assertEquals(1, access.opened)
        assertEquals(at, st.selection.value.index)
        st.onButton(NxButton.A)                                                                    // and by A
        assertEquals(2, access.opened)
        access.isGranted = true
        st.onAllFilesAccessChanged()
        val after = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(1, after.choices[at]?.index)                                                  // On
        assertEquals(at, st.selection.value.index)
    }

    @Test fun library_touch_rows_describe_with_their_state_and_mark_a_problem() = runTest {
        val home = settingsTouchRow(SettingsRow(SettingsKind.HOME, "Home folder", "Not available", warning = true), null, SettingsSection.LIBRARY)
        assertEquals("Not available", home.info)
        assertTrue(home.warning)
        assertTrue(home.opens)
        assertNull(home.value)
        val hidden = settingsTouchRow(SettingsRow(SettingsKind.HIDDEN, "Hidden games", "2 games"), null, SettingsSection.LIBRARY)
        assertEquals("2 games", hidden.info)
        assertTrue(hidden.opens)
        assertFalse(hidden.warning)
        assertTrue(settingsTouchRow(SettingsRow(SettingsKind.SECTION, "Games"), null).section)
        assertTrue(settingsTouchRow(SettingsRow(SettingsKind.GAP, ""), null).spacer)
        assertTrue(settingsTouchPage(Screen.Settings(SettingsSection.LIBRARY, emptyList())))
    }

    @Test fun rescan_says_the_last_scan_and_the_page_gives_it_under_the_counts() = runTest {
        val clock = 1_000_000L
        val st = state(MemorySettings(ContentRef("Home")), now = { clock })
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        val s = assertIs<Screen.Settings>(st.screen.value)
        assertTrue(s.details.isEmpty())
        assertEquals(SettingsRow(SettingsKind.RESCAN, "3 games · 3 consoles", "Last scan: ${formatDateTime(clock)}"), s.rows[0])   // under the counts
    }

    @Test fun b_from_hidden_games_lands_on_its_library_row() = runTest {
        val st = state(MemorySettings(ContentRef("Home")), data = FakeUserData(hidden = listOf(mario)))
        st.load()
        val row = openLibraryOn(st, SettingsKind.HIDDEN)
        st.onButton(NxButton.A)
        assertEquals(ListSource.Hidden, assertIs<Screen.Games>(st.screen.value).source)
        st.onButton(NxButton.B)
        assertEquals(SettingsSection.LIBRARY, assertIs<Screen.Settings>(st.screen.value).section)
        assertEquals(row, st.selection.value.index)
        st.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(st.screen.value)
        assertEquals(0, st.selection.value.index)                                                  // B from Library: the menu on Library
    }

    @Test fun the_remove_dialog_over_library_keeps_the_extras_row() = runTest {
        val loose = ExtraFolder(Sources.idFor("Loose", ContentRef("sd/Loose")), ContentRef("sd/Loose"))
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(loose) }
        val st = state(settings)
        st.load()
        val row = openLibraryOn(st, SettingsKind.EXTRA)
        st.onButton(NxButton.A)
        assertEquals(loose, assertIs<Screen.Settings>(st.screen.value).confirmRemove)
        st.onButton(NxButton.B)                                                                    // keep it
        assertNull(assertIs<Screen.Settings>(st.screen.value).confirmRemove)
        assertEquals(row, st.selection.value.index)
        assertEquals(listOf(loose), settings.extraFolders)
    }

    @Test fun default_emulators_explains_itself_and_left_right_a_cycle_the_tag() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val st = state(settings)
        st.load()
        openLibraryOn(st, SettingsKind.DEFAULTS)
        st.onButton(NxButton.A)
        val d = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsSection.DEFAULT_EMULATORS, d.section)
        assertEquals(
            listOf("A game's own Emulator choice still wins. Saves and settings follow the tag."),
            d.details,
        )
        // Consoles with several emulators first, by console name; then the single-emulator ones (Game Boy, Nintendo DS, dimmed).
        assertEquals(
            listOf(
                SettingsRow(SettingsKind.DEFAULT_TAG, "Game Boy Advance", "gpSP (GBA)", consoleId = "gba"),
                SettingsRow(SettingsKind.DEFAULT_TAG, "Sega CD", "PicoDrive (SEGACD)", consoleId = "segacd"),
                SettingsRow(SettingsKind.DEFAULT_TAG, "Sega Game Gear", "PicoDrive (GG)", consoleId = "gg"),
                SettingsRow(SettingsKind.DEFAULT_TAG, "Sega Genesis", "PicoDrive (MD)", consoleId = "md"),
                SettingsRow(SettingsKind.DEFAULT_TAG, "Sega Master System", "PicoDrive (SMS)", consoleId = "sms"),
                SettingsRow(SettingsKind.DEFAULT_TAG, "Sega SG-1000", "PicoDrive (SG1000)", consoleId = "sg1000"),
                SettingsRow(SettingsKind.DEFAULT_TAG, "Sony PlayStation", "PCSX-ReARMed (PS)", consoleId = "ps"),
                SettingsRow(SettingsKind.DEFAULT_TAG, "Super Nintendo ES", "Snes9x (SFC)", consoleId = "sfc"),
                SettingsRow(SettingsKind.DEFAULT_TAG, "Game Boy", "Gambatte (GB)", consoleId = "gb", enabled = false),
                SettingsRow(SettingsKind.DEFAULT_TAG, "Nintendo DS", "melonDS DS (NDS)", consoleId = "nds", enabled = false),
            ),
            d.rows,
        )
        val value = { id: String -> assertIs<Screen.Settings>(st.screen.value).rows.first { it.consoleId == id }.secondary }
        val goTo = { id: String -> st.select(assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.consoleId == id }) }
        goTo("gb")
        st.onButton(NxButton.A); st.onButton(NxButton.RIGHT); st.onButton(NxButton.LEFT)   // Game Boy: one emulator, nothing moves
        assertEquals("Gambatte (GB)", value("gb"))
        assertEquals(emptyMap(), settings.defaultTags)
        goTo("gba")
        st.onButton(NxButton.RIGHT); assertEquals("mGBA (MGBA)", value("gba"))
        st.onButton(NxButton.LEFT); assertEquals("gpSP (GBA)", value("gba"))
        st.onButton(NxButton.A); st.onButton(NxButton.DOWN); st.onButton(NxButton.A); assertEquals("mGBA (MGBA)", value("gba"))   // A: the picker; DOWN, A pick the next
        assertEquals(mapOf("gba" to "MGBA"), settings.defaultTags)
        goTo("segacd")
        st.onButton(NxButton.RIGHT); assertEquals("Genesis Plus GX (GPGX)", value("segacd"))   // Sega CD: its GPGX_SEGACD, shown as GPGX
        assertEquals(mapOf("gba" to "MGBA", "segacd" to "GPGX_SEGACD"), settings.defaultTags)
        st.onButton(NxButton.LEFT); assertEquals("PicoDrive (SEGACD)", value("segacd"))
        goTo("md")
        st.onButton(NxButton.RIGHT); assertEquals("Genesis Plus GX (GPGX)", value("md"))   // Genesis: PicoDrive or Genesis Plus GX
        assertEquals("GPGX", settings.defaultTags["md"])
        st.onButton(NxButton.LEFT); assertEquals("PicoDrive (MD)", value("md"))
        goTo("ps")
        st.onButton(NxButton.RIGHT); assertEquals("SwanStation (PSX)", value("ps"))
        st.onButton(NxButton.LEFT); assertEquals("PCSX-ReARMed (PS)", value("ps"))
        goTo("sfc")
        st.onButton(NxButton.RIGHT); assertEquals("Supafaust (SUPA)", value("sfc"))
    }

    @Test fun b_from_default_emulators_rescans_only_after_a_change_and_lands_on_its_row() = runTest {
        val counting = CountingRomsSource(source)
        val st = state(MemorySettings(ContentRef("Home")), counting)
        st.load()
        st.openSettings(SettingsSection.DEFAULT_EMULATORS)
        counting.calls.clear()
        st.onButton(NxButton.B)                                                                  // nothing changed: no scan
        assertTrue(counting.calls.isEmpty())
        var lib = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsKind.DEFAULTS, lib.rows[st.selection.value.index].kind)
        st.onButton(NxButton.A)
        st.onButton(NxButton.RIGHT)                                                              // GBA (row 0) -> MGBA
        st.onButton(NxButton.B)
        assertTrue("listChildren:Home/Roms" in counting.calls)                                   // rescanned, under the overlay
        lib = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsSection.EMULATORS, lib.section)
        assertEquals(SettingsKind.DEFAULTS, lib.rows[st.selection.value.index].kind)
        assertEquals("Library rescanned: 3 games", st.notice.value)
    }

    // --- Settings -> Emulators: Default emulators and Emulator Settings ---

    @Test fun the_emulators_page_opens_default_emulators_and_b_returns_to_its_row() = runTest {
        val st = state(MemorySettings(ContentRef("Home")))
        st.load()
        st.onSecondScreenChanged(true)
        st.openTools(); st.onButton(NxButton.DOWN); st.onButton(NxButton.A)                    // Settings
        st.onButton(NxButton.DOWN); st.onButton(NxButton.A)                                      // Emulators
        val e = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsSection.EMULATORS, e.section)
        assertEquals(listOf(SettingsRow(SettingsKind.DEFAULTS, "Default emulators"), SettingsRow(SettingsKind.EMULATORS, "Settings")), e.rows)   // Saves under Library, Pause when unfocused under System
        assertTrue(e.details.isEmpty())
        st.onButton(NxButton.A)                                                                  // Default emulators
        assertEquals(SettingsSection.DEFAULT_EMULATORS, assertIs<Screen.Settings>(st.screen.value).section)
        st.onButton(NxButton.DOWN)
        st.onButton(NxButton.B)
        val back = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsSection.EMULATORS, back.section)
        assertEquals(SettingsKind.DEFAULTS, back.rows[st.selection.value.index].kind)
        st.onButton(NxButton.A)
        assertEquals(1, st.selection.value.index)                                                // the sub-page's row is remembered
        st.onButton(NxButton.B); st.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(st.screen.value)
        assertEquals(1, st.selection.value.index)                                                // B from Emulators: the menu on Emulators
    }

    @Test fun a_changed_default_from_the_emulators_category_rescans_on_b_and_lands_on_emulators() = runTest {
        val counting = CountingRomsSource(source)
        val settings = MemorySettings(ContentRef("Home"))
        val st = state(settings, counting)
        st.load()
        st.openSettingsMenu(); st.onButton(NxButton.DOWN); st.onButton(NxButton.A)              // Emulators
        st.onButton(NxButton.A)                                                                  // Default emulators
        st.onButton(NxButton.RIGHT)                                                              // GBA (row 0) -> MGBA
        assertEquals(mapOf("gba" to "MGBA"), settings.defaultTags)
        counting.calls.clear()
        st.onButton(NxButton.B)
        assertTrue("listChildren:Home/Roms" in counting.calls)                                   // the index no longer fits: rescanned
        val e = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsSection.EMULATORS, e.section)
        assertEquals(SettingsKind.DEFAULTS, e.rows[st.selection.value.index].kind)
        counting.calls.clear()
        st.onButton(NxButton.B)                                                                  // the index fits now: straight to the menu
        assertTrue(counting.calls.isEmpty())
        assertIs<Screen.SettingsMenu>(st.screen.value)
        assertEquals(1, st.selection.value.index)
    }

    @Test fun leaving_the_emulators_page_over_a_stale_library_reloads() = runTest {
        val counting = CountingRomsSource(source)
        val st = state(MemorySettings(ContentRef("Home")), counting)
        st.load()
        st.openSettings(SettingsSection.DEFAULT_EMULATORS)
        st.cycleDefaultTag("gba")                                                                // the index no longer fits
        st.openSettingsMenu(); st.openSettings(SettingsSection.EMULATORS)                        // reached without B: no rescan yet
        counting.calls.clear()
        val log = screenLog(st)
        st.onButton(NxButton.B)
        assertTrue("listChildren:Home/Roms" in counting.calls)
        assertTrue(Screen.Loading in log, log.toString())
        assertIs<Screen.SettingsMenu>(st.screen.value)
    }

    @Test fun split_default_emulators_is_under_the_emulators_category() = runTest {
        val (state, _) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.RIGHT)                            // Emulators, focused
        assertEquals(SettingsSection.EMULATORS, settingsCategoryOf(state.screen.value))
        state.onButton(NxButton.A)                                                               // Default emulators
        assertEquals(SettingsPane.RIGHT, settingsPane(state.screen.value))
        assertEquals(SettingsSection.EMULATORS, settingsCategoryOf(state.screen.value))
        state.onButton(NxButton.B)                                                               // up one level: Emulators
        assertEquals(SettingsSection.EMULATORS, assertIs<Screen.Settings>(state.screen.value).section)
        state.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(1, state.selection.value.index)
        assertEquals(SettingsSection.EMULATORS, state.settingsSections()[state.selection.value.index])
    }

    @Test fun b_from_the_unassigned_list_and_emulator_settings_return_to_their_library_rows() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val st = state(settings)
        st.load()
        openLibraryOn(st, SettingsKind.UNASSIGNED)
        st.onButton(NxButton.A)
        assertTrue(assertIs<Screen.Games>(st.screen.value).system.unassigned)
        st.onButton(NxButton.B)
        assertEquals(SettingsKind.UNASSIGNED, assertIs<Screen.Settings>(st.screen.value).rows[st.selection.value.index].kind)
    }

    @Test fun the_last_scan_time_is_recorded_on_a_good_scan_only() = runTest {
        var clock = 1_790_000_000_000L
        val store = MemoryLibraryIndexStore()
        val st = state(MemorySettings(ContentRef("Home")), store = store, now = { clock })
        st.load()                                                                                // first start: a scan
        val first = clock
        assertEquals(first, assertNotNull(store.text?.let(LibraryIndexCodec::decode)).scannedAt)
        openLibraryOn(st, SettingsKind.RESCAN)
        assertEquals("Last scan: ${formatDateTime(first)}", assertIs<Screen.Settings>(st.screen.value).rows[0].secondary)
        clock += 3_600_000
        source.listingFault = ListingFault.UNAVAILABLE
        st.onButton(NxButton.A)                                                                  // a failed rescan keeps the old time
        assertEquals("Last scan: ${formatDateTime(first)}", assertIs<Screen.Settings>(st.screen.value).rows[0].secondary)
        source.listingFault = null
        st.dismissNotice()
        st.onButton(NxButton.A)                                                                  // a good one moves it
        assertEquals("Last scan: ${formatDateTime(clock)}", assertIs<Screen.Settings>(st.screen.value).rows[0].secondary)
        assertEquals(clock, assertNotNull(store.text?.let(LibraryIndexCodec::decode)).scannedAt)
        val restarted = state(MemorySettings(ContentRef("Home")), store = store)                 // read back from the stored index
        restarted.load()
        restarted.openSettings(SettingsSection.LIBRARY)
        assertEquals("Last scan: ${formatDateTime(clock)}", assertIs<Screen.Settings>(restarted.screen.value).rows[0].secondary)
    }

    @Test fun the_appearance_rows() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        assertEquals(
            listOf(
                SettingsRow(SettingsKind.PAD_THEME, "Pad theme", "Charcoal"),
                SettingsRow(SettingsKind.ACCENT, "Accent", "White"),
                SettingsRow(SettingsKind.SECONDARY_ACCENT, "Secondary accent", "Teal"),
                SettingsRow(SettingsKind.TEXT_SIZE, "Text size", "Large"),
                SettingsRow(SettingsKind.RESET_DEFAULTS, "Reset to defaults"),
            ),
            assertIs<Screen.Settings>(state.screen.value).rows,
        )
    }

    @Test fun the_pad_theme_row_cycles_and_persists() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        assertEquals(SettingsRow(SettingsKind.PAD_THEME, "Pad theme", "Charcoal"), assertIs<Screen.Settings>(state.screen.value).rows[0])
        state.select(0); state.onButton(NxButton.A)
        assertEquals(PadTheme.RETRO, settings.padTheme)
        assertEquals("Retro", assertIs<Screen.Settings>(state.screen.value).rows[0].secondary)
        assertEquals(ListSelection(0, 5), state.selection.value)   // the row stays highlighted
        state.onButton(NxButton.A)
        assertEquals(PadTheme.CHARCOAL, settings.padTheme)
        assertEquals("Charcoal", assertIs<Screen.Settings>(state.screen.value).rows[0].secondary)
    }

    // NX Redux: every value row is ITEM_CYCLE, LEFT/RIGHT step it (A still steps forward).
    @Test fun appearance_value_rows_step_on_left_and_right() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        state.select(0)
        state.onButton(NxButton.LEFT); assertEquals(PadTheme.RETRO, settings.padTheme)
        state.onButton(NxButton.RIGHT); assertEquals(PadTheme.CHARCOAL, settings.padTheme)
        assertEquals(ListSelection(0, 5), state.selection.value)   // the row stays highlighted
    }

    @Test fun appearance_ends_with_reset_and_no_longer_opens_layouts() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals(listOf(SettingsKind.PAD_THEME, SettingsKind.ACCENT, SettingsKind.SECONDARY_ACCENT, SettingsKind.TEXT_SIZE, SettingsKind.RESET_DEFAULTS), rows.map { it.kind })
    }

    @Test fun layouts_rows_follow_the_handheld_order() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuStyles = MenuCategory.entries.associateWith { MenuStyle.CAROUSEL }
            gameListStyle = MenuStyle.CAROUSEL; gameListOrientation = MenuOrientation.VERTICAL
        }
        val state = state(settings)
        state.load()
        fun page(section: SettingsSection) = state.openLayoutsPage(section).map { it.label }
        assertEquals(
            listOf("Home", "Consoles", "Collections", "Tools", "Game lists", "Title and hints", "Game Switcher", "Extra info", "Reset to defaults"),
            page(SettingsSection.MAIN_MENU),
        )
        assertEquals(listOf("Home tab", "Home layout", "New shelf", "Not started shelf", "Home stats"), page(SettingsSection.LAYOUT_HOME))   // Home never has an orientation row
        assertEquals(listOf("Consoles tab", "Consoles layout", "Consoles orientation", "Consoles background"), page(SettingsSection.LAYOUT_CONSOLES))
        assertEquals(listOf("Collections tab", "Collections layout", "Collections orientation"), page(SettingsSection.LAYOUT_COLLECTIONS))
        assertEquals(listOf("Tools tab", "Tools layout", "Tools orientation"), page(SettingsSection.LAYOUT_TOOLS))
        assertEquals(
            listOf("Game lists layout", "Game lists orientation", "Game lists alignment", "Layout options"),
            page(SettingsSection.LAYOUT_GAME_LISTS),
        )
        assertEquals(listOf("Page title", "Button hints", "Recent hint", "Search hint"), page(SettingsSection.LAYOUT_TITLE_HINTS))
        assertEquals(listOf("Game Switcher layout"), page(SettingsSection.LAYOUT_SWITCHER))
        settings.gameListStyle = MenuStyle.LIST
        assertEquals(listOf("Game lists layout", "List art", "Layout options"), page(SettingsSection.LAYOUT_GAME_LISTS).take(3))
        settings.gameListStyle = MenuStyle.BACKDROP
        assertEquals(
            listOf("Game lists layout", "Game lists orientation", "Game lists alignment", "Backdrop art", "Layout options"),
            page(SettingsSection.LAYOUT_GAME_LISTS).take(5),
        )
    }

    @Test fun a_tabs_rows_show_while_it_shows_and_its_orientation_only_on_carousel() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        for (c in listOf(MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS)) for (shown in listOf(true, false)) for (style in MenuStyles.MAIN_MENU_STYLES) {
            settings.menuStyles = LIST_TABS + (c to style)
            settings.setTabShown(c, shown)
            state.openSettings(layoutsPageOf(SettingsKind.TAB_SHOWN, c))
            val expected = buildList {
                add("${c.label} tab")
                if (shown) {
                    add("${c.label} layout")
                    if (style == MenuStyle.CAROUSEL) add("${c.label} orientation")
                    if (c == MenuCategory.CONSOLES && style != MenuStyle.GRID && style != MenuStyle.MINIMAL) add("Consoles background")   // List and Carousel Consoles
                }
            }
            assertEquals(expected, state.layoutsRows().filter { it.category == c }.map { it.label }, "$c shown=$shown $style")
            settings.setTabShown(c, true)
        }
        for (shown in listOf(true, false)) {
            settings.showHomeTab = shown
            state.openSettings(SettingsSection.LAYOUT_HOME)
            assertEquals(if (shown) listOf("Home tab", "Home layout") else listOf("Home tab"), state.layoutsRows().filter { it.category == MenuCategory.HOME }.map { it.label })
        }
        for (style in MenuStyle.entries) for (o in MenuOrientation.entries) {
            settings.gameListStyle = style; settings.gameListOrientation = o
            state.openSettings(SettingsSection.LAYOUT_GAME_LISTS)
            val labels = state.layoutsRows().map { it.label }
            val turns = style == MenuStyle.CAROUSEL || style == MenuStyle.BACKDROP
            assertEquals(turns, "Game lists orientation" in labels, "$style $o")
            assertEquals(turns && o == MenuOrientation.VERTICAL, "Game lists alignment" in labels, "$style $o")
            assertEquals(style == MenuStyle.LIST, "List art" in labels, "$style")
            assertEquals(style == MenuStyle.BACKDROP, "Backdrop art" in labels, "$style")
        }
    }

    @Test fun hiding_a_tab_keeps_the_highlight_on_its_row() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.CONSOLES to MenuStyle.CAROUSEL) }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        val at = state.selectLayoutsRow(SettingsKind.TAB_SHOWN, MenuCategory.CONSOLES)   // on the Consoles page
        val before = state.layoutsRows().size
        state.onButton(NxButton.A)
        assertFalse(settings.showConsolesTab)
        assertEquals(before - 3, state.layoutsRows().size)                 // its layout, orientation and background go
        assertEquals(at, state.selection.value.index)
        assertEquals(SettingsRow(SettingsKind.TAB_SHOWN, "Consoles tab", "Hide", category = MenuCategory.CONSOLES), state.layoutsRows()[at])
        assertTrue(settingsNxRow(state.layoutsRows()[at]).cycleable)
        state.onButton(NxButton.RIGHT); assertTrue(settings.showConsolesTab)
        state.onButton(NxButton.LEFT); assertFalse(settings.showConsolesTab)
        assertEquals(at, state.selection.value.index)
    }

    @Test fun list_art_and_backdrop_art_cycle_their_values() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        val i = state.selectLayoutsRow(SettingsKind.LIST_ART)
        assertEquals("Screenshot", state.layoutsRows()[i].secondary)
        assertEquals(listOf("Mix", "3D box art", "2D box art", "Wheel", "None", "Screenshot"), List(6) { state.onButton(NxButton.RIGHT); state.layoutsRows()[i].secondary })
        state.onButton(NxButton.LEFT); assertEquals(GameListArt.NONE, settings.gameListArt)
        state.onButton(NxButton.RIGHT); assertEquals(GameListArt.SCREENSHOT, settings.gameListArt)
        assertEquals(i, state.selection.value.index)
        settings.gameListStyle = MenuStyle.BACKDROP
        state.openSettings(SettingsSection.MAIN_MENU)
        val b = state.selectLayoutsRow(SettingsKind.BACKDROP_ART)
        assertEquals(listOf("2D box art", "Wheel", "3D box art"), List(3) { state.onButton(NxButton.RIGHT); state.layoutsRows()[b].secondary })
        state.onButton(NxButton.LEFT); assertEquals(BackdropArt.WHEEL, settings.backdropArt)
    }

    @Test fun leaving_list_drops_list_art_and_keeps_the_highlight_on_game_lists_layout() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { gameListArt = GameListArt.MIX }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        val at = state.selectLayoutsRow(SettingsKind.GAME_LIST_STYLE)
        state.onButton(NxButton.RIGHT)                                       // List -> Grid
        assertTrue(state.layoutsRows().none { it.kind == SettingsKind.LIST_ART })
        assertEquals(at, state.selection.value.index)
        assertEquals(GameListArt.MIX, settings.gameListArt)                  // kept while hidden
    }

    @Test fun layouts_home_row_cycles_the_home_style() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_HOME)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsRow(SettingsKind.TAB_SHOWN, "Home tab", "Show", category = MenuCategory.HOME), page.rows[0])
        assertEquals("Home layout", page.rows[1].label)
        assertEquals("Grid", page.rows[1].secondary)
        state.select(1); state.onButton(NxButton.RIGHT)
        assertEquals(MenuStyle.CAROUSEL, MenuStyles.styleOf(settings.menuStyles, MenuCategory.HOME))
        state.onButton(NxButton.RIGHT)
        assertEquals(MenuStyle.MINIMAL, MenuStyles.styleOf(settings.menuStyles, MenuCategory.HOME))
        assertEquals("Minimal", assertIs<Screen.Settings>(state.screen.value).rows[1].secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(MenuStyle.LIST, MenuStyles.styleOf(settings.menuStyles, MenuCategory.HOME))
        assertEquals("List", assertIs<Screen.Settings>(state.screen.value).rows[1].secondary)
    }

    // Layouts groups its options: a row per tab, the game lists, the title and hints and the Game Switcher, each a page.
    @Test fun layouts_lists_its_groups_with_their_summaries() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals(
            listOf("Home", "Consoles", "Collections", "Tools", "Game lists", "Title and hints", "Game Switcher", "Extra info", RESET_DEFAULTS_LABEL),
            rows.map { it.label },
        )
        assertEquals("Grid", rows[0].secondary)                              // a shown tab: its layout
        assertEquals(menuStyleLabel(settings.gameListStyle), rows[4].secondary)
        assertEquals("Show", rows[5].secondary)                              // the title and the hints both shown
        assertEquals(settings.switcherLayout.label, rows[6].secondary)
        settings.showToolsTab = false; settings.menuButtonHints = false
        state.openSettings(SettingsSection.MAIN_MENU)
        val after = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals("Hidden", after[3].secondary)
        assertEquals("Title only", after[5].secondary)
    }

    @Test fun layouts_home_opens_its_page_with_the_picture_following_the_layout() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        state.select(0); state.onButton(NxButton.A)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.LAYOUT_HOME, page.section)
        assertEquals(listOf("Home tab", "Home layout", "New shelf", "Not started shelf", "Home stats"), page.rows.map { it.label })
        assertEquals(LayoutShot.Tab(MenuCategory.HOME, MenuStyle.GRID), page.shot)
        state.select(1); state.onButton(NxButton.RIGHT)                      // Grid -> Carousel: the picture follows
        assertEquals(LayoutShot.Tab(MenuCategory.HOME, MenuStyle.CAROUSEL), assertIs<Screen.Settings>(state.screen.value).shot)
        state.onButton(NxButton.B)                                           // back on Home
        assertEquals(SettingsSection.MAIN_MENU, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(0, state.selection.value.index)
        assertEquals("Carousel", assertIs<Screen.Settings>(state.screen.value).rows[0].secondary)
    }

    // A group page has no Done: B goes back, onto the group's row.
    // Home's picture follows Home stats too: hidden, the capture without the strip.
    @Test fun layouts_home_picture_follows_home_stats() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_HOME)
        assertEquals(LayoutShot.Tab(MenuCategory.HOME, MenuStyle.GRID, stats = true), assertIs<Screen.Settings>(state.screen.value).shot)
        state.select(state.layoutsRows().indexOfFirst { it.kind == SettingsKind.HOME_STATS }); state.onButton(NxButton.RIGHT)
        assertEquals(LayoutShot.Tab(MenuCategory.HOME, MenuStyle.GRID, stats = false), assertIs<Screen.Settings>(state.screen.value).shot)
    }

    // A Carousel tab's picture follows its orientation: Vertical, the stood-on-end capture.
    @Test fun layouts_consoles_picture_follows_the_carousels_orientation() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL) }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_CONSOLES)
        assertEquals(false, assertIs<LayoutShot.Tab>(assertIs<Screen.Settings>(state.screen.value).shot).vertical)
        state.select(state.layoutsRows().indexOfFirst { it.kind == SettingsKind.MENU_ORIENTATION }); state.onButton(NxButton.RIGHT)
        assertEquals(LayoutShot.Tab(MenuCategory.CONSOLES, MenuStyle.CAROUSEL, vertical = true), assertIs<Screen.Settings>(state.screen.value).shot)
    }

    // The Consoles picture follows Layouts → Consoles background (List and Carousel); the other tabs keep the default.
    @Test fun layouts_consoles_picture_follows_the_consoles_background() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL) }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_CONSOLES)
        fun shot() = assertIs<Screen.Settings>(state.screen.value).shot
        assertEquals(LayoutShot.Tab(MenuCategory.CONSOLES, MenuStyle.CAROUSEL), shot())
        state.select(state.layoutsRows().indexOfFirst { it.kind == SettingsKind.CONTROLLER_ART })
        state.onButton(NxButton.RIGHT)
        assertEquals(LayoutShot.Tab(MenuCategory.CONSOLES, MenuStyle.CAROUSEL, art = ConsoleArt.BACKGROUND), shot())
        state.onButton(NxButton.RIGHT)
        assertEquals(LayoutShot.Tab(MenuCategory.CONSOLES, MenuStyle.CAROUSEL, art = ConsoleArt.NONE), shot())
        state.openSettings(SettingsSection.LAYOUT_COLLECTIONS)
        assertEquals(ConsoleArt.CONTROLLER, assertIs<LayoutShot.Tab>(shot()).art)
    }

    // The game lists' picture follows a Carousel's or Backdrop's orientation and, standing on end, its alignment.
    @Test fun layouts_game_lists_picture_follows_orientation_and_alignment() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = MenuStyle.BACKDROP }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_GAME_LISTS)
        assertEquals(LayoutShot.Games(MenuStyle.BACKDROP), assertIs<Screen.Settings>(state.screen.value).shot)
        state.select(state.layoutsRows().indexOfFirst { it.kind == SettingsKind.GAME_LIST_ORIENTATION }); state.onButton(NxButton.RIGHT)
        assertEquals(LayoutShot.Games(MenuStyle.BACKDROP, vertical = true), assertIs<Screen.Settings>(state.screen.value).shot)
        state.select(state.layoutsRows().indexOfFirst { it.kind == SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT }); state.onButton(NxButton.RIGHT)
        assertEquals(LayoutShot.Games(MenuStyle.BACKDROP, vertical = true, right = true), assertIs<Screen.Settings>(state.screen.value).shot)
    }

    @Test fun layouts_group_b_goes_back_to_its_row() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        state.select(2); state.onButton(NxButton.A)                          // Collections
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.LAYOUT_COLLECTIONS, page.section)
        assertEquals(MenuCategory.COLLECTIONS, assertIs<LayoutShot.Tab>(page.shot).category)
        assertEquals(listOf("Collections tab", "Collections layout"), page.rows.map { it.label }.take(2))
        state.onButton(NxButton.B)
        assertEquals(SettingsSection.MAIN_MENU, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(2, state.selection.value.index)
    }

    @Test fun layouts_a_hidden_tab_shows_only_its_tab_row_and_no_picture() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_CONSOLES)
        state.select(0); state.onButton(NxButton.RIGHT)                      // Consoles tab: Show -> Hide
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(listOf("Consoles tab" to "Hide"), page.rows.map { it.label to it.secondary })
        assertNull(page.shot)
    }

    @Test fun layouts_game_lists_page_has_the_list_options_and_its_picture() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        settings.gameListStyle = MenuStyle.LIST
        state.openSettings(SettingsSection.LAYOUT_GAME_LISTS)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(listOf("Game lists layout", "List art", "Layout options"), page.rows.map { it.label }.filter { it != "Achievements panel" })
        assertEquals(LayoutShot.Games(MenuStyle.LIST), page.shot)
    }

    @Test fun layouts_title_and_hints_page_flips_and_the_picture_follows() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_TITLE_HINTS)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(listOf("Page title", "Button hints", "Recent hint", "Search hint"), page.rows.map { it.label })
        assertEquals(LayoutShot.Chrome(title = true, hints = true), page.shot)
        state.select(1); state.onButton(NxButton.A)                          // Button hints: Hide, its hint rows go
        val after = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(listOf("Page title", "Button hints"), after.rows.map { it.label })
        assertEquals(LayoutShot.Chrome(title = true, hints = false), after.shot)
    }

    @Test fun layouts_game_switcher_page_shows_its_layout() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSettings(SettingsSection.LAYOUT_SWITCHER)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(listOf("Game Switcher layout"), page.rows.map { it.label })
        assertEquals(LayoutShot.Switcher(SwitcherLayout.FULL), page.shot)
        state.select(0); state.onButton(NxButton.RIGHT)                      // Full screen -> List: the picture follows
        assertEquals(LayoutShot.Switcher(SwitcherLayout.LIST), assertIs<Screen.Settings>(state.screen.value).shot)
    }

    // Layouts is a Settings category of its own, right after Appearance (NX Redux's main page).
    @Test fun layouts_is_a_category_after_appearance_and_b_leaves_it_for_the_settings_menu() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSettingsMenu()
        val sections = assertIs<Screen.SettingsMenu>(state.screen.value).sections
        assertEquals(sections.indexOf(SettingsSection.APPEARANCE) + 1, sections.indexOf(SettingsSection.MAIN_MENU))
        state.select(sections.indexOf(SettingsSection.MAIN_MENU)); state.onButton(NxButton.A)
        assertEquals(SettingsSection.MAIN_MENU, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(SettingsSection.MAIN_MENU, settingsCategoryOf(state.screen.value))
        state.onButton(NxButton.DOWN)
        state.onButton(NxButton.B)                                       // a category: back to the menu, on Layouts
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(sections.indexOf(SettingsSection.MAIN_MENU), state.selection.value.index)
        state.onButton(NxButton.A)                                       // its row remembered
        assertEquals(1, state.selection.value.index)
    }

    @Test fun layouts_page_title_and_button_hints_rows_flip_and_persist() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_TITLE_HINTS)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals(
            listOf(SettingsKind.PAGE_TITLE, SettingsKind.BUTTON_HINTS, SettingsKind.HINT_SHOWN, SettingsKind.HINT_SHOWN),
            rows.map { it.kind },
        )
        assertEquals(SettingsRow(SettingsKind.PAGE_TITLE, "Page title", "Show"), rows[0])
        assertEquals(SettingsRow(SettingsKind.BUTTON_HINTS, "Button hints", "Show"), rows[1])
        val title = rows.indexOfFirst { it.kind == SettingsKind.PAGE_TITLE }
        state.select(title); state.onButton(NxButton.A)
        assertFalse(settings.menuPageTitle)
        assertEquals("Hide", assertIs<Screen.Settings>(state.screen.value).rows[title].secondary)
        state.onButton(NxButton.LEFT); assertTrue(settings.menuPageTitle)
        state.onButton(NxButton.RIGHT); assertFalse(settings.menuPageTitle)
        assertEquals(title, state.selection.value.index)
        state.select(title + 1); state.onButton(NxButton.RIGHT)
        assertFalse(settings.menuButtonHints)
        assertEquals("Hide", assertIs<Screen.Settings>(state.screen.value).rows[title + 1].secondary)
        state.onButton(NxButton.A); assertTrue(settings.menuButtonHints)
    }

    // Layouts → Extra info, after the Game Switcher row: Show by default, A, LEFT and RIGHT flip it, it persists and the
    // highlight stays on it; unlike Page title and Button hints it is in effect in every footer mode.
    @Test fun layouts_extra_info_row_flips_and_persists() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        val extra = rows.indexOfFirst { it.kind == SettingsKind.EXTRA_INFO }
        assertEquals(rows.indexOfFirst { it.kind == SettingsKind.LAYOUT_SWITCHER } + 1, extra)   // after the groups, before Reset
        assertEquals(rows.indexOfFirst { it.kind == SettingsKind.RESET_DEFAULTS } - 1, extra)
        assertEquals(SettingsRow(SettingsKind.EXTRA_INFO, "Extra info", "Show"), rows[extra])
        assertTrue(settingsNxRow(rows[extra]).cycleable)
        assertTrue(state.launcherChrome().extraInfo)
        state.select(extra); state.onButton(NxButton.A)
        assertFalse(settings.menuExtraInfo)
        assertEquals("Hide", assertIs<Screen.Settings>(state.screen.value).rows[extra].secondary)
        assertFalse(state.launcherChrome().extraInfo)
        state.onFooterMode(FooterMode.BUTTONS); assertFalse(state.launcherChrome().extraInfo)   // touch only too
        state.onButton(NxButton.LEFT); assertTrue(settings.menuExtraInfo)
        state.onButton(NxButton.RIGHT); assertFalse(settings.menuExtraInfo)
        assertEquals(extra, state.selection.value.index)
    }

    // Layouts → Home stats, under Home's shelf rows: Home's strip and cards' info, apart from Extra info (the
    // game lists'); every press flips it, in every footer mode.
    @Test fun layouts_home_stats_row_flips_apart_from_extra_info() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_HOME)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        val home = rows.indexOfFirst { it.kind == SettingsKind.HOME_STATS }
        assertEquals(rows.indexOfFirst { it.label == "Not started shelf" } + 1, home)
        assertEquals(SettingsRow(SettingsKind.HOME_STATS, "Home stats", "Show"), rows[home])
        assertTrue(settingsNxRow(rows[home]).cycleable)
        state.select(home); state.onButton(NxButton.A)
        assertFalse(settings.menuHomeStats); assertTrue(settings.menuExtraInfo)
        assertEquals("Hide", assertIs<Screen.Settings>(state.screen.value).rows[home].secondary)
        assertFalse(state.launcherChrome().homeStats); assertTrue(state.launcherChrome().extraInfo)
        state.onFooterMode(FooterMode.BUTTONS); assertFalse(state.launcherChrome().homeStats)   // touch only too
        state.onButton(NxButton.RIGHT); assertTrue(settings.menuHomeStats)
        assertEquals(home, state.selection.value.index)
    }

    // Home reports its frame with Extra info hidden: the strip's room goes to the top section, which grows the squares
    // but keeps the slots (5 a column): on the portrait type 12 tools still show 9 and "+3". The rows match the board the
    // screen draws.
    @Test fun hidden_extra_info_gives_home_the_strips_room() = runTest {
        val state = b2Home(games = 0, tools = 12); state.load(); advanceUntilIdle()
        state.onHomeLayout(PORT); advanceUntilIdle()
        val lines = assertNotNull(state.homeView.value.lines)
        assertTrue(lines > 0)
        assertEquals(MainRow.MoreTools(3), state.screen.value.homeRows.single { it is MainRow.MoreTools })
        val shown = homeBoard(PORT, lines, 12, 0)
        val hidden = PORT.copy(statsHidden = true)
        state.onHomeLayout(hidden); advanceUntilIdle()
        val view = state.homeView.value
        assertEquals(hidden, view.frame)
        val b = homeBoard(view.frame!!, view.lines!!, view.tools, view.games, view.shelves)
        assertEquals(0, b.stripLines); assertEquals(10, b.slots); assertTrue(b.square > shown.square)
        assertEquals(MainRow.MoreTools(3), state.screen.value.homeRows.single { it is MainRow.MoreTools })
        assertEquals(9, state.screen.value.homeRows.count { it is MainRow.PinnedTool })
        assertEquals(view.topCount, b.top.size)
    }

    @Test fun main_menu_page_sets_each_tabs_style_then_the_game_list_style() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)                   // Layouts
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.MAIN_MENU, page.section)
        assertEquals("Layouts", page.section.label)   // the page's title
        assertEquals(
            listOf("Home" to "Grid", "Consoles" to "List", "Collections" to "List", "Tools" to "List", "Game lists" to "List", "Title and hints" to "Show",
                "Game Switcher" to "Full screen", "Extra info" to "Show", "Reset to defaults" to null),
            page.rows.map { it.label to it.secondary },
        )
        fun group(section: SettingsSection) = state.openLayoutsPage(section).map { it.label to it.secondary }
        assertEquals(listOf("Home tab" to "Show", "Home layout" to "Grid", "New shelf" to "Show", "Not started shelf" to "Show", "Home stats" to "Show"), group(SettingsSection.LAYOUT_HOME))
        assertEquals(listOf("Consoles tab" to "Show", "Consoles layout" to "List", "Consoles background" to settings.consoleArt.label), group(SettingsSection.LAYOUT_CONSOLES))
        assertEquals(listOf("Collections tab" to "Show", "Collections layout" to "List"), group(SettingsSection.LAYOUT_COLLECTIONS))
        assertEquals(listOf("Tools tab" to "Show", "Tools layout" to "List"), group(SettingsSection.LAYOUT_TOOLS))
        assertEquals(listOf("Game lists layout" to "List", "List art" to "Screenshot", "Layout options" to "Show"), group(SettingsSection.LAYOUT_GAME_LISTS))
        assertEquals(
            listOf("Page title" to "Show", "Button hints" to "Show", "Recent hint" to "Show", "Search hint" to "Show"),
            group(SettingsSection.LAYOUT_TITLE_HINTS),
        )
        assertEquals(listOf("Game Switcher layout" to "Full screen"), group(SettingsSection.LAYOUT_SWITCHER))
        state.openSettings(SettingsSection.MAIN_MENU)
        state.selectLayoutsRow(SettingsKind.MENU_STYLE, MenuCategory.CONSOLES); state.onButton(NxButton.RIGHT)
        assertEquals(MenuStyle.GRID, MenuStyles.styleOf(settings.menuStyles, MenuCategory.CONSOLES))
        state.selectLayoutsRow(SettingsKind.MENU_STYLE, MenuCategory.TOOLS); state.onButton(NxButton.A)
        assertEquals(MenuStyle.GRID, MenuStyles.styleOf(settings.menuStyles, MenuCategory.TOOLS))
        val games = state.selectLayoutsRow(SettingsKind.GAME_LIST_STYLE); state.onButton(NxButton.RIGHT)
        assertEquals(MenuStyle.GRID, settings.gameListStyle)
        state.onButton(NxButton.LEFT)
        assertEquals(MenuStyle.LIST, settings.gameListStyle)
        assertEquals(games, state.selection.value.index)
        assertEquals("List", state.layoutsRows()[games].secondary)
        state.onButton(NxButton.B)                        // the group: back to Layouts
        assertEquals(SettingsSection.MAIN_MENU, assertIs<Screen.Settings>(state.screen.value).section)
        state.onButton(NxButton.B)                        // a category: back to the Settings menu
        assertIs<Screen.SettingsMenu>(state.screen.value)
    }

    // --- Two-pane Settings (task S3): the menu is the left pane, every page the right ---

    /** A tap on row [i] as every list draws it ([nxTap]): [opens] is the list's [LocalNxTapOpens] for it (false in game lists). */
    private fun tap(state: AppState, i: Int, opens: Boolean = true) =
        nxTap(i, i == state.selection.value.index, opens, state::select) { state.openSelected() }

    /** A tap on main menu row [i], as [MainMenuScreen] provides it: a game (Continue, a pin) or a Home pinned tool takes two taps. */
    private fun mainTap(state: AppState, i: Int) =
        tap(state, i, opens = !mainRowTakesTwoTaps(assertIs<Screen.Systems>(state.screen.value).rows.getOrNull(i)))

    @Test fun one_tap_opens_a_settings_row() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettingsMenu()
        tap(state, 2)                                                   // Appearance, opened at once
        assertEquals(SettingsSection.APPEARANCE, assertIs<Screen.Settings>(state.screen.value).section)
        val theme = settings.padTheme
        state.onButton(NxButton.DOWN)                                   // off the pad theme row
        tap(state, 0)                                                   // Pad theme: highlighted and stepped, as A does
        assertEquals(0, state.selection.value.index)
        assertNotEquals(theme, settings.padTheme)
    }

    @Test fun one_tap_opens_a_retroachievements_row() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state)
        assertIs<Screen.RetroAchievements>(state.screen.value)
        tap(state, raPageRow(Screen.RaSettings(emptyList()))!!)          // Settings, in one tap
        assertIs<Screen.RaSettings>(state.screen.value)
    }

    @Test fun a_game_list_row_takes_two_taps_to_play() = runTest {
        val runner = RecordingRunner()
        val state = state(MemorySettings(ContentRef("Home")), runner = runner, data = FakeUserData(recents = listOf(Recent(mk), Recent(tetris))))
        state.load()
        state.openList(ListSource.Recent)
        val games = assertIs<Screen.Games>(state.screen.value)
        val i = 1
        assertEquals(0, state.selection.value.index)
        tap(state, i, opens = false)                                    // highlights only
        assertTrue(runner.launched.isEmpty()); assertEquals(i, state.selection.value.index)
        tap(state, i, opens = false)                                    // the highlighted game plays
        assertEquals(1, runner.launched.size)
        assertEquals(games.entries[i].game.path, runner.launched.single().gamePath)
    }

    @Test fun a_hidden_game_takes_two_taps_to_unhide() = runTest {
        val data = FakeUserData(hidden = listOf(mario, "/Roms/Commodore 64 (C64)/Boulder Dash 2.chd"))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load()
        openHiddenList(state)
        assertEquals(0, state.selection.value.index)
        tap(state, 1, opens = false)                                    // highlights only
        assertEquals(2, data.hidden.size); assertEquals(1, state.selection.value.index)
        tap(state, 1, opens = false)                                    // the highlighted game unhides
        assertEquals(listOf(mario), data.hidden)
    }

    @Test fun a_context_menu_over_a_game_list_opens_on_one_tap() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "GBA" })
        state.onButton(NxButton.MENU)
        val menu = assertIs<Screen.Games>(state.screen.value).menu!!
        val at = menu.items.indexOfFirst { it.kind == ContextKind.EMULATOR }
        assertNotEquals(menu.index, at)
        nxTap(at, at == menu.index, opens = true, state::menuSelect) { state.menuOpen() }   // the overlay is a one-tap list
        assertNotNull(assertIs<Screen.Games>(state.screen.value).menu!!.sub)   // the Emulator submenu, in one tap
    }

    @Test fun a_main_menu_console_opens_on_one_tap() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.onConsoles()
        val menu = assertIs<Screen.Systems>(state.screen.value)
        val gba = menu.rows.indexOfFirst { (it as? MainRow.System)?.system?.tag == "GBA" }
        if (state.selection.value.index == gba) state.select(if (gba == 0) 1 else 0)
        mainTap(state, gba)
        assertEquals("GBA", assertIs<ListSource.Console>(assertIs<Screen.Games>(state.screen.value).source).system.tag)
    }

    @Test fun home_continue_and_a_pinned_game_take_two_taps() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(recents = listOf(Recent(tetris)), pins = listOf(Shortcut(mario, "Mario"), Shortcut(tetris, "Tetris")))
        val state = state(MemorySettings(ContentRef("Home")), runner = runner, data = data)
        state.load(); advanceUntilIdle()
        val rows = state.screen.value.homeRows
        val pin = rows.indexOfFirst { it is MainRow.Pin }
        assertIs<MainRow.Continue>(rows[0])
        state.select(0)
        mainTap(state, pin)                                             // highlights only
        assertTrue(runner.launched.isEmpty()); assertEquals(pin, state.selection.value.index)
        mainTap(state, pin); advanceUntilIdle()                         // the highlighted pin plays
        assertEquals(mario, runner.launched.single().gamePath)
        runner.launched.clear()
        while (state.screen.value !is Screen.Systems) state.onButton(NxButton.B)
        state.selectHome { it is MainRow.Pin }                          // Tetris, back as a pin
        mainTap(state, 0)                                               // Continue: highlights only
        assertTrue(runner.launched.isEmpty()); assertEquals(0, state.selection.value.index)
    }

    @Test fun a_home_pinned_tool_takes_two_taps() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val files = MemoryPrivateFiles().apply { this.files[ToolPins.FILE] = "GAME_TIME" }   // Game Tracker pinned on Home
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), FakeUserData(recents = listOf(Recent(tetris))),
            gameTime = HomeGameTime(), privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        state.load(); advanceUntilIdle()
        val tool = state.screen.value.homeRows.indexOfFirst { it is MainRow.PinnedTool }
        assertTrue(tool >= 0); state.select(0)
        mainTap(state, tool); advanceUntilIdle()                        // highlights only
        assertIs<Screen.Systems>(state.screen.value); assertEquals(tool, state.selection.value.index)
        mainTap(state, tool); advanceUntilIdle()                        // the highlighted tool opens
        assertIs<Screen.GameTime>(state.screen.value)
    }

    // Portrait's 5 / 10 slots and the wide face's 15 show all 7 tools, so "+3" is checked on a tall window's 5 (UNDER).
    @Test fun home_more_tools_and_a_tools_tab_tool_open_on_one_tap() = runTest {
        val state = b2Home(games = 6, tools = 7); state.load(); advanceUntilIdle()
        state.onHomeLayout(TALL); advanceUntilIdle()
        val more = state.screen.value.homeRows.indexOfFirst { it is MainRow.MoreTools }
        assertTrue(more > 0); state.select(0)
        mainTap(state, more); advanceUntilIdle()                        // "+3": the Tools tab at once
        val tools = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.TOOLS, tools.category)
        val settings = tools.rows.indexOfFirst { (it as? MainRow.Tool)?.row?.kind == ToolKind.SETTINGS }
        assertTrue(settings >= 0)
        if (state.selection.value.index == settings) state.select(if (settings == 0) 1 else 0)
        mainTap(state, settings); advanceUntilIdle()                    // a Tools tab tool: opened at once
        assertIs<Screen.SettingsMenu>(state.screen.value)
    }

    /** Game Tracker (pinned on Home) open on two games, Tetris and Kirby; [sessions] answers each game id's sessions read. */
    private suspend fun TestScope.gameTracker(sessions: MutableList<Long>): AppState {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val files = MemoryPrivateFiles().apply { this.files[ToolPins.FILE] = "GAME_TIME" }   // Game Tracker pinned on Home
        val data = object : GameTimeData by NoGameTime {
            override suspend fun totals() = listOf(total(tetris, "Tetris", 60_000L), total("/Roms/kirby.gb", "Kirby", 30_000L))
            override suspend fun sessions(target: Long): com.nxredux.mobile.gametime.GameSessions {
                sessions += target
                return com.nxredux.mobile.gametime.GameSessions(listOf(com.nxredux.mobile.gametime.GameSession("s$target", target, "dev", "Fold", 1_000L, 61_000L, 60_000L)))
            }
        }
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), FakeUserData(recents = listOf(Recent(tetris))),
            gameTime = data, privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        state.load(); advanceUntilIdle()
        state.selectHome { it is MainRow.PinnedTool }
        state.onButton(NxButton.A); advanceUntilIdle()
        advanceUntilIdle()   // the art map decodes on deps.compute, the test scheduler
        return state
    }

    private fun AppState.sessionsOf(): String = assertIs<Screen.GameTimeSessions>(screen.value).game.name

    @Test fun game_tracker_a_opens_a_games_sessions_and_b_returns_to_its_row() = runTest {
        val reads = mutableListOf<Long>()
        val state = gameTracker(reads)
        state.onButton(NxButton.DOWN)
        state.onButton(NxButton.A); advanceUntilIdle()                                   // A: Kirby's sessions
        assertEquals("Kirby", state.sessionsOf())
        val parent = state.gameTimeParent(state.screen.value)!!                          // the left pane beside them: the list, Kirby lit
        assertEquals(1, parent.row); assertEquals(listOf("Tetris", "Kirby"), parent.screen.rows.map { it.total.name })
        state.onButton(NxButton.A); state.onButton(NxButton.MENU); advanceUntilIdle()    // read-only rows
        assertEquals("Kirby", state.sessionsOf()); assertEquals(1, reads.size)
        state.onButton(NxButton.B); advanceUntilIdle()                                   // B: back to the list, on Kirby
        assertNull(assertIs<Screen.GameTime>(state.screen.value).modal); assertEquals(1, state.selection.value.index)
        assertNull(state.gameTimeParent(state.screen.value))
        state.onButton(NxButton.B); advanceUntilIdle()                                   // B again leaves the tool
        assertTrue(state.screen.value !is Screen.GameTime)
    }

    @Test fun game_tracker_one_tap_opens_sessions_and_the_row_menu_opens_on_menu_only() = runTest {
        val state = gameTracker(mutableListOf())
        state.onButton(NxButton.MENU); advanceUntilIdle()                                // MENU (and a long press, which sends MENU)
        assertIs<GameTimeModal.Options>(assertIs<Screen.GameTime>(state.screen.value).modal)
        state.onButton(NxButton.B); advanceUntilIdle()                                   // B closes the menu
        assertNull(assertIs<Screen.GameTime>(state.screen.value).modal)
        nxTap(1, selected = false, opens = true, state::select) { state.openSelected() }   // one tap on another row: its sessions
        advanceUntilIdle()
        assertEquals("Kirby", state.sessionsOf())
        nxTap(0, selected = false, opens = true, state::select) { state.openSelected() }   // a tap on a session row: nothing
        advanceUntilIdle()
        assertEquals("Kirby", state.sessionsOf()); assertEquals(0, state.selection.value.index)
    }

    @Test fun game_tracker_split_a_tap_on_another_game_on_the_left_opens_its_sessions_directly() = runTest {
        val reads = mutableListOf<Long>()
        val state = gameTracker(reads)
        state.onButton(NxButton.A); advanceUntilIdle()                                   // Tetris's sessions, the list on the left
        assertEquals("Tetris", state.sessionsOf())
        val tap = { i: Int -> nxTap(i, selected = i == state.gameTimeParent(state.screen.value)?.row, opens = true, state::gameTimeParentTap, state::gameTimeParentTap) }
        tap(0); advanceUntilIdle()                                                       // the game already open: nothing
        assertEquals("Tetris", state.sessionsOf()); assertEquals(1, reads.size)
        tap(1); advanceUntilIdle()                                                       // Kirby: straight to its sessions, one read
        assertEquals("Kirby", state.sessionsOf()); assertEquals(2, reads.size)
        assertEquals(1, state.gameTimeParent(state.screen.value)!!.row)
        state.onButton(NxButton.B); advanceUntilIdle()                                   // B: the list, on Kirby
        assertIs<Screen.GameTime>(state.screen.value); assertEquals(1, state.selection.value.index)
    }

    @Test fun split_one_tap_on_a_category_from_a_page_opens_it() = runTest {
        val (state, _) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.onButton(NxButton.RIGHT)   // Appearance's page, focused
        nxTap(state.settingsSections().indexOf(SettingsSection.SYSTEM), selected = false, opens = true, state::settingsCategoryTap, state::settingsCategoryTap)   // System on the dimmed left
        assertEquals(SettingsSection.SYSTEM, assertIs<Screen.Settings>(state.screen.value).section)
    }

    @Test fun split_one_tap_on_a_preview_row_acts_on_it() = runTest {
        val (state, settings) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN)                                   // Appearance, previewed
        val theme = settings.padTheme
        nxTap(0, selected = false, opens = true, state::settingsPreviewTap, state::settingsPreviewTap)   // Pad theme
        assertEquals(SettingsSection.APPEARANCE, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(0, state.selection.value.index)
        assertNotEquals(theme, settings.padTheme)
    }

    @Test fun one_tap_beside_ra_settings_opens_that_row() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        assertIs<Screen.RaSettings>(state.screen.value)
        val tap = { i: Int -> state.raParentTap(Screen.RetroAchievements(), i) }
        nxTap(0, selected = false, opens = true, tap, tap)               // Achievements on the dimmed left
        advanceUntilIdle()
        assertIs<Screen.RaGames>(state.screen.value)
    }

    private fun TestScope.splitSettings(): Pair<AppState, MemorySettings> {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.onSettingsSplit(true)
        state.openSettingsMenu()
        return state to settings
    }

    @Test fun split_right_or_a_moves_into_the_category_and_b_comes_back_on_it() = runTest {
        val (state, _) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN)   // Appearance, live in the right pane
        assertEquals(settingsPreviewRows(state, SettingsSection.APPEARANCE), state.settingsPreview(SettingsSection.APPEARANCE).rows)
        state.onButton(NxButton.RIGHT)                                  // into the right pane, on its first row
        assertEquals(SettingsSection.APPEARANCE, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN)
        state.onButton(NxButton.B)                                      // back to the left pane, same category
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(2, state.selection.value.index)
        state.onButton(NxButton.A)                                      // A goes in too, on the remembered row
        assertEquals(2, state.selection.value.index)
    }

    @Test fun without_the_split_right_on_the_menu_does_nothing() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettingsMenu()
        state.onButton(NxButton.RIGHT)
        assertIs<Screen.SettingsMenu>(state.screen.value)
    }

    @Test fun split_left_on_a_row_without_a_value_stays_in_the_right_pane() = runTest {
        val (state, _) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.onButton(NxButton.A)
        state.select(3)                                                 // Reset to defaults: no value
        state.onButton(NxButton.LEFT)
        assertEquals(SettingsSection.APPEARANCE, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(3, state.selection.value.index)
    }

    @Test fun split_sub_pages_open_in_the_right_pane_and_b_goes_up_one_level_then_left() = runTest {
        val (state, _) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.RIGHT)  // Emulators
        state.select(0); state.onButton(NxButton.A)                     // Default emulators
        assertEquals(SettingsSection.DEFAULT_EMULATORS, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(SettingsPane.RIGHT, settingsPane(state.screen.value))
        assertEquals(SettingsSection.EMULATORS, settingsCategoryOf(state.screen.value))
        state.onButton(NxButton.B)
        assertEquals(SettingsSection.EMULATORS, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(0, state.selection.value.index)   // back on Default emulators
        state.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(1, state.selection.value.index)
    }

    @Test fun split_layouts_is_a_category_of_its_own_beside_appearance() = runTest {
        val (state, _) = splitSettings()
        repeat(3) { state.onButton(NxButton.DOWN) }                     // Layouts, live in the right pane
        assertEquals(settingsPreviewRows(state, SettingsSection.MAIN_MENU), state.settingsPreview(SettingsSection.MAIN_MENU).rows)
        state.onButton(NxButton.RIGHT)
        assertEquals(SettingsSection.MAIN_MENU, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(SettingsSection.MAIN_MENU, settingsCategoryOf(state.screen.value))
        state.onButton(NxButton.B)                                      // one B: the left pane, on Layouts
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(3, state.selection.value.index)
    }

    @Test fun turning_the_split_off_and_on_changes_what_right_does_on_a_category() = runTest {
        val (state, _) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN)   // Appearance
        state.onSettingsSplit(false)
        state.onButton(NxButton.RIGHT)                                  // one page: RIGHT on the root does nothing
        assertIs<Screen.SettingsMenu>(state.screen.value); assertEquals(2, state.selection.value.index)
        state.onSettingsSplit(true)
        state.onButton(NxButton.RIGHT)                                  // two panes again: into the same category
        assertEquals(SettingsSection.APPEARANCE, assertIs<Screen.Settings>(state.screen.value).section)
    }

    @Test fun split_a_category_tap_waits_for_the_remove_confirm() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.addExtra(ContentRef("sd/Loose"))
        state.onSettingsSplit(true)
        state.openSettingsMenu()
        state.openSettings(SettingsSection.LIBRARY)
        state.select(rowIndex(state, SettingsKind.EXTRA)); state.openSelected()   // Remove folder?
        val confirm = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(settings.extraFolders[0], confirm.confirmRemove)
        state.settingsCategoryTap(1)
        assertEquals(confirm, state.screen.value)
        assertEquals(1, settings.extraFolders.size)
    }

    /** Every screen [state] shows from now on, in order (an unconfined collector sees each one). */
    private fun TestScope.screenLog(state: AppState): List<Screen> {
        val log = mutableListOf<Screen>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.screen.collect { log += it } }
        return log
    }

    @Test fun leaving_an_unchanged_library_goes_straight_to_the_menu() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSettingsMenu(); state.openSettings(SettingsSection.LIBRARY)
        val log = screenLog(state)
        state.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertTrue(log.none { it == Screen.Loading }, log.toString())   // no reload: nothing blanks the page
    }

    @Test fun leaving_library_after_hiding_a_game_reloads_first() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "GBA" })
        choose(state, ContextKind.HIDE)
        state.openSettingsMenu(); state.openSettings(SettingsSection.LIBRARY)
        val log = screenLog(state)
        state.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertTrue(Screen.Loading in log, log.toString())
    }

    @Test fun a_rescan_after_adding_a_folder_leaves_library_clean() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSettingsMenu(); state.openSettings(SettingsSection.LIBRARY)
        state.addExtra(ContentRef("sd/Loose"))                                  // rescans, back on Library
        assertEquals(SettingsSection.LIBRARY, assertIs<Screen.Settings>(state.screen.value).section)
        val log = screenLog(state)
        state.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertTrue(log.none { it == Screen.Loading }, log.toString())
    }

    @Test fun the_reindex_after_removing_a_folder_leaves_library_clean() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.addExtra(ContentRef("sd/Loose"))
        state.openSettingsMenu(); state.openSettings(SettingsSection.LIBRARY)
        state.select(rowIndex(state, SettingsKind.EXTRA)); state.openSelected(); state.onButton(NxButton.A)   // remove: re-indexed in the background
        val log = screenLog(state)
        state.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertTrue(log.none { it == Screen.Loading }, log.toString())
    }

    @Test fun a_category_tap_off_the_settings_panes_leaves_the_roots_row_alone() = runTest {
        val (state, _) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN)   // Appearance
        state.onButton(NxButton.B)                                              // Tools: the root remembers Appearance
        state.settingsCategoryTap(3)                                            // a stale tap from a page drawn whole
        state.openSettingsMenu()
        assertEquals(2, state.selection.value.index)
    }

    @Test fun split_tapping_a_category_from_a_sub_page_focuses_the_left_pane_on_it() = runTest {
        val (state, _) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.onButton(NxButton.RIGHT)
        state.select(2); state.onButton(NxButton.A)                     // Layouts
        state.settingsCategoryTap(3)                                    // About
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(3, state.selection.value.index)
        state.onButton(NxButton.UP); state.onButton(NxButton.A)         // Appearance again: still on its Layouts row
        assertEquals(2, state.selection.value.index)
    }

    @Test fun split_tapping_a_row_of_the_preview_opens_the_category_on_that_row() = runTest {
        val (state, _) = splitSettings()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN)   // Appearance
        state.settingsPreviewTap(1)
        assertEquals(SettingsSection.APPEARANCE, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.B)
        assertEquals(2, state.selection.value.index)                    // the menu on Appearance
    }

    private fun settingsPreviewRows(state: AppState, section: SettingsSection): List<SettingsRow> {
        val back = state.screen.value to state.selection.value
        state.openSettings(section)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        state.onButton(NxButton.B)
        assertEquals(back.first, state.screen.value); assertEquals(back.second, state.selection.value)
        return rows
    }

    @Test fun game_lists_are_drawn_in_the_game_list_style() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = MenuStyle.LIST }
        val state = state(settings)
        assertEquals(MenuStyle.LIST, state.styleOf(Screen.Games(ListSource.Recent, emptyList())))
    }

    @Test fun b_from_settings_lands_on_tools_and_r1_wraps_to_home() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings, data = FakeUserData(recents = listOf(Recent(tetris))))
        state.load()
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.L1)                                     // Tools (wraps)
        state.select(state.screen.value.toolRows().indexOfFirst { it.kind == ToolKind.SETTINGS }); state.onButton(NxButton.A)
        // By section and kind, not by DOWN presses, so the test does not depend on the category order.
        state.select(state.settingsSections().indexOf(SettingsSection.MAIN_MENU)); state.onButton(NxButton.A)   // Layouts
        assertEquals(SettingsSection.MAIN_MENU, assertIs<Screen.Settings>(state.screen.value).section)
        state.selectLayoutsRow(SettingsKind.MENU_STYLE, MenuCategory.CONSOLES); state.onButton(NxButton.A)   // Consoles: Grid
        repeat(3) { state.onButton(NxButton.B) }                        // Layouts, the Settings menu, the main menu
        advanceUntilIdle()
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.TOOLS, s.category)                    // B from the Settings menu lands on Tools
        assertTrue(state.selection.value.index in 0 until s.rows.size)
        state.onButton(NxButton.R1)                                     // wraps to the first tab: Home
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).category)
    }

    /** System's About group: the rows between its label and the gap before Licenses. */
    private fun aboutGroup(st: AppState): List<SettingsRow> {
        val rows = assertIs<Screen.Settings>(st.screen.value).rows
        val from = rows.indexOfFirst { it.kind == SettingsKind.SECTION && it.label == "About" } + 1
        val to = rows.indexOfFirst { it.kind == SettingsKind.SECTION && it.label == "Licenses" } - 1
        return rows.subList(from, to)
    }

    private val aboutInfo = AppInfo(
        "1.2.3", 7, "abc1234-dirty", "2026-09-30T23:30:00+08:00", "Android 15 (API 35)", "Samsung SM-F956B", "Qualcomm SM8650", 16,
    )

    @Test fun the_about_rows_show_the_injected_info_and_info_rows_do_nothing_on_a() = runTest {
        val gpu = CompletableDeferred<String?>()
        var asked = 0
        val state = AppState(
            AppDeps(
                MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), appInfo = aboutInfo,
                deviceGpu = { asked++; gpu.await() }, compute = UnconfinedTestDispatcher(testScheduler),
            ),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load()
        assertEquals(0, asked)                                          // not at start: only when About shows
        state.openSettings(SettingsSection.SYSTEM)
        val released = formatInstallDate(isoEpochMillis("2026-09-30T23:30:00+08:00")!!)
        fun rows(gpuValue: String) = listOf(
            SettingsRow(SettingsKind.INFO, "NX Redux", "1.2.3 (7)"),
            SettingsRow(SettingsKind.INFO, "Build", "abc1234-dirty"),
            SettingsRow(SettingsKind.INFO, "Released", released),
            SettingsRow(SettingsKind.INFO, "Platform", "Android 15 (API 35)"),
            SettingsRow(SettingsKind.INFO, "Device", "Samsung SM-F956B"),
            SettingsRow(SettingsKind.INFO, "Chip", "Qualcomm SM8650"),
            SettingsRow(SettingsKind.INFO, "GPU", gpuValue),
            SettingsRow(SettingsKind.INFO, "Page size", "16 KB"),
            SettingsRow(SettingsKind.COPY_INFO, "Copy device info"),
        )
        assertEquals(rows("…"), aboutGroup(state))
        gpu.complete("Adreno (TM) 750")
        val s = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(rows("Adreno (TM) 750"), aboutGroup(state))        // the page re-reads its rows when the GPU lands
        state.select(s.rows.indexOfFirst { it.kind == SettingsKind.INFO })
        val on = state.screen.value
        state.onButton(NxButton.A); state.onButton(NxButton.DOWN); state.onButton(NxButton.A)
        assertEquals(on, state.screen.value)
        assertNull(state.error.value)
        assertNull(state.notice.value)
        state.onButton(NxButton.B); state.openSettings(SettingsSection.SYSTEM)
        assertEquals(1, asked)                                          // asked once per AppState
    }

    @Test fun a_failed_gpu_query_reads_unknown() = runTest {
        val state = AppState(
            AppDeps(
                MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), appInfo = aboutInfo,
                deviceGpu = { throw IllegalStateException("no EGL") }, compute = UnconfinedTestDispatcher(testScheduler),
            ),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load()
        state.openSettings(SettingsSection.SYSTEM)
        assertEquals("Unknown", assertIs<Screen.Settings>(state.screen.value).rows.first { it.label == "GPU" }.secondary)
    }

    @Test fun copy_device_info_waits_for_the_gpu_then_copies_and_shows_a_notice() = runTest {
        val gpu = CompletableDeferred<String?>()
        val copied = mutableListOf<String>()
        val state = AppState(
            AppDeps(
                MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), appInfo = aboutInfo,
                deviceGpu = { gpu.await() }, copyText = { copied += it }, compute = UnconfinedTestDispatcher(testScheduler),
            ),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load()
        state.openSettings(SettingsSection.SYSTEM)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals(SettingsKind.COPY_INFO, aboutGroup(state).last().kind)   // no updater: no update row
        state.select(rows.indexOfFirst { it.kind == SettingsKind.COPY_INFO })
        state.onButton(NxButton.A)
        assertEquals(emptyList(), copied)                               // the GPU is still being asked
        gpu.complete("Mali-G715")
        assertEquals(listOf(aboutCopyText(aboutInfo, "Mali-G715")), copied)
        assertEquals("Device info copied", state.notice.value)
        state.openSelected()                                            // a tap on the row copies too
        assertEquals(2, copied.size)
    }

    @Test fun the_main_menu_row_survives_tools_settings_and_a_section() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.onConsoles()
        val systems = assertIs<Screen.Systems>(state.screen.value).systems
        state.select(systems.size - 1)                                  // the last console
        state.onButton(NxButton.R1)                                     // Tools
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)       // Settings
        repeat(2) { state.onButton(NxButton.DOWN) }; state.onButton(NxButton.A)   // Appearance
        state.onButton(NxButton.DOWN)
        repeat(2) { state.onButton(NxButton.B) }
        state.screen.value.toolRows()
        assertEquals(ListSelection(1, 2), state.selection.value)       // Tools: back on Settings
        state.onButton(NxButton.L1)                                     // Consoles: its row survived
        assertEquals(ListSelection(systems.size - 1, systems.size), state.selection.value)
        state.onButton(NxButton.R1)                                     // Tools again: still on Settings
        state.onButton(NxButton.A)
        assertEquals(ListSelection(2, 6), state.selection.value)       // Settings menu: back on Appearance
        state.onButton(NxButton.A)
        assertEquals(SettingsSection.APPEARANCE, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(1, state.selection.value.index)                    // the section's row is remembered too
    }
    @Test fun a_home_repick_from_library_returns_to_library() = runTest {
        val src = FakeRomsSource(dirs = mapOf("Home" to emptyList(), "Home2" to emptyList()))
        val state = state(MemorySettings(ContentRef("Home")), src)
        state.load()
        state.openSettings(SettingsSection.LIBRARY)
        state.onButton(NxButton.DOWN)                                   // past the counts row
        state.onButton(NxButton.A)                                      // Home folder: the platform picker opens
        state.setHomePast(ContentRef("Home2"))
        val s = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.LIBRARY, s.section)
        assertEquals(SettingsRow(SettingsKind.HOME, "Home folder", "Home2"), s.rows[1])
        assertEquals(1, state.selection.value.index)
    }

    @Test fun adding_an_extra_from_library_returns_to_library_with_its_row() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LIBRARY)
        state.select(rowIndex(state, SettingsKind.ADD_EXTRA)); state.onButton(NxButton.A)   // Add ROM folder
        state.addExtra(ContentRef("sd/Loose"))
        val s = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.LIBRARY, s.section)
        assertEquals(SettingsRow(SettingsKind.EXTRA, "ROM Folder: Loose", "ROM folder", extra = settings.extraFolders.single()), s.rows[2])   // right under the home
        assertEquals(3, state.selection.value.index)                    // still on Add ROM folder, now below Loose
        assertEquals("6 games", gamesLine(state))                       // counted after the rescan
        state.addExtra(ContentRef("Home/Roms"))                         // an overlap is refused and stays on Library
        assertTrue(state.error.value!!.contains("overlaps"))
        assertEquals(SettingsSection.LIBRARY, assertIs<Screen.Settings>(state.screen.value).section)
    }

    @Test fun adding_an_extra_from_the_first_setup_offer_lands_on_systems_after_its_summary() = runTest {
        val state = state(MemorySettings())
        state.load()
        state.setHomePast(ContentRef("Home"))
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        state.addExtra(ContentRef("sd/Loose"))
        assertIs<Screen.FolderSummary>(state.screen.value)
        state.select(state.selection.value.count - 1); state.onButton(NxButton.A)                                         // Continue
        assertIs<Screen.Systems>(state.screen.value)
    }

    @Test fun the_select_hint_shows_only_when_the_selected_row_acts() {
        val info = listOf(SettingsRow(SettingsKind.INFO, "NX Redux", "1"), SettingsRow(SettingsKind.INFO, "Platform", "x"))
        assertEquals(listOf(Hint(NxButton.B, "Back")), settingsHints(info, 0))
        assertEquals(listOf(Hint(NxButton.B, "Back")), settingsHints(info, -1))
        val about = info + SettingsRow(SettingsKind.COPY_INFO, "Copy device info")
        assertEquals(listOf(Hint(NxButton.B, "Back")), settingsHints(about, 1))
        assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Copy")), settingsHints(about, 2))
        val appearance = listOf(SettingsRow(SettingsKind.PAD_THEME, "Pad theme"), SettingsRow(SettingsKind.RESET_DEFAULTS, "Reset to defaults"))
        assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Select")), settingsHints(appearance, 1))
    }

    @Test fun an_index_from_version_1_is_rescanned_once_after_the_update() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings).copy(version = 1)))
        val counting = CountingRomsSource(source)
        val st = state(settings, counting, store = store)
        st.load()
        assertIs<Screen.Systems>(st.screen.value)
        assertTrue("listChildren:Home/Roms/Game Boy (GB)" in counting.calls)   // scanned
        assertEquals(LibraryIndex.VERSION, LibraryIndexCodec.decode(store.text!!)!!.version)
        assertEquals(1, store.writes)
        val again = CountingRomsSource(source)
        state(settings, again, store = store).load()
        assertTrue("listChildren:Home/Roms/Game Boy (GB)" !in again.calls)    // once: the next start uses the new index
    }

    // --- RetroAchievements tool ---

    private class RaFixture(val state: AppState, val store: FakeRaStore, val http: FakeRaHttp, val controller: RaController, val hasher: FakeRaHasher = FakeRaHasher())

    /** The Tools setup plus an achievements controller on fakes (no cheat DB controller). */
    private fun TestScope.raFixture(home: RaHomeTree? = null, index: LibraryIndex? = null, hasher: FakeRaHasher = FakeRaHasher(), roms: FakeRomsSource = source): RaFixture {
        val store = FakeRaStore(); val http = FakeRaHttp()
        val settings = MemorySettings(ContentRef("Home"))   // a home, so the main menu (and its Tools tab) is built
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = RaController(if (home == null) fakePlatform(store, http, hasher) else fakePlatform(store, http, hasher, home = home), settings, { index }, scope, now = { 0L })
        return RaFixture(AppState(AppDeps(settings, roms, RecordingRunner(), NoopPicker(), achievements = controller, compute = UnconfinedTestDispatcher(testScheduler)), scope).also { it.load() }, store, http, controller, hasher)
    }
    private fun TestScope.stateWithAchievements(): Pair<AppState, FakeRaStore> = raFixture().let { it.state to it.store }

    // Download all opens its progress page; B leaves it running (the page's row brings it back); Y pauses, X cancels after a confirm.
    @Test fun download_all_opens_its_progress_and_back_leaves_it_running() = runTest {
        val games = listOf("Zelda", "Metroid").map { IndexedGame("home", "d", "$it.gba", "r$it", "f", it) }
        val index = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(IndexedSystem("GBA", "GBA", games = games)))
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = "a".repeat(32); hashes["Metroid.gba"] = "b".repeat(32) }
        val f = raFixture(index = index, hasher = hasher); val state = f.state
        val held = CompletableDeferred<Unit>()
        f.http.responders["gameid"] = { held.await(); RaResponse(200, """{"Success":true,"GameID":0}""") }
        f.store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":1}""")
        state.settings.raToken = "tok"; state.settings.raUsername = "me"
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.select(state.raSettings().rows.indexOfFirst { it.kind == RaSettingKind.DOWNLOAD_ALL }); state.onButton(NxButton.A)
        assertIs<Screen.RaDownload>(state.screen.value)
        assertEquals(listOf("Zelda", "Metroid"), f.controller.download.value!!.items.map { it.name })
        state.onButton(NxButton.B)                                         // back: the download goes on
        assertIs<Screen.RetroAchievements>(state.screen.value)
        assertIs<RaState.Prefetching>(f.controller.state.value)
        val rows = raRows(f.controller.state.value, f.controller.download.value)
        assertEquals(RaAction.DOWNLOAD, rows.last())
        assertEquals("Progress (0/2)", raRowLabel(RaAction.DOWNLOAD, f.controller.download.value))
        state.select(rows.lastIndex); state.onButton(NxButton.A)          // the row: the page again
        assertIs<Screen.RaDownload>(state.screen.value)
        state.onButton(NxButton.Y)                                         // pause (after the game in flight)
        assertEquals(true, assertIs<RaState.Prefetching>(f.controller.state.value).paused)
        state.onButton(NxButton.X)                                         // cancel asks first
        assertEquals(true, assertIs<Screen.RaDownload>(state.screen.value).confirmCancel)
        state.onButton(NxButton.A)
        assertEquals(false, assertIs<Screen.RaDownload>(state.screen.value).confirmCancel)
        assertEquals(RaDownloadPhase.CANCELLED, f.controller.download.value!!.phase)
        assertIs<RaState.Idle>(f.controller.state.value)
        held.complete(Unit)
    }

    // A finished download's page offers Done: A clears it, back on the RetroAchievements page with no Progress row.
    @Test fun done_clears_a_finished_download_and_its_progress_row() = runTest {
        val games = listOf("Zelda").map { IndexedGame("home", "d", "$it.gba", "r$it", "f", it) }
        val index = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(IndexedSystem("GBA", "GBA", games = games)))
        val f = raFixture(index = index, hasher = FakeRaHasher()); val state = f.state   // unreadable: skipped at once
        f.store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":1}""")
        state.settings.raToken = "tok"; state.settings.raUsername = "me"
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.select(state.raSettings().rows.indexOfFirst { it.kind == RaSettingKind.DOWNLOAD_ALL }); state.onButton(NxButton.A)
        advanceUntilIdle()
        assertIs<Screen.RaDownload>(state.screen.value)                  // its page, though it ended at once
        assertEquals(RaDownloadPhase.DONE, f.controller.download.value!!.phase)
        state.onButton(NxButton.B)                                         // back: its row reopens it
        val rows = raRows(f.controller.state.value, f.controller.download.value)
        state.select(rows.indexOf(RaAction.DOWNLOAD)); state.onButton(NxButton.A)
        assertIs<Screen.RaDownload>(state.screen.value)
        state.onButton(NxButton.A)                                         // Done
        assertIs<Screen.RetroAchievements>(state.screen.value)
        assertNull(f.controller.download.value)
        assertTrue(RaAction.DOWNLOAD !in raRows(f.controller.state.value, f.controller.download.value))
    }

    /** An RA fixture with onboarding to do, resumed at RetroAchievements sign-in. */
    private fun TestScope.raOnboarding(): RaFixture {
        val store = FakeRaStore(); val http = FakeRaHttp()
        val settings = MemorySettings(ContentRef("Home")).apply { onboardingDone = false; onboardingStep = OnboardingStep.RA_SIGN_IN.name }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = RaController(fakePlatform(store, http), settings, { null }, scope, now = { 0L })
        return RaFixture(AppState(AppDeps(settings, source, RecordingRunner(), NoopPicker(), achievements = controller, compute = UnconfinedTestDispatcher(testScheduler)), scope).also { it.load() }, store, http, controller)
    }

    // a rotation or a controller while the sign-in prompt is open keeps the prompt and what was typed.
    @Test fun a_device_change_keeps_the_sign_in_prompt_and_its_typing() = runTest {
        val f = raOnboarding(); val state = f.state
        state.select(0); state.onButton(NxButton.A)
        state.onboardingDraftUser("someone")
        state.onPadContext(portrait = false, controller = true)
        val s = assertIs<Screen.Onboarding>(state.screen.value)
        assertTrue(s.prompt)
        assertEquals("someone", s.draftUser)
    }

    // Onboarding's sign-in: a failed one stays with the error; a good one goes on to the download offer, which starts it.
    @Test fun onboarding_sign_in_failure_stays_and_success_offers_the_download() = runTest {
        val f = raOnboarding(); val state = f.state
        assertEquals(OnboardingStep.RA_SIGN_IN, assertIs<Screen.Onboarding>(state.screen.value).step)
        f.http.on("login2", body = """{"Success":false,"Error":"Bad password"}""")
        state.select(0); state.onButton(NxButton.A)
        assertTrue(assertIs<Screen.Onboarding>(state.screen.value).prompt)
        state.onboardingDraftUser("me"); state.onboardingDraftPassword("wrong"); state.onButton(NxButton.A)
        advanceUntilIdle()
        if (state.error.value != null) state.onButton(NxButton.A)          // the failure's own message, dismissed
        val failed = assertIs<Screen.Onboarding>(state.screen.value)
        assertEquals(OnboardingStep.RA_SIGN_IN, failed.step)
        assertFalse(failed.prompt)
        assertTrue(failed.details.any { "Bad password" in it }, "${failed.details}")
        f.http.on("login2", body = """{"Success":true,"User":"Me","Token":"tok","Score":1,"SoftcoreScore":5}""")
        state.select(0); state.onButton(NxButton.A)
        state.onboardingDraftUser("me"); state.onboardingDraftPassword("right"); state.onButton(NxButton.A)
        advanceUntilIdle()
        val offer = assertIs<Screen.Onboarding>(state.screen.value)
        assertEquals(OnboardingStep.RA_DOWNLOAD, offer.step)
        assertEquals(listOf("Download now", "Skip"), offer.rows.map { it.label })
    }

    // The setup sign-in prompt is a text box: the d-pad goes to its fields (DOWN to the password), not to the page.
    @Test fun the_onboarding_sign_in_prompt_takes_the_d_pad_as_text_entry() = runTest {
        val state = raOnboarding().state
        assertFalse(state.textEntryOpen)
        state.select(0); state.onButton(NxButton.A)
        assertTrue(state.textEntryOpen)
        state.onButton(NxButton.B)
        assertFalse(state.textEntryOpen)
    }

    // A sign-in still running when the user skips on still counts: once it succeeds, the download offer follows.
    @Test fun a_sign_in_that_ends_after_skip_still_offers_the_download() = runTest {
        val f = raOnboarding(); val state = f.state
        val answer = kotlinx.coroutines.CompletableDeferred<Unit>()
        f.http.responders["login2"] = { answer.await(); com.nxredux.mobile.achievements.RaResponse(200, """{"Success":true,"User":"Me","Token":"tok","Score":1,"SoftcoreScore":5}""") }
        state.select(0); state.onButton(NxButton.A)
        state.onboardingDraftUser("me"); state.onboardingDraftPassword("right"); state.onButton(NxButton.A)
        runCurrent()
        state.select(1); state.onButton(NxButton.A)                          // Skip while it signs in
        val next = assertIs<Screen.Onboarding>(state.screen.value).step
        assertTrue(next != OnboardingStep.RA_SIGN_IN && next != OnboardingStep.RA_DOWNLOAD)
        answer.complete(Unit); advanceUntilIdle()
        assertEquals(next, assertIs<Screen.Onboarding>(state.screen.value).step)   // the page stays
        state.onButton(NxButton.B)                                           // the download offer is a step now
        assertEquals(OnboardingStep.RA_DOWNLOAD, assertIs<Screen.Onboarding>(state.screen.value).step)
    }

    @Test fun onboarding_b_closes_the_sign_in_prompt() = runTest {
        val state = raOnboarding().state
        state.select(0); state.onButton(NxButton.A)
        state.onButton(NxButton.B)
        val s = assertIs<Screen.Onboarding>(state.screen.value)
        assertFalse(s.prompt); assertEquals(OnboardingStep.RA_SIGN_IN, s.step)
    }
    private fun TestScope.stateWithAchievementsHttp(): Pair<AppState, FakeRaHttp> = raFixture().let { it.state to it.http }

    @Test fun tools_lists_retroachievements_by_name_and_opens_its_page() = runTest {
        val (state, _) = stateWithAchievements()
        state.openTools()
        assertEquals(listOf("Cheat Database", "RetroAchievements", "Settings"), state.screen.value.toolRows().map { it.label })
        assertTrue(state.screen.value.toolRows().all { it.secondary == null })   // titles only
        state.select(1); state.openSelected()
        assertIs<Screen.RetroAchievements>(state.screen.value)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()   // Settings
        assertIs<Screen.RaSettings>(state.screen.value)
        assertTrue(state.back()); assertIs<Screen.RetroAchievements>(state.screen.value)
        assertEquals(2, state.selection.value.index)   // Settings stays highlighted
        assertTrue(state.back()); state.screen.value.toolRows()
        assertEquals(1, state.selection.value.index)   // RetroAchievements stays highlighted
    }

    /** Two cached games (Alpha, Beta), two achievements each; on the RetroAchievements page. */
    private fun TestScope.raBrowser(): AppState {
        val (state, store) = stateWithAchievements()
        for ((c, t) in listOf('a' to "Alpha", 'b' to "Beta")) {
            store.put(RaPaths.sets(c.toString().repeat(32)), setsJson(c.code, title = t, achievements = """{"ID":1,"Title":"First","Points":5,"BadgeName":"1"},{"ID":2,"Title":"Second","Points":5,"BadgeName":"2"}"""))
        }
        openRa(state)
        assertIs<Screen.RetroAchievements>(state.screen.value)
        return state
    }

    @Test fun a_on_a_game_page_opens_the_achievement_on_every_window() = runTest {
        val state = raBrowser()
        state.openSelected(); state.openSelected()   // Achievements → Alpha
        assertIs<Screen.RaGame>(state.screen.value)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)   // no list + detail split any more: A opens the detail
        assertEquals("Second", assertIs<Screen.RaAchievement>(state.screen.value).achievement.title)
    }

    @Test fun the_column_browser_left_pane_is_the_level_each_screen_came_from() = runTest {
        val state = raBrowser()
        assertNull(state.raParent(state.screen.value))                                   // the page: whole
        state.openSelected()                                                              // Achievements
        val games = assertIs<Screen.RaGames>(state.screen.value)
        assertEquals(RaParent(Screen.RetroAchievements(), RaAction.entries.indexOf(RaAction.ACHIEVEMENTS)), state.raParent(games))
        state.onButton(NxButton.DOWN); state.openSelected()                               // Beta
        val beta = assertIs<Screen.RaGame>(state.screen.value)
        assertEquals(RaParent(games, 1), state.raParent(beta))                            // the games list, Beta lit
        state.onButton(NxButton.Y); state.onButton(NxButton.DOWN); state.openSelected()   // locked only, Second
        val second = assertIs<Screen.RaAchievement>(state.screen.value)
        val parent = assertNotNull(state.raParent(second))
        assertTrue(assertIs<Screen.RaGame>(parent.screen).lockedOnly); assertEquals(1, parent.row)   // its list, filter and row
        // B steps back one level each time, the columns shifting right; the page is whole again at the end.
        assertTrue(state.back()); assertEquals(beta.copy(lockedOnly = true), state.screen.value); assertEquals(1, state.selection.value.index)
        assertTrue(state.back()); assertIs<Screen.RaGames>(state.screen.value); assertEquals(1, state.selection.value.index)
        assertTrue(state.back()); assertIs<Screen.RetroAchievements>(state.screen.value); assertNull(state.raParent(state.screen.value))
        // LEFT never leaves the focused pane.
        state.openSelected(); state.onButton(NxButton.LEFT); assertIs<Screen.RaGames>(state.screen.value)
        state.openSelected(); state.onButton(NxButton.LEFT); assertIs<Screen.RaGame>(state.screen.value)
        state.openSelected(); state.onButton(NxButton.LEFT); assertIs<Screen.RaAchievement>(state.screen.value)
    }

    @Test fun one_tap_on_the_dimmed_left_pane_opens_that_row_in_place() = runTest {
        val state = raBrowser()
        state.openSelected(); state.openSelected()                                        // Achievements → Alpha
        val games = assertNotNull(state.raParent(state.screen.value)).screen
        val onGames = { i: Int -> state.raParentTap(games, i) }
        val seen = mutableListOf<Screen>()
        val watch = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.screen.collect { seen += it } }
        nxTap(1, selected = false, opens = true, onGames, onGames)                         // Beta on the left
        advanceUntilIdle()
        assertEquals("Beta", assertIs<Screen.RaGame>(state.screen.value).game.title)
        // Straight from Alpha's page to Beta's: the games list never shows on the right in between.
        assertEquals(listOf("Alpha", "Beta"), seen.map { assertIs<Screen.RaGame>(it).game.title })
        assertEquals(1, assertNotNull(state.raParent(state.screen.value)).row)             // now lit on the left
        nxTap(1, selected = true, opens = true, onGames, onGames)                          // the lit one: already open
        assertEquals("Beta", assertIs<Screen.RaGame>(state.screen.value).game.title)
        state.openSelected()                                                              // First
        val list = assertNotNull(state.raParent(state.screen.value)).screen
        val onList = { i: Int -> state.raParentTap(list, i) }
        seen.clear()
        nxTap(1, selected = false, opens = true, onList, onList)                           // Second on the left
        assertEquals("Second", assertIs<Screen.RaAchievement>(state.screen.value).achievement.title)
        assertEquals(listOf("Second"), seen.map { assertIs<Screen.RaAchievement>(it).achievement.title })   // no game page between
        watch.cancel()
        assertEquals(1, assertNotNull(state.raParent(state.screen.value)).row)
        onGames(0); assertIs<Screen.RaAchievement>(state.screen.value)                     // a pane no longer beside it: nothing
        state.back(); state.back()                                                        // the games list
        val page = { i: Int -> state.raParentTap(Screen.RetroAchievements(), i) }
        nxTap(raPageRow(Screen.RaSettings(emptyList()))!!, selected = false, opens = true, page, page)   // Settings on the page
        assertIs<Screen.RaSettings>(state.screen.value)                                   // RetroAchievements Settings, split
        nxTap(0, selected = false, opens = true, page, page)                               // Achievements again
        advanceUntilIdle()
        assertIs<Screen.RaGames>(state.screen.value)
    }

    @Test fun achievements_browser_shows_cached_games_then_their_achievements() = runTest {
        val gba = "Game Boy Advance (GBA)"; val badgeDir = "home/Roms/$gba/.retroachievements/badges/42"
        val tree = RaHomeTree(FakeRomsSource(mapOf(
            "home" to listOf("Roms"), "home/Roms" to listOf(gba), "home/Roms/$gba" to listOf(".retroachievements"),
            "home/Roms/$gba/.retroachievements" to listOf("badges"), "home/Roms/$gba/.retroachievements/badges" to listOf("42"), badgeDir to listOf("1.png"),
        ))) { ContentRef("home") }
        val (state, store) = raFixture(tree).let { it.state to it.store }
        val h = "a".repeat(32)
        store.put(RaPaths.sets(h), setsJson(42, title = "Game", achievements = """{"ID":1,"Title":"First","Points":5,"BadgeName":"1"}"""))
        store.put(RaPaths.game(h), """{"tag":"GBA","source":"home","dir":"Roms/$gba","file":"g.gba","title":"Game"}""")
        openRa(state); state.openSelected()   // Achievements
        val games = assertIs<Screen.RaGames>(state.screen.value); assertEquals("Game", games.games.single().title)
        state.openSelected()
        val game = assertIs<Screen.RaGame>(state.screen.value); assertEquals("First", game.achievements.single().title)
        assertEquals(mapOf("1.png" to "$badgeDir/1.png"), game.badges)   // the game's badge folder, listed
        state.onButton(NxButton.Y); assertTrue((state.screen.value as Screen.RaGame).lockedOnly)
        assertEquals("Showing locked achievements only", state.notice.value)   // the filter says what it did (touch: an icon only)
        state.onButton(NxButton.Y); assertEquals("Showing all achievements", state.notice.value)
        state.onButton(NxButton.Y); assertTrue((state.screen.value as Screen.RaGame).lockedOnly)
        state.openSelected(); assertEquals(game.badges, assertIs<Screen.RaAchievement>(state.screen.value).badges)
        assertTrue(state.back()); assertTrue(state.back()); assertIs<Screen.RaGames>(state.screen.value)
        assertTrue(state.back()); assertIs<Screen.RetroAchievements>(state.screen.value)
        assertEquals(0, state.selection.value.index)   // Achievements stays highlighted
    }

    @Test fun the_games_list_reloads_on_return_and_keeps_its_row() = runTest {
        val (state, store) = stateWithAchievements()
        for (c in listOf('a', 'b')) {
            store.put(RaPaths.sets(c.toString().repeat(32)), setsJson(c.code, title = "Game $c", achievements = """{"ID":1,"Title":"First","Points":5,"BadgeName":"1"}"""))
        }
        openRa(state); state.openSelected()
        state.onButton(NxButton.DOWN); state.openSelected()
        assertEquals("Game b", assertIs<Screen.RaGame>(state.screen.value).game.title)
        store.put(RaPaths.sets("c".repeat(32)), setsJson(99, title = "Game c", achievements = """{"ID":1,"Title":"First","Points":5,"BadgeName":"1"}"""))
        assertTrue(state.back())
        assertEquals(3, assertIs<Screen.RaGames>(state.screen.value).games.size)   // reloaded
        assertEquals(1, state.selection.value.index)
    }

    // Game data cached by a RetroAchievements job (Download all, here a sign-out's reset) shows in the menus'
    // game info as the job ends, not only after a game is played.
    @Test fun a_finished_retroachievements_job_refreshes_the_game_info() = runTest {
        val f = raFixture()
        f.state.settings.raUsername = "me"   // signed in once: the progress reads under the account's name
        val hash = "a".repeat(32)
        f.store.putGame('a')
        RaGamePointers.write(f.store, hash, RaGamePointer("GB", "home", "Roms/Game Boy (GB)", "Tetris.gb", "Tetris"))
        val path = GamePath.of(GameRef("home", "Roms/Game Boy (GB)", "Tetris.gb"))
        assertNull(f.state.gameInfo.value[path]?.ra)   // cached after the menu's last read
        f.controller.signOut(); advanceUntilIdle()
        assertEquals(RaProgress(0, 1, "First"), f.state.gameInfo.value[path]?.ra)
    }

    // --- The achievements panel over a game list (the trophy icon, X) ---

    /** A fixture signed in as "me" with Tetris.gb's two achievements cached, the game info read. */
    private fun TestScope.panelFixture(signedIn: Boolean = true, index: LibraryIndex? = null, hasher: FakeRaHasher = FakeRaHasher(), roms: FakeRomsSource = source): RaFixture {
        val f = raFixture(index = index, hasher = hasher, roms = roms)
        if (signedIn) { f.state.settings.raUsername = "me"; f.state.settings.raToken = "t" }
        val hash = "a".repeat(32)
        f.store.put(RaPaths.sets(hash), setsJson(1, title = "Tetris", achievements = """{"ID":1,"Title":"First","Points":5,"BadgeName":"1"},{"ID":2,"Title":"Second","Points":10,"BadgeName":"2"}"""))
        kotlinx.coroutines.runBlocking { RaGamePointers.write(f.store, hash, RaGamePointer("GB", "home", "Roms/Game Boy (GB)", "Tetris.gb", "Tetris")) }
        f.state.onForeground(); advanceUntilIdle()   // reads the game info (and the panel's hashes) again
        return f
    }

    private fun AppState.openConsole(tag: String) = openSystem(assertIs<Screen.Systems>(screen.value).systems.first { it.tag == tag })

    @Test fun x_opens_the_panel_on_the_highlighted_games_achievements() = runTest {
        val f = panelFixture()
        f.state.openConsole("GB")
        f.state.onButton(NxButton.X); advanceUntilIdle()
        val p = assertNotNull(f.state.raPanel.value)
        assertEquals(listOf("First", "Second"), p.visible.map { it.title })
        assertEquals("0 of 2", p.progress)
        f.state.onButton(NxButton.DOWN)
        assertEquals(1, f.state.raPanel.value?.row)
        assertIs<Screen.Games>(f.state.screen.value)   // the list stays put underneath
        f.state.onButton(NxButton.A)
        assertEquals(true, f.state.raPanel.value?.detail)
        assertTrue(f.state.back())   // the details' back: the list again
        assertEquals(false, f.state.raPanel.value?.detail)
        f.state.onButton(NxButton.Y)   // no filter: Y does nothing here
        assertEquals(2, f.state.raPanel.value?.visible?.size)
        f.state.onButton(NxButton.B)   // closes the panel, not the list
        assertNull(f.state.raPanel.value)
        assertIs<Screen.Games>(f.state.screen.value)
        f.state.toggleRaPanel(); advanceUntilIdle()   // the trophy icon
        assertNotNull(f.state.raPanel.value)
        f.state.openRaPanelRow(1)   // a tap on an achievement: its details
        assertEquals(1 to true, f.state.raPanel.value?.let { it.row to it.detail })
        f.state.onButton(NxButton.X)
        assertNull(f.state.raPanel.value)
    }

    // --- The details pane's achievements (the main menu on the second display) ---

    @Test fun the_details_show_the_highlighted_games_cached_achievements() = runTest {
        val f = panelFixture()
        f.state.onDualMenu(true)
        f.state.openConsole("GB"); runCurrent()   // no time advanced: cached data is read at once
        assertEquals(listOf("First", "Second"), f.state.gameDetailRa.value?.visible?.map { it.title })
    }

    private val threeGbaRoms = FakeRomsSource(dirs = mapOf(
        "Home" to listOf("Roms"), "Home/Roms" to listOf("Game Boy Advance (GBA)"),
        "Home/Roms/Game Boy Advance (GBA)" to listOf("A.gba", "B.gba", "C.gba"),
    ))
    private val threeGbaIndex = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(IndexedSystem("GBA", "GBA", games =
        listOf("A", "B", "C").map { IndexedGame("home", "Roms/Game Boy Advance (GBA)", "$it.gba", "r$it", "f", it) })))
    private fun threeGbaHasher() = FakeRaHasher().apply { hashes["A.gba"] = "1".repeat(32); hashes["B.gba"] = "2".repeat(32); hashes["C.gba"] = "3".repeat(32) }

    @Test fun leaving_the_list_or_dual_mode_within_the_rest_downloads_nothing() = runTest {
        val f = panelFixture(index = threeGbaIndex, hasher = threeGbaHasher(), roms = threeGbaRoms)
        f.http.on("gameid", body = """{"Success":true,"GameID":0}""")
        f.state.onDualMenu(true)
        f.state.openConsole("GBA"); runCurrent()
        f.hasher.calls.clear()
        f.state.back(); advanceTimeBy(600); runCurrent()
        assertTrue(f.hasher.calls.isEmpty())
        f.state.openConsole("GBA"); runCurrent()
        f.state.onDualMenu(false); advanceTimeBy(600); runCurrent()
        assertTrue(f.hasher.calls.isEmpty())
        assertNull(f.state.gameDetailRa.value)
    }

    @Test fun a_failed_details_download_is_tried_again_when_the_highlight_rests_on_it_again() = runTest {
        val f = panelFixture(index = threeGbaIndex, hasher = threeGbaHasher(), roms = threeGbaRoms)
        f.http.offline = true
        f.state.onDualMenu(true)
        f.state.openConsole("GBA"); runCurrent()
        f.hasher.calls.clear()
        advanceTimeBy(600); runCurrent()
        assertEquals(listOf("A.gba"), f.hasher.calls.toList())
        assertIs<RaPanelContent.Failed>(f.state.gameDetailRa.value?.content)
        advanceTimeBy(2000); runCurrent()
        assertEquals(1, f.hasher.calls.size)   // no retry loop while it rests
        f.http.offline = false
        f.http.on("gameid", body = """{"Success":true,"GameID":0}""")
        f.state.onButton(NxButton.DOWN); f.state.onButton(NxButton.UP)
        advanceTimeBy(600); runCurrent()
        assertEquals(listOf("A.gba", "A.gba"), f.hasher.calls.toList())
    }

    @Test fun scrolling_past_games_downloads_only_where_the_highlight_rests() = runTest {
        val games = listOf("A", "B", "C").map { IndexedGame("home", "Roms/Game Boy Advance (GBA)", "$it.gba", "r$it", "f", it) }
        val index = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(IndexedSystem("GBA", "GBA", games = games)))
        val hasher = FakeRaHasher().apply { hashes["A.gba"] = "1".repeat(32); hashes["B.gba"] = "2".repeat(32); hashes["C.gba"] = "3".repeat(32) }
        val roms = threeGbaRoms
        val f = panelFixture(index = index, hasher = hasher, roms = roms)
        f.http.on("gameid", body = """{"Success":true,"GameID":0}""")
        f.state.onDualMenu(true)
        f.state.openConsole("GBA"); advanceUntilIdle()
        f.hasher.calls.clear()
        f.state.onButton(NxButton.DOWN); advanceTimeBy(100)
        f.state.onButton(NxButton.DOWN); advanceTimeBy(100)
        assertTrue(f.hasher.calls.isEmpty())   // still scrolling: nothing requested
        advanceTimeBy(600); advanceUntilIdle()
        assertEquals(listOf("C.gba"), f.hasher.calls.toList())
    }

    @Test fun signed_out_details_have_no_achievements() = runTest {
        val f = panelFixture(signedIn = false)
        f.state.onDualMenu(true)
        f.state.openConsole("GB"); advanceUntilIdle()
        assertNull(f.state.gameDetailRa.value)
    }

    @Test fun the_details_clear_off_the_game_list() = runTest {
        val f = panelFixture()
        f.state.onDualMenu(true)
        f.state.openConsole("GB"); advanceUntilIdle()
        assertNotNull(f.state.gameDetailRa.value)
        assertTrue(f.state.back())
        assertNull(f.state.gameDetailRa.value)
    }

    /** The library index with Mario.gba (the test library's GBA game), hashed as [MARIO_HASH]. */
    private val marioIndex = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(
        IndexedSystem("GBA", "GBA", games = listOf(IndexedGame("home", "Roms/Game Boy Advance (GBA)", "Mario.gba", "rMario", "f", "Mario"))),
    ))
    private val MARIO_HASH = "c".repeat(32)
    private fun marioHasher() = FakeRaHasher().apply { hashes["Mario.gba"] = MARIO_HASH }

    @Test fun the_panel_follows_the_selection_and_shows_why_a_game_has_none() = runTest {
        val f = panelFixture(index = marioIndex, hasher = marioHasher())
        f.http.on("gameid", body = """{"Success":true,"GameID":0}""")
        f.state.openConsole("GBA")   // Mario.gba: never played, nothing downloaded, unknown to RetroAchievements
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertEquals(RaPanelContent.Unrecognised(MARIO_HASH), f.state.raPanel.value?.content)
        assertTrue(f.state.back())
        assertNull(f.state.raPanel.value)
        assertTrue(f.state.back())   // now B leaves the list
        f.state.openConsole("GB")
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertIs<RaPanelContent.Game>(f.state.raPanel.value?.content)
        f.state.onButton(NxButton.RIGHT)   // one game here: RIGHT stays on it, the panel too
        assertIs<RaPanelContent.Game>(f.state.raPanel.value?.content)
        f.state.back(); f.state.back()   // leaving the list closes the panel
        assertNull(f.state.raPanel.value)
    }

    // A game with no cached data downloads when the panel opens on it (in the background), then shows its achievements.
    @Test fun the_panel_downloads_a_game_it_has_no_data_for() = runTest {
        val f = panelFixture(index = marioIndex, hasher = marioHasher())
        val held = CompletableDeferred<Unit>()
        f.http.responders["gameid"] = { held.await(); RaResponse(200, """{"Success":true,"GameID":7}""") }
        f.http.on("achievementsets", body = setsJson(7, title = "Mario", achievements = """{"ID":9,"Title":"Jump","Points":5,"BadgeName":"9"}"""))
        f.http.on("unlocks", body = """{"Success":true,"GameID":7,"HardcoreMode":false,"UserUnlocks":[]}""")
        f.state.openConsole("GBA")
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertEquals(RaPanelContent.Downloading, f.state.raPanel.value?.content)
        held.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("Jump"), f.state.raPanel.value?.visible?.map { it.title })
        val posts = f.http.posts.size
        f.state.onButton(NxButton.X); f.state.onButton(NxButton.X); advanceUntilIdle()   // closed and opened again: cached now
        assertEquals(listOf("Jump"), f.state.raPanel.value?.visible?.map { it.title })
        assertEquals(posts, f.http.posts.size)
    }

    // A file RetroAchievements does not know is marked with why: opening the panel again downloads nothing.
    @Test fun an_unrecognised_game_is_not_downloaded_again() = runTest {
        val f = panelFixture(index = marioIndex, hasher = marioHasher())
        f.http.on("gameid", body = """{"Success":true,"GameID":0}""")
        f.state.openConsole("GBA")
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertEquals(RaPanelContent.Unrecognised(MARIO_HASH), f.state.raPanel.value?.content)
        val posts = f.http.posts.size; val hashes = f.hasher.calls.size
        repeat(2) { f.state.onButton(NxButton.X); f.state.onButton(NxButton.X); advanceUntilIdle() }
        assertEquals(RaPanelContent.Unrecognised(MARIO_HASH), f.state.raPanel.value?.content)
        assertEquals(posts to hashes, f.http.posts.size to f.hasher.calls.size)
    }

    // A file that will not hash is marked too; a passing failure (no connection) is tried again on the next open.
    @Test fun an_unreadable_game_stays_marked_and_a_lost_connection_retries() = runTest {
        val f = panelFixture(index = marioIndex, hasher = FakeRaHasher().apply { hashes["Mario.gba"] = null })
        f.state.openConsole("GBA")
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertEquals(RaPanelContent.Unreadable, f.state.raPanel.value?.content)
        f.state.onButton(NxButton.X); f.state.onButton(NxButton.X); advanceUntilIdle()
        assertEquals(1, f.hasher.calls.size)

        val g = panelFixture(index = marioIndex, hasher = marioHasher())
        g.http.offline = true
        g.state.openConsole("GBA")
        g.state.onButton(NxButton.X); advanceUntilIdle()
        assertEquals(RaPanelContent.Failed("no connection"), g.state.raPanel.value?.content)
        g.http.offline = false
        g.http.on("gameid", body = """{"Success":true,"GameID":0}""")
        g.state.onButton(NxButton.X); g.state.onButton(NxButton.X); advanceUntilIdle()
        assertEquals(RaPanelContent.Unrecognised(MARIO_HASH), g.state.raPanel.value?.content)
    }

    // Signed out there is no panel: no icon or X hint, X and the trophy do nothing, and a game's menu does not offer it.
    @Test fun signed_out_a_game_list_has_no_achievements_panel() = runTest {
        val f = panelFixture(signedIn = false)
        f.state.openConsole("GB")
        assertFalse(f.state.hasAchievements)
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertNull(f.state.raPanel.value)
        f.state.toggleRaPanel(); advanceUntilIdle()
        assertNull(f.state.raPanel.value)
        f.state.settings.gameListAchievementsPanel = false   // hidden from the list: still not in the menu signed out
        f.state.onButton(NxButton.MENU); advanceUntilIdle()
        assertTrue(assertIs<Screen.Games>(f.state.screen.value).menu?.items.orEmpty().none { it.label == "Achievements" })
    }

    // Layouts → Layout options and Achievements panel. Hidden, the Layout dropdown does not open from a game list; the panel's icon and hint go, but X still opens it and a game's menu offers it.
    @Test fun layouts_hide_a_game_lists_layout_options_and_the_panels_icon() = runTest {
        val f = panelFixture()
        val settings = f.state.settings
        f.state.openSettings(SettingsSection.LAYOUT_GAME_LISTS)
        val labels = assertIs<Screen.Settings>(f.state.screen.value).rows.map { it.label }
        assertTrue("Layout options" in labels && "Achievements panel" in labels)
        f.state.selectLayoutsRow(SettingsKind.LAYOUT_OPTIONS); f.state.onButton(NxButton.A)
        assertFalse(settings.gameListLayoutOptions)
        f.state.selectLayoutsRow(SettingsKind.ACHIEVEMENTS_PANEL); f.state.onButton(NxButton.RIGHT)
        assertFalse(settings.gameListAchievementsPanel)
        while (f.state.screen.value !is Screen.Systems) f.state.back()
        f.state.openConsole("GB")
        f.state.toggleLayoutMenu()
        assertNull(assertIs<Screen.Games>(f.state.screen.value).layoutMenu)
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertNotNull(f.state.raPanel.value)
        f.state.onButton(NxButton.X)
        f.state.openContextMenu(0)
        val menu = assertNotNull(assertIs<Screen.Games>(f.state.screen.value).menu)
        val at = menu.items.indexOfFirst { it.kind == ContextKind.ACHIEVEMENTS }
        assertEquals(menu.items.lastIndex, at)
        f.state.menuSelect(at); f.state.menuOpen(); advanceUntilIdle()
        assertNull(assertIs<Screen.Games>(f.state.screen.value).menu)
        assertIs<RaPanelContent.Game>(f.state.raPanel.value?.content)
        // Shown (the default): the game's menu has no Achievements item.
        f.state.closeRaPanel(); settings.gameListAchievementsPanel = true
        f.state.openContextMenu(0)
        assertTrue(assertNotNull(assertIs<Screen.Games>(f.state.screen.value).menu).items.none { it.kind == ContextKind.ACHIEVEMENTS })
    }

    // --- The loading overlay over a slow screen load (ScreenLoader) ---

    private fun FakeRaStore.putGame(c: Char) =
        put(RaPaths.sets(c.toString().repeat(32)), setsJson(c.code, title = "Game $c", achievements = """{"ID":1,"Title":"First","Points":5,"BadgeName":"1"}"""))

    @Test fun the_loading_overlay_shows_after_its_grace_period_and_clears_when_the_games_list_opens() = runTest {
        val (state, store) = stateWithAchievements()
        store.putGame('a')
        val gate = CompletableDeferred<Unit>()
        store.beforeList = { gate.await() }
        openRa(state); state.openSelected()   // Achievements: the read waits on the gate
        assertIs<Screen.RetroAchievements>(state.screen.value)
        advanceTimeBy(100); assertFalse(state.loading.value)   // a fast load never flickers the overlay
        advanceTimeBy(100); assertTrue(state.loading.value)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A); state.openSelected()   // ignored while loading
        assertEquals(0, state.selection.value.index)
        gate.complete(Unit); runCurrent()
        assertIs<Screen.RaGames>(state.screen.value)
        assertFalse(state.loading.value)
    }

    @Test fun a_failed_load_hides_the_overlay_and_shows_the_error() = runTest {
        val (state, store) = stateWithAchievements()
        val gate = CompletableDeferred<Unit>()
        openRa(state)
        store.beforeList = { gate.await() }; store.failList = true   // after the Tools status read
        state.openSelected()
        advanceTimeBy(200); assertTrue(state.loading.value)
        gate.complete(Unit); runCurrent()
        assertFalse(state.loading.value)
        assertEquals("Couldn't read achievements", state.error.value)
        assertIs<Screen.RetroAchievements>(state.screen.value)
    }

    @Test fun b_cancels_a_load_and_stays_on_the_screen_underneath() = runTest {
        val (state, store) = stateWithAchievements()
        store.putGame('a')
        val gate = CompletableDeferred<Unit>()
        store.beforeList = { gate.await() }
        openRa(state); state.openSelected()
        advanceTimeBy(200); assertTrue(state.loading.value)
        state.onButton(NxButton.B)
        assertFalse(state.loading.value)
        gate.complete(Unit); runCurrent()
        assertIs<Screen.RetroAchievements>(state.screen.value)   // the abandoned read never lands
        assertNull(state.error.value)
        state.onButton(NxButton.B); state.screen.value.toolRows()   // the next B is an ordinary back
    }

    @Test fun a_games_achievements_load_under_the_overlay_and_back_cancels_it() = runTest {
        val (state, store) = stateWithAchievements()
        store.putGame('a')
        openRa(state); state.openSelected()
        assertIs<Screen.RaGames>(state.screen.value)
        val gate = CompletableDeferred<Unit>()
        store.beforeRead = { gate.await() }
        state.openSelected()
        advanceTimeBy(200); assertTrue(state.loading.value)
        assertTrue(state.back())   // the system back gesture cancels like B
        assertFalse(state.loading.value)
        assertIs<Screen.RaGames>(state.screen.value)
        store.beforeRead = {}; gate.complete(Unit); runCurrent()
        assertIs<Screen.RaGames>(state.screen.value)
        state.openSelected()   // no wait now: opens at once, the overlay never shows
        assertIs<Screen.RaGame>(state.screen.value); assertFalse(state.loading.value)
    }

    @Test fun sync_row_needs_sign_in() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.openSelected()   // Sync now, not signed in
        assertEquals("Sign in first", state.error.value)
    }

    @Test fun ra_settings_rows_reflect_the_settings_and_cycle_values() = runTest {
        val (state, _) = stateWithAchievements()
        state.settings.raToken = "tok"; state.settings.raUsername = "me"
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        val s = assertIs<Screen.RaSettings>(state.screen.value)
        assertEquals(listOf("Sign out", "Status", "Enable achievements", "Download all game data", "Show notifications", "Notification duration", "Progress duration", "Achievement sort order", "Reset account data", "Erase all achievement data", "Reset settings to defaults"), s.rows.map { it.label })
        assertEquals("Signed in as me", s.rows[1].secondary); assertEquals("Off", s.rows[2].secondary); assertTrue(s.rows[2].enabled); assertTrue(s.rows[3].enabled)
        assertEquals("3 s", s.rows[5].secondary); assertEquals("1 s", s.rows[6].secondary); assertEquals("Unlocked first", s.rows[7].secondary)
        assertEquals(1, state.selection.value.index)   // opens on Status, not Sign out
        state.onButton(NxButton.DOWN); state.openSelected()   // Enable → On
        assertEquals("On", state.raSettings().rows[2].secondary); assertTrue(state.settings.raEnabled)
        assertEquals(listOf(Hint(NxButton.A, "Select"), Hint(NxButton.B, "Back")), raSettingsHints(state.raSettings().rows[2]))
        repeat(4) { state.onButton(NxButton.DOWN) }; state.onButton(NxButton.RIGHT)   // Progress duration 1 → 2
        assertEquals("2 s", state.raSettings().rows[6].secondary)
        repeat(2) { state.onButton(NxButton.LEFT) }   // → Off
        assertEquals("Off", state.raSettings().rows[6].secondary); assertEquals(0, state.settings.raProgressSeconds)
    }
    @Test fun signed_out_the_enable_and_download_rows_are_disabled() = runTest {
        val (state, store) = stateWithAchievements()
        store.put(RaPaths.sets("a".repeat(32)), setsJson(1))
        state.settings.raEnabled = true   // a stale flag from before a sign-out still reads Off
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        val s = state.raSettings()
        assertEquals(listOf("Sign in", "Status", "Enable achievements", "Download all game data"), s.rows.take(4).map { it.label })
        assertEquals("Off", s.rows[2].secondary); assertFalse(s.rows[2].enabled); assertFalse(s.rows[3].enabled); assertEquals("1 games cached", s.rows[3].secondary)
        assertTrue(s.rows.drop(4).all { it.enabled })
        repeat(2) { state.onButton(NxButton.DOWN) }; state.openSelected()   // Enable: nothing
        assertEquals(s.rows, state.raSettings().rows); assertTrue(state.settings.raEnabled)
        assertEquals(listOf(Hint(NxButton.B, "Back")), raSettingsHints(s.rows[2]))
        state.onButton(NxButton.DOWN); state.openSelected()   // Download all: nothing, the page stays
        assertIs<Screen.RaSettings>(state.screen.value); assertIs<com.nxredux.mobile.achievements.RaState.Idle>(state.achievements!!.state.value)
        assertEquals(listOf(Hint(NxButton.B, "Back")), raSettingsHints(s.rows[3]))
    }
    private fun AppState.raSettings(): Screen.RaSettings = assertIs<Screen.RaSettings>(screen.value)
    @Test fun sign_in_opens_one_modal_and_signs_in_with_both_entries() = runTest {
        val (state, http) = stateWithAchievementsHttp()
        http.on("login2", body = """{"Success":true,"User":"Me","Token":"tok"}""")
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.openSelected()   // Sign in
        assertTrue(state.raSettings().signingIn)
        state.raDraftUser(" me "); state.onButton(NxButton.A)   // no password yet
        assertEquals("Enter a username and password", state.error.value); assertTrue(state.raSettings().signingIn); assertTrue(http.posts.isEmpty())
        state.onButton(NxButton.A)   // dismisses the error
        state.raDraftPassword("secret"); state.onButton(NxButton.A)
        assertEquals(listOf("r=login2&u=me&p=secret"), http.posts)
        val s = state.raSettings()
        assertFalse(s.signingIn); assertEquals("", s.draftUser); assertEquals("", s.draftPassword)
        assertEquals(RaSettingKind.SIGN_OUT, s.rows[0].kind); assertEquals("Sign out", s.rows[0].label); assertEquals("Signed in as Me", s.rows[1].secondary)
        assertEquals("tok", state.settings.raToken)
    }
    @Test fun sign_in_cannot_reopen_or_resubmit_while_a_login_runs() = runTest {
        val (state, http) = stateWithAchievementsHttp()
        val gate = CompletableDeferred<Unit>()
        http.responders["login2"] = { gate.await(); com.nxredux.mobile.achievements.RaResponse(200, """{"Success":true,"User":"Me","Token":"tok"}""") }
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.onButton(NxButton.A)   // open
        state.raDraftUser("me"); state.raDraftPassword("secret"); state.onButton(NxButton.A)   // login starts and waits
        assertFalse(state.raSettings().signingIn); assertEquals("Sign in", state.raSettings().rows[0].label)
        state.onButton(NxButton.A)   // the row again: busy, no modal
        assertEquals("Busy, try again", state.error.value); assertFalse(state.raSettings().signingIn)
        state.onButton(NxButton.A); state.onButton(NxButton.A)   // dismiss, then try once more
        assertFalse(state.raSettings().signingIn)
        state.onButton(NxButton.A)   // dismiss
        gate.complete(Unit)
        assertEquals(1, http.posts.count { it.startsWith("r=login2") })
        val s = state.raSettings()
        assertFalse(s.signingIn); assertEquals("", s.draftUser); assertEquals("", s.draftPassword); assertEquals("Sign out", s.rows[0].label)
    }
    @Test fun a_modal_open_when_a_sign_in_lands_closes() = runTest {
        val (state, _) = stateWithAchievements()
        state.settings.raUsername = "me"
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.onButton(NxButton.A); state.raDraftPassword("secret")
        assertTrue(state.raSettings().signingIn)
        state.settings.raToken = "tok"; state.achievements!!.refresh()   // a sign-in finished while the modal was up
        val s = state.raSettings()
        assertFalse(s.signingIn); assertEquals("", s.draftUser); assertEquals("", s.draftPassword); assertEquals("Sign out", s.rows[0].label)
    }
    @Test fun a_failed_server_login_shows_the_error_and_keeps_sign_in() = runTest {
        val (state, http) = stateWithAchievementsHttp()
        http.on("login2", body = """{"Success":false,"Error":"Invalid user/password combination. Please try again."}""")
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.onButton(NxButton.A)
        state.raDraftUser("me"); state.raDraftPassword("wrong"); state.onButton(NxButton.A)
        val s = state.raSettings()
        assertFalse(s.signingIn); assertEquals("", s.draftUser); assertEquals("", s.draftPassword)
        assertEquals(RaSettingKind.SIGN_IN, s.rows[0].kind); assertNull(state.settings.raToken)
        assertTrue(s.rows[1].secondary!!.contains("Invalid"), s.rows[1].secondary)
    }
    @Test fun sign_in_seeds_the_saved_username_and_an_empty_password() = runTest {
        val (state, _) = stateWithAchievements()
        state.settings.raUsername = "saved"
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.openSelected()
        val s = state.raSettings()
        assertTrue(s.signingIn); assertEquals("saved", s.draftUser); assertEquals("", s.draftPassword)
    }
    @Test fun text_entry_is_open_while_the_sign_in_modal_shows() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        assertFalse(state.textEntryOpen)
        state.openSelected(); assertTrue(state.textEntryOpen)
        state.onButton(NxButton.B); assertFalse(state.textEntryOpen)
    }
    @Test fun sign_in_b_and_menu_close_and_clear_the_drafts() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        for (button in listOf(NxButton.B, NxButton.MENU)) {
            state.openSelected()
            state.raDraftUser("me"); state.raDraftPassword("secret"); state.onButton(button)
            val s = state.raSettings()
            assertFalse(s.signingIn); assertEquals("", s.draftUser); assertEquals("", s.draftPassword); assertNull(state.settings.raToken)
        }
    }
    @Test fun ra_drafts_without_the_modal_change_nothing() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        val before = state.screen.value
        state.raDraftUser("x"); state.raDraftPassword("y")
        assertEquals(before, state.screen.value)
    }
    @Test fun sign_in_modal_ignores_dpad() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.openSelected()
        val sel = state.selection.value
        state.onButton(NxButton.DOWN); state.onButton(NxButton.LEFT)
        assertEquals(sel, state.selection.value); assertTrue(state.raSettings().signingIn)
    }
    @Test fun ra_settings_to_string_redacts_the_password() {
        val s = Screen.RaSettings(emptyList(), signingIn = true, draftUser = "me", draftPassword = "hunter2")
        assertEquals("RaSettings(rows=[], signingIn=true, confirm=null, draftUser=me, draftPassword=***)", s.toString())
        assertFalse("hunter2" in s.toString())
    }
    @Test fun sign_out_forgets_the_token_and_login_only() = runTest {
        val (state, store) = stateWithAchievements()
        state.settings.raToken = "tok"; state.settings.raUsername = "me"
        store.put(RaPaths.login(), "{}"); store.put(RaPaths.JOURNAL, "x\n")
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        assertEquals("Sign out", state.raSettings().rows[0].label)
        state.onButton(NxButton.UP); state.openSelected()   // Sign out: asks first
        assertEquals(RaConfirm.SIGN_OUT, state.raSettings().confirm); assertEquals("tok", state.settings.raToken)
        state.onButton(NxButton.A)
        assertNull(state.raSettings().confirm)
        assertNull(state.settings.raToken); assertFalse(state.settings.raEnabled); assertNull(store.text(RaPaths.login())); assertEquals("x\n", store.text(RaPaths.JOURNAL))
        assertEquals(RaSettingKind.SIGN_IN, state.raSettings().rows[0].kind); assertEquals("Signed out", state.notice.value)
    }
    @Test fun sign_out_asks_first_and_b_keeps_the_account() = runTest {
        val (state, store) = stateWithAchievements()
        state.settings.raToken = "tok"; state.settings.raUsername = "me"; state.settings.raEnabled = true
        store.put(RaPaths.login(), "{}")
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.onButton(NxButton.UP); state.onButton(NxButton.A)   // one stray A on Sign out
        assertEquals(RaConfirm.SIGN_OUT, state.raSettings().confirm)
        assertEquals("tok", state.settings.raToken); assertTrue(state.settings.raEnabled); assertEquals("{}", store.text(RaPaths.login()))
        state.onButton(NxButton.DOWN)   // the dialog takes the buttons
        assertEquals(0, state.selection.value.index); assertEquals(RaConfirm.SIGN_OUT, state.raSettings().confirm)
        state.onButton(NxButton.B)
        assertNull(state.raSettings().confirm); assertEquals("tok", state.settings.raToken); assertEquals("Sign out", state.raSettings().rows[0].label)
    }
    @Test fun ra_settings_never_opens_on_sign_out() = runTest {
        val (state, _) = stateWithAchievements()
        state.settings.raToken = "tok"
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        assertEquals(RaSettingKind.STATUS, state.raSettings().rows[state.selection.value.index].kind)
        state.onButton(NxButton.UP); assertTrue(state.back())   // left with Sign out highlighted
        state.openSelected()
        assertEquals(RaSettingKind.STATUS, state.raSettings().rows[state.selection.value.index].kind)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); assertTrue(state.back())   // another row is remembered
        state.openSelected()
        assertEquals(RaSettingKind.DOWNLOAD_ALL, state.raSettings().rows[state.selection.value.index].kind)
    }
    @Test fun signed_out_ra_settings_opens_on_sign_in() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        assertEquals(RaSettingKind.SIGN_IN, state.raSettings().rows[state.selection.value.index].kind)
    }
    @Test fun confirming_while_the_controller_is_busy_says_so_and_changes_nothing() = runTest {
        val (state, http) = stateWithAchievementsHttp()
        val gate = CompletableDeferred<Unit>()
        http.responders["login2"] = { gate.await(); com.nxredux.mobile.achievements.RaResponse(200, """{"Success":true,"User":"Me","Token":"tok"}""") }
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.onButton(NxButton.A); state.raDraftUser("me"); state.raDraftPassword("secret"); state.onButton(NxButton.A)   // login waits
        repeat(8) { state.onButton(NxButton.DOWN) }; state.openSelected()   // Reset account data
        assertEquals(RaConfirm.RESET_ACCOUNT, state.raSettings().confirm)
        state.onButton(NxButton.A)
        assertEquals("Busy, try again", state.error.value); assertNull(state.raSettings().confirm)
        state.onButton(NxButton.A)   // dismiss
        gate.complete(Unit)
        assertEquals("tok", state.settings.raToken)   // the login landed; nothing was reset
    }
    @Test fun destructive_rows_confirm_first() = runTest {
        val (state, _) = stateWithAchievements()
        state.settings.raToken = "tok"
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        repeat(7) { state.onButton(NxButton.DOWN) }; state.openSelected()
        assertEquals(RaConfirm.RESET_ACCOUNT, (state.screen.value as Screen.RaSettings).confirm)
        state.onButton(NxButton.B); assertNull((state.screen.value as Screen.RaSettings).confirm)
        state.openSelected(); state.onButton(NxButton.A)
        assertNull(state.settings.raToken)
    }

    @Test fun the_on_off_rows_toggle_on_left_and_right() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN)   // Enable achievements: dimmed while signed out
        state.onButton(NxButton.RIGHT); assertEquals(false, state.settings.raEnabled)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN)   // Show notifications, On
        state.onButton(NxButton.LEFT); assertEquals(false, state.settings.raShowNotifications)
        assertEquals("Off", (state.screen.value as Screen.RaSettings).rows[4].secondary)
        state.settings.raToken = "tok"
        state.onButton(NxButton.RIGHT); assertEquals(true, state.settings.raShowNotifications)   // the refresh sees the sign-in
        state.onButton(NxButton.UP); state.onButton(NxButton.UP)
        state.onButton(NxButton.RIGHT); assertEquals(true, state.settings.raEnabled)
        state.onButton(NxButton.LEFT); assertEquals(false, state.settings.raEnabled)
    }

    @Test fun a_tap_on_the_left_pane_beside_ra_settings_opens_that_row() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        val tap = { i: Int -> state.raParentTap(Screen.RetroAchievements(), i) }
        tap(raPageRow(Screen.RaSettings(emptyList()))!!); assertIs<Screen.RaSettings>(state.screen.value)   // Settings is already open
        state.openSelected(); assertTrue(state.raSettings().signingIn)                           // Sign in: the prompt answers first
        tap(0); assertTrue(state.raSettings().signingIn)
        state.onButton(NxButton.B)
        tap(1)                                                                                    // Sync now: back on the page, run there
        assertIs<Screen.RetroAchievements>(state.screen.value)
        tap(0); assertIs<Screen.RetroAchievements>(state.screen.value)                           // only from a level beside the page
    }

    @Test fun a_on_the_duration_rows_steps_forward_and_wraps() = runTest {
        val (state, _) = stateWithAchievements()
        openRa(state); state.onButton(NxButton.DOWN); state.onButton(NxButton.DOWN); state.openSelected()
        repeat(5) { state.onButton(NxButton.DOWN) }                  // Notification duration, 3 s
        state.openSelected(); assertEquals("4 s", (state.screen.value as Screen.RaSettings).rows[5].secondary)
        state.onButton(NxButton.RIGHT); assertEquals("5 s", (state.screen.value as Screen.RaSettings).rows[5].secondary)
        state.onButton(NxButton.A); assertEquals("1 s", (state.screen.value as Screen.RaSettings).rows[5].secondary)
        state.onButton(NxButton.DOWN); repeat(4) { state.onButton(NxButton.RIGHT) }   // Progress duration 1 → 5
        assertEquals("5 s", (state.screen.value as Screen.RaSettings).rows[6].secondary)
        state.openSelected(); assertEquals("Off", (state.screen.value as Screen.RaSettings).rows[6].secondary); assertEquals(0, state.settings.raProgressSeconds)
        assertEquals(listOf(Hint(NxButton.A, "Change"), Hint(NxButton.B, "Back")), raSettingsHints((state.screen.value as Screen.RaSettings).rows[6]))
    }

    private val tetris = "/Roms/Game Boy (GB)/Tetris.gb"
    private val mario = "/Roms/Game Boy Advance (GBA)/Mario.gba"
    private val mk = "/Roms/Nintendo DS (NDS)/Mario Kart DS.nds"

    /** A home with a Collections folder holding RPGs (Mario Kart) and an empty Racing. */
    /** [broken]: also a Broken.txt that lists but gives no text (a failed read). */
    private fun sourceWithCollections(broken: Boolean = false) = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms", "Collections"),
            "Home/Roms" to listOf("Nintendo DS (NDS)", "Game Boy (GB)", "Game Boy Advance (GBA)"),
            "Home/Roms/Nintendo DS (NDS)" to listOf("Mario Kart DS.nds"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
            "Home/Collections" to listOf("RPGs.txt", "Racing.txt") + listOfNotNull("Broken.txt".takeIf { broken }),
        ),
        texts = mapOf("Home/Collections/RPGs.txt" to "$mk\n/Roms/Game Boy (GB)/missing.gb\n", "Home/Collections/Racing.txt" to ""),
    )

    /** Every tab in order, as the old single list read: Home's pins ("pin:<name>"), each console's tag, "collections", "tools". */
    private fun rowKinds(state: AppState) = assertIs<Screen.Systems>(state.screen.value).tabs.flatMap { tab ->
        when (tab.category) {
            MenuCategory.HOME -> tab.rows.filterIsInstance<MainRow.Pin>().map { "pin:${it.entry.game.shownName}" }
            MenuCategory.COLLECTIONS -> listOf("collections")
            MenuCategory.TOOLS -> listOf("tools")
            MenuCategory.CONSOLES -> tab.rows.map { (it as MainRow.System).system.tag }
        }
    }

    @Test fun home_shows_the_first_resolvable_recent_and_only_pins_that_resolve() = runTest {
        val data = FakeUserData(recents = listOf(Recent("/Roms/Game Boy (GB)/gone.gb"), Recent(tetris)), pins = listOf(Shortcut(mario, "Mario"), Shortcut("/Roms/Game Boy (GB)/gone.gb", "Gone")))
        val state = state(MemorySettings(ContentRef("Home")), src = sourceWithCollections(), data = data)
        state.load()
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS), assertIs<Screen.Systems>(state.screen.value).tabs.map { it.category })
        assertEquals(listOf("pin:Mario", "GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))
        assertEquals("Tetris", assertIs<MainRow.Continue>(state.screen.value.homeRows[0]).entry.game.shownName)   // the gone recent is passed over
        assertEquals(mario, assertIs<MainRow.Pin>(state.screen.value.homeRows.filterNot { it is MainRow.Shelf }.last()).entry.game.path)   // the pins end the rows (before the shelves): no All pinned tile
        assertEquals(2, data.pins.size)                                   // an unresolvable pin is hidden, not dropped
        val bare = state(MemorySettings(ContentRef("Home")), data = FakeUserData(recents = listOf(Recent("/Roms/Game Boy (GB)/gone.gb"))))
        bare.load()
        assertEquals(listOf("GB", "GBA", "NDS", "tools"), rowKinds(bare))   // no resolvable recent, no Collections folder, no pins
        assertEquals(listOf(MainRow.PickAGame), bare.screen.value.homeRows.filterNot { it is MainRow.Shelf })
    }

    @Test fun a_rescan_after_every_collection_file_went_drops_the_tab() = runTest {
        val src = sourceWithCollections()
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))
        src.delete("Home/Collections/RPGs.txt"); src.delete("Home/Collections/Racing.txt")   // outside the app
        state.rescan()                                                   // Collections/ is listed again: no names now
        assertEquals(listOf("GB", "GBA", "NDS", "tools"), rowKinds(state))
    }

    @Test fun a_rescan_after_a_collection_appeared_brings_the_tab_in() = runTest {
        val src = sourceWithCollections().apply { delete("Home/Collections/RPGs.txt"); delete("Home/Collections/Racing.txt") }
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        assertEquals(listOf("GB", "GBA", "NDS", "tools"), rowKinds(state))
        src.writeText(ContentRef("Home/Collections"), "RPGs.txt", "$mk\n")   // outside the app
        state.rescan()
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))
        assertEquals(listOf("RPGs"), state.openCollectionsTab())
    }

    // --- Page slides: only a user's move into or out of a list (and the switcher) steps the navigation ---

    @Test fun a_into_a_console_slides_forward_and_b_slides_back() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.onConsoles()
        assertEquals(NavStep(), state.nav.value)                         // start-up lands without a slide
        state.select(assertIs<Screen.Systems>(state.screen.value).rows.indexOfFirst { it is MainRow.System && it.system.tag == "GB" })
        state.onButton(NxButton.A)
        assertIs<Screen.Games>(state.screen.value)
        assertEquals(NavStep(1, NavMotion.FORWARD), state.nav.value)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.UP)      // moves within the list do not slide
        assertEquals(1, state.nav.value.seq)
        state.onButton(NxButton.B)
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(NavStep(2, NavMotion.BACK), state.nav.value)
    }

    @Test fun a_collection_list_slides_both_ways_and_the_tab_switch_does_not() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), src = sourceWithCollections())
        state.load()
        state.openCollectionsTab()                                       // a tab switch: no slide
        assertEquals(NavStep(), state.nav.value)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)       // RPGs
        assertIs<Screen.Games>(state.screen.value)
        assertEquals(NavStep(1, NavMotion.FORWARD), state.nav.value)
        state.onButton(NxButton.B)
        state.screen.value.collectionNames()
        assertEquals(NavStep(2, NavMotion.BACK), state.nav.value)
        state.onButton(NxButton.UP); state.onButton(NxButton.A)         // Racing is empty: nothing opens, nothing slides
        state.screen.value.collectionNames()
        assertEquals(2, state.nav.value.seq)
    }

    @Test fun select_slides_the_switcher_up_and_b_slides_it_down() = runTest {
        val data = FakeUserData(recents = listOf(Recent(tetris)), resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9)))
        val state = stateWith(data, RecordingRunner(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onButton(NxButton.SELECT)
        assertIs<Screen.Switcher>(state.screen.value)
        assertEquals(NavStep(1, NavMotion.SWITCHER_OPEN), state.nav.value)
        state.onButton(NxButton.B)
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(NavStep(2, NavMotion.SWITCHER_CLOSE), state.nav.value)
    }

    @Test fun refreshes_menus_and_settings_do_not_slide() = runTest {
        val src = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Game Boy (GB)"), "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Zelda.gb")))
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A)                                       // the GB list
        assertEquals(1, state.nav.value.seq)
        rename(state, "Zed Tetris")                                      // the list rebuilds in place
        assertIs<Screen.Games>(state.screen.value)
        assertEquals(1, state.nav.value.seq)
        state.onButton(NxButton.MENU); state.onButton(NxButton.B)        // a context menu opens and closes
        assertEquals(1, state.nav.value.seq)
        state.onButton(NxButton.B)
        assertEquals(2, state.nav.value.seq)
        state.openTools(); state.back()                                  // Tools and back: no slide
        state.openSettings(SettingsSection.LIBRARY); state.back()        // nor Settings
        assertEquals(2, state.nav.value.seq)
    }

    @Test fun a_hidden_collections_listing_keeps_the_collections_row() = runTest {
        val src = sourceWithCollections()
        var hide = false
        val hiding = object : RomsSource by src {
            override suspend fun listChildren(dir: ContentRef): List<Entry> =
                if (hide && dir.value == "Home/Collections") throw ListingUnavailable(dir.value) else src.listChildren(dir)
        }
        val state = state(MemorySettings(ContentRef("Home")), src = hiding)
        state.load()
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))
        hide = true
        state.rescan()                                                   // the scan lists; only Collections/ is hidden
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))
        assertEquals(listOf("Racing", "RPGs"), state.openCollectionsTab())   // no proof there is nothing: the names as last listed
        src.listingFault = ListingFault.UNAVAILABLE                      // every listing hidden: the Collections tab stays too
        state.rescan()
        state.dismissError()
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))
    }

    @Test fun a_pin_row_shows_the_menu_hint_and_the_hint_opens_its_menu() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(pins = listOf(Shortcut(mario, "Mario"))))
        state.load()
        val tabs = assertIs<Screen.Systems>(state.screen.value).tabs
        assertEquals(listOf(Hint(NxButton.SELECT, "Recent"), Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Play")), mainMenuHints(tabs[0].rows.first { it is MainRow.Pin }))   // the pin
        assertEquals(listOf(Hint(NxButton.SELECT, "Recent"), Hint(NxButton.A, "Open")), mainMenuHints(tabs[1].rows[0]))                                   // a console
        state.selectHome { it is MainRow.Pin }
        state.onButton(NxButton.MENU)                                    // what a tap on the MENU hint sends
        assertNotNull(assertIs<Screen.Systems>(state.screen.value).menu)
    }

    @Test fun a_pin_whose_game_is_hidden_or_coreless_is_not_shown() = runTest {
        val data = FakeUserData(pins = listOf(Shortcut(mario, "Mario"), Shortcut("/Roms/Commodore 64 (C64)/Boulder Dash 2.chd", "Boulder")), hidden = listOf(mario))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load()
        assertEquals(listOf("GB", "NDS", "tools"), rowKinds(state))      // GBA's only game is hidden: the console goes too
        assertEquals(2, data.pins.size)
    }

    @Test fun continue_shows_the_last_game_and_follows_a_launch() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(recents = listOf(Recent(mk, "MK alias"), Recent(tetris, "Tetris DX")))
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load()
        val menu = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.HOME, menu.category)                   // the first tab
        val last = assertIs<MainRow.Continue>(menu.rows[0]).entry
        assertEquals("NDS", last.system.tag)
        assertEquals("Mario Kart DS", last.game.shownName)               // the name from the index, not the recents alias
        state.onButton(NxButton.A)                                       // A launches: no list opens
        assertEquals("NDS", runner.launched.single().systemTag)
        val back = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.HOME, back.category)
        assertIs<MainRow.Continue>(back.rows[state.selection.value.index])   // still on Continue
        state.onConsoles()
        state.select(assertIs<Screen.Systems>(state.screen.value).rows.indexOfFirst { (it as MainRow.System).system.tag == "GB" })
        state.onButton(NxButton.A); state.onButton(NxButton.A)          // Game Boy -> Tetris
        assertEquals("Tetris.gb", runner.launched.last().fileName)
        state.onButton(NxButton.B); state.onButton(NxButton.L1)         // back on Home
        advanceUntilIdle()
        assertEquals("Tetris", assertIs<MainRow.Continue>(state.screen.value.homeRows[0]).entry.game.shownName)   // the launched game, now last
    }

    @Test fun collections_row_opens_the_names_then_a_collection_lists_its_resolvable_games() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), src = sourceWithCollections())
        state.load()
        assertEquals(listOf("Racing", "RPGs"), state.openCollectionsTab())
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals(ListSource.Collection("RPGs"), list.source)
        assertEquals(listOf("Mario Kart DS"), list.entries.map { it.game.shownName })   // the missing line is skipped
        assertTrue(state.back())
        assertEquals(1, state.selection.value.index)                     // back on RPGs
        state.screen.value.collectionNames()                             // on the Collections tab
        state.select(0); state.onButton(NxButton.A)                      // Racing (empty)
        state.screen.value.collectionNames()                             // an empty collection cannot open: stays on the tab
    }

    @Test fun hidden_games_row_in_settings_opens_the_hidden_list_and_b_returns_there() = runTest {
        val data = FakeUserData(hidden = listOf(mario, "/Roms/Commodore 64 (C64)/Boulder Dash 2.chd"))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load()
        state.openSettings(SettingsSection.LIBRARY)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        val hiddenRow = rows.first { it.kind == SettingsKind.HIDDEN }
        assertEquals("Hidden games", hiddenRow.label)
        assertEquals("2 games", hiddenRow.secondary)
        state.select(rows.indexOf(hiddenRow)); state.openSelected()
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals(ListSource.Hidden, list.source)
        assertEquals(listOf("Mario", "Boulder Dash 2"), list.entries.map { it.game.shownName })   // coreless games are listed too
        assertTrue(state.back())
        assertEquals(SettingsSection.LIBRARY, assertIs<Screen.Settings>(state.screen.value).section)
        val none = state(MemorySettings(ContentRef("Home")))
        none.load(); none.openSettings(SettingsSection.LIBRARY)
        assertTrue(assertIs<Screen.Settings>(none.screen.value).rows.none { it.kind == SettingsKind.HIDDEN })
    }

    @Test fun on_an_index_start_the_collections_row_joins_after_the_home_probe_keeping_the_highlighted_row() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val src = sourceWithCollections()
        val store = MemoryLibraryIndexStore(LibraryIndexCodec.encode(scannedIndex(settings, src)))
        lateinit var st: AppState
        val firstShow = ArrayList<List<MenuCategory>>()
        val early = ArrayList<String>()
        val counting = CountingRomsSource(src) {
            val shown = st.screen.value as? Screen.Systems
            if (shown == null) early += it
            else if (firstShow.isEmpty()) { firstShow += shown.tabs.map { it.category }; st.onConsoles(); st.select(1) }   // the home probe: the menu is up; the user moves to GBA
        }
        st = AppState(AppDeps(settings, counting, RecordingRunner(), NoopPicker(), indexStore = store, compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        st.load()
        assertEquals(emptyList<String>(), early)                         // still no tree access before the first show
        assertTrue(MenuCategory.COLLECTIONS !in firstShow.single())
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(st))
        assertEquals(1, st.selection.value.index)                        // still on GBA
    }

    @Test fun a_launch_makes_continue_appear() = runTest {
        val data = FakeUserData()
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = RecordingRunner())
        state.load()
        assertEquals(listOf(MainRow.PickAGame), state.screen.value.homeRows.filterNot { it is MainRow.Shelf })
        state.onConsoles()
        state.onButton(NxButton.A); state.onButton(NxButton.A)           // GB -> Tetris
        assertTrue(state.back())
        state.onButton(NxButton.L1)
        assertEquals(tetris, assertIs<MainRow.Continue>(state.screen.value.homeRows[0]).entry.game.path)
    }

    @Test fun a_launch_the_runner_refuses_writes_no_recent() = runTest {
        val runner = RecordingRunner(starts = false)
        val data = FakeUserData()
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A); state.onButton(NxButton.A)           // GB -> Tetris, refused by the runner
        assertEquals("Tetris.gb", runner.launched.single().fileName)
        assertTrue(data.recents.isEmpty())
        assertTrue(state.back())
        state.onButton(NxButton.L1)
        assertEquals(listOf(MainRow.PickAGame), state.screen.value.homeRows.filterNot { it is MainRow.Shelf })   // no Continue
    }

    @Test fun a_launch_writes_the_recent_only_once_the_runner_reports_the_start() = runTest {
        val runner = RecordingRunner(starts = false)
        val data = FakeUserData()
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A); state.onButton(NxButton.A)           // GB -> Tetris
        assertTrue(state.back())                                         // back on the main menu before the game reports in
        assertTrue(data.recents.isEmpty())
        state.onButton(NxButton.L1)                                      // on Home
        runner.started.single()()                                        // the game loaded
        assertEquals(listOf(Recent(tetris, "Tetris")), data.recents)
        assertEquals(tetris, assertIs<MainRow.Continue>(state.screen.value.homeRows[0]).entry.game.path)   // swapped in on the open main menu
    }

    @Test fun a_launch_records_the_shown_name_as_the_recent_alias_replacing_a_stale_one() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(recents = listOf(Recent(tetris, "Tetris (World)")))   // written when the list showed another name
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A); state.onButton(NxButton.A)           // GB -> Tetris
        assertEquals("Tetris.gb", runner.launched.single().fileName)
        assertEquals(listOf(Recent(tetris, "Tetris")), data.recents)   // as the Brick: Recents_setAlias(entry name) on every open
    }

    @Test fun a_start_reported_after_the_menu_scope_died_still_writes_the_recent() = runTest {
        val runner = RecordingRunner(starts = false)
        val data = FakeUserData()
        val uiScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val recorder = RecentsRecorder(data, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))   // process-lifetime
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, runner, NoopPicker(), data, recents = recorder, compute = UnconfinedTestDispatcher(testScheduler)), uiScope)
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A); state.onButton(NxButton.A)           // GB -> Tetris
        uiScope.cancel()                                                 // the menu activity is destroyed while the game loads
        runner.started.single()()                                        // then the game loads
        assertEquals(listOf(Recent(tetris, "Tetris")), data.recents)
    }

    @Test fun game_info_joins_game_time_by_path_and_refreshes_when_a_game_is_recorded() = runTest {
        val path = mario   // a game the test library holds
        var total = GameTotal(1, "Mario", "GBA", path, totalMs = 7_200_000, plays = 3, lastPlayedAt = 123L)
        val data = object : GameTimeData by NoGameTime { override suspend fun byPath() = mapOf(path to total) }
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(pins = listOf(Shortcut(mario, "Mario"))), gameTime = data)
        state.load(); advanceUntilIdle()
        assertEquals(GameInfo(123L, 7_200_000, null), state.gameInfo.value[path])
        total = total.copy(totalMs = 9_000_000, plays = 4, lastPlayedAt = 456L)
        state.selectHome { it is MainRow.Pin }; state.onButton(NxButton.A); advanceUntilIdle()   // the pin launches Mario: recorded
        assertEquals(GameInfo(456L, 9_000_000, null), state.gameInfo.value[path])
    }

    @Test fun a_game_info_refresh_asked_for_during_one_runs_once_after_it_not_in_parallel() = runTest {
        val path = mario
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var active = 0
        var maxActive = 0
        var total = GameTotal(1, "Mario", "GBA", path, totalMs = 1_000, plays = 1, lastPlayedAt = 1L)
        val data = object : GameTimeData by NoGameTime {
            override suspend fun byPath(): Map<String, GameTotal> {
                calls++; active++; maxActive = maxOf(maxActive, active)
                val seen = total
                if (calls == 1) gate.await()   // the first read is slow
                active--
                return mapOf(path to seen)
            }
        }
        val state = state(MemorySettings(ContentRef("Home")), gameTime = data)
        state.load()                                                     // the first refresh starts and waits
        total = total.copy(totalMs = 2_000, lastPlayedAt = 2L)
        state.onForeground(); state.onForeground()                       // asked for twice while it runs
        assertEquals(1, calls)                                           // not started beside it
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(2, calls)                                           // one re-run for both asks, not lost
        assertEquals(1, maxActive)
        assertEquals(GameInfo(2L, 2_000, null), state.gameInfo.value[path])   // the re-run's (newer) answer
    }

    @Test fun y_remove_in_the_switcher_moves_continue_past_the_game_on_close() = runTest {
        val data = FakeUserData(
            recents = listOf(Recent(tetris), Recent(mario)),
            resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9), ("GBA" to "Mario.gba") to ResumeInfo(9)),
        )
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load()
        val before = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.HOME, before.category)
        assertEquals("Tetris", assertIs<MainRow.Continue>(before.rows[0]).entry.game.shownName)
        state.onButton(NxButton.SELECT)
        assertEquals("Tetris.gb", assertIs<Screen.Switcher>(state.screen.value).entries.first().game.fileName)
        state.onButton(NxButton.Y)
        state.onButton(NxButton.A)                                       // confirm: Tetris leaves recent.txt
        state.onButton(NxButton.B)
        advanceUntilIdle()
        val after = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.HOME, after.category)
        assertEquals("Mario", assertIs<MainRow.Continue>(after.rows[0]).entry.game.shownName)
        assertEquals(0, state.selection.value.index)                     // still on Continue
    }

    @Test fun a_launch_from_a_pin_row_brings_continue_in_and_focuses_it() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(pins = listOf(Shortcut(mario, "Mario"), Shortcut(tetris, "Tetris")))
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load(); advanceUntilIdle()
        state.onHomeLayout(LAND); advanceUntilIdle()                     // landscape: Mario, Tetris row games
        assertEquals(listOf("pin:Mario", "pin:Tetris", "GB", "GBA", "NDS", "tools"), rowKinds(state))
        state.selectHome { it is MainRow.Pin && it.entry.game.path == mario }; state.onButton(NxButton.A)   // the pin launches Mario
        assertEquals("GBA", runner.launched.single().systemTag)
        val home = state.screen.value.homeRows
        assertEquals(mario, assertIs<MainRow.Continue>(home[0]).entry.game.path)   // Pick a game became Continue
        assertEquals(listOf("pin:Tetris", "GB", "GBA", "NDS", "tools"), rowKinds(state))   // Mario is Continue: out of the row
        assertEquals(0, state.selection.value.index)                     // the launched pin is Continue now: focus follows it
    }

    @Test fun a_switcher_launch_over_the_main_menu_returns_to_home_with_continue() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(recents = listOf(Recent(tetris)), resumable = tetrisResumable)
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load()
        state.onButton(NxButton.SELECT); state.onButton(NxButton.A)
        assertEquals(9, runner.launched.single().resumeSlot)
        assertEquals(tetris, assertIs<MainRow.Continue>(state.screen.value.homeRows[0]).entry.game.path)
    }

    @Test fun hiding_every_unassigned_game_drops_the_unassigned_row_and_unhiding_restores_it() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val data = FakeUserData()
        val state = state(settings, data = data)
        state.load()
        openUnassigned(state)
        var hides = 0
        while (state.screen.value is Screen.Games && hides < 10) { choose(state, ContextKind.HIDE); hides++ }
        assertTrue(hides in 1..9)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows    // the list emptied: back on Settings -> Library
        assertTrue(rows.none { it.kind == SettingsKind.UNASSIGNED })     // no empty Unassigned row
        state.select(rows.indexOfFirst { it.kind == SettingsKind.HIDDEN }); state.openSelected()
        assertEquals(hides, assertIs<Screen.Games>(state.screen.value).entries.size)
        state.onButton(NxButton.A)                                       // A unhides in the hidden list
        assertTrue(state.back())                                         // back from the hidden list, or Settings once it emptied
        assertTrue(assertIs<Screen.Settings>(state.screen.value).rows.any { it.kind == SettingsKind.UNASSIGNED })
    }

    @Test fun unassigned_is_a_library_settings_row_not_a_main_menu_row_and_b_returns_to_it() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val state = state(settings)
        state.load()
        assertTrue("" !in rowKinds(state))                               // no Unassigned on the main menu
        state.openSettings(SettingsSection.LIBRARY)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        val row = rows.single { it.kind == SettingsKind.UNASSIGNED }
        assertEquals("Unassigned games", row.label)
        openUnassigned(state)
        val games = assertIs<Screen.Games>(state.screen.value)
        assertEquals(gamesLabel(games.entries.size), row.secondary)
        assertTrue(state.back())
        val back = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.LIBRARY, back.section)
        assertEquals(back.rows.indexOfFirst { it.kind == SettingsKind.UNASSIGNED }, state.selection.value.index)
        val none = state(MemorySettings(ContentRef("Home")))
        none.load(); none.openSettings(SettingsSection.LIBRARY)
        assertTrue(assertIs<Screen.Settings>(none.screen.value).rows.none { it.kind == SettingsKind.UNASSIGNED })
    }

    @Test fun pin_writes_shortcuts_and_the_row_appears_unpin_from_the_row_removes_it() = runTest {
        val data = FakeUserData()
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A)                                       // GB list
        choose(state, ContextKind.PIN)
        assertEquals(listOf(Shortcut(tetris, "Tetris")), data.pins)
        assertEquals("Pinned", state.notice.value)
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        state.onButton(NxButton.MENU)
        assertEquals(ContextKind.UNPIN, menuKinds(state)[0]); state.onButton(NxButton.B)
        assertTrue(state.back())
        assertEquals(listOf("pin:Tetris", "GB", "GBA", "NDS", "tools"), rowKinds(state))
        state.selectTab(0); state.selectHome { it is MainRow.Pin }; choose(state, ContextKind.UNPIN)   // MENU on the pinned row (on Home)
        assertEquals(emptyList<Shortcut>(), data.pins)
        assertEquals("Unpinned", state.notice.value)
        assertEquals(listOf("GB", "GBA", "NDS", "tools"), rowKinds(state))
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
    }

    @Test fun a_full_pin_list_refuses_with_a_notice() = runTest {
        val data = FakeUserData(pins = (1..Shortcuts.MAX).map { Shortcut("/Roms/X (X)/$it.gb", "G$it") })
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A); choose(state, ContextKind.PIN)
        assertEquals(Shortcuts.MAX, data.pins.size)
        assertEquals("Pins are full (12). Unpin one first.", state.notice.value)
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
    }

    @Test fun a_failed_pin_write_reports() = runTest {
        val data = FakeUserData(failWrites = true)
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A); choose(state, ContextKind.PIN)
        assertEquals("Could not save the pin", state.error.value)
    }

    @Test fun hide_drops_the_game_everywhere_and_the_last_game_leaves_the_console() = runTest {
        val data = FakeUserData(recents = listOf(Recent(mario)), pins = listOf(Shortcut(mario, "Mario")))
        val src = sourceWithCollections()
        src.writeText(ContentRef("Home/Collections"), "RPGs.txt", "$mario\n")     // RPGs holds Mario only
        val state = state(MemorySettings(ContentRef("Home")), src = src, data = data)
        state.load()
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))   // Mario is Continue: not in the Pinned row
        state.onConsoles(); state.select(1); state.onButton(NxButton.A)  // GBA list: Mario only
        choose(state, ContextKind.HIDE)
        assertEquals(listOf(mario), data.hidden)
        assertEquals("Hidden. Unhide in Tools › Settings › Library", state.notice.value)
        assertIs<Screen.Systems>(state.screen.value)                     // the list emptied: back on the main menu
        assertEquals(listOf("GB", "NDS", "collections", "tools"), rowKinds(state))   // no recent, no pin, no GBA
        assertTrue(state.selection.value.index in assertIs<Screen.Systems>(state.screen.value).rows.indices)
        state.openCollectionsTab(); state.select(1); state.onButton(NxButton.A)   // Collections -> RPGs
        state.screen.value.collectionNames()                             // RPGs resolves to nothing now: the tab stays
        assertEquals(listOf(Recent(mario)), data.recents)                // files untouched
        assertEquals(listOf(Shortcut(mario, "Mario")), data.pins)
    }

    @Test fun unhide_restores_the_game_and_an_emptied_hidden_list_returns_to_settings() = runTest {
        val data = FakeUserData(hidden = listOf(mario), pins = listOf(Shortcut(mario, "Mario")))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load()
        assertEquals(listOf("GB", "NDS", "tools"), rowKinds(state))
        state.openSettings(SettingsSection.LIBRARY)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        state.select(rows.indexOfFirst { it.kind == SettingsKind.HIDDEN }); state.openSelected()
        state.onButton(NxButton.A)                                       // A unhides in the hidden list
        assertEquals(emptyList<String>(), data.hidden)
        assertEquals("Unhidden", state.notice.value)
        val settings = assertIs<Screen.Settings>(state.screen.value)
        assertTrue(settings.rows.none { it.kind == SettingsKind.HIDDEN })
        assertTrue(state.back()); assertTrue(state.back())               // Settings menu -> the Tools tab of the main menu
        assertEquals(listOf("pin:Mario", "GB", "GBA", "NDS", "tools"), rowKinds(state))
    }

    private fun openHiddenList(state: AppState) {
        state.openSettings(SettingsSection.LIBRARY)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        state.select(rows.indexOfFirst { it.kind == SettingsKind.HIDDEN }); state.openSelected()
        assertEquals(ListSource.Hidden, assertIs<Screen.Games>(state.screen.value).source)
    }

    @Test fun a_in_the_hidden_list_unhides_and_never_launches() = runTest {
        val data = FakeUserData(hidden = listOf(mario))
        val runner = RecordingRunner()
        val state = state(MemorySettings(ContentRef("Home")), runner = runner, data = data)
        state.load()
        openHiddenList(state)
        state.onButton(NxButton.A)
        assertEquals(emptyList<String>(), data.hidden)
        assertEquals("Unhidden", state.notice.value)
        assertTrue(runner.launched.isEmpty())
        val settings = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.LIBRARY, settings.section)
        assertTrue(settings.rows.none { it.kind == SettingsKind.HIDDEN })
        while (state.screen.value !is Screen.Systems && state.back()) Unit
        assertTrue("GBA" in rowKinds(state))
    }

    @Test fun menu_and_x_do_nothing_in_the_hidden_list() = runTest {
        val data = FakeUserData(hidden = listOf(mario), resumable = mapOf(("GBA" to "Mario.gba") to ResumeInfo(0, null)))
        val runner = RecordingRunner()
        val state = state(MemorySettings(ContentRef("Home")), runner = runner, data = data)
        state.load()
        openHiddenList(state)
        state.onButton(NxButton.MENU)
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        state.openContextMenu(0)
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        state.onButton(NxButton.X)
        assertTrue(runner.launched.isEmpty())
        assertEquals(ListSource.Hidden, assertIs<Screen.Games>(state.screen.value).source)
        state.refreshResume()
        assertFalse(state.resumeInfo.value.canResume)
        assertEquals(listOf(mario), data.hidden)
    }

    @Test fun remove_from_recently_played_keeps_the_list_open_on_the_next_row() = runTest {
        val data = FakeUserData(recents = listOf(Recent(mk), Recent(tetris)))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load()
        state.openList(ListSource.Recent)                                // the Recently Played list (no main menu row now)
        state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.REMOVE_RECENT), menuKinds(state))   // recents: only Remove
        state.onButton(NxButton.A)
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals(listOf("Tetris"), list.entries.map { it.game.shownName })
        assertEquals(listOf(Recent(tetris)), data.recents)
        assertNull(state.notice.value)
        state.onButton(NxButton.MENU); state.onButton(NxButton.A)        // remove the last one
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(listOf("GB", "GBA", "NDS", "tools"), rowKinds(state))
    }

    @Test fun collection_counts_come_after_the_menu_and_an_unreadable_file_gives_none() = runTest {
        val src = CountingRomsSource(sourceWithCollections(broken = true))
        val data = FakeUserData()
        val state = state(MemorySettings(ContentRef("Home")), src = src, data = data)
        state.load()
        state.openCollectionsTab()
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        // RPGs: Mario Kart resolves, the missing line does not; Racing is empty; Broken would not read, so no count (not 0).
        assertEquals(listOf(MainRow.Collection("Broken", null), MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 1)), rows)
        assertEquals(emptyList(), collectionInfoLines(rows[0] as MainRow.Collection))
        // A rebuild that changes no collection (a pin) counts from the cache: no collection file is read again.
        val reads = src.calls.count { it.startsWith("readText:Home/Collections/") }
        assertTrue(reads > 0)
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.CONSOLES })
        state.onButton(NxButton.A)
        choose(state, ContextKind.PIN)
        assertEquals(1, data.pins.size)
        assertEquals(reads, src.calls.count { it.startsWith("readText:Home/Collections/") })
        assertEquals(rows, state.back().let { state.openCollectionsTab(); assertIs<Screen.Systems>(state.screen.value).rows })
    }

    @Test fun a_count_pass_that_went_stale_while_reading_is_dropped_and_counted_again() = runTest {
        val inner = sourceWithCollections()
        val gate = CompletableDeferred<Unit>()
        var held = false
        // The pass's first read of RPGs.txt takes the text, then waits: the file changes before the pass stores its count.
        val src = object : RomsSource by inner {
            override suspend fun readText(file: ContentRef): String? {
                val text = inner.readText(file)
                if (file.value == "Home/Collections/RPGs.txt" && !held) { held = true; gate.await() }
                return text
            }
        }
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()                                                     // the pass is waiting inside its RPGs read
        state.onConsoles()
        assertTrue(held)
        state.select(0); state.onButton(NxButton.A)                      // GB list
        choose(state, ContextKind.ADD_TO_COLLECTION)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)        // Tetris into RPGs: its count is stale
        assertEquals("Added to RPGs", state.notice.value)
        gate.complete(Unit)                                              // the old read (Mario Kart alone: 1) comes back
        assertTrue(state.back())
        // The stale 1 was dropped: RPGs was read again and counts Mario Kart and Tetris.
        assertEquals(listOf(MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 2)), state.collectionRows())
    }

    @Test fun adding_to_a_collection_recounts_only_that_one() = runTest {
        val src = CountingRomsSource(sourceWithCollections())
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        assertEquals(listOf(MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 1)), state.collectionRows())
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.CONSOLES })
        fun reads(name: String) = src.calls.count { it == "readText:Home/Collections/$name.txt" }
        val racing = reads("Racing")
        state.select(0); state.onButton(NxButton.A)                      // GB list
        choose(state, ContextKind.ADD_TO_COLLECTION)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)        // Tetris into RPGs
        assertTrue(state.back())
        assertEquals(listOf(MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 2)), state.collectionRows())
        assertEquals(racing, reads("Racing"))                            // Racing's count came from the cache
    }

    /** [sourceWithCollections] with its collection files written at [at], so the provider reports a stamp for each. */
    private suspend fun stampedCollections(broken: Boolean = false, at: Long = 100L) = sourceWithCollections(broken).apply {
        now = at
        writeText(ContentRef("Home/Collections"), "RPGs.txt", textAt("Home/Collections/RPGs.txt")!!)
        writeText(ContentRef("Home/Collections"), "Racing.txt", "")
    }

    private fun TestScope.countState(settings: MemorySettings, src: RomsSource, files: PrivateFiles, store: LibraryIndexStore) =
        AppState(
            AppDeps(settings, src, RecordingRunner(), NoopPicker(), indexStore = store, privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

    /** A first run that counts every collection and stores the counts; its index and cache are what the next start finds. */
    private suspend fun TestScope.firstRun(settings: MemorySettings, src: RomsSource, files: MemoryPrivateFiles, store: LibraryIndexStore) {
        val first = countState(settings, src, files, store)
        first.load()
        assertEquals(listOf(MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 1)), first.collectionRows().filter { (it as MainRow.Collection).name != "Broken" })
        advanceUntilIdle()   // the coalesced cache write
        assertNotNull(files.files[CollectionCountCache.FILE])
    }

    private fun CountingRomsSource.collectionReads() = calls.filter { it.startsWith("readText:Home/Collections/") }

    @Test fun a_cold_start_with_a_valid_count_cache_reads_no_collection_file() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val src = stampedCollections()
        val files = MemoryPrivateFiles()
        val store = MemoryLibraryIndexStore()
        firstRun(settings, src, files, store)
        val counting = CountingRomsSource(src)
        val state = countState(settings, counting, files, store)
        state.load(); advanceUntilIdle()
        assertEquals(listOf(MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 1)), state.collectionRows())
        assertEquals(emptyList(), counting.collectionReads())
    }

    @Test fun a_changed_collection_file_is_the_only_one_counted_again() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val src = stampedCollections()
        val files = MemoryPrivateFiles()
        val store = MemoryLibraryIndexStore()
        firstRun(settings, src, files, store)
        src.now = 200L
        src.writeText(ContentRef("Home/Collections"), "RPGs.txt", "$mk\n$tetris\n")   // on the handheld, between runs
        val counting = CountingRomsSource(src)
        val state = countState(settings, counting, files, store)
        state.load(); advanceUntilIdle()
        assertEquals(listOf(MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 2)), state.collectionRows())
        assertEquals(listOf("readText:Home/Collections/RPGs.txt"), counting.collectionReads())
        advanceUntilIdle()
        // The new count is stored: the start after that reads nothing.
        val again = CountingRomsSource(src)
        countState(settings, again, files, store).apply { load(); advanceUntilIdle() }
        assertEquals(emptyList(), again.collectionReads())
    }

    @Test fun a_changed_library_counts_every_collection_again() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val src = stampedCollections()
        val files = MemoryPrivateFiles()
        firstRun(settings, src, files, MemoryLibraryIndexStore())
        src.delete("Home/Roms/Nintendo DS (NDS)/Mario Kart DS.nds")      // gone between runs; the next start scans
        val counting = CountingRomsSource(src)
        val state = countState(settings, counting, files, MemoryLibraryIndexStore())
        state.load(); advanceUntilIdle()
        assertEquals(listOf(MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 0)), state.collectionRows())
        assertEquals(setOf("readText:Home/Collections/RPGs.txt", "readText:Home/Collections/Racing.txt"), counting.collectionReads().toSet())
    }

    @Test fun an_unreadable_collection_is_not_cached_and_is_read_again_next_start() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val src = stampedCollections(broken = true)
        val files = MemoryPrivateFiles()
        val store = MemoryLibraryIndexStore()
        firstRun(settings, src, files, store)
        val counting = CountingRomsSource(src)
        val state = countState(settings, counting, files, store)
        state.load(); advanceUntilIdle()
        assertEquals(listOf(MainRow.Collection("Broken", null), MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 1)), state.collectionRows())
        assertEquals(listOf("readText:Home/Collections/Broken.txt"), counting.collectionReads())
    }

    @Test fun a_corrupt_count_cache_is_ignored_and_rewritten() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val src = stampedCollections()
        val files = MemoryPrivateFiles().apply { files[CollectionCountCache.FILE] = "{\"version\":1,\"entries\":[{\"name\":" }
        val state = countState(settings, src, files, MemoryLibraryIndexStore())
        state.load(); advanceUntilIdle()
        assertEquals(listOf(MainRow.Collection("Racing", 0), MainRow.Collection("RPGs", 1)), state.collectionRows())
        assertEquals(setOf("Racing", "RPGs"), com.nxredux.mobile.library.CollectionCountCodec.decode(files.files[CollectionCountCache.FILE]).keys)
    }

    @Test fun remove_from_collection_rewrites_the_file_and_an_emptied_collection_returns_to_the_page() = runTest {
        val src = sourceWithCollections()
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.openCollectionsTab(); state.onButton(NxButton.DOWN); state.onButton(NxButton.A)   // Collections -> RPGs
        choose(state, ContextKind.REMOVE_FROM_COLLECTION)
        assertEquals("/Roms/Game Boy (GB)/missing.gb\n", src.textAt("Home/Collections/RPGs.txt"))
        assertEquals("Removed from RPGs", state.notice.value)
        assertEquals(MainRow.Collection("RPGs", count = 0), assertIs<Screen.Systems>(state.screen.value).rows[state.selection.value.index])   // the Collections tab on RPGs; its one line resolves to nothing
        state.screen.value.collectionNames()
    }

    /** Opens Rename on the highlighted row, types [text] and presses A. */
    private fun rename(state: AppState, text: String) {
        choose(state, ContextKind.RENAME)
        val p = assertIs<MenuPrompt.Rename>((state.screen.value as Screen.Games).menu!!.prompt)
        state.menuDraft(text)
        assertEquals(text, assertIs<MenuPrompt.Rename>((state.screen.value as Screen.Games).menu!!.prompt).draft)
        assertEquals(p.initial, p.draft)
        state.onButton(NxButton.A)
    }

    @Test fun rename_writes_the_alias_updates_the_index_recents_and_pins_and_resorts_the_list() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Game Boy (GB)"), "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Zelda.gb", "map.txt")),
            texts = mapOf("Home/Roms/Game Boy (GB)/map.txt" to "Zelda.gb\tLink's Awakening\n"),
        )
        val data = FakeUserData(recents = listOf(Recent(tetris, "Tetris")), pins = listOf(Shortcut(tetris, "Tetris")))
        val store = MemoryLibraryIndexStore()
        val state = state(MemorySettings(ContentRef("Home")), src = src, data = data, store = store)
        state.load()
        state.onConsoles(); state.onButton(NxButton.A)                   // GB list
        assertEquals(listOf("Link's Awakening", "Tetris"), assertIs<Screen.Games>(state.screen.value).games.map { it.shownName })
        state.onButton(NxButton.DOWN)
        rename(state, "  Zed Tetris ")
        assertEquals("Tetris.gb\tZed Tetris\nZelda.gb\tLink's Awakening\n", src.textAt("Home/Roms/Game Boy (GB)/map.txt"))
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals(listOf("Link's Awakening", "Zed Tetris"), list.games.map { it.shownName })
        assertEquals(1, state.selection.value.index)                     // the row follows the renamed game
        assertNull(list.menu)
        assertEquals("Renamed", state.notice.value)
        assertEquals(listOf(Recent(tetris, "Zed Tetris")), data.recents)
        assertEquals(listOf(Shortcut(tetris, "Zed Tetris")), data.pins)
        assertTrue(LibraryIndexCodec.decode(store.text!!)!!.systems.first { it.tag == "GB" }.games.any { it.file == "Tetris.gb" && it.name == "Zed Tetris" })
        assertTrue(state.back())
        assertEquals(listOf("GB", "tools"), rowKinds(state))             // Tetris is Continue: not in the Pinned row
    }

    @Test fun rename_with_a_blank_or_unchanged_name_writes_nothing() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A)
        rename(state, "   ")
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        rename(state, " Tetris ")
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        rename(state, "a/b")
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        assertTrue(source.written.none { it.endsWith("map.txt") })
        assertNull(state.notice.value)
        choose(state, ContextKind.RENAME); state.onButton(NxButton.B)   // B cancels the prompt
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
    }

    @Test fun rename_a_home_disc_folder_keys_the_folder_name_in_the_parent_map() = runTest {
        val src = FakeRomsSource(dirs = mapOf(
            "Home" to listOf("Roms"), "Home/Roms" to listOf("PlayStation (PS)"),
            "Home/Roms/PlayStation (PS)" to listOf("FF7"), "Home/Roms/PlayStation (PS)/FF7" to listOf("FF7.m3u", "FF7 (Disc 1).cue", "FF7 (Disc 1).bin"),
        ), texts = mapOf("Home/Roms/PlayStation (PS)/FF7/FF7.m3u" to "FF7 (Disc 1).cue\n"))
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A)
        rename(state, "Final Fantasy VII")
        assertEquals("FF7\tFinal Fantasy VII\n", src.textAt("Home/Roms/PlayStation (PS)/map.txt"))
        assertEquals(listOf("Final Fantasy VII"), assertIs<Screen.Games>(state.screen.value).games.map { it.shownName })
    }

    @Test fun rename_in_an_extra_writes_the_sources_copy() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val state = state(settings)
        state.load()
        state.onConsoles()
        state.select(1); state.onButton(NxButton.A)                      // GBA: Kirby (extra) and Mario (home)
        val list = assertIs<Screen.Games>(state.screen.value)
        state.select(list.games.indexOfFirst { it.fileName == "Kirby.gba" })
        rename(state, "Kirby's Dream Land")
        assertEquals("Kirby.gba\tKirby's Dream Land\n", source.textAt("Home/Roms/.sources/${settings.extraFolders.single().id}/map.txt"))
        assertTrue(source.written.none { it.startsWith("sd/") })
    }

    @Test fun text_entry_is_open_only_while_a_rename_or_new_collection_prompt_shows() = runTest {
        val src = sourceWithCollections()
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.onConsoles()
        assertFalse(state.textEntryOpen)
        state.select(1); state.onButton(NxButton.A)                      // GB list
        assertFalse(state.textEntryOpen)
        state.onButton(NxButton.MENU); assertFalse(state.textEntryOpen)  // the menu itself is a list
        state.onButton(NxButton.B)
        choose(state, ContextKind.RENAME); assertTrue(state.textEntryOpen)
        state.onButton(NxButton.B); assertFalse(state.textEntryOpen)
        choose(state, ContextKind.ADD_TO_COLLECTION); assertFalse(state.textEntryOpen)   // the collection submenu
        repeat(2) { state.onButton(NxButton.DOWN) }; state.onButton(NxButton.A)          // New Collection…
        assertTrue(state.textEntryOpen)
        state.onButton(NxButton.B); assertFalse(state.textEntryOpen)
    }

    @Test fun a_failed_alias_write_reports_and_changes_nothing() = runTest {
        val src = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("Game Boy (GB)"), "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb")), readOnly = setOf("Home"))
        val refusing = object : com.nxredux.mobile.storage.SidecarStore {
            override suspend fun read(sourceId: String, relativeDir: String, file: String): String? = null
            override suspend fun write(sourceId: String, relativeDir: String, file: String, text: String) = false
            override suspend fun list(sourceId: String, relativeDir: String): List<String> = emptyList()
            override suspend fun delete(sourceId: String, relativeDir: String, file: String) = false
        }
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), src, RecordingRunner(), NoopPicker(), sidecarStore = refusing, compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A)
        rename(state, "New")
        assertEquals("Could not save the name", state.error.value)
        assertEquals(listOf("Tetris"), assertIs<Screen.Games>(state.screen.value).games.map { it.shownName })
    }

    @Test fun add_to_collection_lists_the_names_then_new_collection_prompts_and_creates_the_file() = runTest {
        val src = sourceWithCollections()
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.onConsoles()
        state.select(0); state.onButton(NxButton.A)                      // GB list
        choose(state, ContextKind.ADD_TO_COLLECTION)
        val sub = assertIs<Screen.Games>(state.screen.value).menu!!.sub!!
        assertEquals(listOf("Racing", "RPGs", "New Collection…"), sub.labels)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)        // RPGs
        assertEquals("$mk\n/Roms/Game Boy (GB)/missing.gb\n$tetris\n", src.textAt("Home/Collections/RPGs.txt"))
        assertEquals("Added to RPGs", state.notice.value)
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        choose(state, ContextKind.ADD_TO_COLLECTION)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)        // RPGs again
        assertEquals("Already in RPGs", state.notice.value)
        choose(state, ContextKind.ADD_TO_COLLECTION)
        repeat(2) { state.onButton(NxButton.DOWN) }; state.onButton(NxButton.A)   // New Collection…
        assertIs<MenuPrompt.NewCollection>(assertIs<Screen.Games>(state.screen.value).menu!!.prompt)
        state.menuDraft("Puzzle"); state.onButton(NxButton.A)
        assertEquals("$tetris\n", src.textAt("Home/Collections/Puzzle.txt"))
        assertEquals("Added to Puzzle", state.notice.value)
        assertTrue(state.back())
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))
        assertEquals(listOf("Puzzle", "Racing", "RPGs"), state.openCollectionsTab())   // the new collection is re-listed
    }

    @Test fun a_bad_collection_name_is_refused_and_a_first_collection_creates_the_folder() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.onConsoles()
        state.onButton(NxButton.A)
        choose(state, ContextKind.ADD_TO_COLLECTION)
        assertEquals(listOf("New Collection…"), assertIs<Screen.Games>(state.screen.value).menu!!.sub!!.labels)
        state.onButton(NxButton.A)
        state.menuDraft("a:b"); state.onButton(NxButton.A)
        assertEquals("Names cannot be empty, start with a dot or contain / \\ : * ? \" < > |", state.error.value)
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        state.dismissError()
        choose(state, ContextKind.ADD_TO_COLLECTION); state.onButton(NxButton.A)
        state.menuDraft("Favourites"); state.onButton(NxButton.A)
        assertEquals("$tetris\n", source.textAt("Home/Collections/Favourites.txt"))
        assertTrue(state.back())
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))
    }

    // --- Collections tab: Rename and Delete ---

    private fun AppState.collectionsMenu(): ContextMenu = assertNotNull(assertIs<Screen.Systems>(screen.value).menu)

    private fun AppState.highlightedCollection(): MainRow.Collection =
        assertIs<MainRow.Collection>(assertIs<Screen.Systems>(screen.value).rows[selection.value.index])

    /** On the Collections tab: Rename on the highlighted collection, [text] typed, A pressed. */
    private fun renameCollection(state: AppState, text: String) {
        choose(state, ContextKind.RENAME_COLLECTION)
        val p = assertIs<MenuPrompt.RenameCollection>(state.collectionsMenu().prompt)
        assertEquals(state.highlightedCollection().name, p.initial)      // prefilled with the current name
        assertEquals(p.initial, p.draft)
        assertTrue(state.textEntryOpen)
        state.menuDraft(text)
        state.onButton(NxButton.A)
    }

    @Test fun menu_or_a_long_press_on_a_collection_offers_rename_and_delete() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), src = sourceWithCollections())
        state.load()
        assertEquals(listOf("Racing", "RPGs"), state.openCollectionsTab())
        state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.RENAME_COLLECTION, ContextKind.DELETE_COLLECTION), menuKinds(state))
        assertEquals(listOf("Rename", "Delete"), state.collectionsMenu().items.map { it.label })
        assertEquals("Racing", state.collectionsMenu().caption)
        state.onButton(NxButton.B)
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
        state.openContextMenu(1)                                         // a long press on RPGs
        assertEquals(1, state.selection.value.index)
        assertEquals("RPGs", state.collectionsMenu().caption)
        assertEquals("RPGs", state.collectionsMenu().collection)
    }

    @Test fun rename_collection_renames_the_file_keeps_its_games_and_stays_on_the_row() = runTest {
        val src = CountingRomsSource(sourceWithCollections())
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.openCollectionsTab()
        state.select(1)                                                  // RPGs
        val before = assertNotNull(src.readText(ContentRef("Home/Collections/RPGs.txt")))
        renameCollection(state, "  Adventures ")
        assertEquals(before, src.readText(ContentRef("Home/Collections/Adventures.txt")))   // same lines, same order
        assertNull(src.readText(ContentRef("Home/Collections/RPGs.txt")))
        assertEquals(listOf("rename:Home/Collections/RPGs.txt>Adventures.txt"), src.calls.filter { it.startsWith("rename:") })
        assertEquals(listOf("Adventures", "Racing"), state.screen.value.collectionNames())
        assertEquals(MainRow.Collection("Adventures", 1), state.highlightedCollection())   // re-sorted to the top, still highlighted, counted
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
        assertFalse(state.textEntryOpen)
        assertEquals("Renamed", state.notice.value)
        assertNull(state.error.value)
        state.onButton(NxButton.A)                                       // it opens under its new name
        assertEquals(ListSource.Collection("Adventures"), assertIs<Screen.Games>(state.screen.value).source)
        assertEquals(listOf("Mario Kart DS"), assertIs<Screen.Games>(state.screen.value).games.map { it.shownName })
        assertTrue(state.back())
        assertEquals(MainRow.Collection("Adventures", 1), state.highlightedCollection())
        renameCollection(state, "adventures")                            // only the case changes: allowed
        assertEquals(listOf("adventures", "Racing"), state.screen.value.collectionNames())
        assertEquals("adventures", state.highlightedCollection().name)
    }

    @Test fun rename_collection_refuses_bad_and_taken_names_and_the_same_name_is_a_no_op() = runTest {
        val src = sourceWithCollections()
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.openCollectionsTab()
        state.select(1)                                                  // RPGs
        val written = src.written.size
        val invalid = "Names cannot be empty, start with a dot or contain / \\ : * ? \" < > |"
        for (bad in listOf("   ", "a:b", "x/y", ".hidden", "map")) {
            renameCollection(state, bad)
            assertEquals(invalid, state.error.value, bad)
            assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
            state.dismissError()
        }
        renameCollection(state, " racing ")                              // another collection's name, in another case
        assertEquals("There is already a collection named racing", state.error.value)
        state.dismissError()
        renameCollection(state, " RPGs  ")                               // the same name once trimmed: nothing happens
        assertNull(state.error.value)
        assertNull(state.notice.value)
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
        assertEquals(written, src.written.size)
        assertEquals(listOf("Racing", "RPGs"), state.screen.value.collectionNames())
        assertEquals("RPGs", state.highlightedCollection().name)
    }

    @Test fun rename_collection_cancels_with_b_or_menu() = runTest {
        val src = sourceWithCollections()
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.openCollectionsTab()
        val written = src.written.size
        for (cancel in listOf(NxButton.B, NxButton.MENU)) {
            choose(state, ContextKind.RENAME_COLLECTION)
            state.menuDraft("Other")
            state.onButton(cancel)
            assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
            assertFalse(state.textEntryOpen)
        }
        assertEquals(written, src.written.size)
        assertEquals(listOf("Racing", "RPGs"), state.screen.value.collectionNames())
    }

    @Test fun a_failed_collection_rename_reports_and_changes_nothing() = runTest {
        val src = sourceWithCollections()
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.openCollectionsTab()
        src.writable = false                                             // a read-only grant
        renameCollection(state, "Speed")
        assertEquals("Could not rename the collection", state.error.value)
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
        assertEquals(listOf("Racing", "RPGs"), state.screen.value.collectionNames())
    }

    @Test fun delete_collection_asks_then_removes_only_its_file_and_lands_on_the_next_row() = runTest {
        val src = CountingRomsSource(sourceWithCollections(broken = true))
        val data = FakeUserData(pins = listOf(Shortcut(mk, "Mario Kart DS")), recents = listOf(Recent(tetris)))
        val state = state(MemorySettings(ContentRef("Home")), src = src, data = data)
        state.load()
        assertEquals(listOf("Broken", "Racing", "RPGs"), state.openCollectionsTab())
        state.select(1)                                                  // Racing
        choose(state, ContextKind.DELETE_COLLECTION)
        assertEquals(MenuPrompt.DeleteCollection("Racing"), state.collectionsMenu().prompt)
        assertFalse(state.textEntryOpen)
        val mark = src.calls.size
        state.onButton(NxButton.A)                                       // Delete
        assertEquals(listOf("delete:Home/Collections/Racing.txt"), src.calls.drop(mark).filter { it.startsWith("delete:") || it.startsWith("rename:") || it.startsWith("write") })
        assertEquals(listOf("Broken", "RPGs"), state.screen.value.collectionNames())
        assertEquals("RPGs", state.highlightedCollection().name)         // the row after it
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
        assertEquals("Deleted Racing", state.notice.value)
        assertEquals(listOf(Shortcut(mk, "Mario Kart DS")), data.pins)   // other user data untouched
        assertEquals(listOf(Recent(tetris)), data.recents)
        choose(state, ContextKind.DELETE_COLLECTION); state.onButton(NxButton.A)   // RPGs, the last row
        assertEquals(listOf("Broken"), state.screen.value.collectionNames())
        assertEquals("Broken", state.highlightedCollection().name)       // the row before it
        assertTrue(listOf("GB", "GBA", "NDS").all { it in rowKinds(state) })   // the games stay in the library
    }

    @Test fun deleting_the_last_collection_drops_the_tab() = runTest {
        val src = sourceWithCollections().apply { delete("Home/Collections/Racing.txt") }
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        assertEquals(listOf("RPGs"), state.openCollectionsTab())
        choose(state, ContextKind.DELETE_COLLECTION); state.onButton(NxButton.A)
        assertEquals(listOf("GB", "GBA", "NDS", "tools"), rowKinds(state))
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertNull(s.menu)
        assertTrue(s.category != MenuCategory.COLLECTIONS)
        assertEquals("Deleted RPGs", state.notice.value)
        assertNull(state.error.value)
    }

    @Test fun delete_collection_cancels_with_b_and_a_failed_delete_reports() = runTest {
        val src = sourceWithCollections()
        val state = state(MemorySettings(ContentRef("Home")), src = src)
        state.load()
        state.openCollectionsTab()
        choose(state, ContextKind.DELETE_COLLECTION)
        state.onButton(NxButton.B)
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
        assertEquals("", src.textAt("Home/Collections/Racing.txt"))
        src.writable = false
        choose(state, ContextKind.DELETE_COLLECTION); state.onButton(NxButton.A)
        assertEquals("Could not delete the collection", state.error.value)
        assertEquals(listOf("Racing", "RPGs"), state.screen.value.collectionNames())
    }

    @Test fun back_from_a_console_list_lands_on_that_console_even_when_rows_were_inserted_above() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.onConsoles()
        assertEquals(listOf("GB", "GBA", "NDS", "tools"), rowKinds(state))
        state.select(2); state.onButton(NxButton.A)                      // NDS list
        choose(state, ContextKind.ADD_TO_COLLECTION); state.onButton(NxButton.A)   // New Collection…
        state.menuDraft("Puzzle"); state.onButton(NxButton.A)
        assertTrue(state.back())
        assertEquals(listOf("GB", "GBA", "NDS", "collections", "tools"), rowKinds(state))
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        assertEquals(2, state.selection.value.index)                     // NDS
    }

    // --- Consoles merged by display name (NX Redux collates sibling Roms/<Name> (<TAG>)/ folders) ---

    private val snes = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Super Nintendo ES (SFC)", "Super Nintendo ES (SUPA)", "Game Boy (GB)"),
            "Home/Roms/Super Nintendo ES (SFC)" to listOf("Zelda.sfc", "Mario World.sfc", "Contra (USA).sfc"),
            "Home/Roms/Super Nintendo ES (SUPA)" to listOf("aladdin.sfc", "Mario World.sfc", "Contra (Japan).sfc", "systems.txt"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
        ),
        // A folder's tag names only its console: these games are on Supafaust by their own Emulator pick.
        texts = mapOf("Home/Roms/Super Nintendo ES (SUPA)/systems.txt" to "aladdin.sfc\tSUPA\nMario World.sfc\tSUPA\nContra (Japan).sfc\tSUPA\n"),
    )

    private fun mergedRow(state: AppState) = assertIs<Screen.Systems>(state.screen.value).rows.filterIsInstance<MainRow.System>().first { it.system.displayName == "Super Nintendo ES" }

    @Test fun consoles_sharing_a_display_name_are_one_main_row_and_a_lone_tag_is_unchanged() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), snes)
        state.load()
        state.onConsoles()
        val rows = assertIs<Screen.Systems>(state.screen.value).rows.filterIsInstance<MainRow.System>()
        assertEquals(listOf(listOf("GB"), listOf("SFC", "SUPA")), rows.map { r -> r.systems.map { it.tag } })
        assertEquals("SFC", mergedRow(state).backdrop.tag)                   // the console's default tag gives the background
        state.openSystem(rows[0].system)                                     // Game Boy: one tag, as before
        val gb = assertIs<Screen.Games>(state.screen.value)
        assertEquals(ListSource.Console(rows[0].system), gb.source)
        assertEquals(listOf(null), gb.entries.map { it.label })
    }

    @Test fun a_merged_row_lists_every_tag_by_name_with_unique_labels_and_launches_under_the_game_tag() = runTest {
        val runner = RecordingRunner()
        val state = state(MemorySettings(ContentRef("Home")), snes, runner = runner)
        state.load()
        state.onConsoles()
        val row = mergedRow(state)
        state.select(assertIs<Screen.Systems>(state.screen.value).rows.indexOf(row))
        state.openSelected()
        val games = assertIs<Screen.Games>(state.screen.value)
        assertEquals("Super Nintendo ES", games.source.title)
        assertEquals(listOf("SFC", "SUPA"), (games.source as ListSource.Console).systems.map { it.tag })
        assertEquals(
            listOf("aladdin", "Contra (USA) (SFC)", "Contra (Japan) (SUPA)", "Mario World (SFC)", "Mario World (SUPA)", "Zelda"),
            games.entries.map { it.shown },
        )
        assertEquals(listOf("SUPA", "SFC", "SUPA", "SFC", "SUPA", "SFC"), games.entries.map { it.system.tag })
        assertEquals("Mario World", games.entries[4].game.shownName)        // the label is display only
        state.select(4); state.openSelected()                               // Mario World (SUPA)
        assertEquals("SUPA", runner.launched.single().systemTag)
        assertEquals(ContentRef("Home/Roms/Super Nintendo ES (SUPA)/Mario World.sfc"), runner.launched.single().content)
        assertEquals("Mario World", runner.launched.single().displayName)
        assertTrue(state.back())                                            // back on the merged row
        assertEquals(mergedRow(state), assertIs<Screen.Systems>(state.screen.value).rows[state.selection.value.index])
    }

    @Test fun the_emulator_switch_in_a_merged_list_moves_the_game_and_stays_in_the_merged_list() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), snes)
        state.load()
        state.onConsoles()
        state.openSystem(mergedRow(state).systems[1])                       // any of its systems opens the whole console
        val before = assertIs<Screen.Games>(state.screen.value)
        assertEquals(6, before.entries.size)
        state.select(0)                                                     // aladdin, a SUPA game
        choose(state, ContextKind.EMULATOR)
        val sub = assertIs<Screen.Games>(state.screen.value).menu!!.sub!!
        assertEquals(1, sub.current)                                        // its own tag highlighted, not the list's first
        state.onButton(NxButton.B); state.onButton(NxButton.B)              // the submenu, then the menu
        state.select(5)                                                     // Zelda, an SFC game
        choose(state, ContextKind.EMULATOR)
        assertEquals(0, assertIs<Screen.Games>(state.screen.value).menu!!.sub!!.current)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)          // -> SUPA
        assertEquals("Zelda.sfc\tSUPA\n", snes.textAt("Home/Roms/Super Nintendo ES (SFC)/systems.txt"))
        val after = assertIs<Screen.Games>(state.screen.value)
        assertNull(after.menu)
        assertEquals(listOf("SFC", "SUPA"), (after.source as ListSource.Console).systems.map { it.tag })
        assertEquals(6, after.entries.size)
        val zelda = after.entries[state.selection.value.index]              // the row stays on the moved game
        assertEquals("Zelda", zelda.shown)
        assertEquals("SUPA", zelda.system.tag)
        assertTrue(state.back())
        assertEquals(mergedRow(state), assertIs<Screen.Systems>(state.screen.value).rows[state.selection.value.index])
    }

    @Test fun hiding_the_last_game_of_one_tag_keeps_the_merged_list_on_the_other() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"),
                "Home/Roms" to listOf("Game Boy Advance (GBA)", "Game Boy Advance (MGBA)"),
                "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba", "Kirby.gba"),
                "Home/Roms/Game Boy Advance (MGBA)" to listOf("Zelda.gba", "systems.txt"),
            ),
            texts = mapOf("Home/Roms/Game Boy Advance (MGBA)/systems.txt" to "Zelda.gba\tMGBA\n"),
        )
        val state = state(MemorySettings(ContentRef("Home")), src, data = FakeUserData())
        state.load()
        state.onConsoles()
        state.openSystem(mergedRow2(state).system)
        assertEquals(listOf("Kirby", "Mario", "Zelda"), assertIs<Screen.Games>(state.screen.value).entries.map { it.shown })
        state.select(2)
        choose(state, ContextKind.HIDE)                                     // Zelda: MGBA's only game
        val games = assertIs<Screen.Games>(state.screen.value)
        assertEquals(listOf("Kirby", "Mario"), games.entries.map { it.shown })
        assertEquals(listOf("GBA"), (games.source as ListSource.Console).systems.map { it.tag })
        assertTrue(state.back())
        val row = assertIs<MainRow.System>(assertIs<Screen.Systems>(state.screen.value).rows[state.selection.value.index])
        assertEquals(listOf("GBA"), row.systems.map { it.tag })
    }

    private fun mergedRow2(state: AppState) = assertIs<Screen.Systems>(state.screen.value).rows.filterIsInstance<MainRow.System>().single()

    // --- Main menu tabs: L1/R1 step them, each keeps its row, every return lands on the right tab ---

    /** Two consoles (GB: Tetris, GBA: Mario); [coOp]: a Collections folder with "Co-op" holding Tetris. */
    private fun twoConsoles(coOp: Boolean = false) = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOfNotNull("Roms", "Collections".takeIf { coOp }),
            "Home/Roms" to listOf("Game Boy (GB)", "Game Boy Advance (GBA)"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
        ) + (if (coOp) mapOf("Home/Collections" to listOf("Co-op.txt")) else emptyMap()),
        texts = if (coOp) mapOf("Home/Collections/Co-op.txt" to "$tetris\n") else emptyMap(),
    )

    /** A library with two consoles and one pin (Mario), no collections, loaded. */
    private fun TestScope.pinnedLibrary(runner: RecordingRunner = RecordingRunner()): AppState =
        state(MemorySettings(ContentRef("Home")), src = twoConsoles(), runner = runner, data = FakeUserData(pins = listOf(Shortcut(mario, "Mario"))))
            .also { it.load() }

    @Test fun the_main_menu_opens_on_the_first_tab_and_l1_r1_step_the_tabs_with_wrap() = runTest {
        val state = pinnedLibrary()
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.TOOLS), s.tabs.map { it.category })
        assertEquals(MenuCategory.HOME, s.category)
        state.onButton(NxButton.R1)
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.L1); state.onButton(NxButton.L1)
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun each_tab_remembers_its_row() = runTest {
        val state = pinnedLibrary()                       // Consoles tab has two rows
        state.onButton(NxButton.R1)                       // Consoles
        state.onButton(NxButton.DOWN)                     // second console
        state.onButton(NxButton.R1); state.onButton(NxButton.L1)   // Tools and back
        assertEquals(1, state.selection.value.index)
        assertEquals(2, state.selection.value.count)
    }

    @Test fun b_from_a_console_list_lands_on_consoles_on_that_console() = runTest {
        val state = pinnedLibrary()
        state.onButton(NxButton.R1); state.onButton(NxButton.DOWN)   // Consoles, second console
        val chosen = assertIs<MainRow.System>(assertIs<Screen.Systems>(state.screen.value).rows[1])
        state.onButton(NxButton.A)
        assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.B)
        val back = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.CONSOLES, back.category)
        assertEquals(chosen, back.rows[state.selection.value.index])
    }

    @Test fun a_pin_launch_returns_to_home_on_its_row() = runTest {
        val runner = RecordingRunner()
        // Two pins: the launched one becomes Continue and leaves the rows; focus follows it to Continue.
        val state = state(MemorySettings(ContentRef("Home")), src = twoConsoles(), runner = runner, data = FakeUserData(pins = listOf(Shortcut(mario, "Mario"), Shortcut(tetris, "Tetris"))))
            .also { it.load() }
        state.selectHome { it is MainRow.Pin && it.entry.game.path == mario }
        val at = state.selection.value.index
        state.onButton(NxButton.A)                                       // A launches the pin (RecordingRunner records it)
        assertEquals(1, runner.launched.size)
        state.load()   // returning from the game re-shows the library, as the host does
        advanceUntilIdle()
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).category)
        assertEquals(tetris, assertIs<MainRow.Pin>(state.screen.value.homeRows[at]).entry.game.path)   // Mario is Continue now
        assertEquals(0, state.selection.value.index)                     // on Continue: the game just played
        assertEquals(mario, assertIs<MainRow.Continue>(state.screen.value.homeRows[0]).entry.game.path)
    }

    @Test fun tools_is_a_tab_and_b_from_a_tool_page_lands_back_on_it() = runTest {
        val state = pinnedLibrary()
        state.openTools()
        val tools = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.TOOLS, tools.category)
        val settingsAt = tools.rows.indexOfFirst { (it as MainRow.Tool).row.kind == ToolKind.SETTINGS }
        state.select(settingsAt); state.onButton(NxButton.A)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        state.onButton(NxButton.B)
        val back = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.TOOLS, back.category)
        assertEquals(settingsAt, state.selection.value.index)
    }

    // A pin or recent written for an extra copy the scan hides behind the home game (same file, loose):
    // it shows as the home game, and Unpin / Remove clear that line too.
    private suspend fun TestScope.hiddenCopyLibrary(data: FakeUserData, write: FakeUserData.(String) -> Unit): AppState {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"),
                "Home/Roms" to listOf("Game Boy (GB)"),
                "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
                "sd/Loose" to listOf("Tetris.gb"),
            ),
        )
        val settings = MemorySettings(ContentRef("Home")).apply { extraFolders = listOf(ExtraFolder("e1", ContentRef("sd/Loose"))) }
        val state = state(settings, src = src, data = data).also { it.load() }
        data.write("/Extra/${settings.extraFolders.single().id}/Tetris.gb")   // the copy's line (the folder's id is the app's)
        return state.also { it.load() }
    }

    @Test fun unpinning_a_pin_written_for_a_hidden_copy_removes_it() = runTest {
        val data = FakeUserData()
        val state = hiddenCopyLibrary(data) { pins = listOf(Shortcut(it, "Tetris")) }
        state.selectHome { it is MainRow.Pin }
        choose(state, ContextKind.UNPIN)
        advanceUntilIdle()
        assertEquals(emptyList(), data.pins)
    }

    @Test fun removing_a_recent_written_for_a_hidden_copy_removes_it() = runTest {
        val data = FakeUserData()
        val state = hiddenCopyLibrary(data) { recents = listOf(Recent(it)) }
        state.openList(ListSource.Recent)
        assertEquals(listOf("Tetris"), assertIs<Screen.Games>(state.screen.value).games.map { it.shownName })
        choose(state, ContextKind.REMOVE_RECENT)
        advanceUntilIdle()
        assertEquals(emptyList(), data.recents)
    }

    @Test fun unpinning_the_last_pin_stays_on_home() = runTest {
        val state = pinnedLibrary()
        state.selectHome { it is MainRow.Pin }
        // one pin; open its menu, choose Unpin (the MENU flow of choose())
        state.onButton(NxButton.MENU)
        val kinds = menuKinds(state)
        val at = kinds.indexOf(ContextKind.UNPIN); assertTrue(at >= 0, "no UNPIN in $kinds")
        repeat(at) { state.onButton(NxButton.DOWN) }
        state.onButton(NxButton.A)
        advanceUntilIdle()
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.TOOLS), s.tabs.map { it.category })
        assertEquals(MenuCategory.HOME, s.category)
        assertEquals(listOf(MainRow.PickAGame), s.rows.filterNot { it is MainRow.Shelf })                  // nothing played, no pins left: a fresh Home
        assertEquals(0, state.selection.value.index)
    }

    @Test fun continue_resumes_from_its_slot_and_its_hint_says_resume_only_with_a_save() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(recents = listOf(Recent(tetris), Recent(mk)), resumable = tetrisResumable)
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load()
        advanceUntilIdle()
        val menu = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.HOME, menu.category)
        assertTrue(state.resumeInfo.value.canResume)                     // Tetris has a save
        assertTrue(Hint(NxButton.A, "Resume") in mainMenuHints(menu.rows[0], state.resumeInfo.value.canResume))
        state.onButton(NxButton.X)                                       // X does nothing on the main menu
        assertTrue(runner.launched.isEmpty())
        state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals(9, runner.launched.single().resumeSlot)
        assertEquals("GB", runner.launched.single().systemTag)
        data.recents = listOf(Recent(mk), Recent(tetris))                // Mario Kart DS played since (no save)
        state.onForeground(); advanceUntilIdle()
        assertEquals("Mario Kart DS.nds", assertIs<MainRow.Continue>(state.screen.value.homeRows[0]).entry.game.fileName)
        assertFalse(state.resumeInfo.value.canResume)                    // no save: A Play
        assertTrue(Hint(NxButton.A, "Play") in mainMenuHints(state.screen.value.homeRows[0], state.resumeInfo.value.canResume))
    }

    @Test fun a_tab_that_vanishes_lands_the_fallback_on_its_own_row() = runTest {
        val src = sourceWithCollections()
        val state = state(MemorySettings(ContentRef("Home")), src = src, data = FakeUserData(recents = listOf(Recent(tetris)), pins = listOf(Shortcut(mario, "Mario"))))
        state.load()
        state.openCollectionsTab(); state.select(1)                      // Collections, the second collection
        src.delete("Home/Collections/RPGs.txt"); src.delete("Home/Collections/Racing.txt")   // outside the app
        state.rescan()                                                   // the Collections tab goes
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.TOOLS, s.category)                     // the next visible tab after Collections
        assertEquals(0, state.selection.value.index)                     // Tools' own row, not Collections' old index
    }

    @Test fun hidden_tabs_leave_the_tab_row() = runTest {
        val state = hiddenTabsState(MenuCategory.CONSOLES)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.TOOLS), assertIs<Screen.Systems>(state.screen.value).tabs.map { it.category })
    }

    @Test fun with_home_hidden_the_menu_opens_on_the_first_visible_tab_and_home_rebuilds_are_harmless() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = hiddenTabsState(MenuCategory.HOME, settings = settings, data = FakeUserData(recents = listOf(Recent(tetris))))
        fun s() = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(listOf(MenuCategory.CONSOLES, MenuCategory.TOOLS), s().tabs.map { it.category })
        assertEquals(MenuCategory.CONSOLES, s().category)
        state.onHomeLayout(LAND); advanceUntilIdle()                          // a Home frame: Home rebuilt, not shown
        settings.menuStyles = LIST_TABS + (MenuCategory.HOME to MenuStyle.CAROUSEL)
        state.onForeground(); advanceUntilIdle()                              // Home rebuilt in another layout
        assertEquals(MenuCategory.CONSOLES, s().category)
        assertTrue(s().tabs.none { it.category == MenuCategory.HOME })
        state.goHome()
        assertEquals(MenuCategory.CONSOLES, s().category)                     // Home pressed: the first visible tab
    }

    @Test fun a_single_tab_never_switches_or_takes_the_tab_row() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.TOOLS to MenuStyle.CAROUSEL) }
        val state = hiddenTabsState(MenuCategory.HOME, MenuCategory.CONSOLES, settings = settings)
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(listOf(MenuCategory.TOOLS), s.tabs.map { it.category })
        assertEquals("NX Redux", mainMenuTitle(s))
        for (b in listOf(NxButton.L1, NxButton.R1)) { state.onButton(b); assertEquals(MenuCategory.TOOLS, state.category()) }
        state.select(0); state.onButton(NxButton.LEFT)                        // past the first item: no tab to switch to
        assertEquals(MenuCategory.TOOLS, state.category())
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.UP)                                           // a row's UP reaches the tab row: a title here
        assertFalse(state.tabFocus.value)
    }

    @Test fun the_current_tab_hidden_lands_on_the_next_visible_tab() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = hiddenTabsState(settings = settings)
        state.onConsoles()
        settings.showConsolesTab = false
        state.onForeground(); advanceUntilIdle()                              // the rows rebuilt and swapped in
        assertEquals(MenuCategory.TOOLS, state.category())                    // the next after Consoles, not Home
    }

    @Test fun b_from_a_collection_list_lands_on_collections_on_it() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), src = twoConsoles(coOp = true))
        state.load()                                      // a library with a collection "Co-op" holding one resolvable game
        val tabs = assertIs<Screen.Systems>(state.screen.value).tabs
        state.selectTab(tabs.indexOfFirst { it.category == MenuCategory.COLLECTIONS })
        state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals(ListSource.Collection("Co-op"), assertIs<Screen.Games>(state.screen.value).source)
        state.onButton(NxButton.B)
        val back = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.COLLECTIONS, back.category)
        assertEquals(MainRow.Collection("Co-op", count = 1), back.rows[state.selection.value.index])   // the count is what the list showed
    }

    private fun AppState.pushedMenu(): Screen.Systems = assertIs<Screen.Systems>(screen.value).also { assertTrue(it.pushed, "not pushed") }

    @Test fun a_hidden_tab_opens_over_the_tab_and_b_returns_where_it_was() = runTest {
        val state = hiddenTabsState(MenuCategory.CONSOLES, data = FakeUserData(recents = listOf(Recent(tetris)), pins = listOf(Shortcut(mario, "Mario"))))
        state.select(1)
        val under = assertIs<Screen.Systems>(state.screen.value).rows[1]
        state.openHiddenTab(MenuCategory.CONSOLES)
        val pushed = state.pushedMenu()
        assertEquals(listOf(MenuCategory.CONSOLES), pushed.tabs.map { it.category })
        assertTrue(pushed.rows.all { it is MainRow.System })
        assertEquals(0, state.selection.value.index)                          // a push opens on its first item
        assertEquals("Consoles", mainMenuTitle(pushed))
        for (b in listOf(NxButton.L1, NxButton.R1, NxButton.LEFT, NxButton.RIGHT)) { state.onButton(b); assertTrue(state.pushedMenu().pushed, "$b") }
        state.openHiddenTab(MenuCategory.CONSOLES)                            // already pushed: nothing stacks
        assertEquals(1, state.pushedMenu().tabs.size)
        state.onButton(NxButton.B)
        val back = assertIs<Screen.Systems>(state.screen.value)
        assertFalse(back.pushed)
        assertEquals(MenuCategory.HOME, back.category)
        assertEquals(under, back.rows[state.selection.value.index])           // focus where it was
    }

    @Test fun a_visible_tab_is_never_pushed() = runTest {
        val state = hiddenTabsState()
        state.openHiddenTab(MenuCategory.CONSOLES)
        assertFalse(assertIs<Screen.Systems>(state.screen.value).pushed)
    }

    @Test fun a_hidden_home_is_never_pushed() = runTest {
        val state = hiddenTabsState(MenuCategory.HOME, data = FakeUserData(recents = listOf(Recent(tetris))))
        state.openHiddenTab(MenuCategory.HOME)
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertFalse(s.pushed)
        assertEquals(MenuCategory.CONSOLES, s.category)
    }

    @Test fun a_console_opened_from_a_pushed_tab_returns_to_it() = runTest {
        val state = hiddenTabsState(MenuCategory.CONSOLES)
        state.openHiddenTab(MenuCategory.CONSOLES)
        val gb = state.pushedMenu().rows.indexOfFirst { (it as MainRow.System).system.tag == "GB" }
        state.select(gb); state.onButton(NxButton.A)
        assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.B)
        assertEquals(gb, state.selection.value.index)
        assertEquals(MenuCategory.CONSOLES, state.pushedMenu().category)
    }

    @Test fun a_game_launched_from_a_pushed_tab_returns_to_the_main_menus_tab() = runTest {
        val runner = RecordingRunner()
        val state = state(MemorySettings(ContentRef("Home")).apply { showConsolesTab = false }, runner = runner).also { it.load() }
        state.openHiddenTab(MenuCategory.CONSOLES)
        val gb = state.pushedMenu().rows.indexOfFirst { (it as MainRow.System).system.tag == "GB" }
        state.select(gb); state.onButton(NxButton.A)
        state.play(assertIs<Screen.Games>(state.screen.value).games[0])
        assertEquals(1, runner.launched.size)
        state.onButton(NxButton.B)                                            // back from the game's list
        val menu = assertIs<Screen.Systems>(state.screen.value)
        assertFalse(menu.pushed)                                              // the pushed tab is not restored
        assertEquals(MenuCategory.HOME, menu.category)
    }

    @Test fun a_pushed_grid_wraps_left_and_right_at_its_ends() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.CONSOLES to MenuStyle.GRID) }
        val state = hiddenTabsState(MenuCategory.CONSOLES, settings = settings)
        state.openHiddenTab(MenuCategory.CONSOLES)
        state.onGridLayout(3, 2)
        val last = state.pushedMenu().rows.lastIndex
        state.select(0); state.onButton(NxButton.LEFT)
        assertEquals(last, state.selection.value.index)
        state.onButton(NxButton.RIGHT)
        assertEquals(0, state.selection.value.index)
        state.onButton(NxButton.LEFT, repeat = true)                          // a held key stops at the end
        assertEquals(0, state.selection.value.index)
        assertTrue(state.pushedMenu().pushed)
    }

    @Test fun a_pushed_tab_that_empties_drops_back_to_the_tab_underneath() = runTest {
        val src = sourceWithCollections()
        val state = hiddenTabsState(MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, src = src)
        state.openHiddenTab(MenuCategory.COLLECTIONS)
        state.openHiddenTab(MenuCategory.CONSOLES)                            // over a pushed tab: ignored
        assertEquals(MenuCategory.COLLECTIONS, state.pushedMenu().category)
        src.delete("Home/Collections/RPGs.txt"); src.delete("Home/Collections/Racing.txt")
        state.rescan(); advanceUntilIdle()
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertFalse(s.pushed)
        assertEquals(MenuCategory.HOME, s.category)
    }

    /** Consoles hidden and pushed over Home, with Tetris (resumable) in the switcher; [gone]: Tetris deleted, so its launch fails. */
    private fun TestScope.switcherOverPushedConsoles(runner: RecordingRunner, gone: Boolean): AppState {
        val src = sourceWithCollections()
        val settings = MemorySettings(ContentRef("Home")).apply { showConsolesTab = false }
        val data = FakeUserData(recents = listOf(Recent(tetris)), resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9)))
        val state = state(settings, src = src, runner = runner, data = data).also { it.load(); advanceUntilIdle() }
        state.openHiddenTab(MenuCategory.CONSOLES)
        state.pushedMenu()
        state.onButton(NxButton.SELECT); advanceUntilIdle()
        assertIs<Screen.Switcher>(state.screen.value)
        if (gone) src.delete("Home/Roms/Game Boy (GB)/Tetris.gb")
        state.onButton(NxButton.A); advanceUntilIdle()
        return state
    }

    @Test fun a_game_launched_from_the_switcher_over_a_pushed_tab_returns_to_the_tab_underneath() = runTest {
        val runner = RecordingRunner()
        val state = switcherOverPushedConsoles(runner, gone = false)
        assertEquals(1, runner.launched.size)
        state.onForeground(); advanceUntilIdle()
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertFalse(s.pushed)
        assertEquals(MenuCategory.HOME, s.category)
    }

    @Test fun a_failed_switcher_launch_over_a_pushed_tab_returns_to_the_tab_underneath() = runTest {
        val runner = RecordingRunner()
        val state = switcherOverPushedConsoles(runner, gone = true)
        assertTrue(runner.launched.isEmpty())
        // A launch that never reaches the runner ends nothing: the push stays, B lands on the tab underneath.
        assertEquals(MenuCategory.CONSOLES, state.pushedMenu().category)
        state.dismissError()
        state.onButton(NxButton.B)
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertFalse(s.pushed)
        assertEquals(MenuCategory.HOME, s.category)
    }

    /** Collections hidden and pushed, its "Mixed" collection (Tetris) opened as a list. (A coreless game is not listed in a collection, so that guard has no pushed-list path.) */
    private fun pushedCollectionsSource() = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms", "Collections"),
                "Home/Roms" to listOf("Commodore 64 (C64)", "Game Boy (GB)"),
                "Home/Roms/Commodore 64 (C64)" to listOf("Boulder Dash 2.chd"),
                "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
                "Home/Collections" to listOf("Mixed.txt"),
            ),
            texts = mapOf("Home/Collections/Mixed.txt" to "/Roms/Commodore 64 (C64)/Boulder Dash 2.chd\n$tetris\n"),
        )

    private fun TestScope.listFromPushedCollections(runner: RecordingRunner, src: FakeRomsSource = pushedCollectionsSource()): AppState {
        val state = hiddenTabsState(MenuCategory.COLLECTIONS, src = src, runner = runner)
        state.openHiddenTab(MenuCategory.COLLECTIONS)
        state.pushedMenu()
        state.select(0); state.onButton(NxButton.A)
        assertIs<Screen.Games>(state.screen.value)
        return state
    }

    @Test fun a_vanished_game_in_a_list_from_a_pushed_tab_keeps_the_push() = runTest {
        val runner = RecordingRunner()
        val src = pushedCollectionsSource()
        val state = listFromPushedCollections(runner, src)
        val gb = assertIs<Screen.Games>(state.screen.value).entries.indexOfFirst { it.system.core != null }
        src.delete("Home/Roms/Game Boy (GB)/Tetris.gb")                       // gone from the tree, still listed
        state.select(gb); state.onButton(NxButton.A); advanceUntilIdle()
        assertTrue(runner.launched.isEmpty())
        assertNotNull(state.error.value)                                      // "Could not find ..."
        state.dismissError()
        state.onButton(NxButton.B)
        assertEquals(MenuCategory.COLLECTIONS, state.pushedMenu().category)
    }

    @Test fun a_game_that_launches_from_a_list_over_a_pushed_tab_ends_the_push() = runTest {
        val runner = RecordingRunner()
        val state = listFromPushedCollections(runner)
        val gb = assertIs<Screen.Games>(state.screen.value).entries.indexOfFirst { it.system.core != null }
        state.select(gb); state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals(1, runner.launched.size)
        state.onButton(NxButton.B)
        assertFalse(assertIs<Screen.Systems>(state.screen.value).pushed)
    }

    @Test fun pick_a_game_opens_a_hidden_consoles_tab_pushed() = runTest {
        val state = hiddenTabsState(MenuCategory.CONSOLES)                    // no recents: Home's first item is Pick a game
        assertEquals(MainRow.PickAGame, assertIs<Screen.Systems>(state.screen.value).rows[0])
        state.select(0); state.onButton(NxButton.A)
        assertEquals(MenuCategory.CONSOLES, state.pushedMenu().category)
        state.onButton(NxButton.B)                                            // back over Home, on Pick a game
        assertEquals(MenuCategory.HOME, state.category())
        assertEquals(MainRow.PickAGame, assertIs<Screen.Systems>(state.screen.value).rows[state.selection.value.index])
    }

    @Test fun more_tools_opens_a_hidden_tools_tab_pushed_over_home() = runTest {
        val state = b2Home(games = 6, tools = 7, hidden = setOf(MenuCategory.TOOLS)); state.load(); advanceUntilIdle()
        state.onHomeLayout(TALL); advanceUntilIdle()                          // UNDER's 5 slots: "+3"
        assertTrue(assertIs<Screen.Systems>(state.screen.value).tabs.none { it.category == MenuCategory.TOOLS })
        val more = state.screen.value.homeRows.indexOfFirst { it is MainRow.MoreTools }
        assertTrue(more > 0)
        state.select(more); state.onButton(NxButton.A)                        // "+3": Tools over Home
        assertEquals(MenuCategory.TOOLS, state.pushedMenu().category)
        state.onButton(NxButton.B)                                            // back on Home, on "+3"
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).also { assertFalse(it.pushed) }.category)
        assertIs<MainRow.MoreTools>(state.homeFocus())
    }

    @Test fun menu_offers_hidden_tabs_after_the_items_own() = runTest {
        val state = hiddenTabsState(MenuCategory.CONSOLES, data = FakeUserData(recents = listOf(Recent(tetris))))
        state.select(0); state.onButton(NxButton.MENU)                        // Continue: Pin, then the hidden tab
        assertEquals(listOf(ContextKind.PIN, ContextKind.OPEN_CONSOLES), menuKinds(state))
        state.onButton(NxButton.B)
        choose(state, ContextKind.OPEN_CONSOLES)
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).also { assertTrue(it.pushed) }.category)
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
    }

    @Test fun a_row_without_a_menu_gets_one_holding_just_the_hidden_tabs() = runTest {
        val state = hiddenTabsState(MenuCategory.CONSOLES)                    // Home: Pick a game
        state.select(0); state.onButton(NxButton.MENU)
        val menu = assertNotNull(assertIs<Screen.Systems>(state.screen.value).menu)
        assertEquals(listOf(ContextKind.OPEN_CONSOLES), menu.items.map { it.kind })
        assertEquals("NX Redux", menu.caption)
    }

    @Test fun a_long_press_on_pick_a_game_with_tools_hidden_offers_tools() = runTest {
        val state = hiddenTabsState(MenuCategory.TOOLS)                       // no recents: Home's first item is Pick a game
        val pick = assertIs<Screen.Systems>(state.screen.value).rows.indexOf(MainRow.PickAGame)
        assertTrue(pick >= 0)
        state.openContextMenu(pick)                                           // a touch-only long press: no title, no hints needed
        val menu = assertNotNull(assertIs<Screen.Systems>(state.screen.value).menu)
        assertEquals(listOf(ContextKind.OPEN_TOOLS), menu.items.map { it.kind })
        assertEquals("NX Redux", menu.caption)
    }

    @Test fun a_long_press_on_the_tab_row_with_nothing_hidden_keeps_the_tab_focus() = runTest {
        val state = hiddenTabsState()
        state.onButton(NxButton.UP); assertTrue(state.tabFocus.value)          // Home's first item: UP goes to the tab row
        state.openTabMenu()                                                   // nothing to offer: the focus stays put
        assertTrue(state.tabFocus.value)
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
    }

    @Test fun nothing_hidden_keeps_todays_menus() = runTest {
        val state = hiddenTabsState()
        state.onConsoles(); state.onButton(NxButton.MENU)                     // a console row has no menu
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
        state.openTabMenu()                                                   // the long press: nothing to offer
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
    }

    @Test fun hiding_tools_never_locks_out_settings() = runTest {
        val state = hiddenTabsState(MenuCategory.TOOLS)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES), assertIs<Screen.Systems>(state.screen.value).tabs.map { it.category })
        state.openTabMenu()                                                   // a long press on the tab row
        assertEquals(listOf(ContextKind.OPEN_TOOLS), menuKinds(state))
        state.onButton(NxButton.A)
        val tools = assertIs<Screen.Systems>(state.screen.value).also { assertTrue(it.pushed) }
        val settingsRow = tools.rows.indexOfFirst { (it as? MainRow.Tool)?.row?.kind == ToolKind.SETTINGS }
        state.select(settingsRow); state.onButton(NxButton.A)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        state.onButton(NxButton.B)                                            // back to the pushed Tools, on Settings
        assertTrue(assertIs<Screen.Systems>(state.screen.value).pushed)
        assertEquals(settingsRow, state.selection.value.index)
        state.onButton(NxButton.B)
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).also { assertFalse(it.pushed) }.category)
    }

    @Test fun showing_tools_again_from_its_pushed_settings_lands_on_the_tab_underneath() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = hiddenTabsState(MenuCategory.TOOLS, settings = settings)
        state.openHiddenTab(MenuCategory.TOOLS)
        val settingsRow = assertIs<Screen.Systems>(state.screen.value).rows.indexOfFirst { (it as? MainRow.Tool)?.row?.kind == ToolKind.SETTINGS }
        state.select(settingsRow); state.onButton(NxButton.A)
        state.openSettings(SettingsSection.MAIN_MENU)
        state.selectLayoutsRow(SettingsKind.TAB_SHOWN, MenuCategory.TOOLS); state.onButton(NxButton.A)
        assertTrue(settings.showToolsTab)
        state.onButton(NxButton.B); state.onButton(NxButton.B); state.onButton(NxButton.B)   // Layouts, the Settings menu, then the tab
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertFalse(s.pushed)
        assertEquals(MenuCategory.HOME, s.category)
        assertTrue(s.tabs.any { it.category == MenuCategory.TOOLS })
    }

    @Test fun a_pushed_tabs_items_keep_their_menu_without_the_hidden_tabs() = runTest {
        val state = hiddenTabsState(MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, src = sourceWithCollections())
        state.openHiddenTab(MenuCategory.COLLECTIONS)
        state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.RENAME_COLLECTION, ContextKind.DELETE_COLLECTION), menuKinds(state))
        state.onButton(NxButton.B)
        state.openTabMenu()                                                   // no long-press menu over a pushed tab
        assertNull(assertIs<Screen.Systems>(state.screen.value).menu)
    }

    @Test fun tabs_dropping_to_one_take_the_d_pad_back_from_the_tab_row() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = hiddenTabsState(settings = settings)
        state.onButton(NxButton.UP)
        assertTrue(state.tabFocus.value)                                      // the tab row has the d-pad
        settings.showConsolesTab = false; settings.showToolsTab = false       // one tab left: the title, no row to light
        state.onForeground(); advanceUntilIdle()
        assertEquals(1, assertIs<Screen.Systems>(state.screen.value).tabs.size)
        assertFalse(state.tabFocus.value)
    }

    @Test fun a_screen_lists_the_hidden_tabs_with_content_and_follows_a_rescan() = runTest {
        val src = sourceWithCollections()
        val state = hiddenTabsState(MenuCategory.COLLECTIONS, src = src)
        assertEquals(listOf(MenuCategory.COLLECTIONS), assertIs<Screen.Systems>(state.screen.value).hidden)
        state.openHiddenTab(MenuCategory.COLLECTIONS)
        assertEquals(emptyList(), state.pushedMenu().hidden)                  // none over a pushed tab
        state.onButton(NxButton.B)
        src.delete("Home/Collections/RPGs.txt"); src.delete("Home/Collections/Racing.txt")
        state.rescan(); advanceUntilIdle()
        assertEquals(emptyList(), assertIs<Screen.Systems>(state.screen.value).hidden)   // emptied: nothing left to open
    }

    @Test fun every_tab_hidden_leaves_home_and_offers_the_rest() = runTest {
        val state = hiddenTabsState(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.TOOLS)
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(listOf(MenuCategory.HOME), s.tabs.map { it.category })
        assertEquals("NX Redux", mainMenuTitle(s))
        state.openTabMenu()
        assertEquals(listOf(ContextKind.OPEN_CONSOLES, ContextKind.OPEN_TOOLS), menuKinds(state))
    }

    @Test fun home_pressed_on_a_pushed_tab_lands_on_the_first_tab() = runTest {
        val state = hiddenTabsState(MenuCategory.CONSOLES)
        state.openHiddenTab(MenuCategory.CONSOLES)
        state.goHome()
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertFalse(s.pushed)
        assertEquals(MenuCategory.HOME, s.category)
    }

    // --- Home ---

    /** Game time answering the stats reads from [window] and [paths]; [gate] holds each window read until completed. */
    private class HomeGameTime(var window: WindowTotals = WindowTotals(0L, null), var paths: Set<String> = emptySet()) : GameTimeData by NoGameTime {
        var windowCalls = 0
        val windows = ArrayList<Pair<Long, Long>>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun window(sinceMs: Long, nowMs: Long): WindowTotals { windowCalls++; windows += sinceMs to nowMs; gate?.await(); return window }
        /** The Game Tracker page opened from a Home pin reads its list: empty, so the page loads without an error. */
        override suspend fun totals(): List<GameTotal> = emptyList()
        override suspend fun playedPaths(sinceMs: Long, nowMs: Long): Set<String> = paths
    }

    /** Home's body frames: a 960 × 344 landscape phone (the wide face: 15 slots, 3 games a row, 1–2 inline) and a 475 × 291 portrait one (the portrait type: columns of 5 squares, 2 games a row). */
    private val LAND = HomeFrame(960f, 344f, false, 960f / 432f)
    private val PORT = HomeFrame(475f, 291f, true)
    /** A tall window without the pad (UNDER, 5 slots); the portrait type shows 5 or 10. */
    private val TALL = HomeFrame(360f, 423f, false)

    private val Screen.homeRows: List<MainRow> get() = assertIs<Screen.Systems>(this).also { assertEquals(MenuCategory.HOME, it.category) }.rows
    /** Home's stats strip as the screen shows it. */
    private val AppState.strip: HomeStrip get() = assertNotNull(homeView.value.strip)

    /** On Home: selects the first row matching [p]. */
    private fun AppState.selectHome(p: (MainRow) -> Boolean) {
        val at = screen.value.homeRows.indexOfFirst(p)
        assertTrue(at >= 0, "no such row in ${screen.value.homeRows}")
        select(at)
    }

    private fun total(path: String, name: String, ms: Long) = GameTotal(1, name, "GB", path, totalMs = ms, plays = 1, lastPlayedAt = 1L)

    @Test fun the_menu_opens_on_home_and_the_tabs_are_home_consoles_collections_tools() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), src = sourceWithCollections(), data = FakeUserData(recents = listOf(Recent(tetris))))
        state.load(); advanceUntilIdle()
        val s = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS), s.tabs.map { it.category })
        assertEquals(MenuCategory.HOME, s.category)
        assertEquals("Tetris", assertIs<MainRow.Continue>(s.rows[0]).entry.game.shownName)
        assertEquals(0, state.selection.value.index)
        assertEquals(MenuStyle.GRID, state.currentStyle())   // Home's default
    }

    @Test fun a_on_continue_resumes_from_the_save_read_now_and_plays_without_one() = runTest {
        val runner = RecordingRunner()
        val resumable = HashMap(mapOf(("GB" to "Tetris.gb") to ResumeInfo(9)))
        val data = FakeUserData(recents = listOf(Recent(tetris)), resumable = resumable)
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load(); advanceUntilIdle()
        assertIs<MainRow.Continue>(state.screen.value.homeRows[0])
        assertTrue(state.resumeInfo.value.canResume)                     // A Resume shows
        state.onButton(NxButton.A)
        assertEquals(9, runner.launched.single().resumeSlot)
        assertEquals("Tetris.gb", runner.launched.single().fileName)

        resumable.clear()                                                // the save is gone since the row was built
        state.select(0); state.onButton(NxButton.A)
        assertNull(runner.launched[1].resumeSlot)                        // plays: the row's slot is not used
        resumable[("GB" to "Tetris.gb")] = ResumeInfo(4)                 // a save made since
        state.select(0); state.onButton(NxButton.A)
        assertEquals(4, runner.launched[2].resumeSlot)
    }

    @Test fun a_pinned_game_on_home_resumes_its_save_or_plays_and_the_hint_says_which() = runTest {
        val runner = RecordingRunner()
        val resumable = HashMap(mapOf(("GB" to "Tetris.gb") to ResumeInfo(9)))   // Tetris has a save, Mario none
        val data = FakeUserData(pins = listOf(Shortcut(tetris, "Tetris"), Shortcut(mario, "Mario")), resumable = resumable)
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load(); advanceUntilIdle()
        state.onHomeLayout(LAND); advanceUntilIdle()                     // landscape
        fun hint() = mainMenuHints(state.screen.value.homeRows[state.selection.value.index], state.resumeInfo.value.canResume)
        state.selectHome { it is MainRow.Pin && it.entry.game.path == tetris }; advanceUntilIdle()
        assertTrue(Hint(NxButton.A, "Resume") in hint())
        state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals(9, runner.launched.single().resumeSlot)             // resumed from its save
        state.onForeground(); advanceUntilIdle()                         // back from the game
        state.selectHome { it is MainRow.Pin && it.entry.game.path == mario }; advanceUntilIdle()
        assertTrue(Hint(NxButton.A, "Play") in hint())                   // no save: A Play
        state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals("Mario.gba", runner.launched[1].fileName)
        assertNull(runner.launched[1].resumeSlot)                        // played
        resumable[("GBA" to "Mario.gba")] = ResumeInfo(2)                // it saved while playing
        state.onForeground(); advanceUntilIdle()                         // back from the game: Mario is Continue, out of the row
        assertEquals(mario, assertIs<MainRow.Continue>(state.screen.value.homeRows[0]).entry.game.path)
        assertEquals(listOf(tetris), state.screen.value.homeRows.filterIsInstance<MainRow.Pin>().map { it.entry.game.path })   // Tetris back
        state.select(0); advanceUntilIdle()
        assertTrue(Hint(NxButton.A, "Resume") in hint())                 // its new save: A Resume
        state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals(2, runner.launched[2].resumeSlot)
    }

    @Test fun menu_on_continue_offers_pin_and_pins_its_game() = runTest {
        val data = FakeUserData(recents = listOf(Recent(tetris)))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load(); advanceUntilIdle()
        state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.PIN), menuKinds(state))
        state.onButton(NxButton.A)
        assertEquals(listOf(tetris), data.pins.map { it.path })
        assertEquals(1, state.screen.value.homeRows.count { it !is MainRow.Shelf })   // Continue's game is the only pin: no other row (bar the shelves)
        state.select(0); state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.UNPIN), menuKinds(state))       // a pinned game row's own menu
    }

    // No pins: Continue alone above the shelves, DOWN from it goes to the first shelf tile.
    @Test fun without_pins_down_from_the_top_goes_to_the_first_shelf() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(recents = listOf(Recent(tetris))), gameTime = HomeGameTime())
        state.load(); advanceUntilIdle()
        state.onHomeLayout(LAND); advanceUntilIdle()
        assertEquals(1, state.screen.value.homeRows.count { it !is MainRow.Shelf })   // Continue only
        state.select(0); state.onButton(NxButton.DOWN)
        assertIs<MainRow.Shelf>(state.screen.value.homeRows[state.selection.value.index])   // the shelves are the next band
    }

    // The rows skip the Continue game; when Continue moves to another game, that one leaves the rows and the previous
    // one comes back on the rebuild. A focused pin still shown stays focused (the identity remap), and so does the
    // remembered one.
    @Test fun the_pinned_rows_skip_the_continue_game_and_follow_it() = runTest {
        val data = FakeUserData(recents = listOf(Recent(tetris)), pins = listOf(Shortcut(tetris, "Tetris"), Shortcut(mario, "Mario"), Shortcut(mk, "Mario Kart DS")))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load(); advanceUntilIdle()
        state.onHomeLayout(LAND); advanceUntilIdle()
        fun pinPaths() = state.screen.value.homeRows.filterIsInstance<MainRow.Pin>().map { it.entry.game.path }
        assertEquals(listOf(mario, mk), pinPaths())                                // Tetris is Continue
        state.selectHome { it is MainRow.Pin && it.entry.game.path == mk }
        data.recents = listOf(Recent(mario), Recent(tetris))                      // Mario played since
        state.onForeground(); advanceUntilIdle()
        assertEquals(mario, assertIs<MainRow.Continue>(state.screen.value.homeRows[0]).entry.game.path)
        assertEquals(listOf(tetris, mk), pinPaths())                               // Tetris is back, Mario skipped
        fun focused() = state.screen.value.homeRows[state.selection.value.index]
        assertEquals(mk, assertIs<MainRow.Pin>(focused()).entry.game.path)        // focus kept on Mario Kart
        // The two games are stacked inline in one column, both right of Continue; a tie
        // goes to the top tile last left (Mario Kart, focused before select(0)), the top card only with no such memory.
        state.select(0); state.onButton(NxButton.RIGHT)
        assertEquals(mk, assertIs<MainRow.Pin>(focused()).entry.game.path)        // RIGHT from Continue: back to the card it left
        state.onButton(NxButton.UP)
        assertEquals(tetris, assertIs<MainRow.Pin>(focused()).entry.game.path)    // Tetris: the card above
    }

    // Continue's game the only pin: the row disappears as with no pins; DOWN goes to the first shelf tile.
    @Test fun the_continue_game_as_the_only_pin_leaves_no_pinned_row() = runTest {
        val data = FakeUserData(recents = listOf(Recent(tetris)), pins = listOf(Shortcut(tetris, "Tetris")))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load(); advanceUntilIdle()
        assertEquals(1, state.screen.value.homeRows.count { it !is MainRow.Shelf })   // Continue alone (bar the shelves)
        state.select(0); state.onButton(NxButton.DOWN)
        assertIs<MainRow.Shelf>(state.screen.value.homeRows[state.selection.value.index])
    }

    // Home left open past midnight: the next Home show or focus move on a new day recomputes the stats (a date compare);
    // moves on the same day do not. On the stand-in (wide) frame Tetris sits inline right of Continue, so the moves are
    // RIGHT / LEFT.
    @Test fun the_stats_recompute_when_home_is_used_on_a_new_day() = runTest {
        val day = 86_400_000L
        var clock = 100 * day + 15 * 3_600_000L                          // Saturday 15:00 (UTC)
        val gameTime = HomeGameTime()
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(recents = listOf(Recent(mk)), pins = listOf(Shortcut(tetris, "Tetris"))), now = { clock }, gameTime = gameTime)
        state.load(); advanceUntilIdle()
        assertEquals(1, gameTime.windowCalls)
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.LEFT); state.onButton(NxButton.RIGHT); advanceUntilIdle()   // Continue, Tetris
        assertEquals(1, gameTime.windowCalls)                            // the same day: nothing
        clock = 101 * day + 3_600_000L                                   // Sunday 01:00
        state.onButton(NxButton.LEFT); advanceUntilIdle()                // a focus move
        assertEquals(2, gameTime.windowCalls)
        assertEquals(90 * day, gameTime.windows.last().first)            // the same month (from 1 April 1970)
        state.onButton(NxButton.RIGHT); advanceUntilIdle()
        assertEquals(2, gameTime.windowCalls)
        clock = 120 * day + 3_600_000L                                   // 1 May 01:00: a new month
        state.onButton(NxButton.R1); state.onButton(NxButton.L1); advanceUntilIdle()   // Home shown again
        assertEquals(3, gameTime.windowCalls)
        assertEquals(120 * day, gameTime.windows.last().first)
    }

    @Test fun the_stats_card_follows_a_sign_in_made_in_a_tool_when_home_shows_again() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val settings = MemorySettings(ContentRef("Home"))
        val gameTime = HomeGameTime()
        val files = MemoryPrivateFiles().apply { this.files[ToolPins.FILE] = "GAME_TIME" }   // Game Tracker pinned on Home
        val state = AppState(AppDeps(settings, source, RecordingRunner(), NoopPicker(), FakeUserData(recents = listOf(Recent(tetris))),
            gameTime = gameTime, privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        state.load(); advanceUntilIdle()
        assertNull(state.strip.achievements)   // signed out: "Sign in"
        val calls = gameTime.windowCalls
        state.selectHome { it is MainRow.PinnedTool }
        state.onButton(NxButton.A); advanceUntilIdle()                   // a tool (Game Tracker)
        assertIs<Screen.GameTime>(state.screen.value)
        settings.raToken = "tok"; settings.raUsername = "user"           // signed in meanwhile
        state.onButton(NxButton.B); advanceUntilIdle()                   // back to Home: the card refreshes
        assertEquals(calls + 1, gameTime.windowCalls)
        assertEquals("0", state.strip.achievements)
        state.onButton(NxButton.R1); state.onButton(NxButton.L1); advanceUntilIdle()   // no change: a tab switch does not recompute
        assertEquals(calls + 1, gameTime.windowCalls)
        state.selectHome { it is MainRow.PinnedTool }
        state.onButton(NxButton.A); advanceUntilIdle()
        settings.raToken = null                                          // signed out
        state.onButton(NxButton.B); advanceUntilIdle()
        assertNull(state.strip.achievements)   // signed out: "Sign in"
    }
    @Test fun a_on_pick_a_game_lands_on_consoles() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load(); advanceUntilIdle()
        assertEquals(listOf(MainRow.PickAGame), state.screen.value.homeRows.filterNot { it is MainRow.Shelf })   // nothing played, no pins
        state.onButton(NxButton.A)
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
    }

    @Test fun unpinning_the_focused_pin_moves_the_focus_to_the_next_item() = runTest {
        val data = FakeUserData(recents = listOf(Recent(mk)), pins = listOf(Shortcut(tetris, "Tetris"), Shortcut(mario, "Mario")))   // Continue: a game not pinned
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load(); advanceUntilIdle()
        state.selectHome { it is MainRow.Pin && it.entry.game.path == tetris }
        val at = state.selection.value.index
        choose(state, ContextKind.UNPIN)
        val rows = state.screen.value.homeRows
        assertEquals(at, state.selection.value.index)
        assertEquals(mario, assertIs<MainRow.Pin>(rows[at]).entry.game.path)   // the next pin, where the gone one was
        choose(state, ContextKind.UNPIN)                                 // the last pin: the rows go
        val after = state.screen.value.homeRows
        assertTrue(after.none { it is MainRow.Pin })
        assertEquals(0, state.selection.value.index)                     // no Pinned row left: the top item last on (Continue)
    }

    @Test fun the_main_menu_settings_page_has_style_rows_for_home_consoles_collections_and_tools() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = MenuStyles.decode("RECENT=CAROUSEL\nPINNED=GRID\nCONSOLES=GRID") }   // stored before Home
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        // No Pinned row; Home first. Home, Collections and Tools are unset (their defaults: Grid, Minimal, and Grid).
        assertEquals(
            listOf("Home" to "Grid", "Consoles" to "Grid", "Collections" to "Minimal", "Tools" to "Grid", "Game lists" to "List", "Title and hints" to "Show",
                "Game Switcher" to "Full screen", "Extra info" to "Show", "Reset to defaults" to null),
            state.layoutsRows().map { it.label to it.secondary },
        )
        // Every group's rows, in Layouts' order, each page ending with Done.
        val groups = listOf(SettingsSection.LAYOUT_HOME, SettingsSection.LAYOUT_CONSOLES, SettingsSection.LAYOUT_COLLECTIONS, SettingsSection.LAYOUT_TOOLS,
            SettingsSection.LAYOUT_GAME_LISTS, SettingsSection.LAYOUT_TITLE_HINTS, SettingsSection.LAYOUT_SWITCHER)
        val rows = groups.flatMap { g -> state.openLayoutsPage(g) }
        assertEquals(listOf("Home tab", "Home layout", "New shelf", "Not started shelf", "Home stats", "Consoles tab", "Consoles layout", "Collections tab", "Collections layout", "Tools tab", "Tools layout", "Game lists layout", "List art", "Layout options", "Page title", "Button hints", "Recent hint", "Search hint", "Game Switcher layout"), rows.map { it.label })
        val styles = rows.filter { it.kind == SettingsKind.MENU_STYLE }
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS), styles.map { it.category })
        assertTrue(styles.all { it.enabled })
        assertEquals(1, rows.count { it.kind == SettingsKind.GAME_LIST_STYLE })
        assertEquals(listOf("Show", "Grid", "Show", "Show", "Show", "Show", "Grid", "Show", "Minimal", "Show", "Grid", "List", "Screenshot", "Show", "Show", "Show", "Show", "Show", "Full screen"), rows.map { it.secondary })
    }

    @Test fun layouts_page_defaults_to_carousel_tools_grid_and_cycles_four_main_menu_styles() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = emptyMap() }
        val state = state(settings)
        state.load()
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals(listOf("Home tab", "Home layout", "New shelf", "Not started shelf", "Home stats"), state.openLayoutsPage(SettingsSection.LAYOUT_HOME).map { it.label })
        assertEquals(listOf("Consoles tab", "Consoles layout", "Consoles orientation", "Consoles background"), state.openLayoutsPage(SettingsSection.LAYOUT_CONSOLES).map { it.label })
        assertEquals(listOf("Collections tab", "Collections layout"), state.openLayoutsPage(SettingsSection.LAYOUT_COLLECTIONS).map { it.label })
        assertEquals(listOf("Tools tab", "Tools layout"), state.openLayoutsPage(SettingsSection.LAYOUT_TOOLS).map { it.label })
        assertEquals(listOf("Game lists layout", "List art", "Layout options"), state.openLayoutsPage(SettingsSection.LAYOUT_GAME_LISTS).map { it.label })
        // Layouts' group rows carry each layout: Tools' own default is Grid, Collections' Minimal; the fixture's game list is List.
        assertEquals(listOf("Grid", "Carousel", "Minimal", "Grid", "List"), state.openLayoutsPage(SettingsSection.MAIN_MENU).take(5).map { it.secondary })
        state.selectLayoutsRow(SettingsKind.MENU_STYLE, MenuCategory.CONSOLES); state.onButton(NxButton.RIGHT)   // Consoles: Carousel to Minimal, no Backdrop
        assertEquals(MenuStyle.MINIMAL, settings.menuStyles[MenuCategory.CONSOLES])
        state.onButton(NxButton.RIGHT)                                    // and Minimal wraps to List
        assertEquals(MenuStyle.LIST, settings.menuStyles[MenuCategory.CONSOLES])
        state.onButton(NxButton.LEFT)
        state.onButton(NxButton.LEFT)
        assertEquals(MenuStyle.CAROUSEL, settings.menuStyles[MenuCategory.CONSOLES])
        state.selectLayoutsRow(SettingsKind.GAME_LIST_STYLE)             // Game lists: all five
        val seen = List(5) { state.onButton(NxButton.RIGHT); rows().first { it.kind == SettingsKind.GAME_LIST_STYLE }.secondary }
        assertEquals(listOf("Grid", "Carousel", "Backdrop", "Minimal", "List"), seen)
    }

    @Test fun layouts_page_shows_an_orientation_row_under_carousel_and_backdrop_only() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL)
            gameListStyle = MenuStyle.BACKDROP
        }
        val state = state(settings)
        state.load()
        val home = state.openLayoutsPage(SettingsSection.LAYOUT_HOME)
        assertEquals(
            listOf(SettingsKind.TAB_SHOWN, SettingsKind.MENU_STYLE, SettingsKind.SHELF_SHOWN, SettingsKind.SHELF_SHOWN, SettingsKind.HOME_STATS),
            home.map { it.kind },
        )
        val consoles = state.openLayoutsPage(SettingsSection.LAYOUT_CONSOLES)
        assertEquals(listOf(SettingsKind.TAB_SHOWN, SettingsKind.MENU_STYLE, SettingsKind.MENU_ORIENTATION, SettingsKind.CONTROLLER_ART), consoles.map { it.kind })
        assertEquals(listOf("Consoles tab", "Consoles layout", "Consoles orientation", "Consoles background"), consoles.map { it.label })
        assertEquals(MenuCategory.CONSOLES, consoles[0].category)
        for (g in listOf(SettingsSection.LAYOUT_COLLECTIONS, SettingsSection.LAYOUT_TOOLS)) {
            val rows = state.openLayoutsPage(g)
            assertEquals(listOf(SettingsKind.TAB_SHOWN, SettingsKind.MENU_STYLE), rows.map { it.kind }, "$g")
            assertEquals(listOf("${g.tab!!.label} tab", "${g.tab!!.label} layout"), rows.map { it.label })
        }
        val games = state.openLayoutsPage(SettingsSection.LAYOUT_GAME_LISTS)
        assertEquals(
            listOf(SettingsKind.GAME_LIST_STYLE, SettingsKind.GAME_LIST_ORIENTATION, SettingsKind.BACKDROP_ART, SettingsKind.LAYOUT_OPTIONS),
            games.map { it.kind },
        )
        assertEquals(listOf("Game lists layout", "Game lists orientation", "Backdrop art", "Layout options"), games.map { it.label })
        assertEquals(listOf("Horizontal", "Horizontal"), (consoles + games).filter { it.label.endsWith(" orientation") }.map { it.secondary })   // the default
    }

    @Test fun layouts_page_ends_with_extra_info_page_title_button_hints_then_reset() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        val rows = state.openLayoutsPage(SettingsSection.MAIN_MENU)
        assertEquals(SettingsKind.RESET_DEFAULTS, rows.last().kind)
        assertEquals(listOf(SettingsKind.LAYOUT_TITLE_HINTS, SettingsKind.LAYOUT_SWITCHER, SettingsKind.EXTRA_INFO), rows.dropLast(1).takeLast(3).map { it.kind })
        assertTrue(settingsNxRow(rows.single { it.kind == SettingsKind.EXTRA_INFO }).cycleable)
        // The title and hints' page and the Game Switcher's: their value rows all cycle, then Done.
        val chrome = state.openLayoutsPage(SettingsSection.LAYOUT_TITLE_HINTS)
        assertEquals(listOf(SettingsKind.PAGE_TITLE, SettingsKind.BUTTON_HINTS, SettingsKind.HINT_SHOWN, SettingsKind.HINT_SHOWN), chrome.map { it.kind })
        val switcher = state.openLayoutsPage(SettingsSection.LAYOUT_SWITCHER)
        assertEquals(listOf(SettingsKind.SWITCHER_LAYOUT), switcher.map { it.kind })
        assertTrue((chrome.dropLast(1) + switcher.dropLast(1)).all { settingsNxRow(it).cycleable })
        val consoles = state.openLayoutsPage(SettingsSection.LAYOUT_CONSOLES)
        val controller = consoles.single { it.kind == SettingsKind.CONTROLLER_ART }
        assertEquals(SettingsRow(SettingsKind.CONTROLLER_ART, "Consoles background", "Controller", category = MenuCategory.CONSOLES), controller)
        assertEquals(consoles.indexOfFirst { it.kind == SettingsKind.MENU_STYLE && it.category == MenuCategory.CONSOLES } + 1, consoles.indexOf(controller))   // List: no orientation row between
    }

    // The Consoles background row shows while the Consoles tab is shown and its layout is List or Carousel (not Grid).
    @Test fun the_consoles_background_row_shows_under_a_list_or_carousel_consoles_layout() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        for (style in MenuStyles.MAIN_MENU_STYLES) for (shown in listOf(true, false)) {
            settings.menuStyles = LIST_TABS + (MenuCategory.CONSOLES to style)
            settings.setTabShown(MenuCategory.CONSOLES, shown)
            state.openSettings(SettingsSection.LAYOUT_CONSOLES)
            assertEquals(shown && style != MenuStyle.GRID && style != MenuStyle.MINIMAL, state.layoutsRows().any { it.kind == SettingsKind.CONTROLLER_ART }, "$style shown=$shown")
        }
    }

    @Test fun appearance_has_the_accent_row_reading_white_above_reset() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        val accent = rows[1]   // under the other value rows
        assertEquals(SettingsKind.ACCENT, accent.kind)
        assertEquals("Accent", accent.label)
        assertEquals("White", accent.secondary)   // the default
        assertEquals(true, settingsNxRow(accent).cycleable)
        assertEquals(SettingsKind.SECONDARY_ACCENT, rows[2].kind)
        assertEquals(SettingsKind.TEXT_SIZE, rows[3].kind)
        assertEquals(SettingsKind.RESET_DEFAULTS, rows[4].kind)
        state.openSettings(SettingsSection.MAIN_MENU)
        assertTrue(assertIs<Screen.Settings>(state.screen.value).rows.none { it.kind == SettingsKind.ACCENT })   // not on Layouts
    }

    @Test fun accent_row_cycles_through_the_presets_and_keeps_the_selection() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        val i = rows().indexOfFirst { it.kind == SettingsKind.ACCENT }
        state.select(i)
        val seen = mutableListOf<String?>()
        repeat(AccentPreset.entries.size) {
            seen += rows()[i].secondary
            state.onButton(NxButton.RIGHT)
        }
        assertEquals(listOf<String?>("White", "Red", "Orange", "Amber", "Lime", "Teal", "Sky", "Violet", "Pink"), seen)
        assertEquals(AccentPreset.WHITE, settings.accent)   // wrapped round
        assertEquals(i, state.selection.value.index)
        state.onButton(NxButton.LEFT)
        assertEquals(AccentPreset.PINK, settings.accent)
        assertEquals("Pink", rows()[i].secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(AccentPreset.WHITE, settings.accent)
        state.onButton(NxButton.A)                                         // many values: A opens the picker
        assertEquals(AccentPreset.entries.indexOf(AccentPreset.WHITE), state.chooser.value?.current)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)
        assertEquals(AccentPreset.RED, settings.accent)
    }

    @Test fun secondary_accent_row_cycles_auto_then_the_presets_and_keeps_the_selection() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        assertEquals(SecondaryAccent.Preset(AccentPreset.TEAL), settings.secondaryAccent)   // a fresh store
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        val i = rows().indexOfFirst { it.kind == SettingsKind.SECONDARY_ACCENT }
        assertEquals(rows().indexOfFirst { it.kind == SettingsKind.ACCENT } + 1, i)   // directly under Accent
        assertEquals(true, settingsNxRow(rows()[i]).cycleable)
        state.select(i)
        val seen = mutableListOf<String?>()
        repeat(SecondaryAccent.entries.size) {
            seen += rows()[i].secondary
            state.onButton(NxButton.RIGHT)
        }
        assertEquals(listOf<String?>("Teal", "Sky", "Violet", "Pink", "Auto", "White", "Red", "Orange", "Amber", "Lime"), seen)
        assertEquals(SecondaryAccent.Preset(AccentPreset.TEAL), settings.secondaryAccent)   // wrapped round
        assertEquals(i, state.selection.value.index)
        settings.secondaryAccent = SecondaryAccent.Auto
        state.onButton(NxButton.LEFT)
        assertEquals(SecondaryAccent.Preset(AccentPreset.PINK), settings.secondaryAccent)   // Auto <- Pink
        assertEquals("Pink", rows()[i].secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(SecondaryAccent.Auto, settings.secondaryAccent)                        // Pink -> Auto
        state.onButton(NxButton.RIGHT)
        assertEquals(SecondaryAccent.Preset(AccentPreset.WHITE), settings.secondaryAccent)
    }

    /** The rows of the Layouts page shown now: Layouts itself or one of its groups (the page must be open). */
    private fun AppState.layoutsRows(): List<SettingsRow> =
        assertIs<Screen.Settings>(screen.value).also {
            assertTrue(it.section == SettingsSection.MAIN_MENU || it.section.layoutGroup, "not a Layouts page: ${it.section}")
        }.rows

    /**
     * The Layouts page holding [kind]'s row: a tab's group for a tab's rows (of [category]; Consoles background:
     * Consoles), Home's for the shelves and Home stats, the game lists', the title and hints', the Game Switcher's;
     * else Layouts itself (its group rows, Extra info, Reset to defaults).
     */
    private fun layoutsPageOf(kind: SettingsKind, category: MenuCategory? = null): SettingsSection = when (kind) {
        SettingsKind.TAB_SHOWN, SettingsKind.MENU_STYLE, SettingsKind.MENU_ORIENTATION, SettingsKind.CONTROLLER_ART -> {
            val c = category ?: if (kind == SettingsKind.CONTROLLER_ART) MenuCategory.CONSOLES else error("$kind needs its tab")
            SettingsSection.entries.first { it.tab == c }
        }
        SettingsKind.SHELF_SHOWN, SettingsKind.HOME_STATS -> SettingsSection.LAYOUT_HOME
        SettingsKind.GAME_LIST_STYLE, SettingsKind.GAME_LIST_ORIENTATION, SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT, SettingsKind.LIST_ART,
        SettingsKind.BACKDROP_ART, SettingsKind.LAYOUT_OPTIONS, SettingsKind.ACHIEVEMENTS_PANEL -> SettingsSection.LAYOUT_GAME_LISTS
        SettingsKind.PAGE_TITLE, SettingsKind.BUTTON_HINTS, SettingsKind.HINT_SHOWN -> SettingsSection.LAYOUT_TITLE_HINTS
        SettingsKind.SWITCHER_LAYOUT -> SettingsSection.LAYOUT_SWITCHER
        else -> SettingsSection.MAIN_MENU
    }

    /** Opens Layouts' [section] page (Layouts or one of its groups) afresh and returns its rows. */
    private fun AppState.openLayoutsPage(section: SettingsSection): List<SettingsRow> {
        openSettings(section)
        return layoutsRows()
    }

    /**
     * Goes to Layouts' [section] page (Layouts or one of its groups) as a user does: from another group B back to
     * Layouts, then A on the group's row. A Layouts page must be open. Returns the page's rows.
     */
    private fun AppState.goToLayoutsPage(section: SettingsSection): List<SettingsRow> {
        fun here() = assertIs<Screen.Settings>(screen.value).section
        if (here() == section) return layoutsRows()
        if (here().layoutGroup) onButton(NxButton.B)
        assertEquals(SettingsSection.MAIN_MENU, here())
        if (section != SettingsSection.MAIN_MENU) {
            val i = layoutsRows().indexOfFirst { it.kind == section.opener }
            assertTrue(i >= 0, "no ${section.opener} row")
            select(i); onButton(NxButton.A)
            assertEquals(section, here())
        }
        return layoutsRows()
    }

    /** Highlights Layouts' [kind] row (of [category], for a tab's rows), going to the page holding it, and returns its index there. */
    private fun AppState.selectLayoutsRow(kind: SettingsKind, category: MenuCategory? = null): Int {
        goToLayoutsPage(layoutsPageOf(kind, category))
        val i = layoutsRows().indexOfFirst { it.kind == kind && (category == null || it.category == category) }
        assertTrue(i >= 0, "no $kind $category row")
        select(i)
        return i
    }

    /** A loaded state over [src] (the default library: Home, Consoles, Tools) with Layouts hiding [hidden]. */
    private fun TestScope.hiddenTabsState(
        vararg hidden: MenuCategory, settings: MemorySettings = MemorySettings(ContentRef("Home")),
        src: RomsSource = source, data: UserDataStore = NoUserData, runner: RecordingRunner = RecordingRunner(),
    ): AppState {
        for (c in hidden) settings.setTabShown(c, false)
        return state(settings, src = src, data = data, runner = runner).also { it.load(); advanceUntilIdle() }
    }

    /** Sets Layouts → [c] tab to Show ([shown]) or Hide. */
    private fun MemorySettings.setTabShown(c: MenuCategory, shown: Boolean) {
        when (c) {
            MenuCategory.HOME -> showHomeTab = shown
            MenuCategory.CONSOLES -> showConsolesTab = shown
            MenuCategory.COLLECTIONS -> showCollectionsTab = shown
            MenuCategory.TOOLS -> showToolsTab = shown
        }
    }

    /** A settings store as a fresh install has it: nothing stored, so every getter gives its named default. */
    private fun freshSettings() = MemorySettings(ContentRef("Home")).apply {
        menuStyles = emptyMap(); gameListStyle = MenuStyles.DEFAULT_GAME_LIST_STYLE
    }

    /** Every Appearance and Layouts option moved off its default. */
    private fun MemorySettings.changeEveryAppearanceOption() {
        padTheme = PadTheme.RETRO; accent = AccentPreset.RED; secondaryAccent = SecondaryAccent.Auto
        menuStyles = mapOf(MenuCategory.CONSOLES to MenuStyle.LIST, MenuCategory.COLLECTIONS to MenuStyle.GRID, MenuCategory.TOOLS to MenuStyle.CAROUSEL)
        menuOrientations = mapOf(MenuCategory.CONSOLES to MenuOrientation.VERTICAL, MenuCategory.COLLECTIONS to MenuOrientation.VERTICAL, MenuCategory.TOOLS to MenuOrientation.VERTICAL)
        gameListStyle = MenuStyle.BACKDROP; gameListOrientation = MenuOrientation.VERTICAL
        gameListVerticalAlignment = VerticalListAlignment.RIGHT
        consoleArt = ConsoleArt.NONE; gameListArt = GameListArt.WHEEL; backdropArt = BackdropArt.BOX2D
        showHomeTab = false; showConsolesTab = false; showCollectionsTab = false; showToolsTab = false
        menuPageTitle = false; menuButtonHints = false; menuExtraInfo = false; menuHomeStats = false
    }

    private fun assertAppearanceAtDefaults(settings: AppSettings) {
        assertEquals(PadTheme.CHARCOAL, settings.padTheme)
        assertEquals(AccentPreset.WHITE, settings.accent)
        assertEquals(SecondaryAccent.Preset(AccentPreset.TEAL), settings.secondaryAccent)
    }

    private fun assertLayoutsAtDefaults(settings: AppSettings) {
        assertEquals(MenuStyle.GRID, MenuStyles.styleOf(settings.menuStyles, MenuCategory.HOME))
        assertEquals(MenuStyle.CAROUSEL, MenuStyles.styleOf(settings.menuStyles, MenuCategory.CONSOLES))
        assertEquals(MenuStyle.MINIMAL, MenuStyles.styleOf(settings.menuStyles, MenuCategory.COLLECTIONS))
        assertEquals(MenuStyle.GRID, MenuStyles.styleOf(settings.menuStyles, MenuCategory.TOOLS))
        for (c in listOf(MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS))
            assertEquals(MenuOrientation.HORIZONTAL, MenuStyles.orientationOf(settings.menuOrientations, c), c.name)
        assertEquals(MenuStyle.CAROUSEL, settings.gameListStyle)
        assertEquals(MenuOrientation.HORIZONTAL, settings.gameListOrientation)
        assertEquals(VerticalListAlignment.LEFT, settings.gameListVerticalAlignment)
        assertEquals(ConsoleArt.CONTROLLER, settings.consoleArt)
        assertEquals(GameListArt.SCREENSHOT, settings.gameListArt)
        assertEquals(BackdropArt.BOX3D, settings.backdropArt)
        assertEquals(listOf(true, true, true, true), listOf(settings.showHomeTab, settings.showConsolesTab, settings.showCollectionsTab, settings.showToolsTab))
        assertEquals(true, settings.menuPageTitle)
        assertEquals(true, settings.menuButtonHints)
        assertEquals(true, settings.menuExtraInfo)
        assertEquals(true, settings.menuHomeStats)
    }

    @Test fun a_fresh_install_shows_every_appearance_and_layouts_default() = runTest {
        val settings = freshSettings()
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        assertEquals(listOf("Charcoal", "White"), assertIs<Screen.Settings>(state.screen.value).rows.take(2).map { it.secondary })
        fun page(section: SettingsSection) = state.openLayoutsPage(section).map { it.label to it.secondary }
        assertEquals(
            listOf(
                "Home" to "Grid", "Consoles" to "Carousel", "Collections" to "Minimal", "Tools" to "Grid", "Game lists" to "Carousel", "Title and hints" to "Show",
                "Game Switcher" to "Full screen", "Extra info" to "Show", "Reset to defaults" to null,
            ),
            page(SettingsSection.MAIN_MENU),
        )
        assertEquals(
            listOf("Home tab" to "Show", "Home layout" to "Grid", "New shelf" to "Show", "Not started shelf" to "Show", "Home stats" to "Show"),
            page(SettingsSection.LAYOUT_HOME),
        )
        assertEquals(listOf("Consoles tab" to "Show", "Consoles layout" to "Carousel", "Consoles orientation" to "Horizontal", "Consoles background" to "Controller"), page(SettingsSection.LAYOUT_CONSOLES))
        assertEquals(
            listOf("Collections tab" to "Show", "Collections layout" to "Minimal"),
            page(SettingsSection.LAYOUT_COLLECTIONS),
        )
        assertEquals(listOf("Tools tab" to "Show", "Tools layout" to "Grid"), page(SettingsSection.LAYOUT_TOOLS))
        assertEquals(
            listOf("Game lists layout" to "Carousel", "Game lists orientation" to "Horizontal", "Layout options" to "Show"),
            page(SettingsSection.LAYOUT_GAME_LISTS),
        )
        assertEquals(
            listOf("Page title" to "Show", "Button hints" to "Show", "Recent hint" to "Show", "Search hint" to "Show"),
            page(SettingsSection.LAYOUT_TITLE_HINTS),
        )
        assertEquals(listOf("Game Switcher layout" to "Full screen"), page(SettingsSection.LAYOUT_SWITCHER))
        assertLayoutsAtDefaults(settings)
    }

    @Test fun appearance_and_layouts_end_with_reset_to_defaults() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        for (section in listOf(SettingsSection.APPEARANCE, SettingsSection.MAIN_MENU)) {
            state.openSettings(section)
            val rows = assertIs<Screen.Settings>(state.screen.value).rows
            assertEquals(SettingsRow(SettingsKind.RESET_DEFAULTS, "Reset to defaults"), rows.last(), section.name)
            assertEquals(1, rows.count { it.kind == SettingsKind.RESET_DEFAULTS }, section.name)
            assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Select")), settingsHints(rows, rows.lastIndex))
        }
    }

    // Layouts is its own category, so Appearance's reset leaves it (NX Redux's neither recurses).
    @Test fun appearance_reset_puts_only_the_appearance_options_back() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { changeEveryAppearanceOption() }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        val reset = assertIs<Screen.Settings>(state.screen.value).rows.lastIndex
        state.select(reset); state.onButton(NxButton.A)
        assertAppearanceAtDefaults(settings)
        assertEquals(MenuStyle.BACKDROP, settings.gameListStyle)            // Layouts' options stay
        assertEquals(ConsoleArt.NONE, settings.consoleArt)
        assertEquals(false, settings.menuPageTitle)
        assertEquals(false, settings.menuButtonHints)
        assertEquals(false, settings.menuExtraInfo)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.APPEARANCE, page.section)                        // no dialog, no page change
        assertEquals(listOf("Charcoal", "White"), page.rows.take(2).map { it.secondary })   // the rows re-read
        assertEquals(ListSelection(reset, page.rows.size), state.selection.value)       // still on the reset row
    }

    @Test fun layouts_reset_puts_only_the_layouts_options_back() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { changeEveryAppearanceOption() }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        state.select(assertIs<Screen.Settings>(state.screen.value).rows.lastIndex); state.onButton(NxButton.A)
        assertLayoutsAtDefaults(settings)                     // Page title and Button hints too
        assertEquals(PadTheme.RETRO, settings.padTheme)       // Appearance's own options stay
        assertEquals(AccentPreset.RED, settings.accent)
        assertEquals(SecondaryAccent.Auto, settings.secondaryAccent)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.MAIN_MENU, page.section)
        // The orientation rows the defaults bring push the reset row down: the highlight follows it.
        assertEquals(SettingsKind.RESET_DEFAULTS, page.rows[state.selection.value.index].kind)
        assertEquals(ListSelection(page.rows.lastIndex, page.rows.size), state.selection.value)
    }

    // Controls is a category of its own right after Layouts: Pad vibration (default On) and its Reset.
    @Test fun controls_is_a_category_after_layouts_with_pad_vibration_and_reset() = runTest {
        val settings = freshSettings()
        val state = state(settings)
        state.load()
        state.openSettingsMenu()
        val sections = assertIs<Screen.SettingsMenu>(state.screen.value).sections
        assertEquals(sections.indexOf(SettingsSection.MAIN_MENU) + 1, sections.indexOf(SettingsSection.CONTROLS))
        state.openSettings(SettingsSection.CONTROLS)
        assertEquals(
            listOf(
                SettingsRow(SettingsKind.PAD_VIBRATION, "Pad vibration", "Light"),
                SettingsRow(SettingsKind.GAME_RUMBLE, "Game rumble", "Medium"),
                SettingsRow(SettingsKind.MENU_PAD, "Menu pad", "On"),
                SettingsRow(SettingsKind.PAD_LAYOUT, "Pad layout", "Default"),
                SettingsRow(SettingsKind.RESET_DEFAULTS, "Reset to defaults"),
            ),
            assertIs<Screen.Settings>(state.screen.value).rows,
        )
        assertEquals(PadVibration.LIGHT, settings.padVibration)
        assertEquals(GameRumble.MEDIUM, settings.gameRumble)
        assertTrue(settingsNxRow(assertIs<Screen.Settings>(state.screen.value).rows[0]).cycleable)
        assertTrue(settingsNxRow(assertIs<Screen.Settings>(state.screen.value).rows[1]).cycleable)
    }

    // Controls → Pad layout opens its page: Console, the three shapes, Reset console.
    @Test fun controls_pad_layout_row_opens_its_page() = runTest {
        val settings = freshSettings()
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.CONTROLS)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals(SettingsRow(SettingsKind.PAD_LAYOUT, "Pad layout", "Default"), rows[3])
        assertEquals(SettingsKind.RESET_DEFAULTS, rows.last().kind)
        state.select(3)
        state.onButton(NxButton.A)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.PAD_LAYOUT, page.section)
        assertEquals(
            listOf(
                SettingsRow(SettingsKind.PAD_LAYOUT_CONSOLE, "Console", "Shared"),
                SettingsRow(SettingsKind.PAD_LAYOUT_SHAPE, "Portrait band", "Default", padShape = PadShape.PORTRAIT),
                SettingsRow(SettingsKind.PAD_LAYOUT_SHAPE, "Landscape overlay", "Default", padShape = PadShape.OVERLAY),
                SettingsRow(SettingsKind.PAD_LAYOUT_RESET, "Reset console"),
            ),
            page.rows,
        )
        state.onButton(NxButton.B)
        val back = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.CONTROLS, back.section)
        assertEquals(SettingsKind.PAD_LAYOUT, back.rows[state.selection.value.index].kind)
    }

    @Test fun pad_layout_console_cycles_shared_then_library_consoles_and_values_follow() = runTest {
        val gba = PadLayout(mapOf(PadGroup.FACE to GroupTweak(scale = 1.2f)))
        val shared = PadLayout(mapOf(PadGroup.DPAD to GroupTweak(dy = -1f)))
        val settings = freshSettings().apply {
            padLayouts = mapOf(PadLayouts.SHARED_KEY to mapOf(PadSlot(PadShape.OVERLAY, PadScreen.NARROW) to shared), "gba" to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to gba))
        }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.PAD_LAYOUT)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals(listOf("Shared", "Default", "Custom"), rows().take(3).map { it.secondary })
        val seen = mutableListOf<String>()
        state.select(0)
        repeat(5) { seen += rows()[0].secondary!!; state.onButton(NxButton.RIGHT) }
        // The library's consoles with games (the fixture: GB, GBA, NDS; C64 is no console), by name, after Shared; wraps.
        assertEquals(listOf("Shared", "Game Boy", "Game Boy Advance", "Nintendo DS", "Shared"), seen)
        while (rows()[0].secondary != "Game Boy Advance") state.onButton(NxButton.RIGHT)
        assertEquals(listOf("Custom", "Shared"), rows().subList(1, 3).map { it.secondary })
        state.onButton(NxButton.LEFT)
        assertEquals("Game Boy", rows()[0].secondary)
    }

    @Test fun the_pad_layout_page_follows_the_screen_its_on() = runTest {
        val l = PadLayout(mapOf(PadGroup.DPAD to GroupTweak(dx = 1f)))
        val narrow = PadSlot(PadShape.PORTRAIT, PadScreen.NARROW)
        val wide = PadSlot(PadShape.PORTRAIT, PadScreen.WIDE)
        val requests = mutableListOf<PadEditRequest>()
        val settings = freshSettings().apply { padLayouts = mapOf(PadLayouts.SHARED_KEY to mapOf(narrow to l)) }
        val state = state(settings, padEditor = { requests += it })
        state.load()
        state.openSettings(SettingsSection.PAD_LAYOUT)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals("Custom", rows()[1].secondary)
        state.onPadScreen(PadScreen.WIDE)                        // unfolded: the inner screen's layouts
        assertEquals("Default", rows()[1].secondary)
        state.select(1)
        state.onButton(NxButton.A)
        assertEquals(wide, requests.single().slot)
        settings.padLayouts = PadLayouts.withLayout(settings.padLayouts, PadLayouts.SHARED_KEY, wide, l)
        state.onPadLayoutEdited()
        state.select(3)
        state.onButton(NxButton.A)                               // Reset console: this screen's only
        assertEquals(mapOf(PadLayouts.SHARED_KEY to mapOf(narrow to l)), settings.padLayouts)
    }

    @Test fun a_shape_row_opens_the_editor_for_the_chosen_console() = runTest {
        val requests = mutableListOf<PadEditRequest>()
        val state = state(freshSettings(), padEditor = { requests += it })
        state.load()
        state.openSettings(SettingsSection.PAD_LAYOUT)
        state.select(2)
        state.onButton(NxButton.A)
        assertEquals(PadEditRequest(PadLayouts.SHARED_KEY, PadSlot(PadShape.OVERLAY, PadScreen.NARROW), "", "Shared"), requests.single())
        state.onButton(NxButton.A)                               // a second quick press while it opens: no second editor
        assertEquals(1, requests.size)
        state.onPadLayoutEdited()
        state.select(0)
        while (assertIs<Screen.Settings>(state.screen.value).rows[0].secondary != "Game Boy Advance") state.onButton(NxButton.RIGHT)
        state.select(1)
        state.onButton(NxButton.A)
        assertEquals(PadEditRequest("gba", PadSlot(PadShape.PORTRAIT, PadScreen.NARROW), "GBA", "Game Boy Advance"), requests.last())
    }

    @Test fun an_edit_landing_republishes_the_page_and_the_pad_layouts() = runTest {
        val settings = freshSettings()
        val state = state(settings, padEditor = {})
        state.load()
        state.openSettings(SettingsSection.PAD_LAYOUT)
        val edited = mapOf(PadLayouts.SHARED_KEY to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to PadLayout(mapOf(PadGroup.DPAD to GroupTweak(dx = 1f)))))
        settings.padLayouts = edited                    // the editor activity writes the settings itself
        state.onPadLayoutEdited()
        assertEquals("Custom", assertIs<Screen.Settings>(state.screen.value).rows[1].secondary)
        assertEquals(edited, state.padLayouts.value)
    }

    @Test fun an_edit_landing_off_settings_updates_the_pad_layouts_and_keeps_the_screen() = runTest {
        val settings = freshSettings()
        val state = state(settings, padEditor = {})
        state.load()
        val menu = assertIs<Screen.Systems>(state.screen.value)
        val edited = mapOf(PadLayouts.SHARED_KEY to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to PadLayout(mapOf(PadGroup.DPAD to GroupTweak(dx = 1f)))))
        settings.padLayouts = edited
        state.onPadLayoutEdited()
        assertEquals(menu, state.screen.value)
        assertEquals(edited, state.padLayouts.value)
    }

    @Test fun a_consoles_explicit_default_shape_reads_default_and_reset_console_drops_it() = runTest {
        val l = PadLayout(mapOf(PadGroup.DPAD to GroupTweak(dx = 1f)))
        val settings = freshSettings().apply {
            padLayouts = mapOf(PadLayouts.SHARED_KEY to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to l), "gba" to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to PadLayout.EMPTY, PadSlot(PadShape.OVERLAY, PadScreen.NARROW) to l))
        }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.PAD_LAYOUT)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        state.select(0)
        while (rows()[0].secondary != "Game Boy Advance") state.onButton(NxButton.RIGHT)
        assertEquals(listOf("Default", "Custom"), rows().subList(1, 3).map { it.secondary })
        state.select(3)
        state.onButton(NxButton.A)
        assertEquals(mapOf(PadLayouts.SHARED_KEY to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to l)), settings.padLayouts)
        assertEquals(listOf("Shared", "Shared"), rows().subList(1, 3).map { it.secondary })
    }

    @Test fun reset_console_clears_only_that_consoles_layouts() = runTest {
        val l = PadLayout(mapOf(PadGroup.DPAD to GroupTweak(dx = 1f)))
        val settings = freshSettings().apply {
            padLayouts = mapOf(PadLayouts.SHARED_KEY to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to l), "gba" to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to l, PadSlot(PadShape.OVERLAY, PadScreen.NARROW) to l))
        }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.PAD_LAYOUT)
        state.select(0)
        while (assertIs<Screen.Settings>(state.screen.value).rows[0].secondary != "Game Boy Advance") state.onButton(NxButton.RIGHT)
        state.select(3)
        state.onButton(NxButton.A)
        assertEquals(mapOf(PadLayouts.SHARED_KEY to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to l)), settings.padLayouts)
        assertEquals(settings.padLayouts, state.padLayouts.value)
        assertEquals(SettingsKind.PAD_LAYOUT_RESET, assertIs<Screen.Settings>(state.screen.value).rows[state.selection.value.index].kind)
    }

    @Test fun controls_reset_to_defaults_clears_every_pad_layout() = runTest {
        val l = PadLayout(mapOf(PadGroup.DPAD to GroupTweak(dx = 1f)))
        val settings = freshSettings().apply { padLayouts = mapOf("gba" to mapOf(PadSlot(PadShape.PORTRAIT, PadScreen.NARROW) to l)) }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.CONTROLS)
        assertEquals("Custom", assertIs<Screen.Settings>(state.screen.value).rows[3].secondary)
        state.select(4)
        state.onButton(NxButton.A)
        assertEquals(emptyMap(), settings.padLayouts)
        assertEquals(emptyMap(), state.padLayouts.value)
        assertEquals("Default", assertIs<Screen.Settings>(state.screen.value).rows[3].secondary)
    }

    @Test fun game_rumble_row_cycles_on_left_right_and_a_and_previews_each_level() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val previews = mutableListOf<GameRumble>()
        val state = state(settings, rumblePreview = { previews += it })
        state.load()
        state.openSettings(SettingsSection.CONTROLS)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        state.select(1)
        state.onButton(NxButton.RIGHT)
        assertEquals(GameRumble.STRONG, settings.gameRumble)
        assertEquals("Strong", rows()[1].secondary)
        assertEquals(ListSelection(1, 5), state.selection.value)
        state.onButton(NxButton.A)                                   // wraps
        assertEquals(GameRumble.OFF, settings.gameRumble)
        assertEquals("Off", rows()[1].secondary)
        state.onButton(NxButton.LEFT)
        assertEquals(GameRumble.STRONG, settings.gameRumble)
        state.onButton(NxButton.LEFT)
        assertEquals(GameRumble.MEDIUM, settings.gameRumble)
        assertEquals(listOf(GameRumble.STRONG, GameRumble.OFF, GameRumble.STRONG, GameRumble.MEDIUM), previews)
        assertEquals(PadVibration.LIGHT, settings.padVibration)      // the pad's row is its own
    }

    // Settings → Controls → Menu pad, On by default; two values, so A, left and right each flip it.
    @Test fun menu_pad_row_flips_on_a_left_and_right() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.CONTROLS)
        fun row() = assertIs<Screen.Settings>(state.screen.value).rows[2]
        assertEquals(SettingsRow(SettingsKind.MENU_PAD, "Menu pad", "On"), row())
        assertTrue(settingsNxRow(row()).cycleable)
        state.select(2)
        state.onButton(NxButton.A)
        assertEquals(false, settings.menuPad)
        assertEquals("Off", row().secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(true, settings.menuPad)
        state.onButton(NxButton.LEFT)
        assertEquals(false, settings.menuPad)
        assertEquals(ListSelection(2, 5), state.selection.value)
    }

    // Settings → System → Debug log (its Developer group), Off by default; two values, so A, left and right each flip it.
    @Test fun debug_log_row_flips_on_a_left_and_right() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.SYSTEM)
        fun row() = assertIs<Screen.Settings>(state.screen.value).rows.single { it.kind == SettingsKind.DEBUG_LOG }
        assertEquals("Debug log" to "Off", row().label to row().secondary)
        state.onButton(NxButton.DOWN)                 // from Pause when unfocused, past the Developer label
        assertEquals(SettingsKind.DEBUG_LOG, assertIs<Screen.Settings>(state.screen.value).rows[state.selection.value.index].kind)
        state.onButton(NxButton.A)
        assertEquals(true, settings.debugLog)
        assertEquals("On", row().secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(false, settings.debugLog)
        state.onButton(NxButton.LEFT)
        assertEquals(true, settings.debugLog)
        assertEquals("On", row().secondary)
    }

    // Settings → System → Pause when unfocused (its General group, the first row), On by default; A, left and right each flip it.
    @Test fun pause_when_unfocused_row_flips_on_a_left_and_right() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.SYSTEM)
        fun row() = assertIs<Screen.Settings>(state.screen.value).rows.single { it.kind == SettingsKind.PAUSE_UNFOCUSED }
        assertEquals("Pause when unfocused" to "On", row().label to row().secondary)
        assertEquals(1, state.selection.value.index)  // past the General label
        state.onButton(NxButton.A)
        assertEquals(false, settings.pauseWhenUnfocused)
        assertEquals("Off", row().secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(true, settings.pauseWhenUnfocused)
        state.onButton(NxButton.LEFT)
        assertEquals(false, settings.pauseWhenUnfocused)
    }

    // Settings → Second screen: a root section between Controls and System that exists only while a second screen is connected.
    @Test fun the_second_screen_section_is_on_the_root_only_while_a_second_screen_is_connected() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openSettingsMenu()
        fun sections() = assertIs<Screen.SettingsMenu>(state.screen.value).sections
        assertTrue(SettingsSection.SECOND_SCREEN !in sections())
        state.onSecondScreenChanged(true)                       // plugged in while the root is open
        val s = sections()
        assertEquals(SettingsSection.CONTROLS, s[s.indexOf(SettingsSection.SECOND_SCREEN) - 1])
        assertEquals(SettingsSection.SYSTEM, s[s.indexOf(SettingsSection.SECOND_SCREEN) + 1])
        state.onSecondScreenChanged(false)
        assertTrue(SettingsSection.SECOND_SCREEN !in sections())
        state.openSettings(SettingsSection.EMULATORS)
        state.onSecondScreenChanged(true)
        assertEquals(listOf("Default emulators", "Settings"), assertIs<Screen.Settings>(state.screen.value).rows.map { it.label })   // no row under Emulators any more
    }

    @Test fun the_second_screen_page_has_enable_then_bottom_screen_and_b_returns_to_the_root_on_it() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.onSecondScreenChanged(true)
        state.openSettingsMenu()
        val sections = assertIs<Screen.SettingsMenu>(state.screen.value).sections
        state.select(sections.indexOf(SettingsSection.SECOND_SCREEN))
        state.onButton(NxButton.A)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.SECOND_SCREEN, page.section)
        assertEquals(listOf(SettingsRow(SettingsKind.SECOND_SCREEN_ENABLE, "Enable", "On"), SettingsRow(SettingsKind.BOTTOM_SCREEN, "Bottom screen", "Auto")), page.rows)
        state.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertEquals(sections.indexOf(SettingsSection.SECOND_SCREEN), state.selection.value.index)
    }

    @Test fun the_second_screen_page_goes_back_to_the_root_when_the_screen_is_removed() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.onSecondScreenChanged(true)
        state.openSettings(SettingsSection.SECOND_SCREEN)
        state.onSecondScreenChanged(false)
        val root = assertIs<Screen.SettingsMenu>(state.screen.value)
        assertTrue(SettingsSection.SECOND_SCREEN !in root.sections)
    }

    // Enable is On by default; two values, so A, left and right each flip it.
    @Test fun second_screen_enable_flips_on_a_left_and_right() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.onSecondScreenChanged(true)
        state.openSettings(SettingsSection.SECOND_SCREEN)
        fun row() = assertIs<Screen.Settings>(state.screen.value).rows.single { it.kind == SettingsKind.SECOND_SCREEN_ENABLE }
        assertEquals("On", row().secondary)
        assertTrue(settingsNxRow(row()).cycleable)
        state.select(0)
        state.onButton(NxButton.A)
        assertEquals(false, settings.secondScreen)
        assertEquals("Off", row().secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(true, settings.secondScreen)
        state.onButton(NxButton.LEFT)
        assertEquals(false, settings.secondScreen)
        assertEquals("Off", row().secondary)
    }

    // Bottom screen, right after Enable: Auto by default; A and RIGHT step on, LEFT back.
    @Test fun bottom_screen_row_defaults_to_auto_and_cycles() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.onSecondScreenChanged(true)
        state.openSettings(SettingsSection.SECOND_SCREEN)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        fun row() = rows().single { it.kind == SettingsKind.BOTTOM_SCREEN }
        assertEquals(1, rows().indexOf(row()))
        assertEquals("Bottom screen", row().label)
        assertEquals("Auto", row().secondary)
        assertTrue(settingsNxRow(row()).cycleable)
        state.select(rows().indexOf(row()))
        state.onButton(NxButton.RIGHT)
        assertEquals("main", settings.bottomScreen.value)
        assertEquals("Main screen", row().secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals("second", settings.bottomScreen.value)
        assertEquals("Second screen", row().secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals("auto", settings.bottomScreen.value)
        state.onButton(NxButton.LEFT)
        assertEquals("second", settings.bottomScreen.value)
    }

    // Settings → Library → Saves, NX Redux's three save-naming settings with its labels and defaults.
    @Test fun saves_page_cycles_the_handheld_formats() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LIBRARY)
        val lib = assertIs<Screen.Settings>(state.screen.value)
        state.select(lib.rows.indexOfFirst { it.kind == SettingsKind.SAVES })
        state.onButton(NxButton.A)
        fun page() = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.SAVES, page().section)
        assertEquals(
            listOf(
                Triple(SettingsKind.SAVE_FORMAT, "Save format", "Retroarch (uncompressed)"),
                Triple(SettingsKind.STATE_FORMAT, "Save state format", "Retroarch (uncompressed)"),
                Triple(SettingsKind.EXTRACTED_NAME, "Use extracted file name", "Off"),
                Triple(SettingsKind.EXPORT_SAVES, "Export saves", null),
                Triple(SettingsKind.IMPORT_SAVES, "Import saves", null),
            ),
            page().rows.map { Triple(it.kind, it.label, it.secondary) },
        )
        state.onButton(NxButton.RIGHT)                                // Save format: RIGHT steps forward
        assertEquals(SaveFormat.GENERIC, settings.saveFormat)
        state.onButton(NxButton.RIGHT)                                // wraps to MinUI
        assertEquals(SaveFormat.MINUI, settings.saveFormat)
        assertEquals("MinUI", page().rows[0].secondary)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.LEFT)  // Save state format: back one
        assertEquals(StateFormat.RETROARCH_COMPRESSED, settings.stateFormat)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.A)     // Use extracted file name
        assertEquals(true, settings.useExtractedFileName)
        assertEquals("On", page().rows[2].secondary)
        state.onButton(NxButton.B)                                    // back on Library's Saves row
        assertEquals(SettingsSection.LIBRARY, page().section)
        assertEquals(SettingsKind.SAVES, page().rows[state.selection.value.index].kind)
    }

    // Library's Saves group: after ROM folders, before Games; Emulators no longer lists it; B from Saves lands on it.
    @Test fun saves_is_a_library_row_and_b_from_it_returns_to_its_row() = runTest {
        val st = state(MemorySettings(ContentRef("Home")), data = FakeUserData(hidden = listOf(mario)))   // a hidden game: its row follows
        st.load()
        st.openSettings(SettingsSection.LIBRARY)
        val rows = assertIs<Screen.Settings>(st.screen.value).rows
        val at = rowIndex(st, SettingsKind.SAVES)
        assertEquals(SettingsRow(SettingsKind.SAVES, "Saves"), rows[at])
        assertEquals(SettingsKind.ADD_EXTRA, rows[at - 1].kind)                      // after Add ROM folder (no All files access here)
        assertTrue(rows.subList(at + 1, rows.size).all { it.kind == SettingsKind.UNASSIGNED || it.kind == SettingsKind.HIDDEN })   // then the game lists
        assertEquals(SettingsKind.HIDDEN, rows.last().kind)
        val touch = settingsTouchRow(rows[at], null, SettingsSection.LIBRARY)
        assertTrue(touch.opens)
        assertEquals("", touch.info)                                                    // compact: its text behind the info icon
        assertEquals("Save file formats, and export or import your saves", touch.detail)
        st.openSettings(SettingsSection.EMULATORS)
        assertTrue(assertIs<Screen.Settings>(st.screen.value).rows.none { it.kind == SettingsKind.SAVES })
        st.openSettings(SettingsSection.LIBRARY)
        st.select(at); st.onButton(NxButton.A)
        assertEquals(SettingsSection.SAVES, assertIs<Screen.Settings>(st.screen.value).section)
        st.onButton(NxButton.B)
        assertEquals(SettingsSection.LIBRARY, assertIs<Screen.Settings>(st.screen.value).section)
        assertEquals(at, st.selection.value.index)
        st.onButton(NxButton.B)
        assertIs<Screen.SettingsMenu>(st.screen.value)
        assertEquals(0, st.selection.value.index)                    // the menu on Library
    }

    // System's Licenses group: paragraphs the selection skips, then a row that opens the website's full list.
    @Test fun system_ends_with_licence_paragraphs_and_a_link_row_that_opens_the_full_list() = runTest {
        val opened = mutableListOf<String>()
        val st = AppState(
            AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), openUrl = { opened += it }, compute = UnconfinedTestDispatcher(testScheduler)),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        st.load()
        st.openSettings(SettingsSection.SYSTEM)
        val rows = assertIs<Screen.Settings>(st.screen.value).rows
        val label = rows.indexOfFirst { it.kind == SettingsKind.SECTION && it.label == "Licenses" }
        assertEquals(ABOUT_LICENSES.map { SettingsRow(SettingsKind.TEXT, it) }, rows.subList(label + 1, label + 1 + ABOUT_LICENSES.size))
        assertTrue(ABOUT_LICENSES.any { "MiSans" in it })
        assertEquals(SettingsRow(SettingsKind.LICENSES_LINK, "Full list of licenses", "https://nxredux.com/reference/licenses/"), rows.last())
        assertTrue(rows.filter { it.kind == SettingsKind.TEXT }.none { it.selectable })
        val paragraph = settingsTouchRow(rows[label + 1], null, SettingsSection.SYSTEM)
        assertTrue(paragraph.paragraph)
        val link = settingsTouchRow(rows.last(), null, SettingsSection.SYSTEM)
        assertEquals("", link.info); assertEquals(null, link.value)                    // the URL behind the info icon
        assertEquals("https://nxredux.com/reference/licenses/", link.detail)
        assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Open")), settingsHints(rows, rows.lastIndex))
        st.select(rows.indexOfFirst { it.kind == SettingsKind.COPY_INFO })
        st.onButton(NxButton.DOWN)                                   // past the label and the paragraphs
        assertEquals(rows.lastIndex, st.selection.value.index)
        st.onButton(NxButton.A)
        assertEquals(listOf("https://nxredux.com/reference/licenses/"), opened)
        st.openSelected()                                            // a tap opens it too
        assertEquals(2, opened.size)
        st.onButton(NxButton.UP)                                     // back up past the paragraphs
        assertEquals(SettingsKind.COPY_INFO, rows[st.selection.value.index].kind)
    }

    private class RecordingTransfer(private val result: ImportResult = ImportResult.Done(ImportSummary(saves = 1))) : SaveTransfer {
        val calls = mutableListOf<String>()
        override var importing = false
        override fun exportSaves(report: TransferReport) { calls += "export"; report("Exported 1 save and 0 states (0.0 MB)", true) }
        override fun importSaves(source: ImportSource, progress: (String) -> Unit, done: (ImportResult) -> Unit) { calls += "import $source"; done(result) }
    }

    /** Reports progress at once and keeps the result for the test to deliver ([finish]). */
    private class SlowTransfer : SaveTransfer {
        override var importing = false
        private var pending: ((ImportResult) -> Unit)? = null
        override fun exportSaves(report: TransferReport) = Unit
        override fun importSaves(source: ImportSource, progress: (String) -> Unit, done: (ImportResult) -> Unit) { importing = true; pending = done; progress("Working…") }
        fun finish(result: ImportResult) { importing = false; pending!!(result) }
    }

    @Test fun the_progress_notice_stays_until_the_summary_replaces_it() = runTest {
        val transfer = SlowTransfer()
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = transfer)
        st.load()
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.onButton(NxButton.A)                                             // From a folder
        assertEquals("Working…", st.notice.value)
        advanceTimeBy(60_000); runCurrent()
        assertEquals("Working…", st.notice.value)                          // no timeout
        st.onButton(NxButton.DOWN)
        assertEquals("Working…", st.notice.value)                          // nor does a press clear it
        transfer.finish(ImportResult.Done(ImportSummary(saves = 1)))
        assertNull(st.notice.value)                                         // the results page replaces it
        assertEquals(SettingsSection.IMPORT_RESULT, assertIs<Screen.Settings>(st.screen.value).section)
    }

    @Test fun a_game_does_not_start_while_an_import_runs() = runTest {
        val transfer = RecordingTransfer().apply { importing = true }
        val runner = RecordingRunner()
        val st = state(MemorySettings(ContentRef("Home")), runner = runner, saveTransfer = transfer)
        st.load()
        val systems = assertIs<Screen.Systems>(st.screen.value)
        st.openSystem(systems.systems.first { it.tag == "NDS" })
        val game = assertIs<Screen.Games>(st.screen.value).games[0]
        st.play(game)
        assertEquals(emptyList(), runner.launched)
        assertEquals("Importing saves… please wait", st.notice.value)
        transfer.importing = false
        st.play(game)
        assertEquals(1, runner.launched.size)
    }

    @Test fun import_and_launch_refusals() {
        assertEquals("Close the running game first, then import.", SaveTransferRules.importRefusal(gameScreenAlive = true))
        assertNull(SaveTransferRules.importRefusal(gameScreenAlive = false))
        assertEquals("Importing saves… please wait", SaveTransferRules.launchRefusal(importing = true))
        assertNull(SaveTransferRules.launchRefusal(importing = false))
    }

    // Appearance → Text size: Large (the default) or Small, flipping on A or LEFT / RIGHT; Reset to defaults brings Large back.
    @Test fun appearance_text_size_flips_and_resets() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        val i = rows.indexOfFirst { it.kind == SettingsKind.TEXT_SIZE }
        assertEquals(SettingsRow(SettingsKind.TEXT_SIZE, "Text size", "Large"), rows[i])
        state.select(i); state.onButton(NxButton.A)
        assertEquals(com.nxredux.mobile.settings.OptionTextSize.SMALL, settings.optionTextSize)
        assertEquals("Small", assertIs<Screen.Settings>(state.screen.value).rows[i].secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(com.nxredux.mobile.settings.OptionTextSize.LARGE, settings.optionTextSize)
        state.onButton(NxButton.LEFT)
        state.select(assertIs<Screen.Settings>(state.screen.value).rows.indexOfFirst { it.kind == SettingsKind.RESET_DEFAULTS }); state.onButton(NxButton.A)
        assertEquals(com.nxredux.mobile.settings.OptionTextSize.LARGE, settings.optionTextSize)
    }

    // Touch only: a value row of three or more opens a chooser (every value, the current one marked); a pick applies it.
    @Test fun touch_a_multi_value_setting_opens_a_chooser_and_a_pick_applies() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.onFooterMode(FooterMode.BUTTONS)
        state.openSettings(SettingsSection.APPEARANCE)
        state.select(2); state.openSelected()                              // Secondary accent
        val chooser = state.chooser.value ?: error("no chooser")
        assertEquals("Secondary accent", chooser.title)
        assertEquals(SecondaryAccent.entries.map { it.label }, chooser.labels)
        assertEquals(SecondaryAccent.entries.indexOf(settings.secondaryAccent), chooser.selected)
        assertEquals(SecondaryAccent.DEFAULT, settings.secondaryAccent)    // opening changes nothing
        val pick = (chooser.selected + 2) % chooser.labels.size
        state.chooseValue(pick)
        assertEquals(SecondaryAccent.entries[pick], settings.secondaryAccent)
        assertNull(state.chooser.value)
        assertEquals(chooser.labels[pick], assertIs<Screen.Settings>(state.screen.value).rows[2].secondary)
    }

    @Test fun touch_the_chooser_moves_picks_with_a_and_b_closes_it_unchanged() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.onFooterMode(FooterMode.BUTTONS)
        state.openSettings(SettingsSection.APPEARANCE)
        state.select(1); state.openSelected()                              // Accent
        val before = settings.accent
        state.onButton(NxButton.DOWN)
        assertEquals((state.chooser.value!!.labels.indexOf(before.label) + 1) % state.chooser.value!!.labels.size, state.chooser.value!!.selected)
        state.onButton(NxButton.B)
        assertNull(state.chooser.value); assertEquals(before, settings.accent)
        state.openSelected(); state.onButton(NxButton.DOWN); state.onButton(NxButton.A)
        assertEquals(before.step(1), settings.accent)
    }

    @Test fun touch_a_two_value_setting_flips_and_a_controller_opens_the_picker() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.onFooterMode(FooterMode.BUTTONS)
        state.openSettings(SettingsSection.SAVES)
        state.select(assertIs<Screen.Settings>(state.screen.value).rows.indexOfFirst { it.kind == SettingsKind.EXTRACTED_NAME }); state.openSelected()
        assertNull(state.chooser.value); assertEquals(true, settings.useExtractedFileName)   // two values: a tap flips
        state.onFooterMode(FooterMode.HINTS)
        state.openSettings(SettingsSection.APPEARANCE)
        val accent = settings.accent
        state.select(1); state.openSelected()                              // Accent, many values
        assertEquals(true, state.chooser.value?.popup); assertEquals(accent, settings.accent)   // a controller's A opens the picker too
    }

    @Test fun touch_the_default_emulator_and_the_layouts_open_choosers() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.onFooterMode(FooterMode.BUTTONS)
        state.openSettings(SettingsSection.LAYOUT_GAME_LISTS)
        state.select(0); state.openSelected()
        assertEquals(listOf("List", "Grid", "Carousel", "Backdrop", "Minimal"), state.chooser.value!!.labels)
        state.chooseValue(3)
        assertEquals(MenuStyle.BACKDROP, settings.gameListStyle)
        state.openSettings(SettingsSection.DEFAULT_EMULATORS)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        val i = rows.indexOfFirst { it.enabled && it.kind == SettingsKind.DEFAULT_TAG && (CoreRegistry.consoles.first { c -> c.id == it.consoleId }.tags.size > 2) }
        if (i >= 0) {
            state.select(i); state.openSelected()
            assertEquals(CoreRegistry.consoles.first { it.id == rows[i].consoleId }.tags.size, state.chooser.value!!.labels.size)
        }
    }

    // Touch rows (the in-game menu's): every value row carries its values and the current one, in LEFT / RIGHT order.
    @Test fun settings_value_rows_carry_their_choices_and_index() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings, launcher = FakeLauncher(home = true))
        state.load()
        fun choices(section: SettingsSection, kind: SettingsKind): SettingChoices? {
            state.openSettings(section)
            val s = assertIs<Screen.Settings>(state.screen.value)
            assertEquals(s.rows.size, s.choices.size)
            return s.choices[s.rows.indexOfFirst { it.kind == kind }]
        }
        assertEquals(SettingChoices(listOf("Charcoal", "Retro"), 0), choices(SettingsSection.APPEARANCE, SettingsKind.PAD_THEME))
        assertEquals(SettingChoices(AccentPreset.entries.map { it.label }, 0), choices(SettingsSection.APPEARANCE, SettingsKind.ACCENT))
        assertEquals(SettingChoices(listOf("Large", "Small"), 0), choices(SettingsSection.APPEARANCE, SettingsKind.TEXT_SIZE))
        assertNull(choices(SettingsSection.APPEARANCE, SettingsKind.RESET_DEFAULTS))
        assertEquals(SettingChoices(listOf("Off", "On"), 0), choices(SettingsSection.SAVES, SettingsKind.EXTRACTED_NAME))
        assertEquals(SettingChoices(listOf("Off", "On"), 1), choices(SettingsSection.SYSTEM, SettingsKind.PAUSE_UNFOCUSED))
        assertEquals(SettingChoices(listOf("Hide", "Show"), 1), choices(SettingsSection.LAYOUT_TITLE_HINTS, SettingsKind.PAGE_TITLE))
        assertEquals(SettingChoices(listOf("List", "Grid", "Carousel", "Backdrop", "Minimal"), 0), choices(SettingsSection.LAYOUT_GAME_LISTS, SettingsKind.GAME_LIST_STYLE))
        assertEquals(SettingChoices(listOf("Full screen", "List"), 0), choices(SettingsSection.LAYOUT_SWITCHER, SettingsKind.SWITCHER_LAYOUT))
        assertEquals(SettingChoices(listOf("Off", "On"), 1), choices(SettingsSection.SYSTEM, SettingsKind.HOME_SCREEN))   // Android answers it
        assertNull(choices(SettingsSection.CONTROLS, SettingsKind.PAD_LAYOUT))                                          // a page, not a value
        // Default emulators: a console of several tags its emulators; one of a single tag none (dimmed).
        state.openSettings(SettingsSection.DEFAULT_EMULATORS)
        val d = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(listOf("gpSP (GBA)", "mGBA (MGBA)"), d.choices[d.rows.indexOfFirst { it.consoleId == "gba" }]?.labels)
        assertNull(d.choices[d.rows.indexOfFirst { it.consoleId == "gb" }])
        // Every page with a value row draws touch rows, Library and System (its About rows too); Import saves and Emulators keep theirs.
        for (section in listOf(SettingsSection.IMPORT_SAVES, SettingsSection.EMULATORS)) assertFalse(settingsTouchPage(state.settingsPreview(section)), section.name)
        for (section in listOf(SettingsSection.APPEARANCE, SettingsSection.MAIN_MENU, SettingsSection.LAYOUT_HOME, SettingsSection.CONTROLS, SettingsSection.SAVES, SettingsSection.SYSTEM, SettingsSection.DEFAULT_EMULATORS, SettingsSection.PAD_LAYOUT, SettingsSection.LIBRARY))
            assertTrue(settingsTouchPage(state.settingsPreview(section)), section.name)
    }

    // A touch pick sets the value by its index, as LEFT / RIGHT would step it there (side effects too); the row is selected.
    @Test fun choose_setting_sets_a_value_by_index_and_selects_its_row() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val previews = mutableListOf<GameRumble>()
        val launcher = FakeLauncher(home = false)
        val state = state(settings, rumblePreview = { previews += it }, launcher = launcher)
        state.load()
        state.openSettings(SettingsSection.CONTROLS)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        val rumble = rows().indexOfFirst { it.kind == SettingsKind.GAME_RUMBLE }
        state.settingEvent(NxSettingEvent.Choose(rumble, 3))
        assertEquals(GameRumble.entries[3], settings.gameRumble)
        assertEquals(listOf(GameRumble.entries[3]), previews)                     // the preview pulse, as a step plays
        assertEquals(rumble, state.selection.value.index)
        state.chooseSetting(rumble, 3)
        assertEquals(1, previews.size)                                            // already the one: nothing
        val pad = rows().indexOfFirst { it.kind == SettingsKind.MENU_PAD }
        state.settingEvent(NxSettingEvent.Choose(pad, 0))                         // the switch: Off
        assertFalse(settings.menuPad); assertEquals("Off", rows()[pad].secondary)
        state.chooseSetting(pad, 7)                                               // out of range
        assertFalse(settings.menuPad)
        state.openSettings(SettingsSection.SYSTEM)
        state.chooseSetting(assertIs<Screen.Settings>(state.screen.value).rows.indexOfFirst { it.kind == SettingsKind.HOME_SCREEN }, 1)
        assertEquals(listOf(true), launcher.homeRequests)                         // Android is asked, as A does
        state.openSettings(SettingsSection.DEFAULT_EMULATORS)
        val gb = assertIs<Screen.Settings>(state.screen.value).rows.indexOfFirst { it.consoleId == "gb" }
        state.chooseSetting(gb, 1)                                                // dimmed (one emulator): nothing
        assertEquals(emptyMap(), settings.defaultTags)
    }

    // A picker row (many or long values) opens its picker on a tap or A; a segmented row steps on A; a switch flips.
    @Test fun picker_rows_open_the_picker_and_others_step_or_flip() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.APPEARANCE)
        state.settingEvent(NxSettingEvent.OpenPicker(1))                          // Accent
        val picker = state.chooser.value ?: error("no picker")
        assertTrue(picker.popup); assertEquals("Accent", picker.title); assertEquals(0, picker.current); assertEquals(0, picker.selected)
        state.onButton(NxButton.UP)                                               // wraps to the last
        assertEquals(AccentPreset.entries.lastIndex, state.chooser.value!!.selected)
        state.chooseValue(4)
        assertNull(state.chooser.value); assertEquals(AccentPreset.entries[4], settings.accent)
        state.openSettings(SettingsSection.CONTROLS)
        state.select(0); state.onButton(NxButton.A)                               // Pad vibration: segmented, A steps
        assertNull(state.chooser.value); assertEquals(PadVibration.DEFAULT.step(1), settings.padVibration)
        val pad = assertIs<Screen.Settings>(state.screen.value).rows.indexOfFirst { it.kind == SettingsKind.MENU_PAD }
        state.select(pad); state.onButton(NxButton.A)                             // a switch flips
        assertFalse(settings.menuPad)
    }

    // A row's full description: its info icon or X (when its line is cut off or says less) opens it; A, B or X close it.
    @Test fun setting_info_popup_opens_on_the_icon_and_x() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.SYSTEM)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        val pause = rows.indexOfFirst { it.kind == SettingsKind.PAUSE_UNFOCUSED }
        state.settingEvent(NxSettingEvent.Info(pause))
        val info = state.settingInfo.value ?: error("no info")
        assertEquals("Pause when unfocused", info.title)
        assertEquals(settingsInfo(rows[pause], SettingsSection.SYSTEM).detail, info.text)
        state.onButton(NxButton.DOWN)
        assertEquals(1, state.settingInfo.value!!.scrollSeq)
        state.onButton(NxButton.A)
        assertNull(state.settingInfo.value)
        state.select(pause); state.onButton(NxButton.X)                           // a fuller text: X shows it
        assertNotNull(state.settingInfo.value)
        state.onButton(NxButton.X)
        assertNull(state.settingInfo.value)
        val copy = rows.indexOfFirst { it.kind == SettingsKind.COPY_INFO }
        state.select(copy); state.onButton(NxButton.X)                            // a compact row: X shows its text
        assertEquals(settingsInfo(rows[copy], SettingsSection.SYSTEM).detail, state.settingInfo.value?.text)
        state.onButton(NxButton.X)
        assertNull(state.settingInfo.value)
        val version = rows.indexOfFirst { it.kind == SettingsKind.INFO }
        state.select(version); state.onButton(NxButton.X)                         // no text at all: nothing
        assertNull(state.settingInfo.value)
        state.select(copy); state.onButton(NxButton.X)
        assertTrue(state.back())                                                  // Back closes it
        assertNull(state.settingInfo.value)
        assertIs<Screen.Settings>(state.screen.value)
    }

    // Saves live only inside the app; Export and Import are on the Saves page.
    @Test fun saves_page_has_export_import_and_the_note() = runTest {
        val transfer = RecordingTransfer()
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = transfer)
        st.load()
        st.openSettings(SettingsSection.SAVES)
        val rows = assertIs<Screen.Settings>(st.screen.value).rows
        assertEquals(
            listOf("Save format", "Save state format", "Use extracted file name", "Export saves", "Import saves"),
            rows.map { it.label },
        )
        // The note is the page's banner, under its title, not a row.
        assertEquals("Saves are stored inside the app. Export them before uninstalling or clearing app data.", assertIs<Screen.Settings>(st.screen.value).banner)
        st.select(rows.indexOfFirst { it.kind == SettingsKind.EXPORT_SAVES }); st.onButton(NxButton.A)
        assertEquals(listOf("export"), transfer.calls)
        assertEquals("Exported 1 save and 0 states (0.0 MB)", st.notice.value)
    }

    @Test fun import_page_has_three_sources() = runTest {
        val transfer = RecordingTransfer()
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = transfer)
        st.load()
        st.openSettings(SettingsSection.IMPORT_SAVES)
        fun page() = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(listOf("From a folder", "From a zip file", "From RetroArch"), page().rows.map { it.label })
        assertEquals(listOf(SettingsKind.IMPORT_FOLDER, SettingsKind.IMPORT_ZIP, SettingsKind.IMPORT_RETROARCH), page().rows.map { it.kind })
        for ((i, source) in listOf("FOLDER", "ZIP", "RETROARCH").withIndex()) {
            st.openSettings(SettingsSection.IMPORT_SAVES)
            st.select(i); st.onButton(NxButton.A)
            assertEquals("import $source", transfer.calls.last())
            assertEquals(SettingsSection.IMPORT_RESULT, page().section)
        }
    }

    @Test fun import_done_opens_results() = runTest {
        val summary = ImportSummary(saves = 1, skipped = listOf(SkippedFile("Zelda.srm", SkipReason.NO_GAME)))
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = RecordingTransfer(ImportResult.Done(summary)))
        st.load()
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.onButton(NxButton.A)
        val page = assertIs<Screen.Settings>(st.screen.value)
        assertEquals(SettingsSection.IMPORT_RESULT, page.section)
        assertEquals(
            listOf(
                Triple(SettingsKind.INFO, "Imported 1 save and 0 states", null),
                Triple(SettingsKind.INFO, "1 skipped", null),
                Triple(SettingsKind.INFO, "Zelda.srm", "no matching game"),
                Triple(SettingsKind.IMPORT_DONE, "Done", null),
            ),
            page.rows.map { Triple(it.kind, it.label, it.secondary) },
        )
        assertEquals(0, st.selection.value.index)
        assertNull(st.notice.value)
    }

    @Test fun import_results_with_states_carry_the_states_note() = runTest {
        val summary = ImportSummary(states = 1, stateEmulators = setOf("mGBA"))
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = RecordingTransfer(ImportResult.Done(summary)))
        st.load()
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.onButton(NxButton.A)
        assertEquals(summary.statesNote(), assertIs<Screen.Settings>(st.screen.value).details)
        val onboarding = atImportStep(RecordingTransfer(ImportResult.Done(summary)))
        onboarding.select(0); onboarding.onButton(NxButton.A)
        assertEquals(listOf("Bring your saves") + summary.lines() + summary.statesNote(), assertIs<Screen.Onboarding>(onboarding.screen.value).details)
    }

    @Test fun import_results_ambiguous_reason_names_the_consoles() = runTest {
        val skipped = SkippedFile("Pokemon.srm", SkipReason.AMBIGUOUS, "Game Boy and Game Boy Advance")
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = RecordingTransfer(ImportResult.Done(ImportSummary(skipped = listOf(skipped)))))
        st.load()
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.onButton(NxButton.A)
        val row = assertIs<Screen.Settings>(st.screen.value).rows.first { it.label == "Pokemon.srm" }
        assertEquals("matches Game Boy and Game Boy Advance", row.secondary)
        assertEquals("Pokemon.srm: matches Game Boy and Game Boy Advance", skipped.text())
    }

    @Test fun import_results_done_and_back_go_to_saves() = runTest {
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = RecordingTransfer())
        st.load()
        fun page() = assertIs<Screen.Settings>(st.screen.value)
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.onButton(NxButton.A)
        assertEquals(SettingsSection.IMPORT_RESULT, page().section)
        st.select(page().rows.indexOfFirst { it.kind == SettingsKind.IMPORT_DONE }); st.onButton(NxButton.A)   // Done
        assertEquals(SettingsSection.SAVES, page().section)
        assertEquals(SettingsKind.IMPORT_SAVES, page().rows[st.selection.value.index].kind)
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.onButton(NxButton.A)
        assertEquals(SettingsSection.IMPORT_RESULT, page().section)
        assertEquals(0, st.selection.value.index)                                           // a fresh page starts at the top
        st.onButton(NxButton.B)                                                              // B: Saves too, not Import saves
        assertEquals(SettingsSection.SAVES, page().section)
        assertEquals(SettingsKind.IMPORT_SAVES, page().rows[st.selection.value.index].kind)
    }

    @Test fun an_import_message_shows_as_the_notice() = runTest {
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = RecordingTransfer(ImportResult.Message("Failed: boom")))
        st.load()
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.onButton(NxButton.A)
        assertEquals("Failed: boom", st.notice.value)
    }

    @Test fun retroarch_not_found_message() = runTest {
        val transfer = RecordingTransfer(ImportResult.Message(RetroArchFolders.NOT_FOUND))
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = transfer)
        st.load()
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.select(2); st.onButton(NxButton.A)                                                // From RetroArch
        assertEquals(listOf("import RETROARCH"), transfer.calls)
        assertEquals(RetroArchFolders.NOT_FOUND, st.notice.value)
        assertEquals(SettingsSection.IMPORT_SAVES, assertIs<Screen.Settings>(st.screen.value).section)
    }

    // A Settings import that ends with Import saves still shown opens the results; ended anywhere else, it is a notice.
    @Test fun a_settings_import_ending_on_import_saves_opens_the_results() = runTest {
        val transfer = SlowTransfer()
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = transfer)
        st.load()
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.onButton(NxButton.A)
        transfer.finish(ImportResult.Done(ImportSummary(saves = 1)))
        assertEquals(SettingsSection.IMPORT_RESULT, assertIs<Screen.Settings>(st.screen.value).section)
        assertNull(st.notice.value)
    }

    @Test fun a_settings_import_ending_elsewhere_shows_its_summary_as_a_notice() = runTest {
        val transfer = SlowTransfer()
        val st = state(MemorySettings(ContentRef("Home")), saveTransfer = transfer)
        st.load()
        st.openSettings(SettingsSection.IMPORT_SAVES)
        st.onButton(NxButton.A)
        st.openSettings(SettingsSection.APPEARANCE)                                          // left while it runs
        transfer.finish(ImportResult.Done(ImportSummary(saves = 1, skipped = listOf(SkippedFile("Zelda.srm", SkipReason.NO_GAME)))))
        assertEquals(SettingsSection.APPEARANCE, assertIs<Screen.Settings>(st.screen.value).section)
        assertEquals("Imported 1 save and 0 states; 1 skipped", st.notice.value)
    }

    /** Onboarding resumed at Bring your saves (no handheld answer: Artwork is next). */
    private fun TestScope.atImportStep(transfer: SaveTransfer?): AppState {
        val settings = MemorySettings(ContentRef("Home")).apply { onboardingDone = false; onboardingStep = OnboardingStep.IMPORT_SAVES.name }
        return state(settings, saveTransfer = transfer).also { it.load() }
    }

    @Test fun onboarding_import_page_offers_the_three_sources_and_skip() = runTest {
        val st = atImportStep(RecordingTransfer())
        val page = assertIs<Screen.Onboarding>(st.screen.value)
        assertEquals(OnboardingStep.IMPORT_SAVES, page.step)
        assertEquals(listOf("Bring your saves", "Copy battery saves and save states from another app. Nothing is moved or deleted."), page.details)
        assertEquals(listOf("From a folder", "From a zip file", "From RetroArch", "Skip"), page.rows.map { it.label })
    }

    @Test fun onboarding_import_shows_results_then_continue() = runTest {
        val summary = ImportSummary(saves = 1, skipped = listOf(SkippedFile("Zelda.srm", SkipReason.NO_GAME)))
        val transfer = RecordingTransfer(ImportResult.Done(summary))
        val st = atImportStep(transfer)
        st.select(0); st.onButton(NxButton.A)                                                // From a folder
        assertEquals(listOf("import FOLDER"), transfer.calls)
        val page = assertIs<Screen.Onboarding>(st.screen.value)
        assertEquals(OnboardingStep.IMPORT_SAVES, page.step)                                 // never the Settings results page
        assertEquals(listOf("Bring your saves", "Imported 1 save and 0 states", "1 skipped"), page.details)
        assertEquals(listOf("Zelda.srm: no matching game", "Continue"), page.rows.map { it.label })
        st.select(0); st.onButton(NxButton.A)                                                // a skipped file: inert
        assertEquals(OnboardingStep.IMPORT_SAVES, assertIs<Screen.Onboarding>(st.screen.value).step)
        st.select(1); st.onButton(NxButton.A)                                                // Continue
        assertEquals(OnboardingStep.ARTWORK, assertIs<Screen.Onboarding>(st.screen.value).step)
        st.onButton(NxButton.B)                                                              // back: the step shows fresh
        assertEquals(listOf("From a folder", "From a zip file", "From RetroArch", "Skip"), assertIs<Screen.Onboarding>(st.screen.value).rows.map { it.label })
    }

    @Test fun onboarding_import_skip() = runTest {
        val transfer = RecordingTransfer()
        val st = atImportStep(transfer)
        st.select(3); st.onButton(NxButton.A)                                                // Skip
        assertEquals(emptyList(), transfer.calls)
        assertEquals(OnboardingStep.ARTWORK, assertIs<Screen.Onboarding>(st.screen.value).step)
    }

    @Test fun onboarding_import_progress_shows_on_the_step_and_rows_wait() = runTest {
        val transfer = SlowTransfer()
        val st = atImportStep(transfer)
        st.select(1); st.onButton(NxButton.A)                                                // From a zip file
        var page = assertIs<Screen.Onboarding>(st.screen.value)
        assertEquals("Working…", page.details.last())
        assertNull(st.notice.value)                                                          // no sticky Settings notice
        st.select(3); st.onButton(NxButton.A)                                                // Skip: inert while it runs
        assertEquals(OnboardingStep.IMPORT_SAVES, assertIs<Screen.Onboarding>(st.screen.value).step)
        transfer.finish(ImportResult.Done(ImportSummary(saves = 2)))
        page = assertIs<Screen.Onboarding>(st.screen.value)
        assertEquals(listOf("Bring your saves", "Imported 2 saves and 0 states"), page.details)
        assertEquals(listOf("Continue"), page.rows.map { it.label })
    }

    @Test fun onboarding_import_message_keeps_the_sources() = runTest {
        val st = atImportStep(RecordingTransfer(ImportResult.Message(RetroArchFolders.NOT_FOUND)))
        st.select(2); st.onButton(NxButton.A)                                                // From RetroArch
        val page = assertIs<Screen.Onboarding>(st.screen.value)
        assertEquals(listOf("Bring your saves", RetroArchFolders.NOT_FOUND), page.details)
        assertEquals(listOf("From a folder", "From a zip file", "From RetroArch", "Skip"), page.rows.map { it.label })
        st.select(3); st.onButton(NxButton.A)
        assertEquals(OnboardingStep.ARTWORK, assertIs<Screen.Onboarding>(st.screen.value).step)
    }

    @Test fun onboarding_import_without_a_transfer_only_skips() = runTest {
        val st = atImportStep(null)
        st.select(0); st.onButton(NxButton.A)
        assertEquals(OnboardingStep.IMPORT_SAVES, assertIs<Screen.Onboarding>(st.screen.value).step)
        st.select(3); st.onButton(NxButton.A)
        assertEquals(OnboardingStep.ARTWORK, assertIs<Screen.Onboarding>(st.screen.value).step)
    }

    @Test fun pad_vibration_row_cycles_on_left_right_and_a_and_keeps_the_selection() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.CONTROLS)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        state.select(0)
        state.onButton(NxButton.RIGHT)
        assertEquals(PadVibration.MEDIUM, settings.padVibration)
        assertEquals("Medium", rows()[0].secondary)
        assertEquals(ListSelection(0, 5), state.selection.value)
        state.onButton(NxButton.A)
        assertEquals(PadVibration.STRONG, settings.padVibration)
        assertEquals("Strong", rows()[0].secondary)
        state.onButton(NxButton.RIGHT)                               // wraps
        assertEquals(PadVibration.OFF, settings.padVibration)
        assertEquals("Off", rows()[0].secondary)
        state.onButton(NxButton.LEFT)
        assertEquals(PadVibration.STRONG, settings.padVibration)
    }

    @Test fun controls_reset_puts_pad_vibration_back_and_leaves_the_others() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { changeEveryAppearanceOption(); padVibration = PadVibration.OFF; gameRumble = GameRumble.STRONG; menuPad = false }
        val previews = mutableListOf<GameRumble>()
        val state = state(settings, rumblePreview = { previews += it })
        state.load()
        state.openSettings(SettingsSection.CONTROLS)
        state.select(4); state.onButton(NxButton.A)
        assertEquals(PadVibration.LIGHT, settings.padVibration)
        assertEquals(GameRumble.MEDIUM, settings.gameRumble)
        assertEquals(true, settings.menuPad)
        assertEquals(emptyList(), previews)                          // a reset plays no preview
        assertEquals(PadTheme.RETRO, settings.padTheme)              // Appearance's stays
        assertEquals(ConsoleArt.NONE, settings.consoleArt)             // Layouts' stays
        assertEquals(SettingsSection.CONTROLS, assertIs<Screen.Settings>(state.screen.value).section)
        assertEquals(ListSelection(4, 5), state.selection.value)
    }

    @Test fun appearance_and_layouts_resets_leave_pad_vibration() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { padVibration = PadVibration.STRONG; gameRumble = GameRumble.OFF }
        val state = state(settings)
        state.load()
        for (section in listOf(SettingsSection.APPEARANCE, SettingsSection.MAIN_MENU)) {
            state.openSettings(section)
            state.select(assertIs<Screen.Settings>(state.screen.value).rows.lastIndex); state.onButton(NxButton.A)
            assertEquals(PadVibration.STRONG, settings.padVibration, section.name)
            assertEquals(GameRumble.OFF, settings.gameRumble, section.name)
        }
    }

    @Test fun controller_row_cycles_controller_background_none_and_keeps_the_selection() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_CONSOLES)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        val i = rows().indexOfFirst { it.kind == SettingsKind.CONTROLLER_ART }
        state.select(i)
        assertEquals("Controller", rows()[i].secondary)
        state.onButton(NxButton.RIGHT)
        assertEquals(ConsoleArt.BACKGROUND, settings.consoleArt)
        assertEquals("Background", rows()[i].secondary)
        state.onButton(NxButton.RIGHT); assertEquals(ConsoleArt.NONE, settings.consoleArt)
        state.onButton(NxButton.A); assertEquals(ConsoleArt.CONTROLLER, settings.consoleArt)
        state.onButton(NxButton.LEFT); assertEquals(ConsoleArt.NONE, settings.consoleArt)
        assertEquals(i, state.selection.value.index)
    }

    @Test fun orientation_rows_flip_and_keep_the_selection_on_the_cycled_row() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        val consoles = state.selectLayoutsRow(SettingsKind.MENU_STYLE, MenuCategory.CONSOLES)
        assertEquals(3, rows().size)                                      // List: tab, layout, background; no orientation row
        assertTrue(rows().none { it.kind == SettingsKind.MENU_ORIENTATION })
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.RIGHT)    // List, Grid, Carousel
        assertEquals(4, rows().size)                                      // the orientation row in, above the background row
        assertEquals(consoles, state.selection.value.index)              // the row appeared below: the highlight stays
        assertEquals(SettingsKind.MENU_ORIENTATION, rows()[consoles + 1].kind)
        state.select(consoles + 1); state.onButton(NxButton.RIGHT)
        assertEquals(MenuOrientation.VERTICAL, settings.menuOrientations[MenuCategory.CONSOLES])
        assertEquals("Vertical", rows()[consoles + 1].secondary)
        assertEquals(consoles + 1, state.selection.value.index)
        state.onButton(NxButton.A)
        assertEquals(MenuOrientation.HORIZONTAL, settings.menuOrientations[MenuCategory.CONSOLES])
        state.onButton(NxButton.LEFT)
        assertEquals(MenuOrientation.VERTICAL, settings.menuOrientations[MenuCategory.CONSOLES])
        state.select(consoles); state.onButton(NxButton.RIGHT); state.onButton(NxButton.RIGHT)   // Carousel to Minimal, wrapping to List: the row goes
        assertEquals(3, rows().size)
        assertTrue(rows().none { it.kind == SettingsKind.MENU_ORIENTATION })
        assertEquals(consoles, state.selection.value.index)
        assertEquals(MenuOrientation.VERTICAL, settings.menuOrientations[MenuCategory.CONSOLES])   // kept
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.RIGHT)   // back to Carousel: it returns as it was
        assertEquals("Vertical", rows()[consoles + 1].secondary)
        val last = state.selectLayoutsRow(SettingsKind.GAME_LIST_STYLE)
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.RIGHT)   // List, Grid, Carousel
        assertEquals(SettingsKind.GAME_LIST_ORIENTATION, rows()[last + 1].kind)
        assertEquals(last, state.selection.value.index)
        state.select(last + 1); state.onButton(NxButton.A)
        assertEquals(MenuOrientation.VERTICAL, settings.gameListOrientation)
        assertEquals("Vertical", rows()[last + 1].secondary)
        state.select(last); state.onButton(NxButton.RIGHT); state.onButton(NxButton.RIGHT)   // Backdrop, then Minimal: the rows go
        assertEquals(last + 2, rows().size)                              // Layout options alone: Minimal has no art row
        state.onButton(NxButton.RIGHT)                                   // List
        assertEquals(last + 3, rows().size)                              // List art, Layout options
        assertEquals(last, state.selection.value.index)
        assertEquals(MenuOrientation.VERTICAL, settings.gameListOrientation)
    }

    @Test fun vertical_alignment_row_shows_only_for_a_vertical_carousel_or_backdrop() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load()
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        fun alignmentRow() = rows().singleOrNull { it.kind == SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT }
        for (style in MenuStyle.entries) for (o in MenuOrientation.entries) {
            settings.gameListStyle = style; settings.gameListOrientation = o
            state.openSettings(SettingsSection.LAYOUT_GAME_LISTS)
            val shown = (style == MenuStyle.CAROUSEL || style == MenuStyle.BACKDROP) && o == MenuOrientation.VERTICAL
            assertEquals(shown, alignmentRow() != null, "$style $o")
            if (shown) {
                val i = rows().indexOfFirst { it.kind == SettingsKind.GAME_LIST_ORIENTATION }
                assertEquals(i + 1, rows().indexOf(alignmentRow()))   // directly under the orientation row
                assertEquals("Game lists alignment", alignmentRow()!!.label)
                assertEquals("Left", alignmentRow()!!.secondary)   // the default
            }
        }
    }

    @Test fun vertical_alignment_row_flips_and_is_kept_while_hidden() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = MenuStyle.CAROUSEL; gameListOrientation = MenuOrientation.VERTICAL }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.LAYOUT_GAME_LISTS)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        val i = rows().indexOfFirst { it.kind == SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT }
        state.select(i)
        state.onButton(NxButton.RIGHT)
        assertEquals(VerticalListAlignment.RIGHT, settings.gameListVerticalAlignment)
        assertEquals("Right", rows()[i].secondary)
        assertEquals(i, state.selection.value.index)
        state.onButton(NxButton.LEFT)
        assertEquals(VerticalListAlignment.LEFT, settings.gameListVerticalAlignment)
        state.onButton(NxButton.A)
        assertEquals(VerticalListAlignment.RIGHT, settings.gameListVerticalAlignment)
        state.select(i - 1); state.onButton(NxButton.A)                  // orientation to Horizontal: the row goes
        assertEquals(null, rows().firstOrNull { it.kind == SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT })
        assertEquals(i - 1, state.selection.value.index)
        assertEquals(VerticalListAlignment.RIGHT, settings.gameListVerticalAlignment)   // kept
        state.onButton(NxButton.A)                                       // back to Vertical: it returns as it was
        assertEquals("Right", rows()[i].secondary)
    }

    @Test fun orientation_of_screens_is_vertical_only_for_carousel_and_backdrop() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuOrientations = MenuCategory.entries.associateWith { MenuOrientation.VERTICAL }
            gameListOrientation = MenuOrientation.VERTICAL
            menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.GRID)
        }
        val state = state(settings)
        val consoles = Screen.Systems(listOf(MenuTab(MenuCategory.CONSOLES, emptyList())))
        assertEquals(MenuOrientation.HORIZONTAL, state.orientationOf(consoles))
        settings.menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL)
        assertEquals(MenuOrientation.VERTICAL, state.orientationOf(consoles))
        assertEquals(MenuOrientation.HORIZONTAL, state.orientationOf(Screen.Systems(listOf(MenuTab(MenuCategory.HOME, emptyList())))))
        assertEquals(MenuOrientation.HORIZONTAL, state.orientationOf(Screen.Systems(emptyList())))
        val games = Screen.Games(ListSource.Recent, emptyList())
        settings.gameListStyle = MenuStyle.GRID
        assertEquals(MenuOrientation.HORIZONTAL, state.orientationOf(games))
        settings.gameListStyle = MenuStyle.LIST
        assertEquals(MenuOrientation.HORIZONTAL, state.orientationOf(games))
        settings.gameListStyle = MenuStyle.CAROUSEL
        assertEquals(MenuOrientation.VERTICAL, state.orientationOf(games))
        settings.gameListStyle = MenuStyle.BACKDROP
        assertEquals(MenuOrientation.VERTICAL, state.orientationOf(games))
        assertEquals(MenuOrientation.HORIZONTAL, state.orientationOf(Screen.Loading))
    }

    @Test fun stats_compute_on_the_first_show_and_on_foreground_but_not_on_a_tab_switch() = runTest {
        val gameTime = HomeGameTime(WindowTotals(3_600_000L, total(tetris, "Tetris (World)", 3_600_000L)))
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(recents = listOf(Recent(tetris))), now = { 100 * 86_400_000L }, gameTime = gameTime)
        state.load(); advanceUntilIdle()
        assertEquals(1, gameTime.windowCalls)
        // The calendar month so far: epoch day 100 is 11 April 1970, so from 1 April (day 90) 00:00 (UTC here) through now.
        assertEquals(90 * 86_400_000L to 100 * 86_400_000L, gameTime.windows.single())
        // Signed out: no count ("Sign in"); Most played by the library's name, not the tracker's.
        assertEquals(HomeStrip("1h 0m", false, null, HomeStripMost("Tetris", "1h 0m")), state.strip)
        state.onButton(NxButton.R1); state.onButton(NxButton.L1)
        assertEquals(1, gameTime.windowCalls)                            // a tab switch does not recompute
        state.onForeground(); advanceUntilIdle()
        assertEquals(2, gameTime.windowCalls)
    }

    // With a Most played, the strip is two lines on every face.
    @Test fun the_strip_is_two_lines_on_every_face() = runTest {
        val gameTime = HomeGameTime(WindowTotals(3_600_000L, total(tetris, "Tetris (World)", 3_600_000L)))
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(recents = listOf(Recent(tetris))), now = { 100 * 86_400_000L }, gameTime = gameTime)
        state.load(); advanceUntilIdle()
        assertNotNull(state.strip.most)
        state.onHomeLayout(LAND); advanceUntilIdle()
        assertEquals(2, state.homeView.value.lines)                      // wide landscape
        state.onHomeLayout(HomeFrame(933f, 616f, false, 933f / 704f)); advanceUntilIdle()
        assertEquals(2, state.homeView.value.lines)                      // landscape, not wide
        state.onHomeLayout(PORT); advanceUntilIdle()
        assertEquals(2, state.homeView.value.lines)                      // the portrait type: two
        state.onHomeLayout(HomeFrame(399f, 298f, false, 399f / 386f)); advanceUntilIdle()
        assertEquals(2, state.homeView.value.lines)                      // the Flip's cover: landscape, but narrow: two
    }

    /** The unfolded Fold's Home body: the large face (its discovery shelves under the pins). */
    private val FOLD = HomeFrame(933f, 572f, false, 933f / 660f)

    /** Home's shelf rows on screen. */
    private val AppState.shelfRows: List<MainRow.Shelf> get() = screen.value.homeRows.filterIsInstance<MainRow.Shelf>()

    // The page keys its animations on its layout, not on each Screen (App.kt): closing a menu swaps in what it held back
    // itself, without Home having to report its frame again.
    @Test fun closing_a_menu_swaps_in_the_home_rows_it_held_back() = runTest {
        val state = b2Home(games = 3, tools = 7, recent = 3); state.load(); advanceUntilIdle()   // Continue: G3; pins G1 G2
        state.onHomeLayout(LAND); advanceUntilIdle()                     // the wide face: the 2 pins inline, the 7 tools after them (top 10)
        assertTrue(state.shelfRows.isNotEmpty())                         // every frame shows the shelves
        assertEquals(10, state.homeView.value.topCount)
        val rows = state.screen.value.homeRows
        state.openContextMenu(rows.indices.first { rows[it] is MainRow.Continue }); advanceUntilIdle()   // its game menu
        assertNotNull(assertIs<Screen.Systems>(state.screen.value).menu)
        state.onHomeLayout(PORT); advanceUntilIdle()                     // rebuilt behind the menu: held back
        assertEquals(rows, state.screen.value.homeRows)
        assertEquals(10, state.homeView.value.topCount)
        state.closeContextMenu(); advanceUntilIdle()
        assertEquals(8, state.homeView.value.topCount)                   // the portrait type's rows swapped in: the pins under the tools
        assertNotEquals(rows, state.screen.value.homeRows)
        assertTrue(state.shelfRows.isNotEmpty())
    }

    @Test fun shelves_show_on_the_large_frame() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))           // Tetris, Mario, Mario Kart: never played, no pins
        state.load(); advanceUntilIdle()
        state.onHomeLayout(FOLD); advanceUntilIdle()
        val rows = state.screen.value.homeRows
        val tail = rows.takeLast(3).map { assertIs<MainRow.Shelf>(it) }  // the rows end with the shelves
        assertEquals(List(3) { HomeShelf.NOT_STARTED }, tail.map { it.shelf })
        assertEquals(setOf(tetris, mario, mk), tail.map { it.entry.game.path }.toSet())
        assertEquals(listOf(HomeShelf.NOT_STARTED to 3), state.homeView.value.shelves)
    }

    // Grid Home on the portrait type shows the shelves too (under the pin rows), once the game info is read.
    @Test fun grid_home_on_a_portrait_frame_shows_shelves() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))           // Tetris, Mario, Mario Kart: never played, no pins
        state.load(); advanceUntilIdle()
        state.onHomeLayout(HomeFrame(400f, 700f, portrait = true)); advanceUntilIdle()
        val rows = state.screen.value.homeRows
        val tail = rows.takeLast(3).map { assertIs<MainRow.Shelf>(it) }
        assertEquals(List(3) { HomeShelf.NOT_STARTED }, tail.map { it.shelf })
        assertEquals(listOf(HomeShelf.NOT_STARTED to 3), state.homeView.value.shelves)
    }

    @Test fun shelves_follow_the_frame() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load(); advanceUntilIdle()
        state.onHomeLayout(FOLD); advanceUntilIdle()
        state.select(state.screen.value.homeRows.lastIndex); advanceUntilIdle()   // on the last shelf game
        // Every frame shows the shelves (Grid Home); the focus stays in range as the rows are rebuilt.
        for (f in listOf(LAND, PORT, FOLD)) {
            state.onHomeLayout(f); advanceUntilIdle()
            assertEquals(3, state.shelfRows.size, "$f")
            assertEquals(listOf(HomeShelf.NOT_STARTED to 3), state.homeView.value.shelves, "$f")
            assertTrue(state.selection.value.index in state.screen.value.homeRows.indices, "$f")
        }
    }

    @Test fun layouts_hides_the_not_started_shelf_and_reset_brings_it_back() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load(); advanceUntilIdle()
        state.onHomeLayout(FOLD); advanceUntilIdle()
        assertEquals(listOf(HomeShelf.NOT_STARTED to 3), state.homeView.value.shelves)
        state.openSettings(SettingsSection.LAYOUT_HOME)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        assertEquals(listOf("New shelf", "Not started shelf"), rows().filter { it.kind == SettingsKind.SHELF_SHOWN }.map { it.label })
        val i = rows().indexOfFirst { it.shelf == HomeShelf.NOT_STARTED }
        state.select(i); state.onButton(NxButton.RIGHT); advanceUntilIdle()
        assertFalse(settings.showNotStartedShelf)
        assertEquals("Hide", rows()[i].secondary)
        assertEquals(i, state.selection.value.index)
        assertEquals(emptyList(), state.homeView.value.shelves)          // hidden, and its games go to no other shelf
        state.onButton(NxButton.A); advanceUntilIdle()
        assertTrue(settings.showNotStartedShelf)
        assertEquals(listOf(HomeShelf.NOT_STARTED to 3), state.homeView.value.shelves)
        settings.showNewShelf = false; settings.showNotStartedShelf = false
        state.onButton(NxButton.B)                                       // Reset to defaults is on Layouts itself
        state.select(rows().indexOfFirst { it.kind == SettingsKind.RESET_DEFAULTS }); state.onButton(NxButton.A)
        assertTrue(settings.showNewShelf && settings.showNotStartedShelf)
    }

    // Reset to defaults rebuilds Home as the single rows do: the shelf hidden by its row comes back on Home, not only in the setting.
    @Test fun reset_to_defaults_rebuilds_home_with_the_shelves_back() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings)
        state.load(); advanceUntilIdle()
        state.onHomeLayout(FOLD); advanceUntilIdle()
        state.openSettings(SettingsSection.LAYOUT_HOME)
        fun rows() = assertIs<Screen.Settings>(state.screen.value).rows
        state.select(rows().indexOfFirst { it.shelf == HomeShelf.NOT_STARTED }); state.onButton(NxButton.RIGHT); advanceUntilIdle()
        assertEquals(emptyList(), state.homeView.value.shelves)
        state.onButton(NxButton.B)                                       // Layouts
        state.select(rows().indexOfFirst { it.kind == SettingsKind.RESET_DEFAULTS }); state.onButton(NxButton.A); advanceUntilIdle()
        assertTrue(settings.showNotStartedShelf)
        assertEquals(listOf(HomeShelf.NOT_STARTED to 3), state.homeView.value.shelves)   // Home rebuilt with it
    }

    @Test fun the_shelf_rows_show_only_with_the_home_tab() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { showHomeTab = false }
        val state = state(settings)
        state.load()
        state.openSettings(SettingsSection.MAIN_MENU)
        assertTrue(assertIs<Screen.Settings>(state.screen.value).rows.none { it.kind == SettingsKind.SHELF_SHOWN })
    }

    @Test fun a_shelf_game_opens_like_a_pin() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(resumable = tetrisResumable)            // Tetris has a save, Mario none
        val state = state(MemorySettings(ContentRef("Home")), data = data, runner = runner)
        state.load(); advanceUntilIdle()
        state.onHomeLayout(FOLD); advanceUntilIdle()
        state.selectHome { it is MainRow.Shelf && it.entry.game.path == tetris }; state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals(9, runner.launched.single().resumeSlot)             // resumed from its save
        state.onForeground(); advanceUntilIdle()                         // back from the game
        state.selectHome { it is MainRow.Shelf && it.entry.game.path == mario }; state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals("Mario.gba", runner.launched[1].fileName)
        assertNull(runner.launched[1].resumeSlot)                        // played
    }

    @Test fun the_shelf_menu_offers_pin() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load(); advanceUntilIdle()
        state.onHomeLayout(FOLD); advanceUntilIdle()
        val at = state.screen.value.homeRows.indexOfFirst { it is MainRow.Shelf }
        state.openContextMenu(at)                                        // a long-press on the shelf game
        assertEquals(ContextMenus.homeGameItems(pinned = false), state.screen.value.contextMenu?.items)
    }

    // A cold start on the Fold: no shelves until play time is read, then they come once, by it (Tetris, played 100 days
    // ago, is never in Not started). The scan dates the games at day 0; the read lands on day 100, so none is New.
    @Test fun shelves_wait_for_game_info() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gameTime = object : GameTimeData by NoGameTime {
            override suspend fun byPath(): Map<String, GameTotal> { gate.await(); return mapOf(tetris to total(tetris, "Tetris", 60_000L)) }
        }
        var clock = 0L
        val state = state(MemorySettings(ContentRef("Home")), now = { clock }, gameTime = gameTime)
        state.onHomeLayout(FOLD)
        state.load(); advanceUntilIdle()
        clock = 100 * 86_400_000L
        assertTrue(state.shelfRows.isEmpty())                            // game info not read yet
        assertTrue(state.homeView.value.shelves.isEmpty())
        gate.complete(Unit); advanceUntilIdle()
        val notStarted = state.shelfRows.filter { it.shelf == HomeShelf.NOT_STARTED }.map { it.entry.game.path }.toSet()
        assertEquals(setOf(mario, mk), notStarted)
        assertEquals(listOf(tetris), state.shelfRows.filter { it.shelf == HomeShelf.PICK_UP }.map { it.entry.game.path })
    }

    // A failed play time read is not a read: no shelves (every game would show as Not started) until one succeeds.
    @Test fun a_failed_play_time_read_shows_no_shelves_until_one_succeeds() = runTest {
        var fail = true
        val gameTime = object : GameTimeData by NoGameTime {
            override suspend fun byPath(): Map<String, GameTotal> { if (fail) throw IllegalStateException("db"); return mapOf(tetris to total(tetris, "Tetris", 60_000L)) }
        }
        var clock = 0L                                                   // scanned on day 0, so none is New on day 100
        val state = state(MemorySettings(ContentRef("Home")), now = { clock }, gameTime = gameTime)
        state.onHomeLayout(FOLD)
        state.load(); advanceUntilIdle()
        clock = 100 * 86_400_000L
        assertTrue(state.shelfRows.isEmpty())
        assertTrue(state.homeView.value.shelves.isEmpty())
        fail = false
        state.onForeground(); advanceUntilIdle()                         // the next read works
        assertEquals(listOf(tetris), state.shelfRows.filter { it.shelf == HomeShelf.PICK_UP }.map { it.entry.game.path })
    }

    // A play time read that fails after one that worked keeps the last good times: Tetris stays in Pick up again
    // with its time, not back in Not started.
    @Test fun a_failed_play_time_read_keeps_the_last_good_times() = runTest {
        var fail = false
        val gameTime = object : GameTimeData by NoGameTime {
            override suspend fun byPath(): Map<String, GameTotal> { if (fail) throw IllegalStateException("db"); return mapOf(tetris to total(tetris, "Tetris", 60_000L)) }
        }
        var clock = 0L                                                   // scanned on day 0, so none is New on day 100
        val state = state(MemorySettings(ContentRef("Home")), now = { clock }, gameTime = gameTime)
        state.onHomeLayout(FOLD)
        state.load(); advanceUntilIdle()
        clock = 100 * 86_400_000L
        fail = true
        state.onForeground(); advanceUntilIdle()                         // this read fails
        assertEquals(listOf(tetris), state.shelfRows.filter { it.shelf == HomeShelf.PICK_UP }.map { it.entry.game.path })
        assertEquals(60_000L, state.gameInfo.value[tetris]?.totalMs)
    }

    // RetroAchievements failing alone still lets the shelves show (play time read).
    @Test fun a_failed_ra_read_alone_still_shows_the_shelves() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val settings = MemorySettings(ContentRef("Home")).apply { raUsername = "user"; raToken = "tok" }
        val store = FakeRaStore().apply { beforeList = { throw IllegalStateException("ra cache") } }   // not a RaStoreException: it escapes
        val controller = RaController(fakePlatform(store), settings, { null }, scope, now = { 0L })
        val gameTime = object : GameTimeData by NoGameTime {
            override suspend fun byPath(): Map<String, GameTotal> = mapOf(tetris to total(tetris, "Tetris", 60_000L))
        }
        var clock = 0L
        val state = AppState(AppDeps(settings, source, RecordingRunner(), NoopPicker(), achievements = controller, gameTime = gameTime,
            now = { clock }, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        state.onHomeLayout(FOLD)
        state.load(); advanceUntilIdle()
        clock = 100 * 86_400_000L
        state.onForeground(); advanceUntilIdle()
        assertEquals(listOf(tetris), state.shelfRows.filter { it.shelf == HomeShelf.PICK_UP }.map { it.entry.game.path })
        assertEquals(setOf(mario, mk), state.shelfRows.filter { it.shelf == HomeShelf.NOT_STARTED }.map { it.entry.game.path }.toSet())
    }

    // Pinning a shelf game moves it to the pins; the focus goes with it.
    @Test fun pinning_a_shelf_game_keeps_its_focus() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData())
        state.load(); advanceUntilIdle()
        state.onHomeLayout(FOLD); advanceUntilIdle()
        val game = state.shelfRows.last().entry.game.path               // the last shelf game: no place lands on its pin
        state.selectHome { it is MainRow.Shelf && it.entry.game.path == game }
        choose(state, ContextKind.PIN); advanceUntilIdle()
        val focus = assertIs<MainRow.Pin>(state.homeFocus())
        assertEquals(game, focus.entry.game.path)
    }

    // The month's top game reaches the strip by its library name when it is still in the library.
    @Test fun the_top_game_reaches_the_strip_by_its_library_name() = runTest {
        val tops = listOf(total(tetris, "Tetris DX", 7_200_000L), total("/Roms/gone.gb", "Gone Game", 3_600_000L), total(mario, "Mario (USA)", 60_000L))
        val gameTime = HomeGameTime(WindowTotals(10_860_000L, tops.first(), tops))
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(recents = listOf(Recent(tetris))), now = { 100 * 86_400_000L }, gameTime = gameTime)
        state.load(); advanceUntilIdle()
        assertEquals(HomeStripMost("Tetris", "2h 0m"), state.strip.most)   // the library's "Tetris", not the tracker's "Tetris DX"
    }

    @Test fun stats_keep_their_values_while_a_new_computation_runs_and_show_dots_only_before_the_first() = runTest {
        val gameTime = HomeGameTime(WindowTotals(60_000L, total("/Roms/gone.gb", "Gone Game", 60_000L)))
        gameTime.gate = CompletableDeferred()
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(recents = listOf(Recent(tetris))), now = { 0L }, gameTime = gameTime)
        state.load()
        assertEquals(HomeStrip("…", false, null, HomeStripMost("…", null)), state.strip)   // "…" before the first (signed out: "Sign in")
        gameTime.gate!!.complete(Unit); advanceUntilIdle()
        val first = state.strip
        assertEquals(HomeStripMost("Gone Game", "1m 0s"), first.most)    // not in the library: the tracker's name
        gameTime.gate = CompletableDeferred()
        gameTime.window = WindowTotals(0L, null)
        state.onForeground()
        assertEquals(first, state.strip)                                 // the old values while it runs
        gameTime.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(HomeStrip(NO_PLAY_YET, true, null, null), state.strip)   // nothing played: one line; signed out: Sign in
    }

    @Test fun a_played_games_unlock_in_the_window_counts_on_the_achievements_card() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val settings = MemorySettings(ContentRef("Home")).apply { raUsername = "user"; raToken = "tok" }   // signed in (raEnabled off)
        val store = FakeRaStore()
        val hash = "a".repeat(32)
        // As GameActivity stores a played game's pointer: the parts of the path game time recorded (the session's gamePath).
        val ref = com.nxredux.mobile.storage.GamePath.split(tetris)!!
        store.put(RaPaths.game(hash), com.nxredux.mobile.achievements.RaJson.json.encodeToString(com.nxredux.mobile.achievements.RaGamePointer.serializer(),
            com.nxredux.mobile.achievements.RaGamePointer("GB", ref.sourceId, ref.relativeDir, ref.fileName, "Tetris")))
        store.put(RaPaths.sets(hash), setsJson(42, title = "Tetris", achievements = """{"ID":1,"Title":"First"},{"ID":2,"Title":"Second"}"""))
        val day = 86_400_000L
        val now = 100 * day
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[{"ID":1,"When":${(now - day) / 1000}},{"ID":2,"When":${(now - 40 * day) / 1000}}]}""")   // one in the 5 weeks, one before
        val controller = RaController(fakePlatform(store), settings, { null }, scope, now = { now })
        val gameTime = HomeGameTime(WindowTotals(3_600_000L, total(tetris, "Tetris", 3_600_000L)), paths = setOf(tetris))
        val state = AppState(AppDeps(settings, source, RecordingRunner(), NoopPicker(), FakeUserData(recents = listOf(Recent(tetris))),
            achievements = controller, gameTime = gameTime, now = { now }, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        state.load(); advanceUntilIdle()
        assertEquals("1", state.strip.achievements)
    }

    @Test fun home_hints_follow_the_focused_row() = runTest {
        val data = FakeUserData(recents = listOf(Recent(tetris)), resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9)))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load(); advanceUntilIdle()
        val row = state.screen.value.homeRows[0]
        assertEquals(listOf(Hint(NxButton.SELECT, "Recent"), Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Resume")), mainMenuHints(row, state.resumeInfo.value.canResume))
    }

    @Test fun a_held_right_on_the_last_item_of_a_pinned_row_stays_and_a_fresh_press_switches_tab() = runTest {
        val data = FakeUserData(recents = listOf(Recent(mk)), pins = listOf(Shortcut(tetris, "Tetris"), Shortcut(mario, "Mario")))
        val state = state(MemorySettings(ContentRef("Home")), data = data)
        state.load(); advanceUntilIdle()
        state.onHomeLayout(PORT)                                         // portrait: Tetris, Mario a row
        state.selectHome { it is MainRow.Pin && it.entry.game.path == tetris }
        state.onButton(NxButton.RIGHT)
        val last = state.screen.value.homeRows.lastIndex
        assertEquals(last, state.selection.value.index)                  // Mario, the row's last item
        state.onButton(NxButton.RIGHT, repeat = true)                    // held: stops at the edge
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).category)   // still on Home
        assertEquals(last, state.selection.value.index)
        state.onButton(NxButton.RIGHT)                                   // fresh: the next tab
        assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(state.screen.value).category)
        state.onButton(NxButton.L1)
        assertEquals(last, state.selection.value.index)                  // Home kept its place
    }

    @Test fun a_held_left_on_continue_stays_and_a_fresh_press_wraps_to_tools() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(recents = listOf(Recent(tetris))))
        state.load(); advanceUntilIdle()
        state.onButton(NxButton.LEFT, repeat = true)
        assertEquals(0, state.selection.value.index)
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(state.screen.value).category)   // still on Home
        state.onButton(NxButton.LEFT)
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
    }
    private fun TestScope.threeGamesTwoTools(): AppState {
        val pins = listOf(Shortcut(tetris, "Tetris"), Shortcut(mario, "Mario"), Shortcut(mk, "Mario Kart DS"))
        val files = MemoryPrivateFiles().apply { this.files[ToolPins.FILE] = "CHEAT_DB\nSETTINGS" }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return AppState(AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), FakeUserData(pins = pins),
            cheatDb = cheatDb(FakeCheatDbPlatform(), scope), privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
    }
    private fun AppState.homePin(): String = when (val r = screen.value.homeRows[selection.value.index]) {
        is MainRow.Pin -> r.entry.game.shownName
        is MainRow.PinnedTool -> r.row.kind.name
        else -> r::class.simpleName.orEmpty()
    }

    // Every pin is on Home whatever the frame, in the same order: the tool squares, then every game in the rows.
    @Test fun every_pin_is_on_home_whatever_the_frame() = runTest {
        val state = threeGamesTwoTools(); state.load(); advanceUntilIdle()
        fun pins() = state.screen.value.homeRows.mapNotNull { (it as? MainRow.Pin)?.entry?.game?.shownName ?: (it as? MainRow.PinnedTool)?.row?.kind?.name }
        // Landscape puts the first 2 pinned games inline, before the tools; portrait keeps every game in the rows.
        val land = listOf("Tetris", "Mario", "CHEAT_DB", "SETTINGS", "Mario Kart DS")
        val port = listOf("CHEAT_DB", "SETTINGS", "Tetris", "Mario", "Mario Kart DS")
        assertEquals(land, pins())                                       // before Home reports its frame: the landscape stand-in
        state.onHomeLayout(LAND); assertEquals(land, pins())
        state.onHomeLayout(PORT); assertEquals(port, pins())
    }

    // Portrait: Pick a game, Cheat DB, Settings (a column) | Tetris, Mario | Mario Kart. Pins move in rows (a shorter last
    // row gives its last pin), UP from the first row goes to the top tile last on, the squares move in their column and
    // DOWN leaves them for the pin under them; a fresh press past a row's start switches tab.
    @Test fun the_d_pad_moves_through_the_pin_rows() = runTest {
        val state = threeGamesTwoTools(); state.load(); advanceUntilIdle()
        state.onHomeLayout(PORT)
        state.selectHome { it is MainRow.Pin && it.entry.game.path == mario }
        state.onButton(NxButton.DOWN); assertEquals("Mario Kart DS", state.homePin())   // the shorter last row: its last
        state.onButton(NxButton.DOWN); assertEquals("Mario Kart DS", state.homePin())   // the last row: stays
        state.onButton(NxButton.UP); assertEquals("Tetris", state.homePin())
        state.onButton(NxButton.RIGHT); assertEquals("Mario", state.homePin())
        state.onButton(NxButton.RIGHT, repeat = true); assertEquals("Mario", state.homePin())   // held: the row's end
        state.onButton(NxButton.UP); assertEquals("PickAGame", state.homePin())          // the top tile last on
        state.onButton(NxButton.RIGHT); assertEquals("CHEAT_DB", state.homePin())        // the top square of the column
        state.onButton(NxButton.DOWN); assertEquals("SETTINGS", state.homePin())
        state.onButton(NxButton.DOWN); assertEquals("Mario", state.homePin())            // the pin under the squares
        state.selectHome { it is MainRow.Pin && it.entry.game.path == mk }
        state.onButton(NxButton.LEFT, repeat = true); assertEquals("Mario Kart DS", state.homePin())
        state.onButton(NxButton.LEFT)                                    // fresh, past the row's start: the tab before (Tools)
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
    }

    // A layout change keeps the focused tile and the remembered top tile by identity across a slot-count change.
    // Portrait's columns of 5 (10 slots past 5 tools) show all 7 tools: Pick a game, the tools 1–7, G1… from 8; a tall
    // window (UNDER, 5 slots) Pick a game, Cheat DB … Beta, "+3" (index 5, the row's right end), G1… from 6, 2 a row.
    // "+3" remembered there comes back in portrait as Epsilon, its last tool.
    @Test fun a_rotation_keeps_the_focused_and_the_remembered_tile() = runTest {
        val state = b2Home(games = 6, tools = 7); state.load(); advanceUntilIdle()
        state.onHomeLayout(TALL); advanceUntilIdle()
        state.select(5); assertEquals(MainRow.MoreTools(3), state.homeFocus())
        state.onButton(NxButton.DOWN)                                    // the pin under the row's right end: G2, index 7
        assertEquals(b2Path(2), assertIs<MainRow.Pin>(state.homeFocus()).entry.game.path); assertEquals(7, state.selection.value.index)
        state.onButton(NxButton.DOWN)                                    // the next row: G4, index 9
        assertEquals(b2Path(4), assertIs<MainRow.Pin>(state.homeFocus()).entry.game.path); assertEquals(9, state.selection.value.index)
        state.onHomeLayout(PORT); advanceUntilIdle()
        assertEquals(b2Path(4), assertIs<MainRow.Pin>(state.homeFocus()).entry.game.path); assertEquals(11, state.selection.value.index)
        state.onButton(NxButton.UP)                                      // portrait's rows are 2 a row: G4's above is G2
        assertEquals(b2Path(2), assertIs<MainRow.Pin>(state.homeFocus()).entry.game.path); assertEquals(9, state.selection.value.index)
        state.onButton(NxButton.UP)                                      // the top tile last on, by identity: "+3" is Epsilon now
        assertEquals("Epsilon", assertIs<MainRow.PinnedTool>(state.homeFocus()).row.label); assertEquals(7, state.selection.value.index)
    }

    private val b2Path = { i: Int -> "/Roms/Game Boy Advance (GBA)/G$i.gba" }
    private val b2Source = FakeRomsSource(dirs = mapOf(
        "Home" to listOf("Roms"),
        "Home/Roms" to listOf("Game Boy Advance (GBA)"),
        "Home/Roms/Game Boy Advance (GBA)" to (1..6).map { "G$it.gba" },
    ))
    private val b2Apps = listOf(InstalledApp("a/A", "Alpha"), InstalledApp("b/B", "Beta"), InstalledApp("c/C", "Gamma"), InstalledApp("d/D", "Delta"), InstalledApp("e/E", "Epsilon"))

    /**
     * Home on six GBA games: [games] of them pinned (G1 first), [tools] of Cheat Database, Settings, Alpha, Beta,
     * Gamma, Delta, Epsilon pinned in that order (past 7, apps Extra 1, Extra 2… after them, so a board's slots can
     * run out), Continue on [recent] (null: Pick a game).
     */
    private fun TestScope.b2Home(
        games: Int, tools: Int, recent: Int? = null, runner: RecordingRunner = RecordingRunner(), hidden: Set<MenuCategory> = emptySet(),
        style: MenuStyle? = null,
    ): AppState {
        val apps = b2Apps + (1..(tools - 7).coerceAtLeast(0)).map { InstalledApp("x$it/X", "Extra $it") }
        val keys = (listOf("CHEAT_DB", "SETTINGS") + apps.map { "APP:${it.id}" }).take(tools)
        val files = MemoryPrivateFiles().apply { this.files[ToolPins.FILE] = keys.joinToString("\n") }
        val settings = MemorySettings(ContentRef("Home")).apply {
            toolApps = apps.map { it.id }; for (c in hidden) setTabShown(c, false)
            if (style != null) menuStyles = LIST_TABS + (MenuCategory.HOME to style)
        }
        val data = FakeUserData(recents = listOfNotNull(recent?.let { Recent(b2Path(it)) }), pins = (1..games).map { Shortcut(b2Path(it), "G$it") })
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return AppState(AppDeps(settings, b2Source, runner, NoopPicker(), data, launcher = FakeLauncher(apps),
            privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
    }

    private fun AppState.homeFocus(): MainRow = screen.value.homeRows[selection.value.index]

    // A landscape split (SPS3, 800 × 340) puts the first 2 pinned games inline too: the rows are Pick a game, G1, G2,
    // then the tools, then G3 a row game; they line up with the board the screen draws, and the d-pad walks them in order.
    @Test fun sps3_puts_two_pinned_games_inline_in_the_rows_and_the_board() = runTest {
        val state = b2Home(games = 3, tools = 2); state.load(); advanceUntilIdle()
        val sps3 = HomeFrame(800f, 340f, false, 800f / 428f)
        state.onHomeLayout(sps3); advanceUntilIdle()
        val view = state.homeView.value
        val rows = state.screen.value.homeRows
        assertEquals(5, view.topCount)
        assertEquals(MainRow.PickAGame, rows[0])
        assertEquals(listOf(b2Path(1), b2Path(2)), rows.subList(1, 3).map { assertIs<MainRow.Pin>(it).entry.game.path })
        assertTrue(rows.subList(3, 5).all { it is MainRow.PinnedTool })
        assertEquals(b2Path(3), assertIs<MainRow.Pin>(rows[5]).entry.game.path)
        val b = homeBoard(view.frame!!, view.lines!!, view.tools, view.games, view.shelves)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames)
        assertEquals(view.topCount, b.top.size)
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME, HomeTopKind.GAME, HomeTopKind.TOOL, HomeTopKind.TOOL), b.top.map { it.kind })
        state.select(0)
        state.onButton(NxButton.RIGHT); assertEquals(1, state.selection.value.index)    // Continue → G1
        state.onButton(NxButton.DOWN); assertEquals(2, state.selection.value.index)     // G1 → G2, under it
        state.onButton(NxButton.UP); state.onButton(NxButton.RIGHT)
        assertEquals(3, state.selection.value.index)                                     // G1 → the top square
        state.select(2); state.onButton(NxButton.DOWN)
        assertEquals(b2Path(3), assertIs<MainRow.Pin>(state.homeFocus()).entry.game.path)   // G2 → the row game
    }

    // Six GBA games, none pinned or played: Not started picks 4. Grid Home shows what its board fits ([homeShelfCap]): 4
    // in portrait (two rows of 2) and on the large face (a row of 4), 3 on the wide face (a row of 3); List and Carousel
    // Home show 3 whatever the frame.
    @Test fun a_shelf_shows_four_on_grid_portrait_and_three_on_list_and_carousel() = runTest {
        val grid = b2Home(games = 0, tools = 0, style = MenuStyle.GRID); grid.load(); advanceUntilIdle()
        for ((f, n) in listOf(PORT to 4, LAND to 3, FOLD to 4)) {
            grid.onHomeLayout(f); advanceUntilIdle()
            assertEquals(listOf(HomeShelf.NOT_STARTED to n), grid.homeView.value.shelves, "grid $f")
            assertEquals(n, grid.shelfRows.size, "grid $f")
        }
        for (style in listOf(MenuStyle.LIST, MenuStyle.CAROUSEL)) {
            val state = b2Home(games = 0, tools = 0, style = style); state.load(); advanceUntilIdle()
            for (f in listOf(PORT, FOLD)) {
                state.onHomeLayout(f); advanceUntilIdle()
                assertEquals(3, state.shelfRows.size, "$style $f")
            }
        }
    }

    // The wide face (the Flip): with 2 pinned games (Continue's G3 left out of 3) they sit in the top section
    // between Continue and the squares; A on one launches it as a pin, and the focus follows it onto Continue.
    @Test fun the_wide_face_puts_two_games_inline_and_a_launches_them_as_pins() = runTest {
        val runner = RecordingRunner()
        val state = b2Home(games = 3, tools = 6, recent = 3, runner = runner); state.load(); advanceUntilIdle()
        state.onHomeLayout(HomeFrame(804f, 261f, false, 804f / 330f)); advanceUntilIdle()
        val rows = state.screen.value.homeRows
        val top = rows.take(state.homeView.value.topCount)
        assertEquals(9, top.size); assertEquals(top, rows.filterNot { it is MainRow.Shelf })   // then the shelves
        assertIs<MainRow.Continue>(top[0])
        assertEquals(listOf(b2Path(1), b2Path(2)), top.subList(1, 3).map { assertIs<MainRow.Pin>(it).entry.game.path })
        assertTrue(top.drop(3).all { it is MainRow.PinnedTool })
        state.onButton(NxButton.RIGHT); assertEquals(1, state.selection.value.index)        // Continue → G1
        state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals("G1.gba", runner.launched.single().fileName)
        state.onForeground(); advanceUntilIdle()                                             // back: G1 is Continue
        assertEquals(b2Path(1), assertIs<MainRow.Continue>(state.homeFocus()).entry.game.path)
        assertEquals(listOf(b2Path(2), b2Path(3)), state.screen.value.homeRows.filterIsInstance<MainRow.Pin>().map { it.entry.game.path })
    }

    // The large face (the unfolded Fold) takes 1–2 pinned games inline too:
    // with Continue's G3 left out of 3, G1 and G2 sit in the top section, stacked; RIGHT from Continue is G1, DOWN G2 (the
    // shape rebuilt from the rows counts them inline, as the board does); no Pin is left for the rows.
    @Test fun the_large_face_puts_two_games_inline_stacked() = runTest {
        val state = b2Home(games = 3, tools = 6, recent = 3); state.load(); advanceUntilIdle()
        state.onHomeLayout(FOLD); advanceUntilIdle()
        val rows = state.screen.value.homeRows
        val topCount = state.homeView.value.topCount
        assertEquals(9, topCount)
        assertEquals(listOf(b2Path(1), b2Path(2)), rows.subList(1, 3).map { assertIs<MainRow.Pin>(it).entry.game.path })
        assertTrue(rows.drop(topCount).none { it is MainRow.Pin })
        assertEquals(2, state.homeView.value.games)
        state.select(0); state.onButton(NxButton.RIGHT); assertEquals(1, state.selection.value.index)   // Continue → G1
        state.onButton(NxButton.DOWN); assertEquals(2, state.selection.value.index)                      // G1 → G2, under it
        state.onButton(NxButton.UP); assertEquals(1, state.selection.value.index)
    }

    // A tall window (UNDER), 7 tools in 5 slots: Cheat Database, Settings, Alpha, Beta, then "+3" (index 5, after Pick a
    // game). A on it opens the Tools tab.
    @Test fun more_tools_opens_the_tools_tab() = runTest {
        val state = b2Home(games = 1, tools = 7); state.load(); advanceUntilIdle()
        state.onHomeLayout(TALL); advanceUntilIdle()
        assertEquals(MainRow.MoreTools(3), state.screen.value.homeRows[5])
        state.select(5); state.onButton(NxButton.A)
        assertEquals(MenuCategory.TOOLS, state.category())
    }

    // The view names the frame and strip lines its rows were built for (none before the first report), so HomeScreen
    // places rows built for an older frame on their own board until the rebuild for the new one arrives.
    @Test fun the_home_view_carries_the_frame_its_rows_were_built_for() = runTest {
        val state = b2Home(games = 3, tools = 2); state.load(); advanceUntilIdle()
        assertNull(state.homeView.value.frame)
        state.onHomeLayout(LAND); advanceUntilIdle()
        assertEquals(LAND, state.homeView.value.frame)
        assertEquals(1, state.homeView.value.lines)                     // landscape: the strip on one line
        state.onHomeLayout(PORT); advanceUntilIdle()
        assertEquals(PORT, state.homeView.value.frame)
        assertEquals(homeStripLines(state.homeView.value.strip), state.homeView.value.lines)
    }

    // A rebuild landing while the switcher is up publishes its own view; closing the switcher restores the older Home
    // rows, and their own view with them (frame, counts), not the newer one built meanwhile. 7 tools (a tall window's
    // "+3", portrait all 7), so the rows differ.
    @Test fun closing_the_switcher_restores_the_home_rows_own_view() = runTest {
        val state = b2Home(games = 3, tools = 7); state.load(); advanceUntilIdle()   // the tall window's rows differ from portrait's
        state.onHomeLayout(TALL); advanceUntilIdle()
        val landRows = state.screen.value.homeRows
        val landView = state.homeView.value
        state.openSwitcher(); advanceUntilIdle()
        assertIs<Screen.Switcher>(state.screen.value)
        state.onHomeLayout(PORT); advanceUntilIdle()                    // rebuilt for portrait off the menu
        assertEquals(PORT, state.homeView.value.frame)
        state.onButton(NxButton.B); advanceUntilIdle()
        assertEquals(landRows, state.screen.value.homeRows)              // the snapshot the switcher opened over
        assertEquals(landView, state.homeView.value)
        assertEquals(TALL, state.homeView.value.frame)
        state.onHomeLayout(PORT); advanceUntilIdle()                    // Home re-reports: the portrait rows swap in
        assertEquals(PORT, state.homeView.value.frame)
    }

    // A tool square is a top-section row: A opens the tool.
    @Test fun a_pinned_tool_square_opens_its_tool() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val files = MemoryPrivateFiles().apply { this.files[ToolPins.FILE] = "GAME_TIME" }   // Game Tracker pinned on Home
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), source, RecordingRunner(), NoopPicker(), FakeUserData(recents = listOf(Recent(tetris))),
            gameTime = HomeGameTime(), privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        state.load(); advanceUntilIdle()
        state.onHomeLayout(LAND); advanceUntilIdle()
        val at = state.screen.value.homeRows.indexOfFirst { it is MainRow.PinnedTool }
        assertTrue(at in 1 until state.homeView.value.topCount)        // in the top section, beside Continue
        state.select(at); state.onButton(NxButton.A); advanceUntilIdle()
        assertIs<Screen.GameTime>(state.screen.value)
    }

    // 7 tools: a tall window's 4 and "+3" put G1 at index 6, portrait's two columns show all 7 (G1 at 8). The focus stays
    // on G1 both ways, and so does the row remembered off Home.
    @Test fun a_rotation_keeps_focus_on_the_same_game() = runTest {
        val state = b2Home(games = 3, tools = 7); state.load(); advanceUntilIdle()
        val g1 = MainRow.Pin(state.screen.value.homeRows.filterIsInstance<MainRow.Pin>().first { it.entry.game.path == b2Path(1) }.entry)
        state.onHomeLayout(TALL); advanceUntilIdle()
        assertEquals(6, state.screen.value.homeRows.indexOf(g1))
        state.select(6)
        state.onHomeLayout(PORT); advanceUntilIdle()
        assertEquals(8, state.screen.value.homeRows.indexOf(g1))
        assertEquals(g1, state.homeFocus())
        state.onButton(NxButton.R1)                                      // away from Home, rotated there, back
        state.onHomeLayout(TALL); advanceUntilIdle()
        state.onButton(NxButton.L1)
        assertEquals(g1, state.homeFocus())
        assertEquals(6, state.selection.value.index)
    }

    // Before Home's first report the rows are built on a stand-in frame; a move made then survives the
    // rebuild on the real frame, by identity.
    @Test fun the_first_layout_keeps_a_moved_selection() = runTest {
        val state = b2Home(games = 4, tools = 0, recent = 1); state.load(); advanceUntilIdle()
        state.onButton(NxButton.DOWN)                                    // Continue (G1) to the first row game under it: G4
        val moved = state.homeFocus()                                    // (G2 and G3 sit inline on the landscape stand-in)
        assertEquals(b2Path(4), assertIs<MainRow.Pin>(moved).entry.game.path)
        state.onHomeLayout(LAND); advanceUntilIdle()
        assertEquals(moved, state.homeFocus())
        state.onHomeLayout(PORT); advanceUntilIdle()                     // the same rows on another board
        assertEquals(moved, state.homeFocus())
    }

    // Landscape (the wide face), 6 tools, 6 games: Pick a game 0, G1 1 and G2 2 inline (stacked), the block 3 4 / 5 6 /
    // 7 8 (2 columns of the 5-row grid), then G3 G4 G5 | G6.
    @Test fun the_d_pad_uses_the_board() = runTest {
        val state = b2Home(games = 6, tools = 6); state.load(); advanceUntilIdle()
        state.onHomeLayout(LAND); advanceUntilIdle()
        state.onButton(NxButton.RIGHT)
        assertEquals(1, state.selection.value.index); assertEquals(b2Path(1), assertIs<MainRow.Pin>(state.homeFocus()).entry.game.path)   // G1, inline
        state.onButton(NxButton.RIGHT)
        assertEquals(3, state.selection.value.index); assertIs<MainRow.PinnedTool>(state.homeFocus())   // the block's top left
        state.onButton(NxButton.DOWN); assertEquals(5, state.selection.value.index)                     // the square below
        state.onButton(NxButton.LEFT); assertEquals(1, state.selection.value.index)                     // the left column: G1
        state.onButton(NxButton.RIGHT); assertEquals(5, state.selection.value.index)                    // back to the square it left
        state.onButton(NxButton.RIGHT); state.onButton(NxButton.DOWN); assertEquals(8, state.selection.value.index)   // the block's third row, right
        state.onButton(NxButton.DOWN); assertEquals(11, state.selection.value.index)                    // the row's third game
        assertEquals(b2Path(5), assertIs<MainRow.Pin>(state.homeFocus()).entry.game.path)
    }

    // A layout change with 12 tools and 6 games: portrait (10 slots) is Pick a game, 9 tools, "+3", then G1…G6; a tall
    // window without the pad (UNDER, 5 slots) shows 4 and "+8" (the wide face shows all 12). The focus on "+N" stays on
    // "+N" (its count changes with the slots), on and off Home.
    @Test fun a_rotation_keeps_focus_on_more() = runTest {
        val state = b2Home(games = 6, tools = 12); state.load(); advanceUntilIdle()
        state.onHomeLayout(PORT); advanceUntilIdle()
        state.select(10); assertEquals(MainRow.MoreTools(3), state.homeFocus())
        state.onHomeLayout(TALL); advanceUntilIdle()
        assertEquals(MainRow.MoreTools(8), state.homeFocus())
        state.onHomeLayout(PORT); advanceUntilIdle()
        assertEquals(MainRow.MoreTools(3), state.homeFocus())
        state.onButton(NxButton.R1)                                      // away from Home, rotated there, back
        state.onHomeLayout(TALL); advanceUntilIdle()
        state.onButton(NxButton.L1)
        assertEquals(MainRow.MoreTools(8), state.homeFocus())
    }

    // A tool the new slots hide (Delta, portrait's index 6, its columns show all 7, is behind a tall window's "+3"): the
    // focus goes to "+N"; back in portrait "+3" is Epsilon, the last tool.
    @Test fun a_rotation_moves_focus_from_a_hidden_tool_to_more() = runTest {
        val state = b2Home(games = 6, tools = 7); state.load(); advanceUntilIdle()
        state.onHomeLayout(PORT); advanceUntilIdle()
        state.select(6); assertEquals("Delta", assertIs<MainRow.PinnedTool>(state.homeFocus()).row.label)
        state.onHomeLayout(TALL); advanceUntilIdle()
        assertEquals(MainRow.MoreTools(3), state.homeFocus())
        state.onHomeLayout(PORT); advanceUntilIdle()
        assertEquals("Epsilon", assertIs<MainRow.PinnedTool>(state.homeFocus()).row.label)
    }

    // 7 tools: a tall window's "+3" (index 5); portrait shows all 7, so "+3", whose tools all fit there, becomes the last
    // tool, Epsilon (index 7).
    @Test fun more_whose_tools_all_fit_after_a_rotation_becomes_the_last_tool() = runTest {
        val state = b2Home(games = 6, tools = 7); state.load(); advanceUntilIdle()
        state.onHomeLayout(TALL); advanceUntilIdle()
        state.select(5); assertEquals(MainRow.MoreTools(3), state.homeFocus())
        state.onHomeLayout(PORT); advanceUntilIdle()
        assertEquals("Epsilon", assertIs<MainRow.PinnedTool>(state.homeFocus()).row.label)
        assertEquals(7, state.selection.value.index)
    }

    // Home reports its frame with the tab row hidden (its 44 dp in the body): the squares grow but every column keeps its
    // 5, so portrait's 10 slots hold, 12 tools still show 9 and "+3", and the focus stays on "+3". (A hidden hint bar
    // keeps its room, so it never reaches the frame.)
    @Test fun a_hidden_tab_row_keeps_homes_tool_slots() = runTest {
        val state = b2Home(games = 6, tools = 12); state.load(); advanceUntilIdle()
        state.onHomeLayout(PORT); advanceUntilIdle()
        state.select(10); assertEquals(MainRow.MoreTools(3), state.homeFocus())
        val hidden = PORT.copy(h = PORT.h + 44f, titleHidden = true)
        state.onHomeLayout(hidden); advanceUntilIdle()
        assertEquals(hidden, state.homeView.value.frame)
        assertEquals(MainRow.MoreTools(3), state.screen.value.homeRows.single { it is MainRow.MoreTools })
        assertEquals(9, state.screen.value.homeRows.count { it is MainRow.PinnedTool })
        assertEquals(MainRow.MoreTools(3), state.homeFocus())
    }

    // Focus on "+2" (portrait, 11 tools: 9 and "+2" at index 10), then a tool unpinned (on the Tools tab "+2" opened):
    // back on Home the ten tools all show and the focus is on index 10, the last square, not past the top section.
    @Test fun unpinning_a_tool_moves_focus_off_more() = runTest {
        val state = b2Home(games = 1, tools = 11); state.load(); advanceUntilIdle()
        state.onHomeLayout(PORT); advanceUntilIdle()
        state.select(10); assertEquals(MainRow.MoreTools(2), state.homeFocus())
        state.onButton(NxButton.A)                                       // "+2": the Tools tab
        assertEquals(MenuCategory.TOOLS, state.category())
        state.select(state.screen.value.let { assertIs<Screen.Systems>(it) }.rows.indexOfFirst { it is MainRow.Tool && it.row.kind == ToolKind.SETTINGS })
        choose(state, ContextKind.UNPIN_TOOL); advanceUntilIdle()
        while (state.category() != MenuCategory.HOME) state.onButton(NxButton.L1)   // back to Home
        assertTrue(state.screen.value.homeRows.none { it is MainRow.MoreTools })
        assertEquals(10, state.selection.value.index)
        assertIs<MainRow.PinnedTool>(state.homeFocus())
    }

    @Test fun a_failed_stats_read_on_foreground_keeps_the_stat_rows() = runTest {
        val gameTime = object : GameTimeData by NoGameTime {
            var calls = 0
            override suspend fun window(sinceMs: Long, nowMs: Long): WindowTotals {
                if (++calls >= 2) throw IllegalStateException("database gone")
                return WindowTotals(3_600_000L, total(tetris, "Tetris", 3_600_000L))
            }
        }
        val state = state(MemorySettings(ContentRef("Home")), data = FakeUserData(recents = listOf(Recent(tetris))), now = { 0L }, gameTime = gameTime)
        state.load(); advanceUntilIdle()
        val before = state.strip
        assertEquals("1h 0m", before.played)
        state.onForeground(); advanceUntilIdle()
        assertEquals(2, gameTime.calls)
        assertEquals(before, state.strip)
    }

    @Test fun launcher_group_and_android_games_show_only_with_a_launcher() = runTest {
        val none = state(MemorySettings(ContentRef("Home"))); none.load()
        none.openSettings(SettingsSection.SYSTEM)
        assertTrue(assertIs<Screen.Settings>(none.screen.value).rows.none { it.label == "Launcher" || it.kind == SettingsKind.HOME_SCREEN })
        none.openSettings(SettingsSection.LIBRARY)
        assertTrue(assertIs<Screen.Settings>(none.screen.value).rows.none { it.kind == SettingsKind.ANDROID_GAMES })
        val with = state(MemorySettings(ContentRef("Home")), launcher = FakeLauncher()); with.load(); with.openSettingsMenu()
        val sections = listOf(SettingsSection.LIBRARY, SettingsSection.EMULATORS, SettingsSection.APPEARANCE, SettingsSection.MAIN_MENU, SettingsSection.CONTROLS, SettingsSection.SYSTEM)
        assertEquals(sections, assertIs<Screen.SettingsMenu>(with.screen.value).sections)   // no Launcher category: its rows are on System and Library
        assertEquals(sections, with.settingsSections())   // the two-pane left pane lists the same
        with.openSettings(SettingsSection.SYSTEM)
        assertEquals(listOf("General", "Launcher", "Developer", "About", "Licenses"), assertIs<Screen.Settings>(with.screen.value).rows.filter { it.kind == SettingsKind.SECTION }.map { it.label })
    }

    @Test fun launcher_page_toggles_the_home_screen() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { toolApps = listOf("a/A", "b/B") }
        val launcher = FakeLauncher()
        val state = state(settings, launcher = launcher)
        state.load()
        state.openSettings(SettingsSection.SYSTEM)
        val page = assertIs<Screen.Settings>(state.screen.value)
        val at = rowIndex(state, SettingsKind.HOME_SCREEN)
        assertEquals(SettingsRow(SettingsKind.SECTION, "Launcher"), page.rows[at - 1])         // in System's Launcher group
        assertEquals(SettingsRow(SettingsKind.HOME_SCREEN, "Use as home screen", "Off"), page.rows[at])
        state.select(at); state.onButton(NxButton.A)
        assertEquals(listOf(true), launcher.homeRequests)
        launcher.home = true; state.onLauncherChanged()                    // the role dialog came back
        assertEquals("On", libraryRow(state, SettingsKind.HOME_SCREEN).secondary)
        state.onButton(NxButton.A)
        assertEquals(listOf(true, false), launcher.homeRequests)
    }

    @Test fun a_refused_home_role_says_where_to_set_it() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), launcher = FakeLauncher())
        state.load()
        state.openSettings(SettingsSection.SYSTEM)
        state.onHomeRoleRefused()
        assertEquals("Set NX Redux as the home app in Android settings → Default apps", state.notice.value)
        assertEquals(SettingsSection.SYSTEM, assertIs<Screen.Settings>(state.screen.value).section)
    }

    @Test fun go_home_lands_on_the_first_tab() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), launcher = FakeLauncher(home = true))
        state.load()
        state.openTools()
        state.openSettings(SettingsSection.APPEARANCE)
        state.goHome()
        val menu = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(0, menu.tab)
        assertEquals(MenuCategory.HOME, menu.category)                   // Home is always the first tab
    }

    @Test fun go_home_from_default_emulators_after_a_change_rescans_then_lands_home() = runTest {
        val counting = CountingRomsSource(source)
        val settings = MemorySettings(ContentRef("Home"))
        val st = state(settings, counting, launcher = FakeLauncher(home = true))
        st.load()
        st.openSettings(SettingsSection.DEFAULT_EMULATORS)
        st.onButton(NxButton.DOWN)
        st.onButton(NxButton.RIGHT)                                                              // GBA -> MGBA: the index no longer fits
        counting.calls.clear()
        st.goHome()
        assertTrue("listChildren:Home/Roms" in counting.calls)                                   // rescanned, as B would
        val menu = assertIs<Screen.Systems>(st.screen.value)
        assertEquals(MenuCategory.HOME, menu.category)
        counting.calls.clear()
        st.openSettings(SettingsSection.LIBRARY); st.back()                                      // the index fits now: no second scan
        assertTrue(counting.calls.isEmpty())
    }

    @Test fun go_home_from_an_unchanged_library_page_does_not_scan() = runTest {
        val counting = CountingRomsSource(source)
        val st = state(MemorySettings(ContentRef("Home")), counting, launcher = FakeLauncher(home = true))
        st.load()
        st.openSettings(SettingsSection.DEFAULT_EMULATORS)
        counting.calls.clear()
        st.goHome()
        assertTrue(counting.calls.isEmpty())
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(st.screen.value).category)
    }

    @Test fun go_home_from_emulator_settings_over_a_changed_library_rescans() = runTest {
        val counting = CountingRomsSource(source)
        val st = state(MemorySettings(ContentRef("Home")), counting, launcher = FakeLauncher(home = true))
        st.load()
        st.openSettings(SettingsSection.DEFAULT_EMULATORS)
        st.cycleDefaultTag("gba")                                                                // the index no longer fits
        st.openSettings(SettingsSection.EMULATORS)                                               // a tap on the left pane: no B, no rescan
        st.select(assertIs<Screen.Settings>(st.screen.value).rows.indexOfFirst { it.kind == SettingsKind.EMULATORS }); st.onButton(NxButton.A)
        advanceUntilIdle()                                                                       // its rows build on deps.compute
        counting.calls.clear()
        st.goHome()
        assertTrue("listChildren:Home/Roms" in counting.calls)                                   // the reload B from Emulators makes
        assertEquals(MenuCategory.HOME, assertIs<Screen.Systems>(st.screen.value).category)
    }

    @Test fun go_home_during_the_library_rescan_stays_put() = runTest {
        val held = HeldSource(source)
        val st = state(MemorySettings(ContentRef("Home")), held, launcher = FakeLauncher(home = true))
        st.load()
        openLibraryOn(st, SettingsKind.RESCAN)
        held.hold = true
        st.onButton(NxButton.A)                                                                  // the counts row: rescan: not cancellable
        st.goHome()
        assertIs<Screen.Settings>(st.screen.value)                                               // the scan runs on
        held.gate.complete(Unit); runCurrent()
        assertEquals(SettingsSection.LIBRARY, assertIs<Screen.Settings>(st.screen.value).section)   // and lands by itself
    }

    @Test fun go_home_from_the_switcher_applies_a_y_remove() = runTest {
        val data = FakeUserData(
            recents = listOf(Recent(tetris), Recent(mario)),
            resumable = mapOf(("GB" to "Tetris.gb") to ResumeInfo(9), ("GBA" to "Mario.gba") to ResumeInfo(9)),
        )
        val state = state(MemorySettings(ContentRef("Home")), data = data, launcher = FakeLauncher(home = true))
        state.load()
        state.onButton(NxButton.SELECT)
        state.onButton(NxButton.Y)
        state.onButton(NxButton.A)                                       // confirm: Tetris leaves recent.txt
        state.goHome()
        advanceUntilIdle()
        val after = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(MenuCategory.HOME, after.category)
        assertEquals("Mario", assertIs<MainRow.Continue>(after.rows[0]).entry.game.shownName)
    }

    @Test fun a_tool_app_that_fails_to_open_says_so() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { toolApps = listOf("b/B") }
        val launcher = FakeLauncher(listOf(alpha, beta))
        val state = state(settings, launcher = launcher)
        state.load()
        state.openTools()
        state.select(1)                                                   // Beta, after Apps
        launcher.missing = setOf("b/B")                                   // uninstalled since the menu was built
        state.onButton(NxButton.A)
        assertEquals("Could not open Beta", state.notice.value)
        assertEquals(listOf("Apps", "Cheat Database", "Settings"), state.screen.value.toolRows().map { it.label })
    }

    @Test fun go_home_waits_out_first_setup() = runTest {
        val state = state(MemorySettings(null), launcher = FakeLauncher(home = true))
        state.load()
        assertIs<Screen.PickFolder>(state.screen.value)
        state.goHome()
        assertIs<Screen.PickFolder>(state.screen.value)
    }

    private val alpha = InstalledApp("a/A", "Alpha")
    private val beta = InstalledApp("b/B", "Beta")

    /** Tools -> Apps: the app drawer. */
    private fun TestScope.openToolApps(state: AppState) {
        state.openTools()
        state.select(state.screen.value.toolRows().indexOfFirst { it.kind == ToolKind.APPS }); state.onButton(NxButton.A)
        testScheduler.advanceUntilIdle()
    }

    // Dual screen: a tool opened from Tools has the focus on the top screen, the menu staying below on its row; B on the
    // tool gives the focus back. An app tool only launches.
    @Test fun a_tool_takes_the_focus_on_the_top_screen_until_b() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { toolApps = listOf("a/A") }
        val launcher = FakeLauncher(listOf(alpha, beta))
        val state = state(settings, launcher = launcher)
        state.load()
        state.onDualMenu(true)
        state.openTools()
        val settingsAt = state.screen.value.toolRows().indexOfFirst { it.kind == ToolKind.SETTINGS }
        state.select(settingsAt); state.onButton(NxButton.A)
        assertIs<Screen.SettingsMenu>(state.screen.value)
        assertTrue(state.toolOnTop.value)
        val (menu, at) = assertNotNull(state.toolUnder())
        assertEquals(MenuCategory.TOOLS, menu.category)
        assertEquals(settingsAt, at.index)                                    // the menu below stays on Settings
        state.onButton(NxButton.B)
        assertIs<Screen.Systems>(state.screen.value)
        assertFalse(state.toolOnTop.value); assertNull(state.toolUnder())
        state.select(0); state.onButton(NxButton.A)                           // Alpha, an app: launched, no focus moves
        assertEquals(listOf("a/A"), launcher.launched)
        assertFalse(state.toolOnTop.value)
    }

    // Dual screen: the highlighted Apps tile previews the drawer on the top screen; a tap there opens it on that app.
    // An app added meanwhile sorts before Apps: the menu below still shows Apps highlighted, and B lands on it.
    @Test fun the_drawer_previews_on_the_top_screen_and_opens_there() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings, launcher = FakeLauncher(listOf(alpha, beta)))
        state.load()
        state.onDualMenu(true)
        state.openTools()
        state.select(state.screen.value.toolRows().indexOfFirst { it.kind == ToolKind.SETTINGS }); advanceUntilIdle()
        assertNull(state.drawerPreview.value)                                 // Apps is not highlighted
        state.select(state.screen.value.toolRows().indexOfFirst { it.kind == ToolKind.APPS }); advanceUntilIdle()
        assertEquals(listOf("a/A", "b/B"), state.drawerPreview.value?.map { it.id })
        state.openDrawerPreviewApp(1); advanceUntilIdle()
        assertIs<Screen.ToolApps>(state.screen.value)
        assertEquals(1, state.selection.value.index)                          // on Beta, as tapped
        assertTrue(state.toolOnTop.value)
        assertNull(state.drawerPreview.value)
        state.select(0); choose(state, ContextKind.ADD_TO_TOOLS); advanceUntilIdle()   // Alpha into Tools, before Apps
        val (menu, at) = assertNotNull(state.toolUnder())
        assertEquals(ToolKind.APPS, (menu.rows[at.index] as MainRow.Tool).row.kind)
        state.onButton(NxButton.B)
        assertFalse(state.toolOnTop.value)
        assertEquals(ToolKind.APPS, state.screen.value.toolRows()[state.selection.value.index].kind)
    }

    // An app installed after the preview was read: the tap still lands on the app tapped, not on its old index.
    @Test fun a_drawer_preview_tap_opens_on_the_app_tapped() = runTest {
        val launcher = FakeLauncher(listOf(alpha, beta))
        val state = state(MemorySettings(ContentRef("Home")), launcher = launcher)
        state.load()
        state.onDualMenu(true)
        state.openTools()
        state.select(state.screen.value.toolRows().indexOfFirst { it.kind == ToolKind.APPS }); advanceUntilIdle()
        launcher.apps = listOf(InstalledApp("0/0", "Aardvark"), alpha, beta)
        state.openDrawerPreviewApp(1); advanceUntilIdle()                      // Beta in the preview
        val drawer = assertIs<Screen.ToolApps>(state.screen.value)
        assertEquals("b/B", drawer.apps[state.selection.value.index].id)
    }

    @Test fun the_app_drawer_opens_apps_and_adds_them_to_tools() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val launcher = FakeLauncher(listOf(alpha, beta))
        val state = state(settings, launcher = launcher)
        state.load()
        state.openTools()
        assertEquals(listOf("Apps", "Cheat Database", "Settings"), state.screen.value.toolRows().map { it.label })
        openToolApps(state)
        val drawer = assertIs<Screen.ToolApps>(state.screen.value)
        assertEquals(AppPick.TOOLS, drawer.pick)
        assertEquals(listOf(ToolAppRow("a/A", "Alpha", false), ToolAppRow("b/B", "Beta", false)), drawer.apps)
        state.select(1); state.onButton(NxButton.A)                       // A opens Beta
        assertEquals(listOf("b/B"), launcher.launched)
        assertEquals(emptyList(), settings.toolApps)
        choose(state, ContextKind.ADD_TO_TOOLS)                           // Beta first
        assertEquals("Added to Tools", state.notice.value)
        advanceUntilIdle()
        state.select(0); choose(state, ContextKind.ADD_TO_TOOLS); advanceUntilIdle()   // then Alpha
        assertEquals(listOf("b/B", "a/A"), settings.toolApps)             // in the order added
        assertEquals(listOf(true, true), assertIs<Screen.ToolApps>(state.screen.value).apps.map { it.shown })
        assertNull(assertIs<Screen.ToolApps>(state.screen.value).menu)   // the menu closed on the drawer
        state.onButton(NxButton.RIGHT)                                    // a grid: LEFT/RIGHT move
        assertEquals(1, state.selection.value.index)
        choose(state, ContextKind.REMOVE_FROM_TOOLS); advanceUntilIdle()  // Beta out again
        assertEquals(listOf("a/A"), settings.toolApps)
        state.onButton(NxButton.B)                                        // back on the Apps tile
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(state.screen.value).category)
        assertEquals(ToolKind.APPS, state.screen.value.toolRows()[state.selection.value.index].kind)
        assertEquals(listOf("Alpha", "Apps", "Cheat Database", "Settings"), state.screen.value.toolRows().map { it.label })
    }

    // Pin to Home from the drawer pins an app that is not in Tools; it opens from Home and unpins from the drawer again.
    @Test fun the_app_drawer_pins_an_app_on_home_without_tools() = runTest {
        val files = MemoryPrivateFiles()
        val settings = MemorySettings(ContentRef("Home"))
        val launcher = FakeLauncher(listOf(alpha, beta))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = AppState(AppDeps(settings, source, RecordingRunner(), NoopPicker(), FakeUserData(), launcher = launcher,
            privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        state.load(); advanceUntilIdle()
        openToolApps(state)
        state.select(1); choose(state, ContextKind.PIN_TOOL); advanceUntilIdle()
        assertEquals(listOf("APP:b/B"), state.toolPins)
        assertEquals(listOf(ContextKind.ADD_TO_TOOLS, ContextKind.UNPIN_TOOL), run { state.onButton(NxButton.MENU); menuKinds(state) })
        assertEquals("Unpin from Home", assertIs<Screen.ToolApps>(state.screen.value).menu?.items?.get(1)?.label)
        state.onButton(NxButton.B)                                        // closes the menu, not the drawer
        assertIs<Screen.ToolApps>(state.screen.value)
        state.goHome()
        state.selectHome { it is MainRow.PinnedTool }
        assertEquals(ToolRow(ToolKind.APP, "Beta", null, appId = "b/B"), assertIs<MainRow.PinnedTool>(state.screen.value.homeRows[state.selection.value.index]).row)
        state.onButton(NxButton.A)
        assertEquals(listOf("b/B"), launcher.launched)
        assertEquals(emptyList(), settings.toolApps)                      // pinned on Home only
        openToolApps(state)
        state.select(1); choose(state, ContextKind.UNPIN_TOOL); advanceUntilIdle()
        assertEquals(emptyList(), state.toolPins)
    }

    // An app tile in Tools offers Remove from Tools beside Pin; a built-in tool does not.
    @Test fun an_app_tool_can_leave_tools_from_its_own_menu() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { toolApps = listOf("a/A") }
        val state = state(settings, launcher = FakeLauncher(listOf(alpha, beta)))
        state.load()
        state.openTools()
        state.select(2); state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.PIN_TOOL), menuKinds(state))     // Cheat Database: Alpha, Apps, Cheat Database
        state.onButton(NxButton.B)
        state.select(0); choose(state, ContextKind.REMOVE_FROM_TOOLS); advanceUntilIdle()   // Alpha
        assertEquals(emptyList(), settings.toolApps)
        assertEquals(listOf("Apps", "Cheat Database", "Settings"), state.screen.value.toolRows().map { it.label })
    }

    @Test fun no_launcher_no_apps_tool() = runTest {
        val state = state(MemorySettings(ContentRef("Home")))
        state.load()
        state.openTools()
        assertEquals(listOf("Cheat Database", "Settings"), state.screen.value.toolRows().map { it.label })
    }

    @Test fun tool_apps_picker_moves_as_a_grid() = runTest {
        val apps = (0 until 7).map { InstalledApp("p$it/A", "App $it") }
        val launcher = FakeLauncher(apps)
        val state = state(MemorySettings(ContentRef("Home")), launcher = launcher)
        state.load()
        openToolApps(state)
        assertIs<Screen.ToolApps>(state.screen.value)
        state.onToolAppsLayout(3)                                          // rows 0 1 2 / 3 4 5 / 6
        fun press(b: NxButton) { state.onButton(b) }
        press(NxButton.RIGHT); assertEquals(1, state.selection.value.index)
        press(NxButton.DOWN); assertEquals(4, state.selection.value.index)
        press(NxButton.DOWN); assertEquals(6, state.selection.value.index) // the last row is short: its last tile
        press(NxButton.DOWN); assertEquals(0, state.selection.value.index) // wraps to the top, in 6's column
        press(NxButton.UP); assertEquals(6, state.selection.value.index)   // and back up to the bottom
        press(NxButton.RIGHT); assertEquals(6, state.selection.value.index) // the last tile: RIGHT stops
        press(NxButton.LEFT); assertEquals(5, state.selection.value.index)
        press(NxButton.A)
        assertEquals(listOf("p5/A"), launcher.launched)                    // A opens the selected tile's app
        state.select(0); press(NxButton.LEFT); assertEquals(0, state.selection.value.index)   // the first tile: LEFT stops
    }

    // Icons stay cached after the drawer closes, so reopening it loads none again; an app uninstalled meanwhile loses its icon.
    @Test fun the_drawer_keeps_app_icons_until_an_app_is_uninstalled() = runTest {
        val launcher = FakeLauncher(listOf(alpha, beta))
        val state = state(MemorySettings(ContentRef("Home")), launcher = launcher)
        state.load()
        val icons = assertNotNull(state.appIcons)
        openToolApps(state)
        icons.load("a/A"); icons.load("b/B")                              // the drawer's tiles
        state.onButton(NxButton.B)
        openToolApps(state)
        icons.load("a/A"); icons.load("b/B")
        assertEquals(listOf("a/A", "b/B"), launcher.iconAsks)             // reopened: both from the cache
        state.goHome()
        launcher.apps = listOf(beta)                                       // Alpha uninstalled
        openToolApps(state)
        assertNull(icons.cached("a/A")); icons.load("b/B")
        assertEquals(listOf("a/A", "b/B"), launcher.iconAsks)
    }

    // Before the grid lays out (no column count yet) it steps as one column: UP/DOWN and LEFT/RIGHT go one tile.
    @Test fun tool_apps_picker_steps_singly_before_layout() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), launcher = FakeLauncher(listOf(alpha, beta)))
        state.load()
        openToolApps(state)
        state.onButton(NxButton.DOWN); assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.LEFT); assertEquals(0, state.selection.value.index)
    }

    private val gAlpha = InstalledApp("a/A", "Alpha", game = true)
    private val gBeta = InstalledApp("b/B", "Beta", game = false)
    private val gGamma = InstalledApp("g/G", "Gamma", game = true)

    /** Settings on Home with the Android console holding [ids] (in that order) and the onboarding question answered. */
    private fun androidSettings(vararg ids: String) = MemorySettings(ContentRef("Home")).apply { androidConsoleApps = ids.toList(); androidGamesAsked = true }

    @Test fun android_console_members_come_from_the_list_only() {
        val installed = listOf(gGamma, gBeta, gAlpha)
        assertEquals(listOf(gBeta, gGamma), androidConsoleMembers(installed, listOf("g/G", "gone/X", "b/B")))   // label order; an uninstalled member skipped
        assertEquals(emptyList(), androidConsoleMembers(installed, emptyList()))                                // a detected game never joins by itself
        assertEquals(listOf(InstalledApp("a/A", "alpha", true), InstalledApp("b/B", "Beta", true)),
            androidConsoleMembers(listOf(InstalledApp("b/B", "Beta", true), InstalledApp("a/A", "alpha", true)), listOf("b/B", "a/A")))   // case-insensitive
    }

    @Test fun android_games_pick_order_lists_detected_games_first() {
        val other = InstalledApp("c/C", "aardvark")
        assertEquals(listOf(gAlpha, gGamma, other, gBeta), androidGamesPickOrder(listOf(gGamma, gBeta, other, gAlpha)))
    }

    @Test fun games_count_label_counts() {
        assertEquals("None", gamesCountLabel(0)); assertEquals("1 game", gamesCountLabel(1)); assertEquals("3 games", gamesCountLabel(3))
    }

    @Test fun library_lists_android_games_right_after_saves() = runTest {
        val state = state(androidSettings("b/B"), launcher = FakeLauncher(listOf(gAlpha, gBeta, gGamma)))
        state.load()
        state.openSettings(SettingsSection.LIBRARY)
        testScheduler.advanceUntilIdle()
        val page = assertIs<Screen.Settings>(state.screen.value)
        val at = rowIndex(state, SettingsKind.ANDROID_GAMES)
        assertEquals(SettingsKind.SAVES, page.rows[at - 1].kind)                             // after Saves, before the other game lists
        assertEquals(SettingsRow(SettingsKind.ANDROID_GAMES, "Android games", "1 game"), page.rows[at])
    }

    @Test fun android_games_count_waits_for_the_first_load() = runTest {
        val gate = CompletableDeferred<Unit>()
        val launcher = object : AppLauncher by FakeLauncher(listOf(gAlpha)) {
            override suspend fun installedApps(): List<InstalledApp> { gate.await(); return listOf(gAlpha) }
        }
        val state = state(androidSettings("a/A"), launcher = launcher)
        state.load()
        state.openSettings(SettingsSection.LIBRARY)
        assertEquals("…", libraryRow(state, SettingsKind.ANDROID_GAMES).secondary)
        gate.complete(Unit); testScheduler.advanceUntilIdle()
        assertEquals("1 game", libraryRow(state, SettingsKind.ANDROID_GAMES).secondary)
    }

    @Test fun android_games_picker_adds_and_removes_console_apps() = runTest {
        val settings = androidSettings("b/B")
        val state = state(settings, launcher = FakeLauncher(listOf(gAlpha, gBeta, gGamma)))
        state.load()
        state.openSettings(SettingsSection.LIBRARY)
        state.select(rowIndex(state, SettingsKind.ANDROID_GAMES)); state.onButton(NxButton.A)
        testScheduler.advanceUntilIdle()
        val picker = assertIs<Screen.ToolApps>(state.screen.value)
        assertEquals(AppPick.GAMES, picker.pick)
        // Detected games first (Alpha, Gamma), then the rest; checked = in the console, detected or not.
        assertEquals(listOf(ToolAppRow("a/A", "Alpha", false), ToolAppRow("g/G", "Gamma", false), ToolAppRow("b/B", "Beta", true)), picker.apps)
        fun shown() = assertIs<Screen.ToolApps>(state.screen.value).apps.map { it.shown }
        state.select(0); state.onButton(NxButton.A)                       // Alpha in: appended
        assertEquals(listOf("b/B", "a/A"), settings.androidConsoleApps)
        assertEquals(listOf(true, false, true), shown())
        state.select(2); state.onButton(NxButton.A)                       // Beta out
        assertEquals(listOf("a/A"), settings.androidConsoleApps)
        assertEquals(listOf(true, false, false), shown())
        state.onButton(NxButton.A)                                        // Beta in again: last
        assertEquals(listOf("a/A", "b/B"), settings.androidConsoleApps)
        assertEquals(emptyList(), settings.toolApps)                      // the Tools list is untouched
        state.onButton(NxButton.B)
        val page = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.LIBRARY, page.section)                // B: back on Library's Android games row
        assertEquals(SettingsKind.ANDROID_GAMES, page.rows[state.selection.value.index].kind)
        assertEquals("2 games", libraryRow(state, SettingsKind.ANDROID_GAMES).secondary)
    }

    @Test fun tool_apps_picker_is_the_tools_pick() = runTest {
        val state = state(androidSettings(), launcher = FakeLauncher(listOf(gAlpha, gBeta)))
        state.load()
        openToolApps(state)
        assertEquals(AppPick.TOOLS, assertIs<Screen.ToolApps>(state.screen.value).pick)
        assertEquals("Apps", toolAppsTitle(AppPick.TOOLS)); assertEquals("Android games", toolAppsTitle(AppPick.GAMES))
        assertEquals(listOf(false, false), assertIs<Screen.ToolApps>(state.screen.value).apps.map { it.shown })   // detected games are not Tools apps
    }

    // Added Beta first, then Alpha: they sort by name among the tools, Settings last.
    @Test fun tool_apps_sort_among_the_tools_by_name_and_launch() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { toolApps = listOf("b/B", "a/A") }
        val launcher = FakeLauncher(listOf(alpha, beta))
        val state = state(settings, launcher = launcher)
        state.load()
        state.openTools()
        val tools = state.screen.value.toolRows()
        assertEquals(listOf("Alpha", "Apps", "Beta", "Cheat Database", "Settings"), tools.map { it.label })
        assertEquals(ToolRow(ToolKind.APP, "Beta", null, appId = "b/B"), tools[2])
        state.select(2); state.onButton(NxButton.A)
        assertEquals(listOf("b/B"), launcher.launched)
    }

    @Test fun uninstalled_tool_app_is_skipped_but_kept() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { toolApps = listOf("a/A", "b/B") }
        val state = state(settings, launcher = FakeLauncher(listOf(alpha, beta)).apply { missing = setOf("a/A") })
        state.load()
        state.openTools()
        assertEquals(listOf("Apps", "Beta", "Cheat Database", "Settings"), state.screen.value.toolRows().map { it.label })
        assertEquals(listOf("a/A", "b/B"), settings.toolApps)
    }

    @Test fun an_app_pins_on_home_by_its_id() = runTest {
        val files = MemoryPrivateFiles()
        val settings = MemorySettings(ContentRef("Home")).apply { toolApps = listOf("a/A", "b/B") }
        val launcher = FakeLauncher(listOf(alpha, beta))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = AppState(AppDeps(settings, source, RecordingRunner(), NoopPicker(), FakeUserData(), launcher = launcher,
            privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        state.load(); advanceUntilIdle()
        state.openTools()
        state.select(2)                                                    // Beta: Alpha, Apps, Beta, Cheat Database, Settings
        choose(state, ContextKind.PIN_TOOL); advanceUntilIdle()
        assertEquals(listOf("APP:b/B"), state.toolPins)
        assertEquals("APP:b/B", files.files[ToolPins.FILE])
        state.goHome()
        state.selectHome { it is MainRow.PinnedTool }
        assertEquals(ToolRow(ToolKind.APP, "Beta", null, appId = "b/B"), assertIs<MainRow.PinnedTool>(state.screen.value.homeRows[state.selection.value.index]).row)
        state.onButton(NxButton.A)
        assertEquals(listOf("b/B"), launcher.launched)
        launcher.missing = setOf("b/B")                                   // uninstalled: the pin hides, the file keeps it
        state.onForeground(); advanceUntilIdle()
        assertTrue(state.screen.value.homeRows.none { it is MainRow.PinnedTool })
        assertEquals("APP:b/B", files.files[ToolPins.FILE])
    }

    // --- Consoles -> Android ---

    private val androidSource = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Nintendo DS (NDS)", "Game Boy (GB)"),
            "Home/Roms/Nintendo DS (NDS)" to listOf("Mario Kart DS.nds"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
        ),
    )

    /** The Consoles tab's display names, from whichever tab the main menu shows. */
    private fun consoleNames(state: AppState): List<String> =
        assertIs<Screen.Systems>(state.screen.value).tabs.firstOrNull { it.category == MenuCategory.CONSOLES }?.rows.orEmpty()
            .map { (it as MainRow.System).system.displayName }

    /** The Android console row's system on the main menu, or null. */
    private fun androidRow(state: AppState): SystemFolder? =
        assertIs<Screen.Systems>(state.screen.value).tabs.flatMap { it.rows }.filterIsInstance<MainRow.System>().map { it.system }.firstOrNull { it.android }

    @Test fun android_console_appears_among_consoles_by_name() = runTest {
        val state = state(androidSettings("g/G", "b/B", "a/A"), src = androidSource, launcher = FakeLauncher(listOf(gGamma, gBeta, gAlpha)))
        state.load(); advanceUntilIdle()
        assertEquals(listOf("Android", "Game Boy", "Nintendo DS"), consoleNames(state))
        val android = assertNotNull(androidRow(state))
        assertTrue(android.android)
        assertEquals(ANDROID_TAG, android.tag)
        assertNull(android.core)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), android.games.map { it.shownName })
        assertEquals(listOf("a/A", "b/B", "g/G"), android.games.map { it.fileName })
        assertTrue(android.games.all { it.boxartRef == null && it.screenshotRef == null })   // no art store: no art

        // Detected but never added: no console (an empty list means no Android row).
        val unadded = state(androidSettings(), src = androidSource, launcher = FakeLauncher(listOf(gAlpha, gGamma)))
        unadded.load(); advanceUntilIdle()
        assertEquals(listOf("Game Boy", "Nintendo DS"), consoleNames(unadded))

        val gone = state(androidSettings("x/X"), src = androidSource, launcher = FakeLauncher(listOf(gBeta)))
        gone.load(); advanceUntilIdle()
        assertEquals(listOf("Game Boy", "Nintendo DS"), consoleNames(gone))
    }

    @Test fun android_console_takes_screenshots_from_the_art_store() = runTest {
        val art = object : AndroidArtStore {
            override fun screenshot(appId: String): ContentRef? = if (appId == "a/A") ContentRef("file:///art/a.png") else null
            override suspend fun writeScreenshot(appId: String, png: ByteArray): ContentRef? = null
            override suspend fun deleteAll(): Int = 0
        }
        val state = AppState(
            AppDeps(androidSettings("a/A", "g/G"), androidSource, RecordingRunner(), NoopPicker(), launcher = FakeLauncher(listOf(gAlpha, gGamma)),
                compute = UnconfinedTestDispatcher(testScheduler), androidArtStore = art),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load(); advanceUntilIdle()
        val games = assertNotNull(androidRow(state)).games
        assertEquals(listOf(ContentRef("file:///art/a.png"), null), games.map { it.screenshotRef })
        assertEquals(listOf(null, null), games.map { it.boxartRef })
    }

    @Test fun a_replaced_screenshot_with_a_new_ref_rebuilds_the_console_row() = runTest {
        var ref = ContentRef("file:///art/a.png?v=1")
        val art = object : AndroidArtStore {
            override fun screenshot(appId: String): ContentRef? = ref.takeIf { appId == "a/A" }
            override suspend fun writeScreenshot(appId: String, png: ByteArray): ContentRef? = null
            override suspend fun deleteAll(): Int = 0
        }
        val state = AppState(
            AppDeps(androidSettings("a/A"), androidSource, RecordingRunner(), NoopPicker(), launcher = FakeLauncher(listOf(gAlpha)),
                compute = UnconfinedTestDispatcher(testScheduler), androidArtStore = art),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load(); advanceUntilIdle()
        assertEquals(ref, assertNotNull(androidRow(state)).games.single().screenshotRef)
        ref = ContentRef("file:///art/a.png?v=2")                              // the same file, rewritten (AndroidArtFiles)
        state.onForeground(); advanceUntilIdle()
        assertEquals(ContentRef("file:///art/a.png?v=2"), assertNotNull(androidRow(state)).games.single().screenshotRef)
    }

    @Test fun android_console_follows_the_settings_and_installs() = runTest {
        val settings = androidSettings("a/A", "g/G")
        val launcher = FakeLauncher(listOf(gAlpha, gBeta, gGamma))
        val state = state(settings, src = androidSource, launcher = launcher)
        state.load(); advanceUntilIdle()
        assertEquals(listOf("Alpha", "Gamma"), androidRow(state)?.games?.map { it.shownName })
        state.openSettings(SettingsSection.LIBRARY); advanceUntilIdle()
        state.select(rowIndex(state, SettingsKind.ANDROID_GAMES)); state.onButton(NxButton.A); advanceUntilIdle()        // the games picker: Alpha, Gamma, Beta
        state.select(2); state.onButton(NxButton.A)                            // Beta added
        state.goHome()
        assertEquals(listOf("Alpha", "Beta", "Gamma"), androidRow(state)?.games?.map { it.shownName })
        state.openSettings(SettingsSection.LIBRARY); advanceUntilIdle()
        state.select(rowIndex(state, SettingsKind.ANDROID_GAMES)); state.onButton(NxButton.A); advanceUntilIdle()
        repeat(3) { state.select(it); state.onButton(NxButton.A) }             // all three out
        assertEquals(emptyList(), settings.androidConsoleApps)
        state.goHome()
        assertNull(androidRow(state))                                          // empty: hidden
        settings.androidConsoleApps = listOf("a/A", "b/B", "g/G")
        launcher.apps = listOf(gAlpha)                                         // two apps uninstalled meanwhile
        state.onForeground(); advanceUntilIdle()
        assertEquals(listOf("Alpha"), androidRow(state)?.games?.map { it.shownName })
        assertEquals(listOf("a/A", "b/B", "g/G"), settings.androidConsoleApps)   // skipped, kept
    }

    // Leaving the games picker keeps every installed app's icon, a console game's or not: none loads again.
    @Test fun leaving_the_picker_keeps_the_console_games_icons() = runTest {
        val launcher = FakeLauncher(listOf(gAlpha, gBeta, gGamma))
        val state = state(androidSettings("a/A"), src = androidSource, launcher = launcher)
        state.load(); advanceUntilIdle()
        val icons = assertNotNull(state.appIcons)
        icons.load("a/A"); icons.load("b/B")
        state.openSettings(SettingsSection.LIBRARY); advanceUntilIdle()
        state.select(rowIndex(state, SettingsKind.ANDROID_GAMES)); state.onButton(NxButton.A); advanceUntilIdle()
        state.onButton(NxButton.B)
        icons.load("a/A"); icons.load("b/B")
        assertEquals(1, launcher.iconAsks.count { it == "a/A" })   // a console game's icon is kept
        assertEquals(1, launcher.iconAsks.count { it == "b/B" })   // and so is an app in neither list
    }

    @Test fun opening_an_android_game_launches_it_and_records_nothing() = runTest {
        val runner = RecordingRunner()
        val data = FakeUserData(recents = listOf(Recent(tetris)))
        val launcher = FakeLauncher(listOf(gAlpha, gGamma))
        val state = state(androidSettings("a/A", "g/G"), src = androidSource, runner = runner, data = data, launcher = launcher)
        state.load(); advanceUntilIdle()
        state.onConsoles()
        state.onButton(NxButton.A)                                             // the Android row: first by name
        val games = assertIs<Screen.Games>(state.screen.value)
        assertTrue(games.system.android)
        assertEquals(listOf("Alpha", "Gamma"), games.entries.map { it.shown })
        state.onButton(NxButton.A)
        assertEquals(listOf("a/A"), launcher.launched)
        assertTrue(runner.launched.isEmpty())
        assertEquals(listOf(Recent(tetris)), data.recents)
        assertNull(state.error.value)
        launcher.missing = setOf("a/A")
        state.onButton(NxButton.A)
        assertEquals("Could not open Alpha", state.notice.value)
        assertNull(state.error.value)
        assertTrue(runner.launched.isEmpty())
        assertEquals(listOf(Recent(tetris)), data.recents)
        assertTrue(state.back())
        assertEquals("Android", assertIs<Screen.Systems>(state.screen.value).let { (it.rows[state.selection.value.index] as MainRow.System).system.displayName })
    }

    @Test fun android_game_menu_is_pin_or_unpin_and_pins_on_home() = runTest {
        val settings = androidSettings("a/A", "g/G")
        val runner = RecordingRunner()
        val mk = "/Roms/Nintendo DS (NDS)/Mario Kart DS.nds"
        val data = FakeUserData(recents = listOf(Recent(tetris)), pins = listOf(Shortcut(mk)))
        val launcher = FakeLauncher(listOf(gAlpha, gGamma))
        val state = state(settings, src = androidSource, runner = runner, data = data, launcher = launcher)
        state.load(); advanceUntilIdle()
        state.openSystem(assertNotNull(androidRow(state)))
        state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.PIN), menuKinds(state))
        state.onButton(NxButton.A)                                             // no idle wait: the notice clears itself
        assertEquals(listOf("a/A"), settings.androidPins)
        assertEquals(listOf(Shortcut(mk)), data.pins)                     // shortcuts.txt untouched
        assertEquals("Pinned", state.notice.value)
        assertNull(assertIs<Screen.Games>(state.screen.value).menu)
        state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.UNPIN), menuKinds(state))
        state.onButton(NxButton.B)

        state.goHome()
        val pins = state.screen.value.homeRows.filterIsInstance<MainRow.Pin>()
        assertEquals(listOf("Mario Kart DS.nds", "a/A"), pins.map { it.entry.game.fileName })   // after the ROM pins
        assertTrue(pins[1].entry.system.android)
        state.selectHome { it is MainRow.Pin && it.entry.system.android }
        assertEquals(ResumeInfo.NONE, state.resumeInfo.value)
        state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals(listOf("a/A"), launcher.launched)                         // opened through the app launcher
        assertTrue(runner.launched.isEmpty())
        assertEquals(listOf(Recent(tetris)), data.recents)                     // no recent
        state.onButton(NxButton.MENU)
        assertEquals(listOf(ContextKind.UNPIN), menuKinds(state))
        state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals(emptyList(), settings.androidPins)
        assertEquals(listOf(Shortcut(mk)), data.pins)
        assertEquals(listOf("Mario Kart DS.nds"), state.screen.value.homeRows.filterIsInstance<MainRow.Pin>().map { it.entry.game.fileName })
    }

    @Test fun an_android_pin_out_of_the_console_is_skipped_but_kept() = runTest {
        val settings = androidSettings("a/A").apply { androidPins = listOf("g/G", "a/A") }
        val state = state(settings, src = androidSource, launcher = FakeLauncher(listOf(gAlpha, gGamma)))
        state.load(); advanceUntilIdle()
        assertEquals(listOf("a/A"), state.screen.value.homeRows.filterIsInstance<MainRow.Pin>().map { it.entry.game.fileName })
        assertEquals(listOf("g/G", "a/A"), settings.androidPins)
    }

    @Test fun the_pin_cap_counts_rom_and_android_pins() = runTest {
        val settings = androidSettings("a/A", "g/G").apply { androidPins = listOf("g/G") }
        val data = FakeUserData(pins = List(Shortcuts.MAX - 1) { Shortcut("/Roms/X (X)/g$it.x") })
        val state = state(settings, src = androidSource, data = data, launcher = FakeLauncher(listOf(gAlpha, gGamma)))
        state.load(); advanceUntilIdle()
        state.openSystem(assertNotNull(androidRow(state)))
        choose(state, ContextKind.PIN)                                         // Alpha: 11 + 1 pins already
        assertEquals("Pins are full (${Shortcuts.MAX}). Unpin one first.", state.notice.value)
        assertEquals(listOf("g/G"), settings.androidPins)
        state.back()
        state.openSystem(androidSystemsOf(state).first { it.tag == "GB" })
        choose(state, ContextKind.PIN)                                         // Tetris: a ROM pin, the same cap
        assertEquals("Pins are full (${Shortcuts.MAX}). Unpin one first.", state.notice.value)
        assertEquals(Shortcuts.MAX - 1, data.pins.size)
    }

    /** The main menu's consoles (every tab). */
    private fun androidSystemsOf(state: AppState): List<SystemFolder> = assertIs<Screen.Systems>(state.screen.value).systems

    @Test fun x_and_switcher_ignore_android_games() = runTest {
        val runner = RecordingRunner()
        val androidPath = GamePath.of(GameRef("android", "", "a/A"))
        val data = FakeUserData(
            recents = listOf(Recent(androidPath), Recent(tetris)),
            resumable = mapOf((ANDROID_TAG to "a/A") to ResumeInfo(3), ("GB" to "Tetris.gb") to ResumeInfo(9)),
        )
        val launcher = FakeLauncher(listOf(gAlpha, gGamma))
        val state = state(androidSettings("a/A", "g/G"), src = androidSource, runner = runner, data = data, launcher = launcher)
        state.load(); advanceUntilIdle()
        state.openSystem(assertNotNull(androidRow(state)))
        assertEquals(ResumeInfo.NONE, state.resumeInfo.value)                 // no X Resume hint
        state.onButton(NxButton.X); advanceUntilIdle()
        assertTrue(launcher.launched.isEmpty())
        assertTrue(runner.launched.isEmpty())
        assertNull(state.error.value)
        state.onButton(NxButton.SELECT); advanceUntilIdle()
        val sw = assertIs<Screen.Switcher>(state.screen.value)
        assertEquals(listOf("Tetris.gb"), sw.entries.map { it.game.fileName })
        assertTrue(sw.entries.none { it.system.android })
    }

    @Test fun android_console_rows_stay_out_of_library_counts() = runTest {
        fun TestScope.counts(launcher: AppLauncher?): String {
            val state = state(androidSettings("a/A", "g/G"), src = androidSource, launcher = launcher)
            state.load(); testScheduler.advanceUntilIdle()
            state.openSettings(SettingsSection.LIBRARY)
            return assertIs<Screen.Settings>(state.screen.value).rows[0].label
        }
        val with = counts(FakeLauncher(listOf(gAlpha, gGamma)))
        assertEquals(counts(null), with)
        assertEquals("2 games · 2 consoles", with)
    }

    // --- Android games onboarding ---

    @Test fun android_games_ask_text() {
        val ask = AndroidGamesAsk(listOf("a/A", "g/G"), listOf("Alpha", "Gamma"))
        assertEquals("Android games", ask.title)
        assertEquals("Add 2 Android games to Consoles?\n\nAlpha\nGamma", ask.text)
        assertEquals("Add 1 Android game to Consoles?\n\nAlpha", AndroidGamesAsk(listOf("a/A"), listOf("Alpha")).text)
    }

    @Test fun a_long_android_games_ask_lists_the_first_games_and_how_many_more() {
        fun ask(n: Int) = AndroidGamesAsk((1..n).map { "g$it/G" }, (1..n).map { "Game $it" })
        assertEquals("Add 6 Android games to Consoles?\n\n" + (1..6).joinToString("\n") { "Game $it" }, ask(6).text)   // all fit
        assertEquals("Add 7 Android games to Consoles?\n\n" + (1..5).joinToString("\n") { "Game $it" } + "\nand 2 more", ask(7).text)
        assertEquals("Add 40 Android games to Consoles?\n\n" + (1..5).joinToString("\n") { "Game $it" } + "\nand 35 more", ask(40).text)
    }

    // First setup: B on the Android games step goes back to the ROMs-folder offer (its hint says Back), not "Not now".
    @Test fun first_setup_b_on_the_android_games_step_goes_back() = runTest {
        val settings = freshInstall()
        val state = state(settings, src = messyHome(), launcher = FakeLauncher(listOf(gAlpha)))
        state.load(); state.getStarted()
        state.setHome(ContentRef("M")); advanceUntilIdle()
        state.select(0); state.onButton(NxButton.A)                         // Move off
        state.select(state.selection.value.count - 1); state.onButton(NxButton.A); advanceUntilIdle()
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        state.select(1); state.onButton(NxButton.A); advanceUntilIdle()      // Skip
        assertIs<Screen.AndroidGamesStep>(state.screen.value)
        state.onButton(NxButton.B); advanceUntilIdle()
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        assertFalse(settings.androidGamesAsked)
    }

    // A restart during first setup's folder steps comes back to them (the home page, then the ROMs-folder offer), not
    // straight on to the onboarding steps.
    @Test fun a_restart_during_the_folder_steps_resumes_them() = runTest {
        val settings = freshInstall()
        val src = messyHome()
        val first = state(settings, src = src)
        first.load(); first.onButton(NxButton.A)
        first.setHome(ContentRef("M")); advanceUntilIdle()
        assertIs<Screen.FolderSummary>(first.screen.value)
        val again = state(settings, src = src)                             // the app killed and opened again
        again.load(); advanceUntilIdle()
        assertEquals(Recents.HOME, assertIs<Screen.FolderSummary>(again.screen.value).extraId)
        again.select(0); again.onButton(NxButton.A)                         // Move off
        again.select(again.selection.value.count - 1); again.onButton(NxButton.A); advanceUntilIdle()
        assertIs<Screen.AddFirstExtra>(again.screen.value)
    }

    // Landscape lists more of them (three columns): the cap is the caller's, the last line still says how many more.
    @Test fun an_android_games_ask_lists_up_to_a_given_cap() {
        fun ask(n: Int) = AndroidGamesAsk((1..n).map { "g$it/G" }, (1..n).map { "Game $it" })
        assertEquals((1..12).map { "Game $it" }, ask(12).shown(12))
        assertEquals((1..11).map { "Game $it" } + "and 29 more", ask(40).shown(12))
        assertEquals("Add 40 Android games to Consoles?", ask(40).question)
    }

    @Test fun first_setup_asks_about_detected_games_after_the_folder_steps_and_yes_adds_them() = runTest {
        val settings = MemorySettings()
        val state = state(settings, launcher = FakeLauncher(listOf(gGamma, gBeta, gAlpha)))
        state.load()
        state.setHomePast(ContentRef("Home"))
        assertIs<Screen.AddFirstExtra>(state.screen.value)
        assertNull(state.androidGamesAsk.value)
        state.onButton(NxButton.B); advanceUntilIdle()                        // Skip
        val step = assertIs<Screen.AndroidGamesStep>(state.screen.value)
        assertEquals(AndroidGamesAsk(listOf("a/A", "g/G"), listOf("Alpha", "Gamma")), step.ask)
        assertFalse(settings.androidGamesAsked)
        state.onButton(NxButton.A); advanceUntilIdle()                        // Yes
        assertEquals(listOf("a/A", "g/G"), settings.androidConsoleApps)
        assertTrue(settings.androidGamesAsked)
        assertIs<Screen.Systems>(state.screen.value)
        assertNotNull(androidRow(state))
        assertNull(state.androidGamesAsk.value)                                // not asked again as a dialog
        state.onForeground(); advanceUntilIdle()
        assertNull(state.androidGamesAsk.value)
    }

    @Test fun first_setup_not_now_adds_nothing_and_is_not_asked_again() = runTest {
        val settings = MemorySettings()
        val state = state(settings, launcher = FakeLauncher(listOf(gAlpha)))
        state.load()
        state.setHomePast(ContentRef("Home"))
        state.onButton(NxButton.B); advanceUntilIdle()
        assertIs<Screen.AndroidGamesStep>(state.screen.value)
        state.onButton(NxButton.B); advanceUntilIdle()                        // Not now
        assertEquals(emptyList(), settings.androidConsoleApps)
        assertTrue(settings.androidGamesAsked)
        assertIs<Screen.Systems>(state.screen.value)
        assertNull(androidRow(state))
        assertNull(state.androidGamesAsk.value)
        state.load(); advanceUntilIdle()
        assertNull(state.androidGamesAsk.value)
    }

    // The step's rows take the d-pad: DOWN to Not now, then A chooses it.
    @Test fun first_setup_android_games_step_moves_with_the_d_pad() = runTest {
        val settings = MemorySettings()
        val state = state(settings, launcher = FakeLauncher(listOf(gAlpha)))
        state.load()
        state.setHomePast(ContentRef("Home"))
        state.onButton(NxButton.B); advanceUntilIdle()
        assertIs<Screen.AndroidGamesStep>(state.screen.value)
        state.onButton(NxButton.DOWN)
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.UP); state.onButton(NxButton.UP)              // round to Not now again
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.A); advanceUntilIdle()                        // Not now
        assertEquals(emptyList(), settings.androidConsoleApps)
        assertIs<Screen.Systems>(state.screen.value)
    }

    @Test fun first_setup_asks_after_an_added_extra_too() = runTest {
        val settings = MemorySettings()
        val state = state(settings, launcher = FakeLauncher(listOf(gAlpha)))
        state.load()
        state.setHomePast(ContentRef("Home"))
        state.addExtra(ContentRef("sd/Loose")); advanceUntilIdle()
        state.select(state.selection.value.count - 1); state.onButton(NxButton.A); advanceUntilIdle()                     // the summary's Continue
        assertIs<Screen.AndroidGamesStep>(state.screen.value)
        state.onButton(NxButton.A); advanceUntilIdle()
        val systems = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(2, systems.systems.first { it.tag == "GBA" }.gameCount)   // the extra was scanned
        assertEquals(listOf("a/A"), settings.androidConsoleApps)
        assertNotNull(androidRow(state))
    }

    @Test fun no_question_without_a_detected_game_or_a_launcher() = runTest {
        val noGames = MemorySettings()
        val a = state(noGames, launcher = FakeLauncher(listOf(gBeta)))
        a.load(); a.setHomePast(ContentRef("Home"))
        a.onButton(NxButton.B); advanceUntilIdle()
        assertIs<Screen.Systems>(a.screen.value)
        assertNull(a.androidGamesAsk.value)
        assertFalse(noGames.androidGamesAsked)                                // stays unasked until a game is detected

        val noLauncher = MemorySettings()
        val b = state(noLauncher)
        b.load(); b.setHomePast(ContentRef("Home"))
        b.onButton(NxButton.B); advanceUntilIdle()
        assertIs<Screen.Systems>(b.screen.value)
        assertNull(b.androidGamesAsk.value)
        assertFalse(noLauncher.androidGamesAsked)
    }

    @Test fun an_existing_install_is_asked_once_over_the_menu() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val launcher = FakeLauncher(listOf(gBeta))
        val state = state(settings, src = androidSource, launcher = launcher)
        state.load(); advanceUntilIdle()
        assertNull(state.androidGamesAsk.value)                                // nothing detected: nothing asked
        assertFalse(settings.androidGamesAsked)
        launcher.apps = listOf(gGamma, gBeta, gAlpha)                         // games installed since
        state.onForeground(); advanceUntilIdle()
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(AndroidGamesAsk(listOf("a/A", "g/G"), listOf("Alpha", "Gamma")), state.androidGamesAsk.value)
        state.onButton(NxButton.DOWN)                                          // the dialog takes the buttons
        assertEquals(AndroidGamesAsk(listOf("a/A", "g/G"), listOf("Alpha", "Gamma")), state.androidGamesAsk.value)
        state.onButton(NxButton.A); advanceUntilIdle()                        // Yes
        assertNull(state.androidGamesAsk.value)
        assertTrue(settings.androidGamesAsked)
        assertEquals(listOf("a/A", "g/G"), settings.androidConsoleApps)
        assertEquals(listOf("Alpha", "Gamma"), androidRow(state)?.games?.map { it.shownName })
        state.onForeground(); advanceUntilIdle()
        assertNull(state.androidGamesAsk.value)
    }

    @Test fun an_existing_install_not_now_adds_nothing() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val state = state(settings, src = androidSource, launcher = FakeLauncher(listOf(gAlpha)))
        state.load(); advanceUntilIdle()
        assertNotNull(state.androidGamesAsk.value)
        state.onButton(NxButton.B); advanceUntilIdle()                        // Not now
        assertNull(state.androidGamesAsk.value)
        assertTrue(settings.androidGamesAsked)
        assertEquals(emptyList(), settings.androidConsoleApps)
        assertNull(androidRow(state))
        state.load(); advanceUntilIdle()
        assertNull(state.androidGamesAsk.value)
    }

    @Test fun the_games_question_ignores_a_and_b_just_after_it_shows() = runTest {
        val settings = MemorySettings(ContentRef("Home"))
        val launcher = FakeLauncher(listOf(gBeta))
        val state = state(settings, src = androidSource, launcher = launcher)
        state.load(); advanceUntilIdle()
        launcher.apps = listOf(gAlpha, gBeta)
        state.onForeground(); runCurrent()                                     // the dialog shows now
        val ask = assertNotNull(state.androidGamesAsk.value)
        state.onButton(NxButton.A); state.onButton(NxButton.B)                // a stray press meant for the menu
        assertTrue(state.back())                                               // B taken, not answered
        state.answerAndroidGames(true); state.answerAndroidGames(false)        // a tap on its buttons
        assertEquals(ask, state.androidGamesAsk.value)
        assertFalse(settings.androidGamesAsked)
        advanceTimeBy(ANDROID_ASK_GUARD_MILLIS - 1); runCurrent()
        state.onButton(NxButton.B)
        assertEquals(ask, state.androidGamesAsk.value)                         // still within the guard
        advanceTimeBy(2); runCurrent()
        state.onButton(NxButton.A); advanceUntilIdle()                        // past it: Yes
        assertNull(state.androidGamesAsk.value)
        assertEquals(listOf("a/A"), settings.androidConsoleApps)
    }

    @Test fun the_first_setup_step_is_not_guarded() = runTest {
        val settings = MemorySettings()
        val state = state(settings, launcher = FakeLauncher(listOf(gAlpha)))
        state.load()
        state.setHomePast(ContentRef("Home"))
        state.onButton(NxButton.B); runCurrent()
        assertIs<Screen.AndroidGamesStep>(state.screen.value)
        state.onButton(NxButton.A); advanceUntilIdle()                        // its own page: no stray press to catch
        assertEquals(listOf("a/A"), settings.androidConsoleApps)
    }

    // --- An app joining the console keeps its Home pin ---

    private fun TestScope.pinState(settings: MemorySettings, files: MemoryPrivateFiles, launcher: FakeLauncher) =
        AppState(AppDeps(settings, androidSource, RecordingRunner(), NoopPicker(), FakeUserData(), launcher = launcher,
            privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test fun joining_by_the_picker_moves_its_tools_pin_to_an_android_pin() = runTest {
        val files = MemoryPrivateFiles().apply { files[ToolPins.FILE] = "CHEAT_DB\nAPP:a/A\nAPP:b/B" }
        val settings = androidSettings().apply { toolApps = listOf("a/A", "b/B"); androidPins = listOf("g/G") }
        val state = pinState(settings, files, FakeLauncher(listOf(gAlpha, gBeta, gGamma)))
        state.load(); advanceUntilIdle()
        assertEquals(listOf("a/A", "b/B"), state.screen.value.homeRows.filterIsInstance<MainRow.PinnedTool>().mapNotNull { it.row.appId })
        state.openSettings(SettingsSection.LIBRARY)
        state.select(rowIndex(state, SettingsKind.ANDROID_GAMES)); state.onButton(NxButton.A); advanceUntilIdle()      // the games picker: Alpha, Gamma, Beta
        state.select(0); state.onButton(NxButton.A); advanceUntilIdle()      // Alpha in
        assertEquals(listOf("g/G", "a/A"), settings.androidPins)              // appended
        assertEquals("CHEAT_DB\nAPP:b/B", files.files[ToolPins.FILE])
        assertEquals(listOf("CHEAT_DB", "APP:b/B"), state.toolPins)
        state.goHome()
        assertEquals(listOf("a/A"), state.screen.value.homeRows.filterIsInstance<MainRow.Pin>().map { it.entry.game.fileName })   // g/G not in the console
        assertEquals(listOf("b/B"), state.screen.value.homeRows.filterIsInstance<MainRow.PinnedTool>().mapNotNull { it.row.appId })

        state.openSettings(SettingsSection.LIBRARY)
        state.select(rowIndex(state, SettingsKind.ANDROID_GAMES)); state.onButton(NxButton.A); advanceUntilIdle()
        state.select(0); state.onButton(NxButton.A); advanceUntilIdle()      // Alpha out: the pin stays an Android pin
        assertEquals(listOf("g/G", "a/A"), settings.androidPins)
        assertEquals("CHEAT_DB\nAPP:b/B", files.files[ToolPins.FILE])
        state.onButton(NxButton.A); advanceUntilIdle()                        // in again: not doubled
        assertEquals(listOf("g/G", "a/A"), settings.androidPins)
    }

    @Test fun joining_by_the_question_moves_tools_pins_and_not_now_keeps_them() = runTest {
        val files = MemoryPrivateFiles().apply { files[ToolPins.FILE] = "APP:g/G\nAPP:b/B\nAPP:a/A" }
        val settings = MemorySettings(ContentRef("Home")).apply { toolApps = listOf("a/A", "b/B", "g/G"); androidPins = listOf("a/A") }
        val state = pinState(settings, files, FakeLauncher(listOf(gAlpha, gBeta, gGamma)))
        state.load(); advanceUntilIdle()
        assertNotNull(state.androidGamesAsk.value)
        state.onButton(NxButton.A); advanceUntilIdle()                        // Yes: Alpha and Gamma
        assertEquals(listOf("a/A", "g/G"), settings.androidPins)              // a/A already there: not doubled
        assertEquals("APP:b/B", files.files[ToolPins.FILE])
        state.goHome()
        assertEquals(listOf("a/A", "g/G"), state.screen.value.homeRows.filterIsInstance<MainRow.Pin>().map { it.entry.game.fileName })

        val keepFiles = MemoryPrivateFiles().apply { this.files[ToolPins.FILE] = "APP:a/A" }
        val keep = MemorySettings(ContentRef("Home")).apply { toolApps = listOf("a/A") }
        val other = pinState(keep, keepFiles, FakeLauncher(listOf(gAlpha)))
        other.load(); advanceUntilIdle()
        other.onButton(NxButton.B); advanceUntilIdle()                        // Not now
        assertEquals("APP:a/A", keepFiles.files[ToolPins.FILE])
        assertEquals(emptyList(), keep.androidPins)
    }

    @Test fun joining_in_first_setup_moves_tools_pins_before_the_load() = runTest {
        val files = MemoryPrivateFiles().apply { files[ToolPins.FILE] = "APP:a/A\nSETTINGS" }
        val settings = MemorySettings().apply { toolApps = listOf("a/A") }
        val state = pinState(settings, files, FakeLauncher(listOf(gAlpha)))
        state.load()
        state.setHomePast(ContentRef("Home"))
        state.onButton(NxButton.B); advanceUntilIdle()
        assertIs<Screen.AndroidGamesStep>(state.screen.value)
        state.onButton(NxButton.A); advanceUntilIdle()
        assertEquals(listOf("a/A"), settings.androidPins)
        assertEquals("SETTINGS", files.files[ToolPins.FILE])
        assertEquals(listOf("SETTINGS"), state.toolPins)                       // the load read the moved file
    }

    // --- No duplicates with Tools ---

    @Test fun console_apps_leave_the_tools_tab_its_pins_and_the_tools_picker() = runTest {
        val files = MemoryPrivateFiles().apply { files[ToolPins.FILE] = "APP:a/A\nAPP:b/B" }
        val settings = androidSettings("a/A").apply { toolApps = listOf("a/A", "b/B") }
        val launcher = FakeLauncher(listOf(gAlpha, gBeta))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = AppState(AppDeps(settings, androidSource, RecordingRunner(), NoopPicker(), FakeUserData(), launcher = launcher,
            privateFiles = files, compute = UnconfinedTestDispatcher(testScheduler)), scope)
        state.load(); advanceUntilIdle()
        assertEquals(listOf("b/B"), state.screen.value.homeRows.filterIsInstance<MainRow.PinnedTool>().map { it.row.appId })
        state.openTools()
        assertEquals(listOf("Apps", "Beta", "Cheat Database", "Settings"), state.screen.value.toolRows().map { it.label })
        openToolApps(state)
        assertEquals(listOf("b/B"), assertIs<Screen.ToolApps>(state.screen.value).apps.map { it.id })
        state.onButton(NxButton.B)
        assertEquals(listOf("a/A", "b/B"), settings.toolApps)                  // kept

        state.openSettings(SettingsSection.LIBRARY)
        state.select(rowIndex(state, SettingsKind.ANDROID_GAMES)); state.onButton(NxButton.A); advanceUntilIdle()      // the games picker
        state.select(0); state.onButton(NxButton.A)                           // Alpha out of the console
        assertEquals(emptyList(), settings.androidConsoleApps)
        state.goHome()
        assertEquals(listOf("a/A", "b/B"), state.screen.value.homeRows.filterIsInstance<MainRow.PinnedTool>().map { it.row.appId })
        state.openTools()
        assertEquals(listOf("Alpha", "Apps", "Beta", "Cheat Database", "Settings"), state.screen.value.toolRows().map { it.label })
    }

    private fun searchSource() = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Super Nintendo (SFC)", "Game Boy (GB)"),
            "Home/Roms/Super Nintendo (SFC)" to listOf("Super Mario World (USA).sfc", "Super Mario Kart (USA).sfc", "F-Zero (USA).sfc"),
            "Home/Roms/Game Boy (GB)" to listOf("Super Mario Land (World).gb", "Tetris (World).gb"),
        ),
    )

    @Test fun start_opens_the_search_prompt_and_a_confirms_it() = runTest {
        val runner = RecordingRunner()
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), searchSource(), runner, NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        assertTrue(state.canSearch)
        state.onButton(NxButton.START)
        assertEquals("", assertIs<Screen.Systems>(state.screen.value).search)
        assertTrue(state.textEntryOpen)
        state.onButton(NxButton.A)   // blank: the prompt stays
        assertEquals("", assertIs<Screen.Systems>(state.screen.value).search)
        state.searchDraft("tetris")
        state.onButton(NxButton.A)
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals(ListSource.Search("tetris"), list.source)
        assertFalse(state.textEntryOpen)
        assertTrue(state.back())
        state.onButton(NxButton.START)   // prefilled with the last search
        assertEquals("tetris", assertIs<Screen.Systems>(state.screen.value).search)
    }

    @Test fun b_and_tab_keys_while_search_prompt_is_open() = runTest {
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), searchSource(), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        val tab = assertIs<Screen.Systems>(state.screen.value).tab
        state.onButton(NxButton.START)
        state.onButton(NxButton.R1); state.onButton(NxButton.RIGHT); state.onButton(NxButton.L1)
        val open = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(tab, open.tab)
        assertNotNull(open.search)
        state.selectTab((tab + 1) % open.tabs.size)   // a tap on a tab: ignored under the prompt
        assertEquals(tab, assertIs<Screen.Systems>(state.screen.value).tab)
        assertTrue(state.back())   // the Android back key: closes the prompt only
        assertNull(assertIs<Screen.Systems>(state.screen.value).search)
        state.onButton(NxButton.START)
        state.onButton(NxButton.B)
        assertNull(assertIs<Screen.Systems>(state.screen.value).search)
        assertIs<Screen.Systems>(state.screen.value)
    }

    @Test fun search_prompt_and_its_draft_survive_a_main_menu_rebuild() = runTest {
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), searchSource(), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onButton(NxButton.START)
        state.searchDraft("mar")
        state.onForeground(); advanceUntilIdle()   // rebuilds the rows (Continue may be new)
        assertEquals("mar", assertIs<Screen.Systems>(state.screen.value).search)
        state.onHomeLayout(LAND); advanceUntilIdle()   // a new frame rebuilds the Home tab
        assertEquals("mar", assertIs<Screen.Systems>(state.screen.value).search)
        state.onButton(NxButton.B)
        state.onForeground(); advanceUntilIdle()   // closed stays closed
        assertNull(assertIs<Screen.Systems>(state.screen.value).search)
    }

    @Test fun start_closes_the_open_search_prompt() = runTest {
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), searchSource(), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onButton(NxButton.START)
        assertNotNull(assertIs<Screen.Systems>(state.screen.value).search)
        state.onButton(NxButton.START)
        assertNull(assertIs<Screen.Systems>(state.screen.value).search)
    }

    private fun TestScope.searchState(settings: MemorySettings = MemorySettings(ContentRef("Home"))) =
        AppState(AppDeps(settings, searchSource(), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler))).also { it.load() }

    // START (or the Search icon) in a game list searches that list's games alone.
    @Test fun start_inside_a_game_list_searches_that_list_only() = runTest {
        val state = searchState()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "SFC" })
        state.onButton(NxButton.START)
        assertEquals("", assertIs<Screen.Games>(state.screen.value).search)
        assertTrue(state.textEntryOpen)
        state.onButton(NxButton.A)   // blank: the prompt stays
        assertEquals("", assertIs<Screen.Games>(state.screen.value).search)
        state.searchDraft("mario")
        state.onButton(NxButton.A)
        val results = assertIs<Screen.Games>(state.screen.value)
        val parent = assertIs<ListSource.Console>(assertIs<ListSource.Filtered>(results.source).parent)
        assertEquals("SFC", parent.system.tag)
        assertEquals(listOf("SFC", "SFC"), results.entries.map { it.system.tag })   // Super Mario Land (GB) is not in this list
        assertNull(results.search)
        assertFalse(state.textEntryOpen)
        state.onButton(NxButton.START)   // prefilled with the shown query; a new one searches the console again
        assertEquals("mario", assertIs<Screen.Games>(state.screen.value).search)
        state.searchDraft("zero")
        state.onButton(NxButton.A)
        val again = assertIs<Screen.Games>(state.screen.value)
        assertEquals(ListSource.Filtered(parent, "zero"), again.source)
        assertEquals(listOf("F-Zero"), again.entries.map { it.game.shownName.substringBefore(" (") })
    }

    @Test fun b_from_a_list_search_returns_to_the_list_on_the_game_it_had() = runTest {
        val state = searchState()
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "SFC" })
        val list = assertIs<Screen.Games>(state.screen.value)
        state.onButton(NxButton.START)
        assertTrue(state.back())   // the Android back key: closes the prompt only
        assertNull(assertIs<Screen.Games>(state.screen.value).search)
        state.onButton(NxButton.START); state.searchDraft("kart"); state.onButton(NxButton.A)
        val picked = assertIs<Screen.Games>(state.screen.value).entries.single()
        assertTrue(state.back())
        val back = assertIs<Screen.Games>(state.screen.value)
        assertEquals(list.source, back.source)
        assertEquals(picked.game.path, back.entries[state.selection.value.index].game.path)
        state.onButton(NxButton.DOWN)
        val row = state.selection.value.index
        state.onButton(NxButton.START); state.searchDraft("zelda"); state.onButton(NxButton.A)
        assertTrue(assertIs<Screen.Games>(state.screen.value).entries.isEmpty())   // "No results"
        assertTrue(state.back())
        assertEquals(list.source, assertIs<Screen.Games>(state.screen.value).source)
        assertEquals(row, state.selection.value.index)   // no game in the results: the row it was opened on
    }

    @Test fun a_search_result_list_cannot_be_searched_again_from_inside() = runTest {
        val state = searchState()
        state.showSearch("mario")
        val list = state.screen.value
        state.onButton(NxButton.START)
        state.openListSearch()
        assertSame(list, state.screen.value)
    }

    // The Layout dropdown sets the game list style and orientation for every game list.
    @Test fun the_layout_dropdown_sets_every_game_lists_style_and_orientation() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { gameListStyle = MenuStyle.LIST; gameListOrientation = MenuOrientation.HORIZONTAL }
        val state = searchState(settings)
        state.openSystem(assertIs<Screen.Systems>(state.screen.value).systems.first { it.tag == "SFC" })
        state.onButton(NxButton.DOWN)
        val row = state.selection.value.index
        state.toggleLayoutMenu()
        assertEquals(0, assertIs<Screen.Games>(state.screen.value).layoutMenu)   // on the layout in use
        state.onButton(NxButton.UP)
        assertEquals(0, assertIs<Screen.Games>(state.screen.value).layoutMenu)
        repeat(3) { state.onButton(NxButton.DOWN) }
        assertEquals(3, assertIs<Screen.Games>(state.screen.value).layoutMenu)
        state.onButton(NxButton.A)   // Carousel, vertical left
        assertNull(assertIs<Screen.Games>(state.screen.value).layoutMenu)
        assertEquals(MenuStyle.CAROUSEL, settings.gameListStyle)
        assertEquals(MenuOrientation.VERTICAL, settings.gameListOrientation)
        assertEquals(VerticalListAlignment.LEFT, settings.gameListVerticalAlignment)
        assertEquals(MenuOrientation.VERTICAL, state.currentOrientation())
        assertEquals(row, state.selection.value.index)   // the same game stays selected
        state.toggleLayoutMenu()
        assertEquals(3, assertIs<Screen.Games>(state.screen.value).layoutMenu)
        state.pickLayout(7)   // a tap on Backdrop, vertical right
        assertEquals(MenuStyle.BACKDROP, settings.gameListStyle)
        assertEquals(VerticalListAlignment.RIGHT, settings.gameListVerticalAlignment)
        state.toggleLayoutMenu()
        state.pickLayout(1)   // Grid: the orientation and alignment are kept for Carousel and Backdrop
        assertEquals(MenuStyle.GRID, settings.gameListStyle)
        assertEquals(MenuOrientation.VERTICAL, settings.gameListOrientation)
        assertEquals(VerticalListAlignment.RIGHT, settings.gameListVerticalAlignment)
        state.toggleLayoutMenu()
        assertTrue(state.back())   // B closes the dropdown, not the list
        assertNull(assertIs<Screen.Games>(state.screen.value).layoutMenu)
        state.toggleLayoutMenu(); state.toggleLayoutMenu()   // the icon again closes it
        assertNull(assertIs<Screen.Games>(state.screen.value).layoutMenu)
    }

    @Test fun start_on_the_tab_row_opens_search() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + mapOf(MenuCategory.CONSOLES to MenuStyle.CAROUSEL) }
        val state = stateWithTools(FakeCheatDbPlatform(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), settings = settings)
        state.onConsoles()
        state.onButton(NxButton.UP)
        assertTrue(state.tabFocus.value)
        state.onButton(NxButton.START)
        assertFalse(state.tabFocus.value)
        assertEquals("", assertIs<Screen.Systems>(state.screen.value).search)
    }

    @Test fun search_lists_matches_across_consoles_and_a_launches() = runTest {
        val runner = RecordingRunner()
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), searchSource(), runner, NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        assertIs<Screen.Systems>(state.screen.value)
        state.showSearch("  mario ")
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals(ListSource.Search("mario"), list.source)
        assertEquals("Search: mario", listTitle(list.source))
        assertEquals("mario", state.lastSearch)
        assertEquals(setOf("SFC", "GB"), list.entries.map { it.system.tag }.toSet())
        assertEquals(3, list.entries.size)
        state.onButton(NxButton.A)
        assertEquals(1, runner.launched.size)
        assertEquals(list.entries[0].system.tag, runner.launched[0].systemTag)
    }

    @Test fun search_with_no_matches_shows_an_empty_list_and_b_returns_to_the_row() = runTest {
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), searchSource(), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        state.onButton(NxButton.R1)   // another tab than the first
        val before = assertIs<Screen.Systems>(state.screen.value)
        state.onButton(NxButton.DOWN)
        val row = state.selection.value.index
        state.showSearch("zelda")
        val list = assertIs<Screen.Games>(state.screen.value)
        assertTrue(list.entries.isEmpty())
        assertTrue(state.back())
        val after = assertIs<Screen.Systems>(state.screen.value)
        assertEquals(before.category, after.category)
        assertEquals(row, state.selection.value.index)
    }

    @Test fun a_blank_search_does_nothing() = runTest {
        val state = AppState(AppDeps(MemorySettings(ContentRef("Home")), searchSource(), RecordingRunner(), NoopPicker(), compute = UnconfinedTestDispatcher(testScheduler)), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        state.load()
        val menu = state.screen.value
        state.showSearch("   ")
        assertSame(menu, state.screen.value)
        assertEquals("", state.lastSearch)
    }

    // --- The main menu on the second screen: dual mode and the preview ---

    private fun bigSource() = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms", "Collections"),
            "Home/Roms" to listOf("Nintendo DS (NDS)", "Game Boy (GB)", "Game Boy Advance (GBA)"),
            "Home/Roms/Nintendo DS (NDS)" to listOf("Mario Kart DS.nds"),
            "Home/Roms/Game Boy (GB)" to listOf("Alleyway.gb", "Tetris.gb", "Wario Land.gb"),
            "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
            "Home/Collections" to listOf("RPGs.txt", "Zeta.txt"),
        ),
        texts = mapOf("Home/Collections/RPGs.txt" to "/Roms/Game Boy (GB)/Tetris.gb\n/Roms/Game Boy (GB)/Wario Land.gb\n", "Home/Collections/Zeta.txt" to "/Roms/Game Boy (GB)/Alleyway.gb\n"),
    )

    private fun TestScope.dualState(): AppState =
        state(MemorySettings(ContentRef("Home")), src = bigSource()).also { it.load(); it.onDualMenu(true); advanceUntilIdle() }

    // App moving to the other display (a new App instance) must not load again: the open list and its highlight stay.
    @Test fun a_second_load_once_leaves_the_open_list_alone() = runTest {
        val state = state(MemorySettings(ContentRef("Home")), src = bigSource())
        state.loadOnce(); advanceUntilIdle()
        state.openConsolesTab(); advanceUntilIdle()
        state.openSystem(state.consoleRows().first().system)
        state.onButton(NxButton.DOWN)
        val before = state.screen.value
        state.loadOnce(); advanceUntilIdle()
        assertSame(before, state.screen.value)
        assertEquals(1, state.selection.value.index)
    }

    private fun AppState.consoleRows() = assertIs<Screen.Systems>(screen.value).rows.filterIsInstance<MainRow.System>()
    private fun AppState.openConsolesTab() = selectTab(assertIs<Screen.Systems>(screen.value).tabs.indexOfFirst { it.category == MenuCategory.CONSOLES })

    @Test fun the_preview_follows_the_highlighted_console() = runTest {
        val state = dualState()
        state.openConsolesTab(); advanceUntilIdle()
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        val first = assertIs<MainRow.System>(rows[state.selection.value.index])
        val preview = assertNotNull(state.secondList.value)
        assertEquals(ListSource.Console(first.system), preview.source)
        state.openSystem(first.system)
        assertEquals(assertIs<Screen.Games>(state.screen.value).entries, preview.entries)
        assertTrue(state.back()); advanceUntilIdle()
        state.onButton(NxButton.DOWN); advanceUntilIdle()
        val second = assertIs<MainRow.System>(rows[state.selection.value.index])
        assertEquals(ListSource.Console(second.system), state.secondList.value?.source)
    }

    @Test fun the_preview_is_not_rebuilt_for_the_same_console_and_library_but_is_after_a_rescan() = runTest {
        val state = dualState()
        state.openConsolesTab(); advanceUntilIdle()
        val preview = assertNotNull(state.secondList.value)
        val builds = state.secondListBuilds
        state.openConsolesTab(); advanceUntilIdle()   // the same screen published again
        assertSame(preview, state.secondList.value)
        assertEquals(builds, state.secondListBuilds)
        state.rescan(); advanceUntilIdle()
        assertTrue(state.secondListBuilds > builds)
    }

    @Test fun an_open_achievements_panel_closes_when_dual_mode_turns_on() = runTest {
        val f = panelFixture()
        f.state.openConsole("GB")
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertNotNull(f.state.raPanel.value)
        f.state.onDualMenu(true)
        assertNull(f.state.raPanel.value)
    }

    @Test fun no_preview_off_dual_or_on_home() = runTest {
        val state = dualState()
        state.openConsolesTab(); advanceUntilIdle()
        assertNotNull(state.secondList.value)
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.HOME }); advanceUntilIdle()
        assertNull(state.secondList.value)
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.TOOLS }); advanceUntilIdle()
        assertNull(state.secondList.value)
        state.openConsolesTab(); advanceUntilIdle()
        assertNotNull(state.secondList.value)
        state.onDualMenu(false)
        assertNull(state.secondList.value)
    }

    @Test fun a_collection_previews_its_games() = runTest {
        val state = dualState()
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.COLLECTIONS }); advanceUntilIdle()
        val preview = assertNotNull(state.secondList.value)
        assertEquals(ListSource.Collection("RPGs"), preview.source)
        assertEquals(listOf("Tetris", "Wario Land"), preview.entries.map { it.game.shownName })
        assertEquals(0, preview.index)
    }

    @Test fun the_preview_follows_the_last_highlight_only() = runTest {
        val state = dualState()
        state.openConsolesTab(); advanceUntilIdle()
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        state.select(0)
        repeat(2) { state.onButton(NxButton.DOWN) }
        advanceUntilIdle()
        val third = assertIs<MainRow.System>(rows[state.selection.value.index])
        assertEquals(2, state.selection.value.index)
        assertEquals(ListSource.Console(third.system), state.secondList.value?.source)
    }

    @Test fun the_preview_follows_the_last_collection_highlight_only() = runTest {
        val state = dualState()
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.COLLECTIONS }); advanceUntilIdle()
        state.select(0)
        state.onButton(NxButton.DOWN); state.onButton(NxButton.UP); state.onButton(NxButton.DOWN)   // RPGs, Zeta, RPGs, Zeta: no advance between
        advanceUntilIdle()
        val names = state.screen.value.collectionNames()
        assertEquals(ListSource.Collection(names[state.selection.value.index]), state.secondList.value?.source)
        assertEquals(listOf("Alleyway"), state.secondList.value?.entries?.map { it.game.shownName })
        // collection -> console without advancing: the console's list wins
        state.openConsolesTab()
        state.select(0)
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.COLLECTIONS })
        state.openConsolesTab()
        advanceUntilIdle()
        assertTrue(state.secondList.value?.source is ListSource.Console)
    }

    @Test fun a_tap_on_a_preview_game_opens_the_list_on_it() = runTest {
        val state = dualState()
        state.openConsolesTab(); advanceUntilIdle()
        val gb = state.consoleRows().indexOfFirst { it.system.tag == "GB" }
        state.select(gb); advanceUntilIdle()
        state.openSecondListGame(2)
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals("GB", (list.source as ListSource.Console).system.tag)
        assertEquals(2, state.selection.value.index)
        assertTrue(state.back()); advanceUntilIdle()
        assertIs<Screen.Systems>(state.screen.value)
        assertEquals(gb, state.selection.value.index)
        // a collection's preview game too
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.COLLECTIONS }); advanceUntilIdle()
        state.openSecondListGame(1)
        assertEquals(ListSource.Collection("RPGs"), assertIs<Screen.Games>(state.screen.value).source)
        assertEquals(1, state.selection.value.index)
        assertTrue(state.back())
        assertEquals(0, state.selection.value.index)
    }

    // Minimal's names take two taps, so in dual mode tapMainRow itself opens the console there.
    @Test fun a_tap_on_a_minimal_console_opens_it_in_dual_mode() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply { menuStyles = LIST_TABS + (MenuCategory.CONSOLES to MenuStyle.MINIMAL) }
        val runner = RecordingRunner()
        val state = state(settings, src = bigSource(), runner = runner).also { it.load(); it.onDualMenu(true); advanceUntilIdle() }
        state.openConsolesTab(); advanceUntilIdle()
        val rows = assertIs<Screen.Systems>(state.screen.value).rows
        state.tapMainRow(1); advanceUntilIdle()
        val list = assertIs<Screen.Games>(state.screen.value)
        assertEquals((rows[1] as MainRow.System).system.tag, (list.source as ListSource.Console).system.tag)
        assertTrue(runner.launched.isEmpty())
        val off = state(MemorySettings(ContentRef("Home")).apply { menuStyles = settings.menuStyles }, src = bigSource()).also { it.load(); advanceUntilIdle() }
        off.openConsolesTab(); advanceUntilIdle()
        off.tapMainRow(1); advanceUntilIdle()
        assertEquals(1, off.selection.value.index)
        assertIs<Screen.Systems>(off.screen.value)
    }

    // A Minimal swipe's release goes through select (MainMenuScreen's onSwipeSelect), never tapMainRow: it only moves the
    // highlight, on Consoles and on Collections, dual mode on. The tap (tapMainRow) is what opens, once; the UI side of
    // that rule is Minimal's NxTapNeverOpens (nxTap would not open it), so tapMainRow is the only opener.
    @Test fun a_minimal_swipe_release_only_moves_the_highlight_in_dual_mode() = runTest {
        val settings = MemorySettings(ContentRef("Home")).apply {
            menuStyles = LIST_TABS + (MenuCategory.CONSOLES to MenuStyle.MINIMAL) + (MenuCategory.COLLECTIONS to MenuStyle.MINIMAL)
        }
        val runner = RecordingRunner()
        val state = state(settings, src = bigSource(), runner = runner).also { it.load(); it.onDualMenu(true); advanceUntilIdle() }
        state.openConsolesTab(); advanceUntilIdle()
        state.select(1); advanceUntilIdle()
        assertEquals(1, state.selection.value.index)
        assertIs<Screen.Systems>(state.screen.value)
        state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.COLLECTIONS }); advanceUntilIdle()
        state.select(1); advanceUntilIdle()
        assertEquals(1, state.selection.value.index)
        assertIs<Screen.Systems>(state.screen.value)
        assertTrue(runner.launched.isEmpty())
        state.tapMainRow(1); advanceUntilIdle()   // a real tap still opens, once
        assertEquals(ListSource.Collection("Zeta"), assertIs<Screen.Games>(state.screen.value).source)
    }

    // Every other style's console and collection opens on one tap through nxTap (tapMainRow, then openSelected): dual
    // mode must not open it a second time (the list's first game would launch).
    @Test fun a_one_tap_console_or_collection_opens_once_in_dual_mode() = runTest {
        for (style in listOf(MenuStyle.LIST, MenuStyle.GRID, MenuStyle.CAROUSEL, MenuStyle.BACKDROP)) {
            val settings = MemorySettings(ContentRef("Home")).apply {
                menuStyles = LIST_TABS + (MenuCategory.CONSOLES to style) + (MenuCategory.COLLECTIONS to style)
            }
            val runner = RecordingRunner()
            val state = state(settings, src = bigSource(), runner = runner).also { it.load(); it.onDualMenu(true); advanceUntilIdle() }
            state.openConsolesTab(); advanceUntilIdle()
            val rows = assertIs<Screen.Systems>(state.screen.value).rows
            state.tapMainRow(1); state.openSelected(); advanceUntilIdle()
            val list = assertIs<Screen.Games>(state.screen.value, "$style")
            assertEquals((rows[1] as MainRow.System).system.tag, (list.source as ListSource.Console).system.tag, "$style")
            assertTrue(runner.launched.isEmpty(), "$style launched ${runner.launched}")
            assertTrue(state.back()); advanceUntilIdle()
            state.selectTab(assertIs<Screen.Systems>(state.screen.value).tabs.indexOfFirst { it.category == MenuCategory.COLLECTIONS }); advanceUntilIdle()
            state.tapMainRow(1); state.openSelected(); advanceUntilIdle()
            assertEquals(ListSource.Collection("Zeta"), assertIs<Screen.Games>(state.screen.value, "$style").source, "$style")
            assertTrue(runner.launched.isEmpty(), "$style launched ${runner.launched}")
        }
    }

    // Dual: the panel never opens over the top list (the trophy icon, the game's menu item), Achievements panel Hide or not.
    @Test fun the_dual_game_list_offers_no_achievements_panel() = runTest {
        val f = panelFixture()
        f.state.settings.gameListAchievementsPanel = false   // Hide: the game's menu would carry Achievements
        f.state.onDualMenu(true)
        f.state.openConsole("GB")
        f.state.openRaPanel(); advanceUntilIdle()
        assertNull(f.state.raPanel.value)
        f.state.openContextMenu(0)
        assertTrue(assertNotNull(assertIs<Screen.Games>(f.state.screen.value).menu).items.none { it.kind == ContextKind.ACHIEVEMENTS })
        f.state.closeContextMenu()
        f.state.onDualMenu(false)
        f.state.openContextMenu(0)
        assertTrue(assertNotNull(assertIs<Screen.Games>(f.state.screen.value).menu).items.any { it.kind == ContextKind.ACHIEVEMENTS })
    }

    @Test fun x_does_nothing_in_the_dual_game_list() = runTest {
        val f = panelFixture()
        f.state.onDualMenu(true)
        f.state.openConsole("GB")
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertNull(f.state.raPanel.value)
        f.state.onDualMenu(false)
        f.state.onButton(NxButton.X); advanceUntilIdle()
        assertNotNull(f.state.raPanel.value)
    }
}

/** The consoles on the main menu (every tab), as the tests knew them before typed rows. */
private val Screen.Systems.systems: List<SystemFolder> get() = tabs.flatMap { it.rows }.flatMap { (it as? MainRow.System)?.systems.orEmpty() }   // a merged row: each of its systems

/** The main menu on the Tools tab: its tools. */
private fun Screen.toolRows(): List<ToolRow> =
    assertIs<Screen.Systems>(this).also { assertEquals(MenuCategory.TOOLS, it.category) }.rows.map { (it as MainRow.Tool).row }

/** The main menu's Collections tab rows (it opens that tab). */
private fun AppState.collectionRows(): List<MainRow> {
    openCollectionsTab()
    return assertIs<Screen.Systems>(screen.value).rows
}

/** The main menu on the Collections tab: its names. */
private fun Screen.collectionNames(): List<String> =
    assertIs<Screen.Systems>(this).also { assertEquals(MenuCategory.COLLECTIONS, it.category) }.rows.map { (it as MainRow.Collection).name }

/** On the main menu: shows the Consoles tab (a tap on it). */
private fun AppState.onConsoles() {
    selectTab(assertIs<Screen.Systems>(screen.value).tabs.indexOfFirst { it.category == MenuCategory.CONSOLES })
    assertEquals(MenuCategory.CONSOLES, assertIs<Screen.Systems>(screen.value).category)
}

/** On the main menu: shows the Collections tab (a tap on it) and returns its names. */
private fun AppState.openCollectionsTab(): List<String> {
    selectTab(assertIs<Screen.Systems>(screen.value).tabs.indexOfFirst { it.category == MenuCategory.COLLECTIONS })
    return screen.value.collectionNames()
}
private val Screen.Games.games: List<GameEntry> get() = entries.map { it.game }
private val Screen.Games.system: SystemFolder get() = (source as ListSource.Console).system

/** Game time that only answers the reads the menu makes (empty); every other call is a test bug. */
private object NoGameTime : GameTimeData {
    override suspend fun totals(): List<GameTotal> = error("unused")
    override suspend fun grandTotal(): Long = error("unused")
    override suspend fun members(target: Long): List<GameTotal> = error("unused")
    override suspend fun sessions(target: Long): com.nxredux.mobile.gametime.GameSessions = error("unused")
    override suspend fun mergeCandidates(target: Long): List<GameTotal> = error("unused")
    override suspend fun merge(from: Long, into: Long, name: String): Unit = error("unused")
    override suspend fun unmerge(gameId: Long): Unit = error("unused")
    override suspend fun delete(target: Long): Unit = error("unused")
    override suspend fun byPath(): Map<String, GameTotal> = emptyMap()
    override suspend fun window(sinceMs: Long, nowMs: Long): WindowTotals = WindowTotals(0L, null)
    override suspend fun playedPaths(sinceMs: Long, nowMs: Long): Set<String> = emptySet()
}
