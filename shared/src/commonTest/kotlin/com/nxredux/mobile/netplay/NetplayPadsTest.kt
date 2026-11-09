package com.nxredux.mobile.netplay

import com.nxredux.mobile.runner.SegaPad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetplayPadsTest {
    private val pico = "picodrive"
    private val gpgx = "genesis_plus_gx"
    private val threeButtons = mapOf(0 to 257, 1 to 257)   // RETRO_DEVICE_SUBCLASS(JOYPAD, 0): picodrive's 3-button pad

    @Test fun picodrive_gets_the_paks_three_button_pad_on_every_sega_tag() {
        // SMS/GG/SG1000.pak set it; MD/SEGACD/32X.pak leave picodrive's default, also 3 button pad. Never the 6-button pad.
        for (tag in listOf("MD", "SEGACD", "32X", "SMS", "GG", "SG1000", "GPGX")) {
            val pad = assertNotNull(NetplayPads.forSession(tag, pico), tag)
            assertEquals(threeButtons, pad.ports, tag)
            assertTrue(pad.options.isEmpty(), tag)
            assertEquals(SegaPad.THREE, pad.pad, tag)
        }
    }

    @Test fun genesis_plus_gx_gets_minarchs_joypad_on_ports_0_and_1() {
        for (tag in listOf("GPGX", "MD", "SEGACD", "SMS", "GG", "SG1000")) {
            val pad = assertNotNull(NetplayPads.forSession(tag, gpgx), tag)
            assertEquals(mapOf(0 to 1, 1 to 1), pad.ports, tag)   // RETRO_DEVICE_JOYPAD: the core's auto pad
            assertTrue(pad.options.isEmpty(), tag)
            assertNull(pad.pad, tag)                               // the cart header decides, as the core does
        }
    }

    @Test fun every_sega_netplay_pair_is_in_the_table() {
        for (tag in listOf("MD", "SEGACD", "32X", "SMS", "GG", "SG1000", "GPGX")) {
            NetplayCores.offered(tag).forEach { assertNotNull(NetplayPads.forSession(tag, it), "$tag/$it") }
        }
    }

    @Test fun tags_without_a_pad_option_force_nothing() {
        for (tag in listOf("SFC", "SUPA", "FC", "FDS", "FBN", "PS", "PSX")) {
            NetplayCores.offered(tag).forEach { assertNull(NetplayPads.forSession(tag, it), "$tag/$it") }
        }
        assertNull(NetplayPads.forSession("32X", gpgx))   // not a netplay pair
        assertNull(NetplayPads.forSession("MD", null))
    }

    @Test fun mupen64plus_next_gets_the_handhelds_n64_option_set() {
        // nx-redux minarch ma_config.c np_n64_options, value for value
        val want = mapOf(
            "mupen64plus-cpucore" to "dynamic_recompiler", "mupen64plus-rsp-plugin" to "hle",
            "mupen64plus-Framerate" to "Original", "mupen64plus-virefresh" to "Auto",
            "mupen64plus-CountPerOp" to "0", "mupen64plus-CountPerOpDenomPot" to "0",
            "mupen64plus-ForceDisableExtraMem" to "False", "mupen64plus-IgnoreTLBExceptions" to "False",
            "mupen64plus-alt-map" to "True", "mupen64plus-astick-deadzone" to "15", "mupen64plus-astick-sensitivity" to "100",
            "mupen64plus-r-cbutton" to "C1", "mupen64plus-l-cbutton" to "C2", "mupen64plus-d-cbutton" to "C3", "mupen64plus-u-cbutton" to "C4",
            "mupen64plus-pak1" to "memory", "mupen64plus-pak2" to "none", "mupen64plus-pak3" to "none", "mupen64plus-pak4" to "none",
            "mupen64plus-EnableCopyColorToRDRAM" to "Off", "mupen64plus-EnableCopyDepthToRDRAM" to "Off",
            "mupen64plus-ThreadedRenderer" to "False",
        )
        val pad = assertNotNull(NetplayPads.forSession("N64", "mupen64plus_next"))
        assertEquals(want, pad.options)
        assertEquals(mapOf(0 to 1, 1 to 1), pad.ports)
        assertNull(pad.pad)
    }

    @Test fun the_tag_is_case_insensitive() {
        assertEquals(NetplayPads.forSession("MD", pico), NetplayPads.forSession(" md ", pico))
    }
}
