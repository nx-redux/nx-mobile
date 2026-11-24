package com.nxredux.mobile.library

import com.nxredux.mobile.runner.Console
import com.nxredux.mobile.runner.CoreRegistry

/**
 * Which tag a loose file runs as. Candidates in order: the sidecar tag, the nearest `(TAG)` ancestor (as its
 * console's default tag), the nearest ancestor whose name contains a console alias (as the console's default tag),
 * then the one console owning the extension (a zip by its first entry's extension; a 7z has no cheap peek, so it is
 * placed only by the earlier steps). A candidate tag takes any archive (it is unpacked at launch, where the inner file
 * is checked against the core). A candidate whose core cannot run a bare file's extension falls back to the first tag
 * of the same console whose core can, else is skipped, so a `.gba` inside "Game Boy (GB)" still resolves to GBA. A
 * console with no core ([Console.externalOnly]) takes what its folder lists ([Console.folderTakes]). Null means unassigned.
 */
object Resolution {
    fun isCandidate(fileName: String): Boolean {
        val ext = Names.extensionOf(fileName)
        if (ext.isEmpty()) return false
        return ext in Names.ARCHIVE_EXTENSIONS || ext in CoreRegistry.GENERIC_EXTENSIONS ||
            CoreRegistry.consoles.any { ext in it.extensions || ext in it.folderExtensions }
    }

    /**
     * What a folder's [ancestors] (nearest first) say about its files, worked out once per folder: per `(TAG)` ancestor
     * its console's default tag (a folder's tag names only its console, [CoreRegistry.consoleOfFolder]; a tag of no
     * console as it is), then the default tags of the consoles its other ancestors are named after, in the order they
     * are tried.
     */
    class FolderHints(val tags: List<String>) {
        companion object {
            fun of(ancestors: List<String>, defaultTag: (Console) -> String): FolderHints = FolderHints(
                (ancestors.mapNotNull { a -> Names.parseSystemFolder(a)?.let { (d, t) -> CoreRegistry.consoleOfFolder(d, t)?.let(defaultTag) ?: t } } +
                    ancestors.mapNotNull { CoreRegistry.consoleForFolderName(it)?.let(defaultTag) }).distinct(),
            )
        }
    }

    fun resolve(fileName: String, sidecarTag: String?, ancestors: List<String>, zipInner: String?, defaultTag: (Console) -> String): String? =
        resolve(fileName, sidecarTag, FolderHints.of(ancestors, defaultTag), defaultTag) { zipInner }

    /** As above with [folder]'s hints; [zipInner] (the zip's first entry name) is asked only when the folder and sidecar place nothing. */
    inline fun resolve(fileName: String, sidecarTag: String?, folder: FolderHints, noinline defaultTag: (Console) -> String, zipInner: () -> String?): String? =
        byFolder(fileName, sidecarTag, folder)
            ?: byExtension(fileName, if (Names.extensionOf(fileName) == "zip") zipInner() else null, defaultTag)

    /**
     * The sidecar tag when it is an emulator of the folder's console (its first hint's; [CoreRegistry.tagOn]: GPGX in a
     * Master System folder is GPGX_SMS) or the folder names no console, else the first of [folder]'s tags that takes the
     * file. Another console's sidecar tag counts only when none of them does (an Unassigned game the Console menu placed).
     */
    fun byFolder(fileName: String, sidecarTag: String?, folder: FolderHints): String? {
        val ext = Names.extensionOf(fileName)
        val archive = ext in Names.ARCHIVE_EXTENSIONS
        val console = folder.tags.firstNotNullOfOrNull { CoreRegistry.consoleOf(it) }
        val own = sidecarTag?.let { s -> if (console == null) s else CoreRegistry.tagOn(console, s) }
        own?.let { accepts(it, ext, archive) }?.let { return it }
        folder.tags.firstNotNullOfOrNull { accepts(it, ext, archive) }?.let { return it }
        return if (own == null) sidecarTag?.let { accepts(it, ext, archive) } else null
    }

    /** The one console owning the extension (a zip's by [zipInner]; a 7z has none), as its default tag. */
    fun byExtension(fileName: String, zipInner: String?, defaultTag: (Console) -> String): String? {
        val ext = Names.extensionOf(fileName)
        val archive = ext in Names.ARCHIVE_EXTENSIONS
        val inferred = when {
            ext == "zip" -> zipInner?.let(Names::extensionOf)
            archive -> null   // 7z: the entry table sits inside the compressed header; a head read cannot name it
            else -> ext
        }
        val console = inferred?.let { CoreRegistry.consolesForExtension(it).singleOrNull() } ?: return null
        return accepts(defaultTag(console), ext, archive)
    }

    /** Console [consoleId] (a disc probe's answer) as its default tag, when one of its cores takes [fileName]; else null. */
    fun acceptsConsole(consoleId: String, fileName: String, defaultTag: (Console) -> String): String? {
        val console = CoreRegistry.consoles.firstOrNull { it.id == consoleId } ?: return null
        val ext = Names.extensionOf(fileName)
        return accepts(defaultTag(console), ext, ext in Names.ARCHIVE_EXTENSIONS)
    }

    private fun accepts(tag: String, ext: String, archive: Boolean): String? {
        val core = CoreRegistry.forTag(tag)
            ?: return CoreRegistry.consoleOf(tag)?.takeIf { it.externalOnly && it.folderTakes(ext, archive) }?.let { tag.uppercase() }
        if (archive || ext in core.extensions) return tag.uppercase()
        // The tag's core cannot run the file: the first tag of the same console whose core can, as the home scan does.
        return CoreRegistry.consoleOf(tag)?.tags?.firstOrNull { t -> CoreRegistry.forTag(t)?.let { ext in it.extensions } == true }
    }
}
