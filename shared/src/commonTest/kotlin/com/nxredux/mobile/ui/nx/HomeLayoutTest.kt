package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nx_mark
import com.nxredux.mobile.ui.InfoKind
import com.nxredux.mobile.ui.InfoLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeLayoutTest {
    /** The mockup's landscape body (WIDE, the strip on one line): 11 games, the first 2 inline, 9 in 3 rows of 3. */
    private val land = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), stripLines = 1, tools = 3, games = 11)
    /** The Fold cover (COLUMN, two strip lines): Continue and a row of 3 tools, then 10 games in 5 rows of 2, all below the first screen. */
    private val port = homeBoard(HomeFrame(475f, 291f, true), stripLines = 2, tools = 3, games = 10)

    /** The scroll after each move through [rows] (a row's first game; -1 the top section), from the top. */
    private fun walk(b: HomeBoard, h: Float, rows: List<Int>): List<Float> {
        var y = 0f
        return rows.map { r -> y = homeScrollTo(y, if (r < 0) null else homeBoardPin(b, r * b.perRow), b.pageH, h); y }
    }

    private fun assertScrolls(expected: List<Float>, actual: List<Float>, what: String) {
        assertEquals(expected.size, actual.size, what)
        expected.zip(actual).forEachIndexed { i, (e, a) -> assertEquals(e, a, 0.05f, "$what, move $i") }
    }

    // The scroll moves as little as keeps the selected game, ring room included, 5 dp clear of the tab row and the hint
    // bar; the top section scrolls to the top (DOWN through the rows, then UP).
    // Landscape (the wide face: Continue the body 308, tilesY = 28 + 308 + 14 = 350, pinH 124.4, rows 138.4 apart;
    // then 3 tools in 1 row of toolH = 124.4 * 0.58 = 72.152 below them): row 0 474.4 + 8 - 344 = 138.4, row 1 276.8,
    // row 2 415.2, back up row 1 in view, row 0 350 - 8 = 342.
    // Fold cover (topH = 235, tilesY = 295, pinH = 106.59375, rows 118.59375 apart; then 3 tools in 2 rows of toolH = 61.824375 below).
    @Test fun theScrollKeepsTheSelectedGameInView() {
        assertEquals(3, land.rowGames / land.perRow)
        assertScrolls(listOf(138.4f, 276.8f, 415.2f, 415.2f, 342f, 0f), walk(land, 344f, listOf(0, 1, 2, 1, 0, -1)), "landscape")
        val pRow = port.pinH + port.gap
        val d0 = port.tilesY + port.pinH + 8f - 291f
        assertScrolls(listOf(d0, d0 + pRow, d0 + 2 * pRow, d0 + 3 * pRow, d0 + 4 * pRow, d0 + 4 * pRow, d0 + 4 * pRow, port.tilesY + 2 * pRow - 8f, port.tilesY + pRow - 8f, port.tilesY - 8f, 0f),
            walk(port, 291f, listOf(0, 1, 2, 3, 4, 4, 3, 2, 1, 0, -1)), "portrait")
        // Every selected pin and tool fully in the 291 dp body with its ring room 5 dp clear.
        var y = 0f
        for (k in 0 until port.rowGames + port.toolTiles.size) {
            val p = homeBoardTile(port, k)
            y = homeScrollTo(y, p, port.pageH, 291f)
            assertTrue(p.y - (NX_TILE_RING + 5f) >= y - 0.01f && p.y + p.h + NX_TILE_RING + 5f <= y + 291f + 0.01f, "tile $k in view")
        }
    }

    // A drag leaves the page anywhere between the top and the end; the next move scrolls only if the game is not in view.
    // Row 1 (488.4 … 612.8) scrolls to 276.8, as in theScrollKeepsTheSelectedGameInView.
    @Test fun theScrollStartsFromWhereADragLeftIt() {
        val row1 = homeBoardPin(land, 3)
        val lastTool = land.toolTiles.last()
        assertEquals(300f, homeScrollTo(300f, row1, land.pageH, 344f), 0.01f)                 // in view: stays
        assertEquals(276.8f, homeScrollTo(20f, row1, land.pageH, 344f), 0.02f)                // below the view: just enough
        assertEquals(land.pageH - 344f, homeScrollTo(1000f, lastTool, land.pageH, 344f), 0.02f) // past the end (stale): clamped
        // One row game (3 pinned, 2 inline) is a scroll away too: at 350, so 138.4.
        val one = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), stripLines = 1, tools = 3, games = 3)
        assertEquals(138.4f, homeScrollTo(0f, homeBoardPin(one, 0), one.pageH, 344f), 0.02f)
    }

    // A selected top tile that shows whole on the first screen leaves the page at the top: Continue and the inline
    // games end at 28 + 308 = 336, their ring room and 5 dp (8) just inside the 344 dp body. The pinned tools band
    // follows the row games and scrolls into view cleanly as a row tile.
    @Test fun aSelectedToolScrollsIntoView() {
        val top = land.top
        for (t in top) assertEquals(null, homeTopScrollRect(t.rect, 344f), "${t.kind}: on the first screen")
        val tool = land.toolTiles.first()
        val y = homeScrollTo(0f, tool, land.pageH, 344f)
        assertTrue(tool.y - (NX_TILE_RING + 5f) >= y - 0.01f && tool.y + tool.h + NX_TILE_RING + 5f <= y + 344f + 0.01f, "the tool in view")
        assertEquals(0f, homeScrollTo(y, homeTopScrollRect(top[0].rect, 344f), land.pageH, 344f), "back on Continue: the top")
        val under = homeBoard(HomeFrame(360f, 423f, false), 2, tools = 5, games = 4)
        under.top.forEach { assertEquals(null, homeTopScrollRect(it.rect, 423f), "UNDER ${it.kind}") }
    }

    // The scroll across recompositions: a new board snaps (to show the selection from the top), a new selection eases
    // from where the page is, and anything else (a rows swap on the same board: stats, recents) keeps the offset, a
    // drag's included, only clamped into a page that got shorter.
    // Row 1 at 276.8 and row 2 at 415.2, as in theScrollKeepsTheSelectedGameInView.
    @Test fun aRowsSwapKeepsTheScrollAndOnlyASelectionOrBoardChangeMovesIt() {
        val pageH = land.pageH
        val row1 = homeBoardPin(land, 3)
        val row2 = homeBoardPin(land, 6)
        assertEquals(HomeScrollStep(HomeScrollMove.SNAP, 276.8f), homeScrollStep(0f, null, land, null, row1, pageH, 344f).round())   // first layout
        assertEquals(HomeScrollStep(HomeScrollMove.KEEP, 200f), homeScrollStep(200f, land, land, null, null, pageH, 344f))   // Continue selected, rows swapped
        assertEquals(HomeScrollStep(HomeScrollMove.KEEP, 150f), homeScrollStep(150f, land, land, row1, row1, pageH, 344f))   // the same game: kept
        assertEquals(HomeScrollStep(HomeScrollMove.KEEP, 138.4f), homeScrollStep(200f, land, land, row1, row1, 482.4f, 344f).round())   // a shorter page: clamped
        assertEquals(HomeScrollStep(HomeScrollMove.EASE, 415.2f), homeScrollStep(150f, land, land, row1, row2, pageH, 344f).round())   // a new selection
        assertEquals(HomeScrollStep(HomeScrollMove.EASE, 0f), homeScrollStep(150f, land, land, row1, null, pageH, 344f))             // to the top section
        assertEquals(HomeScrollMove.SNAP, homeScrollStep(150f, land, port, row1, row1, pageH, 291f).move)                       // a rotation
        val morePins = homeBoard(HomeFrame(960f, 344f, false, 960f / 432f), stripLines = 1, tools = 3, games = 10)
        assertEquals(HomeScrollMove.SNAP, homeScrollStep(150f, land, morePins, row1, row1, morePins.pageH, 344f).move)        // the pins changed
    }

    private fun HomeScrollStep.round() = copy(to = kotlin.math.round(to * 100f) / 100f)

    @Test fun signedOutDropsAchievementData() {
        val lines = listOf(InfoLine(InfoKind.TIME, "Today - 1m 31s"), InfoLine(InfoKind.ACHIEVEMENTS, "0 of 35"), InfoLine(InfoKind.NEXT, "Next: That Was Easy"))
        assertEquals(lines, homeInfo(lines, signedIn = true))
        assertEquals(listOf(InfoLine(InfoKind.TIME, "Today - 1m 31s")), homeInfo(lines, signedIn = false))
    }

    // Home's captions (Continue, the pinned games) keep only the TIME segment, in place, whatever else there is.
    @Test fun captionsKeepOnlyTheTime() {
        val time = InfoLine(InfoKind.TIME, "Yesterday - 1h 12m")
        val lines = listOf(time, InfoLine(InfoKind.ACHIEVEMENTS, "3 of 35"), InfoLine(InfoKind.NEXT, "Next: That Was Easy"))
        assertEquals(listOf(time), timeOnly(lines))
        assertEquals(emptyList(), timeOnly(listOf(InfoLine(InfoKind.ACHIEVEMENTS, "0 of 35"))))   // no time: no caption line
        assertEquals(emptyList(), timeOnly(emptyList()))
    }

    // Wide when w / menu h ≥ 2.15; every other screen split.
    @Test fun theShapeRulesAtEachMockupScreen() {
        assertEquals(listOf(HomeArrangement.SPLIT, HomeArrangement.WIDE), HomeArrangement.entries)
        assertEquals(HomeArrangement.WIDE, homeArrangement(960f, 344f))        // the mockup's landscape, 2.22: wide
        assertEquals(HomeArrangement.SPLIT, homeArrangement(475f, 291f))        // Fold cover
        assertEquals(HomeArrangement.SPLIT, homeArrangement(933f, 616f))       // large
        assertEquals(HomeArrangement.SPLIT, homeArrangement(704f, 845f))       // large portrait
        assertEquals(HomeArrangement.SPLIT, homeArrangement(360f, 423f))        // the Flip's tall portrait
        assertEquals(HomeArrangement.WIDE, homeArrangement(840f, 248f))        // the Flip's landscape 2.50
    }

    // Pick a game's NX mark: the drawn mark (not its 108 viewBox) is 60% of the card's height.
    @Test fun theMarkIsSixtyPercentOfTheCard() {
        for ((h, drawn) in listOf(328f to 196.8f, 275f to 165f, 600f to 360f, 813f to 487.8f, 395f to 237f, 232f to 139.2f)) {
            assertEquals(drawn, homeMarkBox(h) * 44.13f / 108f, 0.01f)
        }
    }

    // The mark is the app's own nx_mark, at 10%.
    @Test fun theMarkIsFaint() {
        assertEquals(com.nxredux.mobile.shared.res.Res.drawable.nx_mark, homePickMark)
        assertEquals(0.10f, HOME_MARK_ALPHA)
    }

    // The real Flip in landscape (dumpsys): a 2520 × 1080 window at 480 dpi, less the 36 dp cutout on the left and the
    // 30 dp status bar: an app area (menu area) of 804 × 330, 2.44:1. Its bars leave Home a body 804 × ~261 (the capture:
    // Continue 245 tall), taller than body + 88 = 330 assumes, so the body alone (804 / 349 = 2.30) read as split.
    // The wide rule goes by the app area itself.
    @Test fun theRealFlipLandscapeIsWide() {
        val flip = 804f / 330f
        assertEquals(HomeArrangement.SPLIT, homeArrangement(804f, 261f, menuRatio = 1.9f))   // the app area decides, not the body
        assertEquals(HomeArrangement.WIDE, homeArrangement(804f, 261f, menuRatio = flip))
        assertTrue(homeIsWide(HomeFrame(804f, 261f, portrait = false, menuRatio = flip)))
        // The wide cutoff is 2.15 on the app area: every 20:9 phone in landscape is wide, 16:9 and the unfolded Fold split.
        for ((what, ratio) in listOf("s23 (880 × 406)" to 880f / 406f, "pixel7, small (914 × 411, 800 × 360)" to 914f / 411f,
            "pixel8pro (997 × 448)" to 997f / 448f, "tall 21:9 (840 × 360)" to 840f / 360f, "the Flip" to 2.44f, "the mockup (960 × 432)" to 960f / 432f)) {
            assertEquals(HomeArrangement.WIDE, homeArrangement(800f, 300f, menuRatio = ratio), what)
        }
        for ((what, ratio) in listOf("old 16:9 (731 × 411 less the bars)" to 1.9f, "the unfolded Fold" to 1.33f, "just under" to 2.14f)) {
            assertEquals(HomeArrangement.SPLIT, homeArrangement(800f, 300f, menuRatio = ratio), what)
        }
        assertEquals(HomeArrangement.WIDE, homeArrangement(700f, 215f))    // 700 / 303 = 2.31
        assertEquals(HomeArrangement.SPLIT, homeArrangement(600f, 200f))   // 600 / 288 = 2.08
    }
}
