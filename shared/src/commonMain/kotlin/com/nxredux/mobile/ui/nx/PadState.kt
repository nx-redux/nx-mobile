package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.mutableStateMapOf

/**
 * The Wii pad's MOTION button: [on] says whether the phone's motion is driving the Wiimote; a tap flips it
 * ([onTap]), a long press recentres it ([onLongPress]).
 */
data class PadMotion(val on: Boolean, val onTap: () -> Unit, val onLongPress: () -> Unit)

/** The DS / 3DS pad's mic button: [on] says whether the game hears the phone's mic (the session's Mic toggle), a tap flips it ([onTap]). */
data class PadMic(val on: Boolean, val onTap: () -> Unit)

/**
 * The d-pad / stick choice per game for the process's life. The game rebuilds its pad per layout (rotate, fold: a new
 * view each time, so nothing remembered inside the pad survives) and reads `PadStickModes[game]`, writing the player's
 * choice back, so the choice outlives the rebuild. Snapshot state: a reader recomposes when it changes.
 */
object PadStickModes {
    private val byGame = mutableStateMapOf<String, Boolean>()

    /** The stick (true) or the d-pad (false, the default) for [game]. */
    operator fun get(game: String): Boolean = byGame[game] ?: false

    /** The player's choice for [game], or [default] until they make one (the pad's [PadProfile.stickByDefault]). */
    operator fun get(game: String, default: Boolean): Boolean = byGame[game] ?: default

    operator fun set(game: String, useJoystick: Boolean) {
        byGame[game] = useJoystick
    }
}
