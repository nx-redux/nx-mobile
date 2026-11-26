package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.library.ArchiveEntries
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.storage.AndroidRefs
import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.util.zip.ZipException

/** Why an archive could not be unpacked. [message] is what the user sees. */
class ArchiveException(val kind: Kind, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Kind { UNREADABLE, ENTRY_COUNT, STORAGE }
}

/**
 * An unpacked archive entry. [entryName] is the archive entry's file name, what minarch's Use extracted file name names
 * saves after; [file] may be renamed after the archive (flycast).
 */
data class Unpacked(val file: File, val entryName: String)

/**
 * Unpacks the one ROM inside a `.zip` or `.7z` into `cacheDir/roms/<archive name>.<archive length>/<inner name>` and
 * returns it; with `namedAfterArchive` (a core that names saves after the content, flycast) the file is
 * `<archive stem>.<inner extension>` instead ([ArchiveEntries.unpackedName]). An existing extraction of the entry's
 * size is reused (minarch's rule, ma_game.c:190-217). Both formats are read with Commons Compress over the content's
 * file channel (a SAF document or a file ref), so the entry sizes come from the central directory (a streamed zip has
 * none in its local headers). Blocking: call on Dispatchers.IO. Each extraction is a [RomCache]
 * entry: unpacked under the entry's lock (a prefetch and a launch may unpack it at once; the second caller then reuses
 * the first one's file; a sweep waits), touched on every use (its mtime is its last use), and swept by RomCache. A
 * cancelled caller's copy still completes.
 */
object Archives {
    private const val BUFFER = 1 shl 20
    /** A 7z whose headers or decoder dictionary would need more than this is refused as unreadable, not an OOM. */
    private const val MAX_7Z_MEMORY_KB = 512 * 1024

