package com.nxredux.mobile.runner

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.nxredux.mobile.AppProcess
import com.swordfish.libretrodroid.NxLog

/** The broadcast that asks the game process to quit its game (auto state, saves) and end; [EndGameReceiver] gets it. */
fun endGameAction(context: Context): String = context.packageName + ".END_GAME"

/**
 * The menu's side of the game process, before every launch: at most one game runs, and a new one never starts beside
 * (or inside) the old one's process. The game still paused in the background is brought back when it is the very one
 * picked ([canBringBack]); any other is asked to quit ([sendEnd]: it writes its auto state and saves, then its process
 * ends). A slow quit (a PS2 or GameCube memory card on a weak device) is never cut short while it shows life: the ending
 * game beats its [heartbeat] ([GameHeartbeat]), and it is killed only once that has not moved for [STALL_MS], or after
 * [CAP_MS] in all. Gone means Android has also handled its death ([gameProcessListed]), so the next launch never lands in
 * the dying process. Blocking: never on the main thread.
 */
class GameProcessControl(
    private val store: LiveGameStore,
    private val sendEnd: () -> Unit,
    private val killPid: (Int) -> Unit,
    private val bringToFront: () -> Boolean,
    private val sleep: (Long) -> Unit,
    private val heartbeat: () -> String?,
    private val gameProcessListed: () -> Boolean,
) {
    enum class Decision { START, BROUGHT_BACK }

    fun prepare(session: GameSession): Decision {
        val live = store.current() ?: return Decision.START
        if (live.canBringBack(session) && bringToFront()) return Decision.BROUGHT_BACK
        NxLog.i(TAG, "ending the game process ${live.pid} before the next launch")
        sendEnd()
        if (!awaitEnd(live.pid)) {
            killPid(live.pid)
            awaitGone(live.pid, KILL_WAIT_MS)   // a kill is asynchronous: its memory should be back before the next core loads
        }
        awaitUnlisted()
        return Decision.START
    }

    /** True once [pid] is gone; false when its heartbeat stalled for [STALL_MS] or it has had [CAP_MS]. */
    private fun awaitEnd(pid: Int): Boolean {
        var last = heartbeat()   // an earlier game's beat, or this one's from before the ask: only a change counts
        var waited = 0L
        var lastLife = 0L
        while (true) {
            if (store.current()?.pid != pid) return true
            if (waited - lastLife >= STALL_MS || waited >= CAP_MS) {
                NxLog.w(TAG, "the game process $pid has not ended after $waited ms (last sign of life at $lastLife ms): killing it")
                return false
            }
            sleep(POLL_MS)
            waited += POLL_MS
            val beat = heartbeat()
            if (beat != last) { last = beat; lastLife = waited }
        }
    }

    /** The pid can look gone (a zombie's empty cmdline) before Android drops its process record: wait for that, bounded. */
    private fun awaitUnlisted() {
        var waited = 0L
        while (waited < UNLISTED_WAIT_MS && gameProcessListed()) { sleep(POLL_MS); waited += POLL_MS }
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
        /** No heartbeat for this long: the ending game is stuck (it beats every [GameHeartbeat.INTERVAL_MS]). */
        const val STALL_MS = 5_000L
        /** However alive it looks, a quit gets no longer than this. */
        const val CAP_MS = 60_000L
        private const val KILL_WAIT_MS = 1_000L
        private const val UNLISTED_WAIT_MS = 2_000L

        fun of(context: Context): GameProcessControl {
            val app = context.applicationContext
            return GameProcessControl(
                LiveGameStore.of(app),
                // Foreground: a background broadcast can wait in the queue for many seconds, all of it counted as a stall.
                sendEnd = { app.sendBroadcast(Intent(endGameAction(app)).setPackage(app.packageName).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)) },
                killPid = { android.os.Process.killProcess(it) },
                bringToFront = { bringGameToFront(app) },
                sleep = { Thread.sleep(it) },
                heartbeat = GameHeartbeat.reader(app),
                gameProcessListed = { gameProcessListed(app) },
            )
        }

        private fun gameProcessListed(context: Context): Boolean {
            val name = context.packageName + AppProcess.GAME_SUFFIX
            val procs = runCatching { context.getSystemService(ActivityManager::class.java).runningAppProcesses }.getOrNull() ?: return false
            return procs.any { it.processName == name }
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
