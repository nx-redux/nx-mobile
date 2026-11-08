package com.nxredux.mobile.sync

import com.nxredux.mobile.netplay.usb.FakeUsbCable
import com.nxredux.mobile.storage.SaveFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class FakeSyncSocket : SyncSocket {
    val incoming = Channel<SyncDatagram>(Channel.UNLIMITED)
    val sent = mutableListOf<Pair<String, String>>()   // text to ip
    val broadcasts = mutableListOf<String>()
    var closed = false
    var failWith: IOException? = null
    override suspend fun send(text: String, ip: String) { failWith?.let { throw it }; sent += text to ip }
    override suspend fun broadcast(text: String) { failWith?.let { throw it }; broadcasts += text }
    override suspend fun receive(timeoutMs: Long): SyncDatagram? { failWith?.let { throw it }; return withTimeoutOrNull(timeoutMs) { incoming.receive() } }
    override fun close() { closed = true }
    fun from(ip: String, text: String) { incoming.trySend(SyncDatagram(text, ip)) }
}

class FakeSyncPlatform(override val cable: FakeUsbCable = FakeUsbCable()) : DeviceSyncPlatform {
    var wifi: String? = "192.168.1.20"
    val socket = FakeSyncSocket()
    var socketOk = true
    var gameAlive = false
    var importingNow = false
    /** Per step: output lines and exit code. */
    val steps = mutableMapOf<SyncStep, Pair<List<String>, Int>>()
    val stepsRun = mutableListOf<SyncStep>()
    /** Per step: an exception runClientStep throws instead of running (rsync could not start). */
    val stepThrows = mutableMapOf<SyncStep, Exception>()
    /** When set, that step's runClientStep waits for [holdResult] after its lines; [stepLine] is the latest onLine. */
    var holdAt: SyncStep? = null
    val holdResult = CompletableDeferred<Int>()
    var stepLine: ((String) -> Unit)? = null
    var serverStarts = true
    var serverStarted: String? = null
    var logLine: ((String) -> Unit)? = null
    /** Server teardown calls: "stop" per stopServer. */
    val serverCalls = mutableListOf<String>()
    /** Runs inside stopServer before it returns: a test delivers late log lines here. */
    var onStopServer: () -> Unit = {}
    /** When set, stopServer waits for it (a slow daemon stop). */
    var stopGate: CompletableDeferred<Unit>? = null
    /** stopServer calls that returned. */
    var stopped = 0
    val alive = mutableListOf<Pair<Boolean, String?>>()
    override fun wifiIp() = wifi
    override fun openSocket(link: SyncLink): SyncSocket? = if (socketOk) socket else null
    override fun gameScreenAlive() = gameAlive
    override fun importing() = importingNow
    var roms = false
    override fun romsAvailable() = roms
    var rsync = true
    override suspend fun rsyncAvailable() = rsync
    /** Per step run: the `staged` tags it was given. */
    val stagedBy = mutableMapOf<SyncStep, List<String>?>()
    override suspend fun runClientStep(step: SyncStep, peerIp: String, onLine: (String) -> Unit, staged: List<String>?): Int {
        stepsRun += step; calls += step.name; stagedBy[step] = staged; stepPasswords += password
        stepThrows[step]?.let { throw it }
        stepLine = onLine
        val (lines, code) = steps[step] ?: (emptyList<String>() to 0)
        lines.forEach(onLine)
        if (step != holdAt) return code
        try {
            return holdResult.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            heldCancelled = true   // the real platform kills its rsync here
            throw e
        }
    }
    /** The held step was cancelled while it waited (its rsync would have been killed). */
    var heldCancelled = false
    /** The run's rsync password as [pairWith] set it (null: none, or the run ended). */
    var password: String? = null
    /** The password in effect at each client step. */
    val stepPasswords = mutableListOf<String?>()
    /** The password in effect when the daemon started. */
    var serverPassword: String? = null
    override fun pairWith(password: String?) { this.password = password }
    /** Random bytes: the nonce 0123456789abcdef for eight. */
    override fun randomBytes(count: Int): ByteArray = ByteArray(count) { (0x01 + 0x22 * it).toByte() }
    var serverRoms: Boolean? = null
    override suspend fun startServer(peerIp: String, roms: Boolean, onLogLine: (String) -> Unit): Boolean {
        serverStarted = peerIp; serverRoms = roms; logLine = onLogLine; serverPassword = password; return serverStarts
    }
    override suspend fun stopServer() { serverCalls += "stop"; onStopServer(); stopGate?.await(); stopped++ }
    override fun keepAlive(active: Boolean, text: String?) { alive += active to text }
    val logs = mutableListOf<String>()
    override fun log(message: String) { logs += message }

