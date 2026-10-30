package com.nxredux.mobile.netplay

import com.nxredux.mobile.netplay.wizard.SaveSink
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.findSave
import com.nxredux.mobile.storage.readSaveDecoded
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The client's (and a host on another core's) session copy of the saves: `files/netplay/session/`, outside `sd/` so
 * nothing in it is ever taken for the real saves. The host's saves arrive through [sink].
 *
 * Ownership (one copy folder; game screens may overlap in launcher mode): a [sink] commit leaves the copy unclaimed.
 * The game screen that plays on it [claim]s it, writing its own id to [OWNER_FILE]; a copy another screen had claimed is
 * stale (that screen's session is over) and is emptied first. At its end a screen [release]s the copy, which deletes it
 * only while the marker still holds that screen's id, so an old screen torn down after a new launch never deletes the
 * new session's copy. The app-start sweep ([sweepOnce]) runs once per process: at a process's first start no session of
 * that process exists yet, and a later MainActivity (recreated mid-session, or between a commit and its launch) never
 * touches a live or just-committed copy. Games play in their own process, so a menu process restarted mid-game skips it
 * while a game process lives: the copy may be that game's.
 *
 * One JVM lock (this object's monitor) covers [claim], [release], the sweep, a sink's setup and its begin / commit /
 * abort, and a host's delete + claim + copy ([prepareHost], called from GameActivity), so a screen's release or a new
 * join's sink never interleaves with another screen's handoff.
 */
object NetplaySessionDir {
    const val OWNER_FILE = ".owner"
    private val sweptThisProcess = AtomicBoolean(false)

    fun dir(files: File) = File(files, "netplay/session")
    private fun staging(files: File) = File(files, "netplay/.staging")

    /** The id of the game screen that claimed the copy; null when unclaimed or absent. */
    fun ownerOf(files: File): String? = File(dir(files), OWNER_FILE).takeIf { it.isFile }?.readText()

    /** Game screen [owner] takes the copy (creating it empty when there is none); one another screen claimed is emptied. */
    @Synchronized
    fun claim(files: File, owner: String) {
        val d = dir(files)
        val current = ownerOf(files)
        if (current != null && current != owner) d.deleteRecursively()
        if (!d.isDirectory && !d.mkdirs()) throw IOException("cannot create ${d.name}")
        File(d, OWNER_FILE).writeText(owner)
    }

    /** Deletes the copy if [owner] still holds it; true when it was deleted. */
    @Synchronized
    fun release(files: File, owner: String): Boolean = ownerOf(files) == owner && dir(files).deleteRecursively()

    /**
     * A host's session copy, under this object's lock (so no other screen's release or a joiner's sink lands in between):
     * the copy is emptied and claimed by [owner]; with [savesDir] (`<root>/Saves/<tag>`) the game's save, found under
     * any handheld name and RZIP decoded, is copied in as `<stem>.srm` (the session's own default names). [savesDir]
     * null: the host plays on empty saves (no transfer happened). False when the copy failed: the copy is then
     * empty, still claimed, and [onCopyFailed] gets the cause (the caller logs it).
     */
    @Synchronized
    fun prepareHost(files: File, owner: String, savesDir: File?, paths: NxPaths, onCopyFailed: (Throwable) -> Unit = {}): Boolean {
        val d = dir(files)
        d.deleteRecursively()
        claim(files, owner)
        if (savesDir == null) return true
        val copied = runCatching {
            val found = paths.findSave(savesDir.parentFile!!.parentFile!!)   // the candidates are relative to <root>
            val bytes = found?.readSaveDecoded()
            if (bytes != null) File(d, "${paths.stem}.srm").writeBytes(bytes)
        }
        val cause = copied.exceptionOrNull() ?: return true
        onCopyFailed(cause)
        d.deleteRecursively()   // never a half copy
        claim(files, owner)
        return false
    }

    /** [sweep], at most once per process (per [gate]), and not while [gameLive] (the game process may be playing on the copy). */
    fun sweepOnce(files: File, gate: AtomicBoolean = sweptThisProcess, gameLive: () -> Boolean) {
        if (gate.compareAndSet(false, true) && !gameLive()) sweep(files)
    }

    /** Drops a stale session copy and any half-received staging. */
    @Synchronized
    fun sweep(files: File) {
        staging(files).deleteRecursively()
        dir(files).deleteRecursively()
    }

    /**
     * Stages `nxsave.<ext>` files pulled by [puller] (the bundled rsync; null when there is none: [SaveSink.canPull] false)
     * in `netplay/.staging/`; [SaveSink.commit] renames them to `<stem>.<ext>` and swaps the staging folder in as a fresh
     * `netplay/session/` with one rename, so the session copy is the whole set or nothing.
     *
     * Every join attempt starts from nothing: creating the sink (once per attempt, before the handshake, off the main
     * thread) deletes the session copy and any staging, whoever owned them. A handshake with no saves so leaves no copy
     * (the game screen's [claim] then makes an empty one), one with saves commits a fresh one: a joiner never boots on
     * another session's files. A screen still holding the old copy is about to be replaced by this join; its [release]
     * then does not find its own marker, so it never deletes the new copy.
     */
    fun sink(files: File, rom: String, puller: SavePuller?): SaveSink = object : SaveSink {
        private val stem = NxPaths("X", "x", rom).stem
        private val stage = staging(files)
        private val session = dir(files)

        init {
            synchronized(NetplaySessionDir) {
                stage.deleteRecursively()
                session.deleteRecursively()
            }
        }

        override val canPull: Boolean get() = puller != null

        override fun begin() = synchronized(NetplaySessionDir) {
            stage.deleteRecursively()
            if (!stage.mkdirs()) throw IOException("cannot create ${stage.name}")
        }

        override suspend fun pull(hostIp: String, wireName: String) {
            requireNotNull(NetplaySaves.localName(wireName, stem)) { "not a save: $wireName" }
            val p = puller ?: throw IOException("no rsync on this device")
            p.pull(hostIp, wireName, stage)
            if (!File(stage, wireName).isFile) throw IOException("rsync left no $wireName")
        }

        override fun commit() = synchronized(NetplaySessionDir) {
            try {
                stage.listFiles().orEmpty().forEach { f ->
                    val local = NetplaySaves.localName(f.name, stem) ?: throw IOException("not a save: ${f.name}")
                    if (!f.renameTo(File(stage, local))) throw IOException("rename failed: ${f.name}")
                }
                session.deleteRecursively()
                if (session.exists() || !stage.renameTo(session)) throw IOException("cannot promote the session copy")
            } catch (e: Exception) {
                stage.deleteRecursively()
                session.deleteRecursively()
                throw e
            }
        }

        override fun abort() {
            synchronized(NetplaySessionDir) { stage.deleteRecursively() }
        }
    }
}
