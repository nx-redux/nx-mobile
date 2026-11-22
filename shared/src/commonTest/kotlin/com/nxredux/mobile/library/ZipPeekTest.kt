package com.nxredux.mobile.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZipPeekTest {
    /** A local file header (30 bytes) + name; the rest of the archive is irrelevant to the peek. */

    @Test fun reads_the_first_local_header_name() {
        assertEquals("Mario.gba", ZipPeek.firstEntryName(zipHead("Mario.gba")))
        assertEquals("Sub/Contra.nes", ZipPeek.firstEntryName(zipHead("Sub/Contra.nes", extraLen = 12) + byteArrayOf(9, 9)))
    }
    @Test fun rejects_non_zips_truncated_heads_and_directories() {
        assertNull(ZipPeek.firstEntryName(byteArrayOf(1, 2, 3)))
        assertNull(ZipPeek.firstEntryName("not a zip at all, but long enough to be a header".encodeToByteArray()))
        assertNull(ZipPeek.firstEntryName(zipHead("Mario.gba").copyOf(35)))   // name cut short
        assertNull(ZipPeek.firstEntryName(zipHead("folder/")))                 // first entry is a directory
        assertNull(ZipPeek.firstEntryName(ByteArray(0)))
    }
    @Test fun a_readme_first_is_returned_as_is_so_the_caller_can_reject_it() {
        assertEquals("readme.txt", ZipPeek.firstEntryName(zipHead("readme.txt")))
    }
}

/** A zip's first local file header naming [name], with [extraLen] zero bytes of extra field. */
internal fun zipHead(name: String, extraLen: Int = 0): ByteArray {
    val n = name.encodeToByteArray()
    val h = ByteArray(30 + n.size + extraLen)
    h[0] = 0x50; h[1] = 0x4B; h[2] = 0x03; h[3] = 0x04
    h[26] = (n.size and 0xFF).toByte(); h[27] = (n.size shr 8).toByte()
    h[28] = (extraLen and 0xFF).toByte(); h[29] = (extraLen shr 8).toByte()
    n.copyInto(h, 30)
    return h
}
