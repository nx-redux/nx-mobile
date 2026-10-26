package com.nxredux.mobile.options

import com.nxredux.mobile.runner.CoreDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmulatorSettingsTest {
    private val descriptor = CoreDescriptor(
        id = "genesis_plus_gx", libraryFileName = "x.so", extensions = setOf("md"),
        defaultOptions = mapOf("gpgx_region" to "auto", "gpgx_locked" to "x"),
        lockedOptions = setOf("gpgx_locked"),
        hiddenValues = mapOf("gpgx_region" to setOf("pal")),
    )
    private val table = CoreOptionTable("genesis_plus_gx", "Genesis Plus GX", listOf(
        CoreOption("gpgx_region", "ntsc-u", values = listOf("auto", "ntsc-u", "pal"), defaultValue = "ntsc-u"),
        CoreOption("gpgx_locked", "y", values = listOf("x", "y"), defaultValue = "y"),
        CoreOption("gpgx_res", "1x", label = "Resolution (restart)", values = listOf("1x", "2x"), defaultValue = "1x"),
        CoreOption("gpgx_cfglocked", "a", values = listOf("a", "b"), defaultValue = "a"),
    ))
    private fun cfg(text: String) = CoreConfig.parse(text)

    // The rows read only at launch, left out of the in-game menu, are set here.
    @Test fun the_rows_read_at_launch_are_listed() {
        val wii = com.nxredux.mobile.runner.CoreRegistry.forTag("WII")!!
        val keys = EmulatorSettings.page("WII", wii, null, wii.defaultOptions, null, null, forGame = true).console.map { it.option.key }
        assertTrue(keys.containsAll(listOf(GraphicsApiOptions.KEY, WiiOptions.SCREEN, WiiOptions.POINTER_WIDTH, WiiOptions.POINTER_HEIGHT, WiiOptions.POINTER_OFFSET)), "$keys")
    }

    // Bottom screen shows only while a second screen is connected; the stored value stays in the file.
    @Test fun bottom_screen_row_shows_only_with_a_second_screen() {
        val gba = com.nxredux.mobile.runner.CoreRegistry.forTag("GBA")!!
        fun keys(second: Boolean) = EmulatorSettings.page("GBA", gba, null, gba.defaultOptions, cfg("nx_bottom_screen = main\n"), null, forGame = false, secondScreen = second).console.map { it.option.key }
        assertTrue(BottomScreenOptions.KEY in keys(true))
        assertTrue(BottomScreenOptions.KEY !in keys(false))
    }

    // The Graphics API row's info notes a Vulkan failure recorded for the core on this phone.
    @Test fun the_graphics_api_row_notes_a_vulkan_failure() {
        val n64 = com.nxredux.mobile.runner.CoreRegistry.forTag("N64")!!
        fun info(failed: Boolean) = EmulatorSettings.page("N64", n64, null, n64.defaultOptions, null, null, forGame = false, vulkanFailed = failed)
            .console.single { it.option.key == GraphicsApiOptions.KEY }.option.info
        assertEquals(GraphicsApiOptions.row.info, info(false))
        assertTrue(info(true).startsWith(VulkanFallback.NOTE))
    }

    // The page keeps each row's full text (behind the info icon; the row shows [FrontendOption.brief]).
    @Test fun console_rows_keep_the_full_text() {
        val nds = com.nxredux.mobile.runner.CoreRegistry.forTag("NDS")!!
        val rows = EmulatorSettings.page("NDS", nds, null, nds.defaultOptions, null, null, forGame = false, secondScreen = true).console.map { it.option }
        val layout = rows.single { it.key == NdsOptions.LAYOUT_PORTRAIT }
        assertEquals(NdsOptions.table.single { it.key == NdsOptions.LAYOUT_PORTRAIT }.info, layout.info)
        assertTrue(layout.info.startsWith("How the two screens share a portrait screen.\nLarge screen:"))
        assertTrue(rows.single { it.key == BottomScreenOptions.KEY }.info.endsWith("System default follows Settings → Second screen."))
    }

    @Test fun values_come_from_cfg_then_descriptor_then_table() {
        val p = EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, cfg("gpgx_res = 2x\n"), null, forGame = false)
        val core = p.core!!.associate { it.key to it.value }
        assertEquals("auto", core["gpgx_region"])   // descriptor default
        assertEquals("2x", core["gpgx_res"])        // cfg, restart option kept
    }

    @Test fun locked_keys_from_descriptor_and_cfg_are_hidden() {
        val p = EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, cfg("-gpgx_cfglocked = b\n"), null, forGame = false)
        val keys = p.core!!.map { it.key }
        assertTrue("gpgx_locked" !in keys); assertTrue("gpgx_cfglocked" !in keys)
    }

    @Test fun hidden_values_are_marked_for_cycling() {
        val p = EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, null, null, forGame = false)
        assertEquals(setOf("pal"), p.core!!.first { it.key == "gpgx_region" }.hiddenValues)
    }

    @Test fun an_unknown_cfg_value_is_kept_as_an_extra_choice() {
        val p = EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, cfg("gpgx_res = 4x\n"), null, forGame = false)
        val res = p.core!!.first { it.key == "gpgx_res" }
        assertEquals("4x", res.value); assertEquals(listOf("1x", "2x", "4x"), res.values)
    }

    @Test fun console_rows_follow_the_tag_and_read_the_cfg() {
        val p = EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, cfg("nx_sega_pad_type = 6\n"), null, forGame = false)
        assertEquals(listOf(BottomScreenOptions.KEY, PadOptions.SEGA_PAD, PadOptions.LAYOUT) + SpeedOptions.table.map { it.key }, p.console.map { it.option.key })
        assertEquals("6", p.console[1].value); assertEquals(PadOptions.AUTO, p.console[2].value)
    }

    // The menu window's screen shapes the DS and 3DS layout rows, as in the game.
    @Test fun layout_rows_follow_the_screen() {
        val nds = com.nxredux.mobile.runner.CoreRegistry.forTag("NDS")!!
        fun landscape(screen: com.nxredux.mobile.layout.ScreenClass?) = EmulatorSettings.page("NDS", nds, null, nds.defaultOptions, null, null, forGame = false, screen = screen)
            .console.single { it.option.key == NdsOptions.LAYOUT_LANDSCAPE }
        val fold = landscape(com.nxredux.mobile.layout.ScreenClass.STUBBY_PHONE)
        assertEquals(NdsLayoutChoice.LARGE.value, fold.value)
        assertTrue(NdsLayoutChoice.SIDE_BY_SIDE.value !in fold.option.values)
        assertEquals(NdsLayoutChoice.SIDE_BY_SIDE.value, landscape(com.nxredux.mobile.layout.ScreenClass.REGULAR_PHONE).value)
        val n3ds = com.nxredux.mobile.runner.CoreRegistry.forTag("3DS")!!
        val tablet = EmulatorSettings.page("3DS", n3ds, null, n3ds.defaultOptions, null, null, forGame = false, screen = com.nxredux.mobile.layout.ScreenClass.WIDE_TABLET)
        assertEquals("large", tablet.console.single { it.option.key == N3dsOptions.LAYOUT_LANDSCAPE }.value)
    }

    @Test fun ndsResolutionRows() {
        val nds = com.nxredux.mobile.runner.CoreRegistry.forTag("NDS")!!
        fun labels(text: String?) = EmulatorSettings.page("NDS", nds, null, nds.defaultOptions, text?.let(::cfg), null, forGame = false, displayMax = 5)
            .console.map { it.option.label }
        val auto = labels(null)
        assertEquals(listOf("3D Resolution", "Max Scale"), auto.subList(auto.indexOf("Layout (landscape)") + 1, auto.indexOf("Layout (landscape)") + 3))
        assertTrue("Scale" !in auto)
        val perf = labels("nx_nds_3d_resolution = performance\n")
        assertTrue("Scale" in perf && "Max Scale" !in perf)
    }

    @Test fun softwareRenderModeDisablesOffline() {
        val nds = com.nxredux.mobile.runner.CoreRegistry.forTag("NDS")!!
        fun notes(text: String?) = EmulatorSettings.page("NDS", nds, null, nds.defaultOptions, text?.let(::cfg), null, forGame = false, displayMax = 5)
            .console.filter { it.option.key in setOf(NdsOptions.MODE_3D, NdsOptions.MAX_SCALE_3D) }.map { it.disabledNote }
        assertEquals(listOf(NdsOptions.SOFTWARE_NOTE, NdsOptions.SOFTWARE_NOTE), notes("melonds_render_mode = software\n"))
        assertEquals(listOf(null, null), notes("melonds_render_mode = opengl\n"))
        assertEquals(listOf(null, null), notes(null))
    }

    @Test fun storedValueOutsideTheListStillShows() {
        val nds = com.nxredux.mobile.runner.CoreRegistry.forTag("NDS")!!
        val row = EmulatorSettings.page("NDS", nds, null, nds.defaultOptions, cfg("nx_nds_3d_max_scale = 7\n"), null, forGame = false, displayMax = 3)
            .console.single { it.option.key == NdsOptions.MAX_SCALE_3D }
        assertEquals("7", row.value)
        assertTrue("7" in row.option.values)
        assertEquals("7x", row.option.labelOf("7"))
    }

    @Test fun threeDsResolutionRowsShowWithoutASoftwareNoteAndKeepAStoredExtraScale() {
        val n3ds = com.nxredux.mobile.runner.CoreRegistry.forTag("3DS")!!
        fun rows(text: String?) = EmulatorSettings.page("3DS", n3ds, null, n3ds.defaultOptions, text?.let(::cfg), null, forGame = false, displayMax = 6)
            .console.filter { it.option.key in com.nxredux.mobile.runner.Res3dConsole.N3DS.keys }
        assertEquals(listOf("3D Resolution", "Max Scale"), rows(null).map { it.option.label })
        assertEquals(listOf(null, null), rows("melonds_render_mode = software\n").map { it.disabledNote })
        val max = rows("nx_3ds_3d_max_scale = 8\n").single { it.option.key == "nx_3ds_3d_max_scale" }
        assertEquals("8", max.value)
        assertEquals("8x", max.option.labelOf("8"))
        assertEquals("3", rows(null).single { it.option.key == "nx_3ds_3d_max_scale" }.value)
    }

    @Test fun anUnknownStoredModeShowsAsAutoWithNoExtraChoice() {
        val nds = com.nxredux.mobile.runner.CoreRegistry.forTag("NDS")!!
        val row = EmulatorSettings.page("NDS", nds, null, nds.defaultOptions, cfg("nx_nds_3d_resolution = bogus\n"), null, forGame = false, displayMax = 3)
            .console.single { it.option.key == NdsOptions.MODE_3D }
        assertEquals("auto", row.value)
        assertEquals(NdsOptions.table.single { it.key == NdsOptions.MODE_3D }.values, row.option.values)
    }

    @Test fun anUnstoredDefaultAboveTheListIsNotOffered() {
        val nds = com.nxredux.mobile.runner.CoreRegistry.forTag("NDS")!!
        val row = EmulatorSettings.page("NDS", nds, null, nds.defaultOptions, null, null, forGame = false, displayMax = 3)
            .console.single { it.option.key == NdsOptions.MAX_SCALE_3D }
        assertEquals("3", row.value)
        assertEquals("3x", row.option.labelOf(row.value))
        assertEquals(listOf("1", "2", "3"), row.option.values)
    }

    @Test fun ds_layout_rows_read_the_old_keys_like_the_game_does() {
        val nds = com.nxredux.mobile.runner.CoreRegistry.forTag("NDS")!!
        fun value(cfgText: String, key: String) = EmulatorSettings.page("NDS", nds, null, nds.defaultOptions, cfg(cfgText), null, forGame = false)
            .console.single { it.option.key == key }.value
        assertEquals("pip", value("nx_nds_layout_landscape = pip\n", NdsOptions.LAYOUT_LANDSCAPE))
        assertEquals("single", value("nx_nds_layout_portrait_v2 = single\n", NdsOptions.LAYOUT_PORTRAIT))
        // An untouched old default, or an old "auto", is no pick: the row default.
        assertEquals(NdsOptions.table.single { it.key == NdsOptions.LAYOUT_LANDSCAPE }.default, value("nx_nds_layout_landscape = stacked\n", NdsOptions.LAYOUT_LANDSCAPE))
        assertEquals(NdsOptions.table.single { it.key == NdsOptions.LAYOUT_PORTRAIT_CONTROLLER }.default, value("nx_nds_layout_portrait_controller = auto\n", NdsOptions.LAYOUT_PORTRAIT_CONTROLLER))
    }

    @Test fun game_page_reads_the_game_file_instead_of_the_console_file() {
        val p = EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions,
            cfg("gpgx_res = 2x\n"), cfg("gpgx_region = ntsc-u\n"), forGame = true)
        val core = p.core!!.associate { it.key to it.value }
        assertEquals("ntsc-u", core["gpgx_region"]); assertEquals("1x", core["gpgx_res"])
        assertTrue(p.gameFileExists)
    }

    @Test fun no_table_gives_null_core_and_keeps_console_rows() {
        val p = EmulatorSettings.page("MD", descriptor, null, descriptor.defaultOptions, null, null, forGame = false)
        assertNull(p.core); assertEquals(3 + SpeedOptions.table.size, p.console.size)
    }

    @Test fun offline_hidden_options_never_reach_the_page() {
        val d = descriptor.copy(offlineHiddenOptions = setOf("gpgx_res"))
        val p = EmulatorSettings.page("MD", d, table, d.defaultOptions, cfg("gpgx_res = 2x\n"), null, forGame = false)
        assertTrue("gpgx_res" !in p.core!!.map { it.key })
    }

    @Test fun melonds_machine_paths_are_offline_hidden() {
        assertEquals(
            setOf("melonds_firmware_nds_path", "melonds_firmware_dsi_path", "melonds_dsi_nand_path", "melonds_direct_network_interface"),
            com.nxredux.mobile.runner.CoreRegistry.forId("melondsds")!!.offlineHiddenOptions,
        )
    }

    @Test fun a_console_change_touches_one_key_and_keeps_locks_and_binds() {
        val before = cfg("bind Up = UP\n-gpgx_cfglocked = b\ngpgx_region = auto\nnx_pad_layout = xbox\n")
        val text = EmulatorSettings.consoleWrite(before, "gpgx_region", "ntsc-u")
        assertEquals("bind Up = UP\n-gpgx_cfglocked = b\ngpgx_region = ntsc-u\nnx_pad_layout = xbox\n", text)
    }

    @Test fun a_console_change_to_a_locked_key_keeps_the_lock() {
        val text = EmulatorSettings.consoleWrite(cfg("-gpgx_cfglocked = b\n"), "gpgx_cfglocked", "a")
        assertEquals("-gpgx_cfglocked = b\n", text)
    }

    @Test fun a_console_change_creates_the_file() {
        assertEquals("nx_pad_layout = nintendo\n", EmulatorSettings.consoleWrite(null, "nx_pad_layout", "nintendo"))
    }

    @Test fun the_first_game_change_snapshots_the_console_file_and_every_core_value() {
        val console = cfg("bind Up = UP\nnx_pad_layout = xbox\ngpgx_res = 2x\n")
        val page = EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, console, null, forGame = true)
        val text = EmulatorSettings.gameWrite(page, console, null, "gpgx_region", "ntsc-u")
        val out = cfg(text)
        assertEquals("UP", text.lines().first().removePrefix("bind Up = "))
        assertEquals("xbox", out.values["nx_pad_layout"])
        assertEquals("2x", out.values["gpgx_res"])
        assertEquals("ntsc-u", out.values["gpgx_region"])
        assertEquals("a", out.values["gpgx_cfglocked"])
    }

    @Test fun a_later_game_change_touches_one_key() {
        val console = cfg("gpgx_res = 2x\n")
        val game = cfg("gpgx_res = 1x\ngpgx_region = auto\n")
        val page = EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, console, game, forGame = true)
        assertEquals("gpgx_res = 1x\ngpgx_region = ntsc-u\n", EmulatorSettings.gameWrite(page, console, game, "gpgx_region", "ntsc-u"))
    }

    @Test fun the_first_game_change_never_writes_offline_hidden_keys() {
        val d = descriptor.copy(offlineHiddenOptions = setOf("gpgx_res"))
        val page = EmulatorSettings.page("MD", d, table, d.defaultOptions, cfg("nx_pad_layout = xbox\n"), null, forGame = true)
        val out = cfg(EmulatorSettings.gameWrite(page, cfg("nx_pad_layout = xbox\n"), null, "gpgx_region", "ntsc-u"))
        assertTrue("gpgx_res" !in out.values)
        assertEquals("ntsc-u", out.values["gpgx_region"])
    }

    @Test fun the_first_game_change_keeps_an_offline_hidden_line_already_in_the_console_file() {
        val d = descriptor.copy(offlineHiddenOptions = setOf("gpgx_res"))
        val console = cfg("gpgx_res = 2x\n")
        val page = EmulatorSettings.page("MD", d, table, d.defaultOptions, console, null, forGame = true)
        val out = cfg(EmulatorSettings.gameWrite(page, console, null, "gpgx_region", "ntsc-u"))
        assertEquals("2x", out.values["gpgx_res"])
    }

    @Test fun a_game_file_counts_only_on_a_game_page() {
        val game = cfg("gpgx_region = ntsc-u\n")
        assertTrue(!EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, null, game, forGame = false).gameFileExists)
        assertTrue(EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, null, game, forGame = true).gameFileExists)
    }

    @Test fun a_table_option_with_a_frontend_key_is_not_a_core_option() {
        val t = table.copy(options = table.options + CoreOption(PadOptions.LAYOUT, "a", values = listOf("a", "b"), defaultValue = "a"))
        val p = EmulatorSettings.page("MD", descriptor, t, descriptor.defaultOptions, null, null, forGame = false)
        assertTrue(PadOptions.LAYOUT !in p.core!!.map { it.key })
    }

    @Test fun a_console_row_locked_in_the_cfg_is_hidden() {
        val p = EmulatorSettings.page("MD", descriptor, table, descriptor.defaultOptions, cfg("-${PadOptions.LAYOUT} = xbox\n"), null, forGame = false)
        assertEquals(listOf(BottomScreenOptions.KEY, PadOptions.SEGA_PAD) + SpeedOptions.table.map { it.key }, p.console.map { it.option.key })
    }

    @Test fun a_legacy_minarch_key_is_renamed_in_place_on_a_one_key_write() {
        val text = EmulatorSettings.consoleWrite(cfg("minarch_pad_layout = xbox\ngpgx_res = 2x\n"), "gpgx_res", "1x")
        assertEquals("nx_pad_layout = xbox\ngpgx_res = 1x\n", text)
    }

    // Graphics API picks the renderer for PSP and N64: those options are locked, so the page lists no row for them and a stored value is ignored.
    private val ppsspp = com.nxredux.mobile.runner.CoreRegistry.forId("ppsspp")!!
    private val ppssppTable = CoreOptionTable("ppsspp", "PPSSPP", listOf(
        CoreOption("ppsspp_backend", "auto", label = "Backend", info = "Core restart required.",
            values = listOf("auto", "opengl", "vulkan", "none"), defaultValue = "auto"),
    ))
    private val mupen = com.nxredux.mobile.runner.CoreRegistry.forId("mupen64plus_next")!!
    private val mupenTable = CoreOptionTable("mupen64plus_next", "Mupen64Plus-Next", listOf(
        CoreOption("mupen64plus-rdp-plugin", "gliden64", values = listOf("angrylion", "parallel", "gliden64"), defaultValue = "gliden64"),
        CoreOption("mupen64plus-rsp-plugin", "hle", values = listOf("cxd4", "parallel", "hle"), defaultValue = "hle"),
        CoreOption("mupen64plus-parallel-rdp-upscaling", "1x", values = listOf("1x", "2x"), defaultValue = "1x"),
    ))

    @Test fun ppsspp_backend_follows_graphics_api_so_the_page_has_no_row_for_it() {
        val core = EmulatorSettings.page("PSP", ppsspp, ppssppTable, ppsspp.defaultOptions, null, null, forGame = false).core!!
        assertTrue(core.none { it.key == "ppsspp_backend" })
        assertEquals("auto", ppsspp.defaultOptions["ppsspp_backend"])
    }

    @Test fun a_stored_backend_is_ignored() {
        for (stored in listOf("opengl", "vulkan", "none")) {
            val console = cfg("ppsspp_backend = $stored\n")
            val r = CoreConfig.resolve(ppsspp.defaultOptions, console, null, forced = ppsspp.lockedOptions, hidden = ppsspp.hiddenValues)
            assertEquals("auto", r.values["ppsspp_backend"], stored)
        }
    }

    @Test fun mupen_plugins_follow_graphics_api_and_its_parallel_rows_are_offered() {
        val core = EmulatorSettings.page("N64", mupen, mupenTable, mupen.defaultOptions, null, null, forGame = false).core!!
        assertTrue(core.none { it.key == "mupen64plus-rdp-plugin" || it.key == "mupen64plus-rsp-plugin" })
        assertTrue(core.any { it.key == "mupen64plus-parallel-rdp-upscaling" })
    }

    @Test fun a_stored_plugin_is_ignored() {
        val console = cfg("mupen64plus-rdp-plugin = angrylion\nmupen64plus-rsp-plugin = cxd4\n")
        val r = CoreConfig.resolve(mupen.defaultOptions, console, null, forced = mupen.lockedOptions, hidden = mupen.hiddenValues)
        assertEquals("gliden64", r.values["mupen64plus-rdp-plugin"]); assertEquals("hle", r.values["mupen64plus-rsp-plugin"])
    }

    @Test fun defaultSettingsHidesRewindRowsWithoutRewind() {
        val gba = com.nxredux.mobile.runner.CoreRegistry.forTag("GBA")!!
        fun keys(d: CoreDescriptor) = EmulatorSettings.page("GBA", d, null, d.defaultOptions, null, null, forGame = false).console.map { it.option.key }
        val off = keys(gba.copy(rewind = false))
        assertTrue(SpeedOptions.MAX_FF in off && SpeedOptions.FF_AUDIO in off)
        assertTrue(SpeedOptions.REWIND !in off && SpeedOptions.REWIND_AUDIO !in off)
        assertTrue(keys(gba.copy(rewind = true)).containsAll(listOf(SpeedOptions.MAX_FF, SpeedOptions.FF_AUDIO, SpeedOptions.REWIND, SpeedOptions.REWIND_AUDIO)))
    }
}
