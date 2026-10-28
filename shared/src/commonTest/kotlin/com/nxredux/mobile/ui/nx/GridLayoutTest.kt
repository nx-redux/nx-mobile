package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GridLayoutTest {
    // Still, row-major: 0 1 2 / 3 4 (columns 3, count 5)
    @Test fun stillGridStepsWithinARowAndWrapsRows() {
        assertEquals(1, gridStep(0, 5, 3, NxButton.RIGHT))
        assertNull(gridStep(2, 5, 3, NxButton.RIGHT))         // a row's last column: the caller switches tab
        assertNull(gridStep(3, 5, 3, NxButton.LEFT))          // a row's first column likewise
        assertEquals(3, gridStep(2, 5, 3, NxButton.RIGHT, rowEnds = false))   // the app drawer: on along the row order
        assertNull(gridStep(0, 5, 3, NxButton.LEFT))          // past the first: the caller switches tab
        assertNull(gridStep(4, 5, 3, NxButton.RIGHT))         // past the last
        assertEquals(3, gridStep(0, 5, 3, NxButton.DOWN))
        assertEquals(4, gridStep(2, 5, 3, NxButton.DOWN))     // below is empty: the last tile of the lower row
        assertEquals(1, gridStep(4, 5, 3, NxButton.UP))
        assertEquals(3, gridStep(0, 5, 3, NxButton.UP))       // wraps: top row -> bottom row, same column
        assertEquals(0, gridStep(3, 5, 3, NxButton.DOWN))     // wraps: bottom row -> top row
        assertEquals(4, gridStep(2, 5, 3, NxButton.UP))       // wraps onto an empty spot: the last tile
    }

    @Test fun oneOrTwoItems() {
        assertEquals(0, gridStep(0, 1, 3, NxButton.UP))
        assertEquals(0, gridStep(0, 1, 3, NxButton.DOWN))
        assertNull(gridStep(0, 1, 3, NxButton.LEFT))
        assertNull(gridStep(0, 1, 3, NxButton.RIGHT))
        assertEquals(1, gridStep(0, 2, 3, NxButton.RIGHT))
        assertEquals(1, gridStep(1, 2, 3, NxButton.DOWN))     // one row: stays
        assertEquals(0, gridStep(0, 0, 3, NxButton.DOWN))     // empty: stays
    }

    // The main menu's tab row: UP from the top row reaches it; UP on it lands on the bottom row. Row-major 0 1 2 / 3 4 (columns 3).
    @Test fun gridTopRowIsTheFirstRow() {
        assertTrue(gridAtTop(0, 5, 3)); assertTrue(gridAtTop(2, 5, 3))
        assertFalse(gridAtTop(3, 5, 3)); assertFalse(gridAtTop(4, 5, 3))
        assertTrue(gridAtTop(0, 0, 3))                                         // empty: the top
    }

    // The logo as drawn (Fit in its box): its own aspect (height over width) capped at the box; no logo, half the box.
    @Test fun logoDrawnHeightAndTheLineUnderIt() {
        assertEquals(99f, logoDrawnHeight(330f, 130f, 0.3f), 1e-3f)
        assertEquals(130f, logoDrawnHeight(330f, 130f, 1f), 1e-3f)     // capped at the box
        assertEquals(65f, logoDrawnHeight(330f, 130f, null), 1e-3f)    // no logo: the mockup's fallback
        assertEquals(100f + 114.5f + 8f, underLogoTop(100f, 130f, 99f, 8f), 1e-3f)
    }
}
