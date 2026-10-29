package com.nxredux.mobile.achievements

import android.content.Context
import android.os.ParcelFileDescriptor
import com.nxredux.mobile.library.CompanionLookup
import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.Archives
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.RomCache
import com.nxredux.mobile.storage.AndroidRefs
import com.swordfish.libretrodroid.LibretroAchievements
import com.swordfish.libretrodroid.VirtualFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the game (and its sheet's companions) as descriptors (SAF or file refs, [AndroidRefs]) and hashes them in the
 * fork. A zip or 7z is unpacked first with the launch rule (Archives.extract into the launch cache, which RomCache
 * sweeps), and the unpacked ROM is hashed, its name refining the console. An archive the launch would refuse (no ROM,
 * several, password, unreadable, cannot be opened, or a ROM the tag's core does not run) is unidentified (null). The
 * exception is an archive the tag's core loads as is ([CoreRegistry.loadsArchive]: fbneo's set zip/7z on FBN, whose
 * console is Arcade), which is not unpacked, as at launch: rcheevos hashes the archive itself. A DC zip/7z (a
 * NAOMI/Atomiswave set or a zipped disc) is unidentified here, before any unpack ([RaConsoles.consoleId] has no
 * Dreamcast archive); the launch hashes a zipped disc it unpacks by the inner name (GameActivity's prepareAchievements).
 */
class AndroidRaHasher(private val context: Context, private val source: RomsSource) : RaHasher {
    private val refs = AndroidRefs(context.contentResolver)

    init {
        // Dolphin's disc reader hashes Wii/GameCube images in any format. Both the library sweep and the launch build a
        // hasher, so this is the one place that runs before either hashes; the call is idempotent.
        LibretroAchievements.setDiscLibrary(context.applicationInfo.nativeLibraryDir + "/libdolphin_libretro_android.so")
    }

    override suspend fun hash(tag: String, ref: String, fileName: String, folder: String): String? {
        // A tag RetroAchievements does not know is unidentified before any unpack.
        RaConsoles.consoleId(tag, fileName) ?: return null
        // The 2-argument loadsArchive (no NaomiSets table) is enough: it differs from the launch's 3-argument form only on DC,
        // whose archives consoleId has already turned away above.
        if (Names.isArchive(fileName) && !CoreRegistry.loadsArchive(tag, fileName)) {
            val core = CoreRegistry.forTag(tag)
            // Extract, hash and release in one IO block: the extraction is held until hashed (so a sweep, after a prefetch
            // or at app start, cannot delete it in between), and a cancel that lands during the blocking extract still
            // reaches the release (withContext would otherwise throw with the hold taken and the result discarded).
            return withContext(Dispatchers.IO) {
                val inner = try {
                    // Named as the launch names it, so the two share one extraction.
                    Archives.extract(context, ContentRef(ref), fileName, core?.extensions ?: CoreRegistry.coreExtensions, core?.savesNamedAfterContent == true).file
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext null
                }
                try {
                    // The launch refuses a lone entry its core does not run (GameActivity's mismatch check); so does the hash.
                    if (core != null && Names.extensionOf(inner.name) !in core.extensions) null else hashUnpacked(tag, inner)
                } finally {
                    inner.parentFile?.let { RomCache.release(context, it.name) }
                }
            }
        }
        return hashDirect(tag, ref, fileName, folder)
    }

    /**
     * The file's size and last-modified time (a document's from its provider). Null when either is unknown: a ROM is often
     * a power-of-two size, so a size alone would take a replaced dump for the old one.
     */
    override suspend fun stamp(ref: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = ContentRef(ref)
            val size = refs.size(r) ?: return@withContext null
            val modified = refs.lastModified(r) ?: return@withContext null
            "$size:$modified"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Hashes an already unpacked ROM (the launch cache's inner file); the console is refined by its name. */
    suspend fun hashUnpacked(tag: String, inner: File): String? = withContext(Dispatchers.IO) {
        val consoleId = RaConsoles.consoleId(tag, inner.name) ?: return@withContext null
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = ParcelFileDescriptor.open(inner, ParcelFileDescriptor.MODE_READ_ONLY)
            LibretroAchievements.hash(consoleId, listOf(VirtualFile("/rom/${inner.name}", pfd)))   // dups the descriptor
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            pfd?.let { runCatching { it.close() } }
        }
    }

    private suspend fun hashDirect(tag: String, ref: String, fileName: String, folder: String): String? = withContext(Dispatchers.IO) {
        val consoleId = RaConsoles.consoleId(tag, fileName) ?: return@withContext null
        val opened = ArrayList<ParcelFileDescriptor>()
        try {
            val files = ArrayList<VirtualFile>()
            val pfd = refs.openFd(ContentRef(ref), "r") ?: return@withContext null
            opened += pfd
            files += VirtualFile("/rom/$fileName", pfd)
            if (DiscSheet.isSheet(fileName)) {
                val lookup = DiscSheet.companions(source, ContentRef(folder), fileName)
                if (lookup is CompanionLookup.Found) for (c in lookup.companions) {
                    val cp = refs.openFd(c.ref, "r") ?: continue
                    opened += cp; files += VirtualFile("/rom/${c.fileName}", cp)
                }
            }
            LibretroAchievements.hash(consoleId, files)   // dups the descriptors; ours are closed below
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            opened.forEach { runCatching { it.close() } }
        }
    }
}
