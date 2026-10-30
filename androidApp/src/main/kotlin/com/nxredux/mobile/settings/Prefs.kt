package com.nxredux.mobile.settings

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import com.nxredux.mobile.AppProcess
import java.util.concurrent.ConcurrentHashMap

/** Where every [SharedPreferences] of the app is opened: the real file in the main process, the provider in `:game`. */
object Prefs {
    /**
     * One per file for the whole game process: settings are opened per key event (an `AndroidAppSettings` each time), so
     * opening must cost nothing, and one snapshot per file is kept fresh by the owner's change notices.
     */
    private val remote = OnePerName { name -> ProviderPreferences(name, PrefsProvider.channel(appContext!!)).also(::observe) }
    @Volatile private var appContext: Context? = null

    fun open(context: Context, name: String): SharedPreferences {
        if (!AppProcess.isGame(context)) {
            return context.getSharedPreferences(name, Context.MODE_PRIVATE).also { PrefsProvider.watch(context, name, it) }
        }
        if (appContext == null) appContext = context.applicationContext
        return remote[name]
    }

    /** The menu's writes to [prefs]'s file reach it as a change notice ([PrefsProvider.watch]), on a binder thread. */
    private fun observe(prefs: ProviderPreferences) {
        val context = appContext!!
        context.contentResolver.registerContentObserver(PrefsProvider.changeUri(context, prefs.name), false, object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) = prefs.changed()
        })
    }
}

/** One [T] per name, made on first use and kept for the life of the process. */
class OnePerName<T : Any>(private val create: (String) -> T) {
    private val made = ConcurrentHashMap<String, T>()
    operator fun get(name: String): T = made.computeIfAbsent(name, create)
}
