package com.nxredux.mobile.sync

import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.writeAtomically
import com.nxredux.mobile.storage.Rzip
import com.nxredux.mobile.storage.SaveKey
import com.nxredux.mobile.storage.SaveKeys
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.IOException
import kotlin.math.abs

/**
 * Battery saves against a handheld, the file side: this phone's private [saves] folder, the [send] staging tree step 2
 * pushes and the [recv] staging tree step 4 pulls into, both in the handheld's `Saves/<TAG>/` layout. Blocking; callers
 * move it off the main thread. Every relative path from a plan is checked to stay below its root before anything is
 * touched. [setTime] sets a file's time (a test swaps it).
 */
class SyncSaveFiles(
    private val saves: File, private val send: File, private val recv: File,
    private val setTime: (File, Long) -> Boolean = File::setLastModified,
) {
    /** Every file below [saves] (relative, `/`-separated), with its time; [SyncAreas.isJunk] ones left out. */
    fun listSaves(): List<SaveFile> = list(saves)

    /** Every file below the receive tree. */
    fun listReceived(): List<SaveFile> = list(recv)

    /**
     * Empties both trees, then copies each of [copies] from [saves] into the send tree and the receive tree with the
     * phone file's time, so step 4's `--update` takes only what is newer on the handheld or missing; each of [seeds]
     * ([SaveSyncPlan.seeds]) into the receive tree only, with the same time. False on any error, a tree left behind or a
     * copy whose time did not take (a copy dated now would beat the handheld's newer save). A seed whose time is not
     * exactly the phone file's is dropped (its handheld copy is then downloaded and folded as usual).
     */
    fun stage(copies: List<StageCopy>, seeds: List<StageCopy> = emptyList()): Boolean = try {
        if (!clear()) throw IOException("staging trees not emptied")
        send.mkdirs(); recv.mkdirs()
        for ((c, seed) in copies.map { it to false } + seeds.map { it to true }) {
            val from = below(saves, c.from)
            val bytes = from.readBytes()
            val time = from.lastModified()
            for (root in if (seed) listOf(recv) else listOf(send, recv)) {
                val to = below(root, c.to)
                to.parentFile?.mkdirs()
                to.writeBytes(bytes)
                if (seed) {
                    // Exactly its time or not at all: a later seed could win the fold over a newer handheld save; a
                    // missing one only costs a redundant download.
                    setTime(to, time)
                    if (to.lastModified() != time && !to.delete()) throw IOException("misdated seed not removed: ${c.to}")
                } else if (!timed(to, time)) throw IOException("time not set: ${c.to}")
            }
        }
        true
    } catch (e: Exception) {
        NxLog.w(TAG, "device sync save staging failed", e)
        false
    }

    /**
     * Applies [plan] to [saves]: every write first (from the receive tree; a battery save decoded from RZIP, then
     * encoded again when [compressed]; its time set to the write's), then the removes, skipping those of a game whose
     * write failed so its only copy never goes. False when any write or remove failed (the rest still ran). A write
     * whose time did not take is only logged: its bytes are right, and the next sync merely sends that copy back to the
     * handheld (its `--update` keeps a newer handheld save), so failing the sync would lose nothing worth it.
     */
    fun fold(plan: FoldPlan, compressed: Boolean): Boolean {
        var ok = true
        val failed = HashSet<Pair<String, SaveKey?>>()
        for (w in plan.writes) {
            try {
                val raw = below(recv, w.from).readBytes()
                val bytes = if (!w.battery) raw else {
                    val plain = Rzip.decode(raw) ?: throw IOException("malformed RZIP: ${w.from}")
                    if (compressed) Rzip.encode(plain) else plain
                }
                val to = below(saves, w.to)
                to.writeAtomically(bytes)
                if (!timed(to, w.mtime)) NxLog.w(TAG, "device sync save fold of ${w.from}: time not set")
            } catch (e: Exception) {
                NxLog.w(TAG, "device sync save fold of ${w.from} failed", e)
                ok = false
                failed += game(w.to)
            }
        }
        for (path in plan.removes) {
            if (game(path) in failed) continue
            try {
                val f = below(saves, path)
                if (f.exists() && !f.delete()) throw IOException("not deleted: $path")
            } catch (e: Exception) {
                NxLog.w(TAG, "device sync save remove of $path failed", e)
                ok = false
            }
        }
        return ok
    }

    /** Removes both staging trees; false (logged) when either is left behind. */
    fun clear(): Boolean {
        val ok = send.deleteRecursively() and recv.deleteRecursively()
        if (!ok) NxLog.d(TAG, "device sync save staging not fully removed")
        return ok
    }

    /** Sets [file]'s time to [time]; true when it took, within 2 s (a file system may report false yet set it). */
    private fun timed(file: File, time: Long): Boolean = setTime(file, time) || abs(file.lastModified() - time) <= 2000

    /** The console and game a phone `Saves/` path belongs to, as the plan groups them. */
    private fun game(path: String): Pair<String, SaveKey?> {
        val rest = path.substringAfter('/', "")
        return CoreRegistry.consoleTag(path.substringBefore('/')) to (if ('/' in rest) null else SaveKeys.of(rest))
    }

    private fun list(root: File): List<SaveFile> {
        val base = root.canonicalFile
        return base.walkTopDown().filter { it.isFile }.map { f ->
            SaveFile(f.relativeTo(base).invariantSeparatorsPath, f.lastModified())
        }.filterNot { SyncAreas.isJunk(it.path) }.toList()
    }

    companion object {
        private const val TAG = "NxSync"

        /** [path] below [root]: relative, no `..` or empty segment, and still inside [root] once resolved. */
        fun below(root: File, path: String): File {
            val parts = path.split('/')
            require(path.isNotEmpty() && !path.startsWith('/') && '\\' !in path && parts.none { it.isEmpty() || it == "." || it == ".." }) {
                "not a plain relative path: $path"
            }
            val base = root.canonicalFile
            val file = File(base, path)
            require(file.canonicalPath.startsWith(base.path + File.separator)) { "outside its root: $path" }
            return file
        }
    }
}
