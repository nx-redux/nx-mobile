package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.HomeShelf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [homeRowIn] and [homeLaunchedPin]: where a remembered Home row lands in rebuilt rows. */
class HomeControllerTest {
    private val gba = SystemFolder("Game Boy Advance", "GBA", null, emptyList(), null, null, "Game Boy Advance (GBA)", false)
    private fun listed(name: String) = Listed(gba, GameEntry(ContentRef("r/$name"), "$name.gba", name, folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy Advance (GBA)"))
    private val g1 = listed("G1"); private val g2 = listed("G2")
    private val t1 = ToolRow(ToolKind.SETTINGS, "Settings", null)
    private val t2 = ToolRow(ToolKind.CHEAT_DB, "Cheats & Patches", null)
    private val shelf = HomeShelf.entries.first()

    @Test fun aRowStillThereIsItself() {
        val rows = listOf(MainRow.Continue(g1, ResumeInfo.NONE), MainRow.PinnedTool(t1), MainRow.Pin(g2))
        assertEquals(2, homeRowIn(MainRow.Pin(g2), rows, listOf(t1)))
        assertNull(homeRowIn(null, rows, listOf(t1)))
    }

    // A pinned tool the slots now hide lands on "+N" while still pinned; unpinned it is gone.
    @Test fun aHiddenPinnedToolLandsOnMore() {
        val rows = listOf(MainRow.PinnedTool(t1), MainRow.MoreTools(1))
        assertEquals(1, homeRowIn(MainRow.PinnedTool(t2), rows, listOf(t1, t2)))
        assertNull(homeRowIn(MainRow.PinnedTool(t2), rows, listOf(t1)))
        assertNull(homeRowIn(MainRow.PinnedTool(t2), rows, null))
    }

    // "+N" with a new count is "+N"; with every tool fitting now, the last tool square.
    @Test fun moreFollowsTheSlots() {
        assertEquals(1, homeRowIn(MainRow.MoreTools(3), listOf(MainRow.PinnedTool(t1), MainRow.MoreTools(2)), listOf(t1)))
        assertEquals(1, homeRowIn(MainRow.MoreTools(1), listOf(MainRow.PinnedTool(t1), MainRow.PinnedTool(t2)), listOf(t1, t2)))
    }

    // A shelf game just pinned follows itself onto its pin.
    @Test fun aShelfGameFollowsItsPin() {
        val rows = listOf(MainRow.PinnedTool(t1), MainRow.Pin(g1))
        assertEquals(1, homeRowIn(MainRow.Shelf(g1, shelf), rows, listOf(t1)))
        assertNull(homeRowIn(MainRow.Shelf(g2, shelf), rows, listOf(t1)))
    }

    // A launched pin or shelf game left the rows for Continue: the focus follows it to row 0.
    @Test fun aLaunchedPinLandsOnContinue() {
        val rows = listOf(MainRow.Continue(g1, ResumeInfo.NONE), MainRow.Pin(g2))
        assertEquals(0, homeLaunchedPin(MainRow.Pin(g1), rows))
        assertEquals(0, homeLaunchedPin(MainRow.Shelf(g1, shelf), rows))
        assertNull(homeLaunchedPin(MainRow.Pin(g2), rows))   // still in the rows
        assertNull(homeLaunchedPin(MainRow.PinnedTool(t1), rows))
        assertNull(homeLaunchedPin(MainRow.Pin(g1), listOf(MainRow.Pin(g2))))   // no Continue
    }
}
