package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.NetplayAddress
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.NetplayWire
import com.swordfish.libretrodroid.LibretroNetplay
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * netplay: a host whose peer never connects (its launch may have failed: the joiner's launch can no-op) gives up after
 * this long in CONNECTING, as a failed session. Counted from the engine start, which is the host's listen once its core
 * has loaded (the handheld's 10 s state waits start only then); the joiner connects once its own core has loaded and
 * retries for ~30 s.
 */
internal const val NETPLAY_CONNECT_TIMEOUT_MS = 45_000L
private const val TICK_MS = 250L

/** What one status read of the engine means for the game screen ([netplayNotice]). */
internal sealed interface NetplayNotice {
    /** Nothing to show (running, syncing, a local pause): the countdown pill goes. */
    data object Clear : NetplayNotice
    /** A countdown the other side causes (stall, its pause), as the progress pill. */
    data class Pill(val text: String) : NetplayNotice
    /** The session is over; the player plays on alone. */
    data class Ended(val text: String) : NetplayNotice
    /** The session failed: back to the game list with [text]. */
    data class Failed(val text: String) : NetplayNotice
}

/**
 * netplay: maps the engine's status ([LibretroNetplay.status]) to a notice (a failure's text is the dialog body under the
 * "Netplay failed" title, by reason). A local pause reports RUNNING with its own
 * countdown, which is not shown (only its ENDED after 30 s). [connectingMs] is how long the session has been running
 * since start; still CONNECTING past [NETPLAY_CONNECT_TIMEOUT_MS] counts as failed.
 */
internal fun netplayNotice(state: Int, secondsLeft: Int, reason: Int, onCopy: Boolean, connectingMs: Long): NetplayNotice = when (state) {
    LibretroNetplay.STATE_CONNECTING ->
        if (connectingMs >= NETPLAY_CONNECT_TIMEOUT_MS) NetplayNotice.Failed(NetplayText.FAILED_CONNECT) else NetplayNotice.Clear
    LibretroNetplay.STATE_STALLED -> NetplayNotice.Pill(NetplayText.waiting(secondsLeft))
    LibretroNetplay.STATE_PEER_PAUSED -> NetplayNotice.Pill(NetplayText.peerPaused(secondsLeft))
    LibretroNetplay.STATE_ENDED ->
        NetplayNotice.Ended("${if (onCopy) NetplayText.ENDED_COPY else NetplayText.ENDED}\n${NetplayText.ENDED_DETAIL}")
    LibretroNetplay.STATE_FAILED -> NetplayNotice.Failed(
        when (reason) {
            LibretroNetplay.REASON_STATE_MISMATCH -> NetplayText.FAILED_MISMATCH
            LibretroNetplay.REASON_CONNECT -> NetplayText.FAILED_CONNECT
            LibretroNetplay.REASON_TIMEOUT -> NetplayText.FAILED_TIMEOUT
            LibretroNetplay.REASON_IO -> NetplayText.FAILED_IO
            else -> NetplayText.FAILED
        },
    )
    else -> NetplayNotice.Clear
}

/**
 * netplay: the session GameActivity's launch extras carry ([GameActivity.EXTRA_NETPLAY_ROLE] and the rest), or null for
 * every other launch (and for extras that do not make one: an unknown role, a peer that is not an IPv4 address). Both
 * roles need the peer: the client connects to it, the host's engine accepts only it (an IPv4-mapped one is normalised).
 * [emptySaves]: no save copy happened although one was due ([NetplaySession.emptySaves]); [overUsb]: the session runs
 * over the USB cable ([NetplaySession.overUsb]); [mixed] and [peerWl]: [NetplaySession.mixed], [NetplaySession.peerWl].
 */
internal fun netplaySessionFrom(
    role: String?, peer: String?, coreId: String, folderCoreId: String?, emptySaves: Boolean = false, overUsb: Boolean = false,
    mixed: Boolean = false, peerWl: String? = null,
): NetplaySession? {
    val r = NetplayRole.entries.firstOrNull { it.name == role } ?: return null
    if (folderCoreId.isNullOrEmpty()) return null
    val ip = NetplayAddress.ipv4(peer) ?: return null
    return NetplaySession(r, ip, coreId, folderCoreId, emptySaves, overUsb, mixed, peerWl)
}

/**
 * netplay: what the game screen says on its first frame: "Saves not copied" when no copy happened although one was due;
 * else, for a host on another core than its folder's, that it plays on a copy (and that copying its own saves failed).
 */
internal fun netplayFirstFrameNotices(session: NetplaySession, coreName: String, ownCopyFailed: Boolean): List<String> = when {
    session.emptySaves -> listOf(NetplayText.SAVES_NOT_COPIED)
    session.role == NetplayRole.HOST && session.onCopy ->
        listOfNotNull(NetplayText.usingCopy(coreName), NetplayText.OWN_COPY_FAILED.takeIf { ownCopyFailed })
    else -> emptyList()
}

/** One netplay session on the game screen, lockstep ([NetplayGame]) or GBA link ([NetplayLinkGame]). */
internal interface NetplayRun {
    /** From a successful start until the session ends; after that every call is a no-op. */
    val live: Boolean
    fun pause()
    fun resume()
    /** "Leave netplay?": tell the other side, then shut the engine down (off the main thread). */
    fun leave()
    /** Stop ticking without touching the engine (the screen is going; [NetplayGame.release] shuts it down). */
    fun detach()
}

/** Every blocking engine call (both engines), in order: a later session's start never overtakes an earlier screen's shutdown. */
internal object NetplayEngine {
    val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "nx-netplay") }
    val dispatcher = executor.asCoroutineDispatcher()
}

