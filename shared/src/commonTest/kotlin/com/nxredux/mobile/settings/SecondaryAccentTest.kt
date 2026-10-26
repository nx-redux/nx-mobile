package com.nxredux.mobile.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Secondary accent setting (the bar behind a selected option's value): Auto (the accent at 45 % brightness), then its
 * own dark presets; stored by id, Auto (the default) when missing or unknown, an old Accent preset id as the dark colour
 * of its hue.
 */
class SecondaryAccentTest {
    // Auto, which on the default White accent is NX teal: the default look is unchanged.
    @Test fun theDefaultIsAutoTealOnWhite() {
        assertEquals(SecondaryAccent.Auto, SecondaryAccent.DEFAULT)
        assertEquals(0x006666, SecondaryAccent.DEFAULT.rgb(AccentPreset.DEFAULT))
        assertEquals(0xDCDCDC, SecondaryAccent.DEFAULT.onRgb())
    }

    @Test fun autoThenItsOwnPresetsInOrder() {
        assertEquals(
            listOf(
                "Auto" to null, "Teal" to 0x006666, "Red" to 0x660000, "Rust" to 0x7C2D12, "Bronze" to 0x78350F, "Moss" to 0x365314,
                "Navy" to 0x0C4A6E, "Plum" to 0x4C1D95, "Wine" to 0x831843, "Graphite" to 0x3F3F46,
            ),
            SecondaryAccent.entries.map { it.label to (it as? SecondaryAccent.Preset)?.rgb(AccentPreset.SKY) },
        )
    }

    @Test fun everyIdParsesBackAndUnknownIsAuto() {
        for (s in SecondaryAccent.entries) assertEquals(s, SecondaryAccent.fromId(s.id))
        assertEquals(SecondaryAccent.Auto, SecondaryAccent.fromId(null))
        assertEquals(SecondaryAccent.Auto, SecondaryAccent.fromId(""))
        assertEquals(SecondaryAccent.Auto, SecondaryAccent.fromId("green"))
    }

    // Stored before the Secondary accent had its own presets: the Accent ids read as the dark colour of the same hue.
    @Test fun oldAccentIdsReadAsTheirDarkHue() {
        fun of(id: String) = (SecondaryAccent.fromId(id) as SecondaryAccent.Preset).preset
        assertEquals(
            listOf(SecondaryPreset.RUST, SecondaryPreset.BRONZE, SecondaryPreset.MOSS, SecondaryPreset.NAVY, SecondaryPreset.PLUM, SecondaryPreset.WINE, SecondaryPreset.GRAPHITE),
            listOf("orange", "amber", "lime", "sky", "violet", "pink", "white").map(::of),
        )
        assertEquals(SecondaryPreset.TEAL, of("teal"))
        assertEquals(SecondaryPreset.RED, of("red"))
    }

    @Test fun stepWrapsBothWays() {
        assertEquals(SecondaryAccent.Preset(SecondaryPreset.TEAL), SecondaryAccent.Auto.step(1))
        assertEquals(SecondaryAccent.Auto, SecondaryAccent.Preset(SecondaryPreset.GRAPHITE).step(1))
        assertEquals(SecondaryAccent.Preset(SecondaryPreset.GRAPHITE), SecondaryAccent.Auto.step(-1))
    }

    /** Each channel times 0.45, rounded to the nearest (half up). */
    @Test fun autoIsTheAccentAt45PercentWhiteGivesTeal() {
        assertEquals(0x703333, SecondaryAccent.Auto.rgb(AccentPreset.RED))     // (248, 113, 113) -> (111.6, 50.85, 50.85) -> (112, 51, 51)
        assertEquals(0x195570, SecondaryAccent.Auto.rgb(AccentPreset.SKY))     // (56, 189, 248) -> (25.2, 85.05, 111.6) -> (25, 85, 112)
        assertEquals(0x006666, SecondaryAccent.Auto.rgb(AccentPreset.WHITE))
    }

    @Test fun theTextOnTheBarIsAlwaysLight() {
        for (s in SecondaryAccent.entries) assertEquals(0xDCDCDC, s.onRgb(), s.label)
    }
}
