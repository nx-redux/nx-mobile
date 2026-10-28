package com.nxredux.mobile.tools

import android.content.Context
import com.nxredux.mobile.cheats.db.CheatDatabase
import com.nxredux.mobile.cheats.db.CheatDbController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** One controller per process, on a scope no activity cancels: a download survives rotation and a game launch. */
object CheatDbService {
    @Volatile private var instance: CheatDbController? = null

    fun controller(context: Context): CheatDbController = instance ?: synchronized(this) {
        instance ?: CheatDbController(
            AndroidCheatDb(context.applicationContext), CheatDatabase.installTags,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), System::currentTimeMillis,
        ).also { instance = it }
    }

    /** Re-reads the manifest when a controller exists (after [CheatsRoot] moved it into the home folder). */
    fun refreshIfStarted() { instance?.refresh() }

    /** Cheats & Patches is checking, downloading, installing or removing (the updater's install gate). Main thread only. */
    fun busy(): Boolean = instance?.busy == true
}
