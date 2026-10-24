package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.homeShelfCap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeShelvesTest {
    private val day = 86_400_000L
    private val now = 100 * day
    private val gb = SystemFolder("Game Boy", "GB", null, emptyList(), null, null, "Game Boy (GB)", false)
    private fun g(name: String, seen: Long? = null) =
        Listed(gb, GameEntry(ContentRef("r/$name"), "$name.gb", name, folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy (GB)", firstSeen = seen))
    private fun played(at: Long, ms: Long) = GameInfo(lastPlayedAt = at, totalMs = ms, ra = null)
    private fun names(s: List<Pair<HomeShelf, List<Listed>>>, k: HomeShelf) = s.firstOrNull { it.first == k }?.second.orEmpty().map { it.shown }

    @Test fun newIsLast30DaysNewestFirst() {
        val lib = listOf(g("Old", now - 31 * day), g("A", now - 2 * day), g("B", now - 1 * day), g("Null"))
        val s = homeShelves(lib, emptyMap(), emptySet(), emptySet(), now, day = 100)
        assertEquals(listOf("B", "A"), names(s, HomeShelf.NEW))
    }
    // A file dated in the future (a wrong clock, a copied file) is not New.
    @Test fun aFutureDateIsNotNew() {
        val s = homeShelves(listOf(g("Future", now + day), g("Today", now)), emptyMap(), emptySet(), emptySet(), now, day = 100)
        assertEquals(listOf("Today"), names(s, HomeShelf.NEW))
    }
    @Test fun pickUpIsPlayedOver14DaysAgoMostTimeFirst() {
        val a = g("A"); val b = g("B"); val c = g("Recent")
        val info = mapOf(a.game.path to played(now - 20 * day, 1_000), b.game.path to played(now - 15 * day, 9_000), c.game.path to played(now - 3 * day, 99_000))
        assertEquals(listOf("B", "A"), names(homeShelves(listOf(a, b, c), info, emptySet(), emptySet(), now, 100), HomeShelf.PICK_UP))
    }
    @Test fun notStartedSkipsPlayedAndRecents() {
        val a = g("A"); val b = g("B"); val c = g("C")
        val s = homeShelves(listOf(a, b, c), mapOf(a.game.path to played(now - 1, 1)), recent = setOf(b.game.path), emptySet(), now, 100)
        assertEquals(listOf("C"), names(s, HomeShelf.NOT_STARTED))
    }
    @Test fun notStartedIsStableForADay() {
        val lib = (1..200).map { g("G$it") }
        val one = names(homeShelves(lib, emptyMap(), emptySet(), emptySet(), now, 100), HomeShelf.NOT_STARTED)
        assertEquals(one, names(homeShelves(lib.reversed(), emptyMap(), emptySet(), emptySet(), now + 1000, 100), HomeShelf.NOT_STARTED))
        assertEquals(4, one.size)
        assertTrue(one != names(homeShelves(lib, emptyMap(), emptySet(), emptySet(), now, 101), HomeShelf.NOT_STARTED))
    }
    @Test fun pinnedAndContinueNeverOnAShelf() {
        val a = g("A", now - day); val b = g("B", now - day); val c = g("C", now - day)
        val s = homeShelves(listOf(a, b, c), emptyMap(), emptySet(), exclude = setOf(a.game.path, b.game.path), now, 100)
        assertEquals(listOf("C"), names(s, HomeShelf.NEW)); assertEquals(emptyList(), names(s, HomeShelf.NOT_STARTED))
    }
    @Test fun aGameIsOnOneShelfAndEmptyShelvesDrop() {
        val a = g("A", now - day)   // new and never played: New only
        val s = homeShelves(listOf(a), emptyMap(), emptySet(), emptySet(), now, 100)
        assertEquals(listOf(HomeShelf.NEW), s.map { it.first })
    }
    // The data picks at most 4 a shelf; each Home layout then shows what it fits (Grid [homeShelfCap], the others 3).
    @Test fun atMostFourAShelf() {
        val lib = (1..10).map { g("N$it", now - it * day) }
        assertEquals(4, names(homeShelves(lib, emptyMap(), emptySet(), emptySet(), now, 100), HomeShelf.NEW).size)
        val old = (1..10).map { g("P$it") }
        val info = old.associate { it.game.path to played(now - 20 * day, 1_000) }
        assertEquals(4, names(homeShelves(old, info, emptySet(), emptySet(), now, 100), HomeShelf.PICK_UP).size)
    }
    // 10 new games never played: New takes 4, Not started 4 of the 16 left.
    @Test fun everyShelfHoldsFour() {
        val lib = (1..10).map { g("N$it", now - it * day) } + (1..10).map { g("U$it") }
        val s = homeShelves(lib, emptyMap(), emptySet(), emptySet(), now, 100)
        assertEquals(4, names(s, HomeShelf.NEW).size); assertEquals(4, names(s, HomeShelf.NOT_STARTED).size)
        assertEquals(4, homeShelfMax(HomeShelf.NOT_STARTED)); assertEquals(4, homeShelfMax(HomeShelf.NEW)); assertEquals(4, homeShelfMax(HomeShelf.PICK_UP))
    }
    // Grid Home's shelves: 4 in portrait (two rows of 2) and on a row of 4 or more (the large face), else 3 (a landscape
    // row of 3). List, Minimal and Carousel show 3.
    @Test fun gridShelfCapFollowsTheBoardsRow() {
        assertEquals(4, homeShelfCap(portrait = true, perRow = 2))
        assertEquals(3, homeShelfCap(portrait = false, perRow = 3))
        assertEquals(3, homeShelfCap(portrait = false, perRow = 2))
        assertEquals(4, homeShelfCap(portrait = false, perRow = 4))
        assertEquals(3, HOME_SHELF_LIST_MAX)
    }
    @Test fun unassignedAndAndroidAreLeftOut() {
        val un = SystemFolder("Unassigned", "", null, emptyList(), null, null, "", true)
        val x = Listed(un, GameEntry(ContentRef("r/x"), "x.bin", "X", folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms", firstSeen = now))
        val droid = SystemFolder("Android", "", null, emptyList(), android = true)
        val y = Listed(droid, GameEntry(ContentRef("r/y"), "y", "Y", folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms", firstSeen = now))
        assertEquals(emptyList(), homeShelves(listOf(x, y), emptyMap(), emptySet(), emptySet(), now, 100))
    }
    @Test fun buildHomeAppendsShelvesAfterPins() {
        val a = g("A"); val b = g("B")
        val h = buildHome(MainRow.Continue(g("K"), ResumeInfo.NONE), listOf(a), emptyList(), HomeStats(null, null, false, null, false), slots = 6,
            shelves = listOf(HomeShelf.NOT_STARTED to listOf(b)))
        assertEquals(listOf(MainRow.Shelf(b, HomeShelf.NOT_STARTED)), h.shelves)
        assertEquals(listOf(MainRow.Pin(a), MainRow.Shelf(b, HomeShelf.NOT_STARTED)), homeTabRows(h).drop(1))
        assertEquals(listOf(HomeShelf.NOT_STARTED to 1), homeView(h, homeTabRows(h)).shelves)
    }
    @Test fun shelfBandsGroupInOrder() {
        val rows = listOf(MainRow.Pin(g("P")), MainRow.Shelf(g("A"), HomeShelf.NEW), MainRow.Shelf(g("B"), HomeShelf.NEW), MainRow.Shelf(g("C"), HomeShelf.PICK_UP))
        assertEquals(listOf(HomeShelf.NEW to 2, HomeShelf.PICK_UP to 1), shelfBands(rows))
    }
    // The shelves are computed once while the library, game info, recents, exclusions and day hold.
    @Test fun theShelfCacheComputesOncePerInputs() {
        var calls = 0
        val cache = HomeShelvesCache { lib, info, recent, exclude, at, d -> calls++; homeShelves(lib, info, recent, exclude, at, d) }
        val systems = listOf(gb.copy(games = listOf(g("A").game, g("B").game)))
        val info = mapOf<String, GameInfo>()
        val first = cache.shelves(systems, info, setOf("x"), setOf("y"), now, 100)
        assertEquals(first, cache.shelves(systems, info, setOf("x"), setOf("y"), now + 1000, 100))   // equal sets, later the same day
        assertEquals(1, calls)
        val systems2 = systems.toList(); val info2 = mapOf(g("A").game.path to GameInfo(now - day, 1, null))
        cache.shelves(systems2, info, setOf("x"), setOf("y"), now, 100); assertEquals(2, calls)   // a new library
        cache.shelves(systems2, info2, setOf("x"), setOf("y"), now, 100); assertEquals(3, calls)   // new game info
        cache.shelves(systems2, info2, setOf("z"), setOf("y"), now, 100); assertEquals(4, calls)   // recents
        cache.shelves(systems2, info2, setOf("z"), setOf("w"), now, 100); assertEquals(5, calls)   // exclusions
        cache.shelves(systems2, info2, setOf("z"), setOf("w"), now + day, 101); assertEquals(6, calls)   // the next day
        cache.shelves(systems2, info2, setOf("z"), setOf("w"), now + day, 101); assertEquals(6, calls)
    }
}
