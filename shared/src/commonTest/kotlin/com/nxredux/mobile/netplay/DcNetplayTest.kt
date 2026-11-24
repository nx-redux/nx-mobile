package com.nxredux.mobile.netplay

import com.nxredux.mobile.netplay.wizard.Caps
import com.nxredux.mobile.netplay.wizard.Hello
import com.nxredux.mobile.netplay.wizard.HelloCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DcNetplayTest {
    @Test fun dreamcast_offers_netplay_on_flycast() {
        assertTrue(NetplayCores.supported("DC"))
        assertEquals(listOf("flycast"), NetplayCores.offered("DC"))
        assertTrue(NetplayCores.ggpo("flycast"))
        assertFalse(NetplayCores.ggpo("swanstation"))
        assertNull(NetplayCores.link("flycast"))   // not a link session: saves are copied, and it never plays on alone
    }

    @Test fun a_dreamcast_session_copies_saves_and_never_becomes_a_local_game() {
        val client = NetplaySession(NetplayRole.CLIENT, "10.0.0.2", "flycast", "flycast")
        assertTrue(client.ggpo)
        assertTrue(client.onCopy)
        assertFalse(client.endsAsLocalGame)
        assertTrue(client.locksOptions)
        assertFalse(NetplaySession(NetplayRole.HOST, "10.0.0.2", "flycast", "flycast").onCopy)
        assertFalse(NetplaySession(NetplayRole.HOST, "10.0.0.2", "snes9x", "snes9x").ggpo)
    }

    @Test fun the_bios_fingerprint_is_the_handhelds_md5_prefix() {
        assertEquals("e10c53c2f8b9", DcNetplaySaves.fingerprint("E10C53C2F8B90BAB96EAD2D368858623"))
        assertEquals("none", DcNetplaySaves.fingerprint(null))
        assertEquals("none", DcNetplaySaves.fingerprint("abc"))
    }

    @Test fun the_real_bios_only_when_both_sides_have_the_same_one() {
        assertFalse(DcNetplaySaves.hle("e10c53c2f8b9", "e10c53c2f8b9"))
        assertTrue(DcNetplaySaves.hle("e10c53c2f8b9", "0123456789ab"))
        assertTrue(DcNetplaySaves.hle("none", "none"))
        assertTrue(DcNetplaySaves.hle("e10c53c2f8b9", null))   // a peer that sent none
        assertTrue(DcNetplaySaves.hle(null, "e10c53c2f8b9"))
    }

    @Test fun the_wire_names_are_the_handhelds_fetch_list() {
        assertEquals(
            setOf("nxcard.bin", "nxnvmem.bin", "nxa2.bin", "nxarc.nvmem", "nxarc.nvmem2", "nxarc.eeprom"),
            DcNetplaySaves.WIRE_NAMES,
        )
        assertTrue(NetplaySaves.isWireName("flycast", "nxcard.bin"))
        assertFalse(NetplaySaves.isWireName("flycast", "nxsave.srm"))
        assertFalse(NetplaySaves.isWireName("snes9x", "nxcard.bin"))
        assertTrue(NetplaySaves.isWireName("snes9x", "nxsave.srm"))
    }

    @Test fun a_disc_host_serves_its_cards_and_settings() {
        val files = DcNetplaySaves.hostFiles("Soulcalibur (USA).chd", arcade = false)
        assertEquals(
            listOf(
                DcNetplaySaves.Served("nxcard.bin", "Soulcalibur (USA).A1.bin", inSystem = false),
                DcNetplaySaves.Served("nxa2.bin", "vmu_save_A2.bin", inSystem = false),
                DcNetplaySaves.Served("nxnvmem.bin", "dc_nvmem.bin", inSystem = true),
            ),
            files,
        )
    }

    @Test fun an_arcade_host_serves_its_saves_and_settings() {
        val files = DcNetplaySaves.hostFiles("MVSC2.zip", arcade = true)
        assertEquals(listOf("nxarc.nvmem", "nxarc.nvmem2", "nxarc.eeprom", "nxnvmem.bin"), files.map { it.wire })
        assertEquals("reicast/mvsc2.zip.nvmem", files.first().path)   // flycast's name: the content as the core sees it
    }

    @Test fun the_joiner_keeps_each_file_where_flycast_reads_it() {
        assertEquals("Crazy Taxi 2 (USA).A1.bin", DcNetplaySaves.localPath("nxcard.bin", "Crazy Taxi 2 (USA).cdi"))
        assertEquals("Crazy Taxi 2 (USA).A1.bin", DcNetplaySaves.localPath("nxcard.bin", "Crazy Taxi 2 (USA).zip"))
        assertEquals("vmu_save_A2.bin", DcNetplaySaves.localPath("nxa2.bin", "Game.chd"))
        assertEquals("system/dc/dc_nvmem.bin", DcNetplaySaves.localPath("nxnvmem.bin", "Game.chd"))
        assertEquals("reicast/mvsc2.zip.eeprom", DcNetplaySaves.localPath("nxarc.eeprom", "mvsc2.zip"))
        assertNull(DcNetplaySaves.localPath("nxsave.srm", "Game.chd"))
        assertNull(DcNetplaySaves.localPath("../nxcard.bin", "Game.chd"))
    }

    @Test fun caps_carry_the_bios_fingerprint() {
        val caps = Caps.of("flycast", "DC", dcBios = "e10c53c2f8b9")
        assertEquals("core=flycast,tag=DC,dcbios=e10c53c2f8b9", caps)
        val hello = HelloCodec.parse(HelloCodec.format(Hello.host("Soulcalibur", 2, caps)))
        assertEquals("e10c53c2f8b9", hello?.dcBios)
        // the handheld's DC.pak sends only the fingerprint
        assertEquals("none", HelloCodec.parse("HELLO 2 Soulcalibur host 2 caps=dcbios=none")?.dcBios)
        assertEquals("core=snes9x,tag=SFC", Caps.of("snes9x", "SFC"))
    }

    @Test fun the_core_gets_the_session_in_its_environment() {
        val host = NetplaySession(NetplayRole.HOST, "192.168.1.7", "flycast", "flycast", dcHle = false)
        assertEquals(
            mapOf(
                "NX_GGPO" to "1", "NX_GGPO_HOST" to "1", "NX_GGPO_SERVER" to "192.168.1.7", "NX_GGPO_HLE" to "0",
                "NX_GGPO_DISCONNECT_MS" to "25000", "NX_GGPO_TIMEOUT" to "60",
            ),
            GgpoSession.env("flycast", host),
        )
        val client = GgpoSession.env("flycast", host.copy(role = NetplayRole.CLIENT, dcHle = true))
        assertEquals("0", client["NX_GGPO_HOST"])
        assertEquals("1", client["NX_GGPO_HLE"])
        // a flycast launch outside a session unsets every key; other cores leave the environment alone
        val solo = GgpoSession.env("flycast", null)
        assertTrue(solo.isNotEmpty() && solo.values.all { it == null })
        assertTrue(GgpoSession.env("snes9x", null).isEmpty())
        assertTrue(GgpoSession.env("snes9x", NetplaySession(NetplayRole.HOST, "1.2.3.4", "snes9x", "snes9x")).isEmpty())
    }

    @Test fun the_pause_ends_before_the_core_gives_up() {
        assertTrue(GgpoSession.LEAVE_GRACE_MS < GgpoSession.DISCONNECT_MS)
    }
}
