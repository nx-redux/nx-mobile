package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals

/** Local multiplayer: how many players each core's native ports give, and the devices plugged in at launch. */
class CoreRegistryPlayersTest {
    private fun players(tag: String) = CoreRegistry.playersFor(tag, CoreRegistry.forTag(tag))

    @Test fun four_player_cores() {
        listOf("N64", "DC", "FBN", "PCE").forEach { assertEquals(4, players(it), it) }
    }

    @Test fun two_player_cores() {
        listOf("FC", "FDS", "SFC", "SUPA", "MD", "SMS", "SG1000", "SEGACD", "GPGX", "32X", "PS", "A2600", "A7800", "A5200", "COLECO")
            .forEach { assertEquals(2, players(it), it) }
    }

    @Test fun one_player_cores_and_game_gear() {
        listOf("GG", "GB", "GBC", "GBA", "MGBA", "SGB", "NDS", "3DS", "PSP", "LYNX", "NGP", "NGPC", "WSC", "VB", "PKM", "P8", "PRBOOM")
            .forEach { assertEquals(1, players(it), it) }
    }

    @Test fun an_unknown_core_is_one_player() {
        assertEquals(1, CoreRegistry.playersFor("XYZ", null))
    }

    @Test fun every_player_port_gets_the_pad_device() {
        val dc = CoreRegistry.forTag("DC")!!
        assertEquals(mapOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), dc.controllerPortsFor(4))
        assertEquals(mapOf(0 to 1, 1 to 0, 2 to 0, 3 to 0), dc.controllerPortsFor(1))   // the one-player shape
        val ps = CoreRegistry.forTag("PS")!!
        assertEquals(mapOf(0 to CoreDescriptor.PS_DUALSHOCK, 1 to CoreDescriptor.PS_DUALSHOCK), ps.controllerPortsFor(2))
        assertEquals(mapOf(0 to CoreDescriptor.PS_DUALSHOCK), ps.controllerPortsFor(1))
        assertEquals(emptyMap(), CoreRegistry.forTag("A5200")!!.controllerPortsFor(2))   // never touches a5200's ports
        assertEquals(emptyMap(), CoreRegistry.forTag("GBA")!!.controllerPortsFor(1))
    }
}
