package com.nxredux.mobile.shortcuts

/**
 * A button or a combo, as Android key codes. [modifier] is MENU ([KeyCodes.BUTTON_MODE]) held while [keyCode] is
 * pressed, or null for a single button. SELECT and START are never a shortcut's key, alone or with MENU ([GAME_KEYS]).
 */
data class ShortcutBinding(val modifier: Int?, val keyCode: Int) {
    /** `"110+103"` or `"103"`. */
    fun encode(): String = if (modifier == null) "$keyCode" else "$modifier+$keyCode"

    companion object {
        /** The keys a combo is held with: MENU only. */
        val MODIFIERS = setOf(KeyCodes.BUTTON_MODE)

        /** SELECT and START stay the game's (its pause, the SELECT+START menu chord): never bound, in any form. */
        val GAME_KEYS = setOf(KeyCodes.BUTTON_SELECT, KeyCodes.BUTTON_START)

        /** What press-to-bind says for a [GAME_KEYS] key. */
        const val GAME_KEYS_NOTE = "SELECT and START stay the game's"

        /**
         * Null for blank or garbage, a combo held with anything but [MODIFIERS] included (nothing could press it), or a
         * [GAME_KEYS] key: a SELECT combo or a SELECT / START binding saved before these rules reads as absent, so the
         * action inherits.
         */
        fun decode(s: String): ShortcutBinding? {
            val parts = s.trim().split('+')
            if (parts.size > 2) return null
            val codes = parts.map { it.trim().toIntOrNull() ?: return null }
            if (codes.size == 2 && codes[0] !in MODIFIERS) return null
            if (codes.last() in GAME_KEYS) return null
            return if (codes.size == 2) ShortcutBinding(codes[0], codes[1]) else ShortcutBinding(null, codes[0])
        }
    }
}
