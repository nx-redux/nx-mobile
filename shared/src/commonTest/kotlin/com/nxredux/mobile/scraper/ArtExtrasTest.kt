package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.ensurePath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tools → Artwork → Settings' optional art (NX Redux `scraper_core.c`): the 2D box beside the 3D one, the wheel, the mix. */
class ArtExtrasTest {
    private class Rig(vararg val sources: FakeSource, var extras: ArtExtras = ArtExtras(), val fs: FakeRomsSource = Scrape.homeTree()) {
        val http = FakeHttp()
        val pixels = FakePixels()
        val arts = mutableListOf<Pair<String, ArtRefs>>()
        val written = mutableListOf<Pair<String, ArtOrigin?>>()
        val warns = mutableListOf<String>()
        val logged = mutableSetOf<String>()
        val log = FakeLoggedArt(logged)
        val engine = ScrapeEngine(
            http, FakeFitter(), fs, Scrape.home, sources.toList(), onArt = { p, a -> arts += p to a }, onWritten = { written += it; log.record(it) },
            logged = log, extras = { extras }, pixels = pixels, warn = { warns += it },
        )

        suspend fun run(games: List<LibraryGameRef>, replace: Boolean = false) =
            engine.run(ScrapeState(items = games.map { Scrape.item(it) }), { p -> games.firstOrNull { it.gamePath == p } }, replace) {}

        fun put(folder: String, name: String, bytes: ByteArray) = suspend {
            fs.writeBytes(fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", folder))!!, name, bytes)
        }
    }

    private val tetris = Scrape.homeGame("Tetris.gb")
    private val all = setOf(ArtSlot.BOX_3D, ArtSlot.BOX_2D, ArtSlot.SCREENSHOT, ArtSlot.WHEEL)
    private fun png(b: Int) = HttpResult(200, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, b.toByte()))
    private fun ss() = FakeSource("ss", slots = all)
    /** A source with the mix slot (ScreenScraper's). */
    private fun ssMix() = FakeSource("ss", slots = all + ArtSlot.MIX)
    private val media = "Home/Roms/GB/.media"

