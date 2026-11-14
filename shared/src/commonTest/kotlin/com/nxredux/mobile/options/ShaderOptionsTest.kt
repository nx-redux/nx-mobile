package com.nxredux.mobile.options

import com.nxredux.mobile.shaders.ShaderParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShaderOptionsTest {
    private val scan = ShaderParam("SCAN", "Scanline weight", 0.5f, 0f, 1f, 0.3f)
    private val mask = ShaderParam("MASK", "Mask", 1f, 0f, 2f, 1f)

    @Test fun format2_is_fixed_two_decimals() {
        assertEquals("0.50", ShaderOptions.format2(0.5f))
        assertEquals("-0.05", ShaderOptions.format2(-0.049f))
        assertEquals("320.00", ShaderOptions.format2(320f))
        assertEquals("0.00", ShaderOptions.format2(0.001f))
    }

    @Test fun param_keys_round_trip() {
        assertEquals("nx_shader_param_SCAN", ShaderOptions.paramKey("SCAN"))
        assertEquals("SCAN", ShaderOptions.paramId("nx_shader_param_SCAN"))
        assertNull(ShaderOptions.paramId("nx_shader"))
        assertEquals(true, ShaderOptions.isShaderKey("nx_shader"))
        assertEquals(true, ShaderOptions.isShaderKey("nx_shader_param_X"))
        assertEquals(false, ShaderOptions.isShaderKey("nx_overlay"))
    }

    @Test fun param_choices_end_at_max() {
        assertEquals(listOf("0.00", "0.30", "0.60", "0.90", "1.00"), ShaderOptions.paramChoices(scan))
        assertEquals(listOf("0.00", "1.00", "2.00"), ShaderOptions.paramChoices(mask))
        val huge = ShaderParam("H", "H", 0f, 0f, 10000f, 0.01f)
        assertEquals(1024, ShaderOptions.paramChoices(huge).size)
        val zeroStep = ShaderParam("Z", "Z", 0f, 0f, 1f, 0f)
        assertEquals(listOf("0.00", "1.00"), ShaderOptions.paramChoices(zeroStep))
    }

    @Test fun preset_cycles_through_none_and_the_list_and_params_cycle_their_choices() {
        val presets = listOf("crt/crt-pie.glslp", "nx/line.glslp")
        assertEquals("crt/crt-pie.glslp", ShaderOptions.cycle(ShaderOptions.PRESET, "None", 1, presets, emptyList()))
        assertEquals("nx/line.glslp", ShaderOptions.cycle(ShaderOptions.PRESET, "None", -1, presets, emptyList()))
        assertEquals("None", ShaderOptions.cycle(ShaderOptions.PRESET, "gone.glslp", 1, presets, emptyList()))   // raw → first
        assertEquals("0.60", ShaderOptions.cycle(ShaderOptions.paramKey("SCAN"), "0.30", 1, presets, listOf(scan)))
        assertEquals("0.00", ShaderOptions.cycle(ShaderOptions.paramKey("SCAN"), "1.00", 1, presets, listOf(scan)))
        assertNull(ShaderOptions.cycle(ShaderOptions.paramKey("NOPE"), "0", 1, presets, listOf(scan)))
        assertNull(ShaderOptions.cycle("nx_overlay", "x", 1, presets, emptyList()))
    }

    // crt-geom's cornersmooth: default 1000 on the grid 80, 180, …, 1980, 2000.
    private val corner = ShaderParam("cornersmooth", "CRTGeom Corner Smoothness", 1000f, 80f, 2000f, 100f)
    private val cornerKey = ShaderOptions.paramKey("cornersmooth")

    @Test fun an_off_grid_number_cycles_to_the_nearest_step_in_the_pressed_direction() {
        assertEquals("1080.00", ShaderOptions.cycle(cornerKey, "1000.00", 1, emptyList(), listOf(corner)))
        assertEquals("980.00", ShaderOptions.cycle(cornerKey, "1000.00", -1, emptyList(), listOf(corner)))
        assertEquals("0.60", ShaderOptions.cycle(ShaderOptions.paramKey("SCAN"), "0.3", 1, emptyList(), listOf(scan)))   // same number, other spelling
    }

    @Test fun an_off_grid_number_past_the_ends_wraps() {
        assertEquals("80.00", ShaderOptions.cycle(cornerKey, "2500", 1, emptyList(), listOf(corner)))
        assertEquals("2000.00", ShaderOptions.cycle(cornerKey, "10", -1, emptyList(), listOf(corner)))
        assertEquals("2000.00", ShaderOptions.cycle(cornerKey, "1990", 1, emptyList(), listOf(corner)))   // 1980 < 1990 < 2000
    }

    @Test fun a_non_numeric_value_still_goes_to_first_or_last() {
        assertEquals("80.00", ShaderOptions.cycle(cornerKey, "soft", 1, emptyList(), listOf(corner)))
        assertEquals("2000.00", ShaderOptions.cycle(cornerKey, "soft", -1, emptyList(), listOf(corner)))
    }

    @Test fun from_config_keeps_preset_and_params_and_defaults_to_none() {
        assertEquals(mapOf(ShaderOptions.PRESET to "None"), ShaderOptions.fromConfig(emptyMap()))
        val v = ShaderOptions.fromConfig(mapOf("nx_shader" to "crt/crt-pie.glslp", "nx_shader_param_SCAN" to "0.30", "nx_overlay" to "x"))
        assertEquals(mapOf("nx_shader" to "crt/crt-pie.glslp", "nx_shader_param_SCAN" to "0.30"), v)
        assertEquals("crt/crt-pie.glslp", ShaderOptions.preset(v))
        assertNull(ShaderOptions.preset(mapOf("nx_shader" to "None")))
    }

    @Test fun effect_line_migrates_to_the_line_preset() {
        assertEquals("nx/line.glslp", ShaderOptions.fromConfig(mapOf("nx_screen_effect" to "Line"))[ShaderOptions.PRESET])
        assertEquals("nx/grid.glslp", ShaderOptions.fromConfig(mapOf("nx_screen_effect" to "Grid"))[ShaderOptions.PRESET])
        assertEquals("None", ShaderOptions.fromConfig(mapOf("nx_screen_effect" to "None"))[ShaderOptions.PRESET])
        assertEquals("None", ShaderOptions.fromConfig(mapOf("nx_screen_effect" to "Line", "nx_shader" to "None"))[ShaderOptions.PRESET])
        assertEquals(setOf("nx_screen_effect"), ShaderOptions.MIGRATED_KEYS)
    }

    @Test fun stored_param_outside_range_is_clamped_and_garbage_takes_the_default() {
        val values = mapOf("nx_shader_param_SCAN" to "7", "nx_shader_param_MASK" to "abc")
        assertEquals(mapOf("SCAN" to 1f, "MASK" to 1f), ShaderOptions.paramValues(values, listOf(scan, mask)))
        assertEquals(mapOf("SCAN" to 0.5f), ShaderOptions.paramValues(emptyMap(), listOf(scan)))
    }

    @Test fun active_entries_write_only_the_active_params_and_reset_writes_defaults() {
        val values = mapOf("nx_shader" to "p.glslp", "nx_shader_param_SCAN" to "0.30", "nx_shader_param_OLD" to "1.00")
        assertEquals(listOf("nx_shader" to "p.glslp", "nx_shader_param_SCAN" to "0.30", "nx_shader_param_MASK" to "1.00"), ShaderOptions.activeParamEntries(values, listOf(scan, mask)))
        assertEquals(listOf("nx_shader_param_SCAN" to "0.50", "nx_shader_param_MASK" to "1.00"), ShaderOptions.resetEntries(listOf(scan, mask)))
    }

    @Test fun a_hand_built_inverted_range_does_not_throw() {
        val bad = ShaderParam("B", "B", 0.5f, 1f, 0f, 0.1f)
        assertEquals(listOf("1.00"), ShaderOptions.paramChoices(bad))
        assertEquals(mapOf("B" to 0.5f), ShaderOptions.paramValues(mapOf("nx_shader_param_B" to "0.5"), listOf(bad)))
        assertEquals(mapOf("B" to 0.5f), ShaderOptions.paramValues(mapOf("nx_shader_param_B" to "NaN"), listOf(bad)))
    }

    @Test fun a_replaced_bundled_preset_reads_as_its_slang_version() {
        assertEquals("slang/crt/crt-geom.slangp", ShaderOptions.fromConfig(mapOf("nx_shader" to "crt/crt-geom.glslp"))["nx_shader"])
        assertEquals("slang/handheld/lcd3x.slangp", ShaderOptions.preset(mapOf("nx_shader" to "glsl/lcd3x.glsl")))
        assertEquals("User/crt/crt-geom.glslp", ShaderOptions.preset(mapOf("nx_shader" to "User/crt/crt-geom.glslp")))
        assertEquals("nx/line.glslp", ShaderOptions.current("nx/line.glslp"))
        assertEquals(null, ShaderOptions.preset(mapOf("nx_shader" to "None")))
    }
}
