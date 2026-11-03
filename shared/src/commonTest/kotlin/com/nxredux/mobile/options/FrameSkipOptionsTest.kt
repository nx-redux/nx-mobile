package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameSkipOptionsTest {
    @Test fun dolphin_consoles_show_every_frame_by_default_the_rest_skip() {
        assertEquals(FrameSkipOptions.OFF, FrameSkipOptions.defaultFor("WII"))
        assertEquals(FrameSkipOptions.OFF, FrameSkipOptions.defaultFor(" gc "))
        listOf("GBA", "PS", "N64", "PSP", "3DS", "NDS").forEach { assertEquals(FrameSkipOptions.AUTO, FrameSkipOptions.defaultFor(it), it) }
    }

    @Test fun wii_and_gamecube_offer_the_row_other_consoles_do_not() {
        assertEquals(FrameSkipOptions.OFF, ConsoleSettings.tableFor("WII").single { it.key == FrameSkipOptions.KEY }.default)
        assertEquals(FrameSkipOptions.OFF, ConsoleSettings.tableFor("GC").single { it.key == FrameSkipOptions.KEY }.default)
        listOf("GBA", "PSP", "N64").forEach { tag -> assertTrue(ConsoleSettings.tableFor(tag).none { it.key == FrameSkipOptions.KEY }, tag) }
        assertEquals(1, ConsoleSettings.all.count { it.key == FrameSkipOptions.KEY })
    }

    @Test fun saved_values_win_and_unknown_ones_fall_back_to_the_console_default() {
        assertTrue(FrameSkipOptions.skipOf(mapOf(FrameSkipOptions.KEY to "Auto"), "WII"))
        assertFalse(FrameSkipOptions.skipOf(mapOf(FrameSkipOptions.KEY to "Off"), "GBA"))
        assertFalse(FrameSkipOptions.skipOf(emptyMap(), "WII"))
        assertTrue(FrameSkipOptions.skipOf(mapOf(FrameSkipOptions.KEY to "sometimes"), "GBA"))
    }
}
