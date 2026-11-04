package com.nxredux.mobile.runner

import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * Writes via a sibling temp file + rename, so a crash or kill never leaves a half-written file. The temp
 * name is unique per call, so concurrent writers of one file don't share it: the last rename wins. The
 * temp file is removed when the write or the rename fails.
 */
fun File.writeAtomically(bytes: ByteArray) {
    val dir = parentFile
    dir?.mkdirs()
    // createNewFile is atomic, so two writers can never claim the same name. A short suffix keeps long ROM
    // names under the file-name length limit.
    val tmp = generateSequence { File(dir, "$name.${Random.nextInt().toUInt().toString(16)}.tmp") }
        .first { it.createNewFile() }
    try {
        tmp.outputStream().use { it.write(bytes); it.fd.sync() }
        if (!tmp.renameTo(this)) throw IOException("rename failed: $path")
    } catch (e: Throwable) {
        tmp.delete()
        throw e
    }
}

/**
 * A stamp that changes when the file is rewritten, for an image ref's version ([com.nxredux.mobile.storage.FileRefs.versioned]):
 * the modification time and the size together. A save state's screenshot (`.bmp`) renamed over the old one on shared storage
 * can still show the old modification time to another reader (seen from adb on a Retroid Pocket Nova, Android 13, while the
 * writing process saw the new one); two screenshots of different frames all but never have the same size.
 */
fun File.contentStamp(): Long = lastModified() xor (length() shl 24)
