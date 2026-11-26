package com.nxredux.mobile.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnNetworksTest {
    private fun ip(vararg b: Int) = ByteArray(4) { b[it].toByte() }

    // The Nova's hotspot as seen on the device: 192.168.182.13/24.
    @Test fun a_hotspot_address_gives_its_broadcast_and_mask() {
        val s = OwnNetworks.subnet(ip(192, 168, 182, 13), 24)!!
        assertEquals(0xC0A8B6FF.toInt(), s.broadcast)
        assertEquals(0xFFFFFF00.toInt(), s.mask)
    }

    @Test fun a_prefix_no_hotspot_hands_out_is_skipped() {
        assertNull(OwnNetworks.subnet(ip(10, 0, 0, 1), 32))
        assertNull(OwnNetworks.subnet(ip(10, 0, 0, 1), 0))
        assertNull(OwnNetworks.subnet(ByteArray(16), 64))
    }
}
