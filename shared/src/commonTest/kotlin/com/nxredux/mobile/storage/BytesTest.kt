package com.nxredux.mobile.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BytesTest {
    private val b = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0xF8.toByte())

    @Test fun littleEndian() {
        assertEquals(0x0302, Bytes.le16(b, 1))
        assertEquals(0x04030201L, Bytes.le32(b, 0))
        assertEquals(0xF8070605L, Bytes.le32(b, 4))   // unsigned
        assertEquals(0xF807060504030201uL.toLong(), Bytes.le64(b, 0))
    }

    @Test fun bigEndian() {
        assertEquals(0x01020304L, Bytes.be32(b, 0))
        assertEquals(0x050607F8L, Bytes.be32(b, 4))
        assertEquals(0x01020304050607F8L, Bytes.be64(b, 0))
    }

    @Test fun asciiMagic() {
        val head = "xxPK\u0003\u0004".encodeToByteArray()
        assertTrue(Bytes.startsWithAscii(head, "PK\u0003\u0004", at = 2))
        assertFalse(Bytes.startsWithAscii(head, "PK", at = 0))
        assertFalse(Bytes.startsWithAscii(head, "PK\u0003\u0004\u0000", at = 2))   // too short
        assertTrue(Bytes.startsWithAscii(head, ""))
    }
}
