package com.nxredux.mobile.storage

import android.content.Context
import com.nxredux.mobile.AppProcess
import com.nxredux.mobile.gametime.GameTimeService
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.LiveGameStore
import com.nxredux.mobile.runner.NxStorage
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.PrefsProvider
import com.nxredux.mobile.tools.CheatDbService
import com.nxredux.mobile.tools.CheatsRoot
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch

/**
 * The root saves, states, configs, game time, recents and cheats are read and written under ([StorageLayout.Kind.MOVE]
 * and [StorageLayout.Kind.CHEATS] paths): the home folder ([AndroidHomeLocator]) once the data an earlier version kept
 * in app-private storage has moved there, the private root until then, so a session never sees a split set. Caches and
 * credentials stay under [private] for good.
 *
 * The move happens once. With All files access and a file-path home it starts in the background at process start or
 * the library's load ([startIfDue]), never while a game session or Device Sync uses the data ([useBegin], [useEnd]: it
 * waits for the last one); otherwise the upgrade screen runs it ([moveNow], then [markDone]). The marker survives the
 * move: it is in no-backup storage, not in either root.
 */
object DataRoot {
    private const val TAG = "DataRoot"
    /** In no-backup storage: present once private data is in the home folder (or there was none). */
    private const val MARKER = "storage-moved"
    /** In no-backup storage: how many background moves failed (the library says so after [STUCK_AFTER]). */
    private const val FAILURES = "storage-move-failures"
    private const val STUCK_AFTER = 3
    private const val BACKUP = ".bak-"
    /** SQLite's files beside a database: they move, clash and back up with it, never alone. */
    private val DB_SIDECARS = listOf("-wal", "-shm", "-journal")
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    /** A copy written beside its target before it is renamed into place: `.<name>.moving`. */
    internal const val STAGED = ".moving"
    /** The home folders a move writes into (where an earlier move's staged copies may be left). */
    private val HOME_DIRS = listOf("Saves", ".userdata", "Cheats")
    /** How many times a set that changed mid-move is copied again before the move gives up as incomplete. */
    private const val PASSES = 3
    private const val BLOCK = 64 * 1024

    private val lock = Any()
    /** Held while files move: one move at a time, [of] never waits on it. */
    private val moving = Any()
    /** Game sessions and Device Sync runs on the data root, and the move they hold off. Changed under [lock] only. */
    private val gate = MoveGate { pid -> pidAlive(pid) }
    /** Set from any entry that has a context; only read for game pids, which only arrive through [gameUseBegin]. */
    @Volatile private var pidAlive: (Int) -> Boolean = { true }
    @Volatile private var move: CountDownLatch? = null
    @Volatile private var moveFailed = false
    /** The marker seen (it is never removed while the process lives): [of] then checks nothing else. */
    @Volatile private var done = false
    /** Private data seen: it only goes with the move, which sets the marker, so the private root is not walked again. */
    @Volatile private var privateHasData = false
    /** One settings and one home locator for the process: [of] is called per store call. */
    @Volatile private var settings: AndroidAppSettings? = null
    @Volatile private var locator: AndroidHomeLocator? = null

    /** What [of] does: use [root]; set the marker first when [mark]; [move] is the home a background move would go into. */
    internal data class Decision(val root: File, val mark: Boolean = false, val move: File? = null)

    /**
     * The data root now. Cheap once the move is done (a file check and the home choice); until then it may walk the
     * private root once, so call it off the main thread. It never starts the move ([startIfDue] does).
     */
    fun of(context: Context): File {
        val app = context.applicationContext
        val decision = decide(app)
        if (decision.mark) markDone(app)
        return decision.root
    }

    /** [of] once a move it needs has finished: one root for a whole session. Blocks, so never on the main thread. */
    fun settled(context: Context): File {
        if (AppProcess.isGame(context)) return File(ownerCall(context, PrefsProvider.METHOD_DATA_ROOT_SETTLED, emptyMap())[PrefsProvider.RESULT_PATH] as String)
        of(context)
        move?.await()
        return of(context)
    }

