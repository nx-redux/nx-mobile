package com.nxredux.mobile.library

import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.PrivateFiles
import com.nxredux.mobile.tryOrAny
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/** One collection's cached game count: good while its file still has stamp [file] and the library still has stamp [library]. */
@Serializable
data class CachedCount(val name: String, val file: String, val library: String, val count: Int)

@Serializable
private data class CountCacheFile(val version: Int, val entries: List<CachedCount> = emptyList())

/** JSON for `collection-counts.json`. Anything that does not decode, or another [VERSION], is an empty cache. */
object CollectionCountCodec {
    const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(entries: Collection<CachedCount>): String =
        json.encodeToString(CountCacheFile.serializer(), CountCacheFile(VERSION, entries.sortedBy { it.name }))

    fun decode(text: String?): Map<String, CachedCount> {
        if (text == null) return emptyMap()
        val file = runCatching { json.decodeFromString(CountCacheFile.serializer(), text) }.getOrNull()
        if (file == null || file.version != VERSION) return emptyMap()
        return file.entries.filter { it.count >= 0 && it.file.isNotEmpty() && it.library.isNotEmpty() }.associateBy { it.name }
    }
}

/**
 * The stamps a cached count is checked against. A collection file's: its last-modified time and size as listed (none when
 * the provider reports either as unknown: that collection is always counted), or a hash of the app-private copy that
 * [Collections] reads first. The library's: a fingerprint of the games a collection line can resolve to, so any scan,
 * hide, tag move or home change that alters them alters it.
 */
object CountStamps {
    /** No file: [Collections.readOrNull] gives no lines, a count of 0. */
    const val NONE = "none"

    fun file(entry: Entry): String? {
        val modified = entry.lastModified ?: return null
        val size = entry.sizeBytes ?: return null
        return "h:$modified:$size"
    }

    fun stored(text: String): String = "s:${text.length}:${hex(fnv(text))}"

    /** Order-free over [keys] (a lookup's key set), and tied to the [home] tree. */
    fun library(home: String?, keys: Collection<GameRef>): String {
        var sum = 0L
        var xor = 0L
        keys.forEach { k ->
            val h = mix(fnv("${k.sourceId}\u0000${k.relativeDir}\u0000${k.fileName}"))
            sum += h
            xor = xor xor h
        }
        return "${keys.size}:${hex(sum)}:${hex(xor)}:${hex(fnv(home.orEmpty()))}"
    }

    private fun fnv(s: String): Long {
        var h = -0x340d631b7bdddcdbL   // FNV-1a 64 offset basis
        s.forEach { c ->
            h = (h xor (c.code.toLong() and 0xFF)) * 0x100000001b3L
            h = (h xor (c.code.toLong() ushr 8)) * 0x100000001b3L
        }
        return h
    }

    /** SplitMix64's finaliser, so a sum of hashes does not cancel out. */
    private fun mix(x: Long): Long {
        var z = x
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun hex(v: Long): String = v.toULong().toString(16)
}

/**
 * The main menu's collection counts between runs, in the app-private [files] as [FILE] (next to the library index),
 * never in the collection files. A cached count is used only while both its stamps still match; anything else is counted
 * again. [save] coalesces: a burst of changes within [coalesceMillis] is one write, off the caller's thread on [io]. A
 * failed read is an empty cache; a failed write keeps the old file (whose stale stamps then fail to match).
 */
class CollectionCountCache(
    private val files: PrivateFiles,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val coalesceMillis: Long = COALESCE_MILLIS,
) {
    private val entries = HashMap<String, CachedCount>()
    private val loadLock = Mutex()
    private var loaded = false
    private var written: String? = null   // the file's text as last read or written: an unchanged cache is not written again
    private var dirty = false
    private var writeJob: Job? = null

    /** Reads the file once; later calls return at once. */
    suspend fun load() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            val text = tryOrAny({ null }) { files.read(FILE) }
            entries.putAll(CollectionCountCodec.decode(text))
            written = text   // a corrupt or old file differs from any encoding, so the next save rewrites it
            loaded = true
        }
    }

    /** True when any of [names] has an entry to check. */
    fun hasAny(names: Collection<String>): Boolean = names.any { it in entries }

    /** [name]'s cached count when its entry has exactly these stamps; null (count it) otherwise. */
    fun valid(name: String, file: String?, library: String): Int? =
        if (file == null) null else entries[name]?.takeIf { it.file == file && it.library == library }?.count

    fun put(name: String, file: String, library: String, count: Int) { entries[name] = CachedCount(name, file, library, count) }

    fun remove(name: String) { entries.remove(name) }

    /** Drops the entries of collections not in [names]. */
    fun retain(names: Collection<String>) { entries.keys.retainAll(names.toSet()) }

    /** Writes the entries after [coalesceMillis], once for every change made meanwhile. */
    fun save() {
        dirty = true
        if (writeJob?.isActive == true) return
        writeJob = scope.launch {
            while (dirty) {
                delay(coalesceMillis)
                dirty = false
                val snapshot = entries.values.toList()
                tryOrAny({}) {
                    withContext(io) {
                        val text = CollectionCountCodec.encode(snapshot)
                        if (text != written) { files.write(FILE, text); written = text }
                    }
                }
            }
        }
    }

    companion object {
        const val FILE = "collection-counts.json"
        const val COALESCE_MILLIS = 300L
    }
}
