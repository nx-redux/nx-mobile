package com.nxredux.mobile.runner

import com.nxredux.mobile.shaders.GlslPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The shader presets ([Shaders]): the bundled set on the private root, the user's own in the home's `Shaders/`. */
class ShadersTest {
    private val base = Files.createTempDirectory("shaders").toFile()
    private val private = File(base, "private")
    private val home = File(base, "home")
    private val roots = Shaders.Roots(private, home)

    private fun write(root: File, rel: String, text: String = "x") = File(File(root, Shaders.DIR), rel).apply { parentFile.mkdirs(); writeText(text) }

    @Test fun the_users_presets_follow_the_bundled_ones_under_their_prefix() {
        write(private, "crt/crt-geom.glslp")
        write(private, "glsl/sharp.glsl")
        write(home, "glsl/PT_SkyWalker541.glsl")
        write(home, "PT_SkyWalker541.cfg")   // not a preset
        write(home, "mine/look.glslp")
        write(home, "mine/pass.glsl")        // a pass, not offered alone
        write(home, ".DS_Store")
        assertEquals(
            listOf("crt/crt-geom.glslp", "glsl/sharp.glsl", "User/glsl/PT_SkyWalker541.glsl", "User/mine/look.glslp"),
            Shaders.presets(roots),
        )
    }

    @Test fun no_home_folder_or_a_private_home_offers_the_bundled_set_only() {
        write(private, "glsl/sharp.glsl")
        assertEquals(listOf("glsl/sharp.glsl"), Shaders.presets(roots))   // the home has no Shaders/
        assertEquals(Shaders.Roots(private, null), Shaders.Roots.of(private, private))   // before the storage move
        assertEquals(Shaders.Roots(private, home), Shaders.Roots.of(private, home))
    }

    @Test fun a_user_preset_and_its_passes_read_from_the_home() {
        write(home, "mine/look.glslp", "shaders = 1\nshader0 = pass.glsl\n")
        write(home, "mine/pass.glsl", "void main() {}")
        val chain = GlslPreset.parse("User/mine/look.glslp") { Shaders.read(roots, it) }
        assertEquals("void main() {}", chain.passes.single().source)
        assertEquals(File(home, "Shaders/mine/pass.glsl"), Shaders.file(roots, "User/mine/pass.glsl"))
        assertEquals(File(private, "Shaders/crt/x.glsl"), Shaders.file(roots, "crt/x.glsl"))
        assertNull(Shaders.file(Shaders.Roots(private, null), "User/mine/pass.glsl"))   // no home: nothing to read
    }
}
