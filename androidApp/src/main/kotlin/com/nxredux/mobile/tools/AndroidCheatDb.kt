package com.nxredux.mobile.tools

import android.content.Context
import android.os.StatFs
import com.nxredux.mobile.cheats.db.CheatDbArchive
import com.nxredux.mobile.cheats.db.CheatDbException
import com.nxredux.mobile.cheats.db.CheatDbInstall
import com.nxredux.mobile.cheats.db.CheatDbManifest
import com.nxredux.mobile.cheats.db.CheatDbPlatform
import com.nxredux.mobile.cheats.db.CheatDbSource
import com.nxredux.mobile.runner.writeAtomically
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Cheats & Patches tool on Android: HttpURLConnection into the cache, java.util.zip into `Cheats/<TAG>/` (and PCSX2's
 * patches into `Cheats/<TAG>/Patches/`) under [CheatsRoot]. That root is a real path in every build (the Play build's
 * home is `Android/media/<package>/`), so the PS2 core reads the same folders straight from its save directory.
 */
class AndroidCheatDb(context: Context) : CheatDbPlatform {
    private val app = context.applicationContext
    // Read at each use: the home folder or All files access can change while the process lives.
    private val sdRoot: File get() = CheatsRoot.of(app)
    private val cacheDir: File get() = File(app.cacheDir, "cheatdb").apply { mkdirs() }

    override fun freeBytes(): Long = runCatching { StatFs(sdRoot.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    override suspend fun remoteStamp(source: CheatDbSource): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = open("HEAD", source)
            // A present-but-blank header is as good as none: the manifest cannot round-trip an empty stamp.
            try {
                if (c.responseCode in 200..299) c.getHeaderField("Last-Modified")?.takeIf { it.isNotBlank() } else null
            } finally { c.disconnect() }
        }.onFailure { if (it is CancellationException) throw it; NxLog.w(TAG, "update check failed", it) }.getOrNull()
    }

    override suspend fun download(source: CheatDbSource, onProgress: (Long, Long) -> Unit): CheatDbArchive = withContext(Dispatchers.IO) {
        // Each attempt owns its file names, so a cancelled attempt still unwinding can never delete a newer
        // attempt's files. Sweep what earlier attempts or a killed process left of this source (another source's
        // archive may be waiting for the install): unlinking a file an old attempt still holds open is harmless,
        // that attempt is cancelled and only ever touches its own names.
        val prefix = "${source.name.lowercase()}."
        cacheDir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
        val id = Random.nextInt().toUInt().toString(16)
        val partFile = File(cacheDir, "$prefix$id.zip.part")
        val zipFile = File(cacheDir, "$prefix$id.zip")
        val c = try { open("GET", source) } catch (e: IOException) { throw CheatDbException(CheatDbException.Kind.NETWORK, e) }
        // A read blocked on a stalled socket never sees ensureActive(), so a cancel closes the socket from this
        // watcher and the read throws at once. (Job.invokeOnCompletion would only fire after the body returns.)
        // UNDISPATCHED: the body reaches awaitCancellation() before launch returns, so a cancel can never skip the finally.
        val watchdog = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { c.disconnect() } }
        try {
            if (c.responseCode !in 200..299) throw CheatDbException(CheatDbException.Kind.NETWORK, IOException("HTTP ${c.responseCode}"))
            val total = c.contentLengthLong   // -1 when the server sends no Content-Length
            val stamp = c.getHeaderField("Last-Modified")?.takeIf { it.isNotBlank() }
            var done = 0L
            var reported = 0L
            c.inputStream.use { input ->
                partFile.outputStream().use { out ->
                    val buffer = ByteArray(CHUNK)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        // Every callback becomes a main-thread state update, so report per 256 KB, not per chunk.
                        if (done - reported >= PROGRESS_STEP) { reported = done; onProgress(done, total) }
                    }
                    out.fd.sync()
                }
            }
            onProgress(done, total)   // the final done == total always reaches the UI
            currentCoroutineContext().ensureActive()   // a cancelled attempt must not publish an archive
            if (!partFile.renameTo(zipFile)) throw IOException("rename failed")
            CheatDbArchive(zipFile.path, stamp, source)
        } catch (e: CheatDbException) {
            partFile.delete(); throw e
        } catch (e: IOException) {
            partFile.delete()
            currentCoroutineContext().ensureActive()   // the watchdog's disconnect surfaces as cancellation, not NETWORK
            throw CheatDbException(CheatDbException.Kind.NETWORK, e)
        } catch (e: Throwable) {
            partFile.delete(); throw e   // cancellation included: never leave a .part behind
        } finally {
            watchdog.cancel()
            c.disconnect()
        }
    }

    override suspend fun install(installs: List<CheatDbInstall>, onProgress: (String, Int, Int) -> Unit): List<String> = withContext(Dispatchers.IO) {
        CheatDbFiles.install(CheatsRoot.settled(app), installs, onProgress)   // one root for every file: a move must not split the install
    }

    override suspend fun discard(archive: CheatDbArchive) = withContext(Dispatchers.IO) { File(archive.path).delete(); Unit }

    override suspend fun readManifest(): String? = withContext(Dispatchers.IO) {
        File(sdRoot, CheatDbManifest.PATH).takeIf { it.exists() }?.let { f -> runCatching { f.readText() }.getOrNull() }
    }

    override suspend fun writeManifest(text: String) = withContext(Dispatchers.IO) {
        try { File(sdRoot, CheatDbManifest.PATH).writeAtomically(text.encodeToByteArray()) } catch (e: IOException) { throw CheatDbException(CheatDbException.Kind.STORAGE, e) }
    }

    override suspend fun deleteInstalled(files: List<String>) = withContext(Dispatchers.IO) {
        deleteFiles(files)
        File(sdRoot, CheatDbManifest.PATH).delete()
        Unit
    }

    override suspend fun deleteFiles(files: List<String>) = withContext(Dispatchers.IO) {
        val root = sdRoot
        val targets = files.map { File(root, it) }
        for ((rel, f) in files.zip(targets)) {
            if (f.exists() && !f.delete()) NxLog.w(TAG, "could not delete $rel")
        }
        CheatDbFiles.pruneFolders(targets, root)
    }

    /** GitHub's release link answers with a redirect to its asset host (https to https, followed); buildbot answers directly. */
    private fun open(method: String, source: CheatDbSource): HttpURLConnection = (URL(source.url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
    }

    private companion object {
        const val TAG = "CheatDb"
        const val CHUNK = 64 * 1024
        const val PROGRESS_STEP = 256 * 1024L
    }
}

