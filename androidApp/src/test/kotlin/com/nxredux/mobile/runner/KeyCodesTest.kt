package com.nxredux.mobile.runner

import android.view.KeyEvent
import com.nxredux.mobile.shortcuts.ConsoleHotkeys
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.ui.nx.NxButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `shared`'s [KeyCodes] restate Android's key codes, and [ConsoleHotkeys] the Wii's [WiiShortcuts]: a drift fails here. */
class KeyCodesTest {
    @Test fun key_codes_match_android() {
        val pinned = listOf(
            KeyCodes.BACK to KeyEvent.KEYCODE_BACK,
            KeyCodes.DPAD_UP to KeyEvent.KEYCODE_DPAD_UP,
            KeyCodes.DPAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            KeyCodes.DPAD_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            KeyCodes.DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyCodes.BUTTON_A to KeyEvent.KEYCODE_BUTTON_A,
            KeyCodes.BUTTON_B to KeyEvent.KEYCODE_BUTTON_B,
            KeyCodes.BUTTON_C to KeyEvent.KEYCODE_BUTTON_C,
            KeyCodes.BUTTON_X to KeyEvent.KEYCODE_BUTTON_X,
            KeyCodes.BUTTON_Y to KeyEvent.KEYCODE_BUTTON_Y,
            KeyCodes.BUTTON_Z to KeyEvent.KEYCODE_BUTTON_Z,
            KeyCodes.BUTTON_L1 to KeyEvent.KEYCODE_BUTTON_L1,
            KeyCodes.BUTTON_R1 to KeyEvent.KEYCODE_BUTTON_R1,
            KeyCodes.BUTTON_L2 to KeyEvent.KEYCODE_BUTTON_L2,
            KeyCodes.BUTTON_R2 to KeyEvent.KEYCODE_BUTTON_R2,
            KeyCodes.BUTTON_THUMBL to KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyCodes.BUTTON_THUMBR to KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyCodes.BUTTON_START to KeyEvent.KEYCODE_BUTTON_START,
            KeyCodes.BUTTON_SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyCodes.BUTTON_MODE to KeyEvent.KEYCODE_BUTTON_MODE,
            KeyCodes.BUTTON_1 to KeyEvent.KEYCODE_BUTTON_1,
            KeyCodes.BUTTON_16 to KeyEvent.KEYCODE_BUTTON_16,
        )
        pinned.forEach { (ours, android) -> assertEquals(android, ours) }
        assertEquals(4, KeyCodes.BACK)
    }

    @Test fun wii_hotkeys_match_wii_shortcuts() {
        val singles = ConsoleHotkeys.claimed("WII").filter { it.binding.modifier == null }.map { it.binding.keyCode }
        val recenter = singles.filter { WiiShortcuts.isRecenter(wii = true, it) }
        val home = singles.filter { WiiShortcuts.isHome(wii = true, it) }
        assertEquals(listOf(KeyCodes.BUTTON_THUMBL), recenter)
        assertEquals(listOf(KeyCodes.BUTTON_THUMBR), home)
        // Every Wii-only claim is one of the two: nothing claimed that WiiShortcuts doesn't take.
        val wiiOnly = singles - ConsoleHotkeys.always.map { it.binding.keyCode }.toSet()
        assertTrue(wiiOnly.all { WiiShortcuts.isRecenter(true, it) || WiiShortcuts.isHome(true, it) })
    }

    @Test fun l3r3MapToThumbKeys() {
        assertEquals(KeyCodes.BUTTON_THUMBL, PadKeymap.keyCodeFor(NxButton.L3))
        assertEquals(KeyCodes.BUTTON_THUMBR, PadKeymap.keyCodeFor(NxButton.R3))
        // A physical stick click stays itself: no NxButton is read back from it.
        assertEquals(null, PadKeymap.buttonFor(KeyCodes.BUTTON_THUMBL))
        assertEquals(null, PadKeymap.buttonFor(KeyCodes.BUTTON_THUMBR))
    }
}
