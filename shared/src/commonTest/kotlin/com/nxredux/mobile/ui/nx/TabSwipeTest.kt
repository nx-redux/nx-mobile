package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The main menu's tab swipe: its axis lock, commit rule, target, and settle velocity clamping. */
class TabSwipeTest {
    // The first travel decides: within 30° of horizontal a tab swipe, else the list scrolls.
    @Test fun the_axis_locks_on_the_first_travel() {
        assertNull(tabSwipeAxis(10f, 0f, lockPx = 16f))
        assertEquals(true, tabSwipeAxis(16f, 0f, 16f))
        assertEquals(true, tabSwipeAxis(-20f, 11f, 16f))    // ~29°
        assertEquals(false, tabSwipeAxis(-20f, 12f, 16f))   // ~31°
        assertEquals(false, tabSwipeAxis(0f, 20f, 16f))
    }

    // Past 30% of the width, or a fling the drag's way: right to left is the next tab, left to right the previous.
    @Test fun a_release_commits_past_thirty_percent_or_on_a_fling() {
        assertEquals(1, tabSwipeStep(-31f, 100f, 0f, flingPx = 500f))
        assertEquals(-1, tabSwipeStep(31f, 100f, 0f, flingPx = 500f))
        assertEquals(0, tabSwipeStep(-29f, 100f, 0f, flingPx = 500f))
        assertEquals(1, tabSwipeStep(-5f, 100f, -600f, 500f))   // a fling
        assertEquals(0, tabSwipeStep(-5f, 100f, 600f, 500f))    // flung back: stays
        assertEquals(0, tabSwipeStep(0f, 100f, -600f, 500f))
    }

    // Settling a commit slides the outgoing page one full width off-screen; a cancel returns to 0.
    @Test fun settle_target_matches_step_and_width() {
        assertEquals(-1000f, tabSwipeTarget(1, 1000f))
        assertEquals(1000f, tabSwipeTarget(-1, 1000f))
        assertEquals(0f, tabSwipeTarget(0, 1000f))
    }

    // Release velocity is preserved when safe, clamped against overshoot past target, and never pulls backward on a commit.
    @Test fun settle_velocity_preserves_momentum_without_overshoot() {
        // Stiffness 900 -> omega = 30. Drag -400 -> target -1000 => d = +600, bound = -18000.
        assertEquals(-2500f, tabSwipeSettleVelocity(-400f, -1000f, -2500f, stiffness = 900f, commit = true))
        // Near target (-950 -> -1000 => d = +50, bound = -1500): fast flick is clamped so it never overshoots -1000.
        assertEquals(-1500f, tabSwipeSettleVelocity(-950f, -1000f, -6000f, stiffness = 900f, commit = true))
        // Reverse velocity on a committed swipe is zeroed so it never tugs back before sliding forward.
        assertEquals(0f, tabSwipeSettleVelocity(-400f, -1000f, 400f, stiffness = 900f, commit = true))
        // Previous tab (+400 -> +1000 => d = -600, bound = +18000).
        assertEquals(2500f, tabSwipeSettleVelocity(400f, 1000f, 2500f, stiffness = 900f, commit = true))
        assertEquals(1500f, tabSwipeSettleVelocity(950f, 1000f, 6000f, stiffness = 900f, commit = true))
        // Cancel (-100 -> 0 => d = -100, bound = +3000): fling back is preserved up to bound, never overshooting past 0.
        assertEquals(2000f, tabSwipeSettleVelocity(-100f, 0f, 2000f, stiffness = 900f, commit = false))
        assertEquals(3000f, tabSwipeSettleVelocity(-100f, 0f, 5000f, stiffness = 900f, commit = false))
        assertTrue(tabSwipeSettleVelocity(-100f, 0f, -200f, stiffness = 900f, commit = false) == -200f)
        // Already at target: zero velocity.
        assertEquals(0f, tabSwipeSettleVelocity(0f, 0f, -500f, stiffness = 900f, commit = false))
    }
}
