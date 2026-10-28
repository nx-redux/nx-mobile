package com.nxredux.mobile.achievements

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.PadButtons
import com.nxredux.mobile.settings.PadTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class RaUnlocksSinceTest {
    private class Settings : AppSettings {
        override var treeRef: ContentRef? = null; override var extraFolders: List<ExtraFolder> = emptyList(); override var defaultTags: Map<String, String> = emptyMap()
        override var padTheme = PadTheme.DEFAULT
        override var padButtons: PadButtons = PadButtons.DEFAULT
        override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap(); override var gameListStyle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_GAME_LIST_STYLE; override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap(); override var gameListOrientation = com.nxredux.mobile.settings.MenuStyles.DEFAULT_ORIENTATION; override var gameListVerticalAlignment = com.nxredux.mobile.settings.VerticalListAlignment.DEFAULT; override var toolApps: List<String> = emptyList()
        override var androidConsoleApps: List<String> = emptyList(); override var androidGamesAsked = false; override var androidPins: List<String> = emptyList()
        override var raEnabled = false; override var raUsername = ""; override var raToken: String? = null; override var raShowNotifications = true
        override var raNotificationSeconds = 3; override var raProgressSeconds = 1; override var raSortOrder = RaSortOrder.DEFAULT
        override var ssUsername = ""; override var ssPassword: String? = null; override var sgdbApiKey: String? = null; override var tgdbApiKey: String? = null; override var artReplaceExisting = false; override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showNetplayHint = true; override var menuPageTitle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = com.nxredux.mobile.settings.MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = com.nxredux.mobile.settings.AccentPreset.DEFAULT; override var secondaryAccent: com.nxredux.mobile.settings.SecondaryAccent = com.nxredux.mobile.settings.SecondaryAccent.DEFAULT
    }

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
        val settings = Settings()
        val c = RaController(fakePlatform(store), settings, { null }, this, now = { 0L })
        assertNull(c.unlocksSince(1_000_000L, setOf(pathA)))                              // no token, no user
        settings.raUsername = "user"
        assertNull(c.unlocksSince(1_000_000L, setOf(pathA)))                              // user but no token
        settings.raUsername = " "; settings.raToken = "tok"
        assertNull(c.unlocksSince(1_000_000L, setOf(pathA)))                              // token but blank user
    }

    @Test fun signedInTheControllerConvertsMillisecondsToSeconds() = runTest {
        seed(unlocksA = """{"ID":1,"When":999},{"ID":2,"When":1000}""")
        val settings = Settings().apply { raUsername = "user"; raToken = "tok" }
        val c = RaController(fakePlatform(store), settings, { null }, this, now = { 0L })
        assertEquals(1, c.unlocksSince(1_000_000L, setOf(pathA)))
    }

    @Test fun aStoreFailureCountsZero() = runTest {
        seed(unlocksA = """{"ID":1,"When":2000}""")
        store.failList = true
        val settings = Settings().apply { raUsername = "user"; raToken = "tok" }
        val c = RaController(fakePlatform(store), settings, { null }, this, now = { 0L })
        assertEquals(0, c.unlocksSince(1_000_000L, setOf(pathA)))
    }
}
