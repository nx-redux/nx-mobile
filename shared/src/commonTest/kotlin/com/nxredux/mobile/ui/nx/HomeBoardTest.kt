package com.nxredux.mobile.ui.nx

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)

class HomeBoardTest {
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }

    // The mockup's landscape (WIDE) with 4 tools. Continue and the first 2 of 6 games keep the first screen (topY
    // 2 + 18 + 8 = 28; the whole body 344 − 28 − 8 = 308) across the page (884 between the insets): the inline column
    // 0.4 · (884 − 14) = 348, Continue the other 0.6, 522. Below the top section: the 4 remaining games in 2 rows of 3
    // (285.33 × 124.4) from 350, then the 4 pinned tools in 2 rows of 3 (285.33 × toolH) a gap below the row games.
    @Test fun wideFacePutsToolsAfterPinnedGames() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), stripLines = 1, tools = 4, games = 6)
        val th = 124.4f
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more)
        near(28f, b.topY, "topY"); near(308f, b.topH, "topH"); near(285.33f, b.toolW, "toolW"); near(th, b.toolH, "toolH")
        tiles(b, HomeTopKind.CONTINUE).single().rect.let { near(522f, it.w, "continue w"); near(308f, it.h, "continue h: the body") }
        assertEquals(0, tiles(b, HomeTopKind.TOOL).size); assertEquals(0, tiles(b, HomeTopKind.MORE).size)
        assertEquals(2, b.inlineGames); assertEquals(2, tiles(b, HomeTopKind.GAME).size)
        tiles(b, HomeTopKind.GAME).forEach { near(38f + 522f + 14f, it.rect.x, "game x"); near(348f, it.rect.w, "game w"); near(147f, it.rect.h, "game h") }
        assertEquals(3, b.perRow); near(285.33f, b.pinW, "pinW"); near(124.4f, b.pinH, "pinH"); assertEquals(4, b.rowGames)
        near(350f, b.tilesY, "the rows a gap under Continue")
        val toolsY = 350f + 2 * 124.4f + 14f + 14f
        assertEquals(4, b.toolTiles.size)
        b.toolTiles.forEachIndexed { i, r ->
            near(38f + (i % 3) * (285.33f + 14f), r.x, "tool $i x")
            near(toolsY + (i / 3) * (th + 14f), r.y, "tool $i y")
            near(285.33f, r.w, "tool $i w"); near(th, r.h, "tool $i h")
        }
    }
    // 6 tools on the wide face: 2 rows of 3 under the 4 row games, matching pinW.
    @Test fun wideToolsFillRowsOfPerRow() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 6, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more)
        near(522f, tiles(b, HomeTopKind.CONTINUE).single().rect.w, "continue w")
        assertEquals(6, b.toolTiles.size)
        val toolsY = b.tilesY + 2 * b.pinH + b.gap + b.gap
        for ((i, r) in b.toolTiles.withIndex()) {
            near(38f + (i % 3) * (b.pinW + 14f), r.x, "tile $i x"); near(toolsY + (i / 3) * (b.toolH + 14f), r.y, "tile $i y")
            near(b.pinW, r.w, "tile $i w"); near(b.toolH, r.h, "tile $i h")
        }
        assertEquals(4, b.rowGames)
    }
    // Many tools wrap cleanly into rows of perRow without "+N" overflow.
    @Test fun wideManyToolsWrapWithoutMore() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 17, games = 6)
        assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more)
        assertEquals(17, b.toolTiles.size)
        assertEquals(3, b.top.size)
    }
    // Flip landscape (WIDE): topH = body (212), 2 inline games, 2 row games at 254, 3 tools in 1 row below them.
    @Test fun flipLandscapeIsWide() {
        val b = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(Int.MAX_VALUE, b.slots); near(245.33f, b.toolW, "toolW"); near(86.75f, b.toolH, "toolH")
        tiles(b, HomeTopKind.CONTINUE).single().rect.let { near(450f, it.w, "continue w"); near(212f, it.h, "continue h") }
        near(212f, b.topH, "topH"); near(28f + 212f + 14f, b.tilesY, "rows under Continue")
        near(b.tilesY + b.pinH + 14f, b.toolTiles.first().y, "tools under row games")
        near(245.33f, b.pinW, "pinW"); near(86.75f, b.pinH, "pinH"); assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames)
    }
    // SPS3 (800 × 340): Continue and 2 inline games fill body (304), 2 row games at 346, 3 tools below them.
    @Test fun landscapeSplitIsSps3() {
        val b = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(Int.MAX_VALUE, b.slots); near(28f, b.topY, "topY"); near(304f, b.topH, "topH")
        near(b.pinW, b.toolW, "toolW"); near(109f, b.toolH, "toolH")
        tiles(b, HomeTopKind.CONTINUE).single().rect.let { near(426f, it.w, "continue w"); near(304f, it.h, "continue h: the body") }
        val toolsY = 346f + 109f + 14f
        b.toolTiles.forEachIndexed { i, r ->
            near(38f + i * (b.pinW + 14f), r.x, "tool $i x"); near(toolsY, r.y, "tool $i y")
        }
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames)
    }
    // SPS3's inline games (800 × 340, 3 tools).
    @Test fun sps3PutsTheFirstTwoGamesInline() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val one = homeBoard(f, 1, tools = 3, games = 1)
        assertEquals(HomeToolMode.SPS3, one.mode); assertEquals(1, one.inlineGames); assertEquals(0, one.rowGames)
        near(304f, one.topH, "1: topH"); near(109f, one.toolH, "1: toolH")
        assertEquals(listOf(HomeTopKind.CONTINUE, HomeTopKind.GAME), one.top.map { it.kind })
        tiles(one, HomeTopKind.CONTINUE).single().rect.let { near(426f, it.w, "1: continue w"); near(304f, it.h, "1: continue h") }
        tiles(one, HomeTopKind.GAME).single().rect.let { near(478f, it.x, "1: game x"); near(28f, it.y, "1: game y"); near(284f, it.w, "1: game w"); near(304f, it.h, "1: game h") }
        near(346f, one.toolTiles.first().y, "1: tools under top when no row games")
        val two = homeBoard(f, 1, tools = 3, games = 2)
        assertEquals(2, two.inlineGames); assertEquals(0, two.rowGames); near(304f, two.topH, "2: topH")
        val g2 = tiles(two, HomeTopKind.GAME).map { it.rect }
        near(28f, g2[0].y, "2: game 0 y"); near(28f + 145f + 14f, g2[1].y, "2: game 1 y")
        g2.forEach { near(284f, it.w, "2: game w"); near(145f, it.h, "2: game h") }
        val three = homeBoard(f, 1, tools = 3, games = 3)
        assertEquals(2, three.inlineGames); assertEquals(1, three.rowGames); near(304f, three.topH, "3: topH")
        tiles(three, HomeTopKind.GAME).forEach { near(284f, it.rect.w, "3: game w"); near(145f, it.rect.h, "3: game h") }
        near(346f, three.tilesY, "3: tilesY"); near(109f, three.pinH, "3: pinH")
        near(346f + 109f + 14f, three.toolTiles.first().y, "3: tools under row game")
    }
    // Nova 4:3 screen (landscape SPS3): perRow = 2, so 2 tools per row aligned with pinned games and shelves, same card size.
    @Test fun novaFourByThreePutsTwoToolsPerRowAfterPinnedGames() {
        val nova = HomeFrame(512f, 320f, false, 512f / 408f)
        val b = homeBoard(nova, 1, tools = 4, games = 3, shelves = listOf(HomeShelf.MOST_PLAYED to 4))
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(2, b.perRow)
        assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames); assertEquals(4, b.toolTiles.size)
        near(b.pinW, b.toolW, "toolW matches pinW"); near(b.pinH, b.toolH, "toolH matches pinH")
        val pin0 = homeBoardPin(b, 0)
        near(pin0.y + pin0.h + b.gap, b.toolTiles[0].y, "tools start after pinned games")
        near(b.toolTiles[0].y, b.toolTiles[1].y, "2 tools on first row")
        near(b.toolTiles[0].y + b.toolH + b.gap, b.toolTiles[2].y, "row 2 below row 1")
        near(b.toolTiles[2].y + b.toolH + b.gap, b.shelfTiles.first().y, "Most played follows tools")
    }
    // All faces show every tool in its band (slots = Int.MAX_VALUE, more = 0).
    @Test fun facesShowAllPinnedToolsWithoutMore() {
        assertEquals(Int.MAX_VALUE, homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 8, games = 4).slots)
        assertEquals(Int.MAX_VALUE, homeBoard(HomeFrame(475f, 291f, true), 2, tools = 8, games = 4).slots)
        assertEquals(Int.MAX_VALUE, homeBoard(HomeFrame(360f, 423f, false), 2, tools = 8, games = 4).slots)
    }
    // The Fold cover (475 × 291, portrait): Continue fills the top (235), pins start at 295 (2 a row, 201.5 × 106.59),
    // and tools sit below the pins on the same 2-column grid at the same card size.
    @Test fun foldCoverIsColumn() {
        val f = HomeFrame(475f, 291f, true)
        val b = homeBoard(f, 2, tools = 3, games = 4)
        assertEquals(HomeToolMode.COLUMN, b.mode); assertEquals(Int.MAX_VALUE, b.slots); near(48f, b.topY, "topY"); near(235f, b.topH, "topH")
        near(201.5f, b.toolW, "toolW"); near(106.59f, b.toolH, "toolH")
        near(106.59f, b.pinH, "pinH"); near(1.5f * (94.75f * 3 / 4), b.pinH, "pinH: 1.5 times the 4:3 half shape"); near(295f, b.tilesY, "tilesY")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(30f, c.x, "continue x"); near(415f, c.w, "continue w: across the page"); near(235f, c.h, "continue h: the body")
        val toolsY = 295f + 2 * b.pinH + 12f + 12f
        b.toolTiles.forEachIndexed { i, r ->
            near(30f + (i % 2) * (201.5f + 12f), r.x, "tool $i x")
            near(toolsY + (i / 2) * (b.toolH + 12f), r.y, "tool $i y: under pins")
        }
        assertEquals(2, b.perRow); near(201.5f, b.pinW, "pinW")
    }
    // UNDER (tall window without the pad, 360 × 423): Continue takes the cap (284 / 1.5 = 189.33); pins and tools follow.
    @Test fun flipTallIsUnder() {
        val b = homeBoard(HomeFrame(360f, 423f, false), 2, tools = 5, games = 4)
        assertEquals(HomeToolMode.UNDER, b.mode); near(189.33f, b.topH, "topH"); near(135f, b.toolW, "toolW"); near(101.25f, b.toolH, "toolH")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(284f, c.w, "continue w"); near(189.33f, c.h, "continue h")
        assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more); assertEquals(5, b.toolTiles.size)
        near(135f, b.pinW, "pinW"); near(101.25f, b.pinH, "pinH")
    }

    // Portrait tools fill rows of 2 under the pinned games (all tools shown, no "+N"), without shrinking Continue.
    @Test fun portraitToolsFillRowsAfterPinnedGames() {
        val f = HomeFrame(475f, 291f, true)
        for (tools in 1..12) {
            val b = homeBoard(f, 2, tools = tools, games = 4)
            assertEquals(HomeToolMode.COLUMN, b.mode); near(235f, b.topH, "$tools: topH"); near(b.pinH, b.toolH, "$tools: toolH")
            near(106.59f, b.pinH, "$tools: pinH"); near(295f, b.tilesY, "$tools: tilesY")
            assertEquals(Int.MAX_VALUE, b.slots, "$tools: slots"); assertEquals(0, b.more, "$tools: more")
            assertEquals(tools, b.toolTiles.size, "$tools: tiles")
            val toolsY = b.tilesY + 2 * b.pinH + 12f + 12f
            for ((i, r) in b.toolTiles.withIndex()) {
                near(30f + (i % 2) * (201.5f + 12f), r.x, "$tools: tile $i x")
                near(toolsY + (i / 2) * (b.toolH + 12f), r.y, "$tools: tile $i y")
            }
            val c = tiles(b, HomeTopKind.CONTINUE).single().rect
            near(415f, c.w, "$tools: continue w"); near(235f, c.h, "$tools: continue h")
        }
        val none = homeBoard(f, 2, tools = 0, games = 4)
        near(235f, none.topH, "no tools: the body"); near(0f, none.toolW, "no tools: no tile")
        tiles(none, HomeTopKind.CONTINUE).single().rect.let { near(415f, it.w, "no tools: full width"); near(235f, it.h, "no tools: today's height") }
    }
    // Tool cards match the game card size across font scales.
    @Test fun toolCardsMatchPinSize() {
        val port = homeBoard(HomeFrame(475f, 291f, true, textScale = 1.3f), 2, tools = 3, games = 0)
        near(port.pinW, port.toolW, "portrait toolW"); near(port.pinH, port.toolH, "portrait toolH")
        val land = homeBoard(HomeFrame(800f, 340f, false, 800f / 428f, textScale = 1.3f), 1, tools = 3, games = 0)
        near(land.pinW, land.toolW, "landscape toolW"); near(land.pinH, land.toolH, "landscape toolH")
    }
    // A taller portrait-type body (the Flip's inner display, 300 dp between the insets) stops its top section at the Fold
    // cover's 235 (from 48); pins and tools follow below.
    @Test fun tallPortraitCapsTheTopAtTheFoldCover() {
        val b = homeBoard(HomeFrame(360f, 640f, true), 2, tools = 7, games = 6)
        assertEquals(HomeToolMode.COLUMN, b.mode); assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more); near(235f, b.topH, "topH")
        near(144f, b.toolW, "toolW"); near(103.5f, b.toolH, "toolH"); near(103.5f, b.pinH, "pinH"); near(144f, b.pinW, "pinW"); assertEquals(2, b.perRow)
        near(295f, b.tilesY, "tilesY"); near(235f, tiles(b, HomeTopKind.CONTINUE).single().rect.h, "continue h")
        val lastTool = b.toolTiles.last()
        near(lastTool.y + lastTool.h + 3f + 8f, b.pageH, "pageH: ends under tools")
        assertTrue(b.tilesY + b.pinH + 3f <= 640f - 8f, "the first row on the first screen")
        near(235f, homeBoard(HomeFrame(360f, 640f, true), 2, tools = 0, games = 6).topH, "no tools: the Fold cover's cap")
        near(260f - 48f - 8f, homeBoard(HomeFrame(475f, 260f, true), 2, tools = 0, games = 2).topH, "short: the body")
        near(145f, homeBoard(HomeFrame(475f, 150f, true), 2, tools = 0, games = 2).topH, "tiny: the floor")
    }
    // A landscape halves body (not the portrait type): Continue and the first 2 games keep the cap
    // (628 − 14) / 2 / 1.17 = 262.39: the inline column 0.4 · (628 − 14) = 245.6, Continue 368.4.
    @Test fun largePortraitIsColumn() {
        val b = homeBoard(HomeFrame(704f, 845f, false), 1, tools = 3, games = 4)   // not the portrait type: one strip line
        assertEquals(HomeToolMode.COLUMN, b.mode); assertEquals(Int.MAX_VALUE, b.slots); near(262.39f, b.topH, "topH"); near(b.pinH, b.toolH, "toolH")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(368.4f, c.w, "continue w"); near(262.39f, c.h, "continue h: the cap")
        assertEquals(2, b.inlineGames); assertEquals(2, b.rowGames); assertEquals(3, b.toolTiles.size)
        near(b.tilesY + b.pinH + b.gap, b.toolTiles.first().y, "tools a gap under row games")
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
        assertEquals(1, b.top.size); assertEquals(3, b.toolTiles.size)
        b.top.forEach { assertTrue(it.rect.w >= 0f && it.rect.h >= 0f, "${it.kind} ${it.rect}") }
        b.toolTiles.forEach { assertTrue(it.w >= 0f && it.h >= 0f, "tool $it") }
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
    // No games: the wide face's Continue is the body (308) across the page (884), with tools or without. Tools sit a gap under it.
    @Test fun noGamesContinueTakesTheBody() {
        val b = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 2, games = 0)
        val th = b.pinH
        assertEquals(0, b.rowGames); near(b.pinW, b.toolW, "toolW"); near(th, b.toolH, "toolH"); near(308f, b.topH, "topH")
        near(28f + 308f + 14f + th + 3f + 8f, b.pageH, "page")
        tiles(b, HomeTopKind.CONTINUE).single().rect.let { near(884f, it.w, "continue w"); near(308f, it.h, "continue h: the body") }
        val none = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), 1, tools = 0, games = 0)
        near(344f - 28f - 8f, none.topH, "body (topY 28)"); near(344f, none.pageH, "page")
    }
    @Test fun pinRects() {
        val b = homeBoard(HomeFrame(475f, 291f, true), 2, tools = 0, games = 3)
        val p = homeBoardPin(b, 2)
        near(30f, p.x, "x"); near(b.tilesY + b.pinH + 12f, p.y, "second row")
    }
    // Without games the wide face's Continue is the body, 248 − 28 − 8 = 212, across the page (764); the 3 tools sit a gap under it.
    @Test fun wideToolBandSitsUnderContinueWithoutGames() {
        val b = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 3, games = 0)
        assertEquals(HomeToolMode.WIDE, b.mode); near(b.pinH, b.toolH, "toolH")
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        near(764f, c.w, "continue w"); near(212f, c.h, "continue h")
        val first = b.toolTiles.first()
        near(38f, first.x, "tools from the page's left"); near(254f, first.y, "tools a gap under Continue")
        near(212f, b.topH, "topH")
    }
    // A narrow landscape split (400 × 304, SPS3): Continue and the 2 inline games across the page, the third game a row game, tools below it.
    @Test fun narrowLandscapeKeepsContinueAndTheGamesOverTheBands() {
        val b = homeBoard(HomeFrame(400f, 304f, false, 400f / 392f), 1, tools = 3, games = 3)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(2, b.inlineGames); assertEquals(1, b.rowGames); assertEquals(Int.MAX_VALUE, b.slots)
        b.top.forEach { assertTrue(it.rect.w > 0f && it.rect.h > 0f, "${it.kind} ${it.rect}") }
        val c = tiles(b, HomeTopKind.CONTINUE).single().rect
        val games = tiles(b, HomeTopKind.GAME).map { it.rect }
        val firstTool = b.toolTiles.first()
        near(186f, c.w, "continue w"); near(268f, c.h, "continue h: the body")
        near(c.x + c.w + b.gap, games[0].x, "the games a gap after Continue"); near(38f + 324f, games[0].x + games[0].w, "the games end the page")
        near(c.y + c.h, games[1].y + games[1].h, "the games end with Continue")
        near(310f + b.pinH + b.gap, firstTool.y, "the tools a gap under the row game")
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

    // SPS3 (800 × 340, 10 tools, 8 games): Continue and 2 inline games fill body (304), 6 row games in 2 rows of 3 from
    // 346, then all 10 tools in 4 rows of 3 a gap below the row games.
    @Test fun sps3ToolsBandSitsAfterPinnedGames() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val b = homeBoard(f, 1, tools = 10, games = 8)
        val noTools = homeBoard(f, 1, tools = 0, games = 8)
        assertEquals(HomeToolMode.SPS3, b.mode)
        assertEquals(10, b.toolTiles.size); assertEquals(b.perRow, 3)
        near(b.gutter, b.toolTiles.first().x, "from the page's left")
        near(b.pinW + b.gap, b.toolTiles[1].x - b.toolTiles[0].x, "column step matches pins")
        near(304f, tiles(b, HomeTopKind.CONTINUE).single().rect.h, "Continue the body")
        near(noTools.topH, b.topH, "topH unchanged by tools")
        near(noTools.pinH, b.pinH, "pinH as without tools")
        near(346f, noTools.tilesY, "no tools: the rows under the body"); near(346f, b.tilesY, "the rows under the body")
        val p1 = homeBoardPin(b, b.perRow)
        near(b.tilesY + b.pinH + b.gap, p1.y, "second pin row")
        near(p1.y + b.pinH + b.gap, b.toolTiles.first().y, "tools start after second pin row")
        val lastTool = b.toolTiles.last()
        near(lastTool.y + lastTool.h + 3f + 8f, b.pageH, "pageH")
    }

    // The wide face and UNDER keep their row heights; with no tools the top section is Continue alone.
    @Test fun wideUnderAndNoToolsKeepTheirGeometry() {
        val wide = homeBoard(HomeFrame(840f, 248f, false, 840f / 336f), 1, tools = 6, games = 4)
        assertEquals(HomeToolMode.WIDE, wide.mode); near(86.75f, wide.pinH, "WIDE pinH"); near(248f - 28f - 8f, tiles(wide, HomeTopKind.CONTINUE).single().rect.h, "WIDE Continue: the body")
        near(wide.toolTiles[0].x + wide.toolW + wide.gap, wide.toolTiles[1].x, "WIDE column step")
        val under = homeBoard(HomeFrame(360f, 423f, false), 2, tools = 5, games = 4)
        assertEquals(HomeToolMode.UNDER, under.mode); near(189.33f, under.topH, "UNDER topH"); near(101.25f, under.pinH, "UNDER pinH")
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
