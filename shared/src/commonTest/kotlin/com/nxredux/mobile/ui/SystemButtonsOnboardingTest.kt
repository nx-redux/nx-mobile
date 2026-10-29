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
import com.nxredux.mobile.settings.PadButtons
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

/** The SELECT / START / MENU page in a real [AppState]: in onboarding after the text size, and once per new pad model. */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemButtonsOnboardingTest {
    class Settings(override var treeRef: ContentRef? = ContentRef("Home"), override var onboardingDone: Boolean? = true) : AppSettings {
        override var onboardingStep: String? = null
        override var extraFolders: List<ExtraFolder> = emptyList()
        override var defaultTags: Map<String, String> = emptyMap()
        override var padTheme = PadTheme.DEFAULT
        override var padButtons: PadButtons = PadButtons.DEFAULT
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
    private class Runner : EmulatorRunner { override fun launch(session: GameSession, onStarted: () -> Unit) { onStarted() } }

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

    private fun AppState.page() = assertIs<Screen.Onboarding>(screen.value).also { assertEquals(OnboardingStep.SYSTEM_BUTTONS, it.step) }
    private fun AppState.row(label: String) { select(page().rows.indexOfFirst { it.label == label }); onButton(NxButton.A) }
    private fun AppState.tap(code: Int, scan: Int = 0) = systemButtonKey(aya, code, scan, true, false).also { systemButtonKey(aya, code, scan, false, false) }
    private fun AppState.toTextSizeContinue() {
        onButton(NxButton.A)   // the welcome's Get started
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(screen.value).step)
        select(1); onButton(NxButton.A)
    }

    @Test fun stepFollowsTextSizeWithController() = runTest {
        pads = listOf(aya)
        val s = state(Settings(treeRef = null, onboardingDone = null))
        s.toTextSizeContinue()
        assertEquals(listOf("SELECT", "START", "MENU", "I've pressed them all"), s.page().rows.map { it.label })   // no Skip: the step is required
        assertEquals(
            listOf("Your controller's buttons", "Press SELECT, START and MENU on your controller (AYANEO Controller). One wasn't found? Choose I've pressed them all, and we will configure it next."),
            s.page().details,
        )
        assertEquals("Setup", s.page().title)
    }

    @Test fun noControllerSkipsStepAndAsksLater() = runTest {
        val fresh = Settings(treeRef = null, onboardingDone = null)
        val s = state(fresh)
        s.toTextSizeContinue()
        assertFalse(s.screen.value is Screen.Onboarding)
        assertEquals("", fresh.systemButtons)

        val settings = Settings()
        val later = state(settings)
        assertIs<Screen.Systems>(later.screen.value)
        pads = listOf(aya)
        later.padsChanged()
        later.page()
    }

    @Test fun askedModelNotAskedAgain() = runTest {
        val settings = Settings()
        val s = state(settings)
        pads = listOf(aya)
        s.padsChanged()
        s.tap(KeyCodes.BUTTON_SELECT); s.tap(KeyCodes.BUTTON_START); s.tap(KeyCodes.BUTTON_MODE)
        assertIs<Screen.Systems>(s.screen.value)
        assertTrue(aya.key in SystemButtons.decode(settings.systemButtons).asked)
        s.padsChanged()
        assertIs<Screen.Systems>(s.screen.value)
    }

    @Test fun menuSkipShowsChordNote() = runTest {
        val settings = Settings()
        val s = state(settings)
        pads = listOf(aya)
        s.padsChanged()
        s.tap(KeyCodes.BUTTON_SELECT); s.tap(KeyCodes.BUTTON_START)
        s.row("I've pressed them all")
        assertTrue(s.page().details.any { "MENU" in it })
        assertEquals(listOf("Skip"), s.page().rows.map { it.label })   // MENU's bind: Skip only, no Cancel
        s.row("Skip")
        assertEquals("No MENU button? Press SELECT+START together to open the menu.", s.notice.value)
        assertIs<Screen.Systems>(s.screen.value)
        assertTrue(aya.key in SystemButtons.decode(settings.systemButtons).asked)
    }

    @Test fun standaloneReturnsToPreviousScreen() = runTest {
        val s = state()
        pads = listOf(aya)
        s.padsChanged()
        assertEquals("Controller", s.page().title)
        s.tap(KeyCodes.BUTTON_SELECT); s.tap(KeyCodes.BUTTON_START); s.tap(KeyCodes.BUTTON_MODE)
        assertIs<Screen.Systems>(s.screen.value)
    }

    @Test fun pageKeysDontReachMenu() = runTest {
        val s = state()
        assertFalse(s.systemButtonKey(aya, 0, 0x2f1, true, false))
        s.systemButtonKey(aya, 0, 0x2f1, false, false)
        pads = listOf(aya)
        s.padsChanged()
        assertTrue(s.systemButtonKey(aya, 0, 0x2f1, true, false))
        assertTrue(s.page().details.any { it == "That's not one of the three." })
    }

    @Test fun ayaMenuBindsByScanCode() = runTest {
        val settings = Settings()
        val s = state(settings)
        pads = listOf(aya)
        s.padsChanged()
        s.tap(KeyCodes.BUTTON_SELECT); s.tap(KeyCodes.BUTTON_START); s.tap(0, 0x2f1)
        s.row("I've pressed them all")
        s.tap(0, 0x2f1)
        assertIs<Screen.Systems>(s.screen.value)
        assertEquals(110, SystemButtons.translate(SystemButtons.decode(settings.systemButtons), aya.key, 0, 0x2f1))
    }

    @Test fun bOnThePageGoesBackToTextSizeInOnboarding() = runTest {
        pads = listOf(aya)
        val s = state(Settings(treeRef = null, onboardingDone = null))
        s.toTextSizeContinue()
        s.page()
        s.onButton(NxButton.B)
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(s.screen.value).step)
    }

    @Test fun doneInOnboardingGoesOnToTheFolders() = runTest {
        pads = listOf(aya)
        val settings = Settings(treeRef = null, onboardingDone = null)
        val s = state(settings)
        s.toTextSizeContinue()
        s.tap(KeyCodes.BUTTON_SELECT); s.tap(KeyCodes.BUTTON_START); s.tap(KeyCodes.BUTTON_MODE)
        assertFalse(s.screen.value is Screen.Onboarding)
        assertTrue(aya.key in SystemButtons.decode(settings.systemButtons).asked)
    }

    // Review fix 1: closing the page over the menu brings back the menu's own selection.
    @Test fun standaloneRestoresTheMenuSelection() = runTest {
        val s = state()
        s.select(1)
        val before = s.selection.value
        pads = listOf(aya)
        s.padsChanged()
        s.tap(KeyCodes.BUTTON_SELECT); s.tap(KeyCodes.BUTTON_START); s.tap(KeyCodes.BUTTON_MODE)
        assertIs<Screen.Systems>(s.screen.value)
        assertEquals(before, s.selection.value)
    }

    // Review fix 1: never over Settings (it would drop a Shortcuts draft or a wait); the menu asks instead.
    @Test fun notOpenedOverSettings() = runTest {
        val s = state()
        s.openSettings(SettingsSection.CONTROLS)
        pads = listOf(aya)
        s.padsChanged()
        assertIs<Screen.Settings>(s.screen.value)
    }

    // Review fix 1: a page another screen replaced is abandoned, so the model is still asked later.
    @Test fun aReplacedPageIsAskedAgain() = runTest {
        val s = state()
        pads = listOf(aya)
        s.padsChanged()
        s.page()
        s.openSettings(SettingsSection.CONTROLS)   // something else took the screen
        while (s.screen.value !is Screen.Systems) s.onButton(NxButton.B)
        s.padsChanged()
        s.page()
    }

    // Review fix 3: a pad already connected at launch (a handheld's own) is asked when the menu first shows.
    @Test fun coldStartAsksTheBuiltInPad() = runTest {
        pads = listOf(aya)
        val s = state()
        s.page()
    }

    // The step is required: B on the page on its own (pad or touch) leaves it up and asks nothing.
    @Test fun bDoesNotSkipTheStandalonePage() = runTest {
        val settings = Settings()
        val s = state(settings)
        pads = listOf(aya)
        s.padsChanged()
        assertFalse(s.page().canGoBack)                                           // no B hint
        assertFalse(s.systemButtonKey(aya, KeyCodes.BUTTON_B, 0, true, false))   // the page leaves B to the menu
        s.onButton(NxButton.B)
        s.back()
        s.page()
        assertEquals("", settings.systemButtons)
    }

    // "I've pressed them all" and Skip are rows only: no page line mentions holding START.
    @Test fun pageTextHasNoHoldStart() = runTest {
        val s = state()
        pads = listOf(aya)
        s.padsChanged()
        assertTrue(s.page().details.none { "hold" in it.lowercase() })
        s.tap(KeyCodes.BUTTON_SELECT); s.tap(KeyCodes.BUTTON_START)
        s.row("I've pressed them all")
        assertTrue(s.page().details.none { "hold" in it.lowercase() })
    }
}
