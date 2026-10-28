package com.nxredux.mobile.runner

import com.nxredux.mobile.cheats.CheatFolders
import com.nxredux.mobile.cheats.CheatResolver
import com.nxredux.mobile.cheats.CheatSet
import com.swordfish.libretrodroid.NxLog
import java.io.File

/** Cheat files under [com.nxredux.mobile.tools.CheatsRoot] (RetroArch `.cht` files in `Cheats/<TAG>/`, where the Cheats & Patches tool unpacks too). */
object Cheats {
    /** The folder the Cheats page names for [romFileName] under [tag] ([CheatFolders.primary]). */
    fun folder(tag: String, romFileName: String) = CheatFolders.primary(tag, romFileName)

    /**
     * The cheat set for [romFileName] shown as [title], from the first of its folders ([CheatFolders.tagsFor]) that yields
     * any; [serial] is the game's PSP serial when it was read ([PspSerialReader]). Blocking I/O (a listing plus a few
     * small reads per folder); never throws.
     */
    fun resolve(sdRoot: File, tag: String, romFileName: String, title: String, coreExtensions: Set<String>, serial: String? = null): CheatSet {
        var set = CheatSet.EMPTY
        for (folderTag in CheatFolders.tagsFor(tag, romFileName)) {
            set = resolveIn(File(sdRoot, "Cheats/$folderTag"), romFileName, title, coreExtensions, serial)
            if (set.entries.isNotEmpty()) {
                NxLog.i("Cheats", "$romFileName: ${set.entries.size} cheats from Cheats/$folderTag" + (serial?.let { " (serial $it)" } ?: ""))
                break
            }
        }
        return set
    }

    private fun resolveIn(dir: File, romFileName: String, title: String, coreExtensions: Set<String>, serial: String?): CheatSet {
        val files = runCatching {
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(".cht", ignoreCase = true) }?.map { it.name }.orEmpty()
        }.getOrDefault(emptyList())
        return CheatResolver.resolve(
            files, romFileName, title, coreExtensions,
            read = { name ->
                runCatching { File(dir, name).readText() }.onFailure { NxLog.w("Cheats", "cannot read $name", it) }.getOrNull()
            },
            serial = serial,
            onSkipped = { name, reason -> NxLog.w("Cheats", "skipping $name: $reason") },
        )
    }
}
