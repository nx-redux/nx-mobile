package com.nxredux.mobile.bios

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.nxredux.mobile.storage.PickedRefs
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
    override val running: Boolean get() = anyRunning

    override fun import(source: BiosSource, progress: (String) -> Unit, done: (BiosIntakeResult) -> Unit) {
        refusal()?.let { return done(BiosIntakeResult.Message(it)) }
        when (source) {
            BiosSource.FOLDER -> pickers.openFolder { ref -> ref ?: return@openFolder; work(progress, done) { treeFiles(ref) } }
            BiosSource.FILES -> pickers.openFiles { refs -> if (refs.isNotEmpty()) work(progress, done) { pickedFiles(refs) } }
            BiosSource.ZIP -> pickers.openZip { ref ->
                ref ?: return@openZip
                val open = PickedRefs.opener(resolver, ref)
                // The picker shows every file type: only a zip goes on.
                if (!looksLikeZip(open)) return@openZip done(BiosIntakeResult.Message("That file is not a zip"))
                work(progress, done) { ZipImportFiles { open() ?: error("cannot read the zip") } }
            }
            // With All files access every RetroArch system folder found is read; without it the user picks RetroArch's folder.
            BiosSource.RETROARCH -> if (AndroidAllFilesAccess.isGranted()) {
                val roots = PickedRefs.retroArchRoots(listOf(SYSTEM))
                if (roots.isEmpty()) return done(BiosIntakeResult.Message(NO_RETROARCH))
                work(progress, done) { concat(roots.map { DirImportFiles(it, listOf(SYSTEM)) }) }
            } else pickers.openFolder { ref ->
                ref ?: return@openFolder
                val tree = ImportTree.of(resolver, ref)
                if (tree.child(PickedRefs.treeStart(ref), SYSTEM)?.isDirectory != true) return@openFolder done(BiosIntakeResult.Message(NOT_RETROARCH))
                work(progress, done) { TreeImportFiles(tree, PickedRefs.treeStart(ref), listOf(SYSTEM)) }
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
        active.incrementAndGet()
        main.post { progress("Working…") }
        executor.execute {
            val result = try {
                run(files(), HomeBiosFolder.of(resolver, home))
            } catch (e: Throwable) {
                NxLog.e(TAG, "BIOS import failed", e)
                BiosIntakeResult.Message("Failed: ${e.message ?: e::class.simpleName}")
            }
            main.post { active.decrementAndGet(); done(result) }
        }
    }

    private fun run(files: ImportFiles, folder: HomeBiosFolder): BiosIntakeResult {
        val temp = File(app.cacheDir, "bios-intake").apply { deleteRecursively(); mkdirs() }
        try {
            val picked = mutableListOf<PickedBios>()
            val tempOf = HashMap<String, File>()
            val unread = mutableListOf<BiosSkip>()
            val known = KnownBiosAsset.get(app)
            var other = 0
            for (e in files.entries()) {
                if (!BiosIntakePlanner.isCandidate(e.path)) continue
                val base = biosBaseName(e.path)
                // A size no listed file has and an unknown name: no BIOS, never opened (a whole ROM folder picked).
                if (!known.shouldRead(e.size, base)) { other++; continue }
                val named = known.byName(base).isNotEmpty()
                val copy = File(temp, tempOf.size.toString() + "-" + picked.size)
                val read = try {
                    // An unknown name of unknown size is read no further than the largest listed file.
                    Md5.copy(e.open(), copy.outputStream(), if (named) Long.MAX_VALUE else known.maxSize)
                } catch (t: Throwable) {
                    NxLog.w(TAG, "could not read ${e.path}", t)
                    copy.delete(); unread += BiosSkip(e.path, "Could not read"); continue
                }
                if (read == null) { copy.delete(); other++; continue }
                val (md5, size) = read
                if (known.byMd5(md5).isEmpty() && !named) {
                    copy.delete()   // no BIOS: counted, not kept
                } else tempOf[e.path] = copy
                picked += PickedBios(e.path, size, md5)
            }
            val planned = BiosIntakePlanner.plan(picked, known, object : ExistingBios {
                override fun md5(console: String, name: String) = folder.md5(console, name)
                override fun anyWithMd5(console: String, md5: String) = folder.anyWithMd5(console, md5)
            })
            val plan = planned.copy(otherFiles = planned.otherFiles + other)
            val failed = unread + plan.writes.filter { it.needsWrite }.mapNotNull { w ->
                val src = tempOf[w.pickedPath]
                val ok = src != null && folder.write(w.console, w.name) { FileInputStream(src) }
                if (ok) null else BiosSkip(w.pickedPath, "Could not write", w.console, w.name)
            }
            return BiosIntakeSummary.resultOf(plan, failed)
        } finally {
            temp.deleteRecursively()
        }
    }


    private fun treeFiles(ref: ContentRef): ImportFiles = TreeImportFiles(ImportTree.of(resolver, ref), PickedRefs.treeStart(ref))

    /** Single documents from the multi-file picker, each under its display name. */
    private fun pickedFiles(refs: List<ContentRef>): ImportFiles = object : ImportFiles {
        override fun entries(): Sequence<ImportEntry> = refs.asSequence().map { ref ->
            val uri = Uri.parse(ref.value)
            val name = if (FileRefs.isFile(ref)) File(FileRefs.path(ref)).name else SafTree(resolver).displayName(uri) ?: uri.lastPathSegment.orEmpty()
            object : ImportEntry {
                override val path = name
                override val mtime: Long? = null
                override val size: Long? = null
                override fun open(): InputStream = PickedRefs.opener(resolver, ref)() ?: error("cannot read $name")
            }
        }
    }

    private fun concat(all: List<ImportFiles>): ImportFiles = object : ImportFiles {
        override fun entries(): Sequence<ImportEntry> = all.asSequence().flatMap { it.entries() }
    }



    companion object {
        /** One count for every instance (an activity recreated mid-import): Device Sync waits while any runs. */
        private val active = AtomicInteger()
        // One thread for every instance: runs never overlap, so each may clear the shared temp folder first.
        private val executor = Executors.newSingleThreadExecutor()
        val anyRunning: Boolean get() = active.get() > 0

        private const val TAG = "BiosIntake"
        private const val SYSTEM = "system"
        private const val NO_HOME = "Set a home folder first"
        private const val ACCESS_OFF = "Turn on All files access to import BIOS"
        private const val NO_RETROARCH = "No RetroArch system folder found"
        private const val NOT_RETROARCH = "Pick the RetroArch folder (the one with system inside)"
    }
}
