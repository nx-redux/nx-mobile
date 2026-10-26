package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeCarouselNavTest {
    // rows 0..2 (Continue, 2 pins), dock 3..4
    @Test fun row_left_right_step_and_switch_tab_past_the_ends() {
        assertEquals(HomeCarouselMove.To(1), homeCarouselStep(0, 3, 5, NxButton.RIGHT, 0, null))
        assertEquals(HomeCarouselMove.SwitchTab(-1), homeCarouselStep(0, 3, 5, NxButton.LEFT, 0, null))
        assertEquals(HomeCarouselMove.SwitchTab(1), homeCarouselStep(2, 3, 5, NxButton.RIGHT, 2, null))
    }
    @Test fun down_goes_to_the_dock_square_last_used_up_returns_to_the_row_and_stays_there() {
        assertEquals(HomeCarouselMove.To(3), homeCarouselStep(1, 3, 5, NxButton.DOWN, 1, null))
        assertEquals(HomeCarouselMove.To(4), homeCarouselStep(1, 3, 5, NxButton.DOWN, 1, 4))
        assertEquals(HomeCarouselMove.To(1), homeCarouselStep(4, 3, 5, NxButton.UP, 1, 4))
        assertEquals(HomeCarouselMove.Stay, homeCarouselStep(1, 3, 5, NxButton.UP, 1, null))   // UP from the row stays
    }
    @Test fun dock_left_right_step_and_switch_tab_past_the_ends() {
        assertEquals(HomeCarouselMove.SwitchTab(-1), homeCarouselStep(3, 3, 5, NxButton.LEFT, 0, 3))
        assertEquals(HomeCarouselMove.To(4), homeCarouselStep(3, 3, 5, NxButton.RIGHT, 0, 3))
        assertEquals(HomeCarouselMove.SwitchTab(1), homeCarouselStep(4, 3, 5, NxButton.RIGHT, 0, 4))
        assertEquals(HomeCarouselMove.Stay, homeCarouselStep(4, 3, 5, NxButton.DOWN, 0, 4))
    }
    @Test fun no_dock_down_stays() = assertEquals(HomeCarouselMove.Stay, homeCarouselStep(0, 3, 3, NxButton.DOWN, 0, null))
    @Test fun stale_memory_is_clamped() {
        assertEquals(HomeCarouselMove.To(4), homeCarouselStep(0, 3, 5, NxButton.DOWN, 0, 9))
        assertEquals(HomeCarouselMove.To(2), homeCarouselStep(3, 3, 5, NxButton.UP, 7, 3))
    }
}
