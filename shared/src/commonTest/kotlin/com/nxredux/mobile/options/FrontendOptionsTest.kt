package com.nxredux.mobile.options

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontendOptionsTest {
    @Test fun defaults_match_minarch() {
        assertEquals(
            mapOf(
                FrontendOptions.SCALING to "Aspect", FrontendOptions.SHARPNESS to "NEAREST", FrontendOptions.OVERLAY to "None",
                FrontendOptions.OFFSET_X to "0", FrontendOptions.OFFSET_Y to "0", FrontendOptions.PAD_LANDSCAPE_OPACITY to "40",
                FrontendOptions.DEBUG_HUD to "Off", BottomScreenOptions.KEY to "default", PadOptions.LAYOUT to "auto",
                SpeedOptions.MAX_FF to "4", SpeedOptions.FF_AUDIO to "Off", SpeedOptions.REWIND to "Off", SpeedOptions.REWIND_AUDIO to "Off",
            ),
            FrontendOptions.defaults("GBA"),   // GBA: Bottom screen, Controller Layout and the speed rows (as every console)
        )
    }

    @Test fun sharpness_defaults_to_nearest() {
        assertEquals("NEAREST", FrontendOptions.option(FrontendOptions.SHARPNESS)!!.default)
        assertEquals("NEAREST", FrontendOptions.fromConfig(emptyMap())[FrontendOptions.SHARPNESS])
        assertEquals(false, FrontendSettings.parse(emptyMap()).linear)
    }

    @Test fun overlay_defaults_to_none_on_every_console() {
        for (tag in CoreRegistry.tags) {
            assertEquals("None", FrontendOptions.defaults(tag)[FrontendOptions.OVERLAY], tag)
            assertEquals("None", FrontendOptions.fromConfig(emptyMap(), tag)[FrontendOptions.OVERLAY], tag)
        }
        assertNull(FrontendSettings.parse(emptyMap()).overlay)
    }

    @Test fun a_stored_overlay_still_applies() {
        val stored = mapOf(FrontendOptions.OVERLAY to "Aspect - LCD Grid.png")
        assertEquals("Aspect - LCD Grid.png", FrontendOptions.fromConfig(stored, "GBA")[FrontendOptions.OVERLAY])
        assertEquals("Aspect - LCD Grid.png", FrontendSettings.parse(stored).overlay)
        assertEquals("Native.png", FrontendOptions.fromConfig(mapOf(FrontendOptions.OVERLAY to "Native.png"))[FrontendOptions.OVERLAY])
        assertNull(FrontendSettings.parse(mapOf(FrontendOptions.OVERLAY to "None")).overlay)
    }

    @Test fun scaling_defaults_to_aspect_for_every_console() {
        for (tag in CoreRegistry.tags) {
            assertEquals("Aspect", FrontendOptions.defaults(tag)[FrontendOptions.SCALING], tag)
            assertEquals("Aspect", FrontendOptions.fromConfig(emptyMap(), tag)[FrontendOptions.SCALING], tag)
            // Whatever overlay is picked, the scaling default does not follow it.
            assertEquals("Aspect", FrontendOptions.fromConfig(mapOf(FrontendOptions.OVERLAY to "Native.png"), tag)[FrontendOptions.SCALING], tag)
        }
        assertEquals(ScreenScaling.ASPECT, FrontendSettings.parse(emptyMap()).scaling)
    }

    @Test fun a_stored_scaling_still_applies() {
        for (v in listOf("Native", "Fullscreen")) {
            assertEquals(v, FrontendOptions.fromConfig(mapOf(FrontendOptions.SCALING to v), "GBA")[FrontendOptions.SCALING])
        }
        assertEquals(ScreenScaling.NATIVE, FrontendSettings.parse(mapOf(FrontendOptions.SCALING to "Native")).scaling)
        assertEquals(ScreenScaling.FULLSCREEN, FrontendSettings.parse(mapOf(FrontendOptions.SCALING to "Fullscreen")).scaling)
    }

    @Test fun table_order_labels_and_visual_flags() {
        assertEquals(
            listOf("Screen Scaling", "Screen Sharpness", "Overlay", "Offset screen X", "Offset screen Y", "Pad Opacity (landscape)", "Debug HUD"),
            FrontendOptions.table.map { it.label },
        )
        assertEquals(listOf(true, true, true, true, true, false, false), FrontendOptions.table.map { it.visual })
        assertEquals(listOf("nx_screen_scaling", "nx_scale_filter", "nx_overlay",
            "nx_screen_offsetx", "nx_screen_offsety", "nx_pad_landscape_opacity", "nx_debug_hud"), FrontendOptions.table.map { it.key })
    }

    @Test fun offsets_run_from_minus_64_to_64() {
        val offsets = FrontendOptions.choices(FrontendOptions.OFFSET_X, emptyList())
        assertEquals(129, offsets.size)
        assertEquals("-64", offsets.first())
        assertEquals("64", offsets.last())
    }

    @Test fun cycle_wraps_both_ways() {
        assertEquals("Native", FrontendOptions.cycle(FrontendOptions.SCALING, "Fullscreen", 1, emptyList()))
        assertEquals("Fullscreen", FrontendOptions.cycle(FrontendOptions.SCALING, "Native", -1, emptyList()))
        assertEquals("-64", FrontendOptions.cycle(FrontendOptions.OFFSET_Y, "64", 1, emptyList()))
    }

    @Test fun cycle_from_a_raw_value_goes_to_first_or_last() {
        assertEquals("Native", FrontendOptions.cycle(FrontendOptions.SCALING, "Cropped", 1, emptyList()))
        assertEquals("Fullscreen", FrontendOptions.cycle(FrontendOptions.SCALING, "Aspect Screen", -1, emptyList()))
    }

    @Test fun overlay_choices_are_none_then_the_files() {
        val files = listOf("Aspect.png", "Native.png")
        assertEquals(listOf("None", "Aspect.png", "Native.png"), FrontendOptions.choices(FrontendOptions.OVERLAY, files))
        assertEquals("Aspect.png", FrontendOptions.cycle(FrontendOptions.OVERLAY, "None", 1, files))
        assertEquals("None", FrontendOptions.cycle(FrontendOptions.OVERLAY, "Native.png", 1, files))
    }

    @Test fun cycle_of_an_unknown_key_is_null() {
        assertNull(FrontendOptions.cycle("gambatte_gb_palette", "x", 1, emptyList()))
    }

    @Test fun from_config_fills_defaults_keeps_raw_values_and_drops_other_keys() {
        val values = FrontendOptions.fromConfig(mapOf(FrontendOptions.SCALING to "Cropped", "gambatte_gb_palette" to "x"))
        assertEquals(FrontendOptions.defaults() + (FrontendOptions.SCALING to "Cropped"), values)
    }

    @Test fun settings_parse_known_values() {
        val s = FrontendSettings.parse(mapOf(
            FrontendOptions.SCALING to "Native", FrontendOptions.SHARPNESS to "NEAREST",
            FrontendOptions.OVERLAY to "Aspect.png", FrontendOptions.OFFSET_X to "-12", FrontendOptions.OFFSET_Y to "64",
            FrontendOptions.DEBUG_HUD to "On",
        ))
        assertEquals(FrontendSettings(ScreenScaling.NATIVE, linear = false, "Aspect.png", -12, 64, debugHud = true), s)
        assertEquals(ScreenScaling.FULLSCREEN, FrontendSettings.parse(mapOf(FrontendOptions.SCALING to "Fullscreen")).scaling)
    }

    @Test fun settings_apply_unknown_values_as_default() {
        val s = FrontendSettings.parse(mapOf(
            FrontendOptions.SCALING to "Cropped", FrontendOptions.SHARPNESS to "Soft",
            FrontendOptions.OVERLAY to "None", FrontendOptions.OFFSET_X to "100", FrontendOptions.OFFSET_Y to "abc",
            FrontendOptions.DEBUG_HUD to "yes",
        ))
        assertEquals(FrontendSettings(), s)
        assertEquals(FrontendSettings(), FrontendSettings.parse(emptyMap()))
        assertEquals(false, FrontendSettings.parse(mapOf(FrontendOptions.SHARPNESS to "Soft")).linear)
        assertEquals(true, FrontendSettings.parse(mapOf(FrontendOptions.SHARPNESS to "LINEAR")).linear)
    }

    @Test fun landscape_pad_opacity_row_follows_the_spec() {
        val row = FrontendOptions.option(FrontendOptions.PAD_LANDSCAPE_OPACITY)!!
        assertEquals("nx_pad_landscape_opacity", row.key)
        assertEquals("Pad Opacity (landscape)", row.label)
        assertEquals(listOf("100", "60", "40", "25"), row.values)
        assertEquals(listOf("100%", "60%", "40%", "25%"), row.valueLabels)
        assertEquals("40", row.default)
        assertEquals("How opaque the on-screen buttons are in landscape,\nwhere they sit over the game. Portrait keeps its own band.", row.info)
        assertEquals(false, row.visual)
        // Every console gets it, the DS included.
        assertEquals("40", FrontendOptions.defaults("GBA")[FrontendOptions.PAD_LANDSCAPE_OPACITY])
        assertEquals("40", FrontendOptions.defaults("NDS")[FrontendOptions.PAD_LANDSCAPE_OPACITY])
    }

    @Test fun landscape_pad_opacity_maps_to_alpha_and_falls_back_to_40() {
        fun alpha(v: String?) = FrontendSettings.parse(if (v == null) emptyMap() else mapOf(FrontendOptions.PAD_LANDSCAPE_OPACITY to v)).padLandscapeAlpha
        assertEquals(1.0f, alpha("100"))
        assertEquals(0.6f, alpha("60"))
        assertEquals(0.4f, alpha("40"))
        assertEquals(0.25f, alpha("25"))
        assertEquals(0.4f, alpha(null))
        assertEquals(0.4f, alpha("50"))
        assertEquals(0.4f, alpha("abc"))
        assertEquals(0.4f, FrontendSettings().padLandscapeAlpha)
    }

    @Test fun keys_use_the_nx_prefix() {
        assertEquals("nx_screen_scaling", FrontendOptions.SCALING)
        assertEquals("nx_scale_filter", FrontendOptions.SHARPNESS)
        assertEquals("nx_overlay", FrontendOptions.OVERLAY)
        assertEquals("nx_screen_offsetx", FrontendOptions.OFFSET_X)
        assertEquals("nx_screen_offsety", FrontendOptions.OFFSET_Y)
        assertEquals("nx_debug_hud", FrontendOptions.DEBUG_HUD)
    }

    @Test fun both_prefixes_are_frontend_keys_and_only_minarch_is_legacy() {
        assertEquals(true, FrontendOptions.isFrontendKey("nx_shader"))
        assertEquals(true, FrontendOptions.isFrontendKey("minarch_cpu_speed"))
        assertEquals(false, FrontendOptions.isFrontendKey("gambatte_gb_colorization"))
        assertEquals(true, FrontendOptions.isLegacyKey("minarch_overlay"))
        assertEquals(false, FrontendOptions.isLegacyKey("nx_overlay"))
    }

    @Test fun migrate_keys_renames_minarch_unless_nx_exists() {
        val migrated = FrontendOptions.migrateKeys(
            mapOf("minarch_overlay" to "Aspect.png", "minarch_scale_filter" to "LINEAR", "nx_scale_filter" to "NEAREST", "core_x" to "1"),
        )
        assertEquals(mapOf("nx_overlay" to "Aspect.png", "nx_scale_filter" to "NEAREST", "core_x" to "1"), migrated)
        assertEquals("nx_screen_effect", FrontendOptions.migrateKey("minarch_screen_effect"))
        assertEquals("gambatte_x", FrontendOptions.migrateKey("gambatte_x"))
    }

    @Test fun core_values_drop_both_prefixes() {
        assertEquals(mapOf("core_x" to "1"), FrontendOptions.coreValues(mapOf("core_x" to "1", "nx_shader" to "None", "minarch_overlay" to "x")))
    }

    @Test fun the_table_has_no_effect_row() {
        assertNull(FrontendOptions.option(FrontendOptions.EFFECT))
        assertEquals(listOf(FrontendOptions.SCALING, FrontendOptions.SHARPNESS, FrontendOptions.OVERLAY, FrontendOptions.OFFSET_X, FrontendOptions.OFFSET_Y, FrontendOptions.PAD_LANDSCAPE_OPACITY, FrontendOptions.DEBUG_HUD), FrontendOptions.table.map { it.key })
    }

    @Test fun nx_cheats_is_neither_a_frontend_row_nor_a_core_value() {
        val values = mapOf(FrontendOptions.CHEATS to "a|b", "gambatte_gb_colorization" to "auto")
        assertEquals(null, FrontendOptions.fromConfig(values)[FrontendOptions.CHEATS])
        assertEquals(mapOf("gambatte_gb_colorization" to "auto"), FrontendOptions.coreValues(values))
        assertEquals(true, FrontendOptions.isFrontendKey(FrontendOptions.CHEATS))
    }

    @Test fun the_frontend_page_has_no_ds_rows_but_the_helpers_still_cover_them() {
        assertTrue(FrontendOptions.table.none { it.key.startsWith("nx_nds_") })
        val d = FrontendOptions.defaults("NDS")
        assertEquals("auto", d[NdsOptions.LAYOUT_PORTRAIT])
        val v = FrontendOptions.fromConfig(mapOf(NdsOptions.BIG_SCREEN to "bottom"), "NDS")
        assertEquals("bottom", v[NdsOptions.BIG_SCREEN])
        assertEquals("Big Screen", FrontendOptions.option(NdsOptions.BIG_SCREEN)?.label)
        assertTrue(NdsOptions.BIG_SCREEN !in FrontendOptions.coreValues(v))
        assertTrue(FrontendOptions.defaults("GBA").keys.none { it.startsWith("nx_nds_") })
    }
}
