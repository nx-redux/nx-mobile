package com.nxredux.mobile.library

import com.nxredux.mobile.netplay.cablelink.Sha1
import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Finds the library ROM whose SHA-1 is a given hash, reading through [source] (a SAF document or a file alike, so the
 * build without All files access works). Each ROM is hashed once per change: the hash is kept with the file's size and
 * modified time and reused while both hold. Archives are never opened (a core cannot take one as the linked ROM), and
 * a file bigger than a GBA cartridge can be is skipped unread.
 */
class LocalRomFinder(private val source: RomsSource) {
    private class Stamped(val size: Long, val modified: Long, val hash: String)

    private val lock = Mutex()
    private val hashes = HashMap<String, Stamped>()

    /** The ref value of the first of [games] hashing to [hash], or null. Reads the ROMs it has not hashed yet. */
    suspend fun find(hash: String, games: List<IndexedGame>): String? {
        for (g in games) {
            if (Names.isArchive(g.file)) continue
            if (hashOf(g) == hash) return g.ref
        }
        return null
    }

    private suspend fun hashOf(g: IndexedGame): String? {
        val entry = try { source.child(ContentRef(g.folder), g.file) } catch (e: ListingUnavailable) { null } ?: return null
        val size = entry.sizeBytes ?: return null
        if (size <= 0 || size > MAX_ROM_BYTES) return null
        val modified = entry.lastModified
        if (modified != null) lock.withLock { hashes[g.ref]?.takeIf { it.size == size && it.modified == modified }?.hash }?.let { return it }
        val sha = Sha1()
        var offset = 0L
        while (offset < size) {
            val chunk = source.readRange(entry.ref, offset, CHUNK) ?: return null
            if (chunk.isEmpty()) return null
            sha.update(chunk)
            offset += chunk.size
        }
        return sha.hex().also { h -> if (modified != null) lock.withLock { hashes[g.ref] = Stamped(size, modified, h) } }
    }

    companion object {
        const val MAX_ROM_BYTES = 32L shl 20
        private const val CHUNK = 256 * 1024
    }
}
