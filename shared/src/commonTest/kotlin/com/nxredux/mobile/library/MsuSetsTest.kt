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
        val own = MsuSets.ownFiles("$contra.sfc", contraFiles)
        assertEquals(listOf("$contra.msu", "$contra-1.pcm", "$contra-2.pcm", "$contra-10.pcm"), own.map { it.first })
        // A zero-padded or upper-case track is served under the name the core asks for.
        assertEquals(listOf("Game-1.pcm" to "GAME-01.PCM", "Game.msu1" to "Game.MSU1"), MsuSets.ownFiles("Game.smc", listOf("GAME-01.PCM", "Game.MSU1", "Game (Beta)-1.pcm", "Game-x.pcm")).sortedBy { it.first })
        assertEquals(emptyList(), MsuSets.ownFiles("Game.gba", listOf("Game.msu")))
    }

    @Test fun an_msu_md_cartridge_pairs_with_the_cue_of_its_stem() {
        assertEquals("SoR2.CUE", MsuSets.mdSheet("SoR2.md", listOf("SoR2.md", "SoR2.CUE")))
        assertEquals("SoR2.cue", MsuSets.mdSheet("SoR2.gen", listOf("SoR2.gen", "SoR2.cue")))
        assertNull(MsuSets.mdSheet("SoR2.md", listOf("SoR2.md", "Other.cue")))
        assertNull(MsuSets.mdSheet("SoR2.sfc", listOf("SoR2.cue")))
    }

    @Test fun a_cue_beside_its_cartridge_is_hidden_and_a_lone_cue_is_not() {
        val files = entries("d", listOf("SoR2.md", "SoR2.cue", "Sonic CD.cue"))
        assertEquals(setOf("sor2.cue"), MsuSets.hiddenSheets(files))
    }

    @Test fun a_folder_with_one_rom_that_has_a_set_is_that_rom() {
        assertEquals("$contra.sfc", MsuSets.folderRom(entries("d", contraFiles))?.name)
        assertNull(MsuSets.folderRom(entries("d", listOf("A.sfc", "B.sfc"))))                                 // no set
        assertNull(MsuSets.folderRom(entries("d", listOf("A.sfc", "A.msu", "B.sfc", "B-1.pcm"))))            // two sets
        assertEquals("SoR2.md", MsuSets.folderRom(entries("d", listOf("SoR2.md", "SoR2.cue", "SoR2 (Track 01).bin")))?.name)
    }

    @Test fun a_launch_of_an_msu_md_cartridge_takes_its_cue_and_tracks_and_a_missing_track_is_named() = runTest {
        val src = FakeRomsSource(
            dirs = mapOf("md" to listOf("SoR2.md", "SoR2.cue", "SoR2 (Track 01).bin", "SoR2 (Track 02).bin")),
            texts = mapOf("md/SoR2.cue" to cue),
        )
        val found = MsuSets.companions(src, ContentRef("md"), "SoR2.md") as CompanionLookup.Found
        assertEquals(listOf("SoR2.cue", "SoR2 (Track 01).bin", "SoR2 (Track 02).bin"), found.companions.map { it.fileName })
        assertEquals(listOf("SoR2.cue", "SoR2 (Track 01).bin", "SoR2 (Track 02).bin"), MsuSets.loadedNames(src, ContentRef("md"), "SoR2.md"))

        val partial = FakeRomsSource(dirs = mapOf("md" to listOf("SoR2.md", "SoR2.cue", "SoR2 (Track 01).bin")), texts = mapOf("md/SoR2.cue" to cue))
        assertEquals(CompanionLookup.Missing("SoR2 (Track 02).bin", "SoR2.cue"), MsuSets.companions(partial, ContentRef("md"), "SoR2.md"))
    }

    @Test fun a_rom_without_a_set_is_not_listed_for_one() = runTest {
        val src = CountingRomsSource(FakeRomsSource(dirs = mapOf("sfc" to listOf("Mario.sfc", "Zelda.sfc", "Zelda.msu"))))
        assertEquals(CompanionLookup.Found(emptyList()), MsuSets.companions(src, ContentRef("sfc"), "Mario.sfc"))
        assertTrue(src.calls.none { it.startsWith("listChildren") }, src.calls.toString())
        val zelda = MsuSets.companions(src, ContentRef("sfc"), "Zelda.sfc") as CompanionLookup.Found
        assertEquals(listOf("Zelda.msu"), zelda.companions.map { it.fileName })
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
