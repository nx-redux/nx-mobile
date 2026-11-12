package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
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

    @Test fun theGamesTabLeadsWithJump() {
        val tab = hints(jump = true)
        assertEquals(Hint(NxButton.L2, "Jump"), tab.first())
        assertEquals(hints(), tab.drop(1))
        assertEquals(listOf(Hint(NxButton.L2, "Jump")), hints(menuAvailable = false, achievements = false, netplay = false, canOpen = false, jump = true))
    }

    @Test fun theGamesTabsBottomRowIsTheMenusTitleWhenPushedOrAlone() {
        val tabs = listOf(MenuCategory.HOME, MenuCategory.GAMES, MenuCategory.TOOLS)
        assertNull(tabBarTitle(TabBar(tabs, 1)))                                   // the tab row
        assertEquals("Games", tabBarTitle(TabBar(tabs, 1, pushed = true)))         // a pushed tab: its label
        assertEquals(APP_NAME, tabBarTitle(TabBar(listOf(MenuCategory.GAMES), 0)))  // one tab: the app's name
    }
}
