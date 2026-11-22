package com.nxredux.mobile.storage

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.SourceConversion
import com.nxredux.mobile.settings.Sources
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import java.nio.file.Files

class AndroidStorageUpgradeTest {
    private val base = Files.createTempDirectory("upgrade").toFile()

    @After fun cleanUp() { base.deleteRecursively() }

    private fun write(root: File, rel: String, text: String, mtime: Long = 1_000_000L) =
        File(root, rel).apply { parentFile!!.mkdirs(); writeText(text); setLastModified(mtime) }

    // The ref as DocumentsContract.buildDocumentUriUsingTree(tree, "primary:NXRedux/Roms") writes it.
    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ANXRedux"
    private val romsRef = ContentRef("$tree/document/primary%3ANXRedux%2FRoms")

    @Test fun adoptedExtraPointsAtRoms() {
        val extra = AndroidStorageUpgrade.adoptedExtra(romsRef, "Roms", taken = emptyList())
        val docId = URLDecoder.decode(extra.ref.value.substringAfter("/document/"), "UTF-8")
        assertTrue(docId, docId.endsWith("/Roms"))
        assertTrue(extra.id.startsWith("Roms-"))
        // Its path is the old Roms folder, never the old home's root (whose Bios, Saves and .userdata it would scan):
        // as the overlap check sees it, and as All files access converts it.
        assertEquals("/storage/emulated/0/NXRedux/Roms", Sources.mappedPath(extra.ref))
        val converted = runBlocking { SourceConversion.convert(null, listOf(extra)) { true } }
        assertEquals(FileRefs.of("/storage/emulated/0/NXRedux/Roms"), converted.extras.single().ref)
        assertEquals(extra.id, converted.extras.single().id)
        // A taken id gets the next free one, as an added extra's does.
        assertNotEquals(extra.id, AndroidStorageUpgrade.adoptedExtra(romsRef, "Roms", taken = listOf(extra.id)).id)
    }

    @Test fun keysRewrittenInRecentsShortcutsHiddenAndCollections() {
        val private = File(base, "sd")
        val media = File(base, "media")
        write(private, NxPaths.RECENTS, "/Roms/Game Boy (GB)/Tetris.gb\tTetris\n/Extra/loose-1/Zelda.gba\n")
        write(private, NxPaths.SHORTCUTS, "/Roms/Game Boy Advance (GBA)/Zelda.gba\n")
        write(private, NxPaths.HIDDEN, "/Roms/Game Boy (GB)/Bad.gb\n")
        val copied = write(media, "Collections/Favourites.txt", "/Roms/Game Boy (GB)/Tetris.gb\n")
        val own = write(media, "Collections/Mine.txt", "/Roms/Game Boy (GB)/Kept.gb\n")   // the home's own: not copied

        AndroidStorageUpgrade.rewriteKeys(private, listOf(copied), "Roms-1")

        assertEquals("/Extra/Roms-1/Game Boy (GB)/Tetris.gb\tTetris\n/Extra/loose-1/Zelda.gba\n", File(private, NxPaths.RECENTS).readText())
        assertEquals("/Extra/Roms-1/Game Boy Advance (GBA)/Zelda.gba\n", File(private, NxPaths.SHORTCUTS).readText())
        assertEquals("/Extra/Roms-1/Game Boy (GB)/Bad.gb\n", File(private, NxPaths.HIDDEN).readText())
        assertEquals("/Extra/Roms-1/Game Boy (GB)/Tetris.gb\n", copied.readText())
        assertEquals("/Roms/Game Boy (GB)/Kept.gb\n", own.readText())
        assertEquals(1_000_000L, File(private, NxPaths.RECENTS).lastModified())   // the move's clash rule still sees the real time
    }

    // The old home picked again after a failed decline: its adopted extra's keys become home keys again.
    @Test fun keysRestoredWhenTheOldHomeIsPickedBack() {
        val private = File(base, "sd")
        write(private, NxPaths.RECENTS, "/Extra/Roms-1/Game Boy (GB)/Tetris.gb\tTetris\n/Extra/loose-1/Zelda.gba\n")
        write(private, NxPaths.HIDDEN, "/Extra/Roms-1/Game Boy (GB)/Bad.gb\n")

        AndroidStorageUpgrade.restoreKeys(private, "Roms-1")

        assertEquals("/Roms/Game Boy (GB)/Tetris.gb\tTetris\n/Extra/loose-1/Zelda.gba\n", File(private, NxPaths.RECENTS).readText())
        assertEquals("/Roms/Game Boy (GB)/Bad.gb\n", File(private, NxPaths.HIDDEN).readText())
        assertEquals(1_000_000L, File(private, NxPaths.RECENTS).lastModified())
    }
}
