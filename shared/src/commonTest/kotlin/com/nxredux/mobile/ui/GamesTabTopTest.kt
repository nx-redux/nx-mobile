package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.ui.nx.LauncherChrome
import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Games tab's top: its title (the highlighted game's console), its icons and its List's info, as a console list's. */
class GamesTabTopTest {
    private fun game(system: SystemFolder, name: String) = Listed(
        system, GameEntry(ContentRef("Home/Roms/$name"), name, name, folder = ContentRef("Home/Roms"), sourceId = "home", relativeDir = "Roms"),
    )
    private val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList())
    private val android = SystemFolder("Android", ANDROID_TAG, null, emptyList(), android = true)
    private val nameless = SystemFolder("", "gpgx_sms", null, emptyList())

    // The title names the highlighted game's console, the words its own list has as its title; "Games" with nothing.
    @Test fun theTitleIsTheHighlightedGamesConsole() {
        val entries = listOf(game(gba, "Advance Wars"), game(android, "Alto"), game(nameless, "Alex Kidd"))
        assertEquals("Game Boy Advance", gamesTabTitle(entries, 0))
        assertEquals(listTitle(ListSource.Console(gba)), gamesTabTitle(entries, 0))
        assertEquals("Android", gamesTabTitle(entries, 1))
        assertEquals("GPGX", gamesTabTitle(entries, 2))   // no display name: the tag, as the List's console line names it
        assertEquals("Games", gamesTabTitle(emptyList(), 0))
        assertEquals("Games", gamesTabTitle(entries, 7))
    }

    // On a touchscreen the tab has a console list's icons, Recent first, then the Kids lock in a play window.
    @Test fun theTabsIconsAreAConsoleListsAndTheLock() {
        val sent = mutableListOf<NxButton>()
        var searched = 0; var layout = 0; var trophy = 0; var lock = 0
        val icons = gameListTitleIcons(
            LauncherChrome(), emptyList(), { sent += it }, recentIcon = true, touch = false, touchscreen = true,
            onSearch = { searched++ }, onLayout = { layout++ }, layoutOpen = true, onAchievements = { trophy++ }, achievementsOpen = false,
            onKidsLock = { lock++ },
        )
        assertEquals(listOf("Recent", "Search", "Layout", "Achievements", "Parent"), icons.map { it.description })
        assertEquals(listOf(false, false, true, false, false), icons.map { it.lit })
        icons.forEach { it.onClick() }
        assertEquals(listOf(NxButton.SELECT), sent)
        assertEquals(listOf(1, 1, 1, 1), listOf(searched, layout, trophy, lock))
        // No touchscreen: Recent, and the lock (the main menu's, a pad cannot reach it otherwise).
        assertEquals(listOf("Recent", "Parent"), gameListTitleIcons(LauncherChrome(), emptyList(), {}, true, false, touchscreen = false, onSearch = {}, onLayout = {}, onKidsLock = {}).map { it.description })
    }

    // Search: START's (any games to search); Layout: Layouts → Layout options; trophy: signed in, panel shown, dual's rule.
    @Test fun theTabsIconsFollowAConsoleListsConditions() {
        val console = ListSource.Console(gba)
        assertEquals(GameListIcons(search = true, layout = true), gameListIcons(console, tab = false, canSearch = false, layoutOptions = true))
        assertEquals(GameListIcons(search = true, layout = true), gameListIcons(ListSource.AllGames, tab = true, canSearch = true, layoutOptions = true))
        assertEquals(GameListIcons(search = false, layout = false), gameListIcons(ListSource.AllGames, tab = true, canSearch = false, layoutOptions = false))
        assertEquals(GameListIcons(search = false, layout = true), gameListIcons(ListSource.Hidden, tab = false, canSearch = true, layoutOptions = true))
        assertTrue(gameListTrophy(gameListTrophyAvailable(signedIn = true, panelShown = true), dual = false, info = null))
        assertFalse(gameListTrophy(gameListTrophyAvailable(signedIn = false, panelShown = true), dual = false, info = null))
        assertFalse(gameListTrophy(gameListTrophyAvailable(signedIn = true, panelShown = false), dual = false, info = null))
    }

    // A List's info in the hint bar's row: the bar hidden, Extra info on, not already under the title.
    @Test fun theTabsListInfoIsAConsoleLists() {
        for (barHidden in listOf(true, false)) for (extra in listOf(true, false)) for (top in listOf(true, false)) {
            val console = barHidden && extra && !top
            assertEquals(console, gameListInfoInRow(barHidden, extra, top))
        }
    }
}
