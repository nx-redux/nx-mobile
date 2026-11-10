package com.nxredux.mobile.ui

import com.nxredux.mobile.gametime.FixedOffsetCalendar
import com.nxredux.mobile.scraper.GameInfoFiles
import com.nxredux.mobile.scraper.GameInfoStore
import com.nxredux.mobile.scraper.InfoResult
import com.nxredux.mobile.scraper.JobPause
import com.nxredux.mobile.scraper.LibraryGameRef
import com.nxredux.mobile.scraper.Scrape
import com.nxredux.mobile.scraper.ScreenScraperSource
import com.nxredux.mobile.scraper.SourceStop
import com.nxredux.mobile.scraper.SsGameInfo
import com.nxredux.mobile.scraper.SsQuota
import com.nxredux.mobile.storage.MemoryPrivateFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InfoFetcherTest {
    private class MemoryInfoFiles : GameInfoFiles {
        val texts = HashMap<String, String>()
        var writable = true
        override fun path(ref: LibraryGameRef) = "info/${ref.gamePath}"
        override suspend fun read(ref: LibraryGameRef) = texts[path(ref)]
        override suspend fun write(ref: LibraryGameRef, text: String): Boolean { if (writable) texts[path(ref)] = text; return writable }
    }

    private class Rig(scope: TestScope) {
        val files = MemoryInfoFiles()
        val store = GameInfoStore(files, MemoryPrivateFiles(), StandardTestDispatcher(scope.testScheduler))
        val asked = mutableListOf<String>()
        var answer: suspend (LibraryGameRef) -> InfoResult? = { InfoResult.Found(SsGameInfo("Blocks fall.", null, "1989", null, "en"), "42") }
        var enabled = true
        var runActive = false
        /** The last quota read and when; null: none. */
        var quota: Pair<SsQuota, Long>? = null
        /** Epoch millis: 10:00 on day 0 of a UTC calendar, plus virtual time. */
        var base = 10 * HOUR
        val now = { base + scope.testScheduler.currentTime }
        val fetched = mutableListOf<String>()
        val fetcher = InfoFetcher(
            CoroutineScope(StandardTestDispatcher(scope.testScheduler)), store,
            lookup = { ref -> asked += ref.gamePath; answer(ref) },
            enabled = { enabled }, runActive = { runActive }, quotaRead = { quota }, now = now,
            calendar = FixedOffsetCalendar(),
        ).also { f -> scope.backgroundScope.launch(StandardTestDispatcher(scope.testScheduler)) { f.fetched.collect { fetched += it } } }
    }

    private fun game(i: Int) = Scrape.homeGame("Game$i.gb")

    @Test fun only_a_game_rested_on_is_fetched() = runTest {
        val rig = Rig(this)
        for (i in 0 until 20) { rig.fetcher.follow(game(i)); advanceTimeBy(100) }
        advanceUntilIdle()
        assertEquals(listOf(game(19).gamePath), rig.asked)
    }

    @Test fun one_lookup_at_a_time() = runTest {
        val rig = Rig(this)
        var inFlight = 0
        var most = 0
        rig.answer = { inFlight++; most = maxOf(most, inFlight); delay(2_000); inFlight--; InfoResult.Missing }
        rig.fetcher.follow(game(1)); advanceTimeBy(700)   // its lookup starts
        rig.fetcher.follow(game(2)); advanceTimeBy(700)   // rested on while the first is still asking
        advanceUntilIdle()
        assertEquals(listOf(game(1).gamePath, game(2).gamePath), rig.asked)
        assertEquals(1, most)
    }

    @Test fun skipped_while_a_run_is_active() = runTest {
        val rig = Rig(this)
        rig.runActive = true
        rig.fetcher.follow(game(1)); advanceUntilIdle()
        assertTrue(rig.asked.isEmpty())
        rig.runActive = false
        rig.fetcher.follow(game(1)); advanceUntilIdle()   // highlighted again after the run
        assertEquals(listOf(game(1).gamePath), rig.asked)
    }

    @Test fun quota_exhausted_stops_until_the_next_day() = runTest {
        val rig = Rig(this)
        rig.answer = { throw SourceStop(ScreenScraperSource.DAILY_LIMIT) }
        rig.fetcher.follow(game(1)); advanceUntilIdle()
        assertEquals(1, rig.asked.size)
        rig.answer = { InfoResult.Missing }
        rig.base += 13 * HOUR   // 23:00, same day
        rig.fetcher.follow(game(2)); advanceUntilIdle()
        assertEquals(1, rig.asked.size)
        rig.base += HOUR + 1    // just past midnight
        rig.fetcher.follow(game(2)); advanceUntilIdle()
        assertEquals(2, rig.asked.size)

        // A quota read today already at its limit stops it the same way, without asking.
        val other = Rig(this)
        other.quota = SsQuota(20_000, 20_000) to other.now()
        other.fetcher.follow(game(1)); advanceUntilIdle()
        assertTrue(other.asked.isEmpty())
        other.base += 14 * HOUR + 1   // past midnight: yesterday's reading no longer stops it
        other.fetcher.follow(game(1)); advanceUntilIdle()
        assertEquals(1, other.asked.size)
        other.fetcher.follow(game(2)); advanceUntilIdle()
        assertEquals(2, other.asked.size)   // nor re-arms the stop on the next game
    }

    @Test fun unavailable_waits_a_pause_and_refusals_a_day() = runTest {
        val rig = Rig(this)
        rig.answer = { throw SourceStop(ScreenScraperSource.UNAVAILABLE) }
        rig.fetcher.follow(game(1)); advanceUntilIdle()
        rig.answer = { InfoResult.Missing }
        rig.fetcher.follow(game(1)); advanceUntilIdle()
        assertEquals(1, rig.asked.size)
        rig.base += InfoFetcher.RETRY_MS
        rig.fetcher.follow(game(1)); advanceUntilIdle()
        assertEquals(2, rig.asked.size)
        // A refusal that stays (this build refused) waits for the next day, as the daily limit does.
        rig.answer = { throw SourceStop(ScreenScraperSource.BUILD_REFUSED) }
        rig.fetcher.follow(game(2)); advanceUntilIdle()
        rig.answer = { InfoResult.Missing }
        rig.base += InfoFetcher.RETRY_MS
        rig.fetcher.follow(game(3)); advanceUntilIdle()
        assertEquals(3, rig.asked.size)
        rig.base += 14 * HOUR
        rig.fetcher.follow(game(3)); advanceUntilIdle()
        assertEquals(4, rig.asked.size)
    }

    // Found info that can't be written (a read-only folder) is not asked again for each highlight.
    @Test fun unstorable_info_is_not_asked_again() = runTest {
        val rig = Rig(this)
        rig.files.writable = false
        rig.fetcher.follow(game(1)); advanceUntilIdle()
        rig.fetcher.follow(null); rig.fetcher.follow(game(1)); advanceUntilIdle()
        assertEquals(1, rig.asked.size)
        assertTrue(rig.fetched.isEmpty())
    }

    @Test fun failed_lookups_are_not_misses_and_retry_after_a_pause() = runTest {
        for (fail in listOf<suspend (LibraryGameRef) -> InfoResult?>({ InfoResult.Failed }, { throw JobPause("No connection") })) {
            val rig = Rig(this)
            rig.answer = fail
            rig.fetcher.follow(game(1)); advanceUntilIdle()
            assertTrue(!rig.store.isMiss(game(1).gamePath))
            rig.answer = { InfoResult.Missing }
            rig.fetcher.follow(game(1)); advanceUntilIdle()
            assertEquals(1, rig.asked.size)   // paused
            rig.base += InfoFetcher.RETRY_MS
            rig.fetcher.follow(game(1)); advanceUntilIdle()
            assertEquals(2, rig.asked.size)
            assertTrue(rig.store.isMiss(game(1).gamePath))
            rig.fetcher.follow(game(1)); advanceUntilIdle()
            assertEquals(2, rig.asked.size)   // a miss is not asked again
        }
    }

    @Test fun disabled_setting_never_fetches() = runTest {
        val rig = Rig(this)
        rig.enabled = false
        rig.fetcher.follow(game(1)); advanceUntilIdle()
        assertTrue(rig.asked.isEmpty())
        // Turned off during the rest: still nothing.
        rig.enabled = true
        rig.fetcher.follow(game(1)); advanceTimeBy(300); rig.enabled = false; advanceUntilIdle()
        assertTrue(rig.asked.isEmpty())
    }

    @Test fun found_info_is_stored_and_announced() = runTest {
        val rig = Rig(this)
        rig.fetcher.follow(game(1)); advanceUntilIdle()
        assertEquals("Blocks fall.", rig.store.read(game(1))?.synopsis)
        assertEquals("42", rig.store.read(game(1))?.ssId)
        assertEquals(10 * HOUR + 600, rig.store.read(game(1))?.fetchedAt)   // stored when the rest ended
        assertEquals(listOf(game(1).gamePath), rig.fetched)
        rig.fetcher.follow(game(1)); advanceUntilIdle()
        assertEquals(1, rig.asked.size)   // stored: not asked again
    }

    private companion object { const val HOUR = 3_600_000L }
}
