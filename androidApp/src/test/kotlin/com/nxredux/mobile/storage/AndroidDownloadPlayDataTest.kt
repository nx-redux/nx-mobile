package com.nxredux.mobile.storage

import java.io.File
import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class AndroidDownloadPlayDataTest {
    private fun put(f: File, bytes: Int) = f.apply { parentFile.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test fun both_consoles_kept_games_are_sized_and_cleared() {
        val files = Files.createTempDirectory("files").toFile()
        val home = Files.createTempDirectory("home").toFile()
        val azahar = File(home, "Saves/3DS/Azahar")
        put(File(files, "system/melonDS DS/Download Play/00000390.bin"), 600)
        put(File(azahar, "nand/00000000000000000000000000000000/title/00040001/00030800/content/00000000.app"), 2500)
        put(File(azahar, "sdmc/nx-redux/children/0004000100030800.txt"), 9)
        val key = put(File(azahar, "sysdata/nx_dlp_child.key"), 16)
        val game = put(File(azahar, "nand/00000000000000000000000000000000/title/00040010/00021800/content/a.app"), 100)
        val data = AndroidDownloadPlayData(files) { home }
        assertEquals(3109L, data.sizeBytes())
        assertEquals(3109L, data.clear())
        assertEquals(0L, data.sizeBytes())
        assertFalse(File(files, "system/melonDS DS/Download Play").exists())
        assertFalse(File(azahar, "nand/00000000000000000000000000000000/title/00040001").exists())
        assertTrue(key.isFile)    // the phone's own 3DS key stays
        assertTrue(game.isFile)   // installed titles that are not Download Play's stay
    }

    @Test fun nothing_kept_is_nothing_to_clear() {
        val data = AndroidDownloadPlayData(Files.createTempDirectory("f").toFile()) { Files.createTempDirectory("h").toFile() }
        assertEquals(0L, data.sizeBytes())
        assertEquals(0L, data.clear())
    }
}
