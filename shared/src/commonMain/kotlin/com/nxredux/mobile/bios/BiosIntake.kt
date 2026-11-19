package com.nxredux.mobile.bios

import com.nxredux.mobile.runner.CoreRegistry

/** Where Import BIOS reads from. */
enum class BiosSource { FOLDER, FILES, ZIP, RETROARCH }

/** How an Import BIOS ended: [Done] with its summary, or a [Message] (a refusal, "No BIOS found", "Failed: …"). */
sealed interface BiosIntakeResult {
    data class Done(val summary: BiosIntakeSummary) : BiosIntakeResult
    data class Message(val text: String) : BiosIntakeResult
}

/**
 * Settings → Library → Import BIOS on the platform: opens the picker [import]'s source needs, copies every known BIOS
 * into the home's `Bios/<console>/` in the background, then calls `progress` once when the work starts and `done` once.
 * A cancelled picker reports nothing. Copy only: the source is never changed.
 */
interface BiosIntake {
    val running: Boolean
    fun import(source: BiosSource, progress: (String) -> Unit, done: (BiosIntakeResult) -> Unit)
}

/** One line of an import's results page: a group label, a file with its status, or the closing paragraph. */
sealed interface BiosResultRow {
    data class Group(val title: String) : BiosResultRow
    data class File(val name: String, val status: String) : BiosResultRow
    data class Note(val text: String) : BiosResultRow
}

/** An import's results page: the [plan] as carried out, [failed] the planned writes that could not be written. */
data class BiosIntakeSummary(val plan: BiosIntakePlan, val failed: List<BiosSkip>) {
    private val failedPaths = failed.mapTo(HashSet()) { it.pickedPath }
    private val shown = plan.writes.filter { !(it.needsWrite && it.pickedPath in failedPaths) }

    /** Each console's files (consoles A to Z), then the skips, then a note on other files and placing by hand. */
    fun rows(): List<BiosResultRow> = buildList {
        shown.groupBy { it.console }.entries
            .sortedBy { (tag, _) -> displayName(tag) }
            .forEach { (tag, writes) ->
                add(BiosResultRow.Group(displayName(tag)))
                writes.forEach { add(BiosResultRow.File(it.name, statusText(it) + if (it.bootChange) " · changes boot" else "")) }
            }
        val skipped = plan.skips + failed
        if (skipped.isNotEmpty()) {
            add(BiosResultRow.Group("Skipped"))
            skipped.forEach { add(BiosResultRow.File(it.pickedPath.substringAfterLast('/'), it.reason)) }
        }
        val other = when (plan.otherFiles) {
            0 -> null
            1 -> "1 other file skipped. "
            else -> "${plan.otherFiles} other files skipped. "
        }
        if (skipped.isNotEmpty() || other != null) add(BiosResultRow.Note((other ?: "") + "Anything skipped can still go in Bios/<CONSOLE>/ by hand."))
    }

    /** One line for a notice, when the import ends away from its page. */
    fun noticeText(): String {
        val copied = shown.count { it.needsWrite }
        val there = shown.count { !it.needsWrite }
        return listOfNotNull("Imported $copied BIOS", there.takeIf { it > 0 }?.let { "$it already there" }).joinToString(", ")
    }

    private fun displayName(tag: String) = CoreRegistry.consoleOf(tag)?.displayName ?: tag

    private fun statusText(w: BiosWrite) = when (w.outcome) {
        BiosOutcome.VERIFIED -> "Verified"
        BiosOutcome.RENAMED -> "Renamed from ${w.pickedPath.substringAfterLast('/')}"
        BiosOutcome.NOT_VERIFIED -> "Not verified"
        BiosOutcome.ALREADY_THERE -> "Already there"
        BiosOutcome.KEPT_YOURS -> "Kept yours (verified)"
    }

    companion object {
        const val NONE_FOUND = "No BIOS found"

        /** A pick with no known BIOS in it at all ends as a message, not an empty results page. */
        fun resultOf(plan: BiosIntakePlan, failed: List<BiosSkip>): BiosIntakeResult =
            if (plan.isEmpty) BiosIntakeResult.Message(NONE_FOUND) else BiosIntakeResult.Done(BiosIntakeSummary(plan, failed))
    }
}
