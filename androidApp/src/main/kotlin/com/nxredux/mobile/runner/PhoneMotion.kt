package com.nxredux.mobile.runner

import android.os.Build
import androidx.lifecycle.Lifecycle
import com.swordfish.libretrodroid.LibretroDroid

/**
 * The phone's motion sensors feeding the core's port 0 (libretrodroid's SensorState), for the console that takes them: a
 * Wii game's Wiimote and a 3DS game ([WiiDolphinController]), a Wii U game's GamePad ([WiiuMotionController]). Made on
 * first use; listening only while the game runs. Main thread.
 */
internal class PhoneMotion(private val host: WiiDolphinHost) {
    /** What the sensors feed while the game runs. */
    sealed interface Feed {
        class Wiimote(val pad: () -> WiiPad) : Feed
        data object N3ds : Feed
        data object GamePad : Feed
    }

    /** The phone's accelerometer and gyroscope, created for a game that takes motion only. */
    var sensors: MotionSensors? = null
        private set
    /** The display rotation the motion level was last captured in: a change mid-game recentres ([onRotationChanged]). */
    private var lastRotation = -1

    /** Both of the phone's motion sensors are there (Motion Controls can be offered). */
    val hasSensors: Boolean get() = sensors?.hasSensors == true

    /** The phone's sensors feeding port 0, made on first use. */
    fun sensors(): MotionSensors = sensors ?: MotionSensors(host.activity) { a, g ->
        LibretroDroid.setSensorValues(0, a.x, a.y, a.z, g.x, g.y, g.z)
    }.also { sensors = it }

    /** Offers the phone's sensors to the core (made now if need be): SensorState grants a core only a sensor offered. */
    fun offer(): MotionSensors = sensors().also { LibretroDroid.setSensorAvailable(it.hasAccel, it.hasGyro) }

    /** Offered again, when made: a previous core's unload resets the native sensor state. */
    fun reoffer() { sensors?.let { LibretroDroid.setSensorAvailable(it.hasAccel, it.hasGyro) } }

    /**
     * The rotation the level starts in: only a real rotation recentres, not the first unrelated configuration change (a
     * pad attached, a fold posture).
     */
    fun captureRotation() { lastRotation = displayRotation() }

    /** The motion level is captured in the display's frame: a new rotation takes the phone's attitude as level again. */
    fun onRotationChanged() { displayRotation().let { r -> if (r != lastRotation) { lastRotation = r; sensors?.recenter() } } }

    /** Takes the phone's attitude at the next reading as the level. */
    fun recenter() { sensors?.recenter() }

    /**
     * The sensors listen for [feed] (null: none) only while the game runs (not paused, menu closed, in front).
     * [foreground]: onResume and onPause pass it, as the lifecycle state there does not say yet which side of the pause
     * this is.
     */
    fun sync(feed: Feed?, foreground: Boolean = host.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        val sensors = sensors ?: return
        val run = feed != null && host.gameReady && !host.menuOpen && !host.quitting && foreground
        when {
            !run -> sensors.stop()
            feed is Feed.N3ds -> sensors.startN3ds(::displayRotation)
            feed is Feed.GamePad -> sensors.startGamePad(::displayRotation)
            feed is Feed.Wiimote -> sensors.start(::displayRotation, feed.pad)
        }
    }

    fun onDestroy() {
        // Made only by a path that enables sensor 0; a next game sets its own on its first frame.
        sensors?.let { it.stop(); LibretroDroid.setSensorEnabled(0, false) }
    }

    /** The display's rotation (Surface.ROTATION_*), which [MotionSensors] turns the phone's axes by. */
    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) host.activity.display?.rotation ?: 0 else host.activity.windowManager.defaultDisplay.rotation
}
