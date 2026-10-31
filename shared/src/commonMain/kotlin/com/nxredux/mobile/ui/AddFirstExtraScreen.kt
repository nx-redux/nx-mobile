package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxSettingRow

/**
 * A first-setup folder step in the onboarding steps' shape ([SetupBlockPage]): the page title ("Setup · Step n of m") its
 * crumb, the [headline] and its [lines], the choices as [rows]. A or a tap on the selected row chooses it ([onRow]); B
 * goes back when [canGoBack]. With [button] the last row (a Skip), or both of two (a real choice), are the buttons
 * under them ([setupButtons]); a lone row always is one.
 * [lineBreaks]: a line each.
 */
@Composable
fun SetupStepScreen(
    title: String, headline: String, lines: List<String>, rows: List<String>, canGoBack: Boolean,
    selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit, modifier: Modifier = Modifier,
    button: Boolean = false, lineBreaks: Boolean = false,
) {
    val hints = listOfNotNull(if (canGoBack) Hint(NxButton.B, "Back") else null, Hint(NxButton.A, "Choose"))
    // A lone row only moves on (Pick folder, OK): the button.
    val touchRows = rows.map { NxSettingRow(it) }
    SetupBlockPage(title, headline, lines, touchRows, setupButtons(touchRows, button || rows.size == 1), selected, hints, onSelect, onRow, onHint, modifier, lineBreaks = lineBreaks)
}

/** First setup's Permissions page ([Screen.PermissionsStep]): each permission with its status, Continue under them. */
@Composable
fun PermissionsStepScreen(
    screen: Screen.PermissionsStep, title: String, canGoBack: Boolean, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit,
) {
    fun status(granted: Boolean?) = if (granted == true) "Allowed" else "Not allowed"
    // Each permission its status on the right (a tap asks for it), Continue the button under them.
    val rows = permissionRows(screen).map {
        when (it) {
            PermissionRow.ALL_FILES -> NxSettingRow("All files access", value = status(screen.allFiles))
            PermissionRow.NOTIFICATIONS -> NxSettingRow("Notifications", value = status(screen.notifications))
            PermissionRow.CONTINUE -> NxSettingRow("Continue")
        }
    }
    val hints = listOfNotNull(if (canGoBack) Hint(NxButton.B, "Back") else null, Hint(NxButton.A, "Choose"))
    SetupBlockPage(
        title, "Permissions",
        listOf(
            "All files access lets NX Redux read your library faster, sync ROMs with a handheld and use its own folder picker. " +
                "Notifications show artwork and sync progress.",
        ),
        rows, buttons = 1, selected, hints, onSelect, onRow, onHint,
    )
}

/**
 * The home folder pick (first setup, or a lost home). Android's own picker ([systemPicker]: no All files access) refuses
 * a volume's root and Download, so only then does the page say so; the app's picker takes a card's root (a handheld's),
 * and with it (All files access) the saves live in the home too, which the page says.
 */
@Composable
fun PickFolderScreen(
    title: String, canGoBack: Boolean, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit,
    systemPicker: Boolean = true,
) =
    SetupStepScreen(
        title, "Pick a home folder",
        listOf(
            "An empty folder, or one NX Redux used before. Roms and Bios are made inside it; games elsewhere in it move into Roms." +
                // With All files access (the app's picker) the saves move into the home too ([homeFolderStatus]).
                if (systemPicker) " Not the storage root, the SD card root or Download."
                else " Your saves and save states live here too, so uninstalling the app keeps them. An SD card's root works too.",
        ),
        listOf("Pick folder"), canGoBack, selected, onSelect, onRow, onHint,
    )

/**
 * The one-time storage upgrade ([Screen.StorageUpgrade], installs from v0.3.0 and earlier; remove after v1.0), in the
 * setup steps' shape. Required: B never leaves it for the library, so no Back hint.
 */
