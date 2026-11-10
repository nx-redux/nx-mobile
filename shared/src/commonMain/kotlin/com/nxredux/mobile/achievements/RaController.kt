package com.nxredux.mobile.achievements

import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.tryOr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

data class RaStatus(val user: String?, val points: Int?, val pending: Int, val lastSync: Long?, val cachedGames: Int, val lastError: String? = null)

sealed interface RaState {
    data class Idle(val status: RaStatus) : RaState
    data object Authenticating : RaState
    data class Syncing(val progress: RaSyncProgress, val previous: RaStatus) : RaState
    /** [paused]: held between games ([RaController.pause]); the notification's service stops meanwhile. */
    data class Prefetching(val done: Int, val total: Int, val title: String, val previous: RaStatus, val paused: Boolean = false) : RaState
    data class Resetting(val previous: RaStatus) : RaState
}

enum class RaDownloadPhase { RUNNING, PAUSED, DONE, CANCELLED, STOPPED }

/** One game on the download's page: its name and where it is ([RaItemStatus]), with why when skipped or failed. */
data class RaDownloadItem(val name: String, val status: RaItemStatus, val reason: String? = null)

/**
 * "Download all game data" as its page shows it: the [phase], [finished] of [total] games, the games still to do (the
 * one in flight first, then those waiting) followed by the skipped and failed ([items]; a game that finished well leaves),
 * the outcome counts, and the time left at the measured pace ([secondsLeft], null until a few games have finished).
 * [stopReason]: why a [RaDownloadPhase.STOPPED] download stopped.
 */
data class RaDownload(
    val phase: RaDownloadPhase, val total: Int, val finished: Int, val items: List<RaDownloadItem>,
    val cached: Int = 0, val unknown: Int = 0, val skipped: Int = 0, val secondsLeft: Long? = null, val stopReason: String? = null,
)

sealed interface RaMessage {
    data class Notice(val text: String) : RaMessage
    data class Failure(val text: String) : RaMessage
}

