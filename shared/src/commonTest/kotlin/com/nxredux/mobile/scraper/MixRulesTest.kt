package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.ensurePath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** When the mix is wanted, remade and made at all (NX Redux `scraper_compositor.c` needs a screenshot or a box). */
class MixRulesTest {
    private class Rig(vararg val sources: ArtSource, var extras: ArtExtras = ArtExtras(wheel = true, mix = true), val fs: FakeRomsSource = Scrape.homeTree()) {
        val http = FakeHttp()
        val pixels = FakePixels()
        val arts = mutableListOf<Pair<String, ArtRefs>>()
        val written = mutableListOf<Pair<String, ArtOrigin?>>()
        val warns = mutableListOf<String>()
        val logged = mutableSetOf<String>()
        val log = FakeLoggedArt(logged)
        /** The slots the user picked for the game. */
        var picks: Set<ArtSlot> = emptySet()
        val engine = ScrapeEngine(
            http, FakeFitter(), fs, Scrape.home, sources.toList(), onArt = { p, a -> arts += p to a }, onWritten = { written += it; log.record(it) },
            logged = log, extras = { extras }, pixels = pixels, warn = { warns += it }, picked = { picks },
        )

        /** The mix on disk is in the written-art log with [source] as its origin (the app's: [ScrapeEngine.MIX_ORIGIN]). */
        fun logMix(source: String) {
            logged += MIX_PATH
            log.origins[MIX_PATH] = ArtOrigin(source)
        }

        suspend fun run(games: List<LibraryGameRef>, replace: Boolean = false, notes: Map<String, String> = emptyMap()) =
            engine.run(ScrapeState(items = games.map { Scrape.item(it) }, notes = notes), { p -> games.firstOrNull { it.gamePath == p } }, replace) {}

        suspend fun put(folder: String, name: String, bytes: ByteArray) {
            fs.writeBytes(fs.ensurePath(Scrape.home, listOf("Roms", "GB", ".media", folder))!!, name, bytes)
        }
    }

