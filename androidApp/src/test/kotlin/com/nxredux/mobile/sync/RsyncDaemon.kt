package com.nxredux.mobile.sync

import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/** This machine's rsync ≥ 3.1, or null (the tests then skip, e.g. on CI). */
internal fun hostRsync(): String? = listOf("/opt/homebrew/bin/rsync", "/usr/local/bin/rsync", "/usr/bin/rsync").firstOrNull { path ->
    File(path).canExecute() && runCatching {
        val out = ProcessBuilder(path, "--version").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        Regex("""version (\d+)\.(\d+)""").find(out)?.destructured?.let { (a, b) -> a.toInt() > 3 || (a.toInt() == 3 && b.toInt() >= 1) } == true
    }.getOrDefault(false)
}

/** [args] with the daemon's fixed port swapped for [port]. */
internal fun swapPort(args: List<String>, port: Int) = args.map { it.replace("127.0.0.1:${SyncWire.RSYNC_PORT}/", "127.0.0.1:$port/") }

/** A real rsync daemon for one test, on a free port. */
internal class RsyncDaemon {
    private var process: Process? = null

    /** Starts [bin] with [conf] (its fixed port swapped), its files kept in [work]; the port, once it listens. */
    fun start(bin: String, work: File, conf: String): Int {
        val port = ServerSocket(0).use { it.localPort }
        val confFile = File(work, "rsyncd.conf").apply { writeText(conf.replace("port = ${SyncWire.RSYNC_PORT}\n", "port = $port\n")) }
        process = ProcessBuilder(bin, "--daemon", "--no-detach", "--config=${confFile.path}")
            .redirectErrorStream(true).redirectOutput(File(work, "daemon.out")).start()
        val deadline = System.currentTimeMillis() + 5_000
        while (runCatching { Socket("127.0.0.1", port).close() }.isFailure) {
            check(System.currentTimeMillis() < deadline) { "daemon did not listen: ${File(work, "daemon.out").readText()}" }
            Thread.sleep(50)
        }
        return port
    }

    fun stop() {
        process?.let { it.destroy(); if (!it.waitFor(5, TimeUnit.SECONDS)) it.destroyForcibly() }
    }
}
