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

    @Test fun a_3ds_host_opens_the_room_and_a_3ds_joiner_dials_the_host() {
        assertEquals(
            mapOf("nx_3ds_link_port" to "24872", "nx_3ds_link" to "host", "nx_3ds_link_name" to "Player 1"),
            NetplayLinkOptions.forSession(session(NetplayRole.HOST, "azahar"), null),
        )
        assertEquals(
            mapOf("nx_3ds_link_port" to "24872", "nx_3ds_link" to "join", "nx_3ds_link_name" to "Player 2", "nx_3ds_link_ip" to "192.168.1.7"),
            NetplayLinkOptions.forSession(session(NetplayRole.CLIENT, "azahar", peer = "192.168.1.7"), null),
        )
    }

    @Test fun a_3ds_room_name_is_the_player_name_in_room_characters_and_a_number() {
        assertEquals("KotakNova-0042", NetplayLinkOptions.n3dsRoomName("KotakNova", 42))
        assertEquals("Syus-1234", NetplayLinkOptions.n3dsRoomName("Syu's", 1234))
        assertEquals("Player-0007", NetplayLinkOptions.n3dsRoomName("太郎", 7))
        assertEquals("host", NetplayLinkOptions.forSession(session(NetplayRole.HOST, "azahar"), null, "KotakNova-0042")["nx_3ds_link"])
        assertEquals("KotakNova-0042", NetplayLinkOptions.forSession(session(NetplayRole.HOST, "azahar"), null, "KotakNova-0042")["nx_3ds_link_name"])
    }

    @Test fun a_solo_3ds_game_gets_automatic_local_wireless_under_its_room_name() {
        assertEquals(
            mapOf("nx_3ds_link" to "auto", "nx_3ds_link_name" to "KotakNova-0042", "nx_3ds_link_port" to "24872"),
            NetplayLinkOptions.forSolo("azahar", "KotakNova-0042"),
        )
        assertEquals("192.168.1.255", NetplayLinkOptions.forSolo("azahar", "KotakNova-0042", "192.168.1.255")["nx_3ds_link_broadcast"])
        assertEquals(emptyMap(), NetplayLinkOptions.forSolo("azahar"))   // the console's Local Wireless option is off
        assertEquals(true, com.nxredux.mobile.options.N3dsOptions.localWireless(emptyMap()))
        assertEquals(false, com.nxredux.mobile.options.N3dsOptions.localWireless(mapOf("nx_3ds_local_wireless" to "off")))
        assertEquals(mapOf("mgba_link_mode" to "cable"), NetplayLinkOptions.forSolo("mgba", "x"))
    }

    @Test fun a_ds_mac_is_the_seeds_low_three_bytes_under_the_nintendo_prefix() {
        assertEquals("00:08:BF:0A:0B:0C", NetplayLinkOptions.ndsMac(0x0A0B0C))
        assertEquals("00:08:BF:FF:FF:FF", NetplayLinkOptions.ndsMac(-1))
    }

    @Test fun a_solo_ds_game_gets_automatic_local_wireless_under_its_room_name() {
        val mac = "00:08:BF:0A:0B:0C"
        assertEquals(
            mapOf(
                "nx_nds_link" to "auto", "nx_nds_link_name" to "Ana-0042", "nx_nds_link_port" to "24874",
                "nx_nds_link_broadcast" to "192.168.1.255", "melonds_mac_address_mode" to mac,
            ),
            NetplayLinkOptions.forSolo("melondsds", "Ana-0042", "192.168.1.255", mac),
        )
        assertEquals(emptyMap(), NetplayLinkOptions.forSolo("melondsds", null, null, null))
    }

    @Test fun a_ds_session_hosts_or_joins_with_its_own_mac() {
        val mac = "00:08:BF:01:02:03"
        assertEquals(
            mapOf("nx_nds_link_port" to "24874", "nx_nds_link" to "host", "nx_nds_link_name" to "Player 1", "melonds_mac_address_mode" to mac),
            NetplayLinkOptions.forSession(session(NetplayRole.HOST, "melondsds"), null, null, mac),
        )
        assertEquals(
            mapOf("nx_nds_link_port" to "24874", "nx_nds_link" to "join", "nx_nds_link_name" to "Ana-0042", "nx_nds_link_ip" to "192.168.1.7"),
            NetplayLinkOptions.forSession(session(NetplayRole.CLIENT, "melondsds", peer = "192.168.1.7"), null, "Ana-0042"),
        )
        assertFalse(NetplaySession(NetplayRole.HOST, "1.2.3.4", "melondsds", "melondsds").locksOptions)
    }

    @Test fun the_ds_local_wireless_row_defaults_on() {
        val row = com.nxredux.mobile.options.NdsOptions.localWirelessRow
        assertEquals("nx_nds_local_wireless", row.key)
        assertEquals("on", row.default)
        assertEquals(true, com.nxredux.mobile.options.NdsOptions.localWireless(emptyMap()))
        assertEquals(false, com.nxredux.mobile.options.NdsOptions.localWireless(mapOf(com.nxredux.mobile.options.NdsOptions.LOCAL_WIRELESS to "off")))
        assertTrue(row in com.nxredux.mobile.options.ConsoleSettings.tableFor("NDS"))
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

    @Test fun pair_forced_options_for_host_and_guest() {
        val host = NetplaySession(NetplayRole.HOST, "10.0.0.2", "mgba", "gpsp", cableLink = true, otherRomPath = "/cache/link/abc",
            otherSavePath = "/files/cablelink/other.sav", localSlot = 1, biosMode = "real", rtcEpoch = 1_700_000_000L)
        assertEquals(
            mapOf(
                "mgba_pair" to "ON", "mgba_pair_local" to "1", "mgba_pair_other_rom" to "/cache/link/abc",
                "mgba_pair_other_save" to "/files/cablelink/other.sav", "mgba_pair_rtc" to "1700000000", "mgba_pair_bios" to "real",
                NetplayLinkOptions.MGBA_LINK to NetplayLinkOptions.MGBA_DEFAULT,
            ),
            NetplayLinkOptions.forPair(host),
        )
        val guest = NetplaySession(NetplayRole.CLIENT, "10.0.0.1", "mgba", "mgba", cableLink = true, otherRomPath = "/roms/Ruby.gba",
            otherSavePath = null, localSlot = 2, biosMode = "hle", rtcEpoch = 42L)
        val g = NetplayLinkOptions.forPair(guest)
        assertEquals("2", g["mgba_pair_local"])
        assertEquals("", g["mgba_pair_other_save"])   // set even when there is none: never a stale value from an earlier run
        assertEquals("hle", g["mgba_pair_bios"])
        assertEquals("42", g["mgba_pair_rtc"])
        assertEquals("/roms/Ruby.gba", g["mgba_pair_other_rom"])
        // The session flags: the real save is the player's own, written from checkpoints; achievements for this GBA only.
        assertFalse(host.onCopy)
        assertFalse(host.endsAsLocalGame)
        assertTrue(host.allowsAchievements)
        assertTrue(host.savesAtCheckpoints)
        assertFalse(NetplaySession(NetplayRole.HOST, "1.2.3.4", "mgba", "mgba").savesAtCheckpoints)
        assertEquals(emptyMap(), NetplayLinkOptions.forPair(NetplaySession(NetplayRole.HOST, "1.2.3.4", "mgba", "mgba")))
    }

    @Test fun psp_mac_is_twelve_hex_digits_led_by_02_and_unique_per_seed_in_the_last_five_octets() {
        val a = NetplayLinkOptions.pspMac(1)
        val b = NetplayLinkOptions.pspMac(2)
        assertTrue(Regex("[0-9a-f]{12}").matches(a), a)
        assertTrue(a.startsWith("02") && b.startsWith("02"))
        assertEquals(a, NetplayLinkOptions.pspMac(1))
        assertTrue(a.substring(2) != b.substring(2))
        assertTrue(NetplayLinkOptions.pspMac(0x7fffffff).startsWith("02"))
        assertTrue(Regex("[0-9a-f]{12}").matches(NetplayLinkOptions.pspMac(-5)))
    }

    @Test fun psp_network_forces_the_ad_hoc_options() {
        val want = mapOf(
            "ppsspp_enable_wlan" to "enabled", "ppsspp_enable_builtin_pro_ad_hoc_server" to "disabled",
            "ppsspp_enable_upnp" to "disabled", "ppsspp_port_offset" to "10000",
        ) + "02a1b2c3d4e5".mapIndexed { i, c -> "ppsspp_change_mac_address" + (i + 1).toString().padStart(2, '0') to c.toString() }
        assertEquals(want, NetplayLinkOptions.pspNetwork("02a1b2c3d4e5"))
    }

    @Test fun psp_link_carries_the_mode_and_the_wifi_addresses() {
        assertEquals(
            mapOf("nx_psp_link" to "auto", "nx_psp_link_broadcast" to "192.168.1.255", "nx_psp_link_self" to "192.168.1.24"),
            NetplayLinkOptions.pspLink("auto", null, "192.168.1.255", "192.168.1.24"),
        )
        val join = NetplayLinkOptions.pspLink("join", "192.168.1.5", "192.168.1.255", "192.168.1.24")
        assertEquals("192.168.1.5", join["nx_psp_link_ip"])
        assertEquals("join", join["nx_psp_link"])
        assertEquals(mapOf("nx_psp_link" to "host"), NetplayLinkOptions.pspLink("host", null, null, null))
    }

    @Test fun psp_session_options_follow_the_role() {
        assertEquals(mapOf("nx_psp_link" to "host"), NetplayLinkOptions.forSession(session(NetplayRole.HOST, "ppsspp"), null))
        assertEquals(
            mapOf("nx_psp_link" to "join", "nx_psp_link_ip" to "192.168.1.7"),
            NetplayLinkOptions.forSession(session(NetplayRole.CLIENT, "ppsspp", peer = "192.168.1.7"), null),
        )
        assertFalse(NetplaySession(NetplayRole.HOST, "1.2.3.4", "ppsspp", "ppsspp").locksOptions)
    }

    private val pspSelf = "192.168.1.5"
    private val pspBroadcast = "192.168.1.255"
    private fun pspSessionOptions(role: NetplayRole) = NetplayLinkOptions.forSession(session(role, "ppsspp"), null)

    @Test fun psp_wlan_is_never_on_without_the_link() {
        for (sessionOptions in listOf(null, pspSessionOptions(NetplayRole.HOST), pspSessionOptions(NetplayRole.CLIENT))) {
            for (localWireless in listOf(false, true)) {
                for (self in listOf(null, pspSelf)) {
                    for (broadcast in listOf(null, pspBroadcast)) {
                        val o = NetplayLinkOptions.forPsp(sessionOptions, localWireless, self, broadcast, 0x123456)
                        val wlan = o["ppsspp_enable_wlan"]
                        assertTrue(wlan == "enabled" || wlan == "disabled", "$o")
                        assertEquals(wlan == "enabled", NetplayLinkOptions.PSP_MODE in o, "$o")
                    }
                }
            }
        }
    }

    @Test fun psp_without_local_wireless_or_a_session_is_wlan_off_only() {
        assertEquals(mapOf("ppsspp_enable_wlan" to "disabled"), NetplayLinkOptions.forPsp(null, false, pspSelf, pspBroadcast, 7))
    }

    @Test fun psp_automatic_route_needs_both_addresses() {
        val off = mapOf("ppsspp_enable_wlan" to "disabled")
        assertEquals(off, NetplayLinkOptions.forPsp(null, true, null, pspBroadcast, 7))
        assertEquals(off, NetplayLinkOptions.forPsp(null, true, pspSelf, null, 7))
        assertEquals(off, NetplayLinkOptions.forPsp(null, true, null, null, 7))
        assertEquals(
            NetplayLinkOptions.pspNetwork(NetplayLinkOptions.pspMac(7)) +
                mapOf(NetplayLinkOptions.PSP_MODE to "auto", NetplayLinkOptions.PSP_BROADCAST to pspBroadcast, NetplayLinkOptions.PSP_SELF to pspSelf),
            NetplayLinkOptions.forPsp(null, true, pspSelf, pspBroadcast, 7),
        )
    }

    @Test fun psp_session_is_linked_whatever_local_wireless_says() {
        val join = pspSessionOptions(NetplayRole.CLIENT)
        val linked = NetplayLinkOptions.forPsp(join, false, pspSelf, pspBroadcast, 7)
        assertEquals(
            join + NetplayLinkOptions.pspNetwork(NetplayLinkOptions.pspMac(7)) +
                mapOf(NetplayLinkOptions.PSP_BROADCAST to pspBroadcast, NetplayLinkOptions.PSP_SELF to pspSelf),
            linked,
        )
        assertEquals("join", linked[NetplayLinkOptions.PSP_MODE])
        // Without an address the session still runs; the runner then has no finder to announce on.
        val bare = NetplayLinkOptions.forPsp(pspSessionOptions(NetplayRole.HOST), false, null, pspBroadcast, 7)
        assertEquals("host", bare[NetplayLinkOptions.PSP_MODE])
        assertEquals("enabled", bare["ppsspp_enable_wlan"])
        assertFalse(NetplayLinkOptions.PSP_SELF in bare || NetplayLinkOptions.PSP_BROADCAST in bare)
    }

    @Test fun psp_session_left_falls_back_to_the_automatic_route_with_local_wireless_and_both_addresses() {
        assertEquals(
            mapOf(NetplayLinkOptions.PSP_MODE to "auto", NetplayLinkOptions.PSP_BROADCAST to pspBroadcast, NetplayLinkOptions.PSP_SELF to pspSelf),
            NetplayLinkOptions.pspSessionLeft(localWireless = true, self = pspSelf, broadcast = pspBroadcast),
        )
        // A join's host address is not sent again: the automatic route never reads one.
        assertFalse(NetplayLinkOptions.PSP_IP in NetplayLinkOptions.pspSessionLeft(true, pspSelf, pspBroadcast))
    }

    @Test fun psp_session_left_is_off_without_local_wireless_or_an_address() {
        val off = mapOf(NetplayLinkOptions.PSP_MODE to "off")
        assertEquals(off, NetplayLinkOptions.pspSessionLeft(localWireless = false, self = pspSelf, broadcast = pspBroadcast))
        assertEquals(off, NetplayLinkOptions.pspSessionLeft(true, null, pspBroadcast))
        assertEquals(off, NetplayLinkOptions.pspSessionLeft(true, pspSelf, null))
        assertEquals(off, NetplayLinkOptions.pspSessionLeft(true, null, null))
        assertEquals(off, NetplayLinkOptions.pspSessionLeft(false, null, null))
    }

    @Test fun psp_session_left_is_the_launch_rule_for_the_automatic_route() {
        // What a game launched outside a session would have run with, on the same setting and addresses.
        for (localWireless in listOf(false, true)) {
            for (self in listOf(null, pspSelf)) {
                for (broadcast in listOf(null, pspBroadcast)) {
                    val left = NetplayLinkOptions.pspSessionLeft(localWireless, self, broadcast)
                    val launch = NetplayLinkOptions.forPsp(null, localWireless, self, broadcast, 7)
                    assertEquals(launch[NetplayLinkOptions.PSP_MODE] ?: "off", left[NetplayLinkOptions.PSP_MODE], "$localWireless $self $broadcast")
                    assertEquals(launch[NetplayLinkOptions.PSP_SELF], left[NetplayLinkOptions.PSP_SELF])
                    assertEquals(launch[NetplayLinkOptions.PSP_BROADCAST], left[NetplayLinkOptions.PSP_BROADCAST])
                }
            }
        }
    }

    @Test fun ds_session_left_falls_back_to_the_automatic_route_with_local_wireless() {
        assertEquals(
            mapOf(NetplayLinkOptions.NDS_MODE to "auto", NetplayLinkOptions.NDS_BROADCAST to "192.168.1.255"),
            NetplayLinkOptions.dsSessionLeft(NetplayLinkKind.NDSLINK, localWireless = true, broadcast = "192.168.1.255"),
        )
        assertEquals(
            mapOf(NetplayLinkOptions.N3DS_MODE to "auto", NetplayLinkOptions.N3DS_BROADCAST to "192.168.1.255"),
            NetplayLinkOptions.dsSessionLeft(NetplayLinkKind.N3DSLINK, localWireless = true, broadcast = "192.168.1.255"),
        )
        // No Wi-Fi address: the automatic route still runs, searching on the limited broadcast alone.
        assertEquals(mapOf(NetplayLinkOptions.NDS_MODE to "auto"), NetplayLinkOptions.dsSessionLeft(NetplayLinkKind.NDSLINK, true, null))
        assertEquals(mapOf(NetplayLinkOptions.N3DS_MODE to "auto"), NetplayLinkOptions.dsSessionLeft(NetplayLinkKind.N3DSLINK, true, null))
    }

    @Test fun ds_session_left_is_off_without_local_wireless() {
        assertEquals(mapOf(NetplayLinkOptions.NDS_MODE to "off"), NetplayLinkOptions.dsSessionLeft(NetplayLinkKind.NDSLINK, false, "192.168.1.255"))
        assertEquals(mapOf(NetplayLinkOptions.NDS_MODE to "off"), NetplayLinkOptions.dsSessionLeft(NetplayLinkKind.NDSLINK, false, null))
        assertEquals(mapOf(NetplayLinkOptions.N3DS_MODE to "off"), NetplayLinkOptions.dsSessionLeft(NetplayLinkKind.N3DSLINK, false, "192.168.1.255"))
        assertEquals(mapOf(NetplayLinkOptions.N3DS_MODE to "off"), NetplayLinkOptions.dsSessionLeft(NetplayLinkKind.N3DSLINK, false, null))
    }

    @Test fun ds_session_left_is_nothing_for_other_links() {
        for (kind in listOf(NetplayLinkKind.GBLINK, NetplayLinkKind.NETPACKET, NetplayLinkKind.PSPLINK)) {
            assertEquals(emptyMap(), NetplayLinkOptions.dsSessionLeft(kind, true, "192.168.1.255"), "$kind")
        }
    }

    @Test fun ds_session_left_is_the_launch_rule_for_the_automatic_route() {
        // The mode and broadcast a launch outside a session sends on the same setting and address; name, port and MAC
        // are the session's already, the same values.
        val cases = listOf(
            Triple(NetplayLinkKind.NDSLINK, "melondsds", NetplayLinkOptions.NDS_MODE to NetplayLinkOptions.NDS_BROADCAST),
            Triple(NetplayLinkKind.N3DSLINK, "azahar", NetplayLinkOptions.N3DS_MODE to NetplayLinkOptions.N3DS_BROADCAST),
        )
        for ((kind, core, keys) in cases) {
            for (localWireless in listOf(false, true)) {
                for (broadcast in listOf(null, "192.168.1.255")) {
                    val left = NetplayLinkOptions.dsSessionLeft(kind, localWireless, broadcast)
                    val launch = NetplayLinkOptions.forSolo(core, if (localWireless) "Ana-0042" else null, broadcast, null)
                    assertEquals(launch[keys.first] ?: "off", left[keys.first], "$kind $localWireless $broadcast")
                    assertEquals(launch[keys.second], left[keys.second], "$kind $localWireless $broadcast")
                    assertEquals(setOf(keys.first) + listOfNotNull(keys.second.takeIf { it in launch }), left.keys)
                }
            }
        }
    }

    @Test fun psp_mac_is_the_seed_behind_0200() {
        assertEquals("020000abcdef", NetplayLinkOptions.pspMac(0xabcdef))
    }
}
