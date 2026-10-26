package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class MinimalAnchorTest {
    @Test fun the_selection_sits_a_third_of_the_way_down() {
        assertEquals(300f, minimalAnchor(bodyTop = 100f, pitchPx = 60f, boxH = 900f))
    }

    // A short landscape box under a tall strip: one row under the body's top, never over it.
    @Test fun never_higher_than_one_row_under_the_body_top() {
        assertEquals(220f, minimalAnchor(bodyTop = 160f, pitchPx = 60f, boxH = 420f))
    }
}
