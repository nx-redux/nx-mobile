package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wide face (the Flip, 20:9 phones in landscape): squares 5 a column (at most 58 dp), up to 3 columns, top-aligned,
 * the first 1–2 games inline in one column (stacked when 2), 40% of the width left of the squares, the rest row games.
 */
class HomeWideBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private val flip = HomeFrame(804f, 261f, false, 804f / 330f)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }.map { it.rect }

    // w 756, topY 2 + 18 + 8 = 28, topH 261 − 28 − 8 = 225; square min(58, (225 − 4 · 7) / 5) = 39.4; 6 tools 2 columns
    // (ceil(6 / 5)), the block 2 · 39.4 + 14 = 92.8 wide, from 756 − 92.8 = 663.2 (page 687.2), top-aligned, in reading
    // order (3 rows of 2). Left of them 663.2 − 14 = 649.2: the games one inline column 0.4 · (649.2 − 14) = 254.08
    // wide, stacked (225 − 14) / 2 = 105.5 tall; Continue the other 0.6, 381.12, the column at 24 + 381.12 + 14 = 419.12.
    @Test fun twoGamesStackInOneDoubleWideColumn() {
        val b = homeBoard(flip, 1, tools = 6, games = 2)
        assertEquals(HomeToolMode.WIDE, b.mode); assertTrue(b.wide)
        assertEquals(2, b.inlineGames); assertEquals(0, b.rowGames)
        near(28f, b.topY, "topY"); near(225f, b.topH, "topH"); near(39.4f, b.square, "square")
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME, HomeTopKind.GAME) + List(6) { HomeTopKind.TOOL }, b.top.map { it.kind })
        val c = tiles(b, HomeTopKind.CONTINUE).single()
        near(24f, c.x, "continue x"); near(28f, c.y, "continue y"); near(381.12f, c.w, "continue w"); near(225f, c.h, "continue h")
        val g = tiles(b, HomeTopKind.GAME)
        near(28f, g[0].y, "game 0 y"); near(147.5f, g[1].y, "game 1 y")
        for (r in g) { near(419.12f, r.x, "game x"); near(254.08f, r.w, "game w"); near(105.5f, r.h, "game h") }
        val sq = tiles(b, HomeTopKind.TOOL)
        for ((i, r) in sq.withIndex()) {
            near(687.2f + (i % 2) * (39.4f + 14f), r.x, "square $i x"); near(28f + (i / 2) * (39.4f + 7f), r.y, "square $i y")
            near(39.4f, r.w, "square w"); near(39.4f, r.h, "square h")
        }
        near(261f, b.pageH, "pageH")
    }
    // 1 game, 6 tools: one card 254.08 wide, the top's full height, at 24 + 381.12 + 14.
    @Test fun oneGameIsOneInlineCard() {
        val b = homeBoard(flip, 1, tools = 6, games = 1)
        assertEquals(1, b.inlineGames)
        near(381.12f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        val g = tiles(b, HomeTopKind.GAME).single()
        near(419.12f, g.x, "game x"); near(28f, g.y, "game y"); near(254.08f, g.w, "game w"); near(225f, g.h, "game h")
    }
    // 4 tools: one column (39.4), from 756 − 39.4 = 716.6; left of it 702.6: Continue 0.6 · (702.6 − 14) = 413.16, the
    // game at 24 + 427.16.
    @Test fun oneGameFourTools() {
        val b = homeBoard(flip, 1, tools = 4, games = 1)
        assertEquals(1, b.inlineGames)
        val sq = tiles(b, HomeTopKind.TOOL)
        near(39.4f, sq.maxOf { it.x + it.w } - sq.minOf { it.x }, "block w")
        near(413.16f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        near(451.16f, tiles(b, HomeTopKind.GAME).single().x, "game x")
    }
    // No tools: the column 0.4 · (756 − 14) = 296.8 wide, flush right; Continue 445.2.
    @Test fun oneGameNoTools() {
        val b = homeBoard(flip, 1, tools = 0, games = 1)
        near(445.2f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        near(296.8f, tiles(b, HomeTopKind.GAME).single().w, "game w")
        val g = tiles(b, HomeTopKind.GAME).single()
        near(24f + 756f, g.x + g.w, "flush right")
    }
    // A tiny window still splits the room left of the squares 60 : 40 and never crosses a gap before them. w 252, topH
    // 160 − 36 = 124, square (124 − 4 · 7) / 5 = 19.2, 15 tools a block of 3 columns 3 · 19.2 + 2 · 14 = 85.6 from 166.4;
    // left of it 152.4: the column 0.4 · (152.4 − 14) = 55.36, Continue 83.04.
    @Test fun aTinyWindowSplitsTheRoomLeftOfTheSquares() {
        val b = homeBoard(HomeFrame(300f, 160f, false, 4f), 1, tools = 15, games = 2)
        assertEquals(HomeToolMode.WIDE, b.mode); near(19.2f, b.square, "square")
        near(83.04f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        for (g in tiles(b, HomeTopKind.GAME)) { near(55.36f, g.w, "game w"); near(24f + 166.4f - 14f, g.x + g.w, "game right") }
    }
    @Test fun nothingPinnedContinueFills() {
        val b = homeBoard(flip, 1, tools = 0, games = 0)
        val c = tiles(b, HomeTopKind.CONTINUE).single()
        near(756f, c.w, "continue w"); near(225f, c.h, "continue h"); assertEquals(1, b.top.size)
    }
    // 3 columns of 5: past 15 tools, 14 and "+N".
    @Test fun moreThanFifteenToolsShowFourteenAndMore() {
        val b = homeBoard(flip, 1, tools = 18, games = 2)
        assertEquals(15, b.slots); assertEquals(4, b.more)
        assertEquals(14, tiles(b, HomeTopKind.TOOL).size); assertEquals(HomeTopKind.MORE, b.top.last().kind)
    }
    // 3 games: the first 2 inline, the third a row game a scroll away (6 columns of 114.33, shape min(114.33, max(85.75,
    // (261 − 33) · 0.4 = 91.2)) = 91.2, 3 a row). The top stays the whole body, 225, squares 39.4 as with 2 games; left of
    // the 2 columns 649.2: Continue 381.12, the games 254.08 wide, stacked 105.5 tall; the row at 28 + 225 + 14 = 267.
    @Test fun threeGamesPutTwoInlineAndOneInTheRows() {
        val b = homeBoard(flip, 1, tools = 6, games = 3)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames)
        near(225f, b.topH, "topH"); near(39.4f, b.square, "square")
        assertEquals(3, b.perRow); near(242.67f, b.pinW, "pinW"); near(91.2f, b.pinH, "pinH")
        near(267f, b.tilesY, "tilesY"); assertTrue(b.tilesY >= 261f, "a scroll away")
        val g = tiles(b, HomeTopKind.GAME)
        assertEquals(2, g.size); for (r in g) { near(254.08f, r.w, "game w"); near(105.5f, r.h, "game h") }
        // The block top-aligned, 2 columns (ceil(6 / 5)) flush right.
        val sq = tiles(b, HomeTopKind.TOOL)
        near(28f, sq.first().y, "block top"); near(24f + 756f, sq.last().x + sq.last().w, "flush right")
        near(381.12f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
    }
    // 4 games: 2 inline as with 3, the other 2 one row (3 a row) at 267; the page ends under it, 267 + 91.2 + 3 + 8.
    @Test fun fourGamesPutTwoInlineAndTwoInTheRows() {
        val b = homeBoard(flip, 1, tools = 6, games = 4)
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames); assertEquals(2, tiles(b, HomeTopKind.GAME).size)
        near(225f, b.topH, "topH"); near(267f, b.tilesY, "tilesY")
        near(24f + 242.67f + 14f, homeBoardPin(b, 1).x, "the second row game beside the first")
        near(267f + 91.2f + 3f + 8f, b.pageH, "pageH: one row")
    }
    // The top section never depends on the row games: 2 or 3 pinned games give the same top, squares and Continue, on the
    // wide face (the Flip: 225, 39.4) and on SPS3 (800 × 340: 304, 55.2); the third game only adds a row below.
    @Test fun aThirdGameKeepsTheTopAndTheSquares() {
        for (f in listOf(flip, HomeFrame(800f, 340f, false, 800f / 428f))) {
            val two = homeBoard(f, 1, tools = 6, games = 2)
            val three = homeBoard(f, 1, tools = 6, games = 3)
            assertEquals(0, two.rowGames, "$f"); assertEquals(1, three.rowGames, "$f")
            near(two.topH, three.topH, "$f topH"); near(two.square, three.square, "$f square")
            assertEquals(two.top, three.top, "$f the top tiles")
        }
        near(225f, homeBoard(flip, 1, 6, 3).topH, "wide topH"); near(39.4f, homeBoard(flip, 1, 6, 3).square, "wide square")
        val sps3 = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, 6, 3)
        assertEquals(HomeToolMode.SPS3, sps3.mode); near(304f, sps3.topH, "SPS3 topH"); near(55.2f, sps3.square, "SPS3 square")
    }
    // A 20:9 phone: topH 344 − 28 − 8 = 308, squares min(58, (308 − 28) / 5) = 56, 2 columns from 912 − 126 = 786. The
    // column 0.4 · (786 − 14 − 14) = 303.2 wide, the games (308 − 14) / 2 = 147 tall; the block top-aligned.
    @Test fun twentyByNinePhone() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 2)
        near(308f, b.topH, "topH")
        for (r in tiles(b, HomeTopKind.GAME)) { near(303.2f, r.w, "game w"); near(147f, r.h, "game h") }
        near(28f, tiles(b, HomeTopKind.TOOL).first().y, "block top")
    }
    // The portrait type (and UNDER) never put a game in the top section; landscape SPS3 puts the first min(games, 2)
    // there, the rest row games (its numbers are HomeBoardTest's; the large face's HomeLargeBoardTest's).
    @Test fun portraitKeepsEveryGameInTheRowsAndSps3PutsTwoInline() {
        val faces = listOf(
            HomeFrame(475f, 291f, true) to HomeToolMode.COLUMN, HomeFrame(800f, 340f, false, 800f / 428f) to HomeToolMode.SPS3,
            HomeFrame(360f, 423f, false) to HomeToolMode.UNDER,
        )
        for ((f, mode) in faces) for (games in 0..3) {
            val b = homeBoard(f, if (f.portrait) 2 else 1, tools = 4, games = games)
            val inline = if (mode == HomeToolMode.SPS3) minOf(games, 2) else 0
            assertEquals(mode, b.mode); assertEquals(inline, b.inlineGames, "$mode $games"); assertEquals(games - inline, b.rowGames, "$mode $games")
            assertEquals(inline, b.top.count { it.kind == HomeTopKind.GAME }, "$mode $games")
        }
        // The cover with 2 games: its pin row.
        val cover = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 3, games = 2)
        near(215.5f, cover.pinW, "cover pinW"); assertEquals(2, cover.rowGames)
    }
    // Focus by the stacked cards. Continue 0, G1 1 (top), G2 2, squares 3 4 / 5 6 / 7 8. RIGHT Continue → G1; DOWN G1 →
    // G2, UP back; RIGHT G1 → the top square, LEFT from it back to G1; RIGHT G2 → the third square row (its rows
    // 120.8–160.2 overlap G2's 147.5–253); LEFT G2 → Continue; DOWN from Continue and G2 stays.
    @Test fun focusMovesThroughTheStackedGames() {
        val s = homeShape(homeBoard(flip, 1, tools = 6, games = 2))
        val m = HomeMemory()
        assertEquals(1, homeStep(0, s, m, NxButton.RIGHT))
        assertEquals(2, homeStep(1, s, m, NxButton.DOWN)); assertEquals(1, homeStep(2, s, m, NxButton.UP))
        assertEquals(3, homeStep(1, s, m, NxButton.RIGHT)); assertEquals(1, homeStep(3, s, m, NxButton.LEFT))
        assertEquals(7, homeStep(2, s, m, NxButton.RIGHT))
        assertEquals(0, homeStep(2, s, m, NxButton.LEFT)); assertEquals(0, homeStep(1, s, m, NxButton.LEFT))
        assertEquals(0, homeStep(0, s, m, NxButton.DOWN)); assertEquals(2, homeStep(2, s, m, NxButton.DOWN))
        assertTrue(homeAtTop(0, s, m)); assertTrue(homeAtTop(1, s, m))
    }
}