    /**
     * The extraction is returned [RomCache.hold]-ed (taken under its lock, so no sweep slips in between): the caller
     * must [RomCache.release] `result.file.parentFile.name` when done with it. On a throw nothing stays held.
     */
    fun extract(context: Context, ref: ContentRef, archiveName: String, romExtensions: Set<String>, namedAfterArchive: Boolean = false): Unpacked {
        val ext = Names.extensionOf(archiveName)
        if (ext !in Names.ARCHIVE_EXTENSIONS) throw unreadable(archiveName, null)
        val pfd = try {
            AndroidRefs(context.contentResolver).openFd(ref, "r")
        } catch (e: FileNotFoundException) {
            throw cannotOpen(archiveName, e.message, e)
        } catch (e: SecurityException) {
            throw cannotOpen(archiveName, e.message, e)
        } ?: throw cannotOpen(archiveName, null, null)
        pfd.use { descriptor ->
            val dir = File(RomCache.root(context), "$archiveName.${descriptor.statSize}")
            RomCache.withEntryLock(dir.parentFile!!, dir.name) {
                RomCache.hold(context, dir.name)
                try {
                    FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                        return unpackFrom(channel, ext, archiveName, dir, romExtensions, namedAfterArchive)
                    }
                } catch (e: Throwable) {
                    RomCache.release(context, dir.name)
                    throw e
                }
            }
        }
    }

    private fun unpackFrom(
        channel: SeekableByteChannel,
        ext: String,
        archiveName: String,
        dir: File,
        romExtensions: Set<String>,
        namedAfterArchive: Boolean,
    ): Unpacked {
        val archive = try {
            open(channel, ext)
        } catch (e: OutOfMemoryError) {
            throw unreadable(archiveName, e)
        } catch (e: PasswordRequiredException) {
            throw unreadable(archiveName, e, encrypted = true)
        } catch (e: IOException) {
            throw unreadable(archiveName, e)
        } catch (e: RuntimeException) {
            throw unreadable(archiveName, e)
        }
        archive.use {
            val (entry, chosen) = it.pick(archiveName, romExtensions)
            val file = unpack(entry.size, dir, ArchiveEntries.unpackedName(archiveName, chosen.fileName, namedAfterArchive), archiveName) { out ->
                it.stream(entry).use { s -> s.copyTo(out, BUFFER) }
            }
            return Unpacked(file, chosen.fileName)
        }
    }

    /** An open zip or 7z read the same way (Commons Compress gives the two no shared type). */
    private class Opened(
        archive: Closeable,
        list: () -> List<ArchiveEntry>,
        val stream: (ArchiveEntry) -> InputStream,
    ) : Closeable by archive {
        val entries: List<ArchiveEntry> by lazy(LazyThreadSafetyMode.NONE, list)

        /**
         * Some Windows tools write `\` separators and omit a folder's trailing `/`; 7z stores folders without one, and a
         * 7z entry may be unnamed. Entries are picked on these names, then mapped back by index.
         */
        fun names(): List<String> = entries.map { normalised(it.name, it.isDirectory) }
    }

    /** Only a 7z can ask for a password at this point: opening a zip never throws [PasswordRequiredException]. */
    private fun open(channel: SeekableByteChannel, ext: String): Opened =
        if (ext == "zip") {
            val zip = ZipFile.builder().setSeekableByteChannel(channel).get()
            Opened(zip, { zip.entries.toList() }) { zip.getInputStream(it as ZipArchiveEntry) }
        } else {
            val sevenZ = SevenZFile.builder().setSeekableByteChannel(channel).setMaxMemoryLimitKb(MAX_7Z_MEMORY_KB).get()
            Opened(sevenZ, { sevenZ.entries.toList() }) { sevenZ.getInputStream(it as SevenZArchiveEntry) }
        }

    private fun Opened.pick(archiveName: String, romExtensions: Set<String>): Pair<ArchiveEntry, ArchiveEntries.Pick.Single> {
        val names = names()
        val chosen = choose(names, archiveName, romExtensions)
        return entries[names.indexOf(chosen.entry)] to chosen
    }

    /**
     * The archive's entry paths, normalised as [extract] picks on them (for [ArchiveEntries.pick]), without unpacking
     * anything; null when it cannot be opened or read. Blocking.
     */
    fun entryNames(context: Context, ref: ContentRef, archiveName: String): List<String>? = runCatching {
        val ext = Names.extensionOf(archiveName)
        if (ext !in Names.ARCHIVE_EXTENSIONS) return null
        AndroidRefs(context.contentResolver).openFd(ref, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel -> open(channel, ext).use { it.names() } }
        }
    }.getOrNull()

    /**
     * The first [max] bytes of the entry [extract] would unpack (picked by [romExtensions]), read from its stream without
     * unpacking it (a launch's ROM header check, [com.nxredux.mobile.runner.ClassicNes]); null when the archive cannot be
     * opened or read or holds no single ROM. Blocking.
     */
    fun head(context: Context, ref: ContentRef, archiveName: String, romExtensions: Set<String>, max: Int): ByteArray? = runCatching {
        val ext = Names.extensionOf(archiveName)
        if (ext !in Names.ARCHIVE_EXTENSIONS) return null
        AndroidRefs(context.contentResolver).openFd(ref, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                open(channel, ext).use { a ->
                    a.stream(a.pick(archiveName, romExtensions).first).use { it.readUpTo(max) }
                }
            }
        }
    }.getOrNull()

    /** An entry's path with `/` separators and a folder's trailing `/` (some Windows tools write `\` and omit it; a 7z entry may be unnamed). */
    private fun normalised(name: String?, isDirectory: Boolean): String =
        (name ?: "").replace('\\', '/').let { n -> if (isDirectory && !n.endsWith("/")) "$n/" else n }

    private fun choose(names: List<String>, archiveName: String, romExtensions: Set<String>): ArchiveEntries.Pick.Single =
        when (val pick = ArchiveEntries.pick(names, romExtensions)) {
            is ArchiveEntries.Pick.Single -> pick
            ArchiveEntries.Pick.Empty -> throw ArchiveException(ArchiveException.Kind.ENTRY_COUNT, "$archiveName must hold exactly one ROM file (found 0)")
            is ArchiveEntries.Pick.Many -> throw ArchiveException(
                ArchiveException.Kind.ENTRY_COUNT,
                "$archiveName must hold exactly one ROM file (found ${pick.count}: ${pick.names.joinToString(", ")})",
            )
            is ArchiveEntries.Pick.Sheet -> throw ArchiveException(
                ArchiveException.Kind.ENTRY_COUNT,
                "$archiveName holds ${pick.sheet} with its tracks, which can't be played zipped. " +
                    "Zip a single .chd or .iso instead, or keep the files unzipped.",
            )
        }

    /** Writes through `<name>.part` then renames; a stale `.part` from a killed launch is dropped first and never served. */
    private fun unpack(size: Long, dir: File, fileName: String, archiveName: String, copy: (OutputStream) -> Unit): File {
        val target = File(dir, fileName)
        val part = File(dir, "$fileName.part")
        part.delete()
        if (size >= 0 && target.exists() && target.length() == size) return target.also { RomCache.touch(dir, it) }
        try {
            dir.mkdirs()
            part.outputStream().buffered(BUFFER).use(copy)
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) throw IOException("Cannot rename ${part.name}")
            }
            RomCache.touch(dir, target)
            return target
        } catch (e: OutOfMemoryError) {
            part.delete(); throw unreadable(archiveName, e)
        } catch (e: PasswordRequiredException) {
            part.delete(); throw unreadable(archiveName, e, encrypted = true)
        } catch (e: MemoryLimitException) {
            // The 7z decoder checks its dictionary against the limit when the entry's stream opens.
            part.delete(); throw unreadable(archiveName, e)
        } catch (e: org.tukaani.xz.MemoryLimitException) {
            part.delete(); throw unreadable(archiveName, e)
        } catch (e: ZipException) {
            part.delete(); throw unreadable(archiveName, e)
        } catch (e: RuntimeException) {
            // Commons Compress can throw IllegalArgumentException / ArrayIndexOutOfBoundsException on malformed entries.
            part.delete(); throw unreadable(archiveName, e)
        } catch (e: IOException) {
            part.delete()
            val noSpace = e.message?.contains("ENOSPC") == true || e.message?.contains("No space left", ignoreCase = true) == true
            throw ArchiveException(
                ArchiveException.Kind.STORAGE,
                if (noSpace) "Not enough space to unpack $archiveName" else "Could not unpack $archiveName: ${e.message ?: e.javaClass.simpleName}",
                e,
            )
        }
    }

    private fun cannotOpen(archiveName: String, message: String?, cause: Throwable?) =
        ArchiveException(ArchiveException.Kind.STORAGE, "Could not unpack $archiveName: ${message ?: "cannot open"}", cause)

    private fun unreadable(archiveName: String, cause: Throwable?, encrypted: Boolean = false) = ArchiveException(
        ArchiveException.Kind.UNREADABLE,
        "$archiveName is not a valid zip/7z archive" + if (encrypted) " (password-protected)" else "",
        cause,
    )
}

/** Up to [max] bytes from the stream's start (fewer only at its end); InputStream.readNBytes is API 33. */
internal fun InputStream.readUpTo(max: Int): ByteArray {
    val buf = ByteArray(max)
    var n = 0
    while (n < max) {
        val r = read(buf, n, max - n)
        if (r < 0) break
        n += r
    }
    return buf.copyOf(n)
}
