package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every console logo fills the same optical area in its cell, within the cell's limits. */
class ConsoleLogoBoxTest {
    private val cellW = 120f
    private val cellH = 110f

    @Test fun a_thin_wordmark_and_a_tall_logo_take_the_same_area() {
        val (tw, th) = consoleLogoBox(0.25f, cellW, cellH)    // a one-line wordmark (Dreamcast)
        val (gw, gh) = consoleLogoBox(0.45f, cellW, cellH)    // a two-line logo (Game Boy Advance)
        assertEquals(tw * th, gw * gh, 0.5f)
        assertTrue(tw > gw)                                   // the wordmark grows wider, the tall logo shrinks
    }

    @Test fun a_very_thin_logo_is_held_to_the_cells_width() {
        val (w, h) = consoleLogoBox(0.05f, cellW, cellH)      // Final Burn Neo's strip
        assertEquals(0.82f * cellW, w, 0.01f)
        assertEquals(w * 0.05f, h, 0.01f)
    }
}
