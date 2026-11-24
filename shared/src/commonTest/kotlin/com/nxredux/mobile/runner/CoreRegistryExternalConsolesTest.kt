package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.CONSOLE_FRAMES
import com.nxredux.mobile.library.IndexedGame
import com.nxredux.mobile.library.IndexedSystem
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.Resolution
import com.nxredux.mobile.library.Skeleton
import com.nxredux.mobile.library.disc.DataSectors
import com.nxredux.mobile.library.disc.DiscKind
import com.nxredux.mobile.library.disc.DiscSignatures
import com.nxredux.mobile.library.playableSystems
import com.nxredux.mobile.storage.Recents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The consoles with no bundled core: their games open in other emulators. */
class CoreRegistryExternalConsolesTest {
    private val external = mapOf(
        "SWITCH" to "switch", "WIIU" to "wiiu", "PS3" to "ps3", "PSVITA" to "psvita", "PS4" to "ps4", "SATURN" to "saturn",
        "XBOX" to "xbox", "XBOX360" to "xbox360", "JAGUAR" to "jaguar", "NEOCD" to "neocd", "CDI" to "cdi", "N64DD" to "n64dd", "SGX" to "sgx",
    )

    @Test fun newConsolesHaveNoCoreAndAreExternalOnly() {
        for ((tag, id) in external) {
            assertNull(CoreRegistry.forTag(tag), tag)
            val c = CoreRegistry.consoleOf(tag)!!
            assertEquals(id, c.id, tag)
            assertEquals(listOf(tag), c.tags, tag)
            assertTrue(c.externalOnly, tag)
        }
        assertEquals(external.values.toSet(), CoreRegistry.consoles.filter { it.externalOnly }.map { it.id }.toSet())
    }

    @Test fun aliasesNameTheNewConsoles() {
        for ((alias, id) in listOf("ns" to "switch", "wii u" to "wiiu", "vita" to "psvita", "psv" to "psvita", "ss" to "saturn",
            "jag" to "jaguar", "ngcd" to "neocd", "cd-i" to "cdi", "64dd" to "n64dd", "supergrafx" to "sgx", "nintendo switch" to "switch")) {
            assertEquals(id, CoreRegistry.consoleByAlias(alias)?.id, alias)
        }
        assertEquals("wiiu", CoreRegistry.consoleOfFolder("Wii U", "WIIU")?.id)
        assertEquals("wii", CoreRegistry.consoleOfFolder("Nintendo Wii", "WII")?.id)
    }

    @Test fun skeletonCreatesTheirFolders() {
        val f = Skeleton.folders()
        assertTrue("Roms/Sega Saturn (SATURN)" in f)
        assertTrue("Bios/SATURN" in f)
        assertTrue("Roms/Nintendo Switch (SWITCH)" in f)
        assertTrue("Bios/SWITCH" in f)
    }

    @Test fun isoAloneNeverPicksANewConsole() {
        assertTrue("iso" in CoreRegistry.GENERIC_EXTENSIONS)
        assertEquals(emptyList(), CoreRegistry.consolesForExtension("iso"))
        assertEquals(listOf("switch"), CoreRegistry.consolesForExtension("nsp").map { it.id })
        assertEquals(listOf("wiiu"), CoreRegistry.consolesForExtension("wua").map { it.id })
        assertEquals("SWITCH", Resolution.byExtension("Zelda.nsp", null) { it.defaultTag })
        assertEquals("WIIU", Resolution.byExtension("Mario Kart 8.wua", null) { it.defaultTag })
        assertNull(Resolution.byExtension("Game.iso", null) { it.defaultTag })
        assertNull(Resolution.byExtension("Game.pkg", null) { it.defaultTag })   // PS3 and PS4 both: by folder only
        assertNull(Resolution.byExtension("Game.rom", null) { it.defaultTag })   // Jaguar's, by folder only
    }

    @Test fun newConsolesTakeTheirGamesByFolder() {
        val saturn = Resolution.FolderHints(listOf("SATURN"))
        assertEquals("SATURN", Resolution.byFolder("Nights.cue", null, saturn))
        assertEquals("SATURN", Resolution.byFolder("Nights.zip", null, saturn))
        assertNull(Resolution.byFolder("readme.txt", null, saturn))
        assertEquals("PS4", Resolution.byFolder("Bloodborne.pkg", null, Resolution.FolderHints(listOf("PS4"))))
        assertEquals("JAGUAR", Resolution.byFolder("Tempest 2000.rom", null, Resolution.FolderHints(listOf("JAGUAR"))))
        assertTrue(Resolution.isCandidate("Bloodborne.pkg"))
    }

    @Test fun saturnDiscPlacesInSaturn() {
        val s0 = ByteArray(2048).also { "SEGA SEGASATURN ".encodeToByteArray().copyInto(it) }
        val kind = DiscSignatures.identify(DataSectors(s0, null, null))
        assertEquals(DiscKind.SATURN, kind)
        assertEquals("saturn", kind!!.consoleId)
        assertEquals("SATURN", Resolution.acceptsConsole("saturn", "Nights.chd") { it.defaultTag })
    }

    @Test fun emptyNewConsoleIsNotListed() {
        val dir = "Roms/Nintendo Switch (SWITCH)"
        fun index(games: List<IndexedGame>) = LibraryIndex(
            version = LibraryIndex.VERSION, home = "Home", extras = emptyList(), defaultTags = emptyMap(),
            systems = listOf(IndexedSystem("SWITCH", "Nintendo Switch", "Nintendo Switch (SWITCH)", games = games)),
        )
        assertEquals(emptyList(), index(emptyList()).playableSystems().map { it.tag })
        val game = IndexedGame(Recents.HOME, dir, "Zelda.nsp", "Home/$dir/Zelda.nsp", "Home/$dir", "Zelda")
        assertEquals(listOf("SWITCH"), index(listOf(game)).playableSystems().map { it.tag })
    }

    @Test fun everyNewConsoleHasAFrame() {
        for (id in external.values) assertTrue(id in CONSOLE_FRAMES, id)
        assertEquals(1280f, CONSOLE_FRAMES["switch"]!!.width)
        assertEquals(0f, CONSOLE_FRAMES["switch"]!!.aspect)
        assertEquals(320f, CONSOLE_FRAMES["saturn"]!!.width)
        assertFalse(CONSOLE_FRAMES["saturn"]!!.aspect == 0f)
    }
}
