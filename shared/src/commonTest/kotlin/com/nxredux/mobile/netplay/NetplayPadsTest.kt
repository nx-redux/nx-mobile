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

    @Test fun the_tag_is_case_insensitive() {
        assertEquals(NetplayPads.forSession("MD", pico), NetplayPads.forSession(" md ", pico))
    }
}
