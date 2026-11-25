package com.nxredux.mobile.storage

import com.nxredux.mobile.options.ConfigFile

/** A tag's console `minarch.cfg` and a game's `<rom>.cfg` for the out-of-game settings screens. */
interface EmulatorConfigStore {
    /** (console, game); game is null when [romFileName] is null or the file is absent. Throws on an unreadable existing file. */
    suspend fun read(tag: String, coreId: String, romFileName: String?): Pair<ConfigFile?, ConfigFile?>
    suspend fun writeConsole(tag: String, coreId: String, text: String)
    suspend fun writeGame(tag: String, coreId: String, romFileName: String, text: String)
    suspend fun deleteGame(tag: String, coreId: String, romFileName: String)
    /** Drops the tag's console file, so its page and its games without their own file read the defaults again. */
    suspend fun deleteConsole(tag: String, coreId: String)
}

object NoEmulatorConfigs : EmulatorConfigStore {
    override suspend fun read(tag: String, coreId: String, romFileName: String?): Pair<ConfigFile?, ConfigFile?> = null to null
    override suspend fun writeConsole(tag: String, coreId: String, text: String) {}
    override suspend fun writeGame(tag: String, coreId: String, romFileName: String, text: String) {}
    override suspend fun deleteGame(tag: String, coreId: String, romFileName: String) {}
    override suspend fun deleteConsole(tag: String, coreId: String) {}
}
