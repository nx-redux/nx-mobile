package com.nxredux.mobile.runner

/**
 * The picture held black across a 3DS fold or unfold ([FrontendRenderer.watchGeometry]) until the core's new frame size
 * is laid out, so the old frame never shows cut by the new screens. A watch that gives up while the core is paused (the
 * menu open, another app focused) made no new frame to wait for: the hold stays until the core runs again (watched
 * again then) or its geometry arrives. Main thread only.
 */
internal class GeometryHold {
    /** The picture is held now. */
    var held = false
        private set

    /** Held past a watch that gave up while paused: waiting for the core to run again ([onCoreRunning]). */
    var awaitsRun = false
        private set

    /** A fold's watch began: held. */
    fun start() {
        held = true
        awaitsRun = false
    }

    /** The watch gave up with the core [paused] or not: true when the picture is to be shown now. */
    fun onGaveUp(paused: Boolean): Boolean {
        if (!held) return false
        if (paused) {
            awaitsRun = true
            return false
        }
        return end()
    }

    /** The core runs again: true when a hold waited for it, so the frame size is watched again (still held). */
    fun onCoreRunning(): Boolean {
        if (!awaitsRun) return false
        awaitsRun = false
        return true
    }

    /** The new geometry is laid out, or the watch is cancelled: true when a hold ends, so the picture is shown. */
    fun end(): Boolean {
        if (!held) return false
        held = false
        awaitsRun = false
        return true
    }
}
