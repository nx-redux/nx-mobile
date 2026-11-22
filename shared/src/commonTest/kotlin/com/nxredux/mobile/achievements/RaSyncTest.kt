package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RaSyncTest {
    private val h1 = "1".repeat(32); private val h2 = "2".repeat(32)
    private fun setup(now: Long = 1_000_000L): Triple<RaSync, FakeRaStore, FakeRaHttp> {
        val store = FakeRaStore(); val http = FakeRaHttp()
        store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","Score":1,"SoftcoreScore":2}""")
        val sync = RaSync(store, http, FakeRaSigner(), RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED)) { now }
        return Triple(sync, store, http)
    }

    @Test fun replays_the_journal_in_order_and_confirms_each() = runTest {
        val (sync, store, http) = setup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("you", 9, h1, 901L)); j.append(RaUnlock("me", 2, h1, 1_000L))
        var awarded = 0
        http.responders["awardachievement"] = { body -> awarded++; RaResponse(200, if (RaRequestKind.of(body).a == 2) """{"Success":false,"Error":"User already has this achievement awarded.","Score":5,"SoftcoreScore":50}""" else """{"Success":true,"Score":5,"SoftcoreScore":40,"AchievementID":1}""") }
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok","Score":5,"SoftcoreScore":50}""")
        val progress = ArrayList<RaSyncProgress>()
        val r = sync.run("me", "tok") { progress += it }
        assertEquals(2, r.submitted); assertEquals(false, r.failed); assertEquals(50, r.points)
        assertEquals(listOf(RaUnlock("you", 9, h1, 901L)), j.entries())
        assertEquals(listOf(RaUnlock("me", 1, h1, 900L), RaUnlock("me", 2, h1, 1_000L)), RaJournal(store, RaPaths.CONFIRMED).entries())
        assertTrue(http.posts[0].contains("a=1&h=0&m=$h1&o=100&")); assertTrue(http.posts[1].contains("a=2&h=0&m=$h1&o=1&"))   // seconds since unlock, min 1
        assertEquals(RaSyncProgress(RaSyncStep.SUBMITTING, 1, 2), progress[0])
        assertEquals("1000", store.text(RaPaths.LAST_SYNC))   // now()/1000
    }
    @Test fun a_network_error_stops_the_replay_and_keeps_the_rest() = runTest {
        val (sync, store, http) = setup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, h1, 901L))
        http.offline = true
        val r = sync.run("me", "tok") {}
        assertEquals(0, r.submitted); assertTrue(r.failed); assertEquals(2, j.count("me"))
        assertEquals(null, store.text(RaPaths.LAST_SYNC))
    }
    @Test fun a_network_error_after_a_submit_still_skips_lastsync() = runTest {
        val (sync, store, http) = setup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, h1, 901L))
        http.responders["awardachievement"] = { body -> if (RaRequestKind.of(body).a == 1) RaResponse(200, """{"Success":true,"AchievementID":1}""") else throw RaHttpException("offline") }
        val r = sync.run("me", "tok") {}
        assertEquals(1, r.submitted); assertTrue(r.failed)
        assertEquals(listOf(RaUnlock("me", 2, h1, 901L)), j.entries())
        assertEquals(null, store.text(RaPaths.LAST_SYNC))
    }
    @Test fun a_completed_replay_writes_lastsync_even_when_the_refresh_fails() = runTest {
        val (sync, store, http) = setup()
        http.responders["login2"] = { throw RaHttpException("offline") }
        val r = sync.run("me", "tok") {}
        assertEquals(0, r.submitted); assertTrue(r.failed)
        assertEquals("1000", store.text(RaPaths.LAST_SYNC))
    }
    @Test fun a_rejected_award_moves_to_the_dead_letters_and_the_replay_continues() = runTest {
        val (sync, store, http) = setup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, h1, 901L))
        http.responders["awardachievement"] = { body -> if (RaRequestKind.of(body).a == 1) RaResponse(404, """{"Success":false,"Status":404,"Code":"not_found","Error":"Unknown achievement."}""") else RaResponse(200, """{"Success":true,"AchievementID":2}""") }
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok"}""")
        val r = sync.run("me", "tok") {}
        assertEquals(1, r.submitted); assertEquals(1, r.rejected); assertEquals(0, r.remaining); assertEquals(false, r.failed)
        assertEquals(emptyList(), j.entries())
        assertEquals(listOf(RaUnlock("me", 1, h1, 900L)), RaJournal(store, RaPaths.REJECTED).entries())
        assertEquals(listOf(RaUnlock("me", 2, h1, 901L)), RaJournal(store, RaPaths.CONFIRMED).entries())
        http.posts.clear()
        sync.run("me", "tok") {}
        assertTrue(http.posts.none { it.startsWith("r=awardachievement") })   // never resent
    }
    @Test fun a_refused_login_stops_the_replay_and_keeps_every_entry() = runTest {
        val (sync, store, http) = setup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, h1, 901L))
        http.on("awardachievement", status = 401, body = """{"Success":false,"Status":401,"Code":"invalid_credentials","Error":"Invalid user/token combination."}""")
        val r = sync.run("me", "tok") {}
        assertEquals(RaSyncStop.AUTH, r.stop); assertTrue(r.failed); assertEquals(2, r.remaining); assertEquals(0, r.rejected)
        assertEquals(1, http.posts.size)   // the second award is not tried
        assertEquals(2, j.count("me")); assertEquals(emptyList(), RaJournal(store, RaPaths.REJECTED).entries())
        assertEquals(null, store.text(RaPaths.LAST_SYNC))
    }
    @Test fun a_refused_login_in_the_refresh_stops_with_auth() = runTest {
        val (sync, _, http) = setup()
        http.on("login2", status = 401, body = """{"Success":false,"Status":401,"Code":"invalid_credentials","Error":"Invalid user/token combination."}""")
        val r = sync.run("me", "tok") {}
        assertEquals(RaSyncStop.AUTH, r.stop); assertTrue(r.failed)
    }
    @Test fun an_account_refusal_stops_and_keeps_the_entries_with_the_servers_words() = runTest {
        val (sync, store, http) = setup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L))
        http.on("awardachievement", status = 403, body = """{"Success":false,"Status":403,"Code":"access_denied","Error":"Access denied. Please verify your email address."}""")
        val r = sync.run("me", "tok") {}
        assertEquals(RaSyncStop.REFUSED, r.stop); assertEquals("Access denied. Please verify your email address.", r.error); assertEquals(1, j.count("me"))
    }

    // Pacing and backoff: every wait is recorded, none is real.
    private fun pacedSetup(): Triple<RaSync, FakeRaStore, FakeRaHttp> {
        val store = FakeRaStore(); val http = FakeRaHttp()
        val sync = RaSync(store, http, FakeRaSigner(), RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), delay = { waits += it }) { 1_000_000L }
        return Triple(sync, store, http)
    }
    private val waits = ArrayList<Long>()

    @Test fun requests_are_paced() = runTest {
        val (sync, store, http) = pacedSetup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, h1, 901L))
        http.on("awardachievement", body = """{"Success":true,"AchievementID":1}""")
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok"}""")
        sync.run("me", "tok") {}
        assertEquals(3, http.posts.size); assertEquals(listOf(RA_PACE_MS, RA_PACE_MS), waits)   // none before the first
    }
    @Test fun a_busy_server_is_retried_once_after_a_backoff() = runTest {
        val (sync, store, http) = pacedSetup()
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 1, h1, 900L))
        var calls = 0
        http.responders["awardachievement"] = { calls++; if (calls == 1) RaResponse(429, "Too Many Requests") else RaResponse(200, """{"Success":true,"AchievementID":1}""") }
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok"}""")
        val r = sync.run("me", "tok") {}
        assertEquals(1, r.submitted); assertEquals(false, r.failed); assertEquals(listOf(RA_BACKOFF_MS, RA_PACE_MS), waits)
    }
    @Test fun a_server_still_busy_after_the_backoff_stops_the_run_to_try_later() = runTest {
        val (sync, store, http) = pacedSetup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, h1, 901L))
        http.on("awardachievement", status = 503, body = "<html>Service Unavailable</html>")
        val r = sync.run("me", "tok") {}
        assertEquals(RaSyncStop.BUSY, r.stop); assertTrue(r.failed); assertEquals(2, r.remaining)
        assertEquals(2, http.posts.size); assertEquals(listOf(RA_BACKOFF_MS), waits)   // award 1 twice, award 2 never
        assertEquals(emptyList(), RaJournal(store, RaPaths.REJECTED).entries())
    }
    @Test fun a_busy_server_in_the_refresh_stops_it_to_try_later() = runTest {
        val (sync, store, http) = pacedSetup()
        store.put(RaPaths.sets(h1), setsJson(42, consoleId = 5))
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok","Score":1,"SoftcoreScore":2}""")
        http.on("allprogress", status = 502, body = "Bad Gateway")
        val r = sync.run("me", "tok") {}
        assertEquals(RaSyncStop.BUSY, r.stop); assertTrue(r.failed)
        assertEquals(2, http.posts.count { it.startsWith("r=allprogress") }); assertEquals(null, store.text(RaPaths.FULL_SYNC))
    }

    @Test fun cancelling_after_a_confirmed_award_still_takes_it_off_the_journal() = runTest {
        val (_, store, http) = setup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, h1, 901L))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        http.responders["awardachievement"] = { body -> if (RaRequestKind.of(body).a == 2) gate.await(); RaResponse(200, """{"Success":true,"AchievementID":1}""") }
        // The journal rewrite suspends, as a real store's IO does: a cancelled coroutine would throw there.
        store.beforeRead = { kotlinx.coroutines.yield() }
        val sync = RaSync(store, http, FakeRaSigner(), j, RaJournal(store, RaPaths.CONFIRMED)) { 1_000_000L }
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { sync.run("me", "tok") {} }
        job.cancel(); job.join()
        assertEquals(listOf(RaUnlock("me", 2, h1, 901L)), j.entries())
    }

    @Test fun a_never_played_game_is_not_stale_despite_the_unknown_emulator_warning() = runTest {
        val (sync, store, http) = setup()
        store.put(RaPaths.sets(h1), setsJson(42, consoleId = 5))
        store.put(RaPaths.sets(h2), setsJson(43, consoleId = 5))
        // The server adds the warning to every session of this client; a hardcore unlock from another device counts too.
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[{"ID":$UNKNOWN_EMULATOR_ID,"When":5}]}""")
        store.put(RaPaths.session(43), """{"Success":true,"Unlocks":[{"ID":1,"When":1},{"ID":$UNKNOWN_EMULATOR_ID,"When":5}],"HardcoreUnlocks":[{"ID":2,"When":2}]}""")
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok"}""")
        http.on("allprogress", body = """{"Success":true,"Response":{"42":{"Achievements":3},"43":{"Achievements":3,"Unlocked":2,"UnlockedHardcore":1}}}""")
        val r = sync.run("me", "tok") {}
        assertEquals(0, r.refreshedGames); assertTrue(http.posts.none { it.startsWith("r=startsession") })
    }
    @Test fun progress_names_each_step() = runTest {
        val (sync, store, http) = setup()
        store.put(RaPaths.sets(h1), setsJson(42, title = "Alleyway", consoleId = 4))
        store.put(RaPaths.sets(h2), setsJson(43, title = "Tetris", consoleId = 5))
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[]}""")
        store.put(RaPaths.session(43), """{"Success":true,"Unlocks":[]}""")
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, "3".repeat(32), 901L))
        http.responders["awardachievement"] = { RaResponse(200, """{"Success":true,"AchievementID":1}""") }
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok"}""")
        http.responders["allprogress"] = { body -> RaResponse(200, if (body.endsWith("c=4")) """{"Success":true,"Response":{"42":{"Unlocked":1}}}""" else """{"Success":true,"Response":{"43":{"Unlocked":0}}}""") }
        http.on("startsession", body = """{"Success":true,"Unlocks":[{"ID":1,"When":1}]}""")
        val progress = ArrayList<RaSyncProgress>()
        sync.run("me", "tok") { progress += it }
        assertEquals(listOf(
            RaSyncProgress(RaSyncStep.SUBMITTING, 1, 2, "Alleyway"), RaSyncProgress(RaSyncStep.SUBMITTING, 2, 2, null),
            RaSyncProgress(RaSyncStep.ACCOUNT),
            RaSyncProgress(RaSyncStep.READING, 1, 2), RaSyncProgress(RaSyncStep.READING, 2, 2),
            RaSyncProgress(RaSyncStep.PROGRESS, 1, 2, "1 game"), RaSyncProgress(RaSyncStep.PROGRESS, 2, 2, "1 game"),
            RaSyncProgress(RaSyncStep.SESSIONS, 1, 1, "Alleyway"),
        ), progress)
    }
    @Test fun refresh_refetches_only_stale_sessions() = runTest {
        val (sync, store, http) = setup()
        store.put(RaPaths.sets(h1), setsJson(42, consoleId = 5))
        store.put(RaPaths.sets(h2), setsJson(43, consoleId = 5))
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[{"ID":1,"When":1}]}""")
        store.put(RaPaths.session(43), """{"Success":true,"Unlocks":[]}""")
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":77}""")
        http.on("allprogress", body = """{"Success":true,"Response":{"42":{"Achievements":3,"Unlocked":1},"43":{"Achievements":3,"Unlocked":2}}}""")
        http.on("startsession", body = """{"Success":true,"Unlocks":[{"ID":1,"When":1},{"ID":2,"When":2}]}""")
        val r = sync.run("me", "tok") {}
        assertEquals(1, r.refreshedGames); assertEquals(77, r.points)
        assertEquals(1, http.posts.count { it.startsWith("r=allprogress") && it.endsWith("c=5") })
        assertEquals(listOf("r=startsession&u=me&t=tok&g=43&h=0&m=$h2&l=${RaRequests.RCHEEVOS_VERSION}"), http.posts.filter { it.startsWith("r=startsession") })
        assertTrue(store.text(RaPaths.session(43))!!.contains("\"ID\":2"))
    }

    @Test fun a_game_with_subset_unlocks_is_not_stale_when_every_set_matches() = runTest {
        val (sync, store, http) = setup()
        // Super Mario 64: core set game 10003, bonus set game 11458; the session lists the unlocks of both.
        store.put(RaPaths.sets(h1), """{"Success":true,"GameId":10003,"Title":"SM64","ConsoleId":2,"Sets":[""" +
            """{"Type":"core","AchievementSetId":1,"GameId":10003,"Achievements":[{"ID":1},{"ID":2}]},""" +
            """{"Type":"bonus","AchievementSetId":2,"GameId":11458,"Achievements":[{"ID":3}]}]}""")
        store.put(RaPaths.session(10003), """{"Success":true,"Unlocks":[{"ID":1,"When":1},{"ID":3,"When":3}]}""")
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok"}""")
        http.on("allprogress", body = """{"Success":true,"Response":{"10003":{"Achievements":2,"Unlocked":1},"11458":{"Achievements":1,"Unlocked":1}}}""")
        val r = sync.run("me", "tok") {}
        assertEquals(0, r.refreshedGames); assertTrue(http.posts.none { it.startsWith("r=startsession") })
    }
    @Test fun a_subset_unlock_from_another_device_makes_the_game_stale() = runTest {
        val (sync, store, http) = setup()
        store.put(RaPaths.sets(h1), """{"Success":true,"GameId":10003,"Title":"SM64","ConsoleId":2,"Sets":[""" +
            """{"Type":"core","AchievementSetId":1,"GameId":10003,"Achievements":[{"ID":1},{"ID":2}]},""" +
            """{"Type":"bonus","AchievementSetId":2,"GameId":11458,"Achievements":[{"ID":3}]}]}""")
        store.put(RaPaths.session(10003), """{"Success":true,"Unlocks":[{"ID":1,"When":1}]}""")
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok"}""")
        http.on("allprogress", body = """{"Success":true,"Response":{"10003":{"Achievements":2,"Unlocked":1},"11458":{"Achievements":1,"Unlocked":1}}}""")
        http.on("startsession", body = """{"Success":true,"Unlocks":[{"ID":1,"When":1},{"ID":3,"When":3}]}""")
        val r = sync.run("me", "tok") {}
        assertEquals(1, r.refreshedGames)
    }
    @Test fun the_journal_matches_the_user_whatever_the_case() = runTest {
        val (sync, store, http) = setup()
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("KotakSempit", 1, h1, 900L))
        http.responders["awardachievement"] = { RaResponse(200, """{"Success":true,"AchievementID":1}""") }
        val r = sync.run("kotaksempit", "tok") {}
        assertEquals(1, r.submitted); assertEquals(0, j.count("kotaksempit"))
    }

    // The quick sync: the cloud totals against the last full sync's totals plus this sync's sent unlocks.
    private val day = 24 * 3600 * 1000L
    private val now = 100 * day
    private fun quickSetup(): Triple<RaSync, FakeRaStore, FakeRaHttp> {
        val (sync, store, http) = setup(now)
        store.put(RaPaths.sets(h1), setsJson(42, title = "Alleyway", consoleId = 5, achievements = """{"ID":1,"Points":5,"Flags":3},{"ID":2,"Points":10,"Flags":3},{"ID":3,"Points":25,"Flags":5}"""))
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[]}""")
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok","Score":10,"SoftcoreScore":100}""")
        http.on("allprogress", body = """{"Success":true,"Response":{"42":{"Achievements":3,"Unlocked":1}}}""")
        http.on("startsession", body = """{"Success":true,"Unlocks":[{"ID":1,"When":1}]}""")
        return Triple(sync, store, http)
    }
    private fun FakeRaStore.fullSync(at: Long, score: Int = 10, softcore: Int = 100, user: String = "me") =
        put(RaPaths.FULL_SYNC, """{"user":"$user","at":$at,"score":$score,"softcore":$softcore}""")
    private fun FakeRaHttp.cloudFetches() = posts.count { it.startsWith("r=allprogress") || it.startsWith("r=startsession") }
    private fun FakeRaStore.fullSyncAt() = text(RaPaths.FULL_SYNC)?.let { Regex("\"at\":(\\d+)").find(it)?.groupValues?.get(1)?.toLong() }

    @Test fun equal_totals_skip_the_cloud_fetch() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day)
        val progress = ArrayList<RaSyncProgress>()
        val r = sync.run("me", "tok") { progress += it }
        assertEquals(0, http.cloudFetches()); assertEquals(RaSyncResult(0, 0, 100, false), r)
        assertEquals(listOf(RaSyncProgress(RaSyncStep.ACCOUNT)), progress)
        assertEquals(now - day, store.fullSyncAt())
    }
    @Test fun a_different_softcore_total_runs_the_full_sync() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, softcore = 99)
        val r = sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches()); assertEquals(1, r.refreshedGames)
        assertEquals(now, store.fullSyncAt())
    }
    @Test fun a_different_hardcore_total_runs_the_full_sync() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, score = 9)
        sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches())
    }
    @Test fun another_users_record_runs_the_full_sync() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, user = "you")
        sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches())
    }
    @Test fun a_full_sync_older_than_a_week_runs_again() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - 7 * day - 1)
        sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches()); assertEquals(now, store.fullSyncAt())
    }
    @Test fun a_full_sync_exactly_a_week_old_still_counts() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - 7 * day)
        sync.run("me", "tok") {}
        assertEquals(0, http.cloudFetches())
    }
    @Test fun a_full_sync_dated_in_the_future_runs_again() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now + day)
        sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches())
    }
    @Test fun the_first_sync_is_full_and_records_its_totals() = runTest {
        val (sync, store, http) = quickSetup()
        sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches())
        assertEquals("""{"user":"me","at":$now,"score":10,"softcore":100}""", store.text(RaPaths.FULL_SYNC))
    }
    @Test fun an_empty_cache_runs_the_full_sync() = runTest {
        val (sync, store, http) = quickSetup()
        store.files.keys.filter { it.startsWith(RaPaths.GAMES) }.forEach { store.files.remove(it) }
        store.fullSync(at = now - day)
        sync.run("me", "tok") {}
        assertEquals(now, store.fullSyncAt())   // full (nothing to fetch), so the record is renewed
    }
    @Test fun sent_unlocks_count_toward_the_local_total() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, softcore = 85)
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, h1, 901L))
        http.on("awardachievement", body = """{"Success":true,"Score":10,"SoftcoreScore":100,"AchievementID":1}""")
        val r = sync.run("me", "tok") {}
        assertEquals(0, http.cloudFetches()); assertEquals(RaSyncResult(2, 0, 100, false), r)
        assertEquals("""{"user":"me","at":${now - day},"score":10,"softcore":100}""", store.text(RaPaths.FULL_SYNC))
    }
    @Test fun an_already_unlocked_reply_adds_no_points() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, softcore = 95)
        val j = RaJournal(store, RaPaths.JOURNAL); j.append(RaUnlock("me", 1, h1, 900L)); j.append(RaUnlock("me", 2, h1, 901L))
        http.responders["awardachievement"] = { body -> RaResponse(200, if (RaRequestKind.of(body).a == 2) """{"Success":false,"Error":"User already has this achievement awarded."}""" else """{"Success":true,"AchievementID":1}""") }
        sync.run("me", "tok") {}
        assertEquals(0, http.cloudFetches())
    }
    @Test fun a_sent_unlock_without_known_points_runs_the_full_sync() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, softcore = 95)
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 1, "3".repeat(32), 900L))   // set not cached
        http.on("awardachievement", body = """{"Success":true,"AchievementID":1}""")
        sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches())
    }
    @Test fun a_sent_unofficial_unlock_runs_the_full_sync() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, softcore = 75)
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 3, h1, 900L))
        http.on("awardachievement", body = """{"Success":true,"AchievementID":3}""")
        sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches())
    }
    @Test fun missing_cloud_totals_run_the_full_sync() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day)
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":100}""")
        sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches()); assertEquals(now - day, store.fullSyncAt())   // no totals: the record stays
    }
    @Test fun a_failed_login_runs_the_full_sync() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day)
        http.on("login2", body = """{"Success":false,"Error":"bad token","Score":10,"SoftcoreScore":100}""")
        sync.run("me", "tok") {}
        assertEquals(2, http.cloudFetches()); assertEquals(now - day, store.fullSyncAt())
    }
    @Test fun a_failed_full_sync_keeps_the_old_record() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, softcore = 99)
        http.responders["allprogress"] = { throw RaHttpException("offline") }
        val r = sync.run("me", "tok") {}
        assertTrue(r.failed); assertEquals(now - day, store.fullSyncAt())
    }
    @Test fun an_unrefreshed_stale_game_keeps_the_old_record() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, softcore = 99)
        http.on("startsession", body = """{"Success":false}""")
        val r = sync.run("me", "tok") {}
        assertEquals(false, r.failed); assertEquals(now - day, store.fullSyncAt())
    }
    @Test fun an_unreadable_progress_reply_keeps_the_old_record() = runTest {
        val (sync, store, http) = quickSetup()
        store.fullSync(at = now - day, softcore = 99)
        http.on("allprogress", body = "<html>")
        sync.run("me", "tok") {}
        assertEquals(now - day, store.fullSyncAt())
    }

    @Test fun confirmed_unlocks_the_cached_session_shows_with_a_time_are_pruned() = runTest {
        val (sync, store, http) = setup()
        store.put(RaPaths.sets(h1), setsJson(42, consoleId = 5))
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[{"ID":1,"When":500},{"ID":2,"When":0}],"HardcoreUnlocks":[{"ID":3,"When":600}]}""")
        val confirmed = RaJournal(store, RaPaths.CONFIRMED)
        // 1: shown with a time (pruned); 2: prefetched, no time; 3: hardcore only; 4: not in the session; another user's.
        listOf(RaUnlock("me", 1, h1, 400L), RaUnlock("me", 2, h1, 401L), RaUnlock("me", 3, h1, 402L), RaUnlock("me", 4, h1, 403L), RaUnlock("you", 1, h1, 404L))
            .forEach { confirmed.append(it) }
        http.responders["login2"] = { throw RaHttpException("offline") }
        sync.run("me", "tok") {}
        assertEquals(listOf(2, 3, 4), confirmed.entries("me").map { it.a }); assertEquals(1, confirmed.entries("you").size)
    }
}
