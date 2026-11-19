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

/** An import's results page: the [plan] as carried out, [failed] the planned writes that could not be written. */
data class BiosIntakeSummary(val plan: BiosIntakePlan, val failed: List<BiosSkip>) {
    // A failed write leaves that destination only: the file's other consoles still show, its duplicates there do not.
    private val failedAt = failed.mapNotNullTo(HashSet()) { f -> f.console?.let { it to f.name } }
    private val shown = plan.writes.filter { (it.console to it.name) !in failedAt }

    /** "Imported 3 BIOS, 2 already there": the page's headline, and the notice when the import ends away from it. */
    fun headline(): String {
        val copied = shown.count { it.needsWrite }
        val there = shown.count { !it.needsWrite }
        return listOfNotNull("Imported $copied BIOS", there.takeIf { it > 0 }?.let { "$it already there" }).joinToString(", ")
    }

    /** A line per file (consoles A to Z), the skips, then a note on other files and placing by hand; blank lines between. */
    fun lines(): List<String> {
        val files = shown.sortedBy { displayName(it.console) }.map { w ->
            "${displayName(w.console)}: ${w.name} (${statusText(w)}${if (w.bootChange) ", changes boot" else ""})"
        }
        val skipped = (plan.skips + failed).map { "Skipped ${biosBaseName(it.pickedPath)}: ${it.reason.replaceFirstChar(Char::lowercaseChar)}" }
        val other = when (plan.otherFiles) {
            0 -> null
            1 -> "1 other file skipped. "
            else -> "${plan.otherFiles} other files skipped. "
        }
        val note = if (skipped.isNotEmpty() || other != null) listOf((other ?: "") + "Anything skipped can still go in Bios/<CONSOLE>/ by hand.") else emptyList()
        return listOf(files, skipped, note).filter { it.isNotEmpty() }.flatMapIndexed { i, block -> if (i == 0) block else listOf("") + block }
    }

    private fun displayName(tag: String) = CoreRegistry.consoleOf(tag)?.displayName ?: tag

    private fun statusText(w: BiosWrite) = when (w.outcome) {
        BiosOutcome.VERIFIED -> "verified"
        BiosOutcome.RENAMED -> "renamed from ${biosBaseName(w.pickedPath)}"
        BiosOutcome.NOT_VERIFIED -> "not verified"
        BiosOutcome.ALREADY_THERE -> "already there"
        BiosOutcome.KEPT_YOURS -> "kept yours, verified"
    }

    companion object {
        const val NONE_FOUND = "No BIOS found"

        /** A pick with no known BIOS in it at all ends as a message, not an empty results page. */
        fun resultOf(plan: BiosIntakePlan, failed: List<BiosSkip>): BiosIntakeResult =
            if (plan.isEmpty) BiosIntakeResult.Message(NONE_FOUND) else BiosIntakeResult.Done(BiosIntakeSummary(plan, failed))
    }
}
