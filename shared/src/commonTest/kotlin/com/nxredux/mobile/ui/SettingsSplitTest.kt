package com.nxredux.mobile.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.achievements.RaGameView
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.options.EmulatorPage
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings splits into categories | options on a wide landscape window (task S3). */
class SettingsSplitTest {
    @Test fun wideLandscapeWindowsSplit() {
        assertTrue(settingsSplit(DpSize(840.dp, 360.dp)))   // Flip, 21:9
        assertTrue(settingsSplit(DpSize(960.dp, 432.dp)))   // 20:9 phone
        assertTrue(settingsSplit(DpSize(751.dp, 475.dp)))
        assertTrue(settingsSplit(DpSize(933.dp, 704.dp)))   // the unfolded Fold, landscape
    }

    @Test fun portraitAndNarrowWindowsKeepOnePage() {
        assertFalse(settingsSplit(DpSize(360.dp, 840.dp)))
        assertFalse(settingsSplit(DpSize(704.dp, 933.dp)))  // the Fold, portrait
        assertFalse(settingsSplit(DpSize(560.dp, 360.dp)))  // landscape but under 600 dp wide
        assertFalse(settingsSplit(DpSize(620.dp, 520.dp)))  // barely landscape
        assertFalse(settingsSplit(DpSize.Zero))             // not measured yet
    }

