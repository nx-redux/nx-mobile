package com.nxredux.mobile.bios

/** One picked file, already read: [path] inside the pick (folder or zip), [md5] null when it was too big to hash. */
data class PickedBios(val path: String, val size: Long, val md5: String?)

/** The md5 of what `Bios/<console>/<name>` holds now; null when nothing is there. */
fun interface ExistingBios {
    fun md5(console: String, name: String): String?
}

enum class BiosOutcome { VERIFIED, RENAMED, NOT_VERIFIED, ALREADY_THERE, KEPT_YOURS }

/** A picked file's place: `Bios/<console>/<name>`. Only [needsWrite] ones are copied; the others are reported. */
data class BiosWrite(val pickedPath: String, val console: String, val name: String, val outcome: BiosOutcome, val bootChange: Boolean) {
    val needsWrite: Boolean get() = outcome == BiosOutcome.VERIFIED || outcome == BiosOutcome.RENAMED || outcome == BiosOutcome.NOT_VERIFIED
}

/** A file the results list as skipped, with why. Files that are no known BIOS at all are only counted ([BiosIntakePlan.otherFiles]). */
data class BiosSkip(val pickedPath: String, val reason: String)

data class BiosIntakePlan(val writes: List<BiosWrite>, val skips: List<BiosSkip>, val otherFiles: Int) {
    val isEmpty: Boolean get() = writes.isEmpty() && skips.isEmpty()
}

/**
 * Where each picked file goes. By md5 first, whatever the file is called; then by name (or alias) with a size the
 * console's file can have, copied but "not verified"; the same name at a size it cannot have is skipped. One write per
 * destination: a verified copy beats a not-verified one, else the first picked. A destination already holding the
 * same bytes is left alone, and a verified file there is kept over a not-verified newcomer.
 */
object BiosIntakePlanner {
    /** Above any file in the list (the DSi NAND is 240 MiB): bigger files are not hashed (big games picked by mistake). */
    const val MAX_BYTES = 256L * 1024 * 1024

    fun needsHash(size: Long?): Boolean = size == null || size <= MAX_BYTES

    /** macOS leftovers (`._x`, `.DS_Store`) and other dotfiles are no BIOS and are not even counted. */
    fun isCandidate(path: String): Boolean = !baseName(path).startsWith(".")

    private fun baseName(path: String) = path.substringAfterLast('/')

    private val VERIFIED_RANK = setOf(BiosOutcome.VERIFIED, BiosOutcome.RENAMED)

    private class Candidate(val picked: PickedBios, val entry: KnownBiosEntry, val outcome: BiosOutcome)

    fun plan(picked: List<PickedBios>, known: KnownBios, existing: ExistingBios): BiosIntakePlan {
        val skips = mutableListOf<BiosSkip>()
        var other = 0
        val candidates = mutableListOf<Candidate>()
        for (p in picked) {
            val base = baseName(p.path)
            val byMd5 = p.md5?.let { known.byMd5(it) }.orEmpty()
            if (byMd5.isNotEmpty()) {
                byMd5.groupBy { it.console }.values.forEach { hits ->
                    val e = hits.firstOrNull { h -> (listOf(h.name) + h.aliases).any { it.equals(base, ignoreCase = true) } } ?: hits.first()
                    candidates += Candidate(p, e, if (base == e.name) BiosOutcome.VERIFIED else BiosOutcome.RENAMED)
                }
                continue
            }
            val byName = known.byName(base)
            val fits = byName.filter { it.sizes.isEmpty() || p.size in it.sizes }
            when {
                fits.isNotEmpty() -> fits.groupBy { it.console }.values.forEach { candidates += Candidate(p, it.first(), BiosOutcome.NOT_VERIFIED) }
                byName.isNotEmpty() -> skips += BiosSkip(p.path, "Wrong size (expected ${byName.first().sizes.min()} bytes)")
                else -> other++
            }
        }
        val writes = mutableListOf<BiosWrite>()
        for ((dest, group) in candidates.groupBy { it.entry.console to it.entry.name }) {
            val winner = group.firstOrNull { it.outcome in VERIFIED_RANK } ?: group.first()
            val (console, name) = dest
            val have = existing.md5(console, name)
            val outcome = when {
                have != null && have == winner.picked.md5 -> BiosOutcome.ALREADY_THERE
                have != null && winner.outcome == BiosOutcome.NOT_VERIFIED && have in winner.entry.md5s -> BiosOutcome.KEPT_YOURS
                else -> winner.outcome
            }
            for (c in group) {
                when {
                    c === winner -> writes += BiosWrite(c.picked.path, console, name, outcome, c.entry.bootChange)
                    c.picked.md5 != null && c.picked.md5 == winner.picked.md5 ->
                        writes += BiosWrite(c.picked.path, console, name, BiosOutcome.ALREADY_THERE, c.entry.bootChange)
                    else -> skips += BiosSkip(c.picked.path, "Another copy was used")
                }
            }
        }
        return BiosIntakePlan(writes, skips, other)
    }
}