    var fileTimesOk = true
    var remoteListing: Pair<Int, List<String>> = 0 to listOf("drwxr-xr-x 4,096 2026/10/15 12:00:00 Game Boy (GB)")
    var home: Map<String?, List<String>> = mapOf(null to listOf("Game Boy (GB)", "Nintendo 3DS (3DS)"), "Game Boy (GB)" to emptyList())
    var extras = OwnExtras(ExtrasManifest(), emptyMap())
    val remoteTexts = mutableMapOf<String, RemoteText>()      // "module/path"
    val pushedTexts = mutableListOf<Triple<String, String, String>>()   // module, dir/name, text
    val homeTexts = mutableMapOf<String, String>()
    val romRuns = mutableListOf<RomRun>()
    /** Per run index: lines and exit code. */
    val romResults = mutableMapOf<Int, Pair<List<String>, Int>>()
    /** When set, that run's runRom waits for [romHoldResult] after its lines. */
    var romHoldAt: Int? = null
    val romHoldResult = CompletableDeferred<Int>()
    override suspend fun checkFileTimes() = fileTimesOk
    var saveTimesOk = true
    override suspend fun checkSaveFileTimes() = saveTimesOk
    /** Every client step and ROM/BIOS run, by step name, in the order they ran. */
    val calls = mutableListOf<String>()
    /** The peer's `bios` module listing: exit code and lines (default: listed, no tag folders). */
    var remoteBios: Pair<Int, List<String>> = 0 to emptyList()
    var biosListings = 0
    /** The tag folders in this phone's home `Bios/`. */
    var homeBios: List<String> = emptyList()
    override suspend fun listRemoteBios(peerIp: String): Pair<Int, List<String>> { biosListings++; calls += "LIST_BIOS"; return remoteBios }
    override suspend fun listHomeBios() = homeBios
    override suspend fun listRemoteRoms(peerIp: String) = remoteListing
    /** Per remote console folder: its `--list-only` exit code and lines (absent: 0, none). */
    val remoteFolders = mutableMapOf<String, Pair<Int, List<String>>>()
    val folderListings = mutableListOf<String>()
    var remoteHashes: Pair<Int, List<String>> = 0 to emptyList()
    var hashListings = 0
    override suspend fun listRemoteHashes(peerIp: String): Pair<Int, List<String>> { hashListings++; return remoteHashes }
    override suspend fun listRemoteFolder(peerIp: String, folder: String): Pair<Int, List<String>> {
        folderListings += folder
        return remoteFolders[folder] ?: (0 to emptyList())
    }
    override suspend fun listHomeRoms(console: String?) = home[console].orEmpty()
    override suspend fun ownExtras() = extras
    val fetched = mutableListOf<String>()
    override suspend fun fetchRemoteText(peerIp: String, module: String, path: String): RemoteText {
        fetched += "$module/$path"
        return remoteTexts["$module/$path"] ?: RemoteText.Absent
    }
    override suspend fun pushRemoteText(peerIp: String, module: String, remoteDir: String, name: String, text: String): Int {
        pushedTexts += Triple(module, "$remoteDir/$name", text); return 0
    }
    /** Paths whose [readHomeText] throws (a file that exists but cannot be read). */
    val unreadableHome = mutableSetOf<String>()
    override suspend fun readHomeText(path: String): String? {
        if (path in unreadableHome) throw java.io.IOException("unreadable")
        return homeTexts[path]
    }
    override suspend fun writeHomeText(path: String, text: String): Boolean { homeTexts[path] = text; return true }
    /** The peer's `collections` module as pulled (default: pulled, no files). */
    var collectionsPull = CollectionsPull(0, emptyList())
    /** When set, pullCollections waits for it first (the page shows step 7 meanwhile). */
    var collectionsGate: CompletableDeferred<Unit>? = null
    /** The push's exit code and lines. */
    var collectionsPushResult: Pair<Int, List<String>> = 0 to emptyList()
    val pushedCollections = mutableListOf<Map<String, String>>()
    /** This phone's collections (name → lines). */
    val ownCollections = mutableMapOf<String, List<String>?>()
    val writtenCollections = mutableListOf<Pair<String, List<String>>>()
    /** The base each write was given, in order. */
    val writeBases = mutableListOf<List<String>?>()
    /** False: every writeCollection fails. */
    var collectionWriteOk = true
    /** Runs after each writeCollection (a test writes the file where AppState reads it). */
    var onWriteCollection: suspend (String, List<String>) -> Unit = { _, _ -> }
    override suspend fun pullCollections(peerIp: String): CollectionsPull {
        calls += "PULL_COLLECTIONS"; collectionsGate?.await(); return collectionsPull
    }
    override suspend fun pushCollections(peerIp: String, files: Map<String, String>): Pair<Int, List<String>> {
        calls += "PUSH_COLLECTIONS"; pushedCollections += files; return collectionsPushResult
    }
    override suspend fun readCollections(): Map<String, List<String>?> = ownCollections.toMap()
    override suspend fun writeCollection(name: String, lines: List<String>, base: List<String>?): Boolean {
        if (!collectionWriteOk) return false
        writtenCollections += name to lines; writeBases += base; ownCollections[name] = lines; onWriteCollection(name, lines); return true
    }
    override suspend fun runRom(run: RomRun, peerIp: String, onLine: (String) -> Unit): Int {
        val i = romRuns.size; romRuns += run; calls += run.step.name
        onRomRun(run)
        val (lines, code) = romResults[i] ?: (emptyList<String>() to 0)
        lines.forEach(onLine)
        return if (i == romHoldAt) romHoldResult.await() else code
    }

