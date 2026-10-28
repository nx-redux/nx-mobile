package com.nxredux.mobile.tools

import android.content.Context
import com.nxredux.mobile.cheats.db.CheatDbManifest
import com.nxredux.mobile.storage.DataRoot
import com.swordfish.libretrodroid.NxLog
import java.io.File
import java.io.IOException

/**
 * Where `Cheats/<TAG>/` and the Cheats & Patches manifest live: the data root ([DataRoot]), so an uninstall keeps the
 * download and the folders match the handheld's card. [moveFromPrivate] is the cheats' rule in [DataRoot]'s move.
 */
object CheatsRoot {
    private const val TAG = "CheatsRoot"
    private const val CHEATS = "Cheats"

    /** The root to read and write cheats in: [DataRoot.of]. */
    fun of(context: Context): File = DataRoot.of(context)

    /** [of] once a move it needs has finished: one root for a whole install. Blocks, so never on the main thread. */
    fun settled(context: Context): File = DataRoot.settled(context)

    /**
     * Moves [private]'s `Cheats/` files and the manifest into [home], never over a file there: a private copy identical
     * to the home's is dropped, a different one stays where it was. When [home] has a database of its own, the private
     * one (the files its manifest lists, and the manifest) is deleted instead, so no manifest names files it doesn't
     * have. Everything is copied before anything is deleted, the manifest last. Emptied private folders are removed.
     * Returns the files moved.
     */
    fun moveFromPrivate(private: File, home: File): Int {
        if (private.canonicalFile == home.canonicalFile) return 0
        val cheats = File(private, CHEATS)
        val manifest = File(private, CheatDbManifest.PATH)
        val homeHasDatabase = File(home, CheatDbManifest.PATH).isFile
        val download: Set<String> = if (homeHasDatabase && manifest.isFile) {
            runCatching { CheatDbManifest.parse(manifest.readText())?.files.orEmpty().toSet() }.getOrDefault(emptySet())
        } else emptySet()
        var moved = 0
        val gone = cheats.walkTopDown().filter { it.isFile }.toList().filter { f ->
            val rel = f.relativeTo(private).path
            if (rel in download) return@filter true   // the home's own database replaces it
            when (copy(f, File(home, rel))) {
                Copy.COPIED -> { moved++; true }
                Copy.SAME -> true
                Copy.KEPT -> false
            }
        }
        val manifestGoes = manifest.isFile && (homeHasDatabase ||
            when (copy(manifest, File(home, CheatDbManifest.PATH))) { Copy.COPIED -> { moved++; true }; Copy.SAME -> true; Copy.KEPT -> false })
        gone.forEach { it.delete() }
        if (manifestGoes) manifest.delete()
        cheats.walkBottomUp().filter { it.isDirectory }.forEach { it.delete() }   // only empty ones go
        manifest.parentFile?.delete()
        return moved
    }

    private enum class Copy { COPIED, SAME, KEPT }

    private fun copy(from: File, to: File): Copy {
        if (to.exists()) {
            // Lengths first (in DataRoot.sameContent): a home database from another device differs in most files.
            return if (DataRoot.sameContent(to, from)) Copy.SAME else Copy.KEPT
        }
        // Staged beside it first, so a kill mid-copy never leaves a cut-off file under the real name (the next move drops it).
        val staged = File(to.parentFile, ".${to.name}${DataRoot.STAGED}")
        return try {
            to.parentFile?.mkdirs()
            from.copyTo(staged, overwrite = true)
            if (!staged.renameTo(to)) throw IOException("cannot place ${to.path}")
            Copy.COPIED
        } catch (e: IOException) {
            NxLog.w(TAG, "could not move ${from.name}", e)
            staged.delete()
            Copy.KEPT
        }
    }
}
