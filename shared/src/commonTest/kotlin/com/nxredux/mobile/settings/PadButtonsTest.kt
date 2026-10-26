package com.nxredux.mobile.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** Appearance → Pad buttons: stored by name, Grey unless Outline was stored. */
class PadButtonsTest {
    @Test fun everySetReadsBackByItsName() {
        for (b in PadButtons.entries) assertEquals(b, PadButtons.fromStored(b.name))
    }

    @Test fun missingOrUnknownReadsAsGrey() {
        assertEquals(PadButtons.GREY, PadButtons.DEFAULT)
        assertEquals(PadButtons.GREY, PadButtons.fromStored(null))
        assertEquals(PadButtons.GREY, PadButtons.fromStored("GARBAGE"))
    }

    @Test fun theLabels() {
        assertEquals(listOf("Grey", "Outline"), PadButtons.entries.map { it.label })
    }

    @Test fun charcoalStaysTheDefaultThemeWithBlackAdded() {
        assertEquals(PadTheme.CHARCOAL, PadTheme.DEFAULT)
        assertEquals(listOf(PadTheme.CHARCOAL, PadTheme.RETRO, PadTheme.BLACK), PadTheme.entries)
    }
}
