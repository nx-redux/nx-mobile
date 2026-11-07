package com.nxredux.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow

/** A row of a Kids Mode setup step ([Screen.KidsSetup]). */
internal sealed interface KidsSetupRow {
    /** An installed app, on the kid's list or not. */
    data class App(val app: InstalledApp, val allowed: Boolean) : KidsSetupRow
    /** A day's play time; A opens it ([Screen.KidsDayEdit]). */
    data class Day(val day: Int, val label: String) : KidsSetupRow
    /** Continue (setup) or Done (Settings' Allowed apps). */
    data object Next : KidsSetupRow
    data object Grant : KidsSetupRow
    data object Skip : KidsSetupRow
}

/** [s]'s rows: the apps then Next; the seven days then Next; Grant / Skip. */
internal fun kidsSetupRows(s: Screen.KidsSetup): List<KidsSetupRow> = when (s.step) {
    KidsSetupStep.PIN -> emptyList()   // drawn as the PIN pad ([Screen.KidsPin])
    KidsSetupStep.APPS -> s.apps.map { KidsSetupRow.App(it, it.id in s.allowed) } + KidsSetupRow.Next
    KidsSetupStep.TIMES -> (1..7).map { KidsSetupRow.Day(it, kidsWindowLabel(s.schedule.days[it])) } + KidsSetupRow.Next
    KidsSetupStep.USAGE, KidsSetupStep.ALARMS -> listOf(KidsSetupRow.Grant, KidsSetupRow.Skip)
}

/** A step's headline and its line. */
internal fun kidsSetupText(s: Screen.KidsSetup): Pair<String, String> = when (s.step) {
    KidsSetupStep.PIN -> "Choose a PIN" to ""
    KidsSetupStep.APPS -> "Allowed apps" to
        if (s.setup) "The apps your kid can open while games are closed. You can change them later in Settings. $KIDS_APPS_AVOID"
        else "The apps your kid can open while games are closed. $KIDS_APPS_AVOID"
    KidsSetupStep.TIMES -> "Play times" to "Games open in these hours. Outside them, only the allowed apps can be opened."
    KidsSetupStep.USAGE -> "Usage access" to "It is how NX Redux sends your kid back from apps that aren't allowed."
    KidsSetupStep.ALARMS -> "Alarms & reminders" to "It lets the warning and the lock come on time. Without it they can come a few minutes late."
}

/** A step's lines under its headline: [kidsSetupText]'s, and on setup's Apps step with none picked [KIDS_PICK_ONE_APP]. */
internal fun kidsSetupLines(s: Screen.KidsSetup): List<String> = buildList {
    add(kidsSetupText(s).second)
    if (s.setup && s.step == KidsSetupStep.APPS && s.allowed.isEmpty()) add(KIDS_PICK_ONE_APP)
}

/** [row] as the setup page draws it: an app's switch (a game says so), a day's window, or a button. */
internal fun kidsSetupTouchRow(row: KidsSetupRow, setup: Boolean): NxSettingRow = when (row) {
    is KidsSetupRow.App -> NxSettingRow(
        row.app.label, info = if (row.app.game) "Game" else "", choices = KIDS_OFF_ON, index = if (row.allowed) 1 else 0,
        tapActs = true,   // a tap on the name toggles too ([AppState.kidsSetupRow])
    )
    is KidsSetupRow.Day -> NxSettingRow(kidsDayName(row.day), value = row.label, opens = true)
    KidsSetupRow.Next -> NxSettingRow(if (setup) "Continue" else "Done")
    KidsSetupRow.Grant -> NxSettingRow("Grant")
    KidsSetupRow.Skip -> NxSettingRow("Skip")
}

internal val KIDS_OFF_ON = listOf("Off", "On")

/** The apps never to allow: each is a way out of Kids Mode, or to new games. */
internal const val KIDS_APPS_AVOID =
    "Don't allow Settings, a file manager or the Play Store: from them your kid could turn Kids Mode off or install games."

/**
 * A Kids Mode setup step (or Settings' Allowed apps) in the setup pages' shape ([SetupBlockPage]): its headline and line,
 * its rows, its buttons at the bottom (Continue or Done; Grant / Skip). A or a tap chooses
 * ([onRow]); an app's switch toggles ([onSetting]); B leaves setup (Kids Mode stays off) or Allowed apps.
 */
@Composable
internal fun KidsSetupScreen(
    s: Screen.KidsSetup, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onSetting: (NxSettingEvent) -> Unit,
    onHint: (NxButton) -> Unit, modifier: Modifier = Modifier,
) {
    val rows = kidsSetupRows(s)
    val headline = kidsSetupText(s).first
    val buttons = when (s.step) {
        KidsSetupStep.APPS, KidsSetupStep.TIMES -> 1
        else -> 2
    }
    val hints = listOf(Hint(NxButton.B, if (s.setup) "Cancel" else "Back"), Hint(NxButton.A, "Choose"))
    SetupBlockPage(
        if (s.setup) "Kids Mode setup" else "Kids Mode", headline, kidsSetupLines(s), rows.map { kidsSetupTouchRow(it, s.setup) }, buttons,
        selected, hints, onSelect, onRow, onHint, modifier, onSetting = onSetting,
        lineBreaks = true,   // the Apps step's "Pick at least one app" on its own line
    )
}
