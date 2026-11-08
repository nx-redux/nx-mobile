package com.nxredux.mobile.sync

import com.nxredux.mobile.achievements.RaFingerprintFile
import com.nxredux.mobile.achievements.RaFingerprintMerge
import com.nxredux.mobile.achievements.Rekey
import com.nxredux.mobile.library.ArtworkResolver
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.SidecarTxt
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.usb.UsbLinkState
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.SaveKeys
import com.nxredux.mobile.tryOr
import com.nxredux.mobile.tryOrNull
import com.nxredux.mobile.tryQuietly
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Tools → Device Sync: pairs with a handheld or a phone on the cable or Wi-Fi with the handheld's protocol
 * ([SyncWire]), then runs the rsync steps as the client ([SyncStep]: saves and states 1-4, plus BIOS 5-6 and
 * collections 7 when both sides can, ROMs 8-9 with ROMs on), or the daemon as the server ([SyncRoles]). A peer on old
 * firmware (no `v2`, no `c`) is refused at pairing. Lives in a process-level [scope] so leaving the screen does not
 * stop it; [platform] does the I/O and [now] tells when the next HELLO/READY is due.
 */
class DeviceSyncController(
    private val scope: CoroutineScope,
    private val platform: DeviceSyncPlatform,
    /**
     * Where the listings are parsed and the plans made (Android: Dispatchers.Default): a handheld's folder of thousands of
     * ROMs is a regex per line, the ROM, save, collection and hashes.json plans walk them all. The run itself stays on
     * [scope]'s dispatcher; empty = there too.
     */
    private val work: CoroutineContext = EmptyCoroutineContext,
    private val now: () -> Long,
) {
    private val _page = MutableStateFlow<DeviceSyncPage>(DeviceSyncPage.Ready(SyncLink.CABLE))
    val page: StateFlow<DeviceSyncPage> = _page.asStateFlow()
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** One-shot notices (refusals) for AppState's notice pill. */
    val notices: SharedFlow<String> = _notices.asSharedFlow()
    private val _received = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    /** Completed syncs that received files: AppState refreshes resume points and slot pictures. */
    val received: SharedFlow<Int> = _received.asSharedFlow()
    private val _romsArrived = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /** Once after a run (client or server; done, failed or stopped) that received any ROM file: AppState rescans. */
    val romsArrived: SharedFlow<Unit> = _romsArrived.asSharedFlow()
    private val _collectionsArrived = MutableSharedFlow<SyncedCollections>(extraBufferCapacity = 8)
    /**
     * Once after a run (client or server; done, failed or stopped) that changed any of this phone's collections: their
     * names, so AppState lists the Collections tab again and recounts them (and folds daemon-received ones into a store copy).
     */
    val collectionsArrived: SharedFlow<SyncedCollections> = _collectionsArrived.asSharedFlow()
    /** The X toggle on Ready: ROMs in this run too. Off at every open; only counts while the platform offers ROMs. */
    private var romsToggle = false
    private var job: Job? = null
    /** The link of the latest run, for a Stop that comes while it is not yet on the Syncing page. */
    private var runLink = SyncLink.CABLE
    /**
     * When the serving daemon last logged a line ([serve]): its idle timeout runs from here. Written on the platform's
     * log reader thread, read by the wait loop.
     */
    @Volatile private var serverActivity = 0L
    /**
     * A sync (any phase from Start to its end) is under way: no game may start, no import may run. Stays true through a
     * stopped run's cleanup, since a stopped server still delivers its last log lines.
     */
    val running: Boolean get() = job?.isCompleted == false

    /** Opening the tool: back to Ready(CABLE) unless a sync runs (then the live page stays). */
    fun open() { if (!running) { romsToggle = false; _page.value = ready(SyncLink.CABLE) } }

    /** A touch on a Ready link row. */
    fun pick(link: SyncLink) { if (_page.value is DeviceSyncPage.Ready) _page.value = ready(link) }

    /** Flips ROM sync on Ready where it is available, without the X button's notice (setup's Sync ROMs row says it). */
    fun flipRoms() {
        val p = _page.value as? DeviceSyncPage.Ready ?: return
        if (!p.romsAvailable) return
        romsToggle = !romsToggle
        _page.value = ready(p.link)
    }

    private fun ready(link: SyncLink) = DeviceSyncPage.Ready(link, romsToggle && platform.romsAvailable(), platform.romsAvailable())

    /** The notification's Cancel: stops a running sync, as confirmed Stop. */
    fun cancel() { if (job?.isActive == true) stop() }

    /** A button on the Device Sync screen; false when B on Ready/NoWifi leaves the tool (AppState shows Tools). */
    fun onButton(button: NxButton): Boolean {
        when (val p = _page.value) {
            is DeviceSyncPage.Ready -> when (button) {
                NxButton.UP, NxButton.DOWN -> pick(if (p.link == SyncLink.CABLE) SyncLink.WIFI else SyncLink.CABLE)
                NxButton.A -> start(p.link)
                NxButton.X -> if (p.romsAvailable) {
                    romsToggle = !romsToggle; _page.value = ready(p.link)
                    // On touch the toggle is an icon only, so it says what it did.
                    _notices.tryEmit(if (romsToggle) DeviceSyncText.ROMS_ON_NOTICE else DeviceSyncText.ROMS_OFF_NOTICE)
                }
                NxButton.B -> return false
                else -> Unit
            }
            DeviceSyncPage.NoWifi -> if (button == NxButton.B) _page.value = ready(SyncLink.WIFI)
            DeviceSyncPage.WaitingForCable -> if (button == NxButton.B) backToReady(SyncLink.CABLE)
            is DeviceSyncPage.Searching -> if (button == NxButton.B) backToReady(p.link)
            is DeviceSyncPage.Syncing -> when {
                p.confirmStop && button == NxButton.A -> stop()
                // On the live page, so a progress or file line landing in between is kept.
                button == NxButton.B -> _page.update { if (it is DeviceSyncPage.Syncing) it.copy(confirmStop = !it.confirmStop) else it }
            }
            is DeviceSyncPage.Done -> onEnded(button, p.link)
            is DeviceSyncPage.Failed -> onEnded(button, p.link)
        }
        return true
    }

    private fun onEnded(button: NxButton, link: SyncLink) {
        when (button) {
            NxButton.A -> start(link)
            NxButton.B -> _page.value = ready(link)
            else -> Unit
        }
    }

    // The canceller's page is set after cancel(): from then on the run's show()/edit() see an inactive job and keep it.
    private fun backToReady(link: SyncLink) {
        job?.cancel()
        _page.value = ready(link)
    }

    private fun stop() {
        job?.cancel()
        _page.value = DeviceSyncPage.Failed(runLink, DeviceSyncText.STOPPED)
    }

    private fun start(link: SyncLink) {
        // An ended run still cleaning up (a stopped server stopping its daemon): say so rather than ignore the press.
        if (running) { _notices.tryEmit(DeviceSyncText.FINISHING); return }
        if (platform.gameScreenAlive()) { _notices.tryEmit(DeviceSyncText.CLOSE_GAME); return }
        if (platform.importing()) { _notices.tryEmit(DeviceSyncText.IMPORTING); return }
        runLink = link
        val roms = romsToggle && platform.romsAvailable()
        job = scope.launch { sync(link, roms) }
    }

    /**
     * Shows [p] unless [me] was cancelled. The check runs inside the flow's compare-and-set, so a canceller's page (set
     * after its cancel()) always wins, even against a step's output callback on another thread.
     */
    private fun show(me: Job, p: DeviceSyncPage) = _page.update { if (me.isActive) p else it }

    /** Changes the live Syncing page (keeping a raised Stop dialog) unless [me] was cancelled. */
    private fun edit(me: Job, change: (DeviceSyncPage.Syncing) -> DeviceSyncPage.Syncing) =
        _page.update { if (me.isActive && it is DeviceSyncPage.Syncing) change(it) else it }

    /**
     * One run's file counts, [roms] whether a ROM file arrived, [collections] the collections of this phone that changed.
     * Updated with update{} since the daemon log and rsync output may arrive on another thread; [received], [roms] and
     * [collections] reach AppState however the run ends, since copied files stay.
     */
    private data class Tally(val received: Int = 0, val sent: Int = 0, val roms: Boolean = false, val collections: SyncedCollections = SyncedCollections())

    /** [roms]: the phone's own ROM request (the X toggle when the run started). */
    private suspend fun sync(link: SyncLink, roms: Boolean) {
        val me = currentCoroutineContext().job
        val tally = MutableStateFlow(Tally())
        platform.keepAlive(true, DeviceSyncText.TITLE)
        var startedCable = false
        // The cable dropped mid-run: the run ended for it, and the link is stopped even when it was up before the run.
        var cableDropped = false
        var socket: SyncSocket? = null
        // The paired peer, until the run ends well (or the peer itself failed): it is told FAIL, so it stops waiting.
        var failTo: String? = null
        // The client's Done once every step ran, before its DONE repeats: a handheld stops its cable at the first DONE.
        var finished: DeviceSyncPage.Done? = null
        try {
            if (!platform.rsyncAvailable()) { show(me, DeviceSyncPage.Failed(link, DeviceSyncText.NO_RSYNC)); return }
            val ownIp: String
            var cablePeer: String? = null
            // The cable's drop count once Up: any further drop ends the run ([watchingCable]).
            var drops: Int? = null
            if (link == SyncLink.CABLE) {
                if (platform.cable.state.value !is UsbLinkState.Up) {
                    show(me, DeviceSyncPage.WaitingForCable)
                    startedCable = true
                    platform.cable.start()
                }
                when (val s = platform.cable.state.first { it is UsbLinkState.Up || it is UsbLinkState.Failed || it is UsbLinkState.Lost }) {
                    is UsbLinkState.Up -> { ownIp = s.localIp; cablePeer = s.peerIp; drops = platform.cable.drops.value }
                    is UsbLinkState.Failed -> { show(me, DeviceSyncPage.Failed(link, NetplayText.usbFail(s))); return }
                    else -> { show(me, DeviceSyncPage.Failed(link, DeviceSyncText.CABLE_LOST)); return }
                }
            } else {
                val wifi = platform.wifiIp()
                if (wifi == null) { show(me, DeviceSyncPage.NoWifi); return }
                ownIp = wifi
            }
            val sock = platform.openSocket(link)
            if (sock == null) { show(me, DeviceSyncPage.Failed(link, DeviceSyncText.NO_SOCKET)); return }
            socket = sock
            val end = watchingCable(drops) {
                show(me, DeviceSyncPage.Searching(link, ownIp))
                log("searching on $link as $ownIp, roms $roms")
                val peer = when (val found = discover(sock, ownIp, cablePeer, roms)) {
                    is Peer -> found
                    Unpaired.MISSED_ACK -> { log("no ACK, only READY: failed"); return@watchingCable DeviceSyncPage.Failed(link, DeviceSyncText.MISSED_ACK) }
                    Unpaired.NONE -> { log("no device found"); return@watchingCable DeviceSyncPage.Failed(link, DeviceSyncText.NO_DEVICE) }
                }
                if (!peer.filters) return@watchingCable DeviceSyncPage.Failed(link, DeviceSyncText.OLD_FIRMWARE)   // latest firmware only
                val isServer = SyncRoles.isServer(ownClientOnly = true, peerClientOnly = peer.flags.clientOnly, ownIp = ownIp, peerIp = peer.ip)
                failTo = peer.ip
                if (isServer) serve(me, sock, link, ownIp, peer, roms, tally)
                else client(me, sock, link, ownIp, peer, roms, tally) { finished = it }
            } ?: run {
                cableDropped = true
                finished?.also { log("cable dropped after DONE; run done") } ?: run {
                    log("cable dropped; run ended")
                    DeviceSyncPage.Failed(link, DeviceSyncText.CABLE_DISCONNECTED)
                }
            }
            if (end !is DeviceSyncPage.Failed || end.reason == DeviceSyncText.PEER_FAILED) failTo = null
            show(me, end)
        } catch (e: CancellationException) {
            throw e   // first: a cancelled step must never read as a lost network
        } catch (e: Exception) {   // the socket's IOException: the network went away
            show(me, DeviceSyncPage.Failed(link, DeviceSyncText.NETWORK_LOST))
        } finally {
            withContext(NonCancellable) {
                failTo?.let { ip -> socket?.let { sendFail(it, ip) } }
                socket?.close()
                if (startedCable || cableDropped) platform.cable.stop()
                platform.keepAlive(false, null)
            }
            // Done, stopped, failed or timed out: files that arrived stay, so resume points must refresh.
            tally.value.let { t ->
                if (t.received > 0) _received.tryEmit(t.received)
                if (t.roms) _romsArrived.tryEmit(Unit)
                if (t.collections != SyncedCollections()) _collectionsArrived.tryEmit(t.collections)
            }
        }
    }

    /** FAIL to the paired peer [ip], repeated as DONE is; a send the lost network refuses only ends the repeats. */
    private suspend fun sendFail(sock: SyncSocket, ip: String) {
        log("run failed or stopped; sending FAIL to $ip")
        try {
            sendRepeated(sock, SyncWire.FAIL, ip)
        } catch (e: Exception) {
            log("FAIL not sent: $e")
        }
    }

    /** [text] to [ip] DONE_REPEATS times, DONE_GAP_MS apart: a datagram can be lost. */
    private suspend fun sendRepeated(sock: SyncSocket, text: String, ip: String) {
        repeat(DONE_REPEATS) { i ->
            sock.send(text, ip)
            if (i < DONE_REPEATS - 1) delay(DONE_GAP_MS)
        }
    }

    /**
     * [block], cancelled when the cable drops after [drops] (its count when the run saw it Up) or leaves Up: null then.
     * The cable's VPN outlives a drop and swallows packets without a reset, and rsync's keep-alives land in the socket
     * buffer and count as I/O, so neither its `--timeout` nor the daemon would ever notice. The cancel ends a step's rsync
     * or the daemon as Stop does. Null [drops] (Wi-Fi) runs [block] alone.
     */
    private suspend fun <T : Any> watchingCable(drops: Int?, block: suspend () -> T): T? {
        if (drops == null) return block()
        return coroutineScope {
            var dropped = false
            val work = async { block() }
            val watch = launch {
                combine(platform.cable.state, platform.cable.drops) { s, d -> s !is UsbLinkState.Up || d != drops }.first { it }
                dropped = true
                work.cancel()
            }
            try {
                work.await()
            } catch (e: CancellationException) {
                // A Stop cancels this scope too: only the watch's own cancel turns into null.
                if (dropped && isActive) null else throw e
            } finally {
                watch.cancel()
            }
        }
    }

    /** A HELLO from the paired [peer] after pairing means our ACK was lost: it is sent again. */
    private suspend fun reAck(sock: SyncSocket, d: SyncDatagram, message: SyncMessage?, ownIp: String, peer: Peer, roms: Boolean) {
        if (message is SyncMessage.Hello && message.ip == peer.ip && d.sourceIp == peer.ip) sock.send(SyncWire.ack(ownIp, roms), peer.ip)
    }

    private sealed interface Discovery
    private class Peer(val ip: String, val flags: SyncFlags, val filters: Boolean) : Discovery
    private enum class Unpaired : Discovery { MISSED_ACK, NONE }

    /**
     * HELLO each second (unicast to [cablePeer] on the cable, broadcast on Wi-Fi) until a fitting HELLO or ACK comes.
     * Null when one device keeps sending READY instead ([MISSED_ACK_READIES] in a row): it paired with our HELLO and
     * serves, but its one ACK never arrived, and a handheld answers no further HELLO (nx-redux `discovery_thread_func`
     * stops at its first peer), so searching on would never end. A READY says nothing of the sender's flags, so it never
     * pairs; a phone server re-ACKs our next HELLO ([reAck]) long before that count. [Unpaired.NONE] after
     * [SEARCH_MS] with no peer: the run holds the keep-alive service and keeps the screen on.
     */
    private suspend fun discover(sock: SyncSocket, ownIp: String, cablePeer: String?, roms: Boolean): Discovery {
        val hello = SyncWire.hello(ownIp, roms)
        var nextHello = now()
        val deadline = now() + SEARCH_MS
        var readyFrom: String? = null
        var readies = 0
        while (true) {
            if (now() >= deadline) return Unpaired.NONE
            if (now() >= nextHello) {
                if (cablePeer != null) sock.send(hello, cablePeer) else sock.broadcast(hello)
                nextHello = now() + INTERVAL_MS
            }
            val d = sock.receive(POLL_MS) ?: continue
            val m = SyncWire.parse(d.text)
            if (m == SyncMessage.Ready && fits(d.sourceIp, ownIp, cablePeer)) {
                readies = if (d.sourceIp == readyFrom) readies + 1 else 1
                readyFrom = d.sourceIp
                log("READY from ${d.sourceIp} while searching ($readies)")
                if (readies >= MISSED_ACK_READIES) return Unpaired.MISSED_ACK
                continue
            }
            val (ip, flags, filters) = when (m) {
                is SyncMessage.Hello -> Triple(m.ip, m.flags, m.filters)
                is SyncMessage.Ack -> Triple(m.ip, m.flags, m.filters)
                else -> continue   // a stale READY/DONE from an earlier sync never pairs
            }
            if (ip != d.sourceIp || !fits(ip, ownIp, cablePeer)) continue   // a spoofed payload, our own echo, another link's peer
            if (m is SyncMessage.Hello) sock.send(SyncWire.ack(ownIp, roms), ip)
            log("paired with $ip by ${if (m is SyncMessage.Hello) "HELLO" else "ACK"} ($flags, filters $filters)")
            return Peer(ip, flags, filters)
        }
    }

    /** [ip] may be a peer: not this phone, the cable's peer on the cable, and on Wi-Fi not a cable address. */
    private fun fits(ip: String, ownIp: String, cablePeer: String?): Boolean =
        ip != ownIp && (if (cablePeer != null) ip == cablePeer else SyncWire.ipValue(ip) !in CABLE_NET)

    private suspend fun client(
        me: Job, sock: SyncSocket, link: SyncLink, ownIp: String, peer: Peer, roms: Boolean, tally: MutableStateFlow<Tally>,
        onFinished: (DeviceSyncPage.Done) -> Unit,   // every step ran: the run is Done whatever its DONE repeats meet
    ): DeviceSyncPage {
        show(me, DeviceSyncPage.Syncing(link, peer.ip, SyncRole.CLIENT, null, 0, 0, listOf(DeviceSyncText.WAITING_PEER)))
        val deadline = now() + READY_WAIT_MS
        while (true) {
            if (now() >= deadline) return DeviceSyncPage.Failed(link, DeviceSyncText.noReady(peer.ip))
            val d = sock.receive(POLL_MS) ?: continue
            val m = SyncWire.parse(d.text)
            if (d.sourceIp == peer.ip && m == SyncMessage.Ready) break
            reAck(sock, d, m, ownIp, peer, roms)
        }
        // ROMs run when either side asked and this phone can; asked but unable, steps 1–4 run and Done says so.
        val romsOn = roms || peer.flags.romsOn
        val access = platform.romsAvailable()
        val withRoms = romsOn && access
        log("READY from ${peer.ip}: roms asked $romsOn, access $access")
        if (!platform.checkSaveFileTimes()) return DeviceSyncPage.Failed(link, DeviceSyncText.NO_SAVE_FILE_TIMES)
        // BIOS runs on every sync both sides can take part in; one that can't skips it with a note, never a failure.
        // Its notes come first on Done.
        val biosNotes = ArrayList<String>()
        val collectionNotes = ArrayList<String>()
        val notes = ArrayList<String>()
        if (!access) { biosNotes += DeviceSyncText.BIOS_NO_ACCESS; collectionNotes += DeviceSyncText.COLLECTIONS_NO_ACCESS }
        if (romsOn && !withRoms) notes += DeviceSyncText.ROMS_SKIPPED
        val steps = when { withRoms -> 9; access -> 7; else -> 4 }
        var prep: RomPrep? = null
        if (withRoms) {
            val r = tryOr({ e ->
                log("ROM prep threw: $e")
                PrepFailed(DeviceSyncText.stepError(SyncStep.SEND_ROMS))
            }) { romPrep(peer) }
            when (r) {
                is PrepFailed -> { log("ROM prep failed: ${r.reason} ${r.detail.orEmpty()}"); return DeviceSyncPage.Failed(link, r.reason, r.detail) }
                is RomPrep -> { prep = r; notes += r.notes }
                is BiosPrep -> Unit
            }
        }
        // A handheld keeps battery saves per emulator: steps 2 and 4 run against staging trees in its layout. A phone
        // peer has this phone's layout: the whole Saves/ as is.
        log("save plan")
        val saves = if (peer.flags.clientOnly) null else tryOr({ return DeviceSyncPage.Failed(link, DeviceSyncText.stepError(SyncStep.SEND_SAVES)) }) {
            savePlan(peer, prep?.consoles, access)
        }
        try {
            if (saves != null) {
                val staged = tryOr({ false }) {
                    val mine = platform.listHomeSaves()
                    val (send, seeds) = planned { saves.send(mine) to saves.seeds(mine) }
                    platform.stageSaves(send, seeds)
                }
                if (!staged) return DeviceSyncPage.Failed(link, DeviceSyncText.stepError(SyncStep.SEND_SAVES))
            }
            for (step in SyncStep.entries.filter { it.module == "shared" || it.module == "saves" }) {
                var error: String? = null
                val tags = saves?.receiveTags?.takeIf { step.module == "saves" }
                log("step ${step.number} ${step.name}")
                // A throw here is rsync failing to run, not a socket's lost network.
                val code = tryOr({ return DeviceSyncPage.Failed(link, DeviceSyncText.stepError(step)) }) {
                    runStep(me, link, peer, step, steps, null, tally, keep = { error = rsyncError(it) ?: error }) { platform.runClientStep(step, peer.ip, it, tags) }
                }
                if (code != 0) return DeviceSyncPage.Failed(link, DeviceSyncText.stepFailed(step, code), error)
            }
            if (saves != null) {
                val fold = tryOrNull {
                    val (received, home) = platform.listReceivedSaves() to platform.listHomeSaves()
                    planned { saves.fold(received, home) }.takeIf { platform.applySaveFold(it) }
                } ?: return DeviceSyncPage.Failed(link, DeviceSyncText.stepError(SyncStep.RECEIVE_SAVES))
                if (fold.skipped > 0) notes += DeviceSyncText.savesSkipped(fold.skipped)
            }
        } finally {
            if (saves != null) withContext(NonCancellable) {
                try { platform.clearSaveStaging() } catch (e: Exception) { }   // the platform logs a failed clean-up; emptied again before the next staging
            }
        }
        // Listed and paired only here, after step 4: a BIOS listing failure never keeps the saves from moving.
        if (access) {
            log("saves done; BIOS prep")
            val r = tryOr({ PrepFailed(DeviceSyncText.stepError(SyncStep.SEND_BIOS)) }) { biosPrep(peer) }
            when (r) {
                is PrepFailed -> return DeviceSyncPage.Failed(link, r.reason, r.detail)
                is BiosPrep -> {
                    r.note?.let { biosNotes += it }
                    r.runs?.let { runs -> runRuns(me, link, peer, runs, steps, null, tally)?.let { return it } }
                }
                is RomPrep -> Unit
            }
            // Step 7, every sync this phone can take part in: after BIOS, before the ROMs.
            log("collections")
            val c = syncCollections(me, link, peer, steps, prep?.consoles, prep?.gameTags, tally)
            c.failed?.let { return it }
            c.note?.let { collectionNotes += it }
        }
        prep?.let { p -> log("ROM runs: ${p.runs.size}"); runRuns(me, link, peer, p.runs, steps, p, tally)?.let { return it } }
        val done = tally.value.let { DeviceSyncPage.Done(link, it.received, it.sent, biosNotes + collectionNotes + notes) }
        onFinished(done)
        log("done; sending DONE")
        sendRepeated(sock, SyncWire.DONE, peer.ip)
        return done
    }

    /** Step 7's outcome: a failure's page, or a skip's [note], or neither. */
    private class CollectionsOutcome(val failed: DeviceSyncPage.Failed? = null, val note: String? = null)

    /**
     * Step 7: the peer's collections pulled, merged with this phone's (lines translated by [consoles], the ROM prep's
     * listing, else one made here; a listing that fails only translates less), this phone's changed ones written through
     * the app's writer, the peer's changed ones pushed. Only rsync's `Unknown module` exit 5 skips with a note. A
     * handheld peer's lines map onto its emulator folders ([CollectionPaths] mapped) with [gameTags] (the ROM prep's,
     * else read here).
     */
    private suspend fun syncCollections(
        me: Job, link: SyncLink, peer: Peer, steps: Int, consoles: ConsoleList?, gameTags: Map<String, Map<String, String>>?,
        tally: MutableStateFlow<Tally>,
    ): CollectionsOutcome {
        val step = SyncStep.SYNC_COLLECTIONS
        edit(me) { it.copy(step = step, steps = steps, console = null, done = 0, total = 0) }
        platform.keepAlive(true, notification(peer.ip, link, step, steps, null, 0, 0))
        fun failed(reason: String, detail: String? = null) = CollectionsOutcome(DeviceSyncPage.Failed(link, reason, detail))
        val pull = tryOr({ return failed(DeviceSyncText.stepError(step)) }) { platform.pullCollections(peer.ip) }
        if (pull.code == UNKNOWN_MODULE && pull.lines.any { UNKNOWN_MODULE_TEXT in it }) {
            return CollectionsOutcome(note = if (peer.flags.clientOnly) DeviceSyncText.COLLECTIONS_PEER_PHONE else DeviceSyncText.COLLECTIONS_OLD_HANDHELD)
        }
        if (pull.code != 0) return failed(DeviceSyncText.stepFailed(step, pull.code), errorDetail(pull.lines))
        val list = consoles ?: tryOrNull {
            when (val r = listConsoles(peer)) {
                is ConsolesListed -> r.list
                is ConsolesFailed -> r.paired
            }
        }
        val mapped = !peer.flags.clientOnly
        // Tags that would not read only send a line held nowhere there to its console's default emulator folder.
        val tags = if (!mapped || list == null) emptyMap() else gameTags
            ?: tryOr({ emptyMap() }) { gameTags(list) }
        val paths = list?.let {
            CollectionPaths(it.pairing.pairs, it.localAll, it.remoteAll, it.own, it.theirs, it.homeNames, it.remoteNames, mapped, tags)
        } ?: CollectionPaths()
        val own = tryOr({ return failed(DeviceSyncText.stepError(step)) }) { platform.readCollections() }
        val plan = planned { SyncCollections.plan(own, pull.files, paths, pull.unreadable) }
        var unsaved = false
        for ((name, lines) in plan.own) {
            val ok = tryOr({ false }) { platform.writeCollection(name, lines, own[name]) }
            if (ok) tally.update { it.copy(received = it.received + 1, collections = it.collections.copy(written = it.collections.written + name)) }
            else unsaved = true
        }
        // Accepted: a peer file edited on the peer between this pull and the push below is replaced by the merge of what
        // was pulled; the window is only the merge and this phone's writes (well under a second).
        if (plan.peer.isNotEmpty()) {
            val (code, lines) = tryOr({ return failed(DeviceSyncText.stepError(step)) }) { platform.pushCollections(peer.ip, plan.peer) }
            if (code != 0) return failed(DeviceSyncText.stepFailed(step, code), errorDetail(lines))
            tally.update { it.copy(sent = it.sent + plan.peer.size) }
        }
        return CollectionsOutcome(note = DeviceSyncText.COLLECTIONS_NOT_SAVED.takeIf { unsaved })
    }

    /**
     * [block] (an rsync listing) between two log lines, the second with its exit code, line count and time: a stall
     * shows as the first alone.
     */
    private suspend fun timed(what: String, block: suspend () -> Pair<Int, List<String>>): Pair<Int, List<String>> {
        log("$what…")
        val start = now()
        return block().also { (code, lines) -> log("$what: exit $code, ${lines.size} lines, ${now() - start} ms") }
    }

    private fun log(message: String) = platform.log("device sync: $message")

    /** [block], pure CPU work (a listing parsed, a plan made), on [work]. */
    private suspend fun <T> planned(block: () -> T): T = withContext(work) { block() }

    /** rsync's last error line in [lines]: its own (`rsync: …`) or the daemon's refusal (`@ERROR: …`), without the prefix. */
    private fun errorDetail(lines: List<String>): String? =
        lines.mapNotNull { rsyncError(it) ?: it.takeIf { l -> l.startsWith(DAEMON_ERROR) }?.removePrefix(DAEMON_ERROR)?.trim() }.lastOrNull()

    /**
     * One rsync call as [step] of [steps] ([console]: the console a ROM run works on): the page, the notification, live
     * counts and file lines; each raw line also to [keep]. [run] starts it with the line callback; its exit code.
     */
    private suspend fun runStep(
        me: Job, link: SyncLink, peer: Peer, step: SyncStep, steps: Int, console: String?, tally: MutableStateFlow<Tally>,
        keep: (String) -> Unit = {}, run: suspend ((String) -> Unit) -> Int,
    ): Int {
        val before = tally.value
        edit(me) { it.copy(step = step, steps = steps, console = console, done = 0, total = 0) }
        platform.keepAlive(true, notification(peer.ip, link, step, steps, console, 0, 0))
        // The notification follows at most every NOTIFY_MS: a line per file would flood the binder. Lines come
        // one at a time from the step's reader, so a plain local is enough.
        var notified = now()
        return run { line ->
            keep(line)
            val pr = SyncProgress.progress(line)
            if (pr != null) {
                // Counted live: a step that fails or is stopped still copied these files.
                tally.update {
                    if (step.push) it.copy(sent = before.sent + pr.transferred)
                    else it.copy(received = before.received + pr.transferred, roms = it.roms || (step == SyncStep.RECEIVE_ROMS && pr.transferred > 0))
                }
                edit(me) { it.copy(done = pr.done, total = pr.total) }
                val t = now()
                if (me.isActive && t - notified >= NOTIFY_MS) {
                    notified = t
                    platform.keepAlive(true, notification(peer.ip, link, step, steps, console, pr.done, pr.total))
                }
            } else {
                SyncProgress.fileLine(line)?.let { f -> edit(me) { it.copy(lines = it.lines.plusCapped(f)) } }
            }
        }
    }

    private sealed interface Prep
    /** [detail]: rsync's own error line, when it gave one. */
    private class PrepFailed(val reason: String, val detail: String? = null) : Prep
    /** BIOS sync's plan: [runs] in order, or null with a [note] when the peer can't take part (no `[bios]` module). */
    private class BiosPrep(val runs: List<RomRun>?, val note: String?) : Prep
    /**
     * ROM sync's plan: [runs] in order, [pairs] and their extra [units] by [ConsolePair.key], [notes] for Done, the
     * [consoles] listing it was made from (collections translate with it), the peer folders that have a `hashes.json`
     * ([raFolders]) and a handheld peer's per-folder game tags ([gameTags], empty for a peer phone).
     */
    private class RomPrep(
        val runs: List<RomRun>, val pairs: Map<String, ConsolePair>, val units: Map<String, PairUnits>, val notes: List<String>,
        val consoles: ConsoleList, val raFolders: Set<String>, val gameTags: Map<String, Map<String, String>>,
    ) : Prep

    /**
     * Both sides' consoles: the [pairing] of the top-level folders [localAll] / [remoteAll], both phones' extras ([own],
     * [theirs]: empty for a handheld), the names in this phone's paired folders and their siblings ([homeNames]) and in the
     * peer's folders it listed ([remoteNames], lowercase; [remoteEntries] as listed, for the saves plan).
     */
    private class ConsoleList(
        val pairing: Pairing, val localAll: List<String>, val remoteAll: List<String>, val own: ExtrasManifest,
        val theirs: ExtrasManifest, val homeNames: Map<String, Set<String>>, val remoteNames: Map<String, Set<String>>,
        val remoteEntries: Map<String, Set<String>> = emptyMap(),
    )

    private sealed interface ConsolesResult
    private class ConsolesListed(val list: ConsoleList) : ConsolesResult
    /** A listing failed with [reason]; [paired]: the pairing alone (no extras, no names) when the top level had listed. */
    private class ConsolesFailed(val reason: PrepFailed, val paired: ConsoleList?) : ConsolesResult

    /**
     * After step 4: the peer's `bios/` tag folders paired with this phone's `Bios/` ones (a handheld's by console,
     * [SyncRoms.biosPlanMapped]; a peer phone's by name, [SyncRoms.biosPlan]). Only rsync's unknown-module
     * refusal (exit 5 with `Unknown module`) is a peer that can't sync BIOS — a handheld on older firmware, or a phone
     * without All files access — and only skips; any other exit 5 (`chdir failed`, say) fails as step 5.
     */
    private suspend fun biosPrep(peer: Peer): Prep {
        val (code, lines) = platform.listRemoteBios(peer.ip)
        if (code == UNKNOWN_MODULE && lines.any { UNKNOWN_MODULE_TEXT in it }) {
            return BiosPrep(null, if (peer.flags.clientOnly) DeviceSyncText.BIOS_PEER_PHONE else DeviceSyncText.BIOS_OLD_HANDHELD)
        }
        // The daemon's refusals come as `@ERROR: …`, the client's own errors as `rsync: …`.
        if (code != 0) return PrepFailed(DeviceSyncText.stepFailed(SyncStep.SEND_BIOS, code), errorDetail(lines))
        val local = platform.listHomeBios()
        return BiosPrep(planned {
            val remote = SyncConsoles.parseListing(lines)
            if (peer.flags.clientOnly) SyncRoms.biosPlan(local, remote) else SyncRoms.biosPlanMapped(local, remote)
        }, null)
    }

    /**
     * Before step 1: the file-time check, both sides' consoles paired, both sides' extras, the plan. A handheld peer's
     * pairs also get each landing folder's game tags (its `systems.txt` when the tag is the console's, else the console's
     * Default emulators tag) and `.media` files, so each pair pushes only its emulators' games and their art.
     */
    private suspend fun romPrep(peer: Peer): Prep {
        log("ROM prep: file-time check")
        if (!platform.checkFileTimes()) return PrepFailed(DeviceSyncText.NO_FILE_TIMES)
        val list = when (val r = listConsoles(peer)) {
            is ConsolesListed -> r.list
            is ConsolesFailed -> return r.reason
        }
        val (pairing, own, theirs) = Triple(list.pairing, list.own, list.theirs)
        val mapped = !peer.flags.clientOnly
        log("ROM prep: game tags and art of ${pairing.pairs.size} pairs")
        val gameTags = if (mapped) gameTags(list) else emptyMap()
        val localArt = HashMap<String, List<String>>()
        if (mapped) for (local in gameTags.keys) localArt[local] = homeArt(local)
        val (units, runs) = planned {
            pairing.pairs.associate {
                it.key to SyncRoms.units(it, own, theirs, list.homeNames, list.remoteNames, list.remoteAll, mapped)
            } to SyncRoms.plan(pairing, own, theirs, list.homeNames, list.remoteNames, peerIsPhone = peer.flags.clientOnly,
                peerFolders = list.remoteAll, gameTags = gameTags, localArt = localArt, mapped = mapped, remoteEntries = list.remoteEntries)
        }
        val notes = if (pairing.skippedTags.isEmpty()) emptyList() else listOf(DeviceSyncText.onlyOneSide(pairing.skippedTags))
        log("ROM prep: ${runs.size} runs planned; listing hashes.json")
        val (hashCode, hashLines) = timed("hashes.json listing") { platform.listRemoteHashes(peer.ip) }
        // 23: some console folders have no hashes.json, which is what the listing is for; any other failure fails the step.
        if (hashCode != 0 && !(hashCode == PARTIAL && hashLines.filter { it.startsWith(RSYNC_ERROR) }.all { NO_SUCH_FILE in it })) {
            return PrepFailed(DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, hashCode), errorDetail(hashLines))
        }
        return RomPrep(runs, pairing.pairs.associateBy { it.key }, units, notes, list, planned { SyncConsoles.parseHashFolders(hashLines) }, gameTags)
    }

    /**
     * A handheld peer's landing folders (the `local` of [list]'s pairs, of a phone console) → each top-level name's
     * emulator tag: its `systems.txt` tag when that is one of the console's, else the console's Default emulators tag (a
     * folder's tag names only its console).
     */
    private suspend fun gameTags(list: ConsoleList): Map<String, Map<String, String>> {
        val out = LinkedHashMap<String, Map<String, String>>()
        val defaults = platform.defaultTags()
        for (local in list.pairing.pairs.map { it.local }.distinct()) {
            val console = SyncMap.consoleOf(local) ?: continue
            // As the library scanner and the save prep.
            val untagged = console.defaultTagFor(defaults[console.id])
            val tags = platform.homeGameTags(local)
            // A `systems.txt` tag of another console is no emulator of this one: ignored.
            out[local] = list.homeNames[local].orEmpty().associateWith { n ->
                tags[n]?.trim()?.uppercase()?.takeIf { it in console.tags } ?: untagged
            }
        }
        return out
    }

    /**
     * Home `Roms/<folder>/.media`'s files: `<slot>/<file>` for each typed art slot a handheld shows ([ArtworkResolver.TYPED]
     * less the phone-only ones), plus its top-level image files (`bg.png`).
     */
    private suspend fun homeArt(folder: String): List<String> {
        val media = "$folder/.media"
        return platform.listHomeRoms(media).flatMap { name ->
            when {
                name in ArtworkResolver.TYPED && name !in SyncRoms.PHONE_ONLY_SLOTS -> platform.listHomeRoms("$media/$name").map { "$name/$it" }
                name.substringAfterLast('.', "").lowercase() in ART_EXTENSIONS -> listOf(name)
                else -> emptyList()
            }
        }
    }

    /**
     * Before step 1, a handheld peer only: the battery saves plan. Which games each handheld ROM folder of a phone console
     * holds comes from [consoles] (the ROM prep's listing, ROMs on) or is listed here; a listing that fails is no
     * failure, the plan only knows fewer games (a save then goes where its game's ROM push would; a shared GPGX save is
     * skipped). With no top-level listing at all (no `[roms]` module, or it failed) every save folder the handheld shares
     * across consoles (GPGX) is skipped.
     * A game's emulator and ROM name come from this phone's ROM folders when it may read them ([access]), else the
     * Default emulators setting. The planner's callbacks don't suspend, so those folders are read up front, once per sync.
     */
    private suspend fun savePlan(peer: Peer, consoles: ConsoleList?, access: Boolean): SaveSyncPlan {
        val listed = consoles?.remoteAll ?: tryOrNull { platform.listRemoteRoms(peer.ip) }
            ?.takeIf { it.first == 0 }?.let { planned { SyncConsoles.parseListing(it.second) } }
        val folders = listed.orEmpty()
        val phoneFolders = consoles?.localAll ?: if (access) tryOrNull { platform.listHomeRoms(null) }.orEmpty() else emptyList()
        val roms = HashMap(consoles?.remoteEntries.orEmpty())
        for (folder in folders) {
            if (folder in roms || SyncMap.consoleOf(folder) == null) continue
            val listed = tryOrNull { timed("save prep listing of $folder") { platform.listRemoteFolder(peer.ip, folder) } }?.takeIf { it.first == 0 } ?: continue
            roms[folder] = planned { SyncConsoles.parseEntries(listed.second).toSet() }
        }
        val phoneGames = if (access) tryOrNull { phoneGames(SyncMap.saveConsoles(folders).values.flatten().toSet()) }.orEmpty() else emptyMap()
        val defaults = platform.defaultTags()
        val format = platform.saveFormat()
        return planned {
            SaveSyncPlan(
                listed, roms,
                emulatorTag = { c, g ->
                    phoneGames[c]?.get(g)?.second ?: CoreRegistry.consoleOf(c)?.let { it.defaultTagFor(defaults[it.id]) } ?: c
                },
                romName = { c, g -> phoneGames[c]?.get(g)?.first },
                format = format,
                phoneFolders = phoneFolders,
            )
        }
    }

    /**
     * This phone's games of [consoles] (console tags): console tag → game key → its ROM file name and, when it runs under
     * another tag of its console (a `systems.txt` entry; a folder's tag names only its console), that tag. The
     * console-tag folder is read first and wins a name clash.
     */
    private suspend fun phoneGames(consoles: Set<String>): Map<String, Map<String, Pair<String, String?>>> {
        val out = HashMap<String, HashMap<String, Pair<String, String?>>>()
        val folders = platform.listHomeRoms(null).mapNotNull { f ->
            val c = SyncMap.consoleOf(f)?.defaultTag?.takeIf { it in consoles } ?: return@mapNotNull null
            val tag = Names.parseSystemFolder(f)?.second?.uppercase() ?: return@mapNotNull null
            Triple(f, c, tag)
        }.sortedBy { (_, c, tag) -> if (tag == c) 0 else 1 }
        for ((folder, c, _) in folders) {
            val tags = platform.homeGameTags(folder)
            val games = out.getOrPut(c) { HashMap() }
            for (name in platform.listHomeRoms(folder)) {
                if (Names.isHidden(name)) continue
                games.getOrPut(SaveKeys.romKey(name)) { name to tags[name]?.takeIf { CoreRegistry.consoleTag(it) == c } }
            }
        }
        return out
    }

    /**
     * Both sides' consoles paired, both sides' extras, the names in the paired folders and their siblings. A handheld peer
     * pairs by console ([SyncConsoles.pairMapped]): the names of every folder here of a paired console, and of every one
     * of its folders there.
     */
    private suspend fun listConsoles(peer: Peer): ConsolesResult {
        val (code, lines) = timed("roms listing") { platform.listRemoteRoms(peer.ip) }
        if (code == UNKNOWN_MODULE) return ConsolesFailed(PrepFailed(DeviceSyncText.PEER_NO_ROMS), null)
        if (code != 0) return ConsolesFailed(PrepFailed(DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, code)), null)
        val localAll = platform.listHomeRoms(null)
        val mapped = !peer.flags.clientOnly
        val (remoteAll, pairing) = planned {
            val remoteAll = SyncConsoles.parseListing(lines)
            remoteAll to if (mapped) SyncConsoles.pairMapped(localAll, remoteAll) else SyncConsoles.pair(localAll, remoteAll)
        }
        val paired = ConsoleList(pairing, localAll, remoteAll, ExtrasManifest(), ExtrasManifest(), emptyMap(), emptyMap())
        fun failed(reason: String) = ConsolesFailed(PrepFailed(reason), paired)
        val own = platform.ownExtras().manifest
        // Only a phone (client-only) has extras; its list is untrusted, so decode tolerates junk and the plan drops unsafe units.
        val theirs = if (!peer.flags.clientOnly) ExtrasManifest() else when (val r = platform.fetchRemoteText(peer.ip, "nx-extras", "extras.json")) {
            is RemoteText.Found -> SyncExtras.decode(r.text)
            RemoteText.Absent -> return failed(DeviceSyncText.PEER_NO_ROMS)
            is RemoteText.Failed -> return failed(
                if (r.code == UNKNOWN_MODULE) DeviceSyncText.PEER_NO_ROMS else DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, r.code))
        }
        // No second copy across a console's emulator folders: the names each side keeps in a pair's sibling folders.
        val consoles = if (mapped) pairing.pairs.mapNotNull { SyncMap.consoleOf(it.local)?.id }.toSet() else emptySet()
        val homeFolders = if (mapped) localAll.filter { !it.startsWith(".") && SyncMap.consoleOf(it)?.id in consoles }
            else pairing.pairs.flatMap { listOf(it.local) + SyncConsoles.siblings(it.local, localAll) }
        val homeNames = homeFolders.distinct().associateWith { platform.listHomeRoms(it).toSet() }
        log("listed ${homeNames.size} home folders; ${pairing.pairs.size} pairs")
        val remoteNames = LinkedHashMap<String, Set<String>>()
        val remoteEntries = HashMap<String, Set<String>>()
        for (pair in pairing.pairs) {
            val siblings = SyncConsoles.siblings(pair.remote, remoteAll)
            // A peer phone's extras of this console count less the names its folder has, so that folder is listed too.
            val tags = siblings.mapNotNull { Names.parseSystemFolder(it)?.second?.uppercase() }.toSet() + pair.tag.uppercase()
            if (!mapped && siblings.isEmpty() && theirs.units.none { it.tag.uppercase() in tags || SyncMap.ofConsole(it.tag, pair.remote) }) continue
            for (folder in listOf(pair.remote) + siblings) {
                if (folder in remoteNames) continue
                val (listed, entries) = timed("listing of $folder") { platform.listRemoteFolder(peer.ip, folder) }
                if (listed != 0) return failed(DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, listed))
                val names = planned { SyncConsoles.parseEntries(entries) }
                remoteNames[folder] = names.mapTo(HashSet()) { it.lowercase() }
                remoteEntries[folder] = names.toSet()
            }
        }
        return ConsolesListed(ConsoleList(pairing, localAll, remoteAll, own, theirs, homeNames, remoteNames, remoteEntries))
    }

    /**
     * Steps 5 and 6 (BIOS, [prep] null) or 8 and 9 (ROMs: each pair's hashes.json merge before its first push), of
     * [steps]: every run of [runs] in order; a failure's page, or null.
     */
    private suspend fun runRuns(
        me: Job, link: SyncLink, peer: Peer, runs: List<RomRun>, steps: Int, prep: RomPrep?, tally: MutableStateFlow<Tally>,
    ): DeviceSyncPage.Failed? {
        val merged = HashSet<String>()
        val seen = HashMap<String, Set<String>>()
        for (run in runs) {
            val pair = prep?.pairs?.get(run.pair)
            if (prep != null && run.step == SyncStep.SEND_ROMS && pair != null && merged.add(run.pair)) {
                edit(me) { it.copy(step = SyncStep.SEND_ROMS, steps = steps, console = run.console, done = 0, total = 0) }
                val units = prep.units.getValue(run.pair)
                val keep = if (peer.flags.clientOnly) null else hashNames(prep, pair, units)
                log("hashes.json merge for ${pair.local} ← ${pair.remote}")
                val failed = tryOr({ DeviceSyncText.stepError(SyncStep.SEND_ROMS) }) { mergeHashes(peer.ip, pair, units, prep.raFolders, keep) }
                if (failed != null) { log("hashes.json merge failed: $failed"); return DeviceSyncPage.Failed(link, failed) }
            }
            val last = ArrayDeque<String>()
            log("${run.step} ${run.from} → ${run.to}…")
            val start = now()
            val code = tryOr({ e ->
                log("${run.step} could not run: $e")
                return DeviceSyncPage.Failed(link, DeviceSyncText.stepError(run.step))
            }) {
                runStep(me, link, peer, run.step, steps, run.console, tally, keep = { last.addLast(it); if (last.size > KEPT_LINES) last.removeFirst() }) {
                    platform.runRom(run, peer.ip, it)
                }
            }
            log("${run.step} ${run.from} → ${run.to}: exit $code, ${now() - start} ms")
            if (code != 0) return when {
                last.any { NO_SPACE in it } -> DeviceSyncPage.Failed(link, if (run.step.push) DeviceSyncText.NO_SPACE_PEER else DeviceSyncText.NO_SPACE_HERE)
                else -> DeviceSyncPage.Failed(link, DeviceSyncText.stepFailed(run.step, code), last.mapNotNull(::rsyncError).lastOrNull())
            }
            if (prep != null && pair != null && !peer.flags.clientOnly && run.step == SyncStep.RECEIVE_ROMS && run.from == RomEnd.Peer("roms", pair.remote)) {
                tagPulled(prep, pair, seen)
            }
        }
        return null
    }

    /**
     * After a handheld pair's ROM pull: the names new in its landing folder ([seen]: per landing folder, the names known
     * so far, starting from the prep's listing; an earlier pair's pull of the same name wins) get their handheld folder's
     * emulator as their `systems.txt` pick ([SyncRoms.pulledGameTags]), never replacing an entry there; a disc folder's
     * goes in its own `systems.txt` under its sheet's name ([SyncRoms.pulledDiscTag]). A failure here only leaves those
     * games on the Default emulators pick.
     */
    private suspend fun tagPulled(prep: RomPrep, pair: ConsolePair, seen: MutableMap<String, Set<String>>) {
        tryQuietly {
            val after = platform.listHomeRoms(pair.local)
            val known = seen.getOrPut(pair.local) { prep.consoles.homeNames[pair.local].orEmpty() }.mapTo(HashSet()) { it.lowercase() }
            seen[pair.local] = known + after.map { it.lowercase() }
            val added = after.filter { it.lowercase() !in known }
            val console = SyncMap.consoleOf(pair.local) ?: return
            if (added.isEmpty()) return
            val default = console.defaultTagFor(platform.defaultTags()[console.id])
            val candidates = SyncRoms.pulledGameTags(pair, added, emptyMap(), default).keys   // none when the emulator is the default
            if (candidates.isEmpty()) return
            val files = ArrayList<String>()
            for (name in candidates) {
                val dir = "${pair.local}/$name"
                val children = platform.listHomeRoms(dir)
                if (children.isEmpty()) { files += name; continue }   // a file (or an empty folder) lists nothing
                val (sheet, tag) = SyncRoms.pulledDiscTag(pair, name, children, platform.homeGameTags(dir), default) ?: continue
                writeTags(dir, mapOf(sheet to tag))
            }
            writeTags(pair.local, SyncRoms.pulledGameTags(pair, files, platform.homeGameTags(pair.local), default))
        }
    }

    /** Adds [entries] to home `Roms/<dir>/systems.txt`, keeping what it holds; nothing is written for none. */
    private suspend fun writeTags(dir: String, entries: Map<String, String>) {
        if (entries.isEmpty()) return
        val path = "$dir/${SidecarTxt.FILE}"
        platform.writeHomeText(path, entries.entries.fold(platform.readHomeText(path).orEmpty()) { t, (name, tag) -> SidecarTxt.with(t, name, tag) })
    }

    /** These lines and [line], the oldest past MAX_LINES dropped: one copy, since it runs for every rsync file line. */
    private fun List<String>.plusCapped(line: String): List<String> {
        val from = (size + 1 - MAX_LINES).coerceIn(0, size)
        val out = ArrayList<String>(size - from + 1)
        out.addAll(subList(from, size))
        out.add(line)
        return out
    }

    /** [line] when it is one of rsync's own error lines (`rsync: …`), without the prefix; its closing `rsync error:` summary is not. */
    private fun rsyncError(line: String): String? = line.takeIf { it.startsWith(RSYNC_ERROR) }?.removePrefix(RSYNC_ERROR)?.trim()

    /**
     * The top-level names (lowercase) a handheld folder's `hashes.json` keeps for pair [p]: those its listing has and
     * those the pair's push sends there (home games of its emulators the peer has nowhere, and this phone's extras it
     * pushes).
     */
    private fun hashNames(prep: RomPrep, p: ConsolePair, units: PairUnits): Set<String> {
        val home = prep.consoles.homeNames[p.local].orEmpty()
        val skipped = (SyncRoms.held(p, home, prep.gameTags[p.local]) + units.pushSkip).toSet()
        return prep.consoles.remoteNames[p.remote].orEmpty() +
            (home.filter { !it.startsWith(".") && it !in skipped } + units.mine.map { it.landing }).map { it.lowercase() }
    }

    /**
     * One pair's `hashes.json` ([RaFingerprintMerge]): the peer's file and ours merged, the extras each side sends
     * re-keyed to where they land; ours written, the peer's pushed, each only when it changed. [keep] (a handheld peer,
     * [hashNames]): the peer's copy holds only entries whose key's first segment is one of these names (ignoring case),
     * never a `.sources/` key. A failure's reason, or null.
     */
    private suspend fun mergeHashes(peerIp: String, p: ConsolePair, units: PairUnits, raFolders: Set<String>, keep: Set<String>?): String? {
        val peerText = when (val r = if (p.remote in raFolders) platform.fetchRemoteText(peerIp, "roms", "${p.remote}/$RA_DIR/$HASHES") else RemoteText.Absent) {
            is RemoteText.Found -> r.text
            RemoteText.Absent -> null
            is RemoteText.Failed -> return DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, r.code)
        }
        // Our file exists but cannot be read: merging from nothing would overwrite it, so this pair's merge is skipped.
        val own = tryOr({ return null }) { platform.readHomeText("${p.local}/$RA_DIR/$HASHES") }

        val (m, peerOut) = planned {
            val m = RaFingerprintMerge.plan(
                own = own, peer = peerText,
                ownSent = units.mine.map { Rekey(it.key, it.landingKey) }, peerSent = units.theirs.map { Rekey(it.key, it.landingKey) },
            )
            m to m.peer?.let { text -> if (keep == null) text else onlyGames(text, keep, peerText) }
        }
        // Not fatal: a fingerprint file that stays behind only means hashing those games again later.
        m.own?.let { platform.writeHomeText("${p.local}/$RA_DIR/$HASHES", it) }
        peerOut?.let { text ->
            val code = platform.pushRemoteText(peerIp, "roms", "${p.remote}/$RA_DIR", HASHES, text)
            if (code != 0) return DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, code)
        }
        return null
    }

    /**
     * [merged] less the entries for games outside [keep] (lowercase top-level names; a `.sources/` key never stays);
     * null when that leaves the peer's file as it was ([peerText]).
     */
    private fun onlyGames(merged: String, keep: Set<String>, peerText: String?): String? {
        val f = RaFingerprintFile.parse(merged)
        for (k in f.keys()) if (k.startsWith(SOURCES) || k.substringBefore('/').lowercase() !in keep) f.forget(k)
        val out = f.encode()
        return out.takeIf { it != RaFingerprintFile.parse(peerText).encode() }
    }

    private suspend fun serve(
        me: Job, sock: SyncSocket, link: SyncLink, ownIp: String, peer: Peer, roms: Boolean, tally: MutableStateFlow<Tally>,
    ): DeviceSyncPage {
        try {
            serverActivity = now()
            // The daemon's open connections, by pid: while one is open the peer is mid-step and says nothing.
            val sessions = MutableStateFlow(emptySet<String>())
            if (!platform.checkSaveFileTimes()) return DeviceSyncPage.Failed(link, DeviceSyncText.NO_SAVE_FILE_TIMES)
            val started = platform.startServer(peer.ip, roms || peer.flags.romsOn) { line ->
                serverActivity = now()   // any daemon line, counted or not: the peer is still working with it
                SyncProgress.daemonSession(line)?.let { (pid, open) -> sessions.update { if (open) it + pid else it - pid } }
                val e = SyncProgress.daemonLine(line) ?: return@startServer
                tally.update {
                    when {
                        // The peer reads every collection to merge it on its side (step 7): a send there changes nothing.
                        !e.received && e.module == "collections" -> it
                        !e.received -> it.copy(sent = it.sent + 1)
                        // A collection the peer pushed into the home `Collections/` (top level): AppState lists it again.
                        e.module == "collections" && '/' !in e.path && e.path.endsWith(".txt", ignoreCase = true) ->
                            it.copy(received = it.received + 1, collections = it.collections.copy(received = it.collections.received + e.path.dropLast(4)))
                        else -> it.copy(received = it.received + 1, roms = it.roms || e.module == "roms")
                    }
                }
                edit(me) { it.copy(lines = it.lines.plusCapped(e.path)) }
            }
            if (!started) return DeviceSyncPage.Failed(link, DeviceSyncText.NO_SERVER)
            show(me, DeviceSyncPage.Syncing(link, peer.ip, SyncRole.SERVER, null, 0, 0, listOf(DeviceSyncText.WAITING_PEER)))
            platform.keepAlive(true, DeviceSyncText.syncingWith(peer.ip, link))
            // An idle timeout, not a deadline from the start: a phone-to-phone run with ROMs over Wi-Fi can take well over
            // an hour, its daemon logging all the way. Silence (neither a daemon line nor a peer message) ends it: after
            // SERVER_WAIT_MS while a connection is open (a big file logs only once complete), else after PEER_SILENCE_MS,
            // since a peer that vanished without FAIL would hold the daemon, the service and the screen.
            var peerHeard = now()
            var nextReady = now()
            while (true) {
                val quiet = now() - maxOf(peerHeard, serverActivity)
                if (sessions.value.isNotEmpty()) {
                    if (quiet >= SERVER_WAIT_MS) return DeviceSyncPage.Failed(link, DeviceSyncText.SERVER_TIMEOUT)
                } else if (quiet >= PEER_SILENCE_MS) {
                    log("nothing from ${peer.ip} and no connection open: gave up")
                    return DeviceSyncPage.Failed(link, DeviceSyncText.PEER_SILENT)
                }
                if (now() >= nextReady) { sock.send(SyncWire.READY, peer.ip); nextReady = now() + INTERVAL_MS }
                val d = sock.receive(POLL_MS) ?: continue
                if (d.sourceIp == peer.ip) peerHeard = now()
                val m = SyncWire.parse(d.text)
                if (d.sourceIp == peer.ip && m == SyncMessage.Done) break
                if (d.sourceIp == peer.ip && m == SyncMessage.Fail) { log("FAIL from ${peer.ip}"); return DeviceSyncPage.Failed(link, DeviceSyncText.PEER_FAILED) }
                reAck(sock, d, m, ownIp, peer, roms)
            }
        } finally {
            // Even when stopped, failed or the network went (inside startServer too, which tears down what it began):
            // stop the daemon (its last log lines land in the tally).
            withContext(NonCancellable) { platform.stopServer() }
        }
        return tally.value.let { DeviceSyncPage.Done(link, it.received, it.sent) }
    }

    private fun notification(ip: String, link: SyncLink, step: SyncStep, steps: Int, console: String?, done: Int, total: Int) =
        "${DeviceSyncText.syncingWith(ip, link)} · ${DeviceSyncText.step(step, steps, console)} · ${DeviceSyncText.files(done, total)}"

    private companion object {
        /** rsync's exit for an unknown daemon module: a phone serving without ROM or BIOS modules, an older handheld without `[bios]`. */
        const val UNKNOWN_MODULE = 5
        /** rsync's text for that refusal (`@ERROR: Unknown module 'bios'`); exit 5 alone also covers other daemon refusals. */
        const val UNKNOWN_MODULE_TEXT = "Unknown module"
        /** How a daemon starts a refusal line. */
        const val DAEMON_ERROR = "@ERROR:"
        /** A ROM run's output lines kept to tell a full disk from other failures. */
        const val KEPT_LINES = 20
        const val NO_SPACE = "No space left on device"
        const val NO_SUCH_FILE = "No such file or directory"
        const val PARTIAL = 23
        /** How rsync starts its own error lines (its closing summary is `rsync error: …`, which this does not match). */
        const val RSYNC_ERROR = "rsync: "
        const val RA_DIR = ".retroachievements"
        const val HASHES = "hashes.json"
        const val SOURCES = ".sources/"
        /** A `.media` top-level file that is art (`bg.png`). */
        val ART_EXTENSIONS = setOf("png", "jpg", "jpeg")
        const val INTERVAL_MS = 1_000L
        const val POLL_MS = 200L
        const val READY_WAIT_MS = 30_000L
        /** READYs in a row from one device while searching that mean its ACK was lost (one a second: about 5 s). */
        const val MISSED_ACK_READIES = 5
        /**
         * How long a serving phone waits in silence (no daemon log line, no message from the peer) before it gives up
         * (SERVER_TIMEOUT) and stops its daemon. An idle limit: a long transfer that keeps logging never reaches it.
         */
        const val SERVER_WAIT_MS = 3_600_000L
        /** A serving phone's silence limit while no rsync connection is open (between steps, or a peer gone). */
        const val PEER_SILENCE_MS = 300_000L
        /** How long the search runs with no device answering. */
        const val SEARCH_MS = 300_000L
        const val DONE_REPEATS = 5
        const val DONE_GAP_MS = 200L
        const val MAX_LINES = 200
        /** The shortest gap between two progress updates of the notification. */
        const val NOTIFY_MS = 500L
        /** 10.99.0.0/30, the cable's network: never paired with on Wi-Fi. */
        val CABLE_NET = SyncWire.ipValue("10.99.0.0")..SyncWire.ipValue("10.99.0.3")
    }
}
