package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.IndexedGame
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/** [stop] says why a [failed] walk ended early (a refused login, a busy server); null with [failed] = no connection. */
data class RaPrefetchResult(val cached: Int, val unknown: Int, val skipped: Int, val failed: Boolean, val stop: RaSyncStop? = null)

/** A game's place in the walk: in flight (hashing, then asking the server) or how it ended. */
enum class RaItemStatus(val finished: Boolean) {
    WAITING(false), HASHING(false), DOWNLOADING(false),
    CACHED(true), ALREADY_CACHED(true), NO_ACHIEVEMENTS(true), SKIPPED(true), ERROR(true),
}

/**
 * "Download all game data": hash every indexed game with an RC console, cache its gameid/sets, unlocks and badges.
 * The unlocks come from `unlocks`, never `startsession`: RAWeb's startsession dispatches a player-session heartbeat that
 * attaches the game to the account and stamps it last played, so prefetching would mark the whole library as played.
 * A session cached by real play is kept. Requests are paced; a busy server or a refused login ends the walk. A game
 * whose file is unchanged since its last hash ([RaHasher.stamp]) is found in its console folder's shared `hashes.json`
 * ([RaFingerprints], also the handheld's) and is not hashed (nor unpacked) again; each server answer is stored there.
 */
class RaPrefetch(
    private val store: RaStore, private val http: RaHttp, private val hasher: RaHasher, private val badges: RaBadges,
    private val fingerprints: RaFingerprints,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private class Stopped(val stop: RaSyncStop) : Exception()

    companion object {
        /** A game's file could not be hashed. */
        const val UNREADABLE = "couldn't read the file"
        /** The server gave no usable answer for a game. */
        const val NO_REPLY = "no answer from RetroAchievements"
        const val NO_CONNECTION = "no connection"
    }

    /** What the walk knows of a game's file: its key and stamp (when cacheable), and the cached answer when still valid. */
    private class Known(val hash: String?, val cached: RaFingerprint?, val key: RaFingerprintKey?, val stamp: Pair<Long, Long>?, val console: Int, val storable: Boolean)

    /**
     * Walks [index]'s RA games, reporting each through [onProgress]; the fingerprint answers are saved however it ends.
     * [onListed] names the games first; [onItem] reports each one's steps and outcome by its place in that list; [gate] is
     * passed before each game (a pause holds the walk there, between games).
     */
    suspend fun run(
        index: LibraryIndex, user: String, token: String,
        gate: suspend () -> Unit = {}, onListed: (List<String>) -> Unit = {}, onItem: (Int, RaItemStatus, String?) -> Unit = { _, _, _ -> },
        onProgress: (done: Int, total: Int, title: String) -> Unit,
    ): RaPrefetchResult {
        val pacer = RaPacer(http, delay)
        val candidates = index.systems.filter { !it.unassigned }.flatMap { s -> s.games.filter { g -> RaConsoles.consoleId(s.tag, g.file) != null }.map { g -> s.tag to g } }
        onListed(candidates.map { it.second.name })
        return walkSaving(candidates, pacer, user, token, gate, onItem, onProgress = onProgress)
    }

    /**
     * One game, [game] of console [tag] (the achievements panel's download of a game it has no data for): the walk [run]
     * makes for each game. [onItem] reports its steps and outcome, [onHash] its hash once known.
     */
    suspend fun one(
        tag: String, game: IndexedGame, user: String, token: String,
        onItem: (RaItemStatus, String?) -> Unit = { _, _ -> }, onHash: (String) -> Unit = {},
    ): RaPrefetchResult =
        walkSaving(listOf(tag to game), RaPacer(http, delay), user, token, {}, { _, st, why -> onItem(st, why) }, { _, h -> onHash(h) }) { _, _, _ -> }

    private suspend fun walkSaving(
        candidates: List<Pair<String, IndexedGame>>, pacer: RaPacer, user: String, token: String,
        gate: suspend () -> Unit, onItem: (Int, RaItemStatus, String?) -> Unit, onHash: (Int, String) -> Unit = { _, _ -> },
        onProgress: (done: Int, total: Int, title: String) -> Unit,
    ): RaPrefetchResult {
        val pending = HashMap<String, MutableList<(RaFingerprintFile) -> Unit>>()   // console folder -> its queued answers
        val snapshots = HashMap<String, RaFingerprintFile>()   // each folder's file, read once per walk
        // Saved however the walk ends (done, stopped, failed or cancelled): what was answered is not hashed again.
        try {
            return walk(candidates, snapshots, pending, pacer, user, token, gate, onItem, onHash, onProgress)
        } finally {
            withContext(NonCancellable) { for ((folder, changes) in pending) fingerprints.update(folder) { f -> changes.forEach { it(f) } } }
        }
    }

    /** [game]'s hash: the folder file's while its size, mtime and console hold, else the hasher's. */
    private suspend fun hashOf(tag: String, game: IndexedGame, snapshots: MutableMap<String, RaFingerprintFile>): Known {
        val console = RaConsoles.consoleId(tag, game.file) ?: 0
        val key = fingerprints.keyFor(RaGamePointer(tag, game.source, game.dir, game.file, game.name))
        val stamp = RaStamp.parse(hasher.stamp(game.ref))
        // The hasher unpacks an archive the core can't load (the inner ROM's hash) and hashes a loadable one raw.
        val storable = RaFingerprintKeys.storable(tag, game.file, console, unpacked = Names.isArchive(game.file) && !CoreRegistry.loadsArchive(tag, game.file))
        if (key != null && stamp != null) {
            val snap = snapshots.getOrPut(key.consoleFolder) { fingerprints.snapshot(key.consoleFolder) }
            snap.lookup(key.key, stamp.first, stamp.second, console)?.let { return Known(it.hash, it, key, stamp, console, storable) }
        }
        return Known(hasher.hash(tag, game.ref, game.file, game.folder), null, key, stamp, console, storable)
    }

    /**
     * Queues an answer for [k]'s folder: [game] = the server's id, 0 = unknown. A cached known game that is now unknown
     * is forgotten, only while the file still holds the hash this walk saw (a launch may have re-recorded it since).
     */
    private fun answer(k: Known, game: Int, pending: MutableMap<String, MutableList<(RaFingerprintFile) -> Unit>>) {
        val key = k.key ?: return; val stamp = k.stamp ?: return; val hash = k.hash ?: return
        if (!k.storable) return
        val change: (RaFingerprintFile) -> Unit = if (k.cached != null && k.cached.game != 0 && game == 0) { f -> f.forget(key.key, hash) }
            else { f -> f.record(key.key, stamp.first, stamp.second, k.console, hash, game) }
        pending.getOrPut(key.consoleFolder) { ArrayList() } += change
    }

    /**
     * A cached game whose pointer names a path the library no longer lists (it moved to another folder, or the pointer
     * is a handheld's rom.txt for a home this phone keeps elsewhere) points at [game], where this walk found it: the menu
     * finds its progress by that path. A pointer to a listed path (the same game twice) is left as it is.
     */
    private suspend fun repointStale(hash: String, tag: String, game: IndexedGame, listed: Set<String>) {
        val known = RaGamePointers.read(store, hash)
        if (known != null && GamePath.of(GameRef(known.source, known.dir, known.file)) in listed) return
        RaGamePointers.write(store, hash, RaGamePointer(tag, game.source, game.dir, game.file, game.name))
    }

    private suspend fun walk(
        candidates: List<Pair<String, IndexedGame>>, snapshots: MutableMap<String, RaFingerprintFile>,
        pending: MutableMap<String, MutableList<(RaFingerprintFile) -> Unit>>, pacer: RaPacer, user: String, token: String,
        gate: suspend () -> Unit, onItem: (Int, RaItemStatus, String?) -> Unit, onHash: (Int, String) -> Unit,
        onProgress: (done: Int, total: Int, title: String) -> Unit,
    ): RaPrefetchResult {
        var cached = 0; var unknown = 0; var skipped = 0
        // Every path this walk lists: a cached game's pointer elsewhere is stale (the game moved, or a handheld's rom.txt).
        val listed = candidates.mapTo(HashSet()) { (_, g) -> GamePath.of(GameRef(g.source, g.dir, g.file)) }
        for ((i, c) in candidates.withIndex()) {
            val (tag, game) = c
            currentCoroutineContext().ensureActive()
            gate()
            onProgress(i + 1, candidates.size, game.name)
            onItem(i, RaItemStatus.HASHING, null)
            val k = hashOf(tag, game, snapshots)
            val hash = k.hash
            if (hash == null) { skipped++; onItem(i, RaItemStatus.SKIPPED, UNREADABLE); continue }
            onHash(i, hash)
            if (store.exists(RaPaths.sets(hash))) {
                val sets = readSets(hash)
                // A hit is never restamped. A handheld-fetched sets.json has no (mobile-only) gameid.json: its own GameId answers.
                if (k.cached == null) (cachedGameId(hash) ?: sets?.gameId?.takeIf { it != 0 })?.let { answer(k, it, pending) }
                repointStale(hash, tag, game, listed)
                repairBadges(hash, sets); skipped++; onItem(i, RaItemStatus.ALREADY_CACHED, null); continue
            }
            onItem(i, RaItemStatus.DOWNLOADING, null)
            try {
                // A cached gameid.json is reused; only GameID 0 (known-unknown) short-circuits without a request.
                val id = cachedGameId(hash) ?: post(pacer, RaRequests.gameId(hash))?.also { store.writeAtomic(RaPaths.gameId(hash), it.encodeToByteArray()) }
                    ?.let { RaJson.parse(GameIdBody.serializer(), it)?.GameID }
                if (id == null) { skipped++; onItem(i, RaItemStatus.SKIPPED, NO_REPLY); continue }
                answer(k, id, pending)   // known or known-unknown: stored; a transport error or a stop records nothing
                if (id == 0) { unknown++; onItem(i, RaItemStatus.NO_ACHIEVEMENTS, null); continue }
                val sets = post(pacer, RaRequests.achievementSets(user, token, hash))
                val parsed = sets?.let { RaJson.parse(SetsBody.serializer(), it) }
                // The session is keyed by the set reply's game id, as rc_client keys it (and readers look it up).
                val gameId = parsed?.gameId?.takeIf { it != 0 } ?: id
                val played = store.exists(RaPaths.session(gameId))   // a session from real play is better (it has times): kept
                val session = if (sets == null || played) null else post(pacer, RaRequests.unlocks(user, token, gameId))?.let(::sessionOf)
                if (sets == null || (session == null && !played)) { skipped++; onItem(i, RaItemStatus.SKIPPED, NO_REPLY); continue }
                // sets.json is the "cached" marker, so it is written last: an interrupted game is refetched next run.
                session?.let { store.writeAtomic(RaPaths.session(gameId), it.encodeToByteArray()) }
                val pointer = RaGamePointer(tag, game.source, game.dir, game.file, game.name)
                RaGamePointers.write(store, hash, pointer)
                RaSetsCache.write(store, hash, sets)
                try {
                    badges.folder(pointer, parsed?.gameId ?: 0)?.let { badges.ensureAll(it, parsed?.allAchievements?.map { a -> a.BadgeName }.orEmpty()) }
                } catch (e: CancellationException) {
                    withContext(NonCancellable) { RaSetsCache.delete(store, hash) }   // half-fetched badges: refetch the game next run
                    throw e
                }
                cached++
                onItem(i, RaItemStatus.CACHED, null)
            } catch (e: RaHttpException) {
                onItem(i, RaItemStatus.ERROR, NO_CONNECTION)
                return RaPrefetchResult(cached, unknown, skipped, true)
            } catch (e: Stopped) {
                onItem(i, RaItemStatus.ERROR, stopText(e.stop))
                return RaPrefetchResult(cached, unknown, skipped, true, e.stop)
            }
        }
        return RaPrefetchResult(cached, unknown, skipped, false)
    }

    private fun stopText(stop: RaSyncStop): String = when (stop) {
        RaSyncStop.AUTH -> "sign in again"
        RaSyncStop.BUSY -> "RetroAchievements is busy"
        RaSyncStop.REFUSED -> "refused by RetroAchievements"
    }

    /** POSTs and returns a success body; null on a non-success (which is not a network error). Throws [Stopped] when busy or the login is refused. */
    private suspend fun post(pacer: RaPacer, form: String): String? {
        val r = pacer.post(form) ?: throw Stopped(RaSyncStop.BUSY)
        if (authRefused(r)) throw Stopped(RaSyncStop.AUTH)
        return if (r.status == 200 && RaJson.isSuccess(r.body)) r.body else null
    }

    /**
     * An `unlocks` reply as a cached session body: every id in [SessionBody.Unlocks], at time 0 (the reply has none;
     * readers show no date). Real play replaces it with startsession's own body. Null when unreadable.
     */
    private fun sessionOf(body: String): String? = RaJson.parse(UnlocksBody.serializer(), body)?.let { u ->
        RaJson.json.encodeToString(SessionBody.serializer(), SessionBody(true, u.UserUnlocks.distinct().map { SessionUnlock(it, 0L) }))
    }

    private suspend fun cachedGameId(hash: String): Int? =
        store.readJson(RaPaths.gameId(hash), GameIdBody.serializer())?.GameID

    private suspend fun readSets(hash: String): SetsBody? =
        store.readJson(RaPaths.sets(hash), SetsBody.serializer())

    private suspend fun repairBadges(hash: String, sets: SetsBody?) {
        sets ?: return
        val folder = badges.folder(RaGamePointers.read(store, hash), sets.gameId) ?: return
        badges.ensureAll(folder, sets.allAchievements.map { it.BadgeName })   // present files are skipped
    }
}
