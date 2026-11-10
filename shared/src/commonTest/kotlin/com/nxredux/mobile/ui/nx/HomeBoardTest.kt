package com.nxredux.mobile.ui.nx

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }

    // The Tools page's tile, w wide: 2 · 10 + 0.6 · w + 6 + 2 · 12 · 1.15 + 6 = 59.6 + 0.6 · w tall at a text scale of 1,
    // in as many columns as fit the page at 80 dp with 8 dp between, stretched to fill the row.

    // The mockup's landscape (WIDE) with 4 tools. Continue and the first 2 of 6 games keep the first screen (topY
    // 2 + 18 + 8 = 28; the whole body 344 − 28 − 8 = 308) across the page (884 between the insets): the inline column
    // 0.4 · (884 − 14) = 348, Continue the other 0.6, 522. A gap under them, at 28 + 308 + 14 = 350, one row of the
    // Tools page's tiles: floor(892 / 88) = 10 columns of (884 − 72) / 10 = 81.2, each 59.6 + 48.72 = 108.32 tall, so 10
    // slots, the 4 from the left. The top section is both, 308 + 14 + 108.32 = 430.32; the other 4 games are row games a
    // gap under it, from 28 + 430.32 + 14 = 472.32, at their full shape 124.4.
    @Test fun wideFacePutsTheToolRowUnderContinue() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), stripLines = 1, tools = 4, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(10, b.slots); assertEquals(0, b.more)
        near(28f, b.topY, "topY"); near(430.32f, b.topH, "topH"); near(81.2f, b.toolW, "toolW"); near(108.32f, b.toolH, "toolH")
        near(toolTileHeight(81.2f, 1f), b.toolH, "the Tools page's height")
        tiles(b, HomeTopKind.CONTINUE).single().rect.let { near(522f, it.w, "continue w"); near(308f, it.h, "continue h: the body") }
        assertEquals(4, tiles(b, HomeTopKind.TOOL).size); assertEquals(0, tiles(b, HomeTopKind.MORE).size)
        tiles(b, HomeTopKind.TOOL).map { it.rect }.forEachIndexed { i, r ->
            near(38f + i * (81.2f + 8f), r.x, "tool $i x"); near(350f, r.y, "tool $i y: a gap under Continue")
            assertTrue(r.y >= 344f, "tool $i a scroll away")
        }
        assertEquals(2, b.inlineGames); assertEquals(2, tiles(b, HomeTopKind.GAME).size)
        tiles(b, HomeTopKind.GAME).forEach { near(38f + 522f + 14f, it.rect.x, "game x"); near(348f, it.rect.w, "game w"); near(147f, it.rect.h, "game h") }
        assertEquals(3, b.perRow); near(285.33f, b.pinW, "pinW"); near(124.4f, b.pinH, "pinH"); assertEquals(4, b.rowGames)
        near(472.32f, b.tilesY, "the rows a gap under the tools")
    }
    // 6 tools: the first 6 of the row's 10 columns, from the page's left, in reading order, 8 dp apart, all at 350.
    // Continue's width never follows the tools: 0.6 · (884 − 14) = 522.
    @Test fun wideToolsFillOneRowFromTheLeft() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(10, b.slots); assertEquals(0, b.more)
        near(522f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        val sq = tiles(b, HomeTopKind.TOOL).map { it.rect }
        assertEquals(6, sq.size); assertEquals(0, tiles(b, HomeTopKind.MORE).size)
        for ((i, r) in sq.withIndex()) {
            near(38f + i * (81.2f + 8f), r.x, "tile $i x"); near(350f, r.y, "tile $i y")
            near(81.2f, r.w, "tile $i w"); near(108.32f, r.h, "tile $i h")
        }
        assertEquals(4, b.rowGames)
    }
    // Past the row's 10 slots: 9 tools and "+N", the last tile, at the row's right end (38 + 884 − 81.2 = 840.8).
    @Test fun widePastTenToolsShowNineAndMore() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 17, games = 6)
        assertEquals(10, b.slots); assertEquals(17 - 10 + 1, b.more)
        assertEquals(9, tiles(b, HomeTopKind.TOOL).size)
        val more = b.top.last(); assertEquals(HomeTopKind.MORE, more.kind)
        near(840.8f, more.rect.x, "more x: the row's right end"); near(350f, more.rect.y, "more y: the row")
        near(81.2f, more.rect.w, "more w: a tool tile"); near(108.32f, more.rect.h, "more h: a tool tile")
    }
    // topY 28: Continue and the games keep the whole body, 248 − 28 − 8 = 212; 2 of the 4 games inline
    // (0.4 · (764 − 14) = 300 wide), Continue 450. The row across 764: floor(772 / 88) = 8 columns of
    // (764 − 56) / 8 = 88.5, each 59.6 + 53.1 = 112.7 tall, so 8 slots, a gap under them; the top section
    // 212 + 14 + 112.7 = 338.7. The other 2 games are row games under it.
    @Test fun flipLandscapeIsWide() {
        val b = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(8, b.slots); near(88.5f, b.toolW, "toolW"); near(112.7f, b.toolH, "toolH")
        tiles(b, HomeTopKind.CONTINUE).single().rect.let { near(450f, it.w, "continue w"); near(212f, it.h, "continue h") }
        near(338.7f, b.topH, "topH"); near(28f + 212f + 14f, tiles(b, HomeTopKind.TOOL).first().rect.y, "the row a gap under Continue")
        near(245.33f, b.pinW, "pinW"); near(86.75f, b.pinH, "pinH"); assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames)
    }
    // SPS3: Continue and the first 2 games keep the first screen; one row of tool tiles across the page a gap under
    // them. Landscape, so the strip is one line (2 + 18 + 8 = 28); the two-line reference (48) is in
    // stripLinesFollowTheTextScale. The unfolded Fold is LARGE, so SPS3 is checked on a sub-840 landscape split.
    // w 724, columns 109, row shape min(109, (340 − 33) · 0.4 = 122.8) = 109; Continue and the games the whole body
    // 340 − 28 − 8 = 304: the inline column 0.4 · (724 − 14) = 284, Continue 426. The row: floor(732 / 88) = 8 columns
    // of (724 − 56) / 8 = 83.5, each 59.6 + 50.1 = 109.7 tall, at 28 + 304 + 14 = 346; the top section
    // 304 + 14 + 109.7 = 427.7.
    @Test fun landscapeSplitIsSps3() {
        val b = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(8, b.slots); near(28f, b.topY, "topY"); near(427.7f, b.topH, "topH")
        near(83.5f, b.toolW, "toolW"); near(109.7f, b.toolH, "toolH")
        tiles(b, HomeTopKind.CONTINUE).single().rect.let { near(426f, it.w, "continue w"); near(304f, it.h, "continue h: the body") }
        tiles(b, HomeTopKind.TOOL).map { it.rect }.forEachIndexed { i, r ->
            near(38f + i * (83.5f + 8f), r.x, "tool $i x"); near(346f, r.y, "tool $i y")
        }
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames)
    }
    // SPS3's inline games (800 × 340, 3 tools). Continue and the games are the body, 340 − 28 − 8 = 304, whatever the
    // games: the column 0.4 · (724 − 14) = 284 at 38 + 426 + 14 = 478, Continue 426; 1 game the full 304, 2 stacked
    // (304 − 14) / 2 = 145 tall. The top section with the tools' row 427.7, so the page with nothing under it
    // 28 + 427.7 + 8 = 463.7. 3 games: the same 2 inline and 1 row game under the tools, at 28 + 427.7 + 14 = 469.7, its
    // full shape 109.
    @Test fun sps3PutsTheFirstTwoGamesInline() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val one = homeBoard(f, 1, tools = 3, games = 1)
        assertEquals(HomeToolMode.SPS3, one.mode); assertEquals(1, one.inlineGames); assertEquals(0, one.rowGames)
        near(427.7f, one.topH, "1: topH"); near(109.7f, one.toolH, "1: toolH"); near(463.7f, one.pageH, "1: pageH")
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME) + List(3) { HomeTopKind.TOOL }, one.top.map { it.kind })
        tiles(one, HomeTopKind.CONTINUE).single().rect.let { near(426f, it.w, "1: continue w"); near(304f, it.h, "1: continue h") }
        tiles(one, HomeTopKind.GAME).single().rect.let { near(478f, it.x, "1: game x"); near(28f, it.y, "1: game y"); near(284f, it.w, "1: game w"); near(304f, it.h, "1: game h") }
        val two = homeBoard(f, 1, tools = 3, games = 2)
        assertEquals(2, two.inlineGames); assertEquals(0, two.rowGames); near(427.7f, two.topH, "2: topH")
        val g2 = tiles(two, HomeTopKind.GAME).map { it.rect }
        near(28f, g2[0].y, "2: game 0 y"); near(28f + 145f + 14f, g2[1].y, "2: game 1 y")
        g2.forEach { near(284f, it.w, "2: game w"); near(145f, it.h, "2: game h") }
        val three = homeBoard(f, 1, tools = 3, games = 3)
        assertEquals(2, three.inlineGames); assertEquals(1, three.rowGames); near(427.7f, three.topH, "3: topH")
        tiles(three, HomeTopKind.GAME).forEach { near(284f, it.rect.w, "3: game w"); near(145f, it.rect.h, "3: game h") }
        near(469.7f, three.tilesY, "3: tilesY"); near(109f, three.pinH, "3: pinH")
    }
    // SPS3 (a sub-840 landscape split) past its row's 8 slots: 7 tools and "+N" last, at the row's right end
    // (38 + 724 − 83.5 = 678.5); 12 tools "+5".
    @Test fun sps3PastEightToolsShowsSevenAndMore() {
        for ((tools, more) in listOf(9 to 2, 10 to 3, 12 to 5)) {
            val b = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, tools = tools, games = 4)
            assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(8, b.slots, "$tools: slots"); assertEquals(more, b.more, "$tools: more")
            assertEquals(7, tiles(b, HomeTopKind.TOOL).size, "$tools: tools shown")
            near(426f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "$tools: continue w")
            val last = b.top.last(); assertEquals(HomeTopKind.MORE, last.kind)
            near(678.5f, last.rect.x, "$tools: more x: the row's right end"); near(346f, last.rect.y, "$tools: more y: the row")
        }
    }
    // Each face's slots are the columns that fit its page times its rows, whatever the tool count: the Flip's wide face 8
    // (one row across 764 dp), the portrait type 8 (2 rows of the 4 columns across the Fold cover), UNDER 3 (one row
    // across 284 dp).
    @Test fun facesSlotsAreTheTilesThatFit() {
        assertEquals(8, homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 8, games = 4).slots)   // WIDE
        assertEquals(8, homeBoard(HomeFrame(475f, 291f, true), 2, tools = 8, games = 4).slots)                 // COLUMN
        assertEquals(3, homeBoard(HomeFrame(360f, 423f, false), 2, tools = 8, games = 4).slots)                 // UNDER
    }
    // The portrait type's tiles are the Tools page's grid across the page: on the Fold cover (415 dp between the insets)
    // floor(423 / 88) = 4 columns of (415 − 24) / 4 = 97.75, each 2 · 10 + 0.6 · 97.75 + 6 + 27.6 + 6 = 118.25 tall.
    // Two 18 dp strip lines: topY 48; the top section 235 (the body). With 3 tools one row under Continue: Continue
    // across the page (415), 235 − 12 − 118.25 = 104.75 tall, under the 145 floor, so 145; the row at 48 + 145 + 12 = 205;
    // the top section 145 + 12 + 118.25 = 275.25, past the body (the page scrolls). The pin rows from
    // 48 + 275.25 + 12 = 335.25, 201.5 × 106.59: 1.5 times ([HOME_PORTRAIT_PIN_TALL]) their 4:3 shape (94.75 · 3 / 4).
    @Test fun foldCoverIsColumn() {
        val f = HomeFrame(475f, 291f, true)
        val b = homeBoard(f, 2, tools = 3, games = 4)
        assertEquals(HomeToolMode.COLUMN, b.mode); assertEquals(8, b.slots); near(48f, b.topY, "topY"); near(275.25f, b.topH, "topH")
        near(97.75f, b.toolW, "toolW"); near(118.25f, b.toolH, "toolH")
        val g = gridFixedTools(475f, 0f, 30f, 30f, 1f)
        assertEquals(4, g.columns, "the Tools page's columns"); near(g.tileW, b.toolW, "the Tools page's width"); near(g.tileH, b.toolH, "the Tools page's height")
        near(106.59f, b.pinH, "pinH"); near(1.5f * (94.75f * 3 / 4), b.pinH, "pinH: 1.5 times the 4:3 half shape"); near(335.25f, b.tilesY, "tilesY")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(30f, c.x, "continue x"); near(415f, c.w, "continue w: across the page"); near(145f, c.h, "continue h: the floor")
        tiles(b, HomeTopKind.TOOL).map { it.rect }.forEachIndexed { i, r ->
            near(30f + i * (97.75f + 8f), r.x, "tool $i x"); near(205f, r.y, "tool $i y: a gap under Continue")
        }
        assertEquals(2, b.perRow); near(201.5f, b.pinW, "pinW")
    }
    // UNDER is a tall window without the pad: inset 38, w 284, gap 14, topY 48. One row of the Tools page's tiles across
    // it: floor(292 / 88) = 3 columns of (284 − 16) / 3 = 89.33, 2 · 10 + 53.6 + 6 + 27.6 + 6 = 113.2 tall. topH the cap
    // 284 / 1.5 + 14 + 113.2 = 316.53, Continue 316.53 − 14 − 113.2 = 189.33 tall, the row under it at 48 + 203.33. 5 tools
    // in 3 slots: 2 and "+3". 3 pin columns would be 85.33, under the least tile, so 2 of 135: pinW 135, pinH 101.25.
    @Test fun flipTallIsUnder() {
        val b = homeBoard(HomeFrame(360f, 423f, false), 2, tools = 5, games = 4)
        assertEquals(HomeToolMode.UNDER, b.mode); near(316.53f, b.topH, "topH"); near(89.33f, b.toolW, "toolW"); near(113.2f, b.toolH, "toolH")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(284f, c.w, "continue w"); near(189.33f, c.h, "continue h")
        assertEquals(3, b.slots); assertEquals(3, b.more)
        assertEquals(listOf(HomeTopKind.TOOL, HomeTopKind.TOOL, HomeTopKind.MORE), b.top.drop(1).map { it.kind })
        b.top.drop(1).forEachIndexed { i, t -> near(38f + i * (89.33f + 8f), t.rect.x, "tile $i x"); near(48f + 189.33f + 14f, t.rect.y, "tile $i y") }
        near(135f, b.pinW, "pinW"); near(101.25f, b.pinH, "pinH")
    }

    // The portrait type's tools fill rows of 4 under Continue (the Fold cover, the top section 235 from 48), 8 dp apart
    // both ways, in reading order: up to 4 one row, 5–8 two, past 8 seven and "+N" last. Continue stays across the page,
    // at the 145 floor; the top section is Continue, a gap and the rows (one row 275.25, two 145 + 12 + 2 · 118.25 + 8 =
    // 401.5); the pin rows follow a gap under it.
    @Test fun portraitToolsFillTwoRowsUnderContinue() {
        val f = HomeFrame(475f, 291f, true)
        for (tools in 1..12) {
            val b = homeBoard(f, 2, tools = tools, games = 4)
            val rows = if (tools <= 4) 1 else 2
            val topH = 145f + 12f + rows * 118.25f + (rows - 1) * 8f
            assertEquals(HomeToolMode.COLUMN, b.mode); near(topH, b.topH, "$tools: topH"); near(118.25f, b.toolH, "$tools: toolH")
            near(106.59f, b.pinH, "$tools: pinH"); near(48f + topH + 12f, b.tilesY, "$tools: tilesY")
            assertEquals(8, b.slots, "$tools: slots")
            assertEquals(if (tools > 8) tools - 8 + 1 else 0, b.more, "$tools: more")
            val sq = b.top.filter { it.kind != HomeTopKind.CONTINUE }
            assertEquals(min(tools, 8), sq.size, "$tools: tiles")
            for ((i, t) in sq.withIndex()) {
                near(30f + (i % 4) * (97.75f + 8f), t.rect.x, "$tools: tile $i x")
                near(48f + 145f + 12f + (i / 4) * (118.25f + 8f), t.rect.y, "$tools: tile $i y")
                assertEquals(if (tools > 8 && i == 7) HomeTopKind.MORE else HomeTopKind.TOOL, t.kind, "$tools: tile $i kind")
            }
            val c = tiles(b, HomeTopKind.CONTINUE).single().rect
            near(415f, c.w, "$tools: continue w"); near(145f, c.h, "$tools: continue h")
        }
        val none = homeBoard(f, 2, tools = 0, games = 4)
        near(235f, none.topH, "no tools: the body"); near(0f, none.toolW, "no tools: no tile")
        tiles(none, HomeTopKind.CONTINUE).single().rect.let { near(415f, it.w, "no tools: full width"); near(235f, it.h, "no tools: today's height") }
    }
    // Continue gives the tool rows its height down to the 145 floor: with the tab row and stats hidden the Fold cover's
    // top section is 335 − 8 − 8 = 319, so one row leaves Continue 319 − 12 − 118.25 = 188.75 and the top section stays
    // 319; two rows would leave it 62.5, so it keeps 145 and the top section grows to 401.5.
    @Test fun portraitContinueShrinksToTheFloorForTheRows() {
        val f = HomeFrame(475f, 335f, true, titleHidden = true, statsHidden = true)
        val one = homeBoard(f, 2, tools = 4, games = 0)
        near(319f, one.topH, "one row: the top section kept")
        near(188.75f, tiles(one, HomeTopKind.CONTINUE).single().rect.h, "one row: Continue gives way")
        near(8f + 188.75f + 12f, tiles(one, HomeTopKind.TOOL).first().rect.y, "one row: under Continue")
        val two = homeBoard(f, 2, tools = 5, games = 0)
        near(145f, tiles(two, HomeTopKind.CONTINUE).single().rect.h, "two rows: the floor"); near(401.5f, two.topH, "two rows: the page grows")
        near(homeBoard(f, 2, tools = 0, games = 0).topH, 319f, "no tools: the same top section")
    }
    // The tile follows the system font scale as the Tools page's does: on the Fold cover at 1.3 its two name lines take
    // 2 · 12 · 1.15 · 1.3 = 35.88, so 126.53 tall; in the landscape split's row 83.5 wide and 82.1 + 35.88 = 117.98 tall.
    @Test fun toolTilesFollowTheFontScale() {
        near(126.53f, homeBoard(HomeFrame(475f, 291f, true, textScale = 1.3f), 2, tools = 3, games = 0).toolH, "portrait at 1.3")
        val land = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f, textScale = 1.3f), 1, tools = 3, games = 0)
        near(83.5f, land.toolW, "landscape at 1.3: w"); near(117.98f, land.toolH, "landscape at 1.3: h")
        near(toolTileHeight(83.5f, 1.3f), land.toolH, "the Tools page's height")
    }
    // A taller portrait-type body (the Flip's inner display, 300 dp between the insets) stops its top section at the Fold
    // cover's 235 (from 48): 3 columns of 94.67, each 116.4 tall; 7 tools in 6 slots, two rows (5 and "+2"), so Continue
    // keeps the 145 floor and the top section is 145 + 12 + 2 · 116.4 + 8 = 397.8. The pin rows follow from
    // 48 + 397.8 + 12 at 1.5 times their 4:3 shape (3 columns of 92: 69, so 103.5), the first still on the 640 dp screen.
    @Test fun tallPortraitCapsTheTopAtTheFoldCover() {
        val b = homeBoard(HomeFrame(360f, 640f, true), 2, tools = 7, games = 6)
        assertEquals(HomeToolMode.COLUMN, b.mode); assertEquals(6, b.slots); assertEquals(2, b.more); near(397.8f, b.topH, "topH")
        near(94.67f, b.toolW, "toolW"); near(116.4f, b.toolH, "toolH"); near(103.5f, b.pinH, "pinH"); near(144f, b.pinW, "pinW"); assertEquals(2, b.perRow)
        near(48f + 397.8f + 12f, b.tilesY, "tilesY"); near(145f, tiles(b, HomeTopKind.CONTINUE).single().rect.h, "continue h")
        near(b.tilesY + 3 * 103.5f + 2 * 12f + 3f + 8f, b.pageH, "pageH: three rows")
        assertTrue(b.tilesY + b.pinH + 3f <= 640f - 8f, "the first row on the first screen")
        near(235f, homeBoard(HomeFrame(360f, 640f, true), 2, tools = 0, games = 6).topH, "no tools: the Fold cover's cap")
        // A shorter body than the Fold cover's takes the body; a tiny one keeps the 145 floor and scrolls.
        near(260f - 48f - 8f, homeBoard(HomeFrame(475f, 260f, true), 2, tools = 0, games = 2).topH, "short: the body")
        near(145f, homeBoard(HomeFrame(475f, 150f, true), 2, tools = 0, games = 2).topH, "tiny: the floor")
    }
    // A landscape halves body (not the portrait type): Continue and the first 2 games keep the cap
    // (628 − 14) / 2 / 1.17 = 262.39: the inline column 0.4 · (628 − 14) = 245.6, Continue 368.4. A gap under them the
    // row across 628: floor(636 / 88) = 7 columns of (628 − 48) / 7 = 82.86, each 59.6 + 49.71 = 109.31 tall; the top
    // section 262.39 + 14 + 109.31 = 385.71.
    @Test fun largePortraitIsColumn() {
        val b = homeBoard(HomeFrame(704f, 845f, false), 1, tools = 3, games = 4)   // not the portrait type: one strip line
        assertEquals(HomeToolMode.COLUMN, b.mode); assertEquals(7, b.slots); near(385.71f, b.topH, "topH"); near(109.31f, b.toolH, "toolH")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(368.4f, c.w, "continue w"); near(262.39f, c.h, "continue h: the cap")
        tiles(b, HomeTopKind.TOOL).forEach { near(c.y + c.h + 14f, it.rect.y, "a gap under Continue") }
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames)
    }
    // The strip's line boxes grow with the text scale (the system font scale), so the top section moves down
    // with them; at 1.0, or a smaller font (text shrinking inside the spec's box), the reference geometry holds.
    // The strip's type is 14 sp on 18 dp in both orientations, so the line box is 18 at 1.0 and 23.4 at 1.3; one
    // landscape line 28, two 48.
    @Test fun stripLinesFollowTheTextScale() {
        assertEquals(14f, HOME_STRIP_TYPE.sp); assertEquals(18f, HOME_STRIP_TYPE.lineH)
        val land = HomeFrame(960f, 344f, false, 960f / 432f)
        near(18f, homeStripLineH(land), "line box at 1.0")
        near(28f, homeBoard(land, 1, tools = 5, games = 6).topY, "1.0")
        near(18f, homeStripLineH(land.copy(textScale = 0.85f)), "small UI scale: the spec's box")
        near(28f, homeBoard(land.copy(textScale = 0.85f), 1, tools = 5, games = 6).topY, "small UI scale")
        near(23.4f, homeStripLineH(land.copy(textScale = 1.3f)), "line box at 1.3")
        near(2f + 23.4f + 8f, homeBoard(land.copy(textScale = 1.3f), 1, tools = 5, games = 6).topY, "1.3, one line")
        val large = HomeFrame(933f, 616f, false, 933f / 704f)
        near(48f, homeBoard(large, 2, tools = 3, games = 4).topY, "two lines at 1.0")
        val grown = homeBoard(large.copy(textScale = 1.3f), 2, tools = 3, games = 4)
        near(2f + 2 * 23.4f + 2f + 8f, grown.topY, "two lines at 1.3")
        near(grown.topY, tiles(grown, HomeTopKind.CONTINUE).single().rect.y, "the top section starts under the strip")
        val fold = HomeFrame(475f, 291f, true, textScale = 1.3f)
        near(23.4f, homeStripLineH(fold), "portrait line box at 1.3")
        near(2f + 2 * 23.4f + 2f + 8f, homeBoard(fold, 2, tools = 3, games = 4).topY, "portrait type at 1.3")
        near(8f, homeBoard(fold, 0, tools = 3, games = 4).topY, "no strip: only the margin")
    }

    // A very short body (a freeform or pop-up window) leaves the top section nothing, never a negative size.
    @Test fun aVeryShortBodyNeverGoesNegative() {
        val frames = listOf(
            HomeFrame(600f, 40f, false), HomeFrame(400f, 208f, false), HomeFrame(800f, 200f, false, 800f / 288f),
            HomeFrame(800f, 20f, false, 800f / 108f), HomeFrame(240f, 40f, true), HomeFrame(60f, 300f, true), HomeFrame(600f, 40f, false, textScale = 1.3f),
        )
        for (f in frames) for (lines in 0..2) for (tools in 0..8) for (games in 0..4) {
            val b = homeBoard(f, lines, tools, games)
            val what = "$f lines $lines tools $tools games $games (${b.mode})"
            assertTrue(b.toolW >= 0f && b.toolH >= 0f, "tool tile $what")
            b.top.forEach { assertTrue(it.rect.w >= 0f && it.rect.h >= 0f, "${it.kind} ${it.rect} $what") }
        }
        val b = homeBoard(HomeFrame(600f, 40f, false), 1, tools = 3, games = 0)
        assertEquals(4, b.top.size); b.top.forEach { assertTrue(it.rect.w >= 0f && it.rect.h >= 0f, "${it.kind} ${it.rect}") }
    }

    // No tools: Continue runs the full width, or to a gap before the inline games on a landscape face (the wide face with
    // 3 games: 2 inline, flush right, 0.4 · (884 − 14) = 348 wide: Continue the other 0.6, 522; 1 row game).
    @Test fun noToolsContinueFills() {
        val col = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 2)
        near(415f, tiles(col, HomeTopKind.CONTINUE).single().rect.w, "full width")
        val none = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 0)
        assertEquals(1, none.top.size); near(884f, tiles(none, HomeTopKind.CONTINUE).single().rect.w, "full width")
        val sps = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 3)
        assertEquals(3, sps.top.size); near(522f, tiles(sps, HomeTopKind.CONTINUE).single().rect.w, "to the games"); assertEquals(1, sps.rowGames)
        near(38f + 884f, tiles(sps, HomeTopKind.GAME).first().rect.let { it.x + it.w }, "the games flush right")
    }
    // No games: the wide face's Continue is the body (308) across the page (884), with tools or without. The tools' row
    // (the Tools page's 81.2 × 108.32 tiles) a gap under it makes the top section 308 + 14 + 108.32 = 430.32 and the page
    // 28 + 430.32 + 8 = 466.32; without tools the top is the body and the page 344.
    @Test fun noGamesContinueTakesTheBody() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 2, games = 0)
        assertEquals(0, b.rowGames); near(81.2f, b.toolW, "toolW"); near(108.32f, b.toolH, "toolH"); near(430.32f, b.topH, "topH"); near(466.32f, b.pageH, "page")
        tiles(b, HomeTopKind.CONTINUE).single().rect.let { near(884f, it.w, "continue w"); near(308f, it.h, "continue h: the body") }
        val none = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 0)
        near(344f - 28f - 8f, none.topH, "body (topY 28)"); near(344f, none.pageH, "page")
    }
    @Test fun pinRects() {
        val b = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 3)
        val p = homeBoardPin(b, 2)
        near(30f, p.x, "x"); near(b.tilesY + b.pinH + 12f, p.y, "second row")
    }
    // Without games the wide face's Continue is the body, 248 − 28 − 8 = 212, across the page (764); the row of 3
    // tiles from the page's left a gap under it, at 28 + 212 + 14 = 254, ends the top section (212 + 14 + 112.7 = 338.7).
    @Test fun wideToolRowSitsUnderContinueWithoutGames() {
        val b = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 3, games = 0)
        assertEquals(HomeToolMode.WIDE, b.mode); near(112.7f, b.toolH, "toolH")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(764f, c.w, "continue w"); near(212f, c.h, "continue h")
        val first = tiles(b, HomeTopKind.TOOL).first().rect
        near(38f, first.x, "the row from the page's left"); near(254f, first.y, "the row a gap under Continue")
        near(338.7f, b.topH, "topH"); near(b.topY + b.topH, first.y + first.h, "the row ends the top section")
    }
    // A narrow landscape split (400 × 304, SPS3): Continue and the 2 inline games across the page, a gap apart, the
    // body tall (304 − 28 − 8 = 268), none empty: the column 0.4 · (324 − 14) = 124, Continue 186. The tools' row a gap
    // under them, at 28 + 268 + 14 = 310, across 324: floor(332 / 88) = 3 columns of (324 − 16) / 3 = 102.67, each
    // 59.6 + 61.6 = 121.2 tall; the third game a row game.
    @Test fun narrowLandscapeKeepsContinueAndTheGamesOverTheTools() {
        val b = homeBoard(HomeFrame(400f, 304f, false, 400f / 392f), 1, tools = 3, games = 3)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames); assertEquals(3, b.slots)
        b.top.forEach { assertTrue(it.rect.w > 0f && it.rect.h > 0f, "${it.kind} ${it.rect}") }
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        val games = tiles(b, HomeTopKind.GAME).map { it.rect }
        val firstTool = b.top.first { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.rect
        near(186f, c.w, "continue w"); near(268f, c.h, "continue h: the body")
        near(c.x + c.w + b.gap, games[0].x, "the games a gap after Continue"); near(38f + 324f, games[0].x + games[0].w, "the games end the page")
        near(c.y + c.h, games[1].y + games[1].h, "the games end with Continue")
        near(310f, firstTool.y, "the tools a gap under Continue"); near(121.2f, firstTool.h, "the tile's height")
    }
    @Test fun landscapeTopNeverNegative() {
        val b = homeBoard(HomeFrame(600f, 40f, false, 600f / 128f), 1, tools = 3, games = 2)
        assertTrue(b.topH >= 0f, "topH ${b.topH}")
    }

    // The strip's text sits centred between the selected tab's underline and the top section, for one line or two,
    // either type, any text scale; topY does not move. Measured on the ink: above (the underline, or the screen's top
    // with the tab row hidden, to the first line's cap-top), between (the first line's descent to the second's cap-top)
    // and below (the last line's descent to the selected Continue's ring, NX_TILE_RING above topY). above == below;
    // between under both, half of them with the tab row hidden.
    private data class StripGaps(val above: Float, val between: Float, val below: Float)
    private fun stripGaps(f: HomeFrame, lines: Int): StripGaps {
        val b = homeBoard(f, lines, tools = 3, games = 4)
        val lh = homeStripLineH(f)
        val sp = HOME_STRIP_TYPE.sp * f.textScale
        val pitch = homeStripPitch(f)
        val baseline = lh / 2 + (NX_FONT_ASCENT - NX_FONT_DESCENT) / 2 * sp
        val capTop = baseline - HOME_FONT_CAP * sp; val inkBottom = baseline + HOME_FONT_DESC * sp
        val underline = if (f.titleHidden) 0f else MENU_TAB_ROW_HEIGHT.value - menuTabUnderlineBottom()
        val above = underline + b.stripY + capTop
        val between = pitch + capTop - inkBottom
        val below = b.topY - NX_TILE_RING - (b.stripY + (lines - 1) * pitch + inkBottom)
        return StripGaps(above, between, below)
    }
    @Test fun theStripIsCentredBetweenTheTabsAndTheTop() {
        near(34.78f, menuTabUnderlineBottom(), "underline, either orientation")
        val fold = HomeFrame(475f, 291f, true)
        val land = HomeFrame(960f, 344f, false, 960f / 432f)
        val large = HomeFrame(933f, 616f, false, 933f / 704f)
        val flipCover = HomeFrame(474f, 300f, false, 474f / 380f)
        // (text scale, system font scale) pairs, the text scale drawn at, as the frame carries both.
        val scales = listOf(1f to 1f, 0.85f to 1f, 0.9f to 0.9f, 0.765f to 0.9f, 1.3f to 1.3f, 1.3f to 1f, 2f to 2f)
        // The Fold cover, two lines (topY 48): at 1.0 11.30 / 3.34 / 11.30; at a system font of 0.9 12.43 / 3.91 / 12.43 (the tab words pinned),
        // and with the tab row hidden 7.82 / 3.91 / 7.82; the portrait offset −1.48.
        stripGaps(fold, 2).let { near(11.30f, it.above, "fold above"); near(3.34f, it.between, "fold between"); near(11.30f, it.below, "fold below") }
        stripGaps(fold.copy(textScale = 0.9f), 2).let { near(12.43f, it.above, "0.9 above"); near(3.91f, it.between, "0.9 between"); near(12.43f, it.below, "0.9 below") }
        val hidden = fold.copy(h = 335f, titleHidden = true, textScale = 0.9f)
        stripGaps(hidden, 2).let { near(7.82f, it.above, "hidden above"); near(3.91f, it.between, "hidden between"); near(7.82f, it.below, "hidden below") }
        near(-1.48f, homeBoard(fold, 2, 3, 4).stripY, "portrait offset"); near(-2.73f, homeBoard(land, 1, 3, 4).stripY, "landscape offset (the one title size)")
        for (f in listOf(fold, land, large, flipCover)) for (title in listOf(true, false)) for ((scale, font) in scales) for (lines in 1..2) {
            val frame = (if (title) f else f.copy(h = f.h + 44f, titleHidden = true)).copy(textScale = scale)
            val g = stripGaps(frame, lines)
            val what = "$frame lines $lines"
            assertEquals(g.above, g.below, 0.05f, "$what: above ${g.above} == below ${g.below}")
            assertTrue(g.between > 0f, "$what: between ${g.between}")
            if (lines == 2) {
                // Shown, the underline adds room above the body's top, except at a font scale so large that the tab words
                // outgrow the row and the underline drops into the body (2.0 in landscape): still well under the room round.
                assertTrue(g.between * 1.5f <= g.above + 0.05f, "$what: between ${g.between} well under round ${g.above}")
                if (!title) near(g.above, 2 * g.between, "$what: hidden, round twice between")
            }
        }
        near(48f, homeBoard(fold, 2, 3, 4).topY, "topY stays"); near(28f, homeBoard(land, 1, 3, 4).topY, "topY stays")
    }

    // SPS3 (a sub-840 landscape split) puts its tiles in one row, 8 dp apart, a gap under the first screen (Continue,
    // the body, 304): at 28 + 304 + 14 = 346, under the 340 dp body; 10 tools in its 8 slots, 7 and "+3". Continue and
    // the pins' shape are the same with tools or without; the top section grows by the gap and the row
    // (304 + 14 + 109.7 = 427.7), so the pin rows start under it at 28 + 427.7 + 14 = 469.7, their full shape 109.
    // 8 games: 2 inline, 6 row games in 2 rows.
    @Test fun sps3ToolRowSitsUnderTheFirstScreen() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val b = homeBoard(f, 1, tools = 10, games = 8)
        val noTools = homeBoard(f, 1, tools = 0, games = 8)
        assertEquals(HomeToolMode.SPS3, b.mode)
        val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
        assertEquals(1, sq.map { it.y }.distinct().size, "rows")
        val xs = sq.map { it.x }
        assertEquals(8, xs.size, "columns"); near(b.gutter, xs.first(), "from the page's left")
        xs.zipWithNext().forEach { (a, n) -> near(b.toolW + 8f, n - a, "column step") }
        near(346f, sq.first().y, "the row a gap under Continue"); assertTrue(sq.first().y >= f.h, "the row a scroll away")
        near(304f, tiles(b, HomeTopKind.CONTINUE).single().rect.h, "Continue the body")
        near(304f, tiles(noTools, HomeTopKind.CONTINUE).single().rect.h, "no tools: Continue the body"); near(304f, noTools.topH, "no tools: topH")
        near(noTools.topH + b.gap + b.toolH, b.topH, "topH: the gap and the row more"); near(427.7f, b.topH, "topH")
        near(noTools.pinH, b.pinH, "pinH as without tools")
        near(346f, noTools.tilesY, "no tools: the rows under the body"); near(469.7f, b.tilesY, "the rows under the tools"); near(109f, b.pinH, "pinH: the full shape")
        val p1 = homeBoardPin(b, b.perRow)
        near(b.tilesY + b.pinH + b.gap, p1.y, "second row")
        near(p1.y + b.pinH + 3f + 8f, b.pageH, "pageH")
    }

    // The wide face and UNDER keep their row heights, the Flip's wide face one row of tiles 8 dp apart under Continue;
    // with no tools the top section is Continue alone.
    @Test fun wideUnderAndNoToolsKeepTheirGeometry() {
        val wide = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 6, games = 4)
        assertEquals(HomeToolMode.WIDE, wide.mode); near(86.75f, wide.pinH, "WIDE pinH"); near(248f - 28f - 8f, tiles(wide, HomeTopKind.CONTINUE).single().rect.h, "WIDE Continue: the body")
        val sq = tiles(wide, HomeTopKind.TOOL).map { it.rect }
        near(sq[0].x + wide.toolW + 8f, sq[1].x, "WIDE column step"); assertEquals(1, sq.map { it.y }.distinct().size, "WIDE one row")
        // UNDER on a tall window without the pad (flipTallIsUnder); the Fold cover without tools fills its body.
        val under = homeBoard(HomeFrame(360f, 423f, false), 2, tools = 5, games = 4)
        assertEquals(HomeToolMode.UNDER, under.mode); near(316.53f, under.topH, "UNDER topH"); near(101.25f, under.pinH, "UNDER pinH")
        near(189.33f, tiles(under, HomeTopKind.CONTINUE).single().rect.h, "UNDER Continue")
        val fold = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 4)
        near(235f, fold.topH, "no tools topH"); near(106.59f, fold.pinH, "no tools pinH"); near(295f, fold.tilesY, "no tools: the rows below the body")
        val sps = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 6)
        near(308f, sps.topH, "no tools wide topH: the body"); near(124.4f, sps.pinH, "no tools wide pinH")
    }

    // The Flip's cover screen (a 399 × 298 landscape body, not wide, not large) takes the strip on two lines:
    // topY 2 + 2 · 18 + 2 + 8 = 48, the top section under both lines and the margin.
    @Test fun narrowLandscapeTakesTwoStripLines() {
        val f = HomeFrame(399f, 298f, false, 399f / 386f)
        val b = homeBoard(f, 2, tools = 3, games = 4)
        assertEquals(2, b.stripLines); near(48f, b.topY, "topY")
        val stripEnd = b.stripY + 2 * homeStripLineH(f) + 2f
        assertTrue(b.top.all { it.rect.y >= stripEnd + HOME_PAGE_MARGIN - 0.05f }, "the top section under both strip lines")
        near(homeBoard(f, 1, tools = 3, games = 4).topY + 18f + 2f, b.topY, "a line and its gap lower than one line")
    }
}
