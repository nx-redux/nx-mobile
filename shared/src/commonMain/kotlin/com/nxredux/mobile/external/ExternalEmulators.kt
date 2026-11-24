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
 *
 * `data` (sent as the intent data instead of the game's URI), `extras` values and `arrayExtras` may hold [GameTokens];
 * a recipe that uses one passes the game through them, not through `pass`.
 */
data class ExternalEmulator(
    val id: String,
    val name: String,
    val packages: List<String>,
    val activity: String,
    val pass: GamePass,
    val extraKey: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val arrayExtras: Map<String, List<String>> = emptyMap(),
    val data: String? = null,
    val clearTask: Boolean = false,
    val action: String = "android.intent.action.VIEW",
    val categories: List<String> = emptyList(),
    val tags: Set<String>,
    val sets: SetRule = SetRule.ALL,
    val readsZip: Boolean = false,
    /** Only games with these extensions (lower case); null: any of the console's games. */
    val extensions: Set<String>? = null,
    val commandsSidecar: Boolean = false,
    /** Token values go on the app's command line, each as one word ([commandWord]). */
    val commandLine: Boolean = false,
    val checked: Boolean = false,
) {
    private val templates: List<String> get() = listOfNotNull(data) + extras.values + arrayExtras.values.flatten()

    private val tokenised: Boolean get() = templates.any(GameTokens::mentionsAny)

    /** The game reaches the app through `pass`: no sidecar, no tokens and no fixed data stand in for it. */
    val passesGame: Boolean get() = !commandsSidecar && !tokenised && data == null

    val takesUri: Boolean get() = passesGame && pass != GamePass.PATH_EXTRA

    /** Needs the game's real path: a path extra, or a `{path}` / `{dir}` token. */
    val needsPath: Boolean get() = (passesGame && pass == GamePass.PATH_EXTRA) ||
        templates.any { GameTokens.PATH in it || GameTokens.DIR in it }

    /** Needs the game file's text (a `{text}` token). */
    val needsText: Boolean get() = templates.any { GameTokens.TEXT in it }
}

/** A game's values for a recipe's tokens: `{path}` its real path, `{dir}` that path's folder, `{text}` the file's text. */
data class GameTokens(val path: String?, val dir: String?, val text: String?) {
    /**
     * [template] with its tokens filled, each as one [commandWord] when [commandLine]; null when it names one this game has
     * no value for, or one no word can hold.
     */
    fun fill(template: String, commandLine: Boolean = false): String? {
        var missing = false
        val out = TOKEN.replace(template) { m ->
            val value = when (m.value) {
                PATH -> path
                DIR -> dir
                else -> text
            }
            (if (commandLine) value?.let(::commandWord) else value) ?: run { missing = true; "" }
        }
        return out.takeUnless { missing }
    }

    companion object {
        const val PATH = "{path}"
        const val DIR = "{dir}"
        const val TEXT = "{text}"
        val NONE = GameTokens(null, null, null)
        private val TOKEN = Regex("""\{(path|dir|text)}""")

        fun mentionsAny(template: String): Boolean = TOKEN.containsMatchIn(template)
    }
}

/**
 * [value] as one word of MAME4droid's command line, which splits at spaces and runs a word that starts with a single quote
 * to the next one, with no escape: quoted when it has a space; null when it cannot be one word (a quote and a space, or a
 * leading quote).
 */
fun commandWord(value: String): String? = when {
    value.isNotEmpty() && ' ' !in value && !value.startsWith('\'') -> value
    '\'' !in value -> "'$value'"
    else -> null
}

/** A text-file game's id: its trimmed text, null when empty or not text (a binary such as a `.vpk` read by mistake). */
fun gameText(bytes: ByteArray?): String? {
    val text = bytes?.decodeToString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return text.takeIf { t -> t.none { it.isISOControl() || it == '\uFFFD' } }
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

    /**
     * Sheets and playlists name sibling files; an app handed only the sheet's URI cannot open those, an app given the
     * path can.
     */
    val SHEET_EXTENSIONS: Set<String> = setOf("cue", "m3u", "gdi", "ccd", "toc", "mds")

    /**
     * Whether [e] can open this game ([extension] its file's, lower case): path-only apps need a real file outside any
     * zip (a [ExternalEmulator.commandLine] app one whose path is one [commandWord]: [pathIsWord]), a URI app no sheet or
     * playlist, an extension filter a matching file, a sidecar app its sidecar ([hasCommands]), and set rules the
     * matching arcade set.
     */
    fun offered(
        e: ExternalEmulator, isRealFile: Boolean, insideZip: Boolean, isNaomiSet: Boolean, isModel3Set: Boolean,
        extension: String, hasCommands: Boolean, pathIsWord: Boolean = true,
    ): Boolean {
        if (e.needsPath && (!isRealFile || (insideZip && !e.readsZip))) return false
        if (e.needsPath && e.commandLine && !pathIsWord) return false
        if (!e.needsPath && extension in SHEET_EXTENSIONS) return false
        if (e.extensions != null && extension !in e.extensions) return false
        if (e.commandsSidecar && !hasCommands) return false
        return when (e.sets) {
            SetRule.ALL -> true
            SetRule.NAOMI -> isNaomiSet
            SetRule.MODEL3 -> isModel3Set
        }
    }
}
