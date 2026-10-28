package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.NX_SWATCH_AUTO
import com.nxredux.mobile.settings.SecondaryAccent
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.ui.nx.LocalNxHintBarHidden
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDetailListBody
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxRowKind
import com.nxredux.mobile.ui.nx.NxBanner
import com.nxredux.mobile.ui.nx.NxScreenScaffold
import com.nxredux.mobile.ui.nx.NxSettingControl
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.NxSettingsList
import com.nxredux.mobile.ui.nx.NxSettingsPage
import com.nxredux.mobile.ui.nx.control
import com.nxredux.mobile.ui.nx.hasMoreInfo
import com.nxredux.mobile.ui.nx.nxDialogBody
import com.nxredux.mobile.ui.nx.LocalNxFontWeights
import com.nxredux.mobile.ui.nx.nxListInset

/**
 * One Settings page. Rows come from AppState; A acts on the highlighted row, B goes back to the section menu (from Library
 * the index is loaded first, and rescanned only when it no longer fits the settings) or, from a sub-page, to its
 * section. The detailed pages ([SettingsSection.detailed]) draw a title, their details and the rows; the rest draw
 * plain rows under their name. A page of only navigation rows uses list rows, one with a value row NX Redux's options rows
 * ([listRowKind]). A page with a value row ([settingsTouchPage]) draws touch rows instead, as the in-game menu's options
 * ([NxSettingsList]): [onSetting] takes their controls' and info icons' taps, [onTruncated] whether the selected row's
 * description fits. [titled]: the two-pane Settings' right pane (the split draws the remove dialog); [hints] (null: its
 * own) the pane's.
 */
@Composable
fun SettingsScreen(
    screen: Screen.Settings, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier,
    onHint: ((NxButton) -> Unit)? = null,
    titled: Boolean = false,
    hints: List<Hint>? = null,
    dialog: Boolean = true,   // false: the two-pane Settings draws the remove dialog over both panes
    onSetting: ((NxSettingEvent) -> Unit)? = null,   // null: a preview, its controls take no taps
    onTruncated: (String, Boolean) -> Unit = { _, _ -> },
    truncated: String? = null,   // the selected row's description the UI reported cut off ([AppState.settingInfoTruncated])
) {
    if (settingsTouchPage(screen)) {
        SettingsTouchPage(screen, selectedIndex, onSelect, onOpen, modifier, onHint, hints, dialog, onSetting, onTruncated, truncated)
        return
    }
    val shownHints = hints ?: settingsHints(screen.rows, selectedIndex)
    Box(modifier.fillMaxSize()) {
        val rows = remember(screen.rows) { screen.rows.map(::settingsNxRow) }   // not per d-pad move
        val rowKind = listRowKind(screen)
        if (screen.section.layoutGroup) {
            // A Layouts group: its values above a picture of them (B goes back).
            LayoutPreviewPage(
                screen.section.label, emptyList(), rows, screen.shot?.let(::layoutShotPicture), LAYOUT_PICTURE_AR, selectedIndex,
                shownHints, onSelect, onOpen, onHint ?: {}, landscapeWidth = 1f, titleAction = false,   // the rows across the page, as the other Settings pages'
            )
        } else if (screen.section.detailed) {
            NxDetailListBody(
                title = screen.section.label, details = screen.details, rows = rows, selectedIndex = selectedIndex,
                hints = shownHints, onSelect = onSelect, onOpen = onOpen, onHint = onHint,
                headline = screen.section != SettingsSection.DEFAULT_EMULATORS,   // its lines explain, they are not a status
                rowKind = rowKind,
            )
        } else {
            NxScreenScaffold(
                rows = rows,
                selectedIndex = selectedIndex, showArt = false,
                hints = shownHints, title = screen.section.label,   // its name over its rows, as the Settings menu's
                onSelect = onSelect, onOpen = onOpen, onHint = onHint, rowKind = rowKind,
                header = screen.banner?.let { text -> { NxBanner(text) } },
            )
        }
        if (dialog && screen.confirmRemove != null) SettingsRemoveDialog(onHint)
        if (dialog && screen.confirmReset) settingsResetAsk(screen, selectedIndex)?.let { SettingsResetDialog(it, onHint) }
    }
}