/**
 * netplay: one Wi-Fi netplay session inside the game screen. Starts the native engine, ticks its status every 250 ms on
 * [scope] (the activity's lifecycleScope: it keeps ticking while the activity is stopped, as the engine thread keeps the
 * connection), reports notices to [onNotice] (main thread), and forwards the pause / resume / leave the screen asks for.
 *
 * [live] is true from a successful [start] until the session ends (an ENDED or FAILED read, [leave], [release]); after
 * that every call is a no-op and the game runs on its own. Blocking engine calls (np_shutdown can take ~0.75 s) run on
 * one process-wide "nx-netplay" thread, in call order, never on the main thread.
 */
internal class NetplayGame(
    val session: NetplaySession,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val onNotice: (NetplayNotice) -> Unit,
) : NetplayRun {
    @Volatile override var live = false
        private set
    private var startedAt = 0L
    private var ticker: Job? = null
    /**
     * The screen wants the session paused (menu open, in the background): no input goes out, the other device stalls and
     * counts down. np_pause acts only while RUNNING, so the ticker says it again on every read: a pause asked for while
     * connecting or syncing still holds once the session runs.
     */
    @Volatile private var held = false

    /**
     * Starts the engine (the client connects to the host; the host listens and accepts only the client's address) and
     * the status ticker. A shutdown first: np_start refuses until one, even after an ENDED session. False when the
     * engine would not start.
     */
    suspend fun start(): Boolean {
        val role = if (session.role == NetplayRole.HOST) LibretroNetplay.ROLE_HOST else LibretroNetplay.ROLE_CLIENT
        val peer = session.peerIp
        val ok = withContext(NetplayEngine.dispatcher) {
            runCatching {
                LibretroNetplay.shutdown()
                LibretroNetplay.start(role, peer, NetplayWire.ENGINE_PORT)
            }.onFailure { NxLog.w(TAG, "netplay start failed", it) }.getOrDefault(false)
        }
        if (!ok) return false
        live = true
        startedAt = now()
        ticker = scope.launch {
            while (live) {
                val s = runCatching { LibretroNetplay.status() }.getOrNull()
                if (held && live) runCatching { LibretroNetplay.pause() }   // idempotent while already paused
                if (s != null && s.size >= 3 && live) {
                    val notice = netplayNotice(s[0], s[1], s[2], session.onCopy, now() - startedAt)
                    if (notice is NetplayNotice.Ended || notice is NetplayNotice.Failed) end()
                    onNotice(notice)
                }
                delay(TICK_MS)
            }
        }
        return true
    }

    /** The menu opened or the app went to the background: this device stops sending input (nothing on the wire; the other side stalls and counts down). */
    override fun pause() {
        held = true
        if (live) runCatching { LibretroNetplay.pause() }
    }

    /** Back from the menu or the background: input goes out again. */
    override fun resume() {
        held = false
        if (live) runCatching { LibretroNetplay.resume() }
    }

    /** Leave on "Leave netplay?": DISCONNECT, then the engine is shut down. Off the main thread. */
    override fun leave() {
        val was = live
        detach()
        if (was) NetplayEngine.executor.execute { runCatching { LibretroNetplay.leave(); LibretroNetplay.shutdown() } }
    }

    /** An ENDED or FAILED read: the session is over, the engine goes idle (the game then runs as any other). */
    private fun end() {
        detach()
        NetplayEngine.executor.execute { runCatching { LibretroNetplay.shutdown() } }
    }

    override fun detach() {
        live = false
        ticker?.cancel()   // from the ticker itself (an ENDED read) too: its notice is still delivered, the loop then stops
    }

    companion object {
        private const val TAG = "NXAPP"
        /**
         * The game screen is gone (after its native destroy, which shuts both engines too; a second shutdown is a no-op):
         * the engines are shut down, then [discardCopy] runs (the screen releases its own session copy), off the main thread.
         */
        fun release(game: NetplayRun?, discardCopy: (() -> Unit)?) {
            game?.detach()
            NetplayEngine.executor.execute {
                if (game != null) {
                    runCatching { LibretroNetplay.shutdown() }
                    runCatching { LibretroNetplay.linkShutdown() }
                }
                discardCopy?.let { runCatching { it() }.onFailure { e -> NxLog.e(TAG, "netplay session copy not released", e) } }
            }
        }
    }
}
