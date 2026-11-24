package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** Grid Home on the portrait type shows the shelves under the pin rows, as the large face does. */
class HomePortraitShelvesBoardTest {
    private val cover = HomeFrame(475f, 291f, true)
    private val three = listOf(HomeShelf.NEW to 3, HomeShelf.NOT_STARTED to 3, HomeShelf.PICK_UP to 2)

    // An empty shelf list or an empty shelf leaves the board unchanged.
    @Test fun withoutShelvesTheBoardIsUnchangedByEmptyShelves() {
        val inputs = listOf(
            Triple(HomeFrame(475f, 291f, true), 2, 4 to 5),
            Triple(HomeFrame(400f, 700f, true), 2, 3 to 3),
            Triple(HomeFrame(704f, 801f, true, textScale = 1.3f), 2, 8 to 0),
            Triple(HomeFrame(475f, 335f, true, titleHidden = true, statsHidden = true), 2, 12 to 7),
        )
        for ((f, lines, tg) in inputs) {
            val base = homeBoard(f, lines, tg.first, tg.second)
            val emptyShelf = homeBoard(f, lines, tg.first, tg.second, listOf(HomeShelf.NEW to 0))
            assertEquals(base, emptyShelf, "$f")
            assertEquals(tg.first, base.toolTiles.size, "$f tool tiles")
        }
    }

    // The Fold cover (475 × 291, 2 lines) with 4 tools: Continue fills the top (48 .. 283). The pins start a gap
    // under it at 295, 2 a row, 201.5 × 106.59; then 4 tools in 2 rows of 2 below them; then the shelves.
    @Test fun pinsGoUnderTheTop() {
        val b = homeBoard(cover, 2, tools = 4, games = 5, shelves = three)
        assertEquals(HomeToolMode.COLUMN, b.mode)
        assertEquals(3, b.shelfSizes.size)
        near(295f, b.tilesY, "tilesY")
        assertEquals(2, b.perRow); near(201.5f, b.pinW, "pinW"); near(106.59f, b.pinH, "pinH")
        assertEquals(4, b.toolTiles.size)
    }

    // After 5 pins (3 rows) and 4 tools (2 rows): New, Not started, Pick up again follow in order, 2 a row.
    @Test fun shelvesFollowThePinsAndToolsInThePortraitGrid() {
        val b = homeBoard(cover, 2, tools = 4, games = 5, shelves = three)
        assertEquals(listOf(3, 3, 2), b.shelfSizes); assertEquals(8, b.shelfTiles.size)
        val lastTool = b.toolTiles.last()
        val n0y = lastTool.y + lastTool.h + 12f
        val n = (9..11).map { homeBoardTile(b, it) }
        assertEquals(listOf(30f, 243.5f, 30f), n.map { it.x }); near(n0y, n[0].y, "New 0 y"); near(n0y, n[1].y, "New 1 y")
        near(n0y + b.pinH + 12f, n[2].y, "New 2 y")
        for (r in n) { near(201.5f, r.w, "New w"); near(106.59f, r.h, "New h") }
        val ns0y = n[2].y + b.pinH + 12f
        for (k in 0..2) {
            val t = homeBoardTile(b, 12 + k)
            near(if (k == 1) 243.5f else 30f, t.x, "Not started $k x"); near(201.5f, t.w, "Not started $k w"); near(106.59f, t.h, "Not started $k h")
            near(ns0y + (k / 2) * (b.pinH + 12f), t.y, "Not started $k y")
        }
        val p0y = ns0y + 2 * b.pinH + 2 * 12f
        val p = (15..16).map { homeBoardTile(b, it) }
        assertEquals(listOf(30f, 243.5f), p.map { it.x }); for (r in p) near(p0y, r.y, "Pick up y")
        near(p0y + b.pinH + 3f + 8f, b.pageH, "pageH")
    }

