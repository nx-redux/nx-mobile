package com.nxredux.mobile.ui

import com.nxredux.mobile.gametime.LocalCalendar
import com.nxredux.mobile.gametime.systemLocalCalendar
import com.nxredux.mobile.scraper.GameInfoStore
import com.nxredux.mobile.scraper.InfoResult
import com.nxredux.mobile.scraper.LibraryGameRef
import com.nxredux.mobile.scraper.ScreenScraperSource
import com.nxredux.mobile.scraper.SourceStop
import com.nxredux.mobile.scraper.SsQuota
import com.nxredux.mobile.scraper.StoredGameInfo
import com.nxredux.mobile.tryOr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The top screen's background art and info fetch: the game the highlight rests on for [restMs] gets the quiet art run
 * when it lacks art ([wantsArt]; once per game this process, as art no source has is not asked for on every highlight),
 * its info coming with that lookup; otherwise it is looked up for its info alone when it has none stored and no
 * remembered miss. Scrolling a list asks nothing. One lookup at a time, none while an Artwork run asks
 * ScreenScraper. The daily limit or a lasting refusal (a blocked app) stops it until the next local day, a failure
 * (offline, ScreenScraper unavailable, an unreadable answer) for [RETRY_MS]; silently either way: nothing here throws
 * or tells the user.
 *
 * [follow] is called on [scope]'s thread (the UI's), and the state below is only touched there.
 */
class InfoFetcher(
    private val scope: CoroutineScope,
    private val store: GameInfoStore,
    /** ScreenScraper's info-only lookup; null: not asked (a run started meanwhile). May throw [SourceStop] or a pause. */
    private val lookup: suspend (LibraryGameRef) -> InfoResult?,
    /** The game lacks art an Artwork run would fetch. */
    private val wantsArt: (LibraryGameRef) -> Boolean = { false },
    /**
     * The quiet single-game art run, storing the info its lookup brings: null when not made, true once finished. May
     * throw [SourceStop] or a pause, as [lookup].
     */
    private val fetchArt: suspend (LibraryGameRef) -> Boolean? = { null },
    private val enabled: () -> Boolean,
    private val runActive: () -> Boolean,
    /** ScreenScraper's last quota read and when (epoch millis); null when none was read. */
    private val quotaRead: () -> Pair<SsQuota, Long>?,
    /** Epoch millis. */
    private val now: () -> Long,
    private val restMs: Long = 600,
    private val calendar: LocalCalendar = systemLocalCalendar(),
) {
    companion object {
        const val RETRY_MS = 5 * 60_000L
    }

    private val _fetched = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** The game path whose info was just stored. */
    val fetched: SharedFlow<String> = _fetched.asSharedFlow()

    private var resting: Job? = null
    private var followed: String? = null
    private val flight = Mutex()
    private var stoppedUntil = Long.MIN_VALUE
    /** Found but not storable (a library folder that can't be written): not asked again this process. */
    private val unstorable = HashSet<String>()
    /** The games whose art run finished this process. */
    private val artTried = HashSet<String>()

    /** The game now shown on the top screen, or null for none. */
    fun follow(ref: LibraryGameRef?) {
        resting?.cancel(); resting = null
        followed = ref?.gamePath
        if (ref == null || !enabled()) return
        resting = scope.launch {
            delay(restMs)
            // Not part of [resting]: a lookup already sent is never cut off by the next move, its answer is kept.
            scope.launch { flight.withLock { fetch(ref) } }
        }
    }

    /**
     * Today's quota read is at its limit. A reading from an earlier local day says nothing about today's (it is only
     * read again when ScreenScraper is asked): the next lookup reports the quota itself.
     */
    private fun quotaSpent(t: Long): Boolean {
        val (q, at) = quotaRead() ?: return false
        return q.today >= q.max && calendar.dayStart(at) == calendar.dayStart(t)
    }

    /**
     * Until when a thrown lookup stops the fetch: the daily limit and the refusals that stay (this build or app refused,
     * a bad login, the unrecognised-games limit) until the next local day; ScreenScraper unavailable (a 5xx, a closed
     * API, too many requests) and no connection for [RETRY_MS].
     */
    private fun stopAfter(e: Exception): Long = when ((e as? SourceStop)?.note) {
        ScreenScraperSource.DAILY_LIMIT, ScreenScraperSource.UNRECOGNISED_LIMIT, ScreenScraperSource.BUILD_REFUSED,
        ScreenScraperSource.APP_BLOCKED, ScreenScraperSource.SIGN_IN_FAILED -> calendar.dayStart(now(), 1)
        else -> now() + RETRY_MS
    }

    private suspend fun fetch(ref: LibraryGameRef) {
        // Queued behind another lookup while the highlight moved on: that game was not rested on any more.
        if (followed != ref.gamePath || !enabled() || runActive()) return
        val t = now()
        if (t < stoppedUntil) return
        if (quotaSpent(t)) { stoppedUntil = calendar.dayStart(t, 1); return }
        if (ref.gamePath !in artTried && wantsArt(ref)) {
            tryOr({ e -> stoppedUntil = stopAfter(e); null }) { fetchArt(ref) } ?: return
            artTried += ref.gamePath
            if (store.read(ref) != null) _fetched.emit(ref.gamePath)
            return
        }
        if (ref.gamePath in unstorable || store.read(ref) != null || store.isMiss(ref.gamePath)) return
        val r = tryOr({ e -> stoppedUntil = stopAfter(e); null }) { lookup(ref) }
        when (r) {
            null -> Unit
            is InfoResult.Found ->
                if (store.write(ref, StoredGameInfo.of(r.info, r.ssId, now()))) _fetched.emit(ref.gamePath) else unstorable += ref.gamePath
            InfoResult.Missing -> store.markMiss(ref.gamePath)
            InfoResult.Failed -> stoppedUntil = now() + RETRY_MS
        }
    }
}
