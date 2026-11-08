package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.Rzip
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.SaveNaming
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySavesTest {
    private val dir: File = Files.createTempDirectory("battery").toFile()
    private var reads = 0
    private val countingRead: (File) -> ByteArray = { reads++; it.readBytes() }

    @After fun cleanUp() { dir.deleteRecursively() }

    private var format = SaveFormat.MINUI
    private val paths get() = NxPaths("GB", "gambatte", "Tetris.gb", SaveNaming(saveFormat = format))

    private fun saves(format: SaveFormat = SaveFormat.MINUI): BatterySaves {
        this.format = format
        return BatterySaves(dir, paths, null, countingRead)
    }

    private val target get() = File(dir, paths.saveRam.substringAfterLast('/'))

    /** Another writer replaces the file: new content, a different size and a later modification time. */
    private fun replaceFromOutside(file: File, bytes: ByteArray) {
        val before = file.lastModified()
        file.writeBytes(bytes)
        file.setLastModified(before + 5_000)
    }

    @Test fun anUnchangedSaveAfterLoadIsSkippedWithoutReadingTheFile() {
        val s0 = saves()
        assertTrue(s0.write(byteArrayOf(1, 2, 3)))
        val s = saves()
        assertArrayEquals(byteArrayOf(1, 2, 3), s.load())
        reads = 0
        assertFalse(s.write(byteArrayOf(1, 2, 3)))
        assertFalse(s.write(byteArrayOf(1, 2, 3)))
        assertEquals(0, reads)
    }

    @Test fun aChangedSaveIsAlwaysWritten() {
        val s = saves()
        assertTrue(s.write(byteArrayOf(1, 2, 3)))
        assertTrue(s.write(byteArrayOf(1, 2, 4)))
        assertArrayEquals(byteArrayOf(1, 2, 4), target.readBytes())
        assertTrue(s.write(byteArrayOf(1, 2, 3)))
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }

    @Test fun afterItsOwnWriteTheSameBytesNeedNoRead() {
        val s = saves()
        assertNull(s.load())
        assertTrue(s.write(byteArrayOf(7, 7)))
        reads = 0
        assertFalse(s.write(byteArrayOf(7, 7)))
        assertEquals(0, reads)
    }

    @Test fun aFileReplacedFromOutsideIsReadAgainAndOverwrittenWithTheGamesSave() {
        val s = saves()
        assertTrue(s.write(byteArrayOf(1, 2, 3)))
        replaceFromOutside(target, byteArrayOf(9, 9, 9, 9))
        reads = 0
        assertTrue(s.write(byteArrayOf(1, 2, 3)))
        assertEquals(1, reads)
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }

    @Test fun aFileReplacedFromOutsideWithTheSameBytesIsLeftAlone() {
        val s = saves()
        assertTrue(s.write(byteArrayOf(1, 2, 3)))
        replaceFromOutside(target, byteArrayOf(1, 2, 3))
        val stamp = target.lastModified()
        assertFalse(s.write(byteArrayOf(1, 2, 3)))
        assertEquals(stamp, target.lastModified())
    }

    @Test fun aDeletedFileIsWrittenAgain() {
        val s = saves()
        assertTrue(s.write(byteArrayOf(1, 2, 3)))
        target.delete()
        assertTrue(s.write(byteArrayOf(1, 2, 3)))
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }

    @Test fun anRzipSaveComparesByDecodedContentAndIsCachedDecoded() {
        val s0 = saves(SaveFormat.RETROARCH_COMPRESSED)
        assertTrue(s0.write(ByteArray(4096) { (it % 7).toByte() }))
        assertArrayEquals(ByteArray(4096) { (it % 7).toByte() }, Rzip.decode(target.readBytes()))
        val s = saves(SaveFormat.RETROARCH_COMPRESSED)
        s.load()
        reads = 0
        assertFalse(s.write(ByteArray(4096) { (it % 7).toByte() }))
        assertEquals(0, reads)
        assertTrue(s.write(ByteArray(4096) { (it % 5).toByte() }))
    }

    @Test fun theCallersArrayChangingLaterDoesNotHideAChange() {
        val s = saves()
        val sram = byteArrayOf(1, 2, 3)
        assertTrue(s.write(sram))
        sram[0] = 5
        assertTrue(s.write(sram))
        assertArrayEquals(byteArrayOf(5, 2, 3), target.readBytes())
    }

    @Test fun emptySramIsNeverWritten() {
        val s = saves()
        assertFalse(s.write(ByteArray(0)))
        assertTrue(dir.listFiles()!!.isEmpty())
    }
}
