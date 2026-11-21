package com.nxredux.mobile.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalWirelessTextTest {
    @Test fun the_row_says_where_the_link_stands() {
        assertEquals("linked with 1 device", LocalWirelessText.now(inRoom = true, seeking = false, others = 1))
        assertEquals("linked with 3 devices", LocalWirelessText.now(inRoom = true, seeking = false, others = 3))
        assertEquals("linked", LocalWirelessText.now(inRoom = true, seeking = false, others = -1))
        assertEquals("looking for others", LocalWirelessText.now(inRoom = false, seeking = true, others = 0))
        assertEquals("game's wireless off", LocalWirelessText.now(inRoom = false, seeking = false, others = 0))
    }

    @Test fun ds_toasts_follow_the_other_devices() {
        assertEquals("Local wireless: linked with 1 device", LocalWirelessText.change(0, 1))
        assertEquals("Local wireless: linked with 2 devices", LocalWirelessText.change(1, 2))
        assertEquals("Local wireless: a device left", LocalWirelessText.change(2, 1))
        assertEquals("Local wireless: the other device left", LocalWirelessText.change(1, 0))
        assertEquals("Local wireless: the other devices left", LocalWirelessText.change(2, 0))
        assertNull(LocalWirelessText.change(1, 1))
        assertNull(LocalWirelessText.change(-1, 1))
        assertNull(LocalWirelessText.change(1, -1))
    }
}
