package com.nxredux.mobile.ui

/** What a game's file and its console's setup say, for the top screen's details page. */
data class FileFacts(val sizeBytes: Long?, val cheats: Boolean, val hiresPack: Boolean)

/** [REQUIRED]: the console cannot start without it; [OPTIONAL]: only an optional BIOS file is missing. */
enum class BiosMissing { REQUIRED, OPTIONAL }

/** The platform's reads for the details page; never throws, a failed read is a null / false answer. */
interface TopFactsPlatform {
    suspend fun fileFacts(entry: Listed): FileFacts
    /** Null when nothing is missing (or it cannot be told). */
    suspend fun biosMissing(tag: String): BiosMissing?
}

// Any No-Intro parenthesised word that is a region; checked in order of appearance.
private val REGIONS = setOf(
    "USA", "Europe", "Japan", "World", "Korea", "Asia", "Australia", "Brazil", "Canada", "China", "France", "Germany",
    "Hong Kong", "Italy", "Netherlands", "Spain", "Sweden", "Taiwan", "UK", "Russia", "Poland", "Portugal", "Norway",
    "Denmark", "Finland", "Greece", "Scandinavia", "Latin America", "Mexico", "New Zealand", "South Africa", "India",
)

/** The first No-Intro region tag in [fileName]'s parentheses ("Zelda (USA).gb" -> "USA"); null without one. */
fun regionOf(fileName: String): String? {
    var from = 0
    while (true) {
        val open = fileName.indexOf('(', from).takeIf { it >= 0 } ?: return null
        val close = fileName.indexOf(')', open).takeIf { it >= 0 } ?: return null
        fileName.substring(open + 1, close).split(',').map { it.trim() }.firstOrNull { it in REGIONS }?.let { return it }
        from = close + 1
    }
}

/** The file's extension in capitals ("a.chd" -> "CHD"); empty without one. */
fun formatOf(fileName: String): String = fileName.substringAfterLast('.', "").uppercase()
