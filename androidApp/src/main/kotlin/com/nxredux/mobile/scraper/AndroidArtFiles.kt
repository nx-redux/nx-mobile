package com.nxredux.mobile.scraper

import android.content.Context
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.writeAtomically
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Android games console's pictures at `filesDir/android-art/`, by package (the app id before `/`): the screenshot
 * `<package>.png`, the 2D box `<package>.box2d.png`, the wheel `<package>.wheel.png`, the app-built mix
 * `<package>.mix.png`, and an empty `<package>.hero` while the screenshot is a SteamGridDB hero. App-private: never the home tree, which holds only ROM data. A stored
 * one's ref is a `file://` uri (Coil's model) with the file's version, `?v=<lastModified>`: a replaced picture gets a new
 * ref, so Coil's memory cache (keyed by the uri) loads it afresh and the console, compared by refs, is rebuilt. Coil
 * opens the uri's path, without the query.
 */
class AndroidArtFiles(private val dir: File) : AndroidArtStore {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, DIR))

    companion object {
        const val DIR = "android-art"
        private const val TAG = "AndroidArtFiles"
        private const val HERO = ".hero"
        private val PACKAGE = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")
        private fun suffix(slot: ArtSlot): String? = when (slot) {
            ArtSlot.SCREENSHOT -> ".png"
            ArtSlot.BOX_2D -> ".box2d.png"
            ArtSlot.WHEEL -> ".wheel.png"
            ArtSlot.MIX -> ".mix.png"
            else -> null
        }
    }

    /** [appId]'s package, or null for an id whose package is not a package name (nothing outside [dir] is ever named). */
    private fun pkg(appId: String): String? = appId.substringBefore('/').takeIf { PACKAGE.matches(it) }

    /** [appId]'s file for [slot]; null for a slot the store has no picture for, or a bad id ([pkg]). */
    private fun file(appId: String, slot: ArtSlot): File? {
        val end = suffix(slot) ?: return null
        return pkg(appId)?.let { File(dir, it + end) }
    }

    private fun heroFile(appId: String): File? = pkg(appId)?.let { File(dir, it + HERO) }

    override fun art(appId: String, slot: ArtSlot): ContentRef? =
        file(appId, slot)?.takeIf { it.isFile }?.let { ContentRef("file://${it.absolutePath}?v=${it.lastModified()}") }

    override fun heroShot(appId: String): Boolean = heroFile(appId)?.isFile == true

    override suspend fun write(appId: String, slot: ArtSlot, bytes: ByteArray, hero: Boolean): ContentRef? = withContext(Dispatchers.IO) {
        val f = file(appId, slot) ?: return@withContext null
        runCatching { f.writeAtomically(bytes) }
            .onFailure { NxLog.w(TAG, "write failed (${it::class.java.simpleName})") }
            .getOrNull() ?: return@withContext null
        if (slot == ArtSlot.SCREENSHOT) heroFile(appId)?.let { mark ->
            runCatching { if (hero) mark.createNewFile() else mark.delete() }
                .onFailure { NxLog.w(TAG, "hero mark failed (${it::class.java.simpleName})") }
        }
        art(appId, slot)
    }

    override suspend fun read(appId: String, slot: ArtSlot): ByteArray? = withContext(Dispatchers.IO) {
        val f = file(appId, slot)?.takeIf { it.isFile } ?: return@withContext null
        runCatching { f.readBytes() }.onFailure { NxLog.w(TAG, "read failed (${it::class.java.simpleName})") }.getOrNull()
    }

    /**
     * Deletes every file in [dir] (hero marks, and a temp file left by an interrupted write, too); counts only the
     * pictures deleted (`.png`: screenshots, 2D boxes, wheels and mixes).
     */
    override suspend fun deleteAll(): Int = withContext(Dispatchers.IO) {
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        files.count { f ->
            val gone = runCatching { f.delete() }.getOrDefault(false)
            if (!gone) NxLog.w(TAG, "delete failed")
            gone && f.name.endsWith(".png")
        }
    }
}
