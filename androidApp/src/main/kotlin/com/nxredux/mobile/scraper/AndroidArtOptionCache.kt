package com.nxredux.mobile.scraper

import android.content.Context
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.writeAtomically
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The Change art option previews at `filesDir/art-options/<sha1 of the key>`, app-private: never the home tree. A stored
 * one's ref is a versioned `file://` uri (`?v=<lastModified>`), as [AndroidArtFiles] gives, so Coil loads a rewritten
 * preview afresh. A key is hashed, so no key can name a file outside [dir].
 */
class AndroidArtOptionCache(private val dir: File) : ArtOptionCache {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, DIR))

    companion object {
        const val DIR = "art-options"
        private const val TAG = "AndroidArtOptionCache"
    }

    private fun file(key: String): File {
        val hex = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, hex)
    }

    override fun ref(key: String): ContentRef? =
        file(key).takeIf { it.isFile }?.let { ContentRef("file://${it.absolutePath}?v=${it.lastModified()}") }

    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (!f.isFile) return@withContext null
        runCatching { f.readBytes() }
            .onFailure { NxLog.w(TAG, "read failed (${it::class.java.simpleName})") }
            .getOrNull()
    }

    override suspend fun write(key: String, bytes: ByteArray): ContentRef? = withContext(Dispatchers.IO) {
        runCatching { file(key).writeAtomically(bytes) }
            .onFailure { NxLog.w(TAG, "write failed (${it::class.java.simpleName})") }
            .getOrNull() ?: return@withContext null
        ref(key)
    }

    override suspend fun size(): Long = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
    }

    override suspend fun remove(keys: Collection<String>) = withContext(Dispatchers.IO) {
        keys.forEach { k ->
            val f = file(k)
            if (f.exists() && !runCatching { f.delete() }.getOrDefault(false)) NxLog.w(TAG, "delete failed")
        }
    }

    /** Deletes every file in [dir] (a temp file left by an interrupted write too); returns the bytes freed. */
    override suspend fun clear(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        for (f in dir.listFiles().orEmpty().filter { it.isFile }) {
            val len = f.length()
            if (runCatching { f.delete() }.getOrDefault(false)) freed += len else NxLog.w(TAG, "delete failed")
        }
        freed
    }
}
