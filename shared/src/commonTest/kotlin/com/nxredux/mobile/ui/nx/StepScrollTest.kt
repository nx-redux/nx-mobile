package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A step scroll's running target ([stepScrollTarget]): presses add up, and the target stays within the scroll; the
 * Scroll hint ([scrollHintTarget]) steps down from the same target, and back to the top at the end.
 */
class StepScrollTest {
    @Test fun presses_add_up_from_the_running_target() {
        var t = stepScrollTarget(0, 1, 72f, 1000)
        t = stepScrollTarget(t, 1, 72f, 1000)
        t = stepScrollTarget(t, 1, 72f, 1000)
        assertEquals(216, t)
        assertEquals(144, stepScrollTarget(t, -1, 72f, 1000))
    }

    @Test fun the_target_stays_within_the_scroll() {
        assertEquals(1000, stepScrollTarget(980, 1, 72f, 1000))
        assertEquals(0, stepScrollTarget(30, -1, 72f, 1000))
        assertEquals(0, stepScrollTarget(0, 1, 72f, 0))
    }

    @Test fun the_scroll_hint_steps_from_the_running_target() {
        // a press already heading for 144: the hint goes on from there, not from where the animation is
        assertEquals(216, scrollHintTarget(144, 72f, 1000))
        assertEquals(1000, scrollHintTarget(980, 72f, 1000))
    }

    @Test fun the_scroll_hint_wraps_to_the_top_at_the_end() {
        assertEquals(0, scrollHintTarget(1000, 72f, 1000))
        assertEquals(0, scrollHintTarget(0, 72f, 0))
    }
}
