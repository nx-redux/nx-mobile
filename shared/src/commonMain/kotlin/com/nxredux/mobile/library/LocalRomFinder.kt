package com.nxredux.mobile.library

import com.nxredux.mobile.netplay.cablelink.Sha1
import com.nxredux.mobile.netplay.cablelink.RomHash
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.MemoryPrivateFiles
import com.nxredux.mobile.storage.PrivateFiles
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Finds the library ROM whose SHA-1 and size are a given pair, reading through [source] (a SAF document or a file alike,
 * so the build without All files access works). A file of another size is never read. Each ROM is hashed once per change:
 * the hash is kept with the file's size and modified time (size alone when the provider reports no time) in [store], so a
 * restart does not hash the library again. Archives are never opened (a core cannot take one as the linked ROM), and a
 * file bigger than a GBA cartridge can be is skipped unread.
 */
class LocalRomFinder(private val source: RomsSource, private val store: PrivateFiles = MemoryPrivateFiles()) {
    private class Stamped(val size: Long, val modified: Long?, val hash: String)

    private val lock = Mutex()
    private var hashes: MutableMap<String, Stamped>? = null   // loaded from [store] on first use
    private var dirty = false

    /** The ref value of the first of [games] of [sizeBytes] bytes hashing to [hash], or null. Reads the ROMs it has not hashed yet. */
    suspend fun find(hash: String, sizeBytes: Long, games: List<IndexedGame>): String? = lock.withLock {
        val known = load()
        var found: String? = null
        for (g in games) {
            if (Names.isArchive(g.file)) continue
            if (hashOf(known, g, sizeBytes) == hash) { found = g.ref; break }
        }
        save(known, games)
        found
    }

    private suspend fun hashOf(known: MutableMap<String, Stamped>, g: IndexedGame, wanted: Long): String? {
        val entry = try { source.child(ContentRef(g.folder), g.file) } catch (e: ListingUnavailable) { null } ?: return null
        val size = entry.sizeBytes ?: return null
        if (size != wanted || size <= 0 || size > MAX_ROM_BYTES) return null
        val modified = entry.lastModified
        known[g.ref]?.takeIf { it.size == size && it.modified == modified }?.let { return it.hash }
        val sha = Sha1()
        var offset = 0L
        while (offset < size) {
            val chunk = source.readRange(entry.ref, offset, CHUNK) ?: return null
            if (chunk.isEmpty()) return null
            sha.update(chunk)
            offset += chunk.size
        }
        return sha.hex().also { known[g.ref] = Stamped(size, modified, it); dirty = true }
    }

    private suspend fun load(): MutableMap<String, Stamped> = hashes ?: run {
        val map = HashMap<String, Stamped>()
        val text = try { store.read(FILE) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
        text?.lineSequence()?.forEach { line ->
            val f = line.split('\t')
            val size = f.getOrNull(1)?.toLongOrNull()
            val hash = f.getOrNull(3)
            if (f.size == 4 && size != null && hash != null && RomHash.isHash(hash)) {
                map[f[0]] = Stamped(size, f[2].takeIf { it != NO_TIME }?.toLongOrNull(), hash)
            }
        }
        map.also { hashes = it }
    }

    /** Writes the kept hashes of [games] when a new one was added; a failed write only means hashing again next start. */
    private suspend fun save(known: MutableMap<String, Stamped>, games: List<IndexedGame>) {
        if (!dirty) return
        val refs = games.mapTo(HashSet()) { it.ref }
        known.keys.retainAll(refs)
        val text = known.entries.joinToString("") { (ref, s) -> "$ref\t${s.size}\t${s.modified ?: NO_TIME}\t${s.hash}\n" }
        try { store.write(FILE, text); dirty = false } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Unit }
    }

    companion object {
        const val MAX_ROM_BYTES = 32L shl 20
        private const val CHUNK = 256 * 1024
        private const val FILE = "rom-hashes.txt"
        private const val NO_TIME = "-"
    }
}
