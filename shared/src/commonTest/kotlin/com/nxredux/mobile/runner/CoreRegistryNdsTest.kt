package com.nxredux.mobile.runner

import com.nxredux.mobile.options.ConfigSource
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.storage.NdsResumeCleanup
import com.nxredux.mobile.storage.NdsDsvSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreRegistryNdsTest {
    private val nds get() = CoreRegistry.forTag("NDS")!!

    @Test fun nds_runs_melonds_ds() {
        assertEquals("melondsds", nds.id)
        assertEquals("libmelondsds_libretro_android.so", CoreRegistry.forTag("nds")!!.libraryFileName)
        assertEquals(setOf("nds", "ids", "dsi"), nds.extensions)
        assertEquals(LoadMode.FILE_DESCRIPTOR, nds.loadMode)
        assertTrue(nds.cheats)
        assertNull(CoreRegistry.forId("melonds"))
    }

    @Test fun the_dsv_save_and_resume_cleanup_name_the_registered_core() {
        // A renamed core id would otherwise silently turn both off.
        assertEquals(nds.id, NdsDsvSave.CORE_ID)
        assertEquals(nds.id, NdsResumeCleanup.CORE_ID)
    }

    @Test fun the_core_draws_a_plain_stack_and_takes_touch_as_a_pointer() {
        assertEquals(
            mapOf(
                "melonds_touch_mode" to "touch", "melonds_number_of_screen_layouts" to "1",
                "melonds_screen_layout1" to "top-bottom", "melonds_screen_gap" to "0", "melonds_show_cursor" to "disabled",
                "melonds_opengl_resolution" to "4",
                "melonds_secondary_screen_scale" to "100", "melonds_secondary_screen_filtering" to "nearest",
                "melonds_screen_layout2" to "left-right", "melonds_screen_layout3" to "largescreen-top",
                "melonds_screen_layout4" to "largescreen-bottom", "melonds_screen_layout5" to "hybrid-top",
                "melonds_screen_layout6" to "hybrid-bottom", "melonds_screen_layout7" to "top",
                "melonds_screen_layout8" to "bottom", "melonds_hybrid_ratio" to "2",
                "melonds_hybrid_small_screen" to "both", "melonds_opengl_filtering" to "nearest",
                "melonds_cursor_timeout" to "3", "melonds_show_current_layout" to "enabled",
                "melonds_mic_input" to "microphone",
                "melonds_mic_input_active" to "always",
                "melonds_show_mic_state" to "disabled",
            ),
            nds.defaultOptions,
        )
    }

    @Test fun layout_touch_and_cursor_keys_are_locked_to_their_defaults() {
        assertEquals(nds.defaultOptions.keys, nds.lockedOptions)
        // Dolphin also locks keys it leaves at the core's own default (debug, Bluetooth passthrough, logging): no default to pin.
        assertTrue(CoreRegistry.all.filter { it.id != "dolphin" }.all { it.defaultOptions.keys.containsAll(it.lockedOptions) })
    }

    @Test fun a_cfg_cannot_override_a_locked_key() {
        val game = CoreConfig.parse("melonds_screen_layout1 = left-right\nmelonds_jit_enable = disabled\n")
        val r = CoreConfig.resolve(nds.defaultOptions, null, game, forced = nds.lockedOptions)
        assertEquals("top-bottom", r.values["melonds_screen_layout1"])
        assertEquals("disabled", r.values["melonds_jit_enable"])
        assertEquals(ConfigSource.GAME, r.source)
        assertTrue(r.locked.containsAll(nds.lockedOptions))
    }

    @Test fun start_only_options_are_hidden_but_live_ones_are_not() {
        val hidden = nds.restartOnlyOptions
        assertTrue("melonds_jit_block_size" in hidden)
        assertTrue("melonds_slot2_device" in hidden)
        assertTrue("melonds_homebrew_sdcard" in hidden)
        assertFalse("melonds_audio_interpolation" in hidden)   // UpdateConsole re-applies it live
        assertFalse("melonds_console_mode" in hidden)          // its info already says "restart": the filter hides it
    }

    @Test fun ds_bios_is_optional_and_read_from_bios_nds() {
        assertEquals(emptyList(), CoreRegistry.requiredBios("NDS"))
        assertEquals(listOf("NDS"), BiosFolders.forTag("NDS"))
    }

    @Test fun lockedScreenOptions() {
        // The one-layout top-bottom frame never reads these: the internal resolution is set by the app, and the rest are unused.
        val d = CoreRegistry.forId("melondsds")!!
        assertTrue(
            d.lockedOptions.containsAll(
                setOf(
                    "melonds_opengl_resolution", "melonds_secondary_screen_scale", "melonds_secondary_screen_filtering",
                    "melonds_hybrid_ratio", "melonds_hybrid_small_screen", "melonds_opengl_filtering",
                    "melonds_cursor_timeout", "melonds_show_current_layout",
                ) + (2..8).map { "melonds_screen_layout$it" },
            ),
        )
        assertEquals("100", d.defaultOptions["melonds_secondary_screen_scale"])
        assertEquals("4", d.defaultOptions["melonds_opengl_resolution"])
    }

    @Test fun the_internal_resolution_is_locked_and_the_renderer_stays_a_visible_option() {
        val d = CoreRegistry.forId("melondsds")!!
        assertEquals("4", d.defaultOptions["melonds_opengl_resolution"])
        assertTrue("melonds_opengl_resolution" in d.lockedOptions)
        assertTrue("melonds_render_mode" !in d.defaultOptions)      // device default, set at load (NdsRenderer)
        assertFalse("melonds_render_mode" in d.restartOnlyOptions)  // visible, per-game; held until restart (NdsRenderer)
    }
}
