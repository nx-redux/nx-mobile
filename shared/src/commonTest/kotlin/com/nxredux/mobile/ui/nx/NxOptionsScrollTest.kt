package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NxOptionsScrollTest {
    private val row = 60
    private val gap = 30

    /** A Settings-like page: rows with a gap after the 3rd, 5th and 6th rows ... */
    private val spacers = listOf(false, false, false, true, false, false, true, false, true, false, false, false, false, true, false, false)
    /** ... in the scaffold's slot: 5 whole rows and one gap. */
    private val viewport = nxRowsSlotPx(row, 5, gap)

    private fun geometry(sp: List<Boolean> = spacers, v: Int = viewport) = NxOptionsGeometry.of(sp, row, gap, v)

    /** The items wholly drawn at [offset] (the rows above the whole bottom), and whether one is cut at the top. */
    private fun cleanAt(g: NxOptionsGeometry, offset: Int): Boolean {
        val bottom = nxOptionsWholeBottom(g.starts, offset, g.viewportPx)
        val topCut = (0 until g.starts.size - 1).any { g.starts[it] < offset && g.starts[it + 1] > offset }
        val bottomCut = (0 until g.starts.size - 1).any { g.starts[it] - offset < bottom && g.starts[it + 1] - offset > bottom }
        return !topCut && !bottomCut
    }

    @Test fun startsAddRowsAndGaps() {
        assertContentEquals(intArrayOf(0, 60, 90, 150), nxOptionsStarts(listOf(false, true, false), row, gap))
    }

    @Test fun theEndRestsOnARowTopWithTheLastRowWhole() {
        val g = geometry()
        val total = g.starts.last()
        val maxScroll = total + g.endPadPx - viewport
        assertEquals(g.rests.last(), maxScroll)
        val firstShown = g.starts.indexOf(maxScroll)
        assertTrue(firstShown >= 0 && !spacers[firstShown], "the end's first item is a row")
        assertTrue(maxScroll + viewport >= total, "the last row is whole at the end")
        assertTrue(cleanAt(g, maxScroll))
    }

    @Test fun aListThatFitsHasNoPadAndOneRest() {
        val g = geometry(listOf(false, true, false), v = 1000)
        assertEquals(0, g.endPadPx)
        assertContentEquals(intArrayOf(0), g.rests)
    }

    @Test fun everyRestShowsOnlyWholeRowsWhateverTheGapsOnScreen() {
        // Any gap pattern and viewport (the modal's isn't whole rows): no rest leaves a cut row, and none starts on a gap.
        val patterns = listOf(spacers, List(20) { it % 3 == 2 }, List(12) { false }, List(14) { it == 1 || it == 2 || it == 9 })
        for (sp in patterns) for (v in listOf(viewport, 5 * row, 7 * row + 2 * gap, 333, 451)) {
            val g = geometry(sp, v)
            for (r in g.rests) {
                assertTrue(cleanAt(g, r), "rest $r of v=$v")
                val i = g.starts.indexOf(r)
                assertTrue(r == 0 || (i >= 0 && !sp[i]), "rest $r is a row top")
            }
        }
    }

    @Test fun theRestingListDrawsDownToItsLastWholeItem() {
        // No gap on screen: 6 rows from 0 in 330 px, the 6th cut at 300..360, so drawing stops at 300.
        val starts = nxOptionsStarts(List(10) { false }, row, gap)
        assertEquals(300, nxOptionsWholeBottom(starts, 0, 330))
        assertEquals(330, nxOptionsWholeBottom(nxOptionsStarts(listOf(false, false, true, false, false, false), row, gap), 0, 330))
        assertEquals(20, nxOptionsWholeBottom(starts, 0, 20))   // not even one row fits: nothing hidden
    }

    // A viewport sized in dp from the rows (a list that fits) can come out a px under their rounded sum: the last row
    // still draws, not hidden as cut (the Permissions page's Notifications row on the Fold's cover screen).
    @Test fun aRowShortByAPxOfRoundingStillDraws() {
        val starts = nxOptionsStarts(listOf(false, false), 67, 34)   // two 67 px rows: 0, 67, 134
        assertEquals(133, nxOptionsWholeBottom(starts, 0, 133))
        assertEquals(132, nxOptionsWholeBottom(starts, 0, 132))
        assertEquals(67, nxOptionsWholeBottom(starts, 0, 120))      // really cut: still hidden
    }

    // A list that fits is sized from its rows' own rounded px, so its content is exactly its height: nothing to scroll.
    @Test fun aFittingListIsItsRowsRoundedPx() {
        val rows = listOf(NxRow("a"), NxRow("b"), NxRow("", spacer = true), NxRow("c"))
        assertEquals(67 * 3 + 34, nxOptionsListHeightPx(rows, rowPx = 67, gapPx = 34))
        assertEquals(nxOptionsStarts(rows.map { it.spacer }, 67, 34).last(), nxOptionsListHeightPx(rows, 67, 34))
    }

    @Test fun aSlowReleaseRestsOnTheNearestRow() {
        val rests = intArrayOf(0, 60, 120, 210)
        assertEquals(60, nxOptionsFlingRest(rests, 80f, 0f, 0))
        assertEquals(120, nxOptionsFlingRest(rests, 100f, 0f, 0))
        assertEquals(210, nxOptionsFlingRest(rests, 400f, 0f, 0))   // an overscroll past the end
    }

    @Test fun aFlingGoesAtLeastToTheNextRowItsWay() {
        val rests = intArrayOf(0, 60, 120, 210, 300)
        assertEquals(120, nxOptionsFlingRest(rests, 70f, 5f, 1))
        assertEquals(60, nxOptionsFlingRest(rests, 115f, -5f, -1))
        assertEquals(300, nxOptionsFlingRest(rests, 0f, 280f, 1))   // a long fling: where the decay stops
    }

    @Test fun keepInViewBringsTheSelectedRowWholeAtARest() {
        val g = geometry()
        // At 0 the 330 px viewport holds rows 0-2, the gap, rows 4-5 (ending at 330); row 7 (360..420) is below.
        assertNull(nxOptionsKeepInView(5, 0, g.starts, g.rests, viewport))
        val down = nxOptionsKeepInView(7, 0, g.starts, g.rests, viewport)!!
        assertTrue(down in g.rests && g.starts[8] <= down + viewport && cleanAt(g, down))
        assertEquals(down, g.rests.first { it + viewport >= g.starts[8] })   // the smallest such scroll
        // Back up to a row above: its top on the viewport's top.
        assertEquals(g.starts[1], nxOptionsKeepInView(1, g.starts[4], g.starts, g.rests, viewport))
        // Every row, from every rest, ends whole and clean.
        for (sel in spacers.indices) if (!spacers[sel]) for (r in g.rests) {
            val to = nxOptionsKeepInView(sel, r, g.starts, g.rests, viewport) ?: r
            assertTrue(g.starts[sel] >= to && g.starts[sel + 1] <= to + viewport && cleanAt(g, to), "row $sel from $r")
            assertTrue(g.starts[sel + 1] - to <= nxOptionsWholeBottom(g.starts, to, viewport), "row $sel drawn")
        }
    }

    @Test fun keepInViewWithAMarginKeepsARowClearPastTheSelection() {
        val g = geometry()
        val total = g.starts.last()
        // Row 4 (210..270) and a row's margin end at 330, the viewport's bottom: no scroll; row 5 needs one.
        assertNull(nxOptionsKeepInView(4, 0, g.starts, g.rests, viewport, marginPx = row))
        assertEquals(60, nxOptionsKeepInView(5, 0, g.starts, g.rests, viewport, marginPx = row))
        // From a step (one row up or down) at every rest: the margin, clamped to the list's ends, shows.
        for (sel in spacers.indices) if (!spacers[sel]) for (r in g.rests) {
            val from = nxOptionsKeepInView(sel, r, g.starts, g.rests, viewport, marginPx = row) ?: r
            assertTrue(maxOf(0, g.starts[sel] - row) >= from || g.starts[sel + 1] + row > from + viewport, "row $sel from $r: up")
            assertTrue(minOf(total, g.starts[sel + 1] + row) <= from + viewport || g.starts[sel] - row < from, "row $sel from $r: down")
            assertTrue(g.starts[sel] >= from && g.starts[sel + 1] <= from + viewport, "row $sel from $r: whole")
        }
    }
}