/** The RetroAchievements tool's state machine (nx-redux `ratools`): sign-in, status, sync, prefetch, resets. */
class RaController(
    private val platform: RaPlatform, private val settings: AppSettings, private val index: suspend () -> LibraryIndex?,
    private val scope: CoroutineScope, private val now: () -> Long,
    /**
     * Where the browser's reads and JSON parses run, and the sync's and prefetch's walks with them (Android:
     * Dispatchers.Default); empty = the caller's dispatcher.
     */
    private val work: CoroutineContext = EmptyCoroutineContext,
    /** The sync's and prefetch's request pacing and backoff waits (tests: instant). */
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    val store: RaStore get() = platform.store
    val journal = RaJournal(platform.store, RaPaths.JOURNAL)
    val confirmed = RaJournal(platform.store, RaPaths.CONFIRMED)
    val rejected = RaJournal(platform.store, RaPaths.REJECTED)
    val badges = RaBadges(platform.home, platform.http)
    /** The shared per-console-folder `hashes.json` files, one lock per folder for every prefetch and launch. */
    val fingerprints = RaFingerprints(platform.home)
    val mutes = RaMutes(platform.store)
    val platformConnectivity: RaConnectivity get() = platform.connectivity
    val platformHome: RaHomeTree get() = platform.home

    private val _state = MutableStateFlow<RaState>(RaState.Idle(RaStatus(null, null, 0, null, 0)))
    val state: StateFlow<RaState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<RaMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<RaMessage> = _messages.asSharedFlow()
    private var job: Job? = null
    private val _download = MutableStateFlow<RaDownload?>(null)
    /** The last "Download all game data" (running, paused or ended); null before the first this process. */
    val download: StateFlow<RaDownload?> = _download.asStateFlow()
    private val paused = MutableStateFlow(false)
    private var tracker: DownloadTracker? = null

    val busy: Boolean get() = _state.value !is RaState.Idle
    private val idleStatus: RaStatus get() = (_state.value as? RaState.Idle)?.status ?: RaStatus(null, null, 0, null, 0)

    fun newTransport() = RaTransport(platform.store, platform.http, journal, confirmed, now)
    fun newSync() = RaSync(platform.store, platform.http, platform.signer, journal, confirmed, rejected, delay, now)

    fun refresh() {
        if (busy) return
        scope.launch { val s = readStatusOrKeep(idleStatus.lastError); if (!busy) _state.value = RaState.Idle(s) }
    }

    /** [readStatus], or the status shown now with the read's failure: the store throws when its files go away. */
    private suspend fun readStatusOrKeep(error: String? = null): RaStatus =
        tryOr({ e -> idleStatus.copy(lastError = "Could not read status: ${e::class.simpleName}") }) { readStatus(error) }

    // The whole journal is parsed here (pending count), and the games folder listed: on [work], as the walks.
    private suspend fun readStatus(error: String? = null): RaStatus = withContext(work) {
        val user = settings.raUsername.takeIf { settings.raToken != null && it.isNotBlank() }
        val login = platform.store.readJson(RaPaths.login(), LoginBody.serializer())
        val pending = user?.let { journal.count(it) } ?: 0
        val lastSync = platform.store.read(RaPaths.LAST_SYNC)?.decodeToString()?.trim()?.toLongOrNull()?.let { it * 1000 }
        val cached = platform.store.list(RaPaths.GAMES).count { RaPaths.isHash(it) && platform.store.exists(RaPaths.sets(it)) }
        RaStatus(login?.User ?: user, if (user != null) login?.SoftcoreScore else null, pending, lastSync, cached, error)
    }

    fun login(user: String, password: String) {
        if (busy) return
        val previous = idleStatus
        _state.value = RaState.Authenticating
        job = scope.launch {
            val result: String? = tryOr({ e -> if (e is RaHttpException) "No connection" else "Sign-in failed: ${e::class.simpleName}" }) {
                val r = platform.http.post(RaRequests.URL, RaRequests.loginWithPassword(user, password), RaRequests.CONTENT_TYPE)
                val body = RaJson.parse(LoginBody.serializer(), r.body)
                when {
                    body == null -> "Unexpected reply from RetroAchievements"
                    !body.Success || body.Token.isBlank() -> body.Error ?: "Sign-in failed"
                    else -> {
                        settings.raToken = body.Token; settings.raUsername = body.User.ifBlank { user }; settings.raEnabled = true
                        platform.store.writeAtomic(RaPaths.login(), r.body.encodeToByteArray())   // offline works from the first launch
                        emit(RaMessage.Notice("Signed in as ${settings.raUsername}")); null
                    }
                }
            }
            _state.value = RaState.Idle(
                tryOr({ e -> previous.copy(lastError = result ?: "Sign-in failed: ${e::class.simpleName}") }) { readStatus(result) },
            )
        }
    }

    fun syncNow() {
        if (_state.value is RaState.Prefetching) { emit(RaMessage.Failure("Wait for the game data download to finish")); return }
        if (busy) return
        val (user, token) = credentials() ?: run { emit(RaMessage.Failure("Sign in first")); return }
        if (!platform.connectivity.isConnected()) { emit(RaMessage.Failure("No connection")); return }
        val previous = idleStatus
        _state.value = RaState.Syncing(RaSyncProgress(RaSyncStep.STARTING), previous)
        job = scope.launch {
            tryOr({ e -> _state.value = RaState.Idle(previous); emit(RaMessage.Failure("Sync failed: ${e::class.simpleName}")) }) {
                val r = onWork { post -> newSync().run(user, token) { p -> post { _state.value = RaState.Syncing(p, previous) } } }
                emit(syncMessage(r))
                _state.value = RaState.Idle(readStatus(if (r.stop == RaSyncStop.AUTH) RA_SIGN_IN_AGAIN else null))
            }
        }
    }

    fun prefetchAll() {
        if (busy) return
        val (user, token) = credentials() ?: run { emit(RaMessage.Failure("Sign in first")); return }
        if (!platform.connectivity.isConnected()) { emit(RaMessage.Failure("No connection")); return }
        val previous = idleStatus
        paused.value = false
        val t = DownloadTracker(now()).also { tracker = it }
        _state.value = RaState.Prefetching(0, 0, "", previous)
        _download.value = t.snapshot(RaDownloadPhase.RUNNING)
        fun publish() { if (tracker === t && _state.value is RaState.Prefetching) _download.value = t.snapshot(if (paused.value) RaDownloadPhase.PAUSED else RaDownloadPhase.RUNNING) }
        job = scope.launch {
            tryOr({ e ->
                _state.value = RaState.Idle(previous); _download.value = t.snapshot(RaDownloadPhase.STOPPED, stopReason = e::class.simpleName)
                emit(RaMessage.Failure("Download failed: ${e::class.simpleName}"))
            }) {
                val idx = withContext(work) { index() } ?: run { _state.value = RaState.Idle(previous); _download.value = null; emit(RaMessage.Failure("No library index yet")); return@launch }
                // The walk (every cached game's sets.json parsed, each folder's hashes.json) runs on [work]; the tracker,
                // the page and the state change only here, in its order, so pause/resume/cancel never meet it mid-change.
                val r = onWork { post ->
                    RaPrefetch(platform.store, platform.http, platform.hasher, badges, fingerprints, delay).run(
                        idx, user, token,
                        gate = {
                            if (paused.value) { post { publish() }; paused.first { !it }; val at = now(); post { t.resumed(at) } }
                        },
                        onListed = { names -> post { t.listed(names); publish() } },
                        onItem = { i, st, why -> val at = now(); post { t.item(i, st, why, at); publish() } },
                    ) { d, total, title -> post { _state.value = RaState.Prefetching(d, total, title, previous, paused.value) } }
                }
                val text = "Game data: ${r.cached} cached · ${r.unknown} without achievements · ${r.skipped} skipped"
                val why = when (r.stop) { RaSyncStop.AUTH -> RA_SIGN_IN_AGAIN; RaSyncStop.BUSY -> RA_BUSY; RaSyncStop.REFUSED -> "refused by RetroAchievements"; null -> "no connection" }
                emit(if (r.failed) RaMessage.Failure("$text (stopped: $why)") else RaMessage.Notice(text))
                _download.value = t.snapshot(if (r.failed) RaDownloadPhase.STOPPED else RaDownloadPhase.DONE, stopReason = why.takeIf { r.failed })
                _state.value = RaState.Idle(readStatus(if (r.stop == RaSyncStop.AUTH) RA_SIGN_IN_AGAIN else null))
            }
        }
    }

    private val oneGame = Mutex()

    /**
     * The achievements panel's download of one game it has no data for, by its library [path]: what Download all fetches
     * for each game (its hash, game id, sets, unlocks and badges), one game at a time. A game already cached but without a
     * library pointer gets one, so the panel finds it.
     */
    suspend fun fetchGame(path: String): RaGameFetch {
        val (user, token) = credentials() ?: return RaGameFetch.Failed("sign in first")
        if (!platform.connectivity.isConnected()) return RaGameFetch.Failed(RaPrefetch.NO_CONNECTION)
        return oneGame.withLock {
            withContext(work) {
                val idx = index() ?: return@withContext RaGameFetch.Failed("no library index yet")
                val (tag, game) = idx.systems.filter { !it.unassigned }.firstNotNullOfOrNull { s ->
                    s.games.firstOrNull { GamePath.of(GameRef(it.source, it.dir, it.file)) == path }?.let { s.tag to it }
                } ?: return@withContext RaGameFetch.NotSupported
                if (RaConsoles.consoleId(tag, game.file) == null) return@withContext RaGameFetch.NotSupported
                var status: RaItemStatus? = null; var reason: String? = null; var hash: String? = null
                RaPrefetch(platform.store, platform.http, platform.hasher, badges, fingerprints, delay)
                    .one(tag, game, user, token, onItem = { st, why -> if (st.finished) { status = st; reason = why } }, onHash = { hash = it })
                val h = hash
                when (status) {
                    RaItemStatus.CACHED, RaItemStatus.ALREADY_CACHED -> if (h == null) RaGameFetch.Failed(RaPrefetch.NO_REPLY) else {
                        RaGamePointers.remember(platform.store, h, RaGamePointer(tag, game.source, game.dir, game.file, game.name))
                        RaGameFetch.Cached(h)
                    }
                    RaItemStatus.NO_ACHIEVEMENTS -> RaGameFetch.Unrecognised(h.orEmpty())
                    RaItemStatus.SKIPPED -> if (reason == RaPrefetch.UNREADABLE) RaGameFetch.Unreadable else RaGameFetch.Failed(reason ?: RaPrefetch.NO_REPLY)
                    else -> RaGameFetch.Failed(reason ?: RaPrefetch.NO_REPLY)
                }
            }
        }
    }

    /** Forgets an ended (done, cancelled or stopped) download: the RetroAchievements page loses its Progress row. */
    fun clearDownload() {
        val d = _download.value ?: return
        if (d.phase == RaDownloadPhase.RUNNING || d.phase == RaDownloadPhase.PAUSED) return
        _download.value = null
    }

    /** Holds the download before its next game (the one in flight finishes); [resume] goes on. */
    fun pause() {
        val s = _state.value as? RaState.Prefetching ?: return
        if (paused.value) return
        paused.value = true
        _state.value = s.copy(paused = true)
        tracker?.let { _download.value = it.snapshot(RaDownloadPhase.PAUSED) }
    }

    fun resume() {
        val s = _state.value as? RaState.Prefetching ?: return
        if (!paused.value) return
        paused.value = false
        _state.value = s.copy(paused = false)
        tracker?.let { _download.value = it.snapshot(RaDownloadPhase.RUNNING) }
    }

    /** Signs out and forgets sessions and the journal; game sets and badges stay. */
    fun resetAccount() = reset(ResetScope.ACCOUNT)
    /** Deletes the account data and the shared game data (never the handheld's account files, never anything under Roms/) and signs out. */
    fun eraseAll() = reset(ResetScope.ALL)

    /** Forgets the token and the cached login only; unsynced unlocks, sessions, game data and badges stay. */
    fun signOut() = reset(ResetScope.LOGIN)

    private enum class ResetScope { LOGIN, ACCOUNT, ALL }

    private fun reset(what: ResetScope) {
        if (busy) return
        val previous = idleStatus
        _state.value = RaState.Resetting(previous)
        job = scope.launch {
            try {
                withContext(NonCancellable) {
                    settings.raToken = null; settings.raEnabled = false   // no in-game session on a stale token
                    val store = platform.store
                    when (what) {
                        ResetScope.ALL -> {
                            clearingJournals {
                                for (name in store.list(RaPaths.ACCOUNT)) if (name != PENDING_DIR) store.deleteTree("${RaPaths.ACCOUNT}/$name")
                                clearPending()
                            }
                            store.deleteTree(RaPaths.GAMES)
                        }
                        ResetScope.LOGIN -> store.delete(RaPaths.login())
                        ResetScope.ACCOUNT -> {
                            store.delete(RaPaths.login()); store.deleteTree(RaPaths.SESSIONS)
                            clearingJournals { clearPending() }
                        }
                    }
                }
                emit(RaMessage.Notice(when (what) { ResetScope.ALL -> "Achievement data erased"; ResetScope.LOGIN -> "Signed out"; ResetScope.ACCOUNT -> "Account data reset" }))
            } catch (e: Exception) { emit(RaMessage.Failure("Could not delete: ${e::class.simpleName}")) }
            _state.value = tryOr({ e -> emit(RaMessage.Failure("Could not read status: ${e::class.simpleName}")); RaState.Idle(previous) }) {
                RaState.Idle(readStatus())
            }
        }
    }

    /**
     * [block] holding both journals' locks, as their appends and rewrites do: the game process may be journaling an
     * unlock, and a delete under it would lose the file it is appending to.
     */
    private suspend fun clearingJournals(block: suspend () -> Unit) =
        platform.store.locked(RaPaths.JOURNAL) { platform.store.locked(RaPaths.CONFIRMED) { block() } }

    /** Everything in `pending/` but the lock sidecars: deleting one a process holds would split the lock in two. */
    private suspend fun clearPending() {
        val store = platform.store
        for (name in store.list(RaPaths.PENDING)) if (!name.endsWith(RaPaths.LOCK_SUFFIX)) store.deleteTree("${RaPaths.PENDING}/$name")
    }

    /** Stops a sync or prefetch; sign-in and resets run to completion. */
    fun cancel() {
        val previous = when (val s = _state.value) { is RaState.Syncing -> s.previous; is RaState.Prefetching -> s.previous; else -> return }
        // The tracker is let go first: the walk, woken by the cancel (or the pause lifted below), publishes nothing more.
        val t = tracker.takeIf { _state.value is RaState.Prefetching }
        tracker = null
        job?.cancel(); job = null
        paused.value = false
        t?.let { _download.value = it.snapshot(RaDownloadPhase.CANCELLED) }
        _state.value = RaState.Idle(previous)
        scope.launch { val s = readStatusOrKeep(); if (!busy) _state.value = RaState.Idle(s) }
    }

    /**
     * Every cached game's files are read here: never on the main thread. [library]: the library index already in memory
     * (decoding the stored one again is a whole library's JSON); null reads the stored one.
     */
    suspend fun games(library: LibraryIndex? = null): List<RaGameView> =
        withContext(work) { RaLibraryView.games(platform.store, journal, confirmed, library ?: index(), settings.raUsername) }
    suspend fun achievements(hash: String): List<RaAchievement> = withContext(work) { RaLibraryView.achievements(platform.store, journal, confirmed, hash, settings.raUsername) }

    /** The menu's per-game progress; empty when signed out or the cache won't read. */
    suspend fun progressByPath(): Map<String, RaProgress> = withContext(work) {
        if (settings.raUsername.isBlank()) return@withContext emptyMap()
        try { RaLibraryView.progressByPath(platform.store, journal, confirmed, settings.raUsername) } catch (e: RaStoreException) { emptyMap() }
    }

    /** The cached games' hashes by library path (a game list's achievements panel); empty when the cache won't read. */
    suspend fun hashesByPath(): Map<String, String> = withContext(work) {
        try { RaLibraryView.hashesByPath(platform.store) } catch (e: RaStoreException) { emptyMap() }
    }

    /** Unlocks since [sinceMs] in the games at [paths] (library paths); null when signed out, 0 when the cache won't read. */
    suspend fun unlocksSince(sinceMs: Long, paths: Set<String>): Int? = withContext(work) {
        val user = settings.raUsername.takeIf { settings.raToken != null && it.isNotBlank() } ?: return@withContext null
        try { RaLibraryView.unlocksSince(platform.store, journal, confirmed, user, sinceMs / 1000, paths) } catch (e: RaStoreException) { 0 }
    }

    /** A played game's library pointer, so the menu can find its cached set; an existing pointer (from prefetch) is kept. */
    suspend fun rememberGame(hash: String, pointer: RaGamePointer) = withContext(work) {
        if (!RaPaths.isHash(hash)) return@withContext
        RaGamePointers.remember(platform.store, hash, pointer)
    }

    /** [hash]'s badge folder: its pointer (game.json, else rom.txt) and the set's game id. */
    suspend fun badgeFolder(hash: String): List<String>? = withContext(work) {
        if (!RaPaths.isHash(hash)) return@withContext null
        val gameId = RaSetsCache.summary(platform.store, hash)?.gameId ?: return@withContext null
        badges.folder(RaGamePointers.read(platform.store, hash), gameId)
    }

    /** Badge file name -> image model for [hash]'s game; empty when it has no folder yet. */
    suspend fun badgeListing(hash: String): Map<String, String> = badgeFolder(hash)?.let { platform.home.listing(it) } ?: emptyMap()

    /**
     * Runs [block] (a sync's or a download's walk) on [work], so its JSON parses stay off the main thread, while what it
     * reports, [post]ed as actions, runs here on the caller's dispatcher in the order posted: the state, the download's
     * tracker and its page are only ever changed from one thread, and a cancel there cancels the queue with the job, so no
     * late update lands after it. Every posted action has run when this returns, or throws (a failed walk's last reports
     * land before its failure is handled); a cancel drops them.
     */
    private suspend fun <T> onWork(block: suspend (post: (() -> Unit) -> Unit) -> T): T = coroutineScope {
        val updates = Channel<() -> Unit>(Channel.UNLIMITED)
        val drain = launch { for (u in updates) { u(); yield() } }
        try {
            withContext(work) { block { updates.trySend(it) } }
        } catch (e: Exception) {
            if (e !is CancellationException) { updates.close(); drain.join() }
            throw e
        } finally { updates.close() }
    }

    /** The signed-in user and token; null when signed out. */
    private fun credentials(): Pair<String, String>? {
        val user = settings.raUsername; val token = settings.raToken
        return if (token == null || user.isBlank()) null else user to token
    }

    private fun emit(m: RaMessage) { _messages.tryEmit(m) }
}

