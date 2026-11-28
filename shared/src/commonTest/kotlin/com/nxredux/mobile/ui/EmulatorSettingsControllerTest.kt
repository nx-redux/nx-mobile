package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.netplay.cablelink.LinkRomCache
import com.nxredux.mobile.netplay.cablelink.MemoryCacheStore
import com.nxredux.mobile.options.BottomScreenOptions
import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.options.ConsoleRow
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.CoreOptionSource
import com.nxredux.mobile.options.CoreOptionTable
import com.nxredux.mobile.options.EmulatorPage
import com.nxredux.mobile.options.PadOptions
import com.nxredux.mobile.options.AudioOptions
import com.nxredux.mobile.options.SpeedOptions
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.FakeAppSettings
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
import com.nxredux.mobile.runner.MapKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Files by "<TAG>-<coreId>/minarch.cfg" and "<TAG>-<coreId>/<rom>.cfg". */
internal class MemoryConfigs(initial: Map<String, String> = emptyMap()) : EmulatorConfigStore {
    val files = HashMap(initial)
    var failWrites = false
    private fun console(tag: String, core: String) = "$tag-$core/minarch.cfg"
    private fun game(tag: String, core: String, rom: String) = "$tag-$core/${rom.substringBeforeLast('.')}.cfg"
    override suspend fun read(tag: String, coreId: String, romFileName: String?): Pair<ConfigFile?, ConfigFile?> =
        files[console(tag, coreId)]?.let(CoreConfig::parse) to romFileName?.let { files[game(tag, coreId, it)] }?.let(CoreConfig::parse)
    override suspend fun writeConsole(tag: String, coreId: String, text: String) { if (failWrites) error("disk full"); files[console(tag, coreId)] = text }
    override suspend fun writeGame(tag: String, coreId: String, romFileName: String, text: String) { if (failWrites) error("disk full"); files[game(tag, coreId, romFileName)] = text }
    override suspend fun deleteGame(tag: String, coreId: String, romFileName: String) { files.remove(game(tag, coreId, romFileName)) }
    override suspend fun deleteConsole(tag: String, coreId: String) { files.remove(console(tag, coreId)) }
}

class EmulatorSettingsControllerTest {
    // --- pure: rows ---

    private val optA = CoreOption("opt_a", "x", label = "Option A", values = listOf("x", "y", "z"), defaultValue = "x")
    private val optB = CoreOption(
        "opt_b", "on", label = "Option B", labelCategorized = "B", category = "video", categoryLabel = "Video",
        values = listOf("on", "off"), defaultValue = "on",
    )
    private val layout = ConsoleRow(PadOptions.segaPadRow, PadOptions.segaPadRow.default)

    private fun page(console: List<ConsoleRow> = listOf(layout), core: List<CoreOption>? = listOf(optA, optB), forGame: Boolean = false, gameFile: Boolean = false) =
        EmulatorPage("GBA", "gpsp", "gpSP", console, core, forGame, gameFile)

    @Test fun top_level_has_console_then_core_rows_then_categories() {
        assertEquals(
            listOf(
                EmulatorRow.Header("Controls"), EmulatorRow.Console(layout),
                EmulatorRow.Gap,   // half a row of space between the app's and the core settings
                EmulatorRow.Header("Emulator"), EmulatorRow.Core(optA, inCategory = false), EmulatorRow.Category("video", "Video"),
                EmulatorRow.Gap, EmulatorRow.ResetDefaults,
            ),
            EmulatorRows.build(page(), null),
        )
    }

    @Test fun the_gap_is_only_between_console_and_core_settings() {
        assertEquals(listOf(EmulatorRow.Gap), EmulatorRows.build(page(console = emptyList()), null).filter { it == EmulatorRow.Gap })   // no console rows: only Reset's
        assertFalse(EmulatorRow.Gap in EmulatorRows.build(page(), "video"))                     // a category: core rows only
        assertEquals(2, EmulatorRows.build(page(core = null), null).count { it == EmulatorRow.Gap })   // before the notice's header, and Reset's
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
        assertFalse(EmulatorRow.Header("Emulator").selectable)
    }

    @Test fun the_gap_draws_as_half_a_row_of_space() {
        val rows = EmulatorRows.build(page(), null).map { NxRow("", spacer = it == EmulatorRow.Gap) }
        assertEquals(20.dp * 6 + 10.dp * 2, nxOptionsListHeight(rows, 20.dp))   // 6 rows and two gaps (the last before Reset to defaults)
    }

