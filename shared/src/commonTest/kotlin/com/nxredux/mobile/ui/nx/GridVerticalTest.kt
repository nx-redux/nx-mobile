package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.ui.TOOL_APPS_MIN_TILE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A Grid's columns come from its width and its tiles' least width; its rows fill the box, at least three. */
class GridVerticalTest {
    private val e = 0.01f

    // As many columns as fit at the least width (screenshots 200, logos 136, icons 110 dp, 12 dp apart), stretched to fill.
    @Test fun columnsComeFromTheWidthAndTheTilesLeastWidth() {
        assertEquals(2, gridVertical(475f, 600f, GridTileKind.SCREENSHOT, landscape = false, gutter = 16f).columns)
        assertEquals(3, gridVertical(475f, 600f, GridTileKind.LOGO, landscape = false, gutter = 16f).columns)
        assertEquals(3, gridVertical(475f, 600f, GridTileKind.ICON, landscape = false, gutter = 16f).columns)
        assertEquals(4, gridVertical(711f, 357f, GridTileKind.LOGO, landscape = true, gutter = 24f).columns)
        assertEquals(5, gridVertical(711f, 357f, GridTileKind.ICON, landscape = true, gutter = 24f).columns)
        for (w in listOf(360f, 475f, 960f)) {
            val g = gridVertical(w, 400f, GridTileKind.LOGO, landscape = false, gutter = 16f)
            assertEquals(w - 2 * 16f, g.columns * g.tileW + (g.columns - 1) * g.gap, e)
        }
    }

    // Held sideways, never fewer than three columns: the cells shrink instead.
    @Test fun sidewaysKeepsAtLeastThreeColumns() {
        val g = gridVertical(560f, 300f, GridTileKind.SCREENSHOT, landscape = true, gutter = 24f)   // two would fit at 200
        assertEquals(GRID_MIN_COLUMNS_SIDEWAYS, g.columns)
        assertTrue(g.tileW < GridTileKind.SCREENSHOT.minW)
    }

    @Test fun atLeastThreeRowsAndWholeRowsFillTheBand() {
        assertEquals(GRID_MIN_ROWS, gridVertical(711f, 300f, GridTileKind.ICON, landscape = true, gutter = 24f).rows)
        for (h in listOf(300f, 357f, 600f, 900f)) {
            val g = gridVertical(475f, h, GridTileKind.LOGO, landscape = false, gutter = 16f)
            assertEquals(h - GRID_GAP, g.rows * (g.tileH + g.gap), e)
        }
        assertTrue(gridVertical(475f, 900f, GridTileKind.LOGO, false, 16f).rows > gridVertical(475f, 400f, GridTileKind.LOGO, false, 16f).rows)
    }

    // Upright, a row is the height the same screen gives it held sideways (a 475 × 640 window, the grid 475 × 560 upright:
    // 80 dp of bars, so 640 × 395 sideways), to the nearest whole row.
    @Test fun uprightRowsMatchTheirSidewaysHeight() {
        val side = gridMatched(640f, 395f, 640f, 475f, GridTileKind.LOGO, portrait = false)
        val up = gridMatched(475f, 560f, 475f, 640f, GridTileKind.LOGO, portrait = true)
        assertTrue(kotlin.math.abs(up.tileH + up.gap - (side.tileH + side.gap)) / (side.tileH + side.gap) < 0.15f)
        assertEquals(2, up.columns)                                 // its columns still its own width's (415 between the insets)
        assertEquals(560f - GRID_GAP, up.rows * (up.tileH + up.gap), e)
    }

    // Upright with the on-screen pad under the page (a 475 × 640 window, the grid only 475 × 260): no sideways twin to
    // match, so tiles of their own shape at their width (a fixed height), held to two rows' room.
    @Test fun uprightWithThePadKeepsTilesTheirOwnShape() {
        val g = gridMatched(475f, 260f, 475f, 640f, GridTileKind.SCREENSHOT, portrait = true)
        assertTrue(gridDrawable(475f, 260f, g))
        assertTrue(g.tileH <= g.tileW / GridTileKind.SCREENSHOT.aspect + e)   // never taller than its own shape
        assertEquals(GRID_PAD_MIN_ROWS, g.rows)                     // 2 across, held to 2 rows in 248 (~112 dp each)
        assertEquals(260f - GRID_GAP, g.rows * (g.tileH + g.gap), e)
    }

