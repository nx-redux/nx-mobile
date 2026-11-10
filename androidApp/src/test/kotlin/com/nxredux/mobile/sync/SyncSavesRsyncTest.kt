package com.nxredux.mobile.sync

import com.nxredux.mobile.storage.SaveFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Battery saves against a handheld through staging ([SyncSaveFiles], [SaveSyncPlan], [SyncCommands.clientArgs] with a
 * staging dir and tags) against a real rsync ≥ 3.1 daemon serving a handheld `Saves/` (skipped without one, e.g. on CI).
 */
class SyncSavesRsyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private var daemon: Process? = null
    private val t1 = 1_700_000_000_000L
    private val t2 = t1 + 3_600_000L
    private val t3 = t2 + 3_600_000L
    private val gba = "Game Boy Advance (GBA)"
    private val mgba = "Game Boy Advance (MGBA)"

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

    private class Served(val bin: String, val port: Int, val saves: File)

    /** Starts the daemon serving a fresh handheld `Saves/`; the test is skipped without a usable rsync. */
    private fun serve(): Served {
        val bin = rsync()
        assumeTrue("no rsync >= 3.1 on this machine", bin != null)
        val work = tmp.newFolder("work")
        val saves = tmp.newFolder("handheld-saves")
        val port = freePort()
        val conf = SyncCommands.daemonConf("127.0.0.1", shared = tmp.newFolder("sh").path, saves = saves.path,
            pidFile = File(work, "rsyncd.pid").path, logFile = File(work, "sync.log").path)
            .replace("port = ${SyncWire.RSYNC_PORT}\n", "port = $port\n")
        val confFile = File(work, "rsyncd.conf").apply { writeText(conf) }
        daemon = ProcessBuilder(bin!!, "--daemon", "--no-detach", "--config=${confFile.path}")
            .redirectErrorStream(true).redirectOutput(File(work, "daemon.out")).start()
        val deadline = System.currentTimeMillis() + 5_000
        while (runCatching { Socket("127.0.0.1", port).close() }.isFailure) {
            check(System.currentTimeMillis() < deadline) { "daemon did not listen: ${File(work, "daemon.out").readText()}" }
            Thread.sleep(50)
        }
        return Served(bin, port, saves)
    }

    private fun exec(args: List<String>, port: Int): String {
        val p = ProcessBuilder(args.map { it.replace("127.0.0.1:${SyncWire.RSYNC_PORT}/", "127.0.0.1:$port/") }).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertEquals(out, 0, p.waitFor())
        return out
    }

    private fun write(root: File, path: String, text: String, time: Long) =
        File(root, path).apply { parentFile.mkdirs(); writeText(text); setLastModified(time) }

    /** One sync's saves part as the controller and AndroidDeviceSync run it: the receive rsync's output and the fold. */
    private class Run(val received: String, val fold: FoldPlan)

    private fun sync(s: Served, files: SyncSaveFiles, phoneSaves: File, send: File, recv: File, handheld: Map<String, Set<String>>): Run {
        val plan = SaveSyncPlan(handheld.keys.toList(), handheld, { c, _ -> c }, { _, _ -> null }, SaveFormat.RETROARCH)
        val mine = files.listSaves()
        assertTrue(files.stage(plan.send(mine), plan.seeds(mine)))
        try {
            exec(SyncCommands.clientArgs(s.bin, SyncStep.SEND_SAVES, "127.0.0.1", phoneSaves.parent, send.path), s.port)
            val out = exec(SyncCommands.clientArgs(s.bin, SyncStep.RECEIVE_SAVES, "127.0.0.1", phoneSaves.parent, recv.path, plan.receiveTags), s.port)
            val fold = plan.fold(files.listReceived(), files.listSaves())
            assertTrue(files.fold(fold, compressed = false))
            return Run(out, fold)
        } finally {
            files.clear()
        }
    }

    @Test fun a_newer_handheld_save_folds_back_and_the_phones_reaches_the_handheld() {
        val s = serve()
        write(s.saves, "GBA/Emerald.srm", "OLD", t1)
        write(s.saves, "MGBA/Emerald.gba.sav", "NEW", t3)
        write(s.saves, "DCX/vmu_save_A1.bin", "CARD", t1)
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.srm", "MID", t2)
        val send = File(phone, "work/saves-send")
        val recv = File(phone, "work/saves-recv")
        val files = SyncSaveFiles(saves, send, recv)
        sync(s, files, saves, send, recv, mapOf(gba to setOf("Emerald.gba"), mgba to setOf("Emerald.gba")))

        val mine = File(saves, "GBA/Emerald.srm")
        assertEquals("NEW", mine.readText())
        assertTrue(Math.abs(mine.lastModified() - t3) <= 1000)
        assertFalse(File(saves, "DCX").exists())
        assertEquals(listOf("GBA"), saves.list()!!.toList())
        // MID (t2) was newer than the handheld's OLD (t1): it replaced it, and reached the MGBA folder too.
        assertEquals("MID", File(s.saves, "GBA/Emerald.srm").readText())
        assertEquals("MID", File(s.saves, "MGBA/Emerald.srm").readText())
        assertEquals("CARD", File(s.saves, "DCX/vmu_save_A1.bin").readText())
        assertFalse(send.exists() || recv.exists())
    }

    @Test fun an_older_phone_save_does_not_replace_the_handhelds() {
        val s = serve()
        write(s.saves, "GBA/Emerald.srm", "HANDHELD", t3)
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.srm", "PHONE", t1)
        val files = SyncSaveFiles(saves, File(phone, "send"), File(phone, "recv"))
        sync(s, files, saves, File(phone, "send"), File(phone, "recv"), mapOf(gba to setOf("Emerald.gba")))
        assertEquals("HANDHELD", File(s.saves, "GBA/Emerald.srm").readText())
        assertEquals("HANDHELD", File(saves, "GBA/Emerald.srm").readText())
    }

    /** Both devices on one Save format: once in step, a second sync downloads nothing and folds nothing. */
    @Test fun a_second_sync_downloads_nothing() {
        val s = serve()
        write(s.saves, "MGBA/Emerald.srm", "NEW", t3)
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.srm", "MID", t2)
        val send = File(phone, "send")
        val recv = File(phone, "recv")
        val files = SyncSaveFiles(saves, send, recv)
        val handheld = mapOf(mgba to setOf("Emerald.gba"))
        val first = sync(s, files, saves, send, recv, handheld)
        assertTrue(first.received, "Emerald" in first.received)
        assertEquals("NEW", File(saves, "GBA/Emerald.srm").readText())

        val second = sync(s, files, saves, send, recv, handheld)
        assertTrue(second.received, second.received.lines().none { "Emerald" in it })
        assertEquals(FoldPlan(emptyList(), emptyList(), 0), second.fold)
    }

    /** A handheld holding one game under two formats: the second sync downloads neither name again. */
    @Test fun a_second_sync_downloads_no_other_format_name() {
        val s = serve()
        write(s.saves, "GBA/Emerald.srm", "NEW", t3)
        write(s.saves, "GBA/Emerald.gba.sav", "OLD", t1)
        write(s.saves, "MGBA/Emerald.sav", "OLDER", t1)
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        val send = File(phone, "send")
        val recv = File(phone, "recv")
        val files = SyncSaveFiles(saves, send, recv)
        val handheld = mapOf(gba to setOf("Emerald.gba"), mgba to emptySet())
        val first = sync(s, files, saves, send, recv, handheld)
        assertTrue(first.received, "Emerald.gba.sav" in first.received)
        assertEquals("NEW", File(saves, "GBA/Emerald.srm").readText())

        val second = sync(s, files, saves, send, recv, handheld)
        assertTrue(second.received, second.received.lines().none { "Emerald" in it })
        assertEquals(FoldPlan(emptyList(), emptyList(), 0), second.fold)
        assertEquals("OLD", File(s.saves, "GBA/Emerald.gba.sav").readText())
    }

    /**
     * A seed dated a second after the phone copy could hide a handheld save the fold would take, as rsync's
     * `--modify-window=1` sees the two as one time (same size: skipped). Phone 10.900 s, handheld other name 12.000 s.
     */
    @Test fun a_handheld_save_just_over_a_second_newer_under_another_name_is_received() {
        val s = serve()
        write(s.saves, "GBA/Emerald.gba.sav", "NEW", t1 + 12_000)
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.srm", "OLD", t1 + 10_900)
        val send = File(phone, "send")
        val recv = File(phone, "recv")
        val run = sync(s, SyncSaveFiles(saves, send, recv), saves, send, recv, mapOf(gba to setOf("Emerald.gba")))
        assertTrue(run.received, "Emerald.gba.sav" in run.received)
        assertEquals("NEW", File(saves, "GBA/Emerald.srm").readText())
    }

    /** A seed is dated exactly the phone copy's time (the fold's margin alone decides what wins). */
    @Test fun a_seed_takes_the_phone_copys_own_time() {
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.srm", "MID", t2 + 900)
        val recv = File(phone, "recv")
        val files = SyncSaveFiles(saves, File(phone, "send"), recv)
        assertTrue(files.stage(listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.srm")), listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.gba.sav"))))
        assertEquals(t2 + 900, File(recv, "GBA/Emerald.gba.sav").lastModified())
        assertFalse(File(phone, "send/GBA/Emerald.gba.sav").exists())
    }

    @Test fun listing_leaves_out_cut_short_writes_and_mac_metadata() {
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.srm", "OK", t1)
        write(saves, "GBA/Emerald.srm.1a2b3c4d.tmp", "CUT", t1)
        write(saves, "GBA/Ruby.srm.part", "CUT", t1)
        write(saves, "GBA/._Emerald.srm", "MAC", t1)
        val files = SyncSaveFiles(saves, File(phone, "send"), File(phone, "recv"))
        assertEquals(listOf("GBA/Emerald.srm"), files.listSaves().map { it.path })
    }

    @Test fun a_plan_path_outside_its_root_is_refused_before_any_write() {
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.srm", "MID", t2)
        val recv = File(phone, "recv")
        write(recv, "MGBA/Emerald.srm", "NEW", t3)
        val files = SyncSaveFiles(saves, File(phone, "send"), recv)
        val escape = FoldPlan(listOf(FoldWrite("MGBA/Emerald.srm", "../evil.srm", t3, battery = true)), listOf("/GBA/Emerald.srm"), 0)
        assertFalse(files.fold(escape, compressed = false))
        assertFalse(File(phone, "evil.srm").exists())
        assertEquals("MID", File(saves, "GBA/Emerald.srm").readText())
        assertFalse(files.stage(listOf(StageCopy("GBA/../../x", "GBA/x"))))
        assertFalse(File(phone, "send/GBA/x").exists())
    }

    @Test fun a_failed_write_keeps_the_games_other_names() {
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.gba.sav", "OLDNAME", t1)
        val files = SyncSaveFiles(saves, File(phone, "send"), File(phone, "recv"))
        // The received file is missing: the write fails, so the phone's other-format copy of that game stays.
        val plan = FoldPlan(listOf(FoldWrite("MGBA/Emerald.srm", "GBA/Emerald.srm", t3, battery = true)), listOf("GBA/Emerald.gba.sav"), 0)
        assertFalse(files.fold(plan, compressed = false))
        assertEquals("OLDNAME", File(saves, "GBA/Emerald.gba.sav").readText())
        assertFalse(File(saves, "GBA/Emerald.srm").exists())
    }

    @Test fun a_battery_write_is_re_encoded_for_a_compressed_format() {
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        val recv = File(phone, "recv")
        write(recv, "MGBA/Emerald.srm", "RAW", t3)
        val files = SyncSaveFiles(saves, File(phone, "send"), recv)
        assertTrue(files.fold(FoldPlan(listOf(FoldWrite("MGBA/Emerald.srm", "GBA/Emerald.srm", t3, battery = true)), emptyList(), 0), compressed = true))
        val bytes = File(saves, "GBA/Emerald.srm").readBytes()
        assertEquals("#RZIPv", String(bytes, 0, 6))
        assertEquals("RAW", String(com.nxredux.mobile.storage.Rzip.decode(bytes)!!))
        assertTrue(Math.abs(File(saves, "GBA/Emerald.srm").lastModified() - t3) <= 1000)
    }

    /** A staged copy without the phone file's time would win step 4's `--update` wrongly: staging fails. */
    @Test fun staging_fails_when_a_copys_time_is_not_set() {
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.srm", "MID", t2)
        val files = SyncSaveFiles(saves, File(phone, "send"), File(phone, "recv"), setTime = { _, _ -> false })
        assertFalse(files.stage(listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.srm"))))
        // A setter that reports false but did set the time (some file systems) still stages.
        val lying = SyncSaveFiles(saves, File(phone, "send"), File(phone, "recv"), setTime = { f, t -> f.setLastModified(t); false })
        assertTrue(lying.stage(listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.srm"))))
    }

    /**
     * A seed must carry its time exactly (a later one could win the fold over a newer handheld save);
     * one that does not is dropped, costing only a redundant download, while the staged copies keep the 2 s tolerance.
     */
    @Test fun a_seed_not_dated_exactly_is_dropped() {
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        write(saves, "GBA/Emerald.srm", "MID", t2)
        val recv = File(phone, "recv")
        val copy = listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.srm"))
        val seed = listOf(StageCopy("GBA/Emerald.srm", "GBA/Emerald.gba.sav"))
        // Lands a second late though the setter reports success.
        val late = SyncSaveFiles(saves, File(phone, "send"), recv, setTime = { f, t -> f.setLastModified(t + 1000) })
        assertTrue(late.stage(copy, seed))
        assertFalse(File(recv, "GBA/Emerald.gba.sav").exists())
        assertTrue(File(recv, "GBA/Emerald.srm").exists())
        // Reports false but set exactly: kept.
        val lying = SyncSaveFiles(saves, File(phone, "send"), recv, setTime = { f, t -> f.setLastModified(t); false })
        assertTrue(lying.stage(copy, seed))
        assertEquals(t2, File(recv, "GBA/Emerald.gba.sav").lastModified())
    }

    /** A fold write whose time is not set keeps its (correct) bytes and is no failure: only logged. */
    @Test fun a_fold_write_without_its_time_still_counts() {
        val phone = tmp.newFolder("phone")
        val saves = File(phone, "Saves")
        val recv = File(phone, "recv")
        write(recv, "MGBA/Emerald.srm", "RAW", t3)
        val files = SyncSaveFiles(saves, File(phone, "send"), recv, setTime = { _, _ -> false })
        assertTrue(files.fold(FoldPlan(listOf(FoldWrite("MGBA/Emerald.srm", "GBA/Emerald.srm", t3, battery = true)), emptyList(), 0), compressed = false))
        assertEquals("RAW", File(saves, "GBA/Emerald.srm").readText())
    }
}
