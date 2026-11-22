package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.ListingFault
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RaFingerprintsTest {
    private val h = "0123456789abcdef0123456789abcdef"
    private val home = RaGamePointer("GBA", "home", "Roms/GBA (GBA)", "Tetris.gba", "Tetris")
    private val extra = RaGamePointer("GBA", "x1", "", "Tetris.gba", "Tetris")

    @Test fun keys() {
        assertEquals(RaFingerprintKey("GBA (GBA)", "Tetris.gba"), RaFingerprintKeys.of(home, null))
        assertEquals(RaFingerprintKey("PS (PS)", "FF7/FF7.m3u"), RaFingerprintKeys.of(home.copy(dir = "Roms/PS (PS)/FF7", file = "FF7.m3u"), null))
        assertEquals(RaFingerprintKey("GBA (GBA)", ".sources/x1/Tetris.gba"), RaFingerprintKeys.of(extra, "GBA (GBA)"))
        assertEquals(RaFingerprintKey("GBA (GBA)", ".sources/x1/a/b/Tetris.gba"), RaFingerprintKeys.of(extra.copy(dir = "a/b"), "GBA (GBA)"))
        assertNull(RaFingerprintKeys.of(home.copy(dir = "Roms"), null))
        assertNull(RaFingerprintKeys.of(home.copy(dir = "Roms/.x"), null))
        assertNull(RaFingerprintKeys.of(extra, null))
        assertNull(RaFingerprintKeys.of(extra.copy(dir = "a/../b"), "GBA (GBA)"))
    }

    @Test fun storable_refuses_raw_archives_except_arcade() {
        assertTrue(RaFingerprintKeys.storable("GBA", "E.gba", 5, unpacked = false))
        assertTrue(RaFingerprintKeys.storable("GBA", "E.zip", 5, unpacked = true))
        assertTrue(RaFingerprintKeys.storable("FBN", "1942.zip", 27, unpacked = false))
        // Dreamcast's flycast loads the archive itself: its raw zip hash is never stored (console 40, not Arcade).
        assertTrue(CoreRegistry.loadsArchive("DC", "x.zip"))
        assertFalse(RaFingerprintKeys.storable("DC", "x.zip", 40, unpacked = false))
    }

    @Test fun stamp_parses_size_and_seconds() {
        assertEquals(16L to 1728800000L, RaStamp.parse("16:1728800000999"))
        assertNull(RaStamp.parse(null)); assertNull(RaStamp.parse("x"))
    }

    @Test fun an_extra_rom_never_hits_a_home_entry_of_the_same_name() = runTest {
        val src = raHomeSource("GBA (GBA)")
        val fp = RaFingerprints(RaHomeTree(src) { ContentRef("home") })
        val hk = fp.keyFor(home)!!; val ek = fp.keyFor(extra)!!
        fp.update(hk.consoleFolder) { it.record(hk.key, 1, 1, 5, h, 9) }
        assertEquals(RaFingerprint(h, 9), fp.snapshot(hk.consoleFolder).lookup(hk.key, 1, 1, 5))
        assertNull(fp.snapshot(ek.consoleFolder).lookup(ek.key, 1, 1, 5))
        assertTrue(src.textAt("home/Roms/GBA (GBA)/.retroachievements/hashes.json")!!.contains("\"Tetris.gba\""))
    }

    @Test fun a_part_file_is_read_when_the_main_file_is_missing() = runTest {
        val json = """{"version":1,"files":{"T.gba":{"size":1,"mtime":1,"console":5,"hash":"$h","game":3}}}"""
        val src = FakeRomsSource(
            mapOf("home" to listOf("Roms"), "home/Roms" to listOf("G (GBA)"), "home/Roms/G (GBA)" to listOf(".retroachievements"),
                "home/Roms/G (GBA)/.retroachievements" to listOf("hashes.json.part")),
            texts = mapOf("home/Roms/G (GBA)/.retroachievements/hashes.json.part" to json))
        val fp = RaFingerprints(RaHomeTree(src) { ContentRef("home") })
        assertEquals(RaFingerprint(h, 3), fp.snapshot("G (GBA)").lookup("T.gba", 1, 1, 5))
    }

    @Test fun update_without_a_change_writes_nothing() = runTest {
        val src = raHomeSource("G (GBA)")
        val fp = RaFingerprints(RaHomeTree(src) { ContentRef("home") })
        fp.update("G (GBA)") { }
        assertTrue(src.written.isEmpty())
    }

    private val handheld = """{"version":1,"files":{"H.gba":{"size":1,"mtime":1,"console":5,"hash":"$h","game":7}}}"""
    private val data = "home/Roms/G (GBA)/.retroachievements"
    private fun withData(vararg files: Pair<String, String>) = FakeRomsSource(
        mapOf("home" to listOf("Roms"), "home/Roms" to listOf("G (GBA)"), "home/Roms/G (GBA)" to listOf(".retroachievements"),
            data to files.map { it.first }),
        texts = files.associate { "$data/${it.first}" to it.second })

    @Test fun a_listed_main_file_that_cannot_be_read_is_never_overwritten() = runTest {
        val src = withData("hashes.json" to handheld); src.unreadable += "$data/hashes.json"
        val fp = RaFingerprints(RaHomeTree(src) { ContentRef("home") })
        fp.update("G (GBA)") { it.record("T.gba", 1, 1, 5, h, 3) }
        assertTrue(src.written.isEmpty()); assertEquals(handheld, src.textAt("$data/hashes.json"))
    }

    @Test fun a_failed_listing_writes_nothing() = runTest {
        val unread = withData("hashes.json" to handheld).apply { unreadable += "$data/hashes.json"; listingFault = ListingFault.UNAVAILABLE }
        RaFingerprints(RaHomeTree(unread) { ContentRef("home") }).update("G (GBA)") { it.record("T.gba", 1, 1, 5, h, 3) }
        assertTrue(unread.written.isEmpty()); assertEquals(handheld, unread.textAt("$data/hashes.json"))
        // No main file found by name, and a listing that won't confirm it is absent: no write either.
        val unlisted = withData().apply { listingFault = ListingFault.UNAVAILABLE }
        RaFingerprints(RaHomeTree(unlisted) { ContentRef("home") }).update("G (GBA)") { it.record("T.gba", 1, 1, 5, h, 3) }
        assertTrue(unlisted.written.isEmpty())
    }

    @Test fun the_part_file_is_used_only_when_the_main_file_is_not_listed() = runTest {
        val part = """{"version":1,"files":{"T.gba":{"size":1,"mtime":1,"console":5,"hash":"$h","game":3}}}"""
        val src = withData("hashes.json" to handheld, "hashes.json.part" to part); src.unreadable += "$data/hashes.json"
        val fp = RaFingerprints(RaHomeTree(src) { ContentRef("home") })
        assertNull(fp.snapshot("G (GBA)").lookup("T.gba", 1, 1, 5))
        fp.update("G (GBA)") { it.record("U.gba", 1, 1, 5, h, 4) }
        assertTrue(src.written.isEmpty())
    }

    @Test fun a_console_folder_without_ra_data_gets_its_first_file() = runTest {
        val src = raHomeSource()
        val fp = RaFingerprints(RaHomeTree(src) { ContentRef("home") })
        fp.update("G (GBA)") { it.record("T.gba", 1, 1, 5, h, 3) }
        assertEquals(RaFingerprint(h, 3), fp.snapshot("G (GBA)").lookup("T.gba", 1, 1, 5))
    }
}
