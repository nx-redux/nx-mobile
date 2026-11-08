package com.nxredux.mobile.storage

/** Line files of `key<TAB>value` (map.txt, systems.txt, recent.txt, shortcuts.txt), read through [TextLines.split]. */
object TabLines {
    /**
     * The `key<TAB>value` lines of [text] as a map (a later line wins), each side passed through [key] and [value]. A line
     * without a tab, with the tab first, or with either side empty after its normalising is skipped.
     */
    fun parsePairs(text: String, key: (String) -> String = { it }, value: (String) -> String): Map<String, String> =
        TextLines.split(text).asSequence()
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else {
                    val k = key(line.substring(0, tab))
                    val v = value(line.substring(tab + 1))
                    if (k.isEmpty() || v.isEmpty()) null else k to v
                }
            }
            .toMap()

    /** [entries] as `key<TAB>value` lines, sorted by key. */
    fun render(entries: Map<String, String>): String =
        entries.entries.sortedBy { it.key }.joinToString("") { "${it.key}\t${it.value}\n" }

    /** The non-blank `key[<TAB>value]` lines of [text], in order; a missing or empty value is null. */
    fun parseOptional(text: String): List<Pair<String, String?>> =
        TextLines.split(text)
            .filter { it.isNotBlank() }
            .map { line ->
                val tab = line.indexOf('\t')
                if (tab < 0) line to null else line.substring(0, tab) to line.substring(tab + 1).ifEmpty { null }
            }

    /** One `key[<TAB>value]` line, newline included. */
    fun line(key: String, value: String?): String = key + (value?.let { "\t$it" } ?: "") + "\n"

    /** [line]'s part before its first tab (all of it when there is none). */
    fun key(line: String): String = line.substringBefore('\t')

    /** [line] with its [key] replaced by [f]'s answer, the rest (the tab and value) kept; unchanged when [f] gives null. */
    fun mapKey(line: String, f: (String) -> String?): String {
        val key = key(line)
        return f(key)?.let { it + line.substring(key.length) } ?: line
    }
}
