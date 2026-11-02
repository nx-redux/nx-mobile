package com.nxredux.mobile.scraper

import com.nxredux.mobile.runner.ContentRef

/**
 * The Change art previews: option images downloaded on demand, kept in app-private storage (never under `.media/`) by
 * the game and the option's [ArtOption.key]. Losing them costs only a new download: the links stay in [ArtOptionStore].
 */
interface ArtOptionCache {
    /** The cached image for [key], or null when it is not cached. */
    fun ref(key: String): ContentRef?
    /** The cached image's bytes, or null when it is not cached or can't be read. */
    suspend fun read(key: String): ByteArray?
    /** Stores [bytes] for [key]; its ref, or null when the write failed. */
    suspend fun write(key: String, bytes: ByteArray): ContentRef?
    /** The bytes the cache holds. */
    suspend fun size(): Long
    /** Deletes the images for [keys] (absent ones ignored). */
    suspend fun remove(keys: Collection<String>)
    /** Deletes every image; returns the bytes freed. */
    suspend fun clear(): Long

    companion object {
        /** No cache: nothing is kept, every preview is downloaded again. */
        val NONE: ArtOptionCache = object : ArtOptionCache {
            override fun ref(key: String): ContentRef? = null
            override suspend fun read(key: String): ByteArray? = null
            override suspend fun write(key: String, bytes: ByteArray): ContentRef? = null
            override suspend fun size(): Long = 0
            override suspend fun remove(keys: Collection<String>) {}
            override suspend fun clear(): Long = 0
        }
    }
}
