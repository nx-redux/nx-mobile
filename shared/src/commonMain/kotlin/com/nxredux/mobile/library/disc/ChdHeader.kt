package com.nxredux.mobile.library.disc

import com.nxredux.mobile.storage.Bytes

/** What a CHD holds, by its metadata: a GD-ROM (Dreamcast), a DVD (PSP here), a CD, or something else. */
enum class ChdMedia { GD, DVD, CD, OTHER }

/**
 * A CHD v5 header (`MComprHD`, big-endian; libchdr's chd.h) and the metadata entries chained from its `metaoffset`:
 * tag (4), flags (1), length (3), next (8), then the data. Only entries inside the bytes given are read.
 */
object ChdHeader {
    fun media(head: ByteArray): ChdMedia? {
        if (head.size < 124 || !Bytes.startsWithAscii(head, "MComprHD") || Bytes.be32(head, 12) != 5L) return null
        var at = Bytes.be64(head, 48)
        var found = ChdMedia.OTHER
        val seen = HashSet<Long>()
        while (at > 0 && at + 16 <= head.size && seen.add(at)) {
            when (head.copyOfRange(at.toInt(), at.toInt() + 4).decodeToString()) {
                "CHGD", "CHGT" -> return ChdMedia.GD
                "DVD " -> return ChdMedia.DVD
                "CHT2", "CHTR", "CHCD" -> found = ChdMedia.CD
            }
            at = Bytes.be64(head, at.toInt() + 8)
        }
        return found
    }
}
