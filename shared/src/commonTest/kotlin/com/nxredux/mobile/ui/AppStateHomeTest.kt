package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.library.Entry
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.LibraryScanner
import com.nxredux.mobile.library.RomsSource
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
import com.nxredux.mobile.storage.FakeAllFilesAccess
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.HomeLocator
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.StateFormat
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AppState on the effective home ([HomeLocator]): the full-access folder, else the app's Android/media folder. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStateHomeTest {
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
        // real defaults (a missing tab is its MenuStyles.defaultStyleOf) are tested with menuStyles = emptyMap() (AppStateTest).
        override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap()
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
    private class Runner : EmulatorRunner {
        override fun launch(session: GameSession, onStarted: () -> Unit) = onStarted()
    }
    private class Picker : FolderPicker {
        var picks = 0
        override fun pickHome() { picks++ }
        override fun pickExtra() {}
        override fun forget(ref: ContentRef) {}
    }
    private class Homes(var now: ContentRef?, override val media: ContentRef?) : HomeLocator {
        override fun current(): ContentRef? = now
    }

    private val media = FileRefs.of("/storage/emulated/0/Android/media/com.nxredux.mobile.dev")
    private val fileHome = FileRefs.of("/storage/emulated/0/NXRedux")
    private val mediaNote = "Saves are in Android/media/com.nxredux.mobile.dev/. Uninstalling the app removes them."

    /** [roots] (ref values) each holding one GB game in Roms, the folders listed recorded in [listed]. */
    private class Listing private constructor(private val src: FakeRomsSource) : RomsSource by src {
        constructor(vararg roots: String) : this(
            FakeRomsSource(
                roots.flatMap { r ->
                    listOf(r to listOf("Roms"), "$r/Roms" to listOf("Game Boy (GB)"), "$r/Roms/Game Boy (GB)" to listOf("Tetris.gb"))
                }.toMap(),
            ),
        )
        val listed = ArrayList<String>()
        override suspend fun listChildren(dir: ContentRef): List<Entry> { listed += dir.value; return src.listChildren(dir) }
    }

    private fun TestScope.state(settings: MemorySettings, source: RomsSource, homes: HomeLocator?, picker: FolderPicker = Picker(), allFiles: FakeAllFilesAccess? = null) =
        AppState(
            AppDeps(settings, source, Runner(), picker, compute = UnconfinedTestDispatcher(testScheduler), allFilesAccess = allFiles, homes = homes),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

    /** Every screen [state] shows from now on. */
    private fun TestScope.screens(state: AppState): List<Screen> {
        val seen = ArrayList<Screen>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.screen.collect { seen += it } }
        return seen
    }

    @Test fun noAccessLoadsTheMediaHome() = runTest {
        val source = Listing(media.value)
        val picker = Picker()
        val st = state(MemorySettings(), source, Homes(media, media), picker)
        val seen = screens(st)
        st.load()
        assertIs<Screen.Systems>(st.screen.value)
        assertTrue(source.listed.any { it.startsWith(media.value) })
        assertTrue(seen.none { it is Screen.PickFolder })
        assertEquals(0, picker.picks)
    }

    @Test fun accessOffKeepsTheSavedHome() = runTest {
        val settings = MemorySettings(fileHome)
        val source = Listing(media.value, fileHome.value)
        val st = state(settings, source, Homes(media, media))
        st.load()
        assertIs<Screen.Systems>(st.screen.value)
        assertTrue(source.listed.any { it.startsWith(media.value) })                   // the library is the media home's
        assertTrue(source.listed.none { it.startsWith(fileHome.value) })
        assertEquals(fileHome, settings.treeRef)                   // remembered for when access is back
    }

    @Test fun goneFileHomeIsNotCleared() = runTest {
        for (home in listOf(fileHome, ContentRef("Home"))) {
            val settings = MemorySettings(home)
            val gone = object : RomsSource by FakeRomsSource(emptyMap()) {
                override suspend fun listChildren(dir: ContentRef): List<Entry> = throw IllegalArgumentException("Folder no longer exists")
            }
            val st = state(settings, gone, homes = null)
            st.load()
            advanceTimeBy(LibraryScanner.REMOUNT_RETRY_MILLIS + 1); runCurrent()   // the one remount retry
            if (FileRefs.isFile(home)) assertEquals(home, settings.treeRef) else assertNull(settings.treeRef)   // a SAF home: cleared, as before
        }
    }

    @Test fun onboardingWithoutAccessSkipsThePicker() = runTest {
        val settings = MemorySettings().apply { onboardingDone = null }
        val picker = Picker()
        val st = state(settings, Listing(media.value), Homes(media, media), picker, FakeAllFilesAccess(declared = true, isGranted = false))
        val seen = screens(st)
        st.load()
        assertEquals(OnboardingStep.WELCOME, assertIs<Screen.Onboarding>(st.screen.value).step)
        st.onButton(NxButton.A)                                    // Get started
        st.select(1); st.onButton(NxButton.A)                      // Text size: Continue
        assertIs<Screen.PermissionsStep>(st.screen.value)
        st.skipAllFiles()                                          // All files declined
        // On as after a pick: the home page (the media home holds a game), then the ROMs folder offer and the main steps.
        val home = assertIs<Screen.FolderSummary>(st.screen.value)
        assertEquals(Recents.HOME, home.extraId)
        st.select(folderSummaryRows(home).lastIndex); st.onButton(NxButton.A)
        assertIs<Screen.AddFirstExtra>(st.screen.value)
        st.skipFirstExtra()
        assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(st.screen.value).step)
        assertTrue(seen.none { it is Screen.PickFolder })
        assertEquals(0, picker.picks)
        assertNull(settings.treeRef)                               // the media home is never saved as the home
    }

    @Test fun savesNoteNamesTheLocation() = runTest {
        val homes = Homes(fileHome, media)
        val st = state(MemorySettings(fileHome), Listing(media.value, fileHome.value), homes, allFiles = FakeAllFilesAccess(isGranted = true))
        st.load()
        assertEquals("Saves are in your home folder.", st.settingsPreview(SettingsSection.SAVES).banner)
        homes.now = media
        assertEquals(mediaNote, st.settingsPreview(SettingsSection.SAVES).banner)
    }

    // Read from the media folder for want of All files access: no notice at each start (Library's All files row says it).
    @Test fun mediaHomeWithoutAccessShowsNoNotice() = runTest {
        val st = state(MemorySettings(fileHome), Listing(media.value, fileHome.value), Homes(media, media))
        st.load()
        assertNull(st.notice.value)
        assertFalse(st.screen.value is Screen.PickFolder)
    }

    private val gamesNote = "Your games folder is Android/media/com.nxredux.mobile.dev/. Put ROMs and BIOS there with a file manager. Uninstalling the app removes it."

    /** A fresh install without All files access, walked to its Permissions page. */
    private fun TestScope.noAccessSetup(source: RomsSource, picker: Picker = Picker()): Pair<AppState, MemorySettings> {
        val settings = MemorySettings().apply { onboardingDone = null }
        val st = state(settings, source, Homes(media, media), picker, FakeAllFilesAccess(declared = true, isGranted = false))
        st.load()
        st.onButton(NxButton.A)                                    // Get started
        st.select(1); st.onButton(NxButton.A)                      // Text size: Continue
        assertIs<Screen.PermissionsStep>(st.screen.value)
        return st to settings
    }

    @Test fun backFromTheMediaHomeSkipsThePicker() = runTest {
        // The home page (the media home has a game): B goes back to Permissions, and on again to the home page.
        val (st, _) = noAccessSetup(Listing(media.value))
        val seen = screens(st)
        st.skipAllFiles()
        assertIs<Screen.FolderSummary>(st.screen.value)
        st.onButton(NxButton.B)
        assertIs<Screen.PermissionsStep>(st.screen.value)
        st.skipAllFiles()
        val home = assertIs<Screen.FolderSummary>(st.screen.value)
        assertEquals(Recents.HOME, home.extraId)
        st.select(folderSummaryRows(home).lastIndex); st.onButton(NxButton.A)
        assertIs<Screen.AddFirstExtra>(st.screen.value)
        st.onButton(NxButton.B)                                    // back to the home page
        assertIs<Screen.FolderSummary>(st.screen.value)
        assertTrue(seen.none { it is Screen.PickFolder })

        // No game in the media home: the ROMs folder offer first; B goes back to Permissions, and on again to the offer.
        val (empty, _) = noAccessSetup(FakeRomsSource(mapOf(media.value to emptyList())))
        val seenEmpty = screens(empty)
        empty.skipAllFiles()
        assertIs<Screen.AddFirstExtra>(empty.screen.value)
        empty.onButton(NxButton.B)
        assertIs<Screen.PermissionsStep>(empty.screen.value)
        empty.skipAllFiles()
        assertIs<Screen.AddFirstExtra>(empty.screen.value)
        assertTrue(seenEmpty.none { it is Screen.PickFolder })
    }

    @Test fun noAccessStepsCountNoPick() = runTest {
        val (st, _) = noAccessSetup(Listing(media.value))
        val (permissions, _) = st.setupChrome()
        st.skipAllFiles()
        val (homePage, _) = st.setupChrome()
        val home = assertIs<Screen.FolderSummary>(st.screen.value)
        st.select(folderSummaryRows(home).lastIndex); st.onButton(NxButton.A)
        val (offer, _) = st.setupChrome()
        val total = homePage.substringAfter(" of ").toInt()
        // No pick between Permissions and the home page; the home page itself joins the count once the scan found games, as after a pick.
        assertEquals(listOf("Setup · Step 1 of ${total - 1}", "Setup · Step 2 of $total", "Setup · Step 3 of $total"), listOf(permissions, homePage, offer))
        // Permissions, the home page, the ROMs folder offer, then onboarding's own steps: no pick among them.
        st.skipFirstExtra()
        assertEquals(OnboardingStep.HANDHELD, assertIs<Screen.Onboarding>(st.screen.value).step)
    }

    @Test fun mediaHomeSetupNote() = runTest {
        val (st, _) = noAccessSetup(Listing(media.value))
        assertNull(st.setupNote())
        st.skipAllFiles()
        assertEquals(gamesNote, st.setupNote())                    // the home page: the first folder page here
        val home = assertIs<Screen.FolderSummary>(st.screen.value)
        st.select(folderSummaryRows(home).lastIndex); st.onButton(NxButton.A)
        assertNull(st.setupNote())                                 // the offer after it: said already

        val (empty, _) = noAccessSetup(FakeRomsSource(mapOf(media.value to emptyList())))
        empty.skipAllFiles()
        assertIs<Screen.AddFirstExtra>(empty.screen.value)
        assertEquals(gamesNote, empty.setupNote())                 // no home page: the offer is the first
    }

    @Test fun accessBackSwitchesToTheSavedHome() = runTest {
        val source = Listing(media.value, fileHome.value)
        val access = FakeAllFilesAccess(declared = true, isGranted = false)
        val homes = Homes(media, media)
        val picker = Picker()
        val settings = MemorySettings(fileHome)
        val st = state(settings, source, homes, picker, access)
        st.load()
        assertIs<Screen.Systems>(st.screen.value)
        assertTrue(source.listed.none { it.startsWith(fileHome.value) })
        val seen = screens(st)
        access.isGranted = true; homes.now = fileHome            // back from Android's page with access on
        st.onAllFilesAccessChanged()
        assertIs<Screen.Systems>(st.screen.value)
        assertTrue(source.listed.any { it.startsWith(fileHome.value) })   // the saved home is the library again
        assertTrue(seen.none { it is Screen.PickFolder })
        assertEquals(0, picker.picks)
        assertEquals(fileHome, settings.treeRef)
    }

    @Test fun aPickAfterOnboardingIsNotFirstSetup() = runTest {
        val settings = MemorySettings()                            // onboarding done, no saved home: the media one
        val homes = Homes(media, media)
        val st = state(settings, Listing(media.value, fileHome.value), homes, allFiles = FakeAllFilesAccess(isGranted = true))
        st.load()
        assertIs<Screen.Systems>(st.screen.value)
        homes.now = fileHome
        st.setHome(fileHome)
        assertEquals(fileHome, settings.treeRef)
        assertIs<Screen.Systems>(st.screen.value)                  // no first-setup home page
        assertNull(settings.onboardingStep)
    }

    @Test fun missingHomeNoticeWithAccessOn() = runTest {
        val st = state(MemorySettings(fileHome), Listing(media.value), Homes(media, media), allFiles = FakeAllFilesAccess(isGranted = true))
        st.load()
        assertEquals("Your home folder can't be found. Using Android/media/com.nxredux.mobile.dev/ until it's back. Saves made there stay there.", st.notice.value)
    }

    @Test fun aRestartOnTheMediaHomeStillSkipsThePicker() = runTest {
        for (games in listOf(true, false)) {
            val source: RomsSource = if (games) Listing(media.value) else FakeRomsSource(mapOf(media.value to emptyList()))
            val (first, settings) = noAccessSetup(source)
            first.skipAllFiles()                                   // the folder steps are under way, then the app closes
            // A new process: the same settings and folder, a fresh AppState.
            val access = FakeAllFilesAccess(declared = true, isGranted = false)
            val st = state(settings, source, Homes(media, media), Picker(), access)
            val seen = screens(st)
            st.load()
            if (games) assertEquals(Recents.HOME, assertIs<Screen.FolderSummary>(st.screen.value).extraId)
            else assertIs<Screen.AddFirstExtra>(st.screen.value)
            assertEquals(gamesNote, st.setupNote())
            st.onButton(NxButton.B)
            assertIs<Screen.PermissionsStep>(st.screen.value)
            st.skipAllFiles()
            if (games) assertIs<Screen.FolderSummary>(st.screen.value) else assertIs<Screen.AddFirstExtra>(st.screen.value)
            assertTrue(seen.none { it is Screen.PickFolder }, "games=$games")
        }
    }
}
