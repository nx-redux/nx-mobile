package com.nxredux.mobile.scraper

import com.nxredux.mobile.storage.PrivateFiles
import com.nxredux.mobile.tryOr
import com.nxredux.mobile.tryOrNull
import com.nxredux.mobile.tryQuietly
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/** A game's info file (`.media/info/<name>.json`): ScreenScraper's facts, its game id and when they were fetched (epoch millis). */
@Serializable
data class StoredGameInfo(
    val synopsis: String?, val genre: String?, val year: String?, val publisher: String?, val lang: String?,
    val ssId: String?, val fetchedAt: Long,
) {
    companion object {
        fun of(info: SsGameInfo, ssId: String?, fetchedAt: Long) =
            StoredGameInfo(info.synopsis, info.genre, info.year, info.publisher, info.lang, ssId, fetchedAt)
    }
}

/** Where the info files are: [ScrapeEngine]'s art-target resolution, so a game's info sits beside its art. */
interface GameInfoFiles {
    /** The info file's home-relative path for [ref] (the cache key), or null when [ref] has no art target. */
    fun path(ref: LibraryGameRef): String?
    /** The file's text; null when absent or unreadable. */
    suspend fun read(ref: LibraryGameRef): String?
    /** Writes the file (logged first, as the art is, so Reset deletes it); false when it cannot be written. */
    suspend fun write(ref: LibraryGameRef, text: String): Boolean
}

/**
 * Game info beside the art, read through a per-file cache (the details page reads it on every highlight), and the games
 * ScreenScraper had no info for ([MISS_FILE], app-private so it works where the library folder can't be written; a
 * game path per line). Nothing here throws: an unreadable file is no info, an unwritable one no write.
 */
class GameInfoStore(
    private val files: GameInfoFiles, private val privateFiles: PrivateFiles,
    /** Where the reads and writes run; the main-process caller is on the UI thread. */
    private val work: CoroutineContext,
) {
    companion object {
        const val MISS_FILE = "info-misses.txt"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    private val cacheLock = Mutex()
    // Absent files are cached too: a highlight of a game without info must not query the provider each time.
    private val cache = HashMap<String, StoredGameInfo?>()

    private val missLock = Mutex()
    private var misses: LinkedHashSet<String>? = null
    /** The miss list could not be read: never saved over, as it may still hold more than this run knows. */
    private var missesUnread = false

    suspend fun read(ref: LibraryGameRef): StoredGameInfo? = withContext(work) {
        val key = files.path(ref) ?: return@withContext null
        cacheLock.withLock { if (cache.containsKey(key)) return@withContext cache[key] }
        val info = tryOrNull { files.read(ref) }?.let { text -> tryOrNull { json.decodeFromString(StoredGameInfo.serializer(), text) } }
        cacheLock.withLock { cache.getOrPut(key) { info } }
    }

    suspend fun write(ref: LibraryGameRef, info: StoredGameInfo): Boolean = withContext(work) {
        val key = files.path(ref) ?: return@withContext false
        val ok = tryOrNull { files.write(ref, json.encodeToString(StoredGameInfo.serializer(), info)) } == true
        // A failed write may have left the old file, or none: read it again next time.
        cacheLock.withLock { if (ok) cache[key] = info else cache.remove(key) }
        ok
    }

    /** Drops every cached read (after a Reset deleted info files, or a Device Sync may have brought some). */
    suspend fun forgetCached() = cacheLock.withLock { cache.clear() }

    suspend fun isMiss(path: String): Boolean = withContext(work) { missLock.withLock { loadMisses().contains(path) } }

    suspend fun markMiss(path: String) = withContext(work) {
        missLock.withLock { val m = loadMisses(); if (m.add(path)) saveMisses(m) }
    }

    suspend fun clearMiss(path: String) = withContext(work) {
        missLock.withLock { val m = loadMisses(); if (m.remove(path)) saveMisses(m) }
    }

    private suspend fun loadMisses(): LinkedHashSet<String> = misses ?: run {
        val text = tryOr({ missesUnread = true; null }) { privateFiles.read(MISS_FILE) }
        text.orEmpty().lineSequence().map { it.trim() }.filterTo(LinkedHashSet()) { it.isNotEmpty() }.also { misses = it }
    }

    // A failed save keeps the list in memory: this run still skips the miss, a restart may try the game again.
    private suspend fun saveMisses(m: Set<String>) {
        if (!missesUnread) tryQuietly { privateFiles.write(MISS_FILE, m.joinToString("") { "$it\n" }) }
    }
}
