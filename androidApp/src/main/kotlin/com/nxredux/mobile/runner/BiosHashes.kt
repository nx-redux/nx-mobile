package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.bios.Md5
import com.swordfish.libretrodroid.NxLog
import java.io.File

/**
 * The md5 of BIOS files in the system dir for the launch's checksum check ([BiosChecksum]), kept in a cache file by path,
 * size and modification time ([BiosMd5Cache]): a 4 MB PS2 BIOS is read once, not at every launch. Each game runs in its
 * own process, so the cache lives on disk; two processes writing it at once only lose a row, hashed again next time.
 * Blocking: call off the main thread.
 */
object BiosHashes {
    private const val CACHE = "bios-md5.tsv"

    @Synchronized
    fun files(context: Context, dir: File, names: Collection<String>): Map<String, BiosFile> {
        if (names.isEmpty()) return emptyMap()
        val cacheFile = File(context.filesDir, CACHE)
        val cache = BiosMd5Cache.parse(runCatching { cacheFile.readText() }.getOrDefault(""))
        var changed = false
        val out = HashMap<String, BiosFile>()
        for (name in names) {
            val f = File(dir, name).takeIf { it.isFile } ?: continue
            val size = f.length()
            val modified = f.lastModified()
            val md5 = cache.get(f.path, size, modified) ?: runCatching { Md5.of(f.inputStream()) }
                .onSuccess { cache.put(f.path, size, modified, it); changed = true }
                .onFailure { NxLog.w("BiosHashes", "could not read ${f.path}", it) }
                .getOrNull()
            out[name] = BiosFile(name, size, md5)
        }
        if (changed) {
            cache.retain { File(it).isFile }
            runCatching { cacheFile.writeAtomically(cache.format().encodeToByteArray()) }.onFailure { NxLog.w("BiosHashes", "cache write failed", it) }
        }
        return out
    }
}
