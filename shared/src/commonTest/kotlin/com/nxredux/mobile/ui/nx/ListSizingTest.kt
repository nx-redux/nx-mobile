package com.nxredux.mobile.ui.nx

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ListSizingTest {
    @Test fun theListPitchIsThirtyTwoAndAHalfDp() {
        assertEquals(32.5f, NxListPitch.value)
    }

    @Test fun plainRowCountsFollowThePitch() {
        assertEquals(8, nxRowCountFor(260.dp))
        assertEquals(1, nxRowCountFor(10.dp))
    }

    @Test fun plainLabelIsSixtyTwoPercentOfTheRow() {
        assertEquals(20.15f, nxListLabelSize().value, 0.001f)
    }

    @Test fun richRowsTakeTheNearestCountOfOneAndAHalfPitches() {
        assertEquals(5, nxRichRowCountFor(240.dp))   // 48 dp rows
        assertEquals(4, nxRichRowCountFor(193.dp))   // 48 dp rows: not taller above a footer
        assertEquals(1, nxRichRowCountFor(20.dp))
    }
}
