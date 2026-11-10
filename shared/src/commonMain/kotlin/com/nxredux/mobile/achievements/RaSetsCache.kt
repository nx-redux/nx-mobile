package com.nxredux.mobile.achievements

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One core achievement in a [RaSetsSummary]: its id and title, in display order. */
@Serializable
data class RaSummaryAchievement(@SerialName("i") val id: Int, @SerialName("t") val title: String)

/**
 * `cache/games/<md5>/summary.json`: what the menu, the browser's list and the sync read of a cached `sets.json` (up to
 * ~2 MB), a few KB: the game, its console and title, the core achievements that count ([SetsBody.coreAchievements]),
 * and every set's game id ([setGameIds]: `allprogress` counts a bonus or subset set under its own id). A summary
 * without [setGameIds] is rebuilt on read.
 */
@Serializable
data class RaSetsSummary(
    @SerialName("g") val gameId: Int, @SerialName("c") val consoleId: Int, @SerialName("t") val title: String,
    @SerialName("a") val core: List<RaSummaryAchievement>, @SerialName("s") val setGameIds: List<Int>? = null,
) {
    companion object {
        fun of(sets: SetsBody) = RaSetsSummary(
            sets.gameId, sets.consoleId, sets.Title, sets.coreAchievements.map { RaSummaryAchievement(it.ID, it.Title) },
            (listOf(sets.gameId) + sets.Sets.map { it.GameId }).filter { it != 0 }.distinct(),
        )
    }
}

/**
 * Writes and reads a game's `sets.json` with its [RaSetsSummary]. A write drops the old summary first and writes the new
 * one after, so a crash in between leaves none (never a stale one); a missing summary is rebuilt from `sets.json` on
 * read. One lock keeps a rebuild from racing a write (an old summary landing after the new sets).
 */
object RaSetsCache {
    private val lock = Mutex()

    suspend fun write(store: RaStore, hash: String, body: String) = lock.withLock {
        store.delete(RaPaths.summary(hash))
        store.writeAtomic(RaPaths.sets(hash), body.encodeToByteArray())
        RaJson.parse(SetsBody.serializer(), body)?.let { writeSummary(store, hash, RaSetsSummary.of(it)) }
    }

    suspend fun delete(store: RaStore, hash: String) = lock.withLock {
        store.delete(RaPaths.sets(hash))
        store.delete(RaPaths.summary(hash))
    }

    /** [hash]'s summary; null when its sets are not cached or unreadable. */
    suspend fun summary(store: RaStore, hash: String): RaSetsSummary? {
        read(store, hash)?.let { return it }
        // Most cached hashes of a downloaded library have no set (gameid.json only): one cheap check, not two more reads.
        if (!store.exists(RaPaths.sets(hash))) return null
        return lock.withLock {
            read(store, hash) ?: run {
                val sets = store.readJson(RaPaths.sets(hash), SetsBody.serializer()) ?: return@withLock null
                RaSetsSummary.of(sets).also { s ->
                    try { writeSummary(store, hash, s) } catch (e: RaStoreException) { }   // rebuilt again next read
                }
            }
        }
    }

    private suspend fun read(store: RaStore, hash: String): RaSetsSummary? =
        store.readJson(RaPaths.summary(hash), RaSetsSummary.serializer())?.takeIf { it.setGameIds != null }

    private suspend fun writeSummary(store: RaStore, hash: String, s: RaSetsSummary) =
        store.writeAtomic(RaPaths.summary(hash), RaJson.json.encodeToString(RaSetsSummary.serializer(), s).encodeToByteArray())
}
