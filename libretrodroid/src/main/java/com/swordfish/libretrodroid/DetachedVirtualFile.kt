package com.swordfish.libretrodroid

import android.os.ParcelFileDescriptor

/**
 * A virtual file whose descriptor is handed to native as a raw fd. Native sets [taken] on each item it has read (it then
 * owns that fd, also when the call fails later); a Java exception can stop its walk part way, and the items it never
 * reached are still this side's to close ([handOver]).
 */
internal data class DetachedVirtualFile(val virtualPath: String, val fileDescriptor: Int) {
    @JvmField var taken: Boolean = false
}

/**
 * Detaches each of [files] with [detach]; a detach failing part way closes the ones already detached (native never
 * sees them) with [close] and rethrows.
 */
internal inline fun <T> detachAll(
    files: List<T>,
    close: (Int) -> Unit = ::closeRawFd,
    detach: (T) -> DetachedVirtualFile,
): List<DetachedVirtualFile> {
    val detached = ArrayList<DetachedVirtualFile>(files.size)
    try {
        files.forEach { detached += detach(it) }
    } catch (e: Throwable) {
        detached.forEach { runCatching { close(it.fileDescriptor) } }
        throw e
    }
    return detached
}

/**
 * Runs the native [call] on [files]. Success: native owns every fd. A throw: the fds native never read are closed with
 * [close]; the ones it read are its own to close.
 */
internal inline fun <T> handOver(
    files: List<DetachedVirtualFile>,
    close: (Int) -> Unit = ::closeRawFd,
    call: (List<DetachedVirtualFile>) -> T,
): T {
    try {
        return call(files)
    } catch (e: Throwable) {
        files.forEach { if (!it.taken) runCatching { close(it.fileDescriptor) } }
        throw e
    }
}

internal fun closeRawFd(fd: Int) = ParcelFileDescriptor.adoptFd(fd).close()
