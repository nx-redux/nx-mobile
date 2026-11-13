package com.nxredux.mobile.gpudriver

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Installed custom GPU drivers: one folder per driver under [root] (`filesDir/gpu_drivers`), named by the first 16 hex
 * digits of its package's SHA-256. Rules for what a package may hold live in [GpuDriverPackage]; this class unpacks,
 * lists and removes.
 */
class AndroidGpuDrivers(private val root: File, private val sdkInt: Int) {
    private val lock = Any()

    /** The installed drivers sorted by name; a folder without a readable meta.json is skipped, a `.tmp` folder ignored. */
    fun installed(): List<GpuDriver> {
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            if (dir.name.endsWith(TMP)) return@mapNotNull null   // an install may be unpacking into it right now
            val meta = runCatching { File(dir, GpuDriverPackage.META).readText() }.getOrNull() ?: return@mapNotNull null
            GpuDriverPackage.parseMeta(dir.name, meta)
        }.sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
    }

    /**
     * Installs the zip [open] returns (it may be called twice: once to hash and inspect, once to unpack). Never throws:
     * anything wrong is a [PackageCheck.Rejected] and nothing is left behind.
     */
    fun install(open: () -> InputStream): PackageCheck = synchronized(lock) {
        var tmp: File? = null
        try {
            root.listFiles { f -> f.isDirectory && f.name.endsWith(TMP) }?.forEach { it.deleteRecursively() }   // an interrupted earlier install
            val scan = scan(open) ?: return PackageCheck.Rejected("This file isn't a zip.")
            if (scan is Scan.Bad) return PackageCheck.Rejected(scan.reason)
            scan as Scan.Good
            val check = GpuDriverPackage.check(scan.id, scan.meta, scan.entries, { scan.heads[it] }, sdkInt)
            if (check !is PackageCheck.Ok) return check
            val target = directory(scan.id)
            if (File(target, GpuDriverPackage.META).isFile) return check
            root.mkdirs()
            val work = File(root, scan.id + TMP).also { tmp = it }
            work.deleteRecursively()
            unpack(open, work)?.let { return PackageCheck.Rejected(it) }
            target.deleteRecursively()
            if (!work.renameTo(target)) return PackageCheck.Rejected("This package can't be read.")
            return check
        } catch (_: IOException) {
            return PackageCheck.Rejected("This package can't be read.")
        } finally {
            tmp?.takeIf { it.exists() }?.deleteRecursively()
        }
    }

    fun remove(id: String) {
        if (id.isNotEmpty() && '/' !in id && '\\' !in id && id != "..") directory(id).deleteRecursively()
    }

    fun directory(id: String): File = File(root, id)

    private sealed interface Scan {
        class Good(val id: String, val meta: String?, val entries: Map<String, Long>, val heads: Map<String, ByteArray>) : Scan
        class Bad(val reason: String) : Scan
    }

    /** One pass: the package id (hash of the bytes), meta.json, each file's real size and first 20 bytes. Null: not a zip. */
    private fun scan(open: () -> InputStream): Scan? {
        val digest = MessageDigest.getInstance("SHA-256")
        val entries = LinkedHashMap<String, Long>()
        val heads = HashMap<String, ByteArray>()
        var meta: String? = null
        var count = 0
        var total = 0L
        open().use { raw ->
            val hashed = java.security.DigestInputStream(raw, digest)
            val zip = ZipInputStream(hashed)
            val buf = ByteArray(BUFFER)
            while (true) {
                val e = zip.nextEntry ?: break
                count++
                if (!GpuDriverPackage.safeEntry(e.name)) return Scan.Bad("This package has unsafe file paths.")
                if (e.isDirectory) continue
                var size = 0L
                val head = java.io.ByteArrayOutputStream()
                val metaBytes = if (e.name == GpuDriverPackage.META) java.io.ByteArrayOutputStream() else null
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    size += n
                    total += n   // the bytes really inflated, whatever the headers claim
                    if (total > GpuDriverPackage.MAX_UNPACKED_BYTES) return Scan.Bad("This package is too large.")
                    if (head.size() < HEAD) head.write(buf, 0, minOf(n, HEAD - head.size()))
                    if (metaBytes != null) {
                        if (metaBytes.size() + n > MAX_META) return Scan.Bad("This package's meta.json can't be read.")
                        metaBytes.write(buf, 0, n)
                    }
                }
                entries[e.name] = size
                heads[e.name] = head.toByteArray()
                if (metaBytes != null) meta = metaBytes.toString(Charsets.UTF_8)
            }
            hashed.copyTo(java.io.OutputStream.nullOutputStream())   // trailing bytes count toward the hash
        }
        if (count == 0) return null
        val id = digest.digest().joinToString("") { "%02x".format(it) }.take(ID_LENGTH)
        return Scan.Good(id, meta, entries, heads)
    }

    /** Writes the zip under [work]; null on success, else the reason. Every path is checked to stay under [work]. */
    private fun unpack(open: () -> InputStream, work: File): String? {
        work.mkdirs()
        val base = work.canonicalFile.toPath()
        var total = 0L
        open().use { raw ->
            val zip = ZipInputStream(raw)
            val buf = ByteArray(BUFFER)
            while (true) {
                val e = zip.nextEntry ?: break
                if (!GpuDriverPackage.safeEntry(e.name)) return "This package has unsafe file paths."
                val out = File(work, e.name).canonicalFile
                if (!out.toPath().startsWith(base) || out.toPath() == base) return "This package has unsafe file paths."
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { os ->
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > GpuDriverPackage.MAX_UNPACKED_BYTES) return "This package is too large."
                        os.write(buf, 0, n)
                    }
                }
            }
        }
        return null
    }

    companion object {
        private const val TMP = ".tmp"
        private const val BUFFER = 64 * 1024
        private const val HEAD = 20
        private const val MAX_META = 1024 * 1024
        private const val ID_LENGTH = 16

        /** Where the app keeps its drivers: `filesDir/gpu_drivers`. */
        fun root(filesDir: File): File = File(filesDir, "gpu_drivers")

        /**
         * Null when custom drivers are available here, else why not (shown as is); "" when the feature is off (no row at all).
         * The app does not check that a package matches the phone's GPU: a package for the wrong GPU fails to start and the
         * launch falls back.
         */
        fun unavailableReason(enabled: Boolean, sdkInt: Int): String? = when {
            !enabled -> ""
            sdkInt < 28 -> "Custom drivers need Android 9 or later"
            else -> null
        }
    }
}
