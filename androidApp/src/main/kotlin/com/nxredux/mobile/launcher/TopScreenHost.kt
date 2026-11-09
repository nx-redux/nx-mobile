package com.nxredux.mobile.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.swordfish.libretrodroid.NxLog
import java.lang.ref.WeakReference

/**
 * Dual mode's stand-in on the top screen while an app opened there runs ([AndroidAppLauncher.launch]). Our window on that
 * display (a presentation) sits above every app window, so it goes down while the app runs, and Android tells no one
 * when another app's task leaves a display. This invisible activity, in a task of its own on that display, starts the
 * app over itself; when it shows again (the app finished, went to the back or crashed) the app has left the top screen:
 * [onChange] brings the menu's top screen back, and it closes. A newer host closes the older one. Something else covering
 * the host (a home on that display) is not seen this way: [recheck] and [release] are the other ways back.
 *
 * The menu's window on that display goes down before the host starts ([start]): over it, the system never shows the
 * translucent host (Samsung DeX left it invisible and never created). A host not created within [HOST_TIMEOUT_MS] is
 * removed and the app opens there directly ([direct]).
 */
class TopScreenHost : Activity() {
    private val watch = CoverWatch()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {   // recreated behind the app: it was started already
            if (!active || direct) return close()   // the top screen was let go of meanwhile
            current = WeakReference(this)
            watch.covered()
            return
        }
        // Only the host [start] waits for: one created after its time ran out ([hostLate]) closes.
        if (intent.getIntExtra(EXTRA_TOKEN, -1) != token || waiting == null) return close()
        main.removeCallbacks(waiting!!)
        waiting = null
        current = WeakReference(this)
        @Suppress("DEPRECATION")
        val app = intent.getParcelableExtra<Intent>(EXTRA_APP) ?: return free()
        val wanted = intent.getIntExtra(EXTRA_DISPLAY, Display.INVALID_DISPLAY)
        val fallback = intent.getIntExtra(EXTRA_FALLBACK, Display.DEFAULT_DISPLAY)
        // Landed on another display than asked (the system may place it elsewhere): the normal launch on the menu's
        // display, and no host.
        if (currentDisplayId() != wanted) return fallBack(app, fallback)
        if (!launch(this, app, wanted, fullBounds = true)) fallBack(app, fallback)   // the app refuses this display
    }

    /** The normal launch on the menu's display; no "Could not open" reaches the menu from here (launch() said yes). */
    private fun fallBack(app: Intent, fallback: Int) {
        if (!launch(this, app, fallback, fullBounds = false)) NxLog.w("NXAPP", "top screen app: the fallback launch failed too")
        free()
    }

    override fun onResume() {
        super.onResume()
        if (watch.resumed()) free()
    }

    override fun onPause() { watch.covered(); super.onPause() }

    override fun onStop() { watch.covered(); super.onStop() }

    override fun onDestroy() {
        // Closed some other way (swiped from Recents): the screen is free too. Destroyed without finishing ("Don't keep
        // activities") it comes back through savedInstanceState when the app leaves, and frees then.
        if (current?.get() === this) { if (active && isFinishing) free() else current = null }
        super.onDestroy()
    }

    private fun free() {
        if (current?.get() === this) {   // not a host that was let go of ([removeHostTasks]): a newer start may be on its way
            current = null
            if (active) { active = false; onChange() }
        }
        close()
    }

    private fun close() {
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_APP = "app"
        private const val EXTRA_DISPLAY = "display"
        private const val EXTRA_FALLBACK = "fallback"
        private const val EXTRA_TOKEN = "token"
        private const val HOST_TIMEOUT_MS = 2000L
        private var current: WeakReference<TopScreenHost>? = null
        private val main = Handler(Looper.getMainLooper())
        /** [start]'s count: the host it waits for carries it. */
        private var token = 0
        /** [hostLate], pending while the started host is not created yet. */
        private var waiting: Runnable? = null

        /**
         * The host never came ([hostLate]): the app was opened on the top screen directly, with no host to say when it
         * leaves; the menu regaining focus ([recheck]) takes the top screen back.
         */
        private var direct = false

        /** An app opened on the top screen is there: from [start] until its host shows again. Main thread only. */
        var active = false
            private set

        /** The display [active] holds. */
        var heldDisplay = Display.INVALID_DISPLAY
            private set

        /** [active] changed: the menu's window on the top screen goes down or comes back. Set once by MainActivity; main thread. */
        var onChange: () -> Unit = {}

        /**
         * The menu is in front again: a host whose task is gone (closed without showing again: killed, cleared) frees the
         * top screen. Only this app's own tasks are visible ([ActivityManager.getAppTasks]): whether the app is still
         * above a living host cannot be read, so a host covered by something else waits for [release].
         */
        fun recheck(context: Context) {
            if (!active || waiting != null) return   // a host still starting has its own timeout
            if (!direct && hostTasks(context).isNotEmpty()) return
            active = false
            direct = false
            onChange()
        }

        /** The top screen's display went away, or dual mode was turned off: no host waits any more. The caller re-syncs. */
        fun release(context: Context) {
            TopScreenCover.uncover()
            if (!active) return
            active = false
            direct = false
            stopWaiting()
            current = null
            removeHostTasks(context)
        }

        /**
         * Kids Mode's guard sent an app back while an app opened on the top screen was there: the top screen is taken
         * back, the menu's window over whatever is on that display now (a bounce to the menu covers only the menu's own
         * display) and a [TopScreenCover] in front of it. False when nothing held the top screen.
         */
        fun takeBack(context: Context): Boolean {
            if (!active) return false
            val display = heldDisplay
            release(context)
            TopScreenCover.cover(context, display)
            onChange()
            return true
        }

        private fun stopWaiting() {
            waiting?.let(main::removeCallbacks)
            waiting = null
        }

        /** This app's host tasks ([ActivityManager.getAppTasks] lists only this app's own). */
        private fun hostTasks(context: Context): List<ActivityManager.AppTask> {
            val tasks = runCatching { context.getSystemService(ActivityManager::class.java).appTasks }.getOrNull() ?: return emptyList()
            val name = TopScreenHost::class.java.name
            return tasks.filter { t -> runCatching { t.taskInfo?.let { it.baseActivity ?: it.baseIntent.component }?.className }.getOrNull() == name }
        }

        /** Every host task goes, without a word to the menu ([current] is cleared first, so no host frees on its way out). */
        private fun removeHostTasks(context: Context) {
            current = null
            hostTasks(context).forEach { runCatching { it.finishAndRemoveTask() } }
        }

        /**
         * [app] on [display]: launch display named and, with [fullBounds], the whole display as its bounds (a freeform
         * desktop such as DeX opens it as a window that size; elsewhere bounds are ignored). False when refused.
         */
        private fun launch(from: Context, app: Intent, display: Int, fullBounds: Boolean): Boolean = try {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display)
            if (fullBounds) boundsOf(from, display)?.let(options::setLaunchBounds)
            from.startActivity(app, options.toBundle())
            true
        } catch (e: RuntimeException) {   // ActivityNotFound, Security, IllegalArgument: the display or the app said no
            NxLog.w("NXAPP", "top screen app on display $display: ${e.message}")
            false
        }

        private fun boundsOf(context: Context, display: Int): Rect? {
            val d = context.getSystemService(DisplayManager::class.java)?.getDisplay(display) ?: return null
            val size = Point().also { @Suppress("DEPRECATION") d.getRealSize(it) }
            return Rect(0, 0, size.x, size.y).takeIf { !it.isEmpty }
        }

        /**
         * The host was not created in time (an invisible host never is): its task goes, and the app opens on [display]
         * directly, the top screen taken until the menu has focus again ([direct]); refused there, the normal launch on
         * [fallback] and the top screen back.
         */
        private fun hostLate(from: WeakReference<Activity>, app: Intent, display: Int, fallback: Int) {
            waiting = null
            val context = from.get()
            NxLog.w("NXAPP", "top screen host not shown in ${HOST_TIMEOUT_MS} ms: the app opens directly")
            if (context != null) removeHostTasks(context)
            if (context != null && launch(context, app, display, fullBounds = true)) { direct = true; return }
            if (context != null && !launch(context, app, fallback, fullBounds = false)) NxLog.w("NXAPP", "top screen app: the fallback launch failed too")
            active = false
            onChange()
        }

        /**
         * Starts a host on [display] that opens [app] there; [fallback] is the display for the normal launch should the
         * host land elsewhere or the app refuse [display]. Earlier hosts go first; the menu's window on [display] goes
         * down before the host starts ([active], a frame of the empty display shows). False when [display] takes no
         * activity from this app (the caller then launches as usual, the window comes back).
         */
        fun start(from: Activity, app: Intent, display: Int, fallback: Int): Boolean {
            TopScreenCover.uncover()
            stopWaiting()
            removeHostTasks(from)
            direct = false
            active = true
            heldDisplay = display
            onChange()
            val host = Intent(from, TopScreenHost::class.java)
                .putExtra(EXTRA_APP, app).putExtra(EXTRA_DISPLAY, display).putExtra(EXTRA_FALLBACK, fallback).putExtra(EXTRA_TOKEN, ++token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            val started = try {
                from.startActivity(host, ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle())
                true
            } catch (e: SecurityException) {
                NxLog.w("NXAPP", "top screen display $display: ${e.message}"); false
            } catch (e: IllegalArgumentException) {
                NxLog.w("NXAPP", "top screen display $display: ${e.message}"); false
            }
            if (!started) { active = false; onChange(); return false }
            val late = WeakReference(from).let { ref -> Runnable { hostLate(ref, app, display, fallback) } }
            waiting = late
            main.postDelayed(late, HOST_TIMEOUT_MS)
            return true
        }
    }
}

