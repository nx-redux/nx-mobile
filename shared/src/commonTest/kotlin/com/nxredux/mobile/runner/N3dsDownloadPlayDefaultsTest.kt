package com.nxredux.mobile.runner

import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.options.ConfigLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class N3dsDownloadPlayDefaultsTest {
    @Test fun mali_with_a_driver_gets_vulkan_and_it() {
        assertEquals(mapOf("nx_graphics_api" to "vulkan", "nx_gpu_driver" to "abc"), n3dsDownloadPlayDefaults("Mali-G76", listOf("abc")))
    }

    @Test fun mali_takes_the_driver_the_3ds_console_chose_when_it_is_installed() {
        assertEquals("def", n3dsDownloadPlayDefaults("Mali-G76", listOf("abc", "def"), chosen = "def")["nx_gpu_driver"])
        assertEquals("abc", n3dsDownloadPlayDefaults("Mali-G76", listOf("abc", "def"), chosen = "gone")["nx_gpu_driver"])
    }

    // OpenGL ES at 2x ran a Mali guest at ~52 fps, which pulls a race out of sync.
    @Test fun mali_without_driver_starts_at_1x() {
        assertEquals(mapOf("nx_3ds_3d_resolution" to "performance", "nx_3ds_3d_scale" to "1"), n3dsDownloadPlayDefaults("Mali-G76", emptyList()))
    }

    @Test fun other_gpus_keep_the_console_settings() {
        assertEquals(emptyMap(), n3dsDownloadPlayDefaults("Adreno (TM) 740", listOf("abc")))
        assertEquals(emptyMap(), n3dsDownloadPlayDefaults(null, emptyList()))
    }

    @Test fun local_wireless_off_refuses_with_the_setting_name() {
        assertEquals("3DS Download Play needs Local Wireless on (Game Settings → Multiplayer)", n3dsDownloadPlayRefusal(false))
        assertNull(n3dsDownloadPlayRefusal(true))
    }

    private fun cfg(vararg kv: Pair<String, String>) = ConfigFile(kv.map { (k, v) -> ConfigLine.Entry(k, v, false) })

    @Test
    fun the_row_defaults_sit_under_the_console_file_when_the_row_has_no_file_of_its_own() {
        val (console, game) = n3dsDownloadPlayConfigs(mapOf("res" to "1x"), cfg("res" to "auto", "other" to "a"), null)
        assertEquals(mapOf("res" to "1x", "other" to "a"), console?.values)
        assertNull(game)
    }

    @Test
    fun a_row_file_keeps_the_defaults_over_values_it_only_copied_from_the_console() {
        val (_, game) = n3dsDownloadPlayConfigs(
            mapOf("res" to "1x", "api" to "vulkan"),
            cfg("res" to "auto", "wireless" to "off"),
            cfg("res" to "auto", "wireless" to "on"),
        )
        assertEquals(mapOf("res" to "1x", "api" to "vulkan", "wireless" to "on"), game?.values)
    }

    @Test
    fun a_value_the_player_changed_for_the_row_beats_its_default() {
        val (_, game) = n3dsDownloadPlayConfigs(mapOf("res" to "1x"), cfg("res" to "auto"), cfg("res" to "3x"))
        assertEquals("3x", game?.values?.get("res"))
    }
}
