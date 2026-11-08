package com.nxredux.mobile.storage

/** Unsigned integers and ASCII magics read from file headers. Callers check the bounds; a read past the end throws. */
object Bytes {
    fun le16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    fun le32(b: ByteArray, at: Int): Long = le(b, at, 4)

    fun le64(b: ByteArray, at: Int): Long = le(b, at, 8)

    fun be32(b: ByteArray, at: Int): Long = be(b, at, 4)

    fun be64(b: ByteArray, at: Int): Long = be(b, at, 8)

    /** True when [b] holds [text]'s bytes at [at]; false when it is too short. */
    fun startsWithAscii(b: ByteArray, text: String, at: Int = 0): Boolean {
        val t = text.encodeToByteArray()
        return at >= 0 && b.size >= at + t.size && t.indices.all { b[at + it] == t[it] }
    }

    private fun le(b: ByteArray, at: Int, n: Int): Long = (0 until n).fold(0L) { v, i -> v or ((b[at + i].toLong() and 0xFF) shl (8 * i)) }

    private fun be(b: ByteArray, at: Int, n: Int): Long = (0 until n).fold(0L) { v, i -> (v shl 8) or (b[at + i].toLong() and 0xFF) }
}
