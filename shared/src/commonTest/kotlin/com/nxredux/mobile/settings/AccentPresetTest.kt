package com.nxredux.mobile.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** The Accent setting's presets: light Tailwind colours (zinc-200, the rest -400) in the picker's order, stored by id, White when missing or unknown. */
class AccentPresetTest {
    @Test fun presetsAreTheirColoursInOrder() {
        assertEquals(
            listOf(
                "White" to 0xE4E4E7, "Red" to 0xF87171, "Orange" to 0xFB923C, "Amber" to 0xFBBF24, "Lime" to 0xA3E635,
                "Teal" to 0x2DD4BF, "Sky" to 0x38BDF8, "Violet" to 0xA78BFA, "Pink" to 0xF472B6,
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
}
