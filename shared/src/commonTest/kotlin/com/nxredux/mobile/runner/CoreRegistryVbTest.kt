package com.nxredux.mobile.runner

import com.nxredux.mobile.options.CoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Virtual Boy on Beetle VB: its VR presentation needs stereo video views or a headset, which the frontend never reports. */
class CoreRegistryVbTest {
    private val vb get() = CoreRegistry.forTag("VB")!!
    private val vrKeys = setOf("vb_vr", "vb_vr_screen_distance", "vb_vr_screen_width")

    @Test fun vr_is_locked_off() {
        assertTrue(vb.lockedOptions.containsAll(vrKeys))
        assertEquals("disabled", vb.defaultOptions["vb_vr"])
    }

    @Test fun a_cfg_cannot_turn_head_locked_vr_on() {
        val game = CoreConfig.parse("vb_vr = head-locked\n")
        val r = CoreConfig.resolve(vb.defaultOptions, null, game, forced = vb.lockedOptions)
        assertEquals("disabled", r.values["vb_vr"])
    }

    @Test fun the_3d_mode_stays_anaglyph() {
        assertEquals("anaglyph", vb.defaultOptions["vb_3dmode"])
    }
}
