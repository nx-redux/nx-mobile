package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.MenuStyle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Where a game list shows the selected game's info: on a line under its title in List, Minimal and Grid. */
class GameListTopStatsTest {
    @Test fun list_minimal_and_grid_show_it_under_the_title() {
        assertTrue(gameListTopStats(MenuStyle.LIST, titleShown = true, extraInfo = true, infoInBarRow = false))
        assertFalse(gameListTopStats(MenuStyle.LIST, titleShown = true, extraInfo = true, infoInBarRow = true))   // hints hidden: the bar's row
        assertTrue(gameListTopStats(MenuStyle.MINIMAL, titleShown = true, extraInfo = true, infoInBarRow = true))
        assertTrue(gameListTopStats(MenuStyle.GRID, titleShown = true, extraInfo = true, infoInBarRow = false))
        assertFalse(gameListTopStats(MenuStyle.CAROUSEL, titleShown = true, extraInfo = true, infoInBarRow = false))
        assertFalse(gameListTopStats(MenuStyle.BACKDROP, titleShown = true, extraInfo = true, infoInBarRow = false))
        assertFalse(gameListTopStats(MenuStyle.GRID, titleShown = false, extraInfo = true, infoInBarRow = false))   // no title: the tile keeps it
        assertFalse(gameListTopStats(MenuStyle.MINIMAL, titleShown = true, extraInfo = false, infoInBarRow = false))
    }
}
