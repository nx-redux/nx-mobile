package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.HomeShelf
import kotlin.test.Test
import kotlin.test.assertEquals

class DualMenuTest {
    private val game = GameEntry(ContentRef("r/a.rom"), "a.rom", "A", folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/x")
    private val game2 = GameEntry(ContentRef("r/b.rom"), "b.rom", "B", folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/x")
    private val system = SystemFolder("Console X", "X", null, listOf(game, game2), null, null, "Console (X)", false)
    private val listed = Listed(system, game)
    private val listed2 = Listed(system, game2)
    private val source = ListSource.Console(listOf(system))
    private val resume = ResumeInfo(null)
    private fun systems(vararg rows: MainRow) = Screen.Systems(listOf(MenuTab(MenuCategory.HOME, rows.toList())))

    @Test fun games_show_the_highlighted_games_details() {
        val games = Screen.Games(source, listOf(listed, listed2))
        assertEquals(TopContent.GameDetails(listed), DualMenu.top(games, 0))
        assertEquals(TopContent.GameDetails(listed2), DualMenu.top(games, 1))
    }

    @Test fun an_empty_list_shows_the_backdrop() {
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.Games(source, emptyList()), 0))
    }

    @Test fun home_game_rows_show_details_other_rows_backdrop() {
        val tool = MainRow.Tool(ToolRow(ToolKind.SETTINGS, "Settings", null))
        val s = systems(MainRow.Pin(listed), MainRow.Shelf(listed2, HomeShelf.NEW), MainRow.Continue(listed, resume), tool, MainRow.MoreTools(2), MainRow.PickAGame)
        assertEquals(TopContent.GameDetails(listed), DualMenu.top(s, 0))
        assertEquals(TopContent.GameDetails(listed2), DualMenu.top(s, 1))
        assertEquals(TopContent.GameDetails(listed), DualMenu.top(s, 2))
        assertEquals(TopContent.Backdrop, DualMenu.top(s, 3))
        assertEquals(TopContent.Backdrop, DualMenu.top(s, 4))
        assertEquals(TopContent.Backdrop, DualMenu.top(s, 5))
    }

    @Test fun a_console_row_shows_its_stats() {
        val row = MainRow.System(system)
        assertEquals(TopContent.ConsoleStats(row.systems), DualMenu.top(systems(row), 0))
    }

    @Test fun a_collection_row_shows_its_stats() {
        assertEquals(TopContent.CollectionStats("Favourites"), DualMenu.top(systems(MainRow.Collection("Favourites", 3)), 0))
    }

    @Test fun tools_settings_and_kids_show_the_backdrop() {
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.CheatDb(), 0))
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.SettingsMenu(emptyList()), 0))
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.KidsHome, 0))
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.Loading, 0))
    }

    @Test fun the_switcher_shows_the_highlighted_games_details() {
        val s = Screen.Switcher(listOf(SwitcherEntry(system, game, resume), SwitcherEntry(system, game2, resume)), 1)
        assertEquals(TopContent.GameDetails(listed2), DualMenu.top(s, 0))
        assertEquals(TopContent.Backdrop, DualMenu.top(Screen.Switcher(emptyList(), 0), 0))
    }
}
