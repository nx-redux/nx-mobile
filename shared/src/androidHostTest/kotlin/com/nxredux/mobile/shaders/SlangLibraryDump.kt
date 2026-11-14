package com.nxredux.mobile.shaders

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Parses every preset of a slang shader library and writes each distinct pass as `<hash>.vert` + `<hash>.frag`, the
 * input of the native checker (libretrodroid/src/test/native/slang). Runs only when asked:
 *   NX_SLANG_LIBRARY=<slang-shaders checkout> NX_SLANG_DUMP=<empty dir> ./gradlew :shared:testAndroidHostTest --tests '*SlangLibraryDump*'
 */
class SlangLibraryDump {
    @Test fun dump() {
        val library = System.getenv("NX_SLANG_LIBRARY")?.let(::File) ?: return
        val out = System.getenv("NX_SLANG_DUMP")?.let(::File) ?: return
        out.mkdirs()
        val read: (String) -> String? = { p -> File(library, p).takeIf { it.isFile }?.readText() }
        val presets = library.walkTopDown().filter { it.isFile && it.extension == "slangp" }.map { it.relativeTo(library).path }.sorted().toList()
        val failures = sortedMapOf<String, MutableList<String>>()
        val index = StringBuilder()
        var passes = 0
        for (preset in presets) {
            try {
                val chain = SlangPreset.parse(preset, read)
                index.append(preset)
                for (p in chain.passes) {
                    val hash = MessageDigest.getInstance("SHA-1").digest((p.vertex + "\u0000" + p.fragment).toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
                    index.append(' ').append(hash)
                    val vert = File(out, "$hash.vert")
                    if (!vert.exists()) {
                        vert.writeText(p.vertex)
                        File(out, "$hash.frag").writeText(p.fragment)
                        passes++
                    }
                }
                index.append('\n')
            } catch (e: PresetException) {
                failures.getOrPut(e.message.orEmpty().substringBefore(':')) { mutableListOf() } += "$preset: ${e.message}"
            }
        }
        File(out, "index.txt").writeText(index.toString())
        File(out, "parse-failures.txt").writeText(failures.values.flatten().joinToString("\n"))
        println("slang library: ${presets.size} presets, ${failures.values.sumOf { it.size }} failed to parse, $passes distinct passes")
        failures.forEach { (kind, list) -> println("  ${list.size} x $kind   (e.g. ${list.first()})") }
        assertTrue(presets.isNotEmpty())
    }
}
