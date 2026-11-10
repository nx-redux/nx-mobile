package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wide face (the Flip, 20:9 phones in landscape): the Tools page's tiles (80 × 107.6), as many rows as fit the top
 * section, up to 3 columns, top-aligned, the first 1–2 games inline in one column (stacked when 2), 40% of the width
 * left of the tools, the rest row games.
 */
class HomeWideBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private val flip = HomeFrame(804f, 261f, false, 804f / 330f)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }.map { it.rect }

    // w 728, topY 2 + 18 + 8 = 28, topH 261 − 28 − 8 = 225: 2 rows of tiles (floor(233 / 115.6)), so 6 slots; 6 tools 3
    // columns, the block 3 · 80 + 2 · 8 = 256 wide, from 728 − 256 = 472 (page 510), top-aligned, in reading order (2 rows
    // of 3). Left of them 472 − 14 = 458: the games one inline column 0.4 · (458 − 14) = 177.6 wide, stacked
    // (225 − 14) / 2 = 105.5 tall; Continue the other 0.6, 266.4, the column at 38 + 266.4 + 14 = 318.4.
    @Test fun twoGamesStackInOneDoubleWideColumn() {
        val b = homeBoard(flip, 1, tools = 6, games = 2)
        assertEquals(HomeToolMode.WIDE, b.mode); assertTrue(b.wide); assertEquals(6, b.slots)
        assertEquals(2, b.inlineGames); assertEquals(0, b.rowGames)
        near(28f, b.topY, "topY"); near(225f, b.topH, "topH"); near(80f, b.toolW, "toolW"); near(107.6f, b.toolH, "toolH")
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME, HomeTopKind.GAME) + List(6) { HomeTopKind.TOOL }, b.top.map { it.kind })
        val c = tiles(b, HomeTopKind.CONTINUE).single()
        near(38f, c.x, "continue x"); near(28f, c.y, "continue y"); near(266.4f, c.w, "continue w"); near(225f, c.h, "continue h")
        val g = tiles(b, HomeTopKind.GAME)
        near(28f, g[0].y, "game 0 y"); near(147.5f, g[1].y, "game 1 y")
        for (r in g) { near(318.4f, r.x, "game x"); near(177.6f, r.w, "game w"); near(105.5f, r.h, "game h") }
        val sq = tiles(b, HomeTopKind.TOOL)
        for ((i, r) in sq.withIndex()) {
            near(510f + (i % 3) * (80f + 8f), r.x, "tile $i x"); near(28f + (i / 3) * (107.6f + 8f), r.y, "tile $i y")
            near(80f, r.w, "tile w"); near(107.6f, r.h, "tile h")
        }
        near(261f, b.pageH, "pageH")
    }
    // 1 game, 6 tools: one card 177.6 wide, the top's full height, at 38 + 266.4 + 14.
    @Test fun oneGameIsOneInlineCard() {
        val b = homeBoard(flip, 1, tools = 6, games = 1)
        assertEquals(1, b.inlineGames)
        near(266.4f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        val g = tiles(b, HomeTopKind.GAME).single()
        near(318.4f, g.x, "game x"); near(28f, g.y, "game y"); near(177.6f, g.w, "game w"); near(225f, g.h, "game h")
    }
    // 4 tools: 2 columns (ceil(4 / 2)), 168 wide, from 728 − 168 = 560; left of it 546: Continue 0.6 · (546 − 14) = 319.2,
    // the game at 38 + 333.2.
    @Test fun oneGameFourTools() {
        val b = homeBoard(flip, 1, tools = 4, games = 1)
        assertEquals(1, b.inlineGames)
        val sq = tiles(b, HomeTopKind.TOOL)
        near(168f, sq.maxOf { it.x + it.w } - sq.minOf { it.x }, "block w")
        near(319.2f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        near(371.2f, tiles(b, HomeTopKind.GAME).single().x, "game x")
    }
    // No tools: the column 0.4 · (728 − 14) = 285.6 wide, flush right; Continue 428.4.
    @Test fun oneGameNoTools() {
        val b = homeBoard(flip, 1, tools = 0, games = 1)
        near(428.4f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        near(285.6f, tiles(b, HomeTopKind.GAME).single().w, "game w")
        val g = tiles(b, HomeTopKind.GAME).single()
        near(38f + 728f, g.x + g.w, "flush right")
    }
    // A tiny window still splits the room left of the tools 60 : 40 and never crosses a gap before them, and its tools
    // stay on the page. w 224, topH 160 − 36 = 124: one row; 3 columns would be 256, wider than the page, so 2
    // (floor(232 / 88)), 168 from 56: 15 tools show 1 and "+14". Left of them 42: the column 0.4 · (42 − 14) = 11.2,
    // Continue 16.8.
    @Test fun aTinyWindowSplitsTheRoomLeftOfTheTools() {
        val b = homeBoard(HomeFrame(300f, 160f, false, 4f), 1, tools = 15, games = 2)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(2, b.slots); assertEquals(14, b.more)
        near(16.8f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        for (g in tiles(b, HomeTopKind.GAME)) { near(11.2f, g.w, "game w"); near(38f + 56f - 14f, g.x + g.w, "game right") }
        b.top.forEach { assertTrue(it.rect.x >= 38f - 0.05f && it.rect.x + it.rect.w <= 38f + 224f + 0.05f, "${it.kind} on the page") }
    }
    @Test fun nothingPinnedContinueFills() {
        val b = homeBoard(flip, 1, tools = 0, games = 0)
        val c = tiles(b, HomeTopKind.CONTINUE).single()
        near(728f, c.w, "continue w"); near(225f, c.h, "continue h"); assertEquals(1, b.top.size)
    }
    // 3 columns of 2 rows: past 6 tools, 5 and "+N", a tool tile like the rest.
    @Test fun moreThanSixToolsShowFiveAndMore() {
        val b = homeBoard(flip, 1, tools = 18, games = 2)
        assertEquals(6, b.slots); assertEquals(13, b.more)
        assertEquals(5, tiles(b, HomeTopKind.TOOL).size); assertEquals(HomeTopKind.MORE, b.top.last().kind)
        b.top.last().rect.let { near(80f, it.w, "more w"); near(107.6f, it.h, "more h"); near(38f + 728f - 80f, it.x, "more x"); near(28f + 115.6f, it.y, "more y") }
    }
    // 3 games: the first 2 inline, the third a row game a scroll away (6 columns of 109.67, shape min(109.67, max(82.25,
    // (261 − 33) · 0.4 = 91.2)) = 91.2, 3 a row). The top stays the whole body, 225, the tiles as with 2 games; left of
    // the 3 columns 458: Continue 266.4, the games 177.6 wide, stacked 105.5 tall; the row at 28 + 225 + 14 = 267.
    @Test fun threeGamesPutTwoInlineAndOneInTheRows() {
        val b = homeBoard(flip, 1, tools = 6, games = 3)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames)
        near(225f, b.topH, "topH"); near(107.6f, b.toolH, "toolH")
        assertEquals(3, b.perRow); near(233.33f, b.pinW, "pinW"); near(91.2f, b.pinH, "pinH")
        near(267f, b.tilesY, "tilesY"); assertTrue(b.tilesY >= 261f, "a scroll away")
        val g = tiles(b, HomeTopKind.GAME)
        assertEquals(2, g.size); for (r in g) { near(177.6f, r.w, "game w"); near(105.5f, r.h, "game h") }
        // The block top-aligned, 3 columns (ceil(6 / 2)) flush right.
        val sq = tiles(b, HomeTopKind.TOOL)
        near(28f, sq.first().y, "block top"); near(38f + 728f, sq.last().x + sq.last().w, "flush right")
        near(266.4f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
    }
    // 4 games: 2 inline as with 3, the other 2 one row (3 a row) at 267; the page ends under it, 267 + 91.2 + 3 + 8.
    @Test fun fourGamesPutTwoInlineAndTwoInTheRows() {
        val b = homeBoard(flip, 1, tools = 6, games = 4)
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames); assertEquals(2, tiles(b, HomeTopKind.GAME).size)
        near(225f, b.topH, "topH"); near(267f, b.tilesY, "tilesY")
        near(38f + 233.33f + 14f, homeBoardPin(b, 1).x, "the second row game beside the first")
        near(267f + 91.2f + 3f + 8f, b.pageH, "pageH: one row")
    }
    // The top section never depends on the row games: 2 or 3 pinned games give the same top, tools and Continue, on the
    // wide face (the Flip: 225) and on SPS3 (800 × 340: 304), the tiles 107.6 tall on both; the third game only adds a row below.
    @Test fun aThirdGameKeepsTheTopAndTheSquares() {
        for (f in listOf(flip, HomeFrame(800f, 340f, false, 800f / 428f))) {
            val two = homeBoard(f, 1, tools = 6, games = 2)
            val three = homeBoard(f, 1, tools = 6, games = 3)
            assertEquals(0, two.rowGames, "$f"); assertEquals(1, three.rowGames, "$f")
            near(two.topH, three.topH, "$f topH"); near(two.toolH, three.toolH, "$f toolH")
            assertEquals(two.top, three.top, "$f the top tiles")
        }
        near(225f, homeBoard(flip, 1, 6, 3).topH, "wide topH"); near(107.6f, homeBoard(flip, 1, 6, 3).toolH, "wide toolH")
        val sps3 = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, 6, 3)
        assertEquals(HomeToolMode.SPS3, sps3.mode); near(304f, sps3.topH, "SPS3 topH"); near(107.6f, sps3.toolH, "SPS3 toolH")
    }
    // A 20:9 phone: topH 344 − 28 − 8 = 308, 2 rows of tiles, 6 tools in 3 columns from 884 − 256 = 628. The column
    // 0.4 · (628 − 14 − 14) = 240 wide, the games (308 − 14) / 2 = 147 tall; the block top-aligned.
    @Test fun twentyByNinePhone() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 2)
        near(308f, b.topH, "topH")
        for (r in tiles(b, HomeTopKind.GAME)) { near(240f, r.w, "game w"); near(147f, r.h, "game h") }
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
        near(201.5f, cover.pinW, "cover pinW"); assertEquals(2, cover.rowGames)
    }
    // Focus by the stacked cards. Continue 0, G1 1 (top), G2 2, tools 3 4 5 / 6 7 8. RIGHT Continue → G1; DOWN G1 → G2,
    // UP back; RIGHT G1 → the first tool, LEFT from it back to G1; RIGHT G2 → the second tool row (143.6–251.2 overlaps
    // G2's 147.5–253); DOWN from the first row to the second, UP back; LEFT G2 → Continue; DOWN from Continue and G2 stays.
    @Test fun focusMovesThroughTheStackedGames() {
        val s = homeShape(homeBoard(flip, 1, tools = 6, games = 2))
        val m = HomeMemory()
        assertEquals(1, homeStep(0, s, m, NxButton.RIGHT))
        assertEquals(2, homeStep(1, s, m, NxButton.DOWN)); assertEquals(1, homeStep(2, s, m, NxButton.UP))
        assertEquals(3, homeStep(1, s, m, NxButton.RIGHT)); assertEquals(1, homeStep(3, s, m, NxButton.LEFT))
        assertEquals(6, homeStep(2, s, m, NxButton.RIGHT))
        assertEquals(6, homeStep(3, s, m, NxButton.DOWN)); assertEquals(3, homeStep(6, s, m, NxButton.UP)); assertEquals(8, homeStep(5, s, m, NxButton.DOWN))
        assertEquals(0, homeStep(2, s, m, NxButton.LEFT)); assertEquals(0, homeStep(1, s, m, NxButton.LEFT))
        assertEquals(0, homeStep(0, s, m, NxButton.DOWN)); assertEquals(2, homeStep(2, s, m, NxButton.DOWN))
        assertTrue(homeAtTop(0, s, m)); assertTrue(homeAtTop(1, s, m))
    }
}
