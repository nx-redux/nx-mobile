package com.nxredux.mobile.library.disc

import com.nxredux.mobile.storage.Bytes

/** What a disc image is, by its own bytes ([DiscSignatures]); [consoleId] null: recognised but never placed (Saturn). */
enum class DiscKind(val consoleId: String?) { PS("ps"), PSP("psp"), SEGACD("segacd"), SATURN(null), DC("dc"), PCE("pce"), GC("gc"), WII("wii") }

/** The 2048-byte user data of the first data track's sectors 0, 1 and 16 (null: not read, or past the end). */
class DataSectors(val s0: ByteArray?, val s1: ByteArray?, val s16: ByteArray?)

/**
 * Disc signatures, after rcheevos' rhash/hash_disc.c and RetroArch's task_database_cue.c: Sega's IP header at sector 0,
 * the PC Engine CD system card string at sector 1 (+32), the ISO9660 primary volume descriptor's system id at sector 16;
 * GameCube and Wii by their header magic.
 */
object DiscSignatures {
    fun identify(d: DataSectors): DiscKind? {
        d.s0?.let { s ->
            when {
                Bytes.startsWithAscii(s, "SEGADISCSYSTEM") -> return DiscKind.SEGACD
                Bytes.startsWithAscii(s, "SEGA SEGAKATANA") -> return DiscKind.DC
                Bytes.startsWithAscii(s, "SEGA SEGASATURN") -> return DiscKind.SATURN
            }
        }
        if (d.s1?.let { Bytes.startsWithAscii(it, "PC Engine CD-ROM SYSTEM", at = 32) } == true) return DiscKind.PCE
        d.s16?.let { p ->
            val isPvd = p.size >= 40 && p[0] == 1.toByte() && Bytes.startsWithAscii(p, "CD001", at = 1)
            if (isPvd) {
                val system = p.copyOfRange(8, 40).decodeToString().trim { it <= ' ' }   // space- or NUL-padded
                // A PS2 disc says PLAYSTATION too: its volume (LE u32 at 80, in sectors) is past any CD's.
                if (system == "PLAYSTATION") return if (p.size >= 84 && Bytes.le32(p, 80) > CD_MAX_SECTORS) null else DiscKind.PS
                if (system == "PSP GAME") return DiscKind.PSP
            }
        }
        return null
    }

    fun nintendo(head: ByteArray): DiscKind? = when {
        head.matches(0x1C, 0xC2, 0x33, 0x9F, 0x3D) -> DiscKind.GC
        head.matches(0x18, 0x5D, 0x1C, 0x9E, 0xA3) -> DiscKind.WII
        else -> null
    }

    /** The most sectors a CD holds (99 minutes); a bigger volume is a DVD. */
    private const val CD_MAX_SECTORS = 99L * 60 * 75

    private fun ByteArray.matches(at: Int, vararg v: Int): Boolean = size >= at + v.size && v.indices.all { this[at + it] == v[it].toByte() }
}
