package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.LocalWindowSize
import com.nxredux.mobile.ui.nx.valueHints
import com.nxredux.mobile.ui.nx.HintTouch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nxredux.mobile.scraper.JobPhase
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDetailListBody
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxSettingControl
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.NxSettingsPage
import com.nxredux.mobile.ui.nx.control
import com.nxredux.mobile.ui.nx.NxSignInPrompt
import com.nxredux.mobile.ui.nx.NxTextPrompt

private val selectBack = listOf(Hint(NxButton.A, "Select"), Hint(NxButton.B, "Back"))

/** The Artwork page's label above its words, as "Settings" above Settings' ([SettingsRootScreen]). */
internal const val ARTWORK_TITLE = "Artwork"

/**
 * Tools → Artwork and its pages: the Artwork page in the Settings root's look ([SettingsRootScreen]), the pages under it
 * in the tool-page shape (title, detail lines, whole rows with scroll arrows). The state is in the screen ([ArtworkScreenController] republishes it). One page:
 * the body ([ArtworkPageBody]) with its dialogs ([ArtworkDialogs]) over it; the wide stage draws them apart ([ArtworkStageScreen]).
 */
@Composable
fun ArtworkScreen(
    screen: Screen, state: AppState, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onHint: (NxButton) -> Unit, modifier: Modifier = Modifier,
    split: Boolean = false,   // in the rows block ([LocalSettingsBlock]), whose first line names the tool
) {
    val truncated by state.settingInfoTruncated.collectAsState()   // Settings' selected row's cut-off line: its X Info hint
    Box(modifier.fillMaxSize()) {
        ArtworkPageBody(
            screen, selectedIndex, onSelect, onOpen, onHint, split = split,
            onSetting = state::settingEvent, onTruncated = state::onSettingInfoTruncated, truncated = truncated,
        )
        ArtworkDialogs(screen, state, onHint)
    }
}

/**
 * An Artwork page without its dialogs. [split]: on the wide stage ([ArtworkStageScreen]), titled without the tool's
 * name; [focused] = false: the root's preview, with no hints (Settings' controls take no taps there).
 * [onSetting], [onTruncated] and [truncated]: Settings' touch rows, as Settings' pages ([SettingsScreen]).
 */
@Composable
internal fun ArtworkPageBody(
    screen: Screen, selectedIndex: Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onHint: (NxButton) -> Unit,
    modifier: Modifier = Modifier, split: Boolean = false, focused: Boolean = true,
    onSetting: (NxSettingEvent) -> Unit = {}, onTruncated: (String, Boolean) -> Unit = { _, _ -> }, truncated: String? = null,
) {
    fun hints(h: List<Hint>) = if (focused) h else emptyList()
    when (screen) {
        // The Settings root's look: the actions as its large words, the details as status lines at the top; a tap opens at once.
        is Screen.Artwork -> SettingsRootScreen(
            ARTWORK_TITLE, screen.rows.map { it.label }, screen.details, selectedIndex,
            onTap = { onSelect(it); onOpen(it) }, modifier = modifier, hintBar = focused, onHint = onHint,
        )
        is Screen.ArtworkConsoles -> NxDetailListBody(
            title = toolPageTitle(screen, split).orEmpty(), details = emptyList(),
            rows = screen.rows.map { NxRow(it.name, secondary = "${it.missing} missing") },
            selectedIndex = selectedIndex, hints = hints(listOf(Hint(NxButton.A, "Fetch"), Hint(NxButton.B, "Back"))),
            onSelect = onSelect, onOpen = onOpen, modifier = modifier, onHint = onHint, rowsAtTop = true,
        )
        is Screen.ArtworkProgress -> ArtworkProgressBody(screen, selectedIndex, onSelect, onOpen, onHint, modifier, split, focused)
        is Screen.ArtworkSettings -> {
            val rows = remember(screen.rows) { screen.rows.map(::artworkSettingTouchRow) }   // not per d-pad move
            val touch = rows.getOrNull(selectedIndex)
            NxSettingsPage(
                toolPageTitle(screen, split), rows, selectedIndex,
                hints = hints(artworkSettingsHints(screen.rows.getOrNull(selectedIndex), touch, touch != null && nxShowsInfo(touch, truncated), rowEditTwoPane(screen, settingsSplit(LocalWindowSize.current)))),
                onSelect = onSelect, onOpen = onOpen, onEvent = onSetting, modifier = modifier,
                onTruncated = onTruncated, onHint = onHint, preview = !focused,
            )
        }
        is Screen.ArtworkReset -> NxDetailListBody(
            title = toolPageTitle(screen, split).orEmpty(), details = listOf("Deletes only art this app fetched. Art you added yourself is kept."),
            rows = screen.scopes.map { NxRow(it.label) }, selectedIndex = selectedIndex, headline = false,
            hints = hints(selectBack), onSelect = onSelect, onOpen = onOpen, modifier = modifier, onHint = onHint,
        )
        else -> Unit
    }
}

