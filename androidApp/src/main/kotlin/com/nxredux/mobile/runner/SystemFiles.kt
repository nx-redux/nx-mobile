package com.nxredux.mobile.runner

import android.content.Context
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Cores read BIOS/asset files from an app-owned system directory. On the target phone the app cannot read
 * files pushed by adb into its external dir, so everything is copied by the app itself, from the APK's
 * assets/system/, once per app update (stamped with lastUpdateTime).
 *
 * MainActivity pre-warms it on a background thread (as it does the overlays and shaders) and every caller goes
 * through one lock (in-process, and a file lock for the game process), so a game started meanwhile waits for that copy instead of running a second one.
 * Not [BundledAssets.sync]'s whole-tree swap: this dir is not the APK's alone. BiosImport copies the user's
 * `Bios/<TAG>/` files into it (by path, over bundled ones) and cores write their own files here (flycast's
 * `dc/` nvmem and flash, PPSSPP's), and a swap would drop all of that. So each bundled file is built beside its
 * target and renamed over it (a core or a reader sees the old file or the new, never half of one), and the stamp
 * is written last: a copy cut short (killed, disk full) has no stamp and is redone on the next call.
 */
object SystemFiles {
    private const val TAG = "SystemFiles"
    private const val STAMP = ".stamp"
    /** In filesDir beside the system dir: the cross-process lock on [sync]. */
    private const val LOCK_FILE = "system.lock"
    private val lock = Any()

    /** Read side of the bundled tree; the APK's assets in the app, a fake in tests. */
    internal interface Source {
        /** Children of [path] (a directory); empty for a file or a missing path. */
        fun list(path: String): List<String>
        fun open(path: String): InputStream
    }

    /**
     * The system dir, brought up to date with this APK's assets/system/ first if needed. Blocking I/O: call off the
     * main thread. Never throws: on a failed copy the dir is returned as it is (the next call retries).
     */
    fun ensure(context: Context): File {
        val dir = File(context.filesDir, "system")
        val current = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        val assets = context.assets
        val t0 = System.currentTimeMillis()
        runCatching {
            val copied = sync(dir, current, object : Source {
                override fun list(path: String) = assets.list(path).orEmpty().toList()
                override fun open(path: String): InputStream = assets.open(path)
            })
            // Includes any wait on a copy another thread was running (MainActivity's pre-warm, when a game got here first).
            if (copied) NxLog.i(TAG, "copied the bundled system files ($current) in ${System.currentTimeMillis() - t0} ms")
        }.onFailure { NxLog.w(TAG, "could not copy the bundled system files; will retry next launch", it) }
        return dir
    }

    /**
     * Copies [source]'s `system` tree into [dir] unless [dir]'s stamp already says [current]. Returns true when a
     * copy ran. Files in [dir] that the bundle does not name are left alone (imported BIOS, core-written data). No
     * Android calls, so it is unit-tested.
     */
    internal fun sync(dir: File, current: String, source: Source): Boolean = synchronized(lock) {
        dir.mkdirs()
        // The in-process lock does not span the menu and the game process, which both run this: a file lock beside the dir does.
        val lockFile = dir.parentFile?.let { File(it, LOCK_FILE) }
        val channel = lockFile?.let { runCatching { FileChannel.open(it.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE) }.getOrNull() }
        try {
            channel?.lock()
            val stamp = File(dir, STAMP)
            if (stamp.isFile && stamp.readText() == current) return false
            if (source.list("system").isEmpty()) return false   // nothing staged in this build (cores pipeline not run)
            copyTree(source, "system", dir)
            stamp.writeAtomically(current.encodeToByteArray())   // last: an unstamped dir is never trusted
            true
        } finally {
            runCatching { channel?.close() }   // closing releases the lock
        }
    }

    private fun copyTree(source: Source, assetPath: String, target: File) {
        val names = source.list(assetPath)
        if (names.isEmpty()) {   // a file
            target.writeAtomically(source.open(assetPath).use { it.readBytes() })   // the largest is ~4 MB
            return
        }
        target.mkdirs()
        for (name in names) copyTree(source, "$assetPath/$name", File(target, name))
    }
}
