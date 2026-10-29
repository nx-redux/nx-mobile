package com.nxredux.mobile.settings

import android.content.Context
import android.content.SharedPreferences
import com.nxredux.mobile.AppProcess

/** Where every [SharedPreferences] of the app is opened: the real file in the main process, the provider in `:game`. */
object Prefs {
    fun open(context: Context, name: String): SharedPreferences =
        if (!AppProcess.isGame(context)) context.getSharedPreferences(name, Context.MODE_PRIVATE)
        else ProviderPreferences(name, PrefsProvider.channel(context))
}
