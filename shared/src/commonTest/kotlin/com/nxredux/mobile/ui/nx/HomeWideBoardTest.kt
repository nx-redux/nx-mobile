package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wide face (the Flip, 20:9 phones in landscape): Continue and the first 1–2 games inline in one column (stacked
 * when 2, 40% of the page's width) keep the first screen; below them the remaining row games, then the pinned tools
 * band ([HomeBoard.toolTiles], matching [HomeBoard.perRow] columns and [HomeBoard.pinW] width), then the shelves.
 */
class HomeWideBoardTest {
    private val flip = HomeFrame(804f, 261f, false, 804f / 330f)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }.map { it.rect }

    // w 728, topY 2 + 18 + 8 = 28. Continue and the games keep the body, 261 − 28 − 8 = 225: the games one inline
    // column 0.4 · (728 − 14) = 285.6 wide, stacked (225 − 14) / 2 = 105.5 tall; Continue the other 0.6, 428.4, the
    // column at 38 + 428.4 + 14 = 480.4. No row games, so the 6 tools sit in 2 rows of 3 (perRow = 3, pinW = 233.33,
    // pinH = 91.2, toolH = max(91.2 · 0.58, 56) = 56) from 28 + 225 + 14 = 267.
    @Test fun twoGamesStackInOneDoubleWideColumn() {
        val b = homeBoard(flip, 1, tools = 6, games = 2)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more)
        assertEquals(2, b.inlineGames); assertEquals(0, b.rowGames)
        val th = 91.2f
        near(28f, b.topY, "topY"); near(225f, b.topH, "topH"); near(233.33f, b.toolW, "toolW"); near(th, b.toolH, "toolH")
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME, HomeTopKind.GAME), b.top.map { it.kind })
        val c = tiles(b, HomeTopKind.CONTINUE).single()
        near(38f, c.x, "continue x"); near(28f, c.y, "continue y"); near(428.4f, c.w, "continue w"); near(225f, c.h, "continue h")
        val g = tiles(b, HomeTopKind.GAME)
        near(28f, g[0].y, "game 0 y"); near(147.5f, g[1].y, "game 1 y")
        for (r in g) { near(480.4f, r.x, "game x"); near(285.6f, r.w, "game w"); near(105.5f, r.h, "game h") }
        assertEquals(6, b.toolTiles.size)
        for ((i, r) in b.toolTiles.withIndex()) {
            near(38f + (i % 3) * (233.33f + 14f), r.x, "tile $i x")
            near(267f + (i / 3) * (th + 14f), r.y, "tile $i y")
            near(233.33f, r.w, "tile w"); near(th, r.h, "tile h")
        }
        near(267f + 2 * th + 14f + 3f + 8f, b.pageH, "pageH")
    }
    // 1 game, 6 tools: one card 285.6 wide, the body's full height (225), at 38 + 428.4 + 14.
    @Test fun oneGameIsOneInlineCard() {
        val b = homeBoard(flip, 1, tools = 6, games = 1)
        assertEquals(1, b.inlineGames)
        near(428.4f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
        val g = tiles(b, HomeTopKind.GAME).single()
        near(480.4f, g.x, "game x"); near(28f, g.y, "game y"); near(285.6f, g.w, "game w"); near(225f, g.h, "game h")
    }
    // 4 tools: 3 on the first row and 1 on the second, matching the 3-column pin grid from 267.
    @Test fun oneGameFourTools() {
        val b = homeBoard(flip, 1, tools = 4, games = 1)
        assertEquals(1, b.inlineGames); assertEquals(4, b.toolTiles.size)
        near(38f, b.toolTiles[0].x, "row 0 col 0"); near(267f, b.toolTiles[0].y, "row 0 y")
        near(38f, b.toolTiles[3].x, "row 1 col 0"); near(267f + b.toolH + 14f, b.toolTiles[3].y, "row 1 y")
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
        assertTrue(b.toolTiles.isEmpty())
    }
    // A tiny window still splits the page 60 : 40 and keeps every tile on the page; all 15 tools show in rows of perRow.
    @Test fun aTinyWindowKeepsContinueAndItsTilesOnThePage() {
        val b = homeBoard(HomeFrame(300f, 160f, false, 4f), 1, tools = 15, games = 2)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more)
        assertEquals(15, b.toolTiles.size)
        near(b.pinW, b.toolW, "toolW"); near(b.pinH, b.toolH, "toolH")
        tiles(b, HomeTopKind.CONTINUE).single().let { near(126f, it.w, "continue w"); near(124f, it.h, "continue h: the body") }
        for (g in tiles(b, HomeTopKind.GAME)) { near(84f, g.w, "game w"); near(55f, g.h, "game h"); near(38f + 224f, g.x + g.w, "game right") }
        near(166f, b.toolTiles.first().y, "first tool row a gap under Continue")
        b.top.forEach { assertTrue(it.rect.x >= 38f - 0.05f && it.rect.x + it.rect.w <= 38f + 224f + 0.05f, "${it.kind} on the page") }
        b.top.forEach { assertTrue(it.rect.y >= b.topY - 0.05f && it.rect.y + it.rect.h <= b.topY + b.topH + 0.05f, "${it.kind} in the top section") }
        b.toolTiles.forEach { assertTrue(it.x >= 38f - 0.05f && it.x + it.w <= 38f + 224f + 0.05f, "tool on the page") }
    }
    @Test fun nothingPinnedContinueFills() {
        val b = homeBoard(flip, 1, tools = 0, games = 0)
        val c = tiles(b, HomeTopKind.CONTINUE).single()
        near(728f, c.w, "continue w"); near(225f, c.h, "continue h"); assertEquals(1, b.top.size)
    }
    // Every pinned tool wraps into rows of perRow (no "+N" overflow).
    @Test fun allToolsWrapIntoBandRowsWithoutMore() {
        val b = homeBoard(flip, 1, tools = 18, games = 2)
        assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more)
        assertEquals(18, b.toolTiles.size)
        b.toolTiles.last().let {
            near(38f + 2 * (b.pinW + 14f), it.x, "last tool x")
            near(267f + 5 * (b.toolH + 14f), it.y, "last tool y")
        }
    }
    // 3 games: the first 2 inline, the third a row game at 28 + 225 + 14 = 267, followed by the 6 tools a gap below it.
    @Test fun threeGamesPutTwoInlineOneInRowsAndToolsAfter() {
        val b = homeBoard(flip, 1, tools = 6, games = 3)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames)
        near(225f, b.topH, "topH"); near(91.2f, b.toolH, "toolH")
        assertEquals(3, b.perRow); near(233.33f, b.pinW, "pinW"); near(91.2f, b.pinH, "pinH")
        near(267f, b.tilesY, "tilesY"); assertTrue(b.tilesY >= 261f, "a scroll away")
        val g = tiles(b, HomeTopKind.GAME)
        assertEquals(2, g.size); for (r in g) { near(285.6f, r.w, "game w"); near(105.5f, r.h, "game h") }
        near(267f + 91.2f + 14f, b.toolTiles.first().y, "tools a gap under row games")
        near(38f, b.toolTiles.first().x, "tools left")
        near(428.4f, tiles(b, HomeTopKind.CONTINUE).single().w, "continue w")
    }
    // 4 games, 6 tools: 2 inline, 2 row games at 267 (1 row), then 6 tools in 2 rows of 3 below them.
    @Test fun fourGamesPutTwoInlineTwoInRowsAndToolsAfter() {
        val b = homeBoard(flip, 1, tools = 6, games = 4)
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames); assertEquals(2, tiles(b, HomeTopKind.GAME).size)
        near(225f, b.topH, "topH"); near(267f, b.tilesY, "tilesY")
        near(38f + 233.33f + 14f, homeBoardPin(b, 1).x, "the second row game beside the first")
        val lastTool = b.toolTiles.last()
        near(lastTool.y + lastTool.h + 3f + 8f, b.pageH, "pageH ends under tools")
    }
    // The top section never depends on the row games or tools.
    @Test fun aThirdGameKeepsTheTop() {
        for (f in listOf(flip, HomeFrame(800f, 340f, false, 800f / 428f))) {
            val two = homeBoard(f, 1, tools = 6, games = 2)
            val three = homeBoard(f, 1, tools = 6, games = 3)
            assertEquals(0, two.rowGames, "$f"); assertEquals(1, three.rowGames, "$f")
            near(two.topH, three.topH, "$f topH"); near(two.toolH, three.toolH, "$f toolH")
            assertEquals(two.top, three.top, "$f the top tiles")
        }
        near(225f, homeBoard(flip, 1, 6, 3).topH, "wide topH")
        val sps3 = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, 6, 3)
        assertEquals(HomeToolMode.SPS3, sps3.mode); near(304f, sps3.topH, "SPS3 topH")
    }
    // A 20:9 phone: Continue and the games the body, 344 − 28 − 8 = 308: the column 0.4 · (884 − 14) = 348 wide, the
    // games (308 − 14) / 2 = 147 tall; tools start a gap under Continue at 28 + 308 + 14 = 350.
    @Test fun twentyByNinePhone() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 2)
        near(308f, b.topH, "topH")
        for (r in tiles(b, HomeTopKind.GAME)) { near(348f, r.w, "game w"); near(147f, r.h, "game h") }
        near(350f, b.toolTiles.first().y, "tools top")
    }
    // The portrait type (and UNDER) never put a game in the top section; landscape SPS3 puts the first min(games, 2)
    // there, the rest row games.
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
            assertEquals(4, b.toolTiles.size, "$mode $games")
        }
        val cover = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 3, games = 2)
        near(201.5f, cover.pinW, "cover pinW"); assertEquals(2, cover.rowGames)
    }
    // Focus by the stacked cards. Continue 0 (x 38–466.4), G1 1 (top) and G2 2 (x 480.4–766), over the tools band
    // 3 4 5 / 6 7 8 (3 a row, 233.33 wide: x 38, 285.33, 532.67).
    @Test fun focusMovesThroughTheStackedGames() {
        val s = homeShape(homeBoard(flip, 1, tools = 6, games = 2))
        val m = HomeMemory()
        assertEquals(1, homeStep(0, s, m, NxButton.RIGHT))
        assertEquals(2, homeStep(1, s, m, NxButton.DOWN)); assertEquals(1, homeStep(2, s, m, NxButton.UP))
        assertNull(homeStep(1, s, m, NxButton.RIGHT)); assertNull(homeStep(2, s, m, NxButton.RIGHT))
        assertEquals(0, homeStep(2, s, m, NxButton.LEFT)); assertEquals(0, homeStep(1, s, m, NxButton.LEFT))
        assertEquals(3, homeStep(0, s, m, NxButton.DOWN)); assertEquals(0, homeStep(3, s, m, NxButton.UP))
        assertEquals(4, homeStep(2, s, m, NxButton.DOWN))   // G2 (480.4..766) overlaps tool 1 (285.33..518.67) first
        assertEquals(4, homeStep(3, s, m, NxButton.RIGHT)); assertEquals(5, homeStep(4, s, m, NxButton.RIGHT))
        assertNull(homeStep(5, s, m, NxButton.RIGHT)); assertNull(homeStep(3, s, m, NxButton.LEFT))
        assertEquals(6, homeStep(3, s, m, NxButton.DOWN)); assertEquals(3, homeStep(6, s, m, NxButton.UP))
        assertEquals(6, homeStep(6, s, m, NxButton.DOWN))
        assertTrue(homeAtTop(0, s, m)); assertTrue(homeAtTop(1, s, m)); assertTrue(!homeAtTop(3, s, m))
    }
}
