package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreRegistryArcadeTest {
    @Test fun the_three_new_tags_have_their_cores() {
        assertEquals("libpicodrive_libretro_android.so", CoreRegistry.forTag("32X")!!.libraryFileName)
        assertEquals("libgearcoleco_libretro_android.so", CoreRegistry.forTag("coleco")!!.libraryFileName)
        assertEquals("libfbneo_libretro_android.so", CoreRegistry.forTag("FBN")!!.libraryFileName)
    }
    @Test fun archives_are_content_only_for_cores_that_list_them() {
        assertTrue(CoreRegistry.loadsArchive("FBN", "mslug.zip"))
        assertTrue(CoreRegistry.loadsArchive("fbn", "MSLUG.7Z"))
        assertFalse(CoreRegistry.loadsArchive("GB", "Tetris.zip"))
        assertFalse(CoreRegistry.loadsArchive("FBN", "readme.txt"))
        assertFalse(CoreRegistry.loadsArchive("C64", "x.zip"))
    }
    @Test fun an_archive_the_core_loads_reaches_it_in_lowercase() {
        // fbneo takes the driver name from the content's base name and compares it case-sensitively.
        assertEquals("kof98.zip", CoreRegistry.contentName("FBN", "KOF98.ZIP"))
        assertEquals("mslug.7z", CoreRegistry.contentName("fbn", "MSlug.7z"))
        assertEquals("Tetris (World).zip", CoreRegistry.contentName("GB", "Tetris (World).zip"))
        assertEquals("Sonic (USA).md", CoreRegistry.contentName("MD", "Sonic (USA).md"))
    }
    @Test fun new_core_settings() {
        val pico = CoreRegistry.forTag("32X")!!
        assertEquals(LoadMode.REAL_PATH, pico.loadMode)
        // The Sega consoles' default core: every extension their games use (valid_extensions at the pin, less sc, sgd, pco).
        assertEquals(setOf("32x", "bin", "md", "gen", "smd", "68k", "sms", "gg", "sg", "cue", "iso", "chd", "m3u"), pico.extensions)
        assertEquals("enabled", pico.defaultOptions["picodrive_drc"])
        assertEquals(513, pico.controllerPortsFor(1)[0])
        val fbn = CoreRegistry.forTag("FBN")!!
        assertEquals(setOf("zip", "7z"), fbn.extensions)
        assertEquals(LoadMode.FILE_DESCRIPTOR, fbn.loadMode)
        assertEquals("disabled", fbn.defaultOptions["fbneo-vertical-mode"])
        assertEquals(setOf("fbneo-vertical-mode"), fbn.lockedOptions)
        assertFalse(fbn.cheats)
        assertFalse(CoreRegistry.forTag("COLECO")!!.cheats)
        assertTrue(pico.cheats)
    }
    @Test fun new_consoles_follow_the_brick_folders() {
        assertEquals("Sega 32X", CoreRegistry.consoleOf("32X")!!.displayName)
        assertEquals("Colecovision", CoreRegistry.consoleOf("COLECO")!!.displayName)
        assertEquals("Arcade", CoreRegistry.consoleOf("FBN")!!.displayName)
        assertEquals(emptySet(), CoreRegistry.consoleOf("FBN")!!.extensions)
        assertEquals(listOf("coleco"), CoreRegistry.consolesForExtension("col").map { it.id })
        assertEquals(listOf("32x"), CoreRegistry.consolesForExtension("32x").map { it.id })
        assertEquals(emptyList(), CoreRegistry.consolesForExtension("rom"))
        assertEquals("fbn", CoreRegistry.consoleForFolderName("Arcade")!!.id)
        assertEquals("fbn", CoreRegistry.consoleForFolderName("FBNeo Roms")!!.id)
        assertNull(CoreRegistry.consoleForFolderName("MAME"))
        assertNull(CoreRegistry.consoleForFolderName("Neo Geo"))
        assertEquals("ngp", CoreRegistry.consoleForFolderName("Neo Geo Pocket")!!.id)
    }
    @Test fun coleco_needs_its_bios() {
        val need = CoreRegistry.requiredBios("COLECO").single()
        assertEquals(listOf("colecovision.rom", "coleco.rom"), need.anyOf)
    }
}
