package com.nxredux.mobile.settings

import android.content.Context
import android.content.SharedPreferences
import com.nxredux.mobile.AppProcess
import java.util.concurrent.ConcurrentHashMap

/**
 * Where every [SharedPreferences] of the app is opened: the real file in the main process; in `:game`, the file read
 * locally with writes sent to the main process and reads refreshed from it on [refreshGame] ([ProviderPreferences]).
 */
object Prefs {
    /** One per file for the whole game process: settings are opened per key event (an `AndroidAppSettings` each time). */
    private val game = OnePerName { name ->
        val app = appContext!!
        ProviderPreferences(name, PrefsProvider.channel(app), app.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
    @Volatile private var appContext: Context? = null

    /**
     * Game process: every file opened so far reads the main process's current entries ([ProviderPreferences.refreshAll]),
     * so a setting changed in the menu meanwhile applies. One blocking call, which may have to start the menu's process:
     * off the main thread. Nothing in the main process.
     */
    fun refreshGame() {
        if (!AppProcess.isGame()) return
        val app = appContext ?: return
        ProviderPreferences.refreshAll(PrefsProvider.channel(app), game.all().toList())
    }

    fun open(context: Context, name: String): SharedPreferences {
        if (!AppProcess.isGame()) return context.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (appContext == null) appContext = context.applicationContext
        return game[name]
    }
}

/** One [T] per name, made on first use and kept for the life of the process. */
class OnePerName<T : Any>(private val create: (String) -> T) {
    private val made = ConcurrentHashMap<String, T>()
    operator fun get(name: String): T = made.computeIfAbsent(name, create)
    /** Every [T] made so far. */
    fun all(): Collection<T> = made.values
}
