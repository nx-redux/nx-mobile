package com.nxredux.mobile.runner

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.nxredux.mobile.AppProcess
import com.nxredux.mobile.settings.Prefs

/** How the game process last ended, as far as the menu should say: [CRASH] and [HANG] get a notice, [NONE] stays silent. */
enum class GameExit { NONE, CRASH, HANG }

/**
 * Tells the menu when the `:game` process died on its own. A normal quit (the process kills itself, reported as
 * a signal or exit-self), the user's swipe and memory reclaim are silent; a crash, a native crash or an ANR is not.
 */
object GameExitCheck {
    private const val PREFS = "game-exit"
    private const val KEY_SEEN = "lastSeen"
    private const val CRASH_NOTICE = "The game stopped unexpectedly. It will continue from its last auto-save."

    /** The notice for [exit], null when there is nothing to say (a hang is worded by [CoreHangNotice]). */
    fun notice(exit: GameExit): String? = if (exit == GameExit.CRASH) CRASH_NOTICE else null

    fun classify(reason: Int): GameExit = when (reason) {
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR -> GameExit.CRASH
        else -> GameExit.NONE   // low memory, exit-self, the self-kill signal of a normal quit, the user's own stop
    }

    /**
     * What to say and the new last-seen mark, from the `:game` endings ([entries]: time and class) and the mark
     * kept from the last check ([lastSeen], null on the first run). The first run only sets a baseline (deaths from
     * before this existed stay unmentioned, but the next one counts, history or not). A hang already shown wins.
     * Any new crash counts, even if a normal quit followed it before the menu looked.
     */
    internal fun decide(entries: List<Pair<Long, GameExit>>, lastSeen: Long?, hangShown: Boolean, now: Long): Pair<GameExit, Long> {
        val newest = entries.maxOfOrNull { it.first }
        if (lastSeen == null) return GameExit.NONE to maxOf(newest ?: now, now)
        val mark = maxOf(lastSeen, newest ?: lastSeen)
        if (hangShown) return GameExit.HANG to mark
        val crashed = entries.any { it.first > lastSeen && it.second == GameExit.CRASH }
        return (if (crashed) GameExit.CRASH else GameExit.NONE) to mark
    }

    /**
     * Whether the `:game` process died on its own since the last check. [hangShown]: the menu already told of a hung
     * core for this death, so it wins. Does I/O: not on the main thread.
     */
    fun check(context: Context, hangShown: Boolean = false): GameExit {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return GameExit.NONE
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val entries = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            .filter { AppProcess.isGameName(it.processName) }
            .map { it.timestamp to classify(it.reason) }
        val prefs = Prefs.open(context, PREFS)
        val seen = if (prefs.contains(KEY_SEEN)) prefs.getLong(KEY_SEEN, 0L) else null
        val (exit, mark) = decide(entries, seen, hangShown, System.currentTimeMillis())
        if (mark != seen) prefs.edit().putLong(KEY_SEEN, mark).apply()
        return exit
    }
}
