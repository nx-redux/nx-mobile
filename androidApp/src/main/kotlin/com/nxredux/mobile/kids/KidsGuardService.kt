package com.nxredux.mobile.kids

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nxredux.mobile.LaunchActivity
import com.nxredux.mobile.NotificationIds
import com.nxredux.mobile.settings.AndroidAppSettings
import com.swordfish.libretrodroid.NxLog

/**
 * Kids Mode's guard (foreground service, type specialUse "parental controls"): while Locked and no menu is shown
 * ([guardRuns], driven by [AndroidKids]), it reads the app last resumed in the past [WINDOW_MS] once every [POLL_MS]
 * and sends one not allowed ([guardShouldBounce]) back to the tiles screen through [LaunchActivity], as the lock does.
 *
 * The bounce is an activity start from the background. Android 10+ lets it through when NX Redux is the default home
 * app (the home app is always allowed to start activities); otherwise Android usually drops it and the kid stays where
 * they are (the ongoing notification still opens the tiles screen).
 *
 * Start and stop go through [update] only: a stop never reaches an instance before its startForeground (a crash on
 * API 28+); a start in flight reads [wanted] in onStartCommand instead. Main thread only.
 */
class KidsGuardService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var settings: AndroidAppSettings
    private lateinit var kids: KidsController
    /** The resume event last sent back, so one event is bounced once (the next poll may still see it). */
    private var bouncedPkg: String? = null
    private var bouncedAt = 0L

    private val poll = object : Runnable {
        override fun run() {
            check()
            main.postDelayed(this, POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = AndroidAppSettings(this)
        kids = AndroidKids.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The answer to startForegroundService: startForeground is owed even when the guard is no longer wanted.
        starting = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, NotificationIds.KIDS_GUARD, notice(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NotificationIds.KIDS_GUARD, notice())
        } catch (e: Exception) {
            NxLog.w(TAG, "guard could not enter the foreground (${e::class.java.simpleName})")
            if (running === this) running = null
            stopSelf(); return START_NOT_STICKY
        }
        if (running !== this) {
            running = this
            main.removeCallbacks(poll)
            main.post(poll)
        }
        if (!wanted) stop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(poll)
        if (running === this) running = null
        super.onDestroy()
    }

    private fun stop() {
        main.removeCallbacks(poll)
        if (running === this) running = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun check() {
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) return   // nobody is using another app
        val (pkg, at) = lastResumed() ?: return
        if (pkg == bouncedPkg && at == bouncedAt) return
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val exempt = guardExempt(ime, kids.granting)
        if (!guardShouldBounce(pkg, packageName, guardAllowedPackages(settings.kidsAllowedApps), exempt)) return
        bouncedPkg = pkg; bouncedAt = at
        NxLog.i(TAG, "guard: $pkg sent back")
        runCatching { startActivity(Intent(this, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { NxLog.w(TAG, "guard: menu not brought forward (${it::class.java.simpleName})") }
    }

    /** The package and time of the last activity resume in the past [WINDOW_MS]; null when none (or no Usage access). */
    private fun lastResumed(): Pair<String, Long>? {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - WINDOW_MS, now) }.getOrNull() ?: return null
        val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) UsageEvents.Event.ACTIVITY_RESUMED
        else @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_FOREGROUND
        val e = UsageEvents.Event()
        var last: Pair<String, Long>? = null
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(e)) break
            if (e.eventType == resumed) last = e.packageName to e.timeStamp
        }
        return last
    }

    private fun notice(): Notification {
        getSystemService(NotificationManager::class.java)?.let(AndroidKids::ensureChannel)
        return NotificationCompat.Builder(this, AndroidKids.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentText(KIDS_GUARD_NOTICE)
            .setContentIntent(AndroidKids.openMenu(this))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "NXKIDS"
        const val POLL_MS = 1_000L
        const val WINDOW_MS = 5_000L

        // Main thread only.
        private var wanted = false
        private var starting = false
        private var running: KidsGuardService? = null

        /**
         * The guard on or off. A start from the background may be refused (Android 12+): it is logged and the guard
         * stays off until the next chance (the menu's onStop, an exact alarm).
         */
        fun update(context: Context, want: Boolean) {
            wanted = want
            val svc = running
            when {
                svc != null && !want -> svc.stop()
                svc == null && want && !starting -> {
                    starting = true
                    val app = context.applicationContext
                    try {
                        ContextCompat.startForegroundService(app, Intent(app, KidsGuardService::class.java))
                    } catch (e: Exception) {
                        starting = false
                        NxLog.w(TAG, "guard not started (${e::class.java.simpleName})")
                    }
                }
            }
        }
    }
}
