package com.nxredux.mobile.storage

enum class SyncAction { NONE, IMPORT, EXPORT }

/**
 * The BIOS import's newer-wins rule ([decide]) and the `.part`/`.tmp` names sync skips ([isTransient]). Saves are not
 * imported this way: they are read and written in place under the data root.
 */
object MirrorRules {
    fun decide(localMtime: Long?, treeMtime: Long?): SyncAction = when {
        localMtime == null && treeMtime == null -> SyncAction.NONE
        localMtime == null -> SyncAction.IMPORT
        treeMtime == null -> SyncAction.EXPORT
        treeMtime > localMtime -> SyncAction.IMPORT
        localMtime > treeMtime -> SyncAction.EXPORT
        else -> SyncAction.NONE
    }

    /** The suffix of a tree document written before it replaces the file ([isTransient]). */
    const val PART = ".part"

    /**
     * An interrupted write's leftover, never synced: a local `.tmp` (atomic local writes) or a tree `.part`
     * (`<name>.part`, then `<name>` is replaced with it). A `.part` may be cut short, so importing one could put a truncated
     * file over a good one.
     */
    fun isTransient(name: String): Boolean = name.endsWith(".tmp") || name.endsWith(PART)
}

/** The `.bak` backup names a save write leaves beside the file it replaces. */
object SaveCopyRules {
    /** `<fileName>.<stamp>.bak` for [stamp], or `<stamp>-2`, `-3`… when that name [exists] already: a backup never replaces another. */
    fun freeBackupName(fileName: String, stamp: String, exists: (String) -> Boolean): String =
        generateSequence(1) { it + 1 }.map { n -> "$fileName.${if (n == 1) stamp else "$stamp-$n"}.bak" }.first { !exists(it) }

    private val BACKUP_SUFFIX = Regex("""\.\d{8}-\d{6}(-\d+)?\.bak""")

    /** True for [name] = a [freeBackupName] of [fileName] (`<fileName>.<yyyyMMdd-HHmmss>[-n].bak`). */
    fun isBackupOf(name: String, fileName: String): Boolean =
        name.startsWith(fileName) && BACKUP_SUFFIX.matches(name.substring(fileName.length))
}
