package com.nxredux.mobile.runner

/**
 * When a pad laid out beside the picture may show: not before the picture's place is known (the first frame's geometry),
 * so the clusters never flash at their unplaced size, and after [timeoutMs] regardless, for a game that never draws.
 * Times are any monotonic clock's milliseconds.
 */
internal class PadReveal(val timeoutMs: Long = 1500) {
    private var startedMs: Long? = null
    private var pictureKnown = false

    /** A new layout of the pad: hidden again until [onPictureKnown] or the timeout. */
    fun onLayoutStart(nowMs: Long) {
        startedMs = nowMs
        pictureKnown = false
    }

    /** The picture was placed from the core's geometry: the pad may show. */
    fun onPictureKnown() {
        pictureKnown = true
    }

    /** Whether the pad shows at [nowMs]; true before any layout (nothing to wait for). */
    fun shouldShow(nowMs: Long): Boolean {
        val started = startedMs ?: return true
        return pictureKnown || nowMs - started >= timeoutMs
    }
}
