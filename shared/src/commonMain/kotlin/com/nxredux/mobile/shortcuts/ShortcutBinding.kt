package com.nxredux.mobile.shortcuts

/**
 * A button or a combo, as Android key codes. [modifier] is MENU ([KeyCodes.BUTTON_MODE]) or SELECT
 * ([KeyCodes.BUTTON_SELECT]) held while [keyCode] is pressed, or null for a single button.
 */
data class ShortcutBinding(val modifier: Int?, val keyCode: Int) {
    /** `"110+103"` or `"103"`. */
    fun encode(): String = if (modifier == null) "$keyCode" else "$modifier+$keyCode"

    companion object {
        /** The keys a combo is held with. */
        val MODIFIERS = setOf(KeyCodes.BUTTON_MODE, KeyCodes.BUTTON_SELECT)

        /** Null for blank or garbage, a combo held with anything but [MODIFIERS] included (nothing could press it). */
        fun decode(s: String): ShortcutBinding? {
            val parts = s.trim().split('+')
            if (parts.size > 2) return null
            val codes = parts.map { it.trim().toIntOrNull() ?: return null }
            if (codes.size == 2 && codes[0] !in MODIFIERS) return null
            return if (codes.size == 2) ShortcutBinding(codes[0], codes[1]) else ShortcutBinding(null, codes[0])
        }
    }
}