    @Test fun a_game_page_with_a_game_file_ends_with_use_emulator_settings() {
        val rows = EmulatorRows.build(page(forGame = true, gameFile = true), null)
        assertEquals(EmulatorRow.UseEmulatorSettings, rows.last())
        assertEquals(EmulatorRow.Gap, rows[rows.size - 2])
        assertFalse(EmulatorRow.UseEmulatorSettings in EmulatorRows.build(page(forGame = true, gameFile = false), null))
    }

    @Test fun a_core_without_a_table_shows_the_notice_and_no_console_header_without_console_rows() {
        assertEquals(
            listOf(EmulatorRow.Header("Emulator"), EmulatorRow.Notice("This emulator's options aren't available outside a game.")),
            EmulatorRows.build(page(console = emptyList(), core = null), null),
        )
    }

    @Test fun an_empty_core_table_gets_no_core_header() {
        assertEquals(listOf(EmulatorRow.Header("Controls"), EmulatorRow.Console(layout), EmulatorRow.Gap, EmulatorRow.ResetDefaults), EmulatorRows.build(page(core = emptyList()), null))
    }

    // The app's settings under a header per group (Display, Controls, Gameplay, Audio), in group order, a gap between.
    @Test fun the_console_rows_are_grouped_by_what_they_are_for() {
        val speed = ConsoleRow(SpeedOptions.table.first { it.key == SpeedOptions.MAX_FF }, "4")
        val audio = ConsoleRow(AudioOptions.row, AudioOptions.row.default)
        val bottom = ConsoleRow(BottomScreenOptions.row, BottomScreenOptions.row.default)
        assertEquals(
            listOf(
                EmulatorRow.Header("Display"), EmulatorRow.Console(bottom), EmulatorRow.Gap,
                EmulatorRow.Header("Controls"), EmulatorRow.Console(layout), EmulatorRow.Gap,
                EmulatorRow.Header("Gameplay"), EmulatorRow.Console(speed), EmulatorRow.Gap,
                EmulatorRow.Header("Audio"), EmulatorRow.Console(audio), EmulatorRow.Gap,
                EmulatorRow.ResetDefaults,
            ),
            EmulatorRows.build(page(console = listOf(layout, audio, speed, bottom), core = emptyList()), null),
        )
    }

    @Test fun inside_a_category_only_its_rows_marked_in_category() {
        assertEquals(listOf(EmulatorRow.Core(optB, inCategory = true)), EmulatorRows.build(page(), "video"))
    }

    private val delayRow = EmulatorRow.App(SettingsRow(SettingsKind.CABLE_LINK_DELAY, "Input delay", "1 frame"), SettingChoices(listOf("Auto", "1 frame"), 1))
    private val clearRow = EmulatorRow.App(SettingsRow(SettingsKind.CLEAR_LINK_ROMS, "Clear link ROM cache", "0 B"), null)
    private val cableLink = EmulatorAppSection("Cable Link", listOf(delayRow, clearRow))

    // The app's own rows a page hosts (mGBA's Cable Link) come last, after a gap, under their header; a category has none.
    @Test fun hosted_app_rows_come_last_under_their_header() {
        assertEquals(
            listOf(
                EmulatorRow.Header("Controls"), EmulatorRow.Console(layout), EmulatorRow.Gap,
                EmulatorRow.Header("Emulator"), EmulatorRow.Core(optA, inCategory = false), EmulatorRow.Category("video", "Video"), EmulatorRow.Gap,
                EmulatorRow.ResetDefaults, EmulatorRow.Gap,   // the reset leaves the app-wide rows alone, so it comes before them
                EmulatorRow.Header("Cable Link"), delayRow, clearRow,
            ),
            EmulatorRows.build(page(), null, cableLink),
        )
        assertEquals(listOf(EmulatorRow.Core(optB, inCategory = true)), EmulatorRows.build(page(), "video", cableLink))
        assertTrue(delayRow.selectable && clearRow.selectable)
    }

