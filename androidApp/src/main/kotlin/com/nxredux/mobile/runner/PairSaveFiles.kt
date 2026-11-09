package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.cablelink.CheckpointFiles
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Where a Cable Link session keeps the player's last verified save until its end: app-private, one file per real save
 * ([saveRam] is NxPaths.saveRam), so the menu process (the next session's save exchange) and the game process (the
 * launch's promotion) find the same one.
 */
internal object PairSaveFiles {
    fun sessionFile(filesDir: File, saveRam: String): File = File(File(filesDir, "cablelink/checkpoints"), saveRam)

    /** The verified copy a session left behind for [saveRam] (its process died before the end); null when none. */
    fun pending(filesDir: File, saveRam: String): ByteArray? = sessionFile(filesDir, saveRam).takeIf { it.isFile }?.readBytes()

    /** Checkpoint and commit writes, in order, off the GL and main threads. */
    val writer: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "nx-pair-saves") }
}

/** [CheckpointFiles] over [session] (atomic writes) and the game's real save through [battery] (its naming and encoding). */
internal class AndroidCheckpointFiles(private val session: File, private val battery: BatterySaves) : CheckpointFiles {
    override fun readSession(): ByteArray? = session.takeIf { it.isFile }?.readBytes()
    override fun writeSession(bytes: ByteArray) = session.writeAtomically(bytes)
    override fun deleteSession() { session.delete() }
    override fun writeReal(bytes: ByteArray) { synchronized(battery) { battery.write(bytes) } }
}
