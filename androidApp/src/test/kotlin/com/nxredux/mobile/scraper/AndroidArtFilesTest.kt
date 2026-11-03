package com.nxredux.mobile.scraper

import coil3.filePath
import coil3.toUri
import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AndroidArtFilesTest {
    private val dir = File(Files.createTempDirectory("art").toFile(), AndroidArtFiles.DIR)
    private val store = AndroidArtFiles(dir)

    @Test fun storesByPackageAndReturnsAVersionedFileRef() = runBlocking {
        assertNull(store.art("com.example.game/.Main", ArtSlot.SCREENSHOT))
        val ref = store.write("com.example.game/.Main", ArtSlot.SCREENSHOT, byteArrayOf(1, 2))
        val file = File(dir, "com.example.game.png")
        assertEquals(ContentRef("file://${file.absolutePath}?v=${file.lastModified()}"), ref)
        assertEquals(ref, store.art("com.example.game/com.example.game.Other", ArtSlot.SCREENSHOT))   // the package names the file
        store.write("com.example.game/.Main", ArtSlot.SCREENSHOT, byteArrayOf(3))                   // a replace
        assertArrayEquals(byteArrayOf(3), file.readBytes())
        assertEquals(listOf("com.example.game.png"), dir.list()!!.toList())                 // no temp file left
    }

    @Test fun aReplacedScreenshotGetsANewRef() = runBlocking {
        val first = store.write("com.a/.Main", ArtSlot.SCREENSHOT, byteArrayOf(1))
        val file = File(dir, "com.a.png")
        assertTrue(file.setLastModified(1_000_000L))
        assertEquals(ContentRef("file://${file.absolutePath}?v=1000000"), store.art("com.a/.Main", ArtSlot.SCREENSHOT))
        assertNotEquals(first, store.art("com.a/.Main", ArtSlot.SCREENSHOT))   // a new memory-cache key for Coil
    }

    @Test fun coilReadsTheFileThroughTheVersion() = runBlocking {
        val ref = store.write("com.a/.Main", ArtSlot.SCREENSHOT, byteArrayOf(1))!!
        val uri = ref.value.toUri()
        assertEquals("file", uri.scheme)
        assertEquals(File(dir, "com.a.png").absolutePath, uri.filePath)          // what FileUriFetcher opens: the query is not part of it
        assertEquals(ref.value, uri.toString())                                   // the memory cache key keeps it
    }

    @Test fun anIdThatIsNotAPackageNamesNoFile() = runBlocking {
        assertNull(store.write("../escape/.Main", ArtSlot.SCREENSHOT, byteArrayOf(1)))
        assertNull(store.write("/.Main", ArtSlot.SCREENSHOT, byteArrayOf(1)))
        assertNull(store.art("../escape/.Main", ArtSlot.SCREENSHOT))
        assertTrue(!dir.exists() || dir.list()!!.isEmpty())
    }

    @Test fun deleteAllRemovesEveryScreenshotAndCountsThem() = runBlocking {
        assertEquals(0, store.deleteAll())   // no folder yet
        store.write("com.a/.Main", ArtSlot.SCREENSHOT, byteArrayOf(1))
        store.write("com.b/.Main", ArtSlot.SCREENSHOT, byteArrayOf(2))
        File(dir, "com.c.png.1f.tmp").writeBytes(byteArrayOf(3))   // left by an interrupted write: removed, not counted
        assertEquals(2, store.deleteAll())
        assertNull(store.art("com.a/.Main", ArtSlot.SCREENSHOT))
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test fun heroMarker() = runBlocking {
        val shot = store.write("com.a/.Main", ArtSlot.SCREENSHOT, byteArrayOf(1), hero = true)
        assertEquals(store.art("com.a/.Main", ArtSlot.SCREENSHOT), shot)
        assertTrue(store.heroShot("com.a/.Main"))
        assertTrue(File(dir, "com.a.hero").isFile)
        store.write("com.a/.Main", ArtSlot.SCREENSHOT, byteArrayOf(2))
        assertFalse(store.heroShot("com.a/.Main"))
        store.write("com.a/.Main", ArtSlot.SCREENSHOT, byteArrayOf(3), hero = true)
        val box = store.write("com.a/.Main", ArtSlot.BOX_2D, byteArrayOf(4))
        val wheel = store.write("com.a/.Main", ArtSlot.WHEEL, byteArrayOf(5))
        assertEquals(store.art("com.a/.Main", ArtSlot.BOX_2D), box)
        assertEquals(store.art("com.a/.Main", ArtSlot.WHEEL), wheel)
        assertArrayEquals(byteArrayOf(4), File(dir, "com.a.box2d.png").readBytes())
        assertArrayEquals(byteArrayOf(5), File(dir, "com.a.wheel.png").readBytes())
        assertTrue(store.heroShot("com.a/.Main"))                                   // another slot's write leaves the marker
        assertEquals(3, store.deleteAll())
        assertTrue(dir.list()!!.isEmpty())                                           // .png, .box2d.png, .wheel.png, .hero
        assertFalse(store.heroShot("com.a/.Main"))
        assertNull(store.art("com.a/.Main", ArtSlot.BOX_2D))
    }

    @Test fun onlyTheStoredSlotsAreWritten() = runBlocking {
        assertNull(store.write("com.a/.Main", ArtSlot.BOX_3D, byteArrayOf(1)))
        assertNull(store.art("com.a/.Main", ArtSlot.BOX_3D))
        assertTrue(!dir.exists() || dir.list()!!.isEmpty())
    }
}
