package com.nxredux.mobile.runner

import android.content.Context
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Cores read BIOS/asset files from an app-owned system directory. On the target phone the app cannot read
 * files pushed by adb into its external dir, so everything is copied by the app itself, from the APK's
 * assets/system/, once per app update (stamped with lastUpdateTime).
 *
 * MainActivity pre-warms it on a background thread (as it does the overlays and shaders) and every caller goes
 * through one lock (in this process, and a file lock across the menu and the game process), so a game started
 * meanwhile waits for that copy instead of running a second one.
 * Not [BundledAssets.sync]'s whole-tree swap: this dir is not the APK's alone. BiosImport copies the user's
 * `Bios/<TAG>/` files into it (by path, over bundled ones) and cores write their own files here (flycast's
 * `dc/` nvmem and flash, PPSSPP's), and a swap would drop all of that. So each bundled file that differs is built beside
 * its target and renamed over it (a core or a reader sees the old file or the new, never half of one), and the stamp
 * is written last: a copy cut short (killed, disk full) has no stamp and is redone on the next call.
 * A game launch can wait on this copy, so an update skips what it can: a file whose bundled size and CRC are the ones
 * [MANIFEST] recorded, still at the size and modification time recorded, is not even read; any other file is read
 * and compared, and written only when it differs.
 */
object SystemFiles {
    private const val TAG = "SystemFiles"
    private const val STAMP = ".stamp"
    /** Each bundled file's size and CRC as last copied, with the copy's size and modification time ([Manifest]). */
    private const val MANIFEST = ".manifest"
    /** In filesDir beside the system dir: the cross-process lock on [sync]. */
    private const val LOCK_FILE = "system.lock"
    private val lock = Any()

    /** Read side of the bundled tree; the APK's assets in the app, a fake in tests. */
    internal interface Source {
        /** Children of [path] (a directory); empty for a file or a missing path. */
        fun list(path: String): List<String>
        fun open(path: String): InputStream
        /**
         * Every file under `system/`, by its path below it, with a fingerprint that changes with its content (the APK's
         * size and CRC); null when the bundle has no such index, and the tree is walked by [list].
         */
        fun index(): Map<String, String>? = null
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
                // The APK's own zip index: one read for every file, where asking the asset manager costs a call each.
                override fun index() = runCatching { apkIndex(context.applicationInfo.sourceDir) }
                    .onFailure { NxLog.w(TAG, "could not read the APK's index; comparing every bundled file", it) }
                    .getOrNull()
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
        withFileLock(File(dir.parentFile ?: dir, LOCK_FILE)) {
            val stamp = File(dir, STAMP)
            if (stamp.isFile && stamp.readText() == current) return false
            if (source.list("system").isEmpty()) return false   // nothing staged in this build (cores pipeline not run)
            val index = source.index()?.takeIf { it.isNotEmpty() }
            val manifestFile = File(dir, MANIFEST)
            if (index == null) {
                manifestFile.delete()   // a later index-less copy would leave its rows out of date
                copyTree(source, "system", dir)
            } else {
                val old = Manifest.parse(runCatching { manifestFile.readText() }.getOrDefault(""))
                val now = HashMap<String, Manifest.Row>()
                for ((rel, fingerprint) in index) {
                    val target = File(dir, rel)
                    if (!Manifest.unchanged(old[rel], fingerprint, target)) copyFile(source, "system/$rel", target)
                    now[rel] = Manifest.Row(fingerprint, target.length(), target.lastModified())
                }
                manifestFile.writeAtomically(Manifest.format(now).encodeToByteArray())
            }
            stamp.writeAtomically(current.encodeToByteArray())   // last: an unstamped dir is never trusted
            true
        }
    }

    /** Whether [file] (in [systemDir]) is one this APK bundles. */
    fun isBundled(context: Context, systemDir: File, file: File): Boolean {
        val rel = file.relativeToOrNull(systemDir)?.path?.takeIf { it.isNotEmpty() && !it.startsWith("..") } ?: return false
        val parent = rel.substringBeforeLast('/', "")
        val asset = if (parent.isEmpty()) "system" else "system/$parent"
        return runCatching { context.assets.list(asset).orEmpty().contains(rel.substringAfterLast('/')) }.getOrDefault(false)
    }

    /** Puts this APK's copy of [file] (in [systemDir]) back over it; false when it bundles none or the copy fails. */
    fun restoreBundled(context: Context, systemDir: File, file: File): Boolean {
        if (!isBundled(context, systemDir, file)) return false
        val rel = file.relativeTo(systemDir).path
        return runCatching { file.writeAtomically(context.assets.open("system/$rel").use { it.readBytes() }) }.isSuccess
    }

    private fun copyTree(source: Source, assetPath: String, target: File) {
        val names = source.list(assetPath)
        if (names.isEmpty()) return copyFile(source, assetPath, target)   // a file
        target.mkdirs()
        for (name in names) copyTree(source, "$assetPath/$name", File(target, name))
    }

    private fun copyFile(source: Source, assetPath: String, target: File) {
        val bytes = source.open(assetPath).use { it.readBytes() }   // the largest is ~4 MB
        // A synced write per file costs far more than reading the file back to compare.
        if (!sameContent(target, bytes)) target.writeAtomically(bytes)
    }

    private fun sameContent(file: File, bytes: ByteArray): Boolean =
        file.isFile && file.length() == bytes.size.toLong() && runCatching { file.readBytes().contentEquals(bytes) }.getOrDefault(false)

    /** Null when an entry has no CRC: a file left out of the index would never be copied. */
    private fun apkIndex(apk: String): Map<String, String>? = ZipFile(apk).use { zip ->
        val prefix = "assets/system/"
        val out = HashMap<String, String>()
        for (e in zip.entries()) {
            if (e.isDirectory || !e.name.startsWith(prefix)) continue
            if (e.crc == -1L || e.size == -1L) return null
            out[e.name.removePrefix(prefix)] = "${e.size}:${e.crc}"
        }
        out
    }

    /** [MANIFEST]'s rows: path below `system/` -> the bundled file's fingerprint and its copy's size and modification time. */
    internal object Manifest {
        data class Row(val fingerprint: String, val size: Long, val modified: Long)

        /** Whether [target] still holds the copy [row] recorded of the bundled file now [fingerprint]: nothing to read. */
        fun unchanged(row: Row?, fingerprint: String, target: File): Boolean =
            row != null && row.fingerprint == fingerprint && target.isFile && target.length() == row.size && target.lastModified() == row.modified

        fun parse(text: String): Map<String, Row> = text.lineSequence().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size != 4) return@mapNotNull null
            val size = p[2].toLongOrNull() ?: return@mapNotNull null
            val modified = p[3].toLongOrNull() ?: return@mapNotNull null
            p[0] to Row(p[1], size, modified)
        }.toMap()

        fun format(rows: Map<String, Row>): String =
            rows.entries.sortedBy { it.key }.joinToString("") { (path, r) -> "$path\t${r.fingerprint}\t${r.size}\t${r.modified}\n" }
    }
}
