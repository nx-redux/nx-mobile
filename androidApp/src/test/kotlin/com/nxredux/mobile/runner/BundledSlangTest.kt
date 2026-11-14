package com.nxredux.mobile.runner

import com.nxredux.mobile.shaders.SlangPreset
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The bundled slang presets are complete: every pass, include and lookup texture a preset names ships with it. */
class BundledSlangTest {
    private val root = listOf("src/main/assets/shaders", "androidApp/src/main/assets/shaders").map(::File).first { it.isDirectory }

    @Test fun every_bundled_slang_preset_parses_and_has_its_textures() {
        val presets = Shaders.presetsIn(root).filter(SlangPreset::isSlang)
        assertTrue("no bundled slang presets found", presets.isNotEmpty())
        for (preset in presets) {
            val chain = SlangPreset.parse(preset) { path -> File(root, path).takeIf { it.isFile }?.readText() }
            assertTrue("$preset has no passes", chain.passes.isNotEmpty())
            for (lut in chain.luts) assertTrue("$preset: ${lut.path} is missing", File(root, lut.path).isFile)
        }
    }
}
