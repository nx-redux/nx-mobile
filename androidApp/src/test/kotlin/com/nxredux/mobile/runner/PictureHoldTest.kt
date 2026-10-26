package com.nxredux.mobile.runner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The game view draws black until a layout has placed the picture from the core's geometry. */
class PictureHoldTest {
    @Test fun held_while_a_layout_before_the_first_frame_has_only_a_guess() {
        assertTrue(PictureHold.held(frameKnown = false, gameReady = false))
    }

    @Test fun released_by_the_layout_that_knows_the_frame() {
        assertFalse(PictureHold.held(frameKnown = true, gameReady = false))
        assertFalse(PictureHold.held(frameKnown = true, gameReady = true))
    }

    @Test fun a_core_reporting_no_geometry_still_shows_once_running() {
        // 0x0 geometry after the first frame: placed as before (fit to the area) rather than black for good.
        assertFalse(PictureHold.held(frameKnown = false, gameReady = true))
    }
}
