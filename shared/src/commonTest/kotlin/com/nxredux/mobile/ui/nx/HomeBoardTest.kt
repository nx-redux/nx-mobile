package com.nxredux.mobile.ui.nx

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }

    // The mockup's landscape (WIDE, 15 slots) with up to 5 tools: one column of 5, flush right. The first 2 of 6 games
    // inline before it, the other 4 row games. topY 2 + 18 + 8 = 28; the top the whole body 344 − 28 − 8 = 308, square
    // min(58, (308 − 4 · 7) / 5) = 56. Left of the squares (a gap before them) 912 − 56 − 14 = 842: the inline column
    // 0.4 · (842 − 14) = 331.2, Continue the other 0.6, 496.8. The row games a scroll away, from 28 + 308 + 14 = 350, at
    // their full shape 124.4.
    @Test fun landscapeIsWideWithAColumnOfFive() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), stripLines = 1, tools = 4, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(15, b.slots); assertEquals(0, b.more)
        near(28f, b.topY, "topY"); near(308f, b.topH, "topH"); near(56f, b.square, "square")
        near(496.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        assertEquals(4, tiles(b, HomeTopKind.TOOL).size); assertEquals(0, tiles(b, HomeTopKind.MORE).size)
        assertEquals(2, b.inlineGames); assertEquals(2, tiles(b, HomeTopKind.GAME).size)
        tiles(b, HomeTopKind.GAME).forEach { near(24f + 496.8f + 14f, it.rect.x, "game x"); near(331.2f, it.rect.w, "game w") }
        assertEquals(3, b.perRow); near(294.67f, b.pinW, "pinW"); near(124.4f, b.pinH, "pinH"); assertEquals(4, b.rowGames)
        near(350f, b.tilesY, "the rows under the body"); assertTrue(b.tilesY >= 344f, "a scroll away")
    }
    // More than 5 tools: two columns of the same 56 dp squares, flush right, top-aligned from 28, filled in reading
    // order; the rows 7 dp apart, the columns 14. Left of them 912 − (2 · 56 + 14) − 14 = 772: Continue
    // 0.6 · (772 − 14) = 454.8.
    @Test fun landscapeSixToolsTakeTwoColumns() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(15, b.slots); assertEquals(0, b.more); near(56f, b.square, "square")
        near(454.8f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        val sq = tiles(b, HomeTopKind.TOOL).map { it.rect }
        assertEquals(6, sq.size); assertEquals(0, tiles(b, HomeTopKind.MORE).size)
        val left = 24f + 912f - 2 * 56f - 14f
        for ((i, r) in sq.withIndex()) {
            near(left + (i % 2) * (56f + 14f), r.x, "square $i x"); near(28f + (i / 2) * (56f + 7f), r.y, "square $i y")
        }
        assertEquals(4, b.rowGames)
    }
    // Past 15 tools (3 columns of 5): 14 and "+N", top-aligned from 28, the fifth row 4 · (56 + 7) under the first.
    @Test fun landscapeMoreThanFifteenToolsShowFourteenAndMore() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 17, games = 6)
        assertEquals(15, b.slots); assertEquals(17 - 15 + 1, b.more)
        assertEquals(14, tiles(b, HomeTopKind.TOOL).size)
        val more = b.top.last(); assertEquals(HomeTopKind.MORE, more.kind)
        near(24f + 912f - 56f, more.rect.x, "more x: the right column"); near(28f + 4 * (56f + 7f), more.rect.y, "more y: the fifth row")
    }
    // topY 28: the top the whole body 248 − 28 − 8 = 212; WIDE, square min(58, (212 − 4 · 7) / 5) = 36.8 in one column;
    // 2 of the 4 games inline, the other 2 row games a scroll away. Left of the squares 792 − 36.8 − 14 = 741.2: Continue
    // 0.6 · (741.2 − 14) = 436.32.
    @Test fun flipLandscapeIsWide() {
        val b = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.WIDE, b.mode); near(36.8f, b.square, "square")
        near(436.32f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        near(254.67f, b.pinW, "pinW"); near(90.25f, b.pinH, "pinH"); assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames)
    }
    // SPS3 with up to 5 tools: one column of squares, flush right; the first 2 games inline before it; Continue takes the
    // rest. Landscape, so the strip is one line (2 + 18 + 8 = 28); the two-line reference (48) is in
    // stripLinesFollowTheTextScale. The unfolded Fold is LARGE, so SPS3 is checked on a sub-840 landscape split.
    // w 752, columns 113.67, row shape min(113.67, (340 − 33) · 0.4 = 122.8) = 113.67; the top the whole body
    // 340 − 28 − 8 = 304, square (304 − 4 · 7) / 5 = 55.2, the rows half a gap apart filling it. Left of the squares
    // 752 − 55.2 − 14 = 682.8: the inline column 0.4 · (682.8 − 14) = 267.52, Continue 401.28.
    @Test fun landscapeSplitIsSps3() {
        val b = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(5, b.slots); near(28f, b.topY, "topY"); near(304f, b.topH, "topH"); near(55.2f, b.square, "square")
        near(401.28f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        tiles(b, HomeTopKind.TOOL).forEach { near(24f + 752f - 55.2f, it.rect.x, "one column") }
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames)
    }
    // SPS3's inline games (800 × 340, 3 tools). The top section is the body, 340 − 28 − 8 = 304, whatever the games:
    // squares (304 − 4 · 7) / 5 = 55.2, left of them 752 − 55.2 − 14 = 682.8: the column 0.4 · (682.8 − 14) = 267.52 at
    // 24 + 401.28 + 14 = 439.28, Continue 401.28; 1 game the full 304, 2 stacked (304 − 14) / 2 = 145 tall. 3 games: the
    // same 2 inline and 1 row game a scroll away, at 28 + 304 + 14 = 346, its full shape 113.67.
    @Test fun sps3PutsTheFirstTwoGamesInline() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val one = homeBoard(f, 1, tools = 3, games = 1)
        assertEquals(HomeToolMode.SPS3, one.mode); assertEquals(1, one.inlineGames); assertEquals(0, one.rowGames)
        near(304f, one.topH, "1: topH the body"); near(55.2f, one.square, "1: square"); near(340f, one.pageH, "1: pageH")
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME) + List(3) { HomeTopKind.TOOL }, one.top.map { it.kind })
        near(401.28f, tiles(one, HomeTopKind.CONTINUE).single().rect.w, "1: continue w")
        tiles(one, HomeTopKind.GAME).single().rect.let { near(439.28f, it.x, "1: game x"); near(28f, it.y, "1: game y"); near(267.52f, it.w, "1: game w"); near(304f, it.h, "1: game h") }
        val two = homeBoard(f, 1, tools = 3, games = 2)
        assertEquals(2, two.inlineGames); assertEquals(0, two.rowGames); near(304f, two.topH, "2: topH")
        val g2 = tiles(two, HomeTopKind.GAME).map { it.rect }
        near(28f, g2[0].y, "2: game 0 y"); near(28f + 145f + 14f, g2[1].y, "2: game 1 y")
        g2.forEach { near(267.52f, it.w, "2: game w"); near(145f, it.h, "2: game h") }
        val three = homeBoard(f, 1, tools = 3, games = 3)
        assertEquals(2, three.inlineGames); assertEquals(1, three.rowGames); near(304f, three.topH, "3: topH")
        tiles(three, HomeTopKind.GAME).forEach { near(267.52f, it.rect.w, "3: game w"); near(145f, it.rect.h, "3: game h") }
        near(346f, three.tilesY, "3: tilesY"); near(113.67f, three.pinH, "3: pinH")
    }
    // SPS3 with more than 5 tools: two columns of the same squares, the second right of the first, in reading order; the
    // rows 7 dp apart, the columns 14. Checked on a sub-840 landscape split.
    @Test fun sps3SixToolsTakeTwoColumns() {
        val b = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, tools = 6, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(10, b.slots); assertEquals(0, b.more); near(55.2f, b.square, "square")
        // Left of the squares 752 − (2 · 55.2 + 14) − 14 = 613.6: Continue 0.6 · (613.6 − 14) = 359.76.
        near(359.76f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        val sq = tiles(b, HomeTopKind.TOOL).map { it.rect }
        assertEquals(6, sq.size)
        val left = 24f + 752f - 2 * 55.2f - 14f
        for ((i, r) in sq.withIndex()) {
            near(left + (i % 2) * (55.2f + 14f), r.x, "square $i x"); near(28f + (i / 2) * (55.2f + 7f), r.y, "square $i y")
        }
    }
    // SPS3 (a sub-840 landscape split) past 10 tools: 9 and "+N", the fifth row four half gaps (7 dp) down.
    @Test fun sps3MoreThanTenToolsShowNineAndMore() {
        val b = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, tools = 12, games = 4)
        assertEquals(10, b.slots); assertEquals(3, b.more); assertEquals(9, tiles(b, HomeTopKind.TOOL).size)
        val more = b.top.last(); assertEquals(HomeTopKind.MORE, more.kind)
        near(24f + 752f - 55.2f, more.rect.x, "more x: the right column"); near(28f + 4 * (55.2f + 7f), more.rect.y, "more y: the fifth row")
    }
    // The other faces keep their slots whatever the tool count: the wide face 15 (3 columns of 5).
    @Test fun otherFacesKeepTheirSlots() {
        assertEquals(15, homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 8, games = 4).slots)   // WIDE
        // The portrait type (COLUMN) 10 past 5 tools; UNDER is a tall window without the pad.
        assertEquals(10, homeBoard(HomeFrame(475f, 291f, true), 2, tools = 8, games = 4).slots)                 // COLUMN
        assertEquals(5, homeBoard(HomeFrame(360f, 423f, false), 2, tools = 8, games = 4).slots)                 // UNDER
    }
    // Two 18 dp strip lines: topY 2 + 2 · 18 + 2 + 8 = 48. The portrait type's top section is the whole body
    // (291 − 48 − 8 = 235) and never squeezed: 5 squares a column of side (235 − 24) / 5 = 42.2, Continue
    // 443 − 42.2 − 12 = 388.8. The pin rows start below the first screen (tilesY 48 + 235 + 12 = 295), 215.5 × 114.47:
    // 1.5 times ([HOME_PORTRAIT_PIN_TALL]) their 4:3 shape (101.75 · 3 / 4 = 76.31).
    @Test fun foldCoverIsColumn() {
        val b = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 3, games = 4)
        assertEquals(HomeToolMode.COLUMN, b.mode); assertEquals(5, b.slots); near(48f, b.topY, "topY"); near(235f, b.topH, "topH"); near(42.2f, b.square, "square")
        near(114.47f, b.pinH, "pinH"); near(1.5f * (101.75f * 3 / 4), b.pinH, "pinH: 1.5 times the 4:3 half shape"); near(295f, b.tilesY, "tilesY"); assertTrue(b.tilesY >= 291f, "the rows start below the first screen")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(388.8f, c.w, "continue w"); near(235f, c.h, "continue h"); assertEquals(2, b.perRow); near(215.5f, b.pinW, "pinW")
    }
    // The portrait type is always COLUMN; UNDER is a tall window without the pad: gutter 24, gap 14, topY
    // 2 + 2 · 18 + 2 + 8 = 48, square (312 − 56) / 5 = 51.2, topH the cap 312 / 1.5 + 14 + 51.2 = 273.2 (under 350 − 71),
    // Continue 273.2 − 14 − 51.2 = 208 tall; 3 columns of 94.67, pinW 149, pinH 71.
    @Test fun flipTallIsUnder() {
        val b = homeBoard(HomeFrame(360f, 423f, false), 2, tools = 5, games = 4)
        assertEquals(HomeToolMode.UNDER, b.mode); near(273.2f, b.topH, "topH"); near(51.2f, b.square, "square")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(312f, c.w, "continue w"); near(208f, c.h, "continue h"); assertEquals(5, tiles(b, HomeTopKind.TOOL).size)
        near(149f, b.pinW, "pinW"); near(71f, b.pinH, "pinH")
    }

    // The portrait type's block (the body 235 from 48): columns of 5 squares rows half a gap (6) apart, flush right; the
    // first column the rightmost, each filled top to bottom; 6–10 tools a second column a gap left of the first; past 10,
    // 9 and "+N" last. 5 rows and 4 half gaps are the whole top section, so it saves nothing (the rows keep their shape).
    @Test fun portraitToolsFillColumnsOfFive() {
        val f = HomeFrame(475f, 291f, true)
        val s = (235f - 4 * 6f) / 5
        val rightX = 16f + 443f - s
        for (tools in 1..12) {
            val b = homeBoard(f, 2, tools = tools, games = 4)
            assertEquals(HomeToolMode.COLUMN, b.mode); near(235f, b.topH, "$tools: topH the body"); near(s, b.square, "$tools: square")
            near(114.47f, b.pinH, "$tools: pinH (1.5 × 76.31), nothing saved"); near(295f, b.tilesY, "$tools: tilesY")
            assertEquals(if (tools <= 5) 5 else 10, b.slots, "$tools: slots")
            assertEquals(if (tools > 10) tools - 10 + 1 else 0, b.more, "$tools: more")
            val sq = b.top.filter { it.kind != HomeTopKind.CONTINUE }
            assertEquals(min(tools, b.slots), sq.size, "$tools: squares")
            for ((i, t) in sq.withIndex()) {
                near(rightX - (i / 5) * (s + 12f), t.rect.x, "$tools: square $i x")
                near(48f + (i % 5) * (s + 6f), t.rect.y, "$tools: square $i y")
                assertEquals(if (tools > 10 && i == 9) HomeTopKind.MORE else HomeTopKind.TOOL, t.kind, "$tools: square $i kind")
            }
            val cols = if (tools <= 5) 1 else 2
            near(443f - cols * s - (cols - 1) * 12f - 12f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "$tools: continue w")
            near(235f, tiles(b, HomeTopKind.CONTINUE).single().rect.h, "$tools: continue h")
        }
        val none = homeBoard(f, 2, tools = 0, games = 4)
        near(235f, none.topH, "no tools: the body"); near(443f, tiles(none, HomeTopKind.CONTINUE).single().rect.w, "no tools: full width")
    }
    // A taller portrait-type body (the Flip's inner display) stops its top section at the Fold cover's 235 (from 48); the
    // rows follow from 295 at 1.5 times their 4:3 shape (3 columns of 101.33: 76, so 114), the first (295 … 409) fully on
    // the 640 dp first screen.
    @Test fun tallPortraitCapsTheTopAtTheFoldCover() {
        val b = homeBoard(HomeFrame(360f, 640f, true), 2, tools = 7, games = 6)
        assertEquals(HomeToolMode.COLUMN, b.mode); assertEquals(10, b.slots); near(235f, b.topH, "topH: the Fold cover's")
        near((235f - 24f) / 5, b.square, "square"); near(114f, b.pinH, "pinH"); near(158f, b.pinW, "pinW"); assertEquals(2, b.perRow)
        near(48f + 235f + 12f, b.tilesY, "tilesY"); near(235f, tiles(b, HomeTopKind.CONTINUE).single().rect.h, "continue h")
        near(b.tilesY + 3 * 114f + 2 * 12f + 3f + 8f, b.pageH, "pageH: three rows")
        assertTrue(b.tilesY + b.pinH + 3f <= 640f - 8f, "the first row on the first screen")
        // A shorter body than the Fold cover's takes the body; a tiny one keeps the 145 floor and scrolls.
        near(260f - 48f - 8f, homeBoard(HomeFrame(475f, 260f, true), 2, tools = 3, games = 2).topH, "short: the body")
        near(145f, homeBoard(HomeFrame(475f, 150f, true), 2, tools = 3, games = 2).topH, "tiny: the floor")
    }
    // The cap (656 − 14) / 2 / 1.17 = 274.36, square (274.36 − 4 · 7) / 5 = 49.27, the squares half a gap apart filling
    // the top (topH 274.36). A landscape halves body puts the first 2 games inline: left of the squares 656 − 49.27 − 14
    // = 592.73, Continue 0.6 · (592.73 − 14) = 347.24.
    @Test fun largePortraitIsColumn() {
        val b = homeBoard(HomeFrame(704f, 845f, false), 1, tools = 3, games = 4)   // not the portrait type: one strip line
        assertEquals(HomeToolMode.COLUMN, b.mode); near(274.36f, b.topH, "topH"); near(49.27f, b.square, "square"); near(347.24f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
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
            assertTrue(b.square >= 0f, "square $what")
            b.top.forEach { assertTrue(it.rect.w >= 0f && it.rect.h >= 0f, "${it.kind} ${it.rect} $what") }
        }
        val b = homeBoard(HomeFrame(600f, 40f, false), 1, tools = 3, games = 0)
        assertEquals(4, b.top.size); b.top.forEach { assertTrue(it.rect.w >= 0f && it.rect.h >= 0f, "${it.kind} ${it.rect}") }
    }

    // No tools: Continue runs the full width, or to a gap before the inline games on a landscape face (the wide face with
    // 3 games: 2 inline, flush right, 0.4 · (912 − 14) = 359.2 wide: Continue the other 0.6, 538.8; 1 row game).
    @Test fun noToolsContinueFills() {
        val col = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 2)
        near(443f, tiles(col, HomeTopKind.CONTINUE).single().rect.w, "full width")
        val none = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 0)
        assertEquals(1, none.top.size); near(912f, tiles(none, HomeTopKind.CONTINUE).single().rect.w, "full width")
        val sps = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 3)
        assertEquals(3, sps.top.size); near(538.8f, tiles(sps, HomeTopKind.CONTINUE).single().rect.w, "to the games"); assertEquals(1, sps.rowGames)
        near(24f + 912f, tiles(sps, HomeTopKind.GAME).first().rect.let { it.x + it.w }, "the games flush right")
    }
    // No games: the wide face's top is the body (308), with tools or without, the squares min(58, (308 − 4 · 7) / 5) = 56,
    // the page 344.
    @Test fun noGamesTopTakesTheBody() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 2, games = 0)
        assertEquals(0, b.rowGames); near(56f, b.square, "square"); near(308f, b.topH, "the body"); near(344f, b.pageH, "page")
        val none = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 0)
        near(344f - 28f - 8f, none.topH, "body (topY 28)"); near(344f, none.pageH, "page")
    }
    @Test fun pinRects() {
        val b = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 3)
        val p = homeBoardPin(b, 2)
        near(16f, p.x, "x"); near(b.tilesY + b.pinH + 12f, p.y, "second row")
    }
    // Without games the wide face's top is the body, 248 − 28 − 8 = 212: squares min(58, (212 − 4 · 7) / 5) = 36.8 in one
    // column top-aligned at topY, Continue 792 − 36.8 − 14 = 741.2.
    @Test fun wideSquaresFillTheBodyWithoutGames() {
        val b = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 3, games = 0)
        assertEquals(HomeToolMode.WIDE, b.mode); near(36.8f, b.square, "square")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(741.2f, c.w, "continue w"); near(b.topH, c.h, "continue h")
        near(b.topY, tiles(b, HomeTopKind.TOOL).first().rect.y, "the block top-aligned")
    }
    // A narrow landscape split (400 × 304, SPS3): Continue, the 2 inline games, then the squares, each a gap apart, none
    // empty (the top the body 304 − 28 − 8 = 268, squares (268 − 28) / 5 = 48; left of them 352 − 48 − 14 = 290:
    // Continue 0.6 · (290 − 14) = 165.6); the third game a row game.
    @Test fun narrowLandscapeKeepsContinueBesideTheSquares() {
        val b = homeBoard(HomeFrame(400f, 304f, false, 400f / 392f), 1, tools = 3, games = 3)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames)
        b.top.forEach { assertTrue(it.rect.w > 0f, "${it.kind} w ${it.rect.w}") }
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        val game = tiles(b, HomeTopKind.GAME).first().rect
        val firstSquare = b.top.first { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.rect
        near(165.6f, c.w, "continue w")
        assertTrue(c.x + c.w <= game.x - b.gap + 0.05f, "continue ends a gap before the games")
        assertTrue(game.x + game.w <= firstSquare.x - b.gap + 0.05f, "the games end a gap before the squares")
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

    // SPS3 (a sub-840 landscape split) stacks its squares half a gap apart (7 dp), sized so 5 rows and their 4 half gaps
    // fill the top section (the body, 304), as on the portrait type: it saves nothing, so the pin row keeps its full shape
    // (gain 0, 113.67), a scroll away under the body at 28 + 304 + 14 = 346. 8 games: 2 inline, 6 row games in 2 rows.
    @Test fun sps3BlockRowsSitHalfAGapApartAndFillTheTop() {
        data class Case(val f: HomeFrame, val lines: Int, val mode: HomeToolMode, val rows: Int, val gain: Float)
        val cases = listOf(
            Case(HomeFrame(800f, 340f, false, 800f / 428f), 1, HomeToolMode.SPS3, 5, 0f),
        )
        for (c in cases) {
            val b = homeBoard(c.f, c.lines, tools = 10, games = 8)
            val noTools = homeBoard(c.f, c.lines, tools = 0, games = 8)
            assertEquals(c.mode, b.mode)
            val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
            val ys = sq.map { it.y }.distinct().sorted()
            assertEquals(c.rows, ys.size, "${c.mode} rows")
            ys.zipWithNext().forEach { (a, n) -> near(b.square + b.gap / 2, n - a, "${c.mode} row step") }
            val xs = sq.map { it.x }.distinct().sorted()
            xs.zipWithNext().forEach { (a, n) -> near(b.square + b.gap, n - a, "${c.mode} column step") }
            near(c.rows * b.square + (c.rows - 1) * b.gap / 2, b.topH, "${c.mode} topH: the block")
            near(b.topH, tiles(b, HomeTopKind.CONTINUE).single().rect.h, "${c.mode} Continue as tall")
            near(noTools.topH - c.gain, b.topH, "${c.mode} top shorter by the gain")
            near(noTools.pinH + c.gain, b.pinH, "${c.mode} pinH gain")
            near(noTools.tilesY + noTools.pinH, b.tilesY + b.pinH, "${c.mode} the first row ends where it did")
            near(346f, b.tilesY, "${c.mode} the rows under the body"); near(113.67f, b.pinH, "${c.mode} pinH: the full shape")
            val p1 = homeBoardPin(b, b.perRow)
            near(b.tilesY + b.pinH + b.gap, p1.y, "${c.mode} second row")
            near(p1.y + b.pinH + 3f + 8f, b.pageH, "${c.mode} pageH")
        }
    }

    // The wide face and UNDER keep their row heights, the wide face's squares a 2-column block (1 2 / 3 4 / 5 6) with rows
    // half a gap apart; with no tools nothing changes.
    @Test fun wideUnderAndNoToolsKeepTheirGeometry() {
        val wide = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 6, games = 4)
        assertEquals(HomeToolMode.WIDE, wide.mode); near(90.25f, wide.pinH, "WIDE pinH"); near(248f - 28f - 8f, wide.topH, "WIDE topH: the body")
        val sq = tiles(wide, HomeTopKind.TOOL).map { it.rect }
        near(sq[0].x + wide.square + wide.gap, sq[1].x, "WIDE column step"); near(sq[0].y + wide.square + wide.gap / 2, sq[2].y, "WIDE row step")
        // UNDER on a tall window without the pad (flipTallIsUnder); the Fold cover without tools fills its body.
        val under = homeBoard(HomeFrame(360f, 423f, false), 2, tools = 5, games = 4)
        assertEquals(HomeToolMode.UNDER, under.mode); near(273.2f, under.topH, "UNDER topH"); near(71f, under.pinH, "UNDER pinH")
        near(208f, tiles(under, HomeTopKind.CONTINUE).single().rect.h, "UNDER Continue")
        val fold = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 4)
        near(235f, fold.topH, "no tools topH"); near(114.47f, fold.pinH, "no tools pinH"); near(295f, fold.tilesY, "no tools: the rows below the body")
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
