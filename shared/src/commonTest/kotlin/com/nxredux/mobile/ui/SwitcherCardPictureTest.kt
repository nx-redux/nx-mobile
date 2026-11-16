package com.nxredux.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwitcherCardPictureTest {
    @Test fun resume_frame_then_screenshot_art_then_the_box() {
        assertEquals("p", switcherCardPicture("p", "s", emptySet()))
        assertEquals("s", switcherCardPicture(null, "s", emptySet()))
        assertNull(switcherCardPicture(null, "s", setOf("s")))   // failed to load: the box chain follows
        assertNull(switcherCardPicture(null, null, emptySet()))
    }
}
