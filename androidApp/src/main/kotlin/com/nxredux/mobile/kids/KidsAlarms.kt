package com.nxredux.mobile.kids

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.Prefs
import com.swordfish.libretrodroid.NxLog

/** The warning's broadcast to the game process ([com.nxredux.mobile.runner.GameActivity] shows it in its overlay). */
fun kidsWarnAction(context: Context): String = context.packageName + ".KIDS_WARN"

/**
 * Kids Mode's two boundary alarms: the warning at end − 10 min (once per end, [warnedFor]) and the lock at the end.
 * [plan] is the pure part; [set] puts a plan on AlarmManager, replacing the alarms set before (an empty plan cancels
 * both). Exact when Android allows it ([AlarmManager.canScheduleExactAlarms]); otherwise as close as Android will.
 */
object KidsAlarms {
    enum class Kind { WARN, LOCK }

    data class Alarm(val kind: Kind, val atMs: Long)

    /** Only Open has an end: a warning not given yet for it (now when under 10 minutes are left), then the lock. */
    fun plan(mode: KidsMode, nowMs: Long, warnedFor: Long): List<Alarm> {
        if (mode !is KidsMode.Open) return emptyList()
        val until = mode.untilMs
        return buildList {
            if (warnedFor != until) add(Alarm(Kind.WARN, maxOf(warnAtMs(until), nowMs)))
            add(Alarm(Kind.LOCK, maxOf(until, nowMs)))
        }
    }

    const val ACTION_WARN = "com.nxredux.mobile.kids.WARN"
    const val ACTION_LOCK = "com.nxredux.mobile.kids.LOCK"
    private const val KEY_WARNED_FOR = "kids_warned_for"

    /** The end whose warning was given (epoch ms; 0: none), kept across process deaths so it fires once per end. */
    fun warnedFor(context: Context): Long = prefs(context).getLong(KEY_WARNED_FOR, 0L)

    fun setWarnedFor(context: Context, untilMs: Long) { prefs(context).edit().putLong(KEY_WARNED_FOR, untilMs).apply() }

    private fun prefs(context: Context) = Prefs.open(context.applicationContext, AndroidAppSettings.PREFS_NAME)

    /** [mode]'s alarms from now, replacing the ones set before. */
    fun reschedule(context: Context, mode: KidsMode) = set(context, plan(mode, System.currentTimeMillis(), warnedFor(context)))

    fun set(context: Context, alarms: List<Alarm>) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        for (kind in Kind.entries) {
            val alarm = alarms.firstOrNull { it.kind == kind }
            val pi = pendingIntent(app, kind)
            if (alarm == null) { am.cancel(pi); continue }
            try {
                if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.atMs, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.atMs, pi)
            } catch (e: SecurityException) {   // the exact grant was taken back between the check and the set
                NxLog.w(TAG, "exact alarm refused: ${e.message}")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.atMs, pi)
            }
        }
    }

    /** Alarms & reminders: Android 12+ asks for it (denied by default on 14+); before 12 every app has it. */
    fun canExact(am: AlarmManager): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun pendingIntent(app: Context, kind: Kind): PendingIntent {
        val action = if (kind == Kind.WARN) ACTION_WARN else ACTION_LOCK
        val intent = Intent(app, KidsAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(app, kind.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private const val TAG = "NXKIDS"
}
