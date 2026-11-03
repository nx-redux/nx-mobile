package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.ensurePath
import com.nxredux.mobile.scraper.Scrape.OK_PNG
import com.nxredux.mobile.scraper.Scrape.found
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrapeEngineTest {
    private class Rig(
        scope: TestScope, val fs: FakeRomsSource = Scrape.homeTree(), val hasher: FakeHasher = FakeHasher(), more: List<ArtSource> = emptyList(),
        logged: MutableSet<String> = mutableSetOf(), picked: Boolean = false,
        pickedBy: suspend (String) -> Boolean = { picked },
    ) {
        val options = mutableListOf<Triple<String, List<ArtOption>, Boolean>>()
        val http = FakeHttp().apply { clock = { scope.testScheduler.currentTime } }
        var auth = ScraperAuth("DEV", "DEVPW")
        val arts = mutableListOf<Pair<String, ArtRefs>>()
        val written = mutableListOf<Pair<String, ArtOrigin?>>()
        val fitter = FakeFitter()
        val engine = ssEngine(
            http, fitter, hasher, fs, Scrape.home, { auth }, { scope.testScheduler.currentTime }, { delay(it) },
            onArt = { p, a -> arts += p to a }, onWritten = { written += it }, more = more, logged = FakeLoggedArt(logged),
            onOptions = { p, o, w -> options += Triple(p, o, w) }, picked = pickedBy,
        )

        suspend fun run(games: List<LibraryGameRef>, start: ScrapeState = ScrapeState(items = games.map { Scrape.item(it) }), replace: Boolean = false, wide: Boolean = false) =
            engine.run(start, { p -> games.firstOrNull { it.gamePath == p } }, replace, wide = wide) {}
    }

    private val tetris = Scrape.homeGame("Tetris.gb")
    private val zelda = Scrape.homeGame("Zelda.gb")

    @Test fun pacesApiCallsButNotImages() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png"))
        rig.http.on("romnom=Zelda.gb", found("ss" to "https://m/z-ss.png"))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris, zelda))
        assertEquals(JobPhase.DONE, end.phase)
        val calls = rig.http.calls
        val first = calls.indexOfFirst { it.contains("romnom=Tetris.gb") }
        val second = calls.indexOfFirst { it.contains("romnom=Zelda.gb") }
        val tImage = calls.indexOfFirst { it.contains("t-ss") }
        val zImage = calls.indexOfFirst { it.contains("z-ss") }
        assertTrue(rig.http.times[second] >= rig.http.times[first] + ScreenScraperSource.RATE_MS)
        assertEquals(rig.http.times[first], rig.http.times[tImage])     // images follow at once
        assertEquals(rig.http.times[second], rig.http.times[zImage])
    }

    @Test fun records_every_option_slot_hit_it_sees() = runTest {
        val sgdb = FakeSource("sgdb", setOf(ArtSlot.GRID, ArtSlot.HERO))
        sgdb.offer("Tetris.gb", ArtSlot.GRID, "https://g/1.png"); sgdb.offer("Tetris.gb", ArtSlot.GRID, "https://g/2.png")
        sgdb.offer("Tetris.gb", ArtSlot.HERO, "https://g/h.png")
        val rig = Rig(this, more = listOf(sgdb))
        val signed = "https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=D&devpassword=P&ssid=U&sspassword=W&jeuid=42"
        rig.http.on("romnom=Tetris.gb", Scrape.json(
            """{"response":{"jeu":{"id":"42","medias":[{"type":"ss","region":"us","url":"$signed&media=ss(us)"},{"type":"ss","region":"eu","url":"$signed&media=ss(eu)"},{"type":"box-2D","region":"us","url":"https://m/b.png"}]}}}""",
        ))
        rig.run(listOf(tetris))   // nothing downloads (404): every source is asked
        val (path, options, wide) = rig.options.single()
        assertEquals(tetris.gamePath, path)
        assertEquals(false, wide)
        assertEquals(listOf(ArtSlot.SCREENSHOT, ArtSlot.SCREENSHOT, ArtSlot.GRID, ArtSlot.GRID, ArtSlot.HERO), options.map { it.slot })
        val shots = options.filter { it.slot == ArtSlot.SCREENSHOT }
        assertTrue(shots.all { "devid" !in it.url && "devpassword" !in it.url && "ssid" !in it.url && "sspassword" !in it.url && "jeuid=42" in it.url }, shots.toString())
        assertEquals(listOf(SourceId.SS, SourceId.SS, "sgdb", "sgdb", "sgdb"), options.map { it.source })
    }

    @Test fun picked_screenshot_survives_replace() = runTest {
        val sgdb = FakeSource("sgdb", setOf(ArtSlot.GRID, ArtSlot.HERO))
        sgdb.offer("Tetris.gb", ArtSlot.HERO, "https://m/h.png")
        val rig = Rig(this, more = listOf(sgdb), picked = true)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-3D" to "https://m/t-3d.png"))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), replace = true)
        assertEquals(ItemStatus.DONE, end.items.single().status)
        assertTrue(rig.written.none { it.first.contains("screenshot") || it.first.contains("hero") }, rig.written.toString())
        assertEquals(1, rig.http.count("t-3d"))   // the box is still fetched again
        assertEquals(0, rig.http.count("t-ss"))
        assertTrue(rig.options.all { o -> o.second.none { it.slot == ArtSlot.SCREENSHOT || it.slot == ArtSlot.HERO } })
    }

    @Test fun pick_made_mid_run_is_not_overwritten() = runTest {
        var calls = 0
        val rig = Rig(this, pickedBy = { calls++ > 0 })   // the user picks after the game's first check
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-3D" to "https://m/t-3d.png"))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), replace = true)
        assertTrue(calls > 1)
        assertTrue(rig.written.none { it.first.contains("screenshot") }, rig.written.toString())
        assertEquals(0, rig.http.count("t-ss"))
        assertEquals(1, rig.http.count("t-3d"))
        assertEquals(ItemStatus.DONE, end.items.single().status)
    }

    @Test fun no_options_reported_when_nothing_asked() = runTest {
        val rig = Rig(this)
        val end = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)))
        assertEquals(ItemStatus.SKIPPED, end.items.single().status)
        assertEquals(ScrapeEngine.HAS_ART, end.items.single().reason)
        assertTrue(rig.options.isEmpty())
    }

    @Test fun wide_asks_every_source_for_option_slots_without_writing() = runTest {
        val tgdb = FakeSource("tgdb", setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT, ArtSlot.GRID))
        tgdb.offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://t/s.png"); tgdb.offer("Tetris.gb", ArtSlot.GRID, "https://t/g.png")
        val sgdb = FakeSource("sgdb", setOf(ArtSlot.GRID, ArtSlot.HERO))
        sgdb.offer("Tetris.gb", ArtSlot.HERO, "https://g/h.png")
        val rig = Rig(this, more = listOf(tgdb, sgdb))
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-3D" to "https://m/t-3d.png"))
        val end = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), wide = true)
        val item = end.items.single()
        assertTrue(item.status != ItemStatus.SKIPPED || item.reason != ScrapeEngine.HAS_ART, item.toString())
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.SCREENSHOT, ArtSlot.GRID)), tgdb.asked)
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.GRID, ArtSlot.HERO)), sgdb.asked)
        assertTrue(rig.written.isEmpty() && rig.arts.isEmpty() && rig.http.count("m/t-") == 0, rig.written.toString())
        val (_, options, wide) = rig.options.single()
        assertTrue(wide)
        assertEquals(setOf(SourceId.SS, "tgdb", "sgdb"), options.map { it.source }.toSet())
        assertEquals(4, options.size)
    }

    @Test fun wide_does_not_reask_a_slot_already_asked() = runTest {
        val src = FakeSource("tgdb", setOf(ArtSlot.SCREENSHOT))
        val rig = Rig(this, more = listOf(src))
        rig.run(listOf(tetris.copy(have = ArtHave(box = true))), wide = true)
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.SCREENSHOT)), src.asked)
    }

    @Test fun wide_source_stop_keeps_status() = runTest {
        val sgdb = FakeSource("sgdb", setOf(ArtSlot.GRID, ArtSlot.HERO))
        sgdb.fail = { SourceStop("Key refused") }
        val rig = Rig(this, more = listOf(sgdb))
        val end = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), wide = true)
        assertEquals(ItemStatus.DONE, end.items.single().status)
        assertEquals("Key refused", end.notes["sgdb"])
    }

    @Test fun wide_source_stop_does_not_mark_wide() = runTest {
        val sgdb = FakeSource("sgdb", setOf(ArtSlot.GRID, ArtSlot.HERO))
        sgdb.fail = { SourceStop("Key refused") }
        val tgdb = FakeSource("tgdb", setOf(ArtSlot.SCREENSHOT))
        tgdb.offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://t/s.png")
        val rig = Rig(this, more = listOf(tgdb, sgdb))
        rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), wide = true)
        val (_, options, wide) = rig.options.single()
        assertEquals(false, wide)
        assertEquals(listOf("tgdb"), options.map { it.source })
    }

    @Test fun wide_pause_does_not_mark_wide() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", HttpResult(-1, null))
        val end = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), wide = true)
        assertEquals(JobPhase.PAUSED, end.phase)
        assertEquals(false, rig.options.single().third)
    }

    @Test fun wide_with_no_hits_reports_empty() = runTest {
        val rig = Rig(this)
        rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), wide = true)
        assertEquals(Triple(tetris.gamePath, emptyList<ArtOption>(), true), rig.options.single())
    }

    @Test fun write_pick_replaces_screenshot_and_logs_origin() = runTest {
        val fs = Scrape.homeTree()
        val shot = fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "screenshot"))!!
        fs.writeBytes(shot, "Tetris.jpg", byteArrayOf(7))
        val rig = Rig(this, fs = fs)
        val origin = ArtOrigin("steamgriddb", "9", "77")
        val refs = rig.engine.writePick(tetris, OK_PNG.body!!, origin)
        assertContentEquals(OK_PNG.body, fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertNull(fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.jpg"))
        assertEquals(listOf<Pair<String, ArtOrigin?>>("Roms/GB/.media/screenshot/Tetris.png" to origin), rig.written)
        assertEquals("Home/Roms/GB/.media/screenshot/Tetris.png", refs?.screenshot)
    }

    @Test fun write_pick_of_garbage_is_null() = runTest {
        val rig = Rig(this)
        rig.fitter.undecodable = { true }
        assertNull(rig.engine.writePick(tetris, byteArrayOf(1, 2), ArtOrigin("x")))
        assertTrue(rig.written.isEmpty())
    }

    @Test fun index_art_prefers_picked_mirror_screenshot() {
        val own = "Extra/Genesis/.media/screenshot/Sonic.png"
        val mirror = "Home/Roms/.sources/x1/Genesis/.media/screenshot/Sonic.png"
        val sonic = Scrape.extraGame("Sonic.md", "MD").copy(art = ArtRefs(screenshot = own))
        val after = ArtRefs(screenshot = mirror)
        assertEquals(own, ScrapeEngine.indexArt(sonic, ArtRefs(), after).screenshot)
        assertEquals(mirror, ScrapeEngine.indexArt(sonic, ArtRefs(), after, picked = setOf(ArtSlot.SCREENSHOT)).screenshot)
        // The hero flag follows the screenshot chosen.
        val heroAfter = after.copy(heroShot = true)
        assertEquals(false, ScrapeEngine.indexArt(sonic, ArtRefs(), heroAfter).heroShot)
        assertEquals(true, ScrapeEngine.indexArt(sonic, ArtRefs(), heroAfter, picked = setOf(ArtSlot.SCREENSHOT)).heroShot)
    }

    @Test fun tryTwoOnlyAfterTryOneFails() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", Scrape.json(ScraperFixtures.GARBAGE), Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris))
        assertEquals(ItemStatus.DONE, end.items.single().status)
        val lookups = rig.http.calls.filter { it.contains("jeuInfos") }
        assertEquals(2, lookups.size)
        assertTrue(!lookups[0].contains("&crc="))
        assertTrue(lookups[1].contains("&crc=00001234&romtaille=100"))
    }

    @Test fun bigRomSkipsTryTwo() = runTest {
        val rig = Rig(this, hasher = FakeHasher(null))
        rig.http.on("romnom=Tetris.gb", Scrape.json(ScraperFixtures.GARBAGE))
        val end = rig.run(listOf(tetris))
        assertEquals(ItemStatus.NOT_FOUND, end.items.single().status)
        assertEquals(1, rig.http.count("jeuInfos"))
    }

    @Test fun sheetsAndContainersAreNeverHashed() = runTest {
        for (file in listOf("Game.m3u", "Game.cue", "Game.gdi", "Game.ccd", "Game.CHD")) {
            val rig = Rig(this)
            rig.http.on("romnom=", Scrape.json(ScraperFixtures.GARBAGE))
            assertEquals(ItemStatus.NOT_FOUND, rig.run(listOf(Scrape.homeGame(file, tag = "MD"))).items.single().status, file)
            assertEquals(1, rig.http.count("jeuInfos"), file)   // no try 2: a sheet's CRC names no game
            assertTrue(rig.hasher.asked.isEmpty(), file)
        }
    }

    // A console game's zip: try 2 sends the ROM inside it (a zip's own CRC names no game); a 7z (not read) skips try 2.
    @Test fun aConsoleZipIsHashedInside() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=zz1.zip", Scrape.json(ScraperFixtures.GARBAGE), Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        assertEquals(ItemStatus.DONE, rig.run(listOf(Scrape.homeGame("zz1.zip"))).items.single().status)
        assertTrue(rig.http.calls.filter { it.contains("jeuInfos") }[1].contains("&crc=00005678&romtaille=200"))
        assertTrue(rig.hasher.asked.isEmpty())
        assertEquals(1, rig.hasher.askedInZip.size)
    }

    @Test fun aConsole7zSkipsTryTwo() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=", Scrape.json(ScraperFixtures.GARBAGE))
        assertEquals(ItemStatus.NOT_FOUND, rig.run(listOf(Scrape.homeGame("zz1.7z"))).items.single().status)
        assertEquals(1, rig.http.count("jeuInfos"))
        assertTrue(rig.hasher.asked.isEmpty() && rig.hasher.askedInZip.isEmpty())
    }

    // An arcade set's zip is the game itself (several ROMs, and possibly a BIOS): hashed whole, as before.
    @Test fun anArcadeZipIsHashedWhole() = runTest {
        for ((file, tag) in listOf("mslug.zip" to "FBN", "mslug6.zip" to "DC")) {
            val rig = Rig(this)
            rig.http.on("romnom=", Scrape.json(ScraperFixtures.GARBAGE))
            rig.run(listOf(Scrape.homeGame(file, tag = tag)))
            assertTrue(rig.http.calls.filter { it.contains("jeuInfos") }[1].contains("&crc=00001234&romtaille=100"), file)
            assertTrue(rig.hasher.askedInZip.isEmpty(), file)
        }
    }

    @Test fun writesTypedFilesOnly() = runTest {
        val rig = Rig(this)
        val sonic = Scrape.extraGame("Sonic.md", "MD")
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png", "wheel" to "https://m/t-wheel.png"))
        rig.http.on("romnom=Sonic.md", found("ss" to "https://m/s-ss.png", "box-3D" to "https://m/s-3d.png", "box-2D" to "https://m/s-box.png"))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris, sonic))
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        val fs = rig.fs
        assertNull(fs.bytesAt("Home/Roms/GB/.media/Tetris.png"))   // no mix
        assertContentEquals(OK_PNG.body, fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertContentEquals(OK_PNG.body, fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
        assertNull(fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        assertNull(fs.bytesAt("Home/Roms/.sources/x1/Genesis/.media/Sonic.png"))
        assertNotNull(fs.bytesAt("Home/Roms/.sources/x1/Genesis/.media/screenshot/Sonic.png"))
        assertNotNull(fs.bytesAt("Home/Roms/.sources/x1/Genesis/.media/boxart/Sonic.png"))   // 3D first
        assertNull(fs.bytesAt("Home/Roms/.sources/x1/Genesis/.media/boxart2d/Sonic.png"))
        assertEquals(0, rig.http.count("s-box") + rig.http.count("t-wheel"))
        assertEquals(
            "/Roms/GB/Tetris.gb" to ArtRefs(box2d = "Home/Roms/GB/.media/boxart2d/Tetris.png", screenshot = "Home/Roms/GB/.media/screenshot/Tetris.png"),
            rig.arts[0],
        )
        assertEquals(sonic.gamePath to ArtRefs(boxart = "Home/Roms/.sources/x1/Genesis/.media/boxart/Sonic.png", screenshot = "Home/Roms/.sources/x1/Genesis/.media/screenshot/Sonic.png"), rig.arts[1])
        assertEquals(
            setOf("Roms/GB/.media/boxart2d/Tetris.png", "Roms/GB/.media/screenshot/Tetris.png", "Roms/.sources/x1/Genesis/.media/boxart/Sonic.png", "Roms/.sources/x1/Genesis/.media/screenshot/Sonic.png"),
            rig.written.map { it.first }.toSet(),
        )
    }

    @Test fun logsOriginPerFile() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", Scrape.json(
            """{"response":{"jeu":{"id":"42","medias":[{"type":"ss","region":"jp","url":"https://m/ss.png"},{"type":"box-3D","url":"https://m/3d.png"}]}}}""",
        ))
        rig.http.on("https://m/", OK_PNG)
        rig.run(listOf(tetris))
        assertEquals(
            listOf<Pair<String, ArtOrigin?>>(
                "Roms/GB/.media/boxart/Tetris.png" to ArtOrigin("screenscraper", "42", "box-3D"),
                "Roms/GB/.media/screenshot/Tetris.png" to ArtOrigin("screenscraper", "42", "ss/jp"),
            ),
            rig.written,
        )
    }

    @Test fun jpegWrittenAsJpgButThe2dBoxAlwaysPng() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.jpg", "box-2D" to "https://m/t-box.jpg"))
        rig.http.on("https://m/", Scrape.OK_JPEG)
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))   // NX Redux's 2D box: full-colour PNG
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.jpg"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.jpg"))
        assertEquals(ArtRefs(box2d = "Home/Roms/GB/.media/boxart2d/Tetris.png", screenshot = "Home/Roms/GB/.media/screenshot/Tetris.jpg"), rig.arts.single().second)
        assertEquals(setOf("Roms/GB/.media/boxart2d/Tetris.png", "Roms/GB/.media/screenshot/Tetris.jpg"), rig.written.map { it.first }.toSet())
    }

    @Test fun eachDownloadIsFittedToItsSlotCap() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-3D" to "https://m/t-3d.png"))
        rig.http.on("https://m/", Scrape.OK_JPEG)
        rig.run(listOf(tetris))
        assertEquals(listOf(ArtCaps.of(ArtSlot.BOX_3D), ArtCaps.of(ArtSlot.SCREENSHOT)), rig.fitter.caps)
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))   // a 3D box is always PNG
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.jpg"))
    }

    @Test fun fetchesOnlyMissingKinds() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=", found("ss" to "https://m/ss.png", "box-3D" to "https://m/3d.png"))
        rig.http.on("https://m/", OK_PNG)
        val hasBox = tetris.copy(have = ArtHave(box = true))
        val hasShot = zelda.copy(have = ArtHave(screenshot = true))
        val end = rig.run(listOf(hasBox, hasShot))
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertEquals(listOf("https://m/ss.png", "https://m/3d.png"), rig.http.calls.filter { it.startsWith("https://m/") })
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Zelda.png"))
    }

    @Test fun keepsHandPlacedBox() = runTest {
        // A box placed after the last scan (the index does not know it yet): never overwritten without Replace.
        val fs = Scrape.homeTree()
        val box = fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart"))!!
        fs.writeBytes(box, "Tetris.png", byteArrayOf(7))
        val rig = Rig(this, fs = fs)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-3D" to "https://m/t-3d.png"))
        rig.http.on("https://m/", OK_PNG)
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        assertContentEquals(byteArrayOf(7), fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        assertEquals(0, rig.http.count("t-3d"))
        assertEquals(listOf("Roms/GB/.media/screenshot/Tetris.png"), rig.written.map { it.first })   // the hand-placed box is not logged
        assertEquals(ArtRefs(boxart = "Home/Roms/GB/.media/boxart/Tetris.png", screenshot = "Home/Roms/GB/.media/screenshot/Tetris.png"), rig.arts.single().second)
    }

    @Test fun replaceOverwrites() = runTest {
        val fs = Scrape.homeTree()
        val box = fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart"))!!
        fs.writeBytes(box, "Tetris.png", byteArrayOf(7))
        val rig = Rig(this, fs = fs)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-3D" to "https://m/t-3d.png"))
        rig.http.on("https://m/", OK_PNG)
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), replace = true).items.single().status)
        assertContentEquals(OK_PNG.body, fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
    }

    @Test fun replaceRemovesOtherExtension() = runTest {
        val fs = Scrape.homeTree()
        val shots = fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "screenshot"))!!
        fs.writeBytes(shots, "Tetris.png", byteArrayOf(7))
        fs.writeBytes(shots, "Tetris.jpeg", byteArrayOf(8))
        val rig = Rig(this, fs = fs)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.jpg"))
        rig.http.on("https://m/", Scrape.OK_JPEG)
        rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), replace = true)
        assertContentEquals(Scrape.OK_JPEG.body, fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.jpg"))
        assertNull(fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertNull(fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.jpeg"))
        assertEquals(listOf("Tetris.jpg"), fs.listChildren(shots).map { it.name })
    }

    @Test fun withoutReplaceTheOtherExtensionIsNeverWritten() = runTest {
        // A hand-placed box2d/Tetris.jpg the index does not know yet: the game has a box, so no box is fetched at all.
        val fs = Scrape.homeTree()
        val box = fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart2d"))!!
        fs.writeBytes(box, "Tetris.jpg", byteArrayOf(7))
        val rig = Rig(this, fs = fs)
        rig.http.on("romnom=Tetris.gb", found("box-3D" to "https://m/t-3d.png", "box-2D" to "https://m/t-box.png"))
        rig.http.on("https://m/", OK_PNG)
        val item = rig.run(listOf(tetris)).items.single()
        assertEquals(ItemStatus.NOT_FOUND, item.status)   // only a screenshot was wanted, and there is none
        assertEquals(listOf("Tetris.jpg"), fs.listChildren(box).map { it.name })
        assertNull(fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        assertTrue(rig.written.isEmpty())
        assertTrue(rig.http.calls.none { it.startsWith("https://m/") })
    }

    @Test fun artOnDiskTheIndexMissedIsSkippedAndReported() = runTest {
        val fs = Scrape.homeTree()
        fs.writeBytes(fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart2d"))!!, "Tetris.jpg", byteArrayOf(7))
        fs.writeBytes(fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "screenshot"))!!, "Tetris.png", byteArrayOf(8))
        val rig = Rig(this, fs = fs)
        val item = rig.run(listOf(tetris)).items.single()
        assertEquals(ItemStatus.SKIPPED to "Has art", item.status to item.reason)
        assertTrue(rig.http.calls.isEmpty())
        assertEquals(tetris.gamePath to ArtRefs(box2d = "Home/Roms/GB/.media/boxart2d/Tetris.jpg", screenshot = "Home/Roms/GB/.media/screenshot/Tetris.png"), rig.arts.single())
    }

    @Test fun wheelIsNotFetched() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", found("wheel" to "https://m/t-wheel.png", "sstitle" to "https://m/t-title.png"))
        rig.http.on("https://m/", OK_PNG)
        val item = rig.run(listOf(tetris)).items.single()
        assertEquals(ItemStatus.NOT_FOUND to null, item.status to item.reason)   // no source offered anything
        assertTrue(rig.http.calls.none { it.startsWith("https://m/") })
        assertTrue(rig.arts.isEmpty() && rig.written.isEmpty())
    }

    @Test fun imageDownloadFailureIsPartial() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png"))
        rig.http.on("t-box", HttpResult(500, null))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris))
        assertEquals(ItemStatus.PARTIAL, end.items.single().status)
        assertEquals(JobPhase.DONE, end.phase)   // an image 500 never pauses the job
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
    }

    @Test fun aFailedOrUndecodable3dBoxFallsBackTo2d() = runTest {
        val rig = Rig(this)
        rig.fitter.undecodable = { it.contentEquals(byteArrayOf(6)) }
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-3D" to "https://m/t-3d.png", "box-2D" to "https://m/t-box.png"))
        rig.http.on("romnom=Zelda.gb", found("ss" to "https://m/z-ss.png", "box-3D" to "https://m/z-3d.png", "box-2D" to "https://m/z-box.png"))
        rig.http.on("t-3d", HttpResult(404, null))
        rig.http.on("z-3d", HttpResult(200, byteArrayOf(6)))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris, zelda))
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Zelda.png"))
        assertNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart/Zelda.png"))
    }

    @Test fun imageServerErrorIsAskedAgainOnce() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png"))
        rig.http.on("t-box", HttpResult(503, null), OK_PNG)
        rig.http.on("t-ss", HttpResult(429, null))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris))
        assertEquals(ItemStatus.PARTIAL to ScrapeEngine.NO_SCREENSHOT, end.items.single().let { it.status to it.reason })
        assertEquals(2, rig.http.count("t-box"))
        assertEquals(2, rig.http.count("t-ss"))   // a second 429: that image is skipped, the job goes on
        assertTrue(testScheduler.currentTime >= 2 * ScrapeEngine.BUSY_RETRY_MS)
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
    }

    @Test fun imageWithoutAnAnswerIsTriedOnceMoreThenPausesKeepingWhatWasWritten() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png"))
        rig.http.on("romnom=Zelda.gb", found("ss" to "https://m/z-ss.png", "box-2D" to "https://m/z-box.png"))
        rig.http.on("t-ss", HttpResult(-1, null))
        rig.http.on("z-ss", HttpResult(-1, null), OK_PNG)   // one drop: the retry gets it
        rig.http.on("https://m/", OK_PNG)
        val paused = rig.run(listOf(zelda, tetris))
        assertEquals(JobPhase.PAUSED, paused.phase)
        assertEquals(ScrapeEngine.NO_CONNECTION, paused.pauseReason)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.WAITING), paused.items.map { it.status })   // not "No artwork"
        assertEquals(2, rig.http.count("t-ss"))
        // Tetris's box was written before the drop: it is in the index, and Resume asks only for the screenshot.
        assertEquals(ArtRefs(box2d = "Home/Roms/GB/.media/boxart2d/Tetris.png"), rig.arts.last { it.first == tetris.gamePath }.second)
        rig.http.set("t-ss", OK_PNG)
        val end = rig.run(listOf(zelda, tetris), paused)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertEquals(1, rig.http.count("t-box"))
    }

    @Test fun nothingDownloadsIsNotFound() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png"))
        rig.http.on("https://m/", HttpResult(500, null))
        val item = rig.run(listOf(tetris)).items.single()
        assertEquals(ItemStatus.NOT_FOUND to "No artwork", item.status to item.reason)
        assertTrue(rig.arts.isEmpty())
    }

    @Test fun offlinePausesAndResumesWithoutRepeating() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png", "box-2D" to "https://m/t-box.png"))
        rig.http.on("romnom=Zelda.gb", HttpResult(-1, null), HttpResult(-1, null), HttpResult(-1, null))
        rig.http.on("https://m/", OK_PNG)
        val paused = rig.run(listOf(tetris, zelda))
        assertEquals(JobPhase.PAUSED, paused.phase)
        assertEquals("No connection", paused.pauseReason)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.WAITING), paused.items.map { it.status })
        assertTrue(testScheduler.currentTime >= ScreenScraperSource.BACKOFF.sum())

        rig.http.set("romnom=Zelda.gb", found("ss" to "https://m/z-ss.png", "box-2D" to "https://m/z-box.png"))
        val end = rig.run(listOf(tetris, zelda), paused)
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertEquals(1, rig.http.count("t-ss"))
        assertEquals(1, rig.http.count("romnom=Tetris.gb"))
    }

    private fun text(status: Int, body: String) = HttpResult(status, body.encodeToByteArray())

    /** ScreenScraper alone stopped: every game stays waiting and the job pauses with its note (Resume retries). */
    private fun assertStopped(end: ScrapeState, note: String, msg: String? = null) {
        assertEquals(JobPhase.PAUSED, end.phase, msg)
        assertEquals(ScrapeEngine.ALL_STOPPED, end.pauseReason, msg)
        assertEquals(mapOf(SourceId.SS to note), end.notes, msg)
    }

    @Test fun devLoginRefusedStopsAsBuildRefused() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", text(403, ScraperFixtures.DEV_LOGIN_REFUSED))
        val end = rig.run(listOf(tetris, zelda))
        assertStopped(end, ScreenScraperSource.BUILD_REFUSED)
        assertEquals("This build's ScreenScraper access was refused", end.notes[SourceId.SS])
        assertEquals(listOf(ItemStatus.WAITING, ItemStatus.WAITING), end.items.map { it.status })   // not Not found
        assertEquals(1, rig.http.count("jeuInfos"))
    }

    @Test fun otherForbiddenStopsWithSignInMessage() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", text(403, ScraperFixtures.USER_LOGIN_REFUSED))
        val end = rig.run(listOf(tetris, zelda))
        assertStopped(end, ScreenScraperSource.SIGN_IN_FAILED)
        assertEquals(0, rig.http.count("romnom=Zelda.gb"))
    }

    @Test fun unknownRomIsNotFoundAndTheJobGoesOn() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", text(404, ScraperFixtures.ROM_NOT_FOUND))
        rig.http.on("romnom=Zelda.gb", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris, zelda))
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(ItemStatus.NOT_FOUND, ItemStatus.DONE), end.items.map { it.status })
    }

    @Test fun documentedCodesStopScreenScraperWithTheirNotes() = runTest {
        val cases = mapOf(
            401 to "ScreenScraper is unavailable right now",   // API closed to non-members / inactive: not a bad login
            426 to "This app version is blocked by ScreenScraper — update the app",
            431 to "ScreenScraper's daily limit for unrecognised games reached; Fetch missing again tomorrow to ask it",
        )
        for ((code, reason) in cases) {
            val rig = Rig(this)
            rig.http.on("romnom=Tetris.gb", HttpResult(code, null))
            val end = rig.run(listOf(tetris, zelda))
            assertStopped(end, reason, "status $code")
            assertEquals(listOf(ItemStatus.WAITING, ItemStatus.WAITING), end.items.map { it.status }, "status $code")
            assertEquals(1, rig.http.count("jeuInfos"), "status $code")
        }
    }

    @Test fun quotaReachedAtStartStopsScreenScraper() = runTest {
        val rig = Rig(this)
        rig.auth = ScraperAuth("DEV", "DEVPW", user = "me", password = "pw")
        rig.http.on("ssuserInfos", Scrape.quota(100, 100))
        val end = rig.run(listOf(tetris))
        assertStopped(end, ScreenScraperSource.DAILY_LIMIT)
        assertEquals(ItemStatus.WAITING, end.items.single().status)
        assertEquals(SsQuota(100, 100), end.quota)
        assertEquals(0, rig.http.count("jeuInfos"))
    }

    @Test fun dailyLimitStopsScreenScraper() = runTest {
        // The daily limit stops ScreenScraper only; the job goes on with the next source.
        val other = FakeSource("other").apply {
            offer("Zelda.gb", ArtSlot.BOX_2D, "https://o/z-box"); offer("Zelda.gb", ArtSlot.SCREENSHOT, "https://o/z-ss")
        }
        val rig = Rig(this, more = listOf(other))
        rig.http.on("romnom=Tetris.gb", Scrape.BOTH)
        rig.http.on("romnom=Zelda.gb", HttpResult(430, null))
        rig.http.on("https://", OK_PNG)
        val end = rig.run(listOf(tetris, zelda, Scrape.homeGame("Mario.gb")))
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE, ItemStatus.NOT_FOUND), end.items.map { it.status })
        assertEquals(mapOf(SourceId.SS to ScreenScraperSource.DAILY_LIMIT), end.notes)
        assertEquals(0, rig.http.count("romnom=Mario.gb"))   // ScreenScraper is not asked again this job
        assertEquals(listOf("Zelda.gb", "Mario.gb"), other.asked.map { it.first })
        assertNotNull(rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Zelda.png"))
    }

    @Test fun tooFastRetriesOnceThenQuotaStops() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", HttpResult(429, null), Scrape.BOTH)
        rig.http.on("romnom=Zelda.gb", HttpResult(430, null))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris, zelda))
        assertEquals(ItemStatus.DONE, end.items[0].status)
        assertStopped(end, ScreenScraperSource.DAILY_LIMIT)
        assertEquals(ItemStatus.WAITING, end.items[1].status)
        assertEquals(2, rig.http.count("romnom=Zelda.gb"))
    }

    @Test fun unavailableStops() = runTest {
        for (code in listOf(423, 503)) {
            val rig = Rig(this)
            rig.http.on("romnom=Tetris.gb", HttpResult(code, null))
            val end = rig.run(listOf(tetris))
            assertStopped(end, "ScreenScraper is unavailable right now", "status $code")
            assertEquals(1, rig.http.count("jeuInfos"))
        }
    }

    @Test fun unsupportedTagIsSkipped() = runTest {
        val rig = Rig(this)
        val ports = Scrape.homeGame("Doom.sh", tag = "PORTS")
        val unknown = Scrape.homeGame("x.bin", tag = "ZZZ")
        val end = rig.run(listOf(ports, unknown))
        assertEquals(listOf(ItemStatus.SKIPPED, ItemStatus.SKIPPED), end.items.map { it.status })
        assertEquals(JobPhase.DONE, end.phase)
        assertTrue(rig.http.calls.isEmpty())
    }

    @Test fun failedWritePausesWithStorageMessage() = runTest {
        // A read-only tree with an NX Redux mix: the failed write pauses, and the index keeps what is on disk (no art).
        val fs = FakeRomsSource(
            dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("GB"), "Home/Roms/GB" to listOf("Tetris.gb", ".media"), "Home/Roms/GB/.media" to listOf("Tetris.png")),
            bytes = mapOf("Home/Roms/GB/.media/Tetris.png" to byteArrayOf(5)), writable = false,
        )
        val rig = Rig(this, fs = fs)
        rig.http.on("romnom=Tetris.gb", found("ss" to "https://m/t-ss.png"))
        rig.http.on("https://m/", OK_PNG)
        val end = rig.run(listOf(tetris))
        assertEquals(JobPhase.PAUSED, end.phase)
        assertEquals(ScrapeEngine.STORAGE, end.pauseReason)
        assertEquals(ItemStatus.WAITING, end.items.single().status)
        assertEquals("/Roms/GB/Tetris.gb" to ArtRefs(), rig.arts.single())   // a mix is no art
        assertContentEquals(byteArrayOf(5), fs.bytesAt("Home/Roms/GB/.media/Tetris.png"))
    }

    @Test fun artThatAppearedIsSkippedUnlessReplace() = runTest {
        val rig = Rig(this)
        rig.http.on("romnom=Tetris.gb", Scrape.BOTH)
        rig.http.on("https://m/", OK_PNG)
        val has = tetris.copy(have = Scrape.HAS_ART)
        val skipped = rig.run(listOf(has))
        assertEquals(ItemStatus.SKIPPED, skipped.items.single().status)
        assertEquals("Has art", skipped.items.single().reason)
        assertTrue(rig.http.calls.isEmpty())
        assertEquals(ItemStatus.DONE, rig.run(listOf(has), replace = true).items.single().status)
    }

    @Test fun quotaIsReadAtStartAndEveryFiftyGames() = runTest {
        val rig = Rig(this)
        rig.auth = ScraperAuth("DEV", "DEVPW", user = "me", password = "pw")
        rig.http.on("ssuserInfos", Scrape.quota(1, 1000), Scrape.quota(51, 1000))
        rig.http.on("romnom=", found("wheel" to "https://m/w.png"))
        rig.http.on("https://m/", OK_PNG)
        val games = (1..ScreenScraperSource.QUOTA_EVERY + 1).map { Scrape.homeGame("G$it.gb") }
        val end = rig.run(games)
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(2, rig.http.count("ssuserInfos"))
        val second = rig.http.calls.indexOfLast { it.contains("ssuserInfos") }
        assertEquals(ScreenScraperSource.QUOTA_EVERY, rig.http.calls.take(second).count { it.contains("jeuInfos") })
        assertEquals(SsQuota(51, 1000), end.quota)
    }

    @Test fun anExtrasOwnMediaStaysInTheIndexAfterAFetch() = runTest {
        // Sonic's box is in the extra folder's own .media (the index has its ref); the fetch writes only its screenshot to the mirror.
        val own = "Extra/Genesis/.media/boxart/Sonic.png"
        val sonic = Scrape.extraGame("Sonic.md", "MD").copy(have = ArtHave(box = true), art = ArtRefs(boxart = own))
        val rig = Rig(this)
        rig.http.on("romnom=Sonic.md", found("ss" to "https://m/s-ss.png", "box-2D" to "https://m/s-box.png"))
        rig.http.on("https://m/", OK_PNG)
        assertEquals(ItemStatus.DONE, rig.run(listOf(sonic)).items.single().status)
        assertEquals(sonic.gamePath to ArtRefs(boxart = own, screenshot = "Home/Roms/.sources/x1/Genesis/.media/screenshot/Sonic.png"), rig.arts.single())
    }

    @Test fun artOnDiskIsReadWithAnyExtensionCaseAndPngFirst() = runTest {
        val fs = Scrape.homeTree()
        fs.writeBytes(fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart2d"))!!, "Tetris.JPEG", byteArrayOf(7))
        val shot = fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "screenshot"))!!
        fs.writeBytes(shot, "Tetris.jpeg", byteArrayOf(8)); fs.writeBytes(shot, "Tetris.jpg", byteArrayOf(9)); fs.writeBytes(shot, "Tetris.PNG", byteArrayOf(10))
        val rig = Rig(this, fs = fs)
        val item = rig.run(listOf(tetris)).items.single()
        assertEquals(ItemStatus.SKIPPED to "Has art", item.status to item.reason)
        assertTrue(rig.http.calls.isEmpty())
        assertEquals(
            tetris.gamePath to ArtRefs(box2d = "Home/Roms/GB/.media/boxart2d/Tetris.JPEG", screenshot = "Home/Roms/GB/.media/screenshot/Tetris.PNG"),
            rig.arts.single(),
        )
    }

    @Test fun replaceRemovesAStaleLoggedHigherPriorityBoxOnly() = runTest {
        val fs = Scrape.homeTree()
        val box3d = fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", "boxart"))!!
        fs.writeBytes(box3d, "Tetris.png", byteArrayOf(7))   // the app wrote it in an earlier run (logged)
        fs.writeBytes(box3d, "Zelda.png", byteArrayOf(7))    // hand-placed: never logged
        val logged = mutableSetOf("Roms/GB/.media/boxart/Tetris.png")
        val rig = Rig(this, fs = fs, logged = logged)
        rig.http.on("romnom=", found("box-2D" to "https://m/box.png"))
        rig.http.on("https://m/", OK_PNG)
        rig.run(listOf(tetris.copy(have = Scrape.HAS_ART), zelda.copy(have = Scrape.HAS_ART)), replace = true)
        assertNull(fs.bytesAt("Home/Roms/GB/.media/boxart/Tetris.png"))
        assertNotNull(fs.bytesAt("Home/Roms/GB/.media/boxart/Zelda.png"))
        assertNotNull(fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
        assertTrue("Roms/GB/.media/boxart/Tetris.png" !in logged)
        assertEquals(ArtRefs(box2d = "Home/Roms/GB/.media/boxart2d/Tetris.png"), rig.arts.first { it.first == tetris.gamePath }.second)
    }
}
