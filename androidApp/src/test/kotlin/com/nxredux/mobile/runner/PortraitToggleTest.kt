package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Test

/** The portrait band's hide / show icon mirrors the mic button: the same edge margin, level centres. */
class PortraitToggleTest {
    @Test fun mirrors_the_mics_edge_and_centre() {
        // The mic 22 dp from the left, its centre 40 dp up: the icon's own 6 dp padding and 24 dp centre leave 16 each.
        assertEquals(PortraitToggleInset(16f, 16f), portraitToggleInset(micEdgeDp = 22f, micCentreDp = 40f))
    }

    @Test fun never_past_the_corner() {
        assertEquals(PortraitToggleInset(0f, 0f), portraitToggleInset(micEdgeDp = 4f, micCentreDp = 20f))
    }
}
