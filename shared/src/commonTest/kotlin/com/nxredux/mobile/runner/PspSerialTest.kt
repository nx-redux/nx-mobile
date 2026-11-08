package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PspSerialTest {
    private val sector = 2048

    private fun le32(b: ByteArray, at: Int, v: Int) { for (i in 0..3) b[at + i] = (v ushr (8 * i)).toByte() }
    private fun le16(b: ByteArray, at: Int, v: Int) { for (i in 0..1) b[at + i] = (v ushr (8 * i)).toByte() }

    /** One directory record (ISO 9660 §9.1) at [at] in [b]; returns its length. */
    private fun record(b: ByteArray, at: Int, name: String, lba: Int, size: Int): Int {
        val len = 33 + name.length + (if (name.length % 2 == 0) 1 else 0)
        b[at] = len.toByte()
        le32(b, at + 2, lba)
        le32(b, at + 10, size)
        b[at + 32] = name.length.toByte()
        name.encodeToByteArray().copyInto(b, at + 33)
        return len
    }

    private fun sfo(discId: String): ByteArray {
        val keys = "DISC_ID\u0000TITLE\u0000".encodeToByteArray()
        val keyTable = 20 + 2 * 16
        val dataTable = keyTable + keys.size + 2
        val b = ByteArray(dataTable + 32)
        b[0] = 0; "PSF".encodeToByteArray().copyInto(b, 1)
        le32(b, 8, keyTable); le32(b, 12, dataTable); le32(b, 16, 2)
        // TITLE first, so the lookup goes by key, not position.
        le16(b, 20, 8); le32(b, 24, 4); le32(b, 32, 16)
        le16(b, 36, 0); le32(b, 40, discId.length + 1); le32(b, 48, 0)
        keys.copyInto(b, keyTable)
        discId.encodeToByteArray().copyInto(b, dataTable)
        "Game".encodeToByteArray().copyInto(b, dataTable + 16)
        return b
    }

    /** A tiny image: PVD at 16, root at 18, then either UMD_DATA.BIN at 19 or PSP_GAME at 19 holding PARAM.SFO at 20. */
    private fun iso(umd: String?, sfoId: String?): ByteArray {
        val img = ByteArray(sector * 22)
        val pvd = 16 * sector
        img[pvd] = 1; "CD001".encodeToByteArray().copyInto(img, pvd + 1)
        record(img, pvd + 156, "\u0000", 18, sector)
        var p = 18 * sector
        p += record(img, p, "\u0000", 18, sector)
        p += record(img, p, "\u0001", 18, sector)
        if (umd != null) {
            record(img, p, "UMD_DATA.BIN;1", 19, umd.length)
            umd.encodeToByteArray().copyInto(img, 19 * sector)
        }
        if (sfoId != null) {
            record(img, p, "PSP_GAME", 19, sector)
            val data = sfo(sfoId)
            record(img, 19 * sector, "PARAM.SFO;1", 20, data.size)
            data.copyInto(img, 20 * sector)
        }
        return img
    }

    private fun reader(img: ByteArray): (Long, Int) -> ByteArray? = { off, len ->
        if (off >= img.size) null else img.copyOfRange(off.toInt(), minOf(img.size, (off + len).toInt()))
    }

    @Test fun umd_data_names_the_serial() {
        assertEquals("ULUS-10466", PspSerial.fromIso(reader(iso("ULUS-10466|0001|G|", null))))
    }

    @Test fun param_sfo_is_the_fallback() {
        assertEquals("ULES-01376", PspSerial.fromIso(reader(iso(null, "ULES01376"))))
    }

    @Test fun an_eboot_carries_its_sfo() {
        val data = sfo("NPJH50184")
        val pbp = ByteArray(0x28 + data.size)
        "\u0000PBP".encodeToByteArray().copyInto(pbp, 0)
        le32(pbp, 0x08, 0x28); le32(pbp, 0x0C, 0x28 + data.size)
        data.copyInto(pbp, 0x28)
        assertEquals("NPJH-50184", PspSerial.fromPbp(reader(pbp)))
    }

    @Test fun anything_else_is_null() {
        assertNull(PspSerial.fromIso(reader(ByteArray(sector * 20))))
        assertNull(PspSerial.fromIso(reader(iso(null, null))))
        assertNull(PspSerial.fromPbp(reader(ByteArray(64))))
        assertNull(PspSerial.normalise("Tekken"))
        assertEquals("UCES-00356", PspSerial.normalise("uces00356"))
    }

    @Test fun readable_extensions() {
        assertTrue(PspSerial.readable("Tekken 6.ISO") && PspSerial.readable("x.cso") && PspSerial.readable("EBOOT.PBP"))
        assertTrue(!PspSerial.readable("x.chd"))
        assertTrue(PspSerial.readable(".iso") && !PspSerial.readable("iso"))   // a dot file's name is its extension here
    }
}
