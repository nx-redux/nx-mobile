package com.nxredux.mobile.runner

import com.nxredux.mobile.options.DcOptions
import com.nxredux.mobile.options.SaturnOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Saturn 3D Resolution on YabaSanshiro: the core draws the whole picture at 1x, 2x or 4x, applied mid-game. */
class SaturnResolutionTest {
    private val saturn = Res3dConsole.SATURN

    @Test fun tagMapsToSaturn() {
        assertEquals(saturn, Res3dConsole.forTag("SATURN"))
        assertTrue(SaturnOptions.appliesTo("SATURN"))
        assertFalse(SaturnOptions.appliesTo("DC"))
        assertTrue(saturn.wholePicture)
    }

    @Test fun coreValueIsTheCoresMode() {
        // No 3x: down to 2x, never above what the display reaches.
        assertEquals(listOf("original", "2x", "2x", "4x"), (1..4).map { saturn.coreValue(it) })
    }

    @Test fun displayMaxFollowsTheNativePicture() {
        assertEquals(4, SaturnResolution.displayMax(1280, 960))
        assertEquals(1, SaturnResolution.displayMax(320, 224))
        assertEquals(4, SaturnResolution.displayMax(2400, 1080))
    }

    @Test fun rowsOfferOnlyTheScalesTheCoreHas() {
        val rows = SaturnOptions.resolutionRows(4)
        assertEquals(listOf("1", "2", "4"), rows.first { it.key == saturn.maxScaleKey }.values)
        assertEquals(listOf("1", "2"), rows.first { it.key == saturn.scaleKey }.values)   // Performance stops at half the display's reach
        assertEquals(listOf("1", "2", "4"), SaturnOptions.resolutionRows(null).first { it.key == saturn.scaleKey }.values)
        assertEquals("2", rows.first { it.key == saturn.maxScaleKey }.default)
        assertEquals(listOf("1", "2"), SaturnOptions.resolutionRows(2).first { it.key == saturn.maxScaleKey }.values)
        assertEquals(listOf("1", "2", "4"), SaturnOptions.resolutionRows(null).first { it.key == saturn.maxScaleKey }.values)
    }

    @Test fun otherConsolesKeepEveryScale() {
        val dc = Res3dConsole.DC
        assertEquals((1..8).map { it.toString() }, DcOptions.resolutionRows(null).first { it.key == dc.maxScaleKey }.values)
        assertEquals(listOf("1", "2", "3"), DcOptions.resolutionRows(3).first { it.key == dc.maxScaleKey }.values)
    }
}
