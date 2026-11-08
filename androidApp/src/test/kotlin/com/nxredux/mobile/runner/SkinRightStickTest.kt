package com.nxredux.mobile.runner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a skin's right stick goes: the game's right analog, or the N64's C buttons. */
class SkinRightStickTest {
    @Test fun gameCubeAndPlayStationSticksAreTheRightAnalog() {
        assertTrue(skinRightStickIsAnalog(gcPad = true, isWii = false, wiiPad = null, psSkin = false))
        assertTrue(skinRightStickIsAnalog(gcPad = false, isWii = false, wiiPad = null, psSkin = true))
        assertFalse(skinRightStickIsAnalog(gcPad = false, isWii = false, wiiPad = null, psSkin = false))   // the N64's C buttons
    }

    @Test fun aWiiClassicOrUnknownPadsStickIsTheRightAnalog() {
        assertTrue(skinRightStickIsAnalog(gcPad = false, isWii = true, wiiPad = WiiPad.CLASSIC, psSkin = false))
        // not yet known: the skin shows the Classic Controller, so its right stick is that one's
        assertTrue(skinRightStickIsAnalog(gcPad = false, isWii = true, wiiPad = null, psSkin = false))
        for (pad in listOf(WiiPad.NUNCHUK, WiiPad.SIDEWAYS, WiiPad.UPRIGHT)) {
            assertFalse(pad.name, skinRightStickIsAnalog(gcPad = false, isWii = true, wiiPad = pad, psSkin = false))
        }
    }
}
