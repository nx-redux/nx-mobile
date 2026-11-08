package com.nxredux.mobile.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetplayLinkOptionsTest {
    private fun session(
        role: NetplayRole, core: String, peer: String = "192.168.1.7", usb: Boolean = false, mixed: Boolean = false, peerWl: String? = null,
    ) = NetplaySession(role, peer, core, core, overUsb = usb, mixed = mixed, peerWl = peerWl)

    @Test fun gpsp_serial_follows_the_handhelds_rewrite() {
        assertEquals("auto_cable", NetplayLinkOptions.gpspSerial("auto", overUsb = true, mixed = false))
        assertEquals("auto", NetplayLinkOptions.gpspSerial("auto", overUsb = false, mixed = false))
        assertEquals("auto", NetplayLinkOptions.gpspSerial("lockstep", overUsb = false, mixed = false))
        assertEquals("lockstep", NetplayLinkOptions.gpspSerial("lockstep", overUsb = true, mixed = false))
        assertEquals("rfu", NetplayLinkOptions.gpspSerial("rfu", overUsb = true, mixed = false))
        assertEquals("mul_poke", NetplayLinkOptions.gpspSerial("mul_poke", overUsb = false, mixed = false))
    }

    @Test fun a_mixed_session_runs_the_games_own_mode_whatever_is_pinned() {
        assertEquals("auto", NetplayLinkOptions.gpspSerial("disabled", overUsb = false, mixed = true))
        assertEquals("auto", NetplayLinkOptions.gpspSerial("mul_aw1", overUsb = false, mixed = true))
        assertEquals("auto_cable", NetplayLinkOptions.gpspSerial("lockstep", overUsb = true, mixed = true))
        assertEquals("auto", NetplayLinkOptions.mgbaMode("cable", overUsb = false, peerHasWl = true, mixed = true))
        assertEquals("auto_cable", NetplayLinkOptions.mgbaMode("rfu", overUsb = true, peerHasWl = true, mixed = true))
    }

    @Test fun mgba_mode_follows_the_handhelds_rewrite() {
        // A peer that sent no wl= speaks only the real cable.
        assertEquals("cable", NetplayLinkOptions.mgbaMode("auto", overUsb = true, peerHasWl = false, mixed = false))
        assertEquals("cable", NetplayLinkOptions.mgbaMode("rfu", overUsb = false, peerHasWl = false, mixed = false))
        assertEquals("auto_cable", NetplayLinkOptions.mgbaMode("auto", overUsb = true, peerHasWl = true, mixed = false))
        assertEquals("auto", NetplayLinkOptions.mgbaMode("auto", overUsb = false, peerHasWl = true, mixed = false))
        assertEquals("auto", NetplayLinkOptions.mgbaMode("cable", overUsb = false, peerHasWl = true, mixed = false))
        assertEquals("mul_poke", NetplayLinkOptions.mgbaMode("mul_poke", overUsb = false, peerHasWl = true, mixed = false))
        assertEquals("disabled", NetplayLinkOptions.mgbaMode("disabled", overUsb = true, peerHasWl = true, mixed = false))
    }

    /** gbalink_mode.c's mgba_link_mode rows (test_mgba_option_table there): one truth table on both frontends. */
    @Test fun mgba_mode_matches_the_handhelds_table() {
        val values = listOf("cable", "auto", "auto_cable", "rfu", "mul_poke", "mul_aw1", "mul_aw2", "disabled")
        for (v in values) for (usb in listOf(true, false)) {
            assertEquals("cable", NetplayLinkOptions.mgbaMode(v, usb, peerHasWl = false, mixed = false), "$v usb=$usb old peer")
            assertEquals(if (usb) "auto_cable" else "auto", NetplayLinkOptions.mgbaMode(v, usb, peerHasWl = true, mixed = true), "$v usb=$usb mixed")
        }
        val usb = mapOf("cable" to "auto_cable", "auto" to "auto_cable", "auto_cable" to "auto_cable", "rfu" to "rfu", "mul_poke" to "mul_poke", "disabled" to "disabled")
        val wifi = mapOf("cable" to "auto", "auto" to "auto", "auto_cable" to "auto", "rfu" to "rfu", "mul_aw2" to "mul_aw2", "disabled" to "disabled")
        usb.forEach { (v, want) -> assertEquals(want, NetplayLinkOptions.mgbaMode(v, overUsb = true, peerHasWl = true, mixed = false), "$v usb") }
        wifi.forEach { (v, want) -> assertEquals(want, NetplayLinkOptions.mgbaMode(v, overUsb = false, peerHasWl = true, mixed = false), "$v wifi") }
    }

    /** gbalink_mode.c's gpsp_serial rows (test_gpsp_option_table there). */
    @Test fun gpsp_serial_matches_the_handhelds_table() {
        for (v in listOf("auto", "auto_cable", "lockstep", "rfu", "mul_poke", "disabled")) {
            assertEquals("auto_cable", NetplayLinkOptions.gpspSerial(v, overUsb = true, mixed = true), v)
            assertEquals("auto", NetplayLinkOptions.gpspSerial(v, overUsb = false, mixed = true), v)
            assertEquals(if (v == "auto") "auto_cable" else v, NetplayLinkOptions.gpspSerial(v, overUsb = true, mixed = false), v)
            assertEquals(if (v == "lockstep") "auto" else v, NetplayLinkOptions.gpspSerial(v, overUsb = false, mixed = false), v)
        }
    }

    @Test fun a_solo_mgba_launch_runs_the_real_cable() {
        // The mode only matters in a session; "cable" keeps the core from scanning the ROM on every load.
        assertEquals(mapOf("mgba_link_mode" to "cable"), NetplayLinkOptions.forSolo("mgba"))
        assertEquals(emptyMap<String, String>(), NetplayLinkOptions.forSolo("gpsp"))
        assertEquals(emptyMap<String, String>(), NetplayLinkOptions.forSolo("snes9x"))
    }

    @Test fun ip_digits_pad_each_octet_to_three() {
        assertEquals("192168001007", NetplayLinkOptions.ipDigits("192.168.1.7"))
        assertEquals("010099000001", NetplayLinkOptions.ipDigits("10.99.0.1"))
        assertNull(NetplayLinkOptions.ipDigits("192.168.1"))
        assertNull(NetplayLinkOptions.ipDigits("192.168.1.256"))
        assertNull(NetplayLinkOptions.ipDigits("a.b.c.d"))
        assertNull(NetplayLinkOptions.ipDigits("1..2.3"))
        assertNull(NetplayLinkOptions.ipDigits("1.2.3.+4"))
    }

    @Test fun a_gb_host_serves_and_a_gb_joiner_dials_the_host() {
        assertEquals(
            mapOf("gambatte_gb_link_network_port" to "56400", "gambatte_gb_link_mode" to "Network Server"),
            NetplayLinkOptions.forSession(session(NetplayRole.HOST, "gambatte"), null),
        )
        val joiner = NetplayLinkOptions.forSession(session(NetplayRole.CLIENT, "gambatte", peer = "192.168.1.7"), null)
        assertEquals("Network Client", joiner["gambatte_gb_link_mode"])
        assertEquals("56400", joiner["gambatte_gb_link_network_port"])
        assertEquals("192168001007".map { it.toString() }, (1..12).map { joiner["gambatte_gb_link_network_server_ip_$it"] })
        assertEquals(14, joiner.size)
    }

    @Test fun gpsp_and_mgba_run_the_agreed_mode_for_the_transport() {
        assertEquals(mapOf("gpsp_serial" to "auto_cable"), NetplayLinkOptions.forSession(session(NetplayRole.HOST, "gpsp", usb = true), "auto"))
        assertEquals(mapOf("gpsp_serial" to "auto"), NetplayLinkOptions.forSession(session(NetplayRole.CLIENT, "gpsp"), "lockstep"))
        assertEquals(mapOf("gpsp_serial" to "auto"), NetplayLinkOptions.forSession(session(NetplayRole.CLIENT, "gpsp"), null))
        assertEquals(mapOf("gpsp_serial" to "auto"), NetplayLinkOptions.forSession(session(NetplayRole.CLIENT, "gpsp", mixed = true, peerWl = "rfu"), "mul_poke"))
        assertEquals(mapOf("mgba_link_mode" to "cable"), NetplayLinkOptions.forSession(session(NetplayRole.HOST, "mgba", usb = true), ""))
        assertEquals(mapOf("mgba_link_mode" to "auto_cable"), NetplayLinkOptions.forSession(session(NetplayRole.HOST, "mgba", usb = true, peerWl = "none"), null))
        assertEquals(mapOf("mgba_link_mode" to "auto"), NetplayLinkOptions.forSession(session(NetplayRole.CLIENT, "mgba", peerWl = "rfu"), "cable"))
        assertEquals(mapOf("mgba_link_mode" to "auto_cable"), NetplayLinkOptions.forSession(session(NetplayRole.CLIENT, "mgba", usb = true, peerWl = "rfu"), "auto"))
        assertEquals(mapOf("mgba_link_mode" to "auto"), NetplayLinkOptions.forSession(session(NetplayRole.HOST, "mgba", mixed = true, peerWl = "poke"), "disabled"))
        assertEquals(emptyMap<String, String>(), NetplayLinkOptions.forSession(session(NetplayRole.HOST, "snes9x"), null))
    }

    @Test fun ready_modes() {
        assertEquals("rfu", NetplayLinkOptions.hostMode("gpsp", "rfu"))
        assertEquals("auto", NetplayLinkOptions.hostMode("gpsp", null))
        assertEquals("cable", NetplayLinkOptions.hostMode("mgba", null))
        assertEquals("rfu", NetplayLinkOptions.hostMode("mgba", "rfu"))
        assertEquals("", NetplayLinkOptions.hostMode("snes9x", "rfu"))
        assertEquals("lockstep", NetplayLinkOptions.joinerMode("gpsp", "auto", "lockstep", mixed = false))
        assertEquals("rfu", NetplayLinkOptions.joinerMode("gpsp", "rfu", "", mixed = false))   // the host sent none: keep this device's
        assertEquals("auto", NetplayLinkOptions.joinerMode("gpsp", null, "", mixed = false))
        assertEquals("cable", NetplayLinkOptions.joinerMode("mgba", null, "", mixed = false))
        assertEquals("auto", NetplayLinkOptions.joinerMode("mgba", "cable", "auto", mixed = false))
        // Mixed: the host's value is the other core's option; keep this device's own.
        assertEquals("cable", NetplayLinkOptions.joinerMode("mgba", "cable", "rfu", mixed = true))
        assertEquals("auto", NetplayLinkOptions.joinerMode("gpsp", null, "cable", mixed = true))
        assertEquals("", NetplayLinkOptions.joinerMode("snes9x", null, "auto", mixed = false))
    }

    @Test fun each_link_core_has_its_mode_key() {
        assertEquals("gpsp_serial", NetplayLinkOptions.modeKey("gpsp"))
        assertEquals("mgba_link_mode", NetplayLinkOptions.modeKey("mgba"))
        assertNull(NetplayLinkOptions.modeKey("gambatte"))
    }

    @Test fun link_sessions_play_on_real_saves() {
        assertFalse(session(NetplayRole.CLIENT, "gpsp").onCopy)
        assertFalse(NetplaySession(NetplayRole.CLIENT, "1.2.3.4", "gambatte", "gambatte", emptySaves = true).onCopy)
        assertFalse(NetplaySession(NetplayRole.HOST, "1.2.3.4", "mgba", "mgba").onCopy)
        assertTrue(NetplaySession(NetplayRole.CLIENT, "1.2.3.4", "snes9x", "snes9x").onCopy)   // lockstep: unchanged
    }

    @Test fun only_a_link_session_ends_as_a_local_game() {
        assertTrue(session(NetplayRole.CLIENT, "gpsp").endsAsLocalGame)
        assertTrue(NetplaySession(NetplayRole.HOST, "1.2.3.4", "mgba", "gpsp").endsAsLocalGame)
        assertTrue(NetplaySession(NetplayRole.CLIENT, "1.2.3.4", "gambatte", "gambatte").endsAsLocalGame)
        // Lockstep: the client ran the host's synced state, so the ended game stays a netplay one.
        assertFalse(NetplaySession(NetplayRole.HOST, "1.2.3.4", "snes9x", "snes9x").endsAsLocalGame)
        assertFalse(NetplaySession(NetplayRole.CLIENT, "1.2.3.4", "picodrive", "picodrive").endsAsLocalGame)
    }

    @Test fun only_a_link_session_runs_achievements() {
        assertTrue(session(NetplayRole.HOST, "gpsp").allowsAchievements)
        assertTrue(NetplaySession(NetplayRole.CLIENT, "1.2.3.4", "mgba", "mgba").allowsAchievements)
        assertTrue(NetplaySession(NetplayRole.HOST, "1.2.3.4", "gambatte", "gambatte").allowsAchievements)
        assertFalse(NetplaySession(NetplayRole.HOST, "1.2.3.4", "snes9x", "snes9x").allowsAchievements)
        assertFalse(NetplaySession(NetplayRole.CLIENT, "1.2.3.4", "fceumm", "fceumm").allowsAchievements)
    }
}
