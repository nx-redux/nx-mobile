package com.nxredux.mobile.runner

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Runs [block] holding an exclusive lock on [lockFile] (created with its folder if missing): the lock that spans the menu
 * and the game process, which both write some shared trees. A [java.nio.channels.FileLock] is per JVM and an overlapping
 * one throws, so callers keep their in-process lock around this. A lock file that cannot be opened runs [block] unlocked,
 * as before there was one. Blocks while another process holds it: never on the main thread.
 */
inline fun <T> withFileLock(lockFile: File, block: () -> T): T {
    val channel = runCatching {
        lockFile.parentFile?.mkdirs()
        FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    }.getOrNull()
    try {
        channel?.lock()
        return block()
    } finally {
        runCatching { channel?.close() }   // closing releases the lock
    }
}
