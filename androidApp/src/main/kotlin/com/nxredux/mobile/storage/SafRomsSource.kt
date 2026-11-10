package com.nxredux.mobile.storage

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import com.nxredux.mobile.library.Entry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.library.volumeOffline
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.STALE_TEMP_MILLIS
import com.nxredux.mobile.runner.isUniqueTempName
import com.nxredux.mobile.runner.uniqueTempName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [SafRomsSource.readHead]'s read size: a head is grown in these steps up to its file's size or the cap. */
private const val READ_CHUNK = 64 * 1024

/**
 * RomsSource over persisted SAF trees. A ContentRef is a document URI string; the tree root passed by the
 * picker is a tree URI and children are addressed by document id inside that tree. [storage] tells an unmounted
 * volume from a gone folder ([isOffline]); without it every tree counts as online.
 */
class SafRomsSource(private val resolver: ContentResolver, private val storage: StorageManager? = null) : RomsSource {
    val saf = SafTree(resolver)

    private fun SafTree.SafEntry.toEntry() = Entry(ContentRef(uri.toString()), name, isDirectory, size, lastModified)

    override suspend fun listChildren(dir: ContentRef): List<Entry> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(dir.value)
        // The provider lists a deleted directory as empty; probe a root so a gone folder fails as a lost tree.
        if (saf.isTreeRoot(uri) && !saf.rootExists(uri)) throw IllegalArgumentException("Folder no longer exists")
        saf.listChildren(uri).map { it.toEntry() }
    }

    override suspend fun readText(file: ContentRef): String? = withContext(Dispatchers.IO) {
        runCatching { saf.openRead(Uri.parse(file.value))?.use { it.readBytes().decodeToString() } }.getOrNull()
    }

    override suspend fun child(dir: ContentRef, name: String): Entry? = withContext(Dispatchers.IO) {
        saf.child(Uri.parse(dir.value), name)?.toEntry()
    }

    override suspend fun readHead(file: ContentRef, max: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            saf.openRead(Uri.parse(file.value))?.use { input ->
                // Grown as read, never [max] up front: the scraper's image mix reads its inputs with a 32 MB cap.
                val out = java.io.ByteArrayOutputStream(minOf(max, READ_CHUNK))
                val buf = ByteArray(minOf(max, READ_CHUNK))
                var n = 0
                while (n < max) { val r = input.read(buf, 0, minOf(buf.size, max - n)); if (r < 0) break; out.write(buf, 0, r); n += r }
                out.toByteArray()
            }
        }.getOrNull()
    }

    override suspend fun readRange(file: ContentRef, offset: Long, length: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openFileDescriptor(Uri.parse(file.value), "r")?.use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                    if (offset >= ch.size()) return@use ByteArray(0)
                    val buf = java.nio.ByteBuffer.allocate(minOf(length.toLong(), ch.size() - offset).toInt())
                    var at = offset
                    while (buf.hasRemaining()) { val r = ch.read(buf, at); if (r < 0) break; at += r }
                    buf.array().copyOf(buf.position())
                }
            }
        }.getOrNull()
    }

    /** The provider's move (API 24+), refused when [toDir] already holds [name]. */
    override suspend fun moveFile(fromDir: ContentRef, name: String, toDir: ContentRef): Boolean = withContext(Dispatchers.IO) {
        try {
            val from = Uri.parse(fromDir.value)
            val to = Uri.parse(toDir.value)
            val src = saf.child(from, name) ?: return@withContext false
            if (saf.child(to, name) != null) return@withContext false
            saf.move(src.uri, from, to)
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { false }
    }

    override suspend fun copyFile(fromDir: ContentRef, name: String, toDir: ContentRef): Boolean = withContext(Dispatchers.IO) {
        try {
            val to = Uri.parse(toDir.value)
            val src = saf.child(Uri.parse(fromDir.value), name) ?: return@withContext false
            if (saf.child(to, name) != null) return@withContext false
            saf.copy(src, to)
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { false }
    }

    /** "primary:NXRedux" -> "NXRedux"; a volume root ("XXXX-XXXX:") shows its volume id. */
    override fun displayName(tree: ContentRef): String {
        val id = runCatching { saf.docIdOf(Uri.parse(tree.value)) }.getOrNull() ?: return super.displayName(tree)
        val path = id.substringAfter(':', "")
        return if (path.isEmpty()) id.substringBefore(':') else path.trimEnd('/').substringAfterLast('/')
    }

    /**
     * An external-storage tree whose volume ("4913-1501" in "4913-1501:Roms") is not among the mounted volumes. The
     * provider answers a query on such a tree as it does for a deleted folder ("No root for 4913-1501": no row), so
     * only StorageManager can tell the two apart. Other providers, and any platform failure: false (never throws).
     */
    override suspend fun isOffline(tree: ContentRef): Boolean = withContext(Dispatchers.IO) {
        val sm = storage ?: return@withContext false
        runCatching {
            val uri = Uri.parse(tree.value)
            if (uri.authority != SafTree.EXTERNAL_STORAGE) return@runCatching false
            val mounted = sm.storageVolumes
                .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
                .mapNotNull { it.uuid }
            volumeOffline(DocumentsContract.getTreeDocumentId(uri), mounted)
        }.getOrDefault(false)
    }

    override suspend fun canWrite(tree: ContentRef): Boolean = withContext(Dispatchers.IO) {
        saf.hasWriteGrant(saf.treeOf(Uri.parse(tree.value)))
    }

    override suspend fun createDirectory(parent: ContentRef, name: String): Entry? = withContext(Dispatchers.IO) {
        val uri = saf.ensureDir(Uri.parse(parent.value), name) ?: return@withContext null
        Entry(ContentRef(uri.toString()), name, true, null, saf.lastModified(uri))
    }

    /**
     * [writeBytes]'s `<name>.<hex>.part` swap, so a failed or killed write leaves the old sidecar (`map.txt`, `systems.txt`, a
     * collection) whole instead of truncated in the shared home tree. Where the swap cannot run, [name] is written in
     * place ("wt"): a provider whose documents cannot be renamed (or whose old [name] cannot be deleted), so
     * sidecar writes keep working there, and a swap that failed after the old [name] was deleted (nothing left to lose).
     * A part that could not be written fails with the old [name] untouched.
     */
    override suspend fun writeText(dir: ContentRef, name: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val parent = Uri.parse(dir.value)
        val bytes = text.encodeToByteArray()
        when (replace(parent, name, bytes)) {
            Replace.DONE -> true
            Replace.FAILED -> false
            Replace.NO_SWAP, Replace.OLD_GONE -> writeInPlace(parent, name, bytes)
        }
    }

    private fun writeInPlace(parent: Uri, name: String, bytes: ByteArray): Boolean {
        val uri = saf.createFile(parent, name) ?: return false   // the existing document when [name] is there
        return runCatching { saf.openWrite(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)
    }

    /**
     * `<name>.<hex>.part` first, then the old [name] deleted and the part renamed; a failed step deletes the part. Not atomic
     * on SAF (there is no replacing rename): false may mean the old [name] is already gone. A provider without rename
     * support always fails, with the old [name] kept. A rename the provider turns into another name ("Tetris (1).png") is
     * deleted and fails. The folder is listed at most once per write ([SafTree.lookup]).
     */
    override suspend fun writeBytes(dir: ContentRef, name: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        replace(Uri.parse(dir.value), name, bytes) == Replace.DONE
    }

    private enum class Replace {
        DONE,
        FAILED,     // the part could not be written; the old name is untouched
        NO_SWAP,    // the provider cannot rename the part or delete the old name; nothing changed
        OLD_GONE,   // the old name was deleted, then the rename failed; the part is deleted too
    }

    private fun replace(parent: Uri, name: String, bytes: ByteArray): Replace {
        // A part of its own, so two overlapping writes of one sidecar never write into the same document. One a killed
        // write leaves is skipped by scans and sync like any `.part` ([Names.isHidden]).
        val partName = uniqueTempName(name, Names.PART)
        var part: Uri? = null
        var oldGone = false
        try {
            val find = saf.lookup(parent)
            find("$name${Names.PART}")?.let { saf.delete(it.uri) }   // the fixed part name older versions left behind
            // Parts killed writes of [name] left, where the folder was listed anyway; on the external-storage provider that
            // would take a listing of a whole ROM folder per write, so there they stay (hidden from scans, never synced).
            find.listedEntries()?.forEach { e ->
                val mtime = e.lastModified ?: return@forEach
                if (!e.isDirectory && isUniqueTempName(e.name, name, Names.PART) && System.currentTimeMillis() - mtime > STALE_TEMP_MILLIS) {
                    saf.delete(e.uri)
                }
            }
            part = saf.createNew(parent, partName) ?: return Replace.FAILED
            // Asked before anything is deleted: a provider that reports no rename support would lose the old name.
            if (flags(part)?.let { (it and DocumentsContract.Document.FLAG_SUPPORTS_RENAME) == 0 } == true) {
                saf.delete(part)
                return Replace.NO_SWAP
            }
            val wrote = saf.openWrite(part)?.use { it.write(bytes) } != null
            if (!wrote) { saf.delete(part); return Replace.FAILED }
            val old = find(name)
            if (old != null && old.isDirectory) { saf.delete(part); return Replace.FAILED }
            if (old != null && !saf.delete(old.uri)) { saf.delete(part); return Replace.NO_SWAP }
            oldGone = old != null
            val renamed = saf.rename(part, name) ?: run { saf.delete(part); return if (oldGone) Replace.OLD_GONE else Replace.FAILED }
            part = renamed   // from here the document to clean up on failure is the renamed one
            if (saf.displayName(renamed) != name) { saf.delete(renamed); return if (oldGone) Replace.OLD_GONE else Replace.FAILED }
            part = null
            return Replace.DONE
        } catch (e: CancellationException) {
            part?.let(saf::delete)
            throw e
        } catch (e: Throwable) {
            part?.let(saf::delete)
            return if (oldGone) Replace.OLD_GONE else Replace.FAILED
        }
    }

    /** The document's [DocumentsContract.Document.COLUMN_FLAGS], or null when the provider does not report them. */
    private fun flags(uri: Uri): Int? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else null
        }
    }.getOrNull()

    override suspend fun delete(dir: ContentRef, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            saf.child(Uri.parse(dir.value), name)?.takeIf { !it.isDirectory }?.let { saf.delete(it.uri) } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * DocumentsContract's delete removes a folder with everything in it, so the folder is listed first and deleted only
     * when that listing is empty; a listing the provider cannot vouch for ([ListingUnavailable]) keeps it.
     */
    override suspend fun deleteEmptyDirectory(parent: ContentRef, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = saf.child(Uri.parse(parent.value), name)?.takeIf { it.isDirectory } ?: return@withContext false
            saf.listChildren(dir.uri).isEmpty() && saf.delete(dir.uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * DocumentsContract's rename. A provider that turns [to] into another name ("RPGs (1).txt": [to] taken, or the
     * same name in another case on a case-insensitive volume) is renamed back and fails.
     */
    override suspend fun rename(dir: ContentRef, from: String, to: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = Uri.parse(dir.value)
            val old = saf.child(parent, from)?.takeIf { !it.isDirectory } ?: return@withContext false
            val renamed = saf.rename(old.uri, to) ?: return@withContext false
            if (saf.displayName(renamed) == to) return@withContext true
            saf.rename(renamed, from)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
    }
}
