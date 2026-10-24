package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeBandNavTest {
    private val fold = HomeFrame(933f, 572f, false, 933f / 660f)
    private val m = HomeMemory()
    // 6 tools, 7 games: Continue 0, the inline games 1 2, squares 3 4 / 5 6 / 7 8. Pins 9..13 (rows 9 10 11 12 / 13). New
    // 14..17, Not started 18..20, Pick up 21..24.
    // The large face's shelves hold at most 4 (one row), so New's 6 draws 4, Not started its 3 and Pick up its 4, in the
    // pins' grid (4 a row, the pins' tile size).
    private val s = homeShape(homeBoard(fold, 1, tools = 6, games = 7,
        shelves = listOf(HomeShelf.NEW to 6, HomeShelf.NOT_STARTED to 3, HomeShelf.PICK_UP to 4)))

    @Test fun bandsAreBuilt() {
        assertEquals(listOf(HomeBand(0, 5, 4), HomeBand(5, 4, 4), HomeBand(9, 3, 4), HomeBand(12, 4, 4)), s.bands)
        assertEquals(9 + 16, s.count)
    }
    @Test fun alongARowAndItsEdges() {
        assertEquals(15, homeStep(14, s, m, NxButton.RIGHT))
        assertNull(homeStep(17, s, m, NxButton.RIGHT))          // New's last (4 of 6): switch tab
        assertNull(homeStep(14, s, m, NxButton.LEFT))
        assertNull(homeStep(24, s, m, NxButton.RIGHT))          // Pick up's last (4 of 4)
        assertEquals(20, homeStep(19, s, m, NxButton.RIGHT))
        assertNull(homeStep(20, s, m, NxButton.RIGHT))          // Not started's last (3, one row)
        assertNull(homeStep(18, s, m, NxButton.LEFT))
    }
    @Test fun downFromPinsLastRowGoesToNewNearestInX() {
        assertEquals(14, homeStep(13, s, m, NxButton.DOWN))     // pin 4 (x 24) → New 0
        assertEquals(13, homeStep(10, s, m, NxButton.DOWN))      // the pins' own rule: the shorter last row's last pin
    }
    // Every shelf is in the pins' columns, so DOWN and UP between shelves keep the column.
    @Test fun downAndUpBetweenShelvesKeepTheColumn() {
        assertEquals(18, homeStep(14, s, m, NxButton.DOWN))     // New 0 → Not started 0
        assertEquals(19, homeStep(15, s, m, NxButton.DOWN))     // New 1 → Not started 1
        assertEquals(20, homeStep(16, s, m, NxButton.DOWN))     // New 2 → Not started 2
        assertEquals(20, homeStep(17, s, m, NxButton.DOWN))     // New 3 → Not started's last (no column 3 there)
        assertEquals(14, homeStep(18, s, m, NxButton.UP))       // Not started 0 → New 0
        assertEquals(15, homeStep(19, s, m, NxButton.UP))
        assertEquals(16, homeStep(20, s, m, NxButton.UP))       // Not started 2 → New 2
        assertEquals(21, homeStep(18, s, m, NxButton.DOWN))     // Not started 0 → Pick up 0
        assertEquals(22, homeStep(19, s, m, NxButton.DOWN))     // Not started 1 → Pick up 1
        assertEquals(23, homeStep(20, s, m, NxButton.DOWN))     // Not started 2 → Pick up 2
        assertEquals(18, homeStep(21, s, m, NxButton.UP))       // Pick up 0 → Not started 0
        assertEquals(19, homeStep(22, s, m, NxButton.UP))       // Pick up 1 → Not started 1
        assertEquals(20, homeStep(23, s, m, NxButton.UP))       // Pick up 2 → Not started 2
        assertEquals(20, homeStep(24, s, m, NxButton.UP))       // Pick up 3 → Not started's last
        assertEquals(24, homeStep(24, s, m, NxButton.DOWN))     // the last band stays
    }
    @Test fun upFromNewGoesToThePinsLastRowNearestInX() {
        assertEquals(13, homeStep(14, s, m, NxButton.UP))
        assertEquals(13, homeStep(17, s, m, NxButton.UP))       // only one pin on that row
    }
    @Test fun upFromThePinsFirstRowIsTheTop() {
        assertEquals(0, homeStep(9, s, m, NxButton.UP))
    }
    @Test fun noPinsDownGoesToTheFirstShelf() {
        val t = homeShape(homeBoard(fold, 1, tools = 0, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 3)))
        assertEquals(1, homeStep(0, t, m, NxButton.DOWN))
        assertEquals(0, homeStep(1, t, m, NxButton.UP))
    }
    @Test fun clampIntoFewerBands() {
        val phone = homeShape(homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 5))
        assertEquals(phone.count - 1, homeClamp(22, s, phone))  // a shelf tile on the large face → the phone's last pin
    }

    // The Fold with 1 pinned game: Continue 0, the inline game 1 (484.76 … 782.6), squares 2 3 / 4 5 / 6 7; New 8..11
    // (4 a shelf, in the pins' grid: 4 a row, 210.75 wide). RIGHT Continue → the game → the top square; LEFT back; DOWN
    // from the game to the first New tile under it (New 2, 473.5 … 684.25), UP back to it.
    @Test fun foldOneGameInline() {
        val t = homeShape(homeBoard(fold, 1, tools = 6, games = 1, shelves = listOf(HomeShelf.NEW to 6)))
        assertEquals(8, t.topCount); assertEquals(listOf(HomeBand(0, 4, 4)), t.bands)
        assertEquals(1, homeStep(0, t, m, NxButton.RIGHT)); assertEquals(2, homeStep(1, t, m, NxButton.RIGHT))
        assertEquals(1, homeStep(2, t, m, NxButton.LEFT)); assertEquals(1, homeStep(4, t, m, NxButton.LEFT))
        assertEquals(0, homeStep(1, t, m, NxButton.LEFT))
        assertEquals(10, homeStep(1, t, m, NxButton.DOWN)); assertEquals(8, homeStep(0, t, m, NxButton.DOWN))
        assertEquals(1, homeStep(10, t, HomeMemory(lastTop = 1), NxButton.UP))
        assertEquals(1, homeStep(1, t, m, NxButton.UP))         // the tab row
    }
}
