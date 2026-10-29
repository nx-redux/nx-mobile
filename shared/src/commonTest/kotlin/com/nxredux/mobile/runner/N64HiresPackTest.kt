package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class N64HiresPackTest {
    /** A .z64 header with [name] padded to 20 bytes with [pad]. */
    private fun z64(name: String, pad: Char = ' '): ByteArray {
        val head = ByteArray(N64HiresPack.HEADER_BYTES)
        byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40).copyInto(head)
        name.padEnd(20, pad).encodeToByteArray().copyInto(head, 0x20)
        return head
    }

    private fun swap(head: ByteArray, mask: Int) = ByteArray(head.size) { head[it xor mask] }

    @Test fun readsTheNameInEveryDumpOrder() {
        val head = z64("MARIOKART64")
        assertEquals("MARIOKART64", N64HiresPack.headerName(head))
        assertEquals("MARIOKART64", N64HiresPack.headerName(swap(head, 1)))   // .v64
        assertEquals("MARIOKART64", N64HiresPack.headerName(swap(head, 3)))   // .n64
    }

    @Test fun keepsInnerSpacesAndDropsTrailingOnes() {
        assertEquals("THE LEGEND OF ZELDA", N64HiresPack.headerName(z64("THE LEGEND OF ZELDA")))
        assertEquals("SUPER MARIO 64", N64HiresPack.headerName(z64("SUPER MARIO 64")))
    }

    @Test fun stopsAtTheFirstNul() {
        assertEquals("F-ZERO X", N64HiresPack.headerName(z64("F-ZERO X", pad = '\u0000')))
    }

    @Test fun replacesColonAndSlashAsGlideN64Does() {
        assertEquals("STAR WARS- EP1", N64HiresPack.headerName(z64("STAR WARS: EP1")))
        assertEquals("A-B", N64HiresPack.headerName(z64("A/B")))
    }

    @Test fun rejectsAnythingElse() {
        assertNull(N64HiresPack.headerName(ByteArray(0x40)))
        assertNull(N64HiresPack.headerName(z64("MARIOKART64").copyOf(0x30)))
        assertNull(N64HiresPack.headerName(z64("")))
        val sjis = z64("X").also { it[0x21] = 0x82.toByte() }
        assertNull(N64HiresPack.headerName(sjis))
    }

    @Test fun fileNameIsGlideN64sCacheName() {
        assertEquals("MARIOKART64_HIRESTEXTURES.hts", N64HiresPack.fileName("MARIOKART64"))
    }

    @Test fun autoVramLimitFollowsDeviceMemory() {
        val gib = 1024L * 1024 * 1024
        assertEquals("200", N64HiresPack.vramLimit("auto", 1_932_548L * 1024))   // the AYANEO's 2 GB
        assertEquals("200", N64HiresPack.vramLimit(null, 3 * gib))
        assertEquals("500", N64HiresPack.vramLimit("auto", 4 * gib))
        assertEquals("500", N64HiresPack.vramLimit("auto", 6 * gib))
        assertEquals("0", N64HiresPack.vramLimit("auto", 12 * gib))
    }

    @Test fun pickedVramLimitIsKept() {
        assertEquals("1000", N64HiresPack.vramLimit("1000", 2 * 1024L * 1024 * 1024))
        assertEquals("0", N64HiresPack.vramLimit("none", 2 * 1024L * 1024 * 1024))
    }

    @Test fun wantedOnlyWithHighResOnAndGles() {
        val on = mapOf(N64HiresPack.ENABLE_KEY to "True")
        assertTrue(N64HiresPack.wanted(on, onVulkan = false))
        assertFalse(N64HiresPack.wanted(on, onVulkan = true))
        assertFalse(N64HiresPack.wanted(mapOf(N64HiresPack.ENABLE_KEY to "False"), onVulkan = false))
        assertFalse(N64HiresPack.wanted(emptyMap(), onVulkan = false))
    }
}
