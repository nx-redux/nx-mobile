package com.nxredux.mobile.bios

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.KnownBiosAsset
import com.nxredux.mobile.settings.SaveTransferRules
import com.nxredux.mobile.storage.AndroidAllFilesAccess
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.nxredux.mobile.storage.AndroidSaveTransfer
import com.nxredux.mobile.storage.DirImportFiles
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.ImportEntry
import com.nxredux.mobile.storage.ImportFiles
import com.nxredux.mobile.storage.ImportTree
import com.nxredux.mobile.storage.MediaHome
import com.nxredux.mobile.storage.RetroArchFolders
import com.nxredux.mobile.storage.SafTree
import com.nxredux.mobile.storage.TreeImportFiles
import com.nxredux.mobile.storage.ZipImportFiles
import com.nxredux.mobile.storage.looksLikeZip
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Settings → Library → Import BIOS on Android. Each picked file is read once (a zip entry can only be read in order),
 * hashed while it is copied to a private temp file, then [BiosIntakePlanner] decides where it goes and the chosen
 * files are written into the home's `Bios/<console>/` ([HomeBiosFolder]). One import at a time on its own thread;
 * results come on the main thread. Refused while Device Sync runs (it writes the same folders) and when the home is a
 * path that All files access no longer reaches.
 */
