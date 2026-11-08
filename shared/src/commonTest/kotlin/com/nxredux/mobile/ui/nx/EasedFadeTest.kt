package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EasedFadeTest {
    private val edge = NX_SWITCHER_BAR_ALPHA

    @Test fun runsFromTheEdgeToNothing() {
        assertEquals(edge, easedFadeAlpha(edge, 0f, 2f))
        assertEquals(0f, easedFadeAlpha(edge, 1f, 2f))
        assertEquals(0f, easedFadeAlpha(edge, 1f, TOP_BAND_POWER))
        for (p in listOf(2f, TOP_BAND_POWER)) {
            val a = (0..100).map { easedFadeAlpha(edge, it / 100f, p) }
            assertTrue(a.zipWithNext().all { (x, y) -> y <= x }, "never darkens away from the edge")
        }
    }

    @Test fun tailEndsWithZeroSlope() {
        // The slope where the fade runs out is 0, so a bright picture shows no line there.
        for (p in listOf(2f, TOP_BAND_POWER)) {
            fun slope(h: Float) = (easedFadeAlpha(edge, 1f - h, p) - easedFadeAlpha(edge, 1f, p)) / h
            assertTrue(slope(0.01f) < 0.01f, "slope ${slope(0.01f)} at power $p")
            assertTrue(slope(0.001f) < slope(0.01f) / 10f, "the slope keeps falling to 0 at power $p")
            // And the last linear piece of the drawn gradient is nearly flat.
            val last = 1f / (EASE_STOPS - 1)
            val piece = (easedFadeAlpha(edge, 1f - last, p) - easedFadeAlpha(edge, 1f, p)) / last
            assertTrue(piece < 0.1f, "last piece $piece at power $p")
        }
    }

    @Test fun textZonesStayAsDarkAsBefore() {
        // Switcher title: 10 dp + the 18 sp title + the 13 sp subtitle (about 52 dp), then the reach: the subtitle's
        // bottom keeps about 0.73 or more.
        val title = 52f / (52f + NX_EASED_FADE_REACH.value)
        assertTrue(easedFadeAlpha(edge, title, TOP_BAND_POWER) >= 0.73f, "subtitle ${easedFadeAlpha(edge, title, TOP_BAND_POWER)}")
        // Switcher info band, from the bottom edge: the 2 dp under the text and the 19 dp line, then the reach: the
        // text's top keeps about 0.75.
        val info = 21f / (21f + NX_EASED_FADE_REACH.value)
        val atInfo = easedFadeAlpha(edge, info, 2f)
        assertTrue(atInfo in 0.73f..0.8f, "info $atInfo")
        // The tab row's band: the labels' bottom (about 32 dp into the 44 dp row) keeps about 0.84.
        val labels = 32f / (MENU_TAB_ROW_HEIGHT.value + NX_TOP_BAND_REACH.value)
        assertTrue(easedFadeAlpha(edge, labels, TOP_BAND_POWER) >= 0.82f, "labels ${easedFadeAlpha(edge, labels, TOP_BAND_POWER)}")
    }

    @Test fun reachesFurtherThanBefore() {
        assertEquals(64f, NX_EASED_FADE_REACH.value)
        assertEquals(48f, NX_TOP_BAND_REACH.value)
        assertTrue(EASE_STOPS >= 16)
    }
}
