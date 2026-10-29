package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.AppProcess
import com.nxredux.mobile.storage.CacheEntry
import com.nxredux.mobile.storage.CacheSweep
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/**
 * The ROM cache, `cacheDir/roms/`: one top-level entry per archive extraction (`<archive name>.<length>/<inner ROM>`,
 * [Archives]) or real-path copy (`<file name>`, GameActivity.copyToCache). An entry's mtime is its last use (touched on
 * creation and on every reuse), and [sweep] trims the cache by [CacheSweep]'s policy. Each entry has one lock
 * (unpack/copy and delete of one entry never overlap) and a hold count: a held entry (the running game's, one being
 * hashed) is never deleted. The lock is per process; a hold is also a marker file `.holds/<name>@<pid>` under the root, so
 * the menu's sweep sees what the game process holds (and the reverse). A marker whose process is gone is dropped by the sweep.
 */
object RomCache {
    private const val TAG = "RomCache"
    /**
     * A `.part` older than this is a killed process's leftover. In this process an unpack in flight holds its entry's
     * lock (the sweep waits for it), so the age rule only guards a `.part` a dying process may have written moments ago.
     */
    private const val PART_MAX_AGE_MILLIS = 60L * 60 * 1000
    private const val PART = ".part"
    /** Holds' markers, `<name>@<pid>`; not a cache entry, so the sweep never lists it as one. */
    private const val HOLDS = ".holds"

    private val locks = ConcurrentHashMap<String, Any>()
    private val holds = ConcurrentHashMap<String, Int>()

    fun root(context: Context): File = File(context.cacheDir, "roms")

    /** The lock of top-level entry [name]; held while it is unpacked, copied, reused or deleted. */
    fun lockFor(name: String): Any = locks.getOrPut(name) { Any() }

    /**
     * Marks entry [name] in use until the matching [release]; counted, so a game and a hash may hold one entry at once
     * (one marker file per process and name, dropped with the last release).
     */
    fun hold(context: Context, name: String) = holdIn(root(context), name, android.os.Process.myPid())

    fun release(context: Context, name: String) = releaseIn(root(context), name, android.os.Process.myPid())

    internal fun holdIn(root: File, name: String, pid: Int) {
        // The marker changes under the same lock as the count: a release and a hold of one name never interleave.
        synchronized(holds) {
            if (holds.merge(name, 1, Int::plus) == 1) runCatching { File(root, "$HOLDS/$name@$pid").also { it.parentFile?.mkdirs() }.createNewFile() }
                .onFailure { NxLog.w(TAG, "could not mark the hold on $name", it) }   // still held here; only another process's sweep may miss it
        }
    }

    internal fun releaseIn(root: File, name: String, pid: Int) {
        synchronized(holds) {
            val n = holds[name] ?: return
            if (n > 1) holds[name] = n - 1 else { holds.remove(name); File(root, "$HOLDS/$name@$pid").delete() }
        }
    }

    private fun isHeld(name: String) = holds.containsKey(name)

    /**
     * The names with a live hold in any process of the app ([isAlive] by pid); markers of dead processes are deleted.
     */
    internal fun sweepKeep(root: File, isAlive: (Int) -> Boolean): Set<String> {
        val keep = HashSet<String>()
        for (marker in File(root, HOLDS).listFiles().orEmpty()) {
            val at = marker.name.lastIndexOf('@')
            val pid = if (at > 0) marker.name.substring(at + 1).toIntOrNull() else null
            if (pid != null && isAlive(pid)) keep += marker.name.substring(0, at)
            else marker.delete()
        }
        return keep
    }

    /** Records a use of top-level [entry] (and of [inner], the ROM inside an extraction): their mtimes become now. */
    fun touch(entry: File, inner: File? = null) {
        val now = System.currentTimeMillis()
        inner?.setLastModified(now)
        entry.setLastModified(now)
    }

    /**
     * Deletes what [CacheSweep] picks (unused for 30 days, then least recently used first while over 256 MiB) and `.part`
     * leftovers older than an hour. Never deletes a held entry, one with a younger `.part` (an unpack in flight), or
     * one used since it was listed. Blocking (it may wait on an unpack's lock): call off the main thread. Best effort:
     * each entry fails alone and nothing is thrown.
     */
    fun sweep(context: Context, now: Long = System.currentTimeMillis()) {
        runCatching {
            val root = root(context)
            val children = (root.listFiles() ?: return).filter { it.name != HOLDS }
            val alive = AppProcess.aliveCheck(context)
            val held = sweepKeep(root, alive)
            val listed = children.mapNotNull { f -> runCatching { CacheEntry(f.name, sizeOf(f), f.lastModified()) }.getOrNull() }
            val unpacking = children.filter { f -> f.isDirectory && parts(f).any { now - it.lastModified() <= PART_MAX_AGE_MILLIS } }.map { it.name }
            val victims = CacheSweep.victims(listed, now, keep = holds.keys + held + unpacking)
            val usedAt = listed.associate { it.name to it.lastUsedMillis }
            var freed = 0L
            for (name in victims) runCatching {
                val entry = File(root, name)
                synchronized(lockFor(name)) {
                    // Held (here or by the other process) or reused while we waited for its lock: it is the running game's or was just unpacked again.
                    if (isHeld(name) || name in sweepKeep(root, alive) || entry.lastModified() != usedAt[name]) return@synchronized
                    val bytes = sizeOf(entry)
                    if (deleteTree(entry)) freed += bytes else NxLog.w(TAG, "could not delete ${entry.path}")
                }
            }.onFailure { NxLog.w(TAG, "sweep of $name failed", it) }
            for (dir in root.listFiles().orEmpty()) if (dir.isDirectory && !isLink(dir) && dir.name != HOLDS) runCatching { dropStaleParts(dir, now, held) }
                .onFailure { NxLog.w(TAG, "part sweep of ${dir.name} failed", it) }
            if (victims.isNotEmpty()) NxLog.i(TAG, "swept ${victims.size} of ${listed.size} entries, ${freed shr 20} MiB")
        }.onFailure { NxLog.w(TAG, "sweep failed", it) }
    }

    /** A stale `.part` is removed without bumping the entry's mtime (the removal is not a use). */
    private fun dropStaleParts(dir: File, now: Long, held: Set<String>) = synchronized(lockFor(dir.name)) {
        if (isHeld(dir.name) || dir.name in held) return@synchronized
        val stale = parts(dir).filter { now - it.lastModified() > PART_MAX_AGE_MILLIS }
        if (stale.isEmpty()) return@synchronized
        val usedAt = dir.lastModified()
        stale.forEach { if (!it.delete()) NxLog.w(TAG, "could not delete ${it.path}") }
        dir.setLastModified(usedAt)
    }

    private fun parts(dir: File): List<File> = dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(PART) }

    /** Symlinks are not followed (an entry never holds one; if it did, what it points at is not the cache's). */
    private fun isLink(f: File) = Files.isSymbolicLink(f.toPath())

    private fun sizeOf(f: File): Long = when {
        isLink(f) -> 0L
        f.isDirectory -> f.listFiles().orEmpty().sumOf(::sizeOf)
        else -> f.length()
    }

    /** Deletes [f] and, if it is a real directory, its contents; a symlink is removed itself, never its target. */
    private fun deleteTree(f: File): Boolean {
        if (!isLink(f) && f.isDirectory) f.listFiles().orEmpty().forEach(::deleteTree)
        return f.delete() || !f.exists()
    }
}
