package com.nxredux.mobile.netplay

import android.content.Context
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.netplay.cablelink.LinkRomCache
import com.nxredux.mobile.netplay.cablelink.Sha1
import com.nxredux.mobile.netplay.wizard.CableLinkFiles
import com.nxredux.mobile.netplay.wizard.CableLinkOffer
import com.nxredux.mobile.runner.BiosFolders
import com.nxredux.mobile.runner.BiosImport
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.PairSaveFiles
import com.nxredux.mobile.runner.SystemFiles
import com.nxredux.mobile.runner.writeAtomically
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.nxredux.mobile.storage.AndroidRefs
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.LinkRomCaches
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.findSave
import com.nxredux.mobile.storage.readSaveDecoded
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Cable Link over Wi-Fi on Android: this side's offer (its ROM's hash, its GBA BIOS's hash) and the files the exchange
 * reads and writes ([AndroidCableLinkFiles]).
 */
internal object AndroidCableLink {
    /** mGBA's BIOS file name in the system dir, which the pair core loads when both phones have the same one. */
    private const val GBA_BIOS = "gba_bios.bin"
    private const val GBA_BIOS_SIZE = 16 * 1024L
    private const val ROM_MAX = 32L * 1024 * 1024
    private const val CHUNK = 64 * 1024

    suspend fun offer(
        context: Context, sdRoot: File, tag: String, romFileName: String, content: ContentRef,
        findLibraryRom: suspend (hash: String, sizeBytes: Long) -> String?,
    ): CableLinkOffer = withContext(Dispatchers.IO) {
        // The core takes the other GBA's ROM by path, and the library lookup skips archives: a bare ROM only.
        if (Names.isArchive(romFileName)) throw CableLinkRefused(NetplayText.CABLE_LINK_ARCHIVE)
        val refs = AndroidRefs(context.contentResolver)
        val sha = Sha1()
        var size = 0L
        (refs.openInput(content) ?: throw IOException("cannot open $romFileName")).use { input ->
            read(input) { buf, n ->
                size += n
                if (size > ROM_MAX) throw CableLinkRefused(NetplayText.CABLE_LINK_READ_FAILED)
                sha.update(buf, 0, n)
            }
        }
        if (size == 0L) throw CableLinkRefused(NetplayText.CABLE_LINK_READ_FAILED)
        val files = AndroidCableLinkFiles(context, sdRoot, tag, romFileName, content, findLibraryRom)
        CableLinkOffer(sha.hex(), size, biosHash(context, tag), files, clock = { System.currentTimeMillis() / 1000 })
    }

    /**
     * The hash of the BIOS the game launch would give mGBA (Bios/GBA imported into the system dir first, as the launch
     * does); null when there is none or it isn't a GBA BIOS's size.
     */
    private fun biosHash(context: Context, tag: String): String? = runCatching {
        val systemDir = SystemFiles.ensure(context)
        val biosDir = BiosFolders.subdir(tag).let { if (it.isEmpty()) systemDir else File(systemDir, it) }
        BiosImport.sync(context, AndroidHomeLocator(context).current(), BiosFolders.forTag(tag), biosDir, listOf(GBA_BIOS))
        val bios = File(biosDir, GBA_BIOS).takeIf { it.isFile && it.length() == GBA_BIOS_SIZE } ?: return@runCatching null
        val sha = Sha1()
        bios.inputStream().use { input -> read(input) { buf, n -> sha.update(buf, 0, n) } }
        sha.hex()
    }.onFailure { NxLog.w(TAG, "GBA BIOS hash failed; offering none", it) }.getOrNull()

    private inline fun read(input: InputStream, block: (ByteArray, Int) -> Unit) {
        val buf = ByteArray(CHUNK)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n > 0) block(buf, n)
        }
    }

    const val TAG = "NxNetplay"
}

/**
 * The exchange's files for the game [romFileName] ([content]) in folder [tag]: ROMs found in the link ROM cache or the
 * library ([findLibraryRom]; a document is copied into the cache so the core gets a path), this side's save (a Cable
 * Link session's unpromoted checkpoint first: the next launch makes it the real save, so the other phone must boot that
 * one too), and the other side's save in a fresh session dir in app storage.
 */
internal class AndroidCableLinkFiles(
    private val context: Context,
    private val sdRoot: File,
    private val tag: String,
    private val romFileName: String,
    private val content: ContentRef,
    private val findLibraryRom: suspend (hash: String, sizeBytes: Long) -> String?,
) : CableLinkFiles {
    private val refs = AndroidRefs(context.contentResolver)
    override val cache: LinkRomCache = LinkRomCaches.get(context)

    override suspend fun findRom(hash: String, sizeBytes: Long): String? {
        cache.find(hash)?.let { return it }
        val ref = ContentRef(findLibraryRom(hash, sizeBytes) ?: return null)
        if (FileRefs.isFile(ref)) FileRefs.path(ref).takeIf { File(it).canRead() }?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.put(hash) { sink ->
                (refs.openInput(ref) ?: throw IOException("cannot open the library ROM")).use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n > 0) sink.write(buf, 0, n)
                    }
                }
            }
        }
    }

    override suspend fun readOwnRom(chunk: suspend (ByteArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        (refs.openInput(content) ?: throw IOException("cannot open $romFileName")).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) chunk(buf, n)
            }
        }
    }

    override suspend fun ownSave(): ByteArray? = withContext(Dispatchers.IO) {
        val paths = NxPaths(tag, "", romFileName, AndroidAppSettings(context).saveNaming)
        PairSaveFiles.pending(context.filesDir, paths.saveRam) ?: paths.findSave(sdRoot)?.readSaveDecoded()
    }

    override suspend fun writeOtherSave(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "cablelink/session")
        dir.deleteRecursively()
        File(dir, "other.sav").also { it.writeAtomically(bytes) }.absolutePath
    }

    override fun log(message: String) = NxLog.i(AndroidCableLink.TAG, message)
}
