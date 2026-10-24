package com.nxredux.mobile.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.LibraryIndexCodec
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.ImportResult
import com.nxredux.mobile.settings.ImportSource
import com.nxredux.mobile.settings.SaveTransfer
import com.nxredux.mobile.settings.SaveTransferRules
import com.nxredux.mobile.settings.TransferReport
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Settings → Library → Saves → Export / Import on Android: the pickers come from [pickers] (MainActivity's
 * activity-result launchers); the work runs on one background thread (one transfer at a time) and the results come on
 * the main thread: "Working…" when the work starts, then the summary or the error.
 *
 * An import refuses while a game screen is alive ([gameScreenAlive]: a suspended game would write its save in memory
 * over the imported file), and [importing] stays true from its start to its result so no game starts meanwhile
 * (AppState checks it before the runner).
 */
class AndroidSaveTransfer(
    context: Context,
    private val pickers: Pickers,
    /** Main thread only: some GameActivity holds the session ([com.nxredux.mobile.runner.LibretroRunner.gameScreenAlive]). */
    private val gameScreenAlive: () -> Boolean,
    /** A Device Sync is running: it writes the same saves, so an import waits for it. */
    private val syncing: () -> Boolean = { false },
) : SaveTransfer {
    /** Each answers a SAF document/tree as a content ref, or with All files access a file ref ([FileRefs]); null: cancelled. */
    interface Pickers {
        fun createZip(suggestedName: String, onRef: (ContentRef?) -> Unit)
        fun openZip(onRef: (ContentRef?) -> Unit)
        fun openFolder(onRef: (ContentRef?) -> Unit)
    }

    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val main = Handler(Looper.getMainLooper())

    override val importing: Boolean get() = importsRunning.get() > 0

    override fun exportSaves(report: TransferReport) {
        pickers.createZip(SaveExport.suggestedName(System.currentTimeMillis())) { ref ->
            ref ?: return@createZip
            work(report) {
                // The list-style picker's target (the user agreed to replace an existing one): written through a part.
                val exportTag = exportTagMapper()
                val root = DataRoot.settled(app)   // on the work thread: after a move still running, one whole set
                if (FileRefs.isFile(ref)) return@work SaveExport.writeFile(root, File(FileRefs.path(ref)), exportTag).text()
                val uri = Uri.parse(ref.value)
                val summary = try {
                    val out = resolver.openOutputStream(uri, "wt") ?: error("cannot write the zip")
                    out.use { SaveExport.write(root, it, exportTag) }
                } catch (e: Throwable) {
                    deleteDocument(uri)   // never leave a half zip
                    throw e
                }
                if (summary.saves == 0 && summary.states == 0) deleteDocument(uri)   // "Nothing to export": no empty zip left
                summary.text()
            }
        }
    }

    /** Copy only: no source file is ever deleted or changed. */
    override fun importSaves(source: ImportSource, progress: (String) -> Unit, done: (ImportResult) -> Unit) {
        SaveTransferRules.importRefusal(gameScreenAlive(), syncing())?.let { return done(ImportResult.Message(it)) }   // before any picker
        when (source) {
            ImportSource.FOLDER -> pickers.openFolder { ref ->
                ref ?: return@openFolder
                importWork(progress, done) { ImportResult.Done(importTree(ref)) }   // runs now, under the picker's temporary grant (SAF)
            }
            ImportSource.ZIP -> pickers.openZip { ref ->
                ref ?: return@openZip
                importWork(progress, done) {
                    val file = if (FileRefs.isFile(ref)) File(FileRefs.path(ref)) else null
                    val open: () -> InputStream? = { if (file != null) FileInputStream(file) else resolver.openInputStream(Uri.parse(ref.value)) }
                    // The picker shows every file type (a zip filter hid everything on Samsung's picker): only a zip goes on.
                    if (!isZip(open)) return@importWork ImportResult.Message("That file is not a zip")
                    ImportResult.Done(importer().run(ZipImportFiles { open() ?: error("cannot read the zip") }))
                }
            }
            // With All files access every RetroArch root found is read; without it the user picks the RetroArch folder.
            ImportSource.RETROARCH -> if (AndroidAllFilesAccess.isGranted()) {
                importWork(progress, done) {
                    val roots = retroArchRoots()
                    if (roots.isEmpty()) return@importWork ImportResult.Message(RetroArchFolders.NOT_FOUND)
                    val importer = importer()
                    retroArchResult(roots.map { importer.run(DirImportFiles(it, RetroArchFolders.TOPS), retroArch = true) }.reduce(ImportSummary::plus))
                }
            } else pickers.openFolder { ref ->
                ref ?: return@openFolder
                importWork(progress, done) {
                    val tree = ImportTree.of(resolver, ref)
                    val start = treeStart(ref)
                    if (RetroArchFolders.TOPS.none { tree.child(start, it)?.isDirectory == true }) return@importWork ImportResult.Message(RetroArchFolders.NOT_IN_PICKED)
                    retroArchResult(importer().run(TreeImportFiles(tree, start, RetroArchFolders.TOPS), retroArch = true), RetroArchFolders.NOT_IN_PICKED)
                }
            }
        }
    }

    /** RetroArch creates empty `saves`/`states` folders on install: an empty summary says [notFound] (nothing was found there). */
    private fun retroArchResult(summary: ImportSummary, notFound: String = RetroArchFolders.NOT_FOUND): ImportResult =
        if (summary.isEmpty) ImportResult.Message(notFound) else ImportResult.Done(summary)

    /** The RetroArch roots on shared storage that hold `saves` or `states`, in [RetroArchFolders.ROOTS] order. */
    @Suppress("DEPRECATION")   // the shared storage root itself, read with All files access
    private fun retroArchRoots(): List<File> = RetroArchFolders.ROOTS.map { File(Environment.getExternalStorageDirectory(), it) }
        .filter { root -> RetroArchFolders.TOPS.any { File(root, it).isDirectory } }

    /**
     * An import's work on the transfer thread: asked again after a picker (a game may have started meanwhile); [importing]
     * from the start until [done]. Any throwable becomes "Failed: …".
     */
    private fun importWork(progress: (String) -> Unit, done: (ImportResult) -> Unit, block: () -> ImportResult) {
        SaveTransferRules.importRefusal(gameScreenAlive(), syncing())?.let { return done(ImportResult.Message(it)) }
        importsRunning.incrementAndGet()
        main.post { progress("Working…") }
        executor.execute {
            DataRoot.useBegin()   // an import writes saves: no data move starts under it (begun in the task, so always ended)
            val result = try {
                block()
            } catch (e: Throwable) {
                NxLog.e(TAG, "save import failed", e)
                ImportResult.Message(failure(e))
            } finally {
                DataRoot.useEnd(app)
            }
            main.post { importsRunning.decrementAndGet(); done(result) }
        }
    }

    /** A SAF pick is a tree URI: the walk starts at its root document. A file ref (All files access) is the folder itself. */
    private fun treeStart(root: ContentRef): ContentRef = if (FileRefs.isFile(root)) root else Uri.parse(root.value).let {
        ContentRef(DocumentsContract.buildDocumentUriUsingTree(it, DocumentsContract.getTreeDocumentId(it)).toString())
    }

    /** Every file under the picked folder [root]. */
    private fun importTree(root: ContentRef): ImportSummary =
        importer().run(TreeImportFiles(ImportTree.of(resolver, root), treeStart(root)))

    /**
     * Console folder → the handheld folder of the console's default emulator (Settings → Default emulators; GPGX_SMS's is
     * GPGX, [CoreRegistry.handheldTag]); other folders as they are.
     */
    private fun exportTagMapper(): (String) -> String {
        val defaults = AndroidAppSettings(app).defaultTags
        return { x -> CoreRegistry.consoleOf(x)?.let { CoreRegistry.handheldTag(it.defaultTagFor(defaults[it.id])) } ?: x }
    }

    /**
     * An importer writing console-folder saves in the phone's save naming, matching loose saves to the library's games,
     * into the data root once a move still running is done (called on the work thread).
     */
    private fun importer() = SaveImporter(DataRoot.settled(app), naming = AndroidAppSettings(app).saveNaming, matcher = SaveMatcher(libraryIndex()))

    /** The library's index, read off the main thread; null when there is none (every loose save is then unmatched). */
    private fun libraryIndex(): LibraryIndex? = runCatching {
        File(DataRoot.private(app), ".userdata/android/library.json").takeIf { it.isFile }?.readText()?.let(LibraryIndexCodec::decode)
    }.getOrNull()

    /** True when the stream starts like a zip (`PK\3\4`, or `PK\5\6` for an empty one). */
    private fun isZip(open: () -> InputStream?): Boolean = runCatching {
        open()?.use { s ->
            val head = ByteArray(4)
            var n = 0
            while (n < head.size) { val r = s.read(head, n, head.size - n); if (r < 0) break; n += r }
            n == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
                ((head[2] == 3.toByte() && head[3] == 4.toByte()) || (head[2] == 5.toByte() && head[3] == 6.toByte()))
        } ?: false
    }.getOrDefault(false)

    private fun deleteDocument(uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }.onFailure { NxLog.w(TAG, "could not delete $uri", it) }.getOrDefault(false)

    /** Runs [block] on the transfer thread; any throwable (a corrupt zip, a CRC error, out of memory) becomes "Failed: …". */
    private fun work(report: TransferReport, block: () -> String) {
        main.post { report("Working…", false) }
        executor.execute {
            val text = try {
                block()
            } catch (e: Throwable) {
                NxLog.e(TAG, "save transfer failed", e)
                failure(e)
            }
            main.post { report(text, true) }
        }
    }

    private fun failure(e: Throwable) = "Failed: ${e.message ?: e::class.simpleName}"

    companion object {
        private const val TAG = "SaveTransfer"
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "nx-save-transfer") }
        /** Imports started and not yet reported, process-wide (a recreated MainActivity makes a new instance). */
        private val importsRunning = AtomicInteger()

        /** Some import (from any instance) is still writing saves; Device Sync refuses meanwhile. */
        internal val anyImporting: Boolean get() = importsRunning.get() > 0
    }
}
