package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.AccentPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Home's "Pick a game" picture: `nxabstract://accent/<rrggbb>`, the fixed seed's plan in the accent's palette. */
class AccentArtTest {
    @Test fun theUrlCarriesTheColourAsSixHexDigits() {
        assertEquals("nxabstract://accent/e4e4e7", abstractAccentArtUrl(AccentPreset.WHITE.rgb))
        assertEquals("nxabstract://accent/f87171", abstractAccentArtUrl(AccentPreset.RED.rgb))
        assertEquals("nxabstract://accent/00000a", abstractAccentArtUrl(0x0A))
        assertEquals("nxabstract://accent/f87171", abstractAccentArtUrl(AccentPreset.RED.argb))   // alpha dropped
        assertNotEquals(abstractAccentArtUrl(AccentPreset.WHITE.rgb), abstractAccentArtUrl(AccentPreset.SKY.rgb))   // the cache key differs per colour
    }

    @Test fun theUrlParsesBackAndNoOtherFormDoes() {
        for (p in AccentPreset.entries) assertEquals(p.rgb, abstractAccentOf(abstractAccentArtUrl(p.rgb)))
        assertEquals(0xDC2626, abstractAccentOf("nxabstract://accent/DC2626"))
        assertNull(abstractAccentOf(abstractArtUrl("MD", "Sonic.zip")))
        assertNull(abstractAccentOf(abstractAppArtUrl("com.example/.Main")))
        assertNull(abstractAccentOf("nxabstract://accent/dc262"))
        assertNull(abstractAccentOf("nxabstract://accent/zzzzzz"))
        // and the other forms do not take it
        assertNull(abstractSeedOf(abstractAccentArtUrl(0xDC2626)))
        assertNull(abstractAppArtOf(abstractAccentArtUrl(0xDC2626)))
    }

    @Test fun thePaletteIsTheAccentAndTwoStepsOfItsHue() {
        val red = accentPalette(AccentPreset.RED.rgb)
        assertEquals(3, red.size)
        val base = argbHsla(AccentPreset.RED.argb)
        assertEquals(base.hue, red[0].hue, 0.01)
        assertEquals(base.lightness, red[0].lightness, 0.01)
        for (c in red) { assertEquals(base.hue, c.hue, 0.01); assertEquals(base.saturation, c.saturation, 0.01) }
        assertEquals(red.map { it.lightness }.toSet().size, 3)
        assertEquals(withSteps(listOf(base)), red)   // the single-hue derivation an icon gets
        assertEquals(listOf(71L, 46L, 21L), red.map { kotlin.math.round(it.lightness).toLong() })   // #F87171 steps darker
    }

    @Test fun whiteGivesGreyscaleSteps() {
        val white = accentPalette(AccentPreset.WHITE.rgb)
        assertEquals(3, white.size)
        assertTrue(white.all { it.saturation == 0.0 })
        assertEquals(listOf(90L, 65L, 40L), white.map { kotlin.math.round(it.lightness).toLong() })   // zinc-200 is 90% light
    }

    @Test fun eachPresetGetsItsOwnPicture() {
        val seed = abstractSeed(PICK_A_GAME_ART_KEY)
        val plans = AccentPreset.entries.map { abstractPlan(seed, palette = accentPalette(it.rgb)) }
        assertEquals(plans.size, plans.map { it.stops }.toSet().size)
        // the same layout every time: only the colours change
        assertTrue(plans.all { p -> p.shapes.map { it.x to it.y } == plans[0].shapes.map { it.x to it.y } })
    }

    // Pick a game's picture is dark and subtle: a near-black ground tinted by the accent, the shapes in the accent's hue,
    // fainter than a game's picture; the layout stays the seed's.
    @Test fun thePickAGamePictureIsDarkWithTheAccentInIt() {
        val seed = abstractSeed(PICK_A_GAME_ART_KEY)
        val red = argbHsla(AccentPreset.RED.argb)
        val plan = accentArtPlan(seed, AccentPreset.RED.rgb)
        assertTrue(plan.stops.all { it.lightness <= 8.0 }, "${plan.stops}")
        assertTrue(plan.stops.all { kotlin.math.abs(it.hue - red.hue) < 0.01 })
        assertTrue(plan.shapes.all { kotlin.math.abs(it.color.hue - red.hue) < 0.01 && it.color.lightness in 28.0..45.0 })
        val plain = abstractPlan(seed)
        assertEquals(plain.shapes.map { it.x to it.y }, plan.shapes.map { it.x to it.y })
        assertTrue(plan.shapes.zip(plain.shapes).all { (a, b) -> a.color.alpha < b.color.alpha })
        val white = accentArtPlan(seed, AccentPreset.WHITE.rgb)
        assertTrue(white.stops.all { it.saturation == 0.0 && it.lightness <= 8.0 })
    }
}
