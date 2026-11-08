package com.nxredux.mobile.library.disc

import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.library.Names

/** A disc's first data track: in [file], its sector N's user data is at `start + N * sectorSize + dataOffset`. */
data class DataTrack(val file: String, val start: Long, val sectorSize: Int, val dataOffset: Int)

/** Where a disc image keeps its data sectors: a bare iso or bin/img, a cue's first data track, a gdi's track 3. */
object DiscImages {
    private val SPACES = Regex("\\s+")

    /** An `.iso` is 2048-byte sectors; a `.bin`/`.img` is raw 2352 (MODE1 +16, MODE2 +24) when it opens with the sync pattern, else 2048. */
    fun rawTrack(fileName: String, head: ByteArray): DataTrack {
        if (Names.extensionOf(fileName) == "iso" || !isSync(head)) return DataTrack(fileName, 0, 2048, 0)
        return DataTrack(fileName, 0, 2352, if (head.size > 15 && head[15] == 2.toByte()) 24 else 16)
    }

    /**
     * The first `TRACK nn MODE1/2048|MODE1/2352|MODE2/2352` of [cue], in the `FILE` before it, starting at its `INDEX 01`
     * (mm:ss:ff, counted from its file's start, so a single-bin disc's track 2 and a pregap in its own file both land);
     * null when the cue has no data track.
     */
    fun cueTrack(cue: String): DataTrack? {
        var file: String? = null
        var track: DataTrack? = null
        for (raw in cue.lineSequence()) {
            val line = raw.trim()
            val named = DiscSheet.cueFile(line)
            if (named != null) {
                if (track != null) return track   // the data track's FILE ended with no INDEX 01: from its start
                file = named
                continue
            }
            val words = line.split(SPACES)
            when {
                track == null && words.size >= 3 && words[0].equals("TRACK", true) -> {
                    val (size, offset) = when (words[2].uppercase()) {
                        "MODE1/2048" -> 2048 to 0
                        "MODE1/2352" -> 2352 to 16
                        "MODE2/2352" -> 2352 to 24
                        else -> continue
                    }
                    track = DataTrack(file ?: return null, 0, size, offset)
                }
                track != null && words.size >= 3 && words[0].equals("INDEX", true) && words[1] == "01" ->
                    return msf(words[2])?.let { track!!.copy(start = it * track!!.sectorSize) } ?: track
                track != null && words.isNotEmpty() && words[0].equals("TRACK", true) -> return track   // no INDEX 01: from its file's start
            }
        }
        return track
    }

    /** [gdi]'s track 3 (the GD high-density area), else its first data track (type 4). */
    fun gdiTrack(gdi: String): DataTrack? {
        val tracks = gdi.lineSequence().drop(1).mapNotNull { line ->
            val f = line.trim().split(SPACES)
            val file = DiscSheet.gdiFile(line) ?: return@mapNotNull null
            val number = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val type = f.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            val size = f.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
            Triple(number, type, DataTrack(file, 0, size, if (size == 2352) 16 else 0))
        }.toList()
        return (tracks.firstOrNull { it.first == 3 } ?: tracks.firstOrNull { it.second == 4 })?.third
    }

    private fun isSync(head: ByteArray): Boolean =
        head.size >= 12 && head[0] == 0.toByte() && head[11] == 0.toByte() && (1..10).all { head[it] == 0xFF.toByte() }

    private fun msf(text: String): Long? {
        val p = text.split(':').mapNotNull { it.toLongOrNull() }
        return if (p.size == 3) (p[0] * 60 + p[1]) * 75 + p[2] else null
    }
}
