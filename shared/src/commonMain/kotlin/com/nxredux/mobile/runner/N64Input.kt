package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton

/**
 * The Nintendo 64 by name, as NX Redux binds it (N64.pak `default.cfg`) and Nintendo Classics does: the button printed A
 * is N64 A on every pad. mupen64plus_next runs with `mupen64plus-alt-map` (Independent C-button Controls, locked on in
 * [CoreRegistry]), which gives each N64 input its own RetroPad id: JOYPAD_B = A, JOYPAD_Y = B, JOYPAD_X = C-Up,
 * JOYPAD_A = C-Down, JOYPAD_L = C-Left, JOYPAD_R = C-Right, JOYPAD_L2 = Z, JOYPAD_R2 = R, JOYPAD_SELECT = L. [toRunner]
 * sends each pressed button as the key the core reads as its N64 meaning; the right stick still gives the C buttons.
 */
object N64Input {
    // Android's KeyEvent.KEYCODE_BUTTON_* (no Android imports in shared).
    private const val BUTTON_A = 96
    private const val BUTTON_B = 97
    private const val BUTTON_X = 99
    private const val BUTTON_Y = 100
    private const val BUTTON_L1 = 102
    private const val BUTTON_R1 = 103
    private const val BUTTON_L2 = 104
    private const val BUTTON_R2 = 105
    private const val BUTTON_START = 108
    private const val BUTTON_SELECT = 109

    fun appliesTo(tag: String): Boolean = tag.trim().uppercase() == "N64"

    /** The keycode to send for [button], null to send nothing (R2, SELECT), or [original] for what it doesn't translate. */
    fun toRunner(button: NxButton, original: Int): Int? = when (button) {
        NxButton.A -> BUTTON_B
        NxButton.B -> BUTTON_Y
        NxButton.C_UP -> BUTTON_X
        NxButton.C_DOWN, NxButton.Y -> BUTTON_A
        NxButton.C_LEFT, NxButton.X -> BUTTON_L1
        NxButton.C_RIGHT -> BUTTON_R1
        NxButton.L2 -> BUTTON_L2
        NxButton.L1 -> BUTTON_SELECT
        NxButton.R1 -> BUTTON_R2
        NxButton.START -> BUTTON_START
        NxButton.R2, NxButton.SELECT, NxButton.HOME, NxButton.L3, NxButton.R3 -> null
        NxButton.UP, NxButton.DOWN, NxButton.LEFT, NxButton.RIGHT, NxButton.MENU -> original
    }
}
