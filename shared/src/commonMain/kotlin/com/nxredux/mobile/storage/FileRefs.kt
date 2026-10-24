package com.nxredux.mobile.storage

import com.nxredux.mobile.runner.ContentRef

/**
 * A folder or file read with All files access is `ContentRef("file://" + absolutePath)`, the path raw (never
 * percent-encoded: a ROM name may hold '#', '?' or '%', so a file ref is never parsed as a URI). A child is the parent's
 * value + "/" + name, so refs built by a listing and by [child] compare equal.
 */
object FileRefs {
    /** The error for a file home read while All files access is off (shown by the library, Import and the like). */
    const val ACCESS_OFF_MESSAGE = "All files access is off. Turn it on in Settings → Library → All files access, or pick the home folder again."

    /** The prefix of a file ref's value. */
    const val SCHEME = "file://"

    /** Where ROM folders live: art under it is read by path ([artPath]); app-private `file://` refs are not. */
    private const val SHARED_STORAGE = "/storage/"

    /** True for a folder or file ref read by path. App-private art `file://` models are art, not folder refs: they are never passed here. */
    fun isFile(ref: ContentRef): Boolean = ref.value.startsWith(SCHEME)

    /** True when [home] is a file ref and All files access is not [granted]: its folder cannot be read now. */
    fun accessOff(home: ContentRef?, granted: Boolean): Boolean = home != null && isFile(home) && !granted

    /** True when [ref] is the file ref [dir] or lies inside it (no `..` segment, which could climb out). */
    fun within(dir: ContentRef, ref: ContentRef): Boolean {
        if (!isFile(dir) || !isFile(ref)) return false
        val v = ref.value
        if (v.split('/').any { it == ".." }) return false
        return v == dir.value || v.startsWith(dir.value.trimEnd('/') + "/")
    }

    /** The absolute path of a file ref (the caller checked [isFile]). */
    fun path(ref: ContentRef): String = ref.value.removePrefix(SCHEME)

    /** The file ref of [path]; a trailing '/' is dropped so [child] never doubles it. */
    fun of(path: String): ContentRef = ContentRef(SCHEME + path.trimEnd('/').ifEmpty { "/" })

    /** The ref of [name] inside [ref] (a plain name; see [isPlainName]). */
    fun child(ref: ContentRef, name: String): ContentRef =
        ContentRef(if (ref.value.endsWith("/")) ref.value + name else ref.value + "/" + name)

    /** True when [name] is one path segment: not empty, no '/', not "." or ".." (anything else would address another file). */
    fun isPlainName(name: String): Boolean = name.isNotEmpty() && '/' !in name && name != "." && name != ".."

    /**
     * The path Coil should read for an image model that is a file ref on shared storage (a ROM folder's `.media`), or null
     * for any other model: an app-private `file://...?v=` ref keeps Coil's own handling, whose query is its cache key.
     */
    fun artPath(model: String): String? = model.takeIf { it.startsWith(SCHEME + SHARED_STORAGE) }?.removePrefix(SCHEME)
}
