package com.nxredux.mobile.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.nxredux.mobile.gametime.GameTimeService
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.writeAtomically
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.settings.Sources
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [StorageUpgradePlatform] on Android: [DataRoot]'s marker and move, and the adoption of an old picker (SAF) home as an
 * extra ROM folder on its `Roms/` document. Remove with the upgrade screen after v1.0.
 */
class AndroidStorageUpgrade(context: Context) : StorageUpgradePlatform {
    private val app = context.applicationContext

    override fun isDone(): Boolean = DataRoot.isDone(app)

    override fun markDone() = DataRoot.markDone(app)

    /** Walks the private root (a new install's: a few files). */
    override fun markIfEmpty() = DataRoot.markIfEmpty(app)

    override fun startQuietMove() { Thread({ DataRoot.startIfDue(app) }, "data-move-start").start() }

    override fun quietMoveStuck(): Boolean = DataRoot.moveStuck(app)

    override fun isWaiting(): Boolean = File(app.noBackupFilesDir, WAITING).exists()

    override fun setWaiting() {
        runCatching { File(app.noBackupFilesDir, WAITING).createNewFile() }.onFailure { NxLog.w(TAG, "cannot note the wait", it) }
    }

    override suspend fun restoreHomeKeys(extraId: String): Boolean = withContext(Dispatchers.IO) {
        if (!GameTimeService.renamePaths(app) { GameKeys.toHome(it, extraId) }) return@withContext false
        restoreKeys(DataRoot.private(app), extraId)
        NxLog.i(TAG, "the extra $extraId is the home's Roms again")
        true
    }

    override suspend fun copySaves(from: ContentRef, to: ContentRef): Boolean = withContext(Dispatchers.IO) {
        try {
            val copied = DataRoot.copyHome(File(FileRefs.path(from)), File(FileRefs.path(to)))
            NxLog.i(TAG, "copied $copied files from ${FileRefs.path(from)} into ${FileRefs.path(to)}")
            true
        } catch (e: Exception) {
            NxLog.w(TAG, "copying the saves into ${FileRefs.path(to)} failed", e)
            false
        }
    }

    /** [DataRoot.moveNow] into [home] (a file ref); any failure (a copy, a file changed mid-move) is false: nothing marked. */
    override suspend fun moveInto(home: ContentRef): Boolean = withContext(Dispatchers.IO) {
        try {
            val moved = DataRoot.moveNow(app, File(FileRefs.path(home)))
            NxLog.i(TAG, "upgrade moved $moved files into ${FileRefs.path(home)}")
            true
        } catch (e: Exception) {
            NxLog.w(TAG, "the upgrade's move into ${FileRefs.path(home)} failed; the data stays private", e)
            false
        }
    }

    override suspend fun adoptPickerHome(old: ContentRef, into: ContentRef?): ExtraFolder? = withContext(Dispatchers.IO) {
        val saf = SafTree(app.contentResolver)
        val source = AndroidRomsSources.of(app)
        val tree = saf.treeOf(Uri.parse(old.value))
        // Gone for good: no grant left, or the folder deleted. Nothing to adopt, so the upgrade goes on without it.
        if (app.contentResolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission }) return@withContext null
        if (source.isOffline(old)) throw IOException("the old home's storage is not mounted")
        // No answer from the provider is no proof the folder is gone: it waits for a later start.
        when (saf.rootFound(tree)) {
            null -> throw IOException("the old home's provider did not answer")
            false -> return@withContext null
            true -> Unit
        }
        val roms = saf.child(tree, ROMS)?.takeIf { it.isDirectory }?.uri ?: saf.ensureDir(tree, ROMS)
            ?: throw IOException("the old home has no Roms folder")
        val ref = ContentRef(DocumentsContract.buildDocumentUriUsingTree(tree, saf.docIdOf(roms)).toString())
        val extra = adoptedExtra(ref, source.displayName(ref), AndroidAppSettings(app).extraFolders.map { it.id })

