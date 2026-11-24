package com.nxredux.mobile.library

import com.nxredux.mobile.storage.TabLines

/**
 * The word a game wears as a pill by its name ([GameEntry.badge]): the game's own choice ([IndexedGame.badgeChoice]),
 * else its MSU set ([IndexedGame.badge]), else what its file name's bracketed tags say ([detect]). One per game.
 */
object GameBadges {
    const val HACK = "Hack"
    const val TRANSLATED = "Translated"
    const val PROTO = "Proto"
    const val BETA = "Beta"
    const val DEMO = "Demo"
    const val HOMEBREW = "Homebrew"

    /** The choice that shows no badge at all, even over a detected one. */
    const val NONE = "None"

    /**
     * The badges a game can be given, in the order a tag wins when a name carries several: what was done to the game
     * (a hack, a translation) says more about what is played than how far it got (proto, beta, demo), and that more
     * than where it came from.
     */
    val FIXED: List<String> = listOf(HACK, TRANSLATED, PROTO, BETA, DEMO, HOMEBREW)

    /** The per-game choices in Game Settings' order: Auto (null), None, then each fixed badge. */
    val CHOICES: List<String?> = listOf(null, NONE) + FIXED

    /** A stored choice as the game uses it: [NONE] or a fixed badge; anything else (a word this build does not know) is Auto. */
    fun choiceOf(stored: String?): String? = stored?.takeIf { it == NONE || it in FIXED }

    /** The badge shown for a game with [choice], scan badge [scanned] (an MSU set) and name source [stem]; null for none. */
    fun shown(choice: String?, scanned: String?, stem: String): String? = when (val c = choiceOf(choice)) {
        NONE -> null
        null -> scanned ?: detect(stem)
        else -> c
    }

    /** The badge [stem]'s bracketed tags give (the [FIXED] order breaks ties), or null; the title outside them never counts. */
    fun detect(stem: String): String? {
        var best = -1
        for ((square, text) in groups(stem)) {
            for (part in if (square) listOf(text) else text.split(',')) {
                val i = tagBadge(part.trim().lowercase(), square)?.let(FIXED::indexOf) ?: continue
                if (best < 0 || i < best) best = i
            }
        }
        return FIXED.getOrNull(best)
    }

    /** The bracketed groups of [stem] in order, each with whether it is square ([h1], [T+Eng]) and its text; unclosed ones are left. */
    private fun groups(stem: String): List<Pair<Boolean, String>> {
        val out = ArrayList<Pair<Boolean, String>>()
        var i = 0
        while (i < stem.length) {
            val close = when (stem[i]) { '(' -> ')'; '[' -> ']'; else -> null }
            val end = close?.let { stem.indexOf(it, i + 1) } ?: -1
            if (end < 0) { i++; continue }
            out += (stem[i] == '[') to stem.substring(i + 1, end)
            i = end + 1
        }
        return out
    }

    /**
     * GoodTools' codes, only in square brackets (in parentheses "(Hu)" is a language, "(T-Rex)" a title): a hack's [h],
     * [h1], [h2C], [hI], [hIR], [hM04]; a translation's [T+Eng], [T-Spa1.0], [T+Eng_Group].
     */
    private val HACK_CODE = Regex("h(\\d+c?|ir?|m\\d+)?")
    private val TRANSLATION_CODE = Regex("t[+-][a-z]{2,3}([\\d_ .].*)?")
    private val NUMBERED = Regex("(beta|proto|prototype|demo)\\d*")

    /** The badge one tag (lowercase, trimmed) stands for, or null. */
    private fun tagBadge(tag: String, square: Boolean): String? {
        if (tag.isEmpty()) return null
        if (square && HACK_CODE.matches(tag)) return HACK
        if (square && TRANSLATION_CODE.matches(tag)) return TRANSLATED
        // A tag's first or last word: "(Hack by …)" and "(Kaizo Hack)", "(Beta 2)" and "(Kiosk Demo)".
        val words = tag.split(' ').filter { it.isNotEmpty() }
        val ends = setOf(words.first(), words.last())
        val stage = ends.firstOrNull(NUMBERED::matches)?.trimEnd { it.isDigit() }
        return when {
            "hack" in ends || words.first() == "hacked" -> HACK
            "translated" in ends || "translation" in ends -> TRANSLATED
            stage == "proto" || stage == "prototype" -> PROTO
            stage == "beta" -> BETA
            stage == "demo" || words.first() == "sample" -> DEMO
            "homebrew" in ends || tag == "pd" -> HOMEBREW
            else -> null
        }
    }

    /** `badges.txt`: one "<game path><TAB><choice>" per line ([com.nxredux.mobile.library.path]); Auto is no line. */
    fun parse(text: String): Map<String, String> = TabLines.parsePairs(text) { it.trim() }

    fun render(choices: Map<String, String>): String = TabLines.render(choices)

    /**
     * [choices] with [path]'s set to [choice] (null: Auto, its line dropped). A path with a tab or a line break cannot be
     * stored: [choices] comes back as is. Lines this build does not know are kept.
     */
    fun with(choices: Map<String, String>, path: String, choice: String?): Map<String, String> = when {
        path.isEmpty() || '\t' in path || '\n' in path || '\r' in path -> choices
        choice == null -> choices - path
        else -> choices + (path to choice)
    }
}
