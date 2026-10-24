package com.nxredux.mobile.sync

import com.nxredux.mobile.netplay.usb.UsbCable
import com.nxredux.mobile.storage.SaveFormat

/** How Device Sync reaches the other device: the USB-C cable link, or the shared Wi-Fi. */
enum class SyncLink { CABLE, WIFI }

/** The phone's side of a sync: [CLIENT] runs the rsync steps, [SERVER] runs the daemon the peer's steps use. */
enum class SyncRole { CLIENT, SERVER }

/** One received UDP datagram: its [text] and the address it came from. */
data class SyncDatagram(val text: String, val sourceIp: String)

/** UDP on [SyncWire.UDP_PORT], bound to one link's network. Calls throw IOException when the network is gone. */
interface SyncSocket {
    /** Sends [text] to [ip] alone. */
    suspend fun send(text: String, ip: String)
    /** Sends [text] to the link network's broadcast address. */
    suspend fun broadcast(text: String)
    /** The next datagram within [timeoutMs], or null. */
    suspend fun receive(timeoutMs: Long): SyncDatagram?
    /** Releases the socket; idempotent. */
    fun close()
}

/** A remote file read: its text, absent (rsync exit 23 "No such file"), or a failure with rsync's exit code. */
sealed interface RemoteText {
    data class Found(val text: String) : RemoteText
    data object Absent : RemoteText
    data class Failed(val code: Int) : RemoteText
}

/**
 * The peer's `collections` module as pulled: rsync's exit [code] and output [lines], and when 0 its [files] (file name →
 * text) and the file names that would not decode as UTF-8 ([unreadable]: left alone both ways).
 */
data class CollectionsPull(
    val code: Int, val lines: List<String>, val files: Map<String, String> = emptyMap(), val unreadable: Set<String> = emptySet(),
)

/**
 * Collections a run changed on this phone: [written] through the app's own writer (client), [received] from a peer
 * phone's push straight into the home `Collections/` by the daemon (server), which a store copy must take in.
 */
data class SyncedCollections(val written: Set<String> = emptySet(), val received: Set<String> = emptySet())

/** This phone's extra-folder games and their roots (n → real path). */
data class OwnExtras(val manifest: ExtrasManifest, val roots: Map<Int, String>)

