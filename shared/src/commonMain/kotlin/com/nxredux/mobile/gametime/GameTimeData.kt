package com.nxredux.mobile.gametime

import com.nxredux.mobile.tryQuietly
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The Game Time screen's view of the store. */
interface GameTimeData {
    suspend fun totals(): List<GameTotal>
    suspend fun grandTotal(): Long
    suspend fun members(target: Long): List<GameTotal>
    /** Every play (its closed slices together) of [target]'s group (its merged games' too), newest first. */
    suspend fun sessions(target: Long): GameSessions
    suspend fun mergeCandidates(target: Long): List<GameTotal>
    suspend fun merge(from: Long, into: Long, name: String)
    suspend fun unmerge(gameId: Long)
    suspend fun delete(target: Long)
    /** Every recorded rom path to its group's total (a merged member maps to the group it was merged into). */
    suspend fun byPath(): Map<String, GameTotal>
    /** Play time and the most played group inside [sinceMs, nowMs]. */
    suspend fun window(sinceMs: Long, nowMs: Long): WindowTotals
    /** The rom paths of every game in each group [window] counts (merge targets and their members). */
    suspend fun playedPaths(sinceMs: Long, nowMs: Long): Set<String>
    /** Moves games to new rom paths ([map]: old to new), as [GameTimeStore.renamePaths]. */
    suspend fun renamePaths(map: Map<String, String>)
}

/**
 * [GameTimeStore] on [io]. The reads a screen opens with (Game Time's list, Home's window on the menu's return, the
 * library's per-game times) first close what a crashed game left open ([GameTimeStore.closeLeft]).
 */
class StoreGameTimeData(private val store: GameTimeStore, private val io: CoroutineDispatcher) : GameTimeData {
    /** A failed close (a write) leaves the left sessions open for the next read; the read itself still answers. */
    private fun closeLeft() = tryQuietly { store.closeLeft() }
    override suspend fun totals() = withContext(io) { closeLeft(); store.totals() }
    override suspend fun grandTotal() = withContext(io) { store.grandTotal() }
    override suspend fun members(target: Long) = withContext(io) { store.members(target) }
    override suspend fun sessions(target: Long) = withContext(io) { store.sessions(target) }
    override suspend fun mergeCandidates(target: Long) = withContext(io) { store.mergeCandidates(target) }
    override suspend fun merge(from: Long, into: Long, name: String) = withContext(io) { store.merge(from, into, name) }
    override suspend fun unmerge(gameId: Long) = withContext(io) { store.unmerge(gameId) }
    override suspend fun delete(target: Long) = withContext(io) { store.delete(target) }
    override suspend fun window(sinceMs: Long, nowMs: Long) = withContext(io) { closeLeft(); store.window(sinceMs, nowMs) }
    override suspend fun playedPaths(sinceMs: Long, nowMs: Long) = withContext(io) { store.playedPaths(sinceMs, nowMs) }
    override suspend fun byPath() = withContext(io) { closeLeft(); store.byPath() }
    override suspend fun renamePaths(map: Map<String, String>) = withContext(io) { store.renamePaths(map) }
}

/**
 * Asks [open] for the store on every call, inside the caller's coroutine (the opener keeps it open), so a store reopened
 * at another data root (All files access turned off or back on) is followed; a call while it can't open fails.
 */
class LazyGameTimeData(private val open: suspend () -> GameTimeData?) : GameTimeData {
    private suspend fun d(): GameTimeData = open() ?: error("game time unavailable")
    override suspend fun totals() = d().totals()
    override suspend fun grandTotal() = d().grandTotal()
    override suspend fun members(target: Long) = d().members(target)
    override suspend fun sessions(target: Long) = d().sessions(target)
    override suspend fun mergeCandidates(target: Long) = d().mergeCandidates(target)
    override suspend fun merge(from: Long, into: Long, name: String) = d().merge(from, into, name)
    override suspend fun unmerge(gameId: Long) = d().unmerge(gameId)
    override suspend fun delete(target: Long) = d().delete(target)
    override suspend fun byPath() = d().byPath()
    override suspend fun renamePaths(map: Map<String, String>) = d().renamePaths(map)
    override suspend fun window(sinceMs: Long, nowMs: Long) = d().window(sinceMs, nowMs)
    override suspend fun playedPaths(sinceMs: Long, nowMs: Long) = d().playedPaths(sinceMs, nowMs)
}
