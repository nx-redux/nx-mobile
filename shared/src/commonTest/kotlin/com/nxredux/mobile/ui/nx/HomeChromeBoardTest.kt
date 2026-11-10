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

    // SPS3 (a 16:9 phone in landscape, 800 × 340): Continue takes the 44 dp, the whole body 384 − 28 − 8 = 348 (304
    // with the tab row). The tools stay one row of 8 slots (83.5 × 109.7 tiles) a gap under it, at 28 + 348 + 14 = 390,
    // 7 tools all showing; the top section 348 + 14 + 109.7 = 471.7. The pin row keeps its shape (109, from the
    // bars-shown height).
    @Test fun sps3ContinueTakesTheTabRowsRoom() {
        val land = HomeFrame(800f, 340f, false, 800f / 428f)
        val shown = homeBoard(land, 1, tools = 7, games = 4)
        assertEquals(HomeToolMode.SPS3, shown.mode); assertEquals(8, shown.slots); assertEquals(0, shown.more)
        near(304f, continueOf(shown).h, "shown: continue h")
        val b = homeBoard(chrome(land, title = false), 1, tools = 7, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(8, b.slots); assertEquals(0, b.more)
        near(83.5f, b.toolW, "toolW"); near(109.7f, b.toolH, "toolH: the same tile"); near(471.7f, b.topH, "topH")
        near(109f, b.pinH, "pinH"); near(348f, continueOf(b).h, "continue h: the whole body")
        val sq = tiles(b, HomeTopKind.TOOL)
        assertEquals(7, sq.size)
        sq.forEachIndexed { i, r -> near(38f + i * 91.5f, r.x, "tile $i x"); near(390f, r.y, "tile $i y: the one row") }
    }

    // The Flip's wide face (804 × 261, 0–2 games): Continue and the games the body, 225 → 269. One row of 8 slots
    // (84 × 110 tiles) a gap under them either way: 12 tools show 7 and "+5"; the top section the body and 14 + 110.
    @Test fun flipWideWithAndWithoutTheTabRow() {
        val flip = HomeFrame(804f, 261f, false, 804f / 330f)
        for ((title, body) in listOf(true to 225f, false to 269f)) {
            val f = chrome(flip, title)
            val what = "title $title"
            val b = homeBoard(f, 1, tools = 12, games = 2)
            assertEquals(HomeToolMode.WIDE, b.mode, what); near(body + 14f + 110f, b.topH, "$what: topH"); near(110f, b.toolH, "$what: toolH")
            assertEquals(8, b.slots, "$what: slots"); assertEquals(5, b.more, "$what: more")
            near(body, continueOf(b).h, "$what: continue h")
            val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
            assertEquals(8, sq.size, what)
            for ((i, r) in sq.withIndex()) near(28f + body + 14f, r.y, "$what: tile $i y")
            val g = tiles(b, HomeTopKind.GAME)
            near(28f, g[0].y, "$what: game 0 y"); near(28f + body, g[1].y + g[1].h, "$what: the games end with Continue")
            near(g[0].h, g[1].h, "$what: the games alike")
        }
        // Past 2 games the rest are row games under the tools' row: the top section is still the body and the row
        // (349 → 393).
        val rowsShown = homeBoard(flip, 1, tools = 6, games = 4)
        val rowsHidden = homeBoard(chrome(flip, title = false), 1, tools = 6, games = 4)
        near(349f, rowsShown.topH, "row games, shown"); near(393f, rowsHidden.topH, "row games, hidden")
        near(rowsShown.toolH, rowsHidden.toolH, "row games: the same tile"); near(rowsShown.pinH, rowsHidden.pinH, "the pin row keeps its shape")
    }

    // The unfolded Fold (933 × 572, the large face): Continue 2 · 128.83 + 7 = 264.67 → 308.67. One row of 9 slots
    // (88.11 × 112.47 tiles) a gap under it either way, 9 tools all showing; the top section Continue and
    // 14 + 112.47 (391.13 → 435.13); the bands below move down by the 44 dp.
    @Test fun foldUnfoldedWithAndWithoutTheTabRow() {
        val fold = HomeFrame(933f, 572f, false, 933f / 660f)
        val shown = homeBoard(fold, 1, tools = 9, games = 6, shelves = listOf(HomeShelf.NEW to 6))
        for ((title, contH) in listOf(true to 264.67f, false to 308.67f)) {
            val f = chrome(fold, title)
            val what = "title $title"
            val b = homeBoard(f, 1, tools = 9, games = 6, shelves = listOf(HomeShelf.NEW to 6))
            val topH = contH + 14f + 112.47f
            assertEquals(HomeToolMode.LARGE, b.mode, what); near(topH, b.topH, "$what: topH"); near(112.47f, b.toolH, "$what: toolH")
            assertEquals(9, b.slots, "$what: slots"); assertEquals(0, b.more, "$what: more")
            near(contH, continueOf(b).h, "$what: continue h")
            val sq = b.top.filter { it.kind == HomeTopKind.TOOL || it.kind == HomeTopKind.MORE }.map { it.rect }
            assertEquals(9, sq.size, what)
            for ((i, r) in sq.withIndex()) near(28f + contH + 14f, r.y, "$what: tile $i y")
            assertTrue(sq.all { it.y + it.h <= 28f + topH + 0.05f }, "$what: the tiles inside the top")
            val drop = contH - 264.67f
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
