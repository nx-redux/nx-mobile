package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.NetplayCores
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Saturn on YabaSanshiro: discs only, BIOS read from the system folder's root, HLE when it is missing. */
class CoreRegistrySaturnTest {
    private val ss get() = CoreRegistry.forTag("SATURN")!!

    @Test fun saturn_runs_on_yabasanshiro() {
        assertEquals("yabasanshiro", ss.id)
        assertEquals("libyabasanshiro_libretro_android.so", ss.libraryFileName)
        assertEquals(setOf("cue", "chd", "ccd", "iso", "m3u"), ss.extensions)
        assertEquals("", ss.biosSubdir)
        assertEquals("yabasanshiro", CoreRegistry.forId("yabasanshiro")?.id)
        assertEquals("saturn", CoreRegistry.consoleOf("SATURN")?.id)
        assertEquals("Sega Saturn", CoreRegistry.consoleOf("SATURN")?.displayName)
    }

    @Test fun saturn_defaults() {
        assertEquals("dynarec", ss.defaultOptions["yabasanshiro_sh2coretype"])
        assertEquals("4M_extended_ram", ss.defaultOptions["yabasanshiro_addon_cart"])
        assertEquals("enabled", ss.defaultOptions["yabasanshiro_frameskip"])
        assertEquals("english", ss.defaultOptions["yabasanshiro_system_language"])
        assertEquals("disabled", ss.defaultOptions["yabasanshiro_force_hle_bios"])
        assertTrue("yabasanshiro_force_hle_bios" in ss.lockedOptions)
        assertTrue("yabasanshiro_resolution_mode" in ss.lockedOptions)
        assertEquals(2, ss.players)
        assertEquals(2, ss.controllerPortCount)
        assertEquals(mapOf(0 to CoreDescriptor.JOYPAD, 1 to CoreDescriptor.JOYPAD), ss.controllerPortsFor(2).filterKeys { it < 2 })
        assertTrue(ss.cheats)
        assertTrue(ss.ramCheats)
    }

    @Test fun saturn_saves_named_after_content() = assertTrue(ss.savesNamedAfterContent)

    @Test fun saturn_no_rewind_no_netplay() {
        assertFalse(ss.rewind)
        assertFalse(NetplayCores.supported("SATURN"))
    }

    @Test fun saturn_bios_files_listed() =
        assertEquals(listOf("saturn_bios.bin", "sega_101.bin", "mpr-17933.bin"), ss.biosFiles)
}
