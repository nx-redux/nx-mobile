package com.nxredux.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDetailListBody
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxRowKind
import com.nxredux.mobile.ui.nx.NxSetupLandscapeWidth

/**
 * A first-setup folder step in the onboarding steps' shape ([OnboardingScreen]): the page title ("Setup · Step n of m"),
 * the [headline] and its [lines], the choices as [rows]. A or a tap on the selected row chooses it ([onRow]); B goes back
 * when [canGoBack]. The last [titleActions] rows sit at the title's right (a Skip).
 */
@Composable
fun SetupStepScreen(
    title: String, headline: String, lines: List<String>, rows: List<String>, canGoBack: Boolean,
    selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit, modifier: Modifier = Modifier,
    titleActions: Int = 0, landscapeColumns: IntRange? = null, landscapeColumnCount: Int = 2,
) {
    val hints = listOfNotNull(if (canGoBack) Hint(NxButton.B, "Back") else null, Hint(NxButton.A, "Choose"))
    NxDetailListBody(
        title = title, details = listOf(headline) + lines, rows = rows.map { NxRow(it) }, selectedIndex = selected, hints = hints,
        onSelect = onSelect, onOpen = onRow, onHint = onHint, modifier = modifier, titleActions = titleActions,
        landscapeColumns = landscapeColumns, landscapeColumnCount = landscapeColumnCount, landscapeWidth = NxSetupLandscapeWidth,
    )
}

/** First setup's Permissions page ([Screen.PermissionsStep]): each permission with its status, Continue under them. */
@Composable
fun PermissionsStepScreen(
    screen: Screen.PermissionsStep, title: String, canGoBack: Boolean, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit,
) {
    fun status(granted: Boolean?) = if (granted == true) "Allowed" else "Not allowed"
    val rows = permissionRows(screen).map {
        when (it) {
            PermissionRow.ALL_FILES -> NxRow("All files access", secondary = status(screen.allFiles))
            PermissionRow.NOTIFICATIONS -> NxRow("Notifications", secondary = status(screen.notifications))
            PermissionRow.CONTINUE -> NxRow("Continue")
        }
    }
    val hints = listOfNotNull(if (canGoBack) Hint(NxButton.B, "Back") else null, Hint(NxButton.A, "Choose"))
    NxDetailListBody(
        title = title,
        details = listOf("Permissions", permissionsText(screen)),
        rows = rows, selectedIndex = selected, hints = hints, onSelect = onSelect, onOpen = onRow, onHint = onHint,
        rowKind = NxRowKind.OPTIONS, actionRows = 1, actionsAtTop = true, landscapeWidth = NxSetupLandscapeWidth,
    )
}

/**
 * The home folder pick (first setup, or a lost home). Android's own picker ([systemPicker]: no All files access) refuses
 * a volume's root and Download, so only then does the page say so; the app's picker takes a card's root (a handheld's).
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
                if (systemPicker) " Not the storage root, the SD card root or Download." else " An SD card's root works too.",
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
    SetupStepScreen("Storage", headline, listOf(line), storageUpgradeRows(screen), canGoBack = false, selected, onSelect, onRow, onHint)
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
    SetupStepScreen("Storage", headline, listOf(line), if (screen.busy) emptyList() else copySavesRows(screen), canGoBack = !screen.busy, selected, onSelect, onRow, onHint)
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
        listOf("Add folder", "Skip"), canGoBack, selected, onSelect, onRow, onHint, titleActions = 1,
    )

/**
 * First setup's last folder step: [ask]'s "Add N Android games to Consoles?" with the games listed, a line each; on a
 * landscape screen in three columns, so more of them show.
 */
@Composable
fun AndroidGamesStepScreen(
    ask: AndroidGamesAsk, title: String, canGoBack: Boolean, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit,
) {
    val landscape = !LocalPortrait.current   // the device's orientation (a portrait page above the pad is wide)
    val games = ask.shown(if (landscape) ANDROID_GAMES_LANDSCAPE_LINES else AndroidGamesAsk.MAX_LINES)
    val lines = listOf(ask.question, "") + games
    SetupStepScreen(
        title, ask.title, lines, listOf("Yes", "Not now"), canGoBack, selected, onSelect, onRow, onHint,
        landscapeColumns = 3 until 3 + games.size, landscapeColumnCount = 3,   // after the headline, the question and the blank
    )
}

/** The games a landscape Android games step lists: three columns of four. */
private const val ANDROID_GAMES_LANDSCAPE_LINES = 12

/**
 * First setup's summary of the ROMs folder just added ([Screen.FolderSummary]): what was found, a line per console, and
 * where the unassigned games are; the Move choice as an options row and Continue as a full row under it. B stops a
 * running move.
 */
@Composable
fun FolderSummaryScreen(
    screen: Screen.FolderSummary, title: String, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit,
    note: String? = null,
) {
    val rows = folderSummaryRows(screen)
    val hints = if (screen.progress != null) listOf(Hint(NxButton.B, "Stop")) else listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Choose"))
    NxDetailListBody(
        title = title, details = folderSummaryDetails(screen) + listOfNotNull(note), rows = rows.map { it.row }, selectedIndex = selected, hints = hints,
        onSelect = onSelect, onOpen = onRow,
        onHint = onHint,
        // Continue at the title's right, the Move row alone under the console lines.
        rowKind = NxRowKind.OPTIONS, actionRows = 1, actionsAtTop = true, landscapeWidth = NxSetupLandscapeWidth,
        landscapeColumns = 1..screen.summary.byConsole.size,   // the console lines, after the headline
    )
}
