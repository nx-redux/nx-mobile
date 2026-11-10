package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/** The speed / delay indicators sit in the picture's top-right, never the screen's corner a pad skin holds. */
class PictureIndicatorAnchorTest {
    @Test fun inside_a_skin_screen_not_the_screen_corner() {
        // A phone under a pad skin: a 1260-wide overlay, the skin's screen at x 100..1160 from y 300. The screen's own
        // top-right corner is the skin's shell.
        assertEquals((1160 - 21) to (300 + 21), pictureIndicatorAnchor(1160, 300, 1260, cutRight = 0, cutTop = 0, margin = 21))
    }

    @Test fun a_full_screen_picture_keeps_clear_of_the_cutout() {
        // Landscape with a right-side cutout 80 px wide, and a top cutout 60 px tall: the corner moves in from both.
        assertEquals((2000 - 80 - 8) to (60 + 8), pictureIndicatorAnchor(2000, 0, 2000, cutRight = 80, cutTop = 60, margin = 8))
    }

    @Test fun a_picture_clear_of_the_cutout_is_not_moved_for_it() {
        assertEquals((1500 - 8) to (200 + 8), pictureIndicatorAnchor(1500, 200, 2000, cutRight = 80, cutTop = 60, margin = 8))
    }
}
