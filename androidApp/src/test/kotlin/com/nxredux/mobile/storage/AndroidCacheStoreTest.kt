package com.nxredux.mobile.storage

import com.nxredux.mobile.netplay.cablelink.LinkRomCache
import com.nxredux.mobile.netplay.cablelink.LinkRomHashMismatch
import com.nxredux.mobile.netplay.cablelink.RomHash
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AndroidCacheStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val rom = ByteArray(100_000) { (it * 13).toByte() }
    private val hash = RomHash.sha1Hex(sequenceOf(rom))

    @Test fun a_write_that_throws_midway_leaves_nothing() = runBlocking {
        val dir = File(tmp.root, "link-roms")
        val store = AndroidCacheStore(dir)
        try {
            store.write(hash) { it.write(rom, 0, 5_000); throw IOException("link dropped") }
        } catch (e: IOException) { Unit }
        assertNull(store.path(hash))
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test fun a_failed_second_writer_leaves_the_first_ones_file_whole() = runBlocking {
        val cache = LinkRomCache(AndroidCacheStore(File(tmp.root, "link-roms")))
        val path = cache.put(hash) { it.write(rom) }
        try { cache.put(hash) { it.write(rom, 0, 10); throw IOException("again") } } catch (e: IOException) { Unit }
        assertArrayEquals(rom, File(path).readBytes())
    }

    @Test fun a_wrong_hash_is_deleted_and_the_good_one_renamed_into_place() = runBlocking {
        val dir = File(tmp.root, "link-roms")
        val cache = LinkRomCache(AndroidCacheStore(dir))
        try { cache.put(hash) { it.write(rom, 0, 99_999) }; throw AssertionError("should not be kept") } catch (e: LinkRomHashMismatch) { Unit }
        assertNull(cache.find(hash))
        val path = cache.put(hash) { it.write(rom) }
        assertEquals(listOf(hash), dir.list()!!.toList())
        assertEquals(rom.size.toLong(), cache.sizeBytes())
        cache.clear()
        assertTrue(dir.list()!!.isEmpty())
        assertNull(cache.find(hash)); assertTrue(!File(path).exists())
    }
}
