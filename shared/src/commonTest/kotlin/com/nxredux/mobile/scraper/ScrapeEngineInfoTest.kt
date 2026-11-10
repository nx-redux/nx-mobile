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

/** An Artwork run's game info: only from the art lookup's own answer, never a lookup of its own. */
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

        suspend fun run(vararg games: LibraryGameRef, retryMisses: Boolean = false) =
            engine.run(ScrapeState(items = games.map { Scrape.item(it) }), { p -> games.firstOrNull { it.gamePath == p } }, retryMisses = retryMisses) {}
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

    @Test fun an_art_complete_game_is_not_asked_for_its_info() = runTest {
        for (retry in listOf(false, true)) {
            val rig = Rig(this)
            rig.http.on("romnom=Tetris.gb", withInfo("ss" to "https://m/s.png", "box-2D" to "https://m/b.png"))
            val end = rig.run(tetrisHasArt, retryMisses = retry)
            assertEquals(ItemStatus.SKIPPED to ScrapeEngine.HAS_ART, end.items.single().let { it.status to it.reason })
            assertTrue(rig.http.calls.isEmpty(), "${rig.http.calls}")
            assertNull(rig.store.read(tetris))
        }
    }

    @Test fun not_found_marks_a_miss_and_errors_do_not() = runTest {
        // Not found (try 1 and try 2 both 404), and found without any info field: misses.
        for (answer in listOf<HttpResult?>(null, Scrape.found("ss" to "https://m/s.png"))) {
            val rig = Rig(this)
            answer?.let { rig.http.on("romnom=Tetris.gb", it) }
            rig.http.on("https://m/", OK_PNG)
            rig.run(tetris)
            assertTrue(rig.store.isMiss(tetris.gamePath), "$answer")
            assertNull(rig.store.read(tetris))
        }
        // A 5xx, no connection and an unreadable answer: never a miss.
        for (answer in listOf(HttpResult(500, null), HttpResult(-1, null), Scrape.json(ScraperFixtures.GARBAGE))) {
            val rig = Rig(this)
            rig.http.on("romnom=Tetris.gb", answer)
            rig.run(tetris)
            assertFalse(rig.store.isMiss(tetris.gamePath), "${answer.status}")
            assertNull(rig.store.read(tetris))
        }
    }

    @Test fun only_a_run_that_retries_misses_clears_one_before_the_lookup() = runTest {
        // An unreadable answer neither marks nor clears a miss: what is left is the run's own doing.
        for (retry in listOf(false, true)) {
            val rig = Rig(this)
            rig.store.markMiss(tetris.gamePath)
            rig.http.on("romnom=Tetris.gb", Scrape.json(ScraperFixtures.GARBAGE))
            rig.run(tetris, retryMisses = retry)
            assertTrue(rig.http.count("romnom=Tetris.gb") > 0, "retry=$retry")
            assertEquals(!retry, rig.store.isMiss(tetris.gamePath), "retry=$retry")
        }
    }

    @Test fun info_in_the_art_answer_clears_a_miss_in_any_run() = runTest {
        for (retry in listOf(false, true)) {
            val rig = Rig(this)
            rig.store.markMiss(tetris.gamePath)
            rig.http.on("romnom=Tetris.gb", withInfo("ss" to "https://m/s.png", "box-2D" to "https://m/b.png"))
            rig.http.on("https://m/", OK_PNG)
            rig.run(tetris, retryMisses = retry)
            assertFalse(rig.store.isMiss(tetris.gamePath), "retry=$retry")
            assertEquals("Blocks fall.", rig.store.read(tetris)?.synopsis)
        }
    }
}
