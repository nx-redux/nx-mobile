package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.kids.FlagSeen
import com.nxredux.mobile.kids.KidsController
import com.nxredux.mobile.kids.KidsFlag
import com.nxredux.mobile.kids.KidsMode
import com.nxredux.mobile.kids.KidsPlatform
import com.nxredux.mobile.kids.KidsSchedule
import com.nxredux.mobile.kids.LocalNow
import com.nxredux.mobile.kids.PinPurpose
import com.nxredux.mobile.kids.PlayWindow
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
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.PadButtons
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
import kotlin.test.assertTrue

/** Settings -> Kids Mode: its rows, the first switch-on's setup steps, Change PIN and a day's play time. */
@OptIn(ExperimentalCoroutinesApi::class)
class KidsSettingsTest {
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
        override var androidGamesAsked = true   // no "Add Android games?" over the page (Beta is a game)
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

        override var kidsEnabled = false
        override var kidsSchedule: String? = null   // the default: Mon–Thu locked, Fri–Sun 9:00–20:00
        override var kidsAllowedApps: Set<String> = emptySet()
        override var kidsUnlockUntil = 0L
        override var kidsFlagHome: String? = null
        var pin: String? = null   // a fake: the real one keeps a salted hash
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

    private class Flag(var seen: FlagSeen = FlagSeen.MISSING) : KidsFlag {
        override fun homeKey() = "Home"
        override fun seen() = seen
        override fun write(): Boolean { seen = FlagSeen.PRESENT; return true }
        override fun delete() { seen = FlagSeen.MISSING }
        override fun flagPath() = "/storage/emulated/0/NXRedux/kids-mode.flag"
    }

    /** Monday 10:00 (a locked day by default); the two permissions as set, each system screen counted. */
    private class Platform(var usage: Boolean = true, var alarms: Boolean = true, override val flag: Flag = Flag()) : KidsPlatform {
        var usageOpened = 0
        var alarmsOpened = 0
        override fun now() = LocalNow(10 * 3_600_000L, 1, 10 * 3_600_000L)
        override fun hasUsageAccess() = usage
        override fun openUsageAccess() { usageOpened++ }
        override fun canExactAlarms() = alarms
        override fun openExactAlarms() { alarmsOpened++ }
        override fun reschedule(mode: KidsMode) {}
        override fun lockNow() {}
    }

