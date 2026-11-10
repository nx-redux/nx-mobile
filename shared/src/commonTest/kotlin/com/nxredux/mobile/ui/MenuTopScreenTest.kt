package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals

class MenuTopScreenTest {
    // Hosted only while the menu is on the second display; should it still draw meanwhile, never a game's details.
    @Test fun out_of_dual_mode_it_draws_the_backdrop_even_on_a_game_list() {
        val game = GameEntry(ContentRef("r/a.rom"), "a.rom", "A", folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/x")
        val system = SystemFolder("Console X", "X", null, listOf(game), null, null, "Console (X)", false)
        val source = ListSource.Console(listOf(system))
        val listed = Listed(system, game)
        val games = Screen.Games(source, listOf(listed))
        assertEquals(TopContent.Backdrop, menuTop(dual = false, games, 0))
        assertEquals(TopContent.GameDetails(listed), menuTop(dual = true, games, 0))
    }
}
