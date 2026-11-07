package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SegaPadTypeTest {
    /** A 0x1A0-byte header: "SEGA MEGA DRIVE " at 0x100, [io] at 0x190 (space padded to 16). */
    private fun header(io: String, console: String = "SEGA MEGA DRIVE "): ByteArray {
        val b = ByteArray(SegaHeader.LENGTH) { ' '.code.toByte() }
        console.encodeToByteArray().copyInto(b, 0x100)
        io.padEnd(16).encodeToByteArray().copyInto(b, 0x190)
        return b
    }

    @Test fun io_field_picks_three_or_six() {
        assertEquals(SegaPad.THREE, SegaHeader.padType(header("J")))
        assertEquals(SegaPad.SIX, SegaHeader.padType(header("6")))
        assertEquals(SegaPad.SIX, SegaHeader.padType(header("JD6")))
        assertEquals(SegaPad.THREE, SegaHeader.padType(header("")))
        assertEquals(SegaPad.SIX, SegaHeader.padType(header("J6", console = "SEGA 32X        ")))
        assertEquals(SegaPad.THREE, SegaHeader.padType(header("J", console = " SEGA GENESIS   ")))
    }

    /** genesis_plus_gx reads only the field's first 14 bytes (`core/loadrom.c:293`, `i < 14`): a '6' after them is ignored. */
    @Test fun io_field_scans_first_fourteen_bytes() {
        assertEquals(SegaPad.SIX, SegaHeader.padType(header("J".padEnd(13) + "6")))
        assertEquals(SegaPad.THREE, SegaHeader.padType(header("J".padEnd(14) + "6")))
        assertEquals(SegaPad.THREE, SegaHeader.padType(header("J".padEnd(14) + "66")))
    }

    /** An interleaved .smd: 512-byte header (0xAA 0xBB at 8-9), then a 16 KB block with odd bytes first, even bytes second. */
    private fun smd(io: String): ByteArray {
        val rom = header(io)
        val b = ByteArray(SegaHeader.READ_LENGTH)
        b[8] = 0xAA.toByte(); b[9] = 0xBB.toByte()
        for (i in 0 until rom.size / 2) {
            b[0x200 + i] = rom[2 * i + 1]
            b[0x200 + 0x2000 + i] = rom[2 * i]
        }
        return b
    }

    @Test fun interleaved_smd_is_deinterleaved() {
        assertEquals(SegaPad.THREE, SegaHeader.padType(smd("J")))
        assertEquals(SegaPad.SIX, SegaHeader.padType(smd("J6")))
        assertNull(SegaHeader.padType(smd("J6").copyOf(SegaHeader.LENGTH)))   // block not read
    }

    @Test fun unreadable_headers_are_unknown() {
        assertNull(SegaHeader.padType(ByteArray(0)))
        assertNull(SegaHeader.padType(header("6").copyOf(0x19F)))            // too short for the I/O field
        assertNull(SegaHeader.padType(ByteArray(SegaHeader.LENGTH) { 0x5A })) // no "SEGA" at 0x100 (garbage, interleaved .smd)
    }

    @Test fun header_is_read_only_for_cart_files() {
        for (n in listOf("Sonic.md", "a.GEN", "b.bin", "c.32x", "d.smd")) assertTrue(SegaHeader.readable(n), n)
        for (n in listOf("Game.cue", "Game.chd", "Game.iso", "Game.zip", "Game")) assertFalse(SegaHeader.readable(n), n)
    }

    @Test fun resolve_setting_over_header() {
        assertEquals(SegaPad.THREE, SegaPadType.resolve("3", SegaPad.SIX))
        assertEquals(SegaPad.SIX, SegaPadType.resolve("6", SegaPad.THREE))
        assertEquals(SegaPad.SIX, SegaPadType.resolve("6", null))
        assertEquals(SegaPad.THREE, SegaPadType.resolve("auto", SegaPad.THREE))
        assertEquals(SegaPad.SIX, SegaPadType.resolve(null, SegaPad.SIX))
        assertNull(SegaPadType.resolve("auto", null))
        assertNull(SegaPadType.resolve("junk", null))
    }

    @Test fun gpgx_port_only_when_forced() {
        assertNull(SegaPadType.gpgxPort0("auto")); assertNull(SegaPadType.gpgxPort0(null))
        assertEquals(257, SegaPadType.gpgxPort0("3")); assertEquals(513, SegaPadType.gpgxPort0("6"))
    }

    @Test fun picodrive_ports_default_to_six_when_unknown() {
        assertEquals(mapOf(0 to 257, 1 to 257), SegaPadType.picodrivePorts("3", null, players = 2))
        assertEquals(mapOf(0 to 513, 1 to 513), SegaPadType.picodrivePorts("auto", SegaPad.SIX, players = 2))
        assertEquals(mapOf(0 to 257), SegaPadType.picodrivePorts("auto", SegaPad.THREE, players = 1))
        assertEquals(mapOf(0 to 513, 1 to 513), SegaPadType.picodrivePorts("auto", null, players = 2))
    }

    @Test fun gpgx_live_port_hands_auto_back_to_the_core() {
        assertEquals(1, SegaPadType.gpgxPortLive("auto")); assertEquals(1, SegaPadType.gpgxPortLive(null))
        assertEquals(257, SegaPadType.gpgxPortLive("3")); assertEquals(513, SegaPadType.gpgxPortLive("6"))
    }

    @Test fun a_forced_gpgx_pad_goes_to_every_player_port() {
        assertEquals(mapOf(0 to 513, 1 to 513), SegaPadType.gpgxPorts("6", players = 2))
        assertEquals(mapOf(0 to 257), SegaPadType.gpgxPorts("3", players = 1))
        assertNull(SegaPadType.gpgxPorts("auto", players = 2))
    }

    @Test fun live_changes_reach_player_two() {
        assertEquals(listOf(SegaPadChange.Port(0, 513), SegaPadChange.Port(1, 513)), SegaPadType.liveChanges("genesis_plus_gx", "6", null, players = 2))
        assertEquals(listOf(SegaPadChange.Port(0, 1)), SegaPadType.liveChanges("genesis_plus_gx", "auto", null, players = 1))
        assertEquals(listOf(SegaPadChange.Port(0, 257), SegaPadChange.Port(1, 257)), SegaPadType.liveChanges("picodrive", "3", null, players = 2))
        assertEquals(emptyList(), SegaPadType.liveChanges("mednafen_psx_hw", "6", null, players = 2))
    }

    @Test fun live_changes_per_core() {
        assertEquals(listOf(SegaPadChange.Port(0, 1)), SegaPadType.liveChanges("genesis_plus_gx", "auto", SegaPad.THREE, players = 1))
        assertEquals(listOf(SegaPadChange.Port(0, 257)), SegaPadType.liveChanges("genesis_plus_gx", "3", null, players = 1))
        assertEquals(listOf(SegaPadChange.Port(0, 257), SegaPadChange.Port(1, 257)), SegaPadType.liveChanges("picodrive", "auto", SegaPad.THREE, players = 2))
        assertEquals(listOf(SegaPadChange.Port(0, 513), SegaPadChange.Port(1, 513)), SegaPadType.liveChanges("picodrive", "auto", null, players = 2))
    }
}
