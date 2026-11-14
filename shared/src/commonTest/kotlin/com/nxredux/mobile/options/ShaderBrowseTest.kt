package com.nxredux.mobile.options

import com.nxredux.mobile.options.ShaderBrowse.Folder
import com.nxredux.mobile.options.ShaderBrowse.Preset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShaderBrowseTest {
    private val presets = listOf(
        "crt/crt-geom.glslp", "crt/zfast-crt.glslp", "glsl/lcd3x.glsl", "handheld/dot.glslp", "interpolation/sharp-bilinear.glslp",
        "nx/line.glslp", "slang/crt/crt-geom.slangp", "slang/crt/crt-guest-advanced-hd.slangp", "slang/edge-smoothing/scalefx/scalefx.slangp",
        "slang/ntsc/ntsc-adaptive.slangp", "xbrz/xbrz-freescale.glslp",
        "User/mine.glslp", "User/shaders_slang/crt/crt-royale.slangp", "User/shaders_slang/crt/shaders/x/deep.slangp",
        "User/shaders_slang/bezel/a.slangp", "User/shaders_slang/stock.slangp", "User/two.glslp", "User/two.slangp",
    )

    @Test fun the_top_level_is_none_the_groups_in_use_and_the_users_folder() {
        val top = ShaderBrowse.entries(presets + "glsl/waterpaint.glsl", "")
        assertEquals(listOf("None", "CRT", "Handheld", "Smoothing", "NTSC", "Effects", "Your shaders"), top.map { it.label })
        assertEquals(listOf("@CRT", "@Handheld", "@Smoothing", "@NTSC", "@Effects", "User"), top.filterIsInstance<Folder>().map { it.path })
        assertTrue(top.filterIsInstance<Folder>().all { it.info.isNotEmpty() })
        assertEquals(ShaderBrowse.YOURS_INFO, (top.last() as Folder).info)
        assertEquals(listOf("None", "CRT"), ShaderBrowse.entries(listOf("crt/crt-geom.glslp"), "").map { it.label })
        assertEquals(listOf<ShaderBrowse.Entry>(Preset("None", "None")), ShaderBrowse.entries(emptyList(), ""))
    }

    @Test fun a_group_lists_each_shader_once_by_name_slang_first() {
        assertEquals(
            listOf(Preset("CRT Geom", "slang/crt/crt-geom.slangp"), Preset("CRT Guest Advanced HD", "slang/crt/crt-guest-advanced-hd.slangp"),
                Preset("Line", "nx/line.glslp"), Preset("zFast CRT", "crt/zfast-crt.glslp")),
            ShaderBrowse.entries(presets, "@CRT"),
        )
        assertEquals(listOf(Preset("ScaleFX", "slang/edge-smoothing/scalefx/scalefx.slangp"), Preset("Sharp Bilinear", "interpolation/sharp-bilinear.glslp"),
            Preset("xBRZ Freescale", "xbrz/xbrz-freescale.glslp")), ShaderBrowse.entries(presets, "@Smoothing"))
        assertEquals(listOf(Preset("Dot", "handheld/dot.glslp"), Preset("LCD3x", "glsl/lcd3x.glsl")), ShaderBrowse.entries(presets, "@Handheld"))
        assertEquals(listOf(Preset("NTSC Adaptive", "slang/ntsc/ntsc-adaptive.slangp")), ShaderBrowse.entries(presets, "@NTSC"))
    }

    @Test fun single_file_shaders_are_sorted_by_what_they_do() {
        val single = listOf("glsl/crt-mini.glsl", "glsl/scanline.glsl", "glsl/dmg-perfect.glsl", "glsl/sharp-shimmerless-grid.glsl", "nx/grid.glslp",
            "glsl/pixel_art_AA.glsl", "glsl/sharp-shimmerless.glsl", "glsl/stock.glsl", "glsl/waterpaint.glsl", "glsl/unflat-mini.glsl", "glsl/new-one.glsl")
        fun names(group: String) = ShaderBrowse.entries(single, group).map { it.label }
        assertEquals(listOf("CRT Mini", "Scanline"), names("@CRT"))
        assertEquals(listOf("DMG Perfect", "Grid", "Sharp Shimmerless Grid"), names("@Handheld"))
        assertEquals(listOf("Pixel Art AA", "Sharp Shimmerless", "Stock"), names("@Smoothing"))
        assertEquals(listOf("New One", "Unflat Mini", "Waterpaint"), names("@Effects"))
        assertTrue(ShaderBrowse.entries(single, "@NX Redux").isEmpty())
    }

    @Test fun a_group_carries_its_description_as_the_level_s_intro() {
        val crt = ShaderBrowse.entries(presets, "").filterIsInstance<Folder>().first { it.label == "CRT" }
        assertEquals(crt.info, ShaderBrowse.intro("@CRT"))
        assertEquals("", ShaderBrowse.intro("")); assertEquals("", ShaderBrowse.intro("User")); assertEquals("", ShaderBrowse.intro("User/crt"))
    }

    @Test fun the_users_folder_is_browsed_a_level_at_a_time() {
        assertEquals(
            listOf<ShaderBrowse.Entry>(Folder("shaders_slang", "User/shaders_slang"), Preset("mine", "User/mine.glslp"), Preset("two.glslp", "User/two.glslp"),
                Preset("two.slangp", "User/two.slangp")),
            ShaderBrowse.entries(presets, "User"),
        )
        assertEquals(
            listOf(Folder("bezel", "User/shaders_slang/bezel"), Folder("crt", "User/shaders_slang/crt"), Preset("stock", "User/shaders_slang/stock.slangp")),
            ShaderBrowse.entries(presets, "User/shaders_slang"),
        )
        assertEquals(
            listOf(Folder("shaders", "User/shaders_slang/crt/shaders"), Preset("crt-royale", "User/shaders_slang/crt/crt-royale.slangp")),
            ShaderBrowse.entries(presets, "User/shaders_slang/crt"),
        )
        assertTrue(ShaderBrowse.entries(presets, "User/nope").isEmpty())
        assertTrue(ShaderBrowse.entries(presets, "elsewhere").isEmpty())
    }

    @Test fun titles_and_labels() {
        assertEquals("Shader", ShaderBrowse.title(""))
        assertEquals("CRT", ShaderBrowse.title("@CRT"))
        assertEquals("Your shaders", ShaderBrowse.title("User"))
        assertEquals("crt", ShaderBrowse.title("User/shaders_slang/crt"))
        assertEquals("None", ShaderBrowse.label("None")); assertEquals("None", ShaderBrowse.label(""))
        assertEquals("CRT Guest Advanced HD", ShaderBrowse.label("slang/crt/crt-guest-advanced-hd.slangp"))
        assertEquals("LCD Grid v2", ShaderBrowse.label("slang/handheld/lcd-grid-v2.slangp"))
        assertEquals("AA Shader 4.0", ShaderBrowse.label("glsl/aa-shader-4.0.glsl"))
        assertEquals("Pixel Art AA", ShaderBrowse.label("glsl/pixel_art_AA.glsl"))
        assertEquals("crt-royale", ShaderBrowse.label("User/shaders_slang/crt/crt-royale.slangp"))
    }

    @Test fun the_current_preset_is_itself_or_its_other_format() {
        val geom = Preset("CRT Geom", "slang/crt/crt-geom.slangp")
        assertTrue(ShaderBrowse.isCurrent(geom, "slang/crt/crt-geom.slangp"))
        assertTrue(ShaderBrowse.isCurrent(geom, "crt/crt-geom.glslp"))
        assertFalse(ShaderBrowse.isCurrent(geom, "crt/zfast-crt.glslp"))
        assertFalse(ShaderBrowse.isCurrent(geom, "User/crt/crt-geom.glslp"))
        assertFalse(ShaderBrowse.isCurrent(geom, "None"))
        assertTrue(ShaderBrowse.isCurrent(Preset("None", "None"), ""))
        assertTrue(ShaderBrowse.isCurrent(Preset("mine", "User/mine.glslp"), "User/mine.glslp"))
    }

    @Test fun a_levels_values_are_its_presets_in_order() {
        assertEquals(listOf("slang/crt/crt-geom.slangp", "slang/crt/crt-guest-advanced-hd.slangp", "nx/line.glslp", "crt/zfast-crt.glslp"), ShaderBrowse.values(presets, "@CRT"))
        assertEquals(listOf("None"), ShaderBrowse.values(presets, ""))
    }
}
