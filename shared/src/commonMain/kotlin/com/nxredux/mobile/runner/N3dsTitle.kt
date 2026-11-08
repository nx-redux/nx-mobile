package com.nxredux.mobile.runner

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.storage.Bytes

/**
 * A 3DS game's identity from the start of its file (3dbrew "NCSD" and "NCCH"): the title ID (an NCSD's media ID at 0x108,
 * an NCCH's program ID at 0x118, little-endian) and the NCCH's product code ("CTR-P-AJRE", 16 ASCII bytes at 0x150).
 * Both headers are plaintext, encrypted games included. Null fields: not in what was read.
 */
data class N3dsTitle(val titleId: Long?, val productCode: String?) {
    /** The product code's 3-letter game code, the same in every region ("AJR" of "CTR-P-AJRE"); null when odd. */
    val gameCode: String?
        get() {
            val parts = productCode?.uppercase()?.split('-') ?: return null
            if (parts.size != 3 || parts[0] !in PLATFORMS || parts[2].length != 4) return null
            return parts[2].substring(0, 3)
        }

    companion object {
        /** An NCSD or NCCH header's length. */
        const val HEADER = 0x200
        /** The farthest into a file [ncchOffset] is followed: partition 0 sits at 0x4000 on every dumped cart. */
        const val MAX_NCCH_OFFSET = 0x100000L
        private val PLATFORMS = setOf("CTR", "KTR")
        private const val MAGIC = 0x100
        private const val NCSD_MEDIA_ID = 0x108
        private const val NCSD_PARTITION_0 = 0x120
        private const val NCSD_FLAGS = 0x188
        private const val NCCH_PROGRAM_ID = 0x118
        private const val NCCH_PRODUCT_CODE = 0x150
        /** .3ds and .cci are NCSD (a cart image), .cxi an NCCH; compressed (z*), homebrew (.3dsx, .elf) and .app are not read. */
        private val READABLE = setOf("3ds", "cci", "cxi")

        fun readable(fileName: String): Boolean = Names.extensionOf(fileName, dotFile = true) in READABLE

        /**
         * Where partition 0's NCCH header starts in an NCSD whose header is [head] (its offset in media units of
         * 0x200 << flags[6]), or null when [head] is no NCSD or the offset is out of reach ([MAX_NCCH_OFFSET]).
         */
        fun ncchOffset(head: ByteArray): Long? {
            if (!magic(head, "NCSD")) return null
            val units = Bytes.le32(head, NCSD_PARTITION_0)
            val exp = head[NCSD_FLAGS + 6].toInt() and 0xFF
            if (units <= 0L || exp > 8) return null
            val offset = units * (0x200L shl exp)
            return offset.takeIf { it <= MAX_NCCH_OFFSET }
        }

        /**
         * The title from [head], the file's first bytes: an NCSD's media ID plus partition 0's product code when [head]
         * reaches its NCCH ([ncchOffset]), or an NCCH's own program ID and product code. Null for anything else.
         */
        fun parse(head: ByteArray): N3dsTitle? {
            if (magic(head, "NCCH")) return ncch(head, 0)
            if (!magic(head, "NCSD")) return null
            val mediaId = Bytes.le64(head, NCSD_MEDIA_ID)
            val code = ncchOffset(head)?.toInt()?.let { at -> if (magic(head, "NCCH", at)) ncch(head, at).productCode else null }
            return N3dsTitle(mediaId, code)
        }

        private fun ncch(b: ByteArray, at: Int): N3dsTitle {
            val code = if (b.size >= at + NCCH_PRODUCT_CODE + 16) {
                b.copyOfRange(at + NCCH_PRODUCT_CODE, at + NCCH_PRODUCT_CODE + 16)
                    .takeWhile { it != 0.toByte() }.toByteArray().decodeToString().trim().ifEmpty { null }
            } else null
            return N3dsTitle(Bytes.le64(b, at + NCCH_PROGRAM_ID), code)
        }

        private fun magic(b: ByteArray, m: String, at: Int = 0): Boolean =
            b.size >= at + HEADER && Bytes.startsWithAscii(b, m, at + MAGIC)
    }
}
