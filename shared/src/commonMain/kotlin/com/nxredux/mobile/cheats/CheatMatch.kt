package com.nxredux.mobile.cheats

/**
 * Which `.cht` files belong to a ROM, as nx-redux decides it (`ma_cheats.c` `Cheat_getPaths` and
 * `ma_cheat_match.c`): exact candidate names first, then a stem + region ranking over every file in the
 * folder that merges same-game variants from different cheat sources. Pure string work; the caller lists
 * the folder and reads the files.
 */
object CheatMatch {
    const val MAX_CANDIDATES = 16
    private const val MAX_TOKENS = 8

    /** Parenthesised tokens naming where the cheats came from; every other token is a region/version qualifier. */
    private val SOURCES = setOf(
        "code breaker", "codebreaker", "gameshark", "game shark",
        "action replay", "pro action replay", "game genie", "gamegenie",
        "rumbles", "xploder", "game buster", "gamebuster", "raw", "wip",
    )

    /** Short region codes in `[…]` groups (libretro's PSP set: `Name [US] [ULUS-10041]`) as the No-Intro words ROM names use. */
    private val REGION_CODES = mapOf(
        "us" to "usa", "usa" to "usa", "eu" to "europe", "jp" to "japan", "kr" to "korea", "as" to "asia", "cn" to "china",
        "hk" to "hong kong", "de" to "germany", "fr" to "france", "it" to "italy", "es" to "spain", "spa" to "spain",
        "nl" to "netherlands", "ru" to "russia", "pl" to "poland", "pt" to "portugal", "no" to "norway",
    )

    /** A retail disc serial (`ulus-10041`), which a same-region tie prefers over a homebrew ID (`hb4b12d3bc`). */
    private val SERIAL = Regex("^[a-z]{4}-[0-9]{5}$")

    /** No-Intro's region words: a file qualified by these alone is a region's release, not a version, beta or hack. */
    private val REGIONS = setOf(
        "world", "usa", "europe", "japan", "korea", "asia", "australia", "brazil", "canada", "china", "france", "germany",
        "hong kong", "italy", "netherlands", "spain", "sweden", "taiwan", "uk", "russia", "poland", "portugal", "norway",
        "denmark", "finland", "greece", "scandinavia", "latin america", "mexico", "new zealand", "south africa", "india",
    )

    /** Regions a tie the ROM's own name can't break prefers, in order (a region-less `Tetris.gb`). */
    private val PREFERRED = listOf("usa", "world", "europe")

    /**
     * nx-redux `getDisplayName`: the file name without its 2–4 character extensions (repeated, so `.p8.png` goes)
     * and without trailing `(…)` / `[…]` groups. Never empty: a name that would vanish keeps its extension-less form.
     */
    fun displayName(fileName: String): String {
        var out = fileName.substringAfterLast('/')
        while (true) {
            val dot = out.lastIndexOf('.')
            if (dot < 0 || out.length - dot !in 3..5) break   // ".gb" .. ".doom": dot plus 2–4 letters
            out = out.substring(0, dot)
        }
        val beforeGroups = out
        while (true) {
            val i = out.lastIndexOf('(').takeIf { it >= 0 } ?: out.lastIndexOf('[')
            if (i <= 0) break
            out = out.substring(0, i)
        }
        // Fallbacks after trimming, so a name that is only groups or blanks never comes back empty.
        out = out.trimEnd()
        if (out.isEmpty()) out = beforeGroups.trimEnd()
        if (out.isEmpty()) return fileName
        return out
    }

    /**
     * Exact file names to try, most likely first: `<rom.ext>.cht`, `<rom>.<coreExt>.cht` per core extension
     * (a zipped ROM's cheat file is named after the inner file), `<rom>.cht`, `<displayName>.cht`, `<title>.cht`.
     */
    fun candidates(romFileName: String, title: String, coreExtensions: Set<String>): List<String> {
        val out = LinkedHashSet<String>()
        out += "$romFileName.cht"
        val dot = romFileName.lastIndexOf('.')
        if (dot >= 0 && romFileName.length - dot in 3..5) {
            val stem = romFileName.substring(0, dot)
            coreExtensions.map { it.lowercase() }.filter { it != "zip" }.sorted().forEach { out += "$stem.$it.cht" }
            out += "$stem.cht"   // the whole stem (GoodTools `Tetris [!].cht`); the cleaned name follows
        }
        out += "${displayName(romFileName)}.cht"
        if (title.isNotBlank()) out += "$title.cht"
        return out.take(MAX_CANDIDATES)
    }

    /** `Contra (USA) (GameShark).cht` → `GameShark`; empty when the name carries no source tag. */
    fun sourceLabel(baseName: String): String = parse(baseName).source

