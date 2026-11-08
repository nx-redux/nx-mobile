package com.nxredux.mobile.storage

import com.nxredux.mobile.sync.SyncAreas

/** Where a card path sits in an export or import ([SaveArchive.areaOf]). */
enum class SaveArea { SAVES, STATES, MARKERS }

/**
 * The saves an export writes and an import takes, as card-root-relative paths in the handheld's layout: `Saves/<TAG>/…`
 * (battery saves, clock files, the emulators' own folders), `.userdata/shared/<TAG>-<core>/…` (save states) and
 * `.userdata/shared/.minui/<TAG>/…` (slot pictures and markers). Recents, shortcuts, game time, RetroAchievements data
 * and the platform folder `.userdata/android` are never part of it. The areas are the same as Device Sync's ([SyncAreas]).
 */
object SaveArchive {
    /** The folders an export (and a folder import) walks, under the card root. */
    val ROOTS = listOf("Saves", ".userdata/shared")

    private val DRIVE = Regex("^[A-Za-z]:")

    fun areaOf(path: String): SaveArea? {
        val s = path.split('/')
        if (s.any { it.isEmpty() }) return null
        return when {
            s.size >= 3 && s[0] == "Saves" -> if (SyncAreas.isJunk(path)) null else SaveArea.SAVES
            s.size >= 4 && s[0] == ".userdata" && s[1] == "shared" && SyncAreas.inShared(s.drop(2).joinToString("/")) ->
                if (s[2] == ".minui") SaveArea.MARKERS else SaveArea.STATES
            else -> null
        }
    }

    /** A zip entry name as a card-root-relative path (`\` read as `/`), or null when it must be refused: absolute, a drive, `.`/`..` or empty segments. */
    fun safePath(entryName: String): String? {
        val p = entryName.replace('\\', '/')
        if (p.startsWith("/") || DRIVE.containsMatchIn(p)) return null
        val s = p.split('/')
        if (s.any { it.isEmpty() || it == "." || it == ".." }) return null
        return p
    }
}

enum class ImportAction { NEW, IDENTICAL, REPLACE, SKIP_OLDER }

/** What one imported file does to the private copy. */
object ImportRules {
    /** A zip keeps modified times to 2 s, so a newer file must be newer by more than that. */
    const val TIME_SLACK_MS = 2_000L

    /** [incomingMtime] null: the source has no modified time, so it is never taken as newer. */
    fun decide(existingMtime: Long?, incomingMtime: Long?, sameContent: () -> Boolean): ImportAction = when {
        existingMtime == null -> ImportAction.NEW
        sameContent() -> ImportAction.IDENTICAL
        incomingMtime != null && incomingMtime > existingMtime + TIME_SLACK_MS -> ImportAction.REPLACE
        else -> ImportAction.SKIP_OLDER
    }
}

data class ExportSummary(val saves: Int, val states: Int, val bytes: Long) {
    fun text(): String = if (saves == 0 && states == 0) "Nothing to export"
        else "Exported ${count(saves, "save")} and ${count(states, "state")} (${megabytes(bytes)})"
}

/** Why an import left a file out; `%s` in [text] is filled from [SkippedFile.detail]. */
enum class SkipReason(val text: String) {
    NO_GAME("no matching game"),
    /** Filled with the consoles, joined by " and ". */
    AMBIGUOUS("matches %s"),
    PHONE_NEWER("phone copy is newer"),
    NO_FREE_SLOT("no free slot"),
    NO_CORE("can't tell the core"),
    UNREADABLE("could not be read"),
}

/** One file an import left out: its file [name], the [reason] and what fills the reason's `%s`. */
data class SkippedFile(val name: String, val reason: SkipReason, val detail: String? = null) {
    /** The reason with [detail] filled in, as the results page shows it under [name]. */
    fun reasonText(): String = reason.text.replace("%s", detail.orEmpty())

    fun text(): String = "$name: ${reasonText()}"
}

data class ImportSummary(
    val saves: Int = 0, val states: Int = 0, val markers: Int = 0, val identical: Int = 0, val replaced: Int = 0,
    val ignored: Int = 0, val skipped: List<SkippedFile> = emptyList(),
    /** The emulators (core names, such as mGBA) the imported states are for: [statesNote] names them. */
    val stateEmulators: Set<String> = emptySet(),
) {
    /** Nothing found at all: no file imported, already here, ignored or skipped. */
    val isEmpty: Boolean get() = saves + states + markers + identical + replaced + ignored == 0 && skipped.isEmpty()

    /** The results page's summary lines, or just "Nothing to import". */
    fun lines(): List<String> {
        if (isEmpty) return listOf("Nothing to import")
        return buildList {
            add("Imported ${count(saves, "save")} and ${count(states, "state")}")
            if (identical > 0) add("$identical already here")
            if (replaced > 0) add("$replaced replaced (kept as .bak)")
            if (skipped.isNotEmpty()) add("${skipped.size} skipped")
            if (ignored > 0) add("$ignored other ${plural(ignored, "file")} ignored")
        }
    }

    /**
     * The results page's caption when states came in (empty otherwise): they show only under the emulator that made
     * them ([stateEmulators]), and one made with another BIOS (RetroArch often runs without a BIOS file) may still refuse.
     */
    fun statesNote(): List<String> {
        if (states == 0) return emptyList()
        val names = stateEmulators.sortedBy { it.lowercase() }
        val which = when (names.size) {
            0 -> "Save states load only in the emulator that made them: switch each game to its emulator before you launch it"
            1 -> "Save states load only in the emulator that made them. These are for ${names[0]}: switch the game to ${names[0]} before you launch it"
            else -> "Save states load only in the emulator that made them. These are for ${names.dropLast(1).joinToString(", ")} and ${names.last()}: switch each game to its emulator before you launch it"
        }
        return listOf(
            "$which, in Default emulators or the game's Emulator option.",
            "If a state still won't load, its BIOS may differ. RetroArch often makes states without a BIOS file: if you've " +
                "added one to this app, turn off the emulator's real-BIOS option, such as mGBA's Use BIOS File if Found. " +
                "Otherwise, use the battery save.",
        )
    }

    /** Both imports' counts added up, their skipped files one after the other (RetroArch reads several roots). */
    operator fun plus(other: ImportSummary) = ImportSummary(
        saves + other.saves, states + other.states, markers + other.markers, identical + other.identical,
        replaced + other.replaced, ignored + other.ignored, skipped + other.skipped, stateEmulators + other.stateEmulators,
    )
}

private fun plural(n: Int, word: String) = if (n == 1) word else word + "s"
private fun count(n: Int, word: String) = "$n ${plural(n, word)}"
private fun megabytes(bytes: Long): String {
    val tenths = (bytes * 10 + 524_288) / 1_048_576
    return "${tenths / 10}.${tenths % 10} MB"
}
