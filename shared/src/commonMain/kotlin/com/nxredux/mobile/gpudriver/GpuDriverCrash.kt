package com.nxredux.mobile.gpudriver

/**
 * Pure rules for a custom driver that kills the game in its first seconds. Before a custom-driver launch the app writes a
 * [Marker] (which process, game and driver); it is cleared once the game has been presenting frames for
 * [CLEAR_AFTER_MS]. A marker whose process is gone means the launch died: the game then records the driver as crashed
 * (one `gamePath<TAB>driverId` per line) and uses the system driver, and the user is told ([notice]).
 *
 * Both are stored as text in platform settings; this object only reads and writes those strings.
 */
object GpuDriverCrash {
    /** How long a custom-driver game must keep presenting frames before its marker is cleared. */
    const val CLEAR_AFTER_MS = 10_000L

    data class Marker(
        val pid: Int,
        val gamePath: String,
        val gameName: String,
        val driverId: String,
        val driverName: String,
    )

    private const val FIELD_COUNT = 5

    /** The marker as text, one field per line; a tab or line break inside a field becomes a space. */
    fun encode(m: Marker): String =
        listOf(m.pid.toString(), m.gamePath, m.gameName, m.driverId, m.driverName).joinToString("\n") { clean(it) }

    /** The marker in [stored]; null for null, blank, too few lines or a pid that is not a number. */
    fun decode(stored: String?): Marker? {
        if (stored.isNullOrBlank()) return null
        val lines = stored.split("\n")
        if (lines.size < FIELD_COUNT) return null
        val pid = lines[0].trim().toIntOrNull() ?: return null
        return Marker(pid, lines[1], lines[2], lines[3], lines[4])
    }

    /** The marker's launch died: its process is gone, or it is another process than the one asking in the game process. */
    fun died(m: Marker, alivePids: Set<Int>): Boolean = m.pid !in alivePids

    /** The drivers recorded as crashed for [gamePath]. */
    fun crashed(stored: String?, gamePath: String): Set<String> {
        val path = clean(gamePath)
        return pairs(stored).filter { it.first == path }.map { it.second }.toSet()
    }

    /** [stored] with [driverId] recorded as crashed for [gamePath]; nothing is added when it is already there. */
    fun record(stored: String?, gamePath: String, driverId: String): String {
        val pair = clean(gamePath) to clean(driverId)
        val all = pairs(stored)
        return write(if (pair in all) all else all + pair)
    }

    /** [stored] without the records of [gamePath]. */
    fun clearGame(stored: String?, gamePath: String): String {
        val path = clean(gamePath)
        return write(pairs(stored).filter { it.first != path })
    }

    /** [stored] without the records of [driverId], for a driver that was removed. */
    fun dropDriver(stored: String?, driverId: String): String {
        val id = clean(driverId)
        return write(pairs(stored).filter { it.second != id })
    }

    /** What the user is told when the game is relaunched on the system driver. */
    fun notice(m: Marker): String =
        m.driverName + " crashed in " + m.gameName + ", so it now uses the system driver. Change this in Game Settings."

    private fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun pairs(stored: String?): List<Pair<String, String>> =
        stored?.lines().orEmpty().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2) parts[0] to parts[1] else null
        }

    private fun write(pairs: List<Pair<String, String>>): String =
        pairs.joinToString("\n") { it.first + "\t" + it.second }
}
