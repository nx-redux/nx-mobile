package com.nxredux.mobile.netplay.wizard

import com.nxredux.mobile.netplay.NetplayChannel
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.netplay.NetplayIoException
import com.nxredux.mobile.netplay.NetplaySaves
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.NetplayWire
import com.nxredux.mobile.netplay.RejectReason
import com.nxredux.mobile.tryOr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The handheld's control-channel exchange (nx-redux wizard_net.c at 8f5a760b), both roles: HELLO 2 with caps,
 * the gates (version, game, role, core; gpSP with mGBA passes only for the same Wi-Fi mode, WizCaps_linkMismatch),
 * `REJECT core-<core>.<tag>`, SYNC-READY + `FILE <name>` with an rsync pull,
 * `START 2` / `START`. Neither driver throws [NetplayIoException]; neither closes the channel (the caller does).
 */
object Wizard {
    const val RECV_MS = 5_000L
    const val START_WAIT_TRIES = 12
    const val PULL_MS = 30_000L
    const val FILE_PULL_MS = 15_000L
    const val TOTAL_PULL_MS = 23_000L
    const val MAX_FILES = 32

    /** The handheld's `%255s` game field. */
    const val GAME_TOKEN_MAX = 255

    /**
     * Answers one joiner. [saves] is asked once the HELLO is answered, never for a link session (when it throws, the
     * joiner gets `REJECT sync`).
     */
    suspend fun host(ch: NetplayChannel, offer: HostOffer, saves: suspend () -> HostSaves, onSync: () -> Unit = {}): HostOutcome {
        try {
            val hello = HelloCodec.parse(ch.readLine(RECV_MS)) ?: return HostOutcome.Turned(null)
            HelloCodec.mismatch(hello, offer.game, Hello.CLIENT, hello.any)?.let { reason ->
                ch.writeLine(Reject(reason).format())
                return HostOutcome.Turned(reason.wire)
            }
            val run = NetplayCores.hostRun(
                offer.tag, offer.core, offer.pick, hello.core, hello.tag, hello.auto, offer.usb, offer.wl, hello.wl,
            ) ?: run {
                ch.writeLine(Reject(RejectReason.CORE, offer.core, offer.tag.trim().uppercase()).format())
                return HostOutcome.Turned(RejectReason.CORE.wire, hello.core)
            }
            // The joiner ends on another core unless it names this one or switches to it on this HELLO.
            val joinerCore = hello.core
            val mixed = joinerCore != null && joinerCore != run &&
                !NetplayCores.joinerSwitches(run, hello.tag, hello.auto, offer.usb, hello.wl)
            ch.writeLine(HelloCodec.format(Hello.host(wireGame(offer.game), 2, Caps.of(run, offer.tag, wl = offer.wl))))
            // A link session (GB/GBC/GBA) copies no saves: each player keeps their own, which a trade needs (the
            // handheld's NETPLAY_SYNC_SAVES=0). START follows the HELLO at once.
            val emptySaves = if (NetplayCores.isLink(run)) false else {
                val served = tryOr({
                    sendQuietly(ch, Reject(RejectReason.SYNC).format())
                    return HostOutcome.Turned(RejectReason.SYNC.wire)
                }) { saves() }
                try {
                    if (served.files.isNotEmpty() && served.canServe) {
                        val up = tryOr({ false }) { served.start(ch.remoteAddress) }
                        if (!up) {
                            sendQuietly(ch, Reject(RejectReason.SYNC).format())
                            return HostOutcome.Turned(RejectReason.SYNC.wire)
                        }
                        onSync()
                        ch.writeLine("SYNC-READY ${served.files.size}")
                        for (name in served.files) ch.writeLine("FILE ${NetplayWire.escape(name)}")
                        val done = try { ch.readLine(PULL_MS) } catch (e: NetplayIoException) { null }
                        if (done != "SYNC-DONE") return HostOutcome.Turned(RejectReason.SYNC.wire)
                    }
                } finally {
                    served.close()
                }
                served.files.isNotEmpty() && !served.canServe
            }
            ch.writeLine("START 2")
            if (ch.readLine(RECV_MS) != "START") return HostOutcome.Turned(null)
            return HostOutcome.Started(run, ch.remoteAddress, emptySaves, mixed, hello.wl)
        } catch (e: NetplayIoException) {
            return HostOutcome.Turned(null)
        }
    }