    /**
     * Starts the background move when it is due (Case A: the saved full-access home reachable, private data to move):
     * at process start and on the library's load, never from a store call. While a game or Device Sync uses the data
     * it waits for the last of them to end. Not again in a process after a failure. Walks the private root: off the
     * main thread.
     */
    fun startIfDue(context: Context) {
        val app = context.applicationContext
        if (isDone(app) || moveFailed) return
        pidAlive = AppProcess.aliveCheck(app)
        val decision = decide(app)
        if (decision.mark) markDone(app)
        if (decision.move != null) startMove(app)
    }

    /** A game session or a Device Sync run starts on the data root: no move starts until [useEnd]. Call before [settled]. */
    fun useBegin(context: Context) {
        if (AppProcess.isGame(context)) { ownerCall(context, PrefsProvider.METHOD_DATA_ROOT_USE, mapOf(PrefsProvider.ARG_BEGIN to true)); return }
        synchronized(lock) { gate.begin() }
    }

    /** The game process [pid] (a provider call) starts using the data root; it counts only while that process lives. */
    internal fun gameUseBegin(context: Context, pid: Int) {
        pidAlive = AppProcess.aliveCheck(context)
        synchronized(lock) { gate.beginFor(pid) }
    }

    internal fun gameUseEnd(context: Context, pid: Int) {
        val app = context.applicationContext
        pidAlive = AppProcess.aliveCheck(app)
        if (synchronized(lock) { gate.endFor(pid) }) Thread({ startIfDue(app) }, "data-move-start").start()
    }

    /** The use [useBegin] began ended; the last one starts a move it held off. */
    fun useEnd(context: Context) {
        val app = context.applicationContext
        if (AppProcess.isGame(app)) { ownerCall(app, PrefsProvider.METHOD_DATA_ROOT_USE, mapOf(PrefsProvider.ARG_BEGIN to false)); return }
        if (synchronized(lock) { gate.end() }) Thread({ startIfDue(app) }, "data-move-start").start()
    }

    /** The game process has no say over the data move: the main process owns the root and the gate, so it is asked. */
    private fun ownerCall(context: Context, method: String, args: Map<String, Any?>): Map<String, Any?> =
        PrefsProvider.channel(context).call(method, "data-root", args)

    /** App-private storage: caches, credentials, art, and the data until it moves. */
    fun private(context: Context): File = NxStorage.root(context)

    fun isDone(context: Context): Boolean = done || File(context.noBackupFilesDir, MARKER).exists().also { if (it) done = true }

    /**
     * Sets the marker, so [of] answers the home from now on. The game-time database is closed again (a store opened
     * while the move ran points at the private file, now gone) and Cheats & Patches rereads its manifest at the new root.
     */
    fun markDone(context: Context) {
        val marker = File(context.noBackupFilesDir, MARKER)
        runCatching { marker.parentFile?.mkdirs(); marker.createNewFile() }
            .onSuccess { done = true }
            .onFailure { NxLog.w(TAG, "cannot mark the move done", it) }
        GameTimeService.closeForMove()
        CheatDbService.refreshIfStarted()
    }

    /** A new install finished setup: marked done when the private root holds nothing to move; otherwise the move decides. */
    fun markIfEmpty(context: Context) {
        val app = context.applicationContext
        if (!isDone(app) && !hasData(private(app))) markDone(app)
    }

    /** The background move failed at least [STUCK_AFTER] times (one per start) and the data is still private. */
    fun moveStuck(context: Context): Boolean = !isDone(context) && failures(context) >= STUCK_AFTER

    private fun failures(context: Context): Int =
        runCatching { File(context.noBackupFilesDir, FAILURES).readText().trim().toInt() }.getOrDefault(0)

    private fun countFailure(context: Context) {
        runCatching { File(context.noBackupFilesDir, FAILURES).writeText((failures(context) + 1).toString()) }
            .onFailure { NxLog.w(TAG, "cannot count the failed move", it) }
    }

