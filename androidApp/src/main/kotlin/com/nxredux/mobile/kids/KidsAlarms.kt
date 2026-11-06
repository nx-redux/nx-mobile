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

/** Where the 10-minute warning goes: exactly one place. */
enum class WarnRoute { MENU, GAME, NOTIFICATION }

/**
 * A resumed menu shows its notice; else a game screen in front (resumed) its overlay toast; else (the kid in another
 * app, or a game suspended in its own task) a notification.
 */
fun warnRoute(menuResumed: Boolean, gameInFront: Boolean): WarnRoute = when {
    menuResumed -> WarnRoute.MENU
    gameInFront -> WarnRoute.GAME
    else -> WarnRoute.NOTIFICATION
}

/**
 * A controller made while a game is live and already Locked (a clock change past the end, a cold start, an alarm, with
 * the main process gone meanwhile) never sees a change to Locked, so it locks once itself.
 */
fun lockAtBirth(mode: KidsMode, gameLive: Boolean): Boolean = mode is KidsMode.Locked && gameLive

/**
 * A lock alarm locks itself ([KidsPlatform.lockNow]) when the mode is Locked, no menu is shown (the menu shows the tiles
 * on its own) and the refresh's change to Locked did not lock already: a cold process with an Android game or another
 * app in front, which [lockAtBirth] (a live NX Redux game only) does not cover.
 */
fun lockOnAlarm(mode: KidsMode, menuShown: Boolean, lockedByRefresh: Boolean): Boolean =
    mode is KidsMode.Locked && !menuShown && !lockedByRefresh

/** An exact alarm comes on time, an inexact one late; this much early still counts as the warning's time. */
const val KIDS_WARN_EARLY_MS = 60_000L

/** The warning is due: Open, not given yet for this end, and the end at most 10 minutes away (a minute's slack). */
fun warnDue(mode: KidsMode, nowMs: Long, warnedFor: Long): Boolean =
    mode is KidsMode.Open && warnedFor != mode.untilMs && nowMs >= warnAtMs(mode.untilMs) - KIDS_WARN_EARLY_MS

/** The game screen's answer on the ordered KIDS_WARN broadcast: it was resumed and showed the toast. */
const val KIDS_WARN_SHOWN = 1

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

    /**
     * [plan] for a warning held back because the screen is off: only the lock, so a held warning is not set again for
     * now (an alarm at once, a wake loop); the screen-on path gives it ([warnDue]).
     */
    fun planHeld(mode: KidsMode, nowMs: Long): List<Alarm> =
        if (mode is KidsMode.Open) plan(mode, nowMs, warnedFor = mode.untilMs) else emptyList()

    const val ACTION_WARN = "com.nxredux.mobile.kids.WARN"
    const val ACTION_LOCK = "com.nxredux.mobile.kids.LOCK"
    /** Parent mode's 5 minutes without input, while away from the menu ([setParentIdle]). */
    const val ACTION_PARENT_IDLE = "com.nxredux.mobile.kids.PARENT_IDLE"
    /** A game's input (at most every 30 s, [kidsInputDue]): Parent mode's idle time starts again. */
    const val ACTION_GAME_INPUT = "com.nxredux.mobile.kids.GAME_INPUT"
    private const val KEY_WARNED_FOR = "kids_warned_for"

    /** The end whose warning was given (epoch ms; 0: none), kept across process deaths so it fires once per end. */
    fun warnedFor(context: Context): Long = prefs(context).getLong(KEY_WARNED_FOR, 0L)

    fun setWarnedFor(context: Context, untilMs: Long) { prefs(context).edit().putLong(KEY_WARNED_FOR, untilMs).apply() }

    private fun prefs(context: Context) = Prefs.open(context.applicationContext, AndroidAppSettings.PREFS_NAME)

    /** [mode]'s alarms from now, replacing the ones set before. */
    fun reschedule(context: Context, mode: KidsMode, nowMs: Long) = set(context, plan(mode, nowMs, warnedFor(context)))

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

    /** Parent mode's idle end at [atMs] (null cancels it): the menu is away, so its own 5 s tick does not run. */
    fun setParentIdle(context: Context, atMs: Long?) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            app, PARENT_IDLE_REQUEST, Intent(app, KidsAlarmReceiver::class.java).setAction(ACTION_PARENT_IDLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (atMs == null) { am.cancel(pi); return }
        try {
            if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    private const val PARENT_IDLE_REQUEST = 2   // after the kinds' ordinals (0, 1)

    /** Alarms & reminders: Android 12+ asks for it (denied by default on 14+); before 12 every app has it. */
    fun canExact(am: AlarmManager): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun pendingIntent(app: Context, kind: Kind): PendingIntent {
        val action = if (kind == Kind.WARN) ACTION_WARN else ACTION_LOCK
        val intent = Intent(app, KidsAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(app, kind.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private const val TAG = "NXKIDS"
}
