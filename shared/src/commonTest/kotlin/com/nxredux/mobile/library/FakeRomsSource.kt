package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.yield

/**
 * In-memory tree. Keys are paths like "Roms", "Roms/Nintendo DS (NDS)", values are children. Files map to
 * text (or bytes) or nothing. Writes stamp [now] as last-modified and are recorded in [written].
 */
class FakeRomsSource(
    dirs: Map<String, List<String>>,
    texts: Map<String, String> = emptyMap(),
    bytes: Map<String, ByteArray> = emptyMap(),
    /** Mutable so a test can flip a read-only tree to writable mid-test. */
    var writable: Boolean = true,
    /** Path prefixes (a folder and everything under it) that refuse writes, as a read-only grant would. */
    private val readOnly: Set<String> = emptySet(),
) : RomsSource {
    private val dirs: MutableMap<String, MutableList<String>> = dirs.mapValues { it.value.toMutableList() }.toMutableMap()
    private val texts: MutableMap<String, String> = texts.toMutableMap()
    private val bytes: MutableMap<String, ByteArray> = bytes.toMutableMap()
    /** Last-modified times by path (none: the provider reports no time); writes stamp [now], and a test may set one. */
    val stamps = HashMap<String, Long>()
    var now: Long = 0L
    val written = ArrayList<String>()

    /** Simulates the provider fault: listings come back empty or throw [ListingUnavailable]; [child] still works. */
    var listingFault: ListingFault? = null

    private fun refuses(path: String) = !writable || readOnly.any { path == it || path.startsWith("$it/") }

    /** A file's reported size, by path (default 1), so a test can change a file without changing its bytes. */
    val sizes = HashMap<String, Long>()

    private fun entry(path: String, name: String) =
        Entry(ContentRef(path), name, isDirectory = dirs.containsKey(path), sizeBytes = if (dirs.containsKey(path)) null else sizes[path] ?: 1L, lastModified = stamps[path])

    /** How many directory listings were asked for: a scan lists, so a test can tell a rescan ran. */
    var listings = 0

    override suspend fun listChildren(dir: ContentRef): List<Entry> {
        listings++
        return when (listingFault) {
            ListingFault.EMPTY -> emptyList()
            ListingFault.UNAVAILABLE -> throw ListingUnavailable(dir.value)
            null -> (dirs[dir.value] ?: emptyList()).map { name -> entry("${dir.value}/$name", name) }
        }
    }

    /** Paths whose reads fail (null), as a provider error swallowed by SafRomsSource would; the file is still listed. */
    val unreadable = HashSet<String>()

    override suspend fun readText(file: ContentRef): String? = if (file.value in unreadable) null else texts[file.value]

    /** Direct lookup (no [listChildren] call), like the SAF fast path. */
    override suspend fun child(dir: ContentRef, name: String): Entry? {
        if (name !in (dirs[dir.value] ?: return null)) return null
        return entry("${dir.value}/$name", name)
    }

    override suspend fun readHead(file: ContentRef, max: Int): ByteArray? =
        if (file.value in unreadable) null else (bytes[file.value] ?: texts[file.value]?.encodeToByteArray())?.let { it.copyOfRange(0, minOf(max, it.size)) }

    override suspend fun readRange(file: ContentRef, offset: Long, length: Int): ByteArray? {
        if (file.value in unreadable) return null
        val all = bytes[file.value] ?: texts[file.value]?.encodeToByteArray() ?: return null
        if (offset >= all.size) return ByteArray(0)
        return all.copyOfRange(offset.toInt(), minOf(all.size, offset.toInt() + length))
    }

    /** When true, [moveFile] fails (a provider without a move), so a caller falls back to [copyFile]. */
    var moveRefused: Boolean = false

    /** Called at each [moveFile] (a test cancels a job there); [moveThrows] makes it throw instead. */
    var onMove: () -> Unit = {}
    var moveThrows: Boolean = false

    /** Folders [createDirectory] refuses, by path, so a move finds no target. */
    val readOnlyPaths = HashSet<String>()

    override suspend fun moveFile(fromDir: ContentRef, name: String, toDir: ContentRef): Boolean {
        if (moveThrows) throw IllegalStateException("provider failed")
        onMove()
        yield()
        if (moveRefused || !transfer(fromDir, name, toDir)) return false
        val from = "${fromDir.value}/$name"
        dirs.getValue(fromDir.value).remove(name)
        for (k in keysUnder(from)) { dirs.remove(k); texts.remove(k); bytes.remove(k); stamps.remove(k) }
        return true
    }

    override suspend fun copyFile(fromDir: ContentRef, name: String, toDir: ContentRef): Boolean = transfer(fromDir, name, toDir)

    /** Every path at or under [path] in this tree. */
    private fun keysUnder(path: String): Set<String> = (dirs.keys + texts.keys + bytes.keys).filter { it == path || it.startsWith("$path/") }.toSet()

    /** Copies [name] (a file, or a folder and everything under it) from [fromDir] into [toDir]; false when it is not there or the name is taken. */
    private fun transfer(fromDir: ContentRef, name: String, toDir: ContentRef): Boolean {
        val from = "${fromDir.value}/$name"
        val to = "${toDir.value}/$name"
        val target = dirs[toDir.value] ?: return false
        if (name !in (dirs[fromDir.value] ?: return false) || name in target || refuses(to)) return false
        for (k in keysUnder(from)) {
            val moved = to + k.removePrefix(from)
            dirs[k]?.let { dirs[moved] = it.toMutableList() }
            texts[k]?.let { texts[moved] = it }
            bytes[k]?.let { bytes[moved] = it.copyOf() }
            stamps[k]?.let { stamps[moved] = it }
        }
        target += name
        written += to
        return true
    }

    override suspend fun canWrite(tree: ContentRef): Boolean = !refuses(tree.value)

    /** Every [createDirectory] call by path (the folder made or found), so a test sees a walk repeated. */
    val createDirectoryCalls = HashMap<String, Int>()

    /** When true, [createDirectory] yields before it looks, so concurrent callers race on a missing folder as on SAF. */
    var yieldInCreate: Boolean = false

    override suspend fun createDirectory(parent: ContentRef, name: String): Entry? {
        val path = "${parent.value}/$name"
        createDirectoryCalls[path] = (createDirectoryCalls[path] ?: 0) + 1
        if (path in readOnlyPaths) return null
        if (yieldInCreate) yield()
        if (refuses(path)) return null
        val siblings = dirs[parent.value] ?: return null
        if (name !in siblings) { siblings += name; dirs[path] = ArrayList(); stamps[path] = now; written += path }
        return entry(path, name)
    }

    override suspend fun writeText(dir: ContentRef, name: String, text: String): Boolean {
        val path = "${dir.value}/$name"
        if (refuses(path)) return false
        val siblings = dirs[dir.value] ?: return false
        if (name !in siblings) siblings += name
        texts[path] = text
        stamps[path] = now
        written += path
        return true
    }

    /** When true, the rename step of [writeBytes] fails (as a provider refusing the rename would). */
    var failRename: Boolean = false

    /** `.part`, delete the old [name], then rename, as SafRomsSource: a failed rename leaves neither the part nor [name] (old or new). */
    override suspend fun writeBytes(dir: ContentRef, name: String, bytes: ByteArray): Boolean {
        val path = "${dir.value}/$name"
        val part = "$path.part"
        if (refuses(path)) return false
        val siblings = dirs[dir.value] ?: return false
        if ("$name.part" !in siblings) siblings += "$name.part"
        this.bytes[part] = bytes.copyOf()
        if (name in siblings) delete(path)   // the old file goes before the rename, as on SAF
        delete(part)
        if (failRename) return false
        if (name !in siblings) siblings += name
        texts.remove(path)
        this.bytes[path] = bytes.copyOf()
        stamps[path] = now
        written += path
        return true
    }

    override suspend fun delete(dir: ContentRef, name: String): Boolean {
        val path = "${dir.value}/$name"
        if (refuses(path) || name !in (dirs[dir.value] ?: return false) || dirs.containsKey(path)) return false
        delete(path)
        return true
    }

    override suspend fun deleteEmptyDirectory(parent: ContentRef, name: String): Boolean {
        val path = "${parent.value}/$name"
        if (refuses(path) || name !in (dirs[parent.value] ?: return false) || dirs[path]?.isEmpty() != true) return false
        dirs.remove(path); stamps.remove(path)
        dirs.getValue(parent.value).remove(name)
        return true
    }

    /** When true, [rename] fails (a provider without rename support). */
    var renameUnsupported: Boolean = false

    /** A one-step rename of a file, refused when [to] is taken (case-sensitive, as this tree is). */
    override suspend fun rename(dir: ContentRef, from: String, to: String): Boolean {
        val oldPath = "${dir.value}/$from"
        val newPath = "${dir.value}/$to"
        val siblings = dirs[dir.value] ?: return false
        if (renameUnsupported || refuses(oldPath) || refuses(newPath) || from !in siblings || dirs.containsKey(oldPath) || to in siblings) return false
        siblings[siblings.indexOf(from)] = to
        texts.remove(oldPath)?.let { texts[newPath] = it }
        bytes.remove(oldPath)?.let { bytes[newPath] = it }
        stamps.remove(oldPath)?.let { stamps[newPath] = it }
        written += newPath
        return true
    }

    /** Test helper: the bytes a file holds now (null when there is no such byte file). */
    fun bytesAt(path: String): ByteArray? = bytes[path]

    /** Test helper: deletes the file at [path], as a file manager outside the app would. */
    fun delete(path: String) {
        dirs[path.substringBeforeLast('/')]?.remove(path.substringAfterLast('/'))
        texts.remove(path); bytes.remove(path); stamps.remove(path)
    }

    /** Test helper: the names in folder [path] now (null when there is no such folder). */
    fun childNames(path: String): List<String>? = dirs[path]?.toList()

    /** Test helper: what a text file holds now. */
    fun textAt(path: String): String? = texts[path]
}

enum class ListingFault { EMPTY, UNAVAILABLE }

internal fun listedNames(src: FakeRomsSource, dir: String) =
    kotlinx.coroutines.runBlocking { src.listChildren(ContentRef(dir)).map { it.name } }

/**
 * A cue written in Windows-1252 that names its second track in another case: [FakeRomsSource.readText] gives what a
 * UTF-8 read makes of it (U+FFFD for each Latin-1 byte), its head the bytes as they are.
 */
internal val legacyCue = "FILE \"Pok\u00e9mon (Track 1).bin\" BINARY\n TRACK 01 MODE2/2352\nFILE \"pok\u00e9mon (track 2).BIN\" BINARY\n TRACK 02 AUDIO\n"
internal val legacyCueBytes = ByteArray(legacyCue.length) { legacyCue[it].code.toByte() }
internal val legacyCueUtf8 = legacyCue.replace('\u00e9', '\uFFFD')
