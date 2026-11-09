package com.nxredux.mobile.storage

import com.nxredux.mobile.library.Entry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.writeAtomically
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [FileRomsSource.readHead]'s read size: a head is grown in these steps up to its file's size or the cap. */
private const val FILE_READ_CHUNK = 64 * 1024

/**
 * RomsSource over file refs ([FileRefs]), for trees read with All files access, with [SafRomsSource]'s return
 * conventions; writes are atomic here (`<name>.part`, then a replacing rename). [granted] says whether All files access
 * is on: without it nothing is written and every tree reads as offline, so a revoked grant keeps the home and its
 * extras as an unmounted card does (never "gone"); the app's [own] folder is the exception. [mounted]: the volume holding a
 * path is mounted and its root reads.
 */
class FileRomsSource(
    private val granted: () -> Boolean,
    private val mounted: (File) -> Boolean,
    /** A path the app owns (its media folder): read and written without All files access, and never offline. */
    private val own: (File) -> Boolean = { false },
) : RomsSource {
    private fun fileOf(ref: ContentRef) = File(FileRefs.path(ref))

    /** All files access is on, or [f] is the app's own. */
    private fun allowed(f: File): Boolean = granted() || own(f)

    /** [name] in [dir], or null when [name] is not one path segment (it would address some other file). */
    private fun childOf(dir: ContentRef, name: String): File? = if (FileRefs.isPlainName(name)) File(fileOf(dir), name) else null

    private fun entryOf(dir: ContentRef, f: File): Entry {
        val isDir = f.isDirectory
        return Entry(FileRefs.child(dir, f.name), f.name, isDir, if (isDir) null else f.length(), f.lastModified().takeIf { it > 0 })
    }

    /** A gone folder fails as SAF's gone tree does (IllegalArgumentException); an unreadable one as an I/O failure. */
    override suspend fun listChildren(dir: ContentRef): List<Entry> = withContext(Dispatchers.IO) {
        // Without access listFiles() may answer an empty array, which would read as an emptied folder: fail instead.
        val f = fileOf(dir)
        if (!allowed(f)) throw IOException("All files access is off")
        if (!f.isDirectory) throw IllegalArgumentException("Folder no longer exists")
        val listed = f.listFiles() ?: throw IOException("Cannot list ${f.path}")
        listed.map { entryOf(dir, it) }
    }

    override suspend fun readText(file: ContentRef): String? = withContext(Dispatchers.IO) {
        runCatching { fileOf(file).takeIf { it.isFile }?.readBytes()?.decodeToString() }.getOrNull()
    }

    override suspend fun child(dir: ContentRef, name: String): Entry? = withContext(Dispatchers.IO) {
        childOf(dir, name)?.takeIf { it.exists() }?.let { entryOf(dir, it) }
    }

    override suspend fun readHead(file: ContentRef, max: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            FileInputStream(fileOf(file)).use { input ->
                // Grown as read, never [max] up front: the scraper's image mix reads its inputs with a 32 MB cap.
                val out = ByteArrayOutputStream(minOf(max, FILE_READ_CHUNK))
                val buf = ByteArray(minOf(max, FILE_READ_CHUNK))
                var n = 0
                while (n < max) { val r = input.read(buf, 0, minOf(buf.size, max - n)); if (r < 0) break; out.write(buf, 0, r); n += r }
                out.toByteArray()
            }
        }.getOrNull()
    }

    override suspend fun readRange(file: ContentRef, offset: Long, length: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            RandomAccessFile(fileOf(file), "r").use { f ->
                if (offset >= f.length()) return@use ByteArray(0)
                f.seek(offset)
                val out = ByteArray(minOf(length.toLong(), f.length() - offset).toInt())
                f.readFully(out)
                out
            }
        }.getOrNull()
    }

    /** The last path segment, as typed (a ':' in a folder name is kept, unlike the default's SAF id rule). */
    override fun displayName(tree: ContentRef): String {
        val path = FileRefs.path(tree).trimEnd('/')
        return path.substringAfterLast('/').ifEmpty { path.ifEmpty { "/" } }
    }

    override suspend fun isOffline(tree: ContentRef): Boolean = withContext(Dispatchers.IO) {
        runCatching { val f = fileOf(tree); !own(f) && (!granted() || !mounted(f)) }.getOrDefault(false)
    }

    override suspend fun canWrite(tree: ContentRef): Boolean = withContext(Dispatchers.IO) {
        fileOf(tree).let { allowed(it) && it.isDirectory && it.canWrite() }
    }

    override suspend fun createDirectory(parent: ContentRef, name: String): Entry? = withContext(Dispatchers.IO) {
        val f = childOf(parent, name) ?: return@withContext null
        if (f.isDirectory) return@withContext entryOf(parent, f)
        if (f.exists()) return@withContext null   // a file is in the way
        // A racing create (a scan's skeleton and a sidecar write) can make it first: then it is a folder all the same.
        if (!f.mkdir() && !f.isDirectory) return@withContext null
        entryOf(parent, f)
    }

    override suspend fun writeText(dir: ContentRef, name: String, text: String): Boolean = writeBytes(dir, name, text.encodeToByteArray())

    /**
     * A part of its own (`<name>.<hex>.part`, so two overlapping writes of one sidecar never share it), synced, then
     * renamed over [name] (a replacing rename: atomic). A failure deletes the part; [name] is untouched. A part a killed
     * write leaves is skipped by scans and sync like any `.part` ([Names.isHidden]).
     */
    override suspend fun writeBytes(dir: ContentRef, name: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val target = childOf(dir, name) ?: return@withContext false
        if (target.isDirectory || target.parentFile?.isDirectory != true) return@withContext false
        try {
            File(target.parentFile, name + Names.PART).delete()   // the fixed part name older versions left behind
            target.writeAtomically(bytes, Names.PART)
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    override suspend fun delete(dir: ContentRef, name: String): Boolean = withContext(Dispatchers.IO) {
        childOf(dir, name)?.takeIf { it.isFile }?.delete() ?: false
    }

    override suspend fun deleteEmptyDirectory(parent: ContentRef, name: String): Boolean = withContext(Dispatchers.IO) {
        childOf(parent, name)?.takeIf { it.isDirectory }?.let { d -> d.list()?.isEmpty() == true && d.delete() } ?: false
    }

    /** A rename into [toDir]: fails across volumes (renameTo cannot), or when [toDir] already holds [name]. */
    override suspend fun moveFile(fromDir: ContentRef, name: String, toDir: ContentRef): Boolean = withContext(Dispatchers.IO) {
        val src = childOf(fromDir, name)?.takeIf { it.exists() } ?: return@withContext false
        val dst = childOf(toDir, name) ?: return@withContext false
        if (!allowed(src) || !allowed(dst)) return@withContext false
        !dst.exists() && runCatching { src.renameTo(dst) }.getOrDefault(false)
    }

    /** A whole copy (a folder recursively); any failure or a size mismatch deletes what was written. */
    override suspend fun copyFile(fromDir: ContentRef, name: String, toDir: ContentRef): Boolean = withContext(Dispatchers.IO) {
        val src = childOf(fromDir, name)?.takeIf { it.exists() } ?: return@withContext false
        val dst = childOf(toDir, name) ?: return@withContext false
        if (!allowed(src) || !allowed(dst)) return@withContext false
        if (dst.exists()) return@withContext false
        val ok = runCatching { src.copyRecursively(dst, overwrite = false) && sizeOf(src) == sizeOf(dst) }.getOrDefault(false)
        if (!ok) dst.deleteRecursively()
        ok
    }

    private fun sizeOf(f: File): Long = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()

    /** [to] taken fails, in any case on a case-insensitive volume ([from] itself included), as SAF's rename-back does. */
    override suspend fun rename(dir: ContentRef, from: String, to: String): Boolean = withContext(Dispatchers.IO) {
        val src = childOf(dir, from)?.takeIf { it.isFile } ?: return@withContext false
        val dst = childOf(dir, to) ?: return@withContext false
        if (dst.exists()) return@withContext false
        src.renameTo(dst)
    }
}
