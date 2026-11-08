package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.storage.GamePath

/** A game's `.media` folder ([mediaSegments], home-relative) and the base name of its art files. */
data class ArtTarget(val mediaSegments: List<String>, val base: String) {
    fun segments(slot: ArtSlot): List<String> = mediaSegments + slot.folder
    fun name(e: ArtEncoding) = "$base.${e.ext}"
    /** The home-relative path of [slot]'s file in [e] (the written-art log's key). */
    fun path(slot: ArtSlot, e: ArtEncoding): String = (segments(slot) + name(e)).joinToString("/")
    /** The home-relative path of the 2D box's file in [e] in its older folder ([LEGACY_BOX2D_FOLDER]; Reset). */
    fun legacyBox2dPath(e: ArtEncoding): String = (mediaSegments + LEGACY_BOX2D_FOLDER + name(e)).joinToString("/")
    /** The legacy untyped mix's path `.media/<name>.png` (neither written nor read; Reset deletes it when logged). */
    val legacyMixPath: String get() = (mediaSegments + "$base.png").joinToString("/")
}

/** Where art for a library game goes (NX Redux `scraper_scan.c:50-61`; extras → the home mirror `Roms/.sources/<id>/`). */
object ArtPaths {
    fun target(sourceId: String, relativeDir: String, fileName: String, folderGame: Boolean): ArtTarget {
        val dirParts = relativeDir.split('/').filter { it.isNotEmpty() }
        require(!folderGame || dirParts.isNotEmpty()) { "a folder game needs its disc folder in relativeDir" }
        // A folder game's art is named by the whole folder name (no extension strip): the scanner looks it up that way.
        val (ownerDir, base) = if (folderGame) dirParts.dropLast(1) to dirParts.last() else dirParts to Names.stripExtension(fileName)
        val root = if (sourceId == GamePath.HOME) ownerDir else listOf("Roms", ".sources", sourceId) + ownerDir
        return ArtTarget(root + ".media", base)
    }
}
