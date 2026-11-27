package com.swordfish.libretrodroid

/**
 * When the core stops for its activity: at the pause, or with [runWhilePaused] only at the stop, so a window over the
 * still visible game (a system dialog) leaves it running. Each call says whether to stop or resume the core now.
 */
internal class LifecyclePause {
    @Volatile var runWhilePaused = false
    private var running = false

    fun onResume(): Boolean {
        if (running) return false
        running = true
        return true
    }

    fun onPause(): Boolean = !runWhilePaused && onStop()

    fun onStop(): Boolean {
        if (!running) return false
        running = false
        return true
    }
}
