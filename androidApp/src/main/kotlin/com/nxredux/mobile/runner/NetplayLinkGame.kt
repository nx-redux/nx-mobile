package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.NetplayLinkOptions
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.NetplayWire
import com.swordfish.libretrodroid.LibretroNetplay
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * netplay link: what one link status read means for the game screen ([LibretroNetplay.linkStatus]). A host still waiting
 * for its joiner past [NETPLAY_CONNECT_TIMEOUT_MS] has ended: its game already runs and plays on alone, so no failure
 * dialog over it. Ended never says progress is lost: both players are on their own real saves.
 */
internal fun linkNotice(state: Int, secondsLeft: Int, reason: Int, connectingMs: Long): NetplayNotice = when (state) {
    LibretroNetplay.LINK_CONNECTING ->
        if (connectingMs >= NETPLAY_CONNECT_TIMEOUT_MS) NetplayNotice.Ended("${NetplayText.ENDED}\n${NetplayText.ENDED_DETAIL}")
        else NetplayNotice.Clear
    LibretroNetplay.LINK_PEER_PAUSED -> NetplayNotice.Pill(NetplayText.peerPaused(secondsLeft))
    LibretroNetplay.LINK_ENDED -> NetplayNotice.Ended("${NetplayText.ENDED}\n${NetplayText.ENDED_DETAIL}")
    LibretroNetplay.LINK_FAILED -> NetplayNotice.Failed(
        when (reason) {
            LibretroNetplay.REASON_CONNECT -> NetplayText.FAILED_CONNECT
            LibretroNetplay.REASON_TIMEOUT -> NetplayText.FAILED_TIMEOUT
            LibretroNetplay.REASON_IO -> NetplayText.FAILED_IO
            else -> NetplayText.FAILED
        },
    )
    else -> NetplayNotice.Clear
}

/**
 * netplay link: how long the link has been CONNECTING, counted from its latest entry into it ([startedAt] for the
 * first), so a host whose handheld joiner reloads for the host's mode (it leaves just after READY and comes back) gets
 * a fresh [NETPLAY_CONNECT_TIMEOUT_MS]. One status read per [connectingMs] call, in order.
 */
internal class LinkConnectingClock(private var startedAt: Long) {
    private var last = LibretroNetplay.LINK_CONNECTING

    fun connectingMs(state: Int, now: Long): Long {
        if (state == LibretroNetplay.LINK_CONNECTING && last != LibretroNetplay.LINK_CONNECTING) startedAt = now
        last = state
        return now - startedAt
    }
}

/**
 * netplay link: one GBA link session (gpSP or mGBA) inside the game screen. Unlike lockstep it starts before the core
 * loads ([connect]): the joiner must know the host's link mode first (the handheld reloads instead). Its
 * status ticks from the first frame ([begin]) every 250 ms on [scope], with notices to [onNotice] (main thread).
 * Blocking engine calls run on [NetplayEngine]'s thread.
 */
internal class NetplayLinkGame(
    val session: NetplaySession,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val onNotice: (NetplayNotice) -> Unit,
) : NetplayRun {
    @Volatile override var live = false
        private set
    private var startedAt = 0L
    private var ticker: Job? = null
    /** The screen wants the session paused (menu open, in the background); said again on every tick. */
    @Volatile private var held = false

    /**
     * Starts the link (the host listens and accepts only the joiner's address; the joiner connects and waits for the
     * host's READY). The raw gpsp_serial or mgba_link_mode the core runs with: the host's own ([ownSerial], or the
     * default); the joiner's is the host's, or its own when the host sent none or the session is mixed (the host's value
     * is the other core's option). Null when the engine would not start, or the joiner heard no READY within
     * [NETPLAY_CONNECT_TIMEOUT_MS].
     */
    suspend fun connect(ownSerial: String?): String? {
        val host = session.role == NetplayRole.HOST
        val mode = NetplayLinkOptions.hostMode(session.coreId, ownSerial)
        val ok = withContext(NetplayEngine.dispatcher) {
            runCatching {
                LibretroNetplay.linkShutdown()
                LibretroNetplay.linkStart(
                    if (host) LibretroNetplay.ROLE_HOST else LibretroNetplay.ROLE_CLIENT,
                    session.peerIp, NetplayWire.GBALINK_PORT, if (host) mode else "",
                )
            }.onFailure { NxLog.w(TAG, "link start failed", it) }.getOrDefault(false)
        }
        if (!ok) return null
        live = true
        startedAt = now()
        if (host) return mode
        while (live && now() - startedAt < NETPLAY_CONNECT_TIMEOUT_MS) {   // live: a leave during the wait stops it
            runCatching { LibretroNetplay.linkHostMode() }.getOrNull()?.let {
                return NetplayLinkOptions.joinerMode(session.coreId, ownSerial, it, session.mixed)
            }
            val s = runCatching { LibretroNetplay.linkStatus() }.getOrNull()
            if (s != null && s.isNotEmpty() && (s[0] == LibretroNetplay.LINK_FAILED || s[0] == LibretroNetplay.LINK_ENDED)) break
            delay(POLL_MS)
        }
        if (live) end()   // after a leave, leave() already shut the link down
        return null
    }

    /**
     * The first frame is up: the status ticker starts (a host's 45 s wait for its joiner counts from here, and again
     * from each later return to CONNECTING: [LinkConnectingClock]).
     */
    fun begin() {
        if (!live || ticker != null) return
        startedAt = now()
        val connecting = LinkConnectingClock(startedAt)
        ticker = scope.launch {
            while (live) {
                val s = runCatching { LibretroNetplay.linkStatus() }.getOrNull()
                if (held && live) runCatching { LibretroNetplay.linkPause() }   // idempotent
                if (s != null && s.size >= 3 && live) {
                    val notice = linkNotice(s[0], s[1], s[2], connecting.connectingMs(s[0], now()))
                    if (notice is NetplayNotice.Ended || notice is NetplayNotice.Failed) end()
                    onNotice(notice)
                }
                delay(TICK_MS)
            }
        }
    }

    /** Menu open or in the background: frames hold here and the other device is told (PAUSE, and HOLD on the real cable). */
    override fun pause() {
        held = true
        if (live) runCatching { LibretroNetplay.linkPause() }
    }

    override fun resume() {
        held = false
        if (live) runCatching { LibretroNetplay.linkResume() }
    }

    override fun leave() {
        val was = live
        detach()
        if (was) NetplayEngine.executor.execute { runCatching { LibretroNetplay.linkLeave(); LibretroNetplay.linkShutdown() } }
    }

    /** An ENDED or FAILED read (or no READY): the link goes idle; the game then runs as any other. */
    private fun end() {
        detach()
        NetplayEngine.executor.execute { runCatching { LibretroNetplay.linkShutdown() } }
    }

    override fun detach() {
        live = false
        ticker?.cancel()
    }

    private companion object {
        const val TAG = "NXAPP"
        const val TICK_MS = 250L
        const val POLL_MS = 100L
    }
}
