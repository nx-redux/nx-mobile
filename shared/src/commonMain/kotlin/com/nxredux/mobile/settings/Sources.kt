package com.nxredux.mobile.settings

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.percentDecode

object Sources {
    private const val EXTERNAL_TREE = "content://com.android.externalstorage.documents/tree/"

    /** A volume id the external-storage provider names by uuid ("1234-ABCD"); "home" and other root ids have no path. */
    private val VOLUME_UUID = Regex("[0-9A-Fa-f]+(-[0-9A-Fa-f]+)+")

    /**
     * What [overlaps] compares: a file ref's path, an external-storage tree's [mappedPath] (so a SAF tree and a file ref on
     * the same folder compare equal), else the ref with its path-id part decoded ("primary%3ANXRedux%2FRoms" as
     * "primary:NXRedux/Roms").
     */
    private fun path(ref: ContentRef): String =
        if (FileRefs.isFile(ref)) FileRefs.path(ref).trimEnd('/')
        else mappedPath(ref) ?: ref.value.replace("%3A", ":").replace("%2F", "/").trimEnd('/')

    /**
     * The file-system path of an external-storage tree ref: `primary:<p>` is `/storage/emulated/0/<p>`, a card's
     * `<UUID>:<p>` is `/storage/<UUID>/<p>`. A document URI maps by its own document id (a folder inside a tree: the
     * storage upgrade's extra on an old picker home's `Roms/`). Null for any other ref (another provider, Downloads, the
     * Documents root, a file ref).
     */
    fun mappedPath(ref: ContentRef): String? {
        val v = ref.value
        if (!v.startsWith(EXTERNAL_TREE)) return null
        val tail = v.substringAfter("/tree/")
        val id = percentDecode((if ("/document/" in tail) tail.substringAfter("/document/") else tail).trimEnd('/'))
        if (':' !in id) return null
        val volume = id.substringBefore(':')
        val base = when {
            volume == "primary" -> "/storage/emulated/0"
            VOLUME_UUID.matches(volume) -> "/storage/$volume"
            else -> return null
        }
        val rel = id.substringAfter(':').trim('/')
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    /**
     * A readable path: a file ref's own path; for an external-storage tree ref, as a file manager shows it: "primary:NXRedux"
     * is "/sdcard/NXRedux", a card's "1234-ABCD:Games/NXRedux" is "/storage/1234-ABCD/Games/NXRedux", a volume root just
     * the volume. Null for any other ref (another provider, not a tree URI): the caller falls back to the folder name.
     */
    fun displayPath(ref: ContentRef): String? {
        if (FileRefs.isFile(ref)) return FileRefs.path(ref)
        val v = ref.value
        if (!v.startsWith(EXTERNAL_TREE)) return null
        val id = percentDecode(v.substringAfter("/tree/").substringBefore("/document/").trimEnd('/'))
        if (':' !in id) return null
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':').trim('/')
        val base = if (volume == "primary") "/sdcard" else "/storage/$volume"
        return if (path.isEmpty()) base else "$base/$path"
    }

    /** [child] is [parent] itself or lies inside it. A volume root's id ends with ':' ("primary:"), so its children follow it with no '/'. */
    private fun inside(child: String, parent: String): Boolean =
        child == parent || child.startsWith(if (parent.endsWith(":")) parent else "$parent/")

    /** True when one tree is the other or lies inside it, compared by path ([path]); files would be listed twice. */
    fun overlaps(a: ContentRef, b: ContentRef): Boolean {
        val pa = path(a); val pb = path(b)
        return inside(pa, pb) || inside(pb, pa)
    }

    /** FNV-1a 32-bit over the UTF-8 bytes of [text], as 8 lowercase hex digits. */
    fun fnv1a32(text: String): String {
        var h = 0x811c9dc5.toInt()
        for (b in text.encodeToByteArray()) {
            h = h xor (b.toInt() and 0xff)
            h *= 0x01000193
        }
        return h.toUInt().toString(16).padStart(8, '0')
    }

    /**
     * An extra folder's id: its display name kept to `[A-Za-z0-9_-]` (others become `_`, runs collapsed, leading and
     * trailing `_`/`-` trimmed, at most 32 chars, "folder" when nothing is left), a dash, and the FNV-1a hash of the
     * tree address exactly as persisted. The same folder re-added on one device has the same tree URI, so the same id.
     */
    fun idFor(displayName: String, ref: ContentRef): String {
        val kept = buildString {
            for (c in displayName) append(if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-') c else '_')
        }
        val name = kept.replace(Regex("_+"), "_").trim('_', '-').take(32).ifEmpty { "folder" }
        return "$name-${fnv1a32(ref.value)}"
    }

    /** [base] when free, else the first free `base-2`, `base-3`, … */
    fun uniqueId(base: String, taken: Collection<String>): String {
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }
}
