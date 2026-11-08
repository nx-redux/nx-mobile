package com.nxredux.mobile.storage

/** A parsed save-state file name: the ROM [stem] and its [slot] (0..N, or [NxPaths.AUTO_SLOT]). */
data class StateName(val stem: String, val slot: Int)

/** Reads the three state naming schemes ([NxPaths]) back into a stem and slot, renames between slots, and finds free slots. */
object StateNames {
    private const val RETROARCH_AUTO_OFFSET = 100

    /** A state name's scheme; [AUTO] is `.state.auto`, which both RetroArch schemes write. */
    private enum class Scheme { MINUI, RETROARCH_ISH, RETROARCH, AUTO }

    /** A state name split into what comes before its suffix, its scheme and the suffix's number (as written). */
    private class Parts(val prefix: String, val scheme: Scheme, val n: Int)

    /**
     * Parses a MinUI (`<rom.ext>.st<n>`), Retroarch-ish (`<stem>.state.<n>`, `.state.auto`) or RetroArch (`<stem>.state`,
     * `<stem>.state<n>`) name, suffixes case-insensitive. RetroArch slots 8 and up come back as `n + 100` so `.state9`
     * never lands on [NxPaths.AUTO_SLOT]. Anything else (thumbnails included) is null.
     */
    fun parse(fileName: String): StateName? {
        val p = split(fileName) ?: return null
        return when (p.scheme) {
            Scheme.AUTO -> StateName(p.prefix, NxPaths.AUTO_SLOT)
            Scheme.RETROARCH_ISH -> StateName(p.prefix, p.n)
            Scheme.RETROARCH -> StateName(p.prefix, if (p.n >= NxPaths.SLOT_COUNT) p.n + RETROARCH_AUTO_OFFSET else p.n)
            Scheme.MINUI -> StateName(minarchStem(p.prefix), p.n)
        }
    }

    /**
     * [fileName] (a name [parse] reads) renamed to [slot] (0..7 or auto) in its own scheme: `X.state` → `X.state1`,
     * `X.state.2` → `X.state.0`, `X.gba.st0` → `X.gba.st1`. A `.state.auto` name moves in [auto]'s RetroArch scheme
     * (both write that name). Null when [fileName] is not a state name.
     */
    fun withSlot(fileName: String, slot: Int, auto: StateFormat = StateFormat.DEFAULT): String? {
        require(slot in 0 until NxPaths.SLOT_COUNT || slot == NxPaths.AUTO_SLOT) { "slot $slot" }
        val p = split(fileName) ?: return null
        val scheme = if (p.scheme != Scheme.AUTO) p.scheme
            else if (auto == StateFormat.RETROARCH_ISH || auto == StateFormat.RETROARCH_ISH_COMPRESSED) Scheme.RETROARCH_ISH
            else Scheme.RETROARCH
        return p.prefix + when (scheme) {
            Scheme.MINUI -> ".st$slot"
            Scheme.RETROARCH_ISH -> if (slot == NxPaths.AUTO_SLOT) ".state.auto" else ".state.$slot"
            else -> when (slot) { NxPaths.AUTO_SLOT -> ".state.auto"; 0 -> ".state"; else -> ".state$slot" }
        }
    }

    /**
     * A slot picture (`<rom.ext>.<slot>.bmp`, [NxPaths.thumb]) read as the stem its game's states [parse] to and its
     * slot; null for anything else.
     */
    fun picture(fileName: String): StateName? {
        val m = PICTURE.matchEntire(fileName) ?: return null
        val slot = m.groupValues[2].toInt()
        if (slot !in 0 until NxPaths.SLOT_COUNT && slot != NxPaths.AUTO_SLOT) return null
        return StateName(minarchStem(m.groupValues[1]), slot)
    }

    /**
     * The slot to store a state wanted in [wanted]: that slot when it is real (0..7 or auto) and not in [taken], else the
     * lowest free of 0..7, else null.
     */
    fun place(wanted: Int, taken: Set<Int>): Int? {
        val real = wanted in 0 until NxPaths.SLOT_COUNT || wanted == NxPaths.AUTO_SLOT
        if (real && wanted !in taken) return wanted
        return (0 until NxPaths.SLOT_COUNT).firstOrNull { it !in taken }
    }

    private val PICTURE = Regex("""(.+)\.([0-9])\.bmp""", RegexOption.IGNORE_CASE)

    // Case-insensitive matches on the name itself: indices into a lowercased copy shift where a character lowercases
    // to several ('İ').
    private fun split(fileName: String): Parts? {
        if (fileName.endsWith(".state.auto", ignoreCase = true)) return Parts(fileName.dropLast(".state.auto".length), Scheme.AUTO, NxPaths.AUTO_SLOT)
        fileName.lastIndexOf(".state.", ignoreCase = true).let { i ->
            val n = if (i > 0) digits(fileName.substring(i + ".state.".length)) else null
            if (n != null) return Parts(fileName.substring(0, i), Scheme.RETROARCH_ISH, n)
        }
        if (fileName.endsWith(".state", ignoreCase = true)) return Parts(fileName.dropLast(".state".length), Scheme.RETROARCH, 0)
        fileName.lastIndexOf(".state", ignoreCase = true).let { i ->
            val n = if (i > 0) digits(fileName.substring(i + ".state".length)) else null
            if (n != null) return Parts(fileName.substring(0, i), Scheme.RETROARCH, n)
        }
        fileName.lastIndexOf(".st", ignoreCase = true).let { i ->
            val n = if (i > 0) digits(fileName.substring(i + ".st".length)) else null
            if (n != null) return Parts(fileName.substring(0, i), Scheme.MINUI, n)
        }
        return null
    }

    private fun digits(s: String): Int? = if (s.isNotEmpty() && s.length <= 4 && s.all { it in '0'..'9' }) s.toInt() else null
}
