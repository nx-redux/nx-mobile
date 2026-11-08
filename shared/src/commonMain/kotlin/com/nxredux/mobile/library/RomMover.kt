package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * One game to move: [files] (its sheet first, then what the sheet loads) from [fromDir] into the home's
 * `Roms/<[targetFolder]>`. A disc folder moves as a folder: [subdir] is its name (made inside the target) and
 * [fromParent] the folder holding it, which loses it once it is empty.
 */
data class MoveItem(
    val fromDir: ContentRef, val files: List<String>, val targetFolder: String,
    val subdir: String? = null, val fromParent: ContentRef? = null,
    val relativeDir: String? = null,   // a home's outside game: its folder from the home ([planLoose])
)

/**
 * How a move went: games [moved]; [clashed] (a name already in the target: left in place); [failed] (no target folder,
 * or a file that would not move: left in place); and whether it was [cancelled] before the rest.
 */
data class MoveResult(val moved: Int = 0, val clashed: Int = 0, val failed: Int = 0, val cancelled: Boolean = false)

/**
 * Moves an extra folder's placed games into the home's `Roms/<Console (TAG)>` folders, the handheld's layout. A game
 * moves whole (a cue with its bins) or not at all; an unassigned game is never moved. A provider move is tried first,
 * then a copy and a delete of the original.
 */
class RomMover(private val source: RomsSource, private val home: ContentRef) {
    /** [extraId]'s placed games in [index], each with its files and the home folder its console's games live in. */
    suspend fun plan(index: LibraryIndex, extraId: String): List<MoveItem> {
        val items = ArrayList<MoveItem>()
        for (system in index.systems) {
            if (system.unassigned) continue
            val target = system.folderName.ifEmpty {
                val console = CoreRegistry.consoleOf(system.tag) ?: return@ifEmpty ""
                "${console.displayName} (${console.tags.first()})"
            }
            if (target.isEmpty()) continue
            for (game in system.games) {
                if (game.source != extraId) continue
                val dir = ContentRef(game.folder)
                items += MoveItem(dir, listOf(game.file) + DiscSheet.loadedNames(source, dir, game.file), target)
            }
        }
        return items
    }

    /**
     * A home's games outside `Roms/` ([LibraryScanner.scanOutsideRoms]), placed ones only, each into its console's folder:
     * the one [index] names for it, else `<Console> (<first tag>)`. A disc folder's game is the folder, whole.
     */
    suspend fun planLoose(files: List<LooseFile>, index: LibraryIndex): List<MoveItem> {
        val folders = index.systems.filter { !it.unassigned && it.folderName.isNotEmpty() }
            .associate { CoreRegistry.consoleTag(it.tag) to it.folderName }
        val items = ArrayList<MoveItem>()
        for (f in files) {
            val tag = f.tag ?: continue
            val console = CoreRegistry.consoleOf(tag) ?: continue
            val target = folders[CoreRegistry.consoleTag(tag)] ?: "${console.displayName} (${console.tags.first()})"
            if (f.lookupFolder != f.folder) {
                val all = source.listChildren(f.folder).filter { !it.isDirectory }.map { it.name }
                items += MoveItem(f.folder, all, target, subdir = f.relativeDir.substringAfterLast('/'), fromParent = f.lookupFolder, relativeDir = f.relativeDir)
            } else {
                items += MoveItem(f.folder, listOf(f.entry.name) + DiscSheet.loadedNames(source, f.folder, f.entry.name), target, relativeDir = f.relativeDir)
            }
        }
        return items
    }

