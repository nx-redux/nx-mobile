package com.nxredux.mobile.achievements

import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.PadButtons
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RaControllerTest {
    private class Settings : AppSettings {
        override var treeRef: ContentRef? = null; override var extraFolders: List<ExtraFolder> = emptyList(); override var defaultTags: Map<String, String> = emptyMap()
        override var padTheme = PadTheme.DEFAULT
        override var padButtons: PadButtons = PadButtons.DEFAULT
        override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap(); override var gameListStyle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_GAME_LIST_STYLE; override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap(); override var gameListOrientation = com.nxredux.mobile.settings.MenuStyles.DEFAULT_ORIENTATION; override var gameListVerticalAlignment = com.nxredux.mobile.settings.VerticalListAlignment.DEFAULT; override var toolApps: List<String> = emptyList()
        override var androidConsoleApps: List<String> = emptyList(); override var androidGamesAsked = false; override var androidPins: List<String> = emptyList()
        override var raEnabled = false; override var raUsername = ""; override var raToken: String? = null; override var raShowNotifications = true
        override var raNotificationSeconds = 3; override var raProgressSeconds = 1; override var raSortOrder = RaSortOrder.DEFAULT
        override var ssUsername = ""; override var ssPassword: String? = null; override var sgdbApiKey: String? = null; override var tgdbApiKey: String? = null; override var artReplaceExisting = false; override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showRecentHint = true; override var showSearchHint = true; override var showNetplayHint = true; override var menuPageTitle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = com.nxredux.mobile.settings.MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = com.nxredux.mobile.settings.AccentPreset.DEFAULT; override var secondaryAccent: com.nxredux.mobile.settings.SecondaryAccent = com.nxredux.mobile.settings.SecondaryAccent.DEFAULT
    }
    private fun controller(platform: RaPlatform, settings: Settings, scope: CoroutineScope, messages: MutableList<RaMessage>): RaController {
        val c = RaController(platform, settings, { null }, scope, now = { 1_000_000L })
        scope.launch { c.messages.collect { messages += it } }
        return c
    }

    @Test fun refresh_reads_status_from_the_cache_and_journal() = runTest {
        val store = FakeRaStore()
        store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","Score":1,"SoftcoreScore":234}""")
        store.put(RaPaths.sets("a".repeat(32)), setsJson(1)); store.put(RaPaths.LAST_SYNC, "999")
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 1, "a".repeat(32), 5L))
        val settings = Settings().apply { raUsername = "me"; raToken = "tok" }
        val c = controller(fakePlatform(store), settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList())
        c.refresh()
        assertEquals(RaState.Idle(RaStatus("me", 234, 1, 999_000L, 1)), c.state.value)
    }
    @Test fun login_exchanges_the_password_for_a_token_and_caches_login() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        http.on("login2", body = """{"Success":true,"User":"Me","Token":"tok","Score":1,"SoftcoreScore":5}""")
        val settings = Settings(); val messages = ArrayList<RaMessage>()
        val c = controller(fakePlatform(store, http), settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.login("me", "secret")
        assertEquals("tok", settings.raToken); assertEquals("Me", settings.raUsername); assertTrue(settings.raEnabled)
        assertEquals(listOf("r=login2&u=me&p=secret"), http.posts)
        assertTrue(store.text(RaPaths.login())!!.contains("\"Token\":\"tok\""))
        assertEquals(RaMessage.Notice("Signed in as Me"), messages.single())
        assertEquals(RaState.Idle(RaStatus("Me", 5, 0, null, 0)), c.state.value)
    }
    @Test fun login_failure_keeps_the_status_error_and_no_token() = runTest {
        val http = FakeRaHttp(); http.on("login2", body = """{"Success":false,"Error":"Invalid User/Password combination. Please try again"}""")
        val settings = Settings()
        val c = controller(fakePlatform(http = http), settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList())
        c.login("me", "bad")
        assertNull(settings.raToken)
        assertEquals("Invalid User/Password combination. Please try again", assertIs<RaState.Idle>(c.state.value).status.lastError)
        http.offline = true
        c.login("me", "bad")
        assertEquals("No connection", assertIs<RaState.Idle>(c.state.value).status.lastError)
    }
    @Test fun sync_now_runs_and_reports_and_is_ignored_while_busy() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":1}""")
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 1, "a".repeat(32), 5L))
        val gate = CompletableDeferred<Unit>()
        http.responders["awardachievement"] = { gate.await(); RaResponse(200, """{"Success":true,"AchievementID":1,"Score":1,"SoftcoreScore":11}""") }
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":11}""")
        val settings = Settings().apply { raUsername = "me"; raToken = "tok" }; val messages = ArrayList<RaMessage>()
        val c = controller(fakePlatform(store, http), settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.refresh(); c.syncNow()
        assertIs<RaState.Syncing>(c.state.value); assertTrue(c.busy)
        c.syncNow(); c.prefetchAll(); c.login("me", "pw"); c.resetAccount()   // all ignored while busy
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, http.posts.count { it.startsWith("r=awardachievement") })
        assertTrue(http.posts.none { it.contains("p=pw") })
        assertEquals("tok", settings.raToken)
        assertEquals(RaMessage.Notice("Synced 1 unlock · 11 points"), messages.single())
        assertEquals(RaState.Idle(RaStatus("me", 11, 0, 1_000_000L, 0)), c.state.value)   // lastsync "1000" s → ms
    }
    @Test fun sync_needs_a_token_and_a_connection() = runTest {
        val messages = ArrayList<RaMessage>()
        val c = controller(fakePlatform(connectivity = FakeConnectivity(false)), Settings().apply { raToken = "tok"; raUsername = "me" }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.refresh(); c.syncNow()
        assertEquals(RaMessage.Failure("No connection"), messages.last())
        val c2 = controller(fakePlatform(), Settings(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c2.refresh(); c2.syncNow()
        assertEquals(RaMessage.Failure("Sign in first"), messages.last())
    }
    @Test fun prefetch_reports_counts() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":1}""")
        http.responders["gameid"] = { RaResponse(200, """{"Success":true,"GameID":0}""") }
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = "a".repeat(32) }
        val index = com.nxredux.mobile.library.LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(com.nxredux.mobile.library.IndexedSystem("GBA", "GBA", games = listOf(com.nxredux.mobile.library.IndexedGame("home", "d", "Zelda.gba", "r", "f", "Zelda")))))
        val settings = Settings().apply { raUsername = "me"; raToken = "tok" }; val messages = ArrayList<RaMessage>()
        val c = RaController(fakePlatform(store, http, hasher), settings, { index }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { 0L })
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)).launch { c.messages.collect { messages += it } }
        c.refresh(); c.prefetchAll()
        assertEquals(RaMessage.Notice("Game data: 0 cached · 1 without achievements · 0 skipped"), messages.single())
        assertIs<RaState.Idle>(c.state.value)
    }
    /** A signed-in controller over [names] (GBA games; each hashes to its own md5, the server knows none), on a clock it moves. */
    private class Download(val c: RaController, val http: FakeRaHttp, val clock: LongArray, val messages: MutableList<RaMessage>)
    private fun kotlinx.coroutines.test.TestScope.download(names: List<String>, unreadable: Set<String> = emptySet()): Download {
        val store = FakeRaStore(); val http = FakeRaHttp(); val clock = longArrayOf(0L)
        store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":1}""")
        http.responders["gameid"] = { RaResponse(200, """{"Success":true,"GameID":0}""") }
        val hasher = FakeRaHasher().apply { names.forEachIndexed { i, n -> hashes["$n.gba"] = if (n in unreadable) null else ('a' + i).toString().repeat(32) } }
        val games = names.map { com.nxredux.mobile.library.IndexedGame("home", "d", "$it.gba", "r$it", "f", it) }
        val index = com.nxredux.mobile.library.LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(com.nxredux.mobile.library.IndexedSystem("GBA", "GBA", games = games)))
        val settings = Settings().apply { raUsername = "me"; raToken = "tok" }; val messages = ArrayList<RaMessage>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val c = RaController(fakePlatform(store, http, hasher), settings, { index }, scope, now = { clock[0] })
        scope.launch { c.messages.collect { messages += it } }
        c.refresh()
        return Download(c, http, clock, messages)
    }

    // The download's page: the games still to do (in flight first), the skipped and failed after them; a finished game leaves.
    @Test fun a_download_lists_its_games_and_finished_ones_leave() = runTest {
        val d = download(listOf("Zelda", "Metroid", "Nope"), unreadable = setOf("Nope"))
        d.c.prefetchAll(); advanceUntilIdle()   // the requests are paced
        val dl = assertNotNullDownload(d.c)
        assertEquals(RaDownloadPhase.DONE, dl.phase)
        assertEquals(3, dl.total); assertEquals(3, dl.finished)
        assertEquals(listOf(RaDownloadItem("Nope", RaItemStatus.SKIPPED, RaPrefetch.UNREADABLE)), dl.items)
        assertEquals(Triple(0, 2, 1), Triple(dl.cached, dl.unknown, dl.skipped))
        assertIs<RaState.Idle>(d.c.state.value)
    }

    @Test fun pause_holds_the_download_between_games_and_resume_goes_on() = runTest {
        val d = download(listOf("Zelda", "Metroid", "Kirby"))
        val first = CompletableDeferred<Unit>()
        d.http.responders["gameid"] = { if (!first.isCompleted) first.await(); RaResponse(200, """{"Success":true,"GameID":0}""") }
        d.c.prefetchAll()
        assertEquals(listOf("Zelda", "Metroid", "Kirby"), assertNotNullDownload(d.c).items.map { it.name })
        assertEquals(RaItemStatus.DOWNLOADING, assertNotNullDownload(d.c).items.first().status)   // in flight first
        d.c.pause()
        first.complete(Unit); advanceUntilIdle()
        val held = assertNotNullDownload(d.c)
        assertEquals(RaDownloadPhase.PAUSED, held.phase); assertEquals(1, held.finished)
        assertEquals(listOf("Metroid", "Kirby"), held.items.map { it.name })   // Zelda finished: gone
        assertEquals(true, assertIs<RaState.Prefetching>(d.c.state.value).paused)
        d.c.resume(); advanceUntilIdle()
        assertEquals(RaDownloadPhase.DONE, assertNotNullDownload(d.c).phase)
        assertEquals(3, assertNotNullDownload(d.c).finished)
    }

    @Test fun cancel_ends_the_download_as_cancelled_even_while_paused() = runTest {
        val d = download(listOf("Zelda", "Metroid"))
        val first = CompletableDeferred<Unit>()
        d.http.responders["gameid"] = { if (!first.isCompleted) first.await(); RaResponse(200, """{"Success":true,"GameID":0}""") }
        d.c.prefetchAll(); d.c.pause(); first.complete(Unit); advanceUntilIdle()
        d.c.cancel(); advanceUntilIdle()
        assertEquals(RaDownloadPhase.CANCELLED, assertNotNullDownload(d.c).phase)
        assertIs<RaState.Idle>(d.c.state.value)
    }

    // The time left follows the measured pace (paced requests, instant cached games), once three games have finished.
    @Test fun the_time_left_follows_the_measured_pace() = runTest {
        val d = download(listOf("A", "B", "C", "D", "E"))
        val fourth = CompletableDeferred<Unit>(); var asks = 0
        d.http.responders["gameid"] = { asks++; d.clock[0] += 2_000; if (asks == 4) fourth.await(); RaResponse(200, """{"Success":true,"GameID":0}""") }
        d.c.prefetchAll(); advanceUntilIdle()   // paced; held on the fourth game's request
        val mid = assertNotNullDownload(d.c)
        assertEquals(3, mid.finished)
        assertEquals(4L, mid.secondsLeft)                                  // 2 s a game, 2 games left
        fourth.complete(Unit); advanceUntilIdle()
    }

    private fun assertNotNullDownload(c: RaController): RaDownload = c.download.value ?: error("no download")

    @Test fun reset_account_and_erase_all_scope_their_deletions() = runTest {
        val store = FakeRaStore()
        store.put(RaPaths.login(), "{}"); store.put(RaPaths.session(1), "{}"); store.put(RaPaths.JOURNAL, "x\n"); store.put(RaPaths.sets("a".repeat(32)), "{}")
        val settings = Settings().apply { raToken = "tok"; raUsername = "me" }
        val c = controller(fakePlatform(store), settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList())
        c.refresh(); c.resetAccount()
        assertNull(settings.raToken); assertEquals(setOf(RaPaths.sets("a".repeat(32))), store.files.keys)
        c.eraseAll()
        assertTrue(store.files.isEmpty())
    }
    @Test fun ra_paths_split_account_and_shared() {
        assertEquals("android/ra/pending/unlocks.jsonl", RaPaths.JOURNAL)
        assertEquals("android/ra/cache/login.json", RaPaths.login())
        assertEquals("android/ra/muted/${"a".repeat(32)}.txt", RaPaths.muted("a".repeat(32)))
        assertEquals("shared/.ra/cache/games/${"a".repeat(32)}/sets.json", RaPaths.sets("a".repeat(32)))
        assertEquals("shared/.ra/cache/games/${"a".repeat(32)}/rom.txt", RaPaths.romRecord("a".repeat(32)))
    }
    @Test fun erase_all_keeps_the_handhelds_files() = runTest {
        val store = FakeRaStore()
        store.put(RaPaths.login(), "{}"); store.put(RaPaths.JOURNAL, "x\n"); store.put(RaPaths.sets("a".repeat(32)), "{}")
        store.put("shared/.ra/pending/unlocks.jsonl", "handheld\n"); store.put("shared/.ra/cache/login.json", "{}")
        val c = controller(fakePlatform(store), Settings(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList())
        c.eraseAll(); advanceUntilIdle()
        assertEquals(setOf("shared/.ra/pending/unlocks.jsonl", "shared/.ra/cache/login.json"), store.files.keys)
    }
    @Test fun sign_out_forgets_only_the_token_and_cached_login() = runTest {
        val store = FakeRaStore()
        store.put(RaPaths.login(), "{}"); store.put(RaPaths.session(1), "{}"); store.put(RaPaths.JOURNAL, "x\n"); store.put(RaPaths.sets("a".repeat(32)), "{}")
        val settings = Settings().apply { raToken = "tok"; raUsername = "me"; raEnabled = true }; val messages = ArrayList<RaMessage>()
        val c = controller(fakePlatform(store), settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.refresh(); c.signOut()
        assertNull(settings.raToken); assertFalse(settings.raEnabled); assertEquals("me", settings.raUsername)
        assertEquals(setOf(RaPaths.session(1), RaPaths.JOURNAL, RaPaths.sets("a".repeat(32))), store.files.keys)
        assertEquals(RaMessage.Notice("Signed out"), messages.single())
        assertIs<RaState.Idle>(c.state.value)
    }
    @Test fun cancel_mid_sync_restores_the_previous_status() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":7}""")
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 1, "a".repeat(32), 5L))
        val gate = CompletableDeferred<Unit>()
        http.responders["awardachievement"] = { gate.await(); RaResponse(200, """{"Success":true,"AchievementID":1,"SoftcoreScore":8}""") }
        val messages = ArrayList<RaMessage>()
        val c = controller(fakePlatform(store, http), Settings().apply { raUsername = "me"; raToken = "tok" }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.refresh()
        val previous = assertIs<RaState.Idle>(c.state.value).status
        assertEquals(RaStatus("me", 7, 1, null, 0), previous)
        c.syncNow()
        assertIs<RaState.Syncing>(c.state.value)
        c.cancel(); advanceUntilIdle()
        assertEquals(RaState.Idle(previous), c.state.value)
        assertEquals(1, RaJournal(store, RaPaths.JOURNAL).count("me"))
        assertTrue(messages.isEmpty())
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(RaState.Idle(previous), c.state.value)   // the cancelled job never resumes
    }
    @Test fun cancel_follow_up_refresh_does_not_clobber_a_sync_started_meanwhile() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":7}""")
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 1, "a".repeat(32), 5L))
        val awardGate = CompletableDeferred<Unit>()
        http.responders["awardachievement"] = { awardGate.await(); RaResponse(200, """{"Success":true,"AchievementID":1,"SoftcoreScore":8}""") }
        val c = controller(fakePlatform(store, http), Settings().apply { raUsername = "me"; raToken = "tok" }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList())
        c.refresh(); c.syncNow()
        val listGate = CompletableDeferred<Unit>()
        store.beforeList = { listGate.await() }
        c.cancel()                          // its follow-up status read is now parked in list()
        c.syncNow()                         // a new sync starts while that read is suspended
        assertIs<RaState.Syncing>(c.state.value)
        listGate.complete(Unit); advanceUntilIdle()
        assertIs<RaState.Syncing>(c.state.value)
    }
    @Test fun cancel_while_authenticating_or_resetting_does_nothing() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        val loginGate = CompletableDeferred<Unit>()
        http.responders["login2"] = { loginGate.await(); RaResponse(200, """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":5}""") }
        val settings = Settings()
        val c = controller(fakePlatform(store, http), settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList())
        c.login("me", "pw")
        c.cancel()
        assertEquals(RaState.Authenticating, c.state.value)
        loginGate.complete(Unit); advanceUntilIdle()
        assertEquals("tok", settings.raToken)
        assertEquals(RaState.Idle(RaStatus("me", 5, 0, null, 0)), c.state.value)

        val deleteGate = CompletableDeferred<Unit>()
        store.beforeDelete = { deleteGate.await() }
        c.resetAccount()
        val resetting = assertIs<RaState.Resetting>(c.state.value)
        c.cancel()
        assertEquals(resetting, c.state.value)
        deleteGate.complete(Unit); advanceUntilIdle()
        assertNull(settings.raToken); assertNull(store.text(RaPaths.login()))
        assertEquals(RaState.Idle(RaStatus(null, null, 0, null, 0)), c.state.value)
    }
    @Test fun a_failed_status_read_after_login_or_reset_still_ends_idle() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":5}""")
        val settings = Settings(); val messages = ArrayList<RaMessage>()
        val c = controller(fakePlatform(store, http), settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        store.failList = true
        c.login("me", "pw")
        assertEquals("tok", settings.raToken)
        assertEquals("Sign-in failed: RaStoreException", assertIs<RaState.Idle>(c.state.value).status.lastError)
        assertFalse(c.busy)
        store.failList = false; c.refresh()
        val previous = assertIs<RaState.Idle>(c.state.value).status
        store.failList = true
        c.resetAccount()
        assertEquals(RaState.Idle(previous), c.state.value)
        assertEquals(RaMessage.Failure("Could not read status: RaStoreException"), messages.last())
        assertFalse(c.busy)
    }

    @Test fun rememberGameWritesAPointerOnceAndKeepsAPrefetchedOne() = runTest {
        val store = FakeRaStore(); val hash = "a".repeat(32)
        val c = controller(fakePlatform(store), Settings(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList())
        c.rememberGame(hash, RaGamePointer("GBA", "home", "Roms/Game Boy Advance (GBA)", "A.gba", "First"))
        c.rememberGame(hash, RaGamePointer("GBA", "home", "Roms/Game Boy Advance (GBA)", "A.gba", "Second"))
        assertEquals("First", RaJson.parse(RaGamePointer.serializer(), store.text(RaPaths.game(hash))!!)?.title)
    }

    @Test fun sync_messages_name_the_stop_and_count_what_stays() {
        assertEquals(RaMessage.Notice("Up to date"), syncMessage(RaSyncResult(0, 0, 5, false)))
        assertEquals(RaMessage.Notice("Nothing synced · 2 rejected"), syncMessage(RaSyncResult(0, 0, 5, false, rejected = 2)))
        assertEquals(RaMessage.Notice("Nothing synced · 1 still pending"), syncMessage(RaSyncResult(0, 0, 5, false, remaining = 1)))
        assertEquals(RaMessage.Notice("Synced 1 unlock · 5 points · 1 rejected"), syncMessage(RaSyncResult(1, 0, 5, false, rejected = 1)))
        assertEquals(RaMessage.Failure("Sign in again: RetroAchievements refused the saved login"), syncMessage(RaSyncResult(0, 0, null, true, remaining = 3, stop = RaSyncStop.AUTH)))
        assertEquals(RaMessage.Failure("RetroAchievements is busy, try later (synced 2)"), syncMessage(RaSyncResult(2, 0, null, true, stop = RaSyncStop.BUSY)))
        assertEquals(RaMessage.Failure("Access denied."), syncMessage(RaSyncResult(0, 0, null, true, stop = RaSyncStop.REFUSED, error = "Access denied.")))
        assertEquals(RaMessage.Failure("Sync failed after 1"), syncMessage(RaSyncResult(1, 0, null, true)))
    }
    @Test fun a_refused_login_during_sync_shows_sign_in_again_in_the_status() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 1, "a".repeat(32), 5L))
        http.on("awardachievement", status = 401, body = """{"Success":false,"Status":401,"Code":"invalid_credentials","Error":"Invalid user/token combination."}""")
        val messages = ArrayList<RaMessage>()
        val c = controller(fakePlatform(store, http), Settings().apply { raUsername = "me"; raToken = "tok" }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.refresh(); c.syncNow(); advanceUntilIdle()
        assertEquals(RaMessage.Failure("Sign in again: RetroAchievements refused the saved login"), messages.single())
        val status = assertIs<RaState.Idle>(c.state.value).status
        assertEquals("Sign in again: RetroAchievements refused the saved login", status.lastError); assertEquals(1, status.pending)
    }

    // The walks' reads and parses (a cached game's sets.json, the journal, the status read) run on [work], off the
    // caller's (main) dispatcher; the download's page and the state end as they always did.
    @Test fun the_download_and_the_sync_read_and_parse_on_the_work_context() = runTest {
        val store = FakeRaStore(); val http = FakeRaHttp()
        store.put(RaPaths.login(), """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":1}""")
        val cachedHash = "a".repeat(32)
        store.put(RaPaths.sets(cachedHash), setsJson(0))
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 1, cachedHash, 5L))
        http.responders["gameid"] = { RaResponse(200, """{"Success":true,"GameID":0}""") }
        http.on("awardachievement", body = """{"Success":true,"AchievementID":1,"Score":1,"SoftcoreScore":11}""")
        http.on("login2", body = """{"Success":true,"User":"me","Token":"tok","SoftcoreScore":11}""")
        val hasher = FakeRaHasher().apply { hashes["Zelda.gba"] = cachedHash; hashes["Metroid.gba"] = "b".repeat(32); hashes["Nope.gba"] = null }
        val games = listOf("Zelda", "Metroid", "Nope").map { com.nxredux.mobile.library.IndexedGame("home", "d", "$it.gba", "r$it", "f", it) }
        val index = com.nxredux.mobile.library.LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(com.nxredux.mobile.library.IndexedSystem("GBA", "GBA", games = games)))
        val offWork = ArrayList<String>()
        store.beforeRead = { if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.CoroutineName]?.name != "work") offWork += "read" }
        val settings = Settings().apply { raUsername = "me"; raToken = "tok" }; val messages = ArrayList<RaMessage>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val c = RaController(fakePlatform(store, http, hasher), settings, { index }, scope, now = { 1_000_000L }, work = kotlinx.coroutines.CoroutineName("work"))
        scope.launch { c.messages.collect { messages += it } }
        c.refresh(); advanceUntilIdle()
        c.prefetchAll(); advanceUntilIdle()
        val dl = assertNotNullDownload(c)
        assertEquals(RaDownloadPhase.DONE, dl.phase); assertEquals(3, dl.finished)
        assertEquals(listOf(RaDownloadItem("Nope", RaItemStatus.SKIPPED, RaPrefetch.UNREADABLE)), dl.items)
        assertEquals(Triple(0, 1, 2), Triple(dl.cached, dl.unknown, dl.skipped))
        assertTrue(RaPaths.sets(cachedHash) in store.reads)   // the cached game's sets.json was read (and parsed)
        c.syncNow(); advanceUntilIdle()
        assertEquals(RaMessage.Notice("Synced 1 unlock · 11 points"), messages.last())
        assertEquals(RaState.Idle(RaStatus("me", 11, 0, 1_000_000L, 1)), c.state.value)
        assertEquals(emptyList(), offWork)
    }
}