    /**
     * Joins a host. Once [SaveSink.begin] has run the sink is always finished with [SaveSink.commit] or [SaveSink.abort],
     * also on cancellation. [onWaiting] gets this device's player number before each 5 s wait for the host to start.
     */
    suspend fun client(
        ch: NetplayChannel,
        offer: ClientOffer,
        sink: SaveSink,
        onSync: () -> Unit = {},
        onWaiting: (Int) -> Unit = {},
    ): ClientOutcome {
        val reply = try {
            // Auto with a choice: the joiner switches to the host's core, so a phone host on Auto keeps its own (never a host's caps).
            val auto = offer.pick == null && NetplayCores.offered(offer.tag, offer.usb, offer.wl).size > 1
            ch.writeLine(HelloCodec.format(Hello.client(wireGame(offer.game), offer.any, Caps.of(offer.core, offer.tag, auto, offer.wl))))
            ch.readLine(RECV_MS)
        } catch (e: NetplayIoException) {
            return ClientOutcome.Refused(NetplayText.NO_ANSWER)
        }
        Reject.parse(reply)?.let { r ->
            if (r.reason == RejectReason.CORE && r.core != null) {
                NetplayCores.joinerRetry(offer.tag, offer.pick, r.core, offer.mayRetry, offer.usb, offer.wl)?.let { return ClientOutcome.Retry(it) }
            }
            return ClientOutcome.Refused(rejectText(r, offer))
        }
        val h = HelloCodec.parse(reply) ?: return ClientOutcome.Refused(NetplayText.NOT_WAITING)
        HelloCodec.mismatch(h, offer.game, Hello.HOST, offer.any)?.let { return ClientOutcome.Refused(NetplayText.reject(it)) }
        // A host that names another core (one that predates the check, or a phone): switch on Auto, else stop, sending nothing.
        val core = NetplayCores.joinerRun(offer.tag, offer.core, offer.pick, h.core, offer.usb, offer.wl, h.wl)
            ?: return ClientOutcome.Refused(NetplayCores.refusal(offer.tag, offer.pick, h.core.orEmpty(), h.tag, offer.usb, offer.wl))
        val mixed = h.core != null && h.core != core
        var syncing = false
        try {
            var line: String? = null
            for (i in 0 until START_WAIT_TRIES) {
                onWaiting(h.player)
                line = try {
                    ch.readLine(RECV_MS)
                } catch (e: NetplayIoException) {
                    if (e.message == NetplayIoException.TIMEOUT) continue else throw e   // closed: stop waiting now
                }
                break
            }
            line ?: return ClientOutcome.Refused(NetplayText.NO_ANSWER)
            Reject.parse(line)?.let { return ClientOutcome.Refused(rejectText(it, offer)) }
            val n = syncReadyCount(line)
            if (n != null) {
                if (n !in 0..MAX_FILES) {
                    sendQuietly(ch, "SYNC-FAIL")
                    return ClientOutcome.Refused(NetplayText.TOO_MANY_SAVES)
                }
                onSync()
                val names = ArrayList<String>(n)
                repeat(n) {
                    val f = try {
                        ch.readLine(RECV_MS)
                    } catch (e: NetplayIoException) {
                        sendQuietly(ch, "SYNC-FAIL")
                        return ClientOutcome.Refused(NetplayText.STOPPED_RESPONDING)
                    }
                    val parts = f.split(' ').filter { it.isNotEmpty() }
                    if (parts.size < 2 || parts[0] != "FILE") {
                        sendQuietly(ch, "SYNC-FAIL")
                        return ClientOutcome.Refused(NetplayText.UNEXPECTED)
                    }
                    val name = NetplayWire.unescape(parts[1])
                    if (name !in NetplaySaves.WIRE_NAMES) {
                        sendQuietly(ch, "SYNC-FAIL")
                        return ClientOutcome.Refused(NetplayText.UNEXPECTED_FILE)
                    }
                    names += name
                }
                if (names.isNotEmpty() && !sink.canPull) {
                    // Never decline (the handheld's SYNC-DONE without pulling): the host could not tell and would play
                    // its real save against this device's empty copy. Fail the sync; the host turns this joiner away.
                    sendQuietly(ch, "SYNC-FAIL")
                    return ClientOutcome.Refused(NetplayText.COPY_FAILED)
                }
                if (names.isNotEmpty()) {
                    syncing = true
                    sink.begin()
                    if (!pullAll(sink, ch.remoteAddress, names)) {
                        syncing = false
                        sink.abort()
                        sendQuietly(ch, "SYNC-FAIL")
                        return ClientOutcome.Refused(NetplayText.COPY_FAILED)
                    }
                    sink.commit()
                    syncing = false
                }
                ch.writeLine("SYNC-DONE")
                line = ch.readLine(RECV_MS)
                Reject.parse(line)?.let { return ClientOutcome.Refused(rejectText(it, offer)) }
            }
            if (line != "START" && !line.startsWith("START ")) return ClientOutcome.Refused(NetplayText.UNEXPECTED)
            ch.writeLine("START")
            return ClientOutcome.Started(core, h.player, emptySaves = false, mixed = mixed, peerWl = h.wl)
        } catch (e: CancellationException) {
            if (syncing) sink.abort()
            throw e
        } catch (e: NetplayIoException) {
            if (syncing) {
                sink.abort()
                sendQuietly(ch, "SYNC-FAIL")
                return ClientOutcome.Refused(NetplayText.COPY_FAILED)
            }
            return ClientOutcome.Refused(NetplayText.NO_ANSWER)
        } catch (e: Exception) {
            if (!syncing) throw e   // only the platform sink's file I/O is expected to fail here
            sink.abort()
            sendQuietly(ch, "SYNC-FAIL")
            return ClientOutcome.Refused(NetplayText.COPY_FAILED)
        }
    }

