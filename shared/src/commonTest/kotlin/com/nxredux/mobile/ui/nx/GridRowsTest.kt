package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The d-pad over grids of three and four rows. */
class GridRowsTest {
    @Test fun stillThreeRowsStepWithinARowAndWrapInTheColumn() {
        fun step(i: Int, b: NxButton) = gridStep(i, 13, 5, b)   // rows of 5, 5, 3
        assertNull(step(4, NxButton.RIGHT))            // a row's last column: the caller switches tab
        assertEquals(5, gridStep(4, 13, 5, NxButton.RIGHT, rowEnds = false))   // the app drawer: on along the row order
        assertEquals(3, step(4, NxButton.LEFT))
        assertNull(step(0, NxButton.LEFT))             // past the first: the caller switches tab
        assertNull(step(12, NxButton.RIGHT))           // past the last
        assertEquals(7, step(2, NxButton.DOWN))
        assertEquals(12, step(7, NxButton.DOWN))
        assertEquals(2, step(12, NxButton.DOWN))       // wraps bottom -> top, same column
        assertEquals(12, step(2, NxButton.UP))         // wraps top -> bottom
        assertEquals(12, step(9, NxButton.DOWN))       // below is empty: the last tile
        assertEquals(12, step(4, NxButton.UP))         // wraps onto an empty spot: the last tile
        assertEquals(4, step(9, NxButton.UP))
    }

    @Test fun stillFourRowsWrapAndFillEmptySpots() {
        fun step(i: Int, b: NxButton) = gridStep(i, 14, 4, b)   // rows of 4, 4, 4, 2
        assertEquals(13, step(1, NxButton.UP))         // wraps to the bottom row, same column
        assertEquals(13, step(2, NxButton.UP))         // that spot is empty: the last tile
        assertEquals(1, step(13, NxButton.DOWN))       // wraps to the top
        assertEquals(13, step(10, NxButton.DOWN))      // below is empty: the last tile
        assertEquals(8, step(4, NxButton.DOWN))
        assertEquals(0, step(4, NxButton.UP))
    }
}
