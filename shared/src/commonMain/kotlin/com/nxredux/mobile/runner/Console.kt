package com.nxredux.mobile.runner

/**
 * A console groups the tags that run it. On the handheld a tag is "console + emulator" (`GBA` = gpsp,
 * `MGBA` = mgba), each with its own Saves/<TAG>/; here saves are one folder per console, Saves/<console tag>/
 * ([defaultTag], NxPaths.savesDir), and states stay per tag. [tags] are in preference order: the first is the built-in
 * default (Settings → Default emulators picks another). A folder's tag only names its console: a game runs its own
 * Emulator pick (`systems.txt`), else the console's Default emulators one, whichever of the console's folders holds it.
 * [aliases] are folder names that mean this console, lowercase. [extensions] are set explicitly: the extensions this
 * console's games use, which may be a subset of its cores' (never an archive extension, even for fbneo, whose zip/7z its
 * core loads as is ([CoreRegistry.loadsArchive]); empty for SGB and FBN, which are reached only by folder, sidecar or
 * menu). [rawSram]: the battery save is raw SRAM and interchangeable between the console's tags. [folderExtensions] are
 * extensions its folder takes that never name it alone (another console's too, or too common: PS3 and PS4 `pkg`).
 */
data class Console(
    val id: String,
    val displayName: String,
    val tags: List<String>,
    val aliases: Set<String>,
    val extensions: Set<String>,
    val rawSram: Boolean,
    val folderExtensions: Set<String> = emptySet(),
) {
    val defaultTag: String get() = tags.first()

    /** No tag has a bundled core: its games open only in other emulators. */
    val externalOnly: Boolean get() = tags.none { CoreRegistry.forTag(it) != null }

    /** An [externalOnly] console's folder lists a file of [ext]: an archive (the other app opens it), or one of its extensions. */
    fun folderTakes(ext: String, archive: Boolean): Boolean = archive || ext in extensions || ext in folderExtensions

    /** The tag files resolved by console use: [stored] (the user's Default emulators pick) when it is one of [tags], else [defaultTag]. */
    fun defaultTagFor(stored: String?): String = stored?.takeIf { it in tags } ?: defaultTag
}
