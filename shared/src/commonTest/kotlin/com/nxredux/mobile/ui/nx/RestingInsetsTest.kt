package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class RestingInsetsTest {
    @Test fun mid_animation_the_bars_resting_inset_wins_over_the_animated_one() {
        // Back from an immersive game: the bars animate in from 0, the window already reports their 63 px.
        assertEquals(63, restingInset(livePx = 0, restingPx = 63))
    }

    @Test fun bars_hidden_at_rest_stay_hidden() {
        assertEquals(0, restingInset(livePx = 0, restingPx = 0))
    }

    @Test fun without_the_window_insets_the_live_value_is_kept() {
        assertEquals(63, restingInset(livePx = 63, restingPx = null))
    }
}