        // Game time first (it is the one that can fail as a whole); every step is safe to run again.
        if (!GameTimeService.renamePaths(app) { GameKeys.toExtra(it, extra.id) }) throw IOException("the game time database can't be opened")
        val target = into?.let { File(FileRefs.path(it)) } ?: MediaHome.dir(app)
        copyFolder(source, old, BIOS, File(target, BIOS))
        val collections = copyFolder(source, old, COLLECTIONS, File(target, COLLECTIONS))
        rewriteKeys(DataRoot.private(app), collections, extra.id)
        NxLog.i(TAG, "old home ${old.value} is the extra ${extra.id}")
        extra
    }

    /** [name] of the SAF home [home] copied into [to], folders whole; a file already there is kept. Returns the files copied. */
    private suspend fun copyFolder(source: RomsSource, home: ContentRef, name: String, to: File): List<File> {
        val dir = source.child(home, name)?.takeIf { it.isDirectory } ?: return emptyList()
        val copied = ArrayList<File>()
        suspend fun walk(from: ContentRef, into: File) {
            for (e in source.listChildren(from)) {
                if (!FileRefs.isPlainName(e.name)) continue
                val dest = File(into, e.name)
                if (e.isDirectory) { walk(e.ref, dest); continue }
                if (dest.exists()) continue
                try {
                    dest.parentFile?.mkdirs()
                    val part = File(into, "${e.name}.part")
                    val written = app.contentResolver.openInputStream(Uri.parse(e.ref.value))?.use { input -> part.outputStream().use { input.copyTo(it) } } ?: -1L
                    if (!safCopyComplete(written, e.sizeBytes) || !part.renameTo(dest)) { part.delete(); throw IOException("copy of ${e.name} failed") }
                    e.lastModified?.let { dest.setLastModified(it) }
                    copied += dest
                } catch (x: CancellationException) {
                    throw x
                } catch (x: Exception) {
                    NxLog.w(TAG, "cannot copy $name/${e.name} from the old home", x)   // the old home keeps it
                }
            }
        }
        walk(dir.ref, to)
        return copied
    }

    companion object {
        private const val TAG = "StorageUpgrade"
        private const val ROMS = "Roms"
        private const val BIOS = "Bios"
        private const val COLLECTIONS = "Collections"
        /** In no-backup storage: a file home's upgrader without access said Not now (the data waits in the app). */
        private const val WAITING = "storage-move-waiting"

        /**
         * Rewrites the game keys of the old home's games (`/Roms/...`) to the extra [extraId]'s in [private]'s recents,
         * shortcuts and hidden list and in the [collections] copied from the old home ([GameKeys.rewriteLines]). A file
         * keeps its modified time, so the move's clash rule still compares when it was really last written.
         */
        internal fun rewriteKeys(private: File, collections: List<File>, extraId: String) {
            val files = listOf(NxPaths.RECENTS, NxPaths.SHORTCUTS, NxPaths.HIDDEN).map { File(private, it) } +
                collections.filter { it.name.endsWith(".txt") }
            rewriteEach(files) { GameKeys.rewriteLines(it, extraId) }
        }

        /** [rewriteKeys] the other way: the extra [extraId]'s keys in [private]'s recents, shortcuts and hidden list back to home keys. */
        internal fun restoreKeys(private: File, extraId: String) {
            rewriteEach(listOf(NxPaths.RECENTS, NxPaths.SHORTCUTS, NxPaths.HIDDEN).map { File(private, it) }) {
                GameKeys.rewriteLinesToHome(it, extraId)
            }
        }

        private fun rewriteEach(files: List<File>, rewrite: (String) -> String) {
            for (f in files) {
                if (!f.isFile) continue
                val text = f.readText()
                val next = rewrite(text)
                if (next == text) continue
                val mtime = f.lastModified()
                f.writeAtomically(next.encodeToByteArray())
                f.setLastModified(mtime)
            }
        }

        /** The extra on the old home's `Roms/` document [ref], its id made as an added extra's ([name] its folder name, not one of [taken]). */
        internal fun adoptedExtra(ref: ContentRef, name: String, taken: Collection<String>): ExtraFolder =
            ExtraFolder(Sources.uniqueId(Sources.idFor(name, ref), taken), ref)
    }
}
