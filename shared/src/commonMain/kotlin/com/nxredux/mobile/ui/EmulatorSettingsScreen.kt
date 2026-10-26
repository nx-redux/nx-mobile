package com.nxredux.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nxredux.mobile.options.CoreOptionFilter
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxScreenScaffold
import com.nxredux.mobile.ui.nx.NxSettingControl
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow
import com.nxredux.mobile.ui.nx.NxSettingsPage
import com.nxredux.mobile.ui.nx.control

private val LIST_HINTS = listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Select"))

/**
 * Settings -> Emulation -> Default settings: one row per core, its tags as the secondary text. [split]: the two-pane Settings'
 * right pane, titled "Default settings" (the left pane already shows Emulation); a single page keeps "Emulator Settings".
 */
@Composable
fun EmulatorListScreen(
    screen: Screen.EmulatorList, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, onHint: ((NxButton) -> Unit)? = null,
    split: Boolean = false,
) {
    NxScreenScaffold(
        rows = remember(screen.rows) { screen.rows.map { NxRow(it.emulator, secondary = emulatorListTags(it)) } },   // not per d-pad move
        selectedIndex = selectedIndex, showArt = false,
        hints = LIST_HINTS, onSelect = onSelect, onOpen = onOpen, onHint = onHint, title = emulatorListTitle(split), modifier = modifier,
    )
}

/** A core row's tags as the handheld names them, each once ("GPGX" for Genesis Plus GX on every Sega console). */
internal fun emulatorListTags(row: EmulatorListRow): String = row.tags.map(CoreRegistry::handheldTag).distinct().joinToString(" · ")

/** The core list's title: "Default settings" in the two-pane right pane (under Emulation on the left), else "Emulator Settings". */
internal fun emulatorListTitle(split: Boolean): String = if (split) "Default settings" else "Emulator Settings"

/** The console picker of a core serving several tags. */
@Composable
fun EmulatorConsolesScreen(
    screen: Screen.EmulatorConsoles, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, onHint: ((NxButton) -> Unit)? = null,
) {
    NxScreenScaffold(
        rows = screen.tags.map { NxRow(CoreRegistry.consoleOf(it)?.displayName ?: it, secondary = CoreRegistry.handheldTag(it)) },
        selectedIndex = selectedIndex, showArt = false,
        hints = LIST_HINTS, onSelect = onSelect, onOpen = onOpen, onHint = onHint, title = screen.emulator, modifier = modifier,
    )
}

/**
 * One tag's (or game's) settings: Console settings, then Core settings, as touch rows ([emulatorTouchRow], the in-game
 * menu's options); LEFT/RIGHT change a value, written at once. [onSetting] takes the controls' and info icons' taps.
 */
@Composable
fun EmulatorOptionsScreen(
    screen: Screen.EmulatorOptions, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, onHint: ((NxButton) -> Unit)? = null,
    onSetting: (NxSettingEvent) -> Unit = {}, onTruncated: (String, Boolean) -> Unit = { _, _ -> },
    truncated: String? = null,   // the selected row's description the UI reported cut off ([AppState.settingInfoTruncated])
) {
    val rows = remember(screen.rows) { screen.rows.map(::emulatorTouchRow) }   // not per d-pad move
    NxSettingsPage(
        emulatorOptionsTitle(screen), rows, selectedIndex,
        hints = emulatorOptionsHints(screen.rows.getOrNull(selectedIndex), rows.getOrNull(selectedIndex), truncated),
        onSelect = { i -> if (screen.rows.getOrNull(i)?.selectable == true) onSelect(i) },   // headers and notices take no tap
        onOpen = { i -> if (screen.rows.getOrNull(i)?.selectable == true) onOpen(i) },
        onEvent = { e -> if (screen.rows.getOrNull(e.row)?.selectable == true) onSetting(e) },
        modifier = modifier, onHint = onHint,
        onTruncated = onTruncated,
    )
}

/** The row an event is on. */
internal val NxSettingEvent.row: Int get() = when (this) {
    is NxSettingEvent.Choose -> row
    is NxSettingEvent.OpenPicker -> row
    is NxSettingEvent.Info -> row
}

/**
 * An Emulator Settings / Game Settings row as a touch row: a console row its brief line (none: compact)
 * and values, a core option compact with its visible values; the full text (a core's own description) behind the info
 * icon. A category `›`; a group label grey, the gap empty, a notice its text.
 */
