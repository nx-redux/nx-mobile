package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `<path>\t<source>\t<game id>\t<asset id>`; old path-only lines still decode. */
class WrittenArtLogTest {
    @Test fun oldLinesDecode() {
        val log = WrittenArtLog.decode("Roms/GB/.media/Tetris.png\n  Roms/GB/.media/screenshot/Tetris.png  \n\nRoms/MD/.media/boxart/Sonic.png\n")
        assertEquals(setOf("Roms/GB/.media/Tetris.png", "Roms/GB/.media/screenshot/Tetris.png", "Roms/MD/.media/boxart/Sonic.png"), log.entries)
        assertNull(log.origin("Roms/GB/.media/Tetris.png"))
        assertEquals(log.entries, WrittenArtLog.decode(log.encode()).entries)
        assertEquals("Roms/GB/.media/Tetris.png\nRoms/GB/.media/screenshot/Tetris.png\nRoms/MD/.media/boxart/Sonic.png\n", log.encode())
    }

    @Test fun originRoundTrips() {
        val log = WrittenArtLog.decode(null)
        val path = "Roms/Game Boy (GB)/.media/box2d/Pokémon - Red Version (USA, Europe).jpg"
        assertTrue(log.add(path, ArtOrigin("screenscraper", "1234", "box-2D/us")))
        assertTrue(log.add("Roms/GB/.media/hero/Tetris.jpg", ArtOrigin("steamgriddb", assetId = "77")))
        assertTrue(log.add("Roms/GB/.media/screenshot/Tetris.png", null))
        val text = log.encode()
        assertTrue("$path\tscreenscraper\t1234\tbox-2D/us\n" in text, text)
        val back = WrittenArtLog.decode(text)
        assertEquals(log.entries, back.entries)
        assertEquals(ArtOrigin("screenscraper", "1234", "box-2D/us"), back.origin(path))
        assertEquals(ArtOrigin("steamgriddb", null, "77"), back.origin("Roms/GB/.media/hero/Tetris.jpg"))
        assertNull(back.origin("Roms/GB/.media/screenshot/Tetris.png"))
        assertEquals(text, back.encode())
    }

    @Test fun addReplacesOrigin() {
        val log = WrittenArtLog.decode(null)
        val p = "Roms/GB/.media/boxart/Tetris.png"
        assertTrue(log.add(p, ArtOrigin("screenscraper", "1", "box-3D/us")))
        assertFalse(log.add(p, ArtOrigin("screenscraper", "1", "box-3D/us")))   // nothing new
        assertTrue(log.add(p, ArtOrigin("screenscraper", "1", "box-3D/eu")))
        assertEquals(ArtOrigin("screenscraper", "1", "box-3D/eu"), log.origin(p))
        assertFalse(log.add(p, null))   // a path-only add keeps the known origin
        assertEquals(ArtOrigin("screenscraper", "1", "box-3D/eu"), log.origin(p))
        assertEquals(setOf(p), log.entries)
        log.remove(listOf(p))
        assertTrue(log.entries.isEmpty()); assertNull(log.origin(p))
    }

    @Test fun anUnreadableLogIsNeverSavedOver() = runTest {
        val files = mutableMapOf("log" to "Roms/GB/.media/box2d/Zelda.png\n")
        var unreadable = true
        val host = object : ArtworkHost {
            override suspend fun libraryGames() = emptyList<LibraryGameRef>()
            override suspend fun allLibraryGames() = emptyList<LibraryGameRef>()
            override suspend fun applyArt(gamePath: String, art: ArtRefs) {}
            override suspend fun readPrivate(name: String): String? = if (unreadable) error("read failed") else files[name]
            override suspend fun writePrivate(name: String, text: String) { files[name] = text }
        }
        val store = ArtLogStore(host, "log")
        val tetris = "Roms/GB/.media/box2d/Tetris.png"
        assertFailsWith<IllegalStateException> { store.add(listOf(tetris to null)) }
        assertEquals("Roms/GB/.media/box2d/Zelda.png\n", files["log"])   // not replaced by a log of Tetris alone
        unreadable = false   // the failed read was not kept: the next call reads the real log
        store.add(listOf(tetris to null))
        assertEquals(setOf("Roms/GB/.media/box2d/Zelda.png", tetris), WrittenArtLog.decode(files["log"]).entries)
        // An absent file is an empty log.
        val fresh = ArtLogStore(host, "other")
        fresh.add(listOf(tetris to null))
        assertEquals("$tetris\n", files["other"])
    }

    @Test fun newEntriesAreAppendedNotRewritten() = runTest {
        val host = FakeHost(emptyList()).apply { appendable = true; files["log"] = "Roms/GB/.media/box2d/Zelda.png\n" }
        val store = ArtLogStore(host, "log")
        val tetris = "Roms/GB/.media/box2d/Tetris.png"
        store.add(listOf(tetris to ArtOrigin("screenscraper", "1", "box-2D/us")))
        store.add(listOf(tetris to ArtOrigin("screenscraper", "1", "box-2D/us")))   // nothing new: nothing written
        store.add(listOf(tetris to ArtOrigin("screenscraper", "1", "box-2D/eu")))   // a new origin: a later line wins
        assertTrue(host.writes.isEmpty()); assertEquals(2, host.appends.size)
        val back = WrittenArtLog.decode(host.files["log"])
        assertEquals(setOf("Roms/GB/.media/box2d/Zelda.png", tetris), back.entries)
        assertEquals(ArtOrigin("screenscraper", "1", "box-2D/eu"), back.origin(tetris))
        assertTrue(store.contains(tetris))
        store.remove(tetris)   // a removal is appended too
        assertTrue(host.writes.isEmpty()); assertEquals(3, host.appends.size)
        assertEquals(setOf("Roms/GB/.media/box2d/Zelda.png"), WrittenArtLog.decode(host.files["log"]).entries)
        assertFalse(ArtLogStore(host, "log").contains(tetris))
    }

