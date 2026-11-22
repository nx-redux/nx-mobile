package com.nxredux.mobile.runner

import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.FrontendOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The N64 3D Resolution on Mupen64Plus-Next: GLideN64 draws the whole picture at a whole multiple of 320 x 240, ParaLLEl-RDP at 1, 2, 4 or 8x. */
class N64ResolutionTest {
    private val n64 = Res3dConsole.N64

    @Test fun glideN64SizesTheWholePicture() {
        assertEquals(n64, Res3dConsole.forTag("N64"))
        assertEquals(CoreRegistry.forTag("N64")!!.id, n64.coreId)
        assertEquals("mupen64plus-43screensize", n64.coreKey)
        assertEquals("320x240", n64.coreValue(1))
        assertEquals("960x720", n64.coreValue(3))
        assertEquals("2560x1920", n64.coreValue(8))   // values the core lists
        assertTrue(n64.wholePicture)
    }

    @Test fun aPickByHandGetsTheLargestUpscalingNotAboveIt() {
        // held only by the display's list: on a slow GPU or driver too
        for (slow in listOf(false, true)) {
            assertEquals(listOf(1, 2, 2, 4, 4, 4, 4, 8), (1..8).map { N64Resolution.parallelUpscaling(it, auto = false, slowParallel = slow) })
        }
        assertEquals(mapOf(N64Resolution.PARALLEL_KEY to "4x"), n64.alsoSet(5, auto = false, slowParallel = true))
        assertTrue(Res3dConsole.DC.alsoSet(3, auto = false, slowParallel = false).isEmpty())
    }

    @Test fun autoStopsAt2xAndAtNativeWhereParallelRunsSlowly() {
        assertEquals(listOf(1, 2, 2, 2, 2, 2, 2, 2), (1..8).map { N64Resolution.parallelUpscaling(it, auto = true, slowParallel = false) })   // the Nova on Turnip: 45-51 fps at 4x
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 1, 1), (1..8).map { N64Resolution.parallelUpscaling(it, auto = true, slowParallel = true) })
    }

    @Test fun parallelRunsSlowlyOnAModestGpuOrAnAdrenosOwnDriver() {
        assertTrue(N64Resolution.slowParallel("Mali-G76 MC4", customDriver = false))   // the AYANEO: ~40 fps at 2x
        assertTrue(N64Resolution.slowParallel("Mali-G76 MC4", customDriver = true))    // malisx2: ~35
        assertTrue(N64Resolution.slowParallel("Adreno (TM) 740", customDriver = false))   // the Nova's own: ~40 at 2x, ~48 at 1x
        assertFalse(N64Resolution.slowParallel("Adreno (TM) 740", customDriver = true))   // Turnip: 60 at 2x
        assertFalse(N64Resolution.slowParallel(null, customDriver = false))
    }

    @Test fun aPartWayScaleRoundsUp() {
        // the AYANEO's 1280 x 960 is 4x; the Nova's 1920 x 1080 is 4.5x
        assertEquals(4, N64Resolution.displayMax(1280, 960))
        assertEquals(5, N64Resolution.displayMax(1920, 1080))
        assertEquals(5, N64Resolution.displayMax(1080, 1920))   // either way up
        assertEquals(4, N64Resolution.currentScale(960))
    }

    @Test fun anOldResolutionBecomesTheMaxScale() {
        // The launch's path: the core keys are locked, so the resolve drops their stored values; the carry comes first.
        val core = CoreRegistry.forTag("N64")!!
        fun maxScale(config: String): String? {
            val resolved = CoreConfig.resolve(core.defaultOptions, CoreConfig.parse(config), null, core.lockedOptions)
            assertEquals("320x240", resolved.values[n64.coreKey])
            assertEquals("0", resolved.values["mupen64plus-EnableNativeResFactor"])
            return FrontendOptions.fromConfig(resolved.values, "N64")[n64.maxScaleKey]
        }
        val cap = "${n64.defaultCap}"
        assertEquals("6", maxScale("mupen64plus-43screensize = 1920x1440\n"))
        assertEquals("2", maxScale("mupen64plus-43screensize = 320x240\nmupen64plus-EnableNativeResFactor = 2\n"))   // the factor wins
        assertEquals("5", maxScale("mupen64plus-43screensize = 1440x1080\n"))   // x4.5 rounds up
        assertEquals("8", maxScale("mupen64plus-43screensize = 3840x2880\n"))   // the rows stop at 8x
        assertEquals(cap, maxScale("mupen64plus-43screensize = 640x480\n"))   // the core's default says nothing
        assertEquals("3", maxScale("mupen64plus-aspect = 16:9 adjusted\nmupen64plus-169screensize = 1280x720\n"))
        assertEquals(cap, maxScale("mupen64plus-aspect = 16:9\nmupen64plus-169screensize = 960x540\n"))
        assertEquals(cap, maxScale("mupen64plus-aspect = 4:3\nmupen64plus-169screensize = 1920x1080\n"))   // not the one in use
        assertEquals("1", maxScale("mupen64plus-43screensize = 1920x1440\n${n64.maxScaleKey} = 1\n"))
    }

    @Test fun theRowsShowForN64AndTheCoreOptionsAreTheApps() {
        assertEquals(listOf(n64.modeKey, n64.maxScaleKey, n64.scaleKey), ConsoleSettings.tableFor("N64").map { it.key }.take(3))
        val locked = CoreRegistry.forTag("N64")!!.lockedOptions
        assertTrue(listOf(n64.coreKey, N64Resolution.PARALLEL_KEY, "mupen64plus-169screensize", "mupen64plus-EnableNativeResFactor").all { it in locked })
    }
}
