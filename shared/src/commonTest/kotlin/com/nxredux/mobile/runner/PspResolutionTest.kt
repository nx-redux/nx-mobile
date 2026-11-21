package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DeviceTraits
import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.CoreConfig
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

    @Test fun aModestGpuStartsThePspAtTwo() {
        try {
            DeviceTraits.gpu = "Mali-G76 MC4"
            assertEquals(2, psp.defaultCap)
            assertEquals(3, Res3dConsole.N3DS.defaultCap)
            assertEquals(2, Nds3dResolution.valueFor(emptyMap(), current = 3, displayMax = 3, console = psp))
            assertEquals("2", FrontendOptions.fromConfig(emptyMap(), "PSP")[psp.maxScaleKey])
            DeviceTraits.gpu = "Adreno (TM) 740"
            assertEquals(3, psp.defaultCap)
        } finally {
            DeviceTraits.gpu = null
        }
        assertEquals(3, psp.defaultCap)   // not read yet
        for (modest in listOf("Adreno (TM) 610", "Adreno (TM) 619", "Adreno (TM) 642L", "Adreno (TM) 644", "Adreno (TM) 725", "Adreno (TM) 810", "Adreno (TM) 540", "Mali-G57 MC2", "PowerVR Rogue GE8320"))
            assertTrue(PspResolution.modestGpu(modest), modest)
        for (full in listOf("Adreno (TM) 640", "Adreno (TM) 660", "Adreno (TM) 730", "Adreno (TM) 750", "Adreno (TM) 830", "Samsung Xclipse 940", null))
            assertTrue(!PspResolution.modestGpu(full), full.toString())
    }

    @Test fun autoStopsAtThreeUnlessRaised() {
        assertEquals(3, Nds3dResolution.valueFor(emptyMap(), current = 4, displayMax = 4, console = psp))
        assertEquals(4, Nds3dResolution.valueFor(mapOf(psp.maxScaleKey to "4"), current = 4, displayMax = 4, console = psp))
        assertEquals(1, Nds3dResolution.valueFor(emptyMap(), current = 4, displayMax = 4, console = psp, shaded = true))
    }

    @Test fun anOldRenderingResolutionBecomesTheMaxScale() {
        // The launch's path: the core key is locked, so the resolve drops its stored value; the carry comes first.
        val core = CoreRegistry.forId("ppsspp")!!
        fun maxScale(config: String): String? {
            val resolved = CoreConfig.resolve(core.defaultOptions, CoreConfig.parse(config), null, core.lockedOptions)
            assertEquals(null, resolved.values["ppsspp_internal_resolution"]?.takeIf { it != "480x272" })
            return FrontendOptions.fromConfig(resolved.values, "PSP")[psp.maxScaleKey]
        }
        assertEquals("4", maxScale("ppsspp_internal_resolution = 1920x1088\n"))
        assertEquals("3", maxScale("ppsspp_internal_resolution = 480x272\n"))
        assertEquals("2", maxScale("ppsspp_internal_resolution = 1920x1088\n${psp.maxScaleKey} = 2\n"))
        assertEquals("3", maxScale(""))
    }

    @Test fun theRowsComeBeforeLocalWirelessAndTheCoreOptionIsTheApps() {
        val keys = ConsoleSettings.tableFor("PSP").map { it.key }
        assertEquals(listOf(psp.modeKey, psp.maxScaleKey, psp.scaleKey, PspOptions.LOCAL_WIRELESS), keys.take(4))
        val core = CoreRegistry.forId("ppsspp")!!
        assertTrue("ppsspp_internal_resolution" in core.lockedOptions)
        assertTrue(core.vulkan!!.autoOnMali)
    }
}
