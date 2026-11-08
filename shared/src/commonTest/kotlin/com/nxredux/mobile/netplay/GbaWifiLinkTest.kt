package com.nxredux.mobile.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GbaWifiLinkTest {
    @Test fun mixed_ok_is_the_handhelds_truth_table() {
        assertTrue(GbaWifiLink.mixedOk("rfu", "rfu", usb = true))
        assertFalse(GbaWifiLink.mixedOk("poke", "poke", usb = true))
        assertTrue(GbaWifiLink.mixedOk("poke", "poke", usb = false))
        assertFalse(GbaWifiLink.mixedOk("rfu", "poke", usb = false))
        assertFalse(GbaWifiLink.mixedOk("none", "none", usb = false))
        assertFalse(GbaWifiLink.mixedOk("aw3", "aw3", usb = false))
        assertFalse(GbaWifiLink.mixedOk(null, "rfu", usb = false))
        assertFalse(GbaWifiLink.mixedOk("rfu", null, usb = false))
        assertTrue(GbaWifiLink.mixedOk("aw1", "aw1", usb = false))
        assertTrue(GbaWifiLink.mixedOk("aw2", "aw2", usb = false))
        assertFalse(GbaWifiLink.mixedOk("aw2", "aw2", usb = true))
        assertFalse(GbaWifiLink.mixedOk("", "", usb = false))
    }

    @Test fun anything_but_a_known_mode_reads_as_none() {
        for (m in listOf("rfu", "poke", "aw1", "aw2")) assertEquals(m, GbaWifiLink.token(m))
        for (m in listOf(null, "", "aw3", "RFU", "none")) assertEquals("none", GbaWifiLink.token(m), m.toString())
    }
}
