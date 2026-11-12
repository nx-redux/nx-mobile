package com.nxredux.mobile.netplay.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileOutputStream
import kotlin.coroutines.resume

/** The process's one cable: the link outlives the Netplay sheet and runs under the game until the game screen stops it. */
object UsbCables {
    @Volatile private var cable: AndroidUsbCable? = null
    fun get(context: Context): AndroidUsbCable = cable ?: synchronized(this) { cable ?: AndroidUsbCable(context).also { cable = it } }
}

/**
 * The Android [UsbCable]: finds the other end (an NX Redux handheld's nxlink interface, a phone already in accessory
 * mode, another phone to switch into it, or this phone as that accessory), brings up the VPN for 10.99.0.0/30
 * ([UsbLinkVpnService]) and runs the HELLO and the relay ([UsbLinkEngine]). Android's dialogs (VPN consent, USB
 * permission) restart the wait (30 s, or a game's own start's ten minutes). Once Up, a brief drop (the device detaching and re-attaching) is ridden out: the
 * engine and its transport go, the VPN stays, and the same side looks for the other end again for 28 s; a new engine on
 * the same TUN that says UP in time carries on with the state still Up, anything else is [UsbLinkState.Lost]. Every
 * drop counts in [drops], ridden out or not.
 */
class AndroidUsbCable(context: Context) : UsbCable {
    private val context = context.applicationContext
    private val usb: UsbManager? = this.context.getSystemService(UsbManager::class.java)
    private val connectivity: ConnectivityManager? = this.context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UsbLinkState>(UsbLinkState.Off)
    override val state: StateFlow<UsbLinkState> = _state.asStateFlow()
    private val _drops = MutableStateFlow(0)
    override val drops: StateFlow<Int> = _drops.asStateFlow()

    /** Set by MainActivity: shows Android's VPN consent; its answer comes back through [onConsent]. */
    @Volatile var askConsent: (Intent) -> Unit = {}

    /** The cable's VPN network while up: a USB session's Kotlin sockets bind to it, so they leave from the cable address. */
    @Volatile var network: Network? = null
        private set

    private var job: Job? = null
    /** The running start's wait for the other end: the sheet's [LINK_WAIT_MS], or a game's [UsbSearch.AUTO_WAIT_MS]. */
    @Volatile private var waitMs = LINK_WAIT_MS
    @Volatile private var autoSeenMs = 0L
    /** Bumped by every start ([start], [startAuto]): a newer one makes an older game screen's [stopIf] a no-op. */
    private val generation = UsbCableGeneration()
    @Volatile private var engine: UsbLinkEngine? = null
    @Volatile private var opened: UsbTransport? = null
    /** The USB device [opened] is on (HOST side): a reattach skips it, since Android may list it a moment after it detached. */
    @Volatile private var linkedDevice: String? = null
    /** The VPN fd this run got: closed by [teardown] after the engine stops, as well as by the service's STOP. */
    @Volatile private var tun: ParcelFileDescriptor? = null
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null

    init {
        UsbLinkVpnService.onRevoked = { if (_state.value is UsbLinkState.Up) _state.value = UsbLinkState.Lost }
    }

