package com.nxredux.mobile.scraper

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.GamePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
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
    }

    private fun auth() = ScraperAuth(credentials.devId, credentials.devPassword, user = credentials.user, password = credentials.password)
    private val log = ArtLogStore(host, LOG_FILE)
    /** The Change art option links, recorded by every fetch. */
    private val store = ArtOptionStore(host)
    private val engine: ScrapeEngine = engine(::auth, host::applyArt, log::add, host::refreshAndroidConsole, log, store::record, store::picked)
    private val previews = Semaphore(PREVIEW_DOWNLOADS)

    /** Any source is on; fetching works in a build without ScreenScraper's credentials when another source is. */
    val available: Boolean get() = engine.sources.any { it.enabled }
    /** ScreenScraper's build credentials are present (its account rows). */
    val ssAvailable: Boolean get() = engine.ss?.enabled == true
    /** TheGamesDB has the user's key (its allowance line). */
    val tgdbAvailable: Boolean get() = engine.tgdb?.enabled == true
    /** The enabled sources' names in fetch order (the credit line). */
    val sourceLabels: List<String> get() = engine.sources.filter { it.enabled }.map { it.label }

    /** An enabled source knows [ref]'s system (an excluded tag never). */
    fun fetchable(ref: LibraryGameRef): Boolean = engine.sourcesFor(ref).isNotEmpty()

    /** The game menu's Fetch art: a game file [fileName] in a console tagged [tag] that an enabled source knows. */
    fun canFetch(tag: String, fileName: String): Boolean = fetchable(
        LibraryGameRef("", tag, fileName, fileName, ContentRef(""), "", "", folderGame = false, have = ArtHave()),
    )
    private val _state = MutableStateFlow(ScrapeState())
    val state: StateFlow<ScrapeState> = _state.asStateFlow()

    /** Guards [job], [pending], [replace], [single] and every phase change. */
    private val lock = Mutex()
    /** Non-null exactly while a run loop is live: the only gate for starting one. */
    private var job: Job? = null
    /** Games queued while running; the run loop chains onto them when the engine's run ends. */
    private var pending: List<ScrapeItem> = emptyList()
    private var replace = false
    /** The game a [fetchGame] is scraping outside the job (never queued meanwhile). */
    private var single: String? = null
    private val singleLock = Mutex()
    /** Guards [progress]; never waits on [lock]. */
    private val aliveLock = Mutex()
    private var progress: String? = null
    /** The done count and time of the running job's last save ([publish]). */
    private var savedDone = 0
    private var savedAt = 0L

    /** Games without art (no box or no background, or no mix with Generate mix on); each is asked only for what it lacks. */
    fun fetchMissing(): Job? = enqueue(false) { !it.have.complete }
    fun fetchConsole(tags: Set<String>, replaceExisting: Boolean = false): Job? {
        val up = tags.mapTo(HashSet()) { it.uppercase() }
        return enqueue(replaceExisting) { it.tag.uppercase() in up && (replaceExisting || !it.have.complete) }
    }
    fun fetchAll(replaceExisting: Boolean): Job? = enqueue(replaceExisting) { replaceExisting || !it.have.complete }

    fun missingCount(games: List<LibraryGameRef>): Int = games.count { fetchable(it) && !it.have.complete }

    /**
     * Running: appends the games not already queued. Paused: merges them in and resumes. Otherwise a new job. Before any
     * job this process, the persisted paused job is loaded first (as [restorePending]), so a fetch started before the
     * restore has run merges into it instead of overwriting the saved queue. Returns the queuing (null when unavailable):
     * once it ends, [state] shows the job the games went into (Progress waits on it).
     */
    private fun enqueue(replaceExisting: Boolean, pick: (LibraryGameRef) -> Boolean): Job? {
        if (!available) return null
        return scope.launch(work) {
            val picked = host.libraryGames().filter { fetchable(it) && pick(it) }
                .distinctBy { it.gamePath }.map { ScrapeItem(it.gamePath, it.tag, it.name) }
            lock.withLock {
                if (job == null && _state.value.phase == JobPhase.IDLE) {
                    try { restoreLocked() } catch (e: CancellationException) { throw e } catch (_: Exception) {}   // unreadable: a new job
                }
                val s = _state.value
                val queued = if (job != null || s.phase == JobPhase.PAUSED) s.items.mapTo(HashSet()) { it.gamePath } else HashSet()
                single?.let { queued += it }
                val add = picked.filter { it.gamePath !in queued }
                when {
                    job != null -> if (add.isNotEmpty()) {
                        replace = replace || replaceExisting
                        pending = pending + add
                        _state.value = s.copy(items = s.items + add)
                    }
                    s.phase == JobPhase.PAUSED -> { replace = replace || replaceExisting; startLocked(s.copy(items = s.items + add)) }
                    add.isNotEmpty() -> { replace = replaceExisting; startLocked(ScrapeState(JobPhase.RUNNING, add, quota = s.quota, tgdbLeft = s.tgdbLeft)) }
                }
            }
        }
    }

    private suspend fun startLocked(s: ScrapeState) {
        val start = s.copy(phase = JobPhase.RUNNING, pauseReason = null, notes = emptyMap())   // stopped sources are asked again
        _state.value = start
        persist(start)   // a process killed before the first game finishes still finds this queue (not the last job's)
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
                val end = engine.run(start, { refs[it] }, lock.withLock { replace }, wide = false) { publish(it) }
                start = lock.withLock {
                    val merged = end.copy(items = end.items + pending)
                    pending = emptyList()
                    if (merged.phase == JobPhase.DONE && merged.items.any { it.status == ItemStatus.WAITING }) return@withLock merged.copy(phase = JobPhase.RUNNING)
                    finishLocked(merged)
                    null
                } ?: break
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { keepAlive(null) }   // halt() holds the lock meanwhile: no new job can start
            throw e
        } catch (t: Throwable) {
            // Thrown while being cancelled (an Error met in non-cancellable work): halt() holds the lock while it waits
            // for this job and records the phase itself, so waiting for the lock here would never end.
            if (!currentCoroutineContext().isActive) { withContext(NonCancellable) { keepAlive(null) }; return }
            // A host or provider failure outside the engine's handling, or an Error (out of memory): keep the queue,
            // paused, with the job cleared and keep-alive stopped so Resume can start it again. The library folder's
            // write failures are the engine's own pause; anything here is not one of them.
            val reason = (t as? JobPause)?.reason ?: ScrapeEngine.FAILED
            withContext(NonCancellable) {
                lock.withLock { finishLocked(_state.value.inFlightToWaiting().copy(phase = JobPhase.PAUSED, pauseReason = reason)) }
            }
        } finally {
            flushArt()   // done, paused or cancelled: the art fetched so far reaches the index file and the menu
        }
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
        try { host.writePrivate(JOB_FILE, ScrapeJobCodec.encode(s, replace)) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    fun pause() { scope.launch(work) { halt(JobPhase.PAUSED) } }

    /** Forgets a finished (done or cancelled) job, stored copy included: no Progress until the next fetch. */
    suspend fun clear() = withContext(work) {
        lock.withLock {
            val s = _state.value
            if (job != null || (s.phase != JobPhase.DONE && s.phase != JobPhase.CANCELLED)) return@withLock
            val idle = ScrapeState(quota = s.quota, tgdbLeft = s.tgdbLeft)
            _state.value = idle
            persist(idle)
        }
    }
    fun cancel() { scope.launch(work) { halt(JobPhase.CANCELLED) } }

    /**
     * Stops a running job (its in-flight item goes back to waiting) and records [phase]; a paused job can be cancelled.
     * [only] (checked under the lock) limits it to a running job it accepts. True when it stopped or cancelled a job.
     */
    private suspend fun halt(phase: JobPhase, only: ((ScrapeState) -> Boolean)? = null): Boolean = lock.withLock {
        val running = job
        if (running == null && !(phase == JobPhase.CANCELLED && _state.value.phase == JobPhase.PAUSED)) return@withLock false
        if (running != null && only != null && !only(_state.value)) return@withLock false
        job = null
        running?.cancelAndJoin()
        pending = emptyList()   // already in the shown items
        val s = _state.value.inFlightToWaiting().copy(phase = phase, pauseReason = null)
        _state.value = s
        persist(s)
        true
    }

    fun resume() {
        scope.launch(work) {
            lock.withLock { if (_state.value.phase == JobPhase.PAUSED && job == null && available) startLocked(_state.value) }
        }
    }

    /** Reads the persisted job; true when a paused job with waiting games can be resumed. */
    suspend fun restorePending(): Boolean = withContext(work) {
        lock.withLock { job == null && restoreLocked() }
    }

    /** Under [lock] with no job: loads the persisted job when it is paused with waiting games; true when it did. */
    private suspend fun restoreLocked(): Boolean {
        val (s, r) = host.readPrivate(JOB_FILE)?.let { ScrapeJobCodec.decode(it) } ?: return false
        if (s.phase != JobPhase.PAUSED || s.items.none { it.status == ItemStatus.WAITING }) return false
        replace = r
        _state.value = s.copy(quota = _state.value.quota, tgdbLeft = _state.value.tgdbLeft)
        return true
    }

    /**
     * One game in the caller's coroutine (the art panel's Fetch art). As a job does, it asks only for
     * the kinds the game lacks and never overwrites art already there unless [replaceExisting] (Replace existing art).
     * A game the running job still has queued is not fetched twice: this waits for the job's result. A pause case comes
     * back as ERROR with the pause reason. While a job runs, the shared sources' per-run state is the job's: this run
     * does not start them again ([ScrapeEngine.run]'s `startSources`). [wide]: the Change art search ([ScrapeEngine.run]'s
     * wide pass).
     */
    override suspend fun fetchGame(gamePath: String, replaceExisting: Boolean, wide: Boolean): FetchResult =
        fetchOne(gamePath, replaceExisting, wide).first

    /** [fetchGame], with the sources' stop notes of its own run (none when the job fetched the game). */
    private suspend fun fetchOne(gamePath: String, replaceExisting: Boolean, wide: Boolean): Pair<FetchResult, Map<String, String>> = withContext(work) {
        if (!available) return@withContext FetchResult(ItemStatus.SKIPPED, "Fetching isn't available") to emptyMap()
        val ref = host.libraryGames().firstOrNull { it.gamePath == gamePath }
            ?: return@withContext FetchResult(ItemStatus.SKIPPED, "Not in the library") to emptyMap()
        var result: FetchResult? = null
        var notes: Map<String, String> = emptyMap()
        singleLock.withLock {
            // A job started after this check waits for [singleLock] before its run: the two never start the sources at once.
            var jobLive = false
            val inJob = lock.withLock {
                jobLive = job != null
                (jobLive && _state.value.items.any { it.gamePath == gamePath && it.status.inFlightOrWaiting }).also { if (!it) single = gamePath }
            }
            if (!inJob) try {
                val one = ScrapeState(items = listOf(ScrapeItem(ref.gamePath, ref.tag, ref.name)))
                val end = engine.run(one, { if (it == gamePath) ref else null }, replace = replaceExisting, startSources = !jobLive, wide = wide) {}
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
     * [autoSearch] false: only [searchAgain] searches (the caller has just run the wide fetch itself).
     */
    override suspend fun artOptions(gamePath: String, replaceExisting: Boolean, searchAgain: Boolean, autoSearch: Boolean): ArtOptionsResult = withContext(work) {
        var saved = store.get(gamePath)
        var note: String? = null
        if (searchAgain || (autoSearch && (saved == null || !saved.wide))) {
            val (r, notes) = fetchOne(gamePath, replaceExisting, wide = true)
            note = notes.values.joinToString(" · ").ifEmpty { null }
                ?: r.reason.takeIf { r.status == ItemStatus.ERROR || r.status == ItemStatus.SKIPPED }
            saved = store.get(gamePath)
        }
        val ref = host.libraryGames().firstOrNull { it.gamePath == gamePath }
        ArtOptionsResult(saved?.options.orEmpty(), ref?.let { currentKey(it, saved) }, note)
    }

    /**
     * The option [ref]'s screenshot is: the pick when it is still listed, else the one matching the written-art log's
     * origin for the screenshot file; null for a hand-placed screenshot (or an extra folder's own), or none.
     */
    private suspend fun currentKey(ref: LibraryGameRef, saved: GameOptions?): String? {
        val options = saved?.options.orEmpty()
        saved?.pick?.let { p -> if (options.any { it.key == p }) return p }
        val shot = ref.art.screenshot ?: return null
        if (ref.sourceId != GamePath.HOME && engine.artOnDisk(ref).screenshot != shot) return null   // an extra folder's own file
        val ext = shot.substringAfterLast('.').lowercase()
        val enc = ArtEncoding.entries.firstOrNull { it.ext == ext } ?: return null
        val t = try { ArtPaths.target(ref.sourceId, ref.relativeDir, ref.fileName, ref.folderGame) } catch (_: IllegalArgumentException) { return null }
        val origin = log.origin(t.path(ArtSlot.SCREENSHOT, enc)) ?: return null
        return options.firstOrNull { it.source == origin.source && it.assetId != null && it.assetId == origin.assetId }?.key
    }

    /**
     * [option]'s preview: the cached image, else downloaded (ScreenScraper's with the current login) and cached. A link
     * that answered but gave no image is dropped from the list (never the pick); no connection keeps it. Null when
     * there is no image to show ([OptionImage.dropped] when the option was dropped).
     */
    override suspend fun optionImage(gamePath: String, option: ArtOption): OptionImage = withContext(work) {
        optionCache.ref(cacheKey(gamePath, option.key))?.let { return@withContext OptionImage(it) }
        val got = downloadOption(gamePath, option)
        val bytes = got.bytes ?: return@withContext OptionImage(null, got.dropped)
        OptionImage(optionCache.write(cacheKey(gamePath, option.key), bytes))
    }

    /** A download's [bytes] (null: none); [dropped]: the link answered without an image and the option was dropped. */
    private class Download(val bytes: ByteArray?, val dropped: Boolean = false)

    /** [key]'s preview in the cache: per game, as an option's key is unique only within its game. */
    private fun cacheKey(gamePath: String, key: String): String = gamePath + "\n" + key

    /** [option]'s image from the link (at most [PREVIEW_DOWNLOADS] at once); see [optionImage] for a miss. */
    private suspend fun downloadOption(gamePath: String, option: ArtOption): Download {
        val url = if (option.source == SourceId.SS) ScraperRequest.withAuth(option.url, auth()) else option.url
        val bytes = try {
            previews.withPermit { engine.downloadImage(url) }
        } catch (_: JobPause) {
            return Download(null)   // no connection: the option stays
        }
        if (bytes != null) return Download(bytes)
        if (store.get(gamePath)?.pick == option.key) return Download(null)   // never the pick
        store.dropOption(gamePath, option.key)
        return Download(null, dropped = true)
    }

    /**
     * Writes [option] as the game's screenshot and marks it picked, so no fetch replaces it. Picking the current one
     * only marks it picked (no write). The pick is marked before the write (a running job reads it); a failed write puts the previous
     * pick back. False when the image could not be had or written.
     */
    override suspend fun pickOption(gamePath: String, option: ArtOption): Boolean = withContext(work) {
        val ref = host.libraryGames().firstOrNull { it.gamePath == gamePath } ?: return@withContext false
        singleLock.withLock {
            val saved = store.get(gamePath)
            if (option.key == currentKey(ref, saved)) {
                if (saved?.pick != option.key) withContext(NonCancellable) { store.setPick(gamePath, option.key) }
                return@withLock true
            }
            val bytes = optionCache.read(cacheKey(gamePath, option.key))
                ?: downloadOption(gamePath, option).bytes?.also { optionCache.write(cacheKey(gamePath, option.key), it) }
                ?: return@withLock false
            withContext(NonCancellable) {
                val previous = saved?.pick
                store.setPick(gamePath, option.key)
                val art = try {
                    engine.writePick(ref, bytes, option.origin)
                } catch (t: Throwable) {
                    store.setPick(gamePath, previous); throw t
                }
                if (art == null) { store.setPick(gamePath, previous); return@withContext false }
                host.applyArt(gamePath, art)
                flushArt()
                true
            }
        }
    }

    /** The games whose screenshot is a Change art pick (the library scan prefers an extra folder game's mirror copy). */
    suspend fun pickedGames(): Set<String> = withContext(work) { store.pickedPaths() }

    /**
     * The scan's one-time `grid/` and `hero/` move ([LegacyArtMove]) over [source], keeping this controller's
     * written-art log in step.
     */
    fun legacyArtMove(source: com.nxredux.mobile.library.RomsSource): LegacyArtMove = LegacyArtMove(source, log, host)

    /**
     * The logged screenshots that are SteamGridDB heroes, as home-relative paths without their extension
     * (`Roms/GB/.media/screenshot/Tetris`): the scan's [com.nxredux.mobile.library.ArtRefs.heroShot].
     */
    suspend fun heroShots(): Set<String> = withContext(work) {
        log.read { l ->
            l.entries.filterTo(HashSet()) { path ->
                val parts = path.split('/')
                parts.size >= 3 && parts[parts.size - 2] == ArtSlot.SCREENSHOT.folder && parts[parts.size - 3] == ".media" &&
                    l.origin(path)?.source == SourceId.SGDB
            }.mapTo(HashSet()) { it.substringBeforeLast('/') + "/" + it.substringAfterLast('/').substringBeforeLast('.') }
        }
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
     * Deletes the logged art files of the games in scope ([tags] null = every logged file: typed art and the app's
     * legacy untyped mixes; one whose delete failed stays logged and uncounted), removes the slot and `.media` folders
     * that left empty, and, for Android's scope or All, the Android games console's stored screenshots; then re-reads
     * what is left on disk for every game whose art paths were touched (hidden games too), so hand-placed art stays in
     * the index. The scope's Change art lists, picks and previews are forgotten too (All: every preview). A running job
     * is paused only when it still has games in scope to fetch; an in-flight [fetchGame] finishes first.
     */
    suspend fun resetArt(tags: Set<String>?): ResetResult = withContext(work) {
        val up = tags?.mapTo(HashSet()) { it.uppercase() }
        val paused = halt(JobPhase.PAUSED) { s -> s.items.any { it.status.inFlightOrWaiting && (up == null || it.tag.uppercase() in up) } }
        singleLock.withLock {
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
            // The Android games console's screenshots are app-private, outside the log: Android's scope or All deletes them.
            val androidDeleted = if (up == null || ANDROID_CONSOLE_TAG in up) engine.deleteAndroidArt() else 0
            // The scope's Change art lists, picks and previews go too; a failure there never fails the Reset.
            try {
                val keys = store.forget(up?.let { games.filter { g -> g.tag.uppercase() in it }.mapTo(HashSet()) { it.gamePath } })
                if (up == null) optionCache.clear() else optionCache.remove(keys.flatMap { (g, ks) -> ks.map { cacheKey(g, it) } })
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            for ((g, was) in before) host.applyArt(g.gamePath, ScrapeEngine.indexArt(g, was, engine.artOnDisk(g)))
            flushArt()   // one index write and one refresh for every game reset
            ResetResult(deleted + androidDeleted, paused)
        }
    }

    /**
     * Every path the app may have written for [g]: each typed slot (the 2D box, wheel and mix among them) in both
     * encodings, the 2D box's legacy `box2d/` files and the legacy untyped mix.
     */
    private fun artPaths(g: LibraryGameRef): List<String> {
        val t = try { ArtPaths.target(g.sourceId, g.relativeDir, g.fileName, g.folderGame) } catch (_: IllegalArgumentException) { return emptyList() }
        return ArtEncoding.entries.flatMap { e -> ArtSlot.entries.map { t.path(it, e) } + t.legacyBox2dPath(e) } + t.legacyMixPath
    }
}