    /**
     * Moves the private data into [target] now (the upgrade screen, which then calls [markDone]), after a background move
     * still running. Blocks. Returns the files moved. Throws an [IOException] when the move is not complete: a game or
     * Device Sync uses the data now, a copy failed (the private data then left whole), or files kept changing while they
     * moved ([IncompleteMove]: nothing deleted); either way the caller must not call [markDone].
     */
    fun moveNow(context: Context, target: File): Int {
        val app = context.applicationContext
        pidAlive = AppProcess.aliveCheck(app)
        while (true) {
            move?.await()
            val gameLive = LiveGameStore.alive(app)   // a game process this process has no use for (it restarted mid-game)
            val latch = synchronized(lock) {
                when {
                    move != null -> null   // a background move began meanwhile: wait for it too
                    gate.tryStart(postpone = false, gameLive = gameLive) -> CountDownLatch(1).also { move = it }
                    !gate.idle || gameLive -> throw IOException("a game or Device Sync is using the data")
                    else -> null
                }
            } ?: continue
            try {
                synchronized(moving) {
                    GameTimeService.closeForMove()
                    return moveAll(private(app), target).also { privateHasData = false }
                }
            } catch (e: Exception) {
                GameTimeService.closeForMove()   // a store opened during the move may hold a file the move replaced
                throw e
            } finally {
                synchronized(lock) { move = null; gate.finish() }
                latch.countDown()
            }
        }
    }

    /** [resolve] on the settings, the home and the private root now. */
    private fun decide(app: Context): Decision {
        val private = private(app)
        return resolve(
            marker = isDone(app),
            hasData = { privateHasData || hasData(private).also { privateHasData = it } },
            saved = settings(app).treeRef,
            effective = locator(app).current(),
            media = MediaHome.cachedDir(app),
            private = private,
        )
    }

    private fun settings(app: Context): AndroidAppSettings = settings ?: AndroidAppSettings(app).also { settings = it }

    private fun locator(app: Context): AndroidHomeLocator = locator ?: AndroidHomeLocator(app).also { locator = it }

    /**
     * [of]'s choice from its inputs: [marker] present, [hasData] (the private root holds files that move; only asked
     * when needed), the [saved] home setting, the [effective] home ([HomeChoice.effective]), the [media] home and the
     * [private] root.
     */
    internal fun resolve(marker: Boolean, hasData: () -> Boolean, saved: ContentRef?, effective: ContentRef?, media: File, private: File): Decision {
        val home = effective?.takeIf { FileRefs.isFile(it) }?.let { File(FileRefs.path(it)) }
        if (marker) return Decision(home ?: media)   // a picker home after the upgrade only by mistake: its data has no path
        // A picker home waits for the upgrade screen, which makes it an extra folder, even with nothing to move.
        if (home == null) return Decision(private)
        if (!hasData()) return Decision(home, mark = true)
        // Case A: the saved full-access home is reachable. Any other home (access off, card out) waits with the private root.
        if (effective == saved) return Decision(private, move = home)
        return Decision(private)
    }

    /** True when [private] holds a file that moves (first found ends the walk). */
    internal fun hasData(private: File): Boolean =
        private.walkTopDown().any { it.isFile && StorageLayout.kind(it.relativeTo(private).invariantSeparatorsPath) != StorageLayout.Kind.STAY }

    private fun startMove(app: Context) {
        val latch = synchronized(lock) {
            if (move != null || done) return
            if (!gate.tryStart(postpone = true, gameLive = LiveGameStore.alive(app))) return   // in use: the last useEnd asks again, or the menu's next resume
            CountDownLatch(1).also { move = it }
        }
        Thread({
            try {
                synchronized(moving) {
                    val again = decide(app)
                    when {
                        again.move != null -> {
                            GameTimeService.closeForMove()
                            val moved = moveAll(private(app), again.move)
                            privateHasData = false
                            NxLog.i(TAG, "moved $moved files into ${again.move.path}")
                            markDone(app)
                        }
                        again.mark -> markDone(app)
                    }
                }
            } catch (e: Exception) {
                moveFailed = true   // not retried in this process: the data stays private until the next start
                countFailure(app)
                GameTimeService.closeForMove()   // a store opened during the move may hold a file the move replaced
                NxLog.w(TAG, "moving the data into the home folder failed", e)
            } finally {
                synchronized(lock) { move = null; gate.finish() }
                latch.countDown()
            }
        }, "data-move").start()
    }

