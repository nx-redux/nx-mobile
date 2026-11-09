package com.nxredux.mobile.library

import com.nxredux.mobile.netplay.cablelink.RomHash
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.MemoryPrivateFiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalRomFinderTest {
    private val a = ByteArray(600_000) { (it * 7).toByte() }
    private val b = ByteArray(1_000) { (it * 3).toByte() }
    private val dir = "Home/Roms/Game Boy Advance (GBA)"
    private val source = FakeRomsSource(
        dirs = mapOf(dir to listOf("a.gba", "b.gba", "c.zip")),
        bytes = mapOf("$dir/a.gba" to a, "$dir/b.gba" to b, "$dir/c.zip" to a),
    ).also { it.sizes["$dir/a.gba"] = a.size.toLong(); it.sizes["$dir/b.gba"] = b.size.toLong(); it.sizes["$dir/c.zip"] = a.size.toLong() }
    private fun game(file: String) = IndexedGame(source = "home", dir = "Roms/Game Boy Advance (GBA)", file = file, ref = "$dir/$file", folder = dir, name = file)
    private val games = listOf("c.zip", "a.gba", "b.gba").map(::game)
    private fun sha(x: ByteArray) = RomHash.sha1Hex(sequenceOf(x))

    @Test fun find_local_rom_by_hash_in_gba_folders() = runTest {
        val finder = LocalRomFinder(source)
        assertEquals("$dir/a.gba", finder.find(sha(a), a.size.toLong(), games))   // the archive with the same bytes is never opened
        assertEquals("$dir/b.gba", finder.find(sha(b), b.size.toLong(), games))
        assertNull(finder.find(sha(byteArrayOf(1)), 1, games))
    }

    @Test fun a_rom_is_hashed_once_until_its_stamp_changes() = runTest {
        val finder = LocalRomFinder(source)
        source.stamps["$dir/a.gba"] = 10L
        assertEquals("$dir/a.gba", finder.find(sha(a), a.size.toLong(), games))
        source.unreadable += "$dir/a.gba"
        assertEquals("$dir/a.gba", finder.find(sha(a), a.size.toLong(), games))   // from the kept hash, no read
        source.stamps["$dir/a.gba"] = 11L
        assertNull(finder.find(sha(a), a.size.toLong(), games))                    // changed: read again, now unreadable
    }

    @Test fun a_rom_without_a_modified_time_is_kept_by_its_size() = runTest {
        val finder = LocalRomFinder(source)
        assertEquals("$dir/a.gba", finder.find(sha(a), a.size.toLong(), games))
        source.unreadable += "$dir/a.gba"
        assertEquals("$dir/a.gba", finder.find(sha(a), a.size.toLong(), games))
        source.sizes["$dir/a.gba"] = a.size - 1L
        assertNull(finder.find(sha(a), a.size.toLong(), games))   // another size: never taken for the same file
    }

    @Test fun a_size_mismatch_is_never_hashed() = runTest {
        // The right bytes under a wrong announced size: hashing a.gba would match, so only the size check says no.
        assertNull(LocalRomFinder(source).find(sha(a), a.size + 1L, games))
    }

    @Test fun the_kept_hashes_survive_a_new_finder() = runTest {
        val store = MemoryPrivateFiles()
        source.stamps["$dir/a.gba"] = 5L
        assertEquals("$dir/a.gba", LocalRomFinder(source, store).find(sha(a), a.size.toLong(), games))
        source.unreadable += "$dir/a.gba"
        assertEquals("$dir/a.gba", LocalRomFinder(source, store).find(sha(a), a.size.toLong(), games))   // restart: no read
    }

    @Test fun a_file_bigger_than_a_cartridge_is_skipped() = runTest {
        source.sizes["$dir/a.gba"] = LocalRomFinder.MAX_ROM_BYTES + 1
        assertNull(LocalRomFinder(source).find(sha(a), a.size.toLong() , games))
    }
}
