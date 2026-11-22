package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.storage.DataRoot
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.PathLines
import com.nxredux.mobile.storage.Recent
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.storage.Shortcut
import com.nxredux.mobile.storage.Shortcuts
import com.nxredux.mobile.storage.UserDataStore
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * nx-redux user data under the data root (recent.txt, shortcuts.txt, hidden.txt, `.minui` slot markers and screenshots):
 * [DataRoot.of], read per call (on the IO dispatcher), so a move or a home change is followed.
 */
class AndroidUserDataStore(context: Context) : UserDataStore {
    private val app = context.applicationContext
    private val root: File get() = DataRoot.of(app)

    override suspend fun readRecents(): List<Recent> = readList(NxPaths.RECENTS, Recents::parse)

    override suspend fun writeRecents(list: List<Recent>) {
        writeText(NxPaths.RECENTS, "recents") { Recents.render(list) }
    }

    override suspend fun readPins(): List<Shortcut> = readList(NxPaths.SHORTCUTS, Shortcuts::parse)

    override suspend fun writePins(list: List<Shortcut>): Boolean = writeText(NxPaths.SHORTCUTS, "pins") { Shortcuts.render(list) }

    override suspend fun readHidden(): List<String> = readList(NxPaths.HIDDEN, PathLines::parse)

    override suspend fun writeHidden(list: List<String>): Boolean = writeText(NxPaths.HIDDEN, "hidden") { PathLines.render(list) }

    private suspend fun <T> readList(path: String, parse: (String) -> List<T>): List<T> = withContext(Dispatchers.IO) {
        runCatching { File(root, path).takeIf { it.exists() }?.readText()?.let(parse) }.getOrNull() ?: emptyList()
    }

    private suspend fun writeText(path: String, what: String, render: () -> String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(root, path).writeAtomically(render().encodeToByteArray()); true }
            .onFailure { NxLog.e("UserData", "writing $what failed", it) }.getOrDefault(false)
    }

    override suspend fun resumeInfo(tag: String, fileName: String): ResumeInfo = withContext(Dispatchers.IO) {
        // The .minui paths depend only on tag + ROM file name; the core id is not part of them.
        val paths = NxPaths(tag, "", fileName)
        val root = this@AndroidUserDataStore.root   // one root for the marker and its screenshot
        val slotText = runCatching { File(root, paths.lastSlot).takeIf { it.exists() }?.readText() }.getOrNull()
        val slot = ResumeInfo.slotFrom(slotText) ?: return@withContext ResumeInfo.NONE
        // NxPaths.thumb accepts 0..7 and 9; slot 8 has no screenshot in minarch's scheme.
        val bmp = slot.takeIf { it != 8 }?.let { File(root, paths.thumb(it)) }?.takeIf { it.exists() }
        ResumeInfo(slot, bmp?.let { FileRefs.versioned(it.absolutePath, it.contentStamp()) }, bmp?.lastModified())
    }

    override suspend fun hiddenStateShot(tag: String, fileName: String): String? = withContext(Dispatchers.IO) {
        val bmp = File(root, NxPaths(tag, "", fileName).thumb(NxPaths.AUTO_SLOT)).takeIf { it.exists() }
        bmp?.let { FileRefs.versioned(it.absolutePath, it.contentStamp()) }
    }
}
