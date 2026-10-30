package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.NxPaths

/**
 * The game the game process holds, as the menu can read it: [pid] is the process, [launchId] its launch's record
 * (the Android runner's LaunchRecords). Lets the menu bring a running game back instead of starting a second one.
 */
data class LiveGame(
    val pid: Int,
    val launchId: Long,
    val gamePath: String?,
    val coreId: String,
    val tag: String,
    val resumeSlot: Int?,
    val netplay: Boolean,
) {
    /** One line, tab-separated, fields escaped; [decode] reads it back. */
    fun encode(): String = listOf(
        VERSION, pid.toString(), launchId.toString(), gamePath?.let { "1" + it.escape() } ?: "0", coreId.escape(), tag.escape(),
        resumeSlot?.toString() ?: "", if (netplay) "1" else "0",
    ).joinToString("\t")

    companion object {
        private const val VERSION = "1"

        /** Null for anything [encode] did not write (a torn or foreign file); never throws. */
        fun decode(text: String): LiveGame? {
            val f = text.trimEnd('\n', '\r').split('\t')
            if (f.size != 8 || f[0] != VERSION) return null
            val pid = f[1].toIntOrNull() ?: return null
            val launchId = f[2].toLongOrNull() ?: return null
            val path = when {
                f[3] == "0" -> null
                f[3].startsWith("1") -> f[3].substring(1).unescape() ?: return null
                else -> return null
            }
            val slot = if (f[6].isEmpty()) null else f[6].toIntOrNull() ?: return null
            val netplay = when (f[7]) { "1" -> true; "0" -> false; else -> return null }
            return LiveGame(pid, launchId, path, f[4].unescape() ?: return null, f[5].unescape() ?: return null, slot, netplay)
        }

        private fun String.escape() = replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

        private fun String.unescape(): String? {
            val sb = StringBuilder()
            var i = 0
            while (i < length) {
                val c = this[i++]
                if (c != '\\') { sb.append(c); continue }
                if (i >= length) return null
                sb.append(when (this[i++]) { '\\' -> '\\'; 't' -> '\t'; 'n' -> '\n'; 'r' -> '\r'; else -> return null })
            }
            return sb.toString()
        }
    }
}

/**
 * [session] is the very game this process already runs, started fresh or from its auto save (a Continue): the menu may
 * bring that screen back. A slot pick, another game or core, or netplay (either side) starts a new process instead.
 */
fun LiveGame.canBringBack(session: GameSession): Boolean =
    gamePath != null && gamePath == session.gamePath && coreId == session.core.id &&
        session.netplay == null && !netplay &&
        (session.resumeSlot == null || session.resumeSlot == NxPaths.AUTO_SLOT)
