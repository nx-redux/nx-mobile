package com.nxredux.mobile.runner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fold posture detection for [activity] (a pad skin's hinge): follows WindowManager's folding feature while started and, while a feature is
 * present, the hinge angle sensor (for foldables that report the feature flat at a laptop angle, [postureFromHinge]).
 * [area] gives the laid-out area's top in window px and its height (null: not laid out yet); [onChange] gets each
 * posture change, debounced by [DEBOUNCE_MS], with the hinge angle it was decided at.
 *
 * Each posture it holds while started is also published as the process's [PostureHint]: the game runs in its own
 * process, so the launch carries the hint in its intent ([postureSeedExtras]) and the game's window lays itself out for
 * it before its own reading arrives ([seed]). Published only when the posture or the window changed, and never in the
 * game's process (nothing launches a game from there).
 *
 * Construction touches no system service (safe as an activity field, before onCreate); call [start] from onCreate.
 */
class PostureTracker(
    private val activity: ComponentActivity,
    private val area: () -> Pair<Int, Int>?,
    private val onChange: (posture: Posture, angle: Float?) -> Unit,
) {
    var posture: Posture = Posture.Flat
        private set
    private var fold: FoldingFeature? = null
    private var angle: Float? = null
    private var pending: Posture? = null
    private var pendingJob: Job? = null
    /** Only the menu's process hands a hint on. */
    private val publishes by lazy { !com.nxredux.mobile.AppProcess.isGame() }
    // What was last published ([publish]): the posture, the area's top and the window's size; [publishedPosture] null
    // when nothing is (not yet, or since a stop).
    private var publishedPosture: Posture? = null
    private var publishedTop = 0
    private var publishedW = 0
    private var publishedH = 0

    /**
     * The hinge angle sensor: an on-change sensor, registered at SENSOR_DELAY_NORMAL only while started and a folding
     * feature is present, and unregistered on stop (the STARTED block ends). A phone without a hinge (no sensor, or no
     * feature) never registers it, so it costs nothing there. Both are lazy: an activity's system services are not
     * available before its onCreate, and a tracker may be built as a field (construction must never call getSystemService).
     */
    private val sensors by lazy { activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    private val hingeSensor: Sensor? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) else null
    }
    private var listening = false
    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            angle = event.values.firstOrNull()
            update()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /**
     * Starts from [hint] (the menu's, from the launch intent) instead of [Posture.Flat] ([seededPosture]: only for a
     * window of the hint's size), before [start]: the first reading that agrees changes nothing, one that does not
     * replaces it as any change does.
     */
    fun seed(hint: PostureSeed?): Posture {
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        posture = seededPosture(hint?.posture, hint?.windowW ?: 0, hint?.windowH ?: 0, bounds.width(), bounds.height())
        return posture
    }

    fun start() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
                        fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                        fold?.let { NxLog.i(TAG, "fold: state=${it.state} orientation=${it.orientation} bounds=${it.bounds}") }
                        listen(fold != null)
                        update()
                    }
                } finally {
                    listen(false)
                    PostureHint.forget(this@PostureTracker)   // stopped: no longer a live reading for a new window
                    publishedPosture = null
                }
            }
        }
    }

    /** Re-decides from the last feature and angle (also when the area's size changes). */
    fun update() {
        val f = fold
        val (top, height) = area() ?: return
        val next = if (f == null) Posture.Flat else postureFromHinge(
            halfOpened = f.state == FoldingFeature.State.HALF_OPENED,
            horizontal = f.orientation == FoldingFeature.Orientation.HORIZONTAL,
            top = f.bounds.top - top, bottom = f.bounds.bottom - top, windowH = height,
            hingeAngle = angle, previous = posture,
        )
        if (next == posture) {
            pendingJob?.cancel(); pendingJob = null; pending = null
            publish(top)
            return
        }
        if (next == pending) return   // already waiting to commit it: the sensor's stream must not keep restarting the wait
        pending = next
        pendingJob?.cancel()
        pendingJob = activity.lifecycleScope.launch {
            delay(DEBOUNCE_MS)
            pending = null
            posture = next
            publish(top)
            onChange(next, angle)
        }
    }

    /**
     * [posture] (area-relative, the area at window y [top]) as the process's [PostureHint], in window px. The hinge
     * sensor calls this on every event: the decor view's size stands in for the window's to tell nothing changed, so
     * the metrics are computed and a new hint made only on a change.
     */
    private fun publish(top: Int) {
        if (!publishes) return
        val p = posture
        val decor = activity.window?.decorView
        val w = decor?.width ?: 0
        val h = decor?.height ?: 0
        if (p == publishedPosture && top == publishedTop && w == publishedW && h == publishedH && PostureHint.seen?.owner === this) return
        publishedPosture = p; publishedTop = top; publishedW = w; publishedH = h
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        val inWindow = if (p is Posture.Tabletop) Posture.Tabletop(p.hingeTop + top, p.hingeBottom + top) else p
        PostureHint.publish(this, inWindow, bounds.width(), bounds.height())
    }

    private fun listen(on: Boolean) {
        val s = hingeSensor ?: return
        if (on == listening) return
        listening = on
        if (on) sensors?.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
        else {
            sensors?.unregisterListener(listener)
            angle = null
        }
    }

    companion object {
        private const val TAG = "NXAPP"
        const val DEBOUNCE_MS = 200L
    }
}

