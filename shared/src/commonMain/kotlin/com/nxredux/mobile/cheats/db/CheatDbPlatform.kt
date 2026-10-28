package com.nxredux.mobile.cheats.db

/** The downloaded archive of [source] in the platform's cache; [stamp] is the response's Last-Modified header. */
class CheatDbArchive(val path: String, val stamp: String?, val source: CheatDbSource = CheatDbSource.LIBRETRO)

/** One archive to install and its rows: tag → the archive's folders ("" = its top level). */
class CheatDbInstall(val archive: CheatDbArchive, val folders: Map<String, List<String>>)

/** A failure the UI has words for. Anything else is reported by exception class name. */
class CheatDbException(val kind: Kind, cause: Throwable? = null) : Exception(kind.message, cause) {
    enum class Kind(val message: String) {
        NETWORK("Download failed (check Wi-Fi)"),
        CORRUPT("Downloaded archive is corrupt"),
        NO_SPACE("Not enough space (needs about 400 MB free)"),
        STORAGE("Could not write the cheat files"),
    }
}

/**
 * What the Cheats & Patches tool needs from the platform: network, the zips, and the sd root. Every suspend
 * function is cancellable and cleans up its own partial output on cancellation; [download] and [install]
 * throw [CheatDbException] for the failures the UI names. Implemented on Android with HttpURLConnection and
 * java.util.zip; faked in tests.
 */
interface CheatDbPlatform {
    /** Free bytes on the sd root's volume. */
    fun freeBytes(): Long
    /** The server's Last-Modified for [source]'s archive (HEAD), or null when it can't be reached. */
    suspend fun remoteStamp(source: CheatDbSource): String?
    /** Downloads [source]'s archive to the cache. [onProgress] gets (bytes so far, total or -1 when unknown). */
    suspend fun download(source: CheatDbSource, onProgress: (done: Long, total: Long) -> Unit): CheatDbArchive
    /**
     * For each of [installs] in order, extracts every file with its source's extension (`.cht`, `.pnach`) directly under
     * each tag's folders to [CheatDbSource.target] (`Cheats/<TAG>/`, `Cheats/<TAG>/Patches/`), calling [onProgress]
     * (tag, 1-based index, count over all installs) per tag. When two of a tag's folders hold the same file name, the
     * first folder's file is kept. Returns the written paths relative to the sd root.
     * On failure or cancellation, files this call created (in any of the archives) are deleted again; files that
     * existed before are left as they are.
     */
    suspend fun install(installs: List<CheatDbInstall>, onProgress: (tag: String, index: Int, count: Int) -> Unit): List<String>
    /** Deletes the archive (and any partial download). */
    suspend fun discard(archive: CheatDbArchive)
    /** The manifest text at [CheatDbManifest.PATH], or null when absent. */
    suspend fun readManifest(): String?
    suspend fun writeManifest(text: String)
    /** Deletes [files] (relative paths), prunes the `Cheats/<TAG>/` folders they emptied, and removes the manifest. */
    suspend fun deleteInstalled(files: List<String>)
    /** Deletes [files] (relative paths) and prunes the `Cheats/<TAG>/` folders they emptied; the manifest stays. Best effort. */
    suspend fun deleteFiles(files: List<String>)
}
