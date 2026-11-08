package com.nxredux.mobile.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncConsolesTest {
    @Test fun same_names_pair() {
        val p = SyncConsoles.pair(listOf("Game Boy (GB)", "Nintendo 3DS (3DS)"), listOf("Game Boy (GB)", "Amiga (PUAE)"))
        assertEquals(listOf(ConsolePair("Game Boy (GB)", "Game Boy (GB)", "GB")), p.pairs)
        assertEquals(listOf("3DS", "PUAE"), p.skippedTags)
    }

    @Test fun different_names_pair_by_a_unique_tag() {
        val p = SyncConsoles.pair(listOf("Nintendo ES (FC)"), listOf("Famicom (FC)"))
        assertEquals(listOf(ConsolePair("Nintendo ES (FC)", "Famicom (FC)", "FC")), p.pairs)
    }

    @Test fun ambiguous_tags_are_skipped_but_exact_names_still_pair() {
        val local = listOf("Sega Genesis (GPGX)", "Sega CD (GPGX)", "Sega Game Gear (GPGX)")
        val remote = listOf("Sega Genesis (GPGX)", "Mega CD (GPGX)", "Sega Master System (GPGX)")
        val p = SyncConsoles.pair(local, remote)
        assertEquals(listOf(ConsolePair("Sega Genesis (GPGX)", "Sega Genesis (GPGX)", "GPGX")), p.pairs)
        assertEquals(listOf("GPGX"), p.skippedTags)
    }

    // A handheld peer.

    private val gbaGba = "Game Boy Advance (GBA)"
    private val gbaMgba = "Game Boy Advance (MGBA)"

    @Test fun mapped_pairing_lands_every_emulator_folder_in_the_console_folder() {
        val p = SyncConsoles.pairMapped(
            listOf(gbaGba, gbaMgba, "Dreamcast (DC)"),
            listOf(gbaGba, gbaMgba, "Dreamcast (DC)", "Dreamcast (DCX)", "Amiga (AMIGA)"),
        )
        assertEquals(listOf(
            ConsolePair(gbaGba, gbaGba, "GBA", setOf("GBA")),
            ConsolePair(gbaGba, gbaMgba, "MGBA", setOf("MGBA")),
            ConsolePair("Dreamcast (DC)", "Dreamcast (DC)", "DC", setOf("DC")),
            ConsolePair("Dreamcast (DC)", "Dreamcast (DCX)", "DCX", emptySet()),
        ), p.pairs)
        assertEquals(listOf("AMIGA"), p.skippedTags)
        assertEquals(p.pairs.size, p.pairs.map { it.key }.toSet().size)
    }

    /** The handheld has no `(GBA)` folder, so GBA games go to its `(MGBA)` one. */
    @Test fun a_game_goes_to_the_first_mapped_folder_when_its_emulator_folder_is_missing() {
        val p = SyncConsoles.pairMapped(listOf(gbaGba), listOf(gbaMgba))
        assertEquals(listOf(ConsolePair(gbaGba, gbaMgba, "MGBA", setOf("MGBA", "GBA"))), p.pairs)
        assertEquals(emptyList(), p.skippedTags)
    }

    /** Two handheld folders of one tag: only one is that tag's push target, the console-named one; the other only pulls. */
    @Test fun two_same_tag_folders_give_the_tag_to_one() {
        val p = SyncConsoles.pairMapped(listOf(gbaGba), listOf("GBA Hacks (GBA)", gbaGba, gbaMgba))
        assertEquals(listOf(
            ConsolePair(gbaGba, "GBA Hacks (GBA)", "GBA", emptySet()),
            ConsolePair(gbaGba, gbaGba, "GBA", setOf("GBA")),
            ConsolePair(gbaGba, gbaMgba, "MGBA", setOf("MGBA")),
        ), p.pairs)
    }

    /** `Mega CD (GPGX)` is Sega CD by its name: it never pairs with Genesis, though GPGX is a Genesis tag. */
    @Test fun mega_cd_pairs_with_sega_cd_never_genesis() {
        val remote = listOf("Mega CD (GPGX)", "Sega Genesis (GPGX)")
        val p = SyncConsoles.pairMapped(listOf("Sega Genesis (MD)", "Sega CD (SEGACD)"), remote)
        assertEquals(listOf(
            ConsolePair("Sega Genesis (MD)", "Sega Genesis (GPGX)", "GPGX", setOf("GPGX", "MD")),
            ConsolePair("Sega CD (SEGACD)", "Mega CD (GPGX)", "GPGX", setOf("SEGACD", "GPGX_SEGACD")),
        ), p.pairs)
        val noSegaCd = SyncConsoles.pairMapped(listOf("Sega Genesis (MD)"), remote)
        assertEquals(listOf(ConsolePair("Sega Genesis (MD)", "Sega Genesis (GPGX)", "GPGX", setOf("GPGX", "MD"))), noSegaCd.pairs)
        assertEquals(listOf("GPGX"), noSegaCd.skippedTags)
    }

    /** The handheld's (GPGX) Master System folder is the Master System's, and the GPGX_SMS emulator's games push there. */
    @Test fun a_handheld_gpgx_master_system_folder_takes_the_gpgx_sms_games() {
        val local = "Sega Master System (SMS)"
        val p = SyncConsoles.pairMapped(listOf(local), listOf("Sega Master System (SMS)", "Sega Master System (GPGX)"))
        assertEquals(listOf(
            ConsolePair(local, "Sega Master System (SMS)", "SMS", setOf("SMS")),
            ConsolePair(local, "Sega Master System (GPGX)", "GPGX", setOf("GPGX_SMS")),
        ), p.pairs)
        // Only the (GPGX) folder there: both emulators' games go to it.
        val only = SyncConsoles.pairMapped(listOf(local), listOf("Sega Master System (GPGX)"))
        assertEquals(listOf(ConsolePair(local, "Sega Master System (GPGX)", "GPGX", setOf("SMS", "GPGX_SMS"))), only.pairs)
        // A phone game on GPGX_SMS is held back from (SMS) and pushed to (GPGX).
        val tags = mapOf("Alex Kidd.sms" to "GPGX_SMS", "Wonder Boy.sms" to "SMS")
        assertEquals(listOf("Alex Kidd.sms"), SyncRoms.held(p.pairs[0], tags.keys, tags))
        assertEquals(listOf("Wonder Boy.sms"), SyncRoms.held(p.pairs[1], tags.keys, tags))
    }

    /** A folder of no phone console still pairs with the same name on the handheld. */
    @Test fun console_less_folders_pair_by_exact_name() {
        val p = SyncConsoles.pairMapped(listOf("Amiga (AMIGA)", "Other (XYZ)"), listOf("Amiga (AMIGA)", "Commodore (C64)"))
        assertEquals(listOf(ConsolePair("Amiga (AMIGA)", "Amiga (AMIGA)", "AMIGA")), p.pairs)
        assertEquals(listOf("C64", "XYZ"), p.skippedTags)
    }

    @Test fun renamed_console_folders_pair_by_tag() {
        val p = SyncConsoles.pairMapped(listOf("Famicom (FC)"), listOf("Nintendo ES (FC)"))
        assertEquals(listOf(ConsolePair("Famicom (FC)", "Nintendo ES (FC)", "FC")), p.pairs)
    }

    @Test fun tags_match_ignoring_case_and_dot_folders_and_plain_names_are_ignored() {
        val p = SyncConsoles.pair(listOf("Game Boy (gb)", ".sources", "map.txt", "Loose"), listOf("GB stuff (GB)", ".media"))
        assertEquals(listOf(ConsolePair("Game Boy (gb)", "GB stuff (GB)", "GB")), p.pairs)
        assertEquals(emptyList(), p.skippedTags)
    }

    @Test fun listing_keeps_directory_names_with_spaces() {
        val lines = listOf(
            "drwxr-xr-x          4,096 2026/10/15 12:00:00 .",
            "drwxr-xr-x          4,096 2026/10/15 12:00:00 Game Boy (GB)",
            "-rw-r--r--             12 2026/10/15 12:00:00 map.txt",
            "drwxr-xr-x          4,096 2026/10/15 12:00:00 Pokémon mini (PKM)",
            "",
        )
        assertEquals(listOf("Game Boy (GB)", "Pokémon mini (PKM)"), SyncConsoles.parseListing(lines))
    }

    /** rsync without `--8-bit-output` writes each non-ASCII byte as `\#ooo`: decoded back to the name (UTF-8). */
    @Test fun listing_decodes_rsync_byte_escapes() {
        val lines = listOf("drwxr-xr-x          4,096 2026/10/15 12:00:00 Pok\\#303\\#251mon mini (PKM)")
        assertEquals(listOf("Pokémon mini (PKM)"), SyncConsoles.parseListing(lines))
    }
    @Test fun siblings_share_the_display_name_before_the_tag() {
        val all = listOf("Game Boy Advance (GBA)", "Game Boy Advance (MGBA)", "game boy advance (GBA2)", "Game Boy (GB)",
            "Sega CD (GPGX)", "Sega CD (SEGACD)", "Sega Genesis (GPGX)", ".media", "Game Boy Advance")
        assertEquals(listOf("Game Boy Advance (MGBA)", "game boy advance (GBA2)"), SyncConsoles.siblings("Game Boy Advance (GBA)", all))
        assertEquals(listOf("Sega CD (SEGACD)"), SyncConsoles.siblings("Sega CD (GPGX)", all))
        assertEquals(emptyList(), SyncConsoles.siblings("Sega Genesis (GPGX)", all))
        assertEquals(emptyList(), SyncConsoles.siblings("Game Boy (GB)", all))
        assertEquals(emptyList(), SyncConsoles.siblings("Loose", all))
    }

    @Test fun entries_are_every_top_level_file_and_folder() {
        val lines = listOf(
            "drwxr-xr-x          4,096 2026/10/15 12:00:00 .",
            "drwxr-xr-x          4,096 2026/10/15 12:00:00 Disc Game",
            "-rw-r--r--      1,048,576 2026/10/15 12:00:00 Pokemon Emerald.gba",
            "-rw-r--r--             12 2026/10/15 12:00:00 Pok\\#303\\#251mon.gba",
            "drwxr-xr-x          4,096 2026/10/15 12:00:00 .media",
            "",
        )
        assertEquals(listOf("Disc Game", "Pokemon Emerald.gba", "Pokémon.gba", ".media"), SyncConsoles.parseEntries(lines))
    }

    /** Only lines that start with a mode string are entries: a daemon MOTD or a warning line is never a name. */
    @Test fun entries_ignore_lines_without_a_mode_string() {
        val lines = listOf(
            "Welcome to the NX Redux sync daemon on this phone",
            "rsync: [Receiver] warning: some note about the listing here",
            "d.rwxr-xr-x           4,096 2026/10/15 12:00:00 not a mode",
            "lrwxrwxrwx             10 2026/10/15 12:00:00 link.gba -> real.gba",
            "-rw-r--r--             12 2026/10/15 12:00:00 game.gba",
        )
        assertEquals(listOf("link.gba", "game.gba"), SyncConsoles.parseEntries(lines))
    }
}
