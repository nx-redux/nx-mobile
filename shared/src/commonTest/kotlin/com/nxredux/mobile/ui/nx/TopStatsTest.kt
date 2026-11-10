package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.ui.BiosMissing
import com.nxredux.mobile.ui.GameInfo
import com.nxredux.mobile.ui.Listed
import kotlin.test.Test
import kotlin.test.assertEquals

class TopStatsTest {
    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val now = 100 * day

    private fun game(tag: String, name: String, firstSeen: Long? = null) = GameEntry(
        ContentRef("Home/Roms/$tag/$name.rom"), "$name.rom", name,
        folder = ContentRef("Home/Roms/$tag"), sourceId = GamePath.HOME, relativeDir = "Roms/$tag", firstSeen = firstSeen,
    )

    private fun system(tag: String, name: String, vararg games: GameEntry) = SystemFolder(name, tag, CoreRegistry.forTag(tag), games.toList())

    private fun listed(s: SystemFolder) = s.games.map { Listed(s, it) }

    private fun played(ms: Long, plays: Long, at: Long, ra: RaProgress? = null) = GameInfo(at, ms, ra, plays)

    @Test fun console_stats_count_games_time_sessions_and_never_played() {
        val gba = system("GBA", "Game Boy Advance", game("GBA", "Minish Cap"), game("GBA", "Emerald"), game("GBA", "Fusion"))
        val (minish, emerald) = gba.games
        val info = mapOf(
            minish.path to played(24 * hour, 10, now - day, RaProgress(30, 40, null)),
            emerald.path to played(19 * hour, 5, now - 3 * day, RaProgress(12, 12, null)),
        )
        val s = consoleStats(listOf(gba), listed(gba), info, now, weekStart = now - 6 * day, bios = null, cheatGames = 0)
        assertEquals("Game Boy Advance", s.title)
        assertEquals(CoreRegistry.forTag("GBA")!!.name, s.subtitle)
        assertEquals(3, s.games)
        assertEquals(1, s.neverPlayed)
        assertEquals("1 never played", s.gamesNote)
        assertEquals(43 * hour, s.totalMs)
        assertEquals(15L, s.sessions)
        assertEquals(minish, s.lastPlayed?.first?.game)
        assertEquals(now - day, s.lastPlayed?.second)
        assertEquals("Achievements" to "42", s.fourth)
        assertEquals("in 2 games", s.fourthNote)
    }

    @Test fun most_played_is_top_five_by_time() {
        val names = listOf("A", "B", "C", "D", "E", "F", "G")
        val gb = system("GB", "Game Boy", *names.map { game("GB", it) }.toTypedArray())
        // G most, then F..., A never played.
        val info = gb.games.drop(1).mapIndexed { i, g -> g.path to played((i + 1) * hour, 1, now) }.toMap()
        val s = consoleStats(listOf(gb), listed(gb), info, now, weekStart = now - 6 * day, bios = null, cheatGames = 0)
        assertEquals("Most played", s.shelfTitle)
        assertEquals(listOf("G", "F", "E", "D", "C"), s.shelf.map { it.entry.shown })
        assertEquals(listOf("6h", "5h", "4h", "3h", "2h"), s.shelf.map { it.caption })
    }

    @Test fun nothing_played_shows_newest_added() {
        val gb = system("GB", "Game Boy", game("GB", "Old", firstSeen = now - 40 * day), game("GB", "New", firstSeen = now - day), game("GB", "Mid", firstSeen = now - 10 * day))
        val s = consoleStats(listOf(gb), listed(gb), emptyMap(), now, weekStart = now - 6 * day, bios = null, cheatGames = 0)
        assertEquals("Newest added", s.shelfTitle)
        assertEquals(listOf("New", "Mid", "Old"), s.shelf.map { it.entry.shown })
        assertEquals(listOf("Yesterday", "Last week", "Last month"), s.shelf.map { it.caption })
        assertEquals(0L, s.totalMs)
        assertEquals(null, s.lastPlayed)
        assertEquals("3 never played", s.gamesNote)
        assertEquals("Achievements" to "0", s.fourth)
    }

    @Test fun bios_and_cheat_chips() {
        val ps = system("PS", "PlayStation", game("PS", "A", firstSeen = now - day), game("PS", "B", firstSeen = now - 2 * day), game("PS", "C", firstSeen = now - 30 * day))
        val s = consoleStats(listOf(ps), listed(ps), emptyMap(), now, weekStart = now - 6 * day, bios = BiosMissing.REQUIRED, cheatGames = 40)
        assertEquals(listOf("Added this week: 2", "Cheats for 40 games", "BIOS missing"), s.chips)
        val optional = consoleStats(listOf(ps), listed(ps), emptyMap(), now, weekStart = now, bios = BiosMissing.OPTIONAL, cheatGames = 1)
        assertEquals(listOf("Cheats for 1 game", "BIOS missing (optional)"), optional.chips)
        assertEquals(emptyList(), consoleStats(listOf(ps), listed(ps), emptyMap(), now, weekStart = now, bios = null, cheatGames = 0).chips)
    }

    @Test fun collection_stats_span_consoles_and_count_finished() {
        val gb = system("GB", "Game Boy", game("GB", "Links Awakening"), game("GB", "Tetris"))
        val snes = system("SFC", "Super Nintendo", game("SFC", "Mario World"))
        val gba = system("GBA", "Game Boy Advance", game("GBA", "Emerald"))
        val entries = listed(gb) + listed(snes) + listed(gba)
        val (links, tetris) = gb.games
        val info = mapOf(
            links.path to played(31 * hour, 20, now - day, RaProgress(50, 50, null)),
            snes.games[0].path to played(22 * hour, 8, now - 5 * day, RaProgress(96, 96, null)),
            tetris.path to played(30 * 60_000L, 1, now - 9 * day, RaProgress(3, 10, null)),
        )
        val s = collectionStats("Favourites", entries, info, now)
        assertEquals("Favourites", s.title)
        assertEquals("GB · SFC · GBA", s.subtitle)
        assertEquals(4, s.games)
        assertEquals("3 consoles", s.gamesNote)
        assertEquals(1, s.neverPlayed)
        assertEquals(31 * hour + 22 * hour + 30 * 60_000L, s.totalMs)
        assertEquals(29L, s.sessions)
        assertEquals(links, s.lastPlayed?.first?.game)
        assertEquals("Finished" to "2", s.fourth)
        assertEquals("all achievements", s.fourthNote)
        assertEquals("Most played", s.shelfTitle)
        assertEquals(listOf("GB · 31h", "SFC · 22h", "GB · 30m"), s.shelf.map { it.caption })
        assertEquals(listOf("Never played: 1"), s.chips)
    }

    @Test fun a_merged_console_lists_each_emulator_once_and_counts_one_console() {
        val a = system("GBA", "Game Boy Advance", game("GBA", "A"))
        val b = system("MGBA", "Game Boy Advance", game("MGBA", "B"))
        val c = consoleStats(listOf(a, b), listed(a) + listed(b), emptyMap(), now, weekStart = now, bios = null, cheatGames = 0)
        assertEquals(listOfNotNull(a.core?.name, b.core?.name).distinct().joinToString(" · "), c.subtitle)
        assertEquals("1 console", collectionStats("X", listed(a) + listed(b), emptyMap(), now).gamesNote)
    }
}
