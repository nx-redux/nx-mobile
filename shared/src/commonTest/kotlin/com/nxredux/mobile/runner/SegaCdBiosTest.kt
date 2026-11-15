package com.nxredux.mobile.runner

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.disc.ChdHeaderTest
import com.nxredux.mobile.library.disc.DiscProbe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SegaCdBiosTest {
    /** A Sega CD IP header (sector 0's user data) with [code] at the security code's region byte. */
    private fun header(code: Int): ByteArray = ByteArray(2048).also { "SEGADISCSYSTEM  ".encodeToByteArray().copyInto(it, 0); it[0x20B] = code.toByte() }

    /** A raw MODE1/2352 bin: the sync pattern, then [sector0] at +16. */
    private fun rawBin(sector0: ByteArray): ByteArray = ByteArray(2 * 2352).also { b ->
        for (i in 1..10) b[i] = 0xFF.toByte(); b[15] = 1
        sector0.copyInto(b, 16)
    }

    @Test fun the_region_is_the_security_codes_byte_as_both_cores_read_it() {
        assertEquals(DiscRegion.EU, SegaCdBios.regionOf(header(0x64)))
        assertEquals(DiscRegion.JP, SegaCdBios.regionOf(header(0xA1)))
        assertEquals(DiscRegion.US, SegaCdBios.regionOf(header(0x7A)))
        assertNull(SegaCdBios.regionOf(ByteArray(2048)))                                  // no Sega CD header
        assertNull(SegaCdBios.regionOf(header(0x64).copyOf(0x100)))                        // cut short
        // A raw image read as 2048-byte sectors (a raw dump named .iso): the header 16 bytes in, any case.
        val raw = ByteArray(2048).also { "segadiscsystem  ".encodeToByteArray().copyInto(it, 0x10); it[0x10 + 0x20B] = 0xA1.toByte() }
        assertEquals(DiscRegion.JP, SegaCdBios.regionOf(raw))
    }

    @Test fun each_core_takes_its_own_names_for_the_discs_region_only() {
        val euOnly = setOf("bios_CD_E.bin", "bios_CD_J.bin")
        assertEquals("bios_CD_U.bin", SegaCdBios.missing(SegaPadType.PICODRIVE, DiscRegion.US, euOnly))
        assertEquals("bios_CD_U.bin", SegaCdBios.missing(SegaPadType.GPGX, DiscRegion.US, euOnly))
        assertNull(SegaCdBios.missing(SegaPadType.PICODRIVE, DiscRegion.EU, euOnly))
        // PicoDrive also takes the redump names, as .bin or .zip; Genesis Plus GX does not.
        assertNull(SegaCdBios.missing(SegaPadType.PICODRIVE, DiscRegion.US, setOf("us_scd2_9306.bin")))
        assertNull(SegaCdBios.missing(SegaPadType.PICODRIVE, DiscRegion.JP, setOf("jp_mcd1_9112.zip")))
        assertEquals("bios_CD_U.bin", SegaCdBios.missing(SegaPadType.GPGX, DiscRegion.US, setOf("us_scd2_9306.bin")))
        assertNull(SegaCdBios.missing("snes9x", DiscRegion.US, emptySet()))
    }

    @Test fun the_message_names_the_file_the_disc_needs_and_where_the_others_are() {
        val inMd = mapOf("bios_CD_E.bin" to "MD", "bios_CD_J.bin" to "MD")
        assertEquals(
            "This game is a US disc: put bios_CD_U.bin in Bios/SEGACD/. Bios/MD/ has bios_CD_E.bin and bios_CD_J.bin, which only boot European and Japanese discs.",
            SegaCdBios.message("SEGACD", SegaPadType.GPGX, DiscRegion.US, "bios_CD_U.bin", setOf("bios_CD_E.bin", "bios_CD_J.bin", "scph1001.bin"), inMd::get),
        )
        assertEquals(
            "This game is a Japanese disc: put bios_CD_J.bin in Bios/MD/. Bios/SEGACD/ has bios_CD_E.bin, which only boots European discs.",
            SegaCdBios.message("MD", SegaPadType.PICODRIVE, DiscRegion.JP, "bios_CD_J.bin", setOf("bios_CD_E.bin"), mapOf("bios_CD_E.bin" to "SEGACD")::get),
        )
        // A file in no Bios folder (a stale copy) is not offered as one the user has.
        assertEquals(
            "This game is a US disc: put bios_CD_U.bin in Bios/SEGACD/.",
            SegaCdBios.message("SEGACD", SegaPadType.GPGX, DiscRegion.US, "bios_CD_U.bin", setOf("bios_CD_E.bin"), { null }),
        )
    }

    @Test fun genesis_plus_gx_forced_region_and_the_generic_need() {
        assertEquals(DiscRegion.EU, SegaCdBios.gpgxForced("pal"))
        assertEquals(DiscRegion.US, SegaCdBios.gpgxForced("ntsc-u"))
        assertEquals(DiscRegion.JP, SegaCdBios.gpgxForced("ntsc-j"))
        assertNull(SegaCdBios.gpgxForced("auto"))
        assertNull(SegaCdBios.gpgxForced(null))
        assertTrue(CoreRegistry.requiredBios("SEGACD").all(SegaCdBios::isSegaCdNeed))
        assertTrue(CoreRegistry.requiredBios("MD").any(SegaCdBios::isSegaCdNeed))
        assertFalse(CoreRegistry.requiredBios("PCE").any(SegaCdBios::isSegaCdNeed))
    }

    @Test fun the_header_is_read_through_a_cue_an_iso_an_m3u_and_a_chd() = runTest {
        val src = FakeRomsSource(
            mapOf("d" to listOf("U.cue", "U (Track 1).bin", "E.iso", "J.m3u", "J.cue", "J.bin", "C.chd", "A.cue", "A.bin")),
            texts = mapOf(
                "d/U.cue" to "FILE \"U (Track 1).bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n",
                "d/J.m3u" to "J.cue\n",
                "d/J.cue" to "FILE \"J.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n",
                "d/A.cue" to "FILE \"A.bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n",
            ),
            bytes = mapOf(
                "d/U (Track 1).bin" to rawBin(header(0x7A)), "d/E.iso" to header(0x64), "d/J.bin" to rawBin(header(0xA1)),
                "d/C.chd" to ChdHeaderTest().chdBytes("CHT2"), "d/A.bin" to ByteArray(2352),
            ),
        )
        val dir = src.listChildren(com.nxredux.mobile.runner.ContentRef("d"))
        fun e(name: String) = dir.first { it.name == name }
        val probe = DiscProbe(src) { _, sectors -> sectors.map { header(0x64) } }
        suspend fun region(name: String) = probe.firstDataSector(e(name), dir)?.let(SegaCdBios::regionOf)
        assertEquals(DiscRegion.US, region("U.cue"))
        assertEquals(DiscRegion.EU, region("E.iso"))
        assertEquals(DiscRegion.JP, region("J.m3u"))
        assertEquals(DiscRegion.EU, region("C.chd"))
        assertNull(region("A.cue"))   // audio only (an MSU-MD cue): no header, the generic check stands
    }
}
