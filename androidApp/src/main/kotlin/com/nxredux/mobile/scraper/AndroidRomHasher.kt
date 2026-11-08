package com.nxredux.mobile.scraper

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.AndroidRefs
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The zip part of [AndroidRomHasher] (unit-tested on plain streams). */
internal object ZipRomCrc {
    /**
     * The CRC32 and size of the largest file in the zip [input] (the ROM beside any readme), unpacked; null when it is
     * not a zip, holds no file, or unpacks to more than [limit] bytes in all (a zip bomb stops there). [check] runs
     * about once per MB unpacked (the caller's cancellation).
     */
    fun read(input: InputStream, limit: Long, check: () -> Unit = {}): Pair<Long, Long>? {
        val zip = ZipInputStream(input)
        val buf = ByteArray(64 * 1024)
        var best: Pair<Long, Long>? = null
        var total = 0L
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val crc = CRC32(); var size = 0L
            while (true) {
                val n = zip.read(buf)
                if (n < 0) break
                size += n; total += n
                if (total > limit) return null
                crc.update(buf, 0, n)
                if (total % (1024 * 1024) < n) check()
            }
            if (best == null || size > best.second) best = crc.value to size
        }
        return best?.takeIf { it.second > 0 }
    }
}

/**
 * Streams the ROM (a SAF document or a file ref, [AndroidRefs]) through CRC32; null when unreadable or over
 * [ScreenScraperSource.HASH_LIMIT]. A zip's ROM is unpacked on the fly ([ZipRomCrc]), never written anywhere.
 */
class AndroidRomHasher(private val refs: AndroidRefs) : RomHasher {
    override suspend fun stamp(rom: ContentRef): RomStamp? = withContext(Dispatchers.IO) {
        val size = refs.size(rom) ?: return@withContext null
        val modified = refs.lastModified(rom) ?: return@withContext null
        RomStamp(size, modified)
    }

    override suspend fun crc32AndSize(rom: ContentRef): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val declared = refs.size(rom)
        if (declared != null && declared > ScreenScraperSource.HASH_LIMIT) return@withContext null
        readOrNull(rom) {
            val crc = CRC32(); val buf = ByteArray(64 * 1024); var total = 0L
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > ScreenScraperSource.HASH_LIMIT) return@readOrNull null
                crc.update(buf, 0, n)
                if (total % (1024 * 1024) < n) ensureActive()   // about once per MB
            }
            crc.value to total
        }
    }

    override suspend fun crc32AndSizeInZip(zip: ContentRef): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val declared = refs.size(zip)
        if (declared != null && declared > ScreenScraperSource.HASH_LIMIT) return@withContext null
        readOrNull(zip) { ZipRomCrc.read(it, ScreenScraperSource.HASH_LIMIT) { ensureActive() } }
    }

    private inline fun readOrNull(rom: ContentRef, block: (InputStream) -> Pair<Long, Long>?): Pair<Long, Long>? = try {
        refs.openInput(rom)?.use(block)
    } catch (e: java.io.IOException) {   // a ZipException (not a zip, a broken one) included
        null
    } catch (e: SecurityException) {   // the grant was revoked
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