/**
 * A Settings value page as touch rows ([settingsTouchRow]): a Layouts group above its picture, Library under its counts
 * and last scan, Default emulators under its lines, the others under their name (and banner). The hints follow the selected row ([settingsHints]), X Info while
 * its description is cut off or says more.
 */
@Composable
private fun SettingsTouchPage(
    screen: Screen.Settings, selectedIndex: Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier,
    onHint: ((NxButton) -> Unit)?, hints: List<Hint>?, dialog: Boolean,
    onSetting: ((NxSettingEvent) -> Unit)?, onTruncated: (String, Boolean) -> Unit, truncated: String?,
) {
    val rows = remember(screen) { screen.rows.mapIndexed { i, r -> settingsTouchRow(r, screen.choices.getOrNull(i), screen.section) } }
    val report = onTruncated
    val selected = rows.getOrNull(selectedIndex)
    val infoShown = selected != null && nxShowsInfo(selected, truncated)
    val shownHints = hints ?: settingsHints(screen.rows, selectedIndex, selected, infoShown)
    val preview = onSetting == null
    val events = onSetting ?: {}
    Box(modifier.fillMaxSize()) {
        if (screen.section.layoutGroup) {
            LayoutPreviewPage(
                screen.section.label, emptyList(), emptyList(), screen.shot?.let(::layoutShotPicture), LAYOUT_PICTURE_AR, selectedIndex,
                shownHints, onSelect, onOpen, onHint ?: {}, landscapeWidth = 1f, titleAction = false,
                touchRows = { m, footer -> NxSettingsList(rows, selectedIndex, onSelect, onOpen, events, m, report, preview, footer) },
            )
        } else {
            // Shortcuts' binding keeps its bar: A Bind, X Clear and B Cancel are no console menu's, and a wrong press rebinds.
            CompositionLocalProvider(LocalNxHintBarHidden provides (LocalNxHintBarHidden.current && screen.section != SettingsSection.SHORTCUTS)) {
                NxSettingsPage(
                    screen.section.label, rows, selectedIndex, shownHints, onSelect, onOpen, events,
                    onTruncated = report, onHint = onHint, preview = preview,
                    header = when {
                        screen.banner != null -> ({ NxBanner(screen.banner) })
                        screen.details.isNotEmpty() -> ({ SettingsDetailLines(screen.details) })
                        else -> null
                    },
                )
            }
        }
        if (dialog && screen.confirmRemove != null) SettingsRemoveDialog(onHint)
        if (dialog && screen.confirmReset) settingsResetAsk(screen, selectedIndex)?.let { SettingsResetDialog(it, onHint) }
    }
}

/**
 * Whether a touch row's info shows (its icon, X and the X Info hint): a fuller text is behind it, or its line is the one
 * the UI reported cut off ([truncated], [AppState.settingInfoTruncated]).
 */
internal fun nxShowsInfo(row: NxSettingRow, truncated: String?): Boolean =
    row.hasMoreInfo || (row.info.isNotBlank() && row.info == truncated)

/** A touch page's lines under its title (Default emulators' caption), in the hint grey. */
@Composable
private fun SettingsDetailLines(lines: List<String>) {
    Text(
        lines.joinToString(" "), color = LocalNxColors.current.hint, fontFamily = LocalNxFontWeights.current, fontWeight = FontWeight.Normal,   // Regular, as a row's description
        // A step under a popup's body, under Appearance → Text size as the rows.
        fontSize = nxDialogBody() * 0.9f, lineHeight = nxDialogBody() * 0.9f * 1.35f,
        modifier = Modifier.fillMaxWidth().padding(horizontal = nxListInset(), vertical = 4.dp),
    )
}

/**
 * Whether [screen] draws as touch rows: a page with a value row ([Screen.Settings.choices]), Library and System (their
 * groups, System's licence paragraphs) and Default emulators (a value page even when every console has one emulator).
 * Emulators, Import saves and its results keep their rows.
 */
internal fun settingsTouchPage(screen: Screen.Settings): Boolean =
    screen.choices.any { it != null } || screen.section == SettingsSection.DEFAULT_EMULATORS ||
        screen.section == SettingsSection.LIBRARY || screen.section == SettingsSection.SYSTEM

