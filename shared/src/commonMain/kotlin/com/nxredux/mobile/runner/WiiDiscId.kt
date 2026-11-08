package com.nxredux.mobile.runner

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.storage.Bytes

/**
 * A disc read by [WiiDiscId.read]: its 6-character [id], and whether its header carries the GameCube magic ([gameCube]),
 * so a GameCube image under the WII tag keeps the plain pad.
 */
data class WiiDisc(val id: String, val gameCube: Boolean)

/**
 * A Wii or GameCube disc's 6-character ID ("SMNE01") and platform, read from the disc header in the image formats Dolphin
 * opens (the layouts in Dolphin's DiscIO blob headers). Pure parsing over a reader of the file's bytes; [inflate] is zlib
 * (GCZ blocks), given by the platform. Null for anything unreadable or not an ID.
 */
object WiiDiscId {
    val READABLE = setOf("iso", "wbfs", "wia", "rvz", "ciso", "gcz")
    private val ID = Regex("^[A-Z0-9]{6}$")
    private const val WIA_DISC_HEADER = 0x48 + 0x10   // after WIAHeader1, past disc_type, compression, level, chunk_size
    private const val CISO_DATA = 0x8000L
    private const val GCZ_MAGIC = 0xB10BC001L
    private const val GCZ_HEADER = 0x20
    private const val DISC_HEADER = 0x20   // the ID at 0, the Wii magic at 0x18, the GameCube magic at 0x1C
    private const val WII_MAGIC = 0x5D1C9EA3L
    private const val GC_MAGIC = 0xC2339F3DL

    fun readable(fileName: String): Boolean = Names.extensionOf(fileName, dotFile = true) in READABLE

    /**
     * The disc in the image: its ID and platform. A header with the GameCube magic (and not the Wii one) is a GameCube
     * disc; a header with neither (a short read, homebrew, an odd dump) counts as Wii.
     */
    fun read(extension: String, read: (Long, Int) -> ByteArray?, inflate: (ByteArray, Int) -> ByteArray?): WiiDisc? =
        runCatching {
            when (extension.lowercase()) {
                "iso" -> idAt(read, 0)
                "wbfs" -> {
                    val head = read(0, 12)?.takeIf { it.size == 12 && it.decodeToString(0, 4) == "WBFS" } ?: return null
                    val shift = head[8].toInt() and 0xFF
                    if (shift !in 9..20) return null
                    idAt(read, 1L shl shift)
                }
                "wia", "rvz" -> {
                    val magic = read(0, 4)?.takeIf { it.size == 4 }?.decodeToString() ?: return null
                    if (magic != "WIA\u0001" && magic != "RVZ\u0001") return null
                    idAt(read, WIA_DISC_HEADER.toLong())
                }
                "ciso" -> {
                    if (read(0, 4)?.decodeToString() != "CISO") return null
                    idAt(read, CISO_DATA)
                }
                "gcz" -> gcz(read, inflate)
                else -> null
            }
        }.getOrNull()

    private fun idAt(read: (Long, Int) -> ByteArray?, offset: Long): WiiDisc? = read(offset, DISC_HEADER)?.let(::disc)

    /** The disc from the start of its header: the ID from the first 6 bytes, the platform from the magics when all 0x20 are there. */
    private fun disc(header: ByteArray): WiiDisc? {
        if (header.size < 6) return null
        val id = header.decodeToString(0, 6).takeIf { ID.matches(it) } ?: return null
        val full = header.size >= DISC_HEADER
        return WiiDisc(id, gameCube = full && Bytes.be32(header, 0x1C) == GC_MAGIC && Bytes.be32(header, 0x18) != WII_MAGIC)
    }

    private fun gcz(read: (Long, Int) -> ByteArray?, inflate: (ByteArray, Int) -> ByteArray?): WiiDisc? {
        val h = read(0, GCZ_HEADER)?.takeIf { it.size == GCZ_HEADER } ?: return null
        if (Bytes.le32(h, 0) != GCZ_MAGIC) return null
        val blockSize = Bytes.le32(h, 24)
        val blocks = Bytes.le32(h, 28)
        if (blockSize !in 1..(1L shl 24) || blocks !in 1..(1L shl 24)) return null
        val dataStart = GCZ_HEADER + blocks * 12
        val p = read(GCZ_HEADER.toLong(), 16)?.takeIf { it.size >= 8 } ?: return null
        val raw0 = Bytes.le64(p, 0)
        val uncompressed = raw0 < 0   // top bit set: stored
        val start = raw0 and Long.MAX_VALUE
        val end = if (blocks > 1 && p.size >= 16) Bytes.le64(p, 8) and Long.MAX_VALUE else Bytes.le64(h, 8)
        val size = (end - start).coerceIn(DISC_HEADER.toLong(), blockSize)
        val block = read(dataStart + start, size.toInt()) ?: return null
        val plain = if (uncompressed) block else inflate(block, DISC_HEADER) ?: return null
        return disc(plain.copyOf(minOf(plain.size, DISC_HEADER)))
    }
}
