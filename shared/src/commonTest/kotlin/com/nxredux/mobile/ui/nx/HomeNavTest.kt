package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeNavTest {
    private val land = homeShape(homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 6))
    private val m = HomeMemory()

    // Landscape, 6 tools, 6 games (the wide face): Continue 0 (x 38–560) and beside it the inline games G1 1 (top) and
    // G2 2 (x 574–922), stacked; then the other 4 games as rows 3 4 5 / 6 (285.33 wide: 38, 337.33, 636.67); then the
    // 6 pinned tools as rows 7 8 9 / 10 11 12 on the same 3-column grid.
    @Test fun rightGoesFromContinueToTheGamesAndAlongRows() {
        assertEquals(1, homeStep(0, land, m, NxButton.RIGHT))   // a tie (G1 and G2): the top one
        assertNull(homeStep(1, land, m, NxButton.RIGHT)); assertNull(homeStep(2, land, m, NxButton.RIGHT))   // the page's right edge: switch tab
        assertEquals(4, homeStep(3, land, m, NxButton.RIGHT)); assertEquals(5, homeStep(4, land, m, NxButton.RIGHT))
        assertNull(homeStep(5, land, m, NxButton.RIGHT))        // row end: switch tab
        assertEquals(8, homeStep(7, land, m, NxButton.RIGHT)); assertEquals(9, homeStep(8, land, m, NxButton.RIGHT))
        assertNull(homeStep(9, land, m, NxButton.RIGHT))        // tools row end: switch tab
        assertNull(homeStep(0, land, m, NxButton.LEFT))
    }
    @Test fun leftGoesAlongRowsAndFromTheGamesToContinue() {
        assertEquals(3, homeStep(4, land, m, NxButton.LEFT))
        assertEquals(7, homeStep(8, land, m, NxButton.LEFT))
        assertNull(homeStep(3, land, m, NxButton.LEFT))         // row start: switch tab
        assertNull(homeStep(7, land, m, NxButton.LEFT))         // tools row start: switch tab
        assertEquals(0, homeStep(1, land, m, NxButton.LEFT)); assertEquals(0, homeStep(2, land, m, NxButton.LEFT))
    }
    @Test fun comesBackToTheTileItLeft() {
        val cont = homeRemember(m, from = 2, to = 0, shape = land)   // LEFT from G2 to Continue
        assertEquals(2, homeStep(0, land, cont, NxButton.RIGHT))
    }
    @Test fun downGoesFromTopToPinsThenToolsBand() {
        assertEquals(3, homeStep(0, land, m, NxButton.DOWN))   // Continue → first row game under it
        assertEquals(2, homeStep(1, land, m, NxButton.DOWN))   // G1 → G2, under it
        assertEquals(4, homeStep(2, land, m, NxButton.DOWN))   // G2 (574 … 922) → first row game it overlaps (pin 1: 337.33 … 622.67)
        assertEquals(6, homeStep(3, land, m, NxButton.DOWN))   // row 0 → row 1 of pins
        assertEquals(6, homeStep(4, land, m, NxButton.DOWN))   // shorter last pin row takes its last
        assertEquals(7, homeStep(6, land, m, NxButton.DOWN))   // pins' last row → tools' first row (same column)
        assertEquals(10, homeStep(7, land, m, NxButton.DOWN))  // tools' row 0 → tools' row 1
        assertEquals(10, homeStep(10, land, m, NxButton.DOWN)) // last band stays
        assertEquals(6, homeStep(7, land, m, NxButton.UP))     // tools' row 0 → back to pins' last row
        assertEquals(6, homeStep(9, land, m, NxButton.UP))     // tools' col 2 → pins' last row (only col 0)
        assertEquals(7, homeStep(10, land, m, NxButton.UP))    // tools' row 1 → tools' row 0
    }
    @Test fun downTakesTheLeftPinAStraddlingTileOverlaps() {
        val s = HomeShape(listOf(HomeRect(80f, 0f, 60f, 60f)), listOf(HomeRect(0f, 70f, 100f, 50f), HomeRect(110f, 70f, 100f, 50f)))
        assertEquals(1, homeStep(0, s, m, NxButton.DOWN))   // over both pins (more of the right one): the left one
    }
    @Test fun downFromATileOverNoPinTakesTheNearestByCentre() {
        // Pins 0…100 and 110…210; the tile 300…340 overlaps neither, its centre (320) nearest pin 1's (160).
        val s = HomeShape(listOf(HomeRect(300f, 0f, 40f, 60f)), listOf(HomeRect(0f, 70f, 100f, 50f), HomeRect(110f, 70f, 100f, 50f)))
        assertEquals(2, homeStep(0, s, m, NxButton.DOWN))
    }
    @Test fun upFromTheTopFindsNothingAbove() {
        assertTrue(homeAtTop(0, land, m)); assertTrue(homeAtTop(1, land, m))
        assertTrue(!homeAtTop(2, land, m))                     // G2: G1 above it
        assertTrue(!homeAtTop(3, land, m))                     // row game: top section above it
        assertEquals(1, homeStep(2, land, m, NxButton.UP))
    }
    @Test fun pinsMoveInRows() {
        assertEquals(4, homeStep(3, land, m, NxButton.RIGHT))
        assertNull(homeStep(5, land, m, NxButton.RIGHT))
        assertEquals(6, homeStep(3, land, m, NxButton.DOWN))
        assertEquals(6, homeStep(4, land, m, NxButton.DOWN))   // onto the shorter last row: its last
        assertEquals(1, homeStep(3, land, HomeMemory(lastTop = 1), NxButton.UP))   // to the top tile last on
    }
    @Test fun shorterLastRowTakesItsLast() {
        val s = homeShape(homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 3))   // pins 1, 2 / 3
        assertEquals(3, homeStep(2, s, m, NxButton.DOWN))
    }
    // The Fold cover with 7 tools and 4 games: Continue 0 across the page, then pins 1 2 / 3 4 (2 a row), then all 7
    // tools 5 6 / 7 8 / 9 10 / 11 (2 a row, no "+N").
    @Test fun portraitToolsBandSitsAfterPinnedGames() {
        val s = homeShape(homeBoard(HomeFrame(475f, 291f, true), 2, tools = 7, games = 4))
        assertEquals(1, s.topCount)
        assertEquals(listOf(HomeBand(0, 4, 2), HomeBand(4, 7, 2)), s.bands)
        assertEquals(1, homeStep(0, s, m, NxButton.DOWN))
        assertEquals(0, homeStep(1, s, m, NxButton.UP))
        assertEquals(5, homeStep(3, s, m, NxButton.DOWN)); assertEquals(6, homeStep(4, s, m, NxButton.DOWN))
        assertEquals(3, homeStep(5, s, m, NxButton.UP)); assertEquals(4, homeStep(6, s, m, NxButton.UP))
        assertEquals(6, homeStep(5, s, m, NxButton.RIGHT)); assertNull(homeStep(6, s, m, NxButton.RIGHT)); assertNull(homeStep(5, s, m, NxButton.LEFT))
        assertEquals(7, homeStep(5, s, m, NxButton.DOWN)); assertEquals(11, homeStep(9, s, m, NxButton.DOWN))
        assertEquals(11, homeStep(10, s, m, NxButton.DOWN))    // shorter last tool row: its last
        assertEquals(11, homeStep(11, s, m, NxButton.DOWN))
        assertTrue(homeAtTop(0, s, m)); assertTrue(!homeAtTop(1, s, m))
    }
    // All tools fit in their band (no "+N" overflow): 10 tools produce 5 rows of 2 on the Fold cover.
    @Test fun portraitShowsAllToolsWithoutMoreOverflow() {
        val b = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 10, games = 4)
        val s = homeShape(b)
        assertEquals(1, s.topCount); assertEquals(0, b.more); assertEquals(10, b.toolTiles.size)
        assertEquals(listOf(HomeBand(0, 4, 2), HomeBand(4, 10, 2)), s.bands)
    }
    @Test fun clampKeepsTheSection() {
        val fewer = homeShape(homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 4, games = 6))   // top 0..2, pins 3..6, tools 7..10
        assertEquals(10, homeClamp(12, land, fewer))
        val noTools = homeShape(homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 6)) // top 0..2, pins 3..6
        assertEquals(6, homeClamp(10, land, noTools))           // removed tools clamp to the last row tile
        assertEquals(noTools.topCount, homeClamp(3, land, noTools))   // the first pin stays the first pin
    }
}
