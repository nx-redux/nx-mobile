package com.nxredux.mobile.runner

/** What the DS / 3DS core hears. [native] matches `LibretroDroid.setMicrophoneSource`. */
enum class MicSource(val native: Int) { SILENCE(0), LIVE(1), NOISE(2) }

/**
 * Off is silence; on is the phone mic when the device [usable] (mic hardware, not a TV) and the permission is
 * granted, else noise so games that wait for a blow or shout still move on.
 */
fun micSource(on: Boolean, usable: Boolean, granted: Boolean): MicSource = when {
    !on -> MicSource.SILENCE
    usable && granted -> MicSource.LIVE
    else -> MicSource.NOISE
}

/**
 * The session's Mic toggle. Starts off, never persisted. Turning it on asks for the permission once ([setOn] says when
 * the host should ask now); a denial leaves it on with noise, and a later grant flips it to live. Netplay turns it off
 * and ignores it, and hides it ([shown]). The host owns one per game launch and feeds [setGranted] / [setAsked] from
 * the system and settings.
 */
class MicControl(private val usable: Boolean) {
    private var granted = false
    private var asked = false
    private var netplay = false

    var on = false
        private set

    val source get() = micSource(on, usable, granted)

    val shown get() = !netplay

    /** True when the host should ask for the permission now. */
    fun setOn(on: Boolean): Boolean {
        if (netplay) return false
        this.on = on
        return on && usable && !granted && !asked
    }

    fun setGranted(granted: Boolean) { this.granted = granted }

    fun setAsked(asked: Boolean) { this.asked = asked }

    fun setSession(netplay: Boolean) {
        this.netplay = netplay
        if (netplay) on = false
    }
}
