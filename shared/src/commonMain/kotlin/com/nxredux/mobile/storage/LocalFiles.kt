package com.nxredux.mobile.storage

/** A mounted storage volume for the list-style picker: [name] as Android names it ("Internal storage"), [path] its root. */
data class LocalVolume(val name: String, val path: String, val primary: Boolean = false)   // primary: the phone's own storage

data class LocalEntry(val name: String, val isDir: Boolean)

/** Local folders by absolute path, for the list-style picker and the SAF → file conversion ([com.nxredux.mobile.settings.SourceConversion]). Never throws. */
interface LocalFiles {
    /** The mounted volumes, in the platform's order (internal storage first). */
    suspend fun volumes(): List<LocalVolume>
    /** [path]'s entries, dot entries included (the picker hides them); empty when unreadable. */
    suspend fun list(path: String): List<LocalEntry>
    /** Creates the folder [name] in [parent]; false when it exists, [name] is not one segment, or it cannot be made. */
    suspend fun mkdir(parent: String, name: String): Boolean
    suspend fun isDirectory(path: String): Boolean
    /** True when [path] is a folder that takes new files (the home pick's check). */
    suspend fun canWrite(path: String): Boolean
}
