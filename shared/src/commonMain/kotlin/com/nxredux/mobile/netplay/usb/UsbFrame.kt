package com.nxredux.mobile.netplay.usb

import com.nxredux.mobile.storage.Bytes

/**
 * The NX Redux usblink frame, ported from nx-redux `workspace/all/usblink/usblink_frame.c` (GPL-3.0):
 * `[len lo][len hi][type][payload][pad?]`, one frame per USB bulk transfer. One zero pad byte follows whenever the framed
 * size is a multiple of 64 (which covers 512), so every frame ends in a short packet on full and high speed.
 */
object UsbFrame {
    const val MAX = 2048
    const val HDR = 3
    const val PAYLOAD_MAX = MAX - HDR - 1
    /** The largest SIO payload: its frame, pad included, fits one 512-byte high-speed packet. */
    const val SIO_MAX = 512 - HDR - 1

    const val DATA = 1        // one IPv4 packet
    const val HELLO = 2       // USB-host side -> device side, payload: link version
    const val HELLO_ACK = 3   // device side -> USB-host side, payload: link version
    const val CONFIRM = 4     // siolink only, never an outer frame
    const val SIO = 5         // one siolink message (the GBA link cable)

    /** One decoded frame: its [type] and a copy of its [payload]. */
    class Frame(val type: Int, val payload: ByteArray)

    /** The framed size of a [len]-byte payload, pad included. */
    fun framedSize(len: Int): Int = (len + HDR).let { if (it % 64 == 0) it + 1 else it }

    /** [len] bytes of [payload] from [offset] framed as [type]; null when [len] is outside 0..[PAYLOAD_MAX]. */
    fun encode(type: Int, payload: ByteArray = ByteArray(0), offset: Int = 0, len: Int = payload.size - offset): ByteArray? {
        if (len < 0 || len > PAYLOAD_MAX) return null
        val out = ByteArray(framedSize(len))   // a pad byte, when there is one, stays 0
        out[0] = (len and 0xff).toByte()
        out[1] = (len shr 8).toByte()
        out[2] = type.toByte()
        payload.copyInto(out, HDR, offset, offset + len)
        return out
    }

    /**
     * The frame in the first [n] bytes of [buf], or null: it must hold exactly the payload plus 0 or 1 trailing byte, and
     * a type 1..5 (the handheld's decoder).
     */
    fun decode(buf: ByteArray, n: Int): Frame? {
        if (n < HDR) return null
        val len = Bytes.le16(buf, 0)
        val extra = n - HDR - len
        if (len > PAYLOAD_MAX || extra < 0 || extra > 1) return null
        val type = buf[2].toInt() and 0xff
        if (type < DATA || type > SIO) return null
        return Frame(type, buf.copyOfRange(HDR, HDR + len))
    }
}

/**
 * Frames from a byte stream (an Android Open Accessory link, whose reads do not follow transfer edges): the 2-byte length
 * says how many bytes the frame takes (pad rule included), and a frame is returned once all of them arrived. A header that
 * cannot start a frame (length over [UsbFrame.PAYLOAD_MAX], type outside 1..5) makes the stream [broken] for good: it has
 * lost its place, and the link must be dropped.
 */
class UsbFrameStream {
    private var buf = ByteArray(UsbFrame.MAX * 2)
    private var have = 0

    var broken = false
        private set

    /** Appends [n] bytes of [bytes]; every frame now complete, in order. */
    fun feed(bytes: ByteArray, n: Int = bytes.size): List<UsbFrame.Frame> {
        if (broken) return emptyList()
        if (have + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, have + n))
        bytes.copyInto(buf, have, 0, n)
        have += n
        val out = ArrayList<UsbFrame.Frame>()
        var at = 0
        while (have - at >= UsbFrame.HDR) {
            val len = Bytes.le16(buf, at)
            val type = buf[at + 2].toInt() and 0xff
            if (len > UsbFrame.PAYLOAD_MAX || type < UsbFrame.DATA || type > UsbFrame.SIO) {
                broken = true
                have = 0
                return out
            }
            val size = UsbFrame.framedSize(len)
            if (have - at < size) break
            out += UsbFrame.Frame(type, buf.copyOfRange(at + UsbFrame.HDR, at + UsbFrame.HDR + len))
            at += size
        }
        if (at > 0) {
            buf.copyInto(buf, 0, at, have)
            have -= at
        }
        return out
    }
}