/**
 * An Artwork page's open dialog or prompt (Settings' sign-in, key prompt and logout confirm; Reset's and Progress's
 * confirms): over the page, or over the whole wide stage ([SettingsStage]'s over).
 */
@Composable
internal fun ArtworkDialogs(screen: Screen, state: AppState, onHint: (NxButton) -> Unit) {
    when (screen) {
        is Screen.ArtworkSettings -> {
            if (screen.signingIn) {
                NxSignInPrompt(
                    screen.draftUser, screen.draftPassword, state::artworkDraftUser, state::artworkDraftPassword,
                    onConfirm = { onHint(NxButton.A) }, onCancel = { onHint(NxButton.B) }, caption = "Sign in to ScreenScraper",
                )
            }
            if (screen.confirmLogout) {
                NxDialog(
                    "Log out of ScreenScraper?", "Art fetches use the anonymous quota until you log in again.",
                    onConfirm = { onHint(NxButton.A) }, confirmLabel = "Logout", onCancel = { onHint(NxButton.B) },
                )
            }
            if (screen.confirmClearCache) {
                NxDialog(
                    "Clear unused images?", "Delete cached pictures you aren't using? They download again when you change art.",
                    onConfirm = { onHint(NxButton.A) }, confirmLabel = "Delete", onCancel = { onHint(NxButton.B) }, cancelLabel = "Cancel",
                )
            }
            if (screen.editingKey) {
                NxTextPrompt(
                    "${screen.keyFor.label} API key", screen.draftKey, state::artworkDraftKey,
                    onConfirm = { onHint(NxButton.A) }, onCancel = { onHint(NxButton.B) },
                )
            }
        }
        is Screen.ArtworkReset -> screen.confirm?.let { c ->
            NxDialog(
                "Reset artwork?", "Delete fetched art for ${c.label}? Art you added yourself is kept.",
                onConfirm = { onHint(NxButton.A) }, confirmLabel = "Delete", onCancel = { onHint(NxButton.B) },
            )
        }
        is Screen.ArtworkProgress -> if (screen.confirmCancel) {
            NxDialog(
                "Cancel art fetch?", "Games already fetched keep their art.",
                onConfirm = { onHint(NxButton.A) }, confirmLabel = "Stop", onCancel = { onHint(NxButton.B) },
            )
        }
        else -> Unit
    }
}

/**
 * Artwork on Settings' wide stage ([artworkSplitView], [SettingsStage]): its words and status lines on the left (the
 * page as it stands, [AppState.artworkStagePage]). On the Artwork page the words have the focus and the right frame
 * previews the highlighted word's page ([AppState.artworkPreview]; empty for Fetch missing art and Resume), undimmed and
 * without hints, a tap opening it on that row ([AppState.artworkPreviewTap]). On a page the right frame is that page
 * and its word drops to the open grey with a "›" ([AppState.artworkWord]; Reset artwork's is Settings); a tap on a
 * word opens it in place ([AppState.artworkWordTap]). The dialogs cover the whole stage.
 */