class AndroidBiosIntake(
    context: Context,
    private val pickers: AndroidSaveTransfer.Pickers,
    private val syncing: () -> Boolean = { false },
) : BiosIntake {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val runningCount = AtomicInteger()

    override val running: Boolean get() = runningCount.get() > 0

    override fun import(source: BiosSource, progress: (String) -> Unit, done: (BiosIntakeResult) -> Unit) {
        refusal()?.let { return done(BiosIntakeResult.Message(it)) }
        when (source) {
            BiosSource.FOLDER -> pickers.openFolder { ref -> ref ?: return@openFolder; work(progress, done) { treeFiles(ref) } }
            BiosSource.FILES -> pickers.openFiles { refs -> if (refs.isNotEmpty()) work(progress, done) { pickedFiles(refs) } }
            BiosSource.ZIP -> pickers.openZip { ref ->
                ref ?: return@openZip
                val open = opener(ref)
                // The picker shows every file type: only a zip goes on.
                if (!looksLikeZip(open)) return@openZip done(BiosIntakeResult.Message("That file is not a zip"))
                work(progress, done) { ZipImportFiles { open() ?: error("cannot read the zip") } }
            }
            // With All files access every RetroArch system folder found is read; without it the user picks RetroArch's folder.
            BiosSource.RETROARCH -> if (AndroidAllFilesAccess.isGranted()) {
                val roots = retroArchRoots()
                if (roots.isEmpty()) return done(BiosIntakeResult.Message(NO_RETROARCH))
                work(progress, done) { concat(roots.map { DirImportFiles(it, listOf(SYSTEM)) }) }
            } else pickers.openFolder { ref ->
                ref ?: return@openFolder
                val tree = ImportTree.of(resolver, ref)
                if (tree.child(treeStart(ref), SYSTEM)?.isDirectory != true) return@openFolder done(BiosIntakeResult.Message(NOT_RETROARCH))
                work(progress, done) { TreeImportFiles(tree, treeStart(ref), listOf(SYSTEM)) }
            }
        }
    }

    private fun refusal(): String? {
        if (syncing()) return SaveTransferRules.SYNCING
        val home = AndroidHomeLocator(app).current() ?: return NO_HOME
        val own = FileRefs.isFile(home) && MediaHome.owns(MediaHome.cachedDir(app), File(FileRefs.path(home)))
        if (!own && FileRefs.accessOff(home, AndroidAllFilesAccess.isGranted())) return ACCESS_OFF
        return null
    }

    /** The work on the import thread, asked again after the picker; any throwable becomes "Failed: …". */
    private fun work(progress: (String) -> Unit, done: (BiosIntakeResult) -> Unit, files: () -> ImportFiles) {
        refusal()?.let { return done(BiosIntakeResult.Message(it)) }
        val home = AndroidHomeLocator(app).current() ?: return done(BiosIntakeResult.Message(NO_HOME))
        runningCount.incrementAndGet()
        main.post { progress("Working…") }
        executor.execute {
            val result = try {
                run(files(), HomeBiosFolder.of(resolver, home))
            } catch (e: Throwable) {
                NxLog.e(TAG, "BIOS import failed", e)
                BiosIntakeResult.Message("Failed: ${e.message ?: e::class.simpleName}")
            }
            main.post { runningCount.decrementAndGet(); done(result) }
        }
    }

    private fun run(files: ImportFiles, folder: HomeBiosFolder): BiosIntakeResult {
        val temp = File(app.cacheDir, "bios-intake").apply { deleteRecursively(); mkdirs() }
        try {
            val picked = mutableListOf<PickedBios>()
            val tempOf = HashMap<String, File>()
            val known = KnownBiosAsset.get(app)
            val names = known.entries.flatMap { listOf(it.name) + it.aliases }.mapTo(HashSet()) { it.lowercase() }
            for (e in files.entries()) {
                if (!BiosIntakePlanner.isCandidate(e.path)) continue
                val base = e.path.substringAfterLast('/')
                if (!BiosIntakePlanner.needsHash(e.size)) {
                    // Too big for any BIOS: planned by name and size only, never copied aside.
                    picked += PickedBios(e.path, e.size ?: 0L, null)
                    continue
                }
                val copy = File(temp, tempOf.size.toString())
                val (md5, size) = Md5.copy(e.open(), copy.outputStream())
                if (known.byMd5(md5).isEmpty() && base.lowercase() !in names && base.lowercase().replace("-", "") !in names) {
                    copy.delete()   // no BIOS: counted, not kept
                } else tempOf[e.path] = copy
                picked += PickedBios(e.path, size, md5)
            }
            val plan = BiosIntakePlanner.plan(picked, known) { c, n -> folder.md5(c, n) }
            val failed = plan.writes.filter { it.needsWrite }.mapNotNull { w ->
                val src = tempOf[w.pickedPath]
                val ok = src != null && folder.write(w.console, w.name) { FileInputStream(src) }
                if (ok) null else BiosSkip(w.pickedPath, "Could not write")
            }
            return BiosIntakeSummary.resultOf(plan, failed)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun opener(ref: ContentRef): () -> InputStream? {
        val file = if (FileRefs.isFile(ref)) File(FileRefs.path(ref)) else null
        return { if (file != null) FileInputStream(file) else resolver.openInputStream(Uri.parse(ref.value)) }
    }

    private fun treeFiles(ref: ContentRef): ImportFiles = TreeImportFiles(ImportTree.of(resolver, ref), treeStart(ref))

    /** Single documents from the multi-file picker, each under its display name. */
    private fun pickedFiles(refs: List<ContentRef>): ImportFiles = object : ImportFiles {
        override fun entries(): Sequence<ImportEntry> = refs.asSequence().map { ref ->
            val uri = Uri.parse(ref.value)
            val name = if (FileRefs.isFile(ref)) File(FileRefs.path(ref)).name else SafTree(resolver).displayName(uri) ?: uri.lastPathSegment.orEmpty()
            object : ImportEntry {
                override val path = name
                override val mtime: Long? = null
                override val size: Long? = null
                override fun open(): InputStream = opener(ref)() ?: error("cannot read $name")
            }
        }
    }

    private fun concat(all: List<ImportFiles>): ImportFiles = object : ImportFiles {
        override fun entries(): Sequence<ImportEntry> = all.asSequence().flatMap { it.entries() }
    }

    /** A SAF pick is a tree URI: the walk starts at its root document. A file ref (All files access) is the folder itself. */
    private fun treeStart(root: ContentRef): ContentRef = if (FileRefs.isFile(root)) root else Uri.parse(root.value).let {
        ContentRef(DocumentsContract.buildDocumentUriUsingTree(it, DocumentsContract.getTreeDocumentId(it)).toString())
    }

    @Suppress("DEPRECATION")   // the shared storage root itself, read with All files access
    private fun retroArchRoots(): List<File> = RetroArchFolders.ROOTS.map { File(Environment.getExternalStorageDirectory(), it) }
        .filter { File(it, SYSTEM).isDirectory }

    private companion object {
        const val TAG = "BiosIntake"
        const val SYSTEM = "system"
        const val NO_HOME = "Set a home folder first"
        const val ACCESS_OFF = "Turn on All files access to import BIOS"
        const val NO_RETROARCH = "No RetroArch system folder found"
        const val NOT_RETROARCH = "Pick the RetroArch folder (the one with system inside)"
    }
}
