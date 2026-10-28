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
            val bios = tree.child(home, "Bios")?.takeIf { it.isDirectory } ?: return
            val sync = TreeSync(tree)
            for (tag in tags) {
                val tagDir = tree.child(bios.ref, tag)?.takeIf { it.isDirectory } ?: continue
                runCatching { sync.importDir(tagDir.ref, biosDir, recursive = true, whenDiffers = true, extraNames = names) }
                    .onFailure { NxLog.w("BiosImport", "Bios/$tag import failed", it) }
            }
        }.onFailure { NxLog.w("BiosImport", "Bios import failed", it) }
    }

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
