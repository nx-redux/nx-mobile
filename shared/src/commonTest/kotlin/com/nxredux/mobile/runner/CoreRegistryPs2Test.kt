package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.cheats.Ps2Patches
import com.nxredux.mobile.options.CoreConfig
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

    @Test fun patchOptionsLockedOffCheatsUnlocked() {   // Widescreen / No-Interlacing live in the Patches tab now
        for (k in Ps2Patches.RETIRED_OPTIONS) {
            assertEquals("disabled", core.defaultOptions[k], k)
            assertTrue(k in core.lockedOptions, k)
        }
        assertEquals(setOf("armsx2_widescreen_patches", "armsx2_no_interlacing_patches"), Ps2Patches.RETIRED_OPTIONS)
        assertEquals("disabled", core.defaultOptions["armsx2_cheats"])
        assertFalse("armsx2_cheats" in core.lockedOptions)
        assertTrue(core.patchesTab)
    }

    @Test fun a_save_never_overwrites_a_retired_patch_option() {   // its stored value stays the new-game default
        val running = listOf("armsx2_widescreen_patches" to "disabled", "armsx2_upscale" to "2x")
        // The stored map holds the descriptor's default for the retired keys, so they leave the running values instead.
        val stored = mapOf("armsx2_widescreen_patches" to "disabled", "armsx2_upscale" to "2x")
        assertEquals(listOf("armsx2_upscale" to "2x"), CoreConfig.valuesToSave(Ps2Patches.withoutRetired(running), stored, emptySet()))
    }

    @Test fun softwareThreadsLocked() {
        assertEquals("2", core.defaultOptions["armsx2_sw_threads"])
        assertTrue("armsx2_sw_threads" in core.lockedOptions)
    }
}
