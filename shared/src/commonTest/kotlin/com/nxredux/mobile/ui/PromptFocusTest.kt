package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A prompt's pad focus ([promptFocusStep]): -1 the field, 0 the confirm, 1 the cancel. */
class PromptFocusTest {
    @Test fun down_moves_from_the_field_to_the_confirm_and_up_goes_back() {
        assertEquals(0, promptFocusStep(-1, NxButton.DOWN))
        assertEquals(1, promptFocusStep(1, NxButton.DOWN))   // already on the buttons: stays
        assertEquals(-1, promptFocusStep(0, NxButton.UP))
    }

    @Test fun left_and_right_move_between_the_buttons_only() {
        assertEquals(1, promptFocusStep(0, NxButton.RIGHT))
        assertEquals(0, promptFocusStep(1, NxButton.LEFT))
        assertEquals(-1, promptFocusStep(-1, NxButton.RIGHT))   // in the field: the cursor's, not the focus's
    }

    @Test fun other_buttons_leave_it_to_the_prompt() {
        assertNull(promptFocusStep(0, NxButton.A))
        assertNull(promptFocusStep(-1, NxButton.B))
    }
}
