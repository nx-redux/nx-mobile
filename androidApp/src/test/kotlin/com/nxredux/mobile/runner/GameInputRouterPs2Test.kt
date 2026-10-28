package com.nxredux.mobile.runner

import android.view.KeyEvent
import com.nxredux.mobile.ui.nx.DpadHat
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.VirtualStickRouter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The PS2's stick clicks and its d-pad beside the left stick. [GameInputRouter] needs a GLRetroView and its host, so these
 * test the pure pieces it decides and sends with: [thumbKeyRoute] for a physical click, [PadKeymap] / [PadMap] for the
 * on-screen L3 / R3, and the [DpadHat] and [VirtualStickRouter] the on-screen d-pad and left stick go through.
 */
class GameInputRouterPs2Test {
    @Test fun thumbKeysGoToCoreOnPs2() {
        for (key in listOf(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR))
            assertEquals(ThumbKeyRoute.CORE, thumbKeyRoute(key, wii = false, n3ds = false))
        // The consoles that take them still do.
        assertEquals(ThumbKeyRoute.WII_RECENTER, thumbKeyRoute(KeyEvent.KEYCODE_BUTTON_THUMBL, wii = true, n3ds = false))
        assertEquals(ThumbKeyRoute.WII_HOME, thumbKeyRoute(KeyEvent.KEYCODE_BUTTON_THUMBR, wii = true, n3ds = false))
        assertEquals(ThumbKeyRoute.N3DS_SWAP, thumbKeyRoute(KeyEvent.KEYCODE_BUTTON_THUMBL, wii = false, n3ds = true))
        assertEquals(ThumbKeyRoute.CORE, thumbKeyRoute(KeyEvent.KEYCODE_BUTTON_THUMBR, wii = false, n3ds = true))
        // A physical click passes the PS2 pad map untouched, and the on-screen L3 / R3 send the same keys.
        val ps2 = PadMap.forTag("PS2")
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBL, ps2.sendKey(KeyEvent.KEYCODE_BUTTON_THUMBL))
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBR, ps2.sendKey(KeyEvent.KEYCODE_BUTTON_THUMBR))
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBL, PadKeymap.keyCodeFor(ps2.sends(NxButton.L3)))
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBR, PadKeymap.keyCodeFor(ps2.sends(NxButton.R3)))
    }

    @Test fun dpadThenStickNoStuckDirection() {
        // The core's d-pad hat and left analog as last sent, as GameInputRouter.onPad / stickRouter send them.
        var hat = 0 to 0
        var analog = 0f to 0f
        val dpad = DpadHat()
        val stick = VirtualStickRouter(onAnalog = { x, y -> analog = x to y }, onMenu = {})
        fun pad(e: PadEvent) {
            if (e is PadEvent.Down) dpad.press(e.button) else dpad.release(e.button)
            hat = dpad.x to dpad.y
        }
        pad(PadEvent.Down(NxButton.LEFT))
        stick.onStick(0.8f, -0.3f, menuOpen = false)
        pad(PadEvent.Down(NxButton.UP))
        assertEquals(-1 to -1, hat)                    // the stick never lands on the hat
        stick.onStick(-0.2f, 0.9f, menuOpen = false)
        pad(PadEvent.Up(NxButton.LEFT))
        assertEquals(0f to 0f == analog, false)        // the d-pad never moves the stick
        stick.onStick(0f, 0f, menuOpen = false)
        pad(PadEvent.Up(NxButton.UP))
        assertEquals(0 to 0, hat)
        assertEquals(0f to 0f, analog)
        // A release for a direction never pressed leaves nothing behind either.
        pad(PadEvent.Up(NxButton.RIGHT))
        assertEquals(0 to 0, hat)
    }
}
