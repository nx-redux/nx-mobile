package com.nxredux.mobile.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import com.nxredux.mobile.library.ListingUnavailable
import com.nxredux.mobile.library.pathChildDocId
import com.nxredux.mobile.library.pathParentDocId
import com.swordfish.libretrodroid.NxLog
import java.io.InputStream
import java.io.OutputStream

/** Thin DocumentsContract wrapper over a persisted tree. Every call is blocking: use it from Dispatchers.IO. */
class SafTree(private val resolver: ContentResolver) {

    data class SafEntry(val uri: Uri, val name: String, val isDirectory: Boolean, val size: Long?, val lastModified: Long?)

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    /**
     * [dir]'s children. An empty answer from the external-storage provider is checked: the provider hides every folder
     * while its FUSE root reads as missing (see ListingUnavailable), so an empty folder whose parent also lists empty
     * while the folder's own row exists throws [ListingUnavailable]. A tree root has no parent to check and lists as
     * answered.
     */
    fun listChildren(dir: Uri): List<SafEntry> {
        val treeUri = treeOf(dir)
        val docId = docIdOf(dir)
        val rows = query(treeUri, docId)
        if (rows.isNotEmpty() || dir.authority != EXTERNAL_STORAGE) return rows
        val parent = pathParentDocId(docId, DocumentsContract.getTreeDocumentId(treeUri)) ?: return rows
        val now = SystemClock.elapsedRealtime()
        if (parent == parentOk && now - parentOkAt < PARENT_OK_MILLIS) return rows
        if (query(treeUri, parent).isNotEmpty()) { parentOk = parent; parentOkAt = now; return rows }
        if (!exists(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))) return rows   // deleted, not hidden
        NxLog.w(TAG, "listing of $docId unavailable: its parent lists empty too")
        throw ListingUnavailable(docId)
    }

    private fun exists(uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    // The last parent seen listing non-empty: skeleton siblings (~40 empty system folders) share one parent query.
    @Volatile private var parentOk: String? = null
    @Volatile private var parentOkAt = 0L

    private fun query(treeUri: Uri, docId: String): List<SafEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val out = ArrayList<SafEntry>()
        resolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                val isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (isDir || c.isNull(3)) null else c.getLong(3)
                val mtime = if (c.isNull(4)) null else c.getLong(4)
                out += SafEntry(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), name, isDir, size, mtime)
            }
        }
        return out
    }

    /** One query when the provider uses path ids (external storage); a listing otherwise. */
    fun child(dir: Uri, name: String): SafEntry? {
        if (dir.authority != EXTERNAL_STORAGE) return listChildren(dir).firstOrNull { it.name == name }
        val childId = pathChildDocId(docIdOf(dir), name) ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeOf(dir), childId)
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                SafEntry(uri, c.getString(1) ?: name, isDir, if (isDir || c.isNull(3)) null else c.getLong(3), if (c.isNull(4)) null else c.getLong(4))
            }
        }.getOrNull()
    }

    /**
     * Looks names up in [dir] for a caller that asks about several: on the external-storage provider each lookup is one
     * query ([child]); elsewhere [dir] is listed once, on the first lookup, and every name is found in that listing (a
     * snapshot: a document made after it is not seen). Provider errors propagate.
     */
    fun lookup(dir: Uri): Lookup =
        if (dir.authority == EXTERNAL_STORAGE) Lookup(one = { name -> child(dir, name) }, listing = null)
        else Lookup(one = null, listing = { listChildren(dir).associateBy { it.name } })

    /** [lookup]'s answer: call it with a name. */
    class Lookup internal constructor(private val one: ((String) -> SafEntry?)?, listing: (() -> Map<String, SafEntry>)?) {
        private val listed by lazy(LazyThreadSafetyMode.NONE) { listing?.invoke() }

        operator fun invoke(name: String): SafEntry? = one?.invoke(name) ?: listed?.get(name)

        /**
         * The folder's entries when a lookup already listed it (providers without path ids); null on the external-storage
         * provider, where each lookup is one query and nothing was listed.
         */
        fun listedEntries(): Collection<SafEntry>? = if (one != null) null else listed?.values
    }

    /**
     * Creates a document [name] in [parent] without looking for an existing one first (the caller knows there is none);
     * the provider may rename it.
     */
    fun createNew(parent: Uri, name: String): Uri? =
        runCatching { DocumentsContract.createDocument(resolver, asDocument(parent), "application/octet-stream", name) }
            .onFailure { NxLog.w(TAG, "createFile $name failed", it) }.getOrNull()

    fun ensureDir(parent: Uri, name: String): Uri? {
        child(parent, name)?.let { return if (it.isDirectory) it.uri else null }
        val created = runCatching { DocumentsContract.createDocument(resolver, asDocument(parent), DocumentsContract.Document.MIME_TYPE_DIR, name) }
            .onFailure { NxLog.w(TAG, "createDirectory $name failed", it) }.getOrNull() ?: return null
        // A racing create makes the provider rename ours to "name (1)"; use the folder that has the name.
        return child(parent, name)?.takeIf { it.isDirectory }?.uri ?: created
    }

    /** Creates [name] in [parent], or returns the existing file's uri. */
    fun createFile(parent: Uri, name: String): Uri? {
        child(parent, name)?.let { return if (!it.isDirectory) it.uri else null }
        val created = runCatching { DocumentsContract.createDocument(resolver, asDocument(parent), "application/octet-stream", name) }
            .onFailure { NxLog.w(TAG, "createFile $name failed", it) }.getOrNull() ?: return null
        // Some providers rename on collision or append an extension; verify the name we asked for exists.
        return child(parent, name)?.uri ?: created
    }

    fun openRead(uri: Uri): InputStream? = runCatching { resolver.openInputStream(uri) }.getOrNull()

    /** The provider's own move of [child] from [fromDir] into [toDir]; false where unsupported (it throws) or it fails. */
    fun move(child: Uri, fromDir: Uri, toDir: Uri): Boolean =
        runCatching { DocumentsContract.moveDocument(resolver, asDocument(child), asDocument(fromDir), asDocument(toDir)) != null }
            .onFailure { NxLog.w(TAG, "move failed", it) }.getOrDefault(false)

    /**
     * [src] copied whole into [toDir] (a folder recursively), each file's size checked against the source's: false on any
     * failure, and what was written is deleted.
     */
    fun copy(src: SafEntry, toDir: Uri): Boolean {
        val made = (if (src.isDirectory) runCatching {
            DocumentsContract.createDocument(resolver, asDocument(toDir), DocumentsContract.Document.MIME_TYPE_DIR, src.name)
        }.getOrNull() else createNew(toDir, src.name)) ?: return false
        val ok = runCatching {
            if (src.isDirectory) listChildren(src.uri).all { copy(it, made) }
            else {
                val written = openRead(src.uri)?.use { input -> openWrite(made)?.use { out -> input.copyTo(out, COPY_BUFFER) } } ?: -1L
                safCopyComplete(written, src.size)
            }
        }.getOrDefault(false)
        if (!ok) delete(made)
        return ok
    }

    /** Truncating write stream ("wt"); the caller closes it. */
    fun openWrite(uri: Uri): OutputStream? = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()

    fun lastModified(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    fun rename(uri: Uri, newName: String): Uri? =
        runCatching { DocumentsContract.renameDocument(resolver, asDocument(uri), newName) }.onFailure { NxLog.w(TAG, "rename failed", it) }.getOrNull()

    /** The display name the provider reports for [uri], or null when it cannot be read. */
    fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    }.getOrNull()

    /** Deletes the document [uri]; false when the provider refuses or fails. */
    fun delete(uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, asDocument(uri)) }.onFailure { NxLog.w(TAG, "delete failed", it) }.getOrDefault(false)

    /**
     * False when tree root [dir]'s own document row is gone (a deleted folder lists as empty); one query, for roots only.
     * Provider errors propagate.
     */
    fun rootExists(dir: Uri): Boolean = rootFound(dir) ?: false

    /** [rootExists], with null when the provider gave no answer (no cursor): not proof the folder is gone. */
    fun rootFound(dir: Uri): Boolean? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeOf(dir), docIdOf(dir))
        return resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() }
    }

    /** True when [uri] addresses its tree's root document. */
    fun isTreeRoot(uri: Uri): Boolean = docIdOf(uri) == DocumentsContract.getTreeDocumentId(uri)

    fun hasWriteGrant(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    /**
     * False when [tree]'s top folder says it takes no new files (a read-only provider or medium); null when the provider
     * does not report its flags, or the query fails.
     */
    fun takesNewFiles(tree: Uri): Boolean? = runCatching {
        resolver.query(asDocument(tree), arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0 else null
        }
    }.getOrNull()

    /** The document URI for [uri]: a bare tree URI (the picker's root) becomes its root document; document URIs pass through. */
    internal fun asDocument(uri: Uri): Uri =
        if (DocumentsContract.isTreeUri(uri) && uri.pathSegments.size == 2) DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) else uri

    fun docIdOf(uri: Uri): String =
        if (DocumentsContract.isTreeUri(uri) && uri.pathSegments.size == 2) DocumentsContract.getTreeDocumentId(uri)
        else DocumentsContract.getDocumentId(uri)

    fun treeOf(uri: Uri): Uri = DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri))

    companion object {
        const val TAG = "SafTree"
        const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        const val PARENT_OK_MILLIS = 2_000L
        const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/** [SafTree.copy]'s stream buffer. */
private const val COPY_BUFFER = 8 * 1024 * 1024

/** A file copy is finished when its bytes were written ([written] -1: nothing could be read or written) and match the source's [expected] size where the provider reports one. */
internal fun safCopyComplete(written: Long, expected: Long?): Boolean = written >= 0 && (expected == null || written == expected)
