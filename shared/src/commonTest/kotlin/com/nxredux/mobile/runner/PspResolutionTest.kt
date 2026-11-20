package com.nxredux.mobile.runner

import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.FrontendOptions
import com.nxredux.mobile.options.PspOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The PSP 3D Resolution: PPSSPP draws the whole picture at a whole multiple of 480 x 272, the app picks the multiple. */
class PspResolutionTest {
    private val psp = Res3dConsole.PSP

    @Test fun thePspIsAResConsoleOnPpsspp() {
        assertEquals(psp, Res3dConsole.forTag("PSP"))
        assertEquals("ppsspp", psp.coreId)
        assertEquals("ppsspp_internal_resolution", psp.coreKey)
        assertEquals(3, psp.defaultCap)
    }

    @Test fun theCoreTakesTheScaleAsASize() {
        assertEquals("480x272", psp.coreValue(1))
        assertEquals("1440x816", psp.coreValue(3))
        assertEquals("4800x2720", psp.coreValue(10))
    }

    @Test fun aPartWayScaleRoundsUp() {
        // the AYANEO: 1280 x 960, the 16:9 picture 1280 x 720 (2.65x); the Nova's top screen 1920 x 1080 (3.97x)
        assertEquals(3, PspResolution.displayMax(1280, 960))
        assertEquals(4, PspResolution.displayMax(1920, 1080))
        assertEquals(4, PspResolution.displayMax(1080, 1920))   // either way up
        assertEquals(3, PspResolution.currentScale(720))
        assertEquals(4, PspResolution.currentScale(1080))
        assertEquals(2, PspResolution.currentScale(545))   // a pixel over a whole scale stays at it
        assertEquals(1, PspResolution.currentScale(100))
    }

    @Test fun theFrameScaleReadsTheCroppedFrameToo() {
        // "crop to 16:9" sends 270 rows a scale
        assertEquals(1, PspResolution.frameScale(270))
        assertEquals(2, PspResolution.frameScale(540))
        assertEquals(4, PspResolution.frameScale(1088))
    }

    @Test fun thePspStartsAtOneAndTheOthersAtTheirScale() {
        assertEquals(1, psp.launchScale(3))
        assertEquals(3, Res3dConsole.N3DS.launchScale(3))
        assertEquals(4, Res3dConsole.DS.launchScale(4))
        assertEquals(2, Res3dConsole.PS2.launchScale(2))
    }

    @Test fun autoStopsAtThreeUnlessRaised() {
        assertEquals(3, Nds3dResolution.valueFor(emptyMap(), current = 4, displayMax = 4, console = psp))
        assertEquals(4, Nds3dResolution.valueFor(mapOf(psp.maxScaleKey to "4"), current = 4, displayMax = 4, console = psp))
        assertEquals(1, Nds3dResolution.valueFor(emptyMap(), current = 4, displayMax = 4, console = psp, shaded = true))
    }

    @Test fun anOldRenderingResolutionBecomesTheMaxScale() {
        assertEquals("4", FrontendOptions.fromConfig(mapOf("ppsspp_internal_resolution" to "1920x1088"), "PSP")[psp.maxScaleKey])
        assertEquals("3", FrontendOptions.fromConfig(mapOf("ppsspp_internal_resolution" to "480x272"), "PSP")[psp.maxScaleKey])
        assertEquals("2", FrontendOptions.fromConfig(mapOf("ppsspp_internal_resolution" to "1920x1088", psp.maxScaleKey to "2"), "PSP")[psp.maxScaleKey])
        assertEquals("3", FrontendOptions.fromConfig(emptyMap(), "PSP")[psp.maxScaleKey])
    }

    @Test fun theRowsComeBeforeLocalWirelessAndTheCoreOptionIsTheApps() {
        val keys = ConsoleSettings.tableFor("PSP").map { it.key }
        assertEquals(listOf(psp.modeKey, psp.maxScaleKey, psp.scaleKey, PspOptions.LOCAL_WIRELESS), keys.take(4))
        val core = CoreRegistry.forId("ppsspp")!!
        assertTrue("ppsspp_internal_resolution" in core.lockedOptions)
        assertTrue(core.vulkan!!.autoOnMali)
    }
}
