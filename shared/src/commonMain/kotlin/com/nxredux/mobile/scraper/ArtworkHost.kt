package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the controller needs from the app: the library, index updates (through AppState) and app-private files. */
interface ArtworkHost {
    /** The current index (playable + unassigned), hidden games left out. */
    suspend fun libraryGames(): List<LibraryGameRef>
    /** As [libraryGames] with the hidden games too (Reset clears their art as well). */
    suspend fun allLibraryGames(): List<LibraryGameRef>
    /** AppState: indexLock + setArt in memory; the write and the refresh are batched until [flushArt]. */
    suspend fun applyArt(gamePath: String, art: ArtRefs)
    /** Writes and shows the [applyArt] results still waiting: called when a fetch, a single fetch or a Reset ends. */
    suspend fun flushArt() {}
    /** An Android game's screenshot was stored: the Android games console is rebuilt so its entries show it. */
    suspend fun refreshAndroidConsole() {}
    /**
     * `.userdata/android/` files: null only when [name] is absent; a file that is there but can't be read throws (the
     * written-art log must never take an unreadable log for an empty one and save over it).
     */
    suspend fun readPrivate(name: String): String?
    suspend fun writePrivate(name: String, text: String)
    /**
     * Appends [text] to the file durably (created when absent); false when this host cannot append, and the caller
     * rewrites the whole file instead. A failure part-way throws and may leave a torn last line.
     */
    suspend fun appendPrivate(name: String, text: String): Boolean = false
}

/**
 * Builds the engine (its sources in order, ScreenScraper with the controller's live auth) and its art/log callbacks
 * ([onAndroidArt]: an Android game's screenshot stored; [logged]: the written-art log's queries; [onOptions] and
 * [picked]: the Change art option list, as [ScrapeEngine] takes them).
 */
typealias ScrapeEngineFactory = (
    auth: () -> ScraperAuth,
    onArt: suspend (gamePath: String, art: ArtRefs) -> Unit,
    onWritten: suspend (List<Pair<String, ArtOrigin?>>) -> Unit,
    onAndroidArt: suspend () -> Unit,
    logged: LoggedArt,
    onOptions: suspend (gamePath: String, options: List<ArtOption>, wide: Boolean) -> Unit,
    picked: suspend (gamePath: String) -> Set<ArtSlot>,
) -> ScrapeEngine

/**
 * A sign-in check: only [BAD_LOGIN] clears the stored login. [BUILD_REFUSED] is an [UNAVAILABLE] whose cause is this
 * build's developer login (ScreenScraper refused the app, not the user).
 */
enum class SignInResult { OK, BAD_LOGIN, OFFLINE, UNAVAILABLE, BUILD_REFUSED }

/** One game's fetch: its status and why (the item's reason, or the pause reason when the fetch was stopped). */
data class FetchResult(val status: ItemStatus, val reason: String? = null)

/**
 * A game's Change art list: its saved [options] in the order found, [currentKeys] per file slot ([ArtSlot.fileSlot]) the
 * option that slot's file came from (no entry for a hand-placed file, an extra folder's own, or none), and [note] why
 * some or every source is missing from it (stopped sources, a pause).
 */
data class ArtOptionsResult(val options: List<ArtOption>, val currentKeys: Map<ArtSlot, String>, val note: String? = null)

/** An option's preview: its [ref], or null when there is none to show; [dropped]: the link is dead and left the list. */
data class OptionImage(val ref: ContentRef?, val dropped: Boolean = false)

/** What the art panel asks of the artwork controller ([ArtworkController] implements it; the defaults are its own). */
interface ArtOptionsPort {
    /** One game's fetch; [wide]: also every source's screenshot, grid and hero candidates. */
    suspend fun fetchGame(gamePath: String, replaceExisting: Boolean = false, wide: Boolean = false): FetchResult
    /**
     * The game's Change art list; [searchAgain]: the wide search runs again first. [autoSearch] false: the saved list
     * as it is, never searched for (the read right after the panel's own Fetch art).
     */
    suspend fun artOptions(gamePath: String, replaceExisting: Boolean, searchAgain: Boolean = false, autoSearch: Boolean = true): ArtOptionsResult
    /** [option]'s preview; [OptionImage.dropped] when its link is dead and the option was dropped from the list. */
    suspend fun optionImage(gamePath: String, option: ArtOption): OptionImage
    /** Writes [option] into its file slot (2D box, 3D box, wheel or screenshot); false when it could not. */
    suspend fun pickOption(gamePath: String, option: ArtOption): Boolean
}

