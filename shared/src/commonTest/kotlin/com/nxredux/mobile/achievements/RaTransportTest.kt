package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class RaTransportTest {
    private val hash = "0123456789abcdef0123456789abcdef"
    private fun setup(mode: RaSessionMode = RaSessionMode.ONLINE): Triple<RaTransport, FakeRaStore, FakeRaHttp> {
        val store = FakeRaStore(); val http = FakeRaHttp()
        val t = RaTransport(store, http, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED)) { 5_000L }
        t.mode = mode
        return Triple(t, store, http)
    }

    @Test fun online_writes_success_bodies_through_by_kind() = runTest {
        val (t, store, http) = setup()
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok"}""")
        http.on("gameid", body = """{"Success":true,"GameID":42}""")
        http.on("achievementsets", body = setsJson(42))
        http.on("startsession", body = """{"Success":true,"Unlocks":[]}""")
        http.on("ping", body = """{"Success":true}""")
        t.call(RaRequests.URL, RaRequests.login2("me", "tok")); t.call(RaRequests.URL, RaRequests.gameId(hash))
        t.call(RaRequests.URL, RaRequests.achievementSets("me", "tok", hash)); t.call(RaRequests.URL, RaRequests.startSession("me", "tok", 42, hash))
        t.call(RaRequests.URL, "r=ping&u=me&t=tok&g=42")
        assertEquals(setOf(RaPaths.login(), RaPaths.gameId(hash), RaPaths.sets(hash), RaPaths.summary(hash), RaPaths.session(42)), store.files.keys)   // a set reply writes its summary
    }
    @Test fun online_does_not_cache_failures_or_non_200() = runTest {
        val (t, store, http) = setup()
        http.on("login2", body = """{"Success":false,"Error":"Invalid"}"""); http.on("gameid", status = 500, body = """{"Success":true,"GameID":1}""")
        t.call(RaRequests.URL, RaRequests.login2("me", "tok")); t.call(RaRequests.URL, RaRequests.gameId(hash))
        assertTrue(store.files.isEmpty())
    }
    @Test fun online_network_error_is_status_zero_except_award_which_is_journaled() = runTest {
        val (t, store, http) = setup(); http.offline = true
        assertEquals(0, t.call(RaRequests.URL, RaRequests.gameId(hash)).status)
        val r = t.call(RaRequests.URL, "r=awardachievement&u=me&t=tok&a=7&h=0&m=$hash&v=x")
        assertEquals(200, r.status); assertEquals("""{"Success":true,"AchievementID":7}""", r.body)
        assertEquals(listOf(RaUnlock("me", 7, hash, 5L)), RaJournal(store, RaPaths.JOURNAL).entries())
    }
    @Test fun online_award_without_a_definitive_answer_is_journaled() = runTest {
        val award = "r=awardachievement&u=me&t=tok&a=7&h=0&m=$hash&v=x"
        for ((status, body) in listOf(502 to "<html>Bad gateway</html>", 503 to "", 504 to "", 429 to "Too Many Requests",
            522 to "<html>timed out</html>", 500 to """{"Success":false,"Error":"Server error"}""", 200 to "<html>maintenance</html>")) {
            val (t, store, http) = setup()
            http.on("awardachievement", status = status, body = body)
            val r = t.call(RaRequests.URL, award)
            assertEquals(200, r.status, "status $status"); assertEquals("""{"Success":true,"AchievementID":7}""", r.body, "status $status")
            assertEquals(listOf(RaUnlock("me", 7, hash, 5L)), RaJournal(store, RaPaths.JOURNAL).entries(), "status $status")
        }
    }
    @Test fun online_award_with_a_definitive_answer_is_passed_through() = runTest {
        val award = "r=awardachievement&u=me&t=tok&a=7&h=0&m=$hash&v=x"
        for ((status, body) in listOf(200 to """{"Success":true,"AchievementID":7,"Score":10,"SoftcoreScore":5}""",
            200 to """{"Success":false,"Error":"User already has this achievement unlocked."}""",
            401 to """{"Success":false,"Status":401,"Code":"invalid_credentials","Error":"Invalid token"}""")) {
            val (t, store, http) = setup()
            http.on("awardachievement", status = status, body = body)
            assertEquals(RaResponse(status, body), t.call(RaRequests.URL, award))
            assertTrue(RaJournal(store, RaPaths.JOURNAL).entries().isEmpty())
        }
    }
    @Test fun online_award_is_journaled_before_its_post_and_cleared_on_a_definitive_answer() = runTest {
        val (t, store, http) = setup()
        var duringPost: List<RaUnlock>? = null
        http.responders["awardachievement"] = {
            duringPost = RaJournal(store, RaPaths.JOURNAL).entries()
            RaResponse(200, """{"Success":true,"AchievementID":7,"Score":10,"SoftcoreScore":5}""")
        }
        t.call(RaRequests.URL, "r=awardachievement&u=me&t=tok&a=7&h=0&m=$hash&v=x")
        assertEquals(listOf(RaUnlock("me", 7, hash, 5L)), duringPost)
        assertTrue(RaJournal(store, RaPaths.JOURNAL).entries().isEmpty())
    }
    @Test fun online_award_still_goes_out_when_the_journal_cannot_be_written() = runTest {
        val (t, store, http) = setup()
        store.failAppend = true
        val ok = """{"Success":true,"AchievementID":7,"Score":10,"SoftcoreScore":5}"""
        http.on("awardachievement", body = ok)
        assertEquals(RaResponse(200, ok), t.call(RaRequests.URL, "r=awardachievement&u=me&t=tok&a=7&h=0&m=$hash&v=x"))
        assertEquals(1, http.posts.size)
    }
    @Test fun online_award_neither_journaled_nor_answered_is_a_hard_failure() = runTest {
        val (t, store, http) = setup()
        store.failAppend = true; http.offline = true
        kotlin.test.assertFailsWith<RaStoreException> { t.call(RaRequests.URL, "r=awardachievement&u=me&t=tok&a=7&h=0&m=$hash&v=x") }
    }
    @Test fun online_award_answer_keeps_an_older_equal_journal_entry() = runTest {
        val (t, store, http) = setup()
        val offline = RaUnlock("me", 7, hash, 1L)   // an offline unlock still waiting for the sync
        RaJournal(store, RaPaths.JOURNAL).append(offline)
        http.on("awardachievement", body = """{"Success":true,"AchievementID":7,"Score":10,"SoftcoreScore":5}""")
        t.call(RaRequests.URL, "r=awardachievement&u=me&t=tok&a=7&h=0&m=$hash&v=x")
        assertEquals(listOf(offline), RaJournal(store, RaPaths.JOURNAL).entries())
    }
    @Test fun online_award_whose_post_never_returns_stays_journaled() = runTest {
        val (t, store, http) = setup()
        http.responders["awardachievement"] = { kotlinx.coroutines.awaitCancellation() }   // the process ends mid-POST
        val job = launch { t.call(RaRequests.URL, "r=awardachievement&u=me&t=tok&a=7&h=0&m=$hash&v=x") }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(RaUnlock("me", 7, hash, 5L)), RaJournal(store, RaPaths.JOURNAL).entries())
        job.cancel()
        assertEquals(listOf(RaUnlock("me", 7, hash, 5L)), RaJournal(store, RaPaths.JOURNAL).entries())
    }
    @Test fun online_game_lookups_fall_back_to_the_cache_without_a_definitive_answer() = runTest {
        val (t, store, http) = setup()
        store.put(RaPaths.gameId(hash), """{"Success":true,"GameID":42}"""); store.put(RaPaths.sets(hash), """{"Success":true,"ID":42}""")
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[{"ID":1,"When":10}],"HardcoreUnlocks":[],"ServerNow":11}""")
        http.on("gameid", status = 502, body = "<html/>"); http.on("achievementsets", status = 503, body = "")
        http.on("startsession", status = 200, body = "<html/>")
        assertEquals("""{"Success":true,"GameID":42}""", t.call(RaRequests.URL, RaRequests.gameId(hash)).body)
        assertEquals("""{"Success":true,"ID":42}""", t.call(RaRequests.URL, RaRequests.achievementSets("me", "tok", hash)).body)
        val s = RaJson.parse(SessionBody.serializer(), t.call(RaRequests.URL, RaRequests.startSession("me", "tok", 42, hash)).body)!!
        assertEquals(listOf(SessionUnlock(1, 10L)), s.Unlocks)
        http.offline = true
        assertEquals("""{"Success":true,"GameID":42}""", t.call(RaRequests.URL, RaRequests.gameId(hash)).body)
    }
    @Test fun online_game_lookups_without_a_cache_pass_the_error_on() = runTest {
        val (t, _, http) = setup()
        http.on("gameid", status = 502, body = "<html/>")
        assertEquals(RaResponse(502, "<html/>"), t.call(RaRequests.URL, RaRequests.gameId(hash)))
        http.on("startsession", status = 503, body = "")
        assertEquals(503, t.call(RaRequests.URL, RaRequests.startSession("me", "tok", 42, hash)).status)
    }
    @Test fun offline_login_needs_matching_cached_user() = runTest {
        val (t, store, http) = setup(RaSessionMode.OFFLINE)
        store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","Score":1,"SoftcoreScore":2}""")
        assertEquals(store.text(RaPaths.login()), t.call(RaRequests.URL, RaRequests.login2("me", "tok")).body)
        assertEquals("""{"Success":false,"Error":"Offline mode"}""", t.call(RaRequests.URL, RaRequests.login2("you", "tok")).body)
        assertTrue(http.posts.isEmpty())
    }
    @Test fun offline_game_lookups_come_from_cache_or_say_unknown() = runTest {
        val (t, store, _) = setup(RaSessionMode.OFFLINE)
        assertEquals("""{"Success":true,"GameID":0}""", t.call(RaRequests.URL, RaRequests.gameId(hash)).body)
        store.put(RaPaths.gameId(hash), """{"Success":true,"GameID":42}"""); store.put(RaPaths.sets(hash), """{"Success":true,"ID":42}""")
        assertEquals("""{"Success":true,"GameID":42}""", t.call(RaRequests.URL, RaRequests.gameId(hash)).body)
        assertEquals("""{"Success":true,"ID":42}""", t.call(RaRequests.URL, RaRequests.achievementSets("me", "tok", hash)).body)
        assertEquals("""{"Success":false,"Error":"Offline mode"}""", t.call(RaRequests.URL, RaRequests.achievementSets("me", "tok", "ffff")).body)
    }
    @Test fun offline_startsession_merges_only_this_users_journal() = runTest {
        val (t, store, _) = setup(RaSessionMode.OFFLINE)
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[{"ID":1,"When":10}],"HardcoreUnlocks":[],"ServerNow":11}""")
        RaJournal(store, RaPaths.JOURNAL).apply { append(RaUnlock("me", 2, hash, 20L)); append(RaUnlock("you", 3, hash, 21L)); append(RaUnlock("me", 1, hash, 22L)) }
        val body = t.call(RaRequests.URL, RaRequests.startSession("me", "tok", 42, hash)).body
        val s = RaJson.parse(SessionBody.serializer(), body)!!
        assertEquals(listOf(SessionUnlock(1, 10L), SessionUnlock(2, 20L)), s.Unlocks)
    }
    @Test fun confirmed_unlocks_are_merged_into_a_stale_session() = runTest {
        val (t, store, _) = setup(RaSessionMode.OFFLINE)
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[],"HardcoreUnlocks":[]}""")
        RaJournal(store, RaPaths.CONFIRMED).append(RaUnlock("me", 5, hash, 30L))
        val s = RaJson.parse(SessionBody.serializer(), t.call(RaRequests.URL, RaRequests.startSession("me", "tok", 42, hash)).body)!!
        assertEquals(listOf(SessionUnlock(5, 30L)), s.Unlocks)
    }
    @Test fun offline_startsession_without_cache_is_synthetic_and_needs_the_sets() = runTest {
        val (t, store, _) = setup(RaSessionMode.OFFLINE)
        assertEquals("""{"Success":false,"Error":"Offline mode"}""", t.call(RaRequests.URL, RaRequests.startSession("me", "tok", 42, hash)).body)
        store.put(RaPaths.sets(hash), """{"Success":true,"ID":42}""")
        assertEquals("""{"Success":true,"Unlocks":[],"HardcoreUnlocks":[],"ServerNow":5}""", t.call(RaRequests.URL, RaRequests.startSession("me", "tok", 42, hash)).body)
    }
    @Test fun offline_award_journals_and_ping_succeeds_and_others_fail() = runTest {
        val (t, store, _) = setup(RaSessionMode.OFFLINE)
        assertEquals("""{"Success":true,"AchievementID":7}""", t.call(RaRequests.URL, "r=awardachievement&u=me&t=tok&a=7&h=0&m=$hash&v=x").body)
        assertEquals(1, RaJournal(store, RaPaths.JOURNAL).count("me"))
        assertEquals("""{"Success":true}""", t.call(RaRequests.URL, "r=ping&u=me&t=tok&g=42").body)
        assertEquals("""{"Success":false,"Error":"Offline mode"}""", t.call(RaRequests.URL, "r=submitlbentry&u=me&t=tok&i=1").body)
    }
    @Test fun disabled_answers_offline_for_everything() = runTest {
        val (t, _, _) = setup(RaSessionMode.DISABLED)
        assertEquals("""{"Success":false,"Error":"Offline mode"}""", t.call(RaRequests.URL, "r=ping&u=me&t=tok").body)
    }
    @Test fun session_start_picks_the_mode() = runTest {
        val delays = ArrayList<Long>()
        suspend fun start(connected: Boolean, cached: Boolean, online: Boolean): RaSessionMode {
            val (t, store, http) = setup()
            if (cached) store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok"}""")
            http.offline = !online
            http.on("login2", body = """{"Success":true,"User":"me","Token":"tok"}""")
            return RaSession(t, store, object : RaConnectivity { override fun isConnected() = connected }) { delays += it }.start("me", "tok")
        }
        assertEquals(RaSessionMode.OFFLINE, start(connected = false, cached = true, online = false))
        assertEquals(RaSessionMode.DISABLED, start(connected = false, cached = false, online = false))
        assertEquals(RaSessionMode.ONLINE, start(connected = true, cached = false, online = true))
        delays.clear()
        assertEquals(RaSessionMode.OFFLINE, start(connected = true, cached = true, online = false))
        assertEquals(listOf(2_000L, 4_000L), delays)
        assertEquals(RaSessionMode.DISABLED, start(connected = true, cached = false, online = false))
    }
    @Test fun session_start_counts_only_a_definitive_login_answer_as_online() = runTest {
        suspend fun start(cached: Boolean, status: Int, body: String): RaSessionMode {
            val (t, store, http) = setup()
            if (cached) store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok"}""")
            http.on("login2", status = status, body = body)
            return RaSession(t, store, object : RaConnectivity { override fun isConnected() = true }) { }.start("me", "tok")
        }
        for ((status, body) in listOf(502 to "<html>Bad gateway</html>", 503 to "", 429 to "", 200 to "<html/>")) {
            assertEquals(RaSessionMode.OFFLINE, start(cached = true, status = status, body = body), "status $status")
            assertEquals(RaSessionMode.DISABLED, start(cached = false, status = status, body = body), "status $status")
        }
        // An auth error is the server's answer: the native login reports it.
        assertEquals(RaSessionMode.ONLINE, start(cached = true, status = 401, body = """{"Success":false,"Code":"invalid_credentials","Error":"Invalid"}"""))
        assertEquals(RaSessionMode.ONLINE, start(cached = true, status = 200, body = """{"Success":false,"Error":"Invalid"}"""))
    }
}