    /** Each pull within 15 s and all of them within 23 s (wizard_sync.c); false on a failure or a timeout. */
    private suspend fun pullAll(sink: SaveSink, host: String, names: List<String>): Boolean = tryOr({ false }) {
        withTimeoutOrNull(TOTAL_PULL_MS) {
            names.all { name -> withTimeoutOrNull(FILE_PULL_MS) { sink.pull(host, name); true } == true }
        } == true
    }

    /** The text for a REJECT; `core-<core>[.<tag>]` names the host's emulator, a bare `core` is a plain refusal. */
    private fun rejectText(r: Reject, offer: ClientOffer): String =
        if (r.reason == RejectReason.CORE && r.core != null) NetplayCores.refusal(offer.tag, offer.pick, r.core, r.tag, offer.usb, offer.wl)
        else NetplayText.reject(r.reason)

    /**
     * The game as sent: the handheld reads the token with `%255s`, so at most [GAME_TOKEN_MAX] UTF-8 bytes, cut on a
     * character boundary (escaping is one char for one char). That keeps every HELLO under the 511-byte line limit.
     */
    private fun wireGame(game: String): String {
        var bytes = 0
        var end = 0
        while (end < game.length) {
            val c = game[end]
            val surrogatePair = c.isHighSurrogate() && end + 1 < game.length && game[end + 1].isLowSurrogate()
            val (chars, size) = when {
                surrogatePair -> 2 to 4
                c.code < 0x80 -> 1 to 1
                c.code < 0x800 -> 1 to 2
                else -> 1 to 3
            }
            if (bytes + size > GAME_TOKEN_MAX) break
            bytes += size
            end += chars
        }
        return game.substring(0, end)
    }

    private val LEADING_INT = Regex("^[+-]?[0-9]+")

    /**
     * wiz_parse_sync_ready (`sscanf("%15s %d")`): the count of a `SYNC-READY <n>` line, unvalidated; null for any other
     * line, including one without a number, which then reads as an unexpected reply (no SYNC-FAIL), as on the handheld.
     */
    private fun syncReadyCount(line: String): Int? {
        val f = line.split(' ').filter { it.isNotEmpty() }
        if (f.size < 2 || f[0] != "SYNC-READY") return null
        return LEADING_INT.find(f[1])?.value?.toIntOrNull()
    }

    /** Best effort: the outcome is decided, a dead channel changes nothing. */
    private suspend fun sendQuietly(ch: NetplayChannel, line: String) {
        try { ch.writeLine(line) } catch (_: NetplayIoException) {}
    }
}
