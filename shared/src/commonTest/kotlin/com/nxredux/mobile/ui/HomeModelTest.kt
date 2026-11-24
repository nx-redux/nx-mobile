package com.nxredux.mobile.ui

import com.nxredux.mobile.gametime.GameTotal
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.ResumeInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeModelTest {
    private val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList(), null, null, "Game Boy Advance (GBA)", false)
    private fun listed(name: String) = Listed(gba, GameEntry(ContentRef("r/$name"), "$name.gba", name, folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy Advance (GBA)"))
    private val zelda = GameTotal(1, "Zelda", "GBA", "Roms/Game Boy Advance (GBA)/Zelda.gba", totalMs = 5_400_000, plays = 3, lastPlayedAt = 10)
    private val cont = MainRow.Continue(listed("Kirby"), ResumeInfo(9, "file:///shot.png"))

    private val stats = HomeStats(playedMs = 7_200_000, unlocks = 4, signedIn = true, top = zelda, computed = true)

    private val g1 = listed("G1"); private val g2 = listed("G2"); private val g3 = listed("G3")
    private val t1 = ToolRow(ToolKind.SETTINGS, "Settings", null)
    private val t2 = ToolRow(ToolKind.CHEAT_DB, "Cheats & Patches", null)
    private val t3 = ToolRow(ToolKind.GAME_TIME, "Game Tracker", null)
    private val t4 = ToolRow(ToolKind.RETRO_ACHIEVEMENTS, "RetroAchievements", null)
    private val t5 = ToolRow(ToolKind.ARTWORK, "Artwork", null)
    private fun contOf(g: Listed) = MainRow.Continue(g, ResumeInfo.NONE)

    // Every pinned game is a row game; the top section is Continue, and the pinned tools live in [Home.tools] ("+N" last past slots).
    @Test fun squaresComeFirstAndEveryGameIsARowGame() {
        val h = buildHome(cont, listOf(g1, g2, g3), listOf(t1, t2, t3, t4, t5), stats, slots = 4)
        assertEquals(listOf<MainRow>(cont), h.top)
        assertEquals(listOf(MainRow.Pin(g1), MainRow.Pin(g2), MainRow.Pin(g3)), h.pins)
        assertEquals(listOf(MainRow.PinnedTool(t1), MainRow.PinnedTool(t2), MainRow.PinnedTool(t3), MainRow.MoreTools(2)), h.tools)
    }
    // Tool slots cap [Home.tools]: 5 tools in 6 slots all show; 8 show 5 and "+3".
    @Test fun sixSlotsShowFiveToolsOrFiveAndMore() {
        val five = buildHome(cont, listOf(g1), listOf(t1, t2, t3, t4, t5), stats, slots = 6)
        assertEquals(listOf<MainRow>(cont), five.top)
        assertEquals(listOf(t1, t2, t3, t4, t5).map { MainRow.PinnedTool(it) }, five.tools)
        val eight = listOf(t1, t2, t3, t4, t5, t1.copy(label = "A"), t1.copy(label = "B"), t1.copy(label = "C"))
        val h = buildHome(cont, listOf(g1), eight, stats, slots = 6)
        assertEquals(eight.take(5).map { MainRow.PinnedTool(it) } + MainRow.MoreTools(3), h.tools)
    }
    // The wide face's inline games: the first [inline] row games (Continue's left out) follow Continue in the top; tools stay in [Home.tools].
    @Test fun inlineGamesJoinTheTopAfterContinue() {
        val h = buildHome(contOf(g1), listOf(g1, g2, g3), listOf(t1, t2), stats, slots = 6, inline = 2)
        assertEquals(listOf(contOf(g1), MainRow.Pin(g2), MainRow.Pin(g3)), h.top)
        assertEquals(emptyList(), h.pins)
        assertEquals(listOf(MainRow.PinnedTool(t1), MainRow.PinnedTool(t2)), h.tools)
        assertEquals(HomeView(homeStrip(stats), topCount = 3, tools = 2, games = 2), homeView(h, homeTabRows(h)))
        val one = buildHome(cont, listOf(g1, g2), listOf(t1), stats, slots = 6, inline = 1)
        assertEquals(listOf(cont, MainRow.Pin(g1)), one.top); assertEquals(listOf(MainRow.Pin(g2)), one.pins)
        assertEquals(listOf(MainRow.PinnedTool(t1)), one.tools)
    }
    @Test fun toolsWithinSlotsAllShow() {
        val h = buildHome(cont, listOf(g1), listOf(t1, t2, t3), stats, slots = 3)
        assertEquals(listOf<MainRow>(cont), h.top)
        assertEquals(listOf(MainRow.PinnedTool(t1), MainRow.PinnedTool(t2), MainRow.PinnedTool(t3)), h.tools)
        assertEquals(listOf(MainRow.Pin(g1)), h.pins)
    }
    @Test fun continueGameIsNotARowGame() {
        val h = buildHome(contOf(g1), listOf(g1, g2), emptyList(), stats, slots = 4)
        assertEquals(listOf<MainRow>(contOf(g1)), h.top)
        assertEquals(listOf(MainRow.Pin(g2)), h.pins)
    }
    @Test fun freshInstallHasNoStrip() {
        val h = buildHome(null, emptyList(), emptyList(), stats, slots = 3)
        assertEquals(listOf(MainRow.PickAGame), h.top); assertNull(h.strip); assertTrue(h.freshInstall)
    }
    @Test fun viewCounts() {
        val h = buildHome(cont, listOf(g1, g2, g3), listOf(t1, t2, t3, t4, t5), stats, slots = 4)
        assertEquals(HomeView(homeStrip(stats), topCount = 1, tools = 5, games = 3), homeView(h, homeTabRows(h)))
    }

    @Test fun normal() {
        val home = buildHome(cont, listOf(listed("Zelda")), listOf(t1), stats, slots = 3)
        assertFalse(home.freshInstall)
        assertEquals(listOf<MainRow>(cont), home.top)
        assertEquals(listOf(MainRow.PinnedTool(t1)), home.tools)
        assertEquals(homeStrip(stats), home.strip)
        assertEquals(listOf(MainRow.Pin(listed("Zelda"))), home.pins)
    }

    @Test fun lastGameWithoutResumeStateIsStillContinue() {
        val noResume = MainRow.Continue(listed("Kirby"), ResumeInfo.NONE)
        val home = buildHome(noResume, emptyList(), emptyList(), stats, slots = 3)
        assertEquals(noResume, home.top.first())
        assertFalse(home.freshInstall)
    }

    @Test fun freshInstallIsPickAGameAloneWithNoStatsOrPins() {
        val home = buildHome(null, emptyList(), emptyList(), HomeStats(playedMs = 0, unlocks = 0, signedIn = true, top = null, computed = true), slots = 3)
        assertTrue(home.freshInstall)
        assertEquals(listOf<MainRow>(MainRow.PickAGame), home.top)
        assertEquals(emptyList(), home.pins)
    }

    @Test fun freshInstallHidesStatsEvenBeforeTheyAreComputed() {
        val home = buildHome(null, emptyList(), emptyList(), HomeStats(null, null, signedIn = false, top = null, computed = false), slots = 3)
        assertEquals(listOf<MainRow>(MainRow.PickAGame), home.top)
    }

    @Test fun returningUserWithNoPlayInTheWindow() {
        val none = HomeStats(playedMs = 0, unlocks = 0, signedIn = true, top = null, computed = true)
        val home = buildHome(cont, emptyList(), emptyList(), none, slots = 3)
        assertFalse(home.freshInstall)
        assertEquals(HomeStrip(NO_PLAY_YET, true, "0", null), home.strip)   // one line: "No play yet", 0 achievements
        assertEquals(emptyList(), home.pins)
    }

    @Test fun pinsWithOnlyToolsAreNotAFreshInstall() {
        val home = buildHome(null, emptyList(), listOf(t2, t1), HomeStats(playedMs = 0, unlocks = 0, signedIn = true, top = null, computed = true), slots = 3)
        assertFalse(home.freshInstall)
        assertEquals(listOf<MainRow>(MainRow.PickAGame), home.top)
        assertEquals(listOf(MainRow.PinnedTool(t2), MainRow.PinnedTool(t1)), home.tools)
        assertEquals(emptyList(), home.pins)
    }

    @Test fun pinnedGamesAloneAreNotAFreshInstall() {
        val home = buildHome(null, listOf(listed("Zelda")), emptyList(), stats, slots = 3)
        assertFalse(home.freshInstall)
        assertEquals(MainRow.PickAGame, home.top.first())
        assertEquals(listOf<MainRow>(MainRow.Pin(listed("Zelda"))), home.pins)
    }

    @Test fun gamesAndToolsKeepTheirOwnOrder() {
        val home = buildHome(cont, listOf(listed("B"), listed("A")), listOf(t1, t2), stats, slots = 3)
        assertEquals(listOf<MainRow>(cont), home.top)
        assertEquals(listOf(MainRow.PinnedTool(t1), MainRow.PinnedTool(t2)), home.tools)
        assertEquals(listOf(MainRow.Pin(listed("B")), MainRow.Pin(listed("A"))), home.pins)
    }

    @Test fun unavailableToolIsSkipped() {
        val available = listOf(t2, t1)   // no RetroAchievements, Game Tracker or Artwork on this platform
        val rows = pinnedToolRows(listOf("GAME_TIME", "SETTINGS", "RETRO_ACHIEVEMENTS", "CHEAT_DB"), available)
        assertEquals(listOf(t1, t2), rows)
    }

    @Test fun onlyUnavailableToolPinnedIsAFreshInstall() {
        val rows = pinnedToolRows(listOf("ARTWORK"), listOf(t1))
        val home = buildHome(null, emptyList(), rows, HomeStats(0, 0, signedIn = true, top = null, computed = true), slots = 3)
        assertTrue(home.freshInstall)
    }
}
