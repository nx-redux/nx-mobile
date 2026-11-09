package com.nxredux.mobile.scraper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The shared paced GET's error table, the held stop and the one percent-encoder. */
class PacedGetTest {
    private class Rig {
        var clock = 0L
        val waits = mutableListOf<Long>()
        val http = FakeHttp().apply { clock = { this@Rig.clock } }
        val get = PacedGet(Pacer(0L, { clock }) { clock += it }, http, { waits += it; clock += it }, TRIES, BACKOFF, BUSY)
    }

    private companion object {
        const val TRIES = 3
        val BACKOFF = listOf(2_000L, 5_000L)
        const val BUSY = 5_000L
    }

    @Test fun okReturnsTheBody() = runTest {
        val r = Rig()
        r.http.on("x", HttpResult(200, "body".encodeToByteArray()))
        assertContentEquals("body".encodeToByteArray(), r.get.get("https://h/x", busyNote = { "busy" }))
        assertEquals(1, r.http.calls.size)
        assertTrue(r.waits.isEmpty())
    }

    @Test fun offlinePausesAfterTries() = runTest {
        val r = Rig()
        r.http.on("x", HttpResult(-1, null))
        assertEquals(ScrapeEngine.NO_CONNECTION, assertFailsWith<JobPause> { r.get.get("https://h/x", busyNote = { "busy" }) }.reason)
        assertEquals(TRIES, r.http.calls.size)
        assertEquals(BACKOFF, r.waits)
    }

    @Test fun offlineThenOkReturnsTheBody() = runTest {
        val r = Rig()
        r.http.on("x", HttpResult(-1, null), HttpResult(200, byteArrayOf(1)))
        assertContentEquals(byteArrayOf(1), r.get.get("https://h/x", busyNote = { "busy" }))
        assertEquals(listOf(BACKOFF[0]), r.waits)
    }

    @Test fun busyTwiceStopsWithTheStatusNote() = runTest {
        for (status in listOf(429, 500, 503)) {
            val r = Rig()
            r.http.on("x", HttpResult(status, null))
            val stop = assertFailsWith<SourceStop> { r.get.get("https://h/x", busyNote = { "busy $it" }) }
            assertEquals("busy $status", stop.note)
            assertEquals(2, r.http.calls.size)
            assertEquals(listOf(BUSY), r.waits)
        }
    }

    @Test fun busyOnceThenOkReturnsTheBody() = runTest {
        val r = Rig()
        r.http.on("x", HttpResult(502, null), HttpResult(200, byteArrayOf(2)))
        assertContentEquals(byteArrayOf(2), r.get.get("https://h/x", busyNote = { "busy" }))
        assertEquals(listOf(BUSY), r.waits)
    }

    @Test fun ownStopStatusesStopAtOnceOthersAreNull() = runTest {
        val r = Rig()
        r.http.on("refused", HttpResult(403, null))
        val stop = { s: Int -> if (s == 403) "refused" else null }
        assertEquals("refused", assertFailsWith<SourceStop> { r.get.get("https://h/refused", busyNote = { "busy" }, stop = stop) }.note)
        assertEquals(1, r.http.calls.size)
        assertNull(r.get.get("https://h/missing", busyNote = { "busy" }, stop = stop))   // FakeHttp's 404
    }

    @Test fun headersAndBeforeRunOnEveryTry() = runTest {
        val r = Rig()
        r.http.on("x", HttpResult(-1, null), HttpResult(200, byteArrayOf(3)))
        var before = 0
        r.get.get("https://h/x", mapOf("A" to "b"), busyNote = { "busy" }, before = { before++ })
        assertEquals(2, before)
        assertEquals(listOf(mapOf("A" to "b"), mapOf("A" to "b")), r.http.headers)
    }

    @Test fun pendingStopHeldOnlyWithHits() = runTest {
        val p = PendingStop()
        assertFailsWith<SourceStop> { p.hold(SourceStop("cap"), emptyList()) }
        p.throwIfHeld()   // nothing held: no throw
        p.hold(SourceStop("cap"), listOf(ArtHit(ArtSlot.BOX_2D, "u", ArtOrigin(SourceId.TGDB, "1", "2"))))
        assertTrue(p.held())
        assertEquals("cap", assertFailsWith<SourceStop> { p.throwIfHeld() }.note)
        p.clear()
        assertEquals(false, p.held())
    }

    @Test fun jobAnswersKeepNoEmptyBodyAndStayUnderTheirCharacterCap() = runTest {
        val answers = JobAnswers(max = 10, maxChars = 10)
        var fetches = 0
        val fetch: suspend (String) -> String? = { k -> fetches++; if (k == "empty") "" else "x".repeat(4) }
        answers.get("empty", fetch); answers.get("empty", fetch)
        assertEquals(2, fetches)
        for (k in listOf("a", "b", "c")) answers.get(k, fetch)   // 12 characters: the oldest goes
        assertEquals(false, answers.get("a", fetch).second)
        assertEquals(true, answers.get("c", fetch).second)
        assertEquals(false, answers.get("big") { "y".repeat(11) }.second)
        assertEquals(false, answers.get("big") { "y".repeat(11) }.second)   // larger than the cap: never kept
    }

    @Test fun percentEncodeSpaces() {
        assertEquals("a+b%2Fc%C3%A9~", percentEncode("a b/cé~", spaceAsPlus = true))
        assertEquals("a%20b%2Fc%C3%A9~", percentEncode("a b/cé~", spaceAsPlus = false))
    }
}
