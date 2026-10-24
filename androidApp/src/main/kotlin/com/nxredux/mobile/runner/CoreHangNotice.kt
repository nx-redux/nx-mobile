package com.nxredux.mobile.runner

import android.content.Context

/**
 * The game a hung core ended ([CoreWatchdog]): written just before the process goes, so the menu, started again in a
 * new process, can say why the game closed. Read once.
 */
object CoreHangNotice {
    private const val PREFS = "core-hang"
    private const val KEY_TITLE = "title"

    /** Synchronous (commit): the process is killed right after. Any thread. */
    internal fun record(context: Context, title: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TITLE, title).commit()
    }

    /** The message for a game ended since the last call, else null. */
    fun take(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        prefs.edit().remove(KEY_TITLE).apply()
        return "$title stopped responding, so it was closed."
    }
}
