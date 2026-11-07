package com.nxredux.mobile.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** Appearance → Pad theme: the pad skins only (the drawn Charcoal / Retro / Black themes are gone). */
class PadThemeTest {
    @Test fun nxCarbonIsTheDefaultThemeAndFirst() {
        assertEquals(PadTheme.NX_CARBON, PadTheme.DEFAULT)
        assertEquals(listOf(PadTheme.NX_CARBON, PadTheme.NX_ONYX, PadTheme.NX_OBSIDIAN, PadTheme.NX_PLASMA, PadTheme.NX_EMBER, PadTheme.NX_VOLT, PadTheme.NX_TIDE), PadTheme.entries)
        assertEquals(listOf("nx-carbon", "nx-onyx", "nx-obsidian", "nx-plasma", "nx-ember", "nx-volt", "nx-tide"), PadTheme.entries.map { it.skin })
    }
}
