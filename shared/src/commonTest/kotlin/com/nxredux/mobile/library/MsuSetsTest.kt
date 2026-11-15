package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.MemorySidecarStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MsuSetsTest {
    private val contra = "Contra_III_-_The_Alien_Wars_(USA)_(MSU1)"
    private val contraFiles = listOf("$contra.sfc", "$contra.msu", "$contra-1.pcm", "$contra-10.pcm", "$contra-2.pcm", "Contra III - The Alien Wars (USA) (MSU1).bml")
    private val cue = "FILE \"SoR2 (Track 01).bin\" BINARY\n  TRACK 01 AUDIO\nFILE \"SoR2 (Track 02).bin\" BINARY\n  TRACK 02 AUDIO\n"

    private fun entries(dir: String, names: List<String>) = names.map { Entry(ContentRef("$dir/$it"), it, isDirectory = false, sizeBytes = 1L) }

    @Test fun an_msu1_roms_files_are_its_data_and_tracks_in_track_order_under_the_names_snes9x_opens() {
        val own = MsuSets.Folder(contraFiles).ownFiles("$contra.sfc")
        assertEquals(listOf("$contra.msu", "$contra-1.pcm", "$contra-2.pcm", "$contra-10.pcm"), own.map { it.first })
        // A zero-padded or upper-case track is served under the name the core asks for.
        val game = MsuSets.Folder(listOf("GAME-01.PCM", "Game.MSU1", "Game (Beta)-1.pcm", "Game-x.pcm"))
        assertEquals(listOf("Game-1.pcm" to "GAME-01.PCM", "Game.msu1" to "Game.MSU1"), game.ownFiles("Game.smc").sortedBy { it.first })
        assertEquals(MsuSets.MSU1, game.kind("Game.smc"))
        assertEquals(emptyList(), MsuSets.Folder(listOf("Game.msu")).ownFiles("Game.gba"))
        assertNull(MsuSets.Folder(listOf("Game.msu")).kind("Game.gba"))
    }

    @Test fun an_msu_md_cartridge_pairs_with_the_cue_of_its_stem_and_a_32x_one_does_not() {
        assertEquals("SoR2.CUE", MsuSets.Folder(listOf("SoR2.md", "SoR2.CUE")).mdSheet("SoR2.md"))
        assertEquals("SoR2.cue", MsuSets.Folder(listOf("SoR2.gen", "SoR2.cue")).mdSheet("SoR2.gen"))
        assertNull(MsuSets.Folder(listOf("SoR2.md", "Other.cue")).mdSheet("SoR2.md"))
        assertNull(MsuSets.Folder(listOf("SoR2.cue")).mdSheet("SoR2.sfc"))
        assertNull(MsuSets.Folder(listOf("Game.32x", "Game.cue")).kind("Game.32x"))
        assertEquals(emptySet(), MsuSets.hiddenSheets(entries("d", listOf("Game.32x", "Game.cue"))))
    }

    @Test fun a_cue_beside_its_cartridge_is_hidden_and_a_lone_cue_is_not() {
        val files = entries("d", listOf("SoR2.md", "SoR2.cue", "Sonic CD.cue"))
        assertEquals(setOf("sor2.cue"), MsuSets.hiddenSheets(files))
    }

    @Test fun a_folder_whose_only_rom_has_a_set_is_that_rom_and_a_folder_of_games_is_not() {
        assertEquals("$contra.sfc", MsuSets.folderRom(entries("d", contraFiles))?.name)
        assertNull(MsuSets.folderRom(entries("d", listOf("A.sfc", "B.sfc"))))                                 // no set
        assertNull(MsuSets.folderRom(entries("d", listOf("A.sfc", "A.msu", "B.sfc", "B-1.pcm"))))            // two sets
        assertNull(MsuSets.folderRom(entries("d", listOf("A.sfc", "A.msu", "B.sfc", "C.smc"))))              // games, one with its set
        assertEquals("SoR2.md", MsuSets.folderRom(entries("d", listOf("SoR2.md", "SoR2.cue", "SoR2 (Track 01).bin")))?.name)
    }

    @Test fun a_launch_of_an_msu_md_cartridge_takes_its_cue_and_tracks_and_a_missing_track_is_named() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("md" to listOf("SoR2.md", "SoR2.CUE", "SoR2 (Track 01).bin", "SoR2 (Track 02).bin")),
            texts = mapOf("md/SoR2.CUE" to cue),
        )
        // The cue in another case is still the set's, served under the name PicoDrive derives.
        val found = MsuSets.companions(src, ContentRef("md"), "SoR2.md", MsuSets.MSU_MD) as CompanionLookup.Found
        assertEquals(listOf("SoR2.cue", "SoR2 (Track 01).bin", "SoR2 (Track 02).bin"), found.companions.map { it.fileName })
        assertEquals(listOf("SoR2.CUE", "SoR2 (Track 01).bin", "SoR2 (Track 02).bin"), MsuSets.loadedNames(src, ContentRef("md"), "SoR2.md", MsuSets.MSU_MD))

        val partial = FakeRomsSource(dirs = mapOf("md" to listOf("SoR2.md", "SoR2.cue", "SoR2 (Track 01).bin")), texts = mapOf("md/SoR2.cue" to cue))
        assertEquals(CompanionLookup.Missing("SoR2 (Track 02).bin", "SoR2.cue"), MsuSets.companions(partial, ContentRef("md"), "SoR2.md", MsuSets.MSU_MD))
    }

    @Test fun only_a_game_badged_as_a_set_lists_its_folder_at_launch() = runTest {
        val src = CountingRomsSource(FakeRomsSource(dirs = mapOf("sfc" to listOf("Mario.sfc", "Zelda.sfc", "Zelda-01.pcm", "Zelda-02.pcm"))))
        assertEquals(CompanionLookup.Found(emptyList()), MsuSets.companions(src, ContentRef("sfc"), "Mario.sfc", null))
        assertEquals(CompanionLookup.Found(emptyList()), MsuSets.companions(src, ContentRef("sfc"), "Mario.sfc", "Hack"))
        assertTrue(src.calls.none { it.startsWith("listChildren") }, src.calls.toString())
        // Tracks only, zero-padded, no .msu: still the set, as the scan found it.
        val zelda = MsuSets.companions(src, ContentRef("sfc"), "Zelda.sfc", MsuSets.MSU1) as CompanionLookup.Found
        assertEquals(listOf("Zelda-1.pcm", "Zelda-2.pcm"), zelda.companions.map { it.fileName })
        assertEquals(listOf("Zelda-01.pcm", "Zelda-02.pcm"), MsuSets.loadedNames(src, ContentRef("sfc"), "Zelda.sfc", MsuSets.MSU1))
    }

    @Test fun an_extra_folder_of_games_with_one_set_loose_in_it_lists_every_game() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to emptyList(), "sd/SNES" to listOf("Games"),
                "sd/SNES/Games" to listOf("Mario.sfc", "Zelda.sfc", "Zelda.msu", "Zelda-1.pcm", "Kirby.sfc")),
        )
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("Home") })
            .scan(ContentRef("Home"), listOf(ExtraFolder("e1", ContentRef("sd/SNES"))), emptyMap())
        val games = index.systems.single { it.tag == "SFC" }.games
        assertEquals(listOf("Kirby.sfc", "Mario.sfc", "Zelda.sfc"), games.map { it.file })
        assertEquals(listOf(null, null, MsuSets.MSU1), games.map { it.badge })
        assertTrue(games.all { it.lookupDir == it.dir })   // each loose in its folder, no folder game
    }

    @Test fun home_msu_packs_are_one_game_each_named_after_their_folder_and_an_msu_md_set_lists_as_its_cartridge() = runTest {
        val sfc = "H/Roms/Super Nintendo ES (SFC)"
        val md = "H/Roms/Sega Genesis (MD)"
        val packFolder = "Contra III - The Alien Wars (USA) (MSU1)"
        val src = FakeRomsSource(
            dirs = mapOf(
                "H" to listOf("Roms"),
                "H/Roms" to listOf("Super Nintendo ES (SFC)", "Sega Genesis (MD)"),
                sfc to listOf("Mario.sfc", "Contra III - The Alien Wars (USA).sfc", packFolder, "Saves"),
                "$sfc/$packFolder" to contraFiles,
                "$sfc/Saves" to listOf("Mario.srm"),
                md to listOf("SoR2.md", "SoR2.cue", "SoR2 (Track 01).bin", "SoR2 (Track 02).bin", "Sonic.md", "Golden Axe (MSU-MD)"),
                "$md/Golden Axe (MSU-MD)" to listOf("Golden Axe (MSU-MD).cue", "Golden Axe (World).md", "Golden Axe (World).cue", "Golden Axe (World) (Track 01).bin"),
            ),
            texts = mapOf("$md/SoR2.cue" to cue),
        )
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") }).scan(ContentRef("H"), emptyList(), emptyMap())
        val snes = index.systems.single { it.tag == "SFC" }.games
        // Beside the plain ROM of the same name, both keep the clean name: the badge tells them apart.
        val byFile = snes.associateBy { it.file }
        assertEquals(setOf("$contra.sfc", "Contra III - The Alien Wars (USA).sfc", "Mario.sfc"), byFile.keys)
        assertEquals("Contra III - The Alien Wars", byFile.getValue("$contra.sfc").name)
        assertEquals("Contra III - The Alien Wars", byFile.getValue("Contra III - The Alien Wars (USA).sfc").name)
        assertEquals("Roms/Super Nintendo ES (SFC)", byFile.getValue("$contra.sfc").lookupDir)
        val genesis = index.systems.single { it.tag == "MD" }.games
        assertEquals(listOf("Golden Axe (World).md", "Sonic.md", "SoR2.md"), genesis.map { it.file }.sortedBy { it.lowercase() })
        // The badge: a pack in its folder or loose, never a plain ROM.
        assertEquals(mapOf("$contra.sfc" to MsuSets.MSU1, "Contra III - The Alien Wars (USA).sfc" to null, "Mario.sfc" to null), snes.associate { it.file to it.badge })
        assertEquals(mapOf("Golden Axe (World).md" to MsuSets.MSU_MD, "Sonic.md" to null, "SoR2.md" to MsuSets.MSU_MD), genesis.associate { it.file to it.badge })
    }

    @Test fun an_extra_msu_pack_folder_is_one_game() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"), "Home/Roms" to emptyList(),
                "sd/SNES" to listOf("Contra (MSU1)"),
                "sd/SNES/Contra (MSU1)" to contraFiles,
            ),
        )
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("Home") })
            .scan(ContentRef("Home"), listOf(ExtraFolder("e1", ContentRef("sd/SNES"))), emptyMap())
        val game = index.systems.single { it.tag == "SFC" }.games.single()
        assertEquals("$contra.sfc", game.file)
        assertEquals("Contra (MSU1)", game.dir)
        assertEquals("", game.lookupDir)
        assertEquals(MsuSets.MSU1, game.badge)
    }
}
