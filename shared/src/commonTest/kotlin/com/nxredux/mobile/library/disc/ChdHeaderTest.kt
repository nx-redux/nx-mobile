package com.nxredux.mobile.library.disc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A version 5 CHD header followed by one metadata entry per tag in [meta]. */
internal fun chdBytes(vararg meta: String): ByteArray {
    val out = ByteArray(124 + meta.size * 32)
    "MComprHD".encodeToByteArray().copyInto(out, 0)
    out[15] = 5                                                   // version 5, big-endian u32 at 12
    out[55] = 124                                                 // metaoffset = 124, big-endian u64 at 48
    meta.forEachIndexed { i, tag ->
        val at = 124 + i * 32
        tag.encodeToByteArray().copyInto(out, at)
        out[at + 7] = 8                                           // length 8
        if (i < meta.lastIndex) out[at + 15] = (at + 32).toByte() // next
    }
    return out
}

class ChdHeaderTest {
    @Test fun media_from_the_metadata() {
        assertEquals(ChdMedia.GD, ChdHeader.media(chdBytes("CHGD")))
        assertEquals(ChdMedia.DVD, ChdHeader.media(chdBytes("DVD ")))
        assertEquals(ChdMedia.CD, ChdHeader.media(chdBytes("CHT2", "CHT2")))
        assertNull(ChdHeader.media(ByteArray(124)))                    // not a CHD
    }
}
