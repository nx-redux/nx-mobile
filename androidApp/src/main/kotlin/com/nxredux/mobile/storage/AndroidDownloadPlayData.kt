package com.nxredux.mobile.storage

import android.content.Context
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.DownloadPlayData
import java.io.File

/**
 * Where the kept Download Play games are: DS's in melonDS DS's private system folder (the core keeps one per game), 3DS's
 * as installed titles in Azahar's NAND under the saves home (title type 00040001 is only ever a Download Play child) with
 * the client's list of them. Azahar's own key (sysdata) is the phone's and stays.
 */
class AndroidDownloadPlayData(private val filesDir: File, private val dataRoot: () -> File) : DownloadPlayData {
    constructor(context: Context) : this(context.applicationContext.filesDir, { DataRoot.of(context.applicationContext) })

    private fun dirs(): List<File> {
        val azahar = File(dataRoot(), "Saves/${CoreRegistry.consoleTag("3DS")}/Azahar")
        return listOf(
            File(filesDir, "system/melonDS DS/Download Play"),
            File(azahar, "nand/00000000000000000000000000000000/title/00040001"),
            File(azahar, "sdmc/nx-redux/children"),
        )
    }

    override fun sizeBytes(): Long = dirs().sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    override fun clear(): Long {
        val dirs = dirs()
        val size = dirs.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        dirs.forEach { it.deleteRecursively() }
        return size
    }
}
