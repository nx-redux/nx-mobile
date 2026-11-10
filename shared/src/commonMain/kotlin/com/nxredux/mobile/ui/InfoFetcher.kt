package com.nxredux.mobile.ui

import com.nxredux.mobile.gametime.LocalCalendar
import com.nxredux.mobile.gametime.systemLocalCalendar
import com.nxredux.mobile.scraper.GameInfoStore
import com.nxredux.mobile.scraper.InfoResult
import com.nxredux.mobile.scraper.LibraryGameRef
import com.nxredux.mobile.scraper.SourceStop
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
 * The top screen's background info fetch: the game the highlight rests on for [restMs] is looked up when it has no info
 * stored and no remembered miss, so scrolling a list asks nothing. One lookup at a time, none while an Artwork run asks
 * ScreenScraper. A refusal (the daily limit, a blocked app) stops it until the next local day, a failure (offline, an
 * unreadable answer) for [RETRY_MS]; silently either way: nothing here throws or tells the user.
 *
 * [follow] is called on [scope]'s thread (the UI's), and the state below is only touched there.
 */
class InfoFetcher(
    private val scope: CoroutineScope,
    private val store: GameInfoStore,
    /** ScreenScraper's info-only lookup; null: not asked (a run started meanwhile). May throw [SourceStop] or a pause. */
    private val lookup: suspend (LibraryGameRef) -> InfoResult?,
    private val enabled: () -> Boolean,
    private val runActive: () -> Boolean,
    /** False when the last quota read is at its daily limit. */
    private val quotaLeft: () -> Boolean,
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

    private suspend fun fetch(ref: LibraryGameRef) {
        // Queued behind another lookup while the highlight moved on: that game was not rested on any more.
        if (followed != ref.gamePath || !enabled() || runActive()) return
        val t = now()
        if (t < stoppedUntil) return
        if (!quotaLeft()) { stoppedUntil = calendar.dayStart(t, 1); return }
        if (ref.gamePath in unstorable || store.read(ref) != null || store.isMiss(ref.gamePath)) return
        val r = tryOr({ e -> stoppedUntil = if (e is SourceStop) calendar.dayStart(now(), 1) else now() + RETRY_MS; null }) { lookup(ref) }
        when (r) {
            null -> Unit
            is InfoResult.Found ->
                if (store.write(ref, StoredGameInfo.of(r.info, r.ssId, now()))) _fetched.emit(ref.gamePath) else unstorable += ref.gamePath
            InfoResult.Missing -> store.markMiss(ref.gamePath)
            InfoResult.Failed -> stoppedUntil = now() + RETRY_MS
        }
    }
}
