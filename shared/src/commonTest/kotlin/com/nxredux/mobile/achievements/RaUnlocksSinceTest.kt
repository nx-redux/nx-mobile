package com.nxredux.mobile.achievements

import com.nxredux.mobile.settings.FakeAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class RaUnlocksSinceTest {

    private val h1 = "a".repeat(32); private val h2 = "b".repeat(32)
    private val pathA = "/Roms/Game Boy Advance (GBA)/A.gba"; private val pathB = "/Roms/Game Boy (GB)/B.gb"
    private val store = FakeRaStore()
    private val journal = RaJournal(store, RaPaths.JOURNAL)
    private val confirmed = RaJournal(store, RaPaths.CONFIRMED)

    private fun seed(unlocksA: String = "", unlocksB: String = "") {
        store.put(RaPaths.sets(h1), setsJson(42, title = "A", achievements = """{"ID":1,"Title":"First"},{"ID":2,"Title":"Second"},{"ID":3,"Title":"Third"}"""))
        store.put(RaPaths.game(h1), RaJson.json.encodeToString(RaGamePointer.serializer(), RaGamePointer("GBA", "home", "Roms/Game Boy Advance (GBA)", "A.gba", "A")))
        store.put(RaPaths.session(42), """{"Success":true,"Unlocks":[$unlocksA]}""")
        store.put(RaPaths.sets(h2), setsJson(7, title = "B", achievements = """{"ID":9,"Title":"Nine"},{"ID":10,"Title":"Ten"}"""))
        store.put(RaPaths.game(h2), RaJson.json.encodeToString(RaGamePointer.serializer(), RaGamePointer("GB", "home", "Roms/Game Boy (GB)", "B.gb", "B")))
        store.put(RaPaths.session(7), """{"Success":true,"Unlocks":[$unlocksB]}""")
    }

    private suspend fun since(sinceSec: Long, paths: Set<String>) = RaLibraryView.unlocksSince(store, journal, confirmed, "user", sinceSec, paths)

    @Test fun anUnlockInsideTheWindowCountsAndOneBeforeItDoesNot() = runTest {
        seed(unlocksA = """{"ID":1,"When":999},{"ID":2,"When":1000}""")   // 999 before, 1000 at the start (inclusive)
        journal.append(RaUnlock("user", 3, h1, 1500L))                     // pending, inside
        assertEquals(2, since(1000, setOf(pathA)))
    }

    @Test fun confirmedJournalUnlocksCountByTheirTime() = runTest {
        seed()
        confirmed.append(RaUnlock("user", 1, h1, 2000L))
        confirmed.append(RaUnlock("user", 2, h1, 10L))
        assertEquals(1, since(1000, setOf(pathA)))
    }

    @Test fun theSameAchievementInPendingAndConfirmedCountsOnce() = runTest {
        seed()
        journal.append(RaUnlock("user", 1, h1, 2000L))
        confirmed.append(RaUnlock("user", 1, h1, 2001L))
        assertEquals(1, since(1000, setOf(pathA)))
    }

    @Test fun theSameAchievementInTheSessionAndAJournalCountsOnce() = runTest {
        seed(unlocksA = """{"ID":1,"When":2000}""")
        confirmed.append(RaUnlock("user", 1, h1, 2000L))
        journal.append(RaUnlock("user", 1, h1, 2000L))
        assertEquals(1, since(1000, setOf(pathA)))
    }

    @Test fun aGameWhosePathIsNotInPathsIsIgnored() = runTest {
        seed(unlocksA = """{"ID":1,"When":2000}""", unlocksB = """{"ID":9,"When":2000},{"ID":10,"When":2001}""")
        assertEquals(1, since(1000, setOf(pathA)))
        assertEquals(2, since(1000, setOf(pathB)))
        assertEquals(3, since(1000, setOf(pathA, pathB)))
        assertEquals(0, since(1000, emptySet()))
    }

    @Test fun siblingHashesSharingAGameIdCountAnUnlockOnce() = runTest {
        seed(unlocksA = """{"ID":1,"When":2000}""")
        val h3 = "c".repeat(32)   // disc 2 of A: same game id 42, same session and set
        store.put(RaPaths.sets(h3), setsJson(42, title = "A", achievements = """{"ID":1,"Title":"First"},{"ID":2,"Title":"Second"},{"ID":3,"Title":"Third"}"""))
        store.put(RaPaths.game(h3), RaJson.json.encodeToString(RaGamePointer.serializer(), RaGamePointer("GBA", "home", "Roms/Game Boy Advance (GBA)", "A (Disc 2).gba", "A")))
        assertEquals(1, since(1000, setOf(pathA, "/Roms/Game Boy Advance (GBA)/A (Disc 2).gba")))
    }

    @Test fun anIdTheGamesSetDoesNotHaveIsNotCounted() = runTest {
        seed()
        confirmed.append(RaUnlock("user", 9, h1, 2000L))   // B's id, attributed to A's hash
        assertEquals(0, since(1000, setOf(pathA)))
    }

    @Test fun signedOutTheControllerReturnsNull() = runTest {
        seed(unlocksA = """{"ID":1,"When":2000}""")
        val settings = FakeAppSettings()
        val c = RaController(fakePlatform(store), settings, { null }, this, now = { 0L })
        assertNull(c.unlocksSince(1_000_000L, setOf(pathA)))                              // no token, no user
        settings.raUsername = "user"
        assertNull(c.unlocksSince(1_000_000L, setOf(pathA)))                              // user but no token
        settings.raUsername = " "; settings.raToken = "tok"
        assertNull(c.unlocksSince(1_000_000L, setOf(pathA)))                              // token but blank user
    }

    @Test fun signedInTheControllerConvertsMillisecondsToSeconds() = runTest {
        seed(unlocksA = """{"ID":1,"When":999},{"ID":2,"When":1000}""")
        val settings = FakeAppSettings().apply { raUsername = "user"; raToken = "tok" }
        val c = RaController(fakePlatform(store), settings, { null }, this, now = { 0L })
        assertEquals(1, c.unlocksSince(1_000_000L, setOf(pathA)))
    }

    @Test fun aStoreFailureCountsZero() = runTest {
        seed(unlocksA = """{"ID":1,"When":2000}""")
        store.failList = true
        val settings = FakeAppSettings().apply { raUsername = "user"; raToken = "tok" }
        val c = RaController(fakePlatform(store), settings, { null }, this, now = { 0L })
        assertEquals(0, c.unlocksSince(1_000_000L, setOf(pathA)))
    }
}
