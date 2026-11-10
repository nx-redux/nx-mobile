package com.nxredux.mobile

import android.content.Context
import com.nxredux.mobile.library.RomsSource
import com.nxredux.mobile.storage.AndroidAllFilesAccess
import com.nxredux.mobile.runner.BiosCheck
import com.nxredux.mobile.runner.BiosFolders
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.AndroidHomeLocator
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.ui.BiosMissing
import com.nxredux.mobile.ui.TopFactsPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The stats page's BIOS check, read from the home's `Bios/` folders as a launch finds them; quiet on failure. */
class AndroidTopFacts(context: Context, private val source: RomsSource) : TopFactsPlatform {
    private val app = context.applicationContext
    private val homes = AndroidHomeLocator(app)

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
