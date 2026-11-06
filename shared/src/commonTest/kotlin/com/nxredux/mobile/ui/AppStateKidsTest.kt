package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.kids.FlagSeen
import com.nxredux.mobile.kids.KidsController
import com.nxredux.mobile.kids.KidsFlag
import com.nxredux.mobile.kids.KidsMode
import com.nxredux.mobile.kids.KidsPlatform
import com.nxredux.mobile.kids.LocalNow
import com.nxredux.mobile.kids.PinResult
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
import com.nxredux.mobile.settings.PadButtons
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.settings.SecondaryAccent
import com.nxredux.mobile.settings.VerticalListAlignment
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Kids Mode in AppState: the tiles screen while Locked, no Tools while Open, the PIN wait, the flag and the lock. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStateKidsTest {
    private class MemorySettings(override var treeRef: ContentRef? = ContentRef("Home")) : AppSettings {
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
        private var pin: String? = "1234"   // a fake: the real one keeps a salted hash
        override fun setKidsPin(pin: String) { this.pin = pin }
        override fun kidsPinMatches(pin: String) = pin == this.pin
        override val kidsPinSet get() = pin != null
    }

    private class Runner : EmulatorRunner {
        override suspend fun launch(session: GameSession, recent: com.nxredux.mobile.storage.Recent) {}
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

    private fun TestScope.state(settings: AppSettings, kids: KidsController?) = AppState(
        AppDeps(settings, source, Runner(), Picker(), compute = UnconfinedTestDispatcher(testScheduler)),
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
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS), all)
        val kids = KidsController(settings, Platform(SAT, 10 * 60))
        assertIs<KidsMode.Open>(kids.mode.value)
        val st = state(settings, kids)
        st.load()
        assertEquals(all - MenuCategory.TOOLS, tabsOf(st))
        assertFalse(assertIs<Screen.Systems>(st.screen.value).hidden.contains(MenuCategory.TOOLS))   // not offered as a hidden tab either
        st.openHiddenTab(MenuCategory.TOOLS)
        assertEquals(all - MenuCategory.TOOLS, tabsOf(st))
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
        kids.parentInput(t0 + 4 * 60_000L)
        kids.tick(t0 + 8 * 60_000L)
        assertEquals(KidsMode.Parent, kids.mode.value)
        kids.tick(t0 + 9 * 60_000L)
        assertIs<KidsMode.Locked>(kids.mode.value)
    }

    @Test fun wrongPinFiveTimesWaits30s() = runTest {
        val settings = MemorySettings()
        val kids = KidsController(settings, Platform(MON, 10 * 60))
        val t = 1_000_000L
        repeat(4) { assertEquals(PinResult.WRONG, kids.tryPin("0000", t + it)) }
        assertEquals(PinResult.WAIT, kids.tryPin("0000", t + 4))
        assertEquals(t + 4 + 30_000L, kids.waitUntil)
        assertEquals(PinResult.WAIT, kids.tryPin("1234", t + 4 + 29_999L))   // even the right one waits
        assertEquals(PinResult.OK, kids.tryPin("1234", t + 4 + 30_000L))
        assertEquals(PinResult.WRONG, kids.tryPin("0000", t + 40_000L))        // the count started again
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
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS), tabsOf(st))
        kids.lock()
        assertEquals(0L, settings.kidsUnlockUntil)
        assertIs<KidsMode.Locked>(kids.mode.value)
        assertEquals(1, platform.locks)
        assertSame(Screen.KidsHome, st.screen.value)
    }

    @Test fun lockedSecondScreenShowsTheBackdrop() {
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.KidsHome, null, emptyList(), tool = true))
        assertEquals(BottomContent.Normal, DualMenu.bottom(Screen.KidsHome, active = true, tool = true))
    }

    private companion object {
        const val DAY = 86_400_000L
        const val MON = 1
        const val SAT = 6
    }
}
