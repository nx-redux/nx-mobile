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
    fun hashingCopyGivesMd5AndBytes() {
        val out = tmp.newFile("x")
        val (md5, size) = Md5.copy("abc".byteInputStream(), out.outputStream())
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5)
        assertEquals(3L, size)
        assertEquals("abc", out.readText())
    }
}
