package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home with Layouts → Extra info hidden ([HomeFrame.statsHidden]). The stats strip is not drawn (the
 * board's lines 0, the top section from the 8 dp margin) and its room goes to the top section as the hidden tab row's
 * does: Continue grows, the caps rise by it so the pins start where they did, and a landscape face's row of tool tiles
 * stays one row a gap under Continue (the tiles keep the Tools page's size). On the portrait type Continue already at
 * its floor for the tool rows stays there: the rows and the pins move up by the freed room.
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

    // Landscape SPS3 (800 × 340, one strip line): Continue is the whole body, 20 dp taller from 8 (28 with the strip):
    // 324 (304 with it). The same row of 8 slots (83.5 × 109.7 tiles) a gap under it, where it is with the strip
    // (8 + 324 + 14 = 28 + 304 + 14 = 346), and the pin row under that likewise (346 + 109.7 + 14 = 469.7).
    @Test fun landscapeSps3() {
        val f = HomeFrame(800f, 340f, false, 800f / 428f)
        val shown = homeBoard(f, 1, tools = 3, games = 4)
        val b = homeBoard(hide(f), 1, tools = 3, games = 4)
        assertEquals(HomeToolMode.SPS3, b.mode); assertEquals(8, b.slots); assertEquals(0, b.stripLines)
        near(28f, shown.topY, "shown topY"); near(8f, b.topY, "topY")
        near(shown.topH + 20f, b.topH, "topH"); near(109.7f, shown.toolH, "shown toolH"); near(109.7f, b.toolH, "toolH")
        near(469.7f, shown.tilesY, "shown tilesY"); near(shown.tilesY, b.tilesY, "tilesY"); near(shown.pinH, b.pinH, "pinH")
        near(304f, continueOf(shown).h, "shown continue h"); near(324f, continueOf(b).h, "Continue grows")
        near(346f, tiles(b, HomeTopKind.TOOL).first().y, "the row"); near(tiles(shown, HomeTopKind.TOOL).first().y, tiles(b, HomeTopKind.TOOL).first().y, "the row where it is with the strip")
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

    // The wide face (960 × 344, one line): Continue (the body) 20 dp taller from 8 (308 → 328), keeping its width; the
    // same row of 10 slots (81.2 × 108.32 tiles) a gap under it, so 7 tools all show; the row and the pins where they
    // are with the strip. With the tab row hidden too Continue is 388 − 8 − 8 = 372, still over the one row.
    @Test fun wideFace() {
        val f = HomeFrame(960f, 344f, false, 960f / 432f)
        val shown = homeBoard(f, 1, tools = 7, games = 6)
        val b = homeBoard(hide(f), 1, tools = 7, games = 6)
        assertEquals(HomeToolMode.WIDE, b.mode); assertEquals(10, b.slots); assertEquals(0, b.more)
        near(8f, b.topY, "topY"); near(shown.topH + 20f, b.topH, "topH"); near(shown.toolH, b.toolH, "toolH")
        near(shown.tilesY, b.tilesY, "tilesY"); near(continueOf(shown).w, continueOf(b).w, "continue w")
        near(308f, continueOf(shown).h, "shown continue h"); near(328f, continueOf(b).h, "continue h")
        near(tiles(shown, HomeTopKind.TOOL).first().y, tiles(b, HomeTopKind.TOOL).first().y, "the row where it is with the strip")
        val t = f.copy(h = 388f, titleHidden = true)
        val tb = homeBoard(hide(t), 1, tools = 7, games = 6)
        assertEquals(10, tb.slots); assertEquals(0, tb.more); near(homeBoard(t, 1, tools = 7, games = 6).topH + 20f, tb.topH, "title hidden topH")
        assertEquals(1, tiles(tb, HomeTopKind.TOOL).map { it.y }.distinct().size, "title hidden: one row")
        near(372f, continueOf(tb).h, "title hidden: continue h"); near(8f + 372f + 14f, tiles(tb, HomeTopKind.TOOL).first().y, "title hidden: the row")
    }

    // A very short wide body (960 × 60, no games): Continue keeps the body, 24 shown and 44 hidden. The row's tiles keep
    // the Tools page's size (10 columns of 81.2, 108.32 tall) a gap under it, at 28 + 24 + 14 = 8 + 44 + 14 = 66, a
    // scroll away; the top section 24 + 14 + 108.32 = 146.32 shown, 166.32 hidden.
    @Test fun aShortWideFaceKeepsContinueTheBodyAndTheRowUnderIt() {
        val f = HomeFrame(960f, 60f, false, 960f / 148f)
        val shown = homeBoard(f, 1, tools = 7, games = 0)
        val b = homeBoard(hide(f), 1, tools = 7, games = 0)
        near(24f, continueOf(shown).h, "shown continue h"); near(44f, continueOf(b).h, "continue h")
        near(146.32f, shown.topH, "shown topH"); near(166.32f, b.topH, "topH")
        for ((what, board) in listOf("shown" to shown, "hidden" to b)) {
            assertEquals(10, board.slots, what); assertEquals(0, board.more, what)
            near(81.2f, board.toolW, "$what: toolW"); near(108.32f, board.toolH, "$what: toolH")
            tiles(board, HomeTopKind.TOOL).forEach { near(66f, it.y, "$what: the row a gap under Continue") }
        }
    }

    // The large face (933 × 616, one line): Continue 264.67 + 20 from 8, the same row of 9 slots (88.11 × 112.47 tiles)
    // a gap under it, so 5 tools all show; the top section 284.67 + 14 + 112.47 = 411.13 (391.13 shown), and the bands
    // (Pinned, the shelves) where they are with the strip. With the tab row hidden too: Continue 308.67 + 20, the top
    // section 455.13, still the one row.
    @Test fun largeFace() {
        val f = HomeFrame(933f, 616f, false, 933f / 704f)
        val shelves = listOf(HomeShelf.NEW to 6, HomeShelf.NOT_STARTED to 3)
        val shown = homeBoard(f, 1, tools = 5, games = 6, shelves = shelves)
        val b = homeBoard(hide(f), 1, tools = 5, games = 6, shelves = shelves)
        assertEquals(HomeToolMode.LARGE, b.mode); assertEquals(9, b.slots); assertEquals(0, b.more); assertEquals(0, b.stripLines)
        near(391.13f, shown.topH, "shown topH"); near(264.67f, continueOf(shown).h, "shown continue h")
        near(8f, b.topY, "topY"); near(411.13f, b.topH, "topH"); near(284.67f, continueOf(b).h, "continue h")
        near(112.47f, b.toolH, "toolH")
        assertEquals(shown.shelfTiles, b.shelfTiles); near(shown.tilesY, b.tilesY, "tilesY")
        val t = f.copy(h = 660f, titleHidden = true)
        val tb = homeBoard(hide(t), 1, tools = 5, games = 6, shelves = shelves)
        assertEquals(9, tb.slots); near(455.13f, tb.topH, "title hidden topH"); near(328.67f, continueOf(tb).h, "title hidden continue h"); near(112.47f, tb.toolH, "title hidden toolH")
        assertEquals(1, tiles(tb, HomeTopKind.TOOL).map { it.y }.distinct().size, "title hidden: one row")
    }

    // A face without a strip (nothing on Home: the Pick card, 0 lines) frees nothing: the board is the shown one's.
    @Test fun noStripFreesNothing() {
        for (f in listOf(cover, HomeFrame(960f, 344f, false, 960f / 432f), HomeFrame(933f, 616f, false, 933f / 704f))) {
            assertEquals(homeBoard(f, 0, tools = 0, games = 0), homeBoard(hide(f), 0, tools = 0, games = 0), "$f")
        }
    }
}
