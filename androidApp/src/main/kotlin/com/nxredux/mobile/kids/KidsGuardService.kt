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
import android.os.HandlerThread
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
 * ([guardRuns], driven by [AndroidKids]), it finds the activity in front ([guardFront]) once every [POLL_MS] and sends
 * one not allowed ([guardShouldBounce]) back to the tiles screen through [LaunchActivity], as the lock does. The first
 * look goes back [FIRST_WINDOW_MS] (a cold lock alarm with an Android game or another app long in front); later looks
 * go back [WINDOW_MS]. Our own game screen in front (resumed from Recents while Locked) is locked as the lock does it:
 * the game quits first, then the menu comes forward ([AndroidKids.quitGameThenShowMenu]).
 *
 * The bounce is an activity start from the background. Android 10+ lets it through when NX Redux is the default home
 * app (the home app is always allowed to start activities); otherwise Android usually drops it and the kid stays where
 * they are (the ongoing notification still opens the tiles screen).
 *
 * Start and stop go through [update] only: a stop never reaches an instance before its startForeground (a crash on
 * API 28+); a start in flight reads [wanted] in onStartCommand instead. The service's own calls are on the main thread;
 * each look runs on its own thread (usage events, the keyboard setting and the allowed apps are reads across processes,
 * and the first look scans hours of events), and only the decision and the bounce come back to main.
 */
class KidsGuardService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var looks: HandlerThread
    private lateinit var look: Handler
    private lateinit var settings: AndroidAppSettings
    private lateinit var kids: KidsController
    /** The resume event last sent back, so one event is bounced once (the next poll may still see it). Set on main; a new run clears it on [look]'s thread. */
    @Volatile private var bounced: GuardEvent? = null
    /** The first look of this guard (a long one) is still to come. [look]'s thread only, as are the two below. */
    private var firstLook = true
    private var allowedFrom: Set<String>? = null
    private var allowedPkgs: Set<String> = emptySet()

    /**
     * The current poll chain's number; a stop or a new start moves it on (main), so a chain whose look is under way when
     * removeCallbacks runs ends there instead of reposting beside the new one.
     */
    @Volatile private var generation = 0
    private var poll: Runnable? = null   // the current chain, for removeCallbacks (main)

    /** A new poll chain [gen]; its first run starts a new guard run: the long first look again, nothing bounced yet. */
    private fun pollChain(gen: Int): Runnable = object : Runnable {
        private var fresh = true
        override fun run() {
            if (generation != gen) return
            if (fresh) { fresh = false; firstLook = true; bounced = null }
            check()
            if (generation == gen) look.postDelayed(this, POLL_MS)
        }
    }

    /** Main: ends the current poll chain, the look under way included (it does not post again). */
    private fun stopPolling() {
        generation++
        poll?.let(look::removeCallbacks)
        poll = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = AndroidAppSettings(this)
        kids = AndroidKids.get(this)
        looks = HandlerThread("kids-guard").also { it.start() }
        look = Handler(looks.looper)
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
            stopPolling()
            poll = pollChain(generation).also(look::post)
        }
        if (!wanted) stop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        looks.quitSafely()
        if (running === this) running = null
        super.onDestroy()
    }

    private fun stop() {
        stopPolling()
        if (running === this) running = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** [look]'s thread: what is in front and what may be, handed to [decide] on main. */
    private fun check() {
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) return   // nobody is using another app
        val window = if (firstLook) FIRST_WINDOW_MS else WINDOW_MS
        firstLook = false
        val front = front(window) ?: return
        if (front == bounced) return
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val allowed = allowedPackages()
        main.post { decide(front, ime, allowed) }
    }

    /** The allowed apps' packages, worked out again only when the list changed. */
    private fun allowedPackages(): Set<String> {
        val apps = settings.kidsAllowedApps
        if (apps != allowedFrom) {
            allowedPkgs = guardAllowedPackages(apps)
            allowedFrom = apps
        }
        return allowedPkgs
    }

    /** Main thread: sends [front] back unless it may stay. A look that lands after the guard stopped does nothing. */
    private fun decide(front: GuardEvent, ime: String?, allowed: Set<String>) {
        if (running !== this || front == bounced) return
        val pkg = front.pkg
        val exempt = guardExempt(ime, kids.granting)
        val game = com.nxredux.mobile.runner.GameActivity::class.java.name
        if (!guardShouldBounce(pkg, front.cls, packageName, game, allowed, exempt)) return
        bounced = front
        if (pkg == packageName) {   // our game screen while Locked: it quits as at the lock, then the tiles
            NxLog.i(TAG, "guard: the game screen locked")
            AndroidKids.quitGameThenShowMenu(this)
            return
        }
        NxLog.i(TAG, "guard: $pkg sent back")
        runCatching { startActivity(Intent(this, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { NxLog.w(TAG, "guard: menu not brought forward (${it::class.java.simpleName})") }
    }

    /** The activity in front from the usage events of the past [windowMs] ([guardFront]); null when none (or no Usage access). */
    private fun front(windowMs: Long): GuardEvent? {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - windowMs, now) }.getOrNull() ?: return null
        // ACTIVITY_RESUMED / _PAUSED are MOVE_TO_FOREGROUND / _BACKGROUND's values (1, 2) below API 29; STOPPED is 29+.
        @Suppress("DEPRECATION") val resumed = UsageEvents.Event.MOVE_TO_FOREGROUND
        @Suppress("DEPRECATION") val paused = UsageEvents.Event.MOVE_TO_BACKGROUND
        val stopped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) UsageEvents.Event.ACTIVITY_STOPPED else -1
        val e = UsageEvents.Event()
        val list = ArrayList<GuardEvent>()
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(e)) break
            val type = when (e.eventType) {
                resumed -> GuardEventType.RESUMED
                paused -> GuardEventType.PAUSED
                stopped -> GuardEventType.STOPPED
                else -> continue
            }
            list += GuardEvent(type, e.packageName, e.className, e.timeStamp)
        }
        return guardFront(list)
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
        /** The first look: what has been in front since a lock long ago (a cold lock alarm), up to 6 hours back. */
        const val FIRST_WINDOW_MS = 6 * 3_600_000L

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
