package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.MemorySidecarStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LooseResolverTest {
    private val source = FakeRomsSource(
        dirs = mapOf(
            "sd/ROMs" to listOf("x.gb", "y.gba", "z.nds", "w.zip", "s.7z", "v.iso", "notes.txt", ".media", "Genesis", "Game Boy (GB)", "Old.gb.disabled", "systems.txt"),
            "sd/ROMs/.media" to listOf("x.png"),
            "sd/ROMs/Genesis" to listOf("q.bin", "r.zip", "p.7z"),
            "sd/ROMs/Game Boy (GB)" to listOf("t.gb", "u.gba"),
        ),
        texts = mapOf("sd/ROMs/systems.txt" to "v.iso\tPSP\nt.gb\tGBC\n"),   // t.gb lives in a subfolder: the root sidecar must not touch it
        bytes = mapOf("sd/ROMs/w.zip" to zipHead("Mario.gba"), "sd/ROMs/Genesis/r.zip" to zipHead("readme.txt")),
    )
    private fun resolverOf(source: RomsSource, sidecars: Sidecars) =
        LooseResolver(source, ScanReads(source, sidecars, null), { it.defaultTag })

    private val resolver = resolverOf(source, Sidecars(source, MemorySidecarStore()) { null })

    @Test fun named_root_folders_are_not_walked() = runTest {
        val src = FakeRomsSource(mapOf(
            "H" to listOf("Roms", "Bios", "Games", "Tetris.gb"),
            "H/Roms" to listOf("Game Boy (GB)"), "H/Roms/Game Boy (GB)" to listOf("Zelda.gb"),
            "H/Bios" to listOf("GBA"), "H/Bios/GBA" to listOf("gba_bios.bin"),
            "H/Games" to listOf("Kirby.gba", "Roms"), "H/Games/Roms" to listOf("Mario.gba"),   // only the root's Roms is skipped
        ))
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null })
            .scan(ContentRef("H"), "home-outside", skipTop = setOf("roms", "bios"))
        assertEquals(setOf("Tetris.gb" to "GB", "Kirby.gba" to "GBA", "Mario.gba" to "GBA"), files.map { it.entry.name to it.tag }.toSet())
    }

    @Test fun walks_recursively_and_tags_each_candidate() = runTest {
        val files = resolver.scan(ContentRef("sd/ROMs"), "e1").associate { "${it.relativeDir}|${it.entry.name}" to it.tag }
        assertEquals("GB", files["|x.gb"])
        assertEquals("GBA", files["|y.gba"])
        assertEquals("NDS", files["|z.nds"])
        assertEquals("GBA", files["|w.zip"])   // inner .gba names GBA; unpacked at launch
        assertTrue("|s.7z" in files); assertNull(files["|s.7z"])   // listed (a candidate) but unassigned: nothing places a loose 7z
        assertEquals("MD", files["Genesis|p.7z"])   // alias folder takes any archive
        assertEquals("PSP", files["|v.iso"])           // sidecar
        assertEquals("MD", files["Genesis|q.bin"])       // alias folder accepts .bin
        assertEquals("MD", files["Genesis|r.zip"])       // alias folder accepts zip whatever it holds
        assertEquals("GB", files["Game Boy (GB)|t.gb"])       // not GBC: a sidecar only covers its own folder
        assertEquals("GBA", files["Game Boy (GB)|u.gba"])   // folder can't run it; extension can
        assertTrue("|notes.txt" !in files); assertTrue("|Old.gb.disabled" !in files); assertTrue("|systems.txt" !in files)
        assertTrue(files.keys.none { it.startsWith(".media") })
    }
    // .rom and .pkg are too common to name a console, and a PS1 mds/mdf pair is not a Saturn game: only a console's folder places them.
    @Test fun folder_only_extensions_list_only_in_their_consoles_folder() = runTest {
        val src = FakeRomsSource(dirs = mapOf(
            "sd/Mixed" to listOf("x.rom", "x.pkg", "Crash.mds", "Crash.mdf", "Sega Saturn", "Atari Jaguar", "PS4"),
            "sd/Mixed/Sega Saturn" to listOf("Nights.mds"),
            "sd/Mixed/Atari Jaguar" to listOf("Doom.rom"),
            "sd/Mixed/PS4" to listOf("Bloodborne.pkg"),
        ))
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/Mixed"), "e6")
            .associate { "${it.relativeDir}|${it.entry.name}" to it.tag }
        assertEquals(
            mapOf("|Crash.mdf" to "PS", "Sega Saturn|Nights.mds" to "SATURN", "Atari Jaguar|Doom.rom" to "JAGUAR", "PS4|Bloodborne.pkg" to "PS4"),
            files,
        )
    }
    @Test fun unassigned_files_carry_a_null_tag() = runTest {
        val src = FakeRomsSource(dirs = mapOf("sd/Mixed" to listOf("a.chd", "b.zip")), bytes = mapOf("sd/Mixed/b.zip" to zipHead("readme.txt")))
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/Mixed"), "e2")
        assertEquals(listOf(null, null), files.map { it.tag })
        assertEquals(listOf("", ""), files.map { it.relativeDir })
    }
    @Test fun a_playstation_subfolder_claims_its_discs_and_eboots() = runTest {
        val src = FakeRomsSource(dirs = mapOf(
            "sd/Games" to listOf("My Sony PlayStation", "My Sony Playstation Portable"),
            "sd/Games/My Sony PlayStation" to listOf("Tekken 3.PBP", "Mega Man X4"),
            "sd/Games/My Sony PlayStation/Mega Man X4" to listOf("Mega Man X4.cue", "Mega Man X4.bin"),
            "sd/Games/My Sony Playstation Portable" to listOf("FIFA Street 2.iso"),
        ))
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/Games"), "e5")
            .associate { "${it.relativeDir}|${it.entry.name}" to it.tag }
        assertEquals("PS", files["My Sony PlayStation|Tekken 3.PBP"])
        assertEquals("PS", files["My Sony PlayStation/Mega Man X4|Mega Man X4.cue"])
        assertTrue("My Sony PlayStation/Mega Man X4|Mega Man X4.bin" !in files)   // a disc folder is one game: its sheet
        assertEquals("PSP", files["My Sony Playstation Portable|FIFA Street 2.iso"])
    }
    @Test fun the_root_folder_name_counts_as_an_ancestor() = runTest {
        val src = FakeRomsSource(dirs = mapOf("sd/PSP" to listOf("game.iso")))
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/PSP"), "e3")
        assertEquals("PSP", files.single().tag)
    }
    @Test fun sidecar_falls_back_to_the_store_when_the_tree_has_none() = runTest {
        val src = FakeRomsSource(dirs = mapOf("sd/Mixed" to listOf("a.chd")), writable = false)
        val store = MemorySidecarStore().apply { map["e4||systems.txt"] = "a.chd\tMD\n" }
        val files = resolverOf(src, Sidecars(src, store) { null }).scan(ContentRef("sd/Mixed"), "e4")
        assertEquals("MD", files.single().tag)
    }
    @Test fun a_store_choice_over_an_in_extra_sidecar_takes_effect() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("sd/RO" to listOf("a.chd", "b.chd", "systems.txt")),
            texts = mapOf("sd/RO/systems.txt" to "a.chd\tMD\n"),
            writable = false,
        )
        val store = MemorySidecarStore()
        assertTrue(Sidecars(src, store) { null }.set("e6", ContentRef("sd/RO"), "", "b.chd", "PSP"))
        val files = resolverOf(src, Sidecars(src, store) { null }).scan(ContentRef("sd/RO"), "e6").associate { it.entry.name to it.tag }
        assertEquals(mapOf("a.chd" to "MD", "b.chd" to "PSP"), files)
    }
    @Test fun a_home_copy_retags_a_loose_file_over_the_in_extra_sidecar() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"), "Home/Roms" to listOf(".sources"),
                "Home/Roms/.sources" to listOf("e8"), "Home/Roms/.sources/e8" to listOf("systems.txt"),
                "sd/G" to listOf("Mario.gba", "systems.txt"),
            ),
            texts = mapOf("sd/G/systems.txt" to "Mario.gba\tGBA\n", "Home/Roms/.sources/e8/systems.txt" to "Mario.gba\tMGBA\n"),
        )
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { ContentRef("Home") }).scan(ContentRef("sd/G"), "e8")
        assertEquals("MGBA", files.single().tag)
    }

    @Test fun a_cue_set_shows_once_and_an_m3u_hides_its_discs() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("sd/PSX" to listOf("Crash.cue", "Crash.bin", "FF7.m3u", "FF7 (Disc 1).cue", "FF7 (Disc 1).bin", "FF7 (Disc 2).cue", "FF7 (Disc 2).bin", "Loose.bin")),
            texts = mapOf(
                "sd/PSX/Crash.cue" to "FILE \"Crash.bin\" BINARY\n",
                "sd/PSX/FF7.m3u" to "# discs\nFF7 (Disc 1).cue\n\nFF7 (Disc 2).cue\n",
                "sd/PSX/FF7 (Disc 1).cue" to "FILE \"FF7 (Disc 1).bin\" BINARY\n",
                "sd/PSX/FF7 (Disc 2).cue" to "FILE \"FF7 (Disc 2).bin\" BINARY\n",
            ),
        )
        val counting = CountingRomsSource(src)
        val files = resolverOf(counting, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/PSX"), "e9")
        assertEquals(listOf("Crash.cue" to "PS", "FF7.m3u" to "PS", "Loose.bin" to "PS"), files.map { it.entry.name to it.tag })
        assertEquals(4, counting.calls.count { it.startsWith("readHead:") })   // one read per cue/m3u
    }
    @Test fun a_png_cart_needs_a_pico8_folder_and_a_stray_png_is_not_a_game() = runTest {
        val src = FakeRomsSource(dirs = mapOf(
            "sd/G" to listOf("Pico-8", "cover.png", "Doom", "demo1.lmp"),
            "sd/G/Pico-8" to listOf("celeste.p8.png", "ld45.p8"),
            "sd/G/Doom" to listOf("demo2.lmp", "freedoom2.wad"),
        ))
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/G"), "e10")
            .associate { "${it.relativeDir}|${it.entry.name}" to it.tag }
        assertEquals(
            mapOf("Pico-8|celeste.p8.png" to "P8", "Pico-8|ld45.p8" to "P8", "Doom|demo2.lmp" to "PRBOOM", "Doom|freedoom2.wad" to "PRBOOM"),
            files,
        )   // cover.png and a loose .lmp: no folder says what runs them, so they are not listed (not even Unassigned)
    }

    @Test fun a_disc_folder_in_an_extra_tree_is_one_game() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "sd/PSX" to listOf("Crash", "Two", "Loose.chd"),
                "sd/PSX/Crash" to listOf("Crash.cue", "Crash.bin"),
                "sd/PSX/Two" to listOf("Two.m3u", "Two (Disc 1).chd", "Two (Disc 2).chd"),
            ),
            texts = mapOf("sd/PSX/Crash/Crash.cue" to "FILE \"Crash.bin\" BINARY\n", "sd/PSX/Two/Two.m3u" to "Two (Disc 1).chd\nTwo (Disc 2).chd\n"),
        )
        val counting = CountingRomsSource(src)
        val files = resolverOf(counting, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/PSX"), "e11")
        assertEquals(
            listOf("Crash|Crash.cue" to "PS", "Two|Two.m3u" to "PS", "|Loose.chd" to "PS"),
            files.map { "${it.relativeDir}|${it.entry.name}" to it.tag },
        )
        for (f in files.take(2)) {
            assertEquals(ContentRef("sd/PSX"), f.lookupFolder)   // art and map.txt name from the folder around it
            assertEquals("", f.lookupDir)
        }
        assertEquals(ContentRef("sd/PSX/Crash"), files[0].folder)   // the launch finds the discs here
        assertEquals(ContentRef("sd/PSX"), files[2].lookupFolder)
        assertTrue(counting.calls.none { it.endsWith("Crash.bin") })
    }
    @Test fun a_stale_cue_in_a_disc_folder_still_lists_once() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("sd/PSX" to listOf("Mega Man X4"), "sd/PSX/Mega Man X4" to listOf("Mega Man X4.cue", "Mega Man X4.bin")),
            texts = mapOf("sd/PSX/Mega Man X4/Mega Man X4.cue" to "FILE \"Mega Man X4 (USA).bin\" BINARY\n"),
        )
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/PSX"), "e12")
        assertEquals(listOf("Mega Man X4.cue" to "PS"), files.map { it.entry.name to it.tag })   // the launch reports the missing bin
    }
    @Test fun a_subfolder_without_its_own_sheet_is_walked_as_before() = runTest {
        val src = FakeRomsSource(dirs = mapOf("sd/PSX" to listOf("Misc"), "sd/PSX/Misc" to listOf("a.gb", "b.cue")))
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/PSX"), "e13")
        assertEquals(listOf("Misc|a.gb" to "GB", "Misc|b.cue" to "PS"), files.map { "${it.relativeDir}|${it.entry.name}" to it.tag })
        assertEquals(listOf(ContentRef("sd/PSX/Misc"), ContentRef("sd/PSX/Misc")), files.map { it.lookupFolder })
        assertEquals(listOf("Misc", "Misc"), files.map { it.lookupDir })
    }
    @Test fun a_disc_folder_sheet_matches_its_folder_name_in_any_case() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("sd/PSX" to listOf("Rally"), "sd/PSX/Rally" to listOf("RALLY.CUE", "rally.bin")),
            texts = mapOf("sd/PSX/Rally/RALLY.CUE" to "FILE \"rally.bin\" BINARY\n"),
        )
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/PSX"), "e14")
        assertEquals(listOf("Rally|RALLY.CUE" to "PS"), files.map { "${it.relativeDir}|${it.entry.name}" to it.tag })
    }

    @Test fun a_gdi_lists_once_as_dreamcast_and_no_track_is_unassigned() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("sd/Games" to listOf("Crazy Taxi.gdi", "track01.bin", "track 02.raw", "track03.bin", "Stray.raw")),
            texts = mapOf("sd/Games/Crazy Taxi.gdi" to "3\r\n1 0 4 2352 track01.bin 0\r\n2 756 0 2352 \"track 02.raw\" 0\r\n3 45000 4 2352 track03.bin 0\r\n"),
        )
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/Games"), "e20")
        assertEquals(listOf("Crazy Taxi.gdi" to "DC"), files.map { it.entry.name to it.tag })   // a stray .raw is no core's file
    }

    @Test fun a_folder_with_its_gdi_named_like_it_is_one_dreamcast_game() = runTest {
        val gdi = "1\n1 0 4 2352 track01.bin 0\n"
        val src = FakeRomsSource(
            dirs = mapOf(
                "sd/Games" to listOf("Crazy Taxi", "Tosec", "Two"),
                "sd/Games/Crazy Taxi" to listOf("Crazy Taxi.gdi", "track01.bin", "Other.gdi"),
                "sd/Games/Tosec" to listOf("disc.gdi", "track01.bin", "track02.raw"),
                "sd/Games/Two" to listOf("a.gdi", "b.gdi"),
            ),
            texts = mapOf("sd/Games/Crazy Taxi/Crazy Taxi.gdi" to gdi, "sd/Games/Tosec/disc.gdi" to "2\n1 0 4 2352 track01.bin 0\n2 756 0 2352 track02.raw 0\n"),
        )
        val files = resolverOf(src, Sidecars(src, MemorySidecarStore()) { null }).scan(ContentRef("sd/Games"), "e21")
        assertEquals(
            listOf("Crazy Taxi|Crazy Taxi.gdi" to "DC", "Tosec|disc.gdi" to "DC", "Two|a.gdi" to "DC", "Two|b.gdi" to "DC"),
            files.map { "${it.relativeDir}|${it.entry.name}" to it.tag },
        )
        assertEquals(ContentRef("sd/Games"), files[0].lookupFolder)          // a disc folder: art and name from around it
        assertEquals("", files[0].lookupDir)
        assertEquals(ContentRef("sd/Games/Crazy Taxi"), files[0].folder)
        assertEquals(ContentRef("sd/Games/Tosec"), files[1].lookupFolder)    // disc.gdi: walked as any folder, its tracks hidden
        assertEquals(ContentRef("sd/Games/Two"), files[2].lookupFolder)
    }

    @Test fun a_zip_in_a_folder_that_decides_its_tag_is_never_opened() = runTest {
        val counting = CountingRomsSource(source)
        resolverOf(counting, Sidecars(source, MemorySidecarStore()) { null }).scan(ContentRef("sd/ROMs"), "e1")
        assertEquals(listOf("readHead:sd/ROMs/w.zip"), counting.calls.filter { it.startsWith("readHead:") })   // Genesis/r.zip: the alias folder decides
    }
    @Test fun each_folder_is_listed_once_and_its_sidecar_found_in_that_listing() = runTest {
        val counting = CountingRomsSource(source)
        resolverOf(counting, Sidecars(counting, MemorySidecarStore()) { null }).scan(ContentRef("sd/ROMs"), "e1")
        val lists = counting.calls.filter { it.startsWith("listChildren:") }
        assertEquals(lists.distinct(), lists)
        assertEquals(emptyList(), counting.calls.filter { it.startsWith("child:") })   // systems.txt comes from the listing
        assertEquals(listOf("readText:sd/ROMs/systems.txt"), counting.calls.filter { it.startsWith("readText:") })
    }
}
