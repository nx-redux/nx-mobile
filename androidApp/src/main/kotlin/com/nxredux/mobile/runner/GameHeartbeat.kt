package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.AppProcess
import java.io.File

/**
 * The ending game process's sign of life for the menu ([GameProcessControl]): from the moment a quit or END_GAME begins
 * until the process is gone, a small thread rewrites a file beside the live-game record every [INTERVAL_MS]. A quit's
 * saves can take many seconds on a slow device; the menu kills only a process whose beat has stopped.
 */
object GameHeartbeat {
    const val INTERVAL_MS = 500L

    @Volatile private var started = false

    private fun file(context: Context) = File(context.filesDir, "game-process/heartbeat")

    /** Starts beating (once; the beat lasts until the process ends). Only in the game process. */
    fun start(context: Context) {
        if (!AppProcess.isGame()) return
        synchronized(this) { if (started) return; started = true }
        val file = file(context.applicationContext)
        val pid = android.os.Process.myPid()
        Thread({
            var n = 0L
            while (true) {
                runCatching { file.parentFile?.mkdirs(); file.writeText("$pid ${n++}") }
                Thread.sleep(INTERVAL_MS)
            }
        }, "game-heartbeat").apply { isDaemon = true }.start()
    }

    /** The latest beat as the menu reads it; null when there is none. */
    fun reader(context: Context): () -> String? {
        val file = file(context.applicationContext)
        return { runCatching { file.readText() }.getOrNull() }
    }
}
