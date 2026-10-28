package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.LauncherChrome
import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameListTitleIconsTest {
    private val entry = switcherEntryForTest()

    @Test fun theSwitcherListOnTouchHasTheTrashAndNoRecentWhateverThePageTitleSays() {
        for (title in listOf(true, false)) {
            val chrome = LauncherChrome(listTitle = title, topIcons = true)
            val sent = mutableListOf<NxButton>()
            val icons = gameListTitleIcons(chrome, switcherHints(entry), { sent += it }, recentIcon = false, touch = true)
            assertEquals(listOf("Remove"), icons.map { it.description })
            icons.single().onClick()
            assertEquals(listOf(NxButton.Y), sent)
        }
    }

    // With the pad and Button hints hidden, a game list keeps Recent as an icon (no bar shows it).
    @Test fun aGameListWithHintsHiddenKeepsRecentAsAnIcon() {
        val chrome = LauncherChrome(buttonHints = false, topIcons = true)
        assertEquals(listOf("Recent"), gameListTitleIcons(chrome, emptyList(), {}, recentIcon = true, touch = false).map { it.description })
    }

    @Test fun noIconsOffTouchOrWithoutAnEntry() {
        val chrome = LauncherChrome(topIcons = true)
        assertTrue(gameListTitleIcons(chrome, switcherHints(entry), {}, recentIcon = false, touch = false).isEmpty())
        assertTrue(gameListTitleIcons(chrome, switcherHints(null), {}, recentIcon = false, touch = true).isEmpty())
    }

    // On a device with a touchscreen, Recent, Search and Layout show whatever the bar shows.
    @Test fun aTouchscreenShowsRecentSearchAndLayoutAlways() {
        val withBar = LauncherChrome()   // a controller with the hint bar: no top icons before
        var searched = 0; var layout = 0
        val icons = gameListTitleIcons(withBar, emptyList(), {}, recentIcon = true, touch = false, touchscreen = true, onSearch = { searched++ }, onLayout = { layout++ }, layoutOpen = true)
        assertEquals(listOf("Recent", "Search", "Layout"), icons.map { it.description })
        assertEquals(listOf(false, false, true), icons.map { it.lit })
        icons[1].onClick(); icons[2].onClick()
        assertEquals(1, searched); assertEquals(1, layout)
        // Inside Recent: no Recent icon; a list that cannot be searched: no Search.
        assertEquals(listOf("Layout"), gameListTitleIcons(withBar, emptyList(), {}, recentIcon = false, touch = false, touchscreen = true, onLayout = {}).map { it.description })
        // No touchscreen: Recent alone, an icon even with the bar shown (START and Layouts stand in for the rest).
        assertEquals(listOf("Recent"), gameListTitleIcons(withBar, emptyList(), {}, recentIcon = true, touch = false, touchscreen = false, onSearch = {}, onLayout = {}).map { it.description })
    }
}
