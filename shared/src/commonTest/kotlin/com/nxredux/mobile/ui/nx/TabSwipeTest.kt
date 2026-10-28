package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The main menu's tab swipe: its axis lock and its commit. */
class TabSwipeTest {
    // The first 16 dp decide: within 30° of horizontal a tab swipe, else the list scrolls.
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
        assertEquals(-1, tabSwipeStep(31f, 100f, 0f, 500f))
        assertEquals(0, tabSwipeStep(-29f, 100f, 0f, 500f))
        assertEquals(1, tabSwipeStep(-5f, 100f, -600f, 500f))   // a fling
        assertEquals(0, tabSwipeStep(-5f, 100f, 600f, 500f))    // flung back: stays
        assertEquals(0, tabSwipeStep(0f, 100f, -600f, 500f))
    }
}
