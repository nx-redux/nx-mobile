package com.nxredux.mobile.ui.nx

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RowSnapSlotTest {
    /** What Modifier.height(dp) lays out at [density]: the dp rounded to px. */
    private fun px(dp: Float, density: Float) = (dp * density).roundToInt()

    @Test fun theSlotIsExactlyTheRowsTheListLaysOut() {
        // A start-snapped list rests on whole rows only when its viewport is the sum of its rows' own rounded px.
        for (density in listOf(2f, 2.625f, 3f, 3.5f)) for (rowDp in listOf(26f, 27.878786f, 35.04762f, 36.4f, 41.77f)) for (rows in 2..12) {
            val rowPx = px(rowDp, density)
            val slotPx = nxRowsSlotPx(rowPx, rows)
            assertEquals(rows * rowPx, slotPx)
            assertEquals(slotPx, px(slotPx / density, density))   // the slot's dp lays out back at the same px
        }
    }

    @Test fun aFractionalDpSlotCanMissTheRowsItHolds() {
        // A slot of rows x a fractional dp row rounds once, the rows round each: 8 rows of 36.4 dp at 2.625x lay
        // out as 8 x 96 = 768 px, but the slot as 764 px, so the last row is cut by 4 px at rest.
        assertEquals(768, nxRowsSlotPx(px(36.4f, 2.625f), 8))
        assertEquals(764, px(36.4f * 8, 2.625f))
    }

    @Test fun anOptionsGapAddsItsOwnPx() {
        assertEquals(5 * 60 + 30, nxRowsSlotPx(60, 5, gapPx = 30))
    }

    @Test fun fitRowsAreWholePxThatNeverOverrunTheSlot() {
        for (slot in 300..900) for (rows in 1..9) {
            val row = nxFitRowPx(slot, rows)
            val used = nxRowsSlotPx(row, rows)
            assertTrue(used <= slot && slot - used < rows)
        }
        assertEquals(0, nxFitRowPx(0, 0))   // no rows: no divide by zero
    }
}
