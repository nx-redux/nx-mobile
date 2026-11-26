package com.nxredux.mobile.runner

import com.nxredux.mobile.options.WiiuOptions
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.Variable

/**
 * Motion Controls for a Wii U game: the phone's sensors ([motion]) as the GamePad's, through Cemu's GamePad Motion
 * ([WiiuOptions.CORE_MOTION]). Cemu asks for both sensors only when that option changes to on, and libretrodroid grants
 * them only while they are offered and port 0 is enabled. A previous core's unload can reset that native state while
 * this game loads, so the core starts with the option off ([CoreRegistry]'s default) and the first frame offers the
 * sensors, then turns it on: the ask comes strictly after the offer. Main thread.
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

    /**
     * The first frame: the sensors offered and port 0 set, then Cemu's GamePad Motion from the row as it is now (for On
     * a change from the launch's off, so Cemu asks on its next options read), and the sensors listening.
     */
    fun onFirstFrame() {
        val on = motionOn ?: return
        motion.offer()
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
