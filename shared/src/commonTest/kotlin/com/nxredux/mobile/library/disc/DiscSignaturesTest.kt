package com.nxredux.mobile.library.disc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiscSignaturesTest {
    private fun sector(at: Int = 0, text: String? = null, bytes: ByteArray? = null) = ByteArray(2048).also { s ->
        text?.encodeToByteArray()?.copyInto(s, at); bytes?.copyInto(s, at)
    }
    private fun pvd(system: String) = sector().also { s ->
        byteArrayOf(1, 'C'.code.toByte(), 'D'.code.toByte(), '0'.code.toByte(), '0'.code.toByte(), '1'.code.toByte()).copyInto(s, 0)
        system.padEnd(32).encodeToByteArray().copyInto(s, 8)
    }

    @Test fun playstation_and_psp_by_the_pvd_system_id() {
        assertEquals(DiscKind.PS, DiscSignatures.identify(DataSectors(sector(), sector(), pvd("PLAYSTATION"))))
        assertEquals(DiscKind.PSP, DiscSignatures.identify(DataSectors(sector(), sector(), pvd("PSP GAME"))))
    }

    @Test fun sega_cd_dreamcast_and_saturn_by_sector_zero() {
        assertEquals(DiscKind.SEGACD, DiscSignatures.identify(DataSectors(sector(text = "SEGADISCSYSTEM  "), null, null)))
        assertEquals(DiscKind.DC, DiscSignatures.identify(DataSectors(sector(text = "SEGA SEGAKATANA "), null, null)))
        assertEquals(DiscKind.SATURN, DiscSignatures.identify(DataSectors(sector(text = "SEGA SEGASATURN "), null, null)))
        assertEquals("saturn", DiscKind.SATURN.consoleId)
    }

    @Test fun pc_engine_cd_by_sector_one() {
        assertEquals(DiscKind.PCE, DiscSignatures.identify(DataSectors(sector(), sector(32, "PC Engine CD-ROM SYSTEM"), null)))
    }

    @Test fun gamecube_and_wii_by_their_magic() {
        val gc = ByteArray(0x20).also { byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D).copyInto(it, 0x1C) }
        val wii = ByteArray(0x20).also { byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte()).copyInto(it, 0x18) }
        assertEquals(DiscKind.GC, DiscSignatures.nintendo(gc))
        assertEquals(DiscKind.WII, DiscSignatures.nintendo(wii))
    }

    // A cartridge .bin (a GBA header, or noise) has no disc signature.
    @Test fun a_cartridge_or_noise_is_nothing() {
        val gba = ByteArray(2048).also { "NINTENDO".encodeToByteArray().copyInto(it, 0xA0) }
        assertNull(DiscSignatures.identify(DataSectors(gba, gba, gba)))
        assertNull(DiscSignatures.identify(DataSectors(ByteArray(2048) { (it * 31).toByte() }, null, null)))
        assertNull(DiscSignatures.identify(DataSectors(null, null, null)))
        assertNull(DiscSignatures.nintendo(ByteArray(4)))
    }

    // A PS2 disc says PLAYSTATION too; its volume is past a CD's size: no disc kind.
    @Test fun a_ps2_disc_is_not_a_playstation_one() {
        val ps2 = pvd("PLAYSTATION").also { p -> val n = 2_000_000; for (i in 0..3) p[80 + i] = (n shr (8 * i)).toByte() }
        assertNull(DiscSignatures.identify(DataSectors(sector(), sector(), ps2)))
        val ps1 = pvd("PLAYSTATION").also { p -> val n = 300_000; for (i in 0..3) p[80 + i] = (n shr (8 * i)).toByte() }
        assertEquals(DiscKind.PS, DiscSignatures.identify(DataSectors(sector(), sector(), ps1)))
    }
}
