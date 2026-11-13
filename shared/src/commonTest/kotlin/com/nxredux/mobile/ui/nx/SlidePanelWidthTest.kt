package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** The slide panel's width: its share of the page, at least 320 dp, at most 80% of the page. */
class SlidePanelWidthTest {
    private fun width(w: Float, portrait: Boolean) = slidePanelWidthDp(w, if (portrait) 0.6f else 0.45f)

    @Test fun aPhonePortraitIsCappedAtEightyPercent() = assertEquals(314.4f, width(393f, true), 0.01f)   // 60% = 236 -> min 320 -> cap
    @Test fun theFoldCoverGlassIsRaisedToTheMinimum() = assertEquals(320f, width(440f, false), 0.01f)   // 45% = 198
    @Test fun aWideLandscapeScreenKeepsItsShare() = assertEquals(405f, width(900f, false), 0.01f)
    @Test fun aTabletPortraitKeepsItsShare() = assertEquals(480f, width(800f, true), 0.01f)
    @Test fun aVeryNarrowAreaIsEightyPercentOfIt() = assertEquals(240f, width(300f, true), 0.01f)
    @Test fun atTheBoundaries() {
        assertEquals(320f, width(400f, true), 0.01f)         // 80% of 400 is exactly the minimum
        assertEquals(320f, width(320f / 0.45f, false), 0.01f) // the share reaches the minimum
        assertEquals(320f, width(320f / 0.6f, true), 0.01f)
    }
}