/**
 * How [RaController.fetchGame] ended: cached under [Cached.hash]; a file RetroAchievements does not know ([Unrecognised],
 * GameID 0: a bad dump, a hack, an unsupported version; its answer is cached, so asking again makes no request); a file
 * that would not hash ([Unreadable]); a console RetroAchievements lacks ([NotSupported]); or a passing failure ([Failed]:
 * no connection, a busy server) worth trying again.
 */
sealed interface RaGameFetch {
    data class Cached(val hash: String) : RaGameFetch
    data class Unrecognised(val hash: String) : RaGameFetch
    data object Unreadable : RaGameFetch
    data object NotSupported : RaGameFetch
    data class Failed(val reason: String) : RaGameFetch
}

private const val RA_SIGN_IN_AGAIN = "Sign in again: RetroAchievements refused the saved login"
private const val RA_BUSY = "RetroAchievements is busy, try later"
/** [RaPaths.PENDING]'s name under the account tree. */
private val PENDING_DIR = RaPaths.PENDING.substringAfterLast('/')

/**
 * A sync's outcome for the user: why it stopped, else what it did. "Up to date" only when nothing is left journaled;
 * dead-lettered and still-pending unlocks are counted so a sync that keeps the journal non-empty says so.
 */
internal fun syncMessage(r: RaSyncResult): RaMessage {
    val counts = listOfNotNull(r.rejected.takeIf { it > 0 }?.let { "$it rejected" }, r.remaining.takeIf { it > 0 }?.let { "$it still pending" })
        .joinToString("") { " · $it" }
    val after = if (r.submitted > 0) " (synced ${r.submitted})" else ""
    return when {
        r.stop == RaSyncStop.AUTH -> RaMessage.Failure("$RA_SIGN_IN_AGAIN$after")
        r.stop == RaSyncStop.BUSY -> RaMessage.Failure("$RA_BUSY$after")
        r.stop == RaSyncStop.REFUSED -> RaMessage.Failure((r.error ?: "Refused by RetroAchievements") + after)
        r.failed && r.submitted == 0 -> RaMessage.Failure("Sync failed$counts")
        r.failed -> RaMessage.Failure("Sync failed after ${r.submitted}$counts")
        r.submitted == 0 && r.refreshedGames == 0 -> RaMessage.Notice(if (counts.isEmpty()) "Up to date" else "Nothing synced$counts")
        else -> RaMessage.Notice("Synced ${r.submitted} unlock${if (r.submitted == 1) "" else "s"}" + (r.points?.let { " · $it points" } ?: "") + counts)
    }
}

