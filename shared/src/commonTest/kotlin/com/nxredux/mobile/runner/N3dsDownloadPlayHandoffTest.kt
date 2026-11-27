package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class N3dsDownloadPlayHandoffTest {
    @Test fun a_child_points_to_its_full_game() {
        assertEquals(0x0004000000030800L, N3dsDownloadPlayHandoff.parentOf("0004000100030800"))
        assertEquals(0x0004000000030800L, N3dsDownloadPlayHandoff.parentOf(" 0004000100030800\n"))
    }

    @Test fun anything_but_a_child_title_points_nowhere() {
        assertNull(N3dsDownloadPlayHandoff.parentOf("0004000000030800"))
        assertNull(N3dsDownloadPlayHandoff.parentOf("not hex"))
        assertNull(N3dsDownloadPlayHandoff.parentOf(""))
    }

    // The row decides the renderer, the 3D resolution, the layout and the controls: only the game's own core options travel.
    @Test fun only_gameplay_core_options_travel() {
        val game = mapOf(
            "citra_cpu_scale" to "125", "citra_use_hw_shader" to "enabled",
            "citra_graphics_api" to "vulkan", "citra_resolution_factor" to "3", "citra_is_new_3ds" to "Old 3DS",
            "citra_layout_option" to "x", "citra_swap_screen" to "y", "citra_analog_function" to "z",
            "nx_graphics_api" to "gles", "nx_gpu_driver" to "abc",
        )
        assertEquals(mapOf("citra_cpu_scale" to "125", "citra_use_hw_shader" to "enabled"), N3dsDownloadPlayHandoff.liveOptions(game))
    }

    @Test fun the_title_index_keeps_the_latest_name_per_title() {
        var text = ""
        text = N3dsDownloadPlayHandoff.withTitle(text, 0x0004000000030800L, "Mario Kart 7 (USA).cci")
        text = N3dsDownloadPlayHandoff.withTitle(text, 0x00040000001B5000L, "Pokemon.cci")
        text = N3dsDownloadPlayHandoff.withTitle(text, 0x0004000000030800L, "MK7.3ds")
        assertEquals(mapOf(0x0004000000030800L to "MK7.3ds", 0x00040000001B5000L to "Pokemon.cci"), N3dsDownloadPlayHandoff.titles(text))
    }

    @Test fun a_broken_index_line_is_skipped() {
        assertEquals(mapOf(0x0004000000030800L to "MK7.3ds"), N3dsDownloadPlayHandoff.titles("garbage\n0004000000030800\tMK7.3ds\n\tx\n"))
    }
}
