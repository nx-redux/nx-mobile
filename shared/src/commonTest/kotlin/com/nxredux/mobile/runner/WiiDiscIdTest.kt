package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WiiDiscIdTest {
    private fun reader(b: ByteArray): (Long, Int) -> ByteArray? = { off, len ->
        if (off < 0 || off >= b.size) null else b.copyOfRange(off.toInt(), minOf(b.size, off.toInt() + len))
    }
    private val noInflate: (ByteArray, Int) -> ByteArray? = { _, _ -> null }
    private fun at(size: Int, vararg parts: Pair<Int, ByteArray>) = ByteArray(size).also { b -> parts.forEach { (o, p) -> p.copyInto(b, o) } }
    private val id = "SMNE01".encodeToByteArray()

    @Test fun iso() = assertEquals("SMNE01", WiiDiscId.read("iso", reader(at(0x100, 0 to id)), noInflate)?.id)

    @Test fun wbfsReadsAfterItsHeaderSector() {
        val img = at(0x400, 0 to "WBFS".encodeToByteArray(), 8 to byteArrayOf(9), 0x200 to id)   // 1 shl 9 = 0x200
        assertEquals("SMNE01", WiiDiscId.read("wbfs", reader(img), noInflate)?.id)
    }

    @Test fun wiaAndRvzReadTheStoredDiscHeader() {
        assertEquals("SMNE01", WiiDiscId.read("rvz", reader(at(0x100, 0 to "RVZ\u0001".encodeToByteArray(), 0x58 to id)), noInflate)?.id)
        assertEquals("SMNE01", WiiDiscId.read("wia", reader(at(0x100, 0 to "WIA\u0001".encodeToByteArray(), 0x58 to id)), noInflate)?.id)
    }

    @Test fun cisoReadsAfterItsMap() {
        assertEquals("SMNE01", WiiDiscId.read("ciso", reader(at(0x8100, 0 to "CISO".encodeToByteArray(), 0x8000 to id)), noInflate)?.id)
    }

    @Test fun gczInflatesTheFirstBlock() {
        // header 0x20: magic B10BC001 LE, subtype, compressed size u64, data size u64, block size u32, num blocks u32 (=1)
        val head = at(0x20 + 12 + 4,
            0 to byteArrayOf(0x01, 0xC0.toByte(), 0x0B, 0xB1.toByte()),
            24 to byteArrayOf(0x00, 0x40, 0, 0),   // block size 0x4000
            28 to byteArrayOf(1, 0, 0, 0),         // one block
            // pointer[0] = 0 (compressed, at the data start), hash[0] = 0, then 4 payload bytes "ZZZZ"
            0x20 + 12 to "ZZZZ".encodeToByteArray(),
        )
        var seen: ByteArray? = null
        val inflate: (ByteArray, Int) -> ByteArray? = { input, _ -> seen = input; id }
        assertEquals("SMNE01", WiiDiscId.read("gcz", reader(head), inflate)?.id)
        assertTrue(seen!!.decodeToString().startsWith("ZZZZ"))
    }

    @Test fun wrongMagicTruncatedOrInvalidIdsAreNull() {
        assertNull(WiiDiscId.read("rvz", reader(at(0x100, 0x58 to id)), noInflate)?.id)            // no magic
        assertNull(WiiDiscId.read("wbfs", reader(at(0x20, 0 to "WBFS".encodeToByteArray(), 8 to byteArrayOf(9))), noInflate)?.id)
        assertNull(WiiDiscId.read("iso", reader(at(0x10, 0 to "sm e01".encodeToByteArray())), noInflate)?.id)
        assertNull(WiiDiscId.read("wbfs", reader(at(0x40, 0 to "WBFS".encodeToByteArray(), 8 to byteArrayOf(70))), noInflate)?.id)   // absurd shift
        assertNull(WiiDiscId.read("chd", reader(at(0x100, 0 to id)), noInflate)?.id)
    }

    private val wiiMagic = byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte())   // big-endian, at +0x18
    private val gcMagic = byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D)    // big-endian, at +0x1C

    @Test fun theGameCubeMagicMarksAGameCubeDisc() {
        assertEquals(WiiDisc("GALE01", gameCube = true),
            WiiDiscId.read("iso", reader(at(0x100, 0 to "GALE01".encodeToByteArray(), 0x1C to gcMagic)), noInflate))
        // the header copy in a container format, too
        assertEquals(WiiDisc("GALE01", gameCube = true),
            WiiDiscId.read("rvz", reader(at(0x100, 0 to "RVZ\u0001".encodeToByteArray(), 0x58 to "GALE01".encodeToByteArray(), 0x58 + 0x1C to gcMagic)), noInflate))
    }

    @Test fun theWiiMagicMarksAWiiDisc() {
        assertEquals(WiiDisc("SMNE01", gameCube = false), WiiDiscId.read("iso", reader(at(0x100, 0 to id, 0x18 to wiiMagic)), noInflate))
    }

    // Neither magic (homebrew, an odd dump) or a header cut short: Wii.
    @Test fun noMagicOrAShortHeaderCountsAsWii() {
        assertEquals(WiiDisc("SMNE01", gameCube = false), WiiDiscId.read("iso", reader(at(0x100, 0 to id)), noInflate))
        assertEquals(WiiDisc("GALE01", gameCube = false), WiiDiscId.read("iso", reader(at(0x1C, 0 to "GALE01".encodeToByteArray())), noInflate))
    }

    @Test fun gczReadsThePlatformFromTheInflatedHeader() {
        val head = at(0x20 + 12 + 0x20,
            0 to byteArrayOf(0x01, 0xC0.toByte(), 0x0B, 0xB1.toByte()),
            24 to byteArrayOf(0x00, 0x40, 0, 0),
            28 to byteArrayOf(1, 0, 0, 0),
        )
        val header = at(0x20, 0 to "GALE01".encodeToByteArray(), 0x1C to gcMagic)
        var wanted = 0
        val inflate: (ByteArray, Int) -> ByteArray? = { _, want -> wanted = want; header }
        assertEquals(WiiDisc("GALE01", gameCube = true), WiiDiscId.read("gcz", reader(head), inflate))
        assertEquals(0x20, wanted)
    }

    @Test fun readableExtensions() {
        assertTrue(WiiDiscId.readable("Game (USA).WBFS"))
        assertEquals(setOf("iso", "wbfs", "wia", "rvz", "ciso", "gcz"), WiiDiscId.READABLE)
    }
}
