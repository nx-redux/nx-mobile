package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.PathLines
import com.nxredux.mobile.storage.SidecarStore
import kotlinx.coroutines.CancellationException

enum class CollectionWrite { ADDED, ALREADY, FAILED }

/** [Collections.rename]: done, refused (another collection has the name, ignoring case), or failed with nothing changed. */
enum class CollectionRename { RENAMED, EXISTS, FAILED }

/** [Collections.namesChecked]: the names, and false in [listed] when the home `Collections/` would not list. */
data class CollectionNames(val names: List<String>, val listed: Boolean)

/** [Collections.readStamped]: the lines (null: the read failed) and the stamp of the file they came from. */
data class StampedLines(val lines: List<String>?, val stamp: String?)

/**
 * The handheld's collections: `<home>/Collections/<Name>.txt`, one game path per line in file order. Writes go to the
 * home when it is writable (creating `Collections/` on first use), else to the app-private [store] under
 * `(home, "Collections", "<Name>.txt")`; a store copy is the newest record, so it is read first and rewritten after a
 * later successful home write. `map.txt` there is neither read nor written: names come from the index.
 */
class Collections(private val source: RomsSource, private val store: SidecarStore, private val home: () -> ContentRef?) {

    private fun file(name: String) = "$name.txt"

    private suspend fun folder(create: Boolean = false): ContentRef? {
        val h = home() ?: return null
        return if (create) source.ensureDirs(h, listOf(DIR)) else source.findDir(h, listOf(DIR))
    }

    /** The real name of [name]'s file in [folder]: the child matching `<name>.txt` ignoring case (as [names] lists it), else `<name>.txt`. */
    private suspend fun homeFile(folder: ContentRef, name: String): String = spelled(folder, name, filesOnly = false) ?: file(name)

    /** The home `Collections/` folder's `.txt` files (case-insensitive extension, hidden files and `map.txt` skipped) plus the store's, distinct and sorted ignoring case. */
    suspend fun names(): List<String> = namesChecked().names

