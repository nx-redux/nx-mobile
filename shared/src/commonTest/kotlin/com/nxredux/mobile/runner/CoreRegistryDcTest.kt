package com.nxredux.mobile.runner

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.options.CoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Dreamcast on flycast: discs own the console, arcade zips/7z are loaded as is, and the BIOS lives in `<system>/dc/`. */
class CoreRegistryDcTest {
    private val dc get() = CoreRegistry.forTag("DC")!!

    @Test fun dc_runs_flycast() {
        assertEquals("flycast", dc.id)
        assertEquals("libflycast_libretro_android.so", dc.libraryFileName)
        assertEquals(setOf("chd", "cdi", "gdi", "cue", "m3u", "zip", "7z"), dc.extensions)
        assertEquals(LoadMode.FILE_DESCRIPTOR, dc.loadMode)   // flycast reads every image through the libretro VFS
        assertEquals("dc", dc.biosSubdir)                        // flycast looks for its BIOS in <system>/dc/
        assertFalse(dc.cheats)
        assertEquals(dc, CoreRegistry.forId("flycast"))
        assertEquals(listOf("DC"), CoreRegistry.tagsSharingCore("DC"))
    }

    @Test fun dc_sets_all_four_ports_with_the_pad_on_port_one() {
        // flycast finishes its device setup (the VMU slot options) only once every one of its four ports was set.
        assertEquals(mapOf(0 to CoreDescriptor.JOYPAD, 1 to 0, 2 to 0, 3 to 0), dc.controllerPortsFor(1))
        assertEquals(listOf(0, 1, 2, 3), dc.controllerPortsFor(1).keys.toList())   // flycast's setup completes on whichever port is set last
    }

    @Test fun other_cores_set_port_zero_only_or_nothing() {
        assertEquals(mapOf(0 to CoreDescriptor.PS_DUALSHOCK), CoreRegistry.forTag("PS")!!.controllerPortsFor(1))
        assertEquals(emptyMap(), CoreRegistry.forTag("GBA")!!.controllerPortsFor(1))
    }

    @Test fun dc_loads_arcade_archives_as_is() {
        assertTrue(CoreRegistry.loadsArchive("DC", "mslug6.zip"))
        assertTrue(CoreRegistry.loadsArchive("DC", "MSlug6.7z"))
        assertEquals("mslug6.zip", CoreRegistry.contentName("DC", "MSlug6.ZIP"))
        assertFalse(CoreRegistry.loadsArchive("DC", "Crazy Taxi.chd"))
        assertEquals("Crazy Taxi.chd", CoreRegistry.contentName("DC", "Crazy Taxi.chd"))
    }

    @Test fun a_dc_archive_is_loaded_as_is_only_when_it_is_one_of_flycasts_sets() {
        val sets = NaomiSets.parse("mslug6\t\tMetal Slug 6\tawbios\nawbios\t\t\tawbios\n")
        assertTrue(CoreRegistry.loadsArchive("DC", "MSlug6.zip", sets))
        assertTrue(CoreRegistry.loadsArchive("DC", "mslug6.7z", sets))
        assertFalse(CoreRegistry.loadsArchive("DC", "Crazy Taxi (USA).zip", sets))   // a zipped disc is unpacked
        assertFalse(CoreRegistry.loadsArchive("DC", "Crazy Taxi (USA).7z", sets))
        assertFalse(CoreRegistry.loadsArchive("DC", "Shenmue.chd", sets))
        assertTrue(CoreRegistry.loadsArchive("DC", "Crazy Taxi (USA).zip", NaomiSets.EMPTY))   // no table: as before
        // fbneo's sets are not flycast's: the table does not apply.
        assertTrue(CoreRegistry.loadsArchive("FBN", "mslug2.zip", sets))
        assertFalse(CoreRegistry.loadsArchive("GBA", "Tetris.zip", NaomiSets.EMPTY))
    }

    @Test fun dc_console_uses_the_skeleton_name_and_owns_gdi_and_cdi() {
        val c = CoreRegistry.consoleOf("DC")!!
        assertEquals("dc", c.id)
        assertEquals("Dreamcast", c.displayName)
        assertEquals(listOf("DC"), c.tags)
        assertEquals(setOf("dc", "dreamcast", "sega dreamcast"), c.aliases)
        assertEquals(setOf("chd", "cdi", "gdi", "cue", "m3u"), c.extensions)
        assertTrue(c.extensions.none { it in Names.ARCHIVE_EXTENSIONS })
        assertFalse(c.rawSram)
        assertEquals(listOf("dc"), CoreRegistry.consolesForExtension("gdi").map { it.id })
        assertEquals(listOf("dc"), CoreRegistry.consolesForExtension("cdi").map { it.id })
        assertEquals(c, CoreRegistry.consoleForFolderName("Sega Dreamcast"))
        assertEquals("Dreamcast (DC)", Names.systemFolderName(c, "DC"))
    }

