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
import com.nxredux.mobile.settings.SaveTransferRules
import com.nxredux.mobile.storage.Recent
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.storage.Shortcut
import com.nxredux.mobile.storage.UserDataStore
import com.nxredux.mobile.sync.DeviceSyncController
import com.nxredux.mobile.sync.DeviceSyncPage
import com.nxredux.mobile.sync.DeviceSyncText
import com.nxredux.mobile.sync.FakeSyncPlatform
import com.nxredux.mobile.sync.SyncLink
import com.nxredux.mobile.sync.SyncStep
import com.nxredux.mobile.sync.SyncWire
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tools → Device Sync in a real [AppState]: the row, the screen's buttons, the launch guard and the home refresh. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSyncToolTest {
    private class Settings : AppSettings {
        override var treeRef: ContentRef? = ContentRef("Home")
        override var onboardingDone: Boolean? = true
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
        override var artBox2d = false
        override var artWheel = false
        override var artMix = false
        override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showNetplayHint = true; override var menuPageTitle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = com.nxredux.mobile.settings.MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = com.nxredux.mobile.settings.AccentPreset.DEFAULT; override var secondaryAccent: com.nxredux.mobile.settings.SecondaryAccent = com.nxredux.mobile.settings.SecondaryAccent.DEFAULT
    }

    private object Picker : FolderPicker { override fun pickHome() {}; override fun pickExtra() {}; override fun forget(ref: ContentRef) {} }

    private class RecordingRunner : EmulatorRunner {
        val launched = ArrayList<GameSession>()
        override fun launch(session: GameSession, onStarted: () -> Unit) { launched += session; onStarted() }
    }

    /** Tetris was played last, so Home has a Continue row; [resumeReads] counts the main-row rebuilds that re-read it. */
    private class CountingUserData : UserDataStore {
        var recents = listOf(Recent("/Roms/Game Boy (GB)/Tetris.gb", "Tetris"))
        var resumeReads = 0
        override suspend fun readRecents() = recents
        override suspend fun writeRecents(list: List<Recent>) { recents = list }
        override suspend fun readPins(): List<Shortcut> = emptyList()
        override suspend fun writePins(list: List<Shortcut>) = true
        override suspend fun readHidden(): List<String> = emptyList()
        override suspend fun writeHidden(list: List<String>) = true
        override suspend fun resumeInfo(tag: String, fileName: String): ResumeInfo { resumeReads++; return ResumeInfo.NONE }
    }

    private class Rig(
        val state: AppState, val sync: DeviceSyncController, val platform: FakeSyncPlatform,
        val runner: RecordingRunner, val data: CountingUserData, val source: FakeRomsSource,
    )

    /** [withCollections]: the home has an empty `Collections/`. [onboardingAt]: onboarding to do, resumed at that step. */
    private fun TestScope.rig(withCollections: Boolean = false, onboardingAt: OnboardingStep? = null): Rig {
        val source = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms") + if (withCollections) listOf("Collections") else emptyList(),
                "Home/Roms" to listOf("Game Boy (GB)"),
                "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Zelda.gb"),
            ) + if (withCollections) mapOf("Home/Collections" to emptyList()) else emptyMap(),
        )
        val platform = FakeSyncPlatform()
        val sync = DeviceSyncController(backgroundScope, platform) { testScheduler.currentTime }
        val runner = RecordingRunner()
        val data = CountingUserData()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val settings = Settings().apply { if (onboardingAt != null) { onboardingDone = false; onboardingStep = onboardingAt.name } }
        val state = AppState(
            AppDeps(settings, source, runner, Picker, data, compute = UnconfinedTestDispatcher(testScheduler), deviceSync = sync),
            scope,
        )
        state.load()
        advanceUntilIdle()
        return Rig(state, sync, platform, runner, data, source)
    }

    private fun AppState.toolRows(): List<ToolRow> =
        assertIs<Screen.Systems>(screen.value).also { assertEquals(MenuCategory.TOOLS, it.category) }.rows.map { (it as MainRow.Tool).row }

    private fun Rig.openDeviceSync() {
        state.openTools()
        val rows = state.toolRows()
        state.select(rows.indexOfFirst { it.kind == ToolKind.DEVICE_SYNC }); state.openSelected()
        assertEquals(Screen.DeviceSync, state.screen.value)
    }

    /** Plays Tetris from the Game Boy list, as AppStateTest's import launch-refusal test plays its game. */
    private fun Rig.launchFirstGame() {
        val systems = assertIs<Screen.Systems>(state.screen.value).tabs.flatMap { it.rows }.flatMap { (it as? MainRow.System)?.systems.orEmpty() }
        state.openSystem(systems.first { it.tag == "GB" })
        state.play(assertIs<Screen.Games>(state.screen.value).entries[0].game)
    }

    private val handheld = "192.168.1.10"

    /** Onboarding from the handheld step: Yes, then past its formats to the sync step: Device Sync, in place. */
    private fun TestScope.atSyncOffer(): Rig {
        val r = rig(onboardingAt = OnboardingStep.HANDHELD)
        r.state.select(0); r.state.onButton(NxButton.A)                 // Yes, I have one
        r.state.select(3); r.state.onButton(NxButton.A)                 // the formats' Continue
        r.state.select(3); r.state.onButton(NxButton.A)                 // the save import's Skip
        assertEquals(Screen.DeviceSync, r.state.screen.value)
        assertIs<DeviceSyncPage.Ready>(r.sync.page.value)
        return r
    }

    // The sync step is Device Sync itself, under the setup's page title: Skip goes on, B goes back a step.
    @Test fun onboarding_sync_step_is_device_sync_in_place() = runTest {
        val r = atSyncOffer()
        assertEquals(SyncSetup("Setup · Step 7/12", synced = false, focus = SyncFocus.LINK), r.state.syncSetup.value)
        r.state.onButton(NxButton.B)                                     // B on Ready: the step before
        assertEquals(OnboardingStep.IMPORT_SAVES, assertIs<Screen.Onboarding>(r.state.screen.value).step)
        r.state.select(3); r.state.onButton(NxButton.A)                 // its Skip
        assertEquals(Screen.DeviceSync, r.state.screen.value)
        skip(r)
        assertEquals(OnboardingStep.ARTWORK, assertIs<Screen.Onboarding>(r.state.screen.value).step)
        assertNull(r.state.syncSetup.value)
    }

    // Skip is the third row, under the two links: the d-pad goes cable → Wi-Fi → Skip and round; a tap on it goes on.
    @Test fun onboarding_skip_is_a_row_under_the_links() = runTest {
        val r = atSyncOffer()
        r.sync.pick(SyncLink.CABLE)
        r.state.onButton(NxButton.DOWN)
        assertEquals(SyncLink.WIFI, assertIs<DeviceSyncPage.Ready>(r.sync.page.value).link)
        r.state.onButton(NxButton.DOWN)
        assertTrue(r.state.syncSetup.value!!.skipSelected)
        r.state.onButton(NxButton.DOWN)
        assertFalse(r.state.syncSetup.value!!.skipSelected)
        assertEquals(SyncLink.CABLE, assertIs<DeviceSyncPage.Ready>(r.sync.page.value).link)
        r.state.onButton(NxButton.UP)
        assertTrue(r.state.syncSetup.value!!.skipSelected)
        r.state.syncSetupTap(0, open = false)                             // a tap on cable selects it
        assertFalse(r.state.syncSetup.value!!.skipSelected)
        r.state.syncSetupTap(2, open = true)                              // a tap on Skip goes on
        assertEquals(OnboardingStep.ARTWORK, assertIs<Screen.Onboarding>(r.state.screen.value).step)
    }

    // Sync ROMs is an option row above the links where ROM sync is available: the d-pad reaches it, A / LEFT / RIGHT flip it.
    @Test fun onboarding_sync_roms_is_an_option_row_above_the_links() = runTest {
        val r = atSyncOffer()
        r.platform.roms = true
        r.sync.pick(SyncLink.CABLE)
        r.state.onButton(NxButton.UP)
        assertEquals(SyncFocus.ROMS, r.state.syncSetup.value!!.focus)
        r.state.onButton(NxButton.A)
        assertTrue(assertIs<DeviceSyncPage.Ready>(r.sync.page.value).roms)
        r.state.onButton(NxButton.RIGHT)
        assertFalse(assertIs<DeviceSyncPage.Ready>(r.sync.page.value).roms)
        r.state.syncSetupTap(0, open = true)                              // a tap on the row flips it
        assertTrue(assertIs<DeviceSyncPage.Ready>(r.sync.page.value).roms)
        r.state.onButton(NxButton.DOWN)
        assertEquals(SyncFocus.LINK, r.state.syncSetup.value!!.focus)
        assertEquals(SyncLink.CABLE, assertIs<DeviceSyncPage.Ready>(r.sync.page.value).link)
        r.state.syncSetupTap(3, open = true)                              // Skip, the fourth row with ROMs listed
        assertEquals(OnboardingStep.ARTWORK, assertIs<Screen.Onboarding>(r.state.screen.value).step)
    }

    // Where ROM sync is offered, setup's sync step opens on the Sync ROMs option, before the links.
    @Test fun onboarding_sync_step_opens_on_sync_roms() = runTest {
        val r = atSyncOffer()
        r.platform.roms = true
        r.state.onButton(NxButton.B)                                     // back, then in again with ROM sync offered
        r.state.select(3); r.state.onButton(NxButton.A)
        assertEquals(SyncFocus.ROMS, r.state.syncSetup.value!!.focus)
    }

    // The tool lists Sync ROMs as an option row above the links too (no Skip): the d-pad reaches it, A flips it.
    @Test fun the_tool_lists_sync_roms_above_the_links() = runTest {
        val r = rig()
        r.platform.roms = true
        r.openDeviceSync()
        r.sync.pick(SyncLink.CABLE)
        val ready = assertIs<DeviceSyncPage.Ready>(r.sync.page.value)
        assertEquals(listOf(SyncSetupRow.Roms, SyncSetupRow.Link(SyncLink.CABLE), SyncSetupRow.Link(SyncLink.WIFI)), syncSetupRows(ready, skip = false))
        assertEquals(SyncFocus.ROMS, r.state.syncSetup.value!!.focus)   // the page opens on it
        r.state.onButton(NxButton.A)
        assertTrue(assertIs<DeviceSyncPage.Ready>(r.sync.page.value).roms)
        r.state.onButton(NxButton.UP)                                     // round to Wi-Fi
        assertEquals(SyncLink.WIFI, assertIs<DeviceSyncPage.Ready>(r.sync.page.value).link)
        r.state.onButton(NxButton.B)
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(r.state.screen.value).category)
        assertNull(r.state.syncSetup.value)
    }

    /** DOWN onto the Skip row, then A. */
    private fun skip(r: Rig) {
        while (r.state.syncSetup.value?.skipSelected == false) r.state.onButton(NxButton.DOWN)
        r.state.onButton(NxButton.A)
    }

    // Home on the embedded Device Sync is ignored, as on the other setup screens.
    @Test fun onboarding_home_on_the_embedded_sync_stays() = runTest {
        val r = atSyncOffer()
        r.state.goHome()
        assertEquals(Screen.DeviceSync, r.state.screen.value)
    }

    @Test fun device_sync_outside_onboarding_still_returns_to_tools() = runTest {
        val r = rig(); r.openDeviceSync()
        r.state.onButton(NxButton.B)
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(r.state.screen.value).category)
    }

    @Test fun the_tools_tab_lists_device_sync_and_opens_it_on_ready() = runTest {
        val r = rig()
        r.state.openTools()
        val rows = r.state.toolRows()
        assertTrue(rows.any { it.kind == ToolKind.DEVICE_SYNC && it.label == "Device Sync" })
        assertEquals(rows.indexOfFirst { it.kind == ToolKind.CHEAT_DB } + 1, rows.indexOfFirst { it.kind == ToolKind.DEVICE_SYNC })
        r.state.select(rows.indexOfFirst { it.kind == ToolKind.DEVICE_SYNC }); r.state.openSelected()
        assertEquals(Screen.DeviceSync, r.state.screen.value)
        assertEquals(DeviceSyncPage.Ready(SyncLink.CABLE), r.sync.page.value)
    }

    @Test fun buttons_go_to_the_controller_and_b_on_ready_returns_to_tools() = runTest {
        val r = rig(); r.openDeviceSync()
        r.state.onButton(NxButton.DOWN)
        assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI), r.sync.page.value)
        assertEquals(Screen.DeviceSync, r.state.screen.value)
        r.state.onButton(NxButton.B)
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(r.state.screen.value).category)
    }

    @Test fun back_on_no_wifi_stays_in_the_tool_and_a_second_back_leaves() = runTest {
        val r = rig(); r.openDeviceSync()
        r.platform.wifi = null
        r.sync.pick(SyncLink.WIFI); r.state.onButton(NxButton.A); runCurrent()
        assertEquals(DeviceSyncPage.NoWifi, r.sync.page.value)
        assertTrue(r.state.back())
        assertEquals(Screen.DeviceSync, r.state.screen.value)
        assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI), r.sync.page.value)
        assertTrue(r.state.back())
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(r.state.screen.value).category)
    }

    @Test fun a_tap_on_a_link_row_picks_it_and_open_selected_is_a() = runTest {
        // DeviceSyncScreen calls controller.pick(SyncLink.entries[i]) on select and state.onButton(A) on open;
        // here: openSelected on Screen.DeviceSync acts as A.
        val r = rig(); r.openDeviceSync()
        r.platform.wifi = null
        r.sync.pick(SyncLink.WIFI); r.state.openSelected(); runCurrent()
        assertEquals(DeviceSyncPage.NoWifi, r.sync.page.value)
    }

    @Test fun reopening_the_tool_starts_on_ready_cable() = runTest {
        val r = rig(); r.openDeviceSync()
        r.state.onButton(NxButton.DOWN); r.state.onButton(NxButton.B)
        r.openDeviceSync()
        assertEquals(DeviceSyncPage.Ready(SyncLink.CABLE), r.sync.page.value)
    }

    @Test fun refusals_from_the_controller_show_as_notices() = runTest {
        val r = rig(); r.openDeviceSync()
        r.platform.gameAlive = true
        r.state.onButton(NxButton.A); runCurrent()
        assertEquals("Close the running game first, then sync.", r.state.notice.value)
        assertEquals(DeviceSyncPage.Ready(SyncLink.CABLE), r.sync.page.value)
    }

    @Test fun no_game_starts_while_a_sync_runs() = runTest {
        val r = rig(); r.openDeviceSync()
        r.sync.pick(SyncLink.WIFI); r.state.onButton(NxButton.A); runCurrent()   // Searching: running
        assertIs<DeviceSyncPage.Searching>(r.sync.page.value)
        r.state.goHome()
        r.launchFirstGame(); runCurrent()
        assertEquals("Device Sync is running… please wait", r.state.notice.value)
        assertTrue(r.runner.launched.isEmpty())
        r.sync.cancel(); runCurrent()   // the notification's Cancel
        assertFalse(r.sync.running)
        r.state.goHome()
        r.launchFirstGame(); runCurrent()
        assertEquals(1, r.runner.launched.size)    // the same launch goes through once the sync has ended
    }

    @Test fun a_sync_that_received_files_refreshes_the_home_rows() = runTest {
        val r = rig()
        r.platform.steps[SyncStep.RECEIVE_SAVES] = listOf("GB/Tetris.srm", " 1 (xfr#1, to-chk=0/1)") to 0
        r.openDeviceSync()
        r.sync.pick(SyncLink.WIFI); r.state.onButton(NxButton.A); runCurrent()
        r.platform.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        val before = r.data.resumeReads
        assertTrue(before > 0, "Home's Continue row was built from the recents")
        r.platform.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 1, sent = 0, notes = listOf(DeviceSyncText.BIOS_NO_ACCESS, DeviceSyncText.COLLECTIONS_NO_ACCESS)), r.sync.page.value)
        assertEquals(before + 1, r.data.resumeReads)   // one main-row rebuild re-read Continue's resume point
        assertEquals(Screen.DeviceSync, r.state.screen.value)   // the refresh does not leave the tool
    }

    @Test fun a_sync_that_received_nothing_does_not_rebuild() = runTest {
        val r = rig(); r.openDeviceSync()
        r.sync.pick(SyncLink.WIFI); r.state.onButton(NxButton.A); runCurrent()
        r.platform.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        val before = r.data.resumeReads
        r.platform.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 0, notes = listOf(DeviceSyncText.BIOS_NO_ACCESS, DeviceSyncText.COLLECTIONS_NO_ACCESS)), r.sync.page.value)
        assertEquals(before, r.data.resumeReads)
    }

    /** A collection that arrived shows on the Collections tab without a rescan. */
    @Test fun a_sync_that_wrote_a_collection_lists_it_on_the_collections_tab() = runTest {
        val r = rig(withCollections = true)
        r.platform.roms = true
        r.platform.home = mapOf(null to listOf("Game Boy (GB)"), "Game Boy (GB)" to listOf("Tetris.gb"))
        r.platform.collectionsPull = com.nxredux.mobile.sync.CollectionsPull(0, emptyList(), mapOf("Faves.txt" to "/Roms/Game Boy (GB)/Tetris.gb\n"))
        r.platform.onWriteCollection = { name, lines -> r.source.writeText(ContentRef("Home/Collections"), "$name.txt", lines.joinToString("") { "$it\n" }) }
        r.openDeviceSync()
        r.state.syncSetupTap(2, open = true); runCurrent()   // Sync via Wi-Fi (the page opens on Sync ROMs, above the links)
        r.platform.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        r.platform.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent(); advanceUntilIdle()
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 1, sent = 0), r.sync.page.value)
        assertEquals(Screen.DeviceSync, r.state.screen.value)
        r.state.onButton(NxButton.B); r.state.onButton(NxButton.B)   // Done → Ready → Tools
        val tabs = assertIs<Screen.Systems>(r.state.screen.value).tabs
        val collections = tabs.first { it.category == MenuCategory.COLLECTIONS }.rows.map { (it as MainRow.Collection).name }
        assertEquals(listOf("Faves"), collections)
    }

    /** A ROM sync that received files, run to Done with the X toggle on; the controller is driven directly. */
    private fun TestScope.receiveRoms(r: Rig) {
        r.platform.roms = true
        r.platform.romResults[2] = listOf("a.gb", " 1 (xfr#1, to-chk=0/1)") to 0   // run 2: the home pull
        r.sync.pick(SyncLink.WIFI); r.sync.onButton(NxButton.X); r.sync.onButton(NxButton.A); runCurrent()
        r.platform.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        r.platform.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertIs<DeviceSyncPage.Done>(r.sync.page.value)
    }

    @Test fun roms_arriving_in_the_tool_rescan_the_library_when_it_is_left() = runTest {
        val r = rig(); r.openDeviceSync()
        val before = r.source.listings
        receiveRoms(r)
        assertEquals(Screen.DeviceSync, r.state.screen.value)   // no rescan under the tool
        assertEquals(before, r.source.listings)
        while (r.state.screen.value == Screen.DeviceSync) r.state.onButton(NxButton.B)
        assertTrue(r.source.listings > before)   // leaving the tool scanned
        r.openDeviceSync()
        val after = r.source.listings
        while (r.state.screen.value == Screen.DeviceSync) r.state.onButton(NxButton.B)
        assertEquals(after, r.source.listings)   // spent: the next leave only goes back to Tools
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(r.state.screen.value).category)
    }

    // A sync from onboarding that brought ROMs: A (Continue) on Done rescans the library, then setup goes on.
    @Test fun onboarding_sync_with_roms_rescans_then_goes_on() = runTest {
        val r = atSyncOffer()
        val before = r.source.listings
        receiveRoms(r)
        assertEquals(Screen.DeviceSync, r.state.screen.value)
        r.state.onButton(NxButton.A)                                     // Continue
        advanceUntilIdle()
        assertTrue(r.source.listings > before)                          // rescanned
        assertEquals(OnboardingStep.ARTWORK, assertIs<Screen.Onboarding>(r.state.screen.value).step)
    }

    @Test fun roms_arriving_while_the_systems_page_shows_rescan_at_once() = runTest {
        val r = rig(); r.openDeviceSync()
        r.state.onButton(NxButton.B)   // Tools, a Systems page
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(r.state.screen.value).category)
        val before = r.source.listings
        receiveRoms(r)
        assertTrue(r.source.listings > before)
    }

    // Leaving the tool by Home, not B, still scans: the menu showing again takes the pending ROMs.
    @Test fun roms_arriving_in_the_tool_rescan_when_it_is_left_by_home() = runTest {
        val r = rig(); r.openDeviceSync()
        receiveRoms(r)
        val before = r.source.listings
        r.state.goHome()
        advanceUntilIdle()
        assertTrue(r.source.listings > before)
        assertIs<Screen.Systems>(r.state.screen.value)
        val after = r.source.listings
        r.state.openTools(); advanceUntilIdle()
        assertEquals(after, r.source.listings)   // spent
    }

    // The sync ends while a game list is open: no scan under the list, one when the menu shows again.
    @Test fun roms_arriving_under_a_game_list_rescan_when_the_menu_shows_again() = runTest {
        val r = rig(); r.openDeviceSync()
        r.state.goHome()
        val systems = assertIs<Screen.Systems>(r.state.screen.value).tabs.flatMap { it.rows }.flatMap { (it as? MainRow.System)?.systems.orEmpty() }
        r.state.openSystem(systems.first { it.tag == "GB" })
        assertIs<Screen.Games>(r.state.screen.value)
        val before = r.source.listings
        receiveRoms(r)
        assertIs<Screen.Games>(r.state.screen.value)
        assertEquals(before, r.source.listings)
        r.state.onButton(NxButton.B)
        advanceUntilIdle()
        assertTrue(r.source.listings > before)
    }

    @Test fun refusal_rules_cover_both_transfers() {
        assertEquals("Close the running game first, then import.", SaveTransferRules.importRefusal(gameScreenAlive = true, syncing = true))
        assertEquals("Device Sync is running… please wait", SaveTransferRules.importRefusal(gameScreenAlive = false, syncing = true))
        assertNull(SaveTransferRules.importRefusal(gameScreenAlive = false, syncing = false))
        assertEquals("Importing saves… please wait", SaveTransferRules.launchRefusal(importing = true, syncing = true))
        assertEquals("Device Sync is running… please wait", SaveTransferRules.launchRefusal(importing = false, syncing = true))
        assertNull(SaveTransferRules.launchRefusal(importing = false, syncing = false))
    }
}
