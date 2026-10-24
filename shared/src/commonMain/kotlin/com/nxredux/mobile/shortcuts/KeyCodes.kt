package com.nxredux.mobile.shortcuts

/**
 * Android key codes the shortcuts need, as plain ints because `shared` is platform-free. They mirror
 * `android.view.KeyEvent.KEYCODE_*`; androidApp's `KeyCodesTest` pins them to the real constants.
 */
object KeyCodes {
    /** A controller's or the system's Back: press-to-bind cancels on it, as on B. */
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_C = 98
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_Z = 101
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_L2 = 104
    const val BUTTON_R2 = 105
    const val BUTTON_THUMBL = 106
    const val BUTTON_THUMBR = 107
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109
    const val BUTTON_MODE = 110

    /** `KEYCODE_BUTTON_1` .. `KEYCODE_BUTTON_16` are consecutive. */
    const val BUTTON_1 = 188
    const val BUTTON_16 = 203
}
