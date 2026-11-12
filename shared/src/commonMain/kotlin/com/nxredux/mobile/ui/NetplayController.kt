package com.nxredux.mobile.ui

import com.nxredux.mobile.tryOr
import com.nxredux.mobile.tryOrNull
import com.nxredux.mobile.tryQuietly
import com.nxredux.mobile.netplay.Advert
import com.nxredux.mobile.netplay.CableLinkRefused
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
import com.nxredux.mobile.netplay.wizard.CableLinkOffer
import com.nxredux.mobile.netplay.wizard.ClientOffer
import com.nxredux.mobile.netplay.wizard.ClientOutcome
import com.nxredux.mobile.netplay.wizard.HostOffer
import com.nxredux.mobile.netplay.wizard.HostOutcome
import com.nxredux.mobile.netplay.wizard.Wizard
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CancellationException
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
     * The Connection tabs ([connections], [connection] the picked one: Wi-Fi / USB Cable / Cable Link over Wi-Fi; null
     * when Wi-Fi is the only choice, then no tabs), the Emulator tabs under them ([emulators]: "Auto" then the core names;
     * null for a tag with one netplay core) at [pick], Host Game and Join Game side by side, and with [needWifi] the
     * "Connect to Wi-Fi first" line and a Wi-Fi settings row. Over USB there is no Wi-Fi row; on Cable Link no Emulator
     * tabs (it always runs mGBA). [selected] indexes [rows], the pad's focus: Host and Join share a line. [pad]: the
     * last input was a pad press, so the focus is drawn (a ring); a tap puts it away.
     */
    data class Role(
        val title: String, val emulators: List<String>?, val pick: Int, val selected: Int, val needWifi: Boolean,
        val connection: NetplayLink? = null,
        val connections: List<NetplayLink> = emptyList(),
        val pad: Boolean = true,
    ) : NetplayPage {
        val rows: List<NetplayRoleRow>
            get() = buildList {
                if (connection != null) add(NetplayRoleRow.CONNECTION)
                if (emulators != null && connection != NetplayLink.CABLE_LINK_WIFI) add(NetplayRoleRow.EMULATOR)
                add(NetplayRoleRow.HOST); add(NetplayRoleRow.JOIN)
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

/** The Role page's things the pad can be on; [line]: the line each sits on, top to bottom (Host and Join share one). */
enum class NetplayRoleRow(val line: Int) { CONNECTION(0), EMULATOR(1), HOST(2), JOIN(2), WIFI(3) }

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
    /** The library ROM (a ref value) with this SHA-1, size and folder tag, for Cable Link ([AppState.findLocalRom]). */
    private val findLibraryRom: suspend (hash: String, sizeBytes: Long, tag: String) -> String? = { _, _, _ -> null },
) {
    private val _page = MutableStateFlow<NetplayPage?>(null)
    val page: StateFlow<NetplayPage?> = _page.asStateFlow()

    private var entry: Listed? = null
    /** [entry]'s GBA link mode token, read once when the sheet opens on it; null for a game without a GBA link core. */
    private var wl: String? = null
    private var probeJob: Job? = null
    private var role: NetplayPage.Role? = null          // the Role page as last shown (its pick and row survive Host/Join)
    private var roleAction = NetplayRoleRow.HOST        // Host or Join, whichever the pad was last on: UP / DOWN come back to it
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
     * [entry]'s tag has netplay. An MGBA game without a Wi-Fi link mode needs the cable, which is known only once the
     * sheet has read the game: without one the sheet then says so.
     */
    fun offers(entry: Listed): Boolean = NetplayCores.supported(entry.system.tag)

    /**
     * Opens the sheet on [entry]'s Role page; nothing for a game [offers] refuses. A GBA game's link mode is read first,
     * through the platform (the whole ROM), so its caps, offers and session all use that one value.
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
            val mode = GbaWifiLink.token(tryOrNull { platform.gbaLinkMode(entry.game.fileName, entry.game.ref) })
            if (this@NetplayController.entry !== entry) return@launch
            wl = mode
            if (connections(entry.system.tag).isEmpty()) {
                val name = NetplayCores.name(NetplayCores.defaultCore(entry.system.tag).orEmpty())
                _page.value = NetplayPage.Error(NetplayText.cableOnlyNoUsb(name), null)
                return@launch
            }
            showFirstRole(entry)
        }
    }

    /**
     * The Connection row's choices for [tag]: Wi-Fi (not for an MGBA game without a Wi-Fi mode, which links over the real
     * cable alone), the USB cable where this device has one (not for a PSP game), and Cable Link over Wi-Fi for a GBA game.
     */
    private fun connections(tag: String): List<NetplayLink> = buildList {
        if (!NetplayCores.usbOnly(tag, wl)) add(NetplayLink.WIFI)
        if (platform.usb?.available == true && !NetplayCores.wifiOnly(tag)) add(NetplayLink.USB)
        if (platform.cableLinkAvailable && NetplayCores.cableLinkOffered(tag)) add(NetplayLink.CABLE_LINK_WIFI)
    }

    private fun showFirstRole(entry: Listed) {
        val offered = NetplayCores.offered(entry.system.tag)
        val emulators = if (offered.size > 1) listOf(NetplayText.AUTO) + offered.map(NetplayCores::name) else null
        val choices = connections(entry.system.tag)
        val connection = when {
            choices == listOf(NetplayLink.WIFI) -> null
            platform.lastLink in choices -> platform.lastLink
            else -> choices.firstOrNull()
        }
        val r = NetplayPage.Role(entry.shown, emulators, pick = 0, selected = 0, needWifi = platform.wifi() == null, connection = connection, connections = if (connection != null) choices else emptyList())
        showRole(r.copy(selected = r.rows.indexOf(NetplayRoleRow.HOST)))   // Host Game highlighted, under the tabs
    }

    /** A pad press while the sheet is open: A selects, B goes back or cancels, UP/DOWN move, LEFT/RIGHT change a tab or move between Host and Join. */
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

    /** A tap on a Connection tab. */
    fun pickConnection(link: NetplayLink) {
        val p = _page.value as? NetplayPage.Role ?: return
        setConnection(p.copy(selected = p.rows.indexOf(NetplayRoleRow.CONNECTION).coerceAtLeast(0), pad = false), link)
    }

    /** A tap on an Emulator tab. */
    fun pickEmulator(index: Int) {
        val p = _page.value as? NetplayPage.Role ?: return
        if (index !in p.emulators.orEmpty().indices) return
        showRole(p.copy(pick = index, selected = p.rows.indexOf(NetplayRoleRow.EMULATOR).coerceAtLeast(0), pad = false))
    }

    /** A tap on row [index] of the Role page or the host list: highlights it (a second tap is A). */
    fun select(index: Int) {
        when (val p = _page.value) {
            is NetplayPage.Role -> if (index in p.rows.indices) showRole(p.copy(selected = index, pad = false))
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

    private fun onRole(shown: NetplayPage.Role, button: NxButton) {
        // Any pad press brings the focus ring back, also one that moves nothing.
        val p = if (shown.pad) shown else shown.copy(pad = true).also(::showRole)
        val rows = p.rows
        val row = rows.getOrNull(p.selected)
        when (button) {
            NxButton.UP, NxButton.DOWN -> {
                // By line: Host and Join sit side by side, and the pad comes back to the one it left.
                val lines = rows.map { it.line }.distinct()
                val to = lines[(lines.indexOf(row?.line ?: 0) + (if (button == NxButton.DOWN) 1 else -1) + lines.size) % lines.size]
                val target = if (to == NetplayRoleRow.HOST.line) roleAction else rows.first { it.line == to }
                showRole(p.copy(selected = rows.indexOf(target)))
            }
            NxButton.LEFT, NxButton.RIGHT -> when (row) {
                NetplayRoleRow.EMULATOR -> cycle(p, if (button == NxButton.RIGHT) 1 else -1)
                NetplayRoleRow.CONNECTION -> toggleConnection(p, if (button == NxButton.RIGHT) 1 else -1)
                NetplayRoleRow.HOST -> if (button == NxButton.RIGHT) showRole(p.copy(selected = rows.indexOf(NetplayRoleRow.JOIN)))
                NetplayRoleRow.JOIN -> if (button == NxButton.LEFT) showRole(p.copy(selected = rows.indexOf(NetplayRoleRow.HOST)))
                else -> Unit
            }
            NxButton.A -> when (row) {
                NetplayRoleRow.CONNECTION -> toggleConnection(p, 1)
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

    /** The Connection choice [step] along; remembered at once. The pad stays on the Connection tabs. */
    private fun toggleConnection(p: NetplayPage.Role, step: Int) {
        val choices = entry?.let { connections(it.system.tag) }?.takeIf { it.isNotEmpty() } ?: return
        setConnection(p, choices[(choices.indexOf(p.connection) + step + choices.size) % choices.size])
    }

    private fun setConnection(p: NetplayPage.Role, link: NetplayLink) {
        val choices = entry?.let { connections(it.system.tag) } ?: return
        if (choices.size < 2 || link !in choices) return   // one choice (USB Cable only): nothing to switch to, and not remembered
        platform.lastLink = link
        // The rows change with the connection (the Emulator tabs, the Wi-Fi row): the pad stays on the thing it was on.
        val on = p.rows.getOrNull(p.selected)
        val next = p.copy(connection = link, connections = choices, needWifi = platform.wifi() == null)
        showRole(next.copy(selected = next.rows.indexOf(on).coerceAtLeast(0)))
    }

    private fun showRole(p: NetplayPage.Role) {
        val r = p.copy(selected = p.selected.coerceIn(0, p.rows.size - 1))
        r.rows[r.selected].let { if (it.line == NetplayRoleRow.HOST.line) roleAction = it }
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
        if (p.connection == NetplayLink.USB || p.connection == NetplayLink.CABLE_LINK_WIFI || pick == null || NetplayCores.wifiOk(pick, wl)) return false
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
        val cableLink = p.connection == NetplayLink.CABLE_LINK_WIFI
        val tag = e.system.tag
        val pick = if (cableLink) null else pickedCore(p, NetplayCores.offered(tag))
        if (cableOnlyOverWifi(p, pick)) return
        val wifi = if (usb) null else platform.wifi() ?: run { needWifi(p); return }
        val rom = e.game.fileName
        val game = NetplayWire.gameName(rom)
        val own = if (cableLink) NetplayCores.CABLE_LINK_CORE else pick ?: runCore(tag, usb) ?: return
        val folderCore = folderCore(tag)
        role = p.copy(needWifi = false)
        job = scope.launch {
            val me = coroutineContext.job
            try {
                val cl = if (cableLink) cableLinkOffer(e) ?: return@launch else null
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
                    // A failed or cancelled wizard must still close the peer's socket (it would wait for its timeout).
                    val out = try {
                        withContext(work) {   // staging the saves and the rsync daemon: off the main thread
                            Wizard.host(
                                ch, HostOffer(game, tag, own, pick, usb, wl, cl), saves = { platform.hostSaves(tag, rom, e.game.ref) },
                                onSync = { hostStatus(NetplayText.SENDING) },
                                onTransfer = { t -> hostStatus(NetplayText.transfer(t.sending, t.done, t.total)) },
                            )
                        }
                    } finally {
                        ch.close()
                        if (channel === ch) channel = null
                    }
                    when (out) {
                        is HostOutcome.Started -> {
                            stopHosting()
                            _page.value = NetplayPage.Progress(NetplayText.TITLE, NetplayText.STARTING)
                            val session = NetplaySession(NetplayRole.HOST, out.peer, out.core, folderCore, out.emptySaves, overUsb = link != null, mixed = out.mixed, peerWl = out.peerWl)
                            finish(e, out.cableLink?.let(session::withCableLink) ?: session)
                            return@launch
                        }
                        is HostOutcome.Turned -> {
                            // The host keeps waiting after any refusal; some say why for a moment first.
                            turnedAwayText(out, usb)?.let { hostStatus(it); delay(NOTICE_MS) }
                            hostStatus(NetplayText.WAITING_PLAYER)
                        }
                    }
                }
            } finally {
                if (job === me) stopHosting()   // B or close already stopped it, and a newer host may be running
            }
        }
    }

    private fun turnedAwayText(out: HostOutcome.Turned, usb: Boolean): String? = if (out.noWifiModes) NetplayText.NO_WIFI_MODES else when (out.reason) {
        RejectReason.SYNC.wire -> NetplayText.SAVE_TRANSFER_FAILED
        RejectReason.CABLE_LINK.wire -> NetplayText.CABLE_LINK_NOT_PICKED
        Wizard.GAME_COPY_FAILED -> NetplayText.GAME_TRANSFER_FAILED
        RejectReason.CORE.wire -> out.joinerCore?.let { NetplayText.coreTurnedAway(NetplayCores.name(it)) }
        // As the handheld host: one line while Wi-Fi keeps waiting for others, the full reason for the cable's one peer.
        RejectReason.LINK.wire -> if (!usb) NetplayText.LINK_TURNED_AWAY else NetplayText.linkModes(theirs = out.joinerWl, ours = out.ownWl)
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
                // Cable Link pairs different games too (Ruby with Sapphire), as two GBAs on a real cable: no "Join anyway?".
                if (role?.connection == NetplayLink.CABLE_LINK_WIFI) connect(host, any = true)
                else if (NetplayWire.equivalent(host.game, own)) connect(host, any = false)
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
        val cableLink = !usb && p.connection == NetplayLink.CABLE_LINK_WIFI
        val pick = if (cableLink) null else pickedCore(p, NetplayCores.offered(tag))
        val first = if (cableLink) NetplayCores.CABLE_LINK_CORE else pick ?: runCore(tag, usb) ?: return
        val folderCore = folderCore(tag)
        val cl = if (cableLink) cableLinkOffer(e, errorBack()) ?: return else null
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
                        ch, ClientOffer(game, any, tag, core, pick, usb, mayRetry && cl == null, wl, cl), platform.clientSink(rom),
                        onSync = { joinProgress(NetplayText.RECEIVING) },
                        onWaiting = { n -> joinProgress(NetplayText.youArePlayer(n)) },
                        onTransfer = { t -> joinProgress(NetplayText.transfer(t.sending, t.done, t.total)) },
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
                    val session = NetplaySession(NetplayRole.CLIENT, host.ip, out.core, folderCore, overUsb = link == NetplayLink.USB, mixed = out.mixed, peerWl = out.peerWl)
                    finish(e, out.cableLink?.let(session::withCableLink) ?: session)
                    return
                }
            }
        }
    }

    /**
     * This side's Cable Link offer for [e], read through the platform off the main thread; null once an error page (back
     * to [back]) says why it can't be offered.
     */
    private suspend fun cableLinkOffer(e: Listed, back: NetplayPage? = role): CableLinkOffer? {
        val tag = e.system.tag
        return try {
            withContext(work) { platform.cableLinkOffer(tag, e.game.fileName, e.game.ref) { hash, size -> findLibraryRom(hash, size, tag) } }
        } catch (c: CancellationException) {
            throw c
        } catch (r: CableLinkRefused) {
            _page.value = NetplayPage.Error(r.text, back)
            null
        } catch (x: Exception) {
            _page.value = NetplayPage.Error(NetplayText.CABLE_LINK_READ_FAILED, back)
            null
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
