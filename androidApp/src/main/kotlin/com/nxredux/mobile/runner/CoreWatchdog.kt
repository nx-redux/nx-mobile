package com.nxredux.mobile.runner

/**
 * Notices a core hung inside its frame: a step that has not returned for [limitMillis] ([stalledMillis], the GL
 * thread's time in the current step). The GL thread then holds the core's lock for good, so the achievements unload, the
 * pause and the native destroy of a quit would all wait on it and the app would stop responding. Polls on its own
 * thread, since the main thread may be one of those waiting. [onHung] runs once, on that thread, and ends the watch.
 */
internal class CoreWatchdog(
    private val stalledMillis: () -> Long,
    private val limitMillis: Long = LIMIT_MILLIS,
    private val pollMillis: Long = POLL_MILLIS,
    private val onHung: (stalledMillis: Long) -> Unit,
) {
    @Volatile private var stopped = false
    private val thread = Thread(::watch, "nx-core-watchdog").apply { isDaemon = true }

    fun start() = thread.start()

    /** No report after this returns (a report already running finishes). Any thread. */
    fun stop() {
        stopped = true
        thread.interrupt()
    }

    private fun watch() {
        try {
            while (!stopped) {
                Thread.sleep(pollMillis)
                val ms = stalledMillis()
                if (ms >= limitMillis && !stopped) {
                    onHung(ms)
                    return
                }
            }
        } catch (_: InterruptedException) {
            // stopped
        }
    }

    companion object {
        // Far above any real frame (a synchronous shader compile, a large state load on the GL thread), and well below
        // the minute a broadcast-driven quit waits before Android calls the app not responding.
        const val LIMIT_MILLIS = 20_000L
        private const val POLL_MILLIS = 1_000L
    }
}
