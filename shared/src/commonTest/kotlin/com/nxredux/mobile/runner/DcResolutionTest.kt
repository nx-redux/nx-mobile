package com.nxredux.mobile.runner

import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.FrontendOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Dreamcast 3D Resolution on Flycast: draws the whole picture at a whole multiple of 640 x 480, the app picks it. */
class DcResolutionTest {
    private val dc = Res3dConsole.DC

    @Test fun flycastSizesTheWholePicture() {
        assertEquals(dc, Res3dConsole.forTag("DC"))
        assertEquals(CoreRegistry.forTag("DC")!!.id, dc.coreId)
        assertEquals("reicast_internal_resolution", dc.coreKey)
        assertEquals("640x480", dc.coreValue(1))
        assertEquals("1920x1440", dc.coreValue(3))
        assertEquals("5120x3840", dc.coreValue(8))   // values the core lists
        assertTrue(dc.wholePicture)
    }

    @Test fun aPartWayScaleRoundsUp() {
        // the AYANEO's 1280 x 960 is 2x; the Nova's 1920 x 1080 is 2.25x
        assertEquals(2, DcResolution.displayMax(1280, 960))
        assertEquals(3, DcResolution.displayMax(1920, 1080))
        assertEquals(3, DcResolution.displayMax(1080, 1920))   // either way up
        assertEquals(2, DcResolution.currentScale(960))
    }

    @Test fun anOldInternalResolutionBecomesTheMaxScale() {
        // The launch's path: the core key is locked, so the resolve drops its stored value; the carry comes first.
        val core = CoreRegistry.forTag("DC")!!
        fun maxScale(config: String): String? {
            val resolved = CoreConfig.resolve(core.defaultOptions, CoreConfig.parse(config), null, core.lockedOptions)
            assertEquals("640x480", resolved.values[dc.coreKey])
            return FrontendOptions.fromConfig(resolved.values, "DC")[dc.maxScaleKey]
        }
        assertEquals("2", maxScale("reicast_internal_resolution = 1280x960\n"))
        assertEquals("2", maxScale("reicast_internal_resolution = 960x720\n"))   // x1.5 rounds up
        assertEquals("8", maxScale("reicast_internal_resolution = 12800x9600\n"))   // the rows stop at 8x
        assertEquals("${dc.defaultCap}", maxScale("reicast_internal_resolution = 320x240\n"))
        assertEquals("1", maxScale("reicast_internal_resolution = 1920x1440\n${dc.maxScaleKey} = 1\n"))
    }

    @Test fun theRowsShowForDcAndTheCoreOptionIsTheApps() {
        assertEquals(listOf(dc.modeKey, dc.maxScaleKey, dc.scaleKey), ConsoleSettings.tableFor("DC").map { it.key }.take(3))
        assertTrue(dc.coreKey in CoreRegistry.forTag("DC")!!.lockedOptions)
    }
}
