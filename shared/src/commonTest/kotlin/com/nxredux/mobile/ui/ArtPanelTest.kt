package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaSortOrder
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.scraper.ArtOption
import com.nxredux.mobile.scraper.ArtOptionsPort
import com.nxredux.mobile.scraper.ArtOptionsResult
import com.nxredux.mobile.scraper.ArtSlot
import com.nxredux.mobile.scraper.FetchResult
import com.nxredux.mobile.scraper.ItemStatus
import com.nxredux.mobile.scraper.SourceId
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuOrientation
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.PadButtons
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArtPanelTest {
    private class Settings : AppSettings {
        override var treeRef: ContentRef? = ContentRef("Home")
        override var onboardingDone: Boolean? = true
        override var onboardingStep: String? = null
        override var extraFolders: List<ExtraFolder> = emptyList()
        override var defaultTags: Map<String, String> = emptyMap()
        override var padTheme = PadTheme.DEFAULT
        override var padButtons: PadButtons = PadButtons.DEFAULT
        override var menuStyles: Map<MenuCategory, MenuStyle> = emptyMap()
        override var gameListStyle: MenuStyle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_GAME_LIST_STYLE
        override var menuOrientations: Map<MenuCategory, MenuOrientation> = emptyMap()
        override var gameListOrientation: MenuOrientation = com.nxredux.mobile.settings.MenuStyles.DEFAULT_ORIENTATION
        override var gameListVerticalAlignment: com.nxredux.mobile.settings.VerticalListAlignment = com.nxredux.mobile.settings.VerticalListAlignment.DEFAULT
        override var toolApps: List<String> = emptyList()
        override var androidConsoleApps: List<String> = emptyList()
        override var androidGamesAsked = false
        override var androidPins: List<String> = emptyList()
        override var raEnabled = false
        override var raUsername = ""
        override var raToken: String? = null
        override var raShowNotifications = true
        override var raNotificationSeconds = 3
        override var raProgressSeconds = 1
        override var raSortOrder = RaSortOrder.DEFAULT
        override var ssUsername = ""
        override var ssPassword: String? = null
        override var sgdbApiKey: String? = null
        override var tgdbApiKey: String? = null
        override var artReplaceExisting = false
        override var artBox2d = false
        override var artWheel = false
        override var artMix = false
        override var consoleArt = com.nxredux.mobile.settings.ConsoleArt.DEFAULT; override var gameListArt = com.nxredux.mobile.settings.GameListArt.DEFAULT; override var backdropArt = com.nxredux.mobile.settings.BackdropArt.DEFAULT; override var showHomeTab = true; override var showConsolesTab = true; override var showCollectionsTab = true; override var showToolsTab = true; override var showNewShelf = true; override var showNotStartedShelf = true; override var showNetplayHint = true; override var menuPageTitle = com.nxredux.mobile.settings.MenuStyles.DEFAULT_PAGE_TITLE; override var menuButtonHints = com.nxredux.mobile.settings.MenuStyles.DEFAULT_BUTTON_HINTS; override var menuExtraInfo = com.nxredux.mobile.settings.MenuStyles.DEFAULT_EXTRA_INFO; override var menuHomeStats = true; override var gameListLayoutOptions = true; override var gameListAchievementsPanel = true; override var padVibration = com.nxredux.mobile.settings.PadVibration.DEFAULT; override var gameRumble = com.nxredux.mobile.settings.GameRumble.DEFAULT; override var accent = com.nxredux.mobile.settings.AccentPreset.DEFAULT; override var secondaryAccent: com.nxredux.mobile.settings.SecondaryAccent = com.nxredux.mobile.settings.SecondaryAccent.DEFAULT
    }

    /** Records every call; [options] is what artOptions answers, [fetchGate] holds fetchGame until completed. */
    private class FakePort(var options: List<ArtOption>, var currentKey: String? = null) : ArtOptionsPort {
        val calls = mutableListOf<String>()
        var fetchGate: CompletableDeferred<Unit>? = null
        val picked = mutableListOf<ArtOption>()
        override suspend fun fetchGame(gamePath: String, replaceExisting: Boolean, wide: Boolean): FetchResult {
            calls += "fetch wide=$wide"
            fetchGate?.await()
            return FetchResult(ItemStatus.DONE)
        }
        override suspend fun artOptions(gamePath: String, replaceExisting: Boolean, searchAgain: Boolean, autoSearch: Boolean): ArtOptionsResult {
            calls += "options searchAgain=$searchAgain" + (if (autoSearch) "" else " autoSearch=false")
            return ArtOptionsResult(options, currentKey)
        }
        override suspend fun optionImage(gamePath: String, option: ArtOption): ContentRef? =
            if (option.url.endsWith("bad")) null else ContentRef("file:///cache/${option.assetId}")
        override suspend fun pickOption(gamePath: String, option: ArtOption): Boolean {
            picked += option; currentKey = option.key; return true
        }
    }

    private val game: Listed = run {
        val s = SystemFolder("GB", "GB", CoreRegistry.forTag("GB"), emptyList())
        val file = "Tetris.gb"
        Listed(s, GameEntry(ContentRef("Home/Roms/GB/$file"), file, Names.stripExtension(file), folder = ContentRef("Home/Roms/GB"), sourceId = GamePath.HOME, relativeDir = "Roms/GB"))
    }

    private fun opt(slot: ArtSlot, source: String, id: String, note: String? = null) =
        ArtOption(slot, "https://m/$id", source, gameId = "g", assetId = id, note = note)

    private val mixed = listOf(
        opt(ArtSlot.HERO, SourceId.SGDB, "h1"),
        opt(ArtSlot.SCREENSHOT, SourceId.SS, "s1", note = "us"),
        opt(ArtSlot.GRID, SourceId.SGDB, "g1"),
        opt(ArtSlot.SCREENSHOT, SourceId.TGDB, "s2"),
        opt(ArtSlot.SCREENSHOT, SourceId.LIBRETRO, "s3"),
    )

    @Test fun fetch_first_runs_wide_fetch_then_lists_screenshot_options() = runTest {
        val port = FakePort(mixed).apply { fetchGate = CompletableDeferred() }
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = true)
        runCurrent()
        assertEquals(ArtPanelPhase.FETCHING, assertNotNull(c.panel.value).phase)
        assertEquals("Tetris", c.panel.value!!.title)
        assertEquals(game.game.path, c.panel.value!!.gamePath)
        port.fetchGate!!.complete(Unit)
        advanceUntilIdle()
        val p = assertNotNull(c.panel.value)
        assertEquals(ArtPanelPhase.READY, p.phase)
        assertEquals(listOf("fetch wide=true", "options searchAgain=false autoSearch=false"), port.calls)   // no second search
        assertEquals(listOf("s1", "s2", "s3", "g1", "h1"), p.tiles.map { it.option.assetId })
        assertEquals(listOf("ScreenScraper · us", "TheGamesDB", "libretro", "SteamGridDB · Grid", "SteamGridDB · Hero"), p.tiles.map { it.label })
        assertEquals(ContentRef("file:///cache/s1"), p.tiles[0].image)   // the previews loaded
        assertNull(p.message)
    }

    @Test fun change_art_lists_without_a_fetch_and_marks_a_failed_preview() = runTest {
        val port = FakePort(listOf(opt(ArtSlot.SCREENSHOT, SourceId.SS, "s1"), ArtOption(ArtSlot.SCREENSHOT, "https://m/bad", SourceId.TGDB, assetId = "s2")), currentKey = ArtOption(ArtSlot.SCREENSHOT, "https://m/bad", SourceId.TGDB, assetId = "s2").key)
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = false)
        assertEquals(ArtPanelPhase.LOADING, c.panel.value!!.phase)
        advanceUntilIdle()
        val p = assertNotNull(c.panel.value)
        assertEquals(listOf("options searchAgain=false"), port.calls)
        assertEquals(listOf(false, true), p.tiles.map { it.failed })
        assertEquals(1, p.focus)   // on the picture in use
    }

    @Test fun a_picks_focused_tile_and_marks_it() = runTest {
        val port = FakePort(mixed)
        val picked = mutableListOf<String>()
        val c = ArtPanelController(this, port, Settings(), onPicked = { picked += it })
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        c.onButton(NxButton.RIGHT); c.onButton(NxButton.LEFT); c.onButton(NxButton.DOWN)   // tile 2: the second row's left
        assertEquals(2, c.panel.value!!.focus)
        c.onButton(NxButton.A)
        advanceUntilIdle()
        val tile2 = c.panel.value!!.tiles[2].option
        assertEquals(listOf(tile2), port.picked)
        assertEquals(tile2.key, c.panel.value!!.currentKey)
        assertEquals(listOf(game.game.path), picked)
        assertNotNull(c.panel.value)   // stays open
        c.tap(0)                        // a tap picks too
        advanceUntilIdle()
        assertEquals(c.panel.value!!.tiles[0].option.key, c.panel.value!!.currentKey)
        assertEquals(0, c.panel.value!!.focus)
    }

    @Test fun picking_the_current_picture_goes_through() = runTest {
        val port = FakePort(mixed, currentKey = mixed[1].key)   // the ScreenScraper screenshot, tile 0
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        assertEquals(0, c.panel.value!!.focus)
        c.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals(listOf(mixed[1]), port.picked)   // the controller records it as the pick
    }

    @Test fun search_again_reloads_with_search_again_true() = runTest {
        val port = FakePort(mixed)
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        repeat(4) { c.onButton(NxButton.DOWN) }   // rows 0, 1, 2, then Search again
        assertEquals(mixed.size, c.panel.value!!.focus)
        port.options = mixed.take(1)
        c.onButton(NxButton.A)
        assertEquals(ArtPanelPhase.LOADING, c.panel.value!!.phase)
        advanceUntilIdle()
        assertEquals(listOf("options searchAgain=false", "options searchAgain=true"), port.calls)
        assertEquals(1, c.panel.value!!.tiles.size)
        assertEquals("Only one picture found", c.panel.value!!.message)
        c.tap(1)   // a tap on Search again
        advanceUntilIdle()
        assertEquals("options searchAgain=true", port.calls.last())
        assertEquals(3, port.calls.size)
    }

    @Test fun a_grid_and_hero_with_one_asset_id_are_two_tiles() = runTest {
        val port = FakePort(listOf(opt(ArtSlot.GRID, SourceId.SGDB, "77"), opt(ArtSlot.HERO, SourceId.SGDB, "77")))
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        val p = assertNotNull(c.panel.value)
        assertEquals(listOf("SteamGridDB · Grid", "SteamGridDB · Hero"), p.tiles.map { it.label })
        assertEquals(2, p.tiles.map { it.option.key }.toSet().size)
    }

    @Test fun empty_says_none_found() = runTest {
        val c = ArtPanelController(this, FakePort(emptyList()), Settings(), onPicked = {})
        c.open(game, fetchFirst = true)
        advanceUntilIdle()
        val p = assertNotNull(c.panel.value)
        assertEquals(ArtPanelPhase.READY, p.phase)
        assertEquals("No pictures found", p.message)
        assertEquals(0, p.focus)
        assertTrue(p.tiles.isEmpty())
    }

    @Test fun b_closes() = runTest {
        val port = FakePort(mixed).apply { fetchGate = CompletableDeferred() }
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = true)
        runCurrent()
        c.onButton(NxButton.B)   // while fetching too
        assertNull(c.panel.value)
        port.fetchGate!!.complete(Unit)
        advanceUntilIdle()
        assertNull(c.panel.value)   // the cancelled load does not bring it back
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        c.onButton(NxButton.B)
        assertNull(c.panel.value)
    }
}
