package com.nxredux.mobile

import android.content.Context
import com.nxredux.mobile.cheats.CheatFolders
import com.nxredux.mobile.cheats.CheatMatch
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.storage.AndroidAllFilesAccess
import com.nxredux.mobile.runner.BiosCheck
import com.nxredux.mobile.runner.BiosFolders
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.N64HiresPack
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.tools.CheatsRoot
import com.nxredux.mobile.ui.BiosMissing
import com.nxredux.mobile.ui.FileFacts
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.TopFactsPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The details page's file facts, read the way a launch finds the same things: the game's size from its folder listing,
 * cheats by [CheatMatch.select] over the console's `Cheats/<TAG>/`, an N64 pack by its ROM header name in the home's
 * `Textures/N64/` or `.cache/` beside the game, and the BIOS from the home's `Bios/` folders. Every read is quiet on failure.
 */
class AndroidTopFacts(context: Context, private val source: RomsSource) : TopFactsPlatform {
    private val app = context.applicationContext
    private val homes = AndroidHomeLocator(app)

    override suspend fun fileFacts(entry: Listed): FileFacts = withContext(Dispatchers.IO) {
        val game = entry.game
        val tag = entry.system.tag
        FileFacts(
            sizeBytes = tryOrNull { source.child(game.folder, game.fileName)?.sizeBytes },
            cheats = tryOr({ false }) { hasCheats(tag, game.fileName) },
            hiresPack = tag.trim().uppercase() == "N64" && tryOr({ false }) { hasHiresPack(entry) },
        )
    }

    // A console's games share their cheat folders: each is listed once, then matched by name only.
    override suspend fun cheatGames(entries: List<Listed>): Int = withContext(Dispatchers.IO) {
        tryOr({ 0 }) {
            val root = CheatsRoot.of(app)
            val listed = HashMap<String, List<String>>()
            entries.count { e -> hasCheats(e.system.tag, e.game.fileName, root) { folder -> listed.getOrPut(folder) { cheatFiles(root, folder) } } }
        }
    }

    private fun hasCheats(tag: String, romFileName: String, root: File = CheatsRoot.of(app), files: (String) -> List<String> = { cheatFiles(root, it) }): Boolean {
        if (CoreRegistry.forTag(tag)?.cheats == false) return false
        val displayName = CheatMatch.displayName(romFileName)
        return CheatFolders.tagsFor(tag, romFileName).any { folder -> CheatMatch.select(displayName, romFileName, files(folder)).isNotEmpty() }
    }

    private fun cheatFiles(root: File, folder: String): List<String> =
        File(root, "Cheats/$folder").listFiles()?.filter { it.isFile && it.name.endsWith(".cht", ignoreCase = true) }?.map { it.name }.orEmpty()

    private suspend fun hasHiresPack(entry: Listed): Boolean {
        val game = entry.game
        val head = source.readHead(game.ref, N64HiresPack.HEADER_BYTES) ?: return false
        val fileName = N64HiresPack.fileName(N64HiresPack.headerName(head) ?: return false)
        suspend fun under(root: ContentRef?, path: List<String>): Boolean {
            var dir = root ?: return false
            for (part in path) dir = source.child(dir, part)?.takeIf { it.isDirectory }?.ref ?: return false
            return source.child(dir, fileName)?.let { !it.isDirectory } == true
        }
        return under(homes.current(), N64HiresPack.HOME_PATH) || under(game.folder, listOf(N64HiresPack.FOLDER))
    }

    // Only the needs that apply to every game of the console: a need tied to disc extensions depends on the game, which
    // the page's console-level question does not name. No optional-BIOS list exists in the registry, so never OPTIONAL.
    override suspend fun biosMissing(tag: String): BiosMissing? = withContext(Dispatchers.IO) {
        tryOrNull {
            val needs = CoreRegistry.requiredBios(tag).filter { it.forExtensions.isEmpty() }
            if (needs.isEmpty()) return@tryOrNull null
            val home = homes.current() ?: return@tryOrNull null
            if (FileRefs.accessOff(home, AndroidAllFilesAccess.isGranted())) return@tryOrNull null   // cannot tell: not "missing"
            val bios = source.child(home, "Bios")?.takeIf { it.isDirectory } ?: return@tryOrNull BiosMissing.REQUIRED
            val present = BiosFolders.forTag(tag).flatMap { folder ->
                val dir = source.child(bios.ref, folder)?.takeIf { it.isDirectory } ?: return@flatMap emptyList()
                source.listChildren(dir.ref).filter { !it.isDirectory }.map { it.name }
            }.toSet()
            if (BiosCheck.missing(needs, "", present).isNotEmpty()) BiosMissing.REQUIRED else null
        }
    }
}
