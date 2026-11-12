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

    /** The app forces the ad hoc network options ([com.nxredux.mobile.netplay.NetplayLinkOptions.pspNetwork]) and hides their rows. */
    @Test fun the_ad_hoc_network_options_are_locked() {
        val nn = (1..12).map { it.toString().padStart(2, '0') }
        val want = setOf(
            "ppsspp_enable_wlan", "ppsspp_enable_builtin_pro_ad_hoc_server", "ppsspp_change_pro_ad_hoc_server_address",
            "ppsspp_enable_upnp", "ppsspp_upnp_use_original_port", "ppsspp_port_offset", "ppsspp_wlan_channel",
        ) + nn.map { "ppsspp_change_mac_address$it" } + nn.map { "ppsspp_pro_ad_hoc_server_address$it" }
        assertTrue(psp.lockedOptions.containsAll(want), "missing: ${want - psp.lockedOptions}")
    }
}
