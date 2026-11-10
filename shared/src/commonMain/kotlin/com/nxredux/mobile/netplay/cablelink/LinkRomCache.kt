package com.nxredux.mobile.netplay.cablelink

/** Where a received ROM's bytes go. */
interface ByteSink {
    suspend fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset)
}

/** The hash of what was received is not the hash that was announced. */
class LinkRomHashMismatch(val expected: String, val actual: String) : Exception("Received ROM hash $actual, expected $expected")

/**
 * App-private storage for the cache's files, named by hash. Never under the user's ROM folders: platforms put it where
 * the system does not evict it and the user cannot browse it.
 */
interface CacheStore {
    /** Absolute path of [name], or null when it is not stored. */
    fun path(name: String): String?
    /** Stores [name] from what [write] sinks; a throw from [write] leaves nothing behind (and any old [name] untouched). */
    suspend fun write(name: String, write: suspend (ByteSink) -> Unit): String
    fun sizeBytes(): Long
    fun clear()
}

/**
 * ROMs received over a Cable Link session, keyed by their SHA-1. A file is only ever kept whole and verified, so
 * [find] can hand its path straight to a core.
 */
class LinkRomCache(private val store: CacheStore) {
    fun find(hash: String): String? = if (RomHash.isHash(hash)) store.path(hash) else null

    /**
     * Stores what [write] sinks under [hash] and returns its path. Throws [LinkRomHashMismatch] (nothing kept) when the
     * bytes do not hash to [hash], and for an [hash] that is not one.
     */
    suspend fun put(hash: String, write: suspend (ByteSink) -> Unit): String {
        require(RomHash.isHash(hash)) { "not a SHA-1 hash" }
        return store.write(hash) { out ->
            val sha = Sha1()
            write(object : ByteSink {
                override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                    sha.update(bytes, offset, length)
                    out.write(bytes, offset, length)
                }
            })
            val actual = sha.hex()
            if (actual != hash) throw LinkRomHashMismatch(hash, actual)
        }
    }

    fun sizeBytes(): Long = store.sizeBytes()

    fun clear() = store.clear()
}

/** In memory (tests). */
class MemoryCacheStore : CacheStore {
    val files = HashMap<String, ByteArray>()
    override fun path(name: String): String? = if (name in files) "/mem/$name" else null
    override suspend fun write(name: String, write: suspend (ByteSink) -> Unit): String {
        val buf = ArrayList<Byte>()
        write(object : ByteSink {
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) { for (i in offset until offset + length) buf += bytes[i] }
        })
        files[name] = buf.toByteArray()
        return "/mem/$name"
    }
    override fun sizeBytes(): Long = files.values.sumOf { it.size.toLong() }
    override fun clear() = files.clear()
}
