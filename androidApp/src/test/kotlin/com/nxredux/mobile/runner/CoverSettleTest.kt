package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Test

/** When the resume cover may fade: after a run of frames with the picture's shape unchanged, or at the cap. */
class CoverSettleTest {
    private val fourThree = listOf(640f, 448f, 1.33f)
    private val wide = listOf(796f, 448f, 1.78f)

    /** The frame number the cover may fade at, the shape on each frame given by [shapeAt]. */
    private fun framesUntilSettled(s: CoverSettle, shapeAt: (Int) -> List<Float>): Int {
        var n = 0
        while (true) {
            n++
            if (s.frame(shapeAt(n))) return n
        }
    }

    @Test fun settles_after_the_quiet_frames() {
        assertEquals(6, framesUntilSettled(CoverSettle(quietFrames = 6, maxFrames = 30)) { fourThree })
    }

    @Test fun a_shape_change_starts_the_count_again() {
        // A state load's old 4:3 size, then the widescreen patch's 16:9 from the third frame.
        assertEquals(8, framesUntilSettled(CoverSettle(quietFrames = 6, maxFrames = 30)) { if (it < 3) fourThree else wide })
    }

    @Test fun the_same_shape_reported_again_is_no_change() {
        // A core that re-announces an unchanged geometry every frame must not hold the cover to the cap.
        assertEquals(6, framesUntilSettled(CoverSettle(quietFrames = 6, maxFrames = 30)) { listOf(640f, 448f, 1.33f) })
    }

    @Test fun a_shape_that_keeps_changing_fades_at_the_cap() {
        assertEquals(30, framesUntilSettled(CoverSettle(quietFrames = 6, maxFrames = 30)) { if (it % 2 == 0) fourThree else wide })
    }
}
