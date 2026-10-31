package com.nxredux.mobile.ui

import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.path
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.ui.nx.consolePad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class GridTilesTest {
    private val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList(), null, null, "Game Boy Advance (GBA)", false)
    private fun listed(name: String, shot: String? = null, box: String? = null, label: String? = null) = Listed(gba, GameEntry(ContentRef("r/$name"), "$name.gba", name,
        boxartRef = box?.let(::ContentRef), screenshotRef = shot?.let(::ContentRef), folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy Advance (GBA)"), label)

    @Test fun gamesUseTheScreenshotOrATitleTile() {
        val tiles = gameGridTiles(listOf(listed("Zelda", shot = "s/zelda.png"), listed("Kirby")))
        assertEquals("s/zelda.png", tiles[0].screenshotUrl)
        assertNull(tiles[1].screenshotUrl)
        assertEquals("Kirby", tiles[1].title)
    }

    // Every ROM carries its generated abstract picture (by tag and file name), each slot's last fallback after its real
    // art ([tileArt], [backgroundArt], [boxShown]); an Android game too, seeded by its app id.
    @Test fun everyRomCarriesItsAbstractArt() {
        val bare = listed("Kirby")
        assertEquals(abstractArtUrl("GBA", "Kirby.gba"), abstractArtFor(bare))
        assertEquals(abstractArtUrl("GBA", "Kirby.gba"), gameGridTiles(listOf(bare)).single().abstractUrl)
        assertEquals(abstractArtUrl("GBA", "Kirby.gba"), backdropGames(listOf(bare)).single().abstractUrl)
        assertEquals(abstractArtUrl("GBA", "Zelda.gba"), abstractArtFor(listed("Zelda", shot = "s/zelda.png", box = "b/zelda.png")))
        assertEquals(abstractAppArtUrl("Kirby.gba"), abstractArtFor(bare.copy(system = gba.copy(android = true))))
    }

    // A game list's background: the screenshot, else the stand-in of the screen's shape, else the abstract picture
    // (a box-only game too: its box stays in the box slot).
    @Test fun backgroundsFallBackToAbstractArt() {
        val boxOnly = listed("Kirby", box = "b/kirby.png")
        assertEquals(abstractArtUrl("GBA", "Kirby.gba"), gameBackground(boxOnly, portrait = false))
        assertEquals(abstractArtUrl("GBA", "Kirby.gba"), gameBackground(boxOnly, portrait = true))
        assertEquals("s/z.png", gameBackground(listed("Zelda", shot = "s/z.png"), portrait = false))
        val hero = listed("Metroid").let { it.copy(game = it.game.copy(heroRef = ContentRef("h"))) }
        assertEquals("h", gameBackground(hero, portrait = false))
        assertEquals(abstractArtUrl("GBA", "Metroid.gba"), gameBackground(hero, portrait = true))   // a hero never fills a portrait screen
        // An Android game's background: its icon-less form (the cover shows the icon); a ROM's unchanged.
        val app = boxOnly.copy(system = gba.copy(android = true))
        assertEquals(abstractAppArtUrl("Kirby.gba", icon = false), gameBackground(app, portrait = false))
        assertEquals("nxabstract://app/Kirby.gba?icon=0", gameBackground(app, portrait = true))
        assertEquals(abstractAppArtUrl("Kirby.gba"), abstractArtFor(app))                       // its cover keeps the icon
        assertEquals(abstractArtFor(boxOnly), abstractBackgroundFor(boxOnly))
        assertEquals("s/a.png", gameBackground(listed("Alpha", shot = "s/a.png").copy(system = gba.copy(android = true)), portrait = false))
    }

    @Test fun rowsMapToTheirTiles() {
        val tiles = gridTiles(listOf(MainRow.System(gba), MainRow.Collection("Co-op", count = 2), MainRow.Tool(ToolRow(ToolKind.RETRO_ACHIEVEMENTS, "RetroAchievements", null))))
        assertIs<GridTile.Logo>(tiles[0])
        assertEquals(GridTile.Collection("Co-op", count = 2), tiles[1])
        assertEquals(listOf("Retro", "Achievements"), (tiles[2] as GridTile.Tool).lines)
    }

    // A merged console row's count is its systems' games summed, the number its info line shows.
    @Test fun aConsoleTileCountsTheGamesOfEveryMergedSystem() {
        val mgba = gba.copy(tag = "MGBA", games = List(2) { listed("G$it").game })
        val one = gba.copy(games = List(3) { listed("H$it").game })
        assertEquals(5, (gridTiles(listOf(MainRow.System(listOf(one, mgba)))).single() as GridTile.Logo).count)
    }

    // The Carousel's console tile carries its controller; Grid and Home pins leave it off (the default).
    @Test fun aConsoleTileCarriesItsPadOnlyWhenAsked() {
        val tiles = { art: Boolean -> gridTiles(listOf(MainRow.System(gba)), controllerArt = art).single() as GridTile.Logo }
        assertEquals(consolePad("GBA"), tiles(true).pad)
        assertNull(tiles(false).pad)
        assertNull((gridTiles(listOf(MainRow.System(gba))).single() as GridTile.Logo).pad)   // the default: Grid and Home pins
    }

    // A tag no console claims has no pad, even when asked.
    @Test fun aConsoleWithoutHardwareArtHasNoPad() {
        val unknown = gba.copy(displayName = "Unknown", tag = "NOPE", folderName = "Unknown (NOPE)")
        assertNull((gridTiles(listOf(MainRow.System(unknown)), controllerArt = true).single() as GridTile.Logo).pad)
    }

    // The Android games console (not a registry console) has its Galaxy Fold.
    @Test fun theAndroidConsoleHasItsPad() {
        val android = gba.copy(displayName = "Android", tag = ANDROID_TAG, folderName = "Android ($ANDROID_TAG)", android = true)
        assertEquals(consolePad(ANDROID_TAG), (gridTiles(listOf(MainRow.System(android)), controllerArt = true).single() as GridTile.Logo).pad)
    }

    @Test fun toolLinesBreakRetroAchievementsAndCheatsAndPatches() {
        assertEquals(listOf("Retro", "Achievements"), toolDisplayLines("RetroAchievements"))
        assertEquals(listOf("Cheats &", "Patches"), toolDisplayLines("Cheats & Patches"))
        assertEquals(listOf("Game Tracker"), toolDisplayLines("Game Tracker"))
    }

    @Test fun backdropGamesTakeBoxartScreenshotAndShownTitle() {
        val games = backdropGames(listOf(listed("Zelda", shot = "s/zelda.png", box = "b/zelda.png", label = "Zelda (GBA)"), listed("Kirby")))
        assertEquals(BackdropGame("b/zelda.png", "s/zelda.png", "Zelda (GBA)", "GBA", abstractUrl = abstractArtUrl("GBA", "Zelda.gba")), games[0])
        assertEquals(BackdropGame(null, null, "Kirby", "GBA", abstractUrl = abstractArtUrl("GBA", "Kirby.gba")), games[1])   // no art: its abstract picture
    }

    // The 2D front and the grid travel with the game, for the cover drawn in the case; the hero only to the Grid tile.
    @Test fun backdropGamesCarryBox2dAndGrid() {
        val zelda = listed("Zelda").let { it.copy(game = it.game.copy(box2dRef = ContentRef("2/zelda.jpg"), gridRef = ContentRef("g/zelda.jpg"), heroRef = ContentRef("h/zelda.jpg"))) }
        val game = backdropGames(listOf(zelda)).single()
        assertEquals("2/zelda.jpg", game.box2dUrl)
        assertEquals("g/zelda.jpg", game.gridUrl)
        assertNull(game.boxartUrl)
        assertNull(game.screenshotUrl)
        // A tile keeps the screenshot apart and carries both stand-ins; it picks by its own shape ([tileArt]).
        val tile = gameGridTiles(listOf(zelda)).single()
        assertNull(tile.screenshotUrl)
        assertEquals("h/zelda.jpg", tile.heroUrl)
        assertEquals("g/zelda.jpg", tile.gridUrl)
        val pin = gridTiles(listOf(MainRow.Pin(zelda))).single() as GridTile.Game
        assertEquals(tile.copy(title = pin.title), pin)
    }

    // A pinned tool on Home is the Tools tab's tile.
    @Test fun aPinnedToolIsTheToolTile() {
        val tool = ToolRow(ToolKind.RETRO_ACHIEVEMENTS, "RetroAchievements", null)
        assertEquals(gridTiles(listOf(MainRow.Tool(tool))), gridTiles(listOf(MainRow.PinnedTool(tool))))
        assertIs<GridTile.Tool>(gridTiles(listOf(MainRow.PinnedTool(tool))).single())
    }

    // An Android app's tile carries its id (its icon is looked up by it); other tools have none.
    @Test fun anAppToolTileCarriesItsId() {
        val app = ToolRow(ToolKind.APP, "Beta", null, appId = "b/B")
        assertEquals("b/B", (gridTiles(listOf(MainRow.Tool(app))).single() as GridTile.Tool).appId)
        assertEquals("b/B", (gridTiles(listOf(MainRow.PinnedTool(app))).single() as GridTile.Tool).appId)
        assertNull((gridTiles(listOf(MainRow.Tool(ToolRow(ToolKind.SETTINGS, "Settings", null)))).single() as GridTile.Tool).appId)
    }

    // An Android game's tile carries its app id (the icon its tile falls back to) and the art store's screenshot, if any.
    @Test fun androidGameTilesCarryTheAppId() {
        val android = SystemFolder("Android", ANDROID_TAG, null, emptyList(), android = true)
        val alpha = Listed(android, GameEntry(ContentRef("app:a/A"), "a/A", "Alpha", folder = ContentRef("app:"), sourceId = "android", relativeDir = ""))
        val art = abstractAppArtUrl("a/A")
        assertEquals(GridTile.Game(null, "Alpha", appId = "a/A", abstractUrl = art), gameGridTiles(listOf(alpha)).single())
        assertEquals(BackdropGame(null, null, "Alpha", ANDROID_TAG, appId = "a/A", abstractUrl = art), backdropGames(listOf(alpha)).single())
        val shot = alpha.copy(game = alpha.game.copy(screenshotRef = ContentRef("file:///art/a.png")))
        assertEquals(GridTile.Game("file:///art/a.png", "Alpha", appId = "a/A", abstractUrl = art), gameGridTiles(listOf(shot)).single())
        assertNull(gameGridTiles(listOf(listed("Kirby"))).single().appId)   // a ROM has none
        assertNull(backdropGames(listOf(listed("Kirby"))).single().appId)
    }

    // An Android game's abstract picture is seeded by its app id: stable per app, and never a ROM's of the same file name
    // (nor a ROM's in another console), so two apps differ too.
    @Test fun androidAbstractArtIsStablePerAppAndApartFromRoms() {
        val android = SystemFolder("Android", ANDROID_TAG, null, emptyList(), android = true)
        fun app(id: String, name: String = "Alpha") =
            Listed(android, GameEntry(ContentRef("app:$id"), id, name, folder = ContentRef("app:"), sourceId = "android", relativeDir = ""))
        val url = abstractArtFor(app("com.example.alpha"))
        assertEquals(abstractAppArtUrl("com.example.alpha"), url)
        assertEquals("nxabstract://app/com.example.alpha", url)   // its own form: the fetcher draws it from the app's icon
        assertEquals(url, abstractArtFor(app("com.example.alpha", name = "Renamed")))   // the label never moves it
        assertNotEquals(url, abstractArtFor(app("com.example.beta")))
        val rom = Listed(gba, GameEntry(ContentRef("r/x"), "com.example.alpha", "Alpha", folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy Advance (GBA)"))
        assertNotEquals(url, abstractArtFor(rom))
        assertEquals(abstractArtUrl("GBA", "com.example.alpha"), abstractArtFor(rom))
    }

    // Home's Continue card: the resume frame, the screenshot, the hero, the grid, then the abstract picture; a missing
    // link is skipped, so a game with no art at all still has a picture.
    @Test fun continuePicturesRunTheWholeChain() {
        val full = listed("Zelda", shot = "s").let { it.copy(game = it.game.copy(heroRef = ContentRef("h"), gridRef = ContentRef("g"))) }
        assertEquals(listOf("r", "s", "h", "g", abstractArtUrl("GBA", "Zelda.gba")), continuePictures("r", full))
        assertEquals(listOf("s", "h", "g", abstractArtUrl("GBA", "Zelda.gba")), continuePictures(null, full))
        assertEquals(listOf(abstractArtUrl("GBA", "Kirby.gba")), continuePictures(null, listed("Kirby", box = "b")))   // a box never fills the card
        val gridOnly = listed("Metroid").let { it.copy(game = it.game.copy(gridRef = ContentRef("g"))) }
        assertEquals(listOf("g", abstractArtUrl("GBA", "Metroid.gba")), continuePictures(null, gridOnly))
        val android = SystemFolder("Android", ANDROID_TAG, null, emptyList(), android = true)
        val alpha = Listed(android, GameEntry(ContentRef("app:a/A"), "a/A", "Alpha", folder = ContentRef("app:"), sourceId = "android", relativeDir = ""))
        assertEquals(listOf(abstractAppArtUrl("a/A")), continuePictures(null, alpha))
    }

    // The Grid's reset identity ignores game counts: a rescan that only changes them keeps the grid steady.
    @Test fun theResetIdentityIgnoresCounts() {
        val before = listOf(GridTile.Logo(null, "GBA", count = 3), GridTile.Collection("Co-op", count = 2), GridTile.Game(null, "Kirby"))
        val after = listOf(GridTile.Logo(null, "GBA", count = 4), GridTile.Collection("Co-op", count = 5), GridTile.Game(null, "Kirby"))
        assertEquals(gridTileIdentity(before), gridTileIdentity(after))
        val renamed = after.toMutableList().also { it[1] = GridTile.Collection("Solo", count = 5) }
        assertNotEquals(gridTileIdentity(before), gridTileIdentity(renamed))
    }

    // "No real screenshot": the tile's picture would be its abstract one (or none, a title tile). A screenshot is real
    // art at any size; a hero only on a wide tile, a grid only on a tall or square one (the stand-in [tileArt] draws).
    @Test fun aTileIsArtLessWithoutRealArtOfItsShape() {
        val bare = GridTile.Game(null, "Kirby", abstractUrl = "abstract:kirby")
        assertEquals(true, bare.artLess(200f, 120f))
        assertEquals(true, bare.artLess(140f, 154f))
        assertEquals(true, bare.copy(abstractUrl = null).artLess(140f, 154f))               // a title tile
        val shot = bare.copy(screenshotUrl = "s/kirby.png")
        assertEquals(false, shot.artLess(200f, 120f))
        assertEquals(false, shot.artLess(0f, 0f))                                           // unmeasured: still its screenshot
        val hero = bare.copy(heroUrl = "h/kirby.png")
        assertEquals(false, hero.artLess(200f, 120f))                                       // wide: the hero
        assertEquals(true, hero.artLess(140f, 154f))                                        // tall: no grid, the abstract picture
        val grid = bare.copy(gridUrl = "g/kirby.png")
        assertEquals(false, grid.artLess(140f, 154f))
        assertEquals(true, grid.artLess(200f, 120f))
        assertEquals(true, bare.artLess(0f, 0f))                                            // unmeasured, no screenshot: a title tile
    }

    // The per-tile info lookups hand each tile its own game's info (the Grid's count-only form), none for a row without one.
    @Test fun eachTileLooksUpItsOwnGamesInfo() {
        val zelda = listed("Zelda")
        val kirby = listed("Kirby")
        val day = 86_400_000L
        val now = 10 * day
        val info = mapOf(
            zelda.game.path to GameInfo(now - day, null, null),
            kirby.game.path to GameInfo(now, null, RaProgress(3, 35, next = "Next one")),
        )
        val rows = listOf(MainRow.Collection("Favourites", 2), MainRow.Pin(zelda), MainRow.Pin(kirby))
        val byRow = rowTileInfo(rows, info, now)
        assertEquals(emptyList(), byRow(0))
        assertEquals(listOf(InfoLine(InfoKind.TIME, "Yesterday")), byRow(1))
        assertEquals(listOf(InfoLine(InfoKind.TIME, "Today"), InfoLine(InfoKind.ACHIEVEMENTS, "3 of 35")), byRow(2))   // no Next: not full
        assertEquals(emptyList(), byRow(3))
        val byEntry = entryTileInfo(listOf(kirby, zelda, listed("Metroid")), info, now)
        assertEquals(listOf(InfoLine(InfoKind.TIME, "Yesterday")), byEntry(1))
        assertEquals(byRow(2), byEntry(0))
        assertEquals(emptyList(), byEntry(2))                                               // no info yet
        assertEquals(emptyList(), byEntry(-1))
    }
}
