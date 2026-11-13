package com.nxredux.mobile.runner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A player who leaves the in-game menu open in a room holds the others back, so after [capMs] of it [onCap] runs on
 * [scope]. The countdown belongs to one stretch of menu-in-room: [sync] starts it once and a repeated call keeps it
 * running, so only leaving the room or the menu gives the next stretch a full cap. [onTick] gets the seconds left,
 * once a second, for the menu to show; null when no countdown runs.
 */
internal class DsMenuCap(
    private val scope: CoroutineScope,
    private val capMs: Long = CAP_MS,
    private val onTick: (secondsLeft: Int?) -> Unit = {},
    private val onCap: () -> Unit,
) {
    private var job: Job? = null

    /** Seconds until [onCap], rounded up; null when no countdown runs. */
    var secondsLeft: Int? = null
        private set

    private fun tick(seconds: Int?) {
        if (seconds == secondsLeft) return
        secondsLeft = seconds
        onTick(seconds)
    }

    /** [idleInRoom]: the menu is open while in a room. True starts the countdown if none runs; false cancels it. */
    fun sync(idleInRoom: Boolean) {
        if (!idleInRoom) {
            job?.cancel()
            job = null
            tick(null)
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            var left = capMs
            while (left > 0) {
                tick(((left + 999) / 1000).toInt())
                val step = minOf(1000L, left)
                delay(step)
                left -= step
            }
            tick(null)
            onCap()
        }
    }

    companion object {
        const val CAP_MS = 60_000L
    }
}