/** Games finished before the time left is shown: the first ones (often already cached) say little about the pace. */
private const val PACE_AFTER = 3

/**
 * A download's games as the walk reports them, folded into its page ([RaDownload]): the waiting names, the one in flight,
 * the skipped and failed, the counts, and the active time (pauses left out) the time left is measured from.
 */
private class DownloadTracker(start: Long) {
    private var names: List<String> = emptyList()
    private var next = 0   // the first game not yet reached
    private var current: RaDownloadItem? = null
    private val problems = ArrayList<RaDownloadItem>()
    private var finished = 0; private var cached = 0; private var unknown = 0; private var skipped = 0
    private var mark = start; private var activeMs = 0L

    fun listed(all: List<String>) { names = all }

    fun item(i: Int, status: RaItemStatus, reason: String?, now: Long) {
        val name = names.getOrNull(i) ?: return
        if (!status.finished) { current = RaDownloadItem(name, status); next = i + 1; return }
        current = null; next = maxOf(next, i + 1); finished++
        activeMs += (now - mark).coerceAtLeast(0); mark = now
        when (status) {
            RaItemStatus.CACHED -> cached++
            RaItemStatus.NO_ACHIEVEMENTS -> unknown++
            RaItemStatus.ALREADY_CACHED -> skipped++
            else -> { skipped++; problems += RaDownloadItem(name, status, reason) }
        }
    }

    /** Back from a pause: the paused time is not part of the pace. */
    fun resumed(now: Long) { mark = now }

    fun snapshot(phase: RaDownloadPhase, stopReason: String? = null): RaDownload {
        val ongoing = phase == RaDownloadPhase.RUNNING || phase == RaDownloadPhase.PAUSED
        val waiting = if (ongoing) names.subList(next.coerceAtMost(names.size), names.size).map { RaDownloadItem(it, RaItemStatus.WAITING) } else emptyList()
        val left = (names.size - finished).coerceAtLeast(0)
        val secondsLeft = if (finished >= PACE_AFTER && ongoing) activeMs * left / finished / 1000 else null
        return RaDownload(
            phase, names.size, finished, listOfNotNull(current.takeIf { ongoing }) + waiting + problems,
            cached, unknown, skipped, secondsLeft, stopReason,
        )
    }
}
