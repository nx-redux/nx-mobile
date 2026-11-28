package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the resume cover may fade: after a run of frames with the picture's shape unchanged, or at the cap. */
class CoverSettleTest {
    private fun framesUntilSettled(s: CoverSettle, onFrame: (Int) -> Unit = {}): Int {
        var n = 0
        while (true) {
            n++
            onFrame(n)
            if (s.frame()) return n
        }
    }

    @Test fun settles_after_the_quiet_frames() {
        assertEquals(6, framesUntilSettled(CoverSettle(quietFrames = 6, maxFrames = 30)))
    }

    @Test fun a_shape_change_starts_the_count_again() {
        // A state load's old 4:3 size, then the widescreen patch's 16:9 two frames on.
        val s = CoverSettle(quietFrames = 6, maxFrames = 30)
        assertEquals(8, framesUntilSettled(s) { if (it == 3) s.geometryChanged() })
    }

    @Test fun a_shape_that_keeps_changing_fades_at_the_cap() {
        val s = CoverSettle(quietFrames = 6, maxFrames = 30)
        assertEquals(30, framesUntilSettled(s) { s.geometryChanged() })
    }

    @Test fun not_settled_before_any_frame() {
        val s = CoverSettle(quietFrames = 1, maxFrames = 30)
        s.geometryChanged()
        assertFalse(s.settled)
        assertTrue(s.frame())
        assertTrue(s.settled)
    }
}
