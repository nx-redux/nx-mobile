package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.tryQuietly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.TimeSource

private val processStart = TimeSource.Monotonic.markNow()

/** The artwork façade for the UI and onboarding: one job at a time (appends chain onto it), persistence, account, Reset. */
class ArtworkController(
    private val host: ArtworkHost, engine: ScrapeEngineFactory, private val credentials: ScraperCredentials,
    private val runner: ScrapeRunner, private val scope: CoroutineScope,
    /** Where the job and the suspend calls run (Android: Dispatchers.Default); empty = the caller's dispatcher. */
    private val work: CoroutineContext = EmptyCoroutineContext,
    /** Monotonic milliseconds, for spacing the running job's saves. */
    private val clock: () -> Long = { processStart.elapsedNow().inWholeMilliseconds },
    /** The Change art previews (app-private); none = every preview is downloaded again. */
    private val optionCache: ArtOptionCache = ArtOptionCache.NONE,
) : ArtOptionsPort {
    companion object {
        const val JOB_FILE = "scrape_job.json"
        const val LOG_FILE = "written_art.txt"
        /** A running job is saved once this many more games finished, or [SAVE_EVERY_MS] after its last save. */
        const val SAVE_EVERY_ITEMS = 10
        const val SAVE_EVERY_MS = 5_000L
        /** Change art previews downloaded at once, at most. */
        const val PREVIEW_DOWNLOADS = 3
        /** The file slots an option is picked into ([ArtSlot.fileSlot] of [OPTION_SLOTS]). */
        private val PICK_SLOTS = listOf(ArtSlot.SCREENSHOT, ArtSlot.BOX_2D, ArtSlot.BOX_3D, ArtSlot.WHEEL, ArtSlot.MIX)
    }

    private fun auth() = ScraperAuth(credentials.devId, credentials.devPassword, user = credentials.user, password = credentials.password)
    private val log = ArtLogStore(host, LOG_FILE)
    /** The hand-placed heroes the one-time move put in `screenshot/` (unlogged, still heroes). */
    private val movedHeroes = MovedHeroes(host)
    /** The log as the engine reads it: a moved hand-placed hero is a hero too, as the scan has it ([heroShots]). */
    private val engineLog = object : LoggedArt by log {
        override suspend fun hero(path: String): Boolean = log.origin(path)?.source == SourceId.SGDB || movedHeroes.contains(path)
    }
    /** The Change art option links, recorded by every fetch. */
    private val store = ArtOptionStore(host)
    private val engine: ScrapeEngine = engine(
        ::auth, host::applyArt, { written -> log.add(written); movedHeroes.overwritten(written.map { it.first }) },
        host::refreshAndroidConsole, engineLog, store::record, store::pickedSlots,
    )
    private val previews = Semaphore(PREVIEW_DOWNLOADS)

    /** Game info beside the art; null when the engine keeps none. */
    val info: GameInfoStore? get() = engine.info

    /** Any source is on; fetching works in a build without ScreenScraper's credentials when another source is. */
    val available: Boolean get() = engine.sources.any { it.enabled }
    /** ScreenScraper's build credentials are present (its account rows). */
    val ssAvailable: Boolean get() = engine.ss?.enabled == true
    /** TheGamesDB has the user's key (its allowance line). */
    val tgdbAvailable: Boolean get() = engine.tgdb?.enabled == true

    /** An enabled source knows [ref]'s system (an excluded tag never). */
    fun fetchable(ref: LibraryGameRef): Boolean = engine.sourcesFor(ref).isNotEmpty()

    /**
     * The game menu's Fetch art: a game file [fileName] in a console tagged [tag] that an enabled source knows; [android]:
     * an Android games console's app ([fileName] its app id).
     */
    fun canFetch(tag: String, fileName: String, android: Boolean = false): Boolean = fetchable(
        LibraryGameRef("", tag, fileName, fileName, ContentRef(""), "", "", folderGame = false, have = ArtHave(), android = android),
    )
    private val _state = MutableStateFlow(ScrapeState())
    val state: StateFlow<ScrapeState> = _state.asStateFlow()

    /** Guards [job], [pending], [replace], [retryMisses], [single] and every phase change. */
    private val lock = Mutex()
    /**
     * Held by [halt] from its check to the phase it records, taken before [lock]. The stopped job winds down meanwhile
     * with [lock] free (its last emission takes it), so what would start, restore or forget a job waits here instead.
     */
    private val haltLock = Mutex()
    /** Non-null exactly while a run loop is live: the only gate for starting one. */
    private var job: Job? = null
    /** Games queued while running; the run loop chains onto them when the engine's run ends. */
    private var pending: List<ScrapeItem> = emptyList()
    private var replace = false
    /** A Fetch all or Replace run is (or was) queued in the job: its games' info misses are asked again ([ScrapeEngine.run]). */
    private var retryMisses = false
    /**
     * The engine's end state of the live run, from its return until the loop merges it under [lock]. A stop landing in
     * between (the merge's lock wait is cancellable) leaves it here for [halt] to record after its join, so the games
     * finished since the run's last emission are never lost to the stop.
     */
    @Volatile private var runEnd: ScrapeState? = null
    /** The game a [fetchGame] is scraping outside the job (never queued meanwhile). */
    private var single: String? = null
    private val singleLock = Mutex()
    /**
     * One pick at a time, and none during Reset artwork. Not [singleLock]: a pick goes through while the art panel's
     * own fetch runs, as during a job (the engine reads the picks right before each write).
     */
    private val pickLock = Mutex()
    /** Guards [progress]; never waits on [lock]. */
    private val aliveLock = Mutex()
    private var progress: String? = null
    /** The done count and time of the running job's last save ([publish]). */
    private var savedDone = 0
    private var savedAt = 0L

    /** Games without art (no box or no background, or no mix with Mix on); each is asked only for what it lacks. */
    fun fetchMissing(): Job? = enqueue(false, retry = false) { !it.have.complete }
    fun fetchConsole(tags: Set<String>, replaceExisting: Boolean = false): Job? {
        val up = tags.mapTo(HashSet()) { it.uppercase() }
        return enqueue(replaceExisting, retry = replaceExisting) { it.tag.uppercase() in up && (replaceExisting || !it.have.complete) }
    }
    fun fetchAll(replaceExisting: Boolean): Job? = enqueue(replaceExisting, retry = true) { replaceExisting || !it.have.complete }

    fun missingCount(games: List<LibraryGameRef>): Int = games.count { fetchable(it) && !it.have.complete }

    /**
     * Running: appends the games not already queued. Paused: merges them in and resumes. Otherwise a new job. Before any
     * job this process, the persisted paused job is loaded first (as [restorePending]), so a fetch started before the
     * restore has run merges into it instead of overwriting the saved queue. Returns the queuing (null when unavailable):
     * once it ends, [state] shows the job the games went into (Progress waits on it).
     */
    private fun enqueue(replaceExisting: Boolean, retry: Boolean, pick: (LibraryGameRef) -> Boolean): Job? {
        if (!available) return null
        return scope.launch(work) {
            val picked = host.libraryGames().filter { fetchable(it) && pick(it) }
                .distinctBy { it.gamePath }.map { ScrapeItem(it.gamePath, it.tag, it.name) }
            haltLock.withLock { lock.withLock {
                if (job == null && _state.value.phase == JobPhase.IDLE) {
                    tryQuietly { restoreLocked() }   // unreadable: a new job
                }
                val s = _state.value
                val queued = if (job != null || s.phase == JobPhase.PAUSED) s.items.mapTo(HashSet()) { it.gamePath } else HashSet()
                single?.let { queued += it }
                val add = picked.filter { it.gamePath !in queued }
                when {
                    job != null -> if (add.isNotEmpty()) {
                        replace = replace || replaceExisting
                        retryMisses = retryMisses || retry
                        pending = pending + add
                        _state.value = s.copy(items = s.items + add)
                    }
                    s.phase == JobPhase.PAUSED -> { replace = replace || replaceExisting; retryMisses = retryMisses || retry; startLocked(s.copy(items = s.items + add)) }
                    add.isNotEmpty() -> { replace = replaceExisting; retryMisses = retry; startLocked(ScrapeState(JobPhase.RUNNING, add, quota = s.quota, tgdbLeft = s.tgdbLeft)) }
                }
            } }
        }
    }

    private suspend fun startLocked(s: ScrapeState) {
        val start = s.copy(phase = JobPhase.RUNNING, pauseReason = null, notes = emptyMap())   // stopped sources are asked again
        _state.value = start
        persist(start)   // a process killed before the first game finishes still finds this queue (not the last job's)
        runEnd = null
        job = scope.launch(work) { runLoop(start) }
    }

    /**
     * The host's batched art results written and shown ([ArtworkHost.flushArt]) at a run's end, however it ends; a host
     * failure there is not the run's.
     */
    private suspend fun flushArt() = withContext(NonCancellable) {
        try { host.flushArt() } catch (e: CancellationException) { throw e } catch (_: Throwable) {}
    }

    /** Runs the engine, chaining onto games appended meanwhile; publishes the end, and stops keep-alive, exactly once. */
    private suspend fun runLoop(first: ScrapeState) {
        var start = first
        try {
            while (true) {
                singleLock.withLock {}   // a fetchGame in flight finishes first: its game is never scraped twice at once
                val refs = host.libraryGames().associateBy { it.gamePath }
                // Bulk jobs save the options found on the way, never the wide pass.
                val (rep, retry) = lock.withLock { replace to retryMisses }
                runEnd = engine.run(start, { refs[it] }, rep, wide = false, retryMisses = retry) { publish(it) }
                start = lock.withLock {
                    // Stopped since the run returned: [halt] records the phase from [runEnd] once this job is gone.
                    currentCoroutineContext().ensureActive()
                    val end = runEnd!!
                    runEnd = null
                    val merged = end.copy(items = end.items + pending)
                    pending = emptyList()
                    if (merged.phase == JobPhase.DONE && merged.items.any { it.status == ItemStatus.WAITING }) return@withLock merged.copy(phase = JobPhase.RUNNING)
                    finishLocked(merged)
                    null
                } ?: break
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { keepAlive(null) }   // halt() holds [haltLock] meanwhile: no new job can start
            throw e
        } catch (t: Throwable) {
            // Thrown while being cancelled (an Error met in non-cancellable work): halt() records the phase itself.
            if (!currentCoroutineContext().isActive) { withContext(NonCancellable) { keepAlive(null) }; return }
            // A host or provider failure outside the engine's handling, or an Error (out of memory): keep the queue,
            // paused, with the job cleared and keep-alive stopped so Resume can start it again. The library folder's
            // write failures are the engine's own pause; anything here is not one of them.
            val reason = (t as? JobPause)?.reason ?: ScrapeEngine.FAILED
            withContext(NonCancellable) {
                lock.withLock { finishLocked(takeShownLocked().inFlightToWaiting().copy(phase = JobPhase.PAUSED, pauseReason = reason)) }
            }
        } finally {
            flushArt()   // done, paused or cancelled: the art fetched so far reaches the index file and the menu
        }
    }

    /**
     * Under [lock], for a job that ends without the loop's merge: the job as last known, which is the run's end state
     * ([runEnd], with the games queued meanwhile) when the run had returned, as it holds the games never emitted.
     */
    private fun takeShownLocked(): ScrapeState {
        val shown = runEnd?.let { it.copy(items = it.items + pending) } ?: _state.value
        runEnd = null
        pending = emptyList()   // in the shown items either way
        return shown
    }

    private suspend fun finishLocked(s: ScrapeState) {
        _state.value = s; job = null; pending = emptyList()   // [s] already shows the pending games
        persist(s)
        keepAlive(null)
    }

    /**
     * A running update from the engine (its terminal one is left to [runLoop]). Saved when a game finished and
     * [SAVE_EVERY_ITEMS] games or [SAVE_EVERY_MS] passed since the last save, not per game: the job is saved whole on
     * every start, pause, cancel and end, and a process killed in between redoes at most those games.
     */
    private suspend fun publish(s: ScrapeState) {
        if (s.phase != JobPhase.RUNNING) return
        val text = lock.withLock {
            val shown = s.copy(items = s.items + pending)
            val before = _state.value.done
            _state.value = shown
            if (shown.done != before && (shown.done - savedDone >= SAVE_EVERY_ITEMS || shown.done < savedDone || clock() - savedAt >= SAVE_EVERY_MS)) persist(shown)
            "Fetching art ${shown.done}/${shown.total}"
        }
        keepAlive(text)
    }

    /** Tells the runner only about changes (null = stop). */
    private suspend fun keepAlive(text: String?) = aliveLock.withLock {
        if (text == progress) return@withLock
        progress = text
        runner.keepAlive(text != null, text)
    }

    private suspend fun persist(s: ScrapeState) {
        savedDone = s.done; savedAt = clock()
        tryQuietly { host.writePrivate(JOB_FILE, ScrapeJobCodec.encode(s, replace)) }
    }

    fun pause() { scope.launch(work) { halt(JobPhase.PAUSED) } }

    /** Forgets a finished (done or cancelled) job, stored copy included: no Progress until the next fetch. */
    suspend fun clear() = withContext(work) {
        haltLock.withLock { lock.withLock {
            val s = _state.value
            if (job != null || (s.phase != JobPhase.DONE && s.phase != JobPhase.CANCELLED)) return@withLock
            val idle = ScrapeState(quota = s.quota, tgdbLeft = s.tgdbLeft)
            _state.value = idle
            persist(idle)
        } }
    }
    fun cancel() { scope.launch(work) { halt(JobPhase.CANCELLED) } }

    /**
     * Stops a running job (its in-flight item goes back to waiting) and records [phase]; a paused job can be cancelled.
     * [only] (checked under the lock) limits it to a running job it accepts. True when it stopped or cancelled a job.
     * The job is waited for outside [lock]: its cancellation emits the games it finished since its last emission
     * ([publish] takes [lock]), and those are in the phase recorded; a run that had already returned left them in
     * [runEnd] instead.
     */
    private suspend fun halt(phase: JobPhase, only: ((ScrapeState) -> Boolean)? = null): Boolean = haltLock.withLock {
        var running: Job? = null
        val stop = lock.withLock {
            running = job
            when {
                job == null -> phase == JobPhase.CANCELLED && _state.value.phase == JobPhase.PAUSED
                only != null && !only(_state.value) -> false
                else -> { job = null; running?.cancel(); true }
            }
        }
        if (!stop) return@withLock false
        withContext(NonCancellable) {
            running?.join()
            lock.withLock {
                val s = takeShownLocked().inFlightToWaiting().copy(phase = phase, pauseReason = null)
                _state.value = s
                persist(s)
            }
        }
        true
    }

    fun resume() {
        scope.launch(work) {
            haltLock.withLock { lock.withLock { if (_state.value.phase == JobPhase.PAUSED && job == null && available) startLocked(_state.value) } }
        }
    }

    /** Reads the persisted job; true when a paused job with waiting games can be resumed. */
    suspend fun restorePending(): Boolean = withContext(work) {
        haltLock.withLock { lock.withLock { job == null && restoreLocked() } }
    }

    /** Under [lock] with no job: loads the persisted job when it is paused with waiting games; true when it did. */
    private suspend fun restoreLocked(): Boolean {
        val (s, r) = host.readPrivate(JOB_FILE)?.let { ScrapeJobCodec.decode(it) } ?: return false
        if (s.phase != JobPhase.PAUSED || s.items.none { it.status == ItemStatus.WAITING }) return false
        replace = r
        // Not saved with the job: a restored one asks misses again only when it replaces.
        retryMisses = r
        _state.value = s.copy(quota = _state.value.quota, tgdbLeft = _state.value.tgdbLeft)
        return true
    }

    /**
     * One game in the caller's coroutine (the art panel's Fetch art). As a job does, it asks only for
     * the kinds the game lacks and never overwrites art already there unless [replaceExisting] (Replace existing art).
     * A game the running job still has queued is not fetched twice: this waits for the job's result. A pause case comes
     * back as ERROR with the pause reason. While a job runs, the shared sources' per-run state is the job's: this run
     * does not start them again ([ScrapeEngine.run]'s `startSources`). [wide]: the Change art search ([ScrapeEngine.run]'s
     * wide pass). [found]: its own run's options so far ([ScrapeEngine.run]'s); none when the job fetched the game.
     */
    override suspend fun fetchGame(gamePath: String, replaceExisting: Boolean, wide: Boolean, found: suspend (List<ArtOption>) -> Unit): FetchResult =
        fetchOne(gamePath, replaceExisting, wide, found).first

    /** A job or a single-game fetch is asking the sources (the top screen's info fetch waits it out). */
    val busy: Boolean get() = job != null || single != null || _state.value.phase == JobPhase.RUNNING

    /** ScreenScraper's last quota read and when (epoch millis), or null when none was read (anonymous use never reads one). */
    val ssQuotaRead: Pair<SsQuota, Long>? get() = engine.ss?.lastQuotaRead

    /**
     * The top screen's info-only lookup of [ref]: null when not asked (no ScreenScraper for it, or a job or a fetch is
     * live). Under [singleLock] as [fetchOne], so it never runs beside a run's requests (ScreenScraper's one-thread
     * rule); a job or a user's Fetch art started meanwhile waits for this one lookup (its requests and their back-off). Throws [SourceStop] / [JobPause] as the source does.
     */
    internal suspend fun lookupInfo(ref: LibraryGameRef): InfoResult? = withContext(work) {
        val ss = engine.ss?.takeIf { !ref.android && it.enabled && it.knows(ref) } ?: return@withContext null
        singleLock.withLock {
            if (haltLock.withLock { lock.withLock { job != null } }) null else ss.info(ref)
        }
    }

    /** [fetchGame], with the sources' stop notes of its own run (none when the job fetched the game). */
    private suspend fun fetchOne(
        gamePath: String, replaceExisting: Boolean, wide: Boolean, found: suspend (List<ArtOption>) -> Unit = {},
    ): Pair<FetchResult, Map<String, String>> = withContext(work) {
        if (!available) return@withContext FetchResult(ItemStatus.SKIPPED, "Fetching isn't available") to emptyMap()
        val ref = host.libraryGames().firstOrNull { it.gamePath == gamePath }
            ?: return@withContext FetchResult(ItemStatus.SKIPPED, "Not in the library") to emptyMap()
        var result: FetchResult? = null
        var notes: Map<String, String> = emptyMap()
        singleLock.withLock {
            // A job started after this check waits for [singleLock] before its run: the two never start the sources at once.
            // [haltLock]: a stopped job still winding down (its in-flight game, its sources' end) is waited for, so this
            // run never starts the shared sources under it.
            var jobLive = false
            val inJob = haltLock.withLock { lock.withLock {
                jobLive = job != null
                (jobLive && _state.value.items.any { it.gamePath == gamePath && it.status.inFlightOrWaiting }).also { if (!it) single = gamePath }
            } }
            if (!inJob) try {
                val one = ScrapeState(items = listOf(ScrapeItem(ref.gamePath, ref.tag, ref.name)))
                val end = engine.run(one, { if (it == gamePath) ref else null }, replace = replaceExisting, startSources = !jobLive, wide = wide, found = { _, o -> found(o) }) {}
                notes = end.notes
                end.quota?.let { q -> _state.update { it.copy(quota = q) } }
                end.tgdbLeft?.let { n -> _state.update { it.copy(tgdbLeft = n) } }
                val item = end.items.single()
                result = if (item.status == ItemStatus.WAITING) FetchResult(ItemStatus.ERROR, stopReason(end)) else FetchResult(item.status, item.reason)
            } finally {
                withContext(NonCancellable) { lock.withLock { single = null } }
                flushArt()   // the one game's art shows now, not after the host's batching pause
            }
        }
        (result ?: awaitJobItem(gamePath)) to notes
    }

    /**
     * A game's Change art list. The saved list when the wide search ran for it; otherwise (none saved, only a fetch's
     * links, or [searchAgain]) the game is fetched with the wide search first ([fetchGame], [replaceExisting] as set).
     * [autoSearch] false: only [searchAgain] searches (the caller has just run the wide fetch itself). [found]: the
     * search's options so far, as [fetchGame]'s.
     */
    override suspend fun artOptions(
        gamePath: String, replaceExisting: Boolean, searchAgain: Boolean, autoSearch: Boolean, found: suspend (List<ArtOption>) -> Unit,
    ): ArtOptionsResult = withContext(work) {
        var saved = store.get(gamePath)
        var note: String? = null
        if (searchAgain || (autoSearch && (saved == null || !saved.wide))) {
            val (r, notes) = fetchOne(gamePath, replaceExisting, wide = true, found)
            note = notes.values.joinToString(" · ").ifEmpty { null }
                ?: r.reason.takeIf { r.status == ItemStatus.ERROR || r.status == ItemStatus.SKIPPED }
            saved = store.get(gamePath)
        }
        val ref = host.libraryGames().firstOrNull { it.gamePath == gamePath }
        ArtOptionsResult(saved?.options.orEmpty(), ref?.let { currentKeys(it, saved) }.orEmpty(), note)
    }

    /**
     * Per file slot, the option [ref]'s file there is: the pick when it is still listed, else the one (of that slot)
     * matching the written-art log's origin (source and asset id) for the slot's file; no entry for a hand-placed file,
     * an extra folder's own, or none. The 2D box's legacy `box2d/` file is never matched. An Android game's art is
     * app-private, outside the tree and the log: only its picks.
     */
    private suspend fun currentKeys(ref: LibraryGameRef, saved: GameOptions?): Map<ArtSlot, String> {
        val options = saved?.options.orEmpty()
        if (options.isEmpty()) return emptyMap()
        val t = if (ref.android) null
            else try { ArtPaths.target(ref.sourceId, ref.relativeDir, ref.fileName, ref.folderGame) } catch (_: IllegalArgumentException) { null }
        val disk = t?.let { engine.artOnDisk(ref, heroes = false) }
        val out = LinkedHashMap<ArtSlot, String>()
        for (slot in PICK_SLOTS) {
            val mine = options.filter { it.slot.fileSlot == slot }
            saved?.picks?.get(slot)?.let { p -> if (mine.any { it.key == p }) { out[slot] = p; continue } }
            if (t == null || disk == null) continue
            val file = disk.of(slot) ?: continue
            // An extra folder's game: the index's file there is the folder's own, not the mirror's.
            val shown = ref.art.of(slot)
            if (ref.sourceId != GamePath.HOME && shown != null && shown != file) continue
            val enc = ArtEncoding.entries.firstOrNull { it.ext == file.substringAfterLast('.').lowercase() } ?: continue
            val origin = log.origin(t.path(slot, enc)) ?: continue
            mine.firstOrNull { it.source == origin.source && it.assetId != null && it.assetId == origin.assetId }?.let { out[slot] = it.key }
        }
        return out
    }

    /** The ref of this art's file in the file [slot]. */
    private fun ArtRefs.of(slot: ArtSlot): String? = when (slot) {
        ArtSlot.SCREENSHOT -> screenshot
        ArtSlot.BOX_2D -> box2d
        ArtSlot.BOX_3D -> boxart
        ArtSlot.WHEEL -> wheel
        ArtSlot.MIX -> mix
        else -> null
    }

    /**
     * [option]'s preview: the cached image, else its thumbnail link (the full image when it has none) downloaded
     * (ScreenScraper's with the current login) and cached. A link that answered but gave no image is dropped from the
     * list (never a pick); no connection keeps it. Null when there is no image to show ([OptionImage.dropped] when the
     * option was dropped).
     */
    override suspend fun optionImage(gamePath: String, option: ArtOption): OptionImage = withContext(work) {
        optionCache.ref(cacheKey(gamePath, option.key))?.let { return@withContext OptionImage(it) }
        val got = downloadPreview(gamePath, option)
        val bytes = got.bytes ?: return@withContext OptionImage(null, got.dropped)
        OptionImage(optionCache.write(cacheKey(gamePath, option.key), bytes))
    }

    /** A download's [bytes] (null: none); [dropped]: the link answered without an image and the option was dropped. */
    private class Download(val bytes: ByteArray?, val dropped: Boolean = false)

    /** [key]'s preview in the cache: per game, as an option's key is unique only within its game. */
    private fun cacheKey(gamePath: String, key: String): String = gamePath + "\n" + key

    /**
     * [option]'s preview from its thumbnail link, else its full link (at most [PREVIEW_DOWNLOADS] at once); see
     * [optionImage] for a miss.
     */
    private suspend fun downloadPreview(gamePath: String, option: ArtOption): Download {
        val bytes = try {
            previews.withPermit { download(option, option.thumb ?: option.url) }
        } catch (_: JobPause) {
            return Download(null)   // no connection: the option stays
        }
        if (bytes != null) return Download(bytes)
        if (store.get(gamePath)?.picks.orEmpty().containsValue(option.key)) return Download(null)   // never a pick
        store.dropOption(gamePath, option.key)
        return Download(null, dropped = true)
    }

    /** [url] of [option] (ScreenScraper's with the current login, as its links are saved without one). */
    private suspend fun download(option: ArtOption, url: String): ByteArray? =
        engine.downloadImage(if (option.source == SourceId.SS) ScraperRequest.withAuth(url, auth()) else url)

    /**
     * The full image a pick writes: [option]'s full link downloaded, not cached (the cache holds previews only); an
     * option without a thumbnail uses its cached preview, which is that image (else downloaded and cached as one).
     * Null when it cannot be had (no connection or a dead link); a pick's failed download never drops the option
     * ([downloadPreview]'s drop is the preview's only).
     */
    private suspend fun fullImage(gamePath: String, option: ArtOption): ByteArray? {
        if (option.thumb == null) optionCache.read(cacheKey(gamePath, option.key))?.let { return it }
        val bytes = try { download(option, option.url) } catch (_: JobPause) { null }
        if (option.thumb == null && bytes != null) optionCache.write(cacheKey(gamePath, option.key), bytes)
        return bytes
    }

    /**
     * Writes [option] into its file slot ([ArtSlot.fileSlot]: a grid into the 2D box, a hero into the screenshot) and
     * marks it picked there, so no fetch replaces it. Picking the slot's current one only marks it picked (no write).
     * The full image is downloaded ([fullImage]); the pick is marked before the write (a running job reads it) and a
     * failed write puts the previous pick back. False when the image could not be had or written.
     */
    override suspend fun pickOption(gamePath: String, option: ArtOption): Boolean = withContext(work) {
        val ref = host.libraryGames().firstOrNull { it.gamePath == gamePath } ?: return@withContext false
        pickLock.withLock {
            val saved = store.get(gamePath)
            val slot = option.slot.fileSlot
            if (option.key == currentKeys(ref, saved)[slot]) {
                if (saved?.picks?.get(slot) != option.key) withContext(NonCancellable) { store.setPick(gamePath, slot, option.key) }
                return@withLock true
            }
            val bytes = fullImage(gamePath, option) ?: return@withLock false
            withContext(NonCancellable) {
                val previous = saved?.picks?.get(slot)
                store.setPick(gamePath, slot, option.key)
                val art = try {
                    engine.writePick(ref, slot, bytes, option)
                } catch (t: Throwable) {
                    store.setPick(gamePath, slot, previous); throw t
                }
                if (art == null) { store.setPick(gamePath, slot, previous); return@withContext false }
                host.applyArt(gamePath, art)
                flushArt()
                true
            }
        }
    }

    /**
     * The picked file slots per game ([GameOptions.picks]): the library scan shows an extra folder game's picked slots
     * from its home mirror, where the pick is written.
     */
    suspend fun picks(): Map<String, Set<ArtSlot>> = withContext(work) { store.picks() }

    /**
     * The scan's one-time `grid/` and `hero/` move ([LegacyArtMove]) over [source], keeping this controller's
     * written-art log in step.
     */
    fun legacyArtMove(source: RomsSource): LegacyArtMove = LegacyArtMove(source, log, host, movedHeroes)

    /**
     * The logged screenshots that are SteamGridDB heroes, and the hand-placed heroes the one-time move kept
     * ([MovedHeroes]), as home-relative file paths (`Roms/GB/.media/screenshot/Tetris.jpg`): the scan's
     * [ArtRefs.heroShot], as the engine's [LoggedArt.hero] reads them.
     */
    suspend fun heroShots(): Set<String> = withContext(work) {
        val logged = log.read { l ->
            l.entries.filterTo(HashSet()) { path ->
                val parts = path.split('/')
                parts.size >= 3 && parts[parts.size - 2] == ArtSlot.SCREENSHOT.folder && parts[parts.size - 3] == ".media" &&
                    l.origin(path)?.source == SourceId.SGDB
            }
        }
        logged + movedHeroes.all()
    }

    /** The bytes the Change art previews take. */
    suspend fun optionCacheSize(): Long = withContext(work) { optionCache.size() }

    /** Deletes every Change art preview (the links stay); returns the bytes freed. */
    suspend fun clearOptionCache(): Long = withContext(work) { optionCache.clear() }

    /** Why a run left its game waiting: the stopped sources' notes when every source stopped, else the pause reason. */
    private fun stopReason(end: ScrapeState): String? =
        if (end.pauseReason == ScrapeEngine.ALL_STOPPED && end.notes.isNotEmpty()) end.notes.values.joinToString(" · ") else end.pauseReason

    private suspend fun awaitJobItem(gamePath: String): FetchResult {
        val s = state.first { st -> st.phase != JobPhase.RUNNING || st.items.firstOrNull { it.gamePath == gamePath }?.status?.inFlightOrWaiting == false }
        val item = s.items.firstOrNull { it.gamePath == gamePath }
        return if (item == null || item.status.inFlightOrWaiting) FetchResult(ItemStatus.ERROR, stopReason(s) ?: "Stopped") else FetchResult(item.status, item.reason)
    }

    /** TheGamesDB's requests left this month, read with the free `API/Limit` (null without a key or when unreadable). */
    suspend fun tgdbAllowance(): Int? = withContext(work) {
        engine.tgdb?.takeIf { it.enabled }?.allowance()?.also { n -> _state.update { it.copy(tgdbLeft = n) } }
    }

    /** The build has SteamGridDB (its key rows on Account). */
    val sgdbAvailable: Boolean get() = engine.sgdb != null
    /** The user's SteamGridDB key is set (the source is on). */
    val sgdbKeySet: Boolean get() = !credentials.sgdbKey.isNullOrEmpty()

    /** Checks [key] with SteamGridDB and stores it only when the check answers OK; anything else leaves the old one. */
    suspend fun setSgdbKey(key: String): KeyResult = withContext(work) {
        val k = key.trim()
        val sgdb = engine.sgdb ?: return@withContext KeyResult.UNAVAILABLE
        if (k.isEmpty()) return@withContext KeyResult.BAD_KEY
        sgdb.check(k).also { if (it == KeyResult.OK) credentials.sgdbKey = k }
    }

    fun clearSgdbKey() { credentials.sgdbKey = null }

    /** The engine has TheGamesDB (its key rows on Account). */
    val tgdbSupported: Boolean get() = engine.tgdb != null
    /** The user's own TheGamesDB key is set (TheGamesDB is off without it). */
    val tgdbKeySet: Boolean get() = !credentials.tgdbUserKey.isNullOrEmpty()

    /**
     * Checks [key] with TheGamesDB's free `API/Limit` and stores it only when the check answers OK; anything else leaves
     * the old one. A new key's allowance is unknown until read ([tgdbAllowance]).
     */
    suspend fun setTgdbKey(key: String): KeyResult = withContext(work) {
        val k = key.trim()
        val tgdb = engine.tgdb ?: return@withContext KeyResult.UNAVAILABLE
        if (k.isEmpty()) return@withContext KeyResult.BAD_KEY
        tgdb.check(k).also {
            if (it == KeyResult.OK) { credentials.tgdbUserKey = k; _state.update { s -> s.copy(tgdbLeft = null) } }
        }
    }

    /** Drops the user's TheGamesDB key: TheGamesDB is off again and its allowance forgotten. */
    fun clearTgdbKey() {
        credentials.tgdbUserKey = null
        _state.update { it.copy(tgdbLeft = null) }
    }

    suspend fun refreshQuota(): SsQuota? = withContext(work) {
        engine.ss?.quota()?.also { q -> _state.update { it.copy(quota = q) } }
    }

    /**
     * Stores the login, then validates it with `ssuserInfos`. A bad login clears it; offline puts back the login before
     * it; OK, unavailable and the app refused keep the new one.
     */
    suspend fun signIn(user: String, password: String): SignInResult = withContext(work) {
        val before = credentials.user to credentials.password
        credentials.user = user; credentials.password = password
        val result = engine.ss?.checkLogin() ?: SignInResult.UNAVAILABLE
        when (result) {
            SignInResult.OK -> _state.update { it.copy(quota = engine.ss?.lastQuota) }
            SignInResult.BAD_LOGIN -> { credentials.user = null; credentials.password = null }
            // Offline: nothing was checked, so the login before it comes back. ScreenScraper down or refusing the app
            // (not the user) keeps the new one: logging in again later would not check it either.
            SignInResult.OFFLINE -> { credentials.user = before.first; credentials.password = before.second }
            SignInResult.UNAVAILABLE, SignInResult.BUILD_REFUSED -> Unit
        }
        result
    }

    fun signOut() {
        credentials.user = null; credentials.password = null
        engine.ss?.forgetQuota()
        _state.update { it.copy(quota = null) }
    }

    /**
     * Deletes the logged art files of the games in scope ([tags] null = every logged file: typed art, game info and the
     * app's legacy untyped mixes; one whose delete failed stays logged and uncounted), removes the slot and `.media` folders
     * that left empty, and, for Android's scope or All, the Android games console's stored screenshots; then re-reads
     * what is left on disk for every game whose art paths were touched (hidden games too), so hand-placed art stays in
     * the index. The scope's Change art lists, picks and previews are forgotten too (All: every preview). A running job
     * is paused only when it still has games in scope to fetch; an in-flight [fetchGame] finishes first.
     */
    suspend fun resetArt(tags: Set<String>?): ResetResult = withContext(work) {
        val up = tags?.mapTo(HashSet()) { it.uppercase() }
        val paused = halt(JobPhase.PAUSED) { s -> s.items.any { it.status.inFlightOrWaiting && (up == null || it.tag.uppercase() in up) } }
        singleLock.withLock { pickLock.withLock {   // a pick going through finishes first
            val games = host.allLibraryGames().distinctBy { it.gamePath }
            // By console: exactly the art paths of its games (a folder shared with another console's games is not a scope).
            val scopePaths = up?.let { games.filter { g -> g.tag.uppercase() in it }.flatMapTo(HashSet(), ::artPaths) }
            val (before, deleted) = log.edit { l ->
                val inScope = if (scopePaths == null) l.entries.toList() else l.entries.filter { it in scopePaths }
                val touched = inScope.toSet()
                // Each touched game's files before the deletes: tells the mirror's refs from an extra folder's own (refs
                // only: the hero flag would ask the log, whose lock this holds).
                val before = games.filter { g -> artPaths(g).any { it in touched } }.map { it to engine.artOnDisk(it, heroes = false) }
                val results = inScope.associateWith { engine.deleteArt(it) }
                // A file still there after a failed delete stays logged: still the app's, so a later Reset tries it again.
                l.remove(results.filterValues { it != ScrapeEngine.Deleted.FAILED }.keys)
                engine.removeEmptyArtDirs(inScope)
                before to results.count { it.value == ScrapeEngine.Deleted.DELETED }
            }
            engine.info?.forgetCached()
            // The Android games console's screenshots are app-private, outside the log: Android's scope or All deletes them.
            val android = up == null || ANDROID_CONSOLE_TAG in up
            val androidDeleted = if (android) engine.deleteAndroidArt() else 0
            // The scope's Change art lists, picks and previews go too; a failure there never fails the Reset. The Android
            // games are not in [games] (their art is outside the log): their picks would keep a fetch from storing again.
            tryQuietly {
                val keys = store.forget(up?.let { u ->
                    val paths = games.filter { g -> g.tag.uppercase() in u }.mapTo(HashSet()) { it.gamePath }
                    if (android) host.libraryGames().filter { it.android }.mapTo(paths) { it.gamePath }
                    paths
                })
                if (up == null) optionCache.clear() else optionCache.remove(keys.flatMap { (g, ks) -> ks.map { cacheKey(g, it) } })
            }
            for ((g, was) in before) host.applyArt(g.gamePath, ScrapeEngine.indexArt(g, was, engine.artOnDisk(g)))
            flushArt()   // one index write and one refresh for every game reset
            ResetResult(deleted + androidDeleted, paused)
        } }
    }

    /**
     * Every path the app may have written for [g]: each typed slot (the 2D box, wheel and mix among them) in both
     * encodings, the 2D box's legacy `box2d/` files, the legacy untyped mix and the game info file.
     */
    private fun artPaths(g: LibraryGameRef): List<String> {
        val t = try { ArtPaths.target(g.sourceId, g.relativeDir, g.fileName, g.folderGame) } catch (_: IllegalArgumentException) { return emptyList() }
        return ArtEncoding.entries.flatMap { e -> ArtSlot.entries.map { t.path(it, e) } + t.legacyBox2dPath(e) } + t.legacyMixPath + t.infoPath
    }
}
