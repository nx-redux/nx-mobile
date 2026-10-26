package com.nxredux.mobile.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Keeps every colour named: literals live in the theme files, every alpha names its role. */
class ColourRolesLockTest {

    private val literalHomes = setOf(
        "com/nxredux/mobile/ui/nx/NxTheme.kt",
        "com/nxredux/mobile/settings/Accent.kt",
        "com/nxredux/mobile/ui/nx/PadPalettes.kt",
    )
    private val themeFile = "com/nxredux/mobile/ui/nx/NxTheme.kt"

    private val literal = Regex("""Color\(\s*(\w+\s*=\s*)?0x""")
    // An alpha: `.copy(... alpha = ...)` (any layout), `.alpha(`, or a named `alpha = <number>` argument (Image, Icon).
    private val alphaCall = Regex("""\.\s*(?:copy\s*\(\s*(?:[^()]*,\s*)?alpha\s*=|alpha\s*\()|,\s*alpha\s*=\s*\d""")
    // The one alpha that needs no tag: the ladder's own Float, e.g. `.alpha(colors.artFar)`.
    private val ladderFloat = Regex("""alpha\s*(?:=|\()\s*(?:\w+\.)*artFar\b""")

    private fun sourceRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(File(dir, "src/commonMain/kotlin"), File(dir, "shared/src/commonMain/kotlin"))
                .firstOrNull { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("src/commonMain/kotlin not found above ${File("").absolutePath}")
    }

    private fun sources(root: File): List<File> {
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.size >= 50, "scanned only ${files.size} .kt files; the source root looks wrong")
        return files
    }

    private fun isProse(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
    }

    private fun scan(skip: (String) -> Boolean, hit: (Regex, List<String>, String) -> List<Pair<Int, MatchResult>>, ok: (List<String>, Int, MatchResult) -> Boolean, rx: Regex): List<String> {
        val root = sourceRoot()
        return sources(root).flatMap { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (skip(rel)) return@flatMap emptyList<String>()
            val text = f.readText()
            val lines = text.split("\n")
            hit(rx, lines, text)
                .filter { (i, m) -> !isProse(lines[i]) && !ok(lines, i, m) }
                .map { (i, _) -> "$rel:${i + 1}: ${lines[i].trim()}" }
                .distinct()
        }
    }

    /** Every match of [rx] over the whole text, with the line its dot/keyword starts on. */
    private fun matches(rx: Regex, lines: List<String>, text: String): List<Pair<Int, MatchResult>> {
        val starts = IntArray(lines.size)
        var off = 0
        lines.forEachIndexed { i, l -> starts[i] = off; off += l.length + 1 }
        return rx.findAll(text).map { m ->
            // Report the line of the first non-space character of the match.
            val at = m.range.first + (m.value.length - m.value.trimStart().length)
            val line = starts.indexOfLast { it <= at }
            line to m
        }.toList()
    }

    @Test fun no_colour_literal_outside_the_theme() {
        val bad = scan({ it in literalHomes }, ::matches, { _, _, _ -> false }, literal)
        assertTrue(bad.isEmpty(), "Colour literals outside the theme files:\n" + bad.joinToString("\n"))
    }

    @Test fun every_alpha_names_its_role() {
        val bad = scan({ it == themeFile }, ::matches, { lines, i, m ->
            "// role:" in lines[i] ||
                (i > 0 && "// role:" in lines[i - 1]) ||
                ladderFloat.containsMatchIn(m.value + lines[i].substringAfter(m.value, ""))
        }, alphaCall)
        assertTrue(bad.isEmpty(), "Alphas without a role (add `// role: ...` on the line or the line above, or pass the ladder's artFar):\n" + bad.joinToString("\n"))
    }
}
