package com.nxredux.mobile.sync

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.CoreRegistry

/**
 * One console both devices have: this phone's `Roms/` folder [local], the peer's [remote] (names may differ), [remote]'s
 * [tag]. [pushTags]: the emulator tags whose games this phone's home push sends to [remote] (a handheld peer's mapped
 * folders, [SyncConsoles.pairMapped]); a peer phone's pair pushes every game.
 */
data class ConsolePair(val local: String, val remote: String, val tag: String, val pushTags: Set<String> = setOf(tag)) {
    /** Identifies the pair among several sharing [local]. */
    val key: String get() = "$local\u0000$remote"
}

/** [pairs] in [SyncConsoles.pair] order; [skippedTags] the consoles left alone (sorted, distinct), for Done. */
data class Pairing(val pairs: List<ConsolePair>, val skippedTags: List<String>)

/** Which `Roms/<Name (TAG)>` folders sync. */
object SyncConsoles {
    /**
     * Same name → paired; then a tag (case-insensitive) found exactly once among each side's unpaired folders → paired;
     * the rest skipped. Only `Name (TAG)` folders count, dot names never.
     */
    fun pair(local: List<String>, remote: List<String>): Pairing {
        fun systems(names: List<String>) = names.filter { !it.startsWith(".") }
            .mapNotNull { n -> Names.parseSystemFolder(n)?.let { n to it.second.uppercase() } }
        val l = systems(local); val r = systems(remote)
        val remoteNames = r.map { it.first }.toSet()
        val pairs = ArrayList<ConsolePair>()
        l.filter { it.first in remoteNames }.forEach { (n, t) -> pairs += ConsolePair(n, n, t) }
        val paired = pairs.map { it.local }.toSet()
        val lRest = l.filter { it.first !in paired }
        val rRest = r.filter { it.first !in paired }
        val skipped = sortedSetOf<String>()
        for ((n, t) in lRest) {
            val mine = lRest.filter { it.second == t }
            val theirs = rRest.filter { it.second == t }
            if (mine.size == 1 && theirs.size == 1) pairs += ConsolePair(n, theirs[0].first, t) else skipped += t
        }
        val pairedRemote = pairs.map { it.remote }.toSet()
        rRest.filter { it.first !in pairedRemote }.forEach { skipped += it.second }
        return Pairing(pairs, skipped.toList())
    }

    /**
     * A handheld peer's pairing: both sides' system folders grouped by their phone console ([SyncMap.consoleOf]). Per
     * console both sides have, every remote folder pairs with one landing folder here ([landing]), and each of the
     * console's tags pushes its games to exactly one remote folder ([pushTargets]). Every other remote folder only pulls;
     * other folders here of the console are not paired. A folder of no phone console pairs only with the peer's folder of
     * the same name, as [pair] does; every other folder is skipped (its tag).
     */
    fun pairMapped(local: List<String>, remote: List<String>): Pairing {
        fun systems(names: List<String>) = names.filter { !it.startsWith(".") }.mapNotNull { n ->
            Names.parseSystemFolder(n)?.let { (display, tag) -> Mapped(n, display, tag.uppercase(), SyncMap.consoleOf(n)) }
        }
        val l = systems(local); val r = systems(remote)
        val remoteNames = r.map { it.name }.toSet()
        val pairs = ArrayList<ConsolePair>()
        val paired = HashSet<String>()
        val consoles = (l + r).mapNotNull { it.console }.distinctBy { it.id }
        for (c in consoles) {
            val mine = l.filter { it.console?.id == c.id }
            val theirs = r.filter { it.console?.id == c.id }
            if (mine.isEmpty() || theirs.isEmpty()) continue
            paired += c.id
            val landing = landing(c, mine.map { it.name }, remoteNames)!!
            val targets = pushTargets(c, theirs.map { it.name }, landing)
            theirs.forEach { f -> pairs += ConsolePair(landing, f.name, f.tag, c.tags.filter { targets[it] == f.name }.toSet()) }
        }
        val bare = l.filter { it.console == null && it.name in remoteNames }.map { it.name }.toSet()
        bare.forEach { n -> pairs += ConsolePair(n, n, l.first { it.name == n }.tag) }
        val skipped = (l + r).filter { if (it.console == null) it.name !in bare else it.console.id !in paired }.map { it.tag }.toSortedSet()
        return Pairing(pairs, skipped.toList())
    }

    /**
     * Console [c]'s landing folder among this phone's folders [mine] of it ([pairMapped]): the first named by the console
     * tag that the peer also has ([remoteNames]), else the first named by the console tag, else the first. Null for none.
     */
    fun landing(c: Console, mine: List<String>, remoteNames: Set<String>): String? {
        val tagged = mine.filter { Names.parseSystemFolder(it)?.second?.uppercase() == c.defaultTag }
        return tagged.firstOrNull { it in remoteNames } ?: tagged.firstOrNull() ?: mine.firstOrNull()
    }

