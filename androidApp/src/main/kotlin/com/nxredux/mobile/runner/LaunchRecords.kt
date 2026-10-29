package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.Recent
import java.io.File

/**
 * Launches on disk, shared by the menu and the game screen (they may be different processes): the menu writes
 * `<id>.pending` per launch, the game renames it to `<id>.started` on its first frame, and the menu (alive or
 * restarted) turns started records into Recents. A refused or abandoned launch is [drop]ped; a rename is atomic,
 * so a record is never half-started. Content is `path\tname`.
 */
class LaunchRecords(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {
    // Seeded per instance: two processes writing here must not meet on one id.
    private var nextId = kotlin.random.Random.nextLong(0, Long.MAX_VALUE / 2)

    /** Writes a pending record for [recent] and returns its id for the game's intent. */
    @Synchronized fun pending(recent: Recent): Long {
        dir.mkdirs()
        var id = nextId++
        while (File(dir, "$id.pending").exists() || File(dir, "$id.started").exists()) id = nextId++
        val file = File(dir, "$id.pending")
        file.writeText(recent.path.escape() + "\t" + (recent.alias?.escape() ?: ""))
        file.setLastModified(now())   // the sweep's age, on the same clock
        return id
    }

    /** Marks [id] started; false when there is no such pending record (dropped, swept, or never written). */
    fun started(id: Long): Boolean = id >= 0 && File(dir, "$id.pending").renameTo(File(dir, "$id.started"))

    /** The launch never started: nothing will record it. Only a pending record: a started one is the menu's to drain, however the game ends. */
    fun drop(id: Long) {
        if (id < 0) return
        File(dir, "$id.pending").delete()
    }

    /** Takes every started record (each is read once, then deleted) and sweeps pending ones a day old: no start will come for them. */
    @Synchronized fun drainStarted(): List<Recent> {
        val files = dir.listFiles() ?: return emptyList()
        val out = ArrayList<Recent>()
        for (f in files.sortedBy { it.name }) {
            when (f.extension) {
                "started" -> {
                    val text = runCatching { f.readText() }.getOrNull()
                    if (f.delete() && text != null) parse(text)?.let { out += it }
                }
                "pending" -> if (now() - f.lastModified() > DAY_MS) f.delete()
            }
        }
        return out
    }

    private fun parse(text: String): Recent? {
        val tab = text.indexOf('\t')
        if (tab < 0) return null
        val path = text.substring(0, tab).unescape()
        val alias = text.substring(tab + 1).unescape()
        return if (path.isEmpty()) null else Recent(path, alias.ifEmpty { null })
    }

    // A tab or newline inside a name must not split the pair.
    private fun String.escape() = replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    private fun String.unescape(): String {
        val sb = StringBuilder()
        var i = 0
        while (i < length) {
            val c = this[i]
            if (c == '\\' && i + 1 < length) {
                sb.append(when (this[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> this[i + 1] }); i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    companion object {
        const val NONE = -1L   // an intent without an id (a restore from recents in a fresh process)
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun of(context: android.content.Context) = LaunchRecords(File(context.filesDir, "launches"))

        /** The explicit-package broadcast a game sends once it has started; the menu drains on it. No extras. */
        fun action(context: android.content.Context) = context.packageName + ".LAUNCH_STARTED"
    }
}
