package com.nxredux.mobile.storage

/** One `recent.txt` line: a ROM path relative to the SD root (`/Roms/<System (TAG)>/<file>`) and an optional alias. */
data class Recent(val path: String, val alias: String? = null)

/** minarch/nextui `recent.txt`: newest first, at most [MAX], `path[\talias]` per line. */
object Recents {
    const val MAX = 24
    const val HOME = GamePath.HOME

    fun parse(text: String): List<Recent> = TabLines.parseOptional(text).map { (path, alias) -> Recent(path, alias) }

    fun render(list: List<Recent>): String = list.joinToString(separator = "") { TabLines.line(it.path, it.alias) }

    /** Moves [recent] to the top; a null alias keeps the existing entry's alias. */
    fun add(list: List<Recent>, recent: Recent): List<Recent> {
        val existing = list.firstOrNull { it.path == recent.path }
        return (listOf(recent.copy(alias = recent.alias ?: existing?.alias)) + list.filter { it.path != recent.path }).take(MAX)
    }

    fun remove(list: List<Recent>, path: String): List<Recent> = list.filter { it.path != path }

    /** [path]'s alias replaced in place (no reordering); unchanged when the path is absent. */
    fun updateAlias(list: List<Recent>, path: String, alias: String): List<Recent> =
        list.map { if (it.path == path) it.copy(alias = alias) else it }
}
