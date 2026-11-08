package com.nxredux.mobile.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.nxredux.mobile.library.Collections
import com.nxredux.mobile.library.LibraryIndexCodec
import com.nxredux.mobile.library.SidecarTxt
import com.nxredux.mobile.netplay.Rsync
import com.nxredux.mobile.netplay.WifiNetworks
import com.nxredux.mobile.netplay.usb.UsbCable
import com.nxredux.mobile.netplay.usb.UsbCables
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.nxredux.mobile.storage.AndroidLibraryIndexStore
import com.nxredux.mobile.storage.AndroidRomsSources
import com.nxredux.mobile.storage.AndroidSidecarStore
import com.nxredux.mobile.storage.DataRoot
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.SaveFormat
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Device Sync on Android: sockets on the Wi-Fi or cable network, the bundled rsync. The controller
 * calls [openSocket], [wifiIp], [gameScreenAlive] and [importing] on the main thread; the suspend calls move their I/O
 * to [Dispatchers.IO] themselves. The server's calls come strictly in order (start, stop), so its state needs no
 * lock, only visibility across the IO threads ([Volatile]).
 */
class AndroidDeviceSync(
    context: Context,
    private val gameAlive: () -> Boolean,
    private val importingNow: () -> Boolean,
    private val alive: (Boolean, String?) -> Unit,
) : DeviceSyncPlatform {
    private val app = context.applicationContext
    private val cm: ConnectivityManager? = app.getSystemService(ConnectivityManager::class.java)
    private val settings = AndroidAppSettings(app)
    private val indexStore = AndroidLibraryIndexStore(app)
    /** The data root ([DataRoot.settled]: after a move still running), asked on the IO dispatcher at each use. */
    private val root: File get() = DataRoot.settled(app)
    private val work: File = File(app.filesDir, WORK_DIR)
    // A throwing log callback must not take the process down.
    private val errors = CoroutineExceptionHandler { _, e -> NxLog.e(TAG, "device sync log tail failed", e) }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO + errors)
    @Volatile private var daemon: Process? = null
    @Volatile private var tail: Job? = null
    /** The daemon log's one reader: the [tail] job's while it runs, then [stopServer]'s final pass. */
    @Volatile private var logTail: SyncLogTail? = null
    /** The extra folders (n → path) of the last [ownExtras], which [runRom] resolves its ends with. */
    @Volatile private var extraRoots: Map<Int, String> = emptyMap()

    override val cable: UsbCable get() = UsbCables.get(app)
    override fun wifiIp(): String? = WifiNetworks.address(cm)?.address?.hostAddress?.let(SyncWire::ipv4)
    override fun gameScreenAlive() = gameAlive()
    override fun importing() = importingNow()
    /** A run is under way ([keepAlive] true until its false): it holds off the data move ([DataRoot.useBegin]). */
    @Volatile private var usingData = false
    override fun keepAlive(active: Boolean, text: String?) {
        synchronized(this) {
            if (active && !usingData) { usingData = true; DataRoot.useBegin(app) }
            else if (!active && usingData) { usingData = false; DataRoot.useEnd(app) }
        }
        alive(active, text)
    }
    override fun log(message: String) = NxLog.i(TAG, message)

    /**
     * The UDP socket on [link]'s network. Binding it to the link's `Network` is best effort, as in AndroidNetplay: with
     * no `Network` object, or a refused bind, the socket still opens and routing decides. Wi-Fi with no Wi-Fi at all
     * never gets here (no [wifiIp]: the controller shows NoWifi). Null only when the socket itself fails.
     */
    override fun openSocket(link: SyncLink): SyncSocket? = runCatching {
        val network = if (link == SyncLink.CABLE) UsbCables.get(app).network else WifiNetworks.network(cm)
        // Before the lock and the socket exist, so a throw here leaks neither. The cable is a /30: its broadcast is
        // 10.99.0.3 (the controller unicasts to the peer there anyway).
        val to = if (link == SyncLink.CABLE) InetAddress.getByName(CABLE_BROADCAST)
            else WifiNetworks.broadcast(network?.let { WifiNetworks.address(cm, it) }) ?: InetAddress.getByName("255.255.255.255")
        // Wi-Fi drivers may drop broadcasts in power save without the lock; the cable needs none.
        val lock = if (link == SyncLink.WIFI) {
            app.getSystemService(WifiManager::class.java)?.createMulticastLock("nx-device-sync")?.apply { setReferenceCounted(false); acquire() }
        } else null
        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true; broadcast = true
                try { bind(InetSocketAddress(SyncWire.UDP_PORT)) } catch (e: Exception) { close(); throw e }
            }.also { s ->
                if (network == null) NxLog.w(TAG, "device sync socket on $link: no network object, left to routing")
                else runCatching { network.bindSocket(s) }.onFailure { NxLog.w(TAG, "device sync socket on $link: network bind failed, left to routing", it) }
            }
        } catch (e: Exception) {
            runCatching { lock?.release() }
            throw e
        }
        AndroidSyncSocket(socket, to, onClose = { runCatching { lock?.release() } })
    }.onFailure { NxLog.w(TAG, "device sync socket on $link failed", it) }.getOrNull()

    override suspend fun rsyncAvailable(): Boolean = withContext(Dispatchers.IO) { Rsync.find(app) != null }

    private val homes = AndroidHomeLocator(app)

    /**
     * True when the effective home ([AndroidHomeLocator.current]: the full-access folder, else Android/media) is a path
     * this phone reads natively; a picker (SAF) home from before the upgrade is not.
     */
    override fun romsAvailable(): Boolean = homes.current()?.let(FileRefs::isFile) == true

    /** The effective home folder (callers checked [romsAvailable]). */
    private fun home(): File = File(FileRefs.path(homes.current()?.takeIf(FileRefs::isFile) ?: error("no home folder")))

    /** The home folder's `Roms/` (callers checked [romsAvailable]). */
    private fun homeRoms(): File = File(home(), "Roms")

    /** The app's collections, read and written as the Collections tab does (its app-private copy kept right). */
    private val collections = Collections(AndroidRomsSources.of(app), AndroidSidecarStore(app)) { homes.current() }

    /** The home folder's `Collections/`, created when missing (callers checked [romsAvailable]). */
    private fun homeCollections(): File = File(home(), Collections.DIR).apply { mkdirs() }

    /** The home folder's `Bios/`, created when missing (callers checked [romsAvailable]). */
    private fun homeBios(): File = File(home(), "Bios").apply { mkdirs() }

    override suspend fun checkFileTimes(): Boolean = withContext(Dispatchers.IO) {
        // A home with no Roms/ yet is not a file-time failure.
        val roms = homeRoms()
        roms.mkdirs()
        SyncFileTimes.check(roms)
    }

    override suspend fun checkSaveFileTimes(): Boolean = withContext(Dispatchers.IO) {
        val saves = File(root, "Saves")
        saves.mkdirs()
        SyncFileTimes.check(saves)
    }

    override suspend fun listRemoteRoms(peerIp: String): Pair<Int, List<String>> {
        val rsync = withContext(Dispatchers.IO) { Rsync.find(app) } ?: return NO_RSYNC to emptyList()
        val lines = ArrayList<String>()
        val code = rsync.runStreaming(SyncCommands.listArgs(rsync.binary.absolutePath, peerIp)) { lines += it }
        return code to lines
    }

    override suspend fun listRemoteHashes(peerIp: String): Pair<Int, List<String>> {
        val rsync = withContext(Dispatchers.IO) { Rsync.find(app) } ?: return NO_RSYNC to emptyList()
        val lines = ArrayList<String>()
        val code = rsync.runStreaming(SyncCommands.listHashesArgs(rsync.binary.absolutePath, peerIp)) { lines += it }
        return code to lines
    }

    override suspend fun listRemoteBios(peerIp: String): Pair<Int, List<String>> {
        val rsync = withContext(Dispatchers.IO) { Rsync.find(app) } ?: return NO_RSYNC to emptyList()
        val lines = ArrayList<String>()
        val code = rsync.runStreaming(SyncCommands.listArgs(rsync.binary.absolutePath, peerIp, "bios")) { lines += it }
        return code to lines
    }

    override suspend fun listHomeBios(): List<String> = withContext(Dispatchers.IO) {
        homeBios().listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
    }

    override suspend fun listRemoteFolder(peerIp: String, folder: String): Pair<Int, List<String>> {
        val rsync = withContext(Dispatchers.IO) { Rsync.find(app) } ?: return NO_RSYNC to emptyList()
        val lines = ArrayList<String>()
        val code = rsync.runStreaming(SyncCommands.listFolderArgs(rsync.binary.absolutePath, peerIp, folder)) { lines += it }
        return code to lines
    }

    override suspend fun listHomeRoms(console: String?): List<String> = withContext(Dispatchers.IO) {
        val dir = homeRoms().let { if (console == null) it else File(it, console) }
        dir.list()?.toList().orEmpty()
    }

    override suspend fun ownExtras(): OwnExtras = withContext(Dispatchers.IO) {
        val index = indexStore.read()?.let { LibraryIndexCodec.decode(it) }
        val roots = AndroidSyncExtras.roots(settings.extraFolders)
        val home = homeRoms()
        val own = if (index == null) OwnExtras(ExtrasManifest(), emptyMap()) else {
            val source = AndroidRomsSources.of(app)
            val tags = index.systems.flatMap { s -> s.games.map { it to s.tag } }.toMap(java.util.IdentityHashMap())
            val manifest = SyncExtras.units(index, roots, home.path) { g -> AndroidSyncExtras.companions(app, source, g, tags[g] ?: "") }
            OwnExtras(manifest, roots.associate { it.n to it.path })
        }
        extraRoots = own.roots
        own
    }

    override suspend fun fetchRemoteText(peerIp: String, module: String, path: String): RemoteText {
        val rsync = withContext(Dispatchers.IO) { Rsync.find(app) } ?: return RemoteText.Failed(NO_RSYNC)
        val file = withContext(Dispatchers.IO) { work.mkdirs(); File(work, FETCH_FILE).apply { delete() } }
        val lines = ArrayList<String>()
        val code = rsync.runStreaming(SyncCommands.fetchArgs(rsync.binary.absolutePath, peerIp, module, path, file.absolutePath)) { lines += it }
        return withContext(Dispatchers.IO) {
            try {
                when {
                    code == 0 -> RemoteText.Found(file.readText())
                    code == 23 && lines.any { "No such file" in it || "does not exist" in it } -> RemoteText.Absent
                    else -> RemoteText.Failed(code)
                }
            } catch (e: java.io.IOException) {
                NxLog.w(TAG, "device sync fetch of $path unreadable", e)
                RemoteText.Failed(code.takeIf { it != 0 } ?: 1)
            } finally {
                file.delete()
            }
        }
    }

    override suspend fun pushRemoteText(peerIp: String, module: String, remoteDir: String, name: String, text: String): Int {
        val rsync = withContext(Dispatchers.IO) { Rsync.find(app) } ?: return NO_RSYNC
        val file = try {
            withContext(Dispatchers.IO) { File(work, PUSH_DIR).apply { mkdirs() }.let { File(it, name).apply { writeText(text) } } }
        } catch (e: java.io.IOException) {
            NxLog.w(TAG, "device sync push of $name not written", e)
            return 1
        }
        return rsync.runStreaming(SyncCommands.putArgs(rsync.binary.absolutePath, peerIp, module, remoteDir, file.absolutePath)) { }
    }

    /** The file's text, or null when it is absent; a file that exists but cannot be read throws. */
    override suspend fun readHomeText(path: String): String? = withContext(Dispatchers.IO) {
        try {
            File(homeRoms(), path).takeIf { it.isFile }?.readText()
        } catch (e: java.io.IOException) {
            NxLog.w(TAG, "device sync read of $path failed", e)
            throw e
        }
    }

    override suspend fun writeHomeText(path: String, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(homeRoms(), path)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) { tmp.delete(); error("rename failed") }
            true
        }.onFailure { NxLog.w(TAG, "device sync write of $path failed", it) }.getOrDefault(false)
    }

    override suspend fun pullCollections(peerIp: String): CollectionsPull {
        val rsync = withContext(Dispatchers.IO) { Rsync.find(app) } ?: return CollectionsPull(NO_RSYNC, emptyList())
        return try {
            SyncCollectionFiles.pull(rsync::runStreaming, rsync.binary.absolutePath, peerIp, File(work, COLLECTIONS_PULL_DIR))
        } catch (e: java.io.IOException) {
            NxLog.w(TAG, "device sync collections pull unreadable", e)
            CollectionsPull(1, emptyList())
        }
    }

    override suspend fun pushCollections(peerIp: String, files: Map<String, String>): Pair<Int, List<String>> {
        val rsync = withContext(Dispatchers.IO) { Rsync.find(app) } ?: return NO_RSYNC to emptyList()
        return try {
            SyncCollectionFiles.push(rsync::runStreaming, rsync.binary.absolutePath, peerIp, File(work, COLLECTIONS_PUSH_DIR), files)
        } catch (e: java.io.IOException) {
            NxLog.w(TAG, "device sync collections push not written", e)
            1 to emptyList()
        }
    }

    override suspend fun readCollections(): Map<String, List<String>?> = withContext(Dispatchers.IO) {
        collections.names().associateWith { collections.readOrNull(it) }
    }

    override suspend fun writeCollection(name: String, lines: List<String>, base: List<String>?): Boolean = withContext(Dispatchers.IO) {
        collections.writeMerged(name, lines, base).also { if (!it) NxLog.w(TAG, "device sync could not write collection $name") }
    }

    override suspend fun runRom(run: RomRun, peerIp: String, onLine: (String) -> Unit): Int {
        // A limited run with nothing left to send does nothing: never "the whole folder".
        if (run.filesFrom?.isEmpty() == true || run.only?.isEmpty() == true) return 0
        val rsync = withContext(Dispatchers.IO) { Rsync.find(app) } ?: return NO_RSYNC
        val args = withContext(Dispatchers.IO) {
            work.mkdirs()
            fun list(name: String, lines: List<String>?): String? =
                lines?.takeIf { it.isNotEmpty() }?.let { File(work, name).apply { writeText(it.joinToString("\n") + "\n") }.absolutePath }
            val filesFrom = list(FILES_FROM_FILE, run.filesFrom?.let(SyncCommands::filesFromLines))
            val exclude = list(EXCLUDE_FILE, run.excludes)
            val filter = list(FILTER_FILE, run.only?.let { SyncCommands.onlyRules(it) })
            val bios = if (run.from is RomEnd.Bios || run.to is RomEnd.Bios) homeBios().path else null
            SyncCommands.romArgs(rsync.binary.absolutePath, run, peerIp, homeRoms().path, extraRoots, filesFrom, exclude, filter, bios)
        }
        val lines = ArrayList<String>()
        val code = rsync.runStreaming(args) { lines += it; onLine(it) }
        return SyncCommands.romResult(run, code, lines)
    }

    override suspend fun runClientStep(step: SyncStep, peerIp: String, onLine: (String) -> Unit, staged: List<String>?): Int {
        // A handheld peer's saves steps run against the staging trees: step 2 sends the send tree, step 4 pulls only the
        // staged tags into the receive tree.
        val stagedDir = when {
            staged == null -> null
            step == SyncStep.SEND_SAVES -> File(work, SAVES_SEND_DIR)
            step == SyncStep.RECEIVE_SAVES -> File(work, SAVES_RECV_DIR)
            else -> null
        }
        val (rsync, root) = withContext(Dispatchers.IO) {
            val root = this@AndroidDeviceSync.root
            File(root, "Saves").mkdirs(); File(root, ".userdata/shared").mkdirs(); stagedDir?.mkdirs()
            (Rsync.find(app) ?: return@withContext null) to root
        } ?: return NO_RSYNC
        val only = staged.takeIf { step == SyncStep.RECEIVE_SAVES }
        return rsync.runStreaming(SyncCommands.clientArgs(rsync.binary.absolutePath, step, peerIp, root.absolutePath, stagedDir?.absolutePath, only), onLine)
    }

    /**
     * This phone's `Saves/` (at the data root now: use it on the IO dispatcher) and the save staging trees beside
     * the daemon's work files.
     */
    private val saveFiles: SyncSaveFiles get() = SyncSaveFiles(File(root, "Saves"), File(work, SAVES_SEND_DIR), File(work, SAVES_RECV_DIR))

    override suspend fun listHomeSaves(): List<SaveFile> = withContext(Dispatchers.IO) { saveFiles.listSaves() }
    override fun defaultTags(): Map<String, String> = settings.defaultTags
    override fun saveFormat(): SaveFormat = settings.saveFormat

    /** The folder's own `systems.txt`, then the app-private store's copy (later wins), as the library scan reads it. */
    override suspend fun homeGameTags(folder: String): Map<String, String> = withContext(Dispatchers.IO) {
        if (!romsAvailable()) return@withContext emptyMap()
        try {
            val own = File(homeRoms(), "$folder/${SidecarTxt.FILE}").takeIf { it.isFile }?.readText()?.let(SidecarTxt::parse).orEmpty()
            own + AndroidSidecarStore(app).read(Recents.HOME, "Roms/$folder", SidecarTxt.FILE)?.let(SidecarTxt::parse).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {   // as the other reads here; a store or SecurityException failure is no tag either
            NxLog.w(TAG, "device sync could not read $folder's systems.txt", e)
            emptyMap()
        }
    }

    override suspend fun stageSaves(copies: List<StageCopy>, seeds: List<StageCopy>): Boolean =
        withContext(Dispatchers.IO) { saveFiles.stage(copies, seeds) }
    override suspend fun listReceivedSaves(): List<SaveFile> = withContext(Dispatchers.IO) { saveFiles.listReceived() }
    override suspend fun applySaveFold(plan: FoldPlan): Boolean = withContext(Dispatchers.IO) { saveFiles.fold(plan, settings.saveFormat.compressed) }
    override suspend fun clearSaveStaging() = withContext(NonCancellable + Dispatchers.IO) {
        try { saveFiles.clear() } catch (e: Exception) { NxLog.d(TAG, "device sync save staging clean-up failed: $e") }
        Unit
    }

    /**
     * Starts the daemon on the real `Saves/` and `.userdata/shared`, filtered. On a failure (false, or a cancel) the
     * controller still calls [stopServer], which ends whatever was begun.
     */
    override suspend fun startServer(peerIp: String, roms: Boolean, onLogLine: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val rsync = Rsync.find(app) ?: return@withContext false.also { NxLog.w(TAG, "device sync server: no rsync") }
            val root = this@AndroidDeviceSync.root
            work.mkdirs(); File(root, "Saves").mkdirs()
            val shared = File(root, ".userdata/shared").apply { mkdirs() }
            // Not rsyncd.log: Rsync.startDaemonWith redirects the daemon's stdout/stderr there (truncating it), which
            // would overwrite the transfer log under the tail.
            val log = File(work, LOG_FILE).apply { delete() }
            // Rsync.startDaemonWith clears and stale-checks work/rsyncd.pid, so the conf must name that very file.
            val serverRoms = if (roms && romsAvailable()) {
                val own = ownExtras()
                val manifestDir = File(work, "nx-extras").apply { mkdirs() }
                File(manifestDir, "extras.json").writeText(SyncExtras.encode(own.manifest))
                ServerRoms(homeRoms().path, own.roots.toSortedMap().values.toList(), manifestDir.absolutePath)
            } else null
            // BIOS on every sync this phone can serve it, ROMs on or off. homeBios() creates the folder before the daemon
            // starts (a missing module folder fails the peer's BIOS phase with `chdir failed`); one that still isn't there
            // is not served, so the peer skips BIOS with a note instead.
            val bios = if (romsAvailable()) homeBios().takeIf { it.isDirectory }?.path else null
            // Collections likewise: the folder is created first; one that still isn't there is not served (the peer skips).
            val collectionsDir = if (romsAvailable()) homeCollections().takeIf { it.isDirectory }?.path else null
            // The served folder must be the real record: app-private copies that differ are folded into it first.
            if (collectionsDir != null) collections.foldStoreIntoHome()
            val conf = SyncCommands.daemonConf(peerIp, shared.absolutePath, File(root, "Saves").absolutePath,
                File(work, PID_FILE).absolutePath, log.absolutePath, serverRoms, bios, collectionsDir)
            val p = rsync.startDaemonWith(conf, SyncWire.RSYNC_PORT, work) ?: return@withContext false
            daemon = p
            val reader = SyncLogTail(log, onLogLine)
            logTail = reader
            tail = io.launch { follow(reader) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NxLog.w(TAG, "device sync server did not start", e)
            false
        }
    }

    /** Reads [reader] every [TAIL_MS] until cancelled; a failed read is logged and retried on the next round. */
    private suspend fun follow(reader: SyncLogTail) {
        while (currentCoroutineContext().isActive) {
            try { reader.readNew() } catch (e: Exception) { NxLog.w(TAG, "device sync log read failed", e) }
            delay(TAIL_MS)
        }
    }

    /**
     * Stops the daemon, then ends the tail and reads the log one last time (the lines written as the daemon stopped,
     * and an unterminated last line), so every line reaches onLogLine before this returns. The final pass runs only
     * after the tail job finished, keeping one reader; if it does not finish within [JOIN_MS] those lines are dropped.
     * Never throws; bounded by Rsync.stop's grace + [JOIN_MS].
     */
    override suspend fun stopServer(): Unit = withContext(NonCancellable + Dispatchers.IO) {
        try {
            daemon?.let { p -> try { Rsync.stop(p) } catch (e: Exception) { NxLog.w(TAG, "device sync daemon stop failed", e) } }
            daemon = null
            val job = tail; tail = null
            val reader = logTail; logTail = null
            val ended = job == null || withTimeoutOrNull(JOIN_MS) { job.cancel(); job.join(); true } == true
            if (reader != null) {
                if (ended) { reader.readNew(); reader.flush() }
                else NxLog.w(TAG, "device sync log tail did not stop; its last lines are dropped")
            }
        } catch (e: Exception) {
            NxLog.w(TAG, "device sync server stop failed", e)
        }
    }

    companion object {
        private const val TAG = "NxSync"
        private const val TAIL_MS = 200L
        private const val JOIN_MS = 2_000L
        private const val LOG_FILE = "sync.log"
        private const val PID_FILE = "rsyncd.pid"
        private const val FETCH_FILE = "fetch.tmp"
        private const val PUSH_DIR = "push"
        private const val COLLECTIONS_PULL_DIR = "collections-pull"
        private const val COLLECTIONS_PUSH_DIR = "collections-push"
        private const val FILES_FROM_FILE = "files-from.txt"
        private const val EXCLUDE_FILE = "exclude.txt"
        private const val FILTER_FILE = "filter.txt"
        private const val SAVES_SEND_DIR = "saves-send"
        private const val SAVES_RECV_DIR = "saves-recv"
        private const val CABLE_BROADCAST = "10.99.0.3"
        /** rsync not found mid-sync (the sync checks [rsyncAvailable] first): a failed step, the shell's "command not found". */
        private const val NO_RSYNC = 127
        /** Under filesDir: the daemon's conf, pid and log. */
        const val WORK_DIR = "device-sync"
    }
}
