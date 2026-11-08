package com.nxredux.mobile.library.disc

import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.library.Entry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.CancellationException

/**
 * A CD CHD's first data track, read through the platform's CHD decoder: [sectors] (relative to the track's start) as 2048
 * bytes of user data each (null: unreadable), or null when [file] cannot be opened as a CHD.
 */
fun interface ChdSectors {
    suspend fun read(file: ContentRef, sectors: List<Int>): List<ByteArray?>?
}

/**
 * A probe's answer: the [console] (null: nothing recognised), whether the image could be [read] at all (a missing track
 * file or a failed read is not an answer to keep), and the [data] file read for a sheet (its answer depends on it).
 */
data class ProbeResult(val console: String?, val read: Boolean, val data: Entry? = null)

/**
 * Which console a disc image is, by its own bytes ([DiscSignatures]): a console id, or null when nothing is recognised
 * (or it is a Saturn disc, which no console here runs). Reads a few sectors through [RomsSource.readRange]; a CD CHD
 * needs [chd] (without it only a GD or DVD CHD is placed, by its metadata). Never throws.
 */
class DiscProbe(private val source: RomsSource, private val chd: ChdSectors? = null) {
    /** [file]'s console; [dir] is its folder's listing (a cue's, gdi's or m3u's files are found there). */
    suspend fun identify(file: Entry, dir: List<Entry>): String? = probe(file, dir).console

    /** [identify] with whether [file] (and the data file a sheet names) could be read. */
    suspend fun probe(file: Entry, dir: List<Entry>): ProbeResult = try {
        resultOf(file, dir, followM3u = true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ProbeResult(null, read = false)
    }

    private suspend fun resultOf(file: Entry, dir: List<Entry>, followM3u: Boolean): ProbeResult = when (Names.extensionOf(file.name)) {
        "iso" -> {
            val head = source.readRange(file.ref, 0, 0x20) ?: return UNREAD
            DiscSignatures.nintendo(head)?.let { ProbeResult(it.consoleId, read = true) } ?: sectorsOf(file.ref, DiscImages.rawTrack(file.name, ByteArray(0)), null)
        }
        "bin", "img" -> source.readRange(file.ref, 0, 16)?.let { sectorsOf(file.ref, DiscImages.rawTrack(file.name, it), null) } ?: UNREAD
        "cue" -> sheet(file, dir) { DiscImages.cueTrack(it) }
        "gdi" -> sheet(file, dir) { DiscImages.gdiTrack(it) }
        "chd" -> chdResult(file.ref)
        "m3u" -> {
            val text = if (followM3u) DiscSheet.readSheet(source, file.ref) ?: return UNREAD else return ProbeResult(null, read = true)
            val first = DiscSheet.references(file.name, text).firstOrNull()?.let { named(dir, it) } ?: return UNREAD
            resultOf(first, dir, followM3u = false).let { it.copy(data = it.data ?: first) }
        }
        else -> ProbeResult(null, read = true)
    }

    /** A cue or gdi: its [track] file, found in [dir], read from the track's start. */
    private suspend fun sheet(file: Entry, dir: List<Entry>, track: (String) -> DataTrack?): ProbeResult {
        val text = DiscSheet.readSheet(source, file.ref) ?: return UNREAD
        val t = track(text) ?: return ProbeResult(null, read = true)   // no data track: an answer
        val data = named(dir, t.file) ?: return UNREAD                 // its file is not there (yet)
        return sectorsOf(data.ref, t, data)
    }

    private suspend fun chdResult(ref: ContentRef): ProbeResult {
        val head = source.readHead(ref, CHD_HEAD) ?: return UNREAD
        return when (ChdHeader.media(head)) {
            ChdMedia.GD -> ProbeResult(DiscKind.DC.consoleId, read = true)
            ChdMedia.DVD -> ProbeResult(DiscKind.PSP.consoleId, read = true)
            ChdMedia.CD -> {
                val reader = chd ?: return UNREAD   // no decoder here: ask again where there is one
                val s = reader.read(ref, listOf(0, 1, 16)) ?: return UNREAD
                ProbeResult(DiscSignatures.identify(DataSectors(s.getOrNull(0), s.getOrNull(1), s.getOrNull(2)))?.consoleId, read = true)
            }
            else -> ProbeResult(null, read = true)
        }
    }

    /** Sectors 0, 1 and 16 of [t] in [ref]; a sector past the end reads as missing, a failed open as unread. */
    private suspend fun sectorsOf(ref: ContentRef, t: DataTrack, data: Entry?): ProbeResult {
        var failed = false
        suspend fun sector(n: Int): ByteArray? {
            val b = source.readRange(ref, t.start + n.toLong() * t.sectorSize + t.dataOffset, 2048)
            if (b == null) failed = true
            return b?.takeIf { it.size == 2048 }
        }
        val kind = DiscSignatures.identify(DataSectors(sector(0), sector(1), sector(16)))
        return if (failed && kind == null) UNREAD.copy(data = data) else ProbeResult(kind?.consoleId, read = true, data = data)
    }

    /** [name] in [dir] as a launch finds it ([DiscSheet.Listing]: any case, or another code page's spelling). */
    private fun named(dir: List<Entry>, name: String): Entry? = DiscSheet.Listing(dir.filter { !it.isDirectory }).find(name)

    private companion object {
        /** A CHD's header and the metadata chained from it (chdman writes it right after the 124-byte header). */
        const val CHD_HEAD = 64 * 1024
        val UNREAD = ProbeResult(null, read = false)
    }
}
