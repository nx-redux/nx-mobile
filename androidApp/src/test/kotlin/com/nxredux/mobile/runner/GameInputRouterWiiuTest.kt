package com.nxredux.mobile.runner

import android.view.KeyEvent
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.WiiOptions
import com.nxredux.mobile.ui.nx.NxButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Wii U's stick clicks and minus beside its SELECT chords. [GameInputRouter] needs a GLRetroView and its host, so these
 * test the pure pieces it routes with: [thumbKeyRoute], [PadKeymap] and the WIIU [PadMap].
 */
class GameInputRouterWiiuTest {
    @Test fun stickClicksAreTheGamePadsAndNeverTheChords() {
        assertFalse(WiiOptions.appliesTo("WIIU") || N3dsOptions.appliesTo("WIIU"))
        for (key in listOf(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR)) {
            assertEquals(ThumbKeyRoute.CORE, thumbKeyRoute(key, wii = WiiOptions.appliesTo("WIIU"), n3ds = N3dsOptions.appliesTo("WIIU")))
            assertNull(PadKeymap.buttonFor(key))   // not a placed button: the hotkeys never see a physical click
            assertEquals(key, PadMap.forTag("WIIU").sendKey(key))
        }
    }

    @Test fun aSelectTapReachesTheGameAsMinus() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_SELECT, PadKeymap.keyCodeFor(PadMap.forTag("WIIU").sends(NxButton.SELECT)))
    }

    // Cemu reads the GamePad's sticks as analog: a tilt must not also press the d-pad (Breath of the Wild's item menus)
    @Test fun cemuTakesTheStickAsAnalogOnly() {
        assertEquals(
            setOf("mupen64plus_next", "ppsspp", "pcsx_rearmed", "swanstation", "flycast", "dolphin", "armsx2", "cemu"),
            GameInputRouter.STICK_CORES,
        )
    }
}
