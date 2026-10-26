package com.nxredux.mobile.runner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picture held across a 3DS fold until the new frame, kept through a pause rather than shown on the old frame. */
class GeometryHoldTest {
    @Test fun gives_up_running_shows_the_picture() {
        val h = GeometryHold()
        h.start()
        assertTrue(h.onGaveUp(paused = false))
        assertFalse(h.held)
    }

    @Test fun gives_up_paused_keeps_it_held_until_the_core_runs() {
        val h = GeometryHold()
        h.start()
        assertFalse(h.onGaveUp(paused = true))
        assertTrue(h.held)
        assertTrue(h.awaitsRun)
        assertTrue(h.onCoreRunning())   // watched again, still held
        assertTrue(h.held)
        assertFalse(h.awaitsRun)
        assertFalse(h.onCoreRunning())   // once
        assertTrue(h.end())
        assertFalse(h.held)
    }

    @Test fun new_geometry_while_paused_ends_it() {
        val h = GeometryHold()
        h.start()
        h.onGaveUp(paused = true)
        assertTrue(h.end())
        assertFalse(h.awaitsRun)
        assertFalse(h.onCoreRunning())
    }

    @Test fun nothing_held_does_nothing() {
        val h = GeometryHold()
        assertFalse(h.onGaveUp(paused = false))
        assertFalse(h.onGaveUp(paused = true))
        assertFalse(h.awaitsRun)
        assertFalse(h.onCoreRunning())
        assertFalse(h.end())
    }
}
