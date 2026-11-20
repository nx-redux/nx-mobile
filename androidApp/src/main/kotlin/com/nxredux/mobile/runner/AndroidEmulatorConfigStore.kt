package com.nxredux.mobile.runner

import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.storage.EmulatorConfigStore
import com.nxredux.mobile.storage.NxPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The out-of-game settings screens' view of the same `minarch.cfg` / `<rom>.cfg` files [CoreConfigStore] uses in game,
 * under [sdRoot] (the data root, [com.nxredux.mobile.storage.DataRoot.of], asked per call on the IO dispatcher). Reads
 * are strict (absent → null, unreadable → IOException); writes are atomic and create the config dir of a tag that has
 * never been launched.
 */
class AndroidEmulatorConfigStore(private val sdRoot: () -> File) : EmulatorConfigStore {
    private fun paths(tag: String, coreId: String, romFileName: String?) = NxPaths(tag, coreId, romFileName ?: "_")

    override suspend fun read(tag: String, coreId: String, romFileName: String?): Pair<ConfigFile?, ConfigFile?> =
        withContext(Dispatchers.IO) {
            val p = paths(tag, coreId, romFileName)
            val root = sdRoot()
            readFileStrict(File(root, p.consoleConfig)) to romFileName?.let { readFileStrict(File(root, p.gameConfig)) }
        }

    override suspend fun writeConsole(tag: String, coreId: String, text: String) = withContext(Dispatchers.IO) {
        File(sdRoot(), paths(tag, coreId, null).consoleConfig).writeAtomically(text.encodeToByteArray())
    }

    override suspend fun writeGame(tag: String, coreId: String, romFileName: String, text: String) = withContext(Dispatchers.IO) {
        File(sdRoot(), paths(tag, coreId, romFileName).gameConfig).writeAtomically(text.encodeToByteArray())
    }

    override suspend fun deleteGame(tag: String, coreId: String, romFileName: String) = withContext(Dispatchers.IO) {
        deleteConfig(File(sdRoot(), paths(tag, coreId, romFileName).gameConfig))
    }

    override suspend fun deleteConsole(tag: String, coreId: String) = withContext(Dispatchers.IO) {
        deleteConfig(File(sdRoot(), paths(tag, coreId, null).consoleConfig))
    }
}