    /**
     * The files to merge for a ROM shown as [displayName] (its region-stripped name) whose full name is
     * [romFileName]: only `.cht` files whose stem equals [displayName] (case-insensitive) qualify, or, when none does
     * or only source-only packs do, one of whose ` _ `-separated titles does (`Daimakaimura _ Ghouls'n Ghosts`), or the same letters and digits
     * (`Soul Calibur` for `Soulcalibur`). A source-only pack (`(Rumbles)`) stays out when a file for the ROM's region (or,
     * for a region-less ROM, a [PREFERRED] region) qualifies. The group sharing the most region tokens with the ROM
     * wins, then one with a file that has no source tag (a pack has no region, so it would otherwise win for a
     * region-less ROM), then the one with the fewest extra tokens, then one with a retail disc serial, then the most
     * [PREFERRED] region, then the lexically smallest key. Files without a source tag come first, then the rest in input
     * order; then the other groups for the same regional ROM ([sameRom]), each ordered the same way. Empty when nothing
     * qualifies.
     */
    fun select(displayName: String, romFileName: String, files: List<String>): List<String> {
        val stem = displayName.trim()
        val rom = parse(romFileName)
        val all = files.filter { it.endsWith(".cht", ignoreCase = true) }.map { it to parse(it) }
        // Exact stems, then a ` _ ` title (it can stand for "&" or a multi-game cart as well as "/", so only when no stem is
        // exact or only packs are), then the same letters and digits, the same way.
        val key = loose(stem)
        val exact = all.filter { (_, p) -> p.stem.equals(stem, ignoreCase = true) }
        // Only source-only packs under the exact name: a ` _ ` title's files compete too (`Senjou no Ookami II _ Mercs`).
        val titled = all.filter { (_, p) -> !p.stem.equals(stem, ignoreCase = true) && p.titles().any { it.equals(stem, ignoreCase = true) } }
        val parsed = (if (exact.all { (_, p) -> p.isPack() }) exact + titled else exact)
            .ifEmpty { titled }
            .ifEmpty { all.filter { (_, p) -> key.isNotEmpty() && loose(p.stem) == key } }
            .ifEmpty { all.filter { (_, p) -> key.isNotEmpty() && p.titles().any { loose(it) == key } } }
        if (parsed.isEmpty()) return emptyList()
        // A source-only pack has no region, so it would win as "no extra tokens": a file for the ROM's region (or, for a
        // region-less ROM, a [PREFERRED] one) takes it out of the running.
        val regionless = rom.quals.none { it in REGIONS }
        fun regional(p: Parsed) = p.quals.any { it in rom.quals || (regionless && it in PREFERRED) }
        val pool = if (parsed.any { (_, p) -> !p.isPack() && regional(p) }) parsed.filterNot { (_, p) -> p.isPack() } else parsed
        data class Rank(val overlap: Int, val plain: Boolean, val extra: Int, val serial: Boolean, val region: Int, val key: String)
        fun rank(p: Parsed): Rank {
            val overlap = p.quals.count { it in rom.quals }
            val region = p.quals.map { PREFERRED.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: PREFERRED.size
            return Rank(overlap, p.source.isEmpty(), p.quals.size - overlap, p.quals.any { SERIAL.matches(it) }, region, p.groupKey())
        }
        val best = pool.map { (_, p) -> rank(p) }.reduce { a, b ->
            when {
                b.overlap != a.overlap -> if (b.overlap > a.overlap) b else a
                b.plain != a.plain -> if (b.plain) b else a
                b.extra != a.extra -> if (b.extra < a.extra) b else a
                b.serial != a.serial -> if (b.serial) b else a
                b.region != a.region -> if (b.region < a.region) b else a
                else -> if (b.key < a.key) b else a
            }
        }
        val group = parsed.filter { (_, p) -> p.groupKey() == best.key }
        val others = sameRom(group.first().second, rom, parsed.filter { (_, p) -> p.groupKey() != best.key })
        return noSourceFirst(group) + others.groupBy { (_, p) -> p.groupKey() }.values.flatMap(::noSourceFirst)
    }

    /**
     * The files of [rest] for the same regional ROM as [best]'s group: qualified by region words alone ([REGIONS]), and
     * sharing a region with it, one the ROM has when its name names any (a `(USA)` file for a region-less or USA ROM
     * whose best file is `(USA, Europe, Korea)`: one USA release). A version, beta or source-only pack never merges.
     */
    private fun sameRom(best: Parsed, rom: Parsed, rest: List<Pair<String, Parsed>>): List<Pair<String, Parsed>> {
        if (best.quals.isEmpty() || !best.quals.all { it in REGIONS }) return emptyList()
        val romRegions = rom.quals.filter { it in REGIONS }
        val anchor = best.quals.filter { romRegions.isEmpty() || it in romRegions }
        return rest.filter { (_, p) -> p.quals.isNotEmpty() && p.quals.all { it in REGIONS } && p.quals.any { it in anchor } }
    }

    /**
     * Every `.cht` file in [files] (case-insensitive extension) in the same game+region group as [name]:
     * same stem and the same qualifiers, whatever their cheat source. [name] itself is included when it is in
     * [files]. Files without a source tag come first, then the rest in input order (as [select] orders them); then
     * the same game's files for the same regional ROM ([sameRom]: [name]'s regions are the ROM's).
     */
    fun variants(name: String, files: List<String>): List<String> {
        val own = parse(name)
        val key = own.groupKey()
        val all = files.filter { it.endsWith(".cht", ignoreCase = true) }.map { it to parse(it) }
        val game = all.filter { (_, p) -> p.groupKey() != key && p.stem.equals(own.stem, ignoreCase = true) }
        val others = sameRom(own, own, game)
        return noSourceFirst(all.filter { (_, p) -> p.groupKey() == key }) + others.groupBy { (_, p) -> p.groupKey() }.values.flatMap(::noSourceFirst)
    }

    /**
     * The files under a ` _ ` title of [displayName] (`Sonic 3D Blast _ Sonic 3D Flickies' Island`) not in [chosen], for
     * the same regional ROM as the chosen files ([sameRom]). The name can't tell an alternate title from a multi-game cart
     * (`Hang-On _ Astro Warrior`) or an "&" (`Asterix _ Obelix`): the caller keeps only those sharing a code with the
     * chosen files' cheats.
     */
    fun titledFiles(displayName: String, romFileName: String, chosen: List<String>, files: List<String>): List<String> {
        val stem = displayName.trim()
        val picked = chosen.map(::parse).filterNot { it.isPack() }
        val best = picked.firstOrNull { it.quals.isNotEmpty() && it.quals.all { q -> q in REGIONS } } ?: return emptyList()
        val regions = Parsed(best.stem, picked.flatMap { it.quals }.filter { it in REGIONS }.distinct(), "")
        val titled = files.filter { it.endsWith(".cht", ignoreCase = true) && it !in chosen }.map { it to parse(it) }
            .filter { (_, p) -> !p.stem.equals(stem, ignoreCase = true) && p.titles().any { it.equals(stem, ignoreCase = true) } }
        return noSourceFirst(sameRom(regions, parse(romFileName), titled))
    }

    /** [stem] as letters and digits only, lowercased: `Marvel Vs. Capcom 2` and `Marvel vs Capcom 2` agree. */
    private fun loose(stem: String) = stem.filter { it.isLetterOrDigit() }.lowercase()

    /** The names of [group], those without a source tag first, each part keeping input order. */
    private fun noSourceFirst(group: List<Pair<String, Parsed>>): List<String> {
        val (plain, sourced) = group.partition { (_, p) -> p.source.isEmpty() }
        return (plain + sourced).map { it.first }
    }

    private class Parsed(val stem: String, val quals: List<String>, val source: String) {
        /** A source-only pack (`Name (Rumbles)`): cheats from a source, for no region. */
        fun isPack() = quals.isEmpty() && source.isNotEmpty()

        /** The stem and, when it lists alternate titles (`Sonic 3D Blast _ Sonic 3D Flickies' Island`), each of them. */
        fun titles(): List<String> = listOf(stem) + stem.split(" _ ").map { it.trim() }.filter { it.isNotEmpty() && it != stem }

        /** Stem plus sorted qualifiers: files with the same key are one game+region group. */
        // Each qualifier followed by a comma, as the C code writes it, so `rev` vs `rev 1` ties-break the same way.
        fun groupKey() = stem + "|" + quals.sorted().joinToString("") { "$it," }
    }

    /**
     * `Name (USA, Europe) (GameShark).cht` → stem `Name`, quals `[usa, europe]`, source `GameShark`. `[…]` groups end
     * the stem too and add quals: a region code becomes its No-Intro word (`[US-EU]` → `usa`, `europe`), anything
     * else (`[ULUS-10041]`) stays as it is.
     */
    private fun parse(baseName: String): Parsed {
        val work = if (baseName.endsWith(".cht", ignoreCase = true)) baseName.dropLast(4) else baseName
        // The stem ends at the first " (", then loses its trailing "[…]" groups (`Name [US] [ULUS-10041]`); a "[…]" inside
        // the name (`Game [Hack] Deluxe`) stays.
        var stem = work.indexOf(" (").let { if (it >= 0) work.substring(0, it) else work }.trim()
        while (stem.endsWith(']')) {
            val open = stem.lastIndexOf(" [")
            if (open < 0) break
            stem = stem.substring(0, open).trimEnd()
        }
        val quals = ArrayList<String>()
        var source = ""
        fun add(tok: String) {
            if (tok.isEmpty()) return
            val lc = tok.lowercase()
            if (lc in SOURCES) { if (source.isEmpty()) source = tok }
            else if (quals.size < MAX_TOKENS) quals += lc
        }
        groups(work, '(', ')').forEach { group -> group.split(", ").forEach { add(it.trim()) } }
        groups(work, '[', ']').forEach { group ->
            val tok = group.trim()
            val regions = tok.lowercase().split('-').map { REGION_CODES[it] }
            if (tok.isNotEmpty() && regions.all { it != null }) regions.forEach { add(it!!) } else add(tok)
        }
        return Parsed(stem, quals, source)
    }

    /** The contents of each [open]…[close] group in [s], in order; an unclosed one ends the list. */
    private fun groups(s: String, open: Char, close: Char): List<String> {
        val out = ArrayList<String>()
        var i = s.indexOf(open)
        while (i >= 0) {
            val end = s.indexOf(close, i)
            if (end < 0) break
            out += s.substring(i + 1, end)
            i = s.indexOf(open, end + 1)
        }
        return out
    }
}