    @Test fun aGamesRemovalsAreOneAppend() = runTest {
        val a = "Roms/GB/.media/boxart/Tetris.png"; val b = "Roms/GB/.media/boxart/Tetris.jpg"; val c = "Roms/GB/.media/box2d/Zelda.png"
        val host = FakeHost(emptyList()).apply { appendable = true; files["log"] = "$a\tscreenscraper\t1\tbox\n$b\n$c\n" }
        val store = ArtLogStore(host, "log")
        store.remove(listOf(a, b, "Roms/GB/.media/boxart/Absent.png"))
        assertEquals(1, host.appends.size); assertTrue(host.writes.isEmpty())
        assertEquals(setOf(c), WrittenArtLog.decode(host.files["log"]).entries)
        store.remove(listOf("Roms/GB/.media/boxart/Absent.png"))   // nothing logged: nothing written
        assertEquals(1, host.appends.size)
    }

    @Test fun aPathAddedAgainAfterItsRemovalIsLogged() {
        val log = WrittenArtLog.decode("a.png\tscreenscraper\t1\tbox\n" + WrittenArtLog.removedLine("a.png") + "a.png\tlibretro\t\t\n")
        assertEquals(ArtOrigin("libretro", null, null), log.origin("a.png"))
        assertFalse("b.png" in WrittenArtLog.decode("b.png\n" + WrittenArtLog.removedLine("b.png")))
    }

    @Test fun removalsCountTowardCompaction() = runTest {
        val host = FakeHost(emptyList()).apply { appendable = true }
        val store = ArtLogStore(host, "log")
        val keep = "Roms/GB/.media/box2d/Keep.png"
        store.add(listOf(keep to null))
        var n = 1
        while (host.writes.isEmpty()) {
            val p = "Roms/GB/.media/box2d/G$n.png"
            store.add(listOf(p to null)); store.remove(listOf(p)); n++
        }
        assertTrue(n <= ArtLogStore.COMPACT_MIN, "$n")
        // Compacted: the removed paths and their removal lines are gone (the path just added, if any, stays).
        val lines = host.files.getValue("log").lines().filter { it.isNotEmpty() }
        assertTrue(lines.size <= 2 && lines.first() == keep, lines.toString())
    }

    @Test fun aHostThatCannotAppendRewritesOnRemoval() = runTest {
        val host = FakeHost(emptyList()).apply { files["log"] = "a.png\nb.png\n" }
        val store = ArtLogStore(host, "log")
        store.remove(listOf("a.png", "b.png"))
        assertEquals(listOf("log" to ""), host.writes)
    }

    @Test fun appendedLinesAreCompactedOnceTheyOutnumberTheLog() = runTest {
        val host = FakeHost(emptyList()).apply { appendable = true }
        val store = ArtLogStore(host, "log")
        val p = "Roms/GB/.media/box2d/Tetris.png"
        repeat(ArtLogStore.COMPACT_MIN) { store.add(listOf(p to ArtOrigin("screenscraper", "$it", null))) }
        assertTrue(host.writes.isEmpty())
        store.add(listOf(p to ArtOrigin("screenscraper", "last", null)))
        assertEquals(1, host.writes.size)
        assertEquals("$p\tscreenscraper\tlast\t\n", host.files["log"])
    }

    // The lines earlier sessions appended count too: a long-lived app compacts on its next change, not never.
    @Test fun linesAppendedInEarlierSessionsAreCompactedOnTheNextChange() = runTest {
        val p = "Roms/GB/.media/box2d/Tetris.png"
        val earlier = (0..ArtLogStore.COMPACT_MIN).joinToString("") { WrittenArtLog.line(p, ArtOrigin("screenscraper", "$it", null)) }
        val host = FakeHost(emptyList()).apply { appendable = true; files["log"] = earlier }
        val store = ArtLogStore(host, "log")
        store.add(listOf("Roms/GB/.media/box2d/Zelda.png" to null))
        assertEquals(1, host.writes.size)
        assertEquals("$p\tscreenscraper\t${ArtLogStore.COMPACT_MIN}\t\nRoms/GB/.media/box2d/Zelda.png\n", host.files["log"])
    }

    @Test fun aTornLastLineIsDroppedAndTheNextChangeRewritesTheFile() = runTest {
        val host = FakeHost(emptyList()).apply { appendable = true; files["log"] = "Roms/GB/.media/box2d/Zelda.png\nRoms/GB/.media/box2d/Tetr" }
        val store = ArtLogStore(host, "log")
        assertFalse(store.contains("Roms/GB/.media/box2d/Tetr"))
        store.add(listOf("Roms/GB/.media/box2d/Sonic.png" to null))
        assertEquals("Roms/GB/.media/box2d/Zelda.png\nRoms/GB/.media/box2d/Sonic.png\n", host.files["log"])
        assertEquals(1, host.writes.size); assertTrue(host.appends.isEmpty())
    }

    @Test fun aHostThatCannotAppendGetsTheWholeFile() = runTest {
        val host = FakeHost(emptyList())
        val store = ArtLogStore(host, "log")
        store.add(listOf("a.png" to null)); store.add(listOf("b.png" to null))
        assertEquals(listOf("log" to "a.png\n", "log" to "a.png\nb.png\n"), host.writes)
    }

    @Test fun aPathOnlyLineAfterAnOriginKeepsTheOrigin() {
        val log = WrittenArtLog.decode("a.png\tscreenscraper\t1\tbox\na.png\n")
        assertEquals(ArtOrigin("screenscraper", "1", "box"), log.origin("a.png"))
    }
}
