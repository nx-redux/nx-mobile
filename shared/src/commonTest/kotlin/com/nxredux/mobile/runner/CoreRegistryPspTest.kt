package com.nxredux.mobile.runner

import com.nxredux.mobile.options.CoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PSP on PPSSPP: VR Mode needs stereo video views, which the frontend never reports, so it stays Off. */
class CoreRegistryPspTest {
    private val psp get() = CoreRegistry.forTag("PSP")!!

    @Test fun vr_mode_is_locked_off() {
        assertTrue("ppsspp_vr_mode" in psp.lockedOptions)
        assertEquals("Off", psp.defaultOptions["ppsspp_vr_mode"])
    }

    @Test fun a_cfg_cannot_turn_vr_mode_on() {
        val game = CoreConfig.parse("ppsspp_vr_mode = Stereoscopic\n")
        val r = CoreConfig.resolve(psp.defaultOptions, null, game, forced = psp.lockedOptions)
        assertEquals("Off", r.values["ppsspp_vr_mode"])
    }

    @Test fun restart_only_options_are_ones_the_core_still_has() {
        val gone = setOf(
            "ppsspp_cache_iso", "ppsspp_gpu_hardware_transform", "ppsspp_software_skinning", "ppsspp_hardware_tesselation",
        )
        assertTrue(RestartOnlyOptions.ppsspp.none { it in gone })
    }
}
