package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DeviceTraits
import com.nxredux.mobile.options.ConsoleSettings
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.FrontendOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The PlayStation 3D Resolution on PCSX-ReARMed: native or Enhanced Resolution's 2x, the app picks it. */
class PcsxResolutionTest {
    private val pcsx = Res3dConsole.PCSX

    @Test fun psGetsTheRowsUpTo2x() {
        assertEquals(pcsx, Res3dConsole.forTag("PS"))
        assertEquals("pcsx_rearmed", pcsx.coreId)
        assertEquals(CoreRegistry.forTag("PS")!!.id, pcsx.coreId)
        assertEquals("disabled", pcsx.coreValue(1))
        assertEquals("enabled", pcsx.coreValue(2))
        assertTrue(pcsx.wholePicture)
        val rows = ConsoleSettings.tableFor("PS", displayMax = 4)
        assertEquals(listOf(pcsx.modeKey, pcsx.maxScaleKey), rows.map { it.key }.take(2))
        assertEquals(listOf("1", "2"), rows.first { it.key == pcsx.maxScaleKey }.values)   // the core has 1x and 2x only
        assertTrue(rows.none { it.key == pcsx.scaleKey })   // Performance is native
        assertTrue(ConsoleSettings.tableFor("PSX").none { it.key in pcsx.keys })
    }

    @Test fun anUnsetGameStartsAt2xOnEveryGpu() {
        try {
            DeviceTraits.gpu = "Mali-G76 MC4"   // the AYANEO holds 60 fps at 2x
            assertEquals(2, pcsx.defaultCap)
            assertEquals(2, Nds3dResolution.valueFor(emptyMap(), null, displayMax = 4, console = pcsx))
        } finally {
            DeviceTraits.gpu = null
        }
    }

    @Test fun theModesPickNativeOr2x() {
        fun value(vararg kv: Pair<String, String>, current: Int? = null, shaded: Boolean = false) =
            Nds3dResolution.valueFor(mapOf(*kv), current, displayMax = 4, console = pcsx, shaded = shaded)
        assertEquals(2, value(pcsx.modeKey to Nds3dMode.QUALITY.value))
        assertEquals(1, value(pcsx.modeKey to Nds3dMode.PERFORMANCE.value))
        assertEquals(1, value(pcsx.maxScaleKey to "1"))
        assertEquals(1, value(shaded = true))
        assertEquals(1, Nds3dResolution.valueFor(emptyMap(), null, displayMax = 1, console = pcsx))   // a picture shown at 1x
    }

    @Test fun anOldEnhancedResolutionBecomesTheMaxScale() {
        val core = CoreRegistry.forTag("PS")!!
        fun maxScale(config: String): String? {
            val resolved = CoreConfig.resolve(core.defaultOptions, CoreConfig.parse(config), null, core.lockedOptions)
            assertEquals("disabled", resolved.values[pcsx.coreKey])
            return FrontendOptions.fromConfig(resolved.values, "PS")[pcsx.maxScaleKey]
        }
        assertEquals("2", maxScale("pcsx_rearmed_neon_enhancement_enable = enabled\n"))
        assertEquals("1", maxScale("pcsx_rearmed_neon_enhancement_enable = enabled\n${pcsx.maxScaleKey} = 1\n"))
        assertEquals("2", maxScale(""))
        assertTrue(pcsx.coreKey in core.lockedOptions)
    }
}
