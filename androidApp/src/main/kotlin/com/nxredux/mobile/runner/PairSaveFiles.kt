package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.cablelink.CheckpointFiles
import com.nxredux.mobile.netplay.cablelink.PairCheckpointFrames
import com.nxredux.mobile.netplay.cablelink.PairSaveCheckpoint
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.SaveCopyRules
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Where a Cable Link session keeps the player's last verified save until its end: app-private, one file per game's
 * save, so the menu process (the next session's save exchange) and the game process (the launch's promotion) find the
 * same one. Keyed by the save's folder and name without the save-format extension, so a format change between a crash
 * and the next launch still finds it.
 */
internal object PairSaveFiles {
    fun sessionFile(filesDir: File, paths: NxPaths): File =
        File(File(filesDir, "cablelink/checkpoints"), "${paths.savesDir}/${paths.saveName}.verified")

    /** The verified copy a session left behind for [paths] (its process died before the end); null when none. */
    fun pending(filesDir: File, paths: NxPaths): ByteArray? = sessionFile(filesDir, paths).takeIf { it.isFile }?.readBytes()

    /**
     * The save the next launch of the game under [root] boots, which is what the other phone must get: a pending
     * checkpoint (the launch promotes it), else the battery save as the launch loads it ([BatterySaves.load], which skips
     * a damaged or empty file for the next candidate).
     */
    fun launchSave(filesDir: File, root: File, paths: NxPaths): ByteArray? =
        pending(filesDir, paths) ?: BatterySaves(File(root, paths.savesDir), paths).load()

    /** Checkpoint and commit writes, in order, off the GL and main threads; the game process's end waits for it. */
    val writer: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "nx-pair-saves") }
}

/** [CheckpointFiles] over [session] (atomic writes) and the game's real save through [battery] (its naming and encoding). */
internal class AndroidCheckpointFiles(private val session: File, private val battery: BatterySaves) : CheckpointFiles {
    override fun readSession(): ByteArray? = session.takeIf { it.isFile }?.readBytes()
    override fun writeSession(bytes: ByteArray) = session.writeAtomically(bytes)
    override fun deleteSession() { session.delete() }

    /** An unchanged save counts as written; a damaged real save that is never overwritten does not (the session file stays). */
    override fun writeReal(bytes: ByteArray) {
        synchronized(battery) {
            if (!battery.write(bytes) && !battery.writable) throw IOException("the real save is damaged and is not overwritten")
        }
    }

    /**
     * At a launch: the session file a process that died mid-session left becomes the real save [real]. [battery] loads
     * first, so a damaged real save is set aside as a `.bak` before the promotion replaces it, as any first write does.
     */
    fun promotePending(real: File): PairSaveCheckpoint.Commit {
        if (!session.isFile) return PairSaveCheckpoint.Commit.NothingVerified
        synchronized(battery) { battery.load() }
        keepNewerReal(real)
        return PairSaveCheckpoint(this).promotePending()
    }

    /**
     * Before a promotion: a real save ([real]) changed after the session's last checkpoint (Device Sync, an import) is
     * copied aside as `<name>.<yyyyMMdd-HHmmss>.bak`, which the promotion then replaces. The copy, or null when none was due.
     */
    fun keepNewerReal(real: File): File? {
        if (!session.isFile || !real.isFile || real.lastModified() <= session.lastModified()) return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = real.parentFile ?: return null
        val bak = File(dir, SaveCopyRules.freeBackupName(real.name, stamp) { File(dir, it).exists() })
        real.copyTo(bak)
        NxLog.w(TAG_LOG, "${real.name} changed since the Cable Link session's last checkpoint; kept as ${bak.name}")
        return bak
    }
}

/**
 * One Cable Link launch's save checkpoints: snapshots paired with the drift checks' verdicts ([afterFrame], on the GL
 * thread), then exactly one commit at the session's end ([end]: an engine ending, Leave, or the screen going). Every
 * write is handed to [post] (one thread, in order) under this object's lock, so a verdict's write always lands before
 * the commit and none follows it.
 */
internal class PairSaveSession(private val checkpoint: PairSaveCheckpoint, private val post: (Runnable) -> Unit) {
    private val frames = PairCheckpointFrames()
    private var closed = false

    /** After a step: [runFrame] frames run, [verifiedFrame] the engine's last verified one, [readSave] the local save now. */
    fun afterFrame(runFrame: Long, verifiedFrame: Long, readSave: () -> ByteArray): Unit = synchronized(this) {
        if (closed) return
        frames.afterFrame(runFrame, verifiedFrame, readSave)?.let(::record)
    }

    /**
     * The session ended: [verifiedFrame] is the engine's last verified frame, read before its shutdown (null when it is
     * gone already), so a verdict still waiting is written first; then the commit. False when it had ended already.
     */
    fun end(verifiedFrame: Long?): Boolean = synchronized(this) {
        if (closed) return false
        closed = true
        verifiedFrame?.let(frames::verified)?.let(::record)
        post(Runnable {
            runCatching { checkpoint.commit() }
                .onSuccess {
                    when (it) {
                        PairSaveCheckpoint.Commit.Written -> NxLog.i(TAG_LOG, "pair save committed")
                        PairSaveCheckpoint.Commit.NothingVerified -> NxLog.i(TAG_LOG, "pair save: no verified checkpoint")
                        is PairSaveCheckpoint.Commit.NotWritten -> NxLog.e(TAG_LOG, "pair save not written; kept for the next launch", it.error)
                    }
                }
                .onFailure { NxLog.e(TAG_LOG, "pair save commit failed", it) }
        })
        true
    }

    private fun record(snapshot: PairCheckpointFrames.Snapshot) {
        post(Runnable {
            runCatching { checkpoint.onVerified(snapshot.frame, snapshot.save) }.onFailure { NxLog.e(TAG_LOG, "pair checkpoint not written", it) }
        })
    }
}
