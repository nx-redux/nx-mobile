package com.nxredux.mobile.storage

import android.content.ContentResolver
import android.net.Uri
import com.nxredux.mobile.runner.ContentRef
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/** One file or folder of an [ImportTree]: [size] null for a folder or when unknown, [lastModified] null when unknown. */
data class TreeEntry(val ref: ContentRef, val name: String, val isDirectory: Boolean, val size: Long?, val lastModified: Long?)

/**
 * A folder tree read outside RomsSource: the BIOS import ([com.nxredux.mobile.runner.TreeSync]) and Import saves
 * ([TreeImportFiles]). A SAF tree ([SafImportTree]) or, with All files access, the file system ([FileImportTree]); each
 * entry's [TreeEntry.ref] is the implementation's own (a document URI, a file ref). Blocking: Dispatchers.IO.
 */
interface ImportTree {
    /** [dir]'s direct children (callers recurse). SAF throws [com.nxredux.mobile.library.ListingUnavailable] for a hidden listing. */
    fun list(dir: ContentRef): List<TreeEntry>
    fun child(dir: ContentRef, name: String): TreeEntry?
    fun openRead(file: ContentRef): InputStream?

    companion object {
        /** The tree [root] is on: a file ref's file system, else SAF through [resolver]. */
        fun of(resolver: ContentResolver, root: ContentRef): ImportTree =
            if (FileRefs.isFile(root)) FileImportTree else SafImportTree(SafTree(resolver))
    }
}

/** A persisted SAF tree ([SafTree]); refs are document URI strings. */
class SafImportTree(private val saf: SafTree) : ImportTree {
    private fun SafTree.SafEntry.toEntry() = TreeEntry(ContentRef(uri.toString()), name, isDirectory, size, lastModified)

    override fun list(dir: ContentRef): List<TreeEntry> = saf.listChildren(Uri.parse(dir.value)).map { it.toEntry() }
    override fun child(dir: ContentRef, name: String): TreeEntry? = saf.child(Uri.parse(dir.value), name)?.toEntry()
    override fun openRead(file: ContentRef): InputStream? = saf.openRead(Uri.parse(file.value))
}

/** The file system under All files access; refs are file refs ([FileRefs]). An unreadable folder fails its listing. */
object FileImportTree : ImportTree {
    private fun fileOf(ref: ContentRef) = File(FileRefs.path(ref))

    private fun entryOf(dir: ContentRef, f: File): TreeEntry {
        val isDir = f.isDirectory
        return TreeEntry(FileRefs.child(dir, f.name), f.name, isDir, if (isDir) null else f.length(), f.lastModified().takeIf { it > 0 })
    }

    override fun list(dir: ContentRef): List<TreeEntry> {
        val files = fileOf(dir).listFiles() ?: throw IOException("cannot list ${FileRefs.path(dir)}")
        return files.map { entryOf(dir, it) }
    }

    override fun child(dir: ContentRef, name: String): TreeEntry? {
        if (!FileRefs.isPlainName(name)) return null
        return File(fileOf(dir), name).takeIf { it.exists() }?.let { entryOf(dir, it) }
    }

    override fun openRead(file: ContentRef): InputStream? = fileOf(file).takeIf { it.isFile }?.let(::FileInputStream)
}
