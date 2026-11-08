package com.nxredux.mobile.sync

/** One rsync step's count so far: files transferred, and files checked of the total (`to-chk=X/Y` → Y-X of Y). */
data class StepProgress(val transferred: Int, val done: Int, val total: Int)

/** A daemon transfer-log line (`log format = %m %o %f`): [received] true for `recv` (the peer pushed it to us). */
data class DaemonEntry(val module: String, val received: Boolean, val path: String)

/** Reads rsync's output the way the handheld's `run_rsync_phase` and server log tail do. */
object SyncProgress {
    private val XFR = Regex("""xfr#(\d+)""")
    private val TO_CHECK = Regex("""to-che?c?k=(\d+)/(\d+)""")
    private val DAEMON = Regex("""^\d{4}/\d\d/\d\d \d\d:\d\d:\d\d \[\d+] (shared|saves|bios|collections|roms|extra-\d+|nx-art|nx-extras) (recv|send) (.+)$""")

    private val SESSION = Regex("""^\d{4}/\d\d/\d\d \d\d:\d\d:\d\d \[(\d+)] (connect from |sent \d|rsync error: )""")

    /**
     * A daemon log line that opens (`[pid] connect from …`) or closes (its exit: `[pid] sent N bytes  received …`, or
     * `[pid] rsync error: …`) one connection: its pid and true for an open; else null.
     */
    fun daemonSession(line: String): Pair<String, Boolean>? {
        val m = SESSION.find(line) ?: return null
        return m.groupValues[1] to (m.groupValues[2] == "connect from ")
    }

    private val AUTH_FAILED = Regex("""^\d{4}/\d\d/\d\d \d\d:\d\d:\d\d \[\d+] auth failed on module """)

    /** A daemon log line saying a client gave the wrong user or password. */
    fun authFailed(line: String): Boolean = AUTH_FAILED.containsMatchIn(line)

    /** A `--info=progress2` line with `to-chk=`, else null. */
    fun progress(line: String): StepProgress? {
        val (left, total) = TO_CHECK.find(line)?.destructured ?: return null
        val xfr = XFR.find(line)?.groupValues?.get(1)?.toInt() ?: 0
        return StepProgress(transferred = xfr, done = total.toInt() - left.toInt(), total = total.toInt())
    }

    /** A file name rsync printed (`-v`), else null: blank lines, rsync's summary lines, progress lines and folders and rsync's own messages. */
    fun fileLine(line: String): String? {
        if (line.isBlank()) return null
        if (listOf("sending incremental", "receiving incremental", "sent ", "total size").any { line.startsWith(it) }) return null
        if (TO_CHECK.containsMatchIn(line)) return null
        if (line.startsWith(' ') && '%' in line) return null
        if (line.endsWith('/')) return null
        if (line.startsWith("rsync: ") || line.startsWith("rsync error:") || line.startsWith("@ERROR")) return null
        return line
    }

    /**
     * A daemon transfer-log line (`2026/10/14 23:28:27 [123] shared recv GB-gambatte/T.st0`; path relative to the
     * module) as a [DaemonEntry], else null (connects, errors, totals). rsync logs `recv` only once the file is complete.
     */
    fun daemonLine(line: String): DaemonEntry? {
        val (module, dir, path) = DAEMON.find(line.trimEnd('\n', '\r'))?.destructured ?: return null
        return DaemonEntry(module, received = dir == "recv", path = path)
    }
}
