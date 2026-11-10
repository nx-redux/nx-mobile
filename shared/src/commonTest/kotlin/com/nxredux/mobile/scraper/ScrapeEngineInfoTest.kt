package com.nxredux.mobile.scraper

import com.nxredux.mobile.scraper.Scrape.OK_PNG
import com.nxredux.mobile.storage.MemoryPrivateFiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An Artwork run's game info: from the art's own answer, or one info-only lookup for a game whose art is complete. */
class ScrapeEngineInfoTest {
    private class Rig(scope: TestScope, withInfo: Boolean = true) {
        val http = FakeHttp().apply { clock = { scope.testScheduler.currentTime } }
        val files = MemoryPrivateFiles()
        val engine = ssEngine(
            http, FakeFitter(), FakeHasher(), Scrape.homeTree(), Scrape.home, { ScraperAuth("DEV", "DEVPW") },
            { scope.testScheduler.currentTime }, { delay(it) }, onArt = { _, _ -> }, onWritten = {},
            infoFiles = if (withInfo) files else null,
        )
        val store get() = engine.info!!

        suspend fun run(vararg games: LibraryGameRef) =
            engine.run(ScrapeState(items = games.map { Scrape.item(it) }), { p -> games.firstOrNull { it.gamePath == p } }) {}
    }

    private val tetris = Scrape.homeGame("Tetris.gb")
    private val tetrisHasArt = Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART)

    /** A jeuInfos answer (game id 42) with a synopsis and a year, and the given media. */
    private fun withInfo(vararg media: Pair<String, String>) = Scrape.json(
        """{"response":{"jeu":{"id":"42","synopsis":[{"langue":"en","text":"Blocks fall."}],"dates":[{"region":"us","text":"1989-06-14"}],""" +
            """"medias":[${media.joinToString(",") { (t, u) -> """{"type":"$t","region":"us","url":"$u"}""" }}]}}}""",
    )

    @Test fun a_run_stores_info_from_the_art_lookup_without_extra_requests() = runTest {
        val calls = listOf(true, false).map { info ->
            val rig = Rig(this, withInfo = info)
            rig.http.on("romnom=Tetris.gb", withInfo("ss" to "https://m/s.png", "box-2D" to "https://m/b.png"))
            rig.http.on("https://m/", OK_PNG)
            assertEquals(ItemStatus.DONE, rig.run(tetris).items.single().status)
            if (info) {
                val stored = assertNotNull(rig.store.read(tetris))
                assertEquals("Blocks fall.", stored.synopsis)
                assertEquals("1989", stored.year)
                assertEquals("42", stored.ssId)
            }
            rig.http.calls.toList()
        }
        assertEquals(calls[1], calls[0])
    }

    @Test fun an_art_complete_game_gets_one_info_only_lookup_and_no_downloads() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", withInfo("ss" to "https://m/s.png", "box-2D" to "https://m/b.png"))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(tetrisHasArt)
        assertEquals(ItemStatus.SKIPPED to ScrapeEngine.HAS_ART, end.items.single().let { it.status to it.reason })
        assertEquals(1, rig.http.count("jeuInfos"))
        assertEquals(0, rig.http.count("https://m/"))
        assertEquals("Blocks fall.", rig.store.read(tetris)?.synopsis)
    }

    @Test fun not_found_marks_a_miss_and_errors_do_not() = runTest {
        // Not found (try 1 and try 2 both 404), and found without any info field: misses.
        for (answer in listOf<HttpResult?>(null, Scrape.found("ss" to "https://m/s.png"))) {
            val rig = Rig(this)
            answer?.let { rig.http.on("romnom=Tetris.gb", it) }
            rig.run(tetrisHasArt)
            assertTrue(rig.store.isMiss(tetris.gamePath), "$answer")
            assertNull(rig.store.read(tetris))
        }
        // A 5xx, no connection and an unreadable answer: never a miss.
        for (answer in listOf(HttpResult(500, null), HttpResult(-1, null), Scrape.json(ScraperFixtures.GARBAGE))) {
            val rig = Rig(this)
            rig.http.on("romnom=Tetris.gb", answer)
            rig.run(tetrisHasArt)
            assertFalse(rig.store.isMiss(tetris.gamePath), "${answer.status}")
            assertNull(rig.store.read(tetris))
        }
    }

    @Test fun a_quota_stop_in_an_info_lookup_stops_screenscraper_for_the_run() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=", HttpResult(430, null))
        val end = rig.run(tetrisHasArt, Scrape.homeGame("Zelda.gb", have = Scrape.HAS_ART))
        assertEquals(ScreenScraperSource.DAILY_LIMIT, end.notes[SourceId.SS])
        assertEquals(2, rig.http.count("jeuInfos"))   // the first game's 430 and its one retry; none for the second
        assertFalse(rig.store.isMiss(tetris.gamePath))
    }

    @Test fun a_no_connection_info_lookup_pauses_and_the_game_is_tried_again() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", HttpResult(-1, null))
        val end = rig.run(tetrisHasArt)
        assertEquals(JobPhase.PAUSED to ScrapeEngine.NO_CONNECTION, end.phase to end.pauseReason)
        assertEquals(ItemStatus.WAITING, end.items.single().status)
    }

    @Test fun a_manual_run_clears_the_miss_and_tries_again() = runTest {
        val rig = Rig(this)
        rig.store.markMiss(tetris.gamePath)
        rig.http.on("romnom=Tetris.gb", withInfo())
        rig.run(tetrisHasArt)
        assertEquals(1, rig.http.count("jeuInfos"))
        assertFalse(rig.store.isMiss(tetris.gamePath))
        assertEquals("Blocks fall.", rig.store.read(tetris)?.synopsis)
    }

    @Test fun an_existing_info_file_is_not_fetched_again() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", withInfo())
        rig.run(tetrisHasArt)
        rig.run(tetrisHasArt)
        assertEquals(1, rig.http.count("jeuInfos"))
        // A file written before the run is not fetched either.
        val other = Rig(this)
        assertTrue(other.store.write(tetris, StoredGameInfo("Old.", null, null, null, "en", "42", 1L)))
        other.run(tetrisHasArt)
        assertEquals(0, other.http.count("jeuInfos"))
    }
}
