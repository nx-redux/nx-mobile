package com.nxredux.mobile.runner

/**
 * Notices a core hung inside its frame: a step that has not returned for [limitMillis] ([stalledMillis], the GL
 * thread's time in the current step; the limit is read at every poll, so it may change while a step runs). The GL
 * thread then holds the core's lock for good, so the achievements unload, the pause and the native destroy of a quit
 * would all wait on it and the app would stop responding. Polls on its own thread, since the main thread may be one of
 * those waiting. [onHung] runs once, on that thread, and ends the watch.
 */
internal class CoreWatchdog(
    private val stalledMillis: () -> Long,
    private val limitMillis: () -> Long = { LIMIT_MILLIS },
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
                if (ms >= limitMillis() && !stopped) {
                    onHung(ms)
                    return
                }
            }
        } catch (_: InterruptedException) {
            // stopped
        }
    }

    companion object {
        /**
         * The limit for a game on [core]: its own ([CoreDescriptor.hangLimitMillis]) until the first frame has rendered
         * ([gameReady]), since the long step it allows for is the game's start; [LIMIT_MILLIS] otherwise.
         */
        fun limitFor(core: CoreDescriptor?, gameReady: Boolean): Long =
            core?.hangLimitMillis?.takeUnless { gameReady } ?: LIMIT_MILLIS

        // Far above any real frame (a synchronous shader compile, a large state load on the GL thread). Below the minute
        // a broadcast-driven quit waits before Android calls the app not responding, so a quit blocked on a hung core is
        // ended by this before the system steps in; a longer core limit gives that up while it applies.
        const val LIMIT_MILLIS = 20_000L
        private const val POLL_MILLIS = 1_000L
    }
}