    /** Called with each ROM run as it starts (a test's pull can change [home]). */
    var onRomRun: (RomRun) -> Unit = {}

    /** The files below this phone's data root `Saves/`. */
    var homeSaves: List<SaveFile> = emptyList()
    var defaults: Map<String, String> = emptyMap()
    var format: SaveFormat = SaveFormat.RETROARCH
    /** Per home `Roms/` folder: its `systems.txt` tags. */
    val gameTags = mutableMapOf<String, Map<String, String>>()
    /** Every stageSaves call's copies. */
    val staged = mutableListOf<List<StageCopy>>()
    /** Every stageSaves call's receive-tree seeds. */
    val seeded = mutableListOf<List<StageCopy>>()
    var stageOk = true
    /** The receive tree after step 4. */
    var receivedSaves: List<SaveFile> = emptyList()
    val folds = mutableListOf<FoldPlan>()
    var foldOk = true
    var stagingCleared = 0
    override suspend fun listHomeSaves() = homeSaves
    override fun defaultTags() = defaults
    override fun saveFormat() = format
    override suspend fun homeGameTags(folder: String) = gameTags[folder].orEmpty()
    override suspend fun stageSaves(copies: List<StageCopy>, seeds: List<StageCopy>): Boolean { staged += copies; seeded += seeds; return stageOk }
    override suspend fun listReceivedSaves() = receivedSaves
    override suspend fun applySaveFold(plan: FoldPlan): Boolean { folds += plan; return foldOk }
    override suspend fun clearSaveStaging() { stagingCleared++ }
}
