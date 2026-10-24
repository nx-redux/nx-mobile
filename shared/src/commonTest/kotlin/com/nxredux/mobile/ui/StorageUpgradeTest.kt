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
import com.nxredux.mobile.storage.FakeAllFilesAccess
import com.nxredux.mobile.storage.FakeLocalFiles
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.HomeChoice
import com.nxredux.mobile.storage.HomeLocator
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.StateFormat
import com.nxredux.mobile.storage.StorageUpgradePlatform
import com.nxredux.mobile.ui.Screen.StorageUpgrade.Stage
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one-time storage upgrade screen (installs from v0.3.0 and earlier) over a fake platform that records its calls. */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageUpgradeTest {
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
        override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap()
        override var gameListStyle: MenuStyle = MenuStyle.LIST
        override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap()
        override var gameListOrientation: MenuOrientation = MenuStyles.DEFAULT_ORIENTATION
        override var gameListVerticalAlignment: VerticalListAlignment = VerticalListAlignment.DEFAULT
        override var toolApps: List<String> = emptyList()
        override var androidConsoleApps: List<String> = emptyList()
        override var androidGamesAsked = false
        override var allFilesAsked = false
        override var onboardingDone: Boolean? = true   // an install from before the upgrade
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
        var homePicks = 0
        override fun pickHome() { homePicks++ }
        override fun pickExtra() {}
        override fun forget(ref: ContentRef) {}
    }

    /** The platform's choice ([HomeChoice.effective]) on the settings now, with [dirs] the file folders that exist. */
    private class Homes(val settings: AppSettings, val access: FakeAllFilesAccess, val dirs: Set<String>, override val media: ContentRef) : HomeLocator {
        override fun current(): ContentRef? = HomeChoice.effective(settings.treeRef, access.granted(), { FileRefs.path(it) in dirs }, media)
    }

    /** Records every call in order; [moves] false fails the move; [gone]: the old home is gone; [unreadable]: adoption throws. */
    private class Platform(
        var done: Boolean = false, var moves: Boolean = true, val gone: Boolean = false, val unreadable: Boolean = false,
        var waiting: Boolean = false, val stuck: Boolean = false,
    ) : StorageUpgradePlatform {
        val calls = ArrayList<String>()
        override fun isDone() = done
        override suspend fun adoptPickerHome(old: ContentRef, into: ContentRef?): ExtraFolder? {
            calls += "adopt ${old.value} into ${into?.value}"
            if (unreadable) throw IllegalStateException("card out")
            if (gone) return null
            return ExtraFolder("Roms-old", ContentRef(old.value + "/document/primary%3ANXRedux%2FRoms"))
        }
        override suspend fun moveInto(home: ContentRef): Boolean { calls += "move ${home.value}"; return moves }
        override fun markDone() { calls += "mark"; done = true }
        override fun markIfEmpty() { calls += "markIfEmpty"; done = true }
        override fun startQuietMove() { calls += "quiet" }
        override fun quietMoveStuck() = stuck
        override fun isWaiting() = waiting
        override fun setWaiting() { calls += "wait"; waiting = true }
        override suspend fun restoreHomeKeys(extraId: String): Boolean { calls += "restore $extraId"; return true }
        override suspend fun copySaves(from: ContentRef, to: ContentRef): Boolean { calls += "copy ${from.value} to ${to.value}"; return true }
    }

    private val media = FileRefs.of("/storage/emulated/0/Android/media/com.nxredux.mobile.dev")
    private val fileHome = FileRefs.of("/storage/emulated/0/NXRedux")
    private val other = FileRefs.of("/storage/emulated/0/Games")
    private val saf = ContentRef("content://com.android.externalstorage.documents/tree/primary%3ANXRedux")

    /** Every root holds one GB game in Roms. */
    private val source = FakeRomsSource(
        listOf(media.value, fileHome.value, other.value, saf.value).flatMap { r ->
            listOf(r to listOf("Roms"), "$r/Roms" to listOf("Game Boy (GB)"), "$r/Roms/Game Boy (GB)" to listOf("Tetris.gb"))
        }.toMap(),
    )

    private class Rig(val state: AppState, val settings: MemorySettings, val access: FakeAllFilesAccess, val platform: Platform, val seen: List<Screen>, val picker: Picker)

    private fun TestScope.rig(saved: ContentRef?, granted: Boolean = false, dirs: Set<String> = emptySet(), platform: Platform = Platform(), settings: MemorySettings = MemorySettings(saved)): Rig {
        val access = FakeAllFilesAccess(declared = true, isGranted = granted)
        val picker = Picker()
        val state = AppState(
            AppDeps(
                settings, source, Runner(), picker, compute = UnconfinedTestDispatcher(testScheduler), allFilesAccess = access,
                localFiles = FakeLocalFiles(dirs = dirs.toList()), homes = Homes(settings, access, dirs + FileRefs.path(media), media),
                storageUpgrade = platform,
            ),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        val seen = ArrayList<Screen>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.screen.collect { seen += it } }
        return Rig(state, settings, access, platform, seen, picker)
    }

    private fun AppState.choose(label: String) {
        val s = assertIs<Screen.StorageUpgrade>(screen.value)
        val i = storageUpgradeRows(s).indexOf(label)
        assertTrue(i >= 0, "no $label row on ${s.stage}")
        select(i); onButton(NxButton.A)
    }

    @Test fun declineAdoptsMovesAndMarks() = runTest {
        val r = rig(saf)
        r.state.load()
        assertEquals(Stage.EXPLAIN, assertIs<Screen.StorageUpgrade>(r.state.screen.value).stage)
        r.state.choose("Not now")
        assertEquals(listOf("adopt ${saf.value} into null", "move ${media.value}", "mark"), r.platform.calls)
        assertEquals(listOf("Roms-old"), r.settings.extraFolders.map { it.id })
        assertNull(r.settings.treeRef)
        assertTrue(r.settings.allFilesAsked)
        val declined = assertIs<Screen.StorageUpgrade>(r.state.screen.value)
        assertEquals(Stage.DECLINED, declined.stage)
        assertEquals("Android/media/com.nxredux.mobile.dev/", declined.folder)
        r.state.choose("OK")
        assertIs<Screen.Systems>(r.state.screen.value)
    }

    @Test fun grantWithConversionSkipsPick() = runTest {
        val r = rig(saf, dirs = setOf(FileRefs.path(fileHome)))
        r.state.load()
        r.state.choose("Allow")
        assertEquals(1, r.access.opened)                           // Android's page; the screen waits for the answer
        assertEquals(Stage.EXPLAIN, assertIs<Screen.StorageUpgrade>(r.state.screen.value).stage)
        r.access.isGranted = true
        r.state.onAllFilesAccessChanged()                          // back from it, granted
        assertEquals(fileHome, r.settings.treeRef)                 // the picker home converted to its path
        assertEquals(listOf("move ${fileHome.value}", "mark"), r.platform.calls)
        assertTrue(r.seen.none { it is Screen.StorageUpgrade && it.stage == Stage.PICK })
        assertIs<Screen.Systems>(r.state.screen.value)
    }

    @Test fun grantPickingAnotherFolderAdoptsTheOld() = runTest {
        val r = rig(saf, granted = true, dirs = setOf(FileRefs.path(other)))   // the old home's path is no folder: no conversion
        r.state.load()
        r.state.choose("Allow")                                    // already granted: on at once
        assertEquals(Stage.PICK, assertIs<Screen.StorageUpgrade>(r.state.screen.value).stage)
        assertEquals(emptyList(), r.platform.calls)
        r.state.storageUpgradePicked(other)
        assertEquals(listOf("adopt ${saf.value} into ${other.value}", "move ${other.value}", "mark"), r.platform.calls)
        assertEquals(other, r.settings.treeRef)
        assertEquals(listOf("Roms-old"), r.settings.extraFolders.map { it.id })
        assertIs<Screen.Systems>(r.state.screen.value)
    }

    @Test fun grantPickingTheOldFolderAdoptsNothing() = runTest {
        val r = rig(saf, granted = true)
        r.state.load()
        r.state.choose("Allow")
        assertEquals(Stage.PICK, assertIs<Screen.StorageUpgrade>(r.state.screen.value).stage)
        r.state.storageUpgradePicked(fileHome)                    // the old home's own path
        assertEquals(listOf("move ${fileHome.value}", "mark"), r.platform.calls)
        assertEquals(emptyList(), r.settings.extraFolders)
    }

    @Test fun quietCaseShowsNoScreen() = runTest {
        val r = rig(fileHome, granted = true, dirs = setOf(FileRefs.path(fileHome)))
        r.state.load()
        assertTrue(r.seen.none { it is Screen.StorageUpgrade })
        assertEquals(listOf("quiet"), r.platform.calls)            // the load starts the background move
        assertIs<Screen.Systems>(r.state.screen.value)
    }

    @Test fun doneShowsNoScreen() = runTest {
        val r = rig(saf, platform = Platform(done = true))
        r.state.load()
        assertTrue(r.seen.none { it is Screen.StorageUpgrade })
    }

    @Test fun onboardingFinishMarksDone() = runTest {
        val settings = MemorySettings(fileHome).apply { onboardingDone = false; onboardingStep = OnboardingStep.DONE.name }
        val r = rig(fileHome, granted = true, dirs = setOf(FileRefs.path(fileHome)), settings = settings)
        r.state.load()
        assertEquals(OnboardingStep.DONE, assertIs<Screen.Onboarding>(r.state.screen.value).step)
        r.state.onButton(NxButton.A)                               // Start
        assertEquals(true, settings.onboardingDone)
        assertEquals(listOf("markIfEmpty"), r.platform.calls)       // marked only when nothing waits to move
    }

    // A failed move marks nothing: the data stays private, the page says so, and the screen comes back on the next start.
    @Test fun failedMoveIsNotMarked() = runTest {
        val r = rig(saf, platform = Platform(moves = false))
        r.state.load()
        r.state.choose("Not now")
        assertEquals(listOf("adopt ${saf.value} into null", "move ${media.value}"), r.platform.calls)
        val failed = assertIs<Screen.StorageUpgrade>(r.state.screen.value)
        assertEquals("Couldn't move your saves. They're safe; the app will try again next time.", failed.error)
        r.state.choose("OK")
        assertIs<Screen.Systems>(r.state.screen.value)            // on with the private data this run
        r.state.load()
        assertIs<Screen.Systems>(r.state.screen.value)            // not again in this run
    }

    // A picker home that is gone for good has nothing to adopt: the data still moves. One that can't be read now waits.
    @Test fun goneOldHomeMovesWithoutAnExtra() = runTest {
        val r = rig(saf, platform = Platform(gone = true))
        r.state.load()
        r.state.choose("Not now")
        assertEquals(listOf("adopt ${saf.value} into null", "move ${media.value}", "mark"), r.platform.calls)
        assertEquals(emptyList(), r.settings.extraFolders)
        assertNull(r.settings.treeRef)
    }

    @Test fun unreadableOldHomeWaits() = runTest {
        val r = rig(saf, platform = Platform(unreadable = true))
        r.state.load()
        r.state.choose("Not now")
        assertEquals(listOf("adopt ${saf.value} into null"), r.platform.calls)
        assertEquals(saf, r.settings.treeRef)                      // kept for the next start's try
        assertEquals("Couldn't move your saves. They're safe; the app will try again next time.", assertIs<Screen.StorageUpgrade>(r.state.screen.value).error)
    }

    // B never leaves the required page: on EXPLAIN it stays, on PICK it goes back to EXPLAIN.
    @Test fun backStaysOnTheUpgrade() = runTest {
        val r = rig(saf, granted = true)
        r.state.load()
        assertTrue(r.state.back())
        assertEquals(Stage.EXPLAIN, assertIs<Screen.StorageUpgrade>(r.state.screen.value).stage)
        r.state.choose("Allow")
        assertTrue(r.state.back())
        assertEquals(Stage.EXPLAIN, assertIs<Screen.StorageUpgrade>(r.state.screen.value).stage)
        assertEquals(emptyList(), r.platform.calls)
    }

    // A file home with access off is no Case B: one EXPLAIN; Not now moves nothing to the media home and marks nothing.
    @Test fun fileHomeWithoutAccessNotNowWaits() = runTest {
        val r = rig(fileHome, dirs = setOf(FileRefs.path(fileHome)))
        r.state.load()
        assertEquals(Stage.EXPLAIN, assertIs<Screen.StorageUpgrade>(r.state.screen.value).stage)
        r.state.choose("Not now")
        assertEquals(listOf("wait", "quiet"), r.platform.calls)    // no adopt, no move, no mark; the quiet move waits for access
        assertEquals(fileHome, r.settings.treeRef)
        assertIs<Screen.Systems>(r.state.screen.value)
        r.state.load()
        assertEquals(1, r.seen.count { it is Screen.StorageUpgrade })   // once
    }

    @Test fun fileHomeWithoutAccessAllowMovesIntoIt() = runTest {
        val r = rig(fileHome, dirs = setOf(FileRefs.path(fileHome)))
        r.state.load()
        r.state.choose("Allow")
        assertEquals(1, r.access.opened)
        r.access.isGranted = true
        r.state.onAllFilesAccessChanged()
        assertEquals(listOf("move ${fileHome.value}", "mark"), r.platform.calls)
        assertEquals(fileHome, r.settings.treeRef)
        assertIs<Screen.Systems>(r.state.screen.value)
    }

    // After a failed decline the old home's Roms is an extra (converted to its path): picking the old home takes it back.
    @Test fun pickingTheOldHomeTakesBackItsAdoptedExtra() = runTest {
        val settings = MemorySettings(null).apply { extraFolders = listOf(ExtraFolder("Roms-old", FileRefs.of(FileRefs.path(fileHome) + "/Roms"))) }
        val r = rig(null, granted = true, settings = settings)
        r.state.load()
        r.state.choose("Allow")
        assertEquals(Stage.PICK, assertIs<Screen.StorageUpgrade>(r.state.screen.value).stage)
        r.state.storageUpgradePicked(fileHome)
        assertEquals(listOf("restore Roms-old", "move ${fileHome.value}", "mark"), r.platform.calls)
        assertEquals(emptyList(), r.settings.extraFolders)
        assertEquals(fileHome, r.settings.treeRef)
    }

    // Until the data has moved, the Saves note and the library notice say the saves are still in the app.
    @Test fun savesNoteSaysInsideTheAppUntilMoved() = runTest {
        val r = rig(fileHome, granted = true, dirs = setOf(FileRefs.path(fileHome)))
        r.state.load()
        r.state.openSettings(SettingsSection.SAVES)
        assertEquals("Saves are still stored inside the app until your home folder is available.", assertIs<Screen.Settings>(r.state.screen.value).banner)
        r.platform.done = true
        r.state.openSettings(SettingsSection.SAVES)
        assertEquals("Saves are in your home folder.", assertIs<Screen.Settings>(r.state.screen.value).banner)
    }

    // Access off: no notice at each start, the one-time move waiting or not (Library's All files row says it).
    @Test fun accessOffShowsNoNoticeAtStart() = runTest {
        val r = rig(fileHome, dirs = setOf(FileRefs.path(fileHome)), platform = Platform(waiting = true))
        r.state.load()
        assertNull(r.state.notice.value)
    }

    @Test fun aStuckQuietMoveIsNoticed() = runTest {
        val r = rig(fileHome, granted = true, dirs = setOf(FileRefs.path(fileHome)), platform = Platform(stuck = true))
        r.state.load()
        assertEquals("Saves couldn't move to your home folder. They're still inside the app.", r.state.notice.value)
    }

    // Without access no picker home is ever saved: the Home folder row asks for All files access instead.
    @Test fun pickHomeWithoutAccessAsksForAccess() = runTest {
        val r = rig(null, platform = Platform(done = true))
        r.state.pickHome()
        assertEquals(0, r.picker.homePicks)
        assertEquals(1, r.access.opened)
    }

    // A home change in Settings offers to copy the saves; Copy copies them, Keep separate doesn't.
    @Test fun aHomeChangeOffersToCopyTheSaves() = runTest {
        val r = rig(fileHome, granted = true, dirs = setOf(FileRefs.path(fileHome), FileRefs.path(other)), platform = Platform(done = true))
        r.state.load()
        r.state.setHome(other)
        val offer = assertIs<Screen.CopySaves>(r.state.screen.value)
        assertEquals(listOf("Copy", "Keep separate"), copySavesRows(offer))
        r.state.select(0); r.state.onButton(NxButton.A)
        assertEquals(listOf("copy ${fileHome.value} to ${other.value}"), r.platform.calls)
        assertEquals(other, r.settings.treeRef)
    }

    @Test fun keepSeparateCopiesNothing() = runTest {
        val r = rig(fileHome, granted = true, dirs = setOf(FileRefs.path(fileHome), FileRefs.path(other)), platform = Platform(done = true))
        r.state.load()
        r.state.setHome(other)
        assertIs<Screen.CopySaves>(r.state.screen.value)
        r.state.select(1); r.state.onButton(NxButton.A)
        assertEquals(emptyList(), r.platform.calls)
        assertTrue(r.state.screen.value !is Screen.CopySaves)
    }

    // From the media home (access just granted), picking a folder offers to copy the media home's saves into it.
    @Test fun aPickFromTheMediaHomeOffersToCopy() = runTest {
        val r = rig(null, granted = true, dirs = setOf(FileRefs.path(other)), platform = Platform(done = true))
        r.state.load()
        r.state.setHome(other)
        assertIs<Screen.CopySaves>(r.state.screen.value)
        r.state.select(0); r.state.onButton(NxButton.A)
        assertEquals(listOf("copy ${media.value} to ${other.value}"), r.platform.calls)
    }
}
