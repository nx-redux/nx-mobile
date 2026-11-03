package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.homeCarouselBadge
import com.nxredux.mobile.ui.nx.homeCarouselMenu
import com.nxredux.mobile.ui.nx.homeCarouselShownRow
import com.nxredux.mobile.ui.nx.homeCarouselTiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeCarouselTilesTest {
    private val gb = SystemFolder("Game Boy", "GB", null, emptyList(), null, null, "Game Boy (GB)", false)
    private fun g(name: String) =
        Listed(gb, GameEntry(ContentRef("r/$name"), "$name.gb", name, folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy (GB)"))
    private val a = g("Advance Wars")
    private val t1 = ToolRow(ToolKind.SETTINGS, "Settings", null)

    // Continue's hero screenshot is drawn as a hero in the wide Carousel tile; a resume frame never is.
    @Test fun continue_draws_a_hero_screenshot_as_a_hero() {
        val hero = a.copy(game = a.game.copy(screenshotRef = ContentRef("h.png"), heroShot = true))
        val tile = homeCarouselTiles(listOf(MainRow.Continue(hero, ResumeInfo(1))), 0).single() as GridTile.Game
        assertEquals("h.png", tile.screenshotUrl)
        assertTrue(tile.heroShot)
        assertEquals(TileArt("h.png", TileFit.HERO), tileArt(340f, 240f, tile.screenshotUrl, tile.heroShot, tile.abstractUrl))
        val framed = homeCarouselTiles(listOf(MainRow.Continue(hero, ResumeInfo(1, previewPath = "f.png"))), 0).single() as GridTile.Game
        assertEquals("f.png", framed.screenshotUrl)
        assertFalse(framed.heroShot)
    }

    // Continue shows its resume frame (Grid Home's first picture) under its name; Pick a game the accent's picture;
    // the dock's items are not the carousel's.
    @Test fun continue_shows_its_resume_frame_and_pick_a_game_the_accent_picture() {
        val c = MainRow.Continue(a, ResumeInfo(1, previewPath = "file:///s.png"))
        val tile = homeCarouselTiles(listOf(c), 0x112233).single() as GridTile.Game
        assertEquals("file:///s.png", tile.screenshotUrl)
        assertEquals(a.game.shownName, tile.title)
        assertEquals(abstractArtFor(a), tile.abstractUrl)
        val p = homeCarouselTiles(listOf(MainRow.PickAGame, MainRow.PinnedTool(t1), MainRow.MoreTools(2)), 0x112233)
        assertEquals(1, p.size)
        assertEquals(abstractAccentArtUrl(0x112233), (p.single() as GridTile.Game).screenshotUrl)
        assertEquals("Pick a game", (p.single() as GridTile.Game).title)
    }

    // A pin or a shelf game is its Grid tile.
    @Test fun pins_and_shelf_games_are_their_grid_tiles() {
        val rows = listOf(MainRow.Pin(a), MainRow.Shelf(a, HomeShelf.NEW))
        assertEquals(gridTiles(rows), homeCarouselTiles(rows, 0))
    }

    @Test fun badges() {
        assertEquals("Continue", homeCarouselBadge(MainRow.Continue(a, ResumeInfo.NONE)))
        assertEquals("Pick up again", homeCarouselBadge(MainRow.Shelf(a, HomeShelf.PICK_UP)))
        assertNull(homeCarouselBadge(MainRow.Pin(a)))
        assertNull(homeCarouselBadge(MainRow.PickAGame))
        assertNull(homeCarouselBadge(null))
    }

    // The carousel shows the selected row item; while the dock has the focus, the one last selected (in range).
    @Test fun shown_row_is_the_selection_else_the_last_row_item() {
        assertEquals(2, homeCarouselShownRow(sel = 2, rowCount = 4, last = 0))
        assertEquals(1, homeCarouselShownRow(sel = 5, rowCount = 4, last = 1))
        assertEquals(3, homeCarouselShownRow(sel = 5, rowCount = 4, last = 7))
        assertEquals(0, homeCarouselShownRow(sel = -1, rowCount = 4, last = 0))
    }

    // Only rows with a menu take a long press: Pick a game and "+N" don't (Grid Home's menu = false).
    @Test fun long_press_only_on_rows_with_a_menu() {
        assertTrue(homeCarouselMenu(MainRow.Continue(a, ResumeInfo.NONE)))
        assertTrue(homeCarouselMenu(MainRow.Pin(a)))
        assertTrue(homeCarouselMenu(MainRow.Shelf(a, HomeShelf.NEW)))
        assertTrue(homeCarouselMenu(MainRow.PinnedTool(t1)))
        assertFalse(homeCarouselMenu(MainRow.PickAGame))
        assertFalse(homeCarouselMenu(MainRow.MoreTools(2)))
        assertFalse(homeCarouselMenu(null))
    }

    // A hidden tab can be opened from MENU: every row takes a long press, so touch alone reaches the hidden-tab menu.
    @Test fun a_hidden_tab_gives_pick_a_game_and_more_tools_a_long_press() {
        assertTrue(homeCarouselMenu(MainRow.PickAGame, tabMenu = true))
        assertTrue(homeCarouselMenu(MainRow.MoreTools(2), tabMenu = true))
        assertTrue(homeCarouselMenu(MainRow.Pin(a), tabMenu = true))
        assertFalse(homeCarouselMenu(null, tabMenu = true))
    }
}
