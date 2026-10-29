package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.ToolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GridTitleTest {
    // The selected console's or collection's name over its game count, in the page title's place.
    @Test fun aConsoleOrCollectionShowsItsNameAndCount() {
        assertEquals("Game Boy Advance" to 74, gridTitle(GridTile.Logo(null, "Game Boy Advance", count = 74)))
        assertEquals("Favourites" to 1, gridTitle(GridTile.Collection("Favourites", count = 1)))
    }

    // A count not known yet: the name alone.
    @Test fun anUnknownCountShowsTheNameAlone() {
        assertEquals("RPGs" to null as Int?, gridTitle(GridTile.Collection("RPGs")))
    }

    // The count's word after its number, as Home's "5 achievements".
    @Test fun theCountsWordFollowsItsNumber() {
        assertEquals(" games", gamesWord(74))
        assertEquals(" game", gamesWord(1))
    }

    // A tool has no count, and nothing selected has nothing to name: no title.
    @Test fun aToolOrNothingHasNoTitle() {
        assertNull(gridTitle(GridTile.Tool(toolIcon(ToolKind.SETTINGS), listOf("Settings"))))
        assertNull(gridTitle(null))
    }
}
