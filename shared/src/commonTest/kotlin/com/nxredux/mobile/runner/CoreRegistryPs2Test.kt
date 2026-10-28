package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreRegistryPs2Test {
    @Test fun armsx2LoadsThroughTheVfs() {
        assertEquals(LoadMode.FILE_DESCRIPTOR, CoreRegistry.forTag("PS2")!!.loadMode)
    }

    @Test fun ps2RequiresAnyBin() {
        assertEquals(listOf(BiosNeed(emptyList(), anySuffix = ".bin")), CoreRegistry.requiredBios("PS2"))
    }
    @Test fun noCheatsNoRewindOnePlayer() {
        val core = CoreRegistry.forTag("PS2")!!
        assertEquals(false, core.cheats)
        assertEquals(false, core.rewind)
        assertEquals(1, CoreRegistry.playersFor("PS2", core))
    }
    @Test fun biosOptionLocked() {
        val core = CoreRegistry.forTag("PS2")!!
        assertEquals("auto", core.defaultOptions["armsx2_bios"])
        assertTrue("armsx2_bios" in core.lockedOptions)
    }

    @Test fun upscaleLocked() {
        val core = CoreRegistry.forTag("PS2")!!
        assertEquals("2x", core.defaultOptions["armsx2_upscale"])
        assertTrue("armsx2_upscale" in core.lockedOptions)
    }
}
