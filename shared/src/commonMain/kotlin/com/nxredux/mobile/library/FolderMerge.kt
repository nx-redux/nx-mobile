package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.TabLines
import com.nxredux.mobile.storage.TextLines

/**
 * A console whose home games lie in more than one `Roms/` folder (the handheld's split: `Game Boy Advance (GBA)` and
 * `(MGBA)`): [main], the folder the scan names the system by, and the [others] holding its games.
 */
data class SplitFolders(val console: String, val main: String, val others: List<String>)

/**
 * The home's [SplitFolders] in [index], by console. Only a folder whose own name places the same console counts as an
 * other (a `Misc (MISC)` folder whose `systems.txt` tags games for several consoles is no part of a split), and only
 * when [main] itself holds games (an empty one setup made is no second folder: the user's only folder stays).
 */
fun splitFolders(index: LibraryIndex): List<SplitFolders> {
    class Acc(val main: String, val others: LinkedHashSet<String> = LinkedHashSet(), var mainHas: Boolean = false)
    val out = LinkedHashMap<String, Acc>()
    for (system in index.systems) {
        if (system.unassigned || system.folderName.isEmpty()) continue
        val key = CoreRegistry.consoleTag(system.tag)
        val console = CoreRegistry.consoleOf(system.tag)
        val acc = out.getOrPut(key) { Acc(system.folderName) }
        for (g in system.games) {
            if (g.source != Recents.HOME) continue
            val folder = g.dir.split('/').getOrNull(1) ?: continue
            if (folder.equals(acc.main, ignoreCase = true)) { acc.mainHas = true; continue }
            val own = Names.parseSystemFolder(folder)?.let { (d, t) -> CoreRegistry.consoleOfFolder(d, t) }
            if (console != null && own == console) acc.others += folder
        }
    }
    return out.filter { it.value.mainHas && it.value.others.isNotEmpty() }.map { (k, v) -> SplitFolders(k, v.main, v.others.toList()) }
}

/**
 * Merges each [SplitFolders]' other folders into its main one, game by game from the [LibraryIndex] (so files that are
 * no game stay, and a cue moves with its bins): each moves whole unless a name it needs is taken there (a disc folder
 * whose name main has is taken too); the other folder's `.media` files of the moved games go to the same place in
 * main's `.media`; its `map.txt` and `systems.txt` lines for them are added to main's; a folder left empty is removed.
 */
class FolderMerge(private val source: RomsSource, private val home: ContentRef) {
    /**
     * The merge's result. Each moved game's old line → new line ([GamePath] form) goes into [moved] as it moves, so a
     * failure or a stop later still leaves the caller every line to rewrite.
     */
    suspend fun merge(
        index: LibraryIndex, splits: List<SplitFolders>, moved: MutableMap<String, String>,
        onProgress: (done: Int, total: Int) -> Unit, cancelled: () -> Boolean,
    ): Pair<MoveResult, Map<String, String>> {
        val roms = source.child(home, "Roms")?.ref ?: return MoveResult() to moved
        val mover = RomMover(source, home)
        val items = ArrayList<MoveItem>()
        val paths = HashMap<MoveItem, MutableList<Pair<String, String>>>()
        val movedNames = HashMap<String, MutableList<String>>()   // other folder → its moved names (files, disc folders)
        val homeGames = index.systems.filter { !it.unassigned }.flatMap { s -> s.games.filter { it.source == Recents.HOME } }
        for (split in splits) for (other in split.others) {
            val from = source.child(roms, other)?.ref ?: continue
            val prefix = "Roms/$other"
            val folders = HashMap<String, MoveItem>()   // a subfolder moves once, as a folder, with every game in it
            for (g in homeGames) {
                if (!g.dir.equals(prefix, true) && !g.dir.startsWith("$prefix/", true)) continue
                val disc = g.dir.substringAfter("$prefix/", "").takeIf { it.isNotEmpty() && '/' !in it }
                val item = if (disc != null) {
                    folders[disc.lowercase()] ?: run {
                        val dir = source.child(from, disc)?.ref ?: return@run null
                        MoveItem(dir, source.listChildren(dir).filter { !it.isDirectory }.map { it.name }, split.main, subdir = disc, fromParent = from)
                            .also { folders[disc.lowercase()] = it; items += it }
                    } ?: continue
                } else {
                    if ('/' in g.dir.removePrefix(prefix).trimStart('/')) continue
                    MoveItem(from, listOf(g.file) + DiscSheet.loadedNames(source, from, g.file), split.main).also { items += it }
                }
                paths.getOrPut(item) { ArrayList() } += line(other, disc, g.file) to line(split.main, disc, g.file)
            }
        }
        val movedItems = HashSet<MoveItem>()
        var movedGames = 0
        val result = mover.run(items, onProgress, onMoved = { item ->
            movedItems += item
            paths[item]?.let { ps -> ps.forEach { (a, b) -> moved[a] = b }; movedGames += ps.size }
            val key = (item.fromParent ?: item.fromDir).value
            movedNames.getOrPut(key) { ArrayList() } += item.subdir?.let { listOf(it) } ?: item.files
        }, cancelled = cancelled)
        // A folder where anything planned stayed (a clash, a failure, a stop) is never tried for removal.
        val stayedIn = items.filter { it !in movedItems }.map { (it.fromParent ?: it.fromDir).value }.toSet()
        for (split in splits) {
            val main = source.child(roms, split.main)?.ref ?: continue
            for (other in split.others) {
                val from = source.child(roms, other)?.ref ?: continue
                val names = movedNames[from.value].orEmpty().map { it.lowercase() }.toSet()
                if (names.isEmpty()) continue
                moveMedia(from, main, names.map { Names.stripExtension(it) }.toSet() + names)
                for (file in listOf(Sidecars.MAP_TXT, SidecarTxt.FILE)) carryLines(from, main, file, names)
                if (from.value !in stayedIn) source.deleteIfSurelyEmpty(roms, other)
            }
        }
        return result.copy(moved = movedGames) to moved
    }

