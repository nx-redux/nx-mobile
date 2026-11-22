package com.nxredux.mobile.runner

import com.nxredux.mobile.bios.KnownBios
import com.nxredux.mobile.bios.KnownBiosEntry
import com.nxredux.mobile.library.Names

/** A BIOS file as it is in the core's folder; [md5] null when it could not be read (never judged). */
data class BiosFile(val name: String, val size: Long, val md5: String?)

/**
 * What a BIOS file must hold: a dump with one of [md5s], named in messages as [what] ("the US Sega CD BIOS"). [sizes]
 * empty: the size is not judged. [regionLocked]: a dump of the same console under another name cannot stand in.
 * [unlistedWarns] false: names are free and revisions many, so a dump the table does not list says nothing.
 */
data class BiosExpect(
    val what: String,
    val consoles: Set<String>,
    val md5s: Set<String>,
    val sizes: Set<Long>,
    val regionLocked: Boolean = false,
    val unlistedWarns: Boolean = true,
)

/** One file the core may read for a need, and what it must hold. */
data class BiosCandidate(val name: String, val expect: BiosExpect)

/** A present BIOS file that is not a known good dump. [refuses]: the core cannot run with it, so the launch stops. */
sealed interface BiosProblem {
    val file: BiosFile
    val expect: BiosExpect
    val refuses: Boolean

    /** A size no good dump has: cut short, overdumped or another file altogether. */
    data class WrongSize(override val file: BiosFile, override val expect: BiosExpect) : BiosProblem {
        override val refuses get() = true
    }

    /** A known dump of another file: another console's BIOS or region always fails; another card of the same console may not. */
    data class OtherDump(override val file: BiosFile, override val expect: BiosExpect, val other: KnownBiosEntry) : BiosProblem {
        override val refuses get() = expect.regionLocked || other.console !in expect.consoles
    }

    /** The right size, but a dump the table does not list: a revision it misses, or a bad dump. */
    data class Unlisted(override val file: BiosFile, override val expect: BiosExpect) : BiosProblem {
        override val refuses get() = false
    }
}

/**
 * The launch's checksum check of the required BIOS that is present ([BiosCheck] has already asked for what is missing),
 * against the bundled table of good dumps ([KnownBios]). A file the table does not know, an archive, or one that could
 * not be read is accepted, so the check never stands in the way of a dump it cannot judge.
 */
object BiosChecksum {
    /**
     * The files to hash for [needs] under a game with extension [ext], one group per need: the present names the core
     * may read for it. A Sega CD need with a known [region] narrows to the names [coreId] loads for that region; an
     * any-suffix need takes [pick] while it is present, else every match. A group holding a file the table does not know
     * is left out (that file may be the one loaded), as is an empty one.
     */
    fun toCheck(
        known: KnownBios, tag: String, needs: List<BiosNeed>, ext: String, present: Set<String>,
        coreId: String, region: DiscRegion?, pick: String?,
    ): List<List<BiosCandidate>> {
        val e = ext.lowercase()
        return needs.filter { it.forExtensions.isEmpty() || e in it.forExtensions }.mapNotNull { need ->
            val names: List<String>
            val expectOf: (String) -> BiosExpect?
            when {
                region != null && SegaCdBios.isSegaCdNeed(need) -> {
                    names = SegaCdBios.accepted(coreId, region).filter { it in present }
                    val expect = segaCd(known, region)
                    expectOf = { if (Names.isArchive(it)) null else expect }
                }
                need.anySuffix != null -> {
                    names = pick?.takeIf { it in present }?.let(::listOf)
                        ?: present.filter { it.endsWith(need.anySuffix, ignoreCase = true) }.sorted()
                    val expect = anyName(known, tag)
                    expectOf = { expect }
                }
                else -> {
                    names = need.anyOf.filter { it in present }
                    expectOf = { forName(known, it) }
                }
            }
            val group = names.map { n -> expectOf(n)?.let { BiosCandidate(n, it) } ?: return@mapNotNull null }
            group.ifEmpty { null }
        }
    }

    /**
     * The problem to tell of for [groups] ([toCheck]), [fileOf] giving each file as found: the first group none of whose
     * files is good. Within it a warning comes before a refusal, as the core may load the file warned of.
     */
    fun problem(known: KnownBios, groups: List<List<BiosCandidate>>, fileOf: (String) -> BiosFile?): BiosProblem? {
        for (group in groups) {
            val problems = group.map { c -> fileOf(c.name)?.let { verdict(known, it, c.expect) } }
            if (problems.any { it == null }) continue
            return problems.firstOrNull { !it!!.refuses } ?: problems.first()
        }
        return null
    }

    /** What [file] is against [expect]; null when it is a good dump (or cannot be judged). */
    fun verdict(known: KnownBios, file: BiosFile, expect: BiosExpect): BiosProblem? {
        val md5 = file.md5?.lowercase() ?: return null
        if (md5 in expect.md5s) return null
        val others = known.byMd5(md5)
        if (others.isNotEmpty()) return BiosProblem.OtherDump(file, expect, others.firstOrNull { it.console in expect.consoles } ?: others.first())
        if (expect.sizes.isNotEmpty() && file.size !in expect.sizes) return BiosProblem.WrongSize(file, expect)
        return if (expect.unlistedWarns) BiosProblem.Unlisted(file, expect) else null
    }

