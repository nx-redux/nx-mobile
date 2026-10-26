package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Nintendo 3DS on Azahar: a real path, the GL renderer by default, the user dir kept under the save dir, no BIOS. */
class CoreRegistry3dsTest {
    private val n3ds get() = CoreRegistry.forTag("3DS")!!

    @Test fun three_ds_runs_azahar() {
        assertEquals("azahar", n3ds.id)
        assertEquals("libazahar_libretro_android.so", n3ds.libraryFileName)
        assertEquals(setOf("3ds", "3dsx", "z3dsx", "elf", "axf", "cci", "zcci", "cxi", "zcxi", "app"), n3ds.extensions)
        assertEquals(LoadMode.FILE_DESCRIPTOR, n3ds.loadMode)   // through the runner's VFS (azahar.patch: stat asks the VFS)
        assertEquals(emptyList(), n3ds.biosFiles)           // keyblob and shared font are built in
        assertEquals(emptyList(), CoreRegistry.requiredBios("3DS"))
        assertFalse(n3ds.cheats)
        assertEquals(n3ds, CoreRegistry.forId("azahar"))
        assertEquals(n3ds, CoreRegistry.forTag("3ds"))
        assertEquals(listOf("3DS"), CoreRegistry.tagsSharingCore("3DS"))
        assertEquals("Azahar (3DS)", CoreRegistry.emulatorLabel("3DS"))
    }

    @Test fun three_ds_defaults_to_the_gl_renderer_and_never_offers_software() {
        assertEquals("OpenGL", n3ds.defaultOptions["citra_graphics_api"])
        assertEquals(mapOf("citra_graphics_api" to setOf("Software")), n3ds.hiddenValues)
        assertTrue(n3ds.hiddenValues.all { (k, v) -> n3ds.defaultOptions[k] !in v })
        assertEquals("3", n3ds.defaultOptions["citra_resolution_factor"])
        assertEquals("enabled", n3ds.defaultOptions["citra_use_disk_shader_cache"])
        assertEquals("default", n3ds.defaultOptions["citra_layout_option"])   // the core's top-over-bottom
        assertEquals("enabled", n3ds.defaultOptions["citra_enable_touch_touchscreen"])
    }

    @Test fun three_ds_keeps_its_user_dir_under_the_save_dir() {
        assertEquals("LibRetro Default", n3ds.defaultOptions["citra_use_libretro_save_path"])
        assertTrue("citra_use_libretro_save_path" in n3ds.lockedOptions)
        assertTrue(n3ds.lockedOptions.all { it in n3ds.defaultOptions })
    }

    @Test fun three_ds_locks_mouse_off_and_motion_on() {
        // No mouse in the runner (the pointer is touch, read by Touch Device Support); motion from the phone's sensors.
        assertEquals("disabled", n3ds.defaultOptions["citra_enable_mouse_touchscreen"])
        assertEquals("enabled", n3ds.defaultOptions["citra_enable_motion"])
        assertEquals(
            setOf(
                "citra_graphics_api", "citra_use_libretro_save_path", "citra_enable_mouse_touchscreen", "citra_enable_motion",
                "citra_motion_sensitivity", "citra_layout_option",   // the layout comes from the app's per-orientation rows
                "citra_input_type",   // the mic, fed by the app's Mic toggle
                "citra_resolution_factor", "citra_large_screen_proportion", "citra_render_3d",
            ),
            n3ds.lockedOptions,
        )
    }

    @Test fun lockedResolutionAndScreenOptions() {
        // The 3D resolution is the app's (Nx scale), the large screen proportion is the core's default, 2D only.
        assertTrue(listOf("citra_resolution_factor", "citra_large_screen_proportion", "citra_render_3d").all { it in n3ds.lockedOptions })
        assertEquals("3", n3ds.defaultOptions["citra_resolution_factor"])
        assertEquals("4.00", n3ds.defaultOptions["citra_large_screen_proportion"])
        assertEquals("off", n3ds.defaultOptions["citra_render_3d"])
        // The swap options stay visible: the user picks the prominent screen and the swap mode.
        assertFalse("citra_swap_screen" in n3ds.lockedOptions)
        assertFalse("citra_swap_screen_mode" in n3ds.lockedOptions)
    }

    @Test fun three_ds_console_owns_only_its_specific_extensions() {
        val console = CoreRegistry.consoleOf("3DS")!!
        assertEquals("3ds", console.id)
        assertEquals("Nintendo 3DS", console.displayName)
        assertEquals(listOf("3DS"), console.tags)
        assertFalse(console.rawSram)
        for (ext in listOf("3ds", "3dsx", "z3dsx", "cci", "zcci", "cxi", "zcxi")) {
            assertEquals(listOf("3ds"), CoreRegistry.consolesForExtension(ext).map { it.id }, ext)
        }
        // Too generic to name a 3DS game: reached by folder only (PSP runs elf too).
        for (ext in listOf("app", "axf")) assertEquals(emptyList(), CoreRegistry.consolesForExtension(ext).map { it.id }, ext)
        assertEquals(listOf("psp"), CoreRegistry.consolesForExtension("elf").map { it.id })
    }

    @Test fun three_ds_folder_names_resolve() {
        for (name in listOf("Nintendo 3DS", "3DS", "3DS Roms", "My Nintendo 3DS Games", "Azahar", "Citra")) {
            assertEquals("3ds", CoreRegistry.consoleForFolderName(name)?.id, name)
        }
        assertEquals("nds", CoreRegistry.consoleForFolderName("Nintendo DS")?.id)
    }
}