/**
 * The last posture a started [PostureTracker] decided, in window px for a [Seen.windowW] x [Seen.windowH] window; null
 * when none is started. Main thread only. The menu's tracker is still started when it launches a game, so the launch
 * hands it to the game's process ([postureSeedExtras]) and a game launched half-folded places its skin at the hinge first.
 */
object PostureHint {
    class Seen(val owner: PostureTracker, val posture: Posture, val windowW: Int, val windowH: Int) {
        val seed: PostureSeed get() = PostureSeed(posture, windowW, windowH)
    }

    var seen: Seen? = null
        private set

    fun publish(owner: PostureTracker, posture: Posture, windowW: Int, windowH: Int) {
        seen = Seen(owner, posture, windowW, windowH)
    }

    /** [owner] stopped: its reading goes, unless another tracker has published since. */
    fun forget(owner: PostureTracker) {
        if (seen?.owner === owner) seen = null
    }
}

/** A posture in window px for a [windowW] x [windowH] window, as it travels from the menu to the game's process. */
data class PostureSeed(val posture: Posture, val windowW: Int, val windowH: Int)

internal const val KEY_POSTURE_WINDOW_W = "posture_window_w"
internal const val KEY_POSTURE_WINDOW_H = "posture_window_h"
internal const val KEY_POSTURE_HINGE_TOP = "posture_hinge_top"
internal const val KEY_POSTURE_HINGE_BOTTOM = "posture_hinge_bottom"

/** [seed] as key/value pairs through [put] ([readPostureSeed] reads them back); nothing for none. */
internal fun writePostureSeed(seed: PostureSeed?, put: (String, Int) -> Unit) {
    seed ?: return
    put(KEY_POSTURE_WINDOW_W, seed.windowW)
    put(KEY_POSTURE_WINDOW_H, seed.windowH)
    val p = seed.posture
    if (p is Posture.Tabletop) {
        put(KEY_POSTURE_HINGE_TOP, p.hingeTop)
        put(KEY_POSTURE_HINGE_BOTTOM, p.hingeBottom)
    }
}

/** The seed [writePostureSeed] wrote, read through [get]; null when there is none or no window size. */
internal fun readPostureSeed(get: (String) -> Int?): PostureSeed? {
    val w = get(KEY_POSTURE_WINDOW_W) ?: return null
    val h = get(KEY_POSTURE_WINDOW_H) ?: return null
    val top = get(KEY_POSTURE_HINGE_TOP)
    val bottom = get(KEY_POSTURE_HINGE_BOTTOM)
    val posture = if (top != null && bottom != null) Posture.Tabletop(top, bottom) else Posture.Flat
    return PostureSeed(posture, w, h)
}

/** This process's [PostureHint] as extras for the game's intent ([postureSeedFrom] reads them back). */
internal fun postureSeedExtras(): Bundle = Bundle().apply { writePostureSeed(PostureHint.seen?.seed, ::putInt) }

/** The menu's posture in a game intent's [extras]; null: none (no tracker was started, or another launcher). */
internal fun postureSeedFrom(extras: Bundle?): PostureSeed? =
    extras?.let { b -> readPostureSeed { k -> if (b.containsKey(k)) b.getInt(k) else null } }
