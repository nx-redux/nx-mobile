package com.nxredux.mobile.storage

/** The app's nx-redux user data (recents, pins, hidden games, save-slot markers), implemented per platform over the SD-card root. */
interface UserDataStore {
    suspend fun readRecents(): List<Recent>
    suspend fun writeRecents(list: List<Recent>)
    /** `shortcuts.txt`: the main-menu pins. */
    suspend fun readPins(): List<Shortcut>
    /** False when the file could not be written (a full disk). */
    suspend fun writePins(list: List<Shortcut>): Boolean
    /** `hidden.txt`: game paths hidden from every list. */
    suspend fun readHidden(): List<String>
    suspend fun writeHidden(list: List<String>): Boolean
    /** From `<rom.ext>.txt` / `<rom.ext>.<slot>.bmp` (PNG data; older files BMP) under `.userdata/shared/.minui/<tag>/`. */
    suspend fun resumeInfo(tag: String, fileName: String): ResumeInfo
    /** The hidden (auto) save state's frame, `<rom.ext>.9.bmp`, as a coil-loadable URI: what Save & Quit leaves; null when none. */
    suspend fun hiddenStateShot(tag: String, fileName: String): String? = null
}

/** No persistence (tests / previews). */
object NoUserData : UserDataStore {
    override suspend fun readRecents(): List<Recent> = emptyList()
    override suspend fun writeRecents(list: List<Recent>) = Unit
    override suspend fun readPins(): List<Shortcut> = emptyList()
    override suspend fun writePins(list: List<Shortcut>) = true
    override suspend fun readHidden(): List<String> = emptyList()
    override suspend fun writeHidden(list: List<String>) = true
    override suspend fun resumeInfo(tag: String, fileName: String) = ResumeInfo.NONE
}