/** The archive and folder work of [AndroidCheatDb], on a given root (no Context: unit-tested on a temp folder). */
internal object CheatDbFiles {
    private const val TAG = "CheatDb"

    /** [CheatDbPlatform.install] into [sdRoot]. */
    suspend fun install(sdRoot: File, installs: List<CheatDbInstall>, onProgress: (String, Int, Int) -> Unit): List<String> {
        val written = ArrayList<String>()
        // Targets that did not exist before this attempt wrote them: the only files a failure may take back.
        val created = ArrayList<File>()
        val count = installs.sumOf { it.folders.size }
        var index = 0
        try {
            for (install in installs) {
                val source = install.archive.source
                val zip = try { ZipFile(install.archive.path) } catch (e: ZipException) { throw CheatDbException(CheatDbException.Kind.CORRUPT, e) } catch (e: IOException) { throw CheatDbException(CheatDbException.Kind.CORRUPT, e) }
                zip.use {
                    val entries = zip.entries().toList().filter { !it.isDirectory }
                    for ((tag, tagFolders) in install.folders) {
                        onProgress(tag, ++index, count)
                        val taken = HashSet<String>()   // a name in two of the tag's folders: the first folder's file stays
                        for (folder in tagFolders) {
                            val prefix = if (folder.isEmpty()) "" else "$folder/"   // "": the archive's top level
                            for (entry in entries) {
                                currentCoroutineContext().ensureActive()
                                val name = entry.name
                                if (!name.startsWith(prefix)) continue
                                val base = name.removePrefix(prefix)
                                if (base.contains('/') || !base.endsWith(source.extension, ignoreCase = true)) continue   // flat files only
                                if (!taken.add(base)) continue
                                val relative = source.target(tag, base)
                                val target = File(sdRoot, relative)
                                val existed = target.exists()
                                val bytes = try {
                                    zip.getInputStream(entry).use { it.readBytes() }
                                } catch (e: ZipException) {
                                    throw CheatDbException(CheatDbException.Kind.CORRUPT, e)   // before IOException: a ZipException is one
                                } catch (e: IOException) {
                                    throw CheatDbException(CheatDbException.Kind.STORAGE, e)
                                }
                                try {
                                    target.writeAtomically(bytes)
                                } catch (e: IOException) {
                                    throw CheatDbException(CheatDbException.Kind.STORAGE, e)
                                }
                                if (!existed) created += target
                                written += relative
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // Cancellation included. Pre-existing files (a previous install, or same-named user files) stay.
            rollBack(created, sdRoot)
            throw e
        }
        return written
    }

    /** Best-effort delete of [files], then the folders under `Cheats/` they emptied. */
    private fun rollBack(files: List<File>, sdRoot: File) {
        for (f in files) {
            if (f.exists() && !f.delete()) NxLog.w(TAG, "rollback could not delete ${f.path}")
        }
        pruneFolders(files, sdRoot)
    }

    /**
     * Removes the folders of [files] that are empty now, and their emptied parents up to (not including) `Cheats/`:
     * `Cheats/<TAG>/Patches/`, then `Cheats/<TAG>/`. Delete only succeeds when empty: the user's own files keep a folder.
     */
    fun pruneFolders(files: Collection<File>, sdRoot: File) {
        val stop = File(sdRoot, "Cheats")
        val dirs = LinkedHashSet<File>()
        for (f in files) {
            var dir = f.parentFile
            while (dir != null && dir != stop && dir.path.startsWith(stop.path + File.separator)) { dirs += dir; dir = dir.parentFile }
        }
        dirs.sortedByDescending { it.path.length }.forEach { it.delete() }   // deepest first
    }
}