    /**
     * Where a handheld peer's copy of console [c]'s games goes ([pairMapped]): each of [Console.tags] → the one folder of
     * [theirs] (the peer's folders of [c]) its games push to. A tag the peer has a folder of (by its handheld tag,
     * [CoreRegistry.handheldTag]: GPGX_SMS's is a `(GPGX)` folder) goes there (several: the one named like the console or
     * [landing], ignoring case, else the first listed); every other tag goes
     * with the folder of the first tag in [Console.tags] order the peer has (none: the same choice among all of
     * [theirs]). Empty when [theirs] holds no system folder. The battery saves plan stages a save by the same targets.
     */
    fun pushTargets(c: Console, theirs: List<String>, landing: String?): Map<String, String> {
        val fs = theirs.filter { !it.startsWith(".") }.mapNotNull { n ->
            Names.parseSystemFolder(n)?.let { (display, tag) -> Mapped(n, display, tag.uppercase(), c) }
        }
        if (fs.isEmpty()) return emptyMap()
        val landingDisplay = landing?.let { Names.parseSystemFolder(it)?.first }
        fun preferred(xs: List<Mapped>) = xs.firstOrNull {
            it.display.equals(c.displayName, ignoreCase = true) || it.display.equals(landingDisplay, ignoreCase = true)
        } ?: xs.first()
        // A phone tag meets the peer's folder tags as the handheld's (GPGX_SMS: a (GPGX) folder).
        fun holder(tag: String) = CoreRegistry.handheldTag(tag).let { h -> fs.filter { it.tag == h } }.takeIf { it.isNotEmpty() }?.let(::preferred)
        val target = c.tags.firstNotNullOfOrNull(::holder) ?: preferred(fs)
        return c.tags.associateWith { (holder(it) ?: target).name }
    }

    /** A system folder of either side: its [name], [display] name, folder [tag] (uppercase) and phone [console], if any. */
    private class Mapped(val name: String, val display: String, val tag: String, val console: Console?)

    private val BLANKS = Regex("\\s+")

    /** A `--list-only` line's columns: mode, size, date, time, then the name whole (it may hold spaces). */
    private fun fields(line: String): List<String> = line.trim().split(BLANKS, limit = 5)

    /** `rsync --list-only rsync://…/roms/` lines → the top-level directory names (not `.`). */
    fun parseListing(lines: List<String>): List<String> = lines.mapNotNull { line ->
        if (!line.startsWith("d")) return@mapNotNull null
        val parts = fields(line)
        parts.getOrNull(4)?.let(::unescape)?.takeIf { it != "." && it.isNotEmpty() }
    }

    /** `--list-only -R` lines of `<folder>/.retroachievements/hashes.json` → the folders that have one. */
    fun parseHashFolders(lines: List<String>): Set<String> = lines.mapNotNull { line ->
        if (!line.startsWith("-")) return@mapNotNull null
        val path = fields(line).getOrNull(4)?.let(::unescape) ?: return@mapNotNull null
        path.removeSuffix(HASHES_TAIL).takeIf { it != path && it.isNotEmpty() && "/" !in it }
    }.toSet()

    private const val HASHES_TAIL = "/.retroachievements/hashes.json"

    /**
     * The other system folders in [all] of [folder]'s console: the same display name before ` (TAG)`, ignoring case
     * (`Game Boy Advance (GBA)` / `Game Boy Advance (MGBA)`). None for a name that is not a `Name (TAG)` folder.
     */
    fun siblings(folder: String, all: List<String>): List<String> {
        val display = Names.parseSystemFolder(folder)?.first ?: return emptyList()
        return all.filter { n ->
            n != folder && !n.startsWith(".") && Names.parseSystemFolder(n)?.first?.equals(display, ignoreCase = true) == true
        }
    }

    /** [siblings] by display name, and when [mapped] the folders of the same phone console too. */
    fun siblings(folder: String, all: List<String>, mapped: Boolean): List<String> {
        val named = siblings(folder, all)
        if (!mapped) return named
        val console = SyncMap.consoleOf(folder)?.id ?: return named
        return (named + all.filter { it != folder && !it.startsWith(".") && SyncMap.consoleOf(it)?.id == console }).distinct()
    }

    /**
     * `rsync --list-only rsync://…/roms/<folder>/` lines → every top-level entry name, files and folders (not `.`). Only
     * a line that starts with a mode string (`-rw-r--r--`, `drwxr-xr-x`, …) is an entry, so a daemon MOTD or a warning
     * line is never a name.
     */
    fun parseEntries(lines: List<String>): List<String> = lines.mapNotNull { line ->
        val parts = fields(line)
        if (parts.size < 5 || !MODE.matches(parts[0])) return@mapNotNull null
        val name = if (line.startsWith("l")) parts[4].substringBefore(" -> ") else parts[4]
        unescape(name).takeIf { it != "." && it.isNotEmpty() }
    }

    /** A `--list-only` mode string: the entry type (`-dlpcbs`) and nine permission characters. */
    private val MODE = Regex("""[-dlpcbs]\S{9}""")

    private val BYTE_ESCAPE = Regex("""\\#([0-7]{3})""")

    /** rsync's `\#ooo` byte escapes (output without `--8-bit-output`) back to the UTF-8 name; a name without them is unchanged. */
    private fun unescape(name: String): String {
        if (!BYTE_ESCAPE.containsMatchIn(name)) return name
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < name.length) {
            val m = BYTE_ESCAPE.matchAt(name, i)
            if (m != null) { bytes += m.groupValues[1].toInt(8).toByte(); i = m.range.last + 1 }
            else { name[i].toString().encodeToByteArray().forEach { bytes += it }; i++ }
        }
        return bytes.toByteArray().decodeToString()
    }
}
