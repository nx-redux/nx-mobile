package com.nxredux.mobile.ui

import com.nxredux.mobile.gametime.GameTotal
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.HomeFrame
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.homeBoard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeLayoutsModelTest {
    private val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList(), null, null, "Game Boy Advance (GBA)", false)
    private fun listed(name: String) = Listed(gba, GameEntry(ContentRef("r/$name"), "$name.gba", name, folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy Advance (GBA)"))
    private val zelda = GameTotal(1, "Zelda", "GBA", "Roms/Game Boy Advance (GBA)/Zelda.gba", totalMs = 5_400_000, plays = 3, lastPlayedAt = 10)

    private val cont = MainRow.Continue(listed("Kirby"), ResumeInfo(9, "file:///shot.png"))
    private val a = listed("A"); private val b = listed("B"); private val c = listed("C")
    private val t1 = ToolRow(ToolKind.SETTINGS, "Settings", null)
    private val t2 = ToolRow(ToolKind.CHEAT_DB, "Cheats & Patches", null)
    private val t3 = ToolRow(ToolKind.GAME_TIME, "Game Tracker", null)
    private val stats = HomeStats(playedMs = 7_200_000, unlocks = 4, signedIn = true, top = zelda, computed = true)

    @Test fun list_and_carousel_order_continue_pins_shelves_then_tools() {
        val home = buildHome(cont, listOf(a, b), listOf(t1, t2), stats, slots = Int.MAX_VALUE, shelves = listOf(HomeShelf.NEW to listOf(c)))
        val rows = homeTabRows(home, MenuStyle.LIST)
        assertIs<MainRow.Continue>(rows[0])
        assertEquals(listOf(a, b), rows.subList(1, 3).map { (it as MainRow.Pin).entry })
        assertEquals(c, (rows[3] as MainRow.Shelf).entry)
        assertEquals(listOf(t1, t2), rows.drop(4).map { (it as MainRow.PinnedTool).row })
        assertEquals(4, homeCarouselRowCount(rows))
    }
    @Test fun grid_order_is_unchanged() {
        val home = buildHome(cont, listOf(a), listOf(t1), stats, slots = 4)
        assertEquals(home.top + home.pins + home.shelves, homeTabRows(home, MenuStyle.GRID))
        assertEquals(homeTabRows(home), homeTabRows(home, MenuStyle.GRID))
    }
    @Test fun carousel_dock_overflow_ends_in_more() {
        val home = buildHome(cont, emptyList(), listOf(t1, t2, t3), stats, slots = 2)
        val rows = homeTabRows(home, MenuStyle.CAROUSEL)
        assertEquals(listOf(t1), rows.filterIsInstance<MainRow.PinnedTool>().map { it.row })
        assertEquals(2, rows.filterIsInstance<MainRow.MoreTools>().single().count)
        assertIs<MainRow.MoreTools>(rows.last())
    }
    @Test fun fresh_install_with_tools_starts_on_pick_a_game() {
        val home = buildHome(null, emptyList(), listOf(t1), stats, slots = 4)
        val rows = homeTabRows(home, MenuStyle.CAROUSEL)
        assertEquals(MainRow.PickAGame, rows[0]); assertEquals(1, homeCarouselRowCount(rows))
    }
    @Test fun dock_fit_follows_the_width() {
        assertEquals(5, homeDockFit(16f * 2 + 5 * 56f + 4 * 12f, portrait = true, side = 56f))
        assertEquals(4, homeDockFit(16f * 2 + 5 * 56f + 4 * 12f - 1f, portrait = true, side = 56f))
        assertEquals(1, homeDockFit(10f, portrait = false, side = 64f))
    }
    // The dock's squares are Grid Home's portrait ones, in landscape too: the same size either way.
    @Test fun dock_side_is_grid_homes_portrait_tool_square() {
        val cover = HomeFrame(475f, 291f, portrait = true)
        assertEquals(homeBoard(cover, 2, tools = 3, games = 2).square, homeDockSide(cover, 2, games = 2))
        val phone = HomeFrame(411f, 900f, portrait = true)
        for (land in listOf(HomeFrame(933f, 600f, portrait = false), HomeFrame(900f, 330f, portrait = false), HomeFrame(700f, 600f, portrait = false))) {
            assertEquals(homeDockSide(phone, 1, games = 0), homeDockSide(land, 1, games = 0), "$land")
        }
        assertEquals(32f, homeDockSide(HomeFrame(60f, 40f, portrait = true), 2, games = 0))   // a tiny body: the floor
    }
}
