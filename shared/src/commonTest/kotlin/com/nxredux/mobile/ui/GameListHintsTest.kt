package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.visibleBarHints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameListHintsTest {
    private fun hints(
        menuAvailable: Boolean = true, achievements: Boolean = true, netplay: Boolean = true, canOpen: Boolean = true,
        resume: Boolean = false, openLabel: String = "Open", jump: Boolean = false,
    ) = gameListHints(menuAvailable, achievements, netplay, canOpen, resume, openLabel, jump)

    @Test fun theHintsAreUnchangedWithoutJump() {
        // A console's list: Options, Achievements, Netplay, then A Open (or Resume), in that order.
        assertEquals(
            listOf(Hint(NxButton.MENU, "Options"), Hint(NxButton.X, "Achievements"), Hint(NxButton.Y, "Netplay"), Hint(NxButton.A, "Open")),
            hints(),
        )
        assertEquals(Hint(NxButton.A, "Resume"), hints(resume = true).last())
        assertEquals(listOf(Hint(NxButton.A, "Unhide")), hints(menuAvailable = false, achievements = false, netplay = false, openLabel = "Unhide"))
        assertEquals(listOf(Hint(NxButton.MENU, "Options")), hints(achievements = false, netplay = false, canOpen = false))
    }

    // Jump is on R2 (next letter), so a tap on it moves forward even at the top of the list; the bar puts R2 last, in pad order.
    @Test fun theGamesTabLeadsWithJump() {
        val tab = hints(jump = true)
        assertEquals(Hint(NxButton.R2, "Jump"), tab.first())
        assertEquals(hints(), tab.drop(1))
        assertEquals(listOf(Hint(NxButton.R2, "Jump")), hints(menuAvailable = false, achievements = false, netplay = false, canOpen = false, jump = true))
    }

    @Test fun theGamesTabsBottomRowIsTheMenusTitleWhenPushedOrAlone() {
        val tabs = listOf(MenuCategory.HOME, MenuCategory.GAMES, MenuCategory.TOOLS)
        assertNull(tabBarTitle(TabBar(tabs, 1)))                                   // the tab row
        assertEquals("Games", tabBarTitle(TabBar(tabs, 1, pushed = true)))         // a pushed tab: its label
        assertEquals(APP_NAME, tabBarTitle(TabBar(listOf(MenuCategory.GAMES), 0)))  // one tab: the app's name
    }

    @Test fun mainMenuHintsAreContextualWhenTopIconsShow() {
        val tool = MainRow.Tool(ToolRow(ToolKind.GAME_TIME, "Game Tracker", null))
        val collection = MainRow.Collection("Favorites", 3)
        // Home with a game/menu item and top icons: Options + Netplay (no redundant Recent/Search).
        assertEquals(
            listOf(Hint(NxButton.MENU, "Options"), Hint(NxButton.Y, "Netplay")),
            mainMenuHints(MainRow.PinnedTool(tool.row), MenuCategory.HOME, tabMenu = false, netplay = true, canSearch = true, hasTopIcons = true),
        )
        // Collections & Tools with top icons: Options alone.
        assertEquals(listOf(Hint(NxButton.MENU, "Options")), mainMenuHints(collection, MenuCategory.COLLECTIONS, tabMenu = false, netplay = false, canSearch = true, hasTopIcons = true))
        assertEquals(listOf(Hint(NxButton.MENU, "Options")), mainMenuHints(tool, MenuCategory.TOOLS, tabMenu = false, netplay = false, canSearch = true, hasTopIcons = true))
        // Pick a game (no menu, no hidden tabs) with top icons: clean empty right side.
        assertEquals(emptyList(), mainMenuHints(MainRow.PickAGame, MenuCategory.HOME, tabMenu = false, netplay = false, canSearch = true, hasTopIcons = true))
        // Without touchscreen top icons: falls back to Recent and Search.
        assertEquals(
            listOf(Hint(NxButton.SELECT, "Recent"), Hint(NxButton.START, "Search")),
            mainMenuHints(MainRow.PickAGame, MenuCategory.HOME, tabMenu = false, netplay = false, canSearch = true, hasTopIcons = false),
        )
    }

    @Test fun visibleBarHintsOmitsBBackOnly() {
        // B Back is omitted while other B actions (Cancel, Resume) stay in pad order.
        assertEquals(
            listOf(Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Open")),
            visibleBarHints(listOf(Hint(NxButton.A, "Open"), Hint(NxButton.B, "Back"), Hint(NxButton.MENU, "Options"))),
        )
        assertEquals(
            listOf(Hint(NxButton.B, "Cancel"), Hint(NxButton.A, "OK")),
            visibleBarHints(listOf(Hint(NxButton.A, "OK"), Hint(NxButton.B, "Cancel"))),
        )
        assertEquals(
            listOf(Hint(NxButton.B, "Resume"), Hint(NxButton.A, "Quit")),
            visibleBarHints(listOf(Hint(NxButton.A, "Quit"), Hint(NxButton.B, "Resume"))),
        )
    }
}
