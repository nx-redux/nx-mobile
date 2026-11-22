package com.nxredux.mobile.storage

import android.content.Context
import android.os.Environment
import com.swordfish.libretrodroid.NxLog
import java.io.File

/**
 * The home without All files access: `Android/media/<package>/`, which the app can always write and a file manager can
 * reach (the dev build has its own through its package name). Uninstalling the app removes it.
 */
object MediaHome {
    private const val TAG = "MediaHome"
    private val SKELETON = listOf("Roms", "Bios", "Collections", "Saves")
    private const val NO_MEDIA = ".nomedia"

    /** The folder whose skeleton was last made whole: [ensure] is called per store call, so it is not checked again. */
    @Volatile private var ensured: String? = null

    /** `externalMediaDirs.first()`; its usual path while shared storage is not mounted (the entry is null then). */
    fun dir(context: Context): File =
        context.externalMediaDirs.firstOrNull()
            ?: File(Environment.getExternalStorageDirectory(), "Android/media/${context.packageName}")

    /** [dir], looked up once per process (the media dirs are a system call): the path never changes while the app runs. */
    @Volatile private var cached: File? = null
    fun cachedDir(context: Context): File = cached ?: dir(context).also { cached = it }

    /** True when [file] is [dir] or inside it: the app's own folder, readable and writable without All files access. */
    fun owns(dir: File, file: File): Boolean {
        val f = file.absoluteFile.normalize().path
        val d = dir.absoluteFile.normalize().path.trimEnd('/')
        return f == d || f.startsWith("$d/")
    }

    /** Makes `Roms/`, `Bios/`, `Collections/`, `Saves/` and an empty `.nomedia` (so the gallery skips box art) in [dir]. */
    fun ensure(dir: File) {
        if (ensured == dir.path) return
        try {
            SKELETON.forEach { File(dir, it).mkdirs() }
            val noMedia = File(dir, NO_MEDIA)
            if (!noMedia.exists()) noMedia.createNewFile()
            if (SKELETON.all { File(dir, it).isDirectory } && noMedia.isFile) ensured = dir.path
        } catch (e: Exception) {
            NxLog.w(TAG, "cannot make ${dir.path}", e)   // shared storage not mounted: the next call tries again
        }
    }
}
