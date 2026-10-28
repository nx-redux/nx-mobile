package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A bar too full drops A Select and B Back first: every list's A selects and B goes back. */
class PlainHintTest {
    @Test fun only_a_select_and_b_back_are_plain() {
        assertTrue(plainHint(Hint(NxButton.A, "Select")))
        assertTrue(plainHint(Hint(NxButton.B, "Back")))
        assertFalse(plainHint(Hint(NxButton.A, "Resume")))   // an A that does something particular stays
        assertFalse(plainHint(Hint(NxButton.B, "Cancel")))
        assertFalse(plainHint(Hint(NxButton.X, "Select")))
    }
}
