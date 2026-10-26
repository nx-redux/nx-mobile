package com.nxredux.mobile.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Keeps every colour named: literals live in the theme files, every alpha names its role. */
class ColourRolesLockTest {

    private val literalHomes = setOf("NxTheme.kt", "Accent.kt", "PadPalettes.kt")
    private val ladder = listOf("inkDim", "inkMuted", "inkFaint", "inkEdge", "artFar", "scrim")

    private fun sourceRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidates = listOf(File(dir, "src/commonMain/kotlin"), File(dir, "shared/src/commonMain/kotlin"))
            candidates.firstOrNull { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("src/commonMain/kotlin not found above ${File("").absolutePath}")
    }

    private fun sources(): List<File> {
        val files = sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.size >= 50, "scanned only ${files.size} .kt files; the source root looks wrong")
        return files
    }

    private fun isProse(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
    }

    private fun offenders(skip: (File) -> Boolean, hit: (List<String>, Int) -> Boolean): List<String> =
        sources().filterNot(skip).flatMap { f ->
            val lines = f.readLines()
            lines.indices.filter { !isProse(lines[it]) && hit(lines, it) }.map { "${f.name}:${it + 1}: ${lines[it].trim()}" }
        }

    @Test fun no_colour_literal_outside_the_theme() {
        val bad = offenders({ it.name in literalHomes }) { lines, i -> "Color(0x" in lines[i] }
        assertTrue(bad.isEmpty(), "Colour literals outside the theme files:\n" + bad.joinToString("\n"))
    }

    @Test fun every_alpha_names_its_role() {
        val bad = offenders({ it.name == "NxTheme.kt" }) { lines, i ->
            val line = lines[i]
            val alpha = "copy(alpha" in line || ".alpha(" in line
            val named = "// role:" in line ||
                (i > 0 && "//" in lines[i - 1] && "// role:" in lines[i - 1]) ||
                ladder.any { it in line }
            alpha && !named
        }
        assertTrue(bad.isEmpty(), "Alphas without a role (add `// role: ...` or use a ladder member):\n" + bad.joinToString("\n"))
    }
}
