package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RomMoverTest {
    private val cue = "FILE \"G (Track 1).bin\" BINARY\n TRACK 01 MODE2/2352\nFILE \"G (Track 2).bin\" BINARY\n TRACK 02 AUDIO\n"

    private fun source(home: List<String> = emptyList()) = FakeRomsSource(
        mapOf(
            "H" to listOf("Roms"), "H/Roms" to listOf("Game Boy Advance (GBA)"), "H/Roms/Game Boy Advance (GBA)" to home,
            "x" to listOf("G.cue", "G (Track 1).bin", "G (Track 2).bin", "K.gba", "N.chd"),
        ),
        texts = mapOf("x/G.cue" to cue),
        bytes = mapOf("x/G (Track 1).bin" to ByteArray(10), "x/G (Track 2).bin" to ByteArray(20), "x/K.gba" to ByteArray(4), "x/N.chd" to ByteArray(4)),
    )

    private fun game(file: String, name: String) = IndexedGame("x", "", file, "x/$file", "x", name)

    private val index = LibraryIndex(
        LibraryIndex.VERSION, "H", emptyList(), emptyMap(),
        listOf(
            IndexedSystem("PS", "Sony PlayStation", games = listOf(game("G.cue", "G"))),
            IndexedSystem("GBA", "Game Boy Advance", folderName = "Game Boy Advance (GBA)", games = listOf(game("K.gba", "K"))),
            IndexedSystem("", "Unassigned", unassigned = true, games = listOf(game("N.chd", "N"))),
        ),
    )

    private fun names(src: FakeRomsSource, dir: String) = kotlinx.coroutines.runBlocking { src.listChildren(ContentRef(dir)).map { it.name } }

    @Test fun a_cue_moves_with_its_bins_and_the_unassigned_stay() = runTest {
        val src = source()
        val mover = RomMover(src, ContentRef("H"))
        val items = mover.plan(index, "x")
        assertEquals(listOf("Sony PlayStation (PS)", "Game Boy Advance (GBA)"), items.map { it.targetFolder })
        assertEquals(listOf("G.cue", "G (Track 1).bin", "G (Track 2).bin"), items[0].files)
        val result = mover.run(items, { _, _ -> }) { false }
        assertEquals(MoveResult(moved = 2), result)
        assertEquals(listOf("G.cue", "G (Track 1).bin", "G (Track 2).bin"), names(src, "H/Roms/Sony PlayStation (PS)"))
        assertEquals(listOf("K.gba"), names(src, "H/Roms/Game Boy Advance (GBA)"))
        assertEquals(listOf("N.chd"), names(src, "x"))
    }

    // Review Focus 4: a name already in the target leaves both where they are.
    @Test fun a_clash_leaves_the_game_in_place() = runTest {
        val src = source(home = listOf("K.gba"))
        val mover = RomMover(src, ContentRef("H"))
        val result = mover.run(mover.plan(index, "x"), { _, _ -> }) { false }
        assertEquals(MoveResult(moved = 1, clashed = 1), result)
        assertTrue("K.gba" in names(src, "x"))
    }

    @Test fun without_a_provider_move_it_copies_then_deletes() = runTest {
        val src = source().apply { moveRefused = true }
        val mover = RomMover(src, ContentRef("H"))
        assertEquals(2, mover.run(mover.plan(index, "x"), { _, _ -> }) { false }.moved)
        assertEquals(listOf("N.chd"), names(src, "x"))
    }

    // Review Focus 5: cancel stops between games; the first moved whole, the second untouched.
    @Test fun cancel_stops_after_the_current_game() = runTest {
        val src = source()
        val mover = RomMover(src, ContentRef("H"))
        var done = 0
        val result = mover.run(mover.plan(index, "x"), { d, _ -> done = d }) { done >= 1 }
        assertEquals(MoveResult(moved = 1, cancelled = true), result)
        assertTrue("K.gba" in names(src, "x"))
        assertFalse("G.cue" in names(src, "x"))
    }

    // A move that cannot happen (no target folder, a refused copy) is a failure, not a name clash.
    @Test fun a_refused_move_is_a_failure_not_a_clash() = runTest {
        val src = source().apply { moveRefused = true; readOnlyPaths += "H/Roms/Sony PlayStation (PS)" }
        val mover = RomMover(src, ContentRef("H"))
        assertEquals(MoveResult(moved = 1, failed = 1), mover.run(mover.plan(index, "x"), { _, _ -> }) { false })
        assertTrue("G.cue" in names(src, "x"))
    }

    // Review Focus 5 again: a cancelled coroutine (the app closing) mid-set still moves the set whole.
    @Test fun cancelling_the_job_mid_set_leaves_no_half_set() = runTest {
        val src = source()
        val mover = RomMover(src, ContentRef("H"))
        val items = mover.plan(index, "x")
        val job = launch { mover.run(items, { _, _ -> }) { false } }
        src.onMove = { job.cancel() }                                     // at the cue's first file
        job.join()
        val moved = names(src, "H/Roms/Sony PlayStation (PS)")
        assertTrue(moved.isEmpty() || moved.size == 3, "$moved")
        assertTrue(names(src, "x").none { it.startsWith("G") } || names(src, "x").count { it.startsWith("G") } == 3)
    }

    // A home's outside games: into Roms by console (a disc folder as a folder), and the folders they empty go.
    @Test fun loose_home_files_move_into_roms_disc_folders_whole_and_emptied_folders_go() = runTest {
        val src = FakeRomsSource(
            mapOf(
                "H" to listOf("Roms", "Stuff"), "H/Roms" to listOf("Game Boy Advance (GBA)"), "H/Roms/Game Boy Advance (GBA)" to listOf("Old.gba"),
                "H/Stuff" to listOf("K.gba", "PS"), "H/Stuff/PS" to listOf("G"), "H/Stuff/PS/G" to listOf("G.cue", "G (Track 1).bin", "G (Track 2).bin"),
            ),
            texts = mapOf("H/Stuff/PS/G/G.cue" to cue),
            bytes = mapOf("H/Stuff/K.gba" to ByteArray(4), "H/Stuff/PS/G/G (Track 1).bin" to ByteArray(10), "H/Stuff/PS/G/G (Track 2).bin" to ByteArray(20)),
        )
        val files = LibraryScanner(src).scanOutsideRoms(ContentRef("H"), emptyMap())
        val homeIndex = LibraryIndex(LibraryIndex.VERSION, "H", emptyList(), emptyMap(),
            listOf(IndexedSystem("GBA", "Game Boy Advance", folderName = "Game Boy Advance (GBA)", games = emptyList())))
        val mover = RomMover(src, ContentRef("H"))
        val r = mover.run(mover.planLoose(files, homeIndex), { _, _ -> }) { false }
        mover.pruneEmpty(ContentRef("H"), files.map { it.relativeDir })
        assertEquals(MoveResult(moved = 2), r)
        assertEquals(setOf("Old.gba", "K.gba"), names(src, "H/Roms/Game Boy Advance (GBA)").toSet())
        assertEquals(setOf("G.cue", "G (Track 1).bin", "G (Track 2).bin"), names(src, "H/Roms/Sony PlayStation (PS)/G").toSet())
        assertEquals(listOf("Roms"), names(src, "H"))                     // Stuff, Stuff/PS and Stuff/PS/G emptied: gone
    }

    @Test fun a_loose_file_whose_name_is_taken_stays_with_its_folder() = runTest {
        val src = FakeRomsSource(
            mapOf("H" to listOf("Roms", "Stuff"), "H/Roms" to listOf("Game Boy Advance (GBA)"), "H/Roms/Game Boy Advance (GBA)" to listOf("K.gba"), "H/Stuff" to listOf("K.gba")),
            bytes = mapOf("H/Stuff/K.gba" to ByteArray(4)),
        )
        val files = LibraryScanner(src).scanOutsideRoms(ContentRef("H"), emptyMap())
        val mover = RomMover(src, ContentRef("H"))
        val r = mover.run(mover.planLoose(files, LibraryIndex(LibraryIndex.VERSION, "H", emptyList(), emptyMap(), emptyList())), { _, _ -> }) { false }
        mover.pruneEmpty(ContentRef("H"), files.map { it.relativeDir })
        assertEquals(MoveResult(clashed = 1), r)
        assertEquals(listOf("K.gba"), names(src, "H/Stuff"))
    }

    // I4: a folder is removed only when its parent's listing still shows it: a provider whose listings come back empty
    // (the Fold's Android 17 fault) never gets a delete that would take a full folder with it.
    @Test fun nothing_is_pruned_while_listings_come_back_empty() = runTest {
        val src = FakeRomsSource(mapOf("H" to listOf("Roms", "Stuff"), "H/Stuff" to listOf("K.gba")))
        var deletes = 0
        val counting = object : RomsSource by src {
            override suspend fun deleteEmptyDirectory(parent: ContentRef, name: String): Boolean { deletes++; return src.deleteEmptyDirectory(parent, name) }
        }
        src.listingFault = ListingFault.EMPTY
        RomMover(counting, ContentRef("H")).pruneEmpty(ContentRef("H"), listOf("Stuff"))
        assertEquals(0, deletes)
    }

    // A folder where a game stayed (a clash) is kept even when its listing comes back empty; one whose games all moved goes.
    @Test fun a_folder_where_a_game_stayed_is_never_pruned() = runTest {
        val src = FakeRomsSource(mapOf("H" to listOf("Roms", "Stuff", "Done"), "H/Stuff" to listOf("K.gba"), "H/Done" to emptyList()))
        var deletes = 0
        val faulty = object : RomsSource by src {
            override suspend fun listChildren(dir: ContentRef) = if (dir.value == "H/Stuff") emptyList() else src.listChildren(dir)
            override suspend fun deleteEmptyDirectory(parent: ContentRef, name: String): Boolean { deletes++; return src.deleteEmptyDirectory(parent, name) }
        }
        RomMover(faulty, ContentRef("H")).pruneEmpty(ContentRef("H"), listOf("Stuff", "Done"), keep = listOf("Stuff"))
        assertEquals(1, deletes)                                          // Done only
        assertEquals(listOf("Roms", "Stuff"), src.listChildren(ContentRef("H")).map { it.name })
    }

    /**
     * A cue written in Windows-1252 that names its second track in another case: [FakeRomsSource.readText] gives what a
     * UTF-8 read makes of it (U+FFFD for each Latin-1 byte), its head the bytes as they are.
     */
    private val legacyCue = "FILE \"Pok\u00e9mon (Track 1).bin\" BINARY\n TRACK 01 MODE2/2352\nFILE \"pok\u00e9mon (track 2).BIN\" BINARY\n TRACK 02 AUDIO\n"
    private val legacyCueBytes = ByteArray(legacyCue.length) { legacyCue[it].code.toByte() }
    private val legacyCueUtf8 = legacyCue.replace('\u00e9', '\uFFFD')

    @Test fun a_legacy_cue_moves_with_its_bins_under_their_names_on_disk() = runTest {
        val bins = listOf("Pok\u00e9mon (Track 1).bin", "Pok\u00e9mon (Track 2).bin")
        val src = FakeRomsSource(
            mapOf("H" to listOf("Roms"), "H/Roms" to emptyList(), "x" to listOf("P.cue") + bins),
            texts = mapOf("x/P.cue" to legacyCueUtf8),
            bytes = mapOf("x/P.cue" to legacyCueBytes, "x/${bins[0]}" to ByteArray(10), "x/${bins[1]}" to ByteArray(20)),
        )
        val idx = LibraryIndex(LibraryIndex.VERSION, "H", emptyList(), emptyMap(), listOf(IndexedSystem("PS", "Sony PlayStation", games = listOf(game("P.cue", "P")))))
        val mover = RomMover(src, ContentRef("H"))
        val items = mover.plan(idx, "x")
        assertEquals(listOf("P.cue") + bins, items.single().files)
        assertEquals(MoveResult(moved = 1), mover.run(items, { _, _ -> }) { false })
        assertEquals(listOf("P.cue") + bins, names(src, "H/Roms/Sony PlayStation (PS)"))
    }

    @Test fun a_loose_legacy_cue_moves_with_its_bins() = runTest {
        val bins = listOf("Pok\u00e9mon (Track 1).bin", "Pok\u00e9mon (Track 2).bin")
        val src = FakeRomsSource(
            mapOf("H" to listOf("Roms", "Stuff"), "H/Roms" to emptyList(), "H/Stuff" to listOf("P.cue") + bins),
            texts = mapOf("H/Stuff/P.cue" to legacyCueUtf8),
            bytes = mapOf("H/Stuff/P.cue" to legacyCueBytes, "H/Stuff/${bins[0]}" to ps1Bin(), "H/Stuff/${bins[1]}" to ByteArray(20)),
        )
        val files = LibraryScanner(src).scanOutsideRoms(ContentRef("H"), emptyMap())
        val mover = RomMover(src, ContentRef("H"))
        val items = mover.planLoose(files, LibraryIndex(LibraryIndex.VERSION, "H", emptyList(), emptyMap(), emptyList()))
        assertEquals(listOf("P.cue") + bins, items.single().files)
        assertEquals(MoveResult(moved = 1), mover.run(items, { _, _ -> }) { false })
    }

    private fun ps1Bin(): ByteArray = com.nxredux.mobile.library.disc.DiscProbeTest().ps1BinBytes()
}
