package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home with Layouts → Page title hidden. The tab row's 44 dp goes to the body's top: the strip moves up into it and
 * the top section gains it, the squares growing (to each face's cap), every column keeping its 5. A hidden hint bar keeps its room (it never reaches the
 * frame), so only the tab row is tested here.
 */
class HomeChromeBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }.map { it.rect }
    private fun continueOf(b: HomeBoard) = tiles(b, HomeTopKind.CONTINUE).single()

    /** [base] with the tab row hidden (its 44 dp in the body) unless [title]. */
    private fun chrome(base: HomeFrame, title: Boolean) = base.copy(h = base.h + (if (title) 0f else 44f), titleHidden = !title)

    // The Fold cover (475 × 291 between the bars), the portrait strip 14 / 18: topY 2 + 2 · 18 + 2 + 8 = 48, the top
    // section capped at 235, 5 squares a column of (235 − 24) / 5 = 42.2. Title hidden: the body 335, the top 279 (the
    // cap + 44), still 5 squares a column, of (279 − 24) / 5 = 51; 10 slots past 5 tools.
    @Test fun foldCoverWithAndWithoutTheTabRow() {
        val cover = HomeFrame(475f, 291f, true)
        data class Case(val title: Boolean, val topH: Float, val side: Float, val rows: Int, val contH: Float)
        for (c in listOf(Case(true, 235f, 42.2f, 5, 235f), Case(false, 279f, 255f / 5, 5, 279f))) {
            val f = chrome(cover, c.title)
            for (tools in 1..14) {
                val b = homeBoard(f, 2, tools = tools, games = 4)
                val what = "title ${c.title} tools $tools"
                assertEquals(HomeToolMode.COLUMN, b.mode, what); near(48f, b.topY, "$what: topY")
                near(c.topH, b.topH, "$what: topH"); near(c.side, b.square, "$what: square")
                assertEquals(if (tools <= c.rows) c.rows else 2 * c.rows, b.slots, "$what: slots")
                assertEquals(if (tools > 2 * c.rows) tools - 2 * c.rows + 1 else 0, b.more, "$what: more")
                val sq = b.top.filter { it.kind != HomeTopKind.CONTINUE }
                for ((i, t) in sq.withIndex()) {
                    near(16f + 443f - c.side - (i / c.rows) * (c.side + 12f), t.rect.x, "$what: square $i x")
                    near(48f + (i % c.rows) * (c.side + 6f), t.rect.y, "$what: square $i y, top-aligned")
                }
                near(c.contH, continueOf(b).h, "$what: continue h"); near(48f + c.contH + 12f, b.tilesY, "$what: the rows under Continue")
                assertTrue(48f + c.contH + 8f <= f.h + 0.05f, "$what: Continue ends a margin above the body's bottom")
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

    // A taller portrait body (the Flip's inner display) stops its top section at the cap: 235, or 279 with the tab row hidden.
    @Test fun tallPortraitCapGrowsByTheTabRow() {
        val inner = HomeFrame(360f, 640f, true)
        val shown = homeBoard(inner, 2, tools = 7, games = 6)
        val hidden = homeBoard(chrome(inner, title = false), 2, tools = 7, games = 6)
        near(235f, shown.topH, "shown"); near(279f, hidden.topH, "hidden: the cap + 44")
        assertEquals(10, hidden.slots); near(255f / 5, hidden.square, "square: still 5 a column, bigger")
    }

    // SPS3 (a 16:9 phone in landscape, 800 × 340): the top section takes the 44 dp, still 5 rows:
    // the top the whole body 384 − 28 − 8 = 348 (304 with the tab row), squares (348 − 4 · 7) / 5 = 64 (55.2), half gaps
    // apart, filling it; 10 slots past 5 tools. The pin row keeps its shape (113.67, from the bars-shown height).
    @Test fun sps3KeepsFiveAColumnAndGrowsThem() {
        val land = HomeFrame(800f, 340f, false, 800f / 428f)
        val shown = homeBoard(land, 1, tools = 7, games = 4)
        assertEquals(HomeToolMode.SPS3, shown.mode); assertEquals(10, shown.slots); near(55.2f, shown.square, "shown square")
        val b = homeBoard(chrome(land, title = false), 1, tools = 7, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(10, b.slots); assertEquals(0, b.more)
        near(64f, b.square, "square"); near(348f, b.topH, "topH: the block, the whole body")
        near(113.67f, b.pinH, "pinH")
        val sq = tiles(b, HomeTopKind.TOOL)
        near(28f + 3 * (64f + 7f), sq[6].y, "the 7th square: the 4th row"); near(sq[0].x, sq[6].x, "its column")
        assertEquals(5, homeBoard(chrome(land, title = false), 1, tools = 5, games = 4).slots, "5 tools: one column")
    }

    // The Flip's wide face (804 × 261, 0–2 games: the top section the body): 225, squares (225 − 4 · 7) / 5 = 39.4 → 269,
    // min(58, (269 − 28) / 5) = 48.2; 5 rows and 15 slots either way, 12 tools 3 columns.
    @Test fun flipWideWithAndWithoutTheTabRow() {
        val flip = HomeFrame(804f, 261f, false, 804f / 330f)
        data class Case(val title: Boolean, val topH: Float, val side: Float, val contH: Float)
        for (c in listOf(Case(true, 225f, 39.4f, 225f), Case(false, 269f, 48.2f, 269f))) {
            val f = chrome(flip, c.title)
            val what = "title ${c.title}"
            val b = homeBoard(f, 1, tools = 12, games = 2)
            assertEquals(HomeToolMode.WIDE, b.mode, what); near(c.topH, b.topH, "$what: topH"); near(c.side, b.square, "$what: square")
            assertEquals(15, b.slots, "$what: slots"); assertEquals(0, b.more, "$what: more")
            near(c.contH, continueOf(b).h, "$what: continue h")
            val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
            assertEquals(12, sq.size, what)
            for ((i, r) in sq.withIndex()) near(28f + (i / 3) * (c.side + 7f), r.y, "$what: square $i y")
            val g = tiles(b, HomeTopKind.GAME)
            near(28f, g[0].y, "$what: game 0 y"); near(28f + c.contH, g[1].y + g[1].h, "$what: the games end with Continue")
            near(g[0].h, g[1].h, "$what: the games alike")
        }
        // Past 2 games the rest are row games a scroll away: the top is still the body (225 → 269), squares 48.2 hidden.
        val rowsShown = homeBoard(flip, 1, tools = 6, games = 4)
        val rowsHidden = homeBoard(chrome(flip, title = false), 1, tools = 6, games = 4)
        near(225f, rowsShown.topH, "row games, shown"); near(269f, rowsHidden.topH, "row games, hidden")
        near(48.2f, rowsHidden.square, "row games square"); near(rowsShown.pinH, rowsHidden.pinH, "the pin row keeps its shape")
    }

    // The unfolded Fold (933 × 572, the large face): topH 2 · 133.5 + 7 = 274, 5 squares a column of (274 − 4 · 7) / 5 =
    // 49.2 → 318 and still 5, of min(66.75, (318 − 28) / 5) = 58; 10 slots either way.
    @Test fun foldUnfoldedWithAndWithoutTheTabRow() {
        val fold = HomeFrame(933f, 572f, false, 933f / 660f)
        data class Case(val title: Boolean, val topH: Float, val side: Float, val contH: Float)
        val shown = homeBoard(fold, 1, tools = 9, games = 6, shelves = listOf(HomeShelf.NEW to 6))
        for (c in listOf(Case(true, 274f, 49.2f, 274f), Case(false, 318f, 58f, 318f))) {
            val f = chrome(fold, c.title)
            val what = "title ${c.title}"
            val b = homeBoard(f, 1, tools = 9, games = 6, shelves = listOf(HomeShelf.NEW to 6))
            assertEquals(HomeToolMode.LARGE, b.mode, what); near(c.topH, b.topH, "$what: topH"); near(c.side, b.square, "$what: square")
            assertEquals(10, b.slots, "$what: slots"); assertEquals(0, b.more, "$what: more")
            near(c.contH, continueOf(b).h, "$what: continue h")
            val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
            for ((i, r) in sq.withIndex()) near(28f + (i / 2) * (c.side + 7f), r.y, "$what: square $i y")
            assertTrue(sq.all { it.y + it.h <= 28f + c.topH + 0.05f }, "$what: the squares inside the top")
            val drop = c.topH - 274f
            near(shown.tilesY + drop, b.tilesY, "$what: the bands follow"); near(shown.labels[0].y + drop, b.labels[0].y, "$what: the labels")
            near(shown.shelfTiles[0].y + drop, b.shelfTiles[0].y, "$what: the shelves"); near(shown.pageH + drop, b.pageH, "$what: pageH")
        }
    }

    // A tall narrow window without the pad (UNDER) keeps its 5 squares and takes the 44 dp: the cap 273.2 + 44, Continue 208 → 252.
    @Test fun underTakesTheHeightOnly() {
        val tall = HomeFrame(360f, 423f, false)
        val b = homeBoard(chrome(tall, title = false), 2, tools = 7, games = 4)
        assertEquals(HomeToolMode.UNDER, b.mode); assertEquals(5, b.slots); near(51.2f, b.square, "square")
        near(317.2f, b.topH, "topH"); near(252f, continueOf(b).h, "continue h")
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
