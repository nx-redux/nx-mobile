package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.options.BottomScreenOptions
import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.options.ConsoleRow
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.CoreOptionSource
import com.nxredux.mobile.options.CoreOptionTable
import com.nxredux.mobile.options.EmulatorPage
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.storage.EmulatorConfigStore
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.nxOptionsListHeight
import com.nxredux.mobile.ui.nx.FooterMode
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmulatorSettingsControllerTest {
    // --- pure: rows ---

    private val optA = CoreOption("opt_a", "x", label = "Option A", values = listOf("x", "y", "z"), defaultValue = "x")
    private val optB = CoreOption(
        "opt_b", "on", label = "Option B", labelCategorized = "B", category = "video", categoryLabel = "Video",
        values = listOf("on", "off"), defaultValue = "on",
    )
    private val layout = ConsoleRow(PadOptions.all.first { it.key == PadOptions.LAYOUT }, PadOptions.AUTO)

    private fun page(console: List<ConsoleRow> = listOf(layout), core: List<CoreOption>? = listOf(optA, optB), forGame: Boolean = false, gameFile: Boolean = false) =
        EmulatorPage("GBA", "gpsp", "gpSP", console, core, forGame, gameFile)

    @Test fun top_level_has_console_then_core_rows_then_categories() {
        assertEquals(
            listOf(
                EmulatorRow.Header("Console settings"), EmulatorRow.Console(layout),
                EmulatorRow.Gap,   // half a row of space between the console and the core settings
                EmulatorRow.Header("Core settings"), EmulatorRow.Core(optA, inCategory = false), EmulatorRow.Category("video", "Video"),
            ),
            EmulatorRows.build(page(), null),
        )
    }

    @Test fun the_gap_is_only_between_console_and_core_settings() {
        assertFalse(EmulatorRow.Gap in EmulatorRows.build(page(console = emptyList()), null))   // no console rows
        assertFalse(EmulatorRow.Gap in EmulatorRows.build(page(), "video"))                     // a category: core rows only
        assertEquals(1, EmulatorRows.build(page(core = null), null).count { it == EmulatorRow.Gap })   // before the notice's header
        assertEquals(2, EmulatorRows.build(page(forGame = true, gameFile = true), null).count { it == EmulatorRow.Gap })   // plus one before the last row, Use emulator settings
        assertEquals(1, EmulatorRows.build(page(forGame = true, gameFile = false), null).count { it == EmulatorRow.Gap })
    }

    @Test fun up_and_down_skip_the_gap() {
        val rows = EmulatorRows.build(page(), null)
        assertFalse(EmulatorRow.Gap.selectable)
        val console = rows.indexOf(EmulatorRow.Console(layout))
        val core = rows.indexOf(EmulatorRow.Core(optA, inCategory = false))
        assertEquals(core, EmulatorRows.step(rows, console, 1))
        assertEquals(console, EmulatorRows.step(rows, core, -1))
    }

    @Test fun the_section_headers_are_grey_labels_not_rows() {
        assertEquals(NxRow("Console settings", section = true), emulatorNxRow(EmulatorRow.Header("Console settings")))
        assertFalse(EmulatorRow.Header("Core settings").selectable)
        assertFalse(emulatorNxRow(EmulatorRow.Core(optA, inCategory = false)).section)
    }

    @Test fun the_gap_draws_as_half_a_row_of_space() {
        assertEquals(NxRow("", spacer = true), emulatorNxRow(EmulatorRow.Gap))
        val rows = EmulatorRows.build(page(), null).map(::emulatorNxRow)
        assertEquals(20.dp * 5 + 10.dp, nxOptionsListHeight(rows, 20.dp))   // 5 rows and the gap
    }

    @Test fun a_game_page_with_a_game_file_ends_with_use_emulator_settings() {
        val rows = EmulatorRows.build(page(forGame = true, gameFile = true), null)
        assertEquals(EmulatorRow.UseEmulatorSettings, rows.last())
        assertEquals(EmulatorRow.Gap, rows[rows.size - 2])
        assertFalse(EmulatorRow.UseEmulatorSettings in EmulatorRows.build(page(forGame = true, gameFile = false), null))
    }

    @Test fun a_core_without_a_table_shows_the_notice_and_no_console_header_without_console_rows() {
        assertEquals(
            listOf(EmulatorRow.Header("Core settings"), EmulatorRow.Notice("This core's options aren't available outside a game.")),
            EmulatorRows.build(page(console = emptyList(), core = null), null),
        )
    }

    @Test fun an_empty_core_table_gets_no_core_header() {
        assertEquals(listOf(EmulatorRow.Header("Console settings"), EmulatorRow.Console(layout)), EmulatorRows.build(page(core = emptyList()), null))
    }

    @Test fun inside_a_category_only_its_rows_marked_in_category() {
        assertEquals(listOf(EmulatorRow.Core(optB, inCategory = true)), EmulatorRows.build(page(), "video"))
    }

    // --- pure: list ---

    private fun game(name: String) = GameEntry(ContentRef("r/$name"), name, name, folder = ContentRef("r"), sourceId = "home", relativeDir = "")
    private fun system(tag: String, games: Int = 1, unassigned: Boolean = false) =
        SystemFolder(tag, tag, CoreRegistry.forTag(tag), (1..games).map { game("$tag$it") }, unassigned = unassigned)

    private val names = mapOf("gpsp" to "gpSP", "genesis_plus_gx" to "Genesis Plus GX")
    private inner class Tables(val emulators: Map<String, String> = names) : CoreOptionSource {
        override fun table(coreId: String): CoreOptionTable? =
            emulators[coreId]?.let { CoreOptionTable(coreId, it, listOf(optA, optB)) }
    }

    @Test fun list_groups_tags_by_core_in_registry_order() {
        val rows = EmulatorSettingsController.listRows(listOf(system("GG"), system("MD")))
        assertEquals(listOf(EmulatorListRow("picodrive", "PicoDrive", listOf("MD", "GG"))), rows)
    }

    @Test fun list_one_row_per_core_sorted_skipping_unassigned_and_empty() {
        val rows = EmulatorSettingsController.listRows(
            listOf(system("GBA"), system("MD"), system("GB", games = 0), system("SNES", unassigned = true)),
        )
        assertEquals(listOf("gpSP", "PicoDrive"), rows.map { it.emulator })
        assertEquals(listOf("GBA"), rows[0].tags)
    }

    @Test fun list_label_is_the_descriptor_name_without_a_table() {
        assertEquals(listOf("gpSP"), EmulatorSettingsController.listRows(listOf(system("GBA"))).map { it.emulator })
    }

    // --- controller through AppState ---

    private class MemorySettings(override var treeRef: ContentRef? = null) : AppSettings {
        override var extraFolders: List<ExtraFolder> = emptyList()
        override var defaultTags: Map<String, String> = emptyMap()
        override var padTheme: PadTheme = PadTheme.DEFAULT
        override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap()
        override var gameListStyle: MenuStyle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_GAME_LIST_STYLE
        override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap()
        override var gameListOrientation: MenuOrientation = com.nxredux.mobile.settings.MenuStyles.DEFAULT_ORIENTATION
        override var gameListVerticalAlignment: com.nxredux.mobile.settings.VerticalListAlignment = com.nxredux.mobile.settings.VerticalListAlignment.DEFAULT
        override var toolApps: List<String> = emptyList()
        override var androidConsoleApps: List<String> = emptyList()
        override var androidGamesAsked = false
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
    }
    private object Runner : EmulatorRunner { override fun launch(session: GameSession, onStarted: () -> Unit) {} }
    private object Picker : FolderPicker { override fun pickHome() {}; override fun pickExtra() {}; override fun forget(ref: ContentRef) {} }

    /** Files by "<TAG>-<coreId>/minarch.cfg" and "<TAG>-<coreId>/<rom>.cfg". */
    private class MemoryConfigs(initial: Map<String, String> = emptyMap()) : EmulatorConfigStore {
        val files = HashMap(initial)
        var failWrites = false
        private fun console(tag: String, core: String) = "$tag-$core/minarch.cfg"
        private fun game(tag: String, core: String, rom: String) = "$tag-$core/${rom.substringBeforeLast('.')}.cfg"
        override suspend fun read(tag: String, coreId: String, romFileName: String?): Pair<ConfigFile?, ConfigFile?> =
            files[console(tag, coreId)]?.let(CoreConfig::parse) to romFileName?.let { files[game(tag, coreId, it)] }?.let(CoreConfig::parse)
        override suspend fun writeConsole(tag: String, coreId: String, text: String) { if (failWrites) error("disk full"); files[console(tag, coreId)] = text }
        override suspend fun writeGame(tag: String, coreId: String, romFileName: String, text: String) { if (failWrites) error("disk full"); files[game(tag, coreId, romFileName)] = text }
        override suspend fun deleteGame(tag: String, coreId: String, romFileName: String) { files.remove(game(tag, coreId, romFileName)) }
    }

    private val source = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Game Boy Advance (GBA)", "Sega Genesis (MD)", "Sega Game Gear (GG)"),
            "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
            "Home/Roms/Sega Genesis (MD)" to listOf("Sonic.md"),
            "Home/Roms/Sega Game Gear (GG)" to listOf("Shinobi.gg"),
        ),
    )

    /**
     * The state, its off-main work ([AppDeps.compute], the Emulator Settings pages too) and its scope all on the test's
     * scheduler: one thread, virtual time. On the real Dispatchers.Default a wall-time poll could time out under load,
     * and the work landed on pool threads while the test thread drove the same state.
     */
    private fun TestScope.TestState(configs: MemoryConfigs, src: FakeRomsSource = source, tables: CoreOptionSource = Tables()) = AppState(
        AppDeps(MemorySettings(ContentRef("Home")), src, Runner, Picker, coreOptions = tables, emulatorConfigs = configs, compute = UnconfinedTestDispatcher(testScheduler)),
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    /** Runs what is pending to the end, then [done] must hold. */
    private fun TestScope.await(done: () -> Boolean) {
        advanceUntilIdle()
        assertTrue(done(), "not settled")
    }

    private fun TestScope.openList(state: AppState): Screen.EmulatorList {
        state.load()
        await { state.screen.value is Screen.Systems }
        state.openSettings(SettingsSection.EMULATORS)
        val rows = assertIs<Screen.Settings>(state.screen.value).rows
        state.select(rows.indexOfFirst { it.kind == SettingsKind.EMULATORS })
        state.openSelected()
        await { state.screen.value is Screen.EmulatorList }
        return state.screen.value as Screen.EmulatorList
    }

    private fun TestScope.openCore(state: AppState, coreId: String) {
        val list = openList(state)
        state.select(list.rows.indexOfFirst { it.coreId == coreId })
        state.onButton(NxButton.A)
    }

    private fun options(state: AppState) = assertIs<Screen.EmulatorOptions>(state.screen.value)
    private fun selected(state: AppState) = options(state).rows[state.selection.value.index]
    private fun coreValue(state: AppState, key: String) = options(state).page.core!!.first { it.key == key }.value

    /** [show] writes the screen, then the selection: an await on a value also waits for the selection to land. */
    private fun onOptA(state: AppState) = (options(state).rows.getOrNull(state.selection.value.index) as? EmulatorRow.Core)?.option?.key == "opt_a"

    private fun selectOptA(state: AppState) {
        repeat(options(state).rows.size) { if ((selected(state) as? EmulatorRow.Core)?.option?.key != "opt_a") state.onButton(NxButton.DOWN) }
        assertEquals("opt_a", (selected(state) as EmulatorRow.Core).option.key)
    }

    @Test fun the_emulators_section_has_default_emulators_then_emulator_settings_and_library_neither() = runTest {
        val state = TestState(MemoryConfigs())
        state.load()
        await { state.screen.value is Screen.Systems }
        state.openSettings(SettingsSection.EMULATORS)
        assertEquals(listOf(SettingsKind.DEFAULTS, SettingsKind.EMULATORS), assertIs<Screen.Settings>(state.screen.value).rows.map { it.kind })   // Saves under Library, Pause when unfocused under System
        state.onButton(NxButton.B)
        state.openSettings(SettingsSection.LIBRARY)
        val kinds = assertIs<Screen.Settings>(state.screen.value).rows.map { it.kind }
        assertTrue(SettingsKind.EMULATORS !in kinds && SettingsKind.DEFAULTS !in kinds)
    }

    @Test fun right_on_a_core_row_writes_the_console_file_and_shows_the_value() = runTest {
        val configs = MemoryConfigs()
        val state = TestState(configs)
        openCore(state, "gpsp")
        await { state.screen.value is Screen.EmulatorOptions }
        assertTrue(selected(state) !is EmulatorRow.Header)   // the first selectable row
        selectOptA(state)
        state.onButton(NxButton.RIGHT)
        await { onOptA(state) && coreValue(state, "opt_a") == "y" }
        assertTrue("opt_a = y" in configs.files.getValue("GBA-gpsp/minarch.cfg").lines())
        assertEquals("opt_a", (selected(state) as EmulatorRow.Core).option.key)   // the selection stays on the row
        state.onButton(NxButton.LEFT); state.onButton(NxButton.LEFT)   // serialised: each change reads what the last wrote
        await { onOptA(state) && coreValue(state, "opt_a") == "z" }
    }

    // Touch only, a value row of three or more opens a chooser of its values; a pick writes it as stepping would.
    // A touch row's picker lists the option's values; a pick writes it.
    @Test fun touch_a_core_option_opens_a_picker_and_a_pick_writes_it() = runTest {
        val configs = MemoryConfigs()
        val state = TestState(configs)
        state.onFooterMode(FooterMode.BUTTONS)
        openCore(state, "gpsp")
        await { state.screen.value is Screen.EmulatorOptions }
        selectOptA(state)
        state.settingEvent(NxSettingEvent.OpenPicker(state.selection.value.index))
        val chooser = state.chooser.value ?: error("no chooser")
        assertTrue(chooser.popup)
        assertEquals("Option A", chooser.title)
        assertEquals(listOf("x", "y", "z"), chooser.labels)
        assertEquals(0, chooser.selected)
        state.chooseValue(2)
        await { onOptA(state) && coreValue(state, "opt_a") == "z" }
        assertEquals(null, state.chooser.value)
        assertTrue("opt_a = z" in configs.files.getValue("GBA-gpsp/minarch.cfg").lines())
    }

    @Test fun a_failed_write_says_so_and_the_page_shows_the_disk() = runTest {
        val configs = MemoryConfigs(mapOf("GBA-gpsp/minarch.cfg" to "opt_a = z\n"))
        val state = TestState(configs)
        openCore(state, "gpsp")
        await { state.screen.value is Screen.EmulatorOptions }
        selectOptA(state)
        configs.failWrites = true
        state.onButton(NxButton.RIGHT)
        await { state.error.value != null }
        assertEquals("Could not save the setting", state.error.value)
        assertEquals("z", coreValue(state, "opt_a"))
    }

    @Test fun a_category_opens_and_b_returns_to_its_row() = runTest {
        val state = TestState(MemoryConfigs())
        openCore(state, "gpsp")
        await { state.screen.value is Screen.EmulatorOptions }
        repeat(options(state).rows.size) { if (selected(state) !is EmulatorRow.Category) state.onButton(NxButton.DOWN) }
        state.onButton(NxButton.A)
        assertEquals("video", options(state).category)
        assertEquals(EmulatorRow.Core(optB, inCategory = true), selected(state))
        state.onButton(NxButton.B)
        assertNull(options(state).category)
        assertEquals(EmulatorRow.Category("video", "Video"), selected(state))
    }

    @Test fun b_from_a_one_tag_core_page_returns_to_the_list_and_then_to_emulators_settings() = runTest {
        val state = TestState(MemoryConfigs())
        openCore(state, "gpsp")
        await { state.screen.value is Screen.EmulatorOptions }
        state.onButton(NxButton.B)
        val list = assertIs<Screen.EmulatorList>(state.screen.value)
        assertEquals("gpsp", list.rows[state.selection.value.index].coreId)
        state.onButton(NxButton.B)
        val settings = assertIs<Screen.Settings>(state.screen.value)
        assertEquals(SettingsSection.EMULATORS, settings.section)
        assertEquals(SettingsKind.EMULATORS, settings.rows[state.selection.value.index].kind)
    }

    @Test fun a_several_tag_core_opens_the_picker_and_b_from_its_page_returns_there() = runTest {
        val state = TestState(MemoryConfigs())
        openCore(state, "picodrive")
        val picker = assertIs<Screen.EmulatorConsoles>(state.screen.value)
        assertEquals(listOf("MD", "GG"), picker.tags)
        state.onButton(NxButton.DOWN)
        state.onButton(NxButton.A)
        await { state.screen.value is Screen.EmulatorOptions }
        assertEquals("GG", options(state).page.tag)
        state.onButton(NxButton.B)
        assertIs<Screen.EmulatorConsoles>(state.screen.value)
        assertEquals(1, state.selection.value.index)
        state.onButton(NxButton.B)
        assertIs<Screen.EmulatorList>(state.screen.value)
    }

    private fun TestScope.openGame(state: AppState): Screen.Games {
        state.load()
        await { state.screen.value is Screen.Systems }
        val systems = assertIs<Screen.Systems>(state.screen.value)
        state.openSystem(systems.tabs.flatMap { it.rows }.filterIsInstance<MainRow.System>().first { it.system.tag == "GBA" }.system)
        val games = assertIs<Screen.Games>(state.screen.value)
        state.emulatorSettings.openGame(games.entries[0])
        await { state.screen.value is Screen.EmulatorOptions }
        return games
    }

    // An open Game Settings page (the console rows are shared with Emulator Settings) follows a second screen coming and going.
    @Test fun an_open_page_gains_and_loses_the_bottom_screen_row_with_the_second_screen() = runTest {
        val state = TestState(MemoryConfigs())
        openGame(state)
        fun keys() = options(state).rows.filterIsInstance<EmulatorRow.Console>().map { it.row.option.key }
        assertFalse(BottomScreenOptions.KEY in keys())
        state.onSecondScreenChanged(true)
        await { BottomScreenOptions.KEY in keys() }
        state.onSecondScreenChanged(false)
        await { BottomScreenOptions.KEY !in keys() }
    }

    // GBA with its Controller Layout locked in the cfg and no core options: Bottom screen is the page's only row.
    @Test fun a_page_left_empty_when_the_second_screen_goes_closes() = runTest {
        val noOptions = object : CoreOptionSource { override fun table(coreId: String) = CoreOptionTable(coreId, "gpSP", emptyList()) }
        val state = TestState(MemoryConfigs(mapOf("GBA-gpsp/minarch.cfg" to "-${PadOptions.LAYOUT} = ${PadOptions.AUTO}\n")), tables = noOptions)
        state.onSecondScreenChanged(true)
        openCore(state, "gpsp")
        await { state.screen.value is Screen.EmulatorOptions }
        assertEquals(listOf(BottomScreenOptions.KEY), options(state).rows.filterIsInstance<EmulatorRow.Console>().map { it.row.option.key })
        state.onSecondScreenChanged(false)
        await { state.screen.value is Screen.EmulatorList }   // back to the core list, as B goes
        assertEquals("gpsp", assertIs<Screen.EmulatorList>(state.screen.value).rows[state.selection.value.index].coreId)
    }

    @Test fun a_first_game_change_writes_a_snapshot_with_the_console_binds() = runTest {
        val configs = MemoryConfigs(mapOf("GBA-gpsp/minarch.cfg" to "bind Up = UP\nopt_a = z\n"))
        val state = TestState(configs)
        openGame(state)
        assertEquals("Mario", options(state).game?.game?.shownName)
        assertFalse(EmulatorRow.UseEmulatorSettings in options(state).rows)
        selectOptA(state)
        state.onButton(NxButton.A)   // A on a value row is RIGHT
        await { onOptA(state) && coreValue(state, "opt_a") == "x" }
        val text = configs.files.getValue("GBA-gpsp/Mario.cfg").lines()
        assertTrue("bind Up = UP" in text)
        assertTrue("opt_a = x" in text)
        assertEquals("bind Up = UP\nopt_a = z\n", configs.files["GBA-gpsp/minarch.cfg"])   // the console file is untouched
        assertEquals(EmulatorRow.UseEmulatorSettings, options(state).rows.last())
        assertEquals("opt_a", (selected(state) as EmulatorRow.Core).option.key)
    }

    @Test fun use_emulator_settings_deletes_the_game_file() = runTest {
        val configs = MemoryConfigs(mapOf("GBA-gpsp/minarch.cfg" to "opt_a = z\n", "GBA-gpsp/Mario.cfg" to "opt_a = y\n"))
        val state = TestState(configs)
        openGame(state)
        assertEquals("y", coreValue(state, "opt_a"))
        repeat(options(state).rows.size) { if (selected(state) != EmulatorRow.UseEmulatorSettings) state.onButton(NxButton.DOWN) }
        assertEquals(EmulatorRow.UseEmulatorSettings, selected(state))
        state.onButton(NxButton.A)
        await { EmulatorRow.UseEmulatorSettings !in options(state).rows && selected(state) != EmulatorRow.UseEmulatorSettings }
        assertNull(configs.files["GBA-gpsp/Mario.cfg"])
        assertEquals("z", coreValue(state, "opt_a"))
    }

    @Test fun b_from_a_game_page_restores_the_game_list() = runTest {
        val state = TestState(MemoryConfigs())
        val games = openGame(state)
        state.onButton(NxButton.B)
        assertEquals(games, state.screen.value)
        assertEquals(0, state.selection.value.index)
    }

    // --- Game Settings is a modal over its game list ---

    @Test fun game_settings_is_a_modal_over_the_list_it_came_from() = runTest {
        val state = TestState(MemoryConfigs())
        val games = openGame(state)
        assertTrue(gameSettingsModal(state.screen.value))
        assertEquals(games to ListSelection(0, games.entries.size), state.gameSettingsUnder())
    }

    @Test fun in_game_settings_b_steps_out_of_a_category_then_closes_on_the_same_game() = runTest {
        val state = TestState(MemoryConfigs())
        val games = openGame(state)
        repeat(options(state).rows.size) { if (selected(state) !is EmulatorRow.Category) state.onButton(NxButton.DOWN) }
        state.onButton(NxButton.A)
        assertEquals("video", options(state).category)
        assertTrue(gameSettingsModal(state.screen.value))                      // the sub-page stays in the modal
        state.onButton(NxButton.B)
        assertNull(options(state).category)
        state.onButton(NxButton.B)
        assertEquals(games, state.screen.value)
        assertEquals(0, state.selection.value.index)
        assertNull(state.gameSettingsUnder())
    }

    @Test fun a_tap_on_the_scrim_closes_game_settings_from_any_level() = runTest {
        val state = TestState(MemoryConfigs())
        val games = openGame(state)
        repeat(options(state).rows.size) { if (selected(state) !is EmulatorRow.Category) state.onButton(NxButton.DOWN) }
        state.onButton(NxButton.A)                                             // inside a category
        state.closeGameSettings()
        assertEquals(games, state.screen.value)
        assertEquals(0, state.selection.value.index)
    }

    @Test fun closing_does_nothing_on_the_console_wide_page() = runTest {
        val state = TestState(MemoryConfigs())
        openCore(state, "gpsp")
        await { state.screen.value is Screen.EmulatorOptions }
        val page = state.screen.value
        assertFalse(gameSettingsModal(page))
        state.closeGameSettings()
        assertEquals(page, state.screen.value)
    }

    // --- Game Settings from the context menu ---

    private fun chooseGameSettings(state: AppState) {
        state.openContextMenu()
        val menu = assertIs<Screen.Games>(state.screen.value).menu!!
        state.menuSelect(menu.items.indexOfFirst { it.kind == ContextKind.GAME_SETTINGS })
        state.menuOpen()
    }

    @Test fun game_settings_in_the_context_menu_opens_the_games_own_page_and_b_returns_to_the_list() = runTest {
        val state = TestState(MemoryConfigs())
        state.load()
        await { state.screen.value is Screen.Systems }
        val systems = assertIs<Screen.Systems>(state.screen.value)
        state.openSystem(systems.tabs.flatMap { it.rows }.filterIsInstance<MainRow.System>().first { it.system.tag == "GBA" }.system)
        val games = assertIs<Screen.Games>(state.screen.value)
        chooseGameSettings(state)
        await { state.screen.value is Screen.EmulatorOptions }
        val page = options(state)
        assertEquals("GBA", page.page.tag)
        assertTrue(page.page.forGame)
        assertEquals("Mario", page.game?.game?.shownName)
        assertEquals("Mario", emulatorOptionsTitle(page))
        state.onButton(NxButton.B)
        await { state.screen.value is Screen.Games }
        assertEquals(games, state.screen.value)   // the menu is closed
        assertEquals(0, state.selection.value.index)
    }

    @Test fun b_from_game_settings_shows_the_list_as_it_is_now_on_the_same_game() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms", "Collections"),
                "Home/Roms" to listOf("Game Boy Advance (GBA)", "Sega Genesis (MD)"),
                "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
                "Home/Roms/Sega Genesis (MD)" to listOf("Sonic.md"),
                "Home/Collections" to listOf("Mix.txt"),
            ),
            texts = mapOf("Home/Collections/Mix.txt" to "/Roms/Game Boy Advance (GBA)/Mario.gba\n/Roms/Sega Genesis (MD)/Sonic.md\n"),
        )
        val state = TestState(MemoryConfigs(), src)
        state.load()
        await { state.screen.value is Screen.Systems }
        state.openList(ListSource.Collection("Mix"))
        await { state.screen.value is Screen.Games }
        assertEquals(listOf("Mario", "Sonic"), assertIs<Screen.Games>(state.screen.value).entries.map { it.game.shownName })
        state.select(1)
        chooseGameSettings(state)
        await { state.screen.value is Screen.EmulatorOptions }
        assertEquals("MD", options(state).page.tag)
        assertEquals("Sonic", options(state).game?.game?.shownName)
        src.writeText(ContentRef("Home/Collections"), "Mix.txt", "/Roms/Sega Genesis (MD)/Sonic.md\n")   // the list changed meanwhile
        state.onButton(NxButton.B)
        await { (state.screen.value as? Screen.Games)?.entries?.size == 1 }
        assertEquals(listOf("Sonic"), assertIs<Screen.Games>(state.screen.value).entries.map { it.game.shownName })
        assertEquals(0, state.selection.value.index)   // still on Sonic
    }
}