    /**
     * Moves every [StorageLayout.Kind.MOVE] file of [private] into [home] at the same relative path, then the cheats
     * ([CheatsRoot.moveFromPrivate]'s rules). On a clash, the same content drops the private copy; different content
     * keeps the newer file (by modified time) under the name and the older one beside it as `<name>.bak-<yyyyMMdd-HHmmss>`
     * (its own time; `-2`, `-3`… when taken, one suffix for a whole set; [now] when it has none). A database moves with
     * its `-wal`, `-shm` and `-journal` files as one set. Every copy is written as `.<name>.moving` with its modified
     * time, then renamed into place, so a kill mid-copy never leaves a cut-off file under a real name; staged files left
     * by an earlier kill are dropped first. Everything is copied before anything private is deleted, and a failed copy
     * throws with nothing deleted. A private set that changed after its copy (a store still writing the private root) is
     * copied again, over this move's own copy, up to [PASSES] times; still changing, nothing at all is deleted and
     * [IncompleteMove] is thrown, so no caller sets the marker and the next move drops the identical copies and settles
     * that set by the clash rule. The private root is walked again after every pass: a file created mid-move is copied in
     * the next pass, and one still appearing (or new just before the deletes) leaves the move incomplete, nothing
     * deleted. Emptied private folders are removed. Returns the files copied into [home].
     * [beforeDelete] runs after each pass of copies, [onCopy] on each staged copy before its time is set (tests).
     */
    internal fun moveAll(private: File, home: File, now: Long = System.currentTimeMillis(), beforeDelete: () -> Unit = {}, onCopy: (File) -> Unit = {}): Int {
        if (private.canonicalFile == home.canonicalFile) return 0
        dropStaged(private)
        HOME_DIRS.forEach { dropStaged(File(home, it)) }
        // The sets in the private root now: a sidecar whose database moves too goes with it. Walked again after every
        // pass, so a file a store creates mid-move (a first config, the first hidden list) moves too.
        fun walk(): Set<String> {
            val moving = private.walkTopDown().filter { it.isFile && !it.name.endsWith(STAGED) }.map { it.relativeTo(private).invariantSeparatorsPath }
                .filter { StorageLayout.kind(it) == StorageLayout.Kind.MOVE }.toSet()
            return moving.filterNot { rel -> DB_SIDECARS.any { rel.endsWith(it) && rel.removeSuffix(it) in moving } }.toSet()
        }
        val sets = LinkedHashSet(walk())
        fun members(root: File, rel: String) = (listOf(rel) + DB_SIDECARS.map { rel + it }).map { File(root, it) }
        fun snapshots(rel: String) = members(private, rel).filter { it.isFile }.associate { it.name to Snapshot(it) }
        val counts = HashMap<String, Int>()
        val ours = HashSet<File>()   // home files this move put under their names: a copy again goes over them
        var pending: Collection<String> = sets.toList()
        var pass = 0
        while (true) {
            val before = HashMap<String, Map<String, Snapshot>>()
            for (rel in pending) {
                before[rel] = snapshots(rel)   // taken before the copy: a later write shows as a change
                val from = members(private, rel).filter { it.isFile }
                val to = members(home, rel).filter { it.exists() }
                counts[rel] = moveSet(from, to, private, home, now, ours, onCopy)
            }
            beforeDelete()
            val created = walk() - sets
            val changed = pending.filter { snapshots(it) != before[it] } + created
            if (changed.isEmpty()) break
            if (++pass >= PASSES) throw IncompleteMove(changed.map { File(private, it) })
            sets += created
            pending = changed
        }
        val moved = counts.values.sum() + CheatsRoot.moveFromPrivate(private, home)
        // Once more just before the deletes: a file created since the last pass leaves the move incomplete, nothing deleted.
        (walk() - sets).takeIf { it.isNotEmpty() }?.let { late -> throw IncompleteMove(late.map { File(private, it) }) }
        val gone = sets.flatMap { members(private, it) }.filter { it.isFile }
        gone.forEach { if (!it.delete()) NxLog.w(TAG, "cannot delete the moved ${it.path}") }
        gone.mapNotNull { it.parentFile }.distinct().forEach { dir ->
            var d: File? = dir
            while (d != null && d != private && d.delete()) d = d.parentFile   // only empty ones go
        }
        return moved
    }

