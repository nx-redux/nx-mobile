package com.nxredux.mobile.ui

import com.nxredux.mobile.library.CollectionCountCache
import com.nxredux.mobile.library.Collections
import com.nxredux.mobile.library.CountStamps
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.GameRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nxredux.mobile.ui.nx.statsKey

/** What the collection counts need from [AppState]: the library as loaded and the main menu's rows to rebuild. */
internal interface CollectionCountsHost {
    fun home(): ContentRef?
    fun systems(): List<SystemFolder>
    suspend fun playableOffMain(systems: List<SystemFolder>): Map<GameRef, Listed>
    suspend fun buildMainRowsMerged()
    fun swapMainRows()
    suspend fun refreshMainRows()
    /** The top screen's stats of [stale] (a collection's key; null: every one) hold games that changed. */
    fun invalidateStats(stale: String?)
}

/**
 * The Collections tab's names and each collection's game count: listed against the tree, seeded from the stored cache
 * where its stamps still match, counted after the menu shows, and made stale by a write, a rescan or a hidden game.
 */
internal class CollectionCountsController(
    private val scope: CoroutineScope,
    private val deps: AppDeps,
    private val collections: Collections,
    private val host: CollectionCountsHost,
) {
    var collectionNames: List<String> = emptyList() // Collections/ as last listed against the tree
        private set
    private val collectionCounts = HashMap<String, Int?>()   // each collection's game count, by name; null: its file would not read
    private var countsGeneration = 0                          // bumped when counts go stale, so a pass begun before it is dropped
    private var countsJob: Job? = null
    private val countCache = CollectionCountCache(deps.privateFiles, scope, deps.compute)   // the counts between runs, app-private
    private var libraryStampOf: Pair<List<SystemFolder>, String>? = null   // [CountStamps.library] of the systems it was taken for

    /**
     * The collection counts are stale: [name]'s (it was just written), or every one (the library or the listing changed).
     * A written collection leaves the stored cache too. Every other stored entry stays: its file and library stamps decide
     * whether the next pass may use it, and that pass rewrites the cache.
     */
    fun invalidateCollectionCounts(name: String? = null) {
        if (name == null) collectionCounts.clear() else collectionCounts.remove(name)
        countsGeneration++
        if (name != null) { countCache.remove(name); countCache.save() }
        // The stats page of a collection holds its games too: computed again, shown in place meanwhile.
        val stale = name?.let { statsKey(TopContent.CollectionStats(it)) }
        host.invalidateStats(stale)
    }

    /** [CountStamps.library] of [systems] (the games a collection line can resolve to), taken once per systems list. */
    private suspend fun libraryStamp(systems: List<SystemFolder>): String {
        libraryStampOf?.takeIf { it.first === systems }?.let { return it.second }
        val home = host.home()?.value
        val lookup = host.playableOffMain(systems)
        val stamp = withContext(deps.compute) { CountStamps.library(home, lookup.keys) }
        libraryStampOf = systems to stamp
        return stamp
    }

    /**
     * Fills the counts of the collections just listed from the stored cache where both stamps still match (one listing,
     * no collection file read), so the menu built next shows them at once. Dropped when the counts went stale meanwhile.
     */
    private suspend fun seedCollectionCounts() {
        countCache.load()
        val names = collectionNames
        if (!countCache.hasAny(names)) return
        val generation = countsGeneration
        val systems = host.systems()
        val library = libraryStamp(systems)
        val stamps = collections.stamps(names)
        if (generation != countsGeneration || systems !== host.systems() || names != collectionNames) return
        names.forEach { n -> countCache.valid(n, stamps[n], library)?.let { collectionCounts[n] = it } }
    }

    /**
     * Counts the collections not counted yet, after the menu is shown (a rebuild never waits on collection I/O), then shows
     * them. Each file is read with the loader opening it uses; its lines resolve on [AppDeps.compute] against one lookup of
     * the library. A pass begun before its counts went stale is dropped and counted again. One pass at a time.
     */
    fun fillCollectionCounts() {
        if (countsJob?.isActive == true || collectionNames.all { it in collectionCounts }) return
        countsJob = scope.launch {
            countCache.load()
            while (true) {
                val generation = countsGeneration
                val missing = collectionNames.filter { it !in collectionCounts }
                if (missing.isEmpty()) break
                val systems = host.systems()
                val library = libraryStamp(systems)
                // Stored counts whose stamps still match need no read (a rescan that changed nothing); the rest are read.
                val cached = if (countCache.hasAny(missing)) {
                    val stamps = collections.stamps(missing)
                    missing.mapNotNull { n -> countCache.valid(n, stamps[n], library)?.let { n to it } }.toMap()
                } else emptyMap()
                val read = (missing - cached.keys).associateWith { collections.readStamped(it) }
                val lookup = if (read.isEmpty()) null else host.playableOffMain(systems)
                val counts = if (lookup == null) emptyMap() else withContext(deps.compute) {
                    read.mapValues { (_, r) -> r.lines?.let { resolvedCount(it, lookup) } }
                }
                if (generation != countsGeneration) continue
                collectionCounts.putAll(cached)
                collectionCounts.putAll(counts)
                // An unreadable file (no count) or one without a stamp is not stored: the next start counts it.
                counts.forEach { (n, c) -> val stamp = read[n]?.stamp; if (c != null && stamp != null) countCache.put(n, stamp, library, c) else countCache.remove(n) }
                countCache.retain(collectionNames)
                countCache.save()
                host.buildMainRowsMerged()
                host.swapMainRows()
            }
        }
    }

    /**
     * Re-lists Collections/ (a tree read). A listing that would not list ([ListingUnavailable]) and gives no names keeps
     * the names as they were: an empty answer there is no proof. True when the names changed.
     */
    suspend fun refreshCollectionNames(): Boolean {
        val checked = collections.namesChecked()
        if (!checked.listed && checked.names.isEmpty()) return false
        if (checked.names == collectionNames) return false
        collectionNames = checked.names
        invalidateCollectionCounts()
        seedCollectionCounts()
        return true
    }

    /** The tree was just read (a scan, the index start's probe): pick up a changed Collections tab. */
    suspend fun refreshCollectionsFlag() {
        if (refreshCollectionNames()) host.refreshMainRows()
    }


    /** [name]'s game count as counted so far; null: not counted yet, or its file would not read. */
    fun countOf(name: String): Int? = collectionCounts[name]

    /** The stored counts are read (once): the collections listed next show them. */
    suspend fun loadStored() = countCache.load()
}
