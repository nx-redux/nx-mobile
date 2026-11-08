package com.nxredux.mobile.scraper

import kotlinx.coroutines.CancellationException
import kotlin.test.assertFailsWith
import com.nxredux.mobile.library.Entry
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.scraper.Scrape.OK_PNG
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a run costs: state emissions, folder queries and written-art log writes per game. */
class ScrapeEngineCostTest {
    /** Counts the provider queries a run makes (each is a content-provider round trip on SAF). */
    private class CountingSource(private val inner: FakeRomsSource) : RomsSource by inner {
        var children = 0
        var canWrites = 0
        override suspend fun child(dir: ContentRef, name: String): Entry? { children++; return inner.child(dir, name) }
        override suspend fun canWrite(tree: ContentRef): Boolean { canWrites++; return inner.canWrite(tree) }
    }

    private fun gbTree(files: List<String>) = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to listOf("GB"), "Home/Roms/GB" to files))

    @Test fun a_fast_stream_of_skipped_games_is_not_emitted_per_game() = runTest {
        val games = (1..200).map { Scrape.homeGame("G$it.gb") }
        val src = FakeSource("a", knowsGame = { false })
        var now = 0L
        val engine = ScrapeEngine(FakeHttp(), FakeFitter(), gbTree(emptyList()), Scrape.home, listOf(src), { _, _ -> }, {}, clock = { now })
        val states = mutableListOf<ScrapeState>()
        val end = engine.run(ScrapeState(items = games.map { Scrape.item(it) }), { p -> now += 10; games.firstOrNull { it.gamePath == p } }) { states += it }
        assertTrue(end.items.all { it.status == ItemStatus.SKIPPED })
        // 2 s of games: at most one emission per interval, plus the start and the end.
        assertTrue(states.size <= 2 + 2_000 / ScrapeEngine.EMIT_EVERY_MS + 1, "${states.size} emissions")
        assertEquals(end, states.last())
        assertTrue(states.dropLast(1).zipWithNext().all { (a, b) -> b.done >= a.done })
    }

    @Test fun a_game_is_shown_searching_and_the_last_one_finished_before_a_source_is_asked() = runTest {
        val tetris = Scrape.homeGame("Tetris.gb")
        val zelda = Scrape.homeGame("Zelda.gb")
        val src = FakeSource("a")
        val states = mutableListOf<ScrapeState>()
        val seen = mutableListOf<List<ItemStatus>>()
        src.fail = { seen += states.last().items.map { it.status }; null }
        val engine = ScrapeEngine(FakeHttp(), FakeFitter(), Scrape.homeTree(), Scrape.home, listOf(src), { _, _ -> }, {}, clock = { 0L })
        val games = listOf(tetris, zelda)
        engine.run(ScrapeState(items = games.map { Scrape.item(it) }), { p -> games.firstOrNull { it.gamePath == p } }) { states += it }
        assertEquals(
            listOf(listOf(ItemStatus.SEARCHING, ItemStatus.WAITING), listOf(ItemStatus.NOT_FOUND, ItemStatus.SEARCHING)),
            seen,
        )
    }

    @Test fun a_console_s_folders_are_looked_up_once_per_run_not_per_game() = runTest {
        val files = (1..20).map { "G$it.gb" }
        val fs = CountingSource(gbTree(files))
        val src = FakeSource("a")
        for (f in files) { src.offer(f, ArtSlot.BOX_2D, "https://m/$f-box.png"); src.offer(f, ArtSlot.SCREENSHOT, "https://m/$f-ss.png") }
        val http = FakeHttp().apply { on("https://m/", OK_PNG) }
        val engine = ScrapeEngine(http, FakeFitter(), fs, Scrape.home, listOf(src), { _, _ -> }, {}, clock = { 0L })
        val games = files.map { Scrape.homeGame(it) }
        val end = engine.run(ScrapeState(items = games.map { Scrape.item(it) }), { p -> games.firstOrNull { it.gamePath == p } }) {}
        assertTrue(end.items.all { it.status == ItemStatus.DONE })
        // Per game only the two files written are looked up (their entries); the folders once per run.
        assertTrue(fs.children <= 2 * files.size + 20, "${fs.children} child queries for ${files.size} games")
        assertEquals(1, fs.canWrites)
    }

    @Test fun replace_removes_a_game_s_stale_logged_boxes_without_rewriting_the_log() = runTest {
        val logPaths = listOf("Roms/GB/.media/boxart/Tetris.png", "Roms/GB/.media/boxart/Tetris.jpg", "Roms/GB/.media/box2d/Tetris.png")
        val host = FakeHost(emptyList()).apply { appendable = true; files["log"] = logPaths.joinToString("") { "$it\tscreenscraper\t1\tbox\n" } }
        val log = ArtLogStore(host, "log")
        val fs = FakeRomsSource(
            dirs = mapOf(
                "Home" to listOf("Roms"), "Home/Roms" to listOf("GB"), "Home/Roms/GB" to listOf("Tetris.gb", ".media"),
                "Home/Roms/GB/.media" to listOf("boxart", "box2d"),
                "Home/Roms/GB/.media/boxart" to listOf("Tetris.png", "Tetris.jpg"), "Home/Roms/GB/.media/box2d" to listOf("Tetris.png"),
            ),
        )
        val src = FakeSource("a").apply { offer("Tetris.gb", ArtSlot.BOX_2D, "https://m/b.png") }
        val http = FakeHttp().apply { on("https://m/", OK_PNG) }
        val engine = ScrapeEngine(http, FakeFitter(), fs, Scrape.home, listOf(src), { _, _ -> }, { log.add(it) }, logged = log, clock = { 0L })
        val tetris = Scrape.homeGame("Tetris.gb")
        engine.run(ScrapeState(items = listOf(Scrape.item(tetris))), { tetris.takeIf { t -> t.gamePath == it } }, replace = true) {}
        for (p in logPaths) assertTrue(!log.contains(p), p)
        assertTrue(host.writes.isEmpty(), "${host.writes.size} whole-log rewrites")
        assertEquals(setOf("Roms/GB/.media/boxart2d/Tetris.png"), WrittenArtLog.decode(host.files["log"]).entries)
    }

    @Test fun a_cancel_still_emits_the_games_finished_since_the_last_emission() = runTest {
        val games = (1..3).map { Scrape.homeGame("G$it.gb") }
        val engine = ScrapeEngine(FakeHttp(), FakeFitter(), gbTree(emptyList()), Scrape.home, listOf(FakeSource("a", knowsGame = { false })), { _, _ -> }, {}, clock = { 0L })
        val states = mutableListOf<ScrapeState>()
        assertFailsWith<CancellationException> {
            engine.run(ScrapeState(items = games.map { Scrape.item(it) }), { p ->
                if (p == games[2].gamePath) throw CancellationException("cancelled")
                games.firstOrNull { it.gamePath == p }
            }) { states += it }
        }
        assertEquals(listOf(ItemStatus.SKIPPED, ItemStatus.SKIPPED, ItemStatus.WAITING), states.last().items.map { it.status })
    }
}