    private val source = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Game Boy (GB)"),
            "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb"),
        ),
    )

    private class Launcher(val apps: List<InstalledApp>, var homeNow: Boolean = false) : AppLauncher {
        var homeAsked = 0
        override suspend fun installedApps() = apps
        override fun label(id: String) = apps.firstOrNull { it.id == id }?.label
        override suspend fun icon(id: String): ImageBitmap? = null
        override fun launch(id: String) = true
        override fun isHome() = homeNow
        override fun setHome(on: Boolean) { if (on) homeAsked++ }
    }

    private val apps = listOf(InstalledApp("a/A", "Alpha"), InstalledApp("b/B", "Beta", game = true))

    private class Setup(val st: AppState, val settings: MemorySettings, val kids: KidsController, val platform: Platform, val launcher: Launcher)

    private fun TestScope.setup(usage: Boolean = true, alarms: Boolean = true, home: Boolean = true): Setup {
        val settings = MemorySettings()
        val platform = Platform(usage, alarms)
        val kids = KidsController(settings, platform)
        val launcher = Launcher(apps, home)
        val st = AppState(
            AppDeps(settings, source, Runner(), Picker(), compute = UnconfinedTestDispatcher(testScheduler), launcher = launcher),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            kids,
        )
        st.load()
        return Setup(st, settings, kids, platform, launcher)
    }

    private fun AppState.typePin(pin: String) { pin.forEach { kidsPinDigit(it.digitToInt()) }; kidsPinEnter() }

    private fun AppState.page(): Screen.Settings = assertIs<Screen.Settings>(screen.value)

    /** A on the Kids Mode page's first row of [kind] (a day's: [day]). */
    private fun AppState.pressRow(kind: SettingsKind, day: Int? = null) {
        val i = page().rows.indexOfFirst { it.kind == kind && (day == null || it.day == day) }
        assertTrue(i >= 0, "no $kind row")
        select(i); onButton(NxButton.A)
    }

    private fun AppState.setupStep(): KidsSetupStep = assertIs<Screen.KidsSetup>(screen.value).step

    /** A on the setup step's first row matching [pick]. */
    private fun AppState.pressSetup(pick: (KidsSetupRow) -> Boolean) {
        val rows = kidsSetupRows(assertIs<Screen.KidsSetup>(screen.value))
        val i = rows.indexOfFirst(pick)
        assertTrue(i >= 0, "no such row in $rows")
        select(i); onButton(NxButton.A)
    }

    private fun AppState.next() = pressSetup { it == KidsSetupRow.Next }
    private fun AppState.skip() = pressSetup { it == KidsSetupRow.Skip }

    @Test fun firstEnableRunsSetupInOrder() = runTest {
        val t = setup(usage = false, alarms = false, home = false)
        val st = t.st
        st.openSettings(SettingsSection.KIDS)
        assertEquals(SettingsSection.KIDS, st.page().section)
        st.pressRow(SettingsKind.KIDS_ENABLE)
        assertEquals(PinPurpose.SETUP_NEW, assertIs<Screen.KidsPin>(st.screen.value).purpose)
        st.typePin("1234")
        assertEquals(PinPurpose.SETUP_CONFIRM, assertIs<Screen.KidsPin>(st.screen.value).purpose)
        st.typePin("1234")
        assertNull(t.settings.pin)                                         // kept until setup finishes
        assertEquals(KidsSetupStep.APPS, st.setupStep())
        st.pressSetup { it is KidsSetupRow.App && it.app.id == "b/B" }     // Beta allowed
        assertEquals(setOf("b/B"), t.settings.kidsAllowedApps)
        assertTrue(kidsSetupRows(assertIs(st.screen.value)).any { it is KidsSetupRow.App && it.app.id == "b/B" && it.allowed })
        st.next()
        assertEquals(KidsSetupStep.TIMES, st.setupStep())
        assertEquals((1..7).toList(), kidsSetupRows(assertIs(st.screen.value)).filterIsInstance<KidsSetupRow.Day>().map { it.day })
        st.next()
        assertEquals(KidsSetupStep.USAGE, st.setupStep())
        st.pressSetup { it == KidsSetupRow.Grant }                         // Android's Usage access page
        assertEquals(1, t.platform.usageOpened)
        assertTrue(t.kids.granting)
        assertEquals(KidsSetupStep.USAGE, st.setupStep())
        t.platform.usage = true
        st.onForeground()                                                  // back, granted: the next step
        assertFalse(t.kids.granting)
        assertEquals(KidsSetupStep.ALARMS, st.setupStep())
        st.skip()
        assertEquals(KidsSetupStep.HOME, st.setupStep())
        assertFalse(t.settings.kidsEnabled)
        st.pressSetup { it == KidsSetupRow.UseHome }
        assertEquals(1, t.launcher.homeAsked)
        // Done: the PIN stored, Kids Mode on with its flag, the parent still in the full menu, back on the page.
        assertEquals("1234", t.settings.pin)
        assertTrue(t.settings.kidsEnabled)
        assertEquals(FlagSeen.PRESENT, t.platform.flag.seen)
        assertEquals(KidsMode.Parent, t.kids.mode.value)
        assertEquals(SettingsSection.KIDS, st.page().section)
        assertEquals("On", st.page().rows.first { it.kind == SettingsKind.KIDS_ENABLE }.secondary)
    }

    @Test fun pinMismatchRestartsPinStep() = runTest {
        val st = setup().st
        st.openSettings(SettingsSection.KIDS)
        st.pressRow(SettingsKind.KIDS_ENABLE)
        st.typePin("1234")
        st.typePin("5678")
        val pin = assertIs<Screen.KidsPin>(st.screen.value)
        assertEquals(PinPurpose.SETUP_NEW, pin.purpose)
        assertEquals("PINs didn't match", pin.message)
        assertEquals("", pin.typed)
        st.typePin("4321"); st.typePin("4321")
        assertEquals(KidsSetupStep.APPS, st.setupStep())
    }

    @Test fun skipUsageStillEnablesAndShowsGuardOffRow() = runTest {
        val t = setup(usage = false, alarms = true, home = true)
        val st = t.st
        st.openSettings(SettingsSection.KIDS)
        assertFalse(st.page().rows.any { it.kind == SettingsKind.KIDS_GUARD })   // off: no warning yet
        st.pressRow(SettingsKind.KIDS_ENABLE)
        st.typePin("1234"); st.typePin("1234")
        st.next(); st.next()
        assertEquals(KidsSetupStep.USAGE, st.setupStep())
        st.skip()                                                         // alarms allowed and already home: done
        assertTrue(t.settings.kidsEnabled)
        val rows = st.page().rows
        assertTrue(rows.any { it.kind == SettingsKind.KIDS_GUARD })
        assertFalse(rows.any { it.kind == SettingsKind.KIDS_ALARMS })
        st.pressRow(SettingsKind.KIDS_GUARD)                              // the row asks again
        assertEquals(1, t.platform.usageOpened)
        assertTrue(t.kids.granting)
    }

    @Test fun dayEditStoresWindow() = runTest {
        val t = setup()
        val st = t.st
        st.openSettings(SettingsSection.KIDS)
        assertEquals("9:00 AM – 8:00 PM", st.page().rows.first { it.kind == SettingsKind.KIDS_DAY && it.day == 5 }.secondary)
        assertEquals("Locked", st.page().rows.first { it.kind == SettingsKind.KIDS_DAY && it.day == 1 }.secondary)
        st.pressRow(SettingsKind.KIDS_DAY, day = 5)
        val edit = assertIs<Screen.KidsDayEdit>(st.screen.value)
        assertEquals(5, edit.day)
        fun step(row: KidsDayRow, button: NxButton, times: Int) {
            st.select(kidsDayRows(assertIs(st.screen.value)).indexOf(row))
            repeat(times) { st.onButton(button) }
        }
        step(KidsDayRow.FROM, NxButton.RIGHT, 2)                          // 9:00 -> 10:00
        step(KidsDayRow.TO, NxButton.LEFT, 4)                             // 20:00 -> 18:00
        assertEquals(PlayWindow(600, 1080), KidsSchedule.decode(t.settings.kidsSchedule).days[5])
        // From never reaches To: the steppers stop half an hour apart.
        step(KidsDayRow.FROM, NxButton.RIGHT, 40)
        assertEquals(PlayWindow(1050, 1080), KidsSchedule.decode(t.settings.kidsSchedule).days[5])
        step(KidsDayRow.FROM, NxButton.LEFT, 9)
        // Locked and back: the window it had returns.
        step(KidsDayRow.LOCKED, NxButton.A, 1)
        assertNull(KidsSchedule.decode(t.settings.kidsSchedule).days[5])
        step(KidsDayRow.LOCKED, NxButton.A, 1)
        assertEquals(PlayWindow(780, 1080), KidsSchedule.decode(t.settings.kidsSchedule).days[5])
        st.onButton(NxButton.B)
        assertEquals("1:00 PM – 6:00 PM", st.page().rows.first { it.kind == SettingsKind.KIDS_DAY && it.day == 5 }.secondary)
    }

    @Test fun turnOffDeletesFlag() = runTest {
        val t = setup()
        val st = t.st
        st.openSettings(SettingsSection.KIDS)
        st.pressRow(SettingsKind.KIDS_ENABLE)
        st.typePin("1234"); st.typePin("1234"); st.next(); st.next()
        assertTrue(t.settings.kidsEnabled)
        assertEquals(FlagSeen.PRESENT, t.platform.flag.seen)
        st.pressRow(SettingsKind.KIDS_ENABLE)                              // Off
        assertFalse(t.settings.kidsEnabled)
        assertEquals(FlagSeen.MISSING, t.platform.flag.seen)
        assertEquals(KidsMode.Off, t.kids.mode.value)
        assertEquals("Off", st.page().rows.first { it.kind == SettingsKind.KIDS_ENABLE }.secondary)
        st.pressRow(SettingsKind.KIDS_ENABLE)                              // On again: the PIN is set, so no setup
        assertTrue(t.settings.kidsEnabled)
        assertEquals(SettingsSection.KIDS, st.page().section)
    }

    @Test fun setupNotInOnboarding() {
        assertTrue(OnboardingStep.entries.none { it.name.contains("KIDS") })
        for (handheld in listOf(null, true, false)) for (signedIn in listOf(true, false)) for (customise in listOf(null, true, false)) {
            val steps = onboardingSteps(OnboardingContext(signedIn = signedIn, handheld = handheld, customise = customise, padPage = true, buttons = true))
            assertTrue(steps.none { it.name.contains("KIDS") }, steps.toString())
        }
    }

    @Test fun changePinAsksTwiceAndStores() = runTest {
        val t = setup()
        val st = t.st
        st.openSettings(SettingsSection.KIDS)
        st.pressRow(SettingsKind.KIDS_ENABLE)
        st.typePin("1234"); st.typePin("1234"); st.next(); st.next()
        st.pressRow(SettingsKind.KIDS_PIN)
        assertEquals(PinPurpose.CHANGE, assertIs<Screen.KidsPin>(st.screen.value).purpose)
        st.typePin("9999")
        assertEquals(PinPurpose.SETUP_CONFIRM, assertIs<Screen.KidsPin>(st.screen.value).purpose)
        st.typePin("9999")
        assertEquals("9999", t.settings.pin)
        assertEquals(SettingsSection.KIDS, st.page().section)
    }

    @Test fun pageRowsAndForgotPinPath() = runTest {
        val t = setup()
        val st = t.st
        t.settings.kidsAllowedApps = setOf("a/A")
        st.openSettings(SettingsSection.KIDS)
        val rows = st.page().rows
        assertFalse(rows.any { it.kind == SettingsKind.KIDS_PIN })                 // no PIN yet: the first setup chooses it
        assertEquals((1..7).toList(), rows.filter { it.kind == SettingsKind.KIDS_DAY }.map { it.day })
        assertEquals("Alpha", rows.first { it.kind == SettingsKind.KIDS_APPS }.secondary)
        assertEquals("/storage/emulated/0/NXRedux/kids-mode.flag", rows.first { it.kind == SettingsKind.KIDS_FORGOT }.secondary)
        // Allowed apps: the picker over the installed apps, Done back on the row.
        st.pressRow(SettingsKind.KIDS_APPS)
        val pick = assertIs<Screen.KidsSetup>(st.screen.value)
        assertEquals(KidsSetupStep.APPS, pick.step)
        assertFalse(pick.setup)
        st.pressSetup { it is KidsSetupRow.App && it.app.id == "b/B" }
        st.next()
        assertEquals(setOf("a/A", "b/B"), t.settings.kidsAllowedApps)
        assertEquals("Alpha, Beta", st.page().rows.first { it.kind == SettingsKind.KIDS_APPS }.secondary)
    }

    @Test fun kidsSectionOnlyWithKidsMode() = runTest {
        val settings = MemorySettings()
        val st = AppState(
            AppDeps(settings, source, Runner(), Picker(), compute = UnconfinedTestDispatcher(testScheduler)),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        st.load()
        assertFalse(SettingsSection.KIDS in st.settingsSections())
        assertEquals(SettingsSection.KIDS, setup().st.settingsSections().last())
    }

    /** The kid's presses on whatever shows: B, then a PIN typed twice. */
    private fun AppState.kidTries() {
        onButton(NxButton.B)
        typePin("0000"); typePin("0000")
        onButton(NxButton.B)
    }

    @Test fun changePinPadGoesWhenParentIdlesIntoLocked() = runTest {
        val t = setup()
        val st = t.st
        st.openSettings(SettingsSection.KIDS)
        st.pressRow(SettingsKind.KIDS_ENABLE)
        st.typePin("1234"); st.typePin("1234"); st.next(); st.next()
        assertEquals(KidsMode.Parent, t.kids.mode.value)
        st.pressRow(SettingsKind.KIDS_PIN)
        st.typePin("9999")                                                 // half way: the confirmation is up
        assertEquals(PinPurpose.SETUP_CONFIRM, assertIs<Screen.KidsPin>(st.screen.value).purpose)
        t.kids.tick(t.platform.now().epochMs + KidsController.PARENT_IDLE_MS)   // Monday: Parent idles into Locked
        assertIs<KidsMode.Locked>(t.kids.mode.value)
        assertEquals(Screen.KidsHome, st.screen.value)
        st.kidTries()
        assertEquals(Screen.KidsHome, st.screen.value)                     // never the Kids Mode page
        assertEquals("1234", t.settings.pin)                               // and the PIN never changed
        assertNull(st.kidsNewPin)
    }

    @Test fun setupPinPadGoesWhenKidsModeLocks() = runTest {
        val t = setup()
        val st = t.st
        st.openSettings(SettingsSection.KIDS)
        st.pressRow(SettingsKind.KIDS_ENABLE)
        assertEquals(PinPurpose.SETUP_NEW, assertIs<Screen.KidsPin>(st.screen.value).purpose)
        t.settings.kidsEnabled = true                                      // on from elsewhere, on a locked day
        t.kids.refresh()
        assertIs<KidsMode.Locked>(t.kids.mode.value)
        assertEquals(Screen.KidsHome, st.screen.value)
        st.kidTries()
        assertEquals(Screen.KidsHome, st.screen.value)
        assertNull(t.settings.pin)
    }

    @Test fun setupNeverEndsWithoutAPin() = runTest {
        val t = setup(usage = false)
        val st = t.st
        st.openSettings(SettingsSection.KIDS)
        st.pressRow(SettingsKind.KIDS_ENABLE)
        st.typePin("1234"); st.typePin("1234"); st.next(); st.next()
        assertEquals(KidsSetupStep.USAGE, st.setupStep())
        st.kidsNewPin = null                                               // the PIN lost before the end
        st.skip()                                                          // the last step: setup would end here
        assertFalse(t.settings.kidsEnabled)
        assertEquals(PinPurpose.SETUP_NEW, assertIs<Screen.KidsPin>(st.screen.value).purpose)   // the PIN asked again
    }
}
