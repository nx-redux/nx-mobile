package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.IndexedGame
import com.nxredux.mobile.library.IndexedSystem
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RaPrefetchTest {
    private val h1 = "a".repeat(32); private val h2 = "b".repeat(32)
    private fun game(file: String, name: String = file.substringBeforeLast('.')) = IndexedGame("home", "Roms/x (GBA)", file, "ref:$file", "folder", name)
    private val index = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(
        IndexedSystem("GBA", "Game Boy Advance", games = listOf(game("Zelda.gba"), game("Metroid.gba"), game("Nope.gba"))),
        IndexedSystem("P8", "Pico-8", games = listOf(game("cart.p8"))),
    ))
    private val waits = ArrayList<Long>()
    private val src = FakeRomsSource(mapOf("home" to listOf("Roms"), "home/Roms" to listOf("x (GBA)"), "home/Roms/x (GBA)" to emptyList()))
    private val tree = RaHomeTree(src) { ContentRef("home") }
    private val badgeDir = RaBadgeFolders.segments("x (GBA)", 42)!!
    private fun setup(): Triple<RaPrefetch, FakeRaStore, FakeRaHttp> {
        val store = FakeRaStore(); val http = FakeRaHttp()
        http.responders["gameid"] = { body -> RaResponse(200, if (RaRequestKind.of(body).m == h2) """{"Success":true,"GameID":0}""" else """{"Success":true,"GameID":42}""") }
        http.on("achievementsets", body = setsJson(42, title = "Zelda", consoleId = 5, achievements = """{"ID":1,"BadgeName":"00001"},{"ID":2,"BadgeName":"00002"}"""))
        http.on("unlocks", body = """{"Success":true,"GameID":42,"HardcoreMode":false,"UserUnlocks":[]}""")
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = h1; hashes["Metroid.gba"] = h2; hashes["Nope.gba"] = null }
        return Triple(RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree)) { waits += it }, store, http)
    }

    @Test fun walks_mapped_consoles_caches_sets_sessions_pointers_and_badges() = runTest {
        val (p, store, http) = setup()
        val progress = ArrayList<Triple<Int, Int, String>>()
        val r = p.run(index, "me", "tok") { d, t, title -> progress += Triple(d, t, title) }
        assertEquals(RaPrefetchResult(cached = 1, unknown = 1, skipped = 1, failed = false), r)
        assertEquals(listOf(Triple(1, 3, "Zelda"), Triple(2, 3, "Metroid"), Triple(3, 3, "Nope")), progress)
        assertEquals(RaGamePointer("GBA", "home", "Roms/x (GBA)", "Zelda.gba", "Zelda"), RaJson.parse(RaGamePointer.serializer(), store.text(RaPaths.game(h1))!!))
        assertTrue(store.exists(RaPaths.sets(h1))); assertTrue(store.exists(RaPaths.session(42)))
        assertTrue(store.exists(RaPaths.gameId(h2))); assertTrue(!store.exists(RaPaths.sets(h2)))
        assertEquals(setOf("00001.png", "00001_lock.png", "00002.png", "00002_lock.png"), tree.listing(badgeDir).keys)   // in Roms/x (GBA)/.retroachievements/badges/42
        assertEquals(4, http.gets.size)   // two badges, two variants
        assertTrue(http.posts.none { it.contains("cart.p8") })
    }
    // Each game's status as the walk reaches it (the progress page's rows): the names first, then each game's steps and outcome.
    @Test fun reports_the_games_and_each_ones_status() = runTest {
        val (p, _, _) = setup()
        val listed = ArrayList<String>(); val events = ArrayList<Triple<Int, RaItemStatus, String?>>()
        p.run(index, "me", "tok", onListed = { listed += it }, onItem = { i, st, why -> events += Triple(i, st, why) }) { _, _, _ -> }
        assertEquals(listOf("Zelda", "Metroid", "Nope"), listed)
        assertEquals(
            listOf(
                Triple(0, RaItemStatus.HASHING, null), Triple(0, RaItemStatus.DOWNLOADING, null), Triple(0, RaItemStatus.CACHED, null),
                Triple(1, RaItemStatus.HASHING, null), Triple(1, RaItemStatus.DOWNLOADING, null), Triple(1, RaItemStatus.NO_ACHIEVEMENTS, null),
                Triple(2, RaItemStatus.HASHING, null), Triple(2, RaItemStatus.SKIPPED, RaPrefetch.UNREADABLE),
            ),
            events,
        )
        events.clear()
        p.run(index, "me", "tok", onItem = { i, st, why -> events += Triple(i, st, why) }) { _, _, _ -> }
        assertEquals(RaItemStatus.ALREADY_CACHED, events.first { it.first == 0 && it.second.finished }.second)   // the second run
    }
    @Test fun a_network_error_marks_the_game_and_ends_the_walk() = runTest {
        val (p, _, http) = setup()
        http.responders["gameid"] = { throw RaHttpException("down") }
        val events = ArrayList<Triple<Int, RaItemStatus, String?>>()
        val r = p.run(index, "me", "tok", onItem = { i, st, why -> events += Triple(i, st, why) }) { _, _, _ -> }
        assertTrue(r.failed)
        assertEquals(Triple(0, RaItemStatus.ERROR, RaPrefetch.NO_CONNECTION), events.last())
    }
    // The gate is passed before each game: a pause holds the walk there, between games.
    @Test fun the_gate_holds_the_walk_before_each_game() = runTest {
        val (p, store, _) = setup()
        val open = CompletableDeferred<Unit>(); var passes = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            p.run(index, "me", "tok", gate = { passes++; if (passes == 2) open.await() }) { _, _, _ -> }
        }
        assertEquals(2, passes); assertTrue(store.exists(RaPaths.sets(h1)))      // the first game done, held before the second
        assertTrue(job.isActive)
        open.complete(Unit); job.join()
        assertEquals(3, passes)
    }
    @Test fun sessions_are_keyed_by_the_set_reply_and_bonus_badges_are_fetched() = runTest {
        val (p, store, http) = setup()
        // The set reply names game 77 (the gameid reply said 42); its bonus set has a badge of its own.
        http.on("achievementsets", body = """{"Success":true,"GameId":77,"ConsoleId":5,"Sets":[{"Type":"core","Achievements":[{"ID":1,"BadgeName":"00001"}]},{"Type":"bonus","Achievements":[{"ID":3,"BadgeName":"00003"}]}]}""")
        p.run(index, "me", "tok") { _, _, _ -> }
        assertTrue(store.exists(RaPaths.session(77))); assertTrue(!store.exists(RaPaths.session(42)))
        assertTrue(http.posts.any { it.startsWith("r=unlocks") && it.contains("g=77") })
        assertTrue(tree.has(RaBadgeFolders.segments("x (GBA)", 77)!!, "00003.png")); assertEquals(4, http.gets.size)
    }
    @Test fun without_a_home_no_badge_is_fetched() = runTest {
        val (_, store, http) = setup()
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = h1; hashes["Metroid.gba"] = h2; hashes["Nope.gba"] = null }
        val p = RaPrefetch(store, http, hasher, RaBadges(RaHomeTree(src) { null }, http), RaFingerprints(RaHomeTree(src) { null }))
        p.run(index, "me", "tok") { _, _, _ -> }; p.run(index, "me", "tok") { _, _, _ -> }   // the second run repairs cached games
        assertTrue(http.gets.isEmpty())
    }
    @Test fun a_set_reply_with_game_id_zero_gets_no_badge_folder() = runTest {
        val (p, _, http) = setup()
        http.on("achievementsets", body = """{"Success":true,"GameId":0,"ConsoleId":5,"Sets":[{"Type":"core","Achievements":[{"ID":1,"BadgeName":"00001"}]}]}""")
        p.run(index, "me", "tok") { _, _, _ -> }
        assertTrue(http.gets.isEmpty())
    }
    @Test fun an_archived_game_is_hashed_with_its_systems_tag() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        http.on("gameid", body = """{"Success":true,"GameID":0}""")
        val hasher = FakeRaHasher().apply { hashes["Zelda.zip"] = h1 }
        val archived = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(IndexedSystem("GBA", "Game Boy Advance", games = listOf(game("Zelda.zip")))))
        val r = RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree)).run(archived, "me", "tok") { _, _, _ -> }
        assertEquals(listOf("Zelda.zip"), hasher.calls); assertEquals(listOf("GBA"), hasher.tags)
        assertEquals(RaPrefetchResult(cached = 0, unknown = 1, skipped = 0, failed = false), r)
    }
    @Test fun an_archived_game_under_a_tag_without_an_ra_console_is_never_hashed() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        val hasher = FakeRaHasher().apply { hashes["X.zip"] = h1 }
        val archived = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(
            IndexedSystem("P8", "Pico-8", games = listOf(game("X.zip"))), IndexedSystem("PRBOOM", "Doom", games = listOf(game("X.zip"))),
        ))
        val r = RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree)).run(archived, "me", "tok") { _, _, _ -> }
        assertTrue(hasher.calls.isEmpty()); assertTrue(http.posts.isEmpty())
        assertEquals(RaPrefetchResult(cached = 0, unknown = 0, skipped = 0, failed = false), r)
    }
    // A cached game whose pointer names a path the library no longer lists (it moved to an extra folder) is repointed
    // where the walk found it, so the menu finds its progress; a pointer to a listed path is kept.
    @Test fun a_cached_game_with_a_stale_pointer_is_repointed_where_the_walk_found_it() = runTest {
        val (p, store, _) = setup()
        p.run(index, "me", "tok") { _, _, _ -> }
        val stale = RaGamePointer("GBA", "home", "Roms/Gone (GBA)", "Zelda.gba", "Zelda")
        RaGamePointers.write(store, h1, stale)
        p.run(index, "me", "tok") { _, _, _ -> }
        assertEquals(RaGamePointer("GBA", "home", "Roms/x (GBA)", "Zelda.gba", "Zelda"), RaJson.parse(RaGamePointer.serializer(), store.text(RaPaths.game(h1))!!))
        // The same game listed twice: the pointer to either listed copy stays.
        val twice = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(IndexedSystem("GBA", "Game Boy Advance", games = listOf(
            game("Zelda.gba"), IndexedGame("extra", "GBA", "Zelda.gba", "ref:extra", "folder", "Zelda"),
        ))))
        val other = RaGamePointer("GBA", "extra", "GBA", "Zelda.gba", "Zelda")
        RaGamePointers.write(store, h1, other)
        p.run(twice, "me", "tok") { _, _, _ -> }
        assertEquals(other, RaJson.parse(RaGamePointer.serializer(), store.text(RaPaths.game(h1))!!))
    }
    @Test fun cached_games_only_get_missing_badges() = runTest {
        val (p, store, http) = setup()
        p.run(index, "me", "tok") { _, _, _ -> }
        val posts = http.posts.size
        src.delete("home/Roms/x (GBA)/.retroachievements/badges/42/00002_lock.png")
        val r = p.run(index, "me", "tok") { _, _, _ -> }
        assertEquals(posts, http.posts.size); assertEquals(5, http.gets.size); assertEquals(RaPrefetchResult(cached = 0, unknown = 1, skipped = 2, failed = false), r)
    }
    @Test fun cancel_during_badges_deletes_sets_json() = runTest {
        val (p, store, http) = setup()
        val gate = CompletableDeferred<Unit>()
        http.getBytesResponder = { gate.await(); 200 to "PNG".encodeToByteArray() }
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { p.run(index, "me", "tok") { _, _, _ -> } }
        assertTrue(store.exists(RaPaths.sets(h1)))
        job.cancel(); job.join()
        assertTrue(!store.exists(RaPaths.sets(h1)))
    }
    @Test fun a_network_error_reports_failure_and_keeps_what_was_cached() = runTest {
        val (p, store, http) = setup()
        var calls = 0
        http.responders["gameid"] = { calls++; if (calls == 2) throw RaHttpException("down") else RaResponse(200, """{"Success":true,"GameID":42}""") }
        val r = p.run(index, "me", "tok") { _, _, _ -> }
        assertTrue(r.failed); assertEquals(1, r.cached); assertTrue(store.exists(RaPaths.sets(h1)))
    }
    @Test fun sets_json_is_written_after_session_and_pointer() = runTest {
        val (p, store, _) = setup()
        p.run(index, "me", "tok") { _, _, _ -> }
        val order = store.writes.filter { it in setOf(RaPaths.gameId(h1), RaPaths.session(42), RaPaths.game(h1), RaPaths.sets(h1)) }
        assertEquals(listOf(RaPaths.gameId(h1), RaPaths.session(42), RaPaths.game(h1), RaPaths.sets(h1)), order)
    }
    @Test fun a_non_success_reply_skips_that_game_and_the_walk_continues() = runTest {
        val (p, store, http) = setup()
        http.on("unlocks", body = """{"Success":false}""")
        val progress = ArrayList<String>()
        val r = p.run(index, "me", "tok") { _, _, t -> progress += t }
        assertEquals(RaPrefetchResult(cached = 0, unknown = 1, skipped = 2, failed = false), r)
        assertEquals(listOf("Zelda", "Metroid", "Nope"), progress)
        assertTrue(!store.exists(RaPaths.sets(h1))); assertTrue(!store.exists(RaPaths.session(42))); assertTrue(!store.exists(RaPaths.game(h1)))
        assertTrue(store.exists(RaPaths.gameId(h2)))
    }
    @Test fun a_cached_game_id_without_sets_is_refetched_but_game_id_zero_is_unknown() = runTest {
        val (p, store, http) = setup()
        store.put(RaPaths.gameId(h1), """{"Success":true,"GameID":42}""")
        store.put(RaPaths.gameId(h2), """{"Success":true,"GameID":0}""")
        val r = p.run(index, "me", "tok") { _, _, _ -> }
        assertEquals(RaPrefetchResult(cached = 1, unknown = 1, skipped = 1, failed = false), r)
        assertEquals(listOf("achievementsets", "unlocks"), http.posts.map { RaRequestKind.of(it).r })
        assertTrue(store.exists(RaPaths.sets(h1))); assertTrue(store.exists(RaPaths.session(42)))
    }

    @Test fun never_opens_a_session_and_caches_the_unlock_ids_without_times() = runTest {
        val (p, store, http) = setup()
        http.on("unlocks", body = """{"Success":true,"GameID":42,"HardcoreMode":false,"UserUnlocks":[1,$UNKNOWN_EMULATOR_ID]}""")
        p.run(index, "me", "tok") { _, _, _ -> }
        assertTrue(http.posts.none { it.startsWith("r=startsession") })
        assertEquals(listOf("r=unlocks&u=me&t=tok&g=42&h=0"), http.posts.filter { it.startsWith("r=unlocks") })
        val session = RaJson.parse(SessionBody.serializer(), store.text(RaPaths.session(42))!!)!!
        assertEquals(listOf(SessionUnlock(1, 0L), SessionUnlock(UNKNOWN_EMULATOR_ID, 0L)), session.Unlocks)
        assertTrue(RaJson.isSuccess(store.text(RaPaths.session(42))!!))
    }
    @Test fun a_session_from_real_play_is_kept() = runTest {
        val (p, store, http) = setup()
        val played = """{"Success":true,"Unlocks":[{"ID":1,"When":1700000000}]}"""
        store.put(RaPaths.session(42), played)
        val r = p.run(index, "me", "tok") { _, _, _ -> }
        assertEquals(1, r.cached); assertEquals(played, store.text(RaPaths.session(42)))
        assertTrue(http.posts.none { it.startsWith("r=unlocks") })
    }
    @Test fun requests_are_paced() = runTest {
        val (p, _, http) = setup()
        p.run(index, "me", "tok") { _, _, _ -> }
        assertEquals(List(http.posts.size - 1) { RA_PACE_MS }, waits)
    }
    @Test fun a_busy_server_after_the_backoff_stops_the_walk_to_try_later() = runTest {
        val (p, store, http) = setup()
        http.on("gameid", status = 429, body = "Too Many Requests")
        val r = p.run(index, "me", "tok") { _, _, _ -> }
        assertEquals(RaPrefetchResult(0, 0, 0, failed = true, stop = RaSyncStop.BUSY), r)
        assertEquals(2, http.posts.size); assertEquals(listOf(RA_BACKOFF_MS), waits)
        assertTrue(!store.exists(RaPaths.gameId(h1)))
    }
    @Test fun a_refused_login_stops_the_walk() = runTest {
        val (p, _, http) = setup()
        http.on("achievementsets", status = 401, body = """{"Success":false,"Status":401,"Code":"invalid_credentials","Error":"Invalid user/token combination."}""")
        val r = p.run(index, "me", "tok") { _, _, _ -> }
        assertEquals(RaSyncStop.AUTH, r.stop); assertTrue(r.failed)
        assertEquals(1, http.posts.count { it.startsWith("r=achievementsets") })
    }

    // Fingerprints: the shared per-folder hashes.json; a game whose file is unchanged is not hashed (nor unpacked) again.
    private fun parts() = Triple(FakeRaStore(), FakeRaHttp(), RaHomeTree(FakeRomsSource(mapOf("home" to listOf("Roms"), "home/Roms" to listOf("x (GBA)"), "home/Roms/x (GBA)" to emptyList()))) { ContentRef("home") })
    @Test fun a_game_with_an_unchanged_file_is_not_hashed_again_and_unknown_games_are_remembered() = runTest {
        val (store, http, tree) = parts(); http.on("gameid", body = """{"Success":true,"GameID":0}""")
        val hasher = FakeRaHasher().apply {
            hashes["Zelda.gba"] = h1; hashes["Metroid.gba"] = h2; hashes["Nope.gba"] = null
            stamps["ref:Zelda.gba"] = "10:1000000"; stamps["ref:Metroid.gba"] = "20:1000000"; stamps["ref:Nope.gba"] = "30:1000000"
        }
        val p = RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree))
        p.run(index, "me", "tok") { _, _, _ -> }
        hasher.calls.clear()
        p.run(index, "me", "tok") { _, _, _ -> }
        assertEquals(listOf("Nope.gba"), hasher.calls)   // known-unknown (game 0) is cached too; an unhashable file is retried
        val saved = tree.readText(RaBadgeFolders.dataDir("x (GBA)"), "hashes.json")!!
        assertTrue(saved.contains("\"Zelda.gba\"") && saved.contains("\"game\":0"))
        hasher.calls.clear(); hasher.stamps["ref:Zelda.gba"] = "11:1000000"   // the file was replaced
        p.run(index, "me", "tok") { _, _, _ -> }
        assertTrue("Zelda.gba" in hasher.calls)
    }
    @Test fun a_handheld_entry_is_a_hit() = runTest {
        val (store, http, tree) = parts(); http.on("gameid", body = """{"Success":true,"GameID":0}""")
        val entry = """{"version":1,"files":{"Zelda.gba":{"size":10,"mtime":4600,"console":5,"hash":"$h1","game":0}}}"""
        tree.writeText(RaBadgeFolders.dataDir("x (GBA)"), "hashes.json", entry)
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = h1; stamps["ref:Zelda.gba"] = "10:1000000" }   // 1000 s vs 4600 s: 1 h zone shift
        RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree)).run(index, "me", "tok") { _, _, _ -> }
        assertTrue("Zelda.gba" !in hasher.calls)
        assertEquals(entry, tree.readText(RaBadgeFolders.dataDir("x (GBA)"), "hashes.json"))   // a hit is not restamped
    }
    @Test fun a_transport_error_stores_nothing() = runTest {
        val (store, http, tree) = parts(); http.offline = true
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = h1; stamps["ref:Zelda.gba"] = "10:1000000" }
        RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree)).run(index, "me", "tok") { _, _, _ -> }
        assertNull(tree.readText(RaBadgeFolders.dataDir("x (GBA)"), "hashes.json"))
    }
    @Test fun prefetch_without_a_home_still_hashes() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp(); http.on("gameid", body = """{"Success":true,"GameID":0}""")
        val none = RaHomeTree(FakeRomsSource(emptyMap())) { null }
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = h1; stamps["ref:Zelda.gba"] = "10:1000000" }
        val r = RaPrefetch(store, http, hasher, RaBadges(none, http), RaFingerprints(none)).run(index, "me", "tok") { _, _, _ -> }
        assertFalse(r.failed); assertTrue("Zelda.gba" in hasher.calls)
    }
    @Test fun a_game_with_handheld_sets_and_no_game_id_file_is_recorded_from_the_sets_reply() = runTest {
        val (store, http, tree) = parts()
        store.put(RaPaths.sets(h1), setsJson(42, title = "Zelda"))   // the handheld's sets.json: no mobile gameid.json beside it
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = h1; stamps["ref:Zelda.gba"] = "10:1000000" }
        val p = RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree))
        p.run(index, "me", "tok") { _, _, _ -> }
        val saved = tree.readText(RaBadgeFolders.dataDir("x (GBA)"), "hashes.json")!!
        assertTrue(saved.contains("\"Zelda.gba\"") && saved.contains("\"game\":42"))
        hasher.calls.clear()
        p.run(index, "me", "tok") { _, _, _ -> }
        assertTrue("Zelda.gba" !in hasher.calls)
    }
    @Test fun a_cached_known_game_now_unknown_is_forgotten_not_recorded_as_zero() = runTest {
        val (store, http, tree) = parts(); http.on("gameid", body = """{"Success":true,"GameID":0}""")
        tree.writeText(RaBadgeFolders.dataDir("x (GBA)"), "hashes.json",
            """{"version":1,"files":{"Zelda.gba":{"size":10,"mtime":1000,"console":5,"hash":"$h1","game":42}}}""")
        val hasher = FakeRaHasher().apply { stamps["ref:Zelda.gba"] = "10:1000000" }
        RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree)).run(index, "me", "tok") { _, _, _ -> }
        assertTrue("Zelda.gba" !in hasher.calls)   // the hit's hash was asked about
        assertTrue(!tree.readText(RaBadgeFolders.dataDir("x (GBA)"), "hashes.json")!!.contains("Zelda.gba"))
    }
    // storable()'s refusal of a raw DC archive is pinned in RaFingerprintsTest: here a DC zip never gets that far.
    @Test fun an_unpacked_archive_is_stored_and_a_dc_zip_is_not_walked() = runTest {
        val (store, http, tree) = parts(); http.on("gameid", body = """{"Success":true,"GameID":0}""")
        val hasher = FakeRaHasher().apply { hashes["Zelda.zip"] = h1; hashes["Crazy.zip"] = h2; stamps["ref:Zelda.zip"] = "10:1000000"; stamps["ref:Crazy.zip"] = "20:1000000" }
        val archived = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(
            IndexedSystem("GBA", "Game Boy Advance", games = listOf(game("Zelda.zip"))),   // unpacked by the hasher: the inner ROM's hash
            IndexedSystem("DC", "Dreamcast", games = listOf(game("Crazy.zip"))),   // a zip has no RA console id on DC: dropped before hashing
        ))
        RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree)).run(archived, "me", "tok") { _, _, _ -> }
        val saved = tree.readText(RaBadgeFolders.dataDir("x (GBA)"), "hashes.json")!!
        assertTrue(saved.contains("\"Zelda.zip\"")); assertTrue(!saved.contains("Crazy.zip")); assertEquals(listOf("Zelda.zip"), hasher.calls)
    }
    @Test fun a_cancelled_walk_still_saves_the_hashes_it_made() = runTest {
        val (_, store, http) = setup()
        val gate = CompletableDeferred<Unit>()
        http.getBytesResponder = { gate.await(); 200 to "PNG".encodeToByteArray() }
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = h1; stamps["ref:Zelda.gba"] = "1:1000" }
        val prefetch = RaPrefetch(store, http, hasher, RaBadges(tree, http), RaFingerprints(tree))
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { prefetch.run(index, "me", "tok") { _, _, _ -> } }
        job.cancel(); job.join()
        assertTrue(tree.readText(RaBadgeFolders.dataDir("x (GBA)"), "hashes.json")!!.contains(h1))   // flushed in finally
    }
}
