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
        assertEquals(null, reveal.timeoutInMs(nowMs = 1000))
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

    /** onCreate lays out with the defaults (Shown); the launch's values (Hidden) arrive before the first frame. */
    @Test fun the_band_starts_waiting_when_the_launch_values_say_hidden() {
        // Defaults: band shown, not waiting. The real values: Hidden, but not offered yet (no frame), so the band waits.
        assertEquals(BandGate.START_WAIT, bandGate(builtHidden = false, hiddenNow = false, bandShown = true, waiting = false, waitsNow = true))
        // The values again (an overlay sync): already waiting.
        assertEquals(BandGate.NONE, bandGate(builtHidden = false, hiddenNow = false, bandShown = true, waiting = true, waitsNow = true))
        // The first frame is tall: hidden now, the overlay is built.
        assertEquals(BandGate.RELAYOUT, bandGate(builtHidden = false, hiddenNow = true, bandShown = true, waiting = true, waitsNow = false))
        // Set back to Shown before the first frame: the band stops waiting.
        assertEquals(BandGate.STOP_WAIT, bandGate(builtHidden = false, hiddenNow = false, bandShown = true, waiting = true, waitsNow = false))
        // No band (a controller, landscape): nothing to wait.
        assertEquals(BandGate.NONE, bandGate(builtHidden = false, hiddenNow = false, bandShown = false, waiting = false, waitsNow = true))
    }
}
