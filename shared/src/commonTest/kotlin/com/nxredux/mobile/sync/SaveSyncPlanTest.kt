package com.nxredux.mobile.sync

import com.nxredux.mobile.storage.SaveFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveSyncPlanTest {
    private val hh = listOf("Game Boy Advance (GBA)", "Game Boy Advance (MGBA)", "Sega Genesis (MD)", "Sega Genesis (GPGX)",
        "Sega Master System (GPGX)", "Dreamcast (DC)", "Dreamcast (DCX)")

    private fun plan(roms: Map<String, Set<String>>, default: String? = null, format: SaveFormat = SaveFormat.RETROARCH,
                     romNames: Map<String, String> = emptyMap(), folders: List<String>? = hh) =
        SaveSyncPlan(folders, roms, emulatorTag = { c, _ -> default ?: c }, romName = { _, g -> romNames[g] }, format = format)

    @Test fun send_goes_where_the_game_is() {
        val p = plan(mapOf("Game Boy Advance (GBA)" to setOf("Emerald.gba"), "Game Boy Advance (MGBA)" to setOf("Emerald.gba")))
        assertEquals(
            listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.srm"), StageCopy("GBA/Emerald.srm", "MGBA/Emerald.srm")),
            p.send(listOf(SaveFile("GBA/Emerald.srm", 1000))),
        )
    }

    @Test fun send_without_the_rom_goes_to_the_emulator_folder() {
        val p = plan(emptyMap(), default = "MGBA")
        assertEquals(listOf(StageCopy("GBA/Emerald.srm", "MGBA/Emerald.srm")), p.send(listOf(SaveFile("GBA/Emerald.srm", 1000))))
    }

    @Test fun send_to_gpgx_only_when_the_rom_is_in_that_consoles_gpgx_folder() {
        val save = listOf(SaveFile("SMS/Alex Kidd.srm", 1000))
        val withRom = plan(mapOf("Sega Master System (GPGX)" to setOf("Alex Kidd.sms")))
        assertEquals(listOf(StageCopy("SMS/Alex Kidd.srm", "GPGX/Alex Kidd.srm")), withRom.send(save))
        // The ROM sits in Genesis's GPGX folder only: the shared folder would credit Genesis, so not there.
        val elsewhere = plan(mapOf("Sega Genesis (GPGX)" to setOf("Alex Kidd.sms")), default = "GPGX")
        assertEquals(listOf(StageCopy("SMS/Alex Kidd.srm", "SMS/Alex Kidd.srm")), elsewhere.send(save))
        // Nowhere on the handheld: its ROM push goes to `Sega Master System (GPGX)`, so the save goes with it.
        val noRom = plan(emptyMap(), default = "GPGX")
        assertEquals(listOf(StageCopy("SMS/Alex Kidd.srm", "GPGX/Alex Kidd.srm")), noRom.send(save))
    }

    /** The save goes where the game's ROM push goes, not to an emulator folder the handheld lacks. */
    @Test fun send_without_the_rom_follows_the_rom_push_target() {
        val gbaOnly = plan(emptyMap(), default = "MGBA", folders = listOf("Game Boy Advance (GBA)"))
        assertEquals(listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.srm")), gbaOnly.send(listOf(SaveFile("GBA/Emerald.srm", 1000))))
        val mgbaOnly = plan(emptyMap(), folders = listOf("Game Boy Advance (MGBA)"))
        assertEquals(listOf(StageCopy("GBA/Emerald.srm", "MGBA/Emerald.srm")), mgbaOnly.send(listOf(SaveFile("GBA/Emerald.srm", 1000))))
    }

    /** A Genesis game pushes to `Sega Genesis (GPGX)` (no `(MD)` folder): its save is staged to the shared GPGX folder. */
    @Test fun send_stages_a_genesis_save_to_gpgx_when_that_is_its_push_target() {
        val folders = listOf("Sega Genesis (GPGX)", "Sega Master System (GPGX)")
        val p = SaveSyncPlan(folders, emptyMap(), emulatorTag = { c, _ -> c },
            romName = { c, g -> "Sonic.md".takeIf { c == "MD" && g == "Sonic" } }, format = SaveFormat.RETROARCH)
        assertEquals(listOf(StageCopy("MD/Sonic.srm", "GPGX/Sonic.srm")), p.send(listOf(SaveFile("MD/Sonic.srm", 1000))))
        // Received back from there (newer), it folds to Genesis rather than being skipped as shared.
        val fold = p.fold(listOf(SaveFile("GPGX/Sonic.srm", 9000)), listOf(SaveFile("MD/Sonic.srm", 1000)))
        assertEquals(listOf(FoldWrite("GPGX/Sonic.srm", "MD/Sonic.srm", 9000, battery = true)), fold.writes)
        assertEquals(0, fold.skipped)
    }

    /** A Master System game on Genesis Plus GX here (GPGX_SMS) is the handheld's GPGX: its save goes with its ROM push. */
    @Test fun a_gpgx_sms_game_saves_through_the_handhelds_gpgx_folder() {
        val folders = listOf("Sega Master System (SMS)", "Sega Master System (GPGX)", "Sega Genesis (GPGX)")
        fun on(tag: String, roms: Map<String, Set<String>> = emptyMap(), listed: List<String>? = folders) =
            SaveSyncPlan(listed, roms, emulatorTag = { _, _ -> tag }, romName = { _, _ -> null }, format = SaveFormat.RETROARCH)
        val save = listOf(SaveFile("SMS/Alex Kidd.srm", 1000))
        // Held nowhere: GPGX_SMS's ROM push goes to the (GPGX) Master System folder, PicoDrive's (SMS) to (SMS).
        assertEquals(listOf(StageCopy("SMS/Alex Kidd.srm", "GPGX/Alex Kidd.srm")), on("GPGX_SMS").send(save))
        assertEquals(listOf(StageCopy("SMS/Alex Kidd.srm", "SMS/Alex Kidd.srm")), on("SMS").send(save))
        // No listing: the shared GPGX folder is never staged to; the console's own folder is.
        assertEquals(listOf(StageCopy("SMS/Alex Kidd.srm", "SMS/Alex Kidd.srm")), on("GPGX_SMS", listed = null).send(save))
        // Back: a Saves/GPGX save of a game in the handheld's (GPGX) Master System folder is the Master System's.
        val fold = on("GPGX_SMS", mapOf("Sega Master System (GPGX)" to setOf("Alex Kidd.sms")))
            .fold(listOf(SaveFile("GPGX/Alex Kidd.srm", 9000)), emptyList())
        assertEquals(listOf(FoldWrite("GPGX/Alex Kidd.srm", "SMS/Alex Kidd.srm", 9000, battery = true)), fold.writes)
        assertTrue("GPGX" in on("GPGX_SMS").receiveTags)
        assertTrue(on("GPGX_SMS").receiveTags.none { it.startsWith("GPGX_") })   // never an internal tag on the handheld
    }

    /** With no handheld ROM listing, `Saves/GPGX` may hold any Sega console's saves: skipped, never staged. */
    @Test fun without_a_listing_gpgx_saves_are_skipped() {
        val p = plan(emptyMap(), default = "GPGX", folders = null)
        assertTrue("GPGX" in p.receiveTags)
        val fold = p.fold(listOf(SaveFile("GPGX/Alex Kidd.srm", 9000), SaveFile("GPGX/Sonic.srm", 9000)), emptyList())
        assertEquals(emptyList(), fold.writes)
        assertEquals(2, fold.skipped)
        assertEquals(listOf(StageCopy("MD/Sonic.srm", "MD/Sonic.srm")), p.send(listOf(SaveFile("MD/Sonic.srm", 1000))))
    }

    @Test fun non_keyed_files_go_by_path_to_the_console_folder() {
        val p = plan(mapOf("Dreamcast (DC)" to setOf("Game.chd"), "Dreamcast (DCX)" to setOf("Game.chd")))
        assertEquals(
            listOf(StageCopy("DC/reicast/Game.nvmem", "DC/reicast/Game.nvmem"), StageCopy("DC/vmu_save_A1.bin", "DC/vmu_save_A1.bin")),
            p.send(listOf(SaveFile("DC/vmu_save_A1.bin", 1000), SaveFile("DC/reicast/Game.nvmem", 1000))),
        )
    }

    @Test fun a_saturn_backup_ram_goes_by_path_like_a_dreamcast_card() {
        val p = plan(mapOf("Sega Saturn (SATURN)" to setOf("Game.cue")))
        assertEquals(
            listOf(StageCopy("SATURN/Panzer Dragoon.bkr", "SATURN/Panzer Dragoon.bkr")),
            p.send(listOf(SaveFile("SATURN/Panzer Dragoon.bkr", 1000))),
        )
    }

    @Test fun leftover_tag_folder_counts_as_the_console() {
        val p = plan(emptyMap())
        val phone = listOf(SaveFile("MGBA/Old.srm", 1000), SaveFile("MGBA/sub/x", 1000))
        assertEquals(
            listOf(StageCopy("MGBA/Old.srm", "GBA/Old.srm"), StageCopy("MGBA/sub/x", "GBA/sub/x")),
            p.send(phone),
        )
        val fold = p.fold(listOf(SaveFile("GBA/Old.srm", 5000)), phone)
        assertEquals(listOf(FoldWrite("GBA/Old.srm", "GBA/Old.srm", 5000, battery = true)), fold.writes)
        assertEquals(listOf("MGBA/Old.srm"), fold.removes)
    }

    @Test fun send_stages_only_the_newest_of_a_leftover_and_the_console_folder() {
        val p = plan(emptyMap())
        val staged = p.send(listOf(SaveFile("GBA/Emerald.srm", 9000), SaveFile("MGBA/Emerald.srm", 1000),
            SaveFile("GBA/sub/x", 1000), SaveFile("MGBA/sub/x", 9000)))
        assertEquals(listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.srm"), StageCopy("MGBA/sub/x", "GBA/sub/x")), staged)
        assertEquals(staged.map { it.to }.distinct(), staged.map { it.to })
        // Same time: the console-tag folder wins.
        assertEquals(listOf(StageCopy("GBA/Old.srm", "GBA/Old.srm")),
            p.send(listOf(SaveFile("MGBA/Old.srm", 5000), SaveFile("GBA/Old.srm", 5000))))
    }

    @Test fun send_stages_only_the_newest_format_of_a_game() {
        val p = plan(emptyMap())
        assertEquals(listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.srm")),
            p.send(listOf(SaveFile("GBA/Emerald.srm", 9000), SaveFile("GBA/Emerald.sav", 1000))))
        assertEquals(listOf(StageCopy("GBA/Emerald.gba.sav", "GBA/Emerald.gba.sav")),
            p.send(listOf(SaveFile("GBA/Emerald.srm", 1000), SaveFile("GBA/Emerald.gba.sav", 9000))))
    }

    @Test fun fold_takes_the_newest_across_folders_and_formats() {
        val p = plan(emptyMap())
        val fold = p.fold(
            listOf(SaveFile("GBA/Emerald.sav", 3000), SaveFile("MGBA/Emerald.gba.sav", 9000)),
            listOf(SaveFile("GBA/Emerald.srm", 5000)),
        )
        assertEquals(listOf(FoldWrite("MGBA/Emerald.gba.sav", "GBA/Emerald.srm", 9000, battery = true)), fold.writes)
        assertEquals(emptyList(), fold.removes)
        assertEquals(0, fold.skipped)
    }

    @Test fun fold_in_minui_needs_the_rom_name() {
        val received = listOf(SaveFile("GBA/Emerald.srm", 9000))
        val phone = listOf(SaveFile("GBA/Emerald.srm", 1000))
        val named = plan(emptyMap(), format = SaveFormat.MINUI, romNames = mapOf("Emerald" to "Emerald.gba")).fold(received, phone)
        assertEquals(listOf(FoldWrite("GBA/Emerald.srm", "GBA/Emerald.gba.sav", 9000, battery = true)), named.writes)
        assertEquals(listOf("GBA/Emerald.srm"), named.removes)
        val unnamed = plan(emptyMap(), format = SaveFormat.MINUI).fold(received, phone)
        assertEquals(listOf(FoldWrite("GBA/Emerald.srm", "GBA/Emerald.srm", 9000, battery = true)), unnamed.writes)
        assertEquals(emptyList(), unnamed.removes)
    }

    @Test fun unchanged_round_trip_writes_nothing() {
        val p = plan(mapOf("Game Boy Advance (GBA)" to setOf("Emerald.gba")))
        val phone = listOf(SaveFile("GBA/Emerald.srm", 5000), SaveFile("DC/vmu_save_A1.bin", 7000))
        val fold = p.fold(listOf(SaveFile("GBA/Emerald.srm", 5000), SaveFile("MGBA/Emerald.srm", 5900),
            SaveFile("DC/vmu_save_A1.bin", 7000)), phone)
        assertEquals(emptyList(), fold.writes)
        assertEquals(emptyList(), fold.removes)
    }

    @Test fun phone_newer_writes_nothing() {
        val p = plan(emptyMap())
        val fold = p.fold(listOf(SaveFile("GBA/Emerald.sav", 3000), SaveFile("DC/reicast/x.nvmem", 3000)),
            listOf(SaveFile("GBA/Emerald.srm", 9000), SaveFile("DC/reicast/x.nvmem", 9000)))
        assertEquals(emptyList(), fold.writes)
        assertEquals(emptyList(), fold.removes)
    }

    @Test fun gpgx_save_in_two_consoles_is_skipped_and_counted() {
        val p = plan(mapOf("Sega Genesis (GPGX)" to setOf("Sonic.md"), "Sega Master System (GPGX)" to setOf("Sonic.md")))
        val fold = p.fold(listOf(SaveFile("GPGX/Sonic.srm", 9000), SaveFile("GPGX/Unknown.srm", 9000)), emptyList())
        assertEquals(emptyList(), fold.writes)
        assertEquals(2, fold.skipped)
        // One console's GPGX folder holds it: that console takes it.
        val one = plan(mapOf("Sega Master System (GPGX)" to setOf("Sonic.sms")))
            .fold(listOf(SaveFile("GPGX/Sonic.srm", 9000)), emptyList())
        assertEquals(listOf(FoldWrite("GPGX/Sonic.srm", "SMS/Sonic.srm", 9000, battery = true)), one.writes)
        assertEquals(0, one.skipped)
    }

    @Test fun receive_tags_list_the_mapped_folders() {
        val tags = plan(emptyMap()).receiveTags
        assertTrue(tags.containsAll(listOf("GBA", "MGBA", "GPGX", "DC", "PS", "PSX")))
        assertFalse("DCX" in tags)
        assertEquals(tags.sorted(), tags)
    }

    @Test fun clock_files_follow_their_game() {
        val p = plan(mapOf("Game Boy Color (GBC)" to setOf("Crystal.gbc")), folders = hh + "Game Boy Color (GBC)")
        val fold = p.fold(listOf(SaveFile("GBC/Crystal.gbc.rtc", 9000)), emptyList())
        assertEquals(listOf(FoldWrite("GBC/Crystal.gbc.rtc", "GBC/Crystal.gbc.rtc", 9000, battery = false)), fold.writes)
    }

    /** A file's time and bytes, for [SyncedPair]. */
    private data class Copy(val mtime: Long, val bytes: String)

    /**
     * One phone and one handheld `Saves/` run through the save part of a sync as the controller, [SyncSaveFiles] and
     * rsync `--update` do it (a destination file is replaced when older, or as old with other bytes); [sync] returns
     * how many files moved: pushed, downloaded, written or removed on the phone.
     */
    private class SyncedPair(val plan: SaveSyncPlan, val phone: MutableMap<String, Copy>, val handheld: MutableMap<String, Copy>) {
        private fun newer(src: Copy, dst: Copy?) = dst == null || dst.mtime < src.mtime || (dst.mtime == src.mtime && dst.bytes != src.bytes)

        fun sync(): Int {
            var moved = 0
            val mine = phone.map { (k, v) -> SaveFile(k, v.mtime) }
            val sendTree = plan.send(mine).associate { it.to to phone.getValue(it.from) }
            val seeds = plan.seeds(mine).associate { s -> s.to to phone.getValue(s.from) }
            val recvTree = (sendTree + seeds).toMutableMap()
            for ((path, c) in sendTree) if (newer(c, handheld[path])) { handheld[path] = c; moved++ }
            for ((path, c) in handheld) if (path.substringBefore('/') in plan.receiveTags && newer(c, recvTree[path])) { recvTree[path] = c; moved++ }
            val fold = plan.fold(recvTree.map { (k, v) -> SaveFile(k, v.mtime) }, mine)
            for (w in fold.writes) { phone[w.to] = recvTree.getValue(w.from).copy(mtime = w.mtime); moved++ }
            for (r in fold.removes) if (phone.remove(r) != null) moved++
            return moved
        }
    }

    /** One game under two formats on the handheld is downloaded once, not on every sync. */
    @Test fun a_handhelds_two_formats_of_one_game_move_once() {
        val roms = mapOf("Game Boy Advance (GBA)" to setOf("Emerald.gba"), "Game Boy Advance (MGBA)" to emptySet<String>())
        val p = plan(roms, folders = roms.keys.toList())
        // The phone-format name is the newest: the first sync takes it, the second moves nothing.
        val newestSrm = SyncedPair(p, mutableMapOf(), mutableMapOf(
            "GBA/Emerald.srm" to Copy(9000, "NEW"), "GBA/Emerald.gba.sav" to Copy(5000, "OLD"), "MGBA/Emerald.sav" to Copy(3000, "OLDER")))
        assertTrue(newestSrm.sync() > 0)
        assertEquals(mapOf("GBA/Emerald.srm" to Copy(9000, "NEW")), newestSrm.phone)
        assertEquals(0, newestSrm.sync())
        // The other name is the newest: the phone takes it; the next sync refreshes the handheld's stale phone-format
        // copy once (the phone's newest goes where the game is), then nothing moves and no newest copy is lost.
        val newestSav = SyncedPair(p, mutableMapOf(), mutableMapOf(
            "GBA/Emerald.srm" to Copy(5000, "OLD"), "GBA/Emerald.gba.sav" to Copy(9000, "NEW")))
        assertTrue(newestSav.sync() > 0)
        assertEquals(mapOf("GBA/Emerald.srm" to Copy(9000, "NEW")), newestSav.phone)
        assertEquals(1, newestSav.sync())
        assertEquals(Copy(9000, "NEW"), newestSav.handheld["GBA/Emerald.srm"])
        assertEquals(0, newestSav.sync())
        assertEquals(mapOf("GBA/Emerald.srm" to Copy(9000, "NEW")), newestSav.phone)
        // A newer handheld save under any name still comes through.
        newestSav.handheld["GBA/Emerald.gba.sav"] = Copy(20_000, "NEWER")
        assertEquals(2, newestSav.sync())
        assertEquals(mapOf("GBA/Emerald.srm" to Copy(20_000, "NEWER")), newestSav.phone)
    }

    @Test fun seeds_cover_the_other_names_in_folders_crediting_the_game() {
        val p = plan(mapOf("Sega Master System (GPGX)" to setOf("Alex Kidd.sms")), romNames = mapOf("Alex Kidd" to "Alex Kidd.sms"))
        val seeds = p.seeds(listOf(SaveFile("SMS/Alex Kidd.srm", 1000)))
        // Staged to the shared GPGX folder (its ROM is there), so seeded there; SMS serves Master System alone.
        assertEquals(listOf("GPGX/Alex Kidd.sav", "GPGX/Alex Kidd.sms.sav", "SMS/Alex Kidd.sav", "SMS/Alex Kidd.sms.sav", "SMS/Alex Kidd.srm"),
            seeds.map { it.to })
        assertTrue(seeds.all { it.from == "SMS/Alex Kidd.srm" })
        // Never in a shared folder the save is not staged to; no seeds for files that are not battery or clock files.
        val genesis = plan(mapOf("Sega Genesis (GPGX)" to setOf("Alex Kidd.sms")), default = "GPGX")
        assertTrue(genesis.seeds(listOf(SaveFile("SMS/Alex Kidd.srm", 1000))).none { it.to.startsWith("GPGX/") })
        assertEquals(emptyList(), p.seeds(listOf(SaveFile("DC/vmu_save_A1.bin", 1000))))
    }

    /**
     * With no ROM known, `Emerald.gba.sav` may be ROM `Emerald.gba.zip`'s Generic name, so it is not
     * renamed after ROM `Emerald.gba` (Generic `Emerald.sav`, which minarch would miss); a phone copy's name settles it.
     */
    @Test fun a_received_save_named_after_an_unknown_rom_file_keeps_its_name() {
        val generic = plan(emptyMap(), format = SaveFormat.GENERIC)
        assertEquals(listOf(FoldWrite("GBA/Emerald.gba.sav", "GBA/Emerald.gba.sav", 9000, battery = true)),
            generic.fold(listOf(SaveFile("GBA/Emerald.gba.sav", 9000)), emptyList()).writes)
        val retroarch = plan(emptyMap())
        val zipped = retroarch.fold(listOf(SaveFile("GBA/Emerald.gba.sav", 9000)), listOf(SaveFile("GBA/Emerald.gba.zip.sav", 1000)))
        assertEquals(listOf(FoldWrite("GBA/Emerald.gba.sav", "GBA/Emerald.gba.srm", 9000, battery = true)), zipped.writes)
        assertEquals(listOf("GBA/Emerald.gba.zip.sav"), zipped.removes)
        val plain = retroarch.fold(listOf(SaveFile("GBA/Emerald.gba.sav", 9000)), listOf(SaveFile("GBA/Emerald.srm", 1000)))
        assertEquals(listOf(FoldWrite("GBA/Emerald.gba.sav", "GBA/Emerald.srm", 9000, battery = true)), plain.writes)
        // A known ROM still names it.
        val known = plan(emptyMap(), format = SaveFormat.GENERIC, romNames = mapOf("Emerald" to "Emerald.gba"))
        assertEquals("GBA/Emerald.sav", known.fold(listOf(SaveFile("GBA/Emerald.gba.sav", 9000)), emptyList()).writes.single().to)
    }
}
