package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.GgpoSession
import com.nxredux.mobile.netplay.NetplaySession
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * A Dreamcast session on the game screen: flycast's GGPO plays the other device inside the core, so this only keeps the
 * handheld's goodbye (nx-redux core_netplay.c): a player who leaves sends [GgpoSession.BYE_MESSAGE] to the other device's
 * [GgpoSession.BYE_PORT] a few times, and a goodbye heard here ends the session now ([onPeerLeft], main thread) rather
 * than at the core's disconnect timeout. A pause (menu open, app in the background) leaves after
 * [GgpoSession.LEAVE_GRACE_MS] ([onGraceOver], main thread): the other game waits for this one meanwhile.
 */
internal class GgpoGame(
    private val session: NetplaySession,
    private val scope: CoroutineScope,
    private val onPeerLeft: () -> Unit,
    private val onGraceOver: () -> Unit,
) : NetplayRun {
    @Volatile override var live = false
        private set
    private var socket: DatagramSocket? = null
    private var listener: Job? = null
    private var grace: Job? = null

    /** Opens the goodbye channel; the session runs without it (the core's timeout then is the only end) when the port is taken. */
    fun start(): Boolean {
        live = true
        socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                soTimeout = POLL_MS
                bind(InetSocketAddress(GgpoSession.BYE_PORT))
            }
        }.onFailure { NxLog.w(TAG, "goodbye channel failed", it) }.getOrNull()
        val s = socket ?: return true
        listener = scope.launch {
            val buf = ByteArray(64)
            val peer = runCatching { InetAddress.getByName(session.peerIp) }.getOrNull()
            while (live && isActive && !s.isClosed) {
                val heard = withContext(Dispatchers.IO) {
                    runCatching {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        (peer == null || p.address == peer) && String(p.data, 0, p.length, Charsets.US_ASCII) == GgpoSession.BYE_MESSAGE
                    }.getOrDefault(false)   // a timeout: poll again
                }
                if (heard && live) {
                    onPeerLeft()   // while still live: the session's end is told apart from this side's own leave
                    detach()
                    return@launch
                }
            }
        }
        return true
    }

    override fun pause() {
        if (!live || grace != null) return
        grace = scope.launch {
            delay(GgpoSession.LEAVE_GRACE_MS)
            grace = null
            if (live) onGraceOver()
        }
    }

    override fun resume() {
        grace?.cancel()
        grace = null
    }

    /** Leave: the other device is told at once (a few datagrams, off the main thread), then the channel closes. */
    override fun leave() {
        if (!live) return
        val s = socket
        detach(closeSocket = false)
        Thread({
            runCatching {
                val to = InetAddress.getByName(session.peerIp)
                val bytes = GgpoSession.BYE_MESSAGE.toByteArray(Charsets.US_ASCII)
                (s ?: DatagramSocket()).use { out ->
                    repeat(GgpoSession.BYE_REPEATS) { out.send(DatagramPacket(bytes, bytes.size, to, GgpoSession.BYE_PORT)) }
                }
            }.onFailure { NxLog.w(TAG, "goodbye failed", it) }
        }, "ggpo-bye").start()
    }

    override fun detach() = detach(closeSocket = true)

    private fun detach(closeSocket: Boolean) {
        live = false
        grace?.cancel()
        grace = null
        listener?.cancel()
        listener = null
        if (closeSocket) runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        const val TAG = "NXAPP"
        const val POLL_MS = 250
    }
}
