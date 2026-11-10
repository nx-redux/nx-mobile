package com.nxredux.mobile.netplay.cablelink

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LinkRomCacheTest {
    private val rom = ByteArray(300_000) { (it * 31).toByte() }
    private val hash = RomHash.sha1Hex(sequenceOf(rom))

    @Test fun sha1_matches_known_vectors_however_the_bytes_are_chunked() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", RomHash.sha1Hex(sequenceOf("abc".encodeToByteArray())))
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", RomHash.sha1Hex(emptySequence()))
        val chunked = RomHash.sha1Hex(rom.asList().chunked(7_001).map { it.toByteArray() }.asSequence())
        assertEquals(hash, chunked)
        // 56 bytes: the length field no longer fits the first padded block.
        assertEquals("c2db330f6083854c99d4b5bfb6e8f29f201be699", RomHash.sha1Hex(sequenceOf(ByteArray(56) { 'a'.code.toByte() })))
    }

    @Test fun put_verifies_hash_and_finds() = runTest {
        val cache = LinkRomCache(MemoryCacheStore())
        assertNull(cache.find(hash))
        val path = cache.put(hash) { sink -> rom.asList().chunked(65_536).forEach { sink.write(it.toByteArray()) } }
        assertEquals(path, cache.find(hash))
        assertEquals(rom.size.toLong(), cache.sizeBytes())
    }

    @Test fun put_with_wrong_hash_is_discarded() = runTest {
        val store = MemoryCacheStore()
        val cache = LinkRomCache(store)
        assertFailsWith<LinkRomHashMismatch> { cache.put(hash) { it.write(rom.copyOf().also { b -> b[0]++ }) } }
        assertNull(cache.find(hash))
        assertEquals(0L, cache.sizeBytes())
    }

    @Test fun a_name_that_is_not_a_hash_never_reaches_the_store() = runTest {
        val cache = LinkRomCache(MemoryCacheStore())
        assertNull(cache.find("../../etc/passwd"))
        assertFailsWith<IllegalArgumentException> { cache.put("../x") { it.write(rom) } }
    }

    @Test fun clear_empties_and_size_zero() = runTest {
        val cache = LinkRomCache(MemoryCacheStore())
        cache.put(hash) { it.write(rom) }
        cache.clear()
        assertNull(cache.find(hash))
        assertEquals(0L, cache.sizeBytes())
    }
}
