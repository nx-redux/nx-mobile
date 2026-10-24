package com.nxredux.mobile

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.os.Bundle
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.storage.DataRoot
import com.swordfish.libretrodroid.NxLog
import java.io.File

/**
 * Applies Settings → System → Debug log when the process starts, whichever activity starts it (a game
 * launched from another launcher starts at GameActivity), moves the saves, states, settings and cheats an earlier
 * version kept in private storage into the home folder when it can do so quietly, and clears an earlier run's in-app
 * update download.
 */
class NxApplication : Application() {
    companion object {
        // Main thread only: the lifecycle callbacks run there.
        private var startedActivities = 0

        /**
         * Some activity of the app is started (visible). Android lets a visible app open activities from the background
         * (the updater's install confirm screen, from its receiver); a hidden one is blocked silently. Main thread only.
         */
        val foreground: Boolean get() = startedActivities > 0
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++ }
            override fun onActivityStopped(activity: Activity) { startedActivities = (startedActivities - 1).coerceAtLeast(0) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        // Off the main thread, as both touch storage: drops an earlier run's update download (its .part, or an APK the
        // installer already took or the user cancelled), then starts moving older private data into the home folder.
        Thread({
            File(cacheDir, "update").deleteRecursively()
            DataRoot.startIfDue(this)
        }, "data-root").start()
        // A dead process's install wait notice: that wait ended with it.
        getSystemService(NotificationManager::class.java)?.cancel(NotificationIds.APP_UPDATE_WAIT)
        // Off is both flags' default: only On needs setting, so a launcher-only start never loads the native library.
        if (!AndroidAppSettings(this).debugLog) return
        try {
            NxLog.enabled = true
        } catch (e: UnsatisfiedLinkError) {
            // The Kotlin flag is already set (the setter sets it first); a game launch hits the same load failure.
            NxLog.e("NXAPP", "native log flag not set", e)
        }
    }
}
