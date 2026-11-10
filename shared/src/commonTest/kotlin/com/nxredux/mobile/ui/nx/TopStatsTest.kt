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
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test fun the_console_line_is_games_play_time_and_last_played() {
        val gba = system("GBA", "Game Boy Advance", game("GBA", "Minish Cap"), game("GBA", "Emerald"), game("GBA", "Fusion"))
        val (minish, emerald) = gba.games
        val info = mapOf(
            minish.path to played(24 * hour, 10, now - 3 * day, RaProgress(30, 40, null)),
            emerald.path to played(19 * hour, 5, now - 2 * hour),
        )
        val s = consoleStats(listOf(gba), listed(gba), info)
        assertEquals("Game Boy Advance", s.title)
        assertEquals("3 games · 43 h played · last played today", statsLine(s, now))
        assertNull(s.bios)
    }

    @Test fun a_console_never_played_shows_its_games_alone() {
        val gb = system("GB", "Game Boy", game("GB", "Tetris"), game("GB", "Alleyway"))
        assertEquals("2 games", statsLine(consoleStats(listOf(gb), listed(gb), emptyMap()), now))
    }

    @Test fun one_game_under_an_hour_is_singular_in_minutes() {
        val gb = system("GB", "Game Boy", game("GB", "Tetris"))
        val s = consoleStats(listOf(gb), listed(gb), mapOf(gb.games[0].path to played(30 * 60_000L, 1, now - day)))
        assertEquals("1 game · 30 m played · last played yesterday", statsLine(s, now))
    }

    @Test fun most_played_is_top_three_by_time() {
        val names = listOf("A", "B", "C", "D", "E", "F", "G")
        val gb = system("GB", "Game Boy", *names.map { game("GB", it) }.toTypedArray())
        // G most, then F..., A never played.
        val info = gb.games.drop(1).mapIndexed { i, g -> g.path to played((i + 1) * hour, 1, now) }.toMap()
        val s = consoleStats(listOf(gb), listed(gb), info)
        assertEquals("MOST PLAYED", shelfLabel(s.shelf))
        assertEquals(listOf("G", "F", "E"), s.shelf.map { it.entry.shown })
        assertEquals("6 h", shelfCaption(s.shelf.first()))
    }

    @Test fun nothing_played_shows_newest_added() {
        val gb = system("GB", "Game Boy", game("GB", "Old", firstSeen = now - 40 * day), game("GB", "New", firstSeen = now - day), game("GB", "Mid", firstSeen = now - 10 * day))
        val s = consoleStats(listOf(gb), listed(gb), emptyMap())
        assertEquals("NEWEST ADDED", shelfLabel(s.shelf))
        assertEquals(listOf("New", "Mid", "Old"), s.shelf.map { it.entry.shown })
        assertEquals("Not played yet", shelfCaption(s.shelf.first()))
    }

    @Test fun one_played_game_is_filled_to_three_with_the_newest_added() {
        val gb = system(
            "GB", "Game Boy", game("GB", "Old", firstSeen = now - 40 * day), game("GB", "Played", firstSeen = now - 30 * day),
            game("GB", "New", firstSeen = now - day), game("GB", "Mid", firstSeen = now - 10 * day), game("GB", "Unknown"),
        )
        val played = gb.games[1]
        val s = consoleStats(listOf(gb), listed(gb), mapOf(played.path to played(10 * 60_000L, 1, now - day)))
        assertEquals(listOf("Played", "New", "Mid"), s.shelf.map { it.entry.shown })
        assertEquals("PLAYED & NEW", shelfLabel(s.shelf))
        assertEquals(listOf("10 m", "Not played yet", "Not played yet"), s.shelf.map { shelfCaption(it) })
    }

    @Test fun a_played_game_that_is_also_the_newest_is_not_listed_twice() {
        val gb = system("GB", "Game Boy", game("GB", "New", firstSeen = now - day), game("GB", "Old", firstSeen = now - 9 * day), game("GB", "Mid", firstSeen = now - 5 * day))
        val s = consoleStats(listOf(gb), listed(gb), mapOf(gb.games[0].path to played(hour, 1, now)))
        assertEquals(listOf("New", "Mid", "Old"), s.shelf.map { it.entry.shown })
        assertEquals("PLAYED & NEW", shelfLabel(s.shelf))
    }

    @Test fun three_played_games_fill_the_shelf_alone() {
        val gb = system("GB", "Game Boy", game("GB", "A", firstSeen = now - day), game("GB", "B"), game("GB", "C"), game("GB", "D", firstSeen = now))
        val info = gb.games.take(3).associate { it.path to played(hour, 1, now) }
        val s = consoleStats(listOf(gb), listed(gb), info)
        assertEquals(listOf("A", "B", "C"), s.shelf.map { it.entry.shown })
        assertEquals("MOST PLAYED", shelfLabel(s.shelf))
    }

    @Test fun a_console_with_two_games_shows_two_tiles() {
        val gb = system("GB", "Game Boy", game("GB", "A"), game("GB", "B"))
        val s = consoleStats(listOf(gb), listed(gb), mapOf(gb.games[0].path to played(hour, 1, now)))
        assertEquals(2, s.shelf.size)
        assertEquals("PLAYED & NEW", shelfLabel(s.shelf))
    }

    // The file's own date (a dump from 2008) says nothing about when the player added it.
    @Test fun a_filled_tile_says_not_played_yet_whatever_its_first_seen_time() {
        for (seen in listOf(null, now - 18 * 365 * day)) {
            val gb = system("GB", "Game Boy", game("GB", "Tetris", firstSeen = seen))
            val s = consoleStats(listOf(gb), listed(gb), emptyMap())
            assertEquals("Not played yet", shelfCaption(s.shelf.first()))
        }
    }

    @Test fun the_bios_pill_shows_only_when_a_bios_is_missing() {
        val ps = system("PS", "PlayStation", game("PS", "A"))
        assertEquals(BiosMissing.REQUIRED, consoleStats(listOf(ps), listed(ps), emptyMap(), BiosMissing.REQUIRED).bios)
        assertEquals("BIOS missing", biosPill(BiosMissing.REQUIRED))
        assertEquals("BIOS missing (optional)", biosPill(BiosMissing.OPTIONAL))
        assertNull(biosPill(null))
    }

    @Test fun the_collection_line_adds_the_consoles_it_spans() {
        val gb = system("GB", "Game Boy", game("GB", "Links Awakening"), game("GB", "Tetris"))
        val snes = system("SFC", "Super Nintendo", game("SFC", "Mario World"))
        val gba = system("GBA", "Game Boy Advance", game("GBA", "Emerald"))
        val entries = listed(gb) + listed(snes) + listed(gba)
        val (links, tetris) = gb.games
        val info = mapOf(
            links.path to played(31 * hour, 20, now - 5 * day),
            snes.games[0].path to played(22 * hour, 8, now - day),
            tetris.path to played(30 * 60_000L, 1, now - 9 * day),
        )
        val s = collectionStats("Favourites", entries, info)
        assertEquals("Favourites", s.title)
        assertEquals("4 games · 3 consoles · 53 h played · last played yesterday", statsLine(s, now))
        assertEquals(listOf("Links Awakening", "Mario World", "Tetris"), s.shelf.map { it.entry.shown })
        assertEquals("31 h", shelfCaption(s.shelf.first()))
    }

    @Test fun a_collection_never_played_on_one_console_is_singular() {
        val gb = system("GB", "Game Boy", game("GB", "Tetris"))
        assertEquals("1 game · 1 console", statsLine(collectionStats("X", listed(gb), emptyMap()), now))
    }

    @Test fun a_merged_console_counts_as_one_console() {
        val a = system("GBA", "Game Boy Advance", game("GBA", "A"))
        val b = system("MGBA", "Game Boy Advance", game("MGBA", "B"))
        assertEquals("2 games · 1 console", statsLine(collectionStats("X", listed(a) + listed(b), emptyMap()), now))
    }

    @Test fun a_tile_shows_the_screenshot_then_the_resume_picture_then_the_box() {
        assertEquals(ShelfImage.Shot("shot"), shelfImage("shot", "resume", "box"))
        assertEquals(ShelfImage.Shot("resume"), shelfImage(null, "resume", "box"))
        assertEquals(ShelfImage.Box("box"), shelfImage(null, null, "box"))
        assertEquals(ShelfImage.Placeholder, shelfImage(null, null, null))
    }

    // Three 16:9 tiles over 92% of the width (two 4% gaps): 826dp wide, each 142.5dp tall. Over them a 24dp breath and
    // the 28dp label; under them the 52dp captions.
    @Test fun the_shelf_drops_its_captions_then_itself_on_short_screens() {
        assertEquals(ShelfFit.CAPTIONED, shelfFit(width = 826.dp, height = 400.dp))
        assertEquals(ShelfFit.CAPTIONED, shelfFit(width = 826.dp, height = 247.dp))
        assertEquals(ShelfFit.PICTURES, shelfFit(width = 826.dp, height = 246.dp))
        assertEquals(ShelfFit.PICTURES, shelfFit(width = 826.dp, height = 195.dp))
        assertEquals(ShelfFit.NONE, shelfFit(width = 826.dp, height = 194.dp))
        assertEquals(ShelfFit.CAPTIONED, shelfFit(width = 400.dp, height = 174.dp))   // a narrower screen's smaller tiles fit sooner
    }

    @Test fun a_game_played_under_a_minute_reads_under_one_minute() {
        val gb = system("gb", "Game Boy", game("gb", "A"))
        val s = consoleStats(listOf(gb), listed(gb), mapOf(gb.games[0].path to played(20_000L, 1, now - day)))
        assertEquals("1 game · < 1 m played · last played yesterday", statsLine(s, now))
        assertEquals("< 1 m", shelfCaption(s.shelf.first()))
        assertEquals("< 1 m", hoursText(59_999L))
        assertEquals("1 m", hoursText(60_000L))
    }

    @Test fun an_empty_shelf_does_not_claim_most_played() {
        assertEquals("NEWEST ADDED", shelfLabel(emptyList()))
    }
}
