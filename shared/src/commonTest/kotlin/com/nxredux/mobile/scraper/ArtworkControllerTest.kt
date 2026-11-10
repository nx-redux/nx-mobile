package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.LibraryScanner
import com.nxredux.mobile.library.Sidecars
import com.nxredux.mobile.library.ensurePath
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.scraper.Scrape.OK_PNG
import com.nxredux.mobile.scraper.Scrape.found
import com.nxredux.mobile.storage.MemoryPrivateFiles
import com.nxredux.mobile.storage.MemorySidecarStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ArtworkControllerTest {
    private val tetris = Scrape.homeGame("Tetris.gb")
    private val zelda = Scrape.homeGame("Zelda.gb")
    private val sonic = Scrape.homeGame("Sonic.md", tag = "MD")

    private class Rig(
        scope: TestScope, games: List<LibraryGameRef>, val creds: FakeCredentials = FakeCredentials(), more: List<ArtSource> = emptyList(),
        jobClock: () -> Long = { scope.testScheduler.currentTime }, val fs: FakeRomsSource = Scrape.homeTree(),
        pixels: PixelCodec? = null, engineClock: () -> Long = { scope.testScheduler.currentTime },
    ) {
        val http = FakeHttp().apply { clock = { scope.testScheduler.currentTime } }
        val host = FakeHost(games)
        val runner = FakeRunner()
        val art = FakeArtStore()
        var androidRefreshed = 0
        val cache = FakeOptionCache()
        val controller = ArtworkController(
            host,
            { auth, onArt, onWritten, _, logged, onOptions, picked ->
                ssEngine(
                    http, FakeFitter(), FakeHasher(), fs, Scrape.home, auth, engineClock, { delay(it) }, onArt, onWritten,
                    art, { androidRefreshed++ }, more, logged, onOptions, picked, pixels = pixels, infoFiles = MemoryPrivateFiles(),
                )
            },
            creds, runner, scope, clock = jobClock, optionCache = cache,
        )
    }

    @Test fun persistedQueueResumes() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        val half = ScrapeState(JobPhase.RUNNING, listOf(Scrape.item(tetris, ItemStatus.DONE), Scrape.item(zelda, ItemStatus.SEARCHING)))
        rig.host.files[ArtworkController.JOB_FILE] = ScrapeJobCodec.encode(half, replace = false)
        rig.http.on("romnom=Zelda.gb", found("ss" to "https://m/z-ss.png", "box-2D" to "https://m/z-box.png"))
        rig.http.on("https://m/", OK_PNG)

        assertTrue(rig.controller.restorePending())
        assertEquals(JobPhase.PAUSED, rig.controller.state.value.phase)
        assertEquals(ItemStatus.WAITING, rig.controller.state.value.items[1].status)

        rig.controller.resume()
        advanceUntilIdle()
        val end = rig.controller.state.value
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertEquals(0, rig.http.count("romnom=Tetris.gb"))
        assertEquals(JobPhase.DONE, ScrapeJobCodec.decode(rig.host.files.getValue(ArtworkController.JOB_FILE))!!.first.phase)
        assertEquals(zelda.gamePath to ArtRefs(box2d = "Home/Roms/GB/.media/boxart2d/Zelda.png", screenshot = "Home/Roms/GB/.media/screenshot/Zelda.png"), rig.host.applied.single())
        assertTrue(rig.runner.calls.any { it.first && it.second == "Fetching art 1/2" })
        assertEquals(false to null, rig.runner.calls.last())
        assertFalse(rig.controller.restorePending())   // a finished job is not offered again
    }

    private fun jobSaves(rig: Rig) = rig.host.writes.filter { it.first == ArtworkController.JOB_FILE }.map { ScrapeJobCodec.decode(it.second)!!.first }

    @Test fun aRunningJobIsSavedEveryFewGamesNotPerGame() = runTest {
        val games = (1..12).map { Scrape.homeGame("G$it.gb") }
        val rig = Rig(this, games, jobClock = { 0L })   // no time passes: only the count saves
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.controller.fetchMissing()
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        // The start, the tenth game, the end: not twelve saves.
        assertEquals(listOf(0, ArtworkController.SAVE_EVERY_ITEMS, 12), jobSaves(rig).map { it.done })
    }

    @Test fun aSlowJobIsSavedAfterSomeSecondsEvenBeforeTenGames() = runTest {
        var now = 0L
        val rig = Rig(this, listOf(tetris, zelda, sonic), jobClock = { now += ArtworkController.SAVE_EVERY_MS; now })   // each game outlasts the interval
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.controller.fetchMissing()
        advanceUntilIdle()
        val saves = jobSaves(rig)
        // The start, each game once the next one is fetched, then the end (the last game's finish is shown with it).
        assertEquals(listOf(0, 1, 2, 3), saves.map { it.done })
        assertEquals(JobPhase.PAUSED, saves[1].phase)   // saved running: decoded as paused
        assertEquals(JobPhase.DONE, saves.last().phase)
    }

    @Test fun pauseSavesTheJobAtOnce() = runTest {
        val rig = Rig(this, listOf(tetris, zelda, sonic))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.controller.fetchMissing()
        while (rig.controller.state.value.done < 1) testScheduler.advanceTimeBy(50)
        rig.controller.pause()
        advanceUntilIdle()
        val last = jobSaves(rig).last()
        assertEquals(JobPhase.PAUSED, last.phase); assertTrue(last.done >= 1)
    }

    @Test fun aNewJobIsPersistedBeforeItsFirstGameFinishes() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        rig.host.files[ArtworkController.JOB_FILE] = ScrapeJobCodec.encode(ScrapeState(JobPhase.DONE, listOf(Scrape.item(sonic, ItemStatus.DONE))), replace = false)
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.controller.fetchMissing()
        testScheduler.runCurrent()
        assertEquals(0, rig.controller.state.value.done)
        // The process dies here: the saved queue is the new job's, offered again as paused.
        val (saved, _) = ScrapeJobCodec.decode(rig.host.files.getValue(ArtworkController.JOB_FILE))!!
        assertEquals(listOf(tetris.gamePath, zelda.gamePath), saved.items.map { it.gamePath })
        assertEquals(JobPhase.PAUSED, saved.phase)
    }

    @Test fun resetDeletesOnlyLoggedFiles() = runTest {
        val rig = Rig(this, listOf(tetris, sonic))
        val media = rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media"))!!
        rig.fs.writeBytes(media, "Other.png", byteArrayOf(7))   // hand-placed
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png"))
        rig.http.on("romnom=Sonic.md", found("ss" to "https://m/s-ss.png", "box-2D" to "https://m/s-box.png"))
        rig.http.on("https://m/", OK_PNG)
        rig.controller.fetchMissing()
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        val logged = rig.host.files.getValue(ArtworkController.LOG_FILE).lines().filter { it.isNotBlank() }
        assertEquals(4, logged.size)
        assertTrue("Roms/GB/.media/boxart2d/Tetris.png\tscreenscraper\t42\tbox-2D/us" in logged, "$logged")

        assertEquals(ResetResult(2), rig.controller.resetArt(setOf("MD")))
        assertNull(rig.fs.bytesAt("Home/Roms/MD/.media/boxart2d/Sonic.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/MD/.media/screenshot/Sonic.png"))
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
        assertEquals(sonic.gamePath to ArtRefs(), rig.host.applied.last())

        assertEquals(ResetResult(2), rig.controller.resetArt(null))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/Other.png"))
        assertEquals(tetris.gamePath to ArtRefs(), rig.host.applied.last())
        assertEquals("", rig.host.files.getValue(ArtworkController.LOG_FILE).trim())
        assertEquals(ResetResult(0), rig.controller.resetArt(null))
    }

    @Test fun resetDeletesTypedAndLegacyMix() = runTest {
        // A log from before the mix was dropped (path-only lines) plus typed lines (a 2D box in the legacy box2d/, the
        // wheel, the device-made mix); one screenshot was never logged. By console, so every path must be a known one.
        val rig = Rig(this, listOf(tetris))
        val media = rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media"))!!
        rig.fs.writeBytes(media, "Tetris.png", byteArrayOf(1))
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart"))!!, "Tetris.png", byteArrayOf(2))
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "box2d"))!!, "Tetris.jpg", byteArrayOf(3))
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "screenshot"))!!, "Tetris.png", byteArrayOf(4))
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart2d"))!!, "Tetris.png", byteArrayOf(5))
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "wheel"))!!, "Tetris.png", byteArrayOf(6))
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "mix"))!!, "Tetris.png", byteArrayOf(7))
        rig.host.files[ArtworkController.LOG_FILE] =
            "Roms/GB/.media/Tetris.png\nRoms/GB/.media/boxart/Tetris.png\nRoms/GB/.media/box2d/Tetris.jpg\tscreenscraper\t42\tbox-2D/us\n" +
                "Roms/GB/.media/boxart2d/Tetris.png\tscreenscraper\t42\tbox-2D/us\nRoms/GB/.media/wheel/Tetris.png\tsteamgriddb\t5\t6\n" +
                "Roms/GB/.media/mix/Tetris.png\tdevice\t\t\n"
        assertEquals(ResetResult(6), rig.controller.resetArt(setOf("GB")))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/Tetris.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/box2d/Tetris.jpg"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/wheel/Tetris.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/mix/Tetris.png"))
        assertEquals("", rig.host.files.getValue(ArtworkController.LOG_FILE).trim())
        assertEquals(listOf("screenshot"), rig.fs.childNames("Home/Roms/GB/.media"))   // the emptied slot folders went
        resetLeavesUnloggedFiles(rig)
        resetReappliesArtOnDisk(rig)
    }

    @Test fun resetRemovesTheSlotFoldersItEmptiedAndMediaWhenNothingIsLeft() = runTest {
        val rig = Rig(this, listOf(tetris, sonic))
        rig.http.on("romnom=", found("ss" to "https://m/ss.png", "box-2D" to "https://m/box.png"))
        rig.http.on("https://m/", OK_PNG)
        val gbMedia = rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media"))!!
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "screenshot"))!!, "Mine.png", byteArrayOf(1))   // hand-placed
        rig.controller.fetchMissing()
        advanceUntilIdle()
        assertEquals(ResetResult(4), rig.controller.resetArt(null))
        fun names(path: String) = rig.fs.childNames(path)
        assertEquals(listOf("Sonic.md"), names("Home/Roms/MD"))           // MD's .media held only art: gone
        assertEquals(listOf("screenshot"), names(gbMedia.value))          // boxart2d/ emptied and removed; the user's file kept
        assertEquals(listOf("Mine.png"), names("Home/Roms/GB/.media/screenshot"))
    }

    @Test fun aFileResetCouldNotDeleteStaysLoggedAndUncounted() = runTest {
        val wheel = "Home/Roms/GB/.media/wheel"
        val fs = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"), "Home/Roms" to listOf("GB"), "Home/Roms/GB" to listOf("Tetris.gb", ".media"),
                "Home/Roms/GB/.media" to listOf("wheel", "boxart"), wheel to listOf("Tetris.png"), "Home/Roms/GB/.media/boxart" to listOf("Tetris.png"),
            ),
            bytes = mapOf("$wheel/Tetris.png" to byteArrayOf(1), "Home/Roms/GB/.media/boxart/Tetris.png" to byteArrayOf(2)),
            readOnly = setOf(wheel),
        )
        val rig = Rig(this, listOf(tetris), fs = fs)
        rig.host.files[ArtworkController.LOG_FILE] =
            "Roms/GB/.media/wheel/Tetris.png\tsteamgriddb\t5\t6\nRoms/GB/.media/boxart/Tetris.png\tscreenscraper\t42\tbox-3D/us\n"
        assertEquals(ResetResult(1), rig.controller.resetArt(null))
        assertNotNull(fs.bytesAt("$wheel/Tetris.png"))
        assertNull(fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        assertEquals("Roms/GB/.media/wheel/Tetris.png\tsteamgriddb\t5\t6", rig.host.files.getValue(ArtworkController.LOG_FILE).trim())
        assertEquals(listOf("wheel"), fs.childNames("Home/Roms/GB/.media"))   // still holds the file: kept
        // Still the app's: the next Reset tries it again.
        assertEquals(ResetResult(0), rig.controller.resetArt(null))
        assertEquals("Roms/GB/.media/wheel/Tetris.png\tsteamgriddb\t5\t6", rig.host.files.getValue(ArtworkController.LOG_FILE).trim())
    }

    /** Each run kind on Tetris (art missing, a remembered info miss, an unreadable answer): true when the miss is left. */
    private suspend fun TestScope.missLeftBy(label: String, start: suspend (ArtworkController) -> Unit): Boolean {
        val rig = Rig(this, listOf(tetris))
        rig.controller.info!!.markMiss(tetris.gamePath)
        rig.http.on("romnom=Tetris.gb", Scrape.json(ScraperFixtures.GARBAGE))
        start(rig.controller)
        advanceUntilIdle()
        assertTrue(rig.http.count("romnom=Tetris.gb") > 0, "$label: ${rig.http.calls}")
        return rig.controller.info!!.isMiss(tetris.gamePath)
    }

    @Test fun only_fetch_all_and_replace_runs_ask_a_remembered_info_miss_again() = runTest {
        assertTrue(missLeftBy("Fetch missing") { it.fetchMissing() }, "Fetch missing")
        assertTrue(missLeftBy("console") { it.fetchConsole(setOf("GB")) }, "console")
        assertFalse(missLeftBy("Fetch all") { it.fetchAll(replaceExisting = false) }, "Fetch all")
        assertFalse(missLeftBy("Fetch all, Replace") { it.fetchAll(replaceExisting = true) }, "Fetch all, Replace")
        assertFalse(missLeftBy("console, Replace") { it.fetchConsole(setOf("GB"), replaceExisting = true) }, "console, Replace")
        assertTrue(missLeftBy("one game") { it.fetchGame(tetris.gamePath) }, "one game")
    }

    @Test fun reset_deletes_info_files() = runTest {
        val rig = Rig(this, listOf(tetris, sonic))
        val info = StoredGameInfo("Blocks fall.", "Puzzle", "1989", "Nintendo", "en", "42", 1L)
        val store = rig.controller.info!!
        assertTrue(store.write(tetris, info))
        assertTrue(store.write(sonic, info))
        val handPlaced = rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "info"))!!
        rig.fs.writeText(handPlaced, "Mine.json", "{}")
        assertEquals(info, store.read(tetris))

        assertEquals(ResetResult(1), rig.controller.resetArt(setOf("MD")))
        assertNull(rig.fs.readText(ContentRef("Home/Roms/MD/.media/info/Sonic.json")))
        assertEquals(listOf("Sonic.md"), rig.fs.childNames("Home/Roms/MD"))   // info/ and .media emptied: removed
        assertNull(store.read(sonic))
        assertEquals(info, store.read(tetris))

        assertEquals(ResetResult(1), rig.controller.resetArt(null))
        assertNull(rig.fs.readText(ContentRef("Home/Roms/GB/.media/info/Tetris.json")))
        assertNull(store.read(tetris))
        assertEquals(listOf("Mine.json"), rig.fs.childNames("Home/Roms/GB/.media/info"))
    }

    private fun resetLeavesUnloggedFiles(rig: Rig) =
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))

    private fun resetReappliesArtOnDisk(rig: Rig) =
        assertEquals(tetris.gamePath to ArtRefs(screenshot = "Home/Roms/GB/.media/screenshot/Tetris.png"), rig.host.applied.single())

    @Test fun resetByConsoleDeletesOnlyItsGamesArtAndKeepsWhatElseIsOnDisk() = runTest {
        val switched = Scrape.homeGame("Zelda.gb", tag = "GBC", dir = "Roms/GB")   // tag-switched: GBC in GB's folder
        val mario = Scrape.homeGame("Mario.gb")                                      // hidden by reset time
        val rig = Rig(this, listOf(tetris, switched, mario))
        val box = rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart"))!!
        rig.fs.writeBytes(box, "Tetris.png", byteArrayOf(7))   // hand-placed variant: never logged
        rig.http.on("romnom=", found("ss" to "https://m/ss.png"))
        rig.http.on("https://m/", OK_PNG)
        rig.controller.fetchMissing()
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        rig.host.hidden = listOf(mario); rig.host.games = rig.host.games - mario
        rig.host.applied.clear()

        assertEquals(ResetResult(2), rig.controller.resetArt(setOf("GB")))   // Tetris's and hidden Mario's screenshot
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Zelda.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Mario.png"))
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        assertEquals(
            setOf(tetris.gamePath to ArtRefs(boxart = "Home/Roms/GB/.media/boxart/Tetris.png"), mario.gamePath to ArtRefs()),
            rig.host.applied.toSet(),   // the hand-placed box stays in the index; the GBC game is not touched
        )
        assertEquals(
            setOf("Roms/GB/.media/screenshot/Zelda.png"),
            WrittenArtLog.decode(rig.host.files.getValue(ArtworkController.LOG_FILE)).entries,
        )
    }

    @Test fun resetOfAndroidOrAllDeletesTheStoredScreenshotsAndRefreshesTheConsole() = runTest {
        val rig = Rig(this, listOf(tetris))
        rig.art.files["com.a/.Main"] = byteArrayOf(1); rig.art.files["com.b/.Main"] = byteArrayOf(2)
        assertEquals(ResetResult(0), rig.controller.resetArt(setOf("GB")))
        assertEquals(2, rig.art.files.size)
        assertEquals(0, rig.androidRefreshed)

        assertEquals(ResetResult(2), rig.controller.resetArt(setOf(ANDROID_CONSOLE_TAG)))
        assertTrue(rig.art.files.isEmpty())
        assertEquals(1, rig.androidRefreshed)

        rig.art.files["com.c/.Main"] = byteArrayOf(3)
        assertEquals(ResetResult(1), rig.controller.resetArt(null))
        assertTrue(rig.art.files.isEmpty())
        assertEquals(2, rig.androidRefreshed)
        assertEquals(ResetResult(0), rig.controller.resetArt(null))   // nothing left: no refresh
        assertEquals(2, rig.androidRefreshed)
    }

    @Test fun resetOfAndroidForgetsItsPicksAndPreviewsSoAFetchWritesAgain() = runTest {
        // As the app's host: the Android games are in libraryGames only, not allLibraryGames.
        val pgo = Scrape.androidGame("com.nianticlabs.pokemongo/com.unity.Main", "Pokémon GO")
        val rig = Rig(this, listOf(tetris, pgo))
        val store = ArtOptionStore(rig.host)
        val shot = ArtOption(ArtSlot.SCREENSHOT, "https://m/pgo-ss.png", SourceId.SS, assetId = "ss/us")
        store.record(pgo.gamePath, listOf(shot), wide = true)
        store.setPick(pgo.gamePath, ArtSlot.SCREENSHOT, shot.key)
        rig.cache.files["${pgo.gamePath}\n${shot.key}"] = byteArrayOf(1)
        rig.art.files[pgo.fileName] = byteArrayOf(9)
        rig.controller.resetArt(setOf(ANDROID_CONSOLE_TAG))
        assertTrue(rig.art.files.isEmpty())
        assertEquals(emptySet(), ArtOptionStore(rig.host).pickedSlots(pgo.gamePath))
        assertFalse("${pgo.gamePath}\n${shot.key}" in rig.cache.files)
        rig.http.on("jeuInfos", found("ss" to "https://m/pgo-ss.png"))
        rig.http.on("https://m/", OK_PNG)
        assertEquals(ItemStatus.DONE, rig.controller.fetchGame(pgo.gamePath).status)
        assertContentEquals(OK_PNG.body, rig.art.files[pgo.fileName])
    }

    @Test fun androidCurrentKeysAreItsPicksOnlyNeverAMirrorFile() = runTest {
        val app = Scrape.androidGame("com.a", "A")
        val rig = Rig(this, listOf(app))
        val shot = ArtOption(ArtSlot.SCREENSHOT, "https://m/a-ss.png", SourceId.SS, assetId = "ss/us")
        val box = ArtOption(ArtSlot.BOX_2D, "https://m/a-box.png", SourceId.SS, assetId = "box-2D/us")
        // A file and log entry where ArtPaths would put a ROM named like the app id: never the app's art.
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", ".sources", "android", ".media", "screenshot"))!!, "com.png", byteArrayOf(1))
        ArtLogStore(rig.host, ArtworkController.LOG_FILE).add(listOf("Roms/.sources/android/.media/screenshot/com.png" to shot.origin))
        val store = ArtOptionStore(rig.host)
        store.record(app.gamePath, listOf(shot, box), wide = true)
        store.setPick(app.gamePath, ArtSlot.BOX_2D, box.key)
        val r = rig.controller.artOptions(app.gamePath, replaceExisting = false, autoSearch = false)
        assertEquals(mapOf(ArtSlot.BOX_2D to box.key), r.currentKeys)
    }

    @Test fun resetPausesAnAndroidJobForTheAndroidScope() = runTest {
        val pgo = Scrape.androidGame("com.nianticlabs.pokemongo/com.unity.Main", "Pokémon GO")
        val rig = Rig(this, listOf(tetris, pgo))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.latency = 100
        rig.controller.fetchConsole(setOf(ANDROID_CONSOLE_TAG))
        testScheduler.runCurrent()
        assertEquals(JobPhase.RUNNING, rig.controller.state.value.phase)
        assertEquals(ResetResult(0, pausedJob = true), rig.controller.resetArt(setOf(ANDROID_CONSOLE_TAG)))
        assertEquals(JobPhase.PAUSED, rig.controller.state.value.phase)
    }

    @Test fun resetPausesOnlyAJobWithGamesInScope() = runTest {
        val rig = Rig(this, listOf(tetris, zelda, sonic))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.controller.fetchConsole(setOf("MD"))
        testScheduler.runCurrent()
        assertEquals(JobPhase.RUNNING, rig.controller.state.value.phase)
        assertEquals(ResetResult(0, pausedJob = false), rig.controller.resetArt(setOf("GB")))
        assertEquals(JobPhase.RUNNING, rig.controller.state.value.phase)   // an MD job is left alone
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertEquals(ItemStatus.DONE, rig.controller.state.value.items.single().status)

        rig.controller.fetchConsole(setOf("GB"))
        testScheduler.runCurrent()
        assertEquals(JobPhase.RUNNING, rig.controller.state.value.phase)
        assertEquals(ResetResult(0, pausedJob = true), rig.controller.resetArt(setOf("gb")))
        assertEquals(JobPhase.PAUSED, rig.controller.state.value.phase)
        assertEquals(ResetResult(2, pausedJob = false), rig.controller.resetArt(null))   // Sonic's box and screenshot; nothing running
    }

    @Test fun aFetchBeforeTheRestoreMergesIntoTheSavedPausedJob() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        rig.host.files[ArtworkController.JOB_FILE] = ScrapeJobCodec.encode(
            ScrapeState(JobPhase.PAUSED, listOf(Scrape.item(tetris), Scrape.item(sonic))), replace = false,
        )
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.controller.fetchMissing()   // the Artwork page's restore has not run yet
        testScheduler.runCurrent()
        assertFalse(rig.controller.restorePending())   // too late: the fetch already took the saved queue in
        advanceUntilIdle()
        val end = rig.controller.state.value
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(tetris.gamePath, sonic.gamePath, zelda.gamePath), end.items.map { it.gamePath })
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.SKIPPED, ItemStatus.DONE), end.items.map { it.status })   // Sonic left the library
        assertEquals(1, rig.http.count("romnom=Tetris.gb"))
    }

    @Test fun unavailableWithoutCredentials() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""))
        assertFalse(rig.controller.available)
        rig.controller.fetchMissing()
        advanceUntilIdle()
        assertTrue(rig.http.calls.isEmpty())
        assertEquals(JobPhase.IDLE, rig.controller.state.value.phase)
    }

    @Test fun availableWithoutScreenScraperCredentials() = runTest {
        val other = FakeSource("libretro").apply {
            offer("Tetris.gb", ArtSlot.BOX_2D, "https://o/box"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://o/ss")
        }
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(other))
        rig.http.on("https://o/", OK_PNG)
        assertTrue(rig.controller.available)
        assertFalse(rig.controller.ssAvailable)
        rig.controller.fetchMissing()
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertEquals(listOf(ItemStatus.DONE), rig.controller.state.value.items.map { it.status })
        assertEquals(0, rig.http.count(ScraperRequest.API))   // ScreenScraper is off: never called
    }

    @Test fun tgdbAllowanceIsAFreeReadOnlyWithAKey() = runTest {
        val tgdbHttp = FakeHttp().apply { on("API/Limit", HttpResult(200, TgdbFixtures.limit(left = 1820).encodeToByteArray())) }
        val rig = Rig(this, emptyList(), more = listOf(TheGamesDbSource(tgdbHttp, { "K" }, { 0L }) {}))
        assertTrue(rig.controller.tgdbAvailable)
        assertEquals(1820, rig.controller.tgdbAllowance())
        assertEquals(1820, rig.controller.state.value.tgdbLeft)
        assertEquals(1, tgdbHttp.count("API/Limit"))
        val none = Rig(this, emptyList())
        assertFalse(none.controller.tgdbAvailable)
        assertEquals(null, none.controller.tgdbAllowance())
        val off = Rig(this, emptyList(), more = listOf(TheGamesDbSource(FakeHttp(), { "" }, { 0L }) {}))
        assertFalse(off.controller.tgdbAvailable)
        assertEquals(null, off.controller.tgdbAllowance())
    }

    @Test fun tgdbKeySavedOnlyWhenValidAndTheSourceIsOffWithoutIt() = runTest {
        val creds = FakeCredentials()
        val tgHttp = FakeHttp()
        val tg = TheGamesDbSource(tgHttp, { creds.tgdbUserKey.orEmpty() }, { 0L }) {}
        val rig = Rig(this, emptyList(), creds, more = listOf(tg))
        // No key shipped: the source is there (its Account rows) but off until the user adds one.
        assertTrue(rig.controller.tgdbSupported)
        assertFalse(rig.controller.tgdbAvailable)
        assertFalse(rig.controller.tgdbKeySet)
        assertNull(rig.controller.tgdbAllowance())
        val unknownKey = """{"remaining_monthly_allowance":0,"allowance_refresh_timer":0}"""
        for ((answer, result) in listOf(
            HttpResult(200, unknownKey.encodeToByteArray()) to KeyResult.BAD_KEY, HttpResult(403, null) to KeyResult.BAD_KEY,
            HttpResult(-1, null) to KeyResult.OFFLINE, HttpResult(503, null) to KeyResult.UNAVAILABLE,
        )) {
            tgHttp.set("apikey=BAD", answer)
            assertEquals(result, rig.controller.setTgdbKey("BAD"))
            assertNull(creds.tgdbUserKey)
            assertFalse(rig.controller.tgdbAvailable)
        }
        assertEquals(KeyResult.BAD_KEY, rig.controller.setTgdbKey("  "))
        tgHttp.set("apikey=GOOD", HttpResult(200, TgdbFixtures.limit(left = 2000).encodeToByteArray()))
        assertEquals(KeyResult.OK, rig.controller.setTgdbKey(" GOOD "))
        assertEquals("GOOD", creds.tgdbUserKey)
        assertTrue(rig.controller.tgdbKeySet && rig.controller.tgdbAvailable)
        assertNull(rig.controller.state.value.tgdbLeft)   // unknown until read
        assertEquals(2000, rig.controller.tgdbAllowance())
        assertEquals(2000, rig.controller.state.value.tgdbLeft)
        // A refused new key leaves the saved one.
        tgHttp.set("apikey=OTHER", HttpResult(200, unknownKey.encodeToByteArray()))
        assertEquals(KeyResult.BAD_KEY, rig.controller.setTgdbKey("OTHER"))
        assertEquals("GOOD", creds.tgdbUserKey)
        // Removing it turns the source off again and forgets the allowance.
        rig.controller.clearTgdbKey()
        assertNull(creds.tgdbUserKey)
        assertNull(rig.controller.state.value.tgdbLeft)
        assertFalse(tg.enabled || rig.controller.tgdbAvailable || rig.controller.tgdbKeySet)
        assertNull(rig.controller.tgdbAllowance())
        // A build without the source: nothing to check against.
        assertEquals(KeyResult.UNAVAILABLE, Rig(this, emptyList()).controller.setTgdbKey("K"))
        assertFalse(Rig(this, emptyList()).controller.tgdbSupported)
    }

    @Test fun sgdbKeySavedOnlyWhenValid() = runTest {
        val creds = FakeCredentials()
        val sgHttp = FakeHttp()
        val sg = SteamGridDbSource(sgHttp, { creds.sgdbKey }, { 0L }) {}
        val rig = Rig(this, emptyList(), creds, more = listOf(sg))
        assertFalse(sg.enabled || rig.controller.sgdbKeySet)
        for ((status, result) in listOf(401 to KeyResult.BAD_KEY, -1 to KeyResult.OFFLINE, 503 to KeyResult.UNAVAILABLE)) {
            sgHttp.set("search/autocomplete/", HttpResult(status, null))
            assertEquals(result, rig.controller.setSgdbKey("BAD"))
            assertNull(creds.sgdbKey)
        }
        assertEquals(KeyResult.BAD_KEY, rig.controller.setSgdbKey("  "))
        sgHttp.set("search/autocomplete/", HttpResult(200, SgdbFixtures.autocomplete.encodeToByteArray()))
        assertEquals(KeyResult.OK, rig.controller.setSgdbKey(" GOOD "))
        assertEquals("GOOD", creds.sgdbKey)
        assertTrue(sg.enabled && rig.controller.sgdbKeySet)
        assertEquals(mapOf("Authorization" to "Bearer GOOD"), sgHttp.headers.last())
        // A refused new key leaves the saved one.
        sgHttp.set("search/autocomplete/", HttpResult(401, null))
        assertEquals(KeyResult.BAD_KEY, rig.controller.setSgdbKey("OTHER"))
        assertEquals("GOOD", creds.sgdbKey)
        rig.controller.clearSgdbKey()
        assertNull(creds.sgdbKey)
        assertFalse(sg.enabled)
        // A build without the source: nothing to check against.
        assertEquals(KeyResult.UNAVAILABLE, Rig(this, emptyList()).controller.setSgdbKey("K"))
    }

    @Test fun canFetchNeedsASourceThatKnowsTheSystem() = runTest {
        val rig = Rig(this, emptyList())
        assertTrue(rig.controller.canFetch("GB", "Tetris.gb"))
        assertFalse(rig.controller.canFetch("PORTS", "Doom.sh"))   // excluded
        assertFalse(rig.controller.canFetch("", "thing.chd"))
        assertFalse(rig.controller.canFetch("PICO8X", "game.p8"))
        assertTrue(rig.controller.canFetch("ANDROID", "com.a/.Main", android = true))   // ScreenScraper with the art store
        assertFalse(rig.controller.canFetch("ANDROID", "com.a/.Main"))                  // a ROM folder tagged ANDROID has no system
        val anything = FakeSource("any", knowsGame = { true })
        val wide = Rig(this, emptyList(), FakeCredentials(devId = ""), more = listOf(anything))
        assertTrue(wide.controller.canFetch("ZZZ", "x.bin"))
        assertFalse(wide.controller.canFetch("PORTS", "Doom.sh"))   // excluded for every source
        anything.on = false
        assertFalse(wide.controller.canFetch("ZZZ", "x.bin"))
    }

    @Test fun everySourceStoppedPausesWithTheNotesAndResumeAsksAgain() = runTest {
        val rig = Rig(this, listOf(tetris))
        rig.http.on("romnom=Tetris.gb", HttpResult(503, null))
        rig.controller.fetchMissing()
        advanceUntilIdle()
        val paused = rig.controller.state.value
        assertEquals(JobPhase.PAUSED, paused.phase)
        assertEquals(mapOf(SourceId.SS to ScreenScraperSource.UNAVAILABLE), paused.notes)
        assertEquals(paused.notes, ScrapeJobCodec.decode(rig.host.files.getValue(ArtworkController.JOB_FILE))!!.first.notes)
        rig.http.set("romnom=Tetris.gb", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.controller.resume()
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertTrue(rig.controller.state.value.notes.isEmpty())
        assertEquals(listOf(ItemStatus.DONE), rig.controller.state.value.items.map { it.status })
    }

    @Test fun signInValidatesWithUserInfo() = runTest {
        val rig = Rig(this, listOf(tetris))
        rig.http.on("ssuserInfos", Scrape.quota(5, 100))
        assertEquals(SignInResult.OK, rig.controller.signIn("me", "pw"))
        assertEquals("me", rig.creds.user)
        assertEquals("pw", rig.creds.password)
        assertEquals(SsQuota(5, 100), rig.controller.state.value.quota)

        rig.http.set("ssuserInfos", HttpResult(-1, null))
        assertEquals(SignInResult.OFFLINE, rig.controller.signIn("me", "pw2"))
        assertEquals("me", rig.creds.user)   // not reached: the login before it stays, the new one is not kept
        assertEquals("pw", rig.creds.password)
        rig.http.set("ssuserInfos", HttpResult(503, null))
        assertEquals(SignInResult.UNAVAILABLE, rig.controller.signIn("me", "pw2"))
        assertEquals("me", rig.creds.user)   // ScreenScraper down: the new login is kept
        assertEquals("pw2", rig.creds.password)

        rig.http.set("ssuserInfos", Scrape.json("""{"response":{}}"""))
        assertEquals(SignInResult.BAD_LOGIN, rig.controller.signIn("me", "bad"))
        assertNull(rig.creds.user)
        assertNull(rig.creds.password)
        rig.http.set("ssuserInfos", HttpResult(403, ScraperFixtures.USER_LOGIN_REFUSED.encodeToByteArray()))
        assertEquals(SignInResult.BAD_LOGIN, rig.controller.signIn("me", "bad"))
        assertNull(rig.creds.user)
        assertNull(rig.creds.password)
    }

    @Test fun signInKeepsTheLoginWhenScreenScraperRefusesTheAppNotTheUser() = runTest {
        val rig = Rig(this, listOf(tetris))
        rig.http.set("ssuserInfos", HttpResult(403, ScraperFixtures.DEV_LOGIN_REFUSED.encodeToByteArray()))
        assertEquals(SignInResult.BUILD_REFUSED, rig.controller.signIn("me", "pw"))
        assertEquals("me", rig.creds.user)   // nothing says the login is wrong
        assertEquals("pw", rig.creds.password)
        for (code in listOf(401, 426, 423, 503)) {   // closed to non-members, app blocked, unavailable: never a bad login
            rig.creds.user = null; rig.creds.password = null
            rig.http.set("ssuserInfos", HttpResult(code, null))
            assertEquals(SignInResult.UNAVAILABLE, rig.controller.signIn("me", "pw"), "status $code")
            assertEquals("me", rig.creds.user, "status $code")
            assertEquals("pw", rig.creds.password, "status $code")
        }
    }

    @Test fun signOutForgetsTheQuota() = runTest {
        val rig = Rig(this, listOf(tetris))
        rig.http.on("ssuserInfos", Scrape.quota(5, 100))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        assertEquals(SignInResult.OK, rig.controller.signIn("me", "pw"))
        rig.controller.signOut()
        assertNull(rig.controller.state.value.quota)
        rig.controller.fetchMissing()
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertNull(rig.controller.state.value.quota)
        assertEquals(1, rig.http.count("ssuserInfos"))
    }

    @Test fun missingCountAndAppendWhileRunning() = runTest {
        val rig = Rig(this, listOf(tetris, zelda.copy(have = Scrape.HAS_ART), Scrape.homeGame("Doom.sh", tag = "PORTS")))
        assertEquals(1, rig.controller.missingCount(rig.host.games))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.controller.fetchMissing()
        testScheduler.runCurrent()
        rig.controller.fetchAll(replaceExisting = true)   // appends Zelda only; Tetris is already queued
        advanceUntilIdle()
        val end = rig.controller.state.value
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(tetris.gamePath, zelda.gamePath), end.items.map { it.gamePath })
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertEquals(1, rig.http.count("romnom=Tetris.gb"))
    }

    @Test fun appendAtRunEndChainsOntoOneJobThatPauses() = runTest {
        val rig = Rig(this, listOf(tetris, zelda.copy(have = Scrape.HAS_ART)))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        val fired = mutableSetOf<String>()
        rig.runner.hook = { p ->
            if (p == "Fetching art 0/1" && fired.add(p)) rig.controller.fetchAll(replaceExisting = true)   // Zelda joins while Tetris runs
            if (p == "Fetching art 1/2" && fired.add(p)) rig.controller.pause()                            // exactly as Tetris's run ends
        }
        rig.controller.fetchMissing()
        advanceUntilIdle()
        val paused = rig.controller.state.value
        assertEquals(JobPhase.PAUSED, paused.phase)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.WAITING), paused.items.map { it.status })
        // Zelda was in flight (pacing) in the chained run of the same job; the pause stopped it and no orphan job fetched it.
        assertEquals(0, rig.http.count("romnom=Zelda.gb"))
        assertTrue(rig.runner.calls.dropLast(1).all { it.first })   // never stopped while chaining
        assertEquals(false to null, rig.runner.calls.last())

        rig.controller.resume()
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), rig.controller.state.value.items.map { it.status })
        assertEquals(2, rig.runner.calls.count { !it.first })
    }

    @Test fun pauseRevertsTheInFlightItemAndResumeRedoesIt() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.controller.fetchMissing()
        testScheduler.runCurrent()
        assertEquals(ItemStatus.SEARCHING, rig.controller.state.value.items[0].status)
        rig.controller.pause()
        advanceUntilIdle()
        val paused = rig.controller.state.value
        assertEquals(JobPhase.PAUSED, paused.phase)
        assertEquals(listOf(ItemStatus.WAITING, ItemStatus.WAITING), paused.items.map { it.status })
        assertEquals(false to null, rig.runner.calls.last())
        assertEquals(JobPhase.PAUSED, ScrapeJobCodec.decode(rig.host.files.getValue(ArtworkController.JOB_FILE))!!.first.phase)

        rig.controller.resume()
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), rig.controller.state.value.items.map { it.status })
        assertEquals(2, rig.http.count("romnom=Tetris.gb"))
        assertEquals(1, rig.http.count("romnom=Zelda.gb"))
    }

    // Every skip is emitted and every tenth saved, the save suspending: the stop request runs while the job is between
    // emissions, and the job's next one meets the stop.
    @Test fun aPauseOrCancelDuringAFastSkipStretchStopsTheJob() = runTest(timeout = 10.seconds) {
        for (phase in listOf(JobPhase.PAUSED, JobPhase.CANCELLED)) {
            val games = (1..60).map { Scrape.homeGame("G$it.gb", have = Scrape.HAS_ART) }
            var now = 0L
            val rig = Rig(this, games, engineClock = { now += ScrapeEngine.EMIT_EVERY_MS; now })
            rig.host.yieldOnWrite = true
            rig.host.files[ArtworkController.JOB_FILE] = ScrapeJobCodec.encode(ScrapeState(JobPhase.PAUSED, games.map { Scrape.item(it) }), replace = false)
            rig.runner.hook = { if (it == "Fetching art 5/60") if (phase == JobPhase.PAUSED) rig.controller.pause() else rig.controller.cancel() }
            assertTrue(rig.controller.restorePending())
            rig.controller.resume()
            advanceUntilIdle()
            val s = rig.controller.state.value
            assertEquals(phase, s.phase)
            assertTrue(s.done in 5 until 60, "${s.done} done")
            assertEquals(List(s.done) { ItemStatus.SKIPPED } + List(60 - s.done) { ItemStatus.WAITING }, s.items.map { it.status })
            assertEquals(s.items, ScrapeJobCodec.decode(rig.host.files.getValue(ArtworkController.JOB_FILE))!!.first.items)
            assertEquals(false to null, rig.runner.calls.last())
            if (phase == JobPhase.PAUSED) {
                rig.runner.hook = {}
                rig.controller.resume()
                advanceUntilIdle()
                assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
                assertTrue(rig.controller.state.value.items.all { it.status == ItemStatus.SKIPPED })
            }
        }
    }

    // A host failure right after a stretch of skips that were not emitted yet: those games stay finished, not refetched.
    @Test fun aHostFailurePausesWithTheGamesFinishedSinceTheLastEmission() = runTest {
        val skipped = listOf(Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART), Scrape.homeGame("Zelda.gb", have = Scrape.HAS_ART))
        val rig = Rig(this, skipped + sonic)
        for (folder in listOf("boxart2d", "screenshot")) {
            rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "MD", ".media", folder))!!, "Sonic.png", OK_PNG.body!!)
        }
        rig.host.throwOnApply = { if (it == sonic.gamePath) IllegalStateException("index update failed") else null }
        val queued = (skipped + sonic).map { Scrape.item(it) }
        rig.host.files[ArtworkController.JOB_FILE] = ScrapeJobCodec.encode(ScrapeState(JobPhase.PAUSED, queued), replace = false)
        assertTrue(rig.controller.restorePending())
        rig.controller.resume()
        advanceUntilIdle()
        val s = rig.controller.state.value
        assertEquals(JobPhase.PAUSED, s.phase)
        assertEquals(ScrapeEngine.FAILED, s.pauseReason)
        assertEquals(listOf(ItemStatus.SKIPPED, ItemStatus.SKIPPED, ItemStatus.WAITING), s.items.map { it.status })
    }

    @Test fun cancelStopsTheJob() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.latency = 100
        rig.controller.fetchMissing()
        testScheduler.runCurrent()
        rig.controller.cancel()
        advanceUntilIdle()
        assertEquals(JobPhase.CANCELLED, rig.controller.state.value.phase)
        assertEquals(1, rig.http.calls.size)
        assertFalse(rig.controller.restorePending())
    }

    @Test fun appendWhilePausedMergesAndResumes() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        rig.host.files[ArtworkController.JOB_FILE] = ScrapeJobCodec.encode(ScrapeState(JobPhase.PAUSED, listOf(Scrape.item(tetris))), replace = false)
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        assertTrue(rig.controller.restorePending())
        rig.controller.fetchMissing()
        advanceUntilIdle()
        val end = rig.controller.state.value
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(tetris.gamePath, zelda.gamePath), end.items.map { it.gamePath })
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertEquals(1, rig.http.count("romnom=Tetris.gb"))
    }

    @Test fun restoredJobSkipsArtThatAppearedMeanwhile() = runTest {
        val rig = Rig(this, listOf(tetris.copy(have = Scrape.HAS_ART)))
        rig.host.files[ArtworkController.JOB_FILE] = ScrapeJobCodec.encode(ScrapeState(JobPhase.PAUSED, listOf(Scrape.item(tetris))), replace = false)
        assertTrue(rig.controller.restorePending())
        rig.controller.resume()
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertEquals(ItemStatus.SKIPPED, rig.controller.state.value.items.single().status)
        assertTrue(rig.http.calls.isEmpty())
    }

    @Test fun fetchGameRunsOneGameInTheCallersCoroutine() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        rig.http.on("romnom=Tetris.gb", Scrape.BOTH)
        rig.http.on("romnom=Zelda.gb", HttpResult(403, ScraperFixtures.DEV_LOGIN_REFUSED.encodeToByteArray()))
        rig.http.on("https://m/", OK_PNG)
        assertEquals(FetchResult(ItemStatus.DONE, "ScreenScraper"), rig.controller.fetchGame(tetris.gamePath))
        assertEquals(tetris.gamePath, rig.host.applied.single().first)
        assertEquals(FetchResult(ItemStatus.ERROR, ScreenScraperSource.BUILD_REFUSED), rig.controller.fetchGame(zelda.gamePath))
        assertEquals(ItemStatus.SKIPPED, rig.controller.fetchGame("/Roms/GB/Nope.gb").status)
    }

    @Test fun aFailedRunKeepsNoDuplicatesAndResumeScrapesEachGameOnce() = runTest {
        val rig = Rig(this, listOf(tetris, zelda.copy(have = Scrape.HAS_ART)))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.host.failApply = { it == tetris.gamePath }
        rig.controller.fetchMissing()
        testScheduler.runCurrent()
        rig.controller.fetchAll(replaceExisting = true)   // Zelda is pending when Tetris's index update throws
        advanceUntilIdle()
        val failed = rig.controller.state.value
        assertEquals(JobPhase.PAUSED, failed.phase)
        assertEquals(ScrapeEngine.FAILED, failed.pauseReason)   // an index update is not the library folder
        assertEquals(listOf(tetris.gamePath, zelda.gamePath), failed.items.map { it.gamePath })
        assertEquals(0, rig.http.count("romnom=Zelda.gb"))

        rig.host.failApply = { false }
        rig.controller.resume()
        advanceUntilIdle()
        val end = rig.controller.state.value
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(tetris.gamePath, zelda.gamePath), end.items.map { it.gamePath })
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertEquals(1, rig.http.count("romnom=Zelda.gb"))
        assertEquals(2, rig.http.count("romnom=Tetris.gb"))   // the failed run's, then the resumed one
    }

    @Test fun resumeWaitsForASingleGameFetchOfAQueuedGame() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        rig.host.files[ArtworkController.JOB_FILE] = ScrapeJobCodec.encode(ScrapeState(JobPhase.PAUSED, listOf(Scrape.item(tetris), Scrape.item(zelda))), replace = false)
        rig.http.on("romnom=", found("ss" to "https://m/s.png", "box-2D" to "https://m/b.png"))   // a box and a screenshot: has art
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        assertTrue(rig.controller.restorePending())
        var single: FetchResult? = null
        launch { single = rig.controller.fetchGame(tetris.gamePath) }
        testScheduler.runCurrent()   // Tetris's single fetch is in flight
        rig.controller.resume()
        advanceUntilIdle()
        assertEquals(FetchResult(ItemStatus.DONE, "ScreenScraper"), single)
        val end = rig.controller.state.value
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(ItemStatus.SKIPPED, ItemStatus.DONE), end.items.map { it.status })   // Tetris had its art by then
        assertEquals(1, rig.http.count("romnom=Tetris.gb"))
    }

    @Test fun fetchGameWaitsForTheJobInsteadOfFetchingTwice() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.controller.fetchMissing()
        testScheduler.runCurrent()
        var result: FetchResult? = null
        launch { result = rig.controller.fetchGame(zelda.gamePath) }
        advanceUntilIdle()
        assertEquals(FetchResult(ItemStatus.DONE, "ScreenScraper"), result)
        assertEquals(1, rig.http.count("romnom=Zelda.gb"))
    }

    @Test fun fetchGameDuringAJobLeavesTheJobsSourcesAsTheyAre() = runTest {
        val other = FakeSource("other")
        val rig = Rig(this, listOf(tetris, zelda.copy(have = Scrape.HAS_ART)), more = listOf(other))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.controller.fetchMissing()   // Tetris only
        testScheduler.runCurrent()
        assertEquals(1, other.starts)
        var single: FetchResult? = null
        launch { single = rig.controller.fetchGame(zelda.gamePath, replaceExisting = true) }   // not in the job
        advanceUntilIdle()
        assertEquals(ItemStatus.DONE, single?.status)
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertEquals(1, other.starts)   // the running job's per-run state was not reset
        // With no job live, a single fetch starts the sources as a run does.
        rig.controller.fetchGame(zelda.gamePath, replaceExisting = true)
        assertEquals(2, other.starts)
    }

    // The stopped job's run still holds the shared sources while it winds down: a single fetch then waits for it.
    @Test fun aSingleFetchDuringAStopStartsTheSourcesOnlyOnceTheStoppedRunEnded() = runTest {
        val other = FakeSource("other")
        val rig = Rig(this, listOf(tetris, zelda.copy(have = Scrape.HAS_ART)), more = listOf(other))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 100
        rig.controller.fetchMissing()   // Tetris only
        testScheduler.runCurrent()
        other.onEnd = { delay(50) }   // the stopped run's wind-down takes a while
        rig.controller.pause()
        var single: FetchResult? = null
        launch { single = rig.controller.fetchGame(zelda.gamePath, replaceExisting = true) }   // not in the job
        advanceUntilIdle()
        assertEquals(ItemStatus.DONE, single?.status)
        assertEquals(listOf("start", "end", "start", "end"), other.events)
        assertEquals(JobPhase.PAUSED, rig.controller.state.value.phase)
    }

    // The run returns with games it never emitted (skips inside the emit interval) just as a pause stops it: they stay
    // finished, whether the loop's own merge or the pause records them.
    @Test fun gamesFinishedAsTheRunEndsAtAPauseStayFinished() = runTest {
        val other = FakeSource("other")
        val games = listOf(tetris.copy(have = Scrape.HAS_ART), zelda.copy(have = Scrape.HAS_ART))
        val rig = Rig(this, games, more = listOf(other), engineClock = { 0L })   // no time passes: the skips are never emitted
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.host.files[ArtworkController.JOB_FILE] = ScrapeJobCodec.encode(ScrapeState(JobPhase.PAUSED, games.map { Scrape.item(it) }), replace = false)
        assertTrue(rig.controller.restorePending())
        other.onEnd = { rig.controller.pause(); yield() }   // the pause lands as the run's last step ends
        rig.controller.resume()
        advanceUntilIdle()
        val s = rig.controller.state.value
        assertEquals(JobPhase.PAUSED, s.phase)
        assertEquals(listOf(ItemStatus.SKIPPED, ItemStatus.SKIPPED), s.items.map { it.status })
        assertEquals(s.items, ScrapeJobCodec.decode(rig.host.files.getValue(ArtworkController.JOB_FILE))!!.first.items)
        assertEquals(false to null, rig.runner.calls.last())
    }

    @Test fun fetchGameKeepsHandPlacedArtUnlessReplaceIsOn() = runTest {
        val placed = "Home/Roms/GB/.media/boxart/Tetris.png"
        val rig = Rig(this, listOf(tetris.copy(have = ArtHave(box = true), art = ArtRefs(boxart = placed))))
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart"))!!, "Tetris.png", byteArrayOf(7))
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-3D" to "https://m/t-3d.png"))
        rig.http.on("https://m/", OK_PNG)
        assertEquals(ItemStatus.DONE, rig.controller.fetchGame(tetris.gamePath).status)
        assertContentEquals(byteArrayOf(7), rig.fs.bytesAt(placed))
        assertEquals(0, rig.http.count("t-3d"))
        assertFalse(rig.host.files.getValue(ArtworkController.LOG_FILE).contains("boxart"))
        // Replace existing art on: the box is fetched again (and is the app's from then on).
        assertEquals(ItemStatus.DONE, rig.controller.fetchGame(tetris.gamePath, replaceExisting = true).status)
        assertContentEquals(OK_PNG.body, rig.fs.bytesAt(placed))
    }

    @Test fun unassignedGamesAreNeverFetched() = runTest {
        val loose = Scrape.homeGame("Loose.gb", tag = "", dir = "Roms")
        val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.GRID, ArtSlot.HERO))   // knows every game, as SteamGridDB does
        val rig = Rig(this, listOf(loose, tetris), more = listOf(sg))
        assertFalse(rig.controller.fetchable(loose))
        assertFalse(rig.controller.canFetch("", "Loose.gb"))
        assertEquals(1, rig.controller.missingCount(listOf(loose, tetris)))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.controller.fetchAll(replaceExisting = true)
        advanceUntilIdle()
        assertEquals(listOf(tetris.gamePath), rig.controller.state.value.items.map { it.gamePath })
        assertTrue(sg.asked.none { it.first == "Loose.gb" })
    }

    @Test fun resetKeepsAnExtrasOwnMediaInTheIndex() = runTest {
        val own = "Extra/Genesis/.media/boxart/Sonic.png"
        val sonic = Scrape.extraGame("Sonic.md", "MD").copy(have = ArtHave(box = true), art = ArtRefs(boxart = own))
        val rig = Rig(this, listOf(sonic))
        rig.http.on("romnom=Sonic.md", found("ss" to "https://m/s-ss.png"))
        rig.http.on("https://m/", OK_PNG)
        rig.controller.fetchMissing()
        advanceUntilIdle()
        assertEquals(sonic.gamePath to ArtRefs(boxart = own, screenshot = "Home/Roms/.sources/x1/Genesis/.media/screenshot/Sonic.png"), rig.host.applied.last())
        assertEquals(ResetResult(1), rig.controller.resetArt(null))
        assertEquals(sonic.gamePath to ArtRefs(boxart = own), rig.host.applied.last())
    }

    @Test fun anErrorOutsideTheEngineEndsTheJobPausedAndStopsKeepAlive() = runTest {
        val rig = Rig(this, listOf(tetris, zelda))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.host.throwOnApply = { if (it == tetris.gamePath) Error("out of memory") else null }
        rig.controller.fetchMissing()
        advanceUntilIdle()
        val s = rig.controller.state.value
        assertEquals(JobPhase.PAUSED, s.phase)
        assertEquals(ScrapeEngine.FAILED, s.pauseReason)
        assertEquals(false to null, rig.runner.calls.last())
        rig.host.throwOnApply = { null }
        rig.controller.resume()   // the job was cleared: Resume starts it again
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
    }

    // Change art: the option list, previews and picks.

    private val shotPath = "Home/Roms/GB/.media/screenshot/Tetris.png"
    private val heroBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 9)

    /** SteamGridDB offering two grids and two heroes per game it is given. */
    private fun sgdb(vararg files: String) = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.GRID, ArtSlot.HERO)).apply {
        for (f in files) {
            val b = f.substringBefore('.')
            offer(f, ArtSlot.GRID, "https://g/$b-grid1"); offer(f, ArtSlot.GRID, "https://g/$b-grid2")
            offer(f, ArtSlot.HERO, "https://g/$b-hero1"); offer(f, ArtSlot.HERO, "https://g/$b-hero2")
        }
    }

    /** A source with one screenshot per game it is given. */
    private fun shots(vararg files: String) = FakeSource("other", slots = setOf(ArtSlot.SCREENSHOT)).apply {
        for (f in files) offer(f, ArtSlot.SCREENSHOT, "https://o/${f.substringBefore('.')}-ss")
    }

    private fun logWrites(rig: Rig) =
        rig.host.writes.count { it.first == ArtworkController.LOG_FILE } + rig.host.appends.count { it.first == ArtworkController.LOG_FILE }

    @Test fun bulk_fetch_saves_links_downloads_no_previews() = runTest {
        val sg = sgdb("Tetris.gb", "Zelda.gb")
        val rig = Rig(this, listOf(tetris, zelda), FakeCredentials(devId = ""), more = listOf(sg))
        rig.http.on("https://g/", OK_PNG)
        rig.controller.fetchConsole(setOf("GB"))
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        val store = ArtOptionStore(rig.host)
        for (g in listOf(tetris, zelda)) {
            val saved = assertNotNull(store.get(g.gamePath), g.gamePath)
            assertFalse(saved.wide)
            assertTrue(saved.options.any { it.slot == ArtSlot.GRID && it.url.endsWith("-grid2") }, "${saved.options}")
        }
        assertTrue(rig.cache.files.isEmpty())
        assertEquals(listOf("https://g/Tetris-grid1", "https://g/Tetris-hero1", "https://g/Zelda-grid1", "https://g/Zelda-hero1"), rig.http.calls)
    }

    @Test fun options_from_saved_list_make_no_lookup() = runTest {
        val sg = sgdb("Tetris.gb")
        val tg = FakeSource(SourceId.TGDB, slots = setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT))
        val rig = Rig(this, listOf(tetris), more = listOf(tg, sg))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.on("https://g/", OK_PNG)
        rig.controller.fetchGame(tetris.gamePath, wide = true)
        val lookups = rig.http.count("jeuInfos") to (sg.asked.size + tg.asked.size)
        assertTrue(lookups.first > 0)
        val result = rig.controller.artOptions(tetris.gamePath, replaceExisting = false)
        assertTrue(result.options.any { it.slot == ArtSlot.HERO }, "${result.options}")
        assertTrue(result.options.any { it.source == SourceId.SS && it.slot == ArtSlot.SCREENSHOT }, "${result.options}")
        assertEquals(lookups, rig.http.count("jeuInfos") to (sg.asked.size + tg.asked.size))
    }

    @Test fun narrow_list_triggers_one_wide_search() = runTest {
        val sg = sgdb("Tetris.gb")
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sg))
        rig.http.on("https://", OK_PNG)
        rig.controller.fetchConsole(setOf("GB"))
        advanceUntilIdle()
        assertFalse(assertNotNull(ArtOptionStore(rig.host).get(tetris.gamePath)).wide)
        val asked = sg.asked.size
        val first = rig.controller.artOptions(tetris.gamePath, replaceExisting = false)
        assertTrue(sg.asked.size > asked)
        assertTrue(first.options.any { it.slot == ArtSlot.HERO }, "${first.options}")
        val afterWide = sg.asked.size
        val second = rig.controller.artOptions(tetris.gamePath, replaceExisting = false)
        assertEquals(afterWide, sg.asked.size)
        assertEquals(first.options, second.options)
        // Search again runs it once more.
        rig.controller.artOptions(tetris.gamePath, replaceExisting = false, searchAgain = true)
        assertTrue(sg.asked.size > afterWide)
    }

    @Test fun a_search_reports_options_as_each_source_answers() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sgdb("Tetris.gb")))
        rig.http.on("https://", OK_PNG)
        val found = mutableListOf<List<ArtOption>>()
        val result = rig.controller.artOptions(tetris.gamePath, replaceExisting = false, searchAgain = true) { found += it }
        assertEquals(listOf(1, 5), found.map { it.size })   // the screenshot source's, then SteamGridDB's on top
        assertEquals(found.last().map { it.key }.toSet(), result.options.map { it.key }.toSet())
        assertTrue(assertNotNull(ArtOptionStore(rig.host).get(tetris.gamePath)).wide)   // saved
    }

    @Test fun a_pick_while_the_panels_fetch_runs_goes_through_at_once_and_wins_its_slot() = runTest {
        val two = FakeSource("other", slots = setOf(ArtSlot.SCREENSHOT)).apply {
            offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://o/Tetris-ss1"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://o/Tetris-ss2")
        }
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(two))
        val mine = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 5)
        rig.http.on("Tetris-ss2", HttpResult(200, mine))
        rig.http.on("https://", OK_PNG)
        rig.http.delays["Tetris-ss1"] = 300   // the fetch's own download is slow
        val found = mutableListOf<List<ArtOption>>()
        var fetched = false
        launch { rig.controller.fetchGame(tetris.gamePath, replaceExisting = true, wide = true) { found += it }; fetched = true }   // Replace: its write would replace the pick
        testScheduler.runCurrent()
        val ss2 = found.single().first { it.url.endsWith("ss2") }
        var picked: Boolean? = null
        launch { picked = rig.controller.pickOption(tetris.gamePath, ss2) }
        testScheduler.advanceTimeBy(100)
        assertEquals(true, picked)   // not held until the fetch ends
        assertFalse(fetched)
        advanceUntilIdle()
        assertTrue(fetched)
        assertContentEquals(mine, rig.fs.bytesAt(shotPath))   // the fetch's later write left the pick alone
        assertEquals(shotPath, rig.host.applied.last { it.first == tetris.gamePath }.second.screenshot)   // the index has it too
        assertEquals(ss2.key, listed(rig).currentKeys[ArtSlot.SCREENSHOT])
    }

    @Test fun fetch_art_cut_short_searches_once() = runTest {
        // SteamGridDB stops in the wide pass: the list is not marked wide, and the read right after the fetch asks no one.
        val sg = sgdb("Tetris.gb").apply { fail = { SourceStop("Key refused") } }
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sg))
        rig.http.on("https://", OK_PNG)
        rig.controller.fetchGame(tetris.gamePath, wide = true)
        assertFalse(assertNotNull(ArtOptionStore(rig.host).get(tetris.gamePath)).wide)
        val asked = sg.asked.size
        val result = rig.controller.artOptions(tetris.gamePath, replaceExisting = false, autoSearch = false)
        assertEquals(asked, sg.asked.size)
        assertTrue(result.options.any { it.url == "https://o/Tetris-ss" }, "${result.options}")
        // Change art later (autoSearch on): the cut-short list is searched again.
        rig.controller.artOptions(tetris.gamePath, replaceExisting = false)
        assertTrue(sg.asked.size > asked)
    }

    private suspend fun listed(rig: Rig): ArtOptionsResult = rig.controller.artOptions(tetris.gamePath, replaceExisting = false)

    @Test fun option_image_offline_keeps_option() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sgdb("Tetris.gb")))
        rig.http.on("Tetris-hero2", OK_PNG)   // ahead of the catch-all, so the test can change it
        rig.http.on("https://", OK_PNG)
        val hero = listed(rig).options.first { it.url == "https://g/Tetris-hero2" }
        rig.http.set("Tetris-hero2", HttpResult(-1, null))
        assertEquals(OptionImage(null, dropped = false), rig.controller.optionImage(tetris.gamePath, hero))
        assertEquals(2, rig.http.count("Tetris-hero2"))
        assertTrue(hero in listed(rig).options)
        assertTrue(rig.cache.files.isEmpty())
    }

    @Test fun option_image_404_drops_option() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sgdb("Tetris.gb")))
        rig.http.on("Tetris-hero2", OK_PNG)   // ahead of the catch-all, so the test can change it
        rig.http.on("https://", OK_PNG)
        val hero = listed(rig).options.first { it.url == "https://g/Tetris-hero2" }
        rig.http.set("Tetris-hero2", HttpResult(404, null))
        assertEquals(OptionImage(null, dropped = true), rig.controller.optionImage(tetris.gamePath, hero))
        assertFalse(listed(rig).options.any { it.key == hero.key })
        // A good one is downloaded once, then served from the cache.
        val grid = listed(rig).options.first { it.url == "https://g/Tetris-grid2" }
        assertEquals(ContentRef("cache:${tetris.gamePath}\n${grid.key}"), rig.controller.optionImage(tetris.gamePath, grid).ref)
        assertEquals(ContentRef("cache:${tetris.gamePath}\n${grid.key}"), rig.controller.optionImage(tetris.gamePath, grid).ref)
        assertEquals(1, rig.http.count("Tetris-grid2"))
    }

    @Test fun a_dead_link_of_the_pick_is_kept_not_dropped() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sgdb("Tetris.gb")))
        rig.http.on("Tetris-hero2", OK_PNG)   // ahead of the catch-all, so the test can change it
        rig.http.on("https://", OK_PNG)
        val hero = listed(rig).options.first { it.url == "https://g/Tetris-hero2" }
        assertTrue(rig.controller.pickOption(tetris.gamePath, hero))
        rig.cache.files.clear()
        rig.http.set("Tetris-hero2", HttpResult(404, null))
        assertEquals(OptionImage(null, dropped = false), rig.controller.optionImage(tetris.gamePath, hero))
        assertTrue(listed(rig).options.any { it.key == hero.key })
    }

    @Test fun a_dead_link_without_a_thumbnail_fails_the_pick_and_stays_listed() = runTest {
        val rig = Rig(this, listOf(tetris))
        val dead = ArtOption(ArtSlot.SCREENSHOT, "https://m/dead.png", SourceId.SS, assetId = "ss/us")
        ArtOptionStore(rig.host).record(tetris.gamePath, listOf(dead), wide = true)
        rig.http.on("https://m/dead", HttpResult(404, null))
        assertFalse(rig.controller.pickOption(tetris.gamePath, dead))
        assertEquals(listOf(dead), ArtOptionStore(rig.host).get(tetris.gamePath)?.options)
        assertNull(ArtOptionStore(rig.host).get(tetris.gamePath)?.picks?.get(ArtSlot.SCREENSHOT))
        assertTrue(rig.cache.files.isEmpty())
    }

    @Test fun ss_preview_signed_out_uses_dev_auth_only() = runTest {
        val media = "https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=DEV&devpassword=DEVPW&ssid=U&sspassword=P&media=ss"
        val rig = Rig(this, listOf(tetris))
        rig.http.on("romnom=", found("ss" to media, "box-2D" to "https://m/b.png"))
        rig.http.on("mediaJeu", OK_PNG)
        rig.http.on("https://m/", OK_PNG)
        val shot = listed(rig).options.first { it.source == SourceId.SS && it.slot == ArtSlot.SCREENSHOT }
        assertFalse("devid=" in shot.url || "ssid=" in shot.url || "password" in shot.url, shot.url)
        rig.http.calls.clear()
        assertNotNull(rig.controller.optionImage(tetris.gamePath, shot).ref)
        val url = rig.http.calls.single()
        assertTrue("devid=DEV" in url, url)
        assertFalse("ssid=" in url, url)
    }

    @Test fun pick_writes_marks_and_survives_bulk_replace() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sgdb("Tetris.gb")))
        rig.http.on("Tetris-hero2", HttpResult(200, heroBytes))
        rig.http.on("https://", OK_PNG)
        rig.controller.fetchConsole(setOf("GB"))
        advanceUntilIdle()
        assertContentEquals(OK_PNG.body, rig.fs.bytesAt(shotPath))
        val before = listed(rig)
        assertEquals(before.options.first { it.url == "https://o/Tetris-ss" }.key, before.currentKeys[ArtSlot.SCREENSHOT])   // the fetched screenshot's origin
        val hero = before.options.first { it.url == "https://g/Tetris-hero2" }
        assertTrue(rig.controller.pickOption(tetris.gamePath, hero))
        // Fitted as a hero (JPEG), the fetched PNG replaced.
        val heroPath = shotPath.replace(".png", ".jpg")
        assertContentEquals(heroBytes, rig.fs.bytesAt(heroPath))
        assertNull(rig.fs.bytesAt(shotPath))
        assertEquals(hero.key, listed(rig).currentKeys[ArtSlot.SCREENSHOT])
        assertEquals(hero.key, ArtOptionStore(rig.host).get(tetris.gamePath)?.picks?.get(ArtSlot.SCREENSHOT))
        assertEquals(mapOf(tetris.gamePath to setOf(ArtSlot.SCREENSHOT)), rig.controller.picks())   // what the library scan reads
        assertEquals(heroPath, rig.host.applied.last().second.screenshot)
        assertTrue(rig.host.applied.last().second.heroShot)
        rig.controller.fetchAll(replaceExisting = true)
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertContentEquals(heroBytes, rig.fs.bytesAt(heroPath))
        assertEquals(hero.key, listed(rig).currentKeys[ArtSlot.SCREENSHOT])
    }

    @Test fun pick_current_protects_it_without_a_write() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sgdb("Tetris.gb")))
        rig.http.on("https://o/", OK_PNG)
        rig.http.on("https://", HttpResult(200, heroBytes))
        val result = listed(rig)
        val current = result.options.first { it.key == result.currentKeys[ArtSlot.SCREENSHOT] }
        val writes = logWrites(rig)
        val calls = rig.http.calls.size
        val applied = rig.host.applied.size
        assertTrue(rig.controller.pickOption(tetris.gamePath, current))
        assertEquals(writes, logWrites(rig))
        assertEquals(calls, rig.http.calls.size)
        assertEquals(applied, rig.host.applied.size)
        assertEquals(current.key, ArtOptionStore(rig.host).get(tetris.gamePath)?.picks?.get(ArtSlot.SCREENSHOT))
        assertTrue(ArtOptionStore(rig.host).picked(tetris.gamePath, ArtSlot.SCREENSHOT))
        // Replace existing leaves the picked screenshot, though the source now gives another picture.
        rig.http.set("https://o/", HttpResult(200, heroBytes))
        rig.controller.fetchAll(replaceExisting = true)
        advanceUntilIdle()
        assertEquals(JobPhase.DONE, rig.controller.state.value.phase)
        assertContentEquals(OK_PNG.body, rig.fs.bytesAt(shotPath))
    }

    @Test fun a_failed_pick_write_restores_the_previous_pick() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sgdb("Tetris.gb")))
        rig.http.on("https://", OK_PNG)
        val options = listed(rig).options
        val hero1 = options.first { it.url == "https://g/Tetris-hero1" }
        val hero2 = options.first { it.url == "https://g/Tetris-hero2" }
        assertTrue(rig.controller.pickOption(tetris.gamePath, hero1))
        rig.cache.files["${tetris.gamePath}\n${hero2.key}"] = ByteArray(0)   // the fitter can't read it: the write fails
        assertFalse(rig.controller.pickOption(tetris.gamePath, hero2))
        assertEquals(hero1.key, ArtOptionStore(rig.host).get(tetris.gamePath)?.picks?.get(ArtSlot.SCREENSHOT))
        assertEquals(hero1.key, listed(rig).currentKeys[ArtSlot.SCREENSHOT])
    }

    @Test fun reset_forgets_options_pick_and_cache() = runTest {
        val rig = Rig(this, listOf(tetris, sonic), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb", "Sonic.md"), sgdb("Tetris.gb", "Sonic.md")))
        rig.http.on("https://", OK_PNG)
        val hero = listed(rig).options.first { it.url == "https://g/Tetris-hero2" }
        assertNotNull(rig.controller.optionImage(tetris.gamePath, hero).ref)
        assertTrue(rig.controller.pickOption(tetris.gamePath, hero))
        val sonicHero = rig.controller.artOptions(sonic.gamePath, replaceExisting = false).options.first { it.slot == ArtSlot.HERO }
        assertNotNull(rig.controller.optionImage(sonic.gamePath, sonicHero).ref)
        rig.controller.resetArt(setOf("GB"))
        assertNull(rig.fs.bytesAt(shotPath))
        assertNull(ArtOptionStore(rig.host).get(tetris.gamePath))
        assertFalse("${tetris.gamePath}\n${hero.key}" in rig.cache.files)
        assertTrue("${sonic.gamePath}\n${sonicHero.key}" in rig.cache.files)   // another console's cache stays
        assertNotNull(ArtOptionStore(rig.host).get(sonic.gamePath))
        rig.controller.resetArt(null)
        assertTrue(rig.cache.files.isEmpty())
        assertNull(ArtOptionStore(rig.host).get(sonic.gamePath))
    }

    @Test fun previews_and_picks_are_cached_per_game() = runTest {
        // ScreenScraper's asset id ("ss/us") is the same for every game: each game's preview is its own.
        val rig = Rig(this, listOf(tetris, zelda))
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png"))
        rig.http.on("romnom=Zelda.gb", found("ss" to "https://m/z-ss.png"))
        rig.http.on("https://m/t-ss", HttpResult(200, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1)))
        rig.http.on("https://m/z-ss", HttpResult(200, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 2)))
        // Zelda's screenshot is hand-placed: the fetch keeps it, so its ScreenScraper one is not the current one.
        val zeldaShot = "Home/Roms/GB/.media/screenshot/Zelda.png"
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "screenshot"))!!, "Zelda.png", byteArrayOf(7))
        val t = listed(rig).options.first { it.source == SourceId.SS && it.slot == ArtSlot.SCREENSHOT }
        assertNotNull(rig.controller.optionImage(tetris.gamePath, t).ref)
        val zOptions = rig.controller.artOptions(zelda.gamePath, replaceExisting = false)
        val z = zOptions.options.first { it.source == SourceId.SS && it.slot == ArtSlot.SCREENSHOT }
        assertEquals(t.key, z.key)   // the same key within each game
        assertNull(zOptions.currentKeys[ArtSlot.SCREENSHOT])
        val zRef = assertNotNull(rig.controller.optionImage(zelda.gamePath, z).ref)
        assertEquals(2, rig.cache.files.size)
        assertTrue(zRef != rig.controller.optionImage(tetris.gamePath, t).ref)
        assertTrue(rig.controller.pickOption(zelda.gamePath, z))
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 2), rig.fs.bytesAt(zeldaShot))
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1), rig.fs.bytesAt(shotPath))
    }

    @Test fun a_grid_and_hero_with_the_same_asset_id_are_two_options() = runTest {
        val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.GRID, ArtSlot.HERO)).apply {
            offer("Tetris.gb", ArtSlot.GRID, "https://g/grid", assetId = "77")
            offer("Tetris.gb", ArtSlot.HERO, "https://g/hero", assetId = "77")
        }
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(sg))
        rig.http.on("https://g/grid", OK_PNG)
        rig.http.on("https://g/hero", HttpResult(200, heroBytes))
        val options = listed(rig).options
        assertEquals(setOf(ArtSlot.GRID, ArtSlot.HERO), options.map { it.slot }.toSet(), "$options")
        assertEquals(2, options.map { it.key }.toSet().size)
        for (o in options) assertNotNull(rig.controller.optionImage(tetris.gamePath, o).ref)
        assertEquals(2, rig.cache.files.size)
    }

    @Test fun clear_cache_reports_bytes_and_keeps_links() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sgdb("Tetris.gb")))
        rig.http.on("https://", OK_PNG)
        val options = listed(rig).options
        val two = options.filter { it.slot == ArtSlot.HERO }.take(2)
        for (o in two) assertNotNull(rig.controller.optionImage(tetris.gamePath, o).ref)
        assertEquals(6L, rig.controller.optionCacheSize())
        assertEquals(6L, rig.controller.clearOptionCache())
        assertEquals(0L, rig.controller.optionCacheSize())
        assertEquals(options, listed(rig).options)
    }
    private val box2dPath = "Home/Roms/GB/.media/boxart2d/Tetris.jpg"
    private val gridBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 2)

    /** SteamGridDB offering two grids per game, each with a thumbnail (`https://t/...`) beside its full image (`https://u/...`). */
    private fun thumbGrids(vararg files: String) = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.GRID, ArtSlot.HERO)).apply {
        for (f in files) {
            val b = f.substringBefore('.')
            for (n in 1..2) offer(f, ArtSlot.GRID, "https://u/$b-grid$n", assetId = "g$n", thumb = "https://t/$b-grid$n")
            offer(f, ArtSlot.HERO, "https://g/$b-hero1")
        }
    }

    @Test fun previewUsesThumb() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(thumbGrids("Tetris.gb")))
        rig.http.on("https://", OK_PNG)
        val grid2 = listed(rig).options.single { it.assetId == "g2" }
        assertEquals("https://t/Tetris-grid2", grid2.thumb)
        rig.http.calls.clear(); rig.cache.files.clear()
        assertNotNull(rig.controller.optionImage(tetris.gamePath, grid2).ref)
        assertEquals(listOf("https://t/Tetris-grid2"), rig.http.calls)
        assertTrue(rig.controller.pickOption(tetris.gamePath, grid2))
        assertEquals(listOf("https://t/Tetris-grid2", "https://u/Tetris-grid2"), rig.http.calls)
        assertEquals(1, rig.cache.files.size)   // only the preview
    }

    @Test fun pickBox2dWritesBoxart2d() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), thumbGrids("Tetris.gb")))
        rig.http.on("https://u/Tetris-grid2", HttpResult(200, gridBytes))
        rig.http.on("https://", OK_PNG)
        rig.controller.fetchConsole(setOf("GB"))
        advanceUntilIdle()
        val before = listed(rig)
        val shotKey = assertNotNull(before.currentKeys[ArtSlot.SCREENSHOT])
        assertEquals(before.options.single { it.assetId == "g1" }.key, before.currentKeys[ArtSlot.BOX_2D])   // the fetched grid
        val shotBytes = rig.fs.bytesAt(shotPath)
        val grid2 = before.options.single { it.assetId == "g2" }
        assertTrue(rig.controller.pickOption(tetris.gamePath, grid2))
        assertContentEquals(gridBytes, rig.fs.bytesAt(box2dPath))
        assertContentEquals(shotBytes, rig.fs.bytesAt(shotPath))   // the screenshot is not touched
        val after = listed(rig)
        assertEquals(grid2.key, after.currentKeys[ArtSlot.BOX_2D])
        assertEquals(shotKey, after.currentKeys[ArtSlot.SCREENSHOT])
        assertEquals(box2dPath, rig.host.applied.last().second.box2d)
        assertEquals(mapOf(tetris.gamePath to setOf(ArtSlot.BOX_2D)), rig.controller.picks())
        // Replace existing leaves the picked box.
        rig.controller.fetchAll(replaceExisting = true)
        advanceUntilIdle()
        assertContentEquals(gridBytes, rig.fs.bytesAt(box2dPath))
    }

    private val mixPath = "Home/Roms/GB/.media/mix/Tetris.png"

    @Test fun pickingAMixWritesTheMixSlotAndProtectsIt() = runTest {
        val mixes = FakeSource(SourceId.SS, slots = setOf(ArtSlot.MIX)).apply { offer("Tetris.gb", ArtSlot.MIX, "https://mx/Tetris-mix") }
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), mixes), pixels = FakePixels())
        rig.http.on("https://", OK_PNG)
        rig.controller.fetchConsole(setOf("GB"))
        advanceUntilIdle()
        val mix = listed(rig).options.single { it.slot == ArtSlot.MIX }
        assertTrue(rig.controller.pickOption(tetris.gamePath, mix))
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt(mixPath))   // placed on the mix canvas
        assertEquals(setOf(ArtSlot.MIX), ArtOptionStore(rig.host).pickedSlots(tetris.gamePath))
        assertEquals(mix.key, listed(rig).currentKeys[ArtSlot.MIX])
        assertEquals(mixPath, rig.host.applied.last().second.mix)
        assertEquals(mapOf(tetris.gamePath to setOf(ArtSlot.MIX)), rig.controller.picks())
        rig.controller.fetchAll(replaceExisting = true)
        advanceUntilIdle()
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt(mixPath))
        assertEquals(mix.key, listed(rig).currentKeys[ArtSlot.MIX])
    }

    @Test fun deadFullLinkKeepsArt() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(thumbGrids("Tetris.gb")))
        rig.http.on("https://u/Tetris-grid2", OK_PNG)   // ahead of the catch-all, so the test can change it
        rig.http.on("https://", OK_PNG)
        val grid2 = listed(rig).options.single { it.assetId == "g2" }
        val box = rig.fs.bytesAt(box2dPath)
        rig.http.set("https://u/Tetris-grid2", HttpResult(404, null))
        assertNotNull(rig.controller.optionImage(tetris.gamePath, grid2).ref)   // the thumbnail answers
        assertFalse(rig.controller.pickOption(tetris.gamePath, grid2))
        assertContentEquals(box, rig.fs.bytesAt(box2dPath))
        assertTrue(rig.controller.picks().isEmpty())
        assertTrue(listed(rig).options.any { it.key == grid2.key })
    }

    @Test fun currentKeysFromLog() = runTest {
        val rig = Rig(this, listOf(tetris))
        rig.fs.writeBytes(rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart"))!!, "Tetris.png", OK_PNG.body!!)
        rig.host.files[ArtworkController.LOG_FILE] = logLines("Roms/GB/.media/boxart/Tetris.png" to ArtOrigin(SourceId.SS, "1", "a1"))
        val a1 = ArtOption(ArtSlot.BOX_3D, "https://m/b-a1.png", SourceId.SS, "1", "a1")
        val a2 = ArtOption(ArtSlot.BOX_3D, "https://m/b-a2.png", SourceId.SS, "1", "a2")
        val otherSlot = ArtOption(ArtSlot.BOX_2D, "https://m/b2-a1.png", SourceId.SS, "1", "a1")   // the same asset id, another slot
        ArtOptionStore(rig.host).record(tetris.gamePath, listOf(otherSlot, a2, a1), wide = true)
        val result = rig.controller.artOptions(tetris.gamePath, replaceExisting = false, autoSearch = false)
        assertEquals(mapOf(ArtSlot.BOX_3D to a1.key), result.currentKeys)
    }

    @Test fun resetForgetsPicksAllSlots() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb"), sgdb("Tetris.gb")))
        rig.http.on("https://", OK_PNG)
        val options = listed(rig).options
        assertTrue(rig.controller.pickOption(tetris.gamePath, options.first { it.url == "https://g/Tetris-hero2" }))
        assertTrue(rig.controller.pickOption(tetris.gamePath, options.first { it.url == "https://g/Tetris-grid2" }))
        assertEquals(mapOf(tetris.gamePath to setOf(ArtSlot.SCREENSHOT, ArtSlot.BOX_2D)), rig.controller.picks())
        rig.controller.resetArt(setOf("GB"))
        assertNull(rig.controller.picks()[tetris.gamePath])
    }
    private fun logLines(vararg entries: Pair<String, ArtOrigin?>) = entries.joinToString("") { (p, o) -> WrittenArtLog.line(p, o) }

    @Test fun heroShotsAreTheLoggedSteamGridDbScreenshots() = runTest {
        val rig = Rig(this, emptyList())
        val sgdb = ArtOrigin(SourceId.SGDB, "1", "2")
        rig.host.files[ArtworkController.LOG_FILE] = logLines(
            "Roms/GB/.media/screenshot/Tetris.jpg" to sgdb,
            "Roms/GB/.media/screenshot/Mario v1.1.png" to sgdb,                        // dots in the base name
            "Roms/GB/.media/screenshot/Zelda.png" to ArtOrigin(SourceId.SS, "9", "ss"),  // not SteamGridDB
            "Roms/GB/.media/screenshot/Kirby.png" to null,                             // unknown origin
            "Roms/GB/.media/boxart2d/Tetris.png" to sgdb,                              // a grid, not a screenshot
            "Roms/.sources/x1/GB/.media/screenshot/Sonic.jpg" to sgdb,
        )
        assertEquals(
            setOf("Roms/GB/.media/screenshot/Tetris.jpg", "Roms/GB/.media/screenshot/Mario v1.1.png", "Roms/.sources/x1/GB/.media/screenshot/Sonic.jpg"),
            rig.controller.heroShots(),
        )
    }

    @Test fun theScanFlagsHeroesFromTheControllersLog() = runTest {
        val gb = "Home/Roms/Game Boy (GB)"
        val src = FakeRomsSource(
            mapOf(
                "Home" to listOf("Roms"),
                "Home/Roms" to listOf("Game Boy (GB)"),
                gb to listOf("G.gb", "H.gb", "Mario v1.1.gb", ".media"),
                "$gb/.media" to listOf("screenshot", "hero"),
                "$gb/.media/screenshot" to listOf("G.png", "G.jpg", "Mario v1.1.png"),
                "$gb/.media/hero" to listOf("H.jpg"),
            ),
        )
        val rig = Rig(this, emptyList(), fs = src)
        val sgdb = ArtOrigin(SourceId.SGDB, "1", "2")
        rig.host.files[ArtworkController.LOG_FILE] = logLines(
            "Roms/Game Boy (GB)/.media/screenshot/G.jpg" to sgdb,           // under a hand-placed G.png, which is shown
            "Roms/Game Boy (GB)/.media/screenshot/Mario v1.1.png" to sgdb,
            "Roms/Game Boy (GB)/.media/hero/H.jpg" to sgdb,                 // moved by the scan, its log entry with it
        )
        val move = rig.controller.legacyArtMove(src)
        val home = ContentRef("Home")
        val scanner = LibraryScanner(
            src, Sidecars(src, MemorySidecarStore()) { home },
            heroShots = { rig.controller.heroShots() }, legacyMove = { move },
        )
        val index = scanner.scan(home, emptyList(), emptyMap())
        val games = index.systems.flatMap { it.games }.associateBy { it.file }
        assertEquals("$gb/.media/screenshot/G.png", games.getValue("G.gb").screenshot)
        assertFalse(games.getValue("G.gb").heroShot)   // as the engine reads the shown file
        assertTrue(games.getValue("Mario v1.1.gb").heroShot)
        assertEquals("$gb/.media/screenshot/H.jpg", games.getValue("H.gb").screenshot)
        assertTrue(games.getValue("H.gb").heroShot)
        // readArt agrees.
        val art = scanner.readArt(index)
        assertEquals(listOf(false, true, true), listOf("G.gb", "Mario v1.1.gb", "H.gb").map { art.getValue(Triple("home", "Roms/Game Boy (GB)", it)).heroShot })
    }

    @Test fun aHandPlacedHeroMovedKeepsHeroDrawingUntilAFetchReplacesIt() = runTest {
        val rig = Rig(this, listOf(tetris), FakeCredentials(devId = ""), more = listOf(shots("Tetris.gb")))
        rig.http.on("https://", OK_PNG)
        val media = rig.fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media"))!!
        rig.fs.writeBytes(rig.fs.ensurePath(media, listOf("hero"))!!, "Tetris.jpg", byteArrayOf(5))
        rig.fs.writeBytes(rig.fs.ensurePath(media, listOf("boxart"))!!, "Tetris.png", byteArrayOf(6))
        ArtLogStore(rig.host, ArtworkController.LOG_FILE).add(listOf("Roms/GB/.media/boxart/Tetris.png" to null))   // the app's box
        assertTrue(rig.controller.legacyArtMove(rig.fs).move(media, rig.fs.listChildren(media), listOf("Roms", "GB", ".media")))
        val moved = "Roms/GB/.media/screenshot/Tetris.jpg"
        assertContentEquals(byteArrayOf(5), rig.fs.bytesAt("Home/$moved"))
        assertTrue(moved in rig.controller.heroShots())   // the scan's hero set
        // Reset: never deleted (not logged), and the engine's re-read still draws it as a hero.
        rig.controller.resetArt(setOf("GB"))
        assertContentEquals(byteArrayOf(5), rig.fs.bytesAt("Home/$moved"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        val art = rig.host.applied.last { it.first == tetris.gamePath }.second
        assertEquals("Home/$moved", art.screenshot)
        assertTrue(art.heroShot)
        // A fetch under Replace writes a real screenshot over it: no longer a hero.
        rig.controller.fetchGame(tetris.gamePath, replaceExisting = true)
        assertNull(rig.fs.bytesAt("Home/$moved"))
        assertFalse(moved in rig.controller.heroShots())
        assertFalse(rig.host.applied.last { it.first == tetris.gamePath }.second.heroShot)
    }

    /** A jeuInfos answer (game id 42) with a synopsis and the given media. */
    private fun withInfo(vararg media: Pair<String, String>) = Scrape.json(
        """{"response":{"jeu":{"id":"42","synopsis":[{"langue":"en","text":"Blocks fall."}],""" +
            """"medias":[${media.joinToString(",") { (t, u) -> """{"type":"$t","region":"us","url":"$u"}""" }}]}}}""",
    )

    @Test fun a_browse_fetch_downloads_only_the_missing_slots_and_the_info_from_one_lookup_quietly() = runTest {
        val boxOnly = Scrape.homeGame("Tetris.gb", have = ArtHave(box = true))
        val rig = Rig(this, listOf(boxOnly), creds = FakeCredentials().apply { user = "me"; password = "pw" })
        rig.http.on("romnom=Tetris.gb", withInfo("ss" to "https://m/s.png", "box-2D" to "https://m/b.png"))
        rig.http.on("https://m/", OK_PNG)
        val before = rig.controller.state.value
        assertTrue(rig.controller.wantsArt(boxOnly))
        assertFalse(rig.controller.wantsArt(Scrape.homeGame("Tetris.gb", have = Scrape.HAS_ART)))
        assertEquals(true, rig.controller.fetchArtQuietly(boxOnly))
        assertEquals(1, rig.http.count("jeuInfos"))
        assertEquals(0, rig.http.count("ssuserInfos"))   // no job start: no quota read beside the lookup
        assertEquals(1, rig.http.count("https://m/s.png"))
        assertEquals(0, rig.http.count("https://m/b.png"))   // the box it has is never replaced
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertEquals("Blocks fall.", rig.controller.info!!.read(boxOnly)?.synopsis)
        assertEquals(boxOnly.gamePath, rig.host.applied.single().first)
        assertEquals(before, rig.controller.state.value)   // no job, no progress
        assertTrue(rig.runner.calls.isEmpty())
        assertNull(rig.host.files[ArtworkController.JOB_FILE])
        assertFalse(rig.controller.busy)
    }

    @Test fun a_browse_fetch_reports_screenscrapers_stops_and_no_connection_as_the_info_lookup_does() = runTest {
        for ((answer, note) in listOf(HttpResult(430, null) to ScreenScraperSource.DAILY_LIMIT, HttpResult(503, null) to ScreenScraperSource.UNAVAILABLE)) {
            val rig = Rig(this, listOf(tetris))
            rig.http.on("romnom=Tetris.gb", answer)
            val e = assertFailsWith<SourceStop> { rig.controller.fetchArtQuietly(tetris) }
            assertEquals(note, e.note)
            assertEquals(ScrapeState(), rig.controller.state.value)
        }
        val offline = Rig(this, listOf(tetris))
        offline.http.on("romnom=Tetris.gb", HttpResult(-1, null))
        assertEquals(ScrapeEngine.NO_CONNECTION, assertFailsWith<JobPause> { offline.controller.fetchArtQuietly(tetris) }.reason)
    }

    @Test fun a_browse_fetch_is_not_made_for_a_game_no_source_fetches_or_while_a_job_runs() = runTest {
        val loose = Scrape.homeGame("Loose.gb", tag = "", dir = "Roms")
        val rig = Rig(this, listOf(loose, tetris, zelda))
        assertFalse(rig.controller.wantsArt(loose))
        assertNull(rig.controller.fetchArtQuietly(loose))
        rig.http.on("romnom=", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        rig.http.latency = 1_000
        rig.controller.fetchMissing()
        advanceTimeBy(500)
        assertTrue(rig.controller.busy)
        assertNull(rig.controller.fetchArtQuietly(zelda))
        advanceUntilIdle()
        assertEquals(1, rig.http.count("romnom=Zelda.gb"))   // the job's own
        assertEquals(0, rig.http.count("romnom=Loose.gb"))
    }
}
