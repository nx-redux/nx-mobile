package com.nxredux.mobile.launcher

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import com.swordfish.libretrodroid.NxLog

/** This activity's own task among the app's tasks; null when Android does not list it. */
internal fun Activity.ownAppTask(): ActivityManager.AppTask? {
    val tasks = getSystemService(ActivityManager::class.java)?.appTasks ?: return null
    return tasks.firstOrNull { t ->
        runCatching { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.taskInfo?.taskId else @Suppress("DEPRECATION") t.taskInfo?.persistentId }
            .getOrNull() == taskId
    }
}

/**
 * Launcher mode: keeps this activity's task out of Recents. Recents (Launcher3's) lists a third-party home's task as an
 * ordinary card, and swiping it away leaves another launcher's home in front, out of reach of the home gesture.
 */
internal fun Activity.hideTaskFromRecents() {
    runCatching { ownAppTask()?.setExcludeFromRecents(true) }.onFailure { NxLog.w("NXAPP", "hide task from Recents: ${it.message}") }
}

/**
 * Launcher mode, a game quitting on screen: NX Redux's home task to the front first. Opening Recents (Launcher3 draws it
 * in its own home activity) leaves Launcher3's home above it, and that is where a finished game would land otherwise.
 */
internal fun Activity.bringHomeTaskToFront() {
    runCatching {
        getSystemService(ActivityManager::class.java)?.appTasks
            ?.firstOrNull { t -> runCatching { t.taskInfo?.baseIntent?.hasCategory(Intent.CATEGORY_HOME) }.getOrNull() == true }
            ?.moveToFront()
    }.onFailure { NxLog.w("NXAPP", "home task to front: ${it.message}") }
}
