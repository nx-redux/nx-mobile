package com.swordfish.libretrodroid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecyclePauseTest {
    @Test fun pausesAtThePauseAndResumesOnce() {
        val gate = LifecyclePause()
        assertTrue(gate.onResume())
        assertFalse(gate.onResume())
        assertTrue(gate.onPause())
        assertFalse(gate.onStop())
        assertTrue(gate.onResume())
    }

    @Test fun runningWhilePausedStopsOnlyAtTheStop() {
        val gate = LifecyclePause().apply { runWhilePaused = true }
        gate.onResume()
        assertFalse(gate.onPause())
        assertFalse(gate.onResume())   // back from the window over it: still running
        assertFalse(gate.onPause())
        assertTrue(gate.onStop())
        assertTrue(gate.onResume())
    }

    @Test fun nothingToStopBeforeTheFirstResume() {
        val gate = LifecyclePause()
        assertFalse(gate.onPause())
        assertFalse(gate.onStop())
    }
}
