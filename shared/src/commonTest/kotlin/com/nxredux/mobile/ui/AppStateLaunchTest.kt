package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.MenuStyles
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
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A on a Minimal page's game or tool opens it at once, as on any other page; a folder still fades first. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStateLaunchTest {
    private class MemorySettings(override var treeRef: ContentRef? = null) : AppSettings {
        override var extraFolders: List<ExtraFolder> = emptyList()
        override var defaultTags: Map<String, String> = emptyMap()
        override var debugLog = false
        override var secondScreen = true
        override var pauseWhenUnfocused = true
        override var bottomScreen = com.nxredux.mobile.runner.BottomScreen.AUTO
        override var saveFormat = SaveFormat.DEFAULT
        override var stateFormat = StateFormat.DEFAULT
        override var useExtractedFileName = false
        override var padSkin: Boolean = true
        override var padTheme: PadTheme = PadTheme.DEFAULT
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
        /** Set: each launch suspends until it completes (the runner waiting for a previous game's process to end). */
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override suspend fun launch(session: GameSession, recent: com.nxredux.mobile.storage.Recent) { launched += session; gate?.await() }
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

    private class Rig(val state: AppState, val runner: RecordingRunner, val data: FakeUserData)

    /** Minimal Home with Continue (Tetris) and Settings pinned. */
    private fun TestScope.rig(): Rig {
        val runner = RecordingRunner()
        val data = FakeUserData(recents = listOf(Recent(tetris)))
        val files = MemoryPrivateFiles().apply { files[ToolPins.FILE] = ToolPins.encode(listOf("SETTINGS")) }
        val deps = AppDeps(MemorySettings(ContentRef("Home")), source, runner, NoopPicker(), data, privateFiles = files,
            now = { testScheduler.currentTime }, compute = UnconfinedTestDispatcher(testScheduler))
        val state = AppState(deps, CoroutineScope(UnconfinedTestDispatcher(testScheduler))).also { it.load(); advanceUntilIdle() }
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

    @Test fun a_on_a_minimal_game_launches_at_once() = runTest {
        val r = rig()
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals("Tetris.gb", r.runner.launched.single().fileName)
        assertNull(r.state.minimalOpening.value)   // no fade
    }

    @Test fun a_second_pick_is_refused_while_the_runner_still_waits() = runTest {
        val r = rig()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>().also { r.runner.gate = it }
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals(1, r.runner.launched.size)
        r.state.onForeground()   // the menu is in front again while the runner still waits
        advanceUntilIdle()
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals(1, r.runner.launched.size)   // still in flight: no second launch
        gate.complete(Unit)
        r.state.onForeground()
        advanceUntilIdle()
        r.state.selectRow { it is MainRow.Continue }
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals(2, r.runner.launched.size)
    }

    @Test fun a_refused_launch_shows_the_problem_at_once() = runTest {
        val r = rig()
        r.state.selectRow { it is MainRow.Continue }
        source.moveFile(ContentRef("Home/Roms/Game Boy (GB)"), "Tetris.gb", ContentRef("Home"))   // gone since the scan
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertTrue(r.state.error.value!!.startsWith("Could not find Tetris.gb"))
        assertTrue(r.runner.launched.isEmpty())
    }

    @Test fun a_minimal_tool_opens_at_once() = runTest {
        val r = rig()
        r.state.selectRow { it is MainRow.PinnedTool && it.row.kind == ToolKind.SETTINGS }
        r.state.onButton(NxButton.A)
        assertIs<Screen.SettingsMenu>(r.state.screen.value)
    }
}
