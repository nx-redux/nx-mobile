package com.nxredux.mobile.netplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** The process side of [Rsync] with shell scripts standing in for the binary. */
class RsyncProcessTest {
    @get:Rule val tmp = TemporaryFolder()

    /** android.system.Os is a stub on the JVM: signals go through kill(1). */
    @Before fun hostSignals() {
        Rsync.signal = { pid, sig -> ProcessBuilder("kill", "-$sig", "$pid").start().waitFor() }
    }

    private fun alive(pid: Int) = ProcessBuilder("kill", "-0", "$pid").redirectErrorStream(true).start().waitFor() == 0

    // The waits below are generous deadlines, not expected durations: a loaded machine (the whole suite, a CI runner) is
    // slow to start a process, and a test that cancelled after a fixed few hundred ms could cancel the script before it
    // ran ("no pid in daemon.pid" only in full runs).
    private fun pidIn(f: File): Int {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            f.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("no pid in $f")
    }

    private fun gone(pid: Int): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (alive(pid) && System.currentTimeMillis() < deadline) Thread.sleep(20)
        return !alive(pid)
    }

    /**
     * Starts [call] in the background, waits until the script has written its pid to [running] (it is under way, past
     * any slow start), then cancels the call and waits for it to finish, its cleanup included.
     */
    private fun cancelOnceRunning(running: File, call: suspend () -> Unit) = runBlocking {
        val job = launch(Dispatchers.IO) { call() }
        pidIn(running)
        assertTrue("the call ended before it was cancelled", job.isActive)
        job.cancelAndJoin()
    }

    private val quiet: (String, Throwable?) -> Unit = { _, _ -> }

    private fun script(name: String, body: String): File =
        File(tmp.root, name).apply { writeText("#!/bin/sh\n$body\n"); setExecutable(true) }

    /** A script that is slow to get going, as one is on a loaded machine: longer than any fixed wait a test could pick. */
    private fun slowScript(name: String, body: String, startS: String = "0.6"): File = script(name, "sleep $startS\n$body")

    @Test fun probe_takes_a_binary_that_prints_rsyncs_version() {
        assertNotNull(Rsync.probe(script("ok.so", "echo 'rsync  version 3.4.1  protocol version 32'")))
    }

    @Test fun probe_refuses_a_missing_plain_failing_or_foreign_file() {
        assertNull(Rsync.probe(File(tmp.root, "absent.so")))
        assertNull(Rsync.probe(tmp.newFile("plain.so")))
        assertNull(Rsync.probe(script("fails.so", "echo 'rsync  version 3.4.1'; exit 1")))
        assertNull(Rsync.probe(script("foreign.so", "echo hello")))
    }

    @Test fun pull_runs_the_command_line_and_succeeds_on_exit_0() = runBlocking {
        val dest = tmp.newFolder("stage")
        val args = File(tmp.root, "args.txt")
        val bin = script("rs.so", "echo \"\$@\" > '${args.absolutePath}'\nfor last; do :; done\necho data > \"\${last}nxsave.srm\"")
        Rsync(bin).pull("10.0.0.2", "nxsave.srm", dest)
        assertEquals("-t --inplace --no-perms --timeout=8 --contimeout=4 rsync://10.0.0.2:18731/sync/nxsave.srm ${dest.absolutePath}/", args.readText().trim())
        assertTrue(File(dest, "nxsave.srm").isFile)
    }

    @Test fun a_failing_pull_throws_with_rsyncs_words() = runBlocking {
        val bin = script("rs.so", "echo '@ERROR: access denied to sync'\nexit 5")
        try {
            Rsync(bin).pull("10.0.0.2", "nxsave.srm", tmp.newFolder("stage"))
            fail("exit 5 passed")
        } catch (e: IOException) {
            assertTrue(e.message!!, e.message!!.contains("access denied"))
        }
    }

    @Test fun a_cancelled_pull_kills_rsync() {
        val self = File(tmp.root, "rsync.pid")
        val marker = File(tmp.root, "finished")
        val bin = slowScript("rs.so", "echo \$\$ > '${self.absolutePath}'\nsleep 30\ntouch '${marker.absolutePath}'")
        val stage = tmp.newFolder("stage")
        cancelOnceRunning(self) { Rsync(bin).pull("10.0.0.2", "nxsave.srm", stage) }
        assertTrue("rsync outlived the cancelled pull", gone(pidIn(self)))
        assertFalse(marker.exists())
    }

    /** rsync's receiver is a forked child of the process we started; killing only the parent would orphan it. */
    @Test fun a_cancelled_pull_kills_rsyncs_forked_child_too() {
        val kid = File(tmp.root, "kid.pid")
        val bin = slowScript("rs.so", "sleep 30 &\necho \$! > '${kid.absolutePath}'\nwait")
        val stage = tmp.newFolder("stage")
        cancelOnceRunning(kid) { Rsync(bin, quiet).pull("10.0.0.2", "nxsave.srm", stage) }
        assertTrue("the forked child outlived the pull", gone(pidIn(kid)))
    }

    @Test fun a_cancelled_daemon_start_stops_the_daemon_and_its_child() {
        val self = File(tmp.root, "daemon.pid")
        val kid = File(tmp.root, "kid.pid")
        // Slower to start than startDaemon's own 2 s wait for the daemon to listen: the test gives it a long wait instead,
        // so the cancel, not that timeout, is what stops it.
        val bin = slowScript("rs.so", "echo \$\$ > '${self.absolutePath}'\nsleep 30 &\necho \$! > '${kid.absolutePath}'\nwait", startS = "2.5")
        val serve = tmp.newFolder("serve")
        // The child's pid is written last: once it is there, the daemon and its child are both running.
        cancelOnceRunning(kid) { Rsync(bin, quiet, readyMs = 60_000).startDaemon("10.0.0.3", serve, File(tmp.root, "work")) }
        assertTrue("the daemon outlived its cancelled start", gone(pidIn(self)))
        assertTrue("the daemon's child outlived its cancelled start", gone(pidIn(kid)))
    }

    @Test fun a_daemon_that_never_listens_is_stopped_and_refused() = runBlocking {
        val self = File(tmp.root, "daemon.pid")
        val bin = script("rs.so", "echo \$\$ > '${self.absolutePath}'\nexec sleep 30")
        val work = File(tmp.root, "work")
        // 5 s, not the 2 s default: a loaded machine slow to start the script must not beat the wait to its pid file.
        assertNull(Rsync(bin, quiet, readyMs = 5_000).startDaemon("10.0.0.3", tmp.newFolder("serve"), work))
        assertTrue(gone(pidIn(self)))
        assertTrue(File(work, "rsyncd.conf").readText().contains("hosts allow = 10.0.0.3\n"))
    }

    @Test fun streaming_feeds_each_line_split_on_cr_and_lf_and_returns_the_exit_code() = runBlocking {
        val bin = script("stream.so", "printf 'sending incremental file list\\nGB/a.st0\\n  1 (xfr#1, to-chk=1/2)\\r  2 (xfr#1, to-chk=0/2)\\n'; exit 23")
        val lines = mutableListOf<String>()
        val code = Rsync(bin, quiet).runStreaming(listOf(bin.absolutePath), lines::add)
        assertEquals(23, code)
        assertEquals(listOf("sending incremental file list", "GB/a.st0", "  1 (xfr#1, to-chk=1/2)", "  2 (xfr#1, to-chk=0/2)"), lines)
    }

    @Test fun cancelling_a_streaming_step_stops_the_process() {
        val running = File(tmp.root, "step.pid")
        val bin = slowScript("step.so", "echo \$\$ > '${running.absolutePath}'; while true; do sleep 1; done")
        cancelOnceRunning(running) { Rsync(bin, quiet).runStreaming(listOf(bin.absolutePath)) {} }
        assertTrue(gone(pidIn(running)))
    }

    @Test fun a_daemon_with_its_own_config_and_port_comes_up_and_writes_the_conf() = runBlocking {
        val port = java.net.ServerSocket(0).use { it.localPort }   // a free port; the fake daemon listens on it
        val work = tmp.newFolder("work")
        val bin = script("d.so", "exec python3 -c 'import socket,time; s=socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); s.bind((\"127.0.0.1\", $port)); s.listen(1); time.sleep(30)'")
        val p = Rsync(bin, quiet, readyMs = 10_000).startDaemonWith("pid file = ${work.absolutePath}/rsyncd.pid\nport = $port\n", port, work)
        try {
            assertNotNull(p)
            assertEquals("pid file = ${work.absolutePath}/rsyncd.pid\nport = $port\n", File(work, "rsyncd.conf").readText())
        } finally {
            p?.let { Rsync.stop(it) }
        }
        assertFalse(p!!.isAlive)
    }

    @Test fun a_daemon_that_never_listens_on_its_own_config_is_stopped_and_null() = runBlocking {
        val work = tmp.newFolder("work2")
        val running = File(tmp.root, "never.pid")
        val bin = script("never.so", "echo \$\$ > '${running.absolutePath}'; sleep 30")
        assertNull(Rsync(bin, quiet, readyMs = 5_000).startDaemonWith("port = 1\n", 1, work))
        assertTrue(gone(pidIn(running)))
    }
}
