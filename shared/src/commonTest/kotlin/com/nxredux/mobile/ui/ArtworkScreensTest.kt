package com.nxredux.mobile.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.launcher.AppLauncher
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.scraper.ArtSlot
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.settings.GameListArt
import com.nxredux.mobile.scraper.ArtworkController
import com.nxredux.mobile.scraper.SgdbFixtures
import com.nxredux.mobile.scraper.FakeArtStore
import com.nxredux.mobile.scraper.FakeFitter
import com.nxredux.mobile.scraper.FakeHasher
import com.nxredux.mobile.scraper.FakeHttp
import com.nxredux.mobile.scraper.FakePixels
import com.nxredux.mobile.scraper.FakeRunner
import com.nxredux.mobile.scraper.HttpResult
import com.nxredux.mobile.scraper.ItemStatus
import com.nxredux.mobile.scraper.JobPhase
import com.nxredux.mobile.scraper.ResetResult
import com.nxredux.mobile.scraper.ScrapeItem
import com.nxredux.mobile.scraper.ScrapeState
import com.nxredux.mobile.scraper.Scrape
import com.nxredux.mobile.scraper.ssEngine
import com.nxredux.mobile.scraper.ScraperCredentials
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.storage.MemoryPrivateFiles
import com.nxredux.mobile.storage.PrivateFiles
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.HintTouch
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.nxTap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkScreensTest {
    private class Settings : AppSettings {
        override var treeRef: ContentRef? = ContentRef("Home")
        override var onboardingDone: Boolean? = true
        override var onboardingStep: String? = null
        override var extraFolders: List<ExtraFolder> = emptyList()
        override var defaultTags: Map<String, String> = emptyMap()
        override var padSkin = true
        override var padTheme = PadTheme.DEFAULT
        override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap()
        override var gameListStyle: MenuStyle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_GAME_LIST_STYLE
        override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap()
        override var gameListOrientation: MenuOrientation = com.nxredux.mobile.settings.MenuStyles.DEFAULT_ORIENTATION
        override var gameListVerticalAlignment: com.nxredux.mobile.settings.VerticalListAlignment = com.nxredux.mobile.settings.VerticalListAlignment.DEFAULT
        override var toolApps: List<String> = emptyList()
        override var androidConsoleApps: List<String> = emptyList()
        override var androidGamesAsked = false
        override var androidPins: List<String> = emptyList()
        override var raEnabled = false
        override var raUsername = ""
        override var raToken: String? = null
        override var raShowNotifications = true
        override var raNotificationSeconds = 3
        override var raProgressSeconds = 1
        override var raSortOrder = RaSortOrder.DEFAULT
        override var ssUsername = ""
        override var ssPassword: String? = null
        override var sgdbApiKey: String? = null
        override var tgdbApiKey: String? = null
        override var artReplaceExisting = false
        override var artBox2d = false
        override var artWheel = false
        override var artMix = false
        override var artWheelMigrated = false
        override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showNetplayHint = true; override var menuPageTitle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = com.nxredux.mobile.settings.MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = com.nxredux.mobile.settings.AccentPreset.DEFAULT; override var secondaryAccent: com.nxredux.mobile.settings.SecondaryAccent = com.nxredux.mobile.settings.SecondaryAccent.DEFAULT
    }

    /** As on Android: the login lives in the settings. */
    private class Credentials(private val s: Settings, dev: String) : ScraperCredentials {
        override val devId = dev
        override val devPassword = dev
        override var user: String?
            get() = s.ssUsername.ifEmpty { null }
            set(v) { s.ssUsername = v ?: "" }
        override var password: String?
            get() = s.ssPassword
            set(v) { s.ssPassword = v }
        override var sgdbKey: String?
            get() = s.sgdbApiKey
            set(v) { s.sgdbApiKey = v }
        override var tgdbUserKey: String?
            get() = s.tgdbApiKey
            set(v) { s.tgdbApiKey = v }
    }

    private object Picker : FolderPicker { override fun pickHome() {}; override fun pickExtra() {}; override fun forget(ref: ContentRef) {} }
    private object Runner : EmulatorRunner { override suspend fun launch(session: GameSession, recent: com.nxredux.mobile.storage.Recent) {} }

    /** The installed [apps] (no icons, nothing starts). */
    private class Launcher(private val apps: List<InstalledApp>) : AppLauncher {
        override suspend fun installedApps() = apps
        override fun label(id: String) = apps.firstOrNull { it.id == id }?.label
        override suspend fun icon(id: String): ImageBitmap? = null
        override fun launch(id: String) = false
        override fun isHome() = false
        override fun setHome(on: Boolean) {}
    }

    private class Rig(val state: AppState, val http: FakeHttp, val settings: Settings, val notices: MutableList<String>)

    /**
     * GB (Tetris, Zelda with a mix), GBA (Mario), MD (Sonic); [available] false = a build without dev credentials;
     * [unassigned] adds an extra folder whose loose 7z lands in Unassigned.
     */
    private fun TestScope.rig(
        available: Boolean = true, artwork: Boolean = true, files: PrivateFiles = MemoryPrivateFiles(), unassigned: Boolean = false,
        apps: List<InstalledApp>? = null, artStore: FakeArtStore? = null, consoleApps: List<String> = emptyList(),
        arcade: com.nxredux.mobile.library.ArcadeSets? = null, onHost: (com.nxredux.mobile.scraper.ArtworkHost) -> Unit = {},
        sgdb: Boolean = false, libretro: Boolean = false, tgdbUserKey: String? = null, tgdb: Boolean = false,
        onboardingAt: OnboardingStep? = null,   // onboarding to do, resumed at this step
        optionCache: com.nxredux.mobile.scraper.ArtOptionCache = com.nxredux.mobile.scraper.ArtOptionCache.NONE,
        store: com.nxredux.mobile.storage.MemoryLibraryIndexStore = com.nxredux.mobile.storage.MemoryLibraryIndexStore(),
        mixEngine: Boolean = false,   // the engine reads Settings' Mix and has a codec, as the app's
        stored: Settings.() -> Unit = {},   // settings as an earlier version left them, before the first load
    ): Rig {
        val source = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"),
                "Home/Roms" to listOf("Game Boy (GB)", "Game Boy Advance (GBA)", "Sega Genesis (MD)") + listOfNotNull(arcade?.let { "Arcade (FBN)" }),
                "Home/Roms/Arcade (FBN)" to listOf("sf2.zip"),
                "sd/Loose" to listOf("Mystery.7z"),
                "Home/Roms/Game Boy (GB)" to listOf("Tetris.gb", "Zelda.gb", ".media"),
                "Home/Roms/Game Boy (GB)/.media" to listOf("Tetris.png", "box2d", "screenshot"),   // Tetris's NX Redux mix is not art
                "Home/Roms/Game Boy (GB)/.media/box2d" to listOf("Zelda.jpg"),
                "Home/Roms/Game Boy (GB)/.media/screenshot" to listOf("Zelda.jpg"),     // a box and a screenshot: Zelda has art
                "Home/Roms/Game Boy Advance (GBA)" to listOf("Mario.gba"),
                "Home/Roms/Sega Genesis (MD)" to listOf("Sonic.md"),
            ),
        )
        val settings = Settings().apply {
            if (unassigned) extraFolders = listOf(ExtraFolder("x1", ContentRef("sd/Loose")))
            androidConsoleApps = consoleApps; androidGamesAsked = true
            tgdbApiKey = tgdbUserKey
            if (onboardingAt != null) { onboardingDone = false; onboardingStep = onboardingAt.name }
            stored()
        }
        val http = FakeHttp().apply { clock = { testScheduler.currentTime } }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val factory: ((com.nxredux.mobile.scraper.ArtworkHost) -> ArtworkController)? = if (!artwork) null else { host ->
            onHost(host)
            ArtworkController(
                host,
                { auth, onArt, onWritten, onAndroidArt, logged, onOptions, picked ->
                    val now = { testScheduler.currentTime }
                    val more = listOfNotNull(
                        if (libretro) com.nxredux.mobile.scraper.LibretroThumbnails(http, now) { delay(it) } else null,
                        if (tgdbUserKey != null || tgdb) com.nxredux.mobile.scraper.TheGamesDbSource(http, { settings.tgdbApiKey.orEmpty() }, now) { delay(it) } else null,
                        if (sgdb) com.nxredux.mobile.scraper.SteamGridDbSource(http, { settings.sgdbApiKey }, now) { delay(it) } else null,
                    )
                    ssEngine(
                        http, FakeFitter(), FakeHasher(), source, ContentRef("Home"), auth, { testScheduler.currentTime }, { delay(it) }, onArt, onWritten, artStore, onAndroidArt, more, logged, onOptions, picked,
                        extras = { com.nxredux.mobile.scraper.ArtExtras(mix = mixEngine && settings.artMix) }, pixels = if (mixEngine) FakePixels() else null,
                    )
                },
                Credentials(settings, if (available) "DEV" else ""), FakeRunner(), scope, optionCache = optionCache,
            )
        }
        val state = AppState(
            AppDeps(
                settings, source, Runner, Picker, artwork = factory, privateFiles = files, indexStore = store,
                launcher = apps?.let(::Launcher), androidArtStore = artStore, compute = UnconfinedTestDispatcher(testScheduler),
                arcadeSets = { arcade ?: com.nxredux.mobile.library.ArcadeSets.EMPTY },
            ),
            scope,
        )
        val notices = ArrayList<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.notice.collect { it?.let(notices::add) } }
        state.load()
        advanceUntilIdle()
        return Rig(state, http, settings, notices)
    }

    private fun AppState.openArtwork() {
        openTools()
        val rows = assertIs<Screen.Systems>(screen.value).also { assertEquals(MenuCategory.TOOLS, it.category) }.rows.map { (it as MainRow.Tool).row }
        select(rows.indexOfFirst { it.kind == ToolKind.ARTWORK }); openSelected()
    }

    private fun AppState.pick(action: ArtworkAction) {
        val rows = assertIs<Screen.Artwork>(screen.value).rows
        val at = rows.indexOfFirst { it.action == action }; assertTrue(at >= 0, "no $action in $rows")
        select(at); onButton(NxButton.A)
    }

    private fun AppState.pickRow(label: String, labels: List<String>) {
        val at = labels.indexOf(label); assertTrue(at >= 0, "no $label in $labels")
        select(at); onButton(NxButton.A)
    }

    // Onboarding's artwork step: Fetch now starts the job in the background and goes on to the layout.
    @Test fun onboarding_fetch_now_starts_the_artwork_job_and_moves_on() = runTest {
        val r = rig(onboardingAt = OnboardingStep.ARTWORK)
        val s = assertIs<Screen.Onboarding>(r.state.screen.value)
        // The source's account above the two buttons; the page opens on Fetch now.
        assertEquals(listOf("ScreenScraper" to "Sign in", "Fetch now" to null, "Skip" to null), s.rows.map { it.label to it.secondary })
        assertEquals(1, r.state.selection.value.index)
        assertTrue(s.details.any { "4 games" in it }, "${s.details}")
        assertTrue(s.details.any { "Signing in is optional" in it }, "${s.details}")   // anonymous fetches still run
        r.state.onButton(NxButton.A)
        assertEquals(OnboardingStep.RA_SIGN_IN, assertIs<Screen.Onboarding>(r.state.screen.value).step)
        advanceUntilIdle()
        assertTrue(r.http.calls.isNotEmpty())                           // the fetch ran
    }

    @Test fun onboarding_artwork_without_a_source_only_continues() = runTest {
        val r = rig(available = false, onboardingAt = OnboardingStep.ARTWORK)
        val s = assertIs<Screen.Onboarding>(r.state.screen.value)
        assertEquals(listOf("Skip"), s.rows.map { it.label })   // nothing to fetch from, no account to set here: Skip alone
        r.state.onButton(NxButton.A)
        assertEquals(OnboardingStep.RA_SIGN_IN, assertIs<Screen.Onboarding>(r.state.screen.value).step)
        assertTrue(r.http.calls.isEmpty())
    }

    // The ScreenScraper row opens its sign-in over the step (the name typed in, the password kept out of the row); B closes it.
    @Test fun onboarding_artwork_screenscraper_row_opens_its_sign_in() = runTest {
        val r = rig(onboardingAt = OnboardingStep.ARTWORK)
        r.state.select(0); r.state.onButton(NxButton.A)
        assertTrue(assertIs<Screen.Onboarding>(r.state.screen.value).artSignIn)
        r.state.onboardingDraftUser("player")
        assertEquals("player", assertIs<Screen.Onboarding>(r.state.screen.value).draftUser)
        r.state.onButton(NxButton.B)
        val s = assertIs<Screen.Onboarding>(r.state.screen.value)
        assertEquals(false, s.artSignIn)
        assertEquals(OnboardingStep.ARTWORK, s.step)
    }

    @Test fun tools_shows_artwork_only_with_a_controller_and_opens_with_the_counts() = runTest {
        val without = rig(artwork = false)
        without.state.openTools()
        assertTrue(assertIs<Screen.Systems>(without.state.screen.value).also { assertEquals(MenuCategory.TOOLS, it.category) }.rows.map { (it as MainRow.Tool).row }.none { it.kind == ToolKind.ARTWORK })

        val r = rig()
        r.state.openArtwork()
        val page = assertIs<Screen.Artwork>(r.state.screen.value)
        assertEquals(emptyList(), page.details)
        assertEquals(
            listOf(ArtworkAction.FETCH_MISSING, ArtworkAction.FETCH_BY_CONSOLE, ArtworkAction.SETTINGS),
            page.rows.map { it.action },
        )
        r.state.onButton(NxButton.B)
        assertIs<Screen.Systems>(r.state.screen.value).also { assertEquals(MenuCategory.TOOLS, it.category) }
    }

    // The page's words (the Settings-style block): the job's phases change them, Resume comes with a pause and goes.
    @Test fun the_page_words_follow_the_job_and_resume_comes_and_goes() = runTest {
        val r = rig()
        r.http.latency = 5_000
        fun words() = assertIs<Screen.Artwork>(r.state.screen.value).rows.map { it.label }
        r.state.openArtwork()
        assertEquals(listOf("Fetch missing art", "Fetch by console", "Settings"), words())
        r.state.onButton(NxButton.B)
        r.settings.artReplaceExisting = true
        r.state.openArtwork()
        assertEquals("Fetch all art", words().first())
        r.settings.artReplaceExisting = false
        r.state.onButton(NxButton.B); r.state.openArtwork()

        r.state.pick(ArtworkAction.FETCH_MISSING)
        r.state.onButton(NxButton.B)
        assertEquals(listOf("Fetch missing art", "Fetch by console", "Progress", "Settings"), words())

        r.state.pick(ArtworkAction.PROGRESS)
        r.state.onButton(NxButton.Y); runCurrent()
        val job = assertIs<Screen.ArtworkProgress>(r.state.screen.value).job
        assertEquals(JobPhase.PAUSED, job.phase)
        r.state.onButton(NxButton.B)
        assertEquals("Resume art fetch (${job.done}/${job.total})", words().first())

        r.state.pick(ArtworkAction.RESUME); runCurrent()
        if (r.state.screen.value !is Screen.Artwork) r.state.onButton(NxButton.B)
        assertTrue(words().none { it.startsWith("Resume") }, "${words()}")
        advanceUntilIdle()
        r.state.pick(ArtworkAction.PROGRESS)
        assertEquals(JobPhase.DONE, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.phase)
        r.state.onButton(NxButton.A); runCurrent()   // Done clears it
        assertEquals(listOf("Fetch missing art", "Fetch by console", "Settings"), words())
    }

    // The status lines at the top of the page are its details: the job only; no counts, no source credit.
    @Test fun the_page_status_lines_are_the_details() = runTest {
        val r = rig()
        r.http.latency = 5_000
        fun lines() = assertIs<Screen.Artwork>(r.state.screen.value).details
        r.state.openArtwork()
        assertEquals(emptyList(), lines())

        r.state.pick(ArtworkAction.FETCH_MISSING)
        val running = assertIs<Screen.ArtworkProgress>(r.state.screen.value).job
        r.state.onButton(NxButton.B)
        assertEquals("Fetching ${running.done} / ${running.total} …", lines().last())

        r.state.pick(ArtworkAction.PROGRESS)
        r.state.onButton(NxButton.Y); runCurrent()
        val paused = assertIs<Screen.ArtworkProgress>(r.state.screen.value).job
        r.state.onButton(NxButton.B)
        assertEquals("Paused at ${paused.done} / ${paused.total}", lines().last())

        val off = rig(available = false)
        off.state.openArtwork()
        val page = assertIs<Screen.Artwork>(off.state.screen.value)
        assertEquals(listOf("Artwork fetching isn't available in this build."), page.details)
        assertEquals(listOf("Settings"), page.rows.map { it.label })
    }

    // A tap on a word selects and opens it at once (Settings' rule); B comes back to it, then to Tools.
    @Test fun a_tap_on_a_word_opens_it_and_b_goes_back_to_tools() = runTest {
        val r = rig()
        r.state.openArtwork()
        val at = assertIs<Screen.Artwork>(r.state.screen.value).rows.indexOfFirst { it.action == ArtworkAction.FETCH_BY_CONSOLE }
        r.state.select(at); r.state.openSelected()
        assertIs<Screen.ArtworkConsoles>(r.state.screen.value)
        r.state.onButton(NxButton.B)
        val page = assertIs<Screen.Artwork>(r.state.screen.value)
        assertEquals(ArtworkAction.FETCH_BY_CONSOLE, page.rows[r.state.selection.value.index].action)
        r.state.onButton(NxButton.B)
        assertEquals(MenuCategory.TOOLS, assertIs<Screen.Systems>(r.state.screen.value).category)
    }

    @Test fun the_counts_leave_out_unassigned_games() = runTest {
        val r = rig(unassigned = true)
        r.state.openArtwork()
        assertEquals(3, r.missing())   // the Unassigned game is in no console's count
    }

    /** Fetch by console's missing counts summed (the page shows no counts), back on the page after. */
    private fun Rig.missing(): Int {
        state.pick(ArtworkAction.FETCH_BY_CONSOLE)
        val n = assertIs<Screen.ArtworkConsoles>(state.screen.value).rows.sumOf { it.missing }
        state.onButton(NxButton.B)
        assertIs<Screen.Artwork>(state.screen.value)
        return n
    }

    /** A page host with one Game Boy game; [inputs] stands in for the index, hidden set and Android console. */
    private class OneGameHost : ArtworkScreenHost {
        var inputs: List<Any?> = listOf(Any())
        var games = listOf(
            com.nxredux.mobile.scraper.LibraryGameRef("/gb/Tetris.gb", "GB", "Tetris", "Tetris.gb", ContentRef("t"), "", "", folderGame = false, have = com.nxredux.mobile.scraper.ArtHave()),
        )
        override fun showNotice(text: String) {}
        override fun showTools() {}
        override fun games(): List<com.nxredux.mobile.scraper.LibraryGameRef> = games
        override fun gamesInputs(): List<Any?> = inputs
        override fun consoles(): List<Pair<String, Set<String>>> = emptyList()
    }

    @Test fun reset_says_when_it_paused_the_fetch() {
        assertEquals("Deleted 1 file", resetText(ResetResult(1)))
        assertEquals("Deleted 4 files · art fetch paused", resetText(ResetResult(4, pausedJob = true)))
    }

    @Test fun an_unavailable_build_keeps_only_art_settings() = runTest {
        val r = rig(available = false)
        r.state.openArtwork()
        val page = assertIs<Screen.Artwork>(r.state.screen.value)
        assertEquals(listOf(ArtworkAction.SETTINGS), page.rows.map { it.action })
        assertTrue("Artwork fetching isn't available in this build." in page.details)
        r.state.pick(ArtworkAction.SETTINGS)
        assertEquals(listOf(ArtworkSettingKind.CLEAR_CACHE, ArtworkSettingKind.RESET), assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows.map { it.kind })
    }

    @Test fun arcade_games_reach_the_scraper_with_their_dat_title() = runTest {
        var host: com.nxredux.mobile.scraper.ArtworkHost? = null
        val sets = com.nxredux.mobile.library.ArcadeSets.parse("sf2\tStreet Fighter II: The World Warrior (World 910522)\t\t\t0\n")
        rig(arcade = sets, onHost = { host = it })
        advanceUntilIdle()                                     // the scan reads the set table on deps.compute, the test scheduler
        val games = assertNotNull(host).libraryGames()
        assertEquals("Street Fighter II: The World Warrior (World 910522)", games.single { it.tag == "FBN" }.datTitle)
        assertNull(games.single { it.fileName == "Tetris.gb" }.datTitle)
    }

    private fun storedBoxes(store: com.nxredux.mobile.storage.MemoryLibraryIndexStore): Set<String> =
        assertNotNull(com.nxredux.mobile.library.LibraryIndexCodec.decode(assertNotNull(store.text))).systems.flatMap { it.games }.mapNotNull { it.boxart }.toSet()

    // Art results reach the index at once but are written and shown in batches: a Reset or a fetch of thousands of games
    // would otherwise rewrite the whole index file and rebuild the menu once per game.
    @Test fun art_results_are_written_once_they_stop_coming_and_none_is_lost() = runTest {
        var host: com.nxredux.mobile.scraper.ArtworkHost? = null
        val store = com.nxredux.mobile.storage.MemoryLibraryIndexStore()
        rig(onHost = { host = it }, store = store)
        val h = assertNotNull(host)
        val games = h.allLibraryGames()
        val writes = store.writes
        val boxes = HashSet<String>()
        repeat(5) { round ->
            games.forEachIndexed { i, g -> h.applyArt(g.gamePath, com.nxredux.mobile.library.ArtRefs(boxart = "r$round-$i.png")) }
        }
        games.indices.mapTo(boxes) { "r4-$it.png" }
        assertEquals(writes, store.writes)                                                  // 20 results, nothing written yet
        assertTrue(h.allLibraryGames().all { it.art.boxart in boxes }, "${h.allLibraryGames().map { it.art }}")   // but in memory at once
        advanceUntilIdle()                                                                  // they stopped coming: one write for all
        assertEquals(writes + 1, store.writes)
        assertEquals(boxes, storedBoxes(store))
    }

    @Test fun art_results_that_keep_coming_are_still_written_now_and_then_and_at_the_jobs_end() = runTest {
        var host: com.nxredux.mobile.scraper.ArtworkHost? = null
        val store = com.nxredux.mobile.storage.MemoryLibraryIndexStore()
        rig(onHost = { host = it }, store = store)
        val h = assertNotNull(host)
        val games = h.allLibraryGames()
        val writes = store.writes
        repeat(40) { i ->
            h.applyArt(games[i % games.size].gamePath, com.nxredux.mobile.library.ArtRefs(boxart = "b$i.png"))
            advanceTimeBy(1_000)   // one a second: never a quiet pause
        }
        assertTrue(store.writes - writes in 1..4, "${store.writes - writes} writes for 40 results")
        h.flushArt()                                                                        // the job's end
        assertEquals((36 until 40).map { "b$it.png" }.toSet(), storedBoxes(store))
    }

    @Test fun a_scan_meanwhile_writes_the_waiting_art_with_its_index() = runTest {
        var host: com.nxredux.mobile.scraper.ArtworkHost? = null
        val store = com.nxredux.mobile.storage.MemoryLibraryIndexStore()
        val r = rig(onHost = { host = it }, store = store)
        val h = assertNotNull(host)
        h.applyArt(h.allLibraryGames().first().gamePath, com.nxredux.mobile.library.ArtRefs(boxart = "x.png"))
        val writes = store.writes
        r.state.rescan()
        advanceUntilIdle()
        assertEquals(writes + 1, store.writes)   // the scan's write; the waiting art is not written over it afterwards
    }

    @Test fun fetch_by_console_starts_that_console_and_shows_progress() = runTest {
        val r = rig()
        r.http.latency = 5_000
        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_BY_CONSOLE)
        val consoles = assertIs<Screen.ArtworkConsoles>(r.state.screen.value).rows
        assertEquals(listOf("Game Boy" to 1, "Game Boy Advance" to 1, "Sega Genesis" to 1), consoles.map { it.name to it.missing })
        r.state.pickRow("Game Boy", consoles.map { it.name })
        val progress = assertIs<Screen.ArtworkProgress>(r.state.screen.value)
        assertEquals(JobPhase.RUNNING, progress.job.phase)
        assertEquals(listOf("Tetris" to "GB"), progress.job.items.map { it.name to it.tag })   // Zelda has its mix already
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.phase)
    }

    @Test fun progress_pauses_resumes_cancels_after_asking_and_goes_back() = runTest {
        val r = rig()
        r.http.latency = 5_000
        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_MISSING)
        assertEquals(3, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.total)

        r.state.onButton(NxButton.Y); runCurrent()
        assertEquals(JobPhase.PAUSED, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.phase)
        assertTrue(progressHeader(assertIs<Screen.ArtworkProgress>(r.state.screen.value).job).endsWith("paused"))
        r.state.onButton(NxButton.Y); runCurrent()
        assertEquals(JobPhase.RUNNING, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.phase)

        r.state.onButton(NxButton.X)
        assertTrue(assertIs<Screen.ArtworkProgress>(r.state.screen.value).confirmCancel)
        r.state.onButton(NxButton.B)   // no: the job runs on
        assertFalse(assertIs<Screen.ArtworkProgress>(r.state.screen.value).confirmCancel)
        assertEquals(JobPhase.RUNNING, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.phase)
        r.state.onButton(NxButton.X); r.state.onButton(NxButton.A); runCurrent()
        assertEquals(JobPhase.CANCELLED, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.phase)

        r.state.onButton(NxButton.B)
        val page = assertIs<Screen.Artwork>(r.state.screen.value)
        assertEquals(ArtworkAction.PROGRESS, page.rows[r.state.selection.value.index].action)
    }

    // A finished fetch's page offers Done: A clears the job, back on the Artwork page with no Progress row.
    @Test fun done_clears_a_finished_fetch_and_its_progress_row() = runTest {
        val r = rig()
        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_MISSING); advanceUntilIdle()
        assertEquals(JobPhase.DONE, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.phase)
        assertEquals(Hint(NxButton.A, "Done", HintTouch.Button(primary = true)), artworkProgressHints(JobPhase.DONE).first())
        r.state.onButton(NxButton.A); runCurrent()
        val page = assertIs<Screen.Artwork>(r.state.screen.value)
        assertTrue(page.rows.none { it.action == ArtworkAction.PROGRESS })
    }
    // A done game leaves the list as it finishes (the summary counts it); an ended fetch clears its list to a summary:
    // only the games that need a look (not found, errors) stay.
    @Test fun an_ended_fetch_lists_only_the_games_to_look_at_and_sums_up() {
        fun item(name: String, st: ItemStatus) = ScrapeItem("p/$name", "GB", name, st)
        val items = listOf(item("A", ItemStatus.DONE), item("B", ItemStatus.PARTIAL), item("C", ItemStatus.NOT_FOUND), item("D", ItemStatus.ERROR), item("E", ItemStatus.WAITING), item("F", ItemStatus.SKIPPED))
        val running = ScrapeState(JobPhase.RUNNING, items)
        assertEquals(items - items[0], artworkProgressRows(running))       // running: every game but the done ones
        assertEquals(items - items[0], artworkProgressRows(running.copy(phase = JobPhase.PAUSED)))
        val cancelled = running.copy(phase = JobPhase.CANCELLED)
        assertEquals(listOf("C", "D"), artworkProgressRows(cancelled).map { it.name })
        assertEquals("1 fetched · 1 partial · 1 not found · 1 error · 1 skipped · 1 not fetched", artworkSummary(cancelled))
        assertEquals("1 fetched", artworkSummary(ScrapeState(JobPhase.DONE, listOf(item("A", ItemStatus.DONE)))))
    }
    @Test fun a_running_fetch_has_no_done() {
        assertTrue(artworkProgressHints(JobPhase.RUNNING).none { it.label == "Done" })
        assertTrue(artworkProgressHints(JobPhase.PAUSED).none { it.label == "Done" })
    }

    @Test fun account_sgdb_key_is_saved_only_when_valid_and_can_be_removed() = runTest {
        val r = rig(sgdb = true)
        r.state.openArtwork()
        r.state.pick(ArtworkAction.SETTINGS)
        val account = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertEquals(listOf(ArtworkSettingKind.REPLACE, ArtworkSettingKind.BOX2D, ArtworkSettingKind.WHEEL, ArtworkSettingKind.MIX, ArtworkSettingKind.SS_LOGIN, ArtworkSettingKind.SGDB_KEY, ArtworkSettingKind.CLEAR_CACHE, ArtworkSettingKind.RESET), account.rows.filter { it.selectable }.map { it.kind })
        assertEquals("Not set", account.rows[8].secondary)

        r.http.on("search/autocomplete/tetris", HttpResult(401, null))
        r.state.select(8); r.state.onButton(NxButton.A)
        assertTrue(assertIs<Screen.ArtworkSettings>(r.state.screen.value).editingKey)
        assertTrue(r.state.textEntryOpen)
        r.state.artworkDraftKey("WRONGKEY")
        assertFalse("WRONGKEY" in r.state.screen.value.toString())
        r.state.onButton(NxButton.A)
        advanceTimeBy(200)
        assertEquals("Checking key…", r.state.loadingMessage.value)
        advanceUntilIdle()
        assertEquals("SteamGridDB rejected this key", r.state.error.value)
        assertNull(r.settings.sgdbApiKey)
        assertFalse(assertIs<Screen.ArtworkSettings>(r.state.screen.value).editingKey)
        r.state.dismissError()

        r.http.set("search/autocomplete/tetris", HttpResult(200, "{\"success\":true,\"data\":[]}".encodeToByteArray()))
        r.state.onButton(NxButton.A)
        r.state.artworkDraftKey(" GOODKEY ")
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertNull(r.state.error.value)
        assertTrue("SteamGridDB key saved" in r.notices)
        assertEquals("GOODKEY", r.settings.sgdbApiKey)
        val saved = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertTrue(saved.rows.none { it.kind == ArtworkSettingKind.SGDB_KEY })
        assertEquals("Remove SteamGridDB key", saved.rows.single { it.kind == ArtworkSettingKind.SGDB_REMOVE }.label)
        assertEquals(ArtworkSettingKind.SGDB_REMOVE, saved.rows[r.state.selection.value.index].kind)

        r.state.select(saved.rows.indexOfFirst { it.kind == ArtworkSettingKind.SGDB_REMOVE }); r.state.onButton(NxButton.A)
        assertNull(r.settings.sgdbApiKey)
        assertTrue("SteamGridDB key removed" in r.notices)
        val removedSgdb = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertEquals(ArtworkSettingKind.SGDB_KEY, removedSgdb.rows.dropLast(3).last().kind)   // above the gap, Clear unused images and Reset artwork
        assertEquals("Not set", removedSgdb.rows.single { it.kind == ArtworkSettingKind.SGDB_KEY }.secondary)
        assertEquals(ArtworkSettingKind.SGDB_KEY, removedSgdb.rows[r.state.selection.value.index].kind)
        // B closes the prompt without a check.
        r.state.onButton(NxButton.A); r.state.artworkDraftKey("X"); r.state.onButton(NxButton.B)
        assertFalse(assertIs<Screen.ArtworkSettings>(r.state.screen.value).editingKey)
        assertNull(r.settings.sgdbApiKey)
    }

    @Test fun account_tgdb_key_is_saved_only_when_valid_and_can_be_removed() = runTest {
        val r = rig(tgdb = true)   // no TheGamesDB key until the user adds one
        r.state.openArtwork()
        r.state.pick(ArtworkAction.SETTINGS)
        val account = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertEquals(listOf(ArtworkSettingKind.REPLACE, ArtworkSettingKind.BOX2D, ArtworkSettingKind.WHEEL, ArtworkSettingKind.MIX, ArtworkSettingKind.SS_LOGIN, ArtworkSettingKind.TGDB_KEY, ArtworkSettingKind.CLEAR_CACHE, ArtworkSettingKind.RESET), account.rows.filter { it.selectable }.map { it.kind })
        assertEquals("TheGamesDB API key" to "Not set", account.rows[8].label to account.rows[8].secondary)

        r.http.on("API/Limit?apikey=WRONGKEY", HttpResult(200, """{"remaining_monthly_allowance":0,"allowance_refresh_timer":0}""".encodeToByteArray()))
        r.state.select(8); r.state.onButton(NxButton.A)
        val prompt = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertTrue(prompt.editingKey && prompt.keyFor == ArtKeyFor.TGDB)
        r.state.artworkDraftKey("WRONGKEY")
        assertFalse("WRONGKEY" in r.state.screen.value.toString())
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals("TheGamesDB rejected this key", r.state.error.value)
        assertNull(r.settings.tgdbApiKey)
        r.state.dismissError()

        r.http.on("API/Limit?apikey=GOODKEY", HttpResult(200, """{"remaining_monthly_allowance":1500,"extra_allowance":0,"allowance_refresh_timer":9}""".encodeToByteArray()))
        r.state.onButton(NxButton.A)
        r.state.artworkDraftKey(" GOODKEY ")
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertNull(r.state.error.value)
        assertTrue("TheGamesDB key saved" in r.notices)
        assertEquals("GOODKEY", r.settings.tgdbApiKey)
        val saved = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertTrue(saved.rows.none { it.kind == ArtworkSettingKind.TGDB_KEY })
        assertEquals("Remove TheGamesDB key", saved.rows.single { it.kind == ArtworkSettingKind.TGDB_REMOVE }.label)
        assertEquals(ArtworkSettingKind.TGDB_REMOVE, saved.rows[r.state.selection.value.index].kind)
        assertEquals("TheGamesDB", saved.rows.single { it.kind == ArtworkSettingKind.TGDB_LEFT }.label)
        assertEquals("1,500 left this month", saved.rows.single { it.kind == ArtworkSettingKind.TGDB_LEFT }.secondary)

        r.state.select(saved.rows.indexOfFirst { it.kind == ArtworkSettingKind.TGDB_REMOVE }); r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertNull(r.settings.tgdbApiKey)
        assertTrue("TheGamesDB key removed" in r.notices)
        val removed = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertEquals(ArtworkSettingKind.TGDB_KEY, removed.rows.dropLast(3).last().kind)   // above the gap, Clear unused images and Reset: no allowance row without a key
        assertEquals("Not set", removed.rows.single { it.kind == ArtworkSettingKind.TGDB_KEY }.secondary)
        assertEquals(ArtworkSettingKind.TGDB_KEY, removed.rows[r.state.selection.value.index].kind)
        r.state.onButton(NxButton.B)
    }

    @Test fun account_tgdb_new_user_key_replaces_the_old_keys_allowance() = runTest {
        val r = rig(tgdbUserKey = "OLD")
        r.http.on("API/Limit?apikey=OLD", HttpResult(200, """{"remaining_monthly_allowance":900,"extra_allowance":0}""".encodeToByteArray()))
        r.http.on("API/Limit?apikey=MINE", HttpResult(200, """{"remaining_monthly_allowance":2000,"extra_allowance":0}""".encodeToByteArray()))
        r.state.openArtwork()
        r.state.pick(ArtworkAction.SETTINGS)
        advanceUntilIdle()
        var rows = assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows
        assertEquals("900 left this month", rows.single { it.kind == ArtworkSettingKind.TGDB_LEFT }.secondary)
        // A set key shows only Remove; the new key goes in once the old one is removed.
        r.state.select(rows.indexOfFirst { it.kind == ArtworkSettingKind.TGDB_REMOVE }); r.state.onButton(NxButton.A)
        advanceUntilIdle()
        rows = assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows
        r.state.select(rows.indexOfFirst { it.kind == ArtworkSettingKind.TGDB_KEY }); r.state.onButton(NxButton.A)
        r.state.artworkDraftKey("MINE"); r.state.onButton(NxButton.A)
        advanceUntilIdle()
        rows = assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows
        assertEquals("2,000 left this month", rows.single { it.kind == ArtworkSettingKind.TGDB_LEFT }.secondary)
        r.state.onButton(NxButton.B)
    }

    @Test fun account_sign_in_stores_the_login_or_shows_the_error() = runTest {
        val r = rig()
        r.state.openArtwork()
        r.state.pick(ArtworkAction.SETTINGS)
        val account = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertEquals(listOf(ArtworkSettingKind.REPLACE, ArtworkSettingKind.BOX2D, ArtworkSettingKind.WHEEL, ArtworkSettingKind.MIX, ArtworkSettingKind.SS_LOGIN, ArtworkSettingKind.CLEAR_CACHE, ArtworkSettingKind.RESET), account.rows.filter { it.selectable }.map { it.kind })

        r.http.on("ssuserInfos", Scrape.json("""{"response":{}}"""))   // an answer without a user: a bad login
        r.state.select(7); r.state.onButton(NxButton.A)   // ScreenScraper login, under Replace existing art and the optional art
        assertTrue(r.state.textEntryOpen)
        r.state.artworkDraftUser("me"); r.state.artworkDraftPassword("secret")
        assertFalse("secret" in r.state.screen.value.toString())
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals("ScreenScraper sign-in failed", r.state.error.value)
        val failed = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertFalse(failed.signingIn); assertEquals("", failed.draftUser); assertEquals("", failed.draftPassword)
        assertEquals("", r.settings.ssUsername); assertNull(r.settings.ssPassword)
        r.state.dismissError()

        r.http.set("ssuserInfos", Scrape.quota(5, 100))
        r.state.onButton(NxButton.A)
        r.state.artworkDraftUser(" me "); r.state.artworkDraftPassword("secret")
        r.state.onButton(NxButton.A)
        advanceTimeBy(200)
        assertEquals("Signing in…", r.state.loadingMessage.value)
        advanceUntilIdle()
        assertNull(r.state.error.value)
        assertTrue("Signed in" in r.notices)
        assertEquals("me", r.settings.ssUsername); assertEquals("secret", r.settings.ssPassword)
        val signedIn = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        // Signed in, the one row is logout with today's requests; the name is not shown.
        assertEquals("ScreenScraper logout" to "5 / 100 today", signedIn.rows.single { it.kind == ArtworkSettingKind.SS_LOGOUT }.let { it.label to it.secondary })
        assertTrue(signedIn.rows.none { it.kind == ArtworkSettingKind.SS_LOGIN })
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.SETTINGS)

        r.state.select(signedIn.rows.indexOfFirst { it.kind == ArtworkSettingKind.SS_LOGOUT })
        r.state.onButton(NxButton.A)   // Logout asks first
        assertTrue(assertIs<Screen.ArtworkSettings>(r.state.screen.value).confirmLogout)
        r.state.onButton(NxButton.A)   // yes: the row turns back into login, the highlight stays on it
        assertEquals(ArtworkSettingKind.SS_LOGIN, assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows[7].kind)
        assertEquals(7, r.state.selection.value.index)
        assertEquals("", r.settings.ssUsername)

    }

    @Test fun logout_is_dimmed_while_art_downloads_and_asks_first() = runTest {
        val r = rig()
        r.settings.ssUsername = "me"; r.settings.ssPassword = "secret"
        r.http.latency = 5_000
        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_MISSING)
        assertEquals(JobPhase.RUNNING, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.phase)
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.SETTINGS)
        fun page() = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        fun logout() = page().rows.single { it.kind == ArtworkSettingKind.SS_LOGOUT }
        assertFalse(logout().enabled)
        r.state.select(page().rows.indexOf(logout())); r.state.onButton(NxButton.A)
        assertFalse(page().confirmLogout)   // dimmed: A does nothing

        advanceUntilIdle()   // the fetch ends: the row follows
        assertTrue(logout().enabled)
        r.state.onButton(NxButton.A)
        assertTrue(page().confirmLogout)
        r.state.onButton(NxButton.B)   // no: still signed in
        assertFalse(page().confirmLogout)
        assertEquals("me", r.settings.ssUsername)
        r.state.onButton(NxButton.A); r.state.onButton(NxButton.A)
        assertEquals("", r.settings.ssUsername)
        assertTrue("Signed out" in r.notices)
    }

    @Test fun clear_row_sits_above_reset_with_size() = runTest {
        val cache = com.nxredux.mobile.scraper.FakeOptionCache().apply { files["a"] = ByteArray(1_500_000) }
        val r = rig(optionCache = cache)
        r.state.openArtwork(); r.state.pick(ArtworkAction.SETTINGS)
        val rows = assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows
        val i = rows.indexOfFirst { it.kind == ArtworkSettingKind.CLEAR_CACHE }
        assertTrue(i >= 0)
        assertEquals("Clear unused images" to "1.4 MB", rows[i].let { it.label to it.secondary })
        assertEquals(ArtworkSettingKind.RESET, rows[i + 1].kind)
        assertEquals("0 B", formatBytes(0)); assertEquals("512 B", formatBytes(512)); assertEquals("2.0 KB", formatBytes(2048))
    }

    @Test fun clear_confirm_delete_calls_clear_and_shows_notice() = runTest {
        val cache = com.nxredux.mobile.scraper.FakeOptionCache().apply { files["a"] = ByteArray(2 * 1_048_576) }
        val r = rig(optionCache = cache)
        r.state.openArtwork(); r.state.pick(ArtworkAction.SETTINGS)
        fun page() = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        r.state.select(page().rows.indexOfFirst { it.kind == ArtworkSettingKind.CLEAR_CACHE })
        r.state.onButton(NxButton.A)
        assertTrue(page().confirmClearCache)
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertFalse(page().confirmClearCache)
        assertTrue(cache.files.isEmpty())
        assertTrue("Cleared 2.0 MB" in r.notices)
        assertEquals("0 B", page().rows.single { it.kind == ArtworkSettingKind.CLEAR_CACHE }.secondary)
    }

    @Test fun clear_confirm_cancel_keeps_cache() = runTest {
        val cache = com.nxredux.mobile.scraper.FakeOptionCache().apply { files["a"] = ByteArray(100) }
        val r = rig(optionCache = cache)
        r.state.openArtwork(); r.state.pick(ArtworkAction.SETTINGS)
        fun page() = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        r.state.select(page().rows.indexOfFirst { it.kind == ArtworkSettingKind.CLEAR_CACHE })
        r.state.onButton(NxButton.A)
        assertTrue(page().confirmClearCache)
        r.state.onButton(NxButton.B)
        advanceUntilIdle()
        assertFalse(page().confirmClearCache)
        assertEquals(1, cache.files.size)
        assertTrue(r.notices.none { it.startsWith("Cleared") })
    }

    @Test fun replace_toggles() = runTest {
        val r = rig()
        r.state.openArtwork()
        r.state.pick(ArtworkAction.SETTINGS)
        fun rows() = assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows
        assertEquals(listOf(ArtworkSettingKind.REPLACE, ArtworkSettingKind.BOX2D, ArtworkSettingKind.WHEEL, ArtworkSettingKind.MIX, ArtworkSettingKind.SS_LOGIN, ArtworkSettingKind.CLEAR_CACHE, ArtworkSettingKind.RESET), rows().filter { it.selectable }.map { it.kind })
        assertEquals(ArtworkSettingKind.REPLACE, rows()[r.state.selection.value.index].kind)   // opens past the group label
        assertEquals("Off", rows()[1].secondary)
        r.state.onButton(NxButton.A)
        assertTrue(r.settings.artReplaceExisting); assertEquals("On", rows()[1].secondary)
        r.state.settingEvent(NxSettingEvent.Choose(1, 0)); assertFalse(r.settings.artReplaceExisting)   // the switch's tap
        r.state.settingEvent(NxSettingEvent.Choose(1, 0)); assertFalse(r.settings.artReplaceExisting)   // already Off: stays
        r.state.onButton(NxButton.A)
        r.state.onButton(NxButton.B)
        assertEquals("Fetch all art", assertIs<Screen.Artwork>(r.state.screen.value).rows.first { it.action == ArtworkAction.FETCH_MISSING }.label)
    }

    @Test fun generate_mix_users_keep_their_wheel_once() = runTest {
        // Mix used to fetch wheels whatever Wheel said; now it does not, so the first load turns Wheel on for them.
        val r = rig(stored = { artMix = true; artWheel = false })
        assertTrue(r.settings.artWheel); assertTrue(r.settings.artWheelMigrated)
        // Done once: Wheel turned off stays off on the next launch.
        r.settings.artWheel = false
        r.state.load(); advanceUntilIdle()
        assertFalse(r.settings.artWheel)
    }

    @Test fun without_generate_mix_the_wheel_is_left_alone() = runTest {
        val r = rig(stored = { artMix = false; artWheel = false })
        assertFalse(r.settings.artWheel); assertTrue(r.settings.artWheelMigrated)
        // Turning Mix on afterwards does not bring the wheel back on a later launch.
        r.settings.artMix = true
        r.state.load(); advanceUntilIdle()
        assertFalse(r.settings.artWheel)
    }

    @Test fun extras_toggle_and_mix_leaves_the_wheel_free() = runTest {
        val r = rig()
        r.state.openArtwork()
        assertEquals(3, r.missing())
        r.state.pick(ArtworkAction.SETTINGS)
        fun rows() = assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows
        fun row(k: ArtworkSettingKind) = rows().first { it.kind == k }
        fun on(k: ArtworkSettingKind) { r.state.select(rows().indexOfFirst { it.kind == k }) }
        assertEquals(listOf("Art to fetch", "Replace existing art", "2D box art", "Wheel", "Mix"), rows().take(5).map { it.label })
        assertEquals(listOf("Off", "Off", "Off"), listOf(ArtworkSettingKind.BOX2D, ArtworkSettingKind.WHEEL, ArtworkSettingKind.MIX).map { row(it).secondary })
        on(ArtworkSettingKind.BOX2D); r.state.onButton(NxButton.RIGHT)
        assertTrue(r.settings.artBox2d); assertEquals("On", row(ArtworkSettingKind.BOX2D).secondary)
        on(ArtworkSettingKind.WHEEL); r.state.onButton(NxButton.A)
        assertTrue(r.settings.artWheel); assertEquals("On", row(ArtworkSettingKind.WHEEL).secondary)
        r.state.onButton(NxButton.LEFT)
        assertFalse(r.settings.artWheel)
        on(ArtworkSettingKind.MIX); r.state.onButton(NxButton.A)
        assertTrue(r.settings.artMix)
        // The mix does not force the wheel: its row stays free, Off, and A on it flips the setting.
        assertEquals(ArtworkSettingRow(ArtworkSettingKind.WHEEL, "Wheel", "Off"), row(ArtworkSettingKind.WHEEL))
        assertTrue(row(ArtworkSettingKind.WHEEL).enabled)
        assertEquals(NxSettingRow("Wheel", info = "", detail = "Also fetch the game's logo", choices = listOf("Off", "On"), index = 0), artworkSettingTouchRow(row(ArtworkSettingKind.WHEEL)))
        assertEquals(listOf("Off", "On"), artworkSettingTouchRow(row(ArtworkSettingKind.MIX)).choices)
        on(ArtworkSettingKind.WHEEL); r.state.onButton(NxButton.A)
        assertTrue(r.settings.artWheel)
        // With Mix on, a game has art only with its mix: Zelda (a box and a background, no mix) is missing again.
        r.state.onButton(NxButton.B)
        assertEquals(4, r.missing())
        r.state.pick(ArtworkAction.SETTINGS)
        on(ArtworkSettingKind.MIX); r.state.onButton(NxButton.A)
        assertFalse(r.settings.artMix); assertEquals(ArtworkSettingRow(ArtworkSettingKind.WHEEL, "Wheel", "On"), row(ArtworkSettingKind.WHEEL))
    }

    /** Every write fails, as on a full disk. */
    private class FullDisk : PrivateFiles {
        override suspend fun read(name: String): String? = null
        override suspend fun write(name: String, text: String) = throw IllegalStateException("disk full")
    }

    @Test fun a_failing_store_shows_an_error_instead_of_crashing() = runTest {
        val r = rig(files = FullDisk())
        r.http.on("romnom=Tetris.gb", Scrape.found("ss" to "https://m/t-ss.png"))
        r.http.on("https://m/", Scrape.OK_PNG)
        r.state.openArtwork()
        r.state.pick(ArtworkAction.SETTINGS)
        r.state.pickRow("Reset artwork", assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows.map { it.label })
        r.state.onButton(NxButton.A); r.state.onButton(NxButton.A)   // All, then Delete
        advanceUntilIdle()
        assertEquals(ARTWORK_STORAGE_ERROR, r.state.error.value)
        assertFalse(r.state.loading.value)
        assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        r.state.dismissError()

        val gb = r.state.run { openTools(); assertIs<Screen.Systems>(screen.value) }.tabs.flatMap { it.rows }
            .firstNotNullOf { (it as? MainRow.System)?.systems?.firstOrNull { s -> s.tag == "GB" } }
        r.state.openSystem(gb)
        r.state.onButton(NxButton.MENU)
        val kinds = assertNotNull(assertIs<Screen.Games>(r.state.screen.value).menu).items.map { it.kind }
        repeat(kinds.indexOf(ContextKind.FETCH_ART)) { r.state.onButton(NxButton.DOWN) }
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertNull(r.state.error.value)                                                    // the art panel says so itself
        assertEquals("Couldn't read the pictures", assertNotNull(r.state.artPanel.value).message)
        assertEquals(ArtPanelPhase.READY, r.state.artPanel.value!!.phase)
        assertFalse(r.state.loading.value)
    }

    @Test fun reset_asks_first_then_reports_the_count() = runTest {
        val r = rig()
        r.http.on("romnom=Tetris.gb", Scrape.found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png"))
        r.http.on("https://m/", Scrape.OK_PNG)
        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_MISSING)
        advanceUntilIdle()
        r.state.onButton(NxButton.B)
        assertEquals(2, r.missing())

        r.state.pick(ArtworkAction.SETTINGS)
        r.state.pickRow("Reset artwork", assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows.map { it.label })
        val reset = assertIs<Screen.ArtworkReset>(r.state.screen.value)
        assertEquals(listOf("All", "Game Boy", "Game Boy Advance", "Sega Genesis"), reset.scopes.map { it.label })
        r.state.pickRow("All", reset.scopes.map { it.label })
        assertEquals("All", assertIs<Screen.ArtworkReset>(r.state.screen.value).confirm?.label)
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertTrue("Deleted 2 files" in r.notices, "${r.notices}")
        assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        r.state.onButton(NxButton.B)
        assertEquals(3, r.missing())   // Zelda's own art stays
    }

    // The wide stage: the word lit on the left is the open page's (Reset is under Settings); on the root, the selection.
    @Test fun the_stage_word_is_lit_for_the_open_page() = runTest {
        val r = rig()
        r.http.latency = 5_000
        r.state.openArtwork()
        assertNull(r.state.artworkWord(r.state.screen.value))                              // the root: the highlight is the word
        r.state.pick(ArtworkAction.FETCH_BY_CONSOLE)
        assertEquals(ArtworkAction.FETCH_BY_CONSOLE, r.state.artworkWord(r.state.screen.value))
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.SETTINGS)
        assertEquals(ArtworkAction.SETTINGS, r.state.artworkWord(r.state.screen.value))
        // The words beside Settings are the page as it stands (its rows), not a stale copy.
        assertEquals(listOf("Fetch missing art", "Fetch by console", "Settings"), r.state.artworkStagePage(r.state.screen.value).rows.map { it.label })
        r.state.pickRow("Reset artwork", assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows.map { it.label })
        assertEquals(ArtworkAction.SETTINGS, r.state.artworkWord(assertIs<Screen.ArtworkReset>(r.state.screen.value)))
        r.state.onButton(NxButton.B); r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.FETCH_MISSING)
        assertEquals(ArtworkAction.PROGRESS, r.state.artworkWord(assertIs<Screen.ArtworkProgress>(r.state.screen.value)))
        assertTrue(r.state.artworkStagePage(r.state.screen.value).rows.any { it.action == ArtworkAction.PROGRESS })
    }

    // On the root the right frame previews the highlighted word's page; Fetch missing art and Resume have none.
    @Test fun the_root_previews_the_highlighted_word() = runTest {
        val r = rig()
        r.http.latency = 5_000
        r.state.openArtwork()
        val consoles = assertIs<Screen.ArtworkConsoles>(r.state.artworkPreview(ArtworkAction.FETCH_BY_CONSOLE))
        val settings = assertIs<Screen.ArtworkSettings>(r.state.artworkPreview(ArtworkAction.SETTINGS))
        assertNull(r.state.artworkPreview(ArtworkAction.FETCH_MISSING))
        assertNull(r.state.artworkPreview(ArtworkAction.RESUME))
        r.state.pick(ArtworkAction.FETCH_BY_CONSOLE)
        assertEquals(assertIs<Screen.ArtworkConsoles>(r.state.screen.value).rows, consoles.rows)
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.SETTINGS)
        assertEquals(assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows, settings.rows)
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.FETCH_MISSING)
        val job = assertIs<Screen.ArtworkProgress>(r.state.screen.value).job
        r.state.onButton(NxButton.B)
        assertEquals(job, assertIs<Screen.ArtworkProgress>(r.state.artworkPreview(ArtworkAction.PROGRESS)).job)
    }

    // A tap on the preview: the first opens that page on the row (as Settings' preview), the second acts on it.
    @Test fun a_preview_tap_opens_that_page_on_the_row_and_a_second_tap_acts() = runTest {
        val r = rig()
        r.state.openArtwork()
        fun pageAt(action: ArtworkAction) = assertIs<Screen.Artwork>(r.state.screen.value).rows.indexOfFirst { it.action == action }
        r.state.select(pageAt(ArtworkAction.SETTINGS))
        val preview = assertIs<Screen.ArtworkSettings>(r.state.artworkPreview(ArtworkAction.SETTINGS))
        val replace = preview.rows.indexOfFirst { it.kind == ArtworkSettingKind.REPLACE }
        r.state.artworkPreviewTap(replace)
        val settings = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertEquals(ArtworkSettingKind.REPLACE, settings.rows[r.state.selection.value.index].kind)
        assertFalse(r.settings.artReplaceExisting)                                          // opened, not acted on
        r.state.artworkPreviewTap(replace)
        assertTrue(r.settings.artReplaceExisting)                                           // the second tap: A there
        r.state.onButton(NxButton.B)                                                        // B: the page, on Settings
        assertEquals(ArtworkAction.SETTINGS, assertIs<Screen.Artwork>(r.state.screen.value).rows[r.state.selection.value.index].action)
        r.state.select(pageAt(ArtworkAction.FETCH_BY_CONSOLE))
        r.state.artworkPreviewTap(1)
        assertIs<Screen.ArtworkConsoles>(r.state.screen.value)
        assertEquals(1, r.state.selection.value.index)
    }

    @Test fun a_word_tap_from_a_page_opens_that_word_in_place() = runTest {
        val r = rig()
        r.state.openArtwork()
        r.state.pick(ArtworkAction.SETTINGS)
        /** A tap on the stage's word [action], as drawn beside the page open now. */
        fun tapWord(action: ArtworkAction) {
            val at = r.state.artworkStagePage(r.state.screen.value).rows.indexOfFirst { it.action == action }
            assertTrue(at >= 0, "no $action")
            r.state.artworkWordTap(at)
        }
        tapWord(ArtworkAction.FETCH_BY_CONSOLE)                                             // Fetch by console in Settings' place
        assertIs<Screen.ArtworkConsoles>(r.state.screen.value)
        r.state.select(1)
        tapWord(ArtworkAction.FETCH_BY_CONSOLE)                                             // the lit word: already open, nothing
        assertIs<Screen.ArtworkConsoles>(r.state.screen.value); assertEquals(1, r.state.selection.value.index)
        tapWord(ArtworkAction.SETTINGS)
        val settings = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        r.state.onButton(NxButton.B)                                                        // B from there: the page, on Settings
        assertEquals(ArtworkAction.SETTINGS, assertIs<Screen.Artwork>(r.state.screen.value).rows[r.state.selection.value.index].action)
        r.state.pick(ArtworkAction.SETTINGS)
        r.state.pickRow("Reset artwork", settings.rows.map { it.label })
        tapWord(ArtworkAction.SETTINGS)                                                     // from Reset: back to the Settings rows, on Reset
        assertEquals(ArtworkSettingKind.RESET, assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows[r.state.selection.value.index].kind)
        // An open dialog takes the taps: the words do nothing under it.
        r.state.onButton(NxButton.A)
        r.state.onButton(NxButton.A)
        assertNotNull(assertIs<Screen.ArtworkReset>(r.state.screen.value).confirm)
        tapWord(ArtworkAction.FETCH_BY_CONSOLE)
        assertNotNull(assertIs<Screen.ArtworkReset>(r.state.screen.value).confirm)
        r.state.onButton(NxButton.B); r.state.onButton(NxButton.B)                          // Settings
        // A word that starts a job: one tap, one job, Progress on the right.
        r.http.latency = 5_000
        tapWord(ArtworkAction.FETCH_MISSING)
        val progress = assertIs<Screen.ArtworkProgress>(r.state.screen.value)
        assertEquals(JobPhase.RUNNING, progress.job.phase)
        assertEquals(3, progress.job.total)
        r.state.onButton(NxButton.X)                                                        // the cancel dialog blocks the words
        tapWord(ArtworkAction.SETTINGS)
        assertTrue(assertIs<Screen.ArtworkProgress>(r.state.screen.value).confirmCancel)
        r.state.onButton(NxButton.B)
        // Resume gone since the words were drawn: its tap does nothing.
        r.state.onButton(NxButton.Y); runCurrent()                                         // paused: Resume among the words
        val drawn = r.state.artworkStagePage(r.state.screen.value).rows
        val resume = drawn.indexOfFirst { it.action == ArtworkAction.RESUME }
        assertTrue(resume >= 0, "$drawn")
        r.state.onButton(NxButton.Y); runCurrent()                                         // running again: Resume is gone
        r.state.select(1)
        r.state.artworkWordTap(resume)                                                     // not the word now in its place either
        assertEquals(JobPhase.RUNNING, assertIs<Screen.ArtworkProgress>(r.state.screen.value).job.phase)
        assertEquals(1, r.state.selection.value.index)                                     // Progress not opened again
    }

    @Test fun the_context_menu_fetch_art_opens_the_panel_fetches_then_lists_the_options() = runTest {
        val r = rig()
        r.http.latency = 500
        r.http.on("romnom=Tetris.gb", Scrape.found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png"))
        r.http.on("https://m/", Scrape.OK_PNG)
        val gb = assertIs<Screen.Systems>(r.state.screen.value).tabs.flatMap { it.rows }.firstNotNullOf { (it as? MainRow.System)?.systems?.firstOrNull { s -> s.tag == "GB" } }
        r.state.openSystem(gb)
        r.state.onButton(NxButton.MENU)
        val kinds = assertNotNull(assertIs<Screen.Games>(r.state.screen.value).menu).items.map { it.kind }
        assertTrue(ContextKind.FETCH_ART in kinds)
        repeat(kinds.indexOf(ContextKind.FETCH_ART)) { r.state.onButton(NxButton.DOWN) }
        r.state.onButton(NxButton.A)
        advanceTimeBy(300)
        assertFalse(r.state.loading.value)                                                 // no loading overlay: the panel
        assertNull(assertIs<Screen.Games>(r.state.screen.value).menu)
        assertEquals(ArtPanelPhase.FETCHING, assertNotNull(r.state.artPanel.value).phase)
        assertEquals("Tetris", r.state.artPanel.value!!.title)
        advanceUntilIdle()
        val p = assertNotNull(r.state.artPanel.value)
        assertEquals(ArtPanelPhase.READY, p.phase)
        assertEquals(listOf("ScreenScraper · us", "ScreenScraper · us"), p.tiles.map { it.label })   // the screenshot, then the 2D box
        assertEquals(listOf(ArtSlot.SCREENSHOT, ArtSlot.BOX_2D), p.sections.map { it.slot })
        assertEquals(p.tiles[0].option.key, p.currentKeys[ArtSlot.SCREENSHOT])                                   // the fetched screenshot is the one in use
        val games = assertIs<Screen.Games>(r.state.screen.value)
        assertEquals(ContentRef("Home/Roms/Game Boy (GB)/.media/screenshot/Tetris.png"), games.entries.first { it.game.fileName == "Tetris.gb" }.game.screenshotRef)
        r.state.onButton(NxButton.B)                                                       // B closes the panel, the list stays
        assertNull(r.state.artPanel.value)
        assertIs<Screen.Games>(r.state.screen.value)
        r.state.onButton(NxButton.MENU)                                                    // the game has art now: Change art
        val after = assertNotNull(assertIs<Screen.Games>(r.state.screen.value).menu).items.map { it.kind }
        assertTrue(ContextKind.CHANGE_ART in after && ContextKind.FETCH_ART !in after, "$after")
        repeat(after.indexOf(ContextKind.CHANGE_ART)) { r.state.onButton(NxButton.DOWN) }
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals(ArtPanelPhase.READY, assertNotNull(r.state.artPanel.value).phase)   // the saved list: no search
        assertEquals(2, r.state.artPanel.value!!.tiles.size)
        assertNull(r.state.artPanel.value!!.message)
        r.state.back()                                                                      // the system back closes it too
        assertNull(r.state.artPanel.value)
        assertIs<Screen.Games>(r.state.screen.value)
        r.state.onButton(NxButton.MENU)
        repeat(after.indexOf(ContextKind.CHANGE_ART)) { r.state.onButton(NxButton.DOWN) }
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        r.state.back(); r.state.back()                                                      // the panel, then the list: it closes off the list
        r.state.openSystem(gb)
        assertNull(r.state.artPanel.value)
    }

    @Test fun the_panels_switch_shows_the_new_backdrop_art_on_the_open_list() = runTest {
        val r = rig()
        r.http.on("romnom=Tetris.gb", Scrape.found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png"))
        r.http.on("https://m/", Scrape.OK_PNG)
        val gb = assertIs<Screen.Systems>(r.state.screen.value).tabs.flatMap { it.rows }.firstNotNullOf { (it as? MainRow.System)?.systems?.firstOrNull { s -> s.tag == "GB" } }
        r.state.openSystem(gb)
        r.state.onButton(NxButton.MENU)
        val kinds = assertNotNull(assertIs<Screen.Games>(r.state.screen.value).menu).items.map { it.kind }
        repeat(kinds.indexOf(ContextKind.FETCH_ART)) { r.state.onButton(NxButton.DOWN) }
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals(ArtLayout(BackdropArt.BOX3D, GameListArt.SCREENSHOT), r.state.artLayout.value)
        val box = r.state.artPanel.value!!.tiles.indexOfFirst { it.option.slot == ArtSlot.BOX_2D }
        r.state.artPanelTap(box)
        advanceUntilIdle()
        assertEquals(LayoutQuestion(BackdropArt.BOX2D, GameListArt.BOX2D, alsoListArt = false, yesFocused = true), r.state.artPanel.value!!.question)
        r.state.artPanelAnswer(switch = true)
        assertEquals(BackdropArt.BOX2D, r.state.settings.backdropArt)
        assertEquals(ArtLayout(BackdropArt.BOX2D, GameListArt.SCREENSHOT), r.state.artLayout.value)   // the open list redraws with it
        assertIs<Screen.Games>(r.state.screen.value)
        assertNotNull(r.state.artPanel.value)   // the panel stays open, the question gone
        assertNull(r.state.artPanel.value!!.question)
    }

    /** Opens GB, puts the row on [file] and MENU; the art item the menu offers (none, Fetch art or Change art). */
    private fun Rig.artItem(file: String): ContextKind? {
        val gb = assertIs<Screen.Systems>(state.screen.value).tabs.flatMap { it.rows }.firstNotNullOf { (it as? MainRow.System)?.systems?.firstOrNull { s -> s.tag == "GB" } }
        state.openSystem(gb)
        state.select(assertIs<Screen.Games>(state.screen.value).entries.indexOfFirst { it.game.fileName == file })
        state.onButton(NxButton.MENU)
        val kinds = assertNotNull(assertIs<Screen.Games>(state.screen.value).menu).items.map { it.kind }
        return kinds.firstOrNull { it == ContextKind.FETCH_ART || it == ContextKind.CHANGE_ART }
    }

    @Test fun fetch_art_for_a_game_without_art_change_art_once_it_has_any() = runTest {
        assertEquals(ContextKind.FETCH_ART, rig().artItem("Tetris.gb"))     // no art at all (its .media root png is not art)
        assertEquals(ContextKind.CHANGE_ART, rig().artItem("Zelda.gb"))     // a box and a background
    }

    @Test fun replace_existing_and_generate_mix_no_longer_bring_fetch_art_back() = runTest {
        val replace = rig(); replace.settings.artReplaceExisting = true
        assertEquals(ContextKind.CHANGE_ART, replace.artItem("Zelda.gb"))
        val mix = rig(); mix.settings.artMix = true
        assertEquals(ContextKind.CHANGE_ART, mix.artItem("Zelda.gb"))      // no mix, yet it has art
    }

    @Test fun no_fetch_art_without_credentials_or_for_excluded_tags() = runTest {
        val r = rig(available = false)
        val gb = assertIs<Screen.Systems>(r.state.screen.value).tabs.flatMap { it.rows }.firstNotNullOf { (it as? MainRow.System)?.systems?.firstOrNull { s -> s.tag == "GB" } }
        r.state.openSystem(gb)
        r.state.onButton(NxButton.MENU)
        assertTrue(ContextKind.FETCH_ART !in assertNotNull(assertIs<Screen.Games>(r.state.screen.value).menu).items.map { it.kind })
    }

    @Test fun android_is_an_artwork_console_and_its_fetch_fills_the_console() = runTest {
        val store = FakeArtStore()
        val pgo = InstalledApp("com.nianticlabs.pokemongo/com.unity.Main", "Pokémon GO", game = true)
        val r = rig(apps = listOf(pgo, InstalledApp("com.other/.Main", "Notes")), artStore = store, consoleApps = listOf(pgo.id))
        r.http.on("jeuInfos", Scrape.found("ss" to "https://m/pgo-ss.png", "wheel" to "https://m/pgo-wheel.png"))
        r.http.on("https://m/", Scrape.OK_PNG)
        assertNull(assertNotNull(r.state.androidConsole).games.single().screenshotRef)

        r.state.openArtwork()
        assertEquals(emptyList(), assertIs<Screen.Artwork>(r.state.screen.value).details)
        r.state.pick(ArtworkAction.FETCH_BY_CONSOLE)
        val consoles = assertIs<Screen.ArtworkConsoles>(r.state.screen.value).rows
        assertEquals(listOf("Android" to 1, "Game Boy" to 1, "Game Boy Advance" to 1, "Sega Genesis" to 1), consoles.map { it.name to it.missing })
        r.state.pickRow("Android", consoles.map { it.name })
        advanceUntilIdle()
        val job = assertIs<Screen.ArtworkProgress>(r.state.screen.value).job
        assertEquals(JobPhase.DONE, job.phase)
        assertEquals(listOf("Pokémon GO" to ItemStatus.DONE), job.items.map { it.name to it.status })
        assertEquals(listOf(pgo.id), store.files.keys.toList())
        assertTrue(r.http.calls.none { it.contains("wheel") })

        val shot = ContentRef("file:///art/com.nianticlabs.pokemongo.png")
        assertEquals(shot, assertNotNull(r.state.androidConsole).games.single().screenshotRef)
        r.state.onButton(NxButton.B); r.state.onButton(NxButton.B)   // Progress -> Artwork -> Tools
        val android = assertIs<Screen.Systems>(r.state.screen.value).tabs.flatMap { it.rows }.filterIsInstance<MainRow.System>().map { it.system }.single { it.android }
        assertEquals(shot, android.games.single().screenshotRef)

        r.state.openArtwork()
        assertEquals(3, r.missing())
        r.state.pick(ArtworkAction.SETTINGS)
        r.state.pickRow("Reset artwork", assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows.map { it.label })
        // Reset lists Android like a ROM console, where Fetch lists it; resetting it deletes its stored screenshots.
        val scopes = assertIs<Screen.ArtworkReset>(r.state.screen.value).scopes
        assertEquals(listOf("All", "Android", "Game Boy", "Game Boy Advance", "Sega Genesis"), scopes.map { it.label })
        r.state.pickRow("Android", scopes.map { it.label })
        assertEquals("Android", assertIs<Screen.ArtworkReset>(r.state.screen.value).confirm?.label)
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertTrue("Deleted 1 file" in r.notices, "${r.notices}")
        assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        assertTrue(store.files.isEmpty())
        assertNull(assertNotNull(r.state.androidConsole).games.single().screenshotRef)   // the tile falls back to the app icon
        r.state.onButton(NxButton.B)
        assertEquals(4, r.missing())   // Zelda's own art stays
    }

    @Test fun an_android_game_gets_fetch_art_and_change_art_with_every_section() = runTest {
        val store = FakeArtStore()
        val tetris = InstalledApp("com.tetris.app/.Main", "Tetris", game = true)
        val r = rig(apps = listOf(tetris), artStore = store, consoleApps = listOf(tetris.id), sgdb = true)
        r.settings.sgdbApiKey = "K"
        r.http.on("jeuInfos", Scrape.found("ss" to "https://m/t-ss.png", "wheel" to "https://m/t-wheel.png"))
        r.http.on("https://m/", Scrape.OK_PNG)
        r.http.on("search/autocomplete/", Scrape.json(SgdbFixtures.autocomplete))
        r.http.on("grids/game/", Scrape.json(SgdbFixtures.grids))
        r.http.on("cdn2.steamgriddb.com", Scrape.OK_PNG)
        fun console() = assertIs<Screen.Systems>(r.state.screen.value).tabs.flatMap { it.rows }.filterIsInstance<MainRow.System>().map { it.system }.single { it.android }
        r.state.openSystem(console())
        r.state.onButton(NxButton.MENU)
        val kinds = assertNotNull(assertIs<Screen.Games>(r.state.screen.value).menu).items.map { it.kind }
        assertEquals(listOf(ContextKind.PIN, ContextKind.FETCH_ART), kinds)
        repeat(kinds.indexOf(ContextKind.FETCH_ART)) { r.state.onButton(NxButton.DOWN) }
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        val p = assertNotNull(r.state.artPanel.value)
        assertEquals(ArtPanelPhase.READY, p.phase)
        assertEquals(listOf(tetris.id), store.files.keys.toList())                          // the fetch wrote the screenshot
        val slots = p.tiles.map { it.option.slot }.toSet()
        assertTrue(setOf(ArtSlot.SCREENSHOT, ArtSlot.GRID).all { it in slots }, "$slots")   // the kinds the panel lists today
        assertTrue(ArtSlot.BOX_3D !in slots)
        val grid = p.tiles.indexOfFirst { it.option.slot == ArtSlot.GRID }
        r.state.artPanelTap(grid)
        advanceUntilIdle()
        assertNotNull(store.art(tetris.id, ArtSlot.BOX_2D))
        assertEquals(ContentRef("file:///art/com.tetris.app.box2d.png"), assertNotNull(r.state.androidConsole).games.single().box2dRef)
    }

    @Test fun android_mix_shows_as_the_consoles_mix_and_counts_as_missing_until_built() = runTest {
        val store = FakeArtStore()
        val pgo = InstalledApp("com.nianticlabs.pokemongo/com.unity.Main", "Pokémon GO", game = true)
        store.files[pgo.id] = byteArrayOf(7)
        val r = rig(apps = listOf(pgo), artStore = store, consoleApps = listOf(pgo.id), mixEngine = true)
        r.settings.artMix = true
        assertNull(assertNotNull(r.state.androidConsole).games.single().mixRef)

        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_BY_CONSOLE)
        val consoles = assertIs<Screen.ArtworkConsoles>(r.state.screen.value).rows
        assertEquals(1, consoles.single { it.name == "Android" }.missing)                      // a screenshot, no mix: missing
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.FETCH_MISSING)
        advanceUntilIdle()
        val job = assertIs<Screen.ArtworkProgress>(r.state.screen.value).job
        assertEquals(ItemStatus.DONE, job.items.single { it.name == pgo.label }.status)
        assertTrue(r.http.calls.none { it.contains("systemeid=63") }, "${r.http.calls}")        // built locally: no lookup
        assertEquals(ContentRef("file:///art/com.nianticlabs.pokemongo.mix.png"), assertNotNull(r.state.androidConsole).games.single().mixRef)
    }

    @Test fun reset_all_deletes_the_android_screenshots_too() = runTest {
        val store = FakeArtStore()
        val pgo = InstalledApp("com.nianticlabs.pokemongo/com.unity.Main", "Pokémon GO", game = true)
        val r = rig(apps = listOf(pgo), artStore = store, consoleApps = listOf(pgo.id))
        r.http.on("jeuInfos", Scrape.found("ss" to "https://m/pgo-ss.png"))   // every missing game matches
        r.http.on("https://m/", Scrape.OK_PNG)
        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_MISSING)
        advanceUntilIdle()
        assertEquals(listOf(pgo.id), store.files.keys.toList())
        r.state.onButton(NxButton.B)

        r.state.pick(ArtworkAction.SETTINGS)
        r.state.pickRow("Reset artwork", assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows.map { it.label })
        r.state.pickRow("All", assertIs<Screen.ArtworkReset>(r.state.screen.value).scopes.map { it.label })
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertTrue(store.files.isEmpty())
        assertNull(assertNotNull(r.state.androidConsole).games.single().screenshotRef)
        assertTrue("Deleted 4 files" in r.notices, "${r.notices}")   // three ROM games' screenshot (no mix), and the Android screenshot
    }

    @Test fun no_android_artwork_console_when_the_console_has_no_games() = runTest {
        val r = rig(apps = listOf(InstalledApp("g/G", "Game", game = true)), artStore = FakeArtStore())   // detected, never added
        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_BY_CONSOLE)
        assertEquals(listOf("Game Boy", "Game Boy Advance", "Sega Genesis"), assertIs<Screen.ArtworkConsoles>(r.state.screen.value).rows.map { it.name })
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.SETTINGS)
        r.state.pickRow("Reset artwork", assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows.map { it.label })
        assertEquals(listOf("All", "Game Boy", "Game Boy Advance", "Sega Genesis"), assertIs<Screen.ArtworkReset>(r.state.screen.value).scopes.map { it.label })
    }

    @Test fun detailsLeaveAccountStatusToAccount() {
        // Sign-in, quota and keys are on the Account page; no counts, no source credit: an idle page has no lines.
        val state = com.nxredux.mobile.scraper.ScrapeState(quota = com.nxredux.mobile.scraper.SsQuota(1234, 20000), tgdbLeft = 1820)
        assertEquals(emptyList(), artworkDetails(true, state))
        assertEquals(listOf("Artwork fetching isn't available in this build."), artworkDetails(false, state))
    }

    @Test fun stoppedSourceNoteShown() {
        val state = com.nxredux.mobile.scraper.ScrapeState(
            JobPhase.RUNNING, List(4) { com.nxredux.mobile.scraper.ScrapeItem("/g$it", "GB", "g$it") },
            notes = mapOf("thegamesdb" to "TheGamesDB: monthly limit reached"),
        )
        assertEquals(listOf("Fetching 0 / 4 …", "TheGamesDB: monthly limit reached"), artworkDetails(true, state))
    }

    @Test fun noScreenScraperBuildStillFetches() = runTest {
        val r = rig(available = false, libretro = true)
        val png = HttpResult(200, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1))   // libretro keeps only an image answer
        r.http.on("Named_Boxarts", png)
        r.http.on("Named_Snaps", png)
        r.state.openArtwork()
        val page = assertIs<Screen.Artwork>(r.state.screen.value)
        assertEquals(
            listOf(ArtworkAction.FETCH_MISSING, ArtworkAction.FETCH_BY_CONSOLE, ArtworkAction.SETTINGS),
            page.rows.map { it.action },
        )
        r.state.pick(ArtworkAction.SETTINGS)
        assertTrue(assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows.none { it.kind == ArtworkSettingKind.SS_LOGIN || it.kind == ArtworkSettingKind.SS_LOGOUT })
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.FETCH_BY_CONSOLE)
        r.state.pickRow("Game Boy", assertIs<Screen.ArtworkConsoles>(r.state.screen.value).rows.map { it.name })
        advanceUntilIdle()
        val job = assertIs<Screen.ArtworkProgress>(r.state.screen.value).job
        assertEquals(JobPhase.DONE, job.phase)
        assertEquals(listOf("Done · libretro"), job.items.map { itemStatusText(it.status, it.reason) })
    }

    @Test fun tgdbRowOnlyWithUserKey() = runTest {
        val r = rig(tgdbUserKey = "K")
        r.http.on("API/Limit", HttpResult(200, """{"remaining_monthly_allowance":1820,"extra_allowance":0}""".encodeToByteArray()))
        r.state.openArtwork()
        advanceUntilIdle()
        r.state.pick(ArtworkAction.SETTINGS)
        advanceUntilIdle()
        val row = assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows.single { it.kind == ArtworkSettingKind.TGDB_LEFT }
        assertEquals("TheGamesDB" to "1,820 left this month", row.label to row.secondary)
        r.state.onButton(NxButton.A)   // read-only
        assertIs<Screen.ArtworkSettings>(r.state.screen.value)

        val without = rig()
        without.state.openArtwork()
        without.state.pick(ArtworkAction.SETTINGS)
        assertTrue(assertIs<Screen.ArtworkSettings>(without.state.screen.value).rows.none { it.kind == ArtworkSettingKind.TGDB_LEFT })
        assertEquals(0, without.http.count("API/Limit"))
    }

    @Test fun progressReasonNamesSources() {
        assertEquals("Done · ScreenScraper, libretro", itemStatusText(ItemStatus.DONE, "ScreenScraper, libretro"))
        assertEquals("Done · SteamGridDB · no screenshot", itemStatusText(ItemStatus.DONE, "SteamGridDB · no screenshot"))
        assertEquals("Done", itemStatusText(ItemStatus.DONE, null))
        assertEquals("Partial · no screenshot", itemStatusText(ItemStatus.PARTIAL, "no screenshot"))
        assertEquals("Not found", itemStatusText(ItemStatus.NOT_FOUND, "No artwork"))
        assertEquals("ScreenScraper, libretro · no screenshot", com.nxredux.mobile.scraper.ScrapeEngine.doneReason(listOf("ScreenScraper", "libretro"), standInOnly = true))
        assertNull(com.nxredux.mobile.scraper.ScrapeEngine.doneReason(emptyList(), standInOnly = false))
    }

    @Test fun progress_and_notice_texts() {
        assertEquals("Error: timeout", itemStatusText(com.nxredux.mobile.scraper.ItemStatus.ERROR, "timeout"))
        assertEquals("No art found", fetchResultText(com.nxredux.mobile.scraper.FetchResult(com.nxredux.mobile.scraper.ItemStatus.NOT_FOUND)))
        assertEquals("Daily limit", fetchResultText(com.nxredux.mobile.scraper.FetchResult(com.nxredux.mobile.scraper.ItemStatus.ERROR, "Daily limit")))
        val running = com.nxredux.mobile.scraper.ScrapeState(
            JobPhase.RUNNING, List(80) { com.nxredux.mobile.scraper.ScrapeItem("/g$it", "GB", "g$it") },
        )
        assertEquals("0 / 80 · ~2 min left", progressHeader(running))
        assertEquals("1,234 / 20,000", quotaText(com.nxredux.mobile.scraper.SsQuota(1234, 20000)))
        assertEquals("0", groupThousands(0)); assertEquals("999", groupThousands(999)); assertEquals("1,000,000", groupThousands(1_000_000))
    }

    @Test fun the_settings_highlight_stays_on_its_row_when_the_rows_change() = runTest {
        val r = rig(tgdbUserKey = "OLD", sgdb = true)
        r.settings.sgdbApiKey = "SG"
        r.http.on("API/Limit?apikey=OLD", HttpResult(200, """{"remaining_monthly_allowance":900,"extra_allowance":0}""".encodeToByteArray()))
        r.state.openArtwork()
        r.state.pick(ArtworkAction.SETTINGS)
        advanceUntilIdle()
        fun rows() = assertIs<Screen.ArtworkSettings>(r.state.screen.value).rows
        fun lit() = rows()[r.state.selection.value.index].kind
        // Removing TheGamesDB's key drops its Remove and allowance rows: the highlight lands on its key row, not Reset.
        r.state.select(rows().indexOfFirst { it.kind == ArtworkSettingKind.TGDB_REMOVE }); r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertTrue(rows().none { it.kind == ArtworkSettingKind.TGDB_REMOVE })
        assertEquals(ArtworkSettingKind.TGDB_KEY, lit())
        // The same for SteamGridDB's: on its key row.
        r.state.select(rows().indexOfFirst { it.kind == ArtworkSettingKind.SGDB_REMOVE }); r.state.onButton(NxButton.A)
        assertEquals(ArtworkSettingKind.SGDB_KEY, lit())
        // A row that stays keeps the highlight through a rebuild.
        r.state.select(rows().indexOfFirst { it.kind == ArtworkSettingKind.REPLACE }); r.state.onButton(NxButton.A)
        assertEquals(ArtworkSettingKind.REPLACE, lit())
    }

    @Test fun the_cancel_dialog_closes_when_the_fetch_ends() = runTest {
        val r = rig()
        r.http.latency = 5_000
        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_MISSING)
        r.state.onButton(NxButton.X)
        assertTrue(assertIs<Screen.ArtworkProgress>(r.state.screen.value).confirmCancel)
        advanceUntilIdle()   // the job ends under the dialog
        val done = assertIs<Screen.ArtworkProgress>(r.state.screen.value)
        assertEquals(JobPhase.DONE, done.job.phase)
        assertFalse(done.confirmCancel)
    }

    @Test fun login_is_dimmed_while_art_downloads_and_an_offline_login_is_not_kept() = runTest {
        val r = rig()
        r.http.latency = 5_000
        r.state.openArtwork()
        r.state.pick(ArtworkAction.FETCH_MISSING)
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.SETTINGS)
        fun page() = assertIs<Screen.ArtworkSettings>(r.state.screen.value)
        fun login() = page().rows.single { it.kind == ArtworkSettingKind.SS_LOGIN }
        assertFalse(login().enabled)
        r.state.select(page().rows.indexOf(login())); r.state.onButton(NxButton.A)
        assertFalse(page().signingIn)   // dimmed: A does nothing
        advanceUntilIdle()
        assertTrue(login().enabled)

        r.http.latency = 0
        r.http.on("ssuserInfos", HttpResult(-1, null))   // no connection
        r.state.onButton(NxButton.A)
        r.state.artworkDraftUser("me"); r.state.artworkDraftPassword("secret")
        r.state.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals("No connection", r.state.error.value)
        assertEquals("", r.settings.ssUsername); assertNull(r.settings.ssPassword)
        assertTrue(page().rows.any { it.kind == ArtworkSettingKind.SS_LOGIN })
    }

    @Test fun progress_opens_on_the_new_fetch_and_the_stage_lights_progress() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val settings = Settings()
        val http = FakeHttp()
        val source = FakeRomsSource(dirs = emptyMap())
        val host = OneGameHost()
        val artHost = object : com.nxredux.mobile.scraper.ArtworkHost {
            override suspend fun libraryGames(): List<com.nxredux.mobile.scraper.LibraryGameRef> { delay(100); return host.games }   // a slow library read
            override suspend fun allLibraryGames() = libraryGames()
            override suspend fun applyArt(gamePath: String, art: com.nxredux.mobile.library.ArtRefs) {}
            override suspend fun readPrivate(name: String): String? = null
            override suspend fun writePrivate(name: String, text: String) {}
        }
        val controller = ArtworkController(
            artHost,
            { auth, onArt, onWritten, onAndroidArt, logged, _, _ ->
                ssEngine(http, FakeFitter(), FakeHasher(), source, ContentRef("Home"), auth, { 0L }, { delay(it) }, onArt, onWritten, null, onAndroidArt, emptyList(), logged)
            },
            Credentials(settings, "DEV"), FakeRunner(), scope,
        )
        val screen = kotlinx.coroutines.flow.MutableStateFlow<Screen>(Screen.Loading)
        val selection = kotlinx.coroutines.flow.MutableStateFlow(com.nxredux.mobile.ui.nx.ListSelection(0, 0))
        val screens = ArtworkScreenController(
            scope, screen, selection, kotlinx.coroutines.flow.MutableStateFlow(null), settings, ScreenLoader(scope), controller, host,
        )
        scope.launch { controller.state.collect { screens.onState(it) } }
        screens.open(); advanceUntilIdle()
        fun fetchMissing() {
            val page = assertIs<Screen.Artwork>(screen.value)
            selection.value = com.nxredux.mobile.ui.nx.ListSelection(page.rows.indexOfFirst { it.action == ArtworkAction.FETCH_MISSING }, page.rows.size)
            screens.onButton(page, NxButton.A)
        }
        /** The stage's lit word beside the open page, checked to be among its words. */
        fun parentRow(): ArtworkAction {
            val word = assertNotNull(screens.wordOf(screen.value))
            assertTrue(screens.stagePage(screen.value).rows.any { it.action == word })
            return word
        }
        // No job yet: Progress opens starting, with Progress lit beside it (not row 0).
        fetchMissing()
        val starting = assertIs<Screen.ArtworkProgress>(screen.value)
        assertTrue(starting.starting)
        assertTrue(starting.job.items.isEmpty())
        assertEquals("Starting art fetch…", progressHeader(starting.job, starting.starting))
        assertEquals(ArtworkAction.PROGRESS, parentRow())
        advanceUntilIdle()
        val first = assertIs<Screen.ArtworkProgress>(screen.value)
        assertFalse(first.starting)
        assertTrue(first.job.phase != JobPhase.IDLE)
        assertEquals(listOf("Tetris"), first.job.items.map { it.name })
        // A finished job: the next fetch never shows the last one's items.
        screens.onButton(first, NxButton.B)
        host.games = host.games + host.games.single().copy(gamePath = "/gb/Zelda.gb", name = "Zelda", fileName = "Zelda.gb")
        fetchMissing()
        val again = assertIs<Screen.ArtworkProgress>(screen.value)
        assertTrue(again.starting); assertTrue(again.job.items.isEmpty())
        assertEquals(ArtworkAction.PROGRESS, parentRow())
        advanceUntilIdle()
        assertEquals(listOf("Tetris", "Zelda"), assertIs<Screen.ArtworkProgress>(screen.value).job.items.map { it.name })
    }

    @Test fun the_stage_words_follow_a_running_fetch() = runTest {
        val r = rig()
        r.http.latency = 5_000
        r.http.on("romnom=Tetris.gb", Scrape.found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png"))
        r.http.on("https://m/", Scrape.OK_PNG)
        r.state.openArtwork()
        fun gbMissing() = assertIs<Screen.ArtworkConsoles>(r.state.artworkPreview(ArtworkAction.FETCH_BY_CONSOLE)).rows.first { it.name == "Game Boy" }.missing
        assertEquals(1, gbMissing())
        r.state.pick(ArtworkAction.FETCH_MISSING)
        r.state.onButton(NxButton.B)
        r.state.pick(ArtworkAction.FETCH_BY_CONSOLE)
        val job = assertNotNull(r.state.artworkJob)
        val before = job.value
        fun words() = r.state.artworkStagePage(r.state.screen.value)
        assertTrue(words().rows.any { it.action == ArtworkAction.PROGRESS }, "${words().rows}")   // the words gain Progress
        assertTrue(words().details.any { it.startsWith("Fetching 0 / 3") }, "${words().details}")
        advanceUntilIdle()   // the fetch runs to the end beside Fetch by console
        assertTrue(job.value !== before)   // the stage's key moved on, so its words are drawn again
        assertTrue(words().details.none { it.startsWith("Fetching") }, "${words().details}")
        assertEquals(emptyList(), words().details)
        // Fetch by console's own counts follow too: Tetris got its art; so does the root's preview of it.
        val consoles = assertIs<Screen.ArtworkConsoles>(r.state.screen.value).rows
        assertEquals(listOf("Game Boy" to 0, "Game Boy Advance" to 1, "Sega Genesis" to 1), consoles.map { it.name to it.missing })
        r.state.onButton(NxButton.B)
        assertEquals(0, gbMissing())
    }
}
