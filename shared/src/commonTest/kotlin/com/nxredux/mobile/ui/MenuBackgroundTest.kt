package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.settings.ConsoleArt
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.MenuStyles
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.consolePad
import com.nxredux.mobile.ui.nx.toolIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MenuBackgroundTest {
    private val md = SystemFolder("Sega Genesis", "MD", null, emptyList(), null, null, "Sega Genesis (MD)", false)
    private val android = SystemFolder("Android", ANDROID_TAG, null, emptyList(), null, null, "Android ($ANDROID_TAG)", false, android = true)
    private val unknown = SystemFolder("Unknown", "NOPE", null, emptyList(), null, null, "Unknown (NOPE)", false)
    private val sfc = SystemFolder("Super Nintendo ES", "SFC", null, emptyList(), null, null, "Super Nintendo ES (SFC)", false)
    private val supa = SystemFolder("Super Nintendo ES", "SUPA", null, emptyList(), null, null, "Super Nintendo ES (SUPA)", false)
    private val game = GameEntry(ContentRef("r"), "Sonic.md", "Sonic", screenshotRef = ContentRef("shot"), folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Sega Genesis (MD)")

    private val cont = MainRow.Continue(Listed(md, game), ResumeInfo(1, "frame.png"))
    private val tool = MainRow.PinnedTool(ToolRow(ToolKind.SETTINGS, "Settings", null))

    // Grid and Carousel Home are plain black, whatever is selected: no backdrop art or tint behind the dashboard.
    @Test fun gridAndCarouselHomeArePlainBlack() {
        for (style in listOf(MenuStyle.GRID, MenuStyle.CAROUSEL)) for (row in listOf(cont, MainRow.Pin(Listed(md, game)), tool, MainRow.MoreTools(2))) {
            assertEquals(MenuBackground.Plain, menuBackground(style, MenuCategory.HOME, row), "$style $row")
        }
    }

    // List Home: the selected game, drawn as a game list's List shows it; a pinned tool, plain.
    @Test fun list_home_shows_the_game_picture_and_nothing_for_a_tool() {
        assertEquals(MenuBackground.Game(Listed(md, game)), menuBackground(MenuStyle.LIST, MenuCategory.HOME, MainRow.Pin(Listed(md, game)), portrait = false))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.HOME, tool, portrait = false))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.GRID, MenuCategory.HOME, MainRow.Pin(Listed(md, game)), portrait = false))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.CAROUSEL, MenuCategory.HOME, MainRow.Pin(Listed(md, game)), portrait = false))
    }

    @Test fun list_home_pictures_are_the_selected_games() {
        val e = Listed(md, game)
        assertEquals(MenuBackground.Game(cont.entry, cont.resume.previewPath), menuBackground(MenuStyle.LIST, MenuCategory.HOME, cont))
        assertEquals(MenuBackground.Game(e), menuBackground(MenuStyle.LIST, MenuCategory.HOME, MainRow.Pin(e), portrait = true))
        assertEquals(MenuBackground.Game(e), menuBackground(MenuStyle.LIST, MenuCategory.HOME, MainRow.Shelf(e, HomeShelf.NEW)))
        // A game saved and quit: its hidden save state's frame, Continue's over its resume slot's.
        assertEquals(MenuBackground.Game(e, "auto.png"), menuBackground(MenuStyle.LIST, MenuCategory.HOME, MainRow.Pin(e, "auto.png")))
        assertEquals(MenuBackground.Game(e, "auto.png"), menuBackground(MenuStyle.LIST, MenuCategory.HOME, MainRow.Shelf(e, HomeShelf.MOST_PLAYED, null, "auto.png")))
        assertEquals(MenuBackground.Game(cont.entry, "auto.png"), menuBackground(MenuStyle.LIST, MenuCategory.HOME, MainRow.Continue(cont.entry, cont.resume, "auto.png")))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.HOME, tool))   // a pinned tool: no icon behind
        // The Home background is not the consoles' controller art: Layouts → Consoles background off keeps it.
        assertIs<MenuBackground.Game>(menuBackground(MenuStyle.LIST, MenuCategory.HOME, cont, consoleArt = ConsoleArt.NONE))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.HOME, MainRow.PickAGame))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.HOME, null))
    }

    @Test fun aListConsoleWithHardwareShowsItsPad() {
        assertEquals(MenuBackground.Pad(consolePad("MD")!!), menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(md)))
    }

    @Test fun aListConsoleWithoutHardwareOrLogoIsPlain() {
        // A tag no console claims has neither controller art nor a logo (every registry console has a pad).
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(unknown)))
    }

    @Test fun theAndroidConsoleShowsItsPad() {
        assertEquals(MenuBackground.Pad(consolePad(ANDROID_TAG)!!), menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(android)))
    }

    @Test fun controllerHiddenLeavesTheListEmpty() {
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(md), consoleArt = ConsoleArt.NONE))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(android), consoleArt = ConsoleArt.NONE))
    }

    @Test fun aMergedRowFindsThePadThroughAnyTag() {
        // SFC + SUPA, listed as one console: the first tag with a pad, as the logo.
        assertEquals(MenuBackground.Pad(consolePad("SFC")!!), menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(listOf(supa, sfc))))
    }

    // The other tabs hold no games (Home draws its own), so Grid and Carousel are plain, and List is plain but on a
    // console.
    @Test fun everyOtherTabAndStyleIsPlain() {
        for (style in listOf(MenuStyle.GRID, MenuStyle.CAROUSEL)) {
            assertEquals(MenuBackground.Plain, menuBackground(style, MenuCategory.CONSOLES, MainRow.System(md)), "$style")
        }
        for (style in MenuStyles.MAIN_MENU_STYLES) {
            assertEquals(MenuBackground.Plain, menuBackground(style, MenuCategory.COLLECTIONS, MainRow.Collection("Co-op")), "$style")
            assertEquals(MenuBackground.Plain, menuBackground(style, MenuCategory.TOOLS, MainRow.Tool(ToolRow(ToolKind.SETTINGS, "Settings", null))), "$style")
        }
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, null))
    }

    @Test fun background_shows_the_consoles_own_bg_in_list_and_carousel() {
        val withBg = md.copy(bgRef = ContentRef("Roms/Sega Genesis (MD)/.media/bg.png"))
        assertEquals(MenuBackground.Picture("Roms/Sega Genesis (MD)/.media/bg.png"), menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(withBg), consoleArt = ConsoleArt.BACKGROUND))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(md), consoleArt = ConsoleArt.BACKGROUND))
        // Carousel: the Backdrop game list's picture; plain without a bg.png, on None and on Controller (the tiles carry the pads).
        assertEquals(MenuBackground.Backdrop("Roms/Sega Genesis (MD)/.media/bg.png"), menuBackground(MenuStyle.CAROUSEL, MenuCategory.CONSOLES, MainRow.System(withBg), consoleArt = ConsoleArt.BACKGROUND))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.CAROUSEL, MenuCategory.CONSOLES, MainRow.System(md), consoleArt = ConsoleArt.BACKGROUND))
        for (art in listOf(ConsoleArt.NONE, ConsoleArt.CONTROLLER)) {
            assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.CAROUSEL, MenuCategory.CONSOLES, MainRow.System(withBg), consoleArt = art), "$art")
        }
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.GRID, MenuCategory.CONSOLES, MainRow.System(withBg), consoleArt = ConsoleArt.BACKGROUND))
        assertEquals(MenuBackground.Plain, menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(withBg), consoleArt = ConsoleArt.NONE))
        assertEquals(MenuBackground.Pad(consolePad("MD")!!), menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, MainRow.System(withBg)))
    }

    @Test fun a_merged_row_takes_its_default_tags_bg_first() {
        val row = MainRow.System(listOf(supa.copy(bgRef = ContentRef("supa.png")), sfc.copy(bgRef = ContentRef("sfc.png"))))
        assertEquals(MenuBackground.Picture("sfc.png"), menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, row, consoleArt = ConsoleArt.BACKGROUND))
        val onlySupa = MainRow.System(listOf(supa.copy(bgRef = ContentRef("supa.png")), sfc))
        assertEquals(MenuBackground.Picture("supa.png"), menuBackground(MenuStyle.LIST, MenuCategory.CONSOLES, onlySupa, consoleArt = ConsoleArt.BACKGROUND))
    }
}