    /**
     * [names], and whether the home folder listed: `listed = false` when the provider would not list it
     * ([ListingUnavailable]), so the names are only the store's and an empty list is no proof there are no collections.
     */
    suspend fun namesChecked(): CollectionNames {
        var listed = true
        val inHome = try {
            folder()?.let { dir -> source.listChildren(dir).let { recoverParts(dir, it) } }
                ?.filter { !it.isDirectory && it.name.endsWith(".txt", ignoreCase = true) && !Names.isHidden(it.name) }
                ?.map { it.name } ?: emptyList()
        } catch (e: CancellationException) { throw e } catch (e: ListingUnavailable) {
            listed = false
            emptyList()
        } catch (e: Throwable) { emptyList() }
        val names = (inHome + store.list(GamePath.HOME, DIR))
            .filter { it.endsWith(".txt", ignoreCase = true) }
            .map { it.dropLast(4) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        return CollectionNames(names, listed)
    }

    /** [name]'s lines, empty when it cannot be read (lenient, for display). */
    suspend fun read(name: String): List<String> = readOrNull(name) ?: emptyList()

    /**
     * [name]'s lines (empty when there is no such file), or null when the home read failed: it threw, or the file is
     * there but gave no text (SAF reports a failed read as null). A write never replaces a file it could not read; the
     * main menu leaves such a collection's count out.
     */
    suspend fun readOrNull(name: String): List<String>? = readStamped(name).lines

    /**
     * [readOrNull]'s lines, with the [CountStamps] of what was read, taken before the read (a file changed meanwhile then
     * fails to match later, so it is counted again). The stamp is null when the lines are, or the provider gave no stamp.
     */
    suspend fun readStamped(name: String): StampedLines {
        val stored = store.read(GamePath.HOME, DIR, file(name))
        if (stored != null) return StampedLines(PathLines.parse(stored), CountStamps.stored(stored))
        var stamp: String? = null
        val text = try {
            val f = folder()?.let { source.child(it, homeFile(it, name)) }?.takeIf { !it.isDirectory }
            if (f == null) { stamp = CountStamps.NONE; "" } else { stamp = CountStamps.file(f); source.readText(f.ref) }
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
        return StampedLines(text?.let(PathLines::parse), stamp.takeIf { text != null })
    }

    /**
     * Each of [names]' [CountStamps] as [readStamped] would take it, without reading a home file: one listing of the
     * home `Collections/` (a direct lookup per name where it would not list). Null for a name whose stamp is unknown.
     */
    suspend fun stamps(names: List<String>): Map<String, String?> {
        if (names.isEmpty()) return emptyMap()
        val dir = try { folder() } catch (e: CancellationException) { throw e } catch (e: Throwable) { return names.associateWith { null } }
        val listed = dir?.let {
            try { source.listChildren(it) } catch (e: CancellationException) { throw e } catch (e: ListingUnavailable) { null }
            catch (e: Throwable) { return names.associateWith { null } }
        }
        return names.associateWith { name ->
            try {
                val stored = store.read(GamePath.HOME, DIR, file(name))
                when {
                    stored != null -> CountStamps.stored(stored)
                    dir == null -> CountStamps.NONE
                    else -> {
                        val e = if (listed != null) listed.firstOrNull { it.name.equals(file(name), ignoreCase = true) } else source.child(dir, file(name))
                        if (e == null || e.isDirectory) CountStamps.NONE else CountStamps.file(e)
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
        }
    }

    suspend fun add(name: String, path: String): CollectionWrite {
        val current = readOrNull(name) ?: return CollectionWrite.FAILED
        if (path in current) return CollectionWrite.ALREADY
        return if (write(name, PathLines.render(PathLines.add(current, path)))) CollectionWrite.ADDED else CollectionWrite.FAILED
    }

    suspend fun remove(name: String, path: String): Boolean {
        val current = readOrNull(name) ?: return false
        return write(name, PathLines.render(PathLines.remove(current, path)))
    }

    /**
     * Device Sync's write of a merged collection: [lines] as [name] (its file matched ignoring case) when the collection
     * still reads as [base] (what the merge read; null: it had none, so an empty or absent file). One that reads otherwise
     * at write time (it appeared meanwhile, or under another case) is never replaced: the [lines] it lacks are appended.
     * False when it would not read or the write failed; true with nothing written when nothing is new.
     */
    suspend fun writeMerged(name: String, lines: List<String>, base: List<String>?): Boolean {
        val current = readOrNull(name) ?: return false
        val merged = if (current == base.orEmpty()) lines else current + lines.filter { it !in current }.distinct()
        if (merged == current && (base != null || current.isNotEmpty())) return true
        return write(name, PathLines.render(merged))
    }

    /**
     * [name]'s home file was replaced from outside the app (Device Sync's daemon): a store copy, which is read first,
     * takes in the home lines it lacks (appended), so the new lines show and none of its own is lost. Nothing happens
     * without a store copy or a readable home file.
     */
    suspend fun storeFollowsHome(name: String) {
        try {
            val stored = store.read(GamePath.HOME, DIR, file(name)) ?: return
            val homeLines = homeLines(name) ?: return
            val own = PathLines.parse(stored)
            val union = own + homeLines.filter { it !in own }
            if (union != own) store.write(GamePath.HOME, DIR, file(name), PathLines.render(union))
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { }
    }

    /**
     * Before Device Sync serves the home `Collections/` to a peer phone: each store copy whose lines differ from its home
     * file is folded into it (home lines, then the store lines they lack; a collection only in the store is written to
     * the home), so the served folder is the real record. A home file that would not read is left alone.
     */
    suspend fun foldStoreIntoHome() {
        try {
            for (f in store.list(GamePath.HOME, DIR).filter { it.endsWith(".txt", ignoreCase = true) }) {
                val name = f.dropLast(4)
                val stored = PathLines.parse(store.read(GamePath.HOME, DIR, f) ?: continue)
                val home = homeLines(name, absentAsEmpty = true) ?: continue
                if (home == stored) continue
                write(name, PathLines.render(home + stored.filter { it !in home }))
            }
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { }
    }

    /** [name]'s home file lines; null when it would not read or is absent ([absentAsEmpty]: absent is empty). */
    private suspend fun homeLines(name: String, absentAsEmpty: Boolean = false): List<String>? {
        val dir = folder()
        val f = dir?.let { source.child(it, homeFile(it, name)) }?.takeIf { !it.isDirectory }
            ?: return if (absentAsEmpty) emptyList() else null
        return source.readText(f.ref)?.let(PathLines::parse)
    }

    /**
     * Puts back a `<Name>.txt.part` a case-only [rename] left when both its last rename and the rename back failed, or a
     * replacing [RomsSource.writeText] stopped between deleting `<Name>.txt` and the rename (the collection would be
     * hidden otherwise): renamed to `<Name>.txt` when no `<Name>.txt` is there in any case, else left as it is (nothing
     * is overwritten). [children] is [dir]'s listing; the listing as it is after the recovery is returned.
     */
    private suspend fun recoverParts(dir: ContentRef, children: List<Entry>): List<Entry> {
        val parts = children.filter { !it.isDirectory && it.name.endsWith(PART_SUFFIX, ignoreCase = true) && !it.name.startsWith(".") }   // isHidden hides every .part
        if (parts.isEmpty()) return children
        var recovered = false
        for (part in parts) {
            val base = part.name.dropLast(".part".length)
            if (children.any { it.name.equals(base, ignoreCase = true) }) continue   // a file has the name: keep both
            val ok = try { source.rename(dir, part.name, base) } catch (e: CancellationException) { throw e } catch (e: Throwable) { false }
            if (ok) recovered = true
        }
        return if (recovered) source.listChildren(dir) else children
    }

    /** [name]'s file in the home [folder] as it is spelled there (any case of `<name>.txt`), or null when there is none. */
    private suspend fun existingHomeFile(folder: ContentRef, name: String): String? = spelled(folder, name, filesOnly = true)

    /**
     * The listed child of [folder] named `<name>.txt` in any case (a folder too unless [filesOnly]), the listing's first;
     * by its exact name when the provider will not list. Null when there is none.
     */
    private suspend fun spelled(folder: ContentRef, name: String, filesOnly: Boolean): String? = try {
        source.listChildren(folder).firstOrNull { (!filesOnly || !it.isDirectory) && it.name.equals(file(name), ignoreCase = true) }?.name
    } catch (e: ListingUnavailable) {
        source.child(folder, file(name))?.takeIf { !filesOnly || !it.isDirectory }?.name
    }

    /**
     * Renames the collection [from] to [to] (a valid name, see [Names.validCollectionName]): its home file and its store
     * copy, lines and order kept. The home file is renamed in one step where the platform can; a case-only rename
     * ("rpgs" to "RPGs") goes through `<to>.txt.part`, since a case-insensitive volume would see the target as taken.
     * Without a one-step rename the file is copied to the new name, then the old one deleted (the copy is deleted again
     * when that fails). [CollectionRename.EXISTS]: another collection is named [to] ignoring case. [CollectionRename.FAILED]:
     * there is no such collection, or a step failed; what was renamed is put back.
     */
    suspend fun rename(from: String, to: String): CollectionRename {
        if (from == to) return CollectionRename.RENAMED
        val caseOnly = from.equals(to, ignoreCase = true)
        try {
            if (!caseOnly && names().any { it.equals(to, ignoreCase = true) }) return CollectionRename.EXISTS
            val dir = folder()
            dir?.let { d -> try { recoverParts(d, source.listChildren(d)) } catch (e: ListingUnavailable) { null } }
            val homeName = dir?.let { existingHomeFile(it, from) }
            val stored = store.read(GamePath.HOME, DIR, file(from))
            if (homeName == null && stored == null) return CollectionRename.FAILED
            if (dir != null && homeName != null) {
                if (!caseOnly && source.child(dir, file(to)) != null) return CollectionRename.EXISTS   // a listing that missed it
                if (!renameHome(dir, homeName, file(to), caseOnly)) return CollectionRename.FAILED
            }
            if (stored != null && !renameStored(from, to, stored)) {
                if (dir != null && homeName != null) renameHome(dir, file(to), homeName, caseOnly)   // put the home file back
                return CollectionRename.FAILED
            }
            return CollectionRename.RENAMED
        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            return CollectionRename.FAILED
        }
    }

    private suspend fun renameHome(dir: ContentRef, from: String, to: String, caseOnly: Boolean): Boolean {
        if (caseOnly) {
            val part = "$to.part"
            if (!source.rename(dir, from, part)) return false
            if (source.rename(dir, part, to)) return true
            source.rename(dir, part, from)
            return false
        }
        if (source.rename(dir, from, to)) return true
        val text = source.child(dir, from)?.let { source.readText(it.ref) } ?: return false
        if (source.child(dir, to) != null) return false
        if (!source.writeText(dir, to, text)) { source.delete(dir, to); return false }   // an empty file the create left
        if (source.delete(dir, from)) return true
        source.delete(dir, to)
        return false
    }

    /**
     * The store copy under its new name; the old one is deleted only once the store lists both (a case-insensitive store
     * has one). A delete that fails twice would leave the old name listed beside the new one, so the new copy is deleted
     * again and false returned (the caller puts the home file back): the old copy, which was never touched, stays.
     */
    private suspend fun renameStored(from: String, to: String, text: String): Boolean {
        if (!store.write(GamePath.HOME, DIR, file(to), text)) return false
        val listed = store.list(GamePath.HOME, DIR)
        if (file(from) !in listed || file(to) !in listed) return true
        if (store.delete(GamePath.HOME, DIR, file(from)) || store.delete(GamePath.HOME, DIR, file(from))) return true
        store.delete(GamePath.HOME, DIR, file(to))
        return false
    }

    /**
     * Deletes the collection [name]: its home file, then its store copy. Only that file: the games it lists are untouched.
     * False when there is no such collection or the home file could not be deleted (the store copy is then kept).
     */
    suspend fun delete(name: String): Boolean = try {
        val dir = folder()
        val homeName = dir?.let { existingHomeFile(it, name) }
        val stored = store.read(GamePath.HOME, DIR, file(name)) != null
        when {
            homeName == null && !stored -> false
            dir != null && homeName != null && !source.delete(dir, homeName) -> false
            stored -> store.delete(GamePath.HOME, DIR, file(name))
            else -> true
        }
    } catch (e: CancellationException) { throw e } catch (e: Throwable) { false }

    private suspend fun write(name: String, text: String): Boolean {
        val stored = store.read(GamePath.HOME, DIR, file(name))
        val inHome = try {
            val h = home()
            h != null && source.canWrite(h) && folder(create = true)?.let { source.writeText(it, homeFile(it, name), text) } == true
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { false }
        if (inHome) {
            if (stored != null) store.write(GamePath.HOME, DIR, file(name), text)
            return true
        }
        return store.write(GamePath.HOME, DIR, file(name), text)
    }

    companion object {
        const val DIR = "Collections"
        private const val PART_SUFFIX = ".txt.part"
    }
}
