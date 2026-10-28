package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaGameView
import com.nxredux.mobile.cheats.db.CheatDbManifest
import com.nxredux.mobile.cheats.db.CheatDbSource
import com.nxredux.mobile.cheats.db.CheatDbState
import com.nxredux.mobile.scraper.ScrapeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolsTest {
    private val manifest = CheatDbManifest("s", 1758750000000L, listOf("Cheats/GB/a.cht", "Cheats/GB/b.cht", "Cheats/FC/c.cht"))

    @Test fun rows_follow_the_state() {
        assertEquals(listOf(CheatDbAction.DOWNLOAD), cheatDbRows(CheatDbState.NotInstalled))
        assertEquals(listOf(CheatDbAction.CHECK, CheatDbAction.REMOVE), cheatDbRows(CheatDbState.Installed(manifest)))
        assertEquals(emptyList(), cheatDbRows(CheatDbState.Downloading(1, 2, null)))
        assertEquals(emptyList(), cheatDbRows(CheatDbState.Unknown))
        assertEquals("Download (about 39 MB)", CheatDbAction.DOWNLOAD.label)
    }

    @Test fun retroachievements_is_the_first_tool() {
        assertEquals(listOf(ToolKind.RETRO_ACHIEVEMENTS, ToolKind.GAME_TIME, ToolKind.ARTWORK, ToolKind.CHEAT_DB, ToolKind.DEVICE_SYNC, ToolKind.SETTINGS, ToolKind.APPS, ToolKind.APP), ToolKind.entries.toList())
    }

    @Test fun sub_pages_name_their_tool() {
        assertEquals("RetroAchievements | Achievements", subPageTitle(ToolKind.RETRO_ACHIEVEMENTS, "Achievements"))
        assertEquals("Artwork | Progress", subPageTitle(ToolKind.ARTWORK, "Progress"))
        assertEquals("Cheats & Patches | X", subPageTitle(ToolKind.CHEAT_DB, "X"))
        assertEquals("Game Tracker | X", subPageTitle(ToolKind.GAME_TIME, "X"))
        assertEquals("X", subPageTitle(ToolKind.APP, "X"))
    }

    @Test fun tool_page_titles() {
        val game = RaGameView("h", 1, "Super Mario Land", null, 3, 20, 0)
        assertEquals("RetroAchievements | Achievements", toolPageTitle(Screen.RaGames(emptyList())))
        assertEquals("RetroAchievements | Super Mario Land (3/20)", toolPageTitle(Screen.RaGame(game, emptyList())))
        // The count is drawn apart from the title, so a cut never reaches it.
        assertEquals("RetroAchievements | Super Mario Land", toolPageTitleHead(Screen.RaGame(game, emptyList())))
        val achievement = com.nxredux.mobile.achievements.RaAchievement(1, "Midway", "", 5, "", com.nxredux.mobile.achievements.RaAchievementState.LOCKED, null, null, 0f, 0f, null)
        assertEquals("RetroAchievements | Super Mario Land (3/20)", toolPageTitle(Screen.RaAchievement(game, achievement)))
        assertEquals("(3/20)", toolPageCount(Screen.RaGame(game, emptyList())))
        assertNull(toolPageCount(Screen.RaGames(emptyList())))
        assertEquals("RetroAchievements | Settings", toolPageTitle(Screen.RaSettings(emptyList())))
        assertEquals("Artwork | Fetch by console", toolPageTitle(Screen.ArtworkConsoles(emptyList())))
        assertEquals("Artwork | Settings", toolPageTitle(Screen.ArtworkSettings(emptyList())))
        assertEquals("Artwork | Reset artwork", toolPageTitle(Screen.ArtworkReset(emptyList())))
        assertEquals("Artwork | Progress", toolPageTitle(Screen.ArtworkProgress(ScrapeState())))
        assertNull(toolPageTitle(Screen.Artwork(emptyList(), emptyList())))   // a tool's own page keeps its plain name
        assertNull(toolPageTitle(Screen.RetroAchievements()))
    }

    @Test fun a_split_pane_title_drops_the_tool_the_left_pane_shows() {
        val game = RaGameView("h", 1, "Super Mario Land", null, 3, 20, 0)
        val achievement = com.nxredux.mobile.achievements.RaAchievement(1, "Midway", "", 5, "", com.nxredux.mobile.achievements.RaAchievementState.LOCKED, null, null, 0f, 0f, null)
        assertEquals("Settings", toolPageTitle(Screen.RaSettings(emptyList()), split = true))
        assertEquals("Achievements", toolPageTitle(Screen.RaGames(emptyList()), split = true))
        assertEquals("Super Mario Land (3/20)", toolPageTitle(Screen.RaGame(game, emptyList()), split = true))
        assertEquals("Super Mario Land", toolPageTitleHead(Screen.RaGame(game, emptyList()), split = true))   // the count stays apart
        assertEquals("Super Mario Land", toolPageTitleHead(Screen.RaAchievement(game, achievement), split = true))
        assertEquals("(3/20)", toolPageCount(Screen.RaAchievement(game, achievement)))
        assertEquals("Progress", subPageTitle(ToolKind.ARTWORK, "Progress", split = true))
        assertEquals("Fetch by console", toolPageTitle(Screen.ArtworkConsoles(emptyList()), split = true))
        assertEquals("Progress", toolPageTitle(Screen.ArtworkProgress(ScrapeState()), split = true))
        assertEquals("Settings", toolPageTitle(Screen.ArtworkSettings(emptyList()), split = true))
        assertEquals("Reset artwork", toolPageTitle(Screen.ArtworkReset(emptyList()), split = true))
        assertEquals("Artwork | Settings", toolPageTitle(Screen.ArtworkSettings(emptyList())))
        // Full width keeps the tool's name.
        assertEquals("RetroAchievements | Settings", toolPageTitle(Screen.RaSettings(emptyList()), split = false))
        assertEquals("RetroAchievements | Super Mario Land", toolPageTitleHead(Screen.RaAchievement(game, achievement)))
    }

    @Test fun a_games_sessions_are_titled_by_the_tool_then_the_game_or_the_game_alone_in_a_split() {
        val game = com.nxredux.mobile.gametime.GameTotal(1, "Tetris", "GB", "/Roms/GB/Tetris.gb", 60_000, 2, 0)
        val s = Screen.GameTimeSessions(game, com.nxredux.mobile.gametime.GameSessions(emptyList()))
        assertEquals("Game Tracker | Tetris", toolPageTitle(s))
        assertEquals("Tetris", toolPageTitle(s, split = true))
        assertNull(toolPageCount(s))
    }

    @Test fun status_text() {
        assertEquals("Unavailable", cheatDbStatus(null))
        assertEquals("…", cheatDbStatus(CheatDbState.Unknown))
        assertEquals("Not installed", cheatDbStatus(CheatDbState.NotInstalled))
        assertEquals("2 systems · 3 files · ${formatInstallDate(1758750000000L)}", cheatDbStatus(CheatDbState.Installed(manifest)))
        assertEquals("Downloading…", cheatDbStatus(CheatDbState.Downloading(0, -1, null)))
        assertEquals("Installing…", cheatDbStatus(CheatDbState.Installing("GB", 1, 2, null)))
        assertEquals("Checking…", cheatDbStatus(CheatDbState.Checking(manifest)))
        assertEquals("Removing…", cheatDbStatus(CheatDbState.Removing(manifest)))
    }

    @Test fun details_block_follows_the_state() {
        assertEquals(listOf("Not installed", "About 39 MB download", "Cheats and patches for 9 systems"), cheatDbDetails(CheatDbState.NotInstalled, systems = 9))
        assertEquals(
            listOf("Installed", "2 systems · 3 files", "Last update: ${formatInstallDate(manifest.installedAt)}"),
            cheatDbDetails(CheatDbState.Installed(manifest), systems = 9),
        )
        assertEquals(listOf("…"), cheatDbDetails(CheatDbState.Unknown, systems = 9))
    }

    @Test fun progress_line_and_fraction() {
        assertEquals("Downloading cheats… 12.3 / 37.0 MB", cheatDbProgressLine(CheatDbState.Downloading(12_897_485, 38_797_312, null)))
        assertEquals("Downloading cheats… 12.3 MB", cheatDbProgressLine(CheatDbState.Downloading(12_897_485, -1, null)))
        assertEquals("Downloading patches… 1.0 / 2.1 MB", cheatDbProgressLine(CheatDbState.Downloading(1L shl 20, 2_179_893, null, CheatDbSource.PCSX2)))
        assertEquals("Installing GB (3/8)", cheatDbProgressLine(CheatDbState.Installing("GB", 3, 8, null)))
        assertEquals("Checking for updates…", cheatDbProgressLine(CheatDbState.Checking(manifest)))
        assertEquals("Removing…", cheatDbProgressLine(CheatDbState.Removing(manifest)))
        assertNull(cheatDbProgressLine(CheatDbState.Installed(manifest)))
        assertEquals(0.5f, cheatDbProgressFraction(CheatDbState.Downloading(50, 100, null)))
        assertNull(cheatDbProgressFraction(CheatDbState.Downloading(50, -1, null)))
        assertEquals(0.375f, cheatDbProgressFraction(CheatDbState.Installing("GB", 3, 8, null)))
        assertNull(cheatDbProgressFraction(CheatDbState.Checking(manifest)))
    }

    @Test fun install_date_is_day_month_year() {
        val text = formatInstallDate(1790286000000L)   // 2026-09-25 (UTC+8) / 2026-09-24 (UTC)
        // Month names depend on the device locale, so only the year is pinned.
        assertEquals(true, text.isNotBlank() && text.contains("2026"), text)
    }
}
