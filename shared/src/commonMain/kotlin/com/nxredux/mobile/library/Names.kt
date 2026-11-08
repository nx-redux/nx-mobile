package com.nxredux.mobile.library

/** Naming rules shared with NX Redux (workspace/all/scraper/scraper_scan.c, common/utils.c). */
object Names {
    private val systemFolder = Regex("""^(.*?)\s*\(([^()]+)\)\s*$""")

    /**
     * NX Redux's rules, plus `<name>.part`: a write in progress or interrupted (a leftover `map.txt.part` is neither a game
     * nor a sidecar). `map.txt` in any case: the handheld's card and Android's shared storage are case-insensitive.
     */
    fun isHidden(name: String): Boolean =
        name.startsWith(".") || name.endsWith(".disabled") || name.endsWith(PART) || name.equals("map.txt", ignoreCase = true) || name == SidecarTxt.FILE

    /** The suffix a replacing write ([RomsSource.writeBytes], [RomsSource.writeText]) writes to before the swap. */
    const val PART = ".part"

    /** The skeleton folder for [tag]: "Game Boy Advance (MGBA)". */
    fun systemFolderName(console: com.nxredux.mobile.runner.Console, tag: String): String = "${console.displayName} ($tag)"

    /** "Nintendo DS (NDS)" -> ("Nintendo DS", "NDS"); null when there is no trailing tag. */
    fun parseSystemFolder(name: String): Pair<String, String>? {
        val m = systemFolder.matchEntire(name) ?: return null
        val display = m.groupValues[1].trim()
        val tag = m.groupValues[2].trim()
        if (display.isEmpty() || tag.isEmpty()) return null
        return display to tag
    }

    /**
     * [name]'s extension in lowercase, "" when it has none. A dot file (".iso") has none unless [dotFile]: a format check
     * that reads such a name as its own extension keeps doing so.
     */
    fun extensionOf(name: String, dotFile: Boolean = false): String {
        val dot = name.lastIndexOf('.')
        return if (dot < 0 || (dot == 0 && !dotFile)) "" else name.substring(dot + 1).lowercase()
    }

    fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name else name.substring(0, dot)
    }

    /**
     * nx-redux `getDisplayName` (common/utils.c) on a name already without its extension: cut at the last `(` (else the
     * last `[`) until that bracket is at position 0 or there is none, so "Zelda (USA) (Rev 1)" is "Zelda" and
     * "(Proto) Game" stays; an emptied name keeps [stem]; trailing whitespace goes, the first character never does.
     * The C extension-stripping step is deliberately not ported ([stem] comes from [stripExtension]); identical stems
     * stay identical after the scanner's duplicate rule, which falls back to the stem.
     */
    fun plainName(stem: String): String {
        var out = stem
        while (true) {
            val i = out.lastIndexOf('(').takeIf { it >= 0 } ?: out.lastIndexOf('[')
            if (i <= 0) break
            out = out.substring(0, i)
        }
        if (out.isEmpty()) out = stem
        var end = out.length
        while (end > 1 && out[end - 1] in C_SPACE) end--
        return out.substring(0, end)
    }

    /**
     * nx-redux `gen-arcade-names.sh` `plain_titles` on an arcade DAT title: the part before the first " / " (not at
     * position 0), then trailing balanced `(...)`/`[...]` groups dropped one at a time, nested ones whole ("Dragon
     * Spirit (new version (DS3))" is "Dragon Spirit"). Stops at a title not ending in a bracket, an unbalanced group or
     * one that is the whole title; an emptied title keeps [title].
     */
    fun plainTitle(title: String): String {
        var t = title
        val slash = t.indexOf(" / ")
        if (slash > 0) t = t.substring(0, slash)
        while (true) {
            t = t.trimEnd(' ', '\t')
            val n = t.length
            if (n == 0 || (t[n - 1] != ')' && t[n - 1] != ']')) break
            var depth = 0
            var j = n - 1
            while (j >= 0) {
                val c = t[j]
                if (c == ')' || c == ']') depth++ else if ((c == '(' || c == '[') && --depth == 0) break
                j--
            }
            if (j <= 0) break   // unbalanced, or the whole title is one group
            t = t.substring(0, j)
        }
        return t.ifEmpty { title }
    }

    /** C `isspace` in the "C" locale. */
    private const val C_SPACE = " \t\n\u000B\u000C\r"

    /**
     * Archives the runner unpacks at launch (one ROM inside), unless the tag's core loads them as is
     * ([com.nxredux.mobile.runner.CoreRegistry.loadsArchive]).
     */
    val ARCHIVE_EXTENSIONS: Set<String> = setOf("zip", "7z")

    fun isArchive(name: String): Boolean = extensionOf(name) in ARCHIVE_EXTENSIONS

    private val collectionForbidden = setOf('\\', ':', '*', '?', '"', '<', '>', '|')

    /** A display name the user typed: trimmed; null when empty, containing `/`, a tab or a newline, or starting with `.`. */
    fun validAlias(s: String): String? {
        val t = s.trim()
        if (t.isEmpty() || t.startsWith(".") || t.any { it == '/' || it == '\t' || it == '\n' || it == '\r' }) return null
        return t
    }

    /** A collection name (it becomes `Collections/<name>.txt`): [validAlias] plus not `map` and none of `\ : * ? " < > |`. */
    fun validCollectionName(s: String): String? {
        val t = validAlias(s) ?: return null
        if (t.equals("map", ignoreCase = true) || t.any { it in collectionForbidden }) return null
        return t
    }
}
