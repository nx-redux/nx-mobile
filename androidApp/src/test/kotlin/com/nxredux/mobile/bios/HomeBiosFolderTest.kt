package com.nxredux.mobile.bios

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeBiosFolderTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun createsMissingFolders() {
        val home = tmp.newFolder("home")
        assertTrue(FileHomeBios(home).write("GBA", "gba_bios.bin") { byteArrayOf(1, 2, 3).inputStream() })
        assertArrayEquals(byteArrayOf(1, 2, 3), home.resolve("Bios/GBA/gba_bios.bin").readBytes())
    }

    @Test
    fun writeReplacesAtomically() {
        val home = tmp.newFolder("home")
        val dir = home.resolve("Bios/PS").apply { mkdirs() }
        dir.resolve("scph5501.bin").writeBytes(byteArrayOf(9))
        assertTrue(FileHomeBios(home).write("PS", "scph5501.bin") { byteArrayOf(7, 7).inputStream() })
        assertArrayEquals(byteArrayOf(7, 7), dir.resolve("scph5501.bin").readBytes())
        assertEquals(listOf("scph5501.bin"), dir.list()!!.toList())
    }

    @Test
    fun failedReadLeavesOldFile() {
        val home = tmp.newFolder("home")
        val dir = home.resolve("Bios/PS").apply { mkdirs() }
        dir.resolve("scph5501.bin").writeBytes(byteArrayOf(9))
        assertFalse(FileHomeBios(home).write("PS", "scph5501.bin") { error("gone") })
        assertArrayEquals(byteArrayOf(9), dir.resolve("scph5501.bin").readBytes())
        assertEquals(listOf("scph5501.bin"), dir.list()!!.toList())
    }

    @Test
    fun md5OfExistingAndMissing() {
        val home = tmp.newFolder("home")
        home.resolve("Bios/GB").mkdirs()
        home.resolve("Bios/GB/gb_bios.bin").writeText("abc")
        assertEquals("900150983cd24fb0d6963f7d28e17f72", FileHomeBios(home).md5("GB", "gb_bios.bin"))
        assertNull(FileHomeBios(home).md5("GB", "nope.bin"))
    }

    @Test
    fun md5sInHashesEveryFileOfTheConsoleOnce() {
        val home = tmp.newFolder("home")
        home.resolve("Bios/PS2").mkdirs()
        home.resolve("Bios/PS2/SCPH-70012_BIOS_V12_USA_200.BIN").writeText("abc")
        home.resolve("Bios/PS2/x.bin.part").writeText("half")
        assertEquals(setOf("900150983cd24fb0d6963f7d28e17f72"), FileHomeBios(home).md5sIn("PS2"))
        assertEquals(emptySet<String>(), FileHomeBios(home).md5sIn("GBA"))
    }

    @Test
    fun openerCopyClosesTheInputWhenTheOutputCannotOpen() {
        var closed = false
        val input = object : java.io.ByteArrayInputStream("abc".toByteArray()) { override fun close() { closed = true } }
        assertNull(runCatching { Md5.copy({ input }, { throw java.io.IOException("no space") }) }.getOrNull())
        assertTrue(closed)
    }

    @Test
    fun limitedCopyStopsPastTheLimit() {
        val out = tmp.newFile("y")
        assertNull(Md5.copy({ "abcdef".byteInputStream() }, { out.outputStream() }, limit = 4))
        assertEquals("900150983cd24fb0d6963f7d28e17f72" to 3L, Md5.copy({ "abc".byteInputStream() }, { tmp.newFile("z").outputStream() }, limit = 4))
    }

    @Test
    fun hashingCopyGivesMd5AndBytes() {
        val out = tmp.newFile("x")
        val (md5, size) = Md5.copy({ "abc".byteInputStream() }, { out.outputStream() })!!
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5)
        assertEquals(3L, size)
        assertEquals("abc", out.readText())
    }
}
