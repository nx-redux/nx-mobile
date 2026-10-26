package com.nxredux.mobile.runner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
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
 * Flex mode detection for [activity]: follows WindowManager's folding feature while started and, while a feature is
 * present, the hinge angle sensor (for foldables that report the feature flat at a laptop angle, [postureFromHinge]).
 * [area] gives the laid-out area's top in window px and its height (null: not laid out yet); [onChange] gets each
 * posture change, debounced by [DEBOUNCE_MS], with the hinge angle it was decided at.
 *
 * Each posture it holds while started is also published as the process's [PostureHint], so a window opened meanwhile
 * (the game, launched from the menu) can lay itself out for it before its own reading arrives ([seed]).
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
     * Starts from [PostureHint] instead of [Posture.Flat] ([seededPosture]: only for a window of the hint's size), before
     * [start]: the first reading that agrees changes nothing, one that does not replaces it as any change does.
     */
    fun seed(): Posture {
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        val hint = PostureHint.seen
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

    /** [posture] (area-relative, the area at window y [top]) as the process's [PostureHint], in window px. */
    private fun publish(top: Int) {
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        val p = posture
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
 * when none is started. Main thread only. The menu's tracker is still started while the game's activity is created
 * (the menu stops after it), so a game launched half-folded reads it and builds the tabletop layout first.
 */
object PostureHint {
    class Seen(val owner: PostureTracker, val posture: Posture, val windowW: Int, val windowH: Int)

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
