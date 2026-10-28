package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.runner.LaunchLine
import com.nxredux.mobile.runner.LaunchName
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.settings.PadButtons
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.settings.SecondaryAccent
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.storage.MemoryPrivateFiles
import com.nxredux.mobile.storage.Recent
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.Shortcut
import com.nxredux.mobile.storage.StateFormat
import com.nxredux.mobile.storage.ToolPins
import com.nxredux.mobile.storage.UserDataStore
import com.nxredux.mobile.ui.nx.LaunchTimeline
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The launch sequence on Minimal pages ([AppState.launchSequence]): spread, hand-off, return, retreat. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStateLaunchTest {
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
        override var padButtons: PadButtons = PadButtons.DEFAULT
        // Home Minimal (its default); the other tabs and the game lists too.
        override var menuStyles: Map<MenuCategory, MenuStyle> = MenuCategory.entries.associateWith { MenuStyle.MINIMAL }
        override var gameListStyle: MenuStyle = MenuStyle.MINIMAL
        override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap()
        override var gameListOrientation: MenuOrientation = MenuStyles.DEFAULT_ORIENTATION
        override var gameListVerticalAlignment: VerticalListAlignment = VerticalListAlignment.DEFAULT
        override var toolApps: List<String> = emptyList()
        override var androidConsoleApps: List<String> = emptyList()
        override var androidGamesAsked = false
        override var allFilesAsked = false
        override var onboardingDone: Boolean? = true
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
        override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showNetplayHint = true; override var menuPageTitle = MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = AccentPreset.DEFAULT
        override var secondaryAccent: SecondaryAccent = SecondaryAccent.DEFAULT
        override var switcherLayout = com.nxredux.mobile.settings.SwitcherLayout.DEFAULT
        override var optionTextSize = com.nxredux.mobile.settings.OptionTextSize.DEFAULT
    }
    private class RecordingRunner : EmulatorRunner {
        val launched = ArrayList<GameSession>()
        override fun launch(session: GameSession, onStarted: () -> Unit) { launched += session; onStarted() }
    }
    private class NoopPicker : FolderPicker {
        override fun pickHome() = Unit
        override fun pickExtra() = Unit
        override fun forget(ref: ContentRef) = Unit
    }
    private class FakeUserData(var recents: List<Recent> = emptyList(), var pins: List<Shortcut> = emptyList()) : UserDataStore {
        override suspend fun readRecents() = recents
        override suspend fun writeRecents(list: List<Recent>) { recents = list }
        override suspend fun readPins() = pins
        override suspend fun writePins(list: List<Shortcut>): Boolean { pins = list; return true }
        override suspend fun readHidden() = emptyList<String>()
        override suspend fun writeHidden(list: List<String>) = true
        override suspend fun resumeInfo(tag: String, fileName: String) = ResumeInfo.NONE
    }

    private val tetris = "/Roms/Game Boy (GB)/Tetris.gb"
    private val source = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Game Boy (GB)", "Game Boy Advance (GBA)"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
        ),
    )
    private val name = LaunchName(listOf(LaunchLine("Tetris", 24f, 300f)), 40f, 0.5f, bold = true, argb = 0xFFE4E4E7.toInt())

    private class Rig(val state: AppState, val runner: RecordingRunner, val data: FakeUserData)

    /** Minimal Home with Continue (Tetris) and Settings pinned; the name provider set, as the menu sets it. */
    private fun TestScope.rig(reduced: Boolean = false): Rig {
        val runner = RecordingRunner()
        val data = FakeUserData(recents = listOf(Recent(tetris)))
        val files = MemoryPrivateFiles().apply { files[ToolPins.FILE] = ToolPins.encode(listOf("SETTINGS")) }
        val deps = AppDeps(MemorySettings(ContentRef("Home")), source, runner, NoopPicker(), data, privateFiles = files,
            now = { testScheduler.currentTime }, compute = UnconfinedTestDispatcher(testScheduler), reducedMotion = { reduced })
        val state = AppState(deps, CoroutineScope(UnconfinedTestDispatcher(testScheduler))).also { it.load(); advanceUntilIdle() }
        state.launchNameProvider = { name }
        return Rig(state, runner, data)
    }

    private fun AppState.selectRow(match: (MainRow) -> Boolean): Int {
        val s = assertIs<Screen.Systems>(screen.value)
        assertEquals(MenuCategory.HOME, s.category)
        val at = s.rows.indexOfFirst(match)
        assertTrue(at >= 0, "no such row in ${s.rows}")
        select(at)
        return at
    }

    private fun AppState.phase() = launchSequence.value?.phase

    @Test fun a_on_a_minimal_game_spreads_then_launches_with_the_name() = runTest {
        val r = rig()
        val at = r.state.selectRow { it is MainRow.Continue }
        val t0 = testScheduler.currentTime
        r.state.onButton(NxButton.A)
        val seq = r.state.launchSequence.value
        assertEquals(LaunchPhase.SPREAD, seq?.phase)
        assertEquals(at, seq?.index)
        assertEquals(MenuCategory.HOME, seq?.page)
        assertEquals(false, seq?.tool)
        assertEquals(t0, seq?.startedAtMs)
        assertNull(r.state.minimalOpening.value)   // no band
        advanceTimeBy(LaunchTimeline.SPREAD_MS - 1)
        assertTrue(r.runner.launched.isEmpty())
        advanceTimeBy(2)
        assertEquals(name, r.runner.launched.single().launchName)
        assertEquals(LaunchPhase.HANDED_OFF, r.state.phase())
    }

    @Test fun input_is_ignored_until_the_game_has_the_screen() = runTest {
        val r = rig()
        val at = r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        r.state.onButton(NxButton.A)
        r.state.onButton(NxButton.B)
        r.state.onButton(NxButton.DOWN)
        assertEquals(at, r.state.selection.value.index)
        advanceTimeBy(LaunchTimeline.SPREAD_MS + 1)
        assertEquals(1, r.runner.launched.size)
        assertEquals(at, r.state.selection.value.index)
        r.state.onButton(NxButton.A)   // handed off: the menu is behind the game
        advanceTimeBy(LaunchTimeline.SPREAD_MS + 1)
        assertEquals(1, r.runner.launched.size)
    }

    @Test fun back_from_the_game_fades_the_overlay_out() = runTest {
        val r = rig()
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        advanceTimeBy(LaunchTimeline.SPREAD_MS + 1)
        assertEquals(LaunchPhase.HANDED_OFF, r.state.phase())
        r.state.onForeground()
        assertEquals(LaunchPhase.RETURNING, r.state.phase())
        advanceTimeBy(LaunchTimeline.RETURN_FADE_MS + 1)
        assertNull(r.state.launchSequence.value)
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)   // A works again
        assertEquals(LaunchPhase.SPREAD, r.state.phase())
        advanceTimeBy(LaunchTimeline.SPREAD_MS + 1)
        assertEquals(2, r.runner.launched.size)
    }

    @Test fun a_vanished_row_retreats_instead_of_launching() = runTest {
        val r = rig()
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        r.data.recents = emptyList()
        r.state.onForeground()   // Home's rows rebuilt: Continue is gone
        advanceTimeBy(100)
        assertTrue(assertIs<Screen.Systems>(r.state.screen.value).rows.none { it is MainRow.Continue })
        advanceTimeBy(LaunchTimeline.SPREAD_MS)
        assertEquals(LaunchPhase.RETREAT, r.state.phase())
        assertTrue(r.runner.launched.isEmpty())
        advanceTimeBy(LaunchTimeline.RETREAT_MS + 1)
        assertNull(r.state.launchSequence.value)
        assertTrue(r.runner.launched.isEmpty())
    }

    @Test fun a_refused_launch_retreats_then_shows_the_problem() = runTest {
        val r = rig()
        r.state.selectRow { it is MainRow.Continue }
        source.moveFile(ContentRef("Home/Roms/Game Boy (GB)"), "Tetris.gb", ContentRef("Home"))   // gone since the scan
        r.state.onButton(NxButton.A)
        advanceTimeBy(LaunchTimeline.SPREAD_MS + 1)
        assertEquals(LaunchPhase.RETREAT, r.state.phase())
        assertNull(r.state.error.value)
        advanceTimeBy(LaunchTimeline.RETREAT_MS - 2)
        assertNull(r.state.error.value)   // not over the black
        advanceTimeBy(2)
        assertNull(r.state.launchSequence.value)
        assertTrue(r.state.error.value!!.startsWith("Could not find Tetris.gb"))
        assertTrue(r.runner.launched.isEmpty())
    }

    @Test fun a_minimal_tool_opens_its_screen_on_black() = runTest {
        val r = rig()
        r.state.selectRow { it is MainRow.PinnedTool && it.row.kind == ToolKind.SETTINGS }
        r.state.onButton(NxButton.A)
        assertEquals(true, r.state.launchSequence.value?.tool)
        assertIs<Screen.Systems>(r.state.screen.value)   // not yet
        advanceTimeBy(LaunchTimeline.SPREAD_MS + 1)
        assertIs<Screen.SettingsMenu>(r.state.screen.value)
        assertEquals(LaunchPhase.RETURNING, r.state.phase())
        advanceTimeBy(LaunchTimeline.RETURN_FADE_MS + 1)
        assertNull(r.state.launchSequence.value)
    }

    @Test fun reduced_motion_waits_150_not_450() = runTest {
        val r = rig(reduced = true)
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        advanceTimeBy(LaunchTimeline.REDUCED_FADE_MS - 1)
        assertTrue(r.runner.launched.isEmpty())
        advanceTimeBy(2)
        assertEquals(1, r.runner.launched.size)
    }

    @Test fun a_launch_that_never_returns_clears_after_10_s() = runTest {
        val r = rig()
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        advanceTimeBy(LaunchTimeline.SPREAD_MS + 1)
        assertEquals(LaunchPhase.HANDED_OFF, r.state.phase())
        advanceTimeBy(LaunchTimeline.GIVE_UP_MS - 2)
        assertEquals(LaunchPhase.HANDED_OFF, r.state.phase())
        advanceTimeBy(2)
        assertNull(r.state.launchSequence.value)
        r.state.onButton(NxButton.A)   // input is free again
        assertEquals(LaunchPhase.SPREAD, r.state.phase())
    }

    @Test fun a_long_session_still_fades_back_in() = runTest {
        val r = rig()
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        advanceTimeBy(LaunchTimeline.SPREAD_MS + 1)
        assertEquals(LaunchPhase.HANDED_OFF, r.state.phase())
        r.state.onBackground()   // the game is in front
        advanceTimeBy(LaunchTimeline.GIVE_UP_MS * 3)
        assertEquals(LaunchPhase.HANDED_OFF, r.state.phase())
        r.state.onForeground()
        assertEquals(LaunchPhase.RETURNING, r.state.phase())
        advanceTimeBy(LaunchTimeline.RETURN_FADE_MS + 1)
        assertNull(r.state.launchSequence.value)
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)   // input works
        assertEquals(LaunchPhase.SPREAD, r.state.phase())
    }
}
