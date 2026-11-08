package com.nxredux.mobile.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncWireTest {
    @Test fun the_phone_says_hello_and_ack_as_client_only_without_a_trailer() {
        assertEquals("HELLO_TRIMUI_SYNC:192.168.1.5:c", SyncWire.hello("192.168.1.5"))
        assertEquals("TRIMUI_SYNC_ACK:10.99.0.1:c", SyncWire.ack("10.99.0.1"))
    }

    @Test fun flags_parse_as_the_handheld_reads_them() {
        fun flags(s: String) = (SyncWire.parse("HELLO_TRIMUI_SYNC:10.0.0.2$s") as SyncMessage.Hello).flags
        assertEquals(SyncFlags(romsOn = false, clientOnly = false), flags(""))
        assertEquals(SyncFlags(romsOn = false, clientOnly = false), flags(":0"))
        assertEquals(SyncFlags(romsOn = true, clientOnly = false), flags(":R"))
        assertEquals(SyncFlags(romsOn = false, clientOnly = true), flags(":c"))
        assertEquals(SyncFlags(romsOn = true, clientOnly = true), flags(":C"))
    }

    @Test fun a_v2_trailer_or_a_client_only_flag_means_the_peer_filters() {
        assertTrue((SyncWire.parse("HELLO_TRIMUI_SYNC:10.0.0.2:0\u0000v2") as SyncMessage.Hello).filters)
        assertTrue((SyncWire.parse("TRIMUI_SYNC_ACK:10.0.0.2:R\u0000v2") as SyncMessage.Ack).filters)
        assertTrue((SyncWire.parse("HELLO_TRIMUI_SYNC:10.0.0.2:c") as SyncMessage.Hello).filters)
        assertFalse((SyncWire.parse("HELLO_TRIMUI_SYNC:10.0.0.2:0") as SyncMessage.Hello).filters)
        assertFalse((SyncWire.parse("HELLO_TRIMUI_SYNC:10.0.0.2:0\u0000v3") as SyncMessage.Hello).filters)
        assertEquals("10.0.0.2", (SyncWire.parse("HELLO_TRIMUI_SYNC:10.0.0.2:0\u0000v2") as SyncMessage.Hello).ip)
    }

    @Test fun ready_done_and_fail_are_exact() {
        assertEquals(SyncMessage.Ready, SyncWire.parse("TRIMUI_SYNC_READY"))
        assertEquals(SyncMessage.Done, SyncWire.parse("TRIMUI_SYNC_DONE"))
        assertEquals(SyncMessage.Fail, SyncWire.parse("TRIMUI_SYNC_FAIL"))
        assertEquals(SyncMessage.Fail, SyncWire.parse("TRIMUI_SYNC_FAIL\u0000v2"))
        assertNull(SyncWire.parse("TRIMUI_SYNC_FAILED"))
        assertNull(SyncWire.parse("TRIMUI_SYNC_READYX"))
    }

    @Test fun garbage_is_ignored() {
        listOf(
            "", "HELLO", "HELLO_TRIMUI_SYNC:", "HELLO_TRIMUI_SYNC:host.lan:0", "HELLO_TRIMUI_SYNC:10.0.0.2:x",
            "HELLO_TRIMUI_SYNC:10.0.0.2:RR", "HELLO_TRIMUI_SYNC:10.0.0.256:0", "HELLO_TRIMUI_SYNC:10.0.0:0",
            "HELLO_TRIMUI_SYNC:010.0.0.2:0", "NETPLAY:10.0.0.2",
        ).forEach { assertNull(SyncWire.parse(it), it) }
    }

    @Test fun ipv4_is_canonical_dotted_only() {
        assertEquals("10.99.0.1", SyncWire.ipv4("10.99.0.1"))
        listOf(null, "", "1.2.3", "1.2.3.4.5", "1.2.3.-4", "01.2.3.4", "1.2.3.4 ", "a.b.c.d", "256.1.1.1").forEach { assertNull(SyncWire.ipv4(it), "$it") }
    }

    @Test fun roles_follow_the_handhelds_sync_is_server() {
        // Exactly one client-only side: the other serves, whatever the IPs.
        assertFalse(SyncRoles.isServer(ownClientOnly = true, peerClientOnly = false, ownIp = "10.0.0.1", peerIp = "10.0.0.9"))
        assertTrue(SyncRoles.isServer(ownClientOnly = false, peerClientOnly = true, ownIp = "10.0.0.9", peerIp = "10.0.0.1"))
        // Both or neither: the lower IP serves (numeric, not text: 10.0.0.9 < 10.0.0.10).
        assertTrue(SyncRoles.isServer(ownClientOnly = true, peerClientOnly = true, ownIp = "10.0.0.9", peerIp = "10.0.0.10"))
        assertFalse(SyncRoles.isServer(ownClientOnly = true, peerClientOnly = true, ownIp = "10.0.0.10", peerIp = "10.0.0.9"))
        assertTrue(SyncRoles.isServer(ownClientOnly = false, peerClientOnly = false, ownIp = "9.255.255.255", peerIp = "10.0.0.0"))
    }

    @Test fun the_phone_is_the_client_of_a_handheld_and_the_lower_ip_serves_between_phones() {
        // A new handheld (not client-only): the phone is the client even with the lower IP (the cable: 10.99.0.1 < 10.99.0.2).
        assertFalse(SyncRoles.isServer(ownClientOnly = true, peerClientOnly = false, ownIp = "10.99.0.1", peerIp = "10.99.0.2"))
        // Another phone (c): both client-only, the lower IP serves.
        assertTrue(SyncRoles.isServer(ownClientOnly = true, peerClientOnly = true, ownIp = "192.168.1.5", peerIp = "192.168.1.10"))
        assertFalse(SyncRoles.isServer(ownClientOnly = true, peerClientOnly = true, ownIp = "192.168.1.20", peerIp = "192.168.1.10"))
    }

    @Test fun roms_toggle_sends_capital_c() {
        assertEquals("HELLO_TRIMUI_SYNC:192.168.1.20:C", SyncWire.hello("192.168.1.20", roms = true))
        assertEquals("TRIMUI_SYNC_ACK:192.168.1.20:C", SyncWire.ack("192.168.1.20", roms = true))
        assertEquals("HELLO_TRIMUI_SYNC:192.168.1.20:c", SyncWire.hello("192.168.1.20"))
    }
}
