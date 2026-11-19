package com.nxredux.mobile.bios

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.SafTree
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** The home's `Bios/<console>/` folders, as Import BIOS writes them: a SAF tree or, with All files access, a path. */
interface HomeBiosFolder {
    /** The md5 of `Bios/<console>/<name>`, null when it is not there or cannot be read. */
    fun md5(console: String, name: String): String?

    /** Whether any file in `Bios/<console>/` has [md5]. */
    fun anyWithMd5(console: String, md5: String): Boolean

    /**
     * Writes `Bios/<console>/<name>` from [from], creating the folders: built beside the target and moved over it, so a
     * copy cut short leaves the old file (or none), never half a BIOS. False on any failure.
     */
    fun write(console: String, name: String, from: () -> InputStream): Boolean

    companion object {
        fun of(resolver: ContentResolver, home: ContentRef): HomeBiosFolder =
            if (FileRefs.isFile(home)) FileHomeBios(File(FileRefs.path(home))) else SafHomeBios(SafTree(resolver), Uri.parse(home.value))
    }
}

object Md5 {
    /**
     * Copies [input] to [output] (both closed) and returns its md5 (lowercase hex) and length; null, stopping there, once
     * more than [limit] bytes came.
     */
    fun copy(input: InputStream, output: OutputStream, limit: Long = Long.MAX_VALUE): Pair<String, Long>? {
        val digest = MessageDigest.getInstance("MD5")
        var total = 0L
        input.use { i ->
            output.use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > limit) return null
                    digest.update(buf, 0, n)
                    o.write(buf, 0, n)
                }
            }
        }
        return hex(digest.digest()) to total
    }

    fun of(input: InputStream): String = copy(input, OutputStream.nullOutputStream())!!.first

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}

private const val PART = ".part"

class FileHomeBios(private val home: File) : HomeBiosFolder {
    private fun file(console: String, name: String) = File(File(File(home, "Bios"), console), name)

    override fun md5(console: String, name: String): String? =
        file(console, name).takeIf { it.isFile }?.let { f -> runCatching { Md5.of(f.inputStream()) }.getOrNull() }

    override fun anyWithMd5(console: String, md5: String): Boolean =
        File(File(home, "Bios"), console).listFiles().orEmpty().any { f -> f.isFile && !f.name.endsWith(PART) && runCatching { Md5.of(f.inputStream()) }.getOrNull() == md5 }

    override fun write(console: String, name: String, from: () -> InputStream): Boolean {
        val target = file(console, name)
        val part = File(target.parentFile, name + PART)
        return runCatching {
            target.parentFile!!.mkdirs()
            val out = part.outputStream()
            Md5.copy(from(), out) ?: error("unreachable")
            if (!part.renameTo(target)) {
                target.delete()
                check(part.renameTo(target)) { "rename failed" }
            }
            true
        }.getOrElse { part.delete(); false }
    }
}

class SafHomeBios(private val saf: SafTree, tree: Uri) : HomeBiosFolder {
    private val root: Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun dir(console: String, create: Boolean): Uri? {
        val bios = if (create) saf.ensureDir(root, "Bios") else saf.child(root, "Bios")?.takeIf { it.isDirectory }?.uri
        bios ?: return null
        return if (create) saf.ensureDir(bios, console) else saf.child(bios, console)?.takeIf { it.isDirectory }?.uri
    }

    override fun md5(console: String, name: String): String? {
        val d = dir(console, create = false) ?: return null
        val f = saf.child(d, name)?.takeIf { !it.isDirectory } ?: return null
        return runCatching { saf.openRead(f.uri)?.let(Md5::of) }.getOrNull()
    }

    override fun anyWithMd5(console: String, md5: String): Boolean {
        val d = dir(console, create = false) ?: return false
        return saf.listChildren(d).any { e ->
            !e.isDirectory && !e.name.endsWith(PART) && runCatching { saf.openRead(e.uri)?.let(Md5::of) }.getOrNull() == md5
        }
    }

    override fun write(console: String, name: String, from: () -> InputStream): Boolean {
        val d = dir(console, create = true) ?: return false
        saf.child(d, name + PART)?.let { saf.delete(it.uri) }
        val part = saf.createNew(d, name + PART) ?: return false
        val ok = runCatching { val out = saf.openWrite(part) ?: error("no write"); Md5.copy(from(), out); true }.getOrDefault(false)
        if (!ok) { saf.delete(part); return false }
        saf.child(d, name)?.let { if (!saf.delete(it.uri)) { saf.delete(part); return false } }
        if (saf.rename(part, name) != null) return true
        // A provider that cannot rename: the old file is gone, so write the target straight from the part, then drop it.
        val target = saf.createFile(d, name) ?: return false
        val copied = runCatching { val out = saf.openWrite(target) ?: error("no write"); Md5.copy(saf.openRead(part) ?: error("no read"), out); true }.getOrDefault(false)
        saf.delete(part)
        return copied
    }
}
