package com.nxredux.mobile.settings

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** The Accent setting's presets: light Tailwind colours (zinc-200, the rest -400) in the picker's order, stored by id, White when missing or unknown. */
class AccentPresetTest {
    @Test fun presetsAreTheirColoursInOrder() {
        assertEquals(
            listOf(
                "White" to 0xE4E4E7, "Red" to 0xF87171, "Orange" to 0xFDBA74, "Amber" to 0xFBBF24, "Lime" to 0xA3E635,
                "Teal" to 0x2DD4BF, "Sky" to 0x38BDF8, "Violet" to 0xC4B5FD, "Pink" to 0xF9A8D4,
            ),
            AccentPreset.entries.map { it.label to it.rgb },
        )
        assertEquals(AccentPreset.WHITE, AccentPreset.DEFAULT)
        assertEquals("e4e4e7", AccentPreset.WHITE.hex)
        assertEquals(0xFFF87171.toInt(), AccentPreset.RED.argb)
        assertEquals("f87171", AccentPreset.RED.hex)
    }

    @Test fun everyIdParsesBackToItsPreset() {
        for (p in AccentPreset.entries) assertEquals(p, AccentPreset.fromId(p.id))
    }

    @Test fun missingOrUnknownIdsFallBackToWhite() {
        assertEquals(AccentPreset.WHITE, AccentPreset.fromId(null))
        assertEquals(AccentPreset.WHITE, AccentPreset.fromId(""))
        assertEquals(AccentPreset.WHITE, AccentPreset.fromId("nx-red"))   // a retired or foreign value
        assertEquals(AccentPreset.WHITE, AccentPreset.fromId("RED"))      // ids are lower case, as stored
    }

    @Test fun stepWrapsBothWays() {
        assertEquals(AccentPreset.RED, AccentPreset.WHITE.step(1))
        assertEquals(AccentPreset.PINK, AccentPreset.WHITE.step(-1))
        assertEquals(AccentPreset.WHITE, AccentPreset.PINK.step(1))
    }

    // Every colour preset is bright enough that a selected name stays clearly brighter than an unselected one (ink 60%)
    // and the hint grey: relative luminance 0.43 or more (WHITE is near 0.78). Red is the one exception (0.33): a red
    // bright enough turns pink, so it stays red-400.
    @Test fun every_colour_preset_but_red_has_a_luminance_of_043_or_more() {
        fun channel(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        for (p in AccentPreset.entries - AccentPreset.WHITE - AccentPreset.RED) {
            val l = 0.2126 * channel(p.rgb shr 16 and 0xFF) + 0.7152 * channel(p.rgb shr 8 and 0xFF) + 0.0722 * channel(p.rgb and 0xFF)
            assertTrue(l >= 0.43, "${p.label}: $l")
        }
    }
}
