package com.nxredux.mobile.storage

/** One `shortcuts.txt` line: a game path and the display name at pin time. */
data class Shortcut(val path: String, val name: String? = null)

/** nx-redux `shortcuts.txt` (pins): `path[\tname]` per line, sorted by name ignoring case, at most [MAX]. */
object Shortcuts {
    const val MAX = 12

    fun parse(text: String): List<Shortcut> = TabLines.parseOptional(text).map { (path, name) -> Shortcut(path, name) }

    fun render(list: List<Shortcut>): String = sorted(list).joinToString(separator = "") { TabLines.line(it.path, it.name) }

    /** [list] with [shortcut] added (or its name replaced when the path is known), sorted; null when the list is full and the path is new. */
    fun add(list: List<Shortcut>, shortcut: Shortcut): List<Shortcut>? {
        val known = list.any { it.path == shortcut.path }
        if (!known && list.size >= MAX) return null
        return sorted(list.filter { it.path != shortcut.path } + shortcut)
    }

    fun remove(list: List<Shortcut>, path: String): List<Shortcut> = list.filter { it.path != path }

    /** The pin at [path] renamed in place; unchanged when the path is absent. */
    fun rename(list: List<Shortcut>, path: String, name: String): List<Shortcut> =
        if (list.none { it.path == path }) list else sorted(list.map { if (it.path == path) it.copy(name = name) else it })

    private fun sorted(list: List<Shortcut>): List<Shortcut> = list.sortedBy { (it.name ?: it.path).lowercase() }
}
