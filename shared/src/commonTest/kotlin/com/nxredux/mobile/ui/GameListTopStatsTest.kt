package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.ui.nx.LauncherChrome
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Where a game list shows the selected game's info: on a line under its title in List, Minimal and Grid. */
class GameListTopStatsTest {
    @Test fun list_minimal_and_grid_show_it_under_the_title() {
        assertTrue(gameListTopStats(MenuStyle.LIST, titleShown = true, extraInfo = true))   // hints shown or hidden
        assertFalse(gameListTopStats(MenuStyle.LIST, titleShown = false, extraInfo = true))   // no title: the bar's row
        assertTrue(gameListTopStats(MenuStyle.MINIMAL, titleShown = true, extraInfo = true))
        assertTrue(gameListTopStats(MenuStyle.GRID, titleShown = true, extraInfo = true))
        assertFalse(gameListTopStats(MenuStyle.CAROUSEL, titleShown = true, extraInfo = true))
        assertFalse(gameListTopStats(MenuStyle.BACKDROP, titleShown = true, extraInfo = true))
        assertFalse(gameListTopStats(MenuStyle.GRID, titleShown = false, extraInfo = true))   // no title: the tile keeps it
        assertFalse(gameListTopStats(MenuStyle.MINIMAL, titleShown = true, extraInfo = false))
    }

    // Two screens: the top screen shows the game's stats, so the list's title keeps its name alone, as Extra info off.
    @Test fun the_dual_game_list_drops_the_games_stats_only() {
        val chrome = LauncherChrome(listTitle = true, extraInfo = true, buttonHints = false)
        assertEquals(chrome.copy(extraInfo = false), gameListChrome(chrome, dual = true))
        assertEquals(chrome, gameListChrome(chrome, dual = false))
    }

    // The trophy icon on two screens only for a highlighted game with a set; one screen keeps its rule.
    @Test fun the_dual_trophy_needs_a_set_on_the_highlighted_game() {
        val set = GameInfo(null, null, RaProgress(1, 5, "Next"))
        val none = GameInfo(null, null, RaProgress(0, 0, null))
        assertTrue(gameListTrophy(available = true, dual = true, set))
        assertFalse(gameListTrophy(available = true, dual = true, none))
        assertFalse(gameListTrophy(available = true, dual = true, null))
        assertFalse(gameListTrophy(available = false, dual = true, set))
        assertTrue(gameListTrophy(available = true, dual = false, null))
        assertFalse(gameListTrophy(available = false, dual = false, set))
    }
}
