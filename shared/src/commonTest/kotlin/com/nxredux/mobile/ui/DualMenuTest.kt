package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals

class DualMenuTest {
    private val game = GameEntry(ContentRef("r/a.rom"), "a.rom", "A", folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/x")
    private val system = SystemFolder("Console X", "X", null, listOf(game), null, null, "Console (X)", false)
    private val listed = Listed(system, game)
    private val source = ListSource.Console(listOf(system))
    private val list = SecondList(source, listOf(listed), 0)
    private fun systems() = Screen.Systems(emptyList())
    private fun games() = Screen.Games(source, listOf(listed))

    @Test fun top_is_the_preview_on_systems_with_one() {
        assertEquals(TopContent.Preview(list), DualMenu.top(systems(), list))
        assertEquals(TopContent.Backdrop, DualMenu.top(systems(), null))
    }

    @Test fun top_is_the_game_list_on_games() = assertEquals(TopContent.GameList, DualMenu.top(games(), null))

    @Test fun top_is_the_backdrop_elsewhere() {
        val settings = Screen.Settings(SettingsSection.entries.first(), emptyList())
        assertEquals(TopContent.Backdrop, DualMenu.top(settings, null))
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.SettingsMenu(emptyList()), null))
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.Loading, null))
    }

    @Test fun top_is_a_tool_with_the_focus_and_the_menu_stays_below() {
        val cheats = Screen.CheatDb()
        assertEquals(TopContent.Tool, DualMenu.top(cheats, null, tool = true))
        assertEquals(TopContent.Backdrop, DualMenu.top(cheats, null))
        assertEquals(TopContent.GameList, DualMenu.top(games(), null, tool = true))   // a game list from a tool: as any
        assertEquals(TopContent.Backdrop, DualMenu.top(systems(), null, tool = true))  // back on the menu
        assertEquals(BottomContent.Menu, DualMenu.bottom(cheats, active = true, tool = true))
        assertEquals(BottomContent.Normal, DualMenu.bottom(cheats, active = false, tool = true))
        assertEquals(BottomContent.Details, DualMenu.bottom(games(), active = true, tool = true))
    }

    @Test fun bottom_shows_details_only_on_games_while_active() {
        assertEquals(BottomContent.Details, DualMenu.bottom(games(), active = true))
        assertEquals(BottomContent.Normal, DualMenu.bottom(games(), active = false))
        assertEquals(BottomContent.Normal, DualMenu.bottom(systems(), active = true))
    }
}
