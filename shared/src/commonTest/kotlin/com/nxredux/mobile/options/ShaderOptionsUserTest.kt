package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A preset from the home's own `Shaders/` ([ShaderOptions.USER_PREFIX]). */
class ShaderOptionsUserTest {
    @Test fun a_user_presets_path_is_its_place_in_the_home_folder() {
        assertEquals("glsl/PT_SkyWalker541.glsl", ShaderOptions.userPath("User/glsl/PT_SkyWalker541.glsl"))
        assertNull(ShaderOptions.userPath("crt/crt-geom.glslp"))
    }
}
