package com.nxredux.mobile.settings

import com.nxredux.mobile.storage.ImportSummary

/** Where Import saves reads from. */
enum class ImportSource { FOLDER, ZIP, RETROARCH }

/** How an import ended: [Done] with its summary, or a [Message] (a refusal, nothing found, "Failed: …"). */
sealed interface ImportResult {
    data class Done(val summary: ImportSummary) : ImportResult
    data class Message(val text: String) : ImportResult
}

/**
 * A transfer's notice: [text], with [done] false for the progress notice while the work runs (it stays until the next
 * notice replaces it) and true for the summary, the error or a refusal.
 */
typealias TransferReport = (text: String, done: Boolean) -> Unit

/**
 * The platform's save Export/Import (Settings → Library → Saves). Each call opens what it needs (a picker) and runs in
 * the background. An export's [TransferReport] is called with the progress notice when the work starts and then once
 * more, done, with the summary or the error. An import calls `progress` when its work starts and then `done` once with
 * its [ImportResult]; a refusal (a game still alive, a sync running) is one [ImportResult.Message]. A cancelled picker
 * reports nothing.
 */
interface SaveTransfer {
    /** True while an import runs: a game must not start then, or its save in memory could later overwrite an imported one. */
    val importing: Boolean
    fun exportSaves(report: TransferReport)
    /** Copy only: an import never deletes or changes its source. */
    fun importSaves(source: ImportSource, progress: (String) -> Unit, done: (ImportResult) -> Unit)
}

/** When an import or a game launch must wait for another transfer ([SaveTransfer.importing], a running Device Sync). */
object SaveTransferRules {
    const val CLOSE_GAME = "Close the running game first, then import."
    const val IMPORTING = "Importing saves… please wait"
    const val SYNCING = "Device Sync is running… please wait"

    /** A game screen still alive (suspended, or closing) holds its save in memory and writes it back over an import. */
    fun importRefusal(gameScreenAlive: Boolean, syncing: Boolean = false): String? = when {
        gameScreenAlive -> CLOSE_GAME
        syncing -> SYNCING
        else -> null
    }

    fun launchRefusal(importing: Boolean, syncing: Boolean = false): String? = when {
        importing -> IMPORTING
        syncing -> SYNCING
        else -> null
    }
}
