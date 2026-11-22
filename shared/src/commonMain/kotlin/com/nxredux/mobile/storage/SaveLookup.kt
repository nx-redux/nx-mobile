package com.nxredux.mobile.storage

/** Which of a save's possible files to load (the handheld reads only its configured name; mobile also reads the others). */
object SaveLookup {
    /**
     * [candidates] has the configured name first ([NxPaths.saveRamCandidates], [NxPaths.stateCandidates]): that one when it
     * exists, else the most recently modified of the others; null when none exists. [mtime] is null for a missing file.
     */
    fun pick(candidates: List<String>, mtime: (String) -> Long?): String? = ordered(candidates, mtime).firstOrNull()

    /**
     * Every existing candidate in [pick]'s preference order: the configured name (first in [candidates]) when it exists,
     * then the others newest first. A reader tries them in turn, so a damaged or empty file never hides a good save under
     * another name.
     */
    fun ordered(candidates: List<String>, mtime: (String) -> Long?): List<String> {
        val first = candidates.firstOrNull() ?: return emptyList()
        val rest = candidates.drop(1).mapNotNull { c -> mtime(c)?.let { c to it } }.sortedByDescending { it.second }.map { it.first }
        return if (mtime(first) != null) listOf(first) + rest else rest
    }
}

/** minarch `State_read`'s header probe (ma_saves.c:176, 219-229). */
object StateBytes {
    private const val HEADER_SIZE = 16

    /** [bytes] after a 16-byte RetroArch `RASTATE` header, as minarch reads it; unchanged without one. */
    fun stripRaStateHeader(bytes: ByteArray): ByteArray =
        if (bytes.size >= HEADER_SIZE && Bytes.startsWithAscii(bytes, "RASTATE")) bytes.copyOfRange(HEADER_SIZE, bytes.size) else bytes
}
