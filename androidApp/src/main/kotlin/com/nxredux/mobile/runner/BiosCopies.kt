package com.nxredux.mobile.runner

/**
 * The record of the BIOS files [BiosImport] copied into a system dir, by the `Bios/<TAG>/` folder each came from: a
 * copy outlives its original (the import only adds), so a BIOS the user deleted or renamed would still load and pass
 * the launch's check. A copy goes once no folder that holds it in the record has it any more; files the import never
 * copied (the bundled ones, those a core writes) are never in it. One line per copy: `<TAG>\t<path under the dir>`.
 */
internal object BiosCopies {
    fun parse(text: String): Map<String, Set<String>> = text.lineSequence()
        .mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 && it[0].isNotEmpty() && it[1].isNotEmpty() } }
        .groupBy({ it[0] }, { it[1] }).mapValues { it.value.toSet() }

    fun format(record: Map<String, Set<String>>): String =
        record.toSortedMap().flatMap { (tag, paths) -> paths.sorted().map { "$tag\t$it" } }.joinToString("\n")

    /**
     * The record after an import of the folders in [found] (each folder's files and whether it listed in full), and the
     * copies to remove: those [old] held that no folder holds now. A folder that did not list in full keeps what it had
     * (a hidden listing cannot say a file is gone); the folders this import did not read keep theirs.
     */
    fun update(old: Map<String, Set<String>>, found: Map<String, Pair<Set<String>, Boolean>>): Pair<Map<String, Set<String>>, Set<String>> {
        val now = old.toMutableMap()
        for ((tag, f) in found) {
            val (seen, complete) = f
            now[tag] = if (complete) seen else old[tag].orEmpty() + seen
        }
        val record = now.filterValues { it.isNotEmpty() }
        return record to (old.values.flatten().toSet() - record.values.flatten().toSet())
    }
}
