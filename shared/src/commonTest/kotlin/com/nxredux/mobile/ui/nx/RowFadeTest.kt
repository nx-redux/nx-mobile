package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** The Settings rows block's fade by row: 1, 0.6, 0.25 into an edge with more beyond. */
class RowFadeTest {
    private val steps = listOf(0.6f, 0.25f)
    // Rows 0..5 in view at 100 px each, a 600 px viewport.
    private fun view(first: Int, count: Int = 6) = (0 until count).map { RowSpan(first + it, it * 100, 100) }
    private fun alpha(rows: List<RowSpan>, i: Int, total: Int, back: Boolean, forward: Boolean, moving: Boolean = false, isRow: (Int) -> Boolean = { true }) =
        nxRowFadeAlpha(rows, i, 0, 600, total, back, forward, steps, moving, isRow)

    @Test fun lastThreeRowsStepDownWithMoreBelow() {
        val rows = view(0)
        assertEquals(1f, alpha(rows, 3, 10, back = false, forward = true))
        assertEquals(0.6f, alpha(rows, 4, 10, back = false, forward = true))
        assertEquals(0.25f, alpha(rows, 5, 10, back = false, forward = true))
    }

    @Test fun topMirrorsOnceScrolled() {
        val rows = view(4)
        assertEquals(0.25f, alpha(rows, 4, 10, back = true, forward = false))
        assertEquals(0.6f, alpha(rows, 5, 10, back = true, forward = false))
        assertEquals(1f, alpha(rows, 6, 10, back = true, forward = false))
    }

    @Test fun aListThatFitsShowsNoFade() {
        val rows = view(0)
        for (i in 0..5) assertEquals(1f, alpha(rows, i, 6, back = false, forward = false))
    }

    @Test fun oneRowBeyondFadesOnlyTheLastToTheOuterStep() {
        val rows = view(0)
        assertEquals(1f, alpha(rows, 4, 7, back = false, forward = true))
        assertEquals(0.25f, alpha(rows, 5, 7, back = false, forward = true))
    }

    @Test fun aRowTheEdgeCutsIsTheOuterStep() {
        // Rows of differing heights: row 5 runs 50 px past the end. It is the faint row saying more is there.
        val rows = view(0, 5) + RowSpan(5, 550, 100)
        assertEquals(0.25f, alpha(rows, 5, 10, back = false, forward = true))
        assertEquals(0.6f, alpha(rows, 4, 10, back = false, forward = true))
        assertEquals(1f, alpha(rows, 3, 10, back = false, forward = true))
        // The cut row the last there is: it alone fades.
        assertEquals(0.25f, alpha(rows, 5, 6, back = false, forward = true))
        assertEquals(1f, alpha(rows, 4, 6, back = false, forward = true))
    }

    @Test fun gapsAreNotRows() {
        val rows = view(0)
        // Row 4 is a gap: it stays 1, and the step skips it (row 3 is the second-last row).
        val isRow: (Int) -> Boolean = { it != 4 }
        assertEquals(1f, alpha(rows, 4, 10, back = false, forward = true, isRow = isRow))
        assertEquals(0.25f, alpha(rows, 5, 10, back = false, forward = true, isRow = isRow))
        assertEquals(0.6f, alpha(rows, 3, 10, back = false, forward = true, isRow = isRow))
    }
}
