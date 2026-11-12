package com.nxredux.mobile.netplay.usb

/**
 * What one search for the other end of the cable looks for. The first bring-up ([reattach] null) tries everything for
 * [LINK_WAIT_MS]: this phone as the accessory, a handheld's nxlink or a phone already in accessory mode, else one AOA
 * switch, posting Permission / Failed as it goes. A reattach after a brief drop looks for the same side only, for
 * [REATTACH_MS], with no AOA switch and no state posts (the sheet and the game keep reading Up), skipping the [lost]
 * device. [firstWaitMs] is the first bring-up's wait: a game's own start waits far longer than the sheet's.
 */
class UsbSearch(val reattach: UsbSide?, val lost: String? = null, private val firstWaitMs: Long = LINK_WAIT_MS) {
    /** This phone as our accessory (the DEVICE side). */
    val accessory: Boolean get() = reattach == null || reattach == UsbSide.DEVICE
    /** A handheld's nxlink interface, or a phone already in accessory mode (the HOST side). */
    val devices: Boolean get() = reattach == null || reattach == UsbSide.HOST
    /** One AOA switch of a phone-like device: first bring-up only. */
    val aoaSwitch: Boolean get() = reattach == null
    /** Permission and Starting / Failed posts: first bring-up only. */
    val posts: Boolean get() = reattach == null
    /** The wait, restarted when Android's permission dialog is answered. */
    val windowMs: Long get() = if (reattach == null) firstWaitMs else REATTACH_MS

    companion object {
        const val LINK_WAIT_MS = 30_000L
        /** A game's own start: the other phone starts when its game's wireless comes on, which can be minutes later. */
        const val AUTO_WAIT_MS = 600_000L
        const val REATTACH_MS = 28_000L   // nearly the lockstep session's 30 s stall countdown: the whole countdown is replug time
    }
}
