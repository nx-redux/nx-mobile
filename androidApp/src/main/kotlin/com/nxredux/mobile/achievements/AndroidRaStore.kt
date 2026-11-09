package com.nxredux.mobile.achievements

import android.content.Context
import com.nxredux.mobile.runner.writeAtomically
import com.nxredux.mobile.storage.DataRoot
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * RaStore under `.userdata/` of two roots: the account tree ([RaPaths.ACCOUNT]: login, journal, sessions) under
 * [privateRoot] (app-private for good), everything else ([RaPaths.SHARED], the game definitions nx-redux reads too)
 * under [dataRoot] ([DataRoot.of], the home folder once the data moved), resolved per call so a home change is followed.
 * Appends are fsync'd; whole-file writes go through writeAtomically (which creates the parent). A missing file reads
 * as null and lists as empty; every failure is an [RaStoreException].
 */
class AndroidRaStore internal constructor(private val privateRoot: () -> File, private val dataRoot: () -> File) : RaStore {
    constructor(context: Context) : this({ DataRoot.private(context) }, { DataRoot.of(context) })

    /**
     * [path] under its root: an account path under the private account tree, any other under the data root's
     * `.userdata`; anything that resolves outside it (`..`, an absolute path, a symlink) is refused.
     */
    private fun file(path: String): File {
        val account = path == RaPaths.ACCOUNT || path.startsWith(RaPaths.ACCOUNT + "/")
        val (root, f) = try {
            val userdata = File(if (account) privateRoot() else dataRoot(), RaPaths.ROOT).canonicalFile
            val root = if (account) File(userdata, RaPaths.ACCOUNT).canonicalFile else userdata
            root to File(userdata, path).canonicalFile
        } catch (e: IOException) {
            throw RaStoreException("resolve $path", e)
        }
        if (f != root && !f.path.startsWith(root.path + File.separator)) throw RaStoreException("outside ra root: $path")
        return f
    }

    private suspend fun <T> io(what: String, block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: RaStoreException) {
            throw e
        } catch (e: IOException) {
            throw RaStoreException(what, e)
        } catch (e: SecurityException) {
            throw RaStoreException(what, e)
        }
    }

    override suspend fun read(path: String): ByteArray? = io("read $path") { file(path).takeIf { it.isFile }?.readBytes() }

    override suspend fun writeAtomic(path: String, bytes: ByteArray) = io("write $path") { file(path).writeAtomically(bytes) }

    override suspend fun append(path: String, bytes: ByteArray) = io("append $path") {
        val f = file(path)
        f.parentFile?.mkdirs()
        FileOutputStream(f, true).use { it.write(bytes); it.fd.sync() }
    }

    /**
     * Size, modification time and file key (device + inode): an atomic rewrite is a new inode, so a remove plus a
     * same-length append from another process never matches what came before, even inside one clock tick.
     */
    override suspend fun stamp(path: String): String? = io("stamp $path") {
        val a = try {
            Files.readAttributes(file(path).toPath(), BasicFileAttributes::class.java)
        } catch (e: NoSuchFileException) {
            return@io null
        }
        if (!a.isRegularFile) null else "${a.size()}:${a.lastModifiedTime().to(TimeUnit.NANOSECONDS)}:${a.fileKey()}"
    }

    override suspend fun delete(path: String) { io("delete $path") { file(path).delete() } }

    /** Deletes [dir] recursively; the store root itself is refused (it holds every app's `.userdata`). */
    override suspend fun deleteTree(dir: String) {
        if (dir.isEmpty()) throw RaStoreException("refusing to delete the store root")
        io("delete $dir/") { file(dir).deleteRecursively() }
    }

    override suspend fun list(dir: String): List<String> = io("list $dir") { file(dir).list()?.toList() ?: emptyList() }

    override suspend fun exists(path: String): Boolean = io("exists $path") { file(path).exists() }

    /**
     * An OS file lock on `<path>.lock` beside [path]: a sidecar, because a POSIX lock goes with any descriptor of its file
     * the process closes, and [path] itself is replaced by atomic writes. A process holds one such lock per file, so its
     * own callers queue on [processLocks] first. A file system that refuses locks runs [block] unlocked rather than
     * failing the write.
     */
    override suspend fun <T> locked(path: String, block: suspend () -> T): T {
        val lockFile = File(file(path).path + RaPaths.LOCK_SUFFIX)
        return processLocks.computeIfAbsent(lockFile.path) { Mutex() }.withLock {
            // Set from inside the IO hop, not returned from it: a caller cancelled while lock() blocks gets the hop's
            // result discarded on the way back (withContext's prompt cancellation, NonCancellable or not), and a
            // dropped channel would keep the OS lock, so every later lock here would overlap it.
            var channel: FileChannel? = null
            try {
                withContext(Dispatchers.IO) {
                    channel = try {
                        lockFile.parentFile?.mkdirs()
                        RandomAccessFile(lockFile, "rw").channel.also { c ->
                            try {
                                c.lock()
                            } catch (e: Throwable) {
                                c.close()
                                throw e
                            }
                        }
                    } catch (e: IOException) {
                        null
                    } catch (e: SecurityException) {
                        null
                    } catch (e: OverlappingFileLockException) {
                        // This process already holds it outside [processLocks] (it never should): unlocked, as a
                        // refusing file system runs, rather than failing every write.
                        null
                    }
                }
                currentCoroutineContext().ensureActive()
                block()
            } finally {
                channel?.let { withContext(NonCancellable + Dispatchers.IO) { runCatching { it.close() } } }
            }
        }
    }

    private companion object {
        val processLocks = ConcurrentHashMap<String, Mutex>()
    }
}
