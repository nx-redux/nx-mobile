package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.SidecarStore
import kotlinx.coroutines.CancellationException

/**
 * A folder's `systems.txt` and `map.txt`. The app writes only inside the home tree: a home folder keeps its
 * files in the folder; an extra folder's live in `<home>/Roms/.sources/<id>/<relativeDir>/`, and files
 * inside the extra are only ever read. The app writes `map.txt` only through [setName]. The app-private [store]
 * takes a write the home could not (read-only grant or a failed write) and is rewritten after a later successful
 * home write, so it never goes stale.
 */
class Sidecars(private val source: RomsSource, private val store: SidecarStore, private val home: () -> ContentRef?) {

    private suspend fun textIn(dir: ContentRef?, name: String): String? =
        dir?.let { source.child(it, name) }?.takeIf { !it.isDirectory }?.let { source.readText(it.ref) }

    /**
     * [name] in [dir] for a rewrite: "" when it is not there (or there is no [dir]), null when the read failed: it threw, or
     * the file is there but gave no text (SAF reports a failed read as null). A write never replaces a file it could not
     * read, as [Collections.readOrNull]: a rewrite from such a file would keep only the one line set.
     */
    private suspend fun textToRewrite(dir: ContentRef?, name: String): String? = try {
        val f = dir?.let { source.child(it, name) }?.takeIf { !it.isDirectory }
        if (f == null) "" else source.readText(f.ref)
    } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }

    /** `.sources/<id>/<relativeDir segments>` below the home's `Roms`. */
    private fun copyPath(sourceId: String, relativeDir: String): List<String> =
        listOf(SOURCES, sourceId) + relativeDir.split('/').filter { it.isNotEmpty() }

    private suspend fun homeRoms(): ContentRef? = home()?.let { source.findDir(it, listOf("Roms")) }

    /** The home folder at [relativeDir] ("Roms/Game Boy (GB)"); null when a segment is missing or a file. */
    private suspend fun homeFolder(relativeDir: String): ContentRef? =
        home()?.let { source.findDir(it, relativeDir.split('/').filter { s -> s.isNotEmpty() }) }

    /** The existing home-copy folder of an extra folder, or null when any segment is missing. */
    private suspend fun copyDir(sourceId: String, relativeDir: String): ContentRef? =
        homeRoms()?.let { source.findDir(it, copyPath(sourceId, relativeDir)) }

    /**
     * Extras: in-extra file, then the home copy, then the store (later wins). Home: in-folder file, then the store.
     * The store is read last because it is the app's newest record: it is written only when a home write failed and
     * rewritten after every later successful home write, so a stale home copy can never hide a newer choice.
     */
    suspend fun read(sourceId: String, folder: ContentRef, relativeDir: String): Map<String, String> =
        merged(sourceId, relativeDir, SidecarTxt.FILE, SidecarTxt::parse, textIn(folder, SidecarTxt.FILE)) { copyDir(sourceId, relativeDir) }

    /** `map.txt` aliases: the folder's own, then (extras only) the home copy's, then the store's (written by Rename when the home could not be); later wins. */
    suspend fun displayNames(sourceId: String, folder: ContentRef, relativeDir: String): Map<String, String> =
        merged(sourceId, relativeDir, MAP_TXT, MapTxt::parse, textIn(folder, MAP_TXT)) { copyDir(sourceId, relativeDir) }

    /** [own] (the folder's [file]), then for an extra the home copy's (in [copy]), then the store's; later wins. */
    private suspend fun merged(
        sourceId: String, relativeDir: String, file: String, parse: (String) -> Map<String, String>, own: String?, copy: suspend () -> ContentRef?,
    ): Map<String, String> {
        val mine = own?.let(parse) ?: emptyMap()
        val stored = store.read(sourceId, relativeDir, file)?.let(parse) ?: emptyMap()
        if (sourceId == Recents.HOME) return mine + stored
        return mine + (textIn(copy(), file)?.let(parse) ?: emptyMap()) + stored
    }

    /** A scan's reads ([ScanReads]); home-copy folders go through [walk] when it walks this [home]. */
    internal fun scan(walk: DirWalk?): Scan = Scan(walk?.takeIf { it.root == home() } ?: home()?.let { DirWalk(source, it) })

    /**
     * [read] and [displayNames] for one scan: a folder's own file is taken from the listing the scan already holds (no
     * lookup), and each home-copy folder is looked up once ([dirs], rooted at the home).
     */
    internal inner class Scan(private val dirs: DirWalk?) {
        private suspend fun copy(sourceId: String, relativeDir: String): ContentRef? = dirs?.dir(listOf("Roms") + copyPath(sourceId, relativeDir))

        private suspend fun own(listing: List<Entry>, file: String): String? =
            listing.firstOrNull { it.name == file && !it.isDirectory }?.let { source.readText(it.ref) }

        suspend fun read(sourceId: String, relativeDir: String, listing: List<Entry>): Map<String, String> =
            merged(sourceId, relativeDir, SidecarTxt.FILE, SidecarTxt::parse, own(listing, SidecarTxt.FILE)) { copy(sourceId, relativeDir) }

        suspend fun displayNames(sourceId: String, relativeDir: String, listing: List<Entry>): Map<String, String> =
            merged(sourceId, relativeDir, MAP_TXT, MapTxt::parse, own(listing, MAP_TXT)) { copy(sourceId, relativeDir) }
    }

    /**
     * Sets [file] -> [tag] over what [read] returns, written through [upsert]. False, with nothing written, when the file the
     * write would replace (a home folder's own, an extra's home copy) is there but could not be read ([textToRewrite]).
     */
    suspend fun set(sourceId: String, folder: ContentRef, relativeDir: String, file: String, tag: String): Boolean {
        val home = sourceId == Recents.HOME
        val own = if (home) textToRewrite(folder, SidecarTxt.FILE) ?: return false else textIn(folder, SidecarTxt.FILE)
        val copy = if (home) null else textToRewrite(copyDir(sourceId, relativeDir), SidecarTxt.FILE) ?: return false
        return upsert(sourceId, relativeDir, SidecarTxt.FILE, folder) { stored ->
            val merged = listOfNotNull(own, copy, stored).fold(emptyMap<String, String>()) { acc, t -> acc + SidecarTxt.parse(t) }
            SidecarTxt.with(SidecarTxt.render(merged), file, tag)
        }
    }

    /**
     * Rename: sets [key] -> [name] in the app's `map.txt` for [lookupDir] (a home folder's own file, merged with the user's
     * other aliases; an extra's `.sources` copy). [key] is the file name, or a disc folder's name. False when neither
     * the home nor the store took it, or the `map.txt` it would replace is there but could not be read ([textToRewrite]).
     */
    suspend fun setName(sourceId: String, lookupDir: String, key: String, name: String): Boolean {
        val folder = if (sourceId == Recents.HOME) homeFolder(lookupDir) ?: return false else null
        val mine = textToRewrite(folder ?: copyDir(sourceId, lookupDir), MAP_TXT) ?: return false
        return upsert(sourceId, lookupDir, MAP_TXT, folder) { current ->
            val stored = current?.let(MapTxt::parse) ?: emptyMap()
            MapTxt.with(MapTxt.render(MapTxt.parse(mine) + stored), key, name)
        }
    }

    /**
     * Writes [file] for [relativeDir]: a home folder ([folder]) writes into it; an extra folder writes its home copy (creating
     * `.sources/…` on first use) when the home is writable. After a home write the store is rewritten when it holds this file;
     * otherwise the store takes the text. Nothing is ever written under an extra folder's tree. [edit] gets the store's
     * current text. False when neither took it.
     */
    private suspend fun upsert(sourceId: String, relativeDir: String, file: String, folder: ContentRef?, edit: suspend (String?) -> String): Boolean {
        val stored = store.read(sourceId, relativeDir, file)
        val text = edit(stored)
        val inHome = if (sourceId == Recents.HOME) folder != null && writeInFolder(folder, file, text) else writeHomeCopy(sourceId, relativeDir, file, text)
        if (inHome) {
            if (stored != null) store.write(sourceId, relativeDir, file, text)
            return true
        }
        return store.write(sourceId, relativeDir, file, text)
    }

    private suspend fun writeInFolder(folder: ContentRef, file: String, text: String): Boolean {
        val h = home() ?: return false
        return source.canWrite(h) && source.writeText(folder, file, text)
    }

    /** A home that is read-only, gone or fails mid-way is a silent false: the caller falls back to the store. */
    private suspend fun writeHomeCopy(sourceId: String, relativeDir: String, file: String, text: String): Boolean = try {
        val h = home()
        if (h == null || !source.canWrite(h)) {
            false
        } else {
            val dir = homeRoms()?.let { source.ensureDirs(it, copyPath(sourceId, relativeDir)) }
            dir != null && source.writeText(dir, file, text)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        false
    }

    companion object {
        const val SOURCES = ".sources"
        const val MAP_TXT = "map.txt"
    }
}
