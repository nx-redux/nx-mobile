package com.nxredux.mobile.library

/**
 * Which entry of a ROM archive is the ROM. Directory entries, anything under `__MACOSX/` and dotfiles are never
 * candidates. A lone candidate is the ROM whatever its extension, so the launch can name the real inner file when the
 * core does not run it. Among several candidates only those with one of [pick]'s `romExtensions` (the core's) count, as
 * the Brick picks the entry matching the core: a ROM beside a readme plays, while two ROMs or none are refused. A disc
 * sheet among other entries is refused on its own ([Pick.Sheet]): only one entry is unpacked, so its tracks would be left
 * behind, and a core whose extensions do not include the tracks' would boot with no disc.
 */
object ArchiveEntries {
    sealed class Pick {
        /** [entry] is the full entry path inside the archive; [fileName] its last segment. */
        data class Single(val entry: String) : Pick() {
            val fileName: String get() = entry.substringAfterLast('/')
        }
        data object Empty : Pick()
        /** No single ROM among several candidates; [names] are all the candidates' base names, in archive order. */
        data class Many(val names: List<String>) : Pick() {
            val count: Int get() = names.size
        }
        /** A disc sheet ([sheet], its base name) packed with other entries, its tracks among them. */
        data class Sheet(val sheet: String) : Pick()
    }

    private val SHEET_EXTENSIONS = setOf("cue", "ccd", "gdi", "toc", "m3u")

    fun pick(entryNames: List<String>, romExtensions: Set<String>): Pick {
        val candidates = entryNames.filter(::isCandidate)
        if (candidates.isEmpty()) return Pick.Empty
        if (candidates.size == 1) return Pick.Single(candidates[0])
        candidates.firstOrNull { Names.extensionOf(it.substringAfterLast('/')) in SHEET_EXTENSIONS }
            ?.let { return Pick.Sheet(it.substringAfterLast('/')) }
        val roms = candidates.filter { Names.extensionOf(it.substringAfterLast('/')) in romExtensions }
        return if (roms.size == 1) Pick.Single(roms[0]) else Pick.Many(candidates.map { it.substringAfterLast('/') })
    }

    /**
     * The name the picked entry [innerFileName] is unpacked under. Normally its own; when [namedAfterArchive] (a core
     * that names its saves after the content file, [com.nxredux.mobile.runner.CoreDescriptor.savesNamedAfterContent])
     * the archive's stem with the inner extension (`Crazy Taxi (USA).zip` holding `disc.cdi` unpacks as
     * `Crazy Taxi (USA).cdi`), so flycast's `<content>.A1.bin` VMU is the game's stem like every other save, and two
     * archives holding the same inner name never share one. An inner name with no extension is kept.
     */
    fun unpackedName(archiveName: String, innerFileName: String, namedAfterArchive: Boolean): String {
        if (!namedAfterArchive) return innerFileName
        val dot = innerFileName.lastIndexOf('.')
        if (dot <= 0) return innerFileName
        return Names.stripExtension(archiveName) + innerFileName.substring(dot)
    }

    private fun isCandidate(entry: String): Boolean {
        if (entry.isEmpty() || entry.endsWith("/")) return false
        if (entry.startsWith("__MACOSX/") || entry.contains("/__MACOSX/")) return false
        return !entry.substringAfterLast('/').startsWith(".")
    }
}
