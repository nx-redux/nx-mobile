package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef

/**
 * Folders below [root] by path, each segment looked up once: a scan asks for many paths sharing a prefix
 * (`Roms/.sources/<id>/…`), and a missing prefix answers every longer path with no further lookup. One scan only: a
 * folder created afterwards is not seen.
 */
internal class DirWalk(private val source: RomsSource, val root: ContentRef) {
    private val dirs = HashMap<List<String>, ContentRef?>()

    /** The folder at [segments] below [root]; null when a segment is missing or a file. */
    suspend fun dir(segments: List<String>): ContentRef? {
        if (segments.isEmpty()) return root
        if (segments in dirs) return dirs[segments]
        val parent = dir(segments.dropLast(1))
        return parent?.let { source.child(it, segments.last()) }?.takeIf { it.isDirectory }?.ref.also { dirs[segments] = it }
    }
}

/**
 * One scan's reads of the trees: each folder listed once ([list]; the walk, the disc folders and the art and name
 * lookups share it), the home's paths walked once ([home]), and sidecars read from those listings ([sidecars]).
 */
internal class ScanReads(private val source: RomsSource, sidecars: Sidecars, home: ContentRef?) {
    val home: DirWalk? = home?.let { DirWalk(source, it) }
    val sidecars: Sidecars.Scan = sidecars.scan(this.home)
    private val listings = HashMap<ContentRef, List<Entry>>()

    suspend fun list(dir: ContentRef): List<Entry> = listings[dir] ?: source.listChildren(dir).also { listings[dir] = it }

    /** Drops [dir]'s kept listing: the next [list] reads it again (a folder this scan changed). */
    fun forget(dir: ContentRef) { listings.remove(dir) }
}