    override val available: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST) || ourAccessory() != null

    @Synchronized override fun start() = begin(LINK_WAIT_MS)

    /**
     * A game's own start (its DS wireless came on with something on the USB port): only when no link runs and Android's
     * VPN consent is already given, and waiting [UsbSearch.AUTO_WAIT_MS] for the other end. The token to [stopIf] it
     * with, or null when nothing was started (a link that already runs is used as it is, and left to its starter).
     */
    @Synchronized fun startAuto(): Long? {
        val s = _state.value
        if (s is UsbLinkState.Consent || (job?.isActive == true && s !is UsbLinkState.Lost)) return null
        if (runCatching { VpnService.prepare(context) }.getOrNull() != null) return null
        begin(UsbSearch.AUTO_WAIT_MS)
        val token = token()
        autoSeenMs = SystemClock.elapsedRealtime()
        // A game that is killed never stops its cable: it asks every second while it lives ([autoSeen]).
        scope.launch {
            while (true) {
                delay(AUTO_CHECK_MS)
                synchronized(this@AndroidUsbCable) {
                    if (!generation.isCurrent(token) || job == null) return@launch
                    if (SystemClock.elapsedRealtime() - autoSeenMs > AUTO_QUIET_MS) {
                        NxLog.i(TAG, "the game that started the cable is gone: stopping it")
                        stop()
                        return@launch
                    }
                }
            }
        }
        return token
    }

    /** A game asked about the cable: the one that started it is still there. */
    fun autoSeen() { autoSeenMs = SystemClock.elapsedRealtime() }

    /** What the USB port shows of another phone, before any link. */
    fun peer(): UsbPeer {
        val usb = usb ?: return UsbPeer.NONE
        if (!available) return UsbPeer.NONE
        val inputs = inputDeviceIds()
        val seen = ourAccessory() != null || usb.deviceList.values.any {
            (UsbIds.isAccessoryMode(it.vendorId, it.productId) && it.interfaceCount > 0) || aoaCandidate(it, inputs)
        }
        return if (seen) UsbPeer.SEEN else if (usbDevice()) UsbPeer.MAYBE else UsbPeer.NONE
    }

    /** [state] as a game asks about it ([startAuto]). */
    fun autoLink(): UsbAutoLink = when (val s = _state.value) {
        is UsbLinkState.Up -> UsbAutoLink.UP
        UsbLinkState.Consent, UsbLinkState.Permission, is UsbLinkState.Starting -> UsbAutoLink.LINKING
        UsbLinkState.Off, UsbLinkState.Lost -> UsbAutoLink.IDLE
        is UsbLinkState.Failed -> if (s.reason == UsbFail.NO_LINK || s.reason == UsbFail.PEER_HOST) UsbAutoLink.IDLE else UsbAutoLink.REFUSED
    }

    private fun begin(wait: Long) {
        generation.bump()
        if (job?.isActive == true) {
            // A run still looking (or behind a dialog) is kept; a Lost or a stale Up left by an earlier game is not, nor
            // is a game's long wait when the sheet asks.
            val s = _state.value
            if (s !is UsbLinkState.Lost && s !is UsbLinkState.Up && waitMs == wait) return
            stop()
        }
        if (_state.value is UsbLinkState.Consent) return
        if (usb == null || !available) {
            _state.value = UsbLinkState.Failed(UsbFail.UNSUPPORTED)
            return
        }
        val consent = VpnService.prepare(context)
        if (consent != null) {
            _state.value = UsbLinkState.Consent
            askConsent(consent)
            return
        }
        waitMs = wait
        _state.value = UsbLinkState.Starting(handheld = nxlinkDevice() != null)
        job = scope.launch { runLink() }
    }

    /** Android's VPN consent answered (MainActivity's result). */
    fun onConsent(granted: Boolean) {
        synchronized(this) {
            if (_state.value !is UsbLinkState.Consent) return
            if (!granted) {
                _state.value = UsbLinkState.Failed(UsbFail.DENIED)
                return
            }
            _state.value = UsbLinkState.Off
        }
        start()
    }

    @Synchronized override fun stop() {
        job?.cancel()
        job = null
        teardown()
        _state.value = UsbLinkState.Off
    }

    /** This cable's current start, for [stopIf]; read by a USB netplay game screen as it opens. */
    fun token(): Long = generation.current

    /** Stops the cable only when no [start] came after [token] was read (a newer session's cable is left alone). */
    @Synchronized fun stopIf(token: Long) {
        if (generation.isCurrent(token)) stop()
    }

    // --- the link ---

    /** An opened other end; [deadline] is when the search's wait would have run out (a reattach's UP must beat it). */
    private class End(val side: UsbSide, val transport: UsbTransport, val handheld: Boolean, val deadline: Long)

    private suspend fun CoroutineScope.runLink() {
        val wait = waitMs
        val end = findEnd(UsbSearch(null, firstWaitMs = wait)) ?: return
        // Handed over under the cable's monitor: a stop() either ran before (this run closes the transport) or after
        // (its teardown closes [opened]).
        synchronized(this@AndroidUsbCable) {
            if (!isActive) {
                runCatching { end.transport.close() }
                return
            }
            opened = end.transport
        }
        val pfd = establishVpn(end.side)
        // A stop() meanwhile: the fd it never saw is closed here (the transport is in [opened], closed by its teardown).
        if (!isActive) { runCatching { pfd?.close() }; runCatching { end.transport.close() }; return }
        if (pfd == null) {
            teardown()
            post(UsbLinkState.Failed(UsbFail.VPN_BLOCKED, end.handheld))
            return
        }
        val events = Channel<UsbLinkEngine.Event>(Channel.UNLIMITED)
        val e = UsbLinkEngine(end.side, end.transport, SystemClock::elapsedRealtime) { events.trySend(it) }
        // Under the cable's monitor, as the transport: a stop() before this never sees the engine, so it is not started.
        synchronized(this@AndroidUsbCable) {
            if (!isActive) {
                runCatching { pfd.close() }
                runCatching { end.transport.close() }
                return
            }
            tun = pfd
            engine = e
            e.attachTun(FileOutputStream(pfd.fileDescriptor))
            e.start()
            e.startTun(PollingTunInput(pfd.fileDescriptor))
        }
        when (withTimeoutOrNull(wait) { events.receive() }) {
            UsbLinkEngine.Event.UP -> {
                network = awaitVpnNetwork()
                post(UsbLinkState.Up(UsbHello.localIp(end.side), UsbHello.peerIp(end.side)))
            }
            UsbLinkEngine.Event.VERSION -> { teardown(); post(UsbLinkState.Failed(UsbFail.VERSION, end.handheld)); return }
            else -> { teardown(); post(UsbLinkState.Failed(UsbFail.NO_LINK, end.handheld)); return }
        }
        // Up: each DOWN gets one brief-drop reattach on the same side, keeping the VPN ([reattach]); one that does not come
        // back is Lost, which stays until stop() (the run waits for its cancel, so start() still sees it and tears down).
        var current = events
        while (true) {
            while (current.receive() != UsbLinkEngine.Event.DOWN) Unit
            _drops.value++
            // Android took the VPN away meanwhile (onRevoked posted Lost): nothing to carry the link back on.
            if (_state.value is UsbLinkState.Lost) break
            current = reattach(end.side, pfd) ?: break
        }
        post(UsbLinkState.Lost)
        awaitCancellation()
    }

    /**
     * The cable dropped after being Up (a USB detach and re-attach within a few seconds). The engine and its transport
     * go; the TUN, the VPN service, its network callback and [network] stay, so every socket bound to the VPN survives.
     * The same side looks for the other end again for [UsbSearch.REATTACH_MS] (a dialog restarts that wait, the state
     * staying Up), then a new engine on the same TUN must say UP within what is left of it (at least
     * [REATTACH_UP_MIN_MS]). The new events, or null when it did not come back (the caller posts Lost). Phone ↔ phone
     * after a physical replug does not come back: the device phone leaves accessory mode and a reattach makes no AOA switch.
     */
    private suspend fun CoroutineScope.reattach(side: UsbSide, pfd: ParcelFileDescriptor): Channel<UsbLinkEngine.Event>? {
        NxLog.i(TAG, "cable dropped, reconnecting")
        // Under the cable's monitor: a stop() before this already tore everything down; one after finds them cleared.
        synchronized(this@AndroidUsbCable) {
            if (!isActive) return null
            engine?.stop()   // closes its transport; its threads leave within one read / tick / PollingTunInput poll
            engine = null
            opened?.let { runCatching { it.close() } }
            opened = null
        }
        val end = findEnd(UsbSearch(side, lost = linkedDevice)) ?: return null
        val events = Channel<UsbLinkEngine.Event>(Channel.UNLIMITED)
        val e = UsbLinkEngine(end.side, end.transport, SystemClock::elapsedRealtime) { events.trySend(it) }
        // As the first bring-up: a stop() before this never sees the transport or the engine (closed here, not started);
        // one after closes them through [opened] and [engine] in its teardown. The TUN is the run's, closed by teardown.
        synchronized(this@AndroidUsbCable) {
            if (!isActive) {
                runCatching { end.transport.close() }
                return null
            }
            opened = end.transport
            engine = e
            e.attachTun(FileOutputStream(pfd.fileDescriptor))
            e.start()
            e.startTun(PollingTunInput(pfd.fileDescriptor))   // its own input: never shared with the old engine's thread
        }
        // A late find still gets one HELLO cadence plus the handheld's usblink restart.
        val left = maxOf(end.deadline - SystemClock.elapsedRealtime(), REATTACH_UP_MIN_MS)
        val first = withTimeoutOrNull(left) { events.receive() }
        if (first != UsbLinkEngine.Event.UP) {
            // Not back: this relay stops here (the TUN and VPN stay for stop()'s teardown, as for any Lost).
            synchronized(this@AndroidUsbCable) {
                if (isActive) {
                    e.stop()   // closes end.transport
                    if (engine === e) engine = null
                    if (opened === end.transport) opened = null
                }
            }
            return null
        }
        NxLog.i(TAG, "cable back")
        return events
    }

    /**
     * Polls for the other end every [POLL_MS] until one opens; null after [UsbSearch.windowMs] with none, or when Android's
     * permission is refused (the first bring-up posts Failed for both; a reattach posts nothing). Order: this phone as the
     * accessory; a handheld's nxlink; a phone already in accessory mode; else one AOA switch of a phone-like device (never
     * a peripheral). [search] limits a reattach to its own side, with no AOA switch.
     */
    private suspend fun CoroutineScope.findEnd(search: UsbSearch): End? {
        val usb = usb ?: return null
        var deadline = SystemClock.elapsedRealtime() + search.windowMs
        var switched = !search.aoaSwitch
        val tried = HashSet<String>()   // one AOA attempt per device per run (a device that does not speak it is left alone)
        // Android's USB permission dialog: Permission while it is up (the sheet's 30 s wait pauses), then Starting again.
        // A reattach keeps Up showing (the game carries on) and only restarts its own wait.
        suspend fun dialog(request: (PendingIntent) -> Unit): Boolean {
            if (search.posts) post(UsbLinkState.Permission)
            val ok = askPermission(request)
            if (search.posts) post(UsbLinkState.Starting(handheld = nxlinkDevice() != null))
            deadline = SystemClock.elapsedRealtime() + search.windowMs
            return ok
        }
        suspend fun permittedDevice(d: UsbDevice): Boolean = usb.hasPermission(d) || dialog { pi -> usb.requestPermission(d, pi) }
        suspend fun permittedAccessory(a: UsbAccessory): Boolean = usb.hasPermission(a) || dialog { pi -> usb.requestPermission(a, pi) }
        fun found(side: UsbSide, t: UsbTransport, handheld: Boolean) = End(side, t, handheld, deadline)
        // The dropped device ([UsbSearch.lost]) is skipped only until it leaves the list (or [STALE_MS] passes): after a
        // bus reset Android numbers the device that comes back from 1 again, so it can reappear under the same name.
        var stale = search.lost
        val staleUntil = SystemClock.elapsedRealtime() + STALE_MS
        while (isActive) {
            if (search.accessory) ourAccessory()?.let { a ->
                if (!permittedAccessory(a)) return denied(search)
                usb.openAccessory(a)?.let { return found(UsbSide.DEVICE, AccessoryTransport(it), handheld = false) }
            }
            // A dropped device can stay listed for a moment: asking permission for it would show Android's dialog over the game.
            val listed = usb.deviceList.values
            if (stale != null && (listed.none { it.deviceName == stale } || SystemClock.elapsedRealtime() > staleUntil)) stale = null
            val devices = listed.filter { it.deviceName != stale }
            if (search.devices) devices.firstOrNull { UsbIds.nxlinkInterface(it) != null }?.let { d ->
                if (!permittedDevice(d)) return denied(search)
                openTransport(d, UsbIds.nxlinkInterface(d)!!)?.let { linkedDevice = d.deviceName; return found(UsbSide.HOST, it, handheld = true) }
            }
            if (search.devices) devices.firstOrNull { UsbIds.isAccessoryMode(it.vendorId, it.productId) && it.interfaceCount > 0 }?.let { d ->
                if (!permittedDevice(d)) return denied(search)
                openTransport(d, d.getInterface(0))?.let { linkedDevice = d.deviceName; return found(UsbSide.HOST, it, handheld = false) }
            }
            if (!switched) {
                val inputs = inputDeviceIds()
                devices.firstOrNull { it.deviceName !in tried && aoaCandidate(it, inputs) }?.let { d ->
                    tried += d.deviceName
                    if (!permittedDevice(d)) return denied(search)
                    usb.openDevice(d)?.let { c ->
                        switched = UsbIds.startAccessory(c)
                        c.close()
                        if (switched) deadline = SystemClock.elapsedRealtime() + search.windowMs   // it re-enumerates as the accessory
                    }
                }
            }
            if (SystemClock.elapsedRealtime() > deadline) {
                if (search.posts) post(UsbLinkState.Failed(if (usbDevice()) UsbFail.PEER_HOST else UsbFail.NO_LINK, handheld = nxlinkDevice() != null))
                return null
            }
            delay(POLL_MS)
        }
        return null
    }

    /** This phone is the USB device on its port (the other end took the host role): Android's sticky USB_STATE `connected`. */
    private fun usbDevice(): Boolean = runCatching {
        context.registerReceiver(null, IntentFilter(USB_STATE))?.getBooleanExtra("connected", false) == true
    }.getOrDefault(false)

    private fun CoroutineScope.denied(search: UsbSearch): End? {
        if (search.posts) post(UsbLinkState.Failed(UsbFail.DENIED))
        return null
    }

    private fun openTransport(d: UsbDevice, iface: UsbInterface): UsbTransport? {
        val (input, output) = UsbIds.bulkPair(iface) ?: return null
        val c = usb?.openDevice(d) ?: return null
        if (!c.claimInterface(iface, true)) {
            c.close()
            return null
        }
        return DeviceTransport(c, iface, input, output)
    }

    /** The (vendor, product) of every input device Android shows (pads, keyboards); a USB one never gets the AOA switch. */
    private fun inputDeviceIds(): List<Pair<Int, Int>> {
        val im = context.getSystemService(InputManager::class.java) ?: return emptyList()
        return im.inputDeviceIds.toList().mapNotNull { id -> im.getInputDevice(id) }.filter { d -> !d.isVirtual }.map { d -> d.vendorId to d.productId }
    }

    /** A phone-like device the AOA switch may be tried on: never a handheld, a phone already switched, or a peripheral. */
    private fun aoaCandidate(d: UsbDevice, inputs: List<Pair<Int, Int>>): Boolean =
        UsbIds.nxlinkInterface(d) == null && !UsbIds.isAccessoryMode(d.vendorId, d.productId) &&
            !UsbIds.isHandheldAdbGadget(d.vendorId, d.productId) && UsbIds.isAoaCandidate(UsbIds.interfaceClasses(d)) && !UsbIds.isInputDevice(d.vendorId, d.productId, inputs)

    private fun nxlinkDevice(): UsbDevice? = usb?.deviceList?.values?.firstOrNull { UsbIds.nxlinkInterface(it) != null }

    private fun ourAccessory(): UsbAccessory? =
        usb?.accessoryList?.firstOrNull { it.manufacturer == UsbIds.MANUFACTURER && it.model == UsbIds.MODEL }

    /** Android's USB permission dialog: true when granted. */
    private suspend fun askPermission(request: (PendingIntent) -> Unit): Boolean = suspendCancellableCoroutine { cont ->
        val action = "${context.packageName}.USB_PERMISSION"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                runCatching { context.unregisterReceiver(this) }
                if (cont.isActive) cont.resume(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        if (!cont.isActive) return@suspendCancellableCoroutine   // stopped: no dialog after the user left
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        request(PendingIntent.getBroadcast(context, 0, Intent(action).setPackage(context.packageName), flags))
    }

    /** Starts the VPN as [side]'s address; the fd, or null when Android refused it (another VPN app holds the slot). */
    private suspend fun establishVpn(side: UsbSide): ParcelFileDescriptor? = withTimeoutOrNull(VPN_WAIT_MS) {
        suspendCancellableCoroutine { cont ->
            UsbLinkVpnService.handoff = { fd ->
                UsbLinkVpnService.handoff = null
                if (cont.isActive) cont.resume(fd) else runCatching { fd?.close() }
            }
            cont.invokeOnCancellation { UsbLinkVpnService.handoff = null }
            try {
                // Under the cable's monitor: a stop() either ran before (no START at all) or sends its STOP after this START.
                val started = synchronized(this@AndroidUsbCable) {
                    if (cont.isActive) UsbLinkVpnService.start(context, UsbHello.localIp(side))
                    cont.isActive
                }
                if (!started) UsbLinkVpnService.handoff = null
            } catch (e: Exception) {
                NxLog.w(TAG, "vpn service did not start", e)
                UsbLinkVpnService.handoff = null
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** Our VPN as a Network, for binding sockets; null when it does not show within 2 s (sockets then route by table). */
    private suspend fun awaitVpnNetwork(): Network? = withTimeoutOrNull(2_000) {
        suspendCancellableCoroutine { cont ->
            val cm = connectivity
            if (cm == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(n: Network) { if (cont.isActive) cont.resume(n) }
                override fun onLost(n: Network) { if (network == n) network = null }
            }
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            vpnCallback = cb
            cm.registerNetworkCallback(req, cb)
        }
    }

    /** Sets [state] only while this link's job still runs (a stop() meanwhile wins). */
    private fun CoroutineScope.post(s: UsbLinkState) {
        synchronized(this@AndroidUsbCable) { if (isActive) _state.value = s }
    }

    private fun teardown() {
        engine?.stop()
        engine = null
        tun?.let { runCatching { it.close() } }
        tun = null
        opened?.let { runCatching { it.close() } }
        opened = null
        vpnCallback?.let { cb -> runCatching { connectivity?.unregisterNetworkCallback(cb) } }
        vpnCallback = null
        network = null
        UsbLinkVpnService.handoff = null
        UsbLinkVpnService.stop(context)
    }

    private companion object {
        const val TAG = "NxUsbLink"
        const val LINK_WAIT_MS = UsbSearch.LINK_WAIT_MS
        /** A reattach's UP wait after a late find: one HELLO cadence plus the handheld's usblink restart. */
        const val REATTACH_UP_MIN_MS = 1_500L
        const val POLL_MS = 250L
        /** How long a reattach skips the dropped device's name while Android still lists it (removal takes about 1 s). */
        const val STALE_MS = 2_000L
        const val VPN_WAIT_MS = 5_000L
        const val AUTO_CHECK_MS = 2_000L
        /** A game asks every second; this long without is a game that is gone (or frozen in the background). */
        const val AUTO_QUIET_MS = 6_000L
        /** UsbManager's hidden ACTION_USB_STATE, a sticky broadcast. */
        const val USB_STATE = "android.hardware.usb.action.USB_STATE"
    }
}
