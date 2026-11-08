package com.nxredux.mobile.library

import com.nxredux.mobile.storage.TabLines

/**
 * `systems.txt`: one "<file name><TAB><TAG>" per line, next to the ROMs. Says which tag (console + emulator)
 * a file runs as, overriding the folder's tag or the resolution chain. Only this app reads it. File names
 * are kept byte-for-byte (no trimming): a name is whatever the file system says it is. Tags are upper-cased.
 * Unknown tags are preserved so a newer build's entries survive a rewrite by an older one.
 */
object SidecarTxt {
    const val FILE = "systems.txt"

    fun parse(text: String): Map<String, String> = TabLines.parsePairs(text) { it.trim().uppercase() }

    fun render(entries: Map<String, String>): String = TabLines.render(entries)

    /** [text] (null when the file does not exist) with [file] set to [tag]. A file name with a tab cannot be stored: returns [text] as is (or ""). */
    fun with(text: String?, file: String, tag: String): String {
        if ('\t' in file || '\n' in file || '\r' in file || file.isEmpty()) return text ?: ""
        return render(parse(text ?: "") + (file to tag.trim().uppercase()))
    }
}
