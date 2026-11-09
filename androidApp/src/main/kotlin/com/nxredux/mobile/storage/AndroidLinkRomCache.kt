package com.nxredux.mobile.storage

import android.content.Context
import com.nxredux.mobile.netplay.cablelink.ByteSink
import com.nxredux.mobile.netplay.cablelink.CacheStore
import com.nxredux.mobile.netplay.cablelink.LinkRomCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Link ROMs in `filesDir/link-roms/`: the files dir, not the cache dir, so Android never drops a ROM a session is about
 * to use; the user clears it from Settings.
 */
class AndroidCacheStore(private val dir: File) : CacheStore {
    private fun file(name: String): File {
        require(name.isNotEmpty() && '/' !in name && name != "." && name != "..") { "bad cache file name" }
        return File(dir, name)
    }

    override fun path(name: String): String? = file(name).takeIf { it.isFile }?.absolutePath

    /** Through a private `.part` file and a rename, so [path] never answers with a half-written ROM. */
    override suspend fun write(name: String, write: suspend (ByteSink) -> Unit): String = withContext(Dispatchers.IO) {
        val target = file(name)
        dir.mkdirs()
        val part = File(dir, "$name.${UUID.randomUUID()}.part")   // one per writer: a failed one never touches another's bytes
        try {
            FileOutputStream(part).use { out ->
                write(object : ByteSink {
                    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.IO) { out.write(bytes, offset, length) }
                })
                out.flush()
                out.fd.sync()
            }
            if (!part.renameTo(target)) throw IOException("could not move ${part.name} into place")
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
        target.absolutePath
    }

    override fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    override fun clear() { dir.listFiles()?.forEach { it.delete() } }
}

object LinkRomCaches {
    @Volatile private var cache: LinkRomCache? = null

    /** The one cache of the process; the Settings row and a Cable Link session share it. */
    fun get(context: Context): LinkRomCache =
        cache ?: synchronized(this) { cache ?: LinkRomCache(AndroidCacheStore(File(context.applicationContext.filesDir, "link-roms"))).also { cache = it } }
}
