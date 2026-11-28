package com.nxredux.mobile.runner

/**
 * When the resume cover may fade: once [quietFrames] frames in a row have kept the picture's shape. A state load can
 * bring back the old size for a frame or two before the core's own shape returns (a PS2 widescreen patch's 16:9), and
 * a fade over that would show the jump. [maxFrames] caps the wait for a shape that keeps changing. Main thread.
 */
internal class CoverSettle(private val quietFrames: Int, private val maxFrames: Int) {
    private var quiet = 0
    private var frames = 0

    var settled = false
        private set

    fun geometryChanged() {
        quiet = 0
    }

    /** A frame rendered; true once the cover may fade. */
    fun frame(): Boolean {
        frames++
        quiet++
        if (quiet >= quietFrames || frames >= maxFrames) settled = true
        return settled
    }
}
