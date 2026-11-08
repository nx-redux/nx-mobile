package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.library.ensurePath
import com.nxredux.mobile.library.findDir
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.tryOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What [RaHomeTree.read] found: the file's text, no such file, or a failure (the file may be there and must not be replaced). */
sealed interface RaRead {
    /** The file's content. */
    data class Text(val text: String) : RaRead

    /** A listing shows no such file (or no such folder): a write may create it. */
    data object Absent : RaRead

    /** No home, or the provider would not read or list: the file may exist, so nothing may overwrite it. */
    data object Failed : RaRead
}

/** A folder in the home tree resolved once ([RaHomeTree.resolve]), so many files go into it without a walk each. */
class RaHomeDir internal constructor(internal val ref: ContentRef)

/**
 * The shared RA files in the home tree (badges, `hashes.json`), by home-relative segments. No home (or a lost grant):
 * reads are empty and writes are skipped; a provider failure is the same, never thrown.
 */
class RaHomeTree(private val source: RomsSource, private val home: () -> ContentRef?) {
    // Only folders found in Roms/, by (home, tag): a display-name fallback (none there, or a failed listing) is asked again.
    private val tagFolders = HashMap<Pair<String, String>, String>()
    private val lock = Mutex()

    /**
     * True while the home can be written (a granted tree on mounted storage); without that nothing new can be stored, so
     * callers skip work (a download) that would be discarded. Never throws.
     */
    suspend fun available(): Boolean = guard { home()?.let { !source.isOffline(it) && source.canWrite(it) } } == true

    /** True while the home can be read (a granted tree, write or read only, on mounted storage). Never throws. */
    suspend fun readable(): Boolean = guard { home()?.let { !source.isOffline(it) } } == true

    /** Where [p]'s shared RA files go: its own console folder, else the folder for its tag. */
    suspend fun consoleFolder(p: RaGamePointer): String? = RaBadgeFolders.ownFolder(p) ?: folderForTag(p.tag)

    /** The `Roms/` folder holding [tag]'s console tag (any `Name (TAG)`, as Skeleton finds it; `MGBA` → the `GBA` folder), else its display name (created on first write). */
    suspend fun folderForTag(tag: String): String? {
        val key = CoreRegistry.consoleTag(tag)
        val h = home() ?: return null
        lock.withLock { tagFolders[h.value to key] }?.let { return it }
        val existing = guard {
            source.child(h, "Roms")?.takeIf { it.isDirectory }?.let { source.listChildren(it.ref) }.orEmpty()
                .firstOrNull { it.isDirectory && Names.parseSystemFolder(it.name)?.second?.uppercase() == key }?.name
        }
        if (existing != null) { lock.withLock { tagFolders[h.value to key] = existing }; return existing }
        return CoreRegistry.consoleOf(key)?.let { Names.systemFolderName(it, it.defaultTag) }
    }

    /** File name → model (the document's URI string) for the non-empty files in [segments]; empty when absent. */
    suspend fun listing(segments: List<String>): Map<String, String> = guard {
        home()?.let { source.findDir(it, segments) }?.let { filesIn(it) }
    } ?: emptyMap()

    /** [listing] of a folder [resolve] gave. */
    suspend fun listing(dir: RaHomeDir): Map<String, String> = guard { filesIn(dir.ref) } ?: emptyMap()

    private suspend fun filesIn(dir: ContentRef): Map<String, String> =
        source.listChildren(dir).filter { !it.isDirectory && (it.sizeBytes ?: 1L) > 0L }.associate { it.name to it.ref.value }

    /** True when [segments] holds a non-empty file [name]. */
    suspend fun has(segments: List<String>, name: String): Boolean = guard {
        val dir = home()?.let { source.findDir(it, segments) } ?: return@guard null
        source.child(dir, name)?.takeIf { !it.isDirectory && (it.sizeBytes ?: 1L) > 0L }
    } != null

    /** The folder at [segments], created where missing; null without a home, a write grant, or when a create fails. */
    suspend fun resolve(segments: List<String>): RaHomeDir? = guard { home()?.let { source.ensurePath(it, segments) }?.let(::RaHomeDir) }

    /** Writes [bytes] as [name] in [segments] (folders created); the file's model, or null. */
    suspend fun writeBytes(segments: List<String>, name: String, bytes: ByteArray): String? =
        resolve(segments)?.let { writeBytes(it, name, bytes) }

    /** Writes [bytes] as [name] in [dir] (no walk); the file's model, or null. */
    suspend fun writeBytes(dir: RaHomeDir, name: String, bytes: ByteArray): String? = guard {
        if (!source.writeBytes(dir.ref, name, bytes)) return@guard null
        source.child(dir.ref, name)?.ref?.value
    }

    /**
     * [name] in [segments], telling a missing file from one that could not be read: a lookup that finds nothing is
     * confirmed by listing each level from the home (a folder not there is [RaRead.Absent]); a listed file that would not
     * read, or a listing that failed, is [RaRead.Failed].
     */
    suspend fun read(segments: List<String>, name: String): RaRead {
        val h = home() ?: return RaRead.Failed
        guard { source.findDir(h, segments)?.let { source.child(it, name) }?.takeIf { !it.isDirectory }?.let { source.readText(it.ref) } }
            ?.let { return RaRead.Text(it) }
        return guard { listed(h, segments, name) } ?: RaRead.Failed
    }

    // Lists, never looks up by name: SAF's child lookup and read turn provider errors into "none", a listing throws them.
    private suspend fun listed(home: ContentRef, segments: List<String>, name: String): RaRead {
        var dir = home
        for (s in segments) dir = source.listChildren(dir).firstOrNull { it.name == s && it.isDirectory }?.ref ?: return RaRead.Absent
        return if (source.listChildren(dir).any { it.name == name }) RaRead.Failed else RaRead.Absent
    }

    /** [read]'s text; null when absent or unreadable alike. */
    suspend fun readText(segments: List<String>, name: String): String? = (read(segments, name) as? RaRead.Text)?.text

    /** Writes [text] as [name] in [segments] (folders created, through `.part` where the platform can); false when skipped or failed. */
    suspend fun writeText(segments: List<String>, name: String, text: String): Boolean = guard {
        val dir = home()?.let { source.ensurePath(it, segments) } ?: return@guard null
        source.writeText(dir, name, text).takeIf { it }
    } == true

    private inline fun <T> guard(block: () -> T?): T? = tryOrNull(block)
}
