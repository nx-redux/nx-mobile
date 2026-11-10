package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.ensurePath
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.MemoryPrivateFiles
import com.nxredux.mobile.storage.PrivateFiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameInfoStoreTest {
    private val tetris = Scrape.homeGame("Tetris.gb")
    private val info = StoredGameInfo("Blocks fall.", "Puzzle", "1989", "Nintendo", "en", "42", 1_000L)

    private class Rig(val fs: FakeRomsSource = Scrape.homeTree(), val files: PrivateFiles = MemoryPrivateFiles()) {
        val written = mutableListOf<String>()
        val engine = ScrapeEngine(
            FakeHttp(), FakeFitter(), fs, Scrape.home, emptyList(), { _, _ -> }, { w -> written += w.map { it.first } },
            infoFiles = files,
        )
        val store = engine.info!!
    }

    @Test fun write_then_read_round_trips_beside_the_art() = runTest {
        val rig = Rig()
        assertTrue(rig.store.write(tetris, info))
        // The same `<name>` and `.media` folder as the box art.
        val box = ArtPaths.target(tetris.sourceId, tetris.relativeDir, tetris.fileName, tetris.folderGame).path(ArtSlot.BOX_2D, ArtEncoding.PNG)
        assertEquals("Roms/GB/.media/boxart2d/Tetris.png", box)
        assertNotNull(rig.fs.readText(ContentRef("Home/Roms/GB/.media/info/Tetris.json")))
        // Logged before the write, so Reset deletes it as it does the art.
        assertEquals(listOf("Roms/GB/.media/info/Tetris.json"), rig.written)
        assertEquals(info, rig.store.read(tetris))
        // A new process: nothing cached, read from the file.
        assertEquals(info, Rig(rig.fs).store.read(tetris))
    }

    @Test fun unwritable_folder_returns_false_and_reads_null() = runTest {
        val rig = Rig(Scrape.homeTree().apply { writable = false })
        assertFalse(rig.store.write(tetris, info))
        assertNull(rig.store.read(tetris))
    }

    @Test fun an_unreadable_file_reads_null() = runTest {
        val fs = Scrape.homeTree()
        fs.writeText(fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "info"))!!, "Tetris.json", "{not json")
        assertNull(Rig(fs).store.read(tetris))
    }

    @Test fun a_write_replaces_what_was_read() = runTest {
        val rig = Rig()
        assertNull(rig.store.read(tetris))   // absent is cached too
        assertTrue(rig.store.write(tetris, info))
        assertEquals(info, rig.store.read(tetris))
        val newer = info.copy(synopsis = "Lines clear.", fetchedAt = 2_000L)
        assertTrue(rig.store.write(tetris, newer))
        assertEquals(newer, rig.store.read(tetris))
    }

    @Test fun misses_persist_and_clear() = runTest {
        val files = MemoryPrivateFiles()
        val rig = Rig(files = files)
        assertFalse(rig.store.isMiss(tetris.gamePath))
        rig.store.markMiss(tetris.gamePath)
        rig.store.markMiss("/Roms/GB/Zelda.gb")
        assertTrue(rig.store.isMiss(tetris.gamePath))
        assertEquals(setOf(tetris.gamePath, "/Roms/GB/Zelda.gb"), files.files.getValue(GameInfoStore.MISS_FILE).lines().filter { it.isNotEmpty() }.toSet())
        // After a restart.
        val again = Rig(rig.fs, files).store
        assertTrue(again.isMiss(tetris.gamePath))
        again.clearMiss(tetris.gamePath)
        assertFalse(again.isMiss(tetris.gamePath))
        assertTrue(Rig(rig.fs, files).store.isMiss("/Roms/GB/Zelda.gb"))
        assertFalse(Rig(rig.fs, files).store.isMiss(tetris.gamePath))
    }

    @Test fun an_unwritable_miss_list_still_answers_this_run() = runTest {
        val failing = object : PrivateFiles {
            override suspend fun read(name: String): String? = null
            override suspend fun write(name: String, text: String) = error("disk full")
        }
        val store = Rig(files = failing).store
        store.markMiss(tetris.gamePath)
        assertTrue(store.isMiss(tetris.gamePath))
        store.clearMiss(tetris.gamePath)
        assertFalse(store.isMiss(tetris.gamePath))
    }

    /** The ROM and its `.media` moved into a subfolder (a rescan): the info is found where its art is now. */
    @Test fun info_follows_the_art_name_after_a_rescan_move() = runTest {
        val rig = Rig()
        assertTrue(rig.store.write(tetris, info))
        val text = rig.fs.readText(ContentRef("Home/Roms/GB/.media/info/Tetris.json"))!!
        rig.fs.writeText(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", "Puzzle", ".media", "info"))!!, "Tetris.json", text)
        val moved = Scrape.homeGame("Tetris.gb", dir = "Roms/GB/Puzzle")
        assertEquals(info, rig.store.read(moved))
        // Files sharing a `<name>` share the info, as they share the art.
        assertEquals(info, rig.store.read(Scrape.homeGame("Tetris.zip", dir = "Roms/GB/Puzzle")))
        assertEquals("Roms/GB/Puzzle/.media/info/Tetris.json",
            ArtPaths.target(moved.sourceId, moved.relativeDir, moved.fileName, moved.folderGame).infoPath)
    }

    @Test fun an_extra_folder_game_keeps_its_info_in_the_home_mirror() = runTest {
        val rig = Rig()
        val extra = Scrape.extraGame("Sonic.md", tag = "MD")
        assertTrue(rig.store.write(extra, info))
        assertNotNull(rig.fs.readText(ContentRef("Home/Roms/.sources/x1/Genesis/.media/info/Sonic.json")))
        assertEquals(info, Rig(rig.fs).store.read(extra))
    }
}
