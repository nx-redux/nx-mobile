package com.nxredux.mobile.shortcuts

/**
 * A button or a combo, as Android key codes. [modifier] is MENU ([KeyCodes.BUTTON_MODE]) held while [keyCode] is
 * pressed, or null for a single button. SELECT is no modifier: it stays the game's (and half of the SELECT+START menu
 * chord).
 */
data class ShortcutBinding(val modifier: Int?, val keyCode: Int) {
    /** `"110+103"` or `"103"`. */
    fun encode(): String = if (modifier == null) "$keyCode" else "$modifier+$keyCode"

    companion object {
        /** The keys a combo is held with: MENU only. */
        val MODIFIERS = setOf(KeyCodes.BUTTON_MODE)

        /**
         * Null for blank or garbage, a combo held with anything but [MODIFIERS] included (nothing could press it): a SELECT
         * combo saved before SELECT stopped being a modifier reads as absent, so the action inherits.
         */
        fun decode(s: String): ShortcutBinding? {
            val parts = s.trim().split('+')
            if (parts.size > 2) return null
            val codes = parts.map { it.trim().toIntOrNull() ?: return null }
            if (codes.size == 2 && codes[0] !in MODIFIERS) return null
            return if (codes.size == 2) ShortcutBinding(codes[0], codes[1]) else ShortcutBinding(null, codes[0])
        }
    }
}
