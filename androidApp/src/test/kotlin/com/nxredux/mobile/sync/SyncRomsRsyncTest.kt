package com.nxredux.mobile.sync

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * ROM sync's rsync calls ([SyncRoms.plan], [SyncCommands.romArgs]) against a real rsync ≥ 3.1 daemon started with
 * [SyncCommands.daemonConf] and its ROM modules (skipped when there is no such rsync, e.g. on CI).
 */
class SyncRomsRsyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private var daemon: Process? = null
    private val gb = ConsolePair("Game Boy (GB)", "GB Games (GB)", "GB")
    private val t = 1_700_000_000_000L

    @After fun stopDaemon() {
        daemon?.let { it.destroy(); if (!it.waitFor(5, TimeUnit.SECONDS)) it.destroyForcibly() }
    }

    private fun rsync(): String? = listOf("/opt/homebrew/bin/rsync", "/usr/local/bin/rsync", "/usr/bin/rsync").firstOrNull { path ->
        File(path).canExecute() && runCatching {
            val out = ProcessBuilder(path, "--version").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
            Regex("""version (\d+)\.(\d+)""").find(out)?.destructured?.let { (a, b) -> a.toInt() > 3 || (a.toInt() == 3 && b.toInt() >= 1) } == true
        }.getOrDefault(false)
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private class Served(val bin: String, val port: Int, val roms: File, val extras: List<File>, val bios: File)

    /** Starts the daemon serving a fresh `roms/`, [extras] extra folders and `bios/`; null (test skipped) without a usable rsync. */
    private fun serve(extras: Int = 0): Served {
        val bin = rsync()
        assumeTrue("no rsync >= 3.1 on this machine", bin != null)
        val work = tmp.newFolder("work")
        val roms = tmp.newFolder("served-roms")
        val bios = tmp.newFolder("served-bios")
        val extraDirs = (0 until extras).map { tmp.newFolder("extra-$it") }
        val manifest = tmp.newFolder("nx")
        val port = freePort()
        val conf = SyncCommands.daemonConf("127.0.0.1", shared = tmp.newFolder("sh").path, saves = tmp.newFolder("sv").path,
            pidFile = File(work, "rsyncd.pid").path, logFile = File(work, "sync.log").path,
            roms = ServerRoms(roms.path, extraDirs.map { it.path }, manifest.path), bios = bios.path)
            .replace("port = ${SyncWire.RSYNC_PORT}\n", "port = $port\n")
        val confFile = File(work, "rsyncd.conf").apply { writeText(conf) }
        daemon = ProcessBuilder(bin!!, "--daemon", "--no-detach", "--config=${confFile.path}")
            .redirectErrorStream(true).redirectOutput(File(work, "daemon.out")).start()
        val deadline = System.currentTimeMillis() + 5_000
        while (runCatching { Socket("127.0.0.1", port).close() }.isFailure) {
            check(System.currentTimeMillis() < deadline) { "daemon did not listen: ${File(work, "daemon.out").readText()}" }
            Thread.sleep(50)
        }
        return Served(bin, port, roms, extraDirs, bios)
    }

    private fun swapPort(args: List<String>, port: Int) = args.map { it.replace("127.0.0.1:${SyncWire.RSYNC_PORT}/", "127.0.0.1:$port/") }

    private fun exec(args: List<String>): Pair<Int, String> {
        val p = ProcessBuilder(args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        return p.waitFor() to out
    }

    /** Runs [run] as the phone would: its files-from and exclude lists written to files, the port swapped. */
    private fun runRom(s: Served, run: RomRun, home: File, extraRoots: Map<Int, String> = emptyMap(), homeBios: File? = null): Int {
        runRomOutput(s, run, home, extraRoots, homeBios)
        return 0
    }

    /** [runRom], returning rsync's output (asserting the run ended as done). */
    private fun runRomOutput(s: Served, run: RomRun, home: File, extraRoots: Map<Int, String> = emptyMap(), homeBios: File? = null): String {
        val ff = run.filesFrom?.let { File(tmp.newFolder(), "list.txt").apply { writeText(SyncCommands.filesFromLines(it).joinToString("\n") + "\n") } }
        val ex = run.excludes.takeIf { it.isNotEmpty() }?.let { File(tmp.newFolder(), "ex.txt").apply { writeText(it.joinToString("\n") + "\n") } }
        val fl = run.only?.let { File(tmp.newFolder(), "only.txt").apply { writeText(SyncCommands.onlyRules(it).joinToString("\n") + "\n") } }
        val args = swapPort(SyncCommands.romArgs(s.bin, run, "127.0.0.1", home.path, extraRoots, ff?.path, ex?.path, fl?.path, homeBios?.path), s.port)
        val (raw, out) = exec(args)
        assertEquals(out, 0, SyncCommands.romResult(run, raw, out.lines()))
        return out
    }

    @Test fun a_pair_pushes_and_pulls_between_two_trees() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        File(home, gb.local).mkdirs()
        File(home, "${gb.local}/a.gb").apply { writeText("aaa"); setLastModified(t) }
        File(s.roms, gb.remote).mkdirs()
        File(s.roms, "${gb.remote}/b.gb").apply { writeText("bbb"); setLastModified(t) }
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), ExtrasManifest(), ExtrasManifest(), mapOf(gb.local to setOf("a.gb")))
        runs.forEach { runRom(s, it, home) }
        val served = File(s.roms, "${gb.remote}/a.gb")
        val got = File(home, "${gb.local}/b.gb")
        assertEquals("aaa", served.readText())
        assertEquals("bbb", got.readText())
        assertTrue(Math.abs(served.lastModified() - t) <= 1000)
        assertTrue(Math.abs(got.lastModified() - t) <= 1000)
    }

    @Test fun files_from_pushes_an_extra_unit_into_the_console_folder() {
        val s = serve(extras = 1)
        val home = tmp.newFolder("Roms")
        val root = tmp.newFolder("SD")
        File(root, "Games/GB").mkdirs()
        File(root, "Games/GB/x.gb").writeText("xxx")
        File(root, "Games/GB/y.gb").writeText("yyy")
        File(root, "Games/other.gb").writeText("zzz")
        val run = RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Extra(0, "GB"), RomEnd.Peer("roms", gb.remote), filesFrom = listOf("x.gb"))
        runRom(s, run, home, mapOf(0 to File(root, "Games").path))
        assertEquals(listOf("x.gb"), File(s.roms, gb.remote).list()!!.toList())
        assertEquals(listOf(gb.remote), s.roms.list()!!.toList())
    }

    @Test fun files_from_with_a_missing_file_still_sends_the_existing_one() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        File(home, gb.local).mkdirs()
        File(home, "${gb.local}/here.gb").writeText("h")
        val run = RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Home(gb.local), RomEnd.Peer("roms", gb.remote), filesFrom = listOf("here.gb", "gone.gb"))
        runRom(s, run, home)
        assertEquals(listOf("here.gb"), File(s.roms, gb.remote).list()!!.toList())
    }

    @Test fun a_names_starting_with_a_hash_is_pushed_through_files_from() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        File(home, gb.local).mkdirs()
        File(home, "${gb.local}/#1 Game.gb").writeText("h")
        File(home, "${gb.local}/;semi.gb").writeText("s")
        val run = RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Home(gb.local), RomEnd.Peer("roms", gb.remote), filesFrom = listOf("#1 Game.gb", ";semi.gb"))
        runRom(s, run, home)
        assertEquals(listOf("#1 Game.gb", ";semi.gb"), File(s.roms, gb.remote).list()!!.sorted())
    }

    @Test fun a_peer_extra_pull_from_a_missing_parent_succeeds() {
        val s = serve(extras = 1)
        val home = tmp.newFolder("Roms")
        File(home, "dest").mkdirs()
        val run = RomRun(SyncStep.RECEIVE_ROMS, "dest", RomEnd.Peer("extra-0", "NoSuchParent"), RomEnd.Home("dest"), only = listOf("l.gb"))
        runRom(s, run, home)
        assertTrue(File(home, "dest").list()!!.isEmpty())
    }

    @Test fun a_peer_extra_pull_from_a_subfolder_keeps_only_the_named_entries() {
        val s = serve(extras = 1)
        val home = tmp.newFolder("Roms")
        File(home, "dest").mkdirs()
        File(s.extras[0], "GB/Disc").mkdirs()
        File(s.extras[0], "GB/Disc/Disc.cue").writeText("cue")
        File(s.extras[0], "GB/Disc/Disc.bin").writeText("bin")
        File(s.extras[0], "GB/l.gb").writeText("l")
        File(s.extras[0], "GB/other.gb").writeText("o")
        val run = RomRun(SyncStep.RECEIVE_ROMS, "dest", RomEnd.Peer("extra-0", "GB"), RomEnd.Home("dest"), only = listOf("Disc", "l.gb"))
        runRom(s, run, home)
        assertEquals(listOf("Disc", "l.gb"), File(home, "dest").list()!!.sorted())
        assertEquals(listOf("Disc.bin", "Disc.cue"), File(home, "dest/Disc").list()!!.sorted())
    }

    @Test fun a_peer_art_pull_goes_through_the_art_module() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        File(home, "${gb.local}/.media").mkdirs()
        File(s.roms, ".sources/y/GB/.media/boxart").mkdirs()
        File(s.roms, ".sources/y/GB/.media/boxart/c.png").writeText("png")
        File(s.roms, ".sources/y/GB/.media/boxart/other.png").writeText("png2")
        val run = RomRun(SyncStep.RECEIVE_ROMS, gb.local, RomEnd.Peer("nx-art", "y/GB/.media"), RomEnd.Home("${gb.local}/.media"),
            only = listOf("boxart/c.png"))
        runRom(s, run, home)
        assertEquals(listOf("c.png"), File(home, "${gb.local}/.media/boxart").list()!!.toList())
    }

    @Test fun a_read_only_extra_module_refuses_a_push() {
        val s = serve(extras = 1)
        val home = tmp.newFolder("Roms")
        File(home, "GB").mkdirs()
        File(home, "GB/a.gb").writeText("aaa")
        File(s.extras[0], "keep.gb").writeText("k")
        val run = RomRun(SyncStep.SEND_ROMS, "GB", RomEnd.Home("GB"), RomEnd.Peer("extra-0", ""))
        val (code, _) = exec(swapPort(SyncCommands.romArgs(s.bin, run, "127.0.0.1", home.path, emptyMap(), null, null), s.port))
        assertNotEquals(0, code)
        assertEquals(listOf("keep.gb"), s.extras[0].list()!!.toList())
    }

    @Test fun list_only_lists_the_served_console_folders() {
        val s = serve()
        File(s.roms, gb.remote).mkdirs()
        File(s.roms, "top.txt").writeText("ignored")
        val (code, out) = exec(swapPort(SyncCommands.listArgs(s.bin, "127.0.0.1"), s.port))
        assertEquals(out, 0, code)
        assertEquals(listOf(gb.remote), SyncConsoles.parseListing(out.lines().filter { it.isNotBlank() }))
    }

    /** One call lists every folder's hashes.json (spaces, non-ASCII); folders without one only make it exit 23 on a missing file. */
    @Test fun one_list_finds_every_folder_with_a_hashes_file() {
        val s = serve()
        for (n in listOf("Pokémon mini (PKM)", "Game Boy (GB)")) File(s.roms, "$n/.retroachievements").mkdirs().also { File(s.roms, "$n/.retroachievements/hashes.json").writeText("{}") }
        File(s.roms, "GB Games (GB)/.retroachievements").mkdirs()
        File(s.roms, "GB Games (GB)/.retroachievements/other.json").writeText("{}")
        File(s.roms, "Plain (PL)").mkdirs()
        val (code, out) = exec(swapPort(SyncCommands.listHashesArgs(s.bin, "127.0.0.1"), s.port))
        assertEquals(out, 23, code)
        assertTrue(out, out.lines().filter { it.startsWith("rsync: ") }.all { "No such file or directory" in it })
        assertEquals(setOf("Pokémon mini (PKM)", "Game Boy (GB)"), SyncConsoles.parseHashFolders(out.lines()))
    }

    @Test fun the_hashes_list_with_no_file_anywhere_is_exit_23_and_empty() {
        val s = serve()
        File(s.roms, "Plain (PL)").mkdirs()
        val (code, out) = exec(swapPort(SyncCommands.listHashesArgs(s.bin, "127.0.0.1"), s.port))
        assertEquals(out, 23, code)
        assertTrue(out, out.lines().filter { it.startsWith("rsync: ") }.all { "No such file or directory" in it })
        assertEquals(emptySet<String>(), SyncConsoles.parseHashFolders(out.lines()))
    }

    /** A non-ASCII folder name comes back raw, so it pairs by name with the phone's (the Brick's `Pokémon mini (PKM)`). */
    @Test fun list_only_keeps_non_ascii_names() {
        val s = serve()
        File(s.roms, "Pokémon mini (PKM)").mkdirs()
        val (code, out) = exec(swapPort(SyncCommands.listArgs(s.bin, "127.0.0.1"), s.port))
        assertEquals(out, 0, code)
        assertEquals(listOf("Pokémon mini (PKM)"), SyncConsoles.parseListing(out.lines().filter { it.isNotBlank() }))
    }

    @Test fun bracket_names_are_excluded_literally() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        File(home, gb.local).mkdirs()
        File(s.roms, gb.remote).mkdirs()
        File(s.roms, "${gb.remote}/Tetris [!].gb").writeText("bracket")
        File(s.roms, "${gb.remote}/Tetris !.gb").writeText("plain")
        val run = RomRun(SyncStep.RECEIVE_ROMS, gb.local, RomEnd.Peer("roms", gb.remote), RomEnd.Home(gb.local),
            excludes = listOf("/" + SyncRoms.escape("Tetris [!].gb")))
        runRom(s, run, home)
        assertEquals(listOf("Tetris !.gb"), File(home, gb.local).list()!!.toList())
        assertFalse(File(home, "${gb.local}/Tetris [!].gb").exists())
    }

    /** Art fills gaps only: the handheld's own image stays (even when the phone's is newer), a missing one arrives, grid/ stays home. */
    @Test fun the_art_push_never_replaces_an_image_on_the_peer() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        File(home, "${gb.local}/.media/boxart").mkdirs()
        File(home, "${gb.local}/.media/grid").mkdirs()
        File(home, "${gb.local}/.media/boxart/a.png").apply { writeText("phone"); setLastModified(t + 60_000) }
        File(home, "${gb.local}/.media/boxart/b.png").writeText("new")
        File(home, "${gb.local}/.media/grid/a.png").writeText("grid")
        File(s.roms, "${gb.remote}/.media/boxart").mkdirs()
        File(s.roms, "${gb.remote}/.media/boxart/a.png").apply { writeText("handheld"); setLastModified(t) }
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), ExtrasManifest(), ExtrasManifest(), mapOf(gb.local to emptySet()))
        runs.filter { it.step == SyncStep.SEND_ROMS }.forEach { runRom(s, it, home) }
        assertEquals("handheld", File(s.roms, "${gb.remote}/.media/boxart/a.png").readText())
        assertEquals("new", File(s.roms, "${gb.remote}/.media/boxart/b.png").readText())
        assertFalse(File(s.roms, "${gb.remote}/.media/grid").exists())
    }

    /** The pull mirror: the phone's own image stays (even when the handheld's is newer), a missing one arrives. */
    @Test fun the_art_pull_never_replaces_an_image_on_this_phone() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        File(home, "${gb.local}/.media/boxart").mkdirs()
        File(home, "${gb.local}/.media/boxart/a.png").apply { writeText("phone"); setLastModified(t) }
        File(s.roms, "${gb.remote}/.media/boxart").mkdirs()
        File(s.roms, "${gb.remote}/.media/boxart/a.png").apply { writeText("handheld"); setLastModified(t + 60_000) }
        File(s.roms, "${gb.remote}/.media/boxart/b.png").writeText("new")
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), ExtrasManifest(), ExtrasManifest(), mapOf(gb.local to emptySet()))
        runs.filter { it.step == SyncStep.RECEIVE_ROMS }.forEach { runRom(s, it, home) }
        assertEquals("phone", File(home, "${gb.local}/.media/boxart/a.png").readText())
        assertEquals("new", File(home, "${gb.local}/.media/boxart/b.png").readText())
    }

    /** BIOS never replaces: the peer's own file stays (even when the phone's is newer), a missing one arrives, both ways. */
    @Test fun bios_runs_never_replace_a_file_and_copy_a_missing_one() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        val bios = tmp.newFolder("Bios")
        File(bios, "PS").mkdirs()
        File(bios, "PS/scph5501.bin").apply { writeText("phone"); setLastModified(t + 60_000) }
        File(bios, "PS/scph5502.bin").writeText("eu")
        File(bios, "PS/._scph5502.bin").writeText("junk")
        File(s.bios, "ps").mkdirs()
        File(s.bios, "ps/scph5501.bin").apply { writeText("handheld"); setLastModified(t) }
        File(s.bios, "ps/scph5500.bin").apply { writeText("jp"); setLastModified(t + 120_000) }
        File(bios, "PS/scph5500.bin").apply { writeText("phone-jp"); setLastModified(t) }
        File(s.bios, "ps/psxonpsp660.bin").writeText("psp")
        val listing = exec(swapPort(SyncCommands.listArgs(s.bin, "127.0.0.1", "bios"), s.port))
        assertEquals(listing.second, 0, listing.first)
        val remote = SyncConsoles.parseListing(listing.second.lines().filter { it.isNotBlank() })
        assertEquals(listOf("ps"), remote)
        val runs = SyncRoms.biosPlan(listOf("PS"), remote)
        assertEquals(2, runs.size)
        runs.forEach { runRom(s, it, home, homeBios = bios) }
        assertEquals("handheld", File(s.bios, "ps/scph5501.bin").readText())
        assertEquals("eu", File(s.bios, "ps/scph5502.bin").readText())
        assertFalse(File(s.bios, "ps/._scph5502.bin").exists())
        assertEquals("phone-jp", File(bios, "PS/scph5500.bin").readText())
        assertEquals("psp", File(bios, "PS/psxonpsp660.bin").readText())
        assertEquals(listOf("._scph5502.bin", "psxonpsp660.bin", "scph5500.bin", "scph5501.bin", "scph5502.bin"), File(bios, "PS").list()!!.sorted())
    }

    /** Dreamcast memory cards are saves: a BIOS push and pull skip them at any depth but copy the rest of DC. */
    @Test fun bios_runs_skip_vmu_memory_cards() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        val bios = tmp.newFolder("Bios")
        File(bios, "DC/dc").mkdirs()
        File(bios, "DC/vmu_save_A1.bin").writeText("a1")
        File(bios, "DC/dc/vmu_save_B1.bin").writeText("b1")
        File(bios, "DC/dc_boot.bin").writeText("boot")
        File(s.bios, "dc/dc").mkdirs()
        File(s.bios, "dc/vmu_save_C1.bin").writeText("c1")
        File(s.bios, "dc/dc/vmu_save_D1.bin").writeText("d1")
        File(s.bios, "dc/dc_flash.bin").writeText("flash")
        val runs = SyncRoms.biosPlan(listOf("DC"), listOf("dc"))
        assertEquals(2, runs.size)
        runs.forEach { runRom(s, it, home, homeBios = bios) }
        assertEquals("boot", File(s.bios, "dc/dc_boot.bin").readText())
        assertFalse(File(s.bios, "dc/vmu_save_A1.bin").exists())
        assertFalse(File(s.bios, "dc/dc/vmu_save_B1.bin").exists())
        assertEquals("flash", File(bios, "DC/dc_flash.bin").readText())
        assertFalse(File(bios, "DC/vmu_save_C1.bin").exists())
        assertFalse(File(bios, "DC/dc/vmu_save_D1.bin").exists())
    }

    /**
     * Games and badges in both handheld GBA folders must not come again on every ROMs sync. One sync as the controller
     * plans it (listings, mapped pairing, the pulled game's emulator tag) brings each once, keeps the
     * handheld's copies as they were; the next only fills the badge gap of the folder that lacked one, and the one
     * after moves nothing at all.
     */
    @Test fun a_console_in_two_handheld_folders_syncs_once_then_moves_nothing() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        val gba = "Game Boy Advance (GBA)"; val mgba = "Game Boy Advance (MGBA)"
        File(home, gba).mkdirs()
        fun put(path: String, text: String, time: Long) = File(s.roms, path).apply { parentFile.mkdirs(); writeText(text); setLastModified(time) }
        // Same time, other size: rsync's --update would take each in turn. Other times: the newer would win.
        put("$gba/Ruby.gba", "ruby-gba", t); put("$mgba/Ruby.gba", "ruby-mgba!!", t)
        put("$gba/Sapphire.gba", "sapphire-old", t); put("$mgba/sapphire.gba", "sapphire-newer", t + 86_400_000)
        put("$mgba/Emerald.gba", "em", t)
        put("$gba/.retroachievements/b1.png", "aa", t); put("$mgba/.retroachievements/b1.png", "bbbb", t)
        put("$mgba/.retroachievements/b2.png", "c", t)
        fun listing(args: List<String>) = exec(swapPort(args, s.port)).also { assertEquals(it.second, 0, it.first) }.second.lines()
        val remoteAll = SyncConsoles.parseListing(listing(SyncCommands.listArgs(s.bin, "127.0.0.1")))
        val entries = remoteAll.associateWith { SyncConsoles.parseEntries(listing(SyncCommands.listFolderArgs(s.bin, "127.0.0.1", it))).toSet() }
        val pairing = SyncConsoles.pairMapped(listOf(gba), remoteAll)
        assertEquals(listOf(gba, mgba), pairing.pairs.map { it.remote })
        /** One sync's runs that moved a file. */
        fun sync(tags: Map<String, String>): List<RomRun> {
            val names = File(home, gba).list()!!.toSet()
            val runs = SyncRoms.plan(pairing, ExtrasManifest(), ExtrasManifest(), mapOf(gba to names),
                entries.mapValues { (_, v) -> v.mapTo(HashSet()) { it.lowercase() } }, peerFolders = remoteAll,
                gameTags = mapOf(gba to names.associateWith { tags[it] ?: "GBA" }), mapped = true, remoteEntries = entries)
            return runs.filter { it.filesFrom?.isEmpty() != true && Regex("xfr#[1-9]").containsMatchIn(runRomOutput(s, it, home)) }
        }
        sync(emptyMap())
        assertEquals("ruby-gba", File(home, "$gba/Ruby.gba").readText())
        assertEquals("sapphire-old", File(home, "$gba/Sapphire.gba").readText())
        assertFalse(File(home, "$gba/sapphire.gba").let { it.exists() && it.readText() == "sapphire-newer" })
        assertEquals("em", File(home, "$gba/Emerald.gba").readText())
        assertEquals("aa", File(home, "$gba/.retroachievements/b1.png").readText())
        assertEquals("c", File(home, "$gba/.retroachievements/b2.png").readText())
        // The handheld's copies stay as they were: no folder's copy replaced another's.
        assertEquals("ruby-mgba!!", File(s.roms, "$mgba/Ruby.gba").readText())
        assertEquals("bbbb", File(s.roms, "$mgba/.retroachievements/b1.png").readText())
        // Emerald came from (MGBA), so it runs under mGBA (DeviceSyncController.tagPulled); b2 fills (GBA)'s gap.
        val second = sync(mapOf("Emerald.gba" to "MGBA"))
        assertEquals(listOf(RomEnd.Peer("roms", "$gba/.retroachievements")), second.map { it.to })
        assertEquals("c", File(s.roms, "$gba/.retroachievements/b2.png").readText())
        assertEquals(emptyList<RomRun>(), sync(mapOf("Emerald.gba" to "MGBA")))
    }

    /** Neither side has a `.media` folder: the art push and pull both end as done (runRom asserts 0 after romResult). */
    @Test fun art_runs_without_a_media_folder_succeed() {
        val s = serve()
        val home = tmp.newFolder("Roms")
        File(home, gb.local).mkdirs()
        File(s.roms, gb.remote).mkdirs()
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), ExtrasManifest(), ExtrasManifest(), mapOf(gb.local to emptySet()))
        val art = runs.filter { it.ignoreExisting }
        assertEquals(2, art.size)
        art.forEach { runRom(s, it, home) }
        assertFalse(File(home, "${gb.local}/.media").exists() && File(home, "${gb.local}/.media").list()!!.isNotEmpty())
    }
}
