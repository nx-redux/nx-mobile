package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** Grid Home on the portrait type shows the shelves under the pin rows, as the large face does. */
class HomePortraitShelvesBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private val cover = HomeFrame(475f, 291f, true)
    private val three = listOf(HomeShelf.NEW to 3, HomeShelf.NOT_STARTED to 3, HomeShelf.PICK_UP to 2)

    // The board's text without shelves (the shelfCols field left out), its x and widths on the page inset (30 dp a side
    // upright), the tools the Tools page's tiles in rows under Continue: an empty shelf list or an empty shelf must not
    // move it by a hair.
    private val goldens = listOf(
        Triple(HomeFrame(475f, 291f, true), 2, 4 to 5) to "HomeBoard(mode=COLUMN, wide=false, stripLines=2, topY=48.0, topH=275.25, top=[HomeTopTile(kind=CONTINUE, rect=HomeRect(x=30.0, y=48.0, w=415.0, h=145.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=30.0, y=205.0, w=97.75, h=118.25)), HomeTopTile(kind=TOOL, rect=HomeRect(x=135.75, y=205.0, w=97.75, h=118.25)), HomeTopTile(kind=TOOL, rect=HomeRect(x=241.5, y=205.0, w=97.75, h=118.25)), HomeTopTile(kind=TOOL, rect=HomeRect(x=347.25, y=205.0, w=97.75, h=118.25))], slots=8, more=0, gutter=30.0, gap=12.0, perRow=2, pinW=201.5, pinH=106.59375, tilesY=335.25, rowGames=5, pageH=690.03125, toolW=97.75, toolH=118.25, stripY=-1.4750009, shelfTiles=[], shelfSizes=[], inlineGames=0)",
        Triple(HomeFrame(400f, 700f, true), 2, 3 to 3) to "HomeBoard(mode=COLUMN, wide=false, stripLines=2, topY=48.0, topH=280.6, top=[HomeTopTile(kind=CONTINUE, rect=HomeRect(x=30.0, y=48.0, w=340.0, h=145.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=30.0, y=205.0, w=108.0, h=123.6)), HomeTopTile(kind=TOOL, rect=HomeRect(x=146.0, y=205.0, w=108.0, h=123.6)), HomeTopTile(kind=TOOL, rect=HomeRect(x=262.0, y=205.0, w=108.0, h=123.6))], slots=6, more=0, gutter=30.0, gap=12.0, perRow=2, pinW=164.0, pinH=118.5, tilesY=340.6, rowGames=3, pageH=600.6, toolW=108.0, toolH=123.6, stripY=-1.4750009, shelfTiles=[], shelfSizes=[], inlineGames=0)",
        Triple(HomeFrame(704f, 801f, true, textScale = 1.3f), 2, 8 to 0) to "HomeBoard(mode=COLUMN, wide=false, stripLines=2, topY=58.8, topH=402.93146, top=[HomeTopTile(kind=CONTINUE, rect=HomeRect(x=30.0, y=58.8, w=644.0, h=145.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=30.0, y=215.8, w=85.14286, h=118.96572)), HomeTopTile(kind=TOOL, rect=HomeRect(x=123.14286, y=215.8, w=85.14286, h=118.96572)), HomeTopTile(kind=TOOL, rect=HomeRect(x=216.28572, y=215.8, w=85.14286, h=118.96572)), HomeTopTile(kind=TOOL, rect=HomeRect(x=309.4286, y=215.8, w=85.14286, h=118.96572)), HomeTopTile(kind=TOOL, rect=HomeRect(x=402.57144, y=215.8, w=85.14286, h=118.96572)), HomeTopTile(kind=TOOL, rect=HomeRect(x=495.7143, y=215.8, w=85.14286, h=118.96572)), HomeTopTile(kind=TOOL, rect=HomeRect(x=588.8572, y=215.8, w=85.14286, h=118.96572)), HomeTopTile(kind=TOOL, rect=HomeRect(x=30.0, y=342.76572, w=85.14286, h=118.96572))], slots=14, more=0, gutter=30.0, gap=12.0, perRow=2, pinW=316.0, pinH=171.0, tilesY=473.73145, rowGames=0, pageH=469.73145, toolW=85.14286, toolH=118.96572, stripY=-1.6148, shelfTiles=[], shelfSizes=[], inlineGames=0)",
        Triple(HomeFrame(475f, 335f, true, titleHidden = true, statsHidden = true), 2, 12 to 7) to "HomeBoard(mode=COLUMN, wide=false, stripLines=0, topY=8.0, topH=401.5, top=[HomeTopTile(kind=CONTINUE, rect=HomeRect(x=30.0, y=8.0, w=415.0, h=145.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=30.0, y=165.0, w=97.75, h=118.25)), HomeTopTile(kind=TOOL, rect=HomeRect(x=135.75, y=165.0, w=97.75, h=118.25)), HomeTopTile(kind=TOOL, rect=HomeRect(x=241.5, y=165.0, w=97.75, h=118.25)), HomeTopTile(kind=TOOL, rect=HomeRect(x=347.25, y=165.0, w=97.75, h=118.25)), HomeTopTile(kind=TOOL, rect=HomeRect(x=30.0, y=291.25, w=97.75, h=118.25)), HomeTopTile(kind=TOOL, rect=HomeRect(x=135.75, y=291.25, w=97.75, h=118.25)), HomeTopTile(kind=TOOL, rect=HomeRect(x=241.5, y=291.25, w=97.75, h=118.25)), HomeTopTile(kind=MORE, rect=HomeRect(x=347.25, y=291.25, w=97.75, h=118.25))], slots=8, more=5, gutter=30.0, gap=12.0, perRow=2, pinW=201.5, pinH=106.59375, tilesY=421.5, rowGames=7, pageH=894.875, toolW=97.75, toolH=118.25, stripY=1.8759999, shelfTiles=[], shelfSizes=[], inlineGames=0)",
    )

    @Test fun withoutShelvesTheBoardIsAsBefore() {
        for ((input, golden) in goldens) {
            val (f, lines, tg) = input
            for (shelves in listOf(emptyList(), listOf(HomeShelf.NEW to 0))) {
                val b = homeBoard(f, lines, tg.first, tg.second, shelves)
                assertEquals(golden, b.toString().replace(", shelfCols=[]", ""), "$f $shelves")
            }
        }
    }

    // The Fold cover (475 × 291, 2 lines) with 4 tools: Continue at its 145 floor and one row of tiles (118.25) a gap
    // under it, the top 48 … 323.25. The pins a gap under it at 335.25, 2 a row, 201.5 × 106.59; 3 rows end at
    // 335.25 + 3 · 106.59 + 24 = 679.03.
    @Test fun pinsGoUnderTheTop() {
        val b = homeBoard(cover, 2, tools = 4, games = 5, shelves = three)
        assertEquals(HomeToolMode.COLUMN, b.mode)
        assertEquals(3, b.shelfSizes.size)
        near(335.25f, b.tilesY, "tilesY")
        assertEquals(2, b.perRow); near(201.5f, b.pinW, "pinW"); near(106.59f, b.pinH, "pinH")
    }

    // New: tiles a gap (12) under the pins' end at 679.03, so 691.03, 2 a row at the pins' size; the third on a second row
    // (118.59 lower). Not started: a gap under that, from 928.22, 2 a row like the rest (its third on a second row at
    // 1046.81). Pick up again: from 1165.41, its 2 tiles one row. pageH: the last tile's bottom 1272 + 3 + 8.
    @Test fun shelvesFollowThePinsInThePortraitGrid() {
        val b = homeBoard(cover, 2, tools = 4, games = 5, shelves = three)
        assertEquals(listOf(3, 3, 2), b.shelfSizes); assertEquals(8, b.shelfTiles.size)
        val n = (5..7).map { homeBoardTile(b, it) }
        assertEquals(listOf(30f, 243.5f, 30f), n.map { it.x }); near(691.03f, n[0].y, "New 0 y"); near(691.03f, n[1].y, "New 1 y")
        near(809.62f, n[2].y, "New 2 y")
        for (r in n) { near(201.5f, r.w, "New w"); near(106.59f, r.h, "New h") }
        for (k in 0..2) {
            val t = homeBoardTile(b, 8 + k)
            near(if (k == 1) 243.5f else 30f, t.x, "Not started $k x"); near(201.5f, t.w, "Not started $k w"); near(106.59f, t.h, "Not started $k h")
            near(928.22f + (k / 2) * 118.59f, t.y, "Not started $k y")
        }
        val p = (11..12).map { homeBoardTile(b, it) }
        assertEquals(listOf(30f, 243.5f), p.map { it.x }); for (r in p) near(1165.41f, r.y, "Pick up y")
        near(1165.41f + 106.59f + 3f + 8f, b.pageH, "pageH")
    }

    // No pins: the first shelf's tiles sit a gap under the top (with the tools' row, 48 + 275.25 + 12).
    @Test fun noPinsFirstShelfStartsAGapUnderTheTop() {
        val b = homeBoard(cover, 2, tools = 4, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 2))
        near(335.25f, homeBoardTile(b, 0).y, "first shelf tile y"); near(201.5f, homeBoardTile(b, 0).w, "the pins' width")
    }

    // A shelf offered more holds 4 in portrait ([homeShelfCap]): two rows of 2 from 335.25, the second 106.59 + 12 lower; a
    // landscape phone's row of 3 holds 3.
    @Test fun portraitShelvesHoldFourInTwoRows() {
        val b = homeBoard(cover, 2, tools = 4, games = 0, shelves = listOf(HomeShelf.NEW to 6, HomeShelf.NOT_STARTED to 4))
        assertEquals(listOf(4, 4), b.shelfSizes); assertEquals(listOf(2, 2), b.shelfCols)
        val n = (0..3).map { homeBoardTile(b, it) }
        assertEquals(listOf(30f, 243.5f, 30f, 243.5f), n.map { it.x })
        for (k in 0..3) near(335.25f + (k / 2) * 118.59f, n[k].y, "New $k y")
        val wide = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, 4, 5, shelves = listOf(HomeShelf.NEW to 6))
        assertEquals(3, wide.perRow); assertEquals(listOf(3), wide.shelfSizes)
    }

    // Nothing reserves header room: each shelf starts a gap under the last row above it, pins first.
    @Test fun shelvesStartAGapUnderWhatIsAbove() {
        val b = homeBoard(cover, 2, tools = 4, games = 5, shelves = three)
        val pin = homeBoardTile(b, 4)                                      // the last pin row's one pin
        near(pin.y + pin.h + b.gap, homeBoardTile(b, 5).y, "New under the pins")
        val n = homeBoardTile(b, 7)                                        // New's second row
        near(n.y + n.h + b.gap, homeBoardTile(b, 8).y, "Not started under New")
        val s = homeBoardTile(b, 10)                                       // Not started's second row
        near(s.y + s.h + b.gap, homeBoardTile(b, 11).y, "Pick up again under Not started")
    }

    // Landscape faces that are not large show the shelves too, in the pins' grid (perRow columns, the pins' tile size,
    // the same grid as the pins).
    @Test fun landscapePhonesShowShelves() {
        for (f in listOf(HomeFrame(960f, 344f, false, 960f / 432f), HomeFrame(800f, 340f, false, 800f / 428f), HomeFrame(399f, 298f, false, 399f / 386f))) {
            val b = homeBoard(f, 1, 4, 5, shelves = three)
            assertEquals(listOf(3, 3, 2), b.shelfSizes, "$f"); assertEquals(8, b.shelfTiles.size, "$f")
            assertEquals(List(3) { b.perRow }, b.shelfCols, "$f")
            for (r in b.shelfTiles) { near(b.pinW, r.w, "$f shelf tile w"); near(b.pinH, r.h, "$f shelf tile h") }
        }
    }

    // The shelves' bands move as a grid of their own: 2 a row, every shelf.
    @Test fun navBandsFollowThePortraitColumns() {
        val s = homeShape(homeBoard(cover, 2, tools = 4, games = 5, shelves = three))
        assertEquals(listOf(HomeBand(0, 5, 2), HomeBand(5, 3, 2), HomeBand(8, 3, 2), HomeBand(11, 2, 2)), s.bands)
        val m = HomeMemory()
        val top = s.topCount   // Continue and 4 tools: 5
        assertEquals(top + 5, homeStep(top + 4, s, m, NxButton.DOWN))          // the last pin row → New 0
        assertEquals(top + 4, homeStep(top + 5, s, m, NxButton.UP))            // New 0 → back to the pins' last row
        assertEquals(top + 4, homeStep(top + 6, s, m, NxButton.UP))            // New 1 → the pins' last row (its one pin)
        assertEquals(top + 7, homeStep(top + 5, s, m, NxButton.DOWN))          // New's second row
        assertEquals(top + 8, homeStep(top + 7, s, m, NxButton.DOWN))          // → Not started 0
        assertEquals(top + 9, homeStep(top + 8, s, m, NxButton.RIGHT))         // Not started: 2 a row
        assertEquals(top + 10, homeStep(top + 8, s, m, NxButton.DOWN))         // its second row
        assertEquals(top + 11, homeStep(top + 10, s, m, NxButton.DOWN))        // → Pick up 0
        assertEquals(top + 10, homeStep(top + 12, s, m, NxButton.UP))          // Pick up 1 → Not started's second row (its one tile)
    }
}
