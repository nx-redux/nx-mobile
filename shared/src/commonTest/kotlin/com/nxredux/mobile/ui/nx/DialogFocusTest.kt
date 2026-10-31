package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** An open confirm dialog's pad focus ([DialogFocus]): LEFT and RIGHT light a button, A on Cancel is B. */
class DialogFocusTest {
    @Test fun left_and_right_move_the_light_and_are_taken() {
        assertEquals(1 to null, dialogFocusPress(0, NxButton.RIGHT))
        assertEquals(0 to null, dialogFocusPress(1, NxButton.LEFT))
    }

    @Test fun a_on_cancel_is_b_and_on_the_confirm_a() {
        assertEquals(1 to NxButton.B, dialogFocusPress(1, NxButton.A))
        assertEquals(0 to NxButton.A, dialogFocusPress(0, NxButton.A))
        assertEquals(1 to NxButton.B, dialogFocusPress(1, NxButton.B))
    }

    // The dialog has the focus alone: nothing else reaches the screen under it, UP and DOWN only to a scrolling one.
    @Test fun an_open_dialog_takes_every_other_button() {
        assertEquals(0 to null, dialogFocusPress(0, NxButton.L1))
        assertEquals(0 to null, dialogFocusPress(0, NxButton.MENU))
        assertEquals(0 to null, dialogFocusPress(0, NxButton.DOWN))
        assertEquals(0 to NxButton.DOWN, dialogFocusPress(0, NxButton.DOWN, scrolls = true))
    }

    @Test fun an_info_dialogs_one_button_keeps_the_light() {
        assertEquals(0 to null, dialogFocusPress(0, NxButton.RIGHT, count = 1))
        assertEquals(0 to NxButton.A, dialogFocusPress(0, NxButton.A, count = 1))
    }

    @Test fun every_dialog_opens_on_the_confirm_and_none_open_passes_presses() {
        val f = DialogFocus()
        assertEquals(NxButton.RIGHT, f.press(NxButton.RIGHT))   // no dialog: the screen's
        f.opened()
        assertEquals(null, f.press(NxButton.RIGHT))
        assertEquals(1, f.index)
        assertEquals(NxButton.B, f.press(NxButton.A))
        f.closed(); f.opened()
        assertEquals(0, f.index)
    }
}
