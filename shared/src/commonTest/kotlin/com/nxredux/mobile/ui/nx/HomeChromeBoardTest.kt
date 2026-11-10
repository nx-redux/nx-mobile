package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home with Layouts → Page title hidden. The tab row's 44 dp goes to the body's top: the strip moves up into it and
 * the top section gains it: Continue grows, and a column of tool tiles beside it may fit another row (the tiles keep
 * the Tools page's size). A hidden hint bar keeps its room (it never reaches the frame), so only the tab row is tested here.
 */
class HomeChromeBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }.map { it.rect }
    private fun continueOf(b: HomeBoard) = tiles(b, HomeTopKind.CONTINUE).single()

    /** [base] with the tab row hidden (its 44 dp in the body) unless [title]. */
    private fun chrome(base: HomeFrame, title: Boolean) = base.copy(h = base.h + (if (title) 0f else 44f), titleHidden = !title)

    // The Fold cover (475 × 291 between the bars), the portrait strip 14 / 18: topY 2 + 2 · 18 + 2 + 8 = 48, the top
    // section capped at 235; title hidden: the body 335, the top 279 (the cap + 44). The tools 4 a row under Continue
    // (97.75 × 118.25, 8 dp apart), 8 slots either way. Shown, Continue keeps the 145 floor for one row or two; hidden,
    // one row leaves it 279 − 12 − 118.25 = 148.75, two the floor.
    @Test fun foldCoverWithAndWithoutTheTabRow() {
        val cover = HomeFrame(475f, 291f, true)
        for (title in listOf(true, false)) {
            val f = chrome(cover, title)
            for (tools in 1..12) {
                val b = homeBoard(f, 2, tools = tools, games = 4)
                val what = "title $title tools $tools"
                val rows = if (tools <= 4) 1 else 2
                val rowsH = rows * 118.25f + (rows - 1) * 8f
                val contH = if (!title && rows == 1) 148.75f else 145f
                assertEquals(HomeToolMode.COLUMN, b.mode, what); near(48f, b.topY, "$what: topY")
                near(contH + 12f + rowsH, b.topH, "$what: topH"); near(118.25f, b.toolH, "$what: toolH")
                assertEquals(8, b.slots, "$what: slots")
                assertEquals(if (tools > 8) tools - 8 + 1 else 0, b.more, "$what: more")
                val sq = b.top.filter { it.kind != HomeTopKind.CONTINUE }
                for ((i, t) in sq.withIndex()) {
                    near(30f + (i % 4) * (97.75f + 8f), t.rect.x, "$what: tile $i x")
                    near(48f + contH + 12f + (i / 4) * (118.25f + 8f), t.rect.y, "$what: tile $i y, under Continue")
                }
                near(contH, continueOf(b).h, "$what: continue h"); near(415f, continueOf(b).w, "$what: continue w")
                near(48f + b.topH + 12f, b.tilesY, "$what: the rows under the tools")
                assertTrue(48f + contH + 8f <= f.h + 0.05f, "$what: Continue ends a margin above the body's bottom")
            }
        }
    }

    // The strip with no tab row: centred between the body's top (the screen's) and the top section, as it was between
    // the underline and the top section. The room below ends at Continue's ring (3 dp above topY) and the two lines are
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
    // hidden. One row of its 116.4 dp tiles: shown, Continue keeps the 145 floor (the top 273.4); hidden, it takes
    // 279 − 12 − 116.4 = 150.6 and the top stays 279.
    @Test fun tallPortraitCapGrowsByTheTabRow() {
        val inner = HomeFrame(360f, 640f, true)
        near(235f, homeBoard(inner, 2, tools = 0, games = 6).topH, "shown")
        near(279f, homeBoard(chrome(inner, title = false), 2, tools = 0, games = 6).topH, "hidden: the cap + 44")
        val shown = homeBoard(inner, 2, tools = 3, games = 6)
        val hidden = homeBoard(chrome(inner, title = false), 2, tools = 3, games = 6)
        near(145f, continueOf(shown).h, "shown: Continue at the floor"); near(273.4f, shown.topH, "shown: the top grows")
        near(150.6f, continueOf(hidden).h, "hidden: Continue gives way"); near(279f, hidden.topH, "hidden: the cap")
        assertEquals(6, hidden.slots); near(shown.toolH, hidden.toolH, "the tile's size either way")
    }

    // SPS3 (a 16:9 phone in landscape, 800 × 340): the top section takes the 44 dp, the whole body 384 − 28 − 8 = 348
    // (304 with the tab row): 3 rows of tiles fit (floor(356 / 115.6)), 2 before, so 6 slots (4). 7 tools: 5 and "+2"
    // hidden (3 and "+4" shown), 2 columns, the "+2" at the right of the third row. The pin row keeps its shape (109,
    // from the bars-shown height).
    @Test fun sps3TakesARowMoreWithTheTabRowHidden() {
        val land = HomeFrame(800f, 340f, false, 800f / 428f)
        val shown = homeBoard(land, 1, tools = 7, games = 4)
        assertEquals(HomeToolMode.SPS3, shown.mode); assertEquals(4, shown.slots); assertEquals(4, shown.more)
        val b = homeBoard(chrome(land, title = false), 1, tools = 7, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(6, b.slots); assertEquals(2, b.more)
        near(80f, b.toolW, "toolW"); near(107.6f, b.toolH, "toolH: the same tile"); near(348f, b.topH, "topH: the whole body")
        near(109f, b.pinH, "pinH")
        val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
        assertEquals(6, sq.size)
        near(28f + 2 * 115.6f, sq[5].y, "+2: the third row"); near(sq[1].x, sq[5].x, "its column, the right one")
        assertEquals(1, tiles(homeBoard(chrome(land, title = false), 1, tools = 3, games = 4), HomeTopKind.TOOL).map { it.x }.distinct().size, "3 tools: one column of 3")
    }

    // The Flip's wide face (804 × 261, 0–2 games: the top section the body): 225 → 269, 2 rows of tiles either way
    // (floor(277 / 115.6)), so 6 slots: 12 tools show 5 and "+7", 3 columns, top-aligned; Continue and the games take
    // the taller top.
    @Test fun flipWideWithAndWithoutTheTabRow() {
        val flip = HomeFrame(804f, 261f, false, 804f / 330f)
        for ((title, topH) in listOf(true to 225f, false to 269f)) {
            val f = chrome(flip, title)
            val what = "title $title"
            val b = homeBoard(f, 1, tools = 12, games = 2)
            assertEquals(HomeToolMode.WIDE, b.mode, what); near(topH, b.topH, "$what: topH"); near(107.6f, b.toolH, "$what: toolH")
            assertEquals(6, b.slots, "$what: slots"); assertEquals(7, b.more, "$what: more")
            near(topH, continueOf(b).h, "$what: continue h")
            val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
            assertEquals(6, sq.size, what)
            for ((i, r) in sq.withIndex()) near(28f + (i / 3) * 115.6f, r.y, "$what: tile $i y")
            val g = tiles(b, HomeTopKind.GAME)
            near(28f, g[0].y, "$what: game 0 y"); near(28f + topH, g[1].y + g[1].h, "$what: the games end with Continue")
            near(g[0].h, g[1].h, "$what: the games alike")
        }
        // Past 2 games the rest are row games a scroll away: the top is still the body (225 → 269).
        val rowsShown = homeBoard(flip, 1, tools = 6, games = 4)
        val rowsHidden = homeBoard(chrome(flip, title = false), 1, tools = 6, games = 4)
        near(225f, rowsShown.topH, "row games, shown"); near(269f, rowsHidden.topH, "row games, hidden")
        near(rowsShown.toolH, rowsHidden.toolH, "row games: the same tile"); near(rowsShown.pinH, rowsHidden.pinH, "the pin row keeps its shape")
    }

    // The unfolded Fold (933 × 572, the large face): topH 2 · 128.83 + 7 = 264.67 → 308.67, 2 rows of tiles either way
    // (floor(316.67 / 115.6)), so 4 slots: 9 tools show 3 and "+6"; the bands below move down by the 44 dp.
    @Test fun foldUnfoldedWithAndWithoutTheTabRow() {
        val fold = HomeFrame(933f, 572f, false, 933f / 660f)
        val shown = homeBoard(fold, 1, tools = 9, games = 6, shelves = listOf(HomeShelf.NEW to 6))
        for ((title, topH) in listOf(true to 264.67f, false to 308.67f)) {
            val f = chrome(fold, title)
            val what = "title $title"
            val b = homeBoard(f, 1, tools = 9, games = 6, shelves = listOf(HomeShelf.NEW to 6))
            assertEquals(HomeToolMode.LARGE, b.mode, what); near(topH, b.topH, "$what: topH"); near(107.6f, b.toolH, "$what: toolH")
            assertEquals(4, b.slots, "$what: slots"); assertEquals(6, b.more, "$what: more")
            near(topH, continueOf(b).h, "$what: continue h")
            val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
            for ((i, r) in sq.withIndex()) near(28f + (i / 2) * 115.6f, r.y, "$what: tile $i y")
            assertTrue(sq.all { it.y + it.h <= 28f + topH + 0.05f }, "$what: the tiles inside the top")
            val drop = topH - 264.67f
            near(shown.tilesY + drop, b.tilesY, "$what: the bands follow")
            near(shown.shelfTiles[0].y + drop, b.shelfTiles[0].y, "$what: the shelves"); near(shown.pageH + drop, b.pageH, "$what: pageH")
        }
    }

    // A tall narrow window without the pad (UNDER) keeps its row of 3 tiles and takes the 44 dp: the cap 316.53 + 44,
    // Continue 189.33 → 233.33.
    @Test fun underTakesTheHeightOnly() {
        val tall = HomeFrame(360f, 423f, false)
        val b = homeBoard(chrome(tall, title = false), 2, tools = 7, games = 4)
        assertEquals(HomeToolMode.UNDER, b.mode); assertEquals(3, b.slots); near(89.33f, b.toolW, "toolW"); near(113.2f, b.toolH, "toolH")
        near(360.53f, b.topH, "topH"); near(233.33f, continueOf(b).h, "continue h")
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
