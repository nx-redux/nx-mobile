package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.AndroidRefs
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater

/**
 * Opens a PSP game for [PspSerial]: an `.iso` or `.pbp` is read in place, a `.cso` (CISO: a block index, each block
 * raw-deflated or stored) is inflated block by block into the same byte reader. Blocking I/O; null on any failure.
 */
object PspSerialReader {
    fun read(refs: AndroidRefs, ref: ContentRef, fileName: String): String? = runCatching {
        refs.openFd(ref, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                when (fileName.substringAfterLast('.', "").lowercase()) {
                    "iso" -> PspSerial.fromIso(plain(ch))
                    "pbp" -> PspSerial.fromPbp(plain(ch))
                    "cso" -> cso(ch)?.let(PspSerial::fromIso)
                    else -> null
                }
            }
        }
    }.getOrNull()

    private fun plain(ch: FileChannel): (Long, Int) -> ByteArray? = { offset, length -> readAt(ch, offset, length) }

    /** A reader over the uncompressed image of a CISO file, or null when [ch] is no CISO v0/v1. */
    private fun cso(ch: FileChannel): ((Long, Int) -> ByteArray?)? {
        val head = readAt(ch, 0, 0x18)?.takeIf { it.size == 0x18 } ?: return null
        val h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        if (head.decodeToString(0, 4) != "CISO") return null
        val total = h.getLong(8)
        val blockSize = h.getInt(16)
        val align = head[0x15].toInt() and 0xFF
        if (blockSize <= 0 || blockSize > (1 shl 20) || total <= 0) return null
        val blocks = ((total + blockSize - 1) / blockSize).toInt()
        val cache = HashMap<Int, ByteArray>()
        fun block(i: Int): ByteArray? = cache[i] ?: run {
            if (i !in 0 until blocks) return null
            val idx = readAt(ch, 0x18L + i * 4L, 8)?.takeIf { it.size == 8 } ?: return null
            val b = ByteBuffer.wrap(idx).order(ByteOrder.LITTLE_ENDIAN)
            val a = b.getInt(0).toLong() and 0xFFFFFFFFL
            val next = b.getInt(4).toLong() and 0xFFFFFFFFL
            val start = (a and 0x7FFFFFFFL) shl align
            val end = (next and 0x7FFFFFFFL) shl align
            val raw = readAt(ch, start, (end - start).toInt().coerceIn(1, blockSize * 2)) ?: return null
            val out = if (a and 0x80000000L != 0L) raw.copyOf(blockSize) else {
                val inf = Inflater(true)
                try {
                    inf.setInput(raw)
                    val o = ByteArray(blockSize)
                    var n = 0
                    while (n < blockSize && !inf.finished()) { val r = inf.inflate(o, n, blockSize - n); if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break; n += r }
                    o
                } finally { inf.end() }
            }
            out.also { cache[i] = it }
        }
        return { offset, length ->
            val out = ByteArray(length)
            var done = 0
            while (done < length) {
                val pos = offset + done
                val data = block((pos / blockSize).toInt()) ?: break
                val inBlock = (pos % blockSize).toInt()
                val n = minOf(length - done, blockSize - inBlock)
                data.copyInto(out, done, inBlock, inBlock + n)
                done += n
            }
            out.copyOf(done).takeIf { it.isNotEmpty() }
        }
    }
}

/** Up to [length] bytes of [ch] from [offset], without moving its position; null for none. */
internal fun readAt(ch: FileChannel, offset: Long, length: Int): ByteArray? {
    if (offset < 0 || length <= 0) return null
    val buf = ByteBuffer.allocate(length)
    var at = offset
    while (buf.hasRemaining()) {
        val n = ch.read(buf, at)
        if (n <= 0) break
        at += n
    }
    return buf.array().copyOf(buf.position()).takeIf { it.isNotEmpty() }
}
