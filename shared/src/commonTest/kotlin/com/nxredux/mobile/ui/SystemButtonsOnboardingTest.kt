package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.library.FakeRomsSource
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
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.shortcuts.PadModel
import com.nxredux.mobile.shortcuts.SystemButtons
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Setup with a controller in a real [AppState]: no page asks for its SELECT / START / MENU; the last page says where they are set. */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemButtonsOnboardingTest {
    class Settings(override var treeRef: ContentRef? = ContentRef("Home"), override var onboardingDone: Boolean? = true) : AppSettings {
        override var onboardingStep: String? = null
        override var extraFolders: List<ExtraFolder> = emptyList()
        override var defaultTags: Map<String, String> = emptyMap()
        override var padSkin = true
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
        override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showNetplayHint = true; override var menuPageTitle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = com.nxredux.mobile.settings.MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = com.nxredux.mobile.settings.AccentPreset.DEFAULT; override var secondaryAccent: com.nxredux.mobile.settings.SecondaryAccent = com.nxredux.mobile.settings.SecondaryAccent.DEFAULT
        override var shortcuts = ""
        override var systemButtons = ""
    }

    private object Picker : FolderPicker { override fun pickHome() {}; override fun pickExtra() {}; override fun forget(ref: ContentRef) {} }
    private class Runner : EmulatorRunner { override suspend fun launch(session: GameSession, recent: com.nxredux.mobile.storage.Recent) {} }

    private val source = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Super Nintendo (SFC)"),
            "Home/Roms/Super Nintendo (SFC)" to listOf("Super Mario Kart (USA).sfc"),
        ),
    )

    private val aya = PadModel(0x4001, 0x0428, "AYANEO Controller")
    private var pads = listOf<PadModel>()

    private fun TestScope.state(settings: Settings = Settings()): AppState {
        val state = AppState(
            AppDeps(settings, source, Runner(), Picker, compute = UnconfinedTestDispatcher(testScheduler), connectedPads = { pads }),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load(); runCurrent()
        return state
    }

    private fun AppState.toTextSizeContinue() {
        onButton(NxButton.A)   // the welcome's Get started
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(screen.value).step)
        select(1); onButton(NxButton.A)
    }

    // A controller adds no step: the text size goes straight on to the folders, as without one.
    @Test fun controllerAddsNoSetupStep() = runTest {
        pads = listOf(aya)
        val fresh = Settings(treeRef = null, onboardingDone = null)
        val s = state(fresh)
        s.toTextSizeContinue()
        assertFalse(s.screen.value is Screen.Onboarding)
        assertEquals("", fresh.systemButtons)
    }

    // A controller connected later opens nothing over the menu.
    @Test fun newControllerOpensNoPage() = runTest {
        val s = state()
        val menu = assertIs<Screen.Systems>(s.screen.value)
        pads = listOf(aya)
        assertFalse(s.systemButtonKey(aya, null, KeyCodes.BUTTON_SELECT, 0, true, false))
        assertEquals(menu, s.screen.value)
    }

    // The entry earlier versions kept for the models their page had asked about is read past and dropped on the next save.
    @Test fun oldAskedEntryIsReadPast() {
        val map = SystemButtons.decode("4001:0428=MENU:0:753;asked=4001:0428,057e:2009")
        assertEquals(setOf("4001:0428"), map.bound.keys)
        assertEquals("4001:0428=MENU:0:753", SystemButtons.encode(map))
        assertTrue(SystemButtons.decode("asked=4001:0428").bound.isEmpty())
    }
}
