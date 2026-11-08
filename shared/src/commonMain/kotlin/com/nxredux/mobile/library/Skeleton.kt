package com.nxredux.mobile.library

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry

/**
 * The NX Redux card layout inside the home folder: one `Roms/` and one `Bios/` folder per console, named by its console
 * tag. `Saves/` is not part of it: it is created on first write under the data root, which is this home folder once
 * private data has moved there (the Android media-folder home creates its own `Saves/` too). Creates what is missing;
 * never renames, deletes or touches anything that exists.
 */
object Skeleton {
    fun folders(): List<String> = buildList {
        add("Roms")
        CoreRegistry.consoles.forEach { c -> add("Roms/${Names.systemFolderName(c, c.defaultTag)}") }
        add("Bios")
        CoreRegistry.consoles.forEach { c -> add("Bios/${c.defaultTag}") }
    }

    /**
     * True when every folder exists afterwards. Missing parents are created first because [folders] lists them first.
     * A `Roms/<Name (TAG)>` folder is skipped when `Roms/` already holds any folder with that tag (a home made before a
     * console rename keeps e.g. `Famicom (FC)`; `Nintendo ES (FC)` is not added beside it). `Roms/` is listed at most once,
     * and only when a console folder is missing.
     */
    suspend fun ensure(source: RomsSource, home: ContentRef): Boolean {
        val refs = HashMap<String, ContentRef>().apply { put("", home) }
        var romsTags: Set<String>? = null
        var ok = true
        for (path in folders()) {
            val parentPath = path.substringBeforeLast('/', "")
            val name = path.substringAfterLast('/')
            val parent = refs[parentPath]
            if (parent == null) { ok = false; continue }
            val existing = source.child(parent, name)
            if (existing == null && parentPath == "Roms") {
                val tags = romsTags ?: source.listChildren(parent)
                    .filter { it.isDirectory }
                    .mapNotNull { Names.parseSystemFolder(it.name)?.second?.uppercase() }
                    .toSet()
                    .also { romsTags = it }
                if (Names.parseSystemFolder(name)?.second?.uppercase() in tags) continue
            }
            val entry = when {
                existing == null -> source.createDirectory(parent, name)
                existing.isDirectory -> existing
                else -> null   // a file is in the way
            }
            if (entry == null) ok = false else refs[path] = entry.ref
        }
        return ok
    }
}
