package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home with Layouts → Page title hidden. The tab row's 44 dp goes to the body's top: the strip moves up into it and
 * the top section gains it: Continue grows, and a landscape face's row of tool tiles stays one row a gap under
 * Continue (the tiles keep the Tools page's size). A hidden hint bar keeps its room (it never reaches the frame), so only
 * the tab row is tested here.
 */
class HomeChromeBoardTest {
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }.map { it.rect }
    private fun continueOf(b: HomeBoard) = tiles(b, HomeTopKind.CONTINUE).single()

    /** [base] with the tab row hidden (its 44 dp in the body) unless [title]. */
    private fun chrome(base: HomeFrame, title: Boolean) = base.copy(h = base.h + (if (title) 0f else 44f), titleHidden = !title)

    // The Fold cover (475 × 291 between the bars), the portrait strip 14 / 18: topY 2 + 2 · 18 + 2 + 8 = 48, the top
    // section capped at 235; title hidden: the body 335, the top 279 (the cap + 44). Pinned tools sit in their own band
    // after the row pinned games (2 a row, all tools shown, no "+N").
    @Test fun foldCoverWithAndWithoutTheTabRow() {
        val cover = HomeFrame(475f, 291f, true)
        for (title in listOf(true, false)) {
            val f = chrome(cover, title)
            for (tools in 1..12) {
                val b = homeBoard(f, 2, tools = tools, games = 4)
                val what = "title $title tools $tools"
                val contH = if (title) 235f else 279f
                assertEquals(HomeToolMode.COLUMN, b.mode, what); near(48f, b.topY, "$what: topY")
                near(contH, b.topH, "$what: topH"); near(b.pinH, b.toolH, "$what: toolH")
                assertEquals(Int.MAX_VALUE, b.slots, "$what: slots"); assertEquals(0, b.more, "$what: more")
                assertEquals(tools, b.toolTiles.size, "$what: tool tiles")
                near(contH, continueOf(b).h, "$what: continue h"); near(415f, continueOf(b).w, "$what: continue w")
                near(48f + b.topH + 12f, b.tilesY, "$what: pins under Continue")
                val pinsEnd = b.tilesY + 2 * b.pinH + 12f
                for ((i, r) in b.toolTiles.withIndex()) {
                    near(30f + (i % 2) * (b.pinW + 12f), r.x, "$what: tile $i x")
                    near(pinsEnd + 12f + (i / 2) * (b.toolH + 12f), r.y, "$what: tile $i y, under pins")
                }
                assertTrue(48f + contH + 8f <= f.h + 0.05f, "$what: Continue ends a margin above the body's bottom")
            }
        }
    }

    // The strip with no tab row: centred between the body's top (the screen's) and the top section, as it is between
    // the underline and the top section with one. The room below ends at Continue's ring (3 dp above topY) and the two lines are
    // homeStripPitch apart: (48 − 3 − pitch − inkBottom − capTop) / 2 = 3.13 dp on the cover (the portrait strip
    // 14 / 18), one landscape line (28 − 3 − inkBottom − capTop) / 2 = 1.88.
    @Test fun theStripCentresUnderTheScreenTopWithNoTabRow() {
        val cover = HomeFrame(475f, 291f, true)
        val hidden = homeBoard(chrome(cover, title = false), 2, tools = 3, games = 0)
        near(3.13f, hidden.stripY, "stripY")
        val land = HomeFrame(800f, 340f, false, 800f / 428f)
        near(1.88f, homeBoard(chrome(land, title = false), 1, tools = 3, games = 4).stripY, "landscape stripY")
    }

    // A taller portrait body (the Flip's inner display) stops its top section at the cap: 235, or 279 with the tab row
    // hidden, whether tools are pinned or not.
    @Test fun tallPortraitCapGrowsByTheTabRow() {
        val inner = HomeFrame(360f, 640f, true)
        near(235f, homeBoard(inner, 2, tools = 0, games = 6).topH, "shown")
        near(279f, homeBoard(chrome(inner, title = false), 2, tools = 0, games = 6).topH, "hidden: the cap + 44")
        val shown = homeBoard(inner, 2, tools = 3, games = 6)
        val hidden = homeBoard(chrome(inner, title = false), 2, tools = 3, games = 6)
        near(235f, continueOf(shown).h, "shown: Continue at cap"); near(235f, shown.topH, "shown: topH")
        near(279f, continueOf(hidden).h, "hidden: Continue at cap + 44"); near(279f, hidden.topH, "hidden: topH")
        assertEquals(Int.MAX_VALUE, hidden.slots); near(shown.toolH, hidden.toolH, "the tile's size either way")
    }

    // SPS3 (a 16:9 phone in landscape, 800 × 340): Continue takes the 44 dp, the whole body 384 − 28 − 8 = 348 (304
    // with the tab row). Tools sit in their own band after the 2 row games on the 3-column grid.
    @Test fun sps3ContinueTakesTheTabRowsRoom() {
        val land = HomeFrame(800f, 340f, false, 800f / 428f)
        val shown = homeBoard(land, 1, tools = 7, games = 4)
        assertEquals(HomeToolMode.SPS3, shown.mode); assertEquals(Int.MAX_VALUE, shown.slots); assertEquals(0, shown.more)
        near(304f, continueOf(shown).h, "shown: continue h")
        val b = homeBoard(chrome(land, title = false), 1, tools = 7, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(Int.MAX_VALUE, b.slots); assertEquals(0, b.more)
        near(b.pinW, b.toolW, "toolW"); near(109f, b.toolH, "toolH"); near(348f, b.topH, "topH")
        near(109f, b.pinH, "pinH"); near(348f, continueOf(b).h, "continue h: the whole body")
        assertEquals(7, b.toolTiles.size)
        val toolsY = 28f + 348f + 14f + 109f + 14f
        b.toolTiles.forEachIndexed { i, r ->
            near(38f + (i % 3) * (b.pinW + 14f), r.x, "tile $i x")
            near(toolsY + (i / 3) * (b.toolH + 14f), r.y, "tile $i y")
        }
    }

    // The Flip's wide face (804 × 261, 0–2 games): Continue and the games the body, 225 → 269. All 12 tools sit in rows
    // of 3 a gap under Continue.
    @Test fun flipWideWithAndWithoutTheTabRow() {
        val flip = HomeFrame(804f, 261f, false, 804f / 330f)
        for ((title, body) in listOf(true to 225f, false to 269f)) {
            val f = chrome(flip, title)
            val what = "title $title"
            val b = homeBoard(f, 1, tools = 12, games = 2)
            assertEquals(HomeToolMode.WIDE, b.mode, what); near(body, b.topH, "$what: topH")
            assertEquals(Int.MAX_VALUE, b.slots, "$what: slots"); assertEquals(0, b.more, "$what: more")
            near(body, continueOf(b).h, "$what: continue h")
            assertEquals(12, b.toolTiles.size, what)
            for ((i, r) in b.toolTiles.withIndex()) near(28f + body + 14f + (i / 3) * (b.toolH + 14f), r.y, "$what: tile $i y")
            val g = tiles(b, HomeTopKind.GAME)
            near(28f, g[0].y, "$what: game 0 y"); near(28f + body, g[1].y + g[1].h, "$what: the games end with Continue")
            near(g[0].h, g[1].h, "$what: the games alike")
        }
        val rowsShown = homeBoard(flip, 1, tools = 6, games = 4)
        val rowsHidden = homeBoard(chrome(flip, title = false), 1, tools = 6, games = 4)
        near(225f, rowsShown.topH, "row games, shown"); near(269f, rowsHidden.topH, "row games, hidden")
        near(rowsShown.toolH, rowsHidden.toolH, "row games: the same tile"); near(rowsShown.pinH, rowsHidden.pinH, "the pin row keeps its shape")
    }

    // The unfolded Fold (933 × 572, the large face): Continue 2 · 128.83 + 7 = 264.67 → 308.67. The bands below move
    // down by the 44 dp.
    @Test fun foldUnfoldedWithAndWithoutTheTabRow() {
        val fold = HomeFrame(933f, 572f, false, 933f / 660f)
        val shown = homeBoard(fold, 1, tools = 9, games = 6, shelves = listOf(HomeShelf.NEW to 6))
        for ((title, contH) in listOf(true to 264.67f, false to 308.67f)) {
            val f = chrome(fold, title)
            val what = "title $title"
            val b = homeBoard(f, 1, tools = 9, games = 6, shelves = listOf(HomeShelf.NEW to 6))
            assertEquals(HomeToolMode.LARGE, b.mode, what); near(contH, b.topH, "$what: topH")
            assertEquals(Int.MAX_VALUE, b.slots, "$what: slots"); assertEquals(0, b.more, "$what: more")
            near(contH, continueOf(b).h, "$what: continue h")
            assertEquals(9, b.toolTiles.size, what)
            val drop = contH - 264.67f
            near(shown.tilesY + drop, b.tilesY, "$what: the bands follow")
            near(shown.toolTiles[0].y + drop, b.toolTiles[0].y, "$what: the tools")
            near(shown.shelfTiles[0].y + drop, b.shelfTiles[0].y, "$what: the shelves"); near(shown.pageH + drop, b.pageH, "$what: pageH")
        }
    }

    // A tall narrow window without the pad (UNDER) takes the 44 dp: Continue 189.33 → 233.33.
    @Test fun underTakesTheHeightOnly() {
        val tall = HomeFrame(360f, 423f, false)
        val b = homeBoard(chrome(tall, title = false), 2, tools = 7, games = 4)
        assertEquals(HomeToolMode.UNDER, b.mode); assertEquals(Int.MAX_VALUE, b.slots); near(135f, b.toolW, "toolW"); near(101.25f, b.toolH, "toolH")
        near(233.33f, b.topH, "topH"); near(233.33f, continueOf(b).h, "continue h")
        assertEquals(7, b.toolTiles.size)
    }

    // The face is read from the body as it would be with the tab row: its freed room never changes it.
    @Test fun theFreedRoomNeverChangesTheFace() {
        for (base in listOf(
            HomeFrame(960f, 344f, false), HomeFrame(933f, 572f, false), HomeFrame(800f, 340f, false), HomeFrame(400f, 440f, false),
            HomeFrame(846f, 450f, false, 846f / 538f), HomeFrame(475f, 291f, true),
        )) {
            val mode = homeBoard(base, 1, tools = 3, games = 2).mode
            for (t in listOf(true, false)) assertEquals(mode, homeBoard(chrome(base, t), 1, tools = 3, games = 2).mode, "$base title $t")
        }
    }
}