@Composable
fun StorageUpgradeScreen(screen: Screen.StorageUpgrade, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit) {
    val (headline, line) = when {
        screen.error != null -> "Saves not moved" to screen.error
        screen.stage == Screen.StorageUpgrade.Stage.EXPLAIN ->
            "Keep your saves" to "Your saves can now live in your own folder, so uninstalling keeps them. This needs All files access."
        screen.stage == Screen.StorageUpgrade.Stage.PICK ->
            "Pick your home folder" to "Pick the folder that holds your Roms again. Your saves, states and settings move into it."
        else -> "Saves moved" to
            "Your saves, states and settings are now in ${screen.folder}. Uninstalling the app removes them. Your games stay where they are."
    }
    SetupStepScreen(
        "Storage", headline, listOf(line), storageUpgradeRows(screen), canGoBack = false, selected, onSelect, onRow, onHint,
        button = true,   // Not now (or the lone Pick folder, OK)
    )
}

/** A home change in Settings ([Screen.CopySaves]): copy the old home's saves into the new one, or keep them separate. */
@Composable
fun CopySavesScreen(screen: Screen.CopySaves, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit) {
    val (headline, line) = when {
        screen.error != null -> "Saves not copied" to screen.error
        screen.busy -> "Copying your saves" to "Copying saves, states and settings into the new folder…"
        else -> "Copy your saves to the new folder?" to
            "Saves, states and settings in the old folder stay there. Copy puts them in the new folder too; where both have a file, the newer one is kept and the other saved beside it."
    }
    SetupStepScreen("Storage", headline, listOf(line), if (screen.busy) emptyList() else copySavesRows(screen), canGoBack = !screen.busy, selected, onSelect, onRow, onHint, button = true)
}

/** First setup, after the home pick: offer an existing ROMs folder as an extra source (scanned in place). [note]: a line above it ([AppState.setupNote]). */
@Composable
fun AddFirstExtraScreen(title: String, canGoBack: Boolean, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit, note: String? = null) =
    SetupStepScreen(
        title, "Add a ROMs folder?",
        listOfNotNull(
            note,
            "Already keep ROMs somewhere else? Add that folder too. It is scanned in place and nothing is moved. " +
                "You can add more folders later in Settings.",
        ),
        listOf("Add folder", "Skip"), canGoBack, selected, onSelect, onRow, onHint, button = true,
    )

/** First setup's last folder step: [ask]'s "Add N Android games to Consoles?" with the games listed, a line each. */
@Composable
fun AndroidGamesStepScreen(
    ask: AndroidGamesAsk, title: String, canGoBack: Boolean, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit,
) {
    val lines = listOf(ask.question, "") + ask.shown(AndroidGamesAsk.MAX_LINES)
    SetupStepScreen(title, ask.title, lines, listOf("Yes", "Not now"), canGoBack, selected, onSelect, onRow, onHint, button = true, lineBreaks = true)
}

/**
 * First setup's summary of the ROMs folder just added ([Screen.FolderSummary]): what was found, a line per console, and
 * where the unassigned games are; the Move choice as a row (its On / Off on the right) and Continue the button under it.
 * B stops a running move. Continue with Move on asks first ([folderMoveAsk]): A moves, B goes back.
 */
@Composable
fun FolderSummaryScreen(
    screen: Screen.FolderSummary, title: String, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit,
    note: String? = null,
) {
    val rows = folderSummaryRows(screen)
    val hints = if (screen.progress != null) listOf(Hint(NxButton.B, "Stop")) else listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Choose"))
    val details = folderSummaryDetails(screen) + listOfNotNull(note)
    Box(Modifier.fillMaxSize()) {
        SetupBlockPage(
            title, details.first(), details.drop(1), rows.map { NxSettingRow(it.row.label, value = it.row.secondary) },
            buttons = if (rows.lastOrNull()?.kind == SummaryRowKind.CONTINUE) 1 else 0, selected, hints, onSelect, onRow, onHint, lineBreaks = true,
        )
        if (screen.confirmMove) folderMoveAsk(screen)?.let { (ask, text) ->
            NxDialog(ask, text, onConfirm = { onHint(NxButton.A) }, confirmLabel = "Move", onCancel = { onHint(NxButton.B) })
        }
    }
}
