package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.storage.AndroidAllFilesAccess
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.ImportTree
import com.nxredux.mobile.storage.MediaHome
import com.swordfish.libretrodroid.NxLog
import java.io.File

/**
 * `Bios/<TAG>/` in the home tree is what the user fills; cores read a real path, so its files are copied into the
 * app-private system dir (over the bundled assets, by path) before each launch, whenever size or last-modified differ
 * (a dump is usually older than the bundled file, and an app update re-extracts over it). A launch imports the folder of
 * every tag that shares its core ([BiosFolders]), as the Brick keeps `disksys.rom` in `Bios/FC/` and the Sega CD BIOS
 * in `Bios/MD/`; a later folder wins a name clash. When a folder will not list
 * ([com.nxredux.mobile.library.ListingUnavailable]), the local files and [names] (the BIOS files the launch needs;
 * single-segment names only) are looked up by name instead. [biosDir] is the system dir, or the core's subfolder of it
 * ([BiosFolders.subdir]: flycast's `<system>/dc/`, so the Brick's `Bios/DC/dc_boot.bin` lands where flycast reads it).
 * The home is a SAF tree or, with All files access, a file ref ([ImportTree.of]).
 */
object BiosImport {
    fun sync(context: Context, home: ContentRef?, tags: List<String>, biosDir: File, names: List<String> = emptyList()) {
        home ?: return
        val own = FileRefs.isFile(home) && MediaHome.owns(MediaHome.cachedDir(context), File(FileRefs.path(home)))   // the media home needs no access
        if (!own && FileRefs.accessOff(home, AndroidAllFilesAccess.isGranted())) return NxLog.w("BiosImport", "All files access is off; Bios import skipped")
        runCatching {
            val tree = ImportTree.of(context.contentResolver, home)
            // No Bios folder at all (the home unmounted, or not set up yet) says nothing about the copies: none is removed.
            val bios = tree.child(home, "Bios")?.takeIf { it.isDirectory } ?: return
            val sync = TreeSync(tree)
            val found = LinkedHashMap<String, Pair<Set<String>, Boolean>>()
            for (tag in tags) {
                val tagDir = tree.child(bios.ref, tag)?.takeIf { it.isDirectory }
                if (tagDir == null) { found[tag] = emptySet<String>() to true; continue }
                val f = TreeSync.Found()
                runCatching { sync.importDir(tagDir.ref, biosDir, recursive = true, whenDiffers = true, extraNames = names, found = f) }
                    .onFailure { f.complete = false; NxLog.w("BiosImport", "Bios/$tag import failed", it) }
                found[tag] = f.seen to f.complete
            }
            dropStaleCopies(context, biosDir, tags, names, found)
        }.onFailure { NxLog.w("BiosImport", "Bios import failed", it) }
    }

    /**
     * Removes the copies in [biosDir] whose originals are gone ([BiosCopies]), restoring a bundled file a copy had
     * replaced. For a folder with nothing on record yet, the launch's own BIOS [names] that are in [biosDir] and not
     * bundled count as its copies: only an import puts them there (copies made before the record existed).
     */
    private fun dropStaleCopies(context: Context, biosDir: File, tags: List<String>, names: List<String>, found: Map<String, Pair<Set<String>, Boolean>>) {
        val systemDir = File(context.filesDir, "system")
        val recordFile = File(File(context.filesDir, RECORD_DIR), biosDir.relativeTo(context.filesDir).path.replace('/', '_') + ".tsv")
        val recorded = if (recordFile.isFile) BiosCopies.parse(recordFile.readText()) else emptyMap()
        val seeded by lazy {
            names.filter { FileRefs.isPlainName(it) && File(biosDir, it).isFile && !SystemFiles.isBundled(context, systemDir, File(biosDir, it)) }.toSet()
        }
        val old = recorded + tags.filter { it !in recorded }.associateWith { seeded }.filterValues { it.isNotEmpty() }
        val (record, stale) = BiosCopies.update(old, found)
        for (path in stale) {
            val file = File(biosDir, path)
            if (!file.isFile) continue
            if (SystemFiles.restoreBundled(context, systemDir, file)) NxLog.i("BiosImport", "restored bundled $path: its Bios copy is gone")
            else if (file.delete()) NxLog.i("BiosImport", "removed $path: no Bios folder has it any more")
        }
        if (record != recorded) {
            recordFile.parentFile?.mkdirs()
            recordFile.writeAtomically(BiosCopies.format(record).encodeToByteArray())
        }
    }

    private const val RECORD_DIR = "bios-copies"

    /**
     * The names of the ".bin" files in `Bios/<tag>/` (the ones ARMSX2 accepts), sorted; empty without a home, without access
     * or when the folder will not list.
     */
    fun binFiles(context: Context, home: ContentRef?, tag: String): List<String> {
        home ?: return emptyList()
        return runCatching {
            val tree = ImportTree.of(context.contentResolver, home)
            val bios = tree.child(home, "Bios")?.takeIf { it.isDirectory } ?: return emptyList()
            val dir = tree.child(bios.ref, tag)?.takeIf { it.isDirectory } ?: return emptyList()
            tree.list(dir.ref).filter { !it.isDirectory && it.name.endsWith(".bin", ignoreCase = true) }.map { it.name }.sorted()
        }.getOrDefault(emptyList())
    }
}