internal fun emulatorTouchRow(row: EmulatorRow): NxSettingRow = when (row) {
    is EmulatorRow.Header -> NxSettingRow(row.label, section = true)
    EmulatorRow.UseEmulatorSettings -> NxSettingRow("Use emulator settings", info = USE_EMULATOR_SETTINGS_INFO, detail = USE_EMULATOR_SETTINGS_DETAIL)
    is EmulatorRow.Console -> row.row.option.let { o ->
        val note = row.row.disabledNote
        if (note != null) NxSettingRow(o.label, info = note, enabled = false)
        else NxSettingRow(
            o.label, info = o.brief, detail = o.info,
            choices = o.values.map(o::labelOf), index = o.values.indexOf(row.row.value),
        )
    }
    is EmulatorRow.Core -> row.option.let { o ->
        val visible = CoreOptionFilter.visibleValues(o)
        NxSettingRow(
            CoreOptionFilter.rowLabel(o, row.inCategory), detail = o.info,
            choices = visible.map { v -> CoreOptionFilter.valueLabel(o.copy(value = v)) }, index = visible.indexOf(o.value),
        )
    }
    is EmulatorRow.Category -> NxSettingRow(row.label, opens = true)
    is EmulatorRow.Notice -> NxSettingRow(row.text)
    EmulatorRow.Gap -> NxSettingRow("", spacer = true)
}

/** Use emulator settings' line (what A does) and its fuller text. */
internal const val USE_EMULATOR_SETTINGS_INFO = "Deletes this game's own settings"
internal const val USE_EMULATOR_SETTINGS_DETAIL = "Delete this game's settings, so it follows the emulator's again"

internal fun emulatorNxRow(row: EmulatorRow): NxRow = when (row) {
    is EmulatorRow.Header -> NxRow(row.label, section = true)   // a group's label: grey, never selected
    EmulatorRow.UseEmulatorSettings -> NxRow("Use emulator settings")
    is EmulatorRow.Console -> NxRow(row.row.option.label, enabled = row.row.disabledNote == null, secondary = row.row.disabledNote ?: row.row.option.labelOf(row.row.value), cycleable = row.row.disabledNote == null)
    is EmulatorRow.Core -> NxRow(CoreOptionFilter.rowLabel(row.option, row.inCategory), secondary = CoreOptionFilter.valueLabel(row.option), cycleable = true)
    is EmulatorRow.Category -> NxRow(row.label, secondary = "›")
    is EmulatorRow.Notice -> NxRow(row.text)
    EmulatorRow.Gap -> NxRow("", spacer = true)
}

/** "<emulator> — <console> (<TAG>)", or the game's name for Game Settings; an open category is appended. */
internal fun emulatorOptionsTitle(s: Screen.EmulatorOptions): String {
    val p = s.page
    val base = s.game?.game?.shownName ?: "${p.emulator} — ${CoreRegistry.consoleOf(p.tag)?.displayName ?: p.tag} (${CoreRegistry.handheldTag(p.tag)})"
    val category = s.category?.let { key -> p.core.orEmpty().firstOrNull { it.category == key }?.categoryLabel?.ifEmpty { null } ?: key }
    return if (category == null) base else "$base › $category"
}

/**
 * The page's hints for [row]: a value row Change, and with its touch row ([touch]) A's Toggle on a switch or Choose on a
 * picker (none on a segmented row, where A steps); X Info while its description is cut off ([truncated]) or says more.
 */
internal fun emulatorOptionsHints(row: EmulatorRow?, touch: NxSettingRow? = null, truncated: String? = null): List<Hint> {
    val info = touch != null && nxShowsInfo(touch, truncated)
    val x = if (info) listOf(Hint(NxButton.X, "Info")) else emptyList()
    return when (row) {
        is EmulatorRow.Console, is EmulatorRow.Core -> listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.B, "Back")) + when (touch?.control) {
            null -> listOf(Hint(NxButton.A, "Select"))
            NxSettingControl.SWITCH -> listOf(Hint(NxButton.A, "Toggle"))
            NxSettingControl.PICKER -> listOf(Hint(NxButton.A, "Choose"))
            else -> emptyList()
        } + x
        is EmulatorRow.Category, EmulatorRow.UseEmulatorSettings -> LIST_HINTS + x
        else -> listOf(Hint(NxButton.B, "Back"))
    }
}
