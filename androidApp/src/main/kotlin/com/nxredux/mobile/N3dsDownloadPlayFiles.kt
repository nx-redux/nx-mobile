package com.nxredux.mobile

import android.content.Context
import com.nxredux.mobile.runner.N3dsDownloadPlay
import com.swordfish.libretrodroid.NxLog
import java.io.File

/**
 * 3DS Download Play's client and the row's art, shipped in assets/n3ds-dlp/ and copied once per install or update to a
 * private folder: the core loads the client by real path and the art reads file refs.
 */
internal object N3dsDownloadPlayFiles {
    private const val STAMP = ".stamp"

    /** The folder when this install's copy is already there; null when it needs [copy]. */
    fun current(context: Context): String? = runCatching {
        val dir = File(context.filesDir, N3dsDownloadPlay.ASSET_DIR)
        dir.absolutePath.takeIf {
            File(dir, STAMP).takeIf { it.isFile }?.readText() == stamp(context) && File(dir, N3dsDownloadPlay.APP_FILE).isFile
        }
    }.getOrNull()

    /** Copies every file of the assets folder (off the main thread); the folder, or null when the copy failed. */
    fun copy(context: Context): String? = runCatching {
        val dir = File(context.filesDir, N3dsDownloadPlay.ASSET_DIR).apply { mkdirs() }
        for (name in context.assets.list(N3dsDownloadPlay.ASSET_DIR).orEmpty()) {
            val tmp = File(dir, "$name.tmp")
            context.assets.open("${N3dsDownloadPlay.ASSET_DIR}/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(File(dir, name))) error("could not place $name")
        }
        File(dir, STAMP).writeText(stamp(context))
        dir.absolutePath
    }.onFailure { NxLog.w("N3dsDownloadPlay", "copying the 3DS Download Play files failed", it) }.getOrNull()

    private fun stamp(context: Context) = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
}
