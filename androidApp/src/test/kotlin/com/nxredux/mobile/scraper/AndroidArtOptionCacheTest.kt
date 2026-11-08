package com.nxredux.mobile.scraper

import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AndroidArtOptionCacheTest {
    private val dir = File(Files.createTempDirectory("opts").toFile(), "art-options")
    private val cache = AndroidArtOptionCache(dir)

    @Test fun write_read_size_remove_clear() = runBlocking {
        assertNull(cache.ref("k1"))
        assertNull(cache.read("k1"))
        assertEquals(0L, cache.size())
        val ref = cache.write("k1", byteArrayOf(1, 2, 3))
        assertNotNull(ref)
        cache.write("k2", byteArrayOf(4, 5))
        assertEquals(ref, cache.ref("k1"))
        val f1 = File(dir, java.security.MessageDigest.getInstance("SHA-1").digest("k1".toByteArray()).joinToString("") { "%02x".format(it) })
        assertEquals(ContentRef("file://${f1.absolutePath}?v=${f1.lastModified()}"), ref)
        assertArrayEquals(byteArrayOf(1, 2, 3), cache.read("k1"))
        assertEquals(5L, cache.size())
        cache.remove(listOf("k1", "absent"))
        assertNull(cache.ref("k1"))
        assertNotNull(cache.ref("k2"))
        assertEquals(2L, cache.clear())
        assertNull(cache.ref("k2"))
        assertTrue(dir.list()!!.isEmpty())
    }
}
