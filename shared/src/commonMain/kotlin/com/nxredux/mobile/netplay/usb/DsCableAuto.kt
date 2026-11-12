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
 * Pure: the host asks the cable's owner for [UsbAutoStatus] every second or so and feeds [step].
 */
class DsCableAuto {
    enum class Cable(val code: Int) { NONE(0), COMING(1), UP(2) }

    /** [start]: start the cable now (the host asks Android's consent first, and tells [refuse] when it is not given). */
    data class Step(val cable: Cable, val start: Boolean)

    private var refused = false
    private var linking = false
    private var nextStartMs = 0L

    /** The player said no to Android's consent: not asked again in this game. */
    fun refuse() { refused = true }

    fun step(status: UsbAutoStatus, nowMs: Long): Step {
        when (status.link) {
            UsbAutoLink.UP -> { linking = false; return Step(Cable.UP, false) }
            UsbAutoLink.REFUSED -> { refused = true; linking = false }
            // Only a phone in sight is worth holding a Wi-Fi room back for: a computer on the port never links.
            UsbAutoLink.LINKING -> { linking = true; return Step(if (status.peer == UsbPeer.SEEN) Cable.COMING else Cable.NONE, false) }
            UsbAutoLink.IDLE -> if (linking) { linking = false; nextStartMs = nowMs + RETRY_MS }
        }
        val start = !refused && status.peer != UsbPeer.NONE && nowMs >= nextStartMs
        if (start) nextStartMs = nowMs + RETRY_MS
        return Step(Cable.NONE, start)
    }

    companion object {
        /** The pause before a link that ended (or a start that did not take) is tried again. */
        const val RETRY_MS = 5_000L
    }
}