/** The Settings rows that open a page or an editor: their touch row ends in `›`. */
private val OPENER_KINDS = setOf(
    SettingsKind.HOME, SettingsKind.DEFAULTS, SettingsKind.EMULATORS, SettingsKind.SAVES, SettingsKind.IMPORT_SAVES,
    SettingsKind.PAD_LAYOUT, SettingsKind.PAD_LAYOUT_SHAPE, SettingsKind.SHORTCUTS, SettingsKind.ANDROID_GAMES, SettingsKind.HIDDEN, SettingsKind.UNASSIGNED,
    SettingsKind.LAYOUT_HOME, SettingsKind.LAYOUT_CONSOLES, SettingsKind.LAYOUT_COLLECTIONS, SettingsKind.LAYOUT_TOOLS,
    SettingsKind.LAYOUT_GAME_LISTS, SettingsKind.LAYOUT_TITLE_HINTS, SettingsKind.LAYOUT_SWITCHER,
)

/** Rows whose values are a segmented button however many they are (Game lists' layout: its five styles side by side). */
private val SEGMENTED_KINDS = setOf(SettingsKind.GAME_LIST_STYLE)

/** Rows whose secondary (a path, a state, a count, the last scan) is their description, not a value. */
private val DESCRIBED_KINDS = setOf(SettingsKind.HOME, SettingsKind.EXTRA, SettingsKind.RESCAN, SettingsKind.UNASSIGNED, SettingsKind.HIDDEN)

/**
 * [row] as a touch row: its name, its description ([settingsInfo]; Library's [DESCRIBED_KINDS] their secondary, in the
 * warning colour for a problem), and its [choices] (a dimmed row shows its value only); a row without values its value
 * text, and `›` when it opens a page ([OPENER_KINDS]). A group label and a gap as the list's.
 */
internal fun settingsTouchRow(row: SettingsRow, choices: SettingChoices?, section: SettingsSection? = null): NxSettingRow {
    if (row.kind == SettingsKind.SECTION) return NxSettingRow(row.label, section = true)
    if (row.kind == SettingsKind.GAP) return NxSettingRow("", spacer = true)
    if (row.kind == SettingsKind.TEXT) return NxSettingRow(row.label, paragraph = true)
    val described = row.kind in DESCRIBED_KINDS
    val text = if (described) SettingText(row.secondary.orEmpty()) else settingsInfo(row, section)
    val values = choices?.takeIf { row.enabled }
    return NxSettingRow(
        row.label, info = text.line, detail = text.detail,
        choices = values?.labels.orEmpty(), index = values?.index ?: -1,
        value = if (values == null && !described && row.kind != SettingsKind.LICENSES_LINK) row.secondary else null,   // the link's URL: behind its info icon
        opens = row.kind in OPENER_KINDS, enabled = row.enabled,
        segmented = row.kind in SEGMENTED_KINDS,
        warning = row.warning,
        button = "Rescan".takeIf { row.kind == SettingsKind.RESCAN },   // Library's counts row: the button on their right
        action = settingsRowAction(row, section),
        swatches = when (row.kind) {   // the accents: a circle per colour
            SettingsKind.ACCENT -> AccentPreset.entries.map { it.argb }
            SettingsKind.SECONDARY_ACCENT -> SecondaryAccent.entries.map { if (it is SecondaryAccent.Preset) it.preset.argb else NX_SWATCH_AUTO }
            else -> null
        }.takeIf { values != null },
        adds = row.kind == SettingsKind.ADD_EXTRA,   // Library's Add ROM folder: a "+" before its name
    )
}

/**
 * What A does on [row] when it is no console menu's obvious act, shown on the focused row ([NxSettingRow.action]): Copy
 * device info, the update row's Check or Install, a reset (Shortcuts' Clear all), a ROM folder's Remove. Null elsewhere.
 */
