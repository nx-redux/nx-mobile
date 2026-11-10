package com.nxredux.mobile.netplay.cablelink

/** A streaming SHA-1, so a 32 MiB ROM is hashed chunk by chunk and never held whole. */
class Sha1 {
    private val h = intArrayOf(0x67452301, 0xEFCDAB89.toInt(), 0x98BADCFE.toInt(), 0x10325476, 0xC3D2E1F0.toInt())
    private val block = ByteArray(64)
    private var filled = 0
    private var total = 0L
    private val w = IntArray(80)

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        var i = offset
        val end = offset + length
        total += length
        while (i < end) {
            val n = minOf(64 - filled, end - i)
            bytes.copyInto(block, filled, i, i + n)
            filled += n; i += n
            if (filled == 64) { compress(); filled = 0 }
        }
    }

    /** The digest as 40 lowercase hex digits; the hash is spent afterwards. */
    fun hex(): String {
        val bits = total * 8
        update(byteArrayOf(0x80.toByte()))
        while (filled != 56) update(byteArrayOf(0))
        val len = ByteArray(8) { (bits ushr (56 - 8 * it)).toByte() }
        update(len)
        return buildString(40) { h.forEach { word -> for (s in 28 downTo 0 step 4) append(HEX[(word ushr s) and 15]) } }
    }

    private fun compress() {
        for (t in 0 until 16) {
            w[t] = ((block[4 * t].toInt() and 0xFF) shl 24) or ((block[4 * t + 1].toInt() and 0xFF) shl 16) or
                ((block[4 * t + 2].toInt() and 0xFF) shl 8) or (block[4 * t + 3].toInt() and 0xFF)
        }
        for (t in 16 until 80) w[t] = (w[t - 3] xor w[t - 8] xor w[t - 14] xor w[t - 16]).let { (it shl 1) or (it ushr 31) }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]
        for (t in 0 until 80) {
            val f: Int
            val k: Int
            when {
                t < 20 -> { f = (b and c) or (b.inv() and d); k = 0x5A827999 }
                t < 40 -> { f = b xor c xor d; k = 0x6ED9EBA1 }
                t < 60 -> { f = (b and c) or (b and d) or (c and d); k = 0x8F1BBCDC.toInt() }
                else -> { f = b xor c xor d; k = 0xCA62C1D6.toInt() }
            }
            val tmp = ((a shl 5) or (a ushr 27)) + f + e + k + w[t]
            e = d; d = c; c = (b shl 30) or (b ushr 2); b = a; a = tmp
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e
    }

    private companion object { const val HEX = "0123456789abcdef" }
}

/** The ROM identity both phones compare during the Cable Link handshake. */
object RomHash {
    /** [bytes] hashed in order, one chunk at a time. */
    fun sha1Hex(bytes: Sequence<ByteArray>): String {
        val sha = Sha1()
        for (chunk in bytes) sha.update(chunk)
        return sha.hex()
    }

    /** True for a hash as [sha1Hex] writes it; anything from the wire is checked with this before it names a file. */
    fun isHash(text: String): Boolean = text.length == 40 && text.all { it in '0'..'9' || it in 'a'..'f' }
}
