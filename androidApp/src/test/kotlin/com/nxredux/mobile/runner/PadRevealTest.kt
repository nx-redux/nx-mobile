package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
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
        reveal.onCoreRunning(nowMs = 0)
        reveal.onLayoutStart(nowMs = 1000)
        assertTrue(reveal.shouldShow(nowMs = 2500))
    }

    @Test fun the_timeout_counts_only_once_the_core_runs() {
        // onCreate's layout, then a 4 s load (a big arcade ROM): nothing shows while it loads.
        val reveal = PadReveal(timeoutMs = 1500)
        reveal.onLayoutStart(nowMs = 1000)
        assertEquals(PadReveal.LOAD_CAP_MS, reveal.timeoutInMs(nowMs = 1000))
        assertFalse(reveal.shouldShow(nowMs = 4999))
        reveal.onCoreRunning(nowMs = 5000)
        assertEquals(1500L, reveal.timeoutInMs(nowMs = 5000))
        assertFalse(reveal.shouldShow(nowMs = 6499))
        assertTrue(reveal.shouldShow(nowMs = 6500))   // a game that never draws
        // A new surface later is not a new load; a later layout counts from its own start.
        reveal.onCoreRunning(nowMs = 9000)
        reveal.onLayoutStart(nowMs = 10000)
        assertEquals(1500L, reveal.timeoutInMs(nowMs = 10000))
        assertTrue(reveal.pending())
        reveal.onPictureKnown()
        assertFalse(reveal.pending())
    }

    @Test fun a_core_that_never_runs_still_shows_the_pads_after_the_load_cap() {
        // A hung copy, a netplay peer, a core stuck loading: MENU comes back for a touch-only player.
        val reveal = PadReveal(timeoutMs = 1500)
        assertEquals(null, reveal.timeoutInMs(nowMs = 0))
        reveal.onLayoutStart(nowMs = 1000)
        assertFalse(reveal.shouldShow(nowMs = 8999))
        assertEquals(1L, reveal.timeoutInMs(nowMs = 8999))
        assertTrue(reveal.shouldShow(nowMs = 9000))
    }
}
