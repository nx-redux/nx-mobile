package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleSettingsTest {
    @Test fun nds_offers_its_screen_rows_without_the_ds_prefix() {
        val rows = ConsoleSettings.tableFor("NDS").filter { it !in PadOptions.all && it != BottomScreenOptions.row && it !in SpeedOptions.table }
        assertEquals(
            listOf(
                NdsOptions.LAYOUT_PORTRAIT, NdsOptions.LAYOUT_PORTRAIT_CONTROLLER, NdsOptions.LAYOUT_LANDSCAPE,
                NdsOptions.MODE_3D, NdsOptions.MAX_SCALE_3D, NdsOptions.SCALE_3D,
                NdsOptions.BIG_SCREEN, NdsOptions.PIP_CORNER, NdsOptions.PIP_SIZE, NdsOptions.PIP_OPACITY,
            ),
            rows.map { it.key },
        )
        assertEquals(
            listOf(
                "Layout (portrait)", "Layout (portrait, controller)", "Layout (landscape)",
                "3D Resolution", "Max Scale", "Scale", "Big Screen", "Inset Corner", "Inset Size", "Inset Opacity",
            ),
            rows.map { it.label },
        )
        assertTrue(rows.none { it.label.startsWith("DS") })
        assertEquals(NdsOptions.table, rows)                  // one definition
    }

    @Test fun dc_offers_graphics_api_and_the_shared_controller_layout_row() {
        assertEquals(listOf(GraphicsApiOptions.row, BottomScreenOptions.row, PadOptions.layoutRow) + SpeedOptions.table, ConsoleSettings.tableFor("DC"))
        assertEquals(listOf("Graphics API", "Bottom screen", "Controller Layout") + SpeedOptions.table.map { it.label }, ConsoleSettings.tableFor(" dc ").map { it.label })
    }

    @Test fun other_consoles_have_the_layout_row_only_and_n64_adds_graphics_api() {
        assertEquals(listOf(BottomScreenOptions.row, PadOptions.layoutRow) + SpeedOptions.table, ConsoleSettings.tableFor("GBA"))
        assertEquals(listOf(BottomScreenOptions.row, PadOptions.layoutRow) + SpeedOptions.table, ConsoleSettings.tableFor(""))
        assertEquals(listOf(GraphicsApiOptions.row, BottomScreenOptions.row, PadOptions.layoutRow) + SpeedOptions.table, ConsoleSettings.tableFor("N64"))
        assertEquals(listOf(BottomScreenOptions.row, PadOptions.segaPadRow, PadOptions.layoutRow) + SpeedOptions.table, ConsoleSettings.tableFor("MD"))
    }

    @Test fun everyConsoleHasTheRows() {
        val keys = listOf(SpeedOptions.MAX_FF, SpeedOptions.FF_AUDIO, SpeedOptions.REWIND, SpeedOptions.REWIND_AUDIO)
        for (tag in listOf("GBA", "NDS", "PS")) assertTrue(ConsoleSettings.tableFor(tag).map { it.key }.containsAll(keys), tag)
    }

    @Test fun threeDs_puts_the_3d_rows_after_the_layout_rows_and_before_new_controls() {
        val keys = ConsoleSettings.tableFor("3DS").map { it.key }
        val layout = N3dsOptions.layoutRows.map { it.key }
        val res = N3dsOptions.resolutionRows(null).map { it.key }
        assertEquals(layout + res + N3dsOptions.NEW_CONTROLS, keys.take(layout.size + res.size + 1))
    }

    @Test fun threeDs_fitted_keeps_a_stored_scale_the_display_no_longer_reaches() {
        val max = ConsoleSettings.tableFor("3DS", displayMax = 6).single { it.key == "nx_3ds_3d_max_scale" }
        val (option, shown) = ConsoleSettings.fitted(max, "8")
        assertEquals("8", shown)
        assertEquals("8x", option.labelOf("8"))
        val small = ConsoleSettings.tableFor("3DS", displayMax = 2).single { it.key == "nx_3ds_3d_max_scale" }
        val (listed, unstored) = ConsoleSettings.fitted(small, small.default)
        assertEquals(small.values, listed.values)
        assertEquals("2", unstored)
        val mode = ConsoleSettings.tableFor("3DS").single { it.key == "nx_3ds_3d_resolution" }
        assertEquals("auto", ConsoleSettings.fitted(mode, "bogus").second)
    }
}
