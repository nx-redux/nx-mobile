package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DeviceTraits
import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.FrontendOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The PlayStation 3D Resolution on SwanStation: draws the whole picture at a whole multiple of 320 x 240, the app picks it. */
class Ps1ResolutionTest {
    private val ps1 = Res3dConsole.PS1

    @Test fun swanStationHasItsOwnRows() {
        assertEquals(ps1, Res3dConsole.forTag("PSX"))
        assertEquals("swanstation", ps1.coreId)
        assertEquals("swanstation_GPU_ResolutionScale", ps1.coreKey)
        assertEquals("4", ps1.coreValue(4))
        assertTrue(ps1.wholePicture)
        assertTrue(ConsoleSettings.tableFor("PS").none { it.key in ps1.keys })   // PCSX-ReARMed's are its own
    }

    @Test fun aPartWayScaleRoundsUp() {
        // the AYANEO: 1280 x 960, a 4:3 picture 1280 x 960 over 240 lines (4x); the Nova's 1920 x 1080 (4.5x)
        assertEquals(4, Ps1Resolution.displayMax(1280, 960))
        assertEquals(5, Ps1Resolution.displayMax(1920, 1080))
        assertEquals(5, Ps1Resolution.displayMax(1080, 1920))   // either way up
        assertEquals(4, Ps1Resolution.currentScale(960))
        assertEquals(2, Ps1Resolution.currentScale(481))   // a pixel over a whole scale stays at it
        assertEquals(1, Ps1Resolution.currentScale(100))
    }

    @Test fun theDefaultCapIsFourOnEveryGpu() {
        assertEquals(4, ps1.defaultCap)
        try {
            DeviceTraits.gpu = "Mali-G76 MC4"
            assertEquals(4, ps1.defaultCap)   // the AYANEO holds 60 fps at 4x
        } finally {
            DeviceTraits.gpu = null
        }
    }

    @Test fun anOldInternalResolutionScaleBecomesTheMaxScale() {
        // The launch's path: the core key is locked, so the resolve drops its stored value; the carry comes first.
        val core = CoreRegistry.forTag("PSX")!!
        fun maxScale(config: String): String? {
            val resolved = CoreConfig.resolve(core.defaultOptions, CoreConfig.parse(config), null, core.lockedOptions)
            assertEquals("1", resolved.values["swanstation_GPU_ResolutionScale"])
            return FrontendOptions.fromConfig(resolved.values, "PSX")[ps1.maxScaleKey]
        }
        assertEquals("6", maxScale("swanstation_GPU_ResolutionScale = 6\n"))
        assertEquals("8", maxScale("swanstation_GPU_ResolutionScale = 16\n"))   // the rows stop at 8x
        assertEquals("4", maxScale("swanstation_GPU_ResolutionScale = 1\n"))
        assertEquals("2", maxScale("swanstation_GPU_ResolutionScale = 6\n${ps1.maxScaleKey} = 2\n"))
        assertEquals("4", maxScale(""))
    }

    @Test fun theRowsShowForPsxAndTheCoreOptionIsTheApps() {
        val keys = ConsoleSettings.tableFor("PSX").map { it.key }
        assertEquals(listOf(ps1.modeKey, ps1.maxScaleKey, ps1.scaleKey), keys.take(3))
        assertTrue("swanstation_GPU_ResolutionScale" in CoreRegistry.forTag("PSX")!!.lockedOptions)
    }
}
