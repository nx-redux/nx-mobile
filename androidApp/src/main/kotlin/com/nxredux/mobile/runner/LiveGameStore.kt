package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.AppProcess
import java.io.File

/**
 * The [LiveGame] record on disk, written by the game screen and read by the menu (another process). A record whose
 * process is gone (killed, crashed) is stale: [current] deletes it. [write] is atomic, so a reader never sees half a record.
 */
class LiveGameStore(private val file: File, private val isAlive: (Int) -> Boolean) {
    fun write(game: LiveGame) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(game.encode())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    /**
     * Removes the record [pid] wrote, not a newer game's from another pid. [launchId] narrows it further for one process
     * holding two screens in turn: a record a newer launch rewrote stays.
     */
    fun clear(pid: Int, launchId: Long? = null) {
        val game = read() ?: return
        if (game.pid == pid && (launchId == null || game.launchId == launchId)) file.delete()
    }

    /** The game alive now, or null; a stale record is deleted. */
    fun current(): LiveGame? {
        val game = read() ?: return null
        if (isAlive(game.pid)) return game
        file.delete()
        return null
    }

    private fun read(): LiveGame? {
        val text = try { file.readText() } catch (e: java.io.IOException) { return null }
        return LiveGame.decode(text)
    }

    companion object {
        fun of(context: Context): LiveGameStore =
            LiveGameStore(File(context.applicationContext.filesDir, "game-process/live"), ::processAlive)

        /** This very process, or a `:game` process by pid (a recycled pid is another app's process, not ours). */
        private fun processAlive(pid: Int): Boolean {
            if (pid == android.os.Process.myPid()) return true
            return try {
                File("/proc/$pid/cmdline").readBytes().toString(Charsets.UTF_8).substringBefore('\u0000').endsWith(AppProcess.GAME_SUFFIX)
            } catch (e: java.io.IOException) {
                false
            }
        }

        /** A game screen is alive (playing, suspended in its own task, or closing): the record spans claim to final release. */
        fun alive(context: Context): Boolean = of(context).current() != null
    }
}
