package com.nxredux.mobile.runner

import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.achievements.RaController
import com.nxredux.mobile.achievements.RaGamePointer
import com.nxredux.mobile.achievements.RaLaunchFingerprint
import com.nxredux.mobile.achievements.RaLaunchHash
import com.nxredux.mobile.achievements.RaLibraryView
import com.nxredux.mobile.achievements.RaRequestKind
import com.nxredux.mobile.achievements.RaResponse
import com.nxredux.mobile.achievements.RaSession
import com.nxredux.mobile.achievements.RaSessionMode
import com.nxredux.mobile.achievements.RaSetsCache
import com.nxredux.mobile.achievements.RaStoreException
import com.nxredux.mobile.achievements.UNKNOWN_EMULATOR_ID
import com.nxredux.mobile.settings.AppSettings
import com.swordfish.libretrodroid.AchievementEvent
import com.swordfish.libretrodroid.AchievementInfo
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

sealed interface RaEvent {
    data class Unlocked(val a: RaAchievement) : RaEvent
    data class Progress(val a: RaAchievement) : RaEvent
    data object ProgressHide : RaEvent
    data object Mastered : RaEvent
    data class ServerError(val message: String) : RaEvent
    data object Disconnected : RaEvent
    data object Reconnected : RaEvent
    data class GameLoaded(val title: String, val unlocked: Int, val total: Int) : RaEvent
    data object GameUnknown : RaEvent
    data class LoginFailed(val message: String) : RaEvent
    data object SessionOffline : RaEvent
    data object SessionDisabled : RaEvent
    data class Synced(val count: Int) : RaEvent
    /** [AchievementsBridge.badges] changed (the folder was listed, or the missing badges arrived). */
    data object BadgesReady : RaEvent
}

/**
 * The only class that talks to the fork's achievement natives. One per GameActivity; nothing here survives [stop].
 * Order: [prepare] before the view exists (enables the native client), [attach] right after (answers server calls,
 * translates events), [startSession] after the first frame (mode, login, then the game load once the login answers).
 */
class AchievementsBridge(private val controller: RaController, private val settings: AppSettings, parent: CoroutineScope) {
    private val _events = MutableSharedFlow<RaEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<RaEvent> = _events
    // A child of the activity's scope: stop() cancels everything the bridge started, the activity's scope ends it anyway.
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val transport = controller.newTransport()
    private var consoleId = 0
    private var hashJob: Deferred<String?>? = null
    /** The library entry being played; written as the hash's pointer so the menu finds this game's cached set. */
    private var pointer: RaGamePointer? = null
    /** The launch's place in the shared `hashes.json`, answered once rc_client knows (or doesn't know) the game. */
    private var launch: Deferred<RaLaunchFingerprint?>? = null
    @Volatile private var hash: String? = null
    @Volatile var mode: RaSessionMode = RaSessionMode.DISABLED; private set
    @Volatile private var gameLoaded = false
    @Volatile private var prepared = false
    @Volatile private var stopped = false
    /** Signed in (online or from the cache) with a hash for this game; the menu row shows only then. */
    val available: Boolean get() = !stopped && mode != RaSessionMode.DISABLED && hash != null
    @Volatile var muted: Set<Int> = emptySet(); private set
    /** The game's badge files (name -> image model) from the home folder; empty until its folder is known. */
    @Volatile var badges: Map<String, String> = emptyMap(); private set
    /** Ids journaled but not yet confirmed by the server (rc_client counts them as unlocked); never read from disk by [list]. */
    @Volatile private var pendingIds: Set<Int> = emptySet()

    /**
     * Before the view exists: enable the native client and remember what to hash, which library entry it is and its
     * [launch] fingerprint (completed by the time [hashJob] is, null when nothing may be stored).
     */
    fun prepare(consoleId: Int, hashJob: Deferred<String?>, pointer: RaGamePointer?, launch: Deferred<RaLaunchFingerprint?>? = null) {
        this.consoleId = consoleId; this.hashJob = hashJob; this.pointer = pointer; this.launch = launch
        LibretroDroid.achievementsEnable(true)
        prepared = true
    }