    // Cable Link always runs mGBA, so its settings sit on mGBA's own page, for the tags Cable Link is offered on.
    @Test fun only_mgbas_page_for_a_cable_link_tag_hosts_cable_link() {
        fun hosts(tag: String, core: String, forGame: Boolean = false) = EmulatorRows.hostsCableLink(EmulatorPage(tag, core, core, emptyList(), null, forGame, false))
        assertTrue(hosts("MGBA", "mgba"))
        assertFalse(hosts("GBA", "gpsp"))
        assertFalse(hosts("SGB", "mgba"))                    // no Cable Link on the Super Game Boy
        assertFalse(hosts("GB", "gambatte"))
        assertFalse(hosts("MGBA", "mgba", forGame = true))   // a game's own settings: these are the app's, not the game's
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

    // A tag listed without games (mGBA's MGBA, where Cable Link exists) joins its core's row, in registry order.
    @Test fun list_adds_a_tag_listed_without_games() {
        assertEquals(
            listOf(EmulatorListRow("gpsp", "gpSP", listOf("GBA")), EmulatorListRow("mgba", "mGBA", listOf("MGBA"))),
            EmulatorSettingsController.listRows(listOf(system("GBA")), always = listOf("MGBA")),
        )
        assertEquals(
            listOf(EmulatorListRow("mgba", "mGBA", listOf("MGBA", "SGB"))),
            EmulatorSettingsController.listRows(listOf(system("SGB")), always = listOf("MGBA")),
        )
        assertEquals(listOf("gpSP"), EmulatorSettingsController.listRows(listOf(system("GBA"))).map { it.emulator })
    }

    // --- controller through AppState ---

    private class MemorySettings(treeRef: ContentRef? = null) : FakeAppSettings(treeRef) {
        override var cableLinkDelayPin = com.nxredux.mobile.netplay.NetplayCores.CABLE_LINK_DELAY_DEFAULT
        override var cableLinkDelayOverlay = false
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
     * and the work would land on pool threads while the test thread drove the same state.
     */
    private fun TestScope.TestState(
        configs: MemoryConfigs, src: FakeRomsSource = source, tables: CoreOptionSource = Tables(), linkRomCache: LinkRomCache? = null,
        settings: MemorySettings = MemorySettings(ContentRef("Home")),
    ) = AppState(
        AppDeps(settings, src, NoopRunner, NoopFolderPicker, coreOptions = tables, emulatorConfigs = configs, compute = UnconfinedTestDispatcher(testScheduler), linkRomCache = linkRomCache),
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

    /** [openCore], waited until its options page shows. */
    private fun TestScope.openOptions(state: AppState, coreId: String) {
        openCore(state, coreId)
        await { state.screen.value is Screen.EmulatorOptions }
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
        assertEquals(listOf(SettingsKind.DEFAULTS, SettingsKind.EMULATORS, SettingsKind.SAVE_ON_QUIT, SettingsKind.A_RESUMES), assertIs<Screen.Settings>(state.screen.value).rows.map { it.kind })   // Saves under Library, Pause when unfocused under System
        state.onButton(NxButton.B)
        state.openSettings(SettingsSection.LIBRARY)
        val kinds = assertIs<Screen.Settings>(state.screen.value).rows.map { it.kind }
        assertTrue(SettingsKind.EMULATORS !in kinds && SettingsKind.DEFAULTS !in kinds)
    }

    @Test fun right_on_a_core_row_writes_the_console_file_and_shows_the_value() = runTest {
        val configs = MemoryConfigs()
        val state = TestState(configs)
        openOptions(state, "gpsp")
        assertTrue(selected(state) !is EmulatorRow.Header)   // the first selectable row
        selectOptA(state)
        state.padStep(NxButton.RIGHT)
        await { onOptA(state) && coreValue(state, "opt_a") == "y" }
        assertTrue("opt_a = y" in configs.files.getValue("GBA-gpsp/minarch.cfg").lines())
        assertEquals("opt_a", (selected(state) as EmulatorRow.Core).option.key)   // the selection stays on the row
        state.padStep(NxButton.LEFT); state.padStep(NxButton.LEFT)   // serialised: each change reads what the last wrote
        await { onOptA(state) && coreValue(state, "opt_a") == "z" }
    }

    // The page is rebuilt after each change: the change goes on with it, on the same row, until A or B.
    @Test fun a_change_goes_on_across_the_rebuilt_page() = runTest {
        val configs = MemoryConfigs()
        val state = TestState(configs)
        openOptions(state, "gpsp")
        selectOptA(state)
        state.onButton(NxButton.A)
        state.onButton(NxButton.RIGHT)
        await { onOptA(state) && coreValue(state, "opt_a") == "y" }
        assertEquals(state.selection.value.index, state.rowEdit.value?.index)
        state.onButton(NxButton.RIGHT)
        await { onOptA(state) && coreValue(state, "opt_a") == "z" }
        state.onButton(NxButton.B)
        assertNull(state.rowEdit.value)
    }

    // Touch only, a value row of three or more opens a chooser of its values; a pick writes it as stepping would.
    @Test fun touch_a_core_option_opens_a_picker_and_a_pick_writes_it() = runTest {
        val configs = MemoryConfigs()
        val state = TestState(configs)
        state.onFooterMode(FooterMode.BUTTONS)
        openOptions(state, "gpsp")
        selectOptA(state)
        state.settingEvent(NxSettingEvent.OpenPicker(state.selection.value.index))
        val chooser = state.chooser.value ?: error("no chooser")
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
        openOptions(state, "gpsp")
        selectOptA(state)
        configs.failWrites = true
        state.onButton(NxButton.A); state.onButton(NxButton.RIGHT)   // the change: its error stays up
        await { state.error.value != null }
        assertEquals("Could not save the setting", state.error.value)
        assertEquals("z", coreValue(state, "opt_a"))
    }

    @Test fun a_category_opens_and_b_returns_to_its_row() = runTest {
        val state = TestState(MemoryConfigs())
        openOptions(state, "gpsp")
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
        openOptions(state, "gpsp")
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

    // GBA with its speed and audio rows locked in the cfg and no core options: Bottom screen is its only value row, and
    // Button Mapping keeps the page open when it goes.
    @Test fun a_console_page_keeps_button_mapping_when_the_second_screen_goes() = runTest {
        val noOptions = object : CoreOptionSource { override fun table(coreId: String) = CoreOptionTable(coreId, "gpSP", emptyList()) }
        val state = TestState(MemoryConfigs(mapOf("GBA-gpsp/minarch.cfg" to
            (SpeedOptions.table + AudioOptions.rows).joinToString("") { "-${it.key} = ${it.default}\n" })), tables = noOptions)
        state.onSecondScreenChanged(true)
        openOptions(state, "gpsp")
        assertEquals(listOf(BottomScreenOptions.KEY), options(state).rows.filterIsInstance<EmulatorRow.Console>().map { it.row.option.key })
        state.onSecondScreenChanged(false)
        await { options(state).rows.none { it is EmulatorRow.Console } }
        assertEquals(listOf(EmulatorRow.Header("Controls"), EmulatorRow.ButtonMapping, EmulatorRow.Gap, EmulatorRow.ResetDefaults), options(state).rows)
    }

    @Test fun a_first_game_change_writes_a_snapshot_with_the_console_binds() = runTest {
        val configs = MemoryConfigs(mapOf("GBA-gpsp/minarch.cfg" to "bind Up = UP\nopt_a = z\n"))
        val state = TestState(configs)
        openGame(state)
        assertEquals("Mario", options(state).game?.game?.shownName)
        assertFalse(EmulatorRow.UseEmulatorSettings in options(state).rows)
        selectOptA(state)
        state.padStep(NxButton.RIGHT)
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

    @Test fun the_emulator_page_ends_with_reset_to_defaults_and_a_game_page_never_has_it() {
        val rows = EmulatorRows.build(page(), null)
        assertEquals(EmulatorRow.ResetDefaults, rows.last())
        assertEquals(EmulatorRow.Gap, rows[rows.size - 2])
        assertFalse(EmulatorRow.ResetDefaults in EmulatorRows.build(page(forGame = true, gameFile = true), null))
        assertFalse(EmulatorRow.ResetDefaults in EmulatorRows.build(page(console = emptyList(), core = emptyList()), null))
    }

    @Test fun reset_to_defaults_asks_then_deletes_the_console_file_and_keeps_game_files() = runTest {
        val configs = MemoryConfigs(mapOf("GBA-gpsp/minarch.cfg" to "opt_a = z\n", "GBA-gpsp/Mario.cfg" to "opt_a = y\n"))
        val state = TestState(configs)
        openOptions(state, "gpsp")
        assertEquals("z", coreValue(state, "opt_a"))
        repeat(options(state).rows.size) { if (selected(state) != EmulatorRow.ResetDefaults) state.onButton(NxButton.DOWN) }
        assertEquals(EmulatorRow.ResetDefaults, selected(state))
        state.onButton(NxButton.A)
        assertTrue(options(state).confirmReset)
        state.onButton(NxButton.B)   // B keeps everything
        assertFalse(options(state).confirmReset)
        assertEquals("opt_a = z\n", configs.files["GBA-gpsp/minarch.cfg"])
        state.onButton(NxButton.A)
        state.onButton(NxButton.A)   // A on the question resets
        await { !options(state).confirmReset && coreValue(state, "opt_a") == "x" }
        assertNull(configs.files["GBA-gpsp/minarch.cfg"])
        assertEquals("opt_a = y\n", configs.files["GBA-gpsp/Mario.cfg"])
        assertEquals(EmulatorRow.ResetDefaults, selected(state))
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
        openOptions(state, "gpsp")
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

    // --- Cable Link on mGBA's page ---

    private val gbaSource = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Game Boy Advance (GBA)"),
            "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
        ),
    )
    private val cableKinds = listOf(SettingsKind.CABLE_LINK_DELAY, SettingsKind.CABLE_LINK_DELAY_OVERLAY, SettingsKind.CLEAR_LINK_ROMS)

    private fun cacheOf(bytes: Int = 0) = LinkRomCache(MemoryCacheStore().also { if (bytes > 0) it.files["a".repeat(40)] = ByteArray(bytes) })
    /** GBA games on mGBA ([mgba]: Default emulators picks mGBA (MGBA) for Game Boy Advance, as a player must to see its page) or on gpSP. */
    private fun TestScope.mgbaState(cache: LinkRomCache?, mgba: Boolean = true) = TestState(
        MemoryConfigs(), src = gbaSource, tables = Tables(names + ("mgba" to "mGBA")), linkRomCache = cache,
        settings = MemorySettings(ContentRef("Home")).also { if (mgba) it.defaultTags = mapOf("gba" to "MGBA") },
    )
    private fun appRows(state: AppState) = options(state).rows.filterIsInstance<EmulatorRow.App>()
    private fun appRow(state: AppState, kind: SettingsKind) = appRows(state).single { it.row.kind == kind }
    private fun selectApp(state: AppState, kind: SettingsKind) {
        repeat(options(state).rows.size) { if ((selected(state) as? EmulatorRow.App)?.row?.kind != kind) state.onButton(NxButton.DOWN) }
        assertEquals(kind, (selected(state) as EmulatorRow.App).row.kind)
    }

    @Test fun mgbas_page_ends_with_cable_link_where_cable_link_exists() = runTest {
        val state = mgbaState(cacheOf())
        openOptions(state, "mgba")
        assertEquals("MGBA" to "mgba", options(state).page.tag to options(state).page.coreId)
        val rows = options(state).rows
        assertEquals(cableKinds, rows.takeLast(3).map { (it as EmulatorRow.App).row.kind })
        assertEquals(EmulatorRow.Header("Cable Link"), rows[rows.size - 4])
        assertEquals(EmulatorRow.Gap, rows[rows.size - 5])
        assertEquals(listOf("Input delay", "Delay overlay", "Clear link ROM cache"), appRows(state).map { it.row.label })
        assertEquals(listOf("1 frame", "Off", "0 B"), appRows(state).map { it.row.secondary })
    }

    @Test fun mgbas_page_has_no_cable_link_without_a_link_rom_cache() = runTest {
        val state = mgbaState(null)
        openOptions(state, "mgba")
        assertEquals(emptyList(), appRows(state))
        assertFalse(EmulatorRow.Header("Cable Link") in options(state).rows)
    }

    @Test fun gpsps_page_has_no_cable_link() = runTest {
        val state = mgbaState(cacheOf(), mgba = false)
        openOptions(state, "gpsp")
        assertEquals("gpsp", options(state).page.coreId)
        assertEquals(emptyList(), appRows(state))
    }

    // Each row changes its setting as a Settings row does, and the page stays, its selection on the row.
    @Test fun the_cable_link_rows_change_their_settings_on_the_page() = runTest {
        val state = mgbaState(cacheOf())
        openOptions(state, "mgba")
        selectApp(state, SettingsKind.CABLE_LINK_DELAY)
        state.padStep(NxButton.RIGHT)
        assertEquals("2 frames", appRow(state, SettingsKind.CABLE_LINK_DELAY).row.secondary)
        assertEquals(SettingsKind.CABLE_LINK_DELAY, (selected(state) as EmulatorRow.App).row.kind)
        state.padStep(NxButton.LEFT); state.padStep(NxButton.LEFT)
        assertEquals("Auto", appRow(state, SettingsKind.CABLE_LINK_DELAY).row.secondary)
        state.padStep(NxButton.LEFT)   // wraps, as on Settings
        assertEquals("8 frames", appRow(state, SettingsKind.CABLE_LINK_DELAY).row.secondary)
        selectApp(state, SettingsKind.CABLE_LINK_DELAY_OVERLAY)
        state.onButton(NxButton.A)
        assertEquals("On", appRow(state, SettingsKind.CABLE_LINK_DELAY_OVERLAY).row.secondary)
        assertIs<Screen.EmulatorOptions>(state.screen.value)
    }

    @Test fun input_delay_opens_its_picker_and_a_pick_sets_it() = runTest {
        val state = mgbaState(cacheOf())
        openOptions(state, "mgba")
        selectApp(state, SettingsKind.CABLE_LINK_DELAY)
        state.settingEvent(NxSettingEvent.OpenPicker(state.selection.value.index))
        val chooser = state.chooser.value ?: error("no chooser")
        assertEquals("Input delay", chooser.title)
        assertEquals(listOf("Auto", "1 frame") + (2..8).map { "$it frames" }, chooser.labels)
        assertEquals(1, chooser.selected)
        state.chooseValue(0)
        assertEquals("Auto", appRow(state, SettingsKind.CABLE_LINK_DELAY).row.secondary)
        assertIs<Screen.EmulatorOptions>(state.screen.value)
    }

    @Test fun clear_link_rom_cache_empties_it_and_shows_the_new_size() = runTest {
        val cache = cacheOf(2048)
        val state = mgbaState(cache)
        openOptions(state, "mgba")
        assertEquals("2.0 KB", appRow(state, SettingsKind.CLEAR_LINK_ROMS).row.secondary)
        selectApp(state, SettingsKind.CLEAR_LINK_ROMS)
        state.onButton(NxButton.A)
        assertEquals(0L, cache.sizeBytes())
        assertEquals("0 B", appRow(state, SettingsKind.CLEAR_LINK_ROMS).row.secondary)
        assertEquals(SettingsKind.CLEAR_LINK_ROMS, (selected(state) as EmulatorRow.App).row.kind)
    }

    // The rows draw and explain themselves as on Settings: the same touch row, the same help text behind X.
    @Test fun the_cable_link_rows_draw_and_explain_as_on_settings() = runTest {
        val state = mgbaState(cacheOf())
        openOptions(state, "mgba")
        for (r in appRows(state)) assertEquals(settingsTouchRow(r.row, r.choices, SettingsSection.EMULATORS), emulatorTouchRow(r))
        assertEquals("Clear", emulatorTouchRow(appRow(state, SettingsKind.CLEAR_LINK_ROMS)).action)
        selectApp(state, SettingsKind.CABLE_LINK_DELAY)
        state.onButton(NxButton.X)
        assertEquals("Input delay", state.settingInfo.value?.title)
        assertTrue(state.settingInfo.value?.text.orEmpty().startsWith("How many frames a button press waits"))
    }

    // Cable Link sessions run mGBA whatever Game Boy Advance's default is: with gpSP the default, mGBA's page is still listed, Cable Link last.
    @Test fun with_gpsp_the_default_mgba_is_listed_where_cable_link_exists() = runTest {
        val state = mgbaState(cacheOf(), mgba = false)
        val list = openList(state)
        assertEquals(listOf("gpsp", "mgba"), list.rows.map { it.coreId })
        assertEquals(listOf("MGBA"), list.rows.single { it.coreId == "mgba" }.tags)
        state.select(list.rows.indexOfFirst { it.coreId == "mgba" })
        state.onButton(NxButton.A)
        await { state.screen.value is Screen.EmulatorOptions }
        assertEquals("MGBA" to "mgba", options(state).page.tag to options(state).page.coreId)
        assertEquals(cableKinds, options(state).rows.takeLast(3).map { (it as EmulatorRow.App).row.kind })
        assertTrue(options(state).page.core.orEmpty().isNotEmpty())   // mGBA's own options too
    }

    @Test fun with_gpsp_the_default_mgba_is_not_listed_without_cable_link() = runTest {
        val state = mgbaState(null, mgba = false)
        assertEquals(listOf("gpsp"), openList(state).rows.map { it.coreId })
    }

    // --- Button Mapping on a console's page ---

    private val padSource = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Sony PlayStation (PS)", "Super Nintendo ES (SFC)", "Sega Dreamcast (DC)"),
            "Home/Roms/Sony PlayStation (PS)" to listOf("Crash.chd"),
            "Home/Roms/Sega Dreamcast (DC)" to listOf("Crazy Taxi.chd"),
            "Home/Roms/Super Nintendo ES (SFC)" to listOf("Mario.sfc"),
        ),
    )

    private fun select(state: AppState, row: (EmulatorRow) -> Boolean) {
        repeat(options(state).rows.size) { if (!row(selected(state))) state.onButton(NxButton.DOWN) }
        assertTrue(row(selected(state)))
    }

    private fun TestScope.openMapping(state: AppState, coreId: String) {
        openOptions(state, coreId)
        select(state) { it == EmulatorRow.ButtonMapping }
        state.onButton(NxButton.A)
        assertTrue(options(state).buttonMapping)
    }

    private fun bindRow(state: AppState, label: String) = options(state).rows.filterIsInstance<EmulatorRow.MapBind>().single { it.label == label }

    @Test fun consolePageHasButtonMapping() = runTest {
        val state = TestState(MemoryConfigs(), src = padSource)
        openOptions(state, "pcsx_rearmed")
        val rows = options(state).rows
        val at = rows.indexOf(EmulatorRow.ButtonMapping)
        val controls = rows.indexOf(EmulatorRow.Header("Controls"))
        assertTrue(controls in 0 until at)
        assertTrue(rows.subList(controls + 1, at).none { it is EmulatorRow.Header || it == EmulatorRow.Gap })   // under Controls
    }

    @Test fun bindWritesConsoleOverride() = runTest {
        val configs = MemoryConfigs()
        val state = TestState(configs, src = padSource)
        openMapping(state, "pcsx_rearmed")
        assertTrue(emulatorOptionsTitle(options(state)).endsWith("Sony PlayStation (PS) › Button Mapping"))
        select(state) { (it as? EmulatorRow.MapBind)?.label == "×" }
        state.onButton(NxButton.A)
        assertTrue(bindRow(state, "×").waiting)
        assertEquals(SHORTCUT_PROMPT, emulatorTouchRow(bindRow(state, "×")).value)
        assertTrue(state.captureKey(97, down = true))
        await { bindRow(state, "×").key.name == "B" }
        assertTrue(state.captureKey(97, down = false))   // B's release stays the wait's
        val text = configs.files.getValue("PS-pcsx_rearmed/minarch.cfg").lines()
        assertTrue("nx_map_A = B" in text && "nx_map_B = A" in text)
        assertFalse(bindRow(state, "×").waiting)
        assertFalse(state.captureKey(96, down = true))   // no wait: the menu's keys again
    }

    @Test fun captureRefusesStartAndBackCancels() = runTest {
        val configs = MemoryConfigs()
        val state = TestState(configs, src = padSource)
        openMapping(state, "pcsx_rearmed")
        select(state) { (it as? EmulatorRow.MapBind)?.label == "×" }
        state.onButton(NxButton.A)
        assertTrue(state.captureKey(108, down = true))   // START: refused, the wait goes on and says which keys bind
        assertNull(state.message.value)
        assertTrue(bindRow(state, "×").waiting)
        assertEquals("Pick A, B, X, Y, L1, R1, L2, R2, L3 or R3. Back cancels.", emulatorTouchRow(bindRow(state, "×")).info)
        assertTrue(state.captureKey(108, down = false))
        assertTrue(state.captureKey(19, down = true))   // the d-pad never binds
        assertTrue(bindRow(state, "×").waiting)
        assertTrue(state.captureKey(19, down = false))
        assertTrue(state.captureKey(4, down = true))   // Back cancels quietly
        assertNull(state.message.value)
        assertFalse(bindRow(state, "×").waiting)
        advanceUntilIdle()
        assertNull(configs.files["PS-pcsx_rearmed/minarch.cfg"])
        state.onButton(NxButton.B)   // out of the list, on its row
        assertFalse(options(state).buttonMapping)
        assertEquals(EmulatorRow.ButtonMapping, selected(state))
    }

    @Test fun dreamcastRefusesTheTriggers() = runTest {
        val configs = MemoryConfigs()
        val state = TestState(configs, src = padSource)
        openMapping(state, "flycast")
        val triggers = options(state).rows.filterIsInstance<EmulatorRow.MapBind>().filter { it.job == MapKey.L2 || it.job == MapKey.R2 }
        assertTrue(triggers.size == 2 && triggers.all { it.locked && !emulatorTouchRow(it).enabled }, triggers.toString())
        // A locked row takes no wait.
        select(state) { (it as? EmulatorRow.MapBind)?.job == MapKey.L2 }
        state.onButton(NxButton.A)
        assertFalse(options(state).rows.filterIsInstance<EmulatorRow.MapBind>().any { it.waiting })
        assertEquals(listOf(Hint(NxButton.B, "Back")), emulatorOptionsHints(selected(state)))
        select(state) { (it as? EmulatorRow.MapBind)?.label == "A" }
        state.onButton(NxButton.A)
        assertTrue(state.captureKey(105, down = true))   // R2
        assertEquals("Pick A, B, X, Y, L1, R1, L3 or R3. Back cancels.", bindRow(state, "A").waitLine)
        assertTrue(state.captureKey(105, down = false))
        assertTrue(state.captureKey(4, down = true))
        assertFalse(bindRow(state, "A").waiting)
        advanceUntilIdle()
        assertTrue(configs.files.keys.none { it.startsWith("DC-") })
    }

    @Test fun listShowsTheBuiltInCombosDimmed() = runTest {
        val state = TestState(MemoryConfigs(), src = padSource)
        openMapping(state, "pcsx_rearmed")
        val rows = options(state).rows
        val combo = rows.filterIsInstance<EmulatorRow.MapCombo>().single()
        assertEquals(EmulatorRow.MapCombo("Menu", "SELECT + START"), combo)
        assertFalse(emulatorTouchRow(combo).enabled)
        assertEquals(EmulatorRow.ResetButtonMap, rows.last())
    }

    @Test fun openingTheListDropsAStaleWait() = runTest {
        val state = TestState(MemoryConfigs(), src = padSource)
        openMapping(state, "pcsx_rearmed")
        select(state) { (it as? EmulatorRow.MapBind)?.label == "×" }
        state.onButton(NxButton.A)
        assertTrue(state.captureKey(97, down = true))
        await { bindRow(state, "×").key.name == "B" }
        state.onButton(NxButton.B)   // out of the list with B still held
        state.onButton(NxButton.A)   // and in again
        assertTrue(options(state).buttonMapping)
        assertFalse(state.captureKey(97, down = false))   // the old wait's release is no longer taken
    }

    @Test fun resetDropsOverrides() = runTest {
        val configs = MemoryConfigs(mapOf("PS-pcsx_rearmed/minarch.cfg" to "nx_map_A = B\nnx_map_B = A\nnx_pad_layout = nintendo\nopt_a = z\n"))
        val state = TestState(configs, src = padSource)
        openMapping(state, "pcsx_rearmed")
        assertEquals("B", bindRow(state, "×").key.name)
        select(state) { it == EmulatorRow.ResetButtonMap }
        state.onButton(NxButton.A)
        await { bindRow(state, "×").key.name == "A" }
        assertEquals("opt_a = z\n", configs.files["PS-pcsx_rearmed/minarch.cfg"])
        assertEquals(EmulatorRow.ResetButtonMap, selected(state))
    }

    @Test fun migratedLegacyShows() = runTest {
        val state = TestState(MemoryConfigs(mapOf("SFC-snes9x/minarch.cfg" to "nx_pad_layout = nintendo\n")), src = padSource)
        openMapping(state, "snes9x")
        assertEquals("B", bindRow(state, "A").key.name)
        assertEquals("A", bindRow(state, "B").key.name)
    }

    @Test fun game_settings_has_no_button_mapping() = runTest {
        val state = TestState(MemoryConfigs())
        openGame(state)
        assertFalse(EmulatorRow.ButtonMapping in options(state).rows)
    }
}
