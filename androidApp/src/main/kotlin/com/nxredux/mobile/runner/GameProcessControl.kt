package com.nxredux.mobile.runner

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.swordfish.libretrodroid.NxLog

/** The broadcast that asks the game process to quit its game (auto state, saves) and end; [EndGameReceiver] gets it. */
fun endGameAction(context: Context): String = context.packageName + ".END_GAME"

/**
 * The menu's side of the game process, before every launch: at most one game runs, and a new one never starts beside
 * (or inside) the old one's process. The game still paused in the background is brought back when it is the very one
 * picked ([canBringBack]); any other is asked to quit ([sendEnd]: it writes its auto state and saves, then its process
 * ends), and killed when it has not gone after [END_WAIT_MS]. Blocking: never on the main thread.
 */
class GameProcessControl(
    private val store: LiveGameStore,
    private val sendEnd: () -> Unit,
    private val killPid: (Int) -> Unit,
    private val bringToFront: () -> Boolean,
    private val sleep: (Long) -> Unit,
) {
    enum class Decision { START, BROUGHT_BACK }

    fun prepare(session: GameSession): Decision {
        val live = store.current() ?: return Decision.START
        if (live.canBringBack(session) && bringToFront()) return Decision.BROUGHT_BACK
        NxLog.i(TAG, "ending the game process ${live.pid} before the next launch")
        sendEnd()
        if (awaitGone(live.pid, END_WAIT_MS)) return Decision.START
        NxLog.w(TAG, "the game process ${live.pid} has not ended after $END_WAIT_MS ms: killing it")
        killPid(live.pid)
        awaitGone(live.pid, KILL_WAIT_MS)   // a kill is asynchronous: its memory should be back before the next core loads
        return Decision.START
    }

    /** True once [pid]'s record is gone (cleared at its final release, or stale with its process). */
    private fun awaitGone(pid: Int, limitMs: Long): Boolean {
        var waited = 0L
        while (true) {
            if (store.current()?.pid != pid) return true
            if (waited >= limitMs) return false
            sleep(POLL_MS)
            waited += POLL_MS
        }
    }

    companion object {
        private const val TAG = "NXAPP"
        private const val POLL_MS = 100L
        /** A quit writes the auto state and the battery save, then tears the core down: seconds for the heaviest. */
        const val END_WAIT_MS = 10_000L
        private const val KILL_WAIT_MS = 1_000L

        fun of(context: Context): GameProcessControl {
            val app = context.applicationContext
            return GameProcessControl(
                LiveGameStore.of(app),
                sendEnd = { app.sendBroadcast(Intent(endGameAction(app)).setPackage(app.packageName)) },
                killPid = { android.os.Process.killProcess(it) },
                bringToFront = { bringGameToFront(app) },
                sleep = { Thread.sleep(it) },
            )
        }

        /**
         * The game's own task (launcher mode) to the front, its process and screen as they were. False when there is
         * none: outside launcher mode the game shares the menu's task, which the menu itself is on top of now.
         */
        private fun bringGameToFront(context: Context): Boolean {
            val game = GameActivity::class.java.name
            val tasks = runCatching { context.getSystemService(ActivityManager::class.java).appTasks }.getOrNull() ?: return false
            val task = tasks.firstOrNull { t ->
                runCatching {
                    val info = t.taskInfo ?: return@runCatching false
                    val base = info.baseActivity?.className ?: info.baseIntent.component?.className
                    base == game || info.topActivity?.className == game
                }.getOrDefault(false)
            } ?: return false
            return runCatching { task.moveToFront() }.onFailure { NxLog.w(TAG, "could not bring the game back", it) }.isSuccess
        }
    }
}
