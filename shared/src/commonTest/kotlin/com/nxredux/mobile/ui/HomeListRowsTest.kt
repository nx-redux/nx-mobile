package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.nx.HomeShelf
import com.nxredux.mobile.ui.nx.CaptionPart
import com.nxredux.mobile.ui.nx.homeCaptionParts
import com.nxredux.mobile.ui.nx.homeListAnchor
import com.nxredux.mobile.ui.nx.homeListRows
import com.nxredux.mobile.ui.nx.homeListScroll
import com.nxredux.mobile.ui.nx.homeListViewport
import com.nxredux.mobile.ui.nx.homeListTopRoom
import com.nxredux.mobile.ui.nx.homeFactLines
import com.nxredux.mobile.ui.nx.MENU_TAB_ROW_HEIGHT
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeListRowsTest {
    private val gb = SystemFolder("Game Boy", "GB", null, emptyList(), null, null, "Game Boy (GB)", false)
    private fun g(name: String) =
        Listed(gb, GameEntry(ContentRef("r/$name"), "$name.gb", name, folder = ContentRef("f"), sourceId = "home", relativeDir = "Roms/Game Boy (GB)"))
    private val cont = MainRow.Continue(g("Tetris"), ResumeInfo.NONE)
    private val a = g("Advance Wars")
    private val c = g("Bomberman")
    private val t1 = ToolRow(ToolKind.SETTINGS, "Settings", null)

    // A game wears why it is on Home: Continue, New or Not started; Pick up again, a pin or a tool, none.
    @Test fun games_carry_their_reason_tag() {
        val rows = homeListRows(listOf(cont, MainRow.Pin(a), MainRow.Shelf(c, HomeShelf.NEW), MainRow.PinnedTool(t1)))
        assertEquals("Continue", rows[0].tag); assertEquals(cont.entry.game.shownName, rows[0].label)
        assertNull(rows[1].tag)
        assertEquals(a.game.shownName, rows[1].label)
        assertEquals(c.game.shownName, rows[2].label)
        assertEquals("New", rows[2].tag); assertNull(rows[2].secondary)
        assertEquals("Not started", homeListRows(listOf(MainRow.Shelf(c, HomeShelf.NOT_STARTED))).single().tag)
        assertNull(homeListRows(listOf(MainRow.Shelf(c, HomeShelf.PICK_UP))).single().tag)
        assertEquals(t1.label, rows[3].label)
        assertNull(rows[3].secondary)
        assertEquals("Pick a game", homeListRows(listOf(MainRow.PickAGame)).single().label)
    }

    // The tab row hidden with no stats header: the rows run up beside Recent and Search, as a main menu List's do, so
    // the list sits as far under the page's top as over its band; the Pick a game card still keeps the row clear.
    @Test fun list_home_keeps_the_tab_rows_room_only_for_a_header_or_the_pick_a_game_card() {
        assertEquals(0.dp, homeListTopRoom(tabRow = false, header = null, pickAGame = false))
        assertEquals(MENU_TAB_ROW_HEIGHT, homeListTopRoom(tabRow = false, header = null, pickAGame = true))
        assertEquals(40.dp, homeListTopRoom(tabRow = false, header = 40.dp, pickAGame = false))   // the stats header's own height
        assertEquals(0.dp, homeListTopRoom(tabRow = true, header = null, pickAGame = false))
    }

    // The selected game's facts: one line under Home stats; upright with the stats off, two (the next goal on the second).
    @Test fun list_home_facts_take_one_line_under_the_stats_and_two_upright_without() {
        assertEquals(1, homeFactLines(facts = true, portrait = true, statLines = 2))
        assertEquals(1, homeFactLines(facts = true, portrait = true, statLines = 1))
        assertEquals(2, homeFactLines(facts = true, portrait = true, statLines = 0))
        assertEquals(1, homeFactLines(facts = true, portrait = false, statLines = 0))
        assertEquals(0, homeFactLines(facts = false, portrait = true, statLines = 0))
    }

    // The selection moves freely; the list scrolls only when it reaches the third row from an edge.
    @Test fun scrolls_only_at_the_third_row_from_an_edge() {
        val h = List(20) { 10 }   // 20 rows of 10 px, a 60 px viewport (six rows)
        val all = { _: Int -> true }
        assertEquals(0, homeListScroll(h, all, selected = 3, viewport = 60, current = 0, margin = 2))   // rows 0-5 show: 3 is free
        assertEquals(10, homeListScroll(h, all, selected = 4, viewport = 60, current = 0, margin = 2))  // 4 is third from the bottom
        assertEquals(30, homeListScroll(h, all, selected = 5, viewport = 60, current = 40, margin = 2)) // third from the top going up
        assertEquals(140, homeListScroll(h, all, selected = 19, viewport = 60, current = 0, margin = 2)) // the end: no scroll past it
        assertEquals(0, homeListScroll(List(3) { 10 }, all, selected = 2, viewport = 60, current = 0, margin = 2)) // fits: never scrolls
        // The gap is not a row: two rows past the selection reach over it.
        assertEquals(10, homeListScroll(List(8) { 10 }, { it != 4 }, selected = 3, viewport = 60, current = 0, margin = 2))
    }

    // Whole rows at rest; the whole band while the list fits it.
    @Test fun the_viewport_holds_whole_rows() {
        assertEquals(6 * 8, homeListViewport(band = 52, total = 200, pitch = 8))
        assertEquals(50, homeListViewport(band = 50, total = 40, pitch = 8))
        assertEquals(2 to 5, homeListAnchor(listOf(10, 10, 10, 10), 25))
    }

    // The facts line reads as the stats: values apart from their labels, the trophy before the count.
    @Test fun the_facts_split_values_from_labels() {
        val parts = homeCaptionParts(listOf(
            InfoLine(InfoKind.TIME, "Today - 1h 17m"), InfoLine(InfoKind.ACHIEVEMENTS, "0 of 92"), InfoLine(InfoKind.NEXT, "Next: Class Dismissed!"),
        ))
        assertEquals(listOf(
            CaptionPart("Today ", false), CaptionPart("1h 17m", true), CaptionPart(" · ", false),
            CaptionPart("", false, trophy = true), CaptionPart("0 of 92", true), CaptionPart(" · ", false),
            CaptionPart("Next: ", false), CaptionPart("Class Dismissed!", true),
        ), parts)
        assertEquals(listOf(CaptionPart("2m 5s", true)), homeCaptionParts(listOf(InfoLine(InfoKind.TIME, "2m 5s"))))
        assertEquals(listOf(CaptionPart("3 days ago", false)), homeCaptionParts(listOf(InfoLine(InfoKind.TIME, "3 days ago"))))
    }
}
