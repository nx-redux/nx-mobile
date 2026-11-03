package com.nxredux.mobile.sync

import com.nxredux.mobile.library.IndexedGame
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.storage.GamePath
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One game in a phone's extra folder that ROM sync sends to the other device.
 * [extra] is the extra's position `n` (its daemon module is `extra-<n>`); [tag] is the system tag, uppercase.
 * [parent] is the dir inside the extra that holds the unit (`""` = the extra's root).
 * [names] is what to send from [parent]: a disc folder's folder name; a loose game's file, then its companions.
 * [landing] is the name in the receiving console folder.
 * [key] is the sender's `hashes.json` key (`.sources/<id>/<dir>/<file>`, as `RaFingerprints.of`).
 * [landingKey] is that key after landing (`<file>`, or `<folder>/<file>` for a disc folder).
 * [art] is the art paths relative to the sender's home `Roms/` (`.sources/<id>/…/.media/<slot>/<name>`).
 */
@Serializable
data class ExtraUnit(
    val extra: Int, val tag: String, val parent: String, val names: List<String>, val landing: String,
    val key: String, val landingKey: String, val art: List<String>,
)

/** A phone's extra-folder games (its `nx-extras/extras.json` when it serves). */
@Serializable
data class ExtrasManifest(val version: Int = 1, val units: List<ExtraUnit> = emptyList())

/** Extra folder [id] at real [path], numbered [n] (its daemon module is `extra-<n>`). */
data class ExtraRoot(val n: Int, val id: String, val path: String)

/** What of a phone's extra folders travels with ROM sync. */
object SyncExtras {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Every assigned extra-folder game of [index] whose extra is in [roots] (a real path). A disc-folder game
     * (`lookupDir != dir`) is its folder; a loose game is its file plus [companions] (names beside it). Art is every
     * index art ref under [homeRoms]`/.sources/`, relative to [homeRoms].
     */
    suspend fun units(index: LibraryIndex, roots: List<ExtraRoot>, homeRoms: String, companions: suspend (IndexedGame) -> List<String>): ExtrasManifest {
        val byId = roots.associateBy { it.id }
        val sourcesPrefix = "file://" + homeRoms.trimEnd('/') + "/"
        val out = ArrayList<ExtraUnit>()
        for (sys in index.systems) {
            if (sys.unassigned) continue
            for (g in sys.games) {
                if (g.source == GamePath.HOME) continue
                val root = byId[g.source] ?: continue
                val dir = g.dir.split('/').filter { it.isNotEmpty() }
                val folderGame = g.lookupDir != g.dir && dir.isNotEmpty()
                val parent = (if (folderGame) dir.dropLast(1) else dir).joinToString("/")
                val names = if (folderGame) listOf(dir.last()) else listOf(g.file) + companions(g).filter { it != g.file }
                val landing = if (folderGame) dir.last() else g.file
                val key = (listOf(".sources", g.source) + dir + g.file).joinToString("/")
                val landingKey = if (folderGame) "${dir.last()}/${g.file}" else g.file
                val art = listOfNotNull(g.boxart, g.box2d, g.screenshot, g.wheel, g.mix)
                    .filter { it.startsWith("$sourcesPrefix.sources/") }.map { it.removePrefix(sourcesPrefix) }
                out += ExtraUnit(root.n, sys.tag.uppercase(), parent, names, landing, key, landingKey, art)
            }
        }
        return ExtrasManifest(units = out)
    }

    fun encode(m: ExtrasManifest): String = json.encodeToString(ExtrasManifest.serializer(), m)

    /** The manifest in [text]; an absent or unreadable text is an empty manifest. */
    fun decode(text: String?): ExtrasManifest =
        text?.let { runCatching { json.decodeFromString(ExtrasManifest.serializer(), it) }.getOrNull() } ?: ExtrasManifest()
}
