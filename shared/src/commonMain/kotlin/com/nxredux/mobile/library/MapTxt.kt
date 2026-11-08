package com.nxredux.mobile.library

import com.nxredux.mobile.storage.TabLines

/**
 * NX Redux map.txt: one "<file name><TAB><display name>" per line. Lines without a tab are ignored. Keys are the
 * full file name (or a disc folder's name); the handheld looks the entry's basename up the same way.
 */
object MapTxt {
    fun parse(text: String): Map<String, String> = TabLines.parsePairs(text, key = String::trim, value = String::trim)

    fun render(entries: Map<String, String>): String = TabLines.render(entries)

    /** [text] (null when the file does not exist) with [key] set to [name]. A key or name with a tab or newline cannot be stored: [text] as is (or ""). */
    fun with(text: String?, key: String, name: String): String {
        if (key.isEmpty() || name.isEmpty() || listOf(key, name).any { '\t' in it || '\n' in it || '\r' in it }) return text ?: ""
        return render(parse(text ?: "") + (key to name))
    }

    /** [text] without [key]; unchanged when absent. */
    fun without(text: String?, key: String): String = render(parse(text ?: "") - key)
}
