package com.nxredux.mobile.achievements

import kotlinx.serialization.DeserializationStrategy

class RaStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Files under `sd/.userdata/`; paths are relative to that root. Android writes atomically and fsyncs appends. */
interface RaStore {
    suspend fun read(path: String): ByteArray?
    suspend fun writeAtomic(path: String, bytes: ByteArray)
    /** Durable append (fsync); throws [RaStoreException] on failure. */
    suspend fun append(path: String, bytes: ByteArray)
    /**
     * A cheap change check: null when absent, else a value that differs once the file changed, including a rewrite to
     * the same length. The default reads the file.
     */
    suspend fun stamp(path: String): String? = read(path)?.let { "${it.size}:${it.contentHashCode()}" }
    suspend fun delete(path: String)
    /** Deletes [dir] recursively; never the store root. */
    suspend fun deleteTree(dir: String)
    /** Immediate child names of [dir] (files and directories), empty when absent. */
    suspend fun list(dir: String): List<String>
    suspend fun exists(path: String): Boolean
    /**
     * Runs [block] holding [path]'s exclusive lock, shared with every process of the app (a game runs in its own), so a
     * read-modify-write of [path] never loses another process's append. Not reentrant. The default locks nothing: a
     * store with one process only.
     */
    suspend fun <T> locked(path: String, block: suspend () -> T): T = block()
}

/**
 * The JSON file at [path] read with [deserializer] ([RaJson.parse]): null when absent or unreadable as [T]. A store
 * failure ([RaStoreException]) is thrown as [RaStore.read] throws it: each caller decides whether to catch it.
 */
suspend fun <T> RaStore.readJson(path: String, deserializer: DeserializationStrategy<T>): T? =
    read(path)?.decodeToString()?.let { RaJson.parse(deserializer, it) }

object RaPaths {
    /** The store root below the SD stand-in: the per-app account tree [ACCOUNT] and the shared game tree [SHARED] (nx-redux `.userdata/shared/.ra`). */
    const val ROOT = ".userdata"
    const val ACCOUNT = "android/ra"
    const val SHARED = "shared/.ra"
    const val JOURNAL = "$ACCOUNT/pending/unlocks.jsonl"
    const val CONFIRMED = "$ACCOUNT/pending/confirmed.jsonl"
    /** Dead letters: journal entries the server refused for good (an unknown achievement, a bad parameter); never resent. */
    const val REJECTED = "$ACCOUNT/pending/rejected.jsonl"
    const val LAST_SYNC = "$ACCOUNT/pending/lastsync.txt"
    /** The last complete full sync ([RaFullSync]): when, and the cloud totals the cache was reconciled against. */
    const val FULL_SYNC = "$ACCOUNT/pending/fullsync.json"
    /** Shared with nx-redux: definitions per hash; nx-redux ignores mobile's extra files here. */
    const val GAMES = "$SHARED/cache/games"
    const val SESSIONS = "$ACCOUNT/cache/sessions"
    const val PENDING = "$ACCOUNT/pending"
    /** The cross-process lock's sidecar beside a locked file ([RaStore.locked]); never deleted, as a holder may be on it. */
    const val LOCK_SUFFIX = ".lock"
    private val hashRe = Regex("[0-9a-f]{32}")
    private val nameRe = Regex("[A-Za-z0-9_.-]+")
    fun isHash(s: String) = hashRe.matches(s)
    private fun name(s: String): String { require(nameRe.matches(s) && s != "." && s != "..") { "bad path component: $s" }; return s }
    fun login() = "$ACCOUNT/cache/login.json"
    fun gameDir(hash: String) = "$GAMES/${name(hash)}"
    fun gameId(hash: String) = "${gameDir(hash)}/gameid.json"
    fun sets(hash: String) = "${gameDir(hash)}/sets.json"
    fun game(hash: String) = "${gameDir(hash)}/game.json"
    /** The compact [RaSetsSummary] of [sets], written with it ([RaSetsCache]). */
    fun summary(hash: String) = "${gameDir(hash)}/summary.json"
    /** The ROM last loaded with this hash, relative to the card (home) root (nx-redux `rom.txt`). */
    fun romRecord(hash: String) = "${gameDir(hash)}/rom.txt"
    fun session(gameId: Int) = "$SESSIONS/$gameId.json"
    fun muted(hash: String) = "$ACCOUNT/muted/${name(hash)}.txt"
}
