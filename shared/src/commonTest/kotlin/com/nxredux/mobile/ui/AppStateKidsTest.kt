package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.kids.FlagSeen
import com.nxredux.mobile.kids.KidsController
import com.nxredux.mobile.kids.KidsFlag
import com.nxredux.mobile.kids.KidsMode
import com.nxredux.mobile.kids.KidsPlatform
import com.nxredux.mobile.kids.LocalNow
import com.nxredux.mobile.kids.PinPurpose
import com.nxredux.mobile.kids.PinResult
import com.nxredux.mobile.kids.UnlockChip
import com.nxredux.mobile.launcher.AppLauncher
import com.nxredux.mobile.launcher.InstalledApp
import androidx.compose.ui.graphics.ImageBitmap
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
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.StateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Kids Mode in AppState: the tiles screen while Locked, no Tools while Open, the PIN wait, the flag and the lock. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStateKidsTest {
    private class MemorySettings(override var treeRef: ContentRef? = ContentRef("Home")) : AppSettings {
        override var extraFolders: List<ExtraFolder> = emptyList()
        override var defaultTags: Map<String, String> = emptyMap()
        override var debugLog = false
        override var secondScreen = true
        override var pauseWhenUnfocused = true
        override var bottomScreen = com.nxredux.mobile.runner.BottomScreen.AUTO
        override var saveFormat = SaveFormat.DEFAULT
        override var stateFormat = StateFormat.DEFAULT
        override var useExtractedFileName = false
        override var padTheme: PadTheme = PadTheme.DEFAULT
        override var padSkin = false
        override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap()
        override var gameListStyle: MenuStyle = MenuStyle.LIST
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

        override var kidsEnabled = true
        override var kidsSchedule: String? = null   // the default: Mon–Thu locked, Fri–Sun 9:00–20:00
        override var kidsAllowedApps: Set<String> = emptySet()
        override var kidsUnlockUntil = 0L
        override var kidsFlagHome: String? = "Home"
        private var pin: String? = "123456"   // a fake: the real one keeps a salted hash
        override fun setKidsPin(pin: String) { this.pin = pin }
        override fun kidsPinMatches(pin: String) = pin == this.pin
        override val kidsPinSet get() = pin != null
        override fun clearKidsPin() { pin = null }
    }

    private class Runner : EmulatorRunner {
        val launched = mutableListOf<GameSession>()
        override suspend fun launch(session: GameSession, recent: com.nxredux.mobile.storage.Recent) { launched += session }
    }

    /** [inner], with [child] (the launch's "still there" check) held at [gate] while one is set. */
    private class GatedSource(private val inner: FakeRomsSource) : com.nxredux.mobile.library.RomsSource by inner {
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override suspend fun child(dir: ContentRef, name: String) = gate.let { it?.await(); inner.child(dir, name) }
    }

    private class Picker : FolderPicker {
        override fun pickHome() {}
        override fun pickExtra() {}
        override fun forget(ref: ContentRef) {}
    }

    private class Flag(var home: String? = "Home", var seen: FlagSeen = FlagSeen.PRESENT) : KidsFlag {
        override fun homeKey() = home
        override fun seen() = seen
        override fun write(): Boolean { seen = FlagSeen.PRESENT; return true }
        override fun delete() { seen = FlagSeen.MISSING }
    }

    /** A settable clock: [isoDay] and [minute] of the day, its epoch counted from a Monday midnight. */
    private class Platform(var isoDay: Int, var minute: Int, override val flag: Flag = Flag()) : KidsPlatform {
        var extraMs = 0L
        var locks = 0
        val epochMs get() = (isoDay - 1) * DAY + minute * 60_000L + extraMs
        override fun now() = LocalNow(epochMs, isoDay, minute * 60_000L + extraMs)
        override fun hasUsageAccess() = true
        override fun openUsageAccess() {}
        override fun canExactAlarms() = true
        override fun openExactAlarms() {}
        override fun reschedule(mode: KidsMode) {}
        override fun lockNow() { locks++ }
        /** The boot / clock receiver's state as last set; null: never set. */
        var startReceiver: Boolean? = null
        override fun setStartReceiver(enabled: Boolean) { startReceiver = enabled }
        fun at(day: Int, min: Int) { isoDay = day; minute = min; extraMs = 0 }
    }

    private val source = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms", "Collections"),
            "Home/Roms" to listOf("Game Boy (GB)"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
            "Home/Collections" to listOf("Mix.txt"),
        ),
        texts = mapOf("Home/Collections/Mix.txt" to "/Roms/Game Boy (GB)/Tetris.gb\n"),
    )

    private class Launcher(val apps: List<InstalledApp>) : AppLauncher {
        val launched = mutableListOf<String>()
        override suspend fun installedApps() = apps
        override fun label(id: String) = apps.firstOrNull { it.id == id }?.label
        override suspend fun icon(id: String): ImageBitmap? = null
        override fun launch(id: String): Boolean { launched += id; return true }
        override fun isHome() = false
        override fun setHome(on: Boolean) {}
    }

    private fun TestScope.state(
        settings: AppSettings, kids: KidsController?, launcher: AppLauncher? = null,
        roms: com.nxredux.mobile.library.RomsSource = source, runner: EmulatorRunner = Runner(),
    ) = AppState(
        AppDeps(settings, roms, runner, Picker(), compute = UnconfinedTestDispatcher(testScheduler), launcher = launcher),
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        kids,
    )

    private fun tabsOf(st: AppState) = assertIs<Screen.Systems>(st.screen.value).tabs.map { it.category }

    @Test fun lockedShowsKidsHome() = runTest {
        val settings = MemorySettings()
        val st = state(settings, KidsController(settings, Platform(MON, 10 * 60)))
        st.load()
        assertSame(Screen.KidsHome, st.screen.value)
        st.goHome()                                                   // Home while locked: still the tiles screen
        assertSame(Screen.KidsHome, st.screen.value)
    }

    @Test fun openHidesToolsTab() = runTest {
        val settings = MemorySettings()
        val off = state(MemorySettings().apply { kidsEnabled = false }, null)
        off.load()
        val all = tabsOf(off)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.GAMES, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS), all)
        val kids = KidsController(settings, Platform(SAT, 10 * 60))
        assertIs<KidsMode.Open>(kids.mode.value)
        val st = state(settings, kids)
        st.load()
        assertEquals(all - MenuCategory.TOOLS, tabsOf(st))
        assertFalse(assertIs<Screen.Systems>(st.screen.value).hidden.contains(MenuCategory.TOOLS))   // not offered as a hidden tab either
        st.openHiddenTab(MenuCategory.TOOLS)
        assertEquals(all - MenuCategory.TOOLS, tabsOf(st))
    }

    /** A play window: the menu shows Games beside Consoles, no Tools; the Games tab lists every game. */
    @Test fun a_play_window_shows_the_games_tab_beside_consoles() = runTest {
        val settings = MemorySettings()
        val kids = KidsController(settings, Platform(SAT, 10 * 60))
        assertIs<KidsMode.Open>(kids.mode.value)
        val roms = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"),
                "Home/Roms" to listOf("Game Boy (GB)", "Game Boy Advance (GBA)"),
                "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Alpha.gb"),
                "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
            ),
        )
        val st = state(settings, kids, roms = roms)
        st.load()
        val tabs = tabsOf(st)
        assertTrue(MenuCategory.GAMES in tabs)
        assertTrue(MenuCategory.CONSOLES in tabs)
        assertFalse(MenuCategory.TOOLS in tabs)
        assertEquals(tabs.indexOf(MenuCategory.HOME) + 1, tabs.indexOf(MenuCategory.GAMES))
        st.selectTab(tabs.indexOf(MenuCategory.GAMES))
        val games = assertIs<Screen.Games>(st.screen.value)
        assertEquals(ListSource.AllGames, games.source)
        assertEquals(listOf("Alpha", "Mario", "Tetris"), games.entries.map { it.game.shownName })   // every playable game, both consoles
    }

    /** Locked: the Kids tiles screen, not the menu: neither a tab tap nor MENU's hidden-tab item reaches the Games tab. */
    @Test fun kids_locked_shows_no_games_tab() = runTest {
        val (st) = lockedState()
        st.selectTab(1)                                               // Games' place in the tab row
        assertSame(Screen.KidsHome, st.screen.value)
        val (hidden) = lockedState(MemorySettings().apply { showGamesTab = false })
        hidden.openHiddenTab(MenuCategory.GAMES)                      // MENU's "Games" item, with the tab hidden
        assertSame(Screen.KidsHome, hidden.screen.value)
    }

    @Test fun parentShowsTools() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        assertSame(Screen.KidsHome, st.screen.value)
        kids.enterParent(platform.epochMs)
        assertEquals(KidsMode.Parent, kids.mode.value)
        assertTrue(MenuCategory.TOOLS in tabsOf(st))
        st.openSettingsMenu()
        assertIs<Screen.SettingsMenu>(st.screen.value)
    }

    /** The parent bar's Back to Kids Mode: Parent ends; Monday locked, so the tiles. */
    @Test fun parentBarBackToKidsModeLocks() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        kids.enterParent(platform.epochMs)
        st.openSettingsMenu()
        st.kidsEndParent()
        assertIs<KidsMode.Locked>(kids.mode.value)
        assertSame(Screen.KidsHome, st.screen.value)
    }

    /** In a play window it is the menu without Tools; a timed unlock the kid was given stays. */
    @Test fun parentBarBackToKidsModeKeepsTimedUnlock() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        settings.kidsUnlockUntil = platform.epochMs + 3_600_000L          // an hour given earlier
        kids.enterParent(platform.epochMs)
        st.kidsEndParent()
        assertIs<KidsMode.Open>(kids.mode.value)
        assertFalse(MenuCategory.TOOLS in tabsOf(st))
    }

    private val kidApps = listOf(InstalledApp("a", "Alpha"), InstalledApp("b", "Beta"), InstalledApp("c", "Gamma"))

    /** The Apps tab's app ids, or null with no Apps tab. */
    private fun appsTab(st: AppState): List<String?>? =
        assertIs<Screen.Systems>(st.screen.value).tabs.firstOrNull { it.category == MenuCategory.APPS }?.rows?.map { (it as MainRow.Tool).row.appId }

    /** Games open: an Apps tab with the allowed apps installed (A to Z), no Tools; A on one opens it. */
    @Test fun openShowsAppsTab() = runTest {
        val settings = MemorySettings().apply { kidsAllowedApps = setOf("c", "a", "gone") }
        val launcher = Launcher(kidApps)
        val st = state(settings, KidsController(settings, Platform(SAT, 10 * 60)), launcher)
        st.load(); st.onForeground()
        assertEquals(listOf("a", "c"), appsTab(st))
        assertFalse(MenuCategory.TOOLS in tabsOf(st))
        st.selectTab(tabsOf(st).indexOf(MenuCategory.APPS))
        assertEquals(MenuCategory.APPS, assertIs<Screen.Systems>(st.screen.value).category)
        st.select(1); st.onButton(NxButton.A)
        assertEquals(listOf("c"), launcher.launched)
    }

    @Test fun noAppsTabWhenOffOrLocked() = runTest {
        val off = MemorySettings().apply { kidsEnabled = false; kidsAllowedApps = setOf("a") }
        val st = state(off, KidsController(off, Platform(SAT, 10 * 60)), Launcher(kidApps))
        st.load(); st.onForeground()
        assertNull(appsTab(st))
        val (locked, kids, platform) = lockedState(MemorySettings().apply { kidsAllowedApps = setOf("a") }, launcher = Launcher(kidApps))
        locked.onForeground()
        assertSame(Screen.KidsHome, locked.screen.value)                   // locked: the tiles, no menu and no Apps tab
        kids.enterParent(platform.epochMs)                                 // a parent: Apps beside Tools
        locked.onForeground()
        assertEquals(listOf("a"), appsTab(locked))
        val tabs = tabsOf(locked)
        assertEquals(tabs.indexOf(MenuCategory.TOOLS) - 1, tabs.indexOf(MenuCategory.APPS))
    }

    /** Games open: the main menu's lock opens the PIN; Back returns to the menu; the right PIN opens the sheet. */
    @Test fun lockIconWhileOpenAsksThePin() = runTest {
        val settings = MemorySettings()
        val kids = KidsController(settings, Platform(SAT, 10 * 60))
        val st = state(settings, kids)
        st.load()
        st.kidsParentPressed()
        assertEquals(PinPurpose.UNLOCK, assertIs<Screen.KidsPin>(st.screen.value).purpose)
        st.onButton(NxButton.B)
        assertIs<Screen.Systems>(st.screen.value)
        st.kidsParentPressed(); st.typePin("123456")
        assertSame(Screen.KidsUnlock, st.screen.value)
        st.onButton(NxButton.B)                                            // the sheet without a choice: the menu again
        assertIs<Screen.Systems>(st.screen.value)
    }

    /** The lock on the Games tab asks the PIN as on any other tab. */
    @Test fun lockIconOnTheGamesTabAsksThePin() = runTest {
        val settings = MemorySettings()
        val kids = KidsController(settings, Platform(SAT, 10 * 60))
        val st = state(settings, kids)
        st.load()
        st.selectTab(tabsOf(st).indexOf(MenuCategory.GAMES))
        assertTrue(assertIs<Screen.Games>(st.screen.value).isTab)
        st.kidsParentPressed()
        assertEquals(PinPurpose.UNLOCK, assertIs<Screen.KidsPin>(st.screen.value).purpose)
        st.onButton(NxButton.B)
        assertTrue(assertIs<Screen.Games>(st.screen.value).isTab)             // back on the tab
    }

    @Test fun lockIconDoesNothingWhenLockedOrOff() = runTest {
        val (st) = lockedState()
        st.kidsParentPressed()
        assertSame(Screen.KidsHome, st.screen.value)
        val off = MemorySettings().apply { kidsEnabled = false }
        val st2 = state(off, KidsController(off, Platform(SAT, 10 * 60)))
        st2.load()
        st2.kidsParentPressed()
        assertIs<Screen.Systems>(st2.screen.value)
    }

    /** Kids Mode on: no menu offers Pin (Home's pins are hidden), Remove from Tools stays. */
    @Test fun noPinItemsWhileKidsOn() {
        assertEquals(listOf(ContextKind.REMOVE_FROM_TOOLS), ContextMenus.toolItems(pinned = false, app = true, pin = false).map { it.kind })
        assertTrue(ContextMenus.toolItems(pinned = true, pin = false).isEmpty())
        assertEquals(listOf(ContextKind.ADD_TO_TOOLS), ContextMenus.drawerItems(inTools = false, pinned = false, pin = false).map { it.kind })
    }

    @Test fun settingsUnreachableWhileOpen() = runTest {
        val settings = MemorySettings()
        val st = state(settings, KidsController(settings, Platform(SAT, 10 * 60)))
        st.load()
        val before = st.screen.value
        assertIs<Screen.Systems>(before)
        st.openSettingsMenu()
        assertSame(before, st.screen.value)
        st.openSettings(SettingsSection.LIBRARY)
        assertSame(before, st.screen.value)
        st.openTools()
        assertSame(before, st.screen.value)
    }

    @Test fun parentIdleFiveMinutesLocks() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        val t0 = platform.epochMs
        kids.enterParent(t0)
        st.openSettingsMenu()
        platform.extraMs = 4 * 60_000L + 59_000L
        kids.tick(t0 + platform.extraMs)
        assertEquals(KidsMode.Parent, kids.mode.value)
        assertIs<Screen.SettingsMenu>(st.screen.value)
        platform.extraMs = 5 * 60_000L
        kids.tick(t0 + platform.extraMs)
        assertIs<KidsMode.Locked>(kids.mode.value)                     // Monday: the schedule says locked
        assertSame(Screen.KidsHome, st.screen.value)
    }

    @Test fun parentInputRestartsTheIdleTime() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        val t0 = platform.epochMs
        kids.enterParent(t0)
        platform.extraMs = 4 * 60_000L
        kids.parentInput()                                              // the platform's clock, as tick's
        kids.tick(t0 + 8 * 60_000L)
        assertEquals(KidsMode.Parent, kids.mode.value)
        kids.tick(t0 + 9 * 60_000L)
        assertIs<KidsMode.Locked>(kids.mode.value)
    }

    @Test fun wrongPinFiveTimesWaits30s() = runTest {
        val settings = MemorySettings()
        val kids = KidsController(settings, Platform(MON, 10 * 60))
        val t = 1_000_000L
        repeat(4) { assertEquals(PinResult.WRONG, kids.tryPin("000000", t + it)) }
        assertEquals(PinResult.WAIT, kids.tryPin("000000", t + 4))
        assertEquals(t + 4 + 30_000L, kids.waitUntil)
        assertEquals(PinResult.WAIT, kids.tryPin("123456", t + 4 + 29_999L))   // even the right one waits
        assertEquals(PinResult.OK, kids.tryPin("123456", t + 4 + 30_000L))
        assertEquals(PinResult.WRONG, kids.tryPin("000000", t + 40_000L))        // the count started again
    }

    @Test fun flagMissingTurnsOff() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        assertSame(Screen.KidsHome, st.screen.value)
        platform.flag.seen = FlagSeen.MISSING
        kids.refresh()
        assertFalse(settings.kidsEnabled)
        assertEquals(KidsMode.Off, kids.mode.value)
        assertTrue(MenuCategory.TOOLS in tabsOf(st))                  // the full menu again
        assertFalse(settings.kidsPinSet)                               // the forgotten PIN goes: switching on asks for a new one
        assertEquals(false, platform.startReceiver)                    // no boot or clock start while off
    }

    /** Switching on goes straight to the schedule's mode, never the parent's full menu: Monday locked, Saturday open. */
    @Test fun turnOnFollowsTheSchedule() = runTest {
        val mon = KidsController(MemorySettings().apply { kidsEnabled = false }, Platform(MON, 10 * 60))
        mon.turnOn()
        assertIs<KidsMode.Locked>(mon.mode.value)
        val sat = KidsController(MemorySettings().apply { kidsEnabled = false }, Platform(SAT, 10 * 60))
        sat.turnOn()
        assertIs<KidsMode.Open>(sat.mode.value)
    }

    @Test fun startReceiverFollowsOnAndOff() = runTest {
        val settings = MemorySettings().apply { kidsEnabled = false }
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        kids.turnOn()
        assertEquals(true, platform.startReceiver)
        kids.turnOff()
        assertEquals(false, platform.startReceiver)
        assertTrue(settings.kidsPinSet)                                // a plain switch-off keeps the PIN
    }

    /** A launch already past its first check when the lock comes never reaches the runner. */
    @Test fun launchInFlightAtTheLockNeverStarts() = runTest {
        val settings = MemorySettings()
        val platform = Platform(SAT, 19 * 60 + 59)
        val kids = KidsController(settings, platform)
        val roms = GatedSource(source)
        val runner = Runner()
        val st = state(settings, kids, roms = roms, runner = runner)
        st.load()
        val sys = assertIs<Screen.Systems>(st.screen.value).tabs.flatMap { it.rows }
            .flatMap { (it as? MainRow.System)?.systems.orEmpty() }.first { it.tag == "GB" }
        st.openSystem(sys)
        val game = assertIs<Screen.Games>(st.screen.value).entries.single().game
        roms.gate = kotlinx.coroutines.CompletableDeferred()
        val job = st.play(game)
        assertTrue(job != null && job.isActive)                        // in flight, held at its file check
        platform.at(SAT, 20 * 60)
        kids.tick(platform.epochMs)
        assertIs<KidsMode.Locked>(kids.mode.value)
        roms.gate!!.complete(Unit)
        job.join()
        assertTrue(runner.launched.isEmpty())
    }

    /** Away from the menu (a game, another app) Parent mode ends at its last input + 5 min ([KidsController.parentIdleAtMs]). */
    @Test fun parentIdleAtFollowsTheLastInput() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        assertEquals(null, kids.parentIdleAtMs)
        val t0 = platform.epochMs
        kids.enterParent(t0)
        assertEquals(t0 + KidsController.PARENT_IDLE_MS, kids.parentIdleAtMs)
        platform.extraMs = 90_000L
        kids.parentInput()                                             // a game's reported input
        assertEquals(t0 + 90_000L + KidsController.PARENT_IDLE_MS, kids.parentIdleAtMs)
        kids.tick(t0 + 90_000L + KidsController.PARENT_IDLE_MS)       // the idle alarm
        assertIs<KidsMode.Locked>(kids.mode.value)
        assertEquals(1, platform.locks)                                // the parent's game quits as a kid's lock does
        assertEquals(null, kids.parentIdleAtMs)
    }

    /** The unlock chips: a 2 x 2 grid on a narrow screen (no word wraps mid-way at 360 dp), one row of four from 480 dp. */
    @Test fun unlockChipsGridOnNarrowScreens() {
        assertEquals(2, kidsUnlockChipColumns(360f))
        assertEquals(2, kidsUnlockChipColumns(479f))
        assertEquals(4, kidsUnlockChipColumns(480f))
        // 2 x 2: the d-pad moves in the grid, DOWN from the bottom row to Open full menu, UP from it to the first chip.
        assertEquals(1, kidsUnlockStep(0, NxButton.RIGHT, columns = 2))
        assertEquals(1, kidsUnlockStep(1, NxButton.RIGHT, columns = 2))
        assertEquals(2, kidsUnlockStep(0, NxButton.DOWN, columns = 2))
        assertEquals(KIDS_UNLOCK_FULL_MENU, kidsUnlockStep(3, NxButton.DOWN, columns = 2))
        assertEquals(1, kidsUnlockStep(3, NxButton.UP, columns = 2))
        assertEquals(2, kidsUnlockStep(3, NxButton.LEFT, columns = 2))
        assertEquals(2, kidsUnlockStep(2, NxButton.LEFT, columns = 2))
        assertEquals(0, kidsUnlockStep(KIDS_UNLOCK_FULL_MENU, NxButton.UP, columns = 2))
        // One row: RIGHT steps along, DOWN goes to Open full menu.
        assertEquals(3, kidsUnlockStep(2, NxButton.RIGHT))
        assertEquals(KIDS_UNLOCK_FULL_MENU, kidsUnlockStep(1, NxButton.DOWN))
    }

    @Test fun pinNeverInToString() {
        val s = Screen.KidsPin(PinPurpose.UNLOCK, typed = "123456").toString()
        assertFalse("123456" in s, s)
        assertTrue("KidsPin" in s)
    }

    /** A picker or a row's description open over a parent's page goes when Kids Mode locks. */
    @Test fun lockClosesOpenOverlays() = runTest {
        val (st, kids, platform) = lockedState()
        kids.enterParent(platform.epochMs)
        st.openSettings(SettingsSection.KIDS)
        val page = assertIs<Screen.Settings>(st.screen.value)
        val day = page.rows.indexOfFirst { it.kind == SettingsKind.KIDS_DAY && it.day == SAT }
        st.select(day); st.onButton(NxButton.A)                        // Saturday's play time
        assertIs<Screen.KidsDayEdit>(st.screen.value)
        st.kidsDayRow(1)                                               // From: its picker
        assertTrue(st.chooser.value != null)
        kids.lock()
        assertSame(Screen.KidsHome, st.screen.value)
        assertEquals(null, st.chooser.value)
        assertEquals(null, st.settingInfo.value)
    }

    @Test fun flagUnreadableOrMovedKeepsKidsMode() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60, Flag(home = null, seen = FlagSeen.UNREADABLE))
        val kids = KidsController(settings, platform)
        kids.refresh()
        assertTrue(settings.kidsEnabled)
        platform.flag.home = "Card"; platform.flag.seen = FlagSeen.MISSING
        kids.refresh()                                                 // another home: a fresh flag there
        assertTrue(settings.kidsEnabled)
        assertEquals("Card", settings.kidsFlagHome)
        assertEquals(FlagSeen.PRESENT, platform.flag.seen)
    }

    @Test fun transitionToLockedCallsLockNow() = runTest {
        val settings = MemorySettings()
        val platform = Platform(SAT, 19 * 60 + 59)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        assertIs<KidsMode.Open>(kids.mode.value)
        assertIs<Screen.Systems>(st.screen.value)
        platform.at(SAT, 20 * 60)
        kids.tick(platform.epochMs)
        assertIs<KidsMode.Locked>(kids.mode.value)
        assertEquals(1, platform.locks)
        assertSame(Screen.KidsHome, st.screen.value)
        kids.tick(platform.epochMs + 1000); kids.refresh()             // still locked: no second lock
        assertEquals(1, platform.locks)
    }

    @Test fun alreadyLockedAtStartNeverCallsLockNow() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        kids.refresh(); kids.tick(platform.epochMs)
        assertEquals(0, platform.locks)
    }

    @Test fun unlockOpensAndLockCloses() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        kids.unlock(com.nxredux.mobile.kids.UnlockChip.MIN30)
        assertEquals(platform.epochMs + 30 * 60_000L, settings.kidsUnlockUntil)
        assertEquals(KidsMode.Open(platform.epochMs + 30 * 60_000L), kids.mode.value)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.GAMES, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS), tabsOf(st))
        kids.lock()
        assertEquals(0L, settings.kidsUnlockUntil)
        assertIs<KidsMode.Locked>(kids.mode.value)
        assertEquals(1, platform.locks)
        assertSame(Screen.KidsHome, st.screen.value)
    }

    /** Screen off: Parent mode ends, a timed unlock the parent gave stays. */
    @Test fun endParentKeepsTheTimedUnlock() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        kids.unlock(com.nxredux.mobile.kids.UnlockChip.HOUR1)
        val until = platform.epochMs + 60 * 60_000L
        kids.enterParent(platform.epochMs)
        assertEquals(KidsMode.Parent, kids.mode.value)
        kids.endParent()
        assertEquals(until, settings.kidsUnlockUntil)
        assertEquals(KidsMode.Open(until), kids.mode.value)
        assertEquals(0, platform.locks)
    }

    /** Screen off in Parent on a locked day: Locked, and the lock runs as for any change to Locked. */
    @Test fun endParentOnALockedDayLocks() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        kids.enterParent(platform.epochMs)
        st.openSettingsMenu()
        kids.endParent()
        assertIs<KidsMode.Locked>(kids.mode.value)
        assertEquals(1, platform.locks)
        assertSame(Screen.KidsHome, st.screen.value)
        kids.endParent()                                              // not in Parent: nothing more
        assertEquals(1, platform.locks)
    }

    @Test fun kidsWarningShowsTheMenuNotice() = runTest {
        val settings = MemorySettings()
        val st = state(settings, KidsController(settings, Platform(SAT, 19 * 60 + 50)))
        st.load()
        st.kidsShowWarning()
        assertEquals("Games close in 10 minutes. Save your game now.", st.notice.value)
    }

    @Test fun lockedSecondScreenShowsTheBackdrop() {
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.KidsHome, 0))
    }

    // --- The kid-facing screens: the tiles, the PIN pad and the unlock sheet ---

    /** Locked on a Monday morning, on the tiles screen. */
    private fun TestScope.lockedState(settings: MemorySettings = MemorySettings(), platform: Platform = Platform(MON, 10 * 60), launcher: AppLauncher? = null): Triple<AppState, KidsController, Platform> {
        val kids = KidsController(settings, platform)
        val st = state(settings, kids, launcher)
        st.load()
        assertSame(Screen.KidsHome, st.screen.value)
        return Triple(st, kids, platform)
    }

    private fun AppState.typePin(pin: String) { pin.forEach { kidsPinDigit(it.digitToInt()) }; kidsPinEnter() }

    @Test fun lockButtonOpensPin() = runTest {
        val (st) = lockedState()
        st.kidsLockPressed()
        assertEquals(Screen.KidsPin(PinPurpose.UNLOCK), st.screen.value)
        st.kidsPinDigit(1); st.kidsPinDigit(2)
        assertEquals("12", assertIs<Screen.KidsPin>(st.screen.value).typed)
        st.kidsPinBack()
        assertEquals("1", assertIs<Screen.KidsPin>(st.screen.value).typed)
        st.kidsPinBack(); st.kidsPinBack()                             // Back with nothing typed: the tiles again
        assertSame(Screen.KidsHome, st.screen.value)
    }

    @Test fun pinTakesSixDigitsAndEnter() = runTest {
        val (st) = lockedState()
        st.kidsLockPressed()
        "12345".forEach { st.kidsPinDigit(it.digitToInt()) }
        st.kidsPinEnter()                                              // five digits: Enter does nothing yet
        assertIs<Screen.KidsPin>(st.screen.value)
        "67".forEach { st.kidsPinDigit(it.digitToInt()) }
        assertEquals("123456", assertIs<Screen.KidsPin>(st.screen.value).typed)   // a seventh digit is ignored
        st.kidsPinBack(); st.kidsPinEnter()                            // Enter with five digits does nothing
        assertEquals("12345", assertIs<Screen.KidsPin>(st.screen.value).typed)
    }

    @Test fun rightPinOpensUnlockSheet() = runTest {
        val (st) = lockedState()
        st.kidsLockPressed()
        st.typePin("000000")
        assertEquals(Screen.KidsPin(PinPurpose.UNLOCK, typed = "", wrong = 1), st.screen.value)   // wrong: cleared and counted
        st.typePin("123456")
        assertSame(Screen.KidsUnlock, st.screen.value)
    }

    @Test fun fifthWrongPinWaitsAndIgnoresDigits() = runTest {
        val (st, kids, platform) = lockedState()
        st.kidsLockPressed()
        repeat(5) { st.typePin("000000") }
        val s = assertIs<Screen.KidsPin>(st.screen.value)
        assertEquals(platform.epochMs + 30_000L, s.waitUntil)
        assertEquals(kids.waitUntil, s.waitUntil)
        st.kidsPinDigit(1)
        assertEquals("", assertIs<Screen.KidsPin>(st.screen.value).typed)   // no typing during the wait
        st.back(); st.kidsLockPressed()                                // out and in again: the wait stays
        assertEquals(kids.waitUntil, assertIs<Screen.KidsPin>(st.screen.value).waitUntil)
        platform.extraMs = 30_000L
        st.typePin("123456")
        assertSame(Screen.KidsUnlock, st.screen.value)
    }

    @Test fun chipUnlocksAndReturnsToMenu() = runTest {
        val settings = MemorySettings()
        val (st, kids, platform) = lockedState(settings)
        st.kidsLockPressed(); st.typePin("123456")
        st.kidsUnlockChip(UnlockChip.HOUR1)
        assertEquals(KidsMode.Open(platform.epochMs + 60 * 60_000L), kids.mode.value)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.GAMES, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS), tabsOf(st))   // no Tools
    }

    @Test fun unlockSheetActionsNeedTheSheet() = runTest {
        val (st, kids) = lockedState()
        st.kidsUnlockChip(UnlockChip.TODAY)                            // from the tiles, without the PIN: nothing
        st.kidsOpenFullMenu()
        st.kidsOpenSettings()
        assertIs<KidsMode.Locked>(kids.mode.value)
        assertSame(Screen.KidsHome, st.screen.value)
    }

    @Test fun fullMenuEntersParent() = runTest {
        val (st, kids) = lockedState()
        st.kidsLockPressed(); st.typePin("123456")
        st.kidsOpenFullMenu()
        assertEquals(KidsMode.Parent, kids.mode.value)
        assertTrue(MenuCategory.TOOLS in tabsOf(st))
    }

    @Test fun lockRowFirstInToolsWhileParent() = runTest {
        val (st, kids) = lockedState()
        st.kidsLockPressed(); st.typePin("123456")
        st.kidsOpenFullMenu()
        st.selectTab(tabsOf(st).indexOf(MenuCategory.TOOLS))
        val rows = assertIs<Screen.Systems>(st.screen.value).rows
        val lock = assertIs<MainRow.Tool>(rows.first()).row
        assertEquals(ToolKind.KIDS_LOCK, lock.kind)
        assertEquals("Lock Kids Mode", lock.label)
        assertEquals(1, rows.count { (it as? MainRow.Tool)?.row?.kind == ToolKind.KIDS_LOCK })
        st.select(0); st.openSelected()
        assertIs<KidsMode.Locked>(kids.mode.value)
        assertSame(Screen.KidsHome, st.screen.value)
    }

    @Test fun noLockRowWhenKidsModeIsOff() = runTest {
        val st = state(MemorySettings().apply { kidsEnabled = false }, null)
        st.load()
        st.selectTab(tabsOf(st).indexOf(MenuCategory.TOOLS))
        val rows = assertIs<Screen.Systems>(st.screen.value).rows
        assertTrue(rows.none { (it as? MainRow.Tool)?.row?.kind == ToolKind.KIDS_LOCK })
    }

    @Test fun kidsSettingsOpensSettingsInParent() = runTest {
        val (st, kids) = lockedState()
        st.kidsLockPressed(); st.typePin("123456")
        st.kidsOpenSettings()
        assertEquals(KidsMode.Parent, kids.mode.value)
        assertEquals(SettingsSection.KIDS, assertIs<Screen.Settings>(st.screen.value).section)   // the Kids Mode page
    }

    @Test fun kidsOpenAppLaunchesOnlyAllowed() = runTest {
        val launcher = Launcher(listOf(InstalledApp("a", "Alpha"), InstalledApp("b", "Beta"), InstalledApp("c", "Gamma")))
        val settings = MemorySettings().apply { kidsAllowedApps = setOf("c", "a", "gone") }
        val (st) = lockedState(settings, launcher = launcher)
        assertEquals(listOf(KidsTile("a", "Alpha"), KidsTile("c", "Gamma")), st.kidsTiles.value)   // installed and allowed, by label
        assertEquals(3, st.selection.value.count)                      // the tiles and the lock
        st.kidsOpenApp("b")
        assertEquals(emptyList(), launcher.launched)
        st.kidsOpenApp("a")
        assertEquals(listOf("a"), launcher.launched)
    }

    @Test fun backOnKidsScreensNeverLeaves() = runTest {
        val (st) = lockedState()
        assertTrue(st.back())                                          // consumed: the activity never finishes
        assertSame(Screen.KidsHome, st.screen.value)
        st.kidsLockPressed()
        assertTrue(st.back())
        assertSame(Screen.KidsHome, st.screen.value)
        st.kidsLockPressed(); st.typePin("123456")
        assertTrue(st.back())
        assertSame(Screen.KidsHome, st.screen.value)
    }

    /** A tapped key takes the highlight, as the d-pad's would. */
    @Test fun tapHighlightsTheKey() = runTest {
        val (st) = lockedState()
        st.kidsLockPressed()
        st.kidsPinTap(PinKey.Digit(5))
        assertEquals(KIDS_PIN_KEYS.indexOf(PinKey.Digit(5)), st.selection.value.index)
        assertEquals("5", assertIs<Screen.KidsPin>(st.screen.value).typed)
        st.kidsPinTap(PinKey.Digit(0))
        assertEquals(KIDS_PIN_KEYS.indexOf(PinKey.Digit(0)), st.selection.value.index)
        st.kidsPinTap(PinKey.Back)
        assertEquals(KIDS_PIN_KEYS.indexOf(PinKey.Back), st.selection.value.index)
        assertEquals("5", assertIs<Screen.KidsPin>(st.screen.value).typed)
    }

    @Test fun padDrivesThePinPad() = runTest {
        val (st) = lockedState()
        st.onButton(NxButton.MENU)                                     // the lock's button
        assertIs<Screen.KidsPin>(st.screen.value)
        st.onButton(NxButton.A)                                        // the first key: 1
        st.onButton(NxButton.RIGHT); st.onButton(NxButton.A)           // 2
        st.onButton(NxButton.RIGHT); st.onButton(NxButton.A)           // 3
        st.onButton(NxButton.DOWN); st.onButton(NxButton.LEFT); st.onButton(NxButton.LEFT); st.onButton(NxButton.A)   // 4
        st.onButton(NxButton.RIGHT); st.onButton(NxButton.A)           // 5
        st.onButton(NxButton.RIGHT); st.onButton(NxButton.A)           // 6
        assertEquals("123456", assertIs<Screen.KidsPin>(st.screen.value).typed)
        st.onButton(NxButton.START)                                    // Enter
        assertSame(Screen.KidsUnlock, st.screen.value)
        st.onButton(NxButton.B)
        assertSame(Screen.KidsHome, st.screen.value)
    }

    @Test fun touchRestartsParentIdle() = runTest {
        val (st, kids, platform) = lockedState()
        st.kidsLockPressed(); st.typePin("123456"); st.kidsOpenFullMenu()
        platform.extraMs = 4 * 60_000L
        st.onTouch()
        platform.extraMs = 8 * 60_000L
        kids.tick(platform.epochMs)
        assertEquals(KidsMode.Parent, kids.mode.value)
    }

    /** The screen going off closes the unlock sheet: a kid waking the phone finds the tiles, not Kids Mode settings. */
    @Test fun screenOffClosesTheUnlockSheet() = runTest {
        val (st, kids) = lockedState()
        st.kidsLockPressed(); st.typePin("123456")
        assertSame(Screen.KidsUnlock, st.screen.value)
        kids.screenOff()
        assertSame(Screen.KidsHome, st.screen.value)
        assertIs<KidsMode.Locked>(kids.mode.value)
    }

    @Test fun screenOffClosesAHalfTypedUnlockPin() = runTest {
        val (st, kids) = lockedState()
        st.kidsLockPressed(); st.kidsPinDigit(1); st.kidsPinDigit(2)
        kids.screenOff()
        assertSame(Screen.KidsHome, st.screen.value)
    }

    /** Open mode's lock icon: the sheet over the menu goes when the app leaves the front, back to the kid's menu. */
    @Test fun backgroundClosesTheUnlockSheetOverTheMenu() = runTest {
        val settings = MemorySettings()
        val kids = KidsController(settings, Platform(SAT, 10 * 60))
        val st = state(settings, kids)
        st.load()
        st.kidsParentPressed(); st.typePin("123456")
        assertSame(Screen.KidsUnlock, st.screen.value)
        st.onBackground()
        assertIs<Screen.Systems>(st.screen.value)
        assertIs<KidsMode.Open>(kids.mode.value)
    }

    @Test fun screenOffLeavesOtherScreensAlone() = runTest {
        val (st, kids) = lockedState()
        kids.screenOff()
        assertSame(Screen.KidsHome, st.screen.value)
        st.kidsLockPressed(); st.typePin("123456"); st.kidsOpenFullMenu()
        val menu = st.screen.value
        assertIs<Screen.Systems>(menu)
        st.onBackground()
        assertSame(menu, st.screen.value)
    }

    /** The sheet left alone closes after [KIDS_UNLOCK_IDLE_MS]; a press on it starts that time again. */
    @Test fun unlockSheetClosesWhenIdle() = runTest {
        val (st) = lockedState()
        st.kidsLockPressed(); st.typePin("123456")
        testScheduler.advanceTimeBy(KIDS_UNLOCK_IDLE_MS - 1_000L)
        assertSame(Screen.KidsUnlock, st.screen.value)
        st.onButton(NxButton.RIGHT)
        testScheduler.advanceTimeBy(KIDS_UNLOCK_IDLE_MS - 1_000L)
        assertSame(Screen.KidsUnlock, st.screen.value)
        testScheduler.advanceTimeBy(2_000L)
        assertSame(Screen.KidsHome, st.screen.value)
    }

    /** A kid's MENU on a game in a play window: play items only, nothing that edits the library or the game's setup. */
    @Test fun gameMenuWhileOpenOffersOnlyPlayItems() = runTest {
        val settings = MemorySettings()
        val kids = KidsController(settings, Platform(SAT, 10 * 60))
        val st = state(settings, kids)
        st.load()
        val sys = assertIs<Screen.Systems>(st.screen.value).tabs.flatMap { it.rows }
            .flatMap { (it as? MainRow.System)?.systems.orEmpty() }.first { it.tag == "GB" }
        st.openSystem(sys)
        st.openContextMenu(0)
        val kinds = assertIs<Screen.Games>(st.screen.value).menu!!.items.map { it.kind }
        val parentOnly = setOf(
            ContextKind.HIDE, ContextKind.RENAME, ContextKind.ADD_TO_COLLECTION, ContextKind.GAME_SETTINGS, ContextKind.EMULATOR,
            ContextKind.CONSOLE, ContextKind.FETCH_ART, ContextKind.CHANGE_ART, ContextKind.REMOVE_FROM_COLLECTION,
        )
        assertTrue(kinds.none { it in parentOnly }, "$kinds")
        assertTrue(ContextKind.PIN in kinds || ContextKind.UNPIN in kinds)
    }

    @Test fun gameMenuInParentModeOffersEverything() = runTest {
        val settings = MemorySettings()
        val platform = Platform(SAT, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        kids.enterParent(platform.epochMs)
        val sys = assertIs<Screen.Systems>(st.screen.value).tabs.flatMap { it.rows }
            .flatMap { (it as? MainRow.System)?.systems.orEmpty() }.first { it.tag == "GB" }
        st.openSystem(sys)
        st.openContextMenu(0)
        val kinds = assertIs<Screen.Games>(st.screen.value).menu!!.items.map { it.kind }
        assertTrue(ContextKind.HIDE in kinds && ContextKind.RENAME in kinds && ContextKind.GAME_SETTINGS in kinds, "$kinds")
    }

    /** A Rename prompt a parent left open goes when Parent mode ends under it, before its A could rename anything. */
    @Test fun parentEndingClosesAnOpenRenamePrompt() = runTest {
        val settings = MemorySettings()
        val platform = Platform(SAT, 10 * 60)
        val kids = KidsController(settings, platform)
        val st = state(settings, kids)
        st.load()
        kids.enterParent(platform.epochMs)
        val sys = assertIs<Screen.Systems>(st.screen.value).tabs.flatMap { it.rows }
            .flatMap { (it as? MainRow.System)?.systems.orEmpty() }.first { it.tag == "GB" }
        st.openSystem(sys)
        st.openContextMenu(0)
        val items = assertIs<Screen.Games>(st.screen.value).menu!!.items
        st.menuSelect(items.indexOfFirst { it.kind == ContextKind.RENAME })
        st.menuOpen()
        assertIs<MenuPrompt.Rename>(assertIs<Screen.Games>(st.screen.value).menu?.prompt)
        st.kidsEndParent()
        assertIs<KidsMode.Open>(kids.mode.value)
        assertNull((st.screen.value as? Screen.Games)?.menu)
    }

    /** The main menu's hidden-tab items while Open: only the kid's (Consoles, Collections), never Tools. */
    @Test fun hiddenTabItemsWhileOpenAreOnlyTheKids() = runTest {
        val settings = MemorySettings().apply { showConsolesTab = false; showCollectionsTab = false; showToolsTab = false }
        val st = state(settings, KidsController(settings, Platform(SAT, 10 * 60)))
        st.load()
        st.openContextMenu(0)
        val kinds = assertIs<Screen.Systems>(st.screen.value).menu?.items.orEmpty().map { it.kind }
        assertTrue(ContextKind.OPEN_CONSOLES in kinds, "$kinds")
        assertTrue(kinds.all { ContextMenus.kidsMayPick(it) }, "$kinds")
    }

    @Test fun kidsPlayItemsKeepPlayAndDropLibraryEdits() {
        val all = ContextKind.entries.map { ContextItem(it, it.name) }
        assertEquals(
            setOf(ContextKind.PLAY_FROM_START, ContextKind.NETPLAY, ContextKind.PIN, ContextKind.UNPIN, ContextKind.REMOVE_RECENT,
                ContextKind.ACHIEVEMENTS, ContextKind.OPEN_GAMES, ContextKind.OPEN_CONSOLES, ContextKind.OPEN_COLLECTIONS, ContextKind.USE_TOP_SCREEN),
            ContextMenus.kidsPlayItems(all).map { it.kind }.toSet(),
        )
    }

    /** Deleting the flag (Forgot PIN?) clears a same-day unlock and the flag's home, as switching off does. */
    @Test fun flagMissingClearsTheUnlockAndHome() = runTest {
        val settings = MemorySettings()
        val platform = Platform(MON, 10 * 60)
        val kids = KidsController(settings, platform)
        kids.unlock(UnlockChip.entries.first())
        assertIs<KidsMode.Open>(kids.mode.value)
        platform.flag.seen = FlagSeen.MISSING
        kids.refresh()
        assertEquals(0L, settings.kidsUnlockUntil)
        assertNull(settings.kidsFlagHome)
    }

    /** A re-setup the same day comes up Locked on a locked day, whatever unlock was left before. */
    @Test fun turnOnDropsALeftoverUnlock() = runTest {
        val settings = MemorySettings().apply { kidsEnabled = false; kidsUnlockUntil = 1L * DAY }
        val kids = KidsController(settings, Platform(MON, 10 * 60))
        kids.turnOn()
        assertEquals(0L, settings.kidsUnlockUntil)
        assertIs<KidsMode.Locked>(kids.mode.value)
    }

    private companion object {
        const val DAY = 86_400_000L
        const val MON = 1
        const val SAT = 6
    }
}
