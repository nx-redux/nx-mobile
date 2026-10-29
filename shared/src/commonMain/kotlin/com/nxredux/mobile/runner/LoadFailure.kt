package com.nxredux.mobile.runner

/**
 * Why a core refused a game, from its last warning and error log lines (the runner keeps them, each prefixed with its
 * level: "E " or "W "). flycast's NAOMI/Atomiswave failures (core/hw/naomi/naomi_cart.cpp at the pinned commit) become a
 * sentence saying which file is missing where, gpsp's missing recompiler cache a plain one; any other error line is shown
 * as the core wrote it.
 */
object LoadFailure {
    private val levelPrefix = Regex("""^([EW]) (.*)$""")
    // flycast's LogManager prefixes every non-debug line with its log type: "[NAOMI] ", "[BOOT] ".
    private val typePrefix = Regex("""^\[([A-Za-z0-9_]+)]:?\s*""")
    // The log types flycast's NAOMI loader writes under (naomi_cart.cpp, and libretro.cpp's load error).
    private val flycastTypes = setOf("BOOT", "NAOMI")
    private val cannotLoadBios = Regex("""^Error: cannot load BIOS (\S+)$""")
    private val biosFileMissing = Regex("""^(\S+): Cannot open (\S+)$""")
    private val romMissing = Regex("""^Cannot find (\S+)$""")
    private val romTruncated = Regex("""^Invalid ROM: truncated (\S+)$""")

    private const val MAX_FILES = 3
    private const val MAX_LINE = 200

    /** [flycast]: the line carried one of [flycastTypes], so it may be flycast's arcade loader speaking. */
    private class Line(val error: Boolean, val text: String, val flycast: Boolean)

    /**
     * The message for [title]'s failed load given the core's [lines] (oldest first), or null when they say nothing (keep
     * the generic text). [setFile] is the archive the core was given (mslug6.zip), [biosArchive] the BIOS archive it
     * needs without extension (from the set table; a BIOS name such as naomi-jp-d lives in naomi.zip), [parent] its
     * parent set when it is a clone.
     */
    fun message(
        title: String,
        lines: List<String>,
        setFile: String? = null,
        biosArchive: String? = null,
        parent: String? = null,
    ): String? {
        val parsed = lines.mapNotNull(::parse)
        val errors = parsed.filter { it.error }
        val last = errors.lastOrNull() ?: return null
        // The MAME set wording only for flycast's arcade loader: a set loaded as is, or a line under its log types.
        val arcade = errors.filter { setFile != null || it.flycast }

        arcade.asReversed().firstNotNullOfOrNull { cannotLoadBios.matchEntire(it.text) }?.let { m ->
            val biosName = m.groupValues[1]
            val archive = biosArchive ?: stripArchive(biosName)
            val files = parsed.mapNotNull { biosFileMissing.matchEntire(it.text) }
                .filter { stripArchive(it.groupValues[1]).equals(stripArchive(biosName), ignoreCase = true) }
                .map { it.groupValues[2] }
                .distinct()
            if (files.isEmpty()) return "$title needs a complete $archive.zip in Bios/DC/"
            return "$title needs $archive.zip with ${anyOf(files)} in it: put a complete $archive.zip in Bios/DC/"
        }

        val holder = when {
            setFile == null -> "the set"
            parent != null -> "neither $setFile nor its parent $parent.zip"
            else -> setFile
        }
        val has = if (setFile != null && parent != null) "has" else "does not have"
        arcade.asReversed().firstNotNullOfOrNull { romMissing.matchEntire(it.text) }?.let { m ->
            return "$title needs ${m.groupValues[1]}, which $holder $has: the set is incomplete or from another MAME version"
        }
        arcade.asReversed().firstNotNullOfOrNull { romTruncated.matchEntire(it.text) }?.let { m ->
            return "$title can't start: ${m.groupValues[1]} in ${setFile ?: "the set"} is truncated, the set is damaged or from another MAME version"
        }

        return "The core couldn't load $title: ${oneLine(last.text)}"
    }

    private fun parse(raw: String): Line? {
        val m = levelPrefix.matchEntire(raw.trimEnd()) ?: return null
        val body = m.groupValues[2]
        val type = typePrefix.find(body)?.groupValues?.get(1)
        val text = body.replace(typePrefix, "").trim()
        if (text.isEmpty()) return null
        return Line(m.groupValues[1] == "E", text, type in flycastTypes)
    }

    private fun stripArchive(name: String): String {
        val lower = name.lowercase()
        return if (lower.endsWith(".zip") || lower.endsWith(".7z")) name.substringBeforeLast('.') else name
    }

    private fun anyOf(files: List<String>): String {
        if (files.size == 1) return files[0]
        val shown = files.take(MAX_FILES)
        val more = files.size - shown.size
        return if (more > 0) "${shown.joinToString(", ")} or one of $more more" else "${shown.dropLast(1).joinToString(", ")} or ${shown.last()}"
    }

    private fun oneLine(text: String): String {
        val flat = text.replace(Regex("""\s+"""), " ").trim().removePrefix("Error: ").removePrefix("error: ")
        return if (flat.length <= MAX_LINE) flat else flat.take(MAX_LINE - 1).trimEnd() + "…"
    }
}