    private fun line(folder: String, discDir: String?, file: String): String =
        GamePath.of(GameRef(Recents.HOME, "Roms/$folder" + (discDir?.let { "/$it" } ?: ""), file))

    /**
     * [from]'s `.media` files of the moved games (by base name, any case: [bases]) into [to]'s, sub-paths kept, none over
     * a taken name; emptied folders removed.
     */
    private suspend fun moveMedia(from: ContentRef, to: ContentRef, bases: Set<String>) {
        val media = source.child(from, ".media")?.ref ?: return
        // Whether anything moved out of [dir] (or below it): only such a folder is tried for removal.
        suspend fun walk(dir: ContentRef, into: suspend () -> ContentRef?): Boolean {
            var any = false
            for (c in source.listChildren(dir)) {
                if (c.isDirectory) {
                    var sub: ContentRef? = null
                    if (walk(c.ref) { sub ?: into()?.let { p -> source.createDirectory(p, c.name)?.ref }.also { sub = it } }) {
                        any = true
                        source.deleteIfSurelyEmpty(dir, c.name)
                    }
                } else if (Names.stripExtension(c.name).lowercase() in bases) {
                    val target = into() ?: continue
                    if (source.child(target, c.name) != null) continue
                    if (source.moveFile(dir, c.name, target) || (source.copyFile(dir, c.name, target) && source.delete(dir, c.name))) any = true
                }
            }
            return any
        }
        var top: ContentRef? = null
        if (walk(media) { top ?: source.createDirectory(to, ".media")?.ref.also { top = it } }) source.deleteIfSurelyEmpty(from, ".media")
    }

    /** [from]'s `file` lines (`name\tvalue`) for the moved [names] added to [to]'s, unless it has the name; [from]'s goes when nothing else is in it. */
    private suspend fun carryLines(from: ContentRef, to: ContentRef, file: String, names: Set<String>) {
        val text = source.child(from, file)?.let { source.readText(it.ref) } ?: return
        val lines = TextLines.split(text).filter { it.isNotBlank() }
        val (going, staying) = lines.partition { TabLines.key(it).lowercase() in names }
        if (going.isEmpty()) return
        // Main's file there but unreadable is not an empty one: writing would replace it with only the carried lines, so
        // they stay in [from] (whose folder then keeps the file, and so is not removed).
        val mine = source.child(to, file)?.let { source.readText(it.ref) ?: return } ?: ""
        val have = TextLines.split(mine).map { TabLines.key(it).lowercase() }.toSet()
        val add = going.filter { TabLines.key(it).lowercase() !in have }
        if (add.isNotEmpty() && !source.writeText(to, file, mine.trimEnd('\n').let { if (it.isEmpty()) "" else "$it\n" } + add.joinToString("\n") + "\n")) return
        if (staying.isEmpty()) source.delete(from, file) else source.writeText(from, file, staying.joinToString("\n") + "\n")
    }
}

/** [lines] with each naming a moved game (`path` or `path\t…`) moved too ([moved]: old path → new path). */
fun rewriteLines(lines: List<String>, moved: Map<String, String>): List<String> = lines.map { l -> TabLines.mapKey(l, moved::get) }

/**
 * Removes the folder [name] in [parent] when it is empty, but only while [parent]'s listing still shows it: a provider
 * whose listings come back empty (Android 17's fault on the Fold) would otherwise read a full folder as empty, and a
 * SAF directory delete takes everything in it.
 */
internal suspend fun RomsSource.deleteIfSurelyEmpty(parent: ContentRef, name: String): Boolean {
    val dir = listChildren(parent).firstOrNull { it.name == name && it.isDirectory } ?: return false
    if (listChildren(dir.ref).isNotEmpty()) return false
    return deleteEmptyDirectory(parent, name)
}
