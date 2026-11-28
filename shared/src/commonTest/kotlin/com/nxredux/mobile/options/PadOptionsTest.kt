package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PadOptionsTest {
    @Test fun only_the_sega_pad_tags_have_a_pad_row() {
        assertEquals(emptyList(), PadOptions.tableFor("SFC"))
        for (tag in listOf("DC", "SFC", "NDS", "PS", "GBA", "N64", "3DS", "UNKNOWN")) assertEquals(emptyList(), PadOptions.tableFor(tag), tag)
        assertEquals(listOf(PadOptions.segaPadRow), PadOptions.tableFor("MD"))
        assertEquals(listOf(PadOptions.segaPadRow), PadOptions.all)
    }

    @Test fun no_console_lists_a_controller_layout_row() {
        for (tag in listOf("DC", "MD", "SFC", "NDS", "PS", "GBA", "N64", "3DS", "WII", "GC", "UNKNOWN"))
            assertFalse(ConsoleSettings.tableFor(tag).any { it.key == "nx_pad_layout" || it.label == "Controller Layout" }, tag)
        assertFalse(ConsoleSettings.all.any { it.key == "nx_pad_layout" })
    }

    @Test fun sega_pad_row_only_for_sega_pad_tags() {
        for (tag in listOf("MD", "GPGX", "SEGACD", "32X")) assertTrue(PadOptions.segaPadRow in ConsoleSettings.tableFor(tag), tag)
        for (tag in listOf("SMS", "GG", "DC", "SFC")) assertFalse(PadOptions.segaPadRow in ConsoleSettings.tableFor(tag), tag)
        assertEquals(listOf("auto", "3", "6"), PadOptions.segaPadRow.values)
        assertEquals("auto", PadOptions.segaPadRow.default)
    }

    @Test fun nds_keeps_its_rows() {
        assertEquals(NdsOptions.table + NdsOptions.shaderScreensRow + NdsOptions.localWirelessRow + BottomScreenOptions.row + (SpeedOptions.table + AudioOptions.rows), ConsoleSettings.tableFor("NDS"))
    }

    @Test fun keys_are_unique_and_findable() {
        val keys = ConsoleSettings.all.map { it.key }
        assertEquals(keys.toSet().size, keys.size)
        assertEquals(PadOptions.segaPadRow, FrontendOptions.option(PadOptions.SEGA_PAD))
    }

    @Test fun the_sega_pad_row_reads_as_before() {
        assertEquals(listOf("Auto", "3 buttons", "6 buttons"), PadOptions.segaPadRow.valueLabels)
        assertFalse(PadOptions.segaPadRow.visual)
    }

    @Test fun defaults_have_the_sega_pad_row_on_sega_tags_only() {
        assertEquals("auto", FrontendOptions.defaults("MD")[PadOptions.SEGA_PAD])
        assertFalse(PadOptions.SEGA_PAD in FrontendOptions.defaults("GBA"))
        for (tag in listOf("DC", "GBA", "N64")) assertFalse("nx_pad_layout" in FrontendOptions.defaults(tag), tag)
    }
}
