package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.NxPaths
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AndroidEmulatorConfigStoreTest {
    private val root: File = Files.createTempDirectory("emucfg").toFile()
    private val store = AndroidEmulatorConfigStore { root }
    private val paths = NxPaths("GBA", "mgba", "Game.gba")
    private fun file(rel: String) = File(root, rel)

    @After fun cleanup() { root.deleteRecursively() }

    @Test fun absentFilesReadAsNull() = runBlocking {
        assertEquals(null to null, store.read("GBA", "mgba", "Game.gba"))
        assertEquals(null to null, store.read("GBA", "mgba", null))
    }

    @Test fun writesCreateMissingDirsAndReadBack() = runBlocking {
        store.writeConsole("GBA", "mgba", "a = 1\n")
        store.writeGame("GBA", "mgba", "Game.gba", "b = 2\n")
        assertEquals("a = 1\n", file(paths.consoleConfig).readText())
        assertEquals("b = 2\n", file(paths.gameConfig).readText())
        val (console, game) = store.read("GBA", "mgba", "Game.gba")
        assertEquals("1", console!!.values["a"])
        assertEquals("2", game!!.values["b"])
        // no ROM: game side is null even when a game file exists
        assertNull(store.read("GBA", "mgba", null).second)
    }

    @Test fun unreadableExistingFileThrows() = runBlocking {
        val f = file(paths.consoleConfig)
        f.mkdirs()   // a directory where the file should be: exists, but can't be read as text
        try {
            store.read("GBA", "mgba", "Game.gba")
            fail("expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test fun deleteConsoleRemovesOnlyConsoleFile() = runBlocking {
        store.writeConsole("GBA", "mgba", "a = 1\n")
        store.writeGame("GBA", "mgba", "Game.gba", "b = 2\n")
        store.deleteConsole("GBA", "mgba")
        assertFalse(file(paths.consoleConfig).exists())
        assertTrue(file(paths.gameConfig).exists())
        store.deleteConsole("GBA", "mgba")   // absent: no error
    }

    @Test fun deleteGameRemovesOnlyGameFile() = runBlocking {
        store.writeConsole("GBA", "mgba", "a = 1\n")
        store.writeGame("GBA", "mgba", "Game.gba", "b = 2\n")
        store.deleteGame("GBA", "mgba", "Game.gba")
        assertFalse(file(paths.gameConfig).exists())
        assertTrue(file(paths.consoleConfig).exists())
        store.deleteGame("GBA", "mgba", "Game.gba")   // absent: no error
    }
}