    /** A Sega CD BIOS name's expectation is its region's ([segaCd]); any other name's, the dumps listed under it. */
    fun forName(known: KnownBios, name: String): BiosExpect? {
        if (Names.isArchive(name)) return null
        SegaCdBios.regionOfName(name)?.let { return segaCd(known, it) }
        val entries = known.byName(name).filter { it.md5s.isNotEmpty() }
        if (entries.isEmpty()) return null
        return expect("the ${consoleName(entries.first().console)} BIOS", entries)
    }

    /** Any dump of [region]'s Sega CD BIOS, under any name either core gives it. */
    fun segaCd(known: KnownBios, region: DiscRegion): BiosExpect? {
        val entries = SegaCdBios.dumpNames(region).flatMap(known::byName).filter { it.md5s.isNotEmpty() }
        if (entries.isEmpty()) return null
        return expect("the ${region.label} Sega CD BIOS", entries).copy(regionLocked = true)
    }

    /** Any listed dump of [tag]'s console, whatever its name: only another file altogether is told of. */
    private fun anyName(known: KnownBios, tag: String): BiosExpect? {
        val console = CoreRegistry.consoleTag(tag)
        val entries = known.entries.filter { it.console == console && it.md5s.isNotEmpty() }
        if (entries.isEmpty()) return null
        return expect("a ${consoleName(console)} BIOS", entries).copy(sizes = emptySet(), unlistedWarns = false)
    }

    private fun expect(what: String, entries: List<KnownBiosEntry>) = BiosExpect(
        what = what,
        consoles = entries.mapTo(HashSet()) { it.console },
        md5s = entries.flatMapTo(HashSet()) { it.md5s },
        sizes = entries.flatMapTo(HashSet()) { it.sizes },
    )

    /** "dc_boot.bin in Bios/DC/ is scph1001.bin, the PlayStation BIOS, not the Dreamcast BIOS. Replace it with a good dump." */
    fun message(problem: BiosProblem, folder: String): String {
        val name = problem.file.name
        val what = problem.expect.what
        return when (problem) {
            is BiosProblem.WrongSize -> "$name in $folder is not $what: a good dump is ${sizes(problem.expect.sizes)}, this one is " +
                "${size(problem.file.size)}. Replace it with a good dump."
            is BiosProblem.OtherDump -> if (problem.refuses) "$name in $folder is ${describe(problem.other, name)}, not $what. Replace it with a good dump."
                else "$name in $folder is ${problem.other.name}, another ${consoleName(problem.other.console)} BIOS. " +
                    "If the game does not start, put a good $name there."
            is BiosProblem.Unlisted -> "$name in $folder is not a known good dump of $what. If the game does not start, replace it."
        }
    }

    private fun describe(entry: KnownBiosEntry, fileName: String): String {
        SegaCdBios.regionOfName(entry.name)?.let { return "the ${it.label} Sega CD BIOS" }
        val console = "the ${consoleName(entry.console)} BIOS"
        return if (entry.name.equals(fileName, ignoreCase = true)) console else "${entry.name}, $console"
    }

    private fun consoleName(console: String): String = CoreRegistry.consoleOf(console)?.displayName ?: console

    private fun sizes(sizes: Set<Long>): String = sizes.sorted().joinToString(" or ") { size(it) }

    private fun size(bytes: Long): String = if (bytes > 0 && bytes % 1024 == 0L) "${bytes / 1024} KB" else "$bytes bytes"
}

/**
 * md5s of BIOS files by path, each kept while the file's size and modification time are the ones it was hashed at, so
 * a launch rehashes only a file that changed. Lines: path, size, modification time, md5 (tab-separated).
 */
class BiosMd5Cache private constructor(private val rows: MutableMap<String, Row>) {
    private data class Row(val size: Long, val modified: Long, val md5: String)

    fun get(path: String, size: Long, modified: Long): String? = rows[path]?.takeIf { it.size == size && it.modified == modified }?.md5

    fun put(path: String, size: Long, modified: Long, md5: String) { rows[path] = Row(size, modified, md5) }

    /** Drops the rows of paths [keep] rejects (files gone), so the cache does not outgrow the folder. */
    fun retain(keep: (String) -> Boolean) { rows.keys.retainAll(keep) }

    fun format(): String = rows.entries.joinToString("") { (p, r) -> "$p\t${r.size}\t${r.modified}\t${r.md5}\n" }

    companion object {
        fun parse(text: String): BiosMd5Cache = BiosMd5Cache(LinkedHashMap<String, Row>().apply {
            for (line in text.lineSequence()) {
                val c = line.split('\t')
                if (c.size != 4 || c[0].isEmpty() || c[3].isEmpty()) continue
                val size = c[1].toLongOrNull() ?: continue
                val modified = c[2].toLongOrNull() ?: continue
                put(c[0], Row(size, modified, c[3]))
            }
        })
    }
}
