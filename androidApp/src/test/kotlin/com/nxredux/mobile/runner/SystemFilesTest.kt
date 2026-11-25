package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class SystemFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    /** An asset tree from "a/b/file" -> content; [failOn] throws when that file is opened. */
    private class Fake(private val files: Map<String, String>, var failOn: String? = null) : SystemFiles.Source {
        val opens = AtomicInteger()
        override fun list(path: String): List<String> =
            files.keys.filter { it.startsWith("$path/") }.map { it.removePrefix("$path/").substringBefore('/') }.distinct()
        override fun open(path: String): InputStream {
            opens.incrementAndGet()
            if (path == failOn) throw IOException("boom")
            return ByteArrayInputStream(files.getValue(path).encodeToByteArray())
        }
    }

    /** [Fake] with an index: each file's fingerprint is its content, as the APK's size and CRC stand for it. */
    private class Indexed(private val files: Map<String, String>) : SystemFiles.Source {
        val fake = Fake(files)
        override fun list(path: String) = fake.list(path)
        override fun open(path: String) = fake.open(path)
        override fun index() = files.mapKeys { it.key.removePrefix("system/") }
    }

    private val bundle = mapOf("system/prboom.wad" to "wad", "system/PPSSPP/flash0/font.pgf" to "font")

    @Test fun copiesTheTreeAndStampsIt() {
        val dir = tmp.newFolder("system")
        assertTrue(SystemFiles.sync(dir, "1", Fake(bundle)))
        assertEquals("wad", File(dir, "prboom.wad").readText())
        assertEquals("font", File(dir, "PPSSPP/flash0/font.pgf").readText())
        assertEquals("1", File(dir, ".stamp").readText())
    }

    @Test fun aMatchingStampSkipsTheCopy() {
        val dir = tmp.newFolder("system")
        SystemFiles.sync(dir, "1", Fake(bundle))
        val again = Fake(bundle)
        assertFalse(SystemFiles.sync(dir, "1", again))
        assertEquals(0, again.opens.get())
    }

    @Test fun anUpdateRecopiesOverBundledFilesAndKeepsTheRest() {
        val dir = tmp.newFolder("system")
        SystemFiles.sync(dir, "1", Fake(bundle))
        File(dir, "dc").mkdirs()
        File(dir, "dc/dc_nvmem.bin").writeText("core data")   // core-written
        File(dir, "scph5501.bin").writeText("imported bios")    // BiosImport
        assertTrue(SystemFiles.sync(dir, "2", Fake(bundle + ("system/prboom.wad" to "wad v2"))))
        assertEquals("wad v2", File(dir, "prboom.wad").readText())
        assertEquals("core data", File(dir, "dc/dc_nvmem.bin").readText())
        assertEquals("imported bios", File(dir, "scph5501.bin").readText())
        assertEquals("2", File(dir, ".stamp").readText())
    }

    @Test fun anUpdateLeavesUnchangedFilesUnwritten() {
        val dir = tmp.newFolder("system")
        SystemFiles.sync(dir, "1", Fake(bundle))
        val font = File(dir, "PPSSPP/flash0/font.pgf").apply { setLastModified(1_000_000L) }
        val wad = File(dir, "prboom.wad").apply { setLastModified(1_000_000L) }
        assertTrue(SystemFiles.sync(dir, "2", Fake(bundle + ("system/prboom.wad" to "wad v2"))))
        assertEquals(1_000_000L, font.lastModified())
        assertEquals("wad v2", wad.readText())
        assertEquals("2", File(dir, ".stamp").readText())
    }

    @Test fun anUpdatePutsBackABundledFileOfTheSameSizeThatChanged() {
        val dir = tmp.newFolder("system")
        SystemFiles.sync(dir, "1", Fake(bundle))
        File(dir, "prboom.wad").writeText("WAD")   // a core or an import wrote over it
        assertTrue(SystemFiles.sync(dir, "2", Fake(bundle)))
        assertEquals("wad", File(dir, "prboom.wad").readText())
    }

    @Test fun withAnIndexAnUpdateReadsOnlyWhatChanged() {
        val dir = tmp.newFolder("system")
        assertTrue(SystemFiles.sync(dir, "1", Indexed(bundle)))
        assertEquals("wad", File(dir, "prboom.wad").readText())
        assertEquals("font", File(dir, "PPSSPP/flash0/font.pgf").readText())
        val update = Indexed(bundle + ("system/prboom.wad" to "wad v2"))
        assertTrue(SystemFiles.sync(dir, "2", update))
        assertEquals(1, update.fake.opens.get())   // the font's size, CRC and copy are as recorded: not opened
        assertEquals("wad v2", File(dir, "prboom.wad").readText())
        assertEquals("2", File(dir, ".stamp").readText())
    }

    @Test fun withAnIndexACopyChangedSinceIsPutBack() {
        val dir = tmp.newFolder("system")
        SystemFiles.sync(dir, "1", Indexed(bundle))
        File(dir, "PPSSPP/flash0/font.pgf").writeText("imported font")   // BiosImport or a core wrote over it
        File(dir, "prboom.wad").delete()
        assertTrue(SystemFiles.sync(dir, "2", Indexed(bundle)))
        assertEquals("font", File(dir, "PPSSPP/flash0/font.pgf").readText())
        assertEquals("wad", File(dir, "prboom.wad").readText())
    }

    @Test fun anIndexLessCopyDropsTheManifest() {
        val dir = tmp.newFolder("system")
        SystemFiles.sync(dir, "1", Indexed(bundle))
        assertTrue(File(dir, ".manifest").isFile)
        SystemFiles.sync(dir, "2", Fake(bundle + ("system/prboom.wad" to "wad v2")))
        assertFalse(File(dir, ".manifest").exists())
    }

    @Test fun manifestRoundTripsAndSkipsBadRows() {
        val rows = mapOf("PPSSPP/flash0/font.pgf" to SystemFiles.Manifest.Row("4:123", 4, 99), "prboom.wad" to SystemFiles.Manifest.Row("3:7", 3, 5))
        val text = SystemFiles.Manifest.format(rows)
        assertEquals(rows, SystemFiles.Manifest.parse(text + "broken\tline\nx\t1:2\tnot-a-size\t5\n"))
    }

    @Test fun aCopyCutShortKeepsTheOldStampAndRetries() {
        val dir = tmp.newFolder("system")
        SystemFiles.sync(dir, "1", Fake(bundle))
        val failing = Fake(bundle + ("system/prboom.wad" to "wad v2"), failOn = "system/prboom.wad")
        runCatching { SystemFiles.sync(dir, "2", failing) }
        assertEquals("1", File(dir, ".stamp").readText())
        assertEquals("wad", File(dir, "prboom.wad").readText())   // the old file whole, no half-written one
        assertTrue(dir.walk().none { it.name.endsWith(".tmp") })
        assertTrue(SystemFiles.sync(dir, "2", Fake(bundle + ("system/prboom.wad" to "wad v2"))))
        assertEquals("wad v2", File(dir, "prboom.wad").readText())
    }

    @Test fun anEmptyBundleWritesNoStamp() {
        val dir = tmp.newFolder("system")
        assertFalse(SystemFiles.sync(dir, "1", Fake(emptyMap())))
        assertFalse(File(dir, ".stamp").exists())
    }

    @Test fun concurrentCallersCopyOnce() {
        val dir = tmp.newFolder("system")
        val fake = Fake(bundle)
        val start = CountDownLatch(1)
        val copies = AtomicInteger()
        val threads = List(4) { thread { start.await(); if (SystemFiles.sync(dir, "1", fake)) copies.incrementAndGet() } }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, copies.get())
        assertEquals(bundle.size, fake.opens.get())
    }
}
