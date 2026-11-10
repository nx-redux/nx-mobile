package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wide face (the Flip, 20:9 phones in landscape): Continue and the first 1–2 games inline in one column (stacked
 * when 2, 40% of the page's width) keep the first screen; one row of the Tools page's tiles across the page a gap under
 * them, a scroll away; the rest row games under it.
 */
class HomeWideBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private val flip = HomeFrame(804f, 261f, false, 804f / 330f)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }.map { it.rect }

    // w 728, topY 2 + 18 + 8 = 28. Continue and the games keep the body, 261 − 28 − 8 = 225: the games one inline
    // column 0.4 · (728 − 14) = 285.6 wide, stacked (225 − 14) / 2 = 105.5 tall; Continue the other 0.6, 428.4, the
    // column at 38 + 428.4 + 14 = 480.4. The tools' row a gap under them, at 28 + 225 + 14 = 267: floor(736 / 88) = 8
    // columns of (728 − 56) / 8 = 84, each 2 · 10 + 0.6 · 84 + 6 + 27.6 + 6 = 110 tall, so 8 slots; 6 tools from the
    // page's left (38), 8 dp apart. The top section 225 + 14 + 110 = 349, the page 28 + 349 + 8 = 385.
    @Test fun twoGamesStackInOneDoubleWideColumn() {
        val b = homeBoard(flip, 1, tools = 6, games = 2)
        assertEquals(HomeToolMode.WIDE, b.mode); assertTrue(b.wide); assertEquals(8, b.slots)
        assertEquals(2, b.inlineGames); assertEquals(0, b.rowGames)
        near(28f, b.topY, "topY"); near(349f, b.topH, "topH"); near(84f, b.toolW, "toolW"); near(110f, b.toolH, "toolH")
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME, HomeTopKind.GAME) + List(6) { HomeTopKind.TOOL }, b.top.map { it.kind })
        val c = tiles(b, HomeTopKind.CONTINUE).single()
        near(38f, c.x, "continue x"); near(28f, c.y, "continue y"); near(428.4f, c.w, "continue w"); near(225f, c.h, "continue h")
        val g = tiles(b, HomeTopKind.GAME)
        near(28f, g[0].y, "game 0 y"); near(147.5f, g[1].y, "game 1 y")
        for (r in g) { near(480.4f, r.x, "game x"); near(285.6f, r.w, "game w"); near(105.5f, r.h, "game h") }
        val sq = tiles(b, HomeTopKind.TOOL)
        for ((i, r) in sq.withIndex()) {
            near(38f + i * (84f + 8f), r.x, "tile $i x"); near(267f, r.y, "tile $i y")
            near(84f, r.w, "tile w"); near(110f, r.h, "tile h")
        }
        near(385f, b.pageH, "pageH")
    }
    // 1 game, 6 tools: one card 285.6 wide, the body's full height (225), at 38 + 428.4 + 14.
    @Test fun oneGameIsOneInlineCard() {
        val b = homeBoard(flip, 1, tools = 6, games = 1)
        assertEquals(1, b.inlineGames)
        near(428.4f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        val g = tiles(b, HomeTopKind.GAME).single()
        near(480.4f, g.x, "game x"); near(28f, g.y, "game y"); near(285.6f, g.w, "game w"); near(225f, g.h, "game h")
    }
    // 4 tools: the first 4 of the row's 8 columns, 4 · 84 + 3 · 8 = 360 wide from 38, at 267; Continue and the game
    // keep their widths whatever the tools: 428.4, the game at 480.4.
    @Test fun oneGameFourTools() {
        val b = homeBoard(flip, 1, tools = 4, games = 1)
        assertEquals(1, b.inlineGames)
        val sq = tiles(b, HomeTopKind.TOOL)
        near(38f, sq.minOf { it.x }, "row x"); near(360f, sq.maxOf { it.x + it.w } - sq.minOf { it.x }, "row w")
        sq.forEach { near(267f, it.y, "row y") }
        near(428.4f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        near(480.4f, tiles(b, HomeTopKind.GAME).single().x, "game x")
    }
    // No tools: the column 0.4 · (728 − 14) = 285.6 wide, flush right; Continue 428.4.
    @Test fun oneGameNoTools() {
        val b = homeBoard(flip, 1, tools = 0, games = 1)
        near(428.4f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        near(285.6f, tiles(b, HomeTopKind.GAME).single().w, "game w")
        val g = tiles(b, HomeTopKind.GAME).single()
        near(38f + 728f, g.x + g.w, "flush right")
    }
    // A tiny window still splits the page 60 : 40 and keeps its tiles on the page. w 224; Continue and the games keep
    // the body, 160 − 36 = 124: the column 0.4 · (224 − 14) = 84 wide, flush right, its games (124 − 14) / 2 = 55 tall,
    // Continue 126. The row a gap under them, at 28 + 124 + 14 = 166: floor(232 / 88) = 2 columns of (224 − 8) / 2 = 108,
    // the icon at its 64 cap, so 2 · 10 + 64 + 6 + 27.6 + 6 = 123.6 tall: 15 tools show 1 and "+14".
    @Test fun aTinyWindowKeepsContinueAndItsTilesOnThePage() {
        val b = homeBoard(HomeFrame(300f, 160f, false, 4f), 1, tools = 15, games = 2)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(2, b.slots); assertEquals(14, b.more)
        near(108f, b.toolW, "toolW"); near(123.6f, b.toolH, "toolH")
        tiles(b, HomeTopKind.CONTINUE).single().let { near(126f, it.w, "continue w"); near(124f, it.h, "continue h: the body") }
        for (g in tiles(b, HomeTopKind.GAME)) { near(84f, g.w, "game w"); near(55f, g.h, "game h"); near(38f + 224f, g.x + g.w, "game right") }
        b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.forEach { near(166f, it.rect.y, "${it.kind} y: a gap under Continue") }
        b.top.forEach { assertTrue(it.rect.x >= 38f - 0.05f && it.rect.x + it.rect.w <= 38f + 224f + 0.05f, "${it.kind} on the page") }
        b.top.forEach { assertTrue(it.rect.y >= b.topY - 0.05f && it.rect.y + it.rect.h <= b.topY + b.topH + 0.05f, "${it.kind} in the top section") }
    }
    @Test fun nothingPinnedContinueFills() {
        val b = homeBoard(flip, 1, tools = 0, games = 0)
        val c = tiles(b, HomeTopKind.CONTINUE).single()
        near(728f, c.w, "continue w"); near(225f, c.h, "continue h"); assertEquals(1, b.top.size)
    }
    // One row of 8: past 8 tools, 7 and "+N", a tool tile like the rest, at the row's right end (38 + 728 − 84 = 682).
    @Test fun moreThanEightToolsShowSevenAndMore() {
        val b = homeBoard(flip, 1, tools = 18, games = 2)
        assertEquals(8, b.slots); assertEquals(11, b.more)
        assertEquals(7, tiles(b, HomeTopKind.TOOL).size); assertEquals(HomeTopKind.MORE, b.top.last().kind)
        b.top.last().rect.let { near(84f, it.w, "more w"); near(110f, it.h, "more h"); near(682f, it.x, "more x"); near(267f, it.y, "more y") }
    }
    // 3 games: the first 2 inline, the third a row game (6 columns of 109.67, shape min(109.67, max(82.25,
    // (261 − 33) · 0.4 = 91.2)) = 91.2, 3 a row). The top section is as with 2 games, 225 + 14 + 110 = 349: Continue
    // 428.4, the games 285.6 wide, stacked 105.5 tall, the tools' row at 267; the row game at 28 + 349 + 14 = 391.
    @Test fun threeGamesPutTwoInlineAndOneInTheRows() {
        val b = homeBoard(flip, 1, tools = 6, games = 3)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames)
        near(349f, b.topH, "topH"); near(110f, b.toolH, "toolH")
        assertEquals(3, b.perRow); near(233.33f, b.pinW, "pinW"); near(91.2f, b.pinH, "pinH")
        near(391f, b.tilesY, "tilesY"); assertTrue(b.tilesY >= 261f, "a scroll away")
        val g = tiles(b, HomeTopKind.GAME)
        assertEquals(2, g.size); for (r in g) { near(285.6f, r.w, "game w"); near(105.5f, r.h, "game h") }
        // The tools' row a gap under Continue, 6 of its 8 columns from the page's left (to 38 + 6 · 84 + 5 · 8 = 582).
        val sq = tiles(b, HomeTopKind.TOOL)
        near(267f, sq.first().y, "row top"); near(38f, sq.first().x, "row left"); near(582f, sq.last().x + sq.last().w, "row right")
        near(428.4f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
    }
    // 4 games: 2 inline as with 3, the other 2 one row (3 a row) at 391; the page ends under it, 391 + 91.2 + 3 + 8.
    @Test fun fourGamesPutTwoInlineAndTwoInTheRows() {
        val b = homeBoard(flip, 1, tools = 6, games = 4)
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames); assertEquals(2, tiles(b, HomeTopKind.GAME).size)
        near(349f, b.topH, "topH"); near(391f, b.tilesY, "tilesY")
        near(38f + 233.33f + 14f, homeBoardPin(b, 1).x, "the second row game beside the first")
        near(391f + 91.2f + 3f + 8f, b.pageH, "pageH: one row")
    }
    // The top section never depends on the row games: 2 or 3 pinned games give the same top, tools and Continue, on the
    // wide face (the Flip: 225 + 14 + 110 = 349) and on SPS3 (800 × 340: 304 + 14 + 109.7 = 427.7); the third game only
    // adds a row below.
    @Test fun aThirdGameKeepsTheTopAndTheSquares() {
        for (f in listOf(flip, HomeFrame(800f, 340f, false, 800f / 428f))) {
            val two = homeBoard(f, 1, tools = 6, games = 2)
            val three = homeBoard(f, 1, tools = 6, games = 3)
            assertEquals(0, two.rowGames, "$f"); assertEquals(1, three.rowGames, "$f")
            near(two.topH, three.topH, "$f topH"); near(two.toolH, three.toolH, "$f toolH")
            assertEquals(two.top, three.top, "$f the top tiles")
        }
        near(349f, homeBoard(flip, 1, 6, 3).topH, "wide topH"); near(110f, homeBoard(flip, 1, 6, 3).toolH, "wide toolH")
        val sps3 = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, 6, 3)
        assertEquals(HomeToolMode.SPS3, sps3.mode); near(427.7f, sps3.topH, "SPS3 topH"); near(109.7f, sps3.toolH, "SPS3 toolH")
    }
    // A 20:9 phone: Continue and the games the body, 344 − 28 − 8 = 308: the column 0.4 · (884 − 14) = 348 wide, the
    // games (308 − 14) / 2 = 147 tall; the row's tiles (10 columns of 81.2, 108.32 tall) at 28 + 308 + 14 = 350; the top
    // section 308 + 14 + 108.32 = 430.32.
    @Test fun twentyByNinePhone() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 2)
        near(430.32f, b.topH, "topH")
        for (r in tiles(b, HomeTopKind.GAME)) { near(348f, r.w, "game w"); near(147f, r.h, "game h") }
        near(350f, tiles(b, HomeTopKind.TOOL).first().y, "row top")
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
    // Focus by the stacked cards. Continue 0 (x 38–466.4), G1 1 (top) and G2 2 (x 480.4–766), over the tools' row
    // 3 … 8 (84 wide, 92 apart from 38: tool 7 spans 406–490, tool 8 498–582). RIGHT Continue → G1; DOWN G1 → G2, UP
    // back; nothing right of the games (switch tab); LEFT from either → Continue. DOWN from Continue → the row's first
    // tool, UP back; DOWN G2 → the first tool under it (7, which reaches under its left edge), UP from 8 → G2; along
    // the row RIGHT and LEFT, null past its ends; with no row games DOWN from a tool stays.
    @Test fun focusMovesThroughTheStackedGames() {
        val s = homeShape(homeBoard(flip, 1, tools = 6, games = 2))
        val m = HomeMemory()
        assertEquals(1, homeStep(0, s, m, NxButton.RIGHT))
        assertEquals(2, homeStep(1, s, m, NxButton.DOWN)); assertEquals(1, homeStep(2, s, m, NxButton.UP))
        assertNull(homeStep(1, s, m, NxButton.RIGHT)); assertNull(homeStep(2, s, m, NxButton.RIGHT))
        assertEquals(0, homeStep(2, s, m, NxButton.LEFT)); assertEquals(0, homeStep(1, s, m, NxButton.LEFT))
        assertEquals(3, homeStep(0, s, m, NxButton.DOWN)); assertEquals(0, homeStep(3, s, m, NxButton.UP))
        assertEquals(7, homeStep(2, s, m, NxButton.DOWN)); assertEquals(2, homeStep(8, s, m, NxButton.UP))
        assertEquals(4, homeStep(3, s, m, NxButton.RIGHT)); assertEquals(7, homeStep(8, s, m, NxButton.LEFT))
        assertNull(homeStep(8, s, m, NxButton.RIGHT)); assertNull(homeStep(3, s, m, NxButton.LEFT))
        assertEquals(6, homeStep(6, s, m, NxButton.DOWN))
        assertTrue(homeAtTop(0, s, m)); assertTrue(homeAtTop(1, s, m)); assertTrue(!homeAtTop(3, s, m))
    }
}
