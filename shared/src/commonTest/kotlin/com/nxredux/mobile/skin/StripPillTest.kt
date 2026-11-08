package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertEquals

/** A touch shortcut's pill on the overlay ([stripPill]): round its word, inside its slot, centred on it. */
class StripPillTest {
    @Test fun theWordPaddedCentredInTheSlot() {
        // a slot 230 wide, 86 tall (hh 43), a word 80 wide, at k 1: 80 + 24 each side
        val r = stripPill(70f, 100f, 115f, 43f, 80f, 1f)
        assertEquals(128f, r.width, 0.01f); assertEquals(68.8f, r.height, 0.01f)
        assertEquals(70f - 64f, r.x, 0.01f); assertEquals(100f - 34.4f, r.y, 0.01f)
    }

    @Test fun aShortWordStillAPill() {   // at least as wide as it is tall x 1.6
        val r = stripPill(0f, 0f, 115f, 43f, 4f, 1f)
        assertEquals(68.8f * 1.6f, r.width, 0.01f)
    }

    @Test fun aLongWordNoWiderThanItsSlot() {
        val r = stripPill(0f, 0f, 50f, 43f, 300f, 1f)
        assertEquals(100f - 12f, r.width, 0.01f)
    }
}
