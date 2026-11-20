package com.nxredux.mobile

import android.content.Context
import com.nxredux.mobile.runner.DsMenu
import com.swordfish.libretrodroid.NxLog
import java.io.File

/**
 * The DS Download Play row's art, shipped in assets/nds-menu/ and copied once per install or update to a private folder:
 * every art path in the app reads a file ref, and nothing lands in the user's folders.
 */
internal object DsMenuArt {
    private const val ASSETS = "nds-menu"
    private const val STAMP = ".stamp"

    /** The folder holding [DsMenu.ART_FILES], or null when the copy failed (the row then shows no art). */
    fun install(context: Context): String? = runCatching {
        val dir = File(context.filesDir, ASSETS)
        val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        val stampFile = File(dir, STAMP)
        val current = stampFile.takeIf { it.isFile }?.readText() == stamp && DsMenu.ART_FILES.all { File(dir, it).isFile }
        if (!current) {
            dir.mkdirs()
            for (name in DsMenu.ART_FILES) {
                val tmp = File(dir, "$name.tmp")
                context.assets.open("$ASSETS/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!tmp.renameTo(File(dir, name))) error("could not place $name")
            }
            stampFile.writeText(stamp)
        }
        dir.absolutePath
    }.onFailure { NxLog.w("DsMenuArt", "copying the DS Download Play art failed", it) }.getOrNull()
}
