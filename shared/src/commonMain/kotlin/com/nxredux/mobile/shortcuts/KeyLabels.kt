package com.nxredux.mobile.shortcuts

/** Button names for a binding: `MENU+R1`, `SELECT+X`, `Button 1`, `Key 312` for a code nothing names. */
object KeyLabels {
    fun label(b: ShortcutBinding): String {
        val key = name(b.keyCode)
        return if (b.modifier == null) key else "${name(b.modifier)}+$key"
    }

    private fun name(code: Int): String = when (code) {
        KeyCodes.BUTTON_A -> "A"
        KeyCodes.BUTTON_B -> "B"
        KeyCodes.BUTTON_C -> "C"
        KeyCodes.BUTTON_X -> "X"
        KeyCodes.BUTTON_Y -> "Y"
        KeyCodes.BUTTON_Z -> "Z"
        KeyCodes.BUTTON_L1 -> "L1"
        KeyCodes.BUTTON_R1 -> "R1"
        KeyCodes.BUTTON_L2 -> "L2"
        KeyCodes.BUTTON_R2 -> "R2"
        KeyCodes.BUTTON_THUMBL -> "L3"
        KeyCodes.BUTTON_THUMBR -> "R3"
        KeyCodes.BUTTON_START -> "START"
        KeyCodes.BUTTON_SELECT -> "SELECT"
        KeyCodes.BUTTON_MODE -> "MENU"
        KeyCodes.DPAD_UP -> "Up"
        KeyCodes.DPAD_DOWN -> "Down"
        KeyCodes.DPAD_LEFT -> "Left"
        KeyCodes.DPAD_RIGHT -> "Right"
        in KeyCodes.BUTTON_1..KeyCodes.BUTTON_16 -> "Button ${code - KeyCodes.BUTTON_1 + 1}"
        else -> "Key $code"
    }
}
