package com.nxredux.mobile.ui.nx

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The panel on a small screen: its header by its height ([gamePanelHeaderFor]), a choice's values side by side only where they fit ([quickSegmentsFit]). */
class GamePanelFitTest {
    @Test fun a_tall_panel_keeps_the_full_header() {
        assertEquals(GamePanelHeaderStyle.FULL, gamePanelHeaderFor(480.dp))   // the Nova's bottom screen
    }

    @Test fun the_fold_panel_under_a_game_takes_the_one_line_header() {
        assertEquals(GamePanelHeaderStyle.ONE_LINE, gamePanelHeaderFor(350.dp))
    }

    @Test fun a_touch_screen_cut_to_its_picture_drops_the_header() {
        assertEquals(GamePanelHeaderStyle.NONE, gamePanelHeaderFor(270.dp))   // the 3DS on the Fold upright
    }

    @Test fun four_values_share_a_wide_row() {
        // the Nova: 640 dp, 470 for the control, 117 dp a value; "Fullscreen" (~64 dp) and its room fit
        assertTrue(quickSegmentsFit(640.dp, 64.dp, 4))
    }

    @Test fun four_values_do_not_share_the_fold_panels_row() {
        // 320 dp: 150 for the control, 37 dp a value
        assertFalse(quickSegmentsFit(320.dp, 64.dp, 4))
    }

    @Test fun short_values_share_a_narrow_row() {
        assertTrue(quickSegmentsFit(320.dp, 20.dp, 2))
    }

    @Test fun a_narrow_panel_gives_the_name_less_room() {
        assertTrue(quickLabelFor(330.dp) < quickLabelFor(640.dp))   // "Screen Sharpness" on two lines on the Fold
    }
}
