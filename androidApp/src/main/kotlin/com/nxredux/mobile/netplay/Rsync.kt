package com.nxredux.mobile.netplay

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** netplay: copies one offered save from the host into a staging dir (the bundled rsync on a device; a fake in tests). */
interface SavePuller {
    /** Pulls rsync://[hostIp]:18731/sync/[wireName] into [destDir]; throws on failure; cancellation stops the copy. */
    suspend fun pull(hostIp: String, wireName: String, destDir: File)
}

/**
 * netplay: the bundled rsync 3.4.1 (tools/rsync, `jniLibs/arm64-v8a/librsync_exec.so`, extracted at install), run as a
 * child process with [RsyncCommands]' command lines: a client pull per offered file, or a daemon while this device
 * hosts netplay or serves Device Sync.
 *
 * No rsync process outlives the call that started it unless it is handed back (a running daemon): a process is recorded
 * the moment it starts, before any suspension point, and every exit path that does not return it [stop]s it and its
 * forked children. [warn] is NxLog (Settings → System → Debug log) on a device and injectable so the JVM tests can
 * run [startDaemon].
 */
class Rsync internal constructor(
    val binary: File,
    private val warn: (String, Throwable?) -> Unit = ::logWarn,
    /** How long [startDaemon] waits for the daemon to listen; tests on a loaded machine give a slow-starting fake longer. */
    private val readyMs: Long = DAEMON_READY_MS,
) : SavePuller {
    override suspend fun pull(hostIp: String, wireName: String, destDir: File) {
        val args = RsyncCommands.pullArgs(binary.absolutePath, hostIp, wireName, destDir.absolutePath)
        val log = File(destDir.parentFile ?: destDir, PULL_LOG)
        val started = AtomicReference<Process?>()
        try {
            withContext(Dispatchers.IO) { started.set(launch(args, log)) }
            val code = runInterruptible(Dispatchers.IO) { started.get()!!.waitFor() }
            if (code != 0) throw IOException("rsync pull of $wireName exited $code: ${tail(log)}")
        } finally {
            started.get()?.let { p -> if (p.isAlive) withContext(NonCancellable + Dispatchers.IO) { stop(p) } }
        }
    }

    /**
     * Starts the daemon serving [stage] to [joinerIp] only (config and pid file in [workDir]) and waits up to 2 s until it
     * listens on 18731 (the wait lives in [startDaemonWith]); the running process, or null when it did not come up.
     * Every path that does not return the process, cancellation included, stops it.
     */
    suspend fun startDaemon(joinerIp: String, stage: File, workDir: File): Process? {
        val pidFile = File(workDir, PID_FILE).absolutePath
        return startDaemonWith(RsyncCommands.daemonConf(joinerIp, stage.absolutePath, pidFile), RsyncCommands.PORT, workDir)
    }

    /**
     * Device Sync: runs [args] (the binary first), handing each output line to [onLine] (rsync's progress2 rewrites a
     * line with `\r`, so both `\r` and `\n` end one); the exit code. [onLine] may still be called for output flushed
     * before EOF. Cancellation stops the process and its children and always surfaces as the CancellationException,
     * never as a read error from the pipe closing.
     */
    suspend fun runStreaming(args: List<String>, onLine: (String) -> Unit): Int {
        val started = AtomicReference<Process?>()
        try {
            // Started before the reader child exists, as in pull(): a cancel can then never land inside start().
            val p = withContext(Dispatchers.IO) {
                ProcessBuilder(args).redirectErrorStream(true).start().also { argvOf[it] = args; started.set(it) }
            }
            val cancelled = AtomicBoolean(false)
            return coroutineScope {
                // The pipe read ignores interrupts, so cancelling must stop the process itself: that ends the read,
                // and only then can coroutineScope finish joining the reader.
                val reader = async(Dispatchers.IO) {
                    try {
                        val input = p.inputStream.reader(Charsets.UTF_8)
                        val line = StringBuilder()
                        val buf = CharArray(4096)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            for (i in 0 until n) {
                                val c = buf[i]
                                if (c == '\n' || c == '\r') {
                                    if (line.isNotEmpty()) onLine(line.toString())
                                    line.setLength(0)
                                } else line.append(c)
                            }
                        }
                        if (line.isNotEmpty()) onLine(line.toString())
                    } catch (e: IOException) {
                        if (!cancelled.get()) throw e   // the stream closing under a cancel is not a failure
                    }
                    p.waitFor()
                }
                try {
                    reader.await()
                } catch (e: CancellationException) {
                    cancelled.set(true)
                    withContext(NonCancellable + Dispatchers.IO) { stop(p) }
                    throw e
                }
            }
        } finally {
            started.get()?.let { p -> if (p.isAlive) withContext(NonCancellable + Dispatchers.IO) { stop(p) } }
        }
    }

    /**
     * Starts a daemon on [confText] (written to `rsyncd.conf` in [workDir]) and waits until [port] listens; see
     * [startDaemon].
     */
    suspend fun startDaemonWith(confText: String, port: Int, workDir: File): Process? {
        val started = AtomicReference<Process?>()
        var handedBack = false
        try {
            val up = withContext(Dispatchers.IO) { bringUp(confText, port, workDir, started) }
            if (!up) return null
            handedBack = true   // no suspension point between this and the return
            return started.get()
        } finally {
            if (!handedBack) started.get()?.let { p -> withContext(NonCancellable + Dispatchers.IO) { stop(p) } }
        }
    }

    private suspend fun bringUp(confText: String, port: Int, workDir: File, started: AtomicReference<Process?>): Boolean {
        if (!workDir.isDirectory && !workDir.mkdirs()) return false
        val pid = File(workDir, PID_FILE)
        stopStale(pid)
        pid.delete()   // a stale pid file from a killed session would stop rsync
        val conf = File(workDir, CONF_FILE)
        val log = File(workDir, DAEMON_LOG)
        conf.writeText(confText)
        try {
            started.set(launch(RsyncCommands.daemonArgs(binary.absolutePath, conf.absolutePath), log))
        } catch (e: IOException) {
            warn("rsync daemon did not start", e)
            return false
        }
        val p = started.get()!!
        val deadline = System.nanoTime() + readyMs * 1_000_000
        while (System.nanoTime() < deadline && p.isAlive) {
            if (listening(port)) {
                delay(SETTLE_MS)   // another daemon on the port answers too, but ours then exits on its failed bind
                if (p.isAlive) return true
                break
            }
            delay(POLL_MS)
        }
        warn("rsync daemon not listening: ${tail(log)}", null)
        return false
    }

    /** A daemon left by a killed session still holds 18731: SIGTERM it when its pid file names the bundled rsync. */
    private suspend fun stopStale(pidFile: File) {
        val stale = ProcTree.staleRsync(ProcTree.PROC, pidFile) ?: return
        signal(stale, SIGTERM)
        val deadline = System.nanoTime() + GRACE_MS * 1_000_000
        while (System.nanoTime() < deadline && File(ProcTree.PROC, "$stale").exists()) delay(POLL_MS / 2)
        if (File(ProcTree.PROC, "$stale").exists()) signal(stale, SIGKILL)
    }

    companion object {
        const val FILE_NAME = "librsync_exec.so"
        private const val PULL_LOG = "rsync-pull.log"
        private const val PID_FILE = "rsyncd.pid"
        private const val CONF_FILE = "rsyncd.conf"
        private const val DAEMON_LOG = "rsyncd.log"
        private const val DAEMON_READY_MS = 2_000L
        private const val SETTLE_MS = 150L
        private const val POLL_MS = 100L
        private const val PROBE_S = 3L
        private const val GRACE_MS = 500L
        private const val SIGTERM = 15
        private const val SIGKILL = 9

        /** Sends signal `sig` to `pid`; `android.system.Os.kill` on a device, swapped in JVM tests (Os is a stub there). */
        @Volatile internal var signal: (pid: Int, sig: Int) -> Unit = { pid, sig ->
            try { Os.kill(pid, sig) } catch (_: ErrnoException) {}   // ESRCH: already gone
        }

        /** The argv each live process was started with: on Android, which has no `Process.pid()`, it finds the pid. */
        private val argvOf: MutableMap<Process, List<String>> = Collections.synchronizedMap(WeakHashMap())

        @Volatile private var probed = false
        private var found: Rsync? = null

        /** The bundled binary when it is there and runs; probed once per process. Blocking: call off the main thread. */
        fun find(context: Context): Rsync? {
            if (probed) return found
            synchronized(this) {
                if (!probed) {
                    found = probe(File(context.applicationInfo.nativeLibraryDir, FILE_NAME))
                    probed = true
                }
                return found
            }
        }

        /** [file] as an [Rsync] when it is an executable file whose `--version` exits 0 within 3 s and names rsync. */
        internal fun probe(file: File): Rsync? {
            if (!file.isFile || !file.canExecute()) return null
            return try {
                val p = ProcessBuilder(file.absolutePath, "--version").redirectErrorStream(true).start()
                if (!p.waitFor(PROBE_S, TimeUnit.SECONDS)) { stop(p); return null }
                val out = p.inputStream.bufferedReader().readText()
                if (p.exitValue() == 0 && out.trimStart().startsWith("rsync")) Rsync(file) else null
            } catch (e: IOException) {
                null
            }
        }

        /**
         * Stops [p] and everything it forked (rsync's receiver): SIGTERM to all, then SIGKILL to whatever is left after
         * 0.5 s. The descendants are found before [p] dies, while they are still its children; [p] is signalled first, so
         * it can't go on to its next step when a child it waits on dies. Blocking.
         */
        fun stop(p: Process) {
            val kids = pidOf(p)?.let { ProcTree.descendants(ProcTree.live(), it) }.orEmpty()
            p.destroy()
            kids.forEach { signal(it, SIGTERM) }
            val deadline = System.nanoTime() + GRACE_MS * 1_000_000
            val exited = p.waitFor(GRACE_MS, TimeUnit.MILLISECONDS)
            var left = kids
            while (left.isNotEmpty()) {
                left = ProcTree.live().keys.let { live -> left.filter { it in live } }
                if (left.isEmpty() || System.nanoTime() >= deadline) break
                Thread.sleep(20)
            }
            if (!exited) p.destroyForcibly()
            left.forEach { signal(it, SIGKILL) }
            argvOf.remove(p)
        }

        private fun launch(args: List<String>, log: File): Process =
            ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log).start().also { argvOf[it] = args }

        /** [p]'s pid: `Process.pid()` where the runtime has it (a JVM), else its argv among this app's children (Android). */
        private fun pidOf(p: Process): Int? =
            runCatching { (Process::class.java.getMethod("pid").invoke(p) as Long).toInt() }.getOrNull()
                ?: argvOf[p]?.let { args ->
                    runCatching { Os.getpid() }.getOrNull()?.let { ProcTree.childWithArgs(ProcTree.PROC, it, args) }
                }

        private fun listening(port: Int): Boolean =
            runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }; true }.getOrDefault(false)

        private fun tail(log: File): String = runCatching { log.readText().takeLast(300).trim() }.getOrDefault("")
    }
}

private fun logWarn(message: String, error: Throwable?) {
    NxLog.w("NxNetplay", message, error)
}
