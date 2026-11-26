package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SkeletonTest {
    @Test fun folders_cover_roms_and_bios_once_per_console_and_no_saves() {
        val f = Skeleton.folders()
        assertEquals("Roms", f.first())
        assertTrue("Roms/Game Boy Advance (GBA)" in f)
        assertTrue("Bios/GBA" in f)
        assertFalse("Roms/Game Boy Advance (MGBA)" in f)
        assertFalse("Bios/MGBA" in f)
        assertFalse("Roms/Sony PlayStation (PSX)" in f)
        assertFalse("Roms/Super Nintendo ES (SUPA)" in f)
        assertTrue("Roms/Nintendo DS (NDS)" in f)
        assertTrue("Roms/Nintendo ES (FC)" in f)
        assertTrue("Roms/Sony PlayStation (PS)" in f)
        assertTrue("Roms/Famicom (FC)" !in f)
        assertTrue("Roms/Sega CD (SEGACD)" in f); assertTrue("Bios/SEGACD" in f)
        assertTrue("Roms/Sega Saturn (SATURN)" in f); assertTrue("Bios/SATURN" in f)
        assertFalse(f.any { it.startsWith("Saves") })
        assertEquals(f.size, f.toSet().size)
        val n = CoreRegistry.consoles.size
        assertEquals(1 + n + 1 + n, f.size)
    }
    @Test fun ensure_creates_missing_folders_and_leaves_existing_ones() = runTest {
        val src = FakeRomsSource(dirs = mapOf("H" to listOf("Roms"), "H/Roms" to listOf("Game Boy (GB)", "Stuff"), "H/Roms/Game Boy (GB)" to listOf("t.gb"), "H/Roms/Stuff" to emptyList()))
        assertTrue(Skeleton.ensure(src, ContentRef("H")))
        assertTrue("H/Roms" !in src.written)
        assertTrue("H/Roms/Game Boy (GB)" !in src.written)
        assertTrue("H/Roms/Game Boy Advance (GBA)" in src.written)
        assertTrue("H/Roms/Game Boy Advance (MGBA)" !in src.written)
        assertTrue("H/Bios/PSP" in src.written)
        assertTrue("H/Saves" !in src.written)
        assertEquals(listOf("t.gb"), src.listChildren(ContentRef("H/Roms/Game Boy (GB)")).map { it.name })
        assertTrue("Stuff" in src.listChildren(ContentRef("H/Roms")).map { it.name })
    }
    @Test fun ensure_does_not_add_a_second_folder_for_a_tag_a_pre_rename_home_already_has() = runTest {
        val src = FakeRomsSource(dirs = mapOf(
            "H" to listOf("Roms"),
            "H/Roms" to listOf("Famicom (FC)", "Sega Mega Drive (md)"),
            "H/Roms/Famicom (FC)" to listOf("smb.nes"),
            "H/Roms/Sega Mega Drive (md)" to emptyList(),
        ))
        assertTrue(Skeleton.ensure(src, ContentRef("H")))
        assertTrue("H/Roms/Nintendo ES (FC)" !in src.written)
        assertTrue("H/Roms/Sega Genesis (MD)" !in src.written)   // the tag matches whatever its case
        assertTrue("H/Roms/Super Nintendo ES (SFC)" in src.written)
        assertTrue("H/Roms/Game Boy (GB)" in src.written)
        assertTrue("H/Bios/FC" in src.written)
        assertTrue("H/Bios/MD" in src.written)
        assertEquals(listOf("Famicom (FC)", "Sega Mega Drive (md)"), src.listChildren(ContentRef("H/Roms")).map { it.name }.take(2))
    }
    @Test fun ensure_lists_roms_once_when_folders_are_missing_and_never_on_a_complete_home() = runTest {
        val src = FakeRomsSource(dirs = mapOf("H" to listOf("Roms"), "H/Roms" to listOf("Famicom (FC)"), "H/Roms/Famicom (FC)" to emptyList()))
        val counting = CountingRomsSource(src)
        assertTrue(Skeleton.ensure(counting, ContentRef("H")))
        assertEquals(listOf("listChildren:H/Roms"), counting.calls.filter { it.startsWith("listChildren:") })   // once, for every missing folder
        val fresh = CountingRomsSource(FakeRomsSource(dirs = mapOf("H" to emptyList())))
        assertTrue(Skeleton.ensure(fresh, ContentRef("H")))
        fresh.calls.clear()
        assertTrue(Skeleton.ensure(fresh, ContentRef("H")))
        assertEquals(emptyList(), fresh.calls.filter { it.startsWith("listChildren:") })
    }
    @Test fun ensure_reports_failure_on_a_read_only_tree() = runTest {
        val src = FakeRomsSource(dirs = mapOf("H" to emptyList()), writable = false)
        assertFalse(Skeleton.ensure(src, ContentRef("H")))
    }

    @Test fun a_bricks_sega_cd_gpgx_folder_stops_a_second_gpgx_folder() = runTest {
        val src = FakeRomsSource(dirs = mapOf("H" to listOf("Roms"), "H/Roms" to listOf("Sega CD (GPGX)"), "H/Roms/Sega CD (GPGX)" to emptyList()))
        assertTrue(Skeleton.ensure(src, ContentRef("H")))
        assertTrue("H/Roms/Sega Genesis (GPGX)" !in src.written)
        assertTrue("H/Roms/Sega Genesis (MD)" in src.written)
        assertTrue("H/Roms/Sega CD (SEGACD)" in src.written)
    }
    @Test fun an_md_home_gets_no_gpgx_folder_beside_it() = runTest {
        val src = FakeRomsSource(dirs = mapOf("H" to listOf("Roms"), "H/Roms" to listOf("Sega Genesis (MD)"), "H/Roms/Sega Genesis (MD)" to listOf("Sonic.md")))
        assertTrue(Skeleton.ensure(src, ContentRef("H")))
        assertTrue("H/Roms/Sega Genesis (GPGX)" !in src.written)
        assertTrue("H/Bios/GPGX" !in src.written)
        assertTrue("H/Roms/Sega Genesis (MD)" !in src.written)
        assertEquals(listOf("Sonic.md"), src.listChildren(ContentRef("H/Roms/Sega Genesis (MD)")).map { it.name })
    }
}
