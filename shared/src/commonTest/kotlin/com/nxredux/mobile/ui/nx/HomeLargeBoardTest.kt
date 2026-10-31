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
    // w 857 (933 − 2 · 38); a full square (857 / 2 − 14 − 28) / 3 = 128.83 sets the top, 2 · 128.83 + 7 = 264.67 from
    // topY 28. The squares 5 a column (rows 7 apart), min(128.83 / 2, (264.67 − 4 · 7) / 5) = 47.33, top-aligned, flush
    // right: 6 tools are 2 columns (2 · 47.33 + 14 = 108.67) from 748.33, in reading order. Left of them 734.33: the 2
    // inline games 0.4 · (734.33 − 14) = 288.13 wide, Continue 432.2 (without games 734.33).
    @Test fun topSectionIsContinueAndSquaresFiveAColumn() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(HomeToolMode.LARGE, b.mode); assertEquals(10, b.slots); assertEquals(0, b.more)
        near(28f, b.topY, "topY"); near(264.67f, b.topH, "topH"); near(47.33f, b.square, "square")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(38f, c.x, "continue x"); near(432.2f, c.w, "continue w"); near(264.67f, c.h, "continue h")
        val sq = tiles(b, HomeTopKind.TOOL).map { it.rect }
        assertEquals(6, sq.size)
        for ((i, r) in sq.withIndex()) {
            near(38f + 748.33f + (i % 2) * 61.33f, r.x, "square $i x"); near(28f + (i / 2) * 54.33f, r.y, "square $i y")
            near(47.33f, r.w, "square w"); near(47.33f, r.h, "square h")
        }
    }
    // 4 tools are one column as 3 are (Continue 857 − 47.33 − 14 = 795.67); 6 two.
    @Test fun fewerToolsNarrowTheBlockAndWidenContinue() {
        near(734.33f, tiles(homeBoard(fold, 1, tools = 6, games = 0), HomeTopKind.CONTINUE).single().rect.w, "continue w (2 columns)")
        near(795.67f, tiles(homeBoard(fold, 1, tools = 4, games = 0), HomeTopKind.CONTINUE).single().rect.w, "continue w (1 column)")
        near(795.67f, tiles(homeBoard(fold, 1, tools = 3, games = 0), HomeTopKind.CONTINUE).single().rect.w, "continue w (1 column)")
        val none = homeBoard(fold, 1, tools = 0, games = 0)
        near(857f, tiles(none, HomeTopKind.CONTINUE).single().rect.w, "continue w (no tools)")
        near(264.67f, none.topH, "topH holds")
    }
    // 2 columns of 5: past 10 tools, 9 and "+N".
    @Test fun moreThanTenToolsShowNineAndMore() {
        val b = homeBoard(fold, 1, tools = 13, games = 0)
        assertEquals(10, b.slots); assertEquals(9, tiles(b, HomeTopKind.TOOL).size); assertEquals(1, tiles(b, HomeTopKind.MORE).size); assertEquals(4, b.more)
        near(28f + 4 * 54.33f, tiles(b, HomeTopKind.MORE).single().rect.y, "+N: the last square, fifth row")
    }
    // 1 pinned game sits inline, 0.4 · (734.33 − 14) = 288.13 wide, the top's full height, at 38 + 432.2 + 14 = 484.2;
    // Continue 432.2. No pins: New's tiles start a gap under the top, at 306.67.
    @Test fun oneGameSitsInline() {
        val b = homeBoard(fold, 1, tools = 6, games = 1, shelves = listOf(HomeShelf.NEW to 6))
        assertEquals(1, b.inlineGames); assertEquals(0, b.rowGames)
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME) + List(6) { HomeTopKind.TOOL }, b.top.map { it.kind })
        near(432.2f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        val g = tiles(b, HomeTopKind.GAME).single().rect
        near(484.2f, g.x, "game x"); near(28f, g.y, "game y"); near(288.13f, g.w, "game w"); near(264.67f, g.h, "game h")
        near(306.67f, homeBoardTile(b, 0).y, "New's tiles")
    }
    // 2 games: that column, stacked, each (264.67 − 14) / 2 = 125.33 tall, the second at 28 + 139.33.
    @Test fun twoGamesStackInline() {
        val b = homeBoard(fold, 1, tools = 6, games = 2)
        assertEquals(2, b.inlineGames); assertEquals(0, b.rowGames)
        val g = tiles(b, HomeTopKind.GAME).map { it.rect }
        near(28f, g[0].y, "game 0 y"); near(167.33f, g[1].y, "game 1 y")
        for (r in g) { near(484.2f, r.x, "game x"); near(288.13f, r.w, "game w"); near(125.33f, r.h, "game h") }
        near(28f + 264.67f + 8f, b.pageH, "pageH: nothing below the top")
    }
    // No tools: the column 0.4 · (857 − 14) = 337.2 wide, flush right (38 + 857 − 337.2 = 557.8), Continue 505.8.
    @Test fun oneGameNoTools() {
        val b = homeBoard(fold, 1, tools = 0, games = 1)
        near(505.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        near(557.8f, tiles(b, HomeTopKind.GAME).single().rect.x, "game x")
    }
    // 3 or 4 games: the first 2 inline (stacked, (264.67 − 14) / 2 = 125.33 tall), the rest in the pins' band under the
    // top at 306.67, one row.
    @Test fun threeOrFourGamesPutTwoInlineAndTheRestInThePins() {
        for (games in 3..4) {
            val b = homeBoard(fold, 1, tools = 6, games = games)
            assertEquals(2, b.inlineGames, "$games"); assertEquals(games - 2, b.rowGames, "$games")
            val g = tiles(b, HomeTopKind.GAME).map { it.rect }
            assertEquals(2, g.size, "$games"); g.forEach { near(125.33f, it.h, "$games: game h") }
            near(306.67f, b.tilesY, "$games: tilesY")
            near(306.67f + 114.61f + 3f + 8f, b.pageH, "$games: pageH, one row")
            near(38f + (games - 3) * (203.75f + 14f), homeBoardPin(b, games - 3).x, "$games: the last row game")
        }
    }
    // The pins a gap under the top at 28 + 264.67 + 14 = 306.67: 4 a row, 203.75 × 114.61.
    @Test fun pinsAreFourARowUnderTheTop() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(4, b.perRow); near(203.75f, b.pinW, "pinW"); near(114.61f, b.pinH, "pinH")
        assertEquals(3, b.shelfSizes.size)
        near(306.67f, b.tilesY, "tilesY")
        val fifth = homeBoardTile(b, 4)
        near(38f, fifth.x, "second row x"); near(306.67f + 114.61f + 14f, fifth.y, "second row y")
    }
    // After two pin rows (ending 306.67 + 2 · 114.61 + 14 = 549.89): New's tiles a gap under, at 563.89. Every shelf
    // holds at most 4, one row (New's 6 draws 4, Not started its 3, Pick up its 4), in the pins' grid: 4 a row at the pin
    // size (203.75 × 114.61).
    @Test fun shelvesFollowInOrderEachOneRow() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(listOf(4, 3, 4), b.shelfSizes); assertEquals(11, b.shelfTiles.size)
        val n0 = homeBoardTile(b, 5)
        near(38f, n0.x, "New 0 x"); near(563.89f, n0.y, "New 0 y"); near(203.75f, n0.w, "w"); near(114.61f, n0.h, "h")
        near(38f + 3 * (203.75f + 14f), homeBoardTile(b, 8).x, "New 3 x"); near(563.89f, homeBoardTile(b, 8).y, "New 3 y: the same row")
        near(563.89f + 114.61f + 14f, homeBoardTile(b, 9).y, "Not started 0 y")
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
    // No pins: the first shelf starts a gap under the top (28 + 264.67 + 14).
    @Test fun noPinsFirstShelfStartsAGapUnderTheTop() {
        val b = homeBoard(fold, 1, tools = 6, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 6))
        assertEquals(listOf(4), b.shelfSizes)
        near(306.67f, homeBoardTile(b, 0).y, "first shelf tile y")
    }
    @Test fun nothingBelowTheTop() {
        val b = homeBoard(fold, 1, tools = 2, games = 0)
        assertEquals(0, b.shelfTiles.size); near(28f + 264.67f + 8f, b.pageH, "pageH")
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
    // Nothing reserves header room: the first shelf starts a gap under the last pin row (2 rows end at 567.1), and
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
        assertEquals(b, small.copy(stripY = b.stripY))                                           // a smaller system font: as at 1
    }
}
