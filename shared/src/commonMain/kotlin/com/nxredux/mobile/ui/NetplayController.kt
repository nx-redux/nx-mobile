package com.nxredux.mobile.ui

import com.nxredux.mobile.tryOr
import com.nxredux.mobile.tryOrNull
import com.nxredux.mobile.tryQuietly
import com.nxredux.mobile.netplay.Advert
import com.nxredux.mobile.netplay.FoundHost
import com.nxredux.mobile.netplay.GbaWifiLink
import com.nxredux.mobile.netplay.NetplayChannel
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.netplay.NetplayLinkKind
import com.nxredux.mobile.netplay.NetplayPlatform
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.NetplayWire
import com.nxredux.mobile.netplay.RejectReason
import com.nxredux.mobile.netplay.usb.NetplayLink
import com.nxredux.mobile.netplay.usb.UsbLinkState
import com.nxredux.mobile.netplay.wizard.ClientOffer
import com.nxredux.mobile.netplay.wizard.ClientOutcome
import com.nxredux.mobile.netplay.wizard.HostOffer
import com.nxredux.mobile.netplay.wizard.HostOutcome
import com.nxredux.mobile.netplay.wizard.Wizard
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext

/** One page of the Netplay sheet. */
sealed interface NetplayPage {
    /**
     * Host Game / Join Game, the Emulator row ([emulators]: "Auto (match the other device)" then the core names; null for a
     * tag with one netplay core) at [pick], and with [needWifi] the "Connect to Wi-Fi first" line and a Wi-Fi settings row.
     * [connection]: the Connection row's value (Wi-Fi / USB Cable), first; null when this device cannot offer
     * the cable (no row). Over USB there is no Wi-Fi row. [selected] indexes [rows].
     */
    data class Role(
        val title: String, val emulators: List<String>?, val pick: Int, val selected: Int, val needWifi: Boolean,
        val connection: NetplayLink? = null,
    ) : NetplayPage {
        val rows: List<NetplayRoleRow>
            get() = buildList {
                if (connection != null) add(NetplayRoleRow.CONNECTION)
                add(NetplayRoleRow.HOST); add(NetplayRoleRow.JOIN)
                if (emulators != null) add(NetplayRoleRow.EMULATOR)
                if (needWifi && connection != NetplayLink.USB) add(NetplayRoleRow.WIFI)
            }
    }

    /**
     * Waiting for a player: [address] is "<device name>  <ip>" ("USB" over the cable), [medium] the reminder under it, [status]
     * the small line (waiting, connected, sending, a flash).
     */
    data class Hosting(val title: String, val address: String, val status: String, val medium: String = NetplayText.SAME_WIFI) : NetplayPage

    /** Select Host: the hosts found so far; [empty] is what an empty list says (searching, then no host found). */
    data class Hosts(val hosts: List<FoundHost>, val selected: Int, val empty: String) : NetplayPage

    /** The host runs another game: the handheld's "Join anyway?" prompt (A joins, B goes back to the list). */
    data class JoinAnyway(val host: FoundHost, val text: String) : NetplayPage

    /** A join in progress (connecting, receiving saves, waiting for the host) or the start. */
    data class Progress(val title: String, val line: String) : NetplayPage

    /** A failure; A or B goes [back] (null: closes the sheet). */
    data class Error(val text: String, val back: NetplayPage?) : NetplayPage
}

enum class NetplayRoleRow { CONNECTION, HOST, JOIN, EMULATOR, WIFI }

/**
 * The Netplay sheet over a game list: picks Host or Join (and the emulator), runs discovery and the handheld's
 * control-channel exchange ([Wizard]) through [platform], and on agreement hands the game and its [NetplaySession] to
 * [launch], then closes. Everything it starts (the hosting loop, the advert, discovery, the 120 s timer, the Wi-Fi or
 * cable poll, an open channel, the screen held on, and the cable this sheet started (a launch hands the cable to the game
 * instead)) is stopped by B on that page or by [close]. Its own state lives on [scope]'s (main)
 * thread; the handshakes, with their save staging, rsync and file writes, run on [work], and their progress callbacks only
 * update [page] atomically.
 */
