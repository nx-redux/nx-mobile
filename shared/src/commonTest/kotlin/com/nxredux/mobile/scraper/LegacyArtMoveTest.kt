package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LegacyArtMoveTest {
    private val media = "Home/Roms/GB/.media"
    private val segments = listOf("Roms", "GB", ".media")
    private val sgdbGrid = ArtOrigin(SourceId.SGDB, "7", "11")
    private val sgdbHero = ArtOrigin(SourceId.SGDB, "7", "12")

    private class Rig(dirs: Map<String, List<String>>) {
        val src = FakeRomsSource(dirs)
        val host = FakeHost(emptyList())
        val log = ArtLogStore(host, ArtworkController.LOG_FILE)
        val move = LegacyArtMove(src, log, host)
    }

    private suspend fun Rig.run(at: String = "Home/Roms/GB/.media", homeSegments: List<String>? = listOf("Roms", "GB", ".media")): Boolean =
        move.move(ContentRef(at), src.listChildren(ContentRef(at)), homeSegments)

    private fun Rig.marker(): String? = host.files[LegacyArtMove.MARKER_FILE]

    @Test fun movesIntoEmptyTargets() = runTest {
        val r = Rig(mapOf(media to listOf("grid", "hero"), "$media/grid" to listOf("A.jpg"), "$media/hero" to listOf("A.jpg")))
        r.log.add(listOf("Roms/GB/.media/grid/A.jpg" to sgdbGrid, "Roms/GB/.media/hero/A.jpg" to sgdbHero))
        assertTrue(r.run())
        assertEquals(listOf("A.jpg"), r.src.childNames("$media/boxart2d"))
        assertEquals(listOf("A.jpg"), r.src.childNames("$media/screenshot"))
        assertNull(r.src.childNames("$media/grid"))
        assertNull(r.src.childNames("$media/hero"))
        assertFalse(r.src.listChildren(ContentRef(media)).any { it.name == "grid" || it.name == "hero" })
        assertEquals(sgdbGrid, r.log.origin("Roms/GB/.media/boxart2d/A.jpg"))
        assertEquals(sgdbHero, r.log.origin("Roms/GB/.media/screenshot/A.jpg"))
        assertFalse(r.log.contains("Roms/GB/.media/grid/A.jpg"))
        assertFalse(r.log.contains("Roms/GB/.media/hero/A.jpg"))
        assertEquals("$media\n", r.marker())
    }

    @Test fun targetTakenLoggedDeleted() = runTest {
        val r = Rig(mapOf(media to listOf("grid", "boxart2d"), "$media/grid" to listOf("A.jpg"), "$media/boxart2d" to listOf("A.png")))
        r.log.add(listOf("Roms/GB/.media/grid/A.jpg" to sgdbGrid))
        assertTrue(r.run())
        assertEquals(listOf("A.png"), r.src.childNames("$media/boxart2d"))
        assertNull(r.src.childNames("$media/grid"))   // emptied, removed
        assertFalse(r.log.contains("Roms/GB/.media/grid/A.jpg"))
        assertFalse(r.log.contains("Roms/GB/.media/boxart2d/A.jpg"))
        assertEquals("$media\n", r.marker())
    }

    @Test fun legacyBox2dCountsAsTaken() = runTest {
        // A hand-placed legacy box2d/ cover and two grids for its game: the logged grid goes, a hand-placed one stays.
        val r = Rig(mapOf(media to listOf("grid", "box2d"), "$media/grid" to listOf("A.jpg", "B.png", "C.png"), "$media/box2d" to listOf("A.PNG", "B.Jpeg")))
        r.log.add(listOf("Roms/GB/.media/grid/A.jpg" to sgdbGrid, "Roms/GB/.media/grid/C.png" to sgdbGrid))
        assertTrue(r.run())
        assertEquals(listOf("C.png"), r.src.childNames("$media/boxart2d"))   // no box2d/ cover: moved
        assertEquals(listOf("B.png"), r.src.childNames("$media/grid"))   // hand-placed: stays
        assertEquals(listOf("A.PNG", "B.Jpeg"), r.src.childNames("$media/box2d"))
        assertFalse(r.log.contains("Roms/GB/.media/grid/A.jpg"))
        assertFalse(r.log.contains("Roms/GB/.media/boxart2d/A.jpg"))
        assertEquals("$media\n", r.marker())
    }

    @Test fun aHandPlacedHeroIsListedALoggedOneIsNot() = runTest {
        val r = Rig(mapOf(media to listOf("hero"), "$media/hero" to listOf("A.jpg", "B.jpg")))
        r.log.add(listOf("Roms/GB/.media/hero/B.jpg" to sgdbHero))
        assertTrue(r.run())
        assertEquals(listOf("A.jpg", "B.jpg"), r.src.childNames("$media/screenshot"))
        assertEquals("Roms/GB/.media/screenshot/A.jpg\n", r.host.files[MovedHeroes.FILE])   // never logged: Reset keeps it
        assertFalse(r.log.contains("Roms/GB/.media/screenshot/A.jpg"))
        assertEquals(sgdbHero, r.log.origin("Roms/GB/.media/screenshot/B.jpg"))
    }

    @Test fun aHeroWhoseMoveFailedIsNotListed() = runTest {
        val r = Rig(mapOf(media to listOf("hero"), "$media/hero" to listOf("A.jpg")))
        r.src.moveRefused = true
        assertFalse(r.run())
        assertEquals("", r.host.files[MovedHeroes.FILE].orEmpty())
    }

    @Test fun targetTakenHandPlacedKept() = runTest {
        val r = Rig(mapOf(media to listOf("grid", "boxart2d"), "$media/grid" to listOf("A.jpg"), "$media/boxart2d" to listOf("A.png")))
        r.run()
        assertEquals(listOf("A.jpg"), r.src.childNames("$media/grid"))
        assertEquals(listOf("A.png"), r.src.childNames("$media/boxart2d"))
        assertEquals("$media\n", r.marker())
        // Marked: the kept file is never looked at again.
        r.src.listings = 0
        assertFalse(r.run())
        assertEquals(1, r.src.listings)   // only the test's own listing of .media
    }

    @Test fun extraOwnMediaMoves() = runTest {
        val own = "sd/Ext/GB/.media"
        val r = Rig(mapOf(own to listOf("grid"), "$own/grid" to listOf("A.png")))
        assertTrue(r.run(own, homeSegments = null))
        assertEquals(listOf("A.png"), r.src.childNames("$own/boxart2d"))
        assertNull(r.src.childNames("$own/grid"))
        assertNull(r.host.files[ArtworkController.LOG_FILE])   // nothing logged
        assertEquals("$own\n", r.marker())
    }

    @Test fun readOnlyExtraUntouched() = runTest {
        val own = "sd/Ext/GB/.media"
        val r = Rig(mapOf(own to listOf("grid"), "$own/grid" to listOf("A.png")))
        r.src.writable = false
        assertFalse(r.run(own, homeSegments = null))
        assertEquals(listOf("A.png"), r.src.childNames("$own/grid"))
        assertNull(r.marker())
    }

    @Test fun failedMoveNoMarker() = runTest {
        val r = Rig(mapOf(media to listOf("hero"), "$media/hero" to listOf("A.jpg")))
        r.log.add(listOf("Roms/GB/.media/hero/A.jpg" to sgdbHero))
        r.src.moveRefused = true
        assertFalse(r.run())
        assertNull(r.marker())
        assertEquals(listOf("A.jpg"), r.src.childNames("$media/hero"))
        assertTrue(r.log.contains("Roms/GB/.media/hero/A.jpg"))
        r.src.moveRefused = false
        assertTrue(r.run())   // tried again
        assertEquals(listOf("A.jpg"), r.src.childNames("$media/screenshot"))
        assertEquals(sgdbHero, r.log.origin("Roms/GB/.media/screenshot/A.jpg"))
        assertEquals("$media\n", r.marker())
    }

    @Test fun aThrowNeverEscapes() = runTest {
        val r = Rig(mapOf(media to listOf("hero"), "$media/hero" to listOf("A.jpg")))
        r.src.moveThrows = true
        assertFalse(r.run())
        assertNull(r.marker())
    }

    @Test fun markedSkips() = runTest {
        val r = Rig(mapOf(media to listOf("grid"), "$media/grid" to listOf("A.jpg")))
        r.host.files[LegacyArtMove.MARKER_FILE] = "other\n$media\n"
        assertFalse(r.run())
        assertEquals(listOf("A.jpg"), r.src.childNames("$media/grid"))
        assertNull(r.src.childNames("$media/boxart2d"))
    }

    @Test fun noLegacyFoldersNoMarker() = runTest {
        val r = Rig(mapOf(media to listOf("screenshot"), "$media/screenshot" to listOf("A.png")))
        assertFalse(r.run())
        assertNull(r.marker())
    }

    @Test fun logMoveKeepsOriginInOneSave() = runTest {
        val host = FakeHost(emptyList())
        val log = ArtLogStore(host, ArtworkController.LOG_FILE)
        log.add(listOf("a/grid/A.jpg" to sgdbGrid, "b.png" to null))
        val saves = host.writes.size
        log.move("a/grid/A.jpg", "a/boxart2d/A.jpg")
        assertEquals(saves + 1, host.writes.size)
        assertEquals(sgdbGrid, log.origin("a/boxart2d/A.jpg"))
        assertFalse(log.contains("a/grid/A.jpg"))
        log.move("not/logged.png", "x.png")   // not logged: nothing
        assertFalse(log.contains("x.png"))
        assertEquals(saves + 1, host.writes.size)
    }
}
