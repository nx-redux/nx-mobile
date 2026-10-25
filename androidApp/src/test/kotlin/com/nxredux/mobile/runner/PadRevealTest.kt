package com.nxredux.mobile.runner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The landscape pad stays hidden after a layout until the picture is placed, or a game that never draws times out. */
class PadRevealTest {
    @Test fun hidden_before_either() {
        val reveal = PadReveal(timeoutMs = 1500)
        reveal.onLayoutStart(nowMs = 1000)
        assertFalse(reveal.shouldShow(nowMs = 1000))
        assertFalse(reveal.shouldShow(nowMs = 2499))
    }

    @Test fun pad_shows_once_picture_known() {
        val reveal = PadReveal(timeoutMs = 1500)
        reveal.onLayoutStart(nowMs = 1000)
        reveal.onPictureKnown()
        assertTrue(reveal.shouldShow(nowMs = 1001))
        // A new layout (a rotation) hides it again until its picture is placed.
        reveal.onLayoutStart(nowMs = 5000)
        assertFalse(reveal.shouldShow(nowMs = 5001))
    }

    @Test fun pad_shows_after_timeout_without_geometry() {
        val reveal = PadReveal(timeoutMs = 1500)
        reveal.onLayoutStart(nowMs = 1000)
        assertTrue(reveal.shouldShow(nowMs = 2500))
    }
}
