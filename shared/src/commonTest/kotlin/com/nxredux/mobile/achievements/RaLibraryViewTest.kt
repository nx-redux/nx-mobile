package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.IndexedGame
import com.nxredux.mobile.library.IndexedSystem
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RaLibraryViewTest {
    private val h1 = "a".repeat(32); private val h2 = "b".repeat(32); private val h3 = "c".repeat(32)
    private val index = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(IndexedSystem("PS", "Sony PlayStation", games = listOf(
        IndexedGame("home", "Roms/PS", "Game.m3u", "r1", "f", "Game (m3u)", boxart = "box1", screenshot = "shot1"),
        IndexedGame("home", "Roms/PS", "Game (Disc 1).cue", "r2", "f", "Game (Disc 1)"),
        IndexedGame("home", "Roms/PS", "Other.cue", "r3", "f", "Other", boxart = "box3"),
    ))))
    private suspend fun seed(store: FakeRaStore) {
        store.put(RaPaths.sets(h1), setsJson(42, title = "Game", achievements = """{"ID":1,"Title":"A","Points":5,"BadgeName":"1","Rarity":50},{"ID":2,"Title":"B","Points":10,"BadgeName":"2","Rarity":5},{"ID":101000001,"Title":"Unknown Emulator"}"""))
        store.put(RaPaths.game(h1), RaJson.json.encodeToString(RaGamePointer.serializer(), RaGamePointer("PS", "home", "Roms/PS", "Game.m3u", "Game (m3u)")))
        store.put(RaPaths.sets(h2), setsJson(42, title = "Game", achievements = """{"ID":1},{"ID":2}"""))
        store.put(RaPaths.game(h2), RaJson.json.encodeToString(RaGamePointer.serializer(), RaGamePointer("PS", "home", "Roms/PS", "Game (Disc 1).cue", "Game (Disc 1)")))
        store.put(RaPaths.sets(h3), setsJson(7, title = "Other", achievements = """{"ID":9}"""))
        store.put(RaPaths.game(h3), RaJson.json.encodeToString(RaGamePointer.serializer(), RaGamePointer("PS", "home", "Roms/PS", "Other.cue", "Other")))
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[{"ID":1,"When":100}]}""")
        RaJournal(store, RaPaths.JOURNAL).append(RaUnlock("me", 2, h1, 200L))
        RaJournal(store, RaPaths.CONFIRMED).append(RaUnlock("me", 9, h3, 300L))
    }
    @Test fun games_join_the_index_collapse_discs_and_count_states() = runTest {
        val store = FakeRaStore(); seed(store)
        val games = RaLibraryView.games(store, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), index, "me")
        assertEquals(listOf(RaGameView(h1, 42, "Game (m3u)", "shot1", 2, 2, 1, "Sony PlayStation", "PS"), RaGameView(h3, 7, "Other", "box3", 1, 1, 0, "Sony PlayStation", "PS")), games)
    }
    @Test fun a_games_art_is_its_screenshot_then_its_box_art_then_its_cover() = runTest {
        val store = FakeRaStore(); seed(store)
        fun indexOf(vararg games: IndexedGame) = LibraryIndex(2, "home", emptyList(), emptyMap(), listOf(IndexedSystem("PS", "Sony PlayStation", games = games.toList())))
        suspend fun artOf(i: LibraryIndex) =
            RaLibraryView.games(store, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), i, "me").first { it.hash == h3 }.art
        val other = IndexedGame("home", "Roms/PS", "Other.cue", "r3", "f", "Other")
        assertEquals("shot", artOf(indexOf(other.copy(boxart = "box", box2d = "cover", screenshot = "shot"))))   // the screenshot wins
        assertEquals("box", artOf(indexOf(other.copy(boxart = "box", box2d = "cover"))))                          // no screenshot: the 3D box
        assertEquals("cover", artOf(indexOf(other.copy(box2d = "cover"))))                                        // then the 2D cover
        assertEquals("box", artOf(indexOf(other.copy(boxart = "box", screenshot = "hero", heroShot = true))))      // a hero screenshot: never
        assertEquals(null, artOf(indexOf(other.copy(screenshot = "hero", heroShot = true))))
        assertEquals(null, artOf(indexOf(other)))                                                                 // no art: none
    }
    @Test fun a_game_with_only_the_unknown_emulator_warning_is_not_listed() = runTest {
        val store = FakeRaStore(); seed(store)
        val h4 = "d".repeat(32)
        store.put(RaPaths.sets(h4), setsJson(8, title = "Warned", achievements = """{"ID":101000001,"Title":"Warning: Unknown Emulator","BadgeName":"00000","Flags":3}"""))
        val games = RaLibraryView.games(store, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), index, "me")
        assertTrue(games.none { it.hash == h4 }); assertEquals(2, games.size)
    }
    @Test fun achievements_merge_session_journal_and_confirmed_and_hide_unknown_emulator() = runTest {
        val store = FakeRaStore(); seed(store)
        val list = RaLibraryView.achievements(store, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), h1, "me")
        assertEquals(listOf(1 to RaAchievementState.UNLOCKED, 2 to RaAchievementState.PENDING), list.map { it.id to it.state })
        assertEquals(100L, list[0].unlockedAt); assertEquals(200L, list[1].unlockedAt); assertEquals(5f, list[1].rarity)
    }
    @Test fun a_pointer_to_a_game_no_longer_indexed_is_still_listed_by_its_stored_title() = runTest {
        val store = FakeRaStore(); seed(store)
        val games = RaLibraryView.games(store, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), null, "me")
        assertEquals(listOf("Game (m3u)", "Other"), games.map { it.title })
    }

    @Test fun a_games_console_is_its_library_folder_else_its_pointer_tag_else_retroachievements_console() = runTest {
        val store = FakeRaStore(); seed(store)
        val h4 = "d".repeat(32); val h5 = "e".repeat(32); val h6 = "f".repeat(32)
        store.put(RaPaths.sets(h4), setsJson(8, title = "Gone", consoleId = 3, achievements = """{"ID":1}"""))
        store.put(RaPaths.game(h4), RaJson.json.encodeToString(RaGamePointer.serializer(), RaGamePointer("SUPA", "home", "Roms/SUPA", "Gone.sfc", "Gone")))
        store.put(RaPaths.sets(h5), setsJson(9, title = "Pointerless", consoleId = RaConsoles.GAMEBOY, achievements = """{"ID":1}"""))
        store.put(RaPaths.sets(h6), setsJson(10, title = "Stranger", consoleId = 9999, achievements = """{"ID":1}"""))
        val games = RaLibraryView.games(store, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), index, "me").associateBy { it.title }
        assertEquals("Sony PlayStation" to "PS", games.getValue("Other").let { it.console to it.consoleTag })        // the library's folder
        assertEquals("Super Nintendo ES" to "SUPA", games.getValue("Gone").let { it.console to it.consoleTag })     // not indexed: its pointer's tag
        assertEquals("Game Boy" to "GB", games.getValue("Pointerless").let { it.console to it.consoleTag })         // no pointer: RA's console
        assertEquals(RaConsoleGroups.OTHER to null, games.getValue("Stranger").let { it.console to it.consoleTag })
    }

    // The browser reads per cached hash; on a phone each read crosses the shared storage, so none is spent twice.
    @Test fun the_game_list_reads_an_unrecognised_hash_twice_at_most_and_a_session_once_per_game() = runTest {
        val store = FakeRaStore(); seed(store)
        val unknown = "9".repeat(32)
        store.put(RaPaths.gameId(unknown), """{"GameID":0}""")
        RaLibraryView.games(store, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), index, "me")   // builds the summaries
        store.reads.clear()
        RaLibraryView.games(store, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), index, "me")
        assertTrue(store.reads.count { it.contains(unknown) } <= 1, "${store.reads.filter { it.contains(unknown) }}")   // its summary only (the sets checked by existence)
        assertEquals(1, store.reads.count { it == RaPaths.session(42) })   // two discs, one game: one session read
    }

    @Test fun hardcore_unlocks_count_and_a_prefetched_time_of_zero_shows_no_date() = runTest {
        val store = FakeRaStore()
        store.put(RaPaths.sets(h1), setsJson(42, title = "Game", achievements = """{"ID":1},{"ID":2},{"ID":3}"""))
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[{"ID":1,"When":0}],"HardcoreUnlocks":[{"ID":2,"When":100}]}""")
        val list = RaLibraryView.achievements(store, RaJournal(store, RaPaths.JOURNAL), RaJournal(store, RaPaths.CONFIRMED), h1, "me")
        assertEquals(listOf(RaAchievementState.UNLOCKED, RaAchievementState.UNLOCKED, RaAchievementState.LOCKED), list.map { it.state })
        assertEquals(listOf(null, 100L, null), list.map { it.unlockedAt })
    }

    @Test fun menu_progress_reads_the_compact_summary_not_the_sets() = runTest {
        val store = FakeRaStore(); seed(store)
        val journal = RaJournal(store, RaPaths.JOURNAL); val confirmed = RaJournal(store, RaPaths.CONFIRMED)
        val first = RaLibraryView.progressByPath(store, journal, confirmed, "me")   // builds the summaries
        assertTrue(store.exists(RaPaths.summary(h1)))
        store.reads.clear()
        assertEquals(first, RaLibraryView.progressByPath(store, journal, confirmed, "me"))
        assertTrue(store.reads.none { it.endsWith("/sets.json") })
        assertEquals(RaProgress(2, 2, null), first[GamePath.of(GameRef("home", "Roms/PS", "Game.m3u"))])
        RaLibraryView.games(store, journal, confirmed, index, "me")
        assertTrue(store.reads.none { it.endsWith("/sets.json") })
    }
    @Test fun a_new_set_reply_replaces_the_summary() = runTest {
        val store = FakeRaStore(); seed(store)
        val journal = RaJournal(store, RaPaths.JOURNAL); val confirmed = RaJournal(store, RaPaths.CONFIRMED)
        RaLibraryView.progressByPath(store, journal, confirmed, "me")
        RaSetsCache.write(store, h3, setsJson(7, title = "Other", achievements = """{"ID":9,"Title":"Nine"},{"ID":10,"Title":"Ten"}"""))
        val other = RaLibraryView.progressByPath(store, journal, confirmed, "me")[GamePath.of(GameRef("home", "Roms/PS", "Other.cue"))]
        assertEquals(RaProgress(1, 2, "Ten"), other)
        RaSetsCache.delete(store, h3)
        assertTrue(!store.exists(RaPaths.summary(h3)))
    }
}
