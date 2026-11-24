package com.nxredux.mobile.external

import com.nxredux.mobile.runner.CoreRegistry

/** How a standalone emulator receives the game. */
enum class GamePass {
    /** The game's content URI as the intent data. */
    VIEW_DATA,

    /** The game's content URI in a string extra named by `extraKey`. */
    URI_EXTRA,

    /** A real file path in a string extra named by `extraKey`; the app cannot read content URIs. */
    PATH_EXTRA,
}

/** Which arcade sets an entry can run, for apps that only cover part of a shared tag. */
enum class SetRule { ALL, NAOMI, MODEL3 }

/**
 * How to open a game in one standalone emulator. `activity` is the fully qualified class name and is the same in every
 * listed package (a paid or legacy build of the same app); an app whose builds differ gets its own entry.
 */
data class ExternalEmulator(
    val id: String,
    val name: String,
    val packages: List<String>,
    val activity: String,
    val pass: GamePass,
    val extraKey: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val clearTask: Boolean = false,
    val action: String = "android.intent.action.VIEW",
    val categories: List<String> = emptyList(),
    val tags: Set<String>,
    val sets: SetRule = SetRule.ALL,
    val readsZip: Boolean = false,
    val commandsSidecar: Boolean = false,
    val checked: Boolean = false,
) {
    val takesUri: Boolean get() = pass != GamePass.PATH_EXTRA
}

object ExternalEmulators {
    /** Package ids other frontends list for forks to trick vendor performance modes; never listed or detected. */
    val SPOOFED: Set<String> = setOf("com.miHoYo.Yuanshen", "com.miHoYo.Yuanshen.nightly", "com.antutu.ABenchMark")

    /** Every recipe, in each console's default menu order ([externalEmulatorTable]). */
    val all: List<ExternalEmulator> = externalEmulatorTable

    /** Whether [set] (an arcade zip's base name) is a Sega Model 3 game. */
    fun isModel3Set(set: String): Boolean = set.lowercase() in model3Sets

    fun forTag(tag: String, table: List<ExternalEmulator> = all): List<ExternalEmulator> {
        val base = CoreRegistry.baseTag(tag)
        return table.filter { base in it.tags }
    }

    fun byId(id: String, table: List<ExternalEmulator> = all): ExternalEmulator? = table.firstOrNull { it.id == id }

    /** Whether [e] can open this game: path-only apps need a real file outside any zip, and set rules need the matching arcade set. */
    fun offered(e: ExternalEmulator, isRealFile: Boolean, insideZip: Boolean, isNaomiSet: Boolean, isModel3Set: Boolean): Boolean {
        if (e.pass == GamePass.PATH_EXTRA && (!isRealFile || (insideZip && !e.readsZip))) return false
        return when (e.sets) {
            SetRule.ALL -> true
            SetRule.NAOMI -> isNaomiSet
            SetRule.MODEL3 -> isModel3Set
        }
    }
}
