package com.nxredux.mobile.library

import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolutionTest {
    private val first: (Console) -> String = { it.defaultTag }
    private val mgbaByDefault: (Console) -> String = { if (it.id == "gba") "MGBA" else it.defaultTag }

    @Test fun sidecar_beats_everything_when_its_tag_is_known() {
        assertEquals("MGBA", Resolution.resolve("x.gba", "MGBA", listOf("Game Boy (GB)"), null, first))
        assertEquals("GBA", Resolution.resolve("x.gba", "FUTURE", listOf("GBA"), null, first))   // unknown tag: fall through
    }
    @Test fun tagged_ancestor_beats_alias_beats_extension() {
        assertEquals("GB", Resolution.resolve("x.gb", null, listOf("Sub", "Game Boy (GB)", "gba"), null, first))
        assertEquals("GBA", Resolution.resolve("x.gba", null, listOf("Sub", "Game Boy Advance", "ROMs"), null, first))
        assertEquals("MGBA", Resolution.resolve("x.gba", null, listOf("gba"), null, mgbaByDefault))
        assertEquals("GBA", Resolution.resolve("x.gba", null, listOf("Downloads", "ROMs"), null, first))
    }
    @Test fun a_tagged_or_aliased_ancestor_that_cannot_run_the_file_is_skipped() {
        // A .gba inside "Game Boy (GB)": gambatte can't run it, so the extension decides.
        assertEquals("GBA", Resolution.resolve("x.gba", null, listOf("Game Boy (GB)"), null, first))
        assertNull(Resolution.resolve("x.txt", null, listOf("Game Boy (GB)"), null, first))
    }
    @Test fun zips_resolve_by_their_inner_name() {
        assertEquals("FC", Resolution.resolve("pack.zip", null, listOf("ROMs"), "Contra.nes", first))
        assertNull(Resolution.resolve("pack.zip", null, listOf("ROMs"), "readme.txt", first))
        assertNull(Resolution.resolve("pack.zip", null, listOf("ROMs"), null, first))
        assertEquals("GB", Resolution.resolve("pack.zip", null, listOf("Game Boy (GB)"), "whatever.bin", first))   // folder tag accepts zip
    }
    @Test fun generic_and_shared_extensions_stay_unassigned() {
        assertNull(Resolution.resolve("game.iso", null, listOf("ROMs"), null, first))
        assertNull(Resolution.resolve("game.chd", null, listOf("ROMs"), null, first))
        assertNull(Resolution.resolve("game.bin", null, listOf("ROMs"), null, first))
        assertEquals("PSP", Resolution.resolve("game.iso", null, listOf("PSP"), null, first))
    }
    @Test fun alias_ancestors_match_inside_longer_folder_names() {
        assertEquals("MD", Resolution.resolve("x.bin", null, listOf("My Sega Genesis"), null, first))
        // The Default emulators pick decides: Genesis on Genesis Plus GX.
        assertEquals("GPGX", Resolution.resolve("x.bin", null, listOf("My Sega Genesis"), null) { it.defaultTagFor(if (it.id == "md") "GPGX" else null) })
        assertEquals("PSP", Resolution.resolve("x.iso", null, listOf("My Sony Playstation Portable"), null, first))
        assertEquals("GBA", Resolution.resolve("FIFA Soccer 07.gba", null, listOf("My Game Boy Advance 2"), null, first))
        // "GBA Manuals" names no console ("gba" is short, so only a whole-name match counts); the unique extension decides.
        assertEquals("GB", Resolution.resolve("x.gb", null, listOf("GBA Manuals"), null, first))
    }
    @Test fun playstation_folders_keep_their_discs_and_eboots() {
        assertEquals("PS", Resolution.resolve("Tekken 3.PBP", null, listOf("My Sony PlayStation"), null, first))   // never PSP
        assertEquals("PS", Resolution.resolve("x.chd", null, listOf("Sony PlayStation (PS)"), null, first))
        assertEquals("PS", Resolution.resolve("Mega Man X4.cue", null, listOf("Mega Man X4", "PSX"), null, first))
        assertNull(Resolution.resolve("x.chd", null, listOf("Commodore 64 (C64)"), null, first))   // unknown console: no claim
    }
    @Test fun candidates_are_files_some_console_could_own() {
        assertTrue(Resolution.isCandidate("a.gba")); assertTrue(Resolution.isCandidate("a.zip")); assertTrue(Resolution.isCandidate("a.iso"))
        assertFalse(Resolution.isCandidate("a.txt")); assertFalse(Resolution.isCandidate("noext"))
        assertNull(CoreRegistry.forTag("ZZZ"))
    }

    @Test fun a_wii_or_gamecube_folder_wins_for_shared_disc_formats() {
        assertEquals("GC", Resolution.resolve("Mario Kart.rvz", null, listOf("Nintendo GameCube (GC)"), null, first))
        assertEquals("GC", Resolution.resolve("Mario Kart.iso", null, listOf("Gamecube (GC)"), null, first))
        assertEquals("WII", Resolution.resolve("NSMB.iso", null, listOf("Nintendo Wii (WII)"), null, first))
        assertEquals("PRBOOM", Resolution.resolve("doom2.wad", null, listOf("ROMs"), null, first))
    }
    @Test fun new_unique_extensions_resolve_loose_files() {
        val expected = mapOf(
            "x.pce" to "PCE", "x.ngc" to "NGPC", "x.ngp" to "NGP", "x.ws" to "WSC", "x.min" to "PKM", "x.a78" to "A7800",
            "x.a26" to "A2600", "x.a52" to "A5200", "x.lnx" to "LYNX", "x.vb" to "VB", "x.wad" to "PRBOOM", "x.p8" to "P8",
            "x.sms" to "SMS", "x.gg" to "GG", "x.sg" to "SG1000", "x.fds" to "FDS", "x.md" to "MD", "x.agb" to "GBA",
            "x.wbfs" to "WII", "x.gcz" to "GC",
        )
        for ((file, tag) in expected) assertEquals(tag, Resolution.resolve(file, null, listOf("ROMs"), null, first), file)
        assertEquals("MD", Resolution.resolve("x.md", null, listOf("ROMs"), null) { if (it.id == "md") "MD" else it.defaultTag })
    }
    @Test fun a_pbp_is_psp_unless_a_playstation_folder_holds_it() {
        assertEquals("PSP", Resolution.resolve("EBOOT.PBP", null, listOf("ROMs"), null, first))
        assertEquals("PSP", Resolution.resolve("Tekken 3.pbp", null, listOf("Sony Playstation Portable (PSP)"), null, first))   // the folder wins
        assertEquals("PSP", Resolution.resolve("Tekken 3.pbp", null, listOf("PSP"), null, first))
        assertEquals("PS", Resolution.resolve("Tekken 3.pbp", null, listOf("Sony PlayStation (PS)"), null, first))
        assertEquals("PS", Resolution.resolve("Tekken 3.pbp", null, listOf("PSX"), null, first))
    }
    @Test fun a_cso_with_no_folder_hint_is_psp_and_a_ps2_folder_still_takes_it() {   // PS2's core runs .cso, but only PSP owns it
        assertEquals("PSP", Resolution.resolve("Crisis Core.cso", null, listOf("ROMs"), null, first))
        assertEquals("PSP", Resolution.resolve("Crisis Core.zip", null, listOf("ROMs"), "Crisis Core.cso", first))
        assertEquals("PS2", Resolution.resolve("Okami.cso", null, listOf("PS2"), null, first))
    }
    @Test fun discs_resolve_by_their_folder_and_stay_unassigned_without_one() {
        assertEquals("SEGACD", Resolution.resolve("Sonic CD.chd", null, listOf("Sega CD"), null, first))
        // A handheld's shared GPGX folder: the name says the console, which runs its default emulator.
        assertEquals("SEGACD", Resolution.resolve("Sonic CD.cue", null, listOf("Sega CD (GPGX)"), null, first))
        assertEquals("PCE", Resolution.resolve("Ys.m3u", null, listOf("PC Engine CD"), null, first))
        assertNull(Resolution.resolve("Ys.m3u", null, listOf("ROMs"), null, first))
        assertNull(Resolution.resolve("Game.ccd", null, listOf("ROMs"), null, first))
    }
    @Test fun core_only_extensions_resolve_by_folder_but_are_not_candidates() {
        assertFalse(Resolution.isCandidate("cart.png"))
        assertEquals("P8", Resolution.resolve("cart.png", null, listOf("Pico-8 (P8)"), null, first))
        assertNull(Resolution.resolve("cart.png", null, listOf("ROMs"), null, first))
        assertEquals("PS", Resolution.resolve("demo.exe", null, listOf("Sony PlayStation (PS)"), null, first))
        assertTrue(Resolution.isCandidate("x.m3u")); assertTrue(Resolution.isCandidate("x.ccd"))
    }
    @Test fun a_tag_whose_core_cannot_run_the_file_falls_back_to_its_consoles_next_tag() {
        assertEquals("GBA", Resolution.resolve("w.zip", null, listOf("ROMs"), "game.gba", first))   // inner .gba names GBA; the archive is unpacked at launch
        assertEquals("GBA", Resolution.resolve("Kirby.zip", null, listOf("Game Boy Advance (GBA)"), "k.gba", first))
        assertEquals("GBA", Resolution.resolve("Mario.gba", null, listOf("Game Boy Advance (GBA)"), null, first))
        assertEquals("GBA", Resolution.resolve("Mario.gba", null, listOf("Game Boy (GB)"), null, first))   // no GB-console tag runs .gba: the extension rule
    }
    @Test fun a_7z_is_placed_only_by_a_folder_or_a_sidecar() {
        assertTrue(Resolution.isCandidate("pack.7z"))
        assertEquals("GBA", Resolution.resolve("pack.7z", null, listOf("Game Boy Advance (GBA)"), null, first))
        assertEquals("MGBA", Resolution.resolve("pack.7z", "MGBA", listOf("ROMs"), null, first))
        assertEquals("MD", Resolution.resolve("pack.7z", null, listOf("Genesis"), null, first))   // alias folder
        assertNull(Resolution.resolve("pack.7z", null, listOf("ROMs"), null, first))
        assertNull(Resolution.resolve("pack.7z", null, listOf("ROMs"), "Contra.nes", first))   // an inner name is never used for 7z
    }
    @Test fun an_extra_folder_named_arcade_takes_a_set_zip_and_neogeo_names_no_console() {
        assertEquals("FBN", Resolution.resolve("mslug.zip", null, listOf("Arcade"), "201-p1.p1", first))
        assertEquals("FBN", Resolution.resolve("mslug.7z", null, listOf("Arcade"), null, first))
        // "neogeo" is a BIOS set name, not a console alias: a zip under it resolves only by its inner name, never to FBN.
        assertNull(CoreRegistry.consoleForFolderName("neogeo"))
        assertNull(Resolution.resolve("mslug.zip", null, listOf("neogeo"), "201-p1.p1", first))
        assertNull(Resolution.resolve("mslug.7z", null, listOf("neogeo"), null, first))
    }
    @Test fun any_tagged_folder_takes_an_archive_whatever_its_core_lists() {
        assertEquals("A2600", Resolution.resolve("pack.zip", null, listOf("Atari 2600 (A2600)"), "game.a26", first))
        assertEquals("PCE", Resolution.resolve("pack.7z", null, listOf("TurboGrafx-16 (PCE)"), null, first))
        assertEquals("A2600", Resolution.resolve("pack.zip", null, listOf("Atari 2600 (A2600)"), "whatever.txt", first))   // checked at launch, not here
    }

    @Test fun a_sidecar_tag_of_another_console_is_ignored_and_the_handhelds_gpgx_maps_to_the_consoles() {
        assertEquals("GPGX_SMS", Resolution.resolve("x.sms", "GPGX", listOf("Sega Master System (SMS)"), null, first))
        assertEquals("GPGX", Resolution.resolve("x.md", "GPGX", listOf("Sega Genesis"), null, first))
        assertEquals("SMS", Resolution.resolve("x.sms", "MD", listOf("Sega Master System (SMS)"), null, first))   // Genesis's: ignored
        // The folder's console takes nothing: the Console menu's pick for an Unassigned game still places it.
        assertEquals("PSP", Resolution.resolve("x.iso", "PSP", listOf("Game Boy (GB)"), null, first))
        assertEquals("MD", Resolution.resolve("x.md", "MD", listOf("ROMs"), null, first))   // no console folder: any known tag
    }
    @Test fun a_tagged_folder_names_only_its_console() {
        assertEquals("GBA", Resolution.resolve("x.gba", null, listOf("Game Boy Advance (MGBA)"), null, first))
        assertEquals("MGBA", Resolution.resolve("x.gba", null, listOf("Game Boy Advance (GBA)"), null, mgbaByDefault))
        assertEquals("MD", Resolution.resolve("x.md", null, listOf("Sega Genesis (GPGX)"), null, first))
        assertEquals(listOf("GBA"), Resolution.FolderHints.of(listOf("Game Boy Advance (MGBA)", "Game Boy Advance (GBA)"), first).tags)
    }
    @Test fun folder_hints_are_worked_out_once_and_give_what_the_ancestors_give() {
        val hints = Resolution.FolderHints.of(listOf("Sub", "Game Boy (GB)", "gba"), first)
        assertEquals(listOf("GB", "GBA"), hints.tags)   // tagged ancestors first, then aliased ones as their default tag
        assertEquals("GB", Resolution.resolve("x.gb", null, hints, first) { error("not a zip") })
        assertEquals(Resolution.resolve("x.gba", null, listOf("Sub", "Game Boy (GB)", "gba"), null, first), Resolution.resolve("x.gba", null, hints, first) { null })
    }
    @Test fun a_zip_is_peeked_only_when_no_folder_or_sidecar_places_it() {
        var peeks = 0
        val tagged = Resolution.FolderHints.of(listOf("Game Boy (GB)"), first)
        assertEquals("GB", Resolution.resolve("pack.zip", null, tagged, first) { peeks++; "Contra.nes" })
        assertEquals("MGBA", Resolution.resolve("pack.zip", "MGBA", Resolution.FolderHints.of(listOf("ROMs"), first), first) { peeks++; "Contra.nes" })
        assertEquals(0, peeks)
        assertEquals("FC", Resolution.resolve("pack.zip", null, Resolution.FolderHints.of(listOf("ROMs"), first), first) { peeks++; "Contra.nes" })
        assertNull(Resolution.resolve("pack.7z", null, Resolution.FolderHints.of(listOf("ROMs"), first), first) { peeks++; "Contra.nes" })
        assertEquals(1, peeks)   // only the untagged zip; a 7z is never peeked
    }
}
