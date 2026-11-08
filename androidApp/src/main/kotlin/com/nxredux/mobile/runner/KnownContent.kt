package com.nxredux.mobile.runner

import java.io.File

/**
 * The decoded content of one file as last read or written by this process, valid while the file's [Stamp] (modification
 * time and size) is the one recorded with it. A file another writer replaced (Device Sync, a save import) shows a new
 * stamp and is read again, so a comparison against it never trusts content the file no longer holds.
 */
internal class KnownContent {
    data class Stamp(val modified: Long, val length: Long)

    private var stamp: Stamp? = null
    private var bytes: ByteArray? = null

    /** [bytes] is what the file held at [stamp]; the caller must not change the array afterwards. */
    fun remember(stamp: Stamp?, bytes: ByteArray) {
        if (stamp == null) { forget(); return }
        this.stamp = stamp
        this.bytes = bytes
    }

    fun forget() {
        stamp = null
        bytes = null
    }

    /**
     * The file's content at [stamp] (null: no file): the remembered bytes when the stamp still matches, else [read]'s,
     * which are remembered in turn. Take [stamp] before [read], so a change between the two can only cost a read later.
     */
    fun current(stamp: Stamp?, read: () -> ByteArray?): ByteArray? {
        if (stamp == null) { forget(); return null }
        if (stamp == this.stamp) bytes?.let { return it }
        val fresh = read()
        if (fresh != null) remember(stamp, fresh) else forget()
        return fresh
    }

    companion object {
        fun stampOf(file: File): Stamp? = if (file.isFile) Stamp(file.lastModified(), file.length()) else null
    }
}
