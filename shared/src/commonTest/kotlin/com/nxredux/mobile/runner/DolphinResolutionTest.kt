package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DeviceTraits
import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.FrontendOptions
import com.nxredux.mobile.options.WiiOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The GameCube / Wii 3D Resolution: Dolphin draws the whole picture at a whole multiple of its frame, the app picks it. */
class DolphinResolutionTest {
    private val dolphin = Res3dConsole.DOLPHIN

    @Test fun gameCubeAndWiiShareTheDolphinRows() {
        assertEquals(dolphin, Res3dConsole.forTag("GC"))
        assertEquals(dolphin, Res3dConsole.forTag("WII"))
        assertEquals("dolphin", dolphin.coreId)
        assertEquals("dolphin_efb_scale", dolphin.coreKey)
        assertEquals("3", dolphin.coreValue(3))
        assertTrue(dolphin.wholePicture)
    }

    @Test fun aPartWayScaleRoundsUp() {
        // the AYANEO: 1280 x 960, a 4:3 picture 1280 x 960 tall over 528 lines (1.82x); the Nova's 1920 x 1080 (2.05x)
        assertEquals(2, DolphinResolution.displayMax(1280, 960))
        assertEquals(3, DolphinResolution.displayMax(1920, 1080))
        assertEquals(3, DolphinResolution.displayMax(1080, 1920))   // either way up
        assertEquals(2, DolphinResolution.currentScale(960))
        assertEquals(1, DolphinResolution.currentScale(528))
        assertEquals(2, DolphinResolution.currentScale(1066))   // a pixel over a whole scale stays at it
    }

    @Test fun theFrameScaleReadsNtscAndPal() {
        assertEquals(1, DolphinResolution.frameScale(528))
        assertEquals(1, DolphinResolution.frameScale(576))   // PAL
        assertEquals(2, DolphinResolution.frameScale(1056))
        assertEquals(2, DolphinResolution.frameScale(1152))
        assertEquals(3, DolphinResolution.frameScale(1584))
    }

    @Test fun theDefaultCapFollowsTheGpu() {
        assertEquals(3, dolphin.defaultCap)   // not read yet
        try {
            DeviceTraits.gpu = "Mali-G76 MC4"
            assertEquals(1, dolphin.defaultCap)
            DeviceTraits.gpu = "Adreno (TM) 740"
            assertEquals(3, dolphin.defaultCap)
        } finally {
            DeviceTraits.gpu = null
        }
    }

    @Test fun anOldInternalResolutionBecomesTheMaxScale() {
        // The launch's path: the core key is locked, so the resolve drops its stored value; the carry comes first.
        for (tag in listOf("GC", "WII")) {
            val core = CoreRegistry.forTag(tag)!!
            fun maxScale(config: String): String? {
                val resolved = CoreConfig.resolve(core.defaultOptions, CoreConfig.parse(config), null, core.lockedOptions)
                assertEquals("1", resolved.values["dolphin_efb_scale"])
                return FrontendOptions.fromConfig(resolved.values, tag)[dolphin.maxScaleKey]
            }
            assertEquals("4", maxScale("dolphin_efb_scale = 4\n"), tag)
            val unset = if (tag == "WII") "2" else "3"
            assertEquals(unset, maxScale("dolphin_efb_scale = 1\n"), tag)
            assertEquals("2", maxScale("dolphin_efb_scale = 4\n${dolphin.maxScaleKey} = 2\n"), tag)
            assertEquals(unset, maxScale(""), tag)
        }
    }

    @Test fun theRowsShowForBothConsolesAndTheCoreOptionIsTheApps() {
        val gc = ConsoleSettings.tableFor("GC").map { it.key }
        assertEquals(listOf(dolphin.modeKey, dolphin.maxScaleKey, dolphin.scaleKey), gc.take(3))
        val wii = ConsoleSettings.tableFor("WII").map { it.key }
        assertTrue(wii.containsAll(dolphin.keys))
        assertTrue(wii.indexOf(dolphin.modeKey) > wii.indexOf(WiiOptions.CONTROLLER))
        for (tag in listOf("GC", "WII")) assertTrue("dolphin_efb_scale" in CoreRegistry.forTag(tag)!!.lockedOptions, tag)
    }
}
