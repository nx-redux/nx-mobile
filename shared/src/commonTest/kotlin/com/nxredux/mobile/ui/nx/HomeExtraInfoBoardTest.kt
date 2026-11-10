package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home with Layouts → Extra info hidden ([HomeFrame.statsHidden]). The stats strip is not drawn (the
 * board's lines 0, the top section from the 8 dp margin) and its room goes to the top section as the hidden tab row's
 * does: Continue grows, the caps rise by it so the pins start where they did, and a column of tool tiles beside
 * Continue may fit another row (the tiles keep the Tools page's size). On the portrait type Continue already at its
 * floor for the tool rows stays there: the rows and the pins move up by the freed room.
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

    // The Fold cover (475 × 291, two strip lines): shown, topY 48, the top 235 (the cap); hidden, topY 8, the top 275
    // (the cap 235 + 40). Without tools Continue is the top, 415 × 275, and the pins start at 295 either way. With tools
    // (rows of 4 tiles, 118.25 tall) Continue is at its 145 floor either way (275 − 12 − 118.25 = 144.75 for one row), so
    // the top section is Continue, a gap and the rows, and the rows and pins sit 40 dp higher hidden.
    @Test fun foldCoverContinueGrowsAndTheToolRowsFollow() {
        for (tools in listOf(0, 3, 5, 8, 12)) {
            val shown = homeBoard(cover, 2, tools = tools, games = 4)
            val b = homeBoard(hide(cover), 2, tools = tools, games = 4)
            val what = "tools $tools"
            assertEquals(HomeToolMode.COLUMN, b.mode, what)
            assertEquals(0, b.stripLines, what); near(8f, b.topY, "$what: topY"); near(48f, shown.topY, "$what: shown topY")
            assertEquals(shown.slots, b.slots, "$what: the same slots"); near(shown.pinH, b.pinH, "$what: pinH")
            near(415f, continueOf(b).w, "$what: continue the full width")
            if (tools == 0) {
                near(235f, shown.topH, "$what: shown topH"); near(275f, b.topH, "$what: topH"); near(275f, continueOf(b).h, "$what: continue h")
                near(shown.tilesY, b.tilesY, "$what: the pins start where they did"); near(295f, b.tilesY, "$what: tilesY")
            } else {
                val rows = if (tools <= 4) 1 else 2
                val topH = 145f + 12f + rows * 118.25f + (rows - 1) * 8f
                near(topH, shown.topH, "$what: shown topH"); near(topH, b.topH, "$what: topH"); near(145f, continueOf(b).h, "$what: continue h")
                near(shown.tilesY - 40f, b.tilesY, "$what: the pins 40 higher")
                near(shown.toolH, b.toolH, "$what: the same tile")
                for ((i, r) in b.top.filter { it.kind != HomeTopKind.CONTINUE }.withIndex())
                    near(8f + 145f + 12f + (i / 4) * (118.25f + 8f), r.rect.y, "$what: tile $i y")
            }
        }
    }

    // The cover with the tab row hidden too (its 44 dp in the body) and 3 tools: shown, the top 279, Continue
    // 279 − 12 − 118.25 = 148.75; hidden, 319 (the cap 279 + 40), Continue 188.75: above the floor both, so the top
    // section is the cap and the pins start where they did (339).
    @Test fun foldCoverWithTheTabRowHiddenToo() {
        val f = cover.copy(h = 335f, titleHidden = true)
        val shown = homeBoard(f, 2, tools = 3, games = 4)
        val b = homeBoard(hide(f), 2, tools = 3, games = 4)
        near(279f, shown.topH, "shown topH"); near(148.75f, continueOf(shown).h, "shown continue h")
        near(8f, b.topY, "topY"); near(319f, b.topH, "topH"); near(188.75f, continueOf(b).h, "continue h")
        assertEquals(8, b.slots); near(339f, b.tilesY, "tilesY"); near(shown.tilesY, b.tilesY, "the pins start where they did")
    }

    // A taller portrait body (the Flip's inner display) stops its top section at the cap, raised by the freed room:
    // 235 + 40 = 275 from 8, so with no tools its pins start at 295 as with the strip shown (48 + 235 + 12). With 7 tools
    // (two rows of 116.4) Continue is at the floor either way: the top 397.8 both.
    @Test fun tallPortraitCapRisesByTheFreedRoom() {
        val f = HomeFrame(360f, 640f, true)
        val shown = homeBoard(f, 2, tools = 0, games = 6)
        val b = homeBoard(hide(f), 2, tools = 0, games = 6)
        near(235f, shown.topH, "shown topH"); near(275f, b.topH, "topH")
        near(shown.tilesY, b.tilesY, "the pins start below the cap where they did")
        near(shown.pinH, b.pinH, "pinH")
        near(397.8f, homeBoard(f, 2, tools = 7, games = 6).topH, "7 tools, shown"); near(397.8f, homeBoard(hide(f), 2, tools = 7, games = 6).topH, "7 tools, hidden")
    }

    // A short portrait body (230 tall): shown, the top 174; hidden, 214. Two tool rows keep Continue at 145 either way
    // (the top 145 + 12 + 244.5 = 401.5, the page scrolls); 8 slots, one row's 4 or two's.
    @Test fun aShortPortraitKeepsContinueAtTheFloor() {
        val f = HomeFrame(475f, 230f, true)
        near(174f, homeBoard(f, 2, tools = 0, games = 2).topH, "no tools, shown"); near(214f, homeBoard(hide(f), 2, tools = 0, games = 2).topH, "no tools, hidden")
        val shown = homeBoard(f, 2, tools = 8, games = 2)
        val b = homeBoard(hide(f), 2, tools = 8, games = 2)
        near(401.5f, shown.topH, "shown topH"); assertEquals(8, shown.slots)
        near(401.5f, b.topH, "topH"); assertEquals(8, b.slots); near(145f, continueOf(b).h, "continue h")
        assertEquals(8, homeBoard(hide(f), 2, tools = 4, games = 2).slots)
    }

    // Landscape SPS3 (800 × 340, one strip line): the top section 20 dp taller from 8 (28 with the strip), the whole body
    // 324 (304 with it): still 2 rows of tiles (floor(332 / 115.6)), 4 slots; the pin row where it is with the strip
    // (8 + 324 + 14 = 28 + 304 + 14 = 346).
    @Test fun landscapeSps3() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val shown = homeBoard(f, 1, tools = 3, games = 4)
        val b = homeBoard(hide(f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(4, b.slots); assertEquals(0, b.stripLines)
        near(28f, shown.topY, "shown topY"); near(8f, b.topY, "topY")
        near(shown.topH + 20f, b.topH, "topH"); near(107.6f, shown.toolH, "shown toolH"); near(107.6f, b.toolH, "toolH")
        near(shown.tilesY, b.tilesY, "tilesY"); near(shown.pinH, b.pinH, "pinH")
        assertTrue(continueOf(b).h > continueOf(shown).h, "Continue grows")
    }

    // UNDER (a tall narrow window): its row of 3 tiles stays; Continue grows by the 40 dp (the cap rises with it).
    @Test fun underKeepsItsRowAndContinueGrows() {
        val f = HomeFrame(360f, 423f, false)
        val shown = homeBoard(f, 2, tools = 5, games = 4)
        val b = homeBoard(hide(f), 2, tools = 5, games = 4)
        assertEquals(HomeToolMode.UNDER, b.mode); assertEquals(3, b.slots)
        near(shown.toolH, b.toolH, "toolH"); near(continueOf(shown).h + 40f, continueOf(b).h, "continue h")
        near(shown.tilesY, b.tilesY, "tilesY")
    }

    // The wide face (960 × 344, one line): the top (the body) 20 dp taller from 8 (308 → 328), still 2 rows of tiles, 6
    // slots; the tiles keep their size, so Continue keeps its width; the pins where they are with the strip. With the tab
    // row hidden too the top (388 − 8 − 8 = 372) fits 3 rows: 9 slots, so 7 tools all show.
    @Test fun wideFace() {
        val f = HomeFrame(960f, 344f, false, 960f / 432f)
        val shown = homeBoard(f, 1, tools = 7, games = 6)
        val b = homeBoard(hide(f), 1, tools = 7, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(6, b.slots); assertEquals(2, b.more)
        near(8f, b.topY, "topY"); near(shown.topH + 20f, b.topH, "topH"); near(shown.toolH, b.toolH, "toolH")
        near(shown.tilesY, b.tilesY, "tilesY"); near(continueOf(shown).w, continueOf(b).w, "continue w")
        val t = f.copy(h = 388f, titleHidden = true)
        val tb = homeBoard(hide(t), 1, tools = 7, games = 6)
        assertEquals(9, tb.slots); assertEquals(0, tb.more); near(homeBoard(t, 1, tools = 7, games = 6).topH + 20f, tb.topH, "title hidden topH")
        assertEquals(3, tiles(tb, HomeTopKind.TOOL).map { it.y }.distinct().size, "title hidden: 3 rows")
    }

    // A very short wide body (960 × 60, no games): shown, the top 24, shorter than a tile, so one row of tiles as tall as
    // the top (the drawn tile's icon gives way); hidden, the top 44, the tiles 44: 3 slots either way.
    @Test fun aShortWideFaceHoldsTheTileToTheTop() {
        val f = HomeFrame(960f, 60f, false, 960f / 148f)
        val shown = homeBoard(f, 1, tools = 7, games = 0)
        val b = homeBoard(hide(f), 1, tools = 7, games = 0)
        assertEquals(3, shown.slots); near(24f, shown.toolH, "shown toolH")
        assertEquals(3, b.slots); assertEquals(5, b.more); near(44f, b.toolH, "toolH"); near(44f, b.topH, "topH")
    }

    // The large face (933 × 616, one line): the top 264.67 + 20 from 8, still 2 rows of tiles, 4 slots, the bands
    // (Pinned, the shelves) where they are with the strip. With the tab row hidden too: the top 308.67 + 20, still 2 rows.
    @Test fun largeFace() {
        val f = HomeFrame(933f, 616f, false, 933f / 704f)
        val shelves = listOf(HomeShelf.NEW to 6, HomeShelf.NOT_STARTED to 3)
        val shown = homeBoard(f, 1, tools = 5, games = 6, shelves = shelves)
        val b = homeBoard(hide(f), 1, tools = 5, games = 6, shelves = shelves)
        assertEquals(HomeToolMode.LARGE, b.mode); assertEquals(4, b.slots); assertEquals(2, b.more); assertEquals(0, b.stripLines)
        near(264.67f, shown.topH, "shown topH"); near(8f, b.topY, "topY"); near(284.67f, b.topH, "topH"); near(284.67f, continueOf(b).h, "continue h")
        near(107.6f, b.toolH, "toolH")
        assertEquals(shown.shelfTiles, b.shelfTiles); near(shown.tilesY, b.tilesY, "tilesY")
        val t = f.copy(h = 660f, titleHidden = true)
        val tb = homeBoard(hide(t), 1, tools = 5, games = 6, shelves = shelves)
        assertEquals(4, tb.slots); near(328.67f, tb.topH, "title hidden topH"); near(107.6f, tb.toolH, "title hidden toolH")
    }

    // A face without a strip (nothing on Home: the Pick card, 0 lines) frees nothing: the board is the shown one's.
    @Test fun noStripFreesNothing() {
        for (f in listOf(cover, HomeFrame(960f, 344f, false, 960f / 432f), HomeFrame(933f, 616f, false, 933f / 704f))) {
            assertEquals(homeBoard(f, 0, tools = 0, games = 0), homeBoard(hide(f), 0, tools = 0, games = 0), "$f")
        }
    }
}
