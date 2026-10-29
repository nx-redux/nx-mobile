package com.nxredux.mobile.netplay

import com.nxredux.mobile.settings.Prefs
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.netplay.usb.NetplayLink
import com.nxredux.mobile.netplay.usb.UsbCable
import com.nxredux.mobile.netplay.usb.UsbCables
import com.nxredux.mobile.netplay.wizard.HostSaves
import com.nxredux.mobile.netplay.wizard.SaveSink
import com.nxredux.mobile.runner.Archives
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.NaomiSetsAsset
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.findSave
import com.nxredux.mobile.storage.readSaveDecoded
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The Android [NetplayPlatform]: Wi-Fi state from ConnectivityManager, discovery over UDP broadcast (MulticastLock
 * held while browsing), the control channel over TCP, and the save files under [sdRoot] (the data root, asked per use).
 * Every socket call runs off the main thread.
 */
class AndroidNetplay(context: Context, private val sdRoot: () -> File) : NetplayPlatform {
    private val context = context.applicationContext
    private val connectivity = this.context.getSystemService(ConnectivityManager::class.java)
    private val prefs = Prefs.open(this.context, "netplay")

    override var lastLink: NetplayLink
        get() = if (prefs.getString(KEY_LINK, null) == NetplayLink.USB.name) NetplayLink.USB else NetplayLink.WIFI
        set(value) { prefs.edit().putString(KEY_LINK, value.name).apply() }

    override val usb: UsbCable get() = UsbCables.get(context)

    /** Set by MainActivity: toggles FLAG_KEEP_SCREEN_ON on its window (false restores the menu's own rule). */
    @Volatile var screenOn: (Boolean) -> Unit = {}

    /** The saves staged for the current (or last) joiner, closed before the next joiner's are staged. */
    private val servingLock = Any()
    private var serving: RsyncHostSaves? = null
    /** Bumped by each [hostSaves] call (under [servingLock]); names its staging `serve/<n>`, and a superseded call stops. */
    private var hostGeneration = 0L

    override fun wifi(): WifiInfo? {
        val ip = WifiNetworks.address(connectivity)?.address?.hostAddress ?: return null
        val name = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        return WifiInfo(ip, name?.takeIf { it.isNotBlank() } ?: Build.MODEL)
    }

    override fun openWifiSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun advertise(game: String): Advert {
        val packet = NetplayWire.encodeDiscovery(game)
        val running = AtomicBoolean(true)
        val worker = thread(name = "nx-netplay-advert", isDaemon = true) {
            val socket = try {
                DatagramSocket().apply { broadcast = true }
            } catch (e: IOException) {
                NxLog.w(TAG, "advert socket failed: ${e.message}")
                return@thread
            }
            var bound: Network? = null
            try {
                while (running.get()) {
                    val network = WifiNetworks.network(connectivity)
                    if (network != null && network != bound) {
                        runCatching { network.bindSocket(socket) }
                        bound = network
                    }
                    val target = WifiNetworks.broadcast(WifiNetworks.address(connectivity, network)) ?: InetAddress.getByName("255.255.255.255")
                    try {
                        socket.send(DatagramPacket(packet, packet.size, target, NetplayWire.DISCOVERY_PORT))
                    } catch (e: IOException) {
                        NxLog.w(TAG, "advert send failed: ${e.message}")
                    }
                    try { Thread.sleep(ADVERT_INTERVAL_MS) } catch (_: InterruptedException) { break }
                }
            } finally {
                socket.close()
            }
        }
        return object : Advert {
            override fun stop() {
                if (running.getAndSet(false)) worker.interrupt()
            }
        }
    }