    // Marks with the pad: two rows, as screenshots, so a small screen's marks aren't squeezed to three; logos
    // and collections' names two across, tools' icons three.
    @Test fun uprightWithThePadShowsTwoRowsOfMarks() {
        for ((kind, across) in listOf(GridTileKind.LOGO to 2, GridTileKind.NAME to 2, GridTileKind.ICON to 3)) {
            val g = gridMatched(475f, 260f, 475f, 640f, kind, portrait = true)
            assertEquals(GRID_PAD_MIN_ROWS, g.rows, "$kind")
            assertEquals(across, g.columns, "$kind")
            assertEquals(475f - 2 * g.gutter, g.columns * g.tileW + (g.columns - 1) * g.gap, e)   // they fill the width
            assertEquals(260f - GRID_GAP, g.rows * (g.tileH + g.gap), e)
        }
    }

    // Two rows' room (the pad case): the selected row centred, half a row either side; the ends rest on the list's ends.
    @Test fun aShortGridCentresTheSelectedRow() {
        assertEquals(0, gridCentredScroll(0, 6, 100, 200))           // the first row rests at the top
        assertEquals(250, gridCentredScroll(3, 6, 100, 200))         // row 3 (300..400) centred: 50 px either side
        assertEquals(400, gridCentredScroll(5, 6, 100, 200))         // the last rests at the bottom
    }

    // The unfolded Fold upright with a DS-style skin: the bottom glass (about 369 x 293 dp) leaves a short wide body.
    @Test fun aSkinsShortWideGlassKeepsToolTilesNearTheirShape() {
        val g = gridMatched(369f, 200f, 369f, 293f, GridTileKind.ICON, portrait = false, insetStart = 20f, skin = true)
        assertEquals(GRID_PAD_MIN_ROWS, g.rows)
        assertTrue(g.tileH >= 75f, "tile ${g.tileH} dp tall")
        // Without a skin the same box keeps three rows (a phone's own landscape is left as it was).
        assertEquals(GRID_MIN_ROWS, gridMatched(369f, 200f, 369f, 293f, GridTileKind.ICON, portrait = false, insetStart = 20f).rows)
    }

    // The unfolded Fold sideways with a skin: the glass (about 553 x 650 dp) is the window, so the matched rows come from
    // the glass turned, not the whole window round it, and tool tiles stay near square.
    @Test fun aSkinsTallGlassMatchesTheGlassTurned() {
        val g = gridMatched(553f, 520f, 553f, 650f, GridTileKind.ICON, portrait = true, insetStart = 20f, skin = true)
        assertTrue(g.rows >= GRID_MIN_ROWS, "${g.rows} rows")
        assertTrue(g.tileH <= g.tileW * 1.15f, "tile ${g.tileW} x ${g.tileH}")
    }

    // Tools: one tile size on every screen; only the columns and the rows in view change.
    @Test fun toolsTilesAreOneSizeOnEveryScreen() {
        val small = gridFixedTools(369f, 200f, 20f, 20f, fontScale = 1f)   // the unfolded Fold's bottom glass, upright
        val big = gridFixedTools(933f, 600f, 30f, 30f, fontScale = 1f)     // the unfolded Fold without a skin
        assertEquals(3, small.columns)
        assertTrue(big.columns > small.columns)
        assertTrue(small.tileW in TOOL_APPS_MIN_TILE..(TOOL_APPS_MIN_TILE * 2), "${small.tileW}")
        assertEquals(toolTileHeight(small.tileW, 1f), small.tileH)
        assertTrue(small.rows >= 1)
        // The icon is capped, so a wider tile is no taller past the cap.
        assertEquals(toolTileHeight(200f, 1f), toolTileHeight(300f, 1f))
    }
}
