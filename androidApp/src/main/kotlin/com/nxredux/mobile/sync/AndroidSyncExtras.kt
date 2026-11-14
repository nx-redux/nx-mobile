package com.nxredux.mobile.sync

import android.content.Context
import com.nxredux.mobile.library.ArcadeCompanions
import com.nxredux.mobile.library.CompanionLookup
import com.nxredux.mobile.library.DiscSheet
import com.nxredux.mobile.library.IndexedGame
import com.nxredux.mobile.library.MsuSets
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ArcadeSetsAsset
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.NaomiSetsAsset
import com.nxredux.mobile.settings.ExtraFolder
import com.nxredux.mobile.storage.FileRefs
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CancellationException
import java.io.File

/** The Android side of ROM sync's extra-folder games: which extras are real folders, and each game's companion files. */
object AndroidSyncExtras {
    private const val TAG = "NxSync"

    /** The extras that are file refs to an existing folder, numbered in order (module `extra-<n>`); others are skipped. */
    fun roots(extras: List<ExtraFolder>): List<ExtraRoot> {
        val out = ArrayList<ExtraRoot>()
        for (e in extras) {
            if (!FileRefs.isFile(e.ref)) { NxLog.w(TAG, "extra folder ${e.id} is not a file folder; its games are not synced"); continue }
            val path = FileRefs.path(e.ref)
            if (!File(path).isDirectory) { NxLog.w(TAG, "extra folder ${e.id} is gone; its games are not synced"); continue }
            out += ExtraRoot(out.size, e.id, path)
        }
        return out
    }

    /**
     * The names beside [g] that its core loads with it: a disc sheet's files, an MSU set's music and data, an fbneo set's
     * parent and BIOS sets beside it, a flycast archive's parent. Mirrors the launch path (AppState). A missing file leaves just [g]'s own name.
     */
    suspend fun companions(context: Context, source: RomsSource, g: IndexedGame, tag: String): List<String> {
        try {
            val folder = ContentRef(g.folder)
            val names = ArrayList<String>()
            (DiscSheet.companions(source, folder, g.file) as? CompanionLookup.Found)?.companions?.forEach { names += it.fileName }
            names += MsuSets.loadedNames(source, folder, g.file)
            val core = CoreRegistry.forTag(tag)
            if (core?.id == "fbneo" && CoreRegistry.loadsArchive(tag, g.file)) {
                ArcadeCompanions.find(source, folder, g.file, ArcadeSetsAsset.get(context)).companions.forEach { names += it.fileName }
            } else if (core?.id == "flycast") {
                val sets = NaomiSetsAsset.get(context)
                if (CoreRegistry.loadsArchive(tag, g.file, sets)) ArcadeCompanions.findParent(source, folder, g.file, sets).forEach { names += it.fileName }
            }
            return names
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NxLog.w(TAG, "companions of ${g.file} unreadable; sending the file alone", e)
            return emptyList()
        }
    }
}