@Composable
internal fun ArtworkStageScreen(state: AppState, s: Screen, selection: State<ListSelection>) {
    // Keyed on the job too: beside a running fetch the open page stays the same screen, the words' counts move on.
    val job = state.artworkJob?.collectAsState()?.value
    val words = remember(s, job) { state.artworkStagePage(s) }
    val root = s is Screen.Artwork
    // Read where it is drawn (the word block, the preview), so a d-pad move on the root does not recompose the stage.
    val current = { if (root) selection.value.index else words.rows.indexOfFirst { it.action == state.artworkWord(s) }.coerceAtLeast(0) }
    val truncated by state.settingInfoTruncated.collectAsState()   // Settings' selected row's cut-off line: its X Info hint
    SettingsStage(
        title = ARTWORK_TITLE, words = words.rows.map { it.label }, lines = words.details, current = current, root = root,
        onWordTap = state::artworkWordTap, blockPage = true, onHint = state::onButton,
        preview = {
            val action = words.rows.getOrNull(current())?.action
            val preview = remember(s, action, job) { action?.let(state::artworkPreview) }
            // No selection, undimmed, no hints: the highlighted word is the one accent word, as Settings' preview.
            preview?.let { ArtworkPageBody(it, -1, onSelect = state::artworkPreviewTap, onOpen = state::artworkPreviewTap, onHint = {}, split = true, focused = false) }
        },
        page = {
            WithIndex(selection) {
                ArtworkPageBody(
                    s, it, state::select, { state.openSelected() }, state::onButton, split = true,
                    onSetting = state::settingEvent, onTruncated = state::onSettingInfoTruncated, truncated = truncated,
                )
            }
        },
        over = { ArtworkDialogs(s, state, state::onButton) },
    )
}

@Composable
private fun ArtworkProgressBody(
    screen: Screen.ArtworkProgress, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onHint: (NxButton) -> Unit, modifier: Modifier, split: Boolean, focused: Boolean,
) {
    val phase = screen.job.phase
    val hints = if (focused) artworkProgressHints(phase) else emptyList()   // the stage's preview: no hints, no touch buttons
    NxDetailListBody(
        title = toolPageTitle(screen, split).orEmpty(),
        details = listOfNotNull(progressHeader(screen.job, screen.starting), artworkSummary(screen.job).takeIf { it.isNotEmpty() && !screen.starting }),
        // An ended job's list only keeps the games to look at; the summary says the rest.
        rows = remember(screen.job) { artworkProgressRows(screen.job).map { NxRow(it.name, secondary = itemStatusText(it.status, it.reason)) } },   // not per d-pad move
        selectedIndex = selectedIndex, hints = hints, onSelect = onSelect, onOpen = onOpen, modifier = modifier, onHint = onHint,
    )
}

/** Rows whose secondary (today's requests, the allowance left) is their description, not a value. */
private val ART_DESCRIBED_KINDS = setOf(ArtworkSettingKind.SS_LOGOUT, ArtworkSettingKind.TGDB_LEFT)

/**
 * A Settings row as a touch row, as Settings' ([settingsTouchRow]): the On/Off rows (Replace existing art, 2D box art,
 * Wheel, Mix) a switch; ScreenScraper's logout and TheGamesDB's allowance their count as the line; an unset key row
 * its Not set (once set it is a Remove row, no value); Reset artwork `›`. A dimmed row is drawn disabled. A group
 * label and a gap as the list's.
 */
internal fun artworkSettingTouchRow(row: ArtworkSettingRow): NxSettingRow {
    if (row.kind == ArtworkSettingKind.SECTION) return NxSettingRow(row.label, section = true)
    if (row.kind == ArtworkSettingKind.GAP) return NxSettingRow("", spacer = true)
    val text = artworkSettingInfo(row)
    val switch = row.kind.toggle && row.enabled
    return NxSettingRow(
        row.label, info = text.line, detail = text.detail,
        choices = if (switch) ART_ON_OFF else emptyList(), index = if (switch) (if (row.secondary == "On") 1 else 0) else -1,
        value = if (!switch && row.kind !in ART_DESCRIBED_KINDS) row.secondary else null,
        opens = row.kind == ArtworkSettingKind.RESET, enabled = row.enabled,
    )
}

private val ART_ON_OFF = listOf("Off", "On")

/** A compact row: no line, [text] (or a fuller [detail]) behind the info icon only. */
private fun quiet(text: String, detail: String = "") = SettingText("", detail.ifEmpty { text })

/**
 * [row]'s description, from the user guide's Artwork page: a line for a status (today's requests, the allowance left),
 * its fuller text behind the info icon; the rest compact ([quiet]), why a row is dimmed in its fuller text.
 */
