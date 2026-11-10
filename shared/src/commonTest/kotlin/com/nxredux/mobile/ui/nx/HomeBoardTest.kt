package com.nxredux.mobile.ui.nx

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }

    // The Tools page's tile at its narrowest (80 dp): 2 · 10 + 0.6 · 80 + 6 + 2 · 12 · 1.15 + 6 = 107.6 tall at a text
    // scale of 1, rows 8 dp apart (115.6 a row).
    private val tw = HOME_TOOL_TILE_W
    private val th = toolTileHeight(80f, 1f)

    // The mockup's landscape (WIDE, up to 3 columns) with 4 tools: the Tools page's tiles flush right, as many rows as fit
    // the top section (topY 2 + 18 + 8 = 28; the whole body 344 − 28 − 8 = 308: floor(316 / 115.6) = 2), so 6 slots, the
    // 4 in 2 columns (ceil(4 / 2)), 2 · 80 + 8 = 168 wide. The first 2 of 6 games inline before them, the other 4 row
    // games. Left of the tools (a gap before them) 884 − 168 − 14 = 702: the inline column 0.4 · (702 − 14) = 275.2,
    // Continue the other 0.6, 412.8. The row games a scroll away, from 28 + 308 + 14 = 350, at their full shape 124.4.
    @Test fun landscapeIsWideWithToolTilesBeside() {
        near(107.6f, th, "the tile's height"); near(80f, tw, "the tile's width")
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), stripLines = 1, tools = 4, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(6, b.slots); assertEquals(0, b.more)
        near(28f, b.topY, "topY"); near(308f, b.topH, "topH"); near(80f, b.toolW, "toolW"); near(107.6f, b.toolH, "toolH")
        near(412.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        assertEquals(4, tiles(b, HomeTopKind.TOOL).size); assertEquals(0, tiles(b, HomeTopKind.MORE).size)
        assertEquals(2, b.inlineGames); assertEquals(2, tiles(b, HomeTopKind.GAME).size)
        tiles(b, HomeTopKind.GAME).forEach { near(38f + 412.8f + 14f, it.rect.x, "game x"); near(275.2f, it.rect.w, "game w") }
        assertEquals(3, b.perRow); near(285.33f, b.pinW, "pinW"); near(124.4f, b.pinH, "pinH"); assertEquals(4, b.rowGames)
        near(350f, b.tilesY, "the rows under the body"); assertTrue(b.tilesY >= 344f, "a scroll away")
    }
    // 6 tools: all 3 columns of 2 rows, flush right, top-aligned from 28, in reading order; tiles 8 dp apart both ways.
    // Left of them 884 − (3 · 80 + 16) − 14 = 614: Continue 0.6 · (614 − 14) = 360.
    @Test fun wideSixToolsFillThreeColumnsOfTwoRows() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(6, b.slots); assertEquals(0, b.more)
        near(360f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        val sq = tiles(b, HomeTopKind.TOOL).map { it.rect }
        assertEquals(6, sq.size); assertEquals(0, tiles(b, HomeTopKind.MORE).size)
        val left = 38f + 884f - 3 * tw - 2 * 8f
        for ((i, r) in sq.withIndex()) {
            near(left + (i % 3) * (tw + 8f), r.x, "tile $i x"); near(28f + (i / 3) * (th + 8f), r.y, "tile $i y")
            near(tw, r.w, "tile $i w"); near(th, r.h, "tile $i h")
        }
        assertEquals(4, b.rowGames)
    }
    // Past 6 tools (3 columns of 2): 5 and "+N", the last tile, at the right of the second row.
    @Test fun wideMoreThanSixToolsShowFiveAndMore() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 17, games = 6)
        assertEquals(6, b.slots); assertEquals(17 - 6 + 1, b.more)
        assertEquals(5, tiles(b, HomeTopKind.TOOL).size)
        val more = b.top.last(); assertEquals(HomeTopKind.MORE, more.kind)
        near(38f + 884f - tw, more.rect.x, "more x: the right column"); near(28f + th + 8f, more.rect.y, "more y: the second row")
        near(tw, more.rect.w, "more w: a tool tile"); near(th, more.rect.h, "more h: a tool tile")
    }
    // topY 28: the top the whole body 248 − 28 − 8 = 212, one row of tiles (floor(220 / 115.6) = 1), so 3 slots; 2 of the
    // 4 games inline, the other 2 row games a scroll away. Left of the tools 764 − 256 − 14 = 494: Continue
    // 0.6 · (494 − 14) = 288.
    @Test fun flipLandscapeIsWide() {
        val b = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(3, b.slots); near(80f, b.toolW, "toolW"); near(107.6f, b.toolH, "toolH")
        near(288f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        near(245.33f, b.pinW, "pinW"); near(86.75f, b.pinH, "pinH"); assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames)
    }
    // SPS3: the tool tiles flush right, up to 2 columns of as many rows as fit; the first 2 games inline before them;
    // Continue takes the rest. Landscape, so the strip is one line (2 + 18 + 8 = 28); the two-line reference (48) is in
    // stripLinesFollowTheTextScale. The unfolded Fold is LARGE, so SPS3 is checked on a sub-840 landscape split.
    // w 724, columns 109, row shape min(109, (340 − 33) · 0.4 = 122.8) = 109; the top the whole body 340 − 28 − 8 = 304:
    // 2 rows (floor(312 / 115.6)), 4 slots; 3 tools in 2 columns, 168 wide. Left of them 724 − 168 − 14 = 542: the
    // inline column 0.4 · (542 − 14) = 211.2, Continue 316.8.
    @Test fun landscapeSplitIsSps3() {
        val b = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(4, b.slots); near(28f, b.topY, "topY"); near(304f, b.topH, "topH")
        near(80f, b.toolW, "toolW"); near(107.6f, b.toolH, "toolH")
        near(316.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        val left = 38f + 724f - 2 * tw - 8f
        tiles(b, HomeTopKind.TOOL).map { it.rect }.forEachIndexed { i, r ->
            near(left + (i % 2) * (tw + 8f), r.x, "tool $i x"); near(28f + (i / 2) * (th + 8f), r.y, "tool $i y")
        }
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames)
    }
    // SPS3's inline games (800 × 340, 3 tools). The top section is the body, 340 − 28 − 8 = 304, whatever the games:
    // left of the tools 542: the column 0.4 · (542 − 14) = 211.2 at 38 + 316.8 + 14 = 368.8, Continue 316.8; 1 game the
    // full 304, 2 stacked (304 − 14) / 2 = 145 tall. 3 games: the same 2 inline and 1 row game a scroll away, at
    // 28 + 304 + 14 = 346, its full shape 109.
    @Test fun sps3PutsTheFirstTwoGamesInline() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val one = homeBoard(f, 1, tools = 3, games = 1)
        assertEquals(HomeToolMode.SPS3, one.mode); assertEquals(1, one.inlineGames); assertEquals(0, one.rowGames)
        near(304f, one.topH, "1: topH the body"); near(107.6f, one.toolH, "1: toolH"); near(340f, one.pageH, "1: pageH")
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME) + List(3) { HomeTopKind.TOOL }, one.top.map { it.kind })
        near(316.8f, tiles(one, HomeTopKind.CONTINUE).single().rect.w, "1: continue w")
        tiles(one, HomeTopKind.GAME).single().rect.let { near(368.8f, it.x, "1: game x"); near(28f, it.y, "1: game y"); near(211.2f, it.w, "1: game w"); near(304f, it.h, "1: game h") }
        val two = homeBoard(f, 1, tools = 3, games = 2)
        assertEquals(2, two.inlineGames); assertEquals(0, two.rowGames); near(304f, two.topH, "2: topH")
        val g2 = tiles(two, HomeTopKind.GAME).map { it.rect }
        near(28f, g2[0].y, "2: game 0 y"); near(28f + 145f + 14f, g2[1].y, "2: game 1 y")
        g2.forEach { near(211.2f, it.w, "2: game w"); near(145f, it.h, "2: game h") }
        val three = homeBoard(f, 1, tools = 3, games = 3)
        assertEquals(2, three.inlineGames); assertEquals(1, three.rowGames); near(304f, three.topH, "3: topH")
        tiles(three, HomeTopKind.GAME).forEach { near(211.2f, it.rect.w, "3: game w"); near(145f, it.rect.h, "3: game h") }
        near(346f, three.tilesY, "3: tilesY"); near(109f, three.pinH, "3: pinH")
    }
    // SPS3 (a sub-840 landscape split) past its 4 slots (2 columns of 2): 3 tools and "+N" last, at the right of the
    // second row; 12 tools "+9".
    @Test fun sps3PastFourToolsShowsThreeAndMore() {
        for ((tools, more) in listOf(5 to 2, 6 to 3, 12 to 9)) {
            val b = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, tools = tools, games = 4)
            assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(4, b.slots, "$tools: slots"); assertEquals(more, b.more, "$tools: more")
            assertEquals(3, tiles(b, HomeTopKind.TOOL).size, "$tools: tools shown")
            near(316.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "$tools: continue w")
            val last = b.top.last(); assertEquals(HomeTopKind.MORE, last.kind)
            near(38f + 724f - tw, last.rect.x, "$tools: more x: the right column"); near(28f + th + 8f, last.rect.y, "$tools: more y: the second row")
        }
    }
    // Each face's slots are its columns times the rows that fit, whatever the tool count: the Flip's wide face 3 (one row
    // of 3), the portrait type 8 (2 rows of the 4 columns across the Fold cover), UNDER 3 (one row across 284 dp).
    @Test fun facesSlotsAreTheTilesThatFit() {
        assertEquals(3, homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 8, games = 4).slots)   // WIDE
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
    // 2 · 12 · 1.15 · 1.3 = 35.88, so 126.53 tall; beside Continue (the landscape split) 80 wide and 115.88 tall.
    @Test fun toolTilesFollowTheFontScale() {
        near(126.53f, homeBoard(HomeFrame(475f, 291f, true, textScale = 1.3f), 2, tools = 3, games = 0).toolH, "portrait at 1.3")
        val land = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f, textScale = 1.3f), 1, tools = 3, games = 0)
        near(80f, land.toolW, "landscape at 1.3: w"); near(115.88f, land.toolH, "landscape at 1.3: h")
        near(toolTileHeight(80f, 1.3f), land.toolH, "the Tools page's height")
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
    // A landscape halves body (not the portrait type): the cap (628 − 14) / 2 / 1.17 = 262.39 holds 2 rows of tiles
    // (floor(270.39 / 115.6)); the first 2 games inline: left of the 2 columns 628 − 168 − 14 = 446, Continue
    // 0.6 · (446 − 14) = 259.2.
    @Test fun largePortraitIsColumn() {
        val b = homeBoard(HomeFrame(704f, 845f, false), 1, tools = 3, games = 4)   // not the portrait type: one strip line
        assertEquals(HomeToolMode.COLUMN, b.mode); assertEquals(4, b.slots); near(262.39f, b.topH, "topH"); near(107.6f, b.toolH, "toolH")
        near(259.2f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        tiles(b, HomeTopKind.TOOL).forEach { assertTrue(it.rect.x >= 30f + 259.2f, "beside Continue") }
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
    // No games: the wide face's top is the body (308), with tools or without, the tools the Tools page's 80 × 107.6 tile,
    // the page 344.
    @Test fun noGamesTopTakesTheBody() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 2, games = 0)
        assertEquals(0, b.rowGames); near(80f, b.toolW, "toolW"); near(107.6f, b.toolH, "toolH"); near(308f, b.topH, "the body"); near(344f, b.pageH, "page")
        val none = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 0)
        near(344f - 28f - 8f, none.topH, "body (topY 28)"); near(344f, none.pageH, "page")
    }
    @Test fun pinRects() {
        val b = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 3)
        val p = homeBoardPin(b, 2)
        near(30f, p.x, "x"); near(b.tilesY + b.pinH + 12f, p.y, "second row")
    }
    // Without games the wide face's top is the body, 248 − 28 − 8 = 212: one row of 3 tiles top-aligned at topY,
    // Continue 764 − (3 · 80 + 16) − 14 = 494.
    @Test fun wideToolsTopAlignedWithoutGames() {
        val b = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 3, games = 0)
        assertEquals(HomeToolMode.WIDE, b.mode); near(107.6f, b.toolH, "toolH")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(494f, c.w, "continue w"); near(b.topH, c.h, "continue h")
        near(b.topY, tiles(b, HomeTopKind.TOOL).first().rect.y, "the block top-aligned")
    }
    // A narrow landscape split (400 × 304, SPS3): Continue, the 2 inline games, then the tool tiles, each a gap apart,
    // none empty (the top the body 304 − 28 − 8 = 268: 2 rows, so 3 tools take 2 columns, 168 wide; left of them
    // 324 − 168 − 14 = 142: Continue 0.6 · (142 − 14) = 76.8); the third game a row game.
    @Test fun narrowLandscapeKeepsContinueBesideTheTools() {
        val b = homeBoard(HomeFrame(400f, 304f, false, 400f / 392f), 1, tools = 3, games = 3)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames)
        b.top.forEach { assertTrue(it.rect.w > 0f, "${it.kind} w ${it.rect.w}") }
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        val game = tiles(b, HomeTopKind.GAME).first().rect
        val firstSquare = b.top.first { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.rect
        near(76.8f, c.w, "continue w")
        assertTrue(c.x + c.w <= game.x - b.gap + 0.05f, "continue ends a gap before the games")
        assertTrue(game.x + game.w <= firstSquare.x - b.gap + 0.05f, "the games end a gap before the tools")
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

    // SPS3 (a sub-840 landscape split) stacks its tiles 8 dp apart both ways, as many rows as fit the top section (the
    // body, 304: 2 rows, 2 · 107.6 + 8 = 223.2 of it), top-aligned. The top section and the pins are the same with tools
    // or without: the pin row keeps its full shape (109), a scroll away under the body at 28 + 304 + 14 = 346. 8 games:
    // 2 inline, 6 row games in 2 rows.
    @Test fun sps3ToolRowsFitTheTop() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val b = homeBoard(f, 1, tools = 10, games = 8)
        val noTools = homeBoard(f, 1, tools = 0, games = 8)
        assertEquals(HomeToolMode.SPS3, b.mode)
        val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
        val ys = sq.map { it.y }.distinct().sorted()
        assertEquals(2, ys.size, "rows")
        ys.zipWithNext().forEach { (a, n) -> near(b.toolH + 8f, n - a, "row step") }
        val xs = sq.map { it.x }.distinct().sorted()
        assertEquals(2, xs.size, "columns")
        xs.zipWithNext().forEach { (a, n) -> near(b.toolW + 8f, n - a, "column step") }
        near(b.topY, ys.first(), "top-aligned"); assertTrue(ys.last() + b.toolH <= b.topY + b.topH + 0.05f, "inside the top section")
        near(b.topH, tiles(b, HomeTopKind.CONTINUE).single().rect.h, "Continue as tall")
        near(noTools.topH, b.topH, "topH as without tools"); near(noTools.pinH, b.pinH, "pinH as without tools")
        near(346f, b.tilesY, "the rows under the body"); near(109f, b.pinH, "pinH: the full shape")
        val p1 = homeBoardPin(b, b.perRow)
        near(b.tilesY + b.pinH + b.gap, p1.y, "second row")
        near(p1.y + b.pinH + 3f + 8f, b.pageH, "pageH")
    }

    // The wide face and UNDER keep their row heights, the Flip's wide face one row of 3 tiles 8 dp apart; with no tools
    // nothing changes.
    @Test fun wideUnderAndNoToolsKeepTheirGeometry() {
        val wide = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 6, games = 4)
        assertEquals(HomeToolMode.WIDE, wide.mode); near(86.75f, wide.pinH, "WIDE pinH"); near(248f - 28f - 8f, wide.topH, "WIDE topH: the body")
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
