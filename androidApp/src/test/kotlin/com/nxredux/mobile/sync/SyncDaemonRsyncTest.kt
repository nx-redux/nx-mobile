package com.nxredux.mobile.sync

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The daemon's transfer log as [SyncProgress.daemonLine] reads it, from a real rsync ≥ 3.1 daemon started with
 * [SyncCommands.daemonConf] and driven with [SyncCommands.clientArgs] (skipped when there is no such rsync, e.g. on CI).
 */
class SyncDaemonRsyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private val daemon = RsyncDaemon()

    @After fun stopDaemon() {
        daemon.stop()
    }

    /** Runs [step] against the daemon on [port] (the args' fixed port swapped for it); its exit code. */
    private fun client(bin: String, step: SyncStep, root: File, port: Int): Int {
        val args = swapPort(SyncCommands.clientArgs(bin, step, "127.0.0.1", root.path), port)
        val p = ProcessBuilder(args).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText()
        return p.waitFor()
    }

    @Test fun the_daemon_log_parses_to_module_relative_entries() {
        val bin = hostRsync()
        assumeTrue("no rsync >= 3.1 on this machine", bin != null)
        val work = tmp.newFolder("work")
        val shared = tmp.newFolder("served-shared")
        val saves = tmp.newFolder("served-saves")
        val log = File(work, "sync.log")
        val conf = SyncCommands.daemonConf("127.0.0.1", shared = shared.path, saves = saves.path,
            pidFile = File(work, "rsyncd.pid").path, logFile = log.path)
        val port = daemon.start(bin!!, work, conf)
        // The peer's card: one state (plus a file the filter refuses) and one save to push.
        val peer = tmp.newFolder("peer")
        File(peer, ".userdata/shared/GB-gambatte").mkdirs()
        File(peer, ".userdata/shared/GB-gambatte/t.gb.st0").writeText("state")
        File(peer, "Saves/GB").mkdirs()
        File(peer, "Saves/GB/t.srm").writeText("save")
        assertEquals(0, client(bin, SyncStep.SEND_STATES, peer, port))
        assertEquals(0, client(bin, SyncStep.SEND_SAVES, peer, port))
        // A fresh card pulls the save back.
        val other = tmp.newFolder("other")
        File(other, "Saves").mkdirs()
        assertEquals(0, client(bin, SyncStep.RECEIVE_SAVES, other, port))
        assertEquals("save", File(other, "Saves/GB/t.srm").readText())

        val expected = listOf(
            DaemonEntry("shared", received = true, path = "GB-gambatte/t.gb.st0"),
            DaemonEntry("saves", received = true, path = "GB/t.srm"),
            DaemonEntry("saves", received = false, path = "GB/t.srm"),
        )
        // The daemon's connection child writes the log; give it a moment after the client exits.
        var entries = emptyList<DaemonEntry>()
        val logDeadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < logDeadline) {
            entries = if (log.exists()) log.readLines().mapNotNull(SyncProgress::daemonLine) else emptyList()
            if (entries.size >= expected.size) break
            Thread.sleep(50)
        }
        assertEquals(log.takeIf { it.exists() }?.readText().orEmpty(), expected, entries)
    }
}