    private val tetris = Scrape.homeGame("Tetris.gb")
    private val all = setOf(ArtSlot.BOX_3D, ArtSlot.BOX_2D, ArtSlot.SCREENSHOT, ArtSlot.WHEEL)
    private fun png(b: Int) = HttpResult(200, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, b.toByte()))
    private fun ss() = FakeSource("ss", slots = all)
    private val media = "Home/Roms/GB/.media"
    private val oldMix = byteArrayOf(9)
    /** ScreenScraper with its mix slot. */
    private fun ssMix() = FakeSource(SourceId.SS, slots = all + ArtSlot.MIX)
    private companion object { const val MIX_PATH = "Roms/GB/.media/mix/Tetris.png" }

    @Test fun theAppsMixIsRemadeWhenARunWritesOneOfItsInputs() = runTest {
        val rig = Rig(ss().apply { offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d") })
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.put("mix", "Tetris.png", oldMix)   // made before the game had a box
        rig.logMix(ScrapeEngine.MIX_ORIGIN)
        rig.http.on("https://a/", png(1))
        val item = rig.run(listOf(tetris.copy(have = ArtHave(screenshot = true, mix = true)))).items.single()
        assertEquals(ItemStatus.DONE, item.status)
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertEquals(listOf(listOf<Byte>(12), png(1).body!!.toList()), rig.pixels.decoded.map { it.toList() })
    }

    @Test fun aUsersOwnMixIsNotRemadeWithoutReplace() = runTest {
        val rig = Rig(ss().apply { offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d") })
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.put("mix", "Tetris.png", oldMix)   // not in the log: hand-placed
        rig.http.on("https://a/", png(1))
        rig.run(listOf(tetris.copy(have = ArtHave(screenshot = true, mix = true))))
        assertContentEquals(oldMix, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertTrue(rig.pixels.decoded.isEmpty())
    }

    @Test fun theAppsMixIsNotRemadeWhenNothingWasWritten() = runTest {
        val rig = Rig(ss())   // offers nothing
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.put("mix", "Tetris.png", oldMix)
        rig.logMix(ScrapeEngine.MIX_ORIGIN)
        rig.run(listOf(tetris.copy(have = ArtHave(screenshot = true, mix = true))))
        assertContentEquals(oldMix, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertTrue(rig.pixels.decoded.isEmpty())
    }

    @Test fun aMixIsNeededOnlyWithAScreenshotOrABox() {
        // A hero in the screenshot slot is no mix input; a 2D box (a grid among them) is.
        assertFalse(ArtHave.of(ArtRefs(screenshot = "h.jpg", heroShot = true), needMix = true).mixInput)
        assertFalse(ArtHave.of(ArtRefs(box2d = "g.jpg", screenshot = "h.jpg", heroShot = true), needMix = true).complete)
        assertFalse(ArtHave.of(ArtRefs(boxart = "b.png", screenshot = "s.png"), needMix = true).complete)
        assertTrue(ArtHave.of(ArtRefs(boxart = "b.png", screenshot = "s.png", mix = "m.png"), needMix = true).complete)
    }

    @Test fun mixSkipsHeroShot() = runTest {
        val sg = FakeSource(SourceId.SGDB, slots = setOf(ArtSlot.GRID, ArtSlot.HERO)).apply { offer("Tetris.gb", ArtSlot.HERO, "https://g/h") }
        val rig = Rig(sg)
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.http.on("https://g/", png(5))
        val item = rig.run(listOf(tetris.copy(have = ArtHave(box = true)))).items.single()
        assertEquals(ItemStatus.DONE, item.status)
        assertEquals(listOf(listOf<Byte>(11)), rig.pixels.decoded.map { it.toList() })   // the box alone: the hero is no screenshot input
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertTrue(rig.arts.single().second.heroShot)
    }

    @Test fun theWheelAloneMakesNoMix() = runTest {
        val rig = Rig(ss().apply { offer("Tetris.gb", ArtSlot.WHEEL, "https://a/w") })
        rig.http.on("https://a/", png(3))
        rig.run(listOf(tetris.copy(have = ArtHave(screenshot = true))))
        assertNotNull(rig.fs.bytesAt("$media/wheel/Tetris.png"))
        assertNull(rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertTrue(rig.written.none { it.first.contains("/mix/") })
    }

    @Test fun aGameMissingOnlyItsMixAsksScreenScraperForTheMixAlone() = runTest {
        val a = ssMix().apply { offer("Tetris.gb", ArtSlot.MIX, "https://a/mix"); offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d") }
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.http.on("https://a/", png(20))
        val item = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART))).items.single()
        assertEquals(ItemStatus.DONE, item.status)
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.MIX)), a.asked)   // one request, for the mix alone
        assertEquals(listOf("https://a/mix"), rig.http.calls)
        // Only the download was decoded: no mix was composed from the box and screenshot on disk.
        assertEquals(listOf(png(20).body!!.toList()), rig.pixels.decoded.map { it.toList() })
    }

    @Test fun aDownloadedMixIsWrittenOnTheMixCanvasAsPng() = runTest {
        val a = ssMix().apply { offer("Tetris.gb", ArtSlot.MIX, "https://a/mix") }
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.http.on("https://a/", png(20))
        rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)))
        val placed = rig.pixels.encoded.last()
        assertEquals(MixCompositor.W to MixCompositor.H, placed.width to placed.height)
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertEquals(MIX_PATH to ArtOrigin(SourceId.SS, "Tetris.gb", "https://a/mix"), rig.written.last())
        assertEquals(SourceId.SS, rig.log.origin(MIX_PATH)?.source)
        assertEquals("$media/mix/Tetris.png", rig.arts.single().second.mix)
    }

    @Test fun whenScreenScraperHasNoMixTheAppBuildsOne() = runTest {
        val a = ssMix()   // no mix for this game
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        val item = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART))).items.single()
        assertEquals(ItemStatus.DONE, item.status)
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.MIX)), a.asked)
        assertEquals(listOf(listOf<Byte>(12), listOf<Byte>(11)), rig.pixels.decoded.map { it.toList() })
        assertEquals(MIX_PATH to ArtOrigin(ScrapeEngine.MIX_ORIGIN), rig.written.last())
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
    }

    @Test fun aFullFetchGetsScreenScraperMixInTheSameRequest() = runTest {
        val a = ssMix().apply {
            offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss"); offer("Tetris.gb", ArtSlot.MIX, "https://a/mix")
        }
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.http.on("https://a/", png(1))
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        val wanted = a.asked.single().second
        assertTrue(wanted.containsAll(setOf(ArtSlot.BOX_3D, ArtSlot.SCREENSHOT, ArtSlot.MIX)), "$wanted")
        assertEquals(SourceId.SS, rig.log.origin(MIX_PATH)?.source)   // not rebuilt from the box and screenshot written
        assertTrue(rig.written.none { it.second?.source == ScrapeEngine.MIX_ORIGIN })
    }

    @Test fun aDownloadedMixIsNeverRebuiltWhenAnInputChanges() = runTest {
        val rig = Rig(ssMix().apply { offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss") }, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("mix", "Tetris.png", oldMix)
        rig.logMix(SourceId.SS)
        rig.http.on("https://a/", png(1))
        val item = rig.run(listOf(tetris.copy(have = ArtHave(box = true, mix = true)))).items.single()
        assertEquals(ItemStatus.DONE, item.status)
        assertNotNull(rig.fs.bytesAt("$media/screenshot/Tetris.png"))
        assertContentEquals(oldMix, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertTrue(rig.pixels.decoded.isEmpty())
    }

    @Test fun replaceWithoutAScreenScraperMixKeepsADownloadedMix() = runTest {
        fun source() = ssMix().apply { offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss") }
        val game = tetris.copy(have = ArtHave(box = true, screenshot = true, mix = true))
        val rig = Rig(source(), extras = ArtExtras(mix = true))
        rig.put("mix", "Tetris.png", oldMix)
        rig.logMix(SourceId.SS)
        rig.http.on("https://a/", png(1))
        rig.run(listOf(game), replace = true)
        assertContentEquals(oldMix, rig.fs.bytesAt("$media/mix/Tetris.png"))
        // A hand-placed mix (not in the log) stays too.
        val rig2 = Rig(source(), extras = ArtExtras(mix = true))
        rig2.put("mix", "Tetris.png", oldMix)
        rig2.http.on("https://a/", png(1))
        rig2.run(listOf(game), replace = true)
        assertContentEquals(oldMix, rig2.fs.bytesAt("$media/mix/Tetris.png"))
        assertTrue(rig2.written.none { it.first == MIX_PATH })
    }

    @Test fun replaceSwapsAnAppBuiltMixForScreenScrapers() = runTest {
        val rig = Rig(ssMix().apply { offer("Tetris.gb", ArtSlot.MIX, "https://a/mix") }, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.put("mix", "Tetris.png", oldMix)
        rig.logMix(ScrapeEngine.MIX_ORIGIN)
        rig.http.on("https://a/", png(20))
        rig.run(listOf(tetris.copy(have = ArtHave(box = true, screenshot = true, mix = true))), replace = true)
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertEquals(ArtOrigin(SourceId.SS, "Tetris.gb", "https://a/mix"), rig.log.origin(MIX_PATH))
        assertEquals(listOf(png(20).body!!.toList()), rig.pixels.decoded.map { it.toList() })
    }

    @Test fun aPickedMixIsNeverFetchedOrRebuilt() = runTest {
        val a = ssMix().apply { offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.MIX, "https://a/mix") }
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.picks = setOf(ArtSlot.MIX)
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.put("mix", "Tetris.png", oldMix)
        rig.logMix(SourceId.SS)   // a pick is logged with its option's origin
        rig.http.on("https://a/", png(1))
        rig.run(listOf(tetris.copy(have = ArtHave(screenshot = true, mix = true))))
        assertNotNull(rig.fs.bytesAt("$media/boxart/Tetris.png"))
        assertContentEquals(oldMix, rig.fs.bytesAt("$media/mix/Tetris.png"))
        // Under Replace: ScreenScraper is not asked for the mix, and none is built.
        rig.run(listOf(tetris.copy(have = ArtHave(box = true, screenshot = true, mix = true))), replace = true)
        assertTrue(a.asked.none { ArtSlot.MIX in it.second }, "${a.asked}")
        assertContentEquals(oldMix, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertTrue(rig.pixels.decoded.isEmpty())
    }

    /** ScreenScraper with no mix for the game; while it is asked, [during] runs (the user picks a mix meanwhile). */
    private class PickDuringFind(val during: suspend () -> Unit) : ArtSource {
        override val id = SourceId.SS
        override val label = id
        override val slots = setOf(ArtSlot.BOX_3D, ArtSlot.SCREENSHOT, ArtSlot.MIX)
        override val enabled = true
        val asked = mutableListOf<Set<ArtSlot>>()
        override fun knows(ref: LibraryGameRef) = true
        override suspend fun find(ref: LibraryGameRef, wanted: Set<ArtSlot>): List<ArtHit> { asked += wanted; during(); return emptyList() }
    }

    @Test fun aMixPickedDuringTheFetchWinsOverTheBuiltMix() = runTest {
        lateinit var rig: Rig
        val pickedMix = byteArrayOf(42)
        // The pick as writePick makes it: its file written and logged with the option's origin, then the slot picked.
        val src = PickDuringFind { rig.put("mix", "Tetris.png", pickedMix); rig.logMix(SourceId.SS); rig.picks = setOf(ArtSlot.MIX) }
        rig = Rig(src, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        val item = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART))).items.single()
        assertEquals(listOf(setOf(ArtSlot.MIX)), src.asked)
        assertContentEquals(pickedMix, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertTrue(rig.written.none { it.first == MIX_PATH }, "${rig.written}")
        assertEquals(ItemStatus.DONE, item.status)
    }

    @Test fun aGameWithAnyMixIsNotAskedAgainWithoutReplace() = runTest {
        val a = ssMix().apply { offer("Tetris.gb", ArtSlot.MIX, "https://a/mix") }
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.put("mix", "Tetris.png", oldMix)   // hand-placed
        val item = rig.run(listOf(tetris.copy(have = ArtHave(box = true, screenshot = true, mix = true)))).items.single()
        assertEquals(ItemStatus.SKIPPED to ScrapeEngine.HAS_ART, item.status to item.reason)
        assertTrue(a.asked.isEmpty())
        assertTrue(rig.http.calls.isEmpty())
    }

    @Test fun aMixOnlyGameWaitsWhileScreenScraperIsStopped() = runTest {
        val a = ssMix().apply { offer("Tetris.gb", ArtSlot.MIX, "https://a/mix") }
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        val end = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)), notes = mapOf(SourceId.SS to "Out of requests for today"))
        assertEquals(ItemStatus.WAITING, end.items.single().status)
        assertEquals(JobPhase.PAUSED, end.phase)
        assertTrue(a.asked.isEmpty())
        assertNull(rig.fs.bytesAt("$media/mix/Tetris.png"))
    }

    @Test fun aMixOnlyGameWaitsWhenScreenScraperStopsOnIt() = runTest {
        val a = ssMix().apply { fail = { SourceStop("Out of requests for today") } }
        val rig = Rig(a, FakeSource(SourceId.TGDB, slots = setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT)), extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        val end = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)))
        assertEquals(ItemStatus.WAITING, end.items.single().status)
        assertEquals(JobPhase.PAUSED, end.phase)
        assertNull(rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertTrue(rig.pixels.decoded.isEmpty())
    }

    @Test fun withoutScreenScraperAMixOnlyGameIsBuiltLocally() = runTest {
        fun tgdb() = FakeSource(SourceId.TGDB, slots = setOf(ArtSlot.BOX_2D, ArtSlot.SCREENSHOT, ArtSlot.WHEEL), wheelLast = true)
        val t = tgdb()
        val rig = Rig(t, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        val end = rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)))
        assertEquals(ItemStatus.DONE, end.items.single().status)
        assertTrue(t.asked.isEmpty())
        assertTrue(rig.http.calls.isEmpty())
        assertEquals(MIX_PATH to ArtOrigin(ScrapeEngine.MIX_ORIGIN), rig.written.last())
        // With that source stopped as well: still built, the job not paused.
        val t2 = tgdb()
        val rig2 = Rig(t2, extras = ArtExtras(mix = true))
        rig2.put("boxart", "Tetris.png", byteArrayOf(11))
        rig2.put("screenshot", "Tetris.png", byteArrayOf(12))
        val end2 = rig2.run(listOf(tetris.copy(have = Scrape.HAS_ART)), notes = mapOf(SourceId.TGDB to "Out of requests for today"))
        assertEquals(ItemStatus.DONE, end2.items.single().status)
        assertEquals(JobPhase.DONE, end2.phase)
        assertTrue(t2.asked.isEmpty())
        assertContentEquals(FakePixels.MIX_PNG, rig2.fs.bytesAt("$media/mix/Tetris.png"))
    }

    @Test fun theWheelIsUsedWhenThereAndLeftOutOtherwise() = runTest {
        // Wheel off: no wheel is asked for the mix; one on disk is still a layer.
        fun source() = ssMix().apply { offer("Tetris.gb", ArtSlot.WHEEL, "https://a/w") }
        val a = source()
        val rig = Rig(a, extras = ArtExtras(mix = true))
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.http.on("https://a/", png(13))
        rig.run(listOf(tetris.copy(have = Scrape.HAS_ART)))
        assertEquals(listOf("Tetris.gb" to setOf(ArtSlot.MIX)), a.asked)
        assertTrue(rig.http.calls.isEmpty())
        assertEquals(listOf(listOf<Byte>(12), listOf<Byte>(11)), rig.pixels.decoded.map { it.toList() })
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
        val rig2 = Rig(source(), extras = ArtExtras(mix = true))
        rig2.put("boxart", "Tetris.png", byteArrayOf(11))
        rig2.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig2.put("wheel", "Tetris.png", byteArrayOf(13))
        rig2.run(listOf(tetris.copy(have = Scrape.HAS_ART.copy(wheel = true))))
        assertEquals(listOf(listOf<Byte>(12), listOf<Byte>(11), listOf<Byte>(13)), rig2.pixels.decoded.map { it.toList() })
    }

    @Test fun anInputThatFailsToDecodeIsWarnedAndTheMixMadeWithoutIt() = runTest {
        val rig = Rig(ss())
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        rig.pixels.undecodable = { it.first() == 12.toByte() }
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris.copy(have = Scrape.HAS_ART))).items.single().status)
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertEquals(1, rig.warns.size)
    }

    @Test fun runningOutOfMemoryOnAnInputIsWarnedNotThrown() = runTest {
        val rig = Rig(ss())
        rig.put("boxart", "Tetris.png", byteArrayOf(11))
        rig.put("screenshot", "Tetris.png", byteArrayOf(12))
        val codec = object : PixelCodec {
            override suspend fun decode(image: ByteArray): ArgbImage? =
                if (image.first() == 12.toByte()) throw Error("out of memory") else ArgbImage(1, 1, intArrayOf(image.first().toInt()))
            override suspend fun encodePng(image: ArgbImage): ByteArray = FakePixels.MIX_PNG
        }
        val warns = mutableListOf<String>()
        val engine = ScrapeEngine(
            rig.http, FakeFitter(), rig.fs, Scrape.home, listOf(ss()), { _, _ -> }, {}, extras = { ArtExtras(mix = true) }, pixels = codec, warn = { warns += it },
        )
        val end = engine.run(ScrapeState(items = listOf(Scrape.item(tetris))), { tetris.copy(have = Scrape.HAS_ART) }) {}
        assertEquals(ItemStatus.DONE, end.items.single().status)
        assertContentEquals(FakePixels.MIX_PNG, rig.fs.bytesAt("$media/mix/Tetris.png"))
        assertEquals(1, warns.size)
    }

    @Test fun replaceWritingThe2dBoxRemovesTheAppsLegacyBox2dOnly() = runTest {
        val a = ss().apply { offer("Tetris.gb", ArtSlot.BOX_3D, "https://a/3d"); offer("Tetris.gb", ArtSlot.BOX_2D, "https://a/2d"); offer("Tetris.gb", ArtSlot.SCREENSHOT, "https://a/ss") }
        val rig = Rig(a, extras = ArtExtras(box2d = true))
        rig.put("box2d", "Tetris.jpg", byteArrayOf(1))
        rig.logged += "Roms/GB/.media/box2d/Tetris.jpg"
        rig.http.on("https://a/", png(1))
        rig.run(listOf(tetris.copy(have = ArtHave(box = true, box2d = true, screenshot = true))), replace = true)
        assertNotNull(rig.fs.bytesAt("$media/boxart2d/Tetris.png"))
        assertNull(rig.fs.bytesAt("$media/box2d/Tetris.jpg"))
        assertFalse("Roms/GB/.media/box2d/Tetris.jpg" in rig.logged)
        // A hand-placed legacy 2D box stays.
        val rig2 = Rig(a, extras = ArtExtras(box2d = true))
        rig2.put("box2d", "Tetris.jpg", byteArrayOf(1))
        rig2.http.on("https://a/", png(1))
        rig2.run(listOf(tetris.copy(have = ArtHave(box = true, box2d = true, screenshot = true))), replace = true)
        assertNotNull(rig2.fs.bytesAt("$media/box2d/Tetris.jpg"))
    }

    /** A source that, once asked, waits (ignoring cancellation) for [gate], then throws an Error (out of memory). */
    private class ErrorAfterGate : ArtSource {
        override val id = "x"
        override val label = "x"
        override val slots = setOf(ArtSlot.BOX_3D, ArtSlot.SCREENSHOT)
        override val enabled = true
        val gate = CompletableDeferred<Unit>()
        var entered = false
        override fun knows(ref: LibraryGameRef) = true
        override suspend fun startJob() {}
        override suspend fun find(ref: LibraryGameRef, wanted: Set<ArtSlot>): List<ArtHit> {
            entered = true
            withContext(NonCancellable) { gate.await() }
            throw Error("out of memory")
        }
    }

    @Test fun anErrorWhilePausingNeverDeadlocksTheJob() = runTest(timeout = 10.seconds) {
        val src = ErrorAfterGate()
        val fs = Scrape.homeTree()
        val host = FakeHost(listOf(tetris))
        val runner = FakeRunner()
        val controller = ArtworkController(
            host, { _, onArt, onWritten, _, logged, _, _ -> ScrapeEngine(FakeHttp(), FakeFitter(), fs, Scrape.home, listOf(src), onArt, onWritten, logged = logged) },
            FakeCredentials(), runner, this,
        )
        controller.fetchMissing()
        runCurrent()
        assertTrue(src.entered)
        controller.pause()
        runCurrent()   // halt holds the lock, waiting for the job
        src.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(JobPhase.PAUSED, controller.state.value.phase)
        assertEquals(false to null, runner.calls.last())
    }
}
