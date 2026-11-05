package com.nxredux.mobile.library

import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.runTest

class LibraryIndexCodecTest {
    private val index = LibraryIndex(
        version = LibraryIndex.VERSION,
        home = "content://com.android.externalstorage.documents/tree/primary%3ANXRedux",
        extras = listOf(IndexedExtra("ROMs_Backup-116ad198", "content://com.android.externalstorage.documents/tree/primary%3AROMs")),
        defaultTags = mapOf("gba" to "MGBA"),
        offline = listOf("ROMs_Backup-116ad198"),
        lateMount = listOf("ROMs_Backup-116ad198"),
        systems = listOf(
            IndexedSystem(
                tag = "GB", displayName = "Game Boy", folderName = "Game Boy (GB)",
                art = "Home/Roms/.media/Game Boy (GB).png", bg = "Home/Roms/Game Boy (GB)/.media/bg.png",
                games = listOf(
                    IndexedGame(
                        "home", "Roms/Game Boy (GB)", "Tetris.gb", "Home/Roms/Game Boy (GB)/Tetris.gb", "Home/Roms/Game Boy (GB)", "Tetris",
                        boxart = "b.png", box2d = "c.jpg", screenshot = "s.png", heroShot = true, subtitle = "My GB",
                    ),
                ),
            ),
            IndexedSystem(tag = "MGBA", displayName = "Game Boy Advance", folderName = "Game Boy Advance (MGBA)", games = emptyList()),
            IndexedSystem(
                tag = "PS", displayName = "PlayStation", folderName = "PlayStation (PS)",
                games = listOf(
                    IndexedGame(
                        "home", "Roms/PlayStation (PS)/FF7", "FF7.m3u", "Home/Roms/PlayStation (PS)/FF7/FF7.m3u", "Home/Roms/PlayStation (PS)/FF7", "FF7",
                        lookupDir = "Roms/PlayStation (PS)",
                    ),
                ),
            ),
            IndexedSystem(
                tag = "", displayName = "Unassigned", unassigned = true,
                games = listOf(IndexedGame("ROMs_Backup-116ad198", "日本/\"quoted\"", "tab\tname.chd", "r", "f", "tab\tname")),
            ),
        ),
    )

    @Test fun an_index_round_trips() {
        val decoded = LibraryIndexCodec.decode(LibraryIndexCodec.encode(index))
        assertEquals(index, decoded)
        assertEquals("Roms/PlayStation (PS)", decoded!!.systems[2].games.single().lookupDir)   // a disc folder keeps its lookup dir
        assertEquals("Roms/Game Boy (GB)", decoded.systems[0].games.single().lookupDir)        // defaulted: the game's own dir
        assertEquals("My GB", decoded.systems[0].games.single().subtitle)        // a copy elsewhere: its folder is kept
    }

    @Test fun oldIndexWithArtDecodes() {
        // An index with an old game's untyped mix as "art" (before the typed slots), which is ignored.
        val text = """{"version":${LibraryIndex.VERSION},"home":"H","extras":[],"defaultTags":{},"systems":[{"tag":"GB","displayName":"Game Boy","games":[""" +
            """{"source":"home","dir":"Roms/Game Boy (GB)","file":"Tetris.gb","ref":"r","folder":"f","name":"Tetris","art":"x","boxart":"b.png","screenshot":"s.png"}]}]}"""
        val game = assertNotNull(LibraryIndexCodec.decode(text)).systems.single().games.single()
        assertEquals(IndexedGame("home", "Roms/Game Boy (GB)", "Tetris.gb", "r", "f", "Tetris", boxart = "b.png", screenshot = "s.png"), game)
    }

    @Test fun garbage_decodes_to_null() {
        val bad = listOf(
            "", "{", "not json", "[]", "{\"version\":1}",
            "{\"version\":\"one\",\"home\":\"H\",\"extras\":[],\"defaultTags\":{},\"systems\":[]}",
        )
        for (text in bad) assertNull(LibraryIndexCodec.decode(text), text)
    }

    @Test fun another_version_decodes_to_null() {
        assertEquals(12, LibraryIndex.VERSION)
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = LibraryIndex.VERSION + 1))))
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = 0))))
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = 3))))   // before archives listed
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = 4))))   // before offline extras were recorded
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = 5))))   // before 32X, COLECO and FBN
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = 6))))   // before names dropped bracketed text
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = 8))))   // before copies in two folders kept clean names
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = 9))))   // before wheel, mix and boxart2d/ (an extra's own art unknown)
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = 10))))   // before hidden copies kept their paths
    }

    @Test fun a_version_2_text_does_not_decode() {
        val v2 = LibraryIndexCodec.encode(index).replace("\"version\":${LibraryIndex.VERSION}", "\"version\":2")
        assertNull(LibraryIndexCodec.decode(v2))
    }

    @Test fun a_version_1_index_decodes_to_null() {
        // Written before the cores expansion: its lists never offered .pce/.ngp/... and kept coreless PS files unfiltered.
        assertNull(LibraryIndexCodec.decode("{\"version\":1,\"home\":\"H\",\"extras\":[],\"defaultTags\":{},\"systems\":[]}"))
        assertNull(LibraryIndexCodec.decode(LibraryIndexCodec.encode(index.copy(version = 1))))
    }

    @Test fun unknown_fields_from_a_newer_build_are_ignored() {
        val text = LibraryIndexCodec.encode(index).replaceFirst("{", "{\"future\":true,")
        assertEquals(index, LibraryIndexCodec.decode(text))
    }

    @Test fun library_order_is_by_name_then_tag_with_unassigned_last() {
        assertEquals(listOf("GB", "MGBA", "PS", ""), index.systems.reversed().inLibraryOrder().map { it.tag })
        val games = listOf(IndexedGame("home", "d", "b.gb", "r", "f", "beta"), IndexedGame("home", "d", "a.gb", "r", "f", "Alpha"))
        assertEquals(listOf("Alpha", "beta"), games.inNameOrder().map { it.name })
    }

    @Test fun the_suspend_forms_run_on_the_given_context_and_round_trip() = runTest {
        var dispatched = 0
        val compute = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { dispatched++; Dispatchers.Default.dispatch(context, block) }
        }
        val text = LibraryIndexCodec.encode(index, compute)
        assertEquals(LibraryIndexCodec.encode(index), text)
        assertEquals(index, LibraryIndexCodec.decode(text, compute))
        assertEquals(2, dispatched)
    }
}
