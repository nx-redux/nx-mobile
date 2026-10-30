package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

class FileLocksTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Another holder's try on [file]: null while it is locked (in this JVM, an overlapping lock throws instead). */
    private fun tryLock(file: File): Boolean = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { ch ->
        try { ch.tryLock()?.also { it.release() } != null } catch (e: OverlappingFileLockException) { false }
    }

    @Test fun blockRunsUnderTheFileLockAndReleasesIt() {
        val file = File(tmp.root, "locks/x.lock")
        val result = withFileLock(file) {
            assertTrue(file.exists())
            assertEquals(false, tryLock(file))
            42
        }
        assertEquals(42, result)
        assertTrue(tryLock(file))
    }

    @Test fun releasedOnThrow() {
        val file = File(tmp.root, "x.lock")
        val thrown = runCatching { withFileLock(file) { error("boom") } }.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue(tryLock(file))
    }

    @Test fun unopenableLockFileStillRunsTheBlock() {
        val dirInTheWay = File(tmp.root, "x.lock").apply { mkdirs() }
        assertEquals("ran", withFileLock(dirInTheWay) { "ran" })
    }

    @Test fun romCacheEntryLockSitsOutsideTheEntries() {
        val root = tmp.newFolder("roms")
        val lock = RomCache.lockFileOf(root, "game.zip.10")
        assertEquals(root, lock.parentFile.parentFile)
        RomCache.withEntryLock(root, "game.zip.10") { assertTrue(lock.exists()); assertEquals(false, tryLock(lock)) }
        assertTrue(tryLock(lock))
        assertNull(root.listFiles()!!.firstOrNull { !it.name.startsWith(".") })   // no entry-like name for the sweep to list
    }
}
