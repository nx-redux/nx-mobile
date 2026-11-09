package com.nxredux.mobile.netplay.cablelink

/** The files behind one game's Cable Link save checkpoints. */
interface CheckpointFiles {
    /** The private session file: the last verified copy of this player's save; null when there is none. */
    fun readSession(): ByteArray?
    /** Replaces the session file in one step, so a kill mid-write leaves the previous copy. */
    fun writeSession(bytes: ByteArray)
    fun deleteSession()
    /** Writes the player's real save the way the game's own saves are written; throws when it could not. */
    fun writeReal(bytes: ByteArray)
}

/**
 * A Cable Link session's save: the real save is written only with data both phones agreed on. Each verified
 * checkpoint ([onVerified]) replaces the private session file, and the session's end ([commit]) moves that file to the
 * real save. When the process dies first, the next launch of the game moves it ([promotePending]). Not thread-safe:
 * the caller runs every call on one thread.
 */
class PairSaveCheckpoint(private val files: CheckpointFiles) {
    private var verifiedFrame = -1L

    /** The local GBA's save as it was at [frame], which both phones' hashes matched; an older frame than one kept is ignored. */
    fun onVerified(frame: Long, localSaveRam: ByteArray) {
        if (frame <= verifiedFrame) return
        files.writeSession(localSaveRam)
        verifiedFrame = frame
    }

    /**
     * Writes the last verified copy to the real save and clears the session file. False when there was none, or the real
     * save could not be written (the session file then stays for the next launch).
     */
    fun commit(): Boolean {
        val bytes = files.readSession() ?: return false
        try {
            files.writeReal(bytes)
        } catch (e: Exception) {
            return false
        }
        files.deleteSession()
        return true
    }

    /** At a launch: a session file left by a process that died mid-session becomes the real save. */
    fun promotePending(): Boolean = commit()
}

/**
 * Pairs the local save with the drift checks' verdicts on the frame thread. At each check frame (a non-zero multiple of
 * [CHECK_INTERVAL] frames run) it keeps a snapshot of the save; once the engine's last verified frame equals a kept one,
 * that snapshot is the checkpoint. A snapshot the verified frame passes over (a mismatch, a resync) is dropped. Not
 * thread-safe.
 */
class PairCheckpointFrames {
    class Snapshot(val frame: Long, val save: ByteArray)

    private val kept = ArrayDeque<Snapshot>()

    /**
     * After a lockstep frame: [runFrame] frames run so far, [verifiedFrame] the engine's last verified frame, [readSave]
     * the local save now. The snapshot verified by this call, if any.
     */
    fun afterFrame(runFrame: Long, verifiedFrame: Long, readSave: () -> ByteArray): Snapshot? {
        if (runFrame > 0 && runFrame % CHECK_INTERVAL == 0L && kept.lastOrNull()?.frame != runFrame) {
            kept.addLast(Snapshot(runFrame, readSave()))
            while (kept.size > MAX_KEPT) kept.removeFirst()
        }
        return verified(verifiedFrame)
    }

    /** The snapshot [verifiedFrame] verifies, if one is kept; snapshots at or before it go either way. */
    fun verified(verifiedFrame: Long): Snapshot? {
        var hit: Snapshot? = null
        while (kept.isNotEmpty() && kept.first().frame <= verifiedFrame) {
            val s = kept.removeFirst()
            if (s.frame == verifiedFrame) hit = s
        }
        return hit
    }

    companion object {
        /** The engine's drift-check interval (np_engine.h NP_PAIR_CHECK_INTERVAL). */
        const val CHECK_INTERVAL = 120L
        /** A verdict arrives a few frames after its check: two in flight is already a stall. */
        private const val MAX_KEPT = 4
    }
}
