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
            sizeBytes = runCatching { source.child(game.folder, game.fileName)?.sizeBytes }.getOrNull(),
            cheats = runCatching { hasCheats(tag, game.fileName) }.getOrDefault(false),
            hiresPack = tag.trim().uppercase() == "N64" && runCatching { hasHiresPack(entry) }.getOrDefault(false),
        )
    }

    private fun hasCheats(tag: String, romFileName: String): Boolean {
        if (CoreRegistry.forTag(tag)?.cheats == false) return false
        val root = CheatsRoot.of(app)
        val displayName = CheatMatch.displayName(romFileName)
        return CheatFolders.tagsFor(tag, romFileName).any { folder ->
            val files = File(root, "Cheats/$folder").listFiles()?.filter { it.isFile && it.name.endsWith(".cht", ignoreCase = true) }?.map { it.name }.orEmpty()
            CheatMatch.select(displayName, romFileName, files).isNotEmpty()
        }
    }

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
        runCatching {
            val needs = CoreRegistry.requiredBios(tag).filter { it.forExtensions.isEmpty() }
            if (needs.isEmpty()) return@runCatching null
            val home = homes.current() ?: return@runCatching null
            if (FileRefs.accessOff(home, AndroidAllFilesAccess.isGranted())) return@runCatching null   // cannot tell: not "missing"
            val bios = source.child(home, "Bios")?.takeIf { it.isDirectory } ?: return@runCatching BiosMissing.REQUIRED
            val present = BiosFolders.forTag(tag).flatMap { folder ->
                val dir = source.child(bios.ref, folder)?.takeIf { it.isDirectory } ?: return@flatMap emptyList()
                source.listChildren(dir.ref).filter { !it.isDirectory }.map { it.name }
            }.toSet()
            if (BiosCheck.missing(needs, "", present).isNotEmpty()) BiosMissing.REQUIRED else null
        }.getOrNull()
    }
}