    @Test fun extrasOffKeepTheOldFlow() = runTest {
        val a = ss().apply { offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.BOX_2D, "https://a/2d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss"); offer("Tetris.gb", ArtSlot.WHEEL, "https://a/w") }
        val rig = Rig(a)
        rig.http.on("https://a/", png(1))
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        assertEquals(setOf(ArtSlot.BOX_3D, ArtSlot.BOX_2D, ArtSlot.SCREENSHOT), a.asked.single().second)
        assertEquals(listOf("https://a/3d", "https://a/ss"), rig.http.calls)   // no 2D beside the 3D box, no wheel, no mix
        assertNull(rig.fs.bytesAt("$media/mix/Tetris.png"))
    }

    @Test fun box2dOnIsFetchedBesideThe3dBox() = runTest {
        val a = ss().apply { offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.BOX_2D, "https://a/2d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss") }
        val rig = Rig(a, extras = ArtExtras(box2d = true))
        rig.http.on("https://a/3d", png(1)); rig.http.on("https://a/2d", png(2)); rig.http.on("https://a/ss", png(3))
        val item = rig.run(listOf(tetris)).items.single()
        assertEquals(ItemStatus.DONE to "ss", item.status to item.reason)
        assertContentEquals(png(1).body, rig.fs.bytesAt("$media/boxart/Tetris.png"))
        assertContentEquals(png(2).body, rig.fs.bytesAt("$media/boxart2d/Tetris.png"))   // NX Redux's folder
        assertEquals(
            ArtRefs(boxart = "$media/boxart/Tetris.png", box2d = "$media/boxart2d/Tetris.png", screenshot = "$media/screenshot/Tetris.png"),
            rig.arts.single().second,
        )
    }

    @Test fun box2dOnWithoutA3dBoxIsTheBoxAndNoGridIsAsked() = runTest {
        val a = ss().apply { offer("Tetris.gb", ArtSlot.BOX_2D, "https://a/2d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss") }
        val grids = FakeSource("sgdb", slots = setOf(ArtSlot.GRID, ArtSlot.HERO)).apply { offer("Tetris.gb", ArtSlot.GRID, "https://g/1") }
        val rig = Rig(a, grids, extras = ArtExtras(box2d = true))
        rig.http.on("https://", png(1))
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        assertNotNull(rig.fs.bytesAt("$media/boxart2d/Tetris.png"))
        assertTrue(grids.asked.isEmpty())
    }

    @Test fun box2dAndWheelNeverMakeAGameWithArtFetched() = runTest {
        val a = ss().apply { offer("Tetris.gb", ArtSlot.BOX_2D, "https://a/2d"); offer("Tetris.gb", ArtSlot.WHEEL, "https://a/w") }
        val rig = Rig(a, extras = ArtExtras(box2d = true, wheel = true))
        val item = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART))).items.single()
        assertEquals(ItemStatus.SKIPPED to ScrapeEngine.HAS_ART, item.status to item.reason)
        assertTrue(a.asked.isEmpty())
    }

    @Test fun wheelOnComesFromTheFirstSourceWithOne() = runTest {
        val a = ss().apply { offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss"); offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d") }
        val b = FakeSource("b", slots = setOf(ArtSlot.WHEEL)).apply { offer("Tetris.gb", ArtSlot.WHEEL, "https://b/w1"); offer("Tetris.gb", ArtSlot.WHEEL, "https://b/w2") }
        val rig = Rig(a, b, extras = ArtExtras(wheel = true))
        rig.http.on("https://b/w1", HttpResult(404, null))
        rig.http.on("https://", png(4))
        val item = rig.run(listOf(tetris)).items.single()
        assertEquals(ItemStatus.DONE to "ss, b", item.status to item.reason)
        assertContentEquals(png(4).body, rig.fs.bytesAt("$media/wheel/Tetris.png"))
        assertEquals("Roms/GB/.media/wheel/Tetris.png" to ArtOrigin("b", "Tetris.gb", "https://b/w2"), rig.written.last())
        assertEquals("$media/wheel/Tetris.png", rig.arts.single().second.wheel)
        assertNull(rig.fs.bytesAt("$media/mix/Tetris.png"))   // the wheel alone makes no mix
    }

    @Test fun screenScraperWheelHdFirst() = runTest {
        val fs = Scrape.homeTree()
        val http = FakeHttp()
        val written = mutableListOf<Pair<String, ArtOrigin?>>()
        val ss = ScreenScraperSource(http, FakeHasher(), { ScraperAuth("DEV", "DEVPW") }, { 0L }) {}
        assertTrue(ArtSlot.WHEEL in ss.slots)
        val engine = ScrapeEngine(http, FakeFitter(), fs, Scrape.home, listOf(ss), { _, _ -> }, { written += it }, extras = { ArtExtras(wheel = true) })
        http.on("romnom=Tetris.gb", Scrape.found("ss" to "https://m/ss.png", "box-3D" to "https://m/3d.png", "wheel" to "https://m/w.png", "wheel-hd" to "https://m/whd.png"))
        http.on("https://m/", png(1))
        val end = engine.run(ScrapeState(items = listOf(Scrape.item(tetris))), { tetris }) {}
        assertEquals(ItemStatus.DONE, end.items.single().status)
        assertEquals(listOf("https://m/3d.png", "https://m/ss.png", "https://m/whd.png"), http.calls.filter { it.startsWith("https://m/") })
        assertEquals("Roms/GB/.media/wheel/Tetris.png" to ArtOrigin(SourceId.SS, "42", "wheel-hd/us"), written.last())
        assertNotNull(fs.bytesAt("$media/wheel/Tetris.png"))
    }

    @Test fun aWideRunAsksScreenScraperOnceForAGameLackingOnlyItsWheel() = runTest {
        val fs = Scrape.homeTree()
        val http = FakeHttp()
        val options = mutableListOf<ArtOption>()
        val ss = ScreenScraperSource(http, FakeHasher(), { ScraperAuth("DEV", "DEVPW") }, { 0L }) {}
        val engine = ScrapeEngine(
            http, FakeFitter(), fs, Scrape.home, listOf(ss), { _, _ -> }, { }, extras = { ArtExtras(wheel = true) },
            onOptions = { _, o, _ -> options += o },
        )
        http.on("romnom=Tetris.gb", Scrape.found("ss" to "https://m/ss.png", "box-3D" to "https://m/3d.png", "wheel" to "https://m/w.png"))
        http.on("https://m/", png(1))
        val game = tetris.copy(have = Scrape.HAS_ART)
        engine.run(ScrapeState(items = listOf(Scrape.item(game))), { game }, wide = true) {}
        assertEquals(1, http.count("jeuInfos"))   // the wide pass's slots rode on the wheel's lookup
        assertTrue(options.any { it.slot == ArtSlot.SCREENSHOT && it.source == SourceId.SS }, "$options")
        assertNotNull(fs.bytesAt("$media/wheel/Tetris.png"))
        assertNull(fs.bytesAt("$media/screenshot/Tetris.png"))   // the screenshot hit is only recorded
    }

    @Test fun screenScraperFallsBackToThePlainWheelWhenWheelHdFails() = runTest {
        val fs = Scrape.homeTree()
        val http = FakeHttp()
        val written = mutableListOf<Pair<String, ArtOrigin?>>()
        val ss = ScreenScraperSource(http, FakeHasher(), { ScraperAuth("DEV", "DEVPW") }, { 0L }) {}
        val other = FakeSource("sgdb", slots = setOf(ArtSlot.GRID, ArtSlot.WHEEL)).apply { offer("Tetris.gb", ArtSlot.WHEEL, "https://s/logo") }
        val engine = ScrapeEngine(http, FakeFitter(), fs, Scrape.home, listOf(ss, other), { _, _ -> }, { written += it }, extras = { ArtExtras(wheel = true) })
        http.on("romnom=Tetris.gb", Scrape.found("ss" to "https://m/ss.png", "box-3D" to "https://m/3d.png", "wheel" to "https://m/w.png", "wheel-hd" to "https://m/whd.png"))
        http.on("https://m/whd.png", HttpResult(404, null))
        http.on("https://", png(1))
        assertEquals(ItemStatus.DONE, engine.run(ScrapeState(items = listOf(Scrape.item(tetris))), { tetris }) {}.items.single().status)
        assertEquals(1, http.count("jeuInfos"))   // the same answer, no second lookup
        assertEquals("Roms/GB/.media/wheel/Tetris.png" to ArtOrigin(SourceId.SS, "42", "wheel/us"), written.last())
        assertTrue(other.asked.isEmpty())
    }

    @Test fun aWheelLastSourceIsAskedForTheWheelAloneOnlyWhenNoOtherHadOne() = runTest {
        // TheGamesDB is wanted for nothing but the wheel: SteamGridDB is asked first; TheGamesDB only when it had none.
        val a = FakeSource("a", slots = setOf(ArtSlot.SCREENSHOT)).apply { offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss") }
        fun tgdb() = FakeSource("tgdb", slots = setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT, ArtSlot.WHEEL), wheelLast = true)
            .apply { offer("Tetris.gb", ArtSlot.WHEEL, "https://t/logo") }
        val t1 = tgdb()
        val sgdb = FakeSource("sgdb", slots = setOf(ArtSlot.GRID, ArtSlot.WHEEL)).apply { offer("Tetris.gb", ArtSlot.WHEEL, "https://s/logo") }
        val rig = Rig(a, t1, sgdb, extras = ArtExtras(wheel = true))
        rig.http.on("https://", png(5))
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris.copy(have = ArtHave(box = true)))).items.single().status)
        assertTrue(t1.asked.isEmpty())
        assertEquals("Roms/GB/.media/wheel/Tetris.png" to ArtOrigin("sgdb", "Tetris.gb", "https://s/logo"), rig.written.last())
        // SteamGridDB without a logo: TheGamesDB is asked for the wheel alone, after it.
        val t2 = tgdb()
        val rig2 = Rig(a, t2, FakeSource("sgdb", slots = setOf(ArtSlot.GRID, ArtSlot.WHEEL)), extras = ArtExtras(wheel = true))
        rig2.http.on("https://", png(5))
        val item = rig2.run(listOf(tetris.copy(have = ArtHave(box = true)))).items.single()
        assertEquals(ItemStatus.DONE to "a, tgdb", item.status to item.reason)
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.WHEEL)), t2.asked)
        assertEquals("Roms/GB/.media/wheel/Tetris.png" to ArtOrigin("tgdb", "Tetris.gb", "https://t/logo"), rig2.written.last())
    }

    @Test fun aWheelLastSourcesWheelIsTriedOnlyAfterTheOthers() = runTest {
        // Decision order: ScreenScraper, then SteamGridDB's logos, then TheGamesDB's clear logo (asked earlier for its screenshot).
        val tgdb = FakeSource("tgdb", slots = setOf(ArtSlot.SCREENSHOT, ArtSlot.WHEEL), wheelLast = true)
            .apply { offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://t/ss"); offer("Tetris.gb", ArtSlot.WHEEL, "https://t/logo") }
        val sgdb = FakeSource("sgdb", slots = setOf(ArtSlot.GRID, ArtSlot.WHEEL)).apply { offer("Tetris.gb", ArtSlot.WHEEL, "https://s/logo") }
        val rig = Rig(tgdb, sgdb, extras = ArtExtras(wheel = true))
        rig.http.on("https://", png(5))
        rig.run(listOf(tetris.copy(have = ArtHave(box = true))))
        assertEquals(setOf(ArtSlot.SCREENSHOT, ArtSlot.WHEEL), tgdb.asked.single().second)
        assertEquals(setOf(ArtSlot.WHEEL), sgdb.asked.single().second)
        assertEquals(listOf("https://t/ss", "https://s/logo"), rig.http.calls)
        // Without a SteamGridDB logo, TheGamesDB's is written.
        val sgdb2 = FakeSource("sgdb", slots = setOf(ArtSlot.GRID, ArtSlot.WHEEL))
        val rig2 = Rig(tgdb, sgdb2, extras = ArtExtras(wheel = true))
        rig2.http.on("https://", png(5))
        assertEquals(ItemStatus.DONE, rig2.run(listOf(tetris.copy(have = ArtHave(box = true)))).items.single().status)
        assertEquals(listOf("https://t/ss", "https://t/logo"), rig2.http.calls)
        assertEquals("Roms/GB/.media/wheel/Tetris.png" to ArtOrigin("tgdb", "Tetris.gb", "https://t/logo"), rig2.written.last())
    }

    @Test fun mixDoesNotForceTheWheel() {
        assertFalse(ArtExtras(mix = true).wantWheel)
        assertTrue(ArtExtras(wheel = true).wantWheel)
    }

    @Test fun aGameLackingItsMixAndWheelAsksForBothThenBuildsTheMixWhenNoneCame() = runTest {
        val rig = Rig(ssMix().apply {
            offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss"); offer("Tetris.gb", ArtSlot.WHEEL, "https://a/w")
        }, extras = ArtExtras(wheel = true, mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))()
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))()
        rig.http.on("https://a/w", png(13))
        val item = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART))).items.single()   // has art, but no mix: fetched
        assertEquals(ItemStatus.DONE to "ss", item.status to item.reason)
        assertEquals(setOf(ArtSlot.WHEEL, ArtSlot.MIX), rig.sources[0].asked.single().second)   // no mix offered: built
        assertEquals(listOf("https://a/w"), rig.http.calls)                        // no box or screenshot downloaded again
        assertEquals(listOf(listOf<Byte>(12), listOf<Byte>(11), png(13).body!!.toList()), rig.pixels.decoded.map { it.toList() })
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertEquals("Roms/GB/.media/mix/Tetris.png" to ArtOrigin(ScrapeEngine.MIX_ORIGIN), rig.written.last())
        assertEquals(
            ArtRefs(boxart = "$media/boxart/Tetris.png", screenshot = "$media/screenshot/Tetris.png", wheel = "$media/wheel/Tetris.png", mix = "$media/mix/Tetris.png"),
            rig.arts.single().second,
        )
        // Now it has its mix: a game with art again.
        val again = rig.run(listOf(tetris.copy(have = ArtHave.of(rig.arts.single().second, needMix = true)))).items.single()
        assertEquals(ItemStatus.SKIPPED to ScrapeEngine.HAS_ART, again.status to again.reason)
    }

    @Test fun mixUsesTheJustDownloadedArtThe3dBoxBeforeThe2dAndAGridAs2dBox() = runTest {
        val a = ss().apply {
            offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.BOX_2D, "https://a/2d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss")
        }
        val rig = Rig(a, extras = ArtExtras(box2d = true, mix = true))
        rig.http.on("https://a/3d", png(1)); rig.http.on("https://a/2d", png(2)); rig.http.on("https://a/ss", png(3))
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        assertEquals(listOf(png(3).body!!.toList(), png(1).body!!.toList()), rig.pixels.decoded.map { it.toList() })   // no wheel anywhere: left out
        assertNotNull(rig.fs.bytesAt("$media/mix/Tetris.png"))
        // A grid as the box: written as the 2D box, so it is the mix's box layer.
        val grid = FakeSource("sgdb", slots = setOf(ArtSlot.GRID)).apply { offer("Tetris.gb", ArtSlot.GRID, "https://g/1") }
        val shots = FakeSource("b", slots = setOf(ArtSlot.SCREENSHOT)).apply { offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://b/ss") }
        val rig2 = Rig(shots, grid, extras = ArtExtras(mix = true))
        rig2.http.on("https://g/", png(7)); rig2.http.on("https://b/", png(8))
        assertEquals(ItemStatus.DONE, rig2.run(listOf(tetris)).items.single().status)
        assertEquals(listOf(png(8).body!!.toList(), png(7).body!!.toList()), rig2.pixels.decoded.map { it.toList() })
    }

    @Test fun aMixThatFailsNeverFailsTheGame() = runTest {
        val a = ss().apply { offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss") }
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.pixels.failEncode = true
        rig.http.on("https://a/", png(1))
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        assertNull(rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertEquals(1, rig.warns.size)
        assertTrue(rig.written.none { it.first.contains("/mix/") })
    }

    @Test fun aGameLackingOnlyItsMixWithNothingToMakeItFromIsNotFound() = runTest {
        // A box and a screenshot that do not decode: nothing to make the mix from.
        val a = ssMix()   // asked for the mix, has none
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.pixels.undecodable = { true }
        rig.put("boxart2d", "Tetris.jpg", byteArrayOf(1))()
        rig.put("screenshot", "Tetris.png", byteArrayOf(2))()
        val item = rig.run(listOf(tetris.copy(have = ArtHave(box = true, box2d = true, screenshot = true)))).items.single()
        assertEquals(ItemStatus.NOT_FOUND to ScrapeEngine.NO_MIX, item.status to item.reason)
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.MIX)), a.asked)
        assertEquals(listOf(listOf<Byte>(2), listOf<Byte>(1)), rig.pixels.decoded.map { it.toList() })
        assertNull(rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertEquals(2, rig.warns.size)
    }

    @Test fun replaceRemakesTheAppsMixButNotAHandPlacedOne() = runTest {
        fun source() = ss().apply { offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss") }
        val game = tetris.copy(have = ArtHave(box = true, screenshot = true, mix = true))
        val rig = Rig(source(), extras = ArtExtras(mix = true))
        rig.put("mix", "Tetris.png", byteArrayOf(9))()
        rig.logged += "Roms/GB/.media/mix/Tetris.png"
        rig.log.origins["Roms/GB/.media/mix/Tetris.png"] = ArtOrigin(ScrapeEngine.MIX_ORIGIN)
        rig.http.on("https://a/", png(1))
        rig.run(listOf(game), replace = true)
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
        // Not in the log: hand-placed, kept (the build never overwrites it, Replace included).
        val rig2 = Rig(source(), extras = ArtExtras(mix = true))
        rig2.put("mix", "Tetris.png", byteArrayOf(9))()
        rig2.http.on("https://a/", png(1))
        rig2.run(listOf(game), replace = true)
        assertContentEquals(byteArrayOf(9), rig2.fs.bytesAt("$media/mix/Tetris.png"))
    }

    @Test fun theLegacyBox2dFolderIsStillReadAndBoxart2dWins() = runTest {
        val rig = Rig(ss())
        rig.put("box2d", "Tetris.jpg", byteArrayOf(1))()
        rig.put("screenshot", "Tetris.png", byteArrayOf(2))()
        val item = rig.run(listOf(tetris)).items.single()
        assertEquals(ItemStatus.SKIPPED to ScrapeEngine.HAS_ART, item.status to item.reason)
        assertEquals("$media/box2d/Tetris.jpg", rig.arts.single().second.box2d)
        rig.put("boxart2d", "Tetris.png", byteArrayOf(3))()
        assertEquals("$media/boxart2d/Tetris.png", rig.engine.artOnDisk(tetris).box2d)
    }

    @Test fun replaceWithAGridRemovesTheAppsLegacy2dBox() = runTest {
        val grid = FakeSource("sgdb", slots = setOf(ArtSlot.GRID)).apply { offer("Tetris.gb", ArtSlot.GRID, "https://g/1") }
        val rig = Rig(grid)
        rig.put("box2d", "Tetris.jpg", byteArrayOf(1))()
        rig.logged += "Roms/GB/.media/box2d/Tetris.jpg"
        rig.http.on("https://g/", png(1))
        rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), replace = true)
        assertNull(rig.fs.bytesAt("$media/box2d/Tetris.jpg"))
        assertEquals("$media/boxart2d/Tetris.jpg", rig.arts.single().second.box2d)
    }
}
