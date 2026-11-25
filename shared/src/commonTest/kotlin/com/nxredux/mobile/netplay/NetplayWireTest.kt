package com.nxredux.mobile.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetplayWireTest {
    private val esc = "\u001f"

    @Test fun normalize_matches_the_handheld_fixtures() {
        // nx-redux wiz_normalize_name's own results.
        val cases = mapOf(
            "Pokemon - FireRed (USA).gba" to "pokemonfireredgba",
            "Pokemon - FireRed (USA)" to "pokemonfirered",
            "Pokemon - LeafGreen (USA)" to "pokemonleafgreen",
            "Super Mario Kart (USA)" to "supermariokart",
            "Super Mario Kart" to "supermariokart",
            "Marvel vs. Capcom 2 (USA)" to "marvelvscapcom2",
            "Street Fighter II' Turbo (Japan) [!]" to "streetfighteriiturbo",
            "Sonic the Hedgehog 2 (World) (Rev A)" to "sonicthehedgehog2",
            "Final Fantasy III (USA) (Rev 1) [h1C]" to "finalfantasyiii",
            "Chrono Trigger [T+Fre]" to "chronotrigger",
            "(Unl) [b]" to "",
            "Tetris (World) (Rev 1)) Extra" to "tetrisextra",
            "Game ((nested) tag) Title" to "gametitle",
            "Pokémon Rouge" to "pokmonrouge",
            "Donkey Kong Country 2 - Diddy's Kong Quest (USA) (En,Fr) (Rev 1)" to "donkeykongcountry2diddyskongquest",
            "super${esc}mario${esc}kart" to "supermariokart",
            "Super Street Fighter II - The New Challengers (USA) - Turbo Edition With A Very Long Name That Goes Beyond Sixty Four Characters"
                to "superstreetfighteriithenewchallengersturboeditionwithaverylongn",
        )
        cases.forEach { (input, out) -> assertEquals(out, NetplayWire.normalize(input), input) }
    }

    @Test fun equivalent_needs_a_non_empty_match() {
        assertTrue(NetplayWire.equivalent("Super Mario Kart (USA)", "super mario kart"))
        assertFalse(NetplayWire.equivalent("Pokemon - FireRed (USA)", "Pokemon - LeafGreen (USA)"))
        assertFalse(NetplayWire.equivalent("(Unl) [b]", "(Unl) [b]"))
    }

    @Test fun game_name_drops_only_the_last_extension() {
        assertEquals("Super Mario Kart (USA)", NetplayWire.gameName("Super Mario Kart (USA).sfc"))
        assertEquals("Final Fantasy VII (Disc 1).bin", NetplayWire.gameName("Final Fantasy VII (Disc 1).bin.cue"))
        assertEquals("noext", NetplayWire.gameName("noext"))
    }

    @Test fun escape_round_trips_spaces_only() {
        assertEquals("Super${esc}Mario${esc}Kart${esc}(USA)", NetplayWire.escape("Super Mario Kart (USA)"))
        assertEquals("Super Mario Kart (USA)", NetplayWire.unescape("Super${esc}Mario${esc}Kart${esc}(USA)"))
    }

    @Test fun discovery_packet_is_byte_exact() {
        val hex = "4e58575a0000000200000000d8905375706572204d6172696f204b617274" + "00".repeat(48) + "77697a617264" + "00".repeat(26)
        val expected = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val bytes = NetplayWire.encodeDiscovery("Super Mario Kart")
        assertEquals(110, bytes.size)
        assertEquals(expected.toList(), bytes.toList())
    }

    @Test fun discovery_decode_checks_size_magic_version_and_link_mode() {
        val ok = NetplayWire.decodeDiscovery(NetplayWire.encodeDiscovery("Super Mario Kart (USA)"))!!
        assertEquals(Discovery(2, 55440, "Super Mario Kart (USA)", "wizard"), ok)
        assertNull(NetplayWire.decodeDiscovery(ByteArray(109)))
        assertNull(NetplayWire.decodeDiscovery(NetplayWire.encodeDiscovery("X", version = 1))?.takeIf { it.version == 2 })
        val bad = NetplayWire.encodeDiscovery("X").also { it[0] = 0 }
        assertNull(NetplayWire.decodeDiscovery(bad))
    }

    @Test fun discovery_name_is_cut_to_63_bytes_on_a_utf8_boundary() {
        val long = "é".repeat(40)                       // 80 bytes in UTF-8
        val decoded = NetplayWire.decodeDiscovery(NetplayWire.encodeDiscovery(long))!!
        assertEquals("é".repeat(31), decoded.game)        // 62 bytes: the 32nd é would cross byte 63
    }

    @Test fun reject_texts_are_the_handhelds() {
        assertEquals("The host is running a\ndifferent netplay version.", NetplayText.reject(RejectReason.VERSION))
        assertEquals("Both devices are hosting.\n\nOne of them must join instead.", NetplayText.reject(RejectReason.ROLE))
        assertEquals("The host refused the connection.", NetplayText.reject(RejectReason.CORE))   // a bare `core`
    }
}
