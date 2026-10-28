package com.nxredux.mobile.tools

import com.nxredux.mobile.cheats.db.CheatDatabase
import com.nxredux.mobile.cheats.db.CheatDbArchive
import com.nxredux.mobile.cheats.db.CheatDbException
import com.nxredux.mobile.cheats.db.CheatDbInstall
import com.nxredux.mobile.cheats.db.CheatDbSource
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CheatDbFilesTest {
    private val base = Files.createTempDirectory("cheatdb").toFile()
    private val sd = File(base, "sd").apply { mkdirs() }

    private fun zip(name: String, entries: Map<String, String>): File = File(base, name).apply {
        ZipOutputStream(outputStream()).use { out ->
            for ((path, text) in entries) { out.putNextEntry(ZipEntry(path)); out.write(text.toByteArray()); out.closeEntry() }
        }
    }

    private val cheats = zip("cheats.zip", mapOf(
        "Nintendo - Game Boy Advance/Advance Wars (USA).cht" to "gba",
        "Nintendo - Game Boy Advance/readme.txt" to "not a cheat",
    ))
    // patches.zip as PCSX2 publishes it: every game's .pnach at the top level.
    private val patches = zip("patches.zip", mapOf(
        "SLUS-21678_428113C2.pnach" to "[Widescreen 16:9]",
        "6D980D22.pnach" to "crc only",
        "notes.txt" to "skipped",
        "sub/NESTED.pnach" to "skipped: not at the top level",
    ))

    private fun installs(patchesZip: File = patches, cheatsZip: File = cheats, optionalPatches: Boolean = false) = listOf(
        CheatDbInstall(CheatDbArchive(cheatsZip.path, "s", CheatDbSource.LIBRETRO), mapOf("GBA" to listOf("Nintendo - Game Boy Advance"))),
        CheatDbInstall(CheatDbArchive(patchesZip.path, "p", CheatDbSource.PCSX2), CheatDatabase.PATCH_FOLDERS, optional = optionalPatches),
    )

    @Test fun installsLibretroCheatsAndPcsx2PatchesIntoTheConsolesFolders() = runBlocking {
        val progress = ArrayList<Triple<String, Int, Int>>()
        val result = CheatDbFiles.install(sd, installs()) { tag, i, n -> progress += Triple(tag, i, n) }
        assertEquals(emptyList<CheatDbSource>(), result.failed)
        val written = result.files
        assertEquals(
            listOf("Cheats/GBA/Advance Wars (USA).cht", "Cheats/PS2/Patches/SLUS-21678_428113C2.pnach", "Cheats/PS2/Patches/6D980D22.pnach"),
            written,
        )
        assertEquals("[Widescreen 16:9]", File(sd, "Cheats/PS2/Patches/SLUS-21678_428113C2.pnach").readText())
        assertEquals(listOf(Triple("GBA", 1, 2), Triple("PS2", 2, 2)), progress)   // counted over both archives
        assertFalse(File(sd, "Cheats/PS2/Patches/notes.txt").exists())
        assertFalse(File(sd, "Cheats/PS2/Patches/NESTED.pnach").exists())
        assertFalse(File(sd, "Cheats/PS2/SLUS-21678_428113C2.pnach").exists())   // patches stay out of the cheats folder
    }

    @Test fun aCorruptSecondArchiveTakesBackTheFirstsNewFilesButNotTheUsers() = runBlocking {
        val mine = File(sd, "Cheats/PS2/SLUS-21678_mine.pnach").apply { parentFile!!.mkdirs(); writeText("my cheats") }
        val corrupt = File(base, "corrupt.zip").apply { writeText("not a zip") }
        try {
            CheatDbFiles.install(sd, installs(corrupt)) { _, _, _ -> }
            fail("expected CORRUPT")
        } catch (e: CheatDbException) {
            assertEquals(CheatDbException.Kind.CORRUPT, e.kind)
        }
        assertFalse(File(sd, "Cheats/GBA/Advance Wars (USA).cht").exists())
        assertFalse(File(sd, "Cheats/GBA").exists())
        assertEquals("my cheats", mine.readText())
    }

    @Test fun aCorruptOptionalPatchesArchiveKeepsTheCheatsAndThePreviousPatches() = runBlocking {
        val old = File(sd, "Cheats/PS2/Patches/OLD.pnach").apply { parentFile!!.mkdirs(); writeText("previous install") }
        val corrupt = File(base, "corrupt.zip").apply { writeText("not a zip") }
        val result = CheatDbFiles.install(sd, installs(corrupt, optionalPatches = true)) { _, _, _ -> }
        assertEquals(listOf("Cheats/GBA/Advance Wars (USA).cht"), result.files)
        assertEquals(listOf(CheatDbSource.PCSX2), result.failed)
        assertTrue(File(sd, "Cheats/GBA/Advance Wars (USA).cht").exists())
        assertEquals("previous install", old.readText())
    }

    @Test fun anOptionalArchiveFailingMidwayTakesBackOnlyItsOwnNewFiles() = runBlocking {
        // The second patch's target is a folder, so writing it fails after the first patch was written.
        File(sd, "Cheats/PS2/Patches/6D980D22.pnach").mkdirs()
        val result = CheatDbFiles.install(sd, installs(optionalPatches = true)) { _, _, _ -> }
        assertEquals(listOf("Cheats/GBA/Advance Wars (USA).cht"), result.files)
        assertEquals(listOf(CheatDbSource.PCSX2), result.failed)
        assertTrue(File(sd, "Cheats/GBA/Advance Wars (USA).cht").exists())
        assertFalse(File(sd, "Cheats/PS2/Patches/SLUS-21678_428113C2.pnach").exists())
    }

    @Test fun aCorruptCheatsArchiveFailsTheInstallAndWritesNothing() = runBlocking {
        val corrupt = File(base, "corrupt.zip").apply { writeText("not a zip") }
        try {
            CheatDbFiles.install(sd, installs(cheatsZip = corrupt, optionalPatches = true)) { _, _, _ -> }
            fail("expected CORRUPT")
        } catch (e: CheatDbException) {
            assertEquals(CheatDbException.Kind.CORRUPT, e.kind)
        }
        assertFalse(File(sd, "Cheats").exists() && File(sd, "Cheats").list()!!.isNotEmpty())
    }

    @Test fun pruningRemovesEmptiedPatchAndConsoleFoldersOnly() {
        val patch = File(sd, "Cheats/PS2/Patches/A.pnach").apply { parentFile!!.mkdirs(); writeText("x") }
        val gba = File(sd, "Cheats/GBA/a.cht").apply { parentFile!!.mkdirs(); writeText("x") }
        File(sd, "Cheats/GBA/mine.cht").writeText("user")
        patch.delete(); gba.delete()
        CheatDbFiles.pruneFolders(listOf(patch, gba), sd)
        assertFalse(File(sd, "Cheats/PS2").exists())        // Patches/ emptied, then PS2/
        assertTrue(File(sd, "Cheats/GBA/mine.cht").exists())  // the user's file keeps its folder
        assertTrue(File(sd, "Cheats").isDirectory)            // never Cheats/ itself
    }
}
