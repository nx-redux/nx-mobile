package com.nxredux.mobile.storage

import com.nxredux.mobile.ui.ToolKind

/**
 * The pinned tools, in the app-private [PrivateFiles] as [FILE]: one key per line, in pin order ([com.nxredux.mobile.ui.pinKey]:
 * a [ToolKind] name, or "APP:" and an Android app's id). Never in `shortcuts.txt` (game pins only). An unknown key is
 * dropped, as is a repeat.
 */
object ToolPins {
    const val FILE = "tool-pins.txt"
    private const val APP = "APP:"
    private fun valid(key: String): Boolean =
        if (key.startsWith(APP)) key.length > APP.length else ToolKind.entries.any { it != ToolKind.APP && it != ToolKind.KIDS_LOCK && it.name == key }
    fun decode(text: String?): List<String> =
        text.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() && valid(it) }.distinct().toList()
    fun encode(keys: List<String>): String = keys.distinct().joinToString("\n")
    fun add(keys: List<String>, k: String): List<String> = if (k in keys) keys else keys + k
    fun remove(keys: List<String>, k: String): List<String> = keys - k
}
