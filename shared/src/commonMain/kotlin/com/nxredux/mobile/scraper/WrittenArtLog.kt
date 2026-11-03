package com.nxredux.mobile.scraper

/**
 * What the engine asks of the written-art log: whether the app wrote a home-relative path, dropping one it deleted, and
 * where a logged file came from ([origin]; null when unknown or not logged).
 */
interface LoggedArt {
    suspend fun contains(path: String): Boolean
    suspend fun remove(path: String)
    suspend fun origin(path: String): ArtOrigin? = null

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
 * wins, as [add] would.
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

        fun decode(text: String?): WrittenArtLog {
            val entries = LinkedHashMap<String, ArtOrigin?>()
            for (line in text.orEmpty().lineSequence()) {
                val parts = line.split('\t')
                val path = parts[0].trim()
                if (path.isEmpty()) continue
                val source = parts.getOrNull(1)?.trim().orEmpty()
                if (source.isEmpty()) { if (path !in entries) entries[path] = null; continue }   // as [add]: keeps a known origin
                entries[path] = ArtOrigin(
                    source, parts.getOrNull(2)?.trim()?.ifEmpty { null }, parts.getOrNull(3)?.trim()?.ifEmpty { null },
                )
            }
            return WrittenArtLog(entries)
        }
    }
}
