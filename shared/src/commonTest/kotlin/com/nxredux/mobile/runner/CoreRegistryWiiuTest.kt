package com.nxredux.mobile.runner

import com.nxredux.mobile.library.Resolution
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
        )
        for ((key, value) in expected) {
            assertEquals(value, wiiu.defaultOptions[key], key)
            assertTrue(key in wiiu.lockedOptions, key)
        }
    }
}
