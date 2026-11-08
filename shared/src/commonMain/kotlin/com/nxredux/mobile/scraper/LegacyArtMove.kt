package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.Entry
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What [LegacyArtMove] asks of the written-art log ([ArtLogStore] implements it). */
interface MovedArtLog {
    suspend fun contains(path: String): Boolean
    /** The entry at [from] (when logged) now at [to], its origin kept; nothing when [from] is not logged. */
    suspend fun move(from: String, to: String)
    suspend fun remove(path: String)
}

/**
 * The hand-placed `hero/` files the one-time move put in `screenshot/` ([LegacyArtMove]), as home-relative paths
 * (`Roms/GB/.media/screenshot/Tetris.jpg`), kept in the app-private [FILE] (one per line): still heroes (drawn as one)
 * though not in the written-art log, so Reset never deletes them. A file written over one (a fetch or a pick: any
 * extension of its base name) leaves the list ([overwritten]).
 */
class MovedHeroes(private val host: ArtworkHost) {
    private val mutex = Mutex()
    private var paths: MutableSet<String>? = null

    private suspend fun load(): MutableSet<String> = paths
        ?: host.readPrivate(FILE).orEmpty().lineSequence().filter { it.isNotEmpty() }.toMutableSet().also { paths = it }

    private suspend fun save(set: Set<String>) = host.writePrivate(FILE, set.joinToString("") { "$it\n" })

    suspend fun all(): Set<String> = mutex.withLock { load().toSet() }

    suspend fun contains(path: String): Boolean = mutex.withLock { path in load() }

    suspend fun add(path: String) = mutex.withLock { val set = load(); if (set.add(path)) save(set) }

    suspend fun remove(path: String) = mutex.withLock { val set = load(); if (set.remove(path)) save(set) }

    /** [written] files (home-relative) were written: the listed files of their base names are no longer heroes. */
    suspend fun overwritten(written: List<String>) = mutex.withLock {
        val set = load()
        if (set.isEmpty()) return@withLock
        val bases = written.mapTo(HashSet(), ::withoutExt)
        if (set.removeAll { withoutExt(it) in bases }) save(set)
    }

    companion object {
        const val FILE = "moved_heroes.txt"
        private fun withoutExt(path: String): String {
            val slash = path.lastIndexOf('/')
            val dot = path.lastIndexOf('.')
            return if (dot > slash + 1) path.substring(0, dot) else path
        }
    }
}

/**
 * The one-time move of a `.media` folder's old `grid/` files into `boxart2d/` and `hero/` files into `screenshot/`
 * (nothing else reads `grid/` or `hero/`). A file moves only when its target folder has no file of its base name
 * (any of png, jpg, jpeg; for `boxart2d/`, nor in the legacy `box2d/`, whose cover would be hidden); the log entry
 * follows it. Where the target is taken, a logged file (the app's) is deleted and a hand-placed one stays where it is.
 * A hand-placed hero moved (home only) is listed in [heroes], unlogged, so it is still drawn as a hero and Reset keeps
 * it. Emptied `grid/` and `hero/` folders are removed. A folder done without a failure is recorded in the app-private
 * [MARKER_FILE] (one `.media` ref per line) and never looked at again; a failure, a throw or a folder that cannot be
 * written leaves it unmarked, to try again on the next scan.
 */
