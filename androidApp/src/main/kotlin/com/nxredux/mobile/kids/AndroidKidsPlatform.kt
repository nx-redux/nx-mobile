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
import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    override fun now(): LocalNow = localNow()

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

    /** The game (if one is live) quits without its auto state, then the menu comes forward ([AndroidKids.quitGameThenShowMenu]). */
    override fun lockNow() {
        NxLog.i(TAG, "lock")
        AndroidKids.locks++
        AndroidKids.cancelWarning(app)
        AndroidKids.quitGameThenShowMenu(app)
    }

    /** [KidsClockReceiver] on only while Kids Mode is on (the manifest has it off); the setting outlives app updates. */
    override fun setStartReceiver(enabled: Boolean) = AndroidKids.setStartReceiver(app, enabled)

    companion object {
        private const val TAG = "NXKIDS"

        /** The local clock now, as [KidsPlatform.now] reads it (the game process's start check reads it too). */
        fun localNow(): LocalNow {
            val z = ZonedDateTime.now()
            return LocalNow(z.toInstant().toEpochMilli(), z.dayOfWeek.value, z.toLocalTime().toNanoOfDay() / 1_000_000L)
        }
    }
}

/**
 * The main process's one [KidsController] (Parent mode lives in it, so the menu and the alarms must share it), its
 * screen on/off hook and the 10-minute warning's delivery. Main thread only.
 */
object AndroidKids {
    @Volatile private var controller: KidsController? = null

    /** [KidsPlatform.lockNow] calls so far: an alarm sees whether its refresh locked already ([lockOnAlarm]). */
    var locks = 0

    /**
     * The main process holds Parent mode (in memory only): the game process asks through [com.nxredux.mobile.settings.PrefsProvider]
     * before it refuses to start a game while the stored state reads Locked. Any thread; false before any controller.
     */
    fun parentNow(): Boolean = controller?.mode?.value == KidsMode.Parent

    /** Lock now (the lock alarm's own lock, [lockOnAlarm]); repeating it is safe (the game quits once, the menu moves once more). */
    fun lockNow(context: Context) {
        get(context)
        platform?.lockNow()
    }
    private var platform: AndroidKidsPlatform? = null

    /** The menu's notice for the warning while a menu is resumed (set in its onResume, cleared in its onPause); else null. */
    var menuWarning: (() -> Unit)? = null

    val menuResumed: Boolean get() = menuWarning != null

    /** A menu is between its onStart and onStop. */
    val menuIsShown: Boolean get() = shownMenus.isNotEmpty()

    /**
     * The game screen quits for the lock before the menu comes forward. The lock goes as an ordered broadcast, and the menu
     * moves in its final answer, after the game process's receiver has run. That receiver marks the game quitting
     * synchronously, so its onStop under the menu writes no auto state. The menu comes through [LaunchActivity], which
     * moves the menu's task as it is: MainActivity is singleTask, and starting it would clear the quitting game above it
     * mid-save. With no answer within [LOCK_ANSWER_MS] (a stuck game process) the menu moves anyway. A menu already
     * resumed only switches to the tiles (AppState does that).
     */
    fun quitGameThenShowMenu(context: Context) {
        val app = context.applicationContext
        if (LiveGameStore.of(app).current() == null) { showMenu(app); return }
        val main = Handler(Looper.getMainLooper())
        var done = false
        val once = Runnable { if (!done) { done = true; showMenu(app) } }
        val answer = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { main.removeCallbacks(once); once.run() }
        }
        app.sendOrderedBroadcast(kidsLockIntent(app).addFlags(Intent.FLAG_RECEIVER_FOREGROUND), null, answer, main, 0, null, null)
        main.postDelayed(once, LOCK_ANSWER_MS)
    }

    private const val LOCK_ANSWER_MS = 3_000L

    private fun showMenu(app: Context) {
        if (menuResumed) return
        runCatching { app.startActivity(Intent(app, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { NxLog.w("NXKIDS", "menu not brought forward (${it::class.java.simpleName})") }
    }

    /**
     * Parent mode away from the menu (a game, another app, the menu paused): an alarm at its idle end
     * ([KidsController.parentIdleAtMs]), set again on a game's input. Cancelled while the menu is resumed (its own tick
     * counts) and outside Parent mode.
     */
    fun armParentIdle(context: Context) {
        val c = controller ?: return
        KidsAlarms.setParentIdle(context, if (menuResumed) null else c.parentIdleAtMs)
    }

    /** [KidsClockReceiver] on or off; only when that changes (the call is cheap but writes package state). */
    fun setStartReceiver(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        val pm = app.packageManager
        val name = ComponentName(app, KidsClockReceiver::class.java)
        val want = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            if (pm.getComponentEnabledSetting(name) != want) pm.setComponentEnabledSetting(name, want, PackageManager.DONT_KILL_APP)
        }.onFailure { NxLog.w("NXKIDS", "start receiver not set (${it::class.java.simpleName})") }
    }

    /** The screen came on: a warning held while it was off ([KidsAlarmReceiver]), or one due meanwhile, is given now. */
    private fun warnOnScreenOn(app: Context, c: KidsController) {
        val mode = c.mode.value
        if (!warnDue(mode, c.now().epochMs, KidsAlarms.warnedFor(app))) return
        KidsAlarms.setWarnedFor(app, (mode as KidsMode.Open).untilMs)
        warn(app)
        KidsAlarms.reschedule(app, mode, c.now().epochMs)
    }

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
        val settings = AndroidAppSettings(app)
        val c = KidsController(settings, p)
        platform = p
        controller = c
        // The boot / clock receiver as the setting says (the manifest has it off; on and off follow turnOn / turnOff).
        setStartReceiver(app, settings.kidsEnabled)
        // Screen off ends Parent mode (a timed unlock stays); screen on reads the clock again. Only dynamic receivers get these.
        val screen = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> c.endParent()
                    Intent.ACTION_SCREEN_ON -> { c.refresh(); warnOnScreenOn(app, c) }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        ContextCompat.registerReceiver(app, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // The guard follows the mode wherever it changes (an exact lock alarm with the kid in another app, whose
        // receiver may start a foreground service), not only the menu's onStop; it stops when the mode leaves Locked.
        // Posted, not immediate: built in a menu's onCreate, the first sync runs after its onStart has counted it shown.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            c.mode.collect { m ->
                syncGuard(app)
                if (m != KidsMode.Parent) KidsAlarms.setParentIdle(app, null)   // Parent ended: no idle alarm left
            }
        }
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
