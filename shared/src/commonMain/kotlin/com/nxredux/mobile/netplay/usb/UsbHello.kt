package com.nxredux.mobile.netplay.usb

/** Which end of the cable this device is: HOST is the USB host (it says HELLO), DEVICE answers with HELLO_ACK. */
enum class UsbSide { HOST, DEVICE }

/** What the caller must do after a [UsbHello] call. */
enum class UsbAct { SEND_HELLO, SEND_ACK, UP, DOWN, ERROR, DELIVER, SIO }

/**
 * The usblink handshake and keepalive for this end of one cable, ported from nx-redux
 * `workspace/all/usblink/usblink_link.c` (GPL-3.0) for a single port. Pure: the caller does the I/O the returned acts
 * name. The HOST side says HELLO every [HELLO_IDLE_MS] until linked, then every [HELLO_UP_MS]; the DEVICE side answers
 * every HELLO. Linked on the first right-version HELLO_ACK (host) or HELLO (device); down after [DEAD_MS] without a
 * frame; another version is a sticky error.
 */
class UsbHello(val side: UsbSide) {
    enum class Phase { IDLE, UP, ERROR }

    var phase = Phase.IDLE
        private set
    /** The other end's version after a mismatch; -1 otherwise. */
    var peerVersion = -1
        private set
    private var lastRx = 0L
    private var lastHello = 0L
    private var helloSent = false

    /** Time passing: [UsbAct.SEND_HELLO] on the host side's cadence, [UsbAct.DOWN] after [DEAD_MS] of silence. */
    fun tick(now: Long): Set<UsbAct> {
        if (phase == Phase.ERROR) return emptySet()
        if (phase == Phase.UP && now - lastRx >= DEAD_MS) {
            phase = Phase.IDLE
            return setOf(UsbAct.DOWN)
        }
        if (side != UsbSide.HOST) return emptySet()
        val cadence = if (phase == Phase.UP) HELLO_UP_MS else HELLO_IDLE_MS
        if (!helloSent || now - lastHello >= cadence) {
            helloSent = true
            lastHello = now
            return setOf(UsbAct.SEND_HELLO)
        }
        return emptySet()
    }

    /** One frame from the cable. */
    fun onFrame(type: Int, payload: ByteArray, now: Long): Set<UsbAct> = when {
        side == UsbSide.DEVICE && type == UsbFrame.HELLO ->
            // Always answered: the ACK carries our version, so the host can tell a mismatch.
            if (payload.isEmpty()) emptySet() else setOf(UsbAct.SEND_ACK) + control(payload[0], now)
        side == UsbSide.HOST && type == UsbFrame.HELLO_ACK ->
            if (payload.isEmpty()) emptySet() else control(payload[0], now)
        type == UsbFrame.DATA && phase == Phase.UP -> {
            lastRx = now
            setOf(UsbAct.DELIVER)
        }
        type == UsbFrame.SIO && phase == Phase.UP && payload.size in 1..UsbFrame.SIO_MAX -> {
            lastRx = now   // link-cable traffic keeps the link alive like IP does
            setOf(UsbAct.SIO)
        }
        else -> emptySet()
    }

    /** The cable is gone (a transfer failed for good): down if it was up. */
    fun onDetached(): Set<UsbAct> {
        if (phase != Phase.UP) return emptySet()
        phase = Phase.IDLE
        return setOf(UsbAct.DOWN)
    }

    private fun control(version: Byte, now: Long): Set<UsbAct> {
        val v = version.toInt() and 0xff
        if (v != VERSION) {
            phase = Phase.ERROR
            peerVersion = v
            return setOf(UsbAct.ERROR)
        }
        return when (phase) {
            Phase.IDLE -> {
                phase = Phase.UP
                lastRx = now
                setOf(UsbAct.UP)
            }
            Phase.UP -> {
                lastRx = now
                emptySet()
            }
            Phase.ERROR -> emptySet()
        }
    }

    companion object {
        /** nx-redux USBLINK_PROTO_VERSION. */
        const val VERSION = 2
        const val HELLO_IDLE_MS = 250L
        const val HELLO_UP_MS = 1000L
        const val DEAD_MS = 3000L
        const val HOST_IP = "10.99.0.1"
        const val DEVICE_IP = "10.99.0.2"
        /** The cable network's broadcast address (10.99.0.0/30): the PSP link announces there. */
        const val BROADCAST_IP = "10.99.0.3"

        fun localIp(side: UsbSide): String = if (side == UsbSide.HOST) HOST_IP else DEVICE_IP
        fun peerIp(side: UsbSide): String = if (side == UsbSide.HOST) DEVICE_IP else HOST_IP
    }
}
