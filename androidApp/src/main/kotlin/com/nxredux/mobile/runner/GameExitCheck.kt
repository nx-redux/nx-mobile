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

    @Suppress("UNUSED_PARAMETER")
    fun classify(reason: Int, description: String?): GameExit = when (reason) {
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR -> GameExit.CRASH
        else -> GameExit.NONE   // low memory, exit-self, the self-kill signal of a normal quit, the user's own stop
    }

    /**
     * The newest `:game` ending since the last check. [hangShown]: the menu already told of a hung core for this
     * death, so it wins and the exit reason is not looked at. The first run only records the time (older deaths
     * from before this existed stay unmentioned). Does I/O: not on the main thread.
     */
    fun check(context: Context, hangShown: Boolean = false): GameExit {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return GameExit.NONE
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val games = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            .filter { AppProcess.isGameName(it.processName) }
        val newest = games.maxOfOrNull { it.timestamp } ?: return if (hangShown) GameExit.HANG else GameExit.NONE
        val prefs = Prefs.open(context, PREFS)
        val seen = prefs.getLong(KEY_SEEN, -1L)
        if (newest > seen) prefs.edit().putLong(KEY_SEEN, newest).apply()
        if (hangShown) return GameExit.HANG
        if (seen < 0) return GameExit.NONE
        val latest = games.filter { it.timestamp > seen }.maxByOrNull { it.timestamp } ?: return GameExit.NONE
        return classify(latest.reason, latest.description)
    }
}