    @Test fun theMenuIsTheLeftPaneAndEveryPageTheRight() {
        assertEquals(SettingsPane.LEFT, settingsPane(Screen.SettingsMenu(SettingsSection.entries)))
        assertEquals(SettingsPane.RIGHT, settingsPane(Screen.Settings(SettingsSection.APPEARANCE, emptyList())))
        assertEquals(SettingsPane.RIGHT, settingsPane(Screen.Settings(SettingsSection.MAIN_MENU, emptyList())))
        assertEquals(SettingsPane.RIGHT, settingsPane(Screen.EmulatorList(emptyList())))
        assertEquals(SettingsPane.RIGHT, settingsPane(Screen.EmulatorConsoles("gpsp", "gpSP", listOf("GBA"))))
        val page = EmulatorPage("GBA", "gpsp", "gpSP", emptyList(), emptyList(), forGame = false, gameFileExists = false)
        assertEquals(SettingsPane.RIGHT, settingsPane(Screen.EmulatorOptions(page, emptyList())))
        // A game's own Game Settings (from its menu) is drawn whole: it is not under a category.
        val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList())
        val game = Listed(gba, GameEntry(ContentRef("r/a.gba"), "a.gba", "A", folder = ContentRef("r"), sourceId = "home", relativeDir = "Roms/GBA"))
        assertNull(settingsPane(Screen.EmulatorOptions(page, emptyList(), game = game)))
        assertNull(settingsPane(Screen.Systems(emptyList())))
        assertNull(settingsPane(Screen.RaSettings(emptyList())))   // a Tools page, not a Settings category
        assertNull(settingsPane(Screen.Loading))
    }

    @Test fun aGamesOwnSettingsIsAModalAndNeverAPane() {
        val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList())
        val game = Listed(gba, GameEntry(ContentRef("r/a.gba"), "a.gba", "A", folder = ContentRef("r"), sourceId = "home", relativeDir = "Roms/GBA"))
        val page = EmulatorPage("GBA", "gpsp", "gpSP", emptyList(), emptyList(), forGame = true, gameFileExists = false)
        val gameSettings = Screen.EmulatorOptions(page, emptyList(), game = game)
        val consoleWide = Screen.EmulatorOptions(page.copy(forGame = false), emptyList())
        assertTrue(gameSettingsModal(gameSettings))
        assertTrue(gameSettingsModal(gameSettings.copy(category = "video")))   // its sub-pages open inside the modal
        assertFalse(gameSettingsModal(consoleWide))
        assertFalse(gameSettingsModal(Screen.Settings(SettingsSection.APPEARANCE, emptyList())))
        val wide = DpSize(933.dp, 704.dp)
        assertFalse(settingsSplitView(wide, gameSettings))                     // drawn the same on every window
        assertTrue(settingsSplitView(wide, consoleWide))                       // Settings -> Library's page: the right pane
        assertTrue(settingsSplitView(wide, Screen.SettingsMenu(SettingsSection.entries)))
        assertFalse(settingsSplitView(DpSize(704.dp, 933.dp), consoleWide))    // portrait: one page
        assertFalse(settingsSplitView(wide, Screen.Systems(emptyList())))
    }

    @Test fun theRetroAchievementsLevelsSitBesideTheirParentOnTheSameWindows() {
        val ra = Screen.RaSettings(emptyList())
        val game = RaGameView("h", 1, "G", null, 0, 1, 0)
        val achievement = com.nxredux.mobile.achievements.RaAchievement(1, "A", "", 5, "", com.nxredux.mobile.achievements.RaAchievementState.LOCKED, null, null, 0f, 0f, null)
        val levels = listOf(ra, ra.copy(signingIn = true), Screen.RaGames(emptyList()), Screen.RaGame(game, emptyList()), Screen.RaAchievement(game, achievement))
        for (s in levels) {
            assertTrue(raSplitView(DpSize(933.dp, 704.dp), s))          // the unfolded Fold, landscape
            assertTrue(raSplitView(DpSize(840.dp, 360.dp), s))
            assertFalse(raSplitView(DpSize(704.dp, 933.dp), s))         // portrait: one page
            assertFalse(raSplitView(DpSize(560.dp, 360.dp), s))         // the cover screen and narrow windows
            assertTrue(twoPaneView(DpSize(933.dp, 704.dp), s))
            assertFalse(twoPaneView(DpSize(560.dp, 360.dp), s))
        }
        assertFalse(raSplitView(DpSize(933.dp, 704.dp), Screen.RetroAchievements()))   // the page is drawn whole
        assertFalse(raSplitView(DpSize(933.dp, 704.dp), Screen.Settings(SettingsSection.SYSTEM, emptyList())))
        assertTrue(twoPaneView(DpSize(933.dp, 704.dp), Screen.Settings(SettingsSection.SYSTEM, emptyList())))
        assertFalse(twoPaneView(DpSize(933.dp, 704.dp), Screen.RetroAchievements()))
        // The page's row the left pane highlights: Settings beside the settings, Achievements beside the games list.
        assertEquals(RaAction.SETTINGS, RaAction.entries[raPageRow(ra)!!])
        assertEquals(RaAction.ACHIEVEMENTS, RaAction.entries[raPageRow(Screen.RaGames(emptyList()))!!])
        assertNull(raPageRow(Screen.RaGame(game, emptyList())))
    }

    @Test fun aGamesSessionsSitBesideTheTrackerOnTheSameWindows() {
        val game = com.nxredux.mobile.gametime.GameTotal(1, "Tetris", "GB", "/Roms/GB/Tetris.gb", 60_000, 2, 0)
        val s = Screen.GameTimeSessions(game, com.nxredux.mobile.gametime.GameSessions(emptyList()))
        assertTrue(gameTimeSplitView(DpSize(933.dp, 704.dp), s)); assertTrue(twoPaneView(DpSize(933.dp, 704.dp), s))
        assertFalse(gameTimeSplitView(DpSize(704.dp, 933.dp), s))           // portrait: one page
        assertFalse(gameTimeSplitView(DpSize(560.dp, 360.dp), s))           // narrow: one page
        assertFalse(gameTimeSplitView(DpSize(933.dp, 704.dp), Screen.GameTime(emptyList(), 0L)))   // the list is drawn whole, no preview
    }

    @Test fun theArtworkPagesSitBesideTheirParentOnTheSameWindows() {
        val settings = Screen.ArtworkSettings(emptyList())
        val levels = listOf(
            Screen.ArtworkConsoles(emptyList()), Screen.ArtworkProgress(com.nxredux.mobile.scraper.ScrapeState()), settings,
            settings.copy(signingIn = true), Screen.ArtworkReset(emptyList()),
        )
        for (s in levels) {
            assertTrue(artworkSplitLevel(s))
            assertTrue(artworkSplitView(DpSize(933.dp, 704.dp), s))      // the unfolded Fold, landscape
            assertTrue(artworkSplitView(DpSize(840.dp, 360.dp), s))
            assertFalse(artworkSplitView(DpSize(704.dp, 933.dp), s))     // portrait: one page
            assertFalse(artworkSplitView(DpSize(560.dp, 360.dp), s))     // the cover screen and narrow windows
            assertTrue(twoPaneView(DpSize(933.dp, 704.dp), s))           // the watermark centres on the right pane
            assertFalse(twoPaneView(DpSize(560.dp, 360.dp), s))
        }
        val page = Screen.Artwork(emptyList(), emptyList())
        assertFalse(artworkSplitLevel(page)); assertFalse(artworkSplitView(DpSize(933.dp, 704.dp), page))   // the page is drawn whole
        assertFalse(twoPaneView(DpSize(933.dp, 704.dp), page))
    }

    @Test fun theWatermarkCentresOnTheRightPaneWhenSplit() {
        assertEquals(0f, settingsMarkShift(split = false))
        assertEquals(0.175f, settingsMarkShift(split = true), 1e-6f)   // 0.35 + 0.65 / 2 - 0.5 of the width
    }

    @Test fun theAndroidGamesPickerIsALibraryPage() {
        val picker = Screen.ToolApps(emptyList(), AppPick.GAMES)
        assertEquals(SettingsPane.RIGHT, settingsPane(picker))
        assertEquals(SettingsSection.LIBRARY, settingsCategoryOf(picker))
    }

    // The app drawer is a tool (Tools → Apps), never a Settings pane.
    @Test fun theAppDrawerIsNoSettingsPage() {
        val drawer = Screen.ToolApps(emptyList(), AppPick.TOOLS)
        assertNull(settingsPane(drawer))
        assertNull(settingsCategoryOf(drawer))
    }

    @Test fun eachPageBelongsToItsTopCategory() {
        assertEquals(SettingsSection.MAIN_MENU, settingsCategoryOf(Screen.Settings(SettingsSection.MAIN_MENU, emptyList())))   // its own category
        assertEquals(SettingsSection.LIBRARY, settingsCategoryOf(Screen.Settings(SettingsSection.LIBRARY, emptyList())))
        assertEquals(SettingsSection.EMULATORS, settingsCategoryOf(Screen.Settings(SettingsSection.DEFAULT_EMULATORS, emptyList())))
        assertEquals(SettingsSection.EMULATORS, settingsCategoryOf(Screen.Settings(SettingsSection.EMULATORS, emptyList())))
        assertEquals(SettingsSection.SYSTEM, settingsCategoryOf(Screen.Settings(SettingsSection.SYSTEM, emptyList())))
        assertEquals(SettingsSection.EMULATORS, settingsCategoryOf(Screen.EmulatorList(emptyList())))
        assertEquals(SettingsSection.EMULATORS, settingsCategoryOf(Screen.EmulatorConsoles("gpsp", "gpSP", emptyList())))
        assertNull(settingsCategoryOf(Screen.SettingsMenu(SettingsSection.entries)))
        assertEquals(SettingsSection.LIBRARY, settingsCategoryOf(Screen.Settings(SettingsSection.SAVES, emptyList())))   // Saves under Library
        assertEquals(listOf(SettingsSection.LIBRARY, SettingsSection.EMULATORS, SettingsSection.APPEARANCE, SettingsSection.MAIN_MENU, SettingsSection.CONTROLS, SettingsSection.SYSTEM), settingsCategories())   // System last
        assertEquals(listOf(SettingsSection.LIBRARY, SettingsSection.EMULATORS, SettingsSection.APPEARANCE, SettingsSection.MAIN_MENU, SettingsSection.CONTROLS, SettingsSection.SECOND_SCREEN, SettingsSection.SYSTEM), settingsCategories(secondScreen = true))
    }
}
