package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.runner.WiiPad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WiiPadProfileTest {
    private fun p(pad: WiiPad) = PadProfile.forTag("WII", wii = pad)
    private val face = listOf(NxButton.X, NxButton.Y, NxButton.B, NxButton.A)

    @Test fun sidewaysFollowsTheCoresRotatedButtons() {
        assertEquals(listOf("A", "B", "1", "2"), face.map(p(WiiPad.SIDEWAYS)::label))
        assertEquals("Shake", p(WiiPad.SIDEWAYS).label(NxButton.R2))
        assertEquals(listOf("−", "+"), listOf(NxButton.SELECT, NxButton.START).map(p(WiiPad.SIDEWAYS)::label))
        assertTrue(listOf(NxButton.L1, NxButton.R1, NxButton.L2).all(p(WiiPad.SIDEWAYS)::isHidden))
        assertFalse(p(WiiPad.SIDEWAYS).isHidden(NxButton.R2))
    }

    @Test fun upright() {
        assertEquals(listOf("1", "2", "B", "A"), face.map(p(WiiPad.UPRIGHT)::label))
        assertTrue(p(WiiPad.UPRIGHT).isHidden(NxButton.L2))
    }

    @Test fun nunchuk() {
        val n = p(WiiPad.NUNCHUK)
        assertEquals(listOf("C", "Z", "B", "A"), face.map(n::label))
        assertEquals(listOf("−", "+", "Shake N", "Shake"), listOf(NxButton.L1, NxButton.R1, NxButton.L2, NxButton.R2).map(n::label))
        assertEquals(listOf("2", "1"), listOf(NxButton.SELECT, NxButton.START).map(n::label))
        assertFalse(listOf(NxButton.L1, NxButton.R1, NxButton.L2, NxButton.R2).any(n::isHidden))
    }

    @Test fun classicHasBothSticksAndNoMotionButton() {
        val c = p(WiiPad.CLASSIC)
        assertEquals(listOf("x", "y", "b", "a"), face.map(c::label))
        assertEquals(listOf("L", "R", "ZL", "ZR"), listOf(NxButton.L1, NxButton.R1, NxButton.L2, NxButton.R2).map(c::label))
        assertTrue(c.cStick)
        assertFalse(c.motionButton)
        assertTrue(p(WiiPad.SIDEWAYS).motionButton && p(WiiPad.UPRIGHT).motionButton && p(WiiPad.NUNCHUK).motionButton)
        assertFalse(p(WiiPad.NUNCHUK).cStick)
    }

    @Test fun menusGoByPositionAndOtherTagsIgnoreTheWiiPad() {
        assertEquals(NxButton.A, p(WiiPad.SIDEWAYS).menuButton(NxButton.A))
        assertEquals(PadProfile.GC, PadProfile.forTag("GC", wii = WiiPad.CLASSIC))   // the GameCube pad, no Wii pad on it
        assertEquals(PadProfile.STANDARD, PadProfile.forTag("WII"))   // not resolved yet: the plain pad
        assertEquals(WiiPad.NUNCHUK, p(WiiPad.NUNCHUK).wiiPad)
    }

    // The Nunchuk and Classic pads are on their stick; the Wiimote alone on its d-pad, like every other pad.
    @Test fun nunchukAndClassicStartOnTheStick() {
        assertTrue(p(WiiPad.NUNCHUK).stickByDefault && p(WiiPad.CLASSIC).stickByDefault)
        assertFalse(p(WiiPad.SIDEWAYS).stickByDefault || p(WiiPad.UPRIGHT).stickByDefault)
        assertFalse(PadProfile.STANDARD.stickByDefault || PadProfile.forTag("WII").stickByDefault || PadProfile.N3DS.stickByDefault)
    }

    @Test fun aChoiceMadeForTheGameWinsOverTheDefault() {
        val game = "wii-test/${this.hashCode()}"
        assertTrue(PadStickModes[game, p(WiiPad.NUNCHUK).stickByDefault])
        assertFalse(PadStickModes[game, p(WiiPad.SIDEWAYS).stickByDefault])
        PadStickModes[game] = false
        assertFalse(PadStickModes[game, p(WiiPad.CLASSIC).stickByDefault])
    }
}