class LegacyArtMove(
    private val source: RomsSource, private val log: MovedArtLog, private val host: ArtworkHost,
    private val heroes: MovedHeroes = MovedHeroes(host),
) {
    private val lock = Mutex()
    /** [MARKER_FILE]'s lines, read once. */
    private var marked: MutableSet<String>? = null

    /**
     * Moves [media]'s legacy files; [entries] is [media]'s listing, [homeSegments] its home-relative path (the log's
     * keys), null for an extra folder's own `.media`, where nothing is logged. True when anything changed on disk (the
     * caller lists [media] again). Never throws but for cancellation.
     */
    suspend fun move(media: ContentRef, entries: List<Entry>, homeSegments: List<String>?): Boolean {
        val legacy = entries.filter { it.isDirectory && it.name in LEGACY }
        if (legacy.isEmpty()) return false
        return lock.withLock {
            var changed = false
            try {
                val marks = marked ?: host.readPrivate(MARKER_FILE).orEmpty().lineSequence().filter { it.isNotEmpty() }.toMutableSet().also { marked = it }
                if (media.value in marks || !source.canWrite(media)) return@withLock false
                var ok = true
                for (dir in legacy) {
                    val targetName = LEGACY.getValue(dir.name)
                    val files = source.listChildren(dir.ref).filter { !it.isDirectory && imageBase(it.name) != null }
                    if (files.isNotEmpty()) {
                        val target = entries.firstOrNull { it.isDirectory && it.name == targetName }?.ref
                            ?: source.createDirectory(media, targetName)?.takeIf { it.isDirectory }?.ref
                        if (target == null) { ok = false; continue }
                        val taken = source.listChildren(target).filter { !it.isDirectory }.mapNotNullTo(HashSet()) { imageBase(it.name) }
                        // A legacy `box2d/` cover is the 2D box too (shown over a grid's): its base names are taken.
                        if (targetName == ArtSlot.BOX_2D.folder) entries.firstOrNull { it.isDirectory && it.name == LEGACY_BOX2D_FOLDER }?.let { b ->
                            source.listChildren(b.ref).filter { !it.isDirectory }.mapNotNullTo(taken) { imageBase(it.name) }
                        }
                        fun path(folder: String, name: String) = homeSegments?.let { (it + folder + name).joinToString("/") }
                        for (f in files) {
                            val base = imageBase(f.name)!!
                            val from = path(dir.name, f.name)
                            if (base in taken) {
                                // The slot has its file: the app's own old copy goes, a hand-placed one stays.
                                if (from == null || !log.contains(from)) continue
                                if (source.delete(dir.ref, f.name)) { changed = true; log.remove(from) } else ok = false
                            } else {
                                // A hand-placed hero is listed first (a failed move takes it back): never moved unlisted.
                                val to = path(targetName, f.name)
                                val handHero = dir.name == HERO && from != null && !log.contains(from)
                                if (handHero) heroes.add(to!!)
                                val moved = try { source.moveFile(dir.ref, f.name, target) } catch (t: Throwable) {
                                    if (handHero) withContext(NonCancellable) { heroes.remove(to!!) }
                                    throw t
                                }
                                if (moved) {
                                    changed = true
                                    taken += base
                                    if (from != null && !handHero) log.move(from, to!!)
                                } else {
                                    ok = false
                                    if (handHero) heroes.remove(to!!)
                                }
                            }
                        }
                    }
                    // False when files stay (hand-placed or failed ones): harmless, the folder is just not read.
                    if (source.deleteEmptyDirectory(media, dir.name)) changed = true
                }
                if (ok) {
                    val line = "${media.value}\n"
                    if (!host.appendPrivate(MARKER_FILE, line)) host.writePrivate(MARKER_FILE, (marks + media.value).joinToString("") { "$it\n" })
                    marks += media.value
                }
            } catch (e: CancellationException) { throw e } catch (_: Throwable) {}
            changed
        }
    }

    companion object {
        /** App-private: the `.media` folders already moved. */
        const val MARKER_FILE = "art_moved.txt"
        private const val HERO = "hero"
        /** Each legacy folder and where its files go. */
        private val LEGACY = mapOf("grid" to "boxart2d", HERO to "screenshot")
        private val EXTS = listOf("png", "jpg", "jpeg")

        /** [name]'s base when it is an art file (png, jpg or jpeg in any case), else null. */
        private fun imageBase(name: String): String? {
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || name.substring(dot + 1).lowercase() !in EXTS) return null
            return name.substring(0, dot)
        }
    }
}
