package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class MinimalAnchorTest {
    // The selected name's centre line: a third (0.33) of the way down the band from its top edge.
    @Test fun the_line_sits_a_third_down_the_band() {
        assertEquals(397f, minimalAnchor(bandTop = 100f, bandBottom = 1000f), 1e-3f)
    }
}
