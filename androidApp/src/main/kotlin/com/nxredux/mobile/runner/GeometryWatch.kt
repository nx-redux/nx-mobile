package com.nxredux.mobile.runner

/**
 * Waits for the core's frame size to change after a layout switch, a frame at a time. Azahar reports the new size
 * (SET_GEOMETRY) some frames after its layout option changes; the caller feeds [tick] the geometry each frame and lays
 * out again on [Tick.CHANGED]. Bounded by its frame limit, ended by the first change, by [seen] (another path already laid
 * out from a changed geometry, so it is not done twice) or by [cancel]; [start] again replaces a watch under way.
 * Main thread only.
 */
internal class GeometryWatch(private val defaultFrames: Int = 90) {

    enum class Tick { IDLE, WAITING, CHANGED, GAVE_UP }

    private var baseline: FloatArray? = null
    private var maxFrames = defaultFrames

    /** True while a watch is under way. */
    val active: Boolean get() = baseline != null

    /** Frames [tick] has seen since [start] (or until the watch ended). */
    var framesWaited = 0
        private set

    /** The geometry the watch compares against: the frame before the switch; null when none is under way. */
    val before: FloatArray? get() = baseline

    /** Begins watching from [baseline], the geometry before the switch, for up to [frames] frames. */
    fun start(baseline: FloatArray, frames: Int = defaultFrames) {
        this.baseline = baseline.copyOf()
        maxFrames = frames
        framesWaited = 0
    }

    /** One frame with [current]: the first change from the baseline ends the watch; so does reaching [maxFrames]. */
    fun tick(current: FloatArray): Tick {
        val base = baseline ?: return Tick.IDLE
        framesWaited++
        if (!base.contentEquals(current)) { baseline = null; return Tick.CHANGED }
        if (framesWaited >= maxFrames) { baseline = null; return Tick.GAVE_UP }
        return Tick.WAITING
    }

    /**
     * Another path laid out from [current]. If that differs from the baseline the change is handled: the watch ends and
     * this returns true.
     */
    fun seen(current: FloatArray): Boolean {
        val base = baseline ?: return false
        if (base.contentEquals(current)) return false
        baseline = null
        return true
    }

    /** Stops watching (a newer switch restarts instead; the game is closing). */
    fun cancel() { baseline = null }
}
