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

/** On a home intent ([bringHomeTaskToFront]'s): the menu stays where it was, not back to its first tab as Home does. */
internal const val EXTRA_KEEP_PLACE = "com.nxredux.mobile.keep_place"

/**
 * Launcher mode, a game quitting on screen: NX Redux's home task to the front first. Opening Recents (Launcher3 draws it
 * in its own home activity) leaves Launcher3's home above it, and that is where a finished game would land otherwise.
 * Through a home intent with NO_ANIMATION, the one switch Android honours on a task change (AppTask.moveToFront slides);
 * [EXTRA_KEEP_PLACE] keeps the menu's place. The task moved to the front when the intent does not resolve.
 */
internal fun Activity.bringHomeTaskToFront() {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        .putExtra(EXTRA_KEEP_PLACE, true)
    if (runCatching { startActivity(home) }.isSuccess) return
    runCatching {
        getSystemService(ActivityManager::class.java)?.appTasks
            ?.firstOrNull { t -> runCatching { t.taskInfo?.baseIntent?.hasCategory(Intent.CATEGORY_HOME) }.getOrNull() == true }
            ?.moveToFront()
    }.onFailure { NxLog.w("NXAPP", "home task to front: ${it.message}") }
}