internal fun settingsRowAction(row: SettingsRow, section: SettingsSection?): String? = when (row.kind) {
    SettingsKind.COPY_INFO -> "Copy"
    SettingsKind.APP_UPDATE -> appUpdateHint(row.label)
    SettingsKind.RESET_DEFAULTS -> if (section == SettingsSection.SHORTCUTS) "Clear all" else "Reset"
    SettingsKind.PAD_LAYOUT_RESET -> "Reset"
    SettingsKind.EXTRA -> "Remove"
    else -> null
}

/** A reset's question: its title, the one line saying what it puts back, and its confirm button. */
internal data class SettingsResetAsk(val title: String, val text: String, val confirm: String)

/**
 * What [page]'s reset row at [selected] asks before it acts ([Screen.Settings.confirmReset]): Shortcuts' Clear all,
 * Pad layout's Reset console, every other page's Reset to defaults with that page's scope. Null for any other row.
 */
internal fun settingsResetAsk(page: Screen.Settings, selected: Int): SettingsResetAsk? = when (page.rows.getOrNull(selected)?.kind) {
    SettingsKind.RESET_DEFAULTS -> when (page.section) {
        SettingsSection.SHORTCUTS -> SettingsResetAsk("Clear all shortcuts?", "Every action goes back to None and both touch buttons come off the pad.", "Clear all")
        SettingsSection.APPEARANCE -> SettingsResetAsk("Reset to defaults?", "Pad theme, Pad buttons, Accent, Secondary accent and Text size go back to their defaults.", "Reset")
        SettingsSection.MAIN_MENU -> SettingsResetAsk("Reset to defaults?", "Every Layouts group and Extra info go back to their defaults.", "Reset")
        SettingsSection.CONTROLS -> SettingsResetAsk("Reset to defaults?", "Vibration, Menu pad and every pad layout go back to their defaults.", "Reset")
        else -> SettingsResetAsk("Reset to defaults?", "This page's settings go back to their defaults.", "Reset")
    }
    SettingsKind.PAD_LAYOUT_RESET -> {
        val console = page.rows.firstOrNull { it.kind == SettingsKind.PAD_LAYOUT_CONSOLE }?.secondary ?: "This console"
        SettingsResetAsk("Reset pad layouts?", "$console's pad layouts on this screen go back to their defaults.", "Reset")
    }
    else -> null
}

/** [ask] over the page ([Screen.Settings.confirmReset]): A resets, B (Cancel) keeps everything. */
@Composable
internal fun SettingsResetDialog(ask: SettingsResetAsk, onHint: ((NxButton) -> Unit)?) {
    NxDialog(
        ask.title, ask.text,
        onConfirm = { onHint?.invoke(NxButton.A) }, confirmLabel = ask.confirm, onCancel = { onHint?.invoke(NxButton.B) },
    )
}

/** "Remove folder?" over Library ([Screen.Settings.confirmRemove]): A removes, B keeps it. */
@Composable
internal fun SettingsRemoveDialog(onHint: ((NxButton) -> Unit)?) {
    NxDialog(
        "Remove folder", "Stop scanning this folder? Its ROMs stay where they are.",
        onConfirm = { onHint?.invoke(NxButton.A) }, confirmLabel = "Remove", onCancel = { onHint?.invoke(NxButton.B) },
    )
}

/**
 * The hint bar for the row at [selected]: no A on a read-only row (About's info rows, or a page of only those), "A Copy"
 * on About's Copy device info, "A Open" on Full list of licenses, "A Check" or "A Update" on its update row (none while busy), "A Select" elsewhere.
 */
