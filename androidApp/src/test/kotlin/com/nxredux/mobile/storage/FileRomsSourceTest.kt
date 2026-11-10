package com.nxredux.mobile.storage

import com.nxredux.mobile.library.Entry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileRomsSourceTest {
    @get:Rule val tmp = TemporaryFolder()
    private var granted = true
    private var mounted = true
    private val source = FileRomsSource({ granted }, { mounted })
    private val t0 = 1_700_000_000_000L

    private fun ref(f: File) = FileRefs.of(f.absolutePath)

    @Test fun lists_children_with_raw_child_refs_sizes_and_times() = runBlocking {
        val dir = tmp.newFolder("GB")
        val rom = File(dir, "Pokémon #1 (100%)?.gb").apply { writeBytes(ByteArray(3)); setLastModified(t0) }
        File(dir, ".media").mkdir()
        val listed = source.listChildren(ref(dir)).sortedBy { it.name }
        assertEquals(
            listOf(
                Entry(FileRefs.child(ref(dir), ".media"), ".media", true, null, File(dir, ".media").lastModified()),
                Entry(FileRefs.child(ref(dir), rom.name), rom.name, false, 3L, t0),
            ),
            listed,
        )
        assertEquals("file://${rom.absolutePath}", listed[1].ref.value)
    }

    @Test fun a_missing_folder_fails_as_gone() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { source.listChildren(ref(File(tmp.root, "gone"))) } }
    }

    @Test fun without_access_listing_fails_rather_than_listing_empty() {
        val dir = tmp.newFolder("Home")
        granted = false
        assertThrows(java.io.IOException::class.java) { runBlocking { source.listChildren(ref(dir)) } }
    }

    // The app's media folder (the home without All files access) lists, reads, writes and is online with access off;
    // any other file ref is still refused.
    @Test fun the_own_media_folder_works_without_access_and_others_do_not() = runBlocking {
        val media = tmp.newFolder("Android", "media", "com.nxredux.mobile.dev")
        File(media, "Roms").mkdir()
        File(media, "a.txt").writeText("hi")
        val other = tmp.newFolder("NXRedux")
        val own = FileRomsSource({ false }, { false }) { MediaHome.owns(media, it) }
        assertEquals(listOf("Roms", "a.txt"), own.listChildren(ref(media)).map { it.name }.sorted())
        assertEquals("hi", own.readText(FileRefs.child(ref(media), "a.txt")))
        assertFalse(own.isOffline(ref(media)))
        assertTrue(own.canWrite(ref(media)))
        assertTrue(own.writeText(FileRefs.child(ref(media), "Roms"), "b.txt", "x"))
        assertThrows(java.io.IOException::class.java) { runBlocking { own.listChildren(ref(other)) } }
        assertTrue(own.isOffline(ref(other)))
        assertFalse(own.canWrite(ref(other)))
        assertFalse(own.copyFile(ref(media), "a.txt", ref(other)))
        // A path that only starts like the media folder, or climbs out of it, is not the app's.
        assertFalse(MediaHome.owns(media, File(media.path + "-other")))
        assertFalse(MediaHome.owns(media, File(media, "../../x")))
        assertTrue(MediaHome.owns(media, File(media, "Roms/../Saves")))
    }

    @Test fun child_finds_by_name_and_refuses_other_paths() = runBlocking {
        val dir = tmp.newFolder("Roms")
        File(dir, "a #1.gb").writeText("x")
        assertEquals("a #1.gb", source.child(ref(dir), "a #1.gb")?.name)
        assertNull(source.child(ref(dir), "missing.gb"))
        assertNull(source.child(ref(dir), ".."))
        assertNull(source.child(ref(dir), "../Roms"))
    }

    @Test fun write_bytes_replaces_through_a_part_and_leaves_none() = runBlocking {
        val dir = tmp.newFolder("media")
        File(dir, "a.png").writeBytes(byteArrayOf(1))
        File(dir, "a.png.part").writeBytes(byteArrayOf(9, 9))   // left by a killed write
        assertTrue(source.writeBytes(ref(dir), "a.png", byteArrayOf(2, 3)))
        assertArrayEquals(byteArrayOf(2, 3), File(dir, "a.png").readBytes())
        assertEquals(listOf("a.png"), dir.list()!!.toList())
        File(dir, "folder").mkdir()
        assertFalse(source.writeBytes(ref(dir), "folder", byteArrayOf(1)))   // a folder is never replaced
        assertFalse(source.writeBytes(ref(dir), "../x.png", byteArrayOf(1)))
        assertEquals(setOf("a.png", "folder"), dir.list()!!.toSet())
    }

    @Test fun overlapping_writes_each_use_their_own_part_and_one_whole_text_wins() = runBlocking {
        val dir = tmp.newFolder("sidecars")
        val texts = (1..8).map { n -> "line $n\n".repeat(20_000) }
        kotlinx.coroutines.coroutineScope {
            texts.forEach { t -> launch(kotlinx.coroutines.Dispatchers.IO) { assertTrue(source.writeText(ref(dir), "map.txt", t)) } }
        }
        assertTrue(File(dir, "map.txt").readText() in texts)
        assertEquals(listOf("map.txt"), dir.list()!!.toList())
    }

    @Test fun a_part_left_by_a_killed_write_is_hidden_from_scans_and_sync() {
        val part = com.nxredux.mobile.runner.uniqueTempName("Tetris.gb", com.nxredux.mobile.library.Names.PART)
        assertTrue(part.startsWith("Tetris.gb.") && part != "Tetris.gb.part")
        assertTrue(com.nxredux.mobile.library.Names.isHidden(part))
        assertTrue(MirrorRules.isTransient(part))
    }

    @Test fun a_write_deletes_its_own_old_parts_but_not_fresh_ones_or_other_files_parts() = runBlocking {
        val dir = tmp.newFolder("stale")
        val old = File(dir, "map.txt.1a2b.part").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 3_600_000) }
        val fresh = File(dir, "map.txt.3c4d.part").apply { writeText("y") }
        val other = File(dir, "systems.txt.5e6f.part").apply { writeText("z"); setLastModified(System.currentTimeMillis() - 3_600_000) }
        val lookalike = File(dir, "map.txt.backup.part").apply { writeText("w"); setLastModified(System.currentTimeMillis() - 3_600_000) }
        assertTrue(source.writeText(ref(dir), "map.txt", "a\tb\n"))
        assertTrue(!old.exists())
        assertTrue(fresh.exists() && other.exists() && lookalike.exists())
    }

    @Test fun write_text_and_read_text_round_trip() = runBlocking {
        val dir = tmp.newFolder("Roms")
        assertTrue(source.writeText(ref(dir), "map.txt", "Tetris.gb\tTetris\n"))
        assertEquals("Tetris.gb\tTetris\n", source.readText(FileRefs.child(ref(dir), "map.txt")))
        assertNull(source.readText(FileRefs.child(ref(dir), "none.txt")))
    }

    @Test fun read_head_returns_the_leading_bytes() = runBlocking {
        val dir = tmp.newFolder("Roms")
        File(dir, "a.zip").writeBytes(byteArrayOf(1, 2, 3, 4))
        assertArrayEquals(byteArrayOf(1, 2), source.readHead(FileRefs.child(ref(dir), "a.zip"), 2))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), source.readHead(FileRefs.child(ref(dir), "a.zip"), 64))
        assertNull(source.readHead(FileRefs.child(ref(dir), "none.zip"), 2))
    }

    @Test fun rename_moves_a_file_unless_the_name_is_taken() = runBlocking {
        val dir = tmp.newFolder("Collections")
        File(dir, "a.txt").writeText("a"); File(dir, "b.txt").writeText("b")
        assertTrue(source.rename(ref(dir), "a.txt", "c.txt"))
        assertEquals("a", File(dir, "c.txt").readText())
        assertFalse(source.rename(ref(dir), "c.txt", "b.txt"))   // taken: both kept
        assertEquals("a", File(dir, "c.txt").readText()); assertEquals("b", File(dir, "b.txt").readText())
        assertFalse(source.rename(ref(dir), "none.txt", "d.txt"))
    }

    @Test fun delete_takes_files_only_and_empty_folders_only() = runBlocking {
        val dir = tmp.newFolder("Saves")
        File(dir, "a.srm").writeText("s")
        File(dir, "Empty").mkdir()
        File(dir, "Full").mkdir(); File(dir, "Full/x").writeText("x")
        assertFalse(source.delete(ref(dir), "Empty"))
        assertTrue(source.delete(ref(dir), "a.srm"))
        assertFalse(source.delete(ref(dir), "a.srm"))
        assertFalse(source.deleteEmptyDirectory(ref(dir), "Full"))
        assertTrue(File(dir, "Full/x").exists())
        assertTrue(source.deleteEmptyDirectory(ref(dir), "Empty"))
        assertEquals(listOf("Full"), dir.list()!!.toList())
    }

    @Test fun create_directory_makes_or_returns_a_folder_and_refuses_a_file_in_the_way() = runBlocking {
        val dir = tmp.newFolder("Home")
        assertEquals("Roms", source.createDirectory(ref(dir), "Roms")?.name)
        assertTrue(File(dir, "Roms").isDirectory)
        assertEquals(true, source.createDirectory(ref(dir), "Roms")?.isDirectory)
        File(dir, "Bios").writeText("not a folder")
        assertNull(source.createDirectory(ref(dir), "Bios"))
    }

    @Test fun without_access_or_volume_a_tree_is_offline_and_takes_no_writes() = runBlocking {
        val dir = tmp.newFolder("Home")
        assertFalse(source.isOffline(ref(dir))); assertTrue(source.canWrite(ref(dir)))
        granted = false
        assertTrue(source.isOffline(ref(dir))); assertFalse(source.canWrite(ref(dir)))
        granted = true; mounted = false
        assertTrue(source.isOffline(ref(dir)))
    }

    @Test fun display_name_is_the_last_segment_even_with_a_colon() {
        assertEquals("Roms: Best of", source.displayName(FileRefs.of("/storage/emulated/0/Roms: Best of")))
    }

    @Test fun range_reads_from_an_offset_and_stop_at_the_end() = runBlocking {
        val f = File(tmp.root, "d.bin").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        assertArrayEquals(byteArrayOf(10, 11, 12), source.readRange(ref(f), 10, 3))
        assertArrayEquals(byteArrayOf(98, 99), source.readRange(ref(f), 98, 10))
        assertEquals(0, source.readRange(ref(f), 200, 4)!!.size)
        assertNull(source.readRange(ref(File(tmp.root, "none.bin")), 0, 4))
    }

    @Test fun move_renames_into_the_target_and_refuses_a_taken_name() = runBlocking {
        val from = tmp.newFolder("x"); val to = tmp.newFolder("Roms")
        File(from, "a.gba").writeBytes(ByteArray(5))
        File(to, "b.gba").writeBytes(ByteArray(1))
        File(from, "b.gba").writeBytes(ByteArray(2))
        assertTrue(source.moveFile(ref(from), "a.gba", ref(to)))
        assertEquals(5L, File(to, "a.gba").length())
        assertFalse(File(from, "a.gba").exists())
        assertFalse(source.moveFile(ref(from), "b.gba", ref(to)))                   // taken: both stay
        assertEquals(1L, File(to, "b.gba").length()); assertEquals(2L, File(from, "b.gba").length())
    }

    @Test fun copy_copies_a_file_or_a_folder_whole_and_refuses_a_taken_name() = runBlocking {
        val from = tmp.newFolder("y"); val to = tmp.newFolder("Roms2")
        File(from, "g.cue").writeText("cue")
        val disc = File(from, "D").apply { mkdir() }
        File(disc, "D.m3u").writeText("m3u"); File(disc, "D.bin").writeBytes(ByteArray(7))
        assertTrue(source.copyFile(ref(from), "g.cue", ref(to)))
        assertEquals("cue", File(to, "g.cue").readText())
        assertTrue(File(from, "g.cue").exists())                                    // a copy: the original stays
        assertTrue(source.copyFile(ref(from), "D", ref(to)))
        assertEquals(7L, File(to, "D/D.bin").length())
        assertFalse(source.copyFile(ref(from), "g.cue", ref(to)))
    }

    @Test fun no_move_or_copy_without_all_files_access() = runBlocking {
        val from = tmp.newFolder("z"); val to = tmp.newFolder("Roms3")
        File(from, "a.gba").writeBytes(ByteArray(1))
        granted = false
        assertFalse(source.moveFile(ref(from), "a.gba", ref(to)))
        assertFalse(source.copyFile(ref(from), "a.gba", ref(to)))
    }
}
