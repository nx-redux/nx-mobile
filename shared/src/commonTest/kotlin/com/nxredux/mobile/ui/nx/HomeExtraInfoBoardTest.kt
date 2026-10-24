package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home with Layouts → Extra info hidden ([HomeFrame.statsHidden]). The stats strip is not drawn (the
 * board's lines 0, the top section from the 8 dp margin) and its room goes to the top section as the hidden tab row's
 * does: Continue grows, the caps rise by it so the pins start where they did, and the squares grow (to each face's cap),
 * every column keeping its 5.
 */
class HomeExtraInfoBoardTest {
    private fun near(e: Float, a: Float, what: String) = assertEquals(e, a, 0.05f, what)
    private fun tiles(b: HomeBoard, k: HomeTopKind) = b.top.filter { it.kind == k }.map { it.rect }
    private fun continueOf(b: HomeBoard) = tiles(b, HomeTopKind.CONTINUE).single()
    private fun hide(f: HomeFrame) = f.copy(statsHidden = true)

    private val cover = HomeFrame(475f, 291f, true)

    @Test fun theFreedRoomIsTheStripLinesRoom() {
        near(40f, homeStatsRoom(hide(cover), 2), "two portrait lines: 2 + 2 · 18 + 2")
        near(20f, homeStatsRoom(hide(cover), 1), "one line: 2 + 18")
        near(0f, homeStatsRoom(hide(cover), 0), "no strip, no room")
        near(0f, homeStatsRoom(cover, 2), "shown: nothing freed")
        assertEquals(0, homeStripLinesShown(hide(cover), 2)); assertEquals(2, homeStripLinesShown(cover, 2))
    }

    // The Fold cover (475 × 291, two strip lines): shown, topY 48, the top 235 (the cap), 5 squares of (235 − 24) / 5 =
    // 42.2, Continue 443 − 42.2 − 12 = 388.8 wide; hidden, topY 8, the top 275 (the cap 235 + 40), still 5 squares, of
    // (275 − 24) / 5 = 50.2, Continue 380.8 × 275. The pins start at 295 either way.
    @Test fun foldCoverContinueAndSquaresGrowThePinsStay() {
        for (tools in listOf(0, 3, 5, 8, 12)) {
            val shown = homeBoard(cover, 2, tools = tools, games = 4)
            val b = homeBoard(hide(cover), 2, tools = tools, games = 4)
            val what = "tools $tools"
            assertEquals(HomeToolMode.COLUMN, b.mode, what)
            assertEquals(0, b.stripLines, what); near(8f, b.topY, "$what: topY")
            near(48f, shown.topY, "$what: shown topY"); near(235f, shown.topH, "$what: shown topH")
            near(275f, b.topH, "$what: topH"); near(275f, continueOf(b).h, "$what: continue h")
            assertEquals(shown.slots, b.slots, "$what: no row gained")
            near(shown.tilesY, b.tilesY, "$what: the pins start where they did"); near(295f, b.tilesY, "$what: tilesY")
            near(shown.pinH, b.pinH, "$what: pinH")
            if (tools > 0) {
                near(42.2f, shown.square, "$what: shown square"); near(50.2f, b.square, "$what: square")
                val cols = if (tools > 5) 2 else 1
                near(443f - cols * 50.2f - (cols - 1) * 12f - 12f, continueOf(b).w, "$what: continue w")
                for ((i, r) in b.top.filter { it.kind != HomeTopKind.CONTINUE }.withIndex())
                    near(8f + (i % 5) * (50.2f + 6f), r.rect.y, "$what: square $i y")
            } else near(443f, continueOf(b).w, "$what: continue the full width")
        }
    }

    // The cover with the tab row hidden too (its 44 dp in the body, still 5 squares a column): shown, the top 279
    // ((279 − 24) / 5 = 51); hidden, 319 (the cap 279 + 40): (319 − 24) / 5 = 59.
    @Test fun foldCoverWithTheTabRowHiddenToo() {
        val f = cover.copy(h = 335f, titleHidden = true)
        val shown = homeBoard(f, 2, tools = 3, games = 4)
        val b = homeBoard(hide(f), 2, tools = 3, games = 4)
        near(279f, shown.topH, "shown topH"); near(51f, shown.square, "shown square")
        near(8f, b.topY, "topY"); near(319f, b.topH, "topH"); near(59f, b.square, "square")
        assertEquals(5, b.slots); near(shown.tilesY, b.tilesY, "the pins start where they did")
    }

    // A taller portrait body (the Flip's inner display) stops its top section at the cap, raised by the freed room:
    // 235 + 40 = 275 from 8, so its pins start at 295 as with the strip shown (48 + 235 + 12).
    @Test fun tallPortraitCapRisesByTheFreedRoom() {
        val f = HomeFrame(360f, 640f, true)
        val shown = homeBoard(f, 2, tools = 7, games = 6)
        val b = homeBoard(hide(f), 2, tools = 7, games = 6)
        near(235f, shown.topH, "shown topH"); near(275f, b.topH, "topH")
        near(shown.tilesY, b.tilesY, "the pins start below the cap where they did")
        near(shown.pinH, b.pinH, "pinH")
    }

    // A short portrait body (230 tall): shown, the top 174 and squares of (174 − 24) / 5 = 30; hidden, the column keeps
    // its 5 and they grow: (214 − 24) / 5 = 38 (10 slots past 5 tools).
    @Test fun aShortPortraitKeepsFiveAColumnAndGrowsThem() {
        val f = HomeFrame(475f, 230f, true)
        val shown = homeBoard(f, 2, tools = 8, games = 2)
        val b = homeBoard(hide(f), 2, tools = 8, games = 2)
        near(174f, shown.topH, "shown topH"); near(30f, shown.square, "shown square"); assertEquals(10, shown.slots)
        near(214f, b.topH, "topH"); near(38f, b.square, "square"); assertEquals(10, b.slots)
        assertTrue(b.square >= shown.square, "the freed room only grows the squares")
        assertEquals(5, homeBoard(hide(f), 2, tools = 4, games = 2).slots)
    }

    // Landscape SPS3 (800 × 340, one strip line): the top section 20 dp taller from 8 (28 with the strip), still 5
    // squares, of the whole body 324: (324 − 4 · 7) / 5 = 59.2 (304 and 55.2 with it), the pin row where it is with the
    // strip (8 + 324 + 14 = 28 + 304 + 14 = 346).
    @Test fun landscapeSps3() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val shown = homeBoard(f, 1, tools = 3, games = 4)
        val b = homeBoard(hide(f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(5, b.slots); assertEquals(0, b.stripLines)
        near(28f, shown.topY, "shown topY"); near(8f, b.topY, "topY")
        near(shown.topH + 20f, b.topH, "topH"); near(55.2f, shown.square, "shown square"); near(59.2f, b.square, "square")
        near(shown.tilesY, b.tilesY, "tilesY"); near(shown.pinH, b.pinH, "pinH")
        assertTrue(continueOf(b).h > continueOf(shown).h, "Continue grows")
    }

    // UNDER (a tall narrow window): its 5 squares stay; Continue grows by the 40 dp (the cap rises with it).
    @Test fun underKeepsItsFiveAndContinueGrows() {
        val f = HomeFrame(360f, 423f, false)
        val shown = homeBoard(f, 2, tools = 5, games = 4)
        val b = homeBoard(hide(f), 2, tools = 5, games = 4)
        assertEquals(HomeToolMode.UNDER, b.mode); assertEquals(5, b.slots)
        near(shown.square, b.square, "square"); near(continueOf(shown).h + 40f, continueOf(b).h, "continue h")
        near(shown.tilesY, b.tilesY, "tilesY")
    }

    // The wide face (960 × 344, one line): the top (the body) 20 dp taller from 8 (308 → 328), still 5 squares a column,
    // grown from (308 − 4 · 7) / 5 = 56 to min(58, (328 − 28) / 5 = 60) = 58; the pins where they are with the strip.
    // 7 tools are 2 columns, so the room left of them shrinks by 2 · 2 dp, and Continue (0.6 of it) by 2.4.
    @Test fun wideFace() {
        val f = HomeFrame(960f, 344f, false, 960f / 432f)
        val shown = homeBoard(f, 1, tools = 7, games = 6)
        val b = homeBoard(hide(f), 1, tools = 7, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(15, b.slots); assertEquals(0, b.more)
        near(8f, b.topY, "topY"); near(shown.topH + 20f, b.topH, "topH"); near(56f, shown.square, "shown square"); near(58f, b.square, "square")
        near(shown.tilesY, b.tilesY, "tilesY"); near(continueOf(shown).w - 0.6f * 2 * 2f, continueOf(b).w, "continue w")
        // With the tab row hidden too: still 15 slots; the top (388 − 28 − 8 = 352) 20 dp taller still, squares at the
        // 58 cap.
        val t = f.copy(h = 388f, titleHidden = true)
        val tb = homeBoard(hide(t), 1, tools = 7, games = 6)
        assertEquals(15, tb.slots); near(homeBoard(t, 1, tools = 7, games = 6).topH + 20f, tb.topH, "title hidden topH")
        near(58f, tb.square, "title hidden square")
    }

    // A very short wide body (960 × 60, no games): shown, the top 24, too short for 5 rows and their 4 half gaps (28), so
    // the squares floor at 0; hidden, the top 44 and still 5 rows, of (44 − 28) / 5 = 3.2: 15 slots either way.
    @Test fun aShortWideFaceKeepsFiveAColumn() {
        val f = HomeFrame(960f, 60f, false, 960f / 148f)
        val shown = homeBoard(f, 1, tools = 7, games = 0)
        val b = homeBoard(hide(f), 1, tools = 7, games = 0)
        assertEquals(15, shown.slots); near(0f, shown.square, "shown square")
        assertEquals(15, b.slots); assertEquals(0, b.more); near(3.2f, b.square, "square"); near(44f, b.topH, "topH")
    }

    // The large face (933 × 616, one line): the top 274 + 20 from 8, still 5 squares a column, of min(66.75,
    // (294 − 4 · 7) / 5) = 53.2, the bands (Pinned, the shelves) where they are with the strip. With the tab row hidden
    // too: the top 318 + 20, squares (338 − 28) / 5 = 62.
    @Test fun largeFace() {
        val f = HomeFrame(933f, 616f, false, 933f / 704f)
        val shelves = listOf(HomeShelf.NEW to 6, HomeShelf.NOT_STARTED to 3)
        val shown = homeBoard(f, 1, tools = 5, games = 6, shelves = shelves)
        val b = homeBoard(hide(f), 1, tools = 5, games = 6, shelves = shelves)
        assertEquals(HomeToolMode.LARGE, b.mode); assertEquals(10, b.slots); assertEquals(0, b.stripLines)
        near(274f, shown.topH, "shown topH"); near(8f, b.topY, "topY"); near(294f, b.topH, "topH"); near(294f, continueOf(b).h, "continue h")
        near(53.2f, b.square, "square")
        assertEquals(shown.labels, b.labels); assertEquals(shown.shelfTiles, b.shelfTiles); near(shown.tilesY, b.tilesY, "tilesY")
        val t = f.copy(h = 660f, titleHidden = true)
        val tb = homeBoard(hide(t), 1, tools = 5, games = 6, shelves = shelves)
        assertEquals(10, tb.slots); near(338f, tb.topH, "title hidden topH"); near(62f, tb.square, "title hidden square")
    }

    // A face without a strip (nothing on Home: the Pick card, 0 lines) frees nothing: the board is the shown one's.
    @Test fun noStripFreesNothing() {
        for (f in listOf(cover, HomeFrame(960f, 344f, false, 960f / 432f), HomeFrame(933f, 616f, false, 933f / 704f))) {
            assertEquals(homeBoard(f, 0, tools = 0, games = 0), homeBoard(hide(f), 0, tools = 0, games = 0), "$f")
        }
    }
}
