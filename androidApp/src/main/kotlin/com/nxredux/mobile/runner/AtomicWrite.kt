package com.nxredux.mobile.runner

import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * Writes via a sibling temp file + rename, so a crash or kill never leaves a half-written file. The temp
 * name is unique per call ([uniqueTempName], ending in [suffix]), so concurrent writers of one file don't
 * share it: the last rename wins. The temp file is removed when the write or the rename fails.
 */
fun File.writeAtomically(bytes: ByteArray, suffix: String = ".tmp") {
    val dir = parentFile
    dir?.mkdirs()
    // createNewFile is atomic, so two writers can never claim the same name.
    val tmp = generateSequence { File(dir, uniqueTempName(name, suffix)) }
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
 * `<name>.<hex><suffix>`: a temp sibling name another writer of [name] all but never picks. A short random part keeps
 * long ROM names under the file-name length limit; the suffix stays last, so the rules that skip temp files by their
 * ending (`.tmp`, `.part`) still see it.
 */
fun uniqueTempName(name: String, suffix: String): String = "$name.${Random.nextInt().toUInt().toString(16)}$suffix"

/**
 * A stamp that changes when the file is rewritten, for an image ref's version ([com.nxredux.mobile.storage.FileRefs.versioned]):
 * the modification time and the size together. A save state's screenshot (`.bmp`) renamed over the old one on shared storage
 * can still show the old modification time to another reader (seen from adb on a Retroid Pocket Nova, Android 13, while the
 * writing process saw the new one); two screenshots of different frames all but never have the same size.
 */
fun File.contentStamp(): Long = lastModified() xor (length() shl 24)
