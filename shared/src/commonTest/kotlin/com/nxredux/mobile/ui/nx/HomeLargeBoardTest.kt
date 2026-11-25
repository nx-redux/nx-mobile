package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLargeBoardTest {
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
    // 2 · 128.83 + 7 = 264.67 from topY 28: the 2 games 0.4 · (857 − 14) = 337.2 wide, Continue 505.8.
    // Under the top section: 5 row games (2 rows of 4 at 203.75 × 114.61), then 6 pinned tools in 2 rows of 4
    // (203.75 × toolH), then the 3 shelves.
    @Test fun topSectionIsContinueAndInlineGamesWithToolsInTheirBand() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        val th = 114.61f
        assertEquals(HomeToolMode.LARGE, b.mode); assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more)
        near(28f, b.topY, "topY"); near(264.67f, b.topH, "topH"); near(203.75f, b.toolW, "toolW"); near(th, b.toolH, "toolH")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(38f, c.x, "continue x"); near(505.8f, c.w, "continue w"); near(264.67f, c.h, "continue h")
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME, HomeTopKind.GAME), b.top.map { it.kind })
        assertEquals(6, b.toolTiles.size)
        val pinEnd = b.tilesY + 2 * b.pinH + 14f
        for ((i, r) in b.toolTiles.withIndex()) {
            near(38f + (i % 4) * (203.75f + 14f), r.x, "tile $i x")
            near(pinEnd + 14f + (i / 4) * (th + 14f), r.y, "tile $i y")
            near(203.75f, r.w, "tile w"); near(th, r.h, "tile h")
        }
    }
    // Continue never follows the tools: without games it runs across the page (857), 264.67 tall, with tools or without.
    @Test fun continueSpansThePageWhateverTheTools() {
        for (tools in listOf(6, 4, 3, 2, 0)) {
            val b = homeBoard(fold, 1, tools = tools, games = 0)
            val c = tiles(b, HomeTopKind.CONTINUE).single().rect
            near(857f, c.w, "continue w ($tools tools)"); near(264.67f, c.h, "continue h ($tools tools)"); near(264.67f, b.topH, "topH ($tools tools)")
            assertEquals(tools, b.toolTiles.size, "tool tiles ($tools tools)")
        }
    }
    // All tools fit in rows of 4 without "+N" overflow.
    @Test fun thirteenToolsWrapIntoFourRowsWithoutMore() {
        val b = homeBoard(fold, 1, tools = 13, games = 0)
        assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more); assertEquals(13, b.toolTiles.size)
        near(38f, b.toolTiles.last().x, "13th tool starts row 4")
        near(28f + 264.67f + 14f + 3 * (b.toolH + 14f), b.toolTiles.last().y, "row 4 y")
    }
    // 1 pinned game sits inline, 0.4 · (857 − 14) = 337.2 wide, Continue's full height (264.67), at
    // 38 + 505.8 + 14 = 557.8; Continue 505.8. No row pins: 4 tools sit at 28 + 264.67 + 14 = 306.67, then New a gap under them.
    @Test fun oneGameSitsInline() {
        val b = homeBoard(fold, 1, tools = 4, games = 1, shelves = listOf(HomeShelf.NEW to 6))
        assertEquals(1, b.inlineGames); assertEquals(0, b.rowGames)
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME), b.top.map { it.kind })
        near(505.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        val g = tiles(b, HomeTopKind.GAME).single().rect
        near(557.8f, g.x, "game x"); near(28f, g.y, "game y"); near(337.2f, g.w, "game w"); near(264.67f, g.h, "game h")
        near(306.67f, homeBoardTile(b, 0).y, "tool 0 y")
        near(306.67f + b.toolH + 14f, homeBoardTile(b, 4).y, "New's tiles under tools")
    }
    // 2 games: that column, stacked, each (264.67 − 14) / 2 = 125.33 tall, the second at 28 + 139.33. 6 tools wrap into 2 rows below.
    @Test fun twoGamesStackInline() {
        val b = homeBoard(fold, 1, tools = 6, games = 2)
        assertEquals(2, b.inlineGames); assertEquals(0, b.rowGames)
        val g = tiles(b, HomeTopKind.GAME).map { it.rect }
        near(28f, g[0].y, "game 0 y"); near(167.33f, g[1].y, "game 1 y")
        for (r in g) { near(557.8f, r.x, "game x"); near(337.2f, r.w, "game w"); near(125.33f, r.h, "game h") }
        val lastTool = b.toolTiles.last()
        near(lastTool.y + lastTool.h + 3f + 8f, b.pageH, "pageH ends under tools")
    }
    // No tools: the column 0.4 · (857 − 14) = 337.2 wide, flush right (38 + 857 − 337.2 = 557.8), Continue 505.8.
    @Test fun oneGameNoTools() {
        val b = homeBoard(fold, 1, tools = 0, games = 1)
        near(505.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        near(557.8f, tiles(b, HomeTopKind.GAME).single().rect.x, "game x")
    }
    // 3 or 4 games: the first 2 inline (stacked, (264.67 − 14) / 2 = 125.33 tall), the rest in the pins' band under the
    // top section at 306.67, one row, followed by the 6 tools in 2 rows below.
    @Test fun threeOrFourGamesPutTwoInlineAndTheRestInThePins() {
        for (games in 3..4) {
            val b = homeBoard(fold, 1, tools = 6, games = games)
            assertEquals(2, b.inlineGames, "$games"); assertEquals(games - 2, b.rowGames, "$games")
            val g = tiles(b, HomeTopKind.GAME).map { it.rect }
            assertEquals(2, g.size, "$games"); g.forEach { near(125.33f, it.h, "$games: game h") }
            near(306.67f, b.tilesY, "$games: tilesY")
            near(38f + (games - 3) * (203.75f + 14f), homeBoardPin(b, games - 3).x, "$games: the last row game")
            near(306.67f + 114.61f + 14f, b.toolTiles.first().y, "$games: tools under row games")
            val lastTool = b.toolTiles.last()
            near(lastTool.y + lastTool.h + 3f + 8f, b.pageH, "$games: pageH ends under tools")
        }
    }
    // The pins a gap under the top section at 28 + 264.67 + 14 = 306.67: 4 a row, 203.75 × 114.61.
    @Test fun pinsAreFourARowUnderTheTop() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(4, b.perRow); near(203.75f, b.pinW, "pinW"); near(114.61f, b.pinH, "pinH")
        assertEquals(3, b.shelfSizes.size)
        near(306.67f, b.tilesY, "tilesY")
        val fifth = homeBoardTile(b, 4)
        near(38f, fifth.x, "second row x"); near(306.67f + 114.61f + 14f, fifth.y, "second row y")
    }
    // After two pin rows (5 pins) and two tool rows (6 tools): New's tiles a gap under the tools.
    @Test fun shelvesFollowPinsAndToolsInOrderEachOneRow() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        assertEquals(listOf(4, 3, 4), b.shelfSizes); assertEquals(11, b.shelfTiles.size)
        val lastTool = b.toolTiles.last()
        val n0 = homeBoardTile(b, 5 + 6)
        near(38f, n0.x, "New 0 x"); near(lastTool.y + lastTool.h + 14f, n0.y, "New 0 y"); near(203.75f, n0.w, "w"); near(114.61f, n0.h, "h")
        near(38f + 3 * (203.75f + 14f), homeBoardTile(b, 5 + 6 + 3).x, "New 3 x"); near(n0.y, homeBoardTile(b, 5 + 6 + 3).y, "New 3 y: the same row")
        val ns0 = homeBoardTile(b, 5 + 6 + 4)
        near(n0.y + 114.61f + 14f, ns0.y, "Not started 0 y")
        for (k in 0..2) {
            val t = homeBoardTile(b, 5 + 6 + 4 + k)
            near(38f + k * (203.75f + 14f), t.x, "Not started $k x"); near(203.75f, t.w, "Not started w"); near(114.61f, t.h, "Not started h")
        }
        near(203.75f, homeBoardTile(b, 5 + 6 + 7).w, "Pick up again: the same width")
        val last = homeBoardTile(b, 5 + 6 + 10)
        near(last.y + last.h + 3f + 8f, b.pageH, "pageH")
    }
    // More than 4 offered to a shelf (Not started, New) draw 4: the large face's row of 4.
    @Test fun notStartedHoldsFour() {
        val b = homeBoard(fold, 1, tools = 6, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 6, HomeShelf.NEW to 6))
        assertEquals(listOf(4, 4), b.shelfSizes)
    }
    // No pins: the 6 tools sit in 2 rows under the top, and the first shelf starts a gap under them.
    @Test fun noPinsFirstShelfStartsAGapUnderTools() {
        val b = homeBoard(fold, 1, tools = 6, games = 0, shelves = listOf(HomeShelf.NOT_STARTED to 6))
        assertEquals(listOf(4), b.shelfSizes)
        near(306.67f, homeBoardTile(b, 0).y, "first tool y")
        val lastTool = b.toolTiles.last()
        near(lastTool.y + lastTool.h + 14f, homeBoardTile(b, 6).y, "first shelf tile y")
    }
    @Test fun nothingBelowTheTop() {
        val b = homeBoard(fold, 1, tools = 0, games = 0)
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
    // Nothing reserves header room: tools start a gap under the pins, and shelves a gap under the tools.
    @Test fun shelvesStartAGapUnderToolsAndPins() {
        val b = homeBoard(fold, 1, tools = 6, games = 7, shelves = three)
        val pin = homeBoardTile(b, 4)
        near(pin.y + pin.h + b.gap, b.toolTiles.first().y, "tools under the pins")
        val lastTool = b.toolTiles.last()
        near(lastTool.y + lastTool.h + b.gap, homeBoardTile(b, 5 + 6).y, "New under the tools")
        val n = homeBoardTile(b, 5 + 6)
        near(n.y + n.h + b.gap, homeBoardTile(b, 5 + 6 + 4).y, "Not started under New")
        // At a bigger text scale, tool cards match pinH and bands keep their gap.
        val big = homeBoard(fold.copy(textScale = 1.5f), 1, tools = 6, games = 7, shelves = three)
        near(big.pinH, big.toolH, "toolH matches pinH at 1.5")
        val bp = homeBoardTile(big, 4)
        near(bp.y + bp.h + big.gap, big.toolTiles.first().y, "tools under the pins at 1.5")
        near(big.toolTiles.last().y + big.toolTiles.last().h + big.gap, homeBoardTile(big, 5 + 6).y, "New under tools at 1.5")
    }
}