/** A Reset artwork: how many files it deleted and whether it paused the running fetch (it had games in scope). */
data class ResetResult(val deleted: Int, val pausedJob: Boolean = false)

/**
 * The [WrittenArtLog] kept in the app-private file [name], loaded once. New entries are appended
 * ([ArtworkHost.appendPrivate]); the file is rewritten whole on a removal, an [edit], a host that cannot append, or once
 * the appended lines outnumber the log ([COMPACT_MIN] at least), which folds a path's repeated lines back into one. A
 * torn last line (a crash or failure mid-append) is an entry whose file was never written: dropped on load, and the
 * next change rewrites the file. An absent file is an empty log; a failed read ([ArtworkHost.readPrivate] throws) is
 * neither cached nor saved over: the call fails and the next one reads again.
 */
class ArtLogStore(private val host: ArtworkHost, private val name: String) : LoggedArt, MovedArtLog {
    private val mutex = Mutex()
    private var log: WrittenArtLog? = null
    /** Lines appended since the file was last written whole. */
    private var appended = 0
    /** The file on disk may not match [log] line for line (a torn tail, a failed write): the next change rewrites it. */
    private var rewrite = false

    private suspend fun load(): WrittenArtLog = log ?: run {
        val text = host.readPrivate(name)   // a throw: not cached
        val torn = text != null && text.isNotEmpty() && !text.endsWith("\n")
        rewrite = torn; appended = 0
        WrittenArtLog.decode(if (torn) text!!.substringBeforeLast('\n', "") else text).also { log = it }
    }

    /** Adds (path, origin) entries; appended (or saved) when anything changed. */
    suspend fun add(entries: List<Pair<String, ArtOrigin?>>) = mutex.withLock {
        val l = load()
        val lines = StringBuilder()
        var changed = 0
        for ((path, origin) in entries) if (l.add(path, origin)) { lines.append(WrittenArtLog.line(path, l.origin(path))); changed++ }
        if (changed == 0) return@withLock
        if (rewrite || appended + changed > maxOf(COMPACT_MIN, l.size)) { save(l); return@withLock }
        val ok = try {
            host.appendPrivate(name, lines.toString())
        } catch (e: Throwable) {
            rewrite = true   // a part may have landed
            throw e
        }
        if (ok) appended += changed else save(l)
    }

    override suspend fun contains(path: String): Boolean = mutex.withLock { path in load() }

    /** Where the logged file at [path] came from; null when unknown or not logged. */
    override suspend fun origin(path: String): ArtOrigin? = mutex.withLock { load().origin(path) }

    override suspend fun remove(path: String) = mutex.withLock {
        val l = load()
        if (path in l) { l.remove(listOf(path)); save(l) }
    }

    /** The entry at [from] now at [to], its origin kept, in one save; nothing when [from] is not logged. */
    override suspend fun move(from: String, to: String) = mutex.withLock {
        val l = load()
        if (from !in l) return@withLock
        val origin = l.origin(from)
        l.remove(listOf(from))
        l.remove(listOf(to))   // [to]'s origin is [from]'s, even an unknown one
        l.add(to, origin)
        save(l)
    }

    /** [block]'s answer from the log under its lock, nothing saved. */
    suspend fun <T> read(block: (WrittenArtLog) -> T): T = mutex.withLock { block(load()) }

    /** Runs [block] on the log under its lock, then saves it. */
    suspend fun <T> edit(block: suspend (WrittenArtLog) -> T): T = mutex.withLock {
        val l = load()
        block(l).also { save(l) }
    }

    /** Writes the log whole (atomically); a failure leaves [rewrite] set so the next change tries again. */
    private suspend fun save(l: WrittenArtLog) {
        rewrite = true
        host.writePrivate(name, l.encode())
        rewrite = false; appended = 0
    }

    companion object {
        /** Appended lines allowed before a rewrite, however small the log. */
        const val COMPACT_MIN = 256
    }
}
