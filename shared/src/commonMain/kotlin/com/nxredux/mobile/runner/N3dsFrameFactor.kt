package com.nxredux.mobile.runner

/**
 * The internal resolution factor the 3DS frame on screen was rendered at ([N3ds3dResolution.currentScale] measures with
 * it). A factor sent to Azahar reaches its frame a frame or more later: until the core's frame size changes after the
 * send, the frame shown still carries the factor before. Reading the new one early would measure the old frame at the new
 * factor (Auto and Quality would then send another value and swing back). Starts at the factor the game launched with.
 */
class N3dsFrameFactor(initial: Int) {
    private var shown = initial
    private var pending: Int? = null
    private var sentAt: Pair<Float, Float>? = null   // the frame size when [pending] was sent

    /**
     * [factor] went to the core while its frame was [frameW] x [frameH] (that frame is read first: an earlier send may
     * have arrived unseen). A second send before the first arrived keeps the frame of the first (it has not changed
     * since); sending the shown factor back leaves nothing to wait for.
     */
    fun sent(factor: Int, frameW: Float, frameH: Float) {
        of(frameW, frameH)
        if (factor == shown) {
            pending = null
            return
        }
        if (pending == null) sentAt = frameW to frameH
        pending = factor
    }

    /** The factor a frame [frameW] x [frameH] carries: the one sent last once the frame size has changed since its send. */
    fun of(frameW: Float, frameH: Float): Int {
        val p = pending
        if (p != null && sentAt != (frameW to frameH)) {
            shown = p
            pending = null
        }
        return shown
    }
}
