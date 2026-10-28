package com.nxredux.mobile.cheats.db

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class CheatDatabaseTest {
    @Test fun the_table_is_the_nx_redux_one() {
        // nx-redux's 35, WS folded into WSC, MSX dropped, MGBA, SUPA and PSX read their console's folder, plus PCECD and
        // BS (route targets, not tags)
        assertEquals(32, CheatDatabase.FOLDERS.size)
        assertEquals(listOf("Nintendo - Game Boy"), CheatDatabase.FOLDERS["GB"])
        assertEquals(listOf("Nintendo - Game Boy"), CheatDatabase.FOLDERS["SGB"])
        assertEquals(listOf("Sony - PlayStation"), CheatDatabase.FOLDERS["PS"])
        assertEquals(listOf("Sony - PlayStation Portable"), CheatDatabase.FOLDERS["PSP"])
        assertEquals(listOf("PrBoom"), CheatDatabase.FOLDERS["PRBOOM"])
        assertEquals(listOf("NEC - PC Engine CD - TurboGrafx-CD"), CheatDatabase.FOLDERS["PCECD"])
        assertEquals(listOf("Nintendo - Satellaview"), CheatDatabase.FOLDERS["BS"])
        for (gone in listOf("WS", "GPGX", "MSX", "MGBA", "SUPA", "PSX")) assertEquals(null, CheatDatabase.FOLDERS[gone])
        assertEquals("https://buildbot.libretro.com/assets/frontend/cheats.zip", CheatDatabase.URL)
        assertEquals(400L shl 20, CheatDatabase.NEEDED_FREE_BYTES)
    }

    @Test fun folders_for_the_registry_tags_only() {
        val folders = CheatDatabase.foldersFor(CoreRegistry.cheatTags)
        // Table order, route targets included (BS, PCECD); GPGX routes to the Sega folders. DC, A7800, LYNX, COLECO and
        // FBN cores ignore codes but take the RAM patches their files hold (nx-redux #261); the cores of VB, A2600, A5200
        // (no system RAM), NGP, NGPC, PKM, P8 and 3DS ignore cheats.
        assertEquals(
            listOf("FC", "FDS", "GB", "SGB", "GBC", "GBA", "SFC", "BS", "N64", "NDS", "SMS", "GG", "MD",
                "32X", "SEGACD", "SG1000", "DC", "PS", "PSP", "PCE", "PCECD", "A7800", "LYNX", "COLECO", "WSC", "FBN", "PRBOOM"),
            folders.keys.toList(),
        )
    }

    @Test fun a_second_cores_tag_installs_its_consoles_folder() {
        assertEquals(listOf("GBA"), CheatDatabase.foldersFor(listOf("MGBA")).keys.toList())
        assertEquals(listOf("SFC", "BS"), CheatDatabase.foldersFor(listOf("SUPA")).keys.toList())
        assertEquals(listOf("PS"), CheatDatabase.foldersFor(listOf("PSX")).keys.toList())
    }

    @Test fun route_targets_come_with_their_tag() {
        assertEquals(listOf("SMS", "GG", "MD", "SEGACD", "SG1000"), CheatDatabase.foldersFor(listOf("GPGX")).keys.toList())
        assertEquals(listOf("PCE", "PCECD"), CheatDatabase.foldersFor(listOf("PCE")).keys.toList())
    }

    @Test fun wsc_takes_both_wonderswan_folders() {   // the Color folder first: it wins a name both hold
        assertEquals(listOf("Bandai - WonderSwan Color", "Bandai - WonderSwan"), CheatDatabase.foldersFor(listOf("WSC"))["WSC"])
    }

    @Test fun unknown_and_lowercase_tags() {
        assertEquals(mapOf("N64" to listOf("Nintendo - Nintendo 64")), CheatDatabase.foldersFor(listOf("n64", "PKM", "NOPE")))
        assertEquals(emptyMap(), CheatDatabase.foldersFor(emptyList()))
    }

    @Test fun dolphin_tags_have_no_database_folder() {   // Dolphin brings its own codes; the tags are skipped, not an error
        assertEquals(emptyMap(), CheatDatabase.foldersFor(listOf("WII", "GC")))
    }

    @Test fun registry_exposes_its_tags() {
        assertEquals(
            setOf("NDS", "N64", "PSP", "GB", "GBC", "GBA", "MGBA", "SGB", "FC", "FDS", "SFC", "SUPA", "GPGX", "MD", "SMS", "GG",
                "SG1000", "SEGACD", "PS", "PSX", "PS2", "NGP", "NGPC", "PCE", "VB", "LYNX", "A2600", "A5200", "A7800", "PKM", "WSC", "P8", "PRBOOM",
                "32X", "COLECO", "FBN", "DC", "3DS", "WII", "GC", "GPGX_SMS", "GPGX_GG", "GPGX_SEGACD", "GPGX_SG1000"),
            CoreRegistry.tags,
        )
    }
}