    // No pins: tools sit a gap under the top (295), and the first shelf starts a gap under the tools.
    @Test fun noPinsFirstShelfStartsAGapUnderTools() {
        val b = homeBoard(cover, 2, tools = 4, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 2))
        near(295f, homeBoardTile(b, 0).y, "first tool y")
        val lastTool = b.toolTiles.last()
        near(lastTool.y + lastTool.h + 12f, homeBoardTile(b, 4).y, "first shelf tile y"); near(201.5f, homeBoardTile(b, 4).w, "the pins' width")
    }

    // A shelf offered more holds 4 in portrait ([homeShelfCap]): two rows of 2 under the tools; a
    // landscape phone's row of 3 holds 3.
    @Test fun portraitShelvesHoldFourInTwoRows() {
        val b = homeBoard(cover, 2, tools = 4, games = 0, shelves = listOf(HomeShelf.NEW to 6, HomeShelf.NOT_STARTED to 4))
        assertEquals(listOf(4, 4), b.shelfSizes); assertEquals(listOf(2, 2), b.shelfCols)
        val n0y = b.toolTiles.last().y + b.toolTiles.last().h + 12f
        val n = (4..7).map { homeBoardTile(b, it) }
        assertEquals(listOf(30f, 243.5f, 30f, 243.5f), n.map { it.x })
        for (k in 0..3) near(n0y + (k / 2) * (b.pinH + 12f), n[k].y, "New $k y")
        val wide = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, 4, 5, shelves = listOf(HomeShelf.NEW to 6))
        assertEquals(3, wide.perRow); assertEquals(listOf(3), wide.shelfSizes)
    }

    // Nothing reserves header room: tools start a gap under the pins, and each shelf starts a gap under the band above it.
    @Test fun shelvesStartAGapUnderWhatIsAbove() {
        val b = homeBoard(cover, 2, tools = 4, games = 5, shelves = three)
        val pin = homeBoardTile(b, 4)                                      // the last pin row's one pin
        near(pin.y + pin.h + b.gap, homeBoardTile(b, 5).y, "Tools under the pins")
        val tool = homeBoardTile(b, 8)                                     // the last tool row
        near(tool.y + tool.h + b.gap, homeBoardTile(b, 9).y, "New under the tools")
        val n = homeBoardTile(b, 11)                                       // New's second row
        near(n.y + n.h + b.gap, homeBoardTile(b, 12).y, "Not started under New")
        val s = homeBoardTile(b, 14)                                       // Not started's second row
        near(s.y + s.h + b.gap, homeBoardTile(b, 15).y, "Pick up again under Not started")
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

    // The bands (pins, tools, shelves) move as grids of their own: 2 a row, every band.
    @Test fun navBandsFollowThePortraitColumns() {
        val s = homeShape(homeBoard(cover, 2, tools = 4, games = 5, shelves = three))
        assertEquals(listOf(HomeBand(0, 5, 2), HomeBand(5, 4, 2), HomeBand(9, 3, 2), HomeBand(12, 3, 2), HomeBand(15, 2, 2)), s.bands)
        val m = HomeMemory()
        val top = s.topCount   // Continue alone: 1
        assertEquals(top + 5, homeStep(top + 4, s, m, NxButton.DOWN))          // the last pin row → Tool 0
        assertEquals(top + 4, homeStep(top + 5, s, m, NxButton.UP))            // Tool 0 → back to the pins' last row
        assertEquals(top + 4, homeStep(top + 6, s, m, NxButton.UP))            // Tool 1 → the pins' last row (its one pin)
        assertEquals(top + 7, homeStep(top + 5, s, m, NxButton.DOWN))          // Tool's second row
        assertEquals(top + 9, homeStep(top + 7, s, m, NxButton.DOWN))          // Tool 2 → New 0
        assertEquals(top + 7, homeStep(top + 9, s, m, NxButton.UP))            // New 0 → Tool 2
        assertEquals(top + 11, homeStep(top + 9, s, m, NxButton.DOWN))         // New's second row
        assertEquals(top + 12, homeStep(top + 11, s, m, NxButton.DOWN))        // → Not started 0
        assertEquals(top + 13, homeStep(top + 12, s, m, NxButton.RIGHT))       // Not started: 2 a row
        assertEquals(top + 14, homeStep(top + 12, s, m, NxButton.DOWN))        // its second row
        assertEquals(top + 15, homeStep(top + 14, s, m, NxButton.DOWN))        // → Pick up 0
        assertEquals(top + 14, homeStep(top + 16, s, m, NxButton.UP))          // Pick up 1 → Not started's second row (its one tile)
    }
}
