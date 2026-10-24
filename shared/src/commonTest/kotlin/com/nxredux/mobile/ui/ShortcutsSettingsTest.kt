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
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutBinding
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.Shortcuts
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.ui.nx.Hint
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings -> Controls -> Shortcuts in a real [AppState]: the rows, press to bind on raw key codes, saving on close. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutsSettingsTest {
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
        override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showRecentHint = true; override var showSearchHint = true; override var showNetplayHint = true; override var menuPageTitle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = com.nxredux.mobile.settings.MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = com.nxredux.mobile.settings.AccentPreset.DEFAULT; override var secondaryAccent: com.nxredux.mobile.settings.SecondaryAccent = com.nxredux.mobile.settings.SecondaryAccent.DEFAULT
        override var shortcuts = ""
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

    private fun TestScope.state(settings: Settings = Settings()): AppState {
        val state = AppState(
            AppDeps(settings, source, Runner(), Picker, compute = UnconfinedTestDispatcher(testScheduler)),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load(); runCurrent()
        return state
    }

    private fun AppState.page() = assertIs<Screen.Settings>(screen.value)
    private fun AppState.row(label: String) = page().rows.first { it.label == label }

    /** Settings -> Controls -> Shortcuts, by its row. */
    private fun AppState.openShortcuts() {
        openSettings(SettingsSection.CONTROLS)
        select(page().rows.indexOfFirst { it.kind == SettingsKind.SHORTCUTS }); openSelected()
        assertEquals(SettingsSection.SHORTCUTS, page().section)
    }

    /** A on [action]'s bind row: capture starts. */
    private fun AppState.bind(action: ShortcutAction) {
        select(page().rows.indexOfFirst { it.label == action.label }); onButton(NxButton.A)
        assertEquals(action, capturingShortcut.value)
    }

    private fun AppState.press(vararg codes: Int) { for (c in codes) assertTrue(captureKey(c, true)); for (c in codes.reversed()) captureKey(c, false) }

    @Test fun controlsHasShortcutsRow() = runTest {
        val s = state()
        s.openSettings(SettingsSection.CONTROLS)
        val labels = s.page().rows.map { it.label }
        assertEquals(labels.indexOf("Pad layout") + 1, labels.indexOf("Shortcuts"))
        assertEquals(SettingsKind.SHORTCUTS, s.row("Shortcuts").kind)
    }

    @Test fun pageRows() = runTest {
        val s = state()
        s.openShortcuts()
        val rows = s.page().rows
        val binds = rows.filter { it.kind == SettingsKind.SHORTCUT_BIND }
        assertEquals(ShortcutAction.entries.map { it.label }, binds.map { it.label })
        assertTrue(binds.all { it.secondary == "None" })
        val touch = rows.filter { it.kind == SettingsKind.SHORTCUT_TOUCH }
        assertEquals(listOf("Touch button 1", "Touch button 2"), touch.map { it.label })
        assertTrue(touch.all { it.secondary == "None" })
        assertEquals(SettingsRow(SettingsKind.RESET_DEFAULTS, "Clear all"), rows.last())
        assertEquals(binds + touch + rows.last(), rows)
    }

    @Test fun captureSingle() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF)
        assertEquals("Press a button…", s.row("Toggle FF").secondary)
        assertTrue(s.captureKey(103, true))
        assertNull(s.capturingShortcut.value)
        assertEquals("R1", s.row("Toggle FF").secondary)
    }

    @Test fun captureCombo() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF)
        s.captureKey(110, true); s.captureKey(103, true)
        assertEquals("MENU+R1", s.row("Toggle FF").secondary)
    }

    @Test fun extraButtonBinds() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.RESET)
        s.press(KeyCodes.BUTTON_1)
        assertEquals("Button 1", s.row(ShortcutAction.RESET.label).secondary)
    }

    // L23: MENU pressed and released alone binds nothing, keeps waiting, and opens nothing (its keys are swallowed).
    @Test fun menuAloneNotBound() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF)
        s.press(KeyCodes.BUTTON_MODE)
        assertEquals(ShortcutAction.TOGGLE_FF, s.capturingShortcut.value)
        assertEquals("Press a button…", s.row("Toggle FF").secondary)
        assertNull(s.screen.value.contextMenu)
        // MENU+R1 binds; MENU's release afterwards is still swallowed, not a press for the menu.
        s.captureKey(KeyCodes.BUTTON_MODE, true); s.captureKey(KeyCodes.BUTTON_R1, true)
        assertTrue(s.captureKey(KeyCodes.BUTTON_MODE, false))
        assertFalse(s.captureKey(KeyCodes.BUTTON_MODE, true))   // released: the next MENU is the menu's again
    }

    @Test fun selectAndStartRefused() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF)
        s.captureKey(KeyCodes.BUTTON_SELECT, true)
        assertNull(s.capturingShortcut.value)
        assertEquals("None", s.row("Toggle FF").secondary)
        assertEquals("SELECT and START stay the game's", s.notice.value)
        s.captureKey(KeyCodes.BUTTON_SELECT, false)
        s.bind(ShortcutAction.TOGGLE_FF)
        s.captureKey(KeyCodes.BUTTON_MODE, true); s.captureKey(KeyCodes.BUTTON_START, true)
        assertEquals("None", s.row("Toggle FF").secondary)
        assertEquals("SELECT and START stay the game's", s.notice.value)
    }

    @Test fun duplicateMoves() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF); s.press(KeyCodes.BUTTON_R1)
        s.bind(ShortcutAction.HOLD_FF); s.press(KeyCodes.BUTTON_R1)
        assertEquals("None", s.row("Toggle FF").secondary)
        assertEquals("R1", s.row("Hold FF").secondary)
    }

    @Test fun cancel() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF); s.press(KeyCodes.BUTTON_R1)
        s.bind(ShortcutAction.TOGGLE_FF)
        s.onButton(NxButton.B)                                  // a touch B or the on-screen pad's
        assertNull(s.capturingShortcut.value)
        assertEquals("R1", s.row("Toggle FF").secondary)
        assertEquals(SettingsSection.SHORTCUTS, s.page().section)   // B cancelled, it did not leave the page
        s.bind(ShortcutAction.TOGGLE_FF)
        s.press(KeyCodes.BUTTON_B)                              // a controller's B
        assertEquals("R1", s.row("Toggle FF").secondary)
        s.bind(ShortcutAction.TOGGLE_FF)
        assertTrue(s.back())                                    // Android's Back
        assertNull(s.capturingShortcut.value)
        assertEquals(SettingsSection.SHORTCUTS, s.page().section)
    }

    @Test fun otherButtonsWaitWhileCapturing() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF)
        val sel = s.selection.value
        s.onButton(NxButton.DOWN); s.onButton(NxButton.MENU)
        assertEquals(sel, s.selection.value)
        assertEquals(ShortcutAction.TOGGLE_FF, s.capturingShortcut.value)
    }

    @Test fun clearWithX() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF); s.press(KeyCodes.BUTTON_R1)
        s.onButton(NxButton.X)
        assertEquals("None", s.row("Toggle FF").secondary)
    }

    @Test fun touchCycles() = runTest {
        val s = state()
        s.openShortcuts()
        s.select(s.page().rows.indexOfFirst { it.label == "Touch button 1" })
        s.onButton(NxButton.RIGHT)
        assertEquals("Toggle FF", s.row("Touch button 1").secondary)
        s.onButton(NxButton.RIGHT)
        assertEquals("Hold FF", s.row("Touch button 1").secondary)
        s.onButton(NxButton.LEFT); s.onButton(NxButton.LEFT)
        assertEquals("None", s.row("Touch button 1").secondary)
        assertEquals(listOf("None") + ShortcutAction.entries.map { it.label }, s.page().choices[s.selection.value.index]?.labels)
    }

    @Test fun clearAllEmptiesThePage() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF); s.press(KeyCodes.BUTTON_R1)
        s.select(s.page().rows.indexOfFirst { it.label == "Touch button 2" }); s.onButton(NxButton.RIGHT)
        s.select(s.page().rows.lastIndex); s.openSelected()
        assertTrue(s.page().rows.filter { it.kind != SettingsKind.RESET_DEFAULTS }.all { it.secondary == "None" })
    }

    @Test fun savedOnClose() = runTest {
        val settings = Settings()
        val s = state(settings)
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF); s.captureKey(110, true); s.captureKey(103, true)
        s.select(s.page().rows.indexOfFirst { it.label == "Touch button 2" }); s.onButton(NxButton.RIGHT)
        assertEquals("", settings.shortcuts)                    // not per change (L25)
        s.onButton(NxButton.B)
        assertEquals(SettingsSection.CONTROLS, s.page().section)
        assertEquals(
            ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to ShortcutBinding(110, 103)), mapOf(TouchSlot.TWO to ShortcutAction.TOGGLE_FF)),
            Shortcuts.decodeBase(settings.shortcuts),
        )
        s.openShortcuts()                                       // and read back
        assertEquals("MENU+R1", s.row("Toggle FF").secondary)
    }

    @Test fun storedBindingsShow() = runTest {
        val settings = Settings().apply { shortcuts = "hold_rewind=110+99;toggle_ff=109+99;touch_1=reset" }
        val s = state(settings)
        s.openShortcuts()
        assertEquals("MENU+X", s.row("Hold Rewind").secondary)
        assertEquals("None", s.row("Toggle FF").secondary)   // a SELECT combo from before MENU became the only modifier
        assertEquals("Reset Game", s.row("Touch button 1").secondary)
    }

    @Test fun capturingHints() = runTest {
        val s = state()
        s.openShortcuts()
        val i = s.page().rows.indexOfFirst { it.label == "Toggle FF" }
        assertEquals(listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Bind"), Hint(NxButton.X, "Clear")), settingsHints(s.page().rows, i))
        s.bind(ShortcutAction.TOGGLE_FF)
        assertEquals(listOf(Hint(NxButton.B, "Cancel")), settingsHints(s.page().rows, i))
        assertEquals("Hold MENU for a combo", settingsInfo(s.row("Toggle FF")).line)
    }

    // Touch only: A still starts a capture (a controller may be plugged in next), and the row says what it waits for.
    @Test fun touchOnlySaysConnectAController() = runTest {
        val s = state()
        s.onControllerConnected(false)
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF)
        assertEquals("Connect a controller to bind", settingsInfo(s.row("Toggle FF")).line)
    }

    @Test fun tappingAnotherRowEndsACapture() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF)
        s.select(s.page().rows.indexOfFirst { it.label == "Touch button 1" })
        assertNull(s.capturingShortcut.value)
        assertEquals("None", s.row("Toggle FF").secondary)
    }

    // Home (the launcher's Home press) leaves the page however it is reached: the wait ends, the draft is written, and
    // the controller's keys reach the menu again.
    @Test fun homeDuringACaptureEndsItAndSaves() = runTest {
        val settings = Settings()
        val s = state(settings)
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF); s.press(KeyCodes.BUTTON_R1)
        s.bind(ShortcutAction.HOLD_FF)
        s.goHome()
        assertIs<Screen.Systems>(s.screen.value)
        assertNull(s.capturingShortcut.value)
        assertEquals(mapOf(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, KeyCodes.BUTTON_R1)), Shortcuts.decodeBase(settings.shortcuts).bindings)
        assertFalse(s.captureKey(KeyCodes.BUTTON_MODE, true))
        val tab = assertIs<Screen.Systems>(s.screen.value).tab
        s.onButton(NxButton.R1)                                 // the menu takes the pad again: R1 switches tabs
        assertTrue(assertIs<Screen.Systems>(s.screen.value).tab != tab)
    }

    @Test fun leavingThePageEndsACapture() = runTest {
        val s = state()
        s.openShortcuts()
        s.bind(ShortcutAction.TOGGLE_FF)
        s.openSettings(SettingsSection.APPEARANCE)              // a two-pane category tap, say
        assertNull(s.capturingShortcut.value)
        assertFalse(s.captureKey(KeyCodes.BUTTON_R1, true))
    }
}
