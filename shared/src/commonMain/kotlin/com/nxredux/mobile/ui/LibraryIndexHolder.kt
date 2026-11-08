package com.nxredux.mobile.ui

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.LibraryIndexCodec
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.toSystems
import com.nxredux.mobile.library.withArt
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.LibraryIndexStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal const val ART_QUIET_MS = 1_500L
internal const val ART_MAX_WAIT_MS = 15_000L

/**
 * The library index in memory and in [store], and the lookups built from it. Every change goes through [mutate] under
 * one lock, from any thread (a scan on the main thread, art results on [compute]). A read that cannot wait for the lock
 * (a button press on the main thread) takes [snapshot]: the index is immutable and the reference volatile, so such a read
 * sees a whole index, the last one set, never a torn or stale one.
 *
 * [onArtShown]: art results were written after the debounce ([flushArt]); the index they are in is to be shown.
 */
internal class LibraryIndexHolder(
    private val scope: CoroutineScope,
    private val store: LibraryIndexStore,
    private val compute: CoroutineContext,
    private val onArtShown: suspend (LibraryIndex) -> Unit,
) {
    private val lock = Mutex()             // every index read, write and in-memory swap
    @Volatile private var library: LibraryIndex? = null   // the library as last scanned, read or moved

    /** [LibraryIndex.lateMount] of this run's last scan (a stored one is stale). Main thread. */
    var lateMountHere: List<String> = emptyList()

    // Art results applied to [library] but not yet written ([artUnsaved]) or shown ([artUnshown]); [artSeq] counts them so
    // [artFlushJob] knows they stopped coming. All under [lock].
    private var artUnsaved = false
    private var artUnshown = false
    private var artSeq = 0L
    private var artFlushJob: Job? = null

    /**
     * Each game's places in the index it was built for ((console, game) indices), so a fetch's thousands of results
     * each find their game without a pass over the library. Read and written under [lock] only.
     */
    private var artPositionsOf: Pair<LibraryIndex, Map<GameRef, List<Pair<Int, Int>>>>? = null

    @Volatile private var playableOf: Pair<List<SystemFolder>, Map<GameRef, Listed>>? = null   // [playableLookup] of the systems it was built for
    @Volatile private var hiddenLookupOf: Pair<LibraryIndex, Map<GameRef, Listed>>? = null     // [gameLookup] of the index it was built for (Hidden)

    /** The index in memory now, without the lock (see the class note); null before the first read or scan. */
    val snapshot: LibraryIndex? get() = library

    /** The index in memory, read under the lock: after any change in flight. */
    suspend fun current(): LibraryIndex? = lock.withLock { library }

    /**
     * The one way the library index changes. Under the lock, [change] gets the index in memory (null when there is none)
     * and returns the next one; null or the same instance is no change. A new one becomes the index and is written
     * ([persist]) before the lock is let go, which writes any art results still waiting too. [persistNow] false (art
     * results only) leaves the write and the refresh to [flushArt]: a fetch of thousands of games would otherwise rewrite
     * the whole index and rebuild the menu once per game. Returns the new index, or null for no change.
     */
    suspend fun mutate(persistNow: Boolean = true, change: suspend (LibraryIndex?) -> LibraryIndex?): LibraryIndex? =
        lock.withLock {
            val current = library
            change(current)?.takeIf { it !== current }?.also {
                library = it
                if (persistNow) {
                    persist(it)
                    artUnsaved = false   // the whole index went out, the art in it with it
                } else {
                    artUnsaved = true; artUnshown = true; artSeq++
                }
            }
        }

    /** The index fitting [fits]: the in-memory one, else the stored copy (which then becomes the one in memory). */
    suspend fun fitting(fits: (LibraryIndex) -> Boolean): LibraryIndex? = lock.withLock {
        library?.takeIf(fits) ?: readStored()?.takeIf(fits)?.also { library = it }
    }

    /** The home is gone: no index in memory, and the stored copy deleted so the next start scans. */
    suspend fun forget() {
        lock.withLock {
            library = null
            try { store.delete() } catch (d: CancellationException) { throw d } catch (d: Throwable) { Unit }
        }
    }

    /** The stored index, or null when there is none, it cannot be read, or it does not decode (garbage, another version). */
    suspend fun readStored(): LibraryIndex? = try {
        store.read()?.let { LibraryIndexCodec.decode(it, compute) }   // decoded off the main thread
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    /**
     * Writes [index] (call under the lock). A failed write deletes the stored copy so the next start scans instead
     * of trusting an older index that may still fit the settings. The platform store logs its own failures.
     */
    private suspend fun persist(index: LibraryIndex) {
        val ok = try {
            store.write(LibraryIndexCodec.encode(index, compute))   // the whole index: encoded off the main thread
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
        if (ok) return
        try {
            store.delete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Unit
        }
    }

    /**
     * An art result for the game at [ref]: only the in-memory update under the lock; the write and the refresh are
     * batched ([flushArt]: after a pause in the results, and at the job's end). False when the game is not in the index.
     */
    suspend fun applyArt(ref: GameRef, art: ArtRefs): Boolean {
        mutate(persistNow = false) { current ->
            current ?: return@mutate null
            val positions = artPositionsOf?.takeIf { it.first === current }?.second ?: artPositions(current)
            val at = positions[ref] ?: return@mutate null
            current.withArtAt(at, art).also { artPositionsOf = it to positions }   // same order and sizes: the places hold
        } ?: return false
        scheduleArtFlush()
        return true
    }

    private fun artPositions(index: LibraryIndex): Map<GameRef, List<Pair<Int, Int>>> {
        val map = HashMap<GameRef, MutableList<Pair<Int, Int>>>()
        index.systems.forEachIndexed { si, s -> s.games.forEachIndexed { gi, g -> map.getOrPut(GameRef(g.source, g.dir, g.file)) { ArrayList(1) }.add(si to gi) } }
        return map
    }

    /** [LibraryIndex.setArt] at known places: only the consoles holding the game are copied. */
    private fun LibraryIndex.withArtAt(at: List<Pair<Int, Int>>, art: ArtRefs): LibraryIndex {
        val next = systems.toMutableList()
        at.groupBy({ it.first }, { it.second }).forEach { (si, games) ->
            next[si] = next[si].let { s -> s.copy(games = s.games.toMutableList().also { list -> games.forEach { gi -> list[gi] = list[gi].withArt(art) } }) }
        }
        return copy(systems = next)
    }

    /**
     * After an art result ([mutate] with persistNow false): one [flushArt] once results stop coming for [ART_QUIET_MS]
     * (at most [ART_MAX_WAIT_MS] after the first), whatever thread the result came on. A result that lands during the
     * flush goes round again; the job lets go of [artFlushJob] under the lock only with nothing left, so no result is
     * left without a flush coming.
     */
    private suspend fun scheduleArtFlush() {
        lock.withLock {
            if (artFlushJob?.isActive == true) return@withLock
            artFlushJob = scope.launch {
                do {
                    var waited = 0L
                    while (waited < ART_MAX_WAIT_MS) {
                        val seen = lock.withLock { artSeq }
                        delay(ART_QUIET_MS)
                        waited += ART_QUIET_MS
                        if (lock.withLock { artSeq } == seen) break
                    }
                    flushArt()
                } while (lock.withLock { artUnshown.also { if (!it) artFlushJob = null } })
            }
        }
    }

    /**
     * Writes the index when art results are waiting, then hands it to [onArtShown] once for all of them. The index in
     * memory is the truth: art waiting since a scan or another change wrote the whole index is not written again, and a
     * result that came in meanwhile is in it. Run by the debounce, by a fetch's or Reset's end and when the app goes to
     * the background.
     */
    suspend fun flushArt() {
        val shown = lock.withLock {
            val ix = library
            if (artUnsaved && ix != null) persist(ix)
            artUnsaved = false
            ix?.takeIf { artUnshown }.also { artUnshown = false }
        } ?: return
        onArtShown(shown)
    }

    /**
     * [playableLookup] of [systems]: every line resolves in one lookup instead of a walk over every game ([resolveIn]
     * gives the same answers). Built once per systems list, here when [playableOffMain] has not built it yet (one pass
     * over the games, for a resolve outside a coroutine).
     */
    fun playable(systems: List<SystemFolder>): Map<GameRef, Listed> =
        playableOf?.takeIf { it.first === systems }?.second ?: playableLookup(systems).also { playableOf = systems to it }

    /** [playable], built on [compute] when it is not built yet: a list opening or refreshing never walks the library on the main thread. */
    suspend fun playableOffMain(systems: List<SystemFolder>): Map<GameRef, Listed> =
        playableOf?.takeIf { it.first === systems }?.second ?: withContext(compute) { playableLookup(systems) }.also { playableOf = systems to it }

    /**
     * Every game of the index (coreless and Unassigned included) by its place, so a hidden line can be unhidden: one
     * [gameLookup] per index, built on [compute]. Null with no index.
     */
    suspend fun hiddenLookup(): Map<GameRef, Listed>? {
        val index = current() ?: return null
        return hiddenLookupOf?.takeIf { it.first === index }?.second
            ?: withContext(compute) { gameLookup(index.toSystems()) }.also { hiddenLookupOf = index to it }
    }
}
