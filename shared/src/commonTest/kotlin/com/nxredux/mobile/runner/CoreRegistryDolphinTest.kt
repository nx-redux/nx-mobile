package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreRegistryDolphinTest {
    private val wii = CoreRegistry.forTag("WII")!!
    private val gc = CoreRegistry.forTag("GC")!!

    @Test fun one_core_two_descriptors() {
        assertEquals("dolphin", wii.id)
        assertEquals("dolphin", gc.id)
        assertEquals("libdolphin_libretro_android.so", wii.libraryFileName)
        assertEquals(LoadMode.FILE_DESCRIPTOR, wii.loadMode)   // through the runner's VFS, not a cache copy
        assertEquals(1, CoreRegistry.all.count { it.id == "dolphin" })
        assertEquals(listOf("WII", "GC"), CoreRegistry.tagsSharingCore("GC"))
    }
    @Test fun each_console_plugs_in_its_controller() {
        assertEquals(513, CoreDescriptor.WIIMOTE_SIDEWAYS)
        assertEquals(CoreDescriptor.WIIMOTE_SIDEWAYS, wii.controllerPort0)
        assertEquals(CoreDescriptor.JOYPAD, gc.controllerPort0)
    }
    @Test fun defaults_run_synced_dual_core_with_background_shaders() {
        for (d in listOf(wii, gc)) {
            assertEquals("Hardware", d.defaultOptions["dolphin_renderer"])
            assertEquals("enabled", d.defaultOptions["dolphin_main_cpu_thread"])
            assertEquals("enabled", d.defaultOptions["dolphin_sync_gpu"])
            assertEquals("disabled", d.defaultOptions["dolphin_osd_enabled"])
            assertEquals("3", d.defaultOptions["dolphin_shader_compilation_mode"])
            assertTrue("dolphin_renderer" in d.lockedOptions && "dolphin_osd_enabled" in d.lockedOptions)
            assertEquals(setOf("Software", "Null"), d.hiddenValues["dolphin_renderer"])
        }
        assertEquals("2", wii.defaultOptions["dolphin_ir_mode"])
        assertNull(gc.defaultOptions["dolphin_ir_mode"])
        // The touch pointer's geometry, calibrated on the Fold (SMG2) so a tap lands under the finger.
        assertEquals("18", wii.defaultOptions["dolphin_ir_yaw"])
        assertEquals("20", wii.defaultOptions["dolphin_ir_pitch"])
        assertEquals("16", wii.defaultOptions["dolphin_ir_offset"])
        // Console Settings → Wii Screen sets it at launch: the core option is locked.
        assertTrue("dolphin_widescreen" in wii.lockedOptions)
        // ...and the pointer geometry with it (WiiScreen.pointerOptions): saved values would otherwise win.
        assertTrue(listOf("dolphin_ir_yaw", "dolphin_ir_pitch", "dolphin_ir_offset").all { it in wii.lockedOptions })
    }
    @Test fun upstream_options_that_do_not_fit_the_app_stay_off() {
        for (d in listOf(wii, gc)) {
            // The pushed audio we tested: libretrodroid has no audio or frame-time callback for the other modes.
            assertEquals("0", d.defaultOptions["dolphin_call_back_audio_method"])
            // No VR frontend; Wii Controller sets each port's device; popups are RetroArch notifications.
            assertEquals("disabled", d.defaultOptions["dolphin_vr_mode"])
            assertEquals("frontend", d.defaultOptions["dolphin_wiimote_default_device"])
            assertEquals("disabled", d.defaultOptions["dolphin_log_popups_enabled"])
            assertTrue(listOf("dolphin_vr_mode", "dolphin_wiimote_default_device", "dolphin_log_popups_enabled").all { it in d.lockedOptions })
        }
    }
    @Test fun consoles_and_extensions() {
        val w = CoreRegistry.consoleOf("WII")!!
        val g = CoreRegistry.consoleOf("GC")!!
        assertEquals("Nintendo Wii", w.displayName)
        assertEquals("Nintendo GameCube", g.displayName)
        for (ext in listOf("wbfs", "wia", "rvz")) assertEquals(listOf("wii"), CoreRegistry.consolesForExtension(ext).map { it.id }, ext)
        for (ext in listOf("gcm", "gcz", "tgc", "ciso")) assertEquals(listOf("gc"), CoreRegistry.consolesForExtension(ext).map { it.id }, ext)
        assertEquals(listOf("prboom"), CoreRegistry.consolesForExtension("wad").map { it.id })   // Doom keeps .wad
        assertEquals(listOf("ngpc"), CoreRegistry.consolesForExtension("ngc").map { it.id })
        assertNull(CoreRegistry.consoleByAlias("ngc"))
        assertEquals(g, CoreRegistry.consoleByAlias("gamecube"))
    }

    @Test fun cheatsAreOnAndLockedOn() {
        for (d in listOf(wii, gc)) {
            assertTrue(d.cheats)
            assertEquals("enabled", d.defaultOptions["dolphin_cheats_enabled"])
            assertTrue("dolphin_cheats_enabled" in d.lockedOptions)
        }
        assertTrue("WII" in CoreRegistry.cheatTags && "GC" in CoreRegistry.cheatTags)
    }

    @Test fun dolphinTakesFourPlayersAndLocksItsOrientationHotkeys() {
        for (tag in listOf("WII", "GC")) {
            val d = CoreRegistry.forTag(tag)!!
            assertEquals(4, d.players)
            assertEquals("Disabled", d.defaultOptions["dolphin_hotkey_sideways_toggle"])
            assertEquals("Disabled", d.defaultOptions["dolphin_hotkey_upright_toggle"])
            assertTrue("dolphin_hotkey_sideways_toggle" in d.lockedOptions)
            assertTrue("dolphin_hotkey_upright_toggle" in d.lockedOptions)
        }
    }
}
