package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RaHomeTreeTest {
    private val gba = RaGamePointer("GBA", "home", "Roms/Game Boy Advance (GBA)", "E.gba", "E")

    @Test fun badge_folder_names() {
        assertEquals(listOf("Roms", "GBA (GBA)", ".retroachievements", "badges", "1485"), RaBadgeFolders.segments("GBA (GBA)", 1485))
        assertNull(RaBadgeFolders.segments("GBA (GBA)", 0))
        assertEquals("00012_lock.png", RaBadgeFolders.fileName("00012", locked = true))
        assertNull(RaBadgeFolders.fileName("../x", locked = false))
    }
    @Test fun a_home_rom_uses_its_top_console_folder() {
        assertEquals("Game Boy Advance (GBA)", RaBadgeFolders.ownFolder(gba))
        assertEquals("PS", RaBadgeFolders.ownFolder(gba.copy(tag = "PS", dir = "Roms/PS/FF7 (USA)")))
        assertNull(RaBadgeFolders.ownFolder(gba.copy(dir = "Roms")))
        assertNull(RaBadgeFolders.ownFolder(gba.copy(dir = "Roms/.sources/x")))
        assertNull(RaBadgeFolders.ownFolder(gba.copy(source = "x1", dir = "a")))
    }
    @Test fun an_extra_rom_uses_the_folder_for_its_tag() = runTest {
        val home = RaHomeTree(raHomeSource("Famicom (FC)")) { ContentRef("home") }
        assertEquals("Famicom (FC)", home.consoleFolder(gba.copy(tag = "FC", source = "x1", dir = "a")))   // existing folder with the tag
        assertEquals("Game Boy Advance (GBA)", home.consoleFolder(gba.copy(source = "x1", dir = "a")))      // display name, created on write
        assertEquals("Game Boy Advance (GBA)", home.consoleFolder(gba.copy(dir = "Roms")))                   // a home ROM directly in Roms/
    }
    @Test fun an_extra_rom_of_a_second_emulator_uses_the_console_folder() = runTest {
        assertEquals("Game Boy Advance (GBA)", RaHomeTree(raHomeSource("Game Boy Advance (GBA)")) { ContentRef("home") }.folderForTag("MGBA"))
        assertEquals("Game Boy Advance (GBA)", RaHomeTree(raHomeSource()) { ContentRef("home") }.folderForTag("MGBA"))   // none there: the console-tag display name
    }
    @Test fun write_creates_folders_and_listing_maps_names_to_models() = runTest {
        val src = raHomeSource(); val home = RaHomeTree(src) { ContentRef("home") }
        val seg = RaBadgeFolders.segments("Game Boy Advance (GBA)", 7)!!
        val model = home.writeBytes(seg, "1.png", byteArrayOf(1))
        assertEquals("home/Roms/Game Boy Advance (GBA)/.retroachievements/badges/7/1.png", model)
        assertEquals(mapOf("1.png" to model), home.listing(seg))
        assertTrue(home.has(seg, "1.png"))
    }
    @Test fun no_home_means_no_folder_and_no_writes() = runTest {
        val home = RaHomeTree(raHomeSource()) { null }
        assertNull(home.folderForTag("GBA"))
        assertNull(home.writeBytes(listOf("Roms", "X (GBA)"), "a.png", byteArrayOf(1)))
        assertEquals(emptyMap(), home.listing(listOf("Roms")))
        assertNull(home.readText(listOf("Roms"), "hashes.json"))
    }
    @Test fun a_display_name_fallback_is_not_remembered() = runTest {
        val src = raHomeSource(); val home = RaHomeTree(src) { ContentRef("home") }
        assertEquals("Game Boy Advance (GBA)", home.folderForTag("GBA"))   // none there yet: the name a write would create
        src.createDirectory(ContentRef("home/Roms"), "My GBA (GBA)")
        assertEquals("My GBA (GBA)", home.folderForTag("GBA"))
    }
    @Test fun found_folders_are_remembered_per_home() = runTest {
        val src = FakeRomsSource(mapOf("a" to listOf("Roms"), "a/Roms" to listOf("A (GBA)"), "a/Roms/A (GBA)" to emptyList(),
            "b" to listOf("Roms"), "b/Roms" to listOf("B (GBA)"), "b/Roms/B (GBA)" to emptyList()))
        var ref = "a"; val home = RaHomeTree(src) { ContentRef(ref) }
        assertEquals("A (GBA)", home.folderForTag("GBA"))
        ref = "b"; assertEquals("B (GBA)", home.folderForTag("gba"))
    }
}
