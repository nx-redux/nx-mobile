package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class RestingInsetsTest {
    @Test fun mid_animation_the_bars_resting_inset_wins_over_the_animated_one() {
        // Back from an immersive game: the bars animate in from 0, the window already reports their 63 px.
        assertEquals(63, restingInset(livePx = 0, restingPx = 63, enabled = true))
    }

    @Test fun bars_hidden_at_rest_stay_hidden() {
        assertEquals(0, restingInset(livePx = 0, restingPx = 0, enabled = true))
    }

    @Test fun without_the_window_insets_the_live_value_is_kept() {
        assertEquals(63, restingInset(livePx = 63, restingPx = null, enabled = true))
    }

    @Test fun a_host_that_consumed_the_insets_keeps_the_live_value() {
        // The second screen's presentation pads its root for a DeX taskbar and hands Compose 0: not padded twice.
        assertEquals(0, restingInset(livePx = 0, restingPx = 48, enabled = false))
    }
}
