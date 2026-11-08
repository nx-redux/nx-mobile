package com.nxredux.mobile.scraper

/**
 * What the engine asks of the written-art log: whether the app wrote a home-relative path, dropping one it deleted, and
 * where a logged file came from ([origin]; null when unknown or not logged).
 */
interface LoggedArt {
    suspend fun contains(path: String): Boolean
    suspend fun remove(path: String)
    /** As [remove] for each of [paths], in one write where the log can. */
    suspend fun remove(paths: Collection<String>) { for (p in paths) remove(p) }
    suspend fun origin(path: String): ArtOrigin? = null
    /**
     * The screenshot at [path] is a SteamGridDB hero: its logged origin says so (the controller adds the hand-placed
     * heroes the one-time move kept, [MovedHeroes]).
     */
    suspend fun hero(path: String): Boolean = origin(path)?.source == SourceId.SGDB

    companion object {
        /** No log: nothing counts as the app's. */
        val NONE: LoggedArt = object : LoggedArt {
            override suspend fun contains(path: String) = false
            override suspend fun remove(path: String) {}
        }
    }
}

/**
 * The art files the app wrote, as home-relative paths (`Roms/GB/.media/boxart/Tetris.png`), each with where it came
 * from when known. Reset deletes only these, so hand-placed art is never touched. A path is added before its file is
 * written: a write cut short still resets.
 *
 * One line per file: `<path>\t<source>\t<game id>\t<asset id>` (an unknown id is empty); a path-only line (an older
 * log's, legacy mixes included) has no origin. A path may appear again further down (an appended [add]): the later line
 * wins, as [add] would. `<path>\t-` ([removedLine]) drops the path, until a later line adds it again.
 */
class WrittenArtLog(entries: Map<String, ArtOrigin?>) {
    private val map = LinkedHashMap(entries)
    /** A copy of the paths; [contains] for a lookup. */
    val entries: Set<String> get() = map.keys.toSet()
    val size: Int get() = map.size

    operator fun contains(path: String): Boolean = map.containsKey(path)

    fun origin(path: String): ArtOrigin? = map[path]

    /** Adds [path], or replaces its origin; a null [origin] keeps a known one. True when anything changed. */
    fun add(path: String, origin: ArtOrigin?): Boolean {
        if (path in map && (origin == null || map[path] == origin)) return false
        map[path] = origin
        return true
    }

    fun remove(paths: Collection<String>) { paths.forEach { map.remove(it) } }

    fun encode(): String = map.entries.joinToString("") { (path, o) -> line(path, o) }

    companion object {
        /** One entry's line, newline included; appended lines read back as a later [add] ([decode]: the last line of a path wins). */
        fun line(path: String, origin: ArtOrigin?): String =
            (if (origin == null) path else listOf(path, origin.source, origin.gameId.orEmpty(), origin.assetId.orEmpty()).joinToString("\t")) + "\n"

        /** The source field of a removal line; no source is named so. */
        private const val REMOVED = "-"

        /** An appended removal of [path], newline included ([decode] drops the path there). */
        fun removedLine(path: String): String = "$path\t$REMOVED\n"

        fun decode(text: String?): WrittenArtLog {
            val entries = LinkedHashMap<String, ArtOrigin?>()
            for (line in text.orEmpty().lineSequence()) {
                val parts = line.split('\t')
                val path = parts[0].trim()
                if (path.isEmpty()) continue
                val source = parts.getOrNull(1)?.trim().orEmpty()
                if (source.isEmpty()) { if (path !in entries) entries[path] = null; continue }   // as [add]: keeps a known origin
                if (source == REMOVED) { entries.remove(path); continue }
                entries[path] = ArtOrigin(
                    source, parts.getOrNull(2)?.trim()?.ifEmpty { null }, parts.getOrNull(3)?.trim()?.ifEmpty { null },
                )
            }
            return WrittenArtLog(entries)
        }
    }
}