    /** After the view exists: answer server calls (every one, exactly once), translate events. */
    fun attach(view: GLRetroView) {
        scope.launch {
            view.getAchievementServerCalls().collect { call ->
                launch(Dispatchers.IO) {
                    val award = RaRequestKind.of(call.postData).r == "awardachievement"
                    val r = try {
                        // An award is journaled before its POST (RaTransport), so a process end mid-POST loses nothing; it
                        // still finishes the POST after stop() cancels the scope. Only the answer to the native client below
                        // stays gated on the session being alive.
                        if (award) withContext(NonCancellable) { transport.call(call.url, call.postData) }
                        else transport.call(call.url, call.postData)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: RaStoreException) {
                        NxLog.e(TAG, "journal write failed", e)
                        _events.tryEmit(RaEvent.ServerError("Could not save the unlock: ${e.message}"))
                        RaResponse(0, "")
                    } catch (e: Exception) {
                        NxLog.e(TAG, "server call failed", e)
                        RaResponse(0, "")   // 0 = retryable for rc_client
                    }
                    // Stopped meanwhile: the client was disabled (its callbacks failed); a late answer could reach the next one.
                    if (!isActive || stopped) return@launch
                    LibretroDroid.achievementsServerResponse(call.requestId, r.status, r.body)
                    if (award) refreshPending()   // an offline award was just journaled
                }
            }
        }
        scope.launch { view.getAchievementEvents().collect { e -> onNative(e) } }
    }

    /** After the first frame: decide the mode, log in, load the game once the login answers. */
    fun startSession() {
        val user = settings.raUsername
        val token = settings.raToken ?: return
        safeLaunch {
            val h = hashJob?.await()
            NxLog.i(TAG, "hash ${h ?: "unidentified"} (console $consoleId)")
            hash = h
            if (h != null) pointer?.let { p -> scope.launch { try { controller.rememberGame(h, p) } catch (e: CancellationException) { throw e } catch (e: Exception) { NxLog.w(TAG, "game pointer not written", e) } } }
            if (h == null) { mode = RaSessionMode.DISABLED; return@safeLaunch }
            muted = controller.mutes.load(h)
            mode = withContext(Dispatchers.IO) {
                RaSession(transport, controller.store, controller.platformConnectivity) { delay(it) }.start(user, token)
            }
            NxLog.i(TAG, "session $mode")
            when (mode) {
                RaSessionMode.DISABLED -> { _events.tryEmit(RaEvent.SessionDisabled); return@safeLaunch }
                RaSessionMode.OFFLINE -> _events.tryEmit(RaEvent.SessionOffline)
                RaSessionMode.ONLINE -> Unit
            }
            if (!stopped) LibretroDroid.achievementsLogin(user, token)
        }
    }

    private fun onNative(e: AchievementEvent) {
        when (e.type) {
            LibretroDroid.RA_EVENT_LOGIN_OK -> {
                NxLog.i(TAG, "login ok")
                hash?.let { LibretroDroid.achievementsLoadGame(it, consoleId) }
                if (mode == RaSessionMode.ONLINE) safeLaunch(Dispatchers.IO) {
                    val user = settings.raUsername
                    val token = settings.raToken ?: return@safeLaunch
                    if (controller.journal.count(user) > 0) {
                        val r = controller.newSync().run(user, token) {}
                        if (r.submitted > 0) { _events.tryEmit(RaEvent.Synced(r.submitted)); refreshPending() }
                    }
                }
            }
            LibretroDroid.RA_EVENT_LOGIN_FAILED -> {
                NxLog.w(TAG, "login failed: ${e.extra}")
                mode = RaSessionMode.DISABLED
                _events.tryEmit(RaEvent.LoginFailed(e.extra))
            }
            LibretroDroid.RA_EVENT_GAME_LOADED -> {
                gameLoaded = true
                refreshPending()
                val achievements = list()
                val (unlocked, total) = counts(achievements)
                _events.tryEmit(RaEvent.GameLoaded(e.title, unlocked, total))
                val names = achievements.map { it.badge }
                safeLaunch(Dispatchers.IO) {
                    val h = hash ?: return@safeLaunch
                    answer(h) { RaSetsCache.summary(controller.store, h)?.gameId }   // one hashes.json update, then the badges
                    pointer?.let { controller.rememberGame(h, it) }   // the folder is found through the pointer (idempotent)
                    val folder = controller.badgeFolder(h) ?: return@safeLaunch
                    badges = controller.platformHome.listing(folder)
                    _events.tryEmit(RaEvent.BadgesReady)
                    controller.badges.ensureAll(folder, names)
                    badges = controller.platformHome.listing(folder)
                    _events.tryEmit(RaEvent.BadgesReady)
                }
            }
            LibretroDroid.RA_EVENT_GAME_UNKNOWN, LibretroDroid.RA_EVENT_GAME_LOAD_FAILED -> {
                NxLog.i(TAG, "game not loaded (${e.type}): ${e.extra}")
                _events.tryEmit(RaEvent.GameUnknown)
                // Only RA's "unknown hash" is an answer: a load failure is transport/offline, and offline the shim answers an
                // uncached hash with GameID 0 itself (RaTransport), so neither says anything about the hash.
                if (e.type == LibretroDroid.RA_EVENT_GAME_UNKNOWN && mode == RaSessionMode.ONLINE) safeLaunch(Dispatchers.IO) { hash?.let { answer(it) { 0 } } }
            }
            LibretroDroid.RA_EVENT_ACHIEVEMENT_TRIGGERED -> if (e.id != UNKNOWN_EMULATOR_ID) {
                _events.tryEmit(RaEvent.Unlocked(toModel(e)))
                refreshPending()
            }
            LibretroDroid.RA_EVENT_PROGRESS_SHOW, LibretroDroid.RA_EVENT_PROGRESS_UPDATE -> if (e.id !in muted) _events.tryEmit(RaEvent.Progress(toModel(e)))
            LibretroDroid.RA_EVENT_PROGRESS_HIDE -> _events.tryEmit(RaEvent.ProgressHide)
            LibretroDroid.RA_EVENT_GAME_COMPLETED -> _events.tryEmit(RaEvent.Mastered)
            LibretroDroid.RA_EVENT_SERVER_ERROR -> _events.tryEmit(RaEvent.ServerError(e.extra))
            LibretroDroid.RA_EVENT_DISCONNECTED -> _events.tryEmit(RaEvent.Disconnected)
            LibretroDroid.RA_EVENT_RECONNECTED -> _events.tryEmit(RaEvent.Reconnected)
            else -> NxLog.d(TAG, "event ${e.type} ignored")
        }
    }

