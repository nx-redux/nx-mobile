package com.nxredux.mobile.kids

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nxredux.mobile.LaunchActivity
import com.nxredux.mobile.NotificationIds
import com.nxredux.mobile.runner.LiveGameStore
import com.nxredux.mobile.runner.kidsLockIntent
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.swordfish.libretrodroid.NxLog
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** [KidsPlatform] on Android: the local clock, the flag file at the home root, the two grants, the alarms and the lock. */
class AndroidKidsPlatform(context: Context) : KidsPlatform {
    private val app = context.applicationContext

    override val flag: KidsFlag = AndroidKidsFlag(AndroidHomeLocator(app))

    override fun now(): LocalNow {
        val z = ZonedDateTime.now()
        return LocalNow(z.toInstant().toEpochMilli(), z.dayOfWeek.value, z.toLocalTime().toNanoOfDay() / 1_000_000L)
    }

    override fun hasUsageAccess(): Boolean {
        val ops = app.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        } else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        return mode == AppOpsManager.MODE_ALLOWED || (mode == AppOpsManager.MODE_DEFAULT &&
            app.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED)
    }

    override fun openUsageAccess() = openPage(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    override fun canExactAlarms(): Boolean = app.getSystemService(AlarmManager::class.java)?.let(KidsAlarms::canExact) ?: false

    override fun openExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return   // nothing to grant before Android 12
        openPage(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${app.packageName}")))
    }

    private fun openPage(page: Intent) {
        runCatching { app.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { NxLog.w(TAG, "settings page refused (${it::class.java.simpleName})") }
    }

    override fun reschedule(mode: KidsMode) = KidsAlarms.reschedule(app, mode, now().epochMs)

    /**
     * The game (if one is live) quits without its auto state, then the menu comes forward through [LaunchActivity],
     * which moves the menu's task as it is: MainActivity is singleTask, and starting it would clear the quitting game
     * above it mid-save. The menu already in front only switches to the tiles (AppState does that).
     */
    override fun lockNow() {
        NxLog.i(TAG, "lock")
        AndroidKids.cancelWarning(app)
        if (LiveGameStore.of(app).current() != null) app.sendBroadcast(kidsLockIntent(app).addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
        if (AndroidKids.menuResumed) return
        runCatching { app.startActivity(Intent(app, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { NxLog.w(TAG, "menu not brought forward (${it::class.java.simpleName})") }
    }

    private companion object { const val TAG = "NXKIDS" }
}

/**
 * The main process's one [KidsController] (Parent mode lives in it, so the menu and the alarms must share it), its
 * screen on/off hook and the 10-minute warning's delivery. Main thread only.
 */
object AndroidKids {
    private var controller: KidsController? = null
    private var platform: AndroidKidsPlatform? = null

    /** The menu's notice for the warning while a menu is resumed (set in its onResume, cleared in its onPause); else null. */
    var menuWarning: (() -> Unit)? = null

    val menuResumed: Boolean get() = menuWarning != null

    /** The menus between their onStart and onStop ([menuShown]): while one is shown the guard has nothing to watch. */
    private val shownMenus = HashSet<Any>()

    /**
     * A menu (MainActivity) became shown (onStart) or stopped being shown (onStop): the guard follows ([syncGuard]).
     * A start from onStop is allowed on Android 12+ (the app is just leaving a visible state).
     */
    fun menuShown(context: Context, menu: Any, shown: Boolean) {
        if (shown) shownMenus.add(menu) else shownMenus.remove(menu)
        syncGuard(context)
    }

    /** The guard on while [guardRuns] holds: Locked, Usage access, no menu shown. */
    private fun syncGuard(context: Context) {
        val c = controller ?: return
        val p = platform ?: return
        KidsGuardService.update(context, guardRuns(c.mode.value, p.hasUsageAccess(), shownMenus.isNotEmpty()))
    }

    fun get(context: Context): KidsController = controller ?: run {
        val app = context.applicationContext
        val p = AndroidKidsPlatform(app)
        val c = KidsController(AndroidAppSettings(app), p)
        platform = p
        controller = c
        // Screen off ends Parent mode (a timed unlock stays); screen on reads the clock again. Only dynamic receivers get these.
        val screen = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> c.endParent()
                    Intent.ACTION_SCREEN_ON -> c.refresh()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        ContextCompat.registerReceiver(app, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // The guard follows the mode wherever it changes (an exact lock alarm with the kid in another app, whose
        // receiver may start a foreground service), not only the menu's onStop; it stops when the mode leaves Locked.
        // Posted, not immediate: built in a menu's onCreate, the first sync runs after its onStart has counted it shown.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { c.mode.collect { syncGuard(app) } }
        // Born Locked with a game live: the change to Locked happened while no controller was here to see it.
        if (lockAtBirth(c.mode.value, LiveGameStore.of(app).current() != null)) p.lockNow()
        c
    }

    /**
     * The warning goes to one place ([warnRoute]). Without a resumed menu it is offered to the game screen as an ordered
     * broadcast: only a resumed one shows it and answers [KIDS_WARN_SHOWN]; no answer (no game, or one suspended in its
     * own task) posts the notification instead.
     */
    fun warn(context: Context) {
        val app = context.applicationContext
        if (warnRoute(menuResumed, gameInFront = false) == WarnRoute.MENU) { menuWarning?.invoke(); return }
        val answer = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (warnRoute(menuResumed = false, gameInFront = resultCode == KIDS_WARN_SHOWN) == WarnRoute.NOTIFICATION) postWarning(app)
            }
        }
        val offer = Intent(kidsWarnAction(app)).setPackage(app.packageName).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        app.sendOrderedBroadcast(offer, null, answer, null, 0, null, null)
    }

    private fun postWarning(app: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(nm)
        val notice = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Kids Mode")
            .setContentText(KIDS_WARNING)
            .setContentIntent(openMenu(app))
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NotificationIds.KIDS_WARN, notice)
        } catch (e: SecurityException) {   // the grant was revoked between the check and the post
            NxLog.w("NXKIDS", "warning notice refused")
        }
    }

    /** At the lock the warning is out of date. */
    fun cancelWarning(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NotificationIds.KIDS_WARN)
    }

    /** Kids Mode's notification channel: the warning and the guard's ongoing notice. */
    const val CHANNEL_ID = "kids"

    fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kids Mode", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Says when games are about to close"
            },
        )
    }

    /** A notification's tap: the menu forward through [LaunchActivity]. */
    fun openMenu(context: Context): PendingIntent {
        val app = context.applicationContext
        return PendingIntent.getActivity(
            app, 0, Intent(app, LaunchActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
