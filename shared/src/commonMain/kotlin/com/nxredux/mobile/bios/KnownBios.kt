package com.nxredux.mobile.bios

/**
 * One BIOS file a console's core reads from `Bios/<console>/`: [name] is what it must be called there, [aliases] other
 * names a dump is known by. [sizes] empty: a set archive, whose size varies between romset versions. [bootChange]: an
 * optional file whose presence changes how games boot (a boot animation, the TMSS screen).
 */
data class KnownBiosEntry(
    val name: String,
    val aliases: List<String>,
    val console: String,
    val sizes: Set<Long>,
    val md5s: Set<String>,
    val required: Boolean,
    val bootChange: Boolean,
)

/**
 * The bundled list of known BIOS (assets/bios/known.tsv, built by tools/bios-list from retrobios metadata). A file two
 * consoles read is two entries.
 */
class KnownBios(val entries: List<KnownBiosEntry>) {
    private val md5Index: Map<String, List<KnownBiosEntry>> =
        entries.flatMap { e -> e.md5s.map { it to e } }.groupBy({ it.first }, { it.second })
    private val nameIndex: Map<String, List<KnownBiosEntry>> =
        entries.flatMap { e -> (listOf(e.name) + e.aliases).map { it.lowercase() to e } }.groupBy({ it.first }, { it.second }).mapValues { it.value.distinct() }

    private val allSizes: Set<Long> = entries.flatMapTo(HashSet()) { it.sizes }

    /** The largest listed file: a file of unknown size is read no further than this. */
    val maxSize: Long = allSizes.maxOrNull() ?: 0L

    fun byMd5(md5: String): List<KnownBiosEntry> = md5Index[md5.lowercase()].orEmpty()

    /**
     * Whether a picked file can match anything, decided before it is opened: every listed md5 belongs to a listed size,
     * and set archives match by name. A file of another size with an unknown name is counted, never read.
     */
    fun shouldRead(size: Long?, baseName: String): Boolean = size == null || size in allSizes || byName(baseName).isNotEmpty()

    /** Entries named [name] or aliased so, ignoring case and, as dumps are often spelled with one, a hyphen. */
    fun byName(name: String): List<KnownBiosEntry> {
        val n = name.lowercase()
        return nameIndex[n] ?: nameIndex[n.replace("-", "")].orEmpty()
    }

    companion object {
        val EMPTY = KnownBios(emptyList())

        /** Columns: name, aliases, console, sizes, md5s, required, boot_change; `#` lines and malformed rows are skipped. */
        fun parse(tsv: String): KnownBios = KnownBios(tsv.lineSequence().mapNotNull { line ->
            if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
            val c = line.split('\t')
            if (c.size < 7 || c[0].isEmpty() || c[2].isEmpty()) return@mapNotNull null
            fun list(s: String) = s.split(',').filter { it.isNotEmpty() }
            KnownBiosEntry(
                name = c[0], aliases = list(c[1]), console = c[2],
                sizes = list(c[3]).mapNotNullTo(HashSet()) { it.toLongOrNull() },
                md5s = list(c[4]).mapTo(HashSet()) { it.lowercase() },
                required = c[5] == "1", bootChange = c[6] == "1",
            )
        }.toList())
    }
}
