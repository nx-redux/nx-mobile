package com.nxredux.mobile.achievements

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** One offline unlock: user, achievement id, game md5, unix seconds. The JSON keys are nx-redux's. */
@Serializable
data class RaUnlock(val u: String, val a: Int, val m: String, val t: Long)

/**
 * Append-only JSONL (`pending/unlocks.jsonl` or `pending/confirmed.jsonl`). Deduplicated by (u, a, m); unparseable
 * lines are kept verbatim through a rewrite so nothing a future version understands is thrown away. The dedupe keys
 * are read once and kept in memory while the file's [RaStore.stamp] is the one this journal last saw (a reset, another
 * writer or a failed append makes it read the file again; the size alone would miss a remove plus an append of the same
 * length). Appends and rewrites hold the store's cross-process lock on the file:
 * the game process journals unlocks while the menu's sync rewrites it.
 */
class RaJournal(private val store: RaStore, private val path: String) {
    private val lock = Mutex()
    private var keys: HashSet<Triple<String, Int, String>>? = null
    /** The stamp the cached [keys] belong to; null with no file. */
    private var knownStamp: String? = null
    /** The file ends without a newline (a torn last line from a crash mid-append). */
    private var torn = false

    /** True when appended, false when an equal (u, a, m) entry already exists. Throws [RaStoreException] on write failure. */
    suspend fun append(entry: RaUnlock): Boolean = lock.withLock { store.locked(path) { appendLocked(entry) } }

    private suspend fun appendLocked(entry: RaUnlock): Boolean {
        val known = keys?.takeIf { store.stamp(path) == knownStamp } ?: load()
        if (Triple(entry.u, entry.a, entry.m) in known) return false
        // A torn last line (crash mid-append) must not swallow this entry: start a fresh line.
        val bytes = ((if (torn) "\n" else "") + RaJson.json.encodeToString(entry) + "\n").encodeToByteArray()
        try {
            store.append(path, bytes)
        } catch (e: Throwable) {
            keys = null   // a part may have landed: read the file again next time
            throw e
        }
        known += Triple(entry.u, entry.a, entry.m); torn = false
        knownStamp = store.stamp(path)   // under the lock: no other writer in between
        return true
    }

    /** [user]'s entries (every user's when null); RA usernames are case-insensitive, so the match ignores case. */
    suspend fun entries(user: String? = null): List<RaUnlock> = lock.withLock { parsedLines().mapNotNull { it.second }.filter { user == null || it.u.equals(user, ignoreCase = true) } }

    suspend fun count(user: String): Int = entries(user).size

    /** Rewrites the file without [done] (matched by u, a, m), atomically. */
    suspend fun remove(done: Collection<RaUnlock>): Unit = lock.withLock { store.locked(path) { removeLocked(done) } }

    private suspend fun removeLocked(done: Collection<RaUnlock>) {
        val gone = done.map { Triple(it.u, it.a, it.m) }.toSet()
        val kept = parsedLines().filter { (_, e) -> e == null || Triple(e.u, e.a, e.m) !in gone }
        val bytes = kept.joinToString("") { "${it.first}\n" }.encodeToByteArray()
        keys = null
        store.writeAtomic(path, bytes)
        keys = kept.mapNotNullTo(HashSet()) { (_, e) -> e?.let { Triple(it.u, it.a, it.m) } }; knownStamp = store.stamp(path); torn = false
    }

    /** Reads the file once for its keys and whether it ends torn. */
    private suspend fun load(): HashSet<Triple<String, Int, String>> {
        val stamp = store.stamp(path)
        val text = store.read(path)?.decodeToString() ?: ""
        return parsedLines(text).mapNotNullTo(HashSet()) { (_, e) -> e?.let { Triple(it.u, it.a, it.m) } }.also {
            keys = it; knownStamp = stamp; torn = text.isNotEmpty() && !text.endsWith("\n")
        }
    }

    private suspend fun readText(): String = store.read(path)?.decodeToString() ?: ""

    private suspend fun parsedLines(text: String? = null): List<Pair<String, RaUnlock?>> =
        (text ?: readText()).lineSequence().filter { it.isNotBlank() }
            .map { it to RaJson.parse(RaUnlock.serializer(), it) }.toList()
}
