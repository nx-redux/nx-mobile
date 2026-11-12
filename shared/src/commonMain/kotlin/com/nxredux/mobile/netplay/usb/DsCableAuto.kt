package com.nxredux.mobile.netplay.usb

/** What this phone's USB port shows of another phone before any link. [MAYBE]: something hosts this phone, a phone or a computer. [SEEN]: a phone the cable can open now. */
enum class UsbPeer { NONE, MAYBE, SEEN }

/** The cable as a game asks about it. [REFUSED]: it cannot link however often it is tried (denied, another VPN, a version apart). */
enum class UsbAutoLink { IDLE, LINKING, UP, REFUSED }

data class UsbAutoStatus(val peer: UsbPeer, val link: UsbAutoLink)

/**
 * A DS game on the automatic route with a phone on its USB port: when the cable is started for it, and what the room
 * search is told ([Cable], the native ndl_cable). Two phones on a cable meet over it; any more meet on the Wi-Fi.
 *
 * The search is told of a phone on the port before the game's wireless comes on, so the first search already waits for
 * the cable; the cable itself starts with the wireless. Only a phone in sight counts: something that merely hosts this
 * phone may be a computer, and the phone that sees the other switches it into sight.
 *
 * Pure: the host asks the cable's owner for [UsbAutoStatus] every second or so and feeds [step].
 */
class DsCableAuto {
    enum class Cable(val code: Int) { NONE(0), COMING(1), UP(2) }

    /** [start]: start the cable now (the host asks Android's consent first, and tells [refuse] when it is not given). */
    data class Step(val cable: Cable, val start: Boolean)

    private var refused = false
    private var active = false
    private var nextStartMs = 0L

    /** The player said no to Android's consent: not asked again in this game. */
    fun refuse() { refused = true }

    /** [wirelessOn]: the game's wireless is on (looking for a room, or in one). */
    fun step(status: UsbAutoStatus, wirelessOn: Boolean, nowMs: Long): Step {
        when (status.link) {
            UsbAutoLink.UP -> { active = true; return Step(Cable.UP, false) }
            UsbAutoLink.REFUSED -> { refused = true; active = false }
            UsbAutoLink.LINKING -> active = true
            UsbAutoLink.IDLE -> if (active) { active = false; nextStartMs = nowMs + RETRY_MS }
        }
        val seen = status.peer == UsbPeer.SEEN && !refused
        val start = seen && wirelessOn && status.link == UsbAutoLink.IDLE && nowMs >= nextStartMs
        if (start) nextStartMs = nowMs + RETRY_MS
        return Step(if (seen) Cable.COMING else Cable.NONE, start)
    }

    companion object {
        /** The pause before a link that ended (or a start that did not take) is tried again. */
        const val RETRY_MS = 5_000L
    }
}
