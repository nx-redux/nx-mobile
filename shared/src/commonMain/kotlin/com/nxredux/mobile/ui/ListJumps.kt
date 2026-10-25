package com.nxredux.mobile.ui

/**
 * A game list's jumps, as NX Redux's (nextui gamelist.c): L1 / R1 by letter, LEFT / RIGHT by a page of rows.
 */
object ListJumps {
    /**
     * [name]'s letter group (entry_names.c `EntryNames_indexChar`): a–z are 1–26 whatever the case, a character after
     * 'z' (an accented letter) 27, anything else (a digit, a symbol, nothing) 0.
     */
    fun group(name: String): Int {
        val c = name.firstOrNull()?.lowercaseChar() ?: return 0
        return when {
            c in 'a'..'z' -> c - 'a' + 1
            c > 'z' -> 27
            else -> 0
        }
    }

    /**
     * The row L1 ([dir] -1) or R1 (+1) lands on from [index] in [names] (the rows' shown names, in list order): the first
     * row of the previous or next run of one [group], from anywhere in the run. Past the first or last run it stays.
     */
    fun letter(names: List<String>, index: Int, dir: Int): Int {
        if (index !in names.indices) return index
        val starts = names.indices.filter { it == 0 || group(names[it]) != group(names[it - 1]) }
        val run = starts.indexOfLast { it <= index }
        return starts.getOrNull(run + dir) ?: index
    }

    /** The row LEFT ([dir] -1) or RIGHT (+1) lands on: [rows] (the whole rows shown, at least one) away, held at the ends. */
    fun page(count: Int, rows: Int, index: Int, dir: Int): Int {
        if (count <= 0) return index
        return (index + dir * rows.coerceAtLeast(1)).coerceIn(0, count - 1)
    }
}
