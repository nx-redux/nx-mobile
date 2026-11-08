package com.nxredux.mobile.sync

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * The daemon's transfer log as [SyncProgress.daemonLine] reads it, from a real rsync ≥ 3.1 daemon started with
 * [SyncCommands.daemonConf] and driven with [SyncCommands.clientArgs] (skipped when there is no such rsync, e.g. on CI).
 */
class SyncDaemonRsyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private var daemon: Process? = null

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

    /** Runs [step] against the daemon on [port] (the args' fixed port swapped for it); its exit code. */
    private fun client(bin: String, step: SyncStep, root: File, port: Int): Int {
        val args = SyncCommands.clientArgs(bin, step, "127.0.0.1", root.path)
            .map { it.replace("127.0.0.1:${SyncWire.RSYNC_PORT}/", "127.0.0.1:$port/") }
        val p = ProcessBuilder(args).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText()
        return p.waitFor()
    }

    @Test fun the_daemon_log_parses_to_module_relative_entries() {
        val bin = rsync()
        assumeTrue("no rsync >= 3.1 on this machine", bin != null)
        val work = tmp.newFolder("work")
        val shared = tmp.newFolder("served-shared")
        val saves = tmp.newFolder("served-saves")
        val log = File(work, "sync.log")
        val port = freePort()
        val conf = SyncCommands.daemonConf("127.0.0.1", shared = shared.path, saves = saves.path,
            pidFile = File(work, "rsyncd.pid").path, logFile = log.path)
            .replace("port = ${SyncWire.RSYNC_PORT}\n", "port = $port\n")
        val confFile = File(work, "rsyncd.conf").apply { writeText(conf) }
        daemon = ProcessBuilder(bin!!, "--daemon", "--no-detach", "--config=${confFile.path}")
            .redirectErrorStream(true).redirectOutput(File(work, "daemon.out")).start()
        val deadline = System.currentTimeMillis() + 5_000
        while (runCatching { Socket("127.0.0.1", port).close() }.isFailure) {
            check(System.currentTimeMillis() < deadline) { "daemon did not listen: ${File(work, "daemon.out").readText()}" }
            Thread.sleep(50)
        }
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
