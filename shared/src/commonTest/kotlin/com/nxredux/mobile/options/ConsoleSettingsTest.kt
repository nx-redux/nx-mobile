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
}
