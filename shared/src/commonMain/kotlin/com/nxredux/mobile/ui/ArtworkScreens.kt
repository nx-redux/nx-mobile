package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.HintTouch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

/**
 * Tools → Artwork and its pages, all in the tool-page shape: title, detail
 * lines, whole rows with scroll arrows. The state is in the screen ([ArtworkScreenController] republishes it). One page:
 * the body ([ArtworkPageBody]) with its dialogs ([ArtworkDialogs]) over it; the column browser draws them apart.
 */
@Composable
fun ArtworkScreen(
    screen: Screen, state: AppState, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onHint: (NxButton) -> Unit, modifier: Modifier = Modifier,
) {
    val truncated by state.settingInfoTruncated.collectAsState()   // Settings' selected row's cut-off line: its X Info hint
    Box(modifier.fillMaxSize()) {
        ArtworkPageBody(
            screen, selectedIndex, onSelect, onOpen, onHint,
            onSetting = state::settingEvent, onTruncated = state::onSettingInfoTruncated, truncated = truncated,
        )
        ArtworkDialogs(screen, state, onHint)
    }
}

/**
 * An Artwork page without its dialogs. [split]: a pane of the column browser ([ArtworkSplitScreen]), titled without
 * the tool's name; [focused] = false: its dimmed left pane, with no hints (Settings' controls take no taps there).
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
        is Screen.Artwork -> NxDetailListBody(
            title = "Artwork", details = screen.details, rows = screen.rows.map { NxRow(it.label) },
            selectedIndex = selectedIndex, hints = hints(selectBack), onSelect = onSelect, onOpen = onOpen, modifier = modifier, onHint = onHint,
        )
        is Screen.ArtworkConsoles -> NxDetailListBody(
            title = toolPageTitle(screen, split).orEmpty(), details = emptyList(),
            rows = screen.rows.map { NxRow(it.name, secondary = "${it.missing} missing") },
            selectedIndex = selectedIndex, hints = hints(listOf(Hint(NxButton.A, "Fetch"), Hint(NxButton.B, "Back"))),
            onSelect = onSelect, onOpen = onOpen, modifier = modifier, onHint = onHint, rowsAtTop = true,
        )
        is Screen.ArtworkProgress -> ArtworkProgressBody(screen, selectedIndex, onSelect, onOpen, onHint, modifier, split)
        is Screen.ArtworkSettings -> {
            val rows = remember(screen.rows) { screen.rows.map(::artworkSettingTouchRow) }   // not per d-pad move
            val touch = rows.getOrNull(selectedIndex)
            NxSettingsPage(
                toolPageTitle(screen, split), rows, selectedIndex,
                hints = hints(artworkSettingsHints(screen.rows.getOrNull(selectedIndex), touch, touch != null && nxShowsInfo(touch, truncated))),
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
 * confirms): over the page, or in a split over both panes ([SplitPanes]'s over).
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
 * The Artwork column browser on a split window ([artworkSplitView]): the page the user is on focused on the right (its
 * title without the tool's name); on the left, dimmed, with no hints, the page it came from ([AppState.artworkParent])
 * with the row that opened it highlighted. A tap there opens that row in place of the right pane
 * ([AppState.artworkParentTap]); the left pane's select does nothing, so a tap acts once. The dialogs cover both panes.
 */
@Composable
internal fun ArtworkSplitScreen(state: AppState, s: Screen, selection: State<ListSelection>) {
    // Keyed on the job too: beside a running fetch the right pane stays the same screen, the left pane's counts move on.
    val job = state.artworkJob?.collectAsState()?.value
    val parent = remember(s, job) { state.artworkParent(s) }
    SplitPanes(
        left = {
            parent?.let { p ->
                val tap: (Int) -> Unit = remember(p.screen) { { i -> state.artworkParentTap(p.screen, i) } }
                ArtworkPageBody(
                    p.screen, p.row, onSelect = {}, onOpen = tap, onHint = {},
                    modifier = Modifier.alpha(UNFOCUSED_ALPHA), split = true, focused = false,
                )
            }
        },
        right = {
            val truncated by state.settingInfoTruncated.collectAsState()
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
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onHint: (NxButton) -> Unit, modifier: Modifier, split: Boolean,
) {
    val phase = screen.job.phase
    val hints = artworkProgressHints(phase)
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
 * Wheel, Generate mix) a switch (a locked Wheel its value only); ScreenScraper's logout and TheGamesDB's allowance their
 * count as the line; the key rows their Set / Not set; Reset artwork `›`. A group label and a gap as the list's.
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
 * [row]'s description, from the user guide's Artwork page: a line for a status (today's requests, the allowance left)
 * or a locked row's reason, the fuller text behind the info icon; the rest compact ([quiet]).
 */
internal fun artworkSettingInfo(row: ArtworkSettingRow): SettingText = when (row.kind) {
    ArtworkSettingKind.REPLACE -> quiet(
        "Fetch art again for games that already have it",
        "Fetch art again for games that already have it. Fetch missing art then reads Fetch all art.",
    )
    ArtworkSettingKind.BOX2D -> quiet("Also fetch the flat 2D cover, beside the 3D box")
    ArtworkSettingKind.WHEEL ->
        if (row.enabled) quiet("Also fetch the game's logo")
        else SettingText("Needed by Generate mix", "Also fetch the game's logo. Locked On while Generate mix is on.")
    ArtworkSettingKind.MIX -> quiet("Make the handheld's mix picture on the phone", "Make the handheld's mix picture on the phone. It needs the Wheel, so Wheel stays On.")
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
    ArtworkSettingKind.RESET -> quiet(
        "Delete art the app fetched",
        "Delete art the app fetched, for every console or one. Art you added yourself is kept.",
    )
    ArtworkSettingKind.SGDB_REMOVE, ArtworkSettingKind.TGDB_REMOVE, ArtworkSettingKind.SECTION, ArtworkSettingKind.GAP -> SettingText("")
}

/**
 * Artwork Settings' hints for the highlighted row, as Settings' ([settingsHints]): an On/Off row LEFT Change and A
 * Toggle; TheGamesDB's allowance and a dimmed row Back only; the rest A Select. X Info while its description is cut off
 * or says more ([info]).
 */
internal fun artworkSettingsHints(row: ArtworkSettingRow?, touch: NxSettingRow?, info: Boolean): List<Hint> {
    val back = Hint(NxButton.B, "Back")
    val x = if (info) listOf(Hint(NxButton.X, "Info")) else emptyList()
    return when {
        row == null || !row.selectable -> listOf(back)
        !row.enabled || row.kind == ArtworkSettingKind.TGDB_LEFT -> listOf(back) + x
        touch?.control == NxSettingControl.SWITCH -> listOf(back, Hint(NxButton.LEFT, "Change"), Hint(NxButton.A, "Toggle")) + x
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
