package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.NetplayCores
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreRegistryPs2Test {
    private val core get() = CoreRegistry.forTag("PS2")!!

    @Test fun armsx2LoadsThroughTheVfs() {
        assertEquals(LoadMode.FILE_DESCRIPTOR, core.loadMode)
    }

    @Test fun ps2RequiresAnyBin() {
        assertEquals(listOf(BiosNeed(emptyList(), anySuffix = ".bin")), CoreRegistry.requiredBios("PS2"))
    }
    @Test fun noCheatsNoRewindTwoPlayers() {
        assertEquals(false, core.cheats)
        assertEquals(false, core.rewind)
        assertEquals(2, CoreRegistry.playersFor("PS2", core))
    }
    @Test fun noNetplay() {   // two local players only: PS2 is not in the netplay table
        assertFalse(NetplayCores.supported("PS2"))
    }
    @Test fun biosOptionLocked() {
        assertEquals("auto", core.defaultOptions["armsx2_bios"])
        assertTrue("armsx2_bios" in core.lockedOptions)
    }

    @Test fun upscaleLocked() {
        assertEquals("2x", core.defaultOptions["armsx2_upscale"])
        assertTrue("armsx2_upscale" in core.lockedOptions)
    }

    @Test fun patchesAndCheatsOffButUnlocked() {   // the player's to turn on; their files come from Cheats/PS2
        for (k in listOf("armsx2_widescreen_patches", "armsx2_no_interlacing_patches", "armsx2_cheats")) {
            assertEquals("disabled", core.defaultOptions[k], k)
            assertFalse(k in core.lockedOptions, k)
        }
    }

    @Test fun softwareThreadsLocked() {
        assertEquals("2", core.defaultOptions["armsx2_sw_threads"])
        assertTrue("armsx2_sw_threads" in core.lockedOptions)
    }
}
