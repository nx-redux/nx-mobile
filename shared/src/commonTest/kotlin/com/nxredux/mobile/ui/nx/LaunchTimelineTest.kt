package com.nxredux.mobile.ui.nx

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaunchTimelineTest {
    @Test fun end_radius_reaches_the_farthest_corner_plus_the_feather() {
        assertEquals(hypot(300f, 250f) + 90f, launchEndRadius(100f, 50f, 400f, 300f, 90f), 0.01f)
    }

    @Test fun radius_eases_out_from_zero_to_the_end() {
        val end = 500f
        assertEquals(0f, launchRadius(0L, end), 0.01f)
        assertEquals(end, launchRadius(450L, end), 0.01f)
        assertTrue(launchRadius(225L, end) > end / 2f)
        assertEquals(end, launchRadius(900L, end), 0.01f)
    }

    @Test fun colour_waits_80_ms_then_runs_400() {
        assertEquals(0f, launchColorFraction(79L), 0.001f)
        assertEquals(1f, launchColorFraction(480L), 0.001f)
        assertEquals(0.5f, launchColorFraction(280L), 0.05f)
    }

    @Test fun hold_lasts_400_ms_past_the_spread_or_until_the_first_frame() {
        assertNull(launchHoldUntil(450L, null))
        assertEquals(850L, launchHoldUntil(450L, 100L))
        assertEquals(2000L, launchHoldUntil(450L, 2000L))
    }
}
