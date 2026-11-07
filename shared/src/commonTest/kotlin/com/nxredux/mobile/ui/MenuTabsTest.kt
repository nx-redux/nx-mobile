package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.storage.ResumeInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class MenuTabsTest {
    private val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList(), null, null, "Game Boy Advance (GBA)", false)
    private fun listed(name: String) = Listed(gba, GameEntry(ContentRef("r/$name"), "$name.gba", name, folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy Advance (GBA)"))
    private val tools = listOf(ToolRow(ToolKind.SETTINGS, "Settings", null))
    private val consoles = listOf(MainRow.System(gba))
    private val stats = HomeStats(null, null, signedIn = false, top = null, computed = false)
    private val fresh = buildHome(null, emptyList(), emptyList(), stats, slots = 3)

    @Test fun tabsComeInTheFixedOrderWithHomeFirst() {
        val home = buildHome(MainRow.Continue(listed("Kirby"), ResumeInfo.NONE), listOf(listed("Zelda")), emptyList(), stats, slots = 3)
        val tabs = buildMenuTabs(home, consoles = consoles, collections = listOf(MainRow.Collection("Co-op", count = 2)), tools = tools)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.CONSOLES, MenuCategory.COLLECTIONS, MenuCategory.TOOLS), tabs.map { it.category })
        assertEquals(home.top + MainRow.Pin(listed("Zelda")), tabs[0].rows)
        assertEquals(listOf(MainRow.Collection("Co-op", count = 2)), tabs[2].rows)
        assertEquals(listOf(MainRow.Tool(tools.single())), tabs[3].rows)
    }

    // Every pin is on Home: the top section (the squares, "+N" for the rest), then every pinned game as a row game.
    @Test fun homeListsTheTopThenTheRowGames() {
        val games = listOf("Mega Man X", "Zelda", "Sonic & Knuckles", "Streets of Rage 2", "Sonic II", "Sonic 3D Blast").map(::listed)
        val pinnedTools = listOf(ToolKind.RETRO_ACHIEVEMENTS, ToolKind.SETTINGS, ToolKind.GAME_TIME, ToolKind.ARTWORK, ToolKind.CHEAT_DB).map { ToolRow(it, it.name, null) }
        val home = buildHome(null, games, pinnedTools, stats, slots = 4)
        assertEquals(
            listOf(MainRow.PickAGame) + pinnedTools.take(3).map(MainRow::PinnedTool) + MainRow.MoreTools(2) + games.map(MainRow::Pin),
            homeTabRows(home),
        )
    }

    @Test fun rowsAreTheTopThenThePinsAsGiven() {
        val zelda = MainRow.Pin(listed("Zelda"))
        val mario = MainRow.Pin(listed("Mario"))
        val tool = MainRow.PinnedTool(tools.single())
        val home = Home(top = listOf(MainRow.PickAGame, tool), pins = listOf(zelda, mario), strip = null, freshInstall = false)
        assertEquals(listOf(MainRow.PickAGame, tool, zelda, mario), homeTabRows(home))
    }

    @Test fun homeWithoutPinsHasNoPinnedRow() {
        assertEquals(listOf(MainRow.PickAGame), homeTabRows(fresh))
    }

    @Test fun homeAndToolsAreAlwaysThere() {
        val tabs = buildMenuTabs(fresh, consoles = emptyList(), collections = emptyList(), tools = tools)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.TOOLS), tabs.map { it.category })
    }

    private val home = MenuCategory.HOME
    private val cons = MenuCategory.CONSOLES
    private val coll = MenuCategory.COLLECTIONS
    private val tool = MenuCategory.TOOLS
    private fun tabs(vararg c: MenuCategory) = c.map { MenuTab(it, emptyList()) }

    @Test fun visibleTabsKeepTheFixedOrderAndDropHiddenOnes() {
        val all = tabs(home, cons, coll, tool)
        assertEquals(listOf(home, coll, tool), visibleMenuTabs(all, setOf(tool, home, coll)).map { it.category })
        assertEquals(listOf(cons), visibleMenuTabs(all, setOf(cons)).map { it.category })
    }

    @Test fun aShownTabWithoutContentStaysOut() {
        assertEquals(listOf(home, tool), visibleMenuTabs(tabs(home, tool), MenuCategory.entries.toSet()).map { it.category })
    }

    @Test fun noTabLeftShowsHome() {
        assertEquals(listOf(home), visibleMenuTabs(tabs(home, cons, tool), emptySet()).map { it.category })
    }

    @Test fun theStartTabIsHomeElseTheFirstVisible() {
        assertEquals(0, resolveMenuTab(tabs(home, tool), home))
        assertEquals(0, resolveMenuTab(tabs(cons, tool), home))
        assertEquals(0, resolveMenuTab(tabs(tool), home))
    }

    @Test fun aGoneTabResolvesToTheNextVisibleElseTheLast() {
        val t = tabs(home, cons, tool)
        assertEquals(2, resolveMenuTab(t, tool))
        assertEquals(2, resolveMenuTab(t, coll))                       // the next after it, not index 0
        assertEquals(1, resolveMenuTab(tabs(home, cons), tool))        // none after it: the last
        assertEquals(0, resolveMenuTab(emptyList(), cons))
    }

    @Test fun hiddenTabsAreTheOnesWithContentLayoutsHides() {
        val all = tabs(home, cons, tool)
        assertEquals(listOf(cons, tool), hiddenMenuTabs(all, tabs(home)))
        assertEquals(emptyList<MenuCategory>(), hiddenMenuTabs(all, all))
        assertEquals(listOf(cons), hiddenMenuTabs(all, tabs(tool)))    // a hidden Home is never offered
    }

    /** Kids Mode's Apps tab alone is not enough: with every other tab hidden, Home still shows before it. */
    @Test fun appsAloneStillShowsHome() {
        val home = MenuTab(MenuCategory.HOME, emptyList())
        val tools = MenuTab(MenuCategory.TOOLS, emptyList())
        val apps = MenuTab(MenuCategory.APPS, emptyList())
        val shown = setOf(MenuCategory.TOOLS, MenuCategory.APPS)
        assertEquals(listOf(MenuCategory.HOME, MenuCategory.APPS), visibleMenuTabs(listOf(home, apps, tools), shown, hideTools = true).map { it.category })
        assertEquals(listOf(MenuCategory.APPS, MenuCategory.TOOLS), visibleMenuTabs(listOf(home, apps, tools), shown).map { it.category })
    }

    @Test fun labels() {
        assertEquals(listOf("Home", "Consoles", "Collections", "Tools", "Apps"), MenuCategory.entries.map { it.label })
    }

    // The rows skip the Continue game when it is pinned too ([buildHome] leaves it out); the others keep their order.
    @Test fun thePinnedRowsSkipTheContinueGame() {
        val kirby = listed("Kirby")
        val home = buildHome(MainRow.Continue(kirby, ResumeInfo.NONE), listOf(listed("Mario"), kirby, listed("Zelda")), tools, stats, slots = 3)
        assertEquals(listOf(MainRow.Continue(kirby, ResumeInfo.NONE), MainRow.PinnedTool(tools.single()), MainRow.Pin(listed("Mario")), MainRow.Pin(listed("Zelda"))), homeTabRows(home))
    }

    // Continue's game the only pin: nothing left to show, so no row games at all, as with no pins.
    @Test fun theContinueGameAsTheOnlyPinLeavesNoPinnedRow() {
        val kirby = listed("Kirby")
        val home = buildHome(MainRow.Continue(kirby, ResumeInfo.NONE), listOf(kirby), emptyList(), stats, slots = 4)
        assertEquals(listOf<MainRow>(MainRow.Continue(kirby, ResumeInfo.NONE)), homeTabRows(home))
        assertEquals(emptyList(), home.pins)
    }
}
