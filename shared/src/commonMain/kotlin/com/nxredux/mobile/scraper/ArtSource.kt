package com.nxredux.mobile.scraper

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The art sources' ids: the written-art log's source and the key of a job's notes. */
object SourceId {
    const val SS = "screenscraper"
    const val LIBRETRO = "libretro"
    const val TGDB = "thegamesdb"
    const val SGDB = "steamgriddb"
}

/**
 * One image a source offers for a slot, best first within the source. [bytes]: the image itself when the source already
 * downloaded it to find it (libretro-thumbnails probes by GET); the engine then does not fetch [url] again. [note]: a
 * short label the source gives the image (a region, a style), shown with it in the Change art list. [thumb]: a small
 * version's link, the Change art preview (null: the source has none).
 */
class ArtHit(
    val slot: ArtSlot, val url: String, val origin: ArtOrigin, val bytes: ByteArray? = null, val note: String? = null, val thumb: String? = null,
)

/** This source is done for the rest of the job ([note] shown on the Artwork page); the job goes on with the others. */
class SourceStop(val note: String) : Exception(note)

/** The whole job pauses ("No connection", storage, ...). */
class JobPause(val reason: String) : Exception(reason)

/** Spaces calls at least [gapMs] apart, from the end of one to the start of the next (one call at a time). */
class Pacer(private val gapMs: Long, private val now: () -> Long, private val sleep: suspend (Long) -> Unit) {
    private val lock = Mutex()
    private var last: Long? = null

    suspend fun <T> paced(block: suspend () -> T): T = lock.withLock {
        last?.let { val wait = gapMs - (now() - it); if (wait > 0) sleep(wait) }
        try { block() } finally { last = now() }
    }
}

/**
 * The paced GET and error table TheGamesDB, SteamGridDB and libretro-thumbnails share (each passes its own constants):
 * no connection (-1) is asked again after each [backoff] wait and pauses the job on the [tries]th try; a 429 or 5xx is
 * asked again once after [busyWaitMs], and a second one stops the source. ScreenScraper keeps its own table
 * ([ScreenScraperSource]'s `api`): a 5xx stops it at once, 430 is a quota answer and every other answer (a 404
 * included) comes back whole.
 */
class PacedGet(
    private val pacer: Pacer, private val http: ScraperHttp, private val sleep: suspend (Long) -> Unit,
    private val tries: Int, private val backoff: List<Long>, private val busyWaitMs: Long,
) {
    /**
     * The body of a 200 (null when it carries none), null for any other answer. [stop]: the source's own statuses that
     * stop it at once, as their note (null = not a stop); [busyNote]: the note of a second 429 / 5xx by its status;
     * [before]: run ahead of every try (TheGamesDB stops there once its allowance is spent).
     */
    suspend fun get(
        url: String, headers: Map<String, String> = emptyMap(), busyNote: (Int) -> String,
        stop: (Int) -> String? = { null }, before: suspend () -> Unit = {},
    ): ByteArray? {
        var failures = 0
        var busy = false
        while (true) {
            before()
            val r = pacer.paced { http.get(url, headers) }
            when {
                r.status == -1 -> { if (failures == tries - 1) throw JobPause(ScrapeEngine.NO_CONNECTION); sleep(backoff[failures++]) }
                r.status == 429 || r.status >= 500 -> { if (busy) throw SourceStop(busyNote(r.status)); busy = true; sleep(busyWaitMs) }
                r.status == 200 -> return r.body
                else -> { stop(r.status)?.let { throw SourceStop(it) }; return null }
            }
        }
    }
}

/**
 * A job's API answers by request, so games that send the same one (regional variants and a multi-disc set's discs share
 * a search term, and often the match) ask once: an allowance is spent per request. Only non-empty 200 bodies are kept (a
 * failed or empty answer is asked again), at most [max] of them and [maxChars] characters in all, the oldest dropped
 * first: a job's games come in library order, so the requests that repeat come close together. Cleared when a job
 * starts and when it ends.
 */
class JobAnswers(private val max: Int = 128, private val maxChars: Int = 2_000_000) {
    private val lock = Mutex()
    private val map = LinkedHashMap<String, String>()
    private var chars = 0

    suspend fun clear() { lock.withLock { map.clear(); chars = 0 } }

    /** The kept answer to the request [key] (true: it was kept), else [fetch]'s answer to it (false), kept when it has a body. */
    suspend fun get(key: String, fetch: suspend (String) -> String?): Pair<String?, Boolean> {
        lock.withLock { map[key] }?.let { return it to true }
        val body = fetch(key) ?: return null to false
        if (body.isEmpty() || body.length > maxChars) return body to false
        lock.withLock {
            map.remove(key)?.let { chars -= it.length }
            map[key] = body; chars += body.length
            while (map.size > max || chars > maxChars) chars -= map.remove(map.keys.first())!!.length
        }
        return body to false
    }
}

/**
 * A stop met on a game's later call once its earlier calls found art (TheGamesDB's screenshot call after the box,
 * SteamGridDB's hero or logo call after the grid): that game keeps what it found, the source's next call stops.
 */
class PendingStop {
    private val lock = Mutex()
    private var note: String? = null

    /** A new job starts with none. */
    suspend fun clear() { lock.withLock { note = null } }

    /** One is held from an earlier call. */
    suspend fun held(): Boolean = lock.withLock { note != null }

    /** Throws the held stop, if any: the first thing a source's [ArtSource.find] does. */
    suspend fun throwIfHeld() { lock.withLock { note }?.let { throw SourceStop(it) } }

    /** [e] met with [hits] already in hand: held for the next call (the caller returns its hits); with none, thrown. */
    suspend fun hold(e: SourceStop, hits: Collection<ArtHit>) {
        if (hits.isEmpty()) throw e
        lock.withLock { note = e.note }
    }
}

/**
 * One place art comes from. The engine asks the sources in order, each only for the slots a game still lacks, and only
 * when it is [enabled] and [knows] the game. A source-wide failure is a [SourceStop] (that source is left out for the
 * rest of the job); a [JobPause] pauses the whole job.
 */
interface ArtSource {
    val id: String
    /** "ScreenScraper", "libretro-thumbnails", "TheGamesDB", "SteamGridDB". */
    val label: String
    /** Its name on a Progress row ("Done · ScreenScraper, libretro"); [label] unless shorter is needed. */
    val shortLabel: String get() = label
    /** Its credentials or key are present. */
    val enabled: Boolean
    /** The slots it can offer. */
    val slots: Set<ArtSlot>
    /**
     * Its wheels are the last resort (TheGamesDB's clear logos): in turn it is asked for the wheel only beside other slots
     * (a free extra; it spends no call on it then), and those wheels are tried once every other source has been asked;
     * when still none is written, it is asked for the wheel alone ([find] with only [ArtSlot.WHEEL]).
     */
    val wheelLast: Boolean get() = false
    /** It has a system / platform for [ref] (pure). */
    fun knows(ref: LibraryGameRef): Boolean
    /** Once at the start of each job run, before any [find] (per-job state, an up-front quota read). Throws as [find]. */
    suspend fun startJob() {}
    /** Once at the end of each run that called [startJob], however it ended: per-job state is let go. Never throws. */
    suspend fun endJob() {}
    /** Ranked hits for [wanted]; empty = not found. Throws [SourceStop] or [JobPause]. */
    suspend fun find(ref: LibraryGameRef, wanted: Set<ArtSlot>): List<ArtHit>
}
