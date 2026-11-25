package com.nxredux.mobile.runner

import com.nxredux.mobile.options.WiiuOptions
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.Variable

/**
 * Motion Controls for a Wii U game: the phone's sensors ([motion]) as the GamePad's, through Cemu's GamePad Motion
 * ([WiiuOptions.CORE_MOTION]). Cemu asks for both sensors when that option turns on, which it reads on its first run and
 * on every change after, and libretrodroid grants them only while they are offered and port 0 is enabled: so both are
 * set before the core loads, and again on the first frame. Main thread.
 */
internal class WiiuMotionController(private val host: WiiDolphinHost, private val motion: PhoneMotion) {
    /** Motion Controls resolved ([WiiuOptions.motionOn]); null until the launch resolves it, and outside the Wii U. */
    private var motionOn: Boolean? = null

    val hasSensors: Boolean get() = motion.hasSensors

    /** Launch: the sensors are found (Motion Controls is offered only with both) and the row resolved. */
    fun resolveAtLaunch(tag: String) {
        if (!WiiuOptions.appliesTo(tag)) return
        val on = WiiuOptions.motionOn(host.frontendValues[WiiuOptions.MOTION], motion.sensors().hasSensors)
        motionOn = on
        NxLog.i(TAG_LOG, "Wii U motion $on")
        host.publishMenuFrontend()   // a menu opened during the launch: its Motion Controls row, left out until now
    }

    /** Cemu's GamePad Motion for the launch; empty outside the Wii U. */
    fun launchOption(): Map<String, String> = motionOn?.let(WiiuOptions::motionOption).orEmpty()

    /** Right before the core loads: the sensors offered, port 0 on as the row resolved, for Cemu's ask on its first run. */
    fun offerBeforeLoad() {
        val on = motionOn ?: return
        motion.offer()
        LibretroDroid.setSensorEnabled(0, on)
    }

    /**
     * The first frame: offered and enabled again (a previous game's core unload, its onDestroy running after this
     * screen's onCreate, resets the native sensor state, and without it the core reads the sensors as still), the row as
     * it is now (a change made while the game loaded), and the sensors listening.
     */
    fun onFirstFrame() {
        val on = motionOn ?: return
        motion.reoffer()
        LibretroDroid.setSensorEnabled(0, on)
        host.applyOptions(WiiuOptions.motionOption(on).map { (k, v) -> Variable(k, v) })
        motion.sync(motionFeed())
    }

    /** A Motion Controls change (menu row, Restore Defaults): port 0 and Cemu's GamePad Motion follow it live. */
    fun apply(values: Map<String, String>) {
        val was = motionOn ?: return
        val on = WiiuOptions.motionOn(values[WiiuOptions.MOTION], hasSensors)
        if (on == was) return
        motionOn = on
        // Enabled before the option is queued: Cemu's ask on turning on must find port 0 on.
        LibretroDroid.setSensorEnabled(0, on)
        // Before the first frame the view may not run yet: the first frame sends the row as it is then.
        if (host.gameReady) {
            host.applyOptions(WiiuOptions.motionOption(on).map { (k, v) -> Variable(k, v) })
            motion.sync(motionFeed())
        }
    }

    /** What the phone's sensors feed for this game: the GamePad while Motion Controls is on. */
    fun motionFeed(): PhoneMotion.Feed? = if (motionOn == true) PhoneMotion.Feed.GamePad else null
}
