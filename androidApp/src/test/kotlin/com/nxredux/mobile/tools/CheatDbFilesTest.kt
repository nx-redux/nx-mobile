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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CheatDbFilesTest {
    private val base = Files.createTempDirectory("cheatdb").toFile()
    private val sd = File(base, "sd").apply { mkdirs() }

    @After fun cleanUp() { base.deleteRecursively() }

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

    @Test fun deleteRemovesOnlyFilesInsideCheats() {
        File(sd, "Cheats/GB").mkdirs(); File(sd, "Saves/GBA").mkdirs()
        val cheat = File(sd, "Cheats/GB/x.cht").apply { writeText("c") }
        val save = File(sd, "Saves/GBA/game.srm").apply { writeText("s") }
        val outside = File(base, "outside.cht").apply { writeText("o") }
        CheatDbFiles.delete(sd, listOf("Cheats/GB/x.cht", "Saves/GBA/game.srm", "Cheats/../Saves/GBA/game.srm", "../outside.cht", outside.path))
        assertFalse(cheat.exists())
        assertTrue(save.exists())
        assertTrue(outside.exists())
    }

    private fun file(rel: String, text: String = "x") = File(sd, rel).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test fun takeBackMovesAFolderOfOnlyTheseFilesToTheTrashInOneGo() {
        val ours = (1..50).map { file("Cheats/GBA/g$it.cht") } + file("Cheats/PS2/Patches/A.pnach")
        val progress = ArrayList<Pair<Int, Int>>()
        CheatDbFiles.takeBack(sd, ours) { d, t -> progress += d to t }
        assertFalse(File(sd, "Cheats/GBA").exists())
        assertFalse(File(sd, "Cheats/PS2").exists())
        assertTrue(File(sd, "Cheats").isDirectory)
        assertEquals(51, File(sd, CheatDbFiles.TRASH).walkTopDown().count { it.isFile })   // waiting for the sweep
        assertEquals(51 to 51, progress.last())
        CheatDbFiles.emptyTrash(sd)
        assertFalse(File(sd, CheatDbFiles.TRASH).exists())
    }

    @Test fun takeBackLeavesTheUsersFilesAndDeletesOursBesideThemOneByOne() {
        val mine = file("Cheats/GBA/mine.cht", "user")
        val minePatch = file("Cheats/PS2/Patches/MINE.pnach", "user")
        val ours = (1..450).map { file("Cheats/GBA/g$it.cht") } + file("Cheats/PS2/Patches/A.pnach") + file("Cheats/FC/f.cht")
        val progress = ArrayList<Pair<Int, Int>>()
        CheatDbFiles.takeBack(sd, ours) { d, t -> progress += d to t }
        assertEquals("user", mine.readText())
        assertEquals("user", minePatch.readText())
        assertTrue(ours.none { it.exists() })
        assertFalse(File(sd, "Cheats/FC").exists())   // FC held only ours: moved whole
        assertEquals(452 to 452, progress.last())
        assertTrue(progress.size >= 3)   // reported along the way, not only at the end
    }

    @Test fun takeBackNeverMovesAFolderHoldingALink() {
        val outside = File(base, "outside").apply { mkdirs() }
        File(outside, "keep.cht").writeText("keep")
        val ours = file("Cheats/GBA/g.cht")
        Files.createSymbolicLink(File(sd, "Cheats/GBA/linked").toPath(), outside.toPath())
        CheatDbFiles.takeBack(sd, listOf(ours)) { _, _ -> }
        CheatDbFiles.emptyTrash(sd)
        assertFalse(ours.exists())
        assertTrue(File(sd, "Cheats/GBA/linked").exists())
        assertEquals("keep", File(outside, "keep.cht").readText())
    }

    @Test fun emptyTrashDeletesALinkNotWhatItPointsTo() {
        val outside = File(base, "outside").apply { mkdirs() }
        File(outside, "keep.cht").writeText("keep")
        val trash = File(sd, "${CheatDbFiles.TRASH}/x").apply { mkdirs() }
        Files.createSymbolicLink(File(trash, "link").toPath(), outside.toPath())
        CheatDbFiles.emptyTrash(sd)
        assertFalse(File(sd, CheatDbFiles.TRASH).exists())
        assertEquals("keep", File(outside, "keep.cht").readText())
    }

    @Test fun removeTakesOutOnlyTheListedFiles() {
        val mine = file("Cheats/GB/mine.cht", "user")
        file("Cheats/GB/x.cht"); file("Cheats/GBA/a.cht"); file("Saves/GBA/game.srm", "s")
        CheatDbFiles.remove(sd, listOf("Cheats/GB/x.cht", "Cheats/GBA/a.cht", "Saves/GBA/game.srm")) { _, _ -> }
        assertEquals("user", mine.readText())
        assertFalse(File(sd, "Cheats/GB/x.cht").exists())
        assertFalse(File(sd, "Cheats/GBA").exists())
        assertTrue(File(sd, "Saves/GBA/game.srm").exists())
    }

    @Test fun aCancelledInstallLeavesOnlyTheUsersFilesInCheats() = runBlocking {
        val mine = file("Cheats/PS2/Patches/MINE.pnach", "user")
        val stop = Job()
        // Cancelled once the cheats are written and the patches start.
        val result = runCatching { withContext(stop) { CheatDbFiles.install(sd, installs()) { tag, _, _ -> if (tag == "PS2") stop.cancel() } } }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals("user", mine.readText())
        assertEquals(listOf("MINE.pnach"), File(sd, "Cheats").walkTopDown().filter { it.isFile }.map { it.name }.toList())
        assertTrue(File(sd, CheatDbFiles.TRASH).walkTopDown().any { it.name == "Advance Wars (USA).cht" })   // moved, not deleted inline
    }
}
