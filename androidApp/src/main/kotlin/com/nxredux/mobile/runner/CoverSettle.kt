package com.nxredux.mobile.runner

/**
 * When the resume cover may fade: once [quietFrames] frames in a row have kept the picture's shape. A state load can
 * bring back the old size for a frame or two before the core's own shape returns (a PS2 widescreen patch's 16:9), and
 * a fade over that would show the jump. [maxFrames] caps the wait for a shape that keeps changing. Main thread.
 *
 * The shape is read on each frame rather than taken from the geometry-change signal: that one can arrive after the
 * frames it belongs with, and a core that re-announces an unchanged geometry raises it every frame.
 */
internal class CoverSettle(private val quietFrames: Int, private val maxFrames: Int) {
    private var quiet = 0
    private var frames = 0
    private var shape: List<Float>? = null

    /** A frame rendered with the picture's [shape] now; true once the cover may fade. */
    fun frame(shape: List<Float>): Boolean {
        frames++
        quiet = if (shape == this.shape) quiet + 1 else 1
        this.shape = shape
        return quiet >= quietFrames || frames >= maxFrames
    }
}
