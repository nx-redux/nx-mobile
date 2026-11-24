package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeBandNavTest {
    private val fold = HomeFrame(933f, 572f, false, 933f / 660f)
    private val m = HomeMemory()
    // 4 tools, 7 games: Continue 0, the inline games 1 2. Pins 3..7 (rows 3 4 5 6 / 7). Tools 8..11. New 12..15,
    // Not started 16..18, Pick up 19..22.
    // The large face's shelves hold at most 4 (one row), so New's 6 draws 4, Not started its 3 and Pick up its 4, in the
    // pins' grid (4 a row, the pins' tile size).
    private val s = homeShape(homeBoard(fold, 1, tools = 4, games = 7,
        shelves = listOf(HomeShelf.NEW to 6, HomeShelf.NOT_STARTED to 3, HomeShelf.PICK_UP to 4)))

    @Test fun bandsAreBuilt() {
        assertEquals(listOf(HomeBand(0, 5, 4), HomeBand(5, 4, 4), HomeBand(9, 4, 4), HomeBand(13, 3, 4), HomeBand(16, 4, 4)), s.bands)
        assertEquals(3 + 20, s.count)
    }
    @Test fun alongARowAndItsEdges() {
        assertEquals(13, homeStep(12, s, m, NxButton.RIGHT))
        assertNull(homeStep(15, s, m, NxButton.RIGHT))          // New's last (4 of 6): switch tab
        assertNull(homeStep(12, s, m, NxButton.LEFT))
        assertNull(homeStep(22, s, m, NxButton.RIGHT))          // Pick up's last (4 of 4)
        assertEquals(18, homeStep(17, s, m, NxButton.RIGHT))
        assertNull(homeStep(18, s, m, NxButton.RIGHT))          // Not started's last (3, one row)
        assertNull(homeStep(16, s, m, NxButton.LEFT))
    }
    @Test fun downFromPinsLastRowGoesToToolsBandThenNew() {
        assertEquals(8, homeStep(7, s, m, NxButton.DOWN))       // pin 4 → tool 0
        assertEquals(7, homeStep(4, s, m, NxButton.DOWN))       // the pins' own rule: the shorter last row's last pin
        assertEquals(12, homeStep(8, s, m, NxButton.DOWN))      // tool 0 → New 0
        assertEquals(15, homeStep(11, s, m, NxButton.DOWN))     // tool 3 → New 3
    }
    // Every shelf and tool band is in the pins' columns, so DOWN and UP between bands keep the column.
    @Test fun downAndUpBetweenShelvesKeepTheColumn() {
        assertEquals(16, homeStep(12, s, m, NxButton.DOWN))     // New 0 → Not started 0
        assertEquals(17, homeStep(13, s, m, NxButton.DOWN))     // New 1 → Not started 1
        assertEquals(18, homeStep(14, s, m, NxButton.DOWN))     // New 2 → Not started 2
        assertEquals(18, homeStep(15, s, m, NxButton.DOWN))     // New 3 → Not started's last (no column 3 there)
        assertEquals(12, homeStep(16, s, m, NxButton.UP))       // Not started 0 → New 0
        assertEquals(13, homeStep(17, s, m, NxButton.UP))
        assertEquals(14, homeStep(18, s, m, NxButton.UP))       // Not started 2 → New 2
        assertEquals(19, homeStep(16, s, m, NxButton.DOWN))     // Not started 0 → Pick up 0
        assertEquals(20, homeStep(17, s, m, NxButton.DOWN))     // Not started 1 → Pick up 1
        assertEquals(21, homeStep(18, s, m, NxButton.DOWN))     // Not started 2 → Pick up 2
        assertEquals(16, homeStep(19, s, m, NxButton.UP))       // Pick up 0 → Not started 0
        assertEquals(17, homeStep(20, s, m, NxButton.UP))       // Pick up 1 → Not started 1
        assertEquals(18, homeStep(21, s, m, NxButton.UP))       // Pick up 2 → Not started 2
        assertEquals(18, homeStep(22, s, m, NxButton.UP))       // Pick up 3 → Not started's last
        assertEquals(22, homeStep(22, s, m, NxButton.DOWN))     // the last band stays
    }
    @Test fun upFromNewGoesToToolsAndUpFromToolsGoesToPinsLastRow() {
        assertEquals(8, homeStep(12, s, m, NxButton.UP))
        assertEquals(11, homeStep(15, s, m, NxButton.UP))
        assertEquals(7, homeStep(8, s, m, NxButton.UP))
        assertEquals(7, homeStep(11, s, m, NxButton.UP))        // only one pin on the pins' last row
    }
    @Test fun upFromThePinsFirstRowIsTheTop() {
        assertEquals(0, homeStep(3, s, m, NxButton.UP))
    }
    @Test fun noPinsDownGoesToTheFirstShelf() {
        val t = homeShape(homeBoard(fold, 1, tools = 0, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 3)))
        assertEquals(1, homeStep(0, t, m, NxButton.DOWN))
        assertEquals(0, homeStep(1, t, m, NxButton.UP))
    }
    @Test fun clampIntoFewerBands() {
        val phone = homeShape(homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 5))
        assertEquals(phone.count - 1, homeClamp(20, s, phone))  // a shelf tile on the large face → the phone's last row tile
    }

    // The Fold with 1 pinned game and 4 tools: Continue 0 (38 … 543.8), the inline game 1 (557.8 … 895), then the tools
    // band 2..5 (4 a row, 203.75 wide: x 38, 255.75, 473.5, 691.25), then New 6..9 on the same 4-column grid.
    @Test fun foldOneGameInline() {
        val t = homeShape(homeBoard(fold, 1, tools = 4, games = 1, shelves = listOf(HomeShelf.NEW to 6)))
        assertEquals(2, t.topCount); assertEquals(listOf(HomeBand(0, 4, 4), HomeBand(4, 4, 4)), t.bands)
        assertEquals(1, homeStep(0, t, m, NxButton.RIGHT)); assertNull(homeStep(1, t, m, NxButton.RIGHT))
        assertEquals(0, homeStep(1, t, m, NxButton.LEFT))
        assertEquals(2, homeStep(0, t, m, NxButton.DOWN)); assertEquals(0, homeStep(2, t, m, NxButton.UP))
        assertEquals(3, homeStep(2, t, m, NxButton.RIGHT)); assertNull(homeStep(5, t, m, NxButton.RIGHT)); assertNull(homeStep(2, t, m, NxButton.LEFT))
        assertEquals(6, homeStep(2, t, m, NxButton.DOWN)); assertEquals(9, homeStep(5, t, m, NxButton.DOWN))
        assertEquals(4, homeStep(1, t, m, NxButton.DOWN))       // inline game (557.8 … 895) → tool 2 (473.5 … 677.25)
        assertEquals(1, homeStep(4, t, HomeMemory(lastTop = 1), NxButton.UP))
        assertEquals(1, homeStep(1, t, m, NxButton.UP))         // the top: stays
    }
}
