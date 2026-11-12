package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.ui.nx.GameTileLayer.CAPTION
import com.nxredux.mobile.ui.nx.GameTileLayer.EDGE
import com.nxredux.mobile.ui.nx.GameTileLayer.FADE
import com.nxredux.mobile.ui.nx.GameTileLayer.PICTURE
import com.nxredux.mobile.ui.nx.GameTileLayer.TITLE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameTileLayersTest {
    // A lit title tile (no art) on Home: the ring, the caption fade for its info line, and the centred name over the fade,
    // in full white (under the fade it would be greyed).
    @Test fun aLitTitleTilesNameIsOverTheFade() {
        val layers = gameTileLayers(picture = false, lit = true, caption = true, homeCaption = true)
        assertEquals(listOf(FADE, TITLE, CAPTION), layers)
        assertTrue(layers.indexOf(TITLE) > layers.indexOf(FADE))
    }

    @Test fun theOtherTiles() {
        assertEquals(listOf(TITLE, EDGE), gameTileLayers(picture = false, lit = false, caption = true, homeCaption = true))
        assertEquals(listOf(PICTURE, EDGE), gameTileLayers(picture = true, lit = false, caption = true, homeCaption = true))
        assertEquals(listOf(PICTURE, FADE, CAPTION), gameTileLayers(picture = true, lit = true, caption = true, homeCaption = true))
        assertEquals(listOf(TITLE, CAPTION), gameTileLayers(picture = false, lit = true, caption = true, homeCaption = false))   // the Grid: no fade on a title tile
        assertEquals(listOf(TITLE), gameTileLayers(picture = false, lit = true, caption = false, homeCaption = false))
    }

    // A game list's Grid: only the lit tile with art has the bottom shade under its caption (it fades in and out with
    // the lit face); an unlit tile shows its art bare; a title tile none.
    @Test fun aGameListTileHasItsShadeOnlyWhenLit() {
        assertEquals(listOf(PICTURE, EDGE), gameTileLayers(picture = true, lit = false, caption = true, homeCaption = false, gameFade = true))
        assertEquals(listOf(PICTURE, FADE, CAPTION), gameTileLayers(picture = true, lit = true, caption = true, homeCaption = false, gameFade = true))
        assertEquals(listOf(TITLE, EDGE), gameTileLayers(picture = false, lit = false, caption = true, homeCaption = false, gameFade = true))
        assertEquals(listOf(TITLE, CAPTION), gameTileLayers(picture = false, lit = true, caption = true, homeCaption = false, gameFade = true))
    }

    // The game list Grid tile's shade: 80% black solid from the bottom edge up to the title's bottom
    // ([solid] dp), then a smoothstep to clear at 65% of the tile (35% from its top).
    @Test fun theGameShadeIsSolidUnderTheTitleThenEases() {
        assertEquals(0.8f, gameFadeAlpha(0f, solid = 30f, reach = 100f), 1e-4f)
        assertEquals(0.8f, gameFadeAlpha(30f, solid = 30f, reach = 100f), 1e-4f)
        assertEquals(0.4f, gameFadeAlpha(65f, solid = 30f, reach = 100f), 1e-4f)          // halfway up the soft part
        assertEquals(0.8f * (1f - (3 * .01f - 2 * .001f)), gameFadeAlpha(37f, solid = 30f, reach = 100f), 1e-4f)
        assertEquals(0f, gameFadeAlpha(100f, solid = 30f, reach = 100f), 1e-4f)
        assertEquals(0f, gameFadeAlpha(140f, solid = 30f, reach = 100f), 0f)
        assertEquals(0.8f, gameFadeAlpha(50f, solid = 120f, reach = 100f), 0f)              // a title above the reach: solid
        assertEquals(0.65f, GAME_FADE_SHARE)
        // the title's bottom: the caption's 8 dp bottom padding and its 11 sp info line (1.15 line height)
        assertEquals(8f + 11f * 1.15f, gameFadeSolid(infoLineDp = 11f * 1.15f), 1e-4f)
    }

    @Test fun aTitleTilesNameKeepsClearOfTheCaption() {
        val room = homeTitleCaptionRoom(scale = 1f)
        assertEquals(22f, room, 0.001f)                                         // the 14 dp info line + 8
        assertEquals(4, homeTitleLines(76.31f, 12f, 0f, maxLines = 4))                   // portrait, unlit: 4 (5 would fit)
        assertEquals(3, homeTitleLines(76.31f, 12f, room, maxLines = 4))                 // lit: 54.31 dp left, 3 lines
        assertTrue(3 * 12f * 1.15f <= 76.31f - room)                                     // above the info line
        assertEquals(3, homeTitleLines(124.4f, 15f, 0f, maxLines = 3))                   // landscape
        assertEquals(3, homeTitleLines(124.4f, 15f, room, maxLines = 3))
        assertEquals(1, homeTitleLines(40f, 15f, room, maxLines = 3))                    // never under one
    }

    // The shrunk tiles (the first row above the hint bar): 49 dp and 53.6 dp portrait tiles.
    @Test fun shrunkTilesFitTheirNames() {
        val room = homeTitleCaptionRoom(scale = 1f)
        assertEquals(3, homeTitleLines(49f, 12f, 0f, maxLines = 4))                      // 49 / 13.8
        assertEquals(1, homeTitleLines(49f, 12f, room, maxLines = 4))                    // 27 / 13.8
        assertEquals(3, homeTitleLines(53.6f, 12f, 0f, maxLines = 4))
        assertEquals(2, homeTitleLines(53.6f, 12f, room, maxLines = 4))                  // 31.6 / 13.8
        assertEquals(2, homeTitleLines(53.6f, 12f, 0f, maxLines = 4, scale = 1.3f))      // a larger font scale: fewer
    }

    // An art-less tile (its abstract picture, or a title tile) shows its caption unlit too, exactly as lit: the same
    // fade, the caption carrying the name, and a title tile without its centred name (one name, at the caption's place).
    @Test fun anArtLessTileAlwaysShowsItsCaption() {
        // Home: the eased fade on the abstract picture and on a title tile alike.
        assertEquals(listOf(PICTURE, FADE, EDGE, CAPTION), gameTileLayers(picture = true, lit = false, caption = true, homeCaption = true, artLess = true))
        assertEquals(listOf(PICTURE, FADE, CAPTION), gameTileLayers(picture = true, lit = true, caption = true, homeCaption = true, artLess = true))
        assertEquals(listOf(FADE, EDGE, CAPTION), gameTileLayers(picture = false, lit = false, caption = true, homeCaption = true, artLess = true))
        assertEquals(listOf(FADE, CAPTION), gameTileLayers(picture = false, lit = true, caption = true, homeCaption = true, artLess = true))
        // A game list's Grid: the lit tile's shade on the abstract picture; a title tile none, as lit.
        assertEquals(listOf(PICTURE, FADE, EDGE, CAPTION), gameTileLayers(picture = true, lit = false, caption = true, homeCaption = false, gameFade = true, artLess = true))
        assertEquals(listOf(PICTURE, FADE, CAPTION), gameTileLayers(picture = true, lit = true, caption = true, homeCaption = false, gameFade = true, artLess = true))
        assertEquals(listOf(EDGE, CAPTION), gameTileLayers(picture = false, lit = false, caption = true, homeCaption = false, gameFade = true, artLess = true))
        assertEquals(listOf(CAPTION), gameTileLayers(picture = false, lit = true, caption = true, homeCaption = false, gameFade = true, artLess = true))
        // The main menu Grid's linear fade, as lit.
        assertEquals(listOf(PICTURE, FADE, EDGE, CAPTION), gameTileLayers(picture = true, lit = false, caption = true, homeCaption = false, artLess = true))
        // No inside caption (the Carousel): unchanged, the title tile keeps its centred name.
        assertEquals(listOf(TITLE, EDGE), gameTileLayers(picture = false, lit = false, caption = false, homeCaption = false, artLess = true))
        assertEquals(listOf(PICTURE, EDGE), gameTileLayers(picture = true, lit = false, caption = false, homeCaption = false, artLess = true))
        // Real art: the caption only when lit.
        assertEquals(listOf(PICTURE, EDGE), gameTileLayers(picture = true, lit = false, caption = true, homeCaption = true, artLess = false))
    }

    // A game list Grid's tile: whole detail rows while the name (its two lines), the rows, the stats line and the
    // padding fit the tile; never past it.
    @Test fun aGridTileHoldsTheDetailRowsThatFit() {
        // 10 top + 32 name + 13 a row + 8 bottom.
        assertEquals(2, gridTileDetailRows(tileH = 200f, nameH = 32f, rowH = 13f, statRows = 1, details = 2))
        assertEquals(2, gridTileDetailRows(tileH = 10f + 32f + 3 * 13f + 8f, nameH = 32f, rowH = 13f, statRows = 1, details = 2))
        assertEquals(1, gridTileDetailRows(tileH = 10f + 32f + 3 * 13f + 7f, nameH = 32f, rowH = 13f, statRows = 1, details = 2))
        assertEquals(2, gridTileDetailRows(tileH = 10f + 32f + 2 * 13f + 8f, nameH = 32f, rowH = 13f, statRows = 0, details = 5).coerceAtMost(2))
        assertEquals(0, gridTileDetailRows(tileH = 60f, nameH = 32f, rowH = 13f, statRows = 1, details = 2))   // a small tile: none
        assertEquals(0, gridTileDetailRows(tileH = 200f, nameH = 32f, rowH = 13f, statRows = 1, details = 0))
    }
}
