package com.nxredux.mobile.ui

import com.nxredux.mobile.library.FolderSummary
import com.nxredux.mobile.storage.Recents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The summary's move confirmation ([folderMoveAsk]): asked only when Continue would move something. */
class FolderMoveAskTest {
    private fun summary(extraId: String, sum: FolderSummary, move: Boolean = true) =
        Screen.FolderSummary(extraId, "Games", "NXRedux", sum, move = move)

    @Test fun an_extra_folders_placed_games_are_moved_not_copied() {
        val s = summary("x1", FolderSummary(games = 5, byConsole = emptyList(), unassigned = 2))
        assertEquals("Move games?" to "3 games will be moved from Games into NXRedux/Roms. They are moved, not copied.", folderMoveAsk(s))
    }

    @Test fun the_homes_outside_games_move_in_and_split_consoles_merge() {
        val s = summary(Recents.HOME, FolderSummary(games = 4, byConsole = emptyList(), unassigned = 0, outside = 1, split = 2))
        assertEquals(
            "Move games?" to "1 game outside Roms will be moved into NXRedux/Roms, and 2 consoles' folders will be merged into one. Folders left empty are removed.",
            folderMoveAsk(s),
        )
    }

    @Test fun nothing_to_move_asks_nothing() {
        assertNull(folderMoveAsk(summary("x1", FolderSummary(games = 3, byConsole = emptyList(), unassigned = 0), move = false)))   // Move off
        assertNull(folderMoveAsk(summary("x1", FolderSummary(games = 2, byConsole = emptyList(), unassigned = 2))))                // none placed
        assertNull(folderMoveAsk(summary(Recents.HOME, FolderSummary(games = 2, byConsole = emptyList(), unassigned = 0))))        // all in Roms
    }
}
