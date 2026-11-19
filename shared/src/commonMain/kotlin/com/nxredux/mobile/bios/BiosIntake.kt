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
    fun lines(): List<String> = buildList {
        val failedPaths = failed.mapTo(HashSet()) { it.pickedPath }
        val shown = plan.writes.filter { !(it.needsWrite && it.pickedPath in failedPaths) }
        shown.groupBy { it.console }.entries
            .sortedBy { (tag, _) -> displayName(tag) }
            .forEach { (tag, writes) ->
                add(displayName(tag))
                writes.forEach { add("${it.name} — ${outcomeText(it)}" + if (it.bootChange) " · optional: changes boot" else "") }
            }
        val skipped = plan.skips + failed
        if (skipped.isNotEmpty()) {
            add("Skipped")
            skipped.forEach { add("${it.pickedPath.substringAfterLast('/')} — ${it.reason}") }
        }
        if (plan.otherFiles > 0) add(if (plan.otherFiles == 1) "1 other file skipped" else "${plan.otherFiles} other files skipped")
        if (skipped.isNotEmpty() || plan.otherFiles > 0) add("Anything skipped can still go in Bios/<CONSOLE>/ by hand.")
    }

    private fun displayName(tag: String) = CoreRegistry.consoleOf(tag)?.displayName ?: tag

    private fun outcomeText(w: BiosWrite) = when (w.outcome) {
        BiosOutcome.VERIFIED -> "verified"
        BiosOutcome.RENAMED -> "renamed from \"${w.pickedPath.substringAfterLast('/')}\""
        BiosOutcome.NOT_VERIFIED -> "not verified"
        BiosOutcome.ALREADY_THERE -> "already there"
        BiosOutcome.KEPT_YOURS -> "kept yours (verified)"
    }

    companion object {
        const val NONE_FOUND = "No BIOS found"

        /** A pick with no known BIOS in it at all ends as a message, not an empty results page. */
        fun resultOf(plan: BiosIntakePlan, failed: List<BiosSkip>): BiosIntakeResult =
            if (plan.isEmpty) BiosIntakeResult.Message(NONE_FOUND) else BiosIntakeResult.Done(BiosIntakeSummary(plan, failed))
    }
}
