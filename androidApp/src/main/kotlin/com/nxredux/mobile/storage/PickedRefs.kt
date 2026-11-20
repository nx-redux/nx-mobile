package com.nxredux.mobile.storage

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.nxredux.mobile.runner.ContentRef
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** What the import pickers hand back, read alike by Import saves and Import BIOS. */
internal object PickedRefs {
    /** Opens a picked single file: a path with All files access, else a SAF document. */
    fun opener(resolver: ContentResolver, ref: ContentRef): () -> InputStream? {
        val file = if (FileRefs.isFile(ref)) File(FileRefs.path(ref)) else null
        return { if (file != null) FileInputStream(file) else resolver.openInputStream(Uri.parse(ref.value)) }
    }

    /** A SAF pick is a tree URI: the walk starts at its root document. A file ref (All files access) is the folder itself. */
    fun treeStart(root: ContentRef): ContentRef = if (FileRefs.isFile(root)) root else Uri.parse(root.value).let {
        ContentRef(DocumentsContract.buildDocumentUriUsingTree(it, DocumentsContract.getTreeDocumentId(it)).toString())
    }

    /** The RetroArch roots on shared storage holding any of [tops], in [RetroArchFolders.ROOTS] order. */
    @Suppress("DEPRECATION")   // the shared storage root itself, read with All files access
    fun retroArchRoots(tops: List<String>): List<File> = RetroArchFolders.ROOTS.map { File(Environment.getExternalStorageDirectory(), it) }
        .filter { root -> tops.any { File(root, it).isDirectory } }
}
