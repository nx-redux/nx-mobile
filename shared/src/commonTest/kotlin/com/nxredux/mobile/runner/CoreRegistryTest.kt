package com.nxredux.mobile.runner

import com.nxredux.mobile.library.Names
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreRegistryTest {
    @Test fun nds_maps_to_melondsds_case_insensitively() {
        assertEquals("libmelondsds_libretro_android.so", CoreRegistry.forTag("NDS")!!.libraryFileName)
        assertEquals("libmelondsds_libretro_android.so", CoreRegistry.forTag("nds")!!.libraryFileName)
    }
    @Test fun gb_and_gbc_share_gambatte() {
        assertEquals(CoreRegistry.forTag("GB"), CoreRegistry.forTag("GBC"))
        assertEquals("libgambatte_libretro_android.so", CoreRegistry.forTag("GB")!!.libraryFileName)
    }
    @Test fun gba_runs_gpsp_and_mgba_runs_mgba() {
        assertEquals("libgpsp_libretro_android.so", CoreRegistry.forTag("GBA")!!.libraryFileName)
        assertEquals("mgba", CoreRegistry.forTag("MGBA")!!.id)
        assertNotEquals(CoreRegistry.forTag("GBA"), CoreRegistry.forTag("MGBA"))
    }
    @Test fun every_new_tag_maps_to_its_library() {
        val gpgx = "libgenesis_plus_gx_libretro_android.so"
        val pico = "libpicodrive_libretro_android.so"
        val expected = mapOf(
            "FDS" to "libfceumm_libretro_android.so", "SGB" to "libmgba_libretro_android.so",
            "SUPA" to "libmednafen_supafaust_libretro_android.so",
            "GPGX" to gpgx, "MD" to pico, "SMS" to pico, "GG" to pico, "SG1000" to pico, "SEGACD" to pico, "32X" to pico,
            "PS" to "libpcsx_rearmed_libretro_android.so",
            "NGP" to "librace_libretro_android.so", "NGPC" to "librace_libretro_android.so",
            "PCE" to "libmednafen_pce_fast_libretro_android.so", "VB" to "libmednafen_vb_libretro_android.so",
            "LYNX" to "libhandy_libretro_android.so", "A2600" to "libstella2014_libretro_android.so",
            "A5200" to "liba5200_libretro_android.so", "A7800" to "libprosystem_libretro_android.so",
            "PKM" to "libpokemini_libretro_android.so", "WSC" to "libmednafen_wswan_libretro_android.so",
            "P8" to "libfake08_libretro_android.so", "PRBOOM" to "libprboom_libretro_android.so",
        )
        for ((tag, so) in expected) assertEquals(so, CoreRegistry.forTag(tag)?.libraryFileName, tag)
        for (core in CoreRegistry.all) {
            val so = if (core.id == "mupen64plus_next") "libmupen64plus_next_gles3_libretro_android.so" else "lib${core.id}_libretro_android.so"
            assertEquals(so, core.libraryFileName, core.id)
        }
    }
    @Test fun sgb_is_an_mgba_view_with_the_bricks_defaults() {
        val sgb = CoreRegistry.forTag("SGB")!!
        assertEquals("mgba", sgb.id)
        assertEquals(setOf("gb", "gbc"), sgb.extensions)
        assertEquals(mapOf("mgba_gb_model" to "Super Game Boy", "mgba_sgb_borders" to "ON"), sgb.defaultOptions)
        val plain = CoreRegistry.forId("mgba")!!
        assertEquals(emptyMap(), plain.defaultOptions)
        assertEquals(setOf("gba"), plain.extensions)
        assertEquals(plain, CoreRegistry.forTag("MGBA"))
    }
    @Test fun all_lists_each_core_once() {
        val ids = CoreRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(30, ids.size)
    }
    @Test fun the_bricks_pak_defaults_are_carried() {
        assertEquals(mapOf("pcsx_rearmed_dithering" to "enabled"), CoreRegistry.forTag("PS")!!.defaultOptions)
        assertEquals(mapOf("race_dark_filter_level" to "50"), CoreRegistry.forTag("NGPC")!!.defaultOptions)
        assertEquals(mapOf("vb_3dmode" to "anaglyph"), CoreRegistry.forTag("VB")!!.defaultOptions)
        assertEquals(
            mapOf(
                "supafaust_pixel_format" to "rgb565",
                "supafaust_thread_affinity_emu" to "0x0", "supafaust_thread_affinity_ppu" to "0x0",   // affinity off
            ),
            CoreRegistry.forTag("SUPA")!!.defaultOptions,
        )
        assertEquals(mapOf("prboom-mouse_on" to "disabled", "prboom-rumble" to "enabled"), CoreRegistry.forTag("PRBOOM")!!.defaultOptions)
        assertEquals(
            mapOf("pokemini_60hz_mode" to "enabled", "pokemini_palette" to "Old", "pokemini_piezofilter" to "disabled", "pokemini_lowpass_filter" to "enabled"),
            CoreRegistry.forTag("PKM")!!.defaultOptions,
        )
        assertEquals(mapOf("genesis_plus_gx_system_bram" to "per game"), CoreRegistry.forTag("GPGX")!!.defaultOptions)
        assertEquals(listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"), CoreRegistry.forTag("GPGX")!!.biosFiles)
        assertEquals(listOf("disksys.rom"), CoreRegistry.forTag("FDS")!!.biosFiles)
        assertTrue("iso" in CoreRegistry.forTag("MD")!!.extensions && "m3u" in CoreRegistry.forTag("MD")!!.extensions)
    }
    @Test fun picodrive_runs_the_sega_consoles_with_each_paks_values() {
        val six = mapOf(
            "picodrive_drc" to "enabled", "picodrive_sound_rate" to "44100",
            "picodrive_input1" to "6 button pad", "picodrive_input2" to "6 button pad",
        )
        for (tag in listOf("MD", "SEGACD", "32X")) assertEquals(six, CoreRegistry.forTag(tag)!!.defaultOptions, tag)
        val three = six + mapOf("picodrive_input1" to "3 button pad", "picodrive_input2" to "3 button pad")
        assertEquals(three + ("picodrive_smstype" to "Master System"), CoreRegistry.forTag("SMS")!!.defaultOptions)
        assertEquals(three + ("picodrive_smstype" to "Game Gear"), CoreRegistry.forTag("GG")!!.defaultOptions)
        assertEquals(three, CoreRegistry.forTag("SG1000")!!.defaultOptions)
        // Tag views: one core in `all`, with the plain (MD/32X) values.
        assertEquals(six, CoreRegistry.forId("picodrive")!!.defaultOptions)
        for (tag in listOf("MD", "SMS", "GG", "SG1000", "SEGACD")) {
            val ext = CoreRegistry.forTag(tag)!!.extensions
            assertTrue(CoreRegistry.consoleOf(tag)!!.extensions.all { it in ext }, tag)   // 68k included
        }
    }
    @Test fun unknown_tags_have_no_core() {
        for (tag in listOf("PORTS", "")) assertNull(CoreRegistry.forTag(tag), tag)
    }
    @Test fun n64_uses_gles3_build_and_psp_needs_assets() {
        assertEquals("libmupen64plus_next_gles3_libretro_android.so", CoreRegistry.forTag("N64")!!.libraryFileName)
        assertTrue(CoreRegistry.forTag("PSP")!!.biosFiles.contains("PPSSPP/"))
    }
    @Test fun cores_read_file_descriptors_except_prboom_and_picodrive() {
        for (core in CoreRegistry.all) {
            val real = core.id in setOf("prboom", "picodrive")
            assertEquals(if (real) LoadMode.REAL_PATH else LoadMode.FILE_DESCRIPTOR, core.loadMode, core.id)
            assertTrue(core.extensions.isNotEmpty(), core.id)
            assertTrue(core.extensions.all { it == it.lowercase() && !it.startsWith(".") }, core.id)
            if (core.id !in setOf("fbneo", "flycast")) assertTrue(core.extensions.none { it in Names.ARCHIVE_EXTENSIONS }, core.id)
        }
    }
    @Test fun ps_uses_the_dualshock() {
        assertEquals(517, CoreDescriptor.PS_DUALSHOCK)
        assertEquals(CoreDescriptor.PS_DUALSHOCK, CoreRegistry.forTag("PS")!!.controllerPort0)
        assertEquals(1, CoreDescriptor.JOYPAD)
        assertEquals(CoreDescriptor.JOYPAD, CoreRegistry.forTag("DC")!!.controllerPort0)   // flycast plugs no pad in by itself
        assertEquals(setOf("pcsx_rearmed", "swanstation", "flycast", "dolphin"), CoreRegistry.all.filter { it.controllerPort0 != null }.map { it.id }.toSet())
    }
    @Test fun only_flycast_names_its_saves_after_the_content_so_only_it_unpacks_under_the_archive_name() {
        assertEquals(setOf("flycast"), CoreRegistry.all.filter { it.savesNamedAfterContent }.map { it.id }.toSet())
    }
    @Test fun only_gpsp_stays_loaded_between_games() {
        assertEquals(setOf("gpsp"), CoreRegistry.all.filter { it.keepLoaded }.map { it.id }.toSet())
        assertEquals(true, CoreRegistry.forTag("GBA")!!.keepLoaded)
    }
    @Test fun mupen_hides_startup_only_options_but_not_live_ones() {
        val hidden = CoreRegistry.forTag("N64")!!.restartOnlyOptions
        assertTrue("mupen64plus-aspect" in hidden)
        assertFalse("mupen64plus-astick-deadzone" in hidden)
        assertTrue(hidden.none { it.startsWith("mupen64plus-parallel-rdp-") })
    }
    @Test fun cores_without_startup_only_options_hide_nothing() {
        for (id in listOf("gambatte", "fceumm", "genesis_plus_gx", "gpsp", "pcsx_rearmed", "race", "prboom")) {
            assertEquals(emptySet<String>(), CoreRegistry.forId(id)!!.restartOnlyOptions, id)
        }
    }
    @Test fun every_hidden_value_key_has_a_default_that_is_not_hidden() {
        // A hidden value is only skipped in the menu and ignored in a .cfg: with no default of ours, the core's own
        // default (possibly the hidden value) would reach the core.
        for (tag in CoreRegistry.tags) {
            val core = CoreRegistry.forTag(tag)!!
            val defaults = core.defaultOptions.mapKeys { it.key.removePrefix("-") }
            for ((key, values) in core.hiddenValues) {
                val default = defaults[key]
                assertTrue(default != null, "$tag ${core.id}: $key has hidden values but no defaultOptions entry")
                assertFalse(default in values, "$tag ${core.id}: $key defaults to a hidden value")
            }
        }
    }
    @Test fun values_and_keys_the_app_cannot_serve_are_kept_from_emulator_settings() {
        // Any affinity mask but 0x0 aborts the app at load: locked, so neither the page nor a .cfg can set one.
        assertEquals(setOf("supafaust_thread_affinity_emu", "supafaust_thread_affinity_ppu"), CoreRegistry.forTag("SUPA")!!.lockedOptions)
        // Restart-only save layout the saves depend on.
        assertTrue("genesis_plus_gx_system_bram" in CoreRegistry.forTag("GPGX")!!.offlineHiddenOptions)
        assertEquals(mapOf("ppsspp_backend" to setOf("vulkan", "none")), CoreRegistry.forTag("PSP")!!.hiddenValues)
        assertEquals(mapOf("swanstation_GPU_Renderer" to setOf("Vulkan")), CoreRegistry.forTag("PSX")!!.hiddenValues)
        // N64's plugins follow Graphics API (locked, nothing hidden); CoreRegistryVulkanTest pins them.
        assertEquals(emptyMap(), CoreRegistry.forTag("N64")!!.hiddenValues)
    }
    @Test fun forId_finds_cores_by_id() {
        val psp = CoreRegistry.forId("ppsspp")
        assertEquals(CoreRegistry.forTag("PSP"), psp)
        assertTrue("ppsspp_cpu_core" in psp!!.restartOnlyOptions)
        assertEquals(CoreRegistry.forTag("PS"), CoreRegistry.forId("pcsx_rearmed"))
        assertNull(CoreRegistry.forId("nope"))
    }
    @Test fun consoles_group_tags_in_preference_order() {
        val gba = CoreRegistry.consoleOf("MGBA")!!
        assertEquals("gba", gba.id)
        assertEquals(listOf("GBA", "MGBA"), gba.tags)
        assertEquals(gba, CoreRegistry.consoleOf("gba"))
        val md = CoreRegistry.consoleOf("MD")!!
        assertEquals(listOf("MD", "GPGX"), md.tags)   // PicoDrive first, Genesis Plus GX second, as the Brick's paks
        assertEquals("MD", md.defaultTag)
        assertEquals(md, CoreRegistry.consoleOf("gpgx"))
        assertEquals("picodrive", CoreRegistry.forTag("MD")!!.id)
        assertEquals("genesis_plus_gx", CoreRegistry.forTag("GPGX")!!.id)
        assertEquals("GPGX", md.defaultTagFor("GPGX"))   // Default emulators can switch Genesis to Genesis Plus GX
        assertEquals("MD", md.defaultTagFor(null))
        // One rule for the library and Device Sync: display name, alias, squashed name, tag unless shared, name mention.
        assertEquals("gba", CoreRegistry.consoleOfFolder("Game Boy Advance", "MGBA")?.id)
        assertEquals("segacd", CoreRegistry.consoleOfFolder("Sega CD", "GPGX")?.id)
        assertEquals("segacd", CoreRegistry.consoleOfFolder("Sega Mega-CD", "GPGX")?.id)   // a name mention
        assertEquals("sms", CoreRegistry.consoleOfFolder("Mastersystem", "GPGX")?.id)      // squashed
        assertEquals("gba", CoreRegistry.consoleOfFolder("My Games", "GBA")?.id)          // the tag's console
        assertNull(CoreRegistry.consoleOfFolder("My Games", "GPGX"))                       // a shared tag is never guessed
        for (f in listOf("Sega Mega-CD (GPGX)", "Mastersystem (GPGX)", "My Games (GPGX)", "Gameboy (GB)")) {
            val (d, t) = com.nxredux.mobile.library.Names.parseSystemFolder(f)!!
            assertEquals(com.nxredux.mobile.sync.SyncMap.consoleOf(f), CoreRegistry.consoleOfFolder(d, t), f)
        }
        assertEquals("SUPA", CoreRegistry.consoleOf("SFC")!!.defaultTagFor("SUPA"))
        assertEquals("SFC", CoreRegistry.consoleOf("SFC")!!.defaultTagFor("GBA"))
        assertEquals(listOf("SFC", "SUPA"), CoreRegistry.consoleOf("SUPA")!!.tags)
        assertEquals(
            listOf(
                "nds", "n64", "psp", "ps2", "ps", "gb", "gbc", "gba", "fc", "sfc", "md",
                "sms", "gg", "sg1000", "segacd", "fds", "sgb", "ngp", "ngpc", "pce", "vb",
                "lynx", "a2600", "a5200", "a7800", "pkm", "wsc", "p8", "prboom", "32x", "coleco", "fbn", "dc", "3ds", "wii", "gc",
            ),
            CoreRegistry.consoles.map { it.id },
        )
        assertEquals(listOf("ps", "gba", "sfc", "md", "sms", "gg", "sg1000", "segacd"), CoreRegistry.consoles.filter { it.tags.size > 1 }.map { it.id })
        assertNull(CoreRegistry.consoleOf("C64"))
    }
    @Test fun tags_sharing_a_core_follow_the_bricks_bios_folders() {
        assertEquals(listOf("FC", "FDS"), CoreRegistry.tagsSharingCore("FDS"))
        assertEquals(listOf("MD", "SMS", "GG", "SG1000", "SEGACD", "32X"), CoreRegistry.tagsSharingCore("md"))
        assertEquals(listOf("GPGX", "GPGX_SMS", "GPGX_GG", "GPGX_SEGACD", "GPGX_SG1000"), CoreRegistry.tagsSharingCore("GPGX"))
        assertEquals(listOf("MGBA", "SGB"), CoreRegistry.tagsSharingCore("SGB"))
        assertEquals(listOf("GBA"), CoreRegistry.tagsSharingCore("GBA"))
        assertEquals(listOf("PS"), CoreRegistry.tagsSharingCore("PS"))
        assertEquals(listOf("NGP", "NGPC"), CoreRegistry.tagsSharingCore("NGPC"))
        assertEquals(emptyList(), CoreRegistry.tagsSharingCore("C64"))
    }
    @Test fun display_names_are_the_bricks_skeleton_names() {
        assertEquals(
            listOf(
                "Nintendo DS", "Nintendo 64", "Sony Playstation Portable", "Sony PlayStation 2", "Sony PlayStation", "Game Boy", "Game Boy Color",
                "Game Boy Advance", "Nintendo ES", "Super Nintendo ES", "Sega Genesis", "Sega Master System", "Sega Game Gear",
                "Sega SG-1000", "Sega CD", "Famicom Disk System", "Super Game Boy", "Neo Geo Pocket", "Neo Geo Pocket Color",
                "TurboGrafx-16", "Virtual Boy", "Atari Lynx", "Atari 2600", "Atari 5200", "Atari 7800", "Pokémon mini",
                "Wonderswan Color", "Pico-8", "Doom", "Sega 32X", "Colecovision", "Arcade", "Dreamcast", "Nintendo 3DS", "Nintendo Wii", "Nintendo GameCube",
            ),
            CoreRegistry.consoles.map { it.displayName },
        )
    }
    @Test fun every_display_name_is_an_alias_and_aliases_are_unique() {
        for (c in CoreRegistry.consoles) assertEquals(c.id, CoreRegistry.consoleByAlias(c.displayName)?.id, c.displayName)
        val aliases = CoreRegistry.consoles.flatMap { it.aliases }
        assertEquals(aliases.size, aliases.toSet().size)
        assertTrue(aliases.all { it == it.lowercase() })
    }
    @Test fun aliases_match_nx_redux_folder_names_case_insensitively() {
        assertEquals("fc", CoreRegistry.consoleByAlias("NES")!!.id)
        assertEquals("fc", CoreRegistry.consoleByAlias("Nintendo Entertainment System")!!.id)
        assertEquals("sfc", CoreRegistry.consoleByAlias("snes")!!.id)
        assertEquals("sfc", CoreRegistry.consoleByAlias("SUPA")!!.id)
        assertEquals("md", CoreRegistry.consoleByAlias("Genesis")!!.id)
        assertEquals("md", CoreRegistry.consoleByAlias("gpgx")!!.id)
        assertEquals("gba", CoreRegistry.consoleByAlias("mgba")!!.id)
        assertEquals("nds", CoreRegistry.consoleByAlias("DS")!!.id)
        assertEquals("psp", CoreRegistry.consoleByAlias("PPSSPP")!!.id)
        assertEquals("sgb", CoreRegistry.consoleByAlias("Super Game Boy")!!.id)    // moved from gb
        assertEquals("segacd", CoreRegistry.consoleByAlias("Mega CD")!!.id)
        assertEquals("pce", CoreRegistry.consoleByAlias("PC Engine")!!.id)
        assertEquals("wsc", CoreRegistry.consoleByAlias("WonderSwan")!!.id)
        assertEquals("pkm", CoreRegistry.consoleByAlias("Pokemon Mini")!!.id)
        assertEquals("prboom", CoreRegistry.consoleByAlias("Doom")!!.id)
        assertNull(CoreRegistry.consoleByAlias("Games"))
    }
    @Test fun folder_names_match_the_longest_alias_they_contain() {
        assertEquals("md", CoreRegistry.consoleForFolderName("My Sega Genesis")!!.id)
        assertEquals("psp", CoreRegistry.consoleForFolderName("My Sony Playstation Portable")!!.id)   // beats "sony playstation"
        assertEquals("ps", CoreRegistry.consoleForFolderName("My Sony PlayStation")!!.id)
        assertEquals("gba", CoreRegistry.consoleForFolderName("My Game Boy Advance 2")!!.id)   // beats "game boy"
        assertEquals("gbc", CoreRegistry.consoleForFolderName("game_boy-color")!!.id)
        assertEquals("sfc", CoreRegistry.consoleForFolderName("Super Nintendo ES")!!.id)   // beats "nintendo es"
        assertEquals("ngpc", CoreRegistry.consoleForFolderName("Neo Geo Pocket Color")!!.id)   // beats "neo geo pocket"
        assertEquals("ngp", CoreRegistry.consoleForFolderName("Neo Geo Pocket")!!.id)
        assertEquals("sgb", CoreRegistry.consoleForFolderName("Super Game Boy")!!.id)   // beats "game boy"
        assertEquals("fds", CoreRegistry.consoleForFolderName("Famicom Disk System")!!.id)   // beats "famicom"
        assertEquals("a7800", CoreRegistry.consoleForFolderName("Atari 7800")!!.id)   // beats "atari"
        assertEquals("a2600", CoreRegistry.consoleForFolderName("Atari Games")!!.id)
        assertEquals("segacd", CoreRegistry.consoleForFolderName("Sega Mega CD")!!.id)
        assertEquals("pkm", CoreRegistry.consoleForFolderName("Pokémon mini")!!.id)   // decomposed é: its "poke" word is an alias
        assertEquals("gb", CoreRegistry.consoleForFolderName("GB")!!.id)
        assertNull(CoreRegistry.consoleForFolderName("GBA Manuals"))   // "gba" is short: only a whole-name match counts
        assertNull(CoreRegistry.consoleForFolderName("Roms"))
        assertEquals("ps2", CoreRegistry.consoleForFolderName("PS2")!!.id)
        assertNull(CoreRegistry.consoleForFolderName(""))
    }
    @Test fun short_aliases_match_only_the_whole_folder_name_apart_from_stop_words() {
        assertNull(CoreRegistry.consoleForFolderName("Next Gen Backups"))
        assertNull(CoreRegistry.consoleForFolderName("My DS Games"))
        assertNull(CoreRegistry.consoleForFolderName("My WS Stuff"))
        assertEquals("nds", CoreRegistry.consoleForFolderName("DS")!!.id)
        assertEquals("wsc", CoreRegistry.consoleForFolderName("WS")!!.id)
        assertEquals("md", CoreRegistry.consoleForFolderName(" md ")!!.id)
        assertEquals("gb", CoreRegistry.consoleForFolderName("My Game Boy")!!.id)            // two words
        assertEquals("sfc", CoreRegistry.consoleForFolderName("My SNES Games")!!.id)         // four characters
    }
    @Test fun short_aliases_allow_roms_games_and_isos_around_them() {
        assertEquals("gba", CoreRegistry.consoleForFolderName("GBA Roms")!!.id)
        assertEquals("psp", CoreRegistry.consoleForFolderName("PSP ISOs")!!.id)
        assertEquals("gb", CoreRegistry.consoleForFolderName("Roms GB")!!.id)
        assertEquals("fc", CoreRegistry.consoleForFolderName("NES Games")!!.id)
        assertEquals("ps", CoreRegistry.consoleForFolderName("PS1 ROM")!!.id)
        assertEquals("nds", CoreRegistry.consoleForFolderName("Games - DS - ISO")!!.id)
        assertNull(CoreRegistry.consoleForFolderName("GBA Manuals"))
        assertNull(CoreRegistry.consoleForFolderName("Next Gen Backups"))
        assertNull(CoreRegistry.consoleForFolderName("My GBA"))          // "my" is not a stop word
        assertNull(CoreRegistry.consoleForFolderName("Games"))
        assertNull(CoreRegistry.consoleForFolderName("Roms Games"))
        assertEquals("sfc", CoreRegistry.consoleForFolderName("My SNES Games")!!.id)   // long alias: unchanged
    }
    @Test fun playstation_has_a_core_and_every_console_has_one_on_every_tag() {
        val ps = CoreRegistry.consoleOf("PS")!!
        assertEquals("Sony PlayStation", ps.displayName)
        assertEquals(listOf("PS", "PSX"), ps.tags)
        assertEquals(setOf("ps", "ps1", "psx", "playstation", "sony playstation"), ps.aliases)
        assertEquals(setOf("bin", "cue", "img", "mdf", "toc", "cbn", "m3u", "chd", "iso"), ps.extensions)   // not pbp, exe
        assertFalse(ps.rawSram)
        assertEquals("pcsx_rearmed", CoreRegistry.forTag("PS")!!.id)
        assertEquals(emptyList(), CoreRegistry.consoles.filter { c -> c.tags.any { CoreRegistry.forTag(it) == null } }.map { it.id })
    }
    @Test fun extensions_resolve_uniquely_or_not_at_all() {
        val unique = mapOf(
            "pce" to "pce", "vb" to "vb", "vboy" to "vb", "lnx" to "lynx", "lyx" to "lynx", "ngp" to "ngp",
            "ngc" to "ngpc", "ngpc" to "ngpc", "npc" to "ngpc", "ws" to "wsc", "wsc" to "wsc", "pc2" to "wsc",
            "min" to "pkm", "a26" to "a2600", "a52" to "a5200", "a78" to "a7800", "cdf" to "a7800", "p8" to "p8",
            "wad" to "prboom", "iwad" to "prboom", "pwad" to "prboom", "mdf" to "ps", "cbn" to "ps",
            "agb" to "gba", "gbz" to "gba", "u1" to "gba", "gba" to "gba", "sms" to "sms", "gg" to "gg",
            "sg" to "sg1000", "fds" to "fds", "gb" to "gb", "md" to "md", "pbp" to "psp", "nes" to "fc",
            "32x" to "32x", "col" to "coleco", "cv" to "coleco", "gdi" to "dc", "cdi" to "dc",
            "wbfs" to "wii", "wia" to "wii", "rvz" to "wii", "gcm" to "gc", "gcz" to "gc", "tgc" to "gc", "ciso" to "gc",
        )
        for ((ext, id) in unique) assertEquals(listOf(id), CoreRegistry.consolesForExtension(ext).map { it.id }, ext)
        for (ext in listOf("png", "o", "lmp", "exe", "m3u", "chd", "bin", "iso", "cue", "img", "toc", "ccd", "zip", "7z", "rom")) {
            assertEquals(emptyList<Console>(), CoreRegistry.consolesForExtension(ext), ext)
        }
        assertEquals(setOf("bin", "iso", "chd", "cue", "img", "m3u", "toc", "ccd"), CoreRegistry.GENERIC_EXTENSIONS)
    }
    @Test fun core_only_extensions_are_run_but_never_console_owned() {
        for (ext in listOf("png", "o", "lmp", "exe")) {
            assertTrue(ext in CoreRegistry.coreExtensions, ext)
            assertTrue(CoreRegistry.consoles.none { ext in it.extensions }, ext)
        }
        assertEquals(emptySet(), CoreRegistry.consoleOf("SGB")!!.extensions)   // folder, sidecar or menu only
        assertEquals(emptySet(), CoreRegistry.consoleOf("FBN")!!.extensions)
    }
    @Test fun raw_sram_consoles() {
        assertEquals(setOf("gb", "gbc", "gba", "fc", "sfc", "md"), CoreRegistry.consoles.filter { it.rawSram }.map { it.id }.toSet())
    }
    @Test fun console_tag_is_the_consoles_first_tag() {
        assertEquals("GBA", CoreRegistry.consoleTag("MGBA"))
        assertEquals("GBA", CoreRegistry.consoleTag("gba"))
        assertEquals("PS", CoreRegistry.consoleTag("PSX"))
        assertEquals("SFC", CoreRegistry.consoleTag("SUPA"))
        assertEquals("MD", CoreRegistry.consoleTag("GPGX"))
        assertEquals("DC", CoreRegistry.consoleTag("DC"))
        assertEquals("DCX", CoreRegistry.consoleTag("DCX"))   // no console: itself
    }
}
