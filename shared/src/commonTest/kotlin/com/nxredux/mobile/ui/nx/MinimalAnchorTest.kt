package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

class MinimalAnchorTest {
    // The selected name's centre line: a third (0.33) of the way down the band from its top edge.
    @Test fun the_line_sits_a_third_down_the_band() {
        assertEquals(397f, minimalAnchor(bandTop = 100f, bandBottom = 1000f), 1e-3f)
    }

    // A taller header above MinimalScreen shifts its local box down by extraHeader; anchoring from -extraHeader keeps the
    // selected row at the exact same window coordinate as sibling tabs, while clamping safely clear of bandTop on short screens.
    @Test fun taller_header_keeps_sibling_tab_baseline_and_clamps_on_short_bands() {
        val windowH = 436f
        val standardHeader = 44f
        val extraHeader = 16.5f
        val siblingLine = minimalAnchor(bandTop = standardHeader, bandBottom = windowH)
        val gamesLocalLine = minimalAnchor(
            bandTop = 0f,
            bandBottom = windowH - (standardHeader + extraHeader),
            anchorTop = -extraHeader,
            halfPitch = 20f,
        )
        assertEquals(siblingLine, standardHeader + extraHeader + gamesLocalLine, 1e-3f)
        // On a very short band, the anchor never moves above bandTop + halfPitch.
        assertEquals(20f, minimalAnchor(bandTop = 0f, bandBottom = 60f, anchorTop = -40f, halfPitch = 20f), 1e-3f)
    }
}
