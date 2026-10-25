package com.nxredux.mobile.runner

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class GeometryWatchTest {
    private val old = floatArrayOf(400f, 480f, 0.83f, 0f)
    private val new = floatArrayOf(800f, 480f, 1.67f, 0f)

    @Test fun idleBeforeStart() {
        val w = GeometryWatch()
        assertFalse(w.active)
        assertEquals(GeometryWatch.Tick.IDLE, w.tick(new))
    }

    @Test fun waitsWhileUnchangedThenFiresOnceOnChange() {
        val w = GeometryWatch()
        w.start(old)
        assertEquals(GeometryWatch.Tick.WAITING, w.tick(old))
        assertEquals(GeometryWatch.Tick.WAITING, w.tick(old.copyOf()))
        assertEquals(GeometryWatch.Tick.CHANGED, w.tick(new))
        assertEquals(3, w.framesWaited)
        assertFalse(w.active)
        assertEquals(GeometryWatch.Tick.IDLE, w.tick(new))   // stopped at the first change
    }

    @Test fun givesUpAfterMaxFrames() {
        val w = GeometryWatch(3)
        w.start(old)
        assertEquals(GeometryWatch.Tick.WAITING, w.tick(old))
        assertEquals(GeometryWatch.Tick.WAITING, w.tick(old))
        assertEquals(GeometryWatch.Tick.GAVE_UP, w.tick(old))
        assertFalse(w.active)
    }

    @Test fun cancelStops() {
        val w = GeometryWatch()
        w.start(old)
        w.cancel()
        assertEquals(GeometryWatch.Tick.IDLE, w.tick(new))
        assertNull(w.before)
    }

    @Test fun newerStartReplacesTheBaselineAndCount() {
        val w = GeometryWatch(3)
        w.start(old)
        w.tick(old)
        w.tick(old)
        w.start(new)
        assertEquals(0, w.framesWaited)
        assertEquals(GeometryWatch.Tick.WAITING, w.tick(new))   // compared with the new baseline, not the old one
        assertEquals(GeometryWatch.Tick.CHANGED, w.tick(old))
    }

    @Test fun seenByAnotherPathEndsTheWatchSoItDoesNotLayOutTwice() {
        val w = GeometryWatch()
        w.start(old)
        assertFalse(w.seen(old))   // an unrelated emission with the same geometry changes nothing
        assertTrue(w.active)
        assertTrue(w.seen(new))
        assertFalse(w.active)
        assertEquals(GeometryWatch.Tick.IDLE, w.tick(new))
        assertFalse(w.seen(new))   // nothing under way
    }

    @Test fun baselineIsCopied() {
        val w = GeometryWatch()
        val base = old.copyOf()
        w.start(base)
        base[0] = 999f
        assertEquals(GeometryWatch.Tick.WAITING, w.tick(old))
    }
}
