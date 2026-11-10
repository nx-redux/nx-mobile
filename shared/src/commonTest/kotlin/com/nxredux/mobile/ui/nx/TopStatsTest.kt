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
import kotlin.test.assertTrue

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

    @Test fun console_numbers_are_games_play_time_and_achievements() {
        val gba = system("GBA", "Game Boy Advance", game("GBA", "Minish Cap"), game("GBA", "Emerald"), game("GBA", "Fusion"))
        val (minish, emerald) = gba.games
        val info = mapOf(
            minish.path to played(24 * hour, 10, now - day, RaProgress(30, 40, null)),
            emerald.path to played(19 * hour, 5, now - 3 * day, RaProgress(12, 12, null)),
        )
        val s = consoleStats(listOf(gba), listed(gba), info)
        assertEquals("Game Boy Advance", s.title)
        assertEquals(
            listOf(StatNumber("3", "games · 1 unplayed"), StatNumber("43 h", "played · 15 sessions"), StatNumber("42", "achievements")),
            s.numbers,
        )
        assertNull(s.bios)
    }

    @Test fun no_achievements_drop_the_third_number_and_all_played_drops_unplayed() {
        val gb = system("GB", "Game Boy", game("GB", "Tetris"))
        val s = consoleStats(listOf(gb), listed(gb), mapOf(gb.games[0].path to played(30 * 60_000L, 1, now)))
        assertEquals(listOf(StatNumber("1", "game"), StatNumber("30 m", "played · 1 session")), s.numbers)
    }

    @Test fun most_played_is_top_four_by_time() {
        val names = listOf("A", "B", "C", "D", "E", "F", "G")
        val gb = system("GB", "Game Boy", *names.map { game("GB", it) }.toTypedArray())
        // G most, then F..., A never played.
        val info = gb.games.drop(1).mapIndexed { i, g -> g.path to played((i + 1) * hour, 1, now) }.toMap()
        val s = consoleStats(listOf(gb), listed(gb), info)
        assertEquals(false, s.newest)
        assertEquals(listOf("G", "F", "E", "D"), s.shelf.map { it.entry.shown })
        assertEquals("G · 6 h · most played", heroCaption(s.shelf.first(), s.newest, now))
    }

    @Test fun nothing_played_shows_newest_added() {
        val gb = system("GB", "Game Boy", game("GB", "Old", firstSeen = now - 40 * day), game("GB", "New", firstSeen = now - day), game("GB", "Mid", firstSeen = now - 10 * day))
        val s = consoleStats(listOf(gb), listed(gb), emptyMap())
        assertEquals(true, s.newest)
        assertEquals(listOf("New", "Mid", "Old"), s.shelf.map { it.entry.shown })
        assertEquals("New · added yesterday · newest", heroCaption(s.shelf.first(), s.newest, now))
        assertEquals(listOf(StatNumber("3", "games · 3 unplayed"), StatNumber("0 m", "played · 0 sessions")), s.numbers)
    }

    @Test fun a_newest_game_without_a_first_seen_time_is_captioned_by_name() {
        val gb = system("GB", "Game Boy", game("GB", "Tetris"))
        val s = consoleStats(listOf(gb), listed(gb), emptyMap())
        assertEquals("Tetris · newest", heroCaption(s.shelf.first(), s.newest, now))
    }

    @Test fun the_bios_pill_shows_only_when_a_bios_is_missing() {
        val ps = system("PS", "PlayStation", game("PS", "A"))
        assertEquals(BiosMissing.REQUIRED, consoleStats(listOf(ps), listed(ps), emptyMap(), BiosMissing.REQUIRED).bios)
        assertEquals("BIOS missing", biosPill(BiosMissing.REQUIRED))
        assertEquals("BIOS missing (optional)", biosPill(BiosMissing.OPTIONAL))
        assertNull(biosPill(null))
    }

    @Test fun collection_numbers_span_consoles_and_count_finished() {
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
        val s = collectionStats("Favourites", entries, info)
        assertEquals("Favourites", s.title)
        assertEquals(
            listOf(StatNumber("4", "games · 3 consoles"), StatNumber("53 h", "played · 29 sessions"), StatNumber("2", "finished")),
            s.numbers,
        )
        assertEquals(listOf("Links Awakening", "Mario World", "Tetris"), s.shelf.map { it.entry.shown })
        assertEquals("Links Awakening · 31 h · most played", heroCaption(s.shelf.first(), s.newest, now))
    }

    @Test fun nothing_finished_drops_the_third_collection_number() {
        val gb = system("GB", "Game Boy", game("GB", "Tetris"))
        val s = collectionStats("X", listed(gb), mapOf(gb.games[0].path to played(2 * hour, 3, now, RaProgress(3, 10, null))))
        assertEquals(listOf(StatNumber("1", "game · 1 console"), StatNumber("2 h", "played · 3 sessions")), s.numbers)
    }

    @Test fun a_merged_console_counts_as_one_console() {
        val a = system("GBA", "Game Boy Advance", game("GBA", "A"))
        val b = system("MGBA", "Game Boy Advance", game("MGBA", "B"))
        assertEquals("games · 1 console", collectionStats("X", listed(a) + listed(b), emptyMap()).numbers[0].caption)
    }

    @Test fun a_tile_shows_the_screenshot_then_the_resume_picture_then_the_box() {
        assertEquals(ShelfImage.Shot("shot"), shelfImage("shot", "resume", "box"))
        assertEquals(ShelfImage.Shot("resume"), shelfImage(null, "resume", "box"))
        assertEquals(ShelfImage.Box("box"), shelfImage(null, null, "box"))
        assertEquals(ShelfImage.None, shelfImage(null, null, null))
    }

    // Caption line 22dp, gaps 6dp; strips are a third of the hero's width less the gaps, 16:9 like it.
    @Test fun the_shelf_drops_strips_then_the_picture_on_short_screens() {
        val roomy = assertNotNullFit(shelfFit(width = 360.dp, height = 400.dp))
        assertEquals(360.dp, roomy.heroWidth)
        assertTrue(roomy.strips)
        assertTrue(assertNotNullFit(shelfFit(width = 360.dp, height = 146.dp)).strips)   // a 90dp-tall picture with strips
        val short = assertNotNullFit(shelfFit(width = 360.dp, height = 140.dp))
        assertTrue(short.strips.not() && short.heroWidth * 9 / 16 >= 60.dp)
        assertNull(shelfFit(width = 360.dp, height = 70.dp))
        assertNull(shelfFit(width = 80.dp, height = 400.dp))   // too narrow for a 60dp-tall picture
    }

    private fun assertNotNullFit(f: ShelfFit?): ShelfFit = kotlin.test.assertNotNull(f)
}
