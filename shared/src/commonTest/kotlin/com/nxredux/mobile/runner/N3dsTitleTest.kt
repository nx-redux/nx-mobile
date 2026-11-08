package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class N3dsTitleTest {
    private fun putLe64(b: ByteArray, at: Int, v: Long) { for (i in 0 until 8) b[at + i] = (v ushr (8 * i)).toByte() }
    private fun putLe32(b: ByteArray, at: Int, v: Int) { for (i in 0 until 4) b[at + i] = (v ushr (8 * i)).toByte() }
    private fun putAscii(b: ByteArray, at: Int, s: String) { s.encodeToByteArray().copyInto(b, at) }

    /** An NCCH header (0x200 bytes) with [programId] at 0x118 and [productCode] at 0x150. */
    private fun ncch(programId: Long, productCode: String): ByteArray = ByteArray(0x200).also {
        putAscii(it, 0x100, "NCCH")
        putLe64(it, 0x118, programId)
        putAscii(it, 0x150, productCode)
    }

    /** A .cci head: NCSD header with [mediaId] at 0x108, partition 0 at [p0Units] media units, its NCCH there. */
    private fun cci(mediaId: Long, p0Units: Int, part0: ByteArray?, unitExp: Int = 0): ByteArray {
        val unit = 0x200 shl unitExp
        val b = ByteArray(if (part0 == null) 0x200 else p0Units * unit + part0.size)
        putAscii(b, 0x100, "NCSD")
        putLe64(b, 0x108, mediaId)
        putLe32(b, 0x120, p0Units)
        putLe32(b, 0x124, 0x1000)
        b[0x188 + 6] = unitExp.toByte()
        part0?.copyInto(b, p0Units * unit)
        return b
    }

    @Test fun a_cxi_gives_its_program_id_and_product_code() {
        val t = N3dsTitle.parse(ncch(0x0004000000125500L, "CTR-P-AJRE"))
        assertEquals(N3dsTitle(0x0004000000125500L, "CTR-P-AJRE"), t)
        assertEquals("AJR", t?.gameCode)
    }

    @Test fun a_cci_gives_its_media_id_and_partition_0s_product_code() {
        val head = cci(0x0004000000126300L, 0x20, ncch(0x0004000000126300L, "CTR-P-BFGE"))
        assertEquals(0x4000L, N3dsTitle.ncchOffset(head))
        assertEquals(N3dsTitle(0x0004000000126300L, "CTR-P-BFGE"), N3dsTitle.parse(head))
    }

    @Test fun a_cci_honours_the_media_unit_size_flag() {
        val head = cci(0x000400000F700100L, 0x10, ncch(0x000400000F700100L, "KTR-P-CAFE"), unitExp = 1)
        assertEquals(0x4000L, N3dsTitle.ncchOffset(head))
        assertEquals("CAF", N3dsTitle.parse(head)?.gameCode)
    }

    @Test fun a_cci_read_only_to_its_header_still_gives_the_media_id() {
        val head = cci(0x0004000000030100L, 0x20, null)
        assertEquals(N3dsTitle(0x0004000000030100L, null), N3dsTitle.parse(head))
    }

    @Test fun a_cci_whose_partition_0_is_not_ncch_keeps_the_media_id() {
        val head = cci(0x0004000000030100L, 0x20, ByteArray(0x200))
        assertEquals(N3dsTitle(0x0004000000030100L, null), N3dsTitle.parse(head))
    }

    @Test fun garbage_short_or_unknown_magic_is_unknown() {
        assertNull(N3dsTitle.parse(ByteArray(0)))
        assertNull(N3dsTitle.parse(ByteArray(0x150)))
        assertNull(N3dsTitle.parse(ByteArray(0x400)))
        assertNull(N3dsTitle.ncchOffset(ByteArray(0x200)))
    }

    @Test fun a_blank_or_odd_product_code_has_no_game_code() {
        assertNull(N3dsTitle(1L, null).gameCode)
        assertNull(N3dsTitle(1L, "CTR").gameCode)
        assertNull(N3dsTitle(1L, "XYZ-P-AJRE").gameCode)
        assertEquals("AJR", N3dsTitle(1L, "ctr-p-ajre").gameCode)
    }

    @Test fun only_plain_cartridge_and_ncch_files_are_read() {
        for (n in listOf("a.3ds", "a.CCI", "a.cxi", ".3ds")) assertEquals(true, N3dsTitle.readable(n), n)
        for (n in listOf("a.zcci", "a.zcxi", "a.3dsx", "a.z3dsx", "a.elf", "a.axf", "a.app", "a.cia", "a")) {
            assertEquals(false, N3dsTitle.readable(n), n)
        }
    }
}