    override fun advertiseTo(game: String, ip: String): Advert {
        val packet = NetplayWire.encodeDiscovery(game)
        val running = AtomicBoolean(true)
        val worker = thread(name = "nx-netplay-advert-to", isDaemon = true) {
            val socket = try {
                DatagramSocket()
            } catch (e: IOException) {
                NxLog.w(TAG, "advert socket failed: ${e.message}")
                return@thread
            }
            UsbCables.get(context).network?.let { n -> runCatching { n.bindSocket(socket) } }   // leave from the cable address
            try {
                val target = InetAddress.getByName(ip)
                while (running.get()) {
                    try {
                        socket.send(DatagramPacket(packet, packet.size, target, NetplayWire.DISCOVERY_PORT))
                    } catch (e: IOException) {
                        NxLog.w(TAG, "advert send failed: ${e.message}")
                    }
                    try { Thread.sleep(ADVERT_INTERVAL_MS) } catch (_: InterruptedException) { break }
                }
            } finally {
                socket.close()
            }
        }
        return object : Advert {
            override fun stop() {
                if (running.getAndSet(false)) worker.interrupt()
            }
        }
    }

    override fun browse(): Flow<FoundHost> = callbackFlow {
        val wifiManager = context.getSystemService(WifiManager::class.java)
        val lock = runCatching { wifiManager?.createMulticastLock("nx-netplay")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
        val socket = try {
            DatagramSocket(null)
        } catch (e: IOException) {
            runCatching { lock?.release() }
            throw e
        }
        try {
            socket.reuseAddress = true
            socket.broadcast = true
            socket.soTimeout = BROWSE_POLL_MS
            socket.bind(InetSocketAddress(NetplayWire.DISCOVERY_PORT))
        } catch (e: IOException) {
            socket.close()   // an unbound socket still holds its fd
            runCatching { lock?.release() }
            throw e
        }
        launch(Dispatchers.IO) {
            val ownIp = wifi()?.ip
            val seen = HashSet<String>()
            val buf = ByteArray(NetplayWire.PACKET_SIZE + 16)   // a longer packet then reads as too long, not as a valid prefix
            while (isActive) {
                val p = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(p)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (e: IOException) {
                    break   // closed by awaitClose, or the socket failed
                }
                val ip = p.address?.hostAddress ?: continue
                val host = NetplayDiscovery.accept(buf, p.length, ip, ownIp, seen) ?: continue
                seen += ip
                send(host)
            }
            close()   // a failed socket ends the flow instead of leaving it silent
        }
        awaitClose {
            socket.close()
            runCatching { lock?.release() }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun accept(): NetplayChannel = withContext(Dispatchers.IO) {
        val server = try {
            ServerSocket()
        } catch (e: IOException) {
            throw NetplayIoException(PORT_BUSY)
        }
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(NetplayWire.CONTROL_PORT), 1)
        } catch (e: IOException) {
            server.close()   // an unbound socket still holds its fd
            throw NetplayIoException(PORT_BUSY)
        }
        try {
            SocketChannel(blockingClosedOnCancel(server) { server.accept() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw NetplayIoException(NetplayIoException.CLOSED)
        } finally {
            server.close()
        }
    }

    override suspend fun connect(ip: String, link: NetplayLink): NetplayChannel = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            when (link) {
                NetplayLink.WIFI -> WifiNetworks.network(connectivity)?.let { n -> runCatching { n.bindSocket(socket) } }
                NetplayLink.USB -> UsbCables.get(context).network?.let { n -> runCatching { n.bindSocket(socket) } }
            }
            blockingClosedOnCancel(socket) { socket.connect(InetSocketAddress(ip, NetplayWire.CONTROL_PORT), CONNECT_TIMEOUT_MS) }
            SocketChannel(socket)
        } catch (e: CancellationException) {
            socket.close()
            throw e
        } catch (e: Exception) {
            socket.close()
            throw NetplayIoException(NetplayIoException.CLOSED)
        }
    }

    override suspend fun hostSaves(tag: String, romFileName: String, content: ContentRef): HostSaves {
        val made = AtomicReference<RsyncHostSaves?>()
        val gen = synchronized(servingLock) { ++hostGeneration }
        /** A newer call started (B, Host again while this one sat in the blocking import): this one touches nothing more. */
        fun superseded() = CancellationException("netplay: a newer joiner's saves are being staged")
        try {
            return withContext(Dispatchers.IO) {
                // The runner names saves after an unpacked archive's inner file under Use extracted file name: the same
                // entry here, from the archive's listing (no unpack). Unlistable: the archive name, and the lookup's
                // cross-setting candidates.
                val naomiSets = if (CoreRegistry.forTag(tag)?.id == "flycast" && Names.isArchive(romFileName)) NaomiSetsAsset.get(context) else NaomiSets.EMPTY
                val inner = NetplaySaves.hostInnerFileName(tag, romFileName, naomiSets) { Archives.entryNames(context, content, romFileName) }
                val paths = NxPaths(tag, "", romFileName, AndroidAppSettings(context).saveNaming, inner)
                // One joiner at a time: the previous joiner's daemon and staging go first (the driver closed them already
                // unless its coroutine is still unwinding; a second close waits for the first), so 18731 never has two daemons.
                synchronized(servingLock) {
                    if (gen != hostGeneration) throw superseded()
                    serving.also { serving = null }
                }?.close()
                val work = File(context.cacheDir, "netplay")
                // Each call stages in its own serve/<n>, so an older instance's close never deletes a newer joiner's files;
                // stale siblings (closed instances, a killed process's) go now.
                val serveRoot = File(work, "serve")
                val stage = File(serveRoot, gen.toString())
                serveRoot.listFiles().orEmpty().filter { it != stage }.forEach { it.deleteRecursively() }
                stage.deleteRecursively()
                try {
                    if (!stage.mkdirs()) throw IOException("cannot create ${stage.name}")
                    // The save under any handheld name, RZIP decoded; nothing staged when none exists or it is malformed.
                    val found = paths.findSave(sdRoot())
                    val bytes = found?.readSaveDecoded()
                    val names = if (found == null || bytes == null) emptyList() else {
                        val wire = if (found.name.endsWith(".srm")) "nxsave.srm" else "nxsave.sav"
                        File(stage, wire).writeBytes(bytes)
                        listOf(wire)
                    }
                    RsyncHostSaves(names, Rsync.find(context), stage, work).also {
                        made.set(it)
                        synchronized(servingLock) {
                            if (gen != hostGeneration) throw superseded()   // the outer catch closes it (made)
                            serving = it
                        }
                    }
                } catch (e: Throwable) {
                    stage.deleteRecursively()   // a half-staged set is never served
                    throw e
                }
            }
        } catch (e: Throwable) {
            // Staged but never handed back (cancelled on the way out of withContext): nobody else would close it.
            made.get()?.let { h ->
                synchronized(servingLock) { if (serving === h) serving = null }
                withContext(NonCancellable + Dispatchers.IO) { h.close() }
            }
            throw e
        }
    }

    override fun clientSink(romFileName: String): SaveSink = NetplaySessionDir.sink(context.filesDir, romFileName, Rsync.find(context))

    override fun keepScreenOn(on: Boolean) = screenOn(on)

    /** Runs blocking [block] (on the caller's IO thread) and closes [resource] if the coroutine is cancelled meanwhile. */
    private suspend fun <T> blockingClosedOnCancel(resource: Closeable, block: () -> T): T =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { runCatching { resource.close() } }
            val result = runCatching(block)
            result.fold(
                onSuccess = { value -> cont.resume(value) { _, v, _ -> (v as? Closeable)?.let { c -> runCatching { c.close() } } } },
                onFailure = { cont.resumeWithException(it) },
            )
        }

    companion object {
        private const val TAG = "NxNetplay"
        private const val ADVERT_INTERVAL_MS = 1000L
        private const val BROWSE_POLL_MS = 1000
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val KEY_LINK = "link"
        /** accept()'s error when the control port cannot be bound (another listener holds it). */
        const val PORT_BUSY = "busy"
    }
}