class NetplayController(
    private val scope: CoroutineScope,
    private val platform: NetplayPlatform,
    private val launch: (Listed, NetplaySession) -> Unit,
    private val work: CoroutineContext = Dispatchers.Default,
) {
    private val _page = MutableStateFlow<NetplayPage?>(null)
    val page: StateFlow<NetplayPage?> = _page.asStateFlow()

    private var entry: Listed? = null
    /** [entry]'s GBA link mode token, read once when the sheet opens on it; null for a game without a GBA link core. */
    private var wl: String? = null
    private var probeJob: Job? = null
    private var role: NetplayPage.Role? = null          // the Role page as last shown (its pick and row survive Host/Join)
    private var hosts: List<FoundHost> = emptyList()
    private var hostIndex = 0
    private var searchText = NetplayText.SEARCHING
    private var job: Job? = null                        // the hosting loop, or a join's connect and handshake
    private var browseJob: Job? = null
    private var noHostJob: Job? = null
    private var linkJob: Job? = null                    // polls Wi-Fi (or the cable) while hosting, browsing or joining
    private var advert: Advert? = null
    private var channel: NetplayChannel? = null
    private var screenHeld = false
    /** This sheet started the cable: the Role page, an error and close() stop it; a launch hands it to the game. */
    private var cableOn = false

    /**
     * [entry] can be played over netplay here: its tag has netplay, and MGBA has a cable (most of its games link over the
     * real cable alone; which ones do not is known only once the sheet has read the game).
     */
    fun offers(entry: Listed): Boolean {
        val tag = entry.system.tag
        return NetplayCores.supported(tag) && (tag.trim().uppercase() != "MGBA" || platform.usb?.available == true)
    }

    /**
     * Opens the sheet on [entry]'s Role page; nothing for a game [offers] refuses. A GBA game's link mode is read first,
     * on [work] (the whole ROM), so its caps, offers and session all use that one value.
     */
    fun open(entry: Listed) {
        if (!offers(entry)) return
        close()
        this.entry = entry
        if (NetplayCores.offered(entry.system.tag).none { NetplayCores.link(it) == NetplayLinkKind.NETPACKET }) {
            showFirstRole(entry)
            return
        }
        _page.value = NetplayPage.Progress(entry.shown, NetplayText.READING_GAME)
        probeJob = scope.launch {
            val mode = withContext(work) { GbaWifiLink.token(tryOrNull { platform.gbaLinkMode(entry.game.fileName, entry.game.ref) }) }
            if (this@NetplayController.entry !== entry) return@launch
            wl = mode
            showFirstRole(entry)
        }
    }

    private fun showFirstRole(entry: Listed) {
        val offered = NetplayCores.offered(entry.system.tag)
        val emulators = if (offered.size > 1) listOf(NetplayText.AUTO) + offered.map(NetplayCores::name) else null
        val connection = when {
            NetplayCores.usbOnly(entry.system.tag, wl) -> NetplayLink.USB   // MGBA without a Wi-Fi mode: no Wi-Fi
            platform.usb?.available == true -> platform.lastLink
            else -> null
        }
        val r = NetplayPage.Role(entry.shown, emulators, pick = 0, selected = 0, needWifi = platform.wifi() == null, connection = connection)
        showRole(r.copy(selected = r.rows.indexOf(NetplayRoleRow.HOST)))   // Host Game highlighted, below the Connection row
    }

    /** A pad press while the sheet is open: A selects, B goes back or cancels, UP/DOWN move, LEFT/RIGHT cycle the Emulator. */
    fun onButton(button: NxButton) {
        when (val p = _page.value ?: return) {
            is NetplayPage.Role -> onRole(p, button)
            is NetplayPage.Hosting -> if (button == NxButton.B) { stopWork(); backToRole() }
            is NetplayPage.Hosts -> onHosts(p, button)
            is NetplayPage.JoinAnyway -> when (button) {
                NxButton.A -> connect(p.host, any = true)
                NxButton.B -> if (cableOn) backToRole() else showHosts()
                else -> Unit
            }
            is NetplayPage.Progress -> if (button == NxButton.B && probeJob?.isActive == true) close()
            else if (button == NxButton.B && p.line != NetplayText.STARTING) {
                stopWork()
                if (cableOn) backToRole() else showHosts()
            }
            is NetplayPage.Error -> if (button == NxButton.A || button == NxButton.B) when (p.back) {
                is NetplayPage.Role -> backToRole()
                is NetplayPage.Hosts -> showHosts()
                else -> close()
            }
        }
    }

    /** A tap on row [index] of the Role page or the host list: highlights it (a second tap is A). */
    fun select(index: Int) {
        when (val p = _page.value) {
            is NetplayPage.Role -> if (index in p.rows.indices) showRole(p.copy(selected = index))
            is NetplayPage.Hosts -> if (index in p.hosts.indices) { hostIndex = index; showHosts() }
            else -> Unit
        }
    }

    /** Closes the sheet and stops everything it started. */
    fun close() {
        probeJob?.cancel()
        probeJob = null
        stopWork()
        stopBrowse()
        stopLinkWatch()
        stopCable()
        entry = null
        wl = null
        role = null
        hosts = emptyList()
        _page.value = null
    }

    // --- Role ---

    private fun onRole(p: NetplayPage.Role, button: NxButton) {
        val rows = p.rows
        val row = rows.getOrNull(p.selected)
        when (button) {
            NxButton.UP -> showRole(p.copy(selected = (p.selected - 1 + rows.size) % rows.size))
            NxButton.DOWN -> showRole(p.copy(selected = (p.selected + 1) % rows.size))
            NxButton.LEFT, NxButton.RIGHT -> when (row) {
                NetplayRoleRow.EMULATOR -> cycle(p, if (button == NxButton.RIGHT) 1 else -1)
                NetplayRoleRow.CONNECTION -> toggleConnection(p)
                else -> Unit
            }
            NxButton.A -> when (row) {
                NetplayRoleRow.CONNECTION -> toggleConnection(p)
                NetplayRoleRow.HOST -> host(p)
                NetplayRoleRow.JOIN -> join(p)
                NetplayRoleRow.EMULATOR -> cycle(p, 1)
                NetplayRoleRow.WIFI -> platform.openWifiSettings()
                null -> Unit
            }
            NxButton.B -> close()
            else -> Unit
        }
    }

    private fun cycle(p: NetplayPage.Role, step: Int) {
        val n = p.emulators?.size ?: return
        showRole(p.copy(pick = (p.pick + step + n) % n))
    }

    /** Wi-Fi <-> USB Cable; remembered at once. The highlight stays on the Connection row. */
    private fun toggleConnection(p: NetplayPage.Role) {
        if (entry?.let { NetplayCores.usbOnly(it.system.tag, wl) } == true) return   // USB Cable only, and not remembered
        val next = if (p.connection == NetplayLink.USB) NetplayLink.WIFI else NetplayLink.USB
        platform.lastLink = next
        showRole(p.copy(connection = next, needWifi = platform.wifi() == null))
    }

    private fun showRole(p: NetplayPage.Role) {
        val r = p.copy(selected = p.selected.coerceIn(0, p.rows.size - 1))
        role = r
        _page.value = r
    }

    /** Back on the Role page (the cable this sheet started stops), with Wi-Fi asked again (the user may have joined a network meanwhile). */
    private fun backToRole() {
        stopCable()
        val r = role ?: return close()
        showRole(r.copy(needWifi = platform.wifi() == null))
    }

    /** The core the Emulator row names, or null for Auto (and for a tag with one netplay core). */
    private fun pickedCore(p: NetplayPage.Role, offered: List<String>): String? =
        if (p.emulators == null || p.pick == 0) null else offered.getOrNull(p.pick - 1)

    /**
     * The core Auto runs before any adoption: the tag's default, unless that links this game over the cable alone and this
     * is not the cable, then the first offered core that links without it (MGBA without a Wi-Fi mode never gets here
     * over Wi-Fi: [NetplayCores.usbOnly]).
     */
    private fun runCore(tag: String, usb: Boolean): String? = NetplayCores.offered(tag, usb, wl).firstOrNull()

    /** Host or Join over Wi-Fi with the Emulator row pinned to a core that needs the cable for this game: an error page back to the Role page. */
    private fun cableOnlyOverWifi(p: NetplayPage.Role, pick: String?): Boolean {
        if (p.connection == NetplayLink.USB || pick == null || NetplayCores.wifiOk(pick, wl)) return false
        role = p
        _page.value = NetplayPage.Error(NetplayText.cableOnly(NetplayCores.name(pick)), role)
        return true
    }

    /** The folder's own (single-player) core: its saves are the real ones, whatever core the session runs. */
    private fun folderCore(tag: String): String = CoreRegistry.forTag(tag)?.id ?: NetplayCores.defaultCore(tag).orEmpty()

    // --- Host ---

    private fun host(p: NetplayPage.Role) {
        if (job?.isActive == true) return
        val e = entry ?: return
        val usb = p.connection == NetplayLink.USB
        val tag = e.system.tag
        val pick = pickedCore(p, NetplayCores.offered(tag))
        if (cableOnlyOverWifi(p, pick)) return
        val wifi = if (usb) null else platform.wifi() ?: run { needWifi(p); return }
        val rom = e.game.fileName
        val game = NetplayWire.gameName(rom)
        val own = pick ?: runCore(tag, usb) ?: return
        val folderCore = folderCore(tag)
        role = p.copy(needWifi = false)
        job = scope.launch {
            val me = coroutineContext.job
            try {
                val link = if (usb) bringUpCable(p.title) ?: return@launch else null
                _page.value = if (link != null) {
                    NetplayPage.Hosting(p.title, NetplayText.USB_ADDRESS, NetplayText.WAITING_PLAYER, NetplayText.USB_CONNECTED)
                } else {
                    NetplayPage.Hosting(p.title, "${wifi?.deviceName}  ${wifi?.ip}", NetplayText.WAITING_PLAYER)
                }
                holdScreen(true)
                // A cable has no broadcast: the host's discovery goes to the peer alone (the handheld client peeks for it).
                advert = if (link != null) platform.advertiseTo(game, link.peerIp) else platform.advertise(game)
                watchLink()
                while (true) {
                    val ch = tryOr({
                        stopHosting()
                        _page.value = NetplayPage.Error(NetplayText.HOST_FAILED, role)
                        return@launch
                    }) { platform.accept() }
                    if (link != null && ch.remoteAddress != link.peerIp) {   // over the cable, only the cable's peer
                        ch.close()
                        continue
                    }
                    channel = ch
                    hostStatus(NetplayText.PLAYER_CONNECTED)
                    val out = withContext(work) {   // staging the saves and the rsync daemon: off the main thread
                        Wizard.host(ch, HostOffer(game, tag, own, pick, usb, wl), saves = { platform.hostSaves(tag, rom, e.game.ref) }, onSync = { hostStatus(NetplayText.SENDING) })
                    }
                    ch.close()
                    if (channel === ch) channel = null
                    when (out) {
                        is HostOutcome.Started -> {
                            stopHosting()
                            _page.value = NetplayPage.Progress(NetplayText.TITLE, NetplayText.STARTING)
                            finish(e, NetplaySession(NetplayRole.HOST, out.peer, out.core, folderCore, out.emptySaves, overUsb = link != null, mixed = out.mixed, peerWl = out.peerWl))
                            return@launch
                        }
                        is HostOutcome.Turned -> {
                            // The host keeps waiting after any refusal; some say why for a moment first.
                            turnedAwayText(out)?.let { hostStatus(it); delay(NOTICE_MS) }
                            hostStatus(NetplayText.WAITING_PLAYER)
                        }
                    }
                }
            } finally {
                if (job === me) stopHosting()   // B or close already stopped it, and a newer host may be running
            }
        }
    }

    private fun turnedAwayText(out: HostOutcome.Turned): String? = when (out.reason) {
        RejectReason.SYNC.wire -> NetplayText.SAVE_TRANSFER_FAILED
        RejectReason.CORE.wire -> out.joinerCore?.let { NetplayText.coreTurnedAway(NetplayCores.name(it)) }
        else -> null
    }

    /** Called from the handshake's thread too: an atomic update that only touches a Hosting page. */
    private fun hostStatus(text: String) {
        _page.update { if (it is NetplayPage.Hosting) it.copy(status = text) else it }
    }

    /** Stops advertising and lets the screen sleep again; idempotent. */
    private fun stopHosting() {
        advert?.stop()
        advert = null
        holdScreen(false)
    }

    private fun holdScreen(on: Boolean) {
        if (screenHeld == on) return
        screenHeld = on
        platform.keepScreenOn(on)
    }

    // --- Join ---

    private fun join(p: NetplayPage.Role) {
        if (job?.isActive == true) return
        val tag = entry?.system?.tag ?: return
        if (cableOnlyOverWifi(p, pickedCore(p, NetplayCores.offered(tag)))) return
        if (p.connection == NetplayLink.USB) { joinUsb(p); return }
        if (platform.wifi() == null) { needWifi(p); return }
        role = p.copy(needWifi = false)
        stopBrowse()
        hosts = emptyList()
        hostIndex = 0
        searchText = NetplayText.SEARCHING
        showHosts()
        browseJob = scope.launch {
            // Discovery that cannot listen leaves the list as it is; the timer then says no host was found.
            tryQuietly {
                platform.browse().collect { h ->
                    val at = hosts.indexOfFirst { it.ip == h.ip }
                    hosts = if (at >= 0) hosts.toMutableList().also { it[at] = h } else hosts + h
                    if (_page.value is NetplayPage.Hosts) showHosts()
                }
            }
        }
        noHostJob = scope.launch {
            delay(NO_HOST_MS)
            searchText = NetplayText.NO_HOST
            if (_page.value is NetplayPage.Hosts) showHosts()
        }
        watchLink()
    }

    /**
     * Join over the cable: no host list. A [USB_PEEK_MS] listen for the host's unicast discovery gives its
     * title for the "Join anyway?" check; a host that sends none is joined as running this game.
     */
    private fun joinUsb(p: NetplayPage.Role) {
        val e = entry ?: return
        role = p.copy(needWifi = false)
        val own = NetplayWire.gameName(e.game.fileName)
        job = scope.launch {
            val link = bringUpCable(NetplayText.JOIN) ?: return@launch
            _page.value = NetplayPage.Progress(NetplayText.JOIN, NetplayText.CONNECTING)
            watchLink()
            // Discovery that cannot listen joins without the title check.
            val heard = tryOrNull { withTimeoutOrNull(USB_PEEK_MS) { platform.browse().first { it.ip == link.peerIp } } }
            val host = heard ?: FoundHost(link.peerIp, own)
            if (NetplayWire.equivalent(host.game, own)) handshake(host, any = false)
            else _page.value = NetplayPage.JoinAnyway(host, NetplayText.joinAnyway(host.game, own))
        }
    }

    private fun hostsPage(): NetplayPage.Hosts =
        NetplayPage.Hosts(hosts, hostIndex.coerceIn(0, maxOf(0, hosts.size - 1)), searchText)

    private fun showHosts() { _page.value = hostsPage() }

    private fun onHosts(p: NetplayPage.Hosts, button: NxButton) {
        val n = p.hosts.size
        when (button) {
            NxButton.UP -> if (n > 0) { hostIndex = (p.selected - 1 + n) % n; showHosts() }
            NxButton.DOWN -> if (n > 0) { hostIndex = (p.selected + 1) % n; showHosts() }
            NxButton.A -> {
                val host = p.hosts.getOrNull(p.selected) ?: return
                hostIndex = p.selected
                val own = NetplayWire.gameName(entry?.game?.fileName ?: return)
                if (NetplayWire.equivalent(host.game, own)) connect(host, any = false)
                else _page.value = NetplayPage.JoinAnyway(host, NetplayText.joinAnyway(host.game, own))
            }
            NxButton.B -> { stopBrowse(); backToRole() }
            else -> Unit
        }
    }

    /**
     * Joins [host]: up to [CONNECT_ATTEMPTS] connects, then the handshake. A `REJECT core-<c>` the tag offers, on Auto,
     * reconnects once with `c`.
     */
    private fun connect(host: FoundHost, any: Boolean) {
        if (job?.isActive == true) return
        _page.value = NetplayPage.Progress(NetplayText.JOIN, NetplayText.CONNECTING)
        watchLink()   // usually already running for the discovery; not when the discovery could not listen
        job = scope.launch { handshake(host, any) }
    }

    /** [connect]'s work, inside the running [job] (the cable join calls it directly). Errors go back to the host list, or the Role page over the cable. */
    private suspend fun handshake(host: FoundHost, any: Boolean) {
        val e = entry ?: return
        val p = role ?: return
        val tag = e.system.tag
        val rom = e.game.fileName
        val game = NetplayWire.gameName(rom)
        val link = if (cableOn) NetplayLink.USB else NetplayLink.WIFI
        val usb = link == NetplayLink.USB
        val pick = pickedCore(p, NetplayCores.offered(tag))
        val first = pick ?: runCore(tag, usb) ?: return
        val folderCore = folderCore(tag)
        var core = first
        var mayRetry = true
        while (true) {
            val ch = connectWithRetries(host.ip, link) ?: run {
                _page.value = NetplayPage.Error(NetplayText.CONNECT_FAILED, errorBack())
                return
            }
            channel = ch
            val out = try {
                withContext(work) {   // the save sink runs rsync and writes files: off the main thread
                    Wizard.client(
                        ch, ClientOffer(game, any, tag, core, pick, usb, mayRetry, wl), platform.clientSink(rom),
                        onSync = { joinProgress(NetplayText.RECEIVING) },
                        onWaiting = { n -> joinProgress(NetplayText.youArePlayer(n)) },
                    )
                }
            } finally {
                ch.close()
                if (channel === ch) channel = null
            }
            when (out) {
                is ClientOutcome.Retry -> {
                    core = out.core
                    mayRetry = false
                    joinProgress(NetplayText.CONNECTING)
                }
                is ClientOutcome.Refused -> {
                    _page.value = NetplayPage.Error(out.message, errorBack())
                    return
                }
                is ClientOutcome.Started -> {
                    _page.value = NetplayPage.Progress(NetplayText.JOIN, NetplayText.STARTING)
                    // A joiner never declines offered saves (no rsync: the driver refuses with COPY_FAILED), so it
                    // always plays on the copy it received, empty when the host sent none; only a host has emptySaves.
                    finish(e, NetplaySession(NetplayRole.CLIENT, host.ip, out.core, folderCore, overUsb = link == NetplayLink.USB, mixed = out.mixed, peerWl = out.peerWl))
                    return
                }
            }
        }
    }

    /** Where a join's error page goes back to: the host list, or the Role page over the cable. */
    private fun errorBack(): NetplayPage? = if (cableOn) role else hostsPage()

    /** The handheld's connect: [CONNECT_ATTEMPTS] tries of 5 s each, [CONNECT_RETRY_MS] apart; null when none got through. */
    private suspend fun connectWithRetries(ip: String, link: NetplayLink): NetplayChannel? {
        repeat(CONNECT_ATTEMPTS) { i ->
            if (i > 0) delay(CONNECT_RETRY_MS)
            tryQuietly { return platform.connect(ip, link) }   // refused or timed out: try again
        }
        return null
    }

    /** Called from the handshake's thread: an atomic update that only replaces a Progress page (B may have left it). */
    private fun joinProgress(line: String) {
        _page.update { if (it is NetplayPage.Progress) NetplayPage.Progress(NetplayText.JOIN, line) else it }
    }

    // --- USB cable ---

    /**
     * Starts the cable and waits for its link. Android's dialogs (VPN consent, USB permission) are waited out with no
     * limit; after each one the link has [USB_LINK_MS] again. The link's Up, or null once a failure
     * page shows (the cable is stopped by then).
     */
    private suspend fun bringUpCable(title: String): UsbLinkState.Up? {
        val cable = platform.usb
        if (cable == null || !cable.available) {
            _page.value = NetplayPage.Error(NetplayText.USB_UNAVAILABLE, role)
            return null
        }
        val starting = NetplayPage.Progress(title, NetplayText.USB_CONNECT + "\n\n" + NetplayText.USB_STARTING)
        _page.value = starting
        cableOn = true
        cable.start()
        fun dialog(s: UsbLinkState) = s is UsbLinkState.Consent || s is UsbLinkState.Permission
        var end: UsbLinkState?
        while (true) {
            _page.value = if (cable.state.value is UsbLinkState.Consent) NetplayPage.Progress(title, NetplayText.USB_VPN_NOTE) else starting
            cable.state.first { !dialog(it) }
            _page.value = starting
            end = withTimeoutOrNull(USB_LINK_MS) {
                cable.state.first { it is UsbLinkState.Up || it is UsbLinkState.Failed || it is UsbLinkState.Lost || it is UsbLinkState.Off || dialog(it) }
            }
            if (end == null || !dialog(end)) break   // a dialog came up: wait it out, then the USB_LINK_MS wait starts again
        }
        if (end is UsbLinkState.Up) return end
        val text = when (end) {
            is UsbLinkState.Failed -> NetplayText.usbFail(end)
            UsbLinkState.Lost -> NetplayText.USB_LOST
            else -> if ((cable.state.value as? UsbLinkState.Starting)?.handheld == true) NetplayText.USB_NO_LINK_HANDHELD else NetplayText.USB_NO_LINK
        }
        stopCable()
        _page.value = NetplayPage.Error(text, role)
        return null
    }

    /** Stops the cable this sheet started; idempotent. */
    private fun stopCable() {
        if (!cableOn) return
        cableOn = false
        platform.usb?.stop()
    }

    // --- Shared ---

    /**
     * "Wi-Fi lost before start" and "USB cable disconnected.": every [WIFI_POLL_MS] while hosting,
     * browsing or joining, on this controller's own thread. Losing Wi-Fi (or, over the cable, the link) stops all of it
     * and says so, back to the Role page. The poll ends by itself once none of them runs. Over the cable it starts only once
     * the link is up.
     */
    private fun watchLink() {
        if (linkJob?.isActive == true) return
        linkJob = scope.launch {
            while (true) {
                delay(WIFI_POLL_MS)
                if (job?.isActive != true && browseJob?.isActive != true) break
                val usb = cableOn
                val lost = if (usb) platform.usb?.state?.value !is UsbLinkState.Up else platform.wifi() == null
                if (lost) {
                    linkJob = null   // this coroutine ends here; stopping the rest must not cancel it mid-way
                    stopWork()
                    stopBrowse()
                    if (usb) stopCable() else role = role?.copy(needWifi = true)
                    _page.value = NetplayPage.Error(if (usb) NetplayText.USB_LOST else NetplayText.WIFI_LOST, role)
                    break
                }
            }
        }
    }

    private fun stopLinkWatch() {
        linkJob?.cancel()
        linkJob = null
    }

    private fun needWifi(p: NetplayPage.Role) {
        role = p.copy(needWifi = true)
        _page.value = NetplayPage.Error(NetplayText.NEED_WIFI, role)
    }

    /** Both sides agreed: the game launches and the sheet closes (the handshake's channel is already closed). A cable stays up for the game. */
    private fun finish(e: Listed, session: NetplaySession) {
        cableOn = false   // the game screen stops it when its session ends (NetplaySession.overUsb)
        launch(e, session)
        close()
    }

    /** Cancels the hosting loop or a join in flight, closes its channel and stops hosting; idempotent. */
    private fun stopWork() {
        job?.cancel()
        job = null
        channel?.close()
        channel = null
        stopHosting()
    }

    private fun stopBrowse() {
        browseJob?.cancel()
        browseJob = null
        noHostJob?.cancel()
        noHostJob = null
    }

    companion object {
        const val NO_HOST_MS = 120_000L
        /** A refusal's flash on the waiting page (the handheld's WIZ_NET_NOTICE_MS). */
        const val NOTICE_MS = 1_500L
        const val WIFI_POLL_MS = 1_000L
        const val CONNECT_ATTEMPTS = 3
        const val CONNECT_RETRY_MS = 1_500L
        /** The cable's link wait once Android's dialogs are answered. */
        const val USB_LINK_MS = 35_000L   // past the cable's own 30 s search, so its Failed (with its reason) comes first
        /** A USB joiner listens this long for the host's unicast discovery. */
        const val USB_PEEK_MS = 2_500L
    }
}
