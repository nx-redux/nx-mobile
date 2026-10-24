package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** A list's highlighted row is brought into view by the smallest scroll ([nxKeepInViewScroll]). */
class KeepInViewTest {
    // A 200 px viewport of 20 px rows: rows 0..9 show.
    private fun scroll(selected: Int, offset: Int?, lastVisible: Int = 9) =
        nxKeepInViewScroll(selected, offset, itemSize = 20, viewportStart = 0, viewportEnd = 200, lastVisible = lastVisible, rowHeight = 20)

    @Test fun aRowFullyInViewDoesNotScroll() {
        assertEquals(NxKeepInView.None, scroll(3, offset = 60))
    }

    @Test fun aPartlyVisibleRowScrollsByItsOverflow() {
        assertEquals(NxKeepInView.By(12), scroll(9, offset = 192))     // 12 px past the bottom
        assertEquals(NxKeepInView.By(-5), scroll(0, offset = -5))      // 5 px above the top
    }

    @Test fun anOffScreenRowJumpsToTheNearLine() {
        assertEquals(NxKeepInView.ToItem(15, -180), scroll(15, offset = null))                 // below: lands on the last line
        assertEquals(NxKeepInView.ToItem(2, 0), scroll(2, offset = null, lastVisible = 14))    // above: lands on the top line
    }

    // The same viewport with a one-row margin ([nxKeepInViewMargin]): rows 0..9 show of 30, row r at r * 20 - top.
    private fun margin(selected: Int, top: Int = 0, count: Int = 30) = nxKeepInViewMargin(
        selected, margin = 1, count = count, offsetOf = { r -> (r * 20 - top).takeIf { it > -20 && it < 200 } },
        itemSize = 20, viewportStart = 0, viewportEnd = 200, lastVisible = minOf(count - 1, (top + 199) / 20), rowHeight = 20,
    )

    @Test fun aPartlyShownMarginRowScrollsByItsOverflow() {
        assertEquals(NxKeepInView.By(10), margin(9, top = 10))            // scrolled 10 px: row 10 half shows
    }

    @Test fun theMarginKeepsTheRowBelowInView() {
        assertEquals(NxKeepInView.None, margin(8))                        // row 9 (its margin) fully shows
        assertEquals(NxKeepInView.ToItem(10, -180), margin(9))            // row 10 just off the bottom: onto the last line
    }

    @Test fun theMarginKeepsTheRowAboveInView() {
        assertEquals(NxKeepInView.None, margin(6, top = 100))             // rows 5..14 show; row 5 is the margin
        assertEquals(NxKeepInView.ToItem(4, 0), margin(5, top = 100))     // row 4 just off the top: onto the top line
    }

    @Test fun theListsEndsNeedNoMargin() {
        assertEquals(NxKeepInView.None, margin(0))                        // the first row: nothing above
        assertEquals(NxKeepInView.None, margin(29, top = 400))            // the last row (rows 20..29 show)
    }

    @Test fun anOffScreenRowLandsOneRowInFromTheEdge() {
        assertEquals(NxKeepInView.ToItem(16, -180), margin(15))           // below: its margin row on the last line
        assertEquals(NxKeepInView.ToItem(1, 0), margin(2, top = 280))     // above: its margin row on the top line
    }

    // A 200 px viewport kept 15 px clear at both edges ([nxKeepInViewEdge]), for a row [size] tall at [offset].
    private fun edge(selected: Int, offset: Int?, size: Int = 20, lastVisible: Int = 9) =
        nxKeepInViewEdge(selected, offset, size, edge = 15, viewportStart = 0, viewportEnd = 200, lastVisible = lastVisible)

    @Test fun aRowClearOfTheEdgesDoesNotScroll() {
        assertEquals(NxKeepInView.None, edge(3, offset = 15))
        assertEquals(NxKeepInView.None, edge(8, offset = 165))
    }

    @Test fun aRowUnderAFadeScrollsClearOfIt() {
        assertEquals(NxKeepInView.By(10), edge(8, offset = 175))            // its bottom at 195: 10 px into the 15 px fade
        assertEquals(NxKeepInView.By(25), edge(8, offset = 160, size = 50)) // a taller row, the same way
        assertEquals(NxKeepInView.By(-7), edge(1, offset = 8))              // under the top fade
    }

    @Test fun anOffScreenRowLandsClearOfTheEdge() {
        assertEquals(NxKeepInView.ToItem(15, -165), edge(15, offset = null))
        assertEquals(NxKeepInView.ToItem(2, -15), edge(2, offset = null, lastVisible = 14))
    }

    @Test fun theEdgeShrinksToTheRoomAroundTheRow() {
        assertEquals(NxKeepInView.By(20), edge(1, offset = 30, size = 180))  // 10 px each side: its bottom at 210 → 190
    }
}
