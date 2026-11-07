package com.nxredux.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nxredux.mobile.kids.PlayWindow
import com.nxredux.mobile.kids.timeLabel
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow

/** A day page's rows: Locked, then From and To while it has a window, then Done. */
internal enum class KidsDayRow { LOCKED, FROM, TO, DONE }

internal fun kidsDayRows(s: Screen.KidsDayEdit): List<KidsDayRow> =
    if (s.window == null) listOf(KidsDayRow.LOCKED, KidsDayRow.DONE) else KidsDayRow.entries

/** The steppers' step, minutes. */
internal const val KIDS_DAY_STEP = 30
private const val DAY_END = 24 * 60

/** [w] with From ([from]) or To moved [by] steps, between 0:00 and 24:00 and From always a step before To. */
internal fun kidsDayStep(w: PlayWindow, from: Boolean, by: Int): PlayWindow =
    if (from) w.copy(fromMin = (w.fromMin + by * KIDS_DAY_STEP).coerceIn(0, w.toMin - KIDS_DAY_STEP))
    else w.copy(toMin = (w.toMin + by * KIDS_DAY_STEP).coerceIn(w.fromMin + KIDS_DAY_STEP, DAY_END))

/** The times From ([from]) or To may take beside [w]'s other end, as their picker lists them. */
internal fun kidsDayTimes(w: PlayWindow, from: Boolean): List<Int> =
    if (from) (0 until DAY_END step KIDS_DAY_STEP).filter { it <= w.toMin - KIDS_DAY_STEP }
    else (KIDS_DAY_STEP..DAY_END step KIDS_DAY_STEP).filter { it >= w.fromMin + KIDS_DAY_STEP }

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** ISO day [day]'s name. */
internal fun kidsDayName(day: Int): String = DAY_NAMES[(day - 1).coerceIn(0, 6)]

/** A time on the steppers: "9:00 AM", the day's end "Midnight". */
internal fun kidsTimeLabel(minOfDay: Int): String = if (minOfDay >= DAY_END) "Midnight" else timeLabel(minOfDay)

/** A day's value on the Kids Mode page: "Locked" or "9:00 AM – 8:00 PM". */
internal fun kidsWindowLabel(w: PlayWindow?): String = if (w == null) "Locked" else "${kidsTimeLabel(w.fromMin)} – ${kidsTimeLabel(w.toMin)}"

/**
 * One day's play time ([Screen.KidsDayEdit]) in the setup pages' shape: Locked's switch, From and To (LEFT / RIGHT half
 * an hour, A or a tap their picker), Done. Every change is stored at once; B or Done go back.
 */
@Composable
internal fun KidsDayEditScreen(
    s: Screen.KidsDayEdit, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onSetting: (NxSettingEvent) -> Unit,
    onHint: (NxButton) -> Unit, modifier: Modifier = Modifier,
) {
    val w = s.window
    val rows = kidsDayRows(s).map { row ->
        when (row) {
            KidsDayRow.LOCKED -> NxSettingRow("Locked", choices = KIDS_OFF_ON, index = if (w == null) 1 else 0)
            KidsDayRow.FROM, KidsDayRow.TO -> {
                val from = row == KidsDayRow.FROM
                val times = if (w == null) emptyList() else kidsDayTimes(w, from)
                NxSettingRow(if (from) "From" else "To", choices = times.map(::kidsTimeLabel), index = times.indexOf(if (from) w?.fromMin else w?.toMin))
            }
            KidsDayRow.DONE -> NxSettingRow("Done")
        }
    }
    val line = if (w == null) "Games stay closed all day." else "Games open from ${kidsTimeLabel(w.fromMin)} to ${kidsTimeLabel(w.toMin)}."
    val hints = listOf(Hint(NxButton.B, "Back"), Hint(NxButton.LEFT, "Change"), Hint(NxButton.A, "Choose"))
    SetupBlockPage(
        if (s.setup) "Kids Mode setup" else "Kids Mode", kidsDayName(s.day), listOf(line), rows, buttons = 1,
        selected, hints, onSelect, onRow, onHint, modifier, onSetting = onSetting,
    )
}
