package com.nxredux.mobile.runner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The link's presence as the native side reports it: 0 out, 1 seeking a room, 2 in a room. */
internal enum class DsPresence {
    OUT, SEEKING, ROOM;

    companion object {
        fun of(native: Int): DsPresence = when (native) {
            1 -> SEEKING
            2 -> ROOM
            else -> OUT
        }
    }
}

/**
 * DS local wireless: whether this phone is out, seeking a room or in one comes from the link's network thread, which
 * tells nobody, so it is read every [TICK_MS] on [scope] (the screen's main-thread scope) and [onChange] runs on each
 * change. The room's comings and goings are seconds apart (searches, a 3 s silence), so a half-second read is soon enough.
 */
internal class DsRoomWatcher(
    private val scope: CoroutineScope,
    private val read: () -> DsPresence,
    private val onChange: (DsPresence) -> Unit,
) {
    /** The last state [onChange] was told of; out until the first read says otherwise. */
    var current = DsPresence.OUT
        private set
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                check()
                delay(TICK_MS)
            }
        }
    }

    /** Reads the state now (also right after this phone left on its own, so the change lands at once). */
    fun check() {
        val now = read()
        if (now == current) return
        current = now
        onChange(now)
    }

    /** No more reads or calls; the screen is going. */
    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val TICK_MS = 500L
    }
}
