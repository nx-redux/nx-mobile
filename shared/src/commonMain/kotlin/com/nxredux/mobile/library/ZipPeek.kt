package com.nxredux.mobile.library

import com.nxredux.mobile.storage.Bytes

/**
 * The name of a zip's first entry, from the leading bytes of the file: local file header signature
 * 50 4B 03 04, name length at offset 26 (LE u16), extra length at 28, name at 30. Enough to tell what a
 * ROM zip holds without reading the archive. Names are UTF-8 or CP437 in practice; UTF-8 decoding is
 * good enough for an extension check.
 */
object ZipPeek {
    private const val HEADER = 30
    const val HEAD_BYTES = HEADER + 1024

    fun firstEntryName(head: ByteArray): String? {
        if (head.size < HEADER) return null
        if (!Bytes.startsWithAscii(head, "PK\u0003\u0004")) return null
        val nameLen = Bytes.le16(head, 26)
        if (nameLen == 0 || head.size < HEADER + nameLen) return null
        val name = head.copyOfRange(HEADER, HEADER + nameLen).decodeToString()
        return if (name.endsWith("/")) null else name
    }
}
