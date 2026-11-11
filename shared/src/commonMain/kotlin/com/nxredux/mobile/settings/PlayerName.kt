package com.nxredux.mobile.settings

/**
 * The player's name the emulated consoles show (a 3DS profile name in local multiplayer): Settings → System → Player
 * name, or the device's own name until one is set.
 */
object PlayerName {
    /** The 3DS, DS and Wii profile names all hold ten characters. */
    const val MAX = 10
    const val FALLBACK = "Player"

    /** [text] as a console takes it: one line, no control characters, trimmed, at most [MAX] characters. */
    fun clean(text: String?): String =
        text.orEmpty().filterNot { it.isISOControl() }.trim().take(MAX).trim()

    /** The name in use: the [saved] one, else the [device]'s, else [FALLBACK]. */
    fun resolve(saved: String?, device: String?): String =
        clean(saved).ifEmpty { clean(device).ifEmpty { FALLBACK } }
}
