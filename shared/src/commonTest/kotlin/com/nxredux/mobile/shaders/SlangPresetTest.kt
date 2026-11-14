package com.nxredux.mobile.shaders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlangPresetTest {
    private val stock = "#version 450\nlayout(push_constant) uniform Push { float A; } params;\n" +
        "#pragma stage vertex\nvoid main() { /*v*/ }\n#pragma stage fragment\nvoid main() { /*f*/ }\n"

    private fun files(vararg entries: Pair<String, String>): (String) -> String? = { p -> entries.toMap()[p] }

    @Test fun single_pass_defaults() {
        val chain = SlangPreset.parse("crt/a.slangp", files("crt/a.slangp" to "shaders = 1\nshader0 = shaders/a.slang\n", "crt/shaders/a.slang" to stock))
        val p = chain.passes.single()
        assertNull(p.filterLinear)
        assertEquals(WrapMode.BORDER, p.wrap)
        assertEquals(ScaleType.NONE, p.scaleTypeX); assertEquals(ScaleType.NONE, p.scaleTypeY)
        assertEquals(0, p.frameCountMod); assertEquals("", p.alias)
        assertEquals(SlangPreset.DEFAULT_FORMAT, p.format)
        assertFalse(p.mipmapInput)
        assertTrue(chain.luts.isEmpty()); assertTrue(chain.params.isEmpty())
    }

    @Test fun stages_are_split_with_the_shared_head_in_both_and_lines_kept() {
        val s = SlangPreset.shader("a.slang", files("a.slang" to stock))
        assertTrue(s.vertex.startsWith("#version 450\nlayout(push_constant)"))
        assertTrue(s.fragment.startsWith("#version 450\nlayout(push_constant)"))
        assertTrue("/*v*/" in s.vertex); assertFalse("/*f*/" in s.vertex)
        assertTrue("/*f*/" in s.fragment); assertFalse("/*v*/" in s.fragment)
        assertFalse("#pragma stage" in s.vertex); assertFalse("#pragma stage" in s.fragment)
        val line = stock.lines().indexOfFirst { "/*f*/" in it }
        assertEquals(line, s.fragment.lines().indexOfFirst { "/*f*/" in it })
        assertEquals("", s.vertex.lines()[line])
    }

    @Test fun pragmas_name_format_and_parameters() {
        val src = "#version 450\n#pragma name Blur\n#pragma format R16G16B16A16_SFLOAT\n" +
            "#pragma parameter GLOW \"Glow\" 0.5 0.0 1.0 0.05\n#pragma stage vertex\nvoid main(){}\n#pragma stage fragment\nvoid main(){}\n"
        val chain = SlangPreset.parse("a.slangp", files("a.slangp" to "shaders = 1\nshader0 = a.slang\nGLOW = 0.75\n", "a.slang" to src))
        val p = chain.passes.single()
        assertEquals("Blur", p.alias)
        assertEquals("R16G16B16A16_SFLOAT", p.format)
        assertFalse("#pragma name" in p.vertex); assertFalse("#pragma parameter" in p.fragment); assertFalse("#pragma format" in p.fragment)
        assertEquals(ShaderParam("GLOW", "Glow", 0.75f, 0f, 1f, 0.05f), chain.params.single())
    }

    @Test fun preset_keys_override_the_shader_pragmas() {
        val src = "#version 450\n#pragma name Blur\n#pragma format R16G16B16A16_SFLOAT\n#pragma stage vertex\nvoid main(){}\n#pragma stage fragment\nvoid main(){}\n"
        val preset = "shaders = 2\nshader0 = a.slang\nalias0 = Mine\nsrgb_framebuffer0 = true\nmipmap_input0 = true\nfilter_linear0 = false\n" +
            "wrap_mode0 = mirrored_repeat\nscale_type0 = viewport\nscale0 = 0.5\nframe_count_mod0 = 4\n" +
            "shader1 = a.slang\nfloat_framebuffer1 = true\nscale_type_x1 = absolute\nscale_x1 = 320\nwrap_mode1 = clamp_to_edge\n"
        val chain = SlangPreset.parse("a.slangp", files("a.slangp" to preset, "a.slang" to src))
        val (a, b) = chain.passes
        assertEquals("Mine", a.alias); assertEquals("R8G8B8A8_SRGB", a.format); assertTrue(a.mipmapInput)
        assertEquals(false, a.filterLinear); assertEquals(WrapMode.MIRRORED, a.wrap)
        assertEquals(ScaleType.VIEWPORT, a.scaleTypeX); assertEquals(ScaleType.VIEWPORT, a.scaleTypeY)
        assertEquals(0.5f, a.scaleX); assertEquals(0.5f, a.scaleY); assertEquals(4, a.frameCountMod)
        assertEquals("Blur", b.alias); assertEquals("R16G16B16A16_SFLOAT", b.format); assertEquals(WrapMode.EDGE, b.wrap)
        assertEquals(ScaleType.ABSOLUTE, b.scaleTypeX); assertEquals(ScaleType.SOURCE, b.scaleTypeY); assertEquals(320f, b.scaleX)
    }

    @Test fun includes_expand_relative_to_the_includer_and_optional_ones_may_be_missing() {
        val main = "#version 450\n#include \"inc/common.inc\"\n#pragma include_optional \"missing.inc\"\n#pragma include_optional \"inc/opt.inc\"\n" +
            "#pragma stage vertex\nvoid main(){}\n#pragma stage fragment\nvoid main(){}\n"
        val s = SlangPreset.shader("crt/a.slang", files(
            "crt/a.slang" to main,
            "crt/inc/common.inc" to "#include \"../../shared/deep.inc\"\n#pragma parameter P \"P\" 1.0 0.0 2.0 0.1\n",
            "shared/deep.inc" to "float deep;\n",
            "crt/inc/opt.inc" to "float opt;\n",
        ))
        assertTrue("float deep;" in s.vertex && "float deep;" in s.fragment)
        assertTrue("float opt;" in s.vertex)
        assertFalse("#include" in s.vertex); assertFalse("include_optional" in s.vertex)
        assertEquals("P", s.params.single().id)
    }

    @Test fun a_missing_include_is_an_error() {
        val main = "#version 450\n#include \"nope.inc\"\n#pragma stage vertex\nvoid main(){}\n#pragma stage fragment\nvoid main(){}\n"
        assertFailsWith<PresetException> { SlangPreset.shader("a.slang", files("a.slang" to main)) }
    }

    @Test fun an_include_loop_is_an_error() {
        assertFailsWith<PresetException> { SlangPreset.shader("a.slang", files("a.slang" to "#include \"a.slang\"\n")) }
    }

    @Test fun a_file_without_both_stages_is_an_error() {
        assertFailsWith<PresetException> { SlangPreset.shader("a.slang", files("a.slang" to "#version 450\n#pragma stage vertex\nvoid main(){}\n")) }
    }

    @Test fun a_reference_is_the_base_with_values_changed() {
        val base = "shaders = 1\nshader0 = shaders/a.slang\ntextures = \"LUT\"\nLUT = lut/a.png\nA = 0.25\nfilter_linear0 = true\n"
        val simple = "#reference \"../base/base.slangp\"\nA = 0.5\nLUT = mine.png\nfilter_linear0 = false\n"
        val src = "#version 450\n#pragma parameter A \"A\" 0.0 0.0 1.0 0.05\n#pragma stage vertex\nvoid main(){}\n#pragma stage fragment\nvoid main(){}\n"
        val chain = SlangPreset.parse("my/simple.slangp", files(
            "my/simple.slangp" to simple, "base/base.slangp" to base, "base/shaders/a.slang" to src,
        ))
        assertEquals(1, chain.passes.size)
        assertEquals(false, chain.passes[0].filterLinear)
        assertEquals(0.5f, chain.params.single().default)
        // A path is relative to the file that set it.
        assertEquals(SlangLut("LUT", "my/mine.png", true, WrapMode.BORDER, false), chain.luts.single())
    }

    @Test fun a_reference_loop_is_an_error() {
        assertFailsWith<PresetException> { SlangPreset.parse("a.slangp", files("a.slangp" to "#reference \"a.slangp\"\n")) }
    }

    @Test fun textures_carry_their_sampling() {
        val preset = "shaders = 1\nshader0 = a.slang\ntextures = \"A;B\"\nA = a.png\nA_linear = false\nA_mipmap = true\nA_wrap_mode = repeat\nB = \"sub/b.png\"\n"
        val chain = SlangPreset.parse("p/a.slangp", files("p/a.slangp" to preset, "p/a.slang" to stock))
        assertEquals(listOf(
            SlangLut("A", "p/a.png", false, WrapMode.REPEAT, true),
            SlangLut("B", "p/sub/b.png", true, WrapMode.BORDER, false),
        ), chain.luts)
    }

    @Test fun values_are_unquoted_and_lose_trailing_comments() {
        assertEquals("a b.png", SlangPreset.value(" \"a b.png\" # note"))
        assertEquals("2.0", SlangPreset.value("2.0 # note"))
        assertEquals("2.0", SlangPreset.value("2.0\t// note"))
        assertEquals("a#b.png", SlangPreset.value("a#b.png"))
    }

    @Test fun a_bare_slang_file_is_a_one_pass_chain() {
        val chain = SlangPreset.parse("a.slang", files("a.slang" to stock))
        assertEquals(1, chain.passes.size)
        assertTrue(SlangPreset.isSlang("x/Y.SLANGP")); assertTrue(SlangPreset.isSlang("a.slang")); assertFalse(SlangPreset.isSlang("a.glslp"))
    }

    @Test fun missing_pieces_are_errors() {
        assertFailsWith<PresetException> { SlangPreset.parse("a.slangp", files()) }
        assertFailsWith<PresetException> { SlangPreset.parse("a.slangp", files("a.slangp" to "shader0 = a.slang\n")) }
        assertFailsWith<PresetException> { SlangPreset.parse("a.slangp", files("a.slangp" to "shaders = 1\n")) }
        assertFailsWith<PresetException> { SlangPreset.parse("a.slangp", files("a.slangp" to "shaders = 1\nshader0 = ../../x.slang\n")) }
        assertFailsWith<PresetException> { SlangPreset.parse("a.slangp", files("a.slangp" to "shaders = 1\nshader0 = a.slang\ntextures = L\n", "a.slang" to stock)) }
    }
}
