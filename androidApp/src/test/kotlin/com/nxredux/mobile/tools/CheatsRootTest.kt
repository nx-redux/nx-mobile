package com.nxredux.mobile.tools

import com.nxredux.mobile.cheats.db.CheatDbManifest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CheatsRootTest {
    private val base = Files.createTempDirectory("cheats").toFile()
    private val private = File(base, "sd").apply { mkdirs() }
    private val home = File(base, "home").apply { mkdirs() }

    @After fun cleanUp() { base.deleteRecursively() }

    private fun write(root: File, rel: String, text: String) = File(root, rel).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test fun movesPrivateCheatsAndTheManifestIntoTheHome() {
        write(private, "Cheats/GB/Tetris (World).cht", "a")
        write(private, "Cheats/PSP/Daxter [US] [UCUS-98618].cht", "b")
        write(private, CheatDbManifest.PATH, "manifest")
        write(private, "Saves/GB/Tetris (World).sav", "save")   // not cheats: stays

        assertEquals(3, CheatsRoot.moveFromPrivate(private, home))

        assertEquals("a", File(home, "Cheats/GB/Tetris (World).cht").readText())
        assertEquals("b", File(home, "Cheats/PSP/Daxter [US] [UCUS-98618].cht").readText())
        assertEquals("manifest", File(home, CheatDbManifest.PATH).readText())
        assertFalse(File(private, "Cheats").exists())                                  // emptied folders go too
        assertFalse(File(private, CheatDbManifest.PATH).exists())
        assertTrue(File(private, "Saves/GB/Tetris (World).sav").exists())
        assertEquals(0, CheatsRoot.moveFromPrivate(private, home))                      // nothing left: a no-op
    }

    @Test fun neverOverwritesAHomeFile() {
        write(private, "Cheats/GB/Same.cht", "same")
        write(private, "Cheats/GB/Mine.cht", "private")
        write(private, CheatDbManifest.PATH, "private manifest")
        write(home, "Cheats/GB/Same.cht", "same")
        write(home, "Cheats/GB/Mine.cht", "home")
        write(home, CheatDbManifest.PATH, "home manifest")

        assertEquals(0, CheatsRoot.moveFromPrivate(private, home))

        assertEquals("home", File(home, "Cheats/GB/Mine.cht").readText())
        assertEquals("home manifest", File(home, CheatDbManifest.PATH).readText())
        assertFalse(File(private, "Cheats/GB/Same.cht").exists())                        // an identical copy is dropped
        assertEquals("private", File(private, "Cheats/GB/Mine.cht").readText())          // a different one is kept
        assertFalse(File(private, CheatDbManifest.PATH).exists())                         // the home has its own database
    }

    @Test fun overAHomeDatabaseOnlyTheUsersOwnFilesMove() {
        val db = CheatDbManifest("stamp", 1L, listOf("Cheats/GB/Tetris (World).cht", "Cheats/GB/Gone.cht"), listOf("GB"))
        write(private, CheatDbManifest.PATH, db.render())
        write(private, "Cheats/GB/Tetris (World).cht", "old download")
        write(private, "Cheats/GB/Gone.cht", "old download")
        write(private, "Cheats/GB/Mine.cht", "mine")
        write(home, CheatDbManifest.PATH, "home manifest")
        write(home, "Cheats/GB/Tetris (World).cht", "new download")

        assertEquals(1, CheatsRoot.moveFromPrivate(private, home))

        assertEquals("mine", File(home, "Cheats/GB/Mine.cht").readText())
        assertFalse(File(home, "Cheats/GB/Gone.cht").exists())   // a download file the home's manifest doesn't list stays out
        assertEquals("new download", File(home, "Cheats/GB/Tetris (World).cht").readText())
        assertFalse(File(private, "Cheats").exists())             // the old download goes: the home has its own
        assertFalse(File(private, CheatDbManifest.PATH).exists())
    }

    @Test fun theHomeItselfIsNeverMovedIntoItself() {
        write(private, "Cheats/GB/A.cht", "a")
        assertEquals(0, CheatsRoot.moveFromPrivate(private, private))
        assertTrue(File(private, "Cheats/GB/A.cht").exists())
    }
}
