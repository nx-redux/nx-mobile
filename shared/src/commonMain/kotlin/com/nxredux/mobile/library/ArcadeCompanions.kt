package com.nxredux.mobile.library

import com.nxredux.mobile.runner.Companion
import com.nxredux.mobile.runner.ContentRef

/**
 * Parent and BIOS sets an arcade set loads: the ones beside it go to the core as companions; of the rest only the BIOS
 * sets are [systemSets], which must be in the system dir (a clone from a non-merged set carries its parent's ROMs).
 */
data class ArcadeLookup(val companions: List<Companion>, val systemSets: List<String>)

object ArcadeCompanions {
    /**
     * [fileName]'s set chain ([ArcadeSets.chain]) looked up by name, then case-insensitively in [folder]'s listing, zip
     * before 7z. A found set is a companion named `<set>.<ext>` in lowercase: fbneo opens `<rom dir>/<set>.zip` and the
     * runner's VFS matches names exactly. A BIOS set ([ArcadeSets.isBios]) not in the folder is left in
     * [ArcadeLookup.systemSets] (Bios/FBN/ is copied into the system dir); a missing non-BIOS parent is dropped, and the
     * core tries without it.
     */
    suspend fun find(source: RomsSource, folder: ContentRef, fileName: String, sets: ArcadeSets): ArcadeLookup {
        val chain = sets.chain(Names.stripExtension(fileName))
        if (chain.isEmpty()) return ArcadeLookup(emptyList(), emptyList())
        val found = beside(source, folder, chain)
        return ArcadeLookup(chain.mapNotNull { found[it] }, chain.filter { it !in found && sets.isBios(it) })
    }

    /**
     * A NAOMI/Atomiswave clone's parent set beside it, as a companion `<parent>.zip` (else `.7z`) in lowercase: flycast opens
     * `<rom dir>/<parent>.zip` (naomi_cart.cpp, parent_name). Empty for a set with no parent ([NaomiSets.parent]) or one not
     * beside it: a non-merged clone carries its parent's ROMs, and flycast reports its own error otherwise.
     */
    suspend fun findParent(source: RomsSource, folder: ContentRef, fileName: String, sets: NaomiSets): List<Companion> {
        val parent = sets.parent(Names.stripExtension(fileName)) ?: return emptyList()
        return listOfNotNull(beside(source, folder, listOf(parent))[parent])
    }

    /** Each of [names] (lowercase sets) found in [folder] as `<set>.zip`, else `.7z`: by name, then case-insensitively in its listing. */
    private suspend fun beside(source: RomsSource, folder: ContentRef, names: List<String>): Map<String, Companion> {
        val files = FileLookup(source, folder)
        val found = LinkedHashMap<String, Companion>()
        for (set in names) {
            for (ext in EXTENSIONS) {
                val e = files.find("$set.$ext") ?: continue
                found[set] = Companion("$set.$ext", e.ref)
                break
            }
        }
        return found
    }

    /**
     * The first of [systemSets] with no `<set>.zip`/`.7z` in the system dir's or its `fbneo/` folder's listing; null when
     * all are there. Names match exactly, in lowercase: the app-private system dir is case-sensitive and fbneo opens
     * `<system>/<set>.zip`, so a `NeoGeo.zip` there counts as missing and gets the friendly refusal.
     */
    fun missing(systemSets: List<String>, systemFiles: Set<String>, fbneoFiles: Set<String>): String? {
        val present = systemFiles + fbneoFiles
        return systemSets.firstOrNull { set -> EXTENSIONS.none { "$set.$it" in present } }
    }

    private val EXTENSIONS = listOf("zip", "7z")
}
