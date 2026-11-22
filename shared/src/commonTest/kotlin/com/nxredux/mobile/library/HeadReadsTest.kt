package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.MemorySidecarStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The sheet and zip heads a scan reads ([HeadReads]): kept in the index, not read again while a file is unchanged. */
class HeadReadsTest {
    private val ps = "H/Roms/Sony PlayStation (PS)"


    private fun library() = FakeRomsSource(
        mapOf(
            "H" to listOf("Roms"), "H/Roms" to listOf("Sony PlayStation (PS)"),
            ps to listOf("Crash.cue", "Crash.bin", "FF7.m3u", "FF7 (Disc 1).cue", "FF7 (Disc 1).bin"),
            "x" to listOf("w.zip", "K.gdi", "K track01.bin"),
        ),
        texts = mapOf(
            "$ps/Crash.cue" to "FILE \"Crash.bin\" BINARY\n",
            "$ps/FF7.m3u" to "FF7 (Disc 1).cue\n",
            "$ps/FF7 (Disc 1).cue" to "FILE \"FF7 (Disc 1).bin\" BINARY\n",
            "x/K.gdi" to "1\n1 0 4 2352 \"K track01.bin\" 0\n",
        ),
        bytes = mapOf("x/w.zip" to zipHead("Mario.gba")),
    ).apply { listOf("$ps/Crash.cue", "$ps/FF7.m3u", "$ps/FF7 (Disc 1).cue", "x/K.gdi", "x/w.zip").forEach { stamps[it] = 1_000L } }

    private val extra = ExtraFolder("x-1", ContentRef("x"))

    private fun shown(index: LibraryIndex) = index.systems.associate { s -> s.tag to s.games.map { it.file }.sorted() }

    @Test fun an_unchanged_sheet_or_zip_is_not_read_again() = runTest {
        val src = CountingRomsSource(library())
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
        val first = scanner.scan(ContentRef("H"), listOf(extra), emptyMap())
        assertEquals(listOf("Crash.cue", "FF7.m3u"), shown(first)["PS"])   // the sets show as their sheets
        assertEquals(listOf("w.zip"), shown(first)["GBA"])                  // placed by its first entry
        assertTrue(src.calls.any { it == "readHead:x/w.zip" } && src.calls.any { it == "readHead:$ps/Crash.cue" }, "${src.calls}")
        assertEquals(setOf("$ps/Crash.cue", "$ps/FF7.m3u", "$ps/FF7 (Disc 1).cue", "x/K.gdi", "x/w.zip"), first.heads.map { it.ref }.toSet())

        src.calls.clear()
        val second = scanner.scan(ContentRef("H"), listOf(extra), emptyMap(), previous = first)
        assertTrue(src.calls.none { it.startsWith("readHead:") }, "${src.calls}")
        assertEquals(shown(first), shown(second))
        assertEquals(first.heads.toSet(), second.heads.toSet())   // kept for the scan after
    }

    @Test fun a_changed_time_or_size_reads_the_file_again() = runTest {
        val fake = library()
        val src = CountingRomsSource(fake)
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
        val first = scanner.scan(ContentRef("H"), listOf(extra), emptyMap())
        // Crash.cue now names a second track; the zip now holds a Game Boy game.
        fake.writeText(ContentRef(ps), "Crash.cue", "FILE \"Crash.bin\" BINARY\nFILE \"Crash (Track 2).bin\" BINARY\n")
        fake.stamps["$ps/Crash.cue"] = 2_000L
        fake.writeText(ContentRef(ps), "Crash (Track 2).bin", "")
        fake.writeBytes(ContentRef("x"), "w.zip", zipHead("Tetris.gb"))
        fake.stamps["x/w.zip"] = 1_000L      // same time, another size
        fake.sizes["x/w.zip"] = 99L
        src.calls.clear()
        val second = scanner.scan(ContentRef("H"), listOf(extra), emptyMap(), previous = first)
        assertEquals(setOf("readHead:$ps/Crash.cue", "readHead:x/w.zip"), src.calls.filter { it.startsWith("readHead:") }.toSet())
        assertEquals(listOf("Crash.cue", "FF7.m3u"), shown(second)["PS"])   // the new track is hidden behind its sheet
        assertEquals(listOf("w.zip"), shown(second)["GB"])
    }

    // A provider that reports no time keeps nothing (a same-size rewrite would go unseen); a failed read is not kept either.
    @Test fun no_time_or_a_failed_read_is_read_every_scan() = runTest {
        val fake = library().apply { stamps.remove("$ps/Crash.cue"); unreadable += "x/w.zip" }
        val src = CountingRomsSource(fake)
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
        val first = scanner.scan(ContentRef("H"), listOf(extra), emptyMap())
        assertTrue(first.heads.none { it.ref == "$ps/Crash.cue" || it.ref == "x/w.zip" })
        src.calls.clear()
        scanner.scan(ContentRef("H"), listOf(extra), emptyMap(), previous = first)
        assertEquals(setOf("readHead:$ps/Crash.cue", "readHead:x/w.zip"), src.calls.filter { it.startsWith("readHead:") }.toSet())
    }

    // The index keeps the heads through its codec, so a cold start's scan benefits too.
    @Test fun the_heads_survive_the_stored_index() = runTest {
        val src = library()
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") }).scan(ContentRef("H"), listOf(extra), emptyMap())
        assertEquals(index.heads, LibraryIndexCodec.decode(LibraryIndexCodec.encode(index))?.heads)
    }
}
