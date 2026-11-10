package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLargeBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private val fold = HomeFrame(933f, 572f, false, 933f / 660f)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }
    // Shelf sizes as offered; the large face draws at most 4 a shelf (its row of 4, [homeShelfCap]). With 7 pinned games
    // the first 2 sit inline and 5 are row games (two rows).
    private val three = listOf(HomeShelf.NEW to 6, HomeShelf.NOT_STARTED to 3, HomeShelf.PICK_UP to 4)

    @Test fun whoIsLarge() {
        assertTrue(homeIsLarge(fold))
        assertFalse(homeIsLarge(HomeFrame(704f, 801f, true)))                      // unfolded portrait: COLUMN
        assertFalse(homeIsLarge(HomeFrame(960f, 344f, false, 960f / 432f)))        // 20:9 phone landscape: WIDE
        assertFalse(homeIsLarge(HomeFrame(800f, 340f, false, 800f / 428f)))        // 16:9 phone landscape: under 840
        assertFalse(homeIsLarge(HomeFrame(411f, 700f, true)))                       // phone portrait
        assertFalse(homeIsLarge(HomeFrame(900f, 1000f, false)))                     // a tall window: halves
        assertFalse(homeIsLarge(HomeFrame(846f, 324f, false, 846f / 412f)))        // wide but short (a 21:9 landscape): under 480 tall
    }
    // w 857 (933 − 2 · 38); a full square (857 / 2 − 14 − 28) / 3 = 128.83 sets Continue and the inline games,
    // 2 · 128.83 + 7 = 264.67 from topY 28: the 2 games 0.4 · (857 − 14) = 337.2 wide, Continue 505.8. A gap under them,
    // at 28 + 264.67 + 14 = 306.67, one row of the Tools page's tiles across the page: floor(865 / 88) = 9 columns of
    // (857 − 64) / 9 = 88.11, each 2 · 10 + 0.6 · 88.11 + 6 + 27.6 + 6 = 112.47 tall, so 9 slots: 6 tools from the page's
    // left, 8 dp apart. The top section 264.67 + 14 + 112.47 = 391.13.
    @Test fun topSectionIsContinueOverOneToolRow() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(HomeToolMode.LARGE, b.mode); assertEquals(9, b.slots); assertEquals(0, b.more)
        near(28f, b.topY, "topY"); near(391.13f, b.topH, "topH"); near(88.11f, b.toolW, "toolW"); near(112.47f, b.toolH, "toolH")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(38f, c.x, "continue x"); near(505.8f, c.w, "continue w"); near(264.67f, c.h, "continue h")
        val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }
        assertEquals(List(6) { HomeTopKind.TOOL }, sq.map { it.kind })
        for ((i, t) in sq.withIndex()) {
            val r = t.rect
            near(38f + i * (88.11f + 8f), r.x, "tile $i x"); near(306.67f, r.y, "tile $i y")
            near(88.11f, r.w, "tile w"); near(112.47f, r.h, "tile h")
        }
    }
    // Continue never follows the tools: without games it runs across the page (857), 264.67 tall, with tools or
    // without; the tools' row only makes the top section its gap and height taller (391.13).
    @Test fun continueSpansThePageWhateverTheTools() {
        for (tools in listOf(6, 4, 3, 2)) {
            val b = homeBoard(fold, 1, tools = tools, games = 0)
            val c = tiles(b, HomeTopKind.CONTINUE).single().rect
            near(857f, c.w, "continue w ($tools tools)"); near(264.67f, c.h, "continue h ($tools tools)"); near(391.13f, b.topH, "topH ($tools tools)")
        }
        val none = homeBoard(fold, 1, tools = 0, games = 0)
        near(857f, tiles(none, HomeTopKind.CONTINUE).single().rect.w, "continue w (no tools)")
        near(264.67f, tiles(none, HomeTopKind.CONTINUE).single().rect.h, "continue h (no tools)")
        near(264.67f, none.topH, "topH (no tools)")
    }
    // One row of 9: past 9 tools, 8 and "+N", the last tile, at the row's right end (38 + 857 − 88.11 = 806.89).
    @Test fun moreThanNineToolsShowEightAndMore() {
        val b = homeBoard(fold, 1, tools = 13, games = 0)
        assertEquals(9, b.slots); assertEquals(8, tiles(b, HomeTopKind.TOOL).size); assertEquals(1, tiles(b, HomeTopKind.MORE).size); assertEquals(5, b.more)
        assertEquals(HomeTopKind.MORE, b.top.last().kind)
        tiles(b, HomeTopKind.MORE).single().rect.let { near(806.89f, it.x, "+N: the row's right end"); near(306.67f, it.y, "+N: in the row") }
    }
    // 1 pinned game sits inline, 0.4 · (857 − 14) = 337.2 wide, Continue's full height (264.67), at
    // 38 + 505.8 + 14 = 557.8; Continue 505.8. No pins: New's tiles start a gap under the top section (the tools' row
    // its end), at 28 + 391.13 + 14 = 433.13.
    @Test fun oneGameSitsInline() {
        val b = homeBoard(fold, 1, tools = 4, games = 1, shelves = listOf(HomeShelf.NEW to 6))
        assertEquals(1, b.inlineGames); assertEquals(0, b.rowGames)
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME) + List(4) { HomeTopKind.TOOL }, b.top.map { it.kind })
        near(505.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        val g = tiles(b, HomeTopKind.GAME).single().rect
        near(557.8f, g.x, "game x"); near(28f, g.y, "game y"); near(337.2f, g.w, "game w"); near(264.67f, g.h, "game h")
        near(433.13f, homeBoardTile(b, 0).y, "New's tiles")
    }
    // 2 games: that column, stacked, each (264.67 − 14) / 2 = 125.33 tall, the second at 28 + 139.33. Nothing under
    // the tools' row: the page ends a margin under the top section, 28 + 391.13 + 8.
    @Test fun twoGamesStackInline() {
        val b = homeBoard(fold, 1, tools = 6, games = 2)
        assertEquals(2, b.inlineGames); assertEquals(0, b.rowGames)
        val g = tiles(b, HomeTopKind.GAME).map { it.rect }
        near(28f, g[0].y, "game 0 y"); near(167.33f, g[1].y, "game 1 y")
        for (r in g) { near(557.8f, r.x, "game x"); near(337.2f, r.w, "game w"); near(125.33f, r.h, "game h") }
        near(28f + 391.13f + 8f, b.pageH, "pageH: nothing below the top")
    }
    // No tools: the column 0.4 · (857 − 14) = 337.2 wide, flush right (38 + 857 − 337.2 = 557.8), Continue 505.8.
    @Test fun oneGameNoTools() {
        val b = homeBoard(fold, 1, tools = 0, games = 1)
        near(505.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        near(557.8f, tiles(b, HomeTopKind.GAME).single().rect.x, "game x")
    }
    // 3 or 4 games: the first 2 inline (stacked, (264.67 − 14) / 2 = 125.33 tall), the rest in the pins' band under the
    // top section (the tools' row its end) at 433.13, one row.
    @Test fun threeOrFourGamesPutTwoInlineAndTheRestInThePins() {
        for (games in 3..4) {
            val b = homeBoard(fold, 1, tools = 6, games = games)
            assertEquals(2, b.inlineGames, "$games"); assertEquals(games - 2, b.rowGames, "$games")
            val g = tiles(b, HomeTopKind.GAME).map { it.rect }
            assertEquals(2, g.size, "$games"); g.forEach { near(125.33f, it.h, "$games: game h") }
            near(433.13f, b.tilesY, "$games: tilesY")
            near(433.13f + 114.61f + 3f + 8f, b.pageH, "$games: pageH, one row")
            near(38f + (games - 3) * (203.75f + 14f), homeBoardPin(b, games - 3).x, "$games: the last row game")
        }
    }
    // The pins a gap under the top section (Continue, a gap and the tools' row: 391.13) at 28 + 391.13 + 14 = 433.13:
    // 4 a row, 203.75 × 114.61.
    @Test fun pinsAreFourARowUnderTheTop() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(4, b.perRow); near(203.75f, b.pinW, "pinW"); near(114.61f, b.pinH, "pinH")
        assertEquals(3, b.shelfSizes.size)
        near(433.13f, b.tilesY, "tilesY")
        val fifth = homeBoardTile(b, 4)
        near(38f, fifth.x, "second row x"); near(433.13f + 114.61f + 14f, fifth.y, "second row y")
    }
    // After two pin rows (ending 433.13 + 2 · 114.61 + 14 = 676.35): New's tiles a gap under, at 690.35. Every shelf
    // holds at most 4, one row (New's 6 draws 4, Not started its 3, Pick up its 4), in the pins' grid: 4 a row at the pin
    // size (203.75 × 114.61).
    @Test fun shelvesFollowInOrderEachOneRow() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(listOf(4, 3, 4), b.shelfSizes); assertEquals(11, b.shelfTiles.size)
        val n0 = homeBoardTile(b, 5)
        near(38f, n0.x, "New 0 x"); near(690.35f, n0.y, "New 0 y"); near(203.75f, n0.w, "w"); near(114.61f, n0.h, "h")
        near(38f + 3 * (203.75f + 14f), homeBoardTile(b, 8).x, "New 3 x"); near(690.35f, homeBoardTile(b, 8).y, "New 3 y: the same row")
        near(690.35f + 114.61f + 14f, homeBoardTile(b, 9).y, "Not started 0 y")
        for (k in 0..2) {
            val t = homeBoardTile(b, 9 + k)
            near(38f + k * (203.75f + 14f), t.x, "Not started $k x"); near(203.75f, t.w, "Not started w"); near(114.61f, t.h, "Not started h")
        }
        near(203.75f, homeBoardTile(b, 12).w, "Pick up again: the same width")
        val last = homeBoardTile(b, 15)
        near(last.y + last.h + 3f + 8f, b.pageH, "pageH")
    }
    // More than 4 offered to a shelf (Not started, New) draw 4: the large face's row of 4.
    @Test fun notStartedHoldsFour() {
        val b = homeBoard(fold, 1, tools = 6, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 6, HomeShelf.NEW to 6))
        assertEquals(listOf(4, 4), b.shelfSizes)
    }
    // No pins: the first shelf starts a gap under the top section, the tools' row its end (28 + 391.13 + 14).
    @Test fun noPinsFirstShelfStartsAGapUnderTheTop() {
        val b = homeBoard(fold, 1, tools = 6, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 6))
        assertEquals(listOf(4), b.shelfSizes)
        near(433.13f, homeBoardTile(b, 0).y, "first shelf tile y")
    }
    @Test fun nothingBelowTheTop() {
        val b = homeBoard(fold, 1, tools = 2, games = 0)
        assertEquals(0, b.shelfTiles.size); near(28f + 391.13f + 8f, b.pageH, "pageH")
    }
    // The wide face shows the shelves too, in the pins' grid: the pins' columns and tile size, 3 a
    // shelf on its row of 3 ([homeShelfCap]).
    @Test fun theWideFaceShowsShelves() {
        val f = HomeFrame(960f, 344f, false, 960f / 432f)
        val b = homeBoard(f, 1, 5, 6, shelves = three)
        assertEquals(HomeToolMode.WIDE, b.mode)
        assertEquals(listOf(3, 3, 3), b.shelfSizes); assertEquals(List(3) { b.perRow }, b.shelfCols)
        for (r in b.shelfTiles) { near(b.pinW, r.w, "shelf tile w"); near(b.pinH, r.h, "shelf tile h") }
        assertEquals(homeBoard(f, 1, 5, 6), homeBoard(f, 1, 5, 6, shelves = listOf(HomeShelf.NEW to 0)).copy(shelfCols = emptyList()))
    }
    // Nothing reserves header room: the first shelf starts a gap under the last pin row (2 rows end at 676.35), and
    // the next one a gap under that shelf's row.
    @Test fun shelvesStartAGapUnderWhatIsAbove() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        val pin = homeBoardTile(b, 4)
        near(pin.y + pin.h + b.gap, homeBoardTile(b, 5).y, "New under the pins")
        val n = homeBoardTile(b, 5)
        near(n.y + n.h + b.gap, homeBoardTile(b, 9).y, "Not started under New")
        // At a bigger text scale everything moves down with the top, the shelves keeping the same gap under the pins.
        val big = homeBoard(fold.copy(textScale = 1.5f), 1, tools = 6, games = 7, shelves = three)
        val bp = homeBoardTile(big, 4)
        near(bp.y + bp.h + big.gap, homeBoardTile(big, 5).y, "New under the pins at 1.5")
        val small = homeBoard(fold.copy(textScale = 0.85f), 1, tools = 6, games = 7, shelves = three)
        // A smaller system font: the tool tiles' name lines are shorter, as on the Tools page, so the row, and with it
        // the top section and everything under it, sits that much higher; Continue, the games and every x as at 1.
        val less = b.toolH - small.toolH
        near(toolTileHeight(b.toolW, 0.85f), small.toolH, "the tiles at 0.85"); near(2 * 12f * 1.15f * 0.15f, less, "the name lines' difference")
        near(b.topH - less, small.topH, "topH"); near(b.tilesY - less, small.tilesY, "tilesY"); near(b.pageH - less, small.pageH, "pageH")
        assertEquals(b.top.filter { it.kind != HomeTopKind.TOOL }, small.top.filter { it.kind != HomeTopKind.TOOL }, "Continue and the games")
        assertEquals(b.top.map { it.kind to it.rect.copy(h = 0f) }, small.top.map { it.kind to it.rect.copy(h = 0f) }, "every tile's place")
    }
}
