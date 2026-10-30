package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.settings.Prefs

/**
 * The game a hung core ended ([CoreWatchdog]): written (through the menu process's settings) just before the game
 * process goes, so the menu, back in front or started again, can say why the game closed. Read once.
 */
object CoreHangNotice {
    private const val PREFS = "core-hang"
    private const val KEY_TITLE = "title"

    /** Synchronous (commit): the process is killed right after. Any thread. */
    internal fun record(context: Context, title: String) {
        Prefs.open(context, PREFS).edit().putString(KEY_TITLE, title).commit()
    }

    /** The message for a game ended since the last call, else null. */
    fun take(context: Context): String? {
        val prefs = Prefs.open(context, PREFS)
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        prefs.edit().remove(KEY_TITLE).apply()
        return "$title stopped responding, so it was closed."
    }
}
