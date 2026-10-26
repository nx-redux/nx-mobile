package com.nxredux.mobile.options

import com.nxredux.mobile.storage.TextLines

sealed interface ConfigLine {
    data class Entry(val key: String, val value: String, val locked: Boolean) : ConfigLine
    /** Anything that isn't a `key = value` option line (blank, comments, minarch `bind …`); kept verbatim. */
    data class Other(val text: String) : ConfigLine
}

data class ConfigFile(val lines: List<ConfigLine>) {
    /** The first line for a key wins, like minarch's reader (its lock flag included). */
    private val firstEntries: List<ConfigLine.Entry> by lazy {
        lines.filterIsInstance<ConfigLine.Entry>().distinctBy { it.key }
    }
    val values: Map<String, String> get() = firstEntries.associate { it.key to it.value }
    val locked: Set<String> get() = firstEntries.filter { it.locked }.map { it.key }.toSet()
}

/** Which user-tier file a game's options come from. */
enum class ConfigSource { NONE, CONSOLE, GAME }

/** minarch's flat `key = value` config files (`-key = value` locks an option). */
object CoreConfig {
    private const val SEP = " = "

    fun parse(text: String): ConfigFile {
        val lines = TextLines.split(text)
        val trimmed = if (lines.lastOrNull() == "") lines.dropLast(1) else lines
        return ConfigFile(trimmed.map(::parseLine))
    }

    private fun parseLine(line: String): ConfigLine {
        val i = line.indexOf(SEP)
        if (i <= 0) return ConfigLine.Other(line)
        val rawKey = line.substring(0, i)
        val locked = rawKey.startsWith("-")
        val key = if (locked) rawKey.substring(1) else rawKey
        if (key.isEmpty() || key.contains(' ')) return ConfigLine.Other(line)
        return ConfigLine.Entry(key, line.substring(i + SEP.length).trimEnd(), locked)
    }

    /**
     * Rewrites [existing] with [values]: each existing key is first [rename]d (its lock kept), then known keys are
     * updated in place (locked ones keep their value), repeated lines for a key are dropped, lines whose renamed key
     * matches [drop] are removed, other lines are kept, and new keys are appended in order.
     *
     * When a legacy line and its renamed twin are both present, the first in the file wins here, while
     * [resolve] lets the `nx_` one win on read; only a hand-edited file has both.
     */
    fun render(
        existing: ConfigFile?,
        values: List<Pair<String, String>>,
        rename: (String) -> String = { it },
        drop: (String) -> Boolean = { false },
    ): String {
        val pending = LinkedHashMap<String, String>().apply { values.forEach { (k, v) -> put(k, v) } }
        val written = HashSet<String>()
        val out = mutableListOf<String>()
        existing?.lines?.forEach { line ->
            when (line) {
                is ConfigLine.Other -> out += line.text
                is ConfigLine.Entry -> {
                    val key = rename(line.key)
                    if (drop(key)) return@forEach
                    if (!written.add(key)) return@forEach
                    val newValue = pending.remove(key)
                    val value = if (line.locked || newValue == null) line.value else newValue
                    out += (if (line.locked) "-" else "") + key + SEP + value
                }
            }
        }
        pending.forEach { (k, v) -> if (written.add(k)) out += k + SEP + v }
        return out.joinToString(separator = "\n", postfix = "\n")
    }

    /**
     * The game file after one [key] changed outside the menu (a DS hotkey): [game] updated in place, or, for a game
     * without one, a copy of [console] with the key. Reading uses the game file instead of the console file whole, so a
     * game file holding only the key would drop every console value. A locked line keeps its value. The old key [key]
     * replaced ([FrontendOptions.SUPERSEDED_KEYS]) goes: the new one now holds the pick.
     */
    fun withKey(console: ConfigFile?, game: ConfigFile?, key: String, value: String): String =
        render(game ?: console, listOf(key to value), FrontendOptions::migrateKey, drop = { it in ShaderOptions.MIGRATED_KEYS || FrontendOptions.SUPERSEDED_KEYS[it] == key })

    /**
     * What Save Changes writes: the [running] values, except a [sessionForced] key (one this launch forced, such as
     * Graphics API's renderer) keeps its [stored] value, or is left out without one so [render] keeps the file's line. A
     * value only this session runs with never replaces the player's choice.
     */
    fun valuesToSave(
        running: List<Pair<String, String>>,
        stored: Map<String, String>,
        sessionForced: Set<String>,
    ): List<Pair<String, String>> = running.mapNotNull { (k, v) ->
        if (k in sessionForced) stored[k]?.let { k to it } else k to v
    }

    data class Resolved(val values: Map<String, String>, val source: ConfigSource, val locked: Set<String>)

    /**
     * [defaults] overlaid by the game file if present, else the console file (never both). Legacy `minarch_` keys read as
     * `nx_`. A [forced] key keeps its default whatever the file says, and counts as locked (hidden from the menu). A stored
     * value in [hidden] (key -> values the app never offers) is ignored: the key takes its default, or the core's without one.
     */
    fun resolve(
        defaults: Map<String, String>,
        console: ConfigFile?,
        game: ConfigFile?,
        forced: Set<String> = emptySet(),
        hidden: Map<String, Set<String>> = emptyMap(),
    ): Resolved {
        val user = game ?: console
        val source = when {
            game != null -> ConfigSource.GAME
            console != null -> ConfigSource.CONSOLE
            else -> ConfigSource.NONE
        }
        val values = FrontendOptions.migrateKeys(user?.values ?: emptyMap())
            .filter { (k, v) -> k !in forced && hidden[k]?.contains(v) != true }
        val locked = (user?.locked ?: emptySet()).map(FrontendOptions::migrateKey).toSet() + forced
        return Resolved(defaults + values, source, locked)
    }
}
