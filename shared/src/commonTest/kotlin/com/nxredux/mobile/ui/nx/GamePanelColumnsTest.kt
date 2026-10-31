package com.nxredux.mobile.ui.nx

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shortcut grid's columns ([gamePanelColumns]): 4, 2 or 1, so the widest label is never cut. */
class GamePanelColumnsTest {
    @Test fun short_labels_take_four_columns() {
        // 500 dp: 468 inside the gutters, cells of 109.5 dp; a 60 dp label and its 20 dp of padding fit.
        assertEquals(4, gamePanelColumns(500.dp, 60.dp))
    }

    @Test fun a_label_too_wide_for_a_quarter_takes_two_columns() {
        // "Fast forward" (~95 dp) in the Fold's in-game menu: 115 dp does not fit a 109.5 dp cell, a 229 dp one does.
        assertEquals(2, gamePanelColumns(500.dp, 95.dp))
    }

    @Test fun a_label_too_wide_for_half_takes_one() {
        assertEquals(1, gamePanelColumns(300.dp, 140.dp))
    }

    @Test fun a_narrow_panel_never_takes_four() {
        assertEquals(2, gamePanelColumns(340.dp, 20.dp))
    }
}