/**
 * The host's lifecycle read: its first showing is itself, before the app it started covers it; once covered (paused or
 * stopped), the next showing means the app has gone.
 */
internal class CoverWatch {
    private var covered = false
    fun covered() { covered = true }
    /** True when this showing comes after the app covered the host. */
    fun resumed(): Boolean = covered
}

/**
 * Where an app opens in dual mode: the top screen's display, the one without the menu. Null outside dual mode or with no
 * second display: launch as usual.
 */
internal fun topScreenDisplay(dual: Boolean, menuOnSecond: Boolean, own: Int, second: Int?): Int? = when {
    !dual || second == null -> null
    menuOnSecond -> own
    else -> second
}

/** How [AndroidAppLauncher.launch] starts an app. */
internal enum class LaunchRoute {
    /** Not dual mode: as always. */
    USUAL,
    /** The top screen is the launching activity's display: there, named as the launch display. */
    HERE,
    /** The top screen is another display: through a [TopScreenHost] there. */
    HOST,
}

internal fun launchRoute(top: Int?, own: Int): LaunchRoute = when (top) {
    null -> LaunchRoute.USUAL
    own -> LaunchRoute.HERE
    else -> LaunchRoute.HOST
}

/** The display [this] activity is on now. */
fun Activity.currentDisplayId(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.displayId ?: Display.DEFAULT_DISPLAY
    else @Suppress("DEPRECATION") windowManager.defaultDisplay.displayId