internal fun artworkSettingInfo(row: ArtworkSettingRow): SettingText = when (row.kind) {
    ArtworkSettingKind.REPLACE -> quiet(
        "Fetch art again for games that already have it",
        "Fetch art again for games that already have it. Fetch missing art then reads Fetch all art.",
    )
    ArtworkSettingKind.BOX2D -> quiet("Also fetch the flat 2D cover, beside the 3D box")
    ArtworkSettingKind.WHEEL -> quiet("Also fetch the game's logo")
    ArtworkSettingKind.MIX -> quiet(
        "Fetch the handheld's mix picture",
        "Fetch ScreenScraper's mix picture. When it has none, the app makes one from the screenshot, box and wheel.",
    )
    ArtworkSettingKind.SS_LOGIN -> quiet(
        "Log in for your own daily allowance",
        "ScreenScraper works without an account, on a shared allowance. A free ScreenScraper account gives you your own " +
            "daily allowance, which goes much further. Login is dimmed while art is downloading.",
    )
    ArtworkSettingKind.SS_LOGOUT -> SettingText(
        row.secondary.orEmpty(),
        "Your requests used today. Logout asks first: fetches then use the anonymous allowance again. Logout is dimmed " +
            "while art is downloading.",
    )
    ArtworkSettingKind.SGDB_KEY -> quiet(
        "Only used with your own free key",
        "SteamGridDB is only used with your own free key from steamgriddb.com. A key is checked before it is saved: if " +
            "SteamGridDB turns it down, it is not saved.",
    )
    ArtworkSettingKind.TGDB_KEY -> quiet(
        "Only used with your own free key",
        "TheGamesDB is only used with your own free key from thegamesdb.net. A key is checked before it is saved: if " +
            "TheGamesDB turns it down, it is not saved.",
    )
    ArtworkSettingKind.TGDB_LEFT -> SettingText(row.secondary.orEmpty(), "The requests your TheGamesDB key has left this month.")
    ArtworkSettingKind.CLEAR_CACHE -> quiet(
        "Delete pictures kept for Change art",
        "Delete the pictures kept for Change art that you aren't using. They download again when you change art.",
    )
    ArtworkSettingKind.RESET -> quiet(
        "Delete art the app fetched",
        "Delete art the app fetched, for every console or one. Art you added yourself is kept.",
    )
    ArtworkSettingKind.SGDB_REMOVE, ArtworkSettingKind.TGDB_REMOVE, ArtworkSettingKind.SECTION, ArtworkSettingKind.GAP -> SettingText("")
}

/**
 * Artwork Settings' hints for the highlighted row, as Settings' ([settingsHints]): an On/Off row A Toggle (and on one
 * page LEFT Change, [valueHints]); TheGamesDB's allowance and a dimmed row Back only; the rest A Select. X Info while its description is cut off
 * or says more ([info]).
 */
internal fun artworkSettingsHints(row: ArtworkSettingRow?, touch: NxSettingRow?, info: Boolean, twoPane: Boolean = false): List<Hint> {
    val back = Hint(NxButton.B, "Back")
    val x = if (info) listOf(Hint(NxButton.X, "Info")) else emptyList()
    return when {
        row == null || !row.selectable -> listOf(back)
        !row.enabled || row.kind == ArtworkSettingKind.TGDB_LEFT -> listOf(back) + x
        touch?.control == NxSettingControl.SWITCH -> listOf(back) + touch.valueHints(twoPane) + x
        else -> listOf(back, Hint(NxButton.A, "Select")) + x
    }
}

/**
 * The fetch progress page's hints: Y Pause / Resume and X Cancel while it runs, A Done once it has ended (it clears the
 * job), B Back. On touch Pause, Cancel and Done are buttons under the details, above the list.
 */
internal fun artworkProgressHints(phase: JobPhase): List<Hint> = buildList {
    when (phase) {
        JobPhase.RUNNING -> add(Hint(NxButton.Y, "Pause", HintTouch.Button(primary = true)))
        JobPhase.PAUSED -> add(Hint(NxButton.Y, "Resume", HintTouch.Button(primary = true)))
        JobPhase.DONE, JobPhase.CANCELLED -> add(Hint(NxButton.A, "Done", HintTouch.Button(primary = true)))
        JobPhase.IDLE -> Unit
    }
    if (phase == JobPhase.RUNNING || phase == JobPhase.PAUSED) add(Hint(NxButton.X, "Cancel", HintTouch.Button(primary = false)))
    add(Hint(NxButton.B, "Back"))
}
