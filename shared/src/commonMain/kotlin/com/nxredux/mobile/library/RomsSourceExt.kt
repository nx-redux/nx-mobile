package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef

/**
 * The folder at [segments] below [home], each segment reused when it is a folder and created when missing. Null when
 * [home] has no write grant, a file is in the way, or a create fails. Provider exceptions (a revoked or odd
 * provider) propagate: callers catch them.
 */
suspend fun RomsSource.ensurePath(home: ContentRef, segments: List<String>): ContentRef? =
    if (!canWrite(home)) null else ensureDirs(home, segments)

/** [ensurePath] from [from], a folder inside a home whose write grant the caller has checked. */
internal suspend fun RomsSource.ensureDirs(from: ContentRef, segments: List<String>): ContentRef? {
    var dir = from
    for (segment in segments) {
        val existing = child(dir, segment)
        dir = when {
            existing == null -> createDirectory(dir, segment)?.takeIf { it.isDirectory }?.ref ?: return null
            existing.isDirectory -> existing.ref
            else -> return null   // a file is in the way
        }
    }
    return dir
}

/** The existing folder at [segments] below [home] (nothing created); null when any part is missing or a file. */
internal suspend fun RomsSource.findDir(home: ContentRef, segments: List<String>): ContentRef? {
    var dir = home
    for (s in segments) dir = child(dir, s)?.takeIf { it.isDirectory }?.ref ?: return null
    return dir
}

/**
 * Files in [dir] by name: a direct lookup first (one query, and it still answers when the provider hides the listing),
 * then [dir]'s listing, read once on the first miss, by name in any case ([DiscSheet.Listing]).
 */
internal class FileLookup(private val source: RomsSource, private val dir: ContentRef) {
    private var listing: DiscSheet.Listing? = null

    suspend fun find(name: String): Entry? {
        source.child(dir, name)?.takeIf { !it.isDirectory }?.let { return it }
        val all = listing ?: DiscSheet.Listing(
            try { source.listChildren(dir).filter { !it.isDirectory } } catch (e: ListingUnavailable) { emptyList() },
        ).also { listing = it }
        return all.find(name)
    }
}
