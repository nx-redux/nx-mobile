package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.runner.WiiPad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GcPadProfileTest {
    private val gc = PadProfile.forTag("GC")

    @Test fun labels() {
        assertEquals(PadProfile.Kind.GC, gc.kind)
        assertEquals("X", gc.label(NxButton.X))
        assertEquals("Y", gc.label(NxButton.Y))
        assertEquals("B", gc.label(NxButton.B))
        assertEquals("A", gc.label(NxButton.A))
        assertEquals("Z", gc.label(NxButton.R1))
        assertEquals("L", gc.label(NxButton.L2))
        assertEquals("R", gc.label(NxButton.R2))
    }

    @Test fun l1AndSelectAreHidden() {
        assertTrue(gc.isHidden(NxButton.L1))
        assertTrue(gc.isHidden(NxButton.SELECT))
        assertFalse(gc.isHidden(NxButton.R1))
        assertFalse(gc.isHidden(NxButton.L2))
        assertFalse(gc.isHidden(NxButton.START))
    }

    @Test fun cStickYesMotionNo() {
        assertTrue(gc.cStick)
        assertFalse(gc.motionButton)
    }

    @Test fun wiiTagWithGameCubeFlagIsGc() {
        assertEquals(PadProfile.Kind.GC, PadProfile.forTag("WII", gameCube = true).kind)
        assertEquals(PadProfile.Kind.GC, PadProfile.forTag("WII", wii = WiiPad.CLASSIC, gameCube = true).kind)
        assertEquals(PadProfile.STANDARD, PadProfile.forTag("WII"))
    }
}
