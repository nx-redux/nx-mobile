package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

// Home's captions (Continue, the pinned games, the wide and large faces' inline games) wrap the name to a
// second line where the card holds it, the info line under it at the name's size; a short card keeps one line.
class HomeCaptionLinesTest {
    // A pin's caption: the 13 sp name on a 16 dp line, 2 dp gaps, the 13 sp info line (16 dp), 8 dp bottom padding.
    private val pinLine = 16f
    private val pinBelow = 8f + pinLine + 2f   // 26

    @Test fun pinsWrapWhereTheCardHoldsTwoLines() {
        assertEquals(2, homeCaptionTitleLines(114f, pinLine, pinBelow))   // the Fold cover's portrait pins: 58 of 59.28
        assertEquals(2, homeCaptionTitleLines(120f, pinLine, pinBelow))   // landscape pins
        assertEquals(2, homeCaptionTitleLines(124.4f, pinLine, pinBelow))
        assertEquals(1, homeCaptionTitleLines(111f, pinLine, pinBelow))   // just short of 58 / 0.52
        assertEquals(1, homeCaptionTitleLines(49f, pinLine, pinBelow))    // the shrunk tiles: never under one
        assertEquals(1, homeCaptionTitleLines(0f, pinLine, pinBelow))
    }

    @Test fun neverMoreThanTwo() {
        assertEquals(2, homeCaptionTitleLines(400f, pinLine, pinBelow))
        assertEquals(3, homeCaptionTitleLines(400f, pinLine, pinBelow, maxLines = 3))
    }

    @Test fun whatSitsUnderTheNameCounts() {
        assertEquals(2, homeCaptionTitleLines(80f, pinLine, 8f))                          // no info: 40 of 41.6
        assertEquals(2, homeCaptionTitleLines(108f, pinLine, 8f + 14f + 2f))              // a Not started subtitle only: 56 of 56.16
        assertEquals(1, homeCaptionTitleLines(108f, pinLine, 8f + 14f + 2f + pinLine + 2f))   // subtitle and info
    }

    @Test fun aLargerTextScaleFallsBackToOneLine() {
        val line = pinLine * 1.3f
        assertEquals(1, homeCaptionTitleLines(114f, line, 8f + line + 2f))
        assertEquals(2, homeCaptionTitleLines(140f, line, 8f + line + 2f))
    }

    @Test fun continueCaptions() {
        // Portrait: 13.6 sp on 17 dp, gap 1.7, bottom 8.5; one time row. Landscape: 17 sp on 21.25 dp, gap 2.55, bottom 10.2.
        val pBelow = 8.5f + 17f + 1.7f
        assertEquals(2, homeCaptionTitleLines(118f, 17f, pBelow))
        assertEquals(1, homeCaptionTitleLines(117f, 17f, pBelow))
        val lBelow = 10.2f + 21.25f + 2.55f
        assertEquals(2, homeCaptionTitleLines(150f, 21.25f, lBelow))
        assertEquals(1, homeCaptionTitleLines(104.75f, 21.25f, lBelow))   // a short landscape Continue keeps one line
    }
}
