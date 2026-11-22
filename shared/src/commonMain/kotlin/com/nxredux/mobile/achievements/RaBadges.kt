package com.nxredux.mobile.achievements

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Badge PNGs per game in the home tree ([RaBadgeFolders]); fetched from media.retroachievements.org once. Complete files
 * never change, so a present one is skipped. A 404 is remembered for the process; any other failure is retried next time.
 */
class RaBadges(private val home: RaHomeTree, private val http: RaHttp, permits: Int = 4) {
    private val failed = HashSet<String>()
    private val failedLock = Mutex()
    private val gate = Semaphore(permits)

    /** [pointer]'s badge folder for [gameId]; null without a readable home, a pointer, a console folder or a game id. */
    suspend fun folder(pointer: RaGamePointer?, gameId: Int): List<String>? =
        if (!home.readable()) null else pointer?.let { home.consoleFolder(it) }?.let { RaBadgeFolders.segments(it, gameId) }

    /**
     * Both variants of every name, at most `permits` fetches in flight. The folder is made (or found) and listed once,
     * before any fetch, and every file is written into it: concurrent walks would race to create it (SAF leaves
     * `name (1)` strays). A home that can't be written (no home, a read-only grant) fetches nothing.
     */
    suspend fun ensureAll(folder: List<String>, names: Collection<String>) {
        val unique = names.distinct().takeIf { it.isNotEmpty() } ?: return
        if (!home.available()) return
        val dir = home.resolve(folder) ?: return
        val present = home.listing(dir).keys
        coroutineScope { for (n in unique) { launch { ensureIn(dir, n, false, present) }; launch { ensureIn(dir, n, true, present) } } }
    }

    private suspend fun ensureIn(dir: RaHomeDir, name: String, locked: Boolean, present: Set<String>): Boolean {
        val file = RaBadgeFolders.fileName(name, locked) ?: return false
        if (file in present) return true
        val bytes = fetch(file) ?: return false
        return home.writeBytes(dir, file, bytes) != null
    }

    // The badge's bytes, or null; only the server's "no such badge" is final (a dropped connection, a 5xx or a 429 may pass next time).
    private suspend fun fetch(file: String): ByteArray? {
        val url = "${RaRequests.MEDIA_BADGE}$file"
        if (failedLock.withLock { url in failed }) return null
        val r = gate.withPermit { try { http.getBytes(url) } catch (e: RaHttpException) { null } }
        if (r?.first == 404) { failedLock.withLock { failed += url }; return null }
        return r?.takeIf { it.first == 200 && it.second.isNotEmpty() }?.second
    }
}
