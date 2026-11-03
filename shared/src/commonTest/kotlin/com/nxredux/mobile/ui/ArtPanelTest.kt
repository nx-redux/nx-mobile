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
import com.nxredux.mobile.scraper.fileSlot
import com.nxredux.mobile.scraper.FetchResult
import com.nxredux.mobile.scraper.ItemStatus
import com.nxredux.mobile.scraper.OptionImage
import com.nxredux.mobile.scraper.SourceId
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.settings.GameListArt
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
    private class FakePort(var options: List<ArtOption>, var currentKey: String? = null, var currentKeys: Map<ArtSlot, String>? = null) : ArtOptionsPort {
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
            return ArtOptionsResult(options, currentKeys ?: currentKey?.let { k -> options.firstOrNull { it.key == k }?.let { mapOf(it.slot.fileSlot to k) } }.orEmpty())
        }
        /** A link ending "bad" fails (offline); one ending "gone" is a dead link the controller drops. */
        override suspend fun optionImage(gamePath: String, option: ArtOption): OptionImage = when {
            option.url.endsWith("bad") -> OptionImage(null)
            option.url.endsWith("gone") -> OptionImage(null, dropped = true)
            else -> OptionImage(ContentRef("file:///cache/${option.assetId}"))
        }
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
        opt(ArtSlot.SCREENSHOT, SourceId.SS, "s1", note = "us"),
        opt(ArtSlot.GRID, SourceId.SGDB, "g1"),
        opt(ArtSlot.SCREENSHOT, SourceId.TGDB, "s2"),
        opt(ArtSlot.SCREENSHOT, SourceId.LIBRETRO, "s3"),
        opt(ArtSlot.HERO, SourceId.SGDB, "h1"),
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
        assertEquals(listOf("s1", "s2", "s3", "h1", "g1"), p.tiles.map { it.option.assetId })   // the screenshots (a hero is one), then the 2D boxes
        assertEquals(listOf("ScreenScraper · us", "TheGamesDB", "libretro", "SteamGridDB · Hero", "SteamGridDB · Grid"), p.tiles.map { it.label })
        assertEquals(listOf("Screenshot", "2D box"), p.sections.map { it.title })
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

    @Test fun a_dead_link_leaves_the_panel_an_offline_one_stays_failed() = runTest {
        val bad = ArtOption(ArtSlot.SCREENSHOT, "https://m/bad", SourceId.TGDB, assetId = "s2")
        val gone = ArtOption(ArtSlot.SCREENSHOT, "https://m/gone", SourceId.LIBRETRO, assetId = "s3")
        val port = FakePort(listOf(opt(ArtSlot.SCREENSHOT, SourceId.SS, "s1"), gone, bad))
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        val p = assertNotNull(c.panel.value)
        assertEquals(listOf("s1", "s2"), p.tiles.map { it.option.assetId })
        assertEquals(listOf(false, true), p.tiles.map { it.failed })   // "Couldn't load" for the offline one
        c.onButton(NxButton.RIGHT)
        assertEquals(1, c.panel.value!!.focus)
        c.onButton(NxButton.DOWN)
        assertTrue(c.panel.value!!.onSearchAgain)
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
        assertEquals(mapOf(ArtSlot.SCREENSHOT to tile2.key), c.panel.value!!.currentKeys)
        assertNull(c.panel.value!!.question)   // a screenshot asks nothing
        assertEquals(listOf(game.game.path), picked)
        assertNotNull(c.panel.value)   // stays open
        c.tap(0)                        // a tap picks too
        advanceUntilIdle()
        assertEquals(c.panel.value!!.tiles[0].option.key, c.panel.value!!.currentKeys[ArtSlot.SCREENSHOT])
        assertEquals(0, c.panel.value!!.focus)
    }

    @Test fun picking_the_current_picture_goes_through() = runTest {
        val port = FakePort(mixed, currentKey = mixed[0].key)   // the ScreenScraper screenshot, tile 0
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        assertEquals(0, c.panel.value!!.focus)
        c.onButton(NxButton.A)
        advanceUntilIdle()
        assertEquals(listOf(mixed[0]), port.picked)   // the controller records it as the pick
    }

    @Test fun search_again_reloads_with_search_again_true() = runTest {
        val port = FakePort(mixed)
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        repeat(4) { c.onButton(NxButton.DOWN) }   // the screenshots' rows, the 2D box, then Search again (it stays)
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
        assertEquals(listOf("SteamGridDB · Hero", "SteamGridDB · Grid"), p.tiles.map { it.label })
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

    @Test fun sectionsInOrder() {
        val given = listOf(
            opt(ArtSlot.WHEEL, SourceId.SS, "w1"), opt(ArtSlot.BOX_3D, SourceId.SS, "b3"), opt(ArtSlot.GRID, SourceId.SGDB, "g1"),
            opt(ArtSlot.HERO, SourceId.SGDB, "h1"), opt(ArtSlot.BOX_2D, SourceId.TGDB, "b2"), opt(ArtSlot.SCREENSHOT, SourceId.SS, "s1"),
        )
        val sections = artSections(given.map { ArtTile(it, artTileLabel(it), null, false) })
        assertEquals(listOf(ArtSlot.SCREENSHOT, ArtSlot.BOX_2D, ArtSlot.BOX_3D, ArtSlot.WHEEL), sections.map { it.slot })
        assertEquals(listOf("Screenshot", "2D box", "3D box", "Wheel"), sections.map { it.title })
        assertEquals(listOf(2, 3, 3, 2), sections.map { it.columns })
        assertEquals(listOf("h1", "s1"), sections[0].tiles.map { it.option.assetId })   // a hero is a screenshot
        assertEquals(listOf("g1", "b2"), sections[1].tiles.map { it.option.assetId })   // a grid is a 2D box
        assertEquals(listOf(4f / 3f, 3f / 4f, 3f / 4f, 2f), sections.map { it.aspect })
        assertEquals(listOf(ArtSlot.WHEEL), artSections(given.take(1).map { ArtTile(it, "", null, false) }).map { it.slot })   // empty ones left out
    }

    @Test fun moveAcrossSections() {
        fun tiles(n: Int, slot: ArtSlot) = List(n) { ArtTile(opt(slot, SourceId.SS, "$slot$it"), "", null, false) }
        val sections = listOf(
            ArtSection(ArtSlot.SCREENSHOT, "Screenshot", 2, tiles(3, ArtSlot.SCREENSHOT)),
            ArtSection(ArtSlot.BOX_2D, "2D box", 3, tiles(4, ArtSlot.BOX_2D)),
        )
        assertEquals(3, artPanelMove(sections, 2, NxButton.DOWN))
        assertEquals(6, artPanelMove(sections, 4, NxButton.DOWN))   // no tile below column 1: the row's last
        assertEquals(7, artPanelMove(sections, 6, NxButton.DOWN))   // Search again
        assertEquals(7, artPanelMove(sections, 7, NxButton.DOWN))
        assertEquals(2, artPanelMove(sections, 3, NxButton.UP))
        assertEquals(6, artPanelMove(sections, 7, NxButton.UP))
        assertEquals(2, artPanelMove(sections, 2, NxButton.RIGHT))
        assertEquals(1, artPanelMove(sections, 0, NxButton.RIGHT))
        assertEquals(0, artPanelMove(sections, 1, NxButton.LEFT))
        assertEquals(3, artPanelMove(sections, 3, NxButton.LEFT))
        assertEquals(0, artPanelMove(sections, 0, NxButton.UP))
        assertEquals(2, artPanelMove(sections, 5, NxButton.UP))     // the section above's last row: its nearest tile
        assertEquals(2, artPanelMove(sections, 1, NxButton.DOWN))   // no tile below column 1: the row's last
        // The nearest column across shapes: a 2-column row's right tile lands on a 3-column row's right one.
        val wide = listOf(sections[0].copy(tiles = sections[0].tiles.take(2)), sections[1])
        assertEquals(4, artPanelMove(wide, 1, NxButton.DOWN))
        assertEquals(2, artPanelMove(wide, 0, NxButton.DOWN))
        assertEquals(1, artPanelMove(wide, 4, NxButton.UP))
        assertEquals(0, artPanelMove(emptyList(), 0, NxButton.UP))
    }

    @Test fun questionRules() {
        assertEquals(LayoutQuestion(BackdropArt.BOX2D, alsoListArt = false, yesFocused = true), layoutQuestionFor(ArtSlot.BOX_2D, BackdropArt.BOX3D, GameListArt.SCREENSHOT))
        assertEquals(LayoutQuestion(BackdropArt.BOX2D, alsoListArt = true, yesFocused = true), layoutQuestionFor(ArtSlot.BOX_2D, BackdropArt.BOX2D, GameListArt.BOX3D))
        assertNull(layoutQuestionFor(ArtSlot.BOX_2D, BackdropArt.BOX2D, GameListArt.MIX))
        assertNull(layoutQuestionFor(ArtSlot.SCREENSHOT, BackdropArt.BOX3D, GameListArt.BOX2D))
        assertNull(layoutQuestionFor(ArtSlot.WHEEL, BackdropArt.WHEEL, GameListArt.WHEEL))
        assertEquals(LayoutQuestion(BackdropArt.WHEEL, alsoListArt = true, yesFocused = true), layoutQuestionFor(ArtSlot.WHEEL, BackdropArt.BOX3D, GameListArt.BOX2D))
        assertEquals(
            "Layouts → Backdrop art is set to 3D box art. Switch it to 2D box art?",
            layoutQuestionText(LayoutQuestion(BackdropArt.BOX2D, false, true), BackdropArt.BOX3D, GameListArt.SCREENSHOT),
        )
        assertEquals(
            "Layouts → Backdrop art is set to Wheel. Switch it to 3D box art? List art too.",
            layoutQuestionText(LayoutQuestion(BackdropArt.BOX3D, true, true), BackdropArt.WHEEL, GameListArt.BOX2D),
        )
        // Backdrop art already matches: only List art is asked about.
        assertEquals(
            "Layouts → List art is set to 3D box art. Switch it to 2D box art?",
            layoutQuestionText(LayoutQuestion(BackdropArt.BOX2D, true, true), BackdropArt.BOX2D, GameListArt.BOX3D),
        )
    }

    @Test fun switchWritesSettings() = runTest {
        val box = opt(ArtSlot.BOX_3D, SourceId.SS, "b3")
        val s = Settings().apply { backdropArt = BackdropArt.WHEEL; gameListArt = GameListArt.BOX2D }
        val port = FakePort(listOf(opt(ArtSlot.SCREENSHOT, SourceId.SS, "s1"), box))
        val c = ArtPanelController(this, port, s, onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        // Keep: nothing changes, B too.
        c.tap(1)
        advanceUntilIdle()
        assertEquals(LayoutQuestion(BackdropArt.BOX3D, alsoListArt = true, yesFocused = true), c.panel.value!!.question)
        c.onButton(NxButton.UP); c.onButton(NxButton.DOWN)   // the question has the pad alone
        assertEquals(1, c.panel.value!!.focus)
        c.onButton(NxButton.RIGHT)
        assertEquals(false, c.panel.value!!.question!!.yesFocused)
        c.onButton(NxButton.A)   // Keep
        assertNull(c.panel.value!!.question)
        assertEquals(BackdropArt.WHEEL, s.backdropArt)
        assertEquals(GameListArt.BOX2D, s.gameListArt)
        c.onButton(NxButton.A)   // picks again: asks again
        advanceUntilIdle()
        assertNotNull(c.panel.value!!.question)
        c.onButton(NxButton.B)   // B is Keep; the panel stays open
        assertNull(c.panel.value!!.question)
        assertNotNull(c.panel.value)
        assertEquals(BackdropArt.WHEEL, s.backdropArt)
        // Switch.
        c.onButton(NxButton.A)
        advanceUntilIdle()
        c.onButton(NxButton.RIGHT); c.onButton(NxButton.LEFT)
        c.onButton(NxButton.A)
        assertNull(c.panel.value!!.question)
        assertEquals(BackdropArt.BOX3D, s.backdropArt)
        assertEquals(GameListArt.BOX3D, s.gameListArt)
        c.onButton(NxButton.A)   // now matching: no question
        advanceUntilIdle()
        assertNull(c.panel.value!!.question)
    }

    @Test fun answerByTap() = runTest {
        val s = Settings()
        val port = FakePort(listOf(opt(ArtSlot.WHEEL, SourceId.SS, "w1")))
        val c = ArtPanelController(this, port, s, onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        c.tap(0)
        advanceUntilIdle()
        assertNotNull(c.panel.value!!.question)
        c.tap(1)   // a tile tap while asked does nothing
        assertEquals(0, c.panel.value!!.focus)
        c.answer(switch = true)
        assertEquals(BackdropArt.WHEEL, s.backdropArt)
        assertEquals(GameListArt.SCREENSHOT, s.gameListArt)   // not a boxed kind: left alone
    }

    @Test fun noQuestionWhenThePickFails() = runTest {
        val port = object : ArtOptionsPort by FakePort(listOf(opt(ArtSlot.BOX_2D, SourceId.SS, "b2"))) {
            override suspend fun pickOption(gamePath: String, option: ArtOption): Boolean = false
        }
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        c.onButton(NxButton.A)
        advanceUntilIdle()
        assertNull(c.panel.value!!.question)
        assertEquals("Couldn't use that picture", c.panel.value!!.message)
    }

    @Test fun currentMarkPerSection() = runTest {
        val s1 = opt(ArtSlot.SCREENSHOT, SourceId.SS, "s1"); val s2 = opt(ArtSlot.SCREENSHOT, SourceId.TGDB, "s2")
        val b1 = opt(ArtSlot.BOX_2D, SourceId.TGDB, "b1"); val g1 = opt(ArtSlot.GRID, SourceId.SGDB, "g1")
        val port = FakePort(listOf(s1, s2, b1, g1), currentKeys = mapOf(ArtSlot.SCREENSHOT to s2.key, ArtSlot.BOX_2D to g1.key))
        val c = ArtPanelController(this, port, Settings(), onPicked = {})
        c.open(game, fetchFirst = false)
        advanceUntilIdle()
        val p = assertNotNull(c.panel.value)
        assertEquals(listOf(false, true, false, true), p.tiles.map { p.isCurrent(it) })
        assertEquals(1, p.focus)   // the first one in use
        c.tap(2)   // the 2D box b1: its section's mark moves, the screenshot's stays
        advanceUntilIdle()
        val q = c.panel.value!!
        assertEquals(listOf(false, true, true, false), q.tiles.map { q.isCurrent(it) })
    }

    @Test fun anyArtCountsTheWheelAndTheBox() {
        val g = game.game
        assertEquals(false, hasAnyArt(g))
        assertTrue(hasAnyArt(g.copy(wheelRef = ContentRef("w"))))
        assertTrue(hasAnyArt(g.copy(box2dRef = ContentRef("b"))))
        assertTrue(hasAnyArt(g.copy(screenshotRef = ContentRef("s"))))
        assertTrue(hasAnyArt(g.copy(boxartRef = ContentRef("b"))))
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
