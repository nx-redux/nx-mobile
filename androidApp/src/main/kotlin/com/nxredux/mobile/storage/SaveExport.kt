package com.nxredux.mobile.storage

import com.nxredux.mobile.runner.CoreRegistry
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.attribute.FileTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Settings → Library → Saves → Export saves: the [SaveArchive] areas of the data root ([root], [DataRoot.of]) as
 * one zip, entry names from the card root and entry times from the files, so it unzips onto a handheld card as is.
 * Files are copied byte for byte. [out] is finished but left open (the caller owns the document). No logging here:
 * JVM-tested. Files whose entry names meet (`Saves/GBA/x` and a leftover `Saves/MGBA/x`, both leaving as
 * `Saves/MGBA/x`) export once: the newest, the console-tag folder's on a tie; the rest are left out. Files of two
 * consoles that would meet keep their console folders instead ([entryNames]).
 */
object SaveExport {
    /** In the name of a file the data move kept aside (`<name>.bak-<time>`, [DataRoot]): never exported. */
    private const val MOVE_BACKUP = ".bak-"

    fun write(root: File, out: OutputStream, exportTag: (String) -> String = { it }, onFile: (String) -> Unit = {}): ExportSummary {
        var saves = 0; var states = 0; var bytes = 0L
        val zip = ZipOutputStream(out)
        for (top in SaveArchive.ROOTS) {
            val dir = File(root, top).takeIf { it.isDirectory } ?: continue
            val files = dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") && MOVE_BACKUP !in it.name }.sortedBy { it.path }.toList()
            val named = files.map { f -> f to f.relativeTo(root).invariantSeparatorsPath }
                .filter { (_, path) -> SaveArchive.areaOf(path) != null }
            val entries = entryNames(named.map { it.second }, exportTag)
            val kept = named.groupBy { (_, path) -> entries.getValue(path) }.values.mapTo(HashSet()) { group ->
                group.minWith(compareByDescending<Pair<File, String>> { it.first.lastModified() }
                    .thenBy { (_, path) -> if (inConsoleFolder(path)) 0 else 1 }
                    .thenBy { it.second }).first
            }
            named.filter { it.first in kept }.forEach { (f, path) ->
                val area = SaveArchive.areaOf(path)!!
                zip.putNextEntry(ZipEntry(entries.getValue(path)).apply { lastModifiedTime = FileTime.fromMillis(f.lastModified()) })   // with the UTC extended time: no time-zone shift
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                bytes += f.length()
                when (area) { SaveArea.SAVES -> saves++; SaveArea.STATES -> states++; SaveArea.MARKERS -> Unit }
                onFile(path)
            }
        }
        zip.finish()
        return ExportSummary(saves, states, bytes)
    }

    /**
     * Each path's zip entry name: `Saves/<X>/rest` as `Saves/<exportTag(X)>/rest` (the console folder leaves as its default
     * emulator's handheld folder), except where files of two consoles would meet under one name (Genesis and Master System
     * both on Genesis Plus GX, both leaving as `Saves/GPGX/<same name>`): those keep their console folders
     * (`Saves/MD/…`, `Saves/SMS/…`), which the handheld's PicoDrive paks read and an import maps back as they are, so
     * neither is dropped. Files of one console that meet (a leftover emulator folder's copy) still export once ([write]).
     */
    internal fun entryNames(paths: List<String>, exportTag: (String) -> String): Map<String, String> {
        val mapped = paths.associateWith { entryName(it, exportTag) }
        val clashing = mapped.entries.groupBy({ it.value }, { it.key })
            .filterValues { group -> group.mapTo(HashSet()) { consoleOfPath(it) }.size > 1 }.values.flatten().toSet()
        return mapped.mapValues { (path, name) -> if (path in clashing) consoleFolderName(path) else name }
    }

    private fun entryName(path: String, exportTag: (String) -> String): String {
        val parts = path.split('/', limit = 3)
        return if (parts.size == 3 && parts[0] == "Saves") "Saves/${exportTag(parts[1])}/${parts[2]}" else path
    }

    /** A `Saves/` path's console tag (its folder's, [CoreRegistry.consoleTag]); any other path itself. */
    private fun consoleOfPath(path: String): String {
        val parts = path.split('/', limit = 3)
        return if (parts.size == 3 && parts[0] == "Saves") CoreRegistry.consoleTag(parts[1]) else path
    }

    private fun consoleFolderName(path: String): String {
        val parts = path.split('/', limit = 3)
        return if (parts.size == 3 && parts[0] == "Saves") "Saves/${CoreRegistry.consoleTag(parts[1])}/${parts[2]}" else path
    }

    /** True for a file in a console's own `Saves/<console tag>/` folder (not a leftover emulator-tag one). */
    private fun inConsoleFolder(path: String): Boolean {
        val parts = path.split('/', limit = 3)
        return parts.size == 3 && parts[0] == "Saves" && CoreRegistry.consoleOf(parts[1])?.defaultTag == parts[1]
    }

    /**
     * [write] into the file [target] (Export's save-as with All files access): through `<target>.part`,
     * renamed over [target] only when a save or state went in, so a failed or empty export leaves an existing [target]
     * as it was and no part behind. A [target] that is a folder fails (never deleted or nested into).
     */
    fun writeFile(root: File, target: File, exportTag: (String) -> String = { it }): ExportSummary {
        if (target.isDirectory) throw IOException("${target.name} is a folder")
        val part = File(target.parentFile, target.name + ".part")
        part.delete()
        try {
            val summary = part.outputStream().use { out -> write(root, out, exportTag).also { out.fd.sync() } }
            if (summary.saves == 0 && summary.states == 0) { part.delete(); return summary }
            if (!part.renameTo(target)) throw IOException("cannot write ${target.name}")
            return summary
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    fun suggestedName(nowMillis: Long): String =
        "nx-mobile-saves-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMillis))}.zip"
}
