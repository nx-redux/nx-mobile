package com.nxredux.mobile.netplay.usb

import kotlinx.coroutines.flow.StateFlow

/**
 * How the Netplay sheet reaches the other device (its Connection row). [CABLE_LINK_WIFI]: Cable Link over Wi-Fi, GBA only
 * (each phone runs both players' GBAs joined by mGBA's cable); it runs over Wi-Fi, never the USB cable.
 */
enum class NetplayLink { WIFI, USB, CABLE_LINK_WIFI }

/** Why the cable could not link. [PEER_HOST]: none found, and this phone is the USB device on its port. */
enum class UsbFail { NO_LINK, PEER_HOST, VERSION, VPN_BLOCKED, UNSUPPORTED, DENIED }

/** The cable's state as the sheet sees it. */
sealed interface UsbLinkState {
    data object Off : UsbLinkState
    /** Android's one-time VPN consent dialog is up; the 30 s link wait has not started. */
    data object Consent : UsbLinkState
    /** Android's USB permission dialog is up; the 30 s link wait pauses. */
    data object Permission : UsbLinkState
    /** Looking for the other end or saying HELLO; [handheld]: an NX Redux handheld's nxlink interface is attached. */
    data class Starting(val handheld: Boolean) : UsbLinkState
    /** Linked: this device is [localIp], the other end [peerIp] (10.99.0.1 / 10.99.0.2). */
    data class Up(val localIp: String, val peerIp: String) : UsbLinkState
    /**
     * It was up and did not come back within 28 s (cable pulled for longer, the other end stopped, the VPN taken away); stays
     * so until [UsbCable.stop]. A brief drop that comes back in time never shows: the state stays [Up].
     */
    data object Lost : UsbLinkState
    data class Failed(val reason: UsbFail, val handheld: Boolean = false) : UsbLinkState
}

/** The USB-C cable link, one per process, implemented per platform. */
interface UsbCable {
    /** USB Cable can be offered now: USB host support, or an NX Redux accessory is attached. */
    val available: Boolean
    val state: StateFlow<UsbLinkState>
    /**
     * Counts every drop of an Up link (the other end detached or went silent), the brief ones [state] rides out as Up
     * included: Device Sync fails on the first, since its rsync would not notice (the VPN stays and swallows its packets).
     */
    val drops: StateFlow<Int>
    /**
     * Starts the link; before it returns, [state] is [UsbLinkState.Consent], [UsbLinkState.Starting] or [UsbLinkState.Failed]
     * (it may pass through [UsbLinkState.Consent] or [UsbLinkState.Permission] again later). Idempotent while still linking;
     * a [UsbLinkState.Lost] or [UsbLinkState.Up] left over from an earlier session is stopped and started fresh.
     */
    fun start()
    /** Stops the link and its VPN; [state] goes [UsbLinkState.Off]. Idempotent. */
    fun stop()
}