    /**
     * Copies the data of the home [from] (its `Saves/` and `.userdata/` files that move, [StorageLayout.Kind.MOVE]) into
     * the home [to] by [moveAll]'s clash rule, the backups in [to]; nothing in [from] is deleted, and its ROMs, BIOS and
     * collections stay. A failed copy throws, leaving what was copied (whole files only). Returns the files copied.
     */
    fun copyHome(from: File, to: File, now: Long = System.currentTimeMillis()): Int = synchronized(moving) {
        if (from.canonicalFile == to.canonicalFile) return 0
        HOME_DIRS.forEach { dropStaged(File(to, it)) }
        val files = listOf("Saves", ".userdata").map { File(from, it) }.filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && !it.name.endsWith(STAGED) }.toList() }
            .map { it.relativeTo(from).invariantSeparatorsPath }
            .filter { StorageLayout.kind(it) == StorageLayout.Kind.MOVE }.toSet()
        val sets = files.filterNot { rel -> DB_SIDECARS.any { rel.endsWith(it) && rel.removeSuffix(it) in files } }
        var copied = 0
        for (rel in sets) {
            val names = listOf(rel) + DB_SIDECARS.map { rel + it }
            copied += moveSet(names.map { File(from, it) }.filter { it.isFile }, names.map { File(to, it) }.filter { it.exists() }, from, to, now, HashSet(), {})
        }
        return copied
    }

    /** [moveAll] kept [kept] private (each changed while it moved): the move is not done, so the marker stays unset. */
    internal class IncompleteMove(val kept: List<File>) : IOException("changed while moving: ${kept.joinToString { it.path }}")

    /** What says a file was written since: its length and modified time. */
    private data class Snapshot(val length: Long, val modified: Long) {
        constructor(file: File) : this(file.length(), file.lastModified())
    }

    /**
     * One file (or a database and its sidecars), [from] private into [home], over what [to] already holds there; a set
     * whose home files are all [ours] (this move's own copies) is copied over them. Adds what it places to [ours].
     */
    private fun moveSet(from: List<File>, to: List<File>, private: File, home: File, now: Long, ours: MutableSet<File>, onCopy: (File) -> Unit): Int {
        fun target(f: File) = File(home, f.relativeTo(private).path)
        val targets = from.map(::target)
        if (to.isEmpty() || to.all { it in ours }) {
            val staged = stageAll(from, targets, onCopy)
            to.filter { it !in targets }.forEach { it.delete() }   // this move's own sidecar the private set no longer has
            staged.zip(targets).forEach { (s, t) -> place(s, t) }
            ours += targets
            return from.size
        }
        val sameSet = to.map { it.name }.toSet() == from.map { it.name }.toSet() && from.all { sameContent(it, target(it)) }
        if (sameSet) return 0
        val privateTime = from.maxOf { it.lastModified() }
        val homeTime = to.maxOf { it.lastModified() }
        if (privateTime > homeTime) {
            // Staged beside the targets first, so a failed copy leaves the home's set as it was.
            val staged = stageAll(from, targets, onCopy)
            to.zip(backupsFor(to, stamp(homeTime, now))).forEach { (t, b) -> if (!t.renameTo(b)) throw IOException("cannot back up ${t.path}") }
            staged.zip(targets).forEach { (s, t) -> place(s, t) }
            ours += targets
        } else {
            from.zip(backupsFor(targets, stamp(privateTime, now))).forEach { (f, b) -> place(stage(f, b, onCopy), b) }
        }
        return from.size
    }

    /** Each of [from] staged for its [targets] entry; on a failure the ones staged are removed and it is thrown. */
    private fun stageAll(from: List<File>, targets: List<File>, onCopy: (File) -> Unit): List<File> {
        val staged = ArrayList<File>()
        try {
            from.zip(targets).forEach { (f, t) -> staged += stage(f, t, onCopy) }
        } catch (e: IOException) {
            staged.forEach { it.delete() }
            throw e
        }
        return staged
    }

    /**
     * True when [a] and [b] hold the same bytes (read in blocks, never whole: states can be large); false on a read
     * failure.
     */
    internal fun sameContent(a: File, b: File): Boolean {
        if (!a.isFile || !b.isFile || a.length() != b.length()) return false
        return try {
            a.inputStream().use { x -> b.inputStream().use { y -> sameBytes(x, y) } }
        } catch (e: Exception) {
            false
        }
    }

    private fun sameBytes(x: java.io.InputStream, y: java.io.InputStream): Boolean {
        val p = ByteArray(BLOCK)
        val q = ByteArray(BLOCK)
        while (true) {
            val n = readBlock(x, p)
            if (n != readBlock(y, q)) return false
            if (n == 0) return true
            for (i in 0 until n) if (p[i] != q[i]) return false
        }
    }

    /** Fills [buf] from [input] as far as it goes: the bytes read, short only at the end. */
    private fun readBlock(input: java.io.InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    /** Sets a copy's modified time (tests fail it). */
    @Volatile internal var setTime: (File, Long) -> Boolean = { f, t -> f.setLastModified(t) }

    /**
     * [from] copied beside [to] as `.<to's name>.moving` with [from]'s modified time, not yet in place. A failed copy, or
     * a time that won't set (Device Sync and the clash rule read file times), removes it and throws.
     */
    private fun stage(from: File, to: File, onCopy: (File) -> Unit): File {
        val dir = to.parentFile
        if (dir != null && !dir.isDirectory && !dir.mkdirs()) throw IOException("cannot make ${dir.path}")
        val staged = File(dir, ".${to.name}$STAGED")
        try {
            from.copyTo(staged, overwrite = true)
            onCopy(staged)
            val time = from.lastModified()
            if (time > 0 && !setTime(staged, time)) throw IOException("cannot set the modified time of ${staged.path}")
        } catch (e: IOException) {
            staged.delete()
            throw e
        }
        return staged
    }

    /** [staged] renamed to [to]; a failure removes it and throws. */
    private fun place(staged: File, to: File) {
        if (!staged.renameTo(to)) {
            staged.delete()
            throw IOException("cannot place ${to.path}")
        }
    }

    /** Staged copies an earlier move left (killed mid-copy) under [root]: never data, so they go. */
    private fun dropStaged(root: File) {
        if (!root.isDirectory) return
        root.walkTopDown().filter { it.isFile && it.name.startsWith(".") && it.name.endsWith(STAGED) }.forEach { it.delete() }
    }

    /**
     * `<name>.bak-<stamp>` beside each of [files], or the first `-2`, `-3`… free for all of them: a set's backups share
     * one name.
     */
    private fun backupsFor(files: List<File>, stamp: String): List<File> =
        generateSequence(1) { it + 1 }
            .map { n -> files.map { File(it.parentFile, it.name + BACKUP + stamp + if (n == 1) "" else "-$n") } }
            .first { set -> set.none { it.exists() } }

    private fun stamp(mtime: Long, now: Long) = backupStamp(if (mtime > 0) mtime else now)

    /** A backup's time as `yyyyMMdd-HHmmss` in [zone]. */
    internal fun backupStamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = STAMP.format(Instant.ofEpochMilli(millis).atZone(zone))
}
