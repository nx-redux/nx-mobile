package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeNavTest {
    private val land = homeShape(homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 6))
    private val m = HomeMemory()

    // Landscape, 6 tools, 6 games (the wide face): Continue 0, the inline games G1 1 (top, y 28–175) and G2 2 (189–336),
    // stacked between Continue and the block, the tool tiles 3 4 5 / 6 7 8 (3 columns of 2 rows, in reading order; rows
    // at 28 and 143.6, 107.6 tall), then the other 4 games as rows 9 10 11 / 12.
    @Test fun rightGoesThroughTheGamesIntoTheBlockAtItsTopLeft() {
        assertEquals(1, homeStep(0, land, m, NxButton.RIGHT))   // a tie (G1 and G2): the top one
        assertEquals(3, homeStep(1, land, m, NxButton.RIGHT))   // a tie (3 and 6 overlap G1): the top one
        assertEquals(4, homeStep(3, land, m, NxButton.RIGHT)); assertEquals(5, homeStep(4, land, m, NxButton.RIGHT))
        assertNull(homeStep(5, land, m, NxButton.RIGHT))        // the right edge: switch tab
        assertNull(homeStep(0, land, m, NxButton.LEFT))
    }
    @Test fun leftFromTheLeftColumnIsTheInlineGame() {
        assertEquals(1, homeStep(3, land, m, NxButton.LEFT))
        assertEquals(1, homeStep(6, land, m, NxButton.LEFT))   // over both games' edges: the top one
        assertEquals(3, homeStep(4, land, m, NxButton.LEFT))
        assertEquals(7, homeStep(8, land, m, NxButton.LEFT))
        assertEquals(0, homeStep(1, land, m, NxButton.LEFT)); assertEquals(0, homeStep(2, land, m, NxButton.LEFT))
    }
    @Test fun comesBackToTheTileItLeft() {
        val left = homeRemember(m, from = 6, to = 1, shape = land)   // LEFT from the bottom-left tool to G1
        assertEquals(6, homeStep(1, land, left, NxButton.RIGHT))
        val cont = homeRemember(m, from = 2, to = 0, shape = land)   // LEFT from G2 to Continue
        assertEquals(2, homeStep(0, land, cont, NxButton.RIGHT))
    }
    @Test fun toolsMoveInTheirBlock() {
        assertEquals(6, homeStep(3, land, m, NxButton.DOWN))
        assertEquals(7, homeStep(4, land, m, NxButton.DOWN))
        assertEquals(3, homeStep(6, land, m, NxButton.UP))
        assertEquals(11, homeStep(8, land, m, NxButton.DOWN))   // nothing under the last tool: the pin under it
    }
    @Test fun downLeavesForTheFirstPinUnderTheTile() {
        assertEquals(land.topCount + 0, homeStep(0, land, m, NxButton.DOWN))   // the wide Continue starts over the first pin
        assertEquals(2, homeStep(1, land, m, NxButton.DOWN))   // G1 → G2, under it
        assertEquals(10, homeStep(2, land, m, NxButton.DOWN))  // G2 (412 … 652) starts over the row's second pin
        assertEquals(11, homeStep(6, land, m, NxButton.DOWN))  // the tools sit over the last pin of the row
        assertEquals(11, homeStep(8, land, m, NxButton.DOWN))
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
        assertTrue(homeAtTop(0, land, m)); assertTrue(homeAtTop(1, land, m)); assertTrue(homeAtTop(3, land, m)); assertTrue(homeAtTop(4, land, m))
        assertEquals(1, homeStep(2, land, m, NxButton.UP))     // G2: G1 above it
    }
    @Test fun pinsMoveInRows() {
        assertEquals(10, homeStep(9, land, m, NxButton.RIGHT))
        assertNull(homeStep(11, land, m, NxButton.RIGHT))
        assertEquals(12, homeStep(9, land, m, NxButton.DOWN))
        assertEquals(12, homeStep(10, land, m, NxButton.DOWN))   // onto the shorter last row: its last
        assertEquals(12, homeStep(12, land, m, NxButton.DOWN))   // the last row: stays
        assertEquals(1, homeStep(9, land, HomeMemory(lastTop = 1), NxButton.UP))   // to the top tile last on
    }
    @Test fun shorterLastRowTakesItsLast() {
        val s = homeShape(homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 3))   // pins 1, 2 / 3
        assertEquals(3, homeStep(2, s, m, NxButton.DOWN))
    }
    // The Fold cover with 7 tools: Continue 0 across the page, the tools under it in rows of 4, 1 2 3 4 / 5 6 7, then
    // pins 8 9 / 10 11. DOWN from Continue is the first tool (the nearest row under it, its left tile); UP from a tool in
    // the first row is Continue; DOWN from the second row goes to the pin under it (5 over the first pin, 7 over the
    // second), and from a first-row tool with nothing under it (4) too; UP from the first pin, the top tile last on.
    @Test fun portraitToolRowsSitBetweenContinueAndThePins() {
        val s = homeShape(homeBoard(HomeFrame(475f, 291f, true), 2, tools = 7, games = 4))
        assertEquals(8, s.topCount)
        assertEquals(1, homeStep(0, s, m, NxButton.DOWN))
        assertEquals(0, homeStep(1, s, m, NxButton.UP)); assertEquals(0, homeStep(4, s, m, NxButton.UP))
        assertEquals(2, homeStep(1, s, m, NxButton.RIGHT)); assertNull(homeStep(4, s, m, NxButton.RIGHT)); assertNull(homeStep(1, s, m, NxButton.LEFT))
        assertEquals(5, homeStep(1, s, m, NxButton.DOWN)); assertEquals(1, homeStep(5, s, m, NxButton.UP))
        assertEquals(8, homeStep(5, s, m, NxButton.DOWN)); assertEquals(9, homeStep(7, s, m, NxButton.DOWN))
        assertEquals(9, homeStep(4, s, m, NxButton.DOWN))
        assertEquals(0, homeStep(8, s, m, NxButton.UP)); assertEquals(5, homeStep(8, s, HomeMemory(lastTop = 5), NxButton.UP))
        assertTrue(homeAtTop(0, s, m)); assertTrue(!homeAtTop(1, s, m))
    }
    // Past 8 tools the last tile, "+N", sits at the end of the second row: RIGHT from the tool before it, DOWN from it to
    // the pin under it.
    @Test fun portraitMoreIsTheLastTile() {
        val b = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 10, games = 4)
        val s = homeShape(b)
        assertEquals(HomeTopKind.MORE, b.top[8].kind); assertEquals(9, s.topCount)
        assertEquals(8, homeStep(7, s, m, NxButton.RIGHT)); assertEquals(4, homeStep(8, s, m, NxButton.UP))
        assertEquals(10, homeStep(8, s, m, NxButton.DOWN))
    }
    @Test fun clampKeepsTheSection() {
        val fewer = homeShape(homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 4, games = 6))   // G1 G2, tools 3…6
        assertEquals(6, homeClamp(8, land, fewer))
        val noTools = homeShape(homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 6))   // Continue, G1 G2
        assertEquals(2, homeClamp(5, land, noTools))       // past the top section: its last tile
        assertEquals(noTools.topCount, homeClamp(9, land, noTools))   // the first pin stays the first pin
    }
}
