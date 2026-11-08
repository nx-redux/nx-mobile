package com.nxredux.mobile.runner

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.storage.Bytes

/**
 * A PSP game's serial ("ULUS-10466"), the id libretro's PSP cheat files carry in brackets (`Tekken 6 [US] [ULUS-10466].cht`).
 * A UMD image names it in `UMD_DATA.BIN` at the disc root ("ULUS-10466|…"), and `PSP_GAME/PARAM.SFO` holds it as
 * DISC_ID ("ULUS10466"); an EBOOT (`.pbp`) carries the PARAM.SFO at the offset its header gives. Pure parsing over a
 * reader of the image's bytes; the platform opens the file (and inflates a `.cso` into that reader).
 */
object PspSerial {
    private const val SECTOR = 2048L
    private const val PVD_LBA = 16L
    /** A disc root or PSP_GAME listing past this many sectors is not a PSP disc's. */
    private const val MAX_DIR_SECTORS = 64
    private val SERIAL = Regex("^([A-Z]{4})-?([0-9]{5})$")

    /** Extensions the platform can hand a reader for: plain and CISO-compressed images, and EBOOTs. */
    val READABLE = setOf("iso", "cso", "pbp")

    fun readable(fileName: String): Boolean = Names.extensionOf(fileName, dotFile = true) in READABLE

    /** "ULUS10466" or "ulus-10466" → "ULUS-10466"; null when it is no serial. */
    fun normalise(raw: String): String? {
        val m = SERIAL.find(raw.trim().uppercase()) ?: return null
        return "${m.groupValues[1]}-${m.groupValues[2]}"
    }

    /**
     * The serial of an ISO 9660 image whose bytes [read] returns (offset, length; null or short past the end):
     * `UMD_DATA.BIN`, else `PSP_GAME/PARAM.SFO`'s DISC_ID. Null when neither is found.
     */
    fun fromIso(read: (offset: Long, length: Int) -> ByteArray?): String? {
        val pvd = read(PVD_LBA * SECTOR, SECTOR.toInt())?.takeIf { it.size >= 190 } ?: return null
        if (pvd[0].toInt() != 1 || pvd.decodeToString(1, 6) != "CD001") return null
        val root = Extent(Bytes.le32(pvd, 156 + 2), Bytes.le32(pvd, 156 + 10))
        find(read, root, "UMD_DATA.BIN")?.let { umd ->
            val text = read(umd.lba * SECTOR, minOf(umd.size, 64L).toInt())?.decodeToString() ?: return@let
            normalise(text.substringBefore('|'))?.let { return it }
        }
        val game = find(read, root, "PSP_GAME") ?: return null
        val sfo = find(read, game, "PARAM.SFO") ?: return null
        if (sfo.size > 64 * 1024) return null
        return read(sfo.lba * SECTOR, sfo.size.toInt())?.let(::fromSfo)
    }

    /** The serial of an EBOOT whose bytes [read] returns (its PARAM.SFO lies between the first two header offsets). */
    fun fromPbp(read: (offset: Long, length: Int) -> ByteArray?): String? {
        val head = read(0, 0x28)?.takeIf { it.size >= 0x28 } ?: return null
        if (head[0].toInt() != 0 || head.decodeToString(1, 4) != "PBP") return null
        val start = Bytes.le32(head, 0x08)
        val end = Bytes.le32(head, 0x0C)
        if (end <= start || end - start > 64 * 1024) return null
        return read(start, (end - start).toInt())?.let(::fromSfo)
    }

    /** DISC_ID from a PARAM.SFO (psdevwiki: a key table and a data table, entries of 16 bytes after a 20-byte header). */
    fun fromSfo(sfo: ByteArray): String? {
        if (sfo.size < 20 || sfo.decodeToString(1, 4) != "PSF" || sfo[0].toInt() != 0) return null
        val keys = Bytes.le32(sfo, 8).toInt()
        val data = Bytes.le32(sfo, 12).toInt()
        val count = Bytes.le32(sfo, 16).toInt()
        for (i in 0 until minOf(count, 256)) {
            val e = 20 + i * 16
            if (e + 16 > sfo.size) return null
            val keyAt = keys + Bytes.le16(sfo, e)
            val key = cString(sfo, keyAt) ?: continue
            if (key != "DISC_ID") continue
            val len = Bytes.le32(sfo, e + 4).toInt()
            val at = data + Bytes.le32(sfo, e + 12).toInt()
            if (at < 0 || len <= 0 || at + len > sfo.size) return null
            return normalise(sfo.decodeToString(at, at + len).trimEnd('\u0000'))
        }
        return null
    }

    private class Extent(val lba: Long, val size: Long)

    /** The entry named [name] (case-insensitive, `;1` version dropped) in the directory [dir]. */
    private fun find(read: (Long, Int) -> ByteArray?, dir: Extent, name: String): Extent? {
        val sectors = ((dir.size + SECTOR - 1) / SECTOR).toInt()
        if (sectors !in 1..MAX_DIR_SECTORS) return null
        for (s in 0 until sectors) {
            val block = read((dir.lba + s) * SECTOR, SECTOR.toInt()) ?: return null
            var p = 0
            while (p < block.size) {
                val len = block[p].toInt() and 0xFF
                if (len == 0) break   // records never cross a sector; the rest of this one is padding
                if (p + 33 > block.size || p + len > block.size) break
                val nameLen = block[p + 32].toInt() and 0xFF
                if (p + 33 + nameLen <= block.size) {
                    val entry = block.decodeToString(p + 33, p + 33 + nameLen).substringBefore(';')
                    if (entry.equals(name, ignoreCase = true)) return Extent(Bytes.le32(block, p + 2), Bytes.le32(block, p + 10))
                }
                p += len
            }
        }
        return null
    }

    private fun cString(b: ByteArray, at: Int): String? {
        if (at < 0 || at >= b.size) return null
        var end = at
        while (end < b.size && b[end].toInt() != 0) end++
        return b.decodeToString(at, end)
    }
}
