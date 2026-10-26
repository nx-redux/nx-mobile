package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GridLayoutTest {
    @Test fun twoFullRowsStayStillOneMoreSlides() {
        assertEquals(GridMode.STILL, gridModeFor(count = 12, columns = 6))
        assertEquals(GridMode.SLIDING, gridModeFor(count = 13, columns = 6))
        assertEquals(GridMode.STILL, gridModeFor(count = 1, columns = 3))
    }

    // Still, row-major: 0 1 2 / 3 4 (columns 3, count 5)
    @Test fun stillGridStepsOneTileAndWrapsRows() {
        assertEquals(1, gridStep(0, 5, 3, NxButton.RIGHT))
        assertEquals(3, gridStep(2, 5, 3, NxButton.RIGHT))    // on along the row order
        assertNull(gridStep(0, 5, 3, NxButton.LEFT))          // past the first: the caller switches tab
        assertNull(gridStep(4, 5, 3, NxButton.RIGHT))         // past the last
        assertEquals(3, gridStep(0, 5, 3, NxButton.DOWN))
        assertEquals(4, gridStep(2, 5, 3, NxButton.DOWN))     // below is empty: the last tile of the lower row
        assertEquals(1, gridStep(4, 5, 3, NxButton.UP))
        assertEquals(3, gridStep(0, 5, 3, NxButton.UP))       // wraps: top row -> bottom row, same column
        assertEquals(0, gridStep(3, 5, 3, NxButton.DOWN))     // wraps: bottom row -> top row
        assertEquals(4, gridStep(2, 5, 3, NxButton.UP))       // wraps onto an empty spot: the last tile
    }

    // Sliding, column-major, two rows: columns (0,1) (2,3) ... (12) with count 13, columns 6
    @Test fun slidingGridStepsAColumnAndSwitchesRow() {
        assertEquals(2, gridStep(0, 13, 6, NxButton.RIGHT))
        assertEquals(3, gridStep(1, 13, 6, NxButton.RIGHT))
        assertEquals(12, gridStep(11, 13, 6, NxButton.RIGHT)) // into the one-tile last column
        assertEquals(12, gridStep(10, 13, 6, NxButton.RIGHT))
        assertNull(gridStep(12, 13, 6, NxButton.RIGHT))       // past the last column
        assertNull(gridStep(1, 13, 6, NxButton.LEFT))         // past the first column
        assertEquals(12, gridStep(12, 13, 6, NxButton.DOWN))  // a one-tile column: nothing to wrap to
        assertEquals(1, gridStep(0, 13, 6, NxButton.DOWN))
        assertEquals(1, gridStep(0, 13, 6, NxButton.UP))      // wraps within the column
        assertEquals(0, gridStep(1, 13, 6, NxButton.DOWN))
    }

    @Test fun oneOrTwoItems() {
        assertEquals(0, gridStep(0, 1, 3, NxButton.UP))
        assertEquals(0, gridStep(0, 1, 3, NxButton.DOWN))
        assertNull(gridStep(0, 1, 3, NxButton.LEFT))
        assertNull(gridStep(0, 1, 3, NxButton.RIGHT))
        assertEquals(1, gridStep(0, 2, 3, NxButton.RIGHT))
        assertEquals(1, gridStep(1, 2, 3, NxButton.DOWN))     // one row: stays
        assertEquals(0, gridStep(0, 0, 3, NxButton.DOWN))     // empty: stays
    }

    @Test fun geometryMatchesTheMockupScreens() {
        val land = gridGeometry(960f, 432f - 44f - 30f, portrait = false)   // body under the tab row, above the band
        assertEquals(6, land.columns)
        assertEquals(24f, land.gutter)
        val port = gridGeometry(475f, 379f - 44f - 30f, portrait = true)
        assertEquals(3, port.columns)
        assertEquals(475f - 2 * 16f, 3 * port.tileW + 2 * port.gap, 0.01f) // three columns fill the width exactly
        assertTrue(2 * port.tileH + port.gap <= 379f - 44f - 30f)         // two rows fit the height
    }

    @Test fun logoInsetIs22AtTheSidesAnd30TopAndBottomScaledDownWithTheTile() {
        val land = GridGeometry(tileW = 140f, tileH = 154f, gap = 14f, gutter = 24f, columns = 6)
        assertEquals(Pair(22f, 30f), gridLogoInset(land, portrait = false))                    // the spec's tile
        val port = GridGeometry(tileW = 138f, tileH = 128f, gap = 14f, gutter = 16f, columns = 3)
        assertEquals(Pair(22f, 30f), gridLogoInset(port, portrait = true))
        val (side, vertical) = gridLogoInset(GridGeometry(70f, 77f, 14f, 24f, 6), portrait = false)   // half size
        assertEquals(11f, side, 0.001f); assertEquals(15f, vertical, 0.001f)
        val (pSide, pVertical) = gridLogoInset(GridGeometry(69f, 64f, 14f, 16f, 3), portrait = true)
        assertEquals(11f, pSide, 0.001f); assertEquals(15f, pVertical, 0.001f)
        assertEquals(Pair(22f, 30f), gridLogoInset(GridGeometry(160f, 140f, 14f, 16f, 3), portrait = true))   // wider: not grown
    }

    @Test fun shortHeightShrinksTilesKeepingLandscapeAspect() {
        val g = gridGeometry(960f, 200f, portrait = false)
        assertEquals((200f - 14f) / 2, g.tileH, 0.01f)
        assertEquals(140f / 154f, g.tileW / g.tileH, 0.001f)
    }

    @Test fun slideAnchorsTheSelectionAndNeverPassesTheEnds() {
        val g = gridGeometry(960f, 358f, portrait = false)
        val pitch = g.tileW + g.gap
        assertEquals(g.gutter, gridSlideX(0, 40, g, 960f))                          // start: column 0 on the gutter
        val anchor = g.gutter + 0.55f * g.tileW + g.gap
        assertEquals(anchor - 5 * pitch, gridSlideX(10, 40, g, 960f), 0.01f)        // column 5 anchored
        val content = 20 * pitch - g.gap
        assertEquals(960f - g.gutter - content, gridSlideX(39, 40, g, 960f), 0.01f) // the end: last column on the right gutter
    }

    @Test fun cutColumnsAndEdgeFades() {
        val g = gridGeometry(960f, 358f, portrait = false)
        val x0 = gridSlideX(10, 40, g, 960f)
        assertEquals(Pair(true, true), gridEdges(x0, 40, g, 960f))
        assertEquals(Pair(false, true), gridEdges(gridSlideX(0, 40, g, 960f), 40, g, 960f))
        assertTrue(gridColumnCut(g.gutter - 10f, g, 960f))
        assertFalse(gridColumnCut(g.gutter, g, 960f))
        assertTrue(gridColumnCut(960f - g.gutter - g.tileW + 10f, g, 960f))
    }

    @Test fun stillFrameIsCentred() {
        val g = gridGeometry(960f, 358f, portrait = false)
        val frame = g.columns * (g.tileW + g.gap) - g.gap
        assertEquals((960f - frame) / 2, gridStillX(3, g, 960f), 0.01f)   // a short row still starts in the left column
    }

    // The Fold cover's Grid in portrait: with no info band the body runs from the header down to the hint bar, the
    // 230.5 dp it had above the band plus the band's reserve (one 18.3 dp text line, 2 dp under it, the 12 dp fade).
    @Test fun ringRoomShrinksFittedRowsSoTheRingIsNeverClipped() {
        val body = 230.5f + 18.3f + 2f + 12f
        val g = gridGeometry(475f, gridSizingHeight(body), portrait = true)
        val rowsH = 2 * g.tileH + g.gap
        val top = gridRowsTop(body, g.tileH, g.gap)
        assertTrue(top - NX_TILE_RING >= 1f - 0.01f)                        // the top row's ring clear of the body's top
        assertTrue(body - (top + rowsH) - NX_TILE_RING >= 1f - 0.01f)       // and the bottom row's clear of its bottom
        assertEquals(body - 2 * GRID_RING_ROOM, rowsH, 0.01f)               // the tiles shrank just enough
    }

    @Test fun foldCoverTilesGrowIntoTheBandsSpaceUpToTheCap() {
        val band = 230.5f                            // the body above the band (before)
        val noBand = band + 18.3f + 2f + 12f         // the whole body (now)
        val before = gridGeometry(475f, band - 12f - 2 * GRID_RING_ROOM, portrait = true).tileH   // the old sizing (less the fade)
        val after = gridGeometry(475f, gridSizingHeight(noBand), portrait = true).tileH
        assertEquals(98.25f, before, 0.01f)
        assertEquals(120.4f, after, 0.01f)
        // A taller body (the Flip) still stops at the 128 dp portrait cap.
        assertEquals(128f, gridGeometry(475f, gridSizingHeight(400f), portrait = true).tileH, 0.01f)
        assertEquals(128f, gridGeometry(475f, gridSizingHeight(2 * 128f + 14f + 2 * GRID_RING_ROOM), portrait = true).tileH, 0.01f)
        // Landscape keeps its 154 cap under the same rule.
        assertEquals(154f, gridGeometry(960f, gridSizingHeight(400f), portrait = false).tileH, 0.01f)
    }

    @Test fun rowsAreCentredBetweenTheHeaderAndTheHintBar() {
        // Fitted rows: the same room above the rows as below them.
        val body = 262.8f
        val g = gridGeometry(475f, gridSizingHeight(body), portrait = true)
        val rowsH = 2 * g.tileH + g.gap
        val top = gridRowsTop(body, g.tileH, g.gap)
        assertEquals(top, body - (top + rowsH), 0.01f)
        assertEquals(GRID_RING_ROOM, top, 0.01f)
        // Tall body (tiles at their 128 dp): centred in the body, well clear of both edges.
        assertEquals((400f - 270f) / 2, gridRowsTop(400f, 128f, 14f), 0.01f)
        assertEquals((358f - 270f) / 2, gridRowsTop(358f, 128f, 14f), 0.01f)
        val fit = gridGeometry(960f, gridSizingHeight(300f), portrait = false)
        assertEquals(GRID_RING_ROOM, gridRowsTop(300f, fit.tileH, fit.gap), 0.01f)
    }

    @Test fun rowsTopNeverLeavesTheRingRoom() {
        // Never above the ring room at the top, even when the rows overflow.
        assertEquals(GRID_RING_ROOM, gridRowsTop(80f, 40f, 14f), 0.01f)
    }

    // The main menu's tab row: UP from the top row reaches it; UP on it lands on the bottom row.
    // Still 0 1 2 / 3 4 (columns 3); sliding with 2 rows: columns (0 1) (2 3) (4).
    @Test fun gridTopRowIsTheFirstRowElseTheColumnsTop() {
        assertTrue(gridAtTop(0, 5, 3)); assertTrue(gridAtTop(2, 5, 3))
        assertFalse(gridAtTop(3, 5, 3)); assertFalse(gridAtTop(4, 5, 3))
        assertTrue(gridAtTop(2, 5, 1)); assertFalse(gridAtTop(3, 5, 1))      // sliding: the column's top
        assertTrue(gridAtTop(4, 5, 1))
        assertTrue(gridAtTop(0, 0, 3))                                         // empty: the top
    }

    // The logo as drawn (Fit in its box): its own aspect (height over width) capped at the box; no logo, half the box.
    @Test fun logoDrawnHeightAndTheLineUnderIt() {
        assertEquals(99f, logoDrawnHeight(330f, 130f, 0.3f), 1e-3f)
        assertEquals(130f, logoDrawnHeight(330f, 130f, 1f), 1e-3f)     // capped at the box
        assertEquals(65f, logoDrawnHeight(330f, 130f, null), 1e-3f)    // no logo: the mockup's fallback
        assertEquals(100f + 114.5f + 8f, underLogoTop(100f, 130f, 99f, 8f), 1e-3f)
    }

    @Test fun aStillGridCentresOnlyTheRowsItUses() {
        val g = gridGeometry(960f, gridSizingHeight(344f), portrait = false)        // 6 columns, 2 rows
        assertEquals(1, gridUsedRows(3, GridMode.STILL, g))
        assertEquals(2, gridUsedRows(7, GridMode.STILL, g))
        assertEquals(1, gridUsedRows(0, GridMode.STILL, g))
        assertEquals(2, gridUsedRows(30, GridMode.SLIDING, g))
        assertEquals((344f - g.tileH) / 2, gridRowsTop(344f, g.tileH, g.gap, gridUsedRows(3, GridMode.STILL, g)), 0.01f)
    }

    // The tall portrait's grid: still while its items fit; sliding, the first columns rest on the gutter, a scrolled
    // selection is centred with its neighbours dimmed at the edges, and the last rests on the far gutter.
    @Test fun aTallGridCentresAScrolledColumnAndRestsAtTheEnds() {
        val g = gridFor(360f, 511f, 360f, 423f, portrait = true)
        assertEquals(GridMode.STILL, gridModeFor(6, g.columns, g.rows))
        assertEquals(GridMode.SLIDING, gridModeFor(7, g.columns, g.rows))
        val pitch = g.tileW + 14f
        val count = 15                                                      // 5 columns
        assertEquals(16f, gridSlideX(0, count, g, 360f), 0.01f)             // the first two columns on the gutters
        val c = g.anchorX!!
        assertEquals(c - 2 * pitch, gridSlideX(6, count, g, 360f), 0.01f)    // the third column centred
        assertTrue(gridColumnCut(c - pitch, g, 360f))                        // its neighbours cut and dimmed
        assertTrue(gridColumnCut(c + pitch, g, 360f))
        assertFalse(gridColumnCut(c, g, 360f))
        val content = 5 * pitch - 14f
        assertEquals(360f - 16f - content, gridSlideX(14, count, g, 360f), 0.01f)   // the last on the far gutter
    }

    // Game lists: every screen's tiles take the tall portrait's aspect (182.6 / 116.7), the height by the row rule.
    @Test fun gameListTilesAreWideOnEveryScreen() {
        val e = 0.01f
        val land = gridFor(960f, 432f, 960f, 344f, portrait = false, games = true)
        assertEquals(154f, land.tileH, e); assertEquals(154f * 182.6f / 116.7f, land.tileW, e)
        assertEquals(3, land.columns)                       // floor((960 − 48 + 14) / (240.96 + 14))
        assertEquals(2, land.rows)
        val cover = gridFor(475f, 379f, 475f, 291f, portrait = true, games = true)
        assertEquals(128f, cover.tileH, e); assertEquals(128f * GRID_GAME_TILE_AR, cover.tileW, e)
        assertEquals(2, cover.columns)                      // floor((475 − 32 + 14) / (200.28 + 14))
        // a short body: the height shrinks to fit two rows, the width follows it
        val short = gridFor(840f, 336f, 840f, 248f, portrait = false, games = true)
        assertEquals((248f - 2 * GRID_RING_ROOM - 14f) / 2, short.tileH, e); assertEquals(short.tileH * GRID_GAME_TILE_AR, short.tileW, e)
        assertEquals(4, short.columns)
        // a tablet's rows keep their height rule
        val tab = gridFor(933f, 704f, 933f, 616f, portrait = false, games = true)
        assertEquals(gridFor(933f, 704f, 933f, 616f, portrait = false).tileH, tab.tileH, e)
        assertEquals(tab.tileH * GRID_GAME_TILE_AR, tab.tileW, e)
        assertEquals(gridFor(933f, 704f, 933f, 616f, portrait = false).rows, tab.rows)
    }

    @Test fun theTallPortraitAndTheMainMenuKeepTheirTiles() {
        assertEquals(gridFor(360f, 511f, 360f, 423f, portrait = true), gridFor(360f, 511f, 360f, 423f, portrait = true, games = true))
        assertEquals(140f, gridFor(960f, 432f, 960f, 344f, portrait = false).tileW, 0.01f)
    }

    @Test fun aWideTileNeverOverflowsANarrowBody() {
        val g = gridFor(240f, 432f, 240f, 344f, portrait = false, games = true)
        assertEquals(1, g.columns)
        assertTrue(g.tileW <= 240f - 2 * g.gutter + 0.01f)
        assertEquals(GRID_GAME_TILE_AR, g.tileW / g.tileH, 0.01f)
    }

    // Page title hidden: the Grid's box takes the title's 44 dp room too. The tiles and rows stay those of the
    // shown title (sized from the box less the room); the rows are centred in the whole box, so they move up 22 dp.
    @Test fun aHiddenTitleKeepsTheTilesAndCentresTheRowsInTheTallerBox() {
        val e = 0.01f
        val room = 44f
        assertEquals(291f, gridSizingBody(291f, 0f), e)                     // the title shown: the box itself
        assertEquals(291f, gridSizingBody(291f + room, room), e)
        assertEquals(0f, gridSizingBody(20f, room), e)                      // never negative
        for ((win, body, portrait, games) in listOf(
            Quad(475f to 379f, 291f, true, false), Quad(475f to 379f, 291f, true, true),
            Quad(960f to 432f, 344f, false, false), Quad(960f to 432f, 344f, false, true),
            Quad(360f to 511f, 423f, true, false), Quad(933f to 704f, 616f, false, false),
        )) {
            val shown = gridFor(win.first, win.second, win.first, body, portrait, games)
            val box = body + room
            val hidden = gridFor(win.first, win.second, win.first, gridSizingBody(box, room), portrait, games)
            assertEquals(shown, hidden)                                     // same tiles, columns and rows
            for (used in 1..shown.rows) {
                val rowsH = used * shown.tileH + (used - 1) * shown.gap
                val top = gridRowsTop(box, hidden.tileH, hidden.gap, used)
                val before = room + gridRowsTop(body, shown.tileH, shown.gap, used)   // under the kept 44 dp spacer
                assertEquals(top, box - (top + rowsH), e)                   // the same space above as below, in the whole box
                assertEquals(before - room / 2, top, e)                     // half the room higher than before
                assertTrue(top >= GRID_RING_ROOM - e)                       // the ring clear of the box's (the clip's) top
            }
        }
    }

    // As the handheld's Consoles grid, the Consoles tab's portrait Grid: two half-width tiles across, the same
    // height. Collections, Tools, game lists, landscape, the tall portrait and a tablet keep their tiles.
    @Test fun consolesPortraitTilesAreHalfTheWidthAtTheSameHeight() {
        val e = 0.01f
        val old = gridFor(475f, 379f, 475f, 291f, portrait = true)
        val cover = gridFor(475f, 379f, 475f, 291f, portrait = true, consoles = true)
        assertEquals((475f - 2 * 16f - 14f) / 2, cover.tileW, e)            // 214.5
        assertEquals(old.tileH, cover.tileH, e)                              // 128 kept
        assertEquals(2, cover.columns)
        assertEquals(old.rows, cover.rows); assertEquals(old.gutter, cover.gutter)
        // Four consoles fit still, two whole columns on the gutters.
        assertEquals(GridMode.STILL, gridModeFor(4, cover.columns, cover.rows))
        assertEquals(16f, gridStillX(4, cover, 475f), e)
        // A hidden title: the same tiles.
        assertEquals(cover, gridFor(475f, 379f, 475f, gridSizingBody(291f + 44f, 44f), portrait = true, consoles = true))
        // Every other kind and place keeps its tile.
        assertFalse(old.wideConsoles)
        assertEquals(gridFor(960f, 432f, 960f, 344f, portrait = false), gridFor(960f, 432f, 960f, 344f, portrait = false, consoles = true))
        assertEquals(gridFor(360f, 511f, 360f, 423f, portrait = true), gridFor(360f, 511f, 360f, 423f, portrait = true, consoles = true))
        assertEquals(gridFor(704f, 933f, 704f, 845f, portrait = true), gridFor(704f, 933f, 704f, 845f, portrait = true, consoles = true))
    }

    @Test fun consolesPortraitStartsOnTheGuttersThenCentresTheSelection() {
        val e = 0.01f
        for (w in listOf(240f, 320f, 360f, 412f, 475f, 540f, 590f)) {
            val g = gridFor(w, 379f, w, 291f, portrait = true, consoles = true)
            val pitch = g.tileW + g.gap
            val n = 12                                                       // six columns
            // The first column: the first two whole, on the gutters.
            assertEquals(16f, gridSlideX(0, n, g, w), e)
            assertEquals(w - 16f, gridSlideX(0, n, g, w) + pitch + g.tileW, e)
            // From the second to the second-last: the selected column centred, about half of each neighbour showing.
            for (col in 1..4) {
                val x = gridSlideX(col * 2, n, g, w) + col * pitch
                assertEquals((w - g.tileW) / 2, x, e)
                val peek = x - g.gap
                assertTrue(peek > 0.45f * g.tileW && peek < 0.7f * g.tileW, "peek $peek of ${g.tileW} at $w")
            }
            // The last column on the far gutter.
            assertEquals(w - 16f, gridSlideX(n - 1, n, g, w) + 5 * pitch + g.tileW, e)
            // Every selection's column is drawn whole.
            for (sel in 0 until n) assertFalse(gridColumnCut(gridSlideX(sel, n, g, w) + sel / 2 * pitch, g, w))
        }
    }

    @Test fun consolesWideTileLogoKeepsTheSpecsShareOfTheWidth() {
        val cover = gridFor(475f, 379f, 475f, 291f, portrait = true, consoles = true)
        val (side, vertical) = gridLogoInset(cover, portrait = true)
        assertEquals(22f * cover.tileW / 138f, side, 0.01f)                  // about 44 dp: the logo box keeps 68% of the tile
        assertEquals(gridLogoInset(gridFor(475f, 379f, 475f, 291f, portrait = true), portrait = true).second, vertical, 0.01f)
    }

    private data class Quad(val win: Pair<Float, Float>, val body: Float, val portrait: Boolean, val games: Boolean)
}
