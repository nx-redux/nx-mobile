package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainMenuHintsTest {
    private val md = SystemFolder("Sega Genesis", "MD", null, emptyList(), null, null, "Sega Genesis (MD)", false)
    private val game = GameEntry(ContentRef("r"), "Sonic.md", "Sonic", folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Sega Genesis (MD)")
    private val recent = Hint(NxButton.SELECT, "Recent")

    @Test fun aPinPlaysAndHasOptions() {
        assertEquals(listOf(recent, Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Play")), mainMenuHints(MainRow.Pin(Listed(md, game))))
    }

    @Test fun continueResumesWithASaveAndPlaysWithoutOne() {
        val row = MainRow.Continue(Listed(md, game), ResumeInfo.NONE)
        val options = Hint(NxButton.MENU, "Options")
        assertEquals(listOf(recent, options, Hint(NxButton.A, "Play")), mainMenuHints(row))
        assertEquals(listOf(recent, options, Hint(NxButton.A, "Resume")), mainMenuHints(row, canResume = true))
    }

    @Test fun aPinResumesWithASaveAndPlaysWithoutOne() {
        // Home's pinned games: A Resume, A Play when it has no save state.
        assertEquals(listOf(recent, Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Resume")), mainMenuHints(MainRow.Pin(Listed(md, game)), canResume = true))
        assertEquals(listOf(recent, Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Play")), mainMenuHints(MainRow.Pin(Listed(md, game)), canResume = false))
    }

    @Test fun aGameRowWithNetplayOffersY() {
        val options = Hint(NxButton.MENU, "Options")
        val netplay = Hint(NxButton.Y, "Netplay")
        val pin = MainRow.Pin(Listed(md, game))
        assertEquals(listOf(recent, options, netplay, Hint(NxButton.A, "Play")), mainMenuHints(pin, canNetplay = true))
        assertEquals(listOf(recent, options, netplay, Hint(NxButton.A, "Resume")),
            mainMenuHints(MainRow.Continue(Listed(md, game), ResumeInfo.NONE), canResume = true, canNetplay = true))
        assertEquals(listOf(recent, Hint(NxButton.A, "Open")), mainMenuHints(MainRow.System(md), canNetplay = true))   // not a game
    }

    @Test fun startSearchShowsWhenThereIsSomethingToSearch() {
        val search = Hint(NxButton.START, "Search")
        val pin = MainRow.Pin(Listed(md, game))
        assertEquals(listOf(recent, search, Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Play")), mainMenuHints(pin, canSearch = true))
        assertEquals(listOf(recent, Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Play")), mainMenuHints(pin))   // no START without games
    }

    @Test fun aToolRowOffersItsPinMenu() {
        assertEquals(listOf(recent, Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Open")), mainMenuHints(MainRow.Tool(ToolRow(ToolKind.SETTINGS, "Settings", null))))
    }

    private val tool = ToolRow(ToolKind.GAME_TIME, "Game Tracker", null)

    // On Home a tool square has MENU Options (Unpin) and its A names the tool.
    @Test fun pinnedToolNamesItsA() = assertEquals(
        listOf(Hint(NxButton.SELECT, "Recent"), Hint(NxButton.MENU, "Options"), Hint(NxButton.A, tool.label)),
        mainMenuHints(MainRow.PinnedTool(tool)))
    @Test fun moreToolsOpensMore() = assertEquals(
        listOf(Hint(NxButton.SELECT, "Recent"), Hint(NxButton.A, "More tools")), mainMenuHints(MainRow.MoreTools(2)))

    @Test fun aCollectionRowOffersItsRenameAndDeleteMenu() {
        assertEquals(listOf(recent, Hint(NxButton.MENU, "Options"), Hint(NxButton.A, "Open")), mainMenuHints(MainRow.Collection("Co-op", 3)))
    }

    @Test fun everyOtherRowOpens() {
        val open = listOf(recent, Hint(NxButton.A, "Open"))
        assertEquals(open, mainMenuHints(MainRow.System(md)))
        assertEquals(open, mainMenuHints(MainRow.PickAGame))
        assertEquals(open, mainMenuHints(null))
    }

    // Every Home item's hints, row by row.
    @Test fun homeHintsFollowTheSpecTable() {
        val options = Hint(NxButton.MENU, "Options")
        val cont = MainRow.Continue(Listed(md, game), ResumeInfo(0, "file:///frame.png"))
        assertEquals(listOf(recent, options, Hint(NxButton.A, "Resume")), mainMenuHints(cont, canResume = true))
        assertEquals(listOf(recent, options, Hint(NxButton.A, "Play")), mainMenuHints(cont, canResume = false))
        assertEquals(listOf(recent, options, Hint(NxButton.A, "Play")), mainMenuHints(MainRow.Pin(Listed(md, game))))
        assertEquals(listOf(recent, options, Hint(NxButton.A, "Game Tracker")), mainMenuHints(MainRow.PinnedTool(ToolRow(ToolKind.GAME_TIME, "Game Tracker", null))))
        assertEquals(listOf(recent, Hint(NxButton.A, "More tools")), mainMenuHints(MainRow.MoreTools(3)))
        val open = listOf(recent, Hint(NxButton.A, "Open"))
        assertEquals(open, mainMenuHints(MainRow.PickAGame))
    }

    @Test fun aPushedTabOffersBackFirst() {
        assertEquals(Hint(NxButton.B, "Back"), mainMenuHints(MainRow.System(md), pushed = true).first())
        assertTrue(mainMenuHints(MainRow.System(md)).none { it.button == NxButton.B })
    }

    @Test fun aHiddenTabGivesEveryRowOptions() {
        assertTrue(Hint(NxButton.MENU, "Options") in mainMenuHints(MainRow.System(md), tabMenu = true))
        assertTrue(Hint(NxButton.MENU, "Options") !in mainMenuHints(MainRow.System(md)))
    }
}
