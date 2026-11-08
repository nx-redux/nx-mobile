package com.nxredux.mobile.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncCommandsTest {
    private val opts = listOf("-rtv", "--update", "--no-perms", "--omit-dir-times", "--modify-window=1",
        "--info=progress2", "--timeout=30", "--contimeout=10")
    /** No `--inplace` anywhere: the receiver writes a temp file and renames it, so a cut transfer never leaves a truncated, fresh file. */
    private val pullOpts = opts

    @Test fun steps_are_the_handhelds_six_phases_in_order() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), SyncStep.entries.map { it.number })
        assertEquals(listOf("Sending save states", "Sending save files", "Receiving save states", "Receiving save files",
            "Sending BIOS", "Receiving BIOS", "Syncing collections", "Pushing ROMs", "Pulling ROMs"), SyncStep.entries.map { it.label })
        assertEquals(listOf("bios", "bios"), listOf(SyncStep.SEND_BIOS, SyncStep.RECEIVE_BIOS).map { it.module })
        assertEquals(listOf(true, false), listOf(SyncStep.SEND_BIOS, SyncStep.RECEIVE_BIOS).map { it.push })
        assertEquals("collections", SyncStep.SYNC_COLLECTIONS.module)
        assertEquals("5/6 Sending BIOS: PS", DeviceSyncText.step(SyncStep.SEND_BIOS, 6, "PS"))
        assertEquals("7/9 Syncing collections", DeviceSyncText.step(SyncStep.SYNC_COLLECTIONS, 9))
    }

    /** Collections move without `--update`: a peer clock ahead would otherwise skip the merged push. `map.txt` never moves. */
    @Test fun collections_pull_and_push_args_take_top_level_txt_without_update() {
        val noUpdate = opts - "--update"
        val filters = listOf("--exclude=._*", "--exclude=.DS_Store", "--exclude=/[mM][aA][pP].[tT][xX][tT]", "--include=/*.[tT][xX][tT]", "--exclude=*")
        assertEquals(listOf("/b") + noUpdate + filters + listOf("rsync://10.0.0.2:18730/collections/", "/c/pull/"),
            SyncCommands.collectionsArgs("/b", "10.0.0.2", "/c/pull", push = false))
        assertEquals(listOf("/b") + noUpdate + filters + listOf("/c/push/", "rsync://10.0.0.2:18730/collections/"),
            SyncCommands.collectionsArgs("/b", "10.0.0.2", "/c/push/", push = true))
        assertFalse("--update" in SyncCommands.collectionsArgs("/b", "10.0.0.2", "/c", push = true))
        assertFailsWith<IllegalArgumentException> { SyncCommands.collectionsArgs("/b", "x", "/c", push = true) }
    }

    @Test fun daemon_conf_adds_collections_only_with_a_collections_folder() {
        assertTrue("[collections]" !in SyncCommands.daemonConf("192.168.1.10", "/s", "/v", "/p", "/l"))
        val c = SyncCommands.daemonConf("192.168.1.10", "/s", "/v", "/p", "/l", collections = "/sd/Collections")
        assertTrue("[collections]\n  path = /sd/Collections\n  read only = false\n  list = false\n  exclude = ._* .DS_Store /[mM][aA][pP].[tT][xX][tT]\n" in c)
        assertFailsWith<IllegalArgumentException> { SyncCommands.daemonConf("192.168.1.10", "/s", "/v", "/p", "/l", collections = "/c\n[evil]") }
    }

    @Test fun a_bios_run_resolves_the_home_bios_and_never_replaces() {
        val push = RomRun(SyncStep.SEND_BIOS, "PS", RomEnd.Bios("PS"), RomEnd.Peer("bios", "ps"), ignoreExisting = true)
        val a = SyncCommands.romArgs("/b", push, "10.0.0.2", "/sd/Roms", emptyMap(), null, null, homeBios = "/sd/Bios")
        assertEquals(listOf("/sd/Bios/PS/", "rsync://10.0.0.2:18730/bios/ps/"), a.takeLast(2))
        assertTrue("--ignore-existing" in a && "--ignore-missing-args" in a)
        assertTrue("--exclude=._*" in a && "--exclude=.DS_Store" in a)
        val pull = RomRun(SyncStep.RECEIVE_BIOS, "PS", RomEnd.Peer("bios", "ps"), RomEnd.Bios("PS"), ignoreExisting = true)
        val p = SyncCommands.romArgs("/b", pull, "10.0.0.2", "/sd/Roms", emptyMap(), null, null, homeBios = "/sd/Bios/")
        assertEquals(listOf("rsync://10.0.0.2:18730/bios/ps/", "/sd/Bios/PS/"), p.takeLast(2))
        assertTrue("--ignore-existing" in p && "--ignore-missing-args" in p)
        // The daemon can't enforce never-replace: every planned BIOS run, push and pull, carries --ignore-existing.
        val planned = SyncRoms.biosPlan(listOf("PS"), listOf("ps"))
        assertEquals(listOf(SyncStep.SEND_BIOS, SyncStep.RECEIVE_BIOS), planned.map { it.step })
        planned.forEach { run ->
            val args = SyncCommands.romArgs("/b", run, "10.0.0.2", "/sd/Roms", emptyMap(), null, null, homeBios = "/sd/Bios")
            assertEquals(1, args.count { it == "--ignore-existing" }, run.toString())
            // Dreamcast memory cards are saves (Saves/DC): an old copy in a BIOS folder never travels.
            assertEquals(1, args.count { it == "--exclude=vmu_save_*.bin" }, run.toString())
        }
        assertFailsWith<IllegalStateException> { SyncCommands.romArgs("/b", push, "10.0.0.2", "/sd/Roms", emptyMap(), null, null) }
        assertFailsWith<IllegalArgumentException> {
            SyncCommands.romArgs("/b", push.copy(from = RomEnd.Bios("P\nS")), "10.0.0.2", "/sd/Roms", emptyMap(), null, null, homeBios = "/sd/Bios")
        }
    }

    @Test fun daemon_conf_adds_bios_only_with_a_bios_folder() {
        assertTrue("[bios]" !in SyncCommands.daemonConf("192.168.1.10", "/s", "/v", "/p", "/l"))
        val c = SyncCommands.daemonConf("192.168.1.10", "/s", "/v", "/p", "/l", bios = "/sd/Bios")
        assertTrue("[bios]\n  path = /sd/Bios\n  read only = false\n  list = false\n  exclude = ._* .DS_Store vmu_save_*.bin\n" in c)
        assertTrue("[roms]" !in c)
        assertFailsWith<IllegalArgumentException> { SyncCommands.daemonConf("192.168.1.10", "/s", "/v", "/p", "/l", bios = "/sd/Bios\n[evil]") }
    }

    @Test fun client_args_push_and_pull_each_area_with_its_filter() {
        assertEquals(opts, SyncCommands.OPTIONS)
        assertEquals(
            listOf("/b") + opts + SyncAreas.sharedFilterArgs() + listOf("/r/.userdata/shared/", "rsync://10.0.0.2:18730/shared/"),
            SyncCommands.clientArgs("/b", SyncStep.SEND_STATES, "10.0.0.2", "/r"),
        )
        assertEquals(
            listOf("/b") + opts + SyncAreas.savesExcludeArgs() + listOf("/r/Saves/", "rsync://10.0.0.2:18730/saves/"),
            SyncCommands.clientArgs("/b", SyncStep.SEND_SAVES, "10.0.0.2", "/r/"),
        )
        assertEquals(
            listOf("/b") + pullOpts + SyncAreas.sharedFilterArgs() + listOf("rsync://10.0.0.2:18730/shared/", "/r/.userdata/shared/"),
            SyncCommands.clientArgs("/b", SyncStep.RECEIVE_STATES, "10.0.0.2", "/r"),
        )
        assertEquals(
            listOf("/b") + pullOpts + SyncAreas.savesExcludeArgs() + listOf("rsync://10.0.0.2:18730/saves/", "/r/Saves/"),
            SyncCommands.clientArgs("/b", SyncStep.RECEIVE_SAVES, "10.0.0.2", "/r"),
        )
        assertFailsWith<IllegalArgumentException> { SyncCommands.clientArgs("/b", SyncStep.SEND_SAVES, "10.0.0.2;rm", "/r") }
    }

    @Test fun client_args_with_a_staging_dir_and_only_tags() {
        val args = SyncCommands.clientArgs("/b", SyncStep.RECEIVE_SAVES, "10.0.0.2", "/r", savesDir = "/s/recv", onlyTags = listOf("GBA", "MGBA"))
        assertEquals(
            listOf("/b") + pullOpts + SyncAreas.savesExcludeArgs() +
                listOf("--include=/GBA/", "--include=/GBA/**", "--include=/MGBA/", "--include=/MGBA/**", "--exclude=*") +
                listOf("rsync://10.0.0.2:18730/saves/", "/s/recv/"),
            args,
        )
        assertEquals(
            listOf("/b") + opts + SyncAreas.savesExcludeArgs() + listOf("/s/send/", "rsync://10.0.0.2:18730/saves/"),
            SyncCommands.clientArgs("/b", SyncStep.SEND_SAVES, "10.0.0.2", "/r", savesDir = "/s/send/"),
        )
        assertTrue("--include=/a\\*b/" in SyncCommands.clientArgs("/b", SyncStep.RECEIVE_SAVES, "10.0.0.2", "/r", onlyTags = listOf("a*b")))
        assertFailsWith<IllegalArgumentException> { SyncCommands.clientArgs("/b", SyncStep.RECEIVE_SAVES, "10.0.0.2", "/r", onlyTags = listOf("a/b")) }
    }

    @Test fun the_daemon_always_filters() {
        val conf = SyncCommands.daemonConf("10.0.0.2", shared = "/r/.userdata/shared", saves = "/r/Saves", pidFile = "/w/d.pid", logFile = "/w/d.log")
        assertEquals(
            """
            pid file = /w/d.pid
            port = 18730
            use chroot = no
            reverse lookup = no
            hosts allow = 10.0.0.2
            log file = /w/d.log
            log format = %m %o %f
            transfer logging = yes

            [shared]
              path = /r/.userdata/shared
              read only = false
              list = false
              filter = ${SyncAreas.daemonSharedFilter()}
            [saves]
              path = /r/Saves
              read only = false
              list = false
              exclude = ._* .DS_Store

            """.trimIndent(),
            conf,
        )
        assertFalse("[roms]" in conf)
    }

    @Test fun the_daemon_conf_refuses_line_breaks_and_bad_ips() {
        assertFailsWith<IllegalArgumentException> { SyncCommands.daemonConf("10.0.0.2", "/s\n[x]", "/v", "/p", "/l") }
        assertFailsWith<IllegalArgumentException> { SyncCommands.daemonConf("10.0.0.2", "/s", "/v", "/p\r", "/l") }
        assertFailsWith<IllegalArgumentException> { SyncCommands.daemonConf("any", "/s", "/v", "/p", "/l") }
    }

    @Test fun rom_args_keep_names_with_spaces_whole() {
        val run = RomRun(SyncStep.SEND_ROMS, "Pokémon mini (PKM)", RomEnd.Home("Pokémon mini (PKM)"), RomEnd.Peer("roms", "Pokémon mini (PKM)"))
        val a = SyncCommands.romArgs("/bin/rsync", run, "192.168.1.10", "/sd/Roms", emptyMap(), null, "/c/ex.txt")
        assertEquals("/sd/Roms/Pokémon mini (PKM)/", a[a.size - 2])
        assertEquals("rsync://192.168.1.10:18730/roms/Pokémon mini (PKM)/", a.last())
        assertTrue("--exclude-from=/c/ex.txt" in a); assertTrue("--inplace" !in a)
        assertTrue("--exclude=._*" in a && "--exclude=.DS_Store" in a)
    }

    /** A console with two handheld folders: each one's badges pull without replacing, and a folder without any is no failure. */
    @Test fun badge_pull_of_a_shared_console_never_replaces_and_tolerates_a_missing_folder() {
        val gba = "Game Boy Advance (GBA)"
        val pairs = listOf(ConsolePair(gba, gba, "GBA", setOf("GBA")), ConsolePair(gba, "Game Boy Advance (MGBA)", "MGBA", setOf("MGBA")))
        val runs = SyncRoms.plan(Pairing(pairs, emptyList()), ExtrasManifest(), ExtrasManifest(), mapOf(gba to emptySet()), mapped = true)
            .filter { it.to == RomEnd.Home("$gba/.retroachievements") }
        assertEquals(2, runs.size)
        for (run in runs) {
            val a = SyncCommands.romArgs("/b", run, "10.0.0.2", "/sd/Roms", emptyMap(), null, "/c/ex.txt")
            assertEquals(listOf("rsync://10.0.0.2:18730/roms/${(run.from as RomEnd.Peer).path}/", "/sd/Roms/$gba/.retroachievements/"), a.takeLast(2))
            assertTrue("--ignore-existing" in a && "--ignore-missing-args" in a && "--update" in a)
            val gone = listOf("rsync: [sender] change_dir \"/${(run.from as RomEnd.Peer).path}\" (in roms) failed: No such file or directory (2)",
                "rsync error: some files/attrs were not transferred (see previous errors) (code 23) at main.c(1338) [sender=3.4.1]")
            assertEquals(0, SyncCommands.romResult(run, 23, gone))
        }
    }

    @Test fun rom_pull_from_an_extra_module_with_files_from() {
        val run = RomRun(SyncStep.RECEIVE_ROMS, "Game Boy (GB)", RomEnd.Peer("extra-1", ""), RomEnd.Home("Game Boy (GB)"), filesFrom = listOf("a.gb"))
        val a = SyncCommands.romArgs("/bin/rsync", run, "192.168.1.10", "/sd/Roms", emptyMap(), "/c/list.txt", null)
        assertEquals(listOf("rsync://192.168.1.10:18730/extra-1/", "/sd/Roms/Game Boy (GB)/"), a.takeLast(2))
        assertTrue("--files-from=/c/list.txt" in a); assertTrue("--inplace" !in a)
    }

    @Test fun no_push_uses_inplace() {
        SyncStep.entries.filter { it.push && it.module in setOf("shared", "saves") }.forEach {
            assertTrue("--inplace" !in SyncCommands.clientArgs("/b", it, "10.0.0.2", "/r"))
        }
        val run = RomRun(SyncStep.SEND_ROMS, "GB", RomEnd.Home("GB"), RomEnd.Peer("roms", "GB"))
        assertTrue("--inplace" !in SyncCommands.romArgs("/b", run, "10.0.0.2", "/sd/Roms", emptyMap(), null, null))
    }

    @Test fun limited_rom_runs_ignore_missing_args() {
        val run = RomRun(SyncStep.SEND_ROMS, "GB", RomEnd.Home("GB"), RomEnd.Peer("roms", "GB"))
        assertTrue("--ignore-missing-args" !in SyncCommands.romArgs("/b", run, "10.0.0.2", "/sd/Roms", emptyMap(), null, "/c/ex"))
        assertTrue("--ignore-missing-args" in SyncCommands.romArgs("/b", run, "10.0.0.2", "/sd/Roms", emptyMap(), "/c/l", null))
        assertTrue("--ignore-missing-args" in SyncCommands.romArgs("/b", run, "10.0.0.2", "/sd/Roms", emptyMap(), null, null, "/c/f"))
    }

    @Test fun a_pull_from_a_gone_folder_counts_as_done_but_other_errors_do_not() {
        val pull = RomRun(SyncStep.RECEIVE_ROMS, "GB", RomEnd.Peer("extra-0", "X"), RomEnd.Home("GB"), only = listOf("a.gb"))
        val gone = "rsync: [sender] change_dir \"X\" (in extra-0) failed: No such file or directory (2)"
        val summary = "rsync error: some files/attrs were not transferred (see previous errors) (code 23) at main.c(1956) [Receiver=3.5.0]"
        assertEquals(0, SyncCommands.romResult(pull, 23, listOf(gone, "", summary)))
        assertEquals(23, SyncCommands.romResult(pull, 23, listOf(gone, "rsync: [sender] send_files failed to open \"/x\": Permission denied (13)", summary)))
        assertEquals(23, SyncCommands.romResult(pull, 23, listOf(summary)))
        assertEquals(23, SyncCommands.romResult(pull.copy(only = null), 23, listOf(gone, summary)))
        assertEquals(12, SyncCommands.romResult(pull, 12, listOf(gone)))
    }

    @Test fun files_from_lines_start_with_dot_slash() {
        assertEquals(listOf("./#1 Game.gb", "./;x.gb", "./a.gb"), SyncCommands.filesFromLines(listOf("#1 Game.gb", ";x.gb", "a.gb")))
    }

    @Test fun rom_push_from_an_extra_root() {
        val run = RomRun(SyncStep.SEND_ROMS, "GB", RomEnd.Extra(0, "GB"), RomEnd.Peer("roms", "GB"), filesFrom = listOf("a.gb"))
        val a = SyncCommands.romArgs("/bin/rsync", run, "192.168.1.10", "/sd/Roms", mapOf(0 to "/storage/SD/Games"), "/c/l", null)
        assertEquals("/storage/SD/Games/GB/", a[a.size - 2])
    }

    @Test fun daemon_conf_adds_rom_modules_only_with_roms() {
        val base = SyncCommands.daemonConf("192.168.1.10", "/s", "/v", "/p", "/l")
        assertTrue("[roms]" !in base)
        val c = SyncCommands.daemonConf("192.168.1.10", "/s", "/v", "/p", "/l", ServerRoms("/sd/Roms", listOf("/storage/SD/Games"), "/c/nx"))
        assertTrue("[roms]\n  path = /sd/Roms\n  read only = false\n  list = false\n  exclude = ._* .DS_Store /.sources/\n" in c)
        assertTrue("[extra-0]\n  path = /storage/SD/Games\n  read only = true\n" in c)
        assertTrue("[nx-extras]\n  path = /c/nx\n  read only = true\n" in c)
        assertTrue("[nx-art]\n  path = /sd/Roms/.sources\n  read only = true\n  list = false\n  exclude = ._* .DS_Store\n" in c)
    }

    @Test fun only_rules_keep_named_entries_and_parents() {
        assertEquals(listOf("+ /Disc", "+ /Disc/**", "+ /l\\[1].gb", "+ /l\\[1].gb/**", "- *"), SyncCommands.onlyRules(listOf("Disc", "l[1].gb")))
        assertEquals(listOf("+ /boxart/", "+ /boxart/c.png", "+ /boxart/c.png/**", "- *"), SyncCommands.onlyRules(listOf("boxart/c.png")))
        val run = RomRun(SyncStep.RECEIVE_ROMS, "GB", RomEnd.Peer("extra-0", "GB"), RomEnd.Home("GB"), only = listOf("a"))
        val a = SyncCommands.romArgs("/b", run, "10.0.0.2", "/sd/Roms", emptyMap(), null, null, "/c/f.txt")
        assertTrue("--filter=merge /c/f.txt" in a)
    }

    @Test fun daemon_conf_rejects_a_line_break_in_a_rom_path() {
        assertFailsWith<IllegalArgumentException> {
            SyncCommands.daemonConf("192.168.1.10", "/s", "/v", "/p", "/l", ServerRoms("/sd/Roms", listOf("/x\n[evil]"), "/c"))
        }
    }

    @Test fun list_fetch_put_args() {
        assertEquals(listOf("/b", "--list-only", "--8-bit-output", "--timeout=30", "--contimeout=10", "rsync://10.99.0.2:18730/roms/"), SyncCommands.listArgs("/b", "10.99.0.2"))
        assertEquals(listOf("/b", "--list-only", "--8-bit-output", "--timeout=30", "--contimeout=10", "rsync://10.99.0.2:18730/bios/"),
            SyncCommands.listArgs("/b", "10.99.0.2", "bios"))
        assertEquals(listOf("/b", "--list-only", "--8-bit-output", "-R", "--timeout=30", "--contimeout=10", "rsync://10.99.0.2:18730/roms/./*/.retroachievements/hashes.json"),
            SyncCommands.listHashesArgs("/b", "10.99.0.2"))
        assertEquals("rsync://10.99.0.2:18730/nx-extras/extras.json", SyncCommands.fetchArgs("/b", "10.99.0.2", "nx-extras", "extras.json", "/c/e.json")[4])
        assertEquals("rsync://10.99.0.2:18730/roms/GB/.retroachievements/", SyncCommands.putArgs("/b", "10.99.0.2", "roms", "GB/.retroachievements", "/c/h.json").last())
    }
    @Test fun art_runs_ignore_existing_and_missing_args() {
        val art = RomRun(SyncStep.SEND_ROMS, "GB", RomEnd.Home("GB/.media"), RomEnd.Peer("roms", "GB/.media"), ignoreExisting = true)
        val a = SyncCommands.romArgs("/b", art, "10.0.0.2", "/sd/Roms", emptyMap(), null, "/c/ex")
        assertTrue("--ignore-existing" in a)
        assertEquals(1, a.count { it == "--ignore-missing-args" })
        assertEquals(1, SyncCommands.romArgs("/b", art, "10.0.0.2", "/sd/Roms", emptyMap(), "/c/l", null).count { it == "--ignore-missing-args" })
        val home = art.copy(ignoreExisting = false)
        assertTrue("--ignore-existing" !in SyncCommands.romArgs("/b", home, "10.0.0.2", "/sd/Roms", emptyMap(), null, "/c/ex"))
    }

    @Test fun an_art_run_from_a_missing_media_folder_counts_as_done() {
        val art = RomRun(SyncStep.RECEIVE_ROMS, "GB", RomEnd.Peer("roms", "GB/.media"), RomEnd.Home("GB/.media"), ignoreExisting = true)
        val gone = "rsync: [sender] change_dir \"GB/.media\" (in roms) failed: No such file or directory (2)"
        val summary = "rsync error: some files/attrs were not transferred (see previous errors) (code 23) at main.c(1956) [Receiver=3.5.0]"
        assertEquals(0, SyncCommands.romResult(art, 23, listOf(gone, summary)))
        assertEquals(23, SyncCommands.romResult(art, 23, listOf(gone, "rsync: [receiver] mkstemp \"x.png\" failed: Permission denied (13)", summary)))
        assertEquals(23, SyncCommands.romResult(art, 23, listOf(summary)))
        assertEquals(11, SyncCommands.romResult(art, 11, listOf(gone)))
        assertEquals(23, SyncCommands.romResult(art.copy(ignoreExisting = false), 23, listOf(gone, summary)))
    }

    @Test fun list_folder_args() {
        assertEquals(listOf("/b", "--list-only", "--8-bit-output", "--timeout=30", "--contimeout=10", "rsync://10.99.0.2:18730/roms/Game Boy Advance (MGBA)/"),
            SyncCommands.listFolderArgs("/b", "10.99.0.2", "Game Boy Advance (MGBA)"))
        assertFailsWith<IllegalArgumentException> { SyncCommands.listFolderArgs("/b", "10.99.0.2", "a\nb") }
    }
}
