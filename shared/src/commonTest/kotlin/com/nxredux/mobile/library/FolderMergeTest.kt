package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class FolderMergeTest {
    private val gba = "H/Roms/Game Boy Advance (GBA)"
    private val mgba = "H/Roms/Game Boy Advance (MGBA)"

    private fun source() = FakeRomsSource(
        mapOf(
            "H" to listOf("Roms"), "H/Roms" to listOf("Game Boy Advance (GBA)", "Game Boy Advance (MGBA)"),
            gba to listOf("A.gba", "T.gba", "map.txt"),
            mgba to listOf("K.gba", "T.gba", "map.txt", ".media"), "$mgba/.media" to listOf("K.png", "T.png"),
        ),
        texts = mapOf("$gba/map.txt" to "A.gba\tAdvance\n", "$mgba/map.txt" to "K.gba\tKirby\nT.gba\tTetris\n"),
        bytes = mapOf("$mgba/K.gba" to ByteArray(4), "$mgba/T.gba" to ByteArray(4), "$mgba/.media/K.png" to ByteArray(2), "$mgba/.media/T.png" to ByteArray(2)),
    )

    private fun game(dir: String, file: String) = IndexedGame("home", dir, file, "H/$dir/$file", "H/$dir", file)

    private val index = LibraryIndex(
        LibraryIndex.VERSION, "H", emptyList(), emptyMap(),
        listOf(IndexedSystem("GBA", "Game Boy Advance", folderName = "Game Boy Advance (GBA)", games = listOf(
            game("Roms/Game Boy Advance (GBA)", "A.gba"), game("Roms/Game Boy Advance (GBA)", "T.gba"),
            game("Roms/Game Boy Advance (MGBA)", "K.gba"), game("Roms/Game Boy Advance (MGBA)", "T.gba"),
        ))),
    )

    private fun names(src: FakeRomsSource, dir: String) = kotlinx.coroutines.runBlocking { src.listChildren(ContentRef(dir)).map { it.name } }

    @Test fun a_console_with_games_in_two_folders_is_a_split() {
        assertEquals(listOf(SplitFolders("GBA", "Game Boy Advance (GBA)", listOf("Game Boy Advance (MGBA)"))), splitFolders(index))
    }

    @Test fun the_other_folders_games_and_art_move_in_a_clash_stays_and_the_lines_map() = runTest {
        val src = source()
        val (result, moved) = FolderMerge(src, ContentRef("H")).merge(index, splitFolders(index), LinkedHashMap(), { _, _ -> }) { false }
        assertEquals(MoveResult(moved = 1, clashed = 1), result)
        assertEquals(setOf("A.gba", "T.gba", "map.txt", "K.gba", ".media"), names(src, gba).toSet())
        assertEquals(listOf("K.png"), names(src, "$gba/.media"))
        assertEquals(setOf("T.gba", "map.txt", ".media"), names(src, mgba).toSet())   // the clash keeps its folder
        assertEquals("A.gba\tAdvance\nK.gba\tKirby\n", src.readText(ContentRef("$gba/map.txt")))
        assertEquals(mapOf("/Roms/Game Boy Advance (MGBA)/K.gba" to "/Roms/Game Boy Advance (GBA)/K.gba"), moved)
    }

    // Main's map.txt is there but gives no text: carrying the lines over would replace it with only them, so the lines stay
    // in the other folder (which then keeps its map.txt, and so is not removed).
    @Test fun lines_are_not_carried_into_a_main_map_txt_that_could_not_be_read() = runTest {
        val src = source().apply { unreadable += "$gba/map.txt" }
        FolderMerge(src, ContentRef("H")).merge(index, splitFolders(index), LinkedHashMap(), { _, _ -> }) { false }
        assertEquals("A.gba\tAdvance\n", src.textAt("$gba/map.txt"))
        assertEquals("K.gba\tKirby\nT.gba\tTetris\n", src.textAt("$mgba/map.txt"))
    }

    // A plain subfolder of the other folder (no disc sheet) holding several games moves once, as a folder, and every game
    // in it gets its line.
    @Test fun a_subfolder_of_several_games_moves_once_and_each_game_gets_its_line() = runTest {
        val src = FakeRomsSource(
            mapOf("H" to listOf("Roms"), "H/Roms" to listOf("Game Boy Advance (GBA)", "Game Boy Advance (MGBA)"),
                gba to listOf("A.gba"), mgba to listOf("Hacks"), "$mgba/Hacks" to listOf("a.gba", "b.gba")),
            bytes = mapOf("$mgba/Hacks/a.gba" to ByteArray(4), "$mgba/Hacks/b.gba" to ByteArray(4)),
        )
        val idx = LibraryIndex(LibraryIndex.VERSION, "H", emptyList(), emptyMap(),
            listOf(IndexedSystem("GBA", "Game Boy Advance", folderName = "Game Boy Advance (GBA)", games = listOf(
                game("Roms/Game Boy Advance (GBA)", "A.gba"),
                game("Roms/Game Boy Advance (MGBA)/Hacks", "a.gba"), game("Roms/Game Boy Advance (MGBA)/Hacks", "b.gba"),
            ))))
        val (result, moved) = FolderMerge(src, ContentRef("H")).merge(idx, splitFolders(idx), LinkedHashMap(), { _, _ -> }) { false }
        assertEquals(MoveResult(moved = 2), result)
        assertEquals(setOf("a.gba", "b.gba"), names(src, "$gba/Hacks").toSet())
        assertEquals(mapOf(
            "/Roms/Game Boy Advance (MGBA)/Hacks/a.gba" to "/Roms/Game Boy Advance (GBA)/Hacks/a.gba",
            "/Roms/Game Boy Advance (MGBA)/Hacks/b.gba" to "/Roms/Game Boy Advance (GBA)/Hacks/b.gba",
        ), moved)
    }

    // A folder that still holds a game that could not move is never deleted, even when its listing comes back empty (a
    // SAF delete would take the game with it).
    @Test fun the_other_folder_keeping_a_clash_is_not_removed_even_when_its_listing_comes_back_empty() = runTest {
        val src = source()
        var moves = 0
        var deletes = 0
        val faulty = object : RomsSource by src {
            override suspend fun moveFile(from: ContentRef, name: String, to: ContentRef): Boolean = src.moveFile(from, name, to).also { moves++ }
            override suspend fun listChildren(dir: ContentRef) = if (moves > 0 && dir.value == mgba) emptyList() else src.listChildren(dir)
            override suspend fun deleteEmptyDirectory(parent: ContentRef, name: String): Boolean { deletes++; return src.deleteEmptyDirectory(parent, name) }
        }
        FolderMerge(faulty, ContentRef("H")).merge(index, splitFolders(index), LinkedHashMap(), { _, _ -> }) { false }
        assertEquals(0, deletes)
    }

    @Test fun lines_naming_a_moved_game_follow_it() {
        val moved = mapOf("/Roms/Game Boy Advance (MGBA)/K.gba" to "/Roms/Game Boy Advance (GBA)/K.gba")
        assertEquals(
            listOf("/Roms/Game Boy Advance (GBA)/K.gba", "/Roms/Game Boy Advance (GBA)/K.gba\tKirby", "/Roms/Game Boy (GB)/T.gb"),
            rewriteLines(listOf("/Roms/Game Boy Advance (MGBA)/K.gba", "/Roms/Game Boy Advance (MGBA)/K.gba\tKirby", "/Roms/Game Boy (GB)/T.gb"), moved),
        )
    }

    private val ps = "H/Roms/Sony PlayStation (PS)"
    private val swan = "H/Roms/Sony PlayStation (SWANSTATION)"
    private val cue = "FILE \"G.bin\" BINARY\n TRACK 01 MODE2/2352\n"

    private fun psSource() = FakeRomsSource(
        mapOf(
            "H" to listOf("Roms"), "H/Roms" to listOf("Sony PlayStation (PS)", "Sony PlayStation (SWANSTATION)", "Misc (MISC)"),
            ps to listOf("A.chd"), swan to listOf("G.bin", "G.cue", "readme.txt", "FF7"), "$swan/FF7" to listOf("FF7.m3u", "FF7 (Disc 1).chd"),
            "H/Roms/Misc (MISC)" to listOf("X.chd"),
        ),
        texts = mapOf("$swan/G.cue" to cue, "$swan/FF7/FF7.m3u" to "FF7 (Disc 1).chd\n"),
        bytes = mapOf("$swan/G.bin" to ByteArray(4), "$swan/FF7/FF7 (Disc 1).chd" to ByteArray(4)),
    )

    private val psIndex = LibraryIndex(
        LibraryIndex.VERSION, "H", emptyList(), emptyMap(),
        listOf(IndexedSystem("PS", "Sony PlayStation", folderName = "Sony PlayStation (PS)", games = listOf(
            game("Roms/Sony PlayStation (PS)", "A.chd"), game("Roms/Sony PlayStation (SWANSTATION)", "G.cue"),
            game("Roms/Sony PlayStation (SWANSTATION)/FF7", "FF7.m3u"), game("Roms/Misc (MISC)", "X.chd"),
        ))),
    )

    // C1/I2: a cue moves with its bin whatever the listing order; a file that is no game stays; a disc folder moves whole.
    // I3: a folder that names no console of its own (Misc) is no part of the split.
    @Test fun sets_move_whole_non_games_stay_and_a_consoleless_folder_is_not_merged() = runTest {
        val src = psSource()
        assertEquals(listOf(SplitFolders("PS", "Sony PlayStation (PS)", listOf("Sony PlayStation (SWANSTATION)"))), splitFolders(psIndex))
        val moved = LinkedHashMap<String, String>()
        val (result, _) = FolderMerge(src, ContentRef("H")).merge(psIndex, splitFolders(psIndex), moved, { _, _ -> }) { false }
        assertEquals(MoveResult(moved = 2), result)
        assertEquals(setOf("A.chd", "G.cue", "G.bin", "FF7"), names(src, ps).toSet())
        assertEquals(listOf("readme.txt"), names(src, swan))                          // not a game: stays (and so does its folder)
        assertEquals(listOf("X.chd"), names(src, "H/Roms/Misc (MISC)"))
        assertEquals(setOf("/Roms/Sony PlayStation (SWANSTATION)/G.cue", "/Roms/Sony PlayStation (SWANSTATION)/FF7/FF7.m3u"), moved.keys)
    }

    // M1: a disc folder whose name main already has is a clash, not merged into it.
    @Test fun a_disc_folder_named_like_one_in_main_stays() = runTest {
        val src = psSource()
        kotlinx.coroutines.runBlocking { src.createDirectory(ContentRef(ps), "FF7"); src.writeText(ContentRef("$ps/FF7"), "FF7.cue", "x") }
        val (result, _) = FolderMerge(src, ContentRef("H")).merge(psIndex, splitFolders(psIndex), LinkedHashMap(), { _, _ -> }) { false }
        assertEquals(1, result.clashed)
        assertEquals(listOf("FF7.cue"), names(src, "$ps/FF7"))
    }

    // An empty main folder (one setup made) is no split: the user's only folder stays as it is.
    @Test fun a_console_whose_main_folder_holds_nothing_is_no_split() {
        val only = LibraryIndex(LibraryIndex.VERSION, "H", emptyList(), emptyMap(), listOf(
            IndexedSystem("GBA", "Game Boy Advance", folderName = "Game Boy Advance (GBA)", games = listOf(game("Roms/Game Boy Advance (MGBA)", "K.gba"))),
        ))
        assertEquals(emptyList(), splitFolders(only))
    }

    // I1: what moved before a failure is still handed to the caller, so its lines follow.
    @Test fun moves_made_before_a_failure_are_still_reported() = runTest {
        val src = source()
        val moved = LinkedHashMap<String, String>()
        val failing = object : RomsSource by src {
            override suspend fun child(dir: ContentRef, name: String): Entry? =
                if (name == ".media") throw IllegalStateException("access dropped") else src.child(dir, name)
        }
        assertFailsWith<IllegalStateException> { FolderMerge(failing, ContentRef("H")).merge(index, splitFolders(index), moved, { _, _ -> }) { false } }
        assertEquals(mapOf("/Roms/Game Boy Advance (MGBA)/K.gba" to "/Roms/Game Boy Advance (GBA)/K.gba"), moved)
    }

    /**
     * A cue written in Windows-1252 that names its second track in another case: [FakeRomsSource.readText] gives what a
     * UTF-8 read makes of it (U+FFFD for each Latin-1 byte), its head the bytes as they are.
     */
    private val legacyCue = "FILE \"Pok\u00e9mon (Track 1).bin\" BINARY\n TRACK 01 MODE2/2352\nFILE \"pok\u00e9mon (track 2).BIN\" BINARY\n TRACK 02 AUDIO\n"
    private val legacyCueBytes = ByteArray(legacyCue.length) { legacyCue[it].code.toByte() }
    private val legacyCueUtf8 = legacyCue.replace('\u00e9', '\uFFFD')

    @Test fun a_legacy_cue_merges_with_its_bins() = runTest {
        val bins = listOf("Pok\u00e9mon (Track 1).bin", "Pok\u00e9mon (Track 2).bin")
        val ps = "H/Roms/Sony PlayStation (PS)"
        val other = "H/Roms/Sony PlayStation (PSX)"
        val src = FakeRomsSource(
            mapOf("H" to listOf("Roms"), "H/Roms" to listOf("Sony PlayStation (PS)", "Sony PlayStation (PSX)"), ps to listOf("A.chd"), other to listOf("P.cue") + bins),
            texts = mapOf("$other/P.cue" to legacyCueUtf8),
            bytes = mapOf("$ps/A.chd" to ByteArray(4), "$other/P.cue" to legacyCueBytes, "$other/${bins[0]}" to ByteArray(10), "$other/${bins[1]}" to ByteArray(20)),
        )
        val idx = LibraryIndex(
            LibraryIndex.VERSION, "H", emptyList(), emptyMap(),
            listOf(IndexedSystem("PS", "Sony PlayStation", folderName = "Sony PlayStation (PS)", games = listOf(
                game("Roms/Sony PlayStation (PS)", "A.chd"), game("Roms/Sony PlayStation (PSX)", "P.cue"),
            ))),
        )
        val (result, _) = FolderMerge(src, ContentRef("H")).merge(idx, splitFolders(idx), LinkedHashMap(), { _, _ -> }) { false }
        assertEquals(MoveResult(moved = 1), result)
        assertEquals(setOf("A.chd", "P.cue") + bins, names(src, ps).toSet())
    }
}
