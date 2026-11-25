package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.scraper.Scrape.OK_PNG
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The engine's per-kind order over its sources, with fake sources A, B, C. */
class SourceOrderTest {
    private class Rig(vararg val sources: FakeSource) {
        val fs = Scrape.homeTree()
        val http = FakeHttp()
        val fitter = FakeFitter()
        val arts = mutableListOf<Pair<String, ArtRefs>>()
        val written = mutableListOf<Pair<String, ArtOrigin?>>()
        val engine = ScrapeEngine(http, fitter, fs, Scrape.home, sources.toList(), onArt = { p, a -> arts += p to a }, onWritten = { written += it })

        suspend fun run(games: List<LibraryGameRef>, start: ScrapeState = ScrapeState(items = games.map { Scrape.item(it) })) =
            engine.run(start, { p -> games.firstOrNull { it.gamePath == p } }) {}

        /** The image URLs downloaded, in order. */
        val downloads get() = http.calls
    }

    private val tetris = Scrape.homeGame("Tetris.gb")
    private val zelda = Scrape.homeGame("Zelda.gb")
    private val box = ArtSlot.BOX_2D
    private val shot = ArtSlot.SCREENSHOT


    @Test fun boxFromFirstSourceThatHasIt() = runTest {
        val a = FakeSource("a").apply { offer("Tetris.gb", box, "https://a/box") }
        val b = FakeSource("b").apply { offer("Tetris.gb", box, "https://b/box"); offer("Tetris.gb", shot, "https://b/shot") }
        val rig = Rig(a, b)
        rig.http.on("https://a/", Scrape.png(1))
        rig.http.on("https://b/", Scrape.png(2))
        val end = rig.run(listOf(tetris))
        assertEquals(ItemStatus.DONE, end.items.single().status)
        assertContentEquals(Scrape.png(1).body, rig.fs.bytesAt("Home/Roms/GB/.media/boxart2d/Tetris.png"))
        assertContentEquals(Scrape.png(2).body, rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png"))
        assertEquals(listOf("https://a/box", "https://b/shot"), rig.downloads)
        assertEquals(
            listOf<Pair<String, ArtOrigin?>>("Roms/GB/.media/boxart2d/Tetris.png" to ArtOrigin("a", "Tetris.gb", "https://a/box"), "Roms/GB/.media/screenshot/Tetris.png" to ArtOrigin("b", "Tetris.gb", "https://b/shot")),
            rig.written,
        )
    }

    @Test fun screenshotFromLaterSourceWhenFirstHasNone() = runTest {
        val a = FakeSource("a")
        val b = FakeSource("b", slots = setOf(box))
        val c = FakeSource("c").apply { offer("Tetris.gb", shot, "https://c/shot") }
        val rig = Rig(a, b, c)
        rig.http.on("https://c/", OK_PNG)
        val item = rig.run(listOf(tetris)).items.single()
        assertEquals(ItemStatus.PARTIAL to ScrapeEngine.NO_BOX, item.status to item.reason)
        assertTrue(rig.fs.bytesAt("Home/Roms/GB/.media/screenshot/Tetris.png") != null)
        assertEquals(listOf("Tetris.gb" to setOf(box, shot)), a.asked)
        assertEquals(listOf("Tetris.gb" to setOf(box)), b.asked)   // only the slots it offers
        assertEquals(listOf("Tetris.gb" to setOf(box, shot)), c.asked)
    }

    @Test fun failedDownloadTriesNextCandidateThenNextSource() = runTest {
        val a = FakeSource("a").apply { offer("Tetris.gb", box, "https://a/1"); offer("Tetris.gb", box, "https://a/2") }
        val b = FakeSource("b").apply { offer("Tetris.gb", box, "https://b/1"); offer("Tetris.gb", shot, "https://b/shot") }
        val rig = Rig(a, b)
        rig.fitter.undecodable = { it.contentEquals(byteArrayOf(6)) }
        rig.http.on("https://a/1", HttpResult(404, null))
        rig.http.on("https://a/2", HttpResult(200, byteArrayOf(6)))
        rig.http.on("https://b/", OK_PNG)
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        assertEquals(listOf("https://a/1", "https://a/2", "https://b/1", "https://b/shot"), rig.downloads)
        assertEquals(ArtOrigin("b", "Tetris.gb", "https://b/1"), rig.written.first { it.first.contains("boxart2d") }.second)
    }

    @Test fun disabledSourceSkipped() = runTest {
        val a = FakeSource("a", on = false).apply { offer("Tetris.gb", box, "https://a/box") }
        val b = FakeSource("b").apply { offer("Tetris.gb", box, "https://b/box"); offer("Tetris.gb", shot, "https://b/shot") }
        val rig = Rig(a, b)
        rig.http.on("https://b/", OK_PNG)
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        assertTrue(a.asked.isEmpty())
        assertEquals(0, a.starts)
        assertEquals(1, b.starts)
        assertEquals(listOf("https://b/box", "https://b/shot"), rig.downloads)
    }

    @Test fun unknownSystemSkipped() = runTest {
        val a = FakeSource("a", knowsGame = { it.fileName != "Tetris.gb" })
        val b = FakeSource("b", knowsGame = { false })
        val rig = Rig(a, b)
        val end = rig.run(listOf(tetris, zelda))
        assertEquals(ItemStatus.SKIPPED to ScrapeEngine.NO_SOURCE, end.items[0].status to end.items[0].reason)
        assertEquals(ItemStatus.NOT_FOUND, end.items[1].status)
        assertEquals(listOf("Zelda.gb"), a.asked.map { it.first })
        assertTrue(b.asked.isEmpty())
        assertEquals(JobPhase.DONE, end.phase)
    }

    @Test fun askedOnlyForMissing() = runTest {
        val a = FakeSource("a").apply { offer("Tetris.gb", box, "https://a/box"); offer("Zelda.gb", shot, "https://a/shot") }
        val b = FakeSource("b").apply { offer("Tetris.gb", shot, "https://b/shot"); offer("Zelda.gb", box, "https://b/box") }
        val rig = Rig(a, b)
        rig.http.on("https://", OK_PNG)
        val hasShot = zelda.copy(have = ArtHave(screenshot = true))
        val end = rig.run(listOf(tetris, hasShot))
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertEquals(listOf("Tetris.gb" to setOf(box, shot), "Zelda.gb" to setOf(box)), a.asked)
        assertEquals(listOf("Tetris.gb" to setOf(shot), "Zelda.gb" to setOf(box)), b.asked)   // what A wrote is not asked again
        assertTrue("https://a/shot" !in rig.downloads)
    }

    @Test fun nothingAskedOnceEveryKindIsWritten() = runTest {
        val a = FakeSource("a").apply { offer("Tetris.gb", box, "https://a/box"); offer("Tetris.gb", shot, "https://a/shot") }
        val b = FakeSource("b")
        val rig = Rig(a, b)
        rig.http.on("https://a/", OK_PNG)
        assertEquals(ItemStatus.DONE, rig.run(listOf(tetris)).items.single().status)
        assertTrue(b.asked.isEmpty())
    }

    @Test fun sourceStopContinuesWithOthers() = runTest {
        val a = FakeSource("a").apply { fail = { SourceStop("A: limit reached") } }
        val b = FakeSource("b").apply {
            offer("Tetris.gb", box, "https://b/t-box"); offer("Tetris.gb", shot, "https://b/t-shot")
            offer("Zelda.gb", box, "https://b/z-box"); offer("Zelda.gb", shot, "https://b/z-shot")
        }
        val rig = Rig(a, b)
        rig.http.on("https://b/", OK_PNG)
        val end = rig.run(listOf(tetris, zelda))
        assertEquals(JobPhase.DONE, end.phase)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), end.items.map { it.status })
        assertEquals(listOf("Tetris.gb"), a.asked.map { it.first })   // never asked again this job
        assertEquals(listOf("Tetris.gb", "Zelda.gb"), b.asked.map { it.first })
        assertEquals(mapOf("a" to "A: limit reached"), end.notes)
    }

    @Test fun stopAtJobStartLeavesTheSourceOut() = runTest {
        val a = object : ArtSource by FakeSource("a") {
            override suspend fun startJob() = throw SourceStop("A: refused")
        }
        val b = FakeSource("b").apply { offer("Tetris.gb", box, "https://b/box"); offer("Tetris.gb", shot, "https://b/shot") }
        val rig = ScrapeEngine(FakeHttp().apply { on("https://b/", OK_PNG) }, FakeFitter(), Scrape.homeTree(), Scrape.home, listOf(a, b), { _, _ -> }, {})
        val end = rig.run(ScrapeState(items = listOf(Scrape.item(tetris))), { tetris }) {}
        assertEquals(ItemStatus.DONE, end.items.single().status)
        assertEquals(mapOf("a" to "A: refused"), end.notes)
    }

    @Test fun offlinePausesTheJob() = runTest {
        val a = FakeSource("a").apply {
            offer("Tetris.gb", box, "https://a/box"); offer("Tetris.gb", shot, "https://a/shot")
            fail = { if (it.fileName == "Zelda.gb") JobPause(ScrapeEngine.NO_CONNECTION) else null }
        }
        val b = FakeSource("b")
        val rig = Rig(a, b)
        rig.http.on("https://a/", OK_PNG)
        val end = rig.run(listOf(tetris, zelda))
        assertEquals(JobPhase.PAUSED, end.phase)
        assertEquals(ScrapeEngine.NO_CONNECTION, end.pauseReason)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.WAITING), end.items.map { it.status })
        assertTrue(b.asked.none { it.first == "Zelda.gb" })
        assertTrue(end.notes.isEmpty())
    }

    @Test fun allSourcesStopped() = runTest {
        val a = FakeSource("a").apply { fail = { SourceStop("A: out") } }
        val b = FakeSource("b").apply { fail = { SourceStop("B: out") } }
        val rig = Rig(a, b)
        val end = rig.run(listOf(tetris, zelda))
        assertEquals(JobPhase.PAUSED, end.phase)
        assertEquals(ScrapeEngine.ALL_STOPPED, end.pauseReason)
        assertEquals(listOf(ItemStatus.WAITING, ItemStatus.WAITING), end.items.map { it.status })   // Resume retries them
        assertEquals(mapOf("a" to "A: out", "b" to "B: out"), end.notes)
        assertEquals(listOf("Tetris.gb"), (a.asked + b.asked).map { it.first }.distinct())

        // A chained run of the same job keeps them stopped; Resume (notes cleared) asks them again.
        assertEquals(end.items, rig.run(listOf(tetris, zelda), end.copy(phase = JobPhase.RUNNING)).items)
        assertEquals(1, a.asked.size)
        a.fail = { null }; b.fail = { null }
        a.offer("Tetris.gb", box, "https://a/t"); a.offer("Zelda.gb", box, "https://a/z")
        b.offer("Tetris.gb", shot, "https://b/t"); b.offer("Zelda.gb", shot, "https://b/z")
        rig.http.on("https://", OK_PNG)
        val resumed = rig.run(listOf(tetris, zelda), end.copy(notes = emptyMap()))
        assertEquals(JobPhase.DONE, resumed.phase)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), resumed.items.map { it.status })
    }

    @Test fun aStoppedSourceWithAnotherThatFoundNothingIsNotFound() = runTest {
        val a = FakeSource("a").apply { fail = { SourceStop("A: out") } }
        val b = FakeSource("b")
        val end = Rig(a, b).run(listOf(tetris))
        assertEquals(ItemStatus.NOT_FOUND, end.items.single().status)
        assertEquals(JobPhase.DONE, end.phase)
    }

    @Test fun aGameWhoseRelevantSourcesAllStoppedStaysWaiting() = runTest {
        // b offers only boxes; both games lack only a screenshot, so only a is relevant, and a stops on Tetris.
        val a = FakeSource("a").apply { fail = { SourceStop("A: out") } }
        val b = FakeSource("b", slots = setOf(box))
        val end = Rig(a, b).run(listOf(tetris.copy(have = ArtHave(box = true)), zelda.copy(have = ArtHave(box = true))))
        assertEquals(listOf(ItemStatus.WAITING, ItemStatus.WAITING), end.items.map { it.status })
        assertEquals(JobPhase.PAUSED, end.phase)
        assertEquals(ScrapeEngine.ALL_STOPPED, end.pauseReason)
        assertTrue(b.asked.isEmpty())
    }
}
