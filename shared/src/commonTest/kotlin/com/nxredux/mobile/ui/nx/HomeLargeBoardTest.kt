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
    // w 885 (933 − 2 · 24); a full square (885 / 2 − 14 − 28) / 3 = 133.5 sets the top, 2 · 133.5 + 7 = 274 from topY 28.
    // The squares 5 a column (rows 7 apart), min(133.5 / 2, (274 − 4 · 7) / 5) = 49.2, top-aligned, flush right: 6 tools
    // are 2 columns (2 · 49.2 + 14 = 112.4) from 772.6, in reading order. Left of them 758.6: the 2 inline games
    // 0.4 · (758.6 − 14) = 297.84 wide, Continue 446.76 (without games 758.6).
    @Test fun topSectionIsContinueAndSquaresFiveAColumn() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(HomeToolMode.LARGE, b.mode); assertEquals(10, b.slots); assertEquals(0, b.more)
        near(28f, b.topY, "topY"); near(274f, b.topH, "topH"); near(49.2f, b.square, "square")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(24f, c.x, "continue x"); near(446.76f, c.w, "continue w"); near(274f, c.h, "continue h")
        val sq = tiles(b, HomeTopKind.TOOL).map { it.rect }
        assertEquals(6, sq.size)
        for ((i, r) in sq.withIndex()) {
            near(24f + 772.6f + (i % 2) * 63.2f, r.x, "square $i x"); near(28f + (i / 2) * 56.2f, r.y, "square $i y")
            near(49.2f, r.w, "square w"); near(49.2f, r.h, "square h")
        }
    }
    // 4 tools are one column as 3 are (Continue 885 − 49.2 − 14 = 821.8); 6 two.
    @Test fun fewerToolsNarrowTheBlockAndWidenContinue() {
        near(758.6f, tiles(homeBoard(fold, 1, tools = 6, games = 0), HomeTopKind.CONTINUE).single().rect.w, "continue w (2 columns)")
        near(821.8f, tiles(homeBoard(fold, 1, tools = 4, games = 0), HomeTopKind.CONTINUE).single().rect.w, "continue w (1 column)")
        near(821.8f, tiles(homeBoard(fold, 1, tools = 3, games = 0), HomeTopKind.CONTINUE).single().rect.w, "continue w (1 column)")
        val none = homeBoard(fold, 1, tools = 0, games = 0)
        near(885f, tiles(none, HomeTopKind.CONTINUE).single().rect.w, "continue w (no tools)")
        near(274f, none.topH, "topH holds")
    }
    // 2 columns of 5: past 10 tools, 9 and "+N".
    @Test fun moreThanTenToolsShowNineAndMore() {
        val b = homeBoard(fold, 1, tools = 13, games = 0)
        assertEquals(10, b.slots); assertEquals(9, tiles(b, HomeTopKind.TOOL).size); assertEquals(1, tiles(b, HomeTopKind.MORE).size); assertEquals(4, b.more)
        near(28f + 4 * 56.2f, tiles(b, HomeTopKind.MORE).single().rect.y, "+N: the last square, fifth row")
    }
    // 1 pinned game sits inline, 0.4 · (758.6 − 14) = 297.84 wide, the top's full height, at 24 + 446.76 + 14 = 484.76;
    // Continue 446.76. No pins: New's tiles start a gap under the top, at 316.
    @Test fun oneGameSitsInline() {
        val b = homeBoard(fold, 1, tools = 6, games = 1, shelves = listOf(HomeShelf.NEW to 6))
        assertEquals(1, b.inlineGames); assertEquals(0, b.rowGames)
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME) + List(6) { HomeTopKind.TOOL }, b.top.map { it.kind })
        near(446.76f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        val g = tiles(b, HomeTopKind.GAME).single().rect
        near(484.76f, g.x, "game x"); near(28f, g.y, "game y"); near(297.84f, g.w, "game w"); near(274f, g.h, "game h")
        near(316f, homeBoardTile(b, 0).y, "New's tiles")
    }
    // 2 games: that column, stacked, each (274 − 14) / 2 = 130 tall, the second at 28 + 144.
    @Test fun twoGamesStackInline() {
        val b = homeBoard(fold, 1, tools = 6, games = 2)
        assertEquals(2, b.inlineGames); assertEquals(0, b.rowGames)
        val g = tiles(b, HomeTopKind.GAME).map { it.rect }
        near(28f, g[0].y, "game 0 y"); near(172f, g[1].y, "game 1 y")
        for (r in g) { near(484.76f, r.x, "game x"); near(297.84f, r.w, "game w"); near(130f, r.h, "game h") }
        near(28f + 274f + 8f, b.pageH, "pageH: nothing below the top")
    }
    // No tools: the column 0.4 · (885 − 14) = 348.4 wide, flush right (24 + 885 − 348.4 = 560.6), Continue 522.6.
    @Test fun oneGameNoTools() {
        val b = homeBoard(fold, 1, tools = 0, games = 1)
        near(522.6f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        near(560.6f, tiles(b, HomeTopKind.GAME).single().rect.x, "game x")
    }
    // 3 or 4 games: the first 2 inline (stacked, (274 − 14) / 2 = 130 tall), the rest in the pins' band under the top at
    // 316, one row.
    @Test fun threeOrFourGamesPutTwoInlineAndTheRestInThePins() {
        for (games in 3..4) {
            val b = homeBoard(fold, 1, tools = 6, games = games)
            assertEquals(2, b.inlineGames, "$games"); assertEquals(games - 2, b.rowGames, "$games")
            val g = tiles(b, HomeTopKind.GAME).map { it.rect }
            assertEquals(2, g.size, "$games"); g.forEach { near(130f, it.h, "$games: game h") }
            near(316f, b.tilesY, "$games: tilesY")
            near(316f + 118.55f + 3f + 8f, b.pageH, "$games: pageH, one row")
            near(24f + (games - 3) * (210.75f + 14f), homeBoardPin(b, games - 3).x, "$games: the last row game")
        }
    }
    // The pins a gap under the top at 28 + 274 + 14 = 316: 4 a row, 210.75 × 118.55.
    @Test fun pinsAreFourARowUnderTheTop() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(4, b.perRow); near(210.75f, b.pinW, "pinW"); near(118.55f, b.pinH, "pinH")
        assertEquals(3, b.shelfSizes.size)
        near(316f, b.tilesY, "tilesY")
        val fifth = homeBoardTile(b, 4)
        near(24f, fifth.x, "second row x"); near(316f + 118.55f + 14f, fifth.y, "second row y")
    }
    // After two pin rows (ending 316 + 2 · 118.55 + 14 = 567.1): New's tiles a gap under, at 581.1. Every shelf
    // holds at most 4, one row (New's 6 draws 4, Not started its 3, Pick up its 4), in the pins' grid: 4 a row at the pin
    // size (210.75 × 118.55).
    @Test fun shelvesFollowInOrderEachOneRow() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(listOf(4, 3, 4), b.shelfSizes); assertEquals(11, b.shelfTiles.size)
        val n0 = homeBoardTile(b, 5)
        near(24f, n0.x, "New 0 x"); near(581.1f, n0.y, "New 0 y"); near(210.75f, n0.w, "w"); near(118.55f, n0.h, "h")
        near(24f + 3 * (210.75f + 14f), homeBoardTile(b, 8).x, "New 3 x"); near(581.1f, homeBoardTile(b, 8).y, "New 3 y: the same row")
        near(581.1f + 118.55f + 14f, homeBoardTile(b, 9).y, "Not started 0 y")
        for (k in 0..2) {
            val t = homeBoardTile(b, 9 + k)
            near(24f + k * (210.75f + 14f), t.x, "Not started $k x"); near(210.75f, t.w, "Not started w"); near(118.55f, t.h, "Not started h")
        }
        near(210.75f, homeBoardTile(b, 12).w, "Pick up again: the same width")
        val last = homeBoardTile(b, 15)
        near(last.y + last.h + 3f + 8f, b.pageH, "pageH")
    }
    // More than 4 offered to a shelf (Not started, New) draw 4: the large face's row of 4.
    @Test fun notStartedHoldsFour() {
        val b = homeBoard(fold, 1, tools = 6, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 6, HomeShelf.NEW to 6))
        assertEquals(listOf(4, 4), b.shelfSizes)
    }
    // No pins: the first shelf starts a gap under the top (28 + 274 + 14).
    @Test fun noPinsFirstShelfStartsAGapUnderTheTop() {
        val b = homeBoard(fold, 1, tools = 6, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 6))
        assertEquals(listOf(4), b.shelfSizes)
        near(316f, homeBoardTile(b, 0).y, "first shelf tile y")
    }
    @Test fun nothingBelowTheTop() {
        val b = homeBoard(fold, 1, tools = 2, games = 0)
        assertEquals(0, b.shelfTiles.size); near(28f + 274f + 8f, b.pageH, "pageH")
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
