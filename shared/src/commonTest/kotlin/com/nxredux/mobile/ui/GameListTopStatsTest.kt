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

    // The title over the selected game's lines: its block centred in the header; alone (no lines to draw), on the
    // icons' centre line, the header's height (so the list) the same either way.
    @Test fun a_title_alone_sits_on_the_icons_centre() {
        val time = InfoLine(InfoKind.TIME, "2h")
        val ownRow = listOf(InfoLine(InfoKind.NEXT, "Win"), InfoLine(InfoKind.RELEASE, "1998"))
        // Text scale 1 without achievements: a 44 dp header, a 35 dp block (the title and one line) of 18 dp boxes.
        fun one(facts: List<InfoLine>) = gameListTitleTop(facts, next = false, height = 44f, block = 35f, lineH = 18f, pitch = 17.5f, iconsH = 44f)
        assertEquals(4.5f, one(listOf(time)))   // a first-row line: today's top
        assertEquals(13f, one(emptyList()))   // no lines: the 18 dp box centred on the 44 dp icons
        assertEquals(4.5f, one(ownRow))   // next off: the own-row kinds are drawn on the first row
        // With achievements: a 61 dp header, the next goal's row kept under the first.
        fun two(facts: List<InfoLine>, pitch: Float = 17.5f) = gameListTitleTop(facts, next = true, height = 61f, block = 53f, lineH = 18f, pitch = pitch, iconsH = 44f)
        assertEquals(4f, two(listOf(time)))   // a first-row line
        assertEquals(4f, two(listOf(time) + ownRow))
        assertEquals(13f, two(emptyList()))   // no lines
        assertEquals(13f, two(ownRow))   // only own-row lines: the first row is empty, the title alone (box 13–31, the row below from 39)
        assertEquals(4f, two(ownRow, pitch = 8f))   // were the row below (from 20) to meet the lone title: today's top
        // A huge font: a box taller than the icons never goes above the block's top.
        assertEquals(4f, gameListTitleTop(emptyList(), next = false, height = 150f, block = 142f, lineH = 54f, pitch = 50f, iconsH = 44f))
    }
}
