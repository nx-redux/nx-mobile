package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BiosCheckTest {
    private val segaCd = listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin")

    @Test fun fds_without_disksys_has_one_need() {
        assertEquals(listOf(BiosNeed(listOf("disksys.rom"))), BiosCheck.missing(CoreRegistry.requiredBios("FDS"), "fds", emptySet()))
        assertEquals(emptyList(), BiosCheck.missing(CoreRegistry.requiredBios("fds"), "fds", setOf("disksys.rom", "gba_bios.bin")))
    }
    @Test fun sega_cd_passes_with_any_region_file() {
        assertEquals(emptyList(), BiosCheck.missing(CoreRegistry.requiredBios("SEGACD"), "cue", setOf("bios_CD_J.bin")))   // a region mismatch is the core's to report
        assertEquals(listOf(BiosNeed(segaCd)), BiosCheck.missing(CoreRegistry.requiredBios("SEGACD"), "chd", emptySet()))
    }
    @Test fun pce_hucards_need_nothing_and_pce_cds_need_the_system_card() {
        val needs = CoreRegistry.requiredBios("PCE")
        assertEquals(emptyList(), BiosCheck.missing(needs, "pce", emptySet()))
        assertEquals(listOf("syscard3.pce"), BiosCheck.missing(needs, "cue", emptySet()).single().anyOf)
        assertEquals(emptyList(), BiosCheck.missing(needs, "CUE", setOf("syscard3.pce")))   // the extension's case does not matter
    }
    @Test fun a_cartridge_on_gpgx_or_md_needs_nothing() {
        for (tag in listOf("GPGX", "MD")) {
            for (ext in listOf("md", "bin", "gen", "sms", "gg")) assertEquals(emptyList(), BiosCheck.missing(CoreRegistry.requiredBios(tag), ext, emptySet()), "$tag $ext")
            for (ext in listOf("cue", "chd", "iso", "m3u")) assertEquals(1, BiosCheck.missing(CoreRegistry.requiredBios(tag), ext, emptySet()).size, "$tag $ext")
        }
    }
    @Test fun a_gpgx_disc_passes_with_the_bricks_md_bios_and_fails_without_it() {
        // The Brick keeps the Sega CD BIOS in Bios/MD/ with Bios/GPGX and Bios/SEGACD empty: importing Bios/MD/ before a
        // GPGX launch is what puts bios_CD_E.bin in the system dir.
        assertTrue("MD" in BiosFolders.forTag("GPGX"))
        assertEquals(emptyList(), BiosCheck.missing(CoreRegistry.requiredBios("GPGX"), "chd", setOf("bios_CD_E.bin")))
        val need = BiosCheck.missing(CoreRegistry.requiredBios("GPGX"), "chd", emptySet()).single()
        assertEquals("Sega Genesis needs a BIOS: put bios_CD_U.bin (or bios_CD_E.bin, bios_CD_J.bin) in Bios/MD/", BiosCheck.message("GPGX", need))
    }
    @Test fun a_case_mismatch_counts_as_missing() {
        assertEquals(1, BiosCheck.missing(CoreRegistry.requiredBios("FDS"), "fds", setOf("DISKSYS.ROM")).size)
        assertEquals(1, BiosCheck.missing(CoreRegistry.requiredBios("SEGACD"), "chd", setOf("bios_cd_u.bin")).size)
    }
    @Test fun other_tags_need_nothing() {
        for (tag in listOf("PS", "GBA", "MGBA", "SGB", "LYNX", "PKM", "A5200", "A7800", "PRBOOM", "NDS", "PSP", "SMS", "C64")) {
            assertEquals(emptyList(), CoreRegistry.requiredBios(tag), tag)
        }
        assertEquals(emptyList(), BiosCheck.missing(CoreRegistry.requiredBios("FC"), "nes", emptySet()))
        assertEquals(1, BiosCheck.missing(CoreRegistry.requiredBios("FC"), "fds", emptySet()).size)
    }
    @Test fun messages_name_the_console_the_files_and_the_launched_tags_folder() {
        assertEquals("Famicom Disk System needs a BIOS: put disksys.rom in Bios/FDS/", BiosCheck.message("fds", CoreRegistry.requiredBios("FDS").single()))
        assertEquals("Sega CD needs a BIOS: put bios_CD_U.bin (or bios_CD_E.bin, bios_CD_J.bin) in Bios/SEGACD/", BiosCheck.message("SEGACD", CoreRegistry.requiredBios("SEGACD").single()))
        assertEquals("TurboGrafx-16 needs a BIOS: put syscard3.pce in Bios/PCE/", BiosCheck.message("PCE", CoreRegistry.requiredBios("PCE").single()))
    }
    @Test fun bios_folders_follow_the_bricks_layout() {
        assertEquals(listOf("FC", "FDS"), BiosFolders.forTag("FDS"))                            // Brick: Bios/FC/disksys.rom
        assertEquals(listOf("FDS", "FC"), BiosFolders.forTag("FC"))
        assertEquals(listOf("MD", "SMS", "GG", "SG1000", "32X", "SEGACD"), BiosFolders.forTag("segacd"))   // Brick: Bios/MD/bios_CD_*.bin
        assertEquals(listOf("MD", "SMS", "GG", "SEGACD", "SG1000", "GPGX"), BiosFolders.forTag("GPGX"))   // Genesis Plus GX: every console it runs, then Bios/GPGX/
        assertEquals(listOf("SEGACD", "MD", "SMS", "GG", "SG1000", "GPGX"), BiosFolders.forTag("GPGX_SEGACD"))   // handheld folders only: no Bios/GPGX_*/
        assertEquals(listOf("SMS", "GG", "SG1000", "SEGACD", "32X", "MD"), BiosFolders.forTag("MD"))
        assertEquals(listOf("PS"), BiosFolders.forTag("PS"))                                    // Brick: Bios/PS/psxonpsp660.bin
        assertEquals(listOf("MGBA", "SGB"), BiosFolders.forTag("SGB"))
        assertEquals(listOf("GBA"), BiosFolders.forTag("GBA"))
        assertEquals(listOf("C64"), BiosFolders.forTag("C64"))                                  // no core: its own folder only
    }
    @Test fun every_emulator_imports_the_console_bios_folder_before_its_own() {
        assertEquals(listOf("GBA", "SGB", "MGBA"), BiosFolders.forTag("MGBA"))   // SGB shares the mGBA core
        assertEquals(listOf("GBA"), BiosFolders.forTag("GBA"))
        assertEquals(listOf("FC", "FDS"), BiosFolders.forTag("FDS"))   // core sharing adds FC
        assertEquals(listOf("PS", "PSX"), BiosFolders.forTag("PSX"))
    }
    @Test fun the_message_names_the_console_folder() {
        assertTrue(BiosCheck.message("PSX", BiosNeed(listOf("scph5501.bin"))).endsWith("in Bios/PS/"))
    }
    @Test fun anySuffixNeedIsMetByAnyBinCaseInsensitive() {
        assertEquals(emptyList(), BiosCheck.missing(listOf(BiosNeed(emptyList(), anySuffix = ".bin")), "iso", setOf("SCPH-70012.BIN")))
    }
    @Test fun anySuffixNeedIgnoresNvmAndMec() {
        assertEquals(1, BiosCheck.missing(listOf(BiosNeed(emptyList(), anySuffix = ".bin")), "iso", setOf("ps2.nvm", "ps2.mec", "readme.txt")).size)
    }
    @Test fun anySuffixMessage() {
        assertEquals("Sony PlayStation 2 needs a BIOS: put a .bin file in Bios/PS2/", BiosCheck.message("PS2", BiosNeed(emptyList(), anySuffix = ".bin")))
    }

    @Test fun a_pc_engine_cd_needs_the_system_card_its_cd_bios_option_picks() {
        fun card(value: String?) = CoreRegistry.requiredBios("PCE") { if (it == CoreRegistry.PCE_CARD_KEY) value else null }.single()
        assertEquals(listOf("syscard3.pce"), card(null).anyOf)
        assertEquals(listOf("syscard3u.pce"), card("System Card 3 US").anyOf)
        assertEquals(listOf("gexpress.pce"), card("Games Express").anyOf)
        assertEquals(listOf("syscard3.pce"), card("unknown").anyOf)
        assertEquals(setOf("cue", "ccd", "chd", "toc", "m3u"), card("System Card 1").forExtensions)
        assertEquals(listOf(card("System Card 3 US")), BiosCheck.missing(listOf(card("System Card 3 US")), "cue", setOf("syscard3.pce")))
        assertEquals(emptyList(), BiosCheck.missing(listOf(card("System Card 3 US")), "cue", setOf("syscard3u.pce")))
        assertEquals(CoreRegistry.requiredBios("MD"), CoreRegistry.requiredBios("MD") { "System Card 1" })
        assertTrue("gexpress.pce" in CoreRegistry.biosFileNames)
    }

    @Test fun plan_dc_disc_without_bios_runs_hle() {
        val p = BiosCheck.plan("DC", CoreRegistry.requiredBios("DC"), "gdi", emptySet(), netplay = false)
        assertEquals(BiosPlan.Hle(mapOf("reicast_hle_bios" to "enabled"),
            "Running without a Dreamcast BIOS: some games may not work. Put dc_boot.bin in Bios/DC/."), p)
    }
    @Test fun plan_saturn_without_bios_runs_hle() {
        val p = BiosCheck.plan("SATURN", CoreRegistry.requiredBios("SATURN"), "cue", emptySet(), netplay = false)
        assertEquals(BiosPlan.Hle(mapOf("yabasanshiro_force_hle_bios" to "enabled"),
            "Running without a Sega Saturn BIOS: some games may not work. Put saturn_bios.bin in Bios/SATURN/."), p)
    }
    @Test fun plan_saturn_with_sega_101_is_ok() =
        assertEquals(BiosPlan.Ok, BiosCheck.plan("SATURN", CoreRegistry.requiredBios("SATURN"), "chd", setOf("sega_101.bin"), false))
    @Test fun plan_dc_with_bios_is_ok() =
        assertEquals(BiosPlan.Ok, BiosCheck.plan("DC", CoreRegistry.requiredBios("DC"), "chd", setOf("dc_boot.bin"), false))
    @Test fun plan_present_bios_is_ok_even_if_bad() =   // the checksum check is the pipeline's, after the plan
        assertEquals(BiosPlan.Ok, BiosCheck.plan("DC", CoreRegistry.requiredBios("DC"), "cue", setOf("dc_boot.bin"), false))
    @Test fun plan_dc_netplay_without_bios_runs_hle_without_forcing() {
        val needs = CoreRegistry.requiredBios("DC")
        assertEquals(BiosPlan.Hle(emptyMap(), BiosCheck.hleNotice("DC", needs.first())),
            BiosCheck.plan("DC", needs, "gdi", emptySet(), netplay = true))
    }

    @Test fun plan_netplay_without_an_hle_fallback_still_refuses() {
        val need = BiosNeed(listOf("x.bin"))
        assertEquals(BiosPlan.Refuse(BiosCheck.message("SEGACD", need)),
            BiosCheck.plan("SEGACD", listOf(need), "iso", emptySet(), netplay = true))
    }
    @Test fun plan_naomi_zip_is_not_hle() =
        assertEquals(BiosPlan.Ok, BiosCheck.plan("DC", CoreRegistry.requiredBios("DC"), "zip", emptySet(), false))
    @Test fun plan_without_hle_still_refuses() =
        assertEquals(BiosPlan.Refuse(BiosCheck.message("FDS", BiosNeed(listOf("disksys.rom")))),
            BiosCheck.plan("FDS", CoreRegistry.requiredBios("FDS"), "fds", emptySet(), false))

    @Test fun hle_notice_names_the_console_and_folder_from_the_registry() =
        assertEquals("Running without a Sega CD BIOS: some games may not work. Put x.bin in Bios/SEGACD/.",
            BiosCheck.hleNotice("SEGACD", BiosNeed(listOf("x.bin"), hle = HleFallback("k", "v"))))
    @Test fun hle_notice_for_a_suffix_need_has_no_first_name_to_read() =
        assertEquals("Running without a Sony PlayStation 2 BIOS: some games may not work. Put a .bin file in Bios/PS2/.",
            BiosCheck.hleNotice("PS2", BiosNeed(emptyList(), anySuffix = ".bin", hle = HleFallback("k", "v"))))

    @Test fun notice_due_once_per_console() {
        val hle = BiosCheck.plan("DC", CoreRegistry.requiredBios("DC"), "gdi", emptySet(), netplay = false)
        val notice = (hle as BiosPlan.Hle).notice
        assertEquals(notice, BiosCheck.noticeDue(hle, "DC", emptySet()))
        assertEquals(null, BiosCheck.noticeDue(hle, "DC", setOf("DC")))
        assertEquals(null, BiosCheck.noticeDue(hle, " dc ", setOf(BiosCheck.noticeKey("DC"))))
        assertEquals(null, BiosCheck.noticeDue(BiosPlan.Ok, "DC", emptySet()))
    }
}
