package com.nxredux.mobile.runner

import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.FrontendOptions
import java.io.File
import java.io.IOException

/** The console-wide and per-game minarch config files for one game. Blocking I/O: call off the main thread. */
class CoreConfigStore(private val console: File, private val game: File) {

    /** (console, game); null when a file doesn't exist or can't be read. */
    fun read(): Pair<ConfigFile?, ConfigFile?> = readFile(console) to readFile(game)

    /** Writes the console file, also removing the lines whose key [drop] names; returns what was written. */
    fun saveConsole(values: List<Pair<String, String>>, drop: (String) -> Boolean = { false }): ConfigFile = write(console, values, drop)
    /** Writes the game file, also removing the lines whose key [drop] names; returns what was written. */
    fun saveGame(values: List<Pair<String, String>>, drop: (String) -> Boolean = { false }): ConfigFile = write(game, values, drop)
    /** Sets one key in the game file ([CoreConfig.withKey]: a new game file starts as the console file); returns what was written. */
    fun saveGameKey(key: String, value: String): ConfigFile {
        val text = CoreConfig.withKey(readFileStrict(console), readFileStrict(game), key, value)
        game.writeAtomically(text.encodeToByteArray())
        return CoreConfig.parse(text)
    }
    /**
     * Sets one key for the console: in the console file, and in the game file too when the game has one (it is read
     * instead of the console file whole, so it would hide the change). Returns the file now read: the game's, else the console's.
     */
    fun saveConsoleKey(key: String, value: String): ConfigFile {
        val consoleText = CoreConfig.withKey(readFileStrict(console), null, key, value)
        console.writeAtomically(consoleText.encodeToByteArray())
        val gameFile = readFileStrict(game) ?: return CoreConfig.parse(consoleText)
        val gameText = CoreConfig.withKey(null, gameFile, key, value)
        game.writeAtomically(gameText.encodeToByteArray())
        return CoreConfig.parse(gameText)
    }
    fun deleteGame() = delete(game)
    fun deleteConsole() = delete(console)

    private fun write(file: File, values: List<Pair<String, String>>, drop: (String) -> Boolean): ConfigFile {
        val text = CoreConfig.render(readFileStrict(file), values, FrontendOptions::migrateKey, drop = { FrontendOptions.droppedOnSave(it) || drop(it) })
        file.writeAtomically(text.encodeToByteArray())
        return CoreConfig.parse(text)
    }

    private fun delete(file: File) = deleteConfig(file)

    private fun readFile(file: File): ConfigFile? =
        runCatching { file.takeIf { it.exists() }?.readText()?.let(CoreConfig::parse) }.getOrNull()
}

/**
 * Null only when [file] doesn't exist; a read or parse error on an existing file throws, so a config we
 * couldn't read (with its binds, locks and unknown keys) is never overwritten.
 */
internal fun readFileStrict(file: File): ConfigFile? {
    if (!file.exists()) return null
    return try {
        CoreConfig.parse(file.readText())
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException("unreadable config: ${file.path}", e)
    }
}

/** Removes [file] if present; a failed delete throws. */
internal fun deleteConfig(file: File) {
    if (file.exists() && !file.delete()) throw IOException("delete failed: ${file.path}")
}