    @Test fun dc_needs_dc_boot_bin_for_discs_only() {
        val needs = CoreRegistry.requiredBios("DC")
        assertEquals(listOf(BiosNeed(listOf("dc_boot.bin"), setOf("chd", "cdi", "gdi", "cue", "m3u"))), needs)
        for (ext in listOf("chd", "cdi", "gdi", "cue", "m3u", "GDI")) assertEquals(1, BiosCheck.missing(needs, ext, emptySet()).size, ext)
        for (ext in listOf("zip", "7z")) assertEquals(emptyList(), BiosCheck.missing(needs, ext, emptySet()), ext)   // arcade BIOS is its own check
        assertEquals(emptyList(), BiosCheck.missing(needs, "chd", setOf("dc_boot.bin", "dc_nvmem.bin")))
        assertEquals("Dreamcast needs a BIOS: put dc_boot.bin in Bios/DC/", BiosCheck.message("DC", needs.single()))
    }

    @Test fun only_flycast_and_armsx2_keep_their_bios_in_a_subfolder() {
        assertEquals("dc", BiosFolders.subdir("DC"))
        assertEquals("dc", BiosFolders.subdir(" dc "))
        assertEquals("", BiosFolders.subdir("PS"))
        assertEquals("", BiosFolders.subdir("FBN"))
        assertEquals("", BiosFolders.subdir("PORTS"))
        assertEquals(setOf("flycast", "armsx2"), CoreRegistry.all.filter { it.biosSubdir.isNotEmpty() }.map { it.id }.toSet())
        assertEquals(listOf("DC"), BiosFolders.forTag("DC"))
    }

    @Test fun dc_defaults_are_nx_reduxs_flycast_options() {
        // Values are flycast's exact strings (libretro_core_options.h); a misspelt one is silently ignored by the core.
        assertEquals(
            mapOf(
                "reicast_per_content_vmus" to "VMU A1",
                "reicast_alpha_sorting" to "per-strip (fast, least accurate)",
                "reicast_threaded_rendering" to "enabled",
                "reicast_delay_frame_swapping" to "enabled",
                "reicast_internal_resolution" to "640x480",
                "reicast_region" to "USA",
                "reicast_language" to "English",
                "reicast_broadcast" to "NTSC",
                "reicast_cable_type" to "TV (RGB)",
                "reicast_widescreen_hack" to "disabled",
                "reicast_hle_bios" to "disabled",
                "reicast_detect_vsync_swap_interval" to "enabled",
                "reicast_device_port2_slot1" to "None",
                "reicast_device_port3_slot1" to "None",
                "reicast_device_port4_slot1" to "None",
            ),
            dc.defaultOptions,
        )
    }

    @Test fun frame_rate_detection_is_locked_on_so_an_old_cfg_cannot_bring_back_2x_speed() {
        assertEquals(setOf("reicast_detect_vsync_swap_interval", "reicast_per_content_vmus"), dc.lockedOptions)
        // A .cfg saved before the default existed stores flycast's own "disabled"; the locked default wins.
        val game = CoreConfig.parse("reicast_detect_vsync_swap_interval = disabled\nreicast_widescreen_hack = enabled\n")
        val r = CoreConfig.resolve(dc.defaultOptions, null, game, forced = dc.lockedOptions)
        assertEquals("enabled", r.values["reicast_detect_vsync_swap_interval"])
        assertEquals("enabled", r.values["reicast_widescreen_hack"])
        assertTrue("reicast_detect_vsync_swap_interval" in r.locked)
    }

    @Test fun per_game_vmu_is_locked_so_a_cfg_cannot_move_the_vmu_out_of_the_save_dir() {
        val game = CoreConfig.parse("reicast_per_content_vmus = All VMUs\n")
        val r = CoreConfig.resolve(dc.defaultOptions, null, game, forced = dc.lockedOptions)
        assertEquals("VMU A1", r.values["reicast_per_content_vmus"])
        assertTrue("reicast_per_content_vmus" in r.locked)
    }
}
