package com.nxredux.mobile.runner

import com.nxredux.mobile.library.ListingUnavailable
import com.nxredux.mobile.storage.ImportTree
import com.nxredux.mobile.storage.MirrorRules
import com.nxredux.mobile.storage.SyncAction
import com.nxredux.mobile.storage.TreeEntry
import com.swordfish.libretrodroid.NxLog
import java.io.File

/**
 * Imports a tree folder (SAF, or a file ref with All files access: [ImportTree]) into a local one (BIOS): newer-wins
 * ([MirrorRules.decide]), or with `whenDiffers` whenever size or last-modified differ (user dumps are often older than the
 * bundled files). A leftover part is never imported ([MirrorRules.isTransient]), and after every copy the local file's
 * mtime is set to the tree entry's last-modified. A folder the provider will not list ([ListingUnavailable]) is imported
 * by name: the local names and the caller's extra names are looked up. Blocking: Dispatchers.IO.
 */
class TreeSync(private val tree: ImportTree) {

    /**
     * What an [importDir] found on the tree: the files it takes, by path under the imported folder ([seen]), and whether
     * every folder listed ([complete]; a hidden listing is imported by name, so a file missing from [seen] may still be there).
     */
    class Found {
        val seen = HashSet<String>()
        var complete = true
    }

    fun importDir(
        treeDir: ContentRef,
        localDir: File,
        accept: (String) -> Boolean = { true },
        recursive: Boolean,
        whenDiffers: Boolean = false,
        extraNames: List<String> = emptyList(),
        found: Found? = null,
        prefix: String = "",
    ) {
        val listed = try {
            tree.list(treeDir)
        } catch (e: ListingUnavailable) {
            NxLog.w(TAG, "listing hidden; importing by name", e)
            found?.complete = false
            return importByName(treeDir, localDir, accept, recursive, whenDiffers, extraNames)
        }
        for (e in listed) {
            if (e.isDirectory) {
                if (recursive) importDir(e.ref, File(localDir, e.name), accept, true, whenDiffers, found = found, prefix = "$prefix${e.name}/")
                continue
            }
            if (MirrorRules.isTransient(e.name)) continue   // a part: possibly cut short
            if (accept(e.name)) {
                found?.seen?.add(prefix + e.name)
                importOne(e, File(localDir, e.name), whenDiffers)
            }
        }
    }

    /**
     * The hidden-listing fallback: only names known here can be found, the local files [accept] takes (and, when
     * [recursive], the local subfolders) plus [extraNames]. A file only on the tree waits for a listing that works.
     */
    private fun importByName(
        treeDir: ContentRef,
        localDir: File,
        accept: (String) -> Boolean,
        recursive: Boolean,
        whenDiffers: Boolean,
        extraNames: List<String>,
    ) {
        val local = localDir.listFiles()?.toList() ?: emptyList()
        for (name in (local.filter { it.isFile }.map { it.name }.filter(accept) + extraNames).filterNot(MirrorRules::isTransient).distinct()) {
            val e = tree.child(treeDir, name)?.takeIf { !it.isDirectory } ?: continue
            importOne(e, File(localDir, name), whenDiffers)
        }
        if (recursive) for (d in local.filter { it.isDirectory }) {
            tree.child(treeDir, d.name)?.takeIf { it.isDirectory }
                ?.let { importDir(it.ref, d, accept, true, whenDiffers) }
        }
    }

    /** False when the tree copy should have come in and did not. */
    private fun importOne(e: TreeEntry, local: File, whenDiffers: Boolean): Boolean {
        val copy = if (whenDiffers) {
            !local.exists() || (e.size != null && local.length() != e.size) ||
                (e.lastModified != null && local.lastModified() != e.lastModified)
        } else {
            MirrorRules.decide(local.takeIf { it.exists() }?.lastModified(), e.lastModified) == SyncAction.IMPORT
        }
        if (!copy) return true
        return runCatching {
            val bytes = tree.openRead(e.ref)?.use { it.readBytes() } ?: error("cannot open ${e.name}")
            local.writeAtomically(bytes)
            e.lastModified?.let { local.setLastModified(it) }
            NxLog.i(TAG, "imported ${e.name}")
        }.onFailure { NxLog.e(TAG, "import ${e.name} failed", it) }.isSuccess
    }

    private companion object { const val TAG = "TreeSync" }
}
