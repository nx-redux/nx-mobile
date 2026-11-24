package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RaConsolesTest {
    @Test fun every_registry_tag_maps_or_is_deliberately_unmapped() {
        val expected = mapOf(
            "GB" to 4, "GBC" to 6, "GBA" to 5, "MGBA" to 5, "SGB" to 4, "FC" to 7, "FDS" to 81, "SFC" to 3, "SUPA" to 3,
            "NDS" to 18, "N64" to 2, "PS" to 12, "PSX" to 12, "PSP" to 41, "MD" to 1, "GPGX" to 1, "SMS" to 11, "GG" to 15, "SG1000" to 33,
            "SEGACD" to 9, "NGP" to 14, "NGPC" to 14, "PCE" to 8, "VB" to 28, "LYNX" to 13, "A2600" to 25, "A5200" to 50,
            "A7800" to 51, "PKM" to 24, "WSC" to 53, "32X" to 10, "COLECO" to 44, "FBN" to 27, "DC" to 40,
            "WII" to 19, "GC" to 16, "PS2" to 21,
        )
        // Dreamcast is identified by its disc extensions only (dreamcast_discs_are_console_40).
        for ((tag, id) in expected) assertEquals(id, RaConsoles.consoleId(tag, if (tag == "DC") "game.chd" else "game.bin"), tag)
        assertNull(RaConsoles.consoleId("P8", "x.p8"))
        assertNull(RaConsoles.consoleId("PRBOOM", "x.wad"))
        assertNull(RaConsoles.consoleId("nope", "x.bin"))
    }

    @Test fun external_only_consoles_are_shown_with_their_ids() {
        assertEquals(39, RaConsoles.consoleId("SATURN", "Nights.cue"))
        assertEquals(17, RaConsoles.consoleId("JAGUAR", "Tempest.j64"))
        assertEquals(56, RaConsoles.consoleId("NEOCD", "Metal Slug.chd"))
        assertEquals(8, RaConsoles.consoleId("SGX", "Ghouls.sgx"))
        assertEquals("SATURN", RaConsoles.tagOf(39))
    }

    // rcheevos has no hasher for CD-i or for these consoles' files.
    @Test fun cdi_and_modern_external_consoles_have_no_rcheevos_hasher() {
        for (tag in listOf("CDI", "SWITCH", "WIIU", "PS3", "PSVITA", "PS4", "XBOX", "XBOX360", "N64DD"))
            assertNull(RaConsoles.consoleId(tag, "game.iso"), tag)
    }

    @Test fun unlocks_note_shows_for_external_only_or_a_pick() {
        assertEquals(RaConsoles.UNLOCKS_ONLY_HERE, RaConsoles.unlocksNote(externalOnly = true, pickedExternal = false))
        assertEquals(RaConsoles.UNLOCKS_ONLY_HERE, RaConsoles.unlocksNote(externalOnly = false, pickedExternal = true))
        assertNull(RaConsoles.unlocksNote(externalOnly = false, pickedExternal = false))
    }

    @Test fun mega_drive_tags_refine_by_extension() {
        assertEquals(11, RaConsoles.consoleId("MD", "Sonic.sms"))
        assertEquals(15, RaConsoles.consoleId("GPGX", "Sonic.GG"))
        assertEquals(33, RaConsoles.consoleId("MD", "x.sg"))
        for (ext in listOf("cue", "chd", "iso", "m3u")) assertEquals(9, RaConsoles.consoleId("MD", "Sonic CD.$ext"), ext)
        assertEquals(1, RaConsoles.consoleId("MD", "Sonic.md"))
        assertEquals(1, RaConsoles.consoleId("MD", "Sonic.zip"))
    }

    @Test fun the_refinement_follows_the_file_name_it_is_given() {
        // An archive is refined by its unpacked ROM's name; the archive's own name keeps the tag's console.
        assertEquals(RaConsoles.MASTER_SYSTEM, RaConsoles.consoleId("GPGX", "game.sms"))
        assertEquals(RaConsoles.MEGA_DRIVE, RaConsoles.consoleId("GPGX", "game.zip"))
        assertEquals(RaConsoles.MEGA_DRIVE, RaConsoles.consoleId("GPGX", "Sonic.zip"))   // an archive stays a candidate
    }

    @Test fun pc_engine_disc_extensions_become_pc_engine_cd() {
        for (ext in listOf("chd", "cue", "ccd", "toc", "m3u")) assertEquals(76, RaConsoles.consoleId("PCE", "Rondo.$ext"), ext)
        assertEquals(8, RaConsoles.consoleId("PCE", "Bonk.pce"))
    }

    @Test fun dreamcast_discs_are_console_40() {
        for (ext in listOf("chd", "gdi", "cue", "m3u", "CHD")) assertEquals(40, RaConsoles.consoleId("DC", "Game.$ext"), ext)
        assertEquals(RaConsoles.DREAMCAST, RaConsoles.consoleId("dc", "Game.gdi"))
    }

    @Test fun dreamcast_cdi_and_archives_are_unidentified() {
        // rcheevos has no CDI reader; a NAOMI/Atomiswave set is not on RetroAchievements, and a zipped disc is not prefetched.
        assertNull(RaConsoles.consoleId("DC", "Game.cdi"))
        assertNull(RaConsoles.consoleId("DC", "mvsc2.zip"))
        assertNull(RaConsoles.consoleId("DC", "Game.7z"))
        assertNull(RaConsoles.consoleId("DC", "Game"))
    }

}
