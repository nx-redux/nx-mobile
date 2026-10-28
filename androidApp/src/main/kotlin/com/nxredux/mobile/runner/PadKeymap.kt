package com.nxredux.mobile.runner

import android.view.KeyEvent
import com.nxredux.mobile.ui.nx.NxButton

/**
 * Single source of truth mapping between [NxButton] and Android key codes. Directions are intentionally
 * absent from [keyCodeFor] — the on-screen d-pad is routed as a MOTION_SOURCE_DPAD hat, not key codes —
 * but hardware DPAD keys are still recognised by [buttonFor].
 */
object PadKeymap {
    private val pairs = listOf(
        NxButton.A to KeyEvent.KEYCODE_BUTTON_A,
        NxButton.B to KeyEvent.KEYCODE_BUTTON_B,
        NxButton.X to KeyEvent.KEYCODE_BUTTON_X,
        NxButton.Y to KeyEvent.KEYCODE_BUTTON_Y,
        NxButton.L1 to KeyEvent.KEYCODE_BUTTON_L1,
        NxButton.R1 to KeyEvent.KEYCODE_BUTTON_R1,
        NxButton.L2 to KeyEvent.KEYCODE_BUTTON_L2,
        NxButton.R2 to KeyEvent.KEYCODE_BUTTON_R2,
        NxButton.START to KeyEvent.KEYCODE_BUTTON_START,
        NxButton.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
        NxButton.MENU to KeyEvent.KEYCODE_BUTTON_MODE,
    )
    private val byButton = pairs.toMap()
    private val byKey = pairs.associate { (b, k) -> k to b }

    /**
     * Key code for a non-direction button, or null for a direction (routed as a hat instead) or a C button (sent through
     * [N64Input] on N64). The Wii pads' HOME goes out as R3 (Dolphin's Home) and the PS2 pad's L3 / R3 as themselves,
     * all left out of [buttonFor], so a physical stick click still reaches every core as itself.
     */
    fun keyCodeFor(button: NxButton): Int? = byButton[button] ?: when (button) {
        NxButton.HOME, NxButton.R3 -> KeyEvent.KEYCODE_BUTTON_THUMBR
        NxButton.L3 -> KeyEvent.KEYCODE_BUTTON_THUMBL
        else -> null
    }

    /** NxButton for a hardware key code (buttons + DPAD), or null if unmapped. */
    fun buttonFor(keyCode: Int): NxButton? = byKey[keyCode] ?: when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> NxButton.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> NxButton.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> NxButton.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> NxButton.RIGHT
        else -> null
    }
}