    /**
     * Removes the home's folders [relativeDirs] (and their parents, up to the home itself) that a move left empty,
     * deepest first; a folder holding anything stays. A folder that is or holds one of [keep] (where a game stayed) is
     * never tried: a listing that wrongly comes back empty would otherwise delete it with the game in it.
     */
    suspend fun pruneEmpty(home: ContentRef, relativeDirs: Collection<String>, keep: Collection<String> = emptyList()) {
        val all = relativeDirs.filter { it.isNotEmpty() }.flatMap { d ->
            val parts = d.split('/')
            parts.indices.map { i -> parts.take(i + 1).joinToString("/") }
        }
        for (dir in all.toSet().sortedByDescending { d -> d.count { it == '/' } }) {
            if (keep.any { it.equals(dir, true) || it.startsWith("$dir/", true) }) continue
            val parent = source.findDir(home, dir.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }) ?: continue
            source.deleteIfSurelyEmpty(parent, dir.substringAfterLast('/'))
        }
    }

    /** Moves [items] in order, [onProgress] after each; [cancelled] is asked before each, so a cancel stops between games. */
    suspend fun run(
        items: List<MoveItem>, onProgress: (done: Int, total: Int) -> Unit,
        onMoved: (MoveItem) -> Unit = {},   // each item that moved whole
        cancelled: () -> Boolean,
    ): MoveResult {
        var r = MoveResult()
        val roms = source.createDirectory(home, "Roms")?.ref
        for ((i, item) in items.withIndex()) {
            if (cancelled()) return r.copy(cancelled = true)
            // One game at a time, whole: a coroutine cancelled mid-set (the app closing) still finishes or undoes it.
            val outcome = withContext(NonCancellable) {
                val folder = roms?.let { source.createDirectory(it, item.targetFolder)?.ref }
                // A disc folder moves as a folder: one of its name already there is a clash, never merged into.
                val taken = item.subdir != null && folder != null && source.child(folder, item.subdir) != null
                val target = if (taken) null else item.subdir?.let { s -> folder?.let { source.createDirectory(it, s)?.ref } } ?: folder.takeIf { item.subdir == null }
                when {
                    taken -> Outcome.CLASHED
                    target == null -> Outcome.FAILED
                    else -> moveWhole(item, target).also { o ->
                        // A disc folder all moved: its old folder goes; one that did not: the new one made for it goes.
                        if (item.subdir != null) {
                            if (o == Outcome.MOVED) item.fromParent?.let { source.deleteIfSurelyEmpty(it, item.subdir) }
                            else folder?.let { source.deleteIfSurelyEmpty(it, item.subdir) }
                        }
                    }
                }
            }
            r = when (outcome) {
                Outcome.MOVED -> { onMoved(item); r.copy(moved = r.moved + 1) }
                Outcome.CLASHED -> r.copy(clashed = r.clashed + 1)
                Outcome.FAILED -> r.copy(failed = r.failed + 1)
            }
            onProgress(i + 1, items.size)
            currentCoroutineContext().ensureActive()
        }
        return r
    }

    private enum class Outcome { MOVED, CLASHED, FAILED }

    /** All of [item]'s files into [target], or none: a name taken there refuses it, and a file that fails puts back what moved. */
    private suspend fun moveWhole(item: MoveItem, target: ContentRef): Outcome {
        val taken = try { source.listChildren(target).map { it.name.lowercase() }.toSet() } catch (e: Exception) { return Outcome.FAILED }
        if (item.files.any { it.lowercase() in taken }) return Outcome.CLASHED
        val done = ArrayList<String>()
        for (name in item.files) {
            if (moveOne(item.fromDir, name, target)) { done += name; continue }
            for (back in done.asReversed()) moveOne(target, back, item.fromDir)
            return Outcome.FAILED
        }
        return Outcome.MOVED
    }

    /** [name] from [from] into [to]; any error is a failure (false), never a throw. */
    private suspend fun moveOne(from: ContentRef, name: String, to: ContentRef): Boolean = try {
        moveOrCopy(from, name, to)
    } catch (e: Exception) {
        false
    }

    private suspend fun moveOrCopy(from: ContentRef, name: String, to: ContentRef): Boolean {
        if (source.moveFile(from, name, to)) return true
        if (!source.copyFile(from, name, to)) return false
        if (source.delete(from, name)) return true
        source.delete(to, name)   // the original would not go: no second copy
        return false
    }
}