internal fun settingsHints(rows: List<SettingsRow>, selected: Int, touch: NxSettingRow? = null, info: Boolean = false): List<Hint> {
    val back = Hint(NxButton.B, "Back")
    val kind = rows.getOrNull(selected)?.kind
    val infoHint = if (info) listOf(Hint(NxButton.X, "Info")) else emptyList()
    return when {
        kind == SettingsKind.COPY_INFO -> listOf(back, Hint(NxButton.A, "Copy"))
        kind == SettingsKind.LICENSES_LINK -> listOf(back, Hint(NxButton.A, "Open"))
        kind == SettingsKind.APP_UPDATE -> listOfNotNull(back, appUpdateHint(rows[selected].label)?.let { Hint(NxButton.A, it) })
        kind == SettingsKind.INFO || rows.all { it.kind == SettingsKind.INFO } -> listOf(back)
        // Press to bind: B cancels a wait ([SettingsRow.note] set), else A binds and X clears.
        kind == SettingsKind.SHORTCUT_BIND -> if (rows[selected].note.let { it != null && it != MIC_ROW_NOTE }) listOf(Hint(NxButton.B, "Cancel"))
            else listOf(back, Hint(NxButton.A, "Bind"), Hint(NxButton.X, "Clear"))
        // A touch value row (the in-game menu's): LEFT / RIGHT change it, A toggles a switch or opens a picker. Use as
        // home screen and All files access are Android's to change: A alone (it opens Android's screen).
        touch != null && touch.choices.size >= 2 -> listOf(back) +
            (if (kind == SettingsKind.HOME_SCREEN || kind == SettingsKind.ALL_FILES || kind == SettingsKind.MIC_ACCESS) emptyList() else listOf(Hint(NxButton.LEFT, "Change"))) + when (touch.control) {
            NxSettingControl.SWITCH -> listOf(Hint(NxButton.A, "Toggle"))
            NxSettingControl.PICKER -> listOf(Hint(NxButton.A, "Choose"))
            else -> emptyList()
        } + infoHint
        touch != null && !touch.enabled -> listOf(back) + infoHint
        else -> listOf(back, Hint(NxButton.A, "Select")) + infoHint
    }
}

/**
 * How a list page draws its rows: the "Label: Value" pages (the Settings pages with a value row, an emulator's options)
 * use NX Redux's options row; navigation pages (the Settings pages of only page and action rows,
 * [SettingsSection.navigation]), category lists, pickers and game lists keep the pill. The RetroAchievements and Artwork
 * settings draw touch rows ([raSettingsTouchRow], [artworkSettingTouchRow]).
 */
internal fun listRowKind(screen: Screen): NxRowKind = when (screen) {
    is Screen.Settings -> if (screen.section.navigation) NxRowKind.PILL else NxRowKind.OPTIONS
    is Screen.EmulatorOptions -> NxRowKind.OPTIONS
    else -> NxRowKind.PILL
}

/** A Settings row as an options row: every value row steps on LEFT/RIGHT (NX Redux ITEM_CYCLE), so it shows `< value >` while selected and enabled. */
internal fun settingsNxRow(row: SettingsRow): NxRow = NxRow(
    row.label, enabled = row.enabled, secondary = row.secondary,
    cycleable = row.enabled && when (row.kind) {
        SettingsKind.PAD_THEME, SettingsKind.PAD_BUTTONS,
        SettingsKind.DEFAULT_TAG, SettingsKind.MENU_STYLE, SettingsKind.GAME_LIST_STYLE,
        SettingsKind.MENU_ORIENTATION, SettingsKind.GAME_LIST_ORIENTATION, SettingsKind.GAME_LIST_VERTICAL_ALIGNMENT,
        SettingsKind.CONTROLLER_ART, SettingsKind.TAB_SHOWN, SettingsKind.SHELF_SHOWN, SettingsKind.HINT_SHOWN, SettingsKind.LIST_ART, SettingsKind.BACKDROP_ART, SettingsKind.PAGE_TITLE, SettingsKind.BUTTON_HINTS, SettingsKind.EXTRA_INFO, SettingsKind.HOME_STATS, SettingsKind.LAYOUT_OPTIONS, SettingsKind.ACHIEVEMENTS_PANEL, SettingsKind.SWITCHER_LAYOUT, SettingsKind.PAD_VIBRATION, SettingsKind.GAME_RUMBLE, SettingsKind.MENU_PAD, SettingsKind.PAD_LAYOUT_CONSOLE, SettingsKind.SHORTCUT_TOUCH, SettingsKind.ACCENT, SettingsKind.SECONDARY_ACCENT, SettingsKind.TEXT_SIZE, SettingsKind.DEBUG_LOG, SettingsKind.SECOND_SCREEN_ENABLE, SettingsKind.BOTTOM_SCREEN, SettingsKind.PAUSE_UNFOCUSED, SettingsKind.SAVE_FORMAT, SettingsKind.STATE_FORMAT, SettingsKind.EXTRACTED_NAME -> true
        else -> false
    },
)
