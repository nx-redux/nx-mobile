package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Art layout inside a folder's `.media`, each `.png`, else `.jpg`:
 *   - 3D box art:      .media/boxart/<name>
 *   - 2D front cover:  .media/boxart2d/<name> (NX Redux's folder), else the legacy .media/box2d/<name>
 *   - screenshot:      .media/screenshot/<name>
 *   - SteamGridDB's grids and heroes are written as 2D boxes and screenshots; `.media/grid/` and `.media/hero/` are not read
 *   - wheel (logo):    .media/wheel/<name>  (known, not shown)
 *   - mix:             .media/mix/<name>    (NX Redux's mix; known, not shown)
 *   - folder bg:       .media/bg.png   (key "bg" in the top-level map)
 * NX Redux's old untyped mix `.media/<name>.png` is not read for games; the top-level map still gives a system its
 * `Roms/.media/<System folder>.png` and a folder its bg. Folders (systems) use their full name; files use the name
 * without extension.
 */
class ArtworkResolver(private val source: RomsSource) {
    companion object {
        /** The typed `.media` subfolders a game's art is read from. */
        val TYPED = listOf("boxart", "boxart2d", "box2d", "screenshot", "wheel", "mix")
        private val JPEG = listOf(".jpg", ".jpeg")
    }

    /** Children of a folder's `.media` (listed through [list]: a scan passes its shared listings), or null if there is none. */
    suspend fun mediaListing(children: List<Entry>, list: suspend (ContentRef) -> List<Entry> = { source.listChildren(it) }): List<Entry>? {
        val media = children.firstOrNull { it.isDirectory && it.name == ".media" } ?: return null
        return list(media.ref)
    }

    /**
     * Each typed `.media` subfolder's [subArtMap] as last read, by the subfolder's ref, with the modified time its parent's
     * listing gave it then. [subArtMap] with `reuse` takes the kept map while that time is unchanged: a map holds only names
     * and refs, and a file added, deleted or renamed in a folder (a scraper's `.part` swap included) bumps the folder's
     * modified time on the storage the app reads (internal storage and SD cards, through FUSE or SAF's external storage
     * provider; a file overwritten in place keeps its name and ref, so its map is still right). A folder whose provider reports no time is always listed.
     * Shared by a scan (which fills it) and a later [LibraryScanner.readArt] (which reuses it), so it is locked.
     */
    class SubArtMaps {
        private val lock = Mutex()
        private val maps = HashMap<ContentRef, Pair<Long, Map<String, Entry>>>()

        /** [dir]'s kept map, when it was read at [dir]'s current modified time. */
        suspend fun unchanged(dir: Entry): Map<String, Entry>? {
            val modified = dir.lastModified ?: return null
            return lock.withLock { maps[dir.ref] }?.takeIf { it.first == modified }?.second
        }

        suspend fun put(dir: Entry, map: Map<String, Entry>) {
            lock.withLock { if (dir.lastModified == null) maps.remove(dir.ref) else maps[dir.ref] = dir.lastModified to map }
        }

        /** Forgets every map: a full scan reads each folder again and keeps only those it reaches. */
        suspend fun clear() = lock.withLock { maps.clear() }
    }

    /** baseName -> ref for every .png directly in a listing. */
    fun pngMap(entries: List<Entry>): Map<String, ContentRef> =
        entries.filter { !it.isDirectory && it.name.endsWith(".png", ignoreCase = true) }
            .associate { it.name.dropLast(4) to it.ref }

    /**
     * baseName -> ref for the files in `.media/<sub>` (one of [TYPED]): `.png`, else `.jpg`, else `.jpeg` (the extension
     * in any case), whatever the listing order; the scrape engine reads a slot the same way.
     */
    suspend fun subArtMap(mediaEntries: List<Entry>, sub: String, maps: SubArtMaps? = null, reuse: Boolean = false): Map<String, ContentRef> =
        subArtFiles(mediaEntries, sub, maps, reuse).mapValues { it.value.ref }

    /** As [subArtMap], with each name's chosen file (its full name: the written-art log's key). */
    suspend fun subArtFiles(mediaEntries: List<Entry>, sub: String, maps: SubArtMaps? = null, reuse: Boolean = false): Map<String, Entry> {
        val dir = mediaEntries.firstOrNull { it.isDirectory && it.name == sub } ?: return emptyMap()
        if (reuse) maps?.unchanged(dir)?.let { return it }
        val files = source.listChildren(dir.ref).filter { !it.isDirectory }
        fun withExt(ext: String) = files.filter { it.name.endsWith(ext, ignoreCase = true) && it.name.length > ext.length }
            .associateBy { it.name.dropLast(ext.length) }
        // Lowest priority first: each later map overrides the names it shares.
        return (JPEG.reversed().fold(emptyMap<String, Entry>()) { m, ext -> m + withExt(ext) } + withExt(".png")).also { maps?.put(dir, it) }
    }

    /** Top-level `.media` map from a folder's already-listed children. */
    suspend fun mediaMap(children: List<Entry>, list: suspend (ContentRef) -> List<Entry> = { source.listChildren(it) }): Map<String, ContentRef> =
        pngMap(mediaListing(children, list) ?: emptyList())

    fun artFor(map: Map<String, ContentRef>, entryName: String, isDirectory: Boolean): ContentRef? =
        map[if (isDirectory) entryName else Names.stripExtension(entryName)]
}
