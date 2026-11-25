package com.nxredux.mobile.library

import com.nxredux.mobile.library.disc.DiscProbe
import com.nxredux.mobile.library.disc.DiscSignatures
import com.nxredux.mobile.library.disc.ps1BinBytes
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.MemorySidecarStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LooseProbeTest {
    private val cue = "FILE \"G.bin\" BINARY\n TRACK 01 MODE2/2352\n  INDEX 01 00:00:00\n"

    // A loose disc no folder or extension places is placed by its contents; an unchanged file is not read again.
    @Test fun a_disc_is_placed_by_its_contents_and_probed_once() = runTest {
        val fake = FakeRomsSource(
            mapOf("H" to listOf("Roms"), "H/Roms" to emptyList(), "x" to listOf("G.bin", "G.cue", "K.gba")),
            texts = mapOf("x/G.cue" to cue),
            bytes = mapOf("x/G.bin" to ps1BinBytes(), "x/K.gba" to ByteArray(4)),
        ).apply { stamps["x/G.cue"] = 1_000L; stamps["x/G.bin"] = 1_000L }
        val src = CountingRomsSource(fake)
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
        val extra = ExtraFolder("x-1", ContentRef("x"))
        val first = scanner.scan(ContentRef("H"), listOf(extra), emptyMap())
        assertEquals(listOf("G"), first.systems.first { it.tag == "PS" }.games.map { it.name })
        assertTrue(first.systems.none { it.unassigned })
        assertEquals(listOf("x/G.cue"), first.probes.map { it.ref })
        src.calls.clear()
        val second = scanner.scan(ContentRef("H"), listOf(extra), emptyMap(), previous = first)
        assertTrue(src.calls.none { it.startsWith("readRange:") }, "${src.calls}")
        assertEquals(listOf("G"), second.systems.first { it.tag == "PS" }.games.map { it.name })
    }

    // A bin with no disc signature stays unassigned (and is not read again either).
    @Test fun an_unrecognised_disc_stays_unassigned() = runTest {
        val src = FakeRomsSource(
            mapOf("H" to listOf("Roms"), "H/Roms" to emptyList(), "x" to listOf("N.bin")),
            bytes = mapOf("x/N.bin" to ByteArray(40000) { (it * 7).toByte() }),
        ).apply { stamps["x/N.bin"] = 1_000L }
        val index = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
            .scan(ContentRef("H"), listOf(ExtraFolder("x-1", ContentRef("x"))), emptyMap())
        assertEquals(listOf("N"), index.systems.single { it.unassigned }.games.map { it.name })
        assertEquals(listOf(null), index.probes.map { it.console })
    }

    // A nothing-recognised answer kept under older signatures is probed again (a Saturn disc now places); a current one is reused.
    @Test fun saturn_disc_probed_again_after_version_bump() = runTest {
        val sat = ByteArray(2048 * 17).also { "SEGA SEGASATURN ".encodeToByteArray().copyInto(it, 0) }
        val fake = FakeRomsSource(mapOf("x" to listOf("S.iso")), bytes = mapOf("x/S.iso" to sat)).apply { stamps["x/S.iso"] = 1_000L }
        val src = CountingRomsSource(fake)
        val dir = fake.listChildren(ContentRef("x"))
        val s = dir.single()
        val old = listOf(IndexedProbe(s.ref.value, s.sizeBytes, 1_000L, null, signatures = 1))
        val probes = DiscProbes(DiscProbe(src), old)
        assertEquals("saturn", probes.consoleOf(s, dir))
        assertEquals(listOf("saturn"), probes.used.map { it.console })
        assertEquals(DiscSignatures.VERSION, probes.used.single().signatures)
        src.calls.clear()
        val current = listOf(IndexedProbe(s.ref.value, s.sizeBytes, 1_000L, null, signatures = DiscSignatures.VERSION))
        assertEquals(null, DiscProbes(DiscProbe(src), current).consoleOf(s, dir))
        assertTrue(src.calls.none { it.startsWith("readRange:") }, "${src.calls}")
    }

    // A recognised answer stays good under newer signatures: only nothing-recognised ones are probed again.
    @Test fun a_recognised_answer_from_older_signatures_is_kept() = runTest {
        val sat = ByteArray(2048 * 17).also { "SEGA SEGASATURN ".encodeToByteArray().copyInto(it, 0) }
        val fake = FakeRomsSource(mapOf("x" to listOf("S.iso")), bytes = mapOf("x/S.iso" to sat)).apply { stamps["x/S.iso"] = 1_000L }
        val src = CountingRomsSource(fake)
        val dir = fake.listChildren(ContentRef("x"))
        val s = dir.single()
        val old = IndexedProbe(s.ref.value, s.sizeBytes, 1_000L, "saturn", signatures = 1)
        val probes = DiscProbes(DiscProbe(src), listOf(old))
        assertEquals("saturn", probes.consoleOf(s, dir))
        assertTrue(src.calls.none { it.startsWith("readRange:") }, "${src.calls}")
        assertEquals(listOf(old), probes.used)
    }

    // A cue's answer depends on its bin: a changed bin is probed again; a missing bin (or a failed read) is not kept.
    @Test fun a_changed_or_missing_data_file_is_probed_again() = runTest {
        val fake = FakeRomsSource(
            mapOf("H" to listOf("Roms"), "H/Roms" to emptyList(), "x" to listOf("G.cue")),
            texts = mapOf("x/G.cue" to cue),
        ).apply { stamps["x/G.cue"] = 1_000L }
        val src = CountingRomsSource(fake)
        val scanner = LibraryScanner(src, Sidecars(src, MemorySidecarStore()) { ContentRef("H") })
        val extra = ExtraFolder("x-1", ContentRef("x"))
        val missing = scanner.scan(ContentRef("H"), listOf(extra), emptyMap())
        assertTrue(missing.probes.isEmpty())                                        // the bin is not there yet: nothing kept
        fake.writeBytes(ContentRef("x"), "G.bin", ps1BinBytes())
        val found = scanner.scan(ContentRef("H"), listOf(extra), emptyMap(), previous = missing)
        assertEquals(listOf("G"), found.systems.first { it.tag == "PS" }.games.map { it.name })
        fake.sizes["x/G.bin"] = 99L                                                 // the bin changed
        src.calls.clear()
        scanner.scan(ContentRef("H"), listOf(extra), emptyMap(), previous = found)
        assertTrue(src.calls.any { it.startsWith("readRange:x/G.bin") }, "${src.calls}")
    }

    // Without a modified time a same-size rewrite would go unseen: such a file is probed every scan, never kept.
    @Test fun a_file_without_a_modified_time_is_probed_again_and_kept_once() = runTest {
        val fake = FakeRomsSource(mapOf("x" to listOf("G.cue", "G.bin", "N.cue", "N.bin")), texts = mapOf("x/G.cue" to cue, "x/N.cue" to cue.replace("G.bin", "N.bin")),
            bytes = mapOf("x/G.bin" to ps1BinBytes(), "x/N.bin" to ps1BinBytes()))
            .apply { stamps["x/G.cue"] = 1_000L; stamps["x/G.bin"] = 1_000L }
        val src = CountingRomsSource(fake)
        val dir = fake.listChildren(ContentRef("x"))
        val g = dir.first { it.name == "G.cue" }
        val n = dir.first { it.name == "N.cue" }
        val stale = listOf(IndexedProbe(n.ref.value, n.sizeBytes, null, "md", "x/N.bin", 1L, null))
        val probes = DiscProbes(DiscProbe(src), stale)
        assertEquals("ps", probes.consoleOf(n, dir))
        assertTrue(src.calls.any { it == "readRange:x/N.bin" }, "${src.calls}")
        probes.consoleOf(g, dir); probes.consoleOf(g, dir)   // a folder reached twice
        assertEquals(listOf(g.ref.value), probes.used.map { it.ref })
    }
}