/** What [DeviceSyncController] needs from the platform: the links, rsync, the app's state and the foreground service. */
interface DeviceSyncPlatform {
    val cable: UsbCable
    /** This phone's Wi-Fi IPv4, or null with no Wi-Fi. */
    fun wifiIp(): String?
    /** A socket on [link]'s network, or null when it cannot be opened. */
    fun openSocket(link: SyncLink): SyncSocket?
    /** A game is open (running or paused): a sync would race its saves. */
    fun gameScreenAlive(): Boolean
    /** An Import is writing saves: a sync would race it. */
    fun importing(): Boolean
    /** The bundled rsync runs: a build without it can't sync either role, so the sync stops before pairing. */
    suspend fun rsyncAvailable(): Boolean
    /** ROM sync can run here: All files access is on and the home folder is a real path. Cheap; called on the main thread. */
    fun romsAvailable(): Boolean
    /**
     * Runs one client step against [peerIp]; each output line to [onLine]; the exit code. With [staged] (a handheld
     * peer) steps 2 and 4 run against the save staging trees instead of `Saves/`, and step 4 pulls only the handheld's
     * `Saves/<T>/` for each `T` in [staged].
     */
    suspend fun runClientStep(step: SyncStep, peerIp: String, onLine: (String) -> Unit, staged: List<String>? = null): Int
    /** Every file below this phone's private `Saves/` (relative paths, ms times). */
    suspend fun listHomeSaves(): List<SaveFile>
    /** Settings → Default emulators (console id → tag), read once per sync. */
    fun defaultTags(): Map<String, String>
    /** Settings → Save format, read once per sync. */
    fun saveFormat(): SaveFormat
    /** The `systems.txt` tags of home `Roms/<folder>/` (file name → tag); empty when none or unreadable. */
    suspend fun homeGameTags(folder: String): Map<String, String>
    /**
     * Empties the save staging trees, copies [copies] into the send tree (times kept) and seeds the receive tree with the
     * same files plus [seeds] (receive tree only, [SaveSyncPlan.seeds]), so step 4's `--update` downloads only what is
     * newer or missing. False on an I/O error.
     */
    suspend fun stageSaves(copies: List<StageCopy>, seeds: List<StageCopy>): Boolean
    /** Every file in the receive staging tree (after step 4). */
    suspend fun listReceivedSaves(): List<SaveFile>
    /**
     * Applies [plan] to the private `Saves/`: every write first (a battery save decoded, then encoded as the Save format
     * asks; times kept), then the removes, never those of a game whose write failed. False when anything failed.
     */
    suspend fun applySaveFold(plan: FoldPlan): Boolean
    /** Removes both save staging trees; a failure is logged here (shared code has no logger), not thrown. */
    suspend fun clearSaveStaging()
    /** Sets a temp file's time in the home Roms/ a day back and reads it again: true when it stuck (±2 s). */
    suspend fun checkFileTimes(): Boolean
    /** [checkFileTimes] in the data root's `Saves/` (the home folder's once the saves moved): saves sync by file times too. */
    suspend fun checkSaveFileTimes(): Boolean = true
    /** `--list-only` of the peer's roms module: exit code and output lines. */
    suspend fun listRemoteRoms(peerIp: String): Pair<Int, List<String>>
    /** `--list-only` of the peer's `roms/<folder>/` (every top-level entry): exit code and output lines. */
    suspend fun listRemoteFolder(peerIp: String, folder: String): Pair<Int, List<String>>
    /** `--list-only -R` of every console folder's `.retroachievements/hashes.json` on the peer: exit code and output lines. */
    suspend fun listRemoteHashes(peerIp: String): Pair<Int, List<String>>
    /** `--list-only` of the peer's bios module (its top level): exit code and output lines. */
    suspend fun listRemoteBios(peerIp: String): Pair<Int, List<String>>
    /** The top-level folders (not files) in the home `Bios/`. */
    suspend fun listHomeBios(): List<String>
    /** Top-level names in the home Roms/ ([console] null) or in Roms/<console>/. */
    suspend fun listHomeRoms(console: String?): List<String>
    /** This phone's extra-folder games and their roots. */
    suspend fun ownExtras(): OwnExtras
    /** The text of [path] in the peer's [module]. */
    suspend fun fetchRemoteText(peerIp: String, module: String, path: String): RemoteText
    /** Pushes [text] as [name] into the peer's [module]/[remoteDir]/; rsync's exit code. */
    suspend fun pushRemoteText(peerIp: String, module: String, remoteDir: String, name: String, text: String): Int
    /** A text file below the home Roms/ (null when absent). */
    suspend fun readHomeText(path: String): String?
    /** Writes a text file below the home Roms/; false when it could not. */
    suspend fun writeHomeText(path: String, text: String): Boolean
    /** One ROM run (list/exclude files written by the platform); each output line to [onLine]; the exit code. */
    suspend fun runRom(run: RomRun, peerIp: String, onLine: (String) -> Unit): Int
    /**
     * Pulls the peer's `collections` module (top-level `*.txt`, never `map.txt`) into a fresh cache folder and reads it;
     * the cache is removed again on every path.
     */
    suspend fun pullCollections(peerIp: String): CollectionsPull
    /** Writes [files] (file name → text) into a fresh cache folder and pushes it to the peer's `collections`; exit code and lines. */
    suspend fun pushCollections(peerIp: String, files: Map<String, String>): Pair<Int, List<String>>
    /** This phone's collections (name → lines) as the app reads them; null lines for one whose file would not read. */
    suspend fun readCollections(): Map<String, List<String>?>
    /**
     * Writes collection [name] (matched ignoring case) as [lines] through the app's collection writer. [base]: the lines
     * the merge read (null: no such collection then). A collection that reads otherwise at write time (it appeared
     * meanwhile, or under another case) is never replaced: [lines] it lacks are appended to it. False when it could not
     * be written.
     */
    suspend fun writeCollection(name: String, lines: List<String>, base: List<String>?): Boolean
    /**
     * Starts the daemon for [peerIp], serving the real `Saves/` and `.userdata/shared`, filtered, plus the ROM modules
     * when [roms] (only honoured when [romsAvailable]) and `[bios]` and `[collections]` whenever [romsAvailable]; each
     * daemon log line to [onLogLine]; false when it did not start.
     */
    suspend fun startServer(peerIp: String, roms: Boolean, onLogLine: (String) -> Unit): Boolean
    /** Stops the daemon and delivers every remaining log line to onLogLine before returning. Idempotent; a no-op with no daemon. */
    suspend fun stopServer()
    /** The foreground service + keep-screen-on while [active]; [text] for the notification. */
    fun keepAlive(active: Boolean, text: String?)
    /** A diagnostic line (NxLog on a device: only with Settings → System → Debug log on). */
    fun log(message: String) {}
}

/** What the Device Sync screen shows. */
sealed interface DeviceSyncPage {
    /** The link picker, [link] highlighted; [roms] the X toggle, offered only when [romsAvailable]. */
    data class Ready(val link: SyncLink, val roms: Boolean = false, val romsAvailable: Boolean = false) : DeviceSyncPage
    data object NoWifi : DeviceSyncPage
    data object WaitingForCable : DeviceSyncPage
    /** Saying HELLO as [ownIp] on [link] until a device answers. */
    data class Searching(val link: SyncLink, val ownIp: String) : DeviceSyncPage
    /**
     * [step] null: the client waits for READY, or the server role (no steps). [lines] newest last, at most 200. [steps]:
     * 9 with ROMs, else 7 with BIOS and collections, else 4; [console] the console a ROM step works on (a BIOS step: the tag).
     */
    data class Syncing(
        val link: SyncLink, val peerIp: String, val role: SyncRole, val step: SyncStep?, val done: Int, val total: Int,
        val lines: List<String>, val confirmStop: Boolean = false, val steps: Int = 4, val console: String? = null,
    ) : DeviceSyncPage
    /** [notes]: extra lines under the counts (consoles only on one side, ROMs skipped). */
    data class Done(val link: SyncLink, val received: Int, val sent: Int, val notes: List<String> = emptyList()) : DeviceSyncPage
    /** [detail]: rsync's own last error line under the [reason], when a step failed with one. */
    data class Failed(val link: SyncLink, val reason: String, val detail: String? = null) : DeviceSyncPage
}
