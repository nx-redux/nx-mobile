package com.nxredux.mobile.runner

import com.nxredux.mobile.library.Resolution
import com.nxredux.mobile.options.CoreOption
import com.nxredux.mobile.options.CoreOptionTable
import com.nxredux.mobile.options.EmulatorSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Wii U on the Cemu core. */
class CoreRegistryWiiuTest {
    private val wiiu get() = CoreRegistry.forTag("WIIU")!!

    @Test fun wiiu_runs_cemu() {
        assertEquals("cemu", wiiu.id)
        assertEquals("libcemu_libretro_android.so", wiiu.libraryFileName)
        assertEquals(1, wiiu.players)
        assertFalse(wiiu.rewind)
        assertFalse(wiiu.cheats)
    }

    @Test fun wua_resolves_to_wiiu() {
        assertEquals("WIIU", Resolution.byExtension("Mario Kart 8.wua", null) { it.defaultTag })
    }

    @Test fun wiiu_folders_resolve_to_the_console() {
        assertEquals("wiiu", CoreRegistry.consoleOfFolder("Wii U", "WIIU")?.id)
        assertEquals("wiiu", CoreRegistry.consoleOfFolder("Nintendo Wii U", "")?.id)
    }

    @Test fun wuhb_is_owned_by_the_console_but_not_claimed_by_the_core() {
        assertTrue("wuhb" in CoreRegistry.consoleOf("WIIU")!!.extensions)
        assertFalse("wuhb" in wiiu.extensions)
        assertFalse("wuhb" in CoreRegistry.coreExtensions)
    }

    @Test fun options_are_locked_to_the_phone_setup() {
        val expected = mapOf(
            "cemu_gpu_api" to "Vulkan", "cemu_screen_layout1" to "Stacked", "cemu_number_of_screen_layouts" to "1",
            "cemu_log_to_file" to "disabled", "cemu_show_game_fps" to "disabled",
            "cemu_log_filesystem" to "disabled", "cemu_log_thread_sync" to "disabled", "cemu_log_system_api" to "disabled",
            "cemu_log_texture_memory" to "disabled", "cemu_log_input_api" to "disabled", "cemu_log_audio" to "disabled",
            "cemu_log_thread_time" to "disabled", "cemu_dump_shaders" to "disabled",
            // The app's (Motion Controls, WiiuOptions.motionOption); off until the launch sends the resolved row.
            "cemu_gamepad_motion" to "disabled",
        )
        for ((key, value) in expected) {
            assertEquals(value, wiiu.defaultOptions[key], key)
            assertTrue(key in wiiu.lockedOptions, key)
        }
    }

    @Test fun keys_come_from_the_cemu_subfolder_and_are_never_required() {
        assertEquals("Cemu", wiiu.biosSubdir)
        assertEquals(listOf("keys.txt"), wiiu.biosFiles)
        assertTrue(CoreRegistry.requiredBios("WIIU").isEmpty())
    }

    @Test fun hidden_keys_never_appear_in_the_core_options_page() {
        val hidden = listOf(
            "cemu_install_game", "cemu_uninstall_game", "cemu_install_titles", "cemu_uninstall_titles", "cemu_install_remove_source",
            "cemu_convert_to_wua", "cemu_wua_output_dir",
            "cemu_emulate_skylander_portal", "cemu_emulate_infinity_base", "cemu_emulate_dimensions_toypad",
            "cemu_next_screen_layout_button", "cemu_drc_position",
            "cemu_screen_layout2", "cemu_screen_layout3", "cemu_screen_layout4", "cemu_screen_layout5",
        )
        val shown = CoreOption("cemu_cpu_mode", "Auto", values = listOf("Auto", "Recompiler"), defaultValue = "Auto")
        val table = CoreOptionTable("cemu", "Cemu", listOf(shown) + hidden.map { CoreOption(it, "disabled", values = listOf("disabled", "enabled"), defaultValue = "disabled") })
        val core = EmulatorSettings.page("WIIU", wiiu, table, wiiu.defaultOptions, null, null, forGame = false).core!!
        assertEquals(listOf("cemu_cpu_mode"), core.map { it.key })
        for (key in hidden) assertTrue(key in wiiu.lockedOptions, key)
    }
}
