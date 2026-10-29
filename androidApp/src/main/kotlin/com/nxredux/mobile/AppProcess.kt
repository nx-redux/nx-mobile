package com.nxredux.mobile

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Which of the app's processes this is. The game screen runs in its own `:game` process, whose Application must not
 * start work the menu process owns (moving data, clearing the update download and its notice).
 */
object AppProcess {
    const val GAME_SUFFIX = ":game"

    @Volatile private var game: Boolean? = null

    /** [processName] is the game process's (the package name plus [GAME_SUFFIX]). */
    fun isGameName(processName: String): Boolean = processName.endsWith(GAME_SUFFIX)

    /** This process is the game process. Cached: the name never changes while the process lives. */
    fun isGame(context: Context): Boolean {
        game?.let { return it }
        val result = isGameName(processName())
        game = result
        return result
    }

    private fun processName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return Application.getProcessName() ?: ""
        return try {
            // NUL-terminated: the name is followed by padding zeros.
            File("/proc/self/cmdline").readBytes().toString(Charsets.UTF_8).substringBefore('\u0000')
        } catch (e: java.io.IOException) {
            ""
        }
    }

    /** A process of this app (the menu's or the game's) by pid, or this very one; a recycled pid is another app's. */
    fun aliveCheck(context: Context): (Int) -> Boolean {
        val mine = android.os.Process.myPid()
        val pkg = context.packageName
        return { pid ->
            pid == mine || try {
                val proc = File("/proc/$pid/cmdline").readBytes().toString(Charsets.UTF_8).substringBefore('\u0000')
                proc == pkg || proc.startsWith("$pkg:")
            } catch (e: java.io.IOException) {
                false
            }
        }
    }
}
