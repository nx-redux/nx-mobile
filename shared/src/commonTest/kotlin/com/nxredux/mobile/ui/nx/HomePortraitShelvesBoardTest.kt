package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** Grid Home on the portrait type shows the shelves under the pin rows, as the large face does. */
class HomePortraitShelvesBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private val cover = HomeFrame(475f, 291f, true)
    private val three = listOf(HomeShelf.NEW to 3, HomeShelf.NOT_STARTED to 3, HomeShelf.PICK_UP to 2)

    // The board's text before shelves came to the portrait type (HEAD a509aed4), the new shelfCols field left out:
    // without shelves it must not move by a hair.
    private val goldens = listOf(
        Triple(HomeFrame(475f, 291f, true), 2, 4 to 5) to "HomeBoard(mode=COLUMN, wide=false, stripLines=2, topY=48.0, topH=235.0, top=[HomeTopTile(kind=CONTINUE, rect=HomeRect(x=16.0, y=48.0, w=388.8, h=235.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=416.8, y=48.0, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=416.8, y=96.2, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=416.8, y=144.4, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=416.8, y=192.6, w=42.2, h=42.2))], slots=5, more=0, gutter=16.0, gap=12.0, perRow=2, pinW=215.5, pinH=114.46875, tilesY=295.0, rowGames=5, pageH=673.40625, square=42.2, stripY=-1.4750009, labels=[], shelfTiles=[], shelfSizes=[], inlineGames=0, labelH=18.0)",
        Triple(HomeFrame(400f, 700f, true), 2, 3 to 3) to "HomeBoard(mode=COLUMN, wide=false, stripLines=2, topY=48.0, topH=235.0, top=[HomeTopTile(kind=CONTINUE, rect=HomeRect(x=16.0, y=48.0, w=313.8, h=235.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=341.8, y=48.0, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=341.8, y=96.2, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=341.8, y=144.4, w=42.2, h=42.2))], slots=5, more=0, gutter=16.0, gap=12.0, perRow=2, pinW=178.0, pinH=129.0, tilesY=295.0, rowGames=3, pageH=576.0, square=42.2, stripY=-1.4750009, labels=[], shelfTiles=[], shelfSizes=[], inlineGames=0, labelH=18.0)",
        Triple(HomeFrame(704f, 801f, true, textScale = 1.3f), 2, 8 to 0) to "HomeBoard(mode=COLUMN, wide=false, stripLines=2, topY=58.8, topH=235.0, top=[HomeTopTile(kind=CONTINUE, rect=HomeRect(x=16.0, y=58.8, w=563.6, h=235.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=645.8, y=58.8, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=645.8, y=107.0, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=645.8, y=155.2, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=645.8, y=203.40001, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=645.8, y=251.6, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=591.6, y=58.8, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=591.6, y=107.0, w=42.2, h=42.2)), HomeTopTile(kind=TOOL, rect=HomeRect(x=591.6, y=155.2, w=42.2, h=42.2))], slots=10, more=0, gutter=16.0, gap=12.0, perRow=2, pinW=330.0, pinH=178.875, tilesY=305.8, rowGames=0, pageH=301.8, square=42.2, stripY=-1.6148, labels=[], shelfTiles=[], shelfSizes=[], inlineGames=0, labelH=18.0)",
        Triple(HomeFrame(475f, 335f, true, titleHidden = true, statsHidden = true), 2, 12 to 7) to "HomeBoard(mode=COLUMN, wide=false, stripLines=0, topY=8.0, topH=319.0, top=[HomeTopTile(kind=CONTINUE, rect=HomeRect(x=16.0, y=8.0, w=301.0, h=319.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=400.0, y=8.0, w=59.0, h=59.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=400.0, y=73.0, w=59.0, h=59.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=400.0, y=138.0, w=59.0, h=59.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=400.0, y=203.0, w=59.0, h=59.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=400.0, y=268.0, w=59.0, h=59.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=329.0, y=8.0, w=59.0, h=59.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=329.0, y=73.0, w=59.0, h=59.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=329.0, y=138.0, w=59.0, h=59.0)), HomeTopTile(kind=TOOL, rect=HomeRect(x=329.0, y=203.0, w=59.0, h=59.0)), HomeTopTile(kind=MORE, rect=HomeRect(x=329.0, y=268.0, w=59.0, h=59.0))], slots=10, more=3, gutter=16.0, gap=12.0, perRow=2, pinW=215.5, pinH=114.46875, tilesY=339.0, rowGames=7, pageH=843.875, square=59.0, stripY=1.8759999, labels=[], shelfTiles=[], shelfSizes=[], inlineGames=0, labelH=18.0)",
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

    // The Fold cover (475 × 291, 2 lines): the top 48 … 283. The pins unlabelled, a gap under it at 295,
    // 2 a row, 215.5 × 114.47; 3 rows end at 295 + 3 · 114.47 + 24 = 662.41.
    @Test fun pinsGoUnderTheTopUnlabelled() {
        val b = homeBoard(cover, 2, tools = 4, games = 5, shelves = three)
        assertEquals(HomeToolMode.COLUMN, b.mode)
        assertEquals(listOf("New", "Not started", "Pick up again"), b.labels.map { it.text })
        near(295f, b.tilesY, "tilesY")
        assertEquals(2, b.perRow); near(215.5f, b.pinW, "pinW"); near(114.47f, b.pinH, "pinH")
        near(18f, b.labelH, "labelH")
    }

    // New: label 674.41, tiles at 698.41, 2 a row at the pins' size; the third on a second row (126.47 lower).
    // Not started: label 951.34, tiles from 975.34, 2 a row at the pins' size like the rest (its third on a second row at
    // 1101.81). Pick up again: label 1228.28, its 2 tiles one row at 1252.28. pageH: the last tile's bottom 1366.75 + 3 + 8.
    @Test fun shelvesFollowThePinsInThePortraitGrid() {
        val b = homeBoard(cover, 2, tools = 4, games = 5, shelves = three)
        assertEquals(listOf(3, 3, 2), b.shelfSizes); assertEquals(8, b.shelfTiles.size)
        near(674.41f, b.labels[0].y, "New label")
        val n = (5..7).map { homeBoardTile(b, it) }
        assertEquals(listOf(16f, 243.5f, 16f), n.map { it.x }); near(698.41f, n[0].y, "New 0 y"); near(698.41f, n[1].y, "New 1 y")
        near(824.88f, n[2].y, "New 2 y")
        for (r in n) { near(215.5f, r.w, "New w"); near(114.47f, r.h, "New h") }
        near(951.34f, b.labels[1].y, "Not started label")
        for (k in 0..2) {
            val t = homeBoardTile(b, 8 + k)
            near(if (k == 1) 243.5f else 16f, t.x, "Not started $k x"); near(215.5f, t.w, "Not started $k w"); near(114.47f, t.h, "Not started $k h")
            near(975.34f + (k / 2) * 126.47f, t.y, "Not started $k y")
        }
        near(1228.28f, b.labels[2].y, "Pick up label")
        val p = (11..12).map { homeBoardTile(b, it) }
        assertEquals(listOf(16f, 243.5f), p.map { it.x }); for (r in p) near(1252.28f, r.y, "Pick up y")
        near(1252.28f + 114.47f + 3f + 8f, b.pageH, "pageH")
    }

    // No pins: no Pinned label; the first shelf's label sits a gap under the top.
    @Test fun noPinsNoPinnedLabel() {
        val b = homeBoard(cover, 2, tools = 4, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 2))
        assertEquals(listOf(HomeLabel("Not started", 295f)), b.labels)
        near(319f, homeBoardTile(b, 0).y, "first shelf tile y"); near(215.5f, homeBoardTile(b, 0).w, "the pins' width")
    }

    // A shelf offered more holds 4 in portrait ([homeShelfCap]): two rows of 2 from 319, the second 114.47 + 12 lower; a
    // landscape phone's row of 3 holds 3.
    @Test fun portraitShelvesHoldFourInTwoRows() {
        val b = homeBoard(cover, 2, tools = 4, games = 0, shelves = listOf(HomeShelf.NEW to 6, HomeShelf.NOT_STARTED to 4))
        assertEquals(listOf(4, 4), b.shelfSizes); assertEquals(listOf(2, 2), b.shelfCols)
        val n = (0..3).map { homeBoardTile(b, it) }
        assertEquals(listOf(16f, 243.5f, 16f, 243.5f), n.map { it.x })
        for (k in 0..3) near(319f + (k / 2) * 126.47f, n[k].y, "New $k y")
        val wide = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, 4, 5, shelves = listOf(HomeShelf.NEW to 6))
        assertEquals(3, wide.perRow); assertEquals(listOf(3), wide.shelfSizes)
    }

    // A first-row tile of a shelf is handed to the scroll with its label; a second row is not.
    @Test fun firstRowTilesScrollWithTheirLabel() {
        val b = homeBoard(cover, 2, tools = 4, games = 5, shelves = three)
        assertEquals(homeBoardTile(b, 0), homeScrollRect(b, 0))           // the pins have no label
        assertEquals(homeBoardTile(b, 2), homeScrollRect(b, 2))
        val n0 = homeBoardTile(b, 5)
        assertEquals(HomeRect(n0.x, n0.y - 24f, n0.w, n0.h + 24f), homeScrollRect(b, 5))
        val n1 = homeBoardTile(b, 6)
        assertEquals(HomeRect(n1.x, n1.y - 24f, n1.w, n1.h + 24f), homeScrollRect(b, 6))
        assertEquals(homeBoardTile(b, 7), homeScrollRect(b, 7))           // New's second row
        val s1 = homeBoardTile(b, 9)
        assertEquals(HomeRect(s1.x, s1.y - 24f, s1.w, s1.h + 24f), homeScrollRect(b, 9))   // Not started's second tile: its first row
        assertEquals(homeBoardTile(b, 10), homeScrollRect(b, 10))         // Not started's second row
        // Without shelves the portrait pins scroll as they did: no label room.
        val plain = homeBoard(cover, 2, tools = 4, games = 5)
        assertEquals(homeBoardPin(plain, 0), homeScrollRect(plain, 0))
    }

    // Landscape faces that are not large show the shelves too, in the pins' grid (perRow columns, the pins' tile size,
    // the pins unlabelled).
    @Test fun landscapePhonesShowShelves() {
        for (f in listOf(HomeFrame(960f, 344f, false, 960f / 432f), HomeFrame(800f, 340f, false, 800f / 428f), HomeFrame(399f, 298f, false, 399f / 386f))) {
            val b = homeBoard(f, 1, 4, 5, shelves = three)
            assertEquals(listOf("New", "Not started", "Pick up again"), b.labels.map { it.text }, "$f")
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
        val top = s.topCount   // Continue and 4 squares: 5
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