    private fun toModel(e: AchievementEvent) =
        RaAchievement(e.id, e.title, e.description, e.points, e.badge, RaAchievementState.UNLOCKED, null, e.progress.ifBlank { null }, e.percent, 0f, null)

    /** An unlocked achievement the server has not confirmed yet (journaled offline) shows as PENDING. */
    private fun toModel(a: AchievementInfo): RaAchievement {
        val state = when {
            a.state == 2 && a.id in pendingIds -> RaAchievementState.PENDING
            a.state == 2 -> RaAchievementState.UNLOCKED
            else -> RaAchievementState.LOCKED
        }
        val type = RaLibraryView.raType(when (a.type) { 1 -> "missable"; 2 -> "progression"; 3 -> "win"; else -> null })
        return RaAchievement(a.id, a.title, a.description, a.points, a.badgeName, state, a.unlockTime.takeIf { it > 0 },   // epoch seconds, like the journal
            a.measuredProgress.ifBlank { null }, a.measuredPercent, a.rarity, type)
    }

    /** Refreshed after GameLoaded, each Unlocked and each award answer, so [list] never touches the disk. */
    private fun refreshPending() {
        val h = hash ?: return
        safeLaunch(Dispatchers.IO) { pendingIds = controller.journal.entries(settings.raUsername).filter { it.m == h }.map { it.a }.toSet() }
    }

    /** The core set (no "Unknown Emulator" warning); empty until the game has loaded. */
    fun list(): List<RaAchievement> {
        if (hash == null || !gameLoaded || stopped) return emptyList()
        return LibretroDroid.achievementsList().filter { it.id != UNKNOWN_EMULATOR_ID && it.category == 1 }.map(::toModel)
    }

    /** (unlocked, total), or null while the game is not (or not yet) recognised. */
    fun summary(): Pair<Int, Int>? = if (!available || !gameLoaded) null else counts(list())

    // From [list], not rc_client's own summary: that one counts the "Unknown Emulator" warning (unlocked, in the core set).
    private fun counts(achievements: List<RaAchievement>) = achievements.count { it.state != RaAchievementState.LOCKED } to achievements.size

    suspend fun toggleMute(id: Int): Set<Int> {
        val h = hash ?: return muted
        muted = withContext(Dispatchers.IO) { controller.mutes.toggle(h, id) }
        return muted
    }

    /** Idempotent: cancels everything the bridge started and disables the native client. */
    fun stop() {
        if (stopped) return
        stopped = true
        scope.cancel()
        if (prepared) runCatching { LibretroDroid.achievementsUnloadGame(); LibretroDroid.achievementsEnable(false) }
            .onFailure { NxLog.w(TAG, "stopping the native client failed", it) }
    }

    /**
     * Records rc_client's answer for [h] in the shared fingerprints: [game]'s id (0 = unknown; null = no answer, nothing
     * written). Never throws: a failure only costs a rehash next launch.
     */
    private suspend fun answer(h: String, game: suspend () -> Int?) {
        try {
            val l = launch?.await() ?: return
            RaLaunchHash.answer(controller.fingerprints, l, h, game() ?: return)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { NxLog.w(TAG, "fingerprint not written", e) }
    }

    /** A bridge failure must never take the game down (lifecycleScope has no exception handler). */
    private fun safeLaunch(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch(context) {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { NxLog.e(TAG, "achievements task failed", e) }
        }

    private companion object { const val TAG = "Achievements" }
}
